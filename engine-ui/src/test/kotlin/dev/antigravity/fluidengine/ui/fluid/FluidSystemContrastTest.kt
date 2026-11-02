package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il contrasto del sistema (Android 14+) e il vetro: la mappa da livello a densita' della pellicola.
 *
 * Pura e senza Compose attorno, perche' una pellicola troppo timida per essere letta, o cosi' piena
 * da non essere piu' vetro, si vede solo in mano: i numeri sono fissati qui. Il percorso dal sistema
 * al tema (`UiModeManager`, l'ascolto dei cambi) sta in `FluidSystemContrastObserveTest`, nel sample.
 */
class FluidSystemContrastTest {

  /**
   * L'opacita' di un `Color` sRGB sta in un 255-esimo (0,0039): la pellicola e' costruita da un
   * numero in virgola mobile e il colore lo arrotonda, quindi un'uguaglianza esatta sull'alpha sarebbe
   * una prova della precisione del colore e non della formula.
   */
  private val Quantum = 0.01f

  private val barTint = GlassTint(
    overlay = Color(0xFFFAFAFA).copy(alpha = 0.44f),
    fallback = Color(0xFFFFFFFF).copy(alpha = 0.96f),
    hairline = Color.Black.copy(alpha = 0.10f),
  )

  // --- i livelli ---------------------------------------------------------------------------------

  @Test
  fun `i tre livelli del sistema sono 0, mezzo e uno`() {
    assertEquals(0f, FluidSystemContrast.Standard, 0f)
    assertEquals(0.5f, FluidSystemContrast.Medium, 0f)
    assertEquals(1f, FluidSystemContrast.High, 0f)
  }

  @Test
  fun `un valore del sistema fuori scala o non finito si riporta ai livelli`() {
    assertEquals(0f, FluidSystemContrast.level(0f), 0f)
    assertEquals(0.5f, FluidSystemContrast.level(0.5f), 0f)
    assertEquals(1f, FluidSystemContrast.level(1f), 0f)
    // Meno del standard non e' un'offerta delle impostazioni: vale standard, mai "piu' trasparente".
    assertEquals(0f, FluidSystemContrast.level(-1f), 0f)
    assertEquals(0f, FluidSystemContrast.level(-0.2f), 0f)
    assertEquals(1f, FluidSystemContrast.level(3f), 0f)
    assertEquals(0f, FluidSystemContrast.level(Float.NaN), 0f)
    assertEquals(0f, FluidSystemContrast.level(Float.POSITIVE_INFINITY), 0f)
    assertEquals(0f, FluidSystemContrast.level(Float.NEGATIVE_INFINITY), 0f)
  }

  // --- livello -> fattore -------------------------------------------------------------------------

  @Test
  fun `il velo e' zero a standard, il quaranta per cento a medio, l'ottantacinque ad alto`() {
    assertEquals(0f, FluidSystemContrast.veil(FluidSystemContrast.Standard), 0f)
    assertEquals(0.40f, FluidSystemContrast.veil(FluidSystemContrast.Medium), 1e-6f)
    assertEquals(0.85f, FluidSystemContrast.veil(FluidSystemContrast.High), 1e-6f)
  }

  @Test
  fun `il velo cresce con il contrasto, senza salti e senza uscire da zero e uno`() {
    var previous = FluidSystemContrast.veil(0f)
    var level = 0f
    while (level <= 1.0001f) {
      val veil = FluidSystemContrast.veil(level)
      assertTrue("a $level il velo $veil non e' in [0, 1]", veil in 0f..1f)
      assertTrue("a $level il velo scende: $veil dopo $previous", veil >= previous - 1e-6f)
      // Un centesimo di contrasto sposta il velo di pochi centesimi: nessuno scalino fra i livelli.
      assertTrue("a $level il velo salta di ${veil - previous}", veil - previous <= 0.031f)
      previous = veil
      level += 0.01f
    }
  }

  @Test
  fun `un valore fuori scala ha il velo del livello piu' vicino`() {
    assertEquals(0f, FluidSystemContrast.veil(-1f), 0f)
    assertEquals(0f, FluidSystemContrast.veil(Float.NaN), 0f)
    assertEquals(0.85f, FluidSystemContrast.veil(2f), 1e-6f)
  }

  // --- fattore -> pellicola -----------------------------------------------------------------------

  @Test
  fun `a contrasto standard la pellicola e' la stessa istanza`() {
    assertSame(barTint, barTint.forContrast(FluidSystemContrast.Standard))
    assertSame(barTint, barTint.forContrast(-1f))
    assertSame(barTint, barTint.forContrast(Float.NaN))
  }

  @Test
  fun `una barra al 44 per cento sale al 66 a medio e al 92 ad alto`() {
    val medium = barTint.forContrast(FluidSystemContrast.Medium)
    val high = barTint.forContrast(FluidSystemContrast.High)
    // a' = a + (1 - a) * velo.
    assertEquals(0.44f + 0.56f * 0.40f, medium.overlay.alpha, Quantum)
    assertEquals(0.44f + 0.56f * 0.85f, high.overlay.alpha, Quantum)
    assertEquals(0.664f, medium.overlay.alpha, Quantum)
    assertEquals(0.916f, high.overlay.alpha, Quantum)
    assertNotSame(barTint, medium)
  }

  @Test
  fun `la pellicola e' piu' densa a ogni livello che sale, e non diventa mai un muro`() {
    val standard = barTint.overlay.alpha
    val medium = barTint.forContrast(FluidSystemContrast.Medium).overlay.alpha
    val high = barTint.forContrast(FluidSystemContrast.High).overlay.alpha
    assertTrue("$standard < $medium < $high", standard < medium && medium < high)
    // Resta vetro: neppure ad alto contrasto la tinta e' piena.
    assertTrue("ad alto contrasto ${high} e' un muro", high < 1f)
  }

  @Test
  fun `una tinta quasi vuota si riempie di piu' in assoluto, una gia' piena resta piena`() {
    val faint = GlassTint(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.10f), Color.Transparent)
    val solid = GlassTint(Color.White, Color.White, Color.Transparent)
    assertEquals(0.10f + 0.90f * 0.85f, faint.forContrast(1f).overlay.alpha, Quantum)
    assertEquals(1f, solid.forContrast(1f).overlay.alpha, 0f)
    assertEquals(1f, solid.forContrast(0.5f).fallback.alpha, 0f)
  }

  @Test
  fun `il ripiego sale come la tinta`() {
    // Sotto API 31 non c'e' sfocatura: il ripiego e' l'unica cosa che si vede, e deve salire anche lui.
    val high = barTint.forContrast(FluidSystemContrast.High)
    assertEquals(0.96f + 0.04f * 0.85f, high.fallback.alpha, Quantum)
    assertTrue(high.fallback.alpha > barTint.fallback.alpha)
  }

  @Test
  fun `il colore della pellicola non cambia, solo quanto copre`() {
    val high = barTint.forContrast(FluidSystemContrast.High)
    assertEquals(barTint.overlay.red, high.overlay.red, 0f)
    assertEquals(barTint.overlay.green, high.overlay.green, 0f)
    assertEquals(barTint.overlay.blue, high.overlay.blue, 0f)
    assertEquals(barTint.fallback.red, high.fallback.red, 0f)
    assertEquals(barTint.hairline.red, high.hairline.red, 0f)
  }

  // --- il bordo ---------------------------------------------------------------------------------

  @Test
  fun `il bordo diventa piu' netto, di 2,5 volte in piu' ad alto contrasto e meno a medio`() {
    val medium = barTint.forContrast(FluidSystemContrast.Medium).hairline.alpha
    val high = barTint.forContrast(FluidSystemContrast.High).hairline.alpha
    // 0,10 * (1 + 2,5) = 0,35 ad alto; a medio il guadagno e' la parte 0,40 / 0,85 di quello pieno.
    assertEquals(0.35f, high, Quantum)
    assertEquals(0.10f * (1f + 2.5f * 0.40f / 0.85f), medium, Quantum)
    assertTrue(barTint.hairline.alpha < medium && medium < high)
  }

  @Test
  fun `un bordo trasparente resta trasparente, perche' chi non lo vuole non lo riceve`() {
    val none = barTint.copy(hairline = Color.Transparent)
    assertEquals(0f, none.forContrast(1f).hairline.alpha, 0f)
    assertEquals(Color.Transparent, none.forContrast(0.5f).hairline)
  }

  @Test
  fun `il bordo non supera il tetto del 70 per cento, e uno che lo supera gia' non si abbassa`() {
    val strong = barTint.copy(hairline = Color.Black.copy(alpha = 0.40f))
    assertEquals(0.70f, strong.forContrast(1f).hairline.alpha, Quantum)
    val above = barTint.copy(hairline = Color.Black.copy(alpha = 0.90f))
    assertEquals(0.90f, above.forContrast(1f).hairline.alpha, Quantum)
  }

  @Test
  fun `un colore non specificato resta com'e', senza un'opacita' inventata`() {
    val odd = GlassTint(Color.Unspecified, Color.Unspecified, Color.Unspecified)
    val high = odd.forContrast(1f)
    assertEquals(Color.Unspecified, high.overlay)
    assertEquals(Color.Unspecified, high.fallback)
    assertEquals(Color.Unspecified, high.hairline)
  }
}
