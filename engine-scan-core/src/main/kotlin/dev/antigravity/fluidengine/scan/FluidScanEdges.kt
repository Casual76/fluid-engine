/*
 * engine-scan-core (4.0, F8.17; il nucleo senza fotocamera da F8.17d): il riconoscimento dei bordi del foglio, scritto
 * qui (niente OpenCV, che peserebbe decine di MB). Due strade che si confrontano con lo stesso voto: le rette dei bordi
 * (Hough sui bordi di Canny) e la macchia chiara del foglio (soglia di Otsu). Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Un fotogramma in toni di grigio: [width] × [height] byte di luminanza (0..255), una riga ogni [stride] byte. Arriva
 * nel verso del sensore: [rotationDegrees] (0, 90, 180, 270, in senso orario) lo porta dritto, come
 * `ImageInfo.rotationDegrees` di CameraX. Il riconoscimento consegna gli angoli già nel verso dritto.
 */
class FluidScanFrame(
  val width: Int,
  val height: Int,
  val luma: ByteArray,
  val stride: Int = width,
  val rotationDegrees: Int = 0,
  val timestampMillis: Long = 0L,
) {
  init {
    require(width > 0 && height > 0) { "Fotogramma vuoto" }
    require(stride >= width && luma.size >= stride * (height - 1) + width) { "Fotogramma troppo corto" }
  }

  /** Larghezza su altezza **dritta**, dopo [rotationDegrees]. */
  val uprightAspect: Float get() = if (rotationDegrees % 180 == 0) width.toFloat() / height else height.toFloat() / width

  companion object {
    /** La luminanza di una bitmap (Rec. 601, come il piano Y della fotocamera), già dritta. */
    fun of(bitmap: Bitmap, maxLongSide: Int = FluidScanEdges.AnalysisLongSide): FluidScanFrame {
      val scale = min(1f, maxLongSide.toFloat() / max(bitmap.width, bitmap.height))
      val w = max(1, (bitmap.width * scale).roundToInt())
      val h = max(1, (bitmap.height * scale).roundToInt())
      val small = if (w == bitmap.width && h == bitmap.height) bitmap else Bitmap.createScaledBitmap(bitmap, w, h, true)
      val pixels = IntArray(w * h)
      small.getPixels(pixels, 0, w, 0, 0, w, h)
      if (small !== bitmap) small.recycle()
      val luma = ByteArray(w * h)
      for (i in pixels.indices) {
        val c = pixels[i]
        val y = (299 * (c shr 16 and 0xFF) + 587 * (c shr 8 and 0xFF) + 114 * (c and 0xFF)) / 1000
        luma[i] = y.toByte()
      }
      return FluidScanFrame(w, h, luma)
    }
  }
}

/** Il foglio trovato in un fotogramma: gli angoli dritti e la fiducia (0..1, il voto dei bordi). */
@Immutable
data class FluidScanDetection(
  val quad: FluidScanQuad,
  val confidence: Float,
  /** Larghezza su altezza dell'immagine dritta in cui stanno gli angoli. */
  val aspect: Float,
)

/**
 * Il riconoscimento dei bordi del foglio. Uno per fotocamera ([FluidScanEdges.create]): tiene i buffer fra un
 * fotogramma e l'altro, così l'analisi dal vivo non alloca a ogni fotogramma. Non è thread-safe: lo chiama un solo
 * filo (quello dell'analisi).
 *
 * Il fotogramma si riduce a [FluidScanEdges.AnalysisLongSide] pixel sul lato lungo; un angolo sbaglia così al più di
 * mezzo pixel su 320, lo 0,2% della foto, e la rifinitura dei lati con i minimi quadrati lo porta sotto.
 */
class FluidScanEdges private constructor() {
  private var w = 0
  private var h = 0
  private var gray = FloatArray(0)
  private var tmp = FloatArray(0)
  private var gx = FloatArray(0)
  private var gy = FloatArray(0)
  private var mag = FloatArray(0)
  private var edge = ByteArray(0)
  private var stack = IntArray(0)
  private var accumulator = FloatArray(0)
  private var labels = IntArray(0)
  private var neighbours = IntArray(0)

