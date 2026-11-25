package dev.antigravity.fluidengine.account.host.sync

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.PlacesStore
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.KeyCustody
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.ServerRequest
import dev.antigravity.fluidengine.account.host.server.ServerResponse
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Il server dell'account finto, in memoria, per le prove fra dispositivi di F10.8. Fa quello che fanno le rotte vere
 * (`Pampa-account\src\routes\choices.ts` e `sync.ts`), con le stesse regole che contano qui:
 * - le preferenze: per (dove, chiave) vince l'HLC più grande; una riga presa prende il numero d'ordine nuovo; un HLC
 *   oltre un giorno dall'ora del server si rifiuta (`bad_hlc`), una chiave o un valore sbagliati pure;
 * - le righe cifrate: una scrittura passa solo sulla sua `baseVersion`, altrimenti torna fra i conflitti; la stessa
 *   chiave d'idempotenza dà la stessa risposta; l'epoca dev'essere fra 1 e quella di oggi;
 * - i token delle app: `POST /v1/apps/<pacchetto>/token`.
 *
 * L'ora del server è [now]; ogni dispositivo ha la sua ora in [AccountServerClient] (la `Date` della risposta).
 * [offline] fa cadere la rete. Ogni corpo resta in [bodies], per controllare che al server non arrivi niente in chiaro.
 */
class FakeSharedServer {
  /** Quanto l'ora del server è spostata rispetto a quella vera: l'ora del server scorre, come quella di un server vero. */
  private var shift = 0L
  var now: Long
    get() = System.currentTimeMillis() + shift
    set(value) {
      shift = value - System.currentTimeMillis()
    }
  var offline = false
  var keyEpoch = 1
  val bodies = ArrayList<String>()

  private data class Pref(val value: JsonElement, val hlc: String, val seq: Long)
  private val prefs = LinkedHashMap<String, Pair<Pair<String, String>, Pref>>()

  class Row(val recordId: String, var version: Long, var seq: Long, var deleted: Boolean, var keyVersion: Int, var ciphertext: ByteArray?)
  /** Le righe di `places`, per raccolta e riga: il server cattivo delle prove le tocca da qui. */
  val rows = LinkedHashMap<String, Row>()

  private var seq = 0L
  private val idempotent = HashMap<String, String>()

  val http = AccountHttp { _, request -> handle(request) }

  private fun handle(r: ServerRequest): ServerResponse {
    if (offline) throw IOException("rete assente")
    val body = r.body?.decodeToString()
    if (body != null) bodies += body
    val path = r.path.substringBefore('?')
    val query = r.path.substringAfter('?', "").split('&').filter { it.contains('=') }.associate { it.substringBefore('=') to it.substringAfter('=') }
    val json = body?.let { Json.parseToJsonElement(it) as JsonObject }
    val key = r.headers["idempotency-key"]
    if (key != null) idempotent["${r.method} $path $key"]?.let { return ok(it) }
    val out: String = when {
      r.method == "POST" && path.startsWith("/v1/apps/") && path.endsWith("/token") ->
        buildJsonObject { put("token", "tok-" + path.removePrefix("/v1/apps/").removeSuffix("/token")); put("expiresIn", 900) }.toString()
      r.method == "GET" && path == "/v1/prefs" -> getPrefs(query["since"]?.toLong() ?: 0)
      r.method == "POST" && path == "/v1/prefs" -> postPrefs(json!!)
      r.method == "GET" && path == "/v1/sync/places" -> getRows(query["since"]?.toLong() ?: 0, query["limit"]?.toInt() ?: 500)
      r.method == "POST" && path == "/v1/sync/places" -> postRows(json!!) ?: return error(409, "conflict", "key_version")
      else -> return error(404, "not_found", null)
    }
    if (key != null) idempotent["${r.method} $path $key"] = out
    return ok(out)
  }

  private fun ok(text: String) = ServerResponse(200, text.toByteArray(), now)

  private fun error(status: Int, code: String, reason: String?) = ServerResponse(
    status,
    buildJsonObject { put("error", code); put("message", code); if (reason != null) put("reason", reason) }.toString().toByteArray(),
    now,
  )

  private fun getPrefs(since: Long): String = buildJsonObject {
    put("seq", seq)
    putJsonArray("accepted") {
      for (k in listOf("appearance.mode", "appearance.accent", "appearance.glass", "appearance.textScale", "appearance.contrast", "icon.set", "motion.reduced", "app.followGlobal")) {
        addJsonObject { put("key", k) }
      }
    }
    putJsonArray("prefs") {
      for ((id, p) in prefs.values.filter { it.second.seq > since }.sortedBy { it.second.seq }) addJsonObject {
        put("scope", id.first); put("key", id.second); put("value", p.value); put("hlc", p.hlc); put("seq", p.seq)
      }
    }
  }.toString()

  private val hlc = Regex("^(\\d{13})-(\\d{5})-([a-z0-9]{1,16})$")

