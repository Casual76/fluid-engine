package dev.antigravity.fluidengine.ai.mcp

import dev.antigravity.fluidengine.ai.net.AiErrorMapper
import dev.antigravity.fluidengine.ai.net.SseParser
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Una richiesta HTTP verso il server MCP: POST di un messaggio, GET per riprendere un flusso, DELETE della sessione. */
data class McpHttpRequest(val method: String, val body: JsonObject?, val headers: Map<String, String>)

/** Un messaggio JSON-RPC arrivato dal server, con l'id dell'evento SSE che lo portava (per riprendere). */
data class McpIncoming(val message: JsonObject, val eventId: String?)

/**
 * Com'e' andato uno scambio. [done] e' vero se il chiamante ha avuto quello che aspettava (la sua
 * risposta). [failure] e' l'errore di rete arrivato a meta' di un flusso (allora [done] e' falso);
 * [lastEventId] l'ultimo id visto, per la ripresa. Per uno stato fuori da 2xx [body] e' il testo
 * dell'errore, accorciato, e nessun messaggio e' stato passato.
 */
data class McpExchange(
  val status: Int,
  val headers: Map<String, String>,
  val streamed: Boolean,
  val done: Boolean,
  val lastEventId: String?,
  val failure: Throwable? = null,
  val body: String? = null,
)

/**
 * Il trasporto del cliente MCP. Esiste come interfaccia perche' le prove (e la galleria) parlano con un
 * server finto in memoria; l'app usa [McpHttpTransport].
 */
