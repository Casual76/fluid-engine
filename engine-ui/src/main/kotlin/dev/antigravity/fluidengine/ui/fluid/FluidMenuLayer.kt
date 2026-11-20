package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.AbsoluteAlignment
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.glassMenuPointOfLight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import androidx.compose.ui.unit.isSpecified
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurface
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsSurfaceInked
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
  val liquidMenu = backdrop != null && fluidMenuUsesField()
  val journey = remember { FluidMenuJourney() }
  val fade = remember { Animatable(0f, 0.001f) }
  val physics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  val selection = entry.selection
  // La nascita del contestuale col vetro liquido (sessione «contestuale»): B1 (b) di Apple, con un orologio suo e la card
  // sulla sua molla. Il menu dal tasto e quello del gruppo non la vedono: il loro viaggio resta quello di `FluidMenuJourney`.
  val birth = remember { FluidContextBirth() }
  val bornLiquid = liquidMenu && !reducedMotion && entry.presentation == FluidGlassModalPresentation.ContextMenu
  // L'uscita aspetta anche la goccia (3.0): un'azione della barra torna al suo tasto solo quando la
  // coda si e' posata su di lui, se no il tasto vero comparirebbe sopra una goccia ancora in moto. Il
  // contestuale a quel punto e' gia' spento (la dissolvenza), e non aspetta niente; la card della nascita, si'.
  val exitFinished by remember {
    derivedStateOf {
      journey.settledClosed && !fade.isRunning && fade.value <= 0.001f &&
        (entry.presentation != FluidGlassModalPresentation.Expand || physics.liquid.quiet) &&
        !birth.card.isRunning && birth.card.value <= 0.001f
    }
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
  var fromGroup by remember { mutableStateOf(false) }
  // F5A.4: il sollevamento del contestuale (scala viva del dito, anteprima, forma), tenuto come gli altri.
  var lastLift by remember { mutableStateOf<FluidContextLift?>(null) }
  if (entry.visible) {
    lastLift = FluidContextLifts.of(entry)
    lastActions = entry.actions
    lastOrigin = entry.origin()
    lastPreview = entry.preview
    lastPreviewBounds = entry.previewSize
    lastPaneTitle = entry.paneTitle
    lastPaneTint = entry.paneTint
    lastAnchor = entry.menuAnchor
    fromGroup = lastAnchor?.groupBounds != null
    entry.lifted = true
  }

  // Il verso si decide QUI, nella composizione che vede cambiare `visible`: la velocita' della molla
  // va letta prima che l'effetto vecchio venga cancellato (cancellato, l'Animatable la azzera). Un back
  // a meta' apertura torna indietro da dove si trova, con lo slancio che aveva.
  // Il contestuale col vetro liquido torna sempre sul contorno, dalla sagoma che si vedeva (S4c, NO 2): la si legge qui,
  // prima di cambiare verso, senza osservarla (non e' una dipendenza della composizione).
  if (bornLiquid && !entry.visible && !journey.closing) {
    birth.noteDrawnAtDismiss(androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation { physics.ensurePlan().bounds })
  }
  // Un congedo dal gesto Indietro parte dalla sagoma che il gesto stava tirando (S4f, NO 5), non dal pannello pieno.
  if (!entry.visible && !journey.closing) {
    androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
      if (journey.backPull.value > 0f) journey.noteBackHome(physics.dropRestSlabNow())
    }
  }
  journey.retarget(if (entry.visible) 1f else 0f, onContour = bornLiquid)

  LaunchedEffect(entry.visible, reducedMotion) {
    // Il velo, la nascita e il congedo sull'orologio costante dei menu (S4f, NO 1): i primi fotogrammi di un menu, a 30 Hz e
    // i piu' cari, non fanno correre la nascita di nascosto. Lo stesso istante per tutti, e per la goccia (1/30 s al piu').
    withContext(fluidSteadyFrames(coroutineContext)) {
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
      when {
        !bornLiquid -> journey.run(reducedMotion, withLens = liquidMenu)
        entry.visible -> birth.open(journey)
        else -> birth.close(journey, reducedMotion)
      }
    }
  }

  if (!entry.visible && exitFinished) {
    // La riga e il tasto tornano alla pagina qui, e non un fotogramma prima: fino ad ora c'era la
    // sagoma che li stava riportando a casa.
    SideEffect {
      entry.lifted = false
      lastAnchor?.consume(false)
    }
    return
  }

  val groupAtHome = fromGroup && !entry.visible && journey.settledClosed && physics.liquid.quiet
  if (groupAtHome && lastAnchor?.hidden == true) SideEffect { lastAnchor?.consume(false) }
  FluidMenuLayerContent(
    entry = entry,
    backdrop = backdrop,
    journey = journey,
    physics = physics,
    // S4f, NO 1: nella nascita dal tasto e dal gruppo il velo non va mai avanti alla goccia. L'animazione del velo parte un
    // fotogramma prima dell'orologio della nascita (lo dice la sonda: in Robolectric il velo al 63% con la nascita a 16 ms), e
    // sul Tab S9 a 30 Hz un fotogramma sono 33 ms: il velo al 60% con la goccia ancora sul tasto (f0004). Qui il velo e' la sua
    // dissolvenza letta sull'orologio della nascita, se e' piu' indietro; a nascita finita, o in chiusura, quella di sempre.
    fade = {
      val f = fade.value
      if (journey.born && !journey.closing) min(f, fluidMenuBirthFade(journey.birthClock.value)) else f
    },
    actions = lastActions,
    origin = lastOrigin,
    preview = lastPreview,
    previewBounds = lastPreviewBounds,
    paneTitle = lastPaneTitle,
    paneTint = lastPaneTint,
    anchor = if (entry.presentation == FluidGlassModalPresentation.Expand) lastAnchor else null,
    lift = lastLift,
    withLens = liquidMenu,
    birth = if (bornLiquid) birth else null,
    groupAtHome = groupAtHome,
  )
}

