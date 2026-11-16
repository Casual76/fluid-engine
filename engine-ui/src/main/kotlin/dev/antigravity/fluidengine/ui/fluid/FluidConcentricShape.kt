package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.AbsoluteCutCornerShape
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.modifier.ModifierLocalMap
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.modifier.modifierLocalMapOf
import androidx.compose.ui.modifier.modifierLocalOf
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import kotlin.math.max
import kotlin.math.min

/**
 * Una forma i cui angoli seguono quelli del contenitore: raggio = raggio del contenitore − distanza dal suo bordo, mai
 * sotto [minimum] (4.0, piano «iOS in Android», F3.5; in SwiftUI `ConcentricRectangle` e `.concentric(minimum:)`).
 *
 * **Perché esiste.** Un tasto dentro una capsula, una barra dentro un foglio, un foglio dentro lo schermo: se l'angolo
 * interno ha lo stesso raggio di quello esterno la fascia fra i due si allarga sull'angolo e l'occhio legge due curve
 * che non si parlano. Apple dà a tutto ciò che sta vicino a un bordo curvo un angolo *concentrico*, e scriverlo a mano
 * vuol dire rifare la sottrazione a ogni taglia, a ogni margine, a ogni densità. Qui la fa l'engine.
 *
 * **Da sola vale il minimo.** Una `Shape` di Compose conosce solo la propria taglia, non dove sta: senza un contenitore
 * dichiarato ([fluidContainerShape]) e senza un modificatore dell'engine che la risolva, questa forma è una
 * [ContinuousCornerShape] di raggio [minimum] col suo [smoothing], e così la vede tutto l'engine (lente, riflessi, campo
 * dello shader). Per questo è una sua sottoclasse: chi non sa risolverla la disegna comunque giusta al minimo.
 *
 * **Dentro un contenitore** la risolvono al layout i modificatori dell'engine che ricevono la forma, in quattro raggi
 * ([FluidCornerRadii]) e poi in una [ContinuousCornerShape] con lo **stesso** [smoothing]: per il piano del vetro (3.7)
 * una forma concentrica risolta è una forma ad angoli tondi come le altre. Chi disegna da sé usa
 * [rememberFluidConcentricRadii].
 *
 * **Ogni angolo per conto suo.** Un pezzo vicino a un solo angolo del contenitore è concentrico lì e al minimo
 * altrove; una barra posata in fondo a un foglio è concentrica nei due angoli bassi. La distanza di un angolo è la
 * **maggiore** delle due (dal bordo verticale e da quello orizzontale): con margini diversi non esiste un cerchio con
 * lo stesso centro, e il maggiore è l'unico che tiene l'angolo interno dentro quello esterno (con il minore, un pezzo a
 * 2 dp di lato e 20 dall'alto sporgerebbe dalla curva del contenitore).
 *
 * @param minimum il raggio sotto il quale l'angolo non scende, in SwiftUI `minimum`. Mai oltre mezzo lato corto.
 * @param smoothing il raccordo continuo, come in [ContinuousCornerShape]: la forma risolta lo porta uguale.
 */
@Stable
class FluidConcentricShape(
  val minimum: Dp = 0.dp,
  smoothing: Float = ContinuousCornerShape.IosSmoothing,
) : ContinuousCornerShape(
  CornerSize(minimum.coerceAtLeast(0.dp)),
  CornerSize(minimum.coerceAtLeast(0.dp)),
  CornerSize(minimum.coerceAtLeast(0.dp)),
  CornerSize(minimum.coerceAtLeast(0.dp)),
  smoothing,
) {
  override fun toString(): String = "FluidConcentricShape(minimum=$minimum, smoothing=$smoothing)"
}