  /** Il foglio nel fotogramma, o `null` se non c'è un foglio credibile. */
  fun detect(frame: FluidScanFrame): FluidScanDetection? {
    load(frame)
    val found = find() ?: return null
    // Gli angoli si girano nel verso dritto e solo dopo si ordinano: l'alto a sinistra è quello del foglio dritto.
    val turns = (frame.rotationDegrees / 90).mod(4)
    val upright = found.first.map { p ->
      var q = p
      repeat(turns) { q = FluidScanPoint(1f - q.y, q.x) }
      q
    }
    return FluidScanDetection(FluidScanQuad.ordered(upright, frame.uprightAspect), found.second, frame.uprightAspect)
  }

  // --- Preparazione: riduzione, stiramento dei livelli, sfocatura, gradiente. ---

  private fun load(frame: FluidScanFrame) {
    val scale = min(1f, AnalysisLongSide.toFloat() / max(frame.width, frame.height))
    val nw = max(8, (frame.width * scale).roundToInt())
    val nh = max(8, (frame.height * scale).roundToInt())
    if (nw != w || nh != h) {
      w = nw
      h = nh
      val n = w * h
      gray = FloatArray(n); tmp = FloatArray(n); gx = FloatArray(n); gy = FloatArray(n); mag = FloatArray(n)
      edge = ByteArray(n); stack = IntArray(n); labels = IntArray(n)
      neighbours = intArrayOf(-w - 1, -w, -w + 1, -1, 1, w - 1, w, w + 1)
    }
    // Riduzione per media d'area (ogni pixel d'arrivo è la media del suo riquadro): niente seghettature sui bordi.
    val fx = frame.width.toFloat() / w
    val fy = frame.height.toFloat() / h
    for (y in 0 until h) {
      val y0 = (y * fy).toInt()
      val y1 = max(y0 + 1, ((y + 1) * fy).toInt()).coerceAtMost(frame.height)
      for (x in 0 until w) {
        val x0 = (x * fx).toInt()
        val x1 = max(x0 + 1, ((x + 1) * fx).toInt()).coerceAtMost(frame.width)
        var sum = 0
        var count = 0
        var yy = y0
        while (yy < y1) {
          var p = yy * frame.stride + x0
          var xx = x0
          while (xx < x1) {
            sum += frame.luma[p].toInt() and 0xFF
            p++; xx++; count++
          }
          yy++
        }
        gray[y * w + x] = sum.toFloat() / count
      }
    }
    stretch()
    blur()
    gradient()
  }

  /** Stira i livelli fra il 2° e il 98° percentile: con poca luce il foglio e il tavolo si separano lo stesso. */
  private fun stretch() {
    val histogram = IntArray(256)
    val n = w * h
    for (i in 0 until n) histogram[gray[i].toInt().coerceIn(0, 255)]++
    val lo = percentile(histogram, n, 0.02f)
    val hi = percentile(histogram, n, 0.98f)
    val span = max(8f, (hi - lo).toFloat())
    for (i in 0 until n) gray[i] = ((gray[i] - lo) * 255f / span).coerceIn(0f, 255f)
  }

  /** Sfocatura gaussiana separabile [1 4 6 4 1] / 16: toglie il rumore della poca luce e la grana della carta. */
  private fun blur() {
    for (y in 0 until h) {
      val row = y * w
      for (x in 0 until w) {
        val a = gray[row + (x - 2).coerceIn(0, w - 1)]
        val b = gray[row + (x - 1).coerceIn(0, w - 1)]
        val c = gray[row + x]
        val d = gray[row + (x + 1).coerceIn(0, w - 1)]
        val e = gray[row + (x + 2).coerceIn(0, w - 1)]
        tmp[row + x] = (a + 4 * b + 6 * c + 4 * d + e) / 16f
      }
    }
    for (y in 0 until h) {
      for (x in 0 until w) {
        val a = tmp[(y - 2).coerceIn(0, h - 1) * w + x]
        val b = tmp[(y - 1).coerceIn(0, h - 1) * w + x]
        val c = tmp[y * w + x]
        val d = tmp[(y + 1).coerceIn(0, h - 1) * w + x]
        val e = tmp[(y + 2).coerceIn(0, h - 1) * w + x]
        gray[y * w + x] = (a + 4 * b + 6 * c + 4 * d + e) / 16f
      }
    }
  }

