package dev.antigravity.fluidengine.ai.mcp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.core.DataStore
import dev.antigravity.fluidengine.ai.keys.SecretCipher
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Una cifratura finta ma vera: il testo esce rovesciato e marcato, cosi' una prova vede se qualcosa e' rimasto in chiaro. */
internal class ReversingCipher : SecretCipher {
  var broken = false
  override fun encrypt(plain: String): String = "x1:" + plain.reversed()
  override fun decrypt(blob: String): String? = if (broken || !blob.startsWith("x1:")) null else blob.removePrefix("x1:").reversed()
}

/** Il server di accesso finto: i metadati, la registrazione, i gettoni. Conta le chiamate e ricorda i moduli. */
internal class FakeAuthHttp(private val s256: Boolean = true) : McpHttpJson {
  val forms = mutableListOf<Pair<String, Map<String, String>>>()
  val gets = mutableListOf<String>()
  var issued = 0
  var lastChallenge: String? = null
  var refreshValid = true

  override suspend fun get(url: String): McpHttpReply {
    gets += url
    return when (url) {
      "https://mcp.example.com/.well-known/oauth-protected-resource/mcp", "https://meta.example.com/prm" -> McpHttpReply(200, buildJsonObject {
        put("resource", "https://mcp.example.com/mcp")
        put("authorization_servers", buildJsonArray { add(JsonPrimitive("https://auth.example.com")) })
        put("scopes_supported", buildJsonArray { add(JsonPrimitive("tools")) })
      })
      "https://auth.example.com/.well-known/oauth-authorization-server" -> McpHttpReply(200, buildJsonObject {
        put("issuer", "https://auth.example.com")
        put("authorization_endpoint", "https://auth.example.com/authorize")
        put("token_endpoint", "https://auth.example.com/token")
        put("registration_endpoint", "https://auth.example.com/register")
        put("code_challenge_methods_supported", buildJsonArray { if (s256) add(JsonPrimitive("S256")) else add(JsonPrimitive("plain")) })
      })
      else -> McpHttpReply(404, null)
    }
  }

  override suspend fun postForm(url: String, fields: Map<String, String>): McpHttpReply {
    forms += url to fields
    if (fields["grant_type"] == "refresh_token" && !refreshValid) return McpHttpReply(400, buildJsonObject { put("error", "invalid_grant") })
    if (fields["grant_type"] == "authorization_code") {
      val expected = OAuthDigest.challenge(fields["code_verifier"].orEmpty())
      if (expected != lastChallenge || fields["code"] != "codice-buono") return McpHttpReply(400, buildJsonObject { put("error", "invalid_grant") })
    }
    issued++
    return McpHttpReply(200, buildJsonObject {
      put("access_token", "accesso-$issued")
      put("token_type", "Bearer")
      put("expires_in", 3600)
      put("refresh_token", "rinnovo-$issued")
    })
  }

  override suspend fun postJson(url: String, body: JsonObject): McpHttpReply =
    McpHttpReply(201, buildJsonObject { put("client_id", "cliente-registrato"); put("client_name", (body["client_name"] as JsonPrimitive).content) })
}

internal object OAuthDigest {
  fun challenge(verifier: String): String = McpOAuth.base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
}

internal fun tempPrefs(name: String): DataStore<Preferences> {
  val file = File(Files.createTempDirectory(name).toFile(), "$name.preferences_pb")
  return PreferenceDataStoreFactory.create(scope = CoroutineScope(SupervisorJob() + Dispatchers.IO), produceFile = { file })
}

/** F9.2 - L'accesso con l'account (OAuth 2.1 con PKCE) e i gettoni cifrati. */
class McpOAuthTest {

  private val redirect = "dev.pampa.prova:/mcp"

  @Test
  fun `scopre il server di accesso, si registra, e l'indirizzo porta PKCE S256, state e resource`() = runBlocking {
    val http = FakeAuthHttp()
    val oauth = McpOAuth(http, redirect, "Prova")
    val (server, scopes) = oauth.discover("https://mcp.example.com/mcp", null)
    assertEquals("https://auth.example.com/token", server.tokenEndpoint)
    assertEquals(listOf("tools"), scopes)
    val clientId = oauth.clientId(server, null)
    assertEquals("cliente-registrato", clientId)
    val request = oauth.begin("https://mcp.example.com/mcp", server, clientId, "tools")
    val query = McpOAuth.query(request.authorizationUrl)
    assertEquals("S256", query["code_challenge_method"])
    assertEquals(OAuthDigest.challenge(request.codeVerifier), query["code_challenge"])
    assertEquals(request.state, query["state"])
    assertEquals("https://mcp.example.com/mcp", query["resource"])
    assertEquals(redirect, query["redirect_uri"])
    assertTrue(request.codeVerifier.length >= 43)
  }