  private fun postPrefs(body: JsonObject): String {
    val rejected = ArrayList<Pair<Int, String>>()
    val accepted = ArrayList<Triple<Pair<String, String>, JsonElement, String>>()
    (body["changes"] as JsonArray).forEachIndexed { i, c ->
      val o = c as JsonObject
      val scope = (o["scope"] as JsonPrimitive).content
      val k = (o["key"] as JsonPrimitive).content
      val h = (o["hlc"] as JsonPrimitive).content
      val v = o["value"] ?: JsonNull
      val problem = dev.antigravity.fluidengine.account.host.PrefsContract.check(k, scope, v)
      when {
        problem != null -> rejected += i to problem
        hlc.matchEntire(h)?.let { it.groupValues[1].toLong() <= now + 86_400_000L } != true -> rejected += i to "bad_hlc"
        else -> accepted += Triple(scope to k, v, h)
      }
    }
    if (accepted.isNotEmpty()) {
      seq++
      for ((id, v, h) in accepted) {
        val k = "${id.first}|${id.second}"
        val old = prefs[k]?.second
        if (old == null || h > old.hlc) prefs[k] = id to Pref(v, h, seq)
      }
    }
    return buildJsonObject {
      put("seq", seq)
      put("accepted", accepted.size)
      putJsonArray("rejected") { for ((i, why) in rejected) addJsonObject { put("index", i); put("reason", why) } }
    }.toString()
  }

  /** L'HLC della riga che il server tiene per (dove, chiave), o `null`. */
  fun prefHlc(scope: String, key: String): String? = prefs["$scope|$key"]?.second?.hlc

  /** Il valore che il server tiene per (dove, chiave), come testo, o `null`. */
  fun pref(scope: String, key: String): String? = (prefs["$scope|$key"]?.second?.value as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

  private fun view(r: Row) = buildJsonObject {
    put("recordId", r.recordId); put("version", r.version); put("deleted", r.deleted); put("keyVersion", r.keyVersion); put("seq", r.seq)
    r.ciphertext?.let { put("ciphertext", PampaBase64.encode(it)) }
  }

  private fun getRows(since: Long, limit: Int): String {
    val all = rows.values.filter { it.seq > since }.sortedWith(compareBy({ it.seq }, { it.recordId }))
    val page = all.take(limit)
    return buildJsonObject {
      putJsonArray("records") { for (r in page) add(view(r)) }
      if (all.size > limit) put("next", page.last().seq) else put("next", JsonNull)
    }.toString()
  }

  private fun postRows(body: JsonObject): String? {
    val changes = (body["changes"] as JsonArray).map { it as JsonObject }
    require(changes.size in 1..40) { "da 1 a 40 righe" }
    if (changes.any { ((it["keyVersion"] as JsonPrimitive).intOrNull ?: 0) !in 1..keyEpoch }) return null
    seq++
    val accepted = ArrayList<Pair<String, Long>>()
    val conflicts = ArrayList<Row>()
    for (c in changes) {
      val id = (c["recordId"] as JsonPrimitive).content
      val base = (c["baseVersion"] as JsonPrimitive).longOrNull!!
      val cur = rows[id]
      if ((cur?.version ?: 0L) != base) {
        conflicts += cur!!
        continue
      }
      val deleted = (c["deleted"] as? JsonPrimitive)?.booleanOrNull == true
      val bytes = (c["ciphertext"] as? JsonPrimitive)?.content?.let(PampaBase64::decode)
      rows[id] = Row(id, base + 1, seq, deleted, (c["keyVersion"] as JsonPrimitive).intOrNull!!, if (deleted) null else bytes)
      accepted += id to base + 1
    }
    return buildJsonObject {
      put("seq", seq)
      putJsonArray("accepted") { for ((id, v) in accepted) addJsonObject { put("recordId", id); put("version", v) } }
      putJsonArray("applied") {}
      putJsonArray("conflicts") { for (r in conflicts) add(view(r)) }
    }.toString()
  }

  /** Un dispositivo dell'account [accountId], con la sua ora ([clockSkewMillis] rispetto al server) e i suoi depositi. */
  inner class Device(
    val name: String,
    accountId: String,
    custody: KeyCustody?,
    val dir: File,
    clockSkewMillis: Long = 0,
    val storePackage: String = AppPolicy.STORE,
  ) {
    val vault = MemoryCustodyVault()
    val client = AccountServerClient("https://account.example", "1.10.0", http)
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val session = AccountSession(client, "https://account.example", SessionStore(vault, File(dir, "sessione")), SoftwareDeviceKey(), TokenCache({ 0L }), keyring, AppPolicy())
    val notified = ArrayList<List<String>>()
    val appearance = AppearanceStore(File(dir, "aspetto"), wall = { now + clockSkewMillis }) { notified += it }
    /** La cassaforte dei luoghi è sua: l'uscita cancella quella della custodia, non i luoghi. */
    val placesVault = MemoryCustodyVault()
    val places = PlacesStore(placesVault, File(dir, "luoghi"), AppPolicy()) { notified += it }
    val appearanceSync = AppearanceSync(client, session, appearance)
    val placesSync = PlacesSync(places, client, session, keyring, storePackage)

    init {
      session.adopt(buildJsonObject {
        put("accountId", accountId)
        put("deviceId", "dev_$name")
        put("refreshToken", "r-$name")
        put("accessToken", "a-$name")
        put("expiresIn", 900)
      }, "Alessio", storePackage)
      if (custody != null) keyring.saveCustody(custody, emptyList())
    }
  }
}
