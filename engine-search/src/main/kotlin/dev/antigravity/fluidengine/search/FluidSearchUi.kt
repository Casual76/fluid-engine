package dev.antigravity.fluidengine.search

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFilterPresets
import dev.antigravity.fluidengine.ui.fluid.FluidFilterSet
import dev.antigravity.fluidengine.ui.fluid.FluidFilterValue
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSuggestions
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSuggestionKind
import dev.antigravity.fluidengine.ui.fluid.FluidToken
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// F8.22 · L'aggancio al campo di ricerca che c'e' gia' (`FluidSearchSpec`, F6B.6 e F6B.8b): i suggerimenti (le parole
// che completano quella che si scrive, le domande recenti con la ✕), i gettoni dei tipi («Note», «Allegati») e i
// risultati raggruppati per tipo con le parole trovate in evidenza, come la ricerca di Mail e Note di iOS.

/** Il nome e il simbolo di un tipo di documento, per il suo gruppo e per il suo gettone. */
@Immutable
class FluidSearchKindStyle(
  val label: String,
  val icon: ImageVector? = null,
  val tone: FluidTone = FluidTone.Neutral,
)

/** Le parole della ricerca. **In italiano di serie**: un'app tradotta le passa a [fluidSearchResults]. */
@Immutable
data class FluidSearchLabels(
  /** Quanti risultati, per TalkBack: «%d risultati». */
  val results: String = "%d risultati",
  /** Un risultato solo. */
  val oneResult: String = "1 risultato",
  /** Nessun risultato: «Nessun risultato per «%s»». */
  val noResults: String = "Nessun risultato per «%s»",
  /** La parola corretta: «Risultati per «%1$s» · cercato anche «%2$s»». */
  val corrected: String = "Risultati per %1\$s · cercato anche «%2\$s»",
  /** Il segno di un risultato trovato per significato. */
  val byMeaning: String = "per significato",
  /** Il nome della fila dei gettoni dei tipi. */
  val kinds: String = "Tipo",
  /** «Cerco…», mentre il significato arriva. */
  val searching: String = "Cerco…",
  /** L'indice non ha risposto ([FluidSearchState.failure]): al posto di «Nessun risultato», che non sarebbe vero (R3). */
  val failed: String = "Impossibile cercare adesso",
)

/**
 * Le proprieta' di semantica della ricerca, per le prove e le tracce (TalkBack non le legge).
 */
object FluidSearchSemantics {
  /** Vero sulla riga (o sul gruppo) che arriva sfocato perche' l'app l'ha aggiunto da sola; con «Riduci movimento» no. */
  val Arriving: SemanticsPropertyKey<Boolean> = SemanticsPropertyKey("FluidSearchArriving")
}

/** I numeri della ricerca nel campo. */
object FluidSearchDefaults {
  /**
   * Quanto si aspetta dopo l'ultima lettera prima di cercare per parole: 60 ms (a occhio, 25/11/2026: fra due tasti di
   * chi scrive svelto passano 100–200 ms; Spotlight aggiorna a ogni tasto, e 60 ms non si vedono ma evitano le ricerche
   * di parole a meta' quando si scrive in fretta).
   */
  const val DebounceMillis: Long = 60L

  /**
   * Quanto si aspetta prima di chiedere il significato: 300 ms, come la ricerca del selettore di foto
   * (`FluidMediaPickerDefaults.SearchDebounceMillis`). Il modello costa 50–90 ms per domanda: si chiede solo quando la
   * persona si ferma.
   */
  const val MeaningDelayMillis: Long = 300L

  /** Da quante lettere la domanda passa anche dal significato. */
  const val MeaningMinChars: Int = 4

  /** Il prefisso degli id dei gettoni dei tipi: «kind:nota». */
  const val KindTokenPrefix: String = "kind:"

  /** Quante parole che completano si suggeriscono sopra i risultati. */
  const val MaxCompletions: Int = 3

  /**
   * Quanto dura l'arrivo di un risultato che l'app aggiunge da sola (il significato, un'app che risponde dopo): 300 ms
   * e una sfocatura che si scioglie, come lo scambio del contenuto del vetro (R7, `GlassContentDefaults.DurationMs`,
   * fiducia alta sul tempo). Con «Riduci movimento» arriva fermo.
   */
  const val ArrivalMillis: Int = 300

