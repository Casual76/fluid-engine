package dev.antigravity.fluidengine.ink

import androidx.compose.ui.geometry.Rect
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// F8.16 · La geometria della gomma e del lazo, sui punti del modello (non sulle mesh di androidx.ink): cosi' gomma e lazo
// fanno la stessa cosa sul telefono e sul computer, e si provano senza la libreria nativa.

internal object InkGeometry {
  /**
   * Di quanto un tratto puo' superare il suo spessore nominale: la penna di androidx.ink cresce fino a ×1,5 con la
   * pressione, ×1,17 col verso e ×1,25 con l'accelerazione (`StockBrushes.pressurePen` V1), la matita di questo modulo
   * fino a ×2,2 con l'inclinazione. Serve solo a non ritagliare un tratto nel disegno e nella consegna.
   */
  const val MaxSizeGain: Float = 2.5f

  fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(bx - ax, by - ay)

  /** Il quadrato della distanza del punto (px, py) dal segmento a–b. */
  fun pointSegmentDistanceSq(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val len = dx * dx + dy * dy
    var t = if (len <= 0f) 0f else ((px - ax) * dx + (py - ay) * dy) / len
    t = t.coerceIn(0f, 1f)
    val cx = ax + t * dx - px
    val cy = ay + t * dy - py
    return cx * cx + cy * cy
  }

  /** Il quadrato della distanza fra i segmenti a–b e c–d (zero se si incrociano). */
  fun segmentSegmentDistanceSq(
    ax: Float, ay: Float, bx: Float, by: Float,
    cx: Float, cy: Float, dx: Float, dy: Float,
  ): Float {
    if (segmentsCross(ax, ay, bx, by, cx, cy, dx, dy)) return 0f
    return min(
      min(pointSegmentDistanceSq(ax, ay, cx, cy, dx, dy), pointSegmentDistanceSq(bx, by, cx, cy, dx, dy)),
      min(pointSegmentDistanceSq(cx, cy, ax, ay, bx, by), pointSegmentDistanceSq(dx, dy, ax, ay, bx, by)),
    )
  }

  private fun cross(ox: Float, oy: Float, ax: Float, ay: Float, bx: Float, by: Float): Float =
    (ax - ox) * (by - oy) - (ay - oy) * (bx - ox)

