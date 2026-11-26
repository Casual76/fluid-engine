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
 *
 * Dalla 3.0.0 un gruppo dice anche **quanto possono durare i suoi tool**: [toolTimeoutMillis], il
 * tempo di lavoro, e [awaitsConfirmation], se un suo tool puo' fermarsi ad aspettare il si'
 * dell'utente. Sono due numeri che non si dichiarano a parte: l'orchestratore somma al tempo di
 * lavoro la finestra del cancello delle conferme ([AiOrchestrator.toolTimeoutMillis]), cosi' una
 * scheda Conferma/Annulla non sparisce a meta' perche' il tetto dei tool era piu' corto di lei.
 */
interface AiToolGroup {
  val id: String
  val statusKey: String
  val hint: String
  val category: AiToolCategory? get() = null
  val parent: AiToolGroup? get() = null
  val loadsWithCategory: Boolean get() = false

  /**
   * Quanto puo' lavorare un tool di questo gruppo, **senza** contare l'attesa di una conferma
   * (3.0.0); null = il tetto dell'orchestratore ([AiOrchestratorConfig.toolTimeoutMillis]). Chi legge
   * da una rete dichiara qui i suoi venti secondi; chi scrive dichiara quanto ci mette a scrivere,
   * e se aspetta l'utente lo dice con [awaitsConfirmation] senza sommare niente a mano.
   */
  val toolTimeoutMillis: Long? get() = null

