package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDropLens
import dev.antigravity.fluidengine.ui.glass.interaction.glassDrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Il riordino per trascinamento (3.0, fase 12c). Tre app se lo erano scritto: `GridReorder` di
// FluidWeather (la sola matematica, col gesto a parte), `CollectionsReorder` di MyCloud (uno stato col
// suo trascinamento e le altezze misurate a mano) e `ProviderOrderList` di classeviva e PampAI (due
// frecce, perche' un trascinamento non c'era). Qui il gesto, il moto, l'aptica e la matematica sono uno,
// e funzionano su una `LazyColumn`, una `LazyRow` e una `LazyVerticalGrid` allo stesso modo.

/**
 * Le parole dell'accessibilita' del riordino, in italiano di serie.
 *
 * Chi non puo' trascinare (TalkBack, la tastiera, un interruttore) sposta una riga con due azioni, e
 * sono le stesse due frecce che `ProviderOrderList` metteva sulla riga: qui non sono tasti da disegnare,
 * sono azioni del nodo.
 */
@Immutable
data class FluidReorderStrings(
  val moveBefore: String = "Sposta prima",
  val moveAfter: String = "Sposta dopo",
)

/** Le misure del riordino. */
object FluidReorderDefaults {

  /**
   * Di quanto cresce un elemento sollevato. Poco: una riga che cresce del 4% e' una cosa presa in mano,
   * del 10% e' un pulsante, e una tessera di griglia piu' grande invaderebbe le vicine.
   */
  const val LiftScale: Float = 1.04f

  /** Quanto e' larga la fascia, ai bordi della lista, in cui trascinare fa scorrere la lista. */
  val AutoScrollEdge: Dp = 72.dp

  /** La velocita' massima dello scorrimento automatico, in dp al secondo: a un bordo sono piu' di quattro righe al secondo. */
  val AutoScrollMaxSpeed: Dp = 900.dp

  /** L'ombra dell'elemento sollevato dove non c'e' vetro (sotto API 31, o senza un canvas): di piattaforma, perche' li' il vetro non c'e'. */
  val FallbackShadow: Dp = 10.dp
}

/** Lungo quale asse si riordina. */
internal enum class FluidReorderAxis { Vertical, Horizontal, Both }

/**
 * Un elemento della lista com'e' adesso, **in pixel nel riquadro della lista**.
 *
 * Per una lista a un asse l'altro asse non lo conosce nessuno (`LazyListItemInfo` dice solo la posizione
 * lungo quello di scorrimento): l'elemento prende tutta la larghezza e la corsa dell'altro asse non conta
 * mai, perche' [FluidReorder.targetBox] e [FluidReorder.dragTranslation] guardano solo l'asse della lista.
 */
@Immutable
internal data class FluidReorderBox(
  val key: Any,
  val index: Int,
  val left: Float,
  val top: Float,
  val width: Float,
  val height: Float,
) {
  val right: Float get() = left + width
  val bottom: Float get() = top + height
  fun containsX(x: Float): Boolean = x >= left && x < right
  fun containsY(y: Float): Boolean = y >= top && y < bottom
}

/**
 * La matematica del riordino, pura e provata (`FluidReorderTest`): sopra chi si sta passando, dove sta
 * l'elemento sotto il dito, quando la lista deve scorrere da sola.
 *
 * Il guscio con i `pointerInput` e' sottile apposta. E' la divisione che aveva fatto `GridReorder` di
 * FluidWeather, e che teneva in piedi gli unici due test di riordino che le app avevano: i gesti si
 * guardano in mano, i conti si provano sul computer.
 */
object FluidReorder {

  /**
   * L'elemento (diverso da quello trascinato) sotto il **centro** della sagoma trascinata, se c'e'.
   *
   * Il centro e non il bordo: si scambia a meta' della corsa, quando la sagoma e' per meta' sopra il
   * vicino, che e' dove una mano si aspetta che succeda. E dopo lo scambio non si torna indietro da soli:
   * l'elemento trascinato sta nel posto nuovo e il suo centro e' dentro **il suo** riquadro, non in
   * quello che ha appena lasciato — niente oscillazioni, senza bisogno di un'isteresi.
   * In una lista a un asse conta solo quell'asse; in una griglia, tutti e due. Nello spazio fra due
   * elementi (la spaziatura) non c'e' nessuno, e non si scambia.
   */
  internal fun targetBox(
    centerX: Float,
    centerY: Float,
    draggedKey: Any,
    boxes: List<FluidReorderBox>,
    axis: FluidReorderAxis,
  ): FluidReorderBox? = boxes.firstOrNull { box ->
    box.key != draggedKey && when (axis) {
      FluidReorderAxis.Vertical -> box.containsY(centerY)
      FluidReorderAxis.Horizontal -> box.containsX(centerX)
      FluidReorderAxis.Both -> box.containsX(centerX) && box.containsY(centerY)
    }
  }

