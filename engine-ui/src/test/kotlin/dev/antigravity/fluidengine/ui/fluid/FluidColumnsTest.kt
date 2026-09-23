package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidColumnsTest {

  @Test
  fun `telefono e finestra divisa, una colonna`() {
    assertEquals(1, fluidContentColumns(371.dp))
    assertEquals(1, fluidContentColumns(600.dp))
  }

  @Test
  fun `tablet in verticale due, in orizzontale tre`() {
    assertEquals(2, fluidContentColumns(760.dp))
    assertEquals(3, fluidContentColumns(1120.dp))
  }

  @Test
  fun `mai zero, mai oltre il massimo, e senza misura una`() {
    assertEquals(1, fluidContentColumns(0.dp))
    assertEquals(1, fluidContentColumns(Dp.Unspecified))
    assertEquals(1, fluidContentColumns(Dp.Infinity))
    assertEquals(3, fluidContentColumns(4000.dp))
    assertEquals(2, fluidContentColumns(4000.dp, maxColumns = 2))
  }

  @Test
  fun `metriche non misurate rispondono una colonna`() {
    assertEquals(1, FluidScreenMetrics().columns())
  }

  @Test
  fun `la muratura mette il prossimo nella colonna piu' corta, a parita' la prima`() {
    assertEquals(0, fluidMasonryColumn(intArrayOf(0, 0, 0)))
    assertEquals(1, fluidMasonryColumn(intArrayOf(300, 120, 120)))
    assertEquals(2, fluidMasonryColumn(intArrayOf(300, 200, 100)))
  }
}
