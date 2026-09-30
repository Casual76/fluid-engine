package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.ProviderId
import kotlin.math.ceil

/**
 * Quanto tempo resta a una domanda: [totalMillis] in tutto, poi si risponde con quello che si sa.
 * I default sono quelli di una card che aspetta a schermo; un'app che lavora in un service ne
 * passa di piu' larghi.
 *
 * Il budget si guarda **fra** un giro e l'altro (il giro forzato, le riprove, il tempo dei tool):
 * uno stream gia' partito non lo interrompe nessuno, lo ferma solo il suo timeout di lettura. Per
 * questo dalla 2.8.0 quel timeout si misura anche sul budget (`AiOrchestratorConfig.readTimeoutFor`).
 */
class TimeBudget(
  private val startMillis: Long,
  private val totalMillis: Long = TOTAL_MILLIS,
  private val finalReserveMillis: Long = FINAL_RESERVE_MILLIS,
  private val clock: () -> Long,
) {
  val remainingMillis: Long get() = (startMillis + totalMillis - clock()).coerceAtLeast(0)
  val elapsedMillis: Long get() = clock() - startMillis

  /** Vero quando conviene chiudere: l'ultimo giro deve avere il tempo di scrivere. */
  val forceFinal: Boolean get() = remainingMillis < finalReserveMillis

  companion object {
    const val TOTAL_MILLIS: Long = 90_000L
    const val FINAL_RESERVE_MILLIS: Long = 15_000L
  }
}

sealed interface FailoverDecision {
  data class Wait(val seconds: Int) : FailoverDecision
  data class Switch(val to: ProviderId) : FailoverDecision
  data object RetrySame : FailoverDecision
  data class Fail(val kind: FailureKind, val retryAfterSec: Int?) : FailoverDecision

  /**
   * La richiesta non ci sta (2.8.0): stesso provider, con la storia compattata a meta' budget e i
   * risultati dei tool piu' corti. Una volta sola; poi si cambia provider o si fallisce.
   */
  data object TrimAndRetry : FailoverDecision
}

/**
 * Cosa fare quando un provider fallisce, in una tabella: 429 -> il prossimo della lista, e se non
 * c'e' nessuno si aspetta il `retry-after` (al massimo due volte, mai piu' del budget); 5xx e
 * rete -> un secondo tentativo, poi il prossimo; chiave sbagliata -> ci si ferma e lo si dice,
 * perche' un ripiego silenzioso nasconderebbe l'errore di configurazione.
 *
 * Con [pinned] (1.29.0) la lista dei prossimi non esiste: e' la stessa tabella di quando si e'
 * soli — 429 -> attesa del `retry-after`, poi [FailureKind.RATE_LIMITED]; 5xx e rete -> una
 * riprova, poi il fallimento. Serve a chi ha scelto un servizio e vuole quello: un 429 sul
 * modello profondo di Gemini che porta la domanda sul profondo di OpenRouter non e' una riserva,
 * e' un altro modello che risponde al posto di quello scelto.
 *
 * Dalla 2.8.0 un 400 non e' piu' la fine della domanda: si legge il suo [BadReason].
 *
 * | perche'             | non fissato                         | fissato                         |
 * |---------------------|-------------------------------------|---------------------------------|
 * | `TOOL_USE_FAILED`   | riprova una volta, poi il prossimo  | riprova una volta, poi PROVIDER |
 * | `MODEL_UNAVAILABLE` | il prossimo                         | MODEL_UNAVAILABLE               |
 * | `CONTEXT_TOO_LONG`  | accorcia una volta, poi il prossimo | accorcia una volta, poi CONTEXT_TOO_LONG |
 * | `GENERIC`           | il prossimo                         | PROVIDER                        |
 * | `BLOCKED`           | BLOCKED                             | BLOCKED                         |
 *
 * "Il prossimo" che non c'e' vale come fissato. Un `tool_use_failed` e' un campionamento andato
 * male, non una richiesta sbagliata: la stessa domanda riprovata di solito passa. Un modello sparito
 * non torna riprovando, e un 400 generico nemmeno: se c'e' un altro servizio, tanto vale lui.
 */