  /**
   * Di quanto l'elemento trascinato sta lontano dal **suo posto in lista**, in pixel: e' la traslazione
   * del suo layer.
   *
   * Si calcola dalla posizione che aveva quando e' stato sollevato piu' quanto si e' mosso il dito, **meno
   * dove sta adesso**: cosi' resta sotto il dito qualunque cosa succeda alla lista — uno scambio che gli
   * cambia posto, uno scorrimento automatico che gli sposta il posto sotto i piedi. In una lista a un
   * asse l'altro asse e' bloccato; e la sagoma non esce mai dal riquadro (tenuta la fuori-corsa, un dito che
   * va oltre non la porta con se').
   */
  internal fun dragTranslation(
    box: FluidReorderBox,
    initialLeft: Float,
    initialTop: Float,
    dragX: Float,
    dragY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    axis: FluidReorderAxis,
  ): Offset {
    var x = initialLeft + dragX - box.left
    var y = initialTop + dragY - box.top
    when (axis) {
      FluidReorderAxis.Vertical -> x = 0f
      FluidReorderAxis.Horizontal -> y = 0f
      FluidReorderAxis.Both -> Unit
    }
    x = (box.left + x).coerceIn(0f, max(0f, viewportWidth - box.width)) - box.left
    y = (box.top + y).coerceIn(0f, max(0f, viewportHeight - box.height)) - box.top
    return Offset(x, y)
  }

  /**
   * Di quanto far scorrere la lista in questo fotogramma, in pixel: negativo verso l'inizio, positivo
   * verso la fine, zero se la sagoma e' lontana dai bordi.
   *
   * Conta il **bordo della sagoma**, non il dito: un elemento alto e' al bordo molto prima che il dito ci
   * arrivi, e aspettare il dito vorrebbe dire sollevare un elemento fin fuori dalla vista. La velocita'
   * cresce col **quadrato** della profondita' nella fascia [zone]: piano all'ingresso, cosi' si puo'
   * fermarsi, piena al bordo. [seconds] e' il tempo del fotogramma, per una velocita' in pixel al secondo.
   */
  fun autoScrollDelta(
    visualStart: Float,
    visualEnd: Float,
    viewportStart: Float,
    viewportEnd: Float,
    zone: Float,
    maxSpeedPx: Float,
    seconds: Float,
  ): Float {
    if (zone <= 0f || maxSpeedPx <= 0f || seconds <= 0f) return 0f
    val toStart = visualStart - viewportStart
    val toEnd = viewportEnd - visualEnd
    val speed = when {
      toStart < zone -> -ramp(toStart, zone)
      toEnd < zone -> ramp(toEnd, zone)
      else -> 0f
    }
    return speed * maxSpeedPx * seconds
  }

  private fun ramp(distance: Float, zone: Float): Float {
    val depth = 1f - (distance / zone).coerceIn(0f, 1f)
    return depth * depth
  }
}

/**
 * La lista con [from] spostato in [to]: l'ordine nuovo dopo un `onMove`. Gli altri scalano.
 *
 * Fuori intervallo, o dallo stesso al stesso, restituisce la lista com'e'. E' quello che ogni app
 * scriveva alla fine del suo gestore (`GridReorder.moved`, `CollectionsLayout.moved`) e che sbagliava
 * su un caso diverso (uno spostamento verso il basso che dimenticava di togliere uno all'indice).
 */
fun <T> List<T>.fluidMoved(from: Int, to: Int): List<T> {
  if (from !in indices || to !in indices || from == to) return this
  val result = toMutableList()
  result.add(to, result.removeAt(from))
  return result
}

// ---------------------------------------------------------------------------------------------
// La lista vista dal riordino
// ---------------------------------------------------------------------------------------------

/** Cio' che il riordino chiede alla lista: dove sono gli elementi, quanto e' grande, come si fa scorrere. */
internal interface FluidReorderLayout {
  val axis: FluidReorderAxis

