package dev.antigravity.fluidengine.search

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.bridge.AiToolClient
import dev.antigravity.fluidengine.ai.bridge.BridgeCalls
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * «Cerca in Pampa» (F8.22, `ricerca-in-tutte-le-app`): la domanda va insieme a tutte le app Pampa del telefono che la
 * sanno ricevere (il tool [FluidSearchBridge.ToolName] sul ponte firmato), e i risultati tornano **per app**, come i
 * gruppi di Spotlight. Con un [embedder] il vettore della domanda si calcola **una volta** e va a tutte le app, che
 * confrontano i loro (stesso spazio: MyCloud Gallery compresa).
 *
 * Tutto resta sul telefono: e' una chiamata Binder fra app con la stessa firma, e ogni app risponde dal suo indice.
 *
 * @param discover le app da interrogare (di serie quelle che [discoverApps] trova col ponte).
 * @param timeoutMillis quanto si aspetta un'app prima di darla per assente.
 */
class FluidPampaSearch(
  private val calls: BridgeCalls,
  private val discover: suspend () -> List<FluidPampaApp>,
  private val embedder: FluidTextEmbedder? = null,
  val timeoutMillis: Long = DefaultTimeoutMillis,
) {
  private val cacheLock = Mutex()
  private var cached: List<FluidPampaApp>? = null
  private var cachedAt = 0L

  /** Le app di adesso (tenute un minuto: la scoperta chiede il catalogo a ogni app). */
  suspend fun apps(): List<FluidPampaApp> = cacheLock.withLock {
    val now = SystemClock.elapsedRealtime()
    cached?.takeIf { now - cachedAt < AppsCacheMillis } ?: discover().also {
      cached = it
      cachedAt = now
    }
  }

  /** Dimentica le app trovate: la prossima domanda le cerca di nuovo (un'app installata adesso). */
  suspend fun refresh() = cacheLock.withLock { cached = null }

  /**
   * Cerca [query] in tutte le app. Il primo valore ha tutte le app in attesa; poi un valore nuovo a ogni app che
   * risponde, fino all'ultima. I gruppi restano nell'ordine delle app: niente salti mentre si legge.
   */
  fun search(query: String, kinds: Set<String> = emptySet(), limit: Int = DefaultLimit): Flow<FluidPampaResults> = channelFlow {
    val started = SystemClock.elapsedRealtime()
    val list = apps()
    val groups = list.map { FluidPampaGroup(it) }.toMutableList()
    val lock = Mutex()
    send(FluidPampaResults(query, groups.toList()))
    val text = query.trim()
    val vector = embedder?.takeIf { text.length >= FluidSearchDefaults.MeaningMinChars }?.let { model ->
      try {
        model.embed(listOf(text)).firstOrNull()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        null
      }
    }
    val args = buildJsonObject {
      put("query", query)
      put("limit", limit)
      if (kinds.isNotEmpty()) putJsonArray("kinds") { kinds.forEach { add(JsonPrimitive(it)) } }
      if (vector != null) {
        put("meaning", FluidSearchBridge.encodeVector(vector))
        embedder?.let { put("space", it.space.id) }
      }
    }
    coroutineScope {
      list.forEachIndexed { i, app ->
        launch {
          val result = try {
            withTimeoutOrNull(timeoutMillis) {
              calls.run(app.authority, FluidSearchBridge.ToolName, args, confirmed = false, requestId = UUID.randomUUID().toString(), language = "it")
            }
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            null
          }
          val group = if (result == null) FluidPampaGroup(app, pending = false, error = NoAnswer) else pampaGroupOf(app, result)
          lock.withLock {
            groups[i] = group
            send(FluidPampaResults(query, groups.toList(), meaning = vector != null, tookMillis = SystemClock.elapsedRealtime() - started))
          }
        }
      }
    }
  }

  companion object {
    /** Quanto si aspetta un'app: 2,5 s (a occhio, 25/11/2026: un'app chiusa si avvia in 0,4–1,2 s sul Tab S9, poi cerca in ms). */
    const val DefaultTimeoutMillis: Long = 2_500L

    /** Quanti risultati per app: come Spotlight, che ne mostra pochi per app e «Mostra altro». */
    const val DefaultLimit: Int = 5

    private const val AppsCacheMillis = 60_000L
    private const val NoAnswer = "non ha risposto in tempo"

    /** «Cerca in Pampa» con le app del telefono trovate col ponte. */
    fun create(context: Context, embedder: FluidTextEmbedder? = null): FluidPampaSearch {
      val client = AiToolClient(context.applicationContext)
      return FluidPampaSearch(client, discover = { discoverApps(client) }, embedder = embedder)
    }

    /**
     * Le app del telefono che rispondono a «Cerca in Pampa»: quelle col ponte (`AiToolClient.discover`) che hanno il tool
     * della ricerca nel catalogo. Il catalogo stabilisce anche il protocollo 2 (i risultati tipizzati). Un'app con
     * un'altra firma non risponde e resta fuori. In ordine di nome.
     */
    suspend fun discoverApps(client: AiToolClient): List<FluidPampaApp> = client.discover().mapNotNull { host ->
      val catalog = runCatching { client.catalog(host) }.getOrNull() ?: return@mapNotNull null
      if (catalog.tools.none { it.name == FluidSearchBridge.ToolName }) null else FluidPampaApp(host.packageName, host.authority, catalog.appLabel)
    }.sortedBy { it.label.lowercase() }

    /** Apre il risultato nella sua app (il suo indirizzo, solo in quell'app). Falso se l'app non sa aprirlo. */
    fun open(context: Context, hit: FluidPampaHit): Boolean {
      val link = hit.link ?: return launch(context, hit.app)
      val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link)).setPackage(hit.app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      return try {
        context.startActivity(intent)
        true
      } catch (e: ActivityNotFoundException) {
        launch(context, hit.app)
      }
    }

    /** Apre l'app (per sbloccarla, o quando un risultato non ha indirizzo). */
    fun launch(context: Context, app: FluidPampaApp): Boolean {
      val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return false
      return try {
        context.startActivity(intent)
        true
      } catch (e: ActivityNotFoundException) {
        false
      }
    }
  }
}

