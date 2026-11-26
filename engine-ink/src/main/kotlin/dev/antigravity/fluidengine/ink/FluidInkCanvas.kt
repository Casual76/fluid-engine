package dev.antigravity.fluidengine.ink

import android.graphics.Matrix
import android.os.Build
import android.view.MotionEvent
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** I tag delle prove e delle riprese. */
object FluidInkTags {
  const val Canvas = "fluid-ink:foglio"
  const val SelectionMenu = "fluid-ink:selezione"
  const val Delete = "fluid-ink:selezione:elimina"
  const val Duplicate = "fluid-ink:selezione:duplica"
  const val Recolor = "fluid-ink:selezione:colora"
  const val RulerAngle = "fluid-ink:righello:angolo"
}

/**
 * Il foglio su cui si disegna: [sheet] sotto (un foglio bianco, una foto, le pagine di un PDF), i tratti di [state]
 * sopra, e tutto quello che fanno dito e penna.
 *
 * - **Chi disegna.** Con [FluidInkDrawingPolicy.Auto] (di serie) il dito disegna finche' non arriva la penna; poi
 *   disegna solo la penna, e il dito scorre e ingrandisce la pagina (due dita: zoom). La gomma della penna (la sua
 *   estremita', o il tasto laterale tenuto premuto, come la S Pen) cancella da sola, senza cambiare strumento. Con la
 *   penna sul foglio, un dito che tocca e' il palmo e non fa niente.
 * - **Il tratto.** Segue il dito con la latenza piu' bassa che il telefono permette: il tratto in corso lo disegna
 *   androidx.ink nel front buffer (`InProgressStrokesView`, con la predizione di un fotogramma e la consegna degli eventi
 *   senza attesa), e quando e' finito passa al foglio nello stesso fotogramma, senza lampi. Pressione, inclinazione e
 *   orientamento arrivano dalla penna dove ci sono; dal dito no (come PencilKit, che ignora la pressione del dito).
 * - **Le forme.** Un tratto tenuto fermo alla fine per mezzo secondo diventa la forma perfetta (linea, cerchio, ellisse,
 *   triangolo, rettangolo, quadrato, poligono), con un tic; una linea si allunga e si gira finche' il dito non si alza.
 * - **Il righello.** Acceso, si sposta con un dito e gira con due (si ferma ogni 45° con un tic, e dice l'angolo); un
 *   tratto che comincia vicino al suo bordo corre dritto lungo il bordo.
 * - **La gomma e il lazo** lavorano sui tratti del modello: un passo di annulla per gesto.
 * - **Il vetro.** Con [backdrop] il foglio e' la sorgente del vetro della tavolozza, ferma mentre si disegna o si
 *   scorre (il vetro non rilegge il foglio a ogni fotogramma: un solo aggiornamento a gesto finito).
 *
 * Il dito e' nitido: niente sfocatura su quello che il dito muove (tratto, pagina, righello, selezione). Con «Riduci
 * movimento» la forma riconosciuta arriva senza viaggio e la pagina lanciata non scivola.
 *
 * [contentPadding] e' lo spazio delle barre sopra il foglio: il foglio entra nella vista fra quei margini, e ci scorre
 * sotto.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun FluidInkCanvas(
  state: FluidInkState,
  sheet: FluidInkSheet,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState? = null,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  labels: FluidInkLabels = FluidInkLabels(),
  selectionActions: Boolean = true,
) {
  val density = LocalDensity.current
  val view = LocalView.current
  val haptics = rememberFluidHaptics()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val scope = rememberCoroutineScope()
  val accent = MaterialTheme.colorScheme.primary
  val ink = MaterialTheme.colorScheme.onSurface
  val direction = LocalLayoutDirection.current
  val controller = remember(state, sheet) { InkCanvasController(state, sheet) }
  controller.density = density.density
  controller.haptics = haptics
  controller.reducedMotion = reduced
  controller.scope = scope
  controller.accent = accent
  controller.ink = ink
  controller.padding = with(density) {
    Rect(
      contentPadding.calculateStartPadding(direction).toPx(), contentPadding.calculateTopPadding().toPx(),
      contentPadding.calculateEndPadding(direction).toPx(), contentPadding.calculateBottomPadding().toPx(),
    )
  }

  // Le pagine del PDF: quelle che si vedono, alla larghezza che hanno sullo schermo (a scalini, per non ridisegnarle a
  // ogni passo dello zoom). Fuori dal thread principale; una alla volta.
  val pdf = sheet.pdf
  if (pdf != null) {
    LaunchedEffect(controller, pdf) {
      snapshotFlow { controller.visiblePdfRequest() }.collect { request ->
        for (index in request.pages) pdf.prepare(index, request.widthPx)
        // Solo le pagine vicine a quelle che si vedono restano in memoria (due per parte): un PDF lungo non si accumula.
        val first = request.pages.minOrNull() ?: return@collect
        val last = request.pages.maxOrNull() ?: return@collect
        pdf.ready.keys.filter { it < first - 2 || it > last + 2 }.forEach { pdf.ready.remove(it) }
      }
    }
  }
  DisposableEffect(controller) { onDispose { controller.detachNative() } }

  val n = state.strokes.size
  val selected = state.selection.size
  Box(
    modifier
      .testTag(FluidInkTags.Canvas)
      .onSizeChanged {
        controller.size = it
        // Il foglio bianco «grande quanto la vista» e' lo spazio fra le barre: entra intero, senza scorrere a zoom 1.
        val p = controller.padding
        sheet.adapt((it.width - p.left - p.right) / density.density, (it.height - p.top - p.bottom) / density.density)
      }
      .onGloballyPositioned { controller.rootOffset = it.positionInRoot() }
      .semantics {
        contentDescription = labels.canvas(n)
        if (selected > 0) stateDescription = labels.selected(selected)
        customActions = listOfNotNull(
          if (state.canUndo) CustomAccessibilityAction(labels.undo) { state.undo(); true } else null,
          if (state.canRedo) CustomAccessibilityAction(labels.redo) { state.redo(); true } else null,
          if (selected > 0) CustomAccessibilityAction(labels.delete) { state.deleteSelection(); true } else null,
        )
      }
      .pointerInput(controller) { awaitEachGesture { with(controller) { gesture(view) } } }
      // La consegna degli eventi senza aspettare il fotogramma (come fa androidx.ink nel suo composable): un evento per
      // campione dello schermo, non uno ogni 8 ms. Non consuma niente.
      .pointerInteropFilter {
        if (it.actionMasked == MotionEvent.ACTION_DOWN) view.requestUnbufferedDispatch(it)
        false
      },
  ) {
    Spacer(
      Modifier
        .fillMaxSize()
        .then(if (backdrop != null) Modifier.glassBackdropSource(backdrop, frozen = { state.isInteracting }) else Modifier)
        .drawBehind { controller.drawSheetAndStrokes(this) },
    )
    if (FluidInk.nativeAvailable) {
      AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> InProgressStrokesView(context).also(controller::attachNative) },
        update = { it.maskPath = controller.maskPath() },
      )
    } else {
      Spacer(Modifier.fillMaxSize().drawBehind { controller.drawWetFallback(this) })
    }
    Spacer(Modifier.fillMaxSize().drawBehind { controller.drawOverlays(this) })
    if (controller.rulerAngleShown && state.rulerVisible) {
      val ruler = state.ruler
      if (ruler != null) {
        val at = controller.toScreen(ruler.center)
        Text(
          labels.rulerAngle(controller.rulerDegrees()),
          style = MaterialTheme.typography.titleMedium,
          // Sul righello, non sul tema: scuro su una capsula chiara, come l'angolo del righello di PencilKit.
          color = Color(0xFF1C1C1E),
          modifier = Modifier
            .testTag(FluidInkTags.RulerAngle)
            .offset { IntOffset((at.x - 28.dp.toPx()).roundToInt(), (at.y - 14.dp.toPx()).roundToInt()) }
            .background(Color.White.copy(alpha = 0.9f), dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
        )
      }
    }
    if (selectionActions && selected > 0 && !state.isInteracting) {
      val bounds = state.selectionBounds
      if (bounds != null) {
        CompositionLocalProvider(LocalGlassBackdrop provides (backdrop ?: LocalGlassBackdrop.current)) {
          InkSelectionMenu(state, controller, bounds, labels)
        }
      }
    }
  }
}

/** I tasti della selezione del lazo, sopra i tratti scelti: Elimina, Duplica, Colora (col colore della penna). */
@Composable
private fun InkSelectionMenu(state: FluidInkState, controller: InkCanvasController, bounds: Rect, labels: FluidInkLabels) {
  val density = LocalDensity.current
  val screen = controller.toScreen(bounds)
  val gap = with(density) { 12.dp.toPx() }
  val menuHeight = with(density) { 44.dp.toPx() }
  val above = screen.top - gap - menuHeight
  val y = if (above > controller.padding.top) above else min(screen.bottom + gap, controller.size.height - menuHeight - controller.padding.bottom)
  Row(
    Modifier
      .testTag(FluidInkTags.SelectionMenu)
      .offset { IntOffset(max(8.dp.roundToPx(), screen.left.roundToInt()), y.roundToInt()) },
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    FluidGlassButton(labels.delete, onClick = { state.deleteSelection() }, modifier = Modifier.testTag(FluidInkTags.Delete))
    FluidGlassButton(labels.duplicate, onClick = { state.duplicateSelection() }, modifier = Modifier.testTag(FluidInkTags.Duplicate))
    FluidGlassButton(labels.recolor, onClick = { state.recolorSelection(state.styleOf(FluidInkTool.Pen).color) }, modifier = Modifier.testTag(FluidInkTags.Recolor))
  }
}

