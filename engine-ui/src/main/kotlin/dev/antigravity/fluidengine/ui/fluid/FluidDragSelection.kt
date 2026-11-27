package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * I numeri della selezione trascinando (F8.4, la scheda `selezione-trascinando`). Lo scorrimento automatico e' quello
 * del riordino ([FluidReorderDefaults]): la stessa fascia ai bordi e la stessa velocita' che cresce col quadrato
 * della profondita', perche' una lista che scorre da sola sotto il dito deve farlo allo stesso modo qualunque cosa il
 * dito stia facendo.
 */
object FluidDragSelectionDefaults {
  /** La fascia ai bordi in cui la lista scorre da sola: 72 dp, quella del riordino. */
  val AutoScrollEdge: Dp = FluidReorderDefaults.AutoScrollEdge

  /** La velocita' piena dello scorrimento automatico, al bordo: 900 dp al secondo, quella del riordino. */
  val AutoScrollMaxSpeed: Dp = FluidReorderDefaults.AutoScrollMaxSpeed

  /**
   * Quanto deve essere piu' orizzontale che verticale il primo tratto del dito su una griglia, perche' sia una
   * selezione e non uno scorrimento: il doppio. A occhio, 2026-11-24, sul comportamento di Foto in «Seleziona»: un
   * dito che parte in diagonale scorre, uno che parte di lato seleziona.
   */
  const val HorizontalBias: Float = 2f

  /**
   * L'attesa della pressione lunga che entra nella selezione e continua trascinando: 400 ms, il `longPressTimeout`
   * di Android (`ViewConfiguration`). Apple non lo documenta per questo gesto.
   */
  const val LongPressMillis: Long = 400L
}

/**
 * Una selezione trascinando in corso: l'elemento da cui e' partita ([anchor]), cosa fa ([adding]) e fin dove arriva.
 *
 * **Il primo elemento decide**: se era scelto il gesto toglie, altrimenti aggiunge. **L'intervallo si allarga e si
 * stringe**: va dall'ancora all'elemento sotto il dito, nell'ordine della lista (in una griglia, per righe, come Foto);
 * tornando indietro gli elementi che escono dall'intervallo tornano **com'erano prima del gesto**, non spenti. Pura:
 * chi la usa le dice come leggere e scrivere la scelta.
 *
 * **La lista che cambia sotto il dito** (R2): i due capi dell'intervallo sono elementi, non posti. Se una riga entra o
 * esce sopra di loro (una lista che si aggiorna dal vivo), a ogni passo l'ancora e l'ultimo elemento raggiunto si
 * ritrovano per chiave vicino al posto di prima ([count] elementi in tutto), e l'intervallo continua da li'.
 */
internal class FluidDragSelectRange<K : Any>(
  anchor: Int,
  val adding: Boolean,
  private val keyAt: (Int) -> K?,
  private val isSelected: (K) -> Boolean,
  private val setSelected: (K, Boolean) -> Unit,
  private val count: () -> Int = { Int.MAX_VALUE },
) {
  /** Il posto dell'ancora adesso. */
  var anchor: Int = anchor
    private set

  /** L'elemento sotto il dito adesso. */
  var current: Int = anchor
    private set

  private var anchorKey: K? = keyAt(anchor)
  private var currentKey: K? = anchorKey
  private val original = HashMap<K, Boolean>()

  init {
    apply(anchor)
  }

  /**
   * Porta l'intervallo fino a [index]. Restituisce quanti elementi hanno cambiato stato (zero se il dito e' rimasto
   * sullo stesso elemento).
   */
  fun extendTo(index: Int): Int {
    if (index < 0) return 0
    // Un elemento che non c'e' piu' lascia il suo posto a chi ora sta li': non si cerca a ogni passo.
    anchor = relocate(anchorKey, anchor)
    anchorKey = keyAt(anchor)
    current = relocate(currentKey, current)
    currentKey = keyAt(current)
    if (index == current) return 0
    val oldLow = minOf(anchor, current)
    val oldHigh = maxOf(anchor, current)
    val newLow = minOf(anchor, index)
    val newHigh = maxOf(anchor, index)
    var changed = 0
    // Escono: tornano come prima del gesto.
    for (i in oldLow..oldHigh) {
      if (i in newLow..newHigh) continue
      val key = keyAt(i) ?: continue
      val before = original[key] ?: continue
      if (isSelected(key) != before) {
        setSelected(key, before)
        changed++
      }
    }
    // Entrano: prendono cio' che il gesto fa.
    for (i in newLow..newHigh) {
      if (i in oldLow..oldHigh) continue
      if (apply(i)) changed++
    }
    current = index
    currentKey = keyAt(index)
    return changed
  }

  /**
   * Il posto di [key] adesso: [at] se e' ancora li' (il caso di ogni passo), altrimenti il piu' vicino in cui c'e', entro
   * [RelocateWindow] posti. Se l'elemento non c'e' piu' resta [at].
   */
  private fun relocate(key: K?, at: Int): Int {
    if (key == null || keyAt(at) == key) return at
    val size = count()
    var d = 1
    while (d <= RelocateWindow && (at - d >= 0 || at + d < size)) {
      if (at - d >= 0 && keyAt(at - d) == key) return at - d
      if (at + d < size && keyAt(at + d) == key) return at + d
      d++
    }
    return at
  }

  private fun apply(index: Int): Boolean {
    val key = keyAt(index) ?: return false
    val now = isSelected(key)
    if (!original.containsKey(key)) original[key] = now
    if (now == adding) return false
    setSelected(key, adding)
    return true
  }

  private companion object {
    /** Quanto lontano si cerca un capo spostato: mille posti, piu' di quante righe arrivano fra due passi del dito. */
    const val RelocateWindow = 1_000
  }
}

