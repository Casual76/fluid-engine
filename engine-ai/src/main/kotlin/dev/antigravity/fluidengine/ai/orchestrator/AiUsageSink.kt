package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.Usage

/**
 * Una chiamata a un provider, com'e' andata: chi, con quale modello e livello, quanti token, cosa
 * dicevano gli header sui limiti, quanto ha aspettato, e l'errore se non ha risposto. Una per
 * turno — il router, ogni giro della chat, la riprova senza stream — non una per domanda:
 * un'app che tiene il conto dei consumi per modello li vuole separati.
 */
data class AiUsageEvent(
  val atMillis: Long,
  val conversationId: Long,
  val provider: ProviderId,
  val model: String,
  val tier: ModelTier,
  val usage: Usage?,
  val rateLimit: RateLimitInfo,
  val durationMillis: Long,
  val error: AiError? = null,
) {
  val rateLimited: Boolean get() = error is AiError.RateLimited
}

/**
 * Dove l'orchestratore e il router versano gli [AiUsageEvent] (1.26.0). L'engine non persiste
 * niente: [AiDiagnosticsLog] tiene le ultime dieci domande in memoria, e per un tracker dei
 * consumi che sopravviva alla chiusura dell'app serve un deposito dell'app. Viene chiamato sul
 * thread della domanda: chi scrive su disco fa partire il lavoro altrove.
 */
fun interface AiUsageSink {
  fun onTurn(event: AiUsageEvent)
}
