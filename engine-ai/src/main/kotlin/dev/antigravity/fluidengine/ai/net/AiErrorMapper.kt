package dev.antigravity.fluidengine.ai.net

import dev.antigravity.fluidengine.ai.net.asArray
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject

/**
 * Da codice HTTP + header + corpo a [AiError]. Puro: si prova sul computer con i corpi veri dei
 * tre provider (fixture nei test).
 */
object AiErrorMapper {

  fun map(code: Int, rawHeaders: Map<String?, List<String>>, body: String, nowMillis: Long = System.currentTimeMillis()): AiError {
    val headers = normalize(rawHeaders)
    val json = runCatching { Json.parseToJsonElement(body) }.getOrNull()
    val message = errorMessage(json) ?: body.take(200).ifBlank { "HTTP $code" }
    val lower = message.lowercase()
    val details = providerDetails(json, message)
    return when {
      // La moderazione di OpenRouter risponde 403: e' un contenuto bloccato, non una chiave sbagliata.
      code == 403 && isModeration(json, lower) -> AiError.BadRequest(code, message, BadReason.BLOCKED, details)
      code == 401 || code == 403 -> AiError.Unauthorized(message, code, details)
      code == 400 && ("api key" in lower || "api_key" in lower) -> AiError.Unauthorized(message, code, details)
      code == 429 -> AiError.RateLimited(
        retryAfterSec = parseRetryAfter(headers, json, message, nowMillis),
        rateLimit = parseRateLimit(headers, nowMillis),
        freeModelCap = isFreeModelCap(json, message),
        message = message,
        httpCode = code,
        providerMessage = details,
      )
      code == 408 || code >= 500 -> AiError.Server(code, message, details)
      code >= 400 -> AiError.BadRequest(code, message, badReason(code, json, message), details)
      else -> AiError.Server(code, message, details)
    }
  }

  /**
   * Un errore arrivato **dentro** una risposta 200 (2.8.0): l'oggetto `error` di un pezzo di stream
   * (Groq manda cosi' il suo `tool_use_failed`, a stream gia' aperto) o di una risposta intera.
   * Quelli che si sanno leggere diventano un [AiError.BadRequest] con il loro [BadReason]; gli altri
   * restano un [AiError.Server] come prima, cioe' una riprova.
   */
  fun inBandError(error: JsonElement?, fallbackMessage: String): AiError {
    val message = error["message"].string()?.takeIf { it.isNotBlank() } ?: fallbackMessage
    val wrapped = buildJsonObject { error?.let { put("error", it) } }
    val numeric = error["code"].string()?.toIntOrNull()
    val details = providerDetails(wrapped, message)
    return when (val reason = badReason(numeric ?: 200, wrapped, message)) {
      BadReason.GENERIC -> AiError.Server(200, message, details)
      else -> AiError.BadRequest(numeric ?: 200, message, reason, details)
    }
  }

  /**
   * Il perche' di un 4xx, dal codice e dal corpo (2.8.0). I tre provider lo dicono in modi diversi e
   * nessuno lo promette, quindi si guarda tutto: `error.code` (Groq: una parola; Gemini e OpenRouter:
   * un numero), `error.type`, `error.status` (Gemini), il messaggio, e `error.metadata.raw` di
   * OpenRouter, dove finisce l'errore del fornitore a valle.
   */
  fun badReason(code: Int, json: JsonElement?, message: String): BadReason {
    val error = json["error"]
    val tags = listOfNotNull(error["code"].string(), error["type"].string(), error["status"].string()).map { it.lowercase() }
    val text = (message + " " + (error["metadata"]["raw"].string() ?: "")).lowercase()
    return when {
      "tool_use_failed" in tags || "tool_use_failed" in text || "failed to call a function" in text -> BadReason.TOOL_USE_FAILED
      code == 413 || "context_length_exceeded" in tags || CONTEXT_PHRASES.any { it in text } -> BadReason.CONTEXT_TOO_LONG
      tags.any { it in MODEL_TAGS } || (code == 404 && "model" in text) || MODEL_PHRASES.any { it in text } -> BadReason.MODEL_UNAVAILABLE
      else -> BadReason.GENERIC
    }
  }

