package dev.antigravity.fluidengine.ai.bridge.assist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Il formato dell'assistenza (protocollo 3 del ponte), Kotlin puro: tutto cio' che passa fra l'app e PampAI. Come il
 * ponte dei tool: `ContentProvider.call(metodo, null, Bundle("json" → testo))`, risposta `Bundle("json" → testo)`; in
 * piu' due `IBinder` nei `Bundle` ([KeyCallback], il canale di ritorno dell'app, e [KeyToken], il gettone dell'ospite).
 * Chi legge e' tollerante (campi sconosciuti ignorati, un campo mancante prende il suo valore di serie).
 */
internal object AiAssistProtocol {
  const val PROTOCOL = 3
  const val CAP_ASSIST = "assist"
  const val CAP_STREAM = "assist_stream"
  val CAPABILITIES: Set<String> = setOf(CAP_ASSIST, CAP_STREAM)

  /** Il meta-data sul `<provider>` dell'ospite, col protocollo come valore. */
  const val META_ASSIST_HOST = "dev.antigravity.fluidengine.ai.ASSIST_HOST"

  /** Il suffisso dell'autorita': `<pacchetto>.ai.assist`. */
  const val AUTHORITY_SUFFIX = ".ai.assist"

  const val METHOD_NEGOTIATE = "negotiate"
  const val METHOD_SCOPE = "assist_scope"
  const val METHOD_ASK = "assist_ask"
  const val METHOD_POLL = "assist_poll"
  const val METHOD_CANCEL = "assist_cancel"

  const val KEY_JSON = "json"
  const val KEY_CALLBACK = "callback"
  const val KEY_TOKEN = "token"

  /** La transazione a senso unico del canale di ritorno, e il segno che la apre. */
  const val CODE_EVENTS = 1
  const val CALLBACK_MAGIC = 0x46_39_31_00 // "F91"

  const val TYPE_STATUS = "status"
  const val TYPE_TEXT = "text"
  const val TYPE_RECEIPT = "receipt"
  const val TYPE_DONE = "done"
  const val TYPE_FAILED = "failed"

  val json: Json = Json { ignoreUnknownKeys = true; isLenient = false }

