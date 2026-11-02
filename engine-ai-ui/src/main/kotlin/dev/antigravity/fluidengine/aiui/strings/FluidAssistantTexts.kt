package dev.antigravity.fluidengine.aiui.strings

import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.ProviderSwitch
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId

/**
 * Da stato, errore e cambio di servizio alle parole: l'unico posto in cui la UI dell'assistente
 * sceglie una frase. **Pura**, senza Compose e senza risorse: le quattro copie avevano la stessa
 * tabella in quattro posti (due con le risorse, due senza), e quello che si poteva provare sul
 * computer non lo era mai.
 *
 * Il dominio non c'e': le frasi dei gruppi di strumenti le mette l'app in
 * [FluidAssistantStrings.statusFor], e qui, per una chiave sconosciuta, vale la frase generica.
 */
object FluidAssistantTexts {

  /**
   * La frase di un gruppo di strumenti, o "penso" per i casi che l'engine conosce
   * (`thinking`, `more_tools`, `deep_model`). Sul livello profondo dice anche che lo e', tranne che
   * per il semplice "penso", che ha gia' la sua variante.
   */
  fun status(key: String, tier: ModelTier, strings: FluidAssistantStrings): String {
    val base = when (key) {
      "thinking" -> if (tier == ModelTier.DEEP) strings.thinkingDeep else strings.thinking
      "more_tools" -> strings.moreTools
      "deep_model" -> strings.deepModel
      else -> strings.statusFor(key, tier) ?: strings.thinking
    }
    return if (tier == ModelTier.DEEP && key != "thinking") base + strings.deepSuffix else base
  }

  /**
   * La frase di un fallimento. Per il limite di richieste nomina il servizio e ricorda la riserva
   * automatica: accesa (il default) un limite arriva qui solo quando sono al limite tutti, spenta
   * l'assistente aspetta e poi si arrende, e chi legge "riprova fra 40 s" deve sapere che
   * l'alternativa esiste. Se la riserva li ha gia' provati tutti ([tried] ne ha due o piu') il
   * consiglio sarebbe sbagliato, e nominarne uno solo pure.
   */
  fun failure(
    kind: FailureKind,
    strings: FluidAssistantStrings,
    retryAfterSec: Int? = null,
    provider: ProviderId? = null,
    tried: List<ProviderId> = emptyList(),
  ): String {
    val who = provider?.label ?: strings.theService
    val whoLower = provider?.label ?: strings.thisService
    return when (kind) {
      FailureKind.NO_KEYS -> strings.failNoKeys
      FailureKind.UNAUTHORIZED -> strings.failUnauthorized
      FailureKind.RATE_LIMITED -> when {
        tried.size >= 2 -> fluidFormat(strings.failRateLimitedAll, names(tried, strings), retryAfterSec?.let { "$it s" } ?: strings.soon)
        retryAfterSec != null -> fluidFormat(strings.failRateLimitedIn, who, retryAfterSec)
        else -> fluidFormat(strings.failRateLimited, who)
      }
      FailureKind.NETWORK -> strings.failNetwork
      FailureKind.TIMEOUT -> strings.failTimeout
      FailureKind.BLOCKED -> strings.failBlocked
      FailureKind.PROVIDER -> fluidFormat(strings.failProvider, who)
      FailureKind.MODEL_UNAVAILABLE -> fluidFormat(strings.failModelUnavailable, whoLower)
      FailureKind.CONTEXT_TOO_LONG -> fluidFormat(strings.failContextTooLong, whoLower)
      FailureKind.MICROPHONE -> strings.failMicrophone
      FailureKind.TRANSCRIPTION -> strings.failTranscription
      FailureKind.UNKNOWN -> strings.failUnknown
    }
  }

  /** Il fallimento in due o tre parole, per il titolo di una riga del foglio "Dettagli". */
  fun failureTitle(kind: FailureKind, strings: FluidAssistantStrings): String = when (kind) {
    FailureKind.NO_KEYS -> strings.failTitleNoKeys
    FailureKind.UNAUTHORIZED -> strings.failTitleUnauthorized
    FailureKind.RATE_LIMITED -> strings.failTitleRateLimited
    FailureKind.NETWORK -> strings.failTitleNetwork
    FailureKind.TIMEOUT -> strings.failTitleTimeout
    FailureKind.BLOCKED -> strings.failTitleBlocked
    FailureKind.PROVIDER -> strings.failTitleProvider
    FailureKind.MODEL_UNAVAILABLE -> strings.failTitleModelUnavailable
    FailureKind.CONTEXT_TOO_LONG -> strings.failTitleContextTooLong
    FailureKind.MICROPHONE -> strings.failTitleMicrophone
    FailureKind.TRANSCRIPTION -> strings.failTitleTranscription
    FailureKind.UNKNOWN -> strings.failTitleUnknown
  }

