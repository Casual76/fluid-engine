package dev.antigravity.fluidengine.search

import dev.antigravity.fluidengine.ai.bridge.BridgeCalls
import dev.antigravity.fluidengine.ai.bridge.RemoteResult
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.AiToolGroup
import dev.antigravity.fluidengine.ai.tools.ToolOutcome
import dev.antigravity.fluidengine.ai.tools.ToolOutput
import dev.antigravity.fluidengine.ai.tools.ToolResultDetails
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

// F8.22 · «Cerca in Pampa» (`ricerca-in-tutte-le-app`) passa dal **ponte firmato che le app gia' usano con PampAI**
// (`engine-ai-bridge`, nota del piano): ogni app aggiunge ai suoi tool quello della ricerca ([FluidSearchBridge.searchTool])
// e il suo `AiToolHostProvider` lo espone come gli altri, solo alle app firmate come lei. Chi cerca in tutte le app
// (`FluidPampaSearch`) li chiama insieme e mette i risultati per app. Lo stesso tool lo trova PampAI: l'assistente puo'
// cercare nelle note e nella posta senza un tool scritto a mano per ogni app.

/**
 * Cosa un'app mostra di se' alle altre app Pampa quando cercano.
 *
 * @property kinds i tipi che si possono trovare da fuori (null: tutti). Una nota privata, un diario, si tengono fuori.
 * @property kindLabels il nome di ogni tipo, per il gruppo nella pagina di chi cerca («Note», «Allegati»).
 * @property snippets manda i pezzi di testo (di serie si'); falso: solo i titoli.
 * @property locked vero se l'app e' bloccata adesso (il blocco con l'impronta di `engine-security`): allora non risponde
 *   coi dati, e chi cerca mostra «… e' bloccata».
 * @property maxHits quanti risultati al piu' per domanda.
 * @property embedder il modello del significato dell'app, se ne ha uno: lo usa quando chi cerca non manda il vettore.
 */
class FluidSearchExposure(
  val kinds: Set<String>? = null,
  val kindLabels: Map<String, String> = emptyMap(),
  val snippets: Boolean = true,
  val locked: () -> Boolean = { false },
  val maxHits: Int = 20,
  val embedder: FluidTextEmbedder? = null,
)

/** I tool della ricerca fra app, per il registry dell'app (quello che il suo `AiToolHostProvider` espone). */
object FluidSearchBridge {
  /**
   * L'azione dell'intent-filter che chi risponde mette sul suo provider del ponte: rende l'app visibile a chi cerca
   * (Android 11+, i `<queries>` del manifest del modulo). Il provider resta quello di `AiToolHostProvider`.
   */
  const val ActionSearch: String = "dev.antigravity.fluidengine.search.SEARCH"

  /** Il nome del tool della ricerca: e' cosi' che «Cerca in Pampa» lo riconosce in ogni app. */
  const val ToolName: String = "cerca_in_pampa"

  /** Il nome del tool dei vettori: l'app che tiene il modello lo presta alle altre ([remoteEmbedder]). */
  const val EmbedToolName: String = "vettori_del_testo"

  /** Quanti testi al piu' per una chiamata a [EmbedToolName] (768 float a testo: 32 stanno ben sotto il mega di Binder). */
  const val MaxEmbedTexts: Int = 32

  /** Il gruppo dei due tool, se l'app non ne ha uno suo. */
  val Group: AiToolGroup = object : AiToolGroup {
    override val id: String = "ricerca"
    override val statusKey: String = "searching"
    override val hint: String = "cercare nei dati di questa app: note, messaggi, documenti, per parole o per significato"
    override val toolTimeoutMillis: Long? = 10_000L
  }

  /**
   * Il tool della ricerca nell'[index], per gli assistenti e per «Cerca in Pampa». Legge e basta (niente conferma).
   * I campi riservati non si cercano e non escono mai ([FluidSearchQuery.includeSensitive] falso).
   */
  fun <C> searchTool(index: FluidSearchIndex, exposure: FluidSearchExposure = FluidSearchExposure(), group: AiToolGroup = Group): AiTool<C> =
    SearchTool(index, exposure, group)

  /** Il tool dei vettori: l'app che ha il modello del significato lo presta alle altre app firmate come lei. */
  fun <C> embedTool(embedder: FluidTextEmbedder, group: AiToolGroup = Group): AiTool<C> = EmbedTool(embedder, group)

