package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidScrollPhysicsTest {

  @Test
  fun titleCollapseProgress_isLinearInTheScrollAndClamped() {
    val travel = 160f

    assertEquals(0f, titleCollapseProgress(0f, travel), 0f)
    assertEquals(0.25f, titleCollapseProgress(40f, travel), 0.0001f)
    assertEquals(0.5f, titleCollapseProgress(80f, travel), 0.0001f)
    assertEquals(1f, titleCollapseProgress(160f, travel), 0f)
    assertEquals(1f, titleCollapseProgress(Float.POSITIVE_INFINITY, travel), 0f)
  }

  @Test
  fun titleCollapseProgress_isBinaryBeforeTheTravelIsMeasured() {
    assertEquals(0f, titleCollapseProgress(0f, Float.NaN), 0f)
    assertEquals(1f, titleCollapseProgress(24f, Float.NaN), 0f)
    assertEquals(1f, titleCollapseProgress(24f, 0f), 0f)
  }

  @Test
  fun titleSnap_leavesNoRestingPointBetweenTheTwoPositions() {
    val travel = 160f

    assertEquals(0f, titleSnapTarget(1f, travel), 0f)
    assertEquals(0f, titleSnapTarget(79f, travel), 0f)
    assertEquals(travel, titleSnapTarget(80f, travel), 0f)
    assertEquals(travel, titleSnapTarget(159f, travel), 0f)
  }

  @Test
  fun titleMorph_fallsBackForLargeFontsWrapAndReducedMotion() {
    assertTrue(shouldMorphTitle(fontScale = 1f, expandedLineCount = 1, reducedMotion = false))
    assertFalse(shouldMorphTitle(fontScale = 1.3f, expandedLineCount = 1, reducedMotion = false))
    assertFalse(shouldMorphTitle(fontScale = 1f, expandedLineCount = 2, reducedMotion = false))
    assertFalse(shouldMorphTitle(fontScale = 1f, expandedLineCount = 1, reducedMotion = true))
  }

  @Test
  fun morphingTitle_showsExactlyOneCopyAtEveryProgress() {
    (0..20).map { it / 20f }.forEach { value ->
      assertEquals(0f, expandedTitleAlpha(value, morphing = true, reducedMotion = false), 0f)
      assertEquals(1f, compactTitleAlpha(value, morphing = true, reducedMotion = false), 0f)
    }
  }

  @Test
  fun accessibilityHandoff_keepsCopiesDisjoint() {
    assertEquals(0f, expandedTitleAlpha(0.78f, morphing = false, reducedMotion = false), 0f)
    assertEquals(0f, compactTitleAlpha(0.78f, morphing = false, reducedMotion = false), 0f)
    assertEquals(0f, compactTitleAlpha(0.81f, morphing = false, reducedMotion = false), 0f)
    assertTrue(compactTitleAlpha(0.9f, morphing = false, reducedMotion = false) > 0f)
  }

  @Test
  fun rubberBand_startsAtTheResistanceRateAndNeverReachesItsAsymptote() {
    val dimension = 600f
    val values = listOf(0f, 10f, 120f, 600f, 1_000_000f)
      .map { rubberBandTranslation(it, dimension) }

    assertEquals(0f, values.first(), 0f)
    // A short drag still follows the finger at the UIKit rate, so the edge never feels dead.
    assertEquals(10f * OverscrollResistance, values[1], 0.2f)
    assertTrue(values.zipWithNext().all { (before, after) -> after > before })
    assertTrue(values.all { it < dimension })
    assertTrue(values.last() > dimension * 0.99f)
  }

  @Test
  fun rubberBand_roundTripsThroughItsInverse() {
    val dimension = 600f
    listOf(25f, 140f, 380f).forEach { raw ->
      val visual = rubberBandTranslation(raw, dimension)
      assertEquals(raw, inverseRubberBandTranslation(visual, dimension), 0.01f)
    }
  }

  @Test
  fun signedRubberBand_isSymmetricAcrossBothEdges() {
    val dimension = 600f

    assertEquals(
      -rubberBandTranslation(200f, dimension),
      signedRubberBandTranslation(-200f, dimension),
      0.0001f,
    )
    assertEquals(
      rubberBandTranslation(200f, dimension),
      signedRubberBandTranslation(200f, dimension),
      0.0001f,
    )
    assertEquals(-200f, signedInverseRubberBandTranslation(signedRubberBandTranslation(-200f, dimension), dimension), 0.01f)
  }

  @Test
  fun morphSettles_towardsWhateverTheScrollProposes() {
    // The filter is a spring, so the only contract the pure helpers owe is that the proposal they
    // are fed is itself exact and monotonic in the scroll.
    val travel = 160f
    val proposals = listOf(0f, 20f, 60f, 100f, 160f).map { titleCollapseProgress(it, travel) }

    assertTrue(proposals.zipWithNext().all { (before, after) -> after > before })
    assertEquals(0f, proposals.first(), 0f)
    assertEquals(1f, proposals.last(), 0f)
  }

  @Test
  fun shortPage_hasNoBottomEdgeToBounceAgainst() {
    val state = FluidEdgeOverscrollState(reducedMotion = false, canScroll = { false })
    state.updateViewport(2_400f)

    // Dragging up on a page with nothing below it must not move anything: there is no end of the
    // list to report, and moving would carry the large title into the status bar.
    val consumed = state.onPostScroll(
      consumed = Offset.Zero,
      available = Offset(0f, -220f),
      source = NestedScrollSource.UserInput,
    )

    assertEquals(Offset.Zero, consumed)
    assertEquals(0f, state.offsetPx, 0f)
  }

  @Test
  fun shortPage_stillOpensTheTopEdgeSoRefreshRemainsReachable() {
    val state = FluidEdgeOverscrollState(reducedMotion = false, canScroll = { false })
    state.updateViewport(2_400f)

    state.onPostScroll(
      consumed = Offset.Zero,
      available = Offset(0f, 220f),
      source = NestedScrollSource.UserInput,
    )

    assertTrue(state.offsetPx > 0f)
  }

  @Test
  fun scrollablePage_bouncesAtBothEnds() {
    val state = FluidEdgeOverscrollState(reducedMotion = false, canScroll = { true })
    state.updateViewport(2_400f)

    state.onPostScroll(Offset.Zero, Offset(0f, -220f), NestedScrollSource.UserInput)

    assertTrue(state.offsetPx < 0f)
  }

  @Test
  fun refreshPull_reportsProgressTowardsTheThresholdOnly() {
    val state = FluidEdgeOverscrollState(reducedMotion = false)
    state.updateViewport(2_400f)
    state.refreshTriggerPx = 200f

    assertEquals(0f, state.refreshPull, 0f)

    state.onPostScroll(Offset.Zero, Offset(0f, 400f), NestedScrollSource.UserInput)

    assertTrue(state.refreshPull > 0f)
    assertTrue(state.refreshPull <= 1f)
  }

  @Test
  fun barCommitThreshold_asksMoreOfHidingThanOfRevealing() {
    val density = 3f
    val hide = bottomBarCommitThresholdPx(hidingTravel = true, density = density)
    val reveal = bottomBarCommitThresholdPx(hidingTravel = false, density = density)

    assertTrue(hide > reveal)
    // Both are real distances, so a jittery scroll that never commits moves the bar not at all.
    assertTrue(reveal > 0f)
  }

  @Test
  fun progressiveGlassCoverage_hasNoBinaryFirstFrame() {
    val first = calculateProgressiveGlassCoverage(0.01f)
    assertTrue(first > 0f)
    assertTrue(first < 0.001f)
    assertEquals(0.5f, calculateProgressiveGlassCoverage(0.5f), 0.0001f)
    assertEquals(1f, calculateProgressiveGlassCoverage(1f), 0f)
  }
}
