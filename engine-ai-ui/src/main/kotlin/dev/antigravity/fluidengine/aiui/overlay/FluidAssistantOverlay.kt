package dev.antigravity.fluidengine.aiui.overlay

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.MicLevel
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.aiui.halo.FluidAssistantHalo
import dev.antigravity.fluidengine.aiui.halo.FluidHaloColours
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Cosa l'overlay chiede all'app. Sono callback e non un ViewModel: lo stato dell'assistente vive in
 * quattro posti diversi nelle quattro app (un ViewModel, `AiAssistant.session`, un servizio in
 * primo piano), e l'overlay non deve sapere dove. L'app legge i suoi flussi, li passa come valori,
 * e qui dice cosa fare quando l'utente tocca.
 *
 * @param onAskText una domanda scritta.
 * @param onAskVoice parte l'ascolto (il gettone di [FluidAssistantOverlayState] garantisce una volta sola per tocco).
 * @param onStopListening "ho finito di parlare".
 * @param onCancel ferma il lavoro in corso (il tasto stop, o la card trascinata via mentre lavora).
 * @param onDismiss chiude la card e azzera lo stato di chi lo mostra.
 * @param onNewConversation ricomincia da capo.
 * @param onConfirm l'utente ha detto si' (true) o no (false) a un'azione in attesa.
 * @param onChip un tocco su un chip: l'app lo traduce in una pagina, un posto, un dettaglio.
 */
@Stable
class FluidAssistantActions(
  val onAskText: (String) -> Unit,
  val onAskVoice: () -> Unit,
  val onStopListening: () -> Unit = {},
  val onCancel: () -> Unit = {},
  val onDismiss: () -> Unit = {},
  val onNewConversation: () -> Unit = {},
  val onConfirm: (id: Long, yes: Boolean) -> Unit = { _, _ -> },
  val onChip: (AnswerChip) -> Unit = {},
)

/**
 * Le opzioni della card dentro l'overlay, in un oggetto solo: la card ne ha dodici, e un overlay che
 * le ripetesse tutte come parametri suoi sarebbe un elenco da non leggere. Vedi [FluidAssistantCard].
 */
@Stable
class FluidAssistantCardOptions(
  val maxBodyHeight: Dp = 380.dp,
  val tapHaptic: FluidHapticEvent? = null,
  val showSwitchLine: Boolean = true,
  val onDetails: ((FluidFailureReport) -> Unit)? = null,
  val header: (@Composable ColumnScope.() -> Unit)? = null,
  val footer: (@Composable ColumnScope.() -> Unit)? = null,
  val body: (@Composable (text: String, streaming: Boolean) -> Unit)? = null,
)

/**
 * L'overlay dell'assistente sopra la pagina: aureola in cima, barra di scrittura (in modalita'
 * testo), card della risposta che **cresce dal tasto** sopra la pillola. Non e' modale: nessuno
 * scrim, i tocchi fuori passano alla pagina, e la card si riduce a una pillola quando l'utente
 * torna a usare l'app; trascinarla in alto la chiude (e interrompe, se sta lavorando), il tasto
 * stop interrompe e basta. La domanda vive nell'app (un servizio, un ViewModel): chiudere l'overlay
 * non la ferma.
 *
 * E' l'overlay di ClasseViva e di FluidWeather, che erano lo stesso file: l'aptica di ogni stato
 * ([FluidAssistantHapticTracker]), la voce che legge le frasi che si chiudono (solo per le domande
 * fatte a voce, se l'utente l'ha accesa), lo stato che nasce altrove (una domanda dalla notifica)
 * che apre l'overlay da solo, "non ho sentito niente" che si chiude dopo 1,6 s, il pannello che
 * cresce dal rettangolo del tasto e ci rientra, il trascinamento per chiuderlo, il permesso del
 * microfono riletto quando l'app torna davanti.
 *
 * **Non e' un ViewModel** ([FluidAssistantActions]). Quello che resta all'app e' il dominio: la
 * navigazione ai tool (`overlay.collapseForNavigation()` e poi la sua), l'invocazione (tasto e
 * pressione lunga che chiamano `openVoice`/`openText`), cosa e' un chip, e le frasi dei suoi gruppi.
 *
 * @param speaker chi legge ad alta voce; null non legge ([rememberFluidTtsSpeaker] e' la voce di sistema).
 * @param speakReplies l'interruttore delle impostazioni; vale solo se [lastMode] e' `VOICE`.
 * @param haptics falso spegne l'aptica di stato (l'app ne ha una sua).
 * @param onForegroundChanged l'app e' davanti o no: decide se la risposta va anche in notifica.
 * @param collapseOnChip se un tocco su quel chip fa da parte la card (perche' porta in un'altra pagina).
 * @param dismissDrag quanto va trascinata in alto la card per chiuderla.
 */
