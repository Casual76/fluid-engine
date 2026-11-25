package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.host.custody.DoorInfo
import dev.antigravity.fluidengine.account.host.custody.EpochKind
import dev.antigravity.fluidengine.account.host.custody.EpochUpload
import dev.antigravity.fluidengine.account.host.custody.FakeKeysServer
import dev.antigravity.fluidengine.account.host.custody.KeysView
import dev.antigravity.fluidengine.account.host.custody.TransferSeal
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

/**
 * Il server dell'account finto, dietro un vero socket HTTP (MockWebServer), per il client e la sessione dello Store:
 * il rinnovo con la **verifica vera** della prova del dispositivo (ES256 sulla JWK registrata, `rth`, `htu`), i token
 * delle app con la tabella del contratto, le uscite coi motivi, e le chiavi con le regole di [FakeKeysServer].
 *
 * Ogni richiesta resta in [recorded] (metodo, percorso, intestazioni e corpo) per il banco «niente segreti».
 */
class FakeAccountHttp(val keys: FakeKeysServer = FakeKeysServer()) : Dispatcher() {
  data class Recorded(val method: String, val path: String, val headers: String, val body: String)

  val recorded = mutableListOf<Recorded>()
  var accountId = keys.accountId
  val deviceId = "dev_" + java.util.UUID.randomUUID().toString().replace("-", "").take(26)
  private var refreshToken = PampaBase64.encode(PampaCrypto.random(32))
  private var deviceJwk: JsonObject? = null
  private var accessCounter = 0
  private val validAccess = mutableSetOf<String>()

  /** Il dispositivo è stato fatto uscire con questo motivo (`remote`, `lost`, `all`, `logout`): il rinnovo dà 401. */
  var revokedReason: String? = null
  /** Pacchetti scollegati dall'utente (`app_disconnected`). */
  val disconnected = mutableSetOf<String>()
  /** Il server finge di non esserci (503). */
  var down = false
  /** Quante volte si è rinnovato, e quante sono arrivati token d'app dal server. */
  var refreshes = 0
  var appTokens = 0

  /** La risposta d'ingresso che darebbe `login/verify`: la sessione nuova, con la pubblica del dispositivo. */
  fun login(deviceKey: JsonObject): JsonObject {
    deviceJwk = deviceKey
    refreshToken = PampaBase64.encode(PampaCrypto.random(32))
    return buildJsonObject {
      put("accountId", accountId)
      put("deviceId", deviceId)
      put("accessToken", newAccess())
      put("expiresIn", 900)
      put("refreshToken", refreshToken)
    }
  }

  private fun newAccess() = "at-${++accessCounter}".also { validAccess += it }

  /** Fa scadere i token d'accesso: il prossimo uso vuole un rinnovo. */
  fun expireAccess() = validAccess.clear()

  /** I backup caricati: pacchetto → numero → (epoca, byte). */
  val backups = mutableMapOf<String, MutableMap<Long, Pair<Int, ByteArray>>>()
  private val appTokenOwner = mutableMapOf<String, String>()

  override fun dispatch(request: RecordedRequest): MockResponse {
    val raw = request.body.readByteArray()
    val body = raw.decodeToString()
    val path = request.path ?: ""
    recorded += Recorded(request.method ?: "", path, request.headers.toString(), body)
    if (down) return error(503, "unavailable")
    val json = runCatching { Json.parseToJsonElement(body) as JsonObject }.getOrNull()
    return try {
      if (path.startsWith("/v1/backups")) backupsRoute(request, request.method ?: "", path, raw) else route(request, request.method ?: "", path, json)
    } catch (e: FakeKeysServer.Refused) {
      error(409, "conflict", e.reason)
    }
  }

