package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidAccentPresets
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il vetro tinto di F6B.16: la tinta è vetro (la tinta di Apple sopra il velo, non una pellicola), più chiara e meno satura
 * dell'accento, col filo di luce e l'ombra del suo colore; lo scritto sta ad almeno 4,5:1 in chiaro e in scuro su ogni
 * accento della palette con la dose di serie (la C della tavola, scelta di Alessio); col vetro di prima il tasto in
 * evidenza resta quello di sempre.
 */
class FluidTintedGlassTest {

  private val base = GlassTint(
    overlay = Color(0xFF90A0B0).copy(alpha = 0.38f),
    fallback = Color(0xFFF2F2F7).copy(alpha = 0.9f),
    hairline = Color.White.copy(alpha = 0.3f),
    mode = GlassTintMode.Neutral,
  )

  /** Gli accenti della palette, chiari e scuri, il marchio di serie e i due rossi d'errore del tema. */
  private val accents: List<Pair<Color, Boolean>> =
    (fluidAccentPresets + FluidDefaultBrand).flatMap { listOf(it.resolve(false) to false, it.resolve(true) to true) } +
      listOf(Color(0xFFD70015) to false, Color(0xFFFF6961) to true)

  @Test
  fun `la dose di serie e' la C della tavola, la scelta di Alessio`() {
    assertEquals(FluidTintedGlassMix(lighten = 0.10f, chroma = 0.95f, coverage = 0.78f), FluidTintedGlassProfile.Shipped)
  }

  @Test
  fun `la tinta e' l'accento schiarito e spento, con la stessa tonalita', e copre meno del tutto`() {
    val mix = FluidTintedGlassProfile.Shipped
    for ((accent, _) in accents) {
      val tint = fluidTintedGlassColor(accent)
      val a = oklab(accent)
      val t = oklab(tint.copy(alpha = 1f))
      assertTrue("$accent: più luminosa (${t[0]} contro ${a[0]})", t[0] >= a[0] - 1e-3f)
      assertTrue("$accent: meno satura", hypot(t[1], t[2]) <= hypot(a[1], a[2]) + 1e-3f)
      // L'alfa di un colore sRGB sta in 8 bit: mezzo livello di tolleranza.
      assertEquals("$accent: copre quanto dice la dose", mix.coverage, tint.alpha, 0.5f / 255f + 1e-6f)
      assertTrue("$accent: il vetro si vede", tint.alpha < 1f)
    }
  }

  @Test
  fun `la tinta e' mescolata al vetro, non stesa sopra`() {
    val accent = FluidDefaultBrand.resolve(false)
    val tint = fluidTintedGlassTint(accent, base)
    assertEquals("la tinta di Apple: sopra il velo, segue lo sfondo", GlassTintMode.Colored, tint.mode)
    assertEquals(GlassTintMode.Colored, glassTintLiquidMode(tint))
    assertEquals(base.hairline, tint.hairline)
    // Sul nero la stessa tinta è più scura che sul bianco: la luminosità segue ciò che il vetro ha dietro (R4 A7).
    val onWhite = fluidTintedGlassBody(accent, Color.White, darkSide = false)
    val onBlack = fluidTintedGlassBody(accent, Color.Black, darkSide = true)
    assertTrue(fluidContrastRatio(onWhite, Color.Black) > fluidContrastRatio(onBlack, Color.Black))
  }

  @Test
  fun `il vetro tinto ha il filo di luce della tinta, il riflesso e l'ombra del suo colore`() {
    for ((accent, dark) in accents) {
      val tint = fluidTintedGlassColor(accent)
      val tinted = glassRecipe(GlassRole.Interactive, 96f, 40f, dark, optics = GlassDefaults.buttonOptics(), tint = tint)
      val plain = glassRecipe(GlassRole.Interactive, 96f, 40f, dark, optics = GlassDefaults.buttonOptics())
      assertTrue("$accent: c'è il filo", tinted.drawsRim)
      assertTrue("$accent: c'è il riflesso", tinted.drawsReflex)
      assertNotEquals("$accent: il filo prende la tinta", plain.rimDark, tinted.rimDark)
      assertNotEquals("$accent: l'ombra prende la tinta", plain.shadowColor, tinted.shadowColor)
      assertEquals("$accent: la tinta è quella di Apple", 1f, tinted.tintMode, 0f)
    }
  }

  @Test
  fun `lo scritto sul vetro tinto sta ad almeno 4,5 a 1 in chiaro e in scuro, su ogni accento`() {
    val report = StringBuilder()
    for ((accent, _) in accents) {
      for (dark in listOf(false, true)) {
        val ink = fluidTintedGlassInk(accent, dark)
        val worst = fluidTintedGlassWorstContrast(accent, ink, dark)
        report.append("${if (dark) "scuro" else "chiaro"} $accent → ink $ink: %.2f\n".format(worst))
        assertTrue("$accent, ${if (dark) "scuro" else "chiaro"}: $worst:1", worst >= FluidProminentLabelContrast)
      }
    }
    println(report)
  }

  @Test
  fun `l'inchiostro e' quello degli altri vetri quando regge`() {
    // Sul lato chiaro col marchio di serie lo scritto è scuro: quello degli altri vetri, o il nero pieno se quello non regge.
    val light = fluidTintedGlassInk(FluidDefaultBrand.resolve(false), darkSide = false)
    assertTrue("sul lato chiaro uno scritto scuro: $light", light == FluidGlassInkOnLight || light == Color.Black)
    assertEquals(listOf(FluidGlassInkOnLight, Color.Black, Color.White), fluidTintedGlassInkCandidates(false))
    assertTrue("sul lato scuro un inchiostro chiaro", fluidTintedGlassInkCandidates(true).first().luminanceOf() > 0.5f)
  }

  @Test
  fun `il tasto in evidenza e' vetro tinto col materiale nuovo, e quello di prima col vetro di prima`() {
    val accent = FluidDefaultBrand.resolve(false)
    val tinted = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Prominent, base, accent, tinted = true)
    assertEquals(GlassTintMode.Colored, tinted.tint.mode)
    assertEquals(fluidTintedGlassColor(accent), tinted.tint.overlay)
    assertFalse("l'etichetta segue il lato del vetro tinto", tinted.ink.isSpecified)
    assertEquals(accent, tinted.tintedAccent)
    assertTrue(tinted.prominent)
    assertEquals(GlassLiquidVariant.Regular, tinted.variant)
    // Col vetro di prima: la pellicola che regge l'etichetta su ogni fondo, com'era.
    val classic = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Prominent, base, accent)
    assertEquals(GlassTintMode.Fixed, classic.tint.mode)
    assertTrue(classic.ink.isSpecified)
    assertTrue(classic.prominent)
    // Gli altri stili non cambiano.
    assertSame(base, resolveFluidGlassButtonLook(FluidGlassButtonStyle.Glass, base, accent, tinted = true).tint)
  }

  private fun Color.luminanceOf(): Float = fluidContrastRatio(this, Color.Black).let { (it * 0.05f) - 0.05f }
}