  /** Vero se si scorre in verticale (e' l'asse dello scorrimento automatico). */
  val mainIsVertical: Boolean

  val totalItems: Int
  val viewportWidth: Float
  val viewportHeight: Float

  fun boxes(): List<FluidReorderBox>

  /** Scorre di [px] lungo l'asse della lista; restituisce quanto ha scorso davvero (zero in fondo o in cima). */
  suspend fun scrollBy(px: Float): Float
}

private class LazyListReorderLayout(private val state: LazyListState) : FluidReorderLayout {
  private val vertical: Boolean
    get() = state.layoutInfo.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
  override val axis: FluidReorderAxis get() = if (vertical) FluidReorderAxis.Vertical else FluidReorderAxis.Horizontal
  override val mainIsVertical: Boolean get() = vertical
  override val totalItems: Int get() = state.layoutInfo.totalItemsCount
  override val viewportWidth: Float get() = state.layoutInfo.viewportSize.width.toFloat()
  override val viewportHeight: Float get() = state.layoutInfo.viewportSize.height.toFloat()

  override fun boxes(): List<FluidReorderBox> {
    val info = state.layoutInfo
    val vertical = info.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
    val crossWidth = info.viewportSize.width.toFloat()
    val crossHeight = info.viewportSize.height.toFloat()
    return info.visibleItemsInfo.map { item ->
      if (vertical) {
        FluidReorderBox(item.key, item.index, 0f, item.offset.toFloat(), crossWidth, item.size.toFloat())
      } else {
        FluidReorderBox(item.key, item.index, item.offset.toFloat(), 0f, item.size.toFloat(), crossHeight)
      }
    }
  }

  override suspend fun scrollBy(px: Float): Float = state.scrollBy(px)
}

private class LazyGridReorderLayout(private val state: LazyGridState) : FluidReorderLayout {
  override val axis: FluidReorderAxis get() = FluidReorderAxis.Both
  override val mainIsVertical: Boolean
    get() = state.layoutInfo.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
  override val totalItems: Int get() = state.layoutInfo.totalItemsCount
  override val viewportWidth: Float get() = state.layoutInfo.viewportSize.width.toFloat()
  override val viewportHeight: Float get() = state.layoutInfo.viewportSize.height.toFloat()

  override fun boxes(): List<FluidReorderBox> = state.layoutInfo.visibleItemsInfo.map { item ->
    FluidReorderBox(
      key = item.key,
      index = item.index,
      left = item.offset.x.toFloat(),
      top = item.offset.y.toFloat(),
      width = item.size.width.toFloat(),
      height = item.size.height.toFloat(),
    )
  }

  override suspend fun scrollBy(px: Float): Float = state.scrollBy(px)
}

// ---------------------------------------------------------------------------------------------
// Lo stato
// ---------------------------------------------------------------------------------------------

private enum class ReorderPhase { Idle, Dragging, Settling }

/**
 * Lo stato di un riordino: chi e' sollevato, di quanto, e cosa succede quando si sposta o si posa.
 *
 * Uno per lista ([rememberFluidReorderState]). **Un solo elemento alla volta**: finche' uno e' sollevato
 * o si sta posando, nessun altro si solleva.
 *
 * Tutto cio' che il disegno legge e' stato di snapshot e si legge nel `layerBlock` degli elementi: il dito
 * che si muove non ricompone niente, ridisegna il layer dell'unico elemento che si muove.
 */
