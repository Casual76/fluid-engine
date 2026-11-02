@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainConfig
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.NoOpFluidHaptics
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

  @Test
  fun `una pressione a dito gia' giu' non riparte`() = bench {
    motion.press()
    settle()
    val rising = frames(3)
    // La pressione da tastiera che segue quella del puntatore, o un evento ripetuto: la corsa e' in corso.
    motion.press()
    settle()
    val after = frames(3)
    assertTrue("e' ripartita da capo: $rising $after", after.first() > rising.last())
    assertTrue(motion.held)
  }

  @Test
  fun `i pulsanti rilasciano subito come LibrePods, e un tocco breve fa quel che la molla ha fatto`() = bench {
    FluidPressSprings.configure(motion, FluidPressFeedback.Expand, reducedMotion = false)
    assertFalse(motion.waitsForArrival)
    motion.press()
    settle()
    val rising = frames(3) // ~48 ms: la molla di LibrePods e' a un quarto del percorso
    assertTrue("doveva essere a meta' corsa: $rising", rising.last() in 0.05f..0.5f)

    motion.release()
    val after = frames(80)
    // Non aspetta di arrivare a 0,96: il picco e' quello di una corsa interrotta, molto piu' basso
    // del rimbalzo di una pressione tenuta (1,16), e poi il ritorno rimbalza sotto il riposo.
    assertTrue("ha aspettato la cima: ${after.max()}", after.max() < FluidPressMotion.ArrivalFraction)
    assertTrue("non e' tornata a riposo: ${after.last()}", kotlin.math.abs(after.last()) < 0.02f)
  }

  @Test
  fun `le molle dei pulsanti sono quelle di LibrePods, in salita e in discesa`() {
    // Smorzamento 0,5 e rigidezza 300, la stessa spec: prima erano due molle piu' corte e diverse.
    assertEquals(FluidPressSprings.ExpandIn, FluidPressSprings.ExpandOut)
    assertEquals(spring<Float>(0.5f, 300f, 0.001f), FluidPressSprings.ExpandIn)
    // E la deriva torna con una molla critica, senza rimbalzo.
    assertEquals(spring<Offset>(1f, 300f, Offset.VisibilityThreshold), FluidPressSprings.LeanReturn)
  }

  @Test
  fun `le superfici e le animazioni ridotte tengono la regola della pressione che arriva`() = bench {
    FluidPressSprings.configure(motion, FluidPressFeedback.yielding(), reducedMotion = false)
    assertTrue(motion.waitsForArrival)
    FluidPressSprings.configure(motion, FluidPressFeedback.Expand, reducedMotion = true)
    assertTrue(motion.waitsForArrival)
  }
}

private class CountingHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  val grains = mutableListOf<Float>()
  val styles = mutableListOf<FluidHapticGrainStyle>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    grains += intensity
    styles += style
  }
}

class FluidPressStateTest {

  /** Il dito a densita' 1: [steps] spostamenti di [step] ogni [everyMillis], dal contatto. */
  private fun FluidPressState.run(fromMillis: Long, steps: Int, step: Offset, everyMillis: Long) {
    touchDown(Offset.Zero)
    repeat(steps) { i -> touchMove(step, fromMillis + everyMillis * (i + 1), pxPerDp = 1f) }
  }

