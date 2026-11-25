package dev.antigravity.fluidengine.revisions

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.foundation.text.FluidTextDiff
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Una versione da mettere a confronto: una revisione di un elemento, come l'app la sa raccontare.
 *
 * L'engine non sa leggere i dati (con `engine-sync` viaggiano sigillati): il testo, il nome del dispositivo e l'autore li
 * da' l'app, che ha aperto le due versioni.
 *
 * @param id l'identificativo della revisione; torna nella scelta ([FluidVersionDecision]). Con `engine-sync` e'
 *   `SyncVersion.revision`.
 * @param source da dove viene: il dispositivo («Questo telefono», «Tablet di Alessio»).
 * @param timeMillis quando e' stata scritta.
 * @param text l'anteprima: il testo della versione (o una sua descrizione per un dato che non e' testo).
 * @param author chi l'ha scritta, in uno spazio condiviso fra persone («Giulia»); null per i propri dispositivi.
 * @param current vero per la versione che l'app mostra adesso.
 */
@Immutable
class FluidVersion(
  val id: String,
  val source: String,
  val timeMillis: Long,
  val text: String,
  val author: String? = null,
  val current: Boolean = false,
) {
  override fun equals(other: Any?): Boolean =
    other is FluidVersion && other.id == id && other.source == source && other.timeMillis == timeMillis &&
      other.text == text && other.author == author && other.current == current

  override fun hashCode(): Int = id.hashCode()

  override fun toString(): String = "FluidVersion($id, $source)"
}

/**
 * Il confronto da mostrare: l'elemento ([title], «Lista della gita») e le sue [versions], di solito due. [key] e'
 * l'identita' del confronto (l'elemento e il campo): se le revisioni cambiano con la stessa chiave, il foglio dice che e'
 * arrivata una versione nuova e chiede di scegliere di nuovo.
 */
@Immutable
class FluidVersionChoice(
  val title: String,
  val versions: List<FluidVersion>,
  val key: Any = title,
) {
  init {
    require(versions.size >= 2) { "un confronto ha almeno due versioni" }
    require(versions.map { it.id }.toSet().size == versions.size) { "revisioni ripetute" }
  }

  /** Gli identificativi delle revisioni, nell'ordine. */
  val ids: List<String> get() = versions.map { it.id }

  override fun equals(other: Any?): Boolean =
    other is FluidVersionChoice && other.title == title && other.versions == versions && other.key == key

  override fun hashCode(): Int = versions.hashCode()
}

/**
 * La scelta: le revisioni da tenere ([keep], una o piu'; con piu' d'una, «tieni tutte e due») e quelle che il foglio
 * mostrava quando si e' scelto ([shown]). L'app salva l'esito in modo atomico e controlla che [shown] sia ancora il
 * confronto attuale; con `engine-sync`: `space.resolve(record, field, keep, shown)`, che risponde «superata» se nel
 * frattempo e' arrivata una revisione nuova.
 */
class FluidVersionDecision(val keep: List<String>, val shown: List<String>) {
  /** Vero se si tengono tutte le versioni mostrate. */
  val keepsAll: Boolean get() = keep.size > 1 && keep.toSet() == shown.toSet()

  override fun equals(other: Any?): Boolean = other is FluidVersionDecision && other.keep == keep && other.shown == shown

  override fun hashCode(): Int = keep.hashCode() * 31 + shown.hashCode()

  override fun toString(): String = "FluidVersionDecision(tieni $keep di $shown)"
}

/**
 * Un estratto di una versione da mostrare nella card: il [text] (gia' tagliato attorno al primo passaggio diverso), i
 * tratti diversi dalle altre versioni ([changed], indici in [text]), e se il testo continua prima o dopo.
 * [compared] e' falso quando il confronto ha superato il tetto di lavoro: allora l'estratto e' l'inizio del testo, senza
 * segni (il ripiego su due estratti).
 */
@Immutable
data class FluidVersionExcerpt(
  val text: String,
  val changed: List<IntRange>,
  val clippedStart: Boolean,
  val clippedEnd: Boolean,
  val compared: Boolean,
)

/**
 * I passaggi diversi: per ogni versione, un estratto con i tratti che le altre non hanno. Ogni versione si confronta con
 * quella attuale (o la prima); quella attuale porta segnati i tratti che le altre hanno cambiato. Funzione pura, da
 * chiamare fuori dal thread principale per i testi lunghi (il foglio lo fa da solo).
 *
 * @param maxChars quanti caratteri al massimo per estratto: 280, a occhio (24/11/2026), circa sei righe del foglio sul
 *   telefono.
 * @param maxEdits il tetto del confronto ([FluidTextDiff.DefaultMaxEdits]); oltre, due estratti senza segni.
 */
