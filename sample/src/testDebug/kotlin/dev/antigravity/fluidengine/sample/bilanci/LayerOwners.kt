@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import android.graphics.RenderNode
import androidx.compose.ui.node.LayoutModifierNodeCoordinator
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.NodeCoordinator

/**
 * F7.29 · Di chi è un layer appena nato: il nodo di layout che lo tiene e il modificatore che l'ha chiesto.
 *
 * La pila di chi crea un layer di Compose si ferma dentro Compose (il layer nasce quando la griglia posa una cella, non
 * quando il codice dell'engine lo chiede), e in F7.28 le note dicevano «nessun chiamante dell'engine nella pila». Qui,
 * dopo il disegno, si gira l'albero: ogni coordinatore che ha un layer col RenderNode appena nato dice il suo modificatore
 * (`GraphicsLayerElement`, il layer dell'elemento della griglia pigra) e la catena del suo nodo. Solo nelle note: la
 * guardia non dipende da questo (i campi interni di Compose cambiano fra le versioni; se non si trovano, la nota lo dice).
 */
internal object LayerOwners {
  /** Il RenderNode dentro un layer di Compose (OwnedLayer → GraphicsLayer → GraphicsLayerImpl → RenderNode). */
  private fun renderNodeOf(obj: Any?, depth: Int = 0): RenderNode? {
    if (obj == null || depth > 3) return null
    if (obj is RenderNode) return obj
    var type: Class<*>? = obj.javaClass
    while (type != null && type != Any::class.java) {
      for (field in type.declaredFields) {
        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) continue
        val t = field.type
        if (!RenderNode::class.java.isAssignableFrom(t) && "GraphicsLayer" !in t.name) continue
        val value = runCatching { field.isAccessible = true; field.get(obj) }.getOrNull() ?: continue
        renderNodeOf(value, depth + 1)?.let { return it }
      }
      type = type.superclass
    }
    return null
  }

  private fun chain(layout: LayoutNode): String {
    val names = ArrayList<String>()
    var node: androidx.compose.ui.Modifier.Node? = layout.nodes.head
    while (node != null) {
      val name = node.javaClass.name.substringAfterLast('.')
      if (name != "TailModifierNode") names += name
      node = node.child
    }
    return names.joinToString(",")
  }

  /**
   * Per ogni RenderNode di [born] trovato in un coordinatore dell'albero di [root]: «modificatore in [catena] sotto
   * [catena del genitore]», contati. Quelli che non stanno in un coordinatore (un `GraphicsLayer` chiesto a mano,
   * un layer del vetro) restano «senza nodo».
   */
  fun describe(root: LayoutNode?, born: Set<Any>): Map<String, Int> {
    if (root == null || born.isEmpty()) return emptyMap()
    val found = LinkedHashMap<String, Int>()
    var matched = 0
    fun visit(layout: LayoutNode, parent: String) {
      var coordinator: NodeCoordinator? = layout.outerCoordinator
      while (coordinator != null) {
        val node = renderNodeOf(coordinator.layer)
        if (node != null && node in born) {
          matched++
          val owner = (coordinator as? LayoutModifierNodeCoordinator)?.layoutModifierNode?.javaClass?.name?.substringAfterLast('.')
            ?: coordinator.javaClass.simpleName
          val key = "$owner in [${chain(layout)}] sotto [$parent]"
          found[key] = (found[key] ?: 0) + 1
        }
        if (coordinator === layout.innerCoordinator) break
        coordinator = coordinator.wrapped
      }
      val here = chain(layout).take(120)
      for (child in layout.children) visit(child, here)
    }
    runCatching { visit(root, "") }
    if (matched < born.size) found["senza nodo (un GraphicsLayer a mano, il vetro, o già tolto)"] = born.size - matched
    return found
  }
}
