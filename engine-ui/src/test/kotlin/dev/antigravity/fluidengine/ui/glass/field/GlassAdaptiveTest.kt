package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.GlassAdaptiveState
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.fluidGlassAppearance
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La logica pura del materiale adattivo (F2.2, piano 3.9): come una lettura della sonda diventa numeri del materiale.
 * Le regole che la ricetta applica stanno in `GlassRecipeTest`; qui le funzioni da cui le prende.
 */
class GlassAdaptiveTest {

  // ---- La quota di nero ---------------------------------------------------------------------------------------

  @Test
  fun theBlackShareOfTwoPartsIsExactAtAnyLightLevel() {
    // Nero per una quota q e chiaro (a qualunque livello W) per il resto: media W(1 − q), varianza W²q(1 − q). Quando la
    // media scende sotto [GlassAdaptiveUniformDarkFrom] vale la stima «tutto scuro», se è più alta: un fondo quasi tutto
    // nero è nero anche se ha due parti (W = 0,6 e q = 0,74: media 0,156, stima 0,84).
    for (white in listOf(0.6f, 0.886f, 1f)) {
      for (q in listOf(0.1f, 0.25f, 0.47f, 0.74f, 0.9f)) {
        val mean = white * (1 - q)
        val variance = white * white * q * (1 - q)
        val uniform = ((GlassAdaptiveUniformDarkFrom - mean) / (GlassAdaptiveUniformDarkFrom - GlassAdaptiveUniformDarkTo))
          .coerceIn(0f, 1f)
        assertEquals("W=$white q=$q", maxOf(q, uniform), glassBlackShare(mean, variance), 1e-5f)
      }
    }
    // I tre passi di R4 A6 (bianco 226) cadono tutti nella stima a due parti.
    for (q in listOf(0.25f, 0.47f, 0.74f)) {
      val white = 226f / 255f
      assertEquals(q, glassBlackShare(white * (1 - q), white * white * q * (1 - q)), 1e-5f)
    }
  }

  @Test
  fun aUniformBackdropIsNotHalfBlackUnlessItIsDark() {
    // Un grigio uniforme ha la stessa media di metà nero e metà bianco, ma non scurisce un pannello (F2-e).
    assertEquals(0f, glassBlackShare(0.5f, 0f), 0f)
    assertEquals(0f, glassBlackShare(0.95f, 0f), 0f)
    // Il fondo scuro uniforme invece sì: la pagina scura dell'engine (0,05) e il nero sono neri.
    assertEquals(1f, glassBlackShare(0f, 0f), 0f)
    assertEquals(1f, glassBlackShare(0.05f, 0f), 0f)
    assertEquals(0.5f, glassBlackShare((GlassAdaptiveUniformDarkFrom + GlassAdaptiveUniformDarkTo) / 2f, 0f), 1e-5f)
    // Il testo nero su una pagina chiara, letto a 16×16 (media 0,85, deviazione 0,03–0,06): meno dell'1%.
    assertTrue(glassBlackShare(0.85f, 0.06f * 0.06f) < 0.01f)
    // Letture fallite: niente.
    assertEquals(0f, glassBlackShare(Float.NaN, 0.1f), 0f)
    assertEquals(0f, glassBlackShare(0.9f, Float.NaN), 0f)
  }

  // ---- Le due parti dello sfondo, il pavimento, il velo scuro ------------------------------------------------------

  @Test
  fun theDarkAndLightPartsAreSpreadDeviationsAwayFromTheMean() {
    assertEquals(0.5f, glassAdaptiveDarkLevel(0.5f, 0f, 1.25f), 0f)
    assertEquals(0.375f, glassAdaptiveDarkLevel(0.5f, 0.1f, 1.25f), 1e-6f)
    assertEquals(0.625f, glassAdaptiveLightLevel(0.5f, 0.1f, 1.25f), 1e-6f)
    assertEquals(0f, glassAdaptiveDarkLevel(0.443f, 0.443f, 1.25f), 0f)
    assertEquals(1f, glassAdaptiveLightLevel(0.9f, 0.2f, 1.25f), 0f)
    assertTrue(glassAdaptiveDarkLevel(Float.NaN, 0f, 1f).isNaN())
    assertEquals(0.3f, glassAdaptiveDarkLevel(0.3f, Float.NaN, 1f), 0f)
  }

