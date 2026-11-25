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

import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.foundation.memory.fluidSurfaceBytes
import dev.antigravity.fluidengine.ui.fluid.GlassRenderQuality
import dev.antigravity.fluidengine.ui.fluid.LocalCoveredGlassView
import dev.antigravity.fluidengine.ui.fluid.LocalGlassFrontLayer
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.glassBackgroundScale
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassRenderState
import dev.antigravity.fluidengine.ui.glass.quality.android.glassWindowFor
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyFallback
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyFallbackScope
import dev.antigravity.fluidengine.ui.glass.field.NativeGlassFinish
import dev.antigravity.fluidengine.ui.glass.field.GlassTravelCapture
import dev.antigravity.fluidengine.ui.page.LocalFluidPageTransition
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.field.GlassLightDraw
import dev.antigravity.fluidengine.ui.glass.field.GlassLightEnvironment
import dev.antigravity.fluidengine.ui.glass.field.LocalFluidGlassMotionLight
import dev.antigravity.fluidengine.ui.glass.field.LocalGlassLightEnvironment

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import android.graphics.RenderEffect as AndroidEffect
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
// Fluid Engine addition (F1A, voce 18): lo scope degli effetti dice se la superficie sta su un vetro.
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.isSpecified
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
import androidx.compose.ui.node.findNearestAncestor
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.node.requireLayoutDirection
import androidx.compose.ui.node.requireView
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
// Fluid Engine addition (F1A.7): il materiale di una superficie, per la diagnostica.
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriter
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriters
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerFallbackWriterKey
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.materialLayerBackdrop
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

/**
 * Fluid Engine addition (4.0.0, F1A; voce 18 di `LICENSES/AndroidLiquidGlass.md`): lo scope degli effetti di un
 * nodo del vetro sa se la superficie sta **dentro** un'altra superficie di vetro e ne campiona il materiale.
 *
 * E' la risposta che il nodo gia' si da' per il vetro su vetro (2.13.0, [planNesting]): chi lo contiene lo dice
 * l'albero e non la composizione, perche' un modificatore non puo' fornire un `CompositionLocal` al contenuto.
 * Il materiale nuovo (`glass/field/GlassFieldEffects.kt`) la legge per non posare il proprio velo pieno sopra un
 * materiale che ne ha gia' uno. Si legge con un cast (`this as? GlassNestingScope`): lo scope pubblico
 * ([BackdropEffectScope]) non cambia, e chi non lo chiede non vede niente.
 */
internal interface GlassNestingScope {

    /**
     * Vero se la superficie campiona il materiale di un pannello di vetro che la contiene: infilato nella sua pila
     * ([NestingPlan.Insert]) o gia' cablato a mano nel suo fondale ([NestingPlan.AlreadyIncluded]: la chiusura
     * della notifica, il contenuto del modale). Falso sulla pagina, accanto a un altro vetro (due tasti di una
     * barra, la capsula sopra la pagina), e per chi ha chiesto di non appoggiarsi (`nestInContainer` falso).
     */
    val standsOnGlass: Boolean
}

/**
 * La regola, pura: dal piano del vetro su vetro alla risposta di [GlassNestingScope.standsOnGlass]. [inserted] e'
 * vero se il materiale del contenitore e' davvero entrato nella pila (un contenitore che non ne ha da dare lascia
 * il fondale com'e', e allora la superficie non sta su niente). Fluid Engine addition (F1A, voce 18).
 */
internal fun glassStandsOnGlass(plan: NestingPlan, inserted: Boolean): Boolean = when (plan) {
    NestingPlan.Alone -> false
    NestingPlan.AlreadyIncluded -> true
    is NestingPlan.Insert -> inserted
}

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
    backdropScale: Float = 1f,
    /** See [drawBackdrop]. Since 2.13.0. */
    nestInContainer: Boolean = true,
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
                resampleIntervalMillis = 0L,
                frozen = NeverFrozen,
                heldWhileMoving = NeverFrozen,
                backdropScaleFactor = OneScale,
                nestInContainer = nestInContainer
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
    resampleIntervalMillis: Long = 0L,
    /**
     * Fluid Engine addition: an extra factor on [backdropScale], read per draw, for lowering the
     * whole chain's resolution while the page is moving.
     *
     * The effect chain is charged per pixel, so halving the capture is a quarter of the work — by
     * far the largest lever the material has. It is **quantised** on purpose: a scale that varied
     * continuously would change the recorded size on every frame, and changing the recorded size is
     * the one thing that forces a re-capture. The adaptive factor uses the pixel cost and a short history
     * of draws, with separate entry and recovery thresholds.
     */
    backdropScaleFactor: () -> Float = OneScale,
    /**
     * While this returns true the surface holds the capture it has instead of taking a new one.
     *
     * Not the same thing as [sampleOnce], which is about a backdrop that does not move. This is
     * about a surface whose *own* bounds are being animated: a bar folding, a pane growing into a
     * window. Every frame of that changes the capture rectangle, so the ordinary rule — re-record
     * when the geometry moved — asks for a full replay of the screen plus the whole effect chain on
     * every single frame, at exactly the moment there is least to spare. Measured on the bar this
     * came from: 9 frames of 250-450 ms, against 77 frames of 42 ms with the hold.
     *
     * Held, but not indefinitely. A capture frozen for a whole animation is a reflection that has to
     * be corrected at the end of it, and that correction is a visible jump however it is dressed up.
     * So a held surface still refreshes a few times a second: three or four recordings across a fold
     * instead of one per frame, and nothing left at the end big enough to see.
     */
    frozen: () -> Boolean = NeverFrozen,
    /**
     * Hold the capture even while this surface is moving.
     *
     * [frozen] on its own holds only while the surface is where it was when it took the capture: a
     * pane that has moved and kept its old recording is reflecting somewhere else on the screen, and
     * that reads as the glass showing the wrong part of the page. For a surface in the middle of
     * changing *shape* it is worth it anyway — a reflection that lags for the length of a morph is
     * not something anyone can see, and the alternative, taking the glass away and putting it back,
     * is.
     */
    heldWhileMoving: () -> Boolean = NeverFrozen,
    /**
     * Fluid Engine addition (2.13.0): vetro su vetro.
     *
     * Dentro un altro pannello di vetro (il nodo di vetro piu' vicino fra gli antenati, o un nodo
     * marcato con `glassContainer`) la superficie campiona il *materiale* di quel pannello — il suo
     * fondale sfocato, rifratto e tinto — infilato nella propria pila sopra a tutto cio' che sta
     * fuori dal pannello e sotto a cio' che sta dentro. Un tasto su un pop-up sfoca cosi' la
     * sfocatura del pop-up invece di bucarla. Se [backdrop] contiene gia' il materiale di quel
     * pannello (un cablaggio fatto a mano con `exports`) non cambia niente.
     *
     * False per una superficie che ricostruisce il materiale del pannello da sola e lo sa: il puck
     * della barra flottante, che sfoca come la barra partendo dalla pagina cruda.
     */
    nestInContainer: Boolean = true,
    /**
     * Fluid Engine addition (4.0.0, F1A.6): la **luce** della superficie, disegnata dopo [onDrawSurface] e
     * prima del contenuto, e **fuori dal materiale esportato**.
     *
     * E' il posto del filo, dei riflessi e del bagliore del dito del materiale `Liquid`
     * (`glass/field/GlassLightShader.kt`). Sta fuori dall'export per due ragioni: il vetro annidato deve
     * campionare il corpo del pannello che lo ospita e non il suo bordo ne' il suo bagliore (vetro su
     * vetro, 2.13.0), e un bagliore che cambia a ogni fotogramma di una pressione renderebbe vecchio
     * l'export a ogni fotogramma, cioe' rifarebbe la catena di ogni vetro che sta dentro. Uno stato letto
     * qui ridisegna la superficie e non registra di nuovo il materiale pubblicato.
     */
    onDrawLight: (DrawScope.() -> Unit)? = null,
    /**
     * Fluid Engine addition (4.0.0, F1A.6): modificatori posati **dentro** la trasformazione di
     * [layerBlock] e **fuori** dal ritaglio della superficie, prima del nodo: lo stesso posto in cui
     * stanno l'ombra e il riflesso di sempre.
     *
     * Serve all'ombra del materiale `Liquid` (`Modifier.glassShadow` di `glass/field/GlassShadow.kt`), che
     * non e' una [Shadow]: messa prima di `drawBackdrop` resterebbe fuori dal layer di [layerBlock], e un
     * tasto che cresce sotto il dito lascerebbe la propria ombra dov'era.
     */
    underlay: Modifier = Modifier,
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
        .then(underlay)
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
                resampleIntervalMillis = resampleIntervalMillis,
                backdropScaleFactor = backdropScaleFactor,
                frozen = frozen,
                heldWhileMoving = heldWhileMoving,
                nestInContainer = nestInContainer,
                onDrawLight = onDrawLight
            )
        )
}

/** Primo pixel utile della striscia inferiore esportata, letto dopo l'aggiornamento degli uniform. */
internal open class ScrollEdgeBackdropEffects(
    effects: BackdropEffectScope.() -> Unit,
    val preservesRoleScale: Boolean = true,
) : (BackdropEffectScope) -> Unit by effects

internal class BottomEdgeBackdropEffects(
    val top: () -> Float,
    effects: BackdropEffectScope.() -> Unit,
    val fromEdgeProfile: Boolean,
) : ScrollEdgeBackdropEffects(effects, preservesRoleScale = fromEdgeProfile) {
    constructor(top: () -> Float, effects: BackdropEffectScope.() -> Unit) : this(top, effects, false)
}

/** La fisica e gli shader continuano a lavorare nel sistema di coordinate dell'intera finestra. */
internal class WindowedBackdropEffects(
    private val screenDensity: Float,
    private val unmeasuredRegion: IntRect?,
    private val resolveRegion: (Size, Float) -> IntRect?,
    private val build: BackdropEffectScope.() -> Unit,
) : (BackdropEffectScope) -> Unit {
    var region: IntRect? = unmeasuredRegion
        private set
    private var previousEffect: RenderEffect? = null
    private var previousOrigin = IntOffset.Zero
    private var translated: RenderEffect? = null

    @RequiresApi(31)
    override fun invoke(scope: BackdropEffectScope) {
        scope.build()
        val size = scope.size
        region = if (size.isSpecified) resolveRegion(size, scope.density / screenDensity) else unmeasuredRegion
        val origin = region?.topLeft ?: IntOffset.Zero
        val effect = scope.renderEffect ?: return
        if (origin == IntOffset.Zero) return
        if (effect !== previousEffect || origin != previousOrigin) {
            previousEffect = effect
            previousOrigin = origin
            translated = windowedEffect(effect, origin)
        }
        scope.renderEffect = translated
    }
}

/** Arrotonda verso l'esterno sulla griglia del campione e dei sottolivelli del Gaussian. */
internal fun captureRegion(bounds: Rect, size: Size, scale: Float, explicit: Boolean = false): IntRect? {
    val width = size.width.toInt()
    val height = size.height.toInt()
    if (explicit && (bounds.left * scale >= width || bounds.top * scale >= height ||
        bounds.right * scale <= 0f || bounds.bottom * scale <= 0f)) return IntRect.Zero
    fun start(v: Float, limit: Int) = (floor(v * scale / 16f).toInt() * 16).coerceIn(0, limit)
    fun end(v: Float, limit: Int) = (ceil(v * scale / 16f).toInt() * 16).coerceIn(0, limit)
    val rect = IntRect(start(bounds.left, width), start(bounds.top, height), end(bounds.right, width), end(bounds.bottom, height))
    if (explicit) return if (rect.width > 0 && rect.height > 0) rect else IntRect.Zero
    return rect.takeIf { it.width > 0 && it.height > 0 && (it.width < width || it.height < height) }
}

