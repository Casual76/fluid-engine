package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Quanto e' largo un menu (3.0.1): 252 dp se le voci ci stanno, di piu' per una voce lunga fino a 340 dp,
 * mai oltre lo schermo. Prima era sempre 252, e una voce che porta il suo stato lo perdeva nei puntini.
 * Densita' 1, cosi' i pixel sono dp.
 */
class FluidMenuPanelWidthTest {

  private val metrics = FluidMenuPanelMetrics(
    widthPx = 252f,
    rowHeightPx = 48f,
    paddingPx = 6f,
    sectionGapPx = 8f,
    radiusPx = 22f,
    gapPx = 10f,
    marginPx = 16f,
    edgeMinPx = 6f,
    refHeightPx = 240f,
  )

  private fun width(labels: List<Float>, icon: Boolean = false, check: Boolean = false, host: Float = 1080f) =
    fluidMenuPanelWidth(metrics, labels, hasIcon = icon, hasCheck = check, density = 1f, hostWidthPx = host)

  @Test
  fun `voci corte, la misura di sempre`() {
    assertEquals(252f, width(listOf(80f, 120f)), 0f)
    assertEquals(252f, width(emptyList()), 0f)
  }

  @Test
  fun `una voce lunga allarga il pannello quanto serve, icona e spunta comprese`() {
    // 220 di testo + 32 di margini + 12 di respiro verticale contato come margine.
    val plain = width(listOf(220f))
    assertTrue("$plain", plain > 252f && plain < 340f)
    // Icona e spunta: un segno e lo spazio fra lui e l'etichetta, per ognuna. Cambio voluto della 4.0 (F1A.11, R6): il
    // segno e' passato da 20 a 22 dp, quindi 68 dp in tutto e non piu' 64; con la voce da 230 di prima si toccava il
    // tetto di 340, e il test non provava piu' l'aggiunta. Ora la legge dalle costanti della riga.
    val marks = 2f * (FluidMenuRowGlyph.value + FluidMenuRowGap.value)
    assertEquals(68f, marks, 0f)
    assertEquals(plain + marks, width(listOf(220f), icon = true, check = true), 0.01f)
  }

  @Test
  fun `oltre il tetto restano i puntini, e lo schermo stretto vince`() {
    assertEquals(340f, width(listOf(600f)), 0f)
    // Uno schermo da 300: 300 meno i due margini da 16.
    assertEquals(268f, width(listOf(600f), host = 300f), 0f)
    // Piu' stretto della misura di sempre: resta la misura di sempre (la scelta di prima).
    assertEquals(252f, width(listOf(600f), host = 200f), 0f)
  }
}
