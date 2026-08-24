package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassMaterialStateTest {

  @Test
  fun glassOptics_presetsGiveFloatingAndInteractiveSurfacesMoreDepthThanBars() {
    val bar = GlassDefaults.optics(GlassRole.Bar)
    val floating = GlassDefaults.optics(GlassRole.Floating)
    val interactive = GlassDefaults.optics(GlassRole.Interactive)
    val modal = GlassDefaults.optics(GlassRole.Modal)

    assertTrue(floating.refractionStrength > bar.refractionStrength)
    assertTrue(interactive.refractionStrength > floating.refractionStrength)
    assertTrue(interactive.outerRimAlpha > floating.outerRimAlpha)
    assertTrue(modal.refractionStrength > bar.refractionStrength)
    assertTrue(modal.refractionStrength < interactive.refractionStrength)
  }

  @Test
  fun glassOptics_sanitizedClampsMalformedCustomValuesAndNormalizesLight() {
    val sanitized = GlassOptics(
      refractionStrength = (-4).dp,
      rimWidth = Float.NaN.dp,
      outerRimAlpha = 4f,
      innerRimAlpha = -2f,
      innerShadowAlpha = Float.NaN,
      specularAlpha = 1.4f,
      magnification = 2f,
      pressedDepthBoost = -1f,
      lightDirection = Offset.Zero,
    ).sanitized()

    assertEquals(0.dp, sanitized.refractionStrength)
    assertEquals(0.dp, sanitized.rimWidth)
    assertEquals(1f, sanitized.outerRimAlpha, 0f)
    assertEquals(0f, sanitized.innerRimAlpha, 0f)
    assertEquals(0f, sanitized.innerShadowAlpha, 0f)
    assertEquals(1f, sanitized.specularAlpha, 0f)
    assertEquals(0.08f, sanitized.magnification, 0f)
    assertEquals(0f, sanitized.pressedDepthBoost, 0f)
    assertEquals(1f, sanitized.lightDirection.getDistance(), 0.001f)
    assertEquals(0f, clampGlassUnit(Float.NaN), 0f)
    assertEquals(1f, clampGlassUnit(3f), 0f)
  }

  @Test
  fun glassCapability_selectsStaticMagnifiedAndRuntimeTiers() {
    assertEquals(GlassRenderCapability.StaticRim, glassRenderCapability(30, true))
    assertEquals(GlassRenderCapability.MagnifiedRim, glassRenderCapability(31, true))
    assertEquals(GlassRenderCapability.MagnifiedRim, glassRenderCapability(32, true))
    assertEquals(GlassRenderCapability.RuntimeRefraction, glassRenderCapability(33, true))
    assertEquals(GlassRenderCapability.StaticRim, glassRenderCapability(36, false))
  }

  @Test
  fun glassRecordingDemand_wakesEachNewPaneOnceWithoutSelfInvalidation() {
    val firstPane = GlassRecordingDemand()
    val secondPane = GlassRecordingDemand()

    assertTrue(firstPane.request())
    assertFalse(firstPane.request())
    assertTrue(secondPane.request())

    assertTrue(firstPane.consume(surfaceReady = true))
    assertFalse(firstPane.request())
    assertTrue(firstPane.consume(surfaceReady = true))
    assertTrue(firstPane.consume(surfaceReady = true))
    assertFalse(firstPane.consume(surfaceReady = true))
    assertTrue(firstPane.request())
  }

  @Test
  fun runtimeRefraction_usesOnlyUniformRoundedOutlinesAndSkipsFadeDown() {
    val rounded = uniformRoundRect(radius = 12f)
    val asymmetric = RoundRect(
      left = 0f,
      top = 0f,
      right = 100f,
      bottom = 60f,
      topLeftCornerRadius = CornerRadius(12f, 12f),
      topRightCornerRadius = CornerRadius(12f, 12f),
      bottomRightCornerRadius = CornerRadius(20f, 20f),
      bottomLeftCornerRadius = CornerRadius(12f, 12f),
    )

    assertEquals(12f, runtimeGlassCornerRadiusForRoundRectOrNull(rounded) ?: -1f, 0f)
    assertNull(runtimeGlassCornerRadiusForRoundRectOrNull(asymmetric))
    assertTrue(
      shouldCreateGlassRuntimeRefraction(
        requestedCapability = GlassRenderCapability.RuntimeRefraction,
        perimeterOptics = true,
        hasRefraction = true,
        runtimeCornerRadius = 12f,
      ),
    )
    assertFalse(
      shouldCreateGlassRuntimeRefraction(
        requestedCapability = GlassRenderCapability.RuntimeRefraction,
        perimeterOptics = false,
        hasRefraction = true,
        runtimeCornerRadius = 12f,
      ),
    )
    assertFalse(
      shouldCreateGlassRuntimeRefraction(
        requestedCapability = GlassRenderCapability.RuntimeRefraction,
        perimeterOptics = true,
        hasRefraction = true,
        runtimeCornerRadius = null,
      ),
    )
    assertEquals(
      GlassRenderCapability.MagnifiedRim,
      resolveGlassRenderCapability(
        requestedCapability = GlassRenderCapability.RuntimeRefraction,
        runtimeEligible = false,
        runtimeAvailable = false,
      ),
    )
    assertEquals(
      GlassRenderCapability.RuntimeRefraction,
      resolveGlassRenderCapability(
        requestedCapability = GlassRenderCapability.RuntimeRefraction,
        runtimeEligible = true,
        runtimeAvailable = true,
      ),
    )
  }

  @Test
  fun glassSamplePadding_includesMaximumPressedRefractionAndRimReach() {
    val padding = calculateGlassSamplePadding(
      blurRadiusPx = 1f,
      refractionStrengthPx = 4f,
      rimWidthPx = 2f,
      pressedDepthBoost = 0.5f,
    )

    // max(blur 1 * 4, refraction 4 * 1.5 + two rim widths) = 10.
    assertEquals(10f, padding, 0f)
    val crop = calculateGlassSampleCrop(
      sourceSize = IntSize(200, 200),
      sourceOrigin = Offset.Zero,
      surfaceOrigin = Offset(50f, 60f),
      surfaceSize = IntSize(40, 30),
      blurPaddingPx = padding,
    )
    assertEquals(40, crop?.left)
    assertEquals(50, crop?.top)
    assertEquals(60, crop?.width)
    assertEquals(50, crop?.height)
  }

  @Test
  fun nextGlassFrameTick_advancesAndWrapsWithoutRepeatingThePublishedValue() {
    assertEquals(1, nextGlassFrameTick(0))
    assertEquals(42, nextGlassFrameTick(41))
    assertEquals(0, nextGlassFrameTick(Int.MAX_VALUE))
  }

  @Test
  fun glassSampleCrop_keepsOnlyTopPaneAndFullBlurSupport() {
    val crop = calculateGlassSampleCrop(
      sourceSize = IntSize(1080, 2340),
      sourceOrigin = Offset.Zero,
      surfaceOrigin = Offset.Zero,
      surfaceSize = IntSize(1080, 308),
      blurPaddingPx = 72f,
    )

    assertNotNull(crop)
    assertEquals(0, crop?.left)
    assertEquals(0, crop?.top)
    assertEquals(1080, crop?.width)
    assertEquals(380, crop?.height)
    assertEquals(Offset.Zero, crop?.offsetInSurface)
  }

  @Test
  fun glassSampleCrop_clampsFloatingPaneToSourceWithoutChangingAlignment() {
    val crop = calculateGlassSampleCrop(
      sourceSize = IntSize(1080, 2340),
      sourceOrigin = Offset.Zero,
      surfaceOrigin = Offset(37f, 2151f),
      surfaceSize = IntSize(1006, 168),
      blurPaddingPx = 72f,
    )

    assertNotNull(crop)
    assertEquals(0, crop?.left)
    assertEquals(2079, crop?.top)
    assertEquals(1080, crop?.width)
    assertEquals(261, crop?.height)
    assertEquals(Offset(-37f, -72f), crop?.offsetInSurface)
  }

  @Test
  fun glassSampleCrop_preservesRootAlignmentWhenSourceStartsBelowSurface() {
    val crop = calculateGlassSampleCrop(
      sourceSize = IntSize(1080, 2000),
      sourceOrigin = Offset(0f, 100f),
      surfaceOrigin = Offset.Zero,
      surfaceSize = IntSize(1080, 308),
      blurPaddingPx = 72f,
    )

    assertNotNull(crop)
    assertEquals(280, crop?.height)
    assertEquals(Offset(0f, 100f), crop?.offsetInSurface)
  }

  @Test
  fun glassSampleCrop_ignoresPaneOutsideSource() {
    assertNull(
      calculateGlassSampleCrop(
        sourceSize = IntSize(1080, 2340),
        sourceOrigin = Offset.Zero,
        surfaceOrigin = Offset(0f, 2500f),
        surfaceSize = IntSize(1080, 200),
        blurPaddingPx = 72f,
      ),
    )
  }

  private fun uniformRoundRect(radius: Float): RoundRect = RoundRect(
    left = 0f,
    top = 0f,
    right = 100f,
    bottom = 60f,
    topLeftCornerRadius = CornerRadius(radius, radius),
    topRightCornerRadius = CornerRadius(radius, radius),
    bottomRightCornerRadius = CornerRadius(radius, radius),
    bottomLeftCornerRadius = CornerRadius(radius, radius),
  )
}
