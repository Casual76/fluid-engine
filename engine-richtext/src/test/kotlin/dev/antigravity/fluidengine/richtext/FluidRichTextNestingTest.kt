package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Bullet
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Numbered
import dev.antigravity.fluidengine.richtext.FluidRichTextBlock.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

private fun style(vararg blocks: FluidRichTextBlock) = FluidRichTextParagraphStyle.of(*blocks)
private val Plain = FluidRichTextParagraphStyle.Plain

/** Elenchi e citazioni annidati: modello, comandi, tastiera, numerazione, conversioni, HTML ostile. */
class FluidRichTextNestingTest {

  // --- modello -------------------------------------------------------------------------------------

  @Test fun styleStackDescribesItself() {
    val nested = style(Quote, Bullet, Numbered)
    assertEquals(3, nested.depth)
    assertEquals(FluidRichTextListKind.Numbered, nested.list)
    assertTrue(nested.quote)
    assertEquals(2, nested.listLevel)
    assertEquals(1, nested.quoteLevel)
    val quoteInItem = style(Bullet, Quote)
    assertNull(quoteInItem.list)
    assertEquals(0, quoteInItem.listLevel)
    assertTrue(quoteInItem.quote)
    // Il costruttore a un livello e' la voce dentro la citazione di prima.
    assertEquals(style(Quote, Bullet), FluidRichTextParagraphStyle(FluidRichTextListKind.Bullet, quote = true))
    assertEquals(Plain, FluidRichTextParagraphStyle())
    assertEquals(style(Numbered), FluidRichTextParagraphStyle(list = FluidRichTextListKind.Numbered))
  }

  @Test fun depthIsCappedKeepingTheInnermostContainer() {
    val deep = FluidRichTextParagraphStyle(List(40) { Quote } + Numbered)
    assertEquals(FluidRichTextParagraphStyle.MaxDepth, deep.depth)
    assertEquals(FluidRichTextListKind.Numbered, deep.list)
    assertTrue(FluidRichTextParagraphStyle.MaxDepth >= 4)
  }

  @Test fun copyKeepsTheOneLevelMeaning() {
    val numbered = style(Numbered)
    assertEquals(style(Quote, Numbered), numbered.copy(quote = true))
    assertEquals(style(Bullet), numbered.copy(list = FluidRichTextListKind.Bullet))
    assertEquals(Plain, numbered.copy(list = null))
    assertEquals(style(Bullet, Bullet), style(Quote, Bullet, Bullet).copy(quote = false))
    assertEquals(style(Quote), style(Quote, Bullet, Numbered).copy(list = null))
  }

  @Test fun indentAndOutdentWalkTheLevels() {
    assertEquals(style(Bullet, Bullet), style(Bullet).indented())
    assertEquals(style(Quote, Numbered, Numbered), style(Quote, Numbered).indented())
    assertEquals(style(Quote, Quote), style(Quote).indented())
    // Un paragrafo normale non ha un rientro da aumentare; al tetto non cambia niente.
    assertEquals(Plain, Plain.indented())
    val top = FluidRichTextParagraphStyle(List(FluidRichTextParagraphStyle.MaxDepth) { Bullet })
    assertEquals(top, top.indented())
    assertFalse(top.canIndent)
    assertEquals(style(Bullet), style(Bullet, Bullet).outdented())
    assertEquals(Plain, style(Bullet).outdented())
    assertEquals(style(Quote), style(Quote, Bullet).outdented())
    assertEquals(Plain, Plain.outdented())
  }

  // --- numerazione ---------------------------------------------------------------------------------

