package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il ponte verso androidx.graphics.shapes: due anelli con la stessa parametrizzazione, dai quali
 * ogni fotogramma è un lerp. Qui si inchioda il contratto — stessa lunghezza, dentro il tetto dei
 * vertici, geometria agli estremi fedele alle forme — non la qualità estetica del matching, che è
 * della libreria.
 */
class MatchedRingsTest {

  @Test
  fun bothRingsShareOneParameterisationWithinTheVertexBudget() {
    val circle = FluidForm.circle(Offset(200f, 200f), 100f)
    val star = FluidFormPresets.star(Rect(100f, 100f, 300f, 300f))

    val rings = buildMatchedRings(circle, star)

    assertTrue(rings.count in 3..PhysicsMaxVertices)
    assertEquals(rings.count * 2, rings.start.size)
    assertEquals(rings.count * 2, rings.end.size)
  }

  @Test
  fun theStartRingTracesTheStartFormAndTheEndRingTheTarget() {
    val circle = FluidForm.circle(Offset(200f, 200f), 100f)
    val square = FluidForm.Slab(Rect(400f, 400f, 600f, 600f), FluidCornerRadii.Zero)

    val rings = buildMatchedRings(circle, square)

    // Ogni punto dell'anello di partenza sta sul cerchio (raggio 100 dal centro, entro tolleranza
    // di campionamento), ogni punto d'arrivo dentro il riquadro del quadrato.
    for (i in 0 until rings.count) {
      val dx = rings.start[i * 2] - 200f
      val dy = rings.start[i * 2 + 1] - 200f
      val r = kotlin.math.hypot(dx, dy)
      assertTrue("punto di partenza fuori dal cerchio: r=$r", r in 90f..110f)

      val x = rings.end[i * 2]
      val y = rings.end[i * 2 + 1]
      assertTrue("punto d'arrivo fuori dal quadrato: ($x,$y)", x in 399f..601f && y in 399f..601f)
    }
  }

  @Test
  fun aFreehandPolyTravelsWithoutThrowing() {
    val scribble = FluidFormPresets.fromFreehand(
      buildList {
        for (i in 0..60) {
          val angle = Math.toRadians(i * 11.0)
          val radius = 80f + 40f * ((i % 7) - 3)
          add(Offset(300f + radius * Math.cos(angle).toFloat(), 300f + radius * Math.sin(angle).toFloat()))
        }
      },
    )
    val circle = FluidForm.circle(Offset(300f, 300f), 120f)

    val rings = buildMatchedRings(scribble, circle)
    assertTrue(rings.count in 3..PhysicsMaxVertices)
  }

  @Test
  fun restRingStaysInsideTheFormsFrame() {
    val trapezoid = FluidFormPresets.trapezoid(Rect(0f, 0f, 400f, 300f))
    val ring = restRing(trapezoid)
    assertTrue(ring.size / 2 in 3..PhysicsMaxVertices)
    for (i in 0 until ring.size / 2) {
      assertTrue(ring[i * 2] in -1f..401f)
      assertTrue(ring[i * 2 + 1] in -1f..301f)
    }
  }

  @Test
  fun ringAlignmentRotatesToTheTopWithoutChangingTheOrder() {
    val ring = listOf(Offset(0f, 100f), Offset(50f, 0f), Offset(100f, 100f))
    val aligned = alignRingStart(ring)
    assertEquals(Offset(50f, 0f), aligned[0])
    assertEquals(listOf(Offset(50f, 0f), Offset(100f, 100f), Offset(0f, 100f)), aligned)
  }
}
