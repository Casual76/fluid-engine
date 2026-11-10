package dev.antigravity.fluidengine.aiui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.keys.VerifyResult
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.ai.orchestrator.ProviderSwitch
import dev.antigravity.fluidengine.ai.orchestrator.SwitchReason
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantBubble
import dev.antigravity.fluidengine.aiui.chat.FluidComposer
import dev.antigravity.fluidengine.aiui.chat.FluidComposerChip
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidUserBubble
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.rememberFluidComposerState
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.halo.FluidAssistantHalo
import dev.antigravity.fluidengine.aiui.markdown.FluidAssistantMarkdownOptions
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantCard
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantDock
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantActions
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsActions
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsState
import dev.antigravity.fluidengine.aiui.settings.fluidAiSettingsSection
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidMarkdown
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidCard
import kotlinx.coroutines.delay

// Una pagina di prova dell'interfaccia dell'assistente: pezzi veri con dati finti, per guardarli in
// mano prima di migrare un'app. NON e' nella galleria (il modulo non e' un'app e la galleria non lo
// conosce): un'app ospite, o la galleria quando decidera' di averla, la chiama dentro un
// `FluidTheme` e con l'host dei modali alla radice (il picker dei modelli e il foglio dei dettagli
// ne hanno bisogno).

/** Un testo da assistente con tutto quello che il Markdown sa fare: lo stream lo rivela a poco a poco. */
private const val SampleMarkdown = """## Il tempo di domani

A **Firenze** domani c'e' *sole* e circa `24 °C`, con vento debole. Ecco cosa serve:

1. Occhiali da sole
2. Una giacca leggera per la sera
   - meglio se *impermeabile*
   - altrimenti un ombrello

> Se parti presto, copriti: alle sette ce ne sono 11.

| Ora | Temperatura | Vento |
|:----|:-----------:|------:|
| 08:00 | 14 °C | 6 km/h |
| 14:00 | 24 °C | 12 km/h |

```kotlin
val domani = previsioni.first { it.giorno == Giorno.DOMANI }
println(domani.massima)
```

Altri dettagli su [OpenRouter](https://openrouter.ai) o https://example.org."""

/** Lo stato di una risposta di esempio, a diversi punti del suo cammino. */
private fun sampleStates(): List<Pair<String, AssistantState>> = listOf(
  "Ascolto" to AssistantState.Listening(elapsedMillis = 3_200),
  "Lavoro" to AssistantState.Working("che tempo fa domani?", 1, 6, "thinking", 0, ProviderId.GROQ),
  "Scrive" to AssistantState.Answering("che tempo fa domani?", SampleMarkdown.take(260), ProviderId.GROQ),
  "Risposta" to AssistantState.Done(
    question = "che tempo fa domani?",
    answer = SampleMarkdown,
    chips = listOf(AnswerChip("page", "meteo"), AnswerChip("page", "mappa")),
    provider = ProviderId.GEMINI,
    mode = AskMode.TEXT,
    usage = null,
    toolsUsed = emptyList(),
    durationMillis = 2_400,
    switches = listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED)),
  ),
  "Conferma" to AssistantState.AwaitingConfirmation("metti un promemoria", PendingConfirmation(1, "Crea il promemoria", "Domani alle 8:00, «giacca»"), ProviderId.GROQ),
  "Errore" to AssistantState.Failed("che tempo fa domani?", FailureKind.RATE_LIMITED, null, 40, null, provider = ProviderId.GROQ),
)

/**
 * La pagina di prova: aureola, card, pannello ancorato, Markdown (con lo stream), bolle e composer, e
 * le impostazioni con dati finti. Chiamala dentro un `FluidScreen`; ognuna delle sezioni si prova anche da sola.
 */
fun LazyListScope.fluidAiUiDemoItems() {
  item(key = "ai-demo-markdown-header") { FluidSectionHeader(title = "Markdown", detail = "Titoli, elenchi, tabelle, codice con copia, link. Con lo stream le parole sfumano dentro.") }
  item(key = "ai-demo-markdown") { MarkdownDemo() }
  item(key = "ai-demo-card-header") { FluidSectionHeader(title = "Overlay e pannello", detail = "La card sotto l'aureola, e lo stesso assistente come pannello ancorato.") }
  item(key = "ai-demo-card") { CardDemo() }
  item(key = "ai-demo-chat-header") { FluidSectionHeader(title = "Chat", detail = "Bolle, risposta senza card, composer di vetro.") }
  item(key = "ai-demo-chat") { ChatDemo() }
  fluidAiSettingsSection(state = demoSettingsState(), actions = demoSettingsActions(), keyPrefix = "ai-demo")
}

