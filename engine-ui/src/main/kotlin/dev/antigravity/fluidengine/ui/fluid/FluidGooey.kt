/*
 * Vendored from Elyes Mansour's FloatingTabBar, with Kyant0's liquid-glass puck grafted in.
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant0, Apache License 2.0
 *
 * It arrives here from the app that had it, not the other way round: its glass — the refraction
 * under the puck, the hidden tinted row the puck samples back through itself, the stretch that
 * follows a dragged finger — is a level above what this engine's own bar could do, and an engine
 * whose components are worse than the app's is an engine nobody reaches for.
 *
 * Kept at the formatting it came with (four spaces, not this package's two) so that a later look at
 * upstream is a diff and not an archaeology. What did change is named where it happens: the drag
 * animation and the touch glow are the engine's, the shapes are the engine's, and the three places
 * that pre-multiplied effect parameters by the backdrop scale no longer do — see the note at the
 * hidden row, where getting that wrong is invisible until you look at the glass.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported

// Alpha (0..255 scale, matching this app's other ColorMatrix usage in
// backdrop/effects/ColorFilter.kt) snaps from 0 to 1 across a narrow band
// centered on 0.5 — steep enough to read as a crisp edge, not more blur.
private const val ThresholdSlope = 10f
private const val ThresholdMidpoint255 = 0.5f * 255f

// The threshold never varies, so build the matrix filter once instead of
// allocating an identical one on every frame of every transition.
//
// LOCAL CHANGE: built from the platform's own classes. Upstream reaches for
// the backdrop library's `ColorFilterEffect`, which is internal to the
// published artifact — visible where the library is vendored whole, not where
// it is a dependency. The two compose the same thing: a blur with a colour
// matrix applied after it.
private val ThresholdColorFilter = android.graphics.ColorMatrixColorFilter(
    android.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, ThresholdSlope, -ThresholdSlope * ThresholdMidpoint255
        )
    )
)

// Radius is quantised to whole pixels before the effect is built and cached, so a
// transition reuses one RenderEffect per distinct radius instead of allocating a
// fresh BlurEffect + ColorMatrixColorFilter every frame. Sub-pixel blur steps are
// not visible through an alpha threshold anyway. Bounded: the radius only ever
// ranges over 0..GooeyPeakBlur.
private val gooeyEffectCache = HashMap<Int, RenderEffect>()

@androidx.annotation.RequiresApi(31)
private fun gooeyRenderEffect(blurRadiusPx: Float): RenderEffect {
    val key = blurRadiusPx.toInt()
    return gooeyEffectCache.getOrPut(key) {
        android.graphics.RenderEffect.createColorFilterEffect(
            ThresholdColorFilter,
            android.graphics.RenderEffect.createBlurEffect(
                key.toFloat(),
                key.toFloat(),
                android.graphics.Shader.TileMode.DECAL,
            ),
        ).asComposeRenderEffect()
    }
}

/**
 * Composites this element into one offscreen layer and gooifies it: blurs by
 * [blurRadiusPx], then snaps the blur back to a crisp edge via an alpha
 * threshold. Offscreen compositing is what makes overlapping shapes' alpha
 * bleed together *before* the threshold — two independently-filtered layers
 * would just look like two blurred blobs with a seam, not a merged one.
 *
 * Zero cost at rest: when [blurRadiusPx] returns 0 (or RenderEffect isn't
 * supported pre-API 31), this is a no-op — no offscreen layer, no filter.
 */
fun Modifier.gooey(blurRadiusPx: () -> Float): Modifier {
    if (!isRenderEffectSupported()) return this
    return this.graphicsLayer {
        val radius = blurRadiusPx()
        if (radius > 0f) {
            compositingStrategy = CompositingStrategy.Offscreen
            renderEffect = gooeyRenderEffect(radius)
        } else {
            compositingStrategy = CompositingStrategy.Auto
            renderEffect = null
        }
    }
}
