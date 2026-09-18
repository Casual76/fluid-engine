package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidReadingWidthTest {

  @Test
  fun `su un telefono il margine e' il minimo`() {
    assertEquals(20.dp, fluidScreenPadding(available = 411.dp, horizontalPadding = 20.dp, contentMaxWidth = 760.dp))
  }

  @Test
  fun `su un tablet in orizzontale il margine cresce e il contenuto resta alla misura di lettura`() {
    val padding = fluidScreenPadding(available = 1204.dp, horizontalPadding = 20.dp, contentMaxWidth = 760.dp)
    assertEquals(222.dp, padding)
    assertEquals(760.dp, 1204.dp - padding * 2)
  }

  @Test
  fun `senza una misura nota si tiene il minimo, e un limite infinito spegne il centraggio`() {
    assertEquals(20.dp, fluidScreenPadding(available = Dp.Infinity, horizontalPadding = 20.dp, contentMaxWidth = 760.dp))
    assertEquals(20.dp, fluidScreenPadding(available = 1204.dp, horizontalPadding = 20.dp, contentMaxWidth = Dp.Infinity))
  }

  @Test
  fun `una pagina appena piu' larga del limite non scende sotto il margine minimo`() {
    assertEquals(20.dp, fluidScreenPadding(available = 780.dp, horizontalPadding = 20.dp, contentMaxWidth = 760.dp))
  }

  @Test
  fun `la colonna di lettura sta nel mezzo, e in uno spazio stretto lo occupa tutto`() {
    assertEquals(FluidReadingSpan(start = 222, width = 760), fluidReadingSpan(available = 1204, max = 760))
    assertEquals(FluidReadingSpan(start = 0, width = 411), fluidReadingSpan(available = 411, max = 760))
    assertEquals(FluidReadingSpan(start = 0, width = 0), fluidReadingSpan(available = 0, max = 760))
  }
}