  @Test
  fun `con LibrePods la grana e' un tick per ogni evento sopra la soglia, dal contatto`() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + VirtualFrames(testScheduler))
    val haptics = CountingHaptics()
    val state = FluidPressState(scope, FluidPressFeedback.Expand)
    state.update(FluidPressFeedback.Expand, haptics, reducedMotion = false)

    // Il tasto e' premuto al contatto, non dopo un'attesa: la grana parte dal primo evento.
    state.touchDown(Offset.Zero)
    state.press()
    state.touchMove(Offset(30f, 0f), 8L, pxPerDp = 1f)
    assertEquals(1, haptics.grains.size)
    assertEquals(listOf(FluidHapticGrainStyle.LibrePods), haptics.styles)

    // A passo d'uomo (5 px per evento) niente, per quanta strada faccia: era il ticchettio.
    state.run(fromMillis = 1_000L, steps = 60, step = Offset(5f, 0f), everyMillis = 16L)
    state.press()
    assertEquals(1, haptics.grains.size)

    // Sopra la soglia (20 px: 400 > 350) ogni evento suona, senza memoria ne' tetto.
    state.run(fromMillis = 3_000L, steps = 20, step = Offset(20f, 0f), everyMillis = 8L)
    assertEquals(21, haptics.grains.size)
    assertTrue("niente DragTick dai tasti: ${haptics.played}", haptics.played.isEmpty())

    // Una superficie che cede non ha grana sotto il dito.
    val before = haptics.grains.size
    val surface = FluidPressState(scope, FluidPressFeedback.yielding())
    surface.update(FluidPressFeedback.yielding(), haptics, reducedMotion = false)
    surface.press()
    surface.run(fromMillis = 5_000L, steps = 20, step = Offset(20f, 0f), everyMillis = 8L)
    assertEquals(before, haptics.grains.size)
    job.cancel()
  }

  @Test
  fun `un controllo che vibra per conto suo non ha grana`() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + VirtualFrames(testScheduler))
    val haptics = CountingHaptics()
    val state = FluidPressState(scope, FluidPressFeedback.Expand)
    state.update(FluidPressFeedback.Expand, haptics, reducedMotion = false)
    state.touchDown(Offset.Zero)
    state.press()
    state.touchMove(Offset(40f, 0f), 8L, pxPerDp = 1f, grainEnabled = false)
    assertTrue(haptics.grains.isEmpty())
    job.cancel()
  }

  @Test
  fun `con lo stile dell'engine la grana e' ancora la velocita' misurata, e non parte da ferma`() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + VirtualFrames(testScheduler))
    val haptics = CountingHaptics()
    val state = FluidPressState(scope, FluidPressFeedback.Expand)
    state.update(
      FluidPressFeedback.Expand,
      haptics,
      reducedMotion = false,
      grainConfig = FluidHapticGrainConfig(style = FluidHapticGrainStyle.Fluid),
    )
    state.press()
    // 5 px ogni 16 ms sono 312 dp/s: l'engine non suona, LibrePods nemmeno.
    state.run(fromMillis = 0L, steps = 60, step = Offset(5f, 0f), everyMillis = 16L)
    assertTrue(haptics.grains.isEmpty())
    // 12 px ogni 8 ms sono 1500 dp/s: un treno, e dello stile dell'engine.
    state.run(fromMillis = 2_000L, steps = 20, step = Offset(12f, 0f), everyMillis = 8L)
    assertTrue(haptics.grains.isNotEmpty())
    assertTrue(haptics.styles.all { it == FluidHapticGrainStyle.Fluid })
    assertTrue(haptics.grains.all { it in 0f..1f })
    job.cancel()
  }

  @Test
  fun `il dito e' una somma di spostamenti, torna a zero con una molla critica e si porta il bagliore`() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + VirtualFrames(testScheduler))
    val state = FluidPressState(scope, FluidPressFeedback.Expand)
    state.update(FluidPressFeedback.Expand, NoOpFluidHaptics, reducedMotion = false)

    state.touchDown(Offset(40f, 20f))
    state.press()
    state.touchMove(Offset(10f, -4f), 8L, pxPerDp = 1f)
    state.touchMove(Offset(5f, 0f), 16L, pxPerDp = 1f)
    runCurrent()
    // La somma dei movimenti, non la posizione: se la pagina scorre sotto il dito, il tasto segue.
    assertEquals(Offset(15f, -4f), state.lean)
    assertEquals(Offset(40f, 20f), state.origin)
    assertEquals(Offset(55f, 16f), state.glow)

    state.touchEnd()
    state.release()
    // Una molla critica: scende senza rimbalzare, mai oltre lo zero, e il bagliore torna dov'e' sceso il dito.
    var previous = state.lean.getDistance()
    repeat(60) {
      advanceTimeBy(16L)
      runCurrent()
      val now = state.lean.getDistance()
      assertTrue("la deriva non rimbalza: $previous -> $now", now <= previous + 1e-3f)
      assertTrue(state.lean.x >= -1e-3f)
      previous = now
    }
    assertEquals(0f, state.lean.getDistance(), 0.05f)
    assertEquals(Offset(40f, 20f).x, state.glow.x, 0.05f)

    // Un dito nuovo riparte da zero, anche col ritorno ancora in corso.
    state.touchDown(Offset(10f, 10f))
    runCurrent()
    assertEquals(Offset.Zero, state.lean)
    job.cancel()
  }
}

class FluidPressTransformTest {

  private val dp = 1f

  private fun growth(feedback: FluidPressFeedback, w: Float, h: Float, density: Float = dp) =
    fluidPressGrowth(feedback, w, h, density)