@Composable
private fun FluidMenuLayerContent(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
  journey: FluidMenuJourney,
  physics: FluidPhysicsState,
  fade: () -> Float,
  actions: List<FluidContextAction>,
  origin: Rect?,
  preview: androidx.compose.ui.graphics.layer.GraphicsLayer?,
  previewBounds: Rect?,
  paneTitle: String?,
  paneTint: Color?,
  anchor: FluidMenuAnchor?,
  lift: FluidContextLift? = null,
  withLens: Boolean,
  /** La nascita del contestuale col vetro liquido, o null: il viaggio dei menu di sempre. */
  birth: FluidContextBirth? = null,
  groupAtHome: Boolean,
) {
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val metrics = rememberFluidMenuPanelMetrics()
  val selection = entry.selection
  val previewPhysics = remember { FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)) }
  physics.density = density.density
  // F5A.3: le voci lette una volta per composizione (il contenuto vivo di `FluidMenuActions` si rilegge qui, e una
  // voce che lascia il menu aperto si aggiorna), l'ordine e la taglia dichiarati, e i livelli del sottomenu.
  val list = fluidMenuSnapshot(actions)
  val order = fluidMenuOrderOf(actions)
  val elementSize = fluidMenuElementSizeOf(actions)
  val levels = remember { FluidMenuLevels() }
  levels.subscribe()
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // Le celle del livello che si vede e il pannello del livello verso cui si va: le scrive la composizione, le leggono
  // la scelta e il cambio di livello.
  val shownCells = remember { arrayOfNulls<List<FluidMenuEntry>>(1) }
  val targetPanel = remember { arrayOfNulls<Rect>(1) }

  // Una scelta: un colpo (Tap), poi secondo la cella. L'intestazione di un sottomenu torna indietro, una voce coi figli
  // apre il sottomenu dentro lo stesso vetro, una voce che lascia il menu aperto fa la sua azione e basta; le altre
  // chiedono il congedo e fanno l'azione, come prima. Una voce spenta, un menu in uscita o a meta' di un cambio di
  // livello: niente.
  val pick: (Int) -> Unit = remember(entry, haptics, levels, scope, reducedMotion) {
    { index ->
      val cell = shownCells[0]?.getOrNull(index)
      if (entry.visible && cell != null && cell.enabled && !levels.swapping) {
        haptics.play(FluidHapticEvent.Tap)
        when {
          cell.kind == FluidMenuCellKind.Header -> levels.change(-1, targetPanel[0], scope, reducedMotion)
          cell.opensSubmenu -> levels.change(cell.source, targetPanel[0], scope, reducedMotion)
          cell.action.keepsMenuOpen -> cell.action.onClick()
          else -> {
            entry.onDismissRequest()
            cell.action.onClick()
          }
        }
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

  // Il back predittivo arretra lo scrim e tira il vetro verso la sorgente col dito (S4f, NO 5: sul Tab S9 il vetro restava
  // fermo fino al rilascio); completato, congeda, e il viaggio torna indietro dalla sagoma tirata. Annullato, torna al
  // pannello con la molla del menu. Lo scrim resta dov'e' al congedo: la dissolvenza parte da li'.
  var backProgress by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(entry.visible) { if (entry.visible) backProgress = 0f }
  PredictiveBackHandler(enabled = entry.visible) { events ->
    try {
      events.collect { event ->
        val p = event.progress.coerceIn(0f, 1f)
        backProgress = p
        if (!reducedMotion) journey.backPull.snapTo(p)
      }
      entry.onDismissRequest()
    } catch (_: CancellationException) {
      scope.launch {
        launch { journey.backPull.animateTo(0f, FluidMotion.dropOpen()) }
        androidx.compose.animation.core.animate(backProgress, 0f, animationSpec = FluidMotion.dropOpen()) { v, _ -> backProgress = v }
      }
    }
  }
  // Il back dentro un sottomenu torna al menu di prima (F5A.3); al primo livello resta quello predittivo, che congeda.
  // Composto dopo il predittivo, e quindi davanti a lui finche' un sottomenu e' aperto.
  BackHandler(enabled = entry.visible && levels.openParent >= 0) {
    if (!levels.swapping) levels.change(-1, targetPanel[0], scope, reducedMotion)
  }

  val scrimGlass = rememberGlassBackdrop()
  val paneGlass = rememberGlassBackdrop()
  val noBackdrop = rememberEmptyGlassBackdrop()
  val dark = GlassDefaults.isDarkSurface()
  val panelTint = GlassDefaults.panelTint()
  // F5A.4: il contestuale ha uno scrim suo, piu' scuro di quello dei menu dal tasto (scelta di Alessio: la
  // pagina dietro scurita con una sfocatura leggera). C è di serie; la manopola `ctxscrim` serve alle catture.
  val scrimLook = if (anchor == null) FluidContextScrimTuning.look else FluidScrimLook.Modal
  // La pellicola del pannello con dentro la quota di scrim: la finestra campiona la pagina CRUDA (gli
  // exports dello scrim lo registrano a forza piena, la terza trappola), quindi lo scurimento se lo
  // dipinge da se'. Lo stesso nero dello scrim, composto sotto la pellicola.
  val windowTint = remember(panelTint, dark, scrimLook) {
    panelTint.copy(overlay = panelTint.overlay.compositeOver(scrimLook.color(dark)))
  }
  val progress: () -> Float = { journey.progress.value }
  val liquidMenu = withLens
  val pointOfLight = LocalFluidMenuPointOfLight.current

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
      look = scrimLook,
      menu = true,
    )
    if (groupAtHome) return@Box // solo il velo finisce la propria dissolvenza dopo la consegna
    Box(
      modifier = Modifier
        .size(1.dp)
        .focusRequester(focusRequester)
        .focusable(),
    )

    val labelWidth = rememberFluidMenuLabelMeasure()
    val subtitleWidth = rememberFluidMenuSubtitleMeasure()
    val structureMetrics = rememberFluidMenuStructureMetrics(metrics, elementSize)
    val densityValue = LocalDensity.current.density
    // I figli si posano in coordinate della finestra, come il vetro: l'allineamento d'inizio in RTL li metteva a destra
    // (x = larghezza dell'host - la loro), e `absoluteOffset` ci aggiungeva il loro `left` (S4c sul Tab S9, ripresa 24).
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
      val host = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
      // Un'apertura si prepara una volta (la sequenza la distingue dalla riapertura e dalle
      // ricomposizioni); in uscita non si tocca niente: il viaggio torna dove era partito. Un'apertura nuova riparte
      // dal primo livello (F5A.3), anche se il menu di prima si era chiuso dentro un sottomenu.
      val prepared = remember { intArrayOf(-1) }
      val fresh = entry.visible && prepared[0] != entry.openedAt
      if (fresh) levels.reset()
      // Le celle del primo livello nell'ordine scritto: l'altezza non dipende dal capovolgimento, quindi il pannello si
      // risolve con queste e l'ordine si sceglie dopo, sapendo da che parte si apre.
      val written = remember(list, elementSize, structureMetrics) {
        fluidMenuStructure(fluidMenuEntries(list, elementSize, reversed = false), structureMetrics)
      }
      // Largo quanto serve alla voce piu' lunga, fino a un tetto (3.0.1): vedi [fluidMenuPanelWidth].
      val panelWidth = remember(written, metrics, labelWidth, subtitleWidth, host.width) {
        fluidMenuLevelWidth(written.entries, metrics, labelWidth, subtitleWidth, densityValue, host.width)
      }
      // F5A.4: il contestuale si apre accanto alla card SOLLEVATA (o all'anteprima personalizzata, alla sua
      // taglia misurata), non accanto alla riga a riposo: la card cresce attorno al centro, e un'anteprima
      // piu' grande dell'elemento lo coprirebbe.
      val customPreview = lift?.preview
      val viewport = if (customPreview != null) {
        val insets = WindowInsets.safeDrawing
        val direction = LocalLayoutDirection.current
        Rect(insets.getLeft(density, direction).toFloat(), insets.getTop(density).toFloat(),
          host.width - insets.getRight(density, direction), host.height - insets.getBottom(density))
      } else Rect(Offset.Zero, host)
      val previewMargins = fluidPreviewMargins(viewport, metrics.marginPx)
      val liftTarget = if (anchor == null && origin != null) {
        val measured = lift?.previewSize
        if (customPreview != null && measured != null) {
          fluidPreviewFit(origin.center, Size(measured.width.toFloat(), measured.height.toFloat()), previewMargins)
        } else fluidLiftedFrame(origin)
      } else null
      val anchorRect = anchor?.restBounds ?: liftTarget ?: origin ?: Rect(host.width / 2f, host.height / 2f, host.width / 2f, host.height / 2f)
      val rootPanel = solveFluidMenuPanel(
        anchor = anchorRect,
        host = host,
        width = panelWidth,
        height = written.height,
        gapPx = metrics.gapPx,
        marginPx = metrics.marginPx,
        placement = if (anchor != null) FluidMenuPlacement.OverAnchor else FluidMenuPlacement.BesideAnchor,
      )
      // L'ancora distingue il menu dal tasto dal contestuale. Se si apre verso l'alto, la priorità mette
      // le prime voci vicino al tasto che ha aperto il menu.
      val upward = fluidMenuOpensUpward(anchorRect, rootPanel)
      val reversed = fluidMenuReverses(order, upward, fromButton = anchor != null)
      val root = if (!reversed) {
        written
      } else {
        remember(list, elementSize, structureMetrics) {
          fluidMenuStructure(fluidMenuEntries(list, elementSize, reversed = true), structureMetrics)
        }
      }
      // Il sottomenu aperto, se c'e' (e se la voce madre ha ancora dei figli: il contenuto vivo puo' averli tolti).
      fun submenuLevel(parentIndex: Int): Pair<FluidMenuStructure, Rect>? {
        val parent = list.getOrNull(parentIndex)?.takeIf { it.children.isNotEmpty() } ?: return null
        val cells = fluidMenuStructure(fluidMenuSubmenuEntries(parent, reversed), structureMetrics)
        val width = fluidMenuLevelWidth(cells.entries, metrics, labelWidth, subtitleWidth, densityValue, host.width)
        return cells to fluidMenuSubmenuPanel(rootPanel, width, cells.height, host, metrics.marginPx, upward)
      }
      // Il pannello disegna il livello verso cui si va da subito; finche' il contenuto cambia, anche quello che se ne va.
      val target = submenuLevel(levels.openParent) ?: (root to rootPanel)
      val (shownStructure, panel) = target
      val leaving = if (levels.swapping) submenuLevel(levels.previousParent) ?: (root to rootPanel) else null
      val destination = target.second
      shownCells[0] = shownStructure.entries
      targetPanel[0] = destination
      // Dove il dito ha tenuto premuto, fermato all'apertura: la goccia del contestuale nasce li' sotto
      // e il menu ci torna. Seguire il dito mentre scorre sulle voci sposterebbe la partenza in volo.
      val touch = remember { floatArrayOf(Float.NaN) }
      if (entry.visible) {
        prepared[0] = entry.openedAt
        if (fresh) touch[0] = entry.menuHandOff?.takeIf { it.pointerDown }?.lastRoot?.x ?: Float.NaN
        journey.prepare(
          anchor = anchor,
          hostOrigin = Offset.Zero,
          // Il pannello verso cui si va: dentro un sottomenu e' il suo, e il congedo riporta a casa quella sagoma.
          panel = destination,
          radius = metrics.radiusPx,
          inflation = fluidDropInflation(destination.width, host.width, metrics.edgeMinPx),
          // Il contestuale nasce da una goccia appesa alla riga, sotto il dito (3.0, il vetro liquido):
          // il corpo esce dalla riga e la goccia resta un istante a svuotarsi, col collo fra le due.
          seed = if (anchor == null) {
            fluidMenuDropSeed(
              row = anchorRect,
              panel = destination,
              touchX = touch[0].takeIf { !it.isNaN() },
              diameterPx = with(density) { FluidMenuDropSeedDiameter.toPx() },
              overlapPx = with(density) { FluidMenuDropSeedOverlap.toPx() },
            )
          } else {
            null
          },
          freshStart = fresh && journey.progress.value <= 0.001f,
          liquidMenu = liquidMenu,
          // Il menu dal tasto e dal gruppo nasce come quelli di Apple (B1 a e la graffetta B1 b): sessione contestuale.
          appleBirth = liquidMenu && anchor != null,
        )
        // Mentre il vetro passa da un livello all'altro (F5A.3) lo guida il cambio di livello, da dove stava al
        // pannello nuovo, con la molla d'andata del menu; finito, torna al viaggio, che a quel punto sta proprio li'.
        val from = levels.fromPanel
        if (levels.morphing && from != null) {
          physics.driveExternally(
            from = FluidForm.Slab(from, FluidCornerRadii.all(metrics.radiusPx)),
            to = FluidForm.Slab(destination, FluidCornerRadii.all(metrics.radiusPx)),
            progress = { levels.morph.value },
          )
          levels.driving = true
        } else {
          installMenuTravel(journey, physics, birth)
          levels.driving = false
        }
        if (anchor?.groupBounds != null) {
          // Al ritorno le icone sono intere (S4f, NO 3): tornano quando il vetro e' la capsula, non ritagliate dalla bolla che
          // si allarga. All'andata il ritaglio della goccia le porta via con la capsula che si stringe.
          anchor.groupClip = { if (journey.closing) null else anchor.groupBounds?.let { physics.travelClipIn(it, progress()) } }
          journey.groupCover = { anchor.groupBounds?.let { fluidMenuGroupFaceCover(physics.ensurePlan().bounds, it) } ?: 1f }
        }
        if (birth != null) {
          birth.radius = metrics.radiusPx
          birth.prepare(fresh = fresh && journey.progress.value <= 0.001f)
        }
        val g = selection.geometry
        val enabled = shownStructure.entries.map { it.enabled }
        if (g == null || g.panel != panel || g.enabled != enabled || g.cells.size != shownStructure.size) {
          // Subito, prima del layout del pannello: il dito passato si muove gia' adesso.
          selection.geometry = shownStructure.geometry(panel)
        }
      } else if (levels.driving) {
        // Congedo a meta' di un cambio di livello: il ritorno lo fa il viaggio, dalla sagoma del livello di arrivo.
        installMenuTravel(journey, physics, birth)
        levels.driving = false
      }

      if (anchor == null && customPreview != null && previewBounds != null) {
        FluidCustomLiftedPreview(
          source = previewBounds, recording = preview, custom = customPreview, shape = lift.previewShape,
          viewport = viewport, margins = previewMargins,
          progress = if (birth != null) ({ birth.card.value }) else progress,
          anchorScale = lift.anchorScale, presence = fade, paneTint = paneTint, backdrop = backdrop,
          onMeasured = { if (lift.previewSize != it) lift.previewSize = it },
        )
      } else if (anchor == null) {
        FluidLiftedPreview(
          preview = preview,
          bounds = previewBounds,
          paneTint = paneTint,
          backdrop = backdrop,
          physics = previewPhysics,
          // Nella nascita del contestuale la card ha la sua molla (la stessa di prima): non rallenta col menu.
          progress = if (birth != null) ({ birth.card.value }) else progress,
          presence = fade,
          swells = true,
          target = liftTarget,
          closing = { !entry.visible },
          anchorScale = lift?.anchorScale ?: { 1f },
          shape = lift?.previewShape,
        )
      }

      // Expand: la sagoma E' il tasto alla partenza e al ritorno, a piena forza (il tasto vero e'
      // nascosto). Contestuale: la goccia esce dalla riga mentre si accende e, al ritorno, si spegne
      // mentre la riga la riassorbe — mai un seme fermo che sfuma da solo (era il rettangolo pallido
      // che restava a mezz'aria dopo la chiusura).
      val windowAmount: () -> Float = when {
        anchor != null -> ({ 1f })
        birth != null -> ({ fade() * birth.presence(journey) })
        else -> ({ fade() * fluidMenuDropPresence(progress()) })
      }
      // F2.3b: il menu possiede le sue voci e scurisce in continuo (un grande, `Modal`); le voci lo seguono.
      val menuInk = rememberGlassInk()
      FluidMenuReturnSurface(journey.controlledReturn) {
        Modifier
          .fillMaxSize()
          .menuDrawMarker("surface")
          .menuBirthMarker(journey, physics)
          .drawWithContent { if (windowAmount() > 0.004f) drawContent() }
          .fluidPhysicsSurfaceInked(
            menuSource = anchor?.groupBounds ?: anchor?.pressedBounds ?: journey.seed,
            ink = menuInk,
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
            // G1 (C-R2): il pannello nasce col lato, la misura e l'inchiostro del tasto.
            adoptFrom = anchor?.ink?.state,
          )
          .then(if (liquidMenu && pointOfLight && android.os.Build.VERSION.SDK_INT >= 33) {
            Modifier.glassMenuPointOfLight(journey, physics)
          } else Modifier)
      }

      if (anchor != null && anchor.groupBounds == null) {
        val rest = anchor.restBounds
        Box(
          modifier = Modifier
            .absoluteOffset { IntOffset(rest.left.roundToInt(), rest.top.roundToInt()) }
            .size(
              width = with(density) { rest.width.toDp() },
              height = with(density) { rest.height.toDp() },
            )
            .graphicsLayer {
              alpha = journey.faceAlpha(physics) * fluidMenuAnchorInkAlpha(anchor)
              val map = fluidMenuContentMap(target = rest, current = physics.contentBoundsNow())
              translationX = map.translation.x
              translationY = map.translation.y
              scaleX = map.scale
              scaleY = map.scale
            },
          contentAlignment = Alignment.Center,
        ) {
          FluidMenuAnchorFace(anchor)
        }
      }
      // Le icone del gruppo al ritorno, sopra il vetro che torna capsula (S4b, NO 4): le registra la barra, qui si disegnano.
      if (anchor != null && anchor.groupBounds != null) {
        Box(Modifier.fillMaxSize().drawWithContent { fluidMenuDrawGroupFaces(anchor) })
      }

      GlassInkContent(menuInk, LocalContentColor.current) {
      val panelInLayout = panel.translate(
        panel.left.roundToInt() - panel.left,
        panel.top.roundToInt() - panel.top,
      )
      val lens = if (liquidMenu) FluidMenuLens(journey, physics, panelInLayout) else null
      // L'opacita' del contenuto prima del cambio di livello: il viaggio (o la lente) e l'inchiostro.
      val baseAlpha: () -> Float = {
        // Nella nascita del contestuale le voci entrano nella goccia quando in B1 (b): a 67 ms la goccia e' solo lente. Nella
        // nascita del menu dal tasto subito (B1 a), in quella dal gruppo come nel contestuale.
        val alpha = if (lens != null) journey.lensInkAlpha() * windowAmount() * (birth?.inkPresence(journey) ?: 1f) else
          fluidMenuContentAlpha(progress(), journey.closing)
        alpha * (menuInk.state?.inkAlpha() ?: 1f)
      }
      val blurAvailable = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
      // Dove si disegnano le voci in questo istante: la stessa mappa per il layer e per il dito che sceglie.
      val contentMapNow: () -> FluidMenuContentMap = {
        if (levels.swapping || levels.morphing) {
          // Il livello sta fermo dove si posa e cambia solo di poco la scala: e' il vetro che cambia forma attorno.
          FluidMenuContentMap(Offset.Zero, if (levels.swapping) fluidMenuLevelEnterScale(levels.swap.value) else 1f)
        } else {
          lens?.contentMap() ?: fluidMenuLiquidContentMap(
            target = panel,
            current = physics.contentBoundsNow(),
            alpha = fluidMenuContentAlpha(progress(), closing = journey.closing) * (menuInk.state?.inkAlpha() ?: 1f),
          )
        }
      }
      // Solo nei test (S4c, NO 2): il menu fotogramma per fotogramma, letto come lo disegna il pannello. Null altrove.
      val probe = FluidMenuLayerProbe.onFrame
      if (probe != null) {
        LaunchedEffect(probe, entry) {
          while (true) {
            withFrameNanos { now ->
              probe(
                FluidMenuProbeFrame(
                  nanos = now,
                  layer = System.identityHashCode(journey),
                  visible = entry.visible,
                  closing = journey.closing,
                  controlledReturn = journey.controlledReturn,
                  progress = journey.progress.value,
                  birthMillis = birth?.millis(),
                  frozen = birth?.frozen?.frame,
                  drawn = physics.ensurePlan().bounds,
                  liquidQuiet = physics.liquid.quiet,
                  window = windowAmount(),
                  contentScale = contentMapNow().scale,
                  inkAlpha = baseAlpha(),
                  backdrop = backdrop,
                  fade = fade(),
                  journeyBirth = journey.birthClock.value,
                  born = journey.born,
                  birthRunning = journey.birthClock.isRunning,
                  progressRunning = journey.progress.isRunning,
                ),
              )
            }
          }
        }
      }
      // Il dito mira alle voci che vede (sessione contestuale, fase tasto): durante la nascita le voci stanno col corpo,
      // staccato dalla barra o rimpicciolito nella goccia; la scelta cerca la voce a riposo sotto il punto riportato indietro.
      // Durante un cambio di livello il dito resta sui riquadri del livello che arriva, com'era.
      SideEffect {
        selection.pointerMap = { root ->
          if (levels.swapping || levels.morphing) root else fluidMenuPointerAtRest(root, panelInLayout, contentMapNow())
        }
      }
      // F5A.3: il livello che se ne va, posato dov'era e disegnato sotto quello che arriva finche' svanisce.
      val outgoing = leaving?.let { (cells, rect) ->
        FluidMenuOutgoingLevel(
          structure = cells,
          offset = IntOffset(rect.left.roundToInt() - panel.left.roundToInt(), rect.top.roundToInt() - panel.top.roundToInt()),
          size = androidx.compose.ui.unit.IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
          tileLabels = elementSize == FluidMenuElementSize.Medium,
          alpha = { baseAlpha() * fluidMenuLevelExitAlpha(levels.swap.value, blurAvailable) },
          scale = { fluidMenuLevelExitScale(levels.swap.value) },
          blurDp = { fluidMenuLevelExitBlur(levels.swap.value) * FluidMenuLevelPeakBlurDp },
        )
      }
      FluidGlassMenuPanel(
        actions = shownStructure.entries.map { it.action },
        selection = selection,
        layout = null,
        panelMaterial = if (backdrop != null) paneGlass else null,
        pageBackdrop = backdrop,
        // F2.3b: la dissolvenza dell'inchiostro attorno al passaggio di lato (le voci stanno in un nodo fratello).
        // F5A.3: e, durante un cambio di livello, quella del livello che arriva.
        contentAlpha = {
          val level = if (levels.swapping) fluidMenuLevelEnterAlpha(levels.swap.value, blurAvailable) else 1f
          baseAlpha() * level
        },
        contentMap = contentMapNow,
        onPick = pick,
        modifier = Modifier
          // In coordinate della finestra come il vetro: `offset` in RTL specchiava le voci dall'altra parte (S4b, NO 5), e il
          // contenitore deve posare in alto a sinistra anche in RTL (S4c).
          .absoluteOffset { IntOffset(panel.left.roundToInt(), panel.top.roundToInt()) }
          .menuDrawMarker("items")
          .size(
            width = with(density) { panel.width.toDp() },
            height = with(density) { panel.height.toDp() },
          ),
        paneTitle = paneTitle ?: LocalFluidLabels.current.actions,
        lens = lens,
        // Il pannello e' posato all'intero piu' vicino: il ritaglio parla le stesse coordinate. Mentre il vetro passa da
        // un livello all'altro il ritaglio segue la sua sagoma anche a viaggio d'apertura finito.
        travelClip = {
          physics.travelClipIn(
            Rect(panel.left.roundToInt().toFloat(), panel.top.roundToInt().toFloat(), panel.right, panel.bottom),
            if (levels.morphing) levels.morph.value.coerceAtMost(FluidMenuLevelClipCeiling) else progress(),
          )
        },
        structure = shownStructure,
        tileLabels = elementSize == FluidMenuElementSize.Medium,
        levelBlurDp = { if (levels.swapping) fluidMenuLevelEnterBlur(levels.swap.value) * FluidMenuLevelPeakBlurDp else 0f },
        outgoing = outgoing,
      )
      }
    }
  }
}

