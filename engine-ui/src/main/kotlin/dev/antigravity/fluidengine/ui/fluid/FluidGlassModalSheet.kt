package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassLayerProbe
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassStateDump
import dev.antigravity.fluidengine.ui.glass.backdrop.glassFrameMarker
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.field.glassOpaquePanel
import dev.antigravity.fluidengine.ui.page.displayCornerRadii
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Il foglio dal basso e la pagina intera: il pannello che arriva viaggiando e si congeda col dito, con la
// barra in cima della pagina intera. Spezzato da `FluidGlassModal.kt` in F5A.0; lo chiama
// `FluidGlassModalPaneLayer` (`FluidGlassModalHost.kt`). Dalla 4.0 (F5B.2), quando lo dichiara un `FluidGlassSheet`
// (`FluidGlassSheet.kt`), il foglio ha i detent ed e' di vetro staccato dai bordi; il foglio di oggi resta com'era.

/**
 * The bottom-anchored variants: the sheet and the full-screen page.
 *
 * The only presentations whose edge is against the screen rather than against the thing that opened
 * them, and therefore the only ones that behave like a sheet: they travel in from below on a spring,
 * and they can be **pushed back out with a finger, from anywhere on them**.
 *
 * "From anywhere" is the part that takes work, because a sheet is allowed to contain a list and a
 * downward drag is then ambiguous. It is resolved the way Material resolves it, with nested scroll:
 * the content gets the gesture first and keeps it for as long as it has somewhere to scroll; the
 * moment it runs out — [NestedScrollConnection.onPostScroll] reporting leftover downward travel —
 * the sheet takes the remainder and starts moving. Letting go hands the fling to whichever of the
 * two is holding the gesture. A sheet with nothing scrollable in it never reaches that negotiation
 * and is simply dragged.
 *
 * La pagina intera ([fullScreen]) e' quella di sempre ([FluidFullScreenPane]). Il foglio ([FluidSheetPane]) con i detent
 * di un [FluidGlassSheet] (4.0) e' una lastra di vetro staccata dai bordi con gli angoli bassi concentrici allo schermo,
 * sale e scende fra le sue altezze e a tutta altezza diventa opaco; senza, e' il foglio di sempre, alto quanto il
 * contenuto e a filo dei bordi.
 */
@Composable
internal fun FluidGlassModalSheet(
  paneTitle: String?,
  visible: Boolean,
  paneTint: Color?,
  /** Il fondo della pagina intera, se l'app ne ha dato uno: vedi `FluidGlassModalPortal(pageColor)`. */
  pageColor: Color?,
  backdrop: GlassBackdropState?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  /** [FluidGlassModalPresentation.FullScreen]: tutta l'altezza, la barra in cima, lo scorrimento. */
  fullScreen: Boolean,
  onDismiss: () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)?,
  content: @Composable ColumnScope.() -> Unit,
  sheetBackground: FluidSheetBackground,
) {
  if (fullScreen) {
    FluidFullScreenPane(
      paneTitle = paneTitle,
      paneTint = paneTint,
      pageColor = pageColor,
      slide = slide,
      backProgress = backProgress,
      reducedMotion = reducedMotion,
      onDismiss = onDismiss,
      footer = footer,
      content = content,
    )
  } else {
    FluidSheetPane(
      paneTitle = paneTitle,
      visible = visible,
      paneTint = paneTint,
      backdrop = backdrop,
      slide = slide,
      backProgress = backProgress,
      reducedMotion = reducedMotion,
      onDismiss = onDismiss,
      footer = footer,
      content = content,
      sheetBackground = sheetBackground,
    )
  }
}

// --- La pagina intera (com'era prima di F5B.2) ----------------------------------------------------------------------

