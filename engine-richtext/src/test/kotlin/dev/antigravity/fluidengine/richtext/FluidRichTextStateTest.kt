package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** I comandi dello stato dal codice: stili, elenchi, link, inserimenti, annulla/ripeti, salvataggio. */
class FluidRichTextStateTest {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)

  @Test fun toggleOnSelectionAddsThenRemoves() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("ciao mondo"))
    state.select(TextRange(0, 4))
    state.toggle(FluidRichTextInlineStyle.Bold)
    assertTrue(state.format.bold)
    assertEquals(listOf(FluidRichTextSpan(0, 4, FluidRichTextInlineFormat(bold = true))), state.document.spans)
    // Selezione mista: il primo tocco mette lo stile a tutto.
    state.select(TextRange(2, 7))
    assertFalse(state.format.bold)
    state.toggle(FluidRichTextInlineStyle.Bold)
    assertEquals(listOf(FluidRichTextSpan(0, 7, FluidRichTextInlineFormat(bold = true))), state.document.spans)
    state.toggle(FluidRichTextInlineStyle.Bold)
    assertEquals(listOf(FluidRichTextSpan(0, 2, FluidRichTextInlineFormat(bold = true))), state.document.spans)
    assertEquals(FluidRichTextChangeSource.Formatting, state.lastChangeSource)
  }

  @Test fun toggleWithCursorChangesOnlyWhatComesNext() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("ab"))
    val revision = state.revision
    state.toggle(FluidRichTextInlineStyle.Italic)
    assertTrue(state.format.italic)
    assertEquals(revision, state.revision)
    assertTrue(state.document.spans.isEmpty())
    state.toggle(FluidRichTextInlineStyle.Italic)
    assertFalse(state.format.italic)
  }

  @Test fun listsAndQuotesToggleOnTouchedParagraphs() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("a\nb\nc"))
    state.select(TextRange(0, 3))
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, bullet, FluidRichTextParagraphStyle.Plain), state.document.paragraphStyles)
    state.toggleList(FluidRichTextListKind.Numbered)
    assertEquals(FluidRichTextListKind.Numbered, state.format.list)
    state.toggleList(FluidRichTextListKind.Numbered)
    assertNull(state.format.list)
    state.toggleQuote()
    assertTrue(state.document.paragraphStyles.take(2).all { it.quote })
    state.toggleQuote()
    assertFalse(state.document.paragraphStyles.any { it.quote })
  }

  @Test fun linksAreSafeAndEditable() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("vai al sito"))
    state.select(TextRange(7, 11))
    assertFalse(state.setLink("javascript:alert(1)"))
    assertTrue(state.document.spans.isEmpty())
    assertTrue(state.setLink("esempio.it"))
    assertEquals("https://esempio.it", state.document.formatAt(8).link)
    // Col cursore dentro il link si cambia l'indirizzo e anche il testo.
    state.select(TextRange(9))
    assertEquals("https://esempio.it", state.format.link)
    assertTrue(state.setLink("https://altro.it", "sito nuovo"))
    assertEquals("vai al sito nuovo", state.document.text)
    assertTrue((7 until 17).all { state.document.formatAt(it).link == "https://altro.it" })
    state.select(TextRange(10))
    state.removeLink()
    assertTrue(state.document.spans.isEmpty())
    // Col cursore fuori da un link: si inserisce il link col suo testo.
    state.select(TextRange(0))
    state.setLink("nome@esempio.it")
    assertTrue(state.document.text.startsWith("nome@esempio.it"))
    assertEquals("mailto:nome@esempio.it", state.document.formatAt(0).link)
  }

  @Test fun programmaticInsertionAndReplacement() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("Testo"))
    val firma = buildFluidRichText { paragraph { append("-- ") }; paragraph { bold("Mario") } }
    state.insert(5, buildFluidRichText { paragraph { }; paragraph { } }.let { breakDoc -> breakDoc })
    state.insert(state.document.text.length, firma)
    assertEquals("Testo\n-- \nMario", state.document.text)
    assertTrue(state.document.formatAt(state.document.text.indexOf("Mario")).bold)
    assertEquals(FluidRichTextChangeSource.Program, state.lastChangeSource)
    assertEquals(TextRange(state.document.text.length), state.selection)
    // Sostituire la firma: l'app sa dov'e'.
    val start = state.document.text.indexOf("-- ")
    state.replace(start, state.document.text.length, buildFluidRichText { paragraph { italic("Ciao") } })
    assertEquals("Testo\nCiao", state.document.text)
  }

  @Test fun undoAndRedoWalkTheHistory() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("abc"))
    val original = state.document
    state.select(TextRange(0, 3))
    state.toggle(FluidRichTextInlineStyle.Bold)
    val bolded = state.document
    state.toggleList(FluidRichTextListKind.Bullet)
    val listed = state.document
    assertTrue(state.canUndo)
    assertFalse(state.canRedo)
    state.undo()
    assertEquals(bolded, state.document)
    state.undo()
    assertEquals(original, state.document)
    assertFalse(state.canUndo)
    assertEquals(FluidRichTextChangeSource.History, state.lastChangeSource)
    state.redo()
    state.redo()
    assertEquals(listed, state.document)
    assertFalse(state.canRedo)
    // Una modifica nuova dopo un annulla cancella il «ripeti».
    state.undo()
    state.toggleQuote()
    assertFalse(state.canRedo)
  }

  @Test fun undoRestoresTextAndSelection() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("ab"))
    state.select(TextRange(1))
    state.insert(1, FluidRichTextDocument.fromPlainText("XYZ"))
    assertEquals("aXYZb", state.textFieldState.text.toString())
    state.undo()
    assertEquals("ab", state.textFieldState.text.toString())
    assertEquals("ab", state.document.text)
    assertEquals(TextRange(1), state.selection)
  }

  @Test fun historyIsBounded() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("abc"))
    state.select(TextRange(0, 3))
    repeat(FluidRichTextState.MaxHistory + 20) { state.toggle(FluidRichTextInlineStyle.Bold) }
    var steps = 0
    while (state.canUndo) {
      state.undo()
      steps++
    }
    assertEquals(FluidRichTextState.MaxHistory, steps)
  }

  @Test fun revisionGrowsOnEveryDocumentChange() {
    val state = FluidRichTextState()
    val before = state.revision
    state.setDocument(FluidRichTextDocument.fromPlainText("x"))
    state.select(TextRange(0, 1))
    state.toggle(FluidRichTextInlineStyle.Underline)
    assertEquals(before + 2, state.revision)
    state.clearHistory()
    assertFalse(state.canUndo)
  }

  @Test fun saverKeepsDocumentSelectionAndRequest() {
    val doc = buildFluidRichText {
      paragraph(bullet) { bold("uno"); link("due", "https://a.it") }
      paragraph(FluidRichTextParagraphStyle(FluidRichTextListKind.Numbered, quote = true)) { italic("tre") }
    }
    val state = FluidRichTextState(doc, TextRange(5, 2))
    state.isFormattingRequested = true
    val scope = SaverScope { true }
    val saved = with(FluidRichTextState.Saver) { scope.save(state) }!!
    val restored = FluidRichTextState.Saver.restore(saved)!!
    assertEquals(doc, restored.document)
    assertEquals(TextRange(5, 2), restored.selection)
    assertTrue(restored.isFormattingRequested)
  }

  @Test fun saverSkipsHugeDocuments() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("x".repeat(FluidRichTextState.MaxSavedLength + 1)))
    assertNull(with(FluidRichTextState.Saver) { SaverScope { true }.save(state) })
  }

  @Test fun formattingBarVisibility() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("abc"))
    assertFalse(state.isFormattingBarVisible)
    state.isFocused = true
    assertFalse(state.isFormattingBarVisible)
    state.select(TextRange(0, 2))
    assertTrue(state.isFormattingBarVisible)
    state.select(TextRange(1))
    state.isFormattingRequested = true
    assertTrue(state.isFormattingBarVisible)
    state.isEditable = false
    assertFalse(state.isFormattingBarVisible)
  }
}
