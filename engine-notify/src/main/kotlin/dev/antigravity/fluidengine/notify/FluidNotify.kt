package dev.antigravity.fluidengine.notify

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dev.antigravity.fluidengine.foundation.notify.FluidNotifyCenter
import java.io.File
import java.time.ZoneId
import kotlin.random.Random

// F8.19 · `notifiche-di-sistema`: il kit delle notifiche vere di Android (quelle dentro l'app sono `FluidNotificationHost`
// di engine-ui). Canali con parole comuni, notifiche raccolte per filo («3 nuovi voti» invece di tre sveglie), risposte
// rapide, pallino coerente, nessun passaggio sopra le modalità di concentrazione, e il riepilogo a orari scelti, **spento
// di serie**: tutto arriva subito finché l'utente non lo accende (la scheda: «un riepilogo che nasconde una cosa urgente
// è peggio di una notifica in più»). Il riferimento Apple è il Riepilogo programmato e le Full immersion di iOS.

/**
 * Come è andata una pubblicazione. Non è un `enum` (regola 3.11).
 */
class FluidNotifyResult private constructor(val name: String) {
  /** Vero se la notifica è (o sarà, al turno della porta del ritmo) a schermo. */
  val posted: Boolean get() = this == Posted

  override fun toString(): String = "FluidNotifyResult($name)"

  companion object {
    /** Pubblicata (o in fila per il ritmo, che non la perde). */
    val Posted: FluidNotifyResult = FluidNotifyResult("posted")

    /** Messa da parte per il prossimo riepilogo, come ha scelto l'utente. */
    val InDigest: FluidNotifyResult = FluidNotifyResult("digest")

    /** Non mostrata: le notifiche dell'app sono spente (il permesso negato, o spente dall'utente). */
    val NotificationsOff: FluidNotifyResult = FluidNotifyResult("off")

    /** Non mostrata: il canale è spento nelle impostazioni di Android. */
    val ChannelOff: FluidNotifyResult = FluidNotifyResult("channel-off")
  }
}

/**
 * Come l'app usa engine-notify: i suoi canali, l'icona, le parole e il gestore dei tocchi. Si passa a
 * [FluidNotify.install] dall'`Application.onCreate`, così un tocco che fa ripartire l'app trova il gestore.
 *
 * @param channels i canali dell'app, nell'ordine delle impostazioni.
 * @param smallIcon l'icona piccola della barra di stato (bianca su trasparente): di serie la campanella dell'engine. Le
 *   attività vive hanno le icone del loro genere.
 * @param color l'accento delle notifiche (l'icona e i tasti), o null; mai un fondo pieno (vietato ai Live Update).
 * @param labels le parole.
 * @param onEvent i tocchi dell'utente (tasti, risposte, promemoria): sul thread principale.
 * @param launchActivity l'attività da aprire al tocco di una notifica; di serie quella di avvio dell'app.
 */
class FluidNotifyConfig(
  val channels: List<FluidNotifyChannel> = emptyList(),
  val smallIcon: Int = 0,
  val color: Int? = null,
  val labels: FluidNotifyLabels = FluidNotifyLabels(),
  val onEvent: (FluidNotifyEvent) -> Unit = {},
  val launchActivity: Class<out Activity>? = null,
)

/**
 * Le notifiche dell'app: il punto d'ingresso di engine-notify. Uno per processo: [install] dall'`Application`, poi [get].
 *
 * ```kotlin
 * class App : Application() {
 *   override fun onCreate() {
 *     super.onCreate()
 *     FluidNotify.install(this, FluidNotifyConfig(channels = listOf(Voti, Circolari), onEvent = ::onNotifyEvent))
 *   }
 * }
 * FluidNotify.get(context).post(FluidNotifyMessage("voto-123", "voti", "Matematica: 8", thread = "voti"))
 * ```
 *
 * Dentro: [post] e [cancel] per le notifiche, [liveUpdates] per le attività vive, [reminders] per i promemoria, [center]
 * per le pagine di engine-ui. Il permesso delle notifiche (Android 13 e oltre) non lo chiede l'engine da solo: lo chiede
 * il tasto che ne ha bisogno, con `rememberFluidPermission(FluidPermission.Notifications, …)` di engine-ui (F8.11).
 */
