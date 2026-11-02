package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidStepProgressTest {

  @Test
  fun `step two of five is forty percent, not a quarter`() {
    assertEquals(0.4f, fluidStepFraction(2f, 5), 1e-6f)
  }

  @Test
  fun `the first step is already some way along and the last is full`() {
    assertEquals(0.2f, fluidStepFraction(1f, 5), 1e-6f)
    assertEquals(1f, fluidStepFraction(5f, 5), 0f)
  }

  @Test
  fun `a fraction held between zero and one`() {
    assertEquals(0f, fluidStepFraction(-2f, 5), 0f)
    assertEquals(1f, fluidStepFraction(9f, 5), 0f)
    assertEquals(0f, fluidStepFraction(2f, 0), 0f)
  }

  @Test
  fun `an animating position in between steps is in between fractions`() {
    assertEquals(0.5f, fluidStepFraction(2.5f, 5), 1e-6f)
  }

  @Test
  fun `a segment fills as the position passes over it`() {
    assertEquals(1f, fluidStepSegmentFill(0, 2.5f), 0f)
    assertEquals(1f, fluidStepSegmentFill(1, 2.5f), 0f)
    assertEquals(0.5f, fluidStepSegmentFill(2, 2.5f), 1e-6f)
    assertEquals(0f, fluidStepSegmentFill(3, 2.5f), 0f)
  }

  @Test
  fun `segments only when asked and when they fit`() {
    assertFalse(fluidStepSegmented(requested = false, steps = 5))
    assertTrue(fluidStepSegmented(requested = true, steps = 5))
    assertTrue(fluidStepSegmented(true, 2))
    assertTrue(fluidStepSegmented(true, FluidStepProgressDefaults.MaxSegments))
  }

  @Test
  fun `one step has nothing to segment and too many steps fall back to the bar`() {
    assertFalse(fluidStepSegmented(true, 1))
    assertFalse(fluidStepSegmented(true, 0))
    assertFalse(fluidStepSegmented(true, FluidStepProgressDefaults.MaxSegments + 1))
  }

  @Test
  fun `the default label is the italian step phrase`() {
    assertEquals("Passo 2 di 5", FluidStepProgressDefaults.label(2, 5))
  }
}
