package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class FluidTextEditTest {

  @Test
  fun `inserire in un campo vuoto lascia il cursore dopo lo snippet`() {
    val out = FluidTextEdit.insert(TextFieldValue(""), "- ")
    assertEquals("- ", out.text)
    assertEquals(TextRange(2), out.selection)
  }

  @Test
  fun `inserire a meta' riga scrive al cursore, non in fondo`() {
    val out = FluidTextEdit.insert(TextFieldValue("ciao mondo", TextRange(5)), "bel ")
    assertEquals("ciao bel mondo", out.text)
    assertEquals(TextRange(9), out.selection)
  }

  @Test
  fun `inserire con una selezione la sostituisce`() {
    val out = FluidTextEdit.insert(TextFieldValue("ciao mondo", TextRange(5, 10)), "a tutti")
    assertEquals("ciao a tutti", out.text)
    assertEquals(TextRange(12), out.selection)
  }

  @Test
  fun `inserire in coda appende`() {
    val out = FluidTextEdit.insert(TextFieldValue("fine", TextRange(4)), ".")
    assertEquals("fine.", out.text)
    assertEquals(TextRange(5), out.selection)
  }

  @Test
  fun `il grassetto avvolge la selezione e la tiene selezionata`() {
    val out = FluidTextEdit.wrap(TextFieldValue("ciao mondo", TextRange(5, 10)), "**")
    assertEquals("ciao **mondo**", out.text)
    assertEquals(TextRange(7, 12), out.selection)
  }

  @Test
  fun `il grassetto senza selezione apre la coppia e mette il cursore in mezzo`() {
    val out = FluidTextEdit.wrap(TextFieldValue("ciao ", TextRange(5)), "**")
    assertEquals("ciao ****", out.text)
    assertEquals(TextRange(7), out.selection)
  }

  @Test
  fun `un prefisso e un suffisso diversi, per un link`() {
    val out = FluidTextEdit.wrap(TextFieldValue("vedi qui", TextRange(5, 8)), "[", "](url)")
    assertEquals("vedi [qui](url)", out.text)
    assertEquals(TextRange(6, 9), out.selection)
  }

  @Test
  fun `un segno di riga va a inizio riga, ovunque stia il cursore`() {
    val out = FluidTextEdit.toggleLinePrefix(TextFieldValue("prima\nseconda riga", TextRange(14)), "# ")
    assertEquals("prima\n# seconda riga", out.text)
    assertEquals(TextRange(16), out.selection)
  }

  @Test
  fun `un segno di riga gia' presente si toglie, e il cursore torna indietro`() {
    val out = FluidTextEdit.toggleLinePrefix(TextFieldValue("# titolo", TextRange(8)), "# ")
    assertEquals("titolo", out.text)
    assertEquals(TextRange(6), out.selection)
  }

  @Test
  fun `togliere il segno con il cursore dentro il segno non manda il cursore prima della riga`() {
    val out = FluidTextEdit.toggleLinePrefix(TextFieldValue("a\n- b", TextRange(3)), "- ")
    assertEquals("a\nb", out.text)
    assertEquals(TextRange(2), out.selection)
  }

  @Test
  fun `un segno di riga in un campo vuoto`() {
    val out = FluidTextEdit.toggleLinePrefix(TextFieldValue(""), "- ")
    assertEquals("- ", out.text)
    assertEquals(TextRange(2), out.selection)
  }
}