@Composable
private fun FluidFullScreenPane(
  paneTitle: String?,
  paneTint: Color?,
  pageColor: Color?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)?,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val dismissThresholdPx = with(density) { FluidSheetDismissTravel.toPx() }
  val flingDismissPx = with(density) { FluidSheetDismissVelocity.toPx() }

  // Plain state, not an `Animatable`: nested-scroll callbacks are not suspending, and a drag has to
  // land on the same frame as the finger. The animatable only comes out to settle it.
  var dragPx by remember { mutableFloatStateOf(0f) }

  fun settle(velocity: Float) {
    val shouldDismiss = dragPx > dismissThresholdPx || velocity > flingDismissPx
    if (shouldDismiss) {
      onDismiss()
      return
    }
    scope.launch {
      val from = dragPx
      animate(
        initialValue = from,
        targetValue = 0f,
        initialVelocity = velocity,
        animationSpec = FluidMotion.SheetSettle,
      ) { value, _ -> dragPx = value }
    }
  }

  val nested = remember(dismissThresholdPx, flingDismissPx) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Pulling back up while the sheet is already out of place closes that gap first, before the
        // content is allowed to scroll. Otherwise a half-dragged sheet scrolls its list instead of
        // returning, which reads as the drag having been ignored.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) {
          return Offset.Zero
        }
        val used = (-available.y).coerceAtMost(dragPx)
        dragPx -= used
        return Offset(0f, -used)
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        dragPx += available.y
        return Offset(0f, available.y)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragPx <= 0f) return Velocity.Zero
        settle(available.y)
        return available
      }
    }
  }

  // Il pannello sta in fondo allo schermo e le app girano edge-to-edge, quindi la finestra non si
  // ridimensiona da sola: senza questo, la tastiera copre i campi di testo che il pannello contiene.
  BoxWithConstraints(modifier = Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
    // Una pagina intera su uno schermo largo si stacca dai bordi. Un modulo a tutta larghezza su un
    // tablet e' un muro, e il margine intorno e' quello che lo fa leggere come una cosa sopra la
    // pagina invece che come un'altra pagina.
    val detached = maxWidth >= FluidFullScreenDetachWidth
    val shape = if (detached) {
      ContinuousCornerShape(FluidRadius.Sheet)
    } else {
      ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
    }
    // Una pagina intera e' opaca di proposito. Il vetro sta sulle cose che galleggiano sopra un contenuto, e una
    // pagina non galleggia: copre. Di vetro sarebbe l'intera interfaccia sfocata, con la pagina di sotto che si legge
    // attraverso il titolo — cioe' esattamente il difetto per cui il vetro va tenuto sugli elementi piccoli. La tinta,
    // se c'e', resta come un velo: e' il colore da cui la pagina e' nata.
    val surface = MaterialTheme.colorScheme.surface
    val page = pageColor
      ?: paneTint?.let { androidx.compose.ui.graphics.lerp(surface, it, 0.08f) }
      ?: surface
    Column(
      modifier = Modifier
        .then(
          if (detached) {
            // I margini stanno PRIMA del materiale nella catena: la superficie si disegna dentro
            // di essi, e la molla che porta il pannello in scena muove solo il pannello.
            Modifier
              .fillMaxHeight()
              .windowInsetsPadding(WindowInsets.systemBars)
              .padding(FluidFullScreenDetachedMargin)
              .widthIn(max = FluidScreenDefaults.ContentMaxWidth)
              .fillMaxWidth()
          } else {
            Modifier
              .fillMaxSize()
              .windowInsetsPadding(WindowInsets.statusBars)
          },
        )
        .nestedScroll(nested)
        .draggable(
          orientation = Orientation.Vertical,
          state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
          onDragStopped = { velocity -> settle(velocity) },
        )
        .graphicsLayer {
          val travelled = slide().coerceIn(0f, 1f)
          // Below the screen at 0, home at 1, and the drag is added on top in raw pixels so it
          // tracks the finger exactly rather than through an eased curve.
          translationY = if (reducedMotion) {
            dragPx
          } else {
            size.height * (1f - travelled) + dragPx
          }
          // The alpha only covers the last sliver of the exit; the travel is the animation.
          alpha = (travelled * 4f).coerceIn(0f, 1f)
          val retreat = 1f - backProgress() * 0.06f
          scaleX = retreat
          scaleY = retreat
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
        .background(page, shape)
        .clip(shape)
        .semantics {
          paneTitle?.let { this.paneTitle = it }
          isTraversalGroup = true
        },
    ) {
      FluidGrabber()
      // Il testo sulla pagina e' quello che ci si legge sopra: `onSurface` sulla pagina del tema, il chiaro
      // sul nero di un visualizzatore (3.0.1).
      val pageContent = pageColor?.let { fluidContentColorOn(it) } ?: MaterialTheme.colorScheme.onSurface
      CompositionLocalProvider(LocalContentColor provides pageContent) {
        FluidFullScreenHeader(title = paneTitle, onDismiss = onDismiss)
        // Lo scorrimento sta qui e non nel contenuto, perche' e' un compito della pagina: il
        // chiamante mette i suoi campi e basta. La chiusura trascinando passa di qui lo stesso —
        // quando la colonna non ha piu' niente da scorrere verso l'alto, il resto del gesto arriva
        // alla connessione del pannello.
        // La barra di sistema in fondo la scavalca l'ultima cosa della pagina: i tasti se ci sono,
        // altrimenti la coda del contenuto. Staccata dai bordi non serve: il margine e' gia' fuori.
        val clearsSystemBar = if (detached) Modifier else Modifier.navigationBarsPadding()
        Column(
          modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .then(if (footer == null) clearsSystemBar else Modifier),
          content = content,
        )
        if (footer != null) {
          Column(modifier = Modifier.fillMaxWidth().then(clearsSystemBar), content = footer)
        }
      }
    }
  }
}

// --- Il foglio (F5B.2) ----------------------------------------------------------------------------------------------

/**
 * Dove sta lo schermo per il foglio: la misura dell'area dell'host e il raggio degli angoli bassi del display (0 dove
 * non ce ne sono). Scritto al layout, letto in composizione per la forma.
 */
@Immutable
private data class FluidSheetScreen(val size: Size, val cornerPx: Float) {
  companion object {
    val Unknown = FluidSheetScreen(Size.Zero, 0f)
  }
}

/** Page colour currently drawn over the sheet's glass, including the live fade while dragging. */
internal class FluidSheetPageVeil(val color: Color, val alpha: () -> Float, val enlargement: () -> Float)
internal val LocalFluidSheetPageVeil = androidx.compose.runtime.staticCompositionLocalOf<FluidSheetPageVeil?> { null }

/**
 * Lo stato del moto di un foglio: il detent in cui sta e quanto il dito (o la molla) lo ha spostato da lì.
 *
 * La posizione e' un'altezza: quella del detent di riposo più [drag] (positivo verso l'alto). Il dito scrive [drag] al
 * pixel, la molla lo porta all'altezza del detent di arrivo, e solo a molla ferma il detent di riposo cambia e [drag]
 * torna a zero: così un dito che riprende il foglio a metà corsa ferma la molla dov'è e continua da lì (F5B-e).
 *
 * I campi semplici sono la geometria dell'ultimo layout: li scrive il layout e li leggono i gesti e il disegno dello
 * stesso fotogramma.
 */
@Stable
private class FluidSheetMotion {
  /** Il detent di riposo; null finché il foglio non si è mosso: allora vale quello scelto dall'app, o il più basso. */
  var restDetent: FluidSheetDetent? by mutableStateOf(null)
  var drag: Float by mutableFloatStateOf(0f)

  /**
   * Il velo opaco, 0..1: insegue la «grandezza» nella sua fascia con un piccolo ritardo ([fluidSheetVeilStep]). -1 finché
   * il foglio non ha la sua prima misura: allora prende il bersaglio senza inseguirlo (un foglio che si apre in cima è
   * già opaco).
   */
  var veil: Float by mutableFloatStateOf(-1f)

  /** Il dito sul foglio, per il chiarore (coordinate del pannello, prima dello spostamento). */
  var touch: Offset by mutableStateOf(Offset.Unspecified)
  val glow = Animatable(0f)
  var job: Job? by mutableStateOf(null)

  var stops: FluidSheetStops? = null
  var stopsKey: Any? = null
  var largePx: Float = 0f
  var windowHeightPx: Float = 0f

  /** Vero se il foglio ha i detent di un [FluidGlassSheet]; falso per il foglio alto quanto il contenuto. */
  var detented: Boolean = false

  // Lo zoom dalla sorgente (F5B.3). Campi del layout, letti dal disegno dello stesso fotogramma.
  /** Vero mentre il vetro ha la sagoma dello zoom (il layout lo misura lì, il contenuto resta alla misura d'arrivo). */
  var zoomActive: Boolean by mutableStateOf(false)

  /** Il riquadro d'arrivo del foglio (al suo detent), nelle coordinate dell'area dell'host. */
  var zoomFinal: Rect = Rect.Zero