/** La vista: quanti pixel per unita' del foglio, e dove sta l'angolo del foglio. */
internal class InkView(val scale: Float, val ox: Float, val oy: Float) {
  fun toWorldX(x: Float) = (x - ox) / scale
  fun toWorldY(y: Float) = (y - oy) / scale
  fun toWorld(p: Offset) = Offset(toWorldX(p.x), toWorldY(p.y))
  fun toScreen(p: Offset) = Offset(p.x * scale + ox, p.y * scale + oy)
  fun matrix(out: Matrix): Matrix = out.apply { setScale(scale, scale); postTranslate(ox, oy) }
}

/**
 * I disegni sopra il foglio (lazo, gomma, righello) stanno sulla carta o sulla foto, non sul tema: il grigio scuro di
 * sistema (`systemGray2` scuro, #636366) con un filo bianco sotto, leggibile su bianco e su una foto scura; il corpo del
 * righello e' il grigio di sistema (#8E8E93) al 35 %. A occhio sulle riprese di Note (25/11/2026).
 */
internal val OverlayInk = Color(0xFF636366)
internal val RulerFill = Color(0x598E8E93)

/** Le pagine del PDF da preparare e a che larghezza. */
internal data class InkPdfRequest(val pages: List<Int>, val widthPx: Int)

/**
 * Il cervello del foglio: la vista, i gesti, il tratto in corso, la resa e i disegni sopra. Uno per foglio
 * ([FluidInkCanvas]); legge lo stato nel disegno, cosi' un foglio fermo non si ridisegna.
 */
internal class InkCanvasController(val state: FluidInkState, val sheet: FluidInkSheet) {
  var density = 1f
  var haptics: FluidHaptics? = null
  var reducedMotion = false
  var scope: CoroutineScope? = null
  var accent: Color = Color(0xFF007AFF)
  var ink: Color = Color.Black
  var padding: Rect = Rect.Zero
  var size by mutableStateOf(IntSize.Zero)
  var rootOffset: Offset = Offset.Zero

  private val dry: InkRenderer = InkRenderer.create()
  private var retained: List<FluidInkStroke>? = null
  private val preview = InkPathRenderer()
  private val matrix = Matrix()
  private val builder = FluidInkPointsBuilder(256)

  /** Cresce a ogni punto del tratto in corso: il ripiego lo legge nel disegno e ridisegna solo lo strato del tratto. */
  private var wetRevision by mutableIntStateOf(0)
  private var wetTool: FluidInkTool = FluidInkTool.Pen
  private var wetArgb = 0
  private var wetSize = 1f
  private var wetActive by mutableStateOf(false)

  // Il lazo mentre lo si gira, la gomma, la forma riconosciuta, il righello.
  private var lassoPoints = FloatArray(256)
  private var lassoCount by mutableIntStateOf(0)
  private val lassoPath = Path()
  private var eraserAt by mutableStateOf(Offset.Unspecified)
  private var eraserRadiusPx = 0f
  private var shapePreview by mutableStateOf<FluidInkStroke?>(null)
  private var shapeFrom: FluidInkPoints? = null
  private var shapeMorph = Animatable(1f)
  var rulerAngleShown by mutableStateOf(false)
    private set
  private var flingJob: Job? = null

  // --- La vista ------------------------------------------------------------------------------------------------------

  private fun sheetSize(): Size = sheet.size

  private fun fitScale(): Float {
    val s = sheetSize()
    if (sheet.adaptive) return density
    if (s.width <= 0f || s.height <= 0f || size.width == 0) return density
    val availW = (size.width - padding.left - padding.right).coerceAtLeast(1f)
    val availH = (size.height - padding.top - padding.bottom).coerceAtLeast(1f)
    return if (sheet.fitWidth) availW / s.width else min(availW / s.width, availH / s.height)
  }

  /** La vista di adesso (legge zoom, spostamento e misura: chi la chiama nel disegno ridisegna quando cambiano). */
  fun view(): InkView {
    val scale = fitScale() * state.zoom
    val o = clampOffset(state.pan, scale)
    return InkView(scale, o.x, o.y)
  }

  private fun clampOffset(wanted: Offset, scale: Float): Offset {
    val s = sheetSize()
    val contentW = s.width * scale
    val contentH = s.height * scale
    val availW = size.width - padding.left - padding.right
    val availH = size.height - padding.top - padding.bottom
    val x = if (contentW <= availW || !wanted.isSpecified) {
      if (contentW <= availW) padding.left + (availW - contentW) / 2f else padding.left
    } else wanted.x.coerceIn(size.width - padding.right - contentW, padding.left)
    val y = if (contentH <= availH || !wanted.isSpecified) {
      if (contentH <= availH) padding.top + (availH - contentH) / 2f else padding.top
    } else wanted.y.coerceIn(size.height - padding.bottom - contentH, padding.top)
    return Offset(x, y)
  }

  fun toScreen(world: Offset): Offset = view().toScreen(world)

  fun toScreen(world: Rect): Rect {
    val v = view()
    return Rect(v.toScreen(world.topLeft), v.toScreen(world.bottomRight))
  }

