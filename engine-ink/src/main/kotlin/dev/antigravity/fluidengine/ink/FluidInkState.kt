package dev.antigravity.fluidengine.ink

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/** Lo stile di uno strumento che disegna: il colore (con l'opacita') e quale dei cinque spessori. */
@Immutable
data class FluidInkToolStyle(val color: Color, val widthIndex: Int)

/**
 * Il righello: dove sta il suo centro (nelle coordinate del foglio) e com'e' girato (radianti, 0 = orizzontale). Si
 * sposta con un dito e gira con due, come in PencilKit; un tratto che comincia vicino a un suo bordo corre dritto.
 */
@Immutable
data class FluidInkRuler(val center: Offset, val angle: Float)

/**
 * Lo stato di un disegno: i tratti, la cronologia di annulla e ripristina, la selezione del lazo, lo strumento con i
 * suoi stili, il righello, chi disegna e la vista (zoom e spostamento). Una per foglio; si crea con
 * [rememberFluidInkState], o fuori dalla composizione (in un ViewModel) col costruttore.
 *
 * I tratti sono una lista immutabile ([strokes]): ogni modifica ne fa una nuova e mette la vecchia nella cronologia. Una
 * modifica fatta da un gesto (la gomma che passa, il lazo che sposta) e' **un passo solo**, dall'inizio alla fine del
 * gesto, come in PencilKit.
 *
 * Non si salva da sola nel `Bundle` (un disegno grande supera il limite di Android): per tenerlo, [encode] e
 * [FluidInkCodec.decode].
 */
