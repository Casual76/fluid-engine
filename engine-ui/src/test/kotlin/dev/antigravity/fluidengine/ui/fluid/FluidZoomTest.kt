package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti dello zoom di un'immagine: quanto si puo' ingrandire, dove si puo' spingere, e dove spostarla per
 * tenere fermo il punto pizzicato. Il sistema di riferimento e' il centro del contenitore, lo stesso del
 * layer che li applica.
 */
class FluidZoomTest {

  private val screen = Size(400f, 800f)

  // --- scala ----------------------------------------------------------------------------------------

  @Test
  fun `the scale stays between one and the maximum`() {
    assertEquals(1f, FluidZoom.clampScale(0.4f), 0f)
    assertEquals(2.2f, FluidZoom.clampScale(2.2f), 0f)
    assertEquals(4f, FluidZoom.clampScale(9f), 0f)
    assertEquals(3f, FluidZoom.clampScale(9f, maxScale = 3f), 0f)
  }

  @Test
  fun `a maximum below one never shrinks the image under its fit`() {
    assertEquals(1f, FluidZoom.clampScale(2f, maxScale = 0.5f), 0f)
  }

  @Test
  fun `a pinch left a hair above one is not zoomed, so the pager keeps the finger`() {
    assertFalse(FluidZoom.isZoomed(1.004f))
    assertTrue(FluidZoom.isZoomed(1.2f))
  }

  // --- adattamento ----------------------------------------------------------------------------------

  @Test
  fun `a wide image fits the width of a tall screen`() {
    assertEquals(Size(400f, 200f), FluidZoom.fitSize(screen, aspectRatio = 2f))
  }

  @Test
  fun `a tall image fits the height of a wide container`() {
    assertEquals(Size(250f, 500f), FluidZoom.fitSize(Size(800f, 500f), aspectRatio = 0.5f))
  }

  @Test
  fun `without an aspect ratio the image is the whole container`() {
    assertEquals(screen, FluidZoom.fitSize(screen, null))
    assertEquals(screen, FluidZoom.fitSize(screen, 0f))
    assertEquals(screen, FluidZoom.fitSize(screen, -1f))
  }

  @Test
  fun `a container with no size is returned as it is`() {
    assertEquals(Size.Zero, FluidZoom.fitSize(Size.Zero, 2f))
  }

  // --- limiti dello spostamento ---------------------------------------------------------------------

  @Test
  fun `an image that fits has nowhere to be pushed`() {
    assertEquals(Offset.Zero, FluidZoom.maxPan(screen, screen, 1f))
  }

  @Test
  fun `the push is half of what the scaled image exceeds the container by, per side`() {
    assertEquals(Offset(200f, 400f), FluidZoom.maxPan(screen, screen, 2f))
  }

  @Test
  fun `a wide image zoomed has room to move sideways long before it has room vertically`() {
    // 400x200 a scala 2 e' 800x400 in un contenitore 400x800: di lato 200 per parte, in verticale niente
    // (400 < 800: resta al centro).
    val fit = FluidZoom.fitSize(screen, 2f)
    assertEquals(Offset(200f, 0f), FluidZoom.maxPan(screen, fit, 2f))
  }

  @Test
  fun `a push beyond the limit is brought back to it`() {
    val clamped = FluidZoom.clampPan(Offset(900f, -900f), screen, screen, 2f)
    assertEquals(Offset(200f, -400f), clamped)
  }

  @Test
  fun `a push inside the limit is left alone`() {
    assertEquals(Offset(50f, -30f), FluidZoom.clampPan(Offset(50f, -30f), screen, screen, 2f))
  }

  @Test
  fun `zooming back out to one brings the image back to the centre`() {
    assertEquals(Offset.Zero, FluidZoom.clampPan(Offset(180f, -300f), screen, screen, 1f))
  }

  // --- zoom attorno a un punto ----------------------------------------------------------------------

