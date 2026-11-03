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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
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
 * @param ambient draws the ring once, thin, without its track or halo, and never ticks.
 * @param clearTop the width to leave empty at twelve o'clock, centred — the clock's. The ring then
 *   starts after the gap and stops before it, so it never runs behind the time. Since 2.11.0.
 * @param glow how far a soft halo of [color] reaches in from the ring; it brightens as the ring
 *   fills. Zero draws no halo. Since 2.11.0.
 * @param head a lit point at the moving end of the arc. Since 2.11.0.
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
  clearTop: Dp = 0.dp,
  glow: Dp = 0.dp,
  head: Boolean = false,
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
    val outer = min(size.width, size.height) / 2f - inset.toPx()
    val radius = outer - strokePx / 2f
    if (radius <= 0f) return@Canvas
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val gap = ringGapDegrees(clearTop.toPx() / 2f, radius)
    val start = RingStartAngle + gap / 2f
    val total = 360f - gap
    // A gap needs round ends on the track too, or it reads as a cut; a full circle has no ends.
    val trackCap = if (gap > 0f) StrokeCap.Round else StrokeCap.Butt
    if (!ambient) {
      drawArc(
        color = trackColor,
        startAngle = start,
        sweepAngle = total,
        useCenter = false,
        topLeft = topLeft,
        size = arcSize,
        style = Stroke(width = strokePx, cap = trackCap),
      )
    }
    val sweep = total * progress.coerceIn(0f, 1f)
    if (sweep <= 0f) return@Canvas
    drawGlowArc(
      color = color,
      outer = outer,
      strokePx = strokePx,
      glowPx = if (ambient) 0f else glow.toPx(),
      startAngle = start,
      sweepAngle = sweep,
      intensity = haloIntensity(progress),
      head = head && !ambient,
    )
  }
}

/**
 * An arc on a circle of outer radius [outer] about the centre, with its halo and its lit head.
 *
 * The halo is the same arc, wider, painted with a gradient that runs inwards from the edge: a
 * radial gradient about the screen's centre follows the ring exactly, which a blur would only
 * approximate at many times the cost.
 */
internal fun DrawScope.drawGlowArc(
  color: Color,
  outer: Float,
  strokePx: Float,
  glowPx: Float,
  startAngle: Float,
  sweepAngle: Float,
  intensity: Float,
  head: Boolean,
) {
  val radius = outer - strokePx / 2f
  if (radius <= 0f || sweepAngle == 0f) return
  if (glowPx > strokePx) {
    val band = min(glowPx, outer)
    val inner = (outer - band) / outer
    val solid = (outer - strokePx) / outer
    // Gradient stops must keep their order, whatever the stroke and band turn out to be.
    val knee = (inner + (1f - inner) * HaloKnee).coerceIn(inner, solid)
    val bandRadius = outer - band / 2f
    val bandTopLeft = Offset(center.x - bandRadius, center.y - bandRadius)
    val bandSize = Size(bandRadius * 2f, bandRadius * 2f)
    fun halo(alpha: Float, from: Float, sweep: Float) = drawArc(
      brush = Brush.radialGradient(
        0f to Color.Transparent,
        inner to Color.Transparent,
        knee to color.copy(alpha = alpha * HaloKneeAlpha),
        solid to color.copy(alpha = alpha),
        1f to color.copy(alpha = alpha),
        center = center,
        radius = outer,
      ),
      startAngle = from,
      sweepAngle = sweep,
      useCenter = false,
      topLeft = bandTopLeft,
      size = bandSize,
      style = Stroke(width = band, cap = StrokeCap.Butt),
    )
    // The halo fades in over its first few degrees instead of starting at a hard edge. It is drawn
    // into a layer and masked there by a sweep gradient that runs from clear to solid over those
    // degrees: one pass, no seams — splitting the start into steps left a line at every join.
    // The canvas is turned so the arc starts at three o'clock, where a sweep gradient starts.
    val direction = if (sweepAngle < 0f) -1f else 1f
    val fade = min(HaloFadeDegrees, abs(sweepAngle) * 0.5f) / 360f
    rotate(degrees = startAngle, pivot = center) {
      val canvas = drawContext.canvas
      canvas.saveLayer(Rect(Offset.Zero, size), Paint())
      halo(intensity, 0f, sweepAngle)
      drawArc(
        brush = Brush.sweepGradient(
          colorStops = if (direction > 0f) {
            arrayOf(0f to Color.Transparent, fade to Color.Black)
          } else {
            arrayOf(1f - fade to Color.Black, 1f to Color.Transparent)
          },
          center = center,
        ),
        startAngle = 0f,
        sweepAngle = direction * HaloFadeDegrees,
        useCenter = false,
        topLeft = bandTopLeft,
        size = bandSize,
        style = Stroke(width = band + 2f, cap = StrokeCap.Butt),
        blendMode = BlendMode.DstIn,
      )
      canvas.restore()
    }
  }

  drawArc(
    color = color,
    startAngle = startAngle,
    sweepAngle = sweepAngle,
    useCenter = false,
    topLeft = Offset(center.x - radius, center.y - radius),
    size = Size(radius * 2f, radius * 2f),
    style = Stroke(width = strokePx, cap = StrokeCap.Round),
  )

  if (head) {
    val radians = Math.toRadians((startAngle + sweepAngle).toDouble())
    val tip = Offset(center.x + radius * cos(radians).toFloat(), center.y + radius * sin(radians).toFloat())
    if (glowPx > 0f) {
      drawCircle(
        brush = Brush.radialGradient(
          0f to color.copy(alpha = HeadGlowAlpha),
          1f to Color.Transparent,
          center = tip,
          radius = glowPx * HeadGlowReach,
        ),
        radius = glowPx * HeadGlowReach,
        center = tip,
      )
    }
    drawCircle(color = lerp(color, Color.White, HeadWhiteness), radius = strokePx * HeadSize, center = tip)
  }
}