class FluidNotify private constructor(context: Context, config: FluidNotifyConfig) {
  /** Il contesto dell'applicazione. */
  val context: Context = context.applicationContext

  /** La configurazione di adesso. */
  var config: FluidNotifyConfig = config
    private set

  internal val handler = Handler(Looper.getMainLooper())
  internal val store = FluidNotifyStore(File(this.context.noBackupFilesDir, "fluid_notify.json"))
  internal var poster: FluidNotifyPoster = FluidNotifyPoster.System(this.context)
  internal val gate = FluidNotifyGate(clock = { elapsedNow() }, schedule = { delay, block -> handler.postDelayed(block, delay) })
  internal val foreground = FluidAppForeground(handler)

  /** Il numero di questo processo: un tasto di una notifica di un processo morto non trova più la sua funzione. */
  internal val session: Long = Random.nextLong()

  // Gli orologi e la versione di Android: le prove li spostano.
  internal var sdk: Int = Build.VERSION.SDK_INT
  internal var elapsedNow: () -> Long = SystemClock::elapsedRealtime
  internal var wallNow: () -> Long = System::currentTimeMillis
  internal var zone: () -> ZoneId = ZoneId::systemDefault

  /** L'ultima volta che ogni filo ha suonato (`elapsedRealtime`), per la raffica. */
  private val threadAlerts = HashMap<String, Long>()

  /** Le attività vive fuori dall'app. */
  val liveUpdates: FluidLiveUpdates = FluidLiveUpdates(this)

  /** I promemoria. */
  val reminders: FluidReminders = FluidReminders(this)

  private val centerImpl = FluidNotifyCenterImpl(this)

  /** Il centro delle notifiche per le pagine di engine-ui (impostazioni, «Cosa succederà», «Le notifiche non arrivano?»). */
  val center: FluidNotifyCenter get() = centerImpl

  init {
    (this.context as? Application)?.let { foreground.attach(it) }
  }

  private fun start() {
    ensureChannels()
    // Le attività specchiate seguono l'app dentro e fuori.
    foreground.onChange = { outside -> liveUpdates.onOutsideChanged(outside) }
    reminders.restore()
    armDigest()
  }

  // --- i canali -------------------------------------------------------------------------------------------------------

  /** Tutti i canali: quelli dell'app, poi i tre dell'engine (attività, promemoria, riepilogo). */
  val channels: List<FluidNotifyChannel>
    get() = config.channels + listOf(
      FluidLiveUpdates.channel(config.labels),
      FluidNotifyChannel(
        FluidNotifyChannel.RemindersId, config.labels.remindersChannel, config.labels.remindersChannelDescription,
        importance = FluidNotifyImportance.Urgent, digestible = false, category = NotificationCompat.CATEGORY_REMINDER,
      ),
      FluidNotifyChannel(
        FluidNotifyChannel.DigestId, config.labels.digestChannel, config.labels.digestChannelDescription,
        importance = FluidNotifyImportance.Normal, digestible = false,
      ),
    )

  /** Il canale [id], o null. */
  fun channel(id: String): FluidNotifyChannel? = channels.firstOrNull { it.id == id }

  /**
   * Crea (o aggiorna nome e descrizione di) tutti i canali e i loro gruppi. Android usa l'importanza solo alla prima
   * creazione: dopo decide l'utente, e l'engine non la cambia mai.
   */
  fun ensureChannels() {
    val nm = notificationManager()
    val groups = channels.mapNotNull { it.group }.distinctBy { it.id }
    if (groups.isNotEmpty()) nm.createNotificationChannelGroups(groups.map { NotificationChannelGroup(it.id, it.name) })
    nm.createNotificationChannels(
      channels.map { channel ->
        NotificationChannel(channel.id, channel.name, channel.importance.androidImportance).apply {
          description = channel.description
          setShowBadge(channel.badge)
          channel.group?.let { group = it.id }
        }
      },
    )
  }

  /** Vero se le notifiche dell'app sono accese (da Android 13: il permesso è concesso). */
  fun notificationsAllowed(): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

  /** Vero se il canale [channelId] è acceso. */
  fun channelAllowed(channelId: String): Boolean =
    notificationManager().getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE

