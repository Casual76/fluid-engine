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
package dev.antigravity.fluidengine.ui.glass.backdrop.backdrops

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.recordLayer

fun Modifier.layerBackdrop(
    backdrop: LayerBackdrop,
    /**
     * While this answers true the subtree is not recorded again, and the glass sampling it keeps
     * the capture it already has.
     *
     * Recording is a full traversal of the subtree, and its cost scales with the number of draw
     * operations in it rather than with pixels — so a list being flung pays for every row it holds
     * on every frame, on top of drawing them. That traversal is the dominant cost of a scroll.
     *
     * What freezing costs is a reflection that stops following while the content slides past.
     * Under the blur a pane applies, that is close to invisible; what it buys back is most of a
     * frame.
     */
    frozen: () -> Boolean = { false },
): Modifier = this then LayerBackdropElement(backdrop, frozen)

/** Il velo pubblicato è anche quello visibile. Registrarlo e riprodurlo disegna il velo una volta sola. */
internal fun Modifier.scrimBackdrop(backdrop: LayerBackdrop): Modifier =
    this then LayerBackdropElement(backdrop, { false }, replay = true)

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val frozen: () -> Boolean,
    val replay: Boolean = false,
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, frozen, replay)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            if (node.isAttached) node.unbind()
            node.backdrop = backdrop
            if (node.isAttached) node.bind()
        }
        node.frozen = frozen
        node.replay = replay
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "layerBackdrop"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is LayerBackdropElement) return false

        if (backdrop != other.backdrop) return false
        if (frozen != other.frozen || replay != other.replay) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * (31 * backdrop.hashCode() + frozen.hashCode()) + replay.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var frozen: () -> Boolean,
    var replay: Boolean,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    // Un'identità diversa a ogni riapertura. Un vecchio RenderNode può ancora trattenere
    // la cattura precedente durante il pop: Compose la libera all'ultimo riferimento.
    var page: LayerBackdrop? by mutableStateOf(null)
        private set
    private var coordinates: LayoutCoordinates? = null
    var recorded by mutableStateOf(false)
        private set

    fun updateDemand() {
        if (!backdrop.onDemand) return
        if (backdrop.readerCount > 0) {
            if (page != null) return
            val layer = requireGraphicsContext().createGraphicsLayer()
            layer.compositingStrategy = backdrop.graphicsLayer.compositingStrategy
            page = LayerBackdrop(layer, backdrop.onDraw).also { it.layerCoordinates = coordinates }
            recorded = false
        } else {
            if (page == null) return
            retirePage()
        }
        invalidateDraw()
    }

    private fun retirePage() {
        page?.let {
            page = null
            it.layerCoordinates = null
            requireGraphicsContext().releaseGraphicsLayer(it.graphicsLayer)
        }
        recorded = false
    }

    override fun ContentDrawScope.draw() {
        // Fluid Engine addition (4.0.0, F2.3c): chi si disegna qui dentro sta nella registrazione di questo layer, e
        // quando poi si registra di nuovo da solo lo avvisa (vedi `LayerRecordingStack`).
        LayerRecordingStack.enter(backdrop)
        try {
            // Due pagine possono sovrapporsi durante il push. Se la più recente è uscita,
            // quella che torna a disegnarsi riprende la sorgente con le proprie coordinate.
            if (backdrop.onDemand && backdrop.ownerNode == null) bind()
            val target = if (backdrop.onDemand) page else backdrop
            if (target == null) {
                drawContent()
                return
            }
            if (!replay) drawContent()
            // Held still: the tree is drawn the ordinary way, which reuses every child that did not
            // change, and the glass goes on sampling the capture from before the movement started.
            if (recorded && frozen()) return
            recordLayer(this@LayerBackdropNode, target.graphicsLayer) { backdrop.onDraw(this@draw) }
            recorded = true
            if (replay) {
                FluidGlassDiagnostics.onExport()
                drawLayer(target.graphicsLayer)
            }
        } finally {
            LayerRecordingStack.exit(backdrop)
        }
        // Fluid Engine addition (4.0.0, F2.1): chi campiona questo layer non ricattura quando cambia,
        // quindi lo si dice a chi vuole saperlo (la sonda dello sfondo). Tenuto fermo, non avvisa.
        backdrop.bumpRevision()
    }

    fun bind() {
        if (backdrop.onDemand) (backdrop.ownerNode as? LayerBackdropNode)?.retirePage()
        backdrop.ownerNode = this
        backdrop.layerCoordinates = coordinates
        updateDemand()
    }

    fun unbind() {
        if (backdrop.ownerNode === this) {
            backdrop.layerCoordinates = null
            backdrop.ownerNode = null
        }
        retirePage()
    }

    override fun onAttach() = bind()

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
        if (coordinates.isAttached && backdrop.ownerNode === this) {
            backdrop.layerCoordinates = coordinates
            page?.layerCoordinates = coordinates
        }
    }

    /**
     * Fluid Engine addition: a reused node has nothing to do with the subtree that was here before,
     * and `recorded` (the flag that makes `frozen` keep the registration) belonged to that one.
     */
    override fun onReset() {
        recorded = false
    }

    override fun onDetach() {
        unbind()
        coordinates = null
    }
}

/**
 * Fluid Engine addition (4.0.0, F2.1): se chi registra [this] adesso e' un `layerBackdrop`, cioe' un
 * nodo che avvisa a ogni nuova registrazione (vedi `LayerBackdrop.revision`). Sta qui perche' il nodo
 * e' privato a questo file.
 */
internal fun LayerBackdrop.isRecordedByLayerBackdropNode(): Boolean = ownerNode is LayerBackdropNode

internal fun LayerBackdrop.updatePageDemand() {
    (ownerNode as? LayerBackdropNode)?.updateDemand()
}

/** Un export o un layer passato dall'app mantiene la sua identità. Solo la pagina gestita dal
 * nodo presta una registrazione privata, con la durata dei suoi lettori. */
internal fun LayerBackdrop.pageSourceOrSelf(): LayerBackdrop? {
    val writer = ownerNode as? LayerBackdropNode ?: return this
    return if (onDemand) writer.page else this
}

internal fun LayerBackdrop.hasPageRecording(): Boolean =
    !onDemand || (ownerNode as? LayerBackdropNode)?.recorded != false
