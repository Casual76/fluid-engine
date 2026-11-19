package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.fluidAccentPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gli stili dei tasti di vetro (F6.4): che cosa dice ciascuno al materiale, e il conto dell'etichetta del tasto in
 * evidenza. Il conto e' aritmetica di colori: si prova su ogni accento della palette, chiaro e scuro, e sul caso misurato
 * alla chiusura di F2 (la tinta a meta' opacita' sopra una foto: 3,90:1 e 3,20:1).
 */
class FluidGlassButtonStyleTest {

  /** Una tinta dei controlli qualunque, con un ripiego e un filetto riconoscibili. */
  private val base = GlassTint(
    overlay = Color(0xFF90A0B0).copy(alpha = 0.38f),
    fallback = Color(0xFFF2F2F7).copy(alpha = 0.9f),
    hairline = Color.White.copy(alpha = 0.3f),
    mode = GlassTintMode.Neutral,
  )

  private val accents: List<Color> =
    (fluidAccentPresets + FluidDefaultBrand).flatMap { listOf(it.resolve(false), it.resolve(true)) }

  @Test
  fun `i nomi di SwiftUI danno gli stili attesi`() {
    assertSame(FluidGlassButtonStyle.Glass, FluidGlassButtonStyle.Glass(FluidGlass.Regular))
    assertSame(FluidGlassButtonStyle.Clear, FluidGlassButtonStyle.Glass(FluidGlass.Clear))
    // Un tasto reagisce sempre al dito: l'interattivita' del vetro passato non fa uno stile diverso.
    assertSame(FluidGlassButtonStyle.Glass, FluidGlassButtonStyle.Glass(FluidGlass.Regular.interactive()))
    assertSame(FluidGlassButtonStyle.Clear, FluidGlassButtonStyle.Glass(FluidGlass.Clear.interactive()))
    assertSame(FluidGlassButtonStyle.Prominent, FluidGlassButtonStyle.Prominent(Color.Unspecified))
    assertTrue(FluidGlassButtonStyle.Prominent.isProminent)
    assertFalse(FluidGlassButtonStyle.Glass.isProminent)
    assertTrue(FluidGlassButtonStyle.Clear.glass.isClear)
    // Uguaglianza per valore: uno stile ricreato non invalida niente.
    val red = Color(0xFFE53935)
    assertEquals(FluidGlassButtonStyle.Prominent(red), FluidGlassButtonStyle.Prominent(red))
    assertEquals(FluidGlassButtonStyle.Prominent(red).hashCode(), FluidGlassButtonStyle.Prominent(red).hashCode())
    assertEquals(
      FluidGlassButtonStyle.Glass(FluidGlass.Regular.tint(red)),
      FluidGlassButtonStyle.Glass(FluidGlass.Regular.tint(red).interactive()),
    )
    assertNotEquals(FluidGlassButtonStyle.Prominent(red), FluidGlassButtonStyle.Glass(FluidGlass.Regular.tint(red)))
  }

