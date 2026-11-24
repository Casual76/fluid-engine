package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * F8.7 · I conti del selettore di colore, puri: l'esadecimale che si legge e si scrive, i nomi dei colori per TalkBack,
 * lo spettro che va e torna, la griglia di iOS e i colori recenti.
 */
class F8p7ColorPickerMathTest {

  private fun near(a: Color, b: Color, tol: Float = 1.5f / 255f) =
    abs(a.red - b.red) <= tol && abs(a.green - b.green) <= tol && abs(a.blue - b.blue) <= tol

  @Test
  fun `l'esadecimale si scrive in maiuscolo e si rilegge uguale`() {
    assertEquals("#FF3B30", fluidColorHex(Color(0xFFFF3B30)))
    assertEquals("#007AFF80", fluidColorHex(Color(0x80007AFF), withAlpha = true))
    for (argb in listOf(0xFF000000, 0xFFFFFFFF, 0xFF123456, 0xFFABCDEF, 0xFF7F7F7F)) {
      val c = Color(argb)
      assertEquals(c.toArgb(), fluidParseHexColor(fluidColorHex(c))!!.toArgb())
    }
  }

  @Test
  fun `l'esadecimale si legge in tutte le forme e rifiuta il resto`() {
    assertEquals(Color(0xFFFF0000).toArgb(), fluidParseHexColor("#f00")!!.toArgb())
    assertEquals(Color(0xFF34C759).toArgb(), fluidParseHexColor(" 34c759 ")!!.toArgb())
    assertEquals(Color(0x8034C759).toArgb(), fluidParseHexColor("#34C75980")!!.toArgb())
    assertNull(fluidParseHexColor(""))
    assertNull(fluidParseHexColor("#12"))
    assertNull(fluidParseHexColor("#12345"))
    assertNull(fluidParseHexColor("GGGGGG"))
    assertNull(fluidParseHexColor("#1234567"))
  }

  @Test
  fun `i colori si dicono a parole`() {
    assertEquals("rosso", fluidColorName(Color(0xFFFF3B30)))
    assertEquals("blu", fluidColorName(Color(0xFF007AFF)))
    assertEquals("verde", fluidColorName(Color(0xFF34C759)))
    assertEquals("giallo", fluidColorName(Color(0xFFFFCC00)))
    assertEquals("bianco", fluidColorName(Color.White))
    assertEquals("nero", fluidColorName(Color.Black))
    assertEquals("grigio", fluidColorName(Color(0xFF8E8E93)))
    assertEquals("blu scuro", fluidColorName(Color(0xFF011D57)))
    assertEquals("azzurro chiaro", fluidColorName(Color(0xFFCBF0FF)))
    assertEquals("marrone", fluidColorName(Color(0xFF8B5A2B)))
    assertEquals("marrone", fluidColorName(Color(0xFFA2845E)))
    assertEquals("arancione", fluidColorName(Color(0xFFFF9500)))
    assertEquals("trasparente", fluidColorName(Color.Transparent))
    assertEquals("rosso, opacità 50 %", fluidColorName(Color(0xFFFF3B30).copy(alpha = 0.5f)))
  }

  @Test
  fun `lo spettro va dal bianco al pieno al nero e torna dove era`() {
    assertTrue(near(spectrumColor(0f, 0f), Color.White))
    assertTrue(near(spectrumColor(0.5f, 1f), Color.Black))
    assertTrue(near(spectrumColor(0f, 0.5f), Color.Red))
    assertTrue(near(spectrumColor(1f / 3f, 0.5f), Color.Green))
    assertTrue(near(spectrumColor(2f / 3f, 0.5f), Color.Blue))
    for (x in listOf(0.1f, 0.37f, 0.62f, 0.9f)) for (y in listOf(0.1f, 0.3f, 0.5f, 0.7f, 0.95f)) {
      val c = spectrumColor(x, y)
      val p = spectrumPosition(c, hue = 0f)
      assertTrue("x $x -> ${p.x}", abs(p.x - x) < 0.01f)
      assertTrue("y $y -> ${p.y}", abs(p.y - y) < 0.01f)
    }
    // Un grigio non ha tinta: il segno resta sulla tinta ricordata.
    assertEquals(0.25f, spectrumPosition(Color(0xFF808080), hue = 90f).x, 0.001f)
  }

  @Test
  fun `HSV va e torna`() {
    for (c in listOf(Color(0xFFFF3B30), Color(0xFF5856D6), Color(0xFF00C7BE), Color(0xFF7F7F7F), Color.White)) {
      val hsv = colorToHsv(c)
      assertTrue("$c", near(hsvColor(hsv[0], hsv[1], hsv[2]), c))
    }
  }

  @Test
  fun `la griglia e' quella di iOS, dodici per dieci, grigi in cima e dallo scuro al chiaro`() {
    val grid = FluidColorGrid
    assertEquals(120, grid.size)
    assertEquals(Color.White.toArgb(), grid[0].toArgb())
    assertEquals(Color.Black.toArgb(), grid[11].toArgb())
    // La riga piena (la quinta di colore) ha i colori di iOS; la prima di colore e' la piu' scura, l'ultima la piu' chiara.
    assertEquals(Color(0xFF0062FE).toArgb(), grid[5 * 12 + 1].toArgb())
    assertEquals(Color(0xFF00C7FC).toArgb(), grid[6 * 12].toArgb())
    for (col in 0 until 12) {
      val top = grid[12 + col]
      val bottom = grid[9 * 12 + col]
      assertTrue("colonna $col", top.red + top.green + top.blue < bottom.red + bottom.green + bottom.blue)
    }
    // Tutti diversi, cosi' la casella scelta e' una sola.
    assertEquals(120, grid.map { it.toArgb() }.distinct().size)
    assertEquals(5 * 12 + 1, gridIndexOf(Color(0xFF0062FE)))
    assertEquals(5 * 12 + 1, gridIndexOf(Color(0xFF0061FE)))
    assertEquals(-1, gridIndexOf(Color(0xFF0062FA)))
  }

  @Test
  fun `i recenti tengono l'ultimo davanti, senza doppioni, fino al massimo`() {
    val saved = mutableListOf<List<Color>>()
    val recents = FluidRecentColors(capacity = 3, onChange = { saved += it })
    recents.add(Color.Red)
    recents.add(Color.Blue)
    recents.add(Color.Red)
    assertEquals(listOf(Color.Red, Color.Blue), recents.colors)
    recents.add(Color.Green)
    recents.add(Color.Yellow)
    assertEquals(listOf(Color.Yellow, Color.Green, Color.Red), recents.colors)
    // Rimettere davanti il primo non e' un cambio.
    val before = saved.size
    recents.add(Color.Yellow)
    assertEquals(before, saved.size)
    assertEquals(recents.colors, saved.last())
    recents.clear()
    assertTrue(recents.colors.isEmpty())
  }
}
