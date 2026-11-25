package dev.antigravity.fluidengine.transcript

import dev.antigravity.fluidengine.ui.fluid.FluidSpokenWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.13: il contratto dei tempi e del salto, e i paragrafi dai segmenti. */
class FluidTranscriptTest {

  // «Oggi parliamo del ciclo.» con una parola ogni 400 ms da 1 s; la frase intera da 1 s a 2,6 s.
  private val text = "Oggi parliamo del ciclo."
  private val words = listOf(
    FluidSpokenWord(0, 4, 1_000, 1_400),
    FluidSpokenWord(5, 13, 1_400, 1_800),
    FluidSpokenWord(14, 17, 1_800, 2_200),
    FluidSpokenWord(18, 23, 2_200, 2_600),
  )
  private val withWords = FluidTranscriptParagraph(text, startMillis = 900, words = words, segments = listOf(FluidTranscriptSpan(0, 24, 1_000, 2_600)))

  @Test
  fun aTapOnAWordGoesToThatWordNotToTheSentence() {
    // Il difetto di Pampa Notes: il tocco su «ciclo» saltava all'inizio della frase (1 s). Qui va a «ciclo».
    assertEquals(2_200L, FluidTranscriptSeek.target(withWords, 20))
    assertEquals(1_000L, FluidTranscriptSeek.target(withWords, 0))
    assertEquals(1_400L, FluidTranscriptSeek.target(withWords, 9))
    // L'ultimo carattere della parola e' ancora lei; lo spazio dopo e' la parola dopo.
    assertEquals(1_000L, FluidTranscriptSeek.target(withWords, 4))
    assertEquals(1_400L, FluidTranscriptSeek.target(withWords, 5))
    // Il punto finale resta sull'ultima parola.
    assertEquals(2_200L, FluidTranscriptSeek.target(withWords, 24))
  }

  @Test
  fun withoutWordsASentenceAndWithoutSentencesTheParagraph() {
    val sentences = FluidTranscriptParagraph(
      "Prima frase. Seconda frase.",
      segments = listOf(FluidTranscriptSpan(0, 12, 5_000, 6_000), FluidTranscriptSpan(13, 27, 6_500, 8_000)),
    )
    assertEquals(6_500L, FluidTranscriptSeek.target(sentences, 20))
    assertEquals(5_000L, FluidTranscriptSeek.target(sentences, 3))
    assertEquals(5_000L, sentences.start)
    val paragraphOnly = FluidTranscriptParagraph("Solo il paragrafo.", startMillis = 12_000)
    assertEquals(12_000L, FluidTranscriptSeek.target(paragraphOnly, 7))
    val none = FluidTranscriptParagraph("Senza tempi.")
    assertNull(FluidTranscriptSeek.target(none, 3))
    assertFalse(none.hasTimes)
  }

  @Test
  fun theTimeLabelGoesToTheParagraphStart() {
    assertEquals(900L, FluidTranscriptSeek.paragraphStart(withWords))
  }

  @Test
  fun theParagraphThatIsHeardStaysThroughThePauseAfterIt() {
    val transcript = FluidTranscript(
      listOf(
        withWords,
        FluidTranscriptParagraph("Secondo.", startMillis = 5_000, endMillis = 6_000),
        FluidTranscriptParagraph("Senza tempi, si legge."),
        FluidTranscriptParagraph("Quarto.", startMillis = 9_000),
      ),
    )
    assertEquals(-1, transcript.paragraphAt(500))
    assertEquals(0, transcript.paragraphAt(1_000))
    // Nella pausa fra il primo (finito a 2,6 s) e il secondo (5 s) si resta sul primo: la pagina non salta avanti.
    assertEquals(0, transcript.paragraphAt(4_000))
    assertEquals(1, transcript.paragraphAt(5_000))
    // Un paragrafo senza tempi non «si sente»: resta quello prima.
    assertEquals(1, transcript.paragraphAt(8_000))
    assertEquals(3, transcript.paragraphAt(9_500))
    assertTrue(transcript.hasWordTimes)
  }

  @Test
  fun spokenOffsetIsTheWordBeingHeard() {
    val transcript = FluidTranscript(listOf(withWords))
    assertEquals(0, transcript.spokenOffset(0, 500))
    assertEquals(5, transcript.spokenOffset(0, 1_500))
    assertEquals(18, transcript.spokenOffset(0, 9_999))
    assertEquals(-1, FluidTranscript(listOf(FluidTranscriptParagraph("x", startMillis = 0))).spokenOffset(0, 10))
  }

  @Test
  fun paragraphsFromSegmentsBreakAtPausesAndKeepWordRanges() {
    val segments = listOf(
      FluidTimedSegment("Oggi parliamo dell'acqua.", 0, 1_600, words = listOf(
        FluidTimedWord("Oggi", 0, 300), FluidTimedWord("parliamo", 300, 800), FluidTimedWord("dell'acqua", 800, 1_600),
      )),
      FluidTimedSegment("Il sole la scalda.", 1_800, 3_000),
      // Pausa di 2,5 s: nuovo paragrafo.
      FluidTimedSegment("Poi piove.", 5_500, 6_400, words = listOf(FluidTimedWord("Poi", 5_500, 5_800), FluidTimedWord("piove", 5_800, 6_400))),
      FluidTimedSegment("  ", 6_500, 6_600),
      FluidTimedSegment("Fine.", 6_700, 7_000, speaker = "Voce 2"),
    )
    val transcript = FluidTranscript.fromSegments(segments)
    assertEquals(3, transcript.paragraphs.size)
    val first = transcript.paragraphs[0]
    assertEquals("Oggi parliamo dell'acqua. Il sole la scalda.", first.text)
    assertEquals(0L, first.startMillis)
    assertEquals(3_000L, first.endMillis)
    assertEquals(3, first.words.size)
    assertEquals("dell'acqua", first.text.substring(first.words[2].start, first.words[2].end))
    assertEquals(2, first.segments.size)
    assertEquals("Il sole la scalda.", first.text.substring(first.segments[1].start, first.segments[1].end))
    // Un tocco su «scalda» (frase senza parole) va all'inizio della frase; su «acqua» alla parola.
    assertEquals(1_800L, FluidTranscriptSeek.target(first, first.text.indexOf("scalda")))
    assertEquals(800L, FluidTranscriptSeek.target(first, first.text.indexOf("acqua")))
    assertEquals("Poi piove.", transcript.paragraphs[1].text)
    assertEquals("Voce 2", transcript.paragraphs[2].speaker)
  }

  @Test
  fun aLongTalkIsSplitEverySixSentences() {
    val segments = (0 until 13).map { FluidTimedSegment("Frase $it.", it * 1_000L, it * 1_000L + 800L) }
    val transcript = FluidTranscript.fromSegments(segments)
    assertEquals(listOf(6, 6, 1), transcript.paragraphs.map { it.segments.size })
  }
}
