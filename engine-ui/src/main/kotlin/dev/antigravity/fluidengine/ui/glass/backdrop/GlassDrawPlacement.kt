package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isIdentity
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.InverseLayerScope

/*
 * F7.31 · I pomelli di vetro si spostano nel disegno.
 *
 * Una maniglia di vetro che corre (il pomello di un cursore, la maniglia dello scrubber mentre il lettore suona, il
 * pomello di un interruttore) si spostava con la traslazione del suo `graphicsLayer`, e la goccia cresceva sotto il dito
 * con la scala del suo. Per Compose una traslazione o una scala che cambia è un cambio di layout
 * (`NodeCoordinator.updateLayerParameters` → `Owner.onLayoutChange`): con un servizio di accessibilità acceso
 * («Collegamento a Windows» sul Tab S9 di Alessio, TalkBack) la pagina intera si ricontrolla ogni 100 ms finché la maniglia
 * si muove (`checkForSemanticsChanges`, 1,3–4,4 ms sul thread dell'interfaccia del Tab S9, fuori dal fotogramma).
 *
 * Qui le stesse trasformazioni si fanno **nel disegno**: lo strato resta, fermo, e dentro il suo elenco di disegno c'è la
 * traslazione (o la matrice della goccia). Muoverla ridisegna lo strato e basta: niente layout, niente semantica.
 *
 * Il vetro campiona nella posizione giusta perché il nodo del vetro (`DrawBackdropNode`) sa di essere disegnato altrove:
 * l'origine del campione ([GlassSamplePlacement]) si calcola con le stesse operazioni che Compose faceva sulle matrici
 * degli strati (`resetToPivotedTransform`, `Matrix.map`), e si passa a `localPositionOf` come punto di partenza. Il
 * risultato è lo stesso numero, al bit, di quando la trasformazione stava negli strati.
 *
 * E il disegno è lo stesso al bit: la matrice della goccia si costruisce come la costruisce il renderer per uno strato
 * (`setTranslate`, `preRotate`, `preScale` attorno al perno, su un `android.graphics.Matrix`), e si applica come lui (una
 * traslazione sola con `translate`, il resto con `concat`), nello stesso punto della pila degli strati.
 *
 * Il contratto: [glassDrawOffset] sta su una maniglia, cioè su qualcosa che **non contiene** la sorgente che il suo vetro
 * campiona (la pagina, la pista registrata). Uno spostamento attorno a sorgente e vetro insieme sarebbe contato due volte.
 * E il tocco non segue lo spostamento: si usa dove il gesto sta sul controllo intero (il cursore, lo scrubber,
 * l'interruttore), non dove la maniglia stessa è il bersaglio (il segmentato, lo scorrimento rapido).
 */

/**
 * F7.31: sposta il contenuto di [offset] pixel **nel disegno**, dentro uno strato suo che non si muove: al posto di
 * `graphicsLayer { translationX = … }` per una maniglia di vetro. [offset] si legge nel disegno, e un suo cambio ridisegna
 * lo strato e il vetro che c'è dentro, senza layout. Il vetro dentro (una [glassDrop][dev.antigravity.fluidengine.ui.glass.interaction.glassDrop],
 * un `glassSurface`) campiona dove si vede.
 *
 * Il tocco, la semantica e le misure restano alla posizione del layout: vedi il contratto in cima al file.
 */
internal fun Modifier.glassDrawOffset(offset: Density.() -> Offset): Modifier =
  this.graphicsLayer().then(GlassDrawOffsetElement(offset))

/** La chiave dei nodi di [glassDrawOffset], per chi li cerca fra gli antenati. */
internal object GlassDrawOffsetKey

private class GlassDrawOffsetElement(val offset: Density.() -> Offset) : ModifierNodeElement<GlassDrawOffsetNode>() {
  override fun create() = GlassDrawOffsetNode(offset)

  override fun update(node: GlassDrawOffsetNode) {
    node.offset = offset
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "glassDrawOffset"
    properties["offset"] = offset
  }

  override fun equals(other: Any?): Boolean = other is GlassDrawOffsetElement && other.offset === offset

  override fun hashCode(): Int = System.identityHashCode(offset)
}

/**
 * Il nodo di [glassDrawOffset]. Lo spostamento sta in uno stato (per riferimento): chi lo legge nel disegno (questo nodo e
 * il vetro che c'è dentro) si ridisegna anche quando cambia la lambda, non solo quando cambia uno stato che lei legge.
 */
internal class GlassDrawOffsetNode(offset: Density.() -> Offset) : Modifier.Node(), DrawModifierNode, TraversableNode {
  private val current = mutableStateOf(offset, referentialEqualityPolicy())

