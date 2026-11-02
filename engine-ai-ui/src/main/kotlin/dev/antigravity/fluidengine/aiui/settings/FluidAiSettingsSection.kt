package dev.antigravity.fluidengine.aiui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.keys.VerifyResult
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenRouterDataPolicy
import dev.antigravity.fluidengine.ai.provider.OpenRouterKeyInfo
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import java.text.DateFormat
import java.util.Date

/**
 * Perche' l'assistente non si puo' accendere adesso: un interruttore a distanza lo ha sospeso
 * (FluidWeather). L'interruttore resta, spento e senza risposta, e il sottotitolo dice il motivo
 * invece di far sembrare rotta la pagina.
 */
@Immutable
data class FluidAiLock(val reason: String)

/**
 * Tutto cio' che la sezione legge, **come dati**: l'app li ricava dai suoi flussi
 * (`AiSettingsStore.settings`, `AiKeyStore.states`, `ModelCatalogStore.catalogues`,
 * `AiKeyVerifier.keyInfo`, `AiDiagnosticsLog.entries`) e li passa. Cosi' la sezione non conosce
 * ne' un ViewModel ne' un DataStore, e funziona uguale con i quattro modi in cui le app li tengono.
 *
 * @param thinkingAuto il ragionamento "Auto" di PampAI (lo decide l'assistente, alto sulle domande
 *   profonde): null non mostra il chip, e le app che non lo hanno non lo vedono.
 * @param failoverEnabled l'interruttore "riserva automatica" (`AskInput.pinProvider` dell'engine):
 *   null non mostra la riga.
 * @param openRouterQuota i gratuiti di OpenRouter usati oggi contro il tetto; null non mostra la riga.
 * @param locked l'assistente e' sospeso a distanza.
 */
@Immutable
data class FluidAiSettingsState(
  val settings: AiSettings = AiSettings(),
  val keys: Map<ProviderId, KeyState> = emptyMap(),
  val catalogues: Map<ProviderId, ModelCatalogue> = emptyMap(),
  val keyInfo: Map<ProviderId, OpenRouterKeyInfo> = emptyMap(),
  val recent: List<AiRequestLog> = emptyList(),
  val thinkingAuto: Boolean? = null,
  val failoverEnabled: Boolean? = null,
  val openRouterQuota: FluidOpenRouterQuota? = null,
  val locked: FluidAiLock? = null,
) {
  /** I servizi con una chiave verificata: gli unici che possono rispondere. */
  val verified: Set<ProviderId> get() = keys.filterValues { it.verified }.keys

  /** L'interruttore e' acceso davvero: l'utente lo ha acceso, c'e' una chiave buona, e nessuno lo ha sospeso. */
  val enabled: Boolean get() = settings.enabled && verified.isNotEmpty() && locked == null
}

/**
 * Cosa la sezione fa quando si tocca. Callback, come [dev.antigravity.fluidengine.aiui.overlay.FluidAssistantActions]:
 * un'app con un ViewModel li lega ai suoi metodi, una senza li lega a uno scope e a `AiSettingsStore`.
 *
 * @param saveAndVerify salva la chiave (se non e' null) e la verifica: `{ p, k -> k?.let { keys.set(p, it) }; verifier.verify(p) }`.
 * @param onRequestConsent chiamata al posto di `setEnabled(true)` quando [FluidAiSettingsOptions.requireConsent]
 *   e il consenso non c'e': l'app apre la sua pagina del consenso.
 * @param setEnabled accende o spegne (dopo il consenso, se serve).
 * @param setThinkingAuto, [setFailoverEnabled] solo per le app che hanno quei due interruttori.
 */
