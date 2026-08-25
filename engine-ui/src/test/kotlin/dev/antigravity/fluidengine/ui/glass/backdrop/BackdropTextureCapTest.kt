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
  fun anAbsurdSurfaceStopsAtTheFloorRatherThanAtZero() {
    // A scale of zero is a layer of one pixel and a division by nothing downstream. Whatever the
    // caller has built, the answer stays a number the effect chain can use.
    val scale = fitToTexture(1f, 1080f, 1_000_000f)
    assertEquals(MinBackdropScale, scale, 0.0001f)
  }
}
