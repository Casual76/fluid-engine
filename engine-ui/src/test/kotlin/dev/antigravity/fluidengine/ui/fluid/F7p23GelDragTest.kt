@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import dev.antigravity.fluidengine.ui.glass.interaction.FluidPressGlowTuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.23 · La posa del gel premuto e trascinato, senza Compose attorno: le regole della correzione, numero per numero.
 * La prova col dito vero sul tasto vero è `F7p23GlassDragTest` (sample).
 */
class F7p23GelDragTest {

  private val d = 62f

  private fun drift(lean: Offset, reach: Float = FluidGel.DriftReach) =
    fluidGelPose(FluidPressFeedback.ExpandRound, 1f, lean, Offset.Zero, lean + Offset(d / 2, d / 2), d, d, 1f, false, reach).drift

  @Test fun theRubberBandIsApplesAndHasNoRigidCap() {
    // La curva di iOS, x·d·c/(d + c·x), con c = 0,55 e d = 0,75 lati: i numeri del KDoc di `FluidGel.DriftReach`.
    for ((sides, expected) in listOf(0.71f to 0.257f, 1f to 0.317f, 3f to 0.516f, 6f to 0.611f)) {
      assertEquals("a $sides lati", expected, drift(Offset(sides * d, 0f)).x / d, 0.002f)
    }
    // All'origine il vetro fa il 55% della strada del dito.
    assertEquals(0.55f, drift(Offset(0.1f, 0f)).x / 0.1f, 0.001f)
    // Lontano non si ferma: a 6 lati fa ancora il 2% della strada, a 20 ancora qualcosa.
    assertTrue(drift(Offset(6.1f * d, 0f)).x - drift(Offset(6f * d, 0f)).x > 0.018f * 0.1f * d)
    assertTrue(drift(Offset(20.1f * d, 0f)).x > drift(Offset(20f * d, 0f)).x)
    // E non arriva mai al suo `d`, che è il margine che serve a chi la disegna.
    assertTrue(drift(Offset(1000f * d, 0f)).x < FluidGel.DriftReach * d)
  }

  @Test fun theDriftIsRadialNotABox() {
    for (sides in listOf(0.5f, 1f, 2f, 4f)) {
      val along = drift(Offset(sides * d, 0f))
      val diagonal = drift(Offset(sides * d, sides * d) / kotlin.math.sqrt(2f))
      assertEquals("a $sides lati", along.getDistance(), diagonal.getDistance(), 0.001f * d)
      assertEquals("nel verso del dito", diagonal.x, diagonal.y, 0.0001f * d)
    }
  }

  @Test fun theSmallDragStaysOnApplesHold() {
    // Il trascinamento del banco di F6.1 (−30 e 9 dp su un tondo da 44): Apple 0,23 lati a occhio (B4), F6.1 0,20.
    val pose = fluidGelPose(FluidPressFeedback.ExpandRound, 1f, Offset(-30f, 9f), Offset.Zero, Offset(-8f, 31f),
      44f, 44f, 1f, false)
    val moved = pose.drift.getDistance() / 44f
    assertTrue("deriva $moved", moved in 0.20f..0.28f)
    // A dito fermo il gel tirato resta un'ellisse di circa 1,15:1, il tenuto di Apple (B4). L'asse non è su x: si misura
    // con gli autovalori.
    val a = pose.invXX
    val b = pose.invXY
    val c = pose.invYY
    val half = kotlin.math.sqrt(((a - c) / 2f) * ((a - c) / 2f) + b * b)
    val ellipse = ((a + c) / 2f + half) / ((a + c) / 2f - half)
    assertTrue("ellisse $ellipse", ellipse in 1.12f..1.19f)
  }

