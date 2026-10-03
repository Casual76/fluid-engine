package dev.antigravity.fluidengine.wear.components

import org.junit.Assert.assertEquals
import org.junit.Test

class RotaryAccumulatorTest {

  @Test
  fun bezelCountsEventsNotPixels() {
    val acc = RotaryAccumulator(stepPx = 64f, lowRes = true)
    assertEquals(1, acc.add(3f))
    assertEquals(1, acc.add(500f))
    assertEquals(-1, acc.add(-0.5f))
    assertEquals(0, acc.add(0f))
  }

  @Test
  fun crownAccumulatesDistance() {
    val acc = RotaryAccumulator(stepPx = 64f, lowRes = false)
    assertEquals(0, acc.add(40f))
    assertEquals(1, acc.add(40f))
    assertEquals(2, acc.add(130f))
  }

  @Test
  fun changingDirectionDropsWhatWasGathered() {
    val acc = RotaryAccumulator(stepPx = 64f, lowRes = false)
    acc.add(60f)
    assertEquals(0, acc.add(-10f))
    assertEquals(-1, acc.add(-60f))
  }

  @Test
  fun garbageIsIgnored() {
    val acc = RotaryAccumulator(stepPx = 64f, lowRes = false)
    assertEquals(0, acc.add(Float.NaN))
    assertEquals(0, acc.add(Float.POSITIVE_INFINITY))
  }
}
