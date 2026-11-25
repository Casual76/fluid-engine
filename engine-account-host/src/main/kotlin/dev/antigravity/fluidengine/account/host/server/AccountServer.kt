package dev.antigravity.fluidengine.account.host.server

import dev.antigravity.fluidengine.account.host.custody.StorePrf
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Una richiesta verso il server dell'account, già pronta: niente segreti nell'indirizzo (critica S14). */
class ServerRequest(
  val method: String,
  val path: String,
  val headers: Map<String, String> = emptyMap(),
  val body: ByteArray? = null,
  val contentType: String? = null,
)

/** La risposta: lo stato HTTP, il corpo grezzo, e l'ora del server se l'ha detta (`Date`). */
class ServerResponse(val status: Int, val body: ByteArray, val serverTimeMillis: Long?) {
  val json: JsonObject? by lazy { runCatching { Json.parseToJsonElement(body.decodeToString()) as JsonObject }.getOrNull() }
}

/**
 * Il trasporto HTTP verso `account.pampa-apps.com`, sostituibile nelle prove. Di serie `HttpURLConnection`, come
 * `EngineHttp` di engine-net: niente librerie in più nello Store. Un errore di rete è `IOException`, che la sessione
 * traduce in «offline», non in «errore» (`b\architettura.md` § 3.5).
 */
fun interface AccountHttp {
  suspend fun execute(baseUrl: String, request: ServerRequest): ServerResponse
}

/** Il trasporto di serie. Niente cache, niente redirect seguiti, tempi brevi: il server o risponde o è offline. */
object UrlConnectionHttp : AccountHttp {
  override suspend fun execute(baseUrl: String, request: ServerRequest): ServerResponse = withContext(Dispatchers.IO) {
    val connection = (URL(baseUrl.trimEnd('/') + request.path).openConnection() as HttpURLConnection).apply {
      requestMethod = request.method
      instanceFollowRedirects = false
      useCaches = false
      connectTimeout = 10_000
      readTimeout = 20_000
      for ((k, v) in request.headers) setRequestProperty(k, v)
      if (request.body != null) {
        doOutput = true
        setRequestProperty("content-type", request.contentType ?: "application/json")
      }
    }
    try {
      request.body?.let { b -> connection.outputStream.use { it.write(b) } }
      val status = connection.responseCode
      val stream = if (status >= 400) connection.errorStream else connection.inputStream
      val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
      ServerResponse(status, bytes, connection.getHeaderField("date")?.let(::parseHttpDate))
    } finally {
      connection.disconnect()
    }
  }

  private fun parseHttpDate(text: String): Long? =
    runCatching { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(text)?.time }.getOrNull()
}

/**
 * Un errore del server, col codice del contratto (`errors.json`) e la frase già pronta per l'utente. [reason] è il
 * dettaglio che alcune risposte portano (per esempio il motivo di `session_revoked`, o `lost_rotation_required`).
 */
class AccountServerException(
  val status: Int,
  val code: String,
  override val message: String,
  val reason: String? = null,
  val retryAfterSeconds: Long? = null,
  val body: JsonObject? = null,
) : Exception(message) {
  /** Un dispositivo fatto uscire: con `remote`, `lost` o `all` si cancellano anche le copie (`b\chiavi.md` § 4.4). */
  val isSessionRevoked: Boolean get() = code == "session_revoked"

  /** Azione distruttiva messa in attesa di sicurezza (Q8): la risposta è 202 con `executeAt`. */
  val isWaiting: Boolean get() = code == "waiting"
}

/** Le intestazioni del contratto (`routes.json`, `headers`). */
object AccountHeaders {
  const val STORE_VERSION = "pampa-store-version"
  const val DEVICE_PROOF = "pampa-device-proof"
  const val IDEMPOTENCY_KEY = "idempotency-key"
  const val BACKUP_EPOCH = "pampa-backup-epoch"
}

/**
 * Il client del server dell'account per lo Store: compone le richieste, aggiunge le intestazioni del contratto e
 * traduce le risposte d'errore in [AccountServerException]. Non tiene stato: i token li dà chi chiama.
 */
class AccountServerClient(
  private val baseUrl: String,
  private val storeVersion: String,
  private val http: AccountHttp = UrlConnectionHttp,
) {
  /** L'ultima ora del server vista, per la prova del dispositivo (`b\architettura.md` § 3.5). */
  @Volatile var serverClockOffsetMillis: Long = 0
    private set

  suspend fun call(method: String, path: String, json: JsonObject? = null, token: String? = null, headers: Map<String, String> = emptyMap()): JsonObject {
    val text = json?.toString()
    // La seconda cintura del PRF (K6): un corpo che porta ancora un'uscita del PRF non parte, qualunque sia la strada.
    if (text != null && StorePrf.leaksPrfOutput(text)) throw IllegalStateException("la richiesta $method $path porta un'uscita del PRF: non parte")
    val response = raw(ServerRequest(method, path, buildHeaders(token, headers), text?.toByteArray()))
    return response.json ?: JsonObject(emptyMap())
  }

  suspend fun upload(method: String, path: String, bytes: ByteArray, token: String, headers: Map<String, String> = emptyMap()): JsonObject =
    raw(ServerRequest(method, path, buildHeaders(token, headers), bytes, "application/octet-stream")).json ?: JsonObject(emptyMap())

  suspend fun download(path: String, token: String): ByteArray = raw(ServerRequest("GET", path, buildHeaders(token, emptyMap()))).body

  private fun buildHeaders(token: String?, extra: Map<String, String>): Map<String, String> = buildMap {
    put(AccountHeaders.STORE_VERSION, storeVersion)
    put("accept", "application/json")
    if (token != null) put("authorization", "Bearer $token")
    putAll(extra)
  }

  private suspend fun raw(request: ServerRequest): ServerResponse {
    val response = http.execute(baseUrl, request)
    response.serverTimeMillis?.let { serverClockOffsetMillis = it - System.currentTimeMillis() }
    if (response.status in 200..299 && response.status != 202) return response
    val json = response.json
    val code = (json?.get("error") as? JsonPrimitive)?.content ?: if (response.status == 202) "waiting" else "unavailable"
    throw AccountServerException(
      status = response.status,
      code = code,
      message = (json?.get("message") as? JsonPrimitive)?.content ?: "Il servizio non è raggiungibile. Riprova più tardi.",
      reason = (json?.get("reason") as? JsonPrimitive)?.content,
      retryAfterSeconds = (json?.get("retryAfter") as? JsonPrimitive)?.longOrNull,
      body = json,
    )
  }

  /** L'ora del server stimata, in millisecondi. */
  fun serverNow(): Long = System.currentTimeMillis() + serverClockOffsetMillis
}