  private fun route(request: RecordedRequest, method: String, path: String, json: JsonObject?): MockResponse {
    if (method == "POST" && path == "/v1/session/refresh") return refresh(request, json!!)
    val bearer = request.getHeader("authorization")?.removePrefix("Bearer ")
    if (bearer == null || bearer !in validAccess) return if (revokedReason != null) error(401, "session_revoked", revokedReason) else error(401, "unauthorized")
    val app = Regex("^/v1/apps/([^/]+)/token$").find(path)?.groupValues?.get(1)
    return when {
      app != null && method == "POST" -> appToken(app, json!!)
      method == "POST" && path == "/v1/session/logout" -> {
        revokedReason = "logout"
        validAccess.clear()
        ok(JsonObject(emptyMap()))
      }
      method == "GET" && path == "/v1/keys" -> ok(keys.view().toJson())
      method == "POST" && path == "/v1/recovery" -> {
        val door = (json!!["door"] as? JsonObject)?.let { parseDoor(it) }
        keys.postRecovery((json["auth"] as JsonPrimitive).content, door)
        ok(buildJsonObject { put("recovery", true) })
      }
      method == "PUT" && path.startsWith("/v1/keys/doors/") -> {
        keys.putDoor(parseDoor(json!!))
        ok(JsonObject(emptyMap()))
      }
      method == "POST" && path == "/v1/keys/epochs" -> {
        val j = json!!
        fun list(n: String, f: String) = (j[n] as? JsonArray)?.map { val o = it.jsonObject; o["doorId"]!!.jsonPrimitive.content to o[f]!!.jsonPrimitive.content } ?: emptyList()
        keys.postEpoch(EpochUpload(j["epoch"]!!.jsonPrimitive.int, EpochKind.of(j["kind"]!!.jsonPrimitive.content)!!, (j["link"] as? JsonPrimitive)?.content, list("seals", "sealed"), list("tags", "tag")))
        ok(buildJsonObject { put("epoch", j["epoch"]!!) })
      }
      method == "POST" && path == "/v1/keys/transfers" -> {
        if (json != null && json.isNotEmpty()) return error(400, "bad_request", "transfer_body")
        ok(buildJsonObject { put("transferId", keys.openTransfer()); put("expiresIn", 600) })
      }
      method == "GET" && path.matches(Regex("^/v1/keys/transfers/[^/]+/info$")) -> ok(buildJsonObject { put("deviceName", "Telefono nuovo"); put("createdAt", 1) })
      method == "POST" && path.matches(Regex("^/v1/keys/transfers/[^/]+/seal$")) -> {
        val id = path.split("/")[4]
        keys.sealTransfer(id, TransferSeal(json!!["sealed"]!!.jsonPrimitive.content, json["mac"]!!.jsonPrimitive.content))
        ok(JsonObject(emptyMap()))
      }
      method == "GET" && path.matches(Regex("^/v1/keys/transfers/[^/]+$")) -> {
        val seal = keys.takeTransfer(path.split("/")[4])
        ok(buildJsonObject { put("state", "taken"); put("sealed", seal.sealed); put("mac", seal.mac) })
      }
      else -> error(404, "not_found")
    }
  }

  private fun refresh(request: RecordedRequest, json: JsonObject): MockResponse {
    revokedReason?.let { return error(401, "session_revoked", it) }
    val given = json["refreshToken"]!!.jsonPrimitive.content
    if (given != refreshToken) return error(401, "unauthorized")
    val proof = request.getHeader("pampa-device-proof") ?: return error(401, "unauthorized", "no_proof")
    val parts = proof.split(".")
    val payload = Json.parseToJsonElement(PampaBase64.decode(parts[1]).decodeToString()).jsonObject
    if (payload["htm"]!!.jsonPrimitive.content != "POST" || !payload["htu"]!!.jsonPrimitive.content.endsWith("/v1/session/refresh")) return error(401, "unauthorized", "htu")
    if (payload["rth"]!!.jsonPrimitive.content != PampaBase64.encode(PampaCrypto.sha256(given.toByteArray()))) return error(401, "unauthorized", "rth")
    if (!verifyEs256(deviceJwk!!, "${parts[0]}.${parts[1]}".toByteArray(), PampaBase64.decode(parts[2]))) return error(401, "unauthorized", "proof")
    refreshes++
    return ok(buildJsonObject { put("accessToken", newAccess()); put("expiresIn", 900); put("kind", "full"); put("status", "active") })
  }