/**
 * Questo nodo è un contenitore di forma [shape]: le [FluidConcentricShape] dei discendenti (e di ciò che segue nella
 * stessa catena) si misurano sul riquadro di questo punto della catena, con gli angoli di [shape].
 *
 * In SwiftUI `containerShape(_:)`. Il riquadro è quello che vedono i modificatori **a destra**: in
 * `Modifier.fluidContainerShape(s).padding(8.dp)` il contenuto sta a 8 dp dal bordo, e un suo angolo concentrico
 * prende il raggio di `s` meno 8. La forma non disegna nulla e non ritaglia: dichiara soltanto.
 *
 * [shape] può essere a sua volta una [FluidConcentricShape]: un contenitore concentrico al proprio contenitore
 * (un gruppo dentro una barra dentro un foglio) si risolve prima lui, poi i suoi pezzi. Una forma che non è ad angoli
 * (un `Outline` generico) vale angoli vivi: dentro, i pezzi concentrici stanno al loro minimo.
 */
fun Modifier.fluidContainerShape(shape: Shape): Modifier = this then FluidContainerShapeElement(shape)

/**
 * I raggi concentrici di un nodo, per chi disegna da sé (in SwiftUI 27 `GeometryProxy.concentricCornerRadii`).
 *
 * Si attacca al nodo con [fluidConcentricRadii]: è lì che si sa dove sta il pezzo rispetto al contenitore. [radii] è
 * stato di Compose, aggiornato al layout: va letto **nel disegno** (`drawBehind`, `drawWithCache`, un `Canvas`), dove
 * è già quello giusto dal primo fotogramma; letto in composizione, il primo fotogramma vede il minimo.
 */
@Stable
class FluidConcentricRadiiState internal constructor(
  /** Il raggio sotto il quale gli angoli non scendono. */
  val minimum: Dp,
  initial: FluidCornerRadii,
) {
  /**
   * I quattro raggi in pixel, angoli fisici (sinistra e destra, non inizio e fine). Il [minimum] finché il nodo non è
   * posato o se nessun contenitore è dichiarato sopra di lui.
   */
  var radii: FluidCornerRadii by mutableStateOf(initial)
    internal set
}

/**
 * Lo stato dei raggi concentrici per un nodo che disegna da sé; lo si lega al nodo con [fluidConcentricRadii].
 *
 * Il piano chiedeva `rememberFluidConcentricRadii(minimum): FluidCornerRadii`. Un composable però non sa dove verrà
 * posato il suo nodo, e i raggi concentrici dipendono proprio da quello: in SwiftUI li dà il `GeometryProxy` di un
 * `GeometryReader`, che è una vista. L'equivalente di Compose è uno stato più un modificatore che lo riempie al layout.
 *
 * ```
 * val concentric = rememberFluidConcentricRadii(minimum = 8.dp)
 * Box(Modifier.fluidConcentricRadii(concentric).drawBehind { val r = concentric.radii; ... })
 * ```
 */
@Composable
fun rememberFluidConcentricRadii(minimum: Dp = 0.dp): FluidConcentricRadiiState {
  val density = LocalDensity.current
  return remember(minimum, density) {
    FluidConcentricRadiiState(minimum, FluidCornerRadii.all(with(density) { minimum.coerceAtLeast(0.dp).toPx() }))
  }
}

/** Lega [state] a questo nodo: al layout ci scrive i raggi concentrici del riquadro di questo punto della catena. */
fun Modifier.fluidConcentricRadii(state: FluidConcentricRadiiState): Modifier =
  this then FluidConcentricRadiiElement(state)

// --- Risoluzione, logica pura -------------------------------------------------------------------------------------

/**
 * I raggi concentrici di un pezzo posato in [piece] (coordinate locali del contenitore) dentro un contenitore di taglia
 * [containerSize] con angoli [container]: la regola della classe, angolo per angolo, in pixel.
 *
 * Ogni raggio sta fra il minimo e mezzo lato corto del pezzo; il minimo stesso non supera mezzo lato corto (un pezzo
 * da 20 px con minimo 30 è una capsula, non un errore). Il raggio del contenitore conta fino a mezzo **suo** lato
 * corto, come lo disegna. Le distanze negative (un pezzo che sporge) contano zero: un angolo interno non diventa mai
 * più tondo di quello che lo contiene.
 */
