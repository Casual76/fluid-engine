package dev.antigravity.fluidengine.wear.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidWearAccentTest {

  @Test
  fun aNearBlackCoverColourIsLiftedUntilItShows() {
    val lifted = liftForBlack(Color(0xFF1A0E0E))
    assertTrue(lifted.luminance() > 0.15f)
    // Still the same hue family: red stays the strongest channel.
    assertTrue(lifted.red > lifted.green && lifted.red > lifted.blue)
  }

  @Test
  fun aBrightColourIsLeftAlone() {
    val bright = Color(0xFFFFC857)
    assertEquals(bright, liftForBlack(bright))
  }
}
