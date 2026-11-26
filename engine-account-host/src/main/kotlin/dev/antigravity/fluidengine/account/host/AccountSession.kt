package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.AccountServerException
import dev.antigravity.fluidengine.account.host.server.DeviceKey
import dev.antigravity.fluidengine.account.host.server.DeviceProof
import dev.antigravity.fluidengine.account.host.server.SessionKind
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.StoredSession
import dev.antigravity.fluidengine.account.host.server.TokenCache
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Lo stato della sessione dello Store su questo telefono. */
sealed interface HostSession {
  data object SignedOut : HostSession
  data class SignedIn(val accountId: String, val deviceId: String, val displayName: String, val kind: SessionKind) : HostSession
}

/** Come esce un dispositivo (`b\chiavi.md` § 4.4): chi esce da sé tiene le copie, chi è fatto uscire da remoto no. */
enum class ExitKind {
  /** «Esci da questo dispositivo»: chiavi via, copie sincronizzate e dati locali restano (R1). */
  VOLUNTARY,
  /** Fatto uscire da un altro dispositivo (`remote`, `lost`, `all`): via chiavi **e** copie sincronizzate. */
  REMOTE,
}

/** Il risultato di una richiesta di token per un'app: le stesse ragioni del protocollo (`b\api.md` § 5.3). */
sealed interface TokenOutcome {
  data class Ok(val token: String, val expiresInSeconds: Long) : TokenOutcome {
    override fun toString(): String = "Ok(…, $expiresInSeconds)"
  }
  data object SignedOut : TokenOutcome
  data object NotAllowed : TokenOutcome
  data object Disconnected : TokenOutcome
  data object Offline : TokenOutcome
  data class Unavailable(val reason: String) : TokenOutcome
}

/**
 * La sessione dello Store (`b\architettura.md` § 3; `b\api.md` § 3): il token d'accesso dello Store e quelli delle app
 * in memoria, il rinnovo col token di rinnovo **più la prova del dispositivo**, e l'uscita.
 *
 * Quando il server risponde `session_revoked`, la sessione pulisce: con un'uscita da remoto ([ExitKind.REMOTE])
 * cancella chiavi e copie e avvisa le app con `wiped`; con un'uscita volontaria cancella solo le chiavi.
 */
