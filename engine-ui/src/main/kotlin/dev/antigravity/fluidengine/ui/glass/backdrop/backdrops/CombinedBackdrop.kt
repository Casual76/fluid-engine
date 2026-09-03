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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop

@Composable
fun rememberCombinedBackdrop(
    backdrop1: Backdrop,
    backdrop2: Backdrop
): Backdrop {
    return remember(backdrop1, backdrop2) {
        Combined2Backdrops(backdrop1, backdrop2)
    }
}

@Composable
fun rememberCombinedBackdrop(
    backdrop1: Backdrop,
    backdrop2: Backdrop,
    backdrop3: Backdrop
): Backdrop {
    return remember(backdrop1, backdrop2, backdrop3) {
        Combined3Backdrops(backdrop1, backdrop2, backdrop3)
    }
}

@Composable
fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop {
    return remember(*backdrops) {
        CombinedBackdrops(*backdrops)
    }
}

@Immutable
private class Combined2Backdrops(
    val backdrop1: Backdrop,
    val backdrop2: Backdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent || backdrop2.isCoordinatesDependent

    /** Un impilamento vale quanto il piu' impreparato dei suoi. */
    override fun isReadyToSample(): Boolean =
        backdrop1.isReadyToSample() && backdrop2.isReadyToSample()

    // Fluid Engine addition: a stack is only cacheable if everything in it is.
    override val layerSources: List<Any> =
        if (backdrop1.layerSources.isEmpty() || backdrop2.layerSources.isEmpty()) {
            emptyList()
        } else {
            backdrop1.layerSources + backdrop2.layerSources
        }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class Combined3Backdrops(
    val backdrop1: Backdrop,
    val backdrop2: Backdrop,
    val backdrop3: Backdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        backdrop1.isCoordinatesDependent ||
                backdrop2.isCoordinatesDependent ||
                backdrop3.isCoordinatesDependent

    // Fluid Engine addition: a stack is only cacheable if everything in it is.
    override val layerSources: List<Any> =
        if (backdrop1.layerSources.isEmpty() ||
            backdrop2.layerSources.isEmpty() ||
            backdrop3.layerSources.isEmpty()
        ) {
            emptyList()
        } else {
            backdrop1.layerSources + backdrop2.layerSources + backdrop3.layerSources
        }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        with(backdrop1) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop2) { drawBackdrop(density, coordinates, layerBlock) }
        with(backdrop3) { drawBackdrop(density, coordinates, layerBlock) }
    }
}

@Immutable
private class CombinedBackdrops(
    vararg val backdrops: Backdrop
) : Backdrop {

    override val isCoordinatesDependent: Boolean =
        backdrops.any { it.isCoordinatesDependent }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        backdrops.forEach { backdrop ->
            with(backdrop) { drawBackdrop(density, coordinates, layerBlock) }
        }
    }
}