class FailoverPolicy(
  private val maxWaits: Int = 2,
  private val maxWaitSec: Int = 60,
) {

  fun decide(
    error: Throwable,
    current: ProviderId,
    remaining: List<ProviderId>,
    waitsDone: Int,
    retriesDone: Int,
    budgetRemainingMillis: Long,
    /** Vero = non si cambia mai provider: si aspetta, si riprova, o si fallisce. */
    pinned: Boolean = false,
    /** Quante volte questa domanda ha gia' accorciato la storia su questo provider (2.8.0). */
    trimsDone: Int = 0,
  ): FailoverDecision {
    val next = if (pinned) null else remaining.firstOrNull { it != current }
    return when (error) {
      is AiError.RateLimited -> when {
        next != null -> FailoverDecision.Switch(next)
        waitsDone < maxWaits -> {
          val wait = ceil(error.retryAfterSec ?: DEFAULT_WAIT_SEC).toInt().coerceAtLeast(1)
          val allowed = minOf(maxWaitSec.toLong(), budgetRemainingMillis / 1000 - 10)
          if (wait <= allowed) FailoverDecision.Wait(wait) else FailoverDecision.Fail(FailureKind.RATE_LIMITED, wait)
        }
        else -> FailoverDecision.Fail(FailureKind.RATE_LIMITED, error.retryAfterSec?.let { ceil(it).toInt() })
      }
      is AiError.Server, is AiError.Timeout, is AiError.Network -> when {
        retriesDone < 1 && budgetRemainingMillis > RETRY_MIN_BUDGET_MILLIS -> FailoverDecision.RetrySame
        next != null -> FailoverDecision.Switch(next)
        else -> FailoverDecision.Fail(if (error is AiError.Timeout) FailureKind.TIMEOUT else if (error is AiError.Network) FailureKind.NETWORK else FailureKind.PROVIDER, null)
      }
      is AiError.Unauthorized -> FailoverDecision.Fail(FailureKind.UNAUTHORIZED, null)
      is AiError.BadRequest -> badRequest(error, next, retriesDone, budgetRemainingMillis, trimsDone)
      is AiError.Parse -> if (next != null) FailoverDecision.Switch(next) else FailoverDecision.Fail(FailureKind.PROVIDER, null)
      else -> FailoverDecision.Fail(FailureKind.UNKNOWN, null)
    }
  }

  private fun badRequest(error: AiError.BadRequest, next: ProviderId?, retriesDone: Int, budgetRemainingMillis: Long, trimsDone: Int): FailoverDecision {
    // La convenzione di prima: un messaggio "bloccato: ..." costruito a mano vale ancora.
    if (error.reason == BadReason.BLOCKED || error.message.orEmpty().startsWith(AiError.BLOCKED_PREFIX)) {
      return FailoverDecision.Fail(FailureKind.BLOCKED, null)
    }
    return when (error.reason) {
      BadReason.TOOL_USE_FAILED -> when {
        retriesDone < 1 && budgetRemainingMillis > RETRY_MIN_BUDGET_MILLIS -> FailoverDecision.RetrySame
        next != null -> FailoverDecision.Switch(next)
        else -> FailoverDecision.Fail(FailureKind.PROVIDER, null)
      }
      BadReason.MODEL_UNAVAILABLE -> if (next != null) FailoverDecision.Switch(next) else FailoverDecision.Fail(FailureKind.MODEL_UNAVAILABLE, null)
      BadReason.CONTEXT_TOO_LONG -> when {
        trimsDone < 1 -> FailoverDecision.TrimAndRetry
        next != null -> FailoverDecision.Switch(next)
        else -> FailoverDecision.Fail(FailureKind.CONTEXT_TOO_LONG, null)
      }
      BadReason.GENERIC, BadReason.BLOCKED -> if (next != null) FailoverDecision.Switch(next) else FailoverDecision.Fail(FailureKind.PROVIDER, null)
    }
  }

  /**
   * Una risposta finale vuota (2.8.0): un giro che chiude senza tool call e senza una parola. Prima
   * finiva salvata come risposta riuscita — una bolla vuota — tranne sul giro forzato. Adesso non
   * e' mai un successo: una riprova sullo stesso provider (quando la fine era `LENGTH`
   * l'orchestratore la fa con piu' token o meno ragionamento; `OTHER`, cioe' la chiamata malformata
   * di Gemini, e `STOP` identica), poi il prossimo, poi [FailureKind.PROVIDER]. Un contenuto
   * bloccato resta [FailureKind.BLOCKED].
   */
  fun decideEmpty(
    finish: FinishReason,
    current: ProviderId,
    remaining: List<ProviderId>,
    emptyRetriesDone: Int,
    budgetRemainingMillis: Long,
    pinned: Boolean = false,
  ): FailoverDecision {
    if (finish == FinishReason.BLOCKED) return FailoverDecision.Fail(FailureKind.BLOCKED, null)
    val next = if (pinned) null else remaining.firstOrNull { it != current }
    return when {
      emptyRetriesDone < 1 && budgetRemainingMillis > RETRY_MIN_BUDGET_MILLIS -> FailoverDecision.RetrySame
      next != null -> FailoverDecision.Switch(next)
      else -> FailoverDecision.Fail(FailureKind.PROVIDER, null)
    }
  }

  companion object {
    const val DEFAULT_WAIT_SEC = 5.0

    /** Sotto questo tempo rimasto una riprova non ha spazio per finire: si passa oltre. */
    const val RETRY_MIN_BUDGET_MILLIS = 20_000L
  }
}
