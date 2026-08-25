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
package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.HighlightElement
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.ShapeProvider
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadowElement
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.ShadowElement

private val DefaultHighlight = { Highlight.Default }
private val DefaultShadow = { Shadow.Default }
private val DefaultOnDrawBackdrop: DrawScope.(DrawScope.() -> Unit) -> Unit = { it() }

/** Stable identity, so the default never makes an element compare unequal to itself. */
private val OneScale: () -> Float = { 1f }

fun Modifier.drawPlainBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    backdropScale: Float = 1f
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                backdropScale = backdropScale.coerceIn(0.1f, 1f),
                sampleOnce = false,
                backdropScaleFactor = OneScale
            )
        )
}

fun Modifier.drawBackdrop(
    backdrop: Backdrop,
    shape: () -> Shape,
    effects: BackdropEffectScope.() -> Unit,
    highlight: (() -> Highlight?)? = DefaultHighlight,
    shadow: (() -> Shadow?)? = DefaultShadow,
    innerShadow: (() -> InnerShadow?)? = null,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    onDrawBehind: (DrawScope.() -> Unit)? = null,
    onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = DefaultOnDrawBackdrop,
    onDrawSurface: (DrawScope.() -> Unit)? = null,
    onDrawFront: (DrawScope.() -> Unit)? = null,
    /**
     * Fluid Engine addition: record and process the backdrop at this fraction of the surface's own
     * resolution, then draw it back up.
     *
     * The `RenderEffect` chain is the most expensive thing in the design system and it is charged per
     * pixel, so a full-screen scrim at 0.4 costs a sixth of one at 1. What hides the upscaling is the
     * blur, which is why the caller picks the fraction from the radius rather than this having a
     * useful default — see `glassSurface`. Not safe with a `layerBlock`: that block is inverted
     * against this scope's density when the backdrop is sampled, and a scaled density would move it.
     */
    backdropScale: Float = 1f,
    /**
     * Fluid Engine addition: sample the backdrop **once**, and keep that capture while the surface
     * moves.
     *
     * The default behaviour re-captures whenever the surface's position against its sources changes,
     * which while a list scrolls is every frame of every visible pane — and each capture re-runs the
     * effect chain. On a tablet's grouped-list page that alone is hundreds of milliseconds of render
     * thread per frame, and none of it buys anything a person can see when what is being sampled is
     * an ambient wash: soft gradients riding along with the pane and soft gradients fixed to the
     * screen are indistinguishable in motion.
     *
     * Only ever set this for a surface whose backdrop is **static and featureless** — the ambient
     * canvas. Glass over live content (bars over a scrolling body) must keep re-sampling, or it
     * visibly carries a stale copy of the page around.
     */
    sampleOnce: Boolean = false,
    /**
     * Fluid Engine addition: an extra factor on [backdropScale], read per draw, for lowering the
     * whole chain's resolution while the page is moving.
     *
     * The effect chain is charged per pixel, so halving the capture is a quarter of the work — by
     * far the largest lever the material has. It is **quantised** on purpose: a scale that varied
     * continuously would change the recorded size on every frame, and changing the recorded size is
     * the one thing that forces a re-capture. Two steps mean two re-captures per fling instead of
     * sixty.
     */
    backdropScaleFactor: () -> Float = OneScale
): Modifier {
    val shapeProvider = ShapeProvider(shape)
    return this
        .then(
            if (layerBlock != null) {
                Modifier.graphicsLayer(layerBlock)
            } else {
                Modifier
            }
        )
        .then(
            if (innerShadow != null) {
                InnerShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = innerShadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (shadow != null) {
                ShadowElement(
                    shapeProvider = shapeProvider,
                    shadow = shadow
                )
            } else {
                Modifier
            }
        )
        .then(
            if (highlight != null) {
                HighlightElement(
                    shapeProvider = shapeProvider,
                    highlight = highlight
                )
            } else {
                Modifier
            }
        )
        .then(
            DrawBackdropElement(
                backdrop = backdrop,
                shapeProvider = shapeProvider,
                effects = effects,
                layerBlock = layerBlock,
                exportedBackdrop = exportedBackdrop,
                onDrawBehind = onDrawBehind,
                onDrawBackdrop = onDrawBackdrop,
                onDrawSurface = onDrawSurface,
                onDrawFront = onDrawFront,
                backdropScale = if (layerBlock != null) 1f else backdropScale.coerceIn(0.1f, 1f),
                sampleOnce = sampleOnce,
                backdropScaleFactor = backdropScaleFactor
            )
        )
}

private class DrawBackdropElement(
    val backdrop: Backdrop,
    val shapeProvider: ShapeProvider,
    val effects: BackdropEffectScope.() -> Unit,
    val layerBlock: (GraphicsLayerScope.() -> Unit)?,
    val exportedBackdrop: LayerBackdrop?,
    val onDrawBehind: (DrawScope.() -> Unit)?,
    val onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    val onDrawSurface: (DrawScope.() -> Unit)?,
    val onDrawFront: (DrawScope.() -> Unit)?,
    val backdropScale: Float,
    val sampleOnce: Boolean,
    val backdropScaleFactor: () -> Float
) : ModifierNodeElement<DrawBackdropNode>() {

    override fun create(): DrawBackdropNode {
        return DrawBackdropNode(
            backdrop = backdrop,
            shapeProvider = shapeProvider,
            effects = effects,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawBehind = onDrawBehind,
            onDrawBackdrop = onDrawBackdrop,
            onDrawSurface = onDrawSurface,
            onDrawFront = onDrawFront,
            backdropScale = backdropScale,
            sampleOnce = sampleOnce,
            backdropScaleFactor = backdropScaleFactor
        )
    }

    override fun update(node: DrawBackdropNode) {
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.exportedBackdrop?.layerCoordinates = null
            node.exportedBackdrop = exportedBackdrop
        }
        node.onDrawBehind = onDrawBehind
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.backdropScale = backdropScale
        node.sampleOnce = sampleOnce
        node.backdropScaleFactor = backdropScaleFactor
        node.invalidateDrawCache()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "drawBackdrop"
        properties["backdrop"] = backdrop
        properties["shapeProvider"] = shapeProvider
        properties["effects"] = effects
        properties["layerBlock"] = layerBlock
        properties["exportedBackdrop"] = exportedBackdrop
        properties["onDrawBehind"] = onDrawBehind
        properties["onDrawBackdrop"] = onDrawBackdrop
        properties["onDrawSurface"] = onDrawSurface
        properties["onDrawFront"] = onDrawFront
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DrawBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (shapeProvider != other.shapeProvider) return false
        if (effects != other.effects) return false
        if (layerBlock != other.layerBlock) return false
        if (exportedBackdrop != other.exportedBackdrop) return false
        if (onDrawBehind != other.onDrawBehind) return false
        if (onDrawBackdrop != other.onDrawBackdrop) return false
        if (onDrawSurface != other.onDrawSurface) return false
        if (onDrawFront != other.onDrawFront) return false
        if (backdropScale != other.backdropScale) return false
        if (sampleOnce != other.sampleOnce) return false
        if (backdropScaleFactor != other.backdropScaleFactor) return false

        return true
    }

    override fun hashCode(): Int {
        var result = backdrop.hashCode()
        result = 31 * result + shapeProvider.hashCode()
        result = 31 * result + effects.hashCode()
        result = 31 * result + (layerBlock?.hashCode() ?: 0)
        result = 31 * result + (exportedBackdrop?.hashCode() ?: 0)
        result = 31 * result + (onDrawBehind?.hashCode() ?: 0)
        result = 31 * result + onDrawBackdrop.hashCode()
        result = 31 * result + (onDrawSurface?.hashCode() ?: 0)
        result = 31 * result + (onDrawFront?.hashCode() ?: 0)
        result = 31 * result + backdropScale.hashCode()
        result = 31 * result + sampleOnce.hashCode()
        result = 31 * result + backdropScaleFactor.hashCode()
        return result
    }
}