  /** Sobel, il modulo, la soppressione dei non massimi e l'isteresi di Canny con soglie dai percentili del modulo. */
  private fun gradient() {
    val n = w * h
    for (y in 0 until h) {
      for (x in 0 until w) {
        val i = y * w + x
        if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
          gx[i] = 0f; gy[i] = 0f; mag[i] = 0f
          continue
        }
        val tl = gray[i - w - 1]; val t = gray[i - w]; val tr = gray[i - w + 1]
        val l = gray[i - 1]; val r = gray[i + 1]
        val bl = gray[i + w - 1]; val b = gray[i + w]; val br = gray[i + w + 1]
        val dx = (tr + 2 * r + br) - (tl + 2 * l + bl)
        val dy = (bl + 2 * b + br) - (tl + 2 * t + tr)
        gx[i] = dx; gy[i] = dy; mag[i] = sqrt(dx * dx + dy * dy)
      }
    }
    // Le soglie: alta all'85° percentile dei moduli non nulli, mai sotto un minimo che separa il rumore della carta.
    val histogram = IntArray(256)
    var maxMag = 1f
    for (i in 0 until n) maxMag = max(maxMag, mag[i])
    var counted = 0
    for (i in 0 until n) if (mag[i] > 0f) { histogram[(mag[i] * 255f / maxMag).toInt().coerceIn(0, 255)]++; counted++ }
    val high = max(MinHighGradient, percentile(histogram, max(1, counted), 0.85f) * maxMag / 255f)
    val low = high * 0.45f
    highThreshold = high
    lowThreshold = low
    java.util.Arrays.fill(edge, 0)
    var top = 0
    for (y in 1 until h - 1) {
      for (x in 1 until w - 1) {
        val i = y * w + x
        val m = mag[i]
        if (m < low) continue
        // Il vicino lungo il gradiente, in quattro settori.
        val ax = abs(gx[i]); val ay = abs(gy[i])
        val step = when {
          ay <= ax * 0.4142f -> 1
          ax <= ay * 0.4142f -> w
          (gx[i] > 0) == (gy[i] > 0) -> w + 1
          else -> w - 1
        }
        if (m < mag[i - step] || m < mag[i + step]) continue
        if (m >= high) { edge[i] = 2; stack[top++] = i } else edge[i] = 1
      }
    }
    val neighbours = neighbours
    while (top > 0) {
      val i = stack[--top]
      for (d in neighbours) {
        val j = i + d
        if (j in 0 until n && edge[j].toInt() == 1) { edge[j] = 2; stack[top++] = j }
      }
    }
    for (i in 0 until n) if (edge[i].toInt() != 2) edge[i] = 0
  }

  private var highThreshold = 0f
  private var lowThreshold = 0f

  // --- Le due strade e il voto. ---

  private class Line(val theta: Float, val rho: Float, val votes: Float) {
    val cos = cos(theta)
    val sin = sin(theta)
  }

  private fun find(): Pair<List<FluidScanPoint>, Float>? {
    val candidates = ArrayList<FloatArray>()
    houghQuads(candidates)
    blobQuad()?.let(candidates::add)
    var best: FloatArray? = null
    var bestScore = 0f
    for (c in candidates) {
      val s = score(c) ?: continue
      if (s > bestScore) { bestScore = s; best = c }
    }
    val chosen = best ?: return null
    if (bestScore < MinConfidence) return null
    val refined = refine(chosen)
    val final = if ((score(refined) ?: 0f) >= bestScore * 0.97f) refined else chosen
    val confidence = score(final) ?: bestScore
    return List(4) { FluidScanPoint(final[2 * it] / w, final[2 * it + 1] / h) } to confidence
  }

  /** Hough: ogni pixel di bordo vota le rette vicine alla direzione del suo gradiente (±6°), pesato col modulo. */
  private fun houghQuads(out: MutableList<FloatArray>) {
    val diagonal = hypot(w.toFloat(), h.toFloat())
    val rhoBins = (2 * diagonal).toInt() + 1
    val size = ThetaBins * rhoBins
    if (accumulator.size < size) accumulator = FloatArray(size) else java.util.Arrays.fill(accumulator, 0, size, 0f)
    for (y in 1 until h - 1) {
      for (x in 1 until w - 1) {
        val i = y * w + x
        if (edge[i].toInt() == 0) continue
        var angle = atan2(gy[i], gx[i])
        if (angle < 0) angle += PI.toFloat()
        val center = (angle * ThetaBins / PI.toFloat()).toInt()
        val weight = min(mag[i] / highThreshold, 3f)
        for (dt in -ThetaSpread..ThetaSpread) {
          val t = (center + dt).mod(ThetaBins)
          val rho = x * CosTable[t] + y * SinTable[t]
          // Un θ che passa da 179 a 0 cambia il segno di ρ: con θ in [0, 180) la retta resta la stessa.
          val r = (rho + diagonal).roundToInt()
          if (r in 0 until rhoBins) accumulator[t * rhoBins + r] += weight
        }
      }
    }
    // I picchi: massimi locali in una finestra di ±3 gradi e ±4 pixel, i 24 più votati.
    val peaks = ArrayList<Line>()
    var maxVotes = 0f
    for (k in 0 until size) maxVotes = max(maxVotes, accumulator[k])
    if (maxVotes <= 0f) return
    val floor = max(maxVotes * 0.12f, min(w, h) * 0.12f)
    for (t in 0 until ThetaBins) {
      for (r in 0 until rhoBins) {
        val v = accumulator[t * rhoBins + r]
        if (v < floor) continue
        var isPeak = true
        loop@ for (dt in -3..3) for (dr in -4..4) {
          if (dt == 0 && dr == 0) continue
          val tt = t + dt
          val rr: Int
          val ti: Int
          if (tt < 0) { ti = tt + ThetaBins; rr = rhoBins - 1 - (r + dr) } else if (tt >= ThetaBins) { ti = tt - ThetaBins; rr = rhoBins - 1 - (r + dr) } else { ti = tt; rr = r + dr }
          if (rr !in 0 until rhoBins) continue
          val o = accumulator[ti * rhoBins + rr]
          if (o > v || (o == v && (dt < 0 || (dt == 0 && dr < 0)))) { isPeak = false; break@loop }
        }
        if (isPeak) peaks.add(Line((t + 0.5f) * PI.toFloat() / ThetaBins, r - diagonal, v))
      }
    }
    peaks.sortByDescending { it.votes }
    val lines = peaks.take(MaxLines)
    // Orizzontali: la normale vicina a 90°; verticali: vicina a 0° o 180°.
    val horizontal = lines.filter { abs(it.theta - PI.toFloat() / 2) < PI.toFloat() / 4 }.take(MaxPerGroup)
    val vertical = lines.filter { abs(it.theta - PI.toFloat() / 2) >= PI.toFloat() / 4 }.take(MaxPerGroup)
    for (a in horizontal.indices) for (b in a + 1 until horizontal.size) {
      val h1 = horizontal[a]; val h2 = horizontal[b]
      if (!separated(h1, h2, h.toFloat())) continue
      for (c in vertical.indices) for (d in c + 1 until vertical.size) {
        val v1 = vertical[c]; val v2 = vertical[d]
        if (!separated(v1, v2, w.toFloat())) continue
        val p1 = intersect(h1, v1) ?: continue
        val p2 = intersect(h1, v2) ?: continue
        val p3 = intersect(h2, v2) ?: continue
        val p4 = intersect(h2, v1) ?: continue
        out.add(floatArrayOf(p1[0], p1[1], p2[0], p2[1], p3[0], p3[1], p4[0], p4[1]))
      }
    }
  }

  /** Due lati opposti: almeno a un decimo dell'immagine e con al più 35° fra loro (la prospettiva di un foglio vero). */
  private fun separated(a: Line, b: Line, extent: Float): Boolean {
    var dt = abs(a.theta - b.theta)
    if (dt > PI.toFloat() / 2) dt = PI.toFloat() - dt
    if (dt > MaxOppositeAngle) return false
    // La distanza fra le rette al centro dell'immagine.
    val cx = w / 2f; val cy = h / 2f
    val da = cx * a.cos + cy * a.sin - a.rho
    val db = cx * b.cos + cy * b.sin - b.rho
    val sameSide = (abs(a.theta - b.theta) <= PI.toFloat() / 2)
    val gap = if (sameSide) abs(da - db) else abs(da + db)
    return gap > extent * 0.1f
  }

  private fun intersect(a: Line, b: Line): FloatArray? {
    val det = a.cos * b.sin - a.sin * b.cos
    if (abs(det) < 1e-4f) return null
    val x = (a.rho * b.sin - b.rho * a.sin) / det
    val y = (a.cos * b.rho - b.cos * a.rho) / det
    return floatArrayOf(x, y)
  }

  /**
   * La seconda strada: il foglio come la macchia chiara più grande (soglia di Otsu sulla luminanza sfocata), e il
   * quadrilatero più grande dentro il suo inviluppo convesso. Regge dove i bordi sono deboli ma il foglio è più chiaro
   * del tavolo, il caso più comune; il voto dei bordi decide fra le due strade.
   */
  private fun blobQuad(): FloatArray? {
    val n = w * h
    val histogram = IntArray(256)
    for (i in 0 until n) histogram[gray[i].toInt().coerceIn(0, 255)]++
    val threshold = otsu(histogram, n)
    java.util.Arrays.fill(labels, 0, n, 0)
    var bestLabel = 0
    var bestArea = 0
    var label = 0
    for (start in 0 until n) {
      if (labels[start] != 0 || gray[start] <= threshold) continue
      label++
      var top = 0
      stack[top++] = start
      labels[start] = label
      var area = 0
      while (top > 0) {
        val i = stack[--top]
        area++
        val x = i % w
        val y = i / w
        if (x > 0) push(i - 1, label, threshold).let { if (it) stack[top++] = i - 1 }
        if (x < w - 1) push(i + 1, label, threshold).let { if (it) stack[top++] = i + 1 }
        if (y > 0) push(i - w, label, threshold).let { if (it) stack[top++] = i - w }
        if (y < h - 1) push(i + w, label, threshold).let { if (it) stack[top++] = i + w }
      }
      if (area > bestArea) { bestArea = area; bestLabel = label }
    }
    if (bestLabel == 0 || bestArea < n * MinAreaFraction || bestArea > n * 0.97f) return null
    // I punti di bordo della macchia: per ogni riga l'estremo sinistro e destro.
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    for (y in 0 until h) {
      var first = -1
      var last = -1
      for (x in 0 until w) if (labels[y * w + x] == bestLabel) { if (first < 0) first = x; last = x }
      if (first >= 0) { xs.add(first.toFloat()); ys.add(y.toFloat()); xs.add(last + 1f); ys.add(y.toFloat()) }
    }
    val hull = convexHull(xs, ys)
    if (hull.size < 8) return null
    return largestQuad(hull)
  }

  private fun push(j: Int, label: Int, threshold: Float): Boolean {
    if (labels[j] != 0 || gray[j] <= threshold) return false
    labels[j] = label
    return true
  }

  /** Il voto di un quadrilatero (x, y alternati, in pixel dell'analisi): `null` se non può essere un foglio. */
  private fun score(q: FloatArray): Float? {
    val margin = 0.04f
    for (i in 0 until 4) {
      val x = q[2 * i]; val y = q[2 * i + 1]
      if (x < -w * margin || x > w * (1 + margin) || y < -h * margin || y > h * (1 + margin)) return null
    }
    // Convesso, con angoli interni fra 40° e 140°.
    var sign = 0
    for (i in 0 until 4) {
      val ax = q[2 * i]; val ay = q[2 * i + 1]
      val bx = q[2 * ((i + 1) % 4)]; val by = q[2 * ((i + 1) % 4) + 1]
      val cx = q[2 * ((i + 2) % 4)]; val cy = q[2 * ((i + 2) % 4) + 1]
      val ux = bx - ax; val uy = by - ay
      val vx = cx - bx; val vy = cy - by
      val cross = ux * vy - uy * vx
      val s = if (cross > 0) 1 else if (cross < 0) -1 else return null
      if (sign == 0) sign = s else if (s != sign) return null
      val cosAngle = -(ux * vx + uy * vy) / (hypot(ux, uy) * hypot(vx, vy))
      if (abs(cosAngle) > MaxCornerCos) return null
    }
    var area = 0f
    for (i in 0 until 4) {
      val j = (i + 1) % 4
      area += q[2 * i] * q[2 * j + 1] - q[2 * j] * q[2 * i + 1]
    }
    area = abs(area) / 2f
    val fraction = area / (w * h)
    if (fraction < MinAreaFraction) return null
    var supported = 0f
    var total = 0f
    var weakest = 1f
    for (i in 0 until 4) {
      val j = (i + 1) % 4
      val (s, length) = sideSupport(q[2 * i], q[2 * i + 1], q[2 * j], q[2 * j + 1])
      supported += s * length
      total += length
      weakest = min(weakest, s)
    }
    if (total <= 0f || weakest < MinSideSupport) return null
    val coverage = supported / total
    // Il foglio più grande vince a parità di bordi: un riquadro di testo o una foto sul foglio ha bordi anche lui.
    return coverage * (0.75f + 0.25f * sqrt(min(1f, fraction / 0.6f)))
  }

  /**
   * Quanto del lato da (x0, y0) a (x1, y1) poggia su un bordo vero: la frazione dei campioni (uno ogni pixel e mezzo)
   * con un gradiente forte e perpendicolare al lato entro due pixel. Le parti fuori dall'immagine non contano.
   */
  private fun sideSupport(x0: Float, y0: Float, x1: Float, y1: Float): Pair<Float, Float> {
    val length = hypot(x1 - x0, y1 - y0)
    if (length < 4f) return 0f to length
    // Un lato steso sul bordo della foto non è un bordo del foglio: è il bordo dell'immagine (un tavolo vuoto con la
    // luce a gradiente ne darebbe uno «perfetto»). VisionKit vuole il foglio tutto dentro l'inquadratura.
    val edge = 2f
    if ((x0 < edge && x1 < edge) || (y0 < edge && y1 < edge) || (x0 > w - 1 - edge && x1 > w - 1 - edge) ||
      (y0 > h - 1 - edge && y1 > h - 1 - edge)) return 0f to length
    val nx = -(y1 - y0) / length
    val ny = (x1 - x0) / length
    val steps = (length / 1.5f).toInt().coerceAtLeast(4)
    var hits = 0
    var counted = 0
    for (s in 0..steps) {
      val t = s.toFloat() / steps
      val px = x0 + (x1 - x0) * t
      val py = y0 + (y1 - y0) * t
      if (px < 1 || py < 1 || px >= w - 1 || py >= h - 1) continue
      counted++
      var ok = false
      for (o in -2..2) {
        val sx = (px + nx * o).roundToInt()
        val sy = (py + ny * o).roundToInt()
        if (sx < 1 || sy < 1 || sx >= w - 1 || sy >= h - 1) continue
        val i = sy * w + sx
        val along = abs(gx[i] * nx + gy[i] * ny)
        if (along >= lowThreshold && along >= mag[i] * 0.7f) { ok = true; break }
      }
      if (ok) hits++
    }
    // Un lato per più di metà fuori dall'immagine non si può verificare: non vale.
    if (counted < steps / 2) return 0f to length
    return hits.toFloat() / counted to length
  }

  /**
   * Rifinisce ogni lato con i minimi quadrati sui pixel di bordo entro tre pixel dal lato, pesati col modulo, e
   * ricalcola gli angoli: toglie il grado e il pixel di Hough.
   */
  private fun refine(q: FloatArray): FloatArray {
    val lines = Array(4) { i ->
      val j = (i + 1) % 4
      fitSide(q[2 * i], q[2 * i + 1], q[2 * j], q[2 * j + 1])
    }
    val out = FloatArray(8)
    for (i in 0 until 4) {
      val a = lines[(i + 3) % 4] ?: return q
      val b = lines[i] ?: return q
      val p = intersect(a, b) ?: return q
      out[2 * i] = p[0]; out[2 * i + 1] = p[1]
    }
    return out
  }

  private fun fitSide(x0: Float, y0: Float, x1: Float, y1: Float): Line? {
    val length = hypot(x1 - x0, y1 - y0)
    if (length < 8f) return null
    val nx = -(y1 - y0) / length
    val ny = (x1 - x0) / length
    var sw = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var syy = 0.0; var sxy = 0.0
    val steps = length.toInt()
    // Si tiene lontano dagli angoli (il 10% del lato), dove l'altro lato confonde i pixel.
    for (s in (steps / 10)..(steps - steps / 10)) {
      val t = s.toFloat() / steps
      val px = x0 + (x1 - x0) * t
      val py = y0 + (y1 - y0) * t
      for (o in -3..3) {
        val ix = (px + nx * o).roundToInt()
        val iy = (py + ny * o).roundToInt()
        if (ix < 1 || iy < 1 || ix >= w - 1 || iy >= h - 1) continue
        val i = iy * w + ix
        if (edge[i].toInt() == 0) continue
        val along = abs(gx[i] * nx + gy[i] * ny)
        if (along < mag[i] * 0.7f) continue
        val weight = mag[i].toDouble()
        sw += weight; sx += weight * ix; sy += weight * iy
        sxx += weight * ix * ix; syy += weight * iy * iy; sxy += weight * ix * iy
      }
    }
    if (sw <= 0.0) return null
    val mx = sx / sw; val my = sy / sw
    val cxx = sxx / sw - mx * mx
    val cyy = syy / sw - my * my
    val cxy = sxy / sw - mx * my
    // La direzione principale (autovettore più grande della covarianza); la normale è perpendicolare.
    val angle = 0.5 * atan2(2 * cxy, cxx - cyy)
    var theta = (angle + PI / 2).toFloat()
    if (theta < 0) theta += PI.toFloat()
    if (theta >= PI.toFloat()) theta -= PI.toFloat()
    // La retta rifinita non può girare di più di 3° rispetto al lato da cui parte.
    val original = atan2(ny.toDouble(), nx.toDouble()).toFloat().let { if (it < 0) it + PI.toFloat() else it }.let { if (it >= PI.toFloat()) it - PI.toFloat() else it }
    var diff = abs(theta - original)
    if (diff > PI.toFloat() / 2) diff = PI.toFloat() - diff
    if (diff > 3f * PI.toFloat() / 180f) return null
    val rho = (mx * cos(theta) + my * sin(theta)).toFloat()
    return Line(theta, rho, 1f)
  }

  companion object {
    /** Il lato lungo dell'analisi, in pixel. 320, a occhio (25 novembre 2026): sotto i 10 ms per fotogramma su un A52s. */
    const val AnalysisLongSide: Int = 320

    /** Il voto minimo per credere a un foglio: due terzi dei bordi sostenuti. A occhio, sulle foto di prova. */
    const val MinConfidence: Float = 0.5f

    /** Il foglio più piccolo che si cerca: l'8% dell'immagine; sotto, la scritta chiede di avvicinarsi. */
    const val MinAreaFraction: Float = 0.08f

    private const val ThetaBins = 180
    private const val ThetaSpread = 6
    private const val MaxLines = 24
    private const val MaxPerGroup = 10
    private val MaxOppositeAngle = (35.0 * PI / 180.0).toFloat()
    private val MaxCornerCos = cos(40.0 * PI / 180.0).toFloat()
    private const val MinSideSupport = 0.3f
    private const val MinHighGradient = 24f
    private val CosTable = FloatArray(ThetaBins) { cos((it + 0.5) * PI / ThetaBins).toFloat() }
    private val SinTable = FloatArray(ThetaBins) { sin((it + 0.5) * PI / ThetaBins).toFloat() }

    /** Un riconoscitore nuovo, coi suoi buffer. */
    fun create(): FluidScanEdges = FluidScanEdges()

    /** Il foglio in una foto intera (lo scatto, o una foto importata), ridotta per l'analisi. */
    fun detect(bitmap: Bitmap): FluidScanDetection? = create().detect(FluidScanFrame.of(bitmap))

    private fun percentile(histogram: IntArray, total: Int, fraction: Float): Int {
      val target = (total * fraction).toInt()
      var sum = 0
      for (i in histogram.indices) {
        sum += histogram[i]
        if (sum > target) return i
      }
      return histogram.size - 1
    }

    private fun otsu(histogram: IntArray, total: Int): Float {
      var sum = 0.0
      for (i in histogram.indices) sum += i.toDouble() * histogram[i]
      var sumB = 0.0
      var weightB = 0
      var best = 0.0
      var threshold = 127
      for (i in histogram.indices) {
        weightB += histogram[i]
        if (weightB == 0) continue
        val weightF = total - weightB
        if (weightF == 0) break
        sumB += i.toDouble() * histogram[i]
        val meanB = sumB / weightB
        val meanF = (sum - sumB) / weightF
        val between = weightB.toDouble() * weightF * (meanB - meanF) * (meanB - meanF)
        if (between > best) { best = between; threshold = i }
      }
      return threshold.toFloat()
    }

    /** L'inviluppo convesso (catena monotona di Andrew), in senso orario sullo schermo. */
    private fun convexHull(xs: List<Float>, ys: List<Float>): List<FloatArray> {
      val pts = xs.indices.map { floatArrayOf(xs[it], ys[it]) }.sortedWith(compareBy({ it[0] }, { it[1] }))
      if (pts.size < 3) return pts
      fun cross(o: FloatArray, a: FloatArray, b: FloatArray) = (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
      val lower = ArrayList<FloatArray>()
      for (p in pts) {
        while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
        lower.add(p)
      }
      val upper = ArrayList<FloatArray>()
      for (p in pts.asReversed()) {
        while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
        upper.add(p)
      }
      lower.removeAt(lower.size - 1)
      upper.removeAt(upper.size - 1)
      return lower + upper
    }

    /**
     * Il quadrilatero di area massima coi vertici sull'inviluppo: si parte dai quattro punti estremi lungo le diagonali
     * e si sposta un vertice alla volta finché l'area cresce (al più qualche giro: l'inviluppo ha decine di punti).
     */
    private fun largestQuad(hull: List<FloatArray>): FloatArray {
      val n = hull.size
      fun area(i: IntArray): Float {
        var s = 0f
        for (k in 0 until 4) {
          val a = hull[i[k]]; val b = hull[i[(k + 1) % 4]]
          s += a[0] * b[1] - b[0] * a[1]
        }
        return abs(s) / 2f
      }
      val idx = intArrayOf(
        hull.indices.minBy { hull[it][0] + hull[it][1] },
        hull.indices.maxBy { hull[it][0] - hull[it][1] },
        hull.indices.maxBy { hull[it][0] + hull[it][1] },
        hull.indices.minBy { hull[it][0] - hull[it][1] },
      ).sortedArray()
      var current = area(idx)
      for (round in 0 until 8) {
        var improved = false
        for (k in 0 until 4) {
          for (cand in 0 until n) {
            if (cand in idx) continue
            val trial = idx.copyOf()
            trial[k] = cand
            trial.sort()
            val a = area(trial)
            if (a > current + 1e-3f) { current = a; idx.indices.forEach { idx[it] = trial[it] }; improved = true }
          }
        }
        if (!improved) break
      }
      return FloatArray(8).also { out -> for (k in 0 until 4) { out[2 * k] = hull[idx[k]][0]; out[2 * k + 1] = hull[idx[k]][1] } }
    }
  }
}
