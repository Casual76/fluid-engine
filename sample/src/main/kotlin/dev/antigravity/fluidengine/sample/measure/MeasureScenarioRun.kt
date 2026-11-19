package dev.antigravity.fluidengine.sample.measure

import android.util.Log
import android.view.ViewConfiguration
import android.view.Window
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.sample.material.MaterialTarget
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dove vanno le righe di una corsa. Sempre in logcat (tag `FluidMisure`); sullo schermo ([hud]) solo `PRONTO`,
 * `CHIUSO` ed `ERRORE`, mai un segnale di viaggio: una riga nuova nel riquadro è testo che si ricompone e si ridisegna,
 * e cadrebbe proprio nel primo fotogramma del viaggio che si sta misurando.
 */
internal fun interface ScenarioSink {
  fun line(text: String, hud: Boolean)
}

/** Il pozzo di serie: logcat, e il riquadro della pagina per le righe che lo chiedono. */
internal class LogScenarioSink(private val report: MeasureReport) : ScenarioSink {
  override fun line(text: String, hud: Boolean) {
    if (hud) report.line(text) else Log.i(MeasureLogTag, text)
  }
}

/**
 * Ciò che una scena dà al motore della corsa: un'azione per tratto ([on]) e il segnale che è pronta ([ready]: i
 * bersagli del dito hanno i loro limiti, il foglio da trascinare è aperto). Le azioni sono funzioni sospese che partono
 * col segnale `INIZIO`, nel fotogramma che lo porta, e possono durare quanto il viaggio (un trascinamento, una pressione).
 */
@Stable
internal class ScenarioActions {
  private val actions = HashMap<String, suspend () -> Unit>()

  /** Vero quando la scena può partire. Lo dice la scena; il motore aspetta al più [ReadyTimeoutMillis]. */
  var ready by mutableStateOf(false)

  /** Una nota per la riga `FINE` del tratto in corso (per esempio un foglio che si è dovuto riaprire). */
  var note: String? = null

  /** Un errore che ferma la corsa: la scena non sa fare il tratto (un bersaglio sparito, un host che manca). */
  var failure: String? = null

  fun on(phase: String, action: suspend () -> Unit) {
    actions[phase] = action
  }

  fun action(phase: String): (suspend () -> Unit)? = actions[phase]

  companion object {
    const val ReadyTimeoutMillis = 15_000L
  }
}

/**
 * Uno scenario in corsa: la scena e, sopra, il riquadro con le righe. La corsa parte da sola al primo fotogramma.
 *
 * Nessun ascoltatore di `FrameMetrics` di serie: costa un po' al RenderThread a ogni fotogramma e la 3.1.2 di
 * riferimento non l'avrebbe, quindi falserebbe il confronto. I tempi li prende lo script dai framestats; con
 * `measure.meter on` la corsa scrive anche la sua riga `RIS` (come le scene di F0.6), per un controllo incrociato.
 */
@Composable
internal fun ScenarioRun(
  config: ScenarioConfig,
  window: Window?,
  bottomInset: Dp,
  meterOn: Boolean = false,
  sink: ScenarioSink? = null,
) {
  val report = remember { MeasureReport() }
  val out = sink ?: remember(report) { LogScenarioSink(report) }
  val meter = remember(window, meterOn) { if (meterOn) window?.let { FrameMeter(it) } else null }
  DisposableEffect(meter) {
    meter?.start()
    onDispose { meter?.stop() }
  }
  val actions = remember(config) { ScenarioActions() }
  Box(modifier = Modifier.fillMaxSize()) {
    ScenarioScene(config.scenario, actions, bottomInset)
    if (config.hud) MeasureHud(report)
  }
  LaunchedEffect(config, actions) { runScenario(config, actions, out, meter) }
}

/**
 * La corsa: attesa fissa, `PRONTO`, margine fisso, poi i tratti uno dopo l'altro. Ogni tratto scrive `INIZIO` col tempo
 * del fotogramma in cui la sua azione parte (lo stesso orologio di `IntendedVsync`), una `SONDA` a metà se il viaggio è
 * lungo, `FINE` quando il viaggio è finito e l'azione anche, poi la sua quiete.
 */
internal suspend fun runScenario(config: ScenarioConfig, actions: ScenarioActions, out: ScenarioSink, meter: FrameMeter?) {
  val steps = config.timeline()
  delay(config.warmupMillis)
  val ready = withTimeoutOrNull(ScenarioActions.ReadyTimeoutMillis) {
    while (!actions.ready) delay(ReadyPollMillis)
    true
  }
  if (ready == null) {
    out.line(ScenarioLines.error(config, actions.failure ?: "scena non pronta"), hud = true)
    return
  }
  val missing = steps.map { it.phase.name }.distinct().filter { actions.action(it) == null }
  if (missing.isNotEmpty()) {
    out.line(ScenarioLines.error(config, "azioni mancanti ${missing.joinToString(",")}"), hud = true)
    return
  }
  out.line(ScenarioLines.ready(config, System.nanoTime()), hud = true)
  delay(config.leadMillis)
  val started = System.nanoTime()
  val body: suspend () -> Unit = {
    for ((index, step) in steps.withIndex()) {
      if (!runStep(config, step, index, actions, out)) break
    }
  }
  if (meter != null) {
    val seconds = config.durationMillis() / 1000f
    val window = meter.collect("Scenario ${config.scenario.key}", measureCapacity(seconds)) { body() }
    out.line(measureWindowLine(config.key, 1, 1, window.frames, meter.refreshHz), hud = false)
  } else {
    body()
  }
  val failure = actions.failure
  if (failure != null) {
    out.line(ScenarioLines.error(config, failure), hud = true)
    return
  }
  val now = System.nanoTime()
  out.line(ScenarioLines.closed(config, now, (now - started) / 1_000_000L), hud = true)
}

