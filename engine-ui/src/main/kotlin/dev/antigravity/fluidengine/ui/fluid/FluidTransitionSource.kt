package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.LocalFluidToolbarMinimize
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneClusterId
import dev.antigravity.fluidengine.ui.page.LocalFluidPageBar
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront

/**
 * L'identità da passare a una presentazione perché possa ritrovare la vista che l'ha aperta.
 * Si può conservare mentre cambia la composizione, per esempio per cercare l'origine al ritorno.
 */
@Stable
class FluidTransitionSource internal constructor(private val key: FluidTransitionSourceKey) {
  /** Usa lo stesso [id] e lo stesso [namespace] del modificatore [fluidTransitionSource]. */
  constructor(id: Any, namespace: FluidGlassNamespace) : this(FluidTransitionSourceKey(namespace, id))

  /**
   * Legge sul thread UI la geometria disponibile adesso. È `null` se la sorgente non è posata, è uscita,
   * è completamente fuori vista o la sua pagina ha ceduto il davanti. Per lo zoom di ritorno,
   * [includeCoveredPage] permette di leggere anche una sorgente ancora posata nella pagina sotto.
   */
  fun resolve(includeCoveredPage: Boolean = false): FluidTransitionSourceGeometry? {
    val nodes = transitionSources[key] ?: return null
    return nodes.firstNotNullOfOrNull { it.resolve(includeCoveredPage = false) }
      ?: if (includeCoveredPage) nodes.firstNotNullOfOrNull { it.resolve(includeCoveredPage = true) } else null
  }

  internal var growingGlass: dev.antigravity.fluidengine.ui.page.GrowingPageGlass? = null
    private set

  internal fun withGrowingGlass(material: dev.antigravity.fluidengine.ui.page.GrowingPageGlass) =
    FluidTransitionSource(key).also { it.growingGlass = material }

  // F5B.3: la sorgente «consumata». Una presentazione nata da qui (lo zoom di una pagina o di un foglio) la nasconde
  // finché è aperta e la restituisce al ritorno, nello stesso fotogramma in cui la sagoma ci rientra: la sorgente e
  // l'arrivo non si vedono mai insieme. Un contatore per identità, perché due presentazioni possono tenere la stessa.
  private var held = false
  private var heldSnapshot = false

  /**
   * Nasconde la sorgente (tutte le viste con questa identità) finché non si chiama [release]. Idempotente. Con
   * [snapshot] la vista consumata, invece di non disegnarsi, si registra in un layer ([snapshot]): serve alla variante
   * di Apple in cui la pagina si dissolve nella sorgente dentro la sagoma (F5B.3, di serie spenta). Senza, non registra
   * niente: nessun costo, nessun layer in più.
   */
  internal fun consume(snapshot: Boolean = false) {
    if (!held) {
      held = true
      consumedSources[key] = (consumedSources[key] ?: 0) + 1
    }
    if (snapshot && !heldSnapshot) {
      heldSnapshot = true
      snapshotRequests[key] = (snapshotRequests[key] ?: 0) + 1
    }
  }

  /** Restituisce la sorgente tenuta da [consume]. Idempotente. */
  internal fun release() {
    if (heldSnapshot) {
      heldSnapshot = false
      val requests = (snapshotRequests[key] ?: 1) - 1
      if (requests <= 0) snapshotRequests.remove(key) else snapshotRequests[key] = requests
    }
    if (!held) return
    held = false
    val left = (consumedSources[key] ?: 1) - 1
    if (left <= 0) consumedSources.remove(key) else consumedSources[key] = left
  }

  /**
   * La fotografia della vista consumata con `consume(snapshot = true)`, alla sua misura, o null finché non si è disegnata
   * una volta consumata. La disegna chi presenta, dentro la sua sagoma.
   */
  internal val snapshot: FluidTransitionSnapshot? get() = sourceSnapshots[key]

  /** Vero mentre questo riferimento tiene la sorgente nascosta. */
  internal val isConsumed: Boolean get() = held

  /** Vero se qualcuno tiene nascosta questa identità: è ciò che legge il disegno delle viste. */
  internal val isHidden: Boolean get() = key in consumedSources

  /** Una presa per presentazione, anche quando due chiamanti condividono lo stesso riferimento. */
  internal fun acquireForModal(): FluidTransitionSource? {
    val node = transitionSources[key]?.firstOrNull { it.resolve(includeCoveredPage = false) != null }
      ?: return null
    val handoff = node.modalHandoff
    return FluidTransitionSource(key).also {
      it.modalHandoff = handoff
      handoff?.invoke(true)
      it.consume(snapshot = true)
    }
  }

  internal val hasSeparateModalBody: Boolean get() = modalHandoff != null

  private var modalHandoff: ((Boolean) -> Unit)? = null

  internal fun releaseFromModal() {
    modalHandoff?.invoke(false)
    modalHandoff = null
    release()
  }
}

/**
 * La fotografia che basta a iniziare un viaggio. [bounds] è in pixel nella radice Compose della sorgente.
 * [shape] va risolta alla taglia di [bounds], con la densità e il verso della sorgente.
 */
