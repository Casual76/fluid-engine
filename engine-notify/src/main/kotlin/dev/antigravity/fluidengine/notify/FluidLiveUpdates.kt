package dev.antigravity.fluidengine.notify

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdateSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// F8.19 · `attivita-fuori-dall-app`: le attività vive fuori dall'app. Si pubblica una volta ([publish]) e la notifica si
// aggiorna da sola, al ritmo giusto; alla fine ([finish]) resta l'esito per quattro ore, come una Live Activity finita.
//
// Due modi:
// - **diretto** ([publish], [foreground]): la notifica si vede sempre. È il caso di un servizio in primo piano (il viaggio
//   di Fluid Transit, la conversione di Universal Converter): Android vuole la sua notifica finché il servizio lavora.
// - **specchio della pillola** ([whenOutside]): la notifica si vede solo quando l'app non è in primo piano. Dentro c'è la
//   pillola di attività (engine-ui, `FluidActivityHostState.mirrorOutside`); uscendo diventa notifica, rientrando torna
//   pillola. L'esito di un'attività finita mentre l'app era fuori resta nella notifica.
//
// Le regole dei Live Update di Android 16 (developer.android.com, «Live Updates», fiducia alta) le applica il piano
// ([fluidLivePlan]); qui si rispettano le altre due:
// - «Don't repost Live Updates that the user dismissed»: una notifica tolta dall'utente non torna finché l'attività dura
//   (`setDeleteIntent`); solo un esito non riuscito la riporta, perché va saputo;
// - il ritmo: ogni pubblicazione passa dalla porta di [FluidNotifyGate], e un aggiornamento del solo avanzamento aspetta
//   almeno [FluidNotifyProfile.ProgressGapMs] dal precedente.

/**
 * Le attività vive dell'app fuori dall'app: il pubblicatore delle notifiche vive (Live Update su Android 16, notifica con
 * l'avanzamento prima). Si prende con `FluidNotify.get(context).liveUpdates`.
 *
 * ```kotlin
 * val live = FluidNotify.get(context).liveUpdates
 * live.publish(FluidLiveUpdate("video", FluidLiveKind.Transfer, "Converto il video", progress = 0.4f))
 * live.finish("video", FluidLiveOutcome.succeeded("Video pronto"))
 * ```
 *
 * Come [FluidLiveUpdateSink] è il modo **diretto**: le notifiche si vedono sempre.
 */
class FluidLiveUpdates internal constructor(private val notify: FluidNotify) : FluidLiveUpdateSink {
  private class Entry(val id: String, var update: FluidLiveUpdate, var mirrored: Boolean) {
    var tag: String? = LiveTagPrefix + id
    var notificationId: Int = LiveNotificationId
    var bound: Boolean = false
    var posted: FluidLivePlan? = null
    var postedAt: Long = Long.MIN_VALUE
    var shown: Boolean = false
    var dismissed: Boolean = false
    var unfollowed: Boolean = false
    var outcome: FluidLiveOutcome? = null
  }

  private val entries = LinkedHashMap<String, Entry>()
  private val always = MutableStateFlow(true)
  private val running = MutableStateFlow<List<FluidLiveUpdate>>(emptyList())

  /** Il modo diretto: le notifiche si vedono sempre. */
  override val outside: StateFlow<Boolean> = always.asStateFlow()

  override val sampleMillis: Long get() = FluidNotifyProfile.ProgressGapMs

  /** Le attività in corso fuori dall'app (dirette e specchiate), nell'ordine d'arrivo. */
  val active: StateFlow<List<FluidLiveUpdate>> = running.asStateFlow()

  /**
   * Lo specchio della pillola: le attività pubblicate qui si vedono come notifica **solo quando l'app non è in primo
   * piano**. Si passa a `FluidActivityHostState.mirrorOutside` di engine-ui.
   */
  val whenOutside: FluidLiveUpdateSink = object : FluidLiveUpdateSink {
    override fun publish(update: FluidLiveUpdate) = this@FluidLiveUpdates.publish(update, mirrored = true)
    override fun finish(id: String, outcome: FluidLiveOutcome) = this@FluidLiveUpdates.finish(id, outcome)
    override fun remove(id: String) = this@FluidLiveUpdates.remove(id)
    override val outside: StateFlow<Boolean> get() = notify.foreground.outside
    override val sampleMillis: Long get() = FluidNotifyProfile.ProgressGapMs
  }

  /** Pubblica [update] (modo diretto), o lo aggiorna se c'è già un'attività con lo stesso `id`. */
  override fun publish(update: FluidLiveUpdate) = publish(update, mirrored = false)

