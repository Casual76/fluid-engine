package dev.antigravity.fluidengine.richtext

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I difetti di `engine-richtext` trovati dalla review R2 (muse-f8-prime, 49-54 e i minori): una prova per
 * ognuno, rossa sul codice di prima. Le prove di costo non misurano un tempo assoluto: confrontano un
 * ingresso con uno [Factor] volte piu' grande. Lineare vuol dire circa [Factor] volte il tempo, quadratico
 * il quadrato; il tetto sta in mezzo, cosi' un PC carico non fa cadere la prova.
 */
class FluidRichTextR2Test {
  private val bullet = FluidRichTextParagraphStyle(list = FluidRichTextListKind.Bullet)

  // --- 49: un paragrafo che comincia con ~~~ -------------------------------------------------------

  @Test fun aParagraphStartingWithTildesSurvivesMarkdown() {
    val doc = buildFluidRichText {
      paragraph { append("~~~ non e' codice") }
      paragraph { append("   ~~~x") }
      paragraph { bold("forte") }
      paragraph(bullet) { append("voce") }
      paragraph { append("~~~") }
      paragraph { italic("ultima") }
    }
    val markdown = FluidRichTextMarkdown.write(doc)
    val back = FluidRichTextMarkdown.read(markdown)
    assertEquals("MD: $markdown", doc.text, back.text)
    assertEquals("MD: $markdown", doc.paragraphStyles, back.paragraphStyles)
    assertTrue("MD: $markdown", back.formatAt(back.text.indexOf("forte")).bold)
    assertTrue("MD: $markdown", back.formatAt(back.text.indexOf("ultima")).italic)
  }

  @Test fun aTildeLineInsideAListItemContinuationSurvivesMarkdown() {
    // Un paragrafo dentro la voce (rientrato sotto il suo testo) che comincia con ~~~.
    val doc = buildFluidRichText {
      paragraph(FluidRichTextParagraphStyle.of(FluidRichTextBlock.Bullet, FluidRichTextBlock.Quote)) { append("~~~ citato") }
      paragraph { bold("dopo") }
    }
    val markdown = FluidRichTextMarkdown.write(doc)
    val back = FluidRichTextMarkdown.read(markdown)
    assertEquals("MD: $markdown", doc.text, back.text)
    assertTrue("MD: $markdown", back.formatAt(back.text.indexOf("dopo")).bold)
  }

  // --- 50: salvare o copiare un documento grande ---------------------------------------------------

  /** [paragraphs] paragrafi, ognuno con un tratto in grassetto, uno su tre voce di elenco, uno su sette con un link. */
  private fun longDocument(paragraphs: Int): FluidRichTextDocument = buildFluidRichText {
    repeat(paragraphs) { index ->
      paragraph(if (index % 3 == 0) bullet else FluidRichTextParagraphStyle.Plain) {
        append("voce $index ")
        bold("forte")
        if (index % 7 == 0) link(" sito", "https://esempio.it/$index")
      }
    }
  }

  @Test fun writingHtmlGrowsLinearly() =
    assertLinear("FluidRichTextHtml.write", small = 1_500, prepare = ::longDocument) { FluidRichTextHtml.write(it) }

  @Test fun writingMarkdownGrowsLinearly() =
    assertLinear("FluidRichTextMarkdown.write", small = 1_500, prepare = ::longDocument) { FluidRichTextMarkdown.write(it) }

  @Test fun writingPlainTextGrowsLinearly() =
    assertLinear("FluidRichTextPlainText.write", small = 1_500, prepare = ::longDocument) { FluidRichTextPlainText.write(it) }

  @Test fun copyingASelectionGrowsLinearly() =
    // La copia: il pezzo selezionato (quasi tutto) e il suo HTML, come fa l'editor per gli appunti.
    assertLinear("slice + write", small = 1_500, prepare = ::longDocument) {
      FluidRichTextHtml.write(it.slice(3, it.text.length - 3))
    }

  // --- 54: una riga Markdown ostile ---------------------------------------------------------------

