package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import java.util.UUID
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull

/** Atomic app-owned admission and durable settlement; no pricing or domain policy in the engine. */
interface ProviderAttemptLifecycle {
  suspend fun beforeAttempt(attempt: ProviderAttempt) {}
  suspend fun afterAttempt(settlement: ProviderSettlement) {}
  object None : ProviderAttemptLifecycle
}

/** Inherited by all chat calls in a coroutine, including internally constructed helper/router requests. */
class ProviderExecutionContext(
  val executionId: String,
  val purpose: String,
  val lifecycle: ProviderAttemptLifecycle,
  val beforeDispatch: suspend (ProviderDispatch) -> Unit = {},
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<ProviderExecutionContext>
}

/** Control signals deliberately cross existing cancellation barriers without failover or replay. */
class ProviderAdmissionDenied(val reason: String) : CancellationException(reason)
class ProviderObserverFailure(val attemptId: String, cause: Throwable) : CancellationException("Provider observer failed for $attemptId") {
  init { initCause(cause) }
}

data class ProviderAttempt(
  val attemptId: String,
  val executionId: String?,
  val purpose: String?,
  val dispatch: ProviderDispatch,
  val streaming: Boolean,
  val payloadBytes: Int,
  val maxOutputTokens: Int?,
  val toolCount: Int,
  val binaryInputBytes: Long,
  val webSearch: Boolean,
  val webSearchMaxResults: Int?,
  val documents: Boolean,
  val structuredOutput: Boolean,
  /** Encoded candidates, including server-side model fallbacks; no claim of remote model confirmation. */
  val modelCandidates: List<String> = listOf(dispatch.model),
  /** Unknown without a trustworthy model tokenizer/media bound. Payload bytes are not a token bound. */
  val inputTokenUpperBound: Int? = null,
)
enum class ProviderAttemptOutcome { SUCCESS, FAILED, CANCELLED, NOT_DISPATCHED }
enum class ProviderDispatchCertainty { NOT_DISPATCHED, MAY_HAVE_DISPATCHED, RESPONSE_RECEIVED }
data class ProviderSettlement(
  val attempt: ProviderAttempt,
  val outcome: ProviderAttemptOutcome,
  val certainty: ProviderDispatchCertainty,
  val usage: Usage?,
  val rateLimit: RateLimitInfo,
  val httpStatus: Int?,
  val retryAfterSec: Double?,
  val errorType: String?,
  val partialResponse: Boolean,
)

/** One instance per actual transport invocation. Never wraps retries as one reservation. */
internal class ProviderWireAttempt private constructor(
  private val attempt: ProviderAttempt,
  private val observers: List<ProviderAttemptLifecycle>,
) {
  var usage: Usage? = null
  var rateLimit = RateLimitInfo.EMPTY
  var httpStatus: Int? = null
  var partialResponse = false
  private var certainty = ProviderDispatchCertainty.NOT_DISPATCHED

  fun response(status: Int = 200) { certainty = ProviderDispatchCertainty.RESPONSE_RECEIVED; httpStatus = status }

  suspend fun <T> execute(block: suspend ProviderWireAttempt.() -> T): T {
    var failure: Throwable? = null
    try {
      currentCoroutineContext().ensureActive()
      certainty = ProviderDispatchCertainty.MAY_HAVE_DISPATCHED
      return block()
    } catch (t: Throwable) {
      failure = t
      if (t is AiError) {
        t.httpCode?.let { response(it) }
        if (t is AiError.RateLimited) rateLimit = t.rateLimit
      }
      throw t
    } finally {
      settle(when {
        certainty == ProviderDispatchCertainty.NOT_DISPATCHED -> ProviderAttemptOutcome.NOT_DISPATCHED
        failure is CancellationException -> ProviderAttemptOutcome.CANCELLED
        failure != null -> ProviderAttemptOutcome.FAILED
        else -> ProviderAttemptOutcome.SUCCESS
      }, failure)
    }
  }

  private suspend fun settle(outcome: ProviderAttemptOutcome, failure: Throwable?) {
    val settlement = ProviderSettlement(attempt, outcome, certainty, usage, rateLimit, httpStatus,
      (failure as? AiError.RateLimited)?.retryAfterSec, failure?.javaClass?.simpleName, partialResponse)
    var observerFailure: Throwable? = null
    withContext(NonCancellable) {
      observers.forEach { observer ->
        try { observer.afterAttempt(settlement) } catch (t: Throwable) {
          if (observerFailure == null) observerFailure = t else observerFailure!!.addSuppressed(t)
        }
      }
    }
    observerFailure?.let { throw ProviderObserverFailure(attempt.attemptId, it).also { signal -> failure?.let(signal::addSuppressed) } }
  }

  companion object {
    suspend fun begin(request: ChatRequest, dispatch: ProviderDispatch, payload: JsonObject, streaming: Boolean): ProviderWireAttempt {
      val context = currentCoroutineContext()[ProviderExecutionContext]
      val generation = payload["generationConfig"] as? JsonObject
      val binary = request.messages.filterIsInstance<Message.User>().sumOf { msg -> msg.parts.sumOf { part ->
        when (part) { is ContentPart.Image -> part.bytes.size.toLong(); is ContentPart.Document -> part.bytes.size.toLong(); else -> 0L }
      } }
      val webPlugin = (payload["plugins"] as? JsonArray)?.filterIsInstance<JsonObject>()
        ?.firstOrNull { it["id"]?.jsonPrimitive?.contentOrNull == "web" }
      val models = (payload["models"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
      val attempt = ProviderAttempt(UUID.randomUUID().toString(), context?.executionId, context?.purpose, dispatch,
        streaming, payload.toString().toByteArray(Charsets.UTF_8).size,
        (payload["max_completion_tokens"] ?: generation?.get("maxOutputTokens"))?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        request.tools.size, binary, request.webSearch, webPlugin?.get("max_results")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        request.hasDocuments, payload["response_format"] != null || generation?.get("responseMimeType") != null,
        (listOf(dispatch.model) + models).distinct())
      try {
        context?.beforeDispatch?.invoke(dispatch)
        request.beforeDispatch(dispatch)
      } catch (t: Throwable) {
        if (t is CancellationException) throw t
        throw ProviderObserverFailure(attempt.attemptId, t)
      }
      val admitted = mutableListOf<ProviderAttemptLifecycle>()
      val wire = ProviderWireAttempt(attempt, admitted)
      try {
        val inherited = context?.lifecycle
        listOfNotNull(inherited, request.lifecycle.takeUnless { it === inherited })
          .filter { it !== ProviderAttemptLifecycle.None }.forEach {
          currentCoroutineContext().ensureActive()
          it.beforeAttempt(attempt)
          admitted += it
        }
      } catch (t: Throwable) {
        wire.settle(ProviderAttemptOutcome.NOT_DISPATCHED, t)
        if (t is CancellationException) throw t
        throw ProviderObserverFailure(attempt.attemptId, t)
      }
      return wire
    }
  }
}
