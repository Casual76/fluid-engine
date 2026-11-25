package dev.antigravity.fluidengine.people.client

import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.PersonCard
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Una richiesta verso le rotte delle persone (`b\api.md` § 4.11 ter). Niente segreti nell'indirizzo (critica S14). */
class PeopleRequest(val method: String, val path: String, val body: JsonObject? = null, val token: String? = null)

/** La risposta: lo stato HTTP e il JSON, se c'è. */
class PeopleResponse(val status: Int, val json: JsonObject?)

/**
 * Il trasporto verso `account.pampa-apps.com`, sostituibile nelle prove (il server finto, MockWebServer). Un errore di
 * rete è [IOException]: per la coda in uscita vuol dire «offline, riprova», non «errore».
 */
fun interface PeopleHttp {
  suspend fun execute(request: PeopleRequest): PeopleResponse
}

/** Il token con cui si chiama: la sessione dello Store, o il token del pubblico `persone` (Store e Pampa Mail). */
fun interface PeopleAuth {
  suspend fun token(): String
}

/** Il trasporto di serie, con `HttpURLConnection` come il resto dell'account: niente librerie in più nello Store. */
class UrlPeopleHttp(private val baseUrl: String, private val storeVersion: String) : PeopleHttp {
  override suspend fun execute(request: PeopleRequest): PeopleResponse = withContext(Dispatchers.IO) {
    val connection = (URL(baseUrl.trimEnd('/') + request.path).openConnection() as HttpURLConnection).apply {
      requestMethod = request.method
      instanceFollowRedirects = false
      useCaches = false
      connectTimeout = 10_000
      readTimeout = 20_000
      setRequestProperty("accept", "application/json")
      setRequestProperty("pampa-store-version", storeVersion)
      request.token?.let { setRequestProperty("authorization", "Bearer $it") }
      if (request.body != null) {
        doOutput = true
        setRequestProperty("content-type", "application/json")
      }
    }
    try {
      request.body?.let { b -> connection.outputStream.use { it.write(b.toString().toByteArray()) } }
      val status = connection.responseCode
      val stream = if (status >= 400) connection.errorStream else connection.inputStream
      val text = stream?.use { it.readBytes().decodeToString() }.orEmpty()
      PeopleResponse(status, runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull())
    } finally {
      connection.disconnect()
    }
  }
}

/**
 * Un rifiuto del server, col codice del contratto (`errors.json`): `rate_limited` (429, con [retryAfterSeconds]),
 * `mailbox_full` (507), `gone` (410, la persona ha lasciato Pampa), `not_found`, `conflict`, `forbidden`… [accepted] è
 * quante buste della chiamata sono passate prima del primo rifiuto: sono sempre le prime.
 */
class PeopleServerException(
  val status: Int,
  val code: String,
  override val message: String,
  val reason: String? = null,
  val retryAfterSeconds: Long? = null,
  val accepted: Int? = null,
) : Exception(message) {
  /** Riprovare più tardi ha senso (troppe richieste, casella piena, servizio giù). */
  val retryable: Boolean get() = code in setOf("rate_limited", "mailbox_full", "unavailable", "internal") || status >= 500
}

/** Alta solo per ciò che diventa una notifica, normale per ciò che aggiorna in silenzio (`d\server-e-chiavi.md` § 6). */
enum class EnvelopePriority(val wire: String) { HIGH("high"), NORMAL("normal") }

/** Una busta già chiusa e firmata, pronta per la casella [to]. Il gettone o l'invito viaggiano accanto, in chiaro. */
class OutEnvelope(
  val to: String,
  val data: String,
  val token: String? = null,
  val invite: String? = null,
  val minStore: String = MIN_STORE,
  val priority: EnvelopePriority = EnvelopePriority.HIGH,
) {
  fun toJson(withTo: Boolean = false): JsonObject = buildJsonObject {
    if (withTo) put("to", to)
    token?.let { put("token", it) }
    invite?.let { put("invite", it) }
    put("data", data)
    put("minStore", minStore)
    put("priority", priority.wire)
  }

  companion object {
    /** La versione minima dello Store per aprire le buste di F10.6 (critica M2). */
    const val MIN_STORE = "1.0.0"

    fun fromJson(o: JsonObject): OutEnvelope = OutEnvelope(
      to = o.s("to")!!,
      data = o.s("data")!!,
      token = o.s("token"),
      invite = o.s("invite"),
      minStore = o.s("minStore") ?: MIN_STORE,
      priority = if (o.s("priority") == "normal") EnvelopePriority.NORMAL else EnvelopePriority.HIGH,
    )
  }
}