/**
 * La nascita e il ritorno sul dispositivo, a ogni fotogramma disegnato (S4g, NO 2): l'orologio della nascita, il progresso, la
 * sagoma disegnata e quella che la goccia insegue. Con `setprop log.tag.FluidGlassFrame VERBOSE`, come gli altri marcatori.
 */
internal fun Modifier.menuBirthMarker(journey: FluidMenuJourney, physics: FluidPhysicsState): Modifier =
  if (GlassFrameDiagnostics.enabled) {
    drawWithContent {
      GlassFrameDiagnostics.event("menu.birth", journey) {
        val plan = physics.ensurePlan()
        val b = plan.bounds
        val r = plan.dropRest?.frame
        "clock=${journey.birthClock.value} born=${journey.born} closing=${journey.closing} progress=${journey.progress.value} " +
          "drawn=${b.left},${b.top},${b.right},${b.bottom} rest=${r?.left},${r?.top},${r?.right},${r?.bottom}"
      }
      drawContent()
    }
  } else this

/** Marcatori per distinguere il velo, la finestra fisica e le voci nel rilievo sul dispositivo. */
internal fun Modifier.menuDrawMarker(name: String): Modifier = if (GlassFrameDiagnostics.enabled) {
  drawWithContent {
    GlassFrameDiagnostics.span("menu.$name", this) {
      GlassFrameDiagnostics.event("menu.$name", this) { "size=$size" }
      drawContent()
    }
  }
} else this

