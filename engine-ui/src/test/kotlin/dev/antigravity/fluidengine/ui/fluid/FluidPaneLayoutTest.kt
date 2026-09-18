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
  fun `un tablet in orizzontale ha la barra laterale e un pannello, mai tre`() {
    val browsing = fluidPaneLayout(1204.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.Side, FluidPaneRole.List), browsing.panes)
    assertEquals(280.dp, browsing.sideWidth)
    assertEquals(924.dp, browsing.listWidth)
    assertFalse(browsing.showRail)

    val reading = fluidPaneLayout(1204.dp, sizes, hasSide = true, hasRail = true, showDetail = true)
    assertEquals(listOf(FluidPaneRole.Side, FluidPaneRole.Detail), reading.panes)
    assertEquals(924.dp, reading.detailWidth)
  }

  @Test
  fun `su una finestra larga elenco e dettaglio restano due posti distinti`() {
    assertTrue(fluidPaneLayout(1204.dp, sizes, hasSide = true, hasRail = true).splits)
    // Stretta: aprire una nota la impila sopra l'elenco, come sul telefono.
    assertFalse(fluidPaneLayout(753.dp, sizes, hasSide = true, hasRail = true).splits)
    assertFalse(fluidPaneLayout(411.dp, sizes, hasSide = true, hasRail = true).splits)
  }

  @Test
  fun `un tablet in ritratto ha il rail e un pannello solo`() {
    val list = fluidPaneLayout(753.dp, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), list.panes)
    assertTrue(list.showRail)
    assertEquals(753.dp - 112.dp, list.listWidth)

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
  fun `se quello che si legge scenderebbe sotto il minimo, la barra laterale si sfila`() {
    val tight = FluidPaneSizes(side = 280.dp, list = 380.dp, detailMin = 800.dp, rail = 112.dp)
    val layout = fluidPaneLayout(1050.dp, tight, hasSide = true, hasRail = true, showDetail = true)
    assertEquals(listOf(FluidPaneRole.Detail), layout.panes)
    assertTrue(layout.showRail)
    assertEquals(938.dp, layout.detailWidth)
  }

  @Test
  fun `una misura non finita e' un pannello solo`() {
    val layout = fluidPaneLayout(Dp.Infinity, sizes, hasSide = true, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), layout.panes)
    assertFalse(layout.showRail)
  }

  @Test
  fun `senza barra laterale dichiarata resta il rail, anche su una finestra larga`() {
    val layout = fluidPaneLayout(1204.dp, sizes, hasSide = false, hasRail = true)
    assertEquals(listOf(FluidPaneRole.List), layout.panes)
    assertTrue(layout.showRail)
    assertEquals(1204.dp - 112.dp, layout.listWidth)
    assertTrue(layout.splits)
  }
}
