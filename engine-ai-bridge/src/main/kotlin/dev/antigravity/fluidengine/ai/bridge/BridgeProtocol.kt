package dev.antigravity.fluidengine.ai.bridge

import dev.antigravity.fluidengine.ai.net.asArray
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import dev.antigravity.fluidengine.ai.tools.AiTool
import dev.antigravity.fluidengine.ai.tools.ToolRegistry
import dev.antigravity.fluidengine.ai.tools.resolvedCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/**
 * Il protocollo fra un'app che espone i suoi tool (l'**ospite**, un `ContentProvider`) e l'app
 * che li chiama (il **cliente**, un assistente come PampAI): JSON in stringhe dentro i `Bundle`
 * di `ContentProvider.call`. Puro Kotlin, cosi' si prova sulla JVM. Tutto cio' che attraversa il
 * confine e' qui: chi cambia il formato alza [PROTOCOL] e aggiunge, mai toglie.
 *
 * Metodi: `catalog` (cosa c'e'), `describe` (le parole della conferma), `run` (esegui, o avvia un
 * lavoro lungo), `status` (com'e' il lavoro), `cancel`, `ready` (l'app e' in grado di rispondere?).
 */
object BridgeProtocol {
  const val PROTOCOL = 1

  /** Il meta-data sul `<provider>` dell'ospite che lo fa trovare al cliente. */
  const val META_TOOL_HOST = "dev.antigravity.fluidengine.ai.TOOL_HOST"

  /** Il permesso `signature` sul provider. */
  const val PERMISSION = "dev.antigravity.fluidengine.permission.AI_TOOLS"

  /** Il suffisso dell'autorita': `<applicationId>.ai.tools`. */
  const val AUTHORITY_SUFFIX = ".ai.tools"

  const val METHOD_CATALOG = "catalog"
  const val METHOD_DESCRIBE = "describe"
  const val METHOD_RUN = "run"
  const val METHOD_STATUS = "status"
  const val METHOD_CANCEL = "cancel"
  const val METHOD_READY = "ready"

  /** La chiave del JSON nei Bundle di richiesta e risposta. */
  const val KEY_JSON = "json"

  /** La chiave del `PendingIntent` (Parcelable, non JSON) in una risposta di `status`. */
  const val KEY_PENDING_INTENT = "pendingIntent"

  const val STATE_RUNNING = "running"
  const val STATE_DONE = "done"
  const val STATE_FAILED = "failed"
  const val STATE_UNKNOWN = "unknown"

  val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parse(text: String?): JsonObject? = text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

  /** Il catalogo di un'app: dal suo registry, nel formato che il cliente ricostruisce in [RemoteCatalog]. */
  fun <C> catalogJson(
    registry: ToolRegistry<C>,
    appId: String,
    domain: String,
    appLabel: String,
    appVersion: String,
    hint: String?,
    vocabulary: List<String>,
  ): JsonObject = buildJsonObject {
    put("protocol", PROTOCOL)
    put("appId", appId)
    put("domain", domain)
    put("appLabel", appLabel)
    put("appVersion", appVersion)
    hint?.let { put("hint", it) }
    put("vocabulary", buildJsonArray { vocabulary.forEach { add(JsonPrimitive(it)) } })
    put(
      "categories",
      buildJsonArray {
        registry.categories.forEach { category ->
          add(buildJsonObject { put("id", category.id); put("label", category.label); put("hint", category.hint) })
        }
      },
    )
    put(
      "groups",
      buildJsonArray {
        registry.groups.forEach { group ->
          add(
            buildJsonObject {
              put("id", group.id)
              put("statusKey", group.statusKey)
              put("hint", group.hint)
              group.resolvedCategory?.let { put("category", it.id) }
              group.parent?.let { put("parent", it.id) }
              put("default", group.loadsWithCategory)
              put("action", group == registry.actionGroup)
            },
          )
        }
      },
    )
    put(
      "tools",
      buildJsonArray {
        registry.tools.forEach { tool ->
          add(
            buildJsonObject {
              put("name", tool.name)
              put("group", tool.group.id)
              put("description", tool.description)
              put("parameters", tool.parameters)
              put("needsConfirmation", tool.needsConfirmation)
              put("longRunning", tool.longRunning)
              put("action", tool.isAction)
            },
          )
        }
      },
    )
  }