fun fluidVersionExcerpts(
  versions: List<FluidVersion>,
  maxChars: Int = 280,
  maxEdits: Int = FluidTextDiff.DefaultMaxEdits,
): Map<String, FluidVersionExcerpt> {
  if (versions.isEmpty()) return emptyMap()
  val reference = versions.firstOrNull { it.current } ?: versions.first()
  val refTokens = FluidTextDiff.words(reference.text)
  val refChanged = ArrayList<IntRange>()
  val out = LinkedHashMap<String, FluidVersionExcerpt>()
  var allCompared = true
  for (v in versions) {
    if (v === reference) continue
    val tokens = FluidTextDiff.words(v.text)
    val diff = FluidTextDiff.diff(refTokens, tokens, maxEdits)
    if (diff == null) {
      allCompared = false
      out[v.id] = clip(v.text, emptyList(), maxChars, compared = false)
      continue
    }
    val mine = ArrayList<IntRange>()
    for (hunk in diff.hunks) {
      charRange(tokens, hunk.bStart, hunk.bEnd)?.let(mine::add)
      charRange(refTokens, hunk.aStart, hunk.aEnd)?.let(refChanged::add)
    }
    out[v.id] = clip(v.text, mine, maxChars, compared = true)
  }
  val ordered = LinkedHashMap<String, FluidVersionExcerpt>()
  for (v in versions) {
    ordered[v.id] = if (v === reference) {
      clip(reference.text, mergeRanges(refChanged), maxChars, compared = allCompared)
    } else {
      out.getValue(v.id)
    }
  }
  return ordered
}

private fun charRange(tokens: List<String>, from: Int, to: Int): IntRange? {
  if (from >= to) return null
  var start = 0
  for (i in 0 until from) start += tokens[i].length
  var end = start
  for (i in from until to) end += tokens[i].length
  // Uno spazio da solo non e' un passaggio: si segnano parole, non buchi.
  val text = tokens.subList(from, to).joinToString("")
  if (text.isBlank()) return null
  return start until end
}

private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
  if (ranges.isEmpty()) return ranges
  val sorted = ranges.sortedBy { it.first }
  val out = ArrayList<IntRange>()
  var current = sorted.first()
  for (r in sorted.drop(1)) {
    current = if (r.first <= current.last + 1) current.first..maxOf(current.last, r.last) else {
      out += current
      r
    }
  }
  out += current
  return out
}

/** L'estratto attorno al primo tratto diverso: un po' di contesto prima, fino a [maxChars], ai confini delle parole. */
private fun clip(text: String, changed: List<IntRange>, maxChars: Int, compared: Boolean): FluidVersionExcerpt {
  if (text.length <= maxChars) return FluidVersionExcerpt(text, changed, false, false, compared)
  val first = changed.firstOrNull()?.first ?: 0
  var start = (first - maxChars / 4).coerceAtLeast(0)
  if (start > 0) {
    val space = text.lastIndexOf(' ', start).let { if (it < 0) text.lastIndexOf('\n', start) else it }
    start = if (space in 0 until first) space + 1 else start
  }
  var end = (start + maxChars).coerceAtMost(text.length)
  if (end < text.length) {
    val space = text.lastIndexOf(' ', end)
    if (space > start + maxChars / 2) end = space
  }
  val slice = text.substring(start, end)
  val shifted = changed.mapNotNull { r ->
    val s = (r.first - start).coerceAtLeast(0)
    val e = (r.last - start).coerceAtMost(slice.length - 1)
    if (e < s || r.last < start || r.first >= end) null else s..e
  }
  return FluidVersionExcerpt(slice, shifted, start > 0, end < text.length, compared)
}

/**
 * L'ora di una versione come la dice il foglio: «Oggi, 14:32», «Ieri, 9:10», «21 nov, 18:05», «21 nov 2025, 18:05».
 * Pura, con il giorno di oggi dato ([today]).
 */
fun fluidVersionTime(
  millis: Long,
  zone: ZoneId,
  locale: Locale,
  today: LocalDate,
  labels: FluidVersionLabels = FluidVersionLabels(),
): String {
  val at = Instant.ofEpochMilli(millis).atZone(zone)
  val time = DateTimeFormatter.ofPattern("H:mm", locale).format(at)
  val day = at.toLocalDate()
  return when (day) {
    today -> labels.today(time)
    today.minusDays(1) -> labels.yesterday(time)
    else -> {
      val pattern = if (day.year == today.year) "d MMM" else "d MMM yyyy"
      labels.onDay(DateTimeFormatter.ofPattern(pattern, locale).format(at).replace(".", ""), time)
    }
  }
}
