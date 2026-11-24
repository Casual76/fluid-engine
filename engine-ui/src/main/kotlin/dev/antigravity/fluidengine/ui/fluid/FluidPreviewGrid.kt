package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Un gruppo della [FluidPreviewGrid]: un'intestazione ([title], per esempio il mese) e i suoi elementi. [title] null:
 * nessuna intestazione (una griglia sola, gli screenshot di un'app). [key] dev'essere stabile e unica.
 */
@Immutable
class FluidPreviewGridSection<T>(
  val key: Any,
  val title: String?,
  val items: List<T>,
)

/** I numeri della griglia di anteprime (F8.4, la scheda `griglia-media`). */
object FluidPreviewGridDefaults {
  /**
   * Le densita' di serie: 3, 5 e 9 colonne, quelle di Foto su iPhone in verticale (iOS 17–26: «Tutte le foto» parte a
   * 5, si pizzica a 3 e a 9; in iOS 18 si arriva anche a 13 e a «zoom piu' lontano», che qui non c'e'). Fiducia alta
   * sui tre numeri, presi dalla guida dell'iPhone e dalle schermate.
   */
  val Columns: List<Int> = listOf(3, 5, 9)

  /** La densita' di partenza, come indice in [Columns]: 5 colonne, come Foto. */
  const val InitialDensity: Int = 1

  /**
   * Lo spazio fra le celle: 2 dp. Foto lascia ~2 pt fra le miniature a ogni densita' (misurato a occhio sulle
   * schermate di iOS 26 della guida dell'iPhone, 2026-11-24, fiducia media).
   */
  val Spacing: Dp = 2.dp

  /**
   * Il pizzico cambia densita' a meta' strada (in scala geometrica) fra una densita' e l'altra: da 5 a 3 colonne,
   * quando le dita hanno ingrandito di √(5/3) ≈ 1,29. A occhio, 2026-11-24: in Foto il cambio avviene durante il
   * pizzico, non al rilascio, e la griglia continua a seguire le dita dopo il cambio.
   */
  const val SwitchAtFraction: Float = 0.5f

  /**
   * Il ritorno della scala dopo il pizzico: smorzamento 0,86, rigidezza 380 (a occhio, 2026-11-24: in Foto la griglia
   * si assesta in ~300 ms senza rimbalzo visibile).
   */
  val Settle: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 380f, visibilityThreshold = 0.001f)

  /**
   * Quanto il dito deve allargare o stringere prima che il pizzico sia della griglia e non uno scorrimento a due dita:
   * il 4% della distanza fra le dita. A occhio, 2026-11-24.
   */
  const val PinchSlop: Float = 0.04f
}

/**
 * Lo stato di una [FluidPreviewGrid]: dove scorre ([gridState]) e a che densita' sta ([density], un indice nelle
 * colonne della griglia). Salvabile: dopo una rotazione la griglia torna allo stesso punto e alla stessa densita'.
 */
@Stable
class FluidPreviewGridState(
  initialDensity: Int = FluidPreviewGridDefaults.InitialDensity,
  initialFirstVisibleItemIndex: Int = 0,
  initialFirstVisibleItemScrollOffset: Int = 0,
) {
  val gridState: LazyGridState = LazyGridState(initialFirstVisibleItemIndex, initialFirstVisibleItemScrollOffset)

  /** La densita' di adesso, come indice nelle colonne della griglia. La cambia il pizzico; l'app puo' scriverla. */
  var density: Int by mutableIntStateOf(initialDensity)

  /**
   * La scala che la griglia ha adesso sotto le dita (1 a riposo): legge il disegno, non la composizione. Per le prove e
   * per chi disegna qualcosa sopra la griglia che deve seguirla.
   */
  val pinchScale: Float get() = scale.value

  internal val scale = Animatable(1f)
  internal var focal by mutableStateOf(Offset.Zero)

  companion object {
    val Saver: Saver<FluidPreviewGridState, *> = listSaver(
      save = { listOf(it.density, it.gridState.firstVisibleItemIndex, it.gridState.firstVisibleItemScrollOffset) },
      restore = { FluidPreviewGridState(it[0], it[1], it[2]) },
    )
  }
}