/** Cio' che la selezione trascinando chiede alla lista: dov'e' ogni elemento, quanto e' grande, come scorre. */
private interface DragSelectLayout {
  val vertical: Boolean

  /** L'indice dell'elemento sotto [point] (coordinate del contenitore della lista), o -1 fra due elementi. */
  fun indexAt(point: Offset): Int

  val viewportMain: Float

  /** Quanti elementi ha la lista (per ritrovare i capi dell'intervallo se la lista cambia). */
  val itemCount: Int

  suspend fun scrollBy(px: Float): Float
}

private class GridDragSelectLayout(private val state: LazyGridState) : DragSelectLayout {
  override val vertical: Boolean get() = state.layoutInfo.orientation == Orientation.Vertical
  override val itemCount: Int get() = state.layoutInfo.totalItemsCount
  override val viewportMain: Float
    get() = state.layoutInfo.viewportSize.let { if (vertical) it.height else it.width }.toFloat()

  override fun indexAt(point: Offset): Int {
    val info = state.layoutInfo
    // Gli scarti degli elementi partono dall'inizio del contenuto, dopo il margine interno: nel contenitore sono
    // spostati di quel margine (`viewportStartOffset` e' il suo opposto).
    val shift = -info.viewportStartOffset.toFloat()
    val items = info.visibleItemsInfo
    for (i in items.indices) {
      val item = items[i]
      val left = item.offset.x.toFloat() + if (vertical) 0f else shift
      val top = item.offset.y.toFloat() + if (vertical) shift else 0f
      if (point.x >= left && point.x < left + item.size.width && point.y >= top && point.y < top + item.size.height) {
        return item.index
      }
    }
    return -1
  }

  override suspend fun scrollBy(px: Float): Float = state.scrollBy(px)
}

private class ListDragSelectLayout(private val state: LazyListState) : DragSelectLayout {
  override val vertical: Boolean get() = state.layoutInfo.orientation == Orientation.Vertical
  override val itemCount: Int get() = state.layoutInfo.totalItemsCount
  override val viewportMain: Float
    get() = state.layoutInfo.viewportSize.let { if (vertical) it.height else it.width }.toFloat()

  override fun indexAt(point: Offset): Int {
    val info = state.layoutInfo
    val shift = -info.viewportStartOffset.toFloat()
    val main = if (vertical) point.y else point.x
    val items = info.visibleItemsInfo
    for (i in items.indices) {
      val item = items[i]
      val start = item.offset + shift
      if (main >= start && main < start + item.size) return item.index
    }
    return -1
  }

  override suspend fun scrollBy(px: Float): Float = state.scrollBy(px)
}

/**
 * La selezione trascinando su una griglia (`fluidDragSelection` sulla `LazyVerticalGrid`, nel suo `modifier`): come
 * in Foto in «Seleziona», il dito che scorre di lato sulle miniature le sceglie tutte in fila.
 *
 * - **Quando parte.** In modo selezione ([inSelectionMode], di serie [FluidSelectionState.isActive]: un'app col tasto
 *   «Seleziona», come Foto, lo tiene vero anche con zero elementi scelti) un dito che scende su un elemento e va **di
 *   lato** (piu' orizzontale che verticale, [FluidDragSelectionDefaults.HorizontalBias]) seleziona; uno che va in su o
 *   in giu' scorre la griglia, come sempre. Con [startWithLongPress] anche una **pressione lunga** su un elemento
 *   entra nella selezione (anche fuori dal modo selezione) e continua trascinando, in ogni verso.
 * - **Cosa fa.** Il primo elemento decide se il gesto aggiunge o toglie; l'intervallo va dall'ancora all'elemento
 *   sotto il dito per righe, e si stringe tornando indietro (gli elementi che escono tornano come prima). Un tic per
 *   ogni passo che cambia la scelta.
 * - **Ai bordi** la griglia scorre da sola, con la fascia e la velocita' del riordino ([FluidDragSelectionDefaults]),
 *   e l'intervallo segue gli elementi che passano sotto il dito fermo.
 * - **Accanto a [fluidReorderable]**: il riordino parte dalla pressione lunga di un elemento sollevabile; se una
 *   griglia ha tutti e due, l'app da' [startWithLongPress] falso e la selezione parte di lato.
 *
 * Un gesto preso dalla selezione non arriva piu' alla griglia ne' agli elementi: niente scorrimento sotto il dito,
 * niente tocco al rilascio. Gli elementi che il gesto attraversa li dice [keyAt] (null: non selezionabile, per
 * esempio un'intestazione).
 */