  private fun hostileLine(unit: String): (Int) -> String = { length -> unit.repeat(length / unit.length) }

  @Test fun hostileEmphasisRunsReadLinearly() {
    assertLinear("*a ", small = 8_000, prepare = hostileLine("*a ")) { FluidRichTextMarkdown.read(it) }
    assertLinear("**a ", small = 8_000, prepare = hostileLine("**a ")) { FluidRichTextMarkdown.read(it) }
    assertLinear("_a ", small = 8_000, prepare = hostileLine("_a ")) { FluidRichTextMarkdown.read(it) }
  }

  @Test fun hostileBracketsReadLinearly() {
    assertLinear("[a", small = 8_000, prepare = hostileLine("[a")) { FluidRichTextMarkdown.read(it) }
    assertLinear("[a](", small = 8_000, prepare = hostileLine("[a](")) { FluidRichTextMarkdown.read(it) }
  }

  @Test fun hostileCodeAndAutolinksReadLinearly() {
    assertLinear("`a`", small = 8_000, prepare = hostileLine("`a`")) { FluidRichTextMarkdown.read(it) }
    assertLinear("<a", small = 8_000, prepare = hostileLine("<a")) { FluidRichTextMarkdown.read(it) }
  }

  @Test fun theFastReaderReadsLikeTheOldOne() {
    // Il lettore di prima (copiato in MarkdownReferenceReader, con la sola correzione degli spazi del
    // codice) e' l'oracolo: righe casuali fatte dei segni che contano, con barre rovesciate e chiusure lontane.
    val tokens = listOf("*", "**", "***", "_", "__", "[", "]", "(", ")", "`", "``", "<", ">", "\\", " ", "a", "b", "https://a.it", "\\*", "\\\\", "x_y", "\t")
    val random = java.util.Random(54)
    repeat(4_000) {
      val line = buildString {
        append("x ")
        repeat(1 + random.nextInt(24)) { append(tokens[random.nextInt(tokens.size)]) }
      }
      assertEquals("riga: $line", referenceMarkdownLine(line), FluidRichTextMarkdown.read(line))
    }
  }

  // --- minori: Markdown ----------------------------------------------------------------------------

  @Test fun aLongerFenceIsNotClosedByAShorterOne() {
    val doc = FluidRichTextMarkdown.read("````\n```\n**a**\n````\n**b**")
    assertEquals("```\n**a**\nb", doc.text)
    assertTrue(doc.spans.none { it.start < doc.paragraphStart(2) })
    assertTrue(doc.formatAt(doc.text.indexOf('b')).bold)
  }

  @Test fun inlineCodeKeepsItsInnerSpaces() {
    assertEquals("a  x  b", FluidRichTextMarkdown.read("a `  x  ` b").text)
    assertEquals(" x ", FluidRichTextMarkdown.read("`  x  `").text)
    assertEquals("x", FluidRichTextMarkdown.read("` x `").text)
    assertEquals("`", FluidRichTextMarkdown.read("`` ` ``").text)
    assertEquals("  ", FluidRichTextMarkdown.read("`  `").text)
    assertEquals(" x", FluidRichTextMarkdown.read("` x`").text)
  }

  // --- minori: HTML --------------------------------------------------------------------------------

  @Test fun noBreakSpaceAndSoftHyphenSurviveHtml() {
    val doc = buildFluidRichText {
      paragraph { append("10 km e caff­fe") }
      paragraph { bold("a  b") }
    }
    val html = FluidRichTextHtml.write(doc)
    assertEquals("HTML: $html", doc, FluidRichTextHtml.read(html))
    // Le spaziature dell'HTML di fuori restano spazi normali: `&nbsp;` e' come lo scrivono tutti.
    assertEquals("a  b", FluidRichTextHtml.read("a&nbsp;&nbsp;b").text)
  }