  /**
   * La riga di stato; null quando non c'e' niente da dire (a riposo, a risposta finita: lo stato
   * `Done` non ha una riga, ha la risposta). [provider] e' chi stava rispondendo, per nominarlo in un
   * fallimento per limite quando lo stato non lo porta.
   */
  fun statusLine(state: AssistantState, strings: FluidAssistantStrings, provider: ProviderId? = null): String? = when (state) {
    is AssistantState.Listening -> strings.listening
    AssistantState.Transcribing -> strings.transcribing
    is AssistantState.Classifying -> strings.classifying
    is AssistantState.Working -> {
      val base = status(state.statusKey, state.tier, strings)
      if (state.statusExtra > 0) fluidFormat(strings.parallel, base, state.statusExtra) else base
    }
    is AssistantState.WaitingRateLimit -> fluidFormat(strings.waiting, state.provider.label, state.secondsLeft)
    is AssistantState.SwitchingProvider -> state.reason
      ?.let { fluidFormat(strings.switchingWhy, state.from.label, strings.reasonText(it), state.to.label) }
      ?: fluidFormat(strings.switching, state.to.label)
    is AssistantState.Answering -> strings.writing
    is AssistantState.AwaitingConfirmation -> strings.needsConfirmation
    AssistantState.HeardNothing -> strings.heardNothing
    is AssistantState.Failed -> failure(state.kind, strings, state.retryAfterSec, state.provider ?: provider)
    is AssistantState.Cancelled -> strings.stopped
    is AssistantState.Done -> null
    AssistantState.Idle -> null
  }

  /** La riga della card ridotta a pillola: "Risposta pronta" a risposta finita, altrimenti lo stato. */
  fun collapsed(state: AssistantState, strings: FluidAssistantStrings): String = when (state) {
    is AssistantState.Done -> strings.answerReady
    else -> statusLine(state, strings) ?: strings.title
  }

  /** I servizi per cui una domanda e' passata, in ordine e senza doppioni, dai suoi cambi. */
  fun tried(switches: List<ProviderSwitch>): List<ProviderId> =
    if (switches.isEmpty()) emptyList() else (listOf(switches.first().from) + switches.map { it.to }).distinct()

  /** "Groq", "Groq e Gemini", "Groq, Gemini e OpenRouter". */
  fun names(providers: List<ProviderId>, strings: FluidAssistantStrings): String {
    val labels = providers.map { it.label }
    return if (labels.size <= 1) labels.joinToString() else labels.dropLast(1).joinToString(", ") + " ${strings.and} " + labels.last()
  }

  /**
   * La riga sotto una risposta arrivata da un servizio diverso da quello di partenza: "Ha risposto
   * Gemini: Groq era al limite". La riserva automatica e' accesa di default, e un cambio di servizio
   * che non si dice e' un altro modello che risponde al posto di quello scelto senza che si sappia.
   * Null senza cambi.
   */
  fun switchLine(switches: List<ProviderSwitch>, strings: FluidAssistantStrings): String? {
    val last = switches.lastOrNull() ?: return null
    val why = switches.distinctBy { it.from }.joinToString(", ") { "${it.from.label} ${strings.reasonText(it.reason)}" }
    return fluidFormat(strings.answeredBy, last.to.label, why)
  }

  /** Il testo che una card mostra come corpo: la risposta, o quello che e' arrivato finora; null se non c'e' niente. */
  fun bodyText(state: AssistantState): String? = when (state) {
    is AssistantState.Answering -> state.partial
    is AssistantState.Done -> state.answer
    is AssistantState.Failed -> state.partial
    is AssistantState.Cancelled -> state.partial
    else -> null
  }

  /** La domanda che uno stato porta con se', se la porta (in ascolto e in trascrizione non c'e'). */
  fun question(state: AssistantState): String? = when (state) {
    is AssistantState.Classifying -> state.question
    is AssistantState.Working -> state.question
    is AssistantState.WaitingRateLimit -> state.question
    is AssistantState.SwitchingProvider -> state.question
    is AssistantState.Answering -> state.question
    is AssistantState.AwaitingConfirmation -> state.question
    is AssistantState.Done -> state.question
    is AssistantState.Failed -> state.question
    is AssistantState.Cancelled -> state.question
    else -> null
  }

  /** Vero se lo stato merita la card espansa di default (una risposta, un errore, una conferma). */
  fun wantsExpanded(state: AssistantState): Boolean = when (state) {
    is AssistantState.Done, is AssistantState.Failed, is AssistantState.AwaitingConfirmation, is AssistantState.Answering -> true
    else -> false
  }
}
