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

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
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

private class LayerBackdropElement(
    val backdrop: LayerBackdrop,
    val frozen: () -> Boolean,
) : ModifierNodeElement<LayerBackdropNode>() {

    override fun create(): LayerBackdropNode {
        return LayerBackdropNode(backdrop, frozen)
    }

    override fun update(node: LayerBackdropNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.layerCoordinates = null
            node.backdrop = backdrop
        }
        node.frozen = frozen
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
        if (frozen != other.frozen) return false

        return true
    }

    override fun hashCode(): Int {
        return 31 * backdrop.hashCode() + frozen.hashCode()
    }
}

private class LayerBackdropNode(
    var backdrop: LayerBackdrop,
    var frozen: () -> Boolean,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    /** Whether there is a capture to keep. The first frame always records. */
    private var recorded = false

    override fun ContentDrawScope.draw() {
        drawContent()
        // Held still: the tree is drawn the ordinary way, which reuses every child that did not
        // change, and the glass goes on sampling the capture from before the movement started.
        if (recorded && frozen()) return
        recordLayer(this@LayerBackdropNode, backdrop.graphicsLayer) { backdrop.onDraw(this@draw) }
        recorded = true
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.layerCoordinates = coordinates
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
        backdrop.layerCoordinates = null
    }
}