  @Test
  fun `un tasto con testo cresce del 10 per cento, un tasto tondo del 50, i numeri di LibrePods`() {
    assertEquals(0.10f, growth(FluidPressFeedback.Expand, 120f, 42f), 1e-5f)
    assertEquals(0.5f, growth(FluidPressFeedback.ExpandRound, 44f, 44f), 1e-5f)
    assertEquals(TextMaxScale, FluidPressFeedback.Expand.maxScale, 0f)
    assertEquals(RoundMaxScale, FluidPressFeedback.ExpandRound.maxScale, 0f)
    // Un tasto da 44 dp a pressione piena arriva a 66 dp, e al picco della molla (sorpasso del 16%) a ~70.
    val full = fluidPressTransform(FluidPressFeedback.ExpandRound, 1f, Offset.Zero, 44f, 44f, dp, reducedMotion = false)
    assertEquals(1.5f, full.scaleX, 1e-5f)
    assertEquals(full.scaleX, full.scaleY, 0f)
    val peak = fluidPressTransform(FluidPressFeedback.ExpandRound, 1.163f, Offset.Zero, 44f, 44f, dp, false)
    assertEquals(1.58f, peak.scaleX, 0.01f)
    // E scende sotto il riposo al rilascio: -16% del percorso fatto, come in LibrePods.
    val under = fluidPressTransform(FluidPressFeedback.ExpandRound, -0.163f, Offset.Zero, 44f, 44f, dp, false)
    assertEquals(0.92f, under.scaleX, 0.01f)
  }

  @Test
  fun `un tasto largo cresce al massimo 16 dp sul lato lungo, cioe' 0,05 a larghezza piena come passa a mano LibrePods`() {
    // Sotto il tetto, comanda il 10%.
    assertEquals(0.10f, growth(FluidPressFeedback.Expand, 120f, 42f), 1e-5f)
    assertEquals(0.10f, growth(FluidPressFeedback.Expand, 160f, 48f), 1e-5f)
    // Sopra: i 16 dp sul lato lungo.
    assertEquals(16f / 200f, growth(FluidPressFeedback.Expand, 200f, 48f), 1e-5f)
    assertEquals(16f / 320f, growth(FluidPressFeedback.Expand, 320f, 48f), 1e-5f)
    assertEquals(0.05f, growth(FluidPressFeedback.Expand, 320f, 48f), 1e-5f)
    // In px: la stessa crescita in dp su uno schermo a densita' 3.
    assertEquals(16f / 200f, growth(FluidPressFeedback.Expand, 600f, 144f, density = 3f), 1e-5f)
    // Il tasto tondo non ha tetto: non e' mai largo.
    assertEquals(0.5f, growth(FluidPressFeedback.ExpandRound, 44f, 44f), 1e-5f)
    assertEquals(0f, growth(FluidPressFeedback.Expand, 0f, 40f), 0f)
  }