  /** Vero se una notifica sul canale [channelId] si può mostrare adesso. */
  fun canPost(channelId: String): Boolean = notificationsAllowed() && channelAllowed(channelId)

  // --- le notifiche ---------------------------------------------------------------------------------------------------

  /**
   * Pubblica [message], o la mette nel riepilogo se l'utente l'ha chiesto per il suo canale (e non è urgente). Le
   * notifiche dello stesso filo si raccolgono; quelle che arrivano in raffica dopo la prima arrivano in silenzio.
   */
  fun post(message: FluidNotifyMessage): FluidNotifyResult {
    val channel = channel(message.channel) ?: error("il canale «${message.channel}» non è dichiarato in FluidNotifyConfig.channels")
    if (!notificationsAllowed()) return FluidNotifyResult.NotificationsOff.also { blocked(channel.id) }
    if (!channelAllowed(channel.id)) return FluidNotifyResult.ChannelOff.also { blocked(channel.id) }
    val memory = store.read()
    val urgent = message.urgent ?: !channel.digestible
    if (memory.digestEnabled && !urgent && channel.digestible && channel.id in memory.digestChannels) {
      store.update { it.copy(digestQueue = it.digestQueue.filterNot { q -> q.id == message.id } + message) }
      armDigest()
      centerImpl.refreshSettings()
      return FluidNotifyResult.InDigest
    }
    onMain { show(message, channel, digest = false) }
    return FluidNotifyResult.Posted
  }

  /** Toglie la notifica [messageId] (e ricalcola il riassunto del suo filo). */
  fun cancel(messageId: String) = onMain {
    gate.cancel("msg/$messageId")
    val thread = active().firstOrNull { it.tag == MessageTagPrefix + messageId }?.notification?.group
    poster.cancel(MessageTagPrefix + messageId, MessageNotificationId)
    store.update { it.copy(digestQueue = it.digestQueue.filterNot { q -> q.id == messageId }) }
    thread?.let { summaryLater(it) }
  }

  /** Toglie tutte le notifiche del filo [thread]. */
  fun cancelThread(thread: String) = onMain {
    val key = ThreadGroupPrefix + thread
    active().filter { it.notification.group == key }.forEach { poster.cancel(it.tag, it.id) }
  }

  /** Quante notifiche dell'app contano nel pallino dell'icona: quelle a schermo dei canali col pallino. */
  fun badgeCount(): Int = active().count { sbn ->
    val n = sbn.notification
    sbn.tag?.startsWith(MessageTagPrefix) == true && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
      channel(n.channelId)?.badge != false
  }

  /**
   * Un'attività dell'app aperta da una notifica (un tasto che apre l'app, `FluidNotifyAction.opensApp`) porta l'evento
   * nell'`Intent`: l'attività lo passa qui da `onCreate` e `onNewIntent`. Vero se c'era un evento.
   */
  fun handleIntent(intent: Intent?): Boolean {
    val id = intent?.getStringExtra(FluidNotifyReceiver.ExtraOpenMessage) ?: return false
    val key = intent.getStringExtra(FluidNotifyReceiver.ExtraKey) ?: return false
    intent.removeExtra(FluidNotifyReceiver.ExtraOpenMessage)
    if (id.startsWith("reminder:")) {
      dispatch(FluidNotifyEvent.ReminderAction(id.removePrefix("reminder:"), key))
    } else {
      dispatch(FluidNotifyEvent.Action(id, key))
      cancel(id)
    }
    return true
  }

  private fun show(message: FluidNotifyMessage, channel: FluidNotifyChannel, digest: Boolean) {
    val now = elapsedNow()
    val thread = when {
      digest -> DigestGroup
      message.thread != null -> ThreadGroupPrefix + message.thread
      else -> null
    }
    // La raffica: dopo la prima notifica del filo, le altre entro la finestra arrivano in silenzio, raccolte.
    val alert = when {
      digest -> false
      thread == null -> true
      else -> {
        val last = threadAlerts[thread]
        (last == null || now - last > FluidNotifyProfile.BurstWindowMs).also { if (it) threadAlerts[thread] = now }
      }
    }
    gate.submit("msg/${message.id}") {
      poster.notify(MessageTagPrefix + message.id, MessageNotificationId, buildMessage(message, channel, thread, alert))
      if (thread != null && !digest) summaryLater(thread)
    }
  }

