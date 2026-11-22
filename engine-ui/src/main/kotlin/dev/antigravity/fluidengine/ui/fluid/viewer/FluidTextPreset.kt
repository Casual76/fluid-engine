package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.TextFormat
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBackEffect
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.LocalFluidPagePreparing
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

// F6C.2 · Il preset testo e codice: monospazio, numeri di riga, a capo sì o no, «trova» (F6B.6) e i colori del
// decodificatore quando ci sono. Il file si legge a pezzi mentre si scorre: un file grande non blocca, e la ricerca
// gira fuori dal thread principale con le regole di rememberFluidFindState (maiuscole e accenti ignorati, occorrenze
// che non si sovrappongono, il giro alla fine). Il riconoscimento del charset, il tetto di lettura e i colori di
// sintassi sono del decodificatore (engine-viewer, F6C.4) o dell'app: qui non si decodifica niente.

/**
 * Una sorgente di testo che dà anche i colori (la sintassi di poche lingue, in `engine-viewer` di F6C.4, o i colori
 * dell'app). Il preset la preferisce a [readLines]; il contratto è lo stesso: righe da [start], al più [count], e un
 * pezzo corto segna la fine del file.
 *
 * I colori arrivano in due forme, anche insieme. I **ruoli**, consigliati: annotazioni con il tag
 * [FluidTextPresetDefaults.SyntaxAnnotation] e un valore di [FluidTextPresetDefaults.SyntaxRoles] (`keyword`, `string`,
 * `comment`…), che il preset colora col tema mentre disegna (il passaggio chiaro/scuro non lascia colori vecchi). Oppure
 * gli `SpanStyle` già risolti, che passano così come sono.
 */
interface FluidStyledTextSource : FluidTextSource {
  suspend fun readStyledLines(start: Int, count: Int): List<AnnotatedString>
}

/** Le parole del preset testo, in italiano di serie. */
@Immutable
data class FluidTextPresetStrings(
  val find: String = "Trova",
  val wrap: String = "A capo",
  val loading: String = "Caricamento",
  val failed: String = "Impossibile leggere il file",
  val retry: String = "Riprova",
  val empty: String = "Il file è vuoto",
)

/** I numeri del preset testo. Sono quantità di lettura, non di movimento. */
object FluidTextPresetDefaults {
  /** Righe per lettura: circa 20–40 KB di codice, una lettura ogni qualche schermata di scorrimento. A occhio, 22/11/2026. */
  const val ChunkLines: Int = 400

  /** La lettura successiva parte quando alla fine delle righe lette mancano queste righe. A occhio, 22/11/2026. */
  const val PrefetchLines: Int = 80

  /**
   * Una riga più lunga si spezza in pezzi di questa misura, uno per riga della lista: un JSON minimizzato di 5 MB su
   * una riga sola non impagina 5 MB in un `Text`. Il numero di riga sta sul primo pezzo. A occhio, 22/11/2026.
   */
  const val SegmentChars: Int = 2_000

  /** Il tag delle annotazioni di sintassi di [FluidStyledTextSource]. */
  const val SyntaxAnnotation: String = "fluid.syntax"

  /** I ruoli che il preset sa colorare; un altro valore resta senza colore. */
  val SyntaxRoles: List<String> = listOf(
    "keyword", "type", "string", "number", "comment", "tag", "attribute", "heading", "emphasis", "link", "code", "punctuation",
  )

  /**
   * Gli stili dei ruoli, dal tema: le parole chiave e i tag nell'accento, i tipi e i numeri nel terziario, le stringhe e
   * gli attributi nel secondario, i commenti e la punteggiatura nell'inchiostro attenuato. A occhio, 22/11/2026.
   */
  @Composable
  fun syntaxStyles(): Map<String, SpanStyle> {
    val c = MaterialTheme.colorScheme
    return remember(c) {
      mapOf(
        "keyword" to SpanStyle(color = c.primary, fontWeight = FontWeight.SemiBold),
        "type" to SpanStyle(color = c.tertiary),
        "string" to SpanStyle(color = c.secondary),
        "number" to SpanStyle(color = c.tertiary),
        "comment" to SpanStyle(color = c.onSurfaceVariant, fontStyle = FontStyle.Italic),
        "tag" to SpanStyle(color = c.primary),
        "attribute" to SpanStyle(color = c.secondary),
        "heading" to SpanStyle(color = c.primary, fontWeight = FontWeight.Bold),
        "emphasis" to SpanStyle(fontStyle = FontStyle.Italic),
        "link" to SpanStyle(color = c.primary, textDecoration = TextDecoration.Underline),
        "code" to SpanStyle(background = c.surfaceVariant),
        "punctuation" to SpanStyle(color = c.onSurfaceVariant),
      )
    }
  }
}

