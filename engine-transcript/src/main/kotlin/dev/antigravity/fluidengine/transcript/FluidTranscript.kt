package dev.antigravity.fluidengine.transcript

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ui.fluid.FluidSpokenWord

/**
 * Un tratto di testo con il suo tempo: una frase, un segmento del riconoscitore. [start] e [end] sono caratteri del testo
 * del paragrafo (fine esclusa), [startMillis] e [endMillis] tempi dell'audio.
 */
@Immutable
data class FluidTranscriptSpan(
  val start: Int,
  val end: Int,
  val startMillis: Long,
  val endMillis: Long,
)

/**
 * Un **paragrafo** della trascrizione (F8.13, idea `ascolto-dal-paragrafo`): il testo e i suoi tempi, a tre livelli, dal
 * più fine al più grosso.
 *
 * - [words]: i tempi **per parola** (`FluidSpokenWord` di `engine-ui`, caratteri e tempi). Con loro la parola che si sente
 *   si accende nel testo e un tocco su una parola salta **a quella parola**.
 * - [segments]: i tempi **per frase** (un riconoscitore che non dà le parole, o le dà male). Un tocco salta all'inizio
 *   della frase toccata.
 * - [startMillis]: il tempo **del paragrafo**. Un tocco sull'ora del paragrafo salta qui; un paragrafo senza parole né
 *   frasi salta qui anche dal testo.
 *
 * Senza nessun tempo il paragrafo si legge e basta: il tocco non fa niente (la scheda dell'idea: «se mancano i tempi si
 * legge il testo e si disattiva il salto»).
 *
 * La nota di Alessio: Pampa Notes ha già una versione fatta «non molto bene», in cui il tocco salta all'inizio della frase
 * anche quando ci sono i tempi per parola (`SessionScreen.kt:1305`, `segmentAt(offset).sessionStartMs`). Qui la parola
 * vince sempre sulla frase, e la frase sul paragrafo ([FluidTranscriptSeek]).
 *
 * @param key una chiave stabile (l'id del paragrafo nell'app), per la lista; di serie l'indice.
 * @param speaker chi parla («Voce 2», un nome), mostrato accanto all'ora.
 */
@Immutable
data class FluidTranscriptParagraph(
  val text: String,
  val startMillis: Long? = null,
  val endMillis: Long? = null,
  val words: List<FluidSpokenWord> = emptyList(),
  val segments: List<FluidTranscriptSpan> = emptyList(),
  val speaker: String? = null,
  val key: Any? = null,
) {
  /** L'inizio vero: quello dato, o la prima parola, o la prima frase. */
  val start: Long? get() = startMillis ?: words.firstOrNull()?.startMs ?: segments.firstOrNull()?.startMillis

  /** La fine vera: quella data, o l'ultima parola, o l'ultima frase. */
  val end: Long? get() = endMillis ?: words.lastOrNull()?.endMs ?: segments.lastOrNull()?.endMillis

  /** Vero se il paragrafo ha almeno un tempo: si può toccare per ascoltare. */
  val hasTimes: Boolean get() = start != null
}

/**
 * Una **trascrizione**: i paragrafi in ordine di tempo, con le domande che la vista fa a ogni battito («quale paragrafo
 * si sente», «quale parola») risolte con una ricerca binaria. Si costruisce una volta (con [fromSegments], o dai paragrafi
 * dell'app) e si tiene: cambia solo quando cambia il testo.
 */
@Immutable
class FluidTranscript(val paragraphs: List<FluidTranscriptParagraph>) {

  /** Gli inizi dei paragrafi, per la ricerca; un paragrafo senza tempi eredita quello di prima. */
  private val starts: LongArray = LongArray(paragraphs.size).also { array ->
    var last = Long.MIN_VALUE
    paragraphs.forEachIndexed { i, paragraph ->
      val start = paragraph.start
      if (start != null && start > last) last = start
      array[i] = last
    }
  }

  /** Vero se almeno un paragrafo ha i tempi. */
  val hasTimes: Boolean = paragraphs.any { it.hasTimes }

