package dev.antigravity.fluidengine.ai.mcp

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Uno strumento del server finto: come si descrive, e cosa risponde. */
class McpMemoryTool(
  val info: McpToolInfo,
  val handler: (JsonObject) -> McpCallResult,
)

/**
 * Un server MCP finto, in memoria (4.0, F9.2): parla il protocollo vero (initialize con la negoziazione,
 * la sessione, tools/list a pagine, tools/call, ping, i flussi di eventi con le richieste e le notifiche
 * del server, la ripresa con `Last-Event-ID`) senza rete. Serve alle prove dell'engine, alla galleria e
 * alle prove delle app: si passa al posto del trasporto vero (`McpConnectors(transport = { server })`).
 *
 * I comportamenti da provare si accendono con le proprieta': la versione che risponde ([version]), un
 * gettone richiesto ([requiredToken]), i flussi ([streaming]) con una richiesta `ping` del server prima della
 * risposta, un flusso che si interrompe dopo il primo evento ([interruptNextStream]), la sessione che scade
 * ([expireSession]), le pagine di tools/list ([pageSize]).
 */
class McpMemoryServer(
  var tools: List<McpMemoryTool>,
  var name: String = "server-finto",
  var title: String? = null,
  /** La versione con cui risponde all'initialize; null = quella chiesta dal cliente, se la conosce. */
  var version: String? = null,
  var instructions: String? = null,
  /** Se non e' null, ogni richiesta senza `Authorization: Bearer <questo>` riceve 401. */
  var requiredToken: String? = null,
  var resourceMetadataUrl: String? = "https://auth.example/.well-known/oauth-protected-resource",
  /** Risponde con un flusso di eventi invece che con un JSON. */
  var streaming: Boolean = false,
  var pageSize: Int = 50,
) : McpTransport {

  /** Il prossimo flusso si interrompe dopo il primo evento (la risposta resta da riprendere). */
  @Volatile var interruptNextStream: Boolean = false

  /** Se vero, la ripresa con `Last-Event-ID` non funziona (il server non conserva gli eventi). */
  @Volatile var forgetEvents: Boolean = false

  /** La prossima richiesta con una sessione riceve 404 (sessione scaduta). */
  @Volatile var expireSession: Boolean = false

  /** Le richieste arrivate (metodo HTTP e metodo JSON-RPC), per le prove. */
  val log: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

  /** Le chiamate ai tool arrivate: nome e argomenti. */
  val calls: MutableList<Pair<String, JsonObject>> = java.util.Collections.synchronizedList(mutableListOf())

  /** Le risposte del cliente alle richieste del server (il `ping` nei flussi). */
  val clientReplies: MutableList<JsonObject> = java.util.Collections.synchronizedList(mutableListOf())

  private val sessions = AtomicInteger(0)
  private var session: String? = null
  private val events = AtomicInteger(0)
  /** Gli eventi non ancora consegnati di un flusso interrotto, per la ripresa. */
  private val pending = mutableListOf<Pair<String, JsonObject>>()

  override suspend fun send(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange {
    val method = (request.body?.get("method") as? JsonPrimitive)?.contentOrNull
    log += "${request.method} ${method ?: (if (request.body?.get("result") != null || request.body?.get("error") != null) "risposta" else "")}".trim()
    requiredToken?.let { token ->
      if (request.headers["Authorization"] != "Bearer $token") {
        return McpExchange(401, mapOf("www-authenticate" to "Bearer resource_metadata=\"$resourceMetadataUrl\", scope=\"tools\""), false, false, null, body = "unauthorized")
      }
    }
    if (request.method == "DELETE") {
      session = null
      return McpExchange(200, emptyMap(), false, false, null)
    }
    if (request.method == "GET") return resume(request, onMessage)
    val body = request.body ?: return McpExchange(400, emptyMap(), false, false, null, body = "no body")
    val sent = request.headers[McpProtocol.HEADER_SESSION]
    if (method != "initialize" && session != null) {
      if (expireSession && sent != null) {
        expireSession = false
        session = null
        return McpExchange(404, emptyMap(), false, false, null, body = "session not found")
      }
      if (sent != session) return McpExchange(400, emptyMap(), false, false, null, body = "bad session")
    }
    // Una notifica o una risposta del cliente: 202, niente corpo.
    if (method == null || McpProtocol.idOf(body) == null) {
      if (method == null) clientReplies += body
      return McpExchange(202, sessionHeaders(), false, false, null)
    }
    val id = McpProtocol.idOf(body)!!
    val params = body["params"] as? JsonObject ?: JsonObject(emptyMap())
    val reply = when (method) {
      "initialize" -> initialize(id, params)
      "ping" -> McpProtocol.result(id, buildJsonObject {})
      "tools/list" -> list(id, params)
      "tools/call" -> call(id, params)
      else -> McpProtocol.error(id, McpProtocol.METHOD_NOT_FOUND, "metodo sconosciuto: $method")
    }
    if (!streaming || method == "initialize") {
      val headers = sessionHeaders() + ("content-type" to "application/json")
      return McpExchange(200, headers, false, onMessage(McpIncoming(reply, null)), null)
    }
    // Il flusso: una richiesta del server (ping), una notifica, poi la risposta.
    val stream = listOf(
      nextEvent() to McpProtocol.request(9_000L + events.get(), "ping", null),
      nextEvent() to McpProtocol.notification("notifications/message", buildJsonObject { put("level", "info"); put("data", "lavoro...") }),
      nextEvent() to reply,
    )
    return deliver(stream, onMessage, interrupt = interruptNextStream.also { interruptNextStream = false })
  }

  private suspend fun deliver(stream: List<Pair<String, JsonObject>>, onMessage: suspend (McpIncoming) -> Boolean, interrupt: Boolean): McpExchange {
    var last: String? = null
    stream.forEachIndexed { index, (eventId, message) ->
      if (interrupt && index == 1) {
        synchronized(pending) { pending.clear(); pending += stream.drop(1) }
        return McpExchange(200, sessionHeaders() + ("content-type" to "text/event-stream"), true, false, last, failure = IOException("flusso interrotto"))
      }
      last = eventId
      if (onMessage(McpIncoming(message, eventId))) {
        return McpExchange(200, sessionHeaders() + ("content-type" to "text/event-stream"), true, true, eventId)
      }
    }
    return McpExchange(200, sessionHeaders() + ("content-type" to "text/event-stream"), true, false, last)
  }

  private suspend fun resume(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange {
    val after = request.headers[McpProtocol.HEADER_LAST_EVENT] ?: return McpExchange(405, emptyMap(), false, false, null, body = "no stream")
    val rest = synchronized(pending) { pending.toList().also { pending.clear() } }
    if (forgetEvents || rest.isEmpty()) return McpExchange(200, sessionHeaders() + ("content-type" to "text/event-stream"), true, false, after)
    return deliver(rest, onMessage, interrupt = false)
  }

  private fun nextEvent(): String = "e${events.incrementAndGet()}"

  private fun sessionHeaders(): Map<String, String> = session?.let { mapOf(McpProtocol.HEADER_SESSION.lowercase() to it) } ?: emptyMap()

  private fun initialize(id: JsonElement, params: JsonObject): JsonObject {
    session = "sessione-${sessions.incrementAndGet()}"
    val asked = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
    val answer = version ?: asked?.takeIf { it in McpProtocol.SUPPORTED } ?: McpProtocol.LATEST
    return McpProtocol.result(id, buildJsonObject {
      put("protocolVersion", answer)
      put("capabilities", buildJsonObject { put("tools", buildJsonObject { put("listChanged", true) }) })
      put("serverInfo", buildJsonObject { put("name", name); put("version", "1.0"); title?.let { put("title", it) } })
      instructions?.let { put("instructions", it) }
    })
  }

  private fun list(id: JsonElement, params: JsonObject): JsonObject {
    val start = (params["cursor"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
    val page = tools.drop(start).take(pageSize)
    val next = start + page.size
    return McpProtocol.result(id, buildJsonObject {
      put("tools", JsonArray(page.map { McpCodec.toJson(it.info) }))
      if (next < tools.size) put("nextCursor", next.toString())
    })
  }

  private fun call(id: JsonElement, params: JsonObject): JsonObject {
    val name = (params["name"] as? JsonPrimitive)?.contentOrNull
    val tool = tools.firstOrNull { it.info.name == name } ?: return McpProtocol.error(id, McpProtocol.INVALID_PARAMS, "strumento sconosciuto: $name")
    val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
    calls += tool.info.name to args
    val result = tool.handler(args)
    return McpProtocol.result(id, buildJsonObject {
      put("content", buildJsonArray {
        result.content.forEach { piece ->
          add(when (piece) {
            is McpContent.Text -> buildJsonObject { put("type", "text"); put("text", piece.text) }
            is McpContent.Image -> buildJsonObject { put("type", "image"); put("data", piece.data); put("mimeType", piece.mimeType) }
            is McpContent.ResourceLink -> buildJsonObject { put("type", "resource_link"); put("uri", piece.uri); piece.name?.let { put("name", it) } }
            else -> buildJsonObject { put("type", "text"); put("text", "") }
          })
        }
      })
      result.structured?.let { put("structuredContent", it) }
      if (result.isError) put("isError", true)
    })
  }

  /** Una notifica `tools/list_changed` in un flusso: il prossimo POST la porta prima della risposta. */
  fun toolsChanged(newTools: List<McpMemoryTool>) {
    tools = newTools
  }

  companion object {
    /** Un risultato di solo testo. */
    fun text(text: String, isError: Boolean = false) = McpCallResult(listOf(McpContent.Text(text)), null, isError)

    /** Uno schema `object` con dei campi stringa. */
    fun schema(vararg fields: Pair<String, String>, required: List<String> = emptyList()): JsonObject = buildJsonObject {
      put("type", "object")
      put("properties", buildJsonObject { fields.forEach { (k, d) -> put(k, buildJsonObject { put("type", "string"); put("description", d) }) } })
      if (required.isNotEmpty()) put("required", buildJsonArray { required.forEach { add(JsonPrimitive(it)) } })
    }
  }
}