  @Test fun numberingRestartsInSubListsAndContinuesAfterThem() {
    val doc = buildFluidRichText {
      paragraph(style(Numbered)) { append("uno") }
      paragraph(style(Numbered, Numbered)) { append("uno.uno") }
      paragraph(style(Numbered, Numbered)) { append("uno.due") }
      paragraph(style(Numbered, Bullet)) { append("puntato") }
      paragraph(style(Numbered, Numbered)) { append("di nuovo uno") }
      paragraph(style(Numbered, Quote)) { append("citato nella voce") }
      paragraph(style(Numbered)) { append("due") }
      paragraph(style(Numbered, Numbered)) { append("due.uno") }
      paragraph(style(Numbered)) { append("tre") }
      paragraph(Plain) { append("fine") }
      paragraph(style(Numbered)) { append("di nuovo uno") }
    }
    assertEquals(listOf(1, 1, 2, 0, 1, 0, 2, 1, 3, 0, 1), (0 until doc.paragraphCount).map { doc.listNumber(it) })
  }

  @Test fun anItemOpenedOnlyForASubListCounts() {
    // Come in HTML: `<ol><li><ol><li>a</li></ol></li><li>b</li></ol>` mostra b come 2.
    val doc = buildFluidRichText {
      paragraph(style(Numbered, Numbered)) { append("a") }
      paragraph(style(Numbered)) { append("b") }
    }
    assertEquals(listOf(1, 2), (0..1).map { doc.listNumber(it) })
  }

  // --- comandi -------------------------------------------------------------------------------------

  @Test fun indentCommandsOnAMultiParagraphSelection() {
    val doc = buildFluidRichText {
      paragraph { append("normale") }
      paragraph(style(Bullet)) { append("voce") }
      paragraph(style(Quote)) { append("citato") }
      paragraph(style(Numbered)) { append("numero") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, doc.text.length))
    assertTrue(state.format.canIncreaseIndent)
    assertTrue(state.format.canDecreaseIndent)
    state.increaseIndent()
    assertEquals(
      listOf(Plain, style(Bullet, Bullet), style(Quote, Quote), style(Numbered, Numbered)),
      state.document.paragraphStyles,
    )
    assertEquals(FluidRichTextChangeSource.Formatting, state.lastChangeSource)
    state.decreaseIndent()
    state.decreaseIndent()
    assertEquals(List(4) { Plain }, state.document.paragraphStyles)
    assertFalse(state.format.canDecreaseIndent)
    assertFalse(state.format.canIncreaseIndent)
    // Annulla torna indietro un passo alla volta.
    state.undo()
    assertEquals(listOf(Plain, style(Bullet), style(Quote), style(Numbered)), state.document.paragraphStyles)
    state.undo()
    assertEquals(style(Bullet, Bullet), state.document.paragraphStyles[1])
    state.redo()
    assertEquals(style(Bullet), state.document.paragraphStyles[1])
  }

  @Test fun indentStopsAtTheCapAndPlainParagraphsDisableIt() {
    val state = FluidRichTextState(buildFluidRichText { paragraph(style(Bullet)) { append("x") } })
    state.select(TextRange(1))
    repeat(20) { state.increaseIndent() }
    assertEquals(FluidRichTextParagraphStyle.MaxDepth, state.document.paragraphStyles.single().depth)
    assertFalse(state.format.canIncreaseIndent)
    assertTrue(state.format.canDecreaseIndent)
    assertEquals(FluidRichTextParagraphStyle.MaxDepth, state.format.listLevel)
    val plain = FluidRichTextState(FluidRichTextDocument.fromPlainText("x"))
    val revision = plain.revision
    plain.increaseIndent()
    assertEquals(revision, plain.revision)
    assertFalse(plain.canUndo)
  }

  @Test fun toggleListOnNestedItems() {
    val doc = buildFluidRichText {
      paragraph(style(Quote, Bullet)) { append("a") }
      paragraph(style(Quote, Bullet, Bullet)) { append("b") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 3))
    assertEquals(FluidRichTextListKind.Bullet, state.format.list)
    assertEquals(0, state.format.listLevel)
    // Tutte voci puntate: escono dagli elenchi e restano nella citazione.
    state.toggleList(FluidRichTextListKind.Bullet)
    assertEquals(listOf(style(Quote), style(Quote)), state.document.paragraphStyles)
    state.undo()
    // Numerato: ogni voce cambia tipo al suo livello.
    state.toggleList(FluidRichTextListKind.Numbered)
    assertEquals(listOf(style(Quote, Numbered), style(Quote, Bullet, Numbered)), state.document.paragraphStyles)
  }