  private fun buildMessage(message: FluidNotifyMessage, channel: FluidNotifyChannel, group: String?, alert: Boolean): Notification {
    val builder = NotificationCompat.Builder(context, channel.id)
      .setSmallIcon(smallIcon())
      .setContentTitle(message.title)
      .setContentText(message.text)
      .setWhen(message.timeMillis)
      .setShowWhen(true)
      .setAutoCancel(true)
      .setPriority(channel.importance.priority)
      .setContentIntent(openIntent(message.open, "msg:${message.id}"))
      .setCategory(channel.category ?: if (message.person != null) NotificationCompat.CATEGORY_MESSAGE else null)
    config.color?.let { builder.setColor(it) }
    if (message.person != null) {
      val me = Person.Builder().setName(config.labels.meName).build()
      val sender = Person.Builder().setName(message.person).build()
      builder.setStyle(
        NotificationCompat.MessagingStyle(me).addMessage(message.text ?: message.title, message.timeMillis, sender)
          .setConversationTitle(message.title.takeIf { message.text != null }),
      )
    } else if ((message.text?.length ?: 0) > LongTextChars) {
      builder.setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
    }
    if (message.sensitive) {
      builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      builder.setPublicVersion(
        NotificationCompat.Builder(context, channel.id).setSmallIcon(smallIcon()).setContentTitle(channel.name).build(),
      )
    }
    if (group != null) {
      builder.setGroup(group)
      // Un figlio della raffica tace: suona il primo, il riassunto non suona mai (GROUP_ALERT_CHILDREN).
      if (!alert) builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
    }
    message.reply?.let { reply ->
      val input = RemoteInput.Builder(ReplyKey).setLabel(reply.hint)
        .apply { if (reply.choices.isNotEmpty()) setChoices(reply.choices.toTypedArray()) }
        .build()
      builder.addAction(
        NotificationCompat.Action.Builder(0, reply.label, messagePendingIntent(FluidNotifyReceiver.ActionReply, message.id, ReplyKey, mutable = true))
          .addRemoteInput(input)
          .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
          .setShowsUserInterface(false)
          .build(),
      )
    }
    message.actions.take(if (message.reply != null) 2 else 3).forEach { action ->
      val pi = if (action.opensApp) {
        openIntent(message.open, "msg:${message.id}:${action.key}", eventId = message.id, eventKey = action.key)
      } else {
        messagePendingIntent(FluidNotifyReceiver.ActionMessageAction, message.id, action.key, mutable = false)
      }
      builder.addAction(NotificationCompat.Action.Builder(0, action.label, pi).setShowsUserInterface(action.opensApp).build())
    }
    return builder.build()
  }

  /**
   * Il riassunto del filo [group]: con due o più notifiche a schermo, una notifica che le raccoglie («3 nuovi voti», la
   * frase del canale), che non suona mai; con una sola, niente riassunto.
   */
  /** Il riassunto passa anche lui dalla porta del ritmo: tre messaggi in raffica ne chiedono uno solo, l'ultimo. */
  private fun summaryLater(group: String) {
    gate.submit("sum/$group") { refreshSummary(group, alert = false) }
  }

