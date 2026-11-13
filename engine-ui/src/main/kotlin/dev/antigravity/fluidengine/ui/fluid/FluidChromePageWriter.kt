package dev.antigravity.fluidengine.ui.fluid

import android.view.View
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriter
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriters
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerRecordingStack
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.chromeWritesFallback
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer

/**
 * Lo scrittore di riserva della chrome (4.0, F2.3c), posato da [ProvideFluidChrome] attorno alle pagine.
 *
 * La shell dà alla chrome (la tab bar, i modali, i menu montati sopra le pagine) il fondale della pagina davanti
 * (`activeBackdrop`), e quando la pagina non ne dichiara uno un fondale suo (`fallback`). Se la pagina davanti non è una
 * [FluidScreen] e non registra il fondale da sé, il `fallback` resta vuoto: sul tablet la tab bar della galleria sopra la
 * pagina «Adattività» non girava mai (la sua sonda trovava un layer senza scrittore e non leggeva), e sopra il nero era un
 * grigio medio con le icone scure.
 *
 * Questo nodo registra le pagine **dentro** ogni layer che un vetro **fuori** dalle pagine campiona e che nessuna pagina
 * registra ([chromeWritesFallback]); appena una pagina lo registra da sé (`LayerBackdrop.hasPageWriter`), lascia. A ogni
 * registrazione avvisa ([LayerBackdrop.bumpRevision]), e lo scorrimento delle pagine dentro di lui è un cambio dello
 * sfondo come per ogni sorgente. Un layer che nessuno gli chiede non costa niente: il nodo disegna e basta.
 */
internal class FluidChromePageWriterNode : DelegatingNode(), DrawModifierNode, GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode, LayerFallbackWriter {

  /** I layer che i vetri fuori dalle pagine campionano, con quanti vetri li campionano. */
  private val adopted = LinkedHashMap<LayerBackdrop, Int>()
  private var coordinates: LayoutCoordinates? = null
  private var root: View? = null

  /** Lo scorrimento di una pagina dentro la chrome: avvisa i layer che sta registrando. */
  private val scroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (consumed != Offset.Zero) for (layer in writing()) layer.bumpRevision()
      return Offset.Zero
    }
  }

  init {
    delegate(nestedScrollModifierNode(scroll, null))
  }

  override fun onAttach() {
    val view = currentValueOf(LocalView).rootView
    root = view
    LayerFallbackWriters.register(view, this)
  }

  override fun onDetach() {
    root?.let { LayerFallbackWriters.unregister(it, this) }
    root = null
    for (layer in adopted.keys) leave(layer)
    adopted.clear()
    coordinates = null
  }

  override fun adopt(layer: LayerBackdrop) {
    val count = (adopted[layer] ?: 0) + 1
    adopted[layer] = count
    if (count == 1 && isAttached) invalidateDraw()
  }

  override fun release(layer: LayerBackdrop) {
    val count = (adopted[layer] ?: return) - 1
    if (count > 0) {
      adopted[layer] = count
      return
    }
    adopted.remove(layer)
    leave(layer)
  }

  private fun leave(layer: LayerBackdrop) {
    if (layer.fallbackWriter !== this) return
    layer.fallbackWriter = null
    if (layer.ownerNode == null) layer.layerCoordinates = null
  }

  /** I layer che registra adesso. Legge `hasPageWriter` (stato): quando una pagina arriva o se ne va, ridisegna. */
  private fun writing(): List<LayerBackdrop> {
    if (adopted.isEmpty()) return emptyList()
    val out = ArrayList<LayerBackdrop>(adopted.size)
    for (layer in adopted.keys) {
      // Uno scrittore c'è se una pagina lo registra (`hasPageWriter`, stato: arriva e se ne va) o se è l'export di un
      // pannello (`ownerNode`). Sono su richiesta tutti e due, quindi `onDemand` non li distingue.
      if (chromeWritesFallback(adopted = true, hasPageWriter = layer.hasPageWriter || layer.ownerNode != null)) {
        out += layer
      } else if (layer.fallbackWriter === this) {
        // Una pagina lo registra adesso: lascia (le coordinate le scrive lei).
        layer.fallbackWriter = null
      }
    }
    return out
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    if (!coordinates.isAttached) return
    for (layer in adopted.keys) if (layer.fallbackWriter === this) layer.layerCoordinates = coordinates
  }

  override fun ContentDrawScope.draw() {
    val layers = writing()
    if (layers.isEmpty()) {
      drawContent()
      return
    }
    for (layer in layers) LayerRecordingStack.enter(layer)
    try {
      drawContent()
      for (layer in layers) {
        recordLayer(this@FluidChromePageWriterNode, layer.graphicsLayer) { layer.onDraw(this@draw) }
        layer.fallbackWriter = this@FluidChromePageWriterNode
        coordinates?.let { if (it.isAttached) layer.layerCoordinates = it }
      }
    } finally {
      for (layer in layers) LayerRecordingStack.exit(layer)
    }
    for (layer in layers) layer.bumpRevision()
  }
}

internal object FluidChromePageWriterElement : ModifierNodeElement<FluidChromePageWriterNode>() {
  override fun create(): FluidChromePageWriterNode = FluidChromePageWriterNode()

  override fun update(node: FluidChromePageWriterNode) = Unit

  override fun InspectorInfo.inspectableProperties() {
    name = "fluidChromePageWriter"
  }

  override fun equals(other: Any?): Boolean = other === this

  override fun hashCode(): Int = 7_331
}