/** [text] con i ruoli di sintassi colorati da [styles]; senza annotazioni resta com'è. */
internal fun fluidSyntaxStyled(text: AnnotatedString, styles: Map<String, SpanStyle>): AnnotatedString {
  if (styles.isEmpty()) return text
  val roles = text.getStringAnnotations(FluidTextPresetDefaults.SyntaxAnnotation, 0, text.length)
  if (roles.isEmpty()) return text
  val builder = AnnotatedString.Builder(text)
  for (role in roles) styles[role.item]?.let { builder.addStyle(it, role.start, role.end) }
  return builder.toAnnotatedString()
}

// --- Il modello, senza Compose UI: provato in FluidTextPresetTest -----------------------------------------------------

/** Una riga della lista: il pezzo [start, end) della riga [line] del file. */
@Immutable
internal data class TextRow(val line: Int, val start: Int, val end: Int) {
  val first: Boolean get() = start == 0
}

/** I pezzi di una riga di [length] caratteri, senza spezzare una coppia surrogata. Una riga vuota è un pezzo vuoto. */
internal fun fluidTextRows(line: Int, text: CharSequence, segment: Int): List<TextRow> {
  val size = segment.coerceAtLeast(2)
  if (text.length <= size) return listOf(TextRow(line, 0, text.length))
  val rows = ArrayList<TextRow>(text.length / size + 1)
  var start = 0
  while (start < text.length) {
    var end = min(start + size, text.length)
    if (end < text.length && Character.isHighSurrogate(text[end - 1])) end--
    rows += TextRow(line, start, end)
    start = end
  }
  return rows
}