  /** Vero se almeno un paragrafo ha i tempi per parola. */
  val hasWordTimes: Boolean = paragraphs.any { it.words.isNotEmpty() }

  /**
   * L'indice del paragrafo che si sente a [positionMillis]: l'ultimo cominciato. -1 prima del primo, o senza tempi. Una
   * pausa fra due paragrafi resta sul paragrafo di prima (la pagina non salta avanti nel silenzio).
   */
  fun paragraphAt(positionMillis: Long): Int {
    if (!hasTimes || paragraphs.isEmpty()) return -1
    // Un paragrafo senza tempi non «si sente»: si torna al primo con i tempi prima di lui.
    var found = lastStartedAtOrBefore(starts.size, { starts[it] }, positionMillis)
    while (found >= 0 && !paragraphs[found].hasTimes) found--
    return found
  }

  /**
   * Il carattere fin dove il paragrafo [index] è stato detto a [positionMillis]: l'inizio della parola che si sente (o
   * della frase, senza parole). -1 se il paragrafo non ha tempi fini. È il punto che la vista tiene in vista.
   */
  fun spokenOffset(index: Int, positionMillis: Long): Int {
    val paragraph = paragraphs.getOrNull(index) ?: return -1
    if (paragraph.words.isNotEmpty()) {
      val word = lastStartedAtOrBefore(paragraph.words.size, { paragraph.words[it].startMs }, positionMillis)
      return if (word < 0) 0 else paragraph.words[word].start
    }
    if (paragraph.segments.isNotEmpty()) {
      val segment = lastStartedAtOrBefore(paragraph.segments.size, { paragraph.segments[it].startMillis }, positionMillis)
      return if (segment < 0) 0 else paragraph.segments[segment].start
    }
    return -1
  }

  companion object {
    /** La trascrizione vuota. */
    val Empty: FluidTranscript = FluidTranscript(emptyList())

    /**
     * I paragrafi dai **segmenti** di un riconoscitore (una frase per segmento, con o senza le parole): il testo dei
     * segmenti si unisce con uno spazio, e si va a capo
     *  - a una **pausa** di almeno [paragraphBreakMillis] fra un segmento e il successivo (2 s di serie: è la pausa di chi
     *    cambia argomento in una lezione; a occhio, 25 novembre 2026, come `TranscriptParagraphs` di Pampa Notes);
     *  - dopo [maxSegmentsPerParagraph] segmenti, perché un paragrafo alto dieci schermi non si legge;
     *  - quando cambia chi parla.
     *
     * Le parole di un segmento si cercano nel suo testo in ordine (la punteggiatura del testo resta); una parola che non si
     * trova si salta, senza spostare le altre.
     */
    fun fromSegments(
      segments: List<FluidTimedSegment>,
      paragraphBreakMillis: Long = 2_000L,
      maxSegmentsPerParagraph: Int = 6,
    ): FluidTranscript {
      val out = ArrayList<FluidTranscriptParagraph>()
      var text = StringBuilder()
      var words = ArrayList<FluidSpokenWord>()
      var spans = ArrayList<FluidTranscriptSpan>()
      var count = 0
      var previous: FluidTimedSegment? = null
      fun flush() {
        if (count == 0) return
        out += FluidTranscriptParagraph(
          text = text.toString(),
          startMillis = spans.first().startMillis,
          endMillis = spans.last().endMillis,
          words = words,
          segments = spans,
          speaker = previous?.speaker,
        )
        text = StringBuilder()
        words = ArrayList()
        spans = ArrayList()
        count = 0
      }
      for (segment in segments) {
        val clean = segment.text.trim()
        if (clean.isEmpty()) continue
        val last = previous
        if (last != null && (segment.startMillis - last.endMillis >= paragraphBreakMillis ||
            count >= maxSegmentsPerParagraph || segment.speaker != last.speaker)
        ) {
          flush()
        }
        if (text.isNotEmpty()) text.append(' ')
        val base = text.length
        text.append(clean)
        spans += FluidTranscriptSpan(base, base + clean.length, segment.startMillis, segment.endMillis)
        var cursor = 0
        for (word in segment.words) {
          val needle = word.text.trim()
          if (needle.isEmpty()) continue
          val found = clean.indexOf(needle, cursor, ignoreCase = true)
          if (found < 0) continue
          words += FluidSpokenWord(base + found, base + found + needle.length, word.startMillis, word.endMillis)
          cursor = found + needle.length
        }
        count++
        previous = segment
      }
      flush()
      return FluidTranscript(out)
    }
  }
}

