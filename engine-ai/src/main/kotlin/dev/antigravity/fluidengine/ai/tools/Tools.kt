package dev.antigravity.fluidengine.ai.tools

import dev.antigravity.fluidengine.ai.provider.ContentPart
import dev.antigravity.fluidengine.ai.provider.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Una categoria del catalogo a piu' livelli (1.26.0): un'app, un'area di cose che l'assistente sa
 * fare. Lo stadio 1 sceglie la categoria (o nessuna) e le sue sottocategorie; il modello della chat
 * ne apre altre da se' con i tool built-in [ToolRegistry.OPEN_CATEGORY] e [ToolRegistry.OPEN_GROUP].
 * Un'app con un dominio solo non ne dichiara e il catalogo resta piatto, come prima.
 *
 * [id] e' la parola che il modello legge, [label] quella che la UI mostra, [hint] la riga con cui
 * il router capisce quando serve.
 */
interface AiToolCategory {
  val id: String
  val label: String
  val hint: String
}

/**
 * Un gruppo del catalogo a due stadi: lo stadio 1 sceglie i gruppi, lo stadio 2 riceve i tool di
 * quei gruppi. L'app li definisce (di solito con un enum), l'engine non sa cosa contengano.
 *
 * [id] e' la parola che il modello legge nello schema, quindi nella lingua dei tool; [statusKey] e'
 * la chiave del testo di stato che la UI traduce ("Guardo i voti..."); [hint] e' la riga con cui
 * il router capisce a cosa serve il gruppo.
 *
 * Dalla 1.26.0 un gruppo puo' stare in una [category] (e' una sottocategoria) e sotto un altro
 * gruppo ([parent], una sotto-sottocategoria). Un gruppo [loadsWithCategory] si apre insieme alla
 * sua categoria senza che nessuno lo chieda: e' il posto per i tool che servono quasi sempre.
 */
interface AiToolGroup {
  val id: String
  val statusKey: String
  val hint: String
  val category: AiToolCategory? get() = null
  val parent: AiToolGroup? get() = null
  val loadsWithCategory: Boolean get() = false
}

/** La categoria di un gruppo, risalendo i padri: null se il catalogo e' piatto. */
val AiToolGroup.resolvedCategory: AiToolCategory?
  get() = category ?: parent?.resolvedCategory

/**
 * Cio' che un tool restituisce: testo compatto per il modello e, se l'ha trovato, un allegato
 * (immagini, un documento) che l'orchestratore passa al modello come parte di un messaggio se il
 * modello lo regge, o fa tradurre in testo dall'app se non lo regge.
 *
 * [escalate] e' la richiesta di finire il lavoro col modello piu' capace: la alza il tool built-in
 * [ToolRegistry.DEEP_MODEL], e puo' alzarla un tool dell'app che sa di aver appena portato roba da
 * ragionarci sopra. Vale per il resto della domanda, e indietro non si torna.
 */
data class ToolOutput(val text: String, val parts: List<ContentPart> = emptyList(), val escalate: Boolean = false) {
  companion object {
    fun error(message: String): ToolOutput = ToolOutput("errore: $message")
  }
}

/** Le parole con cui chiedere conferma di un'azione: il titolo del tasto e il dettaglio sotto. */
data class ConfirmationText(val title: String, val detail: String? = null)

/**
 * Un tool: un nome, un gruppo, una descrizione e uno schema per il modello, e un [run] che riceve
 * gli argomenti e il contesto dell'app ([C], quello che i tool possono toccare: dati, ora, azioni).
 *
 * I tre flag e [describe] (1.26.0) sono metadati per chi esegue il tool da fuori dell'app — un
 * altro assistente che lo raggiunge attraverso un bridge — e per la UI: dentro l'app un tool che
 * scrive chiede conferma da se', con il suo cancello, e questi valori non cambiano niente.
 */
interface AiTool<C> {
  val name: String
  val group: AiToolGroup
  val description: String
  val parameters: JsonObject