internal fun concentricCornerRadii(
  container: FluidCornerRadii,
  containerSize: Size,
  piece: Rect,
  minimumPx: Float,
): FluidCornerRadii {
  val half = (min(piece.width, piece.height) / 2f).finiteOrZero().coerceAtLeast(0f)
  val floor = minimumPx.finiteOrZero().coerceIn(0f, half)
  val containerHalf = (containerSize.minDimension / 2f).finiteOrZero().coerceAtLeast(0f)

  fun corner(containerRadius: Float, fromSide: Float, fromTopOrBottom: Float): Float {
    val distance = max(fromSide.finiteOrZero(), fromTopOrBottom.finiteOrZero()).coerceAtLeast(0f)
    val radius = containerRadius.finiteOrZero().coerceIn(0f, containerHalf) - distance
    return radius.coerceAtLeast(floor).coerceAtMost(half)
  }

  val right = containerSize.width - piece.right
  val bottom = containerSize.height - piece.bottom
  return FluidCornerRadii(
    topLeft = corner(container.topLeft, piece.left, piece.top),
    topRight = corner(container.topRight, right, piece.top),
    bottomRight = corner(container.bottomRight, right, bottom),
    bottomLeft = corner(container.bottomLeft, piece.left, bottom),
  )
}

/** I raggi di una [FluidConcentricShape] senza contenitore: il minimo, fino a mezzo lato corto di [size]. */
internal fun concentricRadiiAlone(size: Size, minimumPx: Float): FluidCornerRadii {
  val half = (size.minDimension / 2f).finiteOrZero().coerceAtLeast(0f)
  return FluidCornerRadii.all(minimumPx.finiteOrZero().coerceIn(0f, half))
}

/**
 * I quattro raggi, in pixel e angoli fisici, con cui [shape] disegna un riquadro di taglia [size]: è il raggio del
 * contenitore nella regola concentrica.
 *
 * `RectangleShape` → zero. Una `CornerBasedShape` (le forme dell'engine, capsula compresa, e quelle di Compose) → i
 * suoi angoli, specchiati in RTL salvo le forme «assolute», fino a mezzo lato corto. Ogni altra forma → il suo
 * `Outline`: un rettangolo arrotondato dà i suoi raggi, un path generico angoli vivi (non c'è un raggio da sottrarre).
 * Una [FluidConcentricShape] qui vale il suo minimo: risolverla contro il *suo* contenitore è compito di chi conosce
 * l'albero ([resolveConcentricRadii]).
 */
internal fun cornerRadiiOf(
  shape: Shape,
  size: Size,
  density: Density,
  layoutDirection: LayoutDirection,
): FluidCornerRadii {
  val half = (size.minDimension / 2f).finiteOrZero().coerceAtLeast(0f)
  fun cap(r: Float) = r.finiteOrZero().coerceIn(0f, half)
  return when {
    shape === RectangleShape || half <= 0f -> FluidCornerRadii.Zero
    shape is CornerBasedShape -> {
      val topStart = shape.topStart.toPx(size, density)
      val topEnd = shape.topEnd.toPx(size, density)
      val bottomEnd = shape.bottomEnd.toPx(size, density)
      val bottomStart = shape.bottomStart.toPx(size, density)
      val rtl = layoutDirection == LayoutDirection.Rtl &&
        shape !is AbsoluteRoundedCornerShape && shape !is AbsoluteCutCornerShape
      FluidCornerRadii(
        topLeft = cap(if (rtl) topEnd else topStart),
        topRight = cap(if (rtl) topStart else topEnd),
        bottomRight = cap(if (rtl) bottomStart else bottomEnd),
        bottomLeft = cap(if (rtl) bottomEnd else bottomStart),
      )
    }
    else -> when (val outline = shape.createOutline(size, layoutDirection, density)) {
      is Outline.Rounded -> outline.roundRect.let {
        FluidCornerRadii(
          topLeft = cap(min(it.topLeftCornerRadius.x, it.topLeftCornerRadius.y)),
          topRight = cap(min(it.topRightCornerRadius.x, it.topRightCornerRadius.y)),
          bottomRight = cap(min(it.bottomRightCornerRadius.x, it.bottomRightCornerRadius.y)),
          bottomLeft = cap(min(it.bottomLeftCornerRadius.x, it.bottomLeftCornerRadius.y)),
        )
      }
      else -> FluidCornerRadii.Zero
    }
  }
}

