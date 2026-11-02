package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I conti dello scorrimento rapido, senza lista: dove sta la maniglia, dove porta il dito, quando c'e'.
 * La regola che li tiene insieme e' una sola: dito e maniglia restano 1:1, perche' durante il gesto la
 * maniglia non legge la lista.
 */
class FluidFastScrollTest {

  @Test
  fun `the last index that can sit at the top leaves the visible rows at the end`() {
    // 100 righe, 12 a schermo: oltre la 88 la lista e' finita.
    assertEquals(88, FluidFastScroll.maxIndex(totalItems = 100, visibleItems = 12))
  }

  @Test
  fun `a list that fits on screen has nothing to scroll`() {
    assertEquals(0, FluidFastScroll.maxIndex(10, 10))
    assertEquals(0, FluidFastScroll.maxIndex(5, 12))
    assertEquals(0f, FluidFastScroll.fraction(0f, 5, 12), 0f)
  }

  @Test
  fun `the part of the first row that has gone past counts, so a tall row does not make steps`() {
    assertEquals(3f, FluidFastScroll.fractionalFirstIndex(3, 0, 200), 0f)
    assertEquals(3.25f, FluidFastScroll.fractionalFirstIndex(3, 50, 200), 1e-6f)
    // Uno scarto oltre la riga (una misura vecchia) non porta a 4: tenuto a uno.
    assertEquals(4f, FluidFastScroll.fractionalFirstIndex(3, 900, 200), 0f)
    // Una riga senza altezza non ha una parte.
    assertEquals(3f, FluidFastScroll.fractionalFirstIndex(3, 50, 0), 0f)
  }

  @Test
  fun `the position is zero at the top and one at the end`() {
    assertEquals(0f, FluidFastScroll.fraction(0f, 100, 12), 0f)
    assertEquals(1f, FluidFastScroll.fraction(88f, 100, 12), 0f)
    assertEquals(0.5f, FluidFastScroll.fraction(44f, 100, 12), 1e-6f)
  }

  @Test
  fun `the position is held between zero and one`() {
    assertEquals(1f, FluidFastScroll.fraction(500f, 100, 12), 0f)
    assertEquals(0f, FluidFastScroll.fraction(-4f, 100, 12), 0f)
  }

  @Test
  fun `a fraction leads to an index and the index back to the same fraction`() {
    for (index in listOf(0, 1, 17, 44, 87, 88)) {
      val fraction = FluidFastScroll.fraction(index.toFloat(), 100, 12)
      assertEquals(index, FluidFastScroll.indexForFraction(fraction, 100, 12))
    }
  }

  @Test
  fun `the ends of the track lead to the first and the last index`() {
    assertEquals(0, FluidFastScroll.indexForFraction(0f, 100, 12))
    assertEquals(88, FluidFastScroll.indexForFraction(1f, 100, 12))
    assertEquals(88, FluidFastScroll.indexForFraction(3f, 100, 12))
    assertEquals(0, FluidFastScroll.indexForFraction(-3f, 100, 12))
  }

  // --- il dito e' 1:1 -------------------------------------------------------------------------------

  @Test
  fun `the thumb moves as far as the finger, from where it was taken`() {
    // Maniglia presa a meta' corsa (0,5), corsa di 400 px: 100 px di dito sono un quarto di pista.
    assertEquals(0.75f, FluidFastScroll.scrubbedFraction(0.5f, 100f, 400f), 1e-6f)
    assertEquals(0.25f, FluidFastScroll.scrubbedFraction(0.5f, -100f, 400f), 1e-6f)
  }

  @Test
  fun `taking the thumb does not move it`() {
    assertEquals(0.3f, FluidFastScroll.scrubbedFraction(0.3f, 0f, 400f), 0f)
  }

  @Test
  fun `a finger that goes past the end and comes back does not drag the thumb with it`() {
    // Gli spostamenti sono accumulati: andare oltre la fine tiene la maniglia in fondo, e tornare indietro la
    // rimette in movimento solo quando il dito ripassa dal punto da cui e' uscito.
    val beyond = FluidFastScroll.scrubbedFraction(0.9f, 200f, 400f)
    assertEquals(1f, beyond, 0f)
    val backToTheEdge = FluidFastScroll.scrubbedFraction(0.9f, 40f, 400f)
    assertEquals(1f, backToTheEdge, 1e-6f)
    val inside = FluidFastScroll.scrubbedFraction(0.9f, 20f, 400f)
    assertEquals(0.95f, inside, 1e-6f)
  }

  @Test
  fun `the position is always inside the track`() {
    assertEquals(1f, FluidFastScroll.scrubbedFraction(0.5f, 10_000f, 400f), 0f)
    assertEquals(0f, FluidFastScroll.scrubbedFraction(0.5f, -10_000f, 400f), 0f)
  }

  @Test
  fun `a track with no travel keeps the thumb where it is`() {
    assertEquals(0.4f, FluidFastScroll.scrubbedFraction(0.4f, 300f, 0f), 0f)
  }

  @Test
  fun `the thumb travels the track minus itself, at least a pixel`() {
    assertEquals(500f - 52f, FluidFastScroll.travel(500f, 52f), 0f)
    assertEquals(1f, FluidFastScroll.travel(40f, 52f), 0f)
  }

  // --- quando c'e' ----------------------------------------------------------------------------------

  @Test
  fun `a short list does not get a thumb, a long one that really scrolls does`() {
    assertFalse(FluidFastScroll.isScrollable(totalItems = 12, visibleItems = 8, minItems = 30))
    assertTrue(FluidFastScroll.isScrollable(totalItems = 400, visibleItems = 12, minItems = 30))
  }

  @Test
  fun `a list that fits on screen never gets a thumb whatever the minimum`() {
    assertFalse(FluidFastScroll.isScrollable(totalItems = 40, visibleItems = 40, minItems = 0))
  }

  // --- l'etichetta ----------------------------------------------------------------------------------

  @Test
  fun `a tick plays when the finger reaches a label it was not on`() {
    assertTrue(FluidFastScroll.labelCrossed("Marzo 2024", "Aprile 2024"))
  }

  @Test
  fun `no tick for the same label, and none when there is no label`() {
    assertFalse(FluidFastScroll.labelCrossed("Marzo 2024", "Marzo 2024"))
    assertFalse(FluidFastScroll.labelCrossed("Marzo 2024", null))
  }

  @Test
  fun `the first label after a gap with none is a crossing`() {
    assertTrue(FluidFastScroll.labelCrossed(null, "M"))
  }
}