  var offset: Density.() -> Offset
    get() = current.value
    set(value) {
      current.value = value
    }

  override val traverseKey: Any get() = GlassDrawOffsetKey

  /** Lo spostamento adesso, in pixel. Letto nel disegno di chi lo chiede: un suo cambio lo ridisegna. */
  fun shift(density: Density): Offset = current.value(density)

  /** Lo spazio in cui lo spostamento si applica: quello di ciò che questo nodo disegna. */
  fun space(): LayoutCoordinates? = if (isAttached) requireLayoutCoordinates() else null

  /** Per le prove (F7.31): dove la maniglia si disegna adesso rispetto al suo posto, in pixel. */
  var lastDrawn: Offset = Offset.Zero
    private set

  override fun ContentDrawScope.draw() {
    val o = shift(this)
    lastDrawn = o
    if (o.x == 0f && o.y == 0f) {
      drawContent()
      return
    }
    // Come il renderer con uno strato che ha solo una traslazione: un `translate`, dentro un salvataggio.
    val canvas = drawContext.canvas
    canvas.save()
    canvas.translate(o.x, o.y)
    try {
      drawContent()
    } finally {
      canvas.restore()
    }
  }
}

/**
 * F7.31: il `layerBlock` di un vetro che va applicato **nel disegno** e non in uno strato (la goccia: la scala, lo
 * stiramento). `drawBackdrop` lo riconosce: mette uno strato fermo e, dentro, la trasformazione del blocco
 * ([glassLayerOf]); il nodo del vetro ne tiene conto quando campiona ([GlassSamplePlacement]). Per chi lo invoca è il
 * blocco di sempre: l'inversa della scala che `LayerBackdrop` applica quando campiona non cambia.
 *
 * Il blocco può scrivere traslazione, scala, rotazione sul piano e perno; non alfa, ritaglio, forma o effetti, che restano
 * proprietà di uno strato (un'alfa sotto 1 isolerebbe uno strato fermo e ne taglierebbe il disegno spostato).
 *
 * [mirrorInLayer], per le prove: se risponde vero quando lo strato nasce, lo strato fermo rifà il blocco su uno scope di
 * scarto ogni volta che le sue letture cambiano (senza scrivere niente nello strato), come lo strato di prima, che lo
 * rifaceva senza un disegno. Serve alle sonde che leggono il blocco (`glassDropLayerProbe`) in Robolectric, dove la
 * finestra si disegna solo a una PixelCopy. Di serie falso: lo strato non legge niente e non si rifà mai.
 */
internal class GlassDrawnLayerBlock(
  val block: GraphicsLayerScope.() -> Unit,
  private val mirrorInLayer: () -> Boolean = NeverMirror,
) : (GraphicsLayerScope) -> Unit {
  override fun invoke(scope: GraphicsLayerScope) = scope.block()

  private val scratch = InverseLayerScope()

  /** Il blocco dello strato fermo: niente, o (per le prove) il blocco su uno scope di scarto. */
  val layerMirror: GraphicsLayerScope.() -> Unit = {
    if (mirrorInLayer()) {
      scratch.reset()
      scratch.size = size
      scratch.density = density
      scratch.fontScale = fontScale
      block(scratch)
    }
  }
}

private val NeverMirror: () -> Boolean = { false }

/**
 * Lo strato di un vetro col suo [layerBlock]: quello di sempre (`graphicsLayer(layerBlock)`), o, per un
 * [GlassDrawnLayerBlock], uno strato fermo con la trasformazione nel disegno.
 */
internal fun glassLayerOf(layerBlock: (GraphicsLayerScope.() -> Unit)?): Modifier = when (layerBlock) {
  null -> Modifier
  // Lo strato non scrive niente: il suo blocco è vuoto, o rifà il disegnato su uno scope di scarto (per le prove).
  is GlassDrawnLayerBlock -> Modifier.graphicsLayer(layerBlock.layerMirror).then(GlassDrawnLayerElement(layerBlock))
  else -> Modifier.graphicsLayer(layerBlock)
}

private class GlassDrawnLayerElement(val block: GlassDrawnLayerBlock) : ModifierNodeElement<GlassDrawnLayerNode>() {
  override fun create() = GlassDrawnLayerNode(block)

  override fun update(node: GlassDrawnLayerNode) {
    node.block = block
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "glassDrawnLayer"
    properties["block"] = block.block
  }

  override fun equals(other: Any?): Boolean = other is GlassDrawnLayerElement && other.block === block

  override fun hashCode(): Int = System.identityHashCode(block)
}

