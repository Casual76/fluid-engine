package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidFindHighlights
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.fluidTruncateFileName
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * F6C.2: i modelli dei preset senza Compose UI. Il testo grande letto a pezzi, «trova» riga per riga con le regole di
 * rememberFluidFindState, gli evidenziatori su un pezzo di riga, lo sconosciuto (eseguibile, dimensione, dump) e la
 * regola che fa partire un'immagine animata. Le prove con il dito e la semantica sono in FluidViewerPresetsUiTest del
 * sample; le tracce del banco in FluidViewerPresetTraceTest.
 */
class FluidViewerPresetsTest {

  /** Un file di [total] righe che conta le letture e quante righe gli si chiedono. */
  private class Lines(val total: Int, override val lineCount: Int? = null, val failAt: Int? = null) : FluidTextSource {
    val reads = mutableListOf<Pair<Int, Int>>()
    var failures = 0

    override suspend fun readLines(start: Int, count: Int): List<String> {
      reads += start to count
      if (failAt != null && start >= failAt && failures == 0) {
        failures++
        error("disco staccato")
      }
      return (start until minOf(total, start + count)).map { "riga ${it + 1}" }
    }
  }

  // --- Il testo grande a pezzi ------------------------------------------------------------------------------------

  @Test fun aBigFileIsReadOneChunkAtATimeAndNeverAllAtOnce() = runTest {
    val source = Lines(total = 1_000_000)
    val doc = TextDocument(source, chunk = 400)
    assertTrue(doc.loadMore())
    assertEquals(listOf(0 to 400), source.reads)
    assertEquals(400, doc.lines.size)
    assertEquals("riga 1", doc.lines.first().text)
    // Una riga lontana costa le letture fin lì, a pezzi della stessa misura; il resto del file non si tocca.
    doc.loadThrough(1_000)
    assertEquals(listOf(0 to 400, 400 to 400, 800 to 400), source.reads)
    assertEquals(1_200, doc.lines.size)
    assertFalse(doc.ended)
    assertTrue(source.reads.all { it.second == 400 })
  }

  @Test fun aShortChunkOrTheKnownCountEndsTheFile() = runTest {
    val short = TextDocument(Lines(total = 950), chunk = 400)
    short.loadAll()
    assertTrue(short.ended)
    assertEquals(950, short.lines.size)
    assertFalse(short.loadMore())
    // Con il conteggio noto non serve la lettura vuota in più.
    val exact = Lines(total = 800, lineCount = 800)
    val counted = TextDocument(exact, chunk = 400)
    counted.loadAll()
    assertTrue(counted.ended)
    assertEquals(2, exact.reads.size)
    // Un file vuoto è finito alla prima lettura.
    val empty = TextDocument(Lines(total = 0), chunk = 400)
    assertFalse(empty.loadMore())
    assertTrue(empty.ended)
    assertTrue(empty.lines.isEmpty())
  }

  @Test fun aFailedReadStopsAndRetryReadsTheSameChunkAgain() = runTest {
    val source = Lines(total = 2_000, failAt = 400)
    val doc = TextDocument(source, chunk = 400)
    doc.loadAll()
    assertNotNull(doc.failure)
    assertEquals(400, doc.lines.size)
    assertFalse(doc.loadMore())
    doc.retry()
    doc.loadAll()
    assertNull(doc.failure)
    assertTrue(doc.ended)
    assertEquals(2_000, doc.lines.size)
    assertEquals(listOf(400 to 400, 400 to 400), source.reads.filter { it.first == 400 })
  }