  /** Il risultato e' testo compatto per il modello: righe `chiave: valore`, mai JSON verboso. */
  suspend fun run(args: JsonObject, ctx: C): ToolOutput

  val spec: ToolSpec get() = ToolSpec(name, description, parameters)

  /** Vero se scrive o costa: chi lo chiama da fuori chiede conferma all'utente prima di [run]. */
  val needsConfirmation: Boolean get() = false

  /** Vero se puo' durare piu' di una chiamata (un'installazione, una conversione di file). */
  val longRunning: Boolean get() = false

  /** Vero se agisce nell'app invece di leggere: sparisce quando l'utente spegne le azioni. */
  val isAction: Boolean get() = false

  /** Le parole per la conferma con questi argomenti; null = chi chiama ne inventa di generiche. */
  suspend fun describe(args: JsonObject, ctx: C): ConfirmationText? = null
}

/**
 * Il catalogo: tutti i tool, quelli di un insieme di gruppi, e i tool built-in con cui il modello
 * chiede quello che lo stadio 1 non gli ha dato. [groups] e' l'elenco completo dei gruppi dell'app,
 * nell'ordine in cui il router li legge; [actionGroup] e' quello che sparisce quando le azioni
 * nell'app sono spente.
 *
 * Se qualche gruppo ha una categoria il catalogo e' **gerarchico** ([hierarchical]): al posto di
 * `altri_tool` il modello riceve `apri_categoria` e `apri_sottocategoria`, e l'orchestratore tiene
 * aperti per tutta la conversazione i gruppi gia' aperti.
 */
