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
package dev.antigravity.fluidengine.ui.glass.backdrop.internal

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import androidx.compose.ui.unit.LayoutDirection

@Immutable
internal class ShapeProvider(val shapeBlock: () -> Shape) {

    private var _shape: Shape? = null
    private var _outline: Outline? = null
    private var _size: Size = Size.Unspecified
    private var _layoutDirection: LayoutDirection? = null
    private var _density: Float? = null

    val innerShape
        get() = shapeBlock()

    val shape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val shape = shapeBlock()
            if (_shape != shape) {
                _shape = shape
                _outline = null
            }
            if (_outline == null || _size != size || _layoutDirection != layoutDirection || _density != density.density) {
                _size = size
                _layoutDirection = layoutDirection
                _density = density.density
                _outline = shape.createOutline(size, layoutDirection, density)
            }

            return _outline!!
        }
    }

    /**
     * Fluid Engine addition: the same silhouette, expressed so a `RenderNode` can clip to it in
     * hardware.
     *
     * A layer clipped to an `Outline.Generic` cannot use the GPU's rounded-rect clip, so HWUI masks
     * the entire node offscreen instead — measurably, per pane, on every frame of a scroll. This
     * reads the corner radii off whatever shape is in scope and hands back the rounded-rect form of
     * it, which differs from the continuous one only in how the curvature ramps *inside* the corner.
     * The visible corner is drawn by the surface itself and is untouched.
     */
    val fastClipShape = object : Shape {

        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density
        ): Outline {
            val exact = shape.createOutline(size, layoutDirection, density)
            if (exact !is Outline.Generic) return exact
            val source = shapeBlock()
            if (source !is CornerBasedShape) return exact
            val topStart = source.topStart.toPx(size, density)
            val topEnd = source.topEnd.toPx(size, density)
            val bottomEnd = source.bottomEnd.toPx(size, density)
            val bottomStart = source.bottomStart.toPx(size, density)
            val rtl = layoutDirection == LayoutDirection.Rtl
            // The clip must make the SAME per-size decision the tint does, or the corner shows two
            // curves. Small surfaces draw their tint through the rounded-equivalent radii, so the
            // clip repeats them exactly. Large surfaces draw the true continuous path — there the
            // clip keeps the full nominal radius, the closest rounded fit to the superellipse:
            // shrunk by the small-control factor it fell short of the silhouette, and the ring it
            // left uncovered was a crescent of tint with no refraction in it, framing every corner
            // of a menu pane.
            val small = size.maxDimension < ContinuousCornerShape.SmallShapeCutoffPx
            fun clipRadius(continuousRadius: Float): CornerRadius =
                if (small) {
                    ContinuousCornerShape.roundedEquivalent(continuousRadius, size)
                } else {
                    CornerRadius(continuousRadius)
                }
            return Outline.Rounded(
                RoundRect(
                    rect = Rect(Offset.Zero, size),
                    topLeft = clipRadius(if (rtl) topEnd else topStart),
                    topRight = clipRadius(if (rtl) topStart else topEnd),
                    bottomRight = clipRadius(if (rtl) bottomStart else bottomEnd),
                    bottomLeft = clipRadius(if (rtl) bottomEnd else bottomStart),
                )
            )
        }
    }
}
