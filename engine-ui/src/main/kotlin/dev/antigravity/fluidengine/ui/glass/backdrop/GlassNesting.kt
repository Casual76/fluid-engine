package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.TraversableNode.Companion.TraverseDescendantsAction
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.traverseAncestors
import androidx.compose.ui.node.traverseDescendants
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer

// ---------------------------------------------------------------------------------------------
// Vetro su vetro (Fluid Engine 2.13.0)
//
// Un pannello di vetro che ne contiene un altro pubblica il proprio *materiale* — il fondale come
// lo vede lui, sfocato, rifratto e tinto, cioe' quello che disegna dietro al contenuto — e la
// superficie che gli sta dentro lo campiona al posto della pagina. Cosi' un tasto su un pop-up
// sfoca la sfocatura del pop-up invece di bucarla e mostrare la pagina piu' nitida di lui.
//
// Chi e' il contenitore lo dice l'albero, non la composizione: il nodo di vetro piu' vicino fra gli
// antenati (`findNearestAncestor`). E' cio' che rende la cosa automatica anche per un'app che si
// costruisce il pannello con `glassSurface` su un suo `Box`: un modificatore non puo' fornire un
// CompositionLocal al contenuto, un nodo si' puo' essere trovato da un nodo figlio.
// ---------------------------------------------------------------------------------------------

/** La chiave che fa trovare un contenitore di vetro ai nodi che gli stanno dentro. */
internal object GlassContainerKey

/**
 * Un nodo sotto cui del vetro puo' appoggiare: un pannello di vetro (`drawBackdrop`), o un nodo che
 * dichiara su quale materiale sta il suo contenuto ([glassContainer]).
 */
internal interface GlassContainer : TraversableNode {

    override val traverseKey: Any get() = GlassContainerKey

    /** Il materiale che questo contenitore pubblica gia', senza crearne uno. Per l'uguaglianza. */
    val publishedMaterial: LayerBackdrop?

    /**
     * Il materiale da campionare per chi sta dentro, creato se serve. Null quando non ce n'e' da
     * dare (nodo staccato, niente `RenderEffect`). Il lettore viene ricordato, cosi' sa quando il
     * contenitore se ne va o cambia materiale.
     */
    fun materialFor(reader: GlassContainerReader): LayerBackdrop?

    /** Il lettore non campiona piu' questo contenitore. */
    fun forgetReader(reader: GlassContainerReader)
}

/** Una superficie di vetro che puo' appoggiare su un contenitore. */
internal interface GlassContainerReader {

    /** Il contenitore e' cambiato (comparso, sparito, ha cambiato materiale): rifai la domanda. */
    fun onContainerChanged()
}

/** Come una superficie compone il proprio fondale con il materiale del contenitore. */
internal sealed interface NestingPlan {

    /** Nessun contenitore, o la superficie ha chiesto di non appoggiarci: il fondale com'e'. */
    data object Alone : NestingPlan

    /** Il fondale contiene gia' il materiale del contenitore (la chrome cablata a mano). */
    data object AlreadyIncluded : NestingPlan

    /** Il materiale va infilato nello strato [index] della pila del fondale. */
    data class Insert(val index: Int) : NestingPlan
}

/**
 * La regola, pura: dove va il materiale del contenitore nella pila di una superficie.
 *
 * - Se il fondale lo contiene gia', niente: la superficie e' stata cablata a mano (la barra della
 *   schermata, la tab bar, la notifica, il contenuto del modale) e il cablaggio vince.
 * - Altrimenti sopra tutto cio' che sta *fuori* dal contenitore (la pagina dietro un pop-up) e
 *   sotto la prima cosa che sta *dentro* (la pista di un interruttore, un corpo registrato dentro
 *   il pannello): fuori e' dietro al pannello, dentro e' davanti.
 *
 * @param layers la pila del fondale, dal fondo in su, gia' appiattita (`stackedBackdrops`).
 * @param isInside se uno strato e' registrato da un nodo che sta dentro il contenitore.
 */