  /**
   * Il modello del significato di un'altra app ([authority], col suo [EmbedToolName]): un'app senza il modello (157 MB)
   * chiede i vettori a quella che lo tiene. Restituisce null se l'app non risponde o non lo presta.
   */
  suspend fun remoteEmbedder(calls: BridgeCalls, authority: String): FluidTextEmbedder? {
    val first = runEmbed(calls, authority, emptyList()) ?: return null
    return RemoteEmbedder(calls, authority, first.first)
  }

  // --- Il formato ------------------------------------------------------------------------------------------------

  internal fun encodeVector(v: FloatArray): String {
    val buffer = ByteBuffer.allocate(v.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    for (x in v) buffer.putFloat(x)
    return Base64.getEncoder().encodeToString(buffer.array())
  }

  internal fun decodeVector(text: String): FloatArray? = runCatching {
    val bytes = Base64.getDecoder().decode(text)
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    FloatArray(bytes.size / 4) { buffer.getFloat(it * 4) }
  }.getOrNull()

  internal fun marks(ranges: List<IntRange>): JsonArray = buildJsonArray {
    for (r in ranges) add(buildJsonArray { add(JsonPrimitive(r.first)); add(JsonPrimitive(r.last)) })
  }

  internal fun ranges(json: JsonElement?): List<IntRange> = (json as? JsonArray).orEmpty().mapNotNull { pair ->
    val a = (pair as? JsonArray)?.getOrNull(0)?.let { (it as? JsonPrimitive)?.intOrNull } ?: return@mapNotNull null
    val b = (pair as? JsonArray)?.getOrNull(1)?.let { (it as? JsonPrimitive)?.intOrNull } ?: return@mapNotNull null
    if (b >= a) a..b else null
  }

  /** La risposta di una ricerca, nel formato del ponte (i risultati tipizzati del protocollo 2). */
  internal fun resultJson(results: FluidSearchResults, exposure: FluidSearchExposure, locked: Boolean): JsonObject = buildJsonObject {
    put("format", 1)
    put("locked", locked)
    put("total", results.total)
    put("meaning", results.meaning)
    results.corrected?.let { put("corrected", it) }
    putJsonArray("hits") {
      for (h in results.hits) {
        add(buildJsonObject {
          put("id", h.id)
          put("kind", h.kind)
          exposure.kindLabels[h.kind]?.let { put("kindLabel", it) }
          h.title?.let { put("title", it.text); put("titleMarks", marks(it.highlights)) }
          if (exposure.snippets) h.snippet?.let { put("snippet", it.text); put("snippetMarks", marks(it.highlights)) }
          h.timestamp?.let { put("timestamp", it) }
          h.link?.let { put("link", it) }
          put("byWords", h.byWords)
          put("byMeaning", h.byMeaning)
          put("score", h.score.toDouble())
        })
      }
    }
  }

  /** Le righe per un modello di linguaggio (PampAI): titolo, tipo e pezzo, una per risultato. */
  internal fun modelText(results: FluidSearchResults, exposure: FluidSearchExposure, locked: Boolean): String = when {
    locked -> "l'app e' bloccata: va aperta e sbloccata dalla persona"
    results.hits.isEmpty() -> "nessun risultato per: ${results.query}"
    else -> buildString {
      append("risultati: ").append(results.total).append('\n')
      for (h in results.hits) {
        append("- ").append(h.title?.text ?: h.id)
        exposure.kindLabels[h.kind]?.let { append(" (").append(it).append(')') }
        if (exposure.snippets) h.snippet?.let { append(": ").append(it.text) }
        append('\n')
      }
    }.trimEnd()
  }

  private class SearchTool<C>(
    private val index: FluidSearchIndex,
    private val exposure: FluidSearchExposure,
    override val group: AiToolGroup,
  ) : AiTool<C> {
    override val name: String = ToolName
    override val description: String =
      "Cerca nei dati di questa app (note, messaggi, documenti, foto) per parole, anche con errori di battitura, o per significato. " +
        "Restituisce titolo e pezzo di testo dei risultati."
    override val parameters: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("query") { put("type", "string"); put("description", "le parole da cercare") }
        putJsonObject("limit") { put("type", "integer"); put("minimum", 1); put("maximum", 50) }
        putJsonObject("kinds") { put("type", "array"); putJsonObject("items") { put("type", "string") } }
        putJsonObject("meaning") { put("type", "string"); put("description", "il vettore della domanda, se chi chiama lo ha") }
        putJsonObject("space") { put("type", "string") }
      }
      putJsonArray("required") { add(JsonPrimitive("query")) }
    }