  private fun segmentsCross(
    ax: Float, ay: Float, bx: Float, by: Float,
    cx: Float, cy: Float, dx: Float, dy: Float,
  ): Boolean {
    val d1 = cross(cx, cy, dx, dy, ax, ay)
    val d2 = cross(cx, cy, dx, dy, bx, by)
    val d3 = cross(ax, ay, bx, by, cx, cy)
    val d4 = cross(ax, ay, bx, by, dx, dy)
    return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) && ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
  }

  /** Il riquadro della capsula di raggio [radius] attorno al segmento a–b. */
  fun capsuleBounds(ax: Float, ay: Float, bx: Float, by: Float, radius: Float): Rect =
    Rect(min(ax, bx) - radius, min(ay, by) - radius, max(ax, bx) + radius, max(ay, by) + radius)

  /**
   * Vero se la capsula della gomma (il segmento a–b con raggio [radius]) tocca il tratto, spessore compreso. Un tratto di
   * un punto solo (un puntino) si tocca come un disco.
   */
  fun capsuleHitsStroke(ax: Float, ay: Float, bx: Float, by: Float, radius: Float, stroke: FluidInkStroke): Boolean {
    if (!stroke.bounds.overlaps(capsuleBounds(ax, ay, bx, by, radius))) return false
    val p = stroke.points
    val reach = radius + stroke.size / 2f
    val reachSq = reach * reach
    if (p.size == 1) return pointSegmentDistanceSq(p.x(0), p.y(0), ax, ay, bx, by) <= reachSq
    for (i in 1 until p.size) {
      if (segmentSegmentDistanceSq(ax, ay, bx, by, p.x(i - 1), p.y(i - 1), p.x(i), p.y(i)) <= reachSq) return true
    }
    return false
  }

  /**
   * La gomma a pixel: i pezzi di [stroke] che restano fuori dalla capsula a–b di raggio [radius]. Null se la gomma non
   * lo tocca; una lista vuota se lo toglie tutto. I segmenti piu' lunghi di un terzo del raggio si suddividono prima,
   * cosi' il taglio cade vicino al bordo della gomma anche su un tratto dai punti radi. I pezzi nuovi prendono
   * l'identita' da [nextId].
   */
  fun eraseArea(
    stroke: FluidInkStroke,
    ax: Float, ay: Float, bx: Float, by: Float,
    radius: Float,
    nextId: () -> Long,
  ): List<FluidInkStroke>? {
    if (!capsuleHitsStroke(ax, ay, bx, by, radius, stroke)) return null
    val points = resample(stroke.points, max(radius / 3f, 0.5f))
    val reach = radius + stroke.size / 2f * 0.6f
    val reachSq = reach * reach
    var any = false
    for (i in 0 until points.size) {
      if (pointSegmentDistanceSq(points.x(i), points.y(i), ax, ay, bx, by) <= reachSq) { any = true; break }
    }
    // Sfiorato dal bordo ma nessun punto dentro: il tratto resta com'e', con la sua identita'.
    if (!any) return null
    val pieces = ArrayList<FluidInkStroke>()
    var start = -1
    for (i in 0 until points.size) {
      val erased = pointSegmentDistanceSq(points.x(i), points.y(i), ax, ay, bx, by) <= reachSq
      if (!erased && start < 0) start = i
      if ((erased || i == points.size - 1) && start >= 0) {
        val end = if (erased) i else i + 1
        if (end - start >= 2 || (end - start == 1 && points.size == 1)) {
          pieces += stroke.copy(id = nextId(), points = points.slice(start, end), shape = null)
        }
        start = -1
      }
    }
    return pieces
  }

  /** I punti con i segmenti piu' lunghi di [step] suddivisi (pressione, inclinazione e tempo interpolati). */
  fun resample(points: FluidInkPoints, step: Float): FluidInkPoints {
    if (points.size < 2) return points
    var extra = 0
    for (i in 1 until points.size) {
      val d = distance(points.x(i - 1), points.y(i - 1), points.x(i), points.y(i))
      if (d > step) extra += ceil(d / step).toInt() - 1
    }
    if (extra == 0) return points
    val builder = FluidInkPointsBuilder(points.size + extra)
    fun put(i: Int) = builder.add(points.x(i), points.y(i), points.time(i), points.pressure(i), points.tilt(i), points.orientation(i))
    put(0)
    for (i in 1 until points.size) {
      val d = distance(points.x(i - 1), points.y(i - 1), points.x(i), points.y(i))
      if (d > step) {
        val n = ceil(d / step).toInt()
        for (k in 1 until n) {
          val t = k.toFloat() / n
          builder.add(
            lerp(points.x(i - 1), points.x(i), t), lerp(points.y(i - 1), points.y(i), t),
            lerp(points.time(i - 1), points.time(i), t),
            lerpOptional(points.pressure(i - 1), points.pressure(i), t),
            lerpOptional(points.tilt(i - 1), points.tilt(i), t),
            lerpOptional(points.orientation(i - 1), points.orientation(i), t),
          )
        }
      }
      put(i)
    }
    return builder.build()
  }

  fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  private fun lerpOptional(a: Float, b: Float, t: Float): Float = if (a < 0f || b < 0f) FluidInkPoints.None else lerp(a, b, t)

  /** Il punto (x, y) dentro il poligono [polygon] (coppie x, y), con la regola pari-dispari. */
  fun pointInPolygon(x: Float, y: Float, polygon: FloatArray, count: Int = polygon.size / 2): Boolean {
    if (count < 3) return false
    var inside = false
    var j = count - 1
    for (i in 0 until count) {
      val xi = polygon[i * 2]
      val yi = polygon[i * 2 + 1]
      val xj = polygon[j * 2]
      val yj = polygon[j * 2 + 1]
      if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
      j = i
    }
    return inside
  }

  /**
   * Vero se il lazo [polygon] prende il tratto: almeno meta' dei suoi punti sta dentro, come il lazo di PencilKit, che
   * sceglie quello che si circonda e non quello che si sfiora (osservato in Note e Markup, iOS 26, fiducia media).
   */
  fun lassoTakes(stroke: FluidInkStroke, polygon: FloatArray, count: Int, polygonBounds: Rect): Boolean {
    if (!stroke.points.bounds.overlaps(polygonBounds) && stroke.points.size > 1) return false
    val p = stroke.points
    var inside = 0
    for (i in 0 until p.size) if (pointInPolygon(p.x(i), p.y(i), polygon, count)) inside++
    return inside * 2 >= p.size && inside > 0
  }

  /** Il riquadro di un poligono (coppie x, y). */
  fun polygonBounds(polygon: FloatArray, count: Int = polygon.size / 2): Rect {
    if (count == 0) return Rect.Zero
    var left = Float.MAX_VALUE
    var top = Float.MAX_VALUE
    var right = -Float.MAX_VALUE
    var bottom = -Float.MAX_VALUE
    for (i in 0 until count) {
      left = min(left, polygon[i * 2]); right = max(right, polygon[i * 2])
      top = min(top, polygon[i * 2 + 1]); bottom = max(bottom, polygon[i * 2 + 1])
    }
    return Rect(left, top, right, bottom)
  }

  /** Il riquadro che contiene tutti i [strokes] (Rect.Zero se non ce ne sono). */
  fun union(strokes: Collection<FluidInkStroke>): Rect {
    var r: Rect? = null
    for (s in strokes) r = r?.let { Rect(min(it.left, s.bounds.left), min(it.top, s.bounds.top), max(it.right, s.bounds.right), max(it.bottom, s.bounds.bottom)) } ?: s.bounds
    return r ?: Rect.Zero
  }

  /** La proiezione del punto (x, y) sulla retta per (ox, oy) con direzione (ux, uy) normalizzata. */
  fun projectOnLine(x: Float, y: Float, ox: Float, oy: Float, ux: Float, uy: Float, out: FloatArray) {
    val t = (x - ox) * ux + (y - oy) * uy
    out[0] = ox + ux * t
    out[1] = oy + uy * t
  }

  /** La distanza con segno del punto dalla retta (ox, oy) + t·(ux, uy): positiva a destra del verso. */
  fun signedDistanceToLine(x: Float, y: Float, ox: Float, oy: Float, ux: Float, uy: Float): Float =
    (x - ox) * (-uy) + (y - oy) * ux

  fun length(x: Float, y: Float): Float = sqrt(x * x + y * y)
}