class ToolRegistry<C>(
  val tools: List<AiTool<C>>,
  val groups: List<AiToolGroup>,
  val actionGroup: AiToolGroup? = null,
) {

  init {
    val duplicates = tools.groupBy { it.name }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "tool duplicati: $duplicates" }
    val duplicateGroups = groups.groupBy { it.id }.filterValues { it.size > 1 }.keys
    require(duplicateGroups.isEmpty()) { "gruppi con lo stesso id: $duplicateGroups" }
    val unknown = tools.map { it.group }.filter { it !in groups }.map { it.id }.distinct()
    require(unknown.isEmpty()) { "tool in gruppi non dichiarati: $unknown" }
    val orphans = groups.filter { it.parent != null && it.parent !in groups }.map { it.id }
    require(orphans.isEmpty()) { "gruppi con un padre non dichiarato: $orphans" }
    val homeless = groups.filter { it.parent != null && it.resolvedCategory == null }.map { it.id }
    require(homeless.isEmpty()) { "gruppi con un padre ma senza categoria: $homeless" }
  }

  /** Le categorie, nell'ordine in cui compaiono nei gruppi; vuoto se il catalogo e' piatto. */
  val categories: List<AiToolCategory> = groups.mapNotNull { it.resolvedCategory }.distinct()

  val hierarchical: Boolean get() = categories.isNotEmpty()

  fun specsFor(groups: Set<AiToolGroup>): List<ToolSpec> = tools.filter { it.group in groups }.map { it.spec }

  fun allSpecs(): List<ToolSpec> = tools.map { it.spec }

  fun find(name: String): AiTool<C>? = tools.firstOrNull { it.name == name }

  fun group(id: String?): AiToolGroup? = groups.firstOrNull { it.id == id?.trim()?.lowercase() }

  fun category(id: String?): AiToolCategory? = categories.firstOrNull { it.id == id?.trim()?.lowercase() }

  /** I gruppi di una categoria, a qualunque livello. */
  fun groupsOf(category: AiToolCategory): List<AiToolGroup> = groups.filter { it.resolvedCategory == category }

  /** Le sottocategorie di primo livello di una categoria. */
  fun topGroupsOf(category: AiToolCategory): List<AiToolGroup> = groupsOf(category).filter { it.parent == null }

  fun childrenOf(group: AiToolGroup): List<AiToolGroup> = groups.filter { it.parent == group }

  /**
   * Cosa si apre aprendo una categoria senza dire quale sottocategoria: i gruppi marcati
   * [AiToolGroup.loadsWithCategory], o tutti quelli di primo livello se l'app non ne ha marcati.
   */
  fun initialGroupsOf(category: AiToolCategory): List<AiToolGroup> =
    topGroupsOf(category).filter { it.loadsWithCategory }.ifEmpty { topGroupsOf(category) }

  /** Quanti tool stanno in un insieme di gruppi: per il tetto dell'orchestratore. */
  fun toolCount(groups: Set<AiToolGroup>): Int = tools.count { it.group in groups }

  /** I gruppi che il modello puo' vedere: tutti, meno quello delle azioni se sono spente. */
  fun visibleGroups(actionsEnabled: Boolean): List<AiToolGroup> =
    groups.filter { actionsEnabled || it != actionGroup }

  val moreTools: ToolSpec = ToolSpec(
    name = MORE_TOOLS,
    description = "Chiede altri strumenti di un gruppo non ancora disponibile. Gruppi: " +
      groups.joinToString("; ") { "${it.id} = ${it.hint}" },
    parameters = Schema.obj(
      mapOf("gruppo" to Schema.str("il gruppo di strumenti che serve", groups.map { it.id })),
      required = listOf("gruppo"),
    ),
  )

  /**
   * Il tool con cui il modello apre una categoria (un'app, un'area) che non ha ancora: al giro
   * dopo arrivano i suoi strumenti di partenza e la lista delle sue sottocategorie. La
   * descrizione elenca le categorie con la loro riga: e' quello che il modello legge per capire
   * dove sta cio' che gli serve, e costa una riga per categoria a ogni giro.
   */
  val openCategoryTool: ToolSpec = ToolSpec(
    name = OPEN_CATEGORY,
    description = "Apre gli strumenti di una categoria (un'app o un'area) non ancora disponibile; da usare quando la domanda " +
      "riguarda qualcosa che gli strumenti attuali non coprono. Categorie: " +
      categories.joinToString("; ") { c -> "${c.id} = ${c.hint} (${topGroupsOf(c).joinToString(", ") { it.id }})" },
    parameters = Schema.obj(
      mapOf("categoria" to Schema.str("la categoria che serve", categories.map { it.id })),
      required = listOf("categoria"),
    ),
  )

  /**
   * Il tool con cui il modello apre una sottocategoria delle categorie gia' aperte: solo quelle
   * non ancora aperte, con la loro riga. Null se non c'e' niente da aprire.
   */
  fun openGroupTool(loadedCategories: Set<AiToolCategory>, loaded: Set<AiToolGroup>, actionsEnabled: Boolean): ToolSpec? {
    val openable = groups.filter { g ->
      g !in loaded && g.resolvedCategory in loadedCategories && (actionsEnabled || g != actionGroup)
    }
    if (openable.isEmpty()) return null
    return ToolSpec(
      name = OPEN_GROUP,
      description = "Apre gli strumenti di una sottocategoria di una categoria gia' aperta. Sottocategorie: " +
        openable.joinToString("; ") { "${it.id} = ${it.hint}" },
      parameters = Schema.obj(
        mapOf("sottocategoria" to Schema.str("la sottocategoria che serve", openable.map { it.id })),
        required = listOf("sottocategoria"),
      ),
    )
  }

  /**
   * Il tool con cui il modello dice "questa e' difficile": l'orchestratore lo mostra solo finche'
   * si sta lavorando col modello della chat e solo se il provider ne ha davvero uno piu' capace,
   * e dal giro dopo il lavoro continua li', con la stessa storia.
   */
  val deepTool: ToolSpec = ToolSpec(
    name = DEEP_MODEL,
    description = "Passa al modello piu' capace per il resto della risposta. Chiamalo SUBITO, prima degli altri strumenti, " +
      "quando il compito e' difficile: confronti su molti dati, un documento da analizzare, un ragionamento in piu' passaggi, " +
      "una domanda aperta. Non serve rifare il lavoro gia' fatto: quello che hai raccolto resta.",
    parameters = Schema.obj(mapOf("motivo" to Schema.str("perche' serve, in poche parole"))),
  )

  companion object {
    const val MORE_TOOLS = "altri_tool"
    const val DEEP_MODEL = "modello_avanzato"
    const val OPEN_CATEGORY = "apri_categoria"
    const val OPEN_GROUP = "apri_sottocategoria"

    /** I nomi che l'orchestratore gestisce da se', senza cercarli fra i tool dell'app. */
    val BUILT_IN: Set<String> = setOf(MORE_TOOLS, DEEP_MODEL, OPEN_CATEGORY, OPEN_GROUP)
  }
}

