package dev.antigravity.fluidengine.ui.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val SCREEN = Size(1080f, 2200f)
private const val WIDTH = 800f
private const val HEIGHT = 300f

private fun place(anchor: Rect) = FluidCoachMarkPlacer.place(
  anchor = anchor,
  calloutWidth = WIDTH,
  calloutHeight = HEIGHT,
  screen = SCREEN,
  safeTop = 80f,
  safeBottom = 120f,
  margin = 40f,
  gap = 30f,
  pointerInset = 60f,
)

class FluidCoachMarkPlacementTest {

  @Test
  fun `sotto l'elemento quando c'e' posto`() {
    val anchor = Rect(400f, 500f, 700f, 620f)
    val placement = place(anchor)
    assertTrue(placement.below)
    assertEquals(650f, placement.y, 0.01f)
  }

  @Test
  fun `sopra l'elemento quando sotto non ci sta`() {
    val anchor = Rect(400f, 1900f, 700f, 2020f)
    val placement = place(anchor)
    assertFalse(placement.below)
    assertEquals(1570f, placement.y, 0.01f)
  }

  @Test
  fun `un elemento che riempie lo schermo non fa uscire il callout`() {
    val placement = place(Rect(0f, 100f, 1080f, 2100f))
    assertTrue("resta sotto il bordo alto", placement.y >= 120f)
    assertTrue("resta sopra il bordo basso", placement.y + HEIGHT <= SCREEN.height - 120f - 40f + 0.01f)
  }

  @Test
  fun `il callout resta dentro i margini anche agli angoli`() {
    val sinistra = place(Rect(0f, 300f, 120f, 400f))
    assertEquals(40f, sinistra.x, 0.01f)
    val destra = place(Rect(960f, 300f, 1080f, 400f))
    assertEquals(SCREEN.width - 40f - WIDTH, destra.x, 0.01f)
  }

  @Test
  fun `la punta insegue il centro dell'elemento senza uscire dagli angoli`() {
    val centrato = place(Rect(440f, 300f, 640f, 400f))
    assertEquals(WIDTH / 2f, centrato.pointerX, 0.01f)

    val alBordo = place(Rect(0f, 300f, 80f, 400f))
    assertEquals("non va oltre l'angolo", 60f, alBordo.pointerX, 0.01f)
    assertTrue(alBordo.pointerX <= WIDTH - 60f)

    val altroBordo = place(Rect(1000f, 300f, 1080f, 400f))
    assertTrue(altroBordo.pointerX in 60f..(WIDTH - 60f))
  }

  @Test
  fun `un callout piu' largo dello schermo si appoggia al margine invece di sparire`() {
    val placement = FluidCoachMarkPlacer.place(
      anchor = Rect(400f, 500f, 700f, 620f),
      calloutWidth = 1200f,
      calloutHeight = HEIGHT,
      screen = SCREEN,
      margin = 40f,
      gap = 30f,
      pointerInset = 60f,
    )
    assertEquals(40f, placement.x, 0.01f)
  }
}