  fun parse(text: String?): JsonObject? = text?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }

  // ── negoziazione ──────────────────────────────────────────────────────────────────────────────────────────

  fun negotiationRequest(): JsonObject = buildJsonObject {
    put("versions", buildJsonArray { add(JsonPrimitive(PROTOCOL)) })
    put("capabilities", buildJsonArray { CAPABILITIES.forEach { add(JsonPrimitive(it)) } })
  }

  /** La risposta dell'ospite: la 3 se il cliente la chiede, altrimenti l'errore di sempre. */
  fun negotiate(request: JsonObject?): JsonObject = buildJsonObject {
    val versions = (request?.get("versions") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }
    if (PROTOCOL !in versions) {
      put("error", "no common protocol")
    } else {
      put("protocol", PROTOCOL)
      val requested = (request?.get("capabilities") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
      put("capabilities", buildJsonArray { CAPABILITIES.intersect(requested).forEach { add(JsonPrimitive(it)) } })
    }
  }

  /** Il cliente legge la negoziazione: l'ospite parla l'assistenza? */
  fun agreed(response: JsonObject?): Boolean =
    response != null && response["error"] == null && response["protocol"].long() == PROTOCOL.toLong() &&
      CAP_ASSIST in (response["capabilities"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }

  // ── assist_scope ──────────────────────────────────────────────────────────────────────────────────────────

  fun scopeRequest(): JsonObject = buildJsonObject { put("protocol", PROTOCOL) }

  fun scopeResponse(tasks: Set<String>, tools: Boolean, page: Boolean, consent: AiAssistConsentState, ready: Boolean, reason: String?, app: AiAssistConsentState = AiAssistConsentState.Allowed): JsonObject = buildJsonObject {
    put("protocol", PROTOCOL)
    put("tasks", buildJsonArray { tasks.sorted().forEach { add(JsonPrimitive(it)) } })
    put("tools", tools)
    put("page", page)
    put("consent", consentId(consent))
    put("ready", ready)
    reason?.let { put("reason", it) }
    // F9.1b: il sì per usare PampAI da quest'app. Un ospite di F9.1 non lo manda: vale «gia' detto sì».
    put("app", consentId(app))
  }

  fun statusOf(response: JsonObject?, hostPackage: String?): AiAssistStatus {
    response ?: return AiAssistStatus(false, error = AiAssistError(AiAssistError.Failed), hostPackage = hostPackage)
    errorOf(response)?.let { return AiAssistStatus(false, error = it, hostPackage = hostPackage) }
    val ready = response["ready"].bool() ?: false
    val reason = response["reason"].str()
    return AiAssistStatus(
      ready = ready,
      tasks = (response["tasks"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.toSet(),
      tools = response["tools"].bool() ?: false,
      page = response["page"].bool() ?: false,
      pageConsent = consentOf(response["consent"].str()),
      error = if (ready) null else AiAssistError(AiAssistError.NotReady, reason = reason),
      hostPackage = hostPackage,
      appConsent = response["app"].str()?.let { consentOf(it) } ?: AiAssistConsentState.Allowed,
    )
  }

  // ── assist_ask ────────────────────────────────────────────────────────────────────────────────────────────

  fun askRequest(request: AiAssistRequest): JsonObject = buildJsonObject {
    put("protocol", PROTOCOL)
    put("task", buildJsonObject {
      put("kind", request.task.kind)
      put("text", request.task.text)
      request.task.style?.let { put("style", it) }
    })
    request.page?.let { put("page", pageJson(it)) }
    put("tools", request.tools)
    put("lang", request.language)
    put("timeoutMillis", request.timeoutMillis)
  }

  /** La pagina nel formato del filo: solo i quattro campi. */
  fun pageJson(page: AiAssistPage): JsonObject = buildJsonObject {
    put("title", page.title)
    page.kind?.let { put("kind", it) }
    put("lines", buildJsonArray { page.lines.forEach { add(JsonPrimitive(it)) } })
    page.selection?.let { put("selection", it) }
  }

  /** Rilegge la pagina e la rifa' passare dai tetti: un'app che manda di piu' non ottiene di piu'. */
  fun pageOf(json: JsonObject?): AiAssistPage? {
    json ?: return null
    val title = json["title"].str() ?: return null
    return AiAssistPage(
      title = title,
      lines = (json["lines"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content },
      selection = json["selection"].str(),
      kind = json["kind"].str(),
    )
  }

  /** La domanda letta dall'ospite; null se non si capisce. */
  class AskIn(val task: AiAssistTask, val page: AiAssistPage?, val pageSent: Boolean, val tools: String, val language: String, val timeoutMillis: Long)

  fun askOf(json: JsonObject?): AskIn? {
    json ?: return null
    val task = json["task"] as? JsonObject ?: return null
    val kind = task["kind"].str() ?: return null
    val text = task["text"].str() ?: return null
    val pageJson = json["page"] as? JsonObject
    return AskIn(
      task = AiAssistTask(kind, text, task["style"].str()),
      page = pageOf(pageJson),
      pageSent = pageJson != null,
      tools = json["tools"].str() ?: AiAssistRequest.ToolsCaller,
      language = json["lang"].str()?.take(16) ?: "it",
      timeoutMillis = json["timeoutMillis"].long() ?: AiAssistRequest.DefaultTimeoutMillis,
    )
  }

  fun askResponse(requestId: String): JsonObject = buildJsonObject { put("requestId", requestId) }

  fun requestIdOf(json: JsonObject?): String? = json?.get("requestId").str()

  // ── assist_poll / assist_cancel ───────────────────────────────────────────────────────────────────────────

  fun pollRequest(requestId: String, after: Long): JsonObject = buildJsonObject { put("requestId", requestId); put("after", after) }

  fun afterOf(json: JsonObject?): Long = json?.get("after").long() ?: 0L

  fun pollResponse(events: List<AiAssistWireEvent>, finished: Boolean): JsonObject = buildJsonObject {
    put("events", eventsJson(events))
    put("finished", finished)
  }

  fun cancelRequest(requestId: String): JsonObject = buildJsonObject { put("requestId", requestId) }

  fun cancelResponse(cancelled: Boolean): JsonObject = buildJsonObject { put("cancelled", cancelled) }

  // ── errori ────────────────────────────────────────────────────────────────────────────────────────────────

  fun errorResponse(error: AiAssistError): JsonObject = buildJsonObject {
    put("error", error.code)
    error.retryAfterMillis?.let { put("retryAfterMillis", it) }
    error.reason?.let { put("reason", it) }
    if (error.needs.isNotEmpty()) put("needs", buildJsonArray { error.needs.sorted().forEach { add(JsonPrimitive(it)) } })
  }

  /** Un errore dell'ospite; un codice sconosciuto (o un testo libero di un ospite dei tool) diventa [AiAssistError.Failed]. */
  fun errorOf(json: JsonObject?): AiAssistError? {
    val obj = json ?: return null
    val code = obj["error"].str() ?: return null
    return AiAssistError(
      code = code.takeIf { it in AiAssistError.Codes } ?: AiAssistError.Failed,
      retryAfterMillis = obj["retryAfterMillis"].long(),
      reason = obj["reason"].str()?.take(MaxReasonChars),
      needs = needsOf(obj["needs"] as? JsonArray),
    )
  }

  /** I sì che mancano: solo quelli conosciuti. */
  fun needsOf(array: JsonArray?): Set<String> =
    array.orEmpty().mapNotNull { (it as? JsonPrimitive)?.content }.filterTo(LinkedHashSet()) { it == AiAssistError.NeedsApp || it == AiAssistError.NeedsPage }

  // ── gli eventi ────────────────────────────────────────────────────────────────────────────────────────────

  fun eventsJson(events: List<AiAssistWireEvent>): JsonArray = buildJsonArray { events.forEach { add(it.toJson()) } }

  /** Un invio sul canale di ritorno. */
  fun batch(requestId: String, events: List<AiAssistWireEvent>): String = buildJsonObject {
    put("requestId", requestId)
    put("events", eventsJson(events))
  }.toString()

  class Batch(val requestId: String, val events: List<AiAssistWireEvent>)

  fun batchOf(text: String?): Batch? {
    val json = parse(text) ?: return null
    val id = json["requestId"].str() ?: return null
    return Batch(id, eventsOf(json["events"] as? JsonArray))
  }

  fun eventsOf(array: JsonArray?): List<AiAssistWireEvent> = array.orEmpty().mapNotNull { AiAssistWireEvent.of(it as? JsonObject) }

  const val MaxReasonChars = 160

  fun consentId(consent: AiAssistConsentState): String = when (consent) {
    AiAssistConsentState.NotAsked -> "not_asked"
    AiAssistConsentState.Allowed -> "allowed"
    AiAssistConsentState.Denied -> "denied"
  }

  fun consentOf(id: String?): AiAssistConsentState = when (id) {
    "allowed" -> AiAssistConsentState.Allowed
    "denied" -> AiAssistConsentState.Denied
    else -> AiAssistConsentState.NotAsked
  }

  internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

  internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toDoubleOrNull()?.toLong() }

  internal fun JsonElement?.bool(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
}

/**
 * Un evento sul filo, con il suo numero ([seq], da 1, senza buchi): `status`, `text`, `receipt`, `done`, `failed`.
 * L'app scarta i doppioni e, se ne manca uno, lo chiede con `assist_poll`.
 */
internal data class AiAssistWireEvent(
  val seq: Long,
  val type: String,
  val line: String? = null,
  val app: String? = null,
  val delta: String? = null,
  val outcome: String? = null,
  val text: String? = null,
  val code: String? = null,
  val retryAfterMillis: Long? = null,
  val reason: String? = null,
  val needs: Set<String> = emptySet(),
) {
  fun toJson(): JsonObject = buildJsonObject {
    put("seq", seq)
    put("type", type)
    line?.let { put("line", it) }
    app?.let { put("app", it) }
    delta?.let { put("delta", it) }
    outcome?.let { put("outcome", it) }
    text?.let { put("text", it) }
    code?.let { put("code", it) }
    retryAfterMillis?.let { put("retryAfterMillis", it) }
    reason?.let { put("reason", it) }
    if (needs.isNotEmpty()) put("needs", buildJsonArray { needs.sorted().forEach { add(JsonPrimitive(it)) } })
  }

  val terminal: Boolean get() = type == AiAssistProtocol.TYPE_DONE || type == AiAssistProtocol.TYPE_FAILED

  companion object {
    fun of(json: JsonObject?): AiAssistWireEvent? = with(AiAssistProtocol) {
      json ?: return null
      val seq = json["seq"].long() ?: return null
      val type = json["type"].str() ?: return null
      AiAssistWireEvent(
        seq = seq,
        type = type,
        line = json["line"].str(),
        app = json["app"].str(),
        delta = json["delta"].str(),
        outcome = json["outcome"].str(),
        text = json["text"].str(),
        code = json["code"].str(),
        retryAfterMillis = json["retryAfterMillis"].long(),
        reason = json["reason"].str(),
        needs = needsOf(json["needs"] as? JsonArray),
      )
    }
  }
}

/**
 * Mette insieme gli eventi in un [AiAssistProgress], nell'ordine di [AiAssistWireEvent.seq]: un doppione si scarta, un
 * evento che arriva dopo un buco aspetta ([missingAfter] dice da dove chiedere).
 */
internal class AiAssistAssembler {
  var progress: AiAssistProgress = AiAssistProgress()
    private set

  /** L'ultimo `seq` messo insieme. */
  var last: Long = 0L
    private set

  private val waiting = java.util.TreeMap<Long, AiAssistWireEvent>()

  /** Aggiunge [events]; vero se [progress] e' cambiato. */
  fun add(events: List<AiAssistWireEvent>): Boolean {
    var changed = false
    for (e in events) if (e.seq > last) waiting[e.seq] = e
    while (true) {
      val next = waiting.remove(last + 1) ?: break
      last = next.seq
      if (!progress.finished) { progress = apply(progress, next); changed = true }
    }
    return changed
  }

  /** C'e' un buco: gli eventi dopo [last] sono arrivati ma non quello subito dopo. */
  val missingAfter: Long? get() = if (waiting.isNotEmpty()) last else null

  /** Chiude con un errore del cliente (tempo scaduto, ospite morto), se non e' gia' finito. */
  fun fail(error: AiAssistError): Boolean {
    if (progress.finished) return false
    progress = progress.copy(error = error)
    return true
  }

  private fun apply(p: AiAssistProgress, e: AiAssistWireEvent): AiAssistProgress = when (e.type) {
    AiAssistProtocol.TYPE_STATUS -> p.copy(status = e.line?.let { AiAssistStatusLine(it, e.app) })
    AiAssistProtocol.TYPE_TEXT -> p.copy(text = p.text + (e.delta ?: ""))
    AiAssistProtocol.TYPE_RECEIPT -> e.line?.let { p.copy(receipts = p.receipts + AiAssistReceipt(it, e.app, e.outcome ?: AiAssistReceipt.OutcomeDone)) } ?: p
    AiAssistProtocol.TYPE_DONE -> p.copy(text = e.text ?: p.text, status = null, done = true)
    AiAssistProtocol.TYPE_FAILED -> p.copy(status = null, error = AiAssistError(
      code = e.code?.takeIf { it in AiAssistError.Codes } ?: AiAssistError.Failed,
      retryAfterMillis = e.retryAfterMillis,
      reason = e.reason?.take(AiAssistProtocol.MaxReasonChars),
      needs = e.needs,
    ))
    else -> p // un tipo futuro: si ignora
  }
}
