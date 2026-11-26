package dev.antigravity.fluidengine.ai.mcp

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Le costanti del Model Context Protocol che il cliente dell'engine parla (4.0, F9.2).
 *
 * Il trasporto e' lo "Streamable HTTP" della specifica: un POST per messaggio JSON-RPC, e il server
 * risponde con un JSON o con un flusso di eventi (SSE) che puo' portare, prima della risposta, le sue
 * richieste e le sue notifiche. Le versioni sono quelle pubblicate che lo usano: la prima e' quella che
 * il cliente chiede, il server risponde con la sua, e se il cliente non la conosce si ferma (specifica,
 * "Lifecycle", "Version Negotiation"). La 2024-11-05 aveva l'altro trasporto (HTTP + SSE a due
 * indirizzi) e non c'e': i server di oggi parlano tutti la nuova (fiducia media, 26/11/2026).
 *
 * Fonti: modelcontextprotocol.io/specification/2025-11-25 (basic/transports, basic/lifecycle,
 * basic/authorization, server/tools), 2025-06-18 e 2025-03-26. Fiducia: alta sulle forme dei messaggi,
 * media sui dettagli che cambiano fra le versioni (scritti dove servono).
 */
object McpProtocol {
  const val LATEST = "2025-11-25"

  /** Le versioni conosciute, dalla piu' nuova: la prima si chiede, le altre si accettano. */
  val SUPPORTED: List<String> = listOf(LATEST, "2025-06-18", "2025-03-26")

  const val HEADER_SESSION = "Mcp-Session-Id"
  const val HEADER_VERSION = "MCP-Protocol-Version"
  const val HEADER_LAST_EVENT = "Last-Event-ID"

  /** Gli errori JSON-RPC della specifica. */
  const val METHOD_NOT_FOUND = -32601
  const val INVALID_PARAMS = -32602
  const val INTERNAL_ERROR = -32603

  internal fun request(id: Long, method: String, params: JsonObject?): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    put("method", method)
    params?.let { put("params", it) }
  }

  internal fun notification(method: String, params: JsonObject?): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("method", method)
    params?.let { put("params", it) }
  }

  internal fun result(id: JsonElement, result: JsonObject): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    put("result", result)
  }

  internal fun error(id: JsonElement, code: Int, message: String): JsonObject = buildJsonObject {
    put("jsonrpc", "2.0")
    put("id", id)
    put("error", buildJsonObject { put("code", code); put("message", message) })
  }

  internal fun idOf(message: JsonObject): JsonElement? = message["id"]?.takeIf { it !is JsonNull }

  internal fun idLong(message: JsonObject): Long? = (idOf(message) as? JsonPrimitive)?.content?.toLongOrNull()
}

/** Chi e' il cliente, per l'`initialize`: il nome e la versione dell'app, non dell'utente. */
data class McpClientInfo(val name: String, val version: String, val title: String? = null)

/** Quello che il server ha detto di se' all'`initialize`. */
data class McpServerInfo(
  val name: String,
  val version: String?,
  val title: String? = null,
)

/**
 * Una sessione aperta: la versione negoziata, chi e' il server, l'id di sessione (se il server ne da'
 * uno) e le sue istruzioni. [instructions] arrivano da fuori e **non vanno mai al modello**: la pagina
 * «Connettori» le mostra all'utente, e basta (difesa contro le istruzioni truccate, F9.2).
 */
data class McpSession(
  val protocolVersion: String,
  val server: McpServerInfo,
  val sessionId: String?,
  val instructions: String?,
  val hasTools: Boolean,
  val toolsListChanged: Boolean,
)

/**
 * Le indicazioni di un tool sul suo comportamento. Sono **indizi**, non garanzie: la specifica dice
 * che un cliente non deve fidarsene se il server non e' fidato. L'engine li usa solo per la pagina e
 * per le parole della conferma; la conferma la decide la regola dell'utente ([McpToolPolicy]).
 */
data class McpToolAnnotations(
  val title: String? = null,
  val readOnlyHint: Boolean? = null,
  val destructiveHint: Boolean? = null,
  val idempotentHint: Boolean? = null,
  val openWorldHint: Boolean? = null,
)

/** Un tool come lo descrive il server (tools/list), prima delle difese. */
data class McpToolInfo(
  val name: String,
  val title: String?,
  val description: String?,
  val inputSchema: JsonObject,
  val annotations: McpToolAnnotations = McpToolAnnotations(),
  val outputSchema: JsonObject? = null,
)

/** Un pezzo del risultato di un tool (tools/call). */
sealed interface McpContent {
  data class Text(val text: String) : McpContent
  data class Image(val data: String, val mimeType: String) : McpContent
  data class Audio(val data: String, val mimeType: String) : McpContent
  data class ResourceLink(val uri: String, val name: String?, val description: String?, val mimeType: String?) : McpContent
  data class Resource(val uri: String, val text: String?, val mimeType: String?) : McpContent
  data class Unknown(val type: String) : McpContent
}

/** Il risultato di una chiamata: i pezzi, quello strutturato e se e' un errore del tool. */
data class McpCallResult(val content: List<McpContent>, val structured: JsonObject?, val isError: Boolean)

/**
 * Gli errori del cliente MCP. Ognuno dice se l'effetto di una chiamata e' **incerto** ([uncertain]):
 * un flusso interrotto prima della risposta non dice se il server ha scritto o no, e una scrittura
 * incerta non si ripete da sola.
 */
sealed class McpError(message: String, cause: Throwable? = null, val uncertain: Boolean = false) : Exception(message, cause) {
  /** Il server vuole un accesso (401), o uno piu' ampio (403 con `insufficient_scope`). */
  class AuthRequired(val resourceMetadataUrl: String?, val scope: String?, val insufficientScope: Boolean = false) :
    McpError(if (insufficientScope) "il connettore chiede piu' permessi" else "il connettore chiede di entrare con l'account")

  /** Il server ha risposto con una versione che il cliente non conosce. */
  class UnsupportedVersion(val serverVersion: String?) :
    McpError("il connettore parla una versione del protocollo che l'app non conosce (${serverVersion ?: "nessuna"})")

  /** Un errore HTTP che non e' l'accesso: lo stato e il testo, gia' accorciato. */
  class Http(val status: Int, val body: String) : McpError("il connettore ha risposto $status")

  /** Un errore JSON-RPC del server. */
  class Rpc(val code: Int, val rpcMessage: String) : McpError("errore del connettore ($code): $rpcMessage")

  /** Il flusso si e' interrotto prima della risposta e non si e' potuto riprendere. */
  class Interrupted(cause: Throwable? = null) : McpError("il collegamento col connettore si e' interrotto", cause, uncertain = true)

  /** Nessuna risposta entro il tempo. */
  class Timeout : McpError("il connettore non ha risposto in tempo", uncertain = true)

  /** La rete non c'e' o il server non risponde. */
  class Network(cause: Throwable) : McpError("connettore non raggiungibile: ${cause.message ?: cause::class.simpleName}", cause)

  /** Un messaggio che non e' JSON-RPC, o un campo obbligatorio che manca. */
  class Protocol(detail: String) : McpError("risposta del connettore non valida: $detail")
}
