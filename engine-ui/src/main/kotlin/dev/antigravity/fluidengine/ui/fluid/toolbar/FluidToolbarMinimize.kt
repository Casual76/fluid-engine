package dev.antigravity.fluidengine.ui.fluid.toolbar

import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.calculateBottomBarSettleTarget
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.smoothStep

/** Normalizza Y affinché una corsa positiva ritiri la barra in entrambi i versi. */
internal fun fluidBarMinimizeDirection(
  behavior: FluidBarMinimizeBehavior,
  search: FluidSearchPlacement?,
): Float = when (behavior) {
  FluidBarMinimizeBehavior.Automatic -> if (search == FluidSearchPlacement.Principal) -1f else 0f
  FluidBarMinimizeBehavior.Never -> 0f
  FluidBarMinimizeBehavior.OnScrollDown -> -1f
  FluidBarMinimizeBehavior.OnScrollUp -> 1f
}

/**
 * Quanto è ritirata la barra per chi la disegna: la schermata ([FluidToolbarMinimizeState], la sua corsa) o l'host delle
 * pagine, che fra due pagine in movimento fonde le loro corse (giro di correzione di F4A, B1 e C8: l'indietro, la barra
 * dichiarata e l'area toccabile dell'host sono uno per host e non appartengono a una pagina sola).
 */
@Stable
internal interface FluidToolbarMinimizeProgress {
  /** 0 a riposo, 1 ritirata del tutto. */
  val progress: Float

  /** Col movimento ridotto la barra si ritira solo in opacità, sul posto. */
  val reducedMotion: Boolean

  /** La corsa in px del contenuto che si ritira con la riga (titolo grande, campo); 0 per chi non la conosce. */
  val travelPx: Float get() = 0f

  /** Quanto della corsa è fatto, in px. */
  val offsetPx: Float get() = travelPx * progress

  val hidden: Boolean get() = progress >= 1f
}

/** La corsa appartiene alla schermata: una pagina sotto quella davanti conserva il suo riposo. */
@Stable
internal class FluidToolbarMinimizeState(val direction: Float, override val reducedMotion: Boolean) : FluidToolbarMinimizeProgress {
  private val position = mutableFloatStateOf(0f)
  private val distance = mutableFloatStateOf(0f)
  override val travelPx: Float get() = distance.floatValue
  override val offsetPx: Float get() = travelPx * position.floatValue
  override val progress: Float get() = position.floatValue

  fun measure(travelPx: Float) {
    distance.floatValue = travelPx.coerceAtLeast(0f)
  }

  fun scroll(availableY: Float, canHide: Boolean): Float {
    val travel = travelPx
    val delta = availableY * direction
    if (travel <= 0f || (delta > 0f && !canHide)) return 0f
    val start = offsetPx
    val end = (start + delta).coerceIn(0f, travel)
    position.floatValue = end / travel
    return (end - start) * direction
  }

  fun settledProgress(velocityY: Float, threshold: Float): Float {
    val travel = travelPx
    if (travel <= 0f || progress == 0f || progress == 1f) return progress
    return calculateBottomBarSettleTarget(offsetPx, travel, -velocityY * direction, threshold) / travel
  }

  suspend fun settle(velocityY: Float, threshold: Float) {
    val target = settledProgress(velocityY, threshold)
    if (target == progress) return
    animate(
      initialValue = progress,
      targetValue = target,
      animationSpec = if (reducedMotion) FluidMotion.crossFade() else FluidMotion.gentle(),
    ) { value, _ -> position.floatValue = value.coerceIn(0f, 1f) }
  }
}

internal val LocalFluidToolbarMinimize = staticCompositionLocalOf<FluidToolbarMinimizeProgress?> { null }

internal data class FluidToolbarMinimizeLook(val offsetPx: Float, val alpha: Float, val blur: Float)

internal fun fluidToolbarMinimizeLook(
  progress: Float,
  travelPx: Float,
  reducedMotion: Boolean,
  blurAvailable: Boolean,
): FluidToolbarMinimizeLook {
  val phase = progress.coerceIn(0f, 1f)
  if (reducedMotion) return FluidToolbarMinimizeLook(0f, 1f - smoothStep(phase), 0f)
  return FluidToolbarMinimizeLook(
    offsetPx = -phase * travelPx,
    alpha = GlassContentCurves.alpha(GlassContentRole.Exit, phase * GlassContentDefaults.ExitEnd, blurAvailable),
    blur = if (blurAvailable) GlassContentCurves.blurRise(phase) * GlassContentDefaults.PeakBlur else 0f,
  )
}

/** Il campo deve poter passare sopra la sede fissa del titolo senza tagliare lettere ancora visibili. */
internal fun fluidToolbarReducedFadeProgress(progress: Float, offsetPx: Float, freeSpacePx: Float): Float =
  maxOf(progress, offsetPx / freeSpacePx.coerceAtLeast(1f)).coerceIn(0f, 1f)

/** Il layout conserva la misura d'arrivo e smette di posare i bersagli quando sono svaniti. */
@Composable
internal fun Modifier.fluidToolbarMinimizeLayer(
  minimize: FluidToolbarMinimizeProgress? = LocalFluidToolbarMinimize.current,
  fullTravel: Boolean = false,
  reducedFadeDistancePx: Float? = null,
): Modifier {
  if (minimize == null) return this
  fun visualProgress(): Float = if (minimize.reducedMotion && reducedFadeDistancePx != null) {
    fluidToolbarReducedFadeProgress(minimize.progress, minimize.offsetPx, reducedFadeDistancePx)
  } else {
    minimize.progress
  }
  val hidden by remember(minimize, reducedFadeDistancePx) { derivedStateOf { visualProgress() >= 1f } }
  val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  return then(if (hidden) Modifier.clearAndSetSemantics { } else Modifier).layout { measurable, constraints ->
    val child = measurable.measure(constraints)
    layout(child.width, child.height) {
      if (!hidden) child.place(0, 0)
    }
  }.graphicsLayer {
    val travel = if (fullTravel) minimize.travelPx else FluidMetrics.GlassButtonLens.toPx()
    val look = fluidToolbarMinimizeLook(visualProgress(), travel, minimize.reducedMotion, blurAvailable)
    translationY = look.offsetPx
    alpha = look.alpha
    val radius = glassContentBlurRadiusPx(glassContentSigmaPx(look.blur, FluidMetrics.BarIconSize.toPx()))
    renderEffect = if (radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
  }
}
