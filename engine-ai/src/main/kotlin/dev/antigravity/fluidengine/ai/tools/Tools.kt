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
 * Un gruppo del catalogo a due stadi: lo stadio 1 sceglie i gruppi, lo stadio 2 riceve i tool di
 * quei gruppi. L'app li definisce (di solito con un enum), l'engine non sa cosa contengano.
 *
 * [id] e' la parola che il modello legge nello schema, quindi nella lingua dei tool; [statusKey] e'
 * la chiave del testo di stato che la UI traduce ("Guardo i voti..."); [hint] e' la riga con cui il
 * router capisce a cosa serve il gruppo.
 */
interface AiToolGroup {
  val id: String
  val statusKey: String
  val hint: String
}

/**
 * Cio' che un tool restituisce: testo compatto per il modello e, se l'ha trovato, un allegato
 * (immagini, un documento) che l'orchestratore passa al modello come parte di un messaggio se il
 * modello lo regge, o fa tradurre in testo dall'app se non lo regge.
 */
data class ToolOutput(val text: String, val parts: List<ContentPart> = emptyList()) {
  companion object {
    fun error(message: String): ToolOutput = ToolOutput("errore: $message")
  }
}

/**
 * Un tool: un nome, un gruppo, una descrizione e uno schema per il modello, e un [run] che riceve
 * gli argomenti e il contesto dell'app ([C], quello che i tool possono toccare: dati, ora, azioni).
 */
interface AiTool<C> {
  val name: String
  val group: AiToolGroup
  val description: String
  val parameters: JsonObject

  /** Il risultato e' testo compatto per il modello: righe `chiave: valore`, mai JSON verboso. */
  suspend fun run(args: JsonObject, ctx: C): ToolOutput

  val spec: ToolSpec get() = ToolSpec(name, description, parameters)
}

/**
 * Il catalogo: tutti i tool, quelli di un insieme di gruppi, e il tool-scappatoia con cui il
 * modello chiede un gruppo che lo stadio 1 non gli ha dato. [groups] e' l'elenco completo dei
 * gruppi dell'app, nell'ordine in cui il router li legge; [actionGroup] e' quello che sparisce
 * quando le azioni nell'app sono spente.
 */
class ToolRegistry<C>(
  val tools: List<AiTool<C>>,
  val groups: List<AiToolGroup>,
  val actionGroup: AiToolGroup? = null,
) {

  init {
    val duplicates = tools.groupBy { it.name }.filterValues { it.size > 1 }.keys
    require(duplicates.isEmpty()) { "tool duplicati: $duplicates" }
    val unknown = tools.map { it.group }.filter { it !in groups }.map { it.id }.distinct()
    require(unknown.isEmpty()) { "tool in gruppi non dichiarati: $unknown" }
  }

  fun specsFor(groups: Set<AiToolGroup>): List<ToolSpec> = tools.filter { it.group in groups }.map { it.spec }

  fun allSpecs(): List<ToolSpec> = tools.map { it.spec }

  fun find(name: String): AiTool<C>? = tools.firstOrNull { it.name == name }

  fun group(id: String?): AiToolGroup? = groups.firstOrNull { it.id == id?.trim()?.lowercase() }

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

  companion object {
    const val MORE_TOOLS = "altri_tool"
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
    put("items", str("", enum).let { item -> buildJsonObject { put("type", "string"); enum?.let { put("enum", item["enum"]!!) } } })
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

  class Builder {
    private val lines = mutableListOf<String>()
    fun line(text: String) { lines += text }
    fun line(key: String, value: Any?) { lines += "$key: ${value ?: "—"}" }
    fun blank() { lines += "" }
    fun build(): String = limit(lines.joinToString("\n").trim())
  }

  inline fun build(block: Builder.() -> Unit): String = Builder().apply(block).build()

  /** La scorciatoia per il caso comune: un tool che risponde con solo testo. */
  inline fun output(block: Builder.() -> Unit): ToolOutput = ToolOutput(build(block))
}