/** Gli schemi JSON dei parametri, brevi: ogni parola nello schema costa token a ogni giro. */
object Schema {
  fun obj(properties: Map<String, JsonObject>, required: List<String> = emptyList()): JsonObject = buildJsonObject {
    put("type", "object")
    put("properties", buildJsonObject { properties.forEach { (name, schema) -> put(name, schema) } })
    if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
  }

  fun str(description: String, enum: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    enum?.let { values -> put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) }
  }

  fun int(description: String, minimum: Int? = null, maximum: Int? = null): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
    minimum?.let { put("minimum", it) }
    maximum?.let { put("maximum", it) }
  }

  fun bool(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
  }

  fun strArray(description: String, enum: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "array")
    put("description", description)
    put(
      "items",
      buildJsonObject {
        put("type", "string")
        enum?.let { values -> put("enum", buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }) }
      },
    )
  }
}

/** Lettura tollerante degli argomenti: il modello scrive numeri come stringhe e viceversa. */
object Args {
  fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
  fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull?.toInt() ?: it.contentOrNull?.trim()?.toDoubleOrNull()?.toInt() }
  fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.trim()?.toDoubleOrNull() }
  fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.trim()?.toBooleanStrictOrNull() }
  fun JsonObject.list(key: String): List<String> = (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}

/**
 * Il testo che torna al modello: righe brevi, un budget di caratteri (~600 token) oltre il quale
 * si tronca dicendo quante righe mancano. I tool tagliano prima le loro liste; questo e' l'ultimo
 * argine, e serve soprattutto su Groq.
 */
object ToolText {
  const val MAX_CHARS = 2400

  fun limit(text: String, maxChars: Int = MAX_CHARS): String {
    if (text.length <= maxChars) return text
    val lines = text.lines()
    val kept = StringBuilder()
    var count = 0
    for (line in lines) {
      if (kept.length + line.length + 1 > maxChars - 40) break
      kept.append(line).append('\n')
      count++
    }
    val missing = lines.size - count
    return kept.toString().trimEnd() + "\n… (altre $missing righe omesse)"
  }

  class Builder(private val maxChars: Int = MAX_CHARS) {
    private val lines = mutableListOf<String>()
    fun line(text: String) { lines += text }
    fun line(key: String, value: Any?) { lines += "$key: ${value ?: "—"}" }
    fun blank() { lines += "" }
    fun build(): String = limit(lines.joinToString("\n").trim(), maxChars)
  }

  inline fun build(maxChars: Int = MAX_CHARS, block: Builder.() -> Unit): String = Builder(maxChars).apply(block).build()

  /**
   * La scorciatoia per il caso comune: un tool che risponde con solo testo. [maxChars] serve al
   * tool che raccoglie davvero tanto (tutti i voti dell'anno, tutte le materie insieme) e che
   * troncato a duemila caratteri farebbe rispondere il modello su meta' dei dati — ma deve restare
   * sotto al tetto dell'orchestratore (`AiOrchestratorConfig.toolTextChars`), che ha l'ultima parola.
   */
  inline fun output(maxChars: Int = MAX_CHARS, block: Builder.() -> Unit): ToolOutput = ToolOutput(build(maxChars, block))
}