  /** La sfocatura di partenza dell'arrivo, in dp. */
  const val ArrivalBlurDp: Float = 6f

  /** Il gettone del tipo [kind]. */
  fun kindToken(kind: String, label: String): FluidToken = FluidToken(KindTokenPrefix + kind, label)
}

/** La domanda di adesso, cosi' come la vede la ricerca. */
internal data class SearchInput(val query: String, val kinds: Set<String>, val generation: Long)

/**
 * Lo stato di una ricerca nell'indice: il testo e i gettoni (dell'app, come in [FluidSearchSpec]), e i risultati. Si
 * crea con [rememberFluidSearchState]; [spec] da' il campo gia' agganciato, [fluidSearchResults] i risultati.
 *
 * I risultati sono un valore intero, scritto dal filo principale: si leggono da qualunque parte della composizione.
 */
@Stable
class FluidSearchState internal constructor(
  internal val index: FluidSearchIndex?,
  private val scope: CoroutineScope,
  private val limit: Int,
  private val typos: Boolean,
) {
  /** Il testo del campo. */
  var query: String by mutableStateOf("")

  /** I gettoni del campo (i tipi: [FluidSearchDefaults.kindToken]). */
  var tokens: List<FluidToken> by mutableStateOf(emptyList())

  /** Il campo e' aperto. */
  var active: Boolean by mutableStateOf(false)

  /** I risultati dell'ultima domanda. */
  var results: FluidSearchResults by mutableStateOf(FluidSearchResults.Empty)
    internal set

  /** Vero mentre una domanda e' in corso (il significato compreso). */
  var searching: Boolean by mutableStateOf(false)
    internal set

  /**
   * L'errore dell'indice nell'ultima domanda (una cassaforte chiusa, un file illeggibile, un indice chiuso), o null. Con
   * un errore [results] e' vuoto per la domanda di adesso e la pagina dice [FluidSearchLabels.failed]; la domanda dopo
   * riprova (R3: prima l'errore sembrava «Nessun risultato», o restavano i risultati della domanda di prima). Un modello
   * del significato che non parte non e' un errore: restano i risultati per parole.
   */
  var failure: Throwable? by mutableStateOf(null)
    internal set

  /** Le domande recenti dell'indice. */
  var recents: List<String> by mutableStateOf(emptyList())
    internal set

  /** Le parole dell'indice che completano quella che si sta scrivendo. */
  var completions: List<String> by mutableStateOf(emptyList())
    internal set

  internal var embedder: FluidTextEmbedder? = null

  /** I risultati gia' mostrati per questa domanda: solo quelli nuovi arrivano sfocati. */
  internal val shown = HashSet<String>()
  private var shownFor = ""

  /** I tipi scelti coi gettoni. */
  val kinds: Set<String>
    get() = tokens.asSequence().filter { it.id.startsWith(FluidSearchDefaults.KindTokenPrefix) }
      .map { it.id.removePrefix(FluidSearchDefaults.KindTokenPrefix) }.toSet()

  /** Ricorda la domanda di adesso fra le recenti (l'invio del campo, o un risultato aperto). */
  fun saveRecent() {
    val text = query.trim()
    val idx = index ?: return
    if (text.isEmpty()) return
    scope.launch {
      quietly {
        idx.addRecent(text)
        recents = idx.recents()
      }
    }
  }

  /** Toglie [text] dalle recenti. */
  fun removeRecent(text: String) {
    val idx = index ?: return
    scope.launch {
      quietly {
        idx.removeRecent(text)
        recents = idx.recents()
      }
    }
  }

  /**
   * Le recenti sono una comodita': un indice che non risponde (la cassaforte chiusa) non le scrive, e non fa cadere
   * l'app (R3: prima l'eccezione usciva dal lancio).
   */
  private suspend fun quietly(block: suspend () -> Unit) {
    try {
      block()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // Niente: la domanda resta da ricordare alla prossima volta.
    }
  }

  /** Mette [word] al posto della parola che si sta scrivendo (un suggerimento toccato). */
  fun complete(word: String) {
    val q = query
    val cut = q.indexOfLast { it.isWhitespace() }
    query = (if (cut < 0) "" else q.substring(0, cut + 1)) + word + " "
  }

  internal fun arrived(id: String): Boolean {
    if (shownFor != results.query) {
      shownFor = results.query
      shown.clear()
    }
    return shown.add(id)
  }

  internal suspend fun refreshRecents() {
    quietly { recents = index?.recents().orEmpty() }
  }

  internal suspend fun run(input: SearchInput) {
    val idx = index
    if (idx == null || input.query.isBlank()) {
      results = FluidSearchResults.Empty
      completions = emptyList()
      failure = null
      searching = false
      return
    }
    searching = true
    try {
      delay(FluidSearchDefaults.DebounceMillis)
      // R3: un errore dell'indice si vede. Prima un `catch` solo, pensato per il modello, ingoiava anche questo: la
      // pagina diceva «Nessun risultato», o lasciava i risultati della domanda di prima sotto il testo nuovo.
      val words = try {
        idx.search(FluidSearchQuery(input.query, input.kinds, limit, typos))
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        results = FluidSearchResults(query = input.query, hits = emptyList(), total = 0)
        completions = emptyList()
        failure = e
        return
      }
      results = words
      failure = null
      // I suggerimenti e il significato sono in piu': se non arrivano restano i risultati per parole.
      completions = optional { completionsFor(idx, input.query) }.orEmpty()
      val model = embedder
      if (model != null && input.query.trim().length >= FluidSearchDefaults.MeaningMinChars && optional { idx.usesMeaningSpace(model.space) } == true) {
        delay(FluidSearchDefaults.MeaningDelayMillis)
        // Un modello che non parte non toglie le parole: restano i risultati per parole.
        val vector = optional { model.embed(listOf(input.query.trim())).firstOrNull() }
        if (vector != null) optional { idx.search(FluidSearchQuery(input.query, input.kinds, limit, typos, meaning = vector)) }?.let { results = it }
      }
    } finally {
      searching = false
    }
  }

  /** [block], o null se lancia (l'annullo risale). */
  private suspend fun <T> optional(block: suspend () -> T): T? = try {
    block()
  } catch (e: CancellationException) {
    throw e
  } catch (e: Exception) {
    null
  }

  private suspend fun completionsFor(idx: FluidSearchIndex, query: String): List<String> {
    if (query.isEmpty() || query.last().isWhitespace()) return emptyList()
    val word = query.substringAfterLast(' ').trim()
    if (word.length < 2) return emptyList()
    val folded = SearchAnalyzer.fold(word)
    return idx.completions(word, FluidSearchDefaults.MaxCompletions + 1).filter { SearchAnalyzer.fold(it) != folded }
      .take(FluidSearchDefaults.MaxCompletions)
  }

  /**
   * Il campo agganciato a questo stato: la ricerca di serie ([FluidSearchSpec.search]) col testo, i gettoni dei tipi
   * ([kinds]: la fila delle pillole, che scelte diventano gettoni), e i suggerimenti sopra i risultati (le recenti a
   * campo vuoto, con la ✕; le parole che completano, scrivendo). L'invio ricorda la domanda.
   */
  fun spec(
    prompt: String? = null,
    kinds: Map<String, FluidSearchKindStyle> = emptyMap(),
    placement: FluidSearchPlacement = FluidSearchPlacement.Automatic,
    labels: FluidSearchLabels = FluidSearchLabels(),
  ): FluidSearchSpec = FluidSearchSpec.search(
    query = query,
    onQueryChange = { query = it },
    prompt = prompt,
    onSearch = { saveRecent() },
    placement = placement,
    suggestions = { SearchSuggestions(this) },
    active = active,
    onActiveChange = { active = it },
    tokens = tokens,
    onTokensChange = { tokens = it },
    filters = fluidSearchKindFilters(kinds, labels),
  )
}

