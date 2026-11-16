package dev.antigravity.fluidengine.sample.assistant

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.chat.FluidComposer
import dev.antigravity.fluidengine.aiui.chat.FluidComposerChip
import dev.antigravity.fluidengine.aiui.chat.FluidGlassRoundButton
import dev.antigravity.fluidengine.aiui.chat.fluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.rememberFluidComposerState
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantActions
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantCard
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantDock
import dev.antigravity.fluidengine.aiui.overlay.FluidAssistantTextBar
import dev.antigravity.fluidengine.aiui.overlay.FluidChipPill
import dev.antigravity.fluidengine.aiui.overlay.FluidCollapsedPill
import dev.antigravity.fluidengine.aiui.overlay.FluidListeningHint
import dev.antigravity.fluidengine.aiui.overlay.FluidStopPill
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.adaptive.drawChecker
import dev.antigravity.fluidengine.sample.material.isDebuggableApp
import dev.antigravity.fluidengine.sample.measure.generateMeasurePhoto
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowUpward
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.delay

/**
 * La pagina «Assistente» (piano «iOS in Android», F3.2b e F3.2e): le superfici di `engine-ai-ui` sopra un fondo che
 * cambia, per la sessione sul tablet. Sopra, i pezzi **che girano da soli** (tasti tondi, stop, chip, la capsula
 * dell'ascolto: ognuno in un contenitore con un pezzo solo, il patto dell'inchiostro); sotto, **i pannelli** (F3.2e),
 * ognuno anche lui un contenitore con un pezzo solo: la pillola ridotta e la barra di scrittura **girano**, la card e il
 * dock **scuriscono** in continuo, la capsula del composer **gira**; i pezzi che portano sono vetro annidato del pannello.
 *
 * Il fondo (chiaro, scuro, foto) cambia dal tasto «Fondo» o da solo (`assistant.auto`), e si legge in disegno: le
 * superfici restano ferme e cambia ciò che hanno dietro. In logcat (`adb logcat -s FluidAssistente`) la pagina scrive
 * `PAGINA` (la configurazione, i contenitori e le sonde vive) e `FONDO` (ogni cambio, con l'istante).
 *
 *     adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity \
 *       --es gallery.page assistant --es assistant.fund foto --es assistant.auto true
 *     adb logcat -s FluidAssistente
 *
 * **Ferma, la pagina non chiede fotogrammi**: la card di serie è una risposta finita (niente pallino che respira), la
 * barra di scrittura non prende il fuoco, nessun alone. «Card: al lavoro» accende lo stop annidato e il pallino, e la
 * pagina diventa viva apposta.
 */
@Composable
internal fun AssistantPage(bottomInset: Dp, onBack: () -> Unit) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val debuggable = remember(context) { context.isDebuggableApp() }
  val inheritedModel = LocalFluidGlassModel.current
  val inheritedDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
  val launched = remember(activity, inheritedModel) {
    if (debuggable && activity != null) {
      AssistantLaunch.parse(inheritedDark, inheritedModel) { activity.intent?.getStringExtra(it) }
    } else {
      AssistantConfig(dark = inheritedDark, model = inheritedModel) to AssistantStart()
    }
  }
  val live = remember { AssistantLive(fund = launched.second.fund, autoMs = launched.second.autoMs) }
  AssistantContent(launched.first, live, bottomInset, onBack)
}

/** La pagina senza la lettura degli extra: è ciò che apre il test. */
@Composable
internal fun AssistantContent(initial: AssistantConfig, live: AssistantLive, bottomInset: Dp, onBack: () -> Unit) {
  var config by remember { mutableStateOf(initial) }
  FluidTheme(settings = EngineSettings(themeMode = if (config.dark) ThemeMode.DARK else ThemeMode.LIGHT), systemBars = false) {
    CompositionLocalProvider(
      LocalFluidGlassModel provides config.model,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      // La pagina rinasce solo se cambia il tema o il vetro chiesto; il fondo e la card no. La card che cambia è il
      // contenuto di un pannello che cambia misura sotto lo stesso vetro (F3.8a): quando rinasceva tutta la pagina, ogni
      // contenitore era nuovo, e un contenitore nuovo posa il vetro un fotogramma dopo il contenuto (il «fotogramma senza
      // vetro» su card e dock di S3).
      key(config.dark, config.model) {
        AssistantStage(config, live, bottomInset, onBack, onChange = { config = it })
      }
    }
  }
}