/**
 * La sagoma del menu al servizio del suo viaggio: quello dei menu ([FluidMenuJourney.install]) o, per il contestuale col
 * vetro liquido, la nascita di B1 (b) ([FluidContextBirth.travel]) fra la goccia appesa alla card e il pannello, col suo
 * contorno deformato ([FluidContextBirth.warp]: goccia, fagiolo, uovo con la coda verso la card). Gratis se gli estremi
 * non cambiano, come `install`.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.fluidMenuDrawGroupFaces(anchor: FluidMenuAnchor) {
  if (!anchor.groupFacesAbove()) return
  val faces = anchor.groupFaces ?: return
  val at = anchor.groupBounds ?: return
  if (faces.isReleased || anchor.groupProgress() >= FluidMotion.MenuGroupFaceProgressSpan) return
  translate(at.left, at.top) { drawLayer(faces) }
}

private fun installMenuTravel(journey: FluidMenuJourney, physics: FluidPhysicsState, birth: FluidContextBirth?) {
  val seed = journey.seed
  if (birth == null || seed == null) {
    journey.install(physics)
    return
  }
  physics.dropAnchorReachDp = FluidMotion.DropAnchorReachDp
  physics.driveExternally(
    from = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f)),
    to = FluidForm.Slab(journey.to, FluidCornerRadii.all(journey.toRadius)),
    progress = { journey.progress.value },
    startFrame = null,
    travelSlab = { journey.backPulled(birth.travel(journey)) },
    travelWarp = { birth.warp(journey) },
  )
}

// --- I livelli di un menu (4.0, F5A.3) ------------------------------------------------------------

/**
 * Il sottomenu aperto e il passaggio fra due livelli dello stesso menu.
 *
 * Due orologi, perche' sono due cose: [swap] e' il tempo del contenuto in frazioni di T (le voci di prima che se ne
 * vanno sfocandosi, quelle nuove che arrivano e si mettono a fuoco: il cambio di contenuto di F3.4, lineare fino a
 * [FluidMenuLevelEnd]), [morph] e' la forma del vetro che passa dal pannello di prima a quello nuovo, con la molla
 * d'andata del menu. Il pannello disegna [openParent] da subito e, finche' il contenuto cambia, anche [previousParent],
 * il livello che se ne va.
 *
 * Stato semplice, non di snapshot: chi lo cambia (la scelta, le coroutine) incrementa [version], che la composizione
 * legge. [reset] lo azzera DENTRO la composizione di un'apertura nuova, prima che qualcuno lo legga.
 */