/**
 * Shrinks [requested] until a surface [width] x [height] fits inside one capture.
 *
 * The ceiling is deliberately well under what any current GPU reports. Padding is added to the
 * recording on top of this, the number is not queryable from a draw scope without a GL context, and
 * being conservative costs a quarter of the resolution on a blurred wash — while being wrong
 * costs the whole pane.
 */
internal fun fitToTexture(requested: Float, width: Float, height: Float): Float {
    val longest = maxOf(width, height)
    if (!longest.isFinite() || longest <= 0f) return requested
    val cap = MaxBackdropTextureDimension / longest
    return requested.coerceAtMost(cap).coerceAtLeast(MinBackdropScale)
}

/**
 * Snaps a live quality factor onto the two values a capture is allowed to have.
 *
 * Resolution is the one knob that cannot be animated: it decides the recorded size, and a changed
 * recorded size *is* a re-capture. Continuous would therefore re-run the whole chain on every frame
 * of the very gesture it is trying to make cheaper. Two steps, with the switch well inside the fast
 * range, means one re-capture on the way down and one on the way back.
 */
internal fun quantiseScaleFactor(factor: Float): Float {
    if (!factor.isFinite()) return 1f
    return if (factor < ReducedScaleThreshold) ReducedScaleFactor else 1f
}