/**
 * I raggi di una [FluidConcentricShape] di minimo [minimum] posata in [piece], dentro un contenitore di forma
 * [containerShape] e taglia [containerSize]: [cornerRadiiOf] più [concentricCornerRadii].
 *
 * [containerRadii] serve quando il contenitore è a sua volta concentrico: chi conosce l'albero lo risolve contro il
 * suo contenitore; di serie vale il suo minimo, come una forma concentrica sola.
 */
internal fun concentricRadiiIn(
  containerShape: Shape,
  containerSize: Size,
  piece: Rect,
  minimum: Dp,
  density: Density,
  layoutDirection: LayoutDirection,
  containerRadii: (FluidConcentricShape) -> FluidCornerRadii = {
    cornerRadiiOf(it, containerSize, density, layoutDirection)
  },
): FluidCornerRadii {
  val radii = if (containerShape is FluidConcentricShape) {
    containerRadii(containerShape)
  } else {
    cornerRadiiOf(containerShape, containerSize, density, layoutDirection)
  }
  return concentricCornerRadii(radii, containerSize, piece, with(density) { minimum.toPx() })
}

/**
 * La forma che un modificatore dell'engine disegna al posto di questa [FluidConcentricShape], una volta noti i raggi:
 * una [ContinuousCornerShape] con gli stessi angoli, in pixel, e lo **stesso** `smoothing`. Così il piano del vetro, la
 * lente e i riflessi la ricevono come ogni altra forma ad angoli tondi (3.7).
 *
 * [radii] sono angoli fisici; la forma che esce li porta come inizio e fine nella direzione [layoutDirection], quindi
 * va disegnata in quella direzione.
 */
internal fun FluidConcentricShape.resolvedTo(
  radii: FluidCornerRadii,
  layoutDirection: LayoutDirection,
): ContinuousCornerShape {
  val rtl = layoutDirection == LayoutDirection.Rtl
  return ContinuousCornerShape(
    topStart = CornerSize(if (rtl) radii.topRight else radii.topLeft),
    topEnd = CornerSize(if (rtl) radii.topLeft else radii.topRight),
    bottomEnd = CornerSize(if (rtl) radii.bottomLeft else radii.bottomRight),
    bottomStart = CornerSize(if (rtl) radii.bottomRight else radii.bottomLeft),
    smoothing = smoothing,
  )
}

private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f

// --- Risoluzione nell'albero ----------------------------------------------------------------------------------------

/**
 * Il contenitore più vicino sopra un nodo. Un `ModifierLocal`, e non un `CompositionLocal`, perché il contenitore lo
 * dichiara un modificatore e perché la distanza dal bordo si sa solo al layout: chi legge riceve il **nodo** del
 * contenitore, e con lui le sue coordinate e la sua forma.
 */
internal val ModifierLocalFluidContainerShape = modifierLocalOf<FluidContainerShapeNode?> { null }

/**
 * Oltre questa profondità di contenitori concentrici annidati si smette di risalire e si usa il minimo. Nessuna
 * interfaccia ne ha più di tre (schermo, foglio, barra, gruppo); è una guardia, non un limite di progetto.
 */
private const val MaxConcentricDepth = 8

internal class FluidContainerShapeNode(shape: Shape) :
  Modifier.Node(), ModifierLocalModifierNode, LayoutAwareModifierNode {

  /** Stato di Compose: chi ha risolto i raggi su questa forma li rifà quando cambia ([FluidConcentricRadiiNode]). */
  var shape: Shape by mutableStateOf(shape)

  /** Il riquadro di questo punto della catena, all'ultimo posizionamento. */
  var coordinates: LayoutCoordinates? = null
    private set

  override val providedValues: ModifierLocalMap = modifierLocalMapOf(ModifierLocalFluidContainerShape to this)

  override fun onPlaced(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
  }

  override fun onDetach() {
    coordinates = null
  }

  /** Il contenitore di questo contenitore, per una forma concentrica a sua volta. `current` salta il nodo stesso. */
  private fun outerContainer(): FluidContainerShapeNode? =
    if (isAttached) ModifierLocalFluidContainerShape.current else null

  /** I raggi del contenitore in pixel, o `null` se non è ancora posato. */
  fun cornerRadii(density: Density, layoutDirection: LayoutDirection, depth: Int): FluidCornerRadii? {
    val box = coordinates?.takeIf { it.isAttached } ?: return null
    val current = shape
    return if (current is FluidConcentricShape) {
      resolveConcentricRadii(outerContainer(), box, current.minimum, density, layoutDirection, depth + 1)
    } else {
      cornerRadiiOf(current, box.size.toSize(), density, layoutDirection)
    }
  }
}

