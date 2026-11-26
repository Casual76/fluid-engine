package dev.antigravity.fluidengine.ink

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// F8.16 · Le forme riconosciute: si disegna una forma e si tiene fermo il dito (o la penna) alla fine, e il tratto
// diventa la forma perfetta, come in Note e Markup di iOS 14 e seguenti («Disegna una forma perfetta»). Il riconoscimento
// e' qui, sui punti del modello: geometria semplice, nessuna rete neurale, e si prova sul computer.

/** Una forma riconosciuta: il tipo e il contorno perfetto (coppie x, y, gia' fitto), chiuso o aperto. */
internal class InkRecognizedShape(val kind: FluidInkShapeKind, val outline: FloatArray, val closed: Boolean)

/**
 * Il riconoscitore. I numeri sono tarati a occhio (25/11/2026) su tratti fatti col dito e con la penna nelle prove di
 * questo modulo: Apple non li pubblica. La fiducia e' media sul comportamento (si riconosce quello che Note riconosce:
 * linea, cerchio ed ellisse, triangolo, rettangolo e quadrato, poligoni con pochi lati) e bassa sulle soglie.
 */
internal object InkShapeRecognizer {
  /** Un tratto chiuso torna vicino all'inizio: entro questa parte della diagonale del suo riquadro. */
  const val ClosedGap = 0.25f

  /** Un tratto chiuso e' lungo almeno questo multiplo della diagonale (un cerchio fa 2,22, un quadrato 2,83). */
  const val ClosedLength = 1.8f

  /** Una linea si scosta dalla corda al piu' di questa parte della corda. */
  const val LineDeviation = 0.07f

  /** La semplificazione dei lati (Ramer–Douglas–Peucker): questa parte della diagonale. */
  const val CornerTolerance = 0.075f

  /** Errore medio dell'ellisse (sul raggio normalizzato) sotto cui un tratto chiuso e' di certo un'ellisse. */
  const val EllipseSure = 0.06f

  /** Errore oltre cui non e' un'ellisse. */
  const val EllipseMax = 0.12f

  /** Errore medio del poligono (in diagonali) sotto cui i lati sono dritti. */
  const val PolygonMax = 0.04f

  /** Un'ellisse con gli assi entro questo rapporto diventa un cerchio, un rettangolo coi lati cosi' un quadrato. */
  const val RoundRatio = 0.88f

  /** Gli angoli di un rettangolo stanno entro questa distanza da 90°. */
  const val RightAngleSlack = 18f

  /** Un'ellisse o un rettangolo entro questi gradi dall'orizzontale si raddrizza. */
  const val AxisSnapDegrees = 6f

  fun recognize(points: FluidInkPoints, minSize: Float): InkRecognizedShape? {
    val n = points.size
    if (n < 4) return null
    val xy = FloatArray(n * 2)
    for (i in 0 until n) {
      xy[i * 2] = points.x(i)
      xy[i * 2 + 1] = points.y(i)
    }
    return recognize(xy, n, minSize)
  }

  fun recognize(xy: FloatArray, n: Int, minSize: Float): InkRecognizedShape? {
    if (n < 4) return null
    val bounds = InkGeometry.polygonBounds(xy, n)
    val diag = hypot(bounds.width, bounds.height)
    if (diag < minSize) return null
    var length = 0f
    for (i in 1 until n) length += hypot(xy[i * 2] - xy[i * 2 - 2], xy[i * 2 + 1] - xy[i * 2 - 1])
    val gap = hypot(xy[(n - 1) * 2] - xy[0], xy[(n - 1) * 2 + 1] - xy[1])
    val closed = gap < ClosedGap * diag && length > ClosedLength * diag
    if (!closed) return line(xy, n, gap, length, diag)
    val uniform = resampleUniform(xy, n, 96)
    val ellipse = fitEllipse(uniform)
    if (ellipse != null && ellipse.error < EllipseSure) return ellipse.shape(diag)
    val corners = corners(uniform, CornerTolerance * diag)
    if (corners.size in 3..6) {
      val polygon = FloatArray(corners.size * 2) { uniform[corners[it / 2] * 2 + it % 2] }
      val error = polygonError(uniform, polygon) / diag
      if (error < PolygonMax) return polygonShape(polygon, uniform, diag)
    }
    if (ellipse != null && ellipse.error < EllipseMax) return ellipse.shape(diag)
    return null
  }

