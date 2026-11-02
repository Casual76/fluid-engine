package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidLiveDotTest {

  @Test
  fun `the pulse runs only when asked and when motion is allowed`() {
    assertTrue(fluidPulseEnabled(active = true, policy = FluidMotionPolicy.Default))
    assertFalse(fluidPulseEnabled(active = false, policy = FluidMotionPolicy.Default))
  }

  @Test
  fun `reduced motion stops the pulse whatever the caller asks`() {
    val reduced = FluidMotionPolicy(durationScale = 0f)
    assertFalse(fluidPulseEnabled(active = true, policy = reduced))
    assertFalse(fluidPulseEnabled(active = false, policy = reduced))
  }

  @Test
  fun `a slowed animator scale is still motion`() {
    assertTrue(fluidPulseEnabled(true, FluidMotionPolicy(durationScale = 0.5f)))
  }

  @Test
  fun `the phase is where the clock finds the dot, inside the cycle`() {
    val cycle = FluidMotion.PulseMillis * 2
    assertEquals(0, fluidPulsePhaseOffsetMillis(0L, cycle))
    assertEquals(900, fluidPulsePhaseOffsetMillis(2_700L, cycle))
    assertEquals(0, fluidPulsePhaseOffsetMillis(3_600L, cycle))
  }

  @Test
  fun `two dots that enter at different times start in phase with the clock`() {
    val cycle = FluidMotion.PulseMillis * 2
    val first = 1_000_123L
    val second = first + 4_321L
    val a = fluidPulsePhaseOffsetMillis(first, cycle)
    val b = fluidPulsePhaseOffsetMillis(second, cycle)
    // Dopo che il secondo e' entrato, da 4321 ms, il primo ha fatto lo stesso cammino: sono allo stesso punto.
    assertEquals(b, Math.floorMod(a + 4_321, cycle))
  }

  @Test
  fun `a clock before zero or a broken cycle never produces a negative offset`() {
    assertEquals(1_700, fluidPulsePhaseOffsetMillis(-100L, 1_800))
    assertEquals(0, fluidPulsePhaseOffsetMillis(500L, 0))
    assertEquals(0, fluidPulsePhaseOffsetMillis(500L, -5))
  }

  @Test
  fun `the shared token is the one most of the apps already wrote by hand`() {
    assertEquals(900, FluidMotion.PulseMillis)
    assertTrue(FluidMotion.PulseLowAlpha in 0.2f..0.5f)
  }
}
