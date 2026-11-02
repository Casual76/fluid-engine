package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le regole di un avviso con un campo, e della riga di scelta che non si prova senza comporre. */
class FluidPromptAlertTest {

  @Test
  fun `un campo vuoto o di soli spazi non ha sbagliato, non ha ancora scritto`() {
    assertEquals(FluidPromptValidation.Incomplete, fluidPromptNotBlank(""))
    assertEquals(FluidPromptValidation.Incomplete, fluidPromptNotBlank("   "))
    assertEquals(FluidPromptValidation.Valid, fluidPromptNotBlank("Cucina"))
  }

  @Test
  fun `il testo si ritaglia solo se si chiede, ed e' lo stesso per validare e per confermare`() {
    assertEquals("Cucina", fluidPromptText("  Cucina ", trim = true))
    assertEquals("  Cucina ", fluidPromptText("  Cucina ", trim = false))
    // Il caso per cui la funzione e' una sola: "  " ritagliato e' vuoto, quindi non e' valido.
    assertEquals(FluidPromptValidation.Incomplete, fluidPromptNotBlank(fluidPromptText("  ", trim = true)))
  }

  @Test
  fun `la selezione di apertura e' tutto il testo, o il cursore in fondo`() {
    assertEquals(TextRange(0, 6), fluidPromptInitialSelection("Cucina", selectAll = true))
    assertEquals(TextRange(6), fluidPromptInitialSelection("Cucina", selectAll = false))
    assertTrue(fluidPromptInitialSelection("", selectAll = true).collapsed)
  }

  @Test
  fun `un valore fuori misura porta la sua frase`() {
    val validate: (String) -> FluidPromptValidation = { text ->
      val value = text.replace(',', '.').toDoubleOrNull()
      when {
        text.isBlank() -> FluidPromptValidation.Incomplete
        value == null || value !in 20.0..140.0 -> FluidPromptValidation.Invalid("Tra 20 e 140")
        else -> FluidPromptValidation.Valid
      }
    }
    assertEquals(FluidPromptValidation.Incomplete, validate(""))
    assertEquals(FluidPromptValidation.Invalid("Tra 20 e 140"), validate("200"))
    assertEquals(FluidPromptValidation.Valid, validate("54,3"))
  }

  @Test
  fun `la riga di scelta vibra solo se la scelta si sposta`() {
    assertTrue(fluidChoicePlaysHaptic(alreadySelected = false))
    assertFalse(fluidChoicePlaysHaptic(alreadySelected = true))
  }
}
