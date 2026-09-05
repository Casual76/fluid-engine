package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.Usage

/** Quanto forte si sta sentendo, e se in questo istante e' parlato: la materia dell'aureola. */
data class MicLevel(val level: Float = 0f, val speaking: Boolean = false)

/** Come e' arrivata la domanda: decide se la risposta va anche letta ad alta voce. */
enum class AskMode { VOICE, TEXT }

/**
 * Un chip sotto la risposta: il modello scrive `[[id]]` o `[[id:valore]]`, la card lo rende
 * toccabile. L'engine non sa cosa siano gli id: l'app li dichiara al modello nel prompt e li
 * filtra con `AskInput.chipFilter`, poi li traduce in una pagina, un posto, un dettaglio.
 */
data class AnswerChip(val id: String, val value: String? = null)

/** Perche' e' andata male, in termini che la UI sa tradurre in una frase. */
enum class FailureKind { NO_KEYS, UNAUTHORIZED, RATE_LIMITED, NETWORK, TIMEOUT, BLOCKED, PROVIDER, MICROPHONE, TRANSCRIPTION, UNKNOWN }

/**
 * Un'azione che aspetta il si' dell'utente: solo le parole con cui mostrarla. L'oggetto vero
 * dell'azione lo tiene l'app, che e' l'unica a sapere cosa sia.
 */
data class PendingConfirmation(val id: Long, val title: String, val detail: String?)

/**
 * Lo stato dell'assistente, uno solo per volta: la UI lo osserva e disegna aureola, card e
 * stati leggibili. Le stringhe non stanno qui (le chiavi si', le parole le mette la UI).
 */
sealed interface AssistantState {
  data object Idle : AssistantState

  /**
   * In ascolto. Il livello del microfono **non** sta qui: cambiava cinquanta volte al secondo e
   * ricomponeva tutto l'overlay a ogni frame audio (facendo ripartire, fra l'altro, gli effetti che
   * fermavano la voce di sistema). Sta in un flusso a parte, che legge solo l'aureola.
   */
  data class Listening(val elapsedMillis: Long) : AssistantState

  data object Transcribing : AssistantState

  /** Nessun parlato: la card lo dice e si chiude da sola. */
  data object HeardNothing : AssistantState

  data class Classifying(val question: String, val provider: ProviderId) : AssistantState

  /**
   * Il giro dei tool. [statusKey] e' la chiave del testo di stato: lo `statusKey` di un gruppo,
   * "thinking", "more_tools"; [statusExtra] quante chiamate in parallelo; [tier] con quale
   * livello di modello si sta lavorando (la UI puo' dire "puo' volerci un po'" sul profondo).
   */
  data class Working(
    val question: String,
    val step: Int,
    val maxSteps: Int,
    val statusKey: String,
    val statusExtra: Int,
    val provider: ProviderId,
    val tier: ModelTier = ModelTier.CHAT,
  ) : AssistantState

  data class WaitingRateLimit(val question: String, val provider: ProviderId, val secondsLeft: Int) : AssistantState

  data class SwitchingProvider(val question: String, val from: ProviderId, val to: ProviderId) : AssistantState

  data class Answering(val question: String, val partial: String, val provider: ProviderId, val tier: ModelTier = ModelTier.CHAT) : AssistantState

  /** Un'azione con conferma: la card mostra i chip Conferma/Annulla finche' l'utente non sceglie. */
  data class AwaitingConfirmation(val question: String, val confirmation: PendingConfirmation, val provider: ProviderId) : AssistantState

  data class Done(
    val question: String,
    val answer: String,
    val chips: List<AnswerChip>,
    val provider: ProviderId,
    val mode: AskMode,
    val usage: Usage?,
    val toolsUsed: List<String>,
    val durationMillis: Long,
    val tierReached: ModelTier = ModelTier.CHAT,
  ) : AssistantState

  data class Failed(val question: String?, val kind: FailureKind, val error: AiError?, val retryAfterSec: Int?, val partial: String?) : AssistantState

  data class Cancelled(val question: String?, val partial: String?) : AssistantState

  val isBusy: Boolean
    get() = when (this) {
      is Listening, Transcribing, is Classifying, is Working, is WaitingRateLimit, is SwitchingProvider, is Answering, is AwaitingConfirmation -> true
      else -> false
    }
}
