package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import java.text.Normalizer

/**
 * Gli evidenziatori di «trova» (F6B.6): la corrispondenza corrente e le altre.
 *
 * @property current lo stile dell'occorrenza su cui sta il contatore («3» di «3/12»).
 * @property others lo stile delle altre occorrenze.
 */
@Immutable
data class FluidFindHighlights(val current: SpanStyle, val others: SpanStyle)

/** I valori di serie di «trova nella pagina». */
object FluidFindDefaults {
  /**
   * Il giallo della corrispondenza corrente: quello della demo di `findNavigator` (WWDC25 231, 17:34, F6B.0 §4), il
   * giallo di sistema di Apple. Lo stesso nei due temi, con il testo nero sopra, come in Apple.
   */
  val CurrentHighlight: Color = Color(0xFFFFD60A)

  /**
   * Gli stili di serie: la corrente gialla col testo nero; le altre con un velo dell'inchiostro della pagina al 16%, che
   * in Apple è «evidenziata chiara» (F6B.0 §4). Un'app che ha già un suo giallo ne passa uno suo.
   */
  @Composable
  fun highlights(): FluidFindHighlights = FluidFindHighlights(
    current = SpanStyle(background = CurrentHighlight, color = Color.Black),
    others = SpanStyle(background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)),
  )
}

/**
 * La richiesta di portare in vista un'occorrenza: la fa «trova» quando cambia la corrente (la parola, le frecce,
 * l'invio). [serial] cresce a ogni richiesta, anche verso la stessa occorrenza: l'app ci appoggia un
 * `LaunchedEffect(request)` e scorre.
 *
 * @property range i caratteri dell'occorrenza nel testo.
 * @property index la sua posizione fra le occorrenze, da zero.
 */
@Immutable
class FluidFindScrollRequest internal constructor(val range: TextRange, val index: Int, val serial: Int) {
  /**
   * Il riquadro dell'occorrenza nel testo impaginato (in pixel, nelle coordinate del testo): la riga del primo
   * carattere. Il bordo alto è quello da portare in vista.
   */
  fun boundsIn(layout: TextLayoutResult): Rect {
    val length = layout.layoutInput.text.length
    if (length == 0) return Rect.Zero
    val start = range.start.coerceIn(0, length - 1)
    val end = (range.end - 1).coerceIn(start, length - 1)
    val first = layout.getBoundingBox(start)
    val last = layout.getBoundingBox(end)
    return if (layout.getLineForOffset(start) == layout.getLineForOffset(end)) Rect(first.left, first.top, last.right, last.bottom)
      else first
  }
}

/**
 * «Trova nella pagina» su un testo semplice (F6B.6), il punto 6 del piano F6B: trova le occorrenze della parola in una
 * `String` o in un `AnnotatedString` e dà ciò che serve al campo e alla pagina.
 *
 * - [navigation] va dritta in [FluidSearchSpec.findInPage]: il contatore «3/12», le frecce, l'invio che passa alla
 *   successiva. Alla fine si ricomincia dall'inizio, e dall'inizio si torna alla fine.
 * - [highlight] scrive gli evidenziatori sul testo: la corrente gialla, le altre velate.
 * - [scrollRequest] dice quale occorrenza portare in vista, a ogni cambio della corrente.
 *
 * Il confronto non guarda maiuscole né accenti («perche» trova «Perché»), come in Apple; le occorrenze non si
 * sovrappongono. Le app con contenuti complessi (una trascrizione a righe, un documento a pagine) calcolano da sé le
 * occorrenze e passano a [FluidSearchSpec.findInPage] una [FluidSearchNavigation] loro.
 *
 * La parola e la corrente sopravvivono alla ricreazione dell'attività ([rememberFluidFindState] le salva).
 */
