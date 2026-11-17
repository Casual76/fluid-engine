package dev.antigravity.fluidengine.ui.fluid.toolbar

import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidToolbarExitTransitionTest {
  @Test fun `indietro e Chiudi chiedono la lente senza perdere l'identita e le altre azioni il vicino`() {
    for (role in FluidToolbarRole.entries.filter { it != FluidToolbarRole.Group &&
      it != FluidToolbarRole.FixedSpacer && it != FluidToolbarRole.FlexibleSpacer }) {
      val group = resolveFluidToolbar(FluidToolbarModel(listOf(FluidToolbarItem("tasto", role = role,
        symbol = "segno"))), 400f, metrics = fluidToolbarMetrics()).groups.single()
      assertEquals(role.toString(), if (role == FluidToolbarRole.Back || role == FluidToolbarRole.Close)
        FluidGlassTransition.MaterializeWhenAlone else FluidGlassTransition.MatchedGeometry, fluidToolbarGlassTransition(group))
    }
  }
}