/** Un segmento di un riconoscitore: il testo, i suoi tempi, le parole se ci sono, chi parla. */
@Immutable
data class FluidTimedSegment(
  val text: String,
  val startMillis: Long,
  val endMillis: Long,
  val words: List<FluidTimedWord> = emptyList(),
  val speaker: String? = null,
)

/** Una parola con i suoi tempi, come la dà un riconoscitore. */
@Immutable
data class FluidTimedWord(val text: String, val startMillis: Long, val endMillis: Long)

/**
 * Il **salto** (F8.13): dove porta un tocco sul testo di un paragrafo. È la regola, fuori dalla vista, perché l'app possa
 * usarla anche altrove (un risultato di ricerca, un segnalibro).
 */
object FluidTranscriptSeek {

  /**
   * Dove saltare per un tocco sul carattere [offset] di [paragraph] (l'indice del carattere toccato, non un punto di
   * inserimento: la vista lo calcola dal dito):
   *  1. con i tempi per parola, **la parola toccata** (il tocco sullo spazio dopo una parola va alla parola dopo, nella
   *     stessa frase; la punteggiatura in fondo resta sull'ultima);
   *  2. in una frase senza parole (o senza parole in tutto il paragrafo), l'inizio **della frase toccata**;
   *  3. senza nessuno dei due, l'inizio **del paragrafo**;
   *  4. senza tempi, null: niente salto.
   */
  fun target(paragraph: FluidTranscriptParagraph, offset: Int): Long? {
    val segments = paragraph.segments
    val segmentIndex = if (segments.isEmpty()) -1 else lastStartedAtOrBefore(segments.size, { segments[it].start.toLong() }, offset.toLong())
    val segment = segments.getOrNull(segmentIndex.coerceAtLeast(0))
    val words = paragraph.words
    if (words.isNotEmpty()) {
      val index = lastStartedAtOrBefore(words.size, { words[it].start.toLong() }, offset.toLong())
      if (index < 0) {
        if (segment == null || words.first().start < segment.end) return words.first().startMs
      } else {
        val word = words[index]
        // La parola conta solo se sta nella stessa frase del tocco: una frase senza parole (il riconoscitore non le ha
        // date) salta al suo inizio, non all'ultima parola della frase prima.
        val sameSegment = segment == null || word.start >= segment.start
        if (sameSegment) {
          // Dentro la parola: lei. La fine e' esclusa (R3): il carattere `end` e' gia' lo spazio dopo.
          if (offset < word.end) return word.startMs
          // Nello spazio dopo: la prossima, se e' nella stessa frase; se no (la punteggiatura in fondo) l'ultima.
          val next = words.getOrNull(index + 1)
          if (next != null && (segment == null || next.start < segment.end)) return next.startMs
          return word.startMs
        }
      }
    }
    if (segment != null) return segment.startMillis
    return paragraph.start
  }

  /** Dove saltare per un tocco sull'ora (o su TalkBack, «Ascolta da qui»): l'inizio del paragrafo. */
  fun paragraphStart(paragraph: FluidTranscriptParagraph): Long? = paragraph.start
}

/** L'ultimo indice in `0 until size` con `key(i) <= value`, o -1. Le chiavi sono in ordine. */
internal inline fun lastStartedAtOrBefore(size: Int, key: (Int) -> Long, value: Long): Int {
  var low = 0
  var high = size - 1
  var found = -1
  while (low <= high) {
    val middle = (low + high) ushr 1
    if (key(middle) <= value) {
      found = middle
      low = middle + 1
    } else {
      high = middle - 1
    }
  }
  return found
}
