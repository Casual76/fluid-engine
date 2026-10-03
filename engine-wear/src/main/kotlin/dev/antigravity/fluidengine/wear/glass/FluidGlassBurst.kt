package dev.antigravity.fluidengine.wear.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * A small burst of light from where a glass control was pressed.
 *
 * A ring that leaves the touch point and runs out to the rim, and a handful of short specular
 * sparks travelling with it. It is light, not paint: on a dark surface it is drawn additively, so
 * it brightens whatever the glass is showing (the cover behind a disc stays a cover, lit), and on a
 * light one it darkens instead, for the same reason [GlassTouchHighlight] turns over —
 * there is no light left to add to white.
 *
 * It is drawn *on top of* the glass rather than inside its effect chain, which matters for the
 * frozen-glass rule this module is built on: firing a burst repaints one small layer for a few
 * hundred milliseconds and never asks any pane to re-capture what is behind it. [isAnimating] says
 * exactly when that is happening.
 */
@Stable
class FluidGlassBurstState internal constructor(
  private val scope: CoroutineScope,
  private val reducedMotion: Boolean,
) {
  private val progress = Animatable(1f, visibilityThreshold = 0.002f)

  /** Where the last burst started, in the control's own coordinates. Null: from the centre. */
  internal var origin: Offset? by mutableStateOf(null)
    private set

  /** Changes on every [fire], so the sparks of two bursts in a row do not line up. */
  internal var seed: Int by mutableStateOf(0)
    private set

  /** 0 at the moment of the press, 1 when the burst has faded out. */
  val value: Float get() = progress.value

  /** True while a burst is on screen. */
  val isAnimating: Boolean get() = progress.isRunning

  /** Sparks are left out under reduced motion: the ring alone still says "pressed". */
  internal val withSparks: Boolean get() = !reducedMotion

  fun fire(at: Offset? = null) {
    origin = at
    seed++
    scope.launch {
      progress.snapTo(0f)
      // The gentle spring: a burst has to be seen travelling to the rim, and the snappier
      // responses are over before the eye has found it.
      progress.animateTo(1f, FluidMotion.gentle())
    }
  }
}

@Composable
fun rememberFluidGlassBurstState(): FluidGlassBurstState {
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  return remember(scope, reducedMotion) { FluidGlassBurstState(scope, reducedMotion) }
}

/**
 * Draws [state]'s burst over this control's content.
 *
 * @param onDarkSurface whether the glass under the burst is on its dark side (ask
 *   `GlassDefaults.isDarkSurface()`); decides between adding light and taking it away.
 * @param enabled false in ambient mode, where nothing may animate.
 */
fun Modifier.fluidGlassBurst(
  state: FluidGlassBurstState,
  onDarkSurface: Boolean = true,
  enabled: Boolean = true,
): Modifier = drawWithContent {
  drawContent()
  if (!enabled) return@drawWithContent
  val p = state.value
  if (p >= 1f || p < 0f) return@drawWithContent

  val tone = if (onDarkSurface) Color.White else Color.Black
  val blend = if (onDarkSurface) BlendMode.Plus else BlendMode.SrcOver
  val fade = (1f - p) * (1f - p)
  val origin = state.origin ?: center
  // Far enough to reach every corner of the control from wherever the press was.
  val reach = max(
    max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
    max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y)),
  )

  // The ring: a soft band of light, widest at the start, thinning as it travels.
  val ringRadius = reach * (0.15f + 0.9f * p)
  val ringWidth = size.minDimension * (0.22f - 0.14f * p)
  drawCircle(
    brush = Brush.radialGradient(
      colorStops = arrayOf(
        0f to Color.Transparent,
        0.72f to tone.copy(alpha = 0f),
        0.9f to tone.copy(alpha = RingAlpha * fade),
        1f to Color.Transparent,
      ),
      center = origin,
      radius = ringRadius + ringWidth / 2f,
    ),
    radius = ringRadius + ringWidth / 2f,
    center = origin,
    blendMode = blend,
  )
  // A brief flash at the touch point itself, gone in the first third.
  val flash = ((0.35f - p) / 0.35f).coerceIn(0f, 1f)
  if (flash > 0f) {
    drawCircle(
      brush = Brush.radialGradient(
        listOf(tone.copy(alpha = FlashAlpha * flash), Color.Transparent),
        center = origin,
        radius = size.minDimension * 0.5f,
      ),
      radius = size.minDimension * 0.5f,
      center = origin,
      blendMode = blend,
    )
  }

  if (!state.withSparks) return@drawWithContent
  // The sparks: short streaks at fixed but seed-rotated angles, riding just inside the ring.
  val rotation = (state.seed * GoldenAngle) % 360f
  val streak = size.minDimension * 0.07f
  for (i in 0 until SparkCount) {
    val angle = Math.toRadians((rotation + i * 360f / SparkCount).toDouble())
    val travel = reach * (0.4f + 0.65f * p)
    val dx = cos(angle).toFloat()
    val dy = sin(angle).toFloat()
    val head = Offset(origin.x + dx * travel, origin.y + dy * travel)
    val tail = Offset(head.x - dx * streak, head.y - dy * streak)
    drawLine(
      color = tone.copy(alpha = SparkAlpha * fade),
      start = tail,
      end = head,
      strokeWidth = Stroke.HairlineWidth.coerceAtLeast(size.minDimension * 0.018f),
      blendMode = blend,
    )
  }
}

private const val RingAlpha = 0.55f
private const val FlashAlpha = 0.35f
private const val SparkAlpha = 0.8f
private const val SparkCount = 8
private const val GoldenAngle = 137.5f