/**
 * I raggi concentrici del nodo [piece] dentro [container]; senza contenitore, o finché non è posato, il minimo.
 *
 * È l'aggancio per i modificatori dell'engine che ricevono una forma (il vetro, le barre, i fogli): leggono il
 * contenitore con `ModifierLocalFluidContainerShape.current` e lo passano qui, o delegano a [FluidConcentricRadiiNode].
 */
internal fun resolveConcentricRadii(
  container: FluidContainerShapeNode?,
  piece: LayoutCoordinates,
  minimum: Dp,
  density: Density,
  layoutDirection: LayoutDirection,
  depth: Int = 0,
): FluidCornerRadii {
  val minimumPx = with(density) { minimum.toPx() }
  val alone = concentricRadiiAlone(piece.size.toSize(), minimumPx)
  if (container == null || depth > MaxConcentricDepth || !piece.isAttached) return alone
  val box = container.coordinates?.takeIf { it.isAttached } ?: return alone
  val containerRadii = container.cornerRadii(density, layoutDirection, depth) ?: return alone
  val rect = box.localBoundingBoxOf(piece, clipBounds = false)
  return concentricCornerRadii(containerRadii, box.size.toSize(), rect, minimumPx)
}

/**
 * Il nodo che risolve i raggi concentrici del proprio riquadro e li consegna a [onRadii]: lo usa
 * [fluidConcentricRadii], e un modificatore dell'engine lo può delegare per risolvere una [FluidConcentricShape]
 * (poi [resolvedTo] per la forma).
 *
 * Ricalcola quando il nodo è riposizionato (anche un contenitore che cambia taglia riposiziona i discendenti) e quando
 * cambia la forma del contenitore (letta sotto osservazione). Consegna sempre: chi riceve tiene i valori uguali per
 * conto suo (uno stato di Compose lo fa da sé).
 */
internal class FluidConcentricRadiiNode(
  var minimum: Dp,
  var onRadii: (FluidCornerRadii) -> Unit,
) : Modifier.Node(), ModifierLocalModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode {

  private var coordinates: LayoutCoordinates? = null

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    resolve()
  }

  override fun onObservedReadsChanged() {
    resolve()
  }

  override fun onDetach() {
    coordinates = null
  }

  fun resolve() {
    if (!isAttached) return
    val piece = coordinates?.takeIf { it.isAttached } ?: return
    val density = requireDensity()
    val layoutDirection = requireLayoutDirection()
    var radii: FluidCornerRadii? = null
    observeReads {
      radii = resolveConcentricRadii(ModifierLocalFluidContainerShape.current, piece, minimum, density, layoutDirection)
    }
    radii?.let(onRadii)
  }
}

private data class FluidContainerShapeElement(val shape: Shape) : ModifierNodeElement<FluidContainerShapeNode>() {
  override fun create(): FluidContainerShapeNode = FluidContainerShapeNode(shape)

  override fun update(node: FluidContainerShapeNode) {
    node.shape = shape
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidContainerShape"
    properties["shape"] = shape
  }
}

private data class FluidConcentricRadiiElement(
  val state: FluidConcentricRadiiState,
) : ModifierNodeElement<FluidConcentricRadiiNode>() {
  override fun create(): FluidConcentricRadiiNode =
    FluidConcentricRadiiNode(state.minimum) { state.radii = it }

  override fun update(node: FluidConcentricRadiiNode) {
    node.minimum = state.minimum
    node.onRadii = { state.radii = it }
    node.resolve()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidConcentricRadii"
    properties["minimum"] = state.minimum
  }
}