@Composable
fun <K : Any> Modifier.fluidDragSelection(
  selection: FluidSelectionState<K>,
  gridState: LazyGridState,
  keyAt: (index: Int) -> K?,
  enabled: Boolean = true,
  startWithLongPress: Boolean = true,
  inSelectionMode: Boolean = selection.isActive,
): Modifier {
  val layout = remember(gridState) { GridDragSelectLayout(gridState) }
  return dragSelectionChain(selection, layout, keyAt, enabled, startWithLongPress, inSelectionMode, sideways = true, twoFingers = false)
}

/**
 * La selezione trascinando su una lista (`fluidDragSelection` sulla `LazyColumn`): come il trascinamento a **due dita**
 * di Mail, File e Note. Su una lista a una colonna un dito solo che va su e giu' deve scorrere, quindi la selezione
 * parte da due dita che scendono insieme su una riga e si muovono (anche fuori dal modo selezione: ci entra), o da una
 * **pressione lunga** ([startWithLongPress]). Per il resto e' quella della griglia: il primo elemento decide,
 * l'intervallo si allarga e si stringe, la lista scorre da sola ai bordi.
 */
@Composable
fun <K : Any> Modifier.fluidDragSelection(
  selection: FluidSelectionState<K>,
  listState: LazyListState,
  keyAt: (index: Int) -> K?,
  enabled: Boolean = true,
  startWithLongPress: Boolean = true,
  inSelectionMode: Boolean = selection.isActive,
): Modifier {
  val layout = remember(listState) { ListDragSelectLayout(listState) }
  return dragSelectionChain(selection, layout, keyAt, enabled, startWithLongPress, inSelectionMode, sideways = false, twoFingers = true)
}

@Composable
private fun <K : Any> Modifier.dragSelectionChain(
  selection: FluidSelectionState<K>,
  layout: DragSelectLayout,
  keyAt: (index: Int) -> K?,
  enabled: Boolean,
  startWithLongPress: Boolean,
  inSelectionMode: Boolean,
  sideways: Boolean,
  twoFingers: Boolean,
): Modifier {
  if (!enabled) return this
  val haptics = LocalFluidHaptics.current
  val density = LocalDensity.current
  val edgePx = with(density) { FluidDragSelectionDefaults.AutoScrollEdge.toPx() }
  val speedPx = with(density) { FluidDragSelectionDefaults.AutoScrollMaxSpeed.toPx() }
  val currentKeyAt by rememberUpdatedState(keyAt)
  val currentHaptics by rememberUpdatedState(haptics)
  val currentSelection by rememberUpdatedState(selection)
  val currentLongPress by rememberUpdatedState(startWithLongPress)
  val currentMode by rememberUpdatedState(inSelectionMode)
  // Lo scorrimento automatico chiede i fotogrammi: gira nello scope della composizione, che ha l'orologio.
  val scope = rememberCoroutineScope()
  return this.pointerInput(layout, sideways, twoFingers, edgePx, speedPx) {
    run {
      awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startIndex = layout.indexAt(down.position)
        if (startIndex < 0 || currentKeyAt(startIndex) == null) return@awaitEachGesture
        val slop = viewConfiguration.touchSlop
        val probe = StartProbe(down.position, down.id)
        val decide: suspend AwaitPointerEventScope.() -> Boolean = {
          decideStart(probe, layout, slop, sideways, twoFingers) { currentMode }
        }
        // Null: la pressione lunga, il dito rimasto fermo per l'attesa.
        val decided: Boolean? = if (currentLongPress) {
          withTimeoutOrNull(FluidDragSelectionDefaults.LongPressMillis) { decide() }
        } else {
          decide()
        }
        val pointer = probe.pointer
        val takenAt = probe.takenAt
        val start = when (decided) {
          true -> takenAt ?: down.position
          false -> return@awaitEachGesture
          null -> down.position
        }
        val anchor = layout.indexAt(start).takeIf { it >= 0 } ?: startIndex
        val anchorKey = currentKeyAt(anchor) ?: return@awaitEachGesture
        val sel = currentSelection
        val range = FluidDragSelectRange(
          anchor = anchor,
          adding = !sel.isSelected(anchorKey),
          keyAt = { currentKeyAt(it) },
          isSelected = { sel.isSelected(it) },
          setSelected = { key, on -> sel.setSelected(key, on) },
          count = { layout.itemCount },
        )
        currentHaptics.play(if (decided == null) FluidHapticEvent.GestureStart else FluidHapticEvent.Tick)
        var at = start
        var autoScroll: Job? = null
        fun evaluate() {
          val index = layout.indexAt(at)
          if (index >= 0 && range.extendTo(index) > 0) currentHaptics.play(FluidHapticEvent.Tick)
        }
        fun edgeDelta(seconds: Float): Float {
          val main = if (layout.vertical) at.y else at.x
          return FluidReorder.autoScrollDelta(
            visualStart = main,
            visualEnd = main,
            viewportStart = 0f,
            viewportEnd = layout.viewportMain,
            zone = edgePx,
            maxSpeedPx = speedPx,
            seconds = seconds,
          )
        }
        fun ensureAutoScroll() {
          if (autoScroll?.isActive == true || edgeDelta(1f) == 0f) return
          autoScroll = scope.launch {
            var last = withFrameMillis { it }
            while (isActive) {
              val now = withFrameMillis { it }
              val delta = edgeDelta((now - last) / 1000f)
              last = now
              if (delta == 0f && edgeDelta(1f) == 0f) break
              if (delta != 0f) {
                if (layout.scrollBy(delta) == 0f) break
                evaluate()
              }
            }
          }
        }
        evaluate()
        ensureAutoScroll()
        // Da qui il gesto e' della selezione: i cambi si consumano prima di tutti (Initial), cosi' la lista non
        // scorre sotto il dito e l'elemento sotto non riceve il tocco al rilascio.
        trackSelection(pointer, twoFingers) { position ->
          at = position
          evaluate()
          ensureAutoScroll()
        }
        autoScroll?.cancel()
        currentHaptics.play(FluidHapticEvent.GestureEnd)
      }
    }
  }
}