/** La fila dei tipi per il campo: una pillola per tipo, che scelta diventa gettone. */
fun fluidSearchKindFilters(kinds: Map<String, FluidSearchKindStyle>, labels: FluidSearchLabels = FluidSearchLabels()): FluidFilterSet? =
  if (kinds.isEmpty()) null else FluidFilterPresets.tokens(
    kinds.map { (id, style) -> FluidFilterValue(FluidSearchDefaults.KindTokenPrefix + id, style.label) },
    label = labels.kinds,
  )

/**
 * Lo stato della ricerca nell'[index] (null: un indice che si sta ancora aprendo). Con [embedder] la domanda passa anche
 * dal significato quando la persona si ferma ([FluidSearchDefaults.MeaningDelayMillis]) e l'indice ha i vettori di quel
 * modello; i risultati per significato arrivano dopo quelli per parole, sfocati.
 */
@Composable
fun rememberFluidSearchState(
  index: FluidSearchIndex?,
  embedder: FluidTextEmbedder? = null,
  limit: Int = 50,
  typos: Boolean = true,
): FluidSearchState {
  val scope = rememberCoroutineScope()
  val state = remember(index, limit, typos) { FluidSearchState(index, scope, limit, typos) }
  SideEffect { state.embedder = embedder }
  val generation: State<Long>? = index?.generation?.collectAsState()
  LaunchedEffect(state) {
    state.refreshRecents()
    snapshotFlow { SearchInput(state.query, state.kinds, generation?.value ?: 0L) }
      .distinctUntilChanged()
      .collectLatest { state.run(it) }
  }
  return state
}