@Stable
internal class FluidMenuLevels {
  var version by mutableIntStateOf(0)
    private set

  /** La voce madre del sottomenu aperto (indice nella lista dichiarata), o −1 al primo livello. */
  var openParent: Int = -1
    private set

  /** Il livello che se ne va durante un cambio (−1: il primo livello). */
  var previousParent: Int = -1
    private set

  /** Il pannello da cui il vetro e' partito per il cambio in corso. */
  var fromPanel: Rect? = null
    private set

  var swap: Animatable<Float, AnimationVector1D> = Animatable(FluidMenuLevelEnd, LevelThreshold)
    private set
  var morph: Animatable<Float, AnimationVector1D> = Animatable(1f, LevelThreshold)
    private set

  /** Il contenuto sta cambiando livello: niente scelte, e i due livelli hanno l'opacita' e la sfocatura del cambio. */
  var swapping: Boolean = false
    private set

  /** Il vetro sta cambiando forma fra due livelli: lo guida [morph], non il viaggio. */
  var morphing: Boolean = false
    private set

  /** La superficie e' guidata dal cambio di livello (la composizione lo scrive, il congedo lo legge). */
  var driving: Boolean = false

  private var swapJob: Job? = null
  private var morphJob: Job? = null

  /** Legge [version]: la composizione che la chiama si ricompone a ogni cambio di livello e a ogni sua tappa. */
  fun subscribe(): Int = version