@Stable
class FluidAiSettingsActions(
  val saveAndVerify: suspend (ProviderId, String?) -> VerifyResult,
  val removeKey: (ProviderId) -> Unit,
  val setEnabled: (Boolean) -> Unit,
  val setChatOrder: (List<ProviderId>) -> Unit = {},
  val setSttOrder: (List<ProviderId>) -> Unit = {},
  val setModel: (ProviderId, ModelTier, String) -> Unit = { _, _, _ -> },
  val setSttModel: (ProviderId, String) -> Unit = { _, _ -> },
  val setOpenRouterFallbacks: (List<String>) -> Unit = {},
  val setOpenRouterDataPolicy: (OpenRouterDataPolicy) -> Unit = {},
  val refreshCatalogue: (ProviderId) -> Unit = {},
  val setThinking: (ThinkingLevel) -> Unit = {},
  val setThinkingAuto: ((Boolean) -> Unit)? = null,
  val setSpeakReplies: (Boolean) -> Unit = {},
  val setActionsEnabled: (Boolean) -> Unit = {},
  val setFailoverEnabled: ((Boolean) -> Unit)? = null,
  val onRequestConsent: () -> Unit = {},
  val onVerified: (ProviderId, VerifyResult.Ok) -> Unit = { _, _ -> },
)

/**
 * Cosa la sezione mostra. Tutto acceso di serie: e' l'unione delle quattro app, e ognuna spegne
 * quello che non ha (FluidWeather un solo livello di modello, Fluid-transit niente trascrizione).
 *
 * @param requireConsent la prima volta che si accende passa dal consenso dell'app ([FluidAiSettingsActions.onRequestConsent]).
 * @param tiers i tre livelli (router, chat, profondo) per servizio; falso solo la chat.
 * @param transcription l'ordine e il modello della trascrizione della voce.
 * @param draggableOrder la maniglia per trascinare l'ordine dei servizi.
 * @param microphone la riga del permesso del microfono.
 * @param recentRequests le ultime richieste in memoria.
 * @param dataPolicy la scelta di OpenRouter sull'addestramento.
 * @param initiallyExpandFirstKey la guida del primo servizio aperta finche' nessuna chiave e' verificata.
 */
@Immutable
data class FluidAiSettingsOptions(
  val requireConsent: Boolean = false,
  val tiers: Boolean = true,
  val transcription: Boolean = true,
  val draggableOrder: Boolean = true,
  val microphone: Boolean = true,
  val thinking: Boolean = true,
  val speakReplies: Boolean = true,
  val actions: Boolean = true,
  val recentRequests: Boolean = true,
  val dataPolicy: Boolean = true,
  val privacyNote: Boolean = true,
  val initiallyExpandFirstKey: Boolean = true,
)

/**
 * Dove l'app aggiunge le sue voci fra quelle dell'engine: PampAI la voce, i permessi, le azioni
 * fidate e le app collegate; ClasseViva l'uso e la cronologia; FluidWeather il tutorial e la
 * diagnostica. Ognuno e' un blocco di `item` di una `LazyColumn`, e compare solo se c'e'.
 */
@Immutable
class FluidAiSettingsSlots(
  val beforeKeys: (LazyListScope.() -> Unit)? = null,
  val afterKeys: (LazyListScope.() -> Unit)? = null,
  val afterModels: (LazyListScope.() -> Unit)? = null,
  val afterPreferences: (LazyListScope.() -> Unit)? = null,
  val footer: (LazyListScope.() -> Unit)? = null,
)

/**
 * Impostazioni → Assistente IA, come righe di una lista che la pagina dell'app ospita (come
 * `fluidLicensesSection` e `fluidAppearanceSection`): l'interruttore (che la prima volta puo'
 * passare dal consenso, o essere sospeso a distanza), le chiavi con la guida e la verifica, l'ordine
 * dei servizi, i modelli per livello (con il picker nel vetro), le preferenze (ragionamento, lettura,
 * azioni, riserva automatica, microfono), la nota sulla privacy e le ultime richieste.
 *
 * E' l'**unione** di `assistantSettingsItems` di ClasseViva e PampAI (quasi lo stesso file: PampAI lo
 * aveva portato), di `AiSettingsScreen` di FluidWeather e delle chiavi di Fluid-transit. La sezione
 * non sa niente del dominio: i testi dell'app (il nome dell'assistente, cosa parte verso il servizio)
 * sono in [strings], le voci che l'engine non puo' conoscere in [slots].
 *
 * **I dati escono dalle callback, non da un ViewModel.** `LazyListScope` non puo' leggere stato da
 * se': la pagina raccoglie i flussi e passa [state].
 *
 * @param glass le liste come pannelli di vetro (la pagina deve avere un canvas ambientale).
 * @param keyPrefix il prefisso delle chiavi degli item, se la pagina ha due sezioni di questo tipo.
 */
