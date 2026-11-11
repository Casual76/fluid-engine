/*
 * Il fondo pieno di un pannello OPACO dell'engine che ospita vetro (4.0.0, F1A.13): la notifica quando l'host non ha
 * un fondo, il foglio del modale quando non ha un fondo. Lo disegna e, finché del vetro dentro lo campiona, lo
 * pubblica come il materiale di quel pannello.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.resolveFluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer

/**
 * Vero se un pannello opaco deve pubblicare il proprio fondo per i controlli di vetro che ospita: quando un
 * controllo di vetro di serie (una capsula con la lente, sul fondo uniforme) si disegnerebbe col materiale nuovo.
 *
 * Perché solo allora: col vetro di prima un controllo su un pannello opaco ha sempre avuto il fondo vuoto (la sua
 * pellicola sopra il niente), ed è il vetro che non deve cambiare di un pixel. Col materiale nuovo il fondo vuoto
 * dà un velo pieno sopra il pannello (la toppa grigia) e nessuna regola dell'annidato: il tasto non sa di stare su
 * un pannello. Pura: la tabella di verità è quella di `resolveFluidGlassModel`.
 */
internal fun glassOpaquePanelPublishes(sdk: Int, shadersHealthy: () -> Boolean, requested: FluidGlassModel): Boolean =
  resolveFluidGlassModel(
    sdk = sdk,
    shadersHealthy = shadersHealthy,
    requested = requested,
    falloff = GlassFalloff.Uniform,
    genericShape = false,
    flat = false,
  ) == FluidGlassModel.Liquid

/**
 * Il fondo pieno [color] nella sagoma [shape], al posto di `background(color, shape)`, e lo stesso fondo come
 * **materiale** [material] del pannello, per il vetro che ci sta dentro. Senza [material] è un `background`.
 *
 * Perché un pannello opaco ha un materiale: un tasto di vetro dentro una notifica o un foglio pieni sta su quel
 * pannello come su un pannello di vetro, e la regola dell'annidato (velo a un terzo sul tema scuro, ombra a metà)
 * vale per lui allo stesso modo. Senza un materiale il tasto campionava il fondo vuoto: niente corpo con la
 * smerigliatura di serie, il velo pieno sopra il niente con una più larga (F1A.13, misurato sul Tab S9).
 *
 * Va sul nodo del pannello, e il contenuto con `glassContainer(material)`: il materiale porta **solo** il fondo,
 * mai il contenuto (un tasto che campiona se stesso è il feedback ottico). Come `glassMaterialSource`, registra solo
 * finché qualcuno lo campiona (`LayerBackdrop.wantsRecording`), e si disegna dal layer registrato: quello che si
 * vede è per costruzione quello che il vetro di sopra campiona.
 */
internal fun Modifier.glassOpaquePanel(material: LayerBackdrop?, color: Color, shape: Shape): Modifier =
  this then GlassOpaquePanelElement(material, color, shape)

private class GlassOpaquePanelElement(
  val material: LayerBackdrop?,
  val color: Color,
  val shape: Shape,
) : ModifierNodeElement<GlassOpaquePanelNode>() {

  override fun create() = GlassOpaquePanelNode(material, color, shape)

  override fun update(node: GlassOpaquePanelNode) {
    if (node.material !== material) {
      node.unbind()
      node.material = material
      node.bind()
    }
    node.color = color
    node.shape = shape
    node.invalidateDraw()
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "glassOpaquePanel"
    properties["material"] = material
    properties["color"] = color
    properties["shape"] = shape
  }

  override fun equals(other: Any?): Boolean =
    other is GlassOpaquePanelElement && other.material === material && other.color == color && other.shape == shape

  override fun hashCode(): Int {
    var result = material?.hashCode() ?: 0
    result = 31 * result + color.hashCode()
    result = 31 * result + shape.hashCode()
    return result
  }
}

private class GlassOpaquePanelNode(
  var material: LayerBackdrop?,
  var color: Color,
  var shape: Shape,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

  private val wake: () -> Unit = { if (isAttached) invalidateDraw() }

  fun bind() {
    val layer = material ?: return
    if (!isAttached) return
    layer.ownerNode = this
    layer.onFirstReader = wake
  }

  fun unbind() {
    val layer = material ?: return
    if (layer.ownerNode === this) layer.ownerNode = null
    if (layer.onFirstReader === wake) layer.onFirstReader = null
    layer.layerCoordinates = null
  }

  override fun onAttach() {
    bind()
  }

  override fun onDetach() {
    unbind()
  }

  private fun DrawScope.drawFill() {
    drawOutline(shape.createOutline(size, layoutDirection, this), color)
  }

  override fun ContentDrawScope.draw() {
    val layer = material
    if (layer != null && layer.wantsRecording) {
      recordLayer(this@GlassOpaquePanelNode, layer.graphicsLayer) { drawFill() }
      FluidGlassDiagnostics.onExport()
      drawLayer(layer.graphicsLayer)
    } else {
      drawFill()
    }
    drawContent()
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    if (coordinates.isAttached) material?.layerCoordinates = coordinates
  }
}
