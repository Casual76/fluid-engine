package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.glassAccessibleFilm
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedBody
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
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

  @Test
  fun `il contorno Liquid diventa netto e l'inchiostro regge uno sfondo opposto`() {
    for (role in GlassRole.entries) for (dark in listOf(false, true)) {
      val before = glassRecipe(role, 300f, 120f, dark)
      val high = glassRecipe(role, 300f, 120f, dark, accessibility = GlassMaterialAccessibility(contrast = 1f))
      assertTrue("$role, dark=$dark", high.rimDark.alpha > before.rimDark.alpha)
      assertEquals(0f, high.rimLift, 0f)
      assertTrue(high.rimWidthDp >= 0.85f)
      val ink = if (dark) Color(0xFFF2F2F5) else Color(0xFF121214)
      val background = if (dark) Color.White else Color.Black
      val modes = listOf(
        GlassMaterialAccessibility(contrast = 0.5f),
        GlassMaterialAccessibility(contrast = 1f),
        GlassMaterialAccessibility(reduceTransparency = true),
      )
      for (mode in modes) for (size in listOf(44f, 58f, 120f)) {
        val recipe = glassRecipe(role, 300f, size, dark, accessibility = mode)
        val body = glassPaintedBody(recipe, background)
        val luminances = listOf(ink.luminance(), body.luminance()).sorted()
        val ratio = (luminances[1] + 0.05f) / (luminances[0] + 0.05f)
        assertTrue("$role, dark=$dark, size=$size, $mode: $ratio", ratio >= 4.5f)
      }
    }
  }

  @Test
  fun `la trasparenza ridotta copre anche il limpido e le pellicole fisse senza cambiare la cattura`() {
    val reduced = GlassMaterialAccessibility(reduceTransparency = true)
    val clear = glassRecipe(GlassRole.Floating, 120f, 48f, true, variant = GlassLiquidVariant.Clear)
    val solidClear = glassRecipe(
      GlassRole.Floating, 120f, 48f, true, variant = GlassLiquidVariant.Clear, accessibility = reduced,
    )
    assertEquals(0f, clear.veilColor.alpha, 0f)
    assertEquals(0.96f, solidClear.veilColor.alpha, Quantum)
    assertEquals(0f, solidClear.veilAdapt, 0f)
    assertEquals(clear.blurDp, solidClear.blurDp, 0f)
    assertEquals(clear.paddingDp, solidClear.paddingDp, 0f)
    assertEquals(clear.captureScale, solidClear.captureScale, 0f)
    val custom = Color(0x33445566)
    val fixed = glassRecipe(GlassRole.Modal, 300f, 180f, true, film = custom, accessibility = reduced)
    assertEquals(custom.copy(alpha = 0.96f), fixed.tintColor)
    assertEquals(fixed.tintColor, custom.glassAccessibleFilm(reduced))
    assertEquals(Color.Unspecified, Color.Unspecified.glassAccessibleFilm(reduced))
  }

  @Test
  fun `la finestra inattiva conserva il contrasto e può ritornare alla ricetta esatta`() {
    val high = GlassMaterialAccessibility(contrast = 1f)
    val normal = glassRecipe(GlassRole.Floating, 300f, 64f, false, accessibility = high)
    val reused = glassRecipe(GlassRole.Floating, 300f, 64f, false, accessibility = high.copy(receded = true))
    assertTrue(reused.veilColor.alpha > normal.veilColor.alpha)
    assertTrue(reused.lightColor.alpha < normal.lightColor.alpha)
    assertEquals(normal.rimDark, reused.rimDark)
    assertEquals(normal.paddingDp, reused.paddingDp, 0f)
    glassRecipe(GlassRole.Floating, 300f, 64f, false, accessibility = high, out = reused)
    assertEquals(normal.veilColor, reused.veilColor)
    assertEquals(normal.lightColor, reused.lightColor)
  }
}