  @Test fun toggleQuoteWrapsTheWholeStackAndRemovesEveryQuote() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet)) { append("a") }
      paragraph(style(Bullet, Bullet)) { append("b") }
    }
    val state = FluidRichTextState(doc)
    state.select(TextRange(0, 3))
    state.toggleQuote()
    assertEquals(listOf(style(Quote, Bullet), style(Quote, Bullet, Bullet)), state.document.paragraphStyles)
    assertTrue(state.format.quote)
    state.select(TextRange(2))
    state.increaseIndent()
    assertEquals(style(Quote, Bullet, Bullet, Bullet), state.document.paragraphStyles[1])
    state.setDocument(buildFluidRichText { paragraph(style(Quote, Bullet, Quote)) { append("q") } })
    state.toggleQuote()
    assertEquals(style(Bullet), state.document.paragraphStyles.single())
  }

  @Test fun formatReportsLevelsForTalkBack() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet, Bullet)) { bold("b") }
      paragraph(style(Quote, Quote)) { append("q") }
      paragraph(style(Bullet)) { append("c") }
    }
    val labels = FluidRichTextLabels()
    assertEquals("Grassetto, Elenco puntato, livello 2", doc.formatFor(TextRange(1), null).describe(labels))
    assertEquals("Citazione, livello 2", doc.formatFor(TextRange(3), null).describe(labels))
    assertEquals("Elenco puntato", doc.formatFor(TextRange(5), null).describe(labels))
    assertEquals("Bulleted list, level 2", doc.formatFor(TextRange(0), null).copy(bold = false).describe(FluidRichTextLabels.english()))
    // Selezione su livelli diversi: l'elenco e' comune, il livello no.
    val mixed = doc.formatFor(TextRange(0, 5), null)
    assertNull(mixed.list)
    val bullets = doc.formatFor(TextRange(0, 1), null)
    assertEquals(2, bullets.listLevel)
  }

  // --- tastiera ------------------------------------------------------------------------------------

  @Test fun enterInANestedItemContinuesAtTheSameLevel() {
    val doc = buildFluidRichText { paragraph(style(Bullet, Numbered)) { append("voce") } }
    val result = (applyUserChanges(doc, listOf(UserChange(4, 4, "\n")), TextRange(4), null) as UserEditOutcome.Applied).document
    assertEquals(listOf(style(Bullet, Numbered), style(Bullet, Numbered)), result.paragraphStyles)
    assertEquals(listOf(1, 2), (0..1).map { result.listNumber(it) })
  }

  @Test fun enterOnAnEmptyNestedItemClimbsOneLevelAtATime() {
    var doc = buildFluidRichText {
      paragraph(style(Quote, Bullet)) { append("a") }
      paragraph(style(Quote, Bullet, Bullet)) { }
    }
    val expected = listOf(style(Quote, Bullet), style(Quote), Plain)
    for (step in expected) {
      val outcome = applyUserChanges(doc, listOf(UserChange(2, 2, "\n")), TextRange(2), null)
      doc = (outcome as UserEditOutcome.Intercepted).document
      assertEquals("a\n", doc.text)
      assertEquals(step, doc.paragraphStyles[1])
    }
    // Su un paragrafo normale vuoto l'a capo e' un a capo.
    assertTrue(applyUserChanges(doc, listOf(UserChange(2, 2, "\n")), TextRange(2), null) is UserEditOutcome.Applied)
  }

  @Test fun backspaceAtTheStartOfANestedItemClimbsBeforeMerging() {
    var doc = buildFluidRichText {
      paragraph { append("prima") }
      paragraph(style(Numbered, Numbered)) { append("voce") }
    }
    for (step in listOf(style(Numbered), Plain)) {
      val outcome = applyUserChanges(doc, listOf(UserChange(5, 6, "")), TextRange(6), null)
      doc = (outcome as UserEditOutcome.Intercepted).document
      assertEquals("prima\nvoce", doc.text)
      assertEquals(step, doc.paragraphStyles[1])
    }
    val merged = (applyUserChanges(doc, listOf(UserChange(5, 6, "")), TextRange(6), null) as UserEditOutcome.Applied).document
    assertEquals("primavoce", merged.text)
  }

  @Test fun backspaceAtDocumentStartClimbsToo() {
    val state = FluidRichTextState(buildFluidRichText { paragraph(style(Bullet, Bullet)) { append("x") } }, TextRange(0))
    assertTrue(state.backspaceAtStart())
    assertEquals(style(Bullet), state.document.paragraphStyles.single())
    assertTrue(state.backspaceAtStart())
    assertFalse(state.backspaceAtStart())
    assertEquals(Plain, state.document.paragraphStyles.single())
  }

  // --- salvataggio ---------------------------------------------------------------------------------

  @Test fun saverKeepsNestedStylesAndReadsTheFlatFormat() {
    val doc = buildFluidRichText {
      paragraph(style(Quote, Bullet, Numbered)) { bold("a") }
      paragraph(style(Bullet, Quote, Quote)) { append("b") }
      paragraph(FluidRichTextParagraphStyle(List(FluidRichTextParagraphStyle.MaxDepth) { Numbered })) { append("c") }
    }
    val state = FluidRichTextState(doc, TextRange(1))
    val saved = with(FluidRichTextState.Saver) { SaverScope { true }.save(state) }!!
    assertEquals(doc, FluidRichTextState.Saver.restore(saved)!!.document)
    // Lo stato salvato prima dell'annidamento: un codice per paragrafo (1 puntato, 2 numerato, 4 citazione).
    val flat = arrayListOf<Any>("a\nb", intArrayOf(3, 0), arrayListOf<String>(), intArrayOf(1 or 4, 2), 0, 0, false)
    val restored = FluidRichTextState.Saver.restore(flat)!!.document
    assertEquals(listOf(style(Quote, Bullet), style(Numbered)), restored.paragraphStyles)
  }

  // --- HTML ----------------------------------------------------------------------------------------

  @Test fun writesNestedHtmlInsideListItems() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet)) { append("uno") }
      paragraph(style(Bullet, Bullet)) { append("uno.a") }
      paragraph(style(Bullet, Numbered)) { append("uno.1") }
      paragraph(style(Bullet, Quote)) { append("citato") }
      paragraph(style(Bullet)) { append("due") }
      paragraph(style(Quote)) { append("q") }
      paragraph(style(Quote, Quote)) { append("qq") }
      paragraph(style(Quote)) { append("q2") }
      paragraph(style(Numbered, Numbered)) { append("orfana") }
    }
    assertEquals(
      "<ul><li>uno<ul><li>uno.a</li></ul><ol><li>uno.1</li></ol><blockquote><div>citato</div></blockquote></li>" +
        "<li>due</li></ul>" +
        "<blockquote><div>q</div><blockquote><div>qq</div></blockquote><div>q2</div></blockquote>" +
        "<ol><li><ol><li>orfana</li></ol></li></ol>",
      FluidRichTextHtml.write(doc),
    )
    assertEquals(doc, FluidRichTextHtml.read(FluidRichTextHtml.write(doc)))
    assertEquals(2, FluidRichTextHtml.Version)
  }

  @Test fun readsNestedListsAsMailClientsWriteThem() {
    // Gmail: sotto-elenco dentro il <li>; alcuni editor: <ul> direttamente nel <ul>.
    val gmail = FluidRichTextHtml.read(
      "<div dir=\"ltr\"><ul><li>a</li><li>b<ul><li>b1</li><li>b2<ol><li>n</li></ol></li></ul></li><li>c</li></ul></div>",
    )
    assertEquals("a\nb\nb1\nb2\nn\nc", gmail.text)
    assertEquals(
      listOf(style(Bullet), style(Bullet), style(Bullet, Bullet), style(Bullet, Bullet), style(Bullet, Bullet, Numbered), style(Bullet)),
      gmail.paragraphStyles,
    )
    val direct = FluidRichTextHtml.read("<ul><li>a</li><ul><li>b</li></ul><li>c</li></ul>")
    assertEquals(listOf(style(Bullet), style(Bullet, Bullet), style(Bullet)), direct.paragraphStyles)
    val quotes = FluidRichTextHtml.read("<blockquote>a<blockquote>b<ul><li>c</li></ul></blockquote>d</blockquote>")
    assertEquals(listOf(style(Quote), style(Quote, Quote), style(Quote, Quote, Bullet), style(Quote)), quotes.paragraphStyles)
    // Una voce fuori da un elenco, anche in una citazione, resta una voce; il suo </li> non chiude altro.
    val loose = FluidRichTextHtml.read("<blockquote><li>x<ul><li>y</li><li>z</li></ul></li></blockquote>")
    assertEquals(listOf(style(Quote, Bullet), style(Quote, Bullet, Bullet), style(Quote, Bullet, Bullet)), loose.paragraphStyles)
  }

  @Test fun versionOneHtmlReadsTheSame() {
    val v1 = "<div>Ciao</div><ul><li>uno</li><li>due</li></ul><ol><li>tre</li></ol>" +
      "<blockquote><div>citato</div><ol><li>voce citata</li></ol></blockquote>"
    val doc = FluidRichTextHtml.read(v1)
    assertEquals(
      listOf(Plain, style(Bullet), style(Bullet), style(Numbered), style(Quote), style(Quote, Numbered)),
      doc.paragraphStyles,
    )
    // Il dialetto 2 scrive un documento a un livello esattamente come il dialetto 1.
    assertEquals(v1, FluidRichTextHtml.write(doc))
  }

  @Test fun hostileDeepNestingIsCappedAndFast() {
    val inputs = listOf(
      "<blockquote>".repeat(100_000) + "profondo" + "</blockquote>".repeat(100_000) + "dopo",
      "<ul><li>".repeat(60_000) + "voce" + "</li></ul>".repeat(60_000),
      "<ol>".repeat(80_000) + "x" + "</ul>".repeat(80_000) + "<br>".repeat(20_000),
      "<blockquote><ul><li>".repeat(30_000) + "y" + "</blockquote>".repeat(10) + "</li>".repeat(30_000) + "z",
    )
    val started = System.nanoTime()
    for (input in inputs) {
      val doc = FluidRichTextHtml.read(input)
      assertTrue(doc.paragraphStyles.all { it.depth <= FluidRichTextParagraphStyle.MaxDepth })
      assertEquals(doc.paragraphCount, doc.paragraphStyles.size)
      // Il risultato si riscrive e si rilegge uguale: niente di cio' che e' entrato e' fuori dal modello.
      assertEquals(doc, FluidRichTextHtml.read(FluidRichTextHtml.write(doc)))
    }
    val deep = FluidRichTextHtml.read(inputs[1])
    assertEquals("voce", deep.text)
    assertEquals(FluidRichTextListKind.Bullet, deep.paragraphStyles.single().list)
    assertEquals(FluidRichTextParagraphStyle.MaxDepth, deep.paragraphStyles.single().depth)
    val quoted = FluidRichTextHtml.read(inputs[0])
    assertEquals("profondo\ndopo", quoted.text)
    assertEquals(Plain, quoted.paragraphStyles[1])
    val seconds = (System.nanoTime() - started) / 1e9
    assertTrue("troppo lento: $seconds s", seconds < 20)
  }

  @Test fun htmlRoundTripIsExactWithRandomNesting() {
    val random = java.util.Random(21)
    repeat(500) {
      val doc = randomNestedDocument(random)
      val html = FluidRichTextHtml.write(doc)
      assertEquals("HTML: $html", doc, FluidRichTextHtml.read(html))
      assertEquals(html, FluidRichTextHtml.write(FluidRichTextHtml.read(html)))
    }
  }

  // --- Markdown e testo ----------------------------------------------------------------------------

  @Test fun writesNestedMarkdownWithCommonMarkIndents() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet)) { append("uno") }
      paragraph(style(Bullet, Bullet)) { append("sotto") }
      paragraph(style(Bullet, Bullet, Numbered)) { append("numero") }
      paragraph(style(Bullet, Quote)) { append("citato nella voce") }
      paragraph(style(Bullet)) { append("- due") }
      paragraph(style(Numbered)) { append("primo") }
      paragraph(style(Numbered)) { append("secondo") }
      paragraph(style(Numbered, Numbered)) { append("sotto") }
      paragraph(style(Quote)) { append("q") }
      paragraph(style(Quote, Quote)) { append("qq") }
      paragraph(style(Quote, Bullet)) { append("voce citata") }
      paragraph(style(Quote, Bullet, Bullet)) { append("sotto-voce citata") }
      paragraph(style(Bullet, Bullet)) { append("orfana") }
      paragraph(Plain) { append("  rientrato") }
    }
    val markdown = FluidRichTextMarkdown.write(doc)
    assertEquals(
      listOf(
        "- uno",
        "  - sotto",
        "    1. numero",
        "  > citato nella voce",
        "- \\- due",
        "1. primo",
        "2. secondo",
        "   1. sotto",
        "> q",
        "> > qq",
        "> - voce citata",
        ">   - sotto-voce citata",
        "- * orfana",
        "  rientrato",
      ).joinToString("\n"),
      markdown,
    )
    assertEquals(doc, FluidRichTextMarkdown.read(markdown))
  }

  @Test fun emptyDeepItemsAreNotReadAsHorizontalRules() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet, Bullet, Bullet)) { }
      paragraph(style(Bullet, Quote, Bullet, Bullet, Bullet)) { }
    }
    val markdown = FluidRichTextMarkdown.write(doc)
    assertEquals("- * - \n  > - * - ", markdown)
    assertEquals(doc.paragraphStyles, FluidRichTextMarkdown.read(markdown).paragraphStyles)
    // Una linea vera resta una linea (un paragrafo vuoto), non tre voci.
    assertEquals(Plain, FluidRichTextMarkdown.read("- - -").paragraphStyles.single())
  }

  @Test fun readsForeignNestedMarkdown() {
    val doc = FluidRichTextMarkdown.read(
      listOf(
        "* a",
        "    * quattro spazi",
        "\t* tab",
        "        1) otto spazi",
        "* b",
        "> > > tre citazioni",
        "- > citazione in una voce",
        "",
        "  testo rientrato",
      ).joinToString("\n"),
    )
    assertEquals(
      listOf(
        style(Bullet),
        style(Bullet, Bullet),
        style(Bullet, Bullet),
        style(Bullet, Bullet, Numbered),
        style(Bullet),
        style(Quote, Quote, Quote),
        style(Bullet, Quote),
        Plain,
        Plain,
      ),
      doc.paragraphStyles,
    )
    assertEquals("  testo rientrato", doc.text.split('\n').last())
  }

  @Test fun markdownRoundTripWithRandomNesting() {
    val random = java.util.Random(5)
    repeat(500) {
      val doc = randomNestedDocument(random)
      val markdown = FluidRichTextMarkdown.write(doc)
      val back = FluidRichTextMarkdown.read(markdown)
      assertEquals("MD: $markdown", doc.text, back.text)
      assertEquals("MD: $markdown", doc.paragraphStyles, back.paragraphStyles)
      for (index in doc.text.indices) {
        if (doc.text[index].isWhitespace()) continue
        assertEquals("MD: $markdown @ $index", doc.formatAt(index), back.formatAt(index))
      }
      // E da li' all'HTML e ritorno la struttura non cambia.
      assertEquals(doc.paragraphStyles, FluidRichTextHtml.read(FluidRichTextHtml.write(back)).paragraphStyles)
    }
  }

  @Test fun plainTextShowsReadableIndents() {
    val doc = buildFluidRichText {
      paragraph(style(Bullet)) { append("uno") }
      paragraph(style(Bullet, Bullet)) { append("due") }
      paragraph(style(Bullet, Bullet, Bullet)) { append("tre") }
      paragraph(style(Bullet, Bullet, Bullet, Bullet)) { append("quattro") }
      paragraph(style(Numbered)) { append("primo") }
      paragraph(style(Numbered, Numbered)) { append("sotto") }
      paragraph(style(Numbered)) { append("secondo") }
      paragraph(style(Quote, Quote)) { append("qq") }
      paragraph(style(Bullet, Bullet)) { append("orfana") }
    }
    assertEquals(
      listOf(
        "• uno",
        "  ◦ due",
        "    ▪ tre",
        "      • quattro",
        "1. primo",
        "   1. sotto",
        "2. secondo",
        "> > qq",
        "  ◦ orfana",
      ).joinToString("\n"),
      doc.toPlainText(),
    )
  }

  // --- disegno -------------------------------------------------------------------------------------

  @Test fun indentGeometryHasAVisualCap() {
    val deep = FluidRichTextParagraphStyle(listOf(Quote, Bullet, Bullet, Quote, Bullet, Numbered))
    val doc = buildFluidRichText {
      paragraph(style(Quote, Quote)) { append("q") }
      paragraph(deep) { append("profondo") }
    }
    val free = containerOffsetsEm(deep, indentScale(doc, Float.POSITIVE_INFINITY))
    assertEquals(1f + 1.6f * 4 + 1f, free.last(), 0.001f)
    val scale = indentScale(doc, 4f)
    val capped = containerOffsetsEm(deep, scale)
    assertEquals(4f, capped.last(), 0.001f)
    // Il contenitore piu' interno (il posto del segno) resta intero; gli altri si stringono ma restano in ordine.
    assertEquals(FluidRichTextEditorDefaults.ListIndentEm, capped[6] - capped[5], 0.001f)
    assertTrue((0 until 6).all { capped[it + 1] > capped[it] })
    // Una scala sola per il documento: le barre della citazione interna restano in colonna.
    assertEquals(containerOffsetsEm(style(Quote, Quote), scale)[1], capped[1], 0f)
    assertEquals(0f, containerOffsetsEm(Plain, scale).single(), 0f)
    // Sotto il tetto niente cambia.
    assertEquals(1f, indentScale(buildFluidRichText { paragraph(style(Quote, Bullet)) { } }, 4f), 0f)
    // Ogni livello resta piu' rientrato di quello sopra di un passo uguale, anche oltre il tetto.
    val stairs = buildFluidRichText {
      for (depth in 1..FluidRichTextParagraphStyle.MaxDepth) paragraph(FluidRichTextParagraphStyle(List(depth) { Bullet })) { }
    }
    val stairsScale = indentScale(stairs, 4.5f)
    val starts = stairs.paragraphStyles.map { containerOffsetsEm(it, stairsScale).last() }
    val steps = starts.zipWithNext { a, b -> b - a }
    assertTrue(starts.toString(), steps.all { abs(it - steps.first()) < 0.001f && it > 0.5f })
    assertEquals(4.5f, starts.last(), 0.001f)
  }

  @Test fun bulletMarkersChangePerLevel() {
    assertEquals(listOf("•", "◦", "▪", "•"), (1..4).map { bulletMarker(it) })
  }
}

/** Un documento a caso con pile di contenitori che salgono e scendono come in un testo vero. */
internal fun randomNestedDocument(random: java.util.Random): FluidRichTextDocument {
  val base = randomDocument(random)
  val kinds = FluidRichTextBlock.entries
  var current = emptyList<FluidRichTextBlock>()
  val styles = List(base.paragraphCount) {
    current = when (random.nextInt(5)) {
      0 -> if (current.size < FluidRichTextParagraphStyle.MaxDepth) current + kinds[random.nextInt(kinds.size)] else current
      1 -> current.dropLast(1)
      2 -> if (current.isEmpty()) current else current.dropLast(1) + kinds[random.nextInt(kinds.size)]
      3 -> List(random.nextInt(FluidRichTextParagraphStyle.MaxDepth + 1)) { kinds[random.nextInt(kinds.size)] }
      else -> current
    }
    FluidRichTextParagraphStyle(current)
  }
  return FluidRichTextDocument(base.text, base.runs, styles)
}
