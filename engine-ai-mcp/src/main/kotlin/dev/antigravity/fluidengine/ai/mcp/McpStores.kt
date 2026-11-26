package dev.antigravity.fluidengine.ai.mcp

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.antigravity.fluidengine.ai.keys.AiDataStores
import dev.antigravity.fluidengine.ai.keys.KeystoreCipher
import dev.antigravity.fluidengine.ai.keys.SecretCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Cosa fa l'assistente prima di usare uno strumento di un connettore (4.0, F9.2). Di serie [ASK]: le
 * indicazioni del server ("legge e basta") sono indizi che un server qualunque puo' scrivere come vuole,
 * quindi una scrittura non parte mai senza il si' dell'utente. [ALWAYS] lo decide l'utente, strumento per
 * strumento, nella pagina «Connettori»; [OFF] lo toglie all'assistente.
 */
enum class McpToolPolicy { ASK, ALWAYS, OFF }

/** Uno strumento approvato dall'utente: com'era quando l'ha visto ([hash]) e la sua regola. */
data class McpApproval(val hash: String, val policy: McpToolPolicy = McpToolPolicy.ASK)

/**
 * Un connettore salvato (4.0, F9.2): l'indirizzo, il nome, gli strumenti dell'ultimo `tools/list` (cosi'
 * il catalogo si monta senza collegarsi a ogni avvio), le approvazioni e cio' che il server ha detto di
 * se'. Niente segreti: i gettoni stanno cifrati in [McpTokenStore].
 */
data class McpConnector(
  val id: String,
  val name: String,
  val url: String,
  val addedAtMillis: Long,
  val tools: List<McpToolInfo> = emptyList(),
  val approvals: Map<String, McpApproval> = emptyMap(),
  val server: McpServerInfo? = null,
  /** Le istruzioni del server: si mostrano all'utente, al modello mai. */
  val instructions: String? = null,
  val protocolVersion: String? = null,
  /** Il server chiede l'accesso (l'ultimo tentativo ha avuto un 401). */
  val needsSignIn: Boolean = false,
  /** I metadati del 401: dove stanno quelli del server, e lo scope chiesto. */
  val resourceMetadataUrl: String? = null,
  val scope: String? = null,
  val lastError: String? = null,
  val refreshedAtMillis: Long? = null,
)

/** L'elenco dei connettori, in un file DataStore suo (4.0, F9.2). */
class McpConnectorStore(private val store: DataStore<Preferences>) {

  constructor(context: Context) : this(context.mcpConnectorsStore)

  val connectors: Flow<List<McpConnector>> = store.data.map { prefs -> decode(prefs[KEY]) }

  suspend fun current(): List<McpConnector> = connectors.first()

  suspend fun get(id: String): McpConnector? = current().firstOrNull { it.id == id }

  /** Cambia la lista intera in un passo solo (DataStore lo serializza: niente scritture perse). */
  suspend fun update(transform: (List<McpConnector>) -> List<McpConnector>) {
    store.edit { prefs -> prefs[KEY] = encode(transform(decode(prefs[KEY]))).toString() }
  }

  suspend fun put(connector: McpConnector) = update { list -> list.filter { it.id != connector.id } + connector }

  suspend fun edit(id: String, transform: (McpConnector) -> McpConnector) = update { list -> list.map { if (it.id == id) transform(it) else it } }

  suspend fun remove(id: String) = update { list -> list.filter { it.id != id } }