@Stable
class FluidReorderState internal constructor(
  private val layout: FluidReorderLayout,
  private val scope: CoroutineScope,
  private val haptics: () -> FluidHaptics,
  private val reducedMotion: () -> Boolean,
  private val onMove: () -> (Int, Int) -> Unit,
  private val onDragEnd: () -> () -> Unit,
  private val canMoveTo: () -> (Int) -> Boolean,
  private val autoScrollEdgePx: () -> Float,
  private val autoScrollMaxSpeedPx: () -> Float,
) {

  /** Il dito che ha la maniglia e il peso che regge: la goccia, con la sua pressione e la sua scala. */
  internal val drop = GlassDragAnimation(
    animationScope = scope,
    initialValue = 0f,
    valueRange = 0f..1f,
    visibilityThreshold = 0.001f,
    initialScale = 1f,
    pressedScale = FluidReorderDefaults.LiftScale,
  )

  private var phase by mutableStateOf(ReorderPhase.Idle)

  /** La chiave dell'elemento sollevato, o che si sta posando; null se nessuno. */
  var draggingKey: Any? by mutableStateOf(null)
    private set

  /** Vero mentre un dito tiene un elemento. */
  val isDragging: Boolean get() = phase == ReorderPhase.Dragging

  private var dragX by mutableFloatStateOf(0f)
  private var dragY by mutableFloatStateOf(0f)
  private var initialLeft = 0f
  private var initialTop = 0f
  private var settleX by mutableStateOf(Animatable(0f))
  private var settleY by mutableStateOf(Animatable(0f))
  private val tracker = VelocityTracker()
  private var autoScroll: Job? = null

  // Fra "ho chiesto lo spostamento" e "la lista l'ha fatto" passa almeno un fotogramma: finche' l'elemento
  // non e' arrivato al posto nuovo, rivalutare vorrebbe dire chiedere due volte lo stesso spostamento
  // con un indice vecchio.
  private var pendingTo: Int? = null
  private var pendingWaits = 0

  /** Quanto e' sollevato, per il disegno: la scala della goccia per l'elemento sollevato, 1 per gli altri. */
  internal fun liftScale(key: Any): Float = if (key == draggingKey && !reducedMotion()) drop.scaleX else 1f

  /** La pressione della goccia per [key], 0..1: serve all'ombra di ripiego. */
  internal fun liftProgress(key: Any): Float = if (key == draggingKey && !reducedMotion()) drop.pressProgress else 0f

  /** La traslazione del layer di [key]: di quanto sta lontano dal suo posto in lista. */
  internal fun translationOf(key: Any): Offset {
    if (key != draggingKey) return Offset.Zero
    if (phase == ReorderPhase.Settling) return Offset(settleX.value, settleY.value)
    val box = layout.boxes().firstOrNull { it.key == key } ?: return Offset.Zero
    return FluidReorder.dragTranslation(
      box, initialLeft, initialTop, dragX, dragY, layout.viewportWidth, layout.viewportHeight, layout.axis,
    )
  }

  /** Si puo' sollevare [key]? Solo a riposo, e se e' in lista. */
  internal fun canLift(key: Any): Boolean =
    phase == ReorderPhase.Idle && layout.boxes().any { it.key == key }

  internal fun beginDrag(key: Any): Boolean {
    if (phase != ReorderPhase.Idle) return false
    val box = layout.boxes().firstOrNull { it.key == key } ?: return false
    initialLeft = box.left
    initialTop = box.top
    dragX = 0f
    dragY = 0f
    tracker.resetTracking()
    tracker.addPosition(0L, Offset.Zero)
    pendingTo = null
    draggingKey = key
    phase = ReorderPhase.Dragging
    if (!reducedMotion()) drop.press()
    haptics().play(FluidHapticEvent.GestureStart)
    ensureAutoScroll()
    return true
  }

  /** Il dito si e' mosso di [delta] pixel dello schermo. */
  internal fun dragBy(delta: Offset, uptimeMillis: Long) {
    if (phase != ReorderPhase.Dragging) return
    dragX += delta.x
    dragY += delta.y
    tracker.addPosition(uptimeMillis, Offset(dragX, dragY))
    evaluate()
    ensureAutoScroll()
  }

  /** Il dito si e' sollevato (o il gesto e' stato interrotto): l'elemento si posa nel suo posto. */
  internal fun endDrag() {
    if (phase != ReorderPhase.Dragging) return
    autoScroll?.cancel()
    val key = draggingKey ?: return
    val from = translationOf(key)
    val velocity = tracker.calculateVelocity()
    drop.release()
    haptics().play(FluidHapticEvent.GestureEnd)
    onDragEnd()()
    if (reducedMotion() || from == Offset.Zero) {
      reset()
      return
    }
    // La molla parte dalla velocita' del dito: la posa e' il seguito del gesto, non un altro. Critica,
    // perche' un elemento che si posa non rimbalza (sono le stesse molle delle pagine che tornano).
    val x = Animatable(from.x)
    val y = Animatable(from.y)
    settleX = x
    settleY = y
    phase = ReorderPhase.Settling
    scope.launch {
      val spec = FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)
      val a = launch { x.animateTo(0f, spec, initialVelocity = velocity.x) }
      val b = launch { y.animateTo(0f, spec, initialVelocity = velocity.y) }
      a.join()
      b.join()
      if (phase == ReorderPhase.Settling && draggingKey == key) reset()
    }
  }

  /** L'elemento sollevato non e' piu' in lista (i dati sono cambiati sotto il dito): si lascia andare. */
  internal fun itemGone(key: Any) {
    if (key != draggingKey || phase != ReorderPhase.Dragging) return
    autoScroll?.cancel()
    drop.release()
    onDragEnd()()
    reset()
  }

  private fun reset() {
    draggingKey = null
    phase = ReorderPhase.Idle
    pendingTo = null
  }

  /**
   * Sposta [key] di [delta] posti (-1 prima, +1 dopo) **senza trascinare**: la strada per TalkBack, per la
   * tastiera e per chi ha un interruttore. Vero se lo spostamento e' stato chiesto.
   */
  fun moveBy(key: Any, delta: Int): Boolean {
    if (phase != ReorderPhase.Idle) return false
    val box = layout.boxes().firstOrNull { it.key == key } ?: return false
    val to = box.index + delta
    if (to !in 0 until layout.totalItems || !canMoveTo()(to)) return false
    onMove()(box.index, to)
    haptics().play(FluidHapticEvent.Tick)
    onDragEnd()()
    return true
  }

  /** Rivaluta dove sta la sagoma e, se ha passato un vicino, chiede lo spostamento. */
  private fun evaluate() {
    val key = draggingKey ?: return
    if (phase != ReorderPhase.Dragging) return
    val boxes = layout.boxes()
    val me = boxes.firstOrNull { it.key == key } ?: return itemGone(key)

    pendingTo?.let { to ->
      if (me.index == to) {
        pendingTo = null
      } else if (++pendingWaits > PendingPatience) {
        pendingTo = null
      } else {
        return
      }
    }

    val t = FluidReorder.dragTranslation(
      me, initialLeft, initialTop, dragX, dragY, layout.viewportWidth, layout.viewportHeight, layout.axis,
    )
    val target = FluidReorder.targetBox(
      centerX = me.left + t.x + me.width / 2f,
      centerY = me.top + t.y + me.height / 2f,
      draggedKey = key,
      boxes = boxes,
      axis = layout.axis,
    ) ?: return
    if (!canMoveTo()(target.index)) return

    pendingTo = target.index
    pendingWaits = 0
    onMove()(me.index, target.index)
    // Un'aggancio per ogni posto attraversato: e' la tacca vera di un riordino. Lo scorrimento automatico
    // ne puo' attraversare uno a ogni fotogramma, e il cancello dei Tick (40 ms) li dirada da solo.
    haptics().play(FluidHapticEvent.Tick)
  }

  /**
   * Di quanto far scorrere la lista in [seconds] secondi: dal bordo della sagoma sollevata e dalla fascia ai
   * lati ([FluidReorder.autoScrollDelta]). Zero se la sagoma e' lontana dai bordi.
   */
  private fun autoScrollDelta(seconds: Float): Float {
    val key = draggingKey ?: return 0f
    val me = layout.boxes().firstOrNull { it.key == key } ?: return 0f
    val t = translationOf(key)
    val vertical = layout.mainIsVertical
    val start = if (vertical) me.top + t.y else me.left + t.x
    val size = if (vertical) me.height else me.width
    val viewport = if (vertical) layout.viewportHeight else layout.viewportWidth
    return FluidReorder.autoScrollDelta(
      visualStart = start,
      visualEnd = start + size,
      viewportStart = 0f,
      viewportEnd = viewport,
      zone = autoScrollEdgePx(),
      maxSpeedPx = autoScrollMaxSpeedPx(),
      seconds = seconds,
    )
  }

  /**
   * Fa partire lo scorrimento automatico se la sagoma e' vicina a un bordo, e non prima: il ciclo chiede un
   * fotogramma dopo l'altro, e **tenerlo acceso per tutto il trascinamento** vorrebbe dire un orologio che gira
   * anche quando la lista sta ferma e il dito e' in mezzo (e una schermata che non e' mai a riposo, per chi la
   * prova). Finisce da solo quando la sagoma esce dalla fascia, e il prossimo movimento del dito lo riaccende.
   */
  private fun ensureAutoScroll() {
    if (autoScroll?.isActive == true || autoScrollDelta(1f) == 0f) return
    autoScroll = scope.launch {
      var last = withFrameMillis { it }
      while (isActive && phase == ReorderPhase.Dragging) {
        val now = withFrameMillis { it }
        val seconds = (now - last) / 1000f
        last = now
        val delta = autoScrollDelta(seconds)
        if (delta == 0f && autoScrollDelta(1f) == 0f) break
        if (delta != 0f) {
          // In cima o in fondo non c'e' piu' niente da scorrere: il ciclo si ferma, e il prossimo
          // movimento del dito lo riaccende se serve.
          if (layout.scrollBy(delta) == 0f) break
          evaluate()
        }
      }
    }
  }

  private companion object {
    /** Quante rivalutazioni si aspetta che la lista esegua uno spostamento prima di richiederlo. */
    const val PendingPatience = 3
  }
}