  /** L'ultima sagoma trovata della sorgente: se nel frattempo sparisce, il vetro ci rientra lo stesso. */
  var zoomSource: Rect? = null

  /** Dove sta l'area dell'host nella radice Compose, dov'è il riquadro di una sorgente. */
  var rootOrigin: Offset = Offset.Zero

  /** La sorgente nascosta mentre il foglio è in scena: torna quando il foglio esce. */
  var consumed: FluidTransitionSource? = null

  /** I millisecondi dall'apertura, per la nitidezza del contenuto: si fermano a [FluidSheetZoomSharpMillis]. */
  var zoomElapsed: Float by mutableFloatStateOf(0f)

  /** L'apertura dello zoom, 0..1, sulla molla misurata su Apple ([FluidSheetZoomSpring]); la chiusura è dell'host. */
  val zoomOpen = Animatable(0f)

  val zoomFocus = Animatable(1f)
  val zoomInk = Animatable(1f)

  /** F8.10, diagnostica: lo strato del contenuto per la fotografia a riposo ([GlassStateDump]). */
  val dumpLayer = GlassLayerProbe("sheetLayer")

  /** Il viaggio dello zoom adesso: l'apertura sua, ma mai oltre la molla dell'host (che alla chiusura scende). */
  fun zoomTravel(slide: Float): Float = min(zoomOpen.value, slide)

  /**
   * Lo stesso fatto come stato, per la composizione (la forma): lo scrive il layout quando cambia. Un foglio con i detent
   * ha la forma del foglio staccato dal primo fotogramma in cui si vede; nel primo di tutti è ancora sotto lo schermo.
   */
  var detentedLook: Boolean by mutableStateOf(false)

  fun restIndex(stops: FluidSheetStops, selected: FluidSheetDetent?): Int {
    val rest = stops.indexOf(restDetent)
    if (rest >= 0) return rest
    val chosen = stops.indexOf(selected)
    return if (chosen >= 0) chosen else 0
  }

  fun position(stops: FluidSheetStops, selected: FluidSheetDetent?): Float = stops.heights[restIndex(stops, selected)] + drag

  /** Fissa il detent di riposo prima di muoversi, perché la posizione non salti se l'app cambia scelta a metà gesto. */
  fun hold(stops: FluidSheetStops, selected: FluidSheetDetent?) {
    if (stops.indexOf(restDetent) < 0) restDetent = stops.detents[restIndex(stops, selected)]
  }
}

/** Il marcatore dell'unico «detent» di un foglio alto quanto il suo contenuto. */
private val FluidSheetContentDetents: List<FluidSheetDetent> = listOf(FluidSheetDetent.Large)

