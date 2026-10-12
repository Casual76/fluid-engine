@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 16 ms di tempo virtuale: le molle vere, senza un Recomposer che giri. */
private class VirtualFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(FrameMillis)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

private class DurationScale(override val scaleFactor: Float) : MotionDurationScale

private const val FrameMillis = 16L

/** Il banco: una corsa su fotogrammi finti, e il modo di farli passare guardandola. */
private class Bench(val test: TestScope, durationScale: Float) {
  private val job = SupervisorJob()
  val scope = CoroutineScope(
    test.coroutineContext.minusKey(Job) + job + VirtualFrames(test.testScheduler) +
      DurationScale(durationScale),
  )
  val motion = FluidPressMotion(scope)

  /** Fa passare [count] fotogrammi e restituisce il valore dopo ognuno. */
  fun frames(count: Int): List<Float> = List(count) {
    test.advanceTimeBy(FrameMillis)
    test.runCurrent()
    motion.value
  }

  fun settle() = test.runCurrent()

  fun close() = job.cancel()
}

private fun bench(durationScale: Float = 1f, block: Bench.() -> Unit) = runTest {
  val bench = Bench(this, durationScale)
  try {
    bench.block()
  } finally {
    bench.close()
  }
}

/** Vero se [values] sale (o resta) fino al primo che arriva a [peak]. */
private fun risesUntil(values: List<Float>, peak: Float): Boolean {
  val arrival = values.indexOfFirst { it >= peak }
  if (arrival < 0) return false
  return (1..arrival).all { values[it] >= values[it - 1] - 1e-4f }
}

class FluidPressMotionTest {

  @Test
  fun `un tap rapidissimo fa vedere comunque la pressione intera`() = bench {
    motion.press()
    settle()
    // Il dito si alza prima del primo fotogramma: il tap di 10 ms che prima non muoveva niente.
    motion.release()
    val seen = frames(60)

    assertTrue("la corsa non e' arrivata in cima: max ${seen.max()}", seen.max() >= FluidPressMotion.ArrivalFraction)
    assertTrue("e' tornata indietro prima di arrivare: $seen", risesUntil(seen, FluidPressMotion.ArrivalFraction))
    assertEquals("non e' tornata a riposo", 0f, seen.last(), 0.01f)
  }

  @Test
  fun `il rilascio a meta' corsa la porta a termine, poi torna`() = bench {
    motion.press()
    settle()
    val rising = frames(3)
    assertTrue("doveva essere a meta' corsa: $rising", rising.last() in 0.05f..0.95f)

    motion.release()
    val after = frames(60)
    assertTrue("arrivata solo a ${after.max()}", after.max() >= FluidPressMotion.ArrivalFraction)
    assertTrue("e' tornata indietro prima di arrivare: $after", risesUntil(rising + after, FluidPressMotion.ArrivalFraction))
    assertEquals(0f, after.last(), 0.01f)
  }

  @Test
  fun `tenuto premuto resta in cima finche' il dito c'e'`() = bench {
    motion.press()
    settle()
    val held = frames(40)
    assertEquals(1f, held.last(), 0.01f)
    assertTrue(motion.held)

    motion.release()
    val released = frames(60)
    assertEquals(0f, released.last(), 0.01f)
    assertTrue("il ritorno dei pulsanti rimbalza sotto il riposo", released.min() < -0.01f)
  }

  @Test
  fun `una nuova pressione durante il ritorno risale subito`() = bench {
    motion.press()
    settle()
    frames(30)
    motion.release()
    val falling = frames(4)
    assertTrue("doveva scendere: $falling", falling.last() < falling.first())

    motion.press()
    settle()
    val again = frames(40)
    assertTrue("non e' risalita: $again", again.max() >= FluidPressMotion.ArrivalFraction)
    assertEquals("col dito ancora giu' resta premuta", 1f, again.last(), 0.01f)
  }

  @Test
  fun `con le animazioni a zero la pressione resta intera, un fotogramma per parte`() = bench(durationScale = 0f) {
    FluidPressSprings.configure(motion, FluidPressFeedback.Expand, reducedMotion = true)
    motion.press()
    motion.release()
    settle()
    val seen = frames(6)
    assertTrue("la pressione e' stata saltata: $seen", seen.any { it >= FluidPressMotion.ArrivalFraction })
    assertEquals(0f, seen.last(), 0f)
    assertTrue(risesUntil(seen, FluidPressMotion.ArrivalFraction))
  }

  @Test
  fun `un rilascio senza pressione non fa niente`() = bench {
    motion.release()
    assertEquals(0f, frames(3).last(), 0f)
  }
}

private class CountingHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

class FluidPressStateTest {