  /**
   * Vero se un tool di questo gruppo puo' fermarsi dentro `run` ad aspettare il si' dell'utente
   * (un [AiConfirmationGate]; 3.0.0). All'attesa del tool l'orchestratore aggiunge la finestra del
   * cancello, una per ogni conferma che puo' esserci in coda nello stesso giro. Vale per ogni tool del
   * gruppo, anche per quelli che leggono e basta: il loro tetto e' solo piu' largo, mentre un tool che
   * chiede e non riceve la finestra perderebbe la scheda di conferma a meta' attesa.
   */
  val awaitsConfirmation: Boolean get() = false
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
data class ToolOutput(val text: String, val parts: List<ContentPart> = emptyList(), val escalate: Boolean = false,
  val result: ToolResultDetails? = null,
) {
  companion object {
    fun error(message: String): ToolOutput = ToolOutput("errore: $message", result = ToolResultDetails(ToolOutcome.ERROR, error = ToolError("tool_error", message)))
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
 * scrive chiede conferma da se', con il suo cancello, e questi valori non cambiano niente. Con una
 * sola eccezione (3.0.0): il tempo. Un tool con [needsConfirmation] aspetta l'utente dentro `run`,
 * e l'orchestratore lo sa ([awaitsConfirmation]): al suo tetto ([timeoutMillis]) somma la finestra
 * delle conferme, invece di tagliarlo mentre la scheda e' ancora a schermo.
 */
interface AiTool<C> {
  val name: String
  val group: AiToolGroup
  val description: String
  val parameters: JsonObject

  /** Il risultato e' testo compatto per il modello: righe `chiave: valore`, mai JSON verboso. */
  suspend fun run(args: JsonObject, ctx: C): ToolOutput

  /** Additive dispatch hook; legacy tools continue to implement only run(args, ctx). */
  suspend fun run(args: JsonObject, ctx: C, execution: ToolExecution): ToolOutput = run(args, ctx)

  val spec: ToolSpec get() = ToolSpec(name, description, parameters)

  /** Vero se scrive o costa: chi lo chiama da fuori chiede conferma all'utente prima di [run]. */
  val needsConfirmation: Boolean get() = false

  /** Vero se puo' durare piu' di una chiamata (un'installazione, una conversione di file). */
  val longRunning: Boolean get() = false

  /** Vero se agisce nell'app invece di leggere: sparisce quando l'utente spegne le azioni. */
  val isAction: Boolean get() = false

  /** Le parole per la conferma con questi argomenti; null = chi chiama ne inventa di generiche. */
  suspend fun describe(args: JsonObject, ctx: C): ConfirmationText? = null

  /**
   * Quanto puo' lavorare questo tool, **senza** l'attesa di una conferma (3.0.0). Di serie e' quello
   * del gruppo ([AiToolGroup.toolTimeoutMillis]); null = il tetto dell'orchestratore.
   */
  val timeoutMillis: Long? get() = group.toolTimeoutMillis

  /**
   * Vero se [run] puo' fermarsi ad aspettare il si' dell'utente (3.0.0): di serie [needsConfirmation]
   * oppure il gruppo lo dichiara. Un tool di un gruppo che ne ospita di due tipi (leggere e scrivere)
   * dice di si' anche quando non aspetta: il tetto e' piu' largo, e un tool che finisce prima non lo usa.
   * Un tool che chiede in un gruppo che non lo dichiara scrive `override val awaitsConfirmation = true`.
   */
  val awaitsConfirmation: Boolean get() = needsConfirmation || group.awaitsConfirmation
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
 *
 * Dalla 4.0 (F9.2) alcuni gruppi possono essere **differiti** ([deferred]): sono gli strumenti delle
 * altre app e dei connettori, raggiungibili ma non di serie. Un gruppo differito sta in [groups] (i suoi
 * tool hanno un gruppo dichiarato) ma non entra mai da solo nel giro: non e' fra le categorie di
 * `apri_categoria` ne' fra i gruppi di `altri_tool` e di `apri_sottocategoria`, non e' nella "lista
 * intera" che si manda a OpenRouter, e il router non lo vede se l'app gli passa [homeGroups] e
 * [categories]. I suoi tool si trovano con il tool built-in `cerca_strumenti` ([searchTool]), che
 * carica i tool trovati uno per uno, o li apre l'app (un pre-router che sa gia' cosa serve li passa in
 * `AskInput.preselectedGroups`). [searchTerms] sono le parole in piu' per la ricerca: il vocabolario di
 * un'app collegata (materie, fermate, localita'), le parole del mestiere.
 */
class ToolRegistry<C>(
  val tools: List<AiTool<C>>,
  val groups: List<AiToolGroup>,
  val actionGroup: AiToolGroup? = null,
  /** I gruppi raggiungibili solo con la ricerca o dall'app (4.0, F9.2); vuoto = tutto come prima. */
  val deferred: Set<AiToolGroup> = emptySet(),
  /** Parole in piu' per la ricerca dei tool, per gruppo (4.0, F9.2). */
  val searchTerms: Map<AiToolGroup, List<String>> = emptyMap(),
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
    val undeclared = deferred.filter { it !in groups }.map { it.id }
    require(undeclared.isEmpty()) { "gruppi differiti non dichiarati: $undeclared" }
  }

  /**
   * I gruppi di casa (4.0, F9.2): tutti meno i [deferred]. Sono quelli da dare al router
   * (`AiRouter(groups = registry.homeGroups, categories = registry.categories, ...)`).
   */
  val homeGroups: List<AiToolGroup> = groups.filter { it !in deferred }

  /**
   * Le categorie, nell'ordine in cui compaiono nei gruppi; vuoto se il catalogo e' piatto. Dalla 4.0
   * solo quelle dei gruppi di casa: una categoria con tutti i gruppi differiti (un'altra app, un
   * connettore) sta in [deferredCategories].
   */
  val categories: List<AiToolCategory> = homeGroups.mapNotNull { it.resolvedCategory }.distinct()

  /** Le categorie raggiungibili solo con la ricerca (4.0, F9.2): le altre app, i connettori. */
  val deferredCategories: List<AiToolCategory> = groups.filter { it in deferred }.mapNotNull { it.resolvedCategory }.distinct() - categories.toSet()

  val hierarchical: Boolean get() = categories.isNotEmpty()

  /** Vero se c'e' qualcosa da cercare: almeno un gruppo differito con dei tool (4.0, F9.2). */
  val searchable: Boolean = tools.any { it.group in deferred }

  /** La ricerca su tutti i tool del catalogo (4.0, F9.2): si costruisce alla prima domanda che la usa. */
  val search: ToolSearch<C> by lazy { ToolSearch(tools, searchTerms) }

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

  /**
   * I gruppi che il modello puo' vedere senza cercarli: tutti quelli di casa, meno quello delle azioni
   * se sono spente. Dalla 4.0 i [deferred] non ci sono.
   */
  fun visibleGroups(actionsEnabled: Boolean): List<AiToolGroup> =
    homeGroups.filter { actionsEnabled || it != actionGroup }

  val moreTools: ToolSpec = ToolSpec(
    name = MORE_TOOLS,
    description = "Chiede altri strumenti di un gruppo non ancora disponibile. Gruppi: " +
      homeGroups.joinToString("; ") { "${it.id} = ${it.hint}" },
    parameters = Schema.obj(
      mapOf("gruppo" to Schema.str("il gruppo di strumenti che serve", homeGroups.map { it.id })),
      required = listOf("gruppo"),
    ),
  )

  /**
   * Il tool con cui il modello cerca gli strumenti che non ha (4.0, F9.2): le altre app, i connettori,
   * e i gruppi di casa non ancora aperti. Al giro dopo arrivano i tool trovati, uno per uno. La
   * descrizione nomina le app e i connettori raggiungibili (solo il nome: una riga per tutti), perche'
   * il modello sappia che esistono; le loro righe e i loro schemi non costano niente finche' non servono.
   */
  val searchTool: ToolSpec = ToolSpec(
    name = SEARCH_TOOLS,
    description = "Cerca e carica strumenti che non hai ancora, quando quelli attuali non bastano" +
      (if (deferredCategories.isNotEmpty()) ". Ci sono anche: " + deferredCategories.joinToString(", ") { it.label } else "") +
      ". Scrivi cosa ti serve in poche parole; gli strumenti trovati arrivano al passo dopo.",
    parameters = Schema.obj(
      mapOf("cosa" to Schema.str("cosa serve, in poche parole (es. \"previsioni meteo domani\")")),
      required = listOf("cosa"),
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
    val openable = homeGroups.filter { g ->
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

    /** Il tool built-in della ricerca dei tool (4.0, F9.2): [searchTool]. */
    const val SEARCH_TOOLS = "cerca_strumenti"

    /** I nomi che l'orchestratore gestisce da se', senza cercarli fra i tool dell'app. */
    val BUILT_IN: Set<String> = setOf(MORE_TOOLS, DEEP_MODEL, OPEN_CATEGORY, OPEN_GROUP, SEARCH_TOOLS)
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
    require(maxChars >= 0)
    val lines = text.lines()
    val kept = StringBuilder()
    var count = 0
    for (line in lines) {
      if (kept.length + line.length + 1 > maxChars - 40) break
      kept.append(line).append('\n')
      count++
    }
    if (count > 0) return (kept.toString().trimEnd() + "\n\u2026 (${omitted(lines.size - count)})").take(maxChars)
    val marker = "\n\u2026 (testo troncato)"
    if (maxChars <= marker.length) return text.take(maxChars)
    return text.take(maxChars - marker.length) + marker
  }

  /** Cosa manca, al singolare e al plurale (3.0.0): prima "altre 1 righe omesse". */
  fun omitted(missing: Int): String = if (missing == 1) "un'altra riga omessa" else "altre $missing righe omesse"

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