@Composable
private fun FluidSheetPane(
  paneTitle: String?,
  visible: Boolean,
  paneTint: Color?,
  backdrop: GlassBackdropState?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
  footer: (@Composable ColumnScope.() -> Unit)?,
  content: @Composable ColumnScope.() -> Unit,
  sheetBackground: FluidSheetBackground,
) {
  val density = LocalDensity.current
  val view = LocalView.current
  val scope = rememberCoroutineScope()
  // Il foglio registra se stesso come il pop-up (2.13.0): una card di vetro, un interruttore, un
  // tasto messi nel foglio rifrangono il foglio, non la pagina dietro lo scrim. Registrato solo se
  // dentro c'e' del vetro che lo campiona.
  val sheetGlass = rememberGlassBackdrop()
  // F2.3b: il foglio possiede il suo contenuto e scurisce in continuo (un grande, `Modal`); il contenuto lo segue.
  val sheetInk = rememberGlassInk()
  // Senza fondo il foglio e' pieno (4.0.0, F1A.13): col materiale nuovo pubblica il proprio colore e fa da
  // contenitore, come la notifica senza fondo, perche' il vetro che ci sta dentro sia vetro su vetro e non un
  // tasto che campiona il niente.
  val sheetFill = if (backdrop == null) rememberGlassOpaquePanel() else null
  val sink = remember { FluidSheetSink() }
  val motion = remember { FluidSheetMotion() }
  val scene = if (backdrop != null && motion.detentedLook && isRenderEffectSupported()) {
    rememberSheetScene(backdrop)
  } else null
  val currentOnDismiss by rememberUpdatedState(onDismiss)
  val currentReduced by rememberUpdatedState(reducedMotion)
  val currentVisible by rememberUpdatedState(visible)
  val statusBars = WindowInsets.statusBars
  // Il velo che a tutta altezza fa del foglio una pagina: il colore della pagina del tema (bianco sul chiaro, il grigio
  // rialzato #1B1B1E sullo scuro: quelli del foglio di Apple).
  val pageInk = MaterialTheme.colorScheme.surface
  val glowAlpha = if (GlassDefaults.isDarkSurface()) FluidSheetPressGlowDark else FluidSheetPressGlowLight

  // La forma: angoli alti del foglio (`FluidRadius.Sheet`, 38 dp continui ≈ 32 circolari, come quelli del foglio di
  // Apple misurati su 323 f12203); con i detent angoli bassi concentrici a quelli dello schermo (F3.5), il foglio di oggi
  // a filo del fondo come prima. Cambia solo quando cambia lo schermo: un raggio che seguisse il moto rifarebbe il vetro a
  // ogni fotogramma.
  var screen by remember { mutableStateOf(FluidSheetScreen.Unknown) }
  val topRadiusPx = with(density) { FluidRadius.Sheet.toPx() }
  val restInsetPx = fluidSheetInsetPx(0f, density.density)
  val bottomRadiusPx = if (!motion.detentedLook || sink.spec?.edgeToEdge == true) {
    0f
  } else if (screen.size == Size.Zero) {
    topRadiusPx
  } else {
    val restWidth = min(screen.size.width - 2f * restInsetPx, with(density) { FluidSheetMaxWidth.toPx() })
    fluidSheetBottomRadiusPx(
      windowSize = screen.size,
      sheetWidthPx = restWidth,
      insetPx = restInsetPx,
      displayCornerPx = screen.cornerPx,
      topRadiusPx = topRadiusPx,
      minimumPx = with(density) { FluidRadius.Card.toPx() },
    ).roundToInt().toFloat()
  }
  val shape = remember(topRadiusPx, bottomRadiusPx) {
    ContinuousCornerShape(
      topStart = CornerSize(topRadiusPx),
      topEnd = CornerSize(topRadiusPx),
      bottomEnd = CornerSize(bottomRadiusPx),
      bottomStart = CornerSize(bottomRadiusPx),
    )
  }

  fun animateTo(target: Int, velocityUp: Float) {
    val stops = motion.stops ?: return
    val selected = sink.spec?.selected
    motion.hold(stops, selected)
    val rest = motion.restIndex(stops, selected)
    val to = stops.heights[target] - stops.heights[rest]
    val changes = target != rest
    motion.job?.cancel()
    motion.job = scope.launch {
      if (currentReduced) {
        motion.drag = to
      } else {
        // Il foglio alto quanto il contenuto torna com'e' sempre tornato; quello con i detent con la molla del foglio
        // di Apple: salendo supera appena, scendendo arriva da sopra senza rimbalzo.
        val spec = when {
          !motion.detented -> FluidMotion.SheetSettle
          to < motion.drag -> FluidSheetDetentSpringDown
          else -> FluidSheetDetentSpring
        }
        animate(motion.drag, to, velocityUp, spec) { value, _ -> motion.drag = value }
      }
      motion.restDetent = stops.detents[target]
      motion.drag = 0f
      motion.job = null
      if (changes && motion.detented) sink.onDetentChange(stops.detents[target])
    }
  }

  /** Il dito tocca il foglio: la molla si ferma dov'è e il foglio è del dito. */
  fun grab() {
    motion.job?.cancel()
    motion.job = null
    val stops = motion.stops ?: return
    motion.hold(stops, sink.spec?.selected)
  }

  fun dragBy(deltaDown: Float) {
    val next = motion.drag - deltaDown
    // Il foglio alto quanto il contenuto non sale: come prima, il dito lo può solo spingere giù.
    motion.drag = if (motion.detented) next else next.coerceAtMost(0f)
  }

  fun settle(velocityUp: Float) {
    val stops = motion.stops ?: return
    val position = motion.position(stops, sink.spec?.selected)
    val target = fluidSheetSettleTarget(stops, position, velocityUp, density.density)
    if (target == FluidSheetDismiss) {
      motion.job?.cancel()
      motion.job = null
      currentOnDismiss()
      // F8.26: un foglio che l'app tiene aperto (il foglio sopra la mappa, che come in Mappe non si chiude) torna al
      // detent piu' basso con la molla, invece di restare dove l'ha lasciato il dito. Due fotogrammi: il tempo perche'
      // un `visible = false` dell'app arrivi fin qui.
      if (motion.detented) motion.job = scope.launch {
        withFrameNanos { }
        withFrameNanos { }
        if (currentVisible) animateTo(0, velocityUp)
      }
      return
    }
    animateTo(target, velocityUp)
  }

  /** La maniglia toccata: il detent successivo, e dall'ultimo il primo (in iOS «toccare la maniglia scorre i detent»). */
  fun cycle() {
    val stops = motion.stops ?: return
    if (!motion.detented || stops.heights.size < 2) return
    grab()
    val rest = motion.restIndex(stops, sink.spec?.selected)
    animateTo((rest + 1) % stops.heights.size, 0f)
  }

  // Il velo insegue l'altezza nel tempo (F5B.2): il bersaglio e' la «grandezza» nella fascia del velo all'altezza di
  // adesso, e il velo lo raggiunge con un ritardo di 60 ms, come il bianco del foglio di Apple che arriva dopo il foglio.
  // Il ciclo dei fotogrammi gira solo mentre velo e bersaglio sono diversi: a foglio fermo non chiede niente.
  LaunchedEffect(motion, sink) {
    snapshotFlow {
      // Letti sempre, anche prima della prima misura: sono loro a far rivalutare il bersaglio quando il foglio si muove.
      val selected = sink.spec?.selected
      motion.drag
      motion.restDetent
      val stops = motion.stops
      when {
        // Prima della prima misura non c'e' un bersaglio: il disegno usa quello del momento (vedi sotto).
        stops == null -> null
        !motion.detented -> 0f
        else -> {
          val visible = fluidSheetVisibleHeight(motion.position(stops, selected), stops, motion.windowHeightPx)
          fluidSheetLargeness(visible, stops, motion.largePx, FluidSheetOpaqueBand)
        }
      }
    }.collectLatest { target ->
      if (target == null) return@collectLatest
      if (motion.veil < 0f || currentReduced) {
        motion.veil = target
        return@collectLatest
      }
      var last = 0L
      while (abs(motion.veil - target) > 0.002f) {
        withFrameNanos { now ->
          if (last != 0L) motion.veil = fluidSheetVeilStep(motion.veil, target, (now - last) / 1_000_000f)
          last = now
        }
      }
      motion.veil = target
    }
  }

  // Lo zoom (F5B.3): l'orologio della nitidezza del contenuto, dal primo fotogramma del foglio; e la sorgente che torna a
  // disegnarsi quando il foglio esce di scena (il vetro, all'ultimo fotogramma, e' gia' rientrato nel suo riquadro).
  LaunchedEffect(motion) {
    launch { if (currentReduced) motion.zoomOpen.snapTo(1f) else motion.zoomOpen.animateTo(1f, FluidSheetZoomSpring) }
    var start = 0L
    while (motion.zoomElapsed < FluidSheetZoomSharpMillis) {
      withFrameNanos { now ->
        if (start == 0L) start = now
        motion.zoomElapsed = (now - start) / 1_000_000f
      }
    }
  }
  // Il congedo avvia fuoco e inchiostro della lente F5A anche quando il contorno è ancora quasi fermo.
  // Durante una riapertura le due curve ripartono dai valori raggiunti, prima della riconsegna alla sorgente.
  LaunchedEffect(motion, visible, reducedMotion) {
    // A riposo lo zoom è inattivo anche quando la sorgente è ancora consumata e può ricevere il foglio.
    if (reducedMotion || motion.consumed == null) {
      motion.zoomFocus.snapTo(1f)
      motion.zoomInk.snapTo(1f)
    } else if (visible) {
      if (motion.zoomFocus.value < 1f) launch {
        motion.zoomFocus.animateTo(1f, fluidModalFocusSpec(FluidModalSourceMotion.Popover, true, motion.zoomFocus.value, false))
      }
      if (motion.zoomInk.value < 1f) launch {
        motion.zoomInk.animateTo(1f, fluidModalInkExitSpec(1f - motion.zoomInk.value))
      }
    } else {
      launch { motion.zoomFocus.animateTo(0f, fluidMenuReturnFocusSpec(motion.zoomFocus.value)) }
      launch { motion.zoomInk.animateTo(0f, fluidMenuReturnInkSpec(motion.zoomInk.value)) }
    }
  }
  DisposableEffect(motion) {
    onDispose {
      motion.consumed?.release()
      motion.consumed = null
    }
  }

  // La scelta dell'app: quando cambia, il foglio ci va con la molla. La prima scelta e' quella in cui si apre.
  LaunchedEffect(sink, motion) {
    var last: FluidSheetDetent? = null
    var first = true
    snapshotFlow { sink.spec?.selected }.collect { wanted ->
      val previous = last
      last = wanted
      if (first) {
        first = false
        return@collect
      }
      val stops = motion.stops ?: return@collect
      val target = stops.indexOf(wanted)
      if (target < 0) return@collect
      if (motion.restDetent == null) {
        val was = stops.indexOf(previous)
        motion.restDetent = stops.detents[if (was >= 0) was else 0]
      }
      if (target != motion.restIndex(stops, wanted)) animateTo(target, 0f)
    }
  }

  val nested = remember(motion, sink) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Il dito sale: finché il foglio non e' al detent piu' alto sale lui, e la lista resta ferma (iOS:
        // «lo scorrimento del contenuto lo espande»). Il foglio alto quanto il contenuto chiude solo il vuoto lasciato
        // da un trascinamento verso il basso, come prima.
        if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
        val stops = motion.stops ?: return Offset.Zero
        val position = motion.position(stops, sink.spec?.selected)
        val ceiling = if (motion.detented) stops.highest else stops.lowest
        if (position >= ceiling - 0.01f) return Offset.Zero
        grab()
        val used = (-available.y).coerceAtMost(ceiling - position)
        motion.drag += used
        return Offset(0f, -used)
      }

      override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        // Il dito scende e la lista e' in cima: il resto del gesto abbassa il foglio.
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        if (motion.stops == null) return Offset.Zero
        grab()
        motion.drag -= available.y
        return Offset(0f, available.y)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (motion.drag == 0f && motion.job == null) return Velocity.Zero
        settle(-available.y)
        return available
      }
    }
  }

  Layout(
    // Il pannello sta in fondo allo schermo e le app girano edge-to-edge, quindi la finestra non si
    // ridimensiona da sola: senza questo, la tastiera copre i campi di testo che il pannello contiene.
    modifier = Modifier
      .fillMaxSize()
      .imePadding()
      .onPlaced { coordinates -> motion.rootOrigin = coordinates.positionInRoot() }
      .onSizeChanged { size ->
        val corners = displayCornerRadii(view)
        val next = FluidSheetScreen(size.toSize(), max(corners.bottomLeft, corners.bottomRight))
        if (next != screen) screen = next
      },
    content = {
      val sharedMaterial = motion.detentedLook
      // Read the same value in the glass painter and in content that needs to adapt its ink.
      fun paintedVeil(height: Float): Float {
        val stops = motion.stops
        return when {
          !sharedMaterial || stops == null -> 0f
          motion.zoomActive -> fluidSheetZoomVeil(motion.zoomTravel(slide()),
            fluidSheetLargeness(motion.zoomFinal.height, stops, motion.largePx, FluidSheetOpaqueBand))
          motion.veil < 0f -> fluidSheetLargeness(height, stops, motion.largePx, FluidSheetOpaqueBand)
          else -> motion.veil
        }
      }
      val pageVeil = remember(motion, sharedMaterial, pageInk, slide) {
        FluidSheetPageVeil(pageInk, alpha = { paintedVeil(motion.zoomFinal.height) }, enlargement = {
          val stops = motion.stops
          if (stops == null || stops.highest <= stops.lowest) 0f else {
            val position = motion.position(stops, sink.spec?.selected)
            ((position - stops.lowest) / (stops.highest - stops.lowest)).coerceIn(0f, 1f)
          }
        })
      }
      Box(
        propagateMinConstraints = true,
        modifier = Modifier
          .nestedScroll(nested)
          .draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta -> dragBy(delta) },
            startDragImmediately = motion.detented && motion.job != null,
            onDragStarted = { grab() },
            onDragStopped = { velocity -> settle(-velocity) },
          )
          // Il chiarore del dito: si accende al contatto e si spegne al rilascio, ovunque sul foglio, senza prendere
          // il gesto a nessuno (passaggio iniziale, niente consumato).
          .pointerInput(motion) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
              motion.touch = down.position
              scope.launch {
                if (currentReduced) motion.glow.snapTo(1f) else motion.glow.animateTo(1f, FluidMotion.GlowPress)
              }
              while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change != null && change.pressed) motion.touch = change.position
                if (event.changes.none { it.pressed }) break
              }
              scope.launch {
                if (currentReduced) motion.glow.snapTo(0f) else motion.glow.animateTo(0f, FluidMotion.GlowPress)
              }
            }
          }
          .graphicsLayer {
            val spec = sink.spec
            val stops = motion.stops
            val travelled = slide().coerceIn(0f, 1f)
            // Sotto il detent piu' basso il foglio scende intero, in pixel del dito.
            val sinkPx = if (stops != null) fluidSheetSinkPx(motion.position(stops, spec?.selected), stops) else 0f
            if (motion.zoomActive) {
              // Lo zoom (F5B.3): la sagoma la fa il layout, dalla sorgente al detent (discesa del dito compresa); niente
              // viaggio dal basso e niente dissolvenza, che sulla sorgente sarebbe un lampo.
              translationY = 0f
              alpha = 1f
            } else if (spec?.transition == FluidSheetTransition.CrossFade) {
              // Entra al suo posto, in dissolvenza (iOS 27 `.crossFade`): la molla del foglio letta come opacita'.
              translationY = sinkPx
              alpha = fluidSheetCrossFadeAlpha(slide())
            } else {
              // Below the screen at 0, home at 1, and the drag is added on top in raw pixels so it
              // tracks the finger exactly rather than through an eased curve.
              translationY = if (reducedMotion) sinkPx else size.height * (1f - travelled) + sinkPx
              // The alpha only covers the last sliver of the exit; the travel is the animation.
              alpha = (travelled * 4f).coerceIn(0f, 1f)
            }
            val retreat = 1f - backProgress() * 0.06f
            scaleX = retreat
            scaleY = retreat
            transformOrigin = TransformOrigin(0.5f, 1f)
          }
          .then(
            if (sharedMaterial) Modifier
            else sheetBaseMaterial(backdrop, sheetFill, sheetGlass, sheetInk, paneTint, shape, motion),
          )
          .semantics {
            paneTitle?.let { this.paneTitle = it }
            isTraversalGroup = true
          },
      ) {
        if (sharedMaterial) {
          // Il pannello e i vetri al suo interno leggono la stessa immagine, compreso il velo.
          // La registrazione è sotto la colonna: testo e controlli non possono campionare se stessi.
          Box(
            Modifier.matchParentSize()
              .glassMaterialSource(sheetGlass)
              .then(sheetBaseMaterial(scene?.backdrop ?: backdrop, sheetFill, null, sheetInk, paneTint, shape, motion))
              .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                  val veil = paintedVeil(size.height)
                  if (veil > 0f) drawOutline(outline, pageInk.copy(alpha = veil))
                }
              },
          )
        }
        Column(
          Modifier
            .then(if (sharedMaterial) Modifier.glassContainer(sheetGlass) else Modifier)
            .clip(shape)
            .drawWithCache {
              onDrawBehind {
                val glow = motion.glow.value.coerceIn(0f, 1f)
                val touch = motion.touch
                if (motion.detented && glow > 0.001f && touch.isSpecified) {
                  val sinkPx = motion.stops?.let { fluidSheetSinkPx(motion.position(it, sink.spec?.selected), it) } ?: 0f
                  drawRect(Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = glowAlpha * glow), Color.Transparent),
                    center = Offset(touch.x, touch.y - sinkPx),
                    radius = size.width * 0.75f,
                  ))
                }
              }
            }
            // Lo zoom (F5B.3): il vetro ha la sagoma che cresce, il contenuto la misura d'arrivo, in cima al vetro e al
            // centro, ritagliato dal vetro (R7 A5, A7) e sfocato finché non è arrivato. Fuori dallo zoom passa e basta.
            // Sta sulla colonna, dopo il ritaglio: col materiale condiviso (correzione del foglio dopo S9) il vetro è un
            // figlio del pannello e deve restare alla misura della sagoma, non a quella d'arrivo.
            .layout { measurable, constraints ->
              val final = motion.zoomFinal
              if (!motion.zoomActive || final.width < 1f || final.height < 1f) {
                val placeable = measurable.measure(constraints)
                return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
              }
              val placeable = measurable.measure(Constraints.fixed(final.width.roundToInt(), final.height.roundToInt()))
              layout(constraints.maxWidth, constraints.maxHeight) {
                placeable.place(((constraints.maxWidth - placeable.width) / 2f).roundToInt(), 0)
              }
            }
            .graphicsLayer {
              alpha = if (motion.zoomActive) motion.zoomInk.value else 1f
              val sigma = if (motion.zoomActive && alpha > 0f) {
                fluidSheetZoomContentBlurDp(motion.zoomElapsed, motion.zoomTravel(slide()), motion.zoomFocus.value).dp.toPx()
              } else {
                0f
              }
              renderEffect = if (sigma > 0.05f) {
                val radius = glassContentBlurRadiusPx(sigma)
                BlurEffect(radius, radius, TileMode.Decal)
              } else {
                null
              }
              // F8.10, diagnostica: i valori che lo strato del contenuto ha davvero adesso (la «x» rimasta sfocata).
              GlassFrameDiagnostics.event("sheetLayer", motion) {
                "zoom=${motion.zoomActive} ink=$alpha sigma=$sigma effect=${renderEffect != null}"
              }
              if (GlassStateDump.active) {
                // Letto qui e nel contenuto: la prova `foglio` rifà le proprietà dello strato e il suo contenuto.
                GlassStateDump.readSheetTick()
                motion.dumpLayer.applied("zoom=${motion.zoomActive} ink=$alpha sigma=$sigma effect=${renderEffect != null}")
              }
            }
            // F8.10, diagnostica: quando il contenuto dello strato si registra di nuovo. Solo col log acceso, o nella
            // fotografia a riposo di un'app debuggable.
            .then(if (GlassFrameDiagnostics.enabled) Modifier.glassFrameMarker("sheet.content") else Modifier)
            .then(
              if (GlassStateDump.active) {
                Modifier.drawWithContent {
                  GlassStateDump.readSheetTick()
                  motion.dumpLayer.recorded()
                  drawContent()
                }
              } else {
                Modifier
              },
            ),
        ) {
          // Davanti alla tab bar in alto della navigazione adattiva: la sua fascia qui non conta (F6B.12). Il velo della
          // pagina passa ai contenuti per l'inchiostro del pannello info (F6C.9).
          CompositionLocalProvider(
            LocalFluidSheetSink provides sink,
            LocalFluidSheetPageVeil provides pageVeil,
            LocalFluidTopChromeBand provides 0.dp,
          ) {
            // La maniglia: c'e' sempre (lo spazio in cima e' lo stesso di prima); un foglio che non la vuole la nasconde
            // in disegno. Toccata, passa al detent successivo.
            FluidGrabber(
              modifier = Modifier
                .graphicsLayer { alpha = if (sink.spec?.dragIndicator == false) 0f else 1f }
                .pointerInput(motion, sink) { detectTapGestures { cycle() } },
            )
            if (backdrop != null) {
              // Come nel pop-up: il canvas del contenuto e' il foglio, e il vetro che ci sta sopra e'
              // vetro impilato (`LocalFluidCanvasIsGlass`), non una card su una pagina.
              CompositionLocalProvider(
                LocalFluidCanvasBackdrop provides sheetGlass,
                LocalFluidCanvasIsGlass provides true,
              ) {
                GlassInkContent(sheetInk, LocalContentColor.current) {
                  content()
                  footer?.invoke(this)
                }
              }
            } else {
              content()
              footer?.invoke(this)
            }
          }
        }
      }
      if (scene != null) Box(Modifier.fillMaxSize().then(scene.source))
    },
  ) { measurables, constraints ->
    // `density` qui fuori e' l'oggetto di composizione: nel layout serve il numero dello scope.
    val scale = this.density
    val windowWidth = constraints.maxWidth.toFloat()
    val windowHeight = constraints.maxHeight.toFloat()
    val spec = sink.spec
    val topInset = statusBars.getTop(this).toFloat()
    val large = fluidSheetLargeHeightPx(windowHeight, topInset, scale)
    val maxWidth = FluidSheetMaxWidth.toPx()
    motion.largePx = large
    motion.windowHeightPx = windowHeight
    val panel = measurables.first()
    val source = measurables.getOrNull(1)?.measure(constraints)
    if (spec != null) {
      // I detent di un FluidGlassSheet: il pannello e' alto quanto la sua altezza visibile, il contenuto con lui.
      val key = Triple(spec.detents, windowHeight, topInset)
      val stops = motion.stops.takeIf { motion.stopsKey == key && motion.detented }
        ?: fluidSheetStops(spec.detents, windowHeight, topInset, scale).also {
          motion.stops = it
          motion.stopsKey = key
        }
      motion.detented = true
      if (!motion.detentedLook) motion.detentedLook = true
      val position = motion.position(stops, spec.selected)
      // La soglia di `undimmedUpTo`: il tocco e il fuoco cambiano di colpo alla quota, il velo cresce con la strada fino al
      // detent sopra (F5B, giro di correzione). Senza un detent sopra la pagina resta attiva e chiara a ogni altezza.
      val backgroundLimit = spec.undimmedUpTo?.let { fluidSheetDetentHeightPx(it, windowHeight, large, scale) }
      val dimCeiling = backgroundLimit?.let { fluidSheetDimCeilingPx(stops, it) }
      sheetBackground.undimmed = fluidSheetIsUndimmed(position, backgroundLimit) || (backgroundLimit != null && dimCeiling == null)
      sheetBackground.dim = fluidSheetDim(position, backgroundLimit, dimCeiling)
      val visible = fluidSheetVisibleHeight(position, stops, windowHeight)
      val largeness = fluidSheetLargeness(visible, stops, large)
      val inset = if (spec.edgeToEdge) 0f else fluidSheetInsetPx(largeness, scale)
      val width = min(windowWidth - 2f * inset, maxWidth).coerceAtLeast(0f).roundToInt()
      val height = visible.roundToInt().coerceAtLeast(0)
      val left = ((windowWidth - width) / 2f).roundToInt()
      val top = (windowHeight - inset).roundToInt() - height
      // Lo zoom (F5B.3): finché il foglio non è arrivato (la sua molla in apertura, quella dell'host in chiusura), il vetro
      // ha la sagoma che va dalla sorgente al detent. La sorgente si cerca a ogni fotogramma (una barra può cambiare sotto
      // il foglio); se sparisce, vale l'ultima trovata; se non c'era all'apertura, il foglio sale dal basso come sempre.
      val zoom = spec.source?.takeIf { spec.transition == FluidSheetTransition.Slide && !currentReduced }?.let { source ->
        val travelled = motion.zoomTravel(slide())
        if (travelled >= 1f) return@let null
        source.resolve()?.bounds?.translate(-motion.rootOrigin)?.takeIf { it.isFinite && !it.isEmpty }?.let { motion.zoomSource = it }
        motion.zoomSource?.let { found ->
          if (motion.consumed == null) {
            source.consume()
            motion.consumed = source
          }
          // Un foglio spinto giù dal dito prima di chiudersi rientra da dove si vede, non dal suo detent.
          val sunk = fluidSheetSinkPx(motion.position(stops, spec.selected), stops)
          fluidSheetZoomBounds(found, Rect(left.toFloat(), top + sunk, (left + width).toFloat(), top + height + sunk), travelled)
        }
      }
      motion.zoomFinal = Rect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat())
      motion.zoomActive = zoom != null
      if (zoom != null) {
        val placeable = panel.measure(
          Constraints.fixed(zoom.width.roundToInt().coerceAtLeast(1), zoom.height.roundToInt().coerceAtLeast(1)),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
          source?.place(0, 0)
          placeable.place(zoom.left.roundToInt(), zoom.top.roundToInt())
        }
      } else {
        val placeable = panel.measure(Constraints.fixed(width, height))
        layout(constraints.maxWidth, constraints.maxHeight) {
          source?.place(0, 0)
          placeable.place(left, top)
        }
      }
    } else {
      // Il foglio di sempre, alto quanto il suo contenuto: com'era (piano, F5B-d), a filo dei lati fino a 620 dp e del
      // fondo, al più 640 dp. Un rientro qui stringerebbe il contenuto delle app di 10 dp: il pannello della matrice
      // della galleria, che non scorre, andava a capo e perdeva il tasto in fondo sotto il tetto (sessioni/f5b-2).
      val width = min(windowWidth, maxWidth).coerceAtLeast(0f).roundToInt()
      val cap = min(FluidSheetMaxHeight.toPx(), windowHeight).roundToInt().coerceAtLeast(0)
      val placeable = panel.measure(Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = cap))
      motion.stops = FluidSheetStops(FluidSheetContentDetents, floatArrayOf(placeable.height.toFloat()))
      motion.stopsKey = null
      motion.detented = false
      sheetBackground.undimmed = false
      sheetBackground.dim = 1f
      motion.zoomActive = false
      if (motion.detentedLook) motion.detentedLook = false
      layout(constraints.maxWidth, constraints.maxHeight) {
        source?.place(0, 0)
        placeable.place(((windowWidth - placeable.width) / 2f).roundToInt(), windowHeight.roundToInt() - placeable.height)
      }
    }
  }
}

