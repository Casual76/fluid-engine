package dev.antigravity.fluidengine.wear.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidArcRowTest {

  @Test
  fun bottomRowRunsLeftToRight() {
    val angles = arcAngles(3, centerAngle = 90f, spacing = 45f)
    assertEquals(listOf(135f, 90f, 45f), angles)
    val xs = angles.map { arcPoint(100f, 100f, 70f, it).first }
    assertTrue(xs[0] < xs[1] && xs[1] < xs[2])
  }

  @Test
  fun topRowRunsLeftToRightToo() {
    val angles = arcAngles(3, centerAngle = 270f, spacing = 30f)
    assertEquals(listOf(240f, 270f, 300f), angles)
    val xs = angles.map { arcPoint(100f, 100f, 70f, it).first }
    assertTrue(xs[0] < xs[1] && xs[1] < xs[2])
  }

  @Test
  fun evenCountIsCentred() {
    assertEquals(listOf(112.5f, 67.5f), arcAngles(2, 90f, 45f))
  }

  @Test
  fun emptyRowHasNoAngles() {
    assertTrue(arcAngles(0, 90f, 45f).isEmpty())
  }

  @Test
  fun bottomPointIsBelowTheCentre() {
    val (x, y) = arcPoint(100f, 100f, 50f, 90f)
    assertEquals(100f, x, 1e-3f)
    assertEquals(150f, y, 1e-3f)
  }
}
