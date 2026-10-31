package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 16 ms di tempo virtuale. */
private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(16L)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

/**
 * Un viaggio interrotto si ferma dove si trova (3.0). Fino alla 2.13 la cancellazione lasciava il
 * traguardo come forma a riposo: il menu chiuso mentre si apriva saltava al pannello pieno per un
 * fotogramma, e il viaggio di ritorno partiva da li'.
 */
class FluidPhysicsCancelTest {

  private val capsule = FluidForm.Slab(Rect(100f, 600f, 300f, 644f), FluidCornerRadii.all(22f))
  private val panel = FluidForm.Slab(Rect(40f, 200f, 292f, 400f), FluidCornerRadii.all(22f))

  private fun centerY(form: FluidForm): Float = (form as FluidForm.Slab).frame.center.y

  @Test
  fun `un viaggio cancellato a meta' congela la forma interpolata`() = runTest {
    val state = FluidPhysicsState(capsule)
    val journey = launch(Frames(testScheduler)) { state.morphTo(panel, tween(320, easing = LinearEasing)) }
    advanceTimeBy(160L)
    runCurrent()
    assertTrue(state.isMorphing)

    journey.cancel()
    runCurrent()

    assertFalse(state.isMorphing)
    // A meta' strada fra 622 (capsula) e 300 (pannello): ne' l'una ne' l'altro.
    val frozen = centerY(state.form)
    assertTrue("la forma e' scattata a un capo: $frozen", frozen in 400f..520f)
  }

  @Test
  fun `il viaggio dopo la cancellazione parte dalla forma congelata`() = runTest {
    val state = FluidPhysicsState(capsule)
    val opening = launch(Frames(testScheduler)) { state.morphTo(panel, tween(320, easing = LinearEasing)) }
    advanceTimeBy(160L)
    runCurrent()
    opening.cancel()
    runCurrent()
    val frozen = centerY(state.form)

    launch(Frames(testScheduler)) { state.morphTo(capsule, tween(320, easing = LinearEasing)) }
    advanceTimeBy(17L)
    runCurrent()
    // Il primo fotogramma del ritorno e' accanto alla forma congelata, non al pannello (300).
    val firstFrame = state.ensurePlan().pieceRects[1]
    assertTrue("il ritorno e' partito da $firstFrame invece che da $frozen", abs(firstFrame - frozen) < 30f)

    advanceTimeBy(400L)
    runCurrent()
    assertFalse(state.isMorphing)
    assertTrue(abs(centerY(state.form) - 622f) < 0.5f)
  }

  @Test
  fun `un viaggio arrivato in fondo lascia il traguardo esatto`() = runTest {
    val state = FluidPhysicsState(capsule)
    launch(Frames(testScheduler)) { state.morphTo(panel, tween(320, easing = LinearEasing)) }
    advanceTimeBy(400L)
    runCurrent()
    assertFalse(state.isMorphing)
    assertTrue(state.form == panel)
  }
}