/** Below this quality level the capture drops to [ReducedScaleFactor]. */
internal const val ReducedScaleThreshold = 0.7f

/** Half the linear resolution: a quarter of the pixels through the effect chain. */
internal const val ReducedScaleFactor = 0.5f

/** Pixels. Half of the 8192 that has been the floor of the Android hardware requirement for years. */
internal const val MaxBackdropTextureDimension = 4096f

/** Below this the capture is too coarse to be worth running the chain over at all. */
internal const val MinBackdropScale = 0.05f

private class DrawBackdropNode(
    var backdrop: Backdrop,
    var shapeProvider: ShapeProvider,
    var effects: BackdropEffectScope.() -> Unit,
    var layerBlock: (GraphicsLayerScope.() -> Unit)?,
    var exportedBackdrop: LayerBackdrop?,
    var onDrawBehind: (DrawScope.() -> Unit)?,
    var onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    var onDrawSurface: (DrawScope.() -> Unit)?,
    var onDrawFront: (DrawScope.() -> Unit)?,
    var backdropScale: Float,
    var sampleOnce: Boolean,
    var backdropScaleFactor: () -> Float
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode, Modifier.Node() {

    private val effectScope =
        object : BackdropEffectScopeImpl() {

            override val shape: Shape get() = shapeProvider.innerShape
        }

    private var graphicsLayer: GraphicsLayer? = null

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = true
        // Fluid Engine change: the *layer* is clipped by the shape's rounded-rect reading, not by
        // its exact path. A `RenderNode` clipped to a generic path cannot use the hardware's
        // rounded-rect clip and falls back to masking the whole node offscreen — per pane, per
        // frame. What the eye actually reads as the corner is the tint and the specular edge drawn
        // on the surface, and those keep the exact continuous silhouette; the layer clip only has
        // to stop the refracted image spilling, which a rounded rect at the same radii does to
        // within a fraction of a pixel.
        shape = shapeProvider.fastClipShape
        // Fluid Engine change: upstream forces `CompositingStrategy.Offscreen` here. That routes the
        // whole surface — backdrop draw, tint, and every child — through a texture the size of the
        // node, re-rasterised on every frame in which the refracted image changes... which while the
        // page scrolls is every frame, of every pane, text and all. On a tablet's grouped-list page
        // that texture traffic alone took the frame time from 18 ms to 85 ms; it is also a second
        // way for an oversized pane to hit the GPU texture ceiling and lose its content.
        //
        // The group buys nothing optically: the stack this node draws (backdrop layer, tint,
        // content) is plain `SrcOver`, which composites identically with or without isolation, the
        // clip above antialiases on its own, and `Auto` still promotes to a layer in the one case
        // that genuinely needs one — an animated `alpha` on the pane.
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Auto
    }

    private var layoutCoordinates: LayoutCoordinates? by mutableStateOf(null, neverEqualPolicy())

    private var padding by mutableFloatStateOf(0f)

    /**
     * Fluid Engine addition: whether the recorded capture is stale for a reason of *this* surface's
     * own — an element update, a retuned effect chain, a fresh attach.
     *
     * See [Backdrop.layerSources] for why anything else does not count. Before this, every glass
     * surface replayed the whole screen and re-ran its `RenderEffect` chain on every single frame it
     * drew, whether or not one pixel of the situation had changed, and eight surfaces on a screen
     * meant eight of those per frame. It is the difference between a tab switch that stutters and one
     * that does not.
     */
    private var surfaceDirty = true

    private var recordedSize: IntSize = IntSize.Zero
    private var recordedSelfOffset: Offset? = null
    private var recordedSourceOffsets: List<Offset>? = null

    /**
     * Where every source sits relative to this surface, which is the only thing a capture depends on.
     *
     * Null means at least one source has not been positioned yet, or the backdrop cannot say what it
     * is made of — either way the answer is to record, which is what every backdrop did before.
     */
    private fun currentSourceOffsets(): List<Offset>? {
        val sources = backdrop.layerSources
        if (sources.isEmpty()) return null
        val self = layoutCoordinates ?: return null
        if (!self.isAttached) return null
        val offsets = ArrayList<Offset>(sources.size)
        for (source in sources) {
            val layer = source as? LayerBackdrop ?: return null
            val sourceCoordinates = layer.layerCoordinates ?: return null
            if (!sourceCoordinates.isAttached) return null
            offsets += try {
                sourceCoordinates.localPositionOf(self)
            } catch (_: Exception) {
                return null
            }
        }
        return offsets
    }

    /**
     * The scale this frame actually records at, which is [backdropScale] unless the surface is too
     * big to capture.
     *
     * A capture is a GPU texture, and a texture has a hard ceiling — a few thousand pixels on
     * each side, device by device. Past it the record does not fail loudly: it comes back empty,
     * and an empty capture under a translucent tint is a **black rectangle** where the content was.
     * A grouped list is exactly the surface that reaches it, because a group is as tall as the
     * number of rows someone happens to have.
     *
     * Recording smaller is the one response that keeps the material. What such a pane is standing
     * over is an ambient wash, which is soft by construction and has nothing in it that a quarter of
     * the resolution can lose.
     */
    private var effectiveScale: Float = 1f

    private val recordBackdropBlock: (DrawScope.() -> Unit) = {
        val canvas = drawContext.canvas
        val padding = padding
        val scale = effectiveScale

        if (padding != 0f) {
            canvas.translate(padding, padding)
        }
        // Everything after this point is drawn into a layer that covers fewer pixels, so the effect
        // chain does too. Both the padding above and the effect parameters are already expressed in
        // this smaller world, because the effect scope was handed a scaled density.
        if (scale != 1f) {
            canvas.scale(scale, scale)
        }
        onDrawBackdrop {
            with(backdrop) {
                drawBackdrop(
                    density = effectScope,
                    coordinates = layoutCoordinates,
                    layerBlock = layerBlock
                )
            }
        }
        if (scale != 1f) {
            canvas.scale(1f / scale, 1f / scale)
        }
        if (padding != 0f) {
            canvas.translate(-padding, -padding)
        }
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        val layer = graphicsLayer
        if (layer != null) {
            val padding = padding
            // Not named `scale`: that shadows the DrawScope transform of the same name, which is
            // what un-scales the draw below.
            val contentScale = effectiveScale
            val recordSize = IntSize(
                ((size.width * contentScale).toInt() + padding.toInt() * 2).coerceAtLeast(1),
                ((size.height * contentScale).toInt() + padding.toInt() * 2).coerceAtLeast(1)
            )
            val sourceOffsets = currentSourceOffsets()
            val selfOffset = layoutCoordinates?.takeIf { it.isAttached }?.positionInRoot()

            // Re-record only when this capture can no longer be the right one. A `RenderNode`
            // display list holds its children by reference, so a source that re-records is picked up
            // by every surface already replaying it — what a surface cannot survive is *moving*.
            //
            // `sourceOffsets == null` is the honest "cannot tell" and keeps the original
            // record-every-frame behaviour for backdrops that are not made of recordings.
            //
            // Unless the surface asked to [sampleOnce]: then movement is deliberately not a reason —
            // its backdrop is a static wash, and a wash riding along with the pane is
            // indistinguishable from one fixed to the screen. See `drawBackdrop`.
            val needsRecord = surfaceDirty ||
                recordedSize != recordSize ||
                if (sampleOnce) {
                    false
                } else {
                    sourceOffsets == null ||
                        recordedSourceOffsets != sourceOffsets ||
                        recordedSelfOffset != selfOffset
                }

            if (needsRecord) {
                recordLayer(
                    this@DrawBackdropNode,
                    layer,
                    size = recordSize,
                    block = recordBackdropBlock
                )
                recordedSize = recordSize
                recordedSourceOffsets = sourceOffsets
                recordedSelfOffset = selfOffset
                surfaceDirty = false
            }

            layer.topLeft =
                if (padding != 0f) IntOffset(-padding.toInt(), -padding.toInt())
                else IntOffset.Zero
            // `topLeft` is in the layer's own (scaled) coordinates, so the un-scale has to wrap the
            // placement as well as the draw.
            if (contentScale != 1f) {
                scale(1f / contentScale, pivot = Offset.Zero) { drawLayer(layer) }
            } else {
                drawLayer(layer)
            }
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = layoutLayerBlock)
        }
    }

    override fun ContentDrawScope.draw() {
        effectiveScale = fitToTexture(
            backdropScale * quantiseScaleFactor(backdropScaleFactor()),
            size.width,
            size.height
        )
        if (effectScope.update(this, effectiveScale)) {
            updateEffects()
        }

        onDrawBehind?.invoke(this)
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        drawContent()
        onDrawFront?.invoke(this)

        exportedBackdrop?.graphicsLayer?.let { layer ->
            recordLayer(this@DrawBackdropNode, layer) {
                onDrawBehind?.invoke(this)
                drawBackdropLayer()
                onDrawSurface?.invoke(this)
                onDrawFront?.invoke(this)
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            if (backdrop.isCoordinatesDependent) {
                layoutCoordinates = coordinates
            } else {
                if (layoutCoordinates != null) {
                    layoutCoordinates = null
                }
            }
            exportedBackdrop?.layerCoordinates = coordinates
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        observeReads { updateEffects() }
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return

        effectScope.apply(effects)
        graphicsLayer?.renderEffect = effectScope.renderEffect
        val newPadding = effectScope.padding
        // Only the *padding* invalidates a capture. The `RenderEffect` itself is applied to the layer
        // at draw time, so a chain that changes its numbers — a bar's blur ramping up with a scroll,
        // a modal's lens thickening as it arrives — costs nothing beyond rebuilding the effect.
        //
        // Dirtying the capture here unconditionally, which is what this did, meant every frame of
        // every one of those animations re-recorded the whole screen and re-ran the entire chain
        // over it. On a full-screen scrim that is the single most expensive thing in the design
        // system, happening at the exact moment something is trying to animate: it is why holding a
        // button to open its menu stuttered.
        if (newPadding != padding) {
            padding = newPadding
            surfaceDirty = true
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()
        surfaceDirty = true
        recordedSize = IntSize.Zero
        recordedSourceOffsets = null
        recordedSelfOffset = null

        observeEffects()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        effectScope.reset()
        layoutCoordinates = null
        exportedBackdrop?.layerCoordinates = null
    }
}
