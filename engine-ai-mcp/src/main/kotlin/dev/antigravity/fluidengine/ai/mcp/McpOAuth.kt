package dev.antigravity.fluidengine.ai.mcp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Una risposta HTTP gia' letta, per l'accesso: lo stato e il JSON (null se non lo era). */
data class McpHttpReply(val status: Int, val json: JsonObject?)

/**
 * L'HTTP che serve all'accesso: leggere un documento JSON, mandare un modulo o un JSON. Un'interfaccia
 * perche' le prove la fingono; l'app usa [McpHttpJson.Default].
 */
interface McpHttpJson {
  suspend fun get(url: String): McpHttpReply
  suspend fun postForm(url: String, fields: Map<String, String>): McpHttpReply
  suspend fun postJson(url: String, body: JsonObject): McpHttpReply

  /** Su `HttpURLConnection`, senza redirect, con i limiti di [McpHttpTransport]. */
  class Default(
    private val userAgent: String = "FluidEngine-MCP",
    private val io: CoroutineDispatcher = Dispatchers.IO,
  ) : McpHttpJson {
    override suspend fun get(url: String) = exchange(url, "GET", null, null)

    override suspend fun postForm(url: String, fields: Map<String, String>) =
      exchange(url, "POST", fields.entries.joinToString("&") { (k, v) -> "${McpOAuth.enc(k)}=${McpOAuth.enc(v)}" }, "application/x-www-form-urlencoded")

    override suspend fun postJson(url: String, body: JsonObject) = exchange(url, "POST", body.toString(), "application/json")

    private suspend fun exchange(url: String, method: String, body: String?, type: String?): McpHttpReply = withContext(io) {
      require(McpUrls.allowed(url)) { "solo https: $url" }
      val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = method
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", userAgent)
        setRequestProperty("Accept", "application/json")
      }
      try {
        body?.let {
          val bytes = it.toByteArray(Charsets.UTF_8)
          connection.doOutput = true
          connection.setRequestProperty("Content-Type", type)
          connection.setFixedLengthStreamingMode(bytes.size)
          connection.outputStream.use { out -> out.write(bytes) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8).take(64_000) }.orEmpty()
        McpHttpReply(code, runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull())
      } catch (e: IOException) {
        throw McpError.Network(e)
      } finally {
        connection.disconnect()
      }
    }
  }
}

/** I metadati del server di autorizzazione che servono (RFC 8414). */
data class McpAuthServer(
  val issuer: String,
  val authorizationEndpoint: String,
  val tokenEndpoint: String,
  val registrationEndpoint: String?,
  val scopesSupported: List<String>,
  val codeChallengeMethods: List<String>,
)

/** I gettoni di un connettore. Vivono solo cifrati ([McpTokenStore]); qui un momento, in memoria. */
data class McpTokens(
  val accessToken: String,
  val refreshToken: String?,
  /** Quando scade il gettone d'accesso, in millisecondi; null = il server non l'ha detto. */
  val expiresAtMillis: Long?,
  val scope: String?,
) {
  fun expired(nowMillis: Long, marginMillis: Long = 60_000L): Boolean = expiresAtMillis != null && nowMillis + marginMillis >= expiresAtMillis
}

/**
 * Un accesso cominciato e non ancora finito: l'indirizzo da aprire nel browser e cio' che serve per
 * finirlo quando il browser torna all'app ([McpOAuth.complete]). Lo [state] difende dal ritorno
 * contraffatto, il [codeVerifier] (PKCE) da chi intercetta il codice.
 */
data class McpAuthRequest(
  val authorizationUrl: String,
  val state: String,
  val codeVerifier: String,
  val clientId: String,
  val server: McpAuthServer,
  val resource: String,
  val redirectUri: String,
)

/**
 * L'accesso con l'account a un connettore (4.0, F9.2): OAuth 2.1 con PKCE, come vuole la specifica MCP
 * ("Authorization", 2025-06-18 e 2025-11-25).
 *
 * 1. **Dove si entra.** Il server, al primo 401, dice dove stanno i suoi metadati
 *    (`WWW-Authenticate: Bearer resource_metadata="..."`, RFC 9728); se non lo dice si prova
 *    `/.well-known/oauth-protected-resource` sul suo indirizzo e poi sull'origine. Dai metadati si
 *    prende il server di autorizzazione, e da quello i suoi (`/.well-known/oauth-authorization-server`,
 *    poi `openid-configuration`). Un server che non dichiara `S256` fra i metodi PKCE si rifiuta: la
 *    specifica lo chiede.
 * 2. **Chi e' l'app.** Se l'app non ha un `client_id` per quel server e il server lo permette, si
 *    registra da se' (RFC 7591) come cliente pubblico, senza segreto.
 * 3. **L'accesso.** [begin] da' l'indirizzo da aprire (nel browser o in una Custom Tab: mai dentro una
 *    WebView dell'app), con `state`, la sfida PKCE e il parametro `resource` (RFC 8707: il gettone vale
 *    solo per quel server). Al ritorno, [complete] controlla lo `state` e scambia il codice con i gettoni.
 * 4. **Il rinnovo.** [refresh] con il gettone di rinnovo, quando quello d'accesso scade o il server
 *    risponde 401.
 */