/** Uno stato di [FluidPreviewGrid] che sopravvive alla rotazione. */
@Composable
fun rememberFluidPreviewGridState(initialDensity: Int = FluidPreviewGridDefaults.InitialDensity): FluidPreviewGridState =
  rememberSaveable(saver = FluidPreviewGridState.Saver) { FluidPreviewGridState(initialDensity) }

/** Una voce della griglia appiattita: un'intestazione o un elemento. */
private class PreviewEntry<T>(val section: FluidPreviewGridSection<T>, val item: T?, val indexInSection: Int)

/**
 * Una griglia di anteprime: foto e video, ma anche file, allegati, screenshot (la scheda `griglia-media` con la nota di
 * Alessio: «più in generale una griglia di anteprime, non solo di foto e video ma anche di file, configurabile
 * all'applicazione dell'app»). Il riferimento e' la libreria di Foto.
 *
 * - **Il pizzico fra le densita'** ([columns], di serie 3, 5 e 9). Due dita che si allargano ingrandiscono la griglia
 *   attorno al punto fra le dita; a meta' strada verso la densita' vicina la griglia **cambia colonne restando sul
 *   punto**: l'elemento sotto le dita resta sotto le dita, alla stessa altezza dentro la sua cella, e la griglia
 *   continua a seguire le dita. Al rilascio torna in scala 1 con una molla. Un tic a ogni cambio. TalkBack ha le azioni
 *   «Più colonne» e «Meno colonne».
 * - **La selezione trascinando** con [selection] ([fluidDragSelection]): in modo selezione un dito che va di lato
 *   sceglie in fila, la pressione lunga entra nella selezione; un tocco in modo selezione sceglie o toglie una cella.
 *   Le celle scelte portano il segno ([FluidSelectionMark]).
 * - **Le intestazioni restano in alto** finche' il loro gruppo e' sullo schermo, e la successiva le spinge via.
 * - **Lo scorrimento rapido** ([FluidFastScroller]) con l'etichetta ([fastScrollLabel], per esempio la data); senza
 *   etichetta propria dice il titolo del gruppo.
 *
 * **Cosa sceglie l'app.** Le colonne, la proporzione delle celle ([cellAspectRatio], 1 quadrata), le intestazioni
 * ([header], di serie il titolo), il contenuto della cella ([cell]): la griglia non sa se e' una foto, un video o un
 * PDF. La miniatura la da' l'app: per i file, `rememberFluidFileThumbnail` di `engine-viewer`, che la carica fuori dal
 * disegno e la tiene nella sua cache a memoria limitata.
 *
 * **Memoria e costo.** La griglia non tiene immagini sue: le celle fuori dallo schermo escono dalla composizione (una
 * `LazyVerticalGrid`, senza elementi precomposti oltre quelli di Compose), quindi una miniatura vive solo finche' la
 * sua cella e' vicina alla vista, e la cache e' quella delle miniature. Scorrendo, una cella ferma non ricompone: la
 * scelta la legge solo quando cambia, la scala del pizzico solo il disegno.
 *
 * @param sections i gruppi, nell'ordine (un gruppo solo con `title` null per una griglia semplice); la chiave di un
 *   gruppo entra, come testo, nella chiave della sua intestazione.
 * @param itemKey la chiave stabile di un elemento (un id, non l'indice): come ogni chiave di una lista pigra di
 *   Android dev'essere salvabile in un `Bundle` (un numero, una stringa).
 * @param state la densita' e lo scorrimento ([rememberFluidPreviewGridState]).
 * @param columns le densita' fra cui si pizzica, dalla piu' grande alla piu' fitta.
 * @param selection la selezione, se la griglia ne ha una.
 * @param inSelectionMode il modo selezione: di serie vero quando qualcosa e' scelto; un'app col tasto «Seleziona» lo
 *   tiene vero anche con zero elementi (in modo selezione il tocco sceglie, il dito di lato sceglie in fila).
 * @param onItemClick il tocco fuori dal modo selezione (apri nel visore).
 * @param fastScrollLabel l'etichetta dello scorrimento rapido per un elemento; null: il titolo del gruppo.
 * @param header l'intestazione di un gruppo, se diversa da quella di serie.
 * @param cell il contenuto di una cella: riempie la cella (`fillMaxSize`), che ritaglia e porta il segno.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T, K : Any> FluidPreviewGrid(
  sections: List<FluidPreviewGridSection<T>>,
  itemKey: (T) -> K,
  modifier: Modifier = Modifier,
  state: FluidPreviewGridState = rememberFluidPreviewGridState(),
  columns: List<Int> = FluidPreviewGridDefaults.Columns,
  cellAspectRatio: Float = 1f,
  spacing: Dp = FluidPreviewGridDefaults.Spacing,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  selection: FluidSelectionState<K>? = null,
  inSelectionMode: Boolean = selection?.isActive == true,
  onItemClick: ((T) -> Unit)? = null,
  fastScrollLabel: ((T) -> String?)? = null,
  header: (@Composable (FluidPreviewGridSection<T>) -> Unit)? = null,
  labels: FluidPreviewGridLabels = FluidPreviewGridLabels(),
  cell: @Composable BoxScope.(item: T, selected: Boolean) -> Unit,
) {
  require(columns.isNotEmpty()) { "FluidPreviewGrid: servono delle colonne" }
  val density = state.density.coerceIn(0, columns.size - 1)
  val count = columns[density]
  val entries = remember(sections) {
    buildList {
      for (section in sections) {
        if (section.title != null) add(PreviewEntry(section, null, -1))
        section.items.forEachIndexed { i, item -> add(PreviewEntry(section, item, i)) }
      }
    }
  }
  val currentEntries by rememberUpdatedState(entries)
  val currentItemKey by rememberUpdatedState(itemKey)
  val currentColumns by rememberUpdatedState(columns)
  val currentMode by rememberUpdatedState(inSelectionMode)
  val haptics = LocalFluidHaptics.current
  val currentHaptics by rememberUpdatedState(haptics)
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val currentReduced by rememberUpdatedState(reducedMotion)
  val scope = rememberCoroutineScope()
  val layoutDirection = LocalLayoutDirection.current
  val px = LocalDensity.current
  val spacingPx = with(px) { spacing.toPx() }
  val padStartPx = with(px) { contentPadding.calculateStartPadding(layoutDirection).toPx() }
  val padEndPx = with(px) { contentPadding.calculateEndPadding(layoutDirection).toPx() }
  var size by remember { mutableStateOf(IntSize.Zero) }
  val grid = state.gridState

  /** L'altezza di una cella a [n] colonne. */
  fun cellHeight(n: Int): Float {
    val w = (size.width - padStartPx - padEndPx - spacingPx * (n - 1)) / n
    return w / cellAspectRatio
  }

  /** Cambia densita' tenendo l'elemento sotto [focal] alla stessa altezza dentro la sua cella. */
  fun switchTo(next: Int, focal: Offset) {
    val from = state.density.coerceIn(0, currentColumns.size - 1)
    if (next == from || next !in currentColumns.indices) return
    val info = grid.layoutInfo
    val shift = -info.viewportStartOffset
    val hit = info.visibleItemsInfo.firstOrNull { item ->
      val top = item.offset.y + shift
      focal.y >= top && focal.y < top + item.size.height &&
        (currentEntries.getOrNull(item.index)?.item == null || (focal.x >= item.offset.x && focal.x < item.offset.x + item.size.width))
    } ?: info.visibleItemsInfo.firstOrNull()
    if (hit != null) {
      val top = hit.offset.y + shift
      val fraction = if (hit.size.height > 0) ((focal.y - top) / hit.size.height).coerceIn(0f, 1f) else 0f
      val isItem = currentEntries.getOrNull(hit.index)?.item != null
      val newHeight = if (isItem) cellHeight(currentColumns[next]) else hit.size.height.toFloat()
      // La riga dell'elemento in cima a dove deve stare: sopra il punto delle dita della stessa frazione della cella.
      val desiredTop = focal.y - fraction * newHeight - shift
      grid.requestScrollToItem(hit.index, -desiredTop.toInt())
    }
    state.density = next
    currentHaptics.play(FluidHapticEvent.Tick)
  }

  val pinch = Modifier.pointerInput(cellAspectRatio, spacingPx, padStartPx, padEndPx) {
    awaitEachGesture {
      awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
      var startSpan = 0f
      var zoom = 1f
      var pinching = false
      // La scala di partenza: quella che la griglia aveva (un pizzico che riprende mentre torna).
      var base = 1f
      while (true) {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size < 2) {
          if (pressed.isEmpty() || pinching) break
          continue
        }
        val a = pressed[0].position
        val b = pressed[1].position
        val span = (a - b).getDistance()
        val focal = (a + b) / 2f
        if (startSpan == 0f) {
          startSpan = span
          base = state.scale.value
          continue
        }
        if (startSpan <= 0f) continue
        zoom = span / startSpan
        if (!pinching && abs(zoom - 1f) > FluidPreviewGridDefaults.PinchSlop) pinching = true
        if (!pinching) continue
        event.changes.forEach(PointerInputChange::consume)
        state.focal = focal
        var visual = base * zoom
        val now = state.density.coerceIn(0, currentColumns.size - 1)
        val cols = currentColumns[now]
        // Ingrandire porta a meno colonne (indice piu' basso), rimpicciolire a piu' colonne.
        val fewer = now - 1
        val more = now + 1
        if (fewer >= 0) {
          val ratio = cols.toFloat() / currentColumns[fewer]
          if (visual >= ratio.pow(FluidPreviewGridDefaults.SwitchAtFraction)) {
            switchTo(fewer, focal)
            base /= ratio
            visual /= ratio
          }
        }
        if (more < currentColumns.size) {
          val ratio = cols.toFloat() / currentColumns[more]
          if (visual <= ratio.pow(FluidPreviewGridDefaults.SwitchAtFraction)) {
            switchTo(more, focal)
            base /= ratio
            visual /= ratio
          }
        }
        // Oltre la densita' estrema la griglia cede poco: un elastico, non uno zoom libero.
        val atMost = if (state.density <= 0) 1f + (visual - 1f).coerceAtLeast(0f) * 0.25f else visual
        val bounded = if (state.density >= currentColumns.size - 1 && atMost < 1f) 1f - (1f - atMost) * 0.25f else atMost
        scope.launch { state.scale.snapTo(if (currentReduced) 1f else bounded) }
      }
      if (pinching) {
        scope.launch {
          if (currentReduced) state.scale.snapTo(1f) else state.scale.animateTo(1f, FluidPreviewGridDefaults.Settle)
        }
      }
    }
  }

  val selectionModifier = if (selection != null) {
    Modifier.fluidDragSelection(
      selection = selection,
      gridState = grid,
      keyAt = { index -> currentEntries.getOrNull(index)?.item?.let { currentItemKey(it) } },
      inSelectionMode = inSelectionMode,
    )
  } else {
    Modifier
  }

  val showScroller by remember(grid) { derivedStateOf { grid.layoutInfo.totalItemsCount > 0 } }
  Box(
    modifier
      .onSizeChanged { size = it }
      .semantics {
        customActions = listOf(
          CustomAccessibilityAction(labels.fewerColumns) {
            val next = state.density - 1
            if (next >= 0) { state.density = next; true } else false
          },
          CustomAccessibilityAction(labels.moreColumns) {
            val next = state.density + 1
            if (next < currentColumns.size) { state.density = next; true } else false
          },
        )
      },
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(count),
      state = grid,
      contentPadding = contentPadding,
      horizontalArrangement = Arrangement.spacedBy(spacing),
      verticalArrangement = Arrangement.spacedBy(spacing),
      modifier = Modifier
        .fillMaxSize()
        .then(pinch)
        .then(selectionModifier)
        .graphicsLayer {
          val s = state.scale.value
          scaleX = s
          scaleY = s
          val f = state.focal
          transformOrigin = if (size.width > 0 && size.height > 0) {
            TransformOrigin(f.x / size.width, f.y / size.height)
          } else {
            TransformOrigin.Center
          }
        },
    ) {
      for (section in sections) {
        val title = section.title
        if (title != null) {
          stickyHeader(key = "fluid-preview-header:${section.key}", contentType = HeaderContentType) {
            if (header != null) header(section) else FluidPreviewGridHeader(title)
          }
        }
        items(
          count = section.items.size,
          key = { i -> currentItemKey(section.items[i]) },
          contentType = { CellContentType },
          span = { GridItemSpan(1) },
        ) { i ->
          val item = section.items[i]
          val key = currentItemKey(item)
          val selecting = selection != null && inSelectionMode
          val selectedState = remember(key, selection) { derivedStateOf { selection?.isSelected(key) == true } }
          val selected = selectedState.value
          val currentItem by rememberUpdatedState(item)
          Box(
            Modifier
              .fillMaxWidth()
              .aspectRatio(cellAspectRatio)
              .clipToBounds()
              .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                  if (selection != null && currentMode) selection.toggle(key) else onItemClick?.invoke(currentItem)
                },
              ),
          ) {
            cell(item, selected)
            if (selecting) {
              FluidSelectionMark(
                selected = selected,
                onPhoto = true,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
              )
            }
          }
        }
      }
    }
    if (showScroller) {
      FluidFastScroller(
        state = grid,
        label = { index ->
          val entry = currentEntries.getOrNull(index) ?: return@FluidFastScroller null
          entry.item?.let { fastScrollLabel?.invoke(it) } ?: entry.section.title
        },
        modifier = Modifier.align(Alignment.CenterEnd),
      )
    }
  }
}

