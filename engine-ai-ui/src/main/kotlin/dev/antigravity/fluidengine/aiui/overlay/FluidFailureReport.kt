package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.ProviderSwitch
import dev.antigravity.fluidengine.ai.orchestrator.SwitchReason
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.aiui.strings.fluidFormat

/**
 * Cosa si sa di una risposta finita male, per il foglio "Dettagli": chi, con quale modello, perche',
 * con che codice e che cosa ha detto il servizio, e per quali servizi e' passata prima.
 *
 * "Il servizio ha risposto con un errore" da solo non dice a nessuno cosa fare; il codice e il
 * messaggio del servizio si' — a chi li va a cercare, e a chi li deve incollare in una segnalazione.
 * Era di PampAI, ma niente in lui e' di dominio: l'app che ha una riga del run su disco la porta qui
 * con il suo costruttore, chi no la ricava dallo stato `Failed` ([of]).
 */
@Immutable
data class FluidFailureReport(
  val kind: FailureKind?,
  val provider: ProviderId?,
  val model: String? = null,
  val reason: SwitchReason? = null,
  val httpCode: Int? = null,
  val providerMessage: String? = null,
  val switches: List<ProviderSwitch> = emptyList(),
  val technical: String? = null,
) {
  /** La frase intera, la stessa che la risposta mostra. */
  fun summary(strings: FluidAssistantStrings): String =
    kind?.let { FluidAssistantTexts.failure(it, strings, provider = provider, tried = FluidAssistantTexts.tried(switches)) } ?: strings.failUnknown

  /** "Groq era al limite", se il servizio ha detto perche'. */
  fun reasonLine(strings: FluidAssistantStrings): String? =
    reason?.let { r -> "${provider?.label ?: strings.theService} ${strings.reasonText(r)}" }

  /** "Groq → Gemini → OpenRouter": i servizi per cui la domanda e' passata, se piu' d'uno. */
  val chain: String?
    get() {
      if (switches.isEmpty()) return null
      val hops = (listOf(switches.first().from) + switches.map { it.to }).fold(mutableListOf<ProviderId>()) { acc, p -> if (acc.lastOrNull() != p) acc += p; acc }
      return hops.joinToString(" → ") { it.label }
    }

  /** Il perche' di ogni passaggio di mano, in una riga. */
  fun chainReasons(strings: FluidAssistantStrings): String? =
    switches.takeIf { it.isNotEmpty() }?.distinctBy { it.from }?.joinToString(" · ") { "${it.from.label} ${strings.reasonText(it.reason)}" }

  /** Il testo che "Copia" mette negli appunti: tutto, in righe semplici, pronto da incollare. */
  fun asText(strings: FluidAssistantStrings, header: String? = null): String = buildList {
    add(header ?: strings.failureCopyHeader)
    add(summary(strings))
    provider?.let { add(fluidFormat(strings.failureCopyService, it.label + (model?.let { m -> " ($m)" } ?: ""))) }
    kind?.let { add(fluidFormat(strings.failureCopyReason, FluidAssistantTexts.failureTitle(it, strings) + (reasonLine(strings)?.let { r -> " — $r" } ?: ""))) }
    httpCode?.let { add(fluidFormat(strings.failureCopyHttp, it)) }
    providerMessage?.takeIf { it.isNotBlank() }?.let { add(fluidFormat(strings.failureCopyAnswer, it)) }
    chain?.let { add(fluidFormat(strings.failureCopyTried, it + (chainReasons(strings)?.let { r -> " ($r)" } ?: ""))) }
    technical?.takeIf { it.isNotBlank() && it != providerMessage }?.let { add(fluidFormat(strings.failureCopyTechnical, it)) }
  }.joinToString("\n")

  companion object {
    /** Il resoconto dello stato `Failed`: quello che l'engine sa subito, prima di qualunque riga su disco. */
    fun of(failed: AssistantState.Failed, switches: List<ProviderSwitch> = emptyList()): FluidFailureReport =
      FluidFailureReport(
        kind = failed.kind,
        provider = failed.provider,
        reason = failed.reason,
        httpCode = failed.error?.httpCode,
        providerMessage = failed.error?.providerMessage,
        switches = switches,
        technical = failed.error?.message,
      )
  }
}