  @Test
  fun theVeilFloorIsTheAlphaThatKeepsTheBodyAboveTheFloor() {
    val veil = Color(0xFFF5F5F7)
    val luma = 0.2126f * veil.red + 0.7152f * veil.green + 0.0722f * veil.blue
    for (backdrop in listOf(0f, 0.1f, 0.3f)) {
      val alpha = glassVeilFloorAlpha(veil, backdrop, 0.55f)
      // Il corpo `velo · a + sfondo · (1 − a)` arriva al pavimento.
      assertEquals("sfondo $backdrop", 0.55f, luma * alpha + backdrop * (1 - alpha), 1e-5f)
    }
    // Sopra il pavimento, con un velo più scuro del pavimento, o senza pavimento: niente.
    assertEquals(0f, glassVeilFloorAlpha(veil, 0.6f, 0.55f), 0f)
    assertEquals(0f, glassVeilFloorAlpha(Color(0xFF404040), 0f, 0.55f), 0f)
    assertEquals(0f, glassVeilFloorAlpha(veil, 0f, 0f), 0f)
    assertEquals(0f, glassVeilFloorAlpha(veil, Float.NaN, 0.55f), 0f)
  }

  @Test
  fun theDarkVeilDeepensSmoothlyBetweenItsTwoLevels() {
    val adaptive = GlassLiquidAdaptives.Shipped
    assertEquals(0f, glassAdaptiveDeepen(adaptive.darkDeepenFrom, adaptive), 0f)
    assertEquals(1f, glassAdaptiveDeepen(adaptive.darkDeepenTo, adaptive), 0f)
    assertEquals(0.5f, glassAdaptiveDeepen((adaptive.darkDeepenFrom + adaptive.darkDeepenTo) / 2f, adaptive), 1e-5f)
    var last = -1f
    for (step in 0..20) {
      val value = glassAdaptiveDeepen(step / 20f, adaptive)
      assertTrue(value >= last)
      last = value
    }
    assertEquals(0f, glassAdaptiveDeepen(1f, GlassLiquidAdaptives.Off), 0f)
    assertEquals(0f, glassAdaptiveDeepen(Float.NaN, adaptive), 0f)
  }

  // ---- L'ombra --------------------------------------------------------------------------------------------------

  @Test
  fun theShadowOnTextGrowsWithTheTextureUnderAHeadroom() {
    assertEquals(1f, glassShadowOnText(0f, 4.9f, 0.09f), 0f)
    assertEquals(4.9f, glassShadowOnText(1f, 4.9f, 0.09f), 1e-6f)
    assertEquals(2.95f, glassShadowOnText(0.5f, 4.9f, 0.09f), 1e-6f)
    // Il tetto sulla somma delle alfa: 0,18 × 2,5 = 0,45.
    assertEquals(2.5f, glassShadowOnText(1f, 4.9f, 0.18f), 1e-5f)
    // Un'ombra già oltre il tetto non scende, e nessun rinforzo chiesto è nessun rinforzo.
    assertEquals(1f, glassShadowOnText(1f, 4.9f, 0.6f), 0f)
    assertEquals(1f, glassShadowOnText(1f, 1f, 0.09f), 0f)
    assertEquals(1f, glassShadowOnText(Float.NaN, 4.9f, 0.09f), 0f)
  }