  private fun line(xy: FloatArray, n: Int, chord: Float, length: Float, diag: Float): InkRecognizedShape? {
    if (chord < 0.6f * diag || length > 1.25f * chord) return null
    val ax = xy[0]
    val ay = xy[1]
    val bx = xy[(n - 1) * 2]
    val by = xy[(n - 1) * 2 + 1]
    var maxDev = 0f
    for (i in 0 until n) maxDev = max(maxDev, sqrt(InkGeometry.pointSegmentDistanceSq(xy[i * 2], xy[i * 2 + 1], ax, ay, bx, by)))
    if (maxDev > LineDeviation * chord) return null
    return InkRecognizedShape(FluidInkShapeKind.Line, dense(floatArrayOf(ax, ay, bx, by), closed = false, step = chord / 32f), closed = false)
  }

  /** [count] punti alla stessa distanza lungo il percorso. */
  fun resampleUniform(xy: FloatArray, n: Int, count: Int): FloatArray {
    var total = 0f
    for (i in 1 until n) total += hypot(xy[i * 2] - xy[i * 2 - 2], xy[i * 2 + 1] - xy[i * 2 - 1])
    val out = FloatArray(count * 2)
    if (total <= 0f) {
      for (i in 0 until count) { out[i * 2] = xy[0]; out[i * 2 + 1] = xy[1] }
      return out
    }
    val step = total / (count - 1)
    out[0] = xy[0]; out[1] = xy[1]
    var written = 1
    var carried = 0f
    var i = 1
    var px = xy[0]
    var py = xy[1]
    while (i < n && written < count) {
      val qx = xy[i * 2]
      val qy = xy[i * 2 + 1]
      val d = hypot(qx - px, qy - py)
      if (carried + d >= step && d > 0f) {
        val t = (step - carried) / d
        px += (qx - px) * t
        py += (qy - py) * t
        out[written * 2] = px
        out[written * 2 + 1] = py
        written++
        carried = 0f
      } else {
        carried += d
        px = qx
        py = qy
        i++
      }
    }
    while (written < count) {
      out[written * 2] = xy[(n - 1) * 2]
      out[written * 2 + 1] = xy[(n - 1) * 2 + 1]
      written++
    }
    return out
  }

  private class EllipseFit(val cx: Float, val cy: Float, val a: Float, val b: Float, val angle: Float, val error: Float) {
    fun shape(diag: Float): InkRecognizedShape {
      var theta = angle
      var ra = a
      var rb = b
      val round = min(ra, rb) / max(ra, rb) >= RoundRatio
      if (round) {
        ra = (ra + rb) / 2f
        rb = ra
        theta = 0f
      } else {
        theta = snapAxis(theta)
      }
      val count = 72
      val out = FloatArray((count + 1) * 2)
      val c = cos(theta)
      val s = sin(theta)
      for (k in 0..count) {
        val t = (k % count) * 2.0 * PI / count - PI / 2
        val u = ra * cos(t).toFloat()
        val v = rb * sin(t).toFloat()
        out[k * 2] = cx + u * c - v * s
        out[k * 2 + 1] = cy + u * s + v * c
      }
      return InkRecognizedShape(if (round) FluidInkShapeKind.Circle else FluidInkShapeKind.Ellipse, out, closed = true)
    }
  }

  private fun fitEllipse(p: FloatArray): EllipseFit? {
    val count = p.size / 2
    var mx = 0f
    var my = 0f
    for (i in 0 until count) { mx += p[i * 2]; my += p[i * 2 + 1] }
    mx /= count; my /= count
    var sxx = 0f
    var syy = 0f
    var sxy = 0f
    for (i in 0 until count) {
      val dx = p[i * 2] - mx
      val dy = p[i * 2 + 1] - my
      sxx += dx * dx; syy += dy * dy; sxy += dx * dy
    }
    val angle = 0.5f * atan2(2f * sxy, sxx - syy)
    val c = cos(angle)
    val s = sin(angle)
    var minU = Float.MAX_VALUE
    var maxU = -Float.MAX_VALUE
    var minV = Float.MAX_VALUE
    var maxV = -Float.MAX_VALUE
    for (i in 0 until count) {
      val dx = p[i * 2] - mx
      val dy = p[i * 2 + 1] - my
      val u = dx * c + dy * s
      val v = -dx * s + dy * c
      minU = min(minU, u); maxU = max(maxU, u)
      minV = min(minV, v); maxV = max(maxV, v)
    }
    val a = (maxU - minU) / 2f
    val b = (maxV - minV) / 2f
    if (a <= 0f || b <= 0f || min(a, b) / max(a, b) < 0.12f) return null
    val cu = (maxU + minU) / 2f
    val cv = (maxV + minV) / 2f
    val cx = mx + cu * c - cv * s
    val cy = my + cu * s + cv * c
    var sum = 0f
    for (i in 0 until count) {
      val dx = p[i * 2] - cx
      val dy = p[i * 2 + 1] - cy
      val u = dx * c + dy * s
      val v = -dx * s + dy * c
      val r = sqrt((u / a) * (u / a) + (v / b) * (v / b))
      sum += abs(r - 1f)
    }
    return EllipseFit(cx, cy, a, b, angle, sum / count)
  }