@Composable
private fun AssistantStage(
  config: AssistantConfig,
  live: AssistantLive,
  bottomInset: Dp,
  onBack: () -> Unit,
  onChange: (AssistantConfig) -> Unit,
) {
  val stage = rememberGlassBackdrop()
  val photo = rememberPhotoBrush()
  // La scala delle animazioni di sistema allunga anche l'automatico, perché un giro lento si veda finire.
  val scale = LocalFluidMotionPolicy.current.durationScale
  val change: () -> Unit = remember(live) { { live.change(::logFund) } }

  AutoFund(live, scale, change)
  PageLog(config, live)

  Box(modifier = Modifier.fillMaxSize().testTag("assistant-page")) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        // Il fondo cambia solo in disegno, dentro il layer della sorgente: dalla 4.0 (F3.10) lo dice `glassBackdropSource`
        // da sé (`glassSourceRedraws`, dentro il layer), e le sonde dei vetri rileggono senza che la pagina annunci niente.
        .glassBackdropSource(stage)
        .drawBehind { fund(photo, live.fund) },
    )
    CompositionLocalProvider(LocalGlassBackdrop provides stage, LocalFluidCanvasBackdrop provides stage) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .padding(
            start = FluidMetrics.ScreenEdgeMargin,
            end = FluidMetrics.ScreenEdgeMargin,
            top = 8.dp,
            bottom = bottomInset + FluidFloatingTabBarDefaults.ContentInset + 16.dp,
          ),
      ) {
        Controls(config, live, scale, onBack, onChange, change)
        Spacer(Modifier.height(20.dp))
        Caption("Girano da soli: un contenitore con un pezzo solo, l'inchiostro del lato")
        Spacer(Modifier.height(10.dp))
        Alone(stage)
        Spacer(Modifier.height(24.dp))
        Caption("Pannelli coi pezzi annidati: pillola e barra girano, card e dock scuriscono, il composer gira")
        Spacer(Modifier.height(10.dp))
        Panels(config, stage)
      }
    }
  }
}

private fun logFund(fund: AssistantFund, step: Int) {
  Log.i(AssistantLogTag, "FONDO ${fund.key} passo=$step t=${SystemClock.uptimeMillis()}")
}

// ------------------------------------------------------------------------------------------------------------
// I comandi
// ------------------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Controls(
  config: AssistantConfig,
  live: AssistantLive,
  scale: Float,
  onBack: () -> Unit,
  onChange: (AssistantConfig) -> Unit,
  change: () -> Unit,
) {
  Column(modifier = Modifier.plate()) {
    Text("Assistente", style = MaterialTheme.typography.headlineSmall)
    Text(
      "F3.2e · ${config.key}" + if (scale != 1f) " · animazioni ×${"%.2f".format(scale)}" else "",
      style = MaterialTheme.typography.bodySmall,
    )
  }
  Spacer(Modifier.height(8.dp))
  FlowRow(
    horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
    verticalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing),
  ) {
    FluidGlassButton(text = "Indietro", onClick = onBack)
    FluidGlassButton(
      text = "Fondo: ${live.fund.title}",
      onClick = change,
      modifier = Modifier.testTag("assistant-fund"),
    )
    FluidGlassButton(
      text = "Automatico: ${if (live.autoMs > 0L) "${live.autoMs} ms" else "spento"}",
      onClick = { live.autoMs = if (live.autoMs > 0L) 0L else AssistantLaunch.DefaultAutoMs },
      modifier = Modifier.testTag("assistant-auto"),
    )
    FluidGlassButton(
      text = "Tema: ${if (config.dark) "scuro" else "chiaro"}",
      onClick = { onChange(config.copy(dark = !config.dark)) },
      modifier = Modifier.testTag("assistant-theme"),
    )
    FluidGlassButton(
      text = "Vetro: ${config.model.name}",
      onClick = {
        onChange(config.copy(model = if (config.model == FluidGlassModel.Liquid) FluidGlassModel.Classic else FluidGlassModel.Liquid))
      },
      modifier = Modifier.testTag("assistant-model"),
    )
    FluidGlassButton(
      text = "Card: ${config.card.title}",
      onClick = { onChange(config.copy(card = config.card.next())) },
      modifier = Modifier.testTag("assistant-card"),
    )
  }
}