  @Test
  fun `il ritorno con uno state diverso si ferma, quello giusto da' i gettoni, e il rinnovo ruota`() = runBlocking {
    val http = FakeAuthHttp()
    val oauth = McpOAuth(http, redirect, "Prova", clock = { 1_000L })
    val (server, _) = oauth.discover("https://mcp.example.com/mcp", "https://meta.example.com/prm")
    val request = oauth.begin("https://mcp.example.com/mcp", server, "cliente", null)
    http.lastChallenge = McpOAuth.query(request.authorizationUrl)["code_challenge"]
    val forged = runCatching { oauth.complete(request, "$redirect?code=codice-buono&state=altro") }.exceptionOrNull()
    assertTrue(forged is McpError.Protocol)
    assertTrue(http.forms.isEmpty())
    val denied = runCatching { oauth.complete(request, "$redirect?error=access_denied&state=${request.state}") }.exceptionOrNull()
    assertTrue(denied is McpError.Protocol)
    val tokens = oauth.complete(request, "$redirect?code=codice-buono&state=${request.state}")
    assertEquals("accesso-1", tokens.accessToken)
    assertEquals(3_601_000L, tokens.expiresAtMillis)
    assertEquals("https://mcp.example.com/mcp", http.forms.single().second["resource"])
    val renewed = oauth.refresh(server, "cliente", "https://mcp.example.com/mcp", tokens)
    assertEquals("accesso-2", renewed.accessToken)
    assertEquals("rinnovo-2", renewed.refreshToken)
  }

  @Test
  fun `un server di accesso senza PKCE S256 si rifiuta`() = runBlocking {
    val error = runCatching { McpOAuth(FakeAuthHttp(s256 = false), redirect, "Prova").discover("https://mcp.example.com/mcp", null) }.exceptionOrNull()
    assertTrue("$error", error is McpError.Protocol)
  }

  @Test
  fun `i gettoni stanno cifrati, il rinnovo li aggiorna e un Keystore che non decifra li cancella`() = runBlocking {
    val prefs = tempPrefs("mcp-tokens")
    val cipher = ReversingCipher()
    val store = McpTokenStore(prefs, cipher)
    store.setTokens("c1", McpTokens("segretissimo-123", "rinnovo-abc", expiresAtMillis = 10L, scope = "tools"))
    val raw = prefs.data.first().asMap().values.joinToString()
    assertFalse("in chiaro: $raw", raw.contains("segretissimo-123"))
    assertFalse(raw.contains("rinnovo-abc"))
    assertEquals(setOf("c1"), store.signedIn.first())

    // Scaduto: McpAuthorization lo rinnova e salva quello nuovo.
    val http = FakeAuthHttp()
    val oauth = McpOAuth(http, redirect, "Prova", clock = { 1_000L })
    val (server, _) = oauth.discover("https://mcp.example.com/mcp", null)
    store.setClient("c1", "cliente", server)
    val authorization = McpAuthorization("c1", "https://mcp.example.com/mcp", store, oauth, clock = { 1_000L })
    assertEquals("accesso-1", authorization.bearer())
    assertEquals("accesso-1", store.tokens("c1")!!.accessToken)
    assertEquals("refresh_token", http.forms.single().second["grant_type"])

    // Un gettone di rinnovo che non vale piu': si torna a "da collegare".
    http.refreshValid = false
    assertFalse(authorization.refreshAfterUnauthorized("accesso-1"))
    assertNull(store.tokens("c1"))

    // Il Keystore che non decifra piu' (telefono ripristinato): la voce sparisce.
    store.setTokens("c2", McpTokens("t", null, null, null))
    cipher.broken = true
    assertNull(store.tokens("c2"))
    assertTrue(store.signedIn.first().isEmpty())
  }

  @Test
  fun `l'accesso a meta' sopravvive e si ritrova dallo state, e togliere il connettore dimentica tutto`() = runBlocking {
    val store = McpTokenStore(tempPrefs("mcp-pending"), ReversingCipher())
    val http = FakeAuthHttp()
    val oauth = McpOAuth(http, redirect, "Prova")
    val (server, _) = oauth.discover("https://mcp.example.com/mcp", null)
    val request = oauth.begin("https://mcp.example.com/mcp", server, "cliente", null)
    store.setPending("c9", request)
    assertEquals("c9", store.pendingFor(request.state))
    assertEquals(request, store.pending("c9"))
    store.setTokens("c9", McpTokens("a", "r", null, null))
    store.forget("c9")
    assertNull(store.pending("c9"))
    assertNull(store.tokens("c9"))
    assertNotNull(Json.parseToJsonElement("{}"))
  }
}
