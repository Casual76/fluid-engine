package dev.antigravity.fluidengine.richtext

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I comandi di paragrafo e lo stato mostrato dalla barra trattano la selezione come un intervallo
 * semiaperto `[min, max)`: una selezione non vuota che finisce esattamente all'inizio di un paragrafo
 * (una riga intera col suo a capo) non tocca quel paragrafo. Il cursore fermo indica il suo paragrafo.
 */
class FluidRichTextParagraphSelectionTest {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)
  private val plain = FluidRichTextParagraphStyle.Plain

  private fun state(text: String) = FluidRichTextState(FluidRichTextDocument.fromPlainText(text))

  @Test fun halfOpenParagraphSelectionDoesNotFormatFollowingParagraph() {
    val state = state("uno\ndue")
    state.select(TextRange(0, 4)) // "uno\n": nessun carattere di "due"
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, plain), state.document.paragraphStyles)
  }

  @Test fun reverseHalfOpenSelectionDoesNotQuoteFollowingParagraph() {
    val state = state("uno\ndue")
    state.select(TextRange(4, 0))
    state.toggleQuote()
    assertTrue(state.document.paragraphStyles[0].quote)
    assertFalse(state.document.paragraphStyles[1].quote)
  }

  @Test fun severalParagraphsStopBeforeTheOneTheSelectionOnlyReachesTheStartOf() {
    val state = state("a\nb\nc\nd")
    state.select(TextRange(0, 4)) // "a\nb\n"
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, bullet, plain, plain), state.document.paragraphStyles)
    state.select(TextRange(6, 2)) // all'indietro: "b\nc\n"
    state.toggleList(FluidRichTextListKind.Bullet) // b e' gia' voce, c no: tutti diventano voci
    assertEquals(listOf(bullet, bullet, bullet, plain), state.document.paragraphStyles)
    state.toggleList(FluidRichTextListKind.Bullet) // ora b e c sono voci: escono; a resta, d resta
    assertEquals(listOf(bullet, plain, plain, plain), state.document.paragraphStyles)
  }

  @Test fun aSelectionThatTouchesOneCharacterOfTheNextParagraphTakesIt() {
    val state = state("uno\ndue")
    state.select(TextRange(0, 5)) // "uno\nd"
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, bullet), state.document.paragraphStyles)
  }

  @Test fun theNewlineAloneBelongsToItsParagraph() {
    val state = state("uno\ndue")
    state.select(TextRange(3, 4)) // solo l'a capo di "uno"
    state.toggleQuote()
    assertEquals(listOf(true, false), state.document.paragraphStyles.map { it.quote })
  }

  @Test fun selectionEndingAtTheEndOfTheDocumentIncludesTheLastParagraph() {
    val state = state("a\nb")
    state.select(TextRange(0, 3))
    state.toggleList(FluidRichTextListKind.Numbered)
    assertEquals(
      listOf(FluidRichTextListKind.Numbered, FluidRichTextListKind.Numbered),
      state.document.paragraphStyles.map { it.list },
    )
    state.toggleList(FluidRichTextListKind.Numbered)
    state.select(TextRange(2, 3)) // solo "b"
    state.toggleList(FluidRichTextListKind.Numbered)
    assertEquals(listOf(null, FluidRichTextListKind.Numbered), state.document.paragraphStyles.map { it.list })
  }

  @Test fun selectingTheWholeLastLineWithItsNewlineDoesNotTouchTheEmptyParagraphAfter() {
    val state = state("a\nb\n") // tre paragrafi, l'ultimo vuoto
    state.select(TextRange(0, 4))
    state.toggleQuote()
    assertEquals(listOf(true, true, false), state.document.paragraphStyles.map { it.quote })
  }

  @Test fun anEmptyParagraphInTheMiddleIsTouchedByItsOwnNewline() {
    val state = state("a\n\nb")
    state.select(TextRange(0, 2)) // "a\n": solo il primo
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, plain, plain), state.document.paragraphStyles)
    state.select(TextRange(2, 3)) // l'a capo del paragrafo vuoto
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(bullet, bullet, plain), state.document.paragraphStyles)
  }

  @Test fun aCursorOnlyStillMeansTheParagraphOfTheCursor() {
    val state = state("uno\ndue")
    state.select(TextRange(4)) // l'inizio di "due"
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(plain, bullet), state.document.paragraphStyles)
    state.select(TextRange(3)) // la fine di "uno"
    state.toggleQuote()
    assertEquals(listOf(true, false), state.document.paragraphStyles.map { it.quote })
  }

  @Test fun indentCommandsStopAtTheHalfOpenBoundary() {
    val doc = buildFluidRichText {
      repeat(3) { paragraph(bullet) { append("x") } }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 4)) // "x\nx\n"
    state.increaseIndent()
    assertEquals(listOf(2, 2, 1), state.document.paragraphStyles.map { it.depth })
    state.select(TextRange(4, 0))
    state.decreaseIndent()
    assertEquals(listOf(1, 1, 1), state.document.paragraphStyles.map { it.depth })
  }

  @Test fun nestedListsAreNotDraggedAlongByTheNextParagraph() {
    val nested = FluidRichTextParagraphStyle.of(FluidRichTextBlock.Bullet, FluidRichTextBlock.Bullet)
    val doc = buildFluidRichText {
      paragraph(bullet) { append("a") }
      paragraph(nested) { append("b") }
      paragraph(nested) { append("c") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(2, 4)) // "b\n"
    state.decreaseIndent()
    assertEquals(listOf(1, 1, 2), state.document.paragraphStyles.map { it.depth })
    state.select(TextRange(0, 2)) // "a\n"
    state.increaseIndent()
    assertEquals(listOf(2, 1, 2), state.document.paragraphStyles.map { it.depth })
  }

  @Test fun theToolbarStateFollowsTheSameHalfOpenRule() {
    val doc = buildFluidRichText {
      paragraph(bullet) { append("uno") }
      paragraph { append("due") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 4)) // "uno\n": la barra vede solo la voce
    assertEquals(FluidRichTextListKind.Bullet, state.format.list)
    assertTrue(state.format.canIncreaseIndent)
    state.select(TextRange(4, 0))
    assertEquals(FluidRichTextListKind.Bullet, state.format.list)
    state.select(TextRange(0, 5)) // tocca un carattere di "due": la selezione e' mista
    assertEquals(null, state.format.list)
  }

  @Test fun theToolbarQuoteStateIgnoresTheFollowingParagraphInBothDirections() {
    val doc = buildFluidRichText {
      paragraph(FluidRichTextParagraphStyle(quote = true)) { append("uno") }
      paragraph { append("due") }
      paragraph { append("tre") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 4))
    assertTrue(state.format.quote)
    state.select(TextRange(4, 0))
    assertTrue(state.format.quote)
    state.select(TextRange(0, 8)) // "uno\ndue\n": un paragrafo senza citazione dentro
    assertFalse(state.format.quote)
  }

  @Test fun theToolbarStateAtTheEndOfTheDocumentIncludesTheLastParagraph() {
    val doc = buildFluidRichText {
      paragraph(bullet) { append("a") }
      paragraph { append("b") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 3))
    assertEquals(null, state.format.list)
    state.select(TextRange(2, 3)) // solo "b"
    assertEquals(null, state.format.list)
    assertFalse(state.format.canDecreaseIndent)
  }
}