@Immutable
data class FluidTransitionSourceGeometry(val bounds: Rect, val shape: Shape)

/**
 * Dà a una vista un'origine per menu, presentazioni e zoom senza aggiungere disegno o cambiare la misura.
 * Applica il modificatore al riquadro che deve partire e dai la sua [shape] se ha un contorno proprio.
 * L'identità è la coppia [namespace] e [id]. Se è ripetuta, vale la prima sorgente posata ancora disponibile.
 *
 * Mentre una pagina o un foglio aperti con lo zoom da qui sono in scena (F5B.3) la sorgente è «consumata»: non disegna
 * ciò che le sta **dopo** nella catena dei modificatori e rinasce quando la sagoma ci rientra. Mettilo quindi prima di
 * fondo, bordo e contenuto (in testa alla catena), così sparisce tutta la vista e non solo il suo interno.
 */
fun Modifier.fluidTransitionSource(
  id: Any,
  namespace: FluidGlassNamespace,
  shape: Shape = RectangleShape,
): Modifier = this then FluidTransitionSourceElement(FluidTransitionSourceKey(namespace, id), shape)

/**
 * L'origine di un elemento dichiarato nella barra, cercato per [id] nella pagina corrente (nella radice
 * Compose fuori da un host). Chiamalo nel contesto della pagina che dichiara la barra.
 */
@Composable
fun rememberFluidToolbarTransitionSource(id: Any): FluidTransitionSource {
  val owner = fluidToolbarTransitionSourceOwner()
  return remember(owner, id) { FluidTransitionSource(FluidTransitionSourceKey(owner, id)) }
}

@Composable
private fun fluidToolbarTransitionSourceOwner(): Any = LocalFluidPageBar.current ?: LocalView.current

@Composable
internal fun Modifier.fluidToolbarTransitionSource(
  id: Any,
  glassId: FluidGlassQualifiedId,
  modalHandoff: ((Boolean) -> Unit)? = null,
): Modifier =
  this then FluidTransitionSourceElement(
    key = FluidTransitionSourceKey(fluidToolbarTransitionSourceOwner(), id),
    shape = FluidCapsuleShape,
    toolbar = FluidToolbarTransitionGeometry(
      currentFluidGlassContainer(FluidCapsuleShape), glassId, LocalFluidToolbarMinimize.current,
    ),
    modalHandoff = modalHandoff,
  )

internal data class FluidTransitionSourceKey(val owner: Any, val id: Any)

// Solo i nodi posati. La fine di una registrazione lascia posto agli altri senza cambiare il loro ordine.
private val transitionSources = mutableStateMapOf<FluidTransitionSourceKey, List<FluidTransitionSourceNode>>()

// Le identità consumate da una presentazione aperta (F5B.3), con quante la tengono. Letto nel disegno dei nodi.
private val consumedSources = mutableStateMapOf<FluidTransitionSourceKey, Int>()

// Le identità consumate di cui chi presenta vuole la fotografia (la variante con la dissolvenza), e le fotografie.
// Stato: il disegno della sorgente legge le richieste, quello di chi presenta le fotografie.
private val snapshotRequests = mutableStateMapOf<FluidTransitionSourceKey, Int>()
private val sourceSnapshots = mutableStateMapOf<FluidTransitionSourceKey, FluidTransitionSnapshot>()

/** La fotografia di una sorgente consumata: il suo disegno in un layer, alla sua misura in pixel. */
internal class FluidTransitionSnapshot(val layer: GraphicsLayer, val size: Size)

private data class FluidTransitionSourceElement(
  val key: FluidTransitionSourceKey,
  val shape: Shape,
  val toolbar: FluidToolbarTransitionGeometry? = null,
  val modalHandoff: ((Boolean) -> Unit)? = null,
) : ModifierNodeElement<FluidTransitionSourceNode>() {
  override fun create() = FluidTransitionSourceNode(this)
  override fun update(node: FluidTransitionSourceNode) = node.update(this)
}