/** Le parole di «Cerca in Pampa». **In italiano di serie.** */
@Immutable
data class FluidPampaSearchLabels(
  val prompt: String = "Cerca in Pampa",
  /** «Cercato sul telefono, in %d app». */
  val searchedIn: String = "Cercato sul telefono, in %d app",
  /** «Cerco in %d app…», mentre qualcuna non ha risposto. */
  val searching: String = "Cerco in %d app…",
  /** «%s è bloccata». */
  val locked: String = "%s è bloccata",
  /** Sotto l'app bloccata. */
  val unlock: String = "Aprila per vedere i risultati",
  /** «Non hanno risposto: %s». */
  val noAnswer: String = "Non hanno risposto: %s",
  /** Nessun risultato in nessuna app. */
  val none: String = "Nessun risultato per «%s» nelle app Pampa",
  val byMeaning: String = "per significato",
)

/** Lo stato di «Cerca in Pampa» in una pagina: il testo, il campo aperto e i risultati per app. */
@Stable
class FluidPampaSearchState internal constructor() {
  var query: String by mutableStateOf("")
  var active: Boolean by mutableStateOf(false)

  /** I risultati di adesso, per app. */
  var results: FluidPampaResults by mutableStateOf(FluidPampaResults.Empty)
    internal set

  internal val shown = HashSet<String>()
  private var shownFor = ""

  internal fun arrived(id: String): Boolean {
    if (shownFor != results.query) {
      shownFor = results.query
      shown.clear()
    }
    return shown.add(id)
  }

  /** Il campo «Cerca in Pampa». */
  fun spec(labels: FluidPampaSearchLabels = FluidPampaSearchLabels(), placement: FluidSearchPlacement = FluidSearchPlacement.Automatic): FluidSearchSpec =
    FluidSearchSpec.search(
      query = query,
      onQueryChange = { query = it },
      prompt = labels.prompt,
      placement = placement,
      active = active,
      onActiveChange = { active = it },
    )
}

/** Lo stato di «Cerca in Pampa» con [search] (null: ancora da creare). */
@Composable
fun rememberFluidPampaSearchState(search: FluidPampaSearch?): FluidPampaSearchState {
  val state = remember { FluidPampaSearchState() }
  LaunchedEffect(search) {
    snapshotFlow { state.query }.distinctUntilChanged().collectLatest { query ->
      if (search == null || query.isBlank()) {
        state.results = FluidPampaResults.Empty
        return@collectLatest
      }
      // Le altre app si chiamano quando la persona si ferma: ogni domanda sveglia i loro processi.
      delay(PampaDebounceMillis)
      search.search(query).collect { state.results = it }
    }
  }
  return state
}

