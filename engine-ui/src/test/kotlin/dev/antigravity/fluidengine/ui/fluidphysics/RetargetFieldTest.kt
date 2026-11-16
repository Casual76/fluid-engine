package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un fotogramma ogni 8 ms di tempo virtuale (~120 Hz). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class RetargetFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(8L)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

/**
 * La ridirezione riparte dalla sagoma del campo che si vedeva (F3.3, `SlabTransit.startField`).
 *
 * Fino a F3.3 il viaggio nuovo partiva dalla regola forma -> piano della forma fotografata a metà volo, col raccordo
 * continuo di serie: un pannello grande (raggio nominale) a metà strada verso due capsule diventava un «piccolo» col
 * raggio equivalente (×0,82), e il raggio del campo scendeva di qualche pixel in un fotogramma (il `passo-zero:area`
 * della serie di robustezza).
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RetargetFieldTest {

  private fun panel(left: Float) = FluidForm.Slab(Rect(left, 0f, left + 300f, 180f), FluidCornerRadii.all(30f))
  private fun capsule(left: Float) = FluidFormPresets.capsule(Rect(left, 60f, left + 60f, 120f))

  @Test
  fun aRedirectedJourneyStartsFromTheFieldItWasDrawing() = runTest {
    val big = FluidForm.Group(listOf(panel(0f), panel(320f)), blendRadius = 24f)
    val small = FluidForm.Group(listOf(capsule(100f), capsule(420f)), blendRadius = 24f)
    val state = FluidPhysicsState(big)
    launch(RetargetFrames(testScheduler)) { state.morphTo(small) }
    advanceTimeBy(40L)
    runCurrent()
    val shown = state.ensurePlan()
    assertEquals("in viaggio fra lastre", PlanModeSlabs, shown.mode)
    val before = shown.fieldRadii.copyOf(8)
    val beforeSmooth = shown.fieldSmooth.copyOf(2)
    assertTrue("il viaggio è a metà: ${state.progress}", state.progress in 0.05f..0.95f)
    launch(RetargetFrames(testScheduler)) { state.morphTo(big) }
    runCurrent()
    val after = state.ensurePlan()
    assertEquals(2, after.pieceCount)
    for (i in 0 until 8) assertEquals("raggio $i", before[i], after.fieldRadii[i], 0.01f)
    for (i in 0 until 2) assertEquals("raccordo $i", beforeSmooth[i], after.fieldSmooth[i], 0.001f)
    // E arriva alla forma ferma, con la sua regola forma -> piano.
    advanceTimeBy(2000L)
    runCurrent()
    val rest = state.ensurePlan()
    val reference = PhysicsRenderPlan().also { buildRestPlan(it, big) }
    for (i in 0 until 8) assertEquals("a riposo $i", reference.fieldRadii[i], rest.fieldRadii[i], 0.001f)
  }
}