  @Test
  fun theShadowColourOnANeutralBackdropIsTheBaseToTheBit() {
    val base = Color.Black
    for (grey in listOf(0f, 0.2f, 0.5f, 1f)) {
      assertEquals(base, glassAdaptiveShadowColor(base, Color(grey, grey, grey), 1f))
    }
    assertEquals(base, glassAdaptiveShadowColor(base, Color.Unspecified, 1f))
    assertEquals(base, glassAdaptiveShadowColor(base, Color(0.9f, 0.1f, 0.1f), 0f))
    // Su un colore saturo va verso una sua tinta profonda: la tonalità del rosso, mai più chiara di 0,35.
    val red = glassAdaptiveShadowColor(base, Color(0.9f, 0.1f, 0.1f), 1f)
    assertTrue(red.red > red.green && red.red <= GlassAdaptiveShadowShade + 1f / 255f)
    assertEquals(base.alpha, red.alpha, 0f)
  }

  @Test
  fun clearDimFollowsOnlyWhenAsked() {
    assertEquals(1f, glassClearDimScale(0.05f, 0f), 0f)
    assertEquals(1f, glassClearDimScale(Float.NaN, 1f), 0f)
    assertEquals(0f, glassClearDimScale(0.05f, 1f), 0f)
    assertEquals(1f, glassClearDimScale(0.9f, 1f), 0f)
    assertEquals(0.75f, glassClearDimScale(0.05f, 0.25f), 1e-6f)
  }

  // ---- Il cursore -------------------------------------------------------------------------------------------------

  @Test
  fun theCursorIsOneToTheBitInTheMiddleAndMonotonic() {
    val p = GlassLiquidPreferences.Shipped
    assertEquals(1f, glassPreferenceFrost(FluidGlassPreference.Default, p), 0f)
    assertEquals(1f, glassPreferenceVeil(FluidGlassPreference.Default, p), 0f)
    assertEquals(1f, glassPreferenceFrost(Float.NaN, p), 0f)
    assertEquals(p.frostClear, glassPreferenceFrost(0f, p), 1e-6f)
    assertEquals(p.frostColored, glassPreferenceFrost(1f, p), 1e-5f)
    assertEquals(p.veilClear, glassPreferenceVeil(0f, p), 1e-6f)
    assertEquals(p.veilColored, glassPreferenceVeil(1f, p), 1e-6f)
    // Fuori corsa si legge al bordo.
    assertEquals(glassPreferenceFrost(1f, p), glassPreferenceFrost(3f, p), 0f)
    assertEquals(glassPreferenceFrost(0f, p), glassPreferenceFrost(-1f, p), 0f)
    var lastFrost = 0f
    var lastVeil = 0f
    for (step in 0..100) {
      val frost = glassPreferenceFrost(step / 100f, p)
      val veil = glassPreferenceVeil(step / 100f, p)
      assertTrue(frost > lastFrost)
      assertTrue(veil >= lastVeil)
      lastFrost = frost
      lastVeil = veil
    }
    // Il passo è per moltiplicazione: a un quarto il raggio è la media geometrica fra l'estremo e uno.
    assertEquals(kotlin.math.sqrt(p.frostClear), glassPreferenceFrost(0.25f, p), 1e-5f)
  }

  // ---- Il contenitore e lo stato -----------------------------------------------------------------------------------

  @Test
  fun aMeasureFillsTheMaterialAndUnmeasuredEmptiesIt() {
    val material = GlassAdaptiveMaterial().measure(0.5f, 0.1f, 0.7f, Color.Red, spread = 1.25f)
    assertTrue(material.measured)
    assertEquals(0.375f, material.darkLevel, 1e-6f)
    assertEquals(0.625f, material.lightLevel, 1e-6f)
    assertEquals(0.7f, material.texture, 0f)
    assertEquals(Color.Red, material.color)
    assertTrue(material.darkness.isNaN())
    material.darkness = 0.4f
    material.unmeasured()
    assertFalse(material.measured)
    assertTrue(material.darkness.isNaN())
    assertFalse(GlassAdaptiveMaterial().measure(Float.NaN, 0f, 0f, Color.Red).measured)
  }

