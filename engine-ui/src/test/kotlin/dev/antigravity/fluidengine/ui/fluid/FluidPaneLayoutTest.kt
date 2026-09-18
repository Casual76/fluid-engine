package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPaneLayoutTest {

  private val sizes = FluidPaneSizes(side = 280.dp, list = 380.dp, detailMin = 420.dp, rail = 112.dp)

  @Test
  fun `un tablet in orizzontale ha tre pannelli, e le larghezze fanno la somma esatta`() {
    val layout = fluidPaneLayout(1204.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.Side, FluidPaneRole.List, FluidPaneRole.Detail), layout.panes)
    assertFalse(layout.showRail)
    assertEquals(280.dp, layout.sideWidth)
    assertEquals(380.dp, layout.listWidth)
    assertEquals(544.dp, layout.detailWidth)
    assertEquals(1204.dp, layout.sideWidth + layout.listWidth + layout.detailWidth)
    assertTrue(layout.twoPane)
  }

  @Test
  fun `un tablet in ritratto ha il rail e un pannello solo`() {
    val list = fluidPaneLayout(753.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), list.panes)
    assertTrue(list.showRail)
    assertEquals(753.dp - 112.dp, list.listWidth)
    assertFalse(list.twoPane)

    val detail = fluidPaneLayout(753.dp, sizes, hasSide = true, hasRail = true, showDetail = true)
    assertEquals(listOf(FluidPaneRole.Detail), detail.panes)
    assertTrue(detail.showRail)
  }

  @Test
  fun `un telefono ha un pannello solo e niente rail`() {
    val layout = fluidPaneLayout(411.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), layout.panes)
    assertFalse(layout.showRail)
    assertEquals(411.dp, layout.listWidth)
  }

  @Test
  fun `quando il dettaglio scenderebbe sotto il minimo, la barra laterale si sfila prima della lista`() {
    // 1000 - 280 - 380 = 340 < 420: niente barra laterale; col rail 1000 - 112 - 380 = 508: due pannelli.
    val layout = fluidPaneLayout(1000.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List, FluidPaneRole.Detail), layout.panes)
    assertTrue(layout.showRail)
    assertEquals(508.dp, layout.detailWidth)
  }

  @Test
  fun `senza posto nemmeno per due pannelli si torna a uno, col rail`() {
    val tight = FluidPaneSizes(side = 280.dp, list = 380.dp, detailMin = 600.dp, rail = 112.dp)
    val layout = fluidPaneLayout(1000.dp, tight, hasSide = true, hasRail = true, showDetail = true)
    assertEquals(listOf(FluidPaneRole.Detail), layout.panes)
    assertTrue(layout.showRail)
    assertEquals(888.dp, layout.detailWidth)
  }

  @Test
  fun `una misura non finita e' un pannello solo`() {
    val layout = fluidPaneLayout(Dp.Infinity, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), layout.panes)
    assertFalse(layout.showRail)
  }

  @Test
  fun `senza barra laterale dichiarata, oltre il limite largo stanno lista e dettaglio col rail`() {
    val layout = fluidPaneLayout(1204.dp, sizes, hasSide = false, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List, FluidPaneRole.Detail), layout.panes)
    assertTrue(layout.showRail)
    assertEquals(1204.dp - 112.dp - 380.dp, layout.detailWidth)
  }
}