  /** Un'apertura nuova: il primo livello, fermo. */
  fun reset() {
    swapJob?.cancel()
    morphJob?.cancel()
    openParent = -1
    previousParent = -1
    fromPanel = null
    swap = Animatable(FluidMenuLevelEnd, LevelThreshold)
    morph = Animatable(1f, LevelThreshold)
    swapping = false
    morphing = false
  }

  /**
   * Va al livello [parent] (−1: il primo). [current] e' il pannello del livello di adesso: il vetro parte da li', o da
   * dove stava se un altro cambio lo stava ancora muovendo.
   */
  fun change(parent: Int, current: Rect?, scope: CoroutineScope, reducedMotion: Boolean) {
    if (parent == openParent) return
    val from = fromPanel
    val start = if (morphing && from != null && current != null) {
      androidx.compose.ui.geometry.lerp(from, current, morph.value.coerceIn(0f, 1f))
    } else {
      current
    }
    swapJob?.cancel()
    morphJob?.cancel()
    previousParent = openParent
    openParent = parent
    fromPanel = start
    if (reducedMotion || start == null) {
      swap = Animatable(FluidMenuLevelEnd, LevelThreshold)
      morph = Animatable(1f, LevelThreshold)
      swapping = false
      morphing = false
      version++
      return
    }
    val s = Animatable(0f, LevelThreshold)
    val m = Animatable(0f, LevelThreshold)
    swap = s
    morph = m
    swapping = true
    morphing = true
    version++
    swapJob = scope.launch {
      s.animateTo(FluidMenuLevelEnd, tween((GlassContentDefaults.DurationMs * FluidMenuLevelEnd).roundToInt(), easing = LinearEasing))
      swapping = false
      version++
    }
    morphJob = scope.launch {
      m.animateTo(1f, FluidMotion.dropOpen())
      morphing = false
      version++
    }
  }

  private companion object {
    const val LevelThreshold = 0.001f
  }
}

/** Sotto questo valore della molla il ritaglio segue la sagoma del vetro (vedi `travelClipIn`, che a 1 lo spegne). */
internal const val FluidMenuLevelClipCeiling = 0.999f

/**
 * Quanto e' largo il pannello di un livello: [fluidMenuPanelWidth] sulle sue righe (le tessere della fila si dividono
 * la larghezza che c'e' e non la allargano), con l'etichetta o il sottotitolo, il piu' largo dei due; la colonna della
 * spunta vale anche per il gallone di un sottomenu.
 */
internal fun fluidMenuLevelWidth(
  entries: List<FluidMenuEntry>,
  metrics: FluidMenuPanelMetrics,
  labelWidth: (String) -> Float,
  subtitleWidth: (String) -> Float,
  density: Float,
  hostWidthPx: Float,
): Float {
  val rows = entries.filter { it.kind != FluidMenuCellKind.Tile }
  return fluidMenuPanelWidth(
    metrics = metrics,
    labelWidthsPx = rows.map { e -> maxOf(labelWidth(e.action.label), e.action.subtitle?.let(subtitleWidth) ?: 0f) },
    hasIcon = rows.any { it.action.icon != null },
    hasCheck = rows.any { (it.action.selected && it.kind == FluidMenuCellKind.Row) || it.opensSubmenu || it.kind == FluidMenuCellKind.Header },
    density = density,
    hostWidthPx = hostWidthPx,
  )
}