  /** Le pagine del PDF che si vedono, e la larghezza in pixel a cui disegnarle (a scalini di 1,5×). */
  fun visiblePdfRequest(): InkPdfRequest {
    val v = view()
    if (size.width == 0) return InkPdfRequest(emptyList(), 0)
    val top = v.toWorldY(0f)
    val bottom = v.toWorldY(size.height.toFloat())
    val seen = sheet.pages.withIndex().filter { (_, r) -> r.bottom >= top && r.top <= bottom }.map { it.index }
    // Prima le pagine che si vedono, poi una per parte (arrivano gia' pronte scorrendo).
    val pages = if (seen.isEmpty()) seen else seen + listOf(seen.first() - 1, seen.last() + 1).filter { it in sheet.pages.indices }
    val widest = sheet.pages.maxOfOrNull { it.width } ?: 1f
    val target = widest * v.scale
    var step = size.width.toFloat()
    while (step < target && step < InkPdfPages.MaxPagePixels) step *= 1.5f
    return InkPdfRequest(pages, step.roundToInt().coerceAtMost(InkPdfPages.MaxPagePixels))
  }

  // --- Il tratto nativo ----------------------------------------------------------------------------------------------

  private var native: InProgressStrokesView? = null
  private var predictor: MotionEventPredictor? = null
  private var nativeId: InProgressStrokeId? = null
  private var nativeSent = 0
  private val nativePending = HashMap<InProgressStrokeId, FluidInkStroke>()
  // Pigri: toccano la libreria nativa, che sul computer non c'e' (li usa solo il tratto nativo).
  private val scratchInput by lazy(LazyThreadSafetyMode.NONE) { StrokeInput() }
  private val batch by lazy(LazyThreadSafetyMode.NONE) { MutableStrokeInputBatch() }
  private val predicted by lazy(LazyThreadSafetyMode.NONE) { MutableStrokeInputBatch() }
  private var nativeBrush: Brush? = null
  private val androidMask = android.graphics.Path()

  // --- La misura della latenza (solo con `adb shell setprop log.tag.FluidInkLatency DEBUG`) ---------------------------

  private var latencyOn = false
  private val latency by lazy(LazyThreadSafetyMode.NONE) { InkLatencyProbe() }

  /** Dal momento in cui il sensore ha visto il dito a quello in cui il foglio lo riceve (la consegna di Compose). */
  private fun recordInputDelay(motion: MotionEvent?) {
    if (!latencyOn || motion == null) return
    val event = if (Build.VERSION.SDK_INT >= 34) motion.eventTimeNanos else motion.eventTime * 1_000_000L
    latency.input(event, System.nanoTime())
  }

  /** A tratto consegnato (i dati di resa di androidx.ink arrivano dopo l'ultimo disegno): una riga nel log, poi da capo. */
  private fun logLatency() {
    if (!latencyOn) return
    if (latency.inputCount + latency.renderCount + latency.totalCount == 0) return
    android.util.Log.d(LatencyTag, latency.summary())
    latency.reset()
  }