interface McpTransport {
  /**
   * Manda [request] e passa a [onMessage] ogni messaggio che arriva (uno per un JSON, tanti per un
   * flusso), finche' [onMessage] non torna vero o il server non chiude. Un errore di rete **prima**
   * della risposta lancia [McpError.Network]; uno a meta' di un flusso torna in [McpExchange.failure].
   */
  suspend fun send(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange
}

/**
 * Il trasporto HTTP vero (4.0, F9.2), su `HttpURLConnection` come `AiHttp` di engine-ai: niente librerie,
 * gli stessi timeout, mai un corpo ri-inviato dietro un redirect. Chiede `application/json` e
 * `text/event-stream` insieme (la specifica lo vuole), e legge l'uno o l'altro a seconda di cosa arriva.
 *
 * Una lettura bloccata non vede la cancellazione: la fa un figlio sul dispatcher [io], e il padre, se
 * viene cancellato, chiude la connessione (`disconnect`), che fa fallire la lettura. E' lo stesso schema di
 * `AiHttp.postJsonStream`.
 *
 * Solo `https`, tranne gli indirizzi del telefono stesso (`localhost`, `127.0.0.1`, `10.0.2.2`): un
 * gettone d'accesso non viaggia mai in chiaro.
 */
class McpHttpTransport(
  private val url: String,
  private val userAgent: String = "FluidEngine-MCP",
  private val connectTimeoutMillis: Int = 10_000,
  /** Il silenzio massimo fra due righe (o l'attesa di una risposta intera). */
  private val readTimeoutMillis: Int = 60_000,
  private val io: CoroutineDispatcher = Dispatchers.IO,
  /** Il tetto di quello che si legge da una risposta: un server che manda un giga non riempie la memoria. */
  private val maxBytes: Int = MAX_BYTES,
) : McpTransport {

  init {
    require(McpUrls.allowed(url)) { "solo https (o un indirizzo locale per le prove): $url" }
  }

  override suspend fun send(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange = coroutineScope {
    val connection = try {
      open(request)
    } catch (e: IOException) {
      throw McpError.Network(e)
    }
    val reader = async(io) { exchange(connection, request, onMessage) }
    try {
      reader.await()
    } finally {
      // Fine normale, errore o cancellazione: la connessione si chiude sempre, e una lettura ferma si sblocca.
      connection.disconnect()
    }
  }

  private fun open(request: McpHttpRequest): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = request.method
      connectTimeout = connectTimeoutMillis
      readTimeout = readTimeoutMillis
      instanceFollowRedirects = false
      useCaches = false
      setRequestProperty("User-Agent", userAgent)
      setRequestProperty("Accept", "application/json, text/event-stream")
      // Il gzip di Android bufferizza gli eventi.
      setRequestProperty("Accept-Encoding", "identity")
      request.headers.forEach { (key, value) -> setRequestProperty(key, value) }
    }

  private suspend fun exchange(connection: HttpURLConnection, request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange {
    val code = try {
      request.body?.let { body ->
        val bytes = body.toString().toByteArray(Charsets.UTF_8)
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
      }
      connection.responseCode
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      throw McpError.Network(e)
    }
    val headers = AiErrorMapper.normalize(connection.headerFields)
    if (code !in 200..299) {
      val text = runCatching { connection.errorStream?.use { it.readCapped(ERROR_BYTES) } }.getOrNull().orEmpty()
      return McpExchange(code, headers, streamed = false, done = false, lastEventId = null, body = text)
    }
    if (code == 202 || code == 204) return McpExchange(code, headers, streamed = false, done = false, lastEventId = null)
    val type = headers["content-type"].orEmpty().lowercase()
    if (type.startsWith("text/event-stream")) {
      val parser = SseParser()
      var done = false
      var read = 0L
      val failure = try {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
          while (!done) {
            val line = input.readLine() ?: break
            read += line.length
            if (read > maxBytes) throw IOException("flusso oltre ${maxBytes / 1_000_000} MB")
            val payload = parser.feed(line) ?: continue
            done = deliver(payload, parser.lastEventId, onMessage)
          }
          if (!done) parser.flush()?.let { done = deliver(it, parser.lastEventId, onMessage) }
        }
        null
      } catch (e: CancellationException) {
        throw e
      } catch (e: IOException) {
        e
      } catch (e: McpError.Protocol) {
        e
      }
      return McpExchange(code, headers, streamed = true, done = done, lastEventId = parser.lastEventId, failure = failure)
    }
    val text = try {
      connection.inputStream.use { it.readCapped(maxBytes) }
    } catch (e: IOException) {
      return McpExchange(code, headers, streamed = false, done = false, lastEventId = null, failure = e)
    }
    if (text.isBlank()) return McpExchange(code, headers, streamed = false, done = false, lastEventId = null)
    return McpExchange(code, headers, streamed = false, done = deliver(text, null, onMessage), lastEventId = null)
  }

  private fun InputStream.readCapped(limit: Int): String {
    val bytes = readNBytesCompat(limit)
    return bytes.toString(Charsets.UTF_8)
  }

  private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8_192)
    while (out.size() < limit) {
      val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
      if (n < 0) break
      out.write(buffer, 0, n)
    }
    return out.toByteArray()
  }

  companion object {
    const val MAX_BYTES = 8_000_000
    private const val ERROR_BYTES = 4_000

    /**
     * Un evento o un corpo JSON: un oggetto o una lista (le liste JSON-RPC della 2025-03-26), un pezzo
     * per volta a [onMessage]. Un evento vuoto (il "priming" con il solo id) non porta niente.
     */
    internal suspend fun deliver(payload: String, eventId: String?, onMessage: suspend (McpIncoming) -> Boolean): Boolean {
      if (payload.isBlank()) return false
      val element = runCatching { Json.parseToJsonElement(payload) }.getOrElse { throw McpError.Protocol("non e' JSON") }
      val messages = when (element) {
        is JsonObject -> listOf(element)
        is JsonArray -> element.filterIsInstance<JsonObject>()
        else -> throw McpError.Protocol("non e' un messaggio JSON-RPC")
      }
      var done = false
      for (message in messages) if (onMessage(McpIncoming(message, eventId))) done = true
      return done
    }
  }
}

/** Le regole sugli indirizzi dei connettori. */
object McpUrls {
  private val local = setOf("localhost", "127.0.0.1", "10.0.2.2", "[::1]")

  /** `https`, oppure `http` solo verso il telefono stesso (un server di prova). */
  fun allowed(url: String): Boolean {
    val parsed = runCatching { URL(url) }.getOrNull() ?: return false
    return when (parsed.protocol.lowercase()) {
      "https" -> parsed.host.isNotBlank()
      "http" -> parsed.host.lowercase() in local
      else -> false
    }
  }

  /**
   * L'indirizzo canonico del server, per il parametro `resource` dell'accesso (RFC 8707, specifica MCP
   * "Authorization", "Canonical Server URI"): schema e host in minuscolo, senza frammento e senza la barra
   * finale.
   */
  fun canonical(url: String): String {
    val parsed = URL(url)
    val port = if (parsed.port == -1 || parsed.port == parsed.defaultPort) "" else ":${parsed.port}"
    val path = parsed.path.trimEnd('/')
    return "${parsed.protocol.lowercase()}://${parsed.host.lowercase()}$port$path"
  }

  /** L'origine (`https://host:porta`), per gli indirizzi `.well-known`. */
  fun origin(url: String): String {
    val parsed = URL(url)
    val port = if (parsed.port == -1 || parsed.port == parsed.defaultPort) "" else ":${parsed.port}"
    return "${parsed.protocol.lowercase()}://${parsed.host.lowercase()}$port"
  }
}
