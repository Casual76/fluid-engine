package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pieces of arithmetic that decide whether the bar is a pill or a slab.
 *
 * Both of them were wrong at once, and the second hid the first: five tabs measured at their full
 * ceiling overflowed the bar, so the labels wrapped, so the bar grew to twice its nominal height.
 * Neither needs a device to check, and neither had a test.
 */
class FluidFloatingTabBarLayoutTest {

  private val inset = 8.dp
  private val preferred = FluidFloatingTabBarDefaults.TabWidth

  @Test
  fun `five tabs on a phone are fitted to the bar, with no standalone circle`() {
    // A 411 dp phone, less the bar's own 14 dp side margins.
    val fitted = fittedTabWidth(
      barWidth = 383.dp,
      standaloneWidth = 0.dp,
      gap = 0.dp,
      inset = inset,
      tabsCount = 5,
      preferred = preferred,
    )

    assertEquals(75.dp, fitted)
    assertTrue("five fitted tabs plus the inset must fit the bar", fitted * 5 + inset <= 383.dp)
  }

  @Test
  fun `a bar with room to spare keeps the ceiling`() {
    val fitted = fittedTabWidth(
      barWidth = 600.dp,
      standaloneWidth = 0.dp,
      gap = 0.dp,
      inset = inset,
      tabsCount = 4,
      preferred = preferred,
    )

    assertEquals(preferred, fitted)
  }

  @Test
  fun `the standalone circle and its gap come out of the tabs' share`() {
    val withCircle = fittedTabWidth(
      barWidth = 383.dp,
      standaloneWidth = 60.dp,
      gap = 8.dp,
      inset = inset,
      tabsCount = 5,
      preferred = preferred,
    )
    val without = fittedTabWidth(
      barWidth = 383.dp,
      standaloneWidth = 0.dp,
      gap = 0.dp,
      inset = inset,
      tabsCount = 5,
      preferred = preferred,
    )

    assertTrue("the circle must cost the tabs width", withCircle < without)
    assertTrue(
      "tabs plus circle plus gap plus inset must fit the bar",
      withCircle * 5 + 60.dp + 8.dp + inset <= 383.dp,
    )
  }

  @Test
  fun `nothing measured yet leaves the ceiling alone`() {
    assertEquals(
      preferred,
      fittedTabWidth(0.dp, 0.dp, 0.dp, inset, 5, preferred),
    )
    assertEquals(
      preferred,
      fittedTabWidth(383.dp, 0.dp, 0.dp, inset, 0, preferred),
    )
  }

  @Test
  fun `a bar narrower than its own padding never asks for a negative tab`() {
    assertTrue(fittedTabWidth(4.dp, 0.dp, 0.dp, inset, 5, preferred) >= 0.dp)
  }

  @Test
  fun `a label that fits is not touched`() {
    assertEquals(1f, labelScale(wanted = 40, room = 64), 0f)
    assertEquals(1f, labelScale(wanted = 64, room = 64), 0f)
  }

  @Test
  fun `a label a little too wide is shrunk exactly enough`() {
    assertEquals(0.8f, labelScale(wanted = 80, room = 64), 0.0001f)
  }

  @Test
  fun `a hopeless label stops shrinking instead of vanishing`() {
    assertEquals(MinLabelScale, labelScale(wanted = 400, room = 64), 0f)
  }

  @Test
  fun `an unmeasured slot leaves the label alone`() {
    assertEquals(1f, labelScale(wanted = 80, room = 0), 0f)
  }

  @Test
  fun `the declared content inset covers the height the bar actually keeps`() {
    // The bar's floor plus its bottom margin. These three used to disagree — the constant said 64
    // and the bar stood at about 90 — and a screen that believed the constant had its last row
    // under the bar.
    assertEquals(
      FluidFloatingTabBarDefaults.OpenHeight + FluidFloatingTabBarDefaults.BottomMargin,
      FluidFloatingTabBarDefaults.ContentInset,
    )
    assertTrue(
      "folded is the same bar without a label, so it cannot be taller than open",
      FluidFloatingTabBarDefaults.FoldedHeight < FluidFloatingTabBarDefaults.OpenHeight,
    )
  }
}
