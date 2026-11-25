package dev.antigravity.fluidengine.transfer

import android.app.Notification
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import dev.antigravity.fluidengine.foundation.live.FluidLiveAction
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdateSink
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState

/**
 * **La notifica viva dei trasferimenti** (F8.20b): il punto d'aggancio [FluidTransferNotifications] riempito con
 * l'attività viva di `engine-notify` (F8.19). Il lavoro di WorkManager, che non ha interfaccia, mostra il trasferimento
 * come `FluidLiveKind.Transfer`: chiesto dall'utente, quindi su Android 16 diventa un **Live Update** (il gettone nella
 * barra di stato, la barra, la schermata di blocco), prima una notifica con l'avanzamento. «Pausa» e «Annulla» sono i
 * tasti della notifica; alla fine resta l'esito («Pampa Notes 2.4 scaricato», o non riuscito col motivo).
 *
 * Non dipende da `engine-notify`: prende il pubblicatore dal modello puro di `engine-foundation` e la notifica del servizio
 * da chi la costruisce. Con `engine-notify`, nell'`Application` (che e' un [FluidTransferHost], cosi' il lavoro che
 * riparte a processo morto trova la stessa coda):
 *
 * ```kotlin
 * override val fluidTransfers by lazy {
 *   val live = FluidNotify.get(this).liveUpdates
 *   FluidTransfers.open(this, notifications = LiveTransferNotifications(live, live::foreground))
 * }
 * ```
 *
 * La pillola dentro l'app resta quella di `FluidTransferActivity` (engine-ui); **non** si specchia anche fuori
 * (`mirrorOutside`) con lo stesso trasferimento, se no fuori le notifiche sarebbero due.
 *
 * @param live dove pubblicare e chiudere l'attività: `FluidNotify.get(context).liveUpdates`.
 * @param foreground la notifica del servizio in primo piano per un'attività: `FluidLiveUpdates.foreground`.
 * @param phrases le parole, di serie italiane.
 * @param open dove porta il tocco sulla notifica (un indirizzo dell'app), o null per aprire l'app.
 * @param controls chi riceve «Pausa» e «Annulla»: di serie la coda dell'app ([FluidTransfers.from]).
 */
class LiveTransferNotifications(
  private val live: FluidLiveUpdateSink,
  private val foreground: (FluidLiveUpdate, Int) -> Notification,
  private val phrases: TransferNotificationPhrases = TransferNotificationPhrases(),
  private val open: String? = null,
  private val controls: (Context) -> FluidTransferControls = { FluidTransfers.from(it) },
) : FluidTransferNotifications {
  /** Quando e' cominciato il giro di adesso: l'esito conta solo quelli finiti da li' in poi. */
  @Volatile private var roundStartedAt: Long? = null

  override fun foregroundInfo(context: Context, overview: FluidTransferOverview): ForegroundInfo {
    if (roundStartedAt == null) roundStartedAt = System.currentTimeMillis()
    val update = overview.toLiveUpdate(controls(context), phrases, open)
    val notification = foreground(update, NotificationId)
    return if (Build.VERSION.SDK_INT >= 29) {
      ForegroundInfo(NotificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
      ForegroundInfo(NotificationId, notification)
    }
  }

  override fun finished(context: Context, overview: FluidTransferOverview) {
    val since = roundStartedAt ?: return
    roundStartedAt = null
    live.finish(UpdateId, fluidTransferOutcome(overview, since, phrases))
  }

  companion object {
    /** L'id dell'attività viva dei trasferimenti: lo stesso della pillola (`FluidTransferActivityId` di engine-ui). */
    const val UpdateId: String = "transfers"

    /** L'id della notifica del servizio in primo piano, lo stesso della notifica semplice. */
    const val NotificationId: Int = SimpleTransferNotifications.NotificationId
  }
}

/**
 * La coda come attività viva: il trasferimento in corso (o «Pampa Notes 2.4 e altri 2»), «12,4 MB di 48 MB · ancora 17 s»
 * o il perche' dell'attesa, l'avanzamento di tutto quello che e' in giro, «Pausa» e «Annulla» su quello in corso. Pura.
 */
fun FluidTransferOverview.toLiveUpdate(
  controls: FluidTransferControls,
  phrases: TransferNotificationPhrases = TransferNotificationPhrases(),
  open: String? = null,
): FluidLiveUpdate {
  val item = running ?: active.firstOrNull()
  val count = active.size
  val title = when {
    item == null -> phrases.title
    count > 1 -> "${item.title} ${phrases.andOthers(count - 1)}"
    else -> item.title
  }
  val text = when {
    item == null -> null
    item.state == FluidTransferState.Waiting -> phrases.conditions.waitingFor(item.waitingFor)
    else -> phrases.line(item.bytesDone, item.bytesTotal, item.remainingMillis)
  }
  val actions = if (item != null && item.state == FluidTransferState.Running) {
    listOf(
      FluidLiveAction(phrases.pause, { controls.pause(item.id) }, key = "pause"),
      FluidLiveAction(phrases.cancel, { controls.cancel(item.id) }, destructive = true, key = "cancel"),
    )
  } else {
    emptyList()
  }
  return FluidLiveUpdate(
    id = LiveTransferNotifications.UpdateId,
    kind = FluidLiveKind.Transfer,
    title = title,
    text = text,
    progress = progress,
    paused = running == null,
    actions = actions,
    open = open,
  )
}

/**
 * L'esito di un giro della coda per l'attività viva: non riuscito se uno finito da [sinceMillis] non e' riuscito (col
 * suo nome e il motivo), riuscito se almeno uno e' arrivato, in silenzio se non e' finito niente (aspetta o e' in pausa:
 * lo dice la pagina). Pura.
 */
fun fluidTransferOutcome(
  overview: FluidTransferOverview,
  sinceMillis: Long,
  phrases: TransferNotificationPhrases = TransferNotificationPhrases(),
): FluidLiveOutcome {
  val ended = overview.items.filter { (it.finishedAtMillis ?: Long.MIN_VALUE) >= sinceMillis }
  val failed = ended.firstOrNull { it.state == FluidTransferState.Failed }
  val done = ended.filter { it.state == FluidTransferState.Completed }
  return when {
    failed != null -> FluidLiveOutcome.failed(phrases.failedOne(failed.title))
    done.size == 1 -> FluidLiveOutcome.succeeded(
      phrases.doneOne(done[0].title, done[0].direction == FluidTransferDirection.Upload),
    )
    done.isNotEmpty() -> FluidLiveOutcome.succeeded(phrases.doneMany(done.size))
    else -> FluidLiveOutcome.Silent
  }
}