/** Lo stato del riordino di una `LazyColumn` o `LazyRow`. Vedi [fluidReorderable]. */
@Composable
fun rememberFluidReorderState(
  listState: LazyListState,
  onMove: (from: Int, to: Int) -> Unit,
  onDragEnd: () -> Unit = {},
  canMoveTo: (index: Int) -> Boolean = { true },
): FluidReorderState = rememberFluidReorderStateFor(remember(listState) { LazyListReorderLayout(listState) }, onMove, onDragEnd, canMoveTo)

/** Lo stato del riordino di una `LazyVerticalGrid`. Vedi [fluidReorderable]. */
@Composable
fun rememberFluidReorderState(
  gridState: LazyGridState,
  onMove: (from: Int, to: Int) -> Unit,
  onDragEnd: () -> Unit = {},
  canMoveTo: (index: Int) -> Boolean = { true },
): FluidReorderState = rememberFluidReorderStateFor(remember(gridState) { LazyGridReorderLayout(gridState) }, onMove, onDragEnd, canMoveTo)

@Composable
private fun rememberFluidReorderStateFor(
  layout: FluidReorderLayout,
  onMove: (from: Int, to: Int) -> Unit,
  onDragEnd: () -> Unit,
  canMoveTo: (index: Int) -> Boolean,
): FluidReorderState {
  val scope = rememberCoroutineScope()
  val haptics = rememberFluidHaptics()
  val density = LocalDensity.current
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val currentHaptics by rememberUpdatedState(haptics)
  val currentReduced by rememberUpdatedState(reduced)
  val currentOnMove by rememberUpdatedState(onMove)
  val currentOnDragEnd by rememberUpdatedState(onDragEnd)
  val currentCanMoveTo by rememberUpdatedState(canMoveTo)
  val edgePx = with(density) { FluidReorderDefaults.AutoScrollEdge.toPx() }
  val speedPx = with(density) { FluidReorderDefaults.AutoScrollMaxSpeed.toPx() }
  return remember(layout, scope) {
    FluidReorderState(
      layout = layout,
      scope = scope,
      haptics = { currentHaptics },
      reducedMotion = { currentReduced },
      onMove = { currentOnMove },
      onDragEnd = { currentOnDragEnd },
      canMoveTo = { currentCanMoveTo },
      autoScrollEdgePx = { edgePx },
      autoScrollMaxSpeedPx = { speedPx },
    )
  }
}