internal class FluidSheetScene(val backdrop: GlassBackdropState, val source: Modifier)

/** Pagina e scrim si rasterizzano alla misura della finestra, anche quando il foglio cambia altezza.
 * La lastra legge così la porzione corrente di una texture stabile, mantenendo posizione e ottica del vetro.
 */
@Composable
internal fun rememberSheetScene(input: GlassBackdropState): FluidSheetScene {
  var origin by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val texture = rememberGraphicsLayer()
  val scene = rememberLayerBackdrop(texture) {
    val density = this
    with(input.backdrop) { drawBackdrop(density, origin, null) }
  }
  return remember(input, scene) {
    scene.onDemand = true
    texture.compositingStrategy = CompositingStrategy.Offscreen
    val image = object : Backdrop by scene {
      // Le sonde e il prestito della pagina devono continuare a vedere le sorgenti originali.
      override val layerSources get() = input.backdrop.layerSources
      override fun forEachPart(block: (Backdrop) -> Unit) {
        block(scene)
        input.backdrop.forEachPart(block)
      }
      override fun isReadyToSample() = scene.isReadyToSample() && input.backdrop.isReadyToSample()
    }
    FluidSheetScene(
      GlassBackdropState(image, null, input.blurRadius),
      Modifier.graphicsLayer().onGloballyPositioned { origin = it }.layerBackdrop(scene),
    )
  }
}

