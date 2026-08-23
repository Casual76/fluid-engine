package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidTabBarMotionTest {

  @Test
  fun `reduced motion snaps the top level indicator`() {
    val timing = tabIndicatorTiming(reducedMotion = true)

    assertEquals(0, timing.leadingDurationMillis)
    assertEquals(0, timing.trailingDurationMillis)
  }

  @Test
  fun `top level indicator has one short asymmetric settle`() {
    val timing = tabIndicatorTiming(reducedMotion = false)

    assertTrue(timing.leadingDurationMillis > 0)
    assertTrue(timing.leadingDurationMillis < timing.trailingDurationMillis)
    assertTrue(timing.trailingDurationMillis <= 280)
  }
}
