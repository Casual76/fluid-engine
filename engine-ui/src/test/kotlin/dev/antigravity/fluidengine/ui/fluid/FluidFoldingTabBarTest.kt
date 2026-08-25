package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidFoldingTabBarTest {

  @Test
  fun openTabsShareTheCapsuleEvenly() {
    val widths = fluidFoldedTabWidths(count = 4, selectedIndex = 2, inner = 400, fold = 0f)
    assertEquals(listOf(100, 100, 100, 100), widths.toList())
  }

  @Test
  fun foldedTheSelectedTabIsTheWholeCapsuleAndTheRestAreGone() {
    // Zero, not "small": a tab that keeps a few pixels leaves a sliver of the previous layout
    // hanging off the folded capsule, which is exactly the artefact that reads as a rendering bug
    // rather than as a design.
    val widths = fluidFoldedTabWidths(count = 4, selectedIndex = 2, inner = 400, fold = 1f)
    assertEquals(listOf(0, 0, 400, 0), widths.toList())
  }

  @Test
  fun everyTabKeepsItsPlaceInTheOrderThroughTheFold() {
    // The property the whole component rests on: the widths in front of the selected tab shrink, so
    // the capsule closes *onto* it. If the selected tab were moved instead, a four-tab bar would
    // slide it three tab-widths across the screen on every fold.
    val open = fluidFoldedTabWidths(count = 3, selectedIndex = 0, inner = 300, fold = 0f)
    val mid = fluidFoldedTabWidths(count = 3, selectedIndex = 0, inner = 300, fold = 0.5f)
    val folded = fluidFoldedTabWidths(count = 3, selectedIndex = 0, inner = 300, fold = 1f)
    // The selected tab only ever grows; the others only ever shrink.
    assertTrue(mid[0] > open[0] && folded[0] > mid[0])
    assertTrue(mid[1] < open[1] && folded[1] < mid[1])
    // And at every point the row still adds up to the capsule it is in.
    for (widths in listOf(open, mid, folded)) {
      assertEquals(300, widths.sum())
    }
  }

  @Test
  fun theIndicatorEndsUpAsTheCapsule() {
    assertEquals(100, fluidFoldedIndicatorWidth(open = 100, inner = 400, fold = 0f))
    assertEquals(400, fluidFoldedIndicatorWidth(open = 100, inner = 400, fold = 1f))
    // Folded it fills the capsule, so it has nowhere left to travel: the tab you are left with *is*
    // the indicator rather than sitting somewhere inside it.
    assertEquals(0, fluidFoldedIndicatorTravel(position = 3f, tabWidth = 100, fold = 1f))
    assertEquals(300, fluidFoldedIndicatorTravel(position = 3f, tabWidth = 100, fold = 0f))
  }

  @Test
  fun theCapsuleClosesToASquare() {
    assertEquals(880, fluidFoldedCapsuleWidth(openWidth = 880, rowHeight = 156, fold = 0f))
    assertEquals(156, fluidFoldedCapsuleWidth(openWidth = 880, rowHeight = 156, fold = 1f))
  }

  @Test
  fun labelsAreGoneWellBeforeTheFoldIs() {
    // They stop being readable first, so they leave first — and they are fully gone by the time the
    // capsule is halfway, or the last frames of the close are spent squashing text.
    assertEquals(1f, fluidFoldAlpha(0f), 0f)
    assertEquals(0f, fluidFoldAlpha(0.45f), 0f)
    assertEquals(0f, fluidFoldAlpha(1f), 0f)
    assertTrue(fluidFoldAlpha(0.2f) in 0f..1f)
  }

  @Test
  fun theLensCanBeDraggedFromTabToTab() {
    // The regression this exists for: the indicator is a `GlassDragAnimation` and the bar simply
    // never handed it an `onDrag`, so holding the lens and pushing it sideways did nothing at all
    // and the capsule was a row of buttons wearing a lens.
    assertEquals(2f, fluidTabDragTarget(1f, 100f, 100, 4, isLtr = true), 0.001f)
    assertEquals(1.5f, fluidTabDragTarget(1f, 50f, 100, 4, isLtr = true), 0.001f)
    // Right to left, a push to the right is a push toward the *previous* tab.
    assertEquals(0f, fluidTabDragTarget(1f, 100f, 100, 4, isLtr = false), 0.001f)
    // And it stops at the ends rather than running off them.
    assertEquals(3f, fluidTabDragTarget(1f, 900f, 100, 4, isLtr = true), 0.001f)
    assertEquals(0f, fluidTabDragTarget(1f, -900f, 100, 4, isLtr = true), 0.001f)
  }

  @Test
  fun aDragBeforeAnythingHasBeenMeasuredLeavesTheLensWhereItIs() {
    // The first frame can deliver a pointer event to a node with no width yet. Dividing by it would
    // send the indicator to an end of the bar and select a tab nobody asked for.
    assertEquals(1f, fluidTabDragTarget(1f, 400f, 0, 4, isLtr = true), 0.001f)
    assertEquals(0f, fluidTabDragTarget(0f, 400f, 100, 1, isLtr = true), 0.001f)
  }

  @Test
  fun degenerateInputsDoNotProduceDegenerateLayouts() {
    // These run on the measure pass of every frame, including the first one, where a bar can be
    // asked to lay itself out before it has any width at all.
    assertEquals(0, fluidFoldedTabWidths(count = 0, selectedIndex = 0, inner = 0, fold = 0f).size)
    assertTrue(fluidFoldedIndicatorWidth(open = 0, inner = 0, fold = 0.5f) >= 1)
    assertTrue(fluidFoldedCapsuleWidth(openWidth = 0, rowHeight = 0, fold = 0.5f) >= 1)
    // A fold outside 0..1 is a caller mistake, not a reason to place a tab off the screen.
    assertEquals(
      fluidFoldedTabWidths(count = 2, selectedIndex = 1, inner = 200, fold = 1f).toList(),
      fluidFoldedTabWidths(count = 2, selectedIndex = 1, inner = 200, fold = 4f).toList(),
    )
  }
}