@Composable
private fun sheetBaseMaterial(
  backdrop: GlassBackdropState?,
  sheetFill: GlassBackdropState?,
  exports: GlassBackdropState?,
  ink: GlassInk,
  paneTint: Color?,
  shape: Shape,
  motion: FluidSheetMotion,
): Modifier = when {
  backdrop != null -> Modifier.glassSurfaceInked(
    ink = ink,
    state = backdrop,
    tint = GlassDefaults.panelTint(paneTint),
    shape = shape,
    role = GlassRole.Modal,
    optics = GlassDefaults.panelOptics(),
    pressed = { if (motion.detented) motion.glow.value.coerceIn(0f, 1f) else 0f },
    exports = exports,
  )
  sheetFill != null -> Modifier
    .glassOpaquePanel(sheetFill.layerBackdrop, MaterialTheme.colorScheme.surfaceContainerHigh, shape)
    .glassContainer(sheetFill)
  else -> Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
}

/**
 * Il colore del testo su un fondo qualunque: il ruolo «on» del tema se il fondo e' un ruolo del tema,
 * altrimenti nero o bianco secondo la luminanza (3.0.1, per `FluidGlassModalPortal(pageColor)`).
 */
@Composable
internal fun fluidContentColorOn(background: Color): Color =
  MaterialTheme.colorScheme.contentColorFor(background).takeOrElse {
    if (background.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White
  }

/**
 * La barra in cima a una pagina intera: il titolo nel mezzo, la chiusura a destra.
 *
 * La chiusura e' un **tasto di vetro** ([FluidGlassIconButton], la croce al centro), come ogni altro
 * tasto di una barra: la pagina intera e' opaca di proposito, ma i comandi che le stanno sopra sono
 * della stessa famiglia di quelli di ogni barra dell'app, e un cerchio dipinto accanto a un tasto di
 * vetro e' la differenza che si legge. Sotto API 31 non c'e' un vetro da attraversare e resta il
 * cerchio pieno con la croce di prima, lo stesso segno del tasto che svuota un campo.
 */
@Composable
private fun FluidFullScreenHeader(title: String?, onDismiss: () -> Unit) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(FluidScreenDefaults.ControlRowHeight)
      // 4.0 (F1A.11): il vetro della croce sta a 16 dp dal bordo come ogni tasto di barra ([FluidMetrics.ScreenEdgeMargin]);
      // prima il riquadro da 48 stava a 8 e il vetro a 10. [FluidBarEdgeInset] e' il margine del riquadro, non del vetro.
      .padding(horizontal = FluidBarEdgeInset),
  ) {
    if (title != null) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        // Il colore della pagina (3.0.1): `onSurface` di serie, il chiaro su un fondo scuro dell'app.
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
          .align(Alignment.Center)
          // Il titolo non entra nel riquadro del tasto (48 dp, [FluidMetrics.TouchTarget]) e il margine e' simmetrico.
          .padding(horizontal = FluidMetrics.TouchTarget),
      )
    }
    FluidFullScreenCloseButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd))
  }
}

