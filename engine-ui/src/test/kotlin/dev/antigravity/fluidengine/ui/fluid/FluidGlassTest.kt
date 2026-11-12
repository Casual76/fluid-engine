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
    assertEquals(strings.glassPreferenceColored, FluidAppearance.glassPreferenceLabel(1f, strings))
    assertEquals("30%", FluidAppearance.glassPreferenceLabel(0.3f, strings))
    assertEquals("70%", FluidAppearance.glassPreferenceLabel(0.7f, strings))
    assertEquals(0f, FluidGlassPreference.Transparent, 0f)
    assertEquals(1f, FluidGlassPreference.Colored, 0f)
  }
}