  @Test
  fun `a pressione piena e senza dito che scorre la crescita e' uniforme`() {
    val t = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset.Zero, 120f, 42f, dp, reducedMotion = false)
    assertEquals(1.10f, t.scaleX, 1e-5f)
    assertEquals(t.scaleX, t.scaleY, 0f)
    assertEquals(0f, t.translationX, 0f)
    assertTrue(fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset.Zero, 120f, 42f, dp, false).isIdentity)
  }

  @Test
  fun `con le animazioni ridotte cresce la meta' e non segue il dito`() {
    val full = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset.Zero, 120f, 42f, dp, reducedMotion = false)
    val reduced = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(30f, 10f), 120f, 42f, dp, reducedMotion = true)
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
  fun `la deriva e' m per tanh di 0,05 d su m, senza tetto se non il lato corto`() {
    fun drift(distance: Float, side: Float = 48f) = fluidPressTransform(
      FluidPressFeedback.Expand, 1f, Offset(distance, 0f), 160f, side, dp, false,
    ).translationX
    // I numeri del riferimento, su un tasto da 48 dp di lato corto.
    assertEquals(1.0f, drift(20f), 0.05f)
    assertEquals(5.0f, drift(100f), 0.05f)
    assertEquals(14.5f, drift(300f), 0.1f)
    assertEquals(26.6f, drift(600f), 0.1f)
    // Non c'era un tetto a 8 dp e a un sesto del lato: arriva oltre, e si ferma solo al lato corto.
    assertTrue(drift(300f) > 8f)
    assertTrue(drift(100_000f) <= 48f + 1e-3f)
    // Su entrambi gli assi, con il segno del dito.
    val t = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(-100f, 40f), 160f, 48f, dp, false)
    assertTrue(t.translationX < 0f && t.translationY > 0f)
  }

  @Test
  fun `la deriva non e' pesata dalla pressione, ha la sua molla di ritorno`() {
    // A pressione zero, e anche sotto zero nel rimbalzo, il dito che sta tornando si vede ancora.
    val zero = fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset(40f, 0f), 120f, 48f, dp, false)
    assertTrue(zero.translationX > 0f)
    assertEquals(1f + StretchGain * (40f / 120f), zero.scaleX, 1e-5f)
    val bounce = fluidPressTransform(FluidPressFeedback.Expand, -0.1f, Offset(40f, 0f), 120f, 48f, dp, false)
    assertTrue(bounce.scaleX < zero.scaleX)
    assertEquals(zero.translationX, bounce.translationX, 0f)
    assertTrue(fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset.Zero, 120f, 48f, dp, false).isIdentity)
  }

  @Test
  fun `l'allungamento e' lineare e senza tetto, nel verso del dito`() {
    // Il numero del riferimento: a 400 dp di trascinamento su un tasto da 160 dp, +25%.
    val along = fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset(400f, 0f), 160f, 48f, dp, false)
    assertEquals(1.25f, along.scaleX, 1e-4f)
    assertEquals(1f, along.scaleY, 1e-4f)
    // Verticale: il fattore e' min(h/w, 1), cioe' meno di un tasto largo che si stira in alto.
    val up = fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset(0f, -400f), 160f, 48f, dp, false)
    assertEquals(1f + StretchGain * (400f / 160f) * (48f / 160f), up.scaleY, 1e-4f)
    assertEquals(1f, up.scaleX, 1e-4f)
    // Senza tetto: ben oltre l'8% di prima.
    val far = fluidPressTransform(FluidPressFeedback.Expand, 0f, Offset(4_000f, 0f), 160f, 48f, dp, false)
    assertTrue(far.scaleX > 2f)
    // Diagonale: tutti e due gli assi, ognuno con la sua parte.
    val diagonal = fluidPressTransform(FluidPressFeedback.Expand, 1f, Offset(100f, 100f), 120f, 48f, dp, false)
    assertTrue(diagonal.scaleX > 1.10f && diagonal.scaleY > 1.10f)
  }

  @Test
  fun `un tasto che non segue il dito cresce e basta`() {
    val feedback = FluidPressFeedback.Expand.copy(followsFinger = false)
    val t = fluidPressTransform(feedback, 1f, Offset(200f, 0f), 120f, 42f, dp, false)
    assertEquals(1.10f, t.scaleX, 1e-5f)
    assertEquals(0f, t.translationX, 0f)
  }
}

class FluidPressGestureTest {

  private fun gesture(
    longPress: Long = FluidPressGesture.NoLongPress,
    slop: Float = 20f,
  ) = FluidPressGesture(downUptime = 1_000L, touchSlop = slop, longPressMillis = longPress)

  @Test
  fun `la pressione lunga scatta al suo tempo se il dito e' rimasto nello slop, una volta sola`() {
    val g = gesture(longPress = 400L)
    g.moved(Offset(5f, 5f))
    assertFalse(g.advance(1_399L))
    assertTrue(g.advance(1_400L))
    assertTrue(g.longPressed)
    // Non scatta due volte.
    assertFalse(g.advance(2_000L))
  }

  @Test
  fun `il prossimo scadere e' la pressione lunga, poi niente`() {
    val g = gesture(longPress = 400L)
    assertEquals(1_400L, g.nextDeadline)
    g.advance(1_400L)
    assertEquals(Long.MAX_VALUE, g.nextDeadline)
    // Senza pressione lunga non c'e' nessuna scadenza.
    assertEquals(Long.MAX_VALUE, gesture().nextDeadline)
  }

  @Test
  fun `un dito che ha superato lo slop non sta tenendo premuto, e tornare non lo riarma`() {
    val g = gesture(longPress = 400L, slop = 20f)
    g.moved(Offset(0f, 21f))
    assertEquals("disarmato: nessuna scadenza", Long.MAX_VALUE, g.nextDeadline)
    assertFalse(g.advance(1_500L))
    assertFalse(g.longPressed)
    g.moved(Offset.Zero)
    assertFalse(g.advance(3_000L))
  }