private class FluidTransitionSourceNode(source: FluidTransitionSourceElement) :
  Modifier.Node(), GlobalPositionAwareModifierNode, CompositionLocalConsumerModifierNode, DrawModifierNode {
  private var source by mutableStateOf(source)
  private var coordinates: LayoutCoordinates? = null
  private var placedBounds by mutableStateOf<Rect?>(null)

  private var snapshotLayer: GraphicsLayer? = null
  val modalHandoff: ((Boolean) -> Unit)? get() = source.modalHandoff

  // Consumata, la vista non disegna: la sua sagoma è in viaggio come pagina o foglio (F5B.3). A riposo passa e basta.
  // Se chi presenta ne vuole la fotografia (la variante con la dissolvenza), la vista si registra in un layer suo.
  override fun ContentDrawScope.draw() {
    val key = source.key
    if (key !in consumedSources) {
      drawContent()
      return
    }
    if (key !in snapshotRequests) return
    val layer = snapshotLayer ?: requireGraphicsContext().createGraphicsLayer().also { snapshotLayer = it }
    layer.record { this@draw.drawContent() }
    val current = sourceSnapshots[key]
    if (current == null || current.layer !== layer || current.size != size) {
      sourceSnapshots[key] = FluidTransitionSnapshot(layer, size)
    }
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    val firstPlacement = this.coordinates == null
    this.coordinates = coordinates
    placedBounds = coordinates.boundsInRoot()
    if (firstPlacement) register()
  }

  fun update(next: FluidTransitionSourceElement) {
    val moved = source.key != next.key && coordinates != null
    if (moved) unregister()
    source = next
    if (moved) register()
  }

  override fun onReset() = clear()
  override fun onDetach() = clear()

  private fun register() {
    transitionSources[source.key] = transitionSources[source.key].orEmpty() + this
  }

  private fun unregister() {
    val remaining = transitionSources[source.key].orEmpty() - this
    if (remaining.isEmpty()) transitionSources.remove(source.key)
    else transitionSources[source.key] = remaining
  }

  private fun clear() {
    if (coordinates != null) unregister()
    coordinates = null
    placedBounds = null
    snapshotLayer?.let { layer ->
      if (sourceSnapshots[source.key]?.layer === layer) sourceSnapshots.remove(source.key)
      requireGraphicsContext().releaseGraphicsLayer(layer)
    }
    snapshotLayer = null
  }

  fun resolve(includeCoveredPage: Boolean): FluidTransitionSourceGeometry? {
    val placed = coordinates?.takeIf { isAttached && it.isAttached } ?: return null
    val toolbar = source.toolbar
    if (placedBounds == null) return null
    // La barra ancorata usa il contesto della dichiarazione che sta mostrando, anche prima del cambio del davanti.
    if (toolbar == null && !includeCoveredPage && !currentValueOf(LocalFluidRouteFront).value) return null
    if (toolbar?.minimize?.progress?.let { it >= 1f } == true) return null
    val root = placed.findRootCoordinates()
    toolbar?.resolve()?.let { geometry ->
      val viewport = Rect(0f, 0f, root.size.width.toFloat(), root.size.height.toFloat())
      return geometry.takeIf { it.bounds.overlaps(viewport) }
    }
    if (!placed.boundsInRoot().isUsableSourceBounds()) return null
    val bounds = root.localBoundingBoxOf(placed, clipBounds = false)
    return bounds.takeIf { it.isUsableSourceBounds() }?.let { FluidTransitionSourceGeometry(it, source.shape) }
  }
}

private data class FluidToolbarTransitionGeometry(
  val plane: FluidGlassContainerState?,
  val id: FluidGlassQualifiedId,
  val minimize: FluidToolbarMinimizeProgress?,
) {
  fun resolve(): FluidTransitionSourceGeometry? {
    val plane = plane ?: return null
    val container = plane.coordinates?.takeIf { it.isAttached } ?: return null
    val piece = plane.sourcePiece(id) ?: return null
    val rest = plane.clusterOf(piece)?.pieces?.firstOrNull { it.key === piece }
    val flight = piece.content.drawnFlight()
    val clip = flight?.clip
    val local = clip?.translate(piece.content.place.topLeft) ?: rest?.bounds ?: return null
    if (!local.isUsableSourceBounds()) return null
    val corners = listOf(local.topLeft, local.topRight, local.bottomRight, local.bottomLeft)
      .map(container::localToRoot)
    val bounds = Rect(
      corners.minOf { it.x }, corners.minOf { it.y }, corners.maxOf { it.x }, corners.maxOf { it.y },
    )
    if (!bounds.isUsableSourceBounds()) return null
    if (flight == null || clip == null) return FluidTransitionSourceGeometry(bounds, FluidCapsuleShape)

    // Il ritaglio del piano include il rigonfiamento. I raggi seguono il fotogramma e la scala della radice.
    val radii = flight.radii
    val scale = bounds.minDimension / local.minDimension
    val shape = ContinuousCornerShape(
      CornerSize(radii.topLeft * scale), CornerSize(radii.topRight * scale),
      CornerSize(radii.bottomRight * scale), CornerSize(radii.bottomLeft * scale),
    )
    return FluidTransitionSourceGeometry(bounds, shape)
  }
}

private fun FluidGlassContainerState.sourcePiece(id: FluidGlassQualifiedId): FluidGlassPiece? {
  fun match(key: Any?): FluidGlassPiece? = (key as? FluidGlassPiece)?.takeIf {
    it.attributes.id == id && it.content.attached && it.coordinates?.isAttached == true
  }
  // Il palco vede il destinatario dal primo fotogramma, anche prima che il riposo d'arrivo sia pubblicato.
  return stage?.fields?.firstNotNullOfOrNull { field ->
    field.flights.firstNotNullOfOrNull { match(it.targetKey) }
  } ?: restFields.firstNotNullOfOrNull { field ->
    val key = field.key
    if (key is GlassPlaneClusterId) key.members.firstNotNullOfOrNull(::match) else match(key)
  }
}

private fun Rect.isUsableSourceBounds(): Boolean = isFinite && !isEmpty
