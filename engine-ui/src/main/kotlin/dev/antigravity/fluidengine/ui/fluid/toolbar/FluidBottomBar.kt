package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidBottomInset
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop

/** Uno spaziatore da solo non apre una barra. Una posizione esplicita prevale su quella del gruppo. */
internal fun FluidToolbarEntry.declaresBottomAction(parentBottom: Boolean): Boolean {
  fun bottom(placement: FluidToolbarPlacement) = placement == FluidToolbarPlacement.BottomBar ||
    placement == FluidToolbarPlacement.Automatic && parentBottom
  return when (this) {
    is FluidToolbarSpacerEntry -> false
    is FluidToolbarButtonEntry -> bottom(placement)
    is FluidToolbarGroupEntry -> children.any { it.declaresBottomAction(bottom(placement)) }
  }
}

internal fun fluidBottomBarMetrics(): FluidToolbarMetrics = fluidToolbarMetrics().copy(
  buttonWidthDp = FluidFloatingCapsuleDefaults.ActionSize.value,
  itemPitchDp = FluidFloatingCapsuleDefaults.ActionSize.value,
)

/** La quota già occupata dalla navigazione resta sotto i comandi, anche con un inset fornito dall’app. */
private fun fluidBottomBarOffset(navigation: Dp): Dp =
  if (navigation > 0.dp) navigation + FluidFloatingTabBarDefaults.Spacing else FluidFloatingTabBarDefaults.BottomMargin

internal fun fluidBottomBarExtraInset(navigation: Dp): Dp =
  fluidBottomBarOffset(navigation) + FluidFloatingCapsuleDefaults.ActionSize - navigation

/** Una sede per schermata o per host. Il modello, i viaggi e i bersagli sono quelli della barra dichiarata. */
@Composable
internal fun FluidBottomBar(
  declaration: FluidToolbarDeclaration?,
  backdrop: GlassBackdropState,
  minimize: FluidToolbarMinimizeProgress?,
  modifier: Modifier = Modifier,
) {
  val present = declaration?.hasBottomBar == true
  val visibility = remember { MutableTransitionState(present) }
  visibility.targetState = present
  if (!present && visibility.isIdle) return
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  AnimatedVisibility(
    visibleState = visibility,
    modifier = modifier.navigationBarsPadding().padding(bottom = fluidBottomBarOffset(LocalFluidBottomInset.current)),
    enter = FluidFloatingCapsuleDefaults.enter(reduced),
    exit = FluidFloatingCapsuleDefaults.exit(reduced),
  ) {
    CompositionLocalProvider(
      LocalGlassBackdrop provides backdrop,
      LocalFluidCanvasBackdrop provides null,
      LocalFluidGlassQuality provides null,
      LocalFluidToolbarMinimize provides minimize,
    ) {
      FluidToolbarBar(declaration ?: EmptyBottomDeclaration, bar = FluidToolbarBar.Bottom)
    }
  }
}

private val EmptyBottomDeclaration = buildFluidToolbar(null) {}
