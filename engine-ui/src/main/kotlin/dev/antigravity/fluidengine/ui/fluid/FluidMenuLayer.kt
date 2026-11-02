package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * I due layer nuovi dell'host dei modali (3.0, fase 7): i MENU (contestuale ed Expand con azioni) e
 * l'AVVISO. Stanno nell'host perche' li' il vetro e' vero — la pagina rifratta, non dipinta — e
 * condividono con il menu che si trasforma tutto quello che si vede: il viaggio ([FluidMenuJourney]),
 * il pannello e la goccia ([FluidGlassMenuPanel]), il materiale (`panelOptics`), l'aptica (Open dal
 * gesto, Tick per voce, Tap alla scelta, niente al congedo).
 */

/**
 * Un menu dell'host: la finestra di Fluid-physics che nasce dal tasto (`Expand`) o da un seme accanto
 * alla riga sollevata (menu contestuale), con dentro [FluidGlassMenuPanel].
 *
 * - **Expand**: il pannello si posa SULL'ancora e si apre sotto il dito; la sagoma parte dal tasto
 *   premuto, col suo materiale, e la sua faccia la segue nella prima parte del viaggio.
 * - **Menu contestuale**: la riga si solleva (una card di vetro sorella, [FluidLiftedPreview]) e il
 *   pannello cresce accanto a lei, da un seme sul perno piu' vicino: per geometria, non per un layer
 *   scalato. Prima il pannello classico scalava il proprio vetro.
 *
 * Il layer non suona niente: l'Open e' del gesto che apre, e solo se apre. Il congedo — scrim, back,
 * una scelta — e' silenzioso (la scelta ha il suo Tap). A dito giu', il congedo scollega il dito.
 */
@Composable
internal fun FluidMenuLayer(entry: FluidGlassModalEntry, backdrop: GlassBackdropState?) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val journey = remember { FluidMenuJourney() }
  val fade = remember { Animatable(0f, 0.001f) }
  val selection = entry.selection
  val exitFinished by remember {
    derivedStateOf { journey.settledClosed && !fade.isRunning && fade.value <= 0.001f }
  }

  // Quello che l'uscita deve ancora mostrare, tenuto dall'ultimo fotogramma aperto: sono valori, e i
  // valori sopravvivono al congedo.
  var lastActions by remember { mutableStateOf(entry.actions) }
  var lastOrigin by remember { mutableStateOf<Rect?>(null) }
  var lastPreview by remember { mutableStateOf(entry.preview) }
  var lastPreviewBounds by remember { mutableStateOf(entry.previewSize) }
  var lastPaneTitle by remember { mutableStateOf(entry.paneTitle) }
  var lastPaneTint by remember { mutableStateOf(entry.paneTint) }
  var lastAnchor by remember { mutableStateOf(entry.menuAnchor) }
  if (entry.visible) {
    lastActions = entry.actions
    lastOrigin = entry.origin()
    lastPreview = entry.preview
    lastPreviewBounds = entry.previewSize
    lastPaneTitle = entry.paneTitle
    lastPaneTint = entry.paneTint
    lastAnchor = entry.menuAnchor
    entry.lifted = true
  }

  // Il verso si decide QUI, nella composizione che vede cambiare `visible`: la velocita' della molla
  // va letta prima che l'effetto vecchio venga cancellato (cancellato, l'Animatable la azzera). Un back
  // a meta' apertura torna indietro da dove si trova, con lo slancio che aveva.
  journey.retarget(if (entry.visible) 1f else 0f)

  LaunchedEffect(entry.visible, reducedMotion) {
    if (entry.visible) {
      launch {
        if (reducedMotion) fade.snapTo(1f) else fade.animateTo(1f, FluidMotion.fadeIn(FluidMenuLayerFadeInMillis))
      }
    } else {
      // Congedo: nessuna scelta in corso, e il dito (se e' ancora giu') non e' piu' del menu.
      selection.cancel()
      entry.menuHandOff?.sink = null
      launch {
        if (reducedMotion) fade.snapTo(0f) else fade.animateTo(0f, FluidMotion.fadeOut(FluidMotion.MenuFadeOutMillis))
      }
    }
    journey.run(reducedMotion)
  }

  if (!entry.visible && exitFinished) {
    // La riga e il tasto tornano alla pagina qui, e non un fotogramma prima: fino ad ora c'era la
    // sagoma che li stava riportando a casa.
    SideEffect {
      entry.lifted = false
      lastAnchor?.hidden = false
    }
    return
  }

  FluidMenuLayerContent(
    entry = entry,
    backdrop = backdrop,
    journey = journey,
    fade = { fade.value },
    actions = lastActions,
    origin = lastOrigin,
    preview = lastPreview,
    previewBounds = lastPreviewBounds,
    paneTitle = lastPaneTitle,
    paneTint = lastPaneTint,
    anchor = if (entry.presentation == FluidGlassModalPresentation.Expand) lastAnchor else null,
  )
}

