package dev.antigravity.fluidengine.viewer.ar

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArPlacementTest {
  @Test
  fun placesAtFileScaleAndRestsOnThePlane() {
    val bounds = ArPlacement(floatArrayOf(1f, 2f, 3f), floatArrayOf(.5f, 1f, .5f))
    val result = FloatArray(16)
    bounds.transformModel(floatArrayOf(5f, .4f, -2f), result)
    assertEquals(1f, result[0], .0001f)
    assertEquals(.4f, result[13] + 1f, .0001f)
    assertEquals(5f, result[12] + 1f, .0001f)
    assertEquals(-2f, result[14] + 3f, .0001f)
  }

  @Test
  fun scalesAndRotatesAboutTheBaseAndMovesOnThePlane() {
    val bounds = ArPlacement(floatArrayOf(0f, 2f, 0f), floatArrayOf(.5f, 1f, .5f))
    val result = FloatArray(16)
    bounds.transform(200f, (Math.PI / 2).toFloat())
    bounds.move(2f, -3f)
    bounds.transformModel(floatArrayOf(0f, 1f, 0f), result)
    assertEquals(10f, bounds.scale, .0001f)
    assertEquals(-10f, result[2], .0001f)
    assertEquals(1f, result[13] + 10f, .0001f)
    assertEquals(2f, result[12], .0001f)
    assertEquals(-3f, result[14], .0001f)
  }

  @Test
  fun intersectsOnlyDownwardRaysInReach() {
    val camera = floatArrayOf(0f, 2f, 0f)
    assertArrayEquals(floatArrayOf(2f, -2f), horizontalIntersection(camera, floatArrayOf(1f, -1f, -1f), 0f), .0001f)
    assertNull(horizontalIntersection(camera, floatArrayOf(1f, 0f, -1f), 0f))
    assertNull(horizontalIntersection(camera, floatArrayOf(1f, -.01f, -1f), 0f))
  }
}