class McpOAuth(
  private val http: McpHttpJson = McpHttpJson.Default(),
  /** Dove il browser torna all'app: uno schema dell'app (`dev.pampa.pampai:/mcp`) o un App Link https. */
  private val redirectUri: String,
  private val clientName: String,
  private val clock: () -> Long = System::currentTimeMillis,
  private val random: SecureRandom = SecureRandom(),
) {

  /** Trova il server di autorizzazione per il connettore a [serverUrl], dato l'eventuale indirizzo dei metadati del 401. */
  suspend fun discover(serverUrl: String, resourceMetadataUrl: String?): Pair<McpAuthServer, List<String>> {
    val origin = McpUrls.origin(serverUrl)
    val path = java.net.URL(serverUrl).path.trimEnd('/')
    val candidates = listOfNotNull(
      resourceMetadataUrl,
      "$origin/.well-known/oauth-protected-resource$path".takeIf { path.isNotEmpty() },
      "$origin/.well-known/oauth-protected-resource",
    ).distinct()
    val resource = candidates.firstNotNullOfOrNull { url -> http.get(url).takeIf { it.status in 200..299 }?.json }
    // Un server senza metadati della risorsa (specifiche vecchie): l'autorizzazione sta sulla sua origine.
    val issuer = (resource?.get("authorization_servers") as? JsonArray)?.firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull } ?: origin
    val scopes = (resource?.get("scopes_supported") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val issuerOrigin = McpUrls.origin(issuer)
    val issuerPath = java.net.URL(issuer).path.trimEnd('/')
    val metadataUrls = listOfNotNull(
      "$issuerOrigin/.well-known/oauth-authorization-server$issuerPath",
      "$issuerOrigin/.well-known/openid-configuration$issuerPath",
      "$issuer/.well-known/openid-configuration".takeIf { issuerPath.isNotEmpty() },
    ).distinct()
    val meta = metadataUrls.firstNotNullOfOrNull { url -> http.get(url).takeIf { it.status in 200..299 }?.json }
      ?: throw McpError.Protocol("il server di accesso non ha metadati ($issuer)")
    fun s(key: String) = (meta[key] as? JsonPrimitive)?.contentOrNull
    fun list(key: String) = (meta[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    val server = McpAuthServer(
      issuer = s("issuer") ?: issuer,
      authorizationEndpoint = s("authorization_endpoint") ?: throw McpError.Protocol("manca authorization_endpoint"),
      tokenEndpoint = s("token_endpoint") ?: throw McpError.Protocol("manca token_endpoint"),
      registrationEndpoint = s("registration_endpoint"),
      scopesSupported = list("scopes_supported"),
      codeChallengeMethods = list("code_challenge_methods_supported"),
    )
    if ("S256" !in server.codeChallengeMethods) throw McpError.Protocol("il server di accesso non dichiara PKCE S256")
    listOf(server.authorizationEndpoint, server.tokenEndpoint).forEach { require(McpUrls.allowed(it)) { "indirizzo di accesso non https: $it" } }
    return server to scopes
  }

  /** Il `client_id` per questo server: quello dato dall'app, o uno nuovo registrato da se' (RFC 7591). */
  suspend fun clientId(server: McpAuthServer, preset: String?): String {
    preset?.let { return it }
    val endpoint = server.registrationEndpoint ?: throw McpError.Protocol("il server di accesso non permette la registrazione: serve un client_id dell'app")
    val reply = http.postJson(endpoint, buildJsonObject {
      put("client_name", clientName)
      put("redirect_uris", buildJsonArray { add(JsonPrimitive(redirectUri)) })
      put("grant_types", buildJsonArray { add(JsonPrimitive("authorization_code")); add(JsonPrimitive("refresh_token")) })
      put("response_types", buildJsonArray { add(JsonPrimitive("code")) })
      put("token_endpoint_auth_method", "none")
    })
    return (reply.json?.get("client_id") as? JsonPrimitive)?.contentOrNull
      ?: throw McpError.Protocol("registrazione rifiutata (${reply.status})")
  }

  /** L'inizio dell'accesso: l'indirizzo da aprire e quello che serve a [complete]. */
  fun begin(serverUrl: String, server: McpAuthServer, clientId: String, scope: String?): McpAuthRequest {
    val verifier = randomToken(32)
    val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    val state = randomToken(16)
    val resource = McpUrls.canonical(serverUrl)
    val query = linkedMapOf(
      "response_type" to "code",
      "client_id" to clientId,
      "redirect_uri" to redirectUri,
      "code_challenge" to challenge,
      "code_challenge_method" to "S256",
      "state" to state,
      "resource" to resource,
    )
    scope?.takeIf { it.isNotBlank() }?.let { query["scope"] = it }
    val separator = if ('?' in server.authorizationEndpoint) "&" else "?"
    val url = server.authorizationEndpoint + separator + query.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
    return McpAuthRequest(url, state, verifier, clientId, server, resource, redirectUri)
  }

  /**
   * La fine dell'accesso: [callback] e' l'indirizzo con cui il browser e' tornato all'app. Uno `state`
   * diverso, un errore del server o un codice che manca fermano tutto; se no, i gettoni.
   */
  suspend fun complete(request: McpAuthRequest, callback: String): McpTokens {
    val params = query(callback)
    // Solo il codice: la descrizione e' testo del server di accesso (R3-S1).
    params["error"]?.let { throw McpError.Protocol("accesso rifiutato (${McpError.code(it) ?: "errore"})") }
    if (params["state"] != request.state) throw McpError.Protocol("il ritorno dall'accesso non corrisponde (state)")
    val code = params["code"] ?: throw McpError.Protocol("il ritorno dall'accesso non ha il codice")
    return tokens(request.server, mapOf(
      "grant_type" to "authorization_code",
      "code" to code,
      "redirect_uri" to request.redirectUri,
      "client_id" to request.clientId,
      "code_verifier" to request.codeVerifier,
      "resource" to request.resource,
    ), previousRefresh = null)
  }

  /** Il rinnovo dei gettoni. Il server puo' dare anche un gettone di rinnovo nuovo (rotazione): si tiene quello. */
  suspend fun refresh(server: McpAuthServer, clientId: String, serverUrl: String, current: McpTokens): McpTokens {
    val refresh = current.refreshToken ?: throw McpError.AuthRequired(null, current.scope)
    return tokens(server, mapOf(
      "grant_type" to "refresh_token",
      "refresh_token" to refresh,
      "client_id" to clientId,
      "resource" to McpUrls.canonical(serverUrl),
    ), previousRefresh = refresh)
  }

  private suspend fun tokens(server: McpAuthServer, fields: Map<String, String>, previousRefresh: String?): McpTokens {
    val reply = http.postForm(server.tokenEndpoint, fields)
    val json = reply.json
    if (reply.status !in 200..299 || json == null) {
      val error = (json?.get("error") as? JsonPrimitive)?.contentOrNull
      if (error == "invalid_grant") throw McpError.AuthRequired(null, null)
      throw McpError.Protocol("gettoni rifiutati (${reply.status}${McpError.code(error)?.let { ", $it" } ?: ""})")
    }
    val access = (json["access_token"] as? JsonPrimitive)?.contentOrNull ?: throw McpError.Protocol("manca access_token")
    val expiresIn = (json["expires_in"] as? JsonPrimitive)?.longOrNull
    return McpTokens(
      accessToken = access,
      refreshToken = (json["refresh_token"] as? JsonPrimitive)?.contentOrNull ?: previousRefresh,
      expiresAtMillis = expiresIn?.let { clock() + it * 1_000 },
      scope = (json["scope"] as? JsonPrimitive)?.contentOrNull,
    )
  }

  private fun randomToken(bytes: Int): String = base64Url(ByteArray(bytes).also { random.nextBytes(it) })

  companion object {
    internal fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    internal fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    /** I parametri di un indirizzo (la parte dopo `?`, e il frammento per chi torna col codice li'). */
    fun query(url: String): Map<String, String> {
      val part = url.substringAfter('?', "").substringBefore('#').ifEmpty { url.substringAfter('#', "") }
      return part.split('&').filter { '=' in it }.associate { pair ->
        URLDecoder.decode(pair.substringBefore('='), "UTF-8") to URLDecoder.decode(pair.substringAfter('='), "UTF-8")
      }
    }

    /** Un parametro di `WWW-Authenticate` (`Bearer resource_metadata="...", scope="..."`), o null. */
    fun parameter(header: String?, name: String): String? {
      if (header.isNullOrBlank()) return null
      val match = Regex("""(?i)\b${Regex.escape(name)}\s*=\s*(?:"([^"]*)"|([^\s,]+))""").find(header) ?: return null
      return match.groupValues[1].ifEmpty { match.groupValues[2] }.takeIf { it.isNotEmpty() }
    }
  }
}