  @Test fun aFusedPieceKeepsTheMarginOfItsCluster() {
    // Nel grappolo di un contenitore il nodo ha sempre avuto 0,25 lati di margine per la deriva: resta così.
    for (sides in listOf(1f, 5f, 50f)) {
      assertTrue(drift(Offset(sides * d, 0f), FluidGel.FusedDriftReach).x < 0.25f * d)
    }
    assertTrue(drift(Offset(5f * d, 0f), FluidGel.FusedDriftReach).x > drift(Offset(2f * d, 0f), FluidGel.FusedDriftReach).x)
    val press = FluidPressState(CoroutineScope(SupervisorJob()), FluidPressFeedback.ExpandRound)
    val geometry = FluidGlassPressGeometry(press, reducedMotion = false)
    val free = geometry.reachPx(d, d, 1f)
    geometry.fused = true
    assertEquals("il margine del grappolo è quello di prima", geometry.margin(d, d, 1f) + 0.25f * d, geometry.reachPx(d, d, 1f), 0.001f)
    assertEquals(0.5f * d, free - geometry.reachPx(d, d, 1f), 0.001f)
  }

  @Test fun reducedMotionHasNoDriftAndNoFlex() {
    val pose = fluidGelPose(FluidPressFeedback.ExpandRound, 1f, Offset(200f, 50f), Offset(900f, 0f), Offset.Zero,
      d, d, 1f, true)
    assertEquals(Offset.Zero, pose.drift)
    assertEquals(1f, pose.invXX, 0f)
    assertEquals(0f, pose.invXY, 0f)
  }

  private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      delay(8L)
      return onFrame(scheduler.currentTime * 1_000_000L)
    }
  }

  private class Scale(override val scaleFactor: Float) : MotionDurationScale

  @Test fun theFilteredSpeedFollowsAndThenRestsAndStopsAskingForFrames() = runTest {
    val job = SupervisorJob()
    val scope = CoroutineScope(coroutineContext.minusKey(Job) + job + Frames(testScheduler) + Scale(1f))
    val state = FluidPressState(scope, FluidPressFeedback.ExpandRound)
    state.bindGel(1)
    try {
      state.touchDown(Offset(31f, 31f), 0L)
      state.press()
      // 300 ms a 500 px/s verso destra, un evento ogni 4 ms.
      var t = 0L
      repeat(75) {
        t += 4L
        state.touchMove(Offset(2f, 0f), t, 1f, grainEnabled = false)
        advanceTimeBy(4L)
        runCurrent()
      }
      val speed = state.fingerSpeed
      assertEquals("segue la velocità vera", 500f, speed.x, 25f)
      assertEquals(0f, speed.y, 0.001f)
      // Il dito si ferma: niente eventi. In mezzo secondo la velocità torna a zero esatto, e da lì nessun fotogramma: col
      // dito fermo e la pressione arrivata non resta niente in coda (un inseguimento che non finisse appenderebbe qui).
      advanceTimeBy(500L)
      runCurrent()
      assertEquals(Offset.Zero, state.fingerSpeed)
      val before = testScheduler.currentTime
      testScheduler.advanceUntilIdle()
      assertTrue("nessun fotogramma chiesto a dito fermo", testScheduler.currentTime - before < 20L)
      assertEquals(Offset.Zero, state.fingerSpeed)
      state.touchEnd()
      state.release()
    } finally {
      state.bindGel(-1)
      job.cancel()
    }
  }

  @Test fun theGlowKnobTakesOnlyItsTwoValues() {
    assertEquals(FluidPressGlowTuning.Level.Vetro, FluidPressGlowTuning.parse(mapOf("PressGlow" to " vetro ")))
    assertEquals(FluidPressGlowTuning.Level.B4, FluidPressGlowTuning.parse(mapOf("pressglow" to "b4")))
    assertNull(FluidPressGlowTuning.parse(mapOf("pressglow" to "latte")))
    assertNull(FluidPressGlowTuning.parse(null))
    // Di serie, il bianco di F6.2 (B4): 76% sul chiaro, 88% sullo scuro.
    assertEquals(0.76f, FluidPressGlowTuning.Default.peak(dark = false), 0f)
    assertEquals(0.88f, FluidPressGlowTuning.Default.peak(dark = true), 0f)
    assertTrue(FluidPressGlowTuning.Level.Vetro.peak(dark = true) < 0.5f)
  }
}