  @OptIn(androidx.ink.authoring.ExperimentalLatencyDataApi::class)
  fun attachNative(view: InProgressStrokesView) {
    native = view
    view.eagerInit()
    predictor = MotionEventPredictor.newInstance(view)
    latencyOn = android.util.Log.isLoggable(LatencyTag, android.util.Log.DEBUG)
    if (latencyOn) {
      // I dati di androidx.ink per ogni punto vero: dalla sua chiamata, e dal sensore, ai pixel stimati sullo schermo.
      view.setLatencyDataCallback { data ->
        latency.ink(
          add = data.strokeAction == androidx.ink.authoring.latency.LatencyData.StrokeAction.ADD,
          osDetectsNanos = if (data.isOsDetectsEventSet) data.osDetectsEvent else Long.MIN_VALUE,
          getsActionNanos = if (data.isStrokesViewGetsActionSet) data.strokesViewGetsAction else Long.MIN_VALUE,
          presentationNanos = data.estimatedPixelPresentationTime,
          frontBufferDoneNanos = data.canvasFrontBufferStrokesRenderHelperData.let { if (it.isFinishesDrawCallsSet) it.finishesDrawCalls else Long.MIN_VALUE },
        )
      }
    }
    view.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
      override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        val renderer = dry as? InkNativeRenderer
        for ((id, finished) in strokes) {
          val mine = nativePending.remove(id) ?: continue
          renderer?.adopt(mine, finished)
          state.wetHeld.remove(mine.id)
        }
        // Nello stesso giro del thread: il foglio disegna i tratti nel fotogramma in cui il tratto veloce li lascia.
        view.removeFinishedStrokes(strokes.keys)
        logLatency()
      }
    })
  }

  fun detachNative() {
    native?.clearFinishedStrokesListeners()
    native = null
    predictor = null
    for (s in nativePending.values) state.wetHeld.remove(s.id)
    nativePending.clear()
  }

  /** Le zone dove il tratto nativo non si vede (la tavolozza, le barre), nelle coordinate del foglio. */
  fun maskPath(): android.graphics.Path? {
    val masks = state.inkMasks.values
    if (masks.isEmpty()) return null
    androidMask.rewind()
    for (r in masks) {
      androidMask.addRect(r.left - rootOffset.x, r.top - rootOffset.y, r.right - rootOffset.x, r.bottom - rootOffset.y, android.graphics.Path.Direction.CW)
    }
    return androidMask
  }

  private fun inputToolType(input: FluidInkInput) = InkBrushes.inputType(input)

  private fun nativeBegin(input: FluidInkInput, v: InkView) {
    val view = native ?: return
    try {
      val brush = InkBrushes.brush(wetTool, wetArgb, wetSize, InkBrushes.epsilonFor(1f / v.scale))
      nativeBrush = brush
      fill(scratchInput, 0, input)
      nativeId = view.startStroke(scratchInput, brush, v.matrix(Matrix()))
      nativeSent = 1
    } catch (e: RuntimeException) {
      nativeId = null
    }
  }

  private fun fill(out: StrokeInput, i: Int, input: FluidInkInput) {
    out.update(
      builder.x(i), builder.y(i), builder.time(i).toLong(), inputToolType(input), StrokeInput.NO_STROKE_UNIT_LENGTH,
      builder.pressure(i).let { if (it < 0f) StrokeInput.NO_PRESSURE else it },
      builder.tilt(i).let { if (it < 0f) StrokeInput.NO_TILT else it },
      builder.orientation(i).let { if (it < 0f) StrokeInput.NO_ORIENTATION else it },
    )
  }

  private fun nativeAppend(input: FluidInkInput, until: Int, prediction: MotionEvent?, local: Offset, v: InkView) {
    val view = native ?: return
    val id = nativeId ?: return
    try {
      batch.clear()
      for (i in nativeSent until until) {
        fill(scratchInput, i, input)
        batch.add(scratchInput)
      }
      nativeSent = max(nativeSent, until)
      predicted.clear()
      if (prediction != null && builder.size > 0 && prediction.pointerCount > 0) {
        val lastT = builder.lastTime.toLong()
        val px = v.toWorldX(prediction.getX(0) + local.x)
        val py = v.toWorldY(prediction.getY(0) + local.y)
        // Un fotogramma avanti (8 ms a 120 Hz): la predizione arriva al momento del prossimo disegno.
        scratchInput.update(px, py, lastT + 8L,
          inputToolType(input), StrokeInput.NO_STROKE_UNIT_LENGTH,
          if (builder.knowsPressure) prediction.getPressure(0).coerceIn(0f, 1f) else StrokeInput.NO_PRESSURE,
          if (builder.size > 0 && builder.tilt(0) >= 0f) builder.tilt(builder.size - 1) else StrokeInput.NO_TILT,
          if (builder.size > 0 && builder.orientation(0) >= 0f) builder.orientation(builder.size - 1) else StrokeInput.NO_ORIENTATION,
        )
        if (px != builder.lastX || py != builder.lastY) predicted.add(scratchInput)
      }
      view.addToStroke(batch, id, predicted)
    } catch (e: RuntimeException) {
      // Un ingresso che androidx.ink rifiuta: il tratto continua nel modello, e il foglio lo disegna a fine gesto.
    }
  }

  private fun nativeFinish(stroke: FluidInkStroke, input: FluidInkInput): Boolean {
    val view = native ?: return false
    val id = nativeId ?: return false
    nativeId = null
    return try {
      if (builder.size >= 2) {
        nativeAppendRange(input, builder.size - 1, id)
        fill(scratchInput, builder.size - 1, input)
        view.finishStroke(scratchInput, id)
      } else {
        fill(scratchInput, 0, input)
        view.finishStroke(scratchInput, id)
      }
      nativePending[id] = stroke
      state.wetHeld.add(stroke.id)
      true
    } catch (e: RuntimeException) {
      try { view.cancelStroke(id, null) } catch (_: RuntimeException) {}
      false
    }
  }

  private fun nativeAppendRange(input: FluidInkInput, until: Int, id: InProgressStrokeId) {
    val view = native ?: return
    if (until <= nativeSent) return
    batch.clear()
    for (i in nativeSent until until) {
      fill(scratchInput, i, input)
      batch.add(scratchInput)
    }
    nativeSent = until
    predicted.clear()
    view.addToStroke(batch, id, predicted)
  }

  private fun nativeCancel() {
    val view = native ?: return
    val id = nativeId ?: return
    nativeId = null
    nativeByMotion = false
    try { view.cancelStroke(id, null) } catch (_: RuntimeException) {}
  }

  // La via degli eventi veri: androidx.ink legge da se' i punti storici, pressione, inclinazione e orientamento della penna,
  // e compila i suoi dati di latenza ([InkLatencyProbe]). Gli eventi sono nelle coordinate della radice di Compose.
  private var nativeByMotion = false
  private var nativePointer = -1

  private fun nativeBeginMotion(event: MotionEvent, pointerId: Int, local: Offset, v: InkView) {
    val view = native ?: return
    try {
      val index = event.findPointerIndex(pointerId)
      if (index < 0) return
      val t = InkMotionTransforms(local.x - event.getX(index), local.y - event.getY(index), v.scale, v.ox, v.oy)
      view.motionEventToViewTransform = t.toView
      val brush = InkBrushes.brush(wetTool, wetArgb, wetSize, InkBrushes.epsilonFor(1f / v.scale))
      nativeBrush = brush
      nativeId = view.startStroke(event, pointerId, brush, t.toWorld, Matrix())
      nativeByMotion = true
      nativePointer = pointerId
    } catch (e: RuntimeException) {
      nativeId = null
      nativeByMotion = false
    }
  }

  private fun nativeAddMotion(event: MotionEvent?, prediction: MotionEvent?) {
    val view = native ?: return
    val id = nativeId ?: return
    if (event == null || event.findPointerIndex(nativePointer) < 0) return
    try {
      view.addToStroke(event, nativePointer, id, prediction)
    } catch (e: RuntimeException) {
      // Un evento che androidx.ink rifiuta: il tratto continua nel modello, e il foglio lo disegna a fine gesto.
    }
  }

  /** Chiude il tratto veloce degli eventi veri; falso se non si e' potuto (il foglio disegna subito il tratto). */
  private fun nativeFinishMotion(event: MotionEvent?, stroke: FluidInkStroke): Boolean {
    val view = native ?: return false
    val id = nativeId ?: return false
    nativeId = null
    nativeByMotion = false
    if (event == null || event.findPointerIndex(nativePointer) < 0) {
      try { view.cancelStroke(id, null) } catch (_: RuntimeException) {}
      return false
    }
    return try {
      view.finishStroke(event, nativePointer, id)
      nativePending[id] = stroke
      state.wetHeld.add(stroke.id)
      true
    } catch (e: RuntimeException) {
      try { view.cancelStroke(id, null) } catch (_: RuntimeException) {}
      false
    }
  }

  // --- I gesti -------------------------------------------------------------------------------------------------------

  /** Un gesto intero, dal primo dito giu' all'ultimo su. */
  suspend fun AwaitPointerEventScope.gesture(view: View) {
    val first = awaitFirstDown(requireUnconsumed = false)
    if (first.isConsumed) return
    flingJob?.cancel()
    val event = currentEvent
    val motion = event.motionEvent
    val stylusLike = first.type == PointerType.Stylus || first.type == PointerType.Eraser
    if (stylusLike) state.stylusSeen = true
    predictor?.let { p -> motion?.let { p.record(it) } }
    val stylusButton = stylusLike && motion != null && (motion.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
    val finger = first.type == PointerType.Touch
    when {
      finger && state.rulerVisible && hitsRuler(first.position) -> rulerGesture(first)
      finger && !state.fingerDraws -> navigate(first)
      else -> toolGesture(first, stylusButton)
    }
  }

  /** Lo strumento sotto [first]: la gomma della penna (l'estremita' o il tasto laterale) vince sullo strumento scelto. */
  private suspend fun AwaitPointerEventScope.toolGesture(first: PointerInputChange, stylusButton: Boolean) {
    val tool = if (first.type == PointerType.Eraser || stylusButton) FluidInkTool.Eraser else state.tool
    val input = when (first.type) {
      PointerType.Stylus, PointerType.Eraser -> FluidInkInput.Stylus
      PointerType.Mouse -> FluidInkInput.Mouse
      else -> FluidInkInput.Touch
    }
    when {
      tool.isInking -> inkGesture(first, tool, input)
      tool == FluidInkTool.Eraser -> eraseGesture(first, input)
      else -> lassoGesture(first, input)
    }
  }

  /**
   * La penna che scende mentre un dito e' gia' sul foglio: il dito era il palmo appoggiato (succede quasi sempre: il palmo
   * tocca prima della punta). Il gesto del dito si lascia, e disegna la penna, come in PencilKit.
   */
  private fun stylusArrived(event: PointerEvent): PointerInputChange? =
    event.changes.firstOrNull { it.pressed && !it.previousPressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }

  private suspend fun AwaitPointerEventScope.stylusTakesOver(pen: PointerInputChange) {
    state.stylusSeen = true
    val motion = currentEvent.motionEvent
    val button = motion != null && (motion.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
    toolGesture(pen, button)
  }

  private fun motionPointerId(event: PointerEvent, change: PointerInputChange): Int? {
    val motion = event.motionEvent ?: return null
    return motion.getPointerId(motion.actionIndex.coerceIn(0, motion.pointerCount - 1)).takeIf { change.pressed }
  }

  /**
   * I campioni di [change] in questo evento (prima quelli accumulati, poi l'ultimo), con pressione, inclinazione e
   * orientamento dalla penna. Le posizioni sono quelle locali di Compose; i valori della penna vengono dal
   * `MotionEvent`, per lo stesso puntatore e lo stesso indice di storia.
   */
  private inline fun samples(
    event: PointerEvent,
    change: PointerInputChange,
    pointerId: Int?,
    stylus: Boolean,
    sink: (x: Float, y: Float, uptime: Long, pressure: Float, tilt: Float, orientation: Float) -> Unit,
  ) {
    val motion = event.motionEvent
    val index = if (motion != null && pointerId != null) motion.findPointerIndex(pointerId) else -1
    val history = change.historical
    for (h in history.indices) {
      val item = history[h]
      if (stylus && index >= 0 && h < motion!!.historySize) {
        sink(item.position.x, item.position.y, item.uptimeMillis,
          motion.getHistoricalAxisValue(MotionEvent.AXIS_PRESSURE, index, h),
          motion.getHistoricalAxisValue(MotionEvent.AXIS_TILT, index, h),
          orientation(motion.getHistoricalAxisValue(MotionEvent.AXIS_ORIENTATION, index, h)))
      } else {
        sink(item.position.x, item.position.y, item.uptimeMillis, if (stylus) change.pressure else FluidInkPoints.None, FluidInkPoints.None, FluidInkPoints.None)
      }
    }
    if (stylus && index >= 0) {
      sink(change.position.x, change.position.y, change.uptimeMillis,
        motion!!.getAxisValue(MotionEvent.AXIS_PRESSURE, index),
        motion.getAxisValue(MotionEvent.AXIS_TILT, index),
        orientation(motion.getAxisValue(MotionEvent.AXIS_ORIENTATION, index)))
    } else {
      sink(change.position.x, change.position.y, change.uptimeMillis, if (stylus) change.pressure else FluidInkPoints.None, FluidInkPoints.None, FluidInkPoints.None)
    }
  }

  /** L'orientamento di Android ([-π, π], 0 in su) in quello di androidx.ink ([0, 2π), 0 a sinistra): come fa la libreria. */
  private fun orientation(android: Float): Float = (android + 2.5f * Math.PI.toFloat()).mod(2f * Math.PI.toFloat())

  /** Vero se in questo evento il sistema ha detto che il puntatore era un palmo (Android 13+: `FLAG_CANCELED`). */
  private fun palm(event: PointerEvent): Boolean {
    val motion = event.motionEvent ?: return false
    if (Build.VERSION.SDK_INT < 33) return motion.actionMasked == MotionEvent.ACTION_CANCEL
    val action = motion.actionMasked
    return (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_UP) &&
      (motion.flags and MotionEvent.FLAG_CANCELED) != 0
  }

  private fun dp(value: Float) = value * density

  private suspend fun AwaitPointerEventScope.inkGesture(first: PointerInputChange, tool: FluidInkTool, input: FluidInkInput) {
    state.isInteracting = true
    if (state.autoMinimize) state.paletteMinimized = true
    val v = view()
    val stylus = input == FluidInkInput.Stylus
    val style = state.styleOf(tool)
    wetTool = tool
    wetArgb = (if (tool == FluidInkTool.Highlighter) style.color.copy(alpha = style.color.alpha * FluidInkDefaults.HighlighterAlpha) else style.color).toArgb()
    wetSize = dp(FluidInkDefaults.widths(tool)[style.widthIndex.coerceIn(0, 4)].value) / v.scale
    val start = first.uptimeMillis
    val edge = if (state.rulerVisible) rulerEdgeNear(first.position, v) else null
    val pointer = first.id
    val pointerId = motionPointerId(currentEvent, first)
    builder.clear()
    val projected = FloatArray(2)
    fun add(x: Float, y: Float, uptime: Long, pressure: Float, tilt: Float, orientation: Float) {
      var wx = v.toWorldX(x)
      var wy = v.toWorldY(y)
      if (edge != null) {
        InkGeometry.projectOnLine(wx, wy, edge.ox, edge.oy, edge.ux, edge.uy, projected)
        wx = projected[0] + edge.nx * wetSize / 2f
        wy = projected[1] + edge.ny * wetSize / 2f
      }
      builder.add(wx, wy, (uptime - start).toFloat(), if (stylus) pressure else FluidInkPoints.None,
        if (stylus) tilt else FluidInkPoints.None, if (stylus) orientation else FluidInkPoints.None)
    }
    samples(currentEvent, first, pointerId, stylus) { x, y, t, p, tilt, o -> add(x, y, t, p, tilt, o) }
    first.consume()
    wetActive = true
    wetRevision++
    // Gli eventi veri ad androidx.ink (con la sua latenza misurata), tranne il tratto lungo il righello, che l'engine sposta.
    val startMotion = currentEvent.motionEvent
    if (native != null && inkUsesMotionEvents(edge != null, startMotion != null, pointerId != null)) {
      recordInputDelay(startMotion)
      nativeBeginMotion(startMotion!!, pointerId!!, first.position, v)
    } else {
      nativeBegin(input, v)
    }
    var anchor = first.position
    var stillSince = first.uptimeMillis
    var lastUptime = first.uptimeMillis
    var screenTravel = 0f
    var recognized: InkRecognizedShape? = null
    var tried = false
    val holdSlop = dp(FluidInkDefaults.ShapeHoldSlop.value)
    val canShape = state.shapeRecognition && edge == null
    while (true) {
      val waitFor = if (canShape && recognized == null && !tried) {
        (FluidInkDefaults.ShapeHoldMillis - (lastUptime - stillSince)).coerceAtLeast(1L)
      } else Long.MAX_VALUE
      val event = withTimeoutOrNull(waitFor) { awaitPointerEvent() }
      if (event == null) {
        // Il dito e' fermo da mezzo secondo: la forma.
        tried = true
        lastUptime += waitFor
        val shape = InkShapeRecognizer.recognize(builder.build(), minSize = dp(FluidInkDefaults.ShapeMinSize.value) / v.scale)
        if (shape != null) {
          recognized = shape
          nativeCancel()
          wetActive = false
          wetRevision++
          showShape(shape, tool)
          haptics?.play(FluidHapticEvent.Tick)
        }
        continue
      }
      predictor?.let { p -> event.motionEvent?.let { p.record(it) } }
      if (input == FluidInkInput.Touch) {
        val pen = stylusArrived(event)
        if (pen != null) {
          cancelInk()
          stylusTakesOver(pen)
          return
        }
      }
      val change = event.changes.firstOrNull { it.id == pointer }
      // Un secondo dito presto, su un tratto fatto col dito: non era un tratto, erano due dita per la pagina.
      if (input == FluidInkInput.Touch && event.changes.any { it.id != pointer && it.pressed && it.type == PointerType.Touch }) {
        val young = lastUptime - start < FluidInkDefaults.TwoFingerGraceMillis && screenTravel < dp(FluidInkDefaults.TwoFingerGraceDistance.value)
        if (young && recognized == null) {
          cancelInk()
          navigateFrom(event)
          return
        }
      }
      if (change == null || change.isConsumed || palm(event)) {
        cancelInk()
        return
      }
      lastUptime = change.uptimeMillis
      if (!change.pressed) {
        if (recognized == null) samples(event, change, pointerId, stylus) { x, y, t, p, tilt, o -> add(x, y, t, p, tilt, o) }
        change.consume()
        finishInk(tool, input, recognized, event.motionEvent)
        return
      }
      if (change.positionChanged()) {
        screenTravel += (change.position - change.previousPosition).getDistance()
        if (recognized != null) {
          if (recognized.kind == FluidInkShapeKind.Line) adjustLine(v.toWorld(change.position), tool)
        } else {
          samples(event, change, pointerId, stylus) { x, y, t, p, tilt, o -> add(x, y, t, p, tilt, o) }
          wetRevision++
          if (native != null) {
            val motion = event.motionEvent
            val prediction = if (motion != null) predictor?.predict() else null
            recordInputDelay(motion)
            if (nativeByMotion) {
              nativeAddMotion(motion, prediction)
            } else {
              val local = if (motion != null && pointerId != null && motion.findPointerIndex(pointerId) >= 0) {
                val i = motion.findPointerIndex(pointerId)
                Offset(change.position.x - motion.getX(i), change.position.y - motion.getY(i))
              } else Offset.Zero
              nativeAppend(input, builder.size, prediction, local, v)
            }
            prediction?.recycle()
          }
        }
        if ((change.position - anchor).getDistance() > holdSlop) {
          anchor = change.position
          stillSince = change.uptimeMillis
          if (recognized == null) tried = false
        }
        change.consume()
      }
    }
  }

  private fun cancelInk() {
    nativeCancel()
    wetActive = false
    wetRevision++
    shapePreview = null
    builder.clear()
    state.isInteracting = false
  }

  private fun finishInk(tool: FluidInkTool, input: FluidInkInput, recognized: InkRecognizedShape?, last: MotionEvent?) {
    val color = Color(wetArgb)
    if (recognized != null) {
      val shaped = shapePreview
      shapePreview = null
      if (shaped != null) state.add(shaped.copy(id = state.newStrokeId()))
    } else if (builder.size > 0) {
      val stroke = FluidInkStroke(state.newStrokeId(), tool, color, wetSize, builder.build(), input)
      if (nativeByMotion) nativeFinishMotion(last, stroke) else nativeFinish(stroke, input)
      state.add(stroke)
    }
    wetActive = false
    wetRevision++
    builder.clear()
    state.isInteracting = false
  }

  private fun showShape(shape: InkRecognizedShape, tool: FluidInkTool) {
    val raw = builder.build()
    val base = FluidInkStroke(0L, tool, Color(wetArgb), wetSize, raw, FluidInkInput.Synthetic)
    shapePreview = InkShapeRecognizer.strokeFor(base, shape)
    shapeFrom = raw
    val s = scope
    if (reducedMotion || s == null) {
      shapeMorph = Animatable(1f)
    } else {
      val morph = Animatable(0f)
      shapeMorph = morph
      s.launch { morph.animateTo(1f, tween(FluidInkDefaults.ShapeMorphMillis, easing = LinearEasing)) }
    }
  }

  /** La linea riconosciuta segue il dito finche' non si alza: si allunga e gira attorno al suo inizio. */
  private fun adjustLine(to: Offset, tool: FluidInkTool) {
    val current = shapePreview ?: return
    val p = current.points
    if (p.size < 2) return
    val ax = p.x(0)
    val ay = p.y(0)
    val outline = InkShapeRecognizer.dense(floatArrayOf(ax, ay, to.x, to.y), closed = false, step = max(1f, hypot(to.x - ax, to.y - ay) / 32f))
    shapePreview = current.copy(points = InkShapeRecognizer.outlinePoints(outline, shapeFrom ?: p), tool = tool)
  }

  private suspend fun AwaitPointerEventScope.eraseGesture(first: PointerInputChange, input: FluidInkInput) {
    state.isInteracting = true
    state.beginEdit()
    val v = view()
    val area = state.eraserMode == FluidInkEraserMode.Area
    val widthDp = if (area) FluidInkDefaults.EraserWidths[state.styleOf(FluidInkTool.Eraser).widthIndex.coerceIn(0, 4)].value
    else FluidInkDefaults.ObjectEraserWidth.value
    eraserRadiusPx = dp(widthDp) / 2f
    val radius = eraserRadiusPx / v.scale
    var prev = v.toWorld(first.position)
    erase(prev, prev, radius, area)
    if (area) eraserAt = first.position
    first.consume()
    val pointer = first.id
    val start = first.uptimeMillis
    while (true) {
      val event = awaitPointerEvent()
      if (input == FluidInkInput.Touch) {
        val pen = stylusArrived(event)
        if (pen != null) {
          state.cancelEdit()
          eraserAt = Offset.Unspecified
          state.isInteracting = false
          stylusTakesOver(pen)
          return
        }
      }
      val change = event.changes.firstOrNull { it.id == pointer }
      if (input == FluidInkInput.Touch && event.changes.any { it.id != pointer && it.pressed && it.type == PointerType.Touch } &&
        (change?.uptimeMillis ?: start) - start < FluidInkDefaults.TwoFingerGraceMillis) {
        state.cancelEdit()
        eraserAt = Offset.Unspecified
        state.isInteracting = false
        navigateFrom(event)
        return
      }
      if (change == null || change.isConsumed || palm(event)) {
        state.cancelEdit()
        break
      }
      val history = change.historical
      for (h in history.indices) {
        val p = v.toWorld(history[h].position)
        erase(prev, p, radius, area)
        prev = p
      }
      val p = v.toWorld(change.position)
      erase(prev, p, radius, area)
      prev = p
      if (area) eraserAt = change.position
      change.consume()
      if (!change.pressed) {
        state.endEdit()
        break
      }
    }
    eraserAt = Offset.Unspecified
    state.isInteracting = false
  }

  private fun erase(a: Offset, b: Offset, radius: Float, area: Boolean) {
    if (area) state.eraseArea(a, b, radius) else state.eraseObjects(a, b, radius)
  }

  private suspend fun AwaitPointerEventScope.lassoGesture(first: PointerInputChange, input: FluidInkInput) {
    state.isInteracting = true
    val v = view()
    val pointer = first.id
    val selected = state.selectionBounds
    val grab = dp(12f)
    if (selected != null) {
      val screen = toScreen(selected)
      if (Rect(screen.left - grab, screen.top - grab, screen.right + grab, screen.bottom + grab).contains(first.position)) {
        // Si trascina la selezione: un passo solo.
        state.beginEdit()
        first.consume()
        var last = first.position
        while (true) {
          val event = awaitPointerEvent()
          val change = event.changes.firstOrNull { it.id == pointer }
          if (change == null || change.isConsumed) {
            state.cancelEdit()
            break
          }
          val delta = change.position - last
          last = change.position
          state.moveSelection(delta.x / v.scale, delta.y / v.scale)
          change.consume()
          if (!change.pressed) {
            state.endEdit()
            break
          }
        }
        state.isInteracting = false
        return
      }
    }
    state.clearSelection()
    lassoCount = 0
    fun addLasso(p: Offset) {
      val w = v.toWorld(p)
      if (lassoCount > 0) {
        val lx = lassoPoints[(lassoCount - 1) * 2]
        val ly = lassoPoints[(lassoCount - 1) * 2 + 1]
        if (hypot(w.x - lx, w.y - ly) * v.scale < 2f) return
      }
      if ((lassoCount + 1) * 2 > lassoPoints.size) lassoPoints = lassoPoints.copyOf(lassoPoints.size * 2)
      lassoPoints[lassoCount * 2] = w.x
      lassoPoints[lassoCount * 2 + 1] = w.y
      lassoCount++
    }
    addLasso(first.position)
    first.consume()
    while (true) {
      val event = awaitPointerEvent()
      val change = event.changes.firstOrNull { it.id == pointer }
      if (change == null || change.isConsumed) break
      for (h in change.historical) addLasso(h.position)
      addLasso(change.position)
      change.consume()
      if (!change.pressed) {
        if (lassoCount >= 3 && state.selectInLasso(lassoPoints.copyOf(lassoCount * 2))) haptics?.play(FluidHapticEvent.Tick)
        break
      }
    }
    lassoCount = 0
    state.isInteracting = false
  }

  private suspend fun AwaitPointerEventScope.navigate(first: PointerInputChange) {
    first.consume()
    navigateFrom(null)
  }

  /** Scorre e ingrandisce la pagina con le dita che sono giu' (una scorre, due anche ingrandiscono). */
  private suspend fun AwaitPointerEventScope.navigateFrom(initial: PointerEvent?) {
    state.isInteracting = true
    val tracker = VelocityTracker()
    var pending = initial
    var lastUptime = 0L
    var fingers = 0
    while (true) {
      val event = pending ?: awaitPointerEvent()
      pending = null
      val pen = stylusArrived(event)
      if (pen != null) {
        state.isInteracting = false
        stylusTakesOver(pen)
        return
      }
      val touching = event.changes.filter { it.type == PointerType.Touch || it.type == PointerType.Mouse }
      val pressed = touching.filter { it.pressed }
      if (pressed.isEmpty()) break
      val moving = pressed.filter { it.previousPressed }
      if (moving.isNotEmpty()) {
        var cx = 0f; var cy = 0f; var px = 0f; var py = 0f
        for (c in moving) { cx += c.position.x; cy += c.position.y; px += c.previousPosition.x; py += c.previousPosition.y }
        cx /= moving.size; cy /= moving.size; px /= moving.size; py /= moving.size
        var factor = 1f
        if (moving.size >= 2) {
          var span = 0f
          var prevSpan = 0f
          for (c in moving) {
            span += hypot(c.position.x - cx, c.position.y - cy)
            prevSpan += hypot(c.previousPosition.x - px, c.previousPosition.y - py)
          }
          if (prevSpan > 1f) factor = span / prevSpan
        }
        applyPanZoom(Offset(px, py), Offset(cx, cy), factor)
        if (moving.size != fingers) tracker.resetTracking()
        fingers = moving.size
        tracker.addPosition(moving[0].uptimeMillis, Offset(cx, cy))
        lastUptime = moving[0].uptimeMillis
      }
      event.changes.forEach { if (it.positionChanged() || it.pressed != it.previousPressed) it.consume() }
    }
    state.isInteracting = false
    val velocity = tracker.calculateVelocity()
    if (!reducedMotion && fingers == 1 && hypot(velocity.x, velocity.y) > dp(400f) && lastUptime > 0L) fling(Offset(velocity.x, velocity.y))
  }

  /** Lo spostamento da [from] a [to] (il centro delle dita) e lo zoom di [factor] attorno a [to]. */
  fun applyPanZoom(from: Offset, to: Offset, factor: Float) {
    val v = view()
    val zoom = (state.zoom * factor).coerceIn(1f, FluidInkDefaults.MaxZoom)
    val real = zoom / state.zoom
    val scale = v.scale * real
    val ox = to.x - (from.x - v.ox) * real
    val oy = to.y - (from.y - v.oy) * real
    state.zoom = zoom
    state.pan = clampOffset(Offset(ox, oy), scale)
  }

  /** La pagina lanciata scivola e si ferma (il dito l'ha lanciata: niente sfocatura). */
  private fun fling(velocity: Offset) {
    val s = scope ?: return
    flingJob?.cancel()
    flingJob = s.launch {
      var last = Offset.Zero
      AnimationState(typeConverter = Offset.VectorConverter, initialValue = Offset.Zero, initialVelocity = velocity)
        .animateDecay(exponentialDecay()) {
          val delta = value - last
          last = value
          val v = view()
          val before = Offset(v.ox, v.oy)
          state.pan = clampOffset(before + delta, v.scale)
          if (state.pan == before && delta.getDistance() > 0.5f) cancelAnimation()
        }
    }
  }

  // --- Il righello ---------------------------------------------------------------------------------------------------

  /** Un bordo del righello nel foglio: un punto, la direzione e la normale verso fuori. */
  internal class RulerEdge(val ox: Float, val oy: Float, val ux: Float, val uy: Float, val nx: Float, val ny: Float)

  private fun rulerOrDefault(): FluidInkRuler {
    state.ruler?.let { return it }
    val v = view()
    val center = v.toWorld(Offset(size.width / 2f, size.height / 2f))
    return FluidInkRuler(center, 0f).also { state.ruler = it }
  }

  fun rulerDegrees(): Int {
    val deg = Math.toDegrees((state.ruler?.angle ?: 0f).toDouble()).roundToInt()
    return ((deg % 360) + 360) % 360
  }

  private fun hitsRuler(p: Offset): Boolean {
    val r = rulerOrDefault()
    val v = view()
    val c = v.toScreen(r.center)
    val dx = p.x - c.x
    val dy = p.y - c.y
    val across = -dx * sin(r.angle) + dy * cos(r.angle)
    return abs(across) <= dp(FluidInkDefaults.RulerHeight.value) / 2f
  }

  private fun rulerEdgeNear(p: Offset, v: InkView): RulerEdge? {
    val r = rulerOrDefault()
    val c = v.toScreen(r.center)
    val ux = cos(r.angle)
    val uy = sin(r.angle)
    val across = -(p.x - c.x) * uy + (p.y - c.y) * ux
    val half = dp(FluidInkDefaults.RulerHeight.value) / 2f
    val snap = dp(FluidInkDefaults.RulerSnap.value)
    val side = if (across >= 0f) 1f else -1f
    if (abs(abs(across) - half) > snap) return null
    // Il bordo vicino, nelle coordinate del foglio; la normale va verso fuori dal righello.
    val nx = -uy * side
    val ny = ux * side
    val edgeWorld = v.toWorld(Offset(c.x + nx * half, c.y + ny * half))
    return RulerEdge(edgeWorld.x, edgeWorld.y, ux, uy, nx, ny)
  }

  private suspend fun AwaitPointerEventScope.rulerGesture(first: PointerInputChange) {
    state.isInteracting = true
    first.consume()
    var detent: Int? = null
    while (true) {
      val event = awaitPointerEvent()
      val pressed = event.changes.filter { it.pressed && it.type == PointerType.Touch }
      if (pressed.isEmpty()) break
      val v = view()
      val r = rulerOrDefault()
      if (pressed.size >= 2) {
        rulerAngleShown = true
        val a = pressed[0]
        val b = pressed[1]
        val now = atan2(b.position.y - a.position.y, b.position.x - a.position.x)
        val before = atan2(b.previousPosition.y - a.previousPosition.y, b.previousPosition.x - a.previousPosition.x)
        var angle = r.angle + (now - before)
        val mid = Offset((a.position.x + b.position.x) / 2f, (a.position.y + b.position.y) / 2f)
        val midBefore = Offset((a.previousPosition.x + b.previousPosition.x) / 2f, (a.previousPosition.y + b.previousPosition.y) / 2f)
        // Ogni 45° il righello si ferma per un attimo, con un tic.
        val degrees = Math.toDegrees(angle.toDouble()).toFloat()
        val nearest = Math.round(degrees / FluidInkDefaults.RulerDetentDegrees) * FluidInkDefaults.RulerDetentDegrees
        if (abs(degrees - nearest) <= FluidInkDefaults.RulerDetentSlack) {
          angle = Math.toRadians(nearest.toDouble()).toFloat()
          val key = nearest.roundToInt()
          if (detent != key) {
            detent = key
            haptics?.play(FluidHapticEvent.Tick)
          }
        } else detent = null
        val center = r.center + (mid - midBefore) / v.scale
        state.ruler = FluidInkRuler(center, angle)
      } else {
        val c = pressed[0]
        state.ruler = r.copy(center = r.center + (c.position - c.previousPosition) / v.scale)
      }
      event.changes.forEach { it.consume() }
    }
    rulerAngleShown = false
    state.isInteracting = false
  }

  // --- Il disegno ----------------------------------------------------------------------------------------------------

  fun drawSheetAndStrokes(scope: DrawScope) = with(scope) {
    val v = view()
    // La carta, la foto o le pagine.
    val paper = sheet.paper
    val image = sheet.image
    val pdf = sheet.pdf
    for ((index, page) in sheet.pages.withIndex()) {
      val tl = v.toScreen(page.topLeft)
      val sz = Size(page.width * v.scale, page.height * v.scale)
      if (tl.y > size.height || tl.y + sz.height < 0f) continue
      when {
        image != null -> drawImage(image, dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(sz.width.roundToInt(), sz.height.roundToInt()))
        pdf != null -> {
          drawRect(Color.White, tl, sz)
          val ready = pdf.ready[index]
          if (ready != null) drawImage(ready.image, dstOffset = IntOffset(tl.x.roundToInt(), tl.y.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(sz.width.roundToInt(), sz.height.roundToInt()))
        }
        paper != null -> drawRect(paper, tl, sz)
      }
    }
    val strokes = state.strokes
    val held = state.wetHeld
    // La cache dei tratti nativi segue il disegno solo quando la lista cambia: scorrere e ingrandire non allocano.
    if (strokes !== retained) {
      dry.retain(strokes)
      retained = strokes
    }
    (dry as? InkNativeRenderer)?.epsilon = InkBrushes.epsilonFor(1f / max(v.scale / state.zoom, 1e-3f))
    v.matrix(matrix)
    drawIntoCanvas { canvas ->
      val c = canvas.nativeCanvas
      for (s in strokes) {
        if (held.contains(s.id)) continue
        val b = s.bounds
        val left = b.left * v.scale + v.ox
        val top = b.top * v.scale + v.oy
        val right = b.right * v.scale + v.ox
        val bottom = b.bottom * v.scale + v.oy
        if (right < 0f || bottom < 0f || left > size.width || top > size.height) continue
        dry.draw(c, s, matrix)
      }
    }
  }

  fun drawWetFallback(scope: DrawScope) = with(scope) {
    wetRevision
    if (!wetActive || builder.size == 0) return@with
    val v = view()
    v.matrix(matrix)
    drawIntoCanvas { canvas ->
      preview.drawPacked(canvas.nativeCanvas, builder.packed, builder.size, builder.knowsPressure, wetTool, wetArgb, wetSize, matrix)
    }
  }

  private val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))

  fun drawOverlays(scope: DrawScope) = with(scope) {
    val v = view()
    // Il righello.
    if (state.rulerVisible) drawRuler(v)
    // La forma riconosciuta (con il suo viaggio dal tratto a mano).
    val shape = shapePreview
    if (shape != null) {
      v.matrix(matrix)
      val t = shapeMorph.value
      drawIntoCanvas { canvas ->
        val from = shapeFrom
        if (t < 1f && from != null) {
          val morphed = morph(from, shape.points, t)
          preview.drawPacked(canvas.nativeCanvas, morphed.data, morphed.size, morphed.hasPressure, shape.tool, shape.color.toArgb(), shape.size, matrix)
        } else {
          preview.draw(canvas.nativeCanvas, shape, matrix)
        }
      }
    }
    // Il lazo che si gira.
    if (lassoCount > 1) {
      lassoPath.reset()
      lassoPath.moveTo(lassoPoints[0] * v.scale + v.ox, lassoPoints[1] * v.scale + v.oy)
      for (i in 1 until lassoCount) lassoPath.lineTo(lassoPoints[i * 2] * v.scale + v.ox, lassoPoints[i * 2 + 1] * v.scale + v.oy)
      // Sul foglio, non sul tema: un filo chiaro sotto e il tratteggio scuro sopra, leggibili su carta bianca e su una foto scura.
      drawPath(lassoPath, Color.White.copy(alpha = 0.85f), style = DrawStroke(width = 3.dp.toPx()))
      drawPath(lassoPath, OverlayInk, style = DrawStroke(width = 1.5.dp.toPx(), pathEffect = dash))
    }
    // La selezione.
    val selected = state.selectionBounds
    if (selected != null) {
      val r = toScreen(selected)
      val pad = 6.dp.toPx()
      drawRoundRect(
        accent, topLeft = Offset(r.left - pad, r.top - pad), size = Size(r.width + pad * 2, r.height + pad * 2),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()),
        style = DrawStroke(width = 1.5.dp.toPx(), pathEffect = dash),
      )
    }
    // La gomma a pixel: il suo cerchio sotto il dito.
    val at = eraserAt
    if (at.isSpecified) {
      drawCircle(OverlayInk.copy(alpha = 0.10f), eraserRadiusPx, at)
      drawCircle(Color.White.copy(alpha = 0.85f), eraserRadiusPx, at, style = DrawStroke(width = 2.5.dp.toPx()))
      drawCircle(OverlayInk, eraserRadiusPx, at, style = DrawStroke(width = 1.dp.toPx()))
    }
  }

  private fun DrawScope.drawRuler(v: InkView) {
    val r = rulerOrDefault()
    val c = v.toScreen(r.center)
    val height = FluidInkDefaults.RulerHeight.toPx()
    val length = hypot(size.width, size.height) * 2f
    rotate(Math.toDegrees(r.angle.toDouble()).toFloat(), pivot = c) {
      // Il righello sta sul foglio: grigio di sistema translucido (`systemGray`), uguale in chiaro e in scuro, come PencilKit.
      drawRect(RulerFill, Offset(c.x - length / 2f, c.y - height / 2f), Size(length, height))
      drawLine(OverlayInk.copy(alpha = 0.6f), Offset(c.x - length / 2f, c.y - height / 2f), Offset(c.x + length / 2f, c.y - height / 2f), 1.dp.toPx())
      drawLine(OverlayInk.copy(alpha = 0.6f), Offset(c.x - length / 2f, c.y + height / 2f), Offset(c.x + length / 2f, c.y + height / 2f), 1.dp.toPx())
      // Le tacche: ogni 5 dp sullo schermo, piu' lunghe ogni 25 (il righello dice la misura che si vede).
      val step = 5.dp.toPx()
      val count = (length / step).toInt()
      for (i in -count / 2..count / 2) {
        val x = c.x + i * step
        val long = i % 5 == 0
        val tick = if (long) 12.dp.toPx() else 6.dp.toPx()
        drawLine(OverlayInk.copy(alpha = 0.75f), Offset(x, c.y - height / 2f), Offset(x, c.y - height / 2f + tick), 1.dp.toPx())
        drawLine(OverlayInk.copy(alpha = 0.75f), Offset(x, c.y + height / 2f), Offset(x, c.y + height / 2f - tick), 1.dp.toPx())
      }
    }
  }

  /** Il tratto a mano che diventa la forma: ogni punto del tratto, ricampionato, va verso il punto corrispondente. */
  private fun morph(from: FluidInkPoints, to: FluidInkPoints, t: Float): FluidInkPoints {
    val count = to.size
    val src = FloatArray(from.size * 2) { if (it % 2 == 0) from.x(it / 2) else from.y(it / 2) }
    val uniform = InkShapeRecognizer.resampleUniform(src, from.size, count)
    val b = FluidInkPointsBuilder(count)
    for (i in 0 until count) {
      b.add(InkGeometry.lerp(uniform[i * 2], to.x(i), t), InkGeometry.lerp(uniform[i * 2 + 1], to.y(i), t), to.time(i), to.pressure(i))
    }
    return b.build()
  }
}