fun LazyListScope.fluidAiSettingsSection(
  state: FluidAiSettingsState,
  actions: FluidAiSettingsActions,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
  options: FluidAiSettingsOptions = FluidAiSettingsOptions(),
  slots: FluidAiSettingsSlots = FluidAiSettingsSlots(),
  glass: Boolean = true,
  keyPrefix: String = "fluid-ai",
) {
  val verified = state.verified

  slots.beforeKeys?.invoke(this)

  item(key = "$keyPrefix-enabled") {
    FluidListGroup(glass = glass) {
      FluidListRow(
        title = strings.enabledTitle,
        subtitle = state.locked?.reason ?: when {
          verified.isEmpty() -> strings.enabledNeedsKey
          state.settings.enabled -> strings.enabledOn
          else -> strings.enabledOff
        },
        badge = {
          FluidSwitch(
            checked = state.enabled,
            enabled = verified.isNotEmpty() && state.locked == null,
            onCheckedChange = { on ->
              if (on && options.requireConsent && !state.settings.consentAccepted) actions.onRequestConsent() else actions.setEnabled(on)
            },
          )
        },
      )
    }
  }

  item(key = "$keyPrefix-keys-header") { FluidSectionHeader(title = strings.keysTitle, detail = strings.keysDetail) }
  item(key = "$keyPrefix-keys") {
    FluidListGroup(glass = glass) {
      ProviderId.entries.forEachIndexed { index, provider ->
        if (index > 0) FluidListDivider()
        FluidAiKeySetup(
          provider = provider,
          state = state.keys[provider] ?: KeyState(false, null),
          saveAndVerify = actions.saveAndVerify,
          onRemove = actions.removeKey,
          initiallyExpanded = options.initiallyExpandFirstKey && provider == ProviderId.entries.first() && verified.isEmpty(),
          onVerified = { actions.onVerified(provider, it) },
          strings = strings,
        )
        if (provider == ProviderId.OPENROUTER && state.keys[provider]?.present == true) {
          FluidOpenRouterKeyDetails(
            settings = state.settings,
            catalogue = state.catalogues[ProviderId.OPENROUTER],
            info = state.keyInfo[ProviderId.OPENROUTER],
            quota = state.openRouterQuota,
            strings = strings,
          )
          if (options.dataPolicy) {
            FluidListDivider()
            FluidOpenRouterDataPolicyRow(
              policy = state.settings.openRouterDataPolicy,
              onPolicy = actions.setOpenRouterDataPolicy,
              strings = strings,
            )
          }
        }
      }
    }
  }

  slots.afterKeys?.invoke(this)

  if (verified.size > 1) {
    item(key = "$keyPrefix-order-header") { FluidSectionHeader(title = strings.orderTitle, detail = strings.orderDetail) }
    item(key = "$keyPrefix-order-chat") {
      FluidOrderLabel(strings.orderChat)
      FluidProviderOrderList(
        order = state.settings.chatOrder,
        available = verified,
        onReorder = actions.setChatOrder,
        strings = strings,
        glass = glass,
        draggable = options.draggableOrder,
      )
    }
    if (options.transcription) {
      item(key = "$keyPrefix-order-stt") {
        FluidOrderLabel(strings.orderStt)
        FluidProviderOrderList(
          order = state.settings.sttOrder,
          available = verified,
          onReorder = actions.setSttOrder,
          strings = strings,
          glass = glass,
          draggable = options.draggableOrder,
        )
      }
    }
  }

  if (verified.isNotEmpty()) {
    item(key = "$keyPrefix-models-header") {
      FluidSectionHeader(title = strings.modelsTitle, detail = if (options.tiers) strings.modelsDetail else strings.modelsDetailChatOnly)
    }
    item(key = "$keyPrefix-models") { FluidAiModelsSection(state, actions, strings, options, glass) }
  }

  slots.afterModels?.invoke(this)

  val showPreferences = options.thinking || options.speakReplies || options.actions || state.failoverEnabled != null || options.microphone
  if (showPreferences) {
    item(key = "$keyPrefix-prefs-header") { FluidSectionHeader(title = strings.prefsTitle) }
    item(key = "$keyPrefix-prefs") { FluidAiPreferences(state, actions, strings, options, glass) }
  }

  slots.afterPreferences?.invoke(this)

  if (options.privacyNote && strings.privacyNote.isNotBlank()) {
    item(key = "$keyPrefix-privacy") {
      Text(
        strings.privacyNote,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
      )
    }
  }

  if (options.recentRequests && state.recent.isNotEmpty()) {
    item(key = "$keyPrefix-recent-header") { FluidSectionHeader(title = strings.recentTitle, detail = strings.recentDetail) }
    item(key = "$keyPrefix-recent") {
      FluidListGroup(glass = glass) {
        state.recent.forEachIndexed { index, log ->
          if (index > 0) FluidListDivider()
          FluidRecentRequestRow(log, strings = strings)
        }
      }
    }
  }

  slots.footer?.invoke(this)
}

