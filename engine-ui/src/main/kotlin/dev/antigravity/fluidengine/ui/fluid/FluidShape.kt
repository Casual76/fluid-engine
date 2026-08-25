package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
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
    // Below this size the outline is a rounded rect, and it is a rendering decision, not a design
    // retreat. A generic path this small goes through Skia's small-path atlas: a mask rasterised on
    // the **CPU**, cached with the path's subpixel phase in the key — and a scrolling list changes
    // that phase every frame, so every badge, pill and icon tile on screen re-rasterised and
    // re-uploaded its mask on every frame of every scroll. A hundred masks a frame is what made a
    // tablet's notice board scroll at five frames a second. `Outline.Rounded` is a native GPU
    // primitive with none of that — and at these sizes the smoothing it gives up sits inside a
    // pixel: the continuous ramp only becomes visible at card scale, which is exactly where the
    // path form is kept.
    if (size.maxDimension < SmallShapeCutoffPx) {
      return Outline.Rounded(
        RoundRect(
          rect = Rect(Offset.Zero, size),
          topLeft = roundedEquivalent(if (layoutDirection == LayoutDirection.Rtl) topEnd else topStart, size),
          topRight = roundedEquivalent(if (layoutDirection == LayoutDirection.Rtl) topStart else topEnd, size),
          bottomRight = roundedEquivalent(if (layoutDirection == LayoutDirection.Rtl) bottomStart else bottomEnd, size),
          bottomLeft = roundedEquivalent(if (layoutDirection == LayoutDirection.Rtl) bottomEnd else bottomStart, size),
        ),
      )
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

    /**
     * Il raggio circolare che *sembra* uguale a un raggio continuo dello stesso numero.
     *
     * I raggi di `FluidRadius` sono calibrati per gli angoli continui, che a parità di numero
     * sembrano più stretti perché spalmano la curvatura lungo il lato. Riusarli tali e quali su un
     * rettangolo arrotondato gonfia visibilmente ogni angolo — è successo, e si vedeva su ogni
     * badge e piastrella dell'app. Una capsula invece non si tocca: lì il raggio è metà del lato
     * per definizione, e ridurlo aprirebbe angoli dove non ne esistono.
     */
    internal fun roundedEquivalent(continuousRadius: Float, size: Size): CornerRadius {
      val half = size.minDimension / 2f
      val radius = if (continuousRadius >= half) continuousRadius else continuousRadius * RoundedEquivalentFactor
      return CornerRadius(radius)
    }

    /** Quanto si stringe un raggio continuo per sembrare uguale da circolare. */
    internal const val RoundedEquivalentFactor: Float = 0.82f

    /**
     * Device pixels. Chosen to mirror the ceiling of Skia's small-path atlas: any generic path
     * whose bounds fit under roughly 256 px goes through the CPU mask atlas, and anything above it
     * is drawn by the GPU's analytic renderers — so this is precisely the population for which the
     * path form was being paid for in per-frame mask rasterisation, and the population small
     * enough that the smoothing it encodes is invisible anyway.
     */
    internal const val SmallShapeCutoffPx: Float = 256f
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

/**
 * Kept as an alias so nothing has to remember which shapes glass is allowed to take.
 *
 * There used to be a real distinction here: the old rim shader only understood circular corners, so
 * anything refractive had to give up the design system's continuous ones. The lens reads corner radii
 * off any `CornerBasedShape` now and its gradient is smoothed well past the corner, so glass and
 * everything else finally use the same silhouette.
 */
internal val FluidGlassCapsuleShape: Shape get() = FluidCapsuleShape

internal fun FluidGlassRoundedShape(radius: Dp): Shape = ContinuousCornerShape(radius)

/**
 * Aggiunge a questo path un rettangolo a raccordo continuo posato su [rect], come **sotto-path**.
 *
 * Esiste per Fluid-physics: una silhouette a riposo non riempie necessariamente il proprio nodo —
 * un pezzo può stare ovunque dentro la superficie che lo ospita — quindi serve il tracciato
 * continuo a coordinate arbitrarie, non solo l'`Outline` da (0,0). Stesso emettitore d'angolo
 * della [ContinuousCornerShape], quindi stessa curva al pixel.
 */
internal fun Path.addContinuousRoundRect(
  rect: Rect,
  topLeft: Float,
  topRight: Float,
  bottomRight: Float,
  bottomLeft: Float,
  smoothing: Float = ContinuousCornerShape.IosSmoothing,
) {
  val size = Size(rect.width, rect.height)
  if (topLeft + topRight + bottomRight + bottomLeft <= 0f || size.minDimension <= 0f) {
    addRect(rect)
    return
  }
  continuousCorner(Offset(rect.right, rect.top), Right, Down, topRight, size, smoothing, moveTo = true)
  continuousCorner(Offset(rect.right, rect.bottom), Down, Left, bottomRight, size, smoothing)
  continuousCorner(Offset(rect.left, rect.bottom), Left, Up, bottomLeft, size, smoothing)
  continuousCorner(Offset(rect.left, rect.top), Up, Right, topLeft, size, smoothing)
  close()
}

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
  // The corner consumes (1 + s) * r of each edge and only half the shorter side is available, so
  // something has to give before two adjacent corners overlap. It has to be the *smoothing*, not the
  // radius: clamping the radius instead — which is what this did — meant a 50% corner could only
  // ever reach 62.5% of the half-side, so a capsule came out as a squircle and every pill in the
  // interface had visibly flat ends. Smoothing runs out exactly when the corner becomes a true
  // semicircle, which is the same thing Apple's own capsule does.
  val budget = size.minDimension / 2f
  val radius = min(requestedRadius, budget).coerceAtLeast(0f)
  val s = if (radius <= 0f) {
    0f
  } else {
    smoothing.coerceIn(0f, 1f).coerceAtMost((budget / radius - 1f).coerceAtLeast(0f))
  }

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
