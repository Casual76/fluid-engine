package dev.antigravity.fluidengine.richtext

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La logica che riporta sul documento le modifiche della tastiera: scrivere, comporre, a capo, cancellare. */
class FluidRichTextEditsTest {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)
  private val quote = FluidRichTextParagraphStyle(quote = true)
  private val bold = FluidRichTextInlineFormat(bold = true)

  private fun type(doc: FluidRichTextDocument, at: Int, text: String, pending: PendingFormat? = null) =
    applyUserChanges(doc, listOf(UserChange(at, at, text)), TextRange(at), pending)

  private fun applied(outcome: UserEditOutcome): UserEditOutcome.Applied = outcome as UserEditOutcome.Applied

  @Test fun typingInsideBoldStaysBold() {
    val doc = buildFluidRichText { paragraph { bold("ab"); append(" c") } }
    val result = applied(type(doc, 2, "x")).document
    assertEquals("abx c", result.text)
    assertTrue(result.formatAt(2).bold)
    assertFalse(result.formatAt(3).bold)
  }

  @Test fun pendingFormatAppliesOnlyWhereItWasChosen() {
    val doc = FluidRichTextDocument.fromPlainText("ab")
    val pending = PendingFormat(2, bold)
    assertTrue(applied(type(doc, 2, "x", pending)).document.formatAt(2).bold)
    // Il cursore si e' spostato: il grassetto scelto altrove non vale.
    assertFalse(applied(type(doc, 1, "x", pending)).document.formatAt(1).bold)
  }

  @Test fun compositionReplacementKeepsTheWordFormat() {
    // Gboard compone «cia» -> «ciao» sostituendo la regione di composizione: il grassetto resta.
    var doc = applied(type(FluidRichTextDocument.Empty, 0, "c", PendingFormat(0, bold))).document
    doc = applied(applyUserChanges(doc, listOf(UserChange(0, 1, "ci")), TextRange(1), null)).document
    doc = applied(applyUserChanges(doc, listOf(UserChange(0, 2, "ciao")), TextRange(2), null)).document
    assertEquals("ciao", doc.text)
    assertTrue(doc.formats().all { it.bold })
  }

  @Test fun autocorrectReplacementKeepsFormat() {
    val doc = buildFluidRichText { paragraph { append("un "); bold("teh"); append(" fine") } }
    val result = applied(applyUserChanges(doc, listOf(UserChange(3, 6, "the")), TextRange(6), null)).document
    assertEquals("un the fine", result.text)
    assertEquals(listOf(false, false, false, true, true, true, false), (0..6).map { result.formatAt(it).bold })
  }

  @Test fun enterInListItemContinuesTheList() {
    val doc = buildFluidRichText { paragraph(bullet) { append("uno") } }
    val result = applied(type(doc, 3, "\n")).document
    assertEquals("uno\n", result.text)
    assertEquals(listOf(bullet, bullet), result.paragraphStyles)
  }

  @Test fun enterOnEmptyListItemLeavesTheList() {
    val doc = buildFluidRichText { paragraph(bullet) { append("uno") }; paragraph(bullet) { } }
    val outcome = type(doc, 4, "\n")
    assertTrue(outcome is UserEditOutcome.Intercepted)
    val result = (outcome as UserEditOutcome.Intercepted).document
    assertEquals("uno\n", result.text)
    assertEquals(listOf(bullet, FluidRichTextParagraphStyle.Plain), result.paragraphStyles)
  }

  @Test fun enterOnEmptyQuotedListItemLeavesListThenQuote() {
    val quotedBullet = FluidRichTextParagraphStyle(FluidRichTextListKind.Bullet, quote = true)
    val doc = buildFluidRichText { paragraph(quotedBullet) { } }
    val first = (type(doc, 0, "\n") as UserEditOutcome.Intercepted).document
    assertEquals(listOf(quote), first.paragraphStyles)
    val second = (type(first, 0, "\n") as UserEditOutcome.Intercepted).document
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain), second.paragraphStyles)
    // Un paragrafo normale vuoto: l'a capo e' un a capo.
    assertTrue(type(second, 0, "\n") is UserEditOutcome.Applied)
  }

  @Test fun backspaceAtStartOfListItemRemovesTheItemNotTheLine() {
    val doc = buildFluidRichText { paragraph { append("prima") }; paragraph(bullet) { append("voce") } }
    val outcome = applyUserChanges(doc, listOf(UserChange(5, 6, "")), TextRange(6), null)
    val result = (outcome as UserEditOutcome.Intercepted).document
    assertEquals(doc.text, result.text)
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain, FluidRichTextParagraphStyle.Plain), result.paragraphStyles)
    // Su un paragrafo normale lo stesso cancella unisce le righe.
    val merged = applied(applyUserChanges(result, listOf(UserChange(5, 6, "")), TextRange(6), null)).document
    assertEquals("primavoce", merged.text)
  }

  @Test fun deletingASelectionIncludingTheNewlineMerges() {
    val doc = buildFluidRichText { paragraph { append("ab") }; paragraph(bullet) { append("cd") } }
    // Selezione [1, 4) non e' un cursore in testa alla voce: si cancella e basta.
    val result = applied(applyUserChanges(doc, listOf(UserChange(1, 4, "")), TextRange(1, 4), null)).document
    assertEquals("ad", result.text)
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain), result.paragraphStyles)
  }

  @Test fun enterCarriesBoldIntoTheNewParagraph() {
    val doc = buildFluidRichText { paragraph { bold("titolo") } }
    val outcome = applied(type(doc, 6, "\n"))
    assertEquals(PendingFormat(7, bold), outcome.pending)
    val next = applied(type(outcome.document, 7, "x", outcome.pending)).document
    assertTrue(next.formatAt(7).bold)
  }

  @Test fun multipleChangesApplyFromLastToFirst() {
    val doc = buildFluidRichText { paragraph { bold("ab"); append("cd") } }
    val result = applied(
      applyUserChanges(doc, listOf(UserChange(1, 1, "X"), UserChange(3, 4, "Y")), TextRange(1), null),
    ).document
    assertEquals("aXbcY", result.text)
    assertTrue(result.formatAt(1).bold)
    assertFalse(result.formatAt(4).bold)
  }

  @Test fun formatForCursorAndSelection() {
    val doc = buildFluidRichText {
      paragraph(bullet) { bold("ab"); append("cd") }
      paragraph(bullet) { link("ef", "https://e.it") }
    }
    val atBold = doc.formatFor(TextRange(1), null)
    assertTrue(atBold.bold)
    assertEquals(FluidRichTextListKind.Bullet, atBold.list)
    val mixed = doc.formatFor(TextRange(0, 4), null)
    assertFalse(mixed.bold)
    val selectionBold = doc.formatFor(TextRange(2, 0), null)
    assertTrue(selectionBold.bold)
    assertEquals("https://e.it", doc.formatFor(TextRange(6), null).link)
    assertEquals("https://e.it", doc.formatFor(TextRange(5, 7), null).link)
    assertNull(doc.formatFor(TextRange(3, 7), null).link)
    assertEquals(FluidRichTextListKind.Bullet, doc.formatFor(TextRange(0, 7), null).list)
    val pendingItalic = doc.formatFor(TextRange(4), PendingFormat(4, FluidRichTextInlineFormat(italic = true)))
    assertTrue(pendingItalic.italic)
  }

  @Test fun reconcileFindsTheMinimalChange() {
    val doc = buildFluidRichText { paragraph { bold("abc"); append("def") } }
    val result = doc.reconcile("abXYdef")
    assertEquals("abXYdef", result.text)
    assertTrue(result.formatAt(2).bold)
    assertFalse(result.formatAt(4).bold)
    assertEquals(Triple(2, 3, 4), minimalDiff("abcdef", "abXYdef"))
  }
}
