package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale

/** One source of truth for motion that must disappear when the system animator scale is zero. */
@Immutable
data class FluidMotionPolicy(
  val durationScale: Float,
) {
  val reducedMotion: Boolean get() = durationScale <= 0f
  val allowDecorativeMotion: Boolean get() = !reducedMotion
  val allowElasticity: Boolean get() = !reducedMotion

  companion object {
    val Default = FluidMotionPolicy(durationScale = 1f)
  }
}

val LocalFluidMotionPolicy = staticCompositionLocalOf { FluidMotionPolicy.Default }

@Composable
fun rememberFluidMotionPolicy(): FluidMotionPolicy {
  val scope = rememberCoroutineScope()
  val scale = scope.coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
  return remember(scale) { FluidMotionPolicy(durationScale = scale.coerceAtLeast(0f)) }
}

@Composable
fun FluidMotionPolicyProvider(
  policy: FluidMotionPolicy = rememberFluidMotionPolicy(),
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalFluidMotionPolicy provides policy, content = content)
}