/**
 * Una lastra quasi opaca del colore del tema dietro un testo della pagina, come nelle pagine «Adattività» e «Piano
 * flottante»: senza, il titolo e le didascalie si perdono sul fondo. Non è vetro e non sta nella sorgente.
 */
@Composable
private fun Modifier.plate(): Modifier {
  val color = MaterialTheme.colorScheme.background.copy(alpha = 0.88f)
  return this.drawBehind {
    val dx = 8.dp.toPx()
    val dy = 3.dp.toPx()
    drawRoundRect(
      color = color,
      topLeft = Offset(-dx, -dy),
      size = Size(size.width + 2f * dx, size.height + 2f * dy),
      cornerRadius = CornerRadius(FluidRadius.Small.toPx()),
    )
  }
}

@Composable
private fun Caption(text: String) {
  Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.plate())
}

// ------------------------------------------------------------------------------------------------------------
// Le superfici
// ------------------------------------------------------------------------------------------------------------

/** I pezzi che girano da soli: ognuno un contenitore con un pezzo solo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Alone(stage: GlassBackdropState) {
  FlowRow(
    modifier = Modifier.fillMaxWidth().testTag("assistant-alone"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Il tasto tondo di serie: l'icona nell'inchiostro del lato.
    FluidGlassRoundButton(FluidIcons.Mic, "Parla", stage, onClick = {})
    // Lo stesso con l'accento scritto dall'app: l'accento resta su tutti e due i lati.
    FluidGlassRoundButton(FluidIcons.ArrowUpward, "Invia", stage, tint = MaterialTheme.colorScheme.primary, onClick = {})
    FluidStopPill(stage, onStop = {})
    FluidChipPill(label = "meteo", backdrop = stage, onClick = {})
    FluidChipPill(label = "Stazione Santa Maria Novella", backdrop = stage, onClick = {}, maxLines = 2)
  }
  FluidListeningHint(backdrop = stage, elapsedMillis = 3_200, onStop = {})
}

/** I pannelli (F3.2e), ognuno un contenitore con un pezzo solo, coi pezzi annidati che portano. */
@Composable
private fun Panels(config: AssistantConfig, stage: GlassBackdropState) {
  val state = remember(config.card) { sampleState(config.card) }
  Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().testTag("assistant-panels")) {
    FluidCollapsedPill(text = "Domani a Firenze c'è sole, circa 24 °C", backdrop = stage, onExpand = {})
    FluidAssistantTextBar(
      backdrop = stage,
      busy = false,
      micAvailable = true,
      onSend = {},
      onVoice = {},
      onNewConversation = {},
      // Niente fuoco: la tastiera coprirebbe la pagina, e il cursore che lampeggia chiederebbe fotogrammi.
      autoFocus = false,
    )
    FluidAssistantCard(
      state = state,
      pending = null,
      backdrop = stage,
      onStop = {},
      onChip = {},
      onConfirm = { _, _ -> },
    )
    FluidAssistantDock(
      state = state,
      micLevel = MicLevel(0f, speaking = false),
      pending = null,
      backdrop = stage,
      actions = FluidAssistantActions(onAskText = {}, onAskVoice = {}),
    )
    val composer = rememberFluidComposerState()
    FluidComposer(
      value = composer.text,
      onValueChange = { composer.text = it },
      backdrop = stage,
      action = fluidComposerAction(hasInput = composer.text.isNotBlank(), busy = false, listening = false, speaking = false),
      onSend = { composer.sent() },
      // La galleria non ascolta: il tasto del microfono non chiede il permesso.
      requestMicPermission = false,
      leading = { controls -> FluidGlassRoundButton(FluidIcons.Add, "Allega", controls, onClick = {}) },
      chips = { FluidComposerChip(label = "Flash · Auto", onClick = {}) },
    )
  }
}

