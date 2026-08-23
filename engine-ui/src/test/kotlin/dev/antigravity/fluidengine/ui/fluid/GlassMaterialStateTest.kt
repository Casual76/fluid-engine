package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Test

class GlassMaterialStateTest {

  @Test
  fun nextGlassFrameTick_advancesAndWrapsWithoutRepeatingThePublishedValue() {
    assertEquals(1, nextGlassFrameTick(0))
    assertEquals(42, nextGlassFrameTick(41))
    assertEquals(0, nextGlassFrameTick(Int.MAX_VALUE))
  }
}
