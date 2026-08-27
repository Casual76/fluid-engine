package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged

/**
 * The elastic edge, for a screen that is not a [FluidScreen].
 *
 * `FluidScreen` has had this since the beginning; an app that draws its own
 * screens — a grid, a page under a photograph, a panel inside a player — had no
 * way to ask for it and was left with the platform's stretch, which is a
 * different idiom altogether. The state is the same one the family screen uses,
 * so a list that adopts these two modifiers gives at both ends exactly as every
 * other page in the family does.
 *
 * @param canScroll whether the list has anywhere to go. A page shorter than its
 *   viewport has no end to bounce against, and letting it bounce anyway pushes
 *   the whole page — title included — up past the status bar.
 */
@Composable
fun rememberFluidEdgeOverscroll(
  canScroll: () -> Boolean = { true },
): FluidEdgeOverscrollState {
  val reducedMotion = rememberFluidMotionPolicy().reducedMotion
  return remember(reducedMotion) {
    FluidEdgeOverscrollState(reducedMotion = reducedMotion, canScroll = canScroll)
  }
}

/**
 * Put on the scrolling container: it takes what the list could not use, and
 * learns how tall the viewport is so the give is a fraction of it rather than a
 * fixed number of pixels.
 *
 * The list itself must also pass `overscrollEffect = null`, or the platform's
 * stretch runs underneath this one and the two fight over the same gesture.
 */
fun Modifier.fluidOverscrollEdge(state: FluidEdgeOverscrollState): Modifier = this
  .nestedScroll(state)
  .onSizeChanged { state.updateViewport(it.height.toFloat()) }

/**
 * Put on what should actually move — and only on that.
 *
 * Deliberately a second modifier rather than one that does everything: an
 * opaque background belongs *outside* the layer that travels. Painted inside
 * it, a bounce drags a band of transparency out from under the bar above, and
 * a glass bar sampling that band blurs a hole in the page.
 */
fun Modifier.fluidOverscrollContent(state: FluidEdgeOverscrollState): Modifier =
  this.graphicsLayer { translationY = state.offsetPx }
