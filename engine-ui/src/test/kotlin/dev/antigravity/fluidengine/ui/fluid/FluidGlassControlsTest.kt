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

  @Test
  fun sliderMapsAValueToItsTrackAndBackAgain() {
    val range = 0f..24f
    assertEquals(0f, fluidSliderFraction(0f, range), 0.0001f)
    assertEquals(0.5f, fluidSliderFraction(12f, range), 0.0001f)
    assertEquals(1f, fluidSliderFraction(24f, range), 0.0001f)

    // A value from outside the range is somebody else's stale preference, not a reason to place the
    // handle off the end of the track.
    assertEquals(0f, fluidSliderFraction(-3f, range), 0.0001f)
    assertEquals(1f, fluidSliderFraction(99f, range), 0.0001f)

    // Round trip, on a range that does not start at zero — which is where an off-by-a-start shows.
    val offset = 20f..80f
    for (v in listOf(20f, 35f, 50f, 80f)) {
      assertEquals(v, fluidSliderValue(fluidSliderFraction(v, offset), offset), 0.0001f)
    }
  }

  @Test
  fun aSliderWithNoRangeAndNoWidthStaysPutInsteadOfDividingByZero() {
    // Both of these reach the measure pass on the first frame, before anything has been laid out,
    // and a caller can hand over an empty range at any time.
    val empty = 5f..5f
    assertEquals(0f, fluidSliderFraction(7f, empty), 0f)
    assertEquals(5f, fluidSliderValue(0.7f, empty), 0f)
    assertEquals(0f, fluidSliderFractionAt(x = 120f, edgePx = 20f, trackPx = 0f), 0f)
  }

  @Test
  fun aTouchIsReadLinearlyOnTheTrackBetweenTheTwoEdges() {
    // A 300 px row with 20 px of edge on each side: 260 px of track, and the middle of it is at
    // 20 + 130 = 150.
    assertEquals(0.5f, fluidSliderFractionAt(x = 150f, edgePx = 20f, trackPx = 260f), 0.001f)
    assertEquals(0.25f, fluidSliderFractionAt(x = 85f, edgePx = 20f, trackPx = 260f), 0.001f)
    // The edges belong to the row, so a finger pressed there still reaches the ends of the range.
    assertEquals(0f, fluidSliderFractionAt(x = 0f, edgePx = 20f, trackPx = 260f), 0f)
    assertEquals(0f, fluidSliderFractionAt(x = 20f, edgePx = 20f, trackPx = 260f), 0f)
    assertEquals(1f, fluidSliderFractionAt(x = 300f, edgePx = 20f, trackPx = 260f), 0f)
  }

  @Test
  fun theThumbFollowsTheValueButNeverGetsCloserToAnEndThanAQuarterOfItself() {
    // A 40 dp thumb at 2x is 80 px: its centre stays 20 px inside either end of a 260 px track,
    // which is the source's clamp, and it is what keeps the pill inside the control.
    val thumb = 80f
    val track = 260f
    assertEquals(20f, fluidSliderThumbCentre(0f, track, thumb), 0f)
    assertEquals(20f, fluidSliderThumbCentre(0.05f, track, thumb), 0f)
    assertEquals(130f, fluidSliderThumbCentre(0.5f, track, thumb), 0.001f)
    assertEquals(0.75f * track, fluidSliderThumbCentre(0.75f, track, thumb), 0.001f)
    assertEquals(240f, fluidSliderThumbCentre(0.97f, track, thumb), 0f)
    assertEquals(240f, fluidSliderThumbCentre(1f, track, thumb), 0f)
  }

  @Test
  fun aTrackNarrowerThanTheThumbStillPlacesItInsteadOfThrowing() {
    // The clamp's two ends cross when the track is shorter than half the thumb; the thumb then sits
    // at the low end rather than the layout failing on an empty range.
    assertEquals(20f, fluidSliderThumbCentre(0.5f, trackPx = 30f, thumbPx = 80f), 0f)
  }
}
