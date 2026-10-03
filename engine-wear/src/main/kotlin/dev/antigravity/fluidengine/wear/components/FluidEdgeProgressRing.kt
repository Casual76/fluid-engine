package dev.antigravity.fluidengine.wear.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min

/**
 * How far a song has gone, drawn around the edge of a round screen.
 *
 * Built to cost nothing it does not have to. The arc is only redrawn when its end would move by
 * at least half a pixel ([ringStepMillis]): for a three-minute song on a 450 px screen that is a
 * redraw every ~60 ms, not every frame, and for a paused song it is none at all. It also lives
 * outside whatever glass sits on the screen, so its redraws never make a pane re-capture.
 *
 * @param positionMs where the song is now; called only when a redraw is due.
 * @param running whether the position is moving (playing and not buffering).
 * @param ambient draws the ring once, thin and without its track, and never ticks.
 */
@Composable
fun FluidEdgeProgressRing(
  positionMs: () -> Long,
  durationMs: Long,
  running: Boolean,
  modifier: Modifier = Modifier,
  ambient: Boolean = false,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = TrackAlpha),
  stroke: Dp = FluidWearDimens.RingStroke,
  inset: Dp = FluidWearDimens.RingInset,
) {
  val currentPosition by rememberUpdatedState(positionMs)
  var circumferencePx by remember { mutableFloatStateOf(0f) }
  var progress by remember { mutableFloatStateOf(0f) }

  fun sample() {
    progress = ringProgress(currentPosition(), durationMs)
  }

  LaunchedEffect(running, durationMs, circumferencePx, ambient) {
    sample()
    if (!running || ambient || durationMs <= 0 || circumferencePx <= 0f) return@LaunchedEffect
    val step = ringStepMillis(durationMs, circumferencePx)
    while (true) {
      delay(step)
      sample()
    }
  }
  // A snapshot that arrives while paused (a seek, a new song) must still move the ring.
  LaunchedEffect(positionMs, durationMs) { sample() }

  Canvas(
    modifier = modifier
      .fillMaxSize()
      .onSizeChanged { size ->
        circumferencePx = (2 * PI * (min(size.width, size.height) / 2f)).toFloat()
      },
  ) {
    val strokePx = stroke.toPx() * if (ambient) AmbientStrokeFraction else 1f
    val radius = min(size.width, size.height) / 2f - inset.toPx() - strokePx / 2f
    if (radius <= 0f) return@Canvas
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    if (!ambient) {
      drawArc(
        color = trackColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx),
      )
    }
    val sweep = ringSweep(progress)
    if (sweep > 0f) {
      drawArc(
        color = color,
        startAngle = RingStartAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
      )
    }
  }
}

/** 0..1, safe for an unknown or zero duration. */
internal fun ringProgress(positionMs: Long, durationMs: Long): Float =
  if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

/** The arc's sweep in degrees for [progress]. */
internal fun ringSweep(progress: Float): Float = 360f * progress.coerceIn(0f, 1f)

/**
 * How often the ring has to redraw for its end to move by [minStepPx].
 *
 * Never faster than a frame, and never slower than a second, so a very long track on a small
 * screen still visibly moves.
 */
internal fun ringStepMillis(durationMs: Long, circumferencePx: Float, minStepPx: Float = 0.5f): Long {
  if (durationMs <= 0 || circumferencePx <= 0f) return MaxStepMillis
  val millis = (durationMs * minStepPx / circumferencePx).toLong()
  return max(MinStepMillis, min(MaxStepMillis, millis))
}

/** Twelve o'clock: where a ring that measures time starts. */
internal const val RingStartAngle = -90f
private const val TrackAlpha = 0.18f
private const val AmbientStrokeFraction = 0.5f
private const val MinStepMillis = 16L
private const val MaxStepMillis = 1_000L