  /** Gli indici degli angoli di un percorso chiuso campionato in modo uniforme. */
  fun corners(p: FloatArray, tolerance: Float): IntArray {
    val count = p.size / 2
    // Il punto piu' lontano dall'inizio divide l'anello in due catene aperte.
    var far = 0
    var best = -1f
    for (i in 0 until count) {
      val d = hypot(p[i * 2] - p[0], p[i * 2 + 1] - p[1])
      if (d > best) { best = d; far = i }
    }
    val keep = BooleanArray(count)
    keep[0] = true
    keep[far] = true
    rdp(p, 0, far, tolerance, keep)
    rdp(p, far, count - 1, tolerance, keep)
    val list = ArrayList<Int>()
    for (i in 0 until count - 1) if (keep[i]) list += i
    // Toglie i vertici quasi in linea coi vicini (anche l'inizio, se il dito e' partito a meta' di un lato).
    var changed = true
    while (changed && list.size > 3) {
      changed = false
      for (k in list.indices) {
        val prev = list[(k - 1 + list.size) % list.size]
        val next = list[(k + 1) % list.size]
        val cur = list[k]
        val d = sqrt(InkGeometry.pointSegmentDistanceSq(p[cur * 2], p[cur * 2 + 1], p[prev * 2], p[prev * 2 + 1], p[next * 2], p[next * 2 + 1]))
        if (d < tolerance) {
          list.removeAt(k)
          changed = true
          break
        }
      }
    }
    return list.toIntArray()
  }

  private fun rdp(p: FloatArray, from: Int, to: Int, tolerance: Float, keep: BooleanArray) {
    if (to <= from + 1) return
    var index = -1
    var best = -1f
    for (i in from + 1 until to) {
      val d = InkGeometry.pointSegmentDistanceSq(p[i * 2], p[i * 2 + 1], p[from * 2], p[from * 2 + 1], p[to * 2], p[to * 2 + 1])
      if (d > best) { best = d; index = i }
    }
    if (index >= 0 && sqrt(best) > tolerance) {
      keep[index] = true
      rdp(p, from, index, tolerance, keep)
      rdp(p, index, to, tolerance, keep)
    }
  }

  /** La distanza media dei punti [p] dai lati del poligono chiuso [polygon]. */
  private fun polygonError(p: FloatArray, polygon: FloatArray): Float {
    val k = polygon.size / 2
    var sum = 0f
    for (i in 0 until p.size / 2) {
      var best = Float.MAX_VALUE
      for (e in 0 until k) {
        val a = e
        val b = (e + 1) % k
        best = min(best, InkGeometry.pointSegmentDistanceSq(p[i * 2], p[i * 2 + 1], polygon[a * 2], polygon[a * 2 + 1], polygon[b * 2], polygon[b * 2 + 1]))
      }
      sum += sqrt(best)
    }
    return sum / (p.size / 2)
  }

  private fun polygonShape(polygon: FloatArray, uniform: FloatArray, diag: Float): InkRecognizedShape {
    val k = polygon.size / 2
    if (k == 3) return InkRecognizedShape(FluidInkShapeKind.Triangle, dense(polygon, closed = true, step = diag / 40f), closed = true)
    if (k == 4 && rightAngles(polygon)) return rectangle(polygon, uniform, diag)
    return InkRecognizedShape(FluidInkShapeKind.Polygon, dense(polygon, closed = true, step = diag / 40f), closed = true)
  }

  private fun rightAngles(q: FloatArray): Boolean {
    for (i in 0 until 4) {
      val p = (i + 3) % 4
      val nx = (i + 1) % 4
      val ax = q[p * 2] - q[i * 2]
      val ay = q[p * 2 + 1] - q[i * 2 + 1]
      val bx = q[nx * 2] - q[i * 2]
      val by = q[nx * 2 + 1] - q[i * 2 + 1]
      val angle = abs(Math.toDegrees(atan2(ax * by - ay * bx, ax * bx + ay * by).toDouble())).toFloat()
      if (abs(angle - 90f) > RightAngleSlack) return false
    }
    return true
  }

