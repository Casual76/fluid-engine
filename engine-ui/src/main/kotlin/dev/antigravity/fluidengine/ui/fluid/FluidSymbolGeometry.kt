package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// F8.5 · Gli strati di un simbolo, per gli effetti che lavorano per strato (il colore variabile, Draw On e Draw Off).
//
// Le icone di FluidIcons sono Material Symbols «al tratto»: ogni sagoma e' il **contorno pieno** di un tratto, non il
// tratto. SF Symbols annota a mano gli strati e il verso del disegno; qui li si ricava dal tracciato, una volta per icona:
// - uno **strato** e' un contorno esterno con i suoi buchi (la regola non-zero di Android: chi sta dentro a un numero
//   dispari di contorni e' un buco del contorno che lo contiene);
// - uno strato e' un **tratto** quando e' un contorno solo, sottile, che si puo' dividere in due fianchi paralleli da
//   un capo all'altro (la spunta, un arco del Wi-Fi, un'onda dell'altoparlante): Draw On lo percorre da un capo
//   all'altro, come la penna di SF Symbols 7, ed e' fedele;
// - uno strato con dei buchi (un anello, il profilo della campanella) si disegna **girando attorno al suo centro**, da
//   ore 12 in senso orario, come il cerchio di Variable Draw in WWDC25 337;
// - il resto (un punto, una sagoma piena, un tratto che si ramifica come la croce del «+») **compare** quando tocca a
//   lui, piccolo e trasparente verso la sua taglia: Apple fa lo stesso con i simboli senza annotazioni di disegno.
// Pura (niente Android): i test la provano sulla JVM.

/** Che tipo di disegno fa uno strato in Draw On e Draw Off. */
internal enum class SymbolDrawKind { Stroke, Sweep, Appear }

/**
 * Un contorno appiattito in una spezzata: [xs] e [ys] nel riquadro dell'icona (24 × 24 per FluidIcons), chiusa (l'ultimo
 * punto torna sul primo senza ripeterlo).
 */
internal class SymbolContour(val xs: FloatArray, val ys: FloatArray, val nodes: List<PathNode>) {
  val size: Int get() = xs.size

  /** Area con segno (formula del laccio): il segno dice il verso. */
  val signedArea: Float by lazy {
    var a = 0.0
    for (i in xs.indices) {
      val j = (i + 1) % xs.size
      a += xs[i].toDouble() * ys[j] - xs[j].toDouble() * ys[i]
    }
    (a / 2.0).toFloat()
  }

  val perimeter: Float by lazy {
    var p = 0f
    for (i in xs.indices) {
      val j = (i + 1) % xs.size
      p += hypot(xs[j] - xs[i], ys[j] - ys[i])
    }
    p
  }

  val minX: Float get() = xs.min()
  val maxX: Float get() = xs.max()
  val minY: Float get() = ys.min()
  val maxY: Float get() = ys.max()

  /** Il punto ([x], [y]) e' dentro la spezzata (pari e dispari sui lati, va bene per un contorno semplice). */
  fun contains(x: Float, y: Float): Boolean {
    var inside = false
    var j = xs.size - 1
    for (i in xs.indices) {
      if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
      j = i
    }
    return inside
  }
}

/**
 * Un tratto diviso nei suoi due fianchi, da un capo ([sideA] e [sideB] partono dallo stesso punto) all'altro (finiscono
 * nello stesso punto). Ogni fianco e' ricampionato per lunghezza in [Samples] + 1 punti, cosi' la frazione `p` del tratto
 * e' lo stesso indice sui due fianchi.
 */
internal class SymbolStroke(
  val ax: FloatArray,
  val ay: FloatArray,
  val bx: FloatArray,
  val by: FloatArray,
  /**
   * Il raggio massimo della punta tonda: mezza larghezza tipica del tratto (la mediana). In un angolo (la spunta) i due
   * fianchi alla stessa frazione si allontanano, e una punta grande quanto la loro distanza sarebbe un bottone.
   */
  val capLimit: Float = Float.MAX_VALUE,
) {
  val samples: Int get() = ax.size - 1

  /** La larghezza del tratto a meta' strada: la misura del cappuccio tondo della penna. */
  val width: Float get() = hypot(ax[samples / 2] - bx[samples / 2], ay[samples / 2] - by[samples / 2])

  companion object {
    const val Samples = 96
  }
}

