package dev.antigravity.fluidengine.ui.fluid

import org.junit.Assert.assertEquals
import org.junit.Test

class FluidSpokenTextTest {

  // "Fichte nasce nel 1752"
  private val words = listOf(
    FluidSpokenWord(start = 0, end = 6, startMs = 0, endMs = 1_000),
    FluidSpokenWord(start = 7, end = 12, startMs = 1_000, endMs = 2_000),
    FluidSpokenWord(start = 13, end = 16, startMs = 2_000, endMs = 2_500),
    FluidSpokenWord(start = 17, end = 21, startMs = 2_500, endMs = 3_000),
  )

  @Test
  fun `prima della prima parola non e' acceso niente`() {
    assertEquals(0, fluidSpokenChars(words, -1))
    assertEquals(0, fluidSpokenChars(words, 0))
  }

  @Test
  fun `dopo l'ultima parola e' acceso tutto`() {
    assertEquals(21, fluidSpokenChars(words, 3_000))
    assertEquals(21, fluidSpokenChars(words, 999_999))
  }

  @Test
  fun `dentro una parola il confine avanza in proporzione`() {
    // Meta' di "Fichte", sei caratteri: tre.
    assertEquals(3, fluidSpokenChars(words, 500))
    // Tre quarti di "nasce", cinque caratteri: sette piu' tre.
    assertEquals(10, fluidSpokenChars(words, 1_750))
  }

  @Test
  fun `al passaggio a una parola nuova si accende anche lo spazio prima`() {
    // Un millisecondo prima della fine, "Fichte" e' quasi tutta accesa ma non del tutto: il confine
    // scorre dentro la parola invece di scattare, che e' il punto.
    assertEquals(5, fluidSpokenChars(words, 1_000 - 1))
    // Appena comincia "nasce", l'accensione arriva al suo inizio: lo spazio in mezzo non resta velato.
    assertEquals(7, fluidSpokenChars(words, 1_000))
  }

  @Test
  fun `nel silenzio fra due parole resta accesa quella prima, tutta`() {
    val gapped = listOf(
      FluidSpokenWord(0, 4, 0, 500),
      FluidSpokenWord(5, 9, 3_000, 3_500),
    )
    assertEquals(4, fluidSpokenChars(gapped, 1_500))
    assertEquals(4, fluidSpokenChars(gapped, 2_999))
    assertEquals(5, fluidSpokenChars(gapped, 3_000))
  }

  @Test
  fun `senza parole non si accende niente, e una parola a durata zero non divide per zero`() {
    assertEquals(0, fluidSpokenChars(emptyList(), 1_000))
    val instant = listOf(FluidSpokenWord(0, 4, 1_000, 1_000))
    assertEquals(4, fluidSpokenChars(instant, 1_000))
    assertEquals(0, fluidSpokenChars(instant, 999))
  }
}