  @Test
  fun `lo stile di serie disegna il tasto con gli ingressi di prima`() {
    val look = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Glass, base, Color.Red)
    assertSame("la tinta dei controlli, la stessa istanza", base, look.tint)
    assertEquals(GlassLiquidVariant.Regular, look.variant)
    assertFalse(look.glassless)
    assertFalse("l'etichetta segue il lato del vetro", look.ink.isSpecified)
  }

  @Test
  fun `il limpido cambia solo la variante, il vuoto toglie il vetro, il vetro tinto e' la tinta di Apple`() {
    val clear = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Clear, base, Color.Red)
    assertSame(base, clear.tint)
    assertEquals(GlassLiquidVariant.Clear, clear.variant)
    assertFalse(clear.glassless)

    assertTrue(resolveFluidGlassButtonLook(FluidGlassButtonStyle.Glass(FluidGlass.Identity), base, Color.Red).glassless)

    val blue = Color(0xFF0A84FF).copy(alpha = 0.6f)
    val tinted = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Glass(FluidGlass.Regular.tint(blue)), base, Color.Red)
    assertEquals(GlassTintMode.Colored, tinted.tint.mode)
    assertEquals(blue, tinted.tint.overlay)
    assertFalse(tinted.ink.isSpecified)
  }

  @Test
  fun `il tasto in evidenza prende l'accento, o la tinta dell'app, senza cambiarne il colore`() {
    val accent = FluidDefaultBrand.resolve(false)
    val look = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Prominent, base, accent)
    assertEquals(GlassTintMode.Fixed, look.tint.mode)
    assertEquals("la tinta e' l'accento", accent.copy(alpha = 1f), look.tint.overlay.copy(alpha = 1f))
    assertEquals(base.hairline, look.tint.hairline)
    assertTrue("l'etichetta ha il suo inchiostro", look.ink.isSpecified)

    val red = Color(0xFFD32F2F)
    val own = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Prominent(red), base, accent)
    assertEquals("la tinta scelta dall'app", red, own.tint.overlay.copy(alpha = 1f))
    assertEquals(fluidProminentInk(red), own.ink)
  }

  @Test
  fun `l'etichetta del tasto in evidenza sta ad almeno 4,5 a 1 su qualunque fondo, per ogni accento`() {
    for (accent in accents) {
      val ink = fluidProminentInk(accent)
      val coverage = fluidProminentCoverage(accent)
      val worst = fluidProminentWorstContrast(accent, coverage, ink)
      assertTrue("$accent: $worst:1 alla copertura $coverage", worst >= FluidProminentLabelContrast)
      // Sul nero e sul bianco: i due estremi di cio' che il vetro puo' avere dietro.
      for (behind in listOf(Color.Black, Color.White, Color(0xFF3A2A1A), Color(0xFFE8E0D0))) {
        val body = accent.copy(alpha = coverage).compositeOver(behind)
        assertTrue("$accent su $behind", fluidContrastRatio(ink, body) >= FluidProminentLabelContrast)
      }
      assertTrue(coverage in 0f..1f)
    }
  }

  @Test
  fun `la copertura e' la piu' bassa che regge, e l'alfa della tinta non conta`() {
    val accent = FluidDefaultBrand.resolve(false)
    val coverage = fluidProminentCoverage(accent)
    assertTrue("copre meno del tutto: il vetro resta vetro ($coverage)", coverage < 1f)
    val target = FluidProminentLabelContrast * FluidProminentContrastMargin
    assertTrue(fluidProminentWorstContrast(accent, coverage) >= target)
    assertTrue("un centesimo in meno non regge", fluidProminentWorstContrast(accent, coverage - 0.01f) < target)
    // Gli accenti del tema sono opachi: l'alfa non e' un pavimento, o ogni tasto in evidenza sarebbe pieno.
    assertEquals(coverage, fluidProminentCoverage(accent.copy(alpha = 0.3f)), 1e-6f)
    assertEquals(0.919f, coverage, 0.005f)
    assertEquals(0.670f, fluidProminentCoverage(FluidDefaultBrand.resolve(true)), 0.005f)
  }

  @Test
  fun `il caso della chiusura di F2 sale sopra 4,5 a 1 con lo stesso accento`() {
    // La tinta a meta' opacita' scritta a mano dalla galleria (AdaptivePage, «Tinto»), con l'etichetta del tema:
    // sul fondo peggiore era sotto 4,5. Con lo stile in evidenza la stessa tinta copre quanto serve.
    for (dark in listOf(false, true)) {
      val accent = FluidDefaultBrand.resolve(dark)
      val handMade = fluidProminentWorstContrast(accent, 0.5f)
      assertTrue("a meta' opacita' non reggeva ($handMade)", handMade < FluidProminentLabelContrast)
      val prominent = fluidProminentTint(accent, base)
      assertTrue(fluidProminentWorstContrast(accent, prominent.overlay.alpha) >= FluidProminentLabelContrast)
      assertEquals(prominent.overlay.compositeOver(base.fallback), prominent.fallback)
    }
  }

  @Test
  fun `una tinta di mezzo tono copre tutto e prende il nero pieno, e regge comunque 4,5`() {
    // Un grigio a luminanza 0,18: col quasi nero del tema sta a 4,41:1, col nero pieno a 4,68 (sotto il margine, sopra
    // la soglia).
    val middle = Color(0xFF777777)
    assertEquals(Color.Black, fluidProminentInk(middle))
    assertEquals(1f, fluidProminentCoverage(middle), 0f)
    assertTrue(fluidProminentWorstContrast(middle, 1f) >= FluidProminentLabelContrast)
    // Su ogni grigio, da nero a bianco, l'etichetta regge.
    for (level in 0..255 step 5) {
      val grey = Color(level / 255f, level / 255f, level / 255f)
      val worst = fluidProminentWorstContrast(grey, fluidProminentCoverage(grey))
      assertTrue("grigio $level: $worst", worst >= FluidProminentLabelContrast)
    }
  }

  @Test
  fun `fogli e popover riportano taglia, stile e larghezza`() {
    // Quali: i valori li prova `FluidGlassButtonStylesUiTest` della galleria, in un foglio vero aperto da una barra
    // `Compact`.
    val locals = fluidGlassButtonDefaults().map { it.compositionLocal }
    assertEquals(listOf(LocalFluidGlassSize, LocalFluidGlassButtonStyle, LocalFluidButtonSizing), locals)
  }

  @Test
  fun `le icone discendono dalla taglia, 0,6 del vetro`() {
    assertEquals(listOf(24f, 26f, 32f), FluidGlassSize.entries.map { it.iconSize.value })
    // La tavola per Alessio: con il vetro a 46 l'icona sarebbe 28.
    assertEquals(28f, FluidMetrics.iconSizeIn(androidx.compose.ui.unit.Dp(46f)).value)
  }
}