  fun catalogOf(json: JsonObject?, authority: String, packageName: String): RemoteCatalog? {
    json ?: return null
    val protocol = json["protocol"].int() ?: return null
    val categories = json["categories"].asArray().mapNotNull { c ->
      RemoteCategoryInfo(c["id"].string() ?: return@mapNotNull null, c["label"].string() ?: "", c["hint"].string() ?: "")
    }
    val groups = json["groups"].asArray().mapNotNull { g ->
      RemoteGroupInfo(
        id = g["id"].string() ?: return@mapNotNull null,
        statusKey = g["statusKey"].string() ?: "thinking",
        hint = g["hint"].string() ?: "",
        category = g["category"].string(),
        parent = g["parent"].string(),
        loadsWithCategory = g["default"].bool() ?: false,
        action = g["action"].bool() ?: false,
      )
    }
    val tools = json["tools"].asArray().mapNotNull { t ->
      RemoteToolInfo(
        name = t["name"].string() ?: return@mapNotNull null,
        group = t["group"].string() ?: return@mapNotNull null,
        description = t["description"].string() ?: "",
        parameters = (t["parameters"] as? JsonObject) ?: buildJsonObject { put("type", "object") },
        needsConfirmation = t["needsConfirmation"].bool() ?: false,
        longRunning = t["longRunning"].bool() ?: false,
        action = t["action"].bool() ?: false,
      )
    }
    return RemoteCatalog(
      protocol = protocol,
      authority = authority,
      packageName = packageName,
      appId = json["appId"].string() ?: packageName,
      domain = json["domain"].string() ?: json["appId"].string() ?: "app",
      appLabel = json["appLabel"].string() ?: packageName,
      appVersion = json["appVersion"].string() ?: "",
      hint = json["hint"].string(),
      vocabulary = json["vocabulary"].asArray().mapNotNull { it.string() },
      categories = categories,
      groups = groups,
      tools = tools,
    )
  }

  fun describeRequest(name: String, args: JsonObject, language: String): JsonObject = buildJsonObject {
    put("name", name)
    put("args", args)
    put("lang", language)
  }

  fun describeResponse(title: String?, detail: String?): JsonObject = buildJsonObject {
    title?.let { put("title", it) }
    detail?.let { put("detail", it) }
  }

  fun runRequest(name: String, args: JsonObject, confirmed: Boolean, requestId: String, language: String): JsonObject = buildJsonObject {
    put("name", name)
    put("args", args)
    put("confirmed", confirmed)
    put("requestId", requestId)
    put("lang", language)
  }

  /** Una risposta finita: testo, escalation, parti (file consegnati come URI). */
  fun resultResponse(text: String, escalate: Boolean, parts: List<RemotePart>): JsonObject = buildJsonObject {
    put("text", text)
    put("escalate", escalate)
    put("parts", buildJsonArray { parts.forEach { add(it.toJson()) } })
  }

  /** Un lavoro lungo avviato: il cliente lo segue con `status`. */
  fun jobResponse(jobId: String): JsonObject = buildJsonObject { put("jobId", jobId) }

  fun statusRequest(jobId: String): JsonObject = buildJsonObject { put("jobId", jobId) }

  fun statusResponse(state: String, detail: String?, progress: Double?, text: String?, escalate: Boolean, parts: List<RemotePart>): JsonObject = buildJsonObject {
    put("state", state)
    detail?.let { put("detail", it) }
    progress?.let { put("progress", it) }
    text?.let { put("text", it) }
    put("escalate", escalate)
    put("parts", buildJsonArray { parts.forEach { add(it.toJson()) } })
  }

