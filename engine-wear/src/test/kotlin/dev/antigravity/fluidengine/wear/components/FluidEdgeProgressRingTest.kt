package dev.antigravity.fluidengine.wear.components

import org.junit.Assert.assertEquals
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
}
