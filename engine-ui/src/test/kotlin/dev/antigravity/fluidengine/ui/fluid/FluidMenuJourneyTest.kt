@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 16 ms di tempo virtuale: le molle vere del viaggio, senza un Recomposer. */
private class JourneyFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(JourneyFrameMillis)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

private const val JourneyFrameMillis = 16L

/** Il banco: un viaggio su fotogrammi finti. */
private class JourneyBench(val test: TestScope) {
  private val job = SupervisorJob()
  val scope = CoroutineScope(test.coroutineContext.minusKey(Job) + job + JourneyFrames(test.testScheduler))

  fun frames(count: Int, read: () -> Float): List<Float> = List(count) {
    test.advanceTimeBy(JourneyFrameMillis)
    test.runCurrent()
    read()
  }

  fun close() = job.cancel()
}

/**
 * Il viaggio dei menu (3.0, fase 7) su un orologio finto: la molla d'apertura che oltrepassa e si
 * posa, la partenza dal tasto premuto che si scioglie, e soprattutto **l'inversione**: un back a meta'
 * apertura torna indietro dal valore di adesso, con lo slancio che aveva — non dal pannello pieno, e
 * non da fermo (un `Animatable` cancellato azzera la sua velocita': `retarget` la legge prima).
 */
class FluidMenuJourneyTest {

  private val rest = Rect(100f, 600f, 300f, 644f)
  private val pressed = Rect(90f, 590f, 310f, 654f)
  private val panel = Rect(40f, 300f, 292f, 500f)

  private fun anchor() = FluidMenuAnchor().apply {
    restBounds = rest
    pressedBounds = pressed
  }

  private fun opened(anchor: FluidMenuAnchor = anchor()): FluidMenuJourney = FluidMenuJourney().apply {
    prepare(anchor, Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation(0.5f, 0.6f))
    retarget(1f)
  }

  @Test
  fun theOpeningOvershootsThePanelAndSettlesOnIt() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    bench.scope.launch { journey.run(reducedMotion = false) }

    val values = bench.frames(90) { journey.progress.value }