  @Test fun quotesInsideAnIgnoredElementDoNotLeakItsContent() {
    assertEquals("dopo", FluidRichTextHtml.read("<svg><a title=\"></svg>\">testo</a></svg>dopo").text)
    assertEquals("dopo", FluidRichTextHtml.read("<svg><g a='>'><text>x</text></g></svg>dopo").text)
    // Un `<` di testo dentro l'elemento non e' un tag: la chiusura dopo conta.
    assertEquals("dopo", FluidRichTextHtml.read("<svg>1 < 2</svg>dopo").text)
  }

  @Test fun xmpAndPlaintextShowTheirTextLikeABrowser() {
    assertEquals("prima\nesempio <b>x</b> &amp;\ndopo", FluidRichTextHtml.read("prima<xmp>esempio <b>x</b> &amp;</xmp>dopo").text)
    val plaintext = FluidRichTextHtml.read("<p>prima</p><plaintext>ciao <i>a</i>\nriga</plaintext>")
    assertEquals("prima\nciao <i>a</i>\nriga</plaintext>", plaintext.text)
    assertTrue(plaintext.spans.isEmpty())
  }

  // --- minori: il documento ------------------------------------------------------------------------

  @Test fun toStringDoesNotCarryTextOrAddresses() {
    val doc = buildFluidRichText {
      paragraph { append("Il mio codice segreto e' 4242 ") }
      paragraph { link("qui", "https://privato.example/token=abc") }
    }
    val printed = doc.toString()
    assertFalse(printed, printed.contains("segreto"))
    assertFalse(printed, printed.contains("4242"))
    assertFalse(printed, printed.contains("privato"))
    assertFalse(printed, printed.contains("qui"))
    assertTrue(printed, printed.contains(doc.text.length.toString()))
  }

  // --- minori: lo stato salvato --------------------------------------------------------------------

  private fun saveAndRestore(state: FluidRichTextState): FluidRichTextState {
    val saved = with(FluidRichTextState.Saver) { SaverScope { true }.save(state) }!!
    return FluidRichTextState.Saver.restore(saved)!!
  }