  @Test
  fun `lo slop e' la strada del dito sullo schermo, a filo non disarma`() {
    val g = gesture(longPress = 400L, slop = 20f)
    g.moved(Offset(12f, 16f)) // distanza 20, non oltre
    assertTrue(g.advance(1_400L))
  }

  @Test
  fun `un tasto senza pressione lunga non ne ha mai una`() {
    val g = gesture()
    assertFalse(g.advance(60_000L))
    assertFalse(g.longPressed)
  }

  @Test
  fun `se fra due eventi e' passato tutto, scatta all'evento`() {
    val g = gesture(longPress = 400L)
    assertTrue(g.advance(1_700L))
    assertTrue(g.longPressed)
  }

  @Test
  fun `un tap rapido non e' una pressione lunga`() {
    val g = gesture(longPress = 400L)
    assertFalse(g.advance(1_060L))
    assertFalse(g.longPressed)
  }

  // --- Il dito e' del pulsante (3.0, dopo la prova in mano) -------------------------------------------

  private fun holding(
    slop: Float = 20f,
    claimDistance: Float = slop * FluidPressGesture.ClaimFraction,
    longPress: Long = FluidPressGesture.NoLongPress,
  ) = FluidPressGesture(
    downUptime = 1_000L,
    touchSlop = slop,
    longPressMillis = longPress,
    grip = FluidPressGrip.HoldsFinger,
    claimDistance = claimDistance,
  )

  private fun yielding(slop: Float = 20f) = FluidPressGesture(
    downUptime = 1_000L,
    touchSlop = slop,
    grip = FluidPressGrip.YieldsToScroll,
  )

  @Test
  fun `il gesto prende il dito oltre meta' dello slop, e prima no`() {
    val g = holding(slop = 20f)
    // Un tremito (un tocco vero): sotto i 10 px il click resta di `clickable`.
    assertFalse(g.claim(Offset(3f, 2f), insideClickable = true))
    assertFalse(g.claim(Offset(0f, 4f), insideClickable = true))
    assertFalse(g.claimed)
    // Oltre meta' dello slop: preso, e da li' non si rilascia piu'.
    assertTrue(g.claim(Offset(0f, 10f), insideClickable = true))
    assertTrue(g.claimed)
    assertTrue("preso resta preso", g.claim(Offset.Zero, insideClickable = true))
  }

  @Test
  fun `la soglia e' piu' bassa dello slop, perche' la pagina decide dopo il pulsante e non puo' arrivare prima`() {
    assertTrue(FluidPressGesture.ClaimFraction in 0.1f..0.9f)
    // Con lo slop del sistema di un telefono a xxhdpi (24 px) si prende il dito prima di 24 px.
    val g = holding(slop = 24f)
    assertFalse(g.claim(Offset(0f, 11f), insideClickable = true))
    assertTrue(g.claim(Offset(0f, 2f), insideClickable = true))
  }

  @Test
  fun `la strada e' la somma delle lunghezze, un dito che va e torna la conta due volte`() {
    val g = holding(slop = 20f)
    assertFalse(g.claim(Offset(6f, 0f), insideClickable = true))
    // Torna indietro: lo spostamento netto e' zero, ma la strada e' 12.
    assertTrue(g.claim(Offset(-6f, 0f), insideClickable = true))
  }

  @Test
  fun `un dito che esce dall'area di clickable e' preso subito, anche sotto la soglia`() {
    val g = holding(slop = 20f)
    // `clickable` annulla da solo quando il dito esce: il click deve passare al gesto.
    assertTrue(g.claim(Offset(1f, 0f), insideClickable = false))
    assertTrue(g.claimed)
  }

  @Test
  fun `un mouse prende il dito al primo passo, perche' non ha un tremito da lasciar passare`() {
    val g = holding(slop = 20f, claimDistance = 0f)
    assertTrue(g.claim(Offset(0.2f, 0f), insideClickable = true))
  }

  @Test
  fun `chi cede il dito alla pagina non lo prende mai`() {
    val g = yielding()
    assertFalse(g.claim(Offset(0f, 400f), insideClickable = true))
    assertFalse(g.claim(Offset(0f, 400f), insideClickable = false))
    assertFalse(g.claimed)
  }

  private val button = Size(312f, 96f)
  private val noPadding = Size.Zero
  private val margin = Size(32f, 32f)