/** I suggerimenti sopra i risultati: le recenti a campo vuoto, le parole che completano scrivendo. */
@Composable
private fun SearchSuggestions(state: FluidSearchState) {
  val query = state.query
  val list: List<FluidSearchSuggestion> = if (query.isBlank()) {
    state.recents.mapIndexed { i, text -> FluidFilterPresets.recent("recente:$text", text, relevance = -i.toFloat()) }
  } else {
    state.completions.mapIndexed { i, word -> FluidSearchSuggestion("parola:$word", word, kind = FluidSuggestionKind.Result, relevance = -i.toFloat()) }
  }
  FluidSearchSuggestions(
    // A campo pieno senza parole da suggerire non si dice «Nessun risultato» qui: lo dice la pagina.
    query = if (list.isEmpty()) "" else query,
    suggestions = list,
    onSuggestionClick = { s ->
      if (s.kind == FluidSuggestionKind.Recent) state.query = s.title else state.complete(s.title)
    },
    onRemoveRecent = { s -> state.removeRecent(s.title) },
  )
}

/** [FluidSearchSnippet] col suo testo e le parole trovate in [highlight]. */
fun FluidSearchSnippet.toAnnotatedString(highlight: SpanStyle): AnnotatedString = buildAnnotatedString {
  var at = 0
  for (r in highlights.sortedBy { it.first }) {
    if (r.first < at || r.last >= text.length) continue
    append(text.substring(at, r.first))
    withStyle(highlight) { append(text.substring(r.first, r.last + 1)) }
    at = r.last + 1
  }
  append(text.substring(at))
}

/**
 * Lo stile delle parole trovate: il peso semibold e il colore pieno del testo, sul grigio del pezzo (come la ricerca di
 * Mail di iOS, che mette in nero grassetto le parole trovate nell'anteprima). Non solo il colore: si legge anche senza.
 */
@Composable
fun fluidSearchHighlight(): SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)

/**
 * I risultati di [state] in una pagina (il contenuto di `FluidScreen`), raggruppati per tipo come Mail e Note di iOS:
 * l'intestazione «Note · 3», poi un gruppo con una riga per risultato, il titolo e il pezzo di testo con le parole
 * trovate in evidenza. Sopra, la parola corretta quando c'e' («Risultati per riunione · cercato anche «riunone»»). Un
 * risultato trovato solo per significato arriva dopo, sfocato, e porta il segno «per significato».
 *
 * TalkBack: ogni riga e' un pulsante col titolo e il pezzo; le intestazioni sono titoli; quanti risultati ci sono lo dice
 * un'area viva.
 *
 * @param kinds il nome e il simbolo di ogni tipo; un tipo che manca si chiama col suo id.
 * @param meta la riga piccola sotto il pezzo (la data, la pagina del PDF), o null.
 */
fun LazyListScope.fluidSearchResults(
  state: FluidSearchState,
  kinds: Map<String, FluidSearchKindStyle> = emptyMap(),
  onOpen: (FluidSearchHit) -> Unit,
  meta: (FluidSearchHit) -> String? = { null },
  labels: FluidSearchLabels = FluidSearchLabels(),
) {
  val results = state.results
  if (state.query.isBlank()) return
  item(key = "fluid-search:header") {
    SearchResultsHeader(state, labels)
  }
  for ((kind, hits) in results.byKind) {
    val style = kinds[kind]
    item(key = "fluid-search:kind:$kind") {
      FluidSectionHeader(
        title = "${style?.label ?: kind} · ${hits.size}",
      )
    }
    item(key = "fluid-search:group:$kind") {
      FluidListGroup {
        hits.forEachIndexed { i, hit ->
          key(hit.id) {
            if (i > 0) FluidListDivider()
            FluidSearchResultRow(state, hit, style, onOpen, meta(hit), labels)
          }
        }
      }
    }
  }
}