@Composable
private fun FluidMenuLayerContent(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
  journey: FluidMenuJourney,
  fade: () -> Float,
  actions: List<FluidContextAction>,
  origin: Rect?,
  preview: androidx.compose.ui.graphics.layer.GraphicsLayer?,
  previewBounds: Rect?,
  paneTitle: String?,
  paneTint: Color?,
  anchor: FluidMenuAnchor?,
) {
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val metrics = rememberFluidMenuPanelMetrics()
  val selection = entry.selection
  val physics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  val previewPhysics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  physics.density = density.density
  val current by androidx.compose.runtime.rememberUpdatedState(actions)

  // Una scelta: un colpo (Tap), il congedo, l'azione. Una voce spenta o un menu gia' in uscita: niente.
  val pick: (Int) -> Unit = remember(entry, haptics) {
    { index ->
      val action = current.getOrNull(index)
      if (entry.visible && action != null && action.enabled) {
        haptics.play(FluidHapticEvent.Tap)
        entry.onDismissRequest()
        action.onClick()
      }
    }
  }
  SideEffect {
    selection.touchSlop = touchSlop
    selection.onTick = { haptics.play(FluidHapticEvent.Tick) }
    selection.onPick = pick
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(entry.visible) {
    if (entry.visible) runCatching { focusRequester.requestFocus() }
  }

  // Il back predittivo arretra lo scrim; completato, congeda — e il viaggio torna indietro da dove si
  // trova, non dal pannello pieno.
  var backProgress by remember { mutableFloatStateOf(0f) }
  PredictiveBackHandler(enabled = entry.visible) { events ->
    try {
      events.collect { backProgress = it.progress.coerceIn(0f, 1f) }
      backProgress = 0f
      entry.onDismissRequest()
    } catch (_: CancellationException) {
      backProgress = 0f
    }
  }

  val scrimGlass = rememberGlassBackdrop()
  val paneGlass = rememberGlassBackdrop()
  val noBackdrop = rememberEmptyGlassBackdrop()
  val dark = GlassDefaults.isDarkSurface()
  val panelTint = GlassDefaults.panelTint()
  // La pellicola del pannello con dentro la quota di scrim: la finestra campiona la pagina CRUDA (gli
  // exports dello scrim lo registrano a forza piena, la terza trappola), quindi lo scurimento se lo
  // dipinge da se'. Lo stesso nero dello scrim, composto sotto la pellicola.
  val windowTint = remember(panelTint, dark) {
    panelTint.copy(overlay = panelTint.overlay.compositeOver(Color.Black.copy(alpha = if (dark) 0.20f else 0.12f)))
  }
  val progress: () -> Float = { journey.progress.value }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { isTraversalGroup = true }
      .focusGroup(),
  ) {
    FluidGlassModalScrim(
      backdrop = backdrop,
      exports = scrimGlass,
      intensity = { fade() * (1f - backProgress * 0.6f) },
      interactive = entry.visible,
      onDismiss = entry.onDismissRequest,
    )
    Box(
      modifier = Modifier
        .size(1.dp)
        .focusRequester(focusRequester)
        .focusable(),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val host = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      val layout = remember(actions, metrics) { fluidMenuPanelLayout(actions.map { it.startsSection }, metrics) }
      val anchorRect = anchor?.restBounds ?: origin ?: Rect(host.width / 2f, host.height / 2f, host.width / 2f, host.height / 2f)
      val panel = solveFluidMenuPanel(
        anchor = anchorRect,
        host = host,
        width = metrics.widthPx,
        height = layout.height,
        gapPx = metrics.gapPx,
        marginPx = metrics.marginPx,
        placement = if (anchor != null) FluidMenuPlacement.OverAnchor else FluidMenuPlacement.BesideAnchor,
      )
      // Un'apertura si prepara una volta (la sequenza la distingue dalla riapertura e dalle
      // ricomposizioni); in uscita non si tocca niente: il viaggio torna dove era partito.
      val prepared = remember { intArrayOf(-1) }
      // Dove il dito ha tenuto premuto, fermato all'apertura: la goccia del contestuale nasce li' sotto
      // e il menu ci torna. Seguire il dito mentre scorre sulle voci sposterebbe la partenza in volo.
      val touch = remember { floatArrayOf(Float.NaN) }
      if (entry.visible) {
        val fresh = prepared[0] != entry.openedAt
        prepared[0] = entry.openedAt
        if (fresh) touch[0] = entry.menuHandOff?.takeIf { it.pointerDown }?.lastRoot?.x ?: Float.NaN
        journey.prepare(
          anchor = anchor,
          hostOrigin = Offset.Zero,
          panel = panel,
          radius = metrics.radiusPx,
          inflation = fluidMenuInflation(panel.width, panel.height, host.width, metrics.edgeMinPx, metrics.refHeightPx),
          // Il contestuale nasce da una goccia appesa alla riga, sotto il dito (3.0, il vetro liquido):
          // il corpo esce dalla riga e la goccia resta un istante a svuotarsi, col collo fra le due.
          seed = if (anchor == null) {
            fluidMenuDropSeed(
              row = anchorRect,
              panel = panel,
              touchX = touch[0].takeIf { !it.isNaN() },
              diameterPx = with(density) { FluidMenuDropSeedDiameter.toPx() },
              overlapPx = with(density) { FluidMenuDropSeedOverlap.toPx() },
            )
          } else {
            null
          },
          freshStart = fresh && journey.progress.value <= 0.001f,
        )
        journey.install(physics)
        val enabled = actions.map { it.enabled }
        val g = selection.geometry
        if (g == null || g.panel != panel || g.enabled != enabled) {
          // Subito, prima del layout del pannello: il dito passato si muove gia' adesso.
          selection.geometry = fluidMenuGeometryOf(layout, enabled, panel)
        }
      }

      if (anchor == null) {
        FluidLiftedPreview(
          preview = preview,
          bounds = previewBounds,
          paneTint = paneTint,
          backdrop = backdrop,
          physics = previewPhysics,
          progress = progress,
          presence = fade,
          swells = true,
        )
      }

      // Expand: la sagoma E' il tasto alla partenza e al ritorno, a piena forza (il tasto vero e'
      // nascosto). Contestuale: la goccia esce dalla riga mentre si accende e, al ritorno, si spegne
      // mentre la riga la riassorbe — mai un seme fermo che sfuma da solo (era il rettangolo pallido
      // che restava a mezz'aria dopo la chiusura).
      val windowAmount: () -> Float = if (anchor != null) ({ 1f }) else ({ fade() * fluidMenuDropPresence(progress()) })
      Box(
        modifier = Modifier
          .fillMaxSize()
          .drawWithContent { if (windowAmount() > 0.004f) drawContent() }
          .fluidPhysicsSurface(
            state = physics,
            backdrop = backdrop ?: noBackdrop,
            tint = windowTint,
            role = GlassRole.Modal,
            optics = GlassDefaults.panelOptics(compact = true),
            tier = if (backdrop == null) FluidPhysicsTier.Lite else null,
            intensity = windowAmount,
            tintFrom = anchor?.tintFrom,
            tintBlend = { fluidMenuMaterialBlend(progress()) },
            opticsFrom = anchor?.opticsFrom,
            opticsBlend = { fluidMenuMaterialBlend(progress()) },
            exports = if (backdrop != null) paneGlass else null,
          ),
      )

      if (anchor != null) {
        val rest = anchor.restBounds
        Box(
          modifier = Modifier
            .offset { IntOffset(rest.left.roundToInt(), rest.top.roundToInt()) }
            .size(
              width = with(density) { rest.width.toDp() },
              height = with(density) { rest.height.toDp() },
            )
            .graphicsLayer {
              alpha = fluidMenuFaceAlpha(progress())
              val map = fluidMenuContentMap(target = rest, current = physics.contentBoundsNow())
              translationX = map.translation.x
              translationY = map.translation.y
              scaleX = map.scale
              scaleY = map.scale
            },
          contentAlignment = Alignment.Center,
        ) {
          anchor.face?.invoke()
        }
      }

      FluidGlassMenuPanel(
        actions = actions,
        selection = selection,
        layout = layout,
        panelMaterial = if (backdrop != null) paneGlass else null,
        pageBackdrop = backdrop,
        contentAlpha = { fluidMenuContentAlpha(progress(), closing = journey.closing) },
        contentMap = { fluidMenuContentMap(target = panel, current = physics.contentBoundsNow(), minScale = FluidMenuLiquidContentMinScale) },
        onPick = pick,
        modifier = Modifier
          .offset { IntOffset(panel.left.roundToInt(), panel.top.roundToInt()) }
          .size(
            width = with(density) { panel.width.toDp() },
            height = with(density) { panel.height.toDp() },
          ),
        paneTitle = paneTitle ?: FluidMenuDefaultPaneTitle,
        // Il pannello e' posato all'intero piu' vicino: il ritaglio parla le stesse coordinate.
        travelClip = {
          physics.travelClipIn(
            Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom),
            progress(),
          )
        },
      )
    }
  }
}

