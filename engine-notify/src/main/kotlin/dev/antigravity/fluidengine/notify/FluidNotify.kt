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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
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
    /**
     * Pubblicata, o in fila per il ritmo (al più quattro al secondo per l'app intera). La fila sta in memoria: se il
     * processo muore prima del suo turno (di solito meno di un secondo; con molte notifiche insieme qualche secondo), la
     * notifica non parte.
     */
    val Posted: FluidNotifyResult = FluidNotifyResult("posted")

    /** Messa da parte per il prossimo riepilogo, come ha scelto l'utente. */
    val InDigest: FluidNotifyResult = FluidNotifyResult("digest")

    /** Non mostrata: le notifiche dell'app sono spente (il permesso negato, o spente dall'utente). */
    val NotificationsOff: FluidNotifyResult = FluidNotifyResult("off")

    /**
     * Non mostrata: il canale è spento nelle impostazioni di Android, oppure non è dichiarato in
     * `FluidNotifyConfig.channels` (per esempio un canale tolto in un aggiornamento dell'app, arrivato da una notifica
     * push): l'app non cade.
     */
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

  /** L'ultima volta che ogni filo ha suonato (`elapsedRealtime`), per la raffica. Solo sul principale. */
  private val threadAlerts = HashMap<String, Long>()

  /**
   * Gli eventi arrivati prima che l'app installasse engine-notify (un processo fatto ripartire da un tocco, con l'install
   * in un'attività): aspettano il gestore vero invece di perdersi nel gestore vuoto di serie. Solo sul principale.
   */
  private val earlyEvents = ArrayList<FluidNotifyEvent>()

  /** Vero quando la configurazione l'ha data l'app ([install]), falso per quella di serie di [get]. */
  private var installedByApp = false

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
    // Una scrittura fallita o un file rovinato si vedono in «Le notifiche non arrivano?» (R2, n. 11).
    store.onTrouble = { handler.post { centerImpl.refreshAll() } }
    ensureChannels()
    // Le conversazioni di un processo di prima senza più notifiche escono dal menu dell'icona.
    tidyConversations()
    // Le attività specchiate seguono l'app dentro e fuori; al rientro si riprova ciò che aspettava le notifiche.
    foreground.onChange = { outside ->
      liveUpdates.onOutsideChanged(outside)
      if (!outside) retryWaiting()
    }
    reminders.restore()
    armDigest()
  }

  /**
   * Le notifiche sono (forse) tornate: la pagina si rilegge ([FluidNotifyCenter.refresh]) o l'app torna in primo piano.
   * Arriva ciò che aspettava: le attività vive non pubblicate e le notifiche del riepilogo rimaste da consegnare dopo
   * averlo spento (R2, due minori). I promemoria scaduti nel frattempo no: sono persi, segnati «non mostrato» (la scelta
   * di Alessio). Sul principale.
   */
  internal fun retryWaiting() {
    if (!notificationsAllowed()) return
    liveUpdates.retryBlocked()
    val memory = store.read()
    if (!memory.digestEnabled && memory.digestQueue.isNotEmpty()) deliverDigest()
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

  /**
   * Vero se il canale [channelId] è acceso. Un canale dichiarato che in Android non c'è (cancellato, o mai creato) si
   * ricrea: senza, Android scarterebbe la notifica in silenzio (R2). Un canale che non c'è e non è dichiarato: falso.
   */
  fun channelAllowed(channelId: String): Boolean {
    val nm = notificationManager()
    val existing = nm.getNotificationChannel(channelId)
      ?: if (channel(channelId) != null) runCatching { ensureChannels() }.let { nm.getNotificationChannel(channelId) } else null
    return existing != null && existing.importance != NotificationManager.IMPORTANCE_NONE
  }

  /** Vero se una notifica sul canale [channelId] si può mostrare adesso. */
  fun canPost(channelId: String): Boolean = notificationsAllowed() && channelAllowed(channelId)

  // --- le notifiche ---------------------------------------------------------------------------------------------------

  /**
   * Pubblica [message], o la mette nel riepilogo se l'utente l'ha chiesto per il suo canale (e non è urgente). Le
   * notifiche dello stesso filo si raccolgono; quelle che arrivano in raffica dopo la prima arrivano in silenzio.
   *
   * Un canale non dichiarato in `FluidNotifyConfig.channels` dà [FluidNotifyResult.ChannelOff] (prima faceva cadere
   * l'app). Si può chiamare da un filo qualsiasi: la notifica parte dal principale.
   */
  fun post(message: FluidNotifyMessage): FluidNotifyResult {
    val channel = channel(message.channel) ?: return FluidNotifyResult.ChannelOff
    if (!notificationsAllowed()) return FluidNotifyResult.NotificationsOff.also { blocked(channel.id) }
    if (!channelAllowed(channel.id)) return FluidNotifyResult.ChannelOff.also { blocked(channel.id) }
    val memory = store.read()
    val urgent = message.urgent ?: !channel.digestible
    if (memory.digestEnabled && !urgent && channel.digestible && channel.id in memory.digestChannels) {
      // Al più [FluidNotifyProfile.DigestQueueSize], le più recenti: Android non ne mostra di più per un'app.
      store.update {
        it.copy(digestQueue = (it.digestQueue.filterNot { q -> q.id == message.id } + message).takeLast(FluidNotifyProfile.DigestQueueSize))
      }
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
    tidyConversations()
  }

  /** Toglie tutte le notifiche del filo [thread], anche quelle che aspettano il riepilogo. */
  fun cancelThread(thread: String) = onMain {
    val key = ThreadGroupPrefix + thread
    gate.cancel("sum/$key")
    active().filter { it.notification.group == key }.forEach { poster.cancel(it.tag, it.id) }
    store.update { it.copy(digestQueue = it.digestQueue.filterNot { q -> q.thread == thread }) }
    centerImpl.refreshSettings()
    tidyConversations()
  }

  // --- le conversazioni -----------------------------------------------------------------------------------------------
  //
  // La scelta di Alessio (R2, domanda 2, 26/11/2026): una notifica con `person` è una conversazione vera di Android. Android
  // le vuole legate a una scorciatoia dinamica di lunga vita con la `Person` (developer.android.com, «People and
  // conversations», fiducia alta): così entrano nella sezione «Conversazioni» e l'utente le può rendere prioritarie, anche
  // per Non disturbare. Le scorciatoie compaiono anche nel menu dell'icona dell'app, quindi restano poche e pulite: al più
  // `getMaxShortcutCountPerActivity` in tutto (quelle dell'app non si toccano mai; esce la conversazione usata meno di
  // recente), e una conversazione senza più notifiche a schermo esce dal menu. Esce solo la scorciatoia dinamica: la copia
  // che Android tiene per una conversazione (`removeDynamicShortcuts`, non `removeLongLivedShortcuts`) resta, così la scelta
  // «prioritaria» dell'utente non si perde fra un messaggio e l'altro.

  /** L'id della scorciatoia (e la chiave della `Person`) della conversazione con [person]. */
  private fun conversationId(person: String): String = ConversationShortcutPrefix + person.trim().lowercase()

  /**
   * Pubblica, o rinfresca, la scorciatoia della conversazione con [person] prima della notifica, e ne dà l'id; null se non
   * c'è posto (il menu è pieno di scorciatoie dell'app) o se Android la rifiuta: allora la notifica resta un messaggio.
   */
  private fun conversationShortcut(person: String, sender: Person): String? = runCatching {
    val id = conversationId(person)
    val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context)
    if (max <= 0) return null
    tidyConversations(keep = id)
    val dynamic = ShortcutManagerCompat.getDynamicShortcuts(context)
    val ours = dynamic.filter { it.id.startsWith(ConversationShortcutPrefix) }
    if (ours.none { it.id == id } && dynamic.size >= max) {
      // Il tetto: esce la conversazione usata meno di recente, mai una scorciatoia dell'app.
      val oldest = ours.minByOrNull { it.lastUsed() } ?: return null
      ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(oldest.id))
    }
    val stamp = maxOf(wallNow(), (ours.maxOfOrNull { it.lastUsed() } ?: 0L) + 1)
    val shortcut = ShortcutInfoCompat.Builder(context, id)
      .setShortLabel(person)
      .setLongLived(true)
      .setPerson(sender)
      .setCategories(setOf(ConversationCategory))
      .setIntent(conversationIntent())
      .setExtras(PersistableBundle().apply { putLong(ExtraLastUsed, stamp) })
      .build()
    if (!ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)) return null
    id
  }.getOrNull()

  /**
   * Toglie dal menu dell'icona le conversazioni senza più notifiche a schermo (lette, tolte dall'utente o dall'app),
   * tranne [keep]. Sul principale.
   */
  internal fun tidyConversations(keep: String? = null) {
    runCatching {
      val live = active().mapNotNull { it.notification.shortcutId }.toSet()
      val stale = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
        .filter { it.startsWith(ConversationShortcutPrefix) && it != keep && it !in live }
      if (stale.isNotEmpty()) ShortcutManagerCompat.removeDynamicShortcuts(context, stale)
    }
  }

  private fun ShortcutInfoCompat.lastUsed(): Long = extras?.getLong(ExtraLastUsed) ?: 0L

  /** Dove porta la scorciatoia dal menu dell'icona: l'app (Android vuole un'azione nell'`Intent`). */
  private fun conversationIntent(): Intent {
    val intent = config.launchActivity?.let { Intent(context, it) }
      ?: context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: Intent().setPackage(context.packageName)
    if (intent.action == null) intent.action = Intent.ACTION_MAIN
    return intent
  }

  /**
   * Quante notifiche dell'app contano nel pallino dell'icona: quelle a schermo dei canali col pallino, messaggi e
   * promemoria (i riassunti e le attività in corso no).
   */
  fun badgeCount(): Int = active().count { sbn ->
    val n = sbn.notification
    val tag = sbn.tag.orEmpty()
    (tag.startsWith(MessageTagPrefix) || tag.startsWith(ReminderTagPrefix)) && (n.flags and Notification.FLAG_GROUP_SUMMARY) == 0 &&
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
      val reminderId = id.removePrefix("reminder:")
      dispatch(FluidNotifyEvent.ReminderAction(reminderId, key))
      // Eseguito: la notifica se ne va, come col tasto in sottofondo (R2).
      cancelReminderNotification(reminderId)
    } else {
      dispatch(FluidNotifyEvent.Action(id, key))
      cancel(id)
    }
    return true
  }

  private fun show(message: FluidNotifyMessage, channel: FluidNotifyChannel, digest: Boolean) {
    val thread = when {
      digest -> DigestGroup
      message.thread != null -> ThreadGroupPrefix + message.thread
      else -> null
    }
    gate.submit("msg/${message.id}") {
      // La raffica, decisa al turno della porta (quando le notifiche di prima sono già a schermo): dopo la prima del filo,
      // le altre entro la finestra arrivano in silenzio, raccolte. Se l'utente ha già scartato tutto il filo, la
      // notifica dopo suona di nuovo (R2).
      val alert = when {
        digest -> false
        thread == null -> true
        else -> threadAlert(thread)
      }
      poster.notify(MessageTagPrefix + message.id, MessageNotificationId, buildMessage(message, channel, thread, alert))
      if (thread != null && !digest) summaryLater(thread)
    }
  }

  /** Vero se una notifica nuova del filo [thread] deve suonare; tiene [threadAlerts] corta (solo le raffiche vive). */
  private fun threadAlert(thread: String): Boolean {
    val now = elapsedNow()
    threadAlerts.entries.removeAll { now - it.value > FluidNotifyProfile.BurstWindowMs }
    val onScreen = active().any { it.notification.group == thread && it.tag?.startsWith(MessageTagPrefix) == true }
    val last = threadAlerts[thread]
    val alert = last == null || !onScreen
    if (alert) threadAlerts[thread] = now
    return alert
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
      val sender = Person.Builder().setName(message.person).setKey(conversationId(message.person)).build()
      // La conversazione vera: la scorciatoia prima della notifica, poi la notifica legata a lei (R2, domanda 2).
      conversationShortcut(message.person, sender)?.let { builder.setShortcutId(it) }
      // Chi la toglie avvisa l'engine: una conversazione senza più notifiche esce dal menu dell'icona.
      builder.setDeleteIntent(messagePendingIntent(FluidNotifyReceiver.ActionMessageDismissed, message.id, "", mutable = false))
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
      // Il segno per i riassunti: non devono ripetere ciò che la notifica nasconde (R2, n. 9).
      builder.addExtras(Bundle().apply { putBoolean(ExtraSensitive, true) })
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
    children.sortedByDescending { it.notification.`when` }.take(InboxLines).forEach { sbn -> style.addLine(summaryLine(sbn.notification)) }
    val summary = channel.summary(children.size)
    style.setBigContentTitle(summary)
    val builder = NotificationCompat.Builder(context, channel.id)
      .setSmallIcon(smallIcon())
      .setContentTitle(summary)
      .setContentText(if (newest.isSensitive()) publicTitle(newest) else newest.extras.getCharSequence(NotificationCompat.EXTRA_TITLE))
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
    if (children.any { it.notification.isSensitive() }) protect(builder, channel.id, channel.name, children.size)
    poster.notify(tag, SummaryNotificationId, builder.build())
  }

  // R2 (review `muse-f8-prime`, n. 9): un riassunto non ripete ciò che una notifica sensibile nasconde a telefono
  // bloccato. Le sue righe portano la versione pubblica di quella notifica (il nome del canale), e il riassunto ha la sua
  // versione pubblica, senza contenuto: vale anche se l'utente nasconde i contenuti di un canale solo.

  private fun Notification.isSensitive(): Boolean = extras.getBoolean(ExtraSensitive)

  /** Il titolo pubblico di una notifica sensibile: il nome del suo canale, come la sua versione pubblica. */
  private fun publicTitle(notification: Notification): String =
    channel(notification.channelId)?.name ?: config.labels.digestChannel

  private fun summaryLine(notification: Notification): CharSequence {
    if (notification.isSensitive()) return publicTitle(notification)
    val extras = notification.extras
    return listOfNotNull(extras.getCharSequence(NotificationCompat.EXTRA_TITLE), extras.getCharSequence(NotificationCompat.EXTRA_TEXT))
      .joinToString(" · ")
  }

  private fun protect(builder: NotificationCompat.Builder, channelId: String, title: String, count: Int) {
    builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
    builder.setPublicVersion(
      NotificationCompat.Builder(context, channelId).setSmallIcon(smallIcon()).setContentTitle(title).setNumber(count).build(),
    )
  }

  // --- il riepilogo ---------------------------------------------------------------------------------------------------

  /**
   * Il riepilogo è suonato: le notifiche messe da parte arrivano raccolte, con un riassunto che suona una volta.
   *
   * Dalla coda esce solo ciò che è stato pubblicato davvero, una notifica alla volta, al turno della porta del ritmo (R2,
   * review `muse-f8-prime`, n. 12: prima la coda si svuotava subito, e con le notifiche spente, o col processo morto a
   * metà, le attese erano perse). Con le notifiche (o il canale) spente restano in coda per il riepilogo dopo; una
   * notifica di un canale che l'app non dichiara più si toglie, perché non potrebbe uscire mai.
   */
  internal fun deliverDigest() = onMain {
    val queue = store.read().digestQueue.filter { channel(it.channel) != null }
    store.update { memory -> memory.copy(digestQueue = memory.digestQueue.filter { channel(it.channel) != null }) }
    armDigest()
    centerImpl.refreshSettings()
    if (queue.isEmpty() || !notificationsAllowed()) return@onMain
    val ready = queue.filter { channelAllowed(it.channel) }
    if (ready.isEmpty()) return@onMain
    ready.forEach { message ->
      val channel = channel(message.channel)!!
      gate.submit("msg/${message.id}") {
        poster.notify(MessageTagPrefix + message.id, MessageNotificationId, buildMessage(message, channel, DigestGroup, alert = false))
        // Uscita: via dalla coda (solo questa versione: una più nuova con lo stesso id arrivata nel frattempo resta).
        store.update { memory -> memory.copy(digestQueue = memory.digestQueue.filterNot { it == message }) }
      }
    }
    gate.submit("digest") {
      val style = NotificationCompat.InboxStyle()
      ready.takeLast(InboxLines).reversed().forEach { m ->
        style.addLine(if (m.sensitive) channel(m.channel)!!.name else listOfNotNull(m.title, m.text).joinToString(" · "))
      }
      val title = config.labels.digestTitle(ready.size)
      val last = ready.last()
      val builder = NotificationCompat.Builder(context, FluidNotifyChannel.DigestId)
        .setSmallIcon(smallIcon())
        .setContentTitle(title)
        .setContentText(if (last.sensitive) channel(last.channel)!!.name else last.title)
        .setStyle(style.setBigContentTitle(title))
        .setGroup(DigestGroup)
        .setGroupSummary(true)
        .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
        .setAutoCancel(true)
        .setContentIntent(openIntent(null, "digest"))
      if (ready.any { it.sensitive }) protect(builder, FluidNotifyChannel.DigestId, config.labels.digestChannel, ready.size)
      poster.notify(DigestTag, SummaryNotificationId, builder.build())
      // Tutto uscito: l'allarme del prossimo riepilogo serve solo se nel frattempo è arrivato altro.
      armDigest()
      centerImpl.refreshSettings()
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

  /**
   * Mostra il promemoria (al turno della porta del ritmo, sempre sul principale: `schedule` e `restore` si possono
   * chiamare da un filo di sottofondo, R2 n. 13). Falso se non si può mostrare: le notifiche o il canale sono spenti, e
   * il promemoria è perso, segnato «non mostrato» (R2 n. 7).
   */
  internal fun showReminder(reminder: FluidStoredReminder, scheduledMillis: Long): Boolean {
    val content = reminder.content
    val channelId = content.channel?.takeIf { channel(it) != null } ?: FluidNotifyChannel.RemindersId
    val channel = channel(channelId)!!
    if (!canPost(channelId)) {
      blocked(channelId)
      return false
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
    val notification = builder.build()
    onMain { gate.submit("reminder/${reminder.id}") { poster.notify(tag, ReminderNotificationId, notification) } }
    return true
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

  internal fun dispatch(event: FluidNotifyEvent) = onMain {
    if (installedByApp) {
      config.onEvent(event)
    } else if (earlyEvents.size < MaxEarlyEvents) {
      // L'app non ha ancora installato engine-notify: l'evento aspetta il suo gestore (R2).
      earlyEvents += event
    }
  }

  /** L'app ha installato engine-notify: gli eventi arrivati prima vanno al suo gestore. */
  private fun installedNow() = onMain {
    installedByApp = true
    val early = earlyEvents.toList()
    earlyEvents.clear()
    early.forEach { config.onEvent(it) }
  }

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
   * Un indirizzo `http`/`https` che nessuna attività dell'app apre va al browser; un altro indirizzo che l'app non apre,
   * all'attività di avvio (R2: prima il tocco non apriva niente). [eventId] e [eventKey] portano un tasto che apre l'app
   * fino a [handleIntent]. [unique] distingue i `PendingIntent`.
   */
  internal fun openIntent(open: String?, unique: String, eventId: String? = null, eventKey: String? = null): PendingIntent {
    val inApp = open?.let { Intent(Intent.ACTION_VIEW, Uri.parse(it)).setPackage(context.packageName) }
      ?.takeIf { runCatching { context.packageManager.resolveActivity(it, 0) != null }.getOrDefault(true) }
    val web = open?.takeIf { inApp == null && (it.startsWith("https://") || it.startsWith("http://")) }
    val intent = when {
      inApp != null -> inApp
      web != null -> Intent(Intent.ACTION_VIEW, Uri.parse(web))
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
    /** Il segno, negli extra di una notifica dell'engine, che il suo contenuto è sensibile (per i riassunti). */
    internal const val ExtraSensitive = "fluid.sensitive"
    /** Il prefisso delle scorciatoie delle conversazioni dell'engine: le altre sono dell'app, e non si toccano. */
    internal const val ConversationShortcutPrefix = "fluid.person/"
    /** Negli extra di una scorciatoia di conversazione: quando l'ha usata l'ultima notifica (per il tetto). */
    private const val ExtraLastUsed = "fluid.lastUsed"
    /** `ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION`. */
    private const val ConversationCategory = "android.shortcut.conversation"
    private const val LongTextChars = 60
    private const val InboxLines = 5

    /** Quanti eventi aspettano al più l'install dell'app: un'app che non installa mai non accumula senza fine. */
    private const val MaxEarlyEvents = 32

    @Volatile
    private var instance: FluidNotify? = null

    /**
     * Installa engine-notify con [config] (dall'`Application.onCreate`): crea i canali, rimette promemoria e riepilogo, e
     * comincia a seguire l'app dentro e fuori. Una seconda chiamata cambia la configurazione.
     */
    @JvmStatic
    fun install(context: Context, config: FluidNotifyConfig): FluidNotify = install(context, config, byApp = true)

    private fun install(context: Context, config: FluidNotifyConfig, byApp: Boolean): FluidNotify = synchronized(this) {
      val current = instance
      // Un'Application nuova vuol dire un processo nuovo (nelle prove Robolectric, una prova nuova): si riparte da capo.
      if (current != null && current.context !== context.applicationContext) reset()
      if (current != null && instance != null) {
        current.config = config
        current.ensureChannels()
        if (byApp) current.installedNow()
        return current
      }
      FluidNotify(context, config).also {
        instance = it
        it.installedByApp = byApp
        it.start()
      }
    }

    /**
     * engine-notify dell'app; se l'app non l'ha installato, con la configurazione di serie. Un tocco arrivato prima
     * dell'[install] (un processo fatto ripartire da una notifica, con l'install in un'attività) aspetta l'install: il
     * suo evento arriva allora al gestore dell'app, invece di perdersi.
     */
    @JvmStatic
    fun get(context: Context): FluidNotify {
      val current = instance
      if (current != null && current.context === context.applicationContext) return current
      return install(context, current?.config ?: FluidNotifyConfig(), byApp = current?.installedByApp ?: false)
    }

    internal fun peek(): FluidNotify? = instance

    internal fun reset() {
      instance?.let {
        it.handler.removeCallbacksAndMessages(null)
        // Le scritture già chieste finiscono su disco: il «processo nuovo» delle prove le ritrova.
        it.store.flush()
      }
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
