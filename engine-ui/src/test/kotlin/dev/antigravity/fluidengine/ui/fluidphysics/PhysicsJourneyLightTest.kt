package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassJourneyRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassTravelPaddingDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La ricetta delle superfici fisiche col carattere del viaggio del piano agganciato (F3.10): `physicsLiquidRecipe` porta
 * `glassJourneyRecipe` dopo il vetro in viaggio, a zero non tocca un bit (menu e Fluid-physics fuori dal piano restano la
 * scelta B), il margine della cattura del palco regge la sfocatura al colmo, e il bagliore del dito che il palco porta
 * prende il posto di quello della superficie quando è più forte.
 */
class PhysicsJourneyLightTest {

  private val neutral =
    GlassTint(overlay = Color.Transparent, fallback = Color.Transparent, hairline = Color.Transparent, mode = GlassTintMode.Neutral)
  private val button = GlassDefaults.buttonOptics().sanitized()

  private fun numbers(r: GlassRecipe): List<Any> = listOf(
    r.saturation, r.blurDp, r.mix, r.paddingDp, r.captureScale, r.thicknessDp, r.refractionDp, r.dome, r.dispersion,
    r.magnify, r.materialize, r.veilColor, r.veilAdapt, r.tintColor, r.tintMode, r.tintFollow, r.bodyShade,
    r.bodyShadeDepthDp, r.rimWidthDp, r.rimDark, r.lightColor, r.glowPeak, r.glowFill, r.shadowContactAlpha,
    r.shadowAmbientAlpha, r.shadowColor, r.shadowContactRadiusDp, r.shadowAmbientRadiusDp, r.shadowOffsetXDp,
    r.shadowOffsetYDp,
  )

  private fun recipe(
    light: Float,
    dark: Boolean = false,
    motion: Float = 0.6f,
    profile: GlassLiquidProfile = GlassLiquidProfile.Shipped,
    touch: Float = 0f,
  ) =
    physicsLiquidRecipe(
      role = GlassRole.Interactive,
      material = PhysicsMaterialSource(null, button).at(1f),
      shortDp = 44f,
      darkSide = dark,
      amount = 1f,
      quality = 1f,
      press = 0f,
      tint = neutral,
      tintFrom = null,
      tintBlend = 1f,
      nested = false,
      motion = motion,
      profile = profile,
      journeyLight = light,
      journeyTouch = touch,
    )

  @Test
  fun aLevelOfZeroLeavesTheRecipeOfTheJourneyAsItWas() {
    val plain = physicsLiquidRecipe(
      GlassRole.Interactive, PhysicsMaterialSource(null, button).at(1f), 44f, darkSide = false, amount = 1f, quality = 1f,
      press = 0f, tint = neutral, tintFrom = null, tintBlend = 1f, nested = false, motion = 0.6f,
      profile = GlassLiquidProfile.Shipped,
    )
    assertEquals(numbers(plain), numbers(recipe(0f)))
    // Anche col dito (F3.12b): senza luce del carattere il tocco non accende l'alone.
    assertEquals(numbers(plain), numbers(recipe(0f, touch = 1f)))
  }

  @Test
  fun theLevelIsTheCharacterOfTheJourneyAfterTheTravellingGlass() {
    for (dark in listOf(false, true)) {
      for (level in listOf(0.3f, 1f)) {
        // Col lato corto della superficie: l'alone nella sua geometria (F3.12b).
        val expected = glassJourneyRecipe(recipe(0f, dark), GlassLiquidProfile.Shipped.journey, level, dark, shortDp = 44f)
        val got = recipe(level, dark)
        assertEquals("lato scuro $dark, livello $level", numbers(expected), numbers(got))
        assertNotEquals(numbers(recipe(0f, dark)), numbers(got))
      }
    }
  }

  @Test
  fun theFingerThePlaneCarriesBringsTheGlowOfApple() {
    val journey = GlassLiquidProfile.Shipped.journey
    // A metà senza tocco, pieno col dito pieno: il blocco bianco del render ha l'alone di Apple (al colmo tutto sul
    // canale di contatto, la caduta sulla distanza del campo fuso).
    assertEquals(journey.glow, recipe(1f).shadowContactAlpha + recipe(1f).shadowAmbientAlpha, 1e-6f)
    assertEquals(journey.glowFinger, recipe(1f, touch = 1f).shadowContactAlpha, 1e-6f)
    assertEquals(0f, recipe(1f, touch = 1f).shadowAmbientAlpha, 1e-6f)
    val expected = glassJourneyRecipe(recipe(0f), journey, 0.6f, darkSide = false, shortDp = 44f, finger = 0.7f)
    assertEquals(numbers(expected), numbers(recipe(0.6f, touch = 0.7f)))
    // L'alone scende sotto il tasto della sua frazione del lato corto, come quello del render.
    assertEquals(journey.glowDrop * 44f, recipe(1f).shadowOffsetYDp, 1e-4f)
  }

  @Test
  fun thePlaneCaptureHoldsTheFrostAtThePeakAndTheOthersDoNotMove() {
    val profile = GlassLiquidProfile.Shipped
    val plain = glassTravelPaddingDp(GlassRole.Interactive, button, null, profile)
    val plane = glassTravelPaddingDp(GlassRole.Interactive, button, null, profile, journey = profile.journey)
    assertEquals(plain * (1f + profile.journey.frost), plane, 1e-4f)
    // La sfocatura al colmo (raggio per `1 + frost`) sta dentro il margine del palco.
    assertTrue(recipe(1f).blurDp <= plane + 1e-3f)
  }

  @Test
  fun theFingerThePlaneCarriesReplacesTheSurfaceGlowWhenItIsStronger() {
    val bounds = Rect(10f, 20f, 210f, 120f)
    val out = FloatArray(2)
    // Senza dito del palco: il bagliore della pressione della superficie, com'era (il centro dal suo dito, o NaN).
    val own = recipe(0f)
    val ownFill = own.glowFill
    assertEquals(0.5f, physicsLiquidGlow(0.5f, 1f, Offset(30f, 40f), bounds, null, own, out), 0f)
    assertEquals(40f, out[0], 0f)
    assertEquals(60f, out[1], 0f)
    assertEquals(ownFill, own.glowFill, 0f)
    assertEquals(0f, physicsLiquidGlow(0f, 1f, null, bounds, null, own, out), 0f)
    assertTrue(out[0].isNaN() && out[1].isNaN())
    // Il dito del palco, più forte: il suo centro, e il riempimento verso la sagoma intera.
    val carried = recipe(0f)
    val touch = PhysicsPlaneTouch(amount = 0.8f, x = 50f, y = 70f, fill = 0.5f)
    assertEquals(0.8f, physicsLiquidGlow(0.2f, 1f, null, bounds, touch, carried, out), 0f)
    assertEquals(50f, out[0], 0f)
    assertEquals(70f, out[1], 0f)
    assertEquals(ownFill + (1f - ownFill) * 0.5f, carried.glowFill, 1e-6f)
    // Più debole di quello della superficie: vince la superficie, e il riempimento non cambia.
    val weaker = recipe(0f)
    assertEquals(0.9f, physicsLiquidGlow(0.9f, 1f, null, bounds, touch, weaker, out), 0f)
    assertEquals(ownFill, weaker.glowFill, 0f)
  }
}