// --- L'avviso nell'host ------------------------------------------------------------------------

/** Quello che un [FluidAlert] nell'host deve mostrare, anche dopo che il chiamante l'ha tolto. */
@Immutable
internal class FluidAlertSpec(
  val title: String,
  val message: String?,
  val actions: List<FluidAlertAction>,
  val content: (@Composable ColumnScope.() -> Unit)?,
  val modifier: Modifier,
)

/**
 * [FluidAlert] dentro l'host (3.0, fase 7): vetro vero, la pagina rifratta dietro invece del vetro
 * dipinto di una finestra di piattaforma.
 *
 * - **Arrivo** come iOS: la sagoma parte dal pannello gonfiato dell'8% attorno al centro e si posa
 *   ([FluidMotion.snappy]), il materiale entra in 140 ms; il contenuto segue la sagoma in scala
 *   uniforme. **Uscita** in 160 ms, la sagoma che scende al 96% mentre tutto si spegne.
 * - **Pulsanti** con la stessa scelta dei menu: la goccia che segue il dito di pulsante in pulsante
 *   (in orizzontale fra due affiancati, in verticale nella pila), un Tick per pulsante, un Tap alla
 *   scelta e poi l'azione.
 * - Scrim e back chiamano `onDismissRequest`, come il Dialog; il titolo e' il titolo del pannello per
 *   TalkBack; il fuoco resta dentro.
 * - **Vita**: il chiamante lo toglie dalla composizione e l'avviso esce lo stesso, fino in fondo
 *   (`retire` / `finishExit`).
 */