/** Uno strato: i suoi contorni (il primo e' quello esterno, gli altri i buchi), il tipo di disegno e il tratto se c'e'. */
internal class SymbolLayer(
  val contours: List<SymbolContour>,
  val kind: SymbolDrawKind,
  val stroke: SymbolStroke?,
) {
  val nodes: List<PathNode> = contours.flatMap { it.nodes }
  val outer: SymbolContour get() = contours.first()
  val centerX: Float get() = (outer.minX + outer.maxX) / 2f
  val centerY: Float get() = (outer.minY + outer.maxY) / 2f
}

/** Gli strati di un'icona, nel suo riquadro ([viewportWidth] × [viewportHeight]). */
internal class SymbolGeometry(val layers: List<SymbolLayer>, val viewportWidth: Float, val viewportHeight: Float) {
  /** Vero se almeno uno strato si disegna davvero col tratto (non solo compare): Draw On e' fedele. */
  val drawable: Boolean get() = layers.any { it.kind != SymbolDrawKind.Appear }
}

internal object SymbolGeometryBuilder {
  /** Punti per curva nell'appiattimento: a 24 unita' di riquadro e a 48 dp lo scarto resta sotto un decimo di dp. */
  private const val CurveSteps = 10

  /** Un fianco e' simile all'altro se il piu' corto e' almeno questa parte del piu' lungo. */
  private const val SideBalance = 0.62f

  /** I due fianchi stanno a meno di questa volta lo spessore medio, in ogni punto: e' un tratto, non due rami. */
  private const val SideGap = 2.4f

  /** Spessore massimo di un tratto, in frazione del riquadro: il peso 500 da' circa 2,3 su 24 (0,1). */
  private const val MaxThickness = 0.16f

  private val cache = WeakHashMap<ImageVector, SymbolGeometry>()

  /** Gli strati di [icon], calcolati una volta sola per icona (le icone di FluidIcons sono uniche). */
  fun of(icon: ImageVector): SymbolGeometry = synchronized(cache) {
    cache.getOrPut(icon) { build(paths(icon.root), icon.viewportWidth, icon.viewportHeight) }
  }

  private fun paths(group: VectorGroup): List<List<PathNode>> = buildList {
    for (i in 0 until group.size) {
      when (val child = group[i]) {
        is VectorPath -> add(child.pathData)
        is VectorGroup -> addAll(paths(child))
      }
    }
  }

  fun build(paths: List<List<PathNode>>, viewportWidth: Float, viewportHeight: Float): SymbolGeometry {
    val contours = paths.flatMap { split(it) }.filter { it.size >= 3 && abs(it.signedArea) > 1e-3f }
    // La profondita' di ogni contorno: quanti altri lo contengono.
    val depth = IntArray(contours.size)
    val parent = IntArray(contours.size) { -1 }
    for (i in contours.indices) {
      val c = contours[i]
      var best = -1
      for (j in contours.indices) {
        if (i == j) continue
        val o = contours[j]
        if (abs(o.signedArea) <= abs(c.signedArea)) continue
        if (c.minX < o.minX || c.maxX > o.maxX || c.minY < o.minY || c.maxY > o.maxY) continue
        if (!o.contains(c.xs[0], c.ys[0])) continue
        depth[i]++
        if (best < 0 || abs(contours[j].signedArea) < abs(contours[best].signedArea)) best = j
      }
      parent[i] = best
    }
    val layers = mutableListOf<SymbolLayer>()
    val scale = max(viewportWidth, viewportHeight)
    for (i in contours.indices) {
      if (depth[i] % 2 != 0) continue
      val holes = contours.indices.filter { depth[it] % 2 == 1 && parent[it] == i }.map { contours[it] }
      val members = listOf(contours[i]) + holes
      val stroke = if (holes.isEmpty()) strokeOf(contours[i], scale) else null
      val kind = when {
        stroke != null -> SymbolDrawKind.Stroke
        holes.isNotEmpty() -> SymbolDrawKind.Sweep
        else -> SymbolDrawKind.Appear
      }
      layers += SymbolLayer(members, kind, stroke)
    }
    return SymbolGeometry(layers, viewportWidth, viewportHeight)
  }

