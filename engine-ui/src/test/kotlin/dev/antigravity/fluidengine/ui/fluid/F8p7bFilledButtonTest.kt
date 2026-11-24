package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.7b · Il tasto pieno ([FluidGlassButtonStyle.Filled]): la capsula piena senza vetro, come «Play» nella pagina
 * dell'album di Apple Music (iOS 26.4), con l'etichetta dell'inchiostro chiesto solo se ci si legge.
 */
class F8p7bFilledButtonTest {

  @Test
  fun `il tasto pieno e' senza vetro, col suo pieno e l'inchiostro chiesto`() {
    val page = Color(0xFF525F1E)
    val look = resolveFluidGlassButtonLook(FluidGlassButtonStyle.Filled(Color.White, ink = page), GlassTint(
      overlay = Color.White.copy(alpha = 0.3f), fallback = Color.White, hairline = Color.Transparent,
    ), accent = Color(0xFF007AFF))
    assertTrue(look.glassless)
    assertEquals(Color.White, look.fill)
    assertEquals(page, look.ink)
  }

  @Test
  fun `un inchiostro che non si legge sul pieno lascia il posto a quello che si legge`() {
    // Il colore della pagina scura sul bianco: si legge, resta.
    val dark = Color(0xFF8B2D4C)
    assertEquals(dark, fluidFilledInk(Color.White, dark))
    // Un grigio chiarissimo sul bianco no: l'engine passa al quasi nero.
    val pale = fluidFilledInk(Color.White, Color(0xFFEEEEEE))
    assertNotEquals(Color(0xFFEEEEEE), pale)
    assertTrue(fluidContrastRatio(pale, Color.White) >= FluidProminentLabelContrast)
    // Senza inchiostro chiesto: quello del pieno, come per il tasto in evidenza.
    val onAccent = fluidFilledInk(Color(0xFF007AFF), Color.Unspecified)
    assertTrue(fluidContrastRatio(onAccent, Color(0xFF007AFF)) >= FluidProminentLabelContrast)
  }

  @Test
  fun `lo stile pieno si confronta per valore`() {
    assertEquals(FluidGlassButtonStyle.Filled(Color.White, Color.Black), FluidGlassButtonStyle.Filled(Color.White, Color.Black))
    assertNotEquals(FluidGlassButtonStyle.Filled(Color.White), FluidGlassButtonStyle.Filled(Color.Black))
    assertNotEquals(FluidGlassButtonStyle.Glass(FluidGlass.Identity), FluidGlassButtonStyle.Filled(Color.White))
    assertTrue(FluidGlassButtonStyle.Filled(Color.White).toString().startsWith("FluidGlassButtonStyle.Filled"))
  }
}