@Composable
internal fun FluidAlertLayer(
  entry: FluidGlassModalEntry,
  spec: FluidAlertSpec,
  backdrop: GlassBackdropState?,
  host: FluidGlassModalHostState,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val geometry = remember { Animatable(0f, 0.001f) }
  val fade = remember { Animatable(0f, 0.001f) }
  val exitFinished by remember {
    derivedStateOf { fade.value <= 0.001f && !fade.isRunning && !geometry.isRunning }
  }

  LaunchedEffect(entry.visible, reducedMotion) {
    if (entry.visible) {
      if (reducedMotion) {
        fade.snapTo(1f)
        geometry.snapTo(1f)
        return@LaunchedEffect
      }
      launch { fade.animateTo(1f, FluidMotion.fadeIn(FluidAlertFadeInMillis)) }
      geometry.animateTo(1f, FluidMotion.snappy())
    } else {
      entry.selection.cancel()
      if (reducedMotion) {
        fade.snapTo(0f)
        geometry.snapTo(0f)
        return@LaunchedEffect
      }
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidAlertExitMillis)) }
      // La stessa curva e la stessa durata della dissolvenza: l'avviso se ne va in un gesto solo.
      geometry.animateTo(0f, FluidMotion.fadeOut(FluidAlertExitMillis))
    }
  }

  if (!entry.visible && exitFinished) {
    SideEffect { if (entry.retiring) host.finishExit(entry) }
    return
  }

  FluidAlertLayerContent(
    entry = entry,
    spec = spec,
    backdrop = backdrop,
    geometry = { geometry.value },
    fade = { fade.value },
  )
}

