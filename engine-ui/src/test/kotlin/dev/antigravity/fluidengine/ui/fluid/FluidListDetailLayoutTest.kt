package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidListDetailLayoutTest {

  @Test
  fun `telefono, finestra divisa e tablet in verticale restano una pagina sola`() {
    assertFalse(fluidListDetailLayout(411.dp).twoPane)
    assertFalse(fluidListDetailLayout(640.dp).twoPane)
    assertFalse(fluidListDetailLayout(800.dp).twoPane)
  }

  @Test
  fun `tablet in orizzontale, elenco al massimo e il resto al dettaglio`() {
    val layout = fluidListDetailLayout(1280.dp)
    assertTrue(layout.twoPane)
    assertEquals(500.dp, layout.listWidth)
    assertEquals(780.dp, layout.detailWidth)
  }

  @Test
  fun `appena c'e' posto, elenco al minimo e dettaglio mai sotto il suo`() {
    val layout = fluidListDetailLayout(840.dp)
    assertTrue(layout.twoPane)
    assertEquals(360.dp, layout.listWidth)
    assertEquals(480.dp, layout.detailWidth)
  }

  @Test
  fun `in mezzo l'elenco segue la finestra`() {
    val layout = fluidListDetailLayout(1000.dp)
    assertEquals(400.dp, layout.listWidth)
  }

  @Test
  fun `una misura non nota da' una pagina sola`() {
    assertFalse(fluidListDetailLayout(Dp.Infinity).twoPane)
  }
}