/**
 * [FluidEdgeProgressRing] as a cover screen wants it: on the very edge of the glass, thin, with a
 * halo that grows inwards as the song fills and a lit head, leaving room for a clock at twelve.
 *
 * @param clearTop the clock's width; see [FluidEdgeProgressRing]. Add
 *   [FluidWearDimens.EdgeRingClockMargin] on each side yourself if the clock has no margin of its own.
 */
@Composable
fun FluidEdgeGlowRing(
  positionMs: () -> Long,
  durationMs: Long,
  running: Boolean,
  modifier: Modifier = Modifier,
  ambient: Boolean = false,
  color: Color = MaterialTheme.colorScheme.primary,
  clearTop: Dp = 0.dp,
) {
  FluidEdgeProgressRing(
    positionMs = positionMs,
    durationMs = durationMs,
    running = running,
    modifier = modifier,
    ambient = ambient,
    color = color,
    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = EdgeTrackAlpha),
    stroke = FluidWearDimens.EdgeRingStroke,
    inset = 0.dp,
    clearTop = clearTop,
    glow = FluidWearDimens.EdgeRingGlow,
    head = true,
  )
}

/**
 * The angle, in degrees, a chord of half-width [halfWidthPx] cuts out of a circle of [radiusPx].
 *
 * Zero for no width, and never more than half the circle: a clock wider than the screen still
 * leaves the ring something to draw.
 */
internal fun ringGapDegrees(halfWidthPx: Float, radiusPx: Float): Float {
  if (halfWidthPx <= 0f || radiusPx <= 0f) return 0f
  val ratio = (halfWidthPx / radiusPx).coerceAtMost(1f)
  return (2.0 * Math.toDegrees(asin(ratio.toDouble()))).toFloat().coerceAtMost(MaxGapDegrees)
}

/** How bright the halo is at [progress]: present from the start, strongest at the end. */
internal fun haloIntensity(progress: Float): Float =
  HaloMinAlpha + (HaloMaxAlpha - HaloMinAlpha) * progress.coerceIn(0f, 1f)

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
private const val EdgeTrackAlpha = 0.10f
private const val MaxGapDegrees = 180f
private const val HaloMinAlpha = 0.42f
private const val HaloMaxAlpha = 0.85f

/** Where the halo's falloff bends, as a fraction of its band, and how bright it is there. */
private const val HaloKnee = 0.55f
private const val HaloKneeAlpha = 0.32f
private const val HeadGlowAlpha = 0.75f
private const val HaloFadeDegrees = 16f
private const val HeadGlowReach = 0.7f
private const val HeadWhiteness = 0.6f
private const val HeadSize = 0.95f
private const val AmbientStrokeFraction = 0.5f
private const val MinStepMillis = 16L
private const val MaxStepMillis = 1_000L
