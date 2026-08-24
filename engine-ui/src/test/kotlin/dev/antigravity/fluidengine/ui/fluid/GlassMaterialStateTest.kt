package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialStateTest {

  @Test
  fun glassOptics_presetsRunFromQuietChromeToAThickControl() {
    val bar = GlassDefaults.optics(GlassRole.Bar)
    val floating = GlassDefaults.optics(GlassRole.Floating)
    val interactive = GlassDefaults.optics(GlassRole.Interactive)
    val modal = GlassDefaults.optics(GlassRole.Modal)

    // A control hides the least of anything on screen: it is too small to frost its background and
    // still read as glass, so the bend at its edge has to do the identifying instead.
    assertTrue(interactive.blurScale < bar.blurScale)
    assertTrue(interactive.blurScale < modal.blurScale)
    assertTrue(interactive.blurScale < floating.blurScale)
    assertTrue(interactive.depthEffect)

    // Displacement is bounded by the size of the thing displacing, not by how important it is. The
    // control is the smallest surface here — 44 dp — so its absolute reach is the smallest too, and
    // asserting the opposite is how it grew a coloured halo the first time round.
    assertTrue(interactive.refractionAmount < floating.refractionAmount)

    // A bar is a wide shallow sheet: it reaches far in and displaces gently, or the whole top of
    // the screen swims while a list moves under it.
    assertTrue(bar.refractionAmount < bar.refractionHeight)
    assertFalse(bar.depthEffect)
    assertFalse(bar.dispersion)

    // Seven samples per pixel are only ever worth it on something small.
    assertTrue(interactive.dispersion)
    assertFalse(modal.dispersion)

    // A sheet has to win an argument with a whole page behind it.
    assertTrue(modal.blurScale > bar.blurScale)

    // Only the surfaces that float carry a shadow; a bar is flush with the screen.
    assertEquals(0f, bar.shadowAlpha, 0f)
    assertTrue(floating.shadowAlpha > 0f)

    // Every preset transmits more colour than it receives, or glass reads as grey plastic.
    listOf(bar, floating, interactive, modal).forEach { assertTrue(it.vibrancy > 1f) }
  }

  @Test
  fun glassOptics_pressOnlyDeepensTheSurfacesAFingerCanReach() {
    assertEquals(0f, GlassDefaults.optics(GlassRole.Bar).pressedDepthBoost, 0f)
    assertEquals(0f, GlassDefaults.optics(GlassRole.Modal).pressedDepthBoost, 0f)
    assertTrue(GlassDefaults.optics(GlassRole.Interactive).pressedDepthBoost > 0f)
    assertTrue(GlassDefaults.optics(GlassRole.Floating).pressedDepthBoost > 0f)
  }

  @Test
  fun glassOptics_sanitizedCorrectsMalformedCustomValues() {
    val sanitized = GlassOptics(
      blurScale = Float.NaN,
      refractionHeight = (-4).dp,
      refractionAmount = Float.NaN.dp,
      depthEffect = true,
      dispersion = true,
      vibrancy = Float.NaN,
      highlightWidth = (-1).dp,
      highlightAlpha = 4f,
      highlightAngle = Float.NaN,
      innerShadowRadius = (-2).dp,
      innerShadowAlpha = -1f,
      shadowRadius = Float.NEGATIVE_INFINITY.dp,
      shadowAlpha = Float.NaN,
      pressedDepthBoost = 9f,
    ).sanitized()

    assertEquals(0f, sanitized.blurScale, 0f)
    assertEquals(0.dp, sanitized.refractionHeight)
    assertEquals(0.dp, sanitized.refractionAmount)
    assertEquals(1f, sanitized.vibrancy, 0f)
    assertEquals(0.dp, sanitized.highlightWidth)
    assertEquals(1f, sanitized.highlightAlpha, 0f)
    assertEquals(45f, sanitized.highlightAngle, 0f)
    assertEquals(0.dp, sanitized.innerShadowRadius)
    assertEquals(0f, sanitized.innerShadowAlpha, 0f)
    assertEquals(0.dp, sanitized.shadowRadius)
    assertEquals(0f, sanitized.shadowAlpha, 0f)
    assertEquals(2f, sanitized.pressedDepthBoost, 0f)
  }

  @Test
  fun glassOptics_sanitizedIsIdentityOnEveryPreset() {
    GlassRole.entries.forEach { role ->
      val optics = GlassDefaults.optics(role)
      assertEquals("$role was written outside the range the renderer honours", optics, optics.sanitized())
    }
  }

  @Test
  fun clampGlassUnit_treatsAMalformedIntensityAsAbsentRatherThanFull() {
    assertEquals(0f, clampGlassUnit(Float.NaN), 0f)
    assertEquals(0f, clampGlassUnit(-3f), 0f)
    assertEquals(1f, clampGlassUnit(3f), 0f)
    assertEquals(0.5f, clampGlassUnit(0.5f), 0f)
  }
}