// ---------------------------------------------------------------------------------------------
// Il modificatore
// ---------------------------------------------------------------------------------------------

/**
 * Rende un elemento di una `LazyColumn` o `LazyRow` riordinabile: **tieni premuto, sollevi, trascini**.
 *
 * ```
 * val reorder = rememberFluidReorderState(listState, onMove = { from, to -> items = items.fluidMoved(from, to) })
 * LazyColumn(state = listState) {
 *   items(items, key = { it.id }) { item ->
 *     Box(fluidReorderable(reorder, key = item.id).fillMaxWidth()) { ... }
 *   }
 * }
 * ```
 *
 * E' un'estensione dello **scope dell'elemento** (`LazyItemScope`, `LazyGridItemScope`) e non di `Modifier`: i
 * vicini scalano con `animateItem`, che esiste solo li' dentro. Si chiama senza prefisso, e restituisce un
 * `Modifier` da cui continuare la catena.
 *
 * **Cosa fa, e il perche' di ogni pezzo**
 *
 *  - **Si solleva come una goccia.** Un elemento preso in mano e' cio' che la ricetta del vetro chiama una
 *    goccia (`glass/interaction/GlassDrop.kt`): sotto il dito diventa lente. Dove c'e' un canvas
 *    ambientale e il vetro e' disponibile, dietro l'elemento si apre la goccia (a riposo non c'e' nulla:
 *    la lista e' la lista); dove non c'e', cresce e proietta un'ombra. In **tutti e due** i casi la
 *    crescita sta nel layer dell'elemento e **mai** in un antenato del vetro: un layer scalato sopra un
 *    vetro rifrange cio' che non c'e' piu'. La scala del vetro e' nel suo `layerBlock`, e quella del
 *    contenuto nel layer che lo segue: due numeri uguali dalla stessa molla.
 *  - **Segue il dito sul serio**: la traslazione e' dalla posizione che l'elemento aveva, meno dove sta
 *    adesso. Uno scambio che gli cambia posto in lista, o uno scorrimento che glielo sposta sotto i
 *    piedi, non lo spostano sotto il dito.
 *  - **I vicini scalano con `FluidMotion`** (`animateItem`, molla standard); quello trascinato no, perche'
 *    si muove per il dito, non per la lista.
 *  - **Un `Tick` per ogni posto attraversato**, `GestureStart` al sollevamento, `GestureEnd` alla posa: un
 *    momento, un colpo.
 *  - **La lista scorre da sola** vicino ai bordi (`FluidReorder.autoScrollDelta`: il bordo della sagoma,
 *    non il dito; piano all'ingresso, pieno al bordo).
 *  - **Si posa dalla velocita' del dito**, con una molla critica: l'elemento non rimbalza nel suo posto.
 *  - **Per TalkBack due azioni**, «Sposta prima» e «Sposta dopo» ([FluidReorderStrings]).
 *
 * `onMove(from, to)` arriva con gli **indici della lista pigra**: se prima degli elementi riordinabili ci
 * sono altri `item` (una testata), vanno tolti. Lo scambio avviene **a ogni posto passato**, non al
 * rilascio: l'ordine nuovo e' gia' nella lista quando il dito si solleva, e `onDragEnd` e' il momento di
 * salvarlo. [fluidMoved] fa la parte che tutte le app sbagliavano.
 *
 * **Servono le chiavi** (`items(key = ...)`): senza, la lista non sa quale elemento e' dove, e i vicini non
 * possono scalare. [key] e' la stessa dell'`item`. L'elemento sollevato non esce dal riquadro della lista, e
 * se la lista ha un clip lo ha anche lui: serve un po' di margine (`contentPadding`) perche' la crescita e
 * l'ombra non vengano tagliate.
 *
 * @param enabled falso fuori dalla modalita' «ordina»: il tenuto premuto resta alla riga (un menu, una
 *   selezione). Un elemento con `onLongClick` suo e' un elemento che e' riordinabile solo da disabilitato.
 * @param shape la forma della goccia e dell'ombra: quella dell'elemento.
 */