  @Test
  fun `un tocco che non si e' mosso non e' un click del gesto, e' di clickable`() {
    val g = holding()
    // Nessuna presa, il rilascio e' sul pulsante: due sorgenti per lo stesso tocco sarebbero due click.
    assertFalse(g.clicks(Offset(150f, 40f), button, noPadding, margin))
  }

  @Test
  fun `dopo un trascinamento il click e' del gesto se il dito si solleva dentro il pulsante`() {
    val g = holding()
    g.claim(Offset(0f, 30f), insideClickable = true)
    assertTrue(g.clicks(Offset(150f, 40f), button, noPadding, margin))
    // Anche un dito che ha viaggiato lontano e torna: conta dove si solleva.
    g.claim(Offset(0f, -300f), insideClickable = false)
    assertTrue(g.clicks(Offset(10f, 90f), button, noPadding, margin))
  }

  @Test
  fun `il margine del click e' 16 dp oltre il bordo, a filo conta e oltre no`() {
    val g = holding()
    g.claim(Offset(0f, 30f), insideClickable = true)
    // Il pulsante e' 312 x 96 px; il margine, a 2 px per dp, 32 px.
    assertTrue(g.clicks(Offset(100f, 96f + 32f), button, noPadding, margin))
    assertTrue(g.clicks(Offset(-32f, 10f), button, noPadding, margin))
    assertTrue(g.clicks(Offset(312f + 32f, 96f + 32f), button, noPadding, margin))
    assertFalse(g.clicks(Offset(100f, 96f + 33f), button, noPadding, margin))
    assertFalse(g.clicks(Offset(-33f, 10f), button, noPadding, margin))
    assertFalse(g.clicks(Offset(312f + 40f, 96f + 40f), button, noPadding, margin))
  }

  @Test
  fun `un rilascio fuori da clickable ma dentro il margine clicca il gesto anche senza presa`() {
    val g = holding()
    // Il dito e' sceso nel bordo allargato, e si solleva 6 px fuori dall'area di clickable (che ha un
    // allargamento di 4 px): `clickable` annulla, e il click, dentro il margine di 32, e' del gesto.
    val clickableSlack = Size(4f, 4f)
    assertTrue(g.clicks(Offset(100f, 96f + 6f), button, clickableSlack, margin))
    // Dentro l'area di clickable, invece, e' suo.
    assertFalse(g.clicks(Offset(100f, 96f + 3f), button, clickableSlack, margin))
  }

  @Test
  fun `dopo un click lungo il rilascio non e' un click, preso il dito o no`() {
    val g = holding(longPress = 400L)
    g.claim(Offset(0f, 6f), insideClickable = true)
    assertTrue(g.advance(1_400L))
    g.claim(Offset(0f, 30f), insideClickable = true)
    assertFalse(g.clicks(Offset(150f, 40f), button, noPadding, margin))
  }

  @Test
  fun `chi cede il dito non clicca mai da solo, nemmeno fuori dall'area di clickable`() {
    val g = yielding()
    assertFalse(g.clicks(Offset(100f, 96f + 6f), button, Size(4f, 4f), margin))
    assertFalse(g.clicks(Offset(150f, 40f), button, noPadding, margin))
  }

  @Test
  fun `il margine e' il piu' grande fra l'area di tocco minima e i 16 dp`() {
    // Un tasto di 48 dp ha tocco minimo di 0: il margine e' tutto dei 16 dp (32 px).
    assertEquals(Size(32f, 32f), fluidPressSlack(Size.Zero, 32f))
    // Un'icona di 24 dp ha 12 dp di tocco minimo per lato (24 px): meno dei 16 dp, vince il margine.
    assertEquals(Size(32f, 32f), fluidPressSlack(Size(24f, 24f), 32f))
    // Un tocco minimo piu' largo del margine vince.
    assertEquals(Size(40f, 32f), fluidPressSlack(Size(40f, 10f), 32f))
  }

  @Test
  fun `dentro e fuori da un rettangolo allargato`() {
    val slack = Size(10f, 10f)
    assertTrue(fluidPressInside(Offset(-10f, -10f), button, slack))
    assertTrue(fluidPressInside(Offset(button.width + 10f, button.height + 10f), button, slack))
    assertFalse(fluidPressInside(Offset(-10.1f, 0f), button, slack))
    assertFalse(fluidPressInside(Offset(0f, button.height + 10.1f), button, slack))
  }
}
