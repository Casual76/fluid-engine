package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo stato salvato rinuncia al documento anche per la dimensione serializzata, non solo per la
 * lunghezza del testo: molti link lunghi stanno in poco testo ma superano il limite del Binder.
 */
class FluidRichTextSavedSizeTest {
  private fun save(state: FluidRichTextState): Any? = with(FluidRichTextState.Saver) { SaverScope { true }.save(state) }

  /** Un documento con [count] link distinti di circa [urlLength] caratteri, ognuno su una riga. */
  private fun linkedDocument(count: Int, urlLength: Int): FluidRichTextDocument = buildFluidRichText {
    repeat(count) { index ->
      val url = "https://example.org/" + "a".repeat(urlLength) + index
      paragraph { link("l$index", url) }
    }
  }

  /** Un tratto formattato ogni due caratteri: poco testo, tantissimi tratti. */
  private fun manyRuns(pairs: Int): FluidRichTextDocument = buildFluidRichText {
    paragraph {
      repeat(pairs) { index -> if (index % 2 == 0) bold("ab") else append("cd") }
    }
  }

  @Test fun underTheCapEverythingIsRestored() {
    val doc = linkedDocument(count = 40, urlLength = 1900)
    val state = FluidRichTextState(doc, TextRange(3, 1))
    state.isFormattingRequested = true
    val saved = save(state)
    assertNotNull(saved)
    val restored = FluidRichTextState.Saver.restore(saved!!)!!
    assertEquals(doc, restored.document)
    assertEquals(TextRange(3, 1), restored.selection)
    assertTrue(restored.isFormattingRequested)
  }

  @Test fun theLongestAllowedPlainTextIsStillSaved() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("x".repeat(FluidRichTextState.MaxSavedLength)))
    val saved = save(state)
    assertNotNull(saved)
    assertEquals(state.document, FluidRichTextState.Saver.restore(saved!!)!!.document)
  }

  @Test fun manyLongLinksAreNotSavedAndDoNotThrow() {
    val doc = linkedDocument(count = 600, urlLength = 1900)
    assertTrue("poco testo", doc.text.length < FluidRichTextState.MaxSavedLength)
    assertNull(save(FluidRichTextState(doc)))
  }

  @Test fun justOverTheCapIsNotSavedAndJustUnderIs() {
    val perLink = 8L + 2L * ("https://example.org/".length + 1900 + 2)
    val fits = ((FluidRichTextState.MaxSavedBytes - 8_000) / perLink).toInt()
    assertNotNull(save(FluidRichTextState(linkedDocument(fits, 1900))))
    assertNull(save(FluidRichTextState(linkedDocument(fits + 20, 1900))))
  }

  @Test fun manySmallRunsCountToo() {
    val under = manyRuns(pairs = 10_000)
    val over = manyRuns(pairs = 24_000)
    assertTrue(over.text.length < FluidRichTextState.MaxSavedLength)
    val restored = FluidRichTextState.Saver.restore(save(FluidRichTextState(under))!!)!!
    assertEquals(under, restored.document)
    assertNull(save(FluidRichTextState(over)))
  }

  @Test fun theLengthRuleIsUnchanged() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("x".repeat(FluidRichTextState.MaxSavedLength + 1)))
    assertNull(save(state))
  }
}
