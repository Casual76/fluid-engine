package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il soffitto del velo scuro dei grandi (F2.3, decisione dell'orchestratore): un pannello del tema scuro sopra una
 * pagina chiara non schiarisce e non gira, si fa più profondo finché il testo chiaro torna leggibile.
 */
class GlassDarkCeilingTest {

  private fun luma(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

  @Test
  fun theCeilingIsTheAlphaThatKeepsTheBodyUnderIt() {
    val veil = Color(0x36 / 255f, 0x36 / 255f, 0x3C / 255f, 0.5f)
    val v = luma(veil)
    for (background in listOf(0.5f, 0.7f, 0.9f, 1f)) {
      val a = glassVeilCeilingAlpha(veil, background, 0.4f)
      // Il corpo piatto, velo sopra sfondo: al soffitto esatto.
      assertEquals("sfondo $background", 0.4f, v * a + background * (1 - a), 1e-5f)
    }
    // Sotto il soffitto non serve niente; un velo che non ci scende non promette niente; uno = nessun soffitto.
    assertEquals(0f, glassVeilCeilingAlpha(veil, 0.3f, 0.4f), 0f)
    assertEquals(0f, glassVeilCeilingAlpha(Color(0.6f, 0.6f, 0.6f, 0.5f), 1f, 0.4f), 0f)
    assertEquals(0f, glassVeilCeilingAlpha(veil, 1f, 1f), 0f)
    assertEquals(0f, glassVeilCeilingAlpha(veil, Float.NaN, 0.4f), 0f)
  }

  private fun recipe(role: GlassRole, dark: Boolean, background: Float?, width: Float, height: Float,
                     profile: GlassLiquidProfile = GlassLiquidProfile.Shipped, holdsSide: Boolean = false): GlassRecipe {
    val material = GlassAdaptiveMaterial()
    if (background == null) {
      material.unmeasured()
    } else {
      material.measure(luminance = background, deviation = 0f, texture = 0f,
        color = Color(background, background, background), spread = profile.adaptive.darkLevelSpread)
    }
    material.holdsSide = holdsSide
    return glassRecipe(
      role = role,
      widthDp = width,
      heightDp = height,
      darkSide = dark,
      optics = GlassDefaults.optics(role),
      profile = profile,
      adaptive = material,
    )
  }

  @Test
  fun aDarkPanelOverWhiteDeepensItsVeil() {
    val over = recipe(GlassRole.Modal, dark = true, background = 1f, width = 170f, height = 192f)
    val alpha = over.veilColor.alpha
    // Corpo piatto sul bianco al soffitto (o sotto).
    assertTrue("velo $alpha", luma(over.veilColor) * alpha + (1 - alpha) <= GlassLiquidDarkCeiling + 1e-4f)
    // Senza misura, o sopra uno sfondo già scuro, è il pannello di prima al bit.
    val before = recipe(GlassRole.Modal, dark = true, background = null, width = 170f, height = 192f)
    val onDark = recipe(GlassRole.Modal, dark = true, background = 0.05f, width = 170f, height = 192f)
    assertEquals(before.veilColor, onDark.veilColor)
    assertTrue(alpha > before.veilColor.alpha)
    // Spento, niente soffitto.
    val off = GlassLiquidProfile.Shipped.copy(adaptive = GlassLiquidProfile.Shipped.adaptive.copy(darkCeiling = 1f))
    assertEquals(
      before.veilColor.alpha,
      recipe(GlassRole.Modal, dark = true, background = 1f, width = 170f, height = 192f, profile = off).veilColor.alpha,
      0f,
    )
  }

  @Test
  fun theCeilingIsOnlyForTheLargeAndOnlyOnTheDarkSide() {
    // I piccoli girano invece (F2.3): il loro velo scuro resta quello di F2.2.
    val small = recipe(GlassRole.Floating, dark = true, background = 1f, width = 156f, height = 56f)
    val noCeiling = GlassLiquidProfile.Shipped.copy(adaptive = GlassLiquidProfile.Shipped.adaptive.copy(darkCeiling = 1f))
    val smallBefore = recipe(GlassRole.Floating, dark = true, background = 1f, width = 156f, height = 56f, profile = noCeiling)
    assertEquals(smallBefore.veilColor, small.veilColor)
    // Il lato chiaro di un grande non lo vede.
    val light = recipe(GlassRole.Modal, dark = false, background = 1f, width = 170f, height = 192f)
    val lightBefore = recipe(GlassRole.Modal, dark = false, background = 1f, width = 170f, height = 192f, profile = noCeiling)
    assertEquals(lightBefore.veilColor, light.veilColor)
    assertEquals(GlassLiquidDarkCeiling, GlassLiquidAdaptives.Shipped.darkCeiling, 0f)
    assertEquals(1f, GlassLiquidAdaptives.Off.darkCeiling, 0f)
  }

  @Test
  fun aSmallGlassThatCannotTurnTakesTheCeilingOfTheLarge() {
    // Una barra il cui contenuto non segue l'inchiostro (holdsSide) non gira: sul lato scuro sopra il bianco fa come
    // i grandi, e il testo chiaro torna leggibile.
    val held = recipe(GlassRole.Floating, dark = true, background = 1f, width = 220f, height = 62f, holdsSide = true)
    val a = held.veilColor.alpha
    assertTrue("velo $a", luma(held.veilColor) * a + (1 - a) <= GlassLiquidDarkCeiling + 1e-4f)
    val turns = recipe(GlassRole.Floating, dark = true, background = 1f, width = 220f, height = 62f)
    assertTrue(held.veilColor.alpha > turns.veilColor.alpha)
    // Senza misura non cambia niente.
    assertEquals(
      recipe(GlassRole.Floating, dark = true, background = null, width = 220f, height = 62f).veilColor,
      recipe(GlassRole.Floating, dark = true, background = null, width = 220f, height = 62f, holdsSide = true).veilColor,
    )
  }
}
