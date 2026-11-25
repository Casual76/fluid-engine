package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il tipo `FluidGlass` (F2.2, piano 3.13: il `Glass` di SwiftUI) e la riga del cursore del vetro nelle impostazioni
 * d'aspetto. Il tipo è una configurazione: uguaglianza strutturale, e una traduzione nel materiale.
 */
class FluidGlassTest {

  @Test
  fun theThreeVariantsAndTheirModifiers() {
    assertFalse(FluidGlass.Regular.isClear)
    assertTrue(FluidGlass.Clear.isClear)
    assertTrue(FluidGlass.Identity.isIdentity)
    assertEquals(Color.Unspecified, FluidGlass.Regular.tint)
    assertFalse(FluidGlass.Regular.isInteractive)

    val red = FluidGlass.Regular.tint(Color.Red)
    assertEquals(Color.Red, red.tint)
    assertFalse(red.isClear)
    val pressed = red.interactive()
    assertTrue(pressed.isInteractive)
    assertEquals(Color.Red, pressed.tint)
    assertFalse(pressed.interactive(false).isInteractive)
    // Togliere la tinta: null o non specificato.
    assertEquals(FluidGlass.Regular, red.tint(null))
    assertEquals(FluidGlass.Regular, red.tint(Color.Unspecified))
    // Il vetro spento resta spento: niente tinta, niente tocco.
    assertSame(FluidGlass.Identity, FluidGlass.Identity.tint(Color.Red))
    assertSame(FluidGlass.Identity, FluidGlass.Identity.interactive())
    // Un modificatore che non cambia niente restituisce lo stesso vetro.
    assertSame(FluidGlass.Clear, FluidGlass.Clear.interactive(false))
  }

  @Test
  fun equalityIsStructural() {
    // Un'istanza ricreata a ogni ricomposizione non deve invalidare niente (piano 3.2).
    val a = FluidGlass.Clear.tint(Color(0xFF2B6FD8)).interactive()
    val b = FluidGlass.Clear.interactive().tint(Color(0xFF2B6FD8))
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertNotEquals(a, FluidGlass.Regular.tint(Color(0xFF2B6FD8)).interactive())
    assertNotEquals(FluidGlass.Regular, FluidGlass.Clear)
    assertEquals("FluidGlass.Clear.interactive()", FluidGlass.Clear.interactive().toString())
  }

  @Test
  fun theTypeTranslatesToTheMaterial() {
    assertEquals(GlassLiquidVariant.Regular, FluidGlass.Regular.liquidVariant)
    assertEquals(GlassLiquidVariant.Clear, FluidGlass.Clear.liquidVariant)
    assertEquals(GlassLiquidVariant.Regular, FluidGlass.Identity.liquidVariant)
    assertEquals(1f, FluidGlass.Clear.presence, 0f)
    assertEquals(0f, FluidGlass.Identity.presence, 0f)
    assertEquals(Color.Red, FluidGlass.Regular.tint(Color.Red).recipeTint)
    assertEquals(Color.Unspecified, FluidGlass.Regular.recipeTint)
  }

  @Test
  fun theCursorRowSaysItsValueInWords() {
    val strings = FluidAppearanceStrings()
    assertEquals(strings.glassPreferenceTransparent, FluidAppearance.glassPreferenceLabel(0f, strings))
    assertEquals(strings.glassPreferenceTransparent, FluidAppearance.glassPreferenceLabel(-3f, strings))
    assertEquals(strings.glassPreferenceDefault, FluidAppearance.glassPreferenceLabel(FluidGlassPreference.Default, strings))
    assertEquals(strings.glassPreferenceDefault, FluidAppearance.glassPreferenceLabel(Float.NaN, strings))
    // F10.8c: il cursore finisce a «Di serie»; la percentuale è della corsa (0,5 = 100%).
    assertEquals(strings.glassPreferenceTransparent, FluidAppearance.glassPreferenceLabel(0.1f, strings))
    assertEquals("30%", FluidAppearance.glassPreferenceLabel(0.15f, strings))
    assertEquals("60%", FluidAppearance.glassPreferenceLabel(0.3f, strings))
    assertEquals(strings.glassPreferenceDefault, FluidAppearance.glassPreferenceLabel(0.45f, strings))
    // Un valore salvato nella metà colorata di prima si dice «Di serie», come si vede.
    assertEquals(strings.glassPreferenceDefault, FluidAppearance.glassPreferenceLabel(0.7f, strings))
    assertEquals(strings.glassPreferenceDefault, FluidAppearance.glassPreferenceLabel(1f, strings))
    assertEquals(0f, FluidGlassPreference.Transparent, 0f)
  }

  /**
   * F10.8c, scelta di Alessio del 25/11/2026: un cursore solo, da trasparente a di serie. Un'app che aveva salvato un
   * valore nella metà colorata (fra 0,5 e 1, e oltre) torna al vetro di serie senza sorprese: il valore si legge 0,5, la
   * chiarezza è piena, e il valore che l'estremo colorato aveva resta lo stesso per chi l'ha salvato.
   */
  @Test
  fun aValueSavedInTheOldColoredHalfReadsAsTheStockGlass() {
    for (saved in listOf(0.5001f, 0.7f, 0.8f, 1f, 3f, Float.POSITIVE_INFINITY, Float.NaN)) {
      assertEquals("salvato $saved", FluidGlassPreference.Default, FluidGlassPreference.effective(saved), 0f)
      assertEquals("salvato $saved", 1f, FluidGlassPreference.clarityOf(saved), 0f)
    }
    assertEquals(FluidGlassPreference.Transparent, FluidGlassPreference.effective(-1f), 0f)
    // Un valore non finito è di serie, anche meno infinito: non è una scelta dell'utente.
    assertEquals(FluidGlassPreference.Default, FluidGlassPreference.effective(Float.NEGATIVE_INFINITY), 0f)
    assertEquals(0.2f, FluidGlassPreference.effective(0.2f), 0f)
    assertEquals(FluidGlassPreference.Default, FluidGlassPreference.effective(FluidGlassPreference.Default), 0f)
    @Suppress("DEPRECATION")
    val colored = FluidGlassPreference.Colored
    assertEquals("il valore di prima non cambia", 1f, colored, 0f)
    assertEquals(FluidGlassPreference.Default, FluidGlassPreference.effective(colored), 0f)
  }
}
