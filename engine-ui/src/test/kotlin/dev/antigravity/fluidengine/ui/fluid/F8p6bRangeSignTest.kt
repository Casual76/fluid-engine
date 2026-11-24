package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.6b · Due dita alla maniera di Borsa, i conti puri: il segno di ogni differenza (per il colore della capsula, delle
 * linee e dei punti), il segno di tutto l'intervallo, e il verde e il rosso che si leggono a 4,5:1 nei due temi.
 */
class F8p6bRangeSignTest {

  private val italian = Locale.ITALY

  private fun model(values: List<List<Float?>>, series: List<String> = emptyList(), signed: Boolean = true) = FluidChartScrubModel(
    xLabels = values.first().indices.map { "p$it" },
    seriesLabels = series,
    values = values,
    colors = values.map { Color.Blue },
    format = { v -> fluidFormatNumber(v.toDouble(), italian) },
    delta = null,
    noValue = "nessun dato",
    labels = FluidDataLabels(),
    locale = italian,
    dots = true,
    signed = signed,
  )

  @Test
  fun `il segno di una differenza`() {
    assertEquals(1, fluidChartSign(6f, 7.5f))
    assertEquals(-1, fluidChartSign(6.5f, 5f))
    assertEquals(0, fluidChartSign(5f, 5f))
    assertEquals(0, fluidChartSign(null, 5f))
    assertEquals(0, fluidChartSign(5f, Float.NaN))
  }

  @Test
  fun `la capsula a pezzi - con un dito senza segno, con due dita ognuno il suo`() {
    val m = model(listOf(listOf(6f, 7.5f, null, 5f)))
    val (one, _) = m.parts(1, -1)
    assertEquals(listOf(FluidChartScrubPart("7,5", 0)), one)
    val (up, label) = m.parts(0, 1)
    assertEquals(listOf(FluidChartScrubPart("+1,5 (+25%)", 1)), up)
    assertEquals("p0 – p1", label)
    assertEquals(listOf(FluidChartScrubPart("−2,5 (−33%)", -1)), m.parts(1, 3).first)
    // Un buco: «nessun dato», senza segno.
    assertEquals(listOf(FluidChartScrubPart("nessun dato", 0)), m.parts(1, 2).first)
    // Il testo intero resta quello di prima.
    assertEquals("+1,5 (+25%)" to "p0 – p1", m.text(1, 0))
  }

  @Test
  fun `piu' serie - ognuna col suo segno, l'intervallo ha un segno solo se vanno tutte nello stesso verso`() {
    val m = model(listOf(listOf(1f, 2f, 1f), listOf(3f, 5f, 4f)), series = listOf("Foto", "Video"))
    val (parts, _) = m.parts(0, 1)
    assertEquals(listOf(FluidChartScrubPart("Foto +1 (+100%)", 1), FluidChartScrubPart("Video +2 (+67%)", 1)), parts)
    assertEquals(1, m.rangeSign(0, 1))
    // Da 1 a 2 scendono tutte e due.
    assertEquals(-1, m.seriesSign(0, 1, 2))
    assertEquals(-1, m.seriesSign(1, 1, 2))
    assertEquals(-1, m.rangeSign(1, 2))
    val mixed = model(listOf(listOf(1f, 2f), listOf(5f, 4f)), series = listOf("A", "B"))
    assertEquals(0, mixed.rangeSign(0, 1))
    assertEquals(1, mixed.seriesSign(0, 0, 1))
    assertEquals(-1, mixed.seriesSign(1, 0, 1))
    // Un dito: nessun segno.
    assertEquals(0, mixed.rangeSign(1, -1))
  }

  @Test
  fun `il verde e il rosso si leggono a 4,5 su ogni superficie dei grafici, nei due temi`() {
    for ((scheme, dark) in listOf(lightColorScheme() to false, darkColorScheme() to true)) {
      val film = if (dark) Color(0xFF3A3A3C) else Color(0xFFF2F2F7)
      val (rise, fall) = fluidChartSignColors(scheme, film, dark)
      for (surface in fluidChartSurfaces(scheme, film)) {
        assertTrue("verde su $surface (scuro $dark): ${fluidContrastRatio(rise, surface)}", fluidContrastRatio(rise, surface) >= 4.5f)
        assertTrue("rosso su $surface (scuro $dark): ${fluidContrastRatio(fall, surface)}", fluidContrastRatio(fall, surface) >= 4.5f)
      }
      // Restano verde e rosso: il canale del segno vince.
      assertTrue(rise.green > rise.red && fall.red > fall.green)
    }
  }
}