/** La larghezza di un sottotitolo di menu nel suo tipo (`bodyMedium`), per [fluidMenuLevelWidth]. */
@Composable
internal fun rememberFluidMenuSubtitleMeasure(): (String) -> Float {
  val measurer = androidx.compose.ui.text.rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyMedium
  return remember(measurer, style) {
    { text -> measurer.measure(text, style = style, maxLines = 1).size.width.toFloat() }
  }
}

/** Le misure verticali delle celle dalla densita' e dal tipo del tema: la riga, quella col sottotitolo, la tessera. */
@Composable
internal fun rememberFluidMenuStructureMetrics(
  metrics: FluidMenuPanelMetrics,
  elementSize: FluidMenuElementSize,
): FluidMenuStructureMetrics {
  val density = LocalDensity.current
  val body = MaterialTheme.typography.bodyLarge
  val subtitle = MaterialTheme.typography.bodyMedium
  val tile = MaterialTheme.typography.bodySmall
  return remember(density, body, subtitle, tile, metrics, elementSize) {
    with(density) {
      fun line(style: androidx.compose.ui.text.TextStyle) =
        if (style.lineHeight.isSpecified) style.lineHeight.toDp() else (style.fontSize.toDp() * 1.4f)
      FluidMenuStructureMetrics(
        rowHeightPx = metrics.rowHeightPx,
        subtitleRowHeightPx = fluidMenuSubtitleRowHeight(line(body), line(subtitle)).toPx(),
        tileHeightPx = if (elementSize == FluidMenuElementSize.Medium) {
          fluidMenuMediumTileHeight(line(tile)).toPx()
        } else {
          metrics.rowHeightPx
        },
        paddingPx = metrics.paddingPx,
        sectionGapPx = metrics.sectionGapPx,
      )
    }
  }
}

/** Portale leggero della barra. La posa del gruppo si legge prima di consegnarne il vetro all'host. */
@Composable
internal fun rememberFluidToolbarMenu(): FluidToolbarMenu {
  val host = LocalFluidGlassModalHostState.current
  val menu = remember(host) { FluidToolbarMenu(host) }
  DisposableEffect(host, menu) {
    host?.register(menu.entry)
    onDispose {
      menu.entry.visible = false
      menu.anchor.consume(false)
      menu.anchor.handOff.sink = null
      host?.unregister(menu.entry)
    }
  }
  return menu
}

internal class FluidToolbarMenu(private val host: FluidGlassModalHostState?) {
  val anchor = FluidMenuAnchor()
  val entry = FluidGlassModalEntry().apply {
    presentation = FluidGlassModalPresentation.Expand
    menuAnchor = anchor
    menuHandOff = anchor.handOff
    origin = { anchor.restBounds }
    onDismissRequest = { visible = false }
  }

  fun open(actions: List<FluidContextAction>, group: Rect?, groupLease: (Boolean) -> Unit): Boolean {
    val owner = host ?: return false
    if (actions.isEmpty() || anchor.hidden || anchor.restBounds.isEmpty) return false
    anchor.pressedBounds = anchor.restBounds
    anchor.groupBounds = group?.takeUnless { it.isEmpty || it.width <= anchor.restBounds.width + 1f }
    anchor.groupLease = groupLease
    entry.actions = actions
    entry.openedAt = owner.nextSequence()
    entry.selection.cancel()
    entry.visible = true
    anchor.consume(true)
    anchor.handOff.attach(entry.selection)
    return true
  }
}

// --- L'avviso nell'host ------------------------------------------------------------------------

