package dev.antigravity.fluidengine.ui.glass.backdrop.internal

import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.collection.ScatterSet
import java.util.ArrayDeque
import java.util.IdentityHashMap

internal fun checkRecordingParent(scope: DrawScope, node: DelegatableNode) {
    scope.drawContext.graphicsLayer?.let { checkRecordedGraph(it, node) }
}

/** Dopo record Compose conosce anche i figli trattenuti da pose e catture precedenti.
 * Un ciclo viene segnalato sul filo UI, con lo stack della registrazione che lo ha reso visibile.
 */
internal fun checkRecordedGraph(layer: GraphicsLayer, node: DelegatableNode) {
    val cycle = findLayerCycle(layer, ComposeDependencies::children) ?: return
    error("FluidLayerGraph: " + node.javaClass.simpleName + " ha registrato il ciclo " +
        cycle.joinToString(" -> ") { Integer.toHexString(System.identityHashCode(it)) })
}

/** Solo nella variante debug. Un cambio di ABI di Compose deve rendere visibile la perdita del controllo. */
internal object ComposeDependencies {
    private val tracker = GraphicsLayer::class.java.getDeclaredField("childDependenciesTracker").apply { isAccessible = true }
    private val single = tracker.type.getDeclaredField("dependency").apply { isAccessible = true }
    private val many = tracker.type.getDeclaredField("dependenciesSet").apply { isAccessible = true }

    fun children(layer: GraphicsLayer): List<GraphicsLayer> {
        val dependencies = tracker.get(layer)
        val one = single.get(dependencies) as? GraphicsLayer
        val more = many.get(dependencies) as? ScatterSet<*>
        if (more == null || more.isEmpty()) return listOfNotNull(one)
        return buildList {
            if (one != null) add(one)
            more.forEach { if (it is GraphicsLayer) add(it) }
        }
    }
}

/** Il percorso attivo distingue un antenato da un figlio condiviso fra due layer. La pila esplicita
 * permette di ispezionare anche una gerarchia profonda senza ricorsione sul thread di disegno.
 */
internal fun <T : Any> findLayerCycle(root: T, children: (T) -> List<T>): List<T>? {
    val visited = IdentityHashMap<T, Boolean>()
    val active = IdentityHashMap<T, Int>()
    val path = ArrayList<T>()
    val pending = ArrayDeque<Iterator<T>>()
    fun enter(layer: T) {
        active[layer] = path.size
        visited[layer] = true
        path.add(layer)
        pending.addLast(children(layer).iterator())
    }
    enter(root)
    while (pending.isNotEmpty()) {
        val siblings = pending.last
        if (!siblings.hasNext()) {
            pending.removeLast()
            active.remove(path.removeAt(path.lastIndex))
            continue
        }
        val child = siblings.next()
        active[child]?.let { return path.subList(it, path.size) + child }
        if (child !in visited) enter(child)
    }
    return null
}
