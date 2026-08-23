package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FluidSectionIndexTest {

  @Test
  fun sampling_keepsFirstLastAndAtMostEightAnchors() {
    val anchors = (0 until 21).map { index -> anchor(index) }

    val sampled = sampleFluidSectionAnchors(anchors, maxVisibleAnchors = 8)

    assertEquals(8, sampled.size)
    assertEquals("section-0", sampled.first().key)
    assertEquals("section-20", sampled.last().key)
    assertEquals(sampled.distinct(), sampled)
  }

  @Test
  fun sampling_handlesEmptyAndSingleVisibleAnchor() {
    assertEquals(emptyList<FluidSectionAnchor>(), sampleFluidSectionAnchors(emptyList(), 8))
    assertEquals(listOf(anchor(0)), sampleFluidSectionAnchors((0..4).map(::anchor), 1))
    assertEquals(emptyList<FluidSectionAnchor>(), sampleFluidSectionAnchors((0..4).map(::anchor), 0))
  }

  @Test
  fun sampling_keepsTheActiveAnchorWithoutLosingTheEnds() {
    val anchors = (0 until 21).map(::anchor)

    val sampled = sampleFluidSectionAnchors(
      sections = anchors,
      maxVisibleAnchors = 8,
      preferredKey = "section-11",
    )

    assertEquals(8, sampled.size)
    assertEquals("section-0", sampled.first().key)
    assertEquals("section-20", sampled.last().key)
    assertEquals(true, sampled.any { it.key == "section-11" })
  }

  @Test
  fun activeSection_usesGreatestAnchorNotAfterVisibleItem() {
    val anchors = listOf(
      FluidSectionAnchor("mon", "Lun", 5),
      FluidSectionAnchor("tue", "Mar", 9),
      FluidSectionAnchor("wed", "Mer", 14),
    )

    assertNull(activeFluidSectionForItemIndex(anchors, 4))
    assertEquals("mon", activeFluidSectionForItemIndex(anchors, 5)?.key)
    assertEquals("mon", activeFluidSectionForItemIndex(anchors, 8)?.key)
    assertEquals("tue", activeFluidSectionForItemIndex(anchors, 13)?.key)
    assertEquals("wed", activeFluidSectionForItemIndex(anchors, 99)?.key)
  }

  @Test
  fun pointerPosition_clampsAndMapsToNearestSection() {
    assertEquals(0, fluidSectionIndexForPosition(-20f, 100f, 6))
    assertEquals(0, fluidSectionIndexForPosition(0f, 100f, 6))
    assertEquals(2, fluidSectionIndexForPosition(40f, 100f, 6))
    assertEquals(5, fluidSectionIndexForPosition(100f, 100f, 6))
    assertEquals(5, fluidSectionIndexForPosition(500f, 100f, 6))
    assertEquals(0, fluidSectionIndexForPosition(50f, 0f, 6))
    assertEquals(0, fluidSectionIndexForPosition(50f, 100f, 1))
  }

  @Test
  fun selectedKey_fallsBackToFirstAnchor() {
    val anchors = listOf(anchor(0), anchor(1), anchor(2))
    assertEquals(2, activeFluidSectionIndex(anchors, "section-2"))
    assertEquals(0, activeFluidSectionIndex(anchors, "missing"))
    assertEquals(-1, activeFluidSectionIndex(emptyList(), null))
  }

  private fun anchor(index: Int) = FluidSectionAnchor(
    key = "section-$index",
    label = "Section $index",
    itemIndex = index * 3,
  )
}