  @Test
  fun `le tacche arrivano solo a pressione in corso e solo per chi le vuole`() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + VirtualFrames(testScheduler))
    val haptics = CountingHaptics()
    val state = FluidPressState(scope, FluidPressFeedback.Expand)
    state.update(FluidPressFeedback.Expand, haptics, reducedMotion = false)

    // Prima che clickable confermi la pressione il dito puo' stare iniziando uno scorrimento.
    state.touchDown(Offset.Zero)
    state.touchMove(Offset(25f, 0f), uptimeMillis = 0L, stepPx = 10f)
    assertTrue(haptics.played.isEmpty())

    state.press()
    state.touchDown(Offset.Zero)
    state.touchMove(Offset(12f, 0f), uptimeMillis = 100L, stepPx = 10f)
    state.touchMove(Offset(24f, 0f), uptimeMillis = 200L, stepPx = 10f)
    assertEquals(listOf(FluidHapticEvent.DragTick, FluidHapticEvent.DragTick), haptics.played)
    assertEquals(Offset(24f, 0f), state.lean)

    // Una superficie che cede non scatta sotto il dito.
    val surface = FluidPressState(scope, FluidPressFeedback.yielding())
    surface.update(FluidPressFeedback.yielding(), haptics, reducedMotion = false)
    surface.press()
    surface.touchDown(Offset.Zero)
    surface.touchMove(Offset(50f, 0f), uptimeMillis = 300L, stepPx = 10f)
    assertEquals(2, haptics.played.size)
    job.cancel()
  }
}

class FluidPressTransformTest {

  private val dp = 1f

  @Test
  fun `un pulsante cresce di 6 dp sul lato corto, al massimo 14 sul lungo e mai oltre il 14 per cento`() {
    assertEquals(1f + 6f / 44f, fluidPressExpansion(44f, 44f, dp), 1e-4f)
    // Un pulsante medio: 14 dp in larghezza, 7 per parte, che in una fila a 12 dp non tocca il vicino.
    assertEquals(1f + 14f / 120f, fluidPressExpansion(120f, 42f, dp), 1e-4f)
    assertEquals(1f + 14f / 360f, fluidPressExpansion(360f, 50f, dp), 1e-4f)
    // Un quadrato piccolo: comanda il tetto del 14%.
    assertEquals(ExpandMaxScale, fluidPressExpansion(40f, 40f, dp), 1e-4f)
    assertEquals(ExpandMaxScale, fluidPressExpansion(28f, 28f, dp), 1e-4f)
    // In px: la stessa crescita in dp su uno schermo a densita' 3.
    assertEquals(1f + 6f / 44f, fluidPressExpansion(132f, 132f, 3f), 1e-4f)
    assertEquals(1f, fluidPressExpansion(0f, 40f, dp), 0f)
  }

  @Test
  fun `a pressione piena e senza dito che scorre la crescita e' uniforme`() {
    val t = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset.Zero, 120f, 42f, dp, reducedMotion = false)
    assertEquals(fluidPressExpansion(120f, 42f, dp), t.scaleX, 1e-4f)
    assertEquals(t.scaleX, t.scaleY, 0f)
    assertTrue(t.scaleX > 1.1f)
    assertEquals(0f, t.translationX, 0f)
    assertTrue(fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset(30f, 0f), 120f, 42f, dp, false).isIdentity)
  }

  @Test
  fun `con le animazioni ridotte cresce la meta' e non segue il dito`() {
    val full = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset.Zero, 44f, 44f, dp, reducedMotion = false)
    val reduced = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(30f, 10f), 44f, 44f, dp, reducedMotion = true)
    assertEquals((full.scaleX - 1f) * ReducedAmplitude, reduced.scaleX - 1f, 1e-4f)
    assertEquals(reduced.scaleX, reduced.scaleY, 0f)
    assertEquals(0f, reduced.translationX, 0f)
    assertEquals(0f, reduced.translationY, 0f)
  }

  @Test
  fun `una superficie cede e sta ferma`() {
    val t = fluidPressTransform(FluidPressFeedback.yielding(0.97f), 1f, Offset(40f, 40f), 300f, 120f, dp, false)
    assertEquals(0.97f, t.scaleX, 1e-5f)
    assertEquals(0.97f, t.scaleY, 1e-5f)
    assertEquals(0f, t.translationX, 0f)
    assertTrue(fluidPressTransform(FluidPressFeedback.None, 1f, Offset(40f, 0f), 300f, 120f, dp, false).isIdentity)
  }

  @Test
  fun `l'inclinazione segue il dito ma non scappa`() {
    val near = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(10f, -10f), 120f, 48f, dp, false)
    assertTrue(near.translationX > 0f && near.translationY < 0f)
    assertTrue("il primo tratto e' quasi libero", abs(near.translationX - 10f * LeanGain) < 0.1f)

    val far = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(10_000f, 0f), 120f, 48f, dp, false)
    assertTrue(far.translationX <= LeanMaxDp * dp + 1e-3f)
    // Un'icona da 44 dp non si sposta piu' di un sesto del suo lato.
    val icon = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(10_000f, 0f), 44f, 44f, dp, false)
    assertTrue(icon.translationX <= 44f * LeanMaxFraction + 1e-3f)
  }

  @Test
  fun `si allunga nel verso del dito e si assottiglia di traverso`() {
    val t = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(40f, 0f), 120f, 48f, dp, false)
    val base = fluidPressExpansion(120f, 48f, dp)
    assertTrue(t.scaleX > base)
    assertTrue(t.scaleY < base)
    assertTrue(t.scaleX <= base * (1f + StretchMax) + 1e-4f)
  }

  @Test
  fun `nel rimbalzo del ritorno scende sotto la misura e non trascina l'inclinazione`() {
    val t = fluidPressTransform(FluidPressFeedback.Expand, -0.1f, Offset(40f, 0f), 120f, 48f, dp, false)
    assertTrue(t.scaleX < 1f)
    assertEquals(t.scaleX, t.scaleY, 0f)
    assertEquals(0f, t.translationX, 0f)
  }
}