/** Il rettangolo dell'avviso, scritto dalla misura e letto dal disegno e dalla geometria dei pulsanti. */
private class FluidAlertPane {
  var rect: Rect = Rect.Zero
}

@Composable
private fun FluidAlertLayerContent(
  entry: FluidGlassModalEntry,
  spec: FluidAlertSpec,
  backdrop: GlassBackdropState?,
  geometry: () -> Float,
  fade: () -> Float,
) {
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val selection = entry.selection
  val physics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  physics.density = density.density
  val pane = remember { FluidAlertPane() }
  val alertGlass = rememberGlassBackdrop()
  val scrimGlass = rememberGlassBackdrop()
  val noBackdrop = rememberEmptyGlassBackdrop()
  val dark = GlassDefaults.isDarkSurface()
  val panelTint = GlassDefaults.panelTint()
  val windowTint = remember(panelTint, dark) {
    panelTint.copy(overlay = panelTint.overlay.compositeOver(Color.Black.copy(alpha = if (dark) 0.20f else 0.12f)))
  }
  val current by androidx.compose.runtime.rememberUpdatedState(spec)

  val pick: (Int) -> Unit = remember(entry, haptics) {
    { index ->
      val action = current.actions.getOrNull(index)
      if (entry.visible && action != null && action.enabled) {
        haptics.play(FluidHapticEvent.Tap)
        action.onClick()
      }
    }
  }
  SideEffect {
    selection.touchSlop = touchSlop
    selection.onTick = { haptics.play(FluidHapticEvent.Tick) }
    selection.onPick = pick
  }

  BackHandler(enabled = entry.visible) { entry.onDismissRequest() }
  // Il fuoco entra nella card, non in un segnaposto: e' un gruppo, quindi lo prende il suo primo
  // figlio che lo vuole — il campo di un `FluidPromptAlert` (e con lui la tastiera), un pulsante con
  // la tastiera fisica. Un fotogramma dopo: il contenuto e' composto nell'host, non dal chiamante.
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(entry.visible) {
    if (entry.visible) {
      withFrameNanos { }
      runCatching { focusRequester.requestFocus() }
    }
  }

  // La sagoma: gonfiata dell'8% che si posa all'arrivo, al 96% che si spegne all'uscita. Il contenuto
  // la segue in scala uniforme, come le voci di un menu.
  val exiting = !entry.visible
  val radiusPx = with(density) { FluidRadius.Card.toPx() }
  val marginPx = with(density) { FluidAlertMargin.toPx() }
  val maxWidthPx = with(density) { FluidAlertWidth.toPx() }
  val contentMap: () -> FluidMenuContentMap = {
    fluidMenuContentMap(target = pane.rect, current = physics.contentBoundsNow(), minScale = 0.9f, maxScale = 1.1f)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { isTraversalGroup = true }
      .focusGroup(),
  ) {
    FluidGlassModalScrim(
      backdrop = backdrop,
      exports = scrimGlass,
      intensity = fade,
      interactive = entry.visible,
      onDismiss = entry.onDismissRequest,
    )
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawWithContent { if (fade() > 0.004f) drawContent() }
        .fluidPhysicsSurface(
          state = physics,
          backdrop = backdrop ?: noBackdrop,
          tint = windowTint,
          role = GlassRole.Modal,
          optics = GlassDefaults.panelOptics(),
          tier = if (backdrop == null) FluidPhysicsTier.Lite else null,
          intensity = fade,
          exports = if (backdrop != null) alertGlass else null,
        ),
    )

    Layout(
      // Sopra la tastiera: un avviso con un campo (il prompt) non deve finirle sotto. Solo in basso,
      // quindi l'origine resta quella dell'host e la sagoma e la card parlano le stesse coordinate.
      modifier = Modifier.fillMaxSize().imePadding(),
      content = {
        FluidAlertCard(
          spec = spec,
          selection = selection,
          focusRequester = focusRequester,
          pane = pane,
          material = if (backdrop != null) alertGlass else null,
          backdrop = backdrop,
          fade = fade,
          contentMap = contentMap,
          onPick = pick,
        )
      },
    ) { measurables, constraints ->
      val w = min(maxWidthPx, constraints.maxWidth - marginPx * 2f).roundToInt().coerceAtLeast(1)
      val maxH = (constraints.maxHeight * FluidAlertMaxHeightFraction).roundToInt().coerceAtLeast(1)
      val placeable = measurables.first().measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = maxH))
      val x = (constraints.maxWidth - placeable.width) / 2f
      val y = (constraints.maxHeight - placeable.height) / 2f
      val rect = Rect(x, y, x + placeable.width, y + placeable.height)
      pane.rect = rect
      val seedScale = if (exiting) FluidAlertExitScale else FluidAlertEnterScale
      val seed = fluidMenuSeedFrame(rect, rect.center, seedScale)
      physics.driveExternally(
        from = FluidForm.Slab(seed, FluidCornerRadii.all(radiusPx * seedScale)),
        to = FluidForm.Slab(rect, FluidCornerRadii.all(radiusPx)),
        progress = geometry,
      )
      layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(x.roundToInt(), y.roundToInt())
      }
    }
  }
}