@Composable
private fun FluidFullScreenCloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
  // Il nome per TalkBack viene dalle parole dell'app (3.0.1): era «Chiudi» scritto qui.
  val closeLabel = LocalFluidLabels.current.close
  if (isRenderEffectSupported()) {
    // Il tasto tondo di vetro delle barre: pressione di LibrePods, luce del dito, `Tap`. Il fondale e'
    // quello che c'e' in scope, e su una pagina opaca e' vuoto — il tasto tiene il bordo, l'ombra e la
    // risposta al dito, e niente da piegare, come ogni tasto di vetro su una pagina piatta.
    FluidGlassIconButton(
      onClick = onClick,
      modifier = modifier,
    ) {
      // A inchiostro pieno dalla 4.0 (era all'80%): le icone dei tasti di vetro sono nell'inchiostro del testo (R6).
      FluidIcon(
        imageVector = FluidIcons.Close,
        contentDescription = closeLabel,
        size = FluidFullScreenCloseGlyph,
      )
    }
    return
  }
  val fill = LocalContentColor.current.copy(alpha = 0.10f)
  val glyph = LocalContentColor.current.copy(alpha = 0.70f)
  Box(
    modifier = modifier
      .size(FluidFullScreenCloseTouchTarget)
      .fluidPressable(onClick = onClick, role = Role.Button, feedback = FluidPressFeedback.ExpandRound)
      .semantics { contentDescription = closeLabel },
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(FluidFullScreenCloseSize)) {
      val radius = size.minDimension / 2f
      drawCircle(color = fill, radius = radius)
      val arm = radius * 0.36f
      val stroke = radius * 0.14f
      listOf(
        Offset(-arm, -arm) to Offset(arm, arm),
        Offset(-arm, arm) to Offset(arm, -arm),
      ).forEach { (from, to) ->
        drawLine(color = glyph, start = center + from, end = center + to, strokeWidth = stroke, cap = StrokeCap.Round)
      }
    }
  }
}

/** Il tetto del foglio alto quanto il suo contenuto (sotto c'e' anche l'altezza di `FluidSheetDetent.Large`). */
private val FluidSheetMaxHeight = 640.dp

/** Oltre questa larghezza un foglio non si stira: resta in mezzo, largo quanto un pop-up. */
private val FluidSheetMaxWidth = 620.dp

/** Da questa larghezza in su una pagina intera si stacca dai bordi invece di stare a filo. */
private val FluidFullScreenDetachWidth = 600.dp
private val FluidFullScreenDetachedMargin = 24.dp
private val FluidFullScreenCloseSize = 30.dp
private val FluidFullScreenCloseTouchTarget = 44.dp

/**
 * La croce dentro il tasto di vetro: 0,6 del lato della lente (44 dp), come ogni icona di un tasto tondo, cioe'
 * 26 dp dalla 4.0 (era la meta', 20: R6).
 */
private val FluidFullScreenCloseGlyph = FluidMetrics.BarIconSize