  @Test fun aPendingStyleSurvivesTheRestore() {
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("ciao "), TextRange(5))
    state.toggle(FluidRichTextInlineStyle.Bold)
    assertTrue(state.format.bold)
    val restored = saveAndRestore(state)
    assertNotNull("il grassetto in sospeso", restored.pending)
    assertTrue(restored.format.bold)
    assertEquals(state.pending, restored.pending)
  }

  @Test fun anOpenLinkSheetSurvivesTheRestore() {
    val doc = buildFluidRichText { paragraph { append("vai al "); link("sito", "https://esempio.it") } }
    val state = FluidRichTextState(doc, TextRange(7, 11))
    state.openLinkEditor()
    val request = state.linkRequest
    assertNotNull(request)
    val restored = saveAndRestore(state)
    assertEquals(request, restored.linkRequest)
  }

  @Test fun theLinkSheetDraftsSurviveTheRestoreAndAStaleSheetDoesNot() {
    val doc = buildFluidRichText { paragraph { append("vai al "); link("sito", "https://esempio.it") } }
    val state = FluidRichTextState(doc, TextRange(7, 11))
    state.openLinkEditor()
    assertEquals("sito", state.linkDraftText)
    assertEquals("https://esempio.it", state.linkDraftAddress)
    state.linkDraftText = "il mio sito"
    state.linkDraftAddress = "altro.it"
    val restored = saveAndRestore(state)
    assertEquals("il mio sito", restored.linkDraftText)
    assertEquals("altro.it", restored.linkDraftAddress)
    // Il documento cambia col foglio aperto: il foglio non parla piu' di queste parole.
    state.setDocument(FluidRichTextDocument.fromPlainText("vai al SITO"))
    assertFalse(state.isLinkRequestCurrent(state.linkRequest!!))
    state.setDocument(FluidRichTextDocument.fromPlainText("corto"))
    assertFalse(state.isLinkRequestCurrent(state.linkRequest!!))
    assertEquals(null, saveAndRestore(state).linkRequest)
  }

  // --- 51: l'incolla grande, fuori dal thread principale --------------------------------------------

  /** Un dispatcher a mano: i compiti aspettano finche' la prova non li fa girare. */
  private class Queue : kotlinx.coroutines.CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
      tasks.addLast(block)
    }

    fun runAll() {
      while (tasks.isNotEmpty()) tasks.removeFirst().run()
    }
  }

  @Test fun aBackgroundPasteReadsOnTheWorkerAndLandsOnTheMainThread() {
    val main = Queue()
    val worker = Queue()
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("prima "), TextRange(6))
    var read = false
    state.pasteInBackground(kotlinx.coroutines.CoroutineScope(main), worker) {
      read = true
      FluidRichTextHtml.read("<b>forte</b>")
    }
    main.runAll()
    assertFalse("la lettura aspetta il suo thread", read)
    assertEquals("prima ", state.document.text)
    worker.runAll()
    assertTrue(read)
    assertEquals("il documento cambia solo sul thread principale", "prima ", state.document.text)
    main.runAll()
    assertEquals("prima forte", state.document.text)
    assertTrue(state.document.formatAt(6).bold)
    assertEquals(FluidRichTextChangeSource.Paste, state.lastChangeSource)
  }

  @Test fun aBackgroundPasteIsDroppedIfTheDocumentOrTheCursorMoved() {
    for (move in listOf<(FluidRichTextState) -> Unit>({ it.setDocument(FluidRichTextDocument.fromPlainText("altro")) }, { it.select(TextRange(2)) })) {
      val main = Queue()
      val worker = Queue()
      val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("prima "), TextRange(6))
      state.pasteInBackground(kotlinx.coroutines.CoroutineScope(main), worker) { FluidRichTextHtml.read("<b>forte</b>") }
      main.runAll()
      worker.runAll()
      move(state)
      val before = state.document
      main.runAll()
      assertEquals(before, state.document)
    }
  }

  // --- minori: setLink su piu' paragrafi -----------------------------------------------------------

  @Test fun setLinkOnSeveralParagraphsLinksWithoutReplacing() {
    // Il limite dichiarato nel KDoc: su piu' paragrafi il testo resta e diventa link.
    val state = FluidRichTextState(FluidRichTextDocument.fromPlainText("uno\ndue"), TextRange(0, 7))
    assertTrue(state.setLink("esempio.it", "altro"))
    assertEquals("uno\ndue", state.document.text)
    assertEquals("https://esempio.it", state.document.formatAt(0).link)
    assertEquals("https://esempio.it", state.document.formatAt(4).link)
  }

  // --- il banco della crescita ---------------------------------------------------------------------

  private fun <T> assertLinear(what: String, small: Int, prepare: (Int) -> T, work: (T) -> Unit) {
    val smallInput = prepare(small)
    val largeInput = prepare(small * Factor)
    // F7.32 · Scaldati tutti e due, e il migliore di cinque per tutti e due: prima l'ingresso grande non si scaldava e ne
    // aveva tre, e una pausa del PC carico (la compilazione del JIT, il raccoglitore) nelle tre bastava a spostare il
    // rapporto. Si misura ancora la crescita, non i millisecondi.
    repeat(2) {
      work(smallInput)
      work(largeInput)
    }
    val smallBest = (1..5).minOf { timed { work(smallInput) } }.coerceAtLeast(1L)
    val largeBest = (1..5).minOf { timed { work(largeInput) } }
    val ratio = largeBest.toDouble() / smallBest
    println("R2 crescita | $what | ${smallBest / 1000} us -> ${largeBest / 1000} us | x${"%.1f".format(ratio)} per x$Factor")
    assertTrue(
      "$what: x$Factor di ingresso costa x${"%.1f".format(ratio)} (${smallBest / 1000} us -> ${largeBest / 1000} us): " +
        "lineare e' circa x$Factor, quadratico x${Factor * Factor}",
      ratio < MaxRatio,
    )
  }

  private inline fun timed(block: () -> Unit): Long {
    val start = System.nanoTime()
    block()
    return System.nanoTime() - start
  }

  private companion object {
    const val Factor = 8
    const val MaxRatio = 24.0
  }
}
