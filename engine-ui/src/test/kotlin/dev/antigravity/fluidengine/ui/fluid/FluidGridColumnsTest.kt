package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidGridColumnsTest {

  @Test
  fun `su un telefono e in un pannello lista due colonne`() {
    assertEquals(2, fluidGridColumns(371.dp))
    assertEquals(2, fluidGridColumns(340.dp))
  }

  @Test
  fun `in una colonna di lettura quattro`() {
    assertEquals(3, fluidGridColumns(520.dp))
    assertEquals(4, fluidGridColumns(720.dp))
  }

  @Test
  fun `mai zero, mai oltre il massimo`() {
    assertEquals(1, fluidGridColumns(0.dp))
    assertEquals(1, fluidGridColumns(120.dp))
    assertEquals(1, fluidGridColumns(Dp.Infinity))
    assertEquals(5, fluidGridColumns(2000.dp))
    assertEquals(3, fluidGridColumns(2000.dp, maxColumns = 3))
  }
}
