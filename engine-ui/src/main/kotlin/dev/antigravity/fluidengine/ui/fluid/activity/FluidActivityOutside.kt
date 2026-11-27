package dev.antigravity.fluidengine.ui.fluid.activity

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import dev.antigravity.fluidengine.foundation.live.FluidLiveAction
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveTime
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdateSink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

// F8.19 · `attivita-fuori-dall-app`: la pillola continua fuori dall'app. **Si pubblica una volta** (nella pillola, con
// `FluidActivityHostState.publish`) e si vede dentro e fuori: lo specchio passa ogni attività a un [FluidLiveUpdateSink]
// (quello di engine-notify è `FluidNotify.get(context).liveUpdates.whenOutside`), che la mostra come notifica viva solo
// quando l'app non è in primo piano: su Android 16 un Live Update, prima una notifica con l'avanzamento. Al rientro la
// notifica se ne va e la pillola riprende il suo posto. Il riferimento Apple è la Live Activity che continua nella
// Dynamic Island e sulla schermata di blocco (ActivityKit).
//
// engine-ui non conosce engine-notify: il ponte è il modello puro di engine-foundation (`foundation.live`).

/** Chi riceve i cambi delle attività dell'host, così come arrivano. */
internal interface FluidActivityObserver {
  fun published(activity: FluidActivity)
  fun finished(id: String, outcome: FluidActivityOutcome)
  fun removed(id: String)
}

/**
 * La stessa attività come attività viva fuori dall'app: titolo, riga, avanzamento letto adesso, tempo, pausa e comandi.
 * Il genere passa per nome (`sync`, `transfer`, `timer`, `assistant`, `recording`; un genere su misura resta su misura).
 * «Offline» ([FluidActivityKind.Offline], F8.20) è un lavoro che aspetta la rete, non un'attività avviata dall'utente:
 * fuori è una notifica, mai un Live Update (R2).
 *
 * @param open l'indirizzo da aprire al tocco della notifica (un indirizzo di pagina di F8.12), o null per l'app.
 */
fun FluidActivity.toLiveUpdate(open: String? = null): FluidLiveUpdate = FluidLiveUpdate(
  id = id,
  kind = if (kind == FluidActivityKind.Offline) FluidLiveKind.custom(kind.name, promotable = false) else FluidLiveKind.named(kind.name),
  title = title.ifBlank { kind.name },
  text = detail,
  progress = progress?.invoke()?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f),
  time = time?.toLiveTime(),
  paused = paused,
  actions = actions.map { FluidLiveAction(it.label, it.onClick, it.destructive) },
  open = open,
)

private fun FluidActivityTime.toLiveTime(): FluidLiveTime = when {
  !ticks -> FluidLiveTime.fixed(anchorMillis)
  countsDown -> FluidLiveTime.until(anchorMillis)
  else -> FluidLiveTime.since(anchorMillis)
}

/** L'esito della pillola come esito di un'attività viva: «passa il testimone» non mostra niente fuori. */
fun FluidActivityOutcome.toLiveOutcome(): FluidLiveOutcome = when {
  succeeded -> FluidLiveOutcome.succeeded(message)
  failed -> FluidLiveOutcome.failed(message)
  else -> FluidLiveOutcome.Silent
}

/**
 * Specchia le attività di questo host in [sink], finché il [Job] restituito vive: per un host tenuto per tutta la vita
 * dell'app (nell'`Application` o in un ViewModel), con uno [scope] sul thread principale (`MainScope()`).
 *
 * ```kotlin
 * // nell'Application
 * val activities = FluidActivityHostState()
 * activities.mirrorOutside(FluidNotify.get(this).liveUpdates.whenOutside, MainScope())
 * ```
 *
 * Ogni pubblicazione, fine e rimozione passa subito; l'avanzamento, che la pillola legge quando disegna, fuori lo rilegge
 * lo specchio ogni [FluidLiveUpdateSink.sampleMillis] **solo mentre l'app è fuori** (dentro non serve: non costa niente).
 * Il sink tiene solo l'ultimo stato e pubblica al ritmo giusto. Alla fine del [Job] le attività specchiate si tolgono.
 *
 * @param open l'indirizzo da aprire al tocco della notifica di un'attività, o null per l'app.
 */
fun FluidActivityHostState.mirrorOutside(
  sink: FluidLiveUpdateSink,
  scope: CoroutineScope,
  open: (FluidActivity) -> String? = { null },
): Job {
  val mirrored = LinkedHashSet<String>()
  val observer = object : FluidActivityObserver {
    override fun published(activity: FluidActivity) {
      mirrored += activity.id
      sink.publish(activity.toLiveUpdate(open(activity)))
    }

    override fun finished(id: String, outcome: FluidActivityOutcome) {
      if (mirrored.remove(id)) sink.finish(id, outcome.toLiveOutcome())
    }

    override fun removed(id: String) {
      if (mirrored.remove(id)) sink.remove(id)
    }
  }
  observers = observers + observer
  activities.forEach(observer::published)
  val job = scope.launch {
    sink.outside.collectLatest { outside ->
      // Fuori, l'avanzamento si rilegge da sé: nessuno disegna la pillola.
      while (outside) {
        delay(sink.sampleMillis)
        activities.forEach { activity ->
          if (activity.progress != null && activity.id in mirrored) sink.publish(activity.toLiveUpdate(open(activity)))
        }
      }
    }
  }
  // Sul thread che chiude il lavoro (il principale): lo scope che si chiude non lancerebbe più niente.
  job.invokeOnCompletion {
    observers = observers - observer
    mirrored.toList().forEach(sink::remove)
    mirrored.clear()
  }
  return job
}

/**
 * Lo specchio dentro la composizione, finché questa funzione resta composta: per un host alla radice dell'attività
 * (`rememberFluidActivityHostState`). Per un lavoro che continua quando l'attività si chiude, [mirrorOutside] con un
 * host nell'`Application`.
 *
 * ```kotlin
 * val activities = rememberFluidActivityHostState()
 * FluidActivityHost(activities) {
 *   FluidActivityOutside(activities, FluidNotify.get(LocalContext.current).liveUpdates.whenOutside)
 *   App()
 * }
 * ```
 */
@Composable
fun FluidActivityOutside(
  state: FluidActivityHostState,
  sink: FluidLiveUpdateSink,
  open: (FluidActivity) -> String? = { null },
) {
  val scope = rememberCoroutineScope()
  val latestOpen by rememberUpdatedState(open)
  DisposableEffect(state, sink) {
    val job = state.mirrorOutside(sink, scope) { latestOpen(it) }
    // La composizione se ne va: le notifiche specchiate si tolgono subito.
    onDispose { job.cancel() }
  }
}