  private fun rectangle(q: FloatArray, uniform: FloatArray, diag: Float): InkRecognizedShape {
    // Il verso dei lati: la media dei quattro, ripiegati su un quarto di giro.
    var sx = 0f
    var sy = 0f
    for (i in 0 until 4) {
      val nx = (i + 1) % 4
      val a = atan2(q[nx * 2 + 1] - q[i * 2 + 1], q[nx * 2] - q[i * 2]) * 4f
      sx += cos(a); sy += sin(a)
    }
    val theta = snapAxis(atan2(sy, sx) / 4f)
    val c = cos(theta)
    val s = sin(theta)
    var minU = Float.MAX_VALUE
    var maxU = -Float.MAX_VALUE
    var minV = Float.MAX_VALUE
    var maxV = -Float.MAX_VALUE
    // Le misure dagli angoli trovati (non dai punti: un tratto che sborda non allarga il rettangolo).
    for (i in 0 until 4) {
      val u = q[i * 2] * c + q[i * 2 + 1] * s
      val v = -q[i * 2] * s + q[i * 2 + 1] * c
      minU = min(minU, u); maxU = max(maxU, u)
      minV = min(minV, v); maxV = max(maxV, v)
    }
    var w = maxU - minU
    var h = maxV - minV
    val cu = (minU + maxU) / 2f
    val cv = (minV + maxV) / 2f
    val square = min(w, h) / max(w, h) >= RoundRatio
    if (square) { w = (w + h) / 2f; h = w }
    val corners = floatArrayOf(-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2)
    val out = FloatArray(8)
    for (i in 0 until 4) {
      val u = cu + corners[i * 2]
      val v = cv + corners[i * 2 + 1]
      out[i * 2] = u * c - v * s
      out[i * 2 + 1] = u * s + v * c
    }
    // Parte dall'angolo piu' vicino a dove e' partito il dito.
    var startCorner = 0
    var best = Float.MAX_VALUE
    for (i in 0 until 4) {
      val d = hypot(out[i * 2] - uniform[0], out[i * 2 + 1] - uniform[1])
      if (d < best) { best = d; startCorner = i }
    }
    val ordered = FloatArray(8) { out[((startCorner + it / 2) % 4) * 2 + it % 2] }
    return InkRecognizedShape(if (square) FluidInkShapeKind.Square else FluidInkShapeKind.Rectangle, dense(ordered, closed = true, step = diag / 40f), closed = true)
  }

  private fun snapAxis(theta: Float): Float {
    val degrees = Math.toDegrees(theta.toDouble()).toFloat()
    val nearest = Math.round(degrees / 90f) * 90f
    return if (abs(degrees - nearest) <= AxisSnapDegrees) Math.toRadians(nearest.toDouble()).toFloat() else theta
  }

  /** I lati del poligono [vertices] suddivisi ogni [step]; chiuso: torna al primo vertice. */
  fun dense(vertices: FloatArray, closed: Boolean, step: Float): FloatArray {
    val k = vertices.size / 2
    val edges = if (closed) k else k - 1
    val out = ArrayList<Float>()
    val safeStep = max(step, 0.5f)
    for (e in 0 until edges) {
      val ax = vertices[e * 2]
      val ay = vertices[e * 2 + 1]
      val b = (e + 1) % k
      val bx = vertices[b * 2]
      val by = vertices[b * 2 + 1]
      val pieces = max(1, ceil(hypot(bx - ax, by - ay) / safeStep).toInt())
      for (j in 0 until pieces) {
        val t = j.toFloat() / pieces
        out += ax + (bx - ax) * t
        out += ay + (by - ay) * t
      }
    }
    val last = if (closed) 0 else k - 1
    out += vertices[last * 2]
    out += vertices[last * 2 + 1]
    return out.toFloatArray()
  }

  /**
   * Il tratto della forma: il contorno perfetto con lo strumento, il colore e lo spessore di [source], la pressione media
   * del tratto a mano su tutto il contorno, e il tempo che scorre come se la forma fosse disegnata alla stessa velocita'.
   */
  fun strokeFor(source: FluidInkStroke, shape: InkRecognizedShape): FluidInkStroke =
    source.copy(points = outlinePoints(shape.outline, source.points), input = FluidInkInput.Synthetic, shape = shape.kind)

  fun outlinePoints(outline: FloatArray, source: FluidInkPoints): FluidInkPoints {
    val count = outline.size / 2
    val pressure = source.averagePressure()
    // Il tempo cresce sempre (androidx.ink lo vuole), anche per una forma scritta dall'app senza tempi.
    val duration = max(if (source.size > 0) source.time(source.size - 1) else 0f, count * 4f)
    val builder = FluidInkPointsBuilder(count)
    for (i in 0 until count) {
      val t = if (count > 1) duration * i / (count - 1) else 0f
      builder.add(outline[i * 2], outline[i * 2 + 1], t, pressure)
    }
    return builder.build()
  }
}
