package dev.antigravity.fluidengine.aiui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.OpenRouterDataPolicy
import dev.antigravity.fluidengine.ai.provider.OpenRouterKeyInfo
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * Cosa si sa della chiave di OpenRouter, in due righe sotto la sua scheda: i crediti (e l'uso di
 * oggi), il modello scelto e se e' gratuito, e — FluidWeather — quanti gratuiti si sono usati oggi
 * contro il tetto. Senza `keyInfo` dice di verificare la chiave per leggerli.
 *
 * Pura nella sostanza: [fluidOpenRouterLines] ritorna le righe e basta, e si prova.
 */
@Composable
fun FluidOpenRouterKeyDetails(
  settings: AiSettings,
  catalogue: ModelCatalogue?,
  info: OpenRouterKeyInfo?,
  modifier: Modifier = Modifier,
  quota: FluidOpenRouterQuota? = null,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
) {
  Column(modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
    fluidOpenRouterLines(settings, catalogue, info, quota, strings).forEach { line ->
      Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Le righe dei dettagli di OpenRouter: crediti, modello scelto, gratuiti di oggi. */
fun fluidOpenRouterLines(
  settings: AiSettings,
  catalogue: ModelCatalogue?,
  info: OpenRouterKeyInfo?,
  quota: FluidOpenRouterQuota?,
  strings: FluidAiSettingsStrings,
  locale: Locale = Locale.getDefault(),
): List<String> = buildList {
  add(
    info?.let {
      val left = it.limitRemainingUsd ?: it.limitUsd
      val today = it.usageDailyUsd ?: 0.0
      fluidFormat(
        strings.orCredits,
        left?.let { v -> String.format(locale, "%.2f $", v) } ?: strings.orUnlimited,
        String.format(locale, "%.3f $", today),
      ) + " · " + (if (it.isFreeTier) strings.orFreeTier else strings.orPaidTier)
    } ?: strings.orCreditsUnknown,
  )
  val chosen = settings.chatModel(ProviderId.OPENROUTER)
  val model = chosen?.let { catalogue?.chat(it) }
  if (chosen != null) {
    // Un modello che non e' nel catalogo (ancora non scaricato) si presume gratuito: e' la scelta
    // che l'engine fa da solo, e dire "a pagamento" senza saperlo spaventerebbe per niente.
    add(fluidFormat(if (model?.free == true || model == null) strings.orChosenFree else strings.orChosenPaid, chosen))
  }
  if (quota != null && model?.free == true) add(fluidFormat(strings.orFreeToday, quota.used, quota.cap))
}

/**
 * La scelta di OpenRouter sull'addestramento: "come sul mio account" (di serie) o "non usarli". La
 * politica sui dati e' dell'account, non dell'app: chiedere `deny` a ogni richiesta non rende niente
 * piu' sicuro — sovrascrive con la piu' stretta la scelta gia' fatta su openrouter.ai — e sui modelli
 * i cui endpoint gratuiti registrano i prompt non ne lascia nessuno disponibile ("No endpoints found
 * matching your data policy"). Per questo la frase dice cosa costa.
 */
@Composable
fun FluidOpenRouterDataPolicyRow(
  policy: OpenRouterDataPolicy,
  onPolicy: (OpenRouterDataPolicy) -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
) {
  val strict = policy == OpenRouterDataPolicy.DENY
  FluidChoiceRowWithChips(
    title = strings.orPolicyTitle,
    subtitle = if (strict) strings.orPolicyDenyDetail else strings.orPolicyAccountDetail,
    modifier = modifier,
  ) {
    FluidChip(label = strings.orPolicyAccount, selected = !strict, onClick = { onPolicy(OpenRouterDataPolicy.ACCOUNT) })
    FluidChip(label = strings.orPolicyDeny, selected = strict, onClick = { onPolicy(OpenRouterDataPolicy.DENY) })
  }
}

/**
 * Una riga di lista la cui impostazione e' una scelta fra chip.
 *
 * I chip **non** stanno nel `badge`: quello slot si prende la larghezza che gli serve e lascia al
 * testo quella che avanza, cosi' con tre o quattro voci il titolo finisce spezzato una lettera per
 * riga ("Ricono / scimen / to") e il sottotitolo una parola per riga. Vanno sotto, a tutta
 * larghezza, e scorrono in orizzontale quando non ci stanno.
 */
@Composable
fun FluidChoiceRowWithChips(
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier,
  chips: @Composable () -> Unit,
) {
  Column(modifier) {
    FluidListRow(title = title, subtitle = subtitle)
    Row(
      Modifier
        .horizontalScroll(rememberScrollState())
        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      chips()
    }
  }
}

/**
 * Una domanda in una riga: chi ha risposto (in ordine, con il cambio di servizio: "Gemini gemini-2.5-flash →
 * OpenRouter minimax-m2.5" dice anche il passaggio, che la mappa per livello nasconde), quanti
 * passi e quanto ci ha messo, e sotto — se ci sono — i gruppi, gli strumenti coi tempi, i token e il
 * costo, l'attesa, l'errore.
 */
@Composable
fun FluidRecentRequestRow(log: AiRequestLog, modifier: Modifier = Modifier, strings: FluidAiSettingsStrings = FluidAiSettingsStrings()) {
  FluidListRow(
    title = log.question.take(80),
    subtitle = fluidRequestDetails(log, strings),
    meta = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(log.startedAtMillis)),
    modifier = modifier,
  )
}

/** Le righe sotto una domanda del registro; pura, e provata. */
fun fluidRequestDetails(log: AiRequestLog, strings: FluidAiSettingsStrings, locale: Locale = Locale.getDefault()): String {
  // Chi ha risposto, in ordine: la catena dei modelli veri se c'e', altrimenti servizio e modelli di livello.
  val chain = log.modelsUsed
    .fold(mutableListOf<String>()) { acc, use -> "${use.provider.label} ${use.model}".let { if (acc.lastOrNull() != it) acc += it }; acc }
    .joinToString(" → ")
  val who = chain.ifEmpty {
    "${log.provider.label}${if (log.switchedTo.isNotEmpty()) " → ${log.switchedTo.joinToString(", ") { it.label }}" else ""} · ${log.models.values.distinct().joinToString(", ").ifEmpty { log.model }}"
  }
  return buildList {
    add(fluidFormat(strings.recentSummary, who, log.steps, log.durationMillis / 1000))
    if (log.groups.isNotEmpty()) add(fluidFormat(strings.recentGroups, log.groups.joinToString(", ")))
    if (log.tools.isNotEmpty()) add(fluidFormat(strings.recentTools, log.tools.joinToString(", ") { "${it.name} ${it.millis} ms${if (it.ok) "" else " ✕"}" }))
    log.usage?.let { usage ->
      add(fluidFormat(strings.recentTokens, usage.promptTokens, usage.completionTokens) + (usage.costUsd?.let { " · ${String.format(locale, "%.4f $", it)}" } ?: ""))
    }
    if (log.waitedSeconds > 0) add(fluidFormat(strings.recentWaited, log.waitedSeconds))
    log.error?.let { add(it) }
  }.joinToString("\n")
}

/**
 * La sezione "Assistente" di una pagina Diagnostica (FluidWeather): lo stato, la catena dei servizi con i
 * loro modelli, le quote residue lette dagli header (token e richieste che restano), e le ultime
 * richieste. Le domande restano in memoria e spariscono con l'app. **Dati, non flussi**: l'app legge
 * i suoi (`AiDiagnosticsLog.entries`, `rateLimits`, `AiKeyStore.states`) e passa i valori.
 */
fun LazyListScope.fluidAiDiagnosticsSection(
  settings: AiSettings,
  keys: Map<ProviderId, KeyState>,
  enabled: Boolean,
  rateLimits: Map<ProviderId, RateLimitInfo>,
  requests: List<AiRequestLog>,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
  glass: Boolean = false,
  keyPrefix: String = "fluid-ai-diag",
) {
  val verified = keys.filterValues { it.verified }.keys
  item(key = "$keyPrefix-header") { FluidSectionHeader(title = strings.diagTitle) }
  item(key = "$keyPrefix-state") {
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = strings.diagState,
        subtitle = if (enabled) {
          fluidFormat(strings.diagOn, settings.chatOrder.filter { it in verified }.joinToString(" → ") { "${it.label} (${settings.chatModel(it) ?: "—"})" })
        } else {
          strings.diagOff
        },
      )
      rateLimits.forEach { (provider, info) ->
        val parts = fluidQuotaParts(info, strings)
        if (parts.isNotEmpty()) {
          FluidListDivider()
          FluidListRow(title = fluidFormat(strings.diagQuota, provider.label), subtitle = parts.joinToString(" · "))
        }
      }
    }
  }
  item(key = "$keyPrefix-requests") {
    FluidListGroup(glass = glass) {
      if (requests.isEmpty()) {
        FluidListRow(title = strings.diagRequests, subtitle = strings.diagNone)
      } else {
        requests.forEachIndexed { index, log ->
          if (index > 0) FluidListDivider()
          FluidRecentRequestRow(log, strings = strings)
        }
      }
    }
  }
}

/** Le quote che gli header hanno detto: i token e le richieste che restano. Vuote se non dicono niente. */
fun fluidQuotaParts(info: RateLimitInfo, strings: FluidAiSettingsStrings): List<String> = buildList {
  info.remainingTokens?.let { add(fluidFormat(strings.diagQuotaTokens, it)) }
  info.remainingRequests?.let { add(fluidFormat(strings.diagQuotaRequests, it)) }
}