/**
 * Applica la trasformazione di un [GlassDrawnLayerBlock] al disegno di ciò che segue, come il renderer la applicherebbe
 * a uno strato con quelle proprietà (`RenderProperties::updateMatrix` e `RenderNodeDrawable::setViewProperties`):
 * identità, niente; solo traslazione, un `translate`; altrimenti la matrice `setTranslate · preRotate · preScale` attorno
 * al perno, con `concat`. Le letture del blocco sono osservate dal disegno: una scala che cambia ridisegna lo strato.
 */
private class GlassDrawnLayerNode(var block: GlassDrawnLayerBlock) : Modifier.Node(), DrawModifierNode {
  private val scope = InverseLayerScope()
  private val matrix = android.graphics.Matrix()
  private val values = FloatArray(9)

  override fun ContentDrawScope.draw() {
    scope.reset()
    scope.size = size
    scope.density = density
    scope.fontScale = fontScale
    block.block(scope)
    val pivotX: Float
    val pivotY: Float
    if (scope.transformOrigin == TransformOrigin.Center) {
      // Il perno di serie del renderer: la metà della taglia dello strato.
      pivotX = size.width / 2f
      pivotY = size.height / 2f
    } else {
      pivotX = scope.transformOrigin.pivotFractionX * size.width
      pivotY = scope.transformOrigin.pivotFractionY * size.height
    }
    matrix.setTranslate(scope.translationX, scope.translationY)
    matrix.preRotate(scope.rotationZ, pivotX, pivotY)
    matrix.preScale(scope.scaleX, scope.scaleY, pivotX, pivotY)
    if (matrix.isIdentity) {
      drawContent()
      return
    }
    matrix.getValues(values)
    val canvas = drawContext.canvas
    canvas.save()
    val translateOnly = values[android.graphics.Matrix.MSCALE_X] == 1f && values[android.graphics.Matrix.MSCALE_Y] == 1f &&
      values[android.graphics.Matrix.MSKEW_X] == 0f && values[android.graphics.Matrix.MSKEW_Y] == 0f
    if (translateOnly) {
      canvas.translate(scope.translationX, scope.translationY)
    } else {
      canvas.nativeCanvas.concat(matrix)
    }
    try {
      drawContent()
    } finally {
      canvas.restore()
    }
  }
}

/**
 * Il `layerBlock` che il nodo del vetro passa al fondale quando è disegnato lontano dal suo layout: [inner] è il blocco di
 * sempre (l'inversa della scala, in `LayerBackdrop`), [origin] il punto del suo spazio dove sta davvero l'angolo in alto a
 * sinistra di ciò che si vede. `LayerBackdrop` lo usa come punto di partenza di `localPositionOf`.
 */
internal class GlassPlacedLayerBlock : (GraphicsLayerScope) -> Unit {
  var inner: (GraphicsLayerScope.() -> Unit)? = null
  var origin: Offset = Offset.Zero

  override fun invoke(scope: GraphicsLayerScope) {
    inner?.invoke(scope)
  }
}

/** Il blocco da invocare per l'inversa, e l'origine del campione, di un `layerBlock` dato al fondale. */
internal fun (GraphicsLayerScope.() -> Unit)?.placedInner(): (GraphicsLayerScope.() -> Unit)? =
  if (this is GlassPlacedLayerBlock) inner else this

internal fun (GraphicsLayerScope.() -> Unit)?.placedOrigin(): Offset =
  if (this is GlassPlacedLayerBlock) origin else Offset.Zero

/**
 * Dove il nodo di un vetro è disegnato rispetto al suo layout (F7.31): la sua trasformazione nel disegno
 * ([GlassDrawnLayerBlock]) e gli spostamenti degli antenati ([glassDrawOffset]). Una per nodo; nessuna allocazione per
 * fotogramma.
 */
internal class GlassSamplePlacement {
  private var shifts: Array<GlassDrawOffsetNode> = NoShifts
  private var resolved = false
  private val scope = InverseLayerScope()
  private val matrix = Matrix()
  private val placed = GlassPlacedLayerBlock()

  /** L'origine dell'ultimo [update]: zero per un vetro che sta dove dice il suo layout. */
  var origin: Offset = Offset.Zero
    private set

  /** Da rifare al prossimo [update]: il nodo si è attaccato, è stato aggiornato o riusato. */
  fun invalidate() {
    resolved = false
  }

  fun clear() {
    shifts = NoShifts
    resolved = false
    origin = Offset.Zero
  }

  private fun resolve(node: DelegatableNode) {
    resolved = true
    var found: ArrayList<GlassDrawOffsetNode>? = null
    node.traverseAncestors(GlassDrawOffsetKey) {
      if (it is GlassDrawOffsetNode) (found ?: ArrayList<GlassDrawOffsetNode>(2).also { list -> found = list }).add(it)
      true
    }
    shifts = found?.toTypedArray() ?: NoShifts
  }

