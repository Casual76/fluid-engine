package dev.antigravity.fluidengine.account.host.server

import android.os.SystemClock
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.CustodyVault
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Di che tipo è la sessione: normale, o nata dal codice di recupero (S-rec, `b\api.md` § 2). */
enum class SessionKind(val wire: String) {
  FULL("full"), RECOVERY("recovery");

  companion object {
    fun of(wire: String?): SessionKind = entries.firstOrNull { it.wire == wire } ?: FULL
  }
}

/** La sessione dello Store su questo telefono. Il token di rinnovo sta solo qui, mai in un log, mai nel backup. */
class StoredSession(
  val accountId: String,
  val deviceId: String,
  internal val refreshToken: String,
  val kind: SessionKind,
  val displayName: String,
  /** Il pacchetto dello Store che ha aperto la sessione (vero o di sviluppo, critica M9). */
  val storePackage: String,
) {
  override fun toString(): String = "StoredSession($accountId, $deviceId, ${kind.wire})"

  internal fun toJson(): JsonObject = buildJsonObject {
    put("v", 1)
    put("accountId", accountId)
    put("deviceId", deviceId)
    put("refreshToken", refreshToken)
    put("kind", kind.wire)
    put("displayName", displayName)
    put("storePackage", storePackage)
  }

  internal companion object {
    fun parse(o: JsonObject): StoredSession {
      fun s(k: String) = (o[k] as JsonPrimitive).content
      return StoredSession(s("accountId"), s("deviceId"), s("refreshToken"), SessionKind.of(s("kind")), s("displayName"), s("storePackage"))
    }
  }
}

/**
 * Il file della sessione (`b\architettura.md` § 3.1): chiuso con la cassaforte nella classe di
 * [CustodyKeyKind.SESSION] («consegna» con la raccomandazione A di Q7, così il rinnovo gira in tasca), dentro [dir]
 * che è `noBackupFilesDir`: **escluso dal backup di Android**. Copiato su un altro telefono non servirebbe comunque,
 * perché la chiave del Keystore non passa.
 */
class SessionStore(
  private val vault: CustodyVault,
  private val dir: File,
  private val choice: KeyClassPolicy.Choice = KeyClassPolicy.CHOICE,
) {
  private val file get() = File(dir, "sessione.bin")
  private val keyClass get() = KeyClassPolicy.classOf(CustodyKeyKind.SESSION, choice)

  fun save(session: StoredSession) {
    dir.mkdirs()
    val tmp = File(dir, "sessione.bin.nuovo")
    tmp.writeBytes(vault.seal(keyClass, session.toJson().toString().toByteArray(), AAD))
    if (!tmp.renameTo(file)) {
      file.delete()
      check(tmp.renameTo(file))
    }
  }

  /** La sessione, o `null` se nessuno è entrato. Una chiave del Keystore persa vuol dire rientrare (con la passkey è un tocco). */
  fun load(): StoredSession? {
    if (!file.exists()) return null
    return StoredSession.parse(Json.parseToJsonElement(vault.open(keyClass, file.readBytes(), AAD).decodeToString()) as JsonObject)
  }

  private val remoteExitFile get() = File(dir, "uscita-remota.txt")

  /**
   * Quando è avvenuta l'ultima uscita da remoto: le app la chiedono per confermare `wiped` (`bpi.md` § 5.4). Sta in
   * un file a parte, in chiaro (non è un segreto), così resta anche se lo Store si riavvia prima che l'app chieda.
   */
  fun markRemoteExit(at: Long) {
    dir.mkdirs()
    remoteExitFile.writeText(at.toString())
  }

  fun remoteExitAt(): Long = runCatching { remoteExitFile.readText().trim().toLong() }.getOrDefault(0L)

  /** Una nuova entrata chiude la storia dell'uscita di prima: un avviso `wiped` vecchio non cancella più niente. */
  fun clearRemoteExit() {
    remoteExitFile.delete()
  }

  fun clear() {
    if (file.exists()) {
      runCatching { file.writeBytes(ByteArray(file.length().toInt())) }
      file.delete()
    }
  }

  private companion object {
    val AAD = "pampa/v1/custody|sessione".toByteArray()
  }
}

/**
 * I token d'accesso in memoria (`b\architettura.md` § 2.3; R4): uno per app, pubblico e ambiti, per 15 minuti. Mai su
 * disco. Le scadenze si contano con l'orologio monotono ([elapsedMillis]), non con la data del telefono, che
 * l'utente può spostare (§ 3.5). Un token si rinnova [marginMillis] prima della scadenza.
 */
class TokenCache(
  private val elapsedMillis: () -> Long = { SystemClock.elapsedRealtime() },
  private val marginMillis: Long = 60_000,
) {
  /** Un token con la sua scadenza sull'orologio monotono. */
  class Entry(val token: String, val expiresAtElapsed: Long) {
    override fun toString(): String = "Entry(…, $expiresAtElapsed)"
  }

  private val entries = HashMap<String, Entry>()

  private fun key(packageName: String, audience: String, scopes: Set<String>) = "$packageName|$audience|${scopes.sorted().joinToString(" ")}"

  @Synchronized
  fun get(packageName: String, audience: String, scopes: Set<String>): Entry? {
    val e = entries[key(packageName, audience, scopes)] ?: return null
    return if (elapsedMillis() + marginMillis < e.expiresAtElapsed) e else null
  }

  /** Mette un token che vale [expiresInSeconds] da adesso (la durata dal server, non la sua data). */
  @Synchronized
  fun put(packageName: String, audience: String, scopes: Set<String>, token: String, expiresInSeconds: Long): Entry =
    Entry(token, elapsedMillis() + expiresInSeconds * 1000).also { entries[key(packageName, audience, scopes)] = it }

  /** Quanti secondi vale ancora [entry]: è l'`expiresIn` che si dà all'app. */
  fun secondsLeft(entry: Entry): Long = ((entry.expiresAtElapsed - elapsedMillis()) / 1000).coerceAtLeast(0)

  /** Via i token di un'app scollegata. */
  @Synchronized
  fun forget(packageName: String) {
    entries.keys.removeAll { it.startsWith("$packageName|") }
  }

  /** Uscita: via tutto. */
  @Synchronized
  fun clear() = entries.clear()
}