  private fun refreshSummary(group: String, alert: Boolean) {
    val children = active().filter {
      it.notification.group == group && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0
    }
    val tag = SummaryTagPrefix + group
    if (children.size < 2) {
      poster.cancel(tag, SummaryNotificationId)
      return
    }
    val newest = children.maxByOrNull { it.notification.`when` }!!.notification
    val channel = channel(newest.channelId) ?: return
    val style = NotificationCompat.InboxStyle()
    children.sortedByDescending { it.notification.`when` }.take(InboxLines).forEach { sbn ->
      val extras = sbn.notification.extras
      val title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE)
      val text = extras.getCharSequence(NotificationCompat.EXTRA_TEXT)
      style.addLine(listOfNotNull(title, text).joinToString(" · "))
    }
    val summary = channel.summary(children.size)
    style.setBigContentTitle(summary)
    val builder = NotificationCompat.Builder(context, channel.id)
      .setSmallIcon(smallIcon())
      .setContentTitle(summary)
      .setContentText(newest.extras.getCharSequence(NotificationCompat.EXTRA_TITLE))
      .setStyle(style)
      .setGroup(group)
      .setGroupSummary(true)
      .setGroupAlertBehavior(if (alert) NotificationCompat.GROUP_ALERT_SUMMARY else NotificationCompat.GROUP_ALERT_CHILDREN)
      .setOnlyAlertOnce(!alert)
      .setAutoCancel(true)
      .setNumber(children.size)
      .setPriority(channel.importance.priority)
      .setCategory(channel.category)
      .setContentIntent(openIntent(null, "sum:$group"))
    config.color?.let { builder.setColor(it) }
    poster.notify(tag, SummaryNotificationId, builder.build())
  }

  // --- il riepilogo ---------------------------------------------------------------------------------------------------

  /** Il riepilogo è suonato: le notifiche messe da parte arrivano raccolte, con un riassunto che suona una volta. */
  internal fun deliverDigest() = onMain {
    val memory = store.read()
    val queue = memory.digestQueue
    store.update { it.copy(digestQueue = emptyList()) }
    armDigest()
    centerImpl.refreshSettings()
    if (queue.isEmpty() || !notificationsAllowed()) return@onMain
    queue.forEach { message ->
      val channel = channel(message.channel) ?: return@forEach
      if (!channelAllowed(channel.id)) return@forEach
      gate.submit("msg/${message.id}") {
        poster.notify(MessageTagPrefix + message.id, MessageNotificationId, buildMessage(message, channel, DigestGroup, alert = false))
      }
    }
    gate.submit("digest") {
      val style = NotificationCompat.InboxStyle()
      queue.takeLast(InboxLines).reversed().forEach { m -> style.addLine(listOfNotNull(m.title, m.text).joinToString(" · ")) }
      val title = config.labels.digestTitle(queue.size)
      val summary = NotificationCompat.Builder(context, FluidNotifyChannel.DigestId)
        .setSmallIcon(smallIcon())
        .setContentTitle(title)
        .setContentText(queue.last().title)
        .setStyle(style.setBigContentTitle(title))
        .setGroup(DigestGroup)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        .setAutoCancel(true)
        .setContentIntent(openIntent(null, "digest"))
        .build()
      poster.notify(DigestTag, SummaryNotificationId, summary)
    }
  }

  /** Arma (o toglie) l'allarme del prossimo riepilogo. */
  internal fun armDigest() {
    val memory = store.read()
    val am = context.getSystemService(AlarmManager::class.java)
    val pi = PendingIntent.getBroadcast(
      context, 0,
      Intent(FluidNotifyReceiver.ActionDigest).setClass(context, FluidNotifyReceiver::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    if (!memory.digestEnabled || memory.digestQueue.isEmpty()) {
      am.cancel(pi)
    } else {
      val next = fluidDigestNext(memory.digestTimes, wallNow(), zone())
      am.setWindow(AlarmManager.RTC_WAKEUP, next, FluidNotifyProfile.InexactWindowMs, pi)
    }
    updateRestoreReceiver()
  }

  // --- i promemoria ---------------------------------------------------------------------------------------------------

  internal fun showReminder(reminder: FluidStoredReminder, scheduledMillis: Long) {
    val content = reminder.content
    val channelId = content.channel?.takeIf { channel(it) != null } ?: FluidNotifyChannel.RemindersId
    val channel = channel(channelId)!!
    if (!canPost(channelId)) {
      blocked(channelId)
      return
    }
    val builder = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(config.smallIcon.takeIf { it != 0 } ?: R.drawable.fluid_notify_reminder)
      .setContentTitle(content.title)
      .setContentText(content.text)
      .setWhen(scheduledMillis)
      .setShowWhen(true)
      .setAutoCancel(true)
      .setCategory(channel.category ?: NotificationCompat.CATEGORY_REMINDER)
      .setPriority(channel.importance.priority)
      .setContentIntent(openIntent(content.open, "reminder:${reminder.id}"))
    config.color?.let { builder.setColor(it) }
    content.thread?.let { builder.setGroup(ThreadGroupPrefix + it) }
    content.actions.take(3).forEach { action ->
      val pi = if (action.opensApp) {
        openIntent(content.open, "reminder:${reminder.id}:${action.key}", eventId = "reminder:${reminder.id}", eventKey = action.key)
      } else {
        messagePendingIntent(FluidNotifyReceiver.ActionReminderAction, reminder.id, action.key, mutable = false)
      }
      builder.addAction(NotificationCompat.Action.Builder(0, action.label, pi).build())
    }
    val tag = ReminderTagPrefix + reminder.id
    gate.submit("reminder/${reminder.id}") { poster.notify(tag, ReminderNotificationId, builder.build()) }
  }

  internal fun cancelReminderNotification(id: String) = onMain {
    gate.cancel("reminder/$id")
    poster.cancel(ReminderTagPrefix + id, ReminderNotificationId)
  }

  internal fun remindersChanged() {
    updateRestoreReceiver()
    centerImpl.refreshReminders()
  }

  private fun updateRestoreReceiver() {
    val memory = store.read()
    fluidSetRestoreReceiver(context, memory.reminders.isNotEmpty() || (memory.digestEnabled && memory.digestQueue.isNotEmpty()))
  }

  // --- gli eventi e i tocchi ------------------------------------------------------------------------------------------

  internal fun dispatch(event: FluidNotifyEvent) = onMain { config.onEvent(event) }

  internal fun onMessageAction(id: String, key: String) {
    dispatch(FluidNotifyEvent.Action(id, key))
    cancel(id)
  }

  internal fun onReply(id: String, text: String) {
    dispatch(FluidNotifyEvent.Reply(id, text))
    // Dopo una risposta la notifica se ne va, come su iOS: altrimenti Android la lascia con la rotella che gira.
    cancel(id)
  }

  /** Un tentativo di pubblicare con le notifiche spente: il centro lo rilegge (la pagina «Le notifiche non arrivano?»). */
  internal fun blocked(channelId: String) {
    centerImpl.refreshAll()
  }

  // --- gli intent -----------------------------------------------------------------------------------------------------

  /**
   * L'apertura al tocco: l'indirizzo [open] dentro l'app (`ACTION_VIEW` col pacchetto dell'app), o l'attività di avvio.
   * [eventId] e [eventKey] portano un tasto che apre l'app fino a [handleIntent]. [unique] distingue i `PendingIntent`.
   */
  internal fun openIntent(open: String?, unique: String, eventId: String? = null, eventKey: String? = null): PendingIntent {
    val intent = when {
      open != null -> Intent(Intent.ACTION_VIEW, Uri.parse(open)).setPackage(context.packageName)
      config.launchActivity != null -> Intent(context, config.launchActivity)
      else -> context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(Intent.ACTION_MAIN).setPackage(context.packageName)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    intent.identifier = unique
    if (eventId != null) {
      intent.putExtra(FluidNotifyReceiver.ExtraOpenMessage, eventId)
      intent.putExtra(FluidNotifyReceiver.ExtraKey, eventKey)
    }
    return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  private fun messagePendingIntent(action: String, id: String, key: String, mutable: Boolean): PendingIntent {
    val intent = Intent(action).setClass(context, FluidNotifyReceiver::class.java)
      .setData(Uri.parse("fluid-notify://msg/" + Uri.encode(id) + "/" + Uri.encode(key)))
      .putExtra(FluidNotifyReceiver.ExtraId, id)
      .putExtra(FluidNotifyReceiver.ExtraKey, key)
    // La risposta rapida vuole un PendingIntent mutabile: Android ci scrive dentro il testo.
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
    return PendingIntent.getBroadcast(context, 0, intent, flags)
  }

  // --- servizi --------------------------------------------------------------------------------------------------------

  internal fun notificationManager(): NotificationManager = context.getSystemService(NotificationManager::class.java)

  internal fun active(): List<StatusBarNotification> = poster.active()

  private fun smallIcon(): Int = config.smallIcon.takeIf { it != 0 } ?: R.drawable.fluid_notify_reminder

  /** Esegue [block] sul thread principale: subito se ci siamo già. */
  internal fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
  }

  companion object {
    internal const val MessageTagPrefix = "fluid.msg/"
    internal const val SummaryTagPrefix = "fluid.sum/"
    internal const val ReminderTagPrefix = "fluid.rem/"
    internal const val ThreadGroupPrefix = "fluid.thread/"
    internal const val DigestGroup = "fluid.digest"
    internal const val DigestTag = "fluid.digest/summary"
    internal const val MessageNotificationId = 0x5F1E
    internal const val SummaryNotificationId = 0x5F1F
    internal const val ReminderNotificationId = 0x5F20
    internal const val ReplyKey = "fluid.reply"
    private const val LongTextChars = 60
    private const val InboxLines = 5

    @Volatile
    private var instance: FluidNotify? = null

    /**
     * Installa engine-notify con [config] (dall'`Application.onCreate`): crea i canali, rimette promemoria e riepilogo, e
     * comincia a seguire l'app dentro e fuori. Una seconda chiamata cambia la configurazione.
     */
    @JvmStatic
    fun install(context: Context, config: FluidNotifyConfig): FluidNotify = synchronized(this) {
      val current = instance
      // Un'Application nuova vuol dire un processo nuovo (nelle prove Robolectric, una prova nuova): si riparte da capo.
      if (current != null && current.context !== context.applicationContext) reset()
      if (current != null && instance != null) {
        current.config = config
        current.ensureChannels()
        return current
      }
      FluidNotify(context, config).also {
        instance = it
        it.start()
      }
    }

    /** engine-notify dell'app; se l'app non l'ha installato, con la configurazione di serie. */
    @JvmStatic
    fun get(context: Context): FluidNotify {
      val current = instance
      if (current != null && current.context === context.applicationContext) return current
      return install(context, current?.config ?: FluidNotifyConfig())
    }

    internal fun peek(): FluidNotify? = instance

    internal fun reset() {
      instance?.let { it.handler.removeCallbacksAndMessages(null) }
      instance = null
    }
  }
}

/**
 * Il prossimo istante del riepilogo dopo [nowMillis]: il primo orario della lista ([times], minuti dalla mezzanotte) che
 * viene dopo, oggi o domani, nella [zone]. Pura.
 */
internal fun fluidDigestNext(times: List<Int>, nowMillis: Long, zone: ZoneId): Long {
  val now = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
  val sorted = times.map { it.coerceIn(0, 24 * 60 - 1) }.distinct().sorted().ifEmpty { listOf(18 * 60) }
  for (dayOffset in 0..1) {
    val day = now.toLocalDate().plusDays(dayOffset.toLong())
    for (minutes in sorted) {
      val at = java.time.ZonedDateTime.of(day, java.time.LocalTime.of(minutes / 60, minutes % 60), zone)
      if (at.toInstant().toEpochMilli() > nowMillis) return at.toInstant().toEpochMilli()
    }
  }
  return nowMillis + 24L * 60 * 60 * 1000
}

/** Dove l'engine pubblica: il NotificationManager vero, o uno finto nelle prove. */
internal interface FluidNotifyPoster {
  fun notify(tag: String?, id: Int, notification: Notification)
  fun cancel(tag: String?, id: Int)
  fun active(): List<StatusBarNotification>

  class System(private val context: Context) : FluidNotifyPoster {
    private val manager: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    /** Quante pubblicazioni: per le prove del ritmo. */
    var count: Int = 0
      private set

    override fun notify(tag: String?, id: Int, notification: Notification) {
      count++
      runCatching { manager.notify(tag, id, notification) }
    }

    override fun cancel(tag: String?, id: Int) {
      runCatching { manager.cancel(tag, id) }
    }

    override fun active(): List<StatusBarNotification> =
      runCatching { manager.activeNotifications.toList() }.getOrDefault(emptyList())
  }
}

/** I ganci delle prove, usati da `FluidNotifyTesting`. */
internal object FluidNotifyTestHooks {
  fun reset() = FluidNotify.reset()

  fun setOutside(notify: FluidNotify, outside: Boolean) = notify.foreground.force(outside)

  fun postCount(notify: FluidNotify): Int = notify.gate.posted
}