@Stable
class FluidInkState(
  initial: List<FluidInkStroke> = emptyList(),
  tool: FluidInkTool = FluidInkTool.Pen,
  drawingPolicy: FluidInkDrawingPolicy = FluidInkDrawingPolicy.Auto,
) {
  /** I tratti, dal primo (sotto) all'ultimo (sopra). */
  var strokes: List<FluidInkStroke> by mutableStateOf(initial)
    private set

  private var undoStack = ArrayDeque<List<FluidInkStroke>>()
  private var redoStack = ArrayDeque<List<FluidInkStroke>>()
  private var undoCount by mutableIntStateOf(0)
  private var redoCount by mutableIntStateOf(0)
  private var editBase: List<FluidInkStroke>? = null
  private var nextStrokeId: Long = (initial.maxOfOrNull { it.id } ?: 0L) + 1L

  /** Cresce a ogni modifica del disegno (anche annulla e ripristina): per sapere se c'e' qualcosa da salvare. */
  var revision: Int by mutableIntStateOf(0)
    private set

  val canUndo: Boolean get() = undoCount > 0 && editBase == null
  val canRedo: Boolean get() = redoCount > 0 && editBase == null
  val isEmpty: Boolean get() = strokes.isEmpty()

  /** Lo strumento scelto. Cambiarlo toglie la selezione del lazo (come in Markup). */
  var tool: FluidInkTool by mutableStateOf(tool)
    private set

  fun selectTool(tool: FluidInkTool) {
    if (tool != FluidInkTool.Lasso) clearSelection()
    this.tool = tool
  }

  private val styles = mutableStateMapOf<FluidInkTool, FluidInkToolStyle>()

  /** Lo stile di [tool] (il colore e lo spessore che la tavolozza gli ha dato). */
  fun styleOf(tool: FluidInkTool): FluidInkToolStyle =
    styles[tool] ?: FluidInkToolStyle(FluidInkDefaults.defaultColor(tool), FluidInkDefaults.defaultWidthIndex(tool))

  fun setStyle(tool: FluidInkTool, style: FluidInkToolStyle) {
    styles[tool] = style.copy(widthIndex = style.widthIndex.coerceIn(0, FluidInkDefaults.widths(tool).lastIndex))
  }

  /** Il colore dello strumento scelto (o della penna, se lo strumento non disegna). */
  val color: Color get() = styleOf(if (tool.isInking) tool else FluidInkTool.Pen).color

  /** Lo spessore sullo schermo dello strumento scelto. */
  val width: Dp get() = FluidInkDefaults.widths(tool).let { it[styleOf(tool).widthIndex.coerceIn(0, it.lastIndex)] }

  /** La gomma: a pixel (di serie, come Markup di iOS 26) o a oggetti. */
  var eraserMode: FluidInkEraserMode by mutableStateOf(FluidInkEraserMode.Area)

  /** Chi disegna: [FluidInkDrawingPolicy]. */
  var drawingPolicy: FluidInkDrawingPolicy by mutableStateOf(drawingPolicy)

  /** Vero dalla prima volta che la penna ha toccato il foglio: con [FluidInkDrawingPolicy.Auto] da li' il dito scorre. */
  var stylusSeen: Boolean by mutableStateOf(false)
    internal set

  /** Vero se adesso il dito disegna (e non scorre): legge [drawingPolicy] e [stylusSeen]. */
  val fingerDraws: Boolean
    get() = when (drawingPolicy) {
      FluidInkDrawingPolicy.AnyInput -> true
      FluidInkDrawingPolicy.StylusOnly -> false
      else -> !stylusSeen
    }

  /** Le forme riconosciute tenendo fermo il tratto (di serie accese, come in Note). */
  var shapeRecognition: Boolean by mutableStateOf(true)

  /** Il righello acceso. */
  var rulerVisible: Boolean by mutableStateOf(false)

  /** Dove sta il righello (null: al centro della vista, la prima volta che si accende). */
  var ruler: FluidInkRuler? by mutableStateOf(null)

  /** Vero mentre un dito o la penna lavora sul foglio (disegna, cancella, gira il lazo, sposta): la tavolozza si fa piccola e il vetro sta fermo. */
  var isInteracting: Boolean by mutableStateOf(false)
    internal set

  // --- La vista ------------------------------------------------------------------------------------------------------

  /** Lo zoom sopra il foglio intero (1 = tutto il foglio nella vista, fino a [FluidInkDefaults.MaxZoom]). */
  var zoom: Float by mutableFloatStateOf(1f)
    internal set

  /**
   * Dove sta l'angolo in alto a sinistra del foglio nella vista, in pixel (Unspecified: dove lo mette la vista, centrato o
   * in cima). La vista lo tiene sempre dentro i suoi limiti.
   */
  var pan: Offset by mutableStateOf(Offset.Unspecified)
    internal set

  /** Rimette il foglio intero nella vista. */
  fun resetView() {
    zoom = 1f
    pan = Offset.Unspecified
  }

  // --- La tavolozza ---------------------------------------------------------------------------------------------------

  /** La tavolozza ridotta al tondo dello strumento (si riapre toccandola). */
  var paletteMinimized: Boolean by mutableStateOf(false)

  /** La tavolozza si riduce da sola quando si comincia a disegnare («Riduci automaticamente» di `PKToolPicker`). */
  var autoMinimize: Boolean by mutableStateOf(false)

  /** La tavolozza in alto invece che in basso (la si trascina, o lo si sceglie dal menu). */
  var paletteAtTop: Boolean by mutableStateOf(false)

  /** I tratti appena finiti che il tratto a bassa latenza mostra ancora: il foglio non li disegna due volte. */
  internal val wetHeld = mutableStateSetOf<Long>()

  /**
   * Le zone (nelle coordinate della radice) dove il tratto a bassa latenza non si vede: la tavolozza, la barra. Il
   * tratto nativo si disegna sopra a tutto, e senza queste zone passerebbe sopra il vetro e poi, finito, sotto.
   */
  internal val inkMasks = mutableStateMapOf<Any, Rect>()

  // --- Selezione ------------------------------------------------------------------------------------------------------

  /** Le identita' dei tratti scelti col lazo. */
  var selection: Set<Long> by mutableStateOf(emptySet())
    private set

  val hasSelection: Boolean get() = selection.isNotEmpty()

  /** Il riquadro dei tratti scelti (null senza selezione). */
  val selectionBounds: Rect?
    get() {
      if (selection.isEmpty()) return null
      val chosen = strokes.filter { it.id in selection }
      return if (chosen.isEmpty()) null else InkGeometry.union(chosen)
    }

  fun clearSelection() {
    if (selection.isNotEmpty()) selection = emptySet()
  }

  /** Sceglie i tratti dentro il lazo [polygon] (coppie x, y nelle coordinate del foglio). Vero se ne ha preso qualcuno. */
  fun selectInLasso(polygon: FloatArray): Boolean {
    val count = polygon.size / 2
    if (count < 3) {
      clearSelection()
      return false
    }
    val bounds = InkGeometry.polygonBounds(polygon, count)
    selection = strokes.filter { InkGeometry.lassoTakes(it, polygon, count, bounds) }.mapTo(HashSet()) { it.id }
    return selection.isNotEmpty()
  }

  /** Sceglie i tratti con queste identita' (per un'app che li sceglie da se'). */
  fun select(ids: Set<Long>) {
    selection = ids.filterTo(HashSet()) { id -> strokes.any { it.id == id } }
  }

  /** Toglie i tratti scelti. */
  fun deleteSelection() {
    if (selection.isEmpty()) return
    val gone = selection
    commit(strokes.filterNot { it.id in gone })
    selection = emptySet()
  }

  /** Duplica i tratti scelti un po' piu' in la' (16 unita'), e sceglie le copie, come «Duplica» di Markup. */
  fun duplicateSelection(offset: Offset = Offset(16f, 16f)) {
    if (selection.isEmpty()) return
    val copies = strokes.filter { it.id in selection }.map {
      it.copy(id = newStrokeId(), points = it.points.translated(offset.x, offset.y), input = FluidInkInput.Synthetic)
    }
    commit(strokes + copies)
    selection = copies.mapTo(HashSet()) { it.id }
  }

  /** Ricolora i tratti scelti con [color] (l'evidenziatore tiene la sua trasparenza). */
  fun recolorSelection(color: Color) {
    if (selection.isEmpty()) return
    commit(strokes.map { s ->
      if (s.id !in selection) s else s.copy(color = if (s.tool == FluidInkTool.Highlighter) color.copy(alpha = s.color.alpha) else color)
    })
  }

  /** Sposta i tratti scelti di ([dx], [dy]). Dentro un gesto ([beginEdit]) e' una parte dello stesso passo. */
  fun moveSelection(dx: Float, dy: Float) {
    if (selection.isEmpty() || (dx == 0f && dy == 0f)) return
    commit(strokes.map { s -> if (s.id in selection) s.copy(points = s.points.translated(dx, dy)) else s })
  }

  // --- Modifiche ------------------------------------------------------------------------------------------------------

  /** Una identita' nuova per un tratto. */
  fun newStrokeId(): Long = nextStrokeId++

  /** Aggiunge un tratto (in cima). */
  fun add(stroke: FluidInkStroke) {
    nextStrokeId = max(nextStrokeId, stroke.id + 1)
    commit(strokes + stroke)
  }

  /** Sostituisce il tratto con l'identita' di [stroke] (una forma che prende il posto del tratto a mano). */
  fun replace(stroke: FluidInkStroke) {
    commit(strokes.map { if (it.id == stroke.id) stroke else it })
  }

  /** Toglie tutto, in un passo che si annulla. */
  fun clear() {
    if (strokes.isEmpty()) return
    selection = emptySet()
    commit(emptyList())
  }

  /** Sostituisce il disegno con [strokes] e svuota la cronologia (un disegno riaperto). */
  fun load(strokes: List<FluidInkStroke>) {
    editBase = null
    undoStack = ArrayDeque()
    redoStack = ArrayDeque()
    undoCount = 0
    redoCount = 0
    selection = emptySet()
    nextStrokeId = max(nextStrokeId, (strokes.maxOfOrNull { it.id } ?: 0L) + 1L)
    this.strokes = strokes
    revision++
  }

  /** I tratti salvati ([FluidInkCodec]). */
  fun encode(): ByteArray = FluidInkCodec.encode(strokes)

  fun undo() {
    if (!canUndo) return
    redoStack.addLast(strokes)
    strokes = undoStack.removeLast()
    syncCounts()
    selection = selection.filterTo(HashSet()) { id -> strokes.any { it.id == id } }
    revision++
  }

  fun redo() {
    if (!canRedo) return
    undoStack.addLast(strokes)
    strokes = redoStack.removeLast()
    syncCounts()
    selection = selection.filterTo(HashSet()) { id -> strokes.any { it.id == id } }
    revision++
  }

  /**
   * Comincia un gesto che cambia il disegno a ogni fotogramma (la gomma, lo spostamento del lazo): fino a [endEdit] le
   * modifiche non entrano nella cronologia una per una, e alla fine sono un passo solo.
   */
  fun beginEdit() {
    if (editBase == null) editBase = strokes
  }

  /** Chiude il gesto: se ha cambiato qualcosa, e' un passo della cronologia. */
  fun endEdit() {
    val base = editBase ?: return
    editBase = null
    if (base !== strokes) push(base)
    syncCounts()
  }

  /** Annulla il gesto in corso: il disegno torna com'era al [beginEdit]. */
  fun cancelEdit() {
    val base = editBase ?: return
    editBase = null
    if (base !== strokes) {
      strokes = base
      revision++
    }
    syncCounts()
  }

  /** La gomma a oggetti lungo il segmento a–b (raggio [radius], nelle unita' del foglio). Vero se ha tolto qualcosa. */
  fun eraseObjects(a: Offset, b: Offset, radius: Float): Boolean {
    val kept = strokes.filterNot { InkGeometry.capsuleHitsStroke(a.x, a.y, b.x, b.y, radius, it) }
    if (kept.size == strokes.size) return false
    val gone = strokes.size - kept.size
    commit(kept)
    return gone > 0
  }

  /** La gomma a pixel lungo il segmento a–b: taglia i tratti e toglie solo la parte toccata. Vero se ha cambiato qualcosa. */
  fun eraseArea(a: Offset, b: Offset, radius: Float): Boolean {
    var changed = false
    val out = ArrayList<FluidInkStroke>(strokes.size + 4)
    for (s in strokes) {
      val pieces = InkGeometry.eraseArea(s, a.x, a.y, b.x, b.y, radius, ::newStrokeId)
      if (pieces == null) out += s else {
        out += pieces
        changed = true
      }
    }
    if (changed) commit(out)
    return changed
  }

  private fun commit(next: List<FluidInkStroke>) {
    if (editBase == null) push(strokes)
    strokes = next
    revision++
    syncCounts()
  }

  private fun push(previous: List<FluidInkStroke>) {
    undoStack.addLast(previous)
    while (undoStack.size > FluidInkDefaults.HistoryLimit) undoStack.removeFirst()
    redoStack.clear()
  }

  private fun syncCounts() {
    undoCount = undoStack.size
    redoCount = redoStack.size
  }
}

/** Lo stato di un disegno, che sopravvive alla ricomposizione (non alla ricreazione: vedi [FluidInkState]). */
@Composable
fun rememberFluidInkState(
  initial: List<FluidInkStroke> = emptyList(),
  tool: FluidInkTool = FluidInkTool.Pen,
  drawingPolicy: FluidInkDrawingPolicy = FluidInkDrawingPolicy.Auto,
): FluidInkState = remember { FluidInkState(initial, tool, drawingPolicy) }