/** Un tratto. Falso se la scena ha dichiarato un errore: la corsa si ferma lì. */
private suspend fun runStep(
  config: ScenarioConfig,
  step: ScenarioStep,
  index: Int,
  actions: ScenarioActions,
  out: ScenarioSink,
): Boolean = coroutineScope {
  val action = requireNotNull(actions.action(step.phase.name))
  actions.note = null
  // L'azione parte subito dopo la richiamata del fotogramma del segnale, senza un altro giro del dispatcher: un cambio
  // di stato fatto lì entra di solito nella ricomposizione di quello stesso fotogramma (da verificare in Perfetto in S5:
  // la sezione `Scenario …` e il primo `Choreographer#doFrame` che la segue).
  withFrameNanos { frame -> out.line(ScenarioLines.start(config, step, frame), hud = false) }
  val job = launch(start = CoroutineStart.UNDISPATCHED) {
    measureAsyncSection("Scenario ${config.scenario.key} ${step.cycleLabel} ${step.phase.name}", index + 1) { action() }
  }
  // Le attese contano da qui e non dal tempo del fotogramma: in prova quell'orologio è virtuale e `System.nanoTime()`
  // no. Sul dispositivo la differenza è il tratto fra il vsync del fotogramma e questa riga, pochi millisecondi.
  val probe = step.phase.probeMillis?.takeIf { config.scenario.probes && it < step.phase.journeyMillis }
  if (probe != null) {
    delay(probe)
    out.line(ScenarioLines.probe(config, step, System.nanoTime()), hud = false)
  }
  delay(step.phase.journeyMillis - (probe ?: 0L))
  // Un'azione più lunga del suo viaggio lo allunga: due viaggi non si sovrappongono mai.
  job.join()
  out.line(ScenarioLines.end(config, step, System.nanoTime(), actions.note), hud = false)
  if (actions.failure != null) return@coroutineScope false
  delay(step.phase.pauseMillis)
  true
}

private const val ReadyPollMillis = 50L

// ---------------------------------------------------------------------------------------------------------------------
// Il dito finto: i gesti delle scene
// ---------------------------------------------------------------------------------------------------------------------

/** Il dito finto della vista della composizione, alzato (annullato) quando la scena se ne va. */
@Composable
internal fun rememberScenarioFinger(): MaterialFinger {
  val view = LocalView.current
  val finger = remember(view) { MaterialFinger(view) }
  DisposableEffect(finger) {
    onDispose { if (finger.isDown) finger.cancel(Offset.Zero) }
  }
  return finger
}

/** Un punto del rettangolo, in frazioni della sua larghezza e altezza. */
internal fun Rect.at(fx: Float, fy: Float): Offset = Offset(left + width * fx, top + height * fy)

/** Un tocco: giù, 60 ms, su (come il tocco del dito finto nelle scene di «Materiale»). */
internal suspend fun MaterialFinger.tap(at: Offset) {
  down(at)
  delay(TapMillis)
  up(at)
}

/** Una pressione lunga, ferma: giù, [millis], su senza muoversi (il menu contestuale resta aperto). */
internal suspend fun MaterialFinger.hold(at: Offset, millis: Long) {
  down(at)
  delay(millis)
  up(at)
}

/** La pressione lunga più corta che apre sempre un menu contestuale su questo dispositivo, e mai sotto [floor]. */
internal fun longPressMillis(floor: Long): Long = maxOf(floor, ViewConfiguration.getLongPressTimeout().toLong() + 150L)

/**
 * Un trascinamento a velocità costante da [from] a [to] in [millis], un passo per fotogramma, e il rilascio subito dopo
 * l'ultimo passo: il dito lascia con la sua velocità, quindi una lista prosegue col lancio e un foglio sceglie l'altezza
 * col suo slancio, come col dito vero.
 */
internal suspend fun MaterialFinger.drag(from: Offset, to: Offset, millis: Long) {
  down(from)
  val start = withFrameNanos { it }
  while (true) {
    val now = withFrameNanos { it }
    val fraction = ((now - start) / 1_000_000f / millis).coerceIn(0f, 1f)
    move(Offset(from.x + (to.x - from.x) * fraction, from.y + (to.y - from.y) * fraction))
    if (fraction >= 1f) break
  }
  up(to)
}

/**
 * Una pressione tenuta per [millis] che scivola avanti e indietro lungo [target] (il bagliore segue il dito, la lente
 * resta premuta), poi su. Il moto è una funzione del tempo del fotogramma: lo stesso a ogni lancio.
 */
internal suspend fun MaterialFinger.slideHold(target: Rect, millis: Long) {
  val y = target.center.y
  down(Offset(target.center.x, y))
  val start = withFrameNanos { it }
  var last = Offset(target.center.x, y)
  while (true) {
    val now = withFrameNanos { it }
    val elapsed = (now - start) / 1_000_000f
    if (elapsed >= millis) break
    val swing = 0.18f * sin(2f * PI.toFloat() * elapsed / SlidePeriodMillis)
    last = Offset(target.left + target.width * (0.5f + swing), y)
    move(last)
  }
  up(last)
}

/** Aspetta che il bersaglio abbia dei limiti, poi dichiara la scena pronta. */
internal suspend fun ScenarioActions.readyWhenPlaced(vararg targets: MaterialTarget) {
  while (targets.any { it.bounds == null }) delay(ReadyPollMillis)
  ready = true
}

private const val TapMillis = 60L
private const val SlidePeriodMillis = 1_200f