  /** Divide un tracciato nei suoi contorni (a ogni `M`) e li appiattisce. */
  fun split(nodes: List<PathNode>): List<SymbolContour> {
    val out = mutableListOf<SymbolContour>()
    var current = mutableListOf<PathNode>()
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    var x = 0f
    var y = 0f
    var startX = 0f
    var startY = 0f
    fun flush() {
      if (xs.size >= 3) {
        // Senza il punto di chiusura ripetuto.
        if (abs(xs.last() - xs.first()) < 1e-4f && abs(ys.last() - ys.first()) < 1e-4f) {
          xs.removeAt(xs.lastIndex); ys.removeAt(ys.lastIndex)
        }
        if (xs.size >= 3) out += SymbolContour(xs.toFloatArray(), ys.toFloatArray(), current.toList())
      }
      xs.clear(); ys.clear(); current = mutableListOf()
    }
    fun line(tx: Float, ty: Float) { xs += tx; ys += ty; x = tx; y = ty }
    fun quad(cx: Float, cy: Float, tx: Float, ty: Float) {
      val x0 = x
      val y0 = y
      for (s in 1..CurveSteps) {
        val t = s.toFloat() / CurveSteps
        val u = 1f - t
        xs += u * u * x0 + 2f * u * t * cx + t * t * tx
        ys += u * u * y0 + 2f * u * t * cy + t * t * ty
      }
      x = tx; y = ty
    }
    fun cubic(c1x: Float, c1y: Float, c2x: Float, c2y: Float, tx: Float, ty: Float) {
      val x0 = x
      val y0 = y
      for (s in 1..CurveSteps) {
        val t = s.toFloat() / CurveSteps
        val u = 1f - t
        xs += u * u * u * x0 + 3f * u * u * t * c1x + 3f * u * t * t * c2x + t * t * t * tx
        ys += u * u * u * y0 + 3f * u * u * t * c1y + 3f * u * t * t * c2y + t * t * t * ty
      }
      x = tx; y = ty
    }
    for (node in nodes) {
      when (node) {
        is PathNode.MoveTo -> { flush(); current += node; startX = node.x; startY = node.y; line(node.x, node.y) }
        is PathNode.RelativeMoveTo -> {
          flush(); current += node; startX = x + node.dx; startY = y + node.dy; line(startX, startY)
        }
        is PathNode.LineTo -> { current += node; line(node.x, node.y) }
        is PathNode.RelativeLineTo -> { current += node; line(x + node.dx, y + node.dy) }
        is PathNode.HorizontalTo -> { current += node; line(node.x, y) }
        is PathNode.RelativeHorizontalTo -> { current += node; line(x + node.dx, y) }
        is PathNode.VerticalTo -> { current += node; line(x, node.y) }
        is PathNode.RelativeVerticalTo -> { current += node; line(x, y + node.dy) }
        is PathNode.QuadTo -> { current += node; quad(node.x1, node.y1, node.x2, node.y2) }
        is PathNode.RelativeQuadTo -> {
          current += node; quad(x + node.dx1, y + node.dy1, x + node.dx2, y + node.dy2)
        }
        is PathNode.CurveTo -> { current += node; cubic(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3) }
        is PathNode.RelativeCurveTo -> {
          current += node; cubic(x + node.dx1, y + node.dy1, x + node.dx2, y + node.dy2, x + node.dx3, y + node.dy3)
        }
        is PathNode.Close -> { current += node; x = startX; y = startY }
        // Archi e curve «lisce»: nelle icone di FluidIcons non ci sono; si tiene il punto d'arrivo.
        is PathNode.ArcTo -> { current += node; line(node.arcStartX, node.arcStartY) }
        is PathNode.RelativeArcTo -> { current += node; line(x + node.arcStartDx, y + node.arcStartDy) }
        is PathNode.ReflectiveCurveTo -> { current += node; line(node.x2, node.y2) }
        is PathNode.RelativeReflectiveCurveTo -> { current += node; line(x + node.dx2, y + node.dy2) }
        is PathNode.ReflectiveQuadTo -> { current += node; line(node.x, node.y) }
        is PathNode.RelativeReflectiveQuadTo -> { current += node; line(x + node.dx, y + node.dy) }
      }
    }
    flush()
    return out
  }