  @Test fun theColoursOfTheDecoderAreUsedWhenTheSourceHasThem() = runTest {
    val red = SpanStyle(color = Color.Red)
    val source = object : FluidStyledTextSource {
      override val language = "kotlin"
      override suspend fun readLines(start: Int, count: Int) = error("il preset deve chiedere le righe con i colori")
      override suspend fun readStyledLines(start: Int, count: Int) =
        if (start > 0) emptyList() else listOf(buildAnnotatedString { pushStyle(red); append("val"); pop(); append(" x = 1") })
    }
    val doc = TextDocument(source, chunk = 400)
    doc.loadAll()
    assertEquals("val x = 1", doc.lines.single().text)
    assertEquals(red, doc.lines.single().spanStyles.single().item)
    // I ruoli (la forma consigliata a engine-viewer) diventano gli stili del tema; un ruolo sconosciuto resta com'è.
    val roles = buildAnnotatedString {
      pushStringAnnotation(FluidTextPresetDefaults.SyntaxAnnotation, "keyword"); append("fun"); pop()
      append(" ")
      pushStringAnnotation(FluidTextPresetDefaults.SyntaxAnnotation, "boh"); append("f"); pop()
    }
    val blue = SpanStyle(color = Color.Blue)
    val styled = fluidSyntaxStyled(roles, mapOf("keyword" to blue))
    assertEquals(listOf(blue), styled.spanStyles.map { it.item })
    assertEquals(0 to 3, styled.spanStyles.single().let { it.start to it.end })
    assertSame(roles, fluidSyntaxStyled(roles, emptyMap()))
    assertTrue("keyword" in FluidTextPresetDefaults.SyntaxRoles && "comment" in FluidTextPresetDefaults.SyntaxRoles)
  }

  @Test fun aVeryLongLineIsSplitIntoRowsWithoutBreakingASurrogatePair() {
    assertEquals(listOf(TextRow(4, 0, 0)), fluidTextRows(4, "", 2_000))
    assertEquals(listOf(TextRow(0, 0, 5)), fluidTextRows(0, "corta", 2_000))
    val rows = fluidTextRows(7, "a".repeat(4_500), 2_000)
    assertEquals(listOf(TextRow(7, 0, 2_000), TextRow(7, 2_000, 4_000), TextRow(7, 4_000, 4_500)), rows)
    assertTrue(rows.first().first)
    assertFalse(rows[1].first)
    // Un'emoji a cavallo del taglio resta intera nel pezzo dopo.
    val emoji = "a".repeat(3) + "😀" + "b"
    val split = fluidTextRows(0, emoji, 4)
    assertEquals(listOf(TextRow(0, 0, 3), TextRow(0, 3, 6)), split)
    // Trovare il pezzo che contiene un carattere.
    val all = fluidTextRows(0, "x", 10) + rows + fluidTextRows(8, "y", 10)
    assertEquals(0, fluidTextRowOf(all, 0, 0))
    assertEquals(2, fluidTextRowOf(all, 7, 2_500))
    assertEquals(3, fluidTextRowOf(all, 7, 4_000))
    assertEquals(4, fluidTextRowOf(all, 8, 0))
    assertEquals(-1, fluidTextRowOf(all, 5, 0))
  }

  @Test fun theDocumentKeepsTheWidestRowForTheSidewaysExtent() = runTest {
    val source = object : FluidTextSource {
      override suspend fun readLines(start: Int, count: Int) =
        if (start > 0) emptyList() else listOf("ab", "x".repeat(5_000), "abc")
    }
    val doc = TextDocument(source, chunk = 400, segment = 2_000)
    doc.loadAll()
    assertEquals(3, doc.lines.size)
    assertEquals(5, doc.rows.size)
    assertEquals(2_000, doc.longest)
  }

  // --- «Trova» ------------------------------------------------------------------------------------------------------

  private val text = listOf(
    "Il lago Tahoe è un lago d'acqua dolce",
    "fra la California e il Nevada.",
    "D'inverno il LAGO è freddo; perché?",
  )

  @Test fun findIgnoresCaseAndAccentsAndCountsZeroOneAndMany() {
    assertEquals(listOf(TextMatch(0, 3, 7), TextMatch(0, 19, 23), TextMatch(2, 13, 17)), fluidTextMatches(text, "lago"))
    assertEquals(listOf(TextMatch(2, 28, 34)), fluidTextMatches(text, "perche"))
    assertEquals(listOf(TextMatch(1, 7, 17)), fluidTextMatches(text, "california"))
    assertTrue(fluidTextMatches(text, "zzz").isEmpty())
    assertTrue(fluidTextMatches(text, "  ").isEmpty())
    // Le occorrenze non si sovrappongono, come in rememberFluidFindState; e le righe partono da firstLine.
    assertEquals(listOf(TextMatch(10, 0, 2), TextMatch(10, 2, 4)), fluidTextMatches(listOf("aaaa"), "aa", firstLine = 10))
  }

