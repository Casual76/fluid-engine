package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidSkeletonTest {

  @Test
  fun `still skeleton is the rest veil, between the two ends of the breath`() {
    val rest = fluidSkeletonAlpha(null)
    assertEquals(FluidSkeletonDefaults.RestAlpha, rest, 0f)
    assertTrue(rest > FluidSkeletonDefaults.LowAlpha && rest < FluidSkeletonDefaults.HighAlpha)
  }

  @Test
  fun `the breath runs from the low alpha to the high one`() {
    assertEquals(FluidSkeletonDefaults.LowAlpha, fluidSkeletonAlpha(0f), 1e-6f)
    assertEquals(FluidSkeletonDefaults.HighAlpha, fluidSkeletonAlpha(1f), 1e-6f)
  }

  @Test
  fun `a breath outside zero to one is held at the ends`() {
    assertEquals(FluidSkeletonDefaults.LowAlpha, fluidSkeletonAlpha(-0.5f), 1e-6f)
    assertEquals(FluidSkeletonDefaults.HighAlpha, fluidSkeletonAlpha(1.5f), 1e-6f)
  }

  @Test
  fun `a paragraph is full lines and a short last one`() {
    assertEquals(listOf(1f, 1f, 0.64f), fluidSkeletonLineFractions(3))
    assertEquals(listOf(1f, 0.5f), fluidSkeletonLineFractions(2, lastFraction = 0.5f))
  }

  @Test
  fun `a one line paragraph is just the short line`() {
    assertEquals(listOf(0.64f), fluidSkeletonLineFractions(1))
  }

  @Test
  fun `no lines is no skeleton and a negative count is the same`() {
    assertEquals(emptyList<Float>(), fluidSkeletonLineFractions(0))
    assertEquals(emptyList<Float>(), fluidSkeletonLineFractions(-4))
  }

  @Test
  fun `the last line never collapses to nothing or overflows`() {
    assertEquals(0.1f, fluidSkeletonLineFractions(2, lastFraction = 0f).last(), 0f)
    assertEquals(1f, fluidSkeletonLineFractions(2, lastFraction = 3f).last(), 0f)
  }

  @Test
  fun `row titles alternate so a column does not read as a table`() {
    assertEquals(0.72f, fluidSkeletonTitleFraction(0), 0f)
    assertEquals(0.56f, fluidSkeletonTitleFraction(1), 0f)
    assertEquals(0.72f, fluidSkeletonTitleFraction(2), 0f)
  }

  @Test
  fun `a row is as tall as the real one for its lines of text`() {
    assertEquals(56.dp, fluidSkeletonRowMinHeight(1))
    assertEquals(72.dp, fluidSkeletonRowMinHeight(2))
    assertEquals(88.dp, fluidSkeletonRowMinHeight(3))
  }

  @Test
  fun `row height is held between one and three lines`() {
    assertEquals(56.dp, fluidSkeletonRowMinHeight(0))
    assertEquals(88.dp, fluidSkeletonRowMinHeight(9))
  }
}