@Composable
private fun FluidOrderLabel(text: String) {
  Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
}

/** I modelli: per ogni servizio verificato i suoi livelli (o la sola chat), la trascrizione, e "aggiorna il catalogo". */
@Composable
private fun FluidAiModelsSection(
  state: FluidAiSettingsState,
  actions: FluidAiSettingsActions,
  strings: FluidAiSettingsStrings,
  options: FluidAiSettingsOptions,
  glass: Boolean,
) {
  var picker by remember { mutableStateOf<FluidModelPickRequest?>(null) }
  val settings = state.settings
  val tiers = if (options.tiers) ModelTier.entries else listOf(ModelTier.CHAT)
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    state.verified.sortedBy { it.ordinal }.forEach { provider ->
      val catalogue = state.catalogues[provider]
      val refreshedAt = settings.modelsRefreshedAt[provider]
      FluidListGroup(glass = glass) {
        tiers.forEachIndexed { index, tier ->
          if (index > 0) FluidListDivider()
          val chosen = settings.model(provider, tier)
          // Se il profondo e' lo stesso della chat non c'e' nessun passaggio da fare, e il tool
          // `modello_avanzato` non viene nemmeno offerto al modello: meglio dirlo qui.
          val sameAsChat = options.tiers && tier == ModelTier.DEEP && chosen == settings.model(provider, ModelTier.CHAT)
          FluidListRow(
            title = "${provider.label} · ${strings.tierLabel(tier)}",
            subtitle = (if (options.tiers) "${strings.tierHint(tier)} · " else "") + catalogue.fluidSummary(chosen, strings) +
              if (sameAsChat) strings.modelSameAsChat else "",
            onClick = { picker = FluidModelPickRequest(provider, tier) },
          )
        }
        if (options.transcription) {
          FluidListDivider()
          FluidListRow(
            title = "${provider.label} · ${strings.modelTranscription}",
            subtitle = catalogue.fluidSummary(settings.sttModel(provider), strings),
            onClick = { picker = FluidModelPickRequest(provider, null, stt = true) },
          )
        }
        FluidListDivider()
        FluidListRow(
          title = strings.modelsRefresh,
          subtitle = if (refreshedAt != null) {
            fluidFormat(strings.modelsRefreshedAt, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(refreshedAt)))
          } else {
            strings.modelsNeverRead
          },
          onClick = { actions.refreshCatalogue(provider) },
        )
      }
    }
  }
  // Dichiarato sempre, mai dentro un `let`: smontarlo alla chiusura toglie l'animazione di uscita.
  FluidModelPickerPortal(
    request = picker,
    catalogues = state.catalogues,
    selectedModel = { request -> if (request.stt) settings.sttModel(request.provider) else request.tier?.let { settings.model(request.provider, it) } },
    fallbacks = settings.openRouterFallbacks,
    onSelect = { request, id -> if (request.stt) actions.setSttModel(request.provider, id) else request.tier?.let { actions.setModel(request.provider, it, id) } },
    onFallbacks = actions.setOpenRouterFallbacks,
    onDismiss = { picker = null },
    strings = strings,
  )
}