internal fun <T> planNesting(
    layers: List<T>,
    nestable: Boolean,
    hasContainer: Boolean,
    includesMaterial: Boolean,
    isInside: (T) -> Boolean,
): NestingPlan {
    if (!nestable || !hasContainer) return NestingPlan.Alone
    if (includesMaterial) return NestingPlan.AlreadyIncluded
    val firstInside = layers.indexOfFirst(isInside)
    return NestingPlan.Insert(if (firstInside < 0) layers.size else firstInside)
}

/**
 * Il fondale di una superficie che appoggia su un contenitore: la sua pila, con il materiale del
 * contenitore infilato al posto giusto (vedi [planNesting]).
 *
 * Per nodo, mai condiviso: due tasti con lo stesso fondale possono stare su due pannelli diversi.
 */
internal class NestedBackdrop(
    val layers: List<Backdrop>,
    val material: LayerBackdrop,
    val insertAt: Int,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    // Si puo' tenere la cattura solo se ogni strato sa dire di cosa e' fatto, come per una pila
    // qualsiasi; il materiale e' un layer, quindi dice sempre dove sta.
    override val layerSources: List<Any> =
        if (layers.any { it.layerSources.isEmpty() }) {
            emptyList()
        } else {
            layers.flatMap { it.layerSources } + material
        }

    override fun isReadyToSample(): Boolean =
        material.isReadyToSample() && layers.all { it.isReadyToSample() }

    override val stackedBackdrops: List<Backdrop> get() = layers + material

    override fun forEachPart(block: (Backdrop) -> Unit) {
        block(this)
        layers.forEach { it.forEachPart(block) }
        material.forEachPart(block)
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        for (index in 0 until insertAt) {
            with(layers[index]) { drawBackdrop(density, coordinates, layerBlock) }
        }
        with(material) { drawBackdrop(density, coordinates, layerBlock) }
        for (index in insertAt until layers.size) {
            with(layers[index]) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}

/** Se [part] e' uno dei pezzi di questo fondale, a qualsiasi profondita'. */
internal fun Backdrop.containsPart(part: Backdrop): Boolean {
    var found = false
    forEachPart { if (it === part) found = true }
    return found
}

/** Se uno dei layer di questo fondale e' registrato da un nodo dentro [container]. */
internal fun Backdrop.liesInside(container: DelegatableNode): Boolean {
    var inside = false
    forEachPart { part ->
        if (!inside && part is LayerBackdrop) {
            val owner = part.ownerNode
            if (owner != null && owner.isAttached && owner !== container && owner.isInside(container)) {
                inside = true
            }
        }
    }
    return inside
}

private fun Modifier.Node.isInside(container: DelegatableNode): Boolean {
    var found = false
    traverseAncestors(GlassContainerKey) { ancestor ->
        if (ancestor === container) {
            found = true
            false
        } else {
            true
        }
    }
    return found
}

/**
 * Avvisa le superfici dentro questo contenitore che adesso il loro contenitore e' lui.
 *
 * Serve quando il vetro compare *sopra* del vetro che c'era gia' (un modificatore acceso dopo):
 * chi si attacca dopo il contenitore lo trova da solo. Ci si ferma al primo contenitore di ogni
 * ramo, perche' quello che sta sotto di lui appoggia su di lui. Mai un'eccezione da qui: un albero a
 * meta' costruzione non vale un crash.
 */
internal fun GlassContainer.adoptNestedReaders() {
    try {
        traverseDescendants(GlassContainerKey) { node ->
            if (node is GlassContainerReader && node.node.isAttached) node.onContainerChanged()
            TraverseDescendantsAction.SkipSubtreeAndContinueTraversal
        }
    } catch (_: IllegalStateException) {
        // Un ramo non ancora attaccato: quei nodi si chiederanno il contenitore da soli.
    }
}

// ---------------------------------------------------------------------------------------------
// Il contenuto che non sta dentro il proprio vetro
// ---------------------------------------------------------------------------------------------

/**
 * Dice al vetro dentro questo nodo che sta su [material], anche se il nodo non e' figlio della
 * superficie che lo disegna: la finestra di Fluid-physics, il menu' che si trasforma, il pannello
 * dell'app fatto con un altro motore. Vedi `Modifier.glassContainer` in `GlassMaterial.kt`.
 */
internal fun Modifier.glassContainerScope(material: LayerBackdrop): Modifier =
    this then GlassContainerScopeElement(material)

private class GlassContainerScopeElement(
    val material: LayerBackdrop,
) : ModifierNodeElement<GlassContainerScopeNode>() {

    override fun create() = GlassContainerScopeNode(material)

    override fun update(node: GlassContainerScopeNode) {
        if (node.material !== material) {
            node.material = material
            node.notifyReaders()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassContainer"
        properties["material"] = material
    }

    override fun equals(other: Any?): Boolean =
        other is GlassContainerScopeElement && other.material === material

    override fun hashCode(): Int = material.hashCode()
}

private class GlassContainerScopeNode(
    var material: LayerBackdrop,
) : GlassContainer, Modifier.Node() {

    private val readers = ArrayList<GlassContainerReader>(2)

    override val publishedMaterial: LayerBackdrop get() = material

    override fun materialFor(reader: GlassContainerReader): LayerBackdrop? {
        if (!isAttached || !isRenderEffectSupported()) return null
        if (reader !in readers) readers += reader
        return material
    }

    override fun forgetReader(reader: GlassContainerReader) {
        readers -= reader
    }

    fun notifyReaders() {
        // Copia: chi rifa' la domanda si toglie e si rimette nella lista.
        readers.toList().forEach { it.onContainerChanged() }
    }

    override fun onAttach() {
        adoptNestedReaders()
    }

    override fun onDetach() {
        val gone = readers.toList()
        readers.clear()
        gone.forEach { it.onContainerChanged() }
    }
}

/**
 * Registra quello che disegnano i modificatori dopo questo, in [material], **solo** mentre del vetro
 * lo sta campionando. Per il pannello che un'app disegna con un altro motore: va sul nodo che porta
 * il materiale e nient'altro (mai sul contenuto, o il contenuto finirebbe nel proprio fondale).
 */
internal fun Modifier.glassMaterialRecording(material: LayerBackdrop): Modifier =
    this then GlassMaterialRecordingElement(material)

private class GlassMaterialRecordingElement(
    val material: LayerBackdrop,
) : ModifierNodeElement<GlassMaterialRecordingNode>() {

    override fun create() = GlassMaterialRecordingNode(material)

    override fun update(node: GlassMaterialRecordingNode) {
        if (node.material !== material) {
            node.unbind()
            node.material = material
            node.bind()
            node.invalidateDraw()
        }
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "glassMaterialSource"
        properties["material"] = material
    }

    override fun equals(other: Any?): Boolean =
        other is GlassMaterialRecordingElement && other.material === material

    override fun hashCode(): Int = material.hashCode()
}

private class GlassMaterialRecordingNode(
    var material: LayerBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    private val wake: () -> Unit = { if (isAttached) invalidateDraw() }

    fun bind() {
        material.ownerNode = this
        material.onFirstReader = wake
    }

    fun unbind() {
        if (material.ownerNode === this) material.ownerNode = null
        if (material.onFirstReader === wake) material.onFirstReader = null
        material.layerCoordinates = null
    }

    override fun onAttach() {
        bind()
    }

    override fun onDetach() {
        unbind()
    }

    override fun ContentDrawScope.draw() {
        if (!material.wantsRecording) {
            drawContent()
            return
        }
        // Registrato e poi disegnato dal layer: una volta sola, e quello che si vede e' per
        // costruzione quello che il vetro di sopra campiona.
        recordLayer(this@GlassMaterialRecordingNode, material.graphicsLayer) {
            this@draw.drawContent()
        }
        FluidGlassDiagnostics.onExport()
        drawLayer(material.graphicsLayer)
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) material.layerCoordinates = coordinates
    }
}