@Composable
@Suppress("ModifierFactoryExtensionFunction") // firma pubblicata: l'estensione sullo scope della lista serve ad animateItem, cambiare il ricevitore rompe i chiamanti
fun LazyItemScope.fluidReorderable(
  state: FluidReorderState,
  key: Any,
  enabled: Boolean = true,
  shape: Shape = ContinuousCornerShape(FluidRadius.Card),
  strings: FluidReorderStrings = FluidReorderStrings(),
): Modifier = Modifier.fluidReorderableChain(
  state = state,
  key = key,
  enabled = enabled,
  shape = shape,
  strings = strings,
  animate = { spec ->
    Modifier.animateItem(
      fadeInSpec = FluidMotion.fadeIn(),
      placementSpec = spec,
      fadeOutSpec = FluidMotion.fadeOut(),
    )
  },
)

/** [fluidReorderable] per un elemento di una `LazyVerticalGrid`: tutti e due gli assi, i vicini scalano. */
@Composable
@Suppress("ModifierFactoryExtensionFunction") // firma pubblicata: l'estensione sullo scope della lista serve ad animateItem, cambiare il ricevitore rompe i chiamanti
fun LazyGridItemScope.fluidReorderable(
  state: FluidReorderState,
  key: Any,
  enabled: Boolean = true,
  shape: Shape = ContinuousCornerShape(FluidRadius.Card),
  strings: FluidReorderStrings = FluidReorderStrings(),
): Modifier = Modifier.fluidReorderableChain(
  state = state,
  key = key,
  enabled = enabled,
  shape = shape,
  strings = strings,
  animate = { spec ->
    Modifier.animateItem(
      fadeInSpec = FluidMotion.fadeIn(),
      placementSpec = spec,
      fadeOutSpec = FluidMotion.fadeOut(),
    )
  },
)

