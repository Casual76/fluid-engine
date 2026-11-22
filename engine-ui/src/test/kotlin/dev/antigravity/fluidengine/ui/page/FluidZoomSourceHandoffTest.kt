package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidZoomSourceHandoffTest {
  private val clock = BroadcastFrameClock()
  private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
  private val source = FluidTransitionSource("terza", FluidGlassNamespace())
  private val state = FluidPageHostState().also {
    it.scope = scope
    it.width = 400f
    it.height = 600f
    it.transitionOf = { FluidPageTransition.Expand }
    it.sourceOf = { page -> source.takeIf { page == "foto" } }
    it.resolveSource = { FluidTransitionSourceGeometry(Rect(20f, 150f, 120f, 240f), RectangleShape) }
    it.onStack(listOf("griglia"))
    it.onStack(listOf("griglia", "foto"))
  }

  @After fun close() {
    state.releaseSources()
    scope.cancel()
  }

  @Test fun laMollaPuoFinirePrimaDelPrimoDisegno() {
    repeat(120) { clock.sendFrame(it * 16_666_667L) }
    assertFalse(state.isMoving)
    assertFalse(source.isHidden)
    state.zoomFrontDrawn("griglia")
    assertFalse(source.isHidden)
    state.zoomFrontDrawn("foto")
    assertTrue(source.isHidden)
    state.releaseSources()
    assertFalse(source.isHidden)
  }

  @Test fun unDisegnoTardivoDopoLAnnullamentoNonNascondeLaCard() {
    state.onStack(listOf("griglia"))
    state.zoomFrontDrawn("foto")
    assertFalse(source.isHidden)
    state.onStack(listOf("griglia", "foto"))
    assertFalse(source.isHidden)
    state.zoomFrontDrawn("foto")
    assertTrue(source.isHidden)
  }
}
