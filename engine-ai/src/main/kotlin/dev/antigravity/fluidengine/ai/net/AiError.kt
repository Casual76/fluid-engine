package dev.antigravity.fluidengine.ai.net

/** Cosa gli header di un provider dicono sui limiti: tutto opzionale, Gemini non ne manda nessuno. */
data class RateLimitInfo(
  val limitRequests: Int? = null,
  val remainingRequests: Int? = null,
  val limitTokens: Int? = null,
  val remainingTokens: Int? = null,
  val resetRequestsSec: Double? = null,
  val resetTokensSec: Double? = null,
) {
  companion object {
    val EMPTY = RateLimitInfo()
  }
}

/**
 * Perche' un provider ha rifiutato una richiesta (2.8.0). Un 400 non e' una cosa sola: "il modello
 * ha scritto male una tool call" si risolve riprovando, "il modello non c'e' piu'" cambiando
 * modello, "non ci sta" accorciando la storia. Prima erano tutti "il servizio ha risposto con un
 * errore" e la domanda finiva li'.
 */
enum class BadReason {
  /** Un 4xx che non si sa leggere meglio: un parametro, uno schema, un formato. */
  GENERIC,

  /**
   * Groq `tool_use_failed`: il modello ha scritto una chiamata a un tool che il server non riesce a
   * leggere. E' un errore di campionamento, non della richiesta: un'altra estrazione di solito va.
   */
  TOOL_USE_FAILED,

  /** Il modello non c'e' piu' per questa chiave: ritirato, rinominato, senza endpoint. */
  MODEL_UNAVAILABLE,

  /** La richiesta non ci sta: il contesto del modello, o il tetto di token per richiesta di Groq (413). */
  CONTEXT_TOO_LONG,

  /** Il provider ha bloccato il contenuto (Gemini `blockReason`, la moderazione di OpenRouter). */
  BLOCKED,
}

/**
 * Gli errori di un provider IA, ridotti a quelli su cui l'orchestratore decide qualcosa: aspettare,
 * passare a un altro provider, riprovare, o dirlo all'utente. Il messaggio grezzo resta per la
 * diagnostica; la frase per l'utente la sceglie la UI in base al tipo.
 *
 * Dalla 2.8.0 ogni errore porta anche [httpCode] e [providerMessage], per un foglio "Dettagli" che
 * dica cosa ha risposto davvero il servizio: con solo "errore del servizio" non si capiva mai se
 * fosse il modello, la chiave o la richiesta.
 */
sealed class AiError(
  message: String,
  cause: Throwable? = null,
  /** Il codice HTTP della risposta, se una risposta c'e' stata (200: l'errore e' arrivato dentro uno stream gia' aperto). */
  val httpCode: Int? = null,
  /**
   * La frase del provider, corta (al massimo [AiErrorMapper.PROVIDER_MESSAGE_CHARS] caratteri) e
   * ripulita da qualunque cosa somigli a una chiave; null se l'errore non viene dal provider (rete,
   * timeout, un JSON illeggibile).
   */
  val providerMessage: String? = null,
) : Exception(message, cause) {
  /** 401/403, o il 400 di Gemini "API key not valid": una chiave sbagliata non si maschera con un ripiego. */
  class Unauthorized(
    message: String,
    httpCode: Int? = null,
    providerMessage: String? = null,
  ) : AiError(message, null, httpCode, providerMessage)

  class RateLimited(
    val retryAfterSec: Double?,
    val rateLimit: RateLimitInfo,
    /** Vero per il tetto giornaliero dei modelli gratuiti di OpenRouter: merita una frase sua. */
    val freeModelCap: Boolean = false,
    message: String,
    httpCode: Int? = 429,
    providerMessage: String? = AiErrorMapper.shorten(message),
  ) : AiError(message, null, httpCode, providerMessage)

  class Server(
    val code: Int,
    message: String,
    providerMessage: String? = AiErrorMapper.shorten(message),
  ) : AiError(message, null, code, providerMessage)

  /**
   * Un rifiuto della richiesta. [reason] dice quale (2.8.0); il default tiene in vita la
   * convenzione di prima, un messaggio che comincia con "bloccato" vale [BadReason.BLOCKED].
   */
  class BadRequest(
    val code: Int,
    message: String,
    val reason: BadReason = if (message.startsWith(BLOCKED_PREFIX)) BadReason.BLOCKED else BadReason.GENERIC,
    providerMessage: String? = AiErrorMapper.shorten(message),
  ) : AiError(message, null, code, providerMessage)

  class Network(message: String, cause: Throwable? = null) : AiError(message, cause)
  class Timeout(message: String, cause: Throwable? = null) : AiError(message, cause)

  /** Un 2xx che non si capisce: JSON rotto, struttura inattesa. */
  class Parse(message: String, cause: Throwable? = null) : AiError(message, cause)

  companion object {
    /** L'inizio del messaggio di un contenuto bloccato: la convenzione di prima della 2.8.0. */
    const val BLOCKED_PREFIX = "bloccato"
  }
}
