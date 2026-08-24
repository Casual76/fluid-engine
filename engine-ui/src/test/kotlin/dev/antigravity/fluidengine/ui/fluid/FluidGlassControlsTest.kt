package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassControlsTest {

  @Test
  fun iconButton_keepsA48DpHitTargetAroundThe44DpLens() {
    assertTrue(FluidGlassIconTouchTargetSize >= 48.dp)
    assertEquals(44.dp, FluidGlassIconLensSize)
    assertTrue(FluidGlassIconTouchTargetSize > FluidGlassIconLensSize)
  }

  @Test
  fun glassButton_isTallEnoughToBeTappedWithoutAnExtraTouchTarget() {
    assertTrue(FluidGlassButtonHeight >= 48.dp)
  }

  @Test
  fun tabIndicator_travelsFromTheStartEdgeInBothWritingDirections() {
    val tabWidth = 100f
    val container = 500f

    // Left to right: tab 0 sits at the left edge, tab 4 one tab short of the right one.
    assertEquals(0f, tabIndicatorTranslation(0f, tabWidth, 0f, container, isLtr = true), 0f)
    assertEquals(400f, tabIndicatorTranslation(4f, tabWidth, 0f, container, isLtr = true), 0f)

    // Right to left: the same two tabs, mirrored, and neither leaves the bar.
    assertEquals(400f, tabIndicatorTranslation(0f, tabWidth, 0f, container, isLtr = false), 0f)
    assertEquals(0f, tabIndicatorTranslation(4f, tabWidth, 0f, container, isLtr = false), 0f)
  }

  @Test
  fun tabIndicator_carriesTheBarsOwnOverscrollWithIt() {
    val ltr = tabIndicatorTranslation(1f, 100f, barOffset = 6f, containerWidth = 500f, isLtr = true)
    val rtl = tabIndicatorTranslation(1f, 100f, barOffset = 6f, containerWidth = 500f, isLtr = false)

    // The lens and the bar move together, so a pull past the end never separates them.
    assertEquals(106f, ltr, 0f)
    assertEquals(294f, rtl, 0f)
  }
}