  /**
   * Il contorno come tratto, o nulla. I due capi sono i due punti piu' lontani del contorno; i due fianchi, da un capo
   * all'altro nei due versi, devono essere lunghi simili e restare a una distanza da tratto in ogni frazione.
   */
  fun strokeOf(contour: SymbolContour, viewport: Float): SymbolStroke? {
    val n = contour.size
    val thickness = 2f * abs(contour.signedArea) / contour.perimeter
    if (thickness > MaxThickness * viewport || thickness <= 0f) return null
    var a = 0
    var b = 0
    var best = -1f
    for (i in 0 until n) for (j in i + 1 until n) {
      val d = (contour.xs[i] - contour.xs[j]).let { it * it } + (contour.ys[i] - contour.ys[j]).let { it * it }
      if (d > best) { best = d; a = i; b = j }
    }
    // Il capo di partenza: quello che si legge per primo (a sinistra, poi in alto), come la penna di Apple che comincia
    // la spunta dal braccio corto.
    val ka = contour.xs[a] * 4f + contour.ys[a]
    val kb = contour.xs[b] * 4f + contour.ys[b]
    if (kb < ka) { val t = a; a = b; b = t }
    val sideA = walk(contour, a, b, +1)
    val sideB = walk(contour, a, b, -1)
    val la = length(sideA)
    val lb = length(sideB)
    if (min(la, lb) < SideBalance * max(la, lb)) return null
    val ra = resample(sideA, SymbolStroke.Samples)
    val rb = resample(sideB, SymbolStroke.Samples)
    val gaps = FloatArray(SymbolStroke.Samples + 1)
    for (k in 0..SymbolStroke.Samples) {
      gaps[k] = hypot(ra[0][k] - rb[0][k], ra[1][k] - rb[1][k])
      if (gaps[k] > SideGap * thickness) return null
    }
    gaps.sort()
    return SymbolStroke(ra[0], ra[1], rb[0], rb[1], capLimit = gaps[gaps.size / 2] / 2f * 1.05f)
  }

  private fun walk(c: SymbolContour, from: Int, to: Int, dir: Int): Array<FloatArray> {
    val n = c.size
    val steps = if (dir > 0) (to - from + n) % n else (from - to + n) % n
    val xs = FloatArray(steps + 1)
    val ys = FloatArray(steps + 1)
    for (s in 0..steps) {
      val i = ((from + dir * s) % n + n) % n
      xs[s] = c.xs[i]; ys[s] = c.ys[i]
    }
    return arrayOf(xs, ys)
  }

  private fun length(side: Array<FloatArray>): Float {
    var l = 0f
    for (i in 1 until side[0].size) l += hypot(side[0][i] - side[0][i - 1], side[1][i] - side[1][i - 1])
    return l
  }

