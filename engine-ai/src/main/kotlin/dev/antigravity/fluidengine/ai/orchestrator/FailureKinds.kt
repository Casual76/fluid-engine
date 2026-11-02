package dev.antigravity.fluidengine.ai.orchestrator

import dev.antigravity.fluidengine.ai.net.AiError
import dev.antigravity.fluidengine.ai.net.BadReason

/**
 * Da un errore del provider alla ragione che si dice all'utente (3.0.0), per cio' che non passa dal
 * [FailoverPolicy]: la trascrizione di una domanda parlata. La domanda scritta ha la sua tabella
 * ([FailoverPolicy.decide]); la trascrizione no, e prima dava a ogni guasto la stessa faccia, "Non
 * sono riuscito a capire l'audio": su un treno senza campo l'audio era perfetto e il guasto era la
 * rete, ma la frase spingeva a parlare piu' forte e a ripetere. Cosi' le due strade dicono la stessa
 * cosa dello stesso guasto: chiave, limite, rete, tempo, servizio, contenuto bloccato, modello sparito.
 *
 * Quello che davvero non si capisce — una richiesta rifiutata, una risposta illeggibile, un'eccezione
 * che non conosciamo — vale [other]: per la trascrizione e' [FailureKind.TRANSCRIPTION], per chi non
 * dice niente [FailureKind.UNKNOWN]. Una domanda troppo lunga ([BadReason.CONTEXT_TOO_LONG]) non e' una
 * ragione per un audio: resta [other].
 *
 * Con [AssistantFailure.of] diventa il fallimento che la sessione scrive nello stato.
 */
object FailureKinds {

  fun of(error: Throwable, other: FailureKind = FailureKind.UNKNOWN): FailureKind = when (error) {
    is AiError.Unauthorized -> FailureKind.UNAUTHORIZED
    is AiError.RateLimited -> FailureKind.RATE_LIMITED
    is AiError.Timeout -> FailureKind.TIMEOUT
    is AiError.Network -> FailureKind.NETWORK
    is AiError.Server -> FailureKind.PROVIDER
    is AiError.BadRequest -> when {
      error.reason == BadReason.BLOCKED || error.message.orEmpty().startsWith(AiError.BLOCKED_PREFIX) -> FailureKind.BLOCKED
      error.reason == BadReason.MODEL_UNAVAILABLE -> FailureKind.MODEL_UNAVAILABLE
      else -> other
    }
    else -> other
  }
}