@Composable
private fun Modifier.fluidReorderableChain(
  state: FluidReorderState,
  key: Any,
  enabled: Boolean,
  shape: Shape,
  strings: FluidReorderStrings,
  animate: (FiniteAnimationSpec<IntOffset>?) -> Modifier,
): Modifier {
  val lifted = state.draggingKey == key
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val canvas = LocalFluidCanvasBackdrop.current
  val glass = lifted && !reduced && canvas != null && isRenderEffectSupported()
  // Un nome che non e' quello della proprieta' del `GraphicsLayerScope` dentro cui lo si usa.
  val liftShape = shape

  // Un elemento sollevato a cui la lista toglie il posto non puo' piu' essere tenuto.
  DisposableEffect(state, key) {
    onDispose { state.itemGone(key) }
  }

  return this
    // Il gesto per primo (piu' esterno): le sue coordinate sono quelle del posto in lista, **non** quelle
    // del layer che si muove sotto il dito. Se stesse dentro il layer traslato, l'elemento seguirebbe il dito
    // e il dito, visto dall'elemento, non si muoverebbe mai.
    .then(
      if (enabled) Modifier.pointerInput(state, key) { reorderGesture(state, key) } else Modifier,
    )
    .semantics {
      if (enabled) {
        customActions = listOf(
          CustomAccessibilityAction(strings.moveBefore) { state.moveBy(key, -1) },
          CustomAccessibilityAction(strings.moveAfter) { state.moveBy(key, +1) },
        )
      }
    }
    // I vicini scalano; quello in mano e quello che si sta posando no, perche' li muove un'altra cosa.
    .then(animate(if (lifted || reduced) null else FluidMotion.intOffset()))
    // Sopra tutti: in una lista gli elementi si disegnano nell'ordine del layout, e quello sollevato deve
    // passare sopra i vicini anche quando viene prima di loro.
    .zIndex(if (lifted) 1f else 0f)
    // La traslazione: lo spostamento rispetto al suo posto. E' l'unica cosa che sta sopra il vetro.
    .graphicsLayer {
      val t = state.translationOf(key)
      translationX = t.x
      translationY = t.y
    }
    // La goccia, dietro il contenuto: la sua scala e' nel suo `layerBlock`.
    .then(
      if (glass) {
        Modifier.glassDrop(
          backdrop = canvas!!.backdrop,
          drag = state.drop,
          lensSize = GlassDropLens.Slider,
          // Niente squash: il contenuto che sta sopra non si deformerebbe con lei, e la goccia di una
          // riga e' un pannello che si solleva, non un pomello che corre.
          reducedMotion = { true },
          shape = shape,
        )
      } else {
        Modifier
      },
    )
    // Il layer dell'elemento: la crescita (e, dove il vetro non c'e', l'ombra). Stesso numero della
    // goccia, dalla stessa molla.
    .graphicsLayer {
      val scale = state.liftScale(key)
      scaleX = scale
      scaleY = scale
      if (!glass) {
        shadowElevation = FluidReorderDefaults.FallbackShadow.toPx() * state.liftProgress(key)
        this.shape = liftShape
        clip = false
      }
    }
}

/**
 * Il gesto di un elemento: un tocco che resta fermo per il tempo del click lungo solleva, poi il dito
 * muove l'elemento fino al sollevamento. Prima del click lungo non consuma niente: se il dito si muove,
 * la lista scorre come se questo nodo non esistesse.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.reorderGesture(
  state: FluidReorderState,
  key: Any,
) {
  awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    if (!state.canLift(key)) return@awaitEachGesture

    val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
      while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
        if (!change.pressed || change.isConsumed) return@withTimeoutOrNull false
        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
          return@withTimeoutOrNull false
        }
      }
      @Suppress("UNREACHABLE_CODE")
      false
    } ?: true
    if (!held || !state.beginDrag(key)) return@awaitEachGesture

    try {
      while (true) {
        // Il passaggio `Initial`: il dito e' dell'elemento da qui in poi, e deve esserlo **prima** che lo
        // veda un discendente — una riga cliccabile leggerebbe il sollevamento come un click, e la lista
        // lo leggerebbe come l'inizio di uno scorrimento.
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        val delta = change.positionChangeIgnoreConsumed()
        change.consume()
        if (!change.pressed) break
        if (delta != Offset.Zero) state.dragBy(delta, change.uptimeMillis)
      }
    } finally {
      state.endDrag()
    }
  }
}
