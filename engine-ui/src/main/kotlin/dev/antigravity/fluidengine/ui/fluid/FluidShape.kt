package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * A rounded rectangle whose corners are *continuous* rather than circular.
 *
 * A circular corner joins a straight edge to an arc, so curvature jumps from zero to `1/r` at a
 * single point. The eye reads that discontinuity as a faint crease, and it is most of why a Material
 * card and an iOS card look different even at an identical radius. A continuous corner ramps the
 * curvature up and back down instead, leaving no seam — it also spreads further along each edge,
 * which is why it has to be drawn a little larger to read as the same size.
 *
 * The construction is the one Figma documented for its corner-smoothing control: a smoothing curve,
 * a shortened circular arc, and the mirrored smoothing curve. At [smoothing] `0` it degenerates to
 * an ordinary circular corner; iOS sits at roughly `0.6`.
 */
@Stable
class ContinuousCornerShape(
  topStart: CornerSize,
  topEnd: CornerSize,
  bottomEnd: CornerSize,
  bottomStart: CornerSize,
  private val smoothing: Float = IosSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

  override fun copy(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
  ): ContinuousCornerShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

  override fun createOutline(
    size: Size,
    topStart: Float,
    topEnd: Float,
    bottomEnd: Float,
    bottomStart: Float,
    layoutDirection: LayoutDirection,
  ): Outline {
    if (topStart + topEnd + bottomEnd + bottomStart == 0f || size.minDimension == 0f) {
      return Outline.Rectangle(Rect(Offset.Zero, size))
    }
    val rtl = layoutDirection == LayoutDirection.Rtl
    val topLeft = if (rtl) topEnd else topStart
    val topRight = if (rtl) topStart else topEnd
    val bottomRight = if (rtl) bottomStart else bottomEnd
    val bottomLeft = if (rtl) bottomEnd else bottomStart

    val path = Path()
    // Clockwise from the top-right corner. Each corner is handed the direction it is entered from
    // and the direction it leaves along, so one emitter serves all four without special cases.
    path.continuousCorner(Offset(size.width, 0f), Right, Down, topRight, size, smoothing, moveTo = true)
    path.continuousCorner(Offset(size.width, size.height), Down, Left, bottomRight, size, smoothing)
    path.continuousCorner(Offset(0f, size.height), Left, Up, bottomLeft, size, smoothing)
    path.continuousCorner(Offset(0f, 0f), Up, Right, topLeft, size, smoothing)
    path.close()
    return Outline.Generic(path)
  }

  override fun toString(): String =
    "ContinuousCornerShape(topStart=$topStart, topEnd=$topEnd, bottomEnd=$bottomEnd, bottomStart=$bottomStart)"

  companion object {
    /** How far Apple pushes the curvature ramp. Figma's slider calls this 60%. */
    const val IosSmoothing: Float = 0.6f
  }
}

fun ContinuousCornerShape(
  radius: Dp,
  smoothing: Float = ContinuousCornerShape.IosSmoothing,
): ContinuousCornerShape = ContinuousCornerShape(
  CornerSize(radius),
  CornerSize(radius),
  CornerSize(radius),
  CornerSize(radius),
  smoothing,
)

fun ContinuousCornerShape(
  topStart: Dp = 0.dp,
  topEnd: Dp = 0.dp,
  bottomEnd: Dp = 0.dp,
  bottomStart: Dp = 0.dp,
  smoothing: Float = ContinuousCornerShape.IosSmoothing,
): ContinuousCornerShape = ContinuousCornerShape(
  CornerSize(topStart),
  CornerSize(topEnd),
  CornerSize(bottomEnd),
  CornerSize(bottomStart),
  smoothing,
)

/**
 * A capsule: the radius is always half the shorter side.
 *
 * Switches, segmented controls and pills use this — iOS never gives them a fixed radius, and a
 * fixed one is immediately visible as soon as the control changes height.
 */
val FluidCapsuleShape: Shape =
  ContinuousCornerShape(CornerSize(50), CornerSize(50), CornerSize(50), CornerSize(50))

private val Right = Offset(1f, 0f)
private val Down = Offset(0f, 1f)
private val Left = Offset(-1f, 0f)
private val Up = Offset(0f, -1f)

/**
 * Emits one corner into [this].
 *
 * [incoming] is the direction of travel arriving at [corner], [outgoing] the direction leaving it.
 * Every offset below is expressed in that local frame.
 */
private fun Path.continuousCorner(
  corner: Offset,
  incoming: Offset,
  outgoing: Offset,
  requestedRadius: Float,
  size: Size,
  smoothing: Float,
  moveTo: Boolean = false,
) {
  val s = smoothing.coerceIn(0f, 1f)
  // The corner consumes (1 + s) * r of each edge, so the radius has to give way before two adjacent
  // corners would overlap — otherwise a short pill folds in on itself.
  val budget = size.minDimension / 2f
  val radius = min(requestedRadius, budget / (1f + s)).coerceAtLeast(0f)

  val start = corner - incoming * ((1f + s) * radius)
  if (moveTo) moveTo(start.x, start.y) else lineTo(start.x, start.y)
  if (radius <= 0f) return

  val p = (1f + s) * radius
  val arcSweepDeg = 90f * (1f - s)
  // Length the shortened arc covers along each of the two edge directions.
  val arcChord = sin(arcSweepDeg / 2f * DegToRad) * radius * Sqrt2
  val alphaDeg = (90f - arcSweepDeg) / 2f
  val betaDeg = 45f * s
  val handle = radius * tan(betaDeg / 2f * DegToRad)
  val c = handle * cos(alphaDeg * DegToRad)
  val d = c * tan(alphaDeg * DegToRad)
  val b = (p - arcChord - c - d) / 3f
  val a = 2f * b

  fun local(alongIncoming: Float, alongOutgoing: Float): Offset =
    start + incoming * alongIncoming + outgoing * alongOutgoing

  // Curvature ramping up, away from the straight edge.
  val arcStart = local(a + b + c, d)
  cubic(local(a, 0f), local(a + b, 0f), arcStart)

  // The circular section, as a single cubic: exact to well under a pixel for sweeps below 90 degrees.
  val arcEnd = arcStart + incoming * arcChord + outgoing * arcChord
  val tangentAt = { deg: Float -> incoming * cos(deg * DegToRad) + outgoing * sin(deg * DegToRad) }
  val k = 4f / 3f * tan(arcSweepDeg / 4f * DegToRad) * radius
  cubic(
    arcStart + tangentAt(alphaDeg) * k,
    arcEnd - tangentAt(alphaDeg + arcSweepDeg) * k,
    arcEnd,
  )

  // …and ramping back down into the next straight edge. The mirror of the first curve: both control
  // points sit on the outgoing edge line, so the corner leaves along it with zero curvature.
  cubic(
    local(p, d + arcChord + c),
    local(p, d + arcChord + b + c),
    local(p, p),
  )
}

private fun Path.cubic(c1: Offset, c2: Offset, end: Offset) {
  cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
}

private val Sqrt2 = sqrt(2f)
private const val DegToRad = (PI / 180.0).toFloat()

/**
 * The radii the app uses.
 *
 * They run larger than the Material equivalents on purpose: a continuous corner of the same nominal
 * radius reads visually tighter, because the curve spends longer near the edge.
 */
object FluidRadius {
  val Small: Dp = 10.dp
  val Control: Dp = 12.dp
  val Card: Dp = 18.dp
  val Group: Dp = 22.dp
  val Sheet: Dp = 38.dp
}