/**
 * La card dell'avviso, alla posa finale: la goccia (sorella, con le sue trasformazioni nel proprio
 * layer) e il contenuto (che segue la sagoma nel suo `graphicsLayer`). I pulsanti sono celle di una
 * griglia che arriva ai bordi — due affiancati, tre o piu' in pila, come UIKit — separati da righe
 * sottili.
 */
@Composable
private fun FluidAlertCard(
  spec: FluidAlertSpec,
  selection: FluidMenuSelection,
  focusRequester: FocusRequester,
  pane: FluidAlertPane,
  material: GlassBackdropState?,
  backdrop: GlassBackdropState?,
  fade: () -> Float,
  contentMap: () -> FluidMenuContentMap,
  onPick: (Int) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val count = spec.actions.size
  val side = count == 2
  val drop = rememberFluidMenuDropState(selection, count)
  val card = remember { FluidMenuCoordinates() }
  val buttons = remember(count) { arrayOfNulls<LayoutCoordinates>(count) }
  val area = remember { FluidMenuCoordinates() }
  var dropCell by remember { mutableStateOf(Size.Zero) }
  val enabled = remember(spec.actions) { spec.actions.map { it.enabled } }

  // Le celle in pixel della radice alla posa finale: i pulsanti misurati DENTRO la card (quindi senza
  // la scala del viaggio, che e' fra la card e la pagina) e portati sul rettangolo dell'avviso.
  fun refresh() {
    val c = card.value ?: return
    if (!c.isAttached || count == 0) return
    val origin = pane.rect.topLeft
    val cells = ArrayList<Rect>(count)
    for (b in buttons) {
      if (b == null || !b.isAttached) return
      cells += c.localBoundingBoxOf(b, clipBounds = false).translate(origin)
    }
    val g = FluidMenuGeometry(cells, enabled, pane.rect)
    if (selection.geometry != g) selection.geometry = g
    val size = cells[0].size
    if (size != dropCell) dropCell = size
  }

  Box(modifier = if (material != null) Modifier.glassContainer(material) else Modifier) {
    if (backdrop != null && count > 0) {
      Box(modifier = Modifier.matchParentSize()) {
        FluidMenuDrop(
          state = drop,
          selection = selection,
          backdrop = backdrop,
          cellSize = { dropCell },
          vertical = !side,
          contentAlpha = fade,
          contentMap = contentMap,
        )
      }
    }
    Column(
      modifier = Modifier
        .graphicsLayer {
          alpha = fade().coerceIn(0f, 1f)
          val map = contentMap()
          translationX = map.translation.x
          translationY = map.translation.y
          scaleX = map.scale
          scaleY = map.scale
        }
        // Il fondo lo disegna la superficie sotto (senza fondale, il suo ripiego opaco): qui solo il
        // contenuto.
        .then(spec.modifier)
        .onGloballyPositioned {
          card.value = it
          refresh()
        }
        .semantics { paneTitle = spec.title }
        .focusRequester(focusRequester)
        .focusGroup(),
    ) {
      // Il messaggio scorre nello spazio che i pulsanti lasciano: i pulsanti sono l'unica uscita di
      // un avviso e non devono mai finire fuori dallo schermo.
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f, fill = false)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp)
          .padding(top = 20.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Text(
          text = spec.title,
          style = MaterialTheme.typography.titleMedium,
          textAlign = TextAlign.Center,
          color = scheme.onSurface,
        )
        spec.message?.takeIf { it.isNotBlank() }?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = scheme.onSurface,
          )
        }
        spec.content?.invoke(this)
      }

      val hairline = scheme.onSurface.copy(alpha = 0.12f)
      val areaModifier = Modifier
        .fillMaxWidth()
        .onGloballyPositioned { area.value = it }
        .fluidMenuPointerArea(selection, area)
      if (side) {
        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
        Row(modifier = areaModifier.height(IntrinsicSize.Min)) {
          FluidAlertButton(spec.actions[0], { drop.weight(0) }, { onPick(0) }, Modifier.weight(1f)) {
            buttons[0] = it
            refresh()
          }
          Box(modifier = Modifier.width(0.5.dp).fillMaxHeight().background(hairline))
          FluidAlertButton(spec.actions[1], { drop.weight(1) }, { onPick(1) }, Modifier.weight(1f)) {
            buttons[1] = it
            refresh()
          }
        }
      } else {
        Column(modifier = areaModifier) {
          spec.actions.forEachIndexed { index, action ->
            Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(hairline))
            FluidAlertButton(action, { drop.weight(index) }, { onPick(index) }, Modifier.fillMaxWidth()) {
              buttons[index] = it
              refresh()
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FluidAlertButton(
  action: FluidAlertAction,
  weight: () -> Float,
  onClick: () -> Unit,
  modifier: Modifier,
  onPlaced: (LayoutCoordinates) -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  val base = when (action.emphasis) {
    FluidAlertAction.Emphasis.Destructive -> scheme.error
    else -> scheme.primary
  }
  val color = if (action.enabled) base else base.copy(alpha = base.alpha * 0.35f)
  Box(
    modifier = modifier
      .onGloballyPositioned(onPlaced)
      // TalkBack e tastiera; dal dito sceglie la selezione (l'area consuma il down prima di lui).
      .clickable(
        interactionSource = null,
        indication = null,
        enabled = action.enabled,
        role = Role.Button,
        onClick = onClick,
      )
      .drawWithContent {
        // Il pulsante sotto la goccia si fa avanti di un 5%, dal centro: e' un'etichetta centrata.
        val w = weight()
        if (w > 0.001f) {
          val s = 1f + 0.05f * w
          scale(s, s) { this@drawWithContent.drawContent() }
        } else {
          drawContent()
        }
      }
      .heightIn(min = 44.dp)
      .padding(horizontal = 12.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = action.label,
      style = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = if (action.emphasis == FluidAlertAction.Emphasis.Preferred) FontWeight.SemiBold else FontWeight.Normal,
      ),
      color = color,
      textAlign = TextAlign.Center,
      maxLines = 2,
    )
  }
}

/** Il titolo per TalkBack di un menu che non ne ha dato uno. */
internal const val FluidMenuDefaultPaneTitle = "Azioni"

private const val FluidMenuLayerFadeInMillis = 110
private const val FluidAlertFadeInMillis = 140
private const val FluidAlertExitMillis = 160
private const val FluidAlertEnterScale = 1.08f
private const val FluidAlertExitScale = 0.96f
private const val FluidAlertMaxHeightFraction = 0.86f
private val FluidAlertWidth = 280.dp
private val FluidAlertMargin = 24.dp
