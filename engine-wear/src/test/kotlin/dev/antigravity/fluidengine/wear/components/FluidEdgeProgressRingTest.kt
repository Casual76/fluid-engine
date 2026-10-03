package dev.antigravity.fluidengine.wear.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidEdgeProgressRingTest {

  @Test
  fun progressIsSafeForUnknownDuration() {
    assertEquals(0f, ringProgress(5_000, 0), 0f)
    assertEquals(0.5f, ringProgress(5_000, 10_000), 1e-6f)
    assertEquals(1f, ringProgress(50_000, 10_000), 0f)
    assertEquals(0f, ringProgress(-5, 10_000), 0f)
  }

  @Test
  fun sweepIsAFullTurnAtTheEnd() {
    assertEquals(360f, ringSweep(1f), 0f)
    assertEquals(90f, ringSweep(0.25f), 1e-4f)
  }

  @Test
  fun stepMovesTheArcByHalfAPixel() {
    // Three minutes over ~1476 px of circumference: one half-pixel step is ~61 ms.
    assertEquals(60L, ringStepMillis(180_000, 1_476f))
    assertEquals(60L, ringStepMillis(180_000, 1_500f))
  }

  @Test
  fun stepIsClampedBetweenAFrameAndASecond() {
    assertEquals(16L, ringStepMillis(1_000, 10_000f))
    assertEquals(1_000L, ringStepMillis(36_000_000, 100f))
    assertEquals(1_000L, ringStepMillis(0, 100f))
  }

  @Test
  fun gapIsTheAngleTheClockCutsOut() {
    // A chord as wide as the radius cuts out sixty degrees.
    assertEquals(60f, ringGapDegrees(halfWidthPx = 50f, radiusPx = 100f), 1e-3f)
    assertEquals(0f, ringGapDegrees(0f, 100f), 0f)
    assertEquals(0f, ringGapDegrees(10f, 0f), 0f)
  }

  @Test
  fun gapNeverEatsMoreThanHalfTheRing() {
    assertEquals(180f, ringGapDegrees(500f, 100f), 0f)
  }

  @Test
  fun haloBrightensAsTheSongFills() {
    val start = haloIntensity(0f)
    val end = haloIntensity(1f)
    assertTrue(start > 0f)
    assertTrue(end > start)
    assertEquals(end, haloIntensity(4f), 0f)
  }
}
