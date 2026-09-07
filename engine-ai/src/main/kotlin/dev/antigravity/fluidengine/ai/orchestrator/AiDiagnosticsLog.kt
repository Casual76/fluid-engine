package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.Usage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Una chiamata a un tool, com'e' andata. */
data class ToolTrace(val name: String, val millis: Long, val ok: Boolean, val chars: Int)

/** Un modello che ha risposto davvero in una domanda (1.29.0): quale provider, a quale livello, quale modello. */
data class ModelUse(val provider: ProviderId, val tier: ModelTier, val model: String)

/** Una domanda intera, per la pagina Diagnostica: chi ha risposto, con cosa, quanto e' costata. */
data class AiRequestLog(
  val startedAtMillis: Long,
  val question: String,
  val mode: AskMode,
  val provider: ProviderId,
  /** Il modello di chat con cui si e' partiti. */
  val model: String,
  val switchedTo: List<ProviderId>,
  val groups: List<String>,
  val classifierUsed: Boolean,
  val tools: List<ToolTrace>,
  val steps: Int,
  val usage: Usage?,
  val durationMillis: Long,
  val outcome: String,
  val error: String?,
  val rateLimit: RateLimitInfo?,
  val waitedSeconds: Int,
  /** Il livello piu' alto raggiunto e i modelli usati per ciascuno. */
  val tierReached: ModelTier = ModelTier.CHAT,
  val models: Map<ModelTier, String> = emptyMap(),
  /**
   * Tutti i modelli che hanno risposto, nell'ordine in cui l'hanno fatto (1.29.0), senza ripetere
   * un modello che risponde due giri di fila. [models] tiene una voce per livello, quindi un
   * cambio di provider sovrascrive il profondo di prima: "Gemini pro, poi il profondo di
   * OpenRouter" li' si legge come "il profondo di OpenRouter". Qui no.
   */
  val modelsUsed: List<ModelUse> = emptyList(),
)

/** Le ultime dieci domande, in memoria: nessun contenuto sopravvive alla chiusura dell'app. */
class AiDiagnosticsLog(private val capacity: Int = 10) {
  private val state = MutableStateFlow<List<AiRequestLog>>(emptyList())
  val entries: StateFlow<List<AiRequestLog>> = state

  /** Per provider: cio' che gli header hanno detto l'ultima volta sui limiti. */
  private val limits = MutableStateFlow<Map<ProviderId, RateLimitInfo>>(emptyMap())
  val rateLimits: StateFlow<Map<ProviderId, RateLimitInfo>> = limits

  fun add(log: AiRequestLog) {
    state.value = (listOf(log) + state.value).take(capacity)
  }

  fun rateLimit(provider: ProviderId, info: RateLimitInfo) {
    if (info == RateLimitInfo.EMPTY) return
    limits.value = limits.value + (provider to info)
  }

  fun clear() {
    state.value = emptyList()
  }
}