@Stable
class FluidFindState internal constructor(
  private val queryState: MutableState<String>,
  private val currentState: MutableState<Int>,
  text: AnnotatedString,
  ignoreCase: Boolean,
  ignoreDiacritics: Boolean,
) {
  private var source by mutableStateOf(text)
  private var foldCase by mutableStateOf(ignoreCase)
  private var foldMarks by mutableStateOf(ignoreDiacritics)
  private var serial = 0

  /** Il testo in cui si cerca, l'ultimo passato a [rememberFluidFindState]. */
  val text: AnnotatedString get() = source

  /** La parola cercata. */
  val query: String get() = queryState.value

  private val folded: State<String> = derivedStateOf { fluidFindFold(source.text, foldCase, foldMarks) }

  /** Le occorrenze, nell'ordine del testo. Vuota con la parola vuota. */
  val matches: List<TextRange> by derivedStateOf { fluidFindMatchesFolded(folded.value, queryState.value, foldCase, foldMarks) }

  /** Quante sono. */
  val total: Int get() = matches.size

  /** La corrente, da zero; zero anche senza occorrenze. Se il testo si è accorciato, l'ultima. */
  val current: Int get() = if (total == 0) 0 else currentState.value.coerceIn(0, total - 1)

  /** I caratteri della corrente, o null senza occorrenze. */
  val currentRange: TextRange? get() = matches.getOrNull(current)

  /** L'ultima richiesta di portare in vista la corrente; null finché non ce n'è stata una. */
  var scrollRequest: FluidFindScrollRequest? by mutableStateOf(null)
    private set

  /** Il contatore e le frecce per il campo: la stessa istanza finché corrente e totale non cambiano. */
  val navigation: FluidSearchNavigation by derivedStateOf {
    FluidSearchNavigation(current = current, total = total, onPrevious = ::previous, onNext = ::next)
  }

  /**
   * La parola nuova, dal campo. La corrente resta vicina a dov'era: la prima occorrenza della parola nuova che comincia
   * dove cominciava la corrente o dopo (scrivere una lettera in più non fa tornare all'inizio), altrimenti la prima.
   */
  fun onQueryChange(query: String) {
    if (query == queryState.value) return
    val from = currentRange?.start ?: 0
    queryState.value = query
    val found = matches
    currentState.value = found.indexOfFirst { it.start >= from }.takeIf { it >= 0 } ?: 0
    request()
  }

  /** La successiva; dall'ultima si torna alla prima. Senza occorrenze non fa niente. */
  fun next() {
    val count = total
    if (count == 0) return
    currentState.value = (current + 1) % count
    request()
  }

  /** La precedente; dalla prima si va all'ultima. Senza occorrenze non fa niente. */
  fun previous() {
    val count = total
    if (count == 0) return
    currentState.value = (current - 1 + count) % count
    request()
  }

  /** Il testo con gli evidenziatori: [highlights] di serie sono quelli di [FluidFindDefaults.highlights]. */
  fun highlight(highlights: FluidFindHighlights): AnnotatedString =
    fluidFindHighlight(source, matches, if (total == 0) -1 else current, highlights)

  internal fun update(text: AnnotatedString, ignoreCase: Boolean, ignoreDiacritics: Boolean) {
    // Come rememberUpdatedState: una scrittura in composizione che non fa niente se il valore è lo stesso.
    if (source != text) source = text
    if (foldCase != ignoreCase) foldCase = ignoreCase
    if (foldMarks != ignoreDiacritics) foldMarks = ignoreDiacritics
  }

  private fun request() {
    val range = currentRange ?: return
    scrollRequest = FluidFindScrollRequest(range, current, ++serial)
  }

  internal companion object {
    /** Uno stato fuori dalla composizione, per le prove e per chi ne tiene uno suo. */
    fun of(text: AnnotatedString, query: String = "", ignoreCase: Boolean = true, ignoreDiacritics: Boolean = true) =
      FluidFindState(mutableStateOf(query), mutableStateOf(0), text, ignoreCase, ignoreDiacritics)
  }
}

/** [FluidFindState] su un testo semplice. */
@Composable
fun rememberFluidFindState(
  text: String,
  ignoreCase: Boolean = true,
  ignoreDiacritics: Boolean = true,
): FluidFindState = rememberFluidFindState(remember(text) { AnnotatedString(text) }, ignoreCase, ignoreDiacritics)

/**
 * [FluidFindState] su un testo con gli stili: le occorrenze si cercano nelle lettere, e [FluidFindState.highlight]
 * aggiunge gli evidenziatori sopra gli stili che il testo ha già.
 */
@Composable
fun rememberFluidFindState(
  text: AnnotatedString,
  ignoreCase: Boolean = true,
  ignoreDiacritics: Boolean = true,
): FluidFindState {
  val query = rememberSaveable { mutableStateOf("") }
  val current = rememberSaveable { mutableStateOf(0) }
  val state = remember(query, current) { FluidFindState(query, current, text, ignoreCase, ignoreDiacritics) }
  state.update(text, ignoreCase, ignoreDiacritics)
  return state
}

/**
 * Il testo piegato per il confronto, **un carattere per carattere**, così gli indici restano quelli del testo: senza
 * maiuscole ([ignoreCase]) e senza segni diacritici ([ignoreDiacritics]: «é» → «e», dalla decomposizione NFD).
 */
internal fun fluidFindFold(text: String, ignoreCase: Boolean = true, ignoreDiacritics: Boolean = true): String {
  if (!ignoreCase && !ignoreDiacritics) return text
  val out = CharArray(text.length)
  for (index in text.indices) {
    var char = text[index]
    if (ignoreDiacritics && char.code >= 0x80 && !char.isSurrogate()) {
      val parts = Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
      if (parts.isNotEmpty()) char = parts[0]
    }
    if (ignoreCase) char = char.lowercaseChar()
    out[index] = char
  }
  return String(out)
}

/** Le occorrenze di [query] in [text], senza sovrapposizioni, nell'ordine del testo. Vuota con la parola vuota. */
internal fun fluidFindMatches(text: String, query: String, ignoreCase: Boolean = true, ignoreDiacritics: Boolean = true): List<TextRange> =
  fluidFindMatchesFolded(fluidFindFold(text, ignoreCase, ignoreDiacritics), query, ignoreCase, ignoreDiacritics)

private fun fluidFindMatchesFolded(folded: String, query: String, ignoreCase: Boolean, ignoreDiacritics: Boolean): List<TextRange> {
  if (query.isBlank()) return emptyList()
  val needle = fluidFindFold(query, ignoreCase, ignoreDiacritics)
  val out = ArrayList<TextRange>()
  var from = 0
  while (from <= folded.length - needle.length) {
    val at = folded.indexOf(needle, from)
    if (at < 0) break
    out += TextRange(at, at + needle.length)
    from = at + needle.length
  }
  return out
}

/**
 * [text] con gli evidenziatori di [highlights] sulle [matches]: la [current] (da zero; negativa: nessuna) con lo stile
 * della corrente, le altre con l'altro. Le occorrenze fuori dal testo (un testo che si è accorciato) si saltano.
 */
internal fun fluidFindHighlight(
  text: AnnotatedString,
  matches: List<TextRange>,
  current: Int,
  highlights: FluidFindHighlights,
): AnnotatedString {
  if (matches.isEmpty()) return text
  val builder = AnnotatedString.Builder(text)
  matches.forEachIndexed { index, range ->
    if (range.end > text.length || range.start >= range.end) return@forEachIndexed
    builder.addStyle(if (index == current) highlights.current else highlights.others, range.start, range.end)
  }
  return builder.toAnnotatedString()
}