@Composable
private fun SearchResultsHeader(state: FluidSearchState, labels: FluidSearchLabels) {
  val results = state.results
  val count = results.hits.size
  // R3: l'indice non ha risposto: lo si dice, a vista e a TalkBack (non «Nessun risultato»).
  val failed = state.failure != null && !state.searching && results.query == state.query
  val spoken = when {
    failed -> labels.failed
    count == 0 -> labels.noResults.format(state.query.trim())
    count == 1 -> labels.oneResult
    else -> labels.results.format(count)
  }
  val corrected = results.corrected
  val visible = when {
    failed -> labels.failed
    corrected != null && count > 0 -> labels.corrected.format(corrected, results.query.trim())
    count == 0 && !state.searching && results.query == state.query -> labels.noResults.format(state.query.trim())
    else -> null
  }
  Box(
    Modifier
      .fillMaxWidth()
      .semantics {
        liveRegion = LiveRegionMode.Polite
        contentDescription = visible ?: spoken
      },
  ) {
    if (visible != null) {
      Text(
        text = visible,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
      )
    } else {
      Box(Modifier.size(1.dp))
    }
  }
}

/** Una riga dei risultati: si puo' usare anche fuori da [fluidSearchResults] (un elenco dell'app). */
@Composable
fun FluidSearchResultRow(
  state: FluidSearchState,
  hit: FluidSearchHit,
  style: FluidSearchKindStyle?,
  onOpen: (FluidSearchHit) -> Unit,
  meta: String? = null,
  labels: FluidSearchLabels = FluidSearchLabels(),
) {
  val highlight = fluidSearchHighlight()
  val title = hit.title?.toAnnotatedString(highlight) ?: AnnotatedString(hit.id)
  val subtitle = hit.snippet?.toAnnotatedString(highlight) ?: AnnotatedString("")
  val metaLine = listOfNotNull(meta, labels.byMeaning.takeIf { hit.byMeaning && !hit.byWords }).joinToString(" · ").ifEmpty { null }
  // Arriva sfocato solo cio' che l'app aggiunge da sola dopo la prima lista: il significato, che arriva dopo le parole.
  val arrives = remember(hit.id) { hit.byMeaning && !hit.byWords && state.arrived(hit.id) }
  FluidListRow(
    title = title,
    subtitle = subtitle,
    meta = metaLine,
    tone = style?.tone ?: FluidTone.Neutral,
    leading = style?.icon?.let { icon -> { FluidIcon(imageVector = icon, contentDescription = null) } },
    onClick = {
      state.saveRecent()
      onOpen(hit)
    },
    titleMaxLines = 2,
    subtitleMaxLines = 2,
    modifier = Modifier.fluidSearchArrival(hit.id, arrives),
  )
}

/**
 * L'arrivo di un contenuto che l'app aggiunge da sola (F8, «il blur va solo sui cambi guidati dall'app»): l'opacita' da
 * 0 e una sfocatura che si scioglie in [FluidSearchDefaults.ArrivalMillis]. Si anima solo la prima volta ([arrives]); con
 * «Riduci movimento» arriva fermo. Il valore si legge nel layer: nessuna ricomposizione per fotogramma.
 */
@Composable
fun Modifier.fluidSearchArrival(key: Any, arrives: Boolean): Modifier {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  if (!arrives || reduced) return this
  val progress = remember(key) { Animatable(0f) }
  LaunchedEffect(key) { progress.animateTo(1f, tween(FluidSearchDefaults.ArrivalMillis)) }
  val blurPx = with(LocalDensity.current) { FluidSearchDefaults.ArrivalBlurDp.dp.toPx() }
  return semantics { this[FluidSearchSemantics.Arriving] = true }.graphicsLayer {
    val p = progress.value
    alpha = p
    if (Build.VERSION.SDK_INT >= 31) {
      val r = blurPx * (1f - p)
      renderEffect = if (r > 0.5f) BlurEffect(r, r, TileMode.Decal) else null
    }
  }
}