@Composable
fun BoxScope.FluidAssistantOverlay(
  state: AssistantState,
  micLevel: MicLevel,
  pending: PendingConfirmation?,
  overlay: FluidAssistantOverlayState,
  backdrop: GlassBackdropState,
  actions: FluidAssistantActions,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
  chipLabel: (AnswerChip) -> String = ::fluidDefaultChipLabel,
  speaker: FluidSpeaker? = null,
  speakReplies: Boolean = false,
  lastMode: AskMode? = null,
  haptics: Boolean = true,
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  card: FluidAssistantCardOptions = FluidAssistantCardOptions(),
  onForegroundChanged: ((Boolean) -> Unit)? = null,
  collapseOnChip: (AnswerChip) -> Boolean = { true },
  dismissDrag: Dp = 56.dp,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val fluidHaptics = rememberFluidHaptics()
  val currentState by rememberUpdatedState(state)
  val tracker = remember { FluidAssistantHapticTracker() }

  // L'app davanti o no, e il permesso del microfono riletto allo stesso momento (concesso dalle
  // impostazioni di sistema, prima restava "negato" finche' la schermata non veniva ricreata).
  var permissionEpoch by remember { mutableIntStateOf(0) }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, onForegroundChanged) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_START -> onForegroundChanged?.invoke(true)
        Lifecycle.Event.ON_STOP -> onForegroundChanged?.invoke(false)
        Lifecycle.Event.ON_RESUME -> permissionEpoch++
        else -> Unit
      }
    }
    onForegroundChanged?.invoke(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    lifecycle.addObserver(observer)
    onDispose {
      lifecycle.removeObserver(observer)
      onForegroundChanged?.invoke(false)
    }
  }
  val micGranted = remember(permissionEpoch) { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    permissionEpoch++
    if (granted) overlay.openVoice()
  }

  // L'assistente e' la parte dell'app che si usa senza guardarla: si sente sotto il dito.
  LaunchedEffect(micLevel.speaking) {
    tracker.onSpeaking(micLevel.speaking)?.let { if (haptics) fluidHaptics.play(it) }
  }
  LaunchedEffect(state) {
    tracker.onState(state).forEach { if (haptics) fluidHaptics.play(it) }
  }

  // Lettura ad alta voce: solo per le domande fatte a voce, solo se l'utente l'ha accesa. La voce
  // si azzera all'inizio di una domanda, non a ogni aggiornamento dello stato: con la chiave su
  // `state` questo effetto ripartiva a ogni frame audio e fermava la voce cinquanta volte al secondo.
  val speakerReset = state is AssistantState.Listening || state is AssistantState.Classifying || state == AssistantState.Transcribing
  LaunchedEffect(speakerReset) {
    if (speakerReset) speaker?.restart()
  }
  LaunchedEffect(state, speakReplies, lastMode) {
    val speak = speakReplies && lastMode == AskMode.VOICE
    when (val s = state) {
      is AssistantState.Answering -> if (speak) speaker?.speakNewSentences(s.partial, final = false)
      is AssistantState.Done -> if (speak) speaker?.speakNewSentences(s.answer, final = true)
      is AssistantState.Cancelled, is AssistantState.Failed, AssistantState.Idle -> speaker?.stop()
      else -> Unit
    }
  }

  // A risposta finita la barra torna: la conversazione continua, e chi ha scritto una volta
  // scrivera' ancora. Non durante il lavoro, che e' il momento in cui doveva sparire.
  LaunchedEffect(state is AssistantState.Done) {
    if (state is AssistantState.Done && !overlay.collapsed) overlay.resumeComposing()
  }

  // Lo stato che nasce altrove (una domanda dalla notifica, una risposta arrivata ad app chiusa)
  // apre l'overlay da solo.
  LaunchedEffect(overlay.suppressed) {
    if (overlay.suppressed) overlay.hide()
  }
  LaunchedEffect(state, overlay.suppressed) {
    if (overlay.suppressed) return@LaunchedEffect
    if (state is AssistantState.Listening && overlay.mode == FluidOverlayMode.HIDDEN) overlay.openVoice()
    if (state.isBusy && overlay.mode == FluidOverlayMode.HIDDEN) overlay.mode = FluidOverlayMode.TEXT
    if (FluidAssistantTexts.wantsExpanded(state) && overlay.mode != FluidOverlayMode.HIDDEN) overlay.collapsed = false
    if (state == AssistantState.HeardNothing) {
      delay(HeardNothingMillis)
      if (currentState == AssistantState.HeardNothing) {
        actions.onDismiss()
        if (overlay.mode == FluidOverlayMode.VOICE) overlay.hide()
      }
    }
  }

  val visible = overlay.mode != FluidOverlayMode.HIDDEN && !overlay.suppressed
  val mood = when {
    !visible -> FluidHaloMood.HIDDEN
    state is AssistantState.Listening -> FluidHaloMood.LISTENING
    state is AssistantState.Answering -> FluidHaloMood.WRITING
    state is AssistantState.Failed -> FluidHaloMood.ERROR
    state is AssistantState.Done -> FluidHaloMood.DONE
    state.isBusy -> FluidHaloMood.WORKING
    else -> FluidHaloMood.DONE
  }
  val level = if (state is AssistantState.Listening) micLevel.level else 0f

  FluidAssistantHalo(
    mood = if (overlay.collapsed) FluidHaloMood.HIDDEN else mood,
    level = level,
    colours = colours,
    modifier = Modifier.align(Alignment.TopCenter),
    height = if (overlay.mode == FluidOverlayMode.VOICE) 150.dp else 96.dp,
  )

  // La trasformazione, invece di una comparsa: il pannello **cresce dal tasto** sopra la pillola e
  // ci rientra chiudendosi. E' la stessa idea del tasto che diventa il proprio pop-up, che nel resto
  // dell'app fa il modale di vetro; qui non passa dal modale dell'engine perche' l'assistente per
  // scelta non e' modale (la pagina resta viva e toccabile sotto).
  val morph = remember { Animatable(0f) }
  LaunchedEffect(visible) {
    morph.animateTo(
      targetValue = if (visible) 1f else 0f,
      animationSpec = spring(
        dampingRatio = if (visible) FluidMotion.DampingFluid else FluidMotion.DampingChrome,
        stiffness = if (visible) FluidMotion.ResponseStandard else FluidMotion.ResponseSnappy,
      ),
    )
  }
  var panelBounds by remember { mutableStateOf<Rect?>(null) }

  if (visible || morph.value > 0.001f) {
    val drag = remember { Animatable(0f) }
    // In dp, non in pixel grezzi: su uno schermo denso 160 px erano mezzo centimetro, e la card
    // si chiudeva quasi per sbaglio.
    val dismissDragPx = with(LocalDensity.current) { dismissDrag.toPx() }
    val origin = overlay.originBounds
    val panel = panelBounds
    Column(
      Modifier
        .align(Alignment.TopCenter)
        .statusBarsPadding()
        .imePadding()
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .fillMaxWidth()
        .onGloballyPositioned { panelBounds = it.boundsInRoot() }
        .graphicsLayer {
          translationY = drag.value
          val progress = morph.value
          if (origin == null || panel == null || panel.width <= 0f || panel.height <= 0f) {
            // Senza la geometria del tasto (l'assistente aperto da una scorciatoia, il tasto non
            // ancora misurato) resta una comparsa onesta, non un salto: sale e sfuma.
            alpha = progress
            translationY += (1f - progress) * -(panel?.height ?: 0f) * 0.25f
            return@graphicsLayer
          }
          // Il pannello si rimpicciolisce fino alla misura del tasto e si sposta fino al suo centro:
          // al progresso zero i due sono la stessa cosa, e in mezzo c'e' una sola forma che viaggia.
          val scaleTo = (origin.width / panel.width).coerceIn(0.05f, 1f)
          val scaleToY = (origin.height / panel.height).coerceIn(0.05f, 1f)
          scaleX = scaleTo + (1f - scaleTo) * progress
          scaleY = scaleToY + (1f - scaleToY) * progress
          translationX = (origin.center.x - panel.center.x) * (1f - progress)
          translationY += (origin.center.y - panel.center.y) * (1f - progress)
          // Il contenuto arriva dopo la forma: mentre il pannello e' ancora schiacciato, il testo
          // dentro sarebbe illeggibile e si vedrebbe solo la sua deformazione.
          alpha = ((progress - 0.25f) / 0.55f).coerceIn(0f, 1f)
        }
        .pointerInput(overlay.mode, state.isBusy) {
          detectVerticalDragGestures(
            onVerticalDrag = { change, delta ->
              change.consume()
              scope.launch { drag.snapTo((drag.value + delta).coerceAtMost(0f)) }
            },
            onDragEnd = {
              if (drag.value < -dismissDragPx) {
                if (state.isBusy) actions.onCancel()
                actions.onDismiss()
                overlay.hide()
              }
              scope.launch { drag.animateTo(0f, spring(FluidMotion.DampingStandard, FluidMotion.ResponseSnappy)) }
            },
            onDragCancel = { scope.launch { drag.animateTo(0f) } },
          )
        },
    ) {
      if (overlay.mode == FluidOverlayMode.TEXT && overlay.composing && !overlay.collapsed) {
        FluidAssistantTextBar(
          backdrop = backdrop,
          autoFocus = overlay.autoFocus,
          busy = state.isBusy,
          micAvailable = micGranted,
          strings = strings,
          onSend = {
            actions.onAskText(it)
            overlay.sent()
          },
          // Solo `openVoice`: a far partire l'ascolto e' l'effetto che consuma il gettone, e
          // chiamarlo anche qui apriva due catture sullo stesso microfono.
          onVoice = { overlay.openVoice() },
          onNewConversation = actions.onNewConversation,
        )
        Spacer(Modifier.padding(4.dp))
      }
      if (overlay.mode == FluidOverlayMode.VOICE && !overlay.collapsed && state is AssistantState.Listening) {
        FluidListeningHint(
          backdrop = backdrop,
          elapsedMillis = state.elapsedMillis,
          onStop = actions.onStopListening,
          strings = strings,
        )
      }
      val showCard = state != AssistantState.Idle && !(overlay.mode == FluidOverlayMode.VOICE && state is AssistantState.Listening)
      if (showCard) {
        if (overlay.collapsed) {
          FluidCollapsedPill(
            text = FluidAssistantTexts.collapsed(state, strings),
            backdrop = backdrop,
            onExpand = { overlay.collapsed = false },
            error = state is AssistantState.Failed,
          )
        } else {
          FluidAssistantCard(
            state = state,
            pending = pending,
            backdrop = backdrop,
            strings = strings,
            chipLabel = chipLabel,
            maxBodyHeight = card.maxBodyHeight,
            tapHaptic = card.tapHaptic,
            showSwitchLine = card.showSwitchLine,
            onDetails = card.onDetails,
            header = card.header,
            footer = card.footer,
            body = card.body,
            onStop = actions.onCancel,
            onChip = { chip ->
              if (collapseOnChip(chip)) overlay.collapseForNavigation()
              actions.onChip(chip)
            },
            onConfirm = { id, yes ->
              if (haptics) fluidHaptics.play(if (yes) FluidHapticEvent.ActionConfirmed else FluidHapticEvent.Reject)
              actions.onConfirm(id, yes)
            },
            onTapBody = { speaker?.stop() },
          )
        }
      }
    }
  }

  // Ogni tocco del tasto lascia un gettone, e questo effetto e' l'unico che lo consuma: cosi' una
  // ricomposizione non fa ripartire un ascolto che nessuno ha chiesto, e due catture non si
  // contendono il microfono.
  LaunchedEffect(overlay.voiceRequest) {
    if (overlay.mode != FluidOverlayMode.VOICE) return@LaunchedEffect
    if (currentState.isBusy) return@LaunchedEffect
    if (!overlay.consumeVoiceRequest()) return@LaunchedEffect
    if (micGranted) {
      actions.onAskVoice()
    } else {
      overlay.openText()
      micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }
}

/** Quanto resta "non ho sentito niente" prima di chiudersi da solo. */
private const val HeardNothingMillis = 1_600L