/** Lo stato della card e del dock: la risposta finita coi chip, o l'assistente al lavoro. Dati finti, nessun servizio. */
internal fun sampleState(card: AssistantCardState): AssistantState = when (card) {
  AssistantCardState.Answer -> AssistantState.Done(
    question = "che tempo fa domani a Firenze?",
    answer = "Domani a **Firenze** c'è *sole* e circa `24 °C`, con vento debole. Alle sette ce ne sono 11: copriti se parti presto.",
    chips = listOf(AnswerChip("page", "meteo"), AnswerChip("page", "mappa")),
    provider = ProviderId.GEMINI,
    mode = AskMode.TEXT,
    usage = null,
    toolsUsed = emptyList(),
    durationMillis = 2_400,
  )
  AssistantCardState.Working -> AssistantState.Working("che tempo fa domani a Firenze?", 1, 6, "thinking", 0, ProviderId.GROQ)
}

// ------------------------------------------------------------------------------------------------------------
// Il fondo e i registri
// ------------------------------------------------------------------------------------------------------------

/** La foto sintetica della galleria come pennello che si ripete ogni 512 dp (la stessa di «Adattività» e «Piano flottante»). */
@Composable
private fun rememberPhotoBrush(): Brush {
  val density = LocalDensity.current
  return remember(density) {
    val image = generateMeasurePhoto()
    val period = with(density) { 512.dp.toPx() }
    ShaderBrush(
      ImageShader(image, TileMode.Repeated, TileMode.Repeated).apply {
        val scale = period / image.width.toFloat()
        setLocalMatrix(Matrix().apply { setScale(scale, scale) })
      },
    )
  }
}

/**
 * I due fondi pieni: una scacchiera a bassissimo contrasto, perché la lente abbia qualcosa da piegare senza che la sonda
 * legga altro che «chiaro» o «scuro».
 */
private val LightA = Color(0xFFF7F7F9)
private val LightB = Color(0xFFE6E6EB)
private val DarkA = Color(0xFF0E0E12)
private val DarkB = Color(0xFF1C1C22)

private fun DrawScope.fund(photo: Brush, fund: AssistantFund) {
  val cell = 32.dp.toPx()
  when (fund) {
    AssistantFund.Light -> drawChecker(cell, LightA, LightB, 0f)
    AssistantFund.Dark -> drawChecker(cell, DarkA, DarkB, 0f)
    AssistantFund.Photo -> drawRect(photo)
  }
}

/**
 * Il cambio automatico del fondo, per le riprese: ogni [AssistantLive.autoMs] (moltiplicato per la scala delle animazioni,
 * mai meno di uno) il fondo dopo. Spento, non c'è nessun effetto e la pagina non si muove.
 */
@Composable
private fun AutoFund(live: AssistantLive, scale: Float, change: () -> Unit) {
  val every = live.autoMs
  LaunchedEffect(every, scale) {
    if (every <= 0L) return@LaunchedEffect
    val wait = (every * scale.coerceAtLeast(1f)).toLong()
    while (true) {
      delay(wait)
      change()
    }
  }
}

/**
 * La riga `PAGINA`, un secondo dopo che la pagina (ri)nasce: la configurazione, il fondo, i contenitori vivi (uno per pezzo
 * che gira da solo) e le sonde. Un `delay` solo, che non chiede fotogrammi.
 */
@Composable
private fun PageLog(config: AssistantConfig, live: AssistantLive) {
  LaunchedEffect(config) {
    delay(1_000)
    Log.i(
      AssistantLogTag,
      "PAGINA ${config.key} fondo=${live.fund.key} contenitori=${FluidGlassContainerDiagnostics.liveContainers} " +
        "sonde=${FluidGlassProbeDiagnostics.liveProbes} t=${SystemClock.uptimeMillis()}",
    )
  }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}
