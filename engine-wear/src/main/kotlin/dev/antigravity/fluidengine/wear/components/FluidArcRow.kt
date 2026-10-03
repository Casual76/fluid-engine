package dev.antigravity.fluidengine.wear.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lays its children out along an arc of the round screen, left to right.
 *
 * A row of small actions that hugs the bezel instead of cutting a straight line across the circle:
 * the output, browse and add buttons under a player, where a straight row would either clip its
 * outer items against the edge or waste the middle. Each child is centred on the arc, so items of
 * different sizes still line up along the curve.
 *
 * @param radiusFraction distance of each child's centre from the screen's centre, as a fraction of
 *   the screen's radius.
 * @param centerAngle where the middle of the row sits, in degrees, clockwise from three o'clock:
 *   90 is the bottom of the screen, 270 the top.
 * @param spacing degrees between neighbouring children.
 */
@Composable
fun FluidArcRow(
  modifier: Modifier = Modifier,
  radiusFraction: Float = DefaultArcRadiusFraction,
  centerAngle: Float = 90f,
  spacing: Float = 45f,
  content: @Composable () -> Unit,
) {
  Layout(content = content, modifier = modifier) { measurables, constraints ->
    val loose = constraints.copy(minWidth = 0, minHeight = 0)
    val placeables = measurables.map { it.measure(loose) }
    val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.sumOf { it.width }
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else width
    val radius = min(width, height) / 2f * radiusFraction
    val angles = arcAngles(placeables.size, centerAngle, spacing)
    layout(width, height) {
      placeables.forEachIndexed { index, placeable ->
        val (x, y) = arcPoint(width / 2f, height / 2f, radius, angles[index])
        placeable.place((x - placeable.width / 2f).roundToInt(), (y - placeable.height / 2f).roundToInt())
      }
    }
  }
}

/**
 * The angle of each of [count] children, ordered so the first is the leftmost.
 *
 * On the lower half of the circle the angle has to *decrease* for x to grow (135° is lower-left,
 * 45° lower-right); on the upper half it increases. Picking the direction from the centre angle is
 * what lets the same row be used above and below.
 */
internal fun arcAngles(count: Int, centerAngle: Float, spacing: Float): List<Float> {
  if (count <= 0) return emptyList()
  val lowerHalf = sin(Math.toRadians(centerAngle.toDouble())) >= 0
  val direction = if (lowerHalf) -1f else 1f
  val middle = (count - 1) / 2f
  return List(count) { index -> centerAngle + direction * (index - middle) * spacing }
}

/** The point at [angle] degrees on a circle, in screen coordinates (y grows downwards). */
internal fun arcPoint(centerX: Float, centerY: Float, radius: Float, angle: Float): Pair<Float, Float> {
  val radians = Math.toRadians(angle.toDouble())
  return (centerX + radius * cos(radians).toFloat()) to (centerY + radius * sin(radians).toFloat())
}

/** Where the bottom row of a player sits: inside the bezel, clear of the transport. */
const val DefaultArcRadiusFraction = 0.71f
