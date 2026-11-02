package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidSearchFieldTest {

  private val none = "Nessun risultato"

  @Test
  fun `counter is nothing while the query is blank`() {
    assertNull(fluidSearchCounter("", 0, 0, none))
    assertNull(fluidSearchCounter("   ", 3, 12, none))
  }

  @Test
  fun `counter writes the one-based position over the total`() {
    assertEquals("1/12", fluidSearchCounter("ciao", 0, 12, none))
    assertEquals("3/12", fluidSearchCounter("ciao", 2, 12, none))
    assertEquals("12/12", fluidSearchCounter("ciao", 11, 12, none))
  }

  @Test
  fun `counter says there is nothing when a word finds nothing`() {
    assertEquals(none, fluidSearchCounter("xyz", 0, 0, none))
  }

  @Test
  fun `counter never writes a position past the total`() {
    // La lista dei risultati si e' accorciata prima che l'app aggiornasse la posizione.
    assertEquals("12/12", fluidSearchCounter("ciao", 40, 12, none))
    assertEquals("1/12", fluidSearchCounter("ciao", -3, 12, none))
  }

  @Test
  fun `width runs from the circle to the full room with the progress`() {
    assertEquals(52, fluidSearchWidth(collapsedPx = 52, expandedPx = 800, progress = 0f))
    assertEquals(800, fluidSearchWidth(52, 800, 1f))
    assertEquals(426, fluidSearchWidth(52, 800, 0.5f))
  }

  @Test
  fun `width stays inside the two ends whatever the spring does`() {
    // La molla della casa supera di un soffio il traguardo: la larghezza no.
    assertEquals(800, fluidSearchWidth(52, 800, 1.04f))
    assertEquals(52, fluidSearchWidth(52, 800, -0.02f))
  }

  @Test
  fun `width never drops under the circle when there is no room`() {
    assertEquals(52, fluidSearchWidth(collapsedPx = 52, expandedPx = 30, progress = 1f))
  }

  @Test
  fun `content fades in late and out first`() {
    assertEquals(0f, fluidSearchContentAlpha(0f), 0f)
    assertEquals(0f, fluidSearchContentAlpha(0.2f), 0f)
    assertEquals(1f, fluidSearchContentAlpha(0.8f), 0f)
    assertEquals(1f, fluidSearchContentAlpha(1f), 0f)
    assertTrue(fluidSearchContentAlpha(0.4f) < fluidSearchContentAlpha(0.6f))
  }

  @Test
  fun `the magnifier sits at the centre of the closed circle`() {
    val d = FluidSearchFieldDefaults
    // Il cerchio che diventa campo non sposta la lente: margine a sinistra == margine a destra da chiuso.
    assertEquals(d.Height, d.IconInset * 2 + d.IconSize)
  }

  @Test
  fun `default labels are the engine's italian`() {
    val labels = FluidSearchLabels()
    assertEquals("Cerca", labels.search)
    assertEquals("3 di 12", labels.counterSpoken(3, 12))
  }
}