  /**
   * La notifica di [update] per un **servizio in primo piano**: da passare a `startForeground(notificationId, …)`. Da qui
   * in poi [publish], [finish] e [remove] con lo stesso `id` aggiornano quella notifica (la stessa coppia `null`,
   * [notificationId]). Alla fine chiama [finish] e poi `stopForeground(STOP_FOREGROUND_REMOVE)`: l'esito sta in una
   * notifica a parte, che resta.
   */
  fun foreground(update: FluidLiveUpdate, notificationId: Int): Notification {
    val plan = fluidLivePlan(update, notify.sdk, notify.elapsedNow(), notify.wallNow(), notify.config.labels)!!
    notify.onMain {
      val entry = entries[update.id]?.takeIf { it.outcome == null }
        ?: Entry(update.id, update, mirrored = false).also { entries[update.id] = it }
      entry.update = update
      entry.mirrored = false
      entry.bound = true
      entry.tag = null
      entry.notificationId = notificationId
      entry.dismissed = false
      entry.posted = plan
      entry.postedAt = notify.elapsedNow()
      entry.shown = true
      publishRunning()
    }
    return build(update.id, update, plan)
  }

  override fun finish(id: String, outcome: FluidLiveOutcome) = notify.onMain {
    val entry = entries[id] ?: return@onMain
    notify.gate.cancel(gateKey(id))
    entry.outcome = outcome
    val inside = entry.mirrored && !notify.foreground.outside.value
    when {
      outcome.silent -> drop(entry)
      // Dentro l'app l'esito lo dice la pillola.
      inside -> drop(entry)
      // Tolta dall'utente: niente più, salvo un esito non riuscito, che va saputo.
      // Smessa di seguire: niente, nemmeno l'errore (l'utente non la vuole più vedere).
      entry.dismissed && (!outcome.failed || entry.unfollowed) -> drop(entry)
      else -> {
        val plan = fluidLivePlan(entry.update, notify.sdk, notify.elapsedNow(), notify.wallNow(), notify.config.labels, outcome)
        entries.remove(id)
        publishRunning()
        if (plan == null) {
          cancel(entry)
          return@onMain
        }
        // L'esito di un'attività legata a un servizio sta in una notifica a parte: stopForeground la toglierebbe.
        if (entry.bound) cancel(entry)
        val tag = LiveTagPrefix + id
        notify.gate.submit(gateKey(id)) {
          notify.poster.notify(tag, LiveNotificationId, build(id, entry.update, plan))
        }
      }
    }
  }

  override fun remove(id: String) = notify.onMain {
    val entry = entries[id] ?: return@onMain
    notify.gate.cancel(gateKey(id))
    drop(entry)
  }

  /** Vero se Android 16 lascia promuovere le notifiche dell'app (l'utente non ha spento i Live Update). */
  fun canPromote(): Boolean = notify.sdk >= 36 && runCatching {
    notify.notificationManager().canPostPromotedNotifications()
  }.getOrDefault(false)

  /**
   * La notifica che [update] avrebbe adesso, senza pubblicarla: per un'app che la vuole mostrare a modo suo. Null per
   * un'attività finita in silenzio.
   */
  fun build(update: FluidLiveUpdate, outcome: FluidLiveOutcome? = null): Notification? {
    val plan = fluidLivePlan(update, notify.sdk, notify.elapsedNow(), notify.wallNow(), notify.config.labels, outcome) ?: return null
    return build(update.id, update, plan)
  }

  // --- dentro ---------------------------------------------------------------------------------------------------------

  private fun publish(update: FluidLiveUpdate, mirrored: Boolean) = notify.onMain {
    val old = entries[update.id]
    val entry = if (old == null || old.outcome != null) {
      Entry(update.id, update, mirrored).also { entries[update.id] = it }
    } else {
      old.also {
        it.update = update
        if (!it.bound) it.mirrored = mirrored
      }
    }
    publishRunning()
    schedule(entry)
  }

  private fun visible(entry: Entry): Boolean =
    !entry.dismissed && entry.outcome == null && (!entry.mirrored || notify.foreground.outside.value)

  /** Chiede alla porta di pubblicare l'attività quando il ritmo lo permette; la porta tiene solo l'ultimo stato. */
  private fun schedule(entry: Entry) {
    if (!visible(entry)) {
      notify.gate.cancel(gateKey(entry.id))
      if (entry.shown && !entry.bound) cancel(entry)
      return
    }
    if (!notify.canPost(FluidNotifyChannel.LiveId)) {
      notify.blocked(FluidNotifyChannel.LiveId)
      return
    }
    val plan = fluidLivePlan(entry.update, notify.sdk, notify.elapsedNow(), notify.wallNow(), notify.config.labels) ?: return
    if (plan == entry.posted && entry.shown) return
    val notBefore = if (entry.shown && plan.onlyProgressDiffers(entry.posted)) {
      entry.postedAt + FluidNotifyProfile.ProgressGapMs
    } else {
      notify.elapsedNow()
    }
    notify.gate.submit(gateKey(entry.id), notBefore) { post(entry) }
  }