  @Test fun findWalksTheResultsAroundAndKeepsTheCurrentNearWhereItWas() {
    val find = TextFind.of()
    find.onQueryChange("lago")
    find.replace(fluidTextMatches(text, find.query))
    assertEquals(3, find.total)
    assertEquals(0, find.current)
    assertEquals(TextMatch(0, 3, 7), find.request!!.match)
    find.next()
    find.next()
    assertEquals(2, find.current)
    find.next()
    assertEquals(0, find.current)
    find.previous()
    assertEquals(2, find.current)
    assertEquals(2, find.navigation.current)
    assertEquals(3, find.navigation.total)
    val serial = find.request!!.serial
    // Una lettera in più non torna all'inizio: la prima occorrenza da dove stava la corrente.
    find.onQueryChange("lago ")
    find.replace(fluidTextMatches(text, find.query))
    assertEquals(listOf(TextMatch(0, 3, 8), TextMatch(0, 19, 24), TextMatch(2, 13, 18)), find.matches)
    assertEquals(2, find.current)
    assertTrue(find.request!!.serial > serial)
    // Nessuna occorrenza: frecce e invio non fanno niente.
    find.onQueryChange("zzz")
    find.replace(fluidTextMatches(text, find.query))
    val before = find.request
    find.next()
    find.previous()
    assertEquals(0, find.total)
    assertSame(before, find.request)
    // Le righe che arrivano dopo aggiungono occorrenze senza spostare la corrente; le prime la portano in vista.
    find.onQueryChange("nevada")
    find.replace(emptyList())
    find.append(listOf(TextMatch(1, 23, 29)))
    assertEquals(1, find.total)
    assertEquals(TextMatch(1, 23, 29), find.request!!.match)
    find.append(listOf(TextMatch(40, 0, 6)))
    assertEquals(0, find.current)
    assertEquals(2, find.total)
  }

  @Test fun theHighlightsLandOnThePieceOfTheLineTheyBelongTo() {
    val current = SpanStyle(background = Color.Yellow)
    val others = SpanStyle(background = Color.Gray)
    val highlights = FluidFindHighlights(current, others)
    val line = AnnotatedString(text[0])
    val matches = fluidTextMatches(text, "lago")
    val whole = fluidTextRowText(line, TextRow(0, 0, line.length), matches, current = 1, highlights = highlights)
    assertEquals(listOf(others to (3 to 7), current to (19 to 23)), whole.spanStyles.map { it.item to (it.start to it.end) })
    // Un pezzo che taglia un'occorrenza ne evidenzia solo la sua parte, con le coordinate del pezzo.
    val piece = fluidTextRowText(line, TextRow(0, 5, 21), matches, current = 1, highlights = highlights)
    assertEquals(line.text.substring(5, 21), piece.text)
    assertEquals(listOf(others to (0 to 2), current to (14 to 16)), piece.spanStyles.map { it.item to (it.start to it.end) })
    // Senza «trova» aperto il testo resta quello del decodificatore.
    assertTrue(fluidTextRowText(line, TextRow(0, 0, line.length), matches, 0, highlights = null).spanStyles.isEmpty())
    // La riga 2 vede solo le sue.
    val third = fluidTextRowText(AnnotatedString(text[2]), TextRow(2, 0, text[2].length), matches, current = 2, highlights)
    assertEquals(listOf(current to (13 to 17)), third.spanStyles.map { it.item to (it.start to it.end) })
  }

