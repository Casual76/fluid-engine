package dev.antigravity.fluidengine.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.MotionScheme
import dev.antigravity.fluidengine.ui.fluid.FluidMotion

/**
 * Feeds the app's springs to every Material 3 component that reads `MaterialTheme.motionScheme`,
 * so built-in components (switches, chips, sheets) move on the same timings as the custom chrome.
 */
object FluidMotionScheme : MotionScheme {
  override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.standard()

  override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.snappy()

  override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = FluidMotion.smooth()

  override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.snappy()

  override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.instant()

  override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = FluidMotion.smooth()
}