  /**
   * L'origine del campione adesso, per un nodo della taglia [size] con [layerBlock], che campiona con le coordinate
   * [reader]. Nel disegno: le letture (il blocco, gli spostamenti) sono osservate, e un loro cambio ridisegna il vetro.
   *
   * I passi sono quelli di `localPositionOf` con le trasformazioni negli strati, nello stesso ordine: prima la matrice
   * della goccia (`resetToPivotedTransform` e `map` dello zero, come `GraphicsLayerOwnerLayer`), poi, dal più vicino, la
   * traslazione di ogni antenato spostato. Fra una maniglia e il suo vetro c'è solo una traslazione (gli strati piazzano
   * ciò che segue a zero, e sono fermi), e la `map` di una sola traslazione è una somma: il numero è lo stesso al bit. Se
   * invece in mezzo c'è uno strato che scala (lo zoom di un visore sul pomello del «prima e dopo»), lo spostamento si porta
   * nello spazio del vetro con `localPositionOf`: giusto, anche se non con le stesse operazioni.
   */
  fun update(
    node: DelegatableNode,
    density: Density,
    size: Size,
    layerBlock: (GraphicsLayerScope.() -> Unit)?,
    reader: LayoutCoordinates? = null,
  ): Offset {
    if (!resolved || shifts.any { !it.isAttached }) {
      if (node.node.isAttached) resolve(node) else shifts = NoShifts
    }
    var p = if (layerBlock is GlassDrawnLayerBlock) drawnOrigin(layerBlock, density, size) else Offset.Zero
    for (shift in shifts) {
      val d = shift.shift(density)
      if (d.x == 0f && d.y == 0f) continue
      val r = reader?.takeIf { it.isAttached }
      val s = if (r != null) shift.space()?.takeIf { it.isAttached } else null
      p = if (r == null || s == null || translatesOnly(r, s)) {
        Offset(p.x + d.x, p.y + d.y)
      } else {
        // Il punto del vetro che, nello spazio dello spostamento, sta [d] più in là di dove sta adesso [p].
        r.localPositionOf(s, s.localPositionOf(r, p) + d)
      }
    }
    origin = p
    return p
  }

  /** Vero se fra [reader] e [space] ci sono solo traslazioni (nessuna scala né rotazione): il caso delle maniglie. */
  private fun translatesOnly(reader: LayoutCoordinates, space: LayoutCoordinates): Boolean {
    val a = reader.localPositionOf(space, Offset.Zero)
    val b = reader.localPositionOf(space, UnitProbe)
    return kotlin.math.abs(b.x - a.x - UnitProbe.x) < TranslateTolerance && kotlin.math.abs(b.y - a.y - UnitProbe.y) < TranslateTolerance
  }

  /** Il blocco da dare al fondale: quello di sempre se il vetro sta dove dice il layout, altrimenti con l'origine. */
  fun blockFor(layerBlock: (GraphicsLayerScope.() -> Unit)?): (GraphicsLayerScope.() -> Unit)? {
    if (shifts.isEmpty() && layerBlock !is GlassDrawnLayerBlock) return layerBlock
    placed.inner = layerBlock
    placed.origin = origin
    return placed
  }

  private fun drawnOrigin(block: GlassDrawnLayerBlock, density: Density, size: Size): Offset {
    scope.reset()
    scope.size = size
    scope.density = density.density
    scope.fontScale = density.fontScale
    block.block(scope)
    val pivot = if (scope.transformOrigin == TransformOrigin.Center) {
      size.center
    } else {
      Offset(scope.transformOrigin.pivotFractionX * size.width, scope.transformOrigin.pivotFractionY * size.height)
    }
    matrix.resetToPivotedTransform(
      pivotX = pivot.x,
      pivotY = pivot.y,
      translationX = scope.translationX,
      translationY = scope.translationY,
      rotationX = scope.rotationX,
      rotationY = scope.rotationY,
      rotationZ = scope.rotationZ,
      scaleX = scope.scaleX,
      scaleY = scope.scaleY,
    )
    return if (matrix.isIdentity()) Offset.Zero else matrix.map(Offset.Zero)
  }
}

private val NoShifts = emptyArray<GlassDrawOffsetNode>()

/** Il punto con cui [GlassSamplePlacement] guarda se fra due spazi c'è una scala, e lo scarto che conta come nessuna. */
private val UnitProbe = Offset(1024f, 1024f)
private const val TranslateTolerance = 0.01f
