package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Il bordo della goccia (3.0): una fascia di triangoli col profilo del bordo a riposo, non un tratto
 * su un path. Il profilo e' quello che la lastra ferma registra (tratto doppio, sfocato di mezza
 * larghezza, ritagliato sulla sagoma), quindi quando la goccia si posa non c'e' gradino.
 */
class DropRimTest {

  // La ricetta di serie a 2,625: larghezza 0,8 dp -> banda di 3 px, sfocatura di 0,4 dp.
  private val band = 3f
  private val sigma = blurMaskSigma(0.4f * 2.625f)

  @Test
  fun theSigmaIsSkiasConversionOfTheBlurRadius() {
    assertEquals(1.05f * 0.57735f + 0.5f, sigma, 0.0001f)
    assertEquals(0f, blurMaskSigma(0f), 0f)
  }

  @Test
  fun theProfileIsTheBlurredDoubleStrokeClippedOnTheShape() {
    // Fuori dalla sagoma niente; sul contorno meta' (l'antialias del ritaglio); dentro la banda pieno;
    // alla fine della banda meta' (la sfocatura centrata sul bordo del tratto); oltre si spegne.
    assertEquals(0f, restRimProfile(-0.5f, band, sigma), 0f)
    assertEquals(0f, restRimProfile(-3f, band, sigma), 0f)
    assertEquals(0.5f, restRimProfile(0f, band, sigma), 0.01f)
    assertTrue(restRimProfile(1f, band, sigma) > 0.95f)
    assertEquals(0.5f, restRimProfile(band, band, sigma), 0.01f)
    assertTrue(restRimProfile(band + 3f * sigma, band, sigma) < 0.01f)
    // Senza sfocatura e' la banda dura.
    assertEquals(1f, restRimProfile(2f, band, 0f), 0f)
    assertEquals(0f, restRimProfile(3.5f, band, 0f), 0f)
  }

  @Test
  fun theProfileFallsMonotonicallyFromTheEdgeInwards() {
    var last = restRimProfile(0.5f, band, sigma)
    var u = 1f
    while (u < 8f) {
      val now = restRimProfile(u, band, sigma)
      assertTrue("a $u px risale: $now > $last", now <= last + 1e-6f)
      last = now
      u += 0.25f
    }
  }

  @Test
  fun theMeshRingsRunParallelInsideTheContourAndFadeOutAtBothEnds() {
    // Il contorno di una lastra a riposo, come lo disegna la goccia.
    val slab = FluidForm.Slab(Rect(100f, 200f, 761f, 756f), FluidCornerRadii.all(68f))
    val drop = LiquidDrop()
    val ring = FloatArray(DropRenderPoints * 2)
    buildDropRing(slab, drop, DropRenderPoints, ring, RoundedRectPerimeter(), FloatArray(DropRenderPoints))
    val mesh = DropRimMesh()
    assertTrue(mesh.build(ring, DropRenderPoints, slab.frame.left, slab.frame.top, band, sigma))
    assertTrue("anelli: ${mesh.rings}", mesh.rings in 6..20)
    assertEquals(0f, mesh.alpha(0), 0f)
    assertEquals(0f, mesh.alpha(mesh.rings - 1), 0f)
    assertTrue(mesh.depth(mesh.rings - 1) >= band + 3f * sigma)
    assertEquals(mesh.rings * DropRenderPoints * 2, mesh.vertexFloats)
    assertEquals((mesh.rings - 1) * DropRenderPoints * 6, mesh.indexCount)

    // Ogni vertice dell'anello k sta alla sua profondita' dentro il rettangolo arrotondato (nelle
    // coordinate del riquadro), a meno della normale per corda: un decimo di pixel.
    val hw = slab.frame.width / 2f
    val hh = slab.frame.height / 2f
    var worst = 0f
    for (k in 0 until mesh.rings) {
      for (i in 0 until DropRenderPoints) {
        val v = k * DropRenderPoints + i
        val x = mesh.positions[v * 2]
        val y = mesh.positions[v * 2 + 1]
        val d = sdRoundedRect(x, y, hw, hh, hw, hh, 68f)
        worst = maxOf(worst, abs(d + mesh.depth(k)))
      }
    }
    assertTrue("un anello si stacca dalla sua profondita' di $worst px", worst < 0.1f)
    // Gli alfa dei vertici sono il profilo, in ottavi di bit.
    for (k in 0 until mesh.rings) {
      val a = (mesh.colors[k * DropRenderPoints] ushr 24) / 255f
      assertEquals(mesh.alpha(k), a, 0.5f / 255f + 1e-6f)
    }
  }

  @Test
  fun aDegenerateContourDrawsNothing() {
    val mesh = DropRimMesh()
    assertTrue(!mesh.build(FloatArray(4), 2, 0f, 0f, band, sigma))
    assertTrue(!mesh.build(FloatArray(8), 4, 0f, 0f, 0f, sigma))
  }
}