/** La riga della lista che contiene il carattere [offset] della riga [line] del file, o -1 se non è ancora letta. */
internal fun fluidTextRowOf(rows: List<TextRow>, line: Int, offset: Int): Int {
  var low = 0
  var high = rows.size - 1
  var found = -1
  while (low <= high) {
    val mid = (low + high) ushr 1
    val row = rows[mid]
    if (row.line < line || row.line == line && row.start <= offset) {
      found = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }
  return if (found >= 0 && rows[found].line == line) found else -1
}

/**
 * Il file letto finora, a pezzi di [chunk] righe. Una lettura alla volta: chi scorre e chi cerca chiedono la stessa
 * cosa e la seconda richiesta trova le righe già lette.
 */
@Stable
internal class TextDocument(
  private val source: FluidTextSource,
  private val chunk: Int = FluidTextPresetDefaults.ChunkLines,
  private val segment: Int = FluidTextPresetDefaults.SegmentChars,
) {
  val lines = mutableStateListOf<AnnotatedString>()
  val rows = mutableStateListOf<TextRow>()
  var ended by mutableStateOf(false)
    private set
  var failure by mutableStateOf<Throwable?>(null)
    private set

  /** I caratteri del pezzo più lungo: l'estensione orizzontale senza a capo. */
  var longest by mutableIntStateOf(0)
    private set
  private val mutex = Mutex()

  /** Legge il pezzo dopo. Falso alla fine o dopo un errore. */
  suspend fun loadMore(): Boolean = mutex.withLock {
    if (ended || failure != null) return@withLock false
    val start = lines.size
    val read = try {
      if (source is FluidStyledTextSource) source.readStyledLines(start, chunk)
      else source.readLines(start, chunk).map { AnnotatedString(it) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (error: Throwable) {
      failure = error
      return@withLock false
    }
    val taken = if (read.size > chunk) read.subList(0, chunk) else read
    var widest = longest
    val newRows = ArrayList<TextRow>(taken.size)
    taken.forEachIndexed { i, text ->
      for (row in fluidTextRows(start + i, text, segment)) {
        newRows += row
        widest = max(widest, row.end - row.start)
      }
    }
    lines.addAll(taken)
    rows.addAll(newRows)
    longest = widest
    val known = source.lineCount
    if (taken.size < chunk || known != null && lines.size >= known) ended = true
    !ended
  }

  /** Legge finché la riga [line] c'è, o il file finisce. */
  suspend fun loadThrough(line: Int) {
    // yield: una sorgente che non sospende (righe già in memoria) non tiene il thread principale fino alla fine.
    while (lines.size <= line && loadMore()) yield()
  }

  /** Legge tutto: lo chiede «trova», che cerca nel file intero. */
  suspend fun loadAll() {
    while (loadMore()) yield()
  }

  /** Dopo un errore: la prossima richiesta rilegge dal pezzo che era fallito. */
  fun retry() {
    failure = null
  }
}

/** Un'occorrenza: i caratteri [start, end) della riga [line]. */
@Immutable
internal data class TextMatch(val line: Int, val start: Int, val end: Int)

/** Le occorrenze di [query] in [lines], riga per riga, con le regole di [fluidFindMatches]. [firstLine] è la prima riga. */
internal fun fluidTextMatches(lines: List<CharSequence>, query: String, firstLine: Int = 0): List<TextMatch> {
  if (query.isBlank()) return emptyList()
  val out = ArrayList<TextMatch>()
  lines.forEachIndexed { i, text ->
    for (range in fluidFindMatches(text.toString(), query)) out += TextMatch(firstLine + i, range.start, range.end)
  }
  return out
}

/** La richiesta di portare in vista [match]; [serial] cresce anche verso la stessa occorrenza, come [FluidFindScrollRequest]. */
@Immutable
internal class TextFindRequest(val match: TextMatch, val index: Int, val serial: Int)

/**
 * «Trova» su un file a pezzi: la parola, le occorrenze e la corrente, con le stesse regole di [FluidFindState]. La
 * parola nuova tiene la corrente vicina a dov'era (la prima occorrenza da lì in poi, altrimenti la prima); le frecce
 * fanno il giro; senza occorrenze non succede niente. Le occorrenze le calcola il preset fuori dal thread principale
 * e le consegna con [replace] (parola nuova) e [append] (righe appena lette, la corrente non si muove).
 */
@Stable
internal class TextFind(private val queryState: MutableState<String>, private val currentState: MutableState<Int>) {
  val query: String get() = queryState.value
  var matches by mutableStateOf<List<TextMatch>>(emptyList())
    private set
  val total: Int get() = matches.size
  val current: Int get() = if (total == 0) 0 else currentState.value.coerceIn(0, total - 1)
  val currentMatch: TextMatch? get() = matches.getOrNull(current)
  var request by mutableStateOf<TextFindRequest?>(null)
    private set
  private var serial = 0
  private var anchor: TextMatch? = null

  val navigation: FluidSearchNavigation by derivedStateOf {
    FluidSearchNavigation(current = current, total = total, onPrevious = ::previous, onNext = ::next)
  }

  fun onQueryChange(value: String) {
    if (value == queryState.value) return
    anchor = currentMatch ?: anchor
    queryState.value = value
  }

  fun replace(found: List<TextMatch>) {
    matches = found
    val from = anchor
    anchor = null
    currentState.value = if (from == null) 0 else
      found.indexOfFirst { it.line > from.line || it.line == from.line && it.start >= from.start }.takeIf { it >= 0 } ?: 0
    request()
  }

  fun append(found: List<TextMatch>) {
    if (found.isEmpty()) return
    val wasEmpty = matches.isEmpty()
    matches = matches + found
    if (wasEmpty) {
      currentState.value = 0
      request()
    }
  }

  fun next() {
    if (total == 0) return
    currentState.value = (current + 1) % total
    request()
  }

  fun previous() {
    if (total == 0) return
    currentState.value = (current - 1 + total) % total
    request()
  }

  private fun request() {
    val match = currentMatch ?: return
    request = TextFindRequest(match, current, ++serial)
  }

  internal companion object {
    fun of(query: String = "") = TextFind(mutableStateOf(query), mutableStateOf(0))
  }
}

/** Il primo indice di [matches] che finisce dopo il carattere [offset] della riga [line] (le occorrenze sono in ordine). */
private fun firstMatchAfter(matches: List<TextMatch>, line: Int, offset: Int): Int {
  var low = 0
  var high = matches.size
  while (low < high) {
    val mid = (low + high) ushr 1
    val m = matches[mid]
    if (m.line < line || m.line == line && m.end <= offset) low = mid + 1 else high = mid
  }
  return low
}

/**
 * Il testo di [row]: i ruoli di sintassi colorati da [syntax], poi sopra gli evidenziatori di «trova» (la corrente con
 * [FluidFindHighlights.current], le altre velate).
 */
internal fun fluidTextRowText(
  line: AnnotatedString,
  row: TextRow,
  matches: List<TextMatch>,
  current: Int,
  highlights: FluidFindHighlights?,
  syntax: Map<String, SpanStyle> = emptyMap(),
): AnnotatedString {
  val end = row.end.coerceAtMost(line.length)
  val start = row.start.coerceAtMost(end)
  val piece = fluidSyntaxStyled(if (start == 0 && end == line.length) line else line.subSequence(start, end), syntax)
  if (highlights == null || matches.isEmpty()) return piece
  var index = firstMatchAfter(matches, row.line, start)
  var builder: AnnotatedString.Builder? = null
  while (index < matches.size) {
    val match = matches[index]
    if (match.line != row.line || match.start >= end) break
    val from = max(match.start, start) - start
    val to = min(match.end, end) - start
    if (to > from) {
      val b = builder ?: AnnotatedString.Builder(piece).also { builder = it }
      b.addStyle(if (index == current) highlights.current else highlights.others, from, to)
    }
    index++
  }
  return builder?.toAnnotatedString() ?: piece
}

// --- Il preset -------------------------------------------------------------------------------------------------------

private enum class TextPresetButton { Find, Wrap }

/**
 * Il preset testo e codice del visore: il file di [file] in monospazio, con i numeri di riga.
 *
 * - **A capo** è un tasto della capsula in basso (acceso: le righe lunghe vanno a capo; spento: si scorre di lato, e
 *   al bordo il dito passa al pager). Di serie va a capo un testo senza lingua, non va a capo il codice.
 * - **Trova** apre «trova nella pagina» di F6B.6 ([FluidSearchSpec.findInPage]): il campo nasce in fondo e sale sopra
 *   la tastiera, «3/12» e le frecce, l'invio che passa alla successiva. Cerca nel file intero, leggendo il resto a
 *   pezzi, e porta in vista la corrente. Indietro chiude prima il campo.
 * - **Colori**: da [FluidStyledTextSource] quando la sorgente la implementa, altrimenti testo semplice.
 * - **A pezzi**: [FluidTextPresetDefaults.ChunkLines] righe per volta, mentre si scorre; un errore di lettura ha
 *   «Riprova», un file vuoto lo dice.
 *
 * I comandi stanno nella barra del telaio: fuori da [FluidFileViewer] il preset mostra solo il testo.
 */
@Composable
fun FluidTextPreset(
  file: FluidViewerContent.Text,
  modifier: Modifier = Modifier,
  strings: FluidTextPresetStrings = FluidTextPresetStrings(),
  initialWrap: Boolean = file.source.language == null,
) {
  val source = file.source
  val doc = remember(source) { TextDocument(source) }
  var wrap by rememberSaveable { mutableStateOf(initialWrap) }
  var finding by rememberSaveable { mutableStateOf(false) }
  val queryState = rememberSaveable { mutableStateOf("") }
  val currentState = rememberSaveable { mutableStateOf(0) }
  val find = remember(queryState, currentState) { TextFind(queryState, currentState) }
  // La prima riga in vista, salvata: dopo la ricreazione si rilegge fin lì e si torna lì.
  val savedLine = rememberSaveable { mutableStateOf(0) }
  val list = remember { LazyListState() }
  val active = currentFluidViewerPageActive()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion

  // La lettura: il primo pezzo, poi uno nuovo quando la fine si avvicina; la ricerca legge tutto.
  LaunchedEffect(doc) {
    val restore = savedLine.value
    doc.loadThrough(restore)
    val row = doc.rows.indexOfFirst { it.line >= restore }
    if (row > 0) list.scrollToItem(row)
    snapshotFlow { Triple(list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0, doc.rows.size, doc.failure) }
      .collect { (last, rows, failure) ->
        if (failure == null && !doc.ended && last >= rows - FluidTextPresetDefaults.PrefetchLines) doc.loadMore()
      }
  }
  LaunchedEffect(doc, list) {
    snapshotFlow { list.firstVisibleItemIndex }.collect { savedLine.value = doc.rows.getOrNull(it)?.line ?: savedLine.value }
  }
  LaunchedEffect(doc, finding) { if (finding) doc.loadAll() }
  LaunchedEffect(active) { if (!active) finding = false }

  // Le occorrenze, fuori dal thread principale: tutte per una parola nuova, poi solo le righe appena lette.
  LaunchedEffect(doc, find) {
    var matchedQuery: String? = null
    var until = 0
    snapshotFlow { find.query to doc.lines.size }.collectLatest { (query, size) ->
      if (query != matchedQuery) {
        val texts = doc.lines.subList(0, size).map { it.text }
        val found = withContext(Dispatchers.Default) { fluidTextMatches(texts, query) }
        find.replace(found)
        matchedQuery = query
        until = size
      } else if (size > until) {
        val texts = doc.lines.subList(until, size).map { it.text }
        val found = withContext(Dispatchers.Default) { fluidTextMatches(texts, query, until) }
        find.append(found)
        until = size
      }
    }
  }

  val density = LocalDensity.current
  val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
  val measurer = rememberTextMeasurer()
  val charWidth = remember(style, density) { measurer.measure("0", style).size.width.coerceAtLeast(1) }
  val digits = max(3, (source.lineCount ?: doc.lines.size).coerceAtLeast(1).toString().length)
  val gap = FluidScreenDefaults.HorizontalPadding / 2
  val gutter = with(density) { (charWidth * digits).toDp() } + gap
  var viewportWidth by remember { mutableIntStateOf(0) }
  var offsetX by remember { mutableFloatStateOf(0f) }
  val edge = with(density) { FluidScreenDefaults.HorizontalPadding.roundToPx() }
  val codeWidth = (viewportWidth - with(density) { gutter.roundToPx() } - 2 * edge).coerceAtLeast(0)
  val maxOffset = if (wrap) 0f else (doc.longest * charWidth - codeWidth).coerceAtLeast(0).toFloat()
  LaunchedEffect(maxOffset) { if (offsetX > maxOffset) offsetX = maxOffset }
  val sideways = rememberScrollableState { delta ->
    val before = offsetX
    offsetX = (before - delta).coerceIn(0f, maxOffset)
    before - offsetX
  }

  // La corrente in vista: un terzo sotto la cima (sopra la tastiera), e di lato senza a capo.
  var readingHeight by remember { mutableIntStateOf(0) }
  val window = LocalActivity.current?.window
  DisposableEffect(window, finding, active) {
    val previous = window?.attributes?.softInputMode
    if (finding && active && previous != null) {
      val adjust = if (android.os.Build.VERSION.SDK_INT >= 30) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
      window.setSoftInputMode((previous and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or adjust)
    }
    onDispose { if (finding && active && previous != null) window.setSoftInputMode(previous) }
  }
  val request = find.request.takeIf { finding && find.query.isNotBlank() }
  LaunchedEffect(request, readingHeight, wrap, codeWidth, style) {
    val match = request?.match ?: return@LaunchedEffect
    doc.loadThrough(match.line)
    val row = fluidTextRowOf(doc.rows, match.line, match.start)
    if (row < 0) return@LaunchedEffect
    if (readingHeight <= 0 || codeWidth <= 0) return@LaunchedEffect
    val segment = doc.rows[row]
    val y = if (wrap) {
      val line = doc.lines[match.line].subSequence(segment.start, segment.end)
      val layout = measurer.measure(line, style, constraints = Constraints(maxWidth = codeWidth))
      val offset = (match.start - segment.start).coerceIn(0, line.length)
      layout.getLineTop(layout.getLineForOffset(offset)).toInt()
    } else 0
    val offset = y - readingHeight / 3
    if (reduced) list.scrollToItem(row, offset) else list.animateScrollToItem(row, offset)
    if (!wrap) {
      val r = doc.rows[row]
      val left = (match.start - r.start) * charWidth.toFloat()
      val right = (match.end - r.start) * charWidth.toFloat()
      if (left < offsetX || right > offsetX + codeWidth) offsetX = (left - codeWidth / 3f).coerceIn(0f, maxOffset)
    }
  }

  // I comandi nella barra del telaio, per questa pagina.
  ViewerPresetToolbar {
    if (finding) search(FluidSearchSpec.findInPage(find.query, find::onQueryChange, find.navigation, active = true,
      onActiveChange = { if (!it) finding = false }))
    button(TextPresetButton.Find, FluidIcons.Search, strings.find, { finding = true },
      placement = FluidToolbarPlacement.BottomBar)
    button(TextPresetButton.Wrap, FluidIcons.TextFormat, strings.wrap, { wrap = !wrap },
      placement = FluidToolbarPlacement.BottomBar, selected = wrap)
  }
  val front by LocalFluidRouteFront.current
  if (finding && active && front && !LocalFluidPagePreparing.current) FluidSelectionBackEffect(enabled = true) { finding = false }

  val highlights = if (finding) FluidFindDefaults.highlights() else null
  val syntax = FluidTextPresetDefaults.syntaxStyles()
  val ink = LocalContentColor.current
  val numbers = MaterialTheme.colorScheme.onSurfaceVariant
  // Sotto la barra in alto e sopra la capsula; con «trova» aperto anche sopra la tastiera e il campo che ci sta sopra.
  val top = FluidScreenDefaults.topBarHeight() + gap
  val bottom = FluidScreenDefaults.bottomContentPadding(FluidFloatingCapsuleDefaults.ActionSize)
  val findInsets = if (finding) Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
    .padding(bottom = FluidScreenDefaults.ControlRowHeight + gap) else Modifier.padding(bottom = bottom)
  Column(modifier.fillMaxSize().padding(top = top).then(findInsets).onSizeChanged { viewportWidth = it.width }) {
    source.language?.takeIf { it.isNotBlank() }?.let { language ->
      Row(Modifier.fillMaxWidth().padding(start = FluidScreenDefaults.HorizontalPadding, bottom = gap),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.tertiary, FluidCapsuleShape))
        Text(language, style = MaterialTheme.typography.labelMedium, color = numbers)
      }
    }
    Box(Modifier.fillMaxSize().onSizeChanged { readingHeight = it.height }.clipToBounds()) {
      when {
        doc.ended && doc.lines.isEmpty() -> Text(strings.empty, Modifier.align(Alignment.Center),
          style = MaterialTheme.typography.bodyLarge, color = numbers)
        else -> LazyColumn(
          state = list,
          modifier = Modifier.fillMaxSize().then(if (wrap) Modifier else Modifier.scrollable(sideways, Orientation.Horizontal)),
          contentPadding = PaddingValues(start = FluidScreenDefaults.HorizontalPadding, end = FluidScreenDefaults.HorizontalPadding),
        ) {
          items(count = doc.rows.size, key = { doc.rows[it].let { row -> row.line.toLong() shl 32 or row.start.toLong() } }) { i ->
            val row = doc.rows[i]
            val text = fluidTextRowText(doc.lines[row.line], row, find.matches, find.current, highlights, syntax)
            Row(Modifier.fillMaxWidth()) {
              Box(Modifier.width(gutter).padding(end = gap)) {
                if (row.first) Text("${row.line + 1}", Modifier.fillMaxWidth().clearAndSetSemantics {}, style = style,
                  color = numbers, textAlign = TextAlign.End, maxLines = 1, softWrap = false)
              }
              if (wrap) {
                Text(text, Modifier.weight(1f), style = style, color = ink)
              } else {
                Box(Modifier.weight(1f).clipToBounds()) {
                  Text(text, Modifier.wrapContentWidth(Alignment.Start, unbounded = true).graphicsLayer { translationX = -offsetX },
                    style = style, color = ink, maxLines = 1, softWrap = false)
                }
              }
            }
          }
          val failure = doc.failure
          when {
            failure != null -> item(key = "failure") {
              Column(Modifier.fillMaxWidth().padding(vertical = gap), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.failed, style = MaterialTheme.typography.bodyMedium, color = numbers)
                FluidButton(strings.retry, { doc.retry() }, style = FluidButtonStyle.Plain, grip = FluidPressGrip.YieldsToScroll)
              }
            }
            !doc.ended -> item(key = "loading") {
              Box(Modifier.fillMaxWidth().padding(vertical = gap).semantics { contentDescription = strings.loading },
                contentAlignment = Alignment.Center) { FluidSpinner() }
            }
          }
        }
      }
    }
  }
}