    override suspend fun run(args: JsonObject, ctx: C): ToolOutput {
      val text = (args["query"] as? JsonPrimitive)?.contentOrNull.orEmpty()
      val limit = ((args["limit"] as? JsonPrimitive)?.intOrNull ?: exposure.maxHits).coerceIn(1, exposure.maxHits)
      val asked = (args["kinds"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
      val allowed = exposure.kinds
      val kinds = when {
        allowed == null -> asked
        asked.isEmpty() -> allowed
        else -> asked.intersect(allowed)
      }
      val empty = FluidSearchResults(query = text, hits = emptyList(), total = 0)
      // La risposta «bloccata», senza dati: anche quando la persona blocca l'app durante il calcolo (R1).
      val lockedOutput = ToolOutput(modelText(empty, exposure, true), result = ToolResultDetails(ToolOutcome.COMPLETED, data = resultJson(empty, exposure, true)))
      if (exposure.locked()) return lockedOutput
      if (text.isBlank() || (allowed != null && kinds.isEmpty())) {
        return ToolOutput(modelText(empty, exposure, false), result = ToolResultDetails(ToolOutcome.COMPLETED, data = resultJson(empty, exposure, false)))
      }
      // Il vettore della domanda: quello che manda chi cerca (stesso spazio), o quello del modello dell'app.
      val space = (args["space"] as? JsonPrimitive)?.contentOrNull
      var meaning: FloatArray? = (args["meaning"] as? JsonPrimitive)?.contentOrNull?.let { decodeVector(it) }
      if (meaning != null && (space == null || !index.meaningSpace().orEmpty().startsWith("$space/"))) meaning = null
      if (meaning == null) {
        val model = exposure.embedder
        if (model != null && index.usesMeaningSpace(model.space) && text.trim().length >= FluidSearchDefaults.MeaningMinChars) {
          meaning = runCatching { model.embed(listOf(text.trim())).firstOrNull() }.getOrNull()
        }
      }
      // Il blocco si rilegge dopo ogni sospensione: il vettore e la ricerca prendono tempo, e la persona può bloccare
      // l'app nel frattempo. Bloccata, niente indice e niente risultati.
      if (exposure.locked()) return lockedOutput
      val results = index.search(FluidSearchQuery(text, kinds, limit, typos = true, meaning = meaning, includeSensitive = false))
      if (exposure.locked()) return lockedOutput
      return ToolOutput(modelText(results, exposure, false), result = ToolResultDetails(ToolOutcome.COMPLETED, data = resultJson(results, exposure, false)))
    }
  }

  private class EmbedTool<C>(private val embedder: FluidTextEmbedder, override val group: AiToolGroup) : AiTool<C> {
    override val name: String = EmbedToolName
    override val description: String = "Calcola i vettori del significato di brevi testi, sul telefono (per la ricerca delle altre app)."
    override val parameters: JsonObject = buildJsonObject {
      put("type", "object")
      putJsonObject("properties") {
        putJsonObject("texts") { put("type", "array"); put("maxItems", MaxEmbedTexts); putJsonObject("items") { put("type", "string") } }
      }
      putJsonArray("required") { add(JsonPrimitive("texts")) }
    }

    override suspend fun run(args: JsonObject, ctx: C): ToolOutput {
      val texts = (args["texts"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
      val vectors = if (texts.isEmpty()) emptyList() else embedder.embed(texts)
      val data = buildJsonObject {
        put("space", embedder.space.id)
        put("dimensions", embedder.space.dimensions)
        putJsonArray("vectors") { vectors.forEach { add(JsonPrimitive(encodeVector(it))) } }
      }
      return ToolOutput("vettori: ${vectors.size}", result = ToolResultDetails(ToolOutcome.COMPLETED, data = data))
    }
  }

  private suspend fun runEmbed(calls: BridgeCalls, authority: String, texts: List<String>): Pair<FluidEmbeddingSpace, List<FloatArray>>? {
    val args = buildJsonObject { putJsonArray("texts") { texts.forEach { add(JsonPrimitive(it)) } } }
    val result = calls.run(authority, EmbedToolName, args, confirmed = false, requestId = UUID.randomUUID().toString(), language = "it")
    val data = (result as? RemoteResult.Done)?.result?.data ?: return null
    val id = (data["space"] as? JsonPrimitive)?.contentOrNull ?: return null
    val dims = (data["dimensions"] as? JsonPrimitive)?.intOrNull ?: return null
    val vectors = (data["vectors"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::decodeVector) }
    if (vectors.size != texts.size) return null
    return FluidEmbeddingSpace(id, dims) to vectors
  }

  private class RemoteEmbedder(private val calls: BridgeCalls, private val authority: String, override val space: FluidEmbeddingSpace) : FluidTextEmbedder {
    override suspend fun embed(texts: List<String>): List<FloatArray> =
      texts.chunked(MaxEmbedTexts).flatMap { chunk ->
        runEmbed(calls, authority, chunk)?.second ?: error("l'app del modello non ha risposto")
      }
  }
}

/** Un'app Pampa che risponde a «Cerca in Pampa»: dove sta il suo ospite del ponte e come si chiama. */
data class FluidPampaApp(val packageName: String, val authority: String, val label: String)

/** Un risultato di un'altra app. */
data class FluidPampaHit(
  val app: FluidPampaApp,
  val id: String,
  val kind: String,
  val kindLabel: String?,
  val title: FluidSearchSnippet,
  val snippet: FluidSearchSnippet?,
  val timestamp: Long?,
  val link: String?,
  val byMeaning: Boolean,
  val byWords: Boolean,
)

/**
 * I risultati di un'app.
 *
 * @property pending la risposta non e' ancora arrivata.
 * @property locked l'app e' bloccata (con l'impronta): si apre lei.
 * @property error l'app non ha risposto (in parole semplici), o null.
 */
data class FluidPampaGroup(
  val app: FluidPampaApp,
  val hits: List<FluidPampaHit> = emptyList(),
  val total: Int = 0,
  val pending: Boolean = true,
  val locked: Boolean = false,
  val error: String? = null,
)

/** «Cerca in Pampa» adesso: un gruppo per app, nell'ordine delle app. */
data class FluidPampaResults(
  val query: String,
  val groups: List<FluidPampaGroup>,
  val meaning: Boolean = false,
  /** Da quanto e' partita la domanda, in millisecondi (all'ultima risposta: quanto e' durata tutta). */
  val tookMillis: Long = 0,
) {
  /** Quante app devono ancora rispondere. */
  val pending: Int get() = groups.count { it.pending }

  /** I gruppi da mostrare: quelli con dei risultati, quelli bloccati e quelli che non hanno risposto. */
  val shown: List<FluidPampaGroup> get() = groups.filter { it.hits.isNotEmpty() || it.locked || it.error != null }

  companion object {
    val Empty: FluidPampaResults = FluidPampaResults("", emptyList())
  }
}

/** Legge la risposta di un'app (il formato di [FluidSearchBridge.resultJson]). */
internal fun pampaGroupOf(app: FluidPampaApp, result: RemoteResult): FluidPampaGroup = when (result) {
  is RemoteResult.Done -> {
    val data = result.result?.data
    if (data == null) {
      FluidPampaGroup(app, pending = false, error = "da aggiornare")
    } else {
      val hits = (data["hits"] as? JsonArray).orEmpty().mapNotNull { h ->
        val o = h as? JsonObject ?: return@mapNotNull null
        val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
        val title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: id
        FluidPampaHit(
          app = app,
          id = id,
          kind = (o["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
          kindLabel = (o["kindLabel"] as? JsonPrimitive)?.contentOrNull,
          title = FluidSearchSnippet(title, FluidSearchBridge.ranges(o["titleMarks"]).filter { it.last < title.length }),
          snippet = (o["snippet"] as? JsonPrimitive)?.contentOrNull?.let { s ->
            FluidSearchSnippet(s, FluidSearchBridge.ranges(o["snippetMarks"]).filter { it.last < s.length })
          },
          timestamp = (o["timestamp"] as? JsonPrimitive)?.longOrNull,
          link = (o["link"] as? JsonPrimitive)?.contentOrNull,
          byMeaning = (o["byMeaning"] as? JsonPrimitive)?.booleanOrNull ?: false,
          byWords = (o["byWords"] as? JsonPrimitive)?.booleanOrNull ?: true,
        )
      }
      FluidPampaGroup(
        app = app,
        hits = hits,
        total = (data["total"] as? JsonPrimitive)?.intOrNull ?: hits.size,
        pending = false,
        locked = (data["locked"] as? JsonPrimitive)?.booleanOrNull ?: false,
      )
    }
  }
  is RemoteResult.Error -> FluidPampaGroup(app, pending = false, error = result.message)
  else -> FluidPampaGroup(app, pending = false, error = "risposta inattesa")
}
