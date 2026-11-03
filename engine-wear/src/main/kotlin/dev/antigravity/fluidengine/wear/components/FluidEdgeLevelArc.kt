package dev.antigravity.fluidengine.wear.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens
import kotlin.math.min

/**
 * A level — the volume, a brightness — drawn on the edge of a round screen while it changes.
 *
 * The same stroke, halo and lit head as [FluidEdgeGlowRing], on a short stretch of the bezel: by
 * default the quarter centred on three o'clock, the side the thumb turns the crown or the bezel
 * from, filling from the bottom up. A watch screen that shows progress on its edge and a level on
 * a separate inset arc is two visual languages for one idea.
 *
 * @param level 0..1.
 * @param startAngle where the empty end of the arc is, in degrees clockwise from three o'clock.
 * @param sweepAngle the full arc, signed: negative fills anticlockwise (upwards on the right side).
 */
@Composable
fun FluidEdgeLevelArc(
  level: Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = LevelTrackAlpha),
  startAngle: Float = LevelStartAngle,
  sweepAngle: Float = LevelSweepAngle,
  stroke: Dp = FluidWearDimens.EdgeRingStroke,
  glow: Dp = FluidWearDimens.EdgeRingGlow,
) {
  Canvas(modifier.fillMaxSize()) {
    val strokePx = stroke.toPx() * LevelStrokeScale
    val outer = min(size.width, size.height) / 2f
    val radius = outer - strokePx / 2f
    if (radius <= 0f) return@Canvas
    drawArc(
      color = trackColor,
      startAngle = startAngle,
      sweepAngle = sweepAngle,
      useCenter = false,
      topLeft = Offset(center.x - radius, center.y - radius),
      size = Size(radius * 2f, radius * 2f),
      style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )
    val fraction = level.coerceIn(0f, 1f)
    drawGlowArc(
      color = color,
      outer = outer,
      strokePx = strokePx,
      glowPx = glow.toPx(),
      startAngle = startAngle,
      sweepAngle = sweepAngle * fraction,
      intensity = haloIntensity(fraction),
      head = fraction > 0f,
    )
  }
}

private const val LevelStartAngle = 45f
private const val LevelSweepAngle = -90f
private const val LevelTrackAlpha = 0.16f

/** A level is read at a glance while it moves: a little bolder than the progress ring. */
private const val LevelStrokeScale = 1.6f
