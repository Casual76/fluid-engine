package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * L'orologio dei fotogrammi di un menu in viaggio (S4f sul Tab S9, NO 1): quello di Compose, ma un fotogramma lungo avanza al
 * piu' di [maxStepNanos], come la goccia ([FluidMotion.DropMaxFrameSeconds]: «uno stallo non e' un moto»).
 *
 * Perche': a schermo fermo il Tab S9 va a 30 Hz, e i primi fotogrammi di un menu sono i piu' cari (la composizione del
 * livello, il primo disegno del vetro). Le animazioni di Compose prendono il tempo dal fotogramma, e la nascita di «Altro»
 * correva intanto: il primo fotogramma visibile era gia' a ~45 ms in chiaro e a ~62 ms in scuro (letti sul velo, che parte
 * nello stesso effetto), il secondo oltre i 110 ms. La goccia invece integra al piu' 1/30 s per fotogramma e restava
 * indietro, poi recuperava: dal quadrato stondato al pannello quasi pieno in 33,7 ms (f0004 → f0005), mentre B1 (a) mostra
 * il seme che cresce per ~133 ms (f9148 → f9152). Con questo orologio la nascita perde lo stallo invece di saltarlo: i
 * fotogrammi che si vedono avanzano di un fotogramma di Apple al piu', e la goccia e l'orologio contano lo stesso tempo.
 *
 * Con fotogrammi regolari (sotto il passo massimo) il tempo e' quello di Compose, al nanosecondo: le tracce del banco non
 * cambiano. Tutte le chiamate dello stesso fotogramma leggono lo stesso istante.
 */
internal class FluidSteadyFrameClock(
  private val base: MonotonicFrameClock,
  private val maxStepNanos: Long = FluidSteadyMaxStepNanos,
) : MonotonicFrameClock {
  private var lastReal = Long.MIN_VALUE
  private var steady = 0L

  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
    base.withFrameNanos { real -> onFrame(steadyAt(real)) }

  /** L'istante costante del fotogramma di Compose [real]. */
  internal fun steadyAt(real: Long): Long {
    when {
      lastReal == Long.MIN_VALUE -> steady = real
      real > lastReal -> steady += minOf(real - lastReal, maxStepNanos)
      else -> return steady
    }
    lastReal = real
    return steady
  }
}

/** Il passo massimo dell'orologio dei menu: quello della goccia, un trentesimo di secondo. */
internal val FluidSteadyMaxStepNanos: Long = (FluidMotion.DropMaxFrameSeconds * 1_000_000_000.0).toLong()

/**
 * Il contesto con l'orologio costante sopra quello di [context], o niente se manca un orologio o e' gia' costante (un
 * viaggio dentro il livello dei menu, che ha gia' il suo).
 */
internal fun fluidSteadyFrames(context: CoroutineContext): CoroutineContext {
  val clock = context[MonotonicFrameClock] ?: return EmptyCoroutineContext
  return if (clock is FluidSteadyFrameClock) EmptyCoroutineContext else FluidSteadyFrameClock(clock)
}
