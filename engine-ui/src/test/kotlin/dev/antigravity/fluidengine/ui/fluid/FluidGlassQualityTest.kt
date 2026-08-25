package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidGlassQualityTest {

  @Test
  fun aStillPageGetsTheWholeMaterial() {
    assertEquals(1f, FluidGlassQuality.targetLevel(0f), 0f)
    assertEquals(1f, FluidGlassQuality.targetLevel(FluidGlassQuality.FullQualityVelocity), 0f)
  }

  @Test
  fun aFlingIsHeldAtTheFloorRatherThanTakenToNothing() {
    // Zero would make the glass *disappear* while a finger moves, and a surface that comes and
    // goes with the gesture is far more visible than one that thins out.
    val fast = FluidGlassQuality.targetLevel(FluidGlassQuality.MinQualityVelocity * 4f)
    assertEquals(FluidGlassQuality.ScrollingFloor, fast, 0f)
    assertTrue(fast > 0f)
  }

  @Test
  fun theRampIsMonotonic() {
    var previous = 1f
    var speed = FluidGlassQuality.FullQualityVelocity
    while (speed <= FluidGlassQuality.MinQualityVelocity) {
      val level = FluidGlassQuality.targetLevel(speed)
      assertTrue("il livello deve solo scendere con la velocita'", level <= previous + 0.0001f)
      previous = level
      speed += 4f
    }
  }

  @Test
  fun aMalformedVelocityIsReadAsStillRatherThanAsFast() {
    // The one direction that is safe to be wrong in: a NaN must not strip the material off a page
    // that is standing still.
    assertEquals(1f, FluidGlassQuality.targetLevel(Float.NaN), 0f)
  }

  @Test
  fun qualityFallsAtOnceAndComesBackGradually() {
    val quality = FluidGlassQuality()

    quality.onScrollDelta(FluidGlassQuality.MinQualityVelocity * 2f)
    assertEquals(FluidGlassQuality.ScrollingFloor, quality.level, 0.0001f)

    // One slow frame must not restore the whole material: the page has just stopped, which is
    // exactly when the eye is on it, and a step change there is what reads as a flicker.
    quality.onScrollDelta(0f)
    assertTrue(quality.level > FluidGlassQuality.ScrollingFloor)
    assertTrue(quality.level < 1f)

    repeat(200) { quality.onScrollDelta(0f) }
    assertEquals(1f, quality.level, 0.01f)
  }

  @Test
  fun theEndOfAFlingRestoresTheMaterialOutright() {
    val quality = FluidGlassQuality()
    quality.onScrollDelta(FluidGlassQuality.MinQualityVelocity * 2f)

    quality.onSettled()

    assertEquals(1f, quality.level, 0f)
  }
}