  /** Pubblica lo stato di adesso di [entry] (la porta l'ha lasciata passare). */
  private fun post(entry: Entry) {
    if (entries[entry.id] !== entry || !visible(entry)) return
    val plan = fluidLivePlan(entry.update, notify.sdk, notify.elapsedNow(), notify.wallNow(), notify.config.labels) ?: return
    if (plan == entry.posted && entry.shown) return
    notify.poster.notify(entry.tag, entry.notificationId, build(entry.id, entry.update, plan))
    entry.posted = plan
    entry.postedAt = notify.elapsedNow()
    entry.shown = true
  }

  private fun drop(entry: Entry) {
    entries.remove(entry.id)
    if (entry.shown) cancel(entry)
    publishRunning()
  }

  private fun cancel(entry: Entry) {
    notify.poster.cancel(entry.tag, entry.notificationId)
    entry.shown = false
    entry.posted = null
  }

  private fun publishRunning() {
    running.value = entries.values.filter { it.outcome == null }.map { it.update }
  }

  /** L'app è entrata (false) o uscita (true) dal primo piano: le attività specchiate cambiano posto. */
  internal fun onOutsideChanged(outside: Boolean) {
    entries.values.toList().filter { it.mirrored }.forEach { entry ->
      if (outside) {
        schedule(entry)
      } else if (entry.shown && entry.outcome == null) {
        notify.gate.cancel(gateKey(entry.id))
        cancel(entry)
      }
    }
  }

  /** L'utente ha tolto la notifica di [id] (il `deleteIntent`). */
  internal fun onDismissed(id: String) {
    val entry = entries[id] ?: return
    entry.dismissed = true
    entry.shown = false
    entry.posted = null
    notify.gate.cancel(gateKey(id))
    notify.dispatch(FluidNotifyEvent.LiveDismissed(id))
  }

  /** Il tasto [key] della notifica di [id], pubblicata nel processo [session]. */
  internal fun onAction(id: String, key: String, session: Long) {
    val entry = entries[id]
    if (entry == null || session != notify.session) {
      // Una notifica di un processo morto, o di un'attività già finita: era vecchia, se ne va.
      notify.poster.cancel(LiveTagPrefix + id, LiveNotificationId)
      return
    }
    if (key == FluidLiveUnfollowKey) {
      // Come una notifica tolta dall'utente: non torna finché l'app non finisce o toglie l'attività (visto sul tablet il
      // 25/11/2026: la scena continuava a pubblicare il viaggio e la notifica tornava a ogni fermata).
      notify.gate.cancel(gateKey(id))
      entry.dismissed = true
      entry.unfollowed = true
      cancel(entry)
      notify.dispatch(FluidNotifyEvent.Unfollowed(id))
      return
    }
    entry.update.actions.firstOrNull { it.key == key }?.onClick?.invoke()
  }

  // --- la notifica ----------------------------------------------------------------------------------------------------