/** Dove e' sceso il dito, e da dove la selezione prende il gesto se lo prende. */
private class StartProbe(val down: Offset, var pointer: androidx.compose.ui.input.pointer.PointerId) {
  var takenAt: Offset? = null
}

/**
 * Aspetta che il gesto si decida: vero se e' della selezione (un dito che va di lato su una griglia in modo selezione,
 * due dita che si muovono insieme su una lista), falso se e' della lista o dell'elemento (scorre, si alza, lo consuma
 * qualcun altro).
 */
private suspend fun AwaitPointerEventScope.decideStart(
  probe: StartProbe,
  layout: DragSelectLayout,
  slop: Float,
  sideways: Boolean,
  twoFingers: Boolean,
  selecting: () -> Boolean,
): Boolean {
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Main)
    val pressed = event.changes.filter { it.pressed }
    if (pressed.isEmpty()) return false
    if (twoFingers && pressed.size >= 2) {
      val a = pressed[0]
      val b = pressed[1]
      val moved = (a.position - a.previousPosition) + (b.position - b.previousPosition)
      if (moved != Offset.Zero) {
        val centroid = (a.position + b.position) / 2f
        if (layout.indexAt(centroid) >= 0) {
          probe.pointer = a.id
          probe.takenAt = centroid
          return true
        }
      }
      continue
    }
    val change = event.changes.firstOrNull { it.id == probe.pointer } ?: return false
    // Un movimento consumato e' della lista che scorre; il contatto consumato da un elemento che si puo' toccare no.
    if (change.isConsumed && change.position != change.previousPosition) return false
    val travel = change.position - probe.down
    if (abs(travel.x) > slop || abs(travel.y) > slop) {
      val horizontal = abs(travel.x) > abs(travel.y) * FluidDragSelectionDefaults.HorizontalBias
      if (sideways && horizontal && selecting()) {
        probe.takenAt = probe.down
        return true
      }
      return false
    }
  }
}

/** Segue il dito (o il punto medio di due dita) finche' non si alza, consumando tutto. */
private suspend fun AwaitPointerEventScope.trackSelection(
  pointerId: androidx.compose.ui.input.pointer.PointerId,
  twoFingers: Boolean,
  onMove: (Offset) -> Unit,
) {
  var pointer = pointerId
  while (true) {
    val event = awaitPointerEvent(PointerEventPass.Initial)
    val pressed = event.changes.filter { it.pressed }
    event.changes.forEach(PointerInputChange::consume)
    if (pressed.isEmpty()) return
    val position = if (twoFingers && pressed.size >= 2) {
      (pressed[0].position + pressed[1].position) / 2f
    } else {
      val change = pressed.firstOrNull { it.id == pointer } ?: pressed.first().also { pointer = it.id }
      change.position
    }
    onMove(position)
  }
}
