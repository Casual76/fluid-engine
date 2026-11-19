package dev.antigravity.fluidengine.ui.fluid.toolbar

import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import org.junit.Assert.assertEquals
import org.junit.Test

/** Prova del confine: solo l'adattatore legge i Dp, il risolutore riceve numeri dai token reali. */
class FluidToolbarMetricsTokenTest {
  @Test fun `risolutore legge le misure del motore attraverso ladattatore`() {
    val metrics = FluidToolbarMetrics(FluidMetrics.GroupItemPitch.value, FluidMetrics.GroupSpacing.value,
      FluidMetrics.ScreenEdgeMargin.value, FluidMetrics.GlassButtonLens.value)
    val state = resolveFluidToolbar(FluidToolbarModel(listOf(FluidToolbarItem("a"), FluidToolbarItem("b"))),
      widthDp = 360f, metrics = metrics)
    assertEquals(46f, state.element("b")!!.leftDp - state.element("a")!!.leftDp, 0f)
    assertEquals(16f, 360f - state.groups.single().leftDp - state.groups.single().widthDp, 0f)
  }
}