/** Una busta ritirata dalla casella: il numero, se è una richiesta, chi la manda (lo dice il server), i byte. */
class PickedEnvelope(
  val n: Long,
  val kind: String,
  val from: String,
  val data: String,
  val via: String?,
  val minStore: String,
  val priority: String,
  val at: Long,
)

class PickupPage(val envelopes: List<PickedEnvelope>, val more: Boolean, val last: Long)

/** Un record di uno spazio come lo rende il server: chi l'ha scritto non c'è, sta dentro, firmato. */
class SpaceRow(val recordId: String, val version: Long, val epoch: Int, val ciphertext: String, val deleted: Boolean, val seq: Long)

class SpacePage(val records: List<SpaceRow>, val last: Long, val more: Boolean)

/**
 * Le rotte delle persone (`b\api.md` § 4.11 ter; `Pampa-account\src\routes\people.ts`, `casella.ts`, `spazi.ts`), con le
 * forme del contratto. Il codice cercato viaggia nel corpo (`personCode`), mai nell'indirizzo.
 */
class PeopleApi(private val http: PeopleHttp, private val auth: PeopleAuth) {

  suspend fun call(method: String, path: String, body: JsonObject? = null): JsonObject {
    val r = http.execute(PeopleRequest(method, path, body, auth.token()))
    if (r.status in 200..299) return r.json ?: JsonObject(emptyMap())
    val j = r.json
    throw PeopleServerException(
      status = r.status,
      code = j?.s("error") ?: "unavailable",
      message = j?.s("message") ?: "Il servizio non è raggiungibile. Riprova più tardi.",
      reason = j?.s("reason"),
      retryAfterSeconds = (j?.get("retryAfter") as? JsonPrimitive)?.longOrNull,
      accepted = (j?.get("accepted") as? JsonPrimitive)?.intOrNull,
    )
  }

  // --- La scheda -------------------------------------------------------------------------------------------------

  suspend fun putCard(card: PersonCard, findable: Boolean? = null): JsonObject =
    call("PUT", "/v1/people/card", JsonObject(card.toJson() + (findable?.let { mapOf("findable" to JsonPrimitive(it)) } ?: emptyMap())))

  suspend fun setFindable(findable: Boolean): JsonObject = call("PATCH", "/v1/people/card", buildJsonObject { put("findable", findable) })

  /**
   * La scheda di [code], **controllata sul telefono**: codice e casella dalla radice, firme, e che sia proprio quel
   * codice. Un server che rende la scheda di un altro è [PeopleDetected]. Ogni codice nuovo conta nei 20 del giorno.
   */
  suspend fun lookup(code: String, invite: String? = null): PersonCard {
    val r = call("POST", "/v1/people/card/lookup", buildJsonObject {
      put("personCode", code)
      invite?.let { put("invite", it) }
    })
    return PersonCard.verify(r.s("card") ?: throw PeopleDetected("risposta senza scheda"), r.s("sig") ?: throw PeopleDetected("risposta senza firma"), expectCode = code)
  }

  // --- La casella --------------------------------------------------------------------------------------------------

  /** Buste per una casella (al massimo 45; una richiesta viaggia da sola). Restituisce quante ne ha accettate. */
  suspend fun send(to: String, envelopes: List<OutEnvelope>): Int {
    val r = call("POST", "/v1/casella/$to/buste", buildJsonObject { putJsonArray("envelopes") { envelopes.forEach { add(it.toJson()) } } })
    return (r["accepted"] as? JsonPrimitive)?.intOrNull ?: envelopes.size
  }

