package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassGroupsTest {

  @Test
  fun anEmptyListHasNoPanels() {
    assertTrue(fluidGlassGroupPlan(0).isEmpty())
  }

  @Test
  fun aListThatFitsInOnePanelIsWholeAndJoinsToNothing() {
    listOf(1, 5, 8).forEach { count ->
      val plan = fluidGlassGroupPlan(count, maxRows = 8)
      assertEquals("$count righe", 1, plan.size)
      assertEquals(FluidGroupSegment.Whole, plan.single().segment)
      assertFalse(plan.single().joinToNext)
      assertEquals(0, plan.single().from)
      assertEquals(count, plan.single().toExclusive)
    }
  }

  @Test
  fun aLongListIsFirstThenMiddlesThenLast() {
    val plan = fluidGlassGroupPlan(20, maxRows = 8)
    assertEquals(
      listOf(FluidGroupSegment.First, FluidGroupSegment.Middle, FluidGroupSegment.Last),
      plan.map { it.segment },
    )
    assertEquals(listOf(0 to 8, 8 to 16, 16 to 20), plan.map { it.from to it.toExclusive })
  }

  @Test
  fun twoPanelsAreFirstAndLastWithNoMiddle() {
    val plan = fluidGlassGroupPlan(9, maxRows = 8)
    assertEquals(listOf(FluidGroupSegment.First, FluidGroupSegment.Last), plan.map { it.segment })
    // Un'ultima riga da sola in un pannello e' prevista: gli angoli sono quelli giusti, e il
    // pannello e' alto una riga e basta.
    assertEquals(1, plan.last().toExclusive - plan.last().from)
  }

  @Test
  fun everyPanelButTheLastJoinsTheNext() {
    val plan = fluidGlassGroupPlan(40, maxRows = 8)
    assertEquals(5, plan.size)
    assertTrue(plan.dropLast(1).all { it.joinToNext })
    assertFalse(plan.last().joinToNext)
  }

  @Test
  fun everyRowIsInExactlyOnePanelAndInOrder() {
    listOf(1, 7, 8, 9, 16, 17, 83, 84, 85).forEach { count ->
      val plan = fluidGlassGroupPlan(count, maxRows = 8)
      val covered = plan.flatMap { (it.from until it.toExclusive).toList() }
      assertEquals("$count righe", (0 until count).toList(), covered)
      assertTrue("$count righe: pannello oltre il tetto", plan.all { it.toExclusive - it.from <= 8 })
    }
  }

  @Test
  fun theCapIsTheOneThatKeepsAPanelUnderTheTextureLimit() {
    assertEquals(8, FluidGlassGroupMaxRows)
    // Ottantaquattro voti: undici pannelli invece di uno da sedicimila pixel.
    assertEquals(11, fluidGlassGroupPlan(84).size)
  }

  @Test
  fun aCapBelowOneIsRefused() {
    assertThrows(IllegalArgumentException::class.java) { fluidGlassGroupPlan(10, maxRows = 0) }
  }

  @Test
  fun theGapToAbsorbIsTheSpacingPlusBothTrimmedEdges() {
    // Il pezzo che segue deve risalire della spaziatura piu' il proprio ritaglio e quello del
    // precedente, o al posto della banda chiara resta una fessura chiara.
    assertEquals(16 + 2 * 4, fluidGlassGroupGapPx(spacingPx = 16, openEdgePx = 4))
  }

  @Test
  fun aJoinedPanelDeclaresLessHeightThanItDrawsAndNeverANegativeOne() {
    assertEquals(176, fluidGlassGroupJoinedHeight(drawnHeight = 200, gapPx = 24))
    assertEquals(0, fluidGlassGroupJoinedHeight(drawnHeight = 10, gapPx = 24))
  }
}