  /**
   * La frase per un foglio "Dettagli": il codice o lo stato del provider se c'e' (`tool_use_failed`,
   * `NOT_FOUND`), il messaggio, e l'errore a valle di OpenRouter se dice qualcosa di piu'. Corta e
   * senza niente che somigli a una chiave.
   */
  fun providerDetails(json: JsonElement?, message: String): String? {
    val error = json["error"]
    val tag = error["code"].string()?.takeIf { it.isNotBlank() && it.toIntOrNull() == null }
      ?: error["status"].string()?.takeIf { it.isNotBlank() }
    val raw = error["metadata"]["raw"].string()?.takeIf { it.isNotBlank() && it !in message }
    val text = buildString {
      tag?.let { append(it).append(": ") }
      append(message)
      raw?.let { append(" (").append(it).append(')') }
    }
    return shorten(text)
  }

  /**
   * Un testo del provider pronto da mostrare: una riga sola, al massimo
   * [PROVIDER_MESSAGE_CHARS] caratteri, e senza chiavi. Un messaggio d'errore non dovrebbe mai
   * contenerne, ma qualche servizio ripete meta' della chiave sbagliata, e questo testo finisce in
   * uno screenshot.
   */
  fun shorten(text: String?): String? {
    val clean = text?.let { SECRET.replace(it, "***") }?.replace(WHITESPACE, " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return if (clean.length <= PROVIDER_MESSAGE_CHARS) clean else clean.take(PROVIDER_MESSAGE_CHARS - 1).trimEnd() + "…"
  }

  private fun isModeration(json: JsonElement?, lower: String): Boolean =
    "flagged" in lower || "moderation" in lower || json["error"]["metadata"]["reasons"] != null

  /** Un'eccezione di rete/IO nella sua categoria; le cancellazioni passano oltre intatte. */
  fun wrap(t: Throwable): Throwable = when (t) {
    is AiError -> t
    is CancellationException -> t
    is SocketTimeoutException -> AiError.Timeout("timeout", t)
    is UnknownHostException, is ConnectException, is SSLException -> AiError.Network(t.message ?: "rete", t)
    is IOException -> AiError.Network(t.message ?: "rete", t)
    else -> t
  }

  /** `error.message` e' dove Groq, Gemini e OpenRouter mettono tutti la frase; il resto e' rumore. */
  fun errorMessage(json: JsonElement?): String? =
    json["error"]["message"].string()?.takeIf { it.isNotBlank() }
      ?: json["message"].string()?.takeIf { it.isNotBlank() }

  fun normalize(rawHeaders: Map<String?, List<String>>): Map<String, String> =
    rawHeaders.entries
      .filter { it.key != null && it.value.isNotEmpty() }
      .associate { it.key!!.lowercase() to it.value.first() }

  /**
   * In ordine di fiducia: header `retry-after` (secondi o data HTTP), `RetryInfo.retryDelay` nei
   * dettagli di Gemini ("37s"), la frase di Groq "try again in 7.66s".
   */
  fun parseRetryAfter(
    headers: Map<String, String>,
    json: JsonElement?,
    message: String,
    nowMillis: Long = System.currentTimeMillis(),
  ): Double? {
    headers["retry-after"]?.trim()?.let { value ->
      value.toDoubleOrNull()?.let { return it.coerceAtLeast(0.0) }
      runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME) }.getOrNull()?.let { at ->
        return ((at.toInstant().toEpochMilli() - nowMillis) / 1000.0).coerceAtLeast(0.0)
      }
    }
    json["error"]["details"].asArray().forEach { detail ->
      if (detail["@type"].string()?.endsWith("RetryInfo") == true) {
        detail["retryDelay"].string()?.let { raw -> parseDuration(raw)?.let { return it } }
      }
    }
    GROQ_TRY_AGAIN.find(message)?.let { match -> return parseDuration(match.groupValues[1]) }
    return null
  }

  /** "1h2m3.5s", "7.66s", "250ms", "2m59.56s" -> secondi. Null se non e' una durata. */
  fun parseDuration(raw: String): Double? {
    val text = raw.trim().lowercase()
    if (text.isEmpty()) return null
    var total = 0.0
    var matched = false
    DURATION_PART.findAll(text).forEach { part ->
      matched = true
      val value = part.groupValues[1].toDouble()
      total += when (part.groupValues[2]) {
        "h" -> value * 3600
        "m" -> value * 60
        "s" -> value
        "ms" -> value / 1000
        else -> 0.0
      }
    }
    return if (matched) total else null
  }

  fun parseRateLimit(headers: Map<String, String>, nowMillis: Long = System.currentTimeMillis()): RateLimitInfo =
    RateLimitInfo(
      limitRequests = headers["x-ratelimit-limit-requests"]?.trim()?.toIntOrNull(),
      remainingRequests = headers["x-ratelimit-remaining-requests"]?.trim()?.toIntOrNull(),
      limitTokens = headers["x-ratelimit-limit-tokens"]?.trim()?.toIntOrNull(),
      remainingTokens = headers["x-ratelimit-remaining-tokens"]?.trim()?.toIntOrNull(),
      resetRequestsSec = headers["x-ratelimit-reset-requests"]?.let { parseResetSeconds(it, nowMillis) },
      resetTokensSec = headers["x-ratelimit-reset-tokens"]?.let { parseResetSeconds(it, nowMillis) },
    )

  /** Groq scrive durate ("2m59.56s"); OpenRouter un timestamp in millisecondi. */
  private fun parseResetSeconds(raw: String, nowMillis: Long): Double? {
    val text = raw.trim()
    text.toLongOrNull()?.let { number ->
      if (number > 1_000_000_000_000L) return ((number - nowMillis) / 1000.0).coerceAtLeast(0.0)
      return number.toDouble()
    }
    return parseDuration(text)
  }

  private fun isFreeModelCap(json: JsonElement?, message: String): Boolean {
    val lower = message.lowercase()
    if ("free" in lower && ("daily" in lower || "per day" in lower || "day" in lower)) return true
    val raw = json["error"]["metadata"]["raw"].string()?.lowercase() ?: return false
    return "free" in raw && "day" in raw
  }

  private val GROQ_TRY_AGAIN = Regex("try again in ([0-9hms.]+)", RegexOption.IGNORE_CASE)
  private val DURATION_PART = Regex("(\\d+(?:\\.\\d+)?)(ms|h|m|s)")

  /** Quanto e' lungo, al massimo, [AiError.providerMessage]. */
  const val PROVIDER_MESSAGE_CHARS = 300

  /** Le forme delle chiavi dei tre provider, e un header Authorization che finisse in un messaggio. */
  private val SECRET = Regex("(AIza[0-9A-Za-z_\\-]{8,}|gsk_[0-9A-Za-z]{8,}|sk-[0-9A-Za-z_\\-]{8,}|Bearer\\s+\\S+)")
  private val WHITESPACE = Regex("\\s+")

  /** Il contesto del modello, o il tetto per richiesta: le frasi di Groq, Gemini e OpenRouter. */
  private val CONTEXT_PHRASES = listOf(
    "context_length_exceeded", "maximum context length", "context length", "context window",
    "exceeds the maximum number of tokens", "input token count", "prompt is too long", "request too large",
  )

  /** Groq dice il perche' in `error.code`. */
  private val MODEL_TAGS = setOf("model_decommissioned", "model_not_found")

  /** Gemini e OpenRouter lo dicono solo a parole. */
  private val MODEL_PHRASES = listOf(
    "is not found for api version", "not supported for generatecontent", "no endpoints found",
    "is not a valid model id", "has been decommissioned", "does not exist or you do not have access",
  )
}