/**
 * Quanto si aspetta dopo l'ultima lettera prima di chiamare le altre app: 250 ms (a occhio, 25/11/2026: ogni domanda puo'
 * avviare il processo di un'app chiusa; meno del ritardo del significato, che parte dopo).
 */
private const val PampaDebounceMillis = 250L

/**
 * I risultati di «Cerca in Pampa» in una pagina: sopra «Cercato sul telefono, in 5 app» (o «Cerco in 2 app…»), poi un
 * gruppo per app con l'intestazione «Pampa Mail · 2» e una riga per risultato (titolo e pezzo con le parole trovate, il
 * tipo e «per significato» sotto). Un'app bloccata ha la sua riga che la apre. I gruppi arrivano quando arrivano le
 * risposte, sfocati (lo fa l'app, non il dito); con «Riduci movimento» fermi.
 *
 * @param appIcon il simbolo di un'app nella riga, o null.
 */
fun LazyListScope.fluidPampaResults(
  state: FluidPampaSearchState,
  onOpen: (FluidPampaHit) -> Unit,
  onOpenApp: (FluidPampaApp) -> Unit = {},
  appIcon: (FluidPampaApp) -> (@Composable () -> Unit)? = { null },
  labels: FluidPampaSearchLabels = FluidPampaSearchLabels(),
) {
  val results = state.results
  if (state.query.isBlank() || results.groups.isEmpty()) return
  item(key = "fluid-pampa:header") { PampaHeader(results, labels) }
  for (group in results.shown) {
    val app = group.app
    item(key = "fluid-pampa:app:${app.authority}") {
      val arrives = remember(app.authority, results.query) { state.arrived("app:${app.authority}") && results.pending > 0 }
      Column(Modifier.fluidSearchArrival(app.authority, arrives)) {
        FluidSectionHeader(
          title = if (group.locked) app.label else "${app.label} · ${group.hits.size}",
          )
        FluidListGroup {
          if (group.locked) {
            FluidListRow(
              title = labels.locked.format(app.label),
              subtitle = labels.unlock,
              leading = appIcon(app),
              onClick = { onOpenApp(app) },
            )
          } else {
            group.hits.forEachIndexed { i, hit ->
              key(hit.id) {
                if (i > 0) FluidListDivider()
                PampaHitRow(hit, appIcon(app), onOpen, labels)
              }
            }
          }
        }
      }
    }
  }
  val silent = results.groups.filter { it.error != null }
  if (silent.isNotEmpty() && results.pending == 0) {
    item(key = "fluid-pampa:silent") {
      Text(
        text = labels.noAnswer.format(silent.joinToString(", ") { it.app.label }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
      )
    }
  }
}

@Composable
private fun PampaHeader(results: FluidPampaResults, labels: FluidPampaSearchLabels) {
  val text = when {
    results.pending > 0 -> labels.searching.format(results.pending)
    results.shown.isEmpty() -> labels.none.format(results.query.trim())
    else -> labels.searchedIn.format(results.groups.size)
  }
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier
      .fillMaxWidth()
      .padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
      .semantics { liveRegion = LiveRegionMode.Polite },
  )
}

@Composable
private fun PampaHitRow(hit: FluidPampaHit, icon: (@Composable () -> Unit)?, onOpen: (FluidPampaHit) -> Unit, labels: FluidPampaSearchLabels) {
  val highlight = fluidSearchHighlight()
  val meta = listOfNotNull(hit.kindLabel, labels.byMeaning.takeIf { hit.byMeaning && !hit.byWords }).joinToString(" · ").ifEmpty { null }
  FluidListRow(
    title = hit.title.toAnnotatedString(highlight),
    subtitle = hit.snippet?.toAnnotatedString(highlight) ?: AnnotatedString(""),
    meta = meta,
    leading = icon,
    onClick = { onOpen(hit) },
    titleMaxLines = 2,
    subtitleMaxLines = 2,
  )
}
