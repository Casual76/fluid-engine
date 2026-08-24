package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GlassMaterialStateTest {

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
}