  companion object {
    private val KEY = stringPreferencesKey("connectors_v1")

    fun open(file: java.io.File): McpConnectorStore = McpConnectorStore(AiDataStores.open(file))

    internal fun encode(list: List<McpConnector>): JsonArray = buildJsonArray {
      list.forEach { c ->
        add(buildJsonObject {
          put("id", c.id)
          put("name", c.name)
          put("url", c.url)
          put("added", c.addedAtMillis)
          put("tools", buildJsonArray { c.tools.forEach { add(McpCodec.toJson(it)) } })
          put("approvals", buildJsonObject { c.approvals.forEach { (k, v) -> put(k, buildJsonObject { put("hash", v.hash); put("policy", v.policy.name) }) } })
          c.server?.let { s -> put("server", buildJsonObject { put("name", s.name); s.version?.let { put("version", it) }; s.title?.let { put("title", it) } }) }
          c.instructions?.let { put("instructions", it) }
          c.protocolVersion?.let { put("protocol", it) }
          put("needsSignIn", c.needsSignIn)
          c.resourceMetadataUrl?.let { put("resourceMetadata", it) }
          c.scope?.let { put("scope", it) }
          c.lastError?.let { put("error", it) }
          c.refreshedAtMillis?.let { put("refreshed", it) }
        })
      }
    }

    internal fun decode(raw: String?): List<McpConnector> {
      val array = raw?.let { runCatching { Json.parseToJsonElement(it) as? JsonArray }.getOrNull() } ?: return emptyList()
      return array.mapNotNull { element ->
        val o = element as? JsonObject ?: return@mapNotNull null
        fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
        val server = o["server"] as? JsonObject
        McpConnector(
          id = s("id") ?: return@mapNotNull null,
          name = s("name") ?: "Connettore",
          url = s("url") ?: return@mapNotNull null,
          addedAtMillis = (o["added"] as? JsonPrimitive)?.longOrNull ?: 0L,
          tools = (o["tools"] as? JsonArray).orEmpty().mapNotNull { McpCodec.tool(it) },
          approvals = (o["approvals"] as? JsonObject).orEmpty().mapNotNull { (k, v) ->
            val a = v as? JsonObject ?: return@mapNotNull null
            val hash = (a["hash"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val policy = (a["policy"] as? JsonPrimitive)?.contentOrNull?.let { p -> McpToolPolicy.entries.firstOrNull { it.name == p } } ?: McpToolPolicy.ASK
            k to McpApproval(hash, policy)
          }.toMap(),
          server = server?.let { McpServerInfo((it["name"] as? JsonPrimitive)?.contentOrNull ?: "server", (it["version"] as? JsonPrimitive)?.contentOrNull, (it["title"] as? JsonPrimitive)?.contentOrNull) },
          instructions = s("instructions"),
          protocolVersion = s("protocol"),
          needsSignIn = (o["needsSignIn"] as? JsonPrimitive)?.booleanOrNull == true,
          resourceMetadataUrl = s("resourceMetadata"),
          scope = s("scope"),
          lastError = s("error"),
          refreshedAtMillis = (o["refreshed"] as? JsonPrimitive)?.longOrNull,
        )
      }
    }
  }
}

/**
 * I segreti dei connettori (4.0, F9.2), cifrati col Keystore come le chiavi dei provider ([SecretCipher],
 * `KeystoreCipher` con un alias suo): i gettoni, il `client_id` registrato con i metadati del server di
 * accesso, e l'accesso a meta' (lo `state` e il verificatore PKCE, che devono sopravvivere al browser che
 * si apre e magari al processo che muore). In chiaro un gettone non esce mai da un `Flow`: si legge quando
 * serve e si dimentica.
 *
 * Se il Keystore non decifra piu' (telefono ripristinato) la voce si cancella: il connettore torna "da
 * collegare", meglio di un 401 senza spiegazione.
 */
class McpTokenStore(
  private val store: DataStore<Preferences>,
  private val cipher: SecretCipher,
) {

  constructor(context: Context, cipher: SecretCipher = KeystoreCipher(ALIAS)) : this(context.mcpTokensStore, cipher)

  /** Gli id dei connettori che hanno dei gettoni: per la pagina, senza leggerli. */
  val signedIn: Flow<Set<String>> = store.data.map { prefs ->
    prefs.asMap().keys.map { it.name }.filter { it.startsWith(TOKENS) }.map { it.removePrefix(TOKENS) }.toSet()
  }

  suspend fun tokens(id: String): McpTokens? = read(TOKENS + id)?.let { json ->
    val access = (json["access"] as? JsonPrimitive)?.contentOrNull ?: return null
    McpTokens(access, (json["refresh"] as? JsonPrimitive)?.contentOrNull, (json["expires"] as? JsonPrimitive)?.longOrNull, (json["scope"] as? JsonPrimitive)?.contentOrNull)
  }

  suspend fun setTokens(id: String, tokens: McpTokens?) = write(TOKENS + id, tokens?.let {
    buildJsonObject {
      put("access", it.accessToken)
      it.refreshToken?.let { r -> put("refresh", r) }
      it.expiresAtMillis?.let { e -> put("expires", e) }
      it.scope?.let { s -> put("scope", s) }
    }
  })

  /** Il cliente registrato per un connettore, con il server di accesso. */
  suspend fun client(id: String): Pair<String, McpAuthServer>? = read(CLIENT + id)?.let { json ->
    val clientId = (json["client"] as? JsonPrimitive)?.contentOrNull ?: return null
    val server = authServer(json["server"] as? JsonObject) ?: return null
    clientId to server
  }

  suspend fun setClient(id: String, clientId: String?, server: McpAuthServer?) = write(CLIENT + id, if (clientId == null || server == null) null else buildJsonObject {
    put("client", clientId)
    put("server", authServerJson(server))
  })

  /** L'accesso cominciato, che aspetta il ritorno dal browser. */
  suspend fun pending(id: String): McpAuthRequest? = read(PENDING + id)?.let { json ->
    fun s(key: String) = (json[key] as? JsonPrimitive)?.contentOrNull
    McpAuthRequest(
      s("url") ?: return null, s("state") ?: return null, s("verifier") ?: return null, s("client") ?: return null,
      authServer(json["server"] as? JsonObject) ?: return null, s("resource") ?: return null, s("redirect") ?: return null,
    )
  }

  suspend fun setPending(id: String, request: McpAuthRequest?) = write(PENDING + id, request?.let {
    buildJsonObject {
      put("url", it.authorizationUrl)
      put("state", it.state)
      put("verifier", it.codeVerifier)
      put("client", it.clientId)
      put("server", authServerJson(it.server))
      put("resource", it.resource)
      put("redirect", it.redirectUri)
    }
  })

  /** Il connettore che aspetta il ritorno con questo `state`, se c'e'. */
  suspend fun pendingFor(state: String): String? {
    val ids = store.data.first().asMap().keys.map { it.name }.filter { it.startsWith(PENDING) }.map { it.removePrefix(PENDING) }
    return ids.firstOrNull { pending(it)?.state == state }
  }

  /** Dimentica tutto di un connettore (quando l'utente lo toglie). */
  suspend fun forget(id: String) {
    store.edit { prefs ->
      prefs -= stringPreferencesKey(TOKENS + id)
      prefs -= stringPreferencesKey(CLIENT + id)
      prefs -= stringPreferencesKey(PENDING + id)
    }
  }

  private suspend fun read(key: String): JsonObject? {
    val blob = store.data.first()[stringPreferencesKey(key)] ?: return null
    val plain = withContext(Dispatchers.IO) { cipher.decrypt(blob) }
    if (plain == null) {
      store.edit { it -= stringPreferencesKey(key) }
      return null
    }
    return runCatching { Json.parseToJsonElement(plain) as? JsonObject }.getOrNull()
  }

  private suspend fun write(key: String, value: JsonObject?) {
    val blob = value?.let { v -> withContext(Dispatchers.IO) { cipher.encrypt(v.toString()) } }
    store.edit { prefs ->
      if (blob == null) prefs -= stringPreferencesKey(key) else prefs[stringPreferencesKey(key)] = blob
    }
  }

  private fun authServerJson(s: McpAuthServer) = buildJsonObject {
    put("issuer", s.issuer)
    put("authorization", s.authorizationEndpoint)
    put("token", s.tokenEndpoint)
    s.registrationEndpoint?.let { put("registration", it) }
    put("scopes", buildJsonArray { s.scopesSupported.forEach { add(JsonPrimitive(it)) } })
    put("methods", buildJsonArray { s.codeChallengeMethods.forEach { add(JsonPrimitive(it)) } })
  }

  private fun authServer(o: JsonObject?): McpAuthServer? {
    o ?: return null
    fun s(key: String) = (o[key] as? JsonPrimitive)?.contentOrNull
    fun l(key: String) = (o[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    return McpAuthServer(s("issuer") ?: return null, s("authorization") ?: return null, s("token") ?: return null, s("registration"), l("scopes"), l("methods"))
  }

  companion object {
    /** L'alias del Keystore dei connettori: diverso da quello delle chiavi dei provider. */
    const val ALIAS = "fluidengine.ai.mcp"
    private const val TOKENS = "tokens_"
    private const val CLIENT = "client_"
    private const val PENDING = "pending_"

    fun open(file: java.io.File, cipher: SecretCipher = KeystoreCipher(ALIAS)): McpTokenStore = McpTokenStore(AiDataStores.open(file), cipher)
  }
}

/**
 * Le credenziali di un connettore per [McpClient] (4.0, F9.2): il gettone cifrato in [McpTokenStore],
 * rinnovato da [McpOAuth] quando scade o quando il server risponde 401.
 */
class McpAuthorization(
  private val connectorId: String,
  private val serverUrl: String,
  private val tokens: McpTokenStore,
  private val oauth: McpOAuth,
  private val clock: () -> Long = System::currentTimeMillis,
) : McpCredentials {

  override suspend fun bearer(): String? {
    val current = tokens.tokens(connectorId) ?: return null
    if (!current.expired(clock())) return current.accessToken
    return renew(current)?.accessToken ?: current.accessToken
  }

  override suspend fun refreshAfterUnauthorized(rejected: String?): Boolean {
    val current = tokens.tokens(connectorId) ?: return false
    return renew(current) != null
  }

  private suspend fun renew(current: McpTokens): McpTokens? {
    if (current.refreshToken == null) return null
    val (clientId, server) = tokens.client(connectorId) ?: return null
    return try {
      oauth.refresh(server, clientId, serverUrl, current).also { tokens.setTokens(connectorId, it) }
    } catch (e: McpError.AuthRequired) {
      // Il gettone di rinnovo non vale piu': si torna a "da collegare".
      tokens.setTokens(connectorId, null)
      null
    }
  }
}

private val Context.mcpConnectorsStore: DataStore<Preferences> by preferencesDataStore(
  name = "ai_mcp_connectors",
  corruptionHandler = AiDataStores.corruptionHandler(),
)

private val Context.mcpTokensStore: DataStore<Preferences> by preferencesDataStore(
  name = "ai_mcp_tokens",
  corruptionHandler = AiDataStores.corruptionHandler(),
)