  private fun target(luminance: Float, dark: Boolean = luminance < 0.45f, texture: Float = 0f) = GlassAdaptiveTarget(
    dark = dark, luminance = luminance, smoothedLuminance = luminance, texture = texture,
    color = Color(luminance, luminance, luminance), measured = true,
  )

  private fun stats(luminance: Float, variance: Float = 0f) = GlassBackdropStats(
    luminance = luminance, variance = variance, gradientEnergy = 0f, red = luminance, green = luminance,
    blue = luminance, coverage = 1f,
  )

  @Test
  fun theFirstMeasureIsTakenAtOnceAndOnlyTheLargeOnesDriveTheSide() = runBlocking {
    // Prima di ogni misura: niente, e il lato del tema.
    val state = GlassAdaptiveState()
    val out = GlassAdaptiveMaterial()
    state.follow(GlassAdaptiveTarget.unmeasured(false), null, darkSide = false, continuous = false)
    state.fill(out)
    assertFalse(out.measured)
    assertTrue(out.darkness.isNaN())
    // La prima misura non si anima: un vetro che nasce sopra il nero è già del nero.
    state.follow(target(0f), stats(0f), darkSide = false, continuous = false)
    state.fill(out)
    assertTrue(out.measured)
    assertEquals(0f, out.luminance, 0f)
    assertEquals(0f, out.darkLevel, 0f)
    // Un piccolo non scrive il lato: è di F2.3.
    assertTrue(out.darkness.isNaN())

    // Un grande sul tema chiaro scurisce con la quota di nero, subito alla prima misura.
    val large = GlassAdaptiveState()
    large.follow(target(0.443f), stats(0.443f, 0.443f * 0.443f), darkSide = false, continuous = true)
    large.fill(out)
    assertEquals(0.5f, out.darkness, 1e-4f)
    // Sul tema scuro resta scuro (i grandi scuriscono, non schiariscono: R4 A6).
    val darkTheme = GlassAdaptiveState()
    darkTheme.follow(target(0.95f), stats(0.95f), darkSide = true, continuous = true)
    darkTheme.fill(out)
    assertEquals(1f, out.darkness, 0f)
    // Prima della misura un grande sta al lato del tema, al bit.
    val waiting = GlassAdaptiveState()
    waiting.follow(GlassAdaptiveTarget.unmeasured(true), null, darkSide = true, continuous = true)
    waiting.fill(out)
    assertEquals(1f, out.darkness, 0f)
    assertFalse(out.measured)
  }

  @Test
  fun theShippedProfileMarksTheLargeRolesAsContinuous() {
    val adaptive = GlassLiquidProfile.Shipped.adaptive
    assertTrue(adaptive.darkensContinuously(GlassRole.Modal))
    assertTrue(adaptive.darkensContinuously(GlassRole.Content))
    for (role in listOf(GlassRole.Bar, GlassRole.Floating, GlassRole.Interactive)) {
      assertFalse("$role gira", adaptive.darkensContinuously(role))
    }
    assertEquals(GlassLiquidAdaptives.Shipped, adaptive)
    assertEquals(GlassLiquidPreferences.Shipped, GlassLiquidProfile.Shipped.preference)
  }

  @Test
  fun theAppearanceFollowsTheContinuousSide() {
    val unmeasured = fluidGlassAppearance(GlassAdaptiveTarget.unmeasured(dark = true))
    assertTrue(unmeasured.isDark)
    assertFalse(unmeasured.isMeasured)
    assertTrue(unmeasured.backdropLuminance.isNaN())
    val large = fluidGlassAppearance(target(0.4f, dark = false), darkness = 0.6f)
    assertTrue(large.isDark)
    assertEquals(0.6f, large.darkness, 0f)
    assertEquals(0.4f, large.backdropLuminance, 0f)
    assertEquals(large, fluidGlassAppearance(target(0.4f, dark = false), darkness = 0.6f))
    assertEquals(unmeasured, fluidGlassAppearance(GlassAdaptiveTarget.unmeasured(dark = true)))
  }
}
