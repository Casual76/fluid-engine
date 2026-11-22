package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Un clock a 120 Hz guida la macchina usata da entrambi gli host. */
@OptIn(ExperimentalCoroutinesApi::class)
class FluidPageDimTest {
  private val clock = BroadcastFrameClock()
  private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
  private val state = FluidPageHostState().apply {
    this.scope = this@FluidPageDimTest.scope
    width = 400f
    height = 700f
  }
  private var frames = 0
  private fun tick() = clock.sendFrame(++frames * 1_000_000_000L / 120)
  private fun settle(checkFrame: () -> Unit = {}) {
    repeat(240) {
      if (!state.isMoving) return
      checkFrame()
      tick()
    }
    fail("La molla non si è posata")
  }
  @After fun close() { scope.cancel(); state.releaseSources() }

  @Test fun quartiliSostaInversioneRilascioERipresa() {
    for (style in FluidPageTransition.entries) for (direction in listOf(-1, 1)) {
      state.transitionOf = { style }
      state.onStack(listOf(0, 1))
      state.pagePlaced(1)
      settle()
      for (fraction in listOf(.25f, .5f, .75f)) {
        val token = state.beginDrag(direction)!!
        for (p in listOf(.25f, .5f, .75f, .5f, .25f, fraction)) {
          state.dragTo(token, state.exitTravel() * p)
          repeat(19) {
            assertEquals(0.12f * (1f - p), state.dimOn(0), 0.00001f)
            assertEquals(0f, state.dimOn(1), 0f)
            tick()
          }
        }
        val held = state.dimOn(0)
        state.release(token, 0f, decided = false)
        tick()
        assertEquals(held, state.dimOn(0), 0.00001f)
        repeat(4) { tick() }
        val before = state.dimOn(0)
        val again = state.beginDrag(direction)!!
        assertEquals(before, state.dimOn(0), 0f)
        state.release(again, 0f, decided = false)
        settle {
          assertEquals(0.12f * (1f - state.upperExitFraction()), state.dimOn(0), 0.00001f)
          assertEquals(0f, state.dimOn(1), 0f)
        }
        assertEquals(1, state.upper)
        assertEquals(0f, state.dimOn(0), 0f)
      }
    }
  }

  @Test fun duePopCambianoLaPaginaVelataNelloStessoFotogramma() {
    state.onStack(listOf(0, 1, 2))
    state.onStack(listOf(0, 1))
    repeat(8) { tick() }
    assertTrue(state.dimOn(1) > 0f)
    state.onStack(listOf(0))
    settle {
      assertEquals(listOf(0), (0..2).filter { state.dimOn(it) > 0f })
      assertTrue(state.dimOn(0) <= .12f)
    }
    assertEquals(0, state.upper)
    assertEquals(0f, state.dimOn(0), 0f)
  }

  @Test fun scalaZeroPosaEntrambiICapiSenzaVelo() {
    state.scope = CoroutineScope(scope.coroutineContext + object : MotionDurationScale { override val scaleFactor = 0f })
    state.reducedMotion = true
    state.onStack(listOf(0))
    for (stack in listOf(listOf(0, 1), listOf(0))) {
      state.onStack(stack)
      state.pagePlaced(stack.last())
      tick()
      assertEquals(0f, state.dimOn(0), 0f)
      assertEquals(0f, state.dimOn(1), 0f)
      settle()
      assertEquals(stack.last(), state.upper)
    }
  }

  @Test fun zoomECresceLeggonoLaSagomaNelleDueDirezioni() {
    for (glass in listOf(false, true)) {
      val source = FluidTransitionSource("cella", FluidGlassNamespace()).let { if (glass) it.growingGlass() else it }
      var present = true
      val rect = Rect(32f, 282f, 144f, 382f)
      state.transitionOf = { FluidPageTransition.Expand }
      state.sourceOf = { source }
      state.resolveSource = { if (present) FluidTransitionSourceGeometry(rect, RectangleShape) else null }
      state.onStack(listOf(0))
      state.onStack(listOf(0, 1))
      state.pagePlaced(1)
      repeat(14) { tick() }
      fun growth() = ((state.zoomShape()!!.bounds.height - rect.height) / (state.height - rect.height)).coerceIn(0f, 1f)
      assertEquals(.18f * growth(), state.dimOn(0), .00001f)
      val before = state.dimOn(0)
      val token = state.beginDrag(1)!!
      assertEquals(before, state.dimOn(0), .00001f)
      state.dragTo(token, state.travelPx + 25f)
      assertEquals(.18f * growth(), state.dimOn(0), .00001f)
      state.release(token, 0f, decided = false)
      settle()
      val back = state.beginDrag(-1)!!
      state.dragTo(back, 160f)
      assertEquals(.18f * growth().pow(.55f), state.dimOn(0), .00001f)
      val held = state.dimOn(0)
      present = false
      state.resizePage(800f, 1400f)
      assertEquals(held, state.dimOn(0), .00001f)
      state.onBack = { state.onStack(listOf(0)) }
      state.release(back, 0f, decided = true)
      tick()
      assertEquals(held, state.dimOn(0), .00001f)
      settle()
      assertEquals(0f, state.dimOn(0), 0f)
      state.resizePage(400f, 700f)
    }
  }
}
