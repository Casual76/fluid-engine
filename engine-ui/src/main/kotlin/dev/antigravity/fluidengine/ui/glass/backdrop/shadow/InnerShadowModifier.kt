/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * Vendored from Kyant0/AndroidLiquidGlass ("backdrop" v2.0.0, io.github.kyant0:backdrop),
 * commit b18eb0ff12c616546a68c72e7d0097f1ab286c87.
 *   https://github.com/Kyant0/AndroidLiquidGlass
 *
 * Changes made by the Fluid Engine (Antigravity), see LICENSES/AndroidLiquidGlass.md:
 *   - the Kotlin Multiplatform expect/actual pairs were merged into this single Android
 *     source set, so `expect`/`actual` are gone and the Android bodies are the only ones;
 *   - the package was renamed com.kyant.backdrop -> dev.antigravity.fluidengine.ui.glass.backdrop;
 *   - support for io.github.kyant0:shapes' RoundedRectangularShape was dropped from the lens
 *     effect, so the vendored sources pull in no dependency the engine does not already have.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.antigravity.fluidengine.ui.glass.backdrop.shadow

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightLayerMaxPx
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.layerResolutionScale
import kotlin.math.ceil
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.ShapeProvider
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.clipOutline
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported

internal class InnerShadowElement(
    val shapeProvider: ShapeProvider,
    val shadow: () -> InnerShadow?
) : ModifierNodeElement<InnerShadowNode>() {

    override fun create(): InnerShadowNode {
        return InnerShadowNode(shapeProvider, shadow)
    }

    override fun update(node: InnerShadowNode) {
        node.shapeProvider = shapeProvider
        node.shadow = shadow
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shapeProvider"] = shapeProvider
        properties["shadow"] = shadow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (shadow != other.shadow) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + shadow.hashCode()
        return result
    }
}

internal class InnerShadowNode(
    var shapeProvider: ShapeProvider,
    var shadow: () -> InnerShadow?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var shadowLayer: GraphicsLayer? = null

    private val paint = Paint()
    private var clipPath: Path? = null

    private var prevRadius = Float.NaN

    // Fluid Engine change: what makes the recorded shadow stale. Upstream re-recorded on every
    // draw — an offscreen layer the size of the pane, re-rasterised and re-blurred per frame,
    // for a shadow whose geometry only changes when the pane's does. A pane's draw re-runs on
    // every frame its screen records itself, so this was charged per visible pane per scrolled
    // frame. `alpha` and `blendMode` are layer properties and never need a re-record.
    private var recordedSize: Size = Size.Unspecified
    private var recordedShape: Any? = null
    private var recordedOffsetX = Float.NaN
    private var recordedOffsetY = Float.NaN
    private var recordedColor: androidx.compose.ui.graphics.Color? = null

    override fun ContentDrawScope.draw() {
        drawContent()

        if (!isRenderEffectSupported()) return

        val shadow = shadow() ?: return

        val shadowLayer = shadowLayer
        if (shadowLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            // See [layerResolutionScale]: this layer holds a blurred outline, and a pane-sized one
            // on a tablet is tens of megabytes of GPU resource for it.
            val resScale = layerResolutionScale(size.width, size.height, HighlightLayerMaxPx)
            val scaledSize = Size(size.width * resScale, size.height * resScale)

            val radius = shadow.radius.toPx() * resScale
            val offsetX = shadow.offset.x.toPx() * resScale
            val offsetY = shadow.offset.y.toPx() * resScale

            // Clip di ogni fotogramma: in hardware. Vedi [ShapeProvider.fastClipShape].
            val outline = shapeProvider.fastClipShape.createOutline(size, layoutDirection, density)
            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            shadowLayer.alpha = shadow.alpha
            shadowLayer.blendMode = shadow.blendMode
            if (prevRadius != radius) {
                shadowLayer.renderEffect =
                    if (radius > 0f) {
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                prevRadius = radius
            }

            val shape = shapeProvider.shape
            val needsRecord = recordedSize != scaledSize ||
                recordedShape !== shape ||
                recordedOffsetX != offsetX ||
                recordedOffsetY != offsetY ||
                recordedColor != shadow.color

            if (needsRecord) {
                // Fluid Engine change: densita' ridotta insieme al layer. Stessa correzione di
                // HighlightModifier, stesso difetto: un raggio in Dp non sa di essere finito in un
                // layer al quaranta per cento, quindi l'anello usciva con l'angolo moltiplicato per
                // l'inverso della riduzione. La riduzione scatta solo oltre i 1024 px di lato lungo,
                // percio' capitava ai pannelli alti e non a quelli corti: due gruppi identici, uno
                // giusto e uno storto.
                val scaledDensity = Density(density.density * resScale, density.fontScale)
                val scaledOutline = shape.createOutline(scaledSize, layoutDirection, scaledDensity)
                val scaledClip =
                    if (scaledOutline is Outline.Rounded) {
                        this@InnerShadowNode.clipPath ?: Path().also { this@InnerShadowNode.clipPath = it }
                    } else {
                        null
                    }
                configurePaint(shadow)
                shadowLayer.record(
                    IntSize(
                        ceil(scaledSize.width).toInt().coerceAtLeast(1),
                        ceil(scaledSize.height).toInt().coerceAtLeast(1)
                    )
                ) {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.clipOutline(scaledOutline, scaledClip)
                    canvas.drawOutline(scaledOutline, paint)
                    canvas.translate(offsetX, offsetY)
                    canvas.drawOutline(scaledOutline, ShadowMaskPaint)
                    canvas.translate(-offsetX, -offsetY)
                    canvas.restore()
                }
                recordedSize = scaledSize
                recordedShape = shape
                recordedOffsetX = offsetX
                recordedOffsetY = offsetY
                recordedColor = shadow.color
            }

            val canvas = drawContext.canvas
            canvas.save()
            canvas.clipOutline(outline, clipPath)
            if (resScale != 1f) {
                scale(1f / resScale, pivot = Offset.Zero) { drawLayer(shadowLayer) }
            } else {
                drawLayer(shadowLayer)
            }
            canvas.restore()
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        // A fresh layer holds nothing: whatever was recorded belongs to the released one.
        recordedSize = Size.Unspecified
        prevRadius = Float.NaN
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        shadowLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            shadowLayer = null
        }
    }

    private fun DrawScope.configurePaint(shadow: InnerShadow) {
        paint.color = shadow.color
    }
}

private val ShadowMaskPaint = Paint().apply {
    blendMode = BlendMode.Clear
}