  /** Dove sta sullo schermo il punto `q` dell'immagine, con questa scala e questa traslazione. */
  private fun onScreen(q: Offset, scale: Float, pan: Offset) = Offset(scale * q.x + pan.x, scale * q.y + pan.y)

  @Test
  fun `the point under the fingers stays under the fingers while zooming`() {
    val scale = 1.5f
    val pan = Offset(40f, -25f)
    val focal = Offset(90f, 130f)
    val q = Offset((focal.x - pan.x) / scale, (focal.y - pan.y) / scale)
    val newScale = 3.2f
    val newPan = FluidZoom.zoomAbout(scale, pan, newScale, focal)
    val after = onScreen(q, newScale, newPan)
    assertEquals(focal.x, after.x, 1e-3f)
    assertEquals(focal.y, after.y, 1e-3f)
  }

  @Test
  fun `zooming about the centre does not move the image`() {
    assertEquals(Offset.Zero, FluidZoom.zoomAbout(1f, Offset.Zero, 3f, Offset.Zero))
  }

  @Test
  fun `zooming about a corner of the screen pulls the image the other way`() {
    // Pizzicando a destra del centro, l'immagine si sposta a sinistra: il punto sotto le dita resta li'.
    val pan = FluidZoom.zoomAbout(1f, Offset.Zero, 2f, Offset(100f, 0f))
    assertEquals(-100f, pan.x, 1e-3f)
    assertEquals(0f, pan.y, 1e-3f)
  }

  @Test
  fun `zooming out and then in about the same point comes back`() {
    val focal = Offset(-60f, 80f)
    val out = FluidZoom.zoomAbout(2f, Offset(10f, 20f), 1f, focal)
    val back = FluidZoom.zoomAbout(1f, out, 2f, focal)
    assertEquals(10f, back.x, 1e-3f)
    assertEquals(20f, back.y, 1e-3f)
  }

  // --- doppio tocco e gradini -----------------------------------------------------------------------

  @Test
  fun `a double tap on an unzoomed image goes to the tap scale, on a zoomed one back to one`() {
    assertEquals(2.5f, FluidZoom.doubleTapTarget(1f), 0f)
    assertEquals(1f, FluidZoom.doubleTapTarget(2.5f), 0f)
    assertEquals(1f, FluidZoom.doubleTapTarget(1.3f), 0f)
  }

  @Test
  fun `a double tap never goes beyond the maximum`() {
    assertEquals(2f, FluidZoom.doubleTapTarget(1f, maxScale = 2f), 0f)
  }

  @Test
  fun `the steps for the accessibility actions go one, tap scale, maximum`() {
    assertEquals(2.5f, FluidZoom.step(1f, zoomIn = true), 0f)
    assertEquals(4f, FluidZoom.step(2.5f, zoomIn = true), 0f)
    assertEquals(4f, FluidZoom.step(4f, zoomIn = true), 0f)
    assertEquals(2.5f, FluidZoom.step(4f, zoomIn = false), 0f)
    assertEquals(1f, FluidZoom.step(2.5f, zoomIn = false), 0f)
    assertEquals(1f, FluidZoom.step(1f, zoomIn = false), 0f)
  }

  @Test
  fun `a step from between two levels goes to the nearest one in that direction`() {
    assertEquals(2.5f, FluidZoom.step(1.7f, zoomIn = true), 0f)
    assertEquals(1f, FluidZoom.step(1.7f, zoomIn = false), 0f)
  }

  @Test
  fun `with a low maximum the levels collapse instead of repeating`() {
    assertEquals(2f, FluidZoom.step(1f, zoomIn = true, maxScale = 2f), 0f)
    assertEquals(2f, FluidZoom.step(2f, zoomIn = true, maxScale = 2f), 0f)
    assertEquals(1f, FluidZoom.step(2f, zoomIn = false, maxScale = 2f), 0f)
  }
}