/** I due offset interi sono opposti: anche la grana e la lettura rifratta conservano la fase originale. */
@RequiresApi(31)
internal fun windowedEffect(effect: RenderEffect, origin: IntOffset): RenderEffect {
    val x = origin.x.toFloat()
    val y = origin.y.toFloat()
    val source = AndroidEffect.createOffsetEffect(x, y)
    val inWindow = AndroidEffect.createChainEffect(effect.asAndroidRenderEffect(), source)
    return AndroidEffect.createOffsetEffect(-x, -y, inWindow).asComposeRenderEffect()
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
    val resampleIntervalMillis: Long,
    val frozen: () -> Boolean,
    val heldWhileMoving: () -> Boolean,
    val backdropScaleFactor: () -> Float,
    val nestInContainer: Boolean,
    // Fluid Engine addition (F1A.6): vedi `drawBackdrop(onDrawLight)`.
    val onDrawLight: (DrawScope.() -> Unit)? = null
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
            resampleIntervalMillis = resampleIntervalMillis,
            backdropScaleFactor = backdropScaleFactor,
            frozen = frozen,
            heldWhileMoving = heldWhileMoving,
            nestInContainer = nestInContainer,
            onDrawLight = onDrawLight
        )
    }

    override fun update(node: DrawBackdropNode) {
        val backdropChanged = node.backdrop != backdrop
        if (backdropChanged || node.backdropScale != backdropScale) node.forgetRejectedUpgrade()
        if (backdropChanged) {
            // A record is a picture of one particular backdrop. Swapping the backdrop out — which
            // is what a tab change does, every screen bringing its own recording for the shared
            // chrome to sample — makes the held capture wrong by definition, and the offset
            // comparison below cannot be trusted to notice: the new screen's sources usually sit at
            // exactly the coordinates the old one's did.
            node.surfaceDirty = true
            node.unregisterReads()
        }
        node.backdrop = backdrop
        node.shapeProvider = shapeProvider
        node.effects = effects
        node.layerBlock = layerBlock
        if (node.exportedBackdrop != exportedBackdrop) {
            node.unbindExport()
            node.exportedBackdrop = exportedBackdrop
            node.bindExport()
        }
        // Lambde nuove (tinta, superficie) possono disegnare un materiale diverso.
        node.exportDirty = true
        val nestingChanged = node.nestInContainer != nestInContainer
        node.nestInContainer = nestInContainer
        if (backdropChanged) {
            node.registerReads()
            node.adoptFallbacks()
        }
        if (backdropChanged || nestingChanged) node.resolveContainer()
        node.onDrawBehind = onDrawBehind
        val pipelineChanged = node.onDrawBackdrop !== onDrawBackdrop
        node.onDrawBackdrop = onDrawBackdrop
        node.onDrawSurface = onDrawSurface
        node.onDrawFront = onDrawFront
        node.onDrawLight = onDrawLight
        // Fluid Engine addition (F1A.7): con o senza la luce del materiale nuovo, la superficie si riconta.
        node.recountSurface()
        node.backdropScale = backdropScale
        node.sampleOnce = sampleOnce
        node.resampleIntervalMillis = resampleIntervalMillis
        // Lambdas, and deliberately not part of equals: a caller that rebuilds them every
        // composition would make this element unequal every time and undo the whole point of
        // the gate they feed.
        node.frozen = frozen
        node.heldWhileMoving = heldWhileMoving
        node.backdropScaleFactor = backdropScaleFactor
        // F8.5d: un campione nuovo (il viaggio che parte, la cattura ferma che torna alla posa) chiede memoria al primo
        // disegno. La lettura si rinfresca qui, fuori dal disegno, se ha piu' di un secondo: altrimenti il disegno
        // rimandava la cattura a una lettura sul looper e ne affidava il secondo tentativo a un ridisegno che, durante
        // il viaggio, puo' non arrivare. Il menu dal gruppo si chiudeva allora sulla cattura ferma, senza la finitura
        // del viaggio, ogni volta che era rimasto aperto piu' di un secondo (sul banco: con la JVM lenta).
        if (pipelineChanged && node.isAttached) node.readBeforeAllocation()
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
        if (onDrawLight != other.onDrawLight) return false
        if (backdropScale != other.backdropScale) return false
        if (sampleOnce != other.sampleOnce) return false
        if (resampleIntervalMillis != other.resampleIntervalMillis) return false
        if (backdropScaleFactor != other.backdropScaleFactor) return false
        if (nestInContainer != other.nestInContainer) return false

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
        result = 31 * result + (onDrawLight?.hashCode() ?: 0)
        result = 31 * result + backdropScale.hashCode()
        result = 31 * result + sampleOnce.hashCode()
        result = 31 * result + resampleIntervalMillis.hashCode()
        result = 31 * result + backdropScaleFactor.hashCode()
        result = 31 * result + nestInContainer.hashCode()
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
 * Two capture sizes. Count distinct active frames over a short window, using the cost at the
 * surface's ordinary scale even while reduced. Scoring the current texture would create feedback:
 * halving each side would immediately divide the score by four.
 */
internal class BackdropCaptureResolution {
    private class Frames {
        private val stamps = LongArray(4)
        private var count = 0
        private var next = 0
        var last = Long.MIN_VALUE
            private set

        fun record(now: Long) {
            if (now == last) return
            stamps[next] = now
            next = (next + 1) % stamps.size
            count = (count + 1).coerceAtMost(stamps.size)
            last = now
        }

        fun recent(now: Long, includeNow: Boolean): Int {
            var n = if (includeNow && last != now) 1 else 0
            for (i in 0 until count) {
                if (stamps[i] > now - WindowMillis && stamps[i] <= now) n++
            }
            return n.coerceAtMost(stamps.size)
        }

        fun clear() {
            count = 0
            next = 0
            last = Long.MIN_VALUE
        }
    }

    private val activeDraws = Frames()
    private val movingCaptures = Frames()
    var factor = 1f
        private set

    // Draws follow scroll/route quality even when the display list follows its source by reference.
    // A moving Liquid surface can instead provide evidence from its completed, valid captures.
    // A frame present in both streams never counts twice.
    fun preview(quality: Float, pixels: Float, frameMillis: Long): Float {
        if (!quality.isFinite() || !(pixels > 0f)) return 1f
        val draws = if (quality < RecoverQuality) activeDraws.recent(frameMillis, includeNow = true) else 0
        val captures = movingCaptures.recent(frameMillis, includeNow = false)
        val updates = if (factor == ReducedScaleFactor || quality < EnterQuality) maxOf(draws, captures) else captures
        if (updates < 2) return 1f
        val score = pixels * updates
        val reduce = if (factor == ReducedScaleFactor) score > ExitPixels else score >= EnterPixels
        return if (reduce) ReducedScaleFactor else 1f
    }

    fun update(quality: Float, pixels: Float, frameMillis: Long): Float {
        factor = preview(quality, pixels, frameMillis)
        if (quality.isFinite() && quality < RecoverQuality && pixels > 0f) {
            activeDraws.record(frameMillis)
        } else {
            activeDraws.clear()
        }
        if (movingCaptures.last <= frameMillis - WindowMillis) movingCaptures.clear()
        return factor
    }

    fun movedCapture(frameMillis: Long) = movingCaptures.record(frameMillis)

    val lastUpdate: Long get() = maxOf(activeDraws.last, movingCaptures.last)

    fun reset() {
        activeDraws.clear()
        movingCaptures.clear()
        factor = 1f
    }

    companion object {
        const val WindowMillis = 96L
        private const val EnterQuality = 0.70f
        private const val RecoverQuality = 0.85f
        private const val EnterPixels = 512_000f
        private const val ExitPixels = 256_000f
    }
}

internal const val ReducedScaleFactor = 0.5f
internal const val LargeCapturePixels = 262_144f
private enum class CaptureAdmission { Ready, AwaitReading, Denied }

/** Pixels. Half of the 8192 that has been the floor of the Android hardware requirement for years. */
internal const val MaxBackdropTextureDimension = 4096f

/** Below this the capture is too coarse to be worth running the chain over at all. */
internal const val MinBackdropScale = 0.05f

/**
 * F7.18: la sfocatura minima del campione separato (viaggio, finitura nativa di Fluid-physics), in sigma e in pixel del
 * campione, sotto la quale la sfocatura di un pannello smerigliato non nasconde più la risoluzione ridotta. Il campione
 * scende fin lì prima che scenda l'uscita ([frostedSampleFloor]).
 *
 * Misura del 25 novembre 2026 (`sessioni/f7-18/scala-minima`, `scala_minima.py`) su due fotogrammi veri della pagina
 * della capsula (Tab S9 in formato telefono, chiaro e scuro): sfocatura piena contro campione ridotto, sfocato e riportato
 * su bilineare. Con sigma del campione 2,5 px o più lo scarto resta entro 2,5 livelli su 255 al 99,9° percentile (lo
 * stesso ordine del campione a metà lato di serie, 1,2–1,6); a 1,7 px sale a 4,7–5,7 e a 1,25 px a 7,8–8,7, dove si
 * vedono i gradini. Fiducia media: una pagina, due temi, tre sfocature (sigma 7, 10 e 13,8 px d'uscita).
 */
internal const val FrostedSampleSigmaPx = 2.5f

/** F7.18: il minimo del campione quando la sfocatura non è nota (il pannello flottante, sigma 10 px: un quarto di lato). */
internal const val FrostedSampleFloor = 0.25f

/**
 * F7.18: la scala minima del campione rispetto all'uscita per una sfocatura di raggio [blurPx] (pixel dell'uscita, come
 * `GlassDrawInputs.blurPx`). Android converte il raggio in sigma con 0,57735 r + 0,5 (Skia, `ConvertRadiusToSigma`).
 * Pura.
 */
internal fun frostedSampleFloor(blurPx: Float?): Float {
    if (blurPx == null || !(blurPx > 0f)) return FrostedSampleFloor
    val sigma = 0.57735f * blurPx + 0.5f
    return (FrostedSampleSigmaPx / sigma).coerceIn(MinBackdropScale, 0.5f)
}

/**
 * F7.18: l'uscita di un nodo col campione separato scende fin qui, a metà per volta, quando il campione è già al suo
 * minimo ([frostedSampleFloor]) e il tetto non basta ancora. Prima di F7.18 l'uscita restava alla griglia dello schermo
 * e una cattura grande in viaggio non entrava mai (il popover della capsula, 19,8 MB contro 12 MiB). Un quarto di lato
 * ammorbidisce il filo, ma è vetro e non trasparenza. A occhio, 25 novembre 2026.
 */
internal const val MinTravelOutputScale = 0.25f

/**
 * F7.18: la scala a cui la pagina sotto un pannello davanti rifà le sue catture grandi quando il pannello chiede spazio
 * (`FluidGlassResources.frontDemand`): metà lato, un quarto dei byte. La pagina è velata o coperta, e sotto la
 * sfocatura del suo vetro metà lato non si distingue; rilasciarla del tutto la farebbe diventare piena. A occhio,
 * 25 novembre 2026.
 */
internal const val GlassYieldScale = 0.5f

/** F7.18: le prime riprove di un pannello davanti negato, prima del ritmo di un secondo. */
private const val FrontRetryMillis = 100L
private const val FrontFastRetries = 8

/** Never held. The default for `frozen` and `heldWhileMoving` in [drawBackdrop]. */
private val NeverFrozen: () -> Boolean = { false }

/**
 * How long a held capture may go without a refresh.
 *
 * A hundred and ten milliseconds: short enough that what it has to correct at the end of a hold is
 * never big enough to read as a jump, long enough that a fold costs three or four recordings
 * instead of one per frame.
 */
private const val HoldRefreshMillis = 110L

/**
 * Quanto a lungo un pannello tiene la cattura che ha mentre la sua sorgente non c'e'.
 *
 * Il caso vero dura un fotogramma: un cambio pagina smonta la sorgente vecchia nello stesso
 * fotogramma in cui monta la nuova, e il chrome condiviso (barra, pillola) riceve il fondale nuovo
 * da una ricomposizione che arriva solo al fotogramma dopo. Il margine e' largo apposta, perche' un
 * fotogramma che compone una pagina intera puo' durare ottanta millisecondi: oltre, una sorgente
 * che non torna non e' piu' un ritardo ma un'assenza, e tenere un'immagine ormai falsa a tempo
 * indeterminato sarebbe peggio di un vetro vuoto.
 */
internal const val SourceGoneGraceMillis = 400L

/**
 * Se un pannello deve tenere la cattura che ha invece di rifarne una.
 *
 * Ricatturare con la sorgente sparita non registra niente: `LayerBackdrop.drawBackdrop` esce senza
 * disegnare se non ha piu' le coordinate, e `recordLayer` svuota il layer prima di chiamarla. Il
 * risultato e' una cattura **vuota** sotto una tinta traslucida, cioe' la pagina cruda e nitida
 * dentro la sagoma del vetro: un fotogramma di testo leggibile sotto la barra, e dal fotogramma
 * dopo (fondale nuovo, `surfaceDirty`) di nuovo sfocato. La cattura vecchia invece e' ancora
 * buona da guardare — il `GraphicsLayer` rilasciato resta vivo finche' un layer genitore lo
 * disegna — e un fondale appena scaduto sfocato e' un ritardo che nessuno vede.
 *
 * Solo con una cattura valida da tenere ([sampled]): chi non ne ha mai presa una non ha niente da
 * perdere, e registrare e' l'unico modo di accorgersi che la sorgente e' tornata.
 *
 * @param ready il fondale ha qualcosa da disegnare adesso (`Backdrop.isReadyToSample`).
 * @param notReadyForMillis da quanto non e' pronto; ignorato quando lo e'.
 */
internal fun holdsCaptureOfGoneSource(
    ready: Boolean,
    sampled: Boolean,
    notReadyForMillis: Long,
): Boolean = !ready && sampled && notReadyForMillis < SourceGoneGraceMillis

/**
 * Fluid Engine addition (4.0.0, F2.3): se questo layer lo registra un nodo del vetro (un export, il materiale di un
 * pannello). Per [LayerBackdrop.announcesRevisions]: quei layer avvisano quando si registrano di nuovo. Qui perche'
 * `DrawBackdropNode` e' privato a questo file.
 */
internal fun LayerBackdrop.isRecordedByDrawBackdropNode(): Boolean = ownerNode is DrawBackdropNode

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
    var resampleIntervalMillis: Long,
    var frozen: () -> Boolean,
    var heldWhileMoving: () -> Boolean,
    var backdropScaleFactor: () -> Float,
    var nestInContainer: Boolean,
    // Fluid Engine addition (F1A.6): vedi `drawBackdrop(onDrawLight)`.
    var onDrawLight: (DrawScope.() -> Unit)? = null
) : LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode, ObserverModifierNode,
    GlassContainer, GlassContainerReader, CompositionLocalConsumerModifierNode, GlassStateDump.Probe,
    GlassStateDump.Actionable, Modifier.Node() {

    private val effectScope =
        object : BackdropEffectScopeImpl(), GlassNestingScope, GlassRenderQuality, GlassBodyFallbackScope {
            override val fallbackBody = GlassBodyFallback()
            override val optics get() = renderState.optics
            override val inMotion get() = glassMoving()

            override val shape: Shape get() = shapeProvider.innerShape

            // Fluid Engine addition (F1A, voce 18): vedi [GlassNestingScope].
            override val standsOnGlass: Boolean get() = this@DrawBackdropNode.standsOnGlass
        }

    /**
     * Fluid Engine addition (F1A, voce 18): l'ultima risposta di [resolveContainer] alla domanda «sto dentro un
     * vetro?», per lo scope degli effetti. Vedi [GlassNestingScope].
     */
    private var standsOnGlass = false

    // LOCAL CHANGE (F1B, voce 20 di `LICENSES/AndroidLiquidGlass.md`): ogni giro degli effetti e' osservato, anche
    // quello del disegno in cui lo scope prende la taglia. Vedi [BackdropEffectsUpdates].
    private val effectsUpdates = BackdropEffectsUpdates(observe = { block -> observeReads(block) }, rebuild = { updateEffects() })

    private var graphicsLayer: GraphicsLayer? = null

    /**
     * Fluid Engine addition (F1A.7): ciò che questo nodo ha contato in `FluidGlassDiagnostics` (il nodo, i suoi
     * layer, la superficie col suo materiale), per restituirlo quando si stacca. Costa una lettura volatile per
     * evento a diagnostica spenta.
     */
    private val census = GlassNodeCensus(FluidGlassNodeKind.Backdrop)

    /**
     * Fluid Engine addition (F1A.7): il materiale che la superficie disegna, per la diagnostica. Solo il materiale
     * nuovo passa la luce ([onDrawLight]): sono `Liquid` le lastre di `glassSurface` e `glassControlSurface`, le
     * superfici di Fluid-physics col materiale nuovo, la pillola e la goccia quando lo prendono; tutto il resto (il
     * vetro di prima, Fluid-physics a `Balanced`/`Lite` o con `Classic`, chi chiama `drawBackdrop` a mano senza
     * `onDrawLight`) e' il vetro di prima.
     */
    fun recountSurface() {
        if (isAttached) {
            census.surface(if (onDrawLight != null) FluidGlassModel.Liquid else FluidGlassModel.Classic)
            if (onDrawLight !is GlassLightDraw) bindMotionLight(null)
        }
    }

    private var motionLight: GlassLightEnvironment? = null

    private fun bindMotionLight(next: GlassLightEnvironment?) {
        if (motionLight === next) return
        motionLight?.removeSurface()
        motionLight = next
        next?.addSurface()
    }

    private val layoutLayerBlock: GraphicsLayerScope.() -> Unit = {
        clip = shapeProvider.innerShape !== dev.antigravity.fluidengine.ui.glass.field.GlassUnclippedShape
        // Fluid Engine change: the *layer* is clipped by the shape's rounded-rect reading, not by
        // its exact path. A `RenderNode` clipped to a generic path cannot use the hardware's
        // rounded-rect clip and falls back to masking the whole node offscreen — per pane, per
        // frame. What the eye actually reads as the corner is the tint and the specular edge drawn
        // on the surface, and those keep the exact continuous silhouette; the layer clip only has
        // to stop the refracted image spilling, which a rounded rect at the same radii does to
        // within a fraction of a pixel.
        // F6B.15: il contorno del ritaglio alla misura della posa (vedi [placedLayerSize] e [SizedClipShape]).
        shape = SizedClipShape(shapeProvider.fastClipShape, placedLayerSize)
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
    var surfaceDirty = true

    private var recordedSize: IntSize = IntSize.Zero
    private var recordedSourceOffsets = SamplePositions()
    private var currentOffsets = SamplePositions()

    /** Vero dopo una cattura **valida**: e' questo, non "abbiamo provato", che spegne `sampleOnce`. */
    private var sampled = false
    private val borrowedSources = ArrayList<LayerBackdrop>(0)
    private val borrowedShells = ArrayList<LayerBackdrop>(0)

    private fun rememberLoans() {
        borrowedSources.clear()
        borrowedShells.clear()
        val reader = layoutCoordinates ?: return
        val layers = sampledBackdrop.layerSources
        for (i in layers.indices) {
            val source = layers[i] as? LayerBackdrop ?: continue
            val shell = source.chromeFallback ?: continue
            if (source.sourceFor(reader) !== shell) continue
            borrowedSources.add(source)
            borrowedShells.add(shell)
        }
    }

    /**
     * F8.10, diagnostica: le sorgenti che questa superficie campiona adesso. Per ognuna l'identita' scritta nel fondale,
     * quella che legge davvero (la pagina privata di un layer su richiesta), il suo layer, chi la registra, quanti
     * lettori ha e la sua revisione. Solo per il log `FluidGlassFrame`: serve a vedere una cattura rimasta legata a una
     * sorgente che non si registra piu' o che e' stata sostituita.
     */
    private fun describeSources(): String {
        val reader = layoutCoordinates?.takeIf { it.isAttached }
        return sampledBackdrop.layerSources.joinToString(separator = ";", prefix = "[", postfix = "]") { layer ->
            val raw = layer as? LayerBackdrop ?: return@joinToString "altro:${layer.javaClass.simpleName}"
            val source = reader?.let { raw.sourceFor(it) }
            val graphics = source?.graphicsLayer
            "raw=${System.identityHashCode(raw)} src=${source?.let { System.identityHashCode(it) }}" +
                " layer=${graphics?.let { System.identityHashCode(it) }}${if (graphics?.isReleased == true) "(rilasciato)" else ""}" +
                " owner=${(source?.ownerNode ?: raw.ownerNode)?.let { "${it.javaClass.simpleName}@${System.identityHashCode(it)}" }}" +
                " onDemand=${raw.onDemand} lettori=${raw.readerCount} rev=${source?.revision}"
        }
    }

    /** Quando e' stata presa l'ultima cattura valida, per [resampleIntervalMillis]. */
    private var lastRecordedAt = 0L

    /** Se il fotogramma prima si stava tenendo ferma la cattura. Vedi [frozen]. */
    private var wasHolding = false

    /** Da quando il fondale non e' pronto, o zero se lo e'. Vedi [holdsCaptureOfGoneSource]. */
    private var notReadySince = 0L

    // F8.10, diagnostica per la fotografia a riposo ([GlassStateDump]): costano due assegnazioni per disegno.
    /** Quante volte l'ammissione della cattura ha aspettato la lettura della memoria, e l'ultima. */
    private var awaitReadings = 0
    private var lastAwaitReadingAt = 0L

    /** L'ultimo disegno: quando, a che misura e con quale ammissione della cattura. */
    private var lastDrawAt = 0L
    private var lastDrawSize = Size.Zero
    private var lastAdmission: CaptureAdmission? = null
    private var debugDraws = 0
    private var debugPoses = 0

    /** F8.10: una riga per [GlassStateDump]. Sul thread principale, a richiesta. */
    override fun describe(now: Long): String {
        fun age(at: Long) = if (at == 0L) "mai" else "${now - at}ms"
        return "kind=glass id=${System.identityHashCode(this)} size=$lastDrawSize" +
            " at=${layoutCoordinates?.takeIf { it.isAttached }?.positionInRoot()} drawAge=${age(lastDrawAt)} draws=$debugDraws poses=$debugPoses settle=${placementSettle.intValue}" +
            " sampled=$sampled recorded=$recordedSize captureAge=${age(lastRecordedAt)} replay=$replay" +
            " admission=$lastAdmission awaitReading=$awaitReadings lastAwaitAge=${age(lastAwaitReadingAt)}" +
            " bytes=$residentBytes denied=$budgetDenied suspended=$readsSuspended dirty=$surfaceDirty" +
            " container=${container?.let { System.identityHashCode(it) }}" +
            " material=${containerMaterial?.let { System.identityHashCode(it) }} owner=${glassFrameOwner()}" +
            " src=${describeSources()}"
    }

    /**
     * F8.10, prova di debug (`vetri-cattura` di [GlassStateDump.ExtraAction]): la cattura si rifà al prossimo disegno. Il
     * disegno lo porta lo stato letto in [draw] (`GlassStateDump.readGlassTick`), non un `invalidateDraw()`.
     */
    override fun recapture() {
        if (isAttached) surfaceDirty = true
    }

    // --- Fluid Engine addition (2.13.0), vetro su vetro: questa superficie come lettrice ---------

    /**
     * Quello che la superficie campiona davvero: [backdrop], oppure [backdrop] con il materiale del
     * pannello su cui sta infilato nella pila. Vedi [resolveContainer].
     */
    private var sampledBackdrop: Backdrop = backdrop

    /** Il pannello su cui sta, quando sta su un pannello, e il materiale che ne campiona. */
    private var container: GlassContainer? = null
    private var containerMaterial: LayerBackdrop? = null

    /** I layer su richiesta che il fondale contiene: devono sapere che qualcuno li guarda. */
    private var registeredReads: List<LayerBackdrop> = emptyList()

    /** La domanda "su quale pannello sto?" va rifatta al prossimo disegno. */
    private var containerDirty = false

    // --- Fluid Engine addition (2.13.0), vetro su vetro: questa superficie come contenitore ------

    /** Le superfici che campionano il materiale di questa. */
    private val nestedReaders = ArrayList<GlassContainerReader>(0)

    /**
     * Il materiale che questa superficie pubblica per il vetro che le sta dentro, quando non ne
     * esporta gia' uno ([exportedBackdrop]). Creato al primo lettore, mai prima: un pannello senza
     * vetro dentro non ha un layer in piu'.
     */
    private var ownMaterial: LayerBackdrop? = null

    /** Le ultime coordinate, per dare un posto al materiale creato dopo il posizionamento. */
    private var lastCoordinates: LayoutCoordinates? = null

    private val wakeExport: () -> Unit = {
        exportDirty = true
        if (isAttached) invalidateDraw()
    }

    /**
     * Il materiale pubblicato va registrato di nuovo solo se e' cambiato, non a ogni disegno.
     *
     * Questa superficie si ridisegna a ogni fotogramma in cui si sposta (legge le proprie
     * coordinate per sapere se la cattura vale ancora), e a ogni nuova registrazione del materiale
     * il vetro che lo campiona e' "danneggiato" per la GPU: rifa' sfocatura e lente anche se
     * l'immagine e' identica. Su un pannello che scorre con i suoi tasti era un ricalcolo per tasto
     * per fotogramma, per niente — la stessa cosa che la 2.10.0 aveva tolto alle catture.
     *
     * Quindi si registra quando: e' la prima volta o c'e' un lettore nuovo ([wakeExport]), cambia
     * la geometria (taglia, margine, risoluzione), l'elemento e' stato aggiornato, oppure cambia
     * uno stato che i disegni del materiale hanno letto (intensita', tinta, pressione: osservati da
     * [exportObserver]). La cattura dentro il materiale e' un riferimento, quindi quando lei si
     * rifa' il materiale la segue senza essere registrato di nuovo.
     */
    var exportDirty = true
    private var exportedInto: LayerBackdrop? = null
    private var exportedWidth = -1f
    private var exportedHeight = -1f
    private var exportedPadding = -1f
    private var exportedScale = -1f
    private var exportObserver: SnapshotStateObserver? = null
    private val onExportReadChanged: (DrawBackdropNode) -> Unit = { node ->
        node.exportDirty = true
        if (node.isAttached) node.invalidateDraw()
    }

    /**
     * Where every source sits relative to this surface, which is the only thing a capture depends on.
     *
     * Null means at least one source has not been positioned yet, or the backdrop cannot say what it
     * is made of — either way the answer is to record, which is what every backdrop did before.
     */
    private class SamplePositions {
        var sources = arrayOfNulls<LayerBackdrop>(0)
        var offsets = LongArray(0)
        var valid = false

        fun clear() {
            valid = false
            sources.fill(null)
        }

        fun read(backdrop: Backdrop, reader: LayoutCoordinates?) {
            valid = false
            val layers = backdrop.layerSources
            if (layers.isEmpty() || reader == null || !reader.isAttached) return
            if (layers.size != sources.size) {
                sources = arrayOfNulls(layers.size)
                offsets = LongArray(layers.size)
            }
            for (i in layers.indices) {
                val source = (layers[i] as? LayerBackdrop)?.sourceFor(reader) ?: return
                val at = source.layerCoordinates ?: return
                if (!at.isAttached) return
                sources[i] = source
                offsets[i] = try { at.localPositionOf(reader).packedValue } catch (_: Exception) { return }
            }
            valid = true
        }

        fun sameSources(other: SamplePositions) = valid && other.valid && sources.contentEquals(other.sources)
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
    private lateinit var resources: FluidGlassResources
    private lateinit var renderState: GlassRenderState
    private var window: android.view.Window? = null
    private var windowLease: java.io.Closeable? = null
    private var bytesPerPixel = 4
    private var allocationPrepared = false
    private var refreshPosted = false
    private val refreshAllocationReading = Runnable {
        refreshPosted = false
        GlassFrameDiagnostics.event("glassReadingRefreshed", this) { "attached=$isAttached" }
        if (isAttached) {
            readBeforeAllocation()
            wake()
        }
    }
    private fun releaseNativeSample() {
        nativeSampleLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        nativeSampleLayer = null
        nativeSample = null
    }

    /** Rilegge la memoria se l'ultima lettura ha piu' di un secondo, prima di una cattura. Mai dentro il disegno. */
    fun readBeforeAllocation() {
        resources.refreshBeforeAllocation()
        allocationPrepared = true
    }

    private fun glassMoving() = renderState.scrolling || backdropScaleFactor() < 1f ||
        currentValueOf(LocalFluidGlassQuality)?.let { it.scrolling || it.level < 1f } == true ||
        currentValueOf(LocalFluidPageTransition)?.isMoving == true || frozen() ||
        (onDrawBackdrop as? GlassTravelCapture)?.travelling == true

    private var resident: FluidMemoryBudget.Resident? = null
    private var residentSize = IntSize.Zero
    private var residentBytes = 0L
    private var residentFiltered = false
    private var filterNeedsCredit = false
    private var residentSampleScale = 0f
    private var residentSample: GlassTravelCapture? = null
    private var pending: FluidMemoryBudget.Reservation? = null
    private var nextBytes = 0L
    private var budgetDenied = false
    private var budgetScale = 1f
    /** F7.18: il tetto della scala d'uscita quando c'è un campione separato. Vedi [MinTravelOutputScale]. */
    private var outputBudget = 1f
    /** F7.18: il vetro sta davanti alla pagina ([LocalGlassFrontLayer]), letto a ogni disegno. */
    private var front = false
    /** F7.18: questo pannello davanti ha chiesto alla pagina di cedere (`FluidGlassResources.frontDemand`). */
    private var frontRequested = false
    private var fastRetries = 0

    /**
     * F7.18: la sveglia del nodo quando il credito torna (o arriva la lettura della memoria). Letta in [draw], scritta dai
     * `Runnable` qui sotto: è lo stato osservato a rimettere il nodo nel prossimo fotogramma. Il solo `invalidateDraw()`
     * chiamato da un `Runnable` non bastava: sul Tab S9 (fascia bassa simulata, 25 novembre 2026) il popover della capsula,
     * ammesso a un quarto d'uscita, trovava lo spazio alla posa ma non si ridisegnava più, e restava a un quarto.
     */
    private val budgetWake = mutableIntStateOf(0)

    private fun wake() {
        budgetWake.intValue++
        invalidateDraw()
    }

    /** F7.18: la pagina cede ([GlassYieldScale]) se il vetro è grande, non sta davanti e un pannello davanti chiede spazio. */
    private fun yieldCap(width: Float, height: Float): Float =
        if (!front && large(width, height) && resources.frontDemand.intValue > 0) GlassYieldScale else 1f

    /**
     * F7.18: la scala d'uscita di questo fotogramma, la stessa nel disegno e in [effectScopeIsCurrent]: col campione separato
     * il tetto d'uscita ([outputBudget]), senza il tetto del registro ([budgetScale]); in entrambi i casi la cessione alla
     * pagina davanti. Quando le due strade la calcolavano diversa, a uscita ridotta lo scope non era mai «corrente», il
     * pre-draw non aggiornava la catena e la sagoma restava quella del viaggio (ondulata e ferma).
     */
    private fun budgetedScale(target: Float, separateSample: Boolean, yieldCap: Float): Float =
        if (separateSample) minOf(target, outputBudget, target * yieldCap) else minOf(target, budgetScale, target * yieldCap)

    private fun retryDelayMillis(): Long =
        if (front && fastRetries < FrontFastRetries) { fastRetries++; FrontRetryMillis } else 1000L
    private var waitForBytes = 0L
    private var desiredBytes = 0L
    // Dopo un rifiuto si risveglia il nodo solo quando la nuova cattura entra davvero.
    // Un campione già visibile può risalire solo alla posa.
    private val awaitBudget = object : Runnable {
        override fun run() {
            val required = if (waitForBytes > 0L) waitForBytes else desiredBytes
            if (!isAttached || required == 0L) return
            val glass = resources.budget.usage(FluidMemoryCategory.GLASS)
            val process = resources.budget.usage()
            val available = minOf(glass.limitBytes - glass.usedBytes, process.limitBytes - process.usedBytes)
            GlassFrameDiagnostics.event("glassBudgetRetry", this@DrawBackdropNode) {
                "required=$required available=$available waiting=$waitForBytes moving=${glassMoving()} front=$front"
            }
            if (available >= required && (waitForBytes > 0L || !glassMoving())) {
                if (waitForBytes == 0L) {
                    budgetScale = 1f
                    outputBudget = 1f
                    desiredBytes = 0L
                }
                waitForBytes = 0L
                readBeforeAllocation()
                wake()
            } else MainHandler.postDelayed(this, retryDelayMillis())
        }
    }
    private var budgetTarget = Float.NaN
    private var budgetExtent = Size.Zero
    private var replay = true
    private var readsSuspended = false

    private fun discardRecording() {
        if (residentBytes > 0L) GlassFrameDiagnostics.event("glassRelease", this) { "bytes=$residentBytes discard=true" }
        // Gli export senza lettori conservano la shell. Svuotare le registrazioni stacca
        // anche il campione dal display list prima di restituire i byte al lettore davanti.
        val density = requireDensity()
        val direction = requireLayoutDirection()
        graphicsLayer?.record(density, direction, IntSize(1, 1)) {}
        recordedPipeline?.discard(density, direction)
        activeSample = null
        recordedPipeline = null
        releaseNativeSample()
        sampled = false
        resident?.close()
        resident = null
        residentSize = IntSize.Zero
        residentBytes = 0L
        residentSample = null
        surfaceDirty = true
        exportDirty = true
        MainHandler.removeCallbacks(awaitBudget)
        waitForBytes = 0L
    }

    private fun updateReaderVisibility() {
        val suspend = renderState.optics == GlassOptics.Protected &&
            currentValueOf(LocalCoveredGlassView) === requireView() &&
            publishedMaterial?.wantsRecording != true
        if (suspend == readsSuspended) return
        readsSuspended = suspend
        surfaceDirty = true
        if (suspend) {
            forgetRejectedUpgrade()
            unregisterReads()
            leaveContainer()
            bindMotionLight(null)
            desiredBytes = 0L
            discardRecording()
            budgetTarget = Float.NaN
            if (budgetDenied) resources.visibleCaptureDenied(window, false)
            budgetDenied = false
        } else {
            registerReads()
            containerDirty = true
            exportDirty = true
        }
    }
    private var nativeSampleLayer: GraphicsLayer? = null
    private var nativeSample: GlassTravelCapture? = null
    private var activeSample: GlassTravelCapture? = null
    private var recordedPipeline: GlassTravelCapture? = null
    private var recordedSampleScale = 0f
    private var nativeRoleCeiling = 1f
    private val nativeEffects: BackdropEffectScope.() -> Unit = { effects(this) }

    private fun sampleFor(size: Size, scale: Float): GlassTravelCapture? {
        (onDrawBackdrop as? GlassTravelCapture)?.let { return it }
        if (onDrawLight !== NativeGlassFinish || !large(size.width, size.height)) return null
        // Il palco della finestra può contenere solo una piccola pillola. Vale il campo ritagliato.
        val region = (effects as? WindowedBackdropEffects)?.region
        if (region != null && !large(region.width / scale, region.height / scale) &&
            nativeRoleCeiling == 1f && budgetScale >= scale) return null
        val level = renderState.optics
        val moving = glassMoving()
        if (sampled && budgetScale == 1f && recordedLogicalSize == size &&
            (moving || recordedOptics != level && renderState.windowMotion)) return nativeSample
        val ordinary = minOf(scale * nativeRoleCeiling, glassBackgroundScale(scale, level, moving, 0f))
        if (ordinary >= scale && budgetScale >= scale) return null
        val wanted = minOf(ordinary / scale, 1f)
        val held = nativeSample
        val density = requireDensity().density
        if (held != null && held.ordinaryScale == wanted && held.screenDensity == density) return held
        val sample = nativeSampleLayer ?: requireGraphicsContext().createGraphicsLayer().also { nativeSampleLayer = it }
        return GlassTravelCapture(sample, density, nativeEffects, travelling = false, ordinaryScale = wanted)
            .also { nativeSample = it }
    }

    // Un MiB RGBA8: il confine F7.12 si misura prima dei tetti, per contare anche il ripiego.
    private fun large(width: Float, height: Float) = width * height >= LargeCapturePixels

    private fun reserveCapture(record: IntSize, travel: GlassTravelCapture?): CaptureAdmission {
        val filtered = effectScope.renderEffect != null
        val sampleScale = travel?.sampleScale ?: 0f
        val sameStorage = travel?.sharesStorage(residentSample) ?: (residentSample == null)
        if (sameStorage && record == residentSize && filtered == residentFiltered && sampleScale == residentSampleScale) {
            nextBytes = residentBytes
            return CaptureAdmission.Ready
        }
        val width = record.width.toLong()
        val height = record.height.toLong()
        val stride = (width * bytesPerPixel + 63L) / 64L * 64L
        nextBytes = try {
            val output = fluidSurfaceBytes(width, height, bytesPerPixel, if (filtered) 2 else 1, stride)
            val estimate = Math.addExact(output, travel?.sampleBytes(record, bytesPerPixel) ?: 0L)
            if (estimate < LargeCapturePixels.toLong() * 4 && !budgetDenied &&
                !large(record.width / effectiveScale, record.height / effectiveScale)) 0L else estimate
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        if (sameStorage && record == residentSize && nextBytes in 1..residentBytes) {
            nextBytes = residentBytes
            return CaptureAdmission.Ready
        }
        if (nextBytes == 0L) return CaptureAdmission.Ready
        if (waitForBytes > 0L && nextBytes >= waitForBytes) {
            GlassFrameDiagnostics.event("glassWaitBytes", this) { "bytes=$nextBytes waiting=$waitForBytes denied=$budgetDenied front=$front" }
            return CaptureAdmission.Denied
        }
        if (!allocationPrepared || !resources.allocationReadingFresh) {
            GlassFrameDiagnostics.event("glassAwaitReading", this) { "bytes=$nextBytes prepared=$allocationPrepared front=$front" }
            if (!refreshPosted) {
                refreshPosted = true
                MainHandler.post(refreshAllocationReading)
            }
            // F8.20b: un pannello davanti (modale, popover, card) non aspetta la lettura. Aspettando disegnava vetro limpido
            // sopra la pagina leggibile, e se il risveglio non arrivava restava cosi' (il pannello della capsula della rete
            // sul Tab S9, 25/11; `F8p20GlassPixelsTest` col tetto pieno). Va subito al tetto con la lettura che c'e': se la
            // cattura non entra, la strada di F7.18 (la precedenza davanti, la scala ridotta, poi il pieno).
            if (!front) {
              // F8.10, diagnostica: il vetro aspetta la lettura della memoria (log e fotografia a riposo).
              awaitReadings++
              lastAwaitReadingAt = System.currentTimeMillis()
              GlassFrameDiagnostics.event("awaitReading", this) { "size=$record bytes=$nextBytes prepared=$allocationPrepared" }
              return CaptureAdmission.AwaitReading
            }
        }
        pending = resources.budget.tryReserve(FluidMemoryCategory.GLASS, nextBytes)
        if (pending != null) {
            GlassFrameDiagnostics.event("glassBudgetReserve", this) { "size=$record bytes=$nextBytes" }
            return CaptureAdmission.Ready
        }
        GlassFrameDiagnostics.event("glassBudgetDenied", this) {
            "size=$record bytes=$nextBytes held=$residentBytes limit=${resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes}" +
                " used=${resources.budget.usage(FluidMemoryCategory.GLASS).usedBytes} out=$effectiveScale sample=${travel?.sampleScale}" +
                " front=$front optics=${renderState.optics}"
        }
        if (!budgetDenied) {
            budgetDenied = true
            desiredBytes = nextBytes
            resources.visibleCaptureDenied(window, true, this, nextBytes)
        }
        return CaptureAdmission.Denied
    }

    /** F7.18: il pannello davanti lascia la pagina tornare alla sua scala. */
    private fun releaseFrontDemand() {
        if (!frontRequested) return
        frontRequested = false
        fastRetries = 0
        if (::resources.isInitialized) resources.frontReleased(this)
    }

    fun forgetRejectedUpgrade() {
        if (::renderState.isInitialized && renderState.rejectedUpgrade?.reader === this) resources.forgetRejectedCapture(window, this)
    }

    private fun recorded(record: IntSize) {
        if (sampled && effectiveScale == recordedScale &&
            (record.width.toLong() * record.height < recordedSize.width.toLong() * recordedSize.height ||
                residentFiltered && effectScope.renderEffect == null)) forgetRejectedUpgrade()
        if (waitForBytes > 0L) {
            MainHandler.removeCallbacks(awaitBudget)
            waitForBytes = 0L
        }
        residentFiltered = effectScope.renderEffect != null
        residentSampleScale = activeSample?.sampleScale ?: 0f
        residentSample = activeSample
        if (pending != null || nextBytes == 0L) {
            val owner = pending?.confirm()
            resident?.close()
            resident = owner
            residentBytes = nextBytes
            residentSize = record
        }
        if (budgetDenied) {
            budgetDenied = false
            resources.visibleCaptureDenied(window, false)
            MainHandler.removeCallbacks(awaitBudget)
            if (desiredBytes > nextBytes) MainHandler.postDelayed(awaitBudget, retryDelayMillis()) else desiredBytes = 0L
        }
    }

    private val resolution = BackdropCaptureResolution()
    private var recoveryPending = false
    private val recoverResolution = object : Runnable {
        override fun run() {
            val deadline = resolution.lastUpdate + BackdropCaptureResolution.WindowMillis
            if (android.os.SystemClock.uptimeMillis() < deadline) {
                MainHandler.postAtTime(this, deadline)
            } else {
                recoveryPending = false
                resolution.reset()
                if (isAttached) invalidateDraw()
            }
        }
    }

    private fun captureScale(width: Float, height: Float, commit: Boolean): Float {
        val local = roleScale(width, height, commit)
        // Il palco fisico include il filo nel corpo. Il campione separato riceve il tetto.
        if (onDrawBackdrop is GlassTravelCapture || onDrawLight === NativeGlassFinish) {
            val level = renderState.optics
            val output = fitToTexture(backdropScale, width, height)
            if (commit) nativeRoleCeiling = if (level == GlassOptics.Full) 1f else minOf(1f, local / output)
            if (sampled && recordedLogicalSize.width == width && recordedLogicalSize.height == height &&
                recordedOptics != level && (glassMoving() || renderState.windowMotion)) return recordedScale
            return if (level == GlassOptics.Full) local else output
        }
        if (commit) nativeRoleCeiling = 1f
        // Il bordo di scroll mantiene la risoluzione scelta da F7.4. Si alleggerisce la passata.
        if ((effects as? ScrollEdgeBackdropEffects)?.preservesRoleScale == true || !large(width, height)) return local
        val level = renderState.optics
        if (level == GlassOptics.Full && recordedOptics == level) return local
        val moving = glassMoving()
        val reusable = sampled && recordedLogicalSize.width == width && recordedLogicalSize.height == height
        if (reusable && recordedOptics != level && (moving || renderState.windowMotion)) return recordedScale
        val next = glassBackgroundScale(local, level, moving, if (reusable) recordedScale else 0f)
        return if (sampled && next != recordedScale && !sampledBackdrop.isReadyToSample()) recordedScale else next
    }

    private var recordedOptics = GlassOptics.Full

    private fun roleScale(width: Float, height: Float, commit: Boolean): Float {
        val full = fitToTexture(backdropScale, width, height)
        val quality = backdropScaleFactor()
        // A full-quality draw is the common case (including callers that never opt in).
        if (quality >= 1f && resolution.lastUpdate == Long.MIN_VALUE && (!sampled || recordedScale == full)) return full
        val frame = android.view.animation.AnimationUtils.currentAnimationTimeMillis()
        val pixels = width * full * (height * full)
        val factor = if (commit) resolution.update(quality, pixels, frame)
                     else resolution.preview(quality, pixels, frame)
        if (commit) {
            if (factor == ReducedScaleFactor && !recoveryPending) {
                recoveryPending = true
                MainHandler.postAtTime(recoverResolution, frame + BackdropCaptureResolution.WindowMillis)
            } else if (factor == 1f && recoveryPending) {
                MainHandler.removeCallbacks(recoverResolution)
                recoveryPending = false
            }
        }
        val requested = if (factor == 1f) full else fitToTexture(backdropScale * factor, width, height)
        return if (sampled && requested != recordedScale && !sampledBackdrop.isReadyToSample()) recordedScale else requested
    }

    // A held capture retains its own transform while the source is temporarily missing.
    private var recordedScale = 1f
    private var recordedLogicalSize = Size.Zero

    private var recordedRegion: IntRect? = null
    private var originalExtent = Size.Zero

    private val recordBackdropBlock: (DrawScope.() -> Unit) = record@ {
        if (recordedRegion == IntRect.Zero) return@record
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
        val drawSample = activeSample?.capture ?: onDrawBackdrop
        drawSample {
            val origin = recordedRegion?.topLeft ?: IntOffset.Zero
            val extent = drawContext.size
            val sourceCanvas = drawContext.canvas
            if (origin != IntOffset.Zero) sourceCanvas.translate(-origin.x / scale, -origin.y / scale)
            if (recordedRegion != null) drawContext.size = originalExtent
            try {
                with(sampledBackdrop) {
                    drawBackdrop(density = effectScope, coordinates = layoutCoordinates, layerBlock = layerBlock)
                }
            } finally {
                if (recordedRegion != null) drawContext.size = extent
                if (origin != IntOffset.Zero) sourceCanvas.translate(origin.x / scale, origin.y / scale)
            }
        }
        if (scale != 1f) {
            canvas.scale(1f / scale, 1f / scale)
        }
        if (padding != 0f) {
            canvas.translate(-padding, -padding)
        }
    }

    /**
     * Fluid Engine change (2.13.0): la manutenzione della cattura, separata dal disegnarla.
     *
     * La cattura si disegna due volte quando qualcuno campiona il materiale di questa superficie
     * (sullo schermo e nel materiale esportato), ma si decide e si rifa' una volta sola per
     * fotogramma: prima la decisione stava dentro il disegno, e l'export la ripeteva.
     */
    private fun DrawScope.refreshCapture(): CaptureAdmission {
        val layer = graphicsLayer
        if (layer != null) {
            val padding = padding
            // Not named `scale`: that shadows the DrawScope transform of the same name, which is
            // what un-scales the draw below.
            val contentScale = effectiveScale
            val region = (effects as? WindowedBackdropEffects)?.region
            val fullWidth = (size.width * contentScale).toInt()
            val fullHeight = (size.height * contentScale).toInt()
            val border = padding.toInt().toLong() * 2
            val recordSize = if (region == IntRect.Zero) IntSize(1, 1) else IntSize(
                ((region?.width ?: fullWidth) + border).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
                ((region?.height ?: fullHeight) + border).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt(),
            )
            // Una posa può spostare la cattura dentro le pagine. Una cattura della vecchia
            // riserva va sostituita anche se il vetro è congelato o campiona una volta sola.
            val reader = layoutCoordinates
            val lostLoan = borrowedSources.indices.any { i ->
                reader == null || borrowedSources[i].sourceFor(reader) !== borrowedShells[i]
            }
            if (lostLoan) sampled = false
            currentOffsets.read(sampledBackdrop, layoutCoordinates)
            val sameSources = currentOffsets.sameSources(recordedSourceOffsets)

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
            //
            // Fluid Engine 2.10.0: when every source is a recording, only the *relative* offsets
            // count. The capture draws each source translated by where it sits relative to this
            // surface, so a surface and its backdrop that move together — a page swiped sideways
            // with its glass on it, a sheet sliding up with the cover it carries — have exactly the
            // capture they had. Comparing the absolute position as well made every pane on a page
            // replay the whole screen on every frame of every swipe, for a picture that came out
            // identical. A layer transform on the surface (the lean of a pressed control) still
            // counts: it moves the surface relative to its source, and `localPositionOf` sees it.
            val moved = !sameSources || !currentOffsets.offsets.contentEquals(recordedSourceOffsets.offsets)
            val wanted = surfaceDirty ||
                recordedSize != recordSize ||
                if (sampleOnce) !sampled else moved

            // Fluid Engine addition: [resampleIntervalMillis] puts a floor on how often a surface
            // may replay its source. A pane over a *moving* backdrop — an animated canvas, a
            // scrolling list — otherwise re-records on every single frame, and nine of those on one
            // screen is the difference between a home that scrolls and one that crawls (measured on
            // a Galaxy S25: 44 ms per frame with one capture each, 61 ms with nine live ones).
            // Under a 10 dp blur a capture 100 ms old is indistinguishable from a fresh one, so the
            // interval buys back almost all of that cost and gives up nothing the eye can see.
            // Read unconditionally: the hold below needs a clock even when no resample
            // interval was asked for.
            val now = System.currentTimeMillis()
            val tooSoon = resampleIntervalMillis > 0L &&
                lastRecordedAt != 0L &&
                now - lastRecordedAt < resampleIntervalMillis &&
                // Una cattura di misura sbagliata non e' rinviabile: si vedrebbe subito.
                !surfaceDirty && recordedSize == recordSize
            // Held: see [frozen]. Only when there is something to hold — a surface frozen
            // before it ever recorded would draw an empty layer — and only while it has not
            // drifted too far, which is what keeps the correction at the end too small to see.
            val heldLongEnough =
                lastRecordedAt != 0L && now - lastRecordedAt >= HoldRefreshMillis
            val holding = frozen() &&
                sampled &&
                !heldLongEnough &&
                (!wanted || heldWhileMoving())
            // Coming out of a hold always takes a fresh capture, whether or not anything else
            // asks for one. Without this a held reflection could last for good: a backdrop
            // changes when it redraws, and a page that has stopped scrolling does not redraw.
            val refreshAfterHold = wasHolding && !holding
            wasHolding = holding

            // La sorgente e' sparita (pagina smontata) e il fondale nuovo non e' ancora arrivato: si
            // tiene la cattura vecchia invece di svuotarla. Vedi [holdsCaptureOfGoneSource].
            val ready = sampledBackdrop.isReadyToSample()
            if (ready) {
                notReadySince = 0L
            } else if (notReadySince == 0L) {
                notReadySince = now
            }
            val sourceGone = holdsCaptureOfGoneSource(ready, sampled, now - notReadySince)
            // Quando la sorgente torna (o ne arriva una nuova) si registra da capo, comunque.
            if (sourceGone) surfaceDirty = true

            val needsRecord = when {
                region != recordedRegion -> true
                lostLoan -> true
                sourceGone -> false
                sampled && (recordedScale != contentScale || recordedPipeline !== activeSample ||
                    recordedSampleScale != (activeSample?.sampleScale ?: 0f)) -> true
                renderState.optics == GlassOptics.Protected && !sampleOnce && moved && ready -> true
                holding -> false
                refreshAfterHold -> true
                else -> wanted && !tooSoon
            }
            // F8.10: perché la cattura si rifà o no, per leggere sul dispositivo un vetro rimasto a un fotogramma vecchio
            // (`adb shell setprop log.tag.FluidGlassFrame VERBOSE`, prima di aprire l'app).
            GlassFrameDiagnostics.event("decide", this@DrawBackdropNode) {
                "needs=$needsRecord wanted=$wanted dirty=$surfaceDirty size=$recordSize recorded=$recordedSize moved=$moved " +
                    "same=$sameSources sampled=$sampled ready=$ready gone=$sourceGone holding=$holding tooSoon=$tooSoon " +
                    "region=$region recordedRegion=$recordedRegion lostLoan=$lostLoan at=${layoutCoordinates?.takeIf { it.isAttached }?.positionInRoot()}" +
                    " container=${container?.let { System.identityHashCode(it) }} material=${containerMaterial?.let { System.identityHashCode(it) }}" +
                    " src=${describeSources()}"
            }

            if (filterNeedsCredit && !needsRecord) {
                val admission = reserveCapture(recordSize, activeSample)
                if (admission != CaptureAdmission.Ready) return admission
                try {
                    layer.renderEffect = effectScope.renderEffect
                    filterNeedsCredit = false
                    recorded(recordSize)
                } finally {
                    pending?.close()
                    pending = null
                }
            }

            if (needsRecord) {
                val admission = reserveCapture(recordSize, activeSample)
                if (admission != CaptureAdmission.Ready) return admission
                recordedRegion = region
                originalExtent = Size((fullWidth + border).toFloat(), (fullHeight + border).toFloat())
                try {
                    if (filterNeedsCredit) {
                        layer.renderEffect = effectScope.renderEffect
                        filterNeedsCredit = false
                    }
                    recordLayer(this@DrawBackdropNode, layer, size = recordSize, block = recordBackdropBlock)
                    recorded(recordSize)
                } finally {
                    pending?.close()
                    pending = null
                }
                rememberLoans()
                // Fluid Engine addition: a capture only *counts* if the backdrop had something to
                // give. `LayerBackdrop.drawBackdrop` returns silently when its coordinates are not
                // attached yet, and with `sampleOnce` that empty capture used to be kept for the
                // life of the node — a tile that came up in the wrong frame stayed a flat tint for
                // ever, with no path back. Now the bookkeeping waits for a valid one.
                if (sampledBackdrop.isReadyToSample()) {
                    // Compare logical geometry, never the size of the texture we chose ourselves.
                    // An unknown offset or a change of source is not evidence of motion.
                    if (sampled && onDrawLight != null && layerBlock == null &&
                        (size != recordedLogicalSize || sameSources && moved)) {
                        resolution.movedCapture(android.view.animation.AnimationUtils.currentAnimationTimeMillis())
                    }
                    recordedLogicalSize = size
                    GlassFrameDiagnostics.event("capture", this@DrawBackdropNode) {
                        // F6B.15: anche dove sta il vetro (nella radice), per riconoscerlo sul dispositivo.
                        "scale=$contentScale previous=${if (sampled) recordedScale else null} size=$recordSize window=$region at=${layoutCoordinates?.takeIf { it.isAttached }?.positionInRoot()}" +
                            " optics=${renderState.optics} sample=${activeSample?.sampleScale ?: contentScale} bytes=$residentBytes" +
                            " owner=${this@DrawBackdropNode.glassFrameOwner()} travel=${activeSample?.travelling == true}" +
                            " src=${describeSources()}"
                    }
                    recordedPipeline = activeSample
                    recordedSampleScale = activeSample?.sampleScale ?: 0f
                    recordedOptics = renderState.optics
                    recordedScale = contentScale
                    recordedSize = recordSize
                    // F8.10: un vetro su vetro che cattura muovendosi rispetto alle sue sorgenti riscrive lo strato di
                    // posa quando si ferma (vedi [forceLayerDefaults]).
                    if (containerMaterial != null && !sampleOnce && sameSources && moved) settlePlacementLater()
                    val previous = recordedSourceOffsets
                    recordedSourceOffsets = currentOffsets
                    currentOffsets = previous
                    surfaceDirty = false
                    sampled = true
                    lastRecordedAt = now
                    FluidGlassDiagnostics.onCapture()
                }
            }
        }
        return CaptureAdmission.Ready
    }

    /**
     * Senza una cattura da rigiocare. Prima di F7.18 il corpo si disegnava su un ingresso trasparente: tinta, sagoma e
     * filo sopra il niente, cioè vetro limpido sopra una pagina leggibile parola per parola (il popover della capsula
     * della data sul Tab S9, 24 novembre 2026). Ora, quando la cattura è **negata** dal tetto, il materiale è pieno: il
     * corpo su un ingresso pieno (la trasparenza ridotta della ricetta, `GlassBodyFallback`), oppure, dove il corpo non
     * c'è, il pieno del materiale ([BackdropEffectScopeImpl.deniedFill]) dentro la sagoma. Un'attesa breve della lettura
     * ([CaptureAdmission.AwaitReading], un fotogramma) resta com'era.
     */
    private fun DrawScope.drawWithoutCapture() {
        val body = effectScope.fallbackBody
        if (android.os.Build.VERSION.SDK_INT >= 33 && body.draw(this, effectiveScale, padding, solid = budgetDenied)) return
        if (!budgetDenied) return
        val solid = body.solid()
        val color = if (solid.isSpecified && solid.alpha > 0f) solid else effectScope.deniedFill
        if (!color.isSpecified || color.alpha <= 0f) return
        val shape = shapeProvider.innerShape
        // Una sagoma che la scolpisce lo shader (Fluid-physics) non ha un contorno da riempire qui.
        if (shape === dev.antigravity.fluidengine.ui.glass.field.GlassUnclippedShape) return
        GlassFrameDiagnostics.event("glassDeniedFill", this@DrawBackdropNode) { "color=$color" }
        drawOutline(shape.createOutline(size, layoutDirection, this), color)
    }

    private val drawBackdropLayer: DrawScope.() -> Unit = {
        if (!replay) drawWithoutCapture()
        val layer = if (replay) graphicsLayer else null
        if (layer != null) {
            val padding = padding
            // Not named `scale`: that shadows the DrawScope transform of the same name, which is
            // what un-scales the draw below.
            val contentScale = effectiveScale
            val origin = recordedRegion?.topLeft ?: IntOffset.Zero
            layer.topLeft = IntOffset(origin.x - padding.toInt(), origin.y - padding.toInt())
            // `topLeft` is in the layer's own (scaled) coordinates, so the un-scale has to wrap the
            // placement as well as the draw.
            if (contentScale != 1f) {
                scale(1f / contentScale, pivot = Offset.Zero) { drawLayer(layer) }
            } else {
                drawLayer(layer)
            }
        }
    }

    /**
     * F6B.15: la misura con cui lo strato della posa ha letto la forma, e il blocco di quella misura. Compose ricalcola il
     * contorno del ritaglio di uno strato quando il blocco gli dà una forma diversa, non quando lo strato cambia misura con
     * la stessa forma: sul Tab S9 la prima pillola della fila in alto si posava la prima volta larga 0 (`Rect(0, 0, 0, 51)`)
     * e il ritaglio restava quello. Il vetro usciva senza bordo né scritta (tagliati via) e il tocco non arrivava al chip
     * (fuori dal contorno: l'hit test si ferma allo strato). Un blocco nuovo a ogni cambio di misura (non a ogni fotogramma)
     * fa rileggere la forma, legata alla misura ([SizedClipShape]).
     */
    private var placedLayerSize = IntSize(-1, -1)

    /**
     * F8.10: la volta che lo strato di posa riscrive le sue proprietà a riposo ([forceLayerDefaults]). Uno **stato**,
     * scritto da [placementSettleCheck] e letto nel blocco dello strato: un `invalidateDraw()` chiesto da un `Handler` non
     * fa ridisegnare questo nodo (il tablet e Robolectric, 25 novembre), una scrittura di stato fa rifare il blocco.
     */
    private val placementSettle = mutableIntStateOf(0)
    private val placementSettleCheck = Runnable { if (isAttached) placementSettle.intValue++ }

    /** Riscrive lo strato di posa [PlacementSettleMillis] dopo l'ultima cattura in movimento (ognuna sposta l'attesa). */
    private fun settlePlacementLater() {
        MainHandler.removeCallbacks(placementSettleCheck)
        MainHandler.postDelayed(placementSettleCheck, PlacementSettleMillis)
    }
    private var sizedLayoutLayerBlock: GraphicsLayerScope.() -> Unit = layoutLayerBlock

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        updateReaderVisibility()
        val placeable = measurable.measure(constraints)
        val placed = IntSize(placeable.width, placeable.height)
        if (placed != placedLayerSize) {
            if (large(placed.width.toFloat(), placed.height.toFloat())) readBeforeAllocation()
            placedLayerSize = placed
            sizedLayoutLayerBlock = {
                // F8.10: dopo l'ultima cattura in movimento di un vetro su vetro, le proprietà si riscrivono davvero.
                if (placementSettle.intValue != 0) forceLayerDefaults(this)
                // F8.10, prova di debug `posa-proprieta` ([GlassStateDump]): lo strato di posa riscrive le proprietà.
                if (GlassStateDump.active) {
                    GlassStateDump.readPosePropertiesTick()
                    debugPoses++
                    GlassStateDump.forcePoseDefaults(this)
                }
                layoutLayerBlock()
            }
        }
        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(IntOffset.Zero, layerBlock = sizedLayoutLayerBlock)
            // Fluid Engine addition (F1A.7): il layer della posa nasce alla prima posa e vive fino al distacco.
            census.layer(FluidGlassLayerKind.Placement)
        }
    }

    override fun ContentDrawScope.draw() {
        updateReaderVisibility()
        if (readsSuspended) {
            drawContent()
            return
        }
        // Fluid Engine addition (4.0.0, F2.3c): uno scrittore di riserva arrivato dopo l'attacco (la chrome rifatta).
        adoptFallbacks()
        if (containerDirty) resolveContainer()
        val target = captureScale(size.width, size.height, commit = true)
        if (target != budgetTarget || size != budgetExtent) {
            if (size != budgetExtent) forgetRejectedUpgrade()
            budgetTarget = target
            budgetExtent = size
            budgetScale = 1f
            outputBudget = 1f
            desiredBytes = 0L
            if (waitForBytes == 0L) MainHandler.removeCallbacks(awaitBudget)
        }
        // F7.18: la precedenza al pannello davanti. Un vetro grande della pagina, mentre un pannello davanti negato chiede
        // spazio, si rifà una volta a [GlassYieldScale]; torna alla sua scala quando il pannello se ne va. La lettura dello
        // stato è osservata dal disegno: un cambio ridisegna questi vetri e nessun altro.
        front = currentValueOf(LocalGlassFrontLayer)
        budgetWake.intValue
        val yieldCap = yieldCap(size.width, size.height)
        // La pagina che cede restituisce subito la cattura grande, prima di prenotare quella piccola: tenere le due insieme
        // chiederebbe proprio lo spazio che il pannello davanti non trova.
        if (yieldCap < 1f && sampled && residentBytes > 0L && recordedScale > target * yieldCap) discardRecording()
        val previouslyReplayed = replay
        replay = true
        GlassFrameDiagnostics.event("draw", this@DrawBackdropNode)
        if (GlassStateDump.active) {
            lastDrawAt = System.currentTimeMillis()
            lastDrawSize = size
            debugDraws++
            // F8.10: letto in disegno, perché le prove di debug possano far ridisegnare questo vetro.
            GlassStateDump.readGlassTick()
            // F8.10, prova di debug `nascondi-vetro`: un vetro su vetro disegna solo il suo contenuto.
            if (GlassStateDump.readHideNestedGlass() && containerMaterial != null) {
                drawContent()
                return
            }
        }
        while (true) {
            val previous = effectiveScale
            val travel = sampleFor(size, target)
            val newPipeline = travel !== activeSample
            activeSample = travel
            // F7.18: col campione separato il tetto scende anche sull'uscita ([outputBudget]), non solo sul campione.
            effectiveScale = budgetedScale(target, travel != null, yieldCap)
            val sampleChanged = travel?.setBudgetScale(minOf(budgetScale, nativeRoleCeiling, yieldCap)) == true
            val changed = effectScope.update(this, effectiveScale)
            if (sampleChanged || newPipeline) surfaceDirty = true
            effectsUpdates.onDraw(scopeChanged = changed || sampleChanged || newPipeline)
            val admission = refreshCapture()
            lastAdmission = admission
            if (admission == CaptureAdmission.Ready) {
                if (nativeSampleLayer != null && travel !== nativeSample && !surfaceDirty) releaseNativeSample()
                break
            }
            if (admission == CaptureAdmission.Denied && front && !frontRequested) {
                frontRequested = true
                fastRetries = 0
                resources.frontDenied(this@DrawBackdropNode)
            }
            val attempted = travel?.sampleScale ?: effectiveScale
            // F7.18: il campione arriva al suo minimo uguale a vista, poi scende l'uscita, poi di nuovo il campione.
            if (admission == CaptureAdmission.Denied && travel != null &&
                attempted * 0.5f < frostedSampleFloor(effectScope.fallbackBody.inputs?.blurPx) &&
                effectiveScale > MinTravelOutputScale * target) {
                outputBudget = (effectiveScale * 0.5f).coerceAtLeast(MinTravelOutputScale * target)
                continue
            }
            if (admission == CaptureAdmission.Denied && attempted > MinBackdropScale) {
                budgetScale = (attempted * 0.5f).coerceAtLeast(MinBackdropScale)
                continue
            }
            if (admission == CaptureAdmission.Denied && residentBytes > 0L && !glassMoving() &&
                sampledBackdrop.isReadyToSample()) {
                // La vecchia cattura può occupare da sola tutta la nuova quota. Alla posa
                // si svuota la sua registrazione e si ritenta nello stesso disegno.
                discardRecording()
                budgetScale = minOf(target, renderState.optics.backgroundScaleCeiling)
                outputBudget = 1f
                continue
            }
            if (admission == CaptureAdmission.Denied && (waitForBytes == 0L || nextBytes < waitForBytes)) {
                if (waitForBytes == 0L) MainHandler.postDelayed(awaitBudget, retryDelayMillis())
                waitForBytes = nextBytes
            }
            replay = sampled
            effectiveScale = if (sampled) recordedScale else previous
            effectsUpdates.onDraw(scopeChanged = effectScope.update(this, effectiveScale))
            break
        }
        if (replay != previouslyReplayed) exportDirty = true
        onDrawBehind?.invoke(this)
        drawBackdropLayer()
        onDrawSurface?.invoke(this)
        // Fluid Engine addition (F1A.6): la luce, sopra il materiale e sotto il contenuto. Non entra
        // nell'export qui sotto: vedi `drawBackdrop(onDrawLight)`.
        val light = onDrawLight
        if (light is GlassLightDraw) {
            val environment = currentValueOf(LocalGlassLightEnvironment)?.takeIf {
                (renderState.optics == GlassOptics.Full || !glassMoving()) &&
                currentValueOf(LocalFluidGlassMotionLight) && !currentValueOf(LocalFluidMotionPolicy).reducedMotion
            }
            // Una superficie entra in scena col suo primo draw, non durante il prefetch della lista.
            bindMotionLight(environment)
            light(this, environment?.angle ?: 0f)
        } else {
            light?.invoke(this)
        }
        drawContent()
        onDrawFront?.invoke(this)

        // Il materiale pubblicato: tutto quello che questa superficie disegna *tranne* il
        // contenuto. Il contenuto resta fuori apposta, e non per risparmio: dentro c'e' il vetro che
        // campiona questo materiale, e un tasto che rifrange se stesso e' il feedback ottico che
        // nel vetro non si nasconde. Registrato solo se qualcuno lo guarda (2.13.0): un pannello
        // senza vetro dentro non paga niente.
        val export = exportedBackdrop ?: ownMaterial
        if (export != null && export.wantsRecording) {
            val stale = exportDirty ||
                export !== exportedInto ||
                exportedWidth != size.width ||
                exportedHeight != size.height ||
                exportedPadding != padding ||
                exportedScale != effectiveScale
            if (stale) {
                exportDirty = false
                exportedInto = export
                exportedWidth = size.width
                exportedHeight = size.height
                exportedPadding = padding
                exportedScale = effectiveScale
                val observer = exportObserver
                    ?: SnapshotStateObserver(OnMainThread).also {
                        it.start()
                        exportObserver = it
                    }
                observer.observeReads(this@DrawBackdropNode, onExportReadChanged) {
                    recordLayer(this@DrawBackdropNode, export.graphicsLayer) {
                        onDrawBehind?.invoke(this)
                        drawBackdropLayer()
                        onDrawSurface?.invoke(this)
                        onDrawFront?.invoke(this)
                    }
                }
                FluidGlassDiagnostics.onExport()
                // Fluid Engine addition (4.0.0, F2.3): l'export avvisa chi lo legge (la sonda dei vetri che stanno
                // sopra questo pannello) che si e' registrato di nuovo, come fa la pagina. Senza, quei vetri lo
                // leggevano a cadenza fissa per sempre.
                export.bumpRevision()
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            lastCoordinates = coordinates
            if (sampledBackdrop.isCoordinatesDependent) {
                layoutCoordinates = coordinates
            } else {
                if (layoutCoordinates != null) {
                    layoutCoordinates = null
                }
            }
            exportedBackdrop?.takeIf { it.ownerNode === this }?.layerCoordinates = coordinates
            ownMaterial?.layerCoordinates = coordinates
        }
    }

    override fun onObservedReadsChanged() {
        invalidateDrawCache()
    }

    fun invalidateDrawCache() {
        observeEffects()
    }

    private fun observeEffects() {
        // F3: accorpa le notifiche prima del prossimo draw. Gli effetti immutabili prendono l'ultimo campione
        // del frame, insieme alla luce; nessuna catena intermedia viene installata fra due disegni.
        effectsUpdates.invalidate()
        if (isAttached) {
            invalidateDraw()
            // F3 (f3-corpo): il draw chiesto qui può non arrivare (il palco in viaggio su un fondo fermo): la catena si
            // costruisce comunque prima del fotogramma. Vedi [BackdropEffectsUpdates.beforeFrame].
            requestBeforeFrame()
        }
    }

    // --- F3 (f3-corpo): la catena prima del fotogramma, anche senza il draw del nodo ---------------------------------

    /** La vista con il pre-draw iscritto, o null: uno per nodo, una tantum, fino al prossimo fotogramma. */
    private var beforeFrameView: View? = null

    private val beforeFrameListener = ViewTreeObserver.OnPreDrawListener {
        cancelBeforeFrame()
        if (isAttached) {
            val built = effectsUpdates.beforeFrame(scopeCurrent = effectScopeIsCurrent())
            GlassFrameDiagnostics.event("beforeFrame", this@DrawBackdropNode) { "built=$built" }
        }
        true
    }

    private fun requestBeforeFrame() {
        if (beforeFrameView != null) return
        val view = requireView()
        view.viewTreeObserver.addOnPreDrawListener(beforeFrameListener)
        beforeFrameView = view
    }

    private fun cancelBeforeFrame() {
        val view = beforeFrameView ?: return
        beforeFrameView = null
        // L'osservatore della finestra, quello in cui il listener è finito anche se iscritto prima dell'attacco della vista.
        val observer = view.viewTreeObserver
        if (observer.isAlive) observer.removeOnPreDrawListener(beforeFrameListener)
    }

    /**
     * Se lo scope degli effetti è quello che il draw di questo fotogramma troverebbe: la stessa taglia, densità, scala del
     * testo, direzione e scala della cattura dell'ultimo [effectScope] aggiornato nel draw. Mai vero prima del primo draw
     * (lo scope non ha taglia). Stessa aritmetica di `BackdropEffectScopeImpl.update` e di [draw], a numeri uguali.
     */
    private fun effectScopeIsCurrent(): Boolean {
        val scoped = effectScope.size
        if (!scoped.isSpecified) return false
        val coordinates = lastCoordinates?.takeIf { it.isAttached } ?: return false
        val width = coordinates.size.width.toFloat()
        val height = coordinates.size.height.toFloat()
        val target = captureScale(width, height, commit = false)
        val contentScale = budgetedScale(target, activeSample != null, yieldCap(width, height))
        if (contentScale != effectiveScale) return false
        val density = requireDensity()
        val size = if (contentScale != 1f) Size(width * contentScale, height * contentScale) else Size(width, height)
        return scoped == size &&
            effectScope.density == density.density * contentScale &&
            effectScope.fontScale == density.fontScale &&
            effectScope.layoutDirection == requireLayoutDirection()
    }

    private fun updateEffects() {
        if (!isRenderEffectSupported()) return

        effectScope.fallbackBody.shader = null
        effectScope.apply(activeSample?.takeIf { it === nativeSample }?.effects ?: effects)
        graphicsLayer?.let { layer ->
            val effect = effectScope.renderEffect
            // Un filtro può comparire su una registrazione già valida, anche dal pre-draw.
            // Prima di installarlo il draw ammette lo scratch, senza ricatturare la sorgente ferma.
            filterNeedsCredit = effect != null && layer.renderEffect == null && sampled && !residentFiltered &&
                (residentBytes > 0L || residentSize.width.toLong() * residentSize.height >=
                    LargeCapturePixels.toLong() * 4 / (2 * bytesPerPixel))
            if (filterNeedsCredit) invalidateDraw()
            else if (layer.renderEffect !== effect) layer.renderEffect = effect
            // Il clip delimita l'uscita del filtro HWUI, anche quando il layer viene rigiocato nel velo.
            // Senza questo limite Skia può allocare un intermedio grande quanto l'intera finestra.
            val size = effectScope.size
            val edge = (effects as? BottomEdgeBackdropEffects)?.takeIf {
                effect != null && size.isSpecified && effectScope.padding == 0f
            }
            val window = effects as? WindowedBackdropEffects
            val crop = edge != null || (effect != null && window?.region != null) || window?.region == IntRect.Zero
            if (edge != null) {
                val y = (edge.top() * if (edge.fromEdgeProfile) 1f else effectiveScale).coerceIn(0f, size.height)
                layer.setRectOutline(Offset(0f, y), Size(size.width, size.height - y))
            } else if (window?.region == IntRect.Zero) {
                layer.setRectOutline(Offset.Zero, Size.Zero)
            } else if (layer.clip || crop) {
                // Ripristina l'intero rettangolo del layer anche nel passaggio da striscia a finestra.
                layer.setRectOutline()
            }
            if (layer.clip != crop) layer.clip = crop
        }
        GlassFrameDiagnostics.event("installed", this) { "effect=${System.identityHashCode(effectScope.renderEffect)}" }
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
        val view = requireView()
        readsSuspended = false
        filterNeedsCredit = false
        window = glassWindowFor(view, true)
        resources = FluidGlassResources.get(view.context)
        windowLease = window?.let(resources::acquire)
        renderState = resources.renderState(window)
        bytesPerPixel = if (window?.isWideColorGamut == true) 8 else 4
        val graphicsContext = requireGraphicsContext()
        graphicsLayer = graphicsContext.createGraphicsLayer()
        // Fluid Engine addition (F1A.7): il nodo, la sua cattura e la superficie, nella diagnostica.
        census.attached()
        census.layer(FluidGlassLayerKind.Capture)
        recountSurface()
        surfaceDirty = true
        recordedSize = IntSize.Zero
        recordedSourceOffsets.clear()
        currentOffsets.clear()

        bindExport()
        registerReads()
        adoptFallbacks()
        resolveContainer()
        // E come contenitore: il vetro che c'era gia' dentro adesso sta su questo pannello.
        adoptNestedReaders()

        observeEffects()
        // F8.10: la fotografia a riposo, solo in un'app debuggable ([GlassStateDump]).
        GlassStateDump.install(view.context)
        GlassStateDump.register(this)
    }

    /**
     * Fluid Engine addition: a lazy list reuses the *node* for another item, and everything this
     * node remembers about the capture belongs to the item that is gone. Without this, a recycled
     * tile of the same size inherited the previous tile's picture — taken where the previous tile
     * was — and `needsRecord` had no reason to ever fix it. From the outside it looked like "the
     * glass stopped updating", and sometimes like "this tile has no glass at all".
     */
    override fun onReset() {
        forgetRejectedUpgrade()
        releaseFrontDemand()
        outputBudget = 1f
        bindMotionLight(null)
        if (residentBytes > 0L) discardRecording()
        if (budgetDenied) resources.visibleCaptureDenied(window, false)
        budgetDenied = false
        budgetTarget = Float.NaN
        desiredBytes = 0L
        waitForBytes = 0L
        MainHandler.removeCallbacks(awaitBudget)
        surfaceDirty = true
        containerDirty = true
        exportDirty = true
        sampled = false
        notReadySince = 0L
        lastRecordedAt = 0L
        recordedSize = IntSize.Zero
        recordedSourceOffsets.clear()
        currentOffsets.clear()
    }

    override fun onDetach() {
        GlassStateDump.unregister(this)
        MainHandler.removeCallbacks(placementSettleCheck)
        forgetRejectedUpgrade()
        releaseFrontDemand()
        if (residentBytes > 0L) GlassFrameDiagnostics.event("glassRelease", this) { "bytes=$residentBytes detach=true" }
        bindMotionLight(null)
        if (recoveryPending) MainHandler.removeCallbacks(recoverResolution)
        recoveryPending = false
        resolution.reset()
        cancelBeforeFrame()
        borrowedSources.clear()
        borrowedShells.clear()
        recordedSourceOffsets.clear()
        currentOffsets.clear()
        sampled = false
        val graphicsContext = requireGraphicsContext()
        graphicsLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            graphicsLayer = null
        }

        activeSample = null
        recordedPipeline = null
        releaseNativeSample()
        resident?.close()
        resident = null
        residentSize = IntSize.Zero
        residentBytes = 0L
        residentSample = null
        budgetScale = 1f
        outputBudget = 1f
        budgetTarget = Float.NaN
        budgetExtent = Size.Zero
        allocationPrepared = false
        waitForBytes = 0L
        desiredBytes = 0L
        MainHandler.removeCallbacks(awaitBudget)
        MainHandler.removeCallbacks(refreshAllocationReading)
        refreshPosted = false
        if (budgetDenied) resources.visibleCaptureDenied(window, false)
        budgetDenied = false
        windowLease?.close()
        windowLease = null
        window = null
        effectScope.reset()
        layoutCoordinates = null
        lastCoordinates = null
        unbindExport()
        exportObserver?.let {
            it.stop()
            it.clear()
        }
        exportObserver = null
        exportedInto = null
        exportDirty = true

        // Come lettrice: non campiona piu' niente.
        unregisterReads()
        leaveContainer()
        sampledBackdrop = backdrop
        // Fluid Engine addition (F1A, voce 18): al prossimo attacco la domanda si rifa' da capo.
        standsOnGlass = false

        // Come contenitore: chi ci stava sopra rifa' la domanda al prossimo disegno, e il layer del
        // materiale torna al contesto (chi lo sta ancora disegnando lo tiene vivo finche' serve).
        val readers = nestedReaders.toList()
        nestedReaders.clear()
        readers.forEach { it.onContainerChanged() }
        ownMaterial?.let { material ->
            material.layerCoordinates = null
            material.onFirstReader = null
            graphicsContext.releaseGraphicsLayer(material.graphicsLayer)
        }
        ownMaterial = null
        // Fluid Engine addition (F1A.7): cattura, posa e materiale sono rilasciati; si restituisce cio' che si
        // era contato.
        census.detached()
    }

    // --- Fluid Engine addition (2.13.0): vetro su vetro -----------------------------------------

    /** Il layer in cui questa superficie esporta: si fa conoscere come chi lo registra. */
    fun bindExport() {
        val export = exportedBackdrop ?: return
        if (!isAttached) return
        export.ownerNode = this
        export.onFirstReader = wakeExport
        lastCoordinates?.let { if (it.isAttached) export.layerCoordinates = it }
        // Chi stava sul materiale proprio passa a quello esportato.
        notifyNestedReaders()
    }

    fun unbindExport() {
        val export = exportedBackdrop ?: return
        if (export.ownerNode === this) {
            export.layerCoordinates = null
            export.ownerNode = null
        }
        if (export.onFirstReader === wakeExport) export.onFirstReader = null
        notifyNestedReaders()
    }

    private fun notifyNestedReaders() {
        if (nestedReaders.isEmpty()) return
        nestedReaders.toList().forEach { it.onContainerChanged() }
    }

    /** Conta questa superficie fra i lettori dei layer su richiesta che campiona. */
    fun registerReads() {
        if (!isAttached || readsSuspended || registeredReads.isNotEmpty()) return
        val reads = ArrayList<LayerBackdrop>(0)
        backdrop.forEachPart { part ->
            if (part is LayerBackdrop && part.onDemand && part !in reads) reads += part
        }
        reads.forEach { it.addReader() }
        registeredReads = reads
    }

    fun unregisterReads() {
        registeredReads.forEach { it.removeReader() }
        registeredReads = emptyList()
        releaseFallbacks()
    }

    // --- Fluid Engine addition (4.0.0, F2.3c): lo scrittore di riserva ---------------------------------------

    private var fallbackAdopted: LayerBackdrop? = null
    private var fallbackWriter: LayerFallbackWriter? = null

    /**
     * Un vetro **fuori** dalla pagina (la tab bar, un modale, un menu montati dalla chrome) chiede alla chrome della
     * sua finestra la registrazione di riserva quando la pagina non pubblica il fondale. Dentro la pagina mai: campionerebbe una
     * registrazione che contiene sé stesso. Vedi `LayerBackdrop.chromeFallback`.
     */
    fun adoptFallbacks() {
        if (!isAttached || readsSuspended) return
        // Un vetro dentro le pagine non chiede mai la riserva: campionerebbe una registrazione che contiene sé stesso.
        // Dalla 4.0 G1 «dentro» vuol dire sotto il nodo scrittore (`Modifier.fluidChromePages`), non un CompositionLocal.
        val writer = if (findNearestAncestor(LayerFallbackWriterKey) == null)
            LayerFallbackWriters.of(currentValueOf(LocalView).rootView) else null
        if (writer === fallbackWriter) return
        releaseFallbacks()
        if (writer == null) return
        // Solo lo sfondo di base. Gli export sovrapposti conservano il loro produttore.
        val base = backdrop.layerSources.firstOrNull() as? LayerBackdrop ?: return
        writer.adopt(base)
        fallbackAdopted = base
        fallbackWriter = writer
    }

    private fun releaseFallbacks() {
        val writer = fallbackWriter ?: return
        fallbackAdopted?.let { writer.release(it) }
        fallbackAdopted = null
        fallbackWriter = null
    }

    private fun leaveContainer() {
        containerMaterial?.removeReader()
        containerMaterial = null
        container?.forgetReader(this)
        container = null
    }

    override fun onContainerChanged() {
        if (!isAttached) return
        // La domanda si rifa' al prossimo disegno: chi avvisa puo' essere a meta' di un distacco,
        // e un albero a meta' non e' un posto dove cercare antenati.
        containerDirty = true
        invalidateDraw()
    }

    /**
     * Su quale pannello sta questa superficie, e come se ne campiona il materiale.
     *
     * Rifatta all'attacco, quando il fondale cambia, e quando il pannello di sopra cambia. Mai a ogni
     * fotogramma: la risposta dipende dall'albero, non dal disegno.
     */
    fun resolveContainer() {
        containerDirty = false
        if (!isAttached || readsSuspended) return
        val found: GlassContainer? =
            if (nestInContainer && isRenderEffectSupported()) {
                findNearestAncestor(GlassContainerKey) as? GlassContainer
            } else {
                null
            }
        // Il fondale vuoto non ha niente da disegnare, e in una pila non serve.
        val layers = backdrop.stackedBackdrops.filter { it !== EmptyStack }
        val published = found?.publishedMaterial
        val plan = planNesting(
            layers = layers,
            nestable = nestInContainer,
            hasContainer = found != null,
            includesMaterial = published != null && backdrop.containsPart(published),
            isInside = { layer -> found != null && layer.liesInside(found) },
        )
        val next: Backdrop = when (plan) {
            NestingPlan.Alone, NestingPlan.AlreadyIncluded -> {
                leaveContainer()
                backdrop
            }
            is NestingPlan.Insert -> {
                val target = found!!
                if (target !== container) leaveContainer()
                val material = target.materialFor(this)
                if (material == null) {
                    leaveContainer()
                    backdrop
                } else {
                    if (material !== containerMaterial) {
                        containerMaterial?.removeReader()
                        material.addReader()
                        containerMaterial = material
                    }
                    container = target
                    NestedBackdrop(layers, material, plan.index)
                }
            }
        }
        if (!sameSampling(next, sampledBackdrop)) {
            sampledBackdrop = next
            surfaceDirty = true
            lastCoordinates?.let { coordinates ->
                if (coordinates.isAttached && next.isCoordinatesDependent) layoutCoordinates = coordinates
            }
            invalidateDraw()
        }
        // Fluid Engine addition (F1A, voce 18): la stessa risposta, per gli effetti che la leggono. Cambia di
        // rado (all'attacco, quando il pannello di sopra compare o se ne va): gli effetti si rifanno solo allora,
        // solo se hanno gia' una taglia (altrimenti li fa il primo disegno) e solo per il materiale nuovo, l'unico
        // che la legge (passa la luce): il vetro di prima non ricostruisce niente in piu'.
        val onGlass = glassStandsOnGlass(plan, inserted = next is NestedBackdrop)
        if (onGlass != standsOnGlass) {
            standsOnGlass = onGlass
            if (onDrawLight != null && effectScope.size.isSpecified) observeEffects()
            invalidateDraw()
        }
    }

    override val publishedMaterial: LayerBackdrop? get() = exportedBackdrop ?: ownMaterial

    override fun materialFor(reader: GlassContainerReader): LayerBackdrop? {
        if (!isAttached || !isRenderEffectSupported()) return null
        val material = exportedBackdrop ?: ownMaterial ?: createOwnMaterial()
        if (reader !in nestedReaders) nestedReaders += reader
        return material
    }

    override fun forgetReader(reader: GlassContainerReader) {
        nestedReaders -= reader
    }

    private fun createOwnMaterial(): LayerBackdrop {
        val layer = requireGraphicsContext().createGraphicsLayer()
        // Fluid Engine addition (F1A.7): il materiale proprio, solo quando del vetro dentro lo campiona.
        census.layer(FluidGlassLayerKind.Material)
        val material = materialLayerBackdrop(layer)
        material.ownerNode = this
        material.onFirstReader = wakeExport
        lastCoordinates?.let { if (it.isAttached) material.layerCoordinates = it }
        ownMaterial = material
        return material
    }
}

/**
 * Dove arrivano le notifiche di uno stato cambiato: sul thread principale, che e' l'unico da cui si
 * puo' chiedere un nuovo disegno. Quasi sempre ci sono gia'.
 */
private val MainHandler = Handler(Looper.getMainLooper())
private val OnMainThread: (() -> Unit) -> Unit = { callback ->
    if (Looper.myLooper() === Looper.getMainLooper()) callback() else MainHandler.post(callback)
}

/** Il fondale vuoto: in una pila non disegna niente, quindi non ci entra. */
private val EmptyStack: Backdrop = emptyBackdrop()

/** Due fondali che campionano la stessa cosa, per non sporcare una cattura per niente. */
private fun sameSampling(a: Backdrop, b: Backdrop): Boolean {
    if (a === b) return true
    if (a !is NestedBackdrop || b !is NestedBackdrop) return false
    return a.material === b.material && a.insertAt == b.insertAt && a.layers == b.layers
}

/**
 * LOCAL CHANGE (F1B, giro di correzione unico; voce 20 di `LICENSES/AndroidLiquidGlass.md`): **quando** il nodo rifà gli
 * effetti, e sotto quale osservazione.
 *
 * A monte gli effetti girano in tre momenti: all'attacco e quando cambia uno stato letto in un giro precedente, sotto
 * `observeReads` (le letture del giro sono quelle che lo rifaranno); e nel disegno, quando lo scope cambia (la taglia,
 * la densità, la scala della cattura), **fuori** dall'osservazione. All'attacco la taglia non c'è ancora: un effetto
 * che senza taglia esce presto (il corpo del vetro nuovo, `GlassLiquidSurface.fillBody`; la superficie fisica,
 * `PhysicsLiquidSurface.fill`) legge in quel giro poco o niente, e il giro del disegno, che legge tutto, non iscriveva
 * niente. Da lì intensità, profondità, qualità, pressione, pellicola, tinta, miscele, la lente in moto potevano
 * cambiare senza che la catena si rifacesse: la luce (che si ridisegna) andava avanti, il corpo restava fermo. È la
 * radice della barra senza vetro nei ritorni (riga 17 della sessione sul tablet dopo F1B.2) e dei rilievi R1 e 7 dei
 * critici della fase.
 *
 * Qui ogni giro passa da [observe]: anche quello del disegno, e anche quello che apre un ramo nuovo (una superficie
 * che si accende legge, nel giro che la accende, gli ingressi che prima non leggeva). Le letture osservate sono
 * sempre quelle dell'ultimo giro. Per il vetro di sempre (`Classic`) gli effetti producono la stessa catena di prima a
 * parità di stato: cambia solo che, quando uno stato letto dopo la taglia cambia, la catena si rifà.
 *
 * F3: le notifiche segnano la catena come sporca e il nodo chiede un draw. La costruzione si fa una volta sola
 * nel draw, dopo l'aggiornamento dello scope: più scritture applicate fra due frame non costruiscono catene
 * intermedie. Il giro resta osservato anche quando il ramo degli effetti cambia. La cattura si invalida solo
 * se cambia il padding, come prima: un nuovo uniform non fa registrare di nuovo lo sfondo.
 *
 * F3 (f3-corpo): **il draw del nodo non è garantito.** La catena è una proprietà del layer della cattura, che il
 * renderer applica anche se il nodo non si ridisegna, e Compose non ridisegna sempre il nodo che lo chiede: sul tablet,
 * per tutto il viaggio del palco su un fondo fermo, il nodo riceveva un avviso a fotogramma e chiedeva il draw, e il draw
 * non arrivava fino all'arrivo (`sessioni/f3-corpo/`). Costruita solo nel draw, la catena restava quella di partenza:
 * il corpo congelato. Per questo c'è [beforeFrame]: il nodo lo chiama dal pre-draw della finestra, l'ultimo istante prima
 * che il fotogramma si disegni, dopo i callback del fotogramma e le applicazioni di Compose. Lì una catena sporca si
 * costruisce con l'ultimo campione **se lo scope è quello del fotogramma** (stessa taglia, densità, direzione e scala
 * dell'ultimo draw); altrimenti la lascia al draw, che con una taglia nuova arriva comunque. Un fotogramma che ridisegna
 * anche il nodo la trova già fatta: resta una costruzione per fotogramma.
 *
 * Una classe e non due righe nel nodo perché la regola si prova sul JVM con un osservatore vero
 * (`BackdropEffectsUpdatesTest`), senza un nodo attaccato.
 *
 * @param observe esegue il blocco iscrivendone le letture (nel nodo, `observeReads`).
 * @param rebuild rifà gli effetti (nel nodo, `updateEffects`).
 */
internal class BackdropEffectsUpdates(
    private val observe: (block: () -> Unit) -> Unit,
    private val rebuild: () -> Unit,
) {
    private var dirty = true

    /** All'attacco, all'aggiornamento del nodo o quando cambia un ingresso; il chiamante chiede il draw e il pre-draw. */
    fun invalidate() {
        dirty = true
    }

    /** La costruzione osservata; il nodo la raggiunge da [beforeFrame] e da [onDraw]. Resta disponibile ai modelli di test. */
    fun refresh() {
        dirty = false
        observe(rebuild)
    }

    /**
     * Prima che il fotogramma si disegni (il pre-draw della finestra), anche se il nodo non verrà ridisegnato: una catena
     * sporca si costruisce qui con l'ultimo campione, se [scopeCurrent] (lo scope è quello che il draw di questo
     * fotogramma troverebbe). Senza scope corrente, o mai disegnata, resta sporca per [onDraw].
     *
     * @return se ha costruito.
     */
    fun beforeFrame(scopeCurrent: Boolean): Boolean {
        if (!dirty || !scopeCurrent) return false
        refresh()
        return true
    }

    /** L'ultimo campione e la taglia del frame, in un solo giro **osservato** prima di disegnare il corpo. */
    fun onDraw(scopeChanged: Boolean) {
        if (dirty || scopeChanged) {
            refresh()
        }
    }
}

/**
 * F6B.15: [shape] alla misura della posa ([size], quella del placeable che `DrawBackdropNode` posa col suo strato). Due
 * misure diverse sono due forme diverse, e il contorno si calcola con la misura della posa, non con quella che Compose dà
 * al blocco dello strato: per la prima pillola della fila in alto sul Tab S9 il blocco girava una volta sola, con lo scope a
 * `0 × 51` (il contenuto non ancora alla sua misura) e la posa già a `131 × 96`; poi Compose ridimensionava lo strato senza
 * rieseguire il blocco, e il ritaglio restava `Rect(0, 0, 0, 51)`. Senza una misura di posa, quella dello scope.
 */
private data class SizedClipShape(val shape: Shape, val size: IntSize) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val placed = this.size
        val outlineSize = if (placed.width >= 0 && placed.height >= 0) {
            androidx.compose.ui.geometry.Size(placed.width.toFloat(), placed.height.toFloat())
        } else {
            size
        }
        return shape.createOutline(outlineSize, layoutDirection, density)
    }
}