/** Le preferenze: ragionamento (con "Auto" se l'app lo ha), lettura, azioni, riserva automatica, microfono. */
@Composable
private fun FluidAiPreferences(
  state: FluidAiSettingsState,
  actions: FluidAiSettingsActions,
  strings: FluidAiSettingsStrings,
  options: FluidAiSettingsOptions,
  glass: Boolean,
) {
  val context = LocalContext.current
  var permissionEpoch by remember { mutableIntStateOf(0) }
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionEpoch++ }
  val micGranted = remember(permissionEpoch) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  val settings = state.settings
  val rows = buildList<@Composable () -> Unit> {
    if (options.thinking) {
      add {
        val auto = state.thinkingAuto
        FluidChoiceRowWithChips(
          title = strings.thinkingTitle,
          subtitle = if (auto != null) strings.thinkingDetailAuto else strings.thinkingDetail,
        ) {
          if (auto != null && actions.setThinkingAuto != null) {
            FluidChip(label = strings.thinkingAuto, selected = auto, onClick = { actions.setThinkingAuto.invoke(true) })
          }
          ThinkingLevel.entries.forEach { level ->
            FluidChip(
              label = strings.thinkingLabel(level),
              selected = auto != true && settings.thinking == level,
              onClick = {
                actions.setThinkingAuto?.invoke(false)
                actions.setThinking(level)
              },
            )
          }
        }
      }
    }
    if (options.speakReplies) {
      add {
        FluidListRow(
          title = strings.speakTitle,
          subtitle = strings.speakDetail,
          badge = { FluidSwitch(checked = settings.speakReplies, onCheckedChange = actions.setSpeakReplies) },
        )
      }
    }
    if (options.actions) {
      add {
        FluidListRow(
          title = strings.actionsTitle,
          subtitle = strings.actionsDetail,
          badge = { FluidSwitch(checked = settings.actionsEnabled, onCheckedChange = actions.setActionsEnabled) },
        )
      }
    }
    val failover = state.failoverEnabled
    val setFailover = actions.setFailoverEnabled
    if (failover != null && setFailover != null) {
      add {
        FluidListRow(
          title = strings.failoverTitle,
          subtitle = strings.failoverDetail,
          badge = { FluidSwitch(checked = failover, onCheckedChange = setFailover) },
        )
      }
    }
    if (options.microphone) {
      add {
        FluidListRow(
          title = strings.micTitle,
          subtitle = if (micGranted) strings.micGranted else strings.micDenied,
          badge = if (micGranted) {
            null
          } else {
            {
              FluidButton(
                text = strings.micAllow,
                style = FluidButtonStyle.Tinted,
                size = FluidButtonSize.Small,
                onClick = {
                  micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                  // Negato per sempre: l'unica strada sono le impostazioni di sistema.
                  if (permissionEpoch > 0) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                  }
                },
              )
            }
          },
        )
      }
    }
  }
  FluidListGroup(glass = glass) {
    rows.forEachIndexed { index, row ->
      if (index > 0) FluidListDivider()
      row()
    }
  }
}

/**
 * I cataloghi si leggono dal disco all'apertura e si rinfrescano se vecchi di un giorno: un
 * `LaunchedEffect` che la pagina chiama una volta, con i servizi verificati, e che `AiKeyVerifier`
 * (`refreshIfStale`) fa per ognuno. Sta qui perche' tutte e quattro le app lo avevano scritto
 * identico (tre lo dimenticavano, e la lista dei modelli restava quella dell'ultima verifica).
 */
@Composable
fun FluidAiCatalogueRefresh(verified: Set<ProviderId>, refresh: suspend (ProviderId) -> Unit) {
  LaunchedEffect(verified) {
    verified.forEach { provider -> refresh(provider) }
  }
}