/** Le parole della griglia per TalkBack: le due azioni che fanno cio' che fa il pizzico. */
@Immutable
data class FluidPreviewGridLabels(
  val fewerColumns: String = "Meno colonne",
  val moreColumns: String = "Più colonne",
) {
  companion object {
    fun english() = FluidPreviewGridLabels(fewerColumns = "Fewer columns", moreColumns = "More columns")
  }
}

/**
 * L'intestazione di serie di un gruppo: il titolo in grassetto sopra un velo che sfuma verso il basso, come i mesi di
 * Foto (il titolo sta sulle miniature, non su una fascia piena). Resta in alto finche' il gruppo e' sullo schermo. Il
 * velo non imita il vetro: e' l'ombra del bordo di scorrimento, perche' il titolo si legga su qualunque miniatura.
 */
@Composable
fun FluidPreviewGridHeader(title: String, modifier: Modifier = Modifier) {
  val veil = MaterialTheme.colorScheme.surface
  Box(
    modifier
      .fillMaxWidth()
      .drawBehind {
        drawRect(Brush.verticalGradient(0f to veil.copy(alpha = 0.92f), 0.6f to veil.copy(alpha = 0.7f), 1f to veil.copy(alpha = 0f)))
      }
      .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
  ) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

private object HeaderContentType
private object CellContentType

private fun Float.pow(e: Float): Float = Math.pow(this.toDouble(), e.toDouble()).toFloat()
