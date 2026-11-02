package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidPagerDotsTest {

  @Test
  fun `the current dot is full and a page away is nothing`() {
    assertEquals(1f, fluidPagerDotWeight(2, 2f), 0f)
    assertEquals(0f, fluidPagerDotWeight(3, 2f), 0f)
    assertEquals(0f, fluidPagerDotWeight(0, 2f), 0f)
  }

  @Test
  fun `halfway through a swipe two dots are half current each`() {
    assertEquals(0.5f, fluidPagerDotWeight(2, 2.5f), 1e-6f)
    assertEquals(0.5f, fluidPagerDotWeight(3, 2.5f), 1e-6f)
  }

  @Test
  fun `the weight follows the finger without a step`() {
    // Da pagina 2 a pagina 3 in dieci passi (contati, non accumulati: la somma di float sfora il 3).
    var previous = fluidPagerDotWeight(3, 2f)
    for (step in 1..10) {
      val weight = fluidPagerDotWeight(3, 2f + step / 10f)
      assertTrue("il peso deve salire con il dito", weight >= previous)
      previous = weight
    }
    assertEquals(1f, fluidPagerDotWeight(3, 3f), 1e-6f)
  }

  @Test
  fun `when every dot fits the row does not scroll`() {
    assertEquals(0f, fluidPagerDotsScroll(pageCount = 5, position = 4f, maxVisible = 7), 0f)
    assertEquals(0f, fluidPagerDotsScroll(pageCount = 7, position = 6f, maxVisible = 7), 0f)
  }

  @Test
  fun `the row keeps the current dot in the middle while there is row both sides`() {
    // 20 pagine, 7 visibili: il centro e' lo slot 3, quindi a pagina 10 la fila e' scorsa di 7.
    assertEquals(0f, fluidPagerDotsScroll(20, 2f, 7), 0f)
    assertEquals(0f, fluidPagerDotsScroll(20, 3f, 7), 0f)
    assertEquals(7f, fluidPagerDotsScroll(20, 10f, 7), 0f)
    assertEquals(7.5f, fluidPagerDotsScroll(20, 10.5f, 7), 0f)
  }

  @Test
  fun `the row stops at the last page and the current dot walks to the edge`() {
    assertEquals(13f, fluidPagerDotsScroll(20, 19f, 7), 0f)
    assertEquals(13f, fluidPagerDotsScroll(20, 25f, 7), 0f)
  }

  @Test
  fun `no taper when every dot fits`() {
    assertEquals(1f, fluidPagerDotTaper(0f, maxVisible = 7, scroll = 0f, pageCount = 5), 0f)
    assertEquals(1f, fluidPagerDotTaper(6f, 7, 0f, 7), 0f)
  }

  @Test
  fun `at the start the first dot is whole and the far edge tapers`() {
    assertEquals(1f, fluidPagerDotTaper(0f, 7, scroll = 0f, pageCount = 20), 0f)
    assertEquals(0.35f, fluidPagerDotTaper(6f, 7, scroll = 0f, pageCount = 20), 1e-6f)
  }

  @Test
  fun `at the end the last dot is whole and the first tapers`() {
    assertEquals(1f, fluidPagerDotTaper(6f, 7, scroll = 13f, pageCount = 20), 0f)
    assertEquals(0.35f, fluidPagerDotTaper(0f, 7, scroll = 13f, pageCount = 20), 1e-6f)
  }

  @Test
  fun `the middle of a long row is never tapered`() {
    assertEquals(1f, fluidPagerDotTaper(3f, 7, scroll = 5f, pageCount = 20), 0f)
  }

  @Test
  fun `a dot that left the row on the leaving side is gone`() {
    assertEquals(0f, fluidPagerDotTaper(-1f, 7, scroll = 5f, pageCount = 20), 0f)
    assertEquals(0f, fluidPagerDotTaper(7f, 7, scroll = 5f, pageCount = 20), 0f)
  }

  @Test
  fun `the taper does not jump when the row starts to move`() {
    // Il primo punto passa da intero a ridotto in proporzione a quanto la fila e' scorsa, non di colpo.
    val still = fluidPagerDotTaper(0f, 7, scroll = 0f, pageCount = 20)
    val moving = fluidPagerDotTaper(-0.01f, 7, scroll = 0.01f, pageCount = 20)
    assertTrue(still - moving < 0.02f)
  }

  @Test
  fun `the row shows at most the visible count`() {
    assertEquals(5, fluidPagerDotsShown(5, 7))
    assertEquals(7, fluidPagerDotsShown(20, 7))
    assertEquals(0, fluidPagerDotsShown(-2, 7))
    assertEquals(1, fluidPagerDotsShown(20, 0))
  }
}
