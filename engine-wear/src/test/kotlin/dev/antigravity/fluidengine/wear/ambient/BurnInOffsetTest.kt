package dev.antigravity.fluidengine.wear.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BurnInOffsetTest {

  @Test
  fun walkStaysWithinReachAndMovesByOneStep() {
    val reach = 6
    var previous = burnInOffset(0, reach)
    assertEquals(0, previous.x)
    assertEquals(0, previous.y)
    for (tick in 1L..32L) {
      val next = burnInOffset(tick, reach)
      assertTrue(abs(next.x) <= reach && abs(next.y) <= reach)
      assertTrue(abs(next.x - previous.x) <= reach && abs(next.y - previous.y) <= reach)
      previous = next
    }
  }

  @Test
  fun walkRepeatsEveryEightRefreshes() {
    assertEquals(burnInOffset(3, 4), burnInOffset(11, 4))
  }

  @Test
  fun negativeTicksDoNotCrash() {
    burnInOffset(-1, 4)
  }
}