    assertTrue("la molla del menu oltrepassa: ${values.max()}", values.max() > 1.03f)
    assertTrue("ma di quanto dice il token, non di piu': ${values.max()}", values.max() < 1f + FluidMotion.MenuPeakOvershoot + 0.03f)
    assertEquals(1f, values.last(), 0.002f)
    assertFalse(journey.closing)
    bench.close()
  }

  @Test
  fun aBackAtAHundredMillisReversesFromWhereItIsWithItsVelocity() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    val opening = bench.scope.launch { journey.run(reducedMotion = false) }
    bench.frames(6) { journey.progress.value }
    val atBack = journey.progress.value
    assertTrue("a 100 ms il menu e' a meta' strada: $atBack", atBack > 0.1f && atBack < 1f)

    // Quello che fa l'host: il verso cambia nel momento in cui si decide, poi l'effetto vecchio viene
    // cancellato e il nuovo corre.
    journey.retarget(0f)
    opening.cancel()
    bench.scope.launch { journey.run(reducedMotion = false) }
    val after = bench.frames(70) { journey.progress.value }

    assertTrue(journey.closing)
    // Riparte da dove si trova: nessun salto al pannello pieno, nessun salto all'ancora.
    assertTrue("riparte da $atBack, non da ${after.first()}", abs(after.first() - atBack) < 0.15f)
    // E con lo slancio dell'apertura: il primo fotogramma prosegue ancora un poco in su, invece di
    // tornare indietro da fermo.
    assertTrue("eredita la velocita': ${after.first()} dopo $atBack", after.first() >= atBack)
    // Poi rientra senza rimbalzare oltre l'ancora, e si posa.
    assertTrue(after.all { it >= -0.002f })
    assertEquals(0f, after.last(), 0.002f)
    assertTrue(journey.settledClosed)
    bench.close()
  }

  @Test
  fun theStartIsThePressedButtonAndRelaxesToItsRestWhileTravelling() = runTest {
    val bench = JourneyBench(this)
    val journey = opened()
    assertEquals(pressed, journey.startFrame())

    bench.scope.launch { journey.run(reducedMotion = false) }
    bench.frames(60) { journey.progress.value }

    assertEquals(rest, journey.startFrame())
    bench.close()
  }

  @Test
  fun theStartIsInHostCoordinates() {
    val journey = FluidMenuJourney()
    journey.prepare(anchor(), hostOrigin = Offset(0f, 80f), panel = panel, radius = 22f, inflation = FluidMenuInflation.None)
    assertEquals(pressed.translate(0f, -80f), journey.startFrame())
  }

  @Test
  fun theWindowGoesFromThePressedButtonToThePanel() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val journey = opened()
    journey.install(physics)

    val start = physics.ensurePlan().bounds
    assertEquals(pressed.left, start.left, 0.01f)
    assertEquals(pressed.bottom, start.bottom, 0.01f)

    journey.progress.snapTo(1f)
    val end = physics.ensurePlan().bounds
    assertEquals(panel.left, end.left, 0.01f)
    assertEquals(panel.top, end.top, 0.01f)
    assertEquals(panel.right, end.right, 0.01f)
    assertEquals(panel.bottom, end.bottom, 0.01f)
  }

  @Test
  fun theOvershootIsAnInflationAroundThePanelCenter() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val journey = opened()
    journey.install(physics)

    journey.progress.snapTo(1.1f)
    val over = physics.ensurePlan().bounds
    // Gonfiato attorno al centro: il centro resta quello del pannello, i lati crescono tutti e quattro.
    assertEquals(panel.center.x, over.center.x, 0.01f)
    assertEquals(panel.center.y, over.center.y, 0.01f)
    assertTrue(over.width > panel.width && over.height > panel.height)
  }

  @Test
  fun aContextMenuGrowsFromItsSeed() = runTest {
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
    val seed = fluidMenuSeedFrame(panel, Offset(panel.center.x, panel.top), FluidMenuSeedScale)
    val journey = FluidMenuJourney()
    journey.prepare(null, Offset.Zero, panel, radius = 22f, inflation = FluidMenuInflation.None, seed = seed)
    journey.install(physics)

    assertEquals(seed, journey.startFrame())
    val start = physics.ensurePlan().bounds
    assertEquals(seed.width, start.width, 0.01f)
    assertEquals(seed.top, start.top, 0.01f)
  }

  @Test
  fun aContextMenuIsBornFromADropHangingFromTheRowUnderTheFinger() {
    // Il vetro liquido (3.0): il menu contestuale esce dalla riga, da una goccia sotto il dito.
    val row = Rect(0f, 100f, 400f, 164f)
    val below = Rect(20f, 176f, 272f, 400f)
    val drop = fluidMenuDropSeed(row, below, touchX = 120f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(28f, drop.width, 0.001f)
    assertEquals(28f, drop.height, 0.001f)
    assertEquals(120f, drop.center.x, 0.001f)
    // Appesa alla riga, ci entra di 4: esce DALLA riga, non le compare accanto.
    assertEquals(row.bottom - 4f, drop.top, 0.001f)
    // Il corpo ne esce davvero: la goccia ha la seconda goccia che si svuota.
    assertTrue(dev.antigravity.fluidengine.ui.fluidphysics.liquidLeavesOrigin(drop, below))

    // Un dito oltre il pannello: la goccia resta sopra il pannello.
    val clamped = fluidMenuDropSeed(row, below, touchX = 390f, diameterPx = 28f, overlapPx = 4f)
    assertEquals(below.right - 14f, clamped.center.x, 0.001f)

    // Un pannello sopra la riga: la goccia sta sul bordo alto, e senza dito sotto il centro della riga.
    val above = fluidMenuDropSeed(row, Rect(20f, 0f, 272f, 90f), touchX = null, diameterPx = 28f, overlapPx = 4f)
    assertEquals(row.top + 4f, above.bottom, 0.001f)
    assertEquals(200f, above.center.x, 0.001f)
  }

  @Test
  fun reducedMotionArrivesWithoutTravelling() = runTest {
    val journey = opened()
    journey.run(reducedMotion = true)
    assertEquals(1f, journey.progress.value, 0f)
    assertEquals(rest, journey.startFrame())
  }

  @Test
  fun reopeningWhileClosingKeepsTheStartItHad() {
    val a = anchor()
    val journey = FluidMenuJourney()
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = true)
    val relax = journey.anchorRelax
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = false)
    assertSame("una riapertura non riparte dal tasto premuto", relax, journey.anchorRelax)
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None, freshStart = true)
    assertNotSame(relax, journey.anchorRelax)
  }

  @Test
  fun aButtonOpenedWithoutPressStartsAtRest() {
    val a = FluidMenuAnchor().apply {
      restBounds = rest
      pressedBounds = rest
    }
    val journey = FluidMenuJourney()
    journey.prepare(a, Offset.Zero, panel, 22f, FluidMenuInflation.None)
    assertEquals(rest, journey.startFrame())
  }

  @Test
  fun retargetingToTheSameDirectionIsFree() {
    val journey = opened()
    assertEquals(1f, journey.target, 0f)
    journey.retarget(1f)
    assertEquals(1f, journey.target, 0f)
    journey.retarget(0f)
    assertTrue(journey.closing)
  }
}
