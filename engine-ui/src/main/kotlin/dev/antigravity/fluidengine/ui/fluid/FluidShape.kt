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
  smoothing: Float = IosSmoothing,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {

  /**
   * Quanto questa forma spalma la curvatura lungo i lati, come è stata chiesta (non ancora limitata dal lato:
   * quello lo fa [continuousCornerSmoothing], angolo per angolo).
   *
   * È leggibile dall'engine perché nel materiale `Liquid` la sagoma non la disegna più un path ma il campo
   * dello shader (`glass/field/GlassFieldKernel.kt`), che deve sapere quale curva ripetere: senza questo
   * numero il campo potrebbe solo supporre [IosSmoothing], e una forma costruita con un altro valore avrebbe
   * un vetro con un angolo diverso dal suo contorno.
   */
  internal val smoothing: Float = smoothing

  override fun copy(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
  ): ContinuousCornerShape = ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart, smoothing)

  // Uguaglianza per valore, come `RoundedCornerShape`: due forme con gli stessi angoli e lo stesso raccordo sono la
  // stessa forma. Senza, una forma creata in composizione era ogni volta una chiave nuova per `remember`, e chi ne
  // dipende (la lastra del vetro per prima) si rifaceva a ogni ricomposizione (F2.3: era un anello del ciclo di
  // ridisegno a riposo della scheda «Materiale»).
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ContinuousCornerShape) return false
    return topStart == other.topStart &&
      topEnd == other.topEnd &&
      bottomEnd == other.bottomEnd &&
      bottomStart == other.bottomStart &&
      smoothing == other.smoothing
  }

  override fun hashCode(): Int {
    var result = topStart.hashCode()
    result = 31 * result + topEnd.hashCode()
    result = 31 * result + bottomEnd.hashCode()
    result = 31 * result + bottomStart.hashCode()
    result = 31 * result + smoothing.hashCode()
    return result
  }

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
 * Quanti numeri restituisce [continuousCornerPoints] per un angolo con raggio: dieci punti, cioè la partenza
 * e tre cubiche (due maniglie e l'arrivo ciascuna).
 */
internal const val ContinuousCornerPointFloats: Int = 20

/**
 * Lo `smoothing` che un angolo riesce davvero a spendere.
 *
 * Un angolo continuo consuma `(1 + s) * r` di ciascun lato e ne ha a disposizione mezzo lato corto: quando
 * non basta cede lo *smoothing*, non il raggio. Se cedesse il raggio un angolo al 50% arriverebbe solo al
 * 62,5% del mezzo lato: una capsula verrebbe fuori come un quadrato smussato, e ogni pillola dell'interfaccia
 * avrebbe le estremità piatte (è successo). Lo smoothing finisce esattamente quando l'angolo diventa un
 * semicerchio, che è ciò che fa la capsula di Apple.
 *
 * È una funzione a parte perché la stessa regola la ripete il campo dello shader
 * (`GlassFieldGeometry.effectiveSmoothing`), e un test le tiene uguali.
 *
 * @param minDimension il lato corto della forma, in pixel.
 */
internal fun continuousCornerSmoothing(requestedRadius: Float, minDimension: Float, smoothing: Float): Float {
  val budget = minDimension / 2f
  val radius = min(requestedRadius, budget).coerceAtLeast(0f)
  return if (radius <= 0f) {
    0f
  } else {
    smoothing.coerceIn(0f, 1f).coerceAtMost((budget / radius - 1f).coerceAtLeast(0f))
  }
}

/**
 * I punti di un angolo continuo, senza un `Path`: è la geometria che [continuousCorner] disegna, in una
 * funzione pura.
 *
 * Sta fuori dall'emettitore perché il materiale `Liquid` ritaglia il vetro con un campo di distanza che
 * *approssima* questa curva (`glass/field/GlassFieldKernel.kt`), e quanto la approssima si può misurare solo
 * avendo la curva vera in mano su JVM, dove un `Path` di Android non esiste
 * (`ContinuousCornerFieldTest`). I conti sono quelli dell'emettitore di sempre, nello stesso ordine: il path
 * che ne esce è lo stesso al bit.
 *
 * [incoming] è la direzione di marcia con cui si arriva a [corner], [outgoing] quella con cui si riparte.
 *
 * @param minDimension il lato corto della forma, in pixel.
 * @return `x, y` di ogni punto, nell'ordine in cui il path li consuma: la partenza sul lato entrante, poi
 *   per ciascuna delle tre cubiche (curvatura che sale, arco accorciato, curvatura che scende) le due
 *   maniglie e l'arrivo: [ContinuousCornerPointFloats] numeri. Con raggio zero la sola partenza, cioè
 *   l'angolo vivo: due numeri.
 */
internal fun continuousCornerPoints(
  corner: Offset,
  incoming: Offset,
  outgoing: Offset,
  requestedRadius: Float,
  minDimension: Float,
  smoothing: Float,
): FloatArray {
  // The corner consumes (1 + s) * r of each edge and only half the shorter side is available, so
  // something has to give before two adjacent corners overlap. It has to be the *smoothing*, not the
  // radius: see continuousCornerSmoothing.
  val budget = minDimension / 2f
  val radius = min(requestedRadius, budget).coerceAtLeast(0f)
  val s = continuousCornerSmoothing(requestedRadius, minDimension, smoothing)

  val start = corner - incoming * ((1f + s) * radius)
  if (radius <= 0f) return floatArrayOf(start.x, start.y)

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

  val out = FloatArray(ContinuousCornerPointFloats)
  var at = 0
  fun put(point: Offset) {
    out[at++] = point.x
    out[at++] = point.y
  }
  put(start)

  // Curvature ramping up, away from the straight edge.
  val arcStart = local(a + b + c, d)
  put(local(a, 0f))
  put(local(a + b, 0f))
  put(arcStart)

  // The circular section, as a single cubic: exact to well under a pixel for sweeps below 90 degrees.
  val arcEnd = arcStart + incoming * arcChord + outgoing * arcChord
  val tangentAt = { deg: Float -> incoming * cos(deg * DegToRad) + outgoing * sin(deg * DegToRad) }
  val k = 4f / 3f * tan(arcSweepDeg / 4f * DegToRad) * radius
  put(arcStart + tangentAt(alphaDeg) * k)
  put(arcEnd - tangentAt(alphaDeg + arcSweepDeg) * k)
  put(arcEnd)

  // ...and ramping back down into the next straight edge. The mirror of the first curve: both control
  // points sit on the outgoing edge line, so the corner leaves along it with zero curvature.
  put(local(p, d + arcChord + c))
  put(local(p, d + arcChord + b + c))
  put(local(p, p))
  return out
}

/**
 * Emits one corner into [this].
 *
 * [incoming] is the direction of travel arriving at [corner], [outgoing] the direction leaving it.
 * The geometry is [continuousCornerPoints]; this only hands it to the path.
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
  val points = continuousCornerPoints(corner, incoming, outgoing, requestedRadius, size.minDimension, smoothing)
  if (moveTo) moveTo(points[0], points[1]) else lineTo(points[0], points[1])
  if (points.size < ContinuousCornerPointFloats) return
  cubicTo(points[2], points[3], points[4], points[5], points[6], points[7])
  cubicTo(points[8], points[9], points[10], points[11], points[12], points[13])
  cubicTo(points[14], points[15], points[16], points[17], points[18], points[19])
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