@Composable
private fun MarkdownDemo() {
  var streaming by remember { mutableStateOf(false) }
  var shown by remember { mutableStateOf(SampleMarkdown) }
  // Lo stream finto: a pezzi di undici caratteri, ogni 60 ms, ripetibile.
  LaunchedEffect(streaming) {
    if (!streaming) return@LaunchedEffect
    shown = ""
    SampleMarkdown.chunked(11).forEach { chunk ->
      shown += chunk
      delay(60)
    }
    streaming = false
  }
  FluidCard {
    FluidChip(label = if (streaming) "In arrivo…" else "Ripeti lo stream", selected = streaming, onClick = { if (!streaming) streaming = true })
    Box(Modifier.padding(top = 12.dp)) { FluidMarkdown(shown, streaming = streaming, animateFirst = streaming, options = FluidAssistantMarkdownOptions) }
  }
}

@Composable
private fun CardDemo() {
  val backdrop = rememberGlassBackdrop()
  val states = remember { sampleStates() }
  var index by remember { mutableIntStateOf(3) }
  val (_, state) = states[index]
  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      states.forEachIndexed { i, (label, _) -> FluidChip(label = label, selected = i == index, onClick = { index = i }) }
    }
    Box(Modifier.fillMaxWidth().height(110.dp)) {
      FluidAssistantHalo(mood = demoMood(state), level = 0.6f, modifier = Modifier.align(Alignment.TopCenter))
    }
    FluidAssistantCard(
      state = state,
      pending = (state as? AssistantState.AwaitingConfirmation)?.confirmation,
      backdrop = backdrop,
      onStop = {},
      onChip = {},
      onConfirm = { _, _ -> },
      onDetails = { },
    )
    FluidAssistantDock(
      state = state,
      micLevel = MicLevel(0.4f, speaking = state is AssistantState.Listening),
      pending = (state as? AssistantState.AwaitingConfirmation)?.confirmation,
      backdrop = backdrop,
      actions = FluidAssistantActions(onAskText = {}, onAskVoice = {}),
      suggestions = listOf("Che tempo fa domani?", "Quando passa il prossimo bus?"),
    )
  }
}

private fun demoMood(state: AssistantState): FluidHaloMood = when (state) {
  is AssistantState.Listening -> FluidHaloMood.LISTENING
  is AssistantState.Answering -> FluidHaloMood.WRITING
  is AssistantState.Failed -> FluidHaloMood.ERROR
  is AssistantState.Done -> FluidHaloMood.DONE
  else -> FluidHaloMood.WORKING
}

@Composable
private fun ChatDemo() {
  val backdrop: GlassBackdropState = rememberGlassBackdrop()
  val composer = rememberFluidComposerState()
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    FluidUserBubble(text = "Che tempo fa domani a Firenze?")
    FluidAssistantBubble(text = SampleMarkdown.take(220), streaming = false)
    FluidComposer(
      value = composer.text,
      onValueChange = { composer.text = it },
      backdrop = backdrop,
      action = fluidComposerAction(hasInput = composer.text.isNotBlank(), busy = false, listening = false, speaking = false),
      onSend = { composer.sent() },
      chips = { FluidComposerChip(label = "Flash · Auto", onClick = {}) },
    )
  }
}

private fun demoSettingsState(): FluidAiSettingsState {
  val catalogue = ModelCatalogue(
    chat = listOf(
      ModelInfo("gemini-flash", "Gemini Flash", ModelKind.CHAT, contextWindow = 1_000_000, supportsVision = true, supportsDocuments = true),
      ModelInfo("qwen/qwen3:free", "Qwen3", ModelKind.CHAT, contextWindow = 128_000, supportsReasoning = true, free = true),
    ),
    stt = emptyList(),
  )
  return FluidAiSettingsState(
    settings = AiSettings(enabled = true),
    keys = mapOf(
      ProviderId.GROQ to KeyState(present = true, verifiedAtMillis = 1_700_000_000_000),
      ProviderId.GEMINI to KeyState(present = true, verifiedAtMillis = 1_700_000_000_000),
      ProviderId.OPENROUTER to KeyState(present = false, verifiedAtMillis = null),
    ),
    catalogues = mapOf(ProviderId.GEMINI to catalogue, ProviderId.GROQ to catalogue),
    thinkingAuto = true,
    failoverEnabled = true,
  )
}

private fun demoSettingsActions() = FluidAiSettingsActions(
  saveAndVerify = { _, _ -> VerifyResult.Invalid },
  removeKey = {},
  setEnabled = {},
  setThinkingAuto = {},
  setFailoverEnabled = {},
)