  private fun resample(side: Array<FloatArray>, samples: Int): Array<FloatArray> {
    val xs = side[0]
    val ys = side[1]
    val total = length(side)
    val ox = FloatArray(samples + 1)
    val oy = FloatArray(samples + 1)
    var seg = 1
    var walked = 0f
    for (k in 0..samples) {
      val target = total * k / samples
      while (seg < xs.size - 1 && walked + hypot(xs[seg] - xs[seg - 1], ys[seg] - ys[seg - 1]) < target) {
        walked += hypot(xs[seg] - xs[seg - 1], ys[seg] - ys[seg - 1]); seg++
      }
      val segLength = hypot(xs[seg] - xs[seg - 1], ys[seg] - ys[seg - 1])
      val t = if (segLength > 0f) ((target - walked) / segLength).coerceIn(0f, 1f) else 0f
      ox[k] = xs[seg - 1] + (xs[seg] - xs[seg - 1]) * t
      oy[k] = ys[seg - 1] + (ys[seg] - ys[seg - 1]) * t
    }
    return arrayOf(ox, oy)
  }
}

/**
 * Il pezzo di tratto fra le frazioni [from] e [to] (0 = il capo di partenza, 1 = l'altro), come spezzata chiusa: il fianco A
 * avanti, poi il fianco B indietro. Scrive nei vettori dati (niente allocazioni) e restituisce quanti punti ha scritto.
 * Lo usa il disegno, a ogni fotogramma di Draw On e Draw Off.
 */
internal fun symbolStrokeWindow(stroke: SymbolStroke, from: Float, to: Float, outX: FloatArray, outY: FloatArray): Int {
  val n = stroke.samples
  val a = (from.coerceIn(0f, 1f) * n)
  val b = (to.coerceIn(0f, 1f) * n)
  if (b - a <= 1e-4f) return 0
  var count = 0
  count = strokePoint(stroke.ax, stroke.ay, a, n, outX, outY, count)
  for (i in (a.toInt() + 1)..b.toInt().coerceAtMost(n)) {
    if (i > a && i < b) { outX[count] = stroke.ax[i]; outY[count] = stroke.ay[i]; count++ }
  }
  count = strokePoint(stroke.ax, stroke.ay, b, n, outX, outY, count)
  count = strokePoint(stroke.bx, stroke.by, b, n, outX, outY, count)
  for (i in b.toInt().coerceAtMost(n) downTo (a.toInt() + 1)) {
    if (i > a && i < b) { outX[count] = stroke.bx[i]; outY[count] = stroke.by[i]; count++ }
  }
  count = strokePoint(stroke.bx, stroke.by, a, n, outX, outY, count)
  return count
}

private fun strokePoint(xs: FloatArray, ys: FloatArray, f: Float, n: Int, outX: FloatArray, outY: FloatArray, at: Int): Int {
  val i = f.toInt().coerceAtMost(n - 1)
  val t = f - i
  outX[at] = xs[i] + (xs[i + 1] - xs[i]) * t
  outY[at] = ys[i] + (ys[i + 1] - ys[i]) * t
  return at + 1
}

/** La capacita' dei vettori di [symbolStrokeWindow]: i due fianchi interi piu' i quattro capi. */
internal fun symbolStrokeWindowCapacity(): Int = 2 * (SymbolStroke.Samples + 1) + 4

/** Il centro e il raggio del cappuccio tondo della penna alla frazione [f] (in [out]: x, y, raggio). */
internal fun symbolStrokeCap(stroke: SymbolStroke, f: Float, out: FloatArray) {
  val n = stroke.samples
  val p = f.coerceIn(0f, 1f) * n
  val i = p.toInt().coerceAtMost(n - 1)
  val t = p - i
  val ax = stroke.ax[i] + (stroke.ax[i + 1] - stroke.ax[i]) * t
  val ay = stroke.ay[i] + (stroke.ay[i + 1] - stroke.ay[i]) * t
  val bx = stroke.bx[i] + (stroke.bx[i + 1] - stroke.bx[i]) * t
  val by = stroke.by[i] + (stroke.by[i + 1] - stroke.by[i]) * t
  out[0] = (ax + bx) / 2f
  out[1] = (ay + by) / 2f
  out[2] = min(sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by)) / 2f, stroke.capLimit)
}
