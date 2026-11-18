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
import androidx.compose.ui.node.TraversableNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.input.nestedscroll.nestedScrollModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriter
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriterKey
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriters
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerRecordingStack
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerRevisionListener
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.materialLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.checkRecordingParent
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer

/**
 * Marks the container that holds the app's pages, below [ProvideFluidChrome] and **outside** the chrome (tab bar, modal
 * and menu hosts): the chrome's glass samples what these pages draw even when the page in front is not a [FluidScreen].
 *
 * Dalla 4.0 (G1, rilievi C-R1, B-R8, D-R6) [ProvideFluidChrome] non posa più nodi: è solo un fornitore di
 * CompositionLocal, e non cambia il layout di chi ci sta dentro (in F2.3c avvolgeva le pagine in un `Box`, e un
 * `Modifier.weight` o un `matchParentSize` sotto di lui non arrivava più al genitore). Lo scrittore di riserva va
 * posato qui, sul contenitore delle pagine, da chi le monta:
 *
 * ```
 * Box(Modifier.fillMaxSize()) {
 *   ProvideFluidChrome(controller, bottomInset, scrollToTop) {
 *     Box(Modifier.fillMaxSize().fluidChromePages()) { /* la pagina davanti */ }
 *   }
 *   FluidFloatingTabBar(...) // la chrome, fuori dal contenitore
 * }
 * ```
 *
 * Senza, tutto funziona come prima di F2.3c: le [FluidScreen] registrano da sé il fondale che la chrome campiona, e
 * sopra una pagina che non lo registra la chrome campiona un layer vuoto (non gira e non rifrange niente). Un vetro
 * **dentro** il contenitore non chiede mai la riserva: campionerebbe una registrazione che contiene sé stesso. Uno per
 * finestra; non cambia la misura né la posizione di niente, e un layer che nessun vetro della chrome campiona non costa
 * niente.
 */
fun Modifier.fluidChromePages(): Modifier = this then FluidChromePageWriterElement

/**
 * Lo scrittore di riserva della chrome (4.0, F2.3c), posato da [fluidChromePages] sul contenitore delle pagine (fino a
 * G1 lo posava [ProvideFluidChrome] in un suo `Box`).
 *
 * La shell dà alla chrome (la tab bar, i modali, i menu montati sopra le pagine) il fondale della pagina davanti
 * (`activeBackdrop`), e quando la pagina non ne dichiara uno un fondale suo (`fallback`). Se la pagina davanti non è una
 * [FluidScreen] e non registra il fondale da sé, il `fallback` resta vuoto: sul tablet la tab bar della galleria sopra la
 * pagina «Adattività» non girava mai (la sua sonda trovava un layer senza scrittore e non leggeva), e sopra il nero era un
 * grigio medio con le icone scure.
 *
 * La registrazione della pagina appartiene a questo nodo. Le sorgenti dei vetri esterni possono
 * leggerla quando manca un fondale attivo. Restano di proprietà di chi le ha create anche se il
 * loro produttore si stacca durante un pop.
 */
internal class FluidChromePageWriterNode : DelegatingNode(), DrawModifierNode, GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode, TraversableNode, LayerFallbackWriter {

  /** I vetri sotto questo nodo lo trovano risalendo l'albero (`DrawBackdropNode.adoptFallbacks`) e non chiedono la riserva. */
  override val traverseKey: Any get() = LayerFallbackWriterKey

  /** I layer che i vetri fuori dalle pagine campionano, con quanti vetri li campionano. */
  private val adopted = LinkedHashMap<LayerBackdrop, Int>()
  private var pages: LayerBackdrop? = null
  private var coordinates: LayoutCoordinates? = null
  private var root: View? = null

  private val pageRevision = LayerRevisionListener { target, _ ->
    for (source in adopted.keys) if (source.chromeFallback === target) source.bumpRevision()
  }

  /** Lo scorrimento di una pagina dentro la chrome: avvisa i layer che sta registrando. */
  private val scroll = object : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
      if (consumed != Offset.Zero) {
        val now = android.os.SystemClock.uptimeMillis()
        for (layer in adopted.keys) {
          if (layer.chromeFallback == null || layer.chromeFallback !== pages) continue
          layer.scrollMotion.note(now, consumed.y)
          layer.bumpRevision()
        }
      }
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
    retireRecording()
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
    // Una cattura esterna può sopravvivere al distacco, anche se un altro vetro usa la
    // stessa sorgente. Il vecchio layer resta leggibile ma la shell non lo registra di nuovo.
    if (pages != null && layer.chromeFallback === pages) {
      retireRecording()
      if (isAttached) invalidateDraw()
    }
    if (count > 0) {
      adopted[layer] = count
      return
    }
    adopted.remove(layer)
    leave(layer)
  }

  private fun leave(layer: LayerBackdrop) {
    if (layer.chromeFallback === pages) layer.chromeFallback = null
  }

  private fun retireRecording() {
    val target = pages ?: return
    for (source in adopted.keys) leave(source)
    target.removeRevisionListener(pageRevision)
    target.layerCoordinates = null
    requireGraphicsContext().releaseGraphicsLayer(target.graphicsLayer)
    pages = null
  }

  private fun ContentDrawScope.drawPages() {
    drawContent()
    checkRecordingParent(this, this@FluidChromePageWriterNode)
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    pages?.layerCoordinates = coordinates
  }

  override fun ContentDrawScope.draw() {
    if (adopted.isEmpty()) {
      drawPages()
      return
    }
    val active = currentValueOf(LocalFluidChromeController)?.activeBackdrop?.value
    val readers = adopted.keys.filter { source ->
      val needsPages = active == null && source.ownerNode == null && !source.graphicsLayer.isReleased
      if (!needsPages) leave(source)
      needsPages
    }
    if (readers.isEmpty()) {
      retireRecording()
      drawPages()
      return
    }
    val target = pages ?: materialLayerBackdrop(requireGraphicsContext().createGraphicsLayer()).also {
      it.addRevisionListener(pageRevision)
      pages = it
    }
    LayerRecordingStack.enter(target)
    try {
      drawPages()
      if (pages !== target || target.graphicsLayer.isReleased) return
      recordLayer(this@FluidChromePageWriterNode, target.graphicsLayer) { this@draw.drawContent() }
    } finally {
      LayerRecordingStack.exit(target)
    }
    if (target.layerCoordinates !== coordinates) target.layerCoordinates = coordinates
    for (source in readers) source.chromeFallback = target
    target.bumpRevision()
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