  private fun appToken(pkg: String, json: JsonObject): MockResponse {
    if (pkg in disconnected) return error(403, "app_disconnected")
    val audience = json["audience"]!!.jsonPrimitive.content
    val scopes = (json["scopes"] as JsonArray).map { it.jsonPrimitive.content }.toSet()
    if (!AppPolicy().allows(pkg, audience, scopes)) return error(403, "forbidden")
    appTokens++
    val token = "app-$pkg-$appTokens"
    appTokenOwner[token] = pkg
    return ok(buildJsonObject { put("token", token); put("expiresIn", 900) })
  }

  /** I backup (`bpi.md` § 4.11 bis): il pacchetto viene dal token, mai dal percorso; lo Store vede l'elenco di tutti. */
  private fun backupsRoute(request: RecordedRequest, method: String, path: String, raw: ByteArray): MockResponse {
    val pkg = request.getHeader("authorization")?.removePrefix("Bearer ")?.let { appTokenOwner[it] } ?: return error(401, "unauthorized")
    val n = path.removePrefix("/v1/backups").trim('/').toLongOrNull()
    return when {
      method == "GET" && n == null -> ok(buildJsonObject {
        put("backups", kotlinx.serialization.json.buildJsonArray {
          for ((p, m) in backups) if (pkg == AppPolicy.STORE || p == pkg) for ((k, v) in m) add(buildJsonObject {
            put("package", p); put("n", k); put("epoch", v.first); put("size", v.second.size); put("createdAt", 1)
          })
        })
      })
      method == "PUT" && n != null -> {
        val epoch = request.getHeader("pampa-backup-epoch")?.toIntOrNull() ?: return error(400, "bad_request")
        backups.getOrPut(pkg) { sortedMapOf() }[n] = epoch to raw
        ok(buildJsonObject { put("n", n) })
      }
      method == "GET" && n != null -> backups[pkg]?.get(n)?.let { MockResponse().setResponseCode(200).setBody(okio.Buffer().write(it.second)) } ?: error(404, "not_found")
      else -> error(404, "not_found")
    }
  }

  private fun parseDoor(o: JsonObject): DoorInfo =
    KeysView.parse(Json.parseToJsonElement("""{"epoch":0,"root":1,"reference":1,"epochs":[],"doors":[$o]}""") as JsonObject).doors.single()

  private fun ok(json: JsonObject) = MockResponse().setResponseCode(200).setHeader("content-type", "application/json").setBody(json.toString())

  private fun error(status: Int, code: String, reason: String? = null) = MockResponse().setResponseCode(status).setHeader("content-type", "application/json")
    .setBody(buildJsonObject { put("error", code); put("message", "errore $code"); if (reason != null) put("reason", reason) }.toString())

  companion object {
    /** Verifica una firma JWS ES256 (`r || s`) con la pubblica P-256 di una JWK. */
    fun verifyEs256(jwk: JsonObject, data: ByteArray, sig: ByteArray): Boolean {
      val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec("secp256r1")) }.getParameterSpec(ECParameterSpec::class.java)
      val x = BigInteger(1, PampaBase64.decode(jwk["x"]!!.jsonPrimitive.content))
      val y = BigInteger(1, PampaBase64.decode(jwk["y"]!!.jsonPrimitive.content))
      val pub = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params))
      val der = toDer(sig)
      return Signature.getInstance("SHA256withECDSA").run { initVerify(pub); update(data); verify(der) }
    }

    private fun toDer(sig: ByteArray): ByteArray {
      fun int(b: ByteArray): ByteArray {
        val v = BigInteger(1, b).toByteArray()
        return byteArrayOf(0x02, v.size.toByte()) + v
      }
      val body = int(sig.copyOfRange(0, 32)) + int(sig.copyOfRange(32, 64))
      return byteArrayOf(0x30, body.size.toByte()) + body
    }
  }
}
