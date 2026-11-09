package dev.antigravity.fluidengine.richtext

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val B = FluidRichTextInlineFormat(bold = true)
private val I = FluidRichTextInlineFormat(italic = true)
private val BI = FluidRichTextInlineFormat(bold = true, italic = true)
private val Plain = FluidRichTextInlineFormat.Plain
private val Bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)
private val Numbered = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered)
private val Quote = FluidRichTextParagraphStyle(quote = true)

/** Il formato di ogni carattere, per confronti leggibili. */
internal fun FluidRichTextDocument.formats(): List<FluidRichTextInlineFormat> = text.indices.map { formatAt(it) }

class FluidRichTextDocumentTest {

  @Test fun emptyDocumentHasOnePlainParagraph() {
    val doc = FluidRichTextDocument.Empty
    assertEquals(1, doc.paragraphCount)
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain), doc.paragraphStyles)
    assertTrue(doc.isEmpty())
    assertEquals(doc, buildFluidRichText { })
  }

  @Test fun plainTextNormalizesLineBreaksAndControls() {
    val doc = FluidRichTextDocument.fromPlainText("a\r\nb\rc\u2028d\u0000e\tf")
    assertEquals("a\nb\nc\nde\tf", doc.text)
    assertEquals(4, doc.paragraphCount)
  }

  @Test fun builderMakesNormalizedRunsAndSafeLinks() {
    val doc = buildFluidRichText {
      paragraph { append("Ciao "); bold("Mario"); bold(" Rossi"); link("qui", "javascript:alert(1)") }
      paragraph(Bullet) { link("sito", "https://esempio.it") }
    }
    assertEquals("Ciao Mario Rossiqui\nsito", doc.text)
    // "Mario" e " Rossi" sono un tratto solo; il link pericoloso non e' un link.
    assertEquals(
      listOf(FluidRichTextSpan(5, 16, B), FluidRichTextSpan(20, 24, FluidRichTextInlineFormat(link = "https://esempio.it"))),
      doc.spans,
    )
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain, Bullet), doc.paragraphStyles)
  }

  @Test fun newlineInsideBuilderParagraphBecomesSpace() {
    val doc = buildFluidRichText { paragraph { append("a\nb") } }
    assertEquals("a b", doc.text)
    assertEquals(1, doc.paragraphCount)
  }

  @Test fun paragraphLookupAtEveryOffset() {
    val doc = FluidRichTextDocument.fromPlainText("ab\n\ncd")
    assertEquals(listOf(0, 0, 0, 1, 2, 2, 2), (0..6).map { doc.paragraphIndexAt(it) })
    assertEquals(3, doc.paragraphStart(1))
    assertEquals(3, doc.paragraphEnd(1))
    assertEquals(6, doc.paragraphEnd(2))
  }

  @Test fun overlappingStylesApplyAndRemoveCleanly() {
    var doc = FluidRichTextDocument.fromPlainText("abcdefghij")
    doc = doc.mapInline(0, 6) { it.with(FluidRichTextInlineStyle.Bold, true) }
    doc = doc.mapInline(4, 9) { it.with(FluidRichTextInlineStyle.Italic, true) }
    assertEquals(listOf(B, B, B, B, BI, BI, I, I, I, Plain), doc.formats())
    doc = doc.mapInline(2, 5) { it.with(FluidRichTextInlineStyle.Bold, false) }
    assertEquals(listOf(B, B, Plain, Plain, I, BI, I, I, I, Plain), doc.formats())
    // Tratti normalizzati: nessun vicino uguale, nessuno vuoto.
    val runs = doc.runs
    assertTrue(runs.zipWithNext().all { (a, b) -> a.format != b.format })
    assertTrue(runs.all { it.length > 0 })
  }

  @Test fun stylesNeverTouchNewlines() {
    var doc = FluidRichTextDocument.fromPlainText("ab\ncd")
    doc = doc.mapInline(0, 5) { it.with(FluidRichTextInlineStyle.Bold, true) }
    assertEquals(Plain, doc.formatAt(2))
    assertEquals(listOf(B, B, Plain, B, B), doc.formats())
    assertTrue(doc.allInline(0, 5) { it.bold })
    assertFalse(doc.allInline(2, 3) { it.bold })
  }

  @Test fun typingInheritsFromCharacterBefore() {
    val doc = buildFluidRichText { paragraph { bold("ab"); append("cd") } }
    assertEquals(B, doc.typingFormatAt(2))
    assertEquals(Plain, doc.typingFormatAt(3))
    // In testa al paragrafo: il formato del primo carattere.
    assertEquals(B, doc.typingFormatAt(0))
  }

  @Test fun typingAtEndOfLinkDoesNotExtendIt() {
    val link = FluidRichTextInlineFormat(link = "https://a.it")
    val doc = buildFluidRichText { paragraph { append("x"); link("ab", "https://a.it"); append("y") } }
    assertNull(doc.typingFormatAt(3).link)
    assertEquals(link, doc.typingFormatAt(2))
    assertNull(doc.typingFormatAt(1).link)
    val end = buildFluidRichText { paragraph { link("ab", "https://a.it") } }
    assertNull(end.typingFormatAt(2).link)
  }

  @Test fun linkRangeSpansRunsWithSameAddress() {
    val doc = buildFluidRichText {
      paragraph {
        append("x ")
        append("ab", FluidRichTextInlineFormat(link = "https://a.it"))
        append("cd", FluidRichTextInlineFormat(bold = true, link = "https://a.it"))
        append(" y")
      }
    }
    assertEquals(TextRange(2, 6), doc.linkRangeAt(3))
    assertEquals(TextRange(2, 6), doc.linkRangeAt(6))
    assertEquals(TextRange(2, 6), doc.linkRangeAt(2))
    assertNull(doc.linkRangeAt(0))
  }

  @Test fun deletingAcrossParagraphsKeepsTheFirstStyle() {
    val doc = buildFluidRichText {
      paragraph(Bullet) { append("uno") }
      paragraph { append("due") }
      paragraph(Quote) { append("tre") }
    }
    val merged = doc.replaceText(2, 9, "", Plain)
    assertEquals("unre", merged.text)
    assertEquals(listOf(Bullet), merged.paragraphStyles)
  }

  @Test fun typedNewlinesContinueTheParagraphStyle() {
    val doc = buildFluidRichText { paragraph(Numbered) { append("uno") } }
    val split = doc.replaceText(3, 3, "\n\n", Plain)
    assertEquals(listOf(Numbered, Numbered, Numbered), split.paragraphStyles)
  }

  @Test fun fragmentInsertionKeepsFragmentParagraphs() {
    val doc = FluidRichTextDocument.fromPlainText("inizio fine")
    val fragment = buildFluidRichText {
      paragraph { bold("A") }
      paragraph(Bullet) { append("B") }
      paragraph(Numbered) { append("C") }
    }
    val result = doc.replaceWith(7, 7, fragment)
    assertEquals("inizio A\nB\nCfine", result.text)
    // Il primo paragrafo del frammento si fonde col testo prima (che tiene il suo stile); l'ultimo porta
    // il suo stile al resto della riga.
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain, Bullet, Numbered), result.paragraphStyles)
    assertEquals(B, result.formatAt(7))
  }

  @Test fun singleParagraphFragmentIntoEmptyParagraphAdoptsItsStyle() {
    val doc = buildFluidRichText { paragraph { append("a") }; paragraph { } }
    val fragment = buildFluidRichText { paragraph(Quote) { append("citato") } }
    assertEquals(listOf(FluidRichTextParagraphStyle.Plain, Quote), doc.replaceWith(2, 2, fragment).paragraphStyles)
    // In una voce di elenco vuota un frammento semplice non toglie la voce.
    val list = buildFluidRichText { paragraph(Bullet) { } }
    assertEquals(listOf(Bullet), list.replaceWith(0, 0, FluidRichTextDocument.fromPlainText("x")).paragraphStyles)
  }

  @Test fun fragmentEndingWithNewlineLeavesTheRestInItsParagraph() {
    val doc = buildFluidRichText { paragraph(Bullet) { append("voce") } }
    val fragment = buildFluidRichText { paragraph(Quote) { append("q") }; paragraph { } }
    val result = doc.replaceWith(0, 0, fragment)
    assertEquals("q\nvoce", result.text)
    assertEquals(listOf(Quote, Bullet), result.paragraphStyles)
  }

  @Test fun numberingRestartsAfterABreak() {
    val doc = buildFluidRichText {
      paragraph(Numbered) { append("a") }
      paragraph(Numbered) { append("b") }
      paragraph { append("x") }
      paragraph(Numbered) { append("c") }
      paragraph(Numbered.copy(quote = true)) { append("d") }
    }
    assertEquals(listOf(1, 2, 0, 1, 1), (0 until doc.paragraphCount).map { doc.listNumber(it) })
  }

  @Test fun sliceKeepsStylesAndTouchedParagraphs() {
    val doc = buildFluidRichText {
      paragraph(Bullet) { append("ab"); bold("cd") }
      paragraph(Quote) { italic("ef") }
    }
    val slice = doc.slice(3, 7)
    assertEquals("d\nef", slice.text)
    assertEquals(listOf(Bullet, Quote), slice.paragraphStyles)
    assertEquals(listOf(B, Plain, I, I), slice.formats())
  }

  @Test fun plainTextExportShowsStructure() {
    val doc = buildFluidRichText {
      paragraph { append("Ciao "); bold("tutti") }
      paragraph(Bullet) { append("uno") }
      paragraph(Numbered) { append("due") }
      paragraph(Numbered) { link("sito", "https://esempio.it") }
      paragraph(Quote) { link("https://a.it", "https://a.it") }
    }
    assertEquals(
      "Ciao tutti\n• uno\n1. due\n2. sito (https://esempio.it)\n> https://a.it",
      doc.toPlainText(),
    )
  }

  @Test fun unicodeSurvivesEditing() {
    val doc = FluidRichTextDocument.fromPlainText("ciao 👋🏽 مرحبا")
    val bolded = doc.mapInline(5, 9) { it.copy(bold = true) }
    assertEquals(doc.text, bolded.text)
    val edited = bolded.replaceText(9, 9, " ✓", bolded.typingFormatAt(9))
    assertEquals("ciao 👋🏽 ✓ مرحبا", edited.text)
    assertTrue(edited.formatAt(10).bold)
  }
}