  fun readyResponse(ready: Boolean, reason: String?): JsonObject = buildJsonObject {
    put("ready", ready)
    reason?.let { put("reason", it) }
  }

  fun errorResponse(message: String): JsonObject = buildJsonObject { put("error", message) }

  /** Un risultato di `run` o `status` letto dal cliente. */
  fun resultOf(json: JsonObject?): RemoteResult? {
    json ?: return null
    json["error"].string()?.let { return RemoteResult.Error(it) }
    json["jobId"].string()?.let { return RemoteResult.Job(it) }
    val state = json["state"].string()
    val parts = json["parts"].asArray().mapNotNull { RemotePart.of(it) }
    if (state != null) {
      return RemoteResult.Status(state, json["detail"].string(), json["progress"].double(), json["text"].string(), json["escalate"].bool() ?: false, parts)
    }
    val text = json["text"].string() ?: return null
    return RemoteResult.Done(text, json["escalate"].bool() ?: false, parts)
  }

  private fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.doubleOrNull?.toInt() ?: it.contentOrNull?.toIntOrNull() }
  private fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
  private fun JsonElement?.double(): Double? = (this as? JsonPrimitive)?.doubleOrNull
  private val JsonPrimitive.contentOrNull: String? get() = if (isString || content.isNotEmpty()) content else null
}

data class RemoteCategoryInfo(val id: String, val label: String, val hint: String)

data class RemoteGroupInfo(val id: String, val statusKey: String, val hint: String, val category: String?, val parent: String?, val loadsWithCategory: Boolean, val action: Boolean)

data class RemoteToolInfo(val name: String, val group: String, val description: String, val parameters: JsonObject, val needsConfirmation: Boolean, val longRunning: Boolean, val action: Boolean)

/** Il catalogo di un'app ospite, come lo vede il cliente. */
data class RemoteCatalog(
  val protocol: Int,
  val authority: String,
  val packageName: String,
  val appId: String,
  /** Il prefisso dei nomi dei tool e dei gruppi nel registry del cliente (`cv`, `meteo`, `bus`). */
  val domain: String,
  val appLabel: String,
  val appVersion: String,
  val hint: String?,
  val vocabulary: List<String>,
  val categories: List<RemoteCategoryInfo>,
  val groups: List<RemoteGroupInfo>,
  val tools: List<RemoteToolInfo>,
)

/** Un file consegnato da un tool ospite: un URI leggibile dal chiamante (grant temporaneo). */
data class RemotePart(val kind: String, val uri: String, val mime: String, val name: String?) {
  fun toJson(): JsonObject = buildJsonObject {
    put("kind", kind)
    put("uri", uri)
    put("mime", mime)
    name?.let { put("name", it) }
  }

  companion object {
    const val KIND_IMAGE = "image"
    const val KIND_DOCUMENT = "document"

    fun of(json: JsonElement?): RemotePart? {
      val kind = json["kind"].string() ?: return null
      val uri = json["uri"].string() ?: return null
      return RemotePart(kind, uri, json["mime"].string() ?: "application/octet-stream", json["name"].string())
    }
  }
}

sealed interface RemoteResult {
  data class Done(val text: String, val escalate: Boolean, val parts: List<RemotePart>) : RemoteResult
  data class Job(val jobId: String) : RemoteResult
  data class Status(val state: String, val detail: String?, val progress: Double?, val text: String?, val escalate: Boolean, val parts: List<RemotePart>) : RemoteResult
  data class Error(val message: String) : RemoteResult
}

/** Per i test e per chi vuole leggere un `parameters` senza il registry. */
fun JsonArray?.strings(): List<String> = this?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

@Suppress("unused")
private fun <C> keepAiToolImport(tool: AiTool<C>) = tool.name
