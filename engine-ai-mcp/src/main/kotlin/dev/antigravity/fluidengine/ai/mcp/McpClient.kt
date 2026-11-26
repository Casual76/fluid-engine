package dev.antigravity.fluidengine.ai.mcp

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Chi da' il gettone d'accesso al cliente (4.0, F9.2): di solito [McpAuthorization], che lo legge cifrato e
 * lo rinnova quando scade. Null = il server non vuole accesso.
 */
interface McpCredentials {
  /** Il gettone da mettere in `Authorization: Bearer`, o null se non c'e'. */
  suspend fun bearer(): String?

  /** Il server ha risposto 401 con questo gettone: tornare vero se ne e' arrivato uno nuovo (rinnovato), e si riprova. */
  suspend fun refreshAfterUnauthorized(rejected: String?): Boolean = false
}

/**
 * Il cliente di un server MCP (4.0, F9.2): apre la sessione, legge gli strumenti, li chiama.
 *
 * - **La sessione.** `initialize` con la versione piu' nuova che l'engine conosce; il server risponde con
 *   la sua, e se non e' fra [versions] la sessione si chiude con [McpError.UnsupportedVersion]. Poi la
 *   notifica `initialized`. L'id di sessione del server (`Mcp-Session-Id`) e la versione
 *   (`MCP-Protocol-Version`) vanno in ogni richiesta dopo; un 404 con una sessione vuol dire "scaduta":
 *   se ne apre una nuova e si riprova una volta (la richiesta non era arrivata: ripeterla e' sicuro).
 * - **I flussi.** La risposta a un POST puo' essere un flusso di eventi con, prima della risposta, le
 *   richieste del server (`ping` riceve la sua risposta vuota, il resto "metodo non trovato": l'engine
 *   non offre `sampling`, `elicitation` ne' `roots`) e le sue notifiche (`tools/list_changed` alza
 *   [toolsChanged]; i log e l'avanzamento si ignorano). Un flusso interrotto prima della risposta si
 *   riprende con un GET e `Last-Event-ID`, se il server numerava gli eventi, al massimo due volte; se no la
 *   chiamata finisce con [McpError.Interrupted], che e' **incerta**: una scrittura non si ripete da sola.
 * - **L'accesso.** Un 401 chiede a [credentials] di rinnovare il gettone e riprova una volta; se non si
 *   puo', [McpError.AuthRequired] porta l'indirizzo dei metadati del server (dall'intestazione
 *   `WWW-Authenticate`) per [McpOAuth]. Un 403 `insufficient_scope` porta lo scope che serve.
 * - **Il tempo.** Ogni richiesta ha [requestTimeoutMillis]; scaduto, il server riceve
 *   `notifications/cancelled` (la specifica lo chiede) e la chiamata finisce con [McpError.Timeout].
 *
 * Le richieste passano una alla volta (un `Mutex`): un connettore e' una conversazione sola, e i tool di
 * un giro che toccano lo stesso server aspettano il loro turno.
 */
