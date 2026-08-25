package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidFormGeometryTest {

  @Test
  fun rdpKeepsEndpointsAndDropsTheNoiseInBetween() {
    // Una linea quasi retta con un dente sotto tolleranza: restano solo gli estremi.
    val nearlyStraight = listOf(
      Offset(0f, 0f),
      Offset(50f, 0.4f),
      Offset(100f, -0.3f),
      Offset(150f, 0.2f),
      Offset(200f, 0f),
    )
    val simplified = rdpSimplify(nearlyStraight, tolerance = 2f)
    assertEquals(listOf(Offset(0f, 0f), Offset(200f, 0f)), simplified)

    // Lo stesso tracciato con un vertice vero sopra tolleranza: il vertice sopravvive.
    val bent = listOf(Offset(0f, 0f), Offset(100f, 40f), Offset(200f, 0f))
    assertEquals(bent, rdpSimplify(bent, tolerance = 2f))
  }

  @Test
  fun resamplingIsUniformAlongThePerimeter() {
    val square = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))
    val ring = resampleClosed(square, FluidFormPresets.FreehandVertices)

    assertEquals(FluidFormPresets.FreehandVertices, ring.size)
    // Perimetro 400, 48 campioni: ogni passo deve valere 400/48, sempre.
    val step = 400f / FluidFormPresets.FreehandVertices
    for (i in ring.indices) {
      val a = ring[i]
      val b = ring[(i + 1) % ring.size]
      assertEquals(
        "passo $i non uniforme",
        step,
        hypot(b.x - a.x, b.y - a.y),
        0.01f,
      )
    }
  }

  @Test
  fun windingIsNormalisedWhicheverWayTheFingerWent() {
    val clockwise = listOf(Offset(0f, 0f), Offset(100f, 0f), Offset(100f, 100f), Offset(0f, 100f))
    val counterClockwise = clockwise.reversed()

    assertTrue(signedArea(clockwise) > 0f)
    assertTrue(signedArea(counterClockwise) < 0f)
    assertTrue(signedArea(ensureClockwise(counterClockwise)) > 0f)
    // Un anello già orario non viene toccato: il punto di partenza è parte della parametrizzazione.
    assertEquals(clockwise, ensureClockwise(clockwise))
  }

  @Test
  fun freehandProducesANormalisedRingOfTheDeclaredSize() {
    // Uno scarabocchio concavo vero: una C.
    val stroke = buildList {
      for (i in 0..40) {
        val angle = Math.toRadians(40.0 + i * 7.0)
        add(Offset(200f + 150f * Math.cos(angle).toFloat(), 200f + 150f * Math.sin(angle).toFloat()))
      }
      add(Offset(220f, 200f))
    }
    val form = FluidFormPresets.fromFreehand(stroke)

    assertTrue(form is FluidForm.Poly)
    form as FluidForm.Poly
    assertEquals(FluidFormPresets.FreehandVertices, form.vertices.size)
    for (v in form.vertices) {
      assertTrue("vertice fuori dal frame: $v", v.x in -0.001f..1.001f && v.y in -0.001f..1.001f)
    }
    assertTrue("l'anello dev'essere orario", signedArea(form.vertices) > 0f)
  }

  @Test
  fun aTapDegradesToACircleInsteadOfAnEmptyShape() {
    val tap = listOf(Offset(100f, 100f), Offset(101f, 100f), Offset(100f, 101f))
    val form = FluidFormPresets.fromFreehand(tap)

    assertTrue(form is FluidForm.Slab)
    form as FluidForm.Slab
    // Un cerchio: i quattro raggi valgono metà del lato.
    assertEquals(form.frame.width / 2f, form.cornerRadii.topLeft, 0.01f)
    assertTrue(form.frame.width >= 96f)
  }

  @Test
  fun distinctPointCountIgnoresJitterWithinTolerance() {
    val jittery = listOf(
      Offset(0f, 0f),
      Offset(0.2f, 0.1f),
      Offset(0.1f, 0.3f),
      Offset(50f, 0f),
      Offset(50.2f, 0.2f),
    )
    assertEquals(2, countDistinct(jittery, tolerance = 1f))
  }
}
