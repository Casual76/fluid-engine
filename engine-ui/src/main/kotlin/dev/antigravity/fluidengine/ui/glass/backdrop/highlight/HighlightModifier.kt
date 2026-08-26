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
package dev.antigravity.fluidengine.ui.glass.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.ShapeProvider
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.clipOutline
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.setRuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

/**
 * Fluid Engine addition: the fraction of its own resolution an auxiliary layer is recorded at.
 *
 * These layers hold blurred strokes and shadows, never content, so what they cost is measured in
 * megabytes of GPU resource rather than in detail. A pane the size of a tablet's grouped list is
 * twenty megabytes per auxiliary layer at full resolution, and a screenful of panes exhausts HWUI's
 * resource budget — at which point the cache thrashes and *every* small texture on screen is
 * re-uploaded per frame. Capping the long edge bounds that, and a blurred edge cannot show it.
 */
internal fun layerResolutionScale(width: Float, height: Float, maxPx: Float): Float {
    val longest = maxOf(width, height)
    if (!longest.isFinite() || longest <= maxPx) return 1f
    return (maxPx / longest).coerceAtLeast(0.1f)
}

/** Long edge, in device pixels, past which a highlight ring is recorded smaller. */
internal const val HighlightLayerMaxPx = 1024f

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    private var prevStyle: HighlightStyle? = null

    // Fluid Engine change: what makes the recorded ring stale. Upstream re-recorded the layer on
    // every draw, and the ring is drawn with a `BlurMaskFilter` — a mask the size of the pane,
    // blurred on the CPU and uploaded again. That is invisible on a static screen and ruinous on a
    // scrolling one: a pane's draw re-runs on every frame its screen records itself (the glass
    // pipeline does exactly that), so every visible pane re-blurred a multi-megapixel mask per
    // frame. The ring only actually changes with the geometry and the stroke; `alpha` — the one
    // thing that animates — is a layer property and needs no re-record at all.
    private var recordedSize: IntSize = IntSize.Zero
    private var recordedShape: Any? = null
    private var recordedWidthPx: Float = Float.NaN
    private var recordedBlurPx: Float = Float.NaN
    private var recordedStyle: HighlightStyle? = null

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            // Fluid Engine change: the ring's layer is area-capped. A grouped-list pane recorded a
            // full-resolution texture of itself to hold a hairline ring, twenty megabytes a pane on
            // a tablet — and a screenful of panes blew straight through HWUI's resource budget,
            // which then evicted and re-uploaded every small texture on every frame. The ring is a
            // blurred stroke: rendered at a fraction of the pane and drawn back up, the difference
            // sits inside its own blur.
            val resScale = layerResolutionScale(size.width, size.height, HighlightLayerMaxPx)
            val scaledSize = Size(size.width * resScale, size.height * resScale)
            val safeSize =
                IntSize(
                    ceil(scaledSize.width).toInt() + 2,
                    ceil(scaledSize.height).toInt() + 2
                )

            val widthPx = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f * resScale
            val blurPx = highlight.blurRadius.toPx() * resScale
            val shape = shapeProvider.shape
            val needsRecord = recordedSize != safeSize ||
                recordedShape !== shape ||
                recordedWidthPx != widthPx ||
                recordedBlurPx != blurPx ||
                recordedStyle != highlight.style

            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = highlight.style.blendMode

            if (needsRecord) {
                val outline = shape.createOutline(scaledSize, layoutDirection, density)
                val clipPath =
                    if (outline is Outline.Rounded) {
                        clipPath ?: Path().also { clipPath = it }
                    } else {
                        null
                    }

                configurePaint(highlight, widthPx, blurPx)

                highlightLayer.record(safeSize) {
                    translate(1f, 1f) {
                        val canvas = drawContext.canvas
                        canvas.save()
                        canvas.clipOutline(outline, clipPath)
                        canvas.drawOutline(outline, paint)
                        canvas.restore()
                    }
                }
                recordedSize = safeSize
                recordedShape = shape
                recordedWidthPx = widthPx
                recordedBlurPx = blurPx
                recordedStyle = highlight.style
            }

            if (resScale != 1f) {
                scale(1f / resScale, pivot = Offset.Zero) {
                    translate(-1f, -1f) {
                        drawLayer(highlightLayer)
                    }
                }
            } else {
                translate(-1f, -1f) {
                    drawLayer(highlightLayer)
                }
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer = graphicsContext.createGraphicsLayer()
        // A fresh layer holds nothing: whatever was recorded belongs to the released one.
        recordedSize = IntSize.Zero
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
        }
        clipPath = null
        runtimeShaderCache.clear()
        prevStyle = null
    }

    private fun DrawScope.configurePaint(highlight: Highlight, strokeWidthPx: Float, blurPx: Float) {
        paint.color = highlight.style.color
        paint.strokeWidth = strokeWidthPx
        paint.blur(blurPx)
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        // Fluid Engine change: la sagoma VERA, non l'involucro che la memorizza.
                        //
                        // `shapeProvider.shape` e' un `object : Shape` anonimo che tiene in cache
                        // l'outline. Lo shader del riflesso pero' non gli chiede un outline: gli
                        // chiede i RAGGI, con `shape as? CornerBasedShape ?: FloatArray(4) { maxRadius }`.
                        // L'involucro non e' una CornerBasedShape, quindi si finiva sempre nel ramo
                        // di riserva e il riflesso disegnava una capsula: raggio = meta' del lato
                        // corto, su tutti e quattro gli angoli, qualunque forma avesse il pannello.
                        //
                        // Su un controllo non si vede, ed e' per questo che e' passato: una capsula
                        // alta 44 px ha meta' lato corto 22, cioe' esattamente il suo raggio, e il
                        // ramo di riserva indovina la risposta giusta. Su un gruppo lista largo
                        // mille pixel indovina 498 invece di 58, e quello che si vede e' un arco
                        // chiaro che taglia il pannello a mezza altezza e non coincide con niente.
                        // "Solo in alcuni casi" era esattamente questo: i casi sono le taglie.
                        shape = shapeProvider.innerShape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