class McpClient(
  private val transport: McpTransport,
  private val credentials: McpCredentials? = null,
  private val clientInfo: McpClientInfo = McpClientInfo("Fluid Engine", "4.0"),
  private val versions: List<String> = McpProtocol.SUPPORTED,
  private val requestTimeoutMillis: Long = 30_000L,
  /** Quanti strumenti si leggono al massimo da un server (le pagine di tools/list si fermano li'). */
  private val maxTools: Int = MAX_TOOLS,
) {
  private val ids = AtomicLong(1)
  private val lock = Mutex()
  private val sessionState = MutableStateFlow<McpSession?>(null)
  private val changed = MutableStateFlow(0L)

  /** La sessione aperta, o null. */
  val session: StateFlow<McpSession?> = sessionState

  /** Sale ogni volta che il server dice che i suoi strumenti sono cambiati (`notifications/tools/list_changed`). */
  val toolsChanged: StateFlow<Long> = changed

  /** Apre la sessione se non e' aperta. */
  suspend fun connect(): McpSession = lock.withLock { ensureSession() }

  /** Tutti gli strumenti del server, pagina per pagina, fino a [maxTools]. */
  suspend fun listTools(): List<McpToolInfo> = lock.withLock {
    ensureSession()
    val out = mutableListOf<McpToolInfo>()
    var cursor: String? = null
    var pages = 0
    do {
      val params = cursor?.let { c -> buildJsonObject { put("cursor", c) } }
      val result = call("tools/list", params)
      (result["tools"] as? JsonArray)?.forEach { element -> McpCodec.tool(element)?.let { if (out.size < maxTools) out += it } }
      cursor = (result["nextCursor"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
      pages++
    } while (cursor != null && out.size < maxTools && pages < MAX_PAGES)
    out
  }

  /** Chiama uno strumento. Un errore del tool (`isError`) torna nel risultato; uno del protocollo lancia [McpError]. */
  suspend fun callTool(name: String, arguments: JsonObject): McpCallResult = lock.withLock {
    ensureSession()
    val result = call("tools/call", buildJsonObject { put("name", name); put("arguments", arguments) })
    McpCodec.callResult(result)
  }

  /** Un `ping`: vero se il server risponde. */
  suspend fun ping(): Boolean = lock.withLock {
    runCatching { ensureSession(); call("ping", null) }.isSuccess
  }

  /** Chiude la sessione: un DELETE al server (che puo' rifiutarlo: va bene lo stesso) e si dimentica tutto. */
  suspend fun close() = lock.withLock {
    val current = sessionState.value ?: return@withLock
    withContext(NonCancellable) {
      if (current.sessionId != null) {
        runCatching { transport.send(McpHttpRequest("DELETE", null, headers(current, credentials?.bearer())), { false }) }
      }
    }
    sessionState.value = null
  }

  private suspend fun ensureSession(): McpSession {
    sessionState.value?.let { return it }
    val id = ids.getAndIncrement()
    val params = buildJsonObject {
      put("protocolVersion", versions.first())
      // Niente sampling, elicitation, roots: l'engine non li offre, e il server non deve chiederli.
      put("capabilities", buildJsonObject {})
      put("clientInfo", buildJsonObject {
        put("name", clientInfo.name)
        put("version", clientInfo.version)
        clientInfo.title?.let { put("title", it) }
      })
    }
    val (result, exchange) = roundTrip(id, McpProtocol.request(id, "initialize", params), session = null)
    val version = (result["protocolVersion"] as? JsonPrimitive)?.contentOrNull
    val sessionId = exchange.headers[McpProtocol.HEADER_SESSION.lowercase()]
    if (version == null || version !in versions) {
      // La specifica: il cliente che non regge la versione del server si scollega.
      if (sessionId != null) {
        withContext(NonCancellable) {
          runCatching { transport.send(McpHttpRequest("DELETE", null, mapOf(McpProtocol.HEADER_SESSION to sessionId)), { false }) }
        }
      }
      throw McpError.UnsupportedVersion(version)
    }
    val serverInfo = result["serverInfo"] as? JsonObject
    val capabilities = result["capabilities"] as? JsonObject
    val tools = capabilities?.get("tools") as? JsonObject
    val session = McpSession(
      protocolVersion = version,
      server = McpServerInfo(
        name = (serverInfo?.get("name") as? JsonPrimitive)?.contentOrNull ?: "server",
        version = (serverInfo?.get("version") as? JsonPrimitive)?.contentOrNull,
        title = (serverInfo?.get("title") as? JsonPrimitive)?.contentOrNull,
      ),
      sessionId = sessionId,
      instructions = (result["instructions"] as? JsonPrimitive)?.contentOrNull,
      hasTools = tools != null,
      toolsListChanged = (tools?.get("listChanged") as? JsonPrimitive)?.booleanOrNull == true,
    )
    val exchanged = transport.send(McpHttpRequest("POST", McpProtocol.notification("notifications/initialized", null), headers(session, credentials?.bearer()))) { false }
    if (exchanged.status !in 200..299) throw McpError.Http(exchanged.status, exchanged.body.orEmpty().take(300))
    sessionState.value = session
    return session
  }

  /** Una richiesta con la sua risposta, dentro la sessione di adesso; una sessione scaduta si riapre una volta. */
  private suspend fun call(method: String, params: JsonObject?): JsonObject {
    repeat(2) { attempt ->
      val session = sessionState.value ?: ensureSession()
      val id = ids.getAndIncrement()
      try {
        return roundTrip(id, McpProtocol.request(id, method, params), session).first
      } catch (e: SessionExpired) {
        sessionState.value = null
        if (attempt == 1) throw McpError.Http(404, "sessione scaduta")
      }
    }
    error("non raggiungibile")
  }

  private class SessionExpired : Exception()

  /**
   * Manda un messaggio e aspetta la risposta con il suo [id]: gestisce l'accesso (un rinnovo e una
   * riprova), la ripresa di un flusso interrotto e il tempo.
   */
  private suspend fun roundTrip(id: Long, message: JsonObject, session: McpSession?): Pair<JsonObject, McpExchange> {
    var response: JsonObject? = null
    var lastExchange: McpExchange? = null
    var refreshed = false
    val handler: suspend (McpIncoming) -> Boolean = { incoming ->
      val m = incoming.message
      when {
        m["method"] != null && McpProtocol.idOf(m) != null -> {
          answerServerRequest(m, session)
          false
        }
        m["method"] != null -> {
          onNotification(m)
          false
        }
        McpProtocol.idLong(m) == id -> {
          response = m
          true
        }
        else -> false
      }
    }
    val outcome = withTimeoutOrNull(requestTimeoutMillis) {
      while (true) {
        val token = credentials?.bearer()
        val exchange = transport.send(McpHttpRequest("POST", message, headers(session, token)), handler)
        lastExchange = exchange
        when {
          exchange.status == 401 -> {
            if (!refreshed && credentials?.refreshAfterUnauthorized(token) == true) {
              refreshed = true
              continue
            }
            throw authRequired(exchange, insufficient = false)
          }
          exchange.status == 403 && exchange.headers["www-authenticate"].orEmpty().contains("insufficient_scope") ->
            throw authRequired(exchange, insufficient = true)
          exchange.status == 404 && session?.sessionId != null -> throw SessionExpired()
          exchange.status !in 200..299 -> throw McpError.Http(exchange.status, exchange.body.orEmpty().take(300))
        }
        if (exchange.done) break
        // Il flusso e' finito senza la risposta: si riprende dall'ultimo evento, se il server li numerava.
        var resumed = exchange
        var tries = 0
        while (!resumed.done && resumed.lastEventId != null && tries < MAX_RESUMES) {
          tries++
          resumed = transport.send(
            McpHttpRequest("GET", null, headers(session, token) + (McpProtocol.HEADER_LAST_EVENT to resumed.lastEventId!!)),
            handler,
          )
          if (resumed.status !in 200..299) break
        }
        if (resumed.done) break
        throw McpError.Interrupted(exchange.failure ?: resumed.failure)
      }
      true
    }
    if (outcome == null) {
      // La specifica: chi smette di aspettare lo dice al server.
      withContext(NonCancellable) {
        runCatching {
          transport.send(McpHttpRequest("POST", McpProtocol.notification("notifications/cancelled", buildJsonObject {
            put("requestId", id)
            put("reason", "timeout")
          }), headers(session, credentials?.bearer())), { false })
        }
      }
      throw McpError.Timeout()
    }
    val reply = response ?: throw McpError.Protocol("nessuna risposta")
    (reply["error"] as? JsonObject)?.let { error ->
      throw McpError.Rpc((error["code"] as? JsonPrimitive)?.intOrNull ?: McpProtocol.INTERNAL_ERROR, (error["message"] as? JsonPrimitive)?.contentOrNull ?: "errore")
    }
    val result = reply["result"] as? JsonObject ?: throw McpError.Protocol("risposta senza result")
    return result to lastExchange!!
  }

  private fun authRequired(exchange: McpExchange, insufficient: Boolean): McpError.AuthRequired {
    val header = exchange.headers["www-authenticate"]
    return McpError.AuthRequired(McpOAuth.parameter(header, "resource_metadata"), McpOAuth.parameter(header, "scope"), insufficient)
  }

  /** Le richieste del server durante un flusso: `ping` si', il resto no (l'engine non offre altro). */
  private suspend fun answerServerRequest(message: JsonObject, session: McpSession?) {
    val id = McpProtocol.idOf(message) ?: return
    val reply = when ((message["method"] as? JsonPrimitive)?.contentOrNull) {
      "ping" -> McpProtocol.result(id, buildJsonObject {})
      else -> McpProtocol.error(id, McpProtocol.METHOD_NOT_FOUND, "non supportato da questo cliente")
    }
    try {
      transport.send(McpHttpRequest("POST", reply, headers(session, credentials?.bearer()))) { false }
    } catch (e: CancellationException) {
      throw e
    } catch (_: McpError) {
      // Una risposta persa non ferma la nostra richiesta: il server la riavra' come timeout suo.
    }
  }

  private fun onNotification(message: JsonObject) {
    when ((message["method"] as? JsonPrimitive)?.contentOrNull) {
      "notifications/tools/list_changed" -> changed.value = changed.value + 1
      else -> Unit
    }
  }

  private fun headers(session: McpSession?, token: String? = null): Map<String, String> = buildMap {
    session?.sessionId?.let { put(McpProtocol.HEADER_SESSION, it) }
    // La 2025-03-26 non lo chiedeva; dalla 2025-06-18 va in ogni richiesta dopo l'initialize.
    session?.protocolVersion?.takeIf { it != "2025-03-26" }?.let { put(McpProtocol.HEADER_VERSION, it) }
    token?.let { put("Authorization", "Bearer $it") }
  }

  companion object {
    const val MAX_TOOLS = 128
    private const val MAX_PAGES = 16
    private const val MAX_RESUMES = 2
  }
}

/** Da JSON ai tipi di [McpProtocol], senza eccezioni: un campo che manca e' una decisione. */
internal object McpCodec {
  fun tool(element: JsonElement): McpToolInfo? {
    val o = element as? JsonObject ?: return null
    val name = (o["name"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
    val annotations = o["annotations"] as? JsonObject
    fun hint(key: String) = (annotations?.get(key) as? JsonPrimitive)?.booleanOrNull
    return McpToolInfo(
      name = name,
      title = (o["title"] as? JsonPrimitive)?.contentOrNull ?: (annotations?.get("title") as? JsonPrimitive)?.contentOrNull,
      description = (o["description"] as? JsonPrimitive)?.contentOrNull,
      inputSchema = (o["inputSchema"] as? JsonObject) ?: buildJsonObject { put("type", "object") },
      annotations = McpToolAnnotations(
        title = (annotations?.get("title") as? JsonPrimitive)?.contentOrNull,
        readOnlyHint = hint("readOnlyHint"),
        destructiveHint = hint("destructiveHint"),
        idempotentHint = hint("idempotentHint"),
        openWorldHint = hint("openWorldHint"),
      ),
      outputSchema = o["outputSchema"] as? JsonObject,
    )
  }

  fun callResult(result: JsonObject): McpCallResult {
    val content = (result["content"] as? JsonArray).orEmpty().mapNotNull { element ->
      val o = element as? JsonObject ?: return@mapNotNull null
      fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
      when (val type = s("type")) {
        "text" -> McpContent.Text(s("text").orEmpty())
        "image" -> McpContent.Image(s("data").orEmpty(), s("mimeType") ?: "image/png")
        "audio" -> McpContent.Audio(s("data").orEmpty(), s("mimeType") ?: "audio/wav")
        "resource_link" -> McpContent.ResourceLink(s("uri").orEmpty(), s("name"), s("description"), s("mimeType"))
        "resource" -> {
          val r = o["resource"]?.let { it as? JsonObject }
          McpContent.Resource(
            (r?.get("uri") as? JsonPrimitive)?.contentOrNull.orEmpty(),
            (r?.get("text") as? JsonPrimitive)?.contentOrNull,
            (r?.get("mimeType") as? JsonPrimitive)?.contentOrNull,
          )
        }
        else -> McpContent.Unknown(type ?: "?")
      }
    }
    return McpCallResult(content, result["structuredContent"] as? JsonObject, (result["isError"] as? JsonPrimitive)?.booleanOrNull == true)
  }

  /** Uno strumento di nuovo in JSON, com'era: per conservarlo fra un avvio e l'altro senza riconnettersi. */
  fun toJson(tool: McpToolInfo): JsonObject = buildJsonObject {
    put("name", tool.name)
    tool.title?.let { put("title", it) }
    tool.description?.let { put("description", it) }
    put("inputSchema", tool.inputSchema)
    put("annotations", buildJsonObject {
      tool.annotations.title?.let { put("title", it) }
      tool.annotations.readOnlyHint?.let { put("readOnlyHint", it) }
      tool.annotations.destructiveHint?.let { put("destructiveHint", it) }
      tool.annotations.idempotentHint?.let { put("idempotentHint", it) }
      tool.annotations.openWorldHint?.let { put("openWorldHint", it) }
    })
    tool.outputSchema?.let { put("outputSchema", it) }
  }

  @Suppress("unused")
  private fun JsonElement?.obj(): JsonObject? = this?.jsonObject
}