  /** Un gruppo: fino a 100 caselle, che il server consegna a pezzi da 45. [List] dei risultati, uno per busta. */
  suspend fun sendMany(envelopes: List<OutEnvelope>): List<String> {
    val r = call("POST", "/v1/casella/consegne", buildJsonObject { putJsonArray("envelopes") { envelopes.forEach { add(it.toJson(withTo = true)) } } })
    return (r["results"] as? JsonArray)?.map { (it as JsonPrimitive).content } ?: envelopes.map { "accepted" }
  }

  /** Il ritiro, per questo dispositivo: [after] conferma fin dove ha già preso. */
  suspend fun pickup(after: Long, limit: Int? = null): PickupPage {
    val r = call("GET", "/v1/casella/buste?after=$after" + (limit?.let { "&limit=$it" } ?: ""))
    val list = (r["envelopes"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
      PickedEnvelope(
        n = it.l("n"),
        kind = it.s("kind") ?: "envelope",
        from = it.s("from") ?: "",
        data = it.s("data") ?: "",
        via = it.s("via"),
        minStore = it.s("minStore") ?: OutEnvelope.MIN_STORE,
        priority = it.s("priority") ?: "high",
        at = it.l("at"),
      )
    }
    return PickupPage(list, (r["more"] as? JsonPrimitive)?.booleanOrNull == true, r.l("last"))
  }

  /** L'impronta di un gettone di consegna, di un bloccato o di un invito (`delivery`, `blocked`, `invite.qr|link|group`). */
  suspend fun addPrint(kind: String, print: String): JsonObject = call("POST", "/v1/casella/gettoni", buildJsonObject {
    put("kind", kind)
    put("print", print)
  })

  suspend fun removePrint(print: String) {
    try {
      call("DELETE", "/v1/casella/gettoni/$print")
    } catch (e: PeopleServerException) {
      if (e.code != "not_found") throw e
    }
  }

  // --- Gli spazi ---------------------------------------------------------------------------------------------------

  suspend fun createSpace(spaceId: String, kind: String, writePrint: String): JsonObject = call("POST", "/v1/spazi", buildJsonObject {
    put("spaceId", spaceId)
    put("kind", kind)
    put("epoch", 1)
    put("writePrint", writePrint)
  })

  /** Scrive un record sulla versione [baseVersion] (0 per uno nuovo); `conflict` se nel frattempo è cambiato. */
  suspend fun writeRecord(spaceId: String, writeToken: String, recordId: String, baseVersion: Long, epoch: Int, ciphertext: String, deleted: Boolean = false): Pair<Long, Long> {
    val r = call("POST", "/v1/spazi/$spaceId/record", buildJsonObject {
      put("writeToken", writeToken)
      put("recordId", recordId)
      put("baseVersion", baseVersion)
      put("epoch", epoch)
      put("ciphertext", ciphertext)
      if (deleted) put("deleted", true)
    })
    return r.l("version") to r.l("seq")
  }

  suspend fun readSpace(spaceId: String, since: Long): SpacePage {
    val r = call("GET", "/v1/spazi/$spaceId?since=$since")
    val rows = (r["records"] as? JsonArray).orEmpty().map { it.jsonObject }.map {
      SpaceRow(it.s("recordId")!!, it.l("version"), it.l("epoch").toInt(), it.s("ciphertext") ?: "", (it["deleted"] as? JsonPrimitive)?.booleanOrNull == true, it.l("seq"))
    }
    return SpacePage(rows, r.l("last"), (r["more"] as? JsonPrimitive)?.booleanOrNull == true)
  }

  suspend fun rotateSpace(spaceId: String, writeToken: String, epoch: Int, writePrint: String): JsonObject = call("POST", "/v1/spazi/$spaceId/epoca", buildJsonObject {
    put("writeToken", writeToken)
    put("epoch", epoch)
    put("writePrint", writePrint)
  })

  suspend fun deleteSpace(spaceId: String): JsonObject = call("DELETE", "/v1/spazi/$spaceId")
}

internal fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
internal fun JsonObject.l(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