class AccountSession(
  private val client: AccountServerClient,
  private val baseUrl: String,
  private val sessions: SessionStore,
  private val deviceKey: DeviceKey,
  private val tokens: TokenCache,
  private val keyring: CustodyKeyring,
  private val policy: AppPolicy,
  /** Avvisa le app (broadcast `CHANGED`): `state` dopo ogni entrata e uscita, `wiped` dopo un'uscita da remoto. */
  private val notify: (what: List<String>) -> Unit = {},
) {
  private val mutex = Mutex()
  private val _state = MutableStateFlow<HostSession>(HostSession.SignedOut)
  val state: StateFlow<HostSession> = _state.asStateFlow()

  /** L'ultima uscita da remoto vista, per rispondere alle app che chiedono conferma di `wiped` (`b\api.md` § 5.4). */
  val lastRemoteExit: Long get() = sessions.remoteExitAt()

  /** Rilegge la sessione dal disco all'avvio dello Store. */
  fun restore() {
    val s = runCatching { sessions.load() }.getOrNull()
    _state.value = s?.let { HostSession.SignedIn(it.accountId, it.deviceId, it.displayName, it.kind) } ?: HostSession.SignedOut
  }

  /**
   * Una risposta d'ingresso del server (`register/verify`, `login/verify`, `recovery/redeem`, `google/login`): salva
   * la sessione e mette in memoria il token d'accesso dello Store.
   */
  fun adopt(response: JsonObject, displayName: String, storePackage: String) {
    fun s(k: String) = (response[k] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("manca $k")
    val session = StoredSession(s("accountId"), s("deviceId"), s("refreshToken"), SessionKind.of((response["kind"] as? JsonPrimitive)?.content), displayName, storePackage)
    sessions.save(session)
    sessions.clearRemoteExit()
    tokens.clear()
    tokens.put(STORE_KEY, "account", emptySet(), s("accessToken"), (response["expiresIn"] as? JsonPrimitive)?.longOrNull ?: 900)
    _state.value = HostSession.SignedIn(session.accountId, session.deviceId, displayName, session.kind)
    notify(listOf("state"))
  }

  /**
   * F10.7 · La sessione nata dal codice di recupero è diventata normale sul server: una passkey aggiunta o Google
   * collegato (`b\api.md` § 2). Lo diventa anche qui, e le app lo vedono (`sessionKind`). Dopo, la custodia può scrivere
   * la porta di questo dispositivo (`CustodyService.ensureDeviceDoor`). Una sessione già normale non cambia.
   */
  fun promote() {
    val s = runCatching { sessions.load() }.getOrNull() ?: return
    if (s.kind != SessionKind.RECOVERY) return
    val full = StoredSession(s.accountId, s.deviceId, s.refreshToken, SessionKind.FULL, s.displayName, s.storePackage)
    sessions.save(full)
    _state.value = HostSession.SignedIn(full.accountId, full.deviceId, full.displayName, full.kind)
    notify(listOf("state"))
  }

  /** Il campo `device` delle richieste d'ingresso: nome, pacchetto dello Store, e la pubblica della chiave del dispositivo. */
  fun deviceField(name: String, storePackage: String, osApi: Int?, storeVersion: String): JsonObject = buildJsonObject {
    put("name", name)
    put("package", storePackage)
    if (osApi != null) put("osApi", osApi)
    put("storeVersion", storeVersion)
    put("deviceKey", deviceKey.publicJwk)
  }

  /**
   * Il token d'accesso dello Store (`aud: account`), rinnovato se serve. [IOException] senza rete;
   * [AccountServerException] per il resto. Un `session_revoked` pulisce prima di essere rilanciato.
   */
  suspend fun storeToken(): String = mutex.withLock {
    tokens.get(STORE_KEY, "account", emptySet())?.let { return it.token }
    val session = sessions.load() ?: throw AccountServerException(401, "unauthorized", "Non sei entrato nell'Account Pampa.")
    val url = baseUrl.trimEnd('/') + REFRESH_PATH
    val proof = DeviceProof.create(deviceKey, "POST", url, session.refreshToken, client.serverNow())
    val r = try {
      client.call("POST", REFRESH_PATH, buildJsonObject { put("refreshToken", session.refreshToken) }, headers = mapOf("pampa-device-proof" to proof))
    } catch (e: AccountServerException) {
      if (e.isSessionRevoked) revokedLocked(e.reason)
      throw e
    }
    val token = (r["accessToken"] as JsonPrimitive).content
    tokens.put(STORE_KEY, "account", emptySet(), token, (r["expiresIn"] as? JsonPrimitive)?.longOrNull ?: 900).token
  }

  /**
   * Un token per l'app [packageName] (`b\architettura.md` § 2.3). Prima la tabella sul telefono (un'app non ammessa
   * non fa partire niente), poi la cache, poi il server. Il pacchetto lo ha già ricavato dal sistema chi chiama.
   */
  suspend fun appToken(packageName: String, audience: String, scopes: Set<String>): TokenOutcome {
    if (_state.value is HostSession.SignedOut) return TokenOutcome.SignedOut
    if (!policy.allows(packageName, audience, scopes)) return TokenOutcome.NotAllowed
    tokens.get(packageName, audience, scopes)?.let { return TokenOutcome.Ok(it.token, tokens.secondsLeft(it)) }
    return try {
      val r = client.call("POST", "/v1/apps/$packageName/token", buildJsonObject {
        put("audience", audience)
        putJsonArray("scopes") { scopes.sorted().forEach { add(JsonPrimitive(it)) } }
      }, token = storeToken())
      val expires = (r["expiresIn"] as? JsonPrimitive)?.longOrNull ?: 900
      val token = (r["token"] as JsonPrimitive).content
      tokens.put(packageName, audience, scopes, token, expires)
      TokenOutcome.Ok(token, expires)
    } catch (e: IOException) {
      TokenOutcome.Offline
    } catch (e: AccountServerException) {
      when {
        e.isSessionRevoked || e.code == "unauthorized" -> TokenOutcome.SignedOut
        e.code == "app_disconnected" -> TokenOutcome.Disconnected.also { tokens.forget(packageName) }
        e.code == "forbidden" -> TokenOutcome.NotAllowed
        e.code == "unavailable" || e.status >= 500 -> TokenOutcome.Offline
        else -> TokenOutcome.Unavailable(e.message)
      }
    }
  }

  /** «Esci da questo dispositivo»: revoca sul server (se c'è rete) e pulisce le chiavi. Le copie restano (R1). */
  suspend fun signOut() {
    runCatching { client.call("POST", "/v1/session/logout", JsonObject(emptyMap()), token = storeToken()) }
    mutex.withLock { clearLocked(ExitKind.VOLUNTARY) }
  }

  /** Il server ha detto `session_revoked` (anche dal campanello): pulisce secondo il motivo. */
  suspend fun onRevoked(reason: String?) = mutex.withLock { revokedLocked(reason) }

  private fun revokedLocked(reason: String?) {
    clearLocked(if (reason in REMOTE_REASONS) ExitKind.REMOTE else ExitKind.VOLUNTARY)
  }

  private fun clearLocked(kind: ExitKind) {
    tokens.clear()
    sessions.clear()
    keyring.wipe()
    deviceKey.destroy()
    _state.value = HostSession.SignedOut
    if (kind == ExitKind.REMOTE) {
      sessions.markRemoteExit(System.currentTimeMillis())
      notify(listOf("state", "wiped"))
    } else {
      notify(listOf("state"))
    }
  }

  companion object {
    const val REFRESH_PATH = "/v1/session/refresh"
    private const val STORE_KEY = "\u0000store"

    /** I motivi di `session_revoked` che vogliono la cancellazione delle copie (`b\chiavi.md` § 4.4; F10.3). */
    val REMOTE_REASONS = setOf("remote", "lost", "all")
  }
}