  private fun build(id: String, update: FluidLiveUpdate, plan: FluidLivePlan): Notification {
    val context = notify.context
    val icon = liveIcon(plan.icon)
    val builder = NotificationCompat.Builder(context, FluidNotifyChannel.LiveId)
      .setSmallIcon(icon)
      .setContentTitle(plan.title)
      .setContentText(plan.text)
      .setOngoing(plan.ongoing)
      .setOnlyAlertOnce(true)
      .setCategory(plan.category)
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setContentIntent(notify.openIntent(update.open, "live:$id"))
      .setDeleteIntent(livePendingIntent(FluidNotifyReceiver.ActionLiveDismissed, id, null))
      .setShowWhen(plan.chronometer != null)
      .setRequestPromotedOngoing(plan.requestPromotion)
    notify.config.color?.let { builder.setColor(it) }
    plan.chip?.let { builder.setShortCriticalText(it) }
    plan.chronometer?.let { chrono ->
      builder.setWhen(chrono.whenWallMillis).setUsesChronometer(true).setChronometerCountDown(chrono.countDown)
    }
    plan.bar?.let { bar ->
      val style = NotificationCompat.ProgressStyle()
        .setProgressIndeterminate(bar.indeterminate)
        .setStyledByProgress(true)
      if (bar.stages.isNotEmpty()) {
        style.setProgressSegments(
          bar.stages.map { stage ->
            NotificationCompat.ProgressStyle.Segment(stage.length).also { s -> stage.color?.let { s.setColor(it) } }
          },
        )
      }
      if (bar.points.isNotEmpty()) {
        style.setProgressPoints(
          bar.points.map { point ->
            NotificationCompat.ProgressStyle.Point(point.position).also { p -> point.color?.let { p.setColor(it) } }
          },
        )
      }
      if (!bar.indeterminate) style.setProgress(bar.progress)
      // Il segno che avanza sulla barra: il bus sul viaggio.
      if (update.kind == FluidLiveKind.Navigation) style.setProgressTrackerIcon(IconCompat.createWithResource(context, icon))
      builder.setStyle(style)
    }
    plan.actions.forEach { action ->
      builder.addAction(
        NotificationCompat.Action.Builder(0, action.label, livePendingIntent(FluidNotifyReceiver.ActionLiveAction, id, action.key))
          .build(),
      )
    }
    if (plan.finished) {
      builder.setAutoCancel(true)
      plan.timeoutMillis?.let { builder.setTimeoutAfter(it) }
    }
    val notification = builder.build()
    // Android 16 ha cambiato regola fra la prima uscita e la QPR1. La documentazione di oggi (16 QPR1, API 36.1) vuole la
    // richiesta esplicita e **niente** colore pieno; la prima uscita (36.0, il codice di `hasPromotableCharacteristics`
    // letto nell'immagine di Robolectric 16-13921718, 25/11/2026) promuove invece solo una notifica col colore chiesto.
    // Decide il telefono: se la notifica scritta come vuole la documentazione non è promuovibile per lui, si prova la
    // regola della prima uscita. Su un telefono 36.1 il primo tentativo basta e il colore non si chiede mai.
    if (plan.requestPromotion && Build.VERSION.SDK_INT >= 36 && !notification.hasPromotableCharacteristics()) {
      builder.setColorized(true).setColor(notify.config.color ?: EarlyPromotionColor)
      val early = builder.build()
      if (early.hasPromotableCharacteristics()) return early
    }
    return notification
  }

  private fun liveIcon(name: String): Int = when (name) {
    "done" -> R.drawable.fluid_notify_done
    "failed" -> R.drawable.fluid_notify_failed
    FluidLiveKind.Sync.name -> R.drawable.fluid_notify_sync
    FluidLiveKind.Transfer.name -> R.drawable.fluid_notify_transfer
    FluidLiveKind.Timer.name -> R.drawable.fluid_notify_timer
    FluidLiveKind.Assistant.name -> R.drawable.fluid_notify_assistant
    FluidLiveKind.Recording.name -> R.drawable.fluid_notify_recording
    FluidLiveKind.Navigation.name -> R.drawable.fluid_notify_navigation
    else -> notify.config.smallIcon.takeIf { it != 0 } ?: R.drawable.fluid_notify_reminder
  }

  private fun livePendingIntent(action: String, id: String, key: String?): PendingIntent {
    val intent = Intent(action).setClass(notify.context, FluidNotifyReceiver::class.java)
      .setData(Uri.parse("fluid-notify://live/" + Uri.encode(id) + "/" + Uri.encode(key ?: "")))
      .putExtra(FluidNotifyReceiver.ExtraId, id)
      .putExtra(FluidNotifyReceiver.ExtraKey, key)
      .putExtra(FluidNotifyReceiver.ExtraSession, notify.session)
    return PendingIntent.getBroadcast(notify.context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  }

  private fun gateKey(id: String): String = "live/$id"

  internal companion object {
    const val LiveTagPrefix: String = "fluid.live/"
    const val LiveNotificationId: Int = 0x5F1D

    /**
     * Il colore della regola della prima uscita di Android 16, quando l'app non ne ha dato uno: l'azzurro di sistema di
     * Apple (`systemBlue`, #007AFF in chiaro, HIG «Color», fiducia alta). Android lo mostra pieno solo per un servizio in
     * primo piano.
     */
    const val EarlyPromotionColor: Int = 0xFF007AFF.toInt()

    /** Il canale delle attività: in silenzio, senza pallino sull'icona (un'attività non è una cosa da leggere). */
    fun channel(labels: FluidNotifyLabels): FluidNotifyChannel = FluidNotifyChannel(
      id = FluidNotifyChannel.LiveId,
      name = labels.liveChannel,
      description = labels.liveChannelDescription,
      importance = FluidNotifyImportance.Quiet,
      digestible = false,
      badge = false,
      category = NotificationCompat.CATEGORY_PROGRESS,
    )
  }
}