  @Test fun findInPageIsTheF6b6PresetWithTheNavigationOfTheFile() {
    val find = TextFind.of()
    find.onQueryChange("lago")
    find.replace(fluidTextMatches(text, find.query))
    var open = true
    val spec = FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, active = true, onActiveChange = { open = it })
    assertEquals(3, spec.navigation!!.total)
    assertFalse(spec.showsAtRest)
    spec.navigation!!.onNext()
    assertEquals(1, find.current)
    spec.onActiveChange!!(false)
    assertFalse(open)
  }

  // --- Lo sconosciuto -----------------------------------------------------------------------------------------------

  @Test fun anExecutableIsNeverDowngradedAndTheDetailsSayTypeAndSize() {
    val strings = FluidUnknownPresetStrings()
    // Un nome innocuo con un MIME eseguibile, e un .apk con un MIME generico: eseguibili tutti e due.
    assertEquals(FluidFileKind.Executable, FluidFileInfo("foto.png", "application/vnd.android.package-archive").kind)
    assertEquals(FluidFileKind.Executable, FluidFileInfo("aggiornamento.apk", "application/octet-stream").kind)
    assertEquals(FluidFileKind.Other, FluidFileInfo("dati.bin").kind)
    assertEquals("File · Dimensione sconosciuta", fluidUnknownDetails(FluidFileInfo("dati.bin"), "File", strings))
    assertEquals("File · File vuoto", fluidUnknownDetails(FluidFileInfo("vuoto.bin", sizeBytes = 0), "File", strings))
    assertEquals("File · 12,4 MB", fluidUnknownDetails(FluidFileInfo("x.bin", sizeBytes = 12_400_000), "File", strings, Locale.ITALY))
    assertEquals("File · Dimensione sconosciuta", fluidUnknownDetails(FluidFileInfo("x.bin", sizeBytes = -1), "File", strings))
  }

  @Test fun aLongNameKeepsItsRealExtension() {
    val name = "Relazione finale del progetto di fisica, versione corretta.tar.gz"
    val cut = fluidTruncateFileName(name, 20)
    assertTrue(cut, cut.endsWith("….gz"))
    assertEquals(20, cut.codePointCount(0, cut.length))
  }

  @Test fun theHexDumpIsOnlyTheFirstKilobyteWithPrintableCharacters() {
    val bytes = "PK\u0003\u0004ciao".toByteArray(Charsets.ISO_8859_1) + ByteArray(2_000) { 0x41 }
    val dump = fluidHexDump(bytes)
    assertEquals(1024 / 8, dump.size)
    assertEquals("00000000  50 4B 03 04 63 69 61 6F  PK..ciao", dump.first())
    assertEquals("000003F8  41 41 41 41 41 41 41 41  AAAAAAAA", dump.last())
    // Una riga corta allinea i caratteri sotto quelli delle altre.
    val short = fluidHexDump(byteArrayOf(0x00, 0x7F, 0x20), perRow = 8)
    assertEquals(listOf("00000000  00 7F 20 " + " ".repeat(5 * 3) + " " + ".. "), short)
    assertTrue(fluidHexDump(ByteArray(0)).isEmpty())
  }

  @Test fun anOpenWithThatFailsBecomesAMessageAndNotACrash() {
    var failed = 0
    fluidRunFileAction({ throw IllegalStateException("nessuna app") }) { failed++ }
    fluidRunFileAction({ }) { failed++ }
    assertEquals(1, failed)
  }

  // --- L'immagine animata -------------------------------------------------------------------------------------------

  @Test fun anAnimatedImagePlaysOnlyOnTheSettledPageAndWaitsWithReducedMotion() {
    assertTrue(animatedImagePlays(animatable = true, active = true, reducedMotion = false, requested = null))
    assertFalse(animatedImagePlays(animatable = true, active = false, reducedMotion = false, requested = null))
    assertFalse(animatedImagePlays(animatable = false, active = true, reducedMotion = false, requested = null))
    // Movimento ridotto: ferma, finché TalkBack non chiede di riprodurla; e la pausa chiesta resta pausa.
    assertFalse(animatedImagePlays(animatable = true, active = true, reducedMotion = true, requested = null))
    assertTrue(animatedImagePlays(animatable = true, active = true, reducedMotion = true, requested = true))
    assertFalse(animatedImagePlays(animatable = true, active = true, reducedMotion = false, requested = false))
    assertFalse(animatedImagePlays(animatable = true, active = false, reducedMotion = true, requested = true))
  }
}