/** Quello che un [FluidAlert] nell'host deve mostrare, anche dopo che il chiamante l'ha tolto. */
@Immutable
internal data class FluidAlertSpec(
  val title: String,
  val message: String?,
  val actions: List<FluidAlertAction>,
  val content: (@Composable ColumnScope.() -> Unit)?,
  val modifier: Modifier,
  val confirmationCancel: FluidAlertAction? = null,
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
  sourceJourney: FluidModalSourceJourney?,
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

  if (!entry.visible && (sourceJourney?.returned ?: exitFinished)) {
    SideEffect {
      entry.lifted = false
      entry.inlineConfirmation = false
      host.finishExit(entry)
    }
    return
  }
  val inline = spec.confirmationCancel != null && sourceJourney != null
  SideEffect {
    if (sourceJourney != null) entry.lifted = true
    entry.inlineConfirmation = inline
  }
  val shownSpec = remember(spec, inline) {
    val cancel = spec.confirmationCancel
    if (cancel != null && !inline) spec.copy(actions = spec.actions + cancel) else spec
  }

  FluidAlertLayerContent(
    entry = entry,
    spec = shownSpec,
    backdrop = backdrop,
    geometry = { geometry.value },
    fade = { fade.value },
    sourceJourney = sourceJourney,
    inline = inline,
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
  sourceJourney: FluidModalSourceJourney?,
  inline: Boolean,
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
  val windowTint = remember(panelTint, dark, inline) {
    if (inline) panelTint else
      panelTint.copy(overlay = panelTint.overlay.compositeOver(Color.Black.copy(alpha = if (dark) 0.20f else 0.12f)))
  }
  val current by androidx.compose.runtime.rememberUpdatedState(spec)
  val progress = { sourceJourney?.clock?.value ?: geometry() }
  val materialAlpha: () -> Float = { sourceJourney?.bodyAlpha(progress(), physics) ?: fade() }

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
    if (sourceJourney != null) fluidModalContentMap(physics, pane.rect, progress(), sourceJourney.focus.value)
    else fluidMenuContentMap(target = pane.rect, current = physics.contentBoundsNow(), minScale = 0.9f, maxScale = 1.1f)
  }
  // F2.3b: l'avviso possiede il suo testo e i suoi pulsanti e scurisce in continuo (un grande, `Modal`).
  val alertInk = rememberGlassInk()
  val lens = rememberFluidModalLens(sourceJourney != null)

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { isTraversalGroup = true }
      .focusGroup(),
  ) {
    if (!inline) {
      FluidPresentationScrim(entry, backdrop, scrimGlass, fade, plain = sourceJourney != null)
    }
    FluidMenuReturnSurface(sourceJourney?.controlledReturn == true) {
      Modifier
        .fillMaxSize()
        .drawWithContent { if (materialAlpha() > 0.004f) drawContent() }
        .fluidPhysicsSurfaceInked(
          ink = alertInk,
          state = physics,
          backdrop = backdrop ?: noBackdrop,
          tint = windowTint,
          role = GlassRole.Modal,
          optics = GlassDefaults.panelOptics(),
          tier = if (backdrop == null) FluidPhysicsTier.Lite else null,
          intensity = materialAlpha,
          exports = if (backdrop != null) alertGlass else null,
        )
    }
    sourceJourney?.SourceFace(physics, progress)

    Layout(
      // Sopra la tastiera: un avviso con un campo (il prompt) non deve finirle sotto. Solo in basso,
      // quindi l'origine resta quella dell'host e la sagoma e la card parlano le stesse coordinate.
      modifier = Modifier.fillMaxSize()
        .then(if (sourceJourney == null) Modifier else Modifier.onGloballyPositioned {
          sourceJourney.rootOrigin = it.positionInRoot()
        })
        .imePadding(),
      content = {
        GlassInkContent(alertInk, LocalContentColor.current) {
          FluidAlertCard(
            spec = spec,
            selection = selection,
            focusRequester = focusRequester,
            pane = pane,
            material = if (backdrop != null) alertGlass else null,
            backdrop = backdrop,
            // F2.3b: la dissolvenza dell'inchiostro attorno al passaggio di lato.
            fade = {
              val alpha = sourceJourney?.contentAlpha(progress()) ?: fade()
              alpha * (alertInk.state?.inkAlpha() ?: 1f)
            },
            contentMap = contentMap,
            contentEffect = sourceJourney?.let { journey ->
              { map -> fluidModalLensEffect(lens, physics, pane.rect, map, journey.focus.value) }
            },
            onPick = pick,
            travelClip = sourceJourney?.let { journey ->
              Modifier.graphicsLayer {
                val round = fluidModalTravelClip(physics, pane.rect, progress(), journey.focus.value)
                if (round != null) shape = FluidPopoverTravelClipShape(round)
                clip = round != null
              }
            } ?: Modifier,
            inline = inline,
          )
        }
      },
    ) { measurables, constraints ->
      val w = min(maxWidthPx, constraints.maxWidth - marginPx * 2f).roundToInt().coerceAtLeast(1)
      val maxH = (constraints.maxHeight * FluidAlertMaxHeightFraction).roundToInt().coerceAtLeast(1)
      val placeable = measurables.first().measure(Constraints(minWidth = w, maxWidth = w, minHeight = 0, maxHeight = maxH))
      val anchor = sourceJourney?.bounds?.takeIf { inline }
      val rect = fluidConfirmationFrame(
        anchor, Size(placeable.width.toFloat(), placeable.height.toFloat()),
        Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
        marginPx, FluidMenuAnchorGap.toPx(),
      )
      pane.rect = if (sourceJourney != null) {
        rect.translate(rect.left.roundToInt() - rect.left, rect.top.roundToInt() - rect.top)
      } else rect
      entry.outsideTapArea.bounds = rect
      if (sourceJourney != null) {
        sourceJourney.drive(physics, rect, radiusPx, constraints.maxWidth.toFloat(), this, layoutDirection, progress)
      } else {
        val seedScale = if (exiting) FluidAlertExitScale else FluidAlertEnterScale
        val seed = fluidMenuSeedFrame(rect, rect.center, seedScale)
        physics.driveExternally(
          from = FluidForm.Slab(seed, FluidCornerRadii.all(radiusPx * seedScale)),
          to = FluidForm.Slab(rect, FluidCornerRadii.all(radiusPx)),
          progress = geometry,
        )
      }
      layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
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
  contentEffect: (GraphicsLayerScope.(FluidMenuContentMap) -> RenderEffect?)?,
  onPick: (Int) -> Unit,
  travelClip: Modifier,
  inline: Boolean,
) {
  val scheme = MaterialTheme.colorScheme
  val count = spec.actions.size
  val side = count == 2 && spec.confirmationCancel == null
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

  Box(modifier = travelClip
    .then(if (inline) Modifier.fluidParallelModalPanel() else Modifier)
    .then(if (material != null) Modifier.glassContainer(material) else Modifier),
  ) {
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
          renderEffect = contentEffect?.invoke(this, map)
        }
        // Il fondo lo disegna la superficie sotto (senza fondale, il suo ripiego opaco): qui solo il
        // contenuto.
        .then(spec.modifier)
        .onGloballyPositioned {
          card.value = it
          refresh()
        }
        .semantics { if (spec.title.isNotBlank()) paneTitle = spec.title }
        .focusRequester(focusRequester)
        .focusGroup(),
    ) {
      // Il messaggio scorre nello spazio che i pulsanti lasciano: i pulsanti sono l'unica uscita di
      // un avviso e non devono mai finire fuori dallo schermo.
      if (spec.confirmationCancel == null || spec.title.isNotBlank()) Column(
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

/** L'arrivo dello scrim e del materiale dei menu dell'host; interna per la traccia del contestuale (F5A.4). */
internal const val FluidMenuLayerFadeInMillis = 110

/** Il velo che entra ([FluidMenuLayerFadeInMillis], EaseOut) letto a [birthMillis] dalla nascita del menu (S4f, NO 1). */
internal fun fluidMenuBirthFade(birthMillis: Float): Float =
  androidx.compose.animation.core.EaseOut.transform((birthMillis / FluidMenuLayerFadeInMillis).coerceIn(0f, 1f))

private const val FluidAlertFadeInMillis = 140
private const val FluidAlertExitMillis = 160
private const val FluidAlertEnterScale = 1.08f
private const val FluidAlertExitScale = 0.96f
private const val FluidAlertMaxHeightFraction = 0.86f
private val FluidAlertWidth = 280.dp
private val FluidAlertMargin = 24.dp

/**
 * Solo per i test: chi guarda il menu fotogramma per fotogramma (S4c, NO 2: il salto dopo «Condividi» si riproduce al PC
 * nell'ordine dell'app). Null fuori dai test, e allora non si compone niente.
 */
@androidx.annotation.VisibleForTesting
internal object FluidMenuLayerProbe {
  @Volatile var onFrame: ((FluidMenuProbeFrame) -> Unit)? = null
}

/** Un fotogramma del menu per [FluidMenuLayerProbe]: lo stato del viaggio e la sagoma che il pannello disegna. */
internal data class FluidMenuProbeFrame(
  val nanos: Long,
  val layer: Int,
  val visible: Boolean,
  val closing: Boolean,
  val controlledReturn: Boolean,
  val progress: Float,
  val birthMillis: Float?,
  val frozen: Rect?,
  val drawn: Rect,
  val liquidQuiet: Boolean,
  val window: Float,
  val contentScale: Float,
  val inkAlpha: Float,
  val backdrop: GlassBackdropState?,
  /** Il velo e l'orologio della nascita dal tasto e dal gruppo (S4f, NO 1). */
  val fade: Float = 0f,
  val journeyBirth: Float = 0f,
  val born: Boolean = false,
  val birthRunning: Boolean = false,
  val progressRunning: Boolean = false,
)
