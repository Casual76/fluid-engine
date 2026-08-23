package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The spinner.
 *
 * Not a rotating arc. Eight tapered spokes around a circle, each dimmer than the one before it, and
 * the whole ramp *steps* round rather than sweeping — the stepping is the part people recognise, and
 * a smooth rotation of the same drawing reads as a generic loading graphic instead.
 */
@Composable
fun FluidSpinner(
  modifier: Modifier = Modifier,
  size: Dp = 20.dp,
  color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
  /** 0..1 — how much of the ring is drawn. Used by pull-to-refresh while the finger is still down. */
  progress: (() -> Float)? = null,
) {
  val transition = rememberInfiniteTransition(label = "spinner")
  val step by transition.animateFloat(
    initialValue = 0f,
    targetValue = SpokeCount.toFloat(),
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 900, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "spinner step",
  )

  Canvas(modifier = modifier.size(size)) {
    val reveal = progress?.invoke()?.coerceIn(0f, 1f) ?: 1f
    if (reveal <= 0f) return@Canvas
    val radius = this.size.minDimension / 2f
    val spokeLength = radius * 0.42f
    val spokeInset = radius * 0.48f
    val strokeWidth = radius * 0.24f
    // Stepping, not sweeping: the index is floored so the ramp jumps one spoke at a time.
    val current = if (progress == null) floor(step) else 0f

    for (i in 0 until SpokeCount) {
      val revealedSpokes = reveal * SpokeCount
      if (i >= revealedSpokes) continue
      // Partial credit for the spoke straddling the reveal edge, so pulling feels continuous.
      val edgeFade = (revealedSpokes - i).coerceIn(0f, 1f)
      val position = ((i - current) + SpokeCount) % SpokeCount
      val alpha = (0.18f + 0.82f * (position / (SpokeCount - 1f))) * edgeFade
      val angle = (i.toFloat() / SpokeCount) * TwoPi - HalfPi
      val dx = cos(angle)
      val dy = sin(angle)
      drawLine(
        color = color.copy(alpha = color.alpha * alpha),
        start = center + Offset(dx * spokeInset, dy * spokeInset),
        end = center + Offset(dx * (spokeInset + spokeLength), dy * (spokeInset + spokeLength)),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }
  }
}

private const val SpokeCount = 8
private const val TwoPi = (2 * PI).toFloat()
private const val HalfPi = (PI / 2).toFloat()

/**
 * A determinate bar.
 *
 * A hairline-thin capsule rather than Material's segmented track: no stop indicator, no gap before
 * the remaining portion, and the track is the text colour at low opacity rather than a container
 * colour, so it works on any surface it lands on.
 */
@Composable
fun FluidProgressBar(
  progress: () -> Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  height: Dp = 4.dp,
) {
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(FluidCapsuleShape)
      .background(track),
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
      val fraction = progress().coerceIn(0f, 1f)
      if (fraction <= 0f) return@Canvas
      val radius = size.height / 2f
      drawRoundRect(
        color = color,
        size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
      )
    }
  }
}

/**
 * An indeterminate bar, for the rare case where something is loading but nothing knows how far.
 *
 * A short segment travelling the width, easing at both ends — Material's version accelerates out of
 * frame and restarts abruptly, which reads as a stutter at the loop point.
 */
@Composable
fun FluidIndeterminateBar(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  height: Dp = 3.dp,
) {
  val transition = rememberInfiniteTransition(label = "indeterminate")
  val travel by transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100, easing = FluidMotion.EaseInOut),
      repeatMode = RepeatMode.Restart,
    ),
    label = "indeterminate travel",
  )
  val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(height)
      .clip(FluidCapsuleShape)
      .background(track),
  ) {
    Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
      val segment = size.width * 0.35f
      val x = (size.width + segment) * travel - segment
      val radius = size.height / 2f
      val start = x.coerceAtLeast(0f)
      val end = (x + segment).coerceAtMost(size.width)
      if (end <= start) return@Canvas
      drawRoundRect(
        color = color,
        topLeft = Offset(start, 0f),
        size = androidx.compose.ui.geometry.Size(end - start, size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
      )
    }
  }
}
