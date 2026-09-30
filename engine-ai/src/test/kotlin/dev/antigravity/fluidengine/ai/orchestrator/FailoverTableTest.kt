package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason
import dev.antigravity.fluidengine.ai.provider.FinishReason
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.ReasoningLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La tabella dei 400 (2.8.0), riga per riga, fissata e non: prima ogni 400 che non fosse
 * "bloccato" era `Fail(PROVIDER)`, cioe' "il servizio ha risposto con un errore" e basta.
 */
class FailoverTableTest {

  private val policy = FailoverPolicy()
  private val others = listOf(ProviderId.GEMINI, ProviderId.OPENROUTER)
  private fun bad(reason: BadReason) = AiError.BadRequest(400, "no", reason)

  private fun decide(reason: BadReason, retries: Int = 0, trims: Int = 0, pinned: Boolean = false, remaining: List<ProviderId> = others, budget: Long = 80_000) =
    policy.decide(bad(reason), ProviderId.GROQ, remaining, 0, retries, budget, pinned = pinned, trimsDone = trims)

  private fun fail(kind: FailureKind) = FailoverDecision.Fail(kind, null)

  @Test
  fun `tool_use_failed riprova una volta, poi passa, e fissato fallisce`() {
    assertEquals(FailoverDecision.RetrySame, decide(BadReason.TOOL_USE_FAILED))
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decide(BadReason.TOOL_USE_FAILED, retries = 1))
    assertEquals(FailoverDecision.RetrySame, decide(BadReason.TOOL_USE_FAILED, pinned = true))
    assertEquals(fail(FailureKind.PROVIDER), decide(BadReason.TOOL_USE_FAILED, retries = 1, pinned = true))
    assertEquals(fail(FailureKind.PROVIDER), decide(BadReason.TOOL_USE_FAILED, retries = 1, remaining = emptyList()))
    // Senza tempo per una riprova si passa subito oltre.
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decide(BadReason.TOOL_USE_FAILED, budget = 10_000))
  }

  @Test
  fun `un modello sparito passa al prossimo, e fissato non cambia mai`() {
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decide(BadReason.MODEL_UNAVAILABLE))
    assertEquals(fail(FailureKind.MODEL_UNAVAILABLE), decide(BadReason.MODEL_UNAVAILABLE, pinned = true))
    assertEquals(fail(FailureKind.MODEL_UNAVAILABLE), decide(BadReason.MODEL_UNAVAILABLE, remaining = emptyList()))
  }

  @Test
  fun `una richiesta troppo lunga si accorcia una volta, poi passa o fallisce`() {
    assertEquals(FailoverDecision.TrimAndRetry, decide(BadReason.CONTEXT_TOO_LONG))
    assertEquals(FailoverDecision.TrimAndRetry, decide(BadReason.CONTEXT_TOO_LONG, pinned = true))
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decide(BadReason.CONTEXT_TOO_LONG, trims = 1))
    assertEquals(fail(FailureKind.CONTEXT_TOO_LONG), decide(BadReason.CONTEXT_TOO_LONG, trims = 1, pinned = true))
  }

  @Test
  fun `un 400 generico passa al prossimo, fissato e' PROVIDER, bloccato resta BLOCKED`() {
    assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), decide(BadReason.GENERIC))
    assertEquals(fail(FailureKind.PROVIDER), decide(BadReason.GENERIC, pinned = true))
    assertEquals(fail(FailureKind.BLOCKED), decide(BadReason.BLOCKED))
    assertEquals(fail(FailureKind.BLOCKED), decide(BadReason.BLOCKED, pinned = true))
    // La convenzione di prima: un messaggio "bloccato" vale anche con la ragione generica.
    val legacy = policy.decide(AiError.BadRequest(200, "bloccato: SAFETY", BadReason.GENERIC), ProviderId.GEMINI, others, 0, 0, 80_000)
    assertEquals(fail(FailureKind.BLOCKED), legacy)
  }

  @Test
  fun `una risposta vuota riprova una volta, poi passa, poi PROVIDER, e mai un successo`() {
    fun empty(finish: FinishReason, retries: Int, pinned: Boolean = false, remaining: List<ProviderId> = others) =
      policy.decideEmpty(finish, ProviderId.GROQ, remaining, retries, 80_000, pinned)
    for (finish in listOf(FinishReason.LENGTH, FinishReason.OTHER, FinishReason.STOP)) {
      assertEquals(FailoverDecision.RetrySame, empty(finish, 0))
      assertEquals(FailoverDecision.Switch(ProviderId.GEMINI), empty(finish, 1))
      assertEquals(fail(FailureKind.PROVIDER), empty(finish, 1, pinned = true))
      assertEquals(fail(FailureKind.PROVIDER), empty(finish, 1, remaining = emptyList()))
    }
    assertEquals(fail(FailureKind.BLOCKED), empty(FinishReason.BLOCKED, 0))
  }

  @Test
  fun `il timeout di lettura dipende da cosa si chiede, e si misura sul budget`() {
    val config = AiOrchestratorConfig()
    assertEquals(45_000, config.readTimeoutFor(ModelTier.CHAT, ReasoningLevel.NONE, 200_000))
    assertEquals(120_000, config.readTimeoutFor(ModelTier.CHAT, ReasoningLevel.LOW, 200_000))
    assertEquals(120_000, config.readTimeoutFor(ModelTier.DEEP, ReasoningLevel.NONE, 200_000))
    // Una card da novanta secondi: il silenzio lungo non sfora il budget...
    assertEquals(80_000, config.readTimeoutFor(ModelTier.DEEP, ReasoningLevel.HIGH, 80_000))
    // ...ma a budget quasi finito un giro aspetta comunque quanto uno normale.
    assertEquals(45_000, config.readTimeoutFor(ModelTier.DEEP, ReasoningLevel.HIGH, 5_000))
    assertEquals(45_000, config.readTimeoutFor(ModelTier.CHAT, ReasoningLevel.NONE, 0))
  }

  @Test
  fun `il perche' di un cambio si legge dall'errore`() {
    assertEquals(SwitchReason.TOOL_USE_FAILED, SwitchReason.of(bad(BadReason.TOOL_USE_FAILED)))
    assertEquals(SwitchReason.MODEL_UNAVAILABLE, SwitchReason.of(bad(BadReason.MODEL_UNAVAILABLE)))
    assertEquals(SwitchReason.CONTEXT_TOO_LONG, SwitchReason.of(bad(BadReason.CONTEXT_TOO_LONG)))
    assertEquals(SwitchReason.BAD_REQUEST, SwitchReason.of(bad(BadReason.GENERIC)))
    assertEquals(null, SwitchReason.of(bad(BadReason.BLOCKED)))
    assertEquals(SwitchReason.RATE_LIMITED, SwitchReason.of(AiError.RateLimited(1.0, dev.antigravity.fluidengine.ai.net.RateLimitInfo.EMPTY, message = "429")))
    assertEquals(SwitchReason.TIMEOUT, SwitchReason.of(AiError.Timeout("t")))
    assertEquals(null, SwitchReason.of(AiError.Unauthorized("k")))
  }
}
