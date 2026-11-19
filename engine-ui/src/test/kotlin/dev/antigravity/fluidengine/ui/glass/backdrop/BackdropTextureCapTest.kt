package dev.antigravity.fluidengine.ui.glass.backdrop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackdropTextureCapTest {

  @Test
  fun ordinarySurfacesAreRecordedExactlyAsAsked() {
    assertEquals(1f, fitToTexture(1f, 1080f, 300f), 0.0001f)
    assertEquals(0.5f, fitToTexture(0.5f, 1080f, 2340f), 0.0001f)
  }

  @Test
  fun aSurfaceTallerThanATextureIsRecordedSmaller() {
    // The regression this exists for: a grouped list of eighty rows is one pane, and one pane is
    // one capture. Past the texture ceiling the capture comes back empty, and an empty capture
    // under a translucent tint is a black rectangle where the list was - which is exactly what
    // ClasseViva's grades page did the first time its rows were grouped.
    val scale = fitToTexture(1f, 1080f, 16800f)
    assertTrue(scale < 1f)
    assertTrue(16800f * scale <= MaxBackdropTextureDimension + 0.5f)
  }

  @Test
  fun theCapNeverRaisesTheScaleTheCallerAskedFor() {
    // A small surface that already asked for a coarse capture keeps it: the cap is a ceiling on
    // size, never a floor on quality.
    assertEquals(0.4f, fitToTexture(0.4f, 200f, 200f), 0.0001f)
  }

  @Test
  fun degenerateSizesAreLeftAlone() {
    // The first frame measures nothing, and dividing by it would take the scale to infinity.
    assertEquals(1f, fitToTexture(1f, 0f, 0f), 0.0001f)
    assertEquals(1f, fitToTexture(1f, Float.NaN, Float.NaN), 0.0001f)
  }

  @Test
  fun alternatingAcrossTheOldBoundarySwitchesOnlyOnceUntilTheGestureSettles() {
    val resolution = BackdropCaptureResolution()
    val levels = (0..15).map { index ->
      resolution.update(if (index % 2 == 0) 0.69f else 0.71f, 200_000f, index * 16L)
    }
    assertEquals(listOf(1f, 1f) + List(14) { ReducedScaleFactor }, levels)
    assertEquals(1f, resolution.update(1f, 200_000f, 256L), 0f)
    assertEquals(1f, resolution.update(0.25f, 200_000f, 272L), 0f)
  }

  @Test
  fun pixelPressureHasSeparateEntryAndExitThresholds() {
    val resolution = BackdropCaptureResolution()
    val pixels = listOf(126_000f, 130_000f, 126_000f, 130_000f, 126_000f, 66_000f, 64_000f, 66_000f)
    val levels = pixels.mapIndexed { i, area -> resolution.update(0.4f, area, i * 8L) }
    assertEquals(listOf(1f, 1f, 1f, 0.5f, 0.5f, 0.5f, 1f, 1f), levels)
  }

  @Test
  fun preDrawAndRepeatedDrawsDoNotInventAdditionalFrames() {
    val resolution = BackdropCaptureResolution()
    repeat(6) {
      assertEquals(1f, resolution.preview(0.25f, 300_000f, 10L), 0f)
      assertEquals(1f, resolution.update(0.25f, 300_000f, 10L), 0f)
    }
    assertEquals(0.5f, resolution.preview(0.25f, 300_000f, 27L), 0f)
    assertEquals(1f, resolution.factor, 0f)
    assertEquals(0.5f, resolution.update(0.25f, 300_000f, 27L), 0f)
    assertEquals(1f, resolution.update(0.25f, 300_000f, 27L + BackdropCaptureResolution.WindowMillis), 0f)
  }

  @Test
  fun validMovingCapturesCanReduceAtFullQualityAndExpireWithoutScroll() {
    val resolution = BackdropCaptureResolution()
    // The sheet moves against its source without scrolling a list. Counting commits after capture
    // keeps pre-draw and drawing the exported material from advancing its history.
    for (i in 0..2) {
      val t = i * 16L
      assertEquals(1f, resolution.update(1f, 200_000f, t), 0f)
      resolution.movedCapture(t)
      resolution.movedCapture(t)
    }
    assertEquals(0.5f, resolution.preview(1f, 200_000f, 48L), 0f)
    assertEquals(0.5f, resolution.update(1f, 200_000f, 48L), 0f)
    assertEquals(1f, resolution.update(1f, 200_000f, 128L), 0f)
    resolution.movedCapture(144L)
    resolution.reset()
    assertEquals(1f, resolution.update(1f, 900_000f, 150L), 0f)
  }

  @Test
  fun smallTexturesSlowUpdatesAndMalformedQualityKeepTheOrdinaryScale() {
    val resolution = BackdropCaptureResolution()
    repeat(10) { i -> assertEquals(1f, resolution.update(0.25f, 55_000f, i * 16L), 0f) }
    repeat(5) { i -> assertEquals(1f, resolution.update(0.25f, 900_000f, 300L + i * 120L), 0f) }
    assertEquals(0.5f, resolution.update(0.25f, 900_000f, 790L), 0f)
    assertEquals(1f, resolution.update(Float.NaN, 900_000f, 805L), 0f)
    assertEquals(1f, resolution.update(0.25f, 900_000f, 820L), 0f)
    assertEquals(0.5f, resolution.update(0.25f, 900_000f, 836L), 0f)
    assertEquals(1f, resolution.update(0.85f, 900_000f, 852L), 0f)
  }

  @Test
  fun anAbsurdSurfaceStopsAtTheFloorRatherThanAtZero() {
    // A scale of zero is a layer of one pixel and a division by nothing downstream. Whatever the
    // caller has built, the answer stays a number the effect chain can use.
    val scale = fitToTexture(1f, 1080f, 1_000_000f)
    assertEquals(MinBackdropScale, scale, 0.0001f)
  }
}
