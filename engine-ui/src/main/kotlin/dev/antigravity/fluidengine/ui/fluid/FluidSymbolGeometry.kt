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

// --- F8.5b · La Magic Replace: cosa resta, cosa si disegna, cosa compare ------------------------------------------
//
// La Magic Replace di SF Symbols 6 (iOS 18) passa fra due simboli «parenti» cambiando solo i pezzi diversi: la barra
// della campana barrata si disegna con la penna e taglia la campana mentre passa, il pallino compare con un rimbalzo, il
// resto sta fermo. Apple lo sa dalle annotazioni dei suoi simboli; qui lo si ricava dai tracciati, una volta per coppia:
// - si confrontano le due icone su una griglia (le celle dentro l'una, dentro l'altra, dentro tutte e due);
// - se la parte comune e' almeno [SymbolMagicPlanner.MinCommon] di ciascuna, le icone sono parenti: altrimenti resta la
//   sostituzione sfocata (play e pausa non hanno niente in comune);
// - le celle nuove e quelle che spariscono si dividono in **pezzi**; i pezzi lunghi e stretti (la barra, le onde della
//   campana che suona) fanno una **banda**, che una penna percorre da un capo all'altro (o da cui si ritira), e ogni pezzo
//   che sta lungo la banda (il taglio nella campana) segue la penna per intero; i pezzi raccolti (il pallino) compaiono
//   o spariscono da soli; i bordi sottilissimi che cambiano fra due disegni simili si incrociano piano;
// - un pallino e' la sua isola intera, quando nell'icona sta da se' (come in Apple): in `notifications_unread` il
//   pallino copre un tratto della campana, che e' comune alle due icone ma va via con l'intaglio. I pezzi dell'altra
//   icona che gli stanno intorno sono l'intaglio che gli fa posto: si incrociano coi tempi del pallino, non fanno una
//   banda (F8.5c).
// Pura (niente Android): i test la provano sulla JVM. I disegni usano le operazioni sui tracciati, esatte: ogni pezzo e'
// il tracciato vero ritagliato col suo contorno ([SymbolPiece]).

/** Il contorno di un pezzo (un poligono convesso un po' allargato, nel riquadro dell'icona). */
internal class SymbolPiece(val xs: FloatArray, val ys: FloatArray)

/** Una banda: l'asse da [startX], [startY] in direzione ([ux], [uy]) da 0 a [length], e i pezzi che la seguono. */
internal class SymbolBand(
  val startX: Float,
  val startY: Float,
  val ux: Float,
  val uy: Float,
  val length: Float,
  /** Il raggio della punta tonda della penna (mezza larghezza tipica del tratto). */
  val capRadius: Float,
  /** Vero: la penna disegna i pezzi nuovi dall'inizio alla fine; falso: si ritira dai pezzi che spariscono verso l'inizio. */
  val drawsOn: Boolean,
  /** I pezzi della seconda icona (che compaiono) e della prima (che spariscono) lungo la banda. */
  val added: List<SymbolPiece>,
  val removed: List<SymbolPiece>,
  /** Dove sta, lungo l'asse, il centro di ogni pezzo: un pezzo dell'altra icona cambia intero quando la penna ci passa. */
  val addedAt: FloatArray,
  val removedAt: FloatArray,
)

/** Un pallino: il centro, il suo contorno (l'isola intera, se sta da se'), e se compare ([appears]) o sparisce. */
internal class SymbolBadge(val centerX: Float, val centerY: Float, val piece: SymbolPiece, val appears: Boolean)

/**
 * Il piano di una Magic Replace fra due icone: le bande, i pallini e il rimbalzo della base ([bounce]: +1 su, -1 giu', 0
 * niente), come li ha misurati F8.5b sui fotogrammi di Apple.
 */
internal class SymbolMagicPlan(
  val bands: List<SymbolBand>,
  val badges: List<SymbolBadge>,
  val bounce: Int,
) {
  /**
   * Quando si incrociano i pezzi che non stanno in una banda o in un pallino, da [restStartMs] a [restMs]: lungo la
   * penna o la ritirata; l'intaglio di un pallino che arriva nel primo fotogramma, quello di un pallino che se ne va
   * nell'ultimo, quando il pallino e' svanito.
   */
  val restMs: Float
    get() = when {
      bands.any { it.drawsOn } -> FluidSymbolMagic.PenMs
      bands.isNotEmpty() -> FluidSymbolMagic.RetractMs
      badges.any { it.appears } -> FluidSymbolMagic.CutMs
      else -> FluidSymbolMagic.BadgeOutMs
    }
  val restStartMs: Float
    get() = if (bands.isEmpty() && badges.isNotEmpty() && badges.none { it.appears }) {
      FluidSymbolMagic.BadgeOutMs - FluidSymbolMagic.CutMs
    } else {
      0f
    }
}

internal object SymbolMagicPlanner {
  /** La griglia del confronto: 96 celle per lato, un quarto di unita' su un'icona da 24. */
  const val Grid = 96

  /** Le icone sono parenti se la parte comune e' almeno questa frazione di ciascuna (play e pausa: 0,58). */
  const val MinCommon = 0.75f

  /** Un cambio piu' piccolo di questa frazione della griglia non e' un cambio (due disegni identici). */
  const val MinChange = 0.004f

  /** Un pezzo con meno celle e' un bordo che cambia, non un pezzo. */
  const val MinCells = 6

  /** Allungamento (rapporto degli assi) da cui un pezzo e' una banda; per l'insieme dei pezzi, una barra sola. */
  const val BandElongation = 3f
  const val UnionElongation = 6f

  /** Quanto lontano dall'asse (in unita' oltre la mezza larghezza) un pezzo segue ancora la banda. */
  const val BandReach = 1.5f

  /** Fin dove (in unita' oltre il raggio del pallino) un pezzo dell'altra icona e' l'intaglio che gli fa posto. */
  const val BadgeRoom = 1.5f

  /** L'isola di un pallino e' al massimo questo multiplo del pezzo: se e' di piu', il pallino tocca la base. */
  const val IslandGrowth = 2f

  private val cache = java.util.WeakHashMap<SymbolGeometry, java.util.WeakHashMap<SymbolGeometry, Any>>()
  private val none = Any()

  /** Il piano fra [from] e [to], o nulla se non sono parenti (resta la sostituzione sfocata). Una volta per coppia. */
  fun plan(from: SymbolGeometry, to: SymbolGeometry): SymbolMagicPlan? = synchronized(cache) {
    val row = cache.getOrPut(from) { java.util.WeakHashMap() }
    val found = row.getOrPut(to) { build(from, to) ?: none }
    found as? SymbolMagicPlan
  }

  fun build(from: SymbolGeometry, to: SymbolGeometry): SymbolMagicPlan? {
    if (from.viewportWidth != to.viewportWidth || from.viewportHeight != to.viewportHeight) return null
    val n = Grid
    val a = symbolMask(from, n)
    val b = symbolMask(to, n)
    var countA = 0
    var countB = 0
    var common = 0
    var changed = 0
    for (i in 0 until n * n) {
      if (a[i]) countA++
      if (b[i]) countB++
      if (a[i] && b[i]) common++
      if (a[i] != b[i]) changed++
    }
    if (countA == 0 || countB == 0) return null
    if (common < MinCommon * minOf(countA, countB)) return null
    if (changed < MinChange * n * n) return null
    val cell = from.viewportWidth / n
    val addedParts = components(BooleanArray(n * n) { b[it] && !a[it] }, n).map { Part(it, appears = true) }
    val removedParts = components(BooleanArray(n * n) { a[it] && !b[it] }, n).map { Part(it, appears = false) }
    val bands = mutableListOf<MutableBand>()
    val badges = mutableListOf<SymbolBadge>()
    val rooms = mutableListOf<Room>()
    // Prima l'insieme piu' grande: la barra che compare taglia la campana, non il contrario.
    val first = if (addedParts.sumOf { it.cells.size } >= removedParts.sumOf { it.cells.size }) addedParts else removedParts
    val second = if (first === addedParts) removedParts else addedParts
    for (parts in listOf(first, second)) {
      // L'intaglio attorno a un pallino dell'altro insieme e' suo: si incrocia, coi tempi del pallino.
      for (part in parts) {
        if (part.owner != null) continue
        rooms.firstOrNull { it.appears != part.appears && it.holds(part, cell) }?.let { part.owner = it }
      }
      val free = parts.filter { part -> part.owner == null && bands.none { it.reaches(part, cell) } }
      if (free.isEmpty()) continue
      val union = free.flatMap { it.cells }
      if (free.size > 1 && axes(union)[4] >= UnionElongation) {
        val band = MutableBand.of(union, free.first().appears)
        free.forEach { band.take(it) }
        bands += band
      } else {
        for (part in free) {
          if (bands.any { it.reaches(part, cell) }) continue
          if (axes(part.cells)[4] >= BandElongation) {
            val band = MutableBand.of(part.cells, part.appears)
            band.take(part)
            bands += band
          } else {
            part.owner = Unit
            val cells = island(if (part.appears) b else a, n, part) ?: part.cells
            val ax = axes(cells)
            var radius = 0f
            for (c in cells) radius = maxOf(radius, hypot(c[0] - ax[0], c[1] - ax[1]))
            rooms += Room(ax[0], ax[1], radius + 0.5f, part.appears)
            badges += SymbolBadge(ax[0] * cell, ax[1] * cell, hull(cells, cell), part.appears)
          }
        }
      }
    }
    // Ogni pezzo che sta lungo una banda la segue per intero: il taglio nella campana si apre quando la penna ci passa.
    for (part in addedParts + removedParts) {
      if (part.owner != null) continue
      bands.firstOrNull { it.reaches(part, cell) }?.take(part)
    }
    val on = bands.any { it.drawsOn }
    val off = bands.any { !it.drawsOn }
    val badgeOn = badges.any { it.appears }
    val badgeOff = badges.any { !it.appears }
    // Il rimbalzo della base, dai quattro casi di Apple: la barra che si disegna no; il pallino che arriva e la barra che
    // si ritira su; il pallino che se ne va giu'.
    val bounce = when {
      badgeOff && !badgeOn && !on && !off -> -1
      on && !off && !badgeOn && !badgeOff -> 0
      else -> 1
    }
    return SymbolMagicPlan(bands.map { it.freeze(cell) }, badges, bounce)
  }

  /** Un pezzo di celle (x, y in celle), nuove ([appears]) o perse; [owner] non nullo quando qualcuno lo ha preso. */
  private class Part(val cells: List<FloatArray>, val appears: Boolean) {
    var owner: Any? = null
  }

  /** Lo spazio di un pallino, in celle: il centro e il raggio della sua isola. */
  private class Room(val cx: Float, val cy: Float, val radius: Float, val appears: Boolean) {
    /** Il pezzo e' l'intaglio che fa posto al pallino: il suo centro sta entro il raggio, piu' [BadgeRoom]. */
    fun holds(part: Part, cellUnits: Float): Boolean {
      var mx = 0f
      var my = 0f
      for (c in part.cells) { mx += c[0]; my += c[1] }
      mx /= part.cells.size; my /= part.cells.size
      return hypot(mx - cx, my - cy) <= radius + BadgeRoom / cellUnits
    }
  }

  /**
   * L'isola di [mask] che contiene il pezzo: le celle collegate, tutto il pallino anche dove copre un tratto
   * dell'altra icona. Nulla se l'isola e' molto piu' grande del pezzo ([IslandGrowth]): il pallino tocca la base, e
   * conta il pezzo.
   */
  private fun island(mask: BooleanArray, n: Int, part: Part): List<FloatArray>? {
    val limit = (part.cells.size * IslandGrowth).toInt()
    val seed = part.cells[0][1].toInt() * n + part.cells[0][0].toInt()
    if (!mask[seed]) return null
    val seen = HashSet<Int>()
    val stack = ArrayDeque<Int>()
    val out = mutableListOf<FloatArray>()
    seen += seed
    stack.addLast(seed)
    while (stack.isNotEmpty()) {
      val i = stack.removeLast()
      val x = i % n
      val y = i / n
      out += floatArrayOf(x + 0.5f, y + 0.5f)
      if (out.size > limit) return null
      if (x > 0 && mask[i - 1] && seen.add(i - 1)) stack.addLast(i - 1)
      if (x < n - 1 && mask[i + 1] && seen.add(i + 1)) stack.addLast(i + 1)
      if (y > 0 && mask[i - n] && seen.add(i - n)) stack.addLast(i - n)
      if (y < n - 1 && mask[i + n] && seen.add(i + n)) stack.addLast(i + n)
    }
    return out
  }

  /** I pezzi: le celle collegate di [mask], ognuna col suo centro in celle. */
  private fun components(mask: BooleanArray, n: Int): List<List<FloatArray>> {
    val seen = BooleanArray(n * n)
    val out = mutableListOf<List<FloatArray>>()
    val stack = IntArray(n * n)
    for (start in 0 until n * n) {
      if (!mask[start] || seen[start]) continue
      var top = 0
      stack[top++] = start
      seen[start] = true
      val cells = mutableListOf<FloatArray>()
      while (top > 0) {
        val i = stack[--top]
        val x = i % n
        val y = i / n
        cells += floatArrayOf(x + 0.5f, y + 0.5f)
        if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[top++] = i - 1 }
        if (x < n - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[top++] = i + 1 }
        if (y > 0 && mask[i - n] && !seen[i - n]) { seen[i - n] = true; stack[top++] = i - n }
        if (y < n - 1 && mask[i + n] && !seen[i + n]) { seen[i + n] = true; stack[top++] = i + n }
      }
      if (cells.size >= MinCells) out += cells
    }
    return out
  }

  /** Centro, asse maggiore e allungamento (radice del rapporto degli autovalori) di un insieme di celle. */
  internal fun axes(cells: List<FloatArray>): FloatArray {
    var cx = 0.0
    var cy = 0.0
    for (c in cells) { cx += c[0]; cy += c[1] }
    cx /= cells.size; cy /= cells.size
    var sxx = 0.0
    var syy = 0.0
    var sxy = 0.0
    for (c in cells) {
      val dx = c[0] - cx
      val dy = c[1] - cy
      sxx += dx * dx; syy += dy * dy; sxy += dx * dy
    }
    sxx /= cells.size; syy /= cells.size; sxy /= cells.size
    val tr = sxx + syy
    val det = sxx * syy - sxy * sxy
    val disc = sqrt(maxOf(0.0, tr * tr / 4 - det))
    val l1 = tr / 2 + disc
    val l2 = maxOf(tr / 2 - disc, 1e-6)
    var ux = sxy
    var uy = l1 - sxx
    if (abs(ux) < 1e-9 && abs(uy) < 1e-9) { ux = 1.0; uy = 0.0 }
    val norm = sqrt(ux * ux + uy * uy)
    return floatArrayOf(cx.toFloat(), cy.toFloat(), (ux / norm).toFloat(), (uy / norm).toFloat(), sqrt(l1 / l2).toFloat())
  }

  /** Il contorno di un pezzo: l'inviluppo convesso delle celle, allargato di una cella, in unita' del riquadro. */
  private fun hull(cells: List<FloatArray>, cell: Float): SymbolPiece {
    // Gli angoli delle celle, non i centri: il tracciato vero arriva al bordo della cella.
    val pts = ArrayList<FloatArray>(cells.size * 4)
    for (c in cells) {
      pts += floatArrayOf(c[0] - 0.5f, c[1] - 0.5f); pts += floatArrayOf(c[0] + 0.5f, c[1] - 0.5f)
      pts += floatArrayOf(c[0] + 0.5f, c[1] + 0.5f); pts += floatArrayOf(c[0] - 0.5f, c[1] + 0.5f)
    }
    pts.sortWith(compareBy<FloatArray>({ it[0] }, { it[1] }))
    fun cross(o: FloatArray, p: FloatArray, q: FloatArray) = (p[0] - o[0]) * (q[1] - o[1]) - (p[1] - o[1]) * (q[0] - o[0])
    val lower = ArrayList<FloatArray>()
    for (p in pts) {
      while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) lower.removeAt(lower.size - 1)
      lower += p
    }
    val upper = ArrayList<FloatArray>()
    for (p in pts.asReversed()) {
      while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) upper.removeAt(upper.size - 1)
      upper += p
    }
    val ring = lower.dropLast(1) + upper.dropLast(1)
    var cx = 0f
    var cy = 0f
    for (p in ring) { cx += p[0]; cy += p[1] }
    cx /= ring.size; cy /= ring.size
    val xs = FloatArray(ring.size)
    val ys = FloatArray(ring.size)
    for ((k, p) in ring.withIndex()) {
      val d = hypot(p[0] - cx, p[1] - cy)
      val grow = if (d > 0f) (d + 1f) / d else 1f
      xs[k] = (cx + (p[0] - cx) * grow) * cell
      ys[k] = (cy + (p[1] - cy) * grow) * cell
    }
    return SymbolPiece(xs, ys)
  }

  /** Una banda in costruzione, in celle. */
  private class MutableBand(
    val cx: Float,
    val cy: Float,
    val ux: Float,
    val uy: Float,
    var s0: Float,
    var s1: Float,
    var half: Float,
    val cap: Float,
    val drawsOn: Boolean,
  ) {
    val added = mutableListOf<Part>()
    val removed = mutableListOf<Part>()

    fun project(x: Float, y: Float): Float = (x - cx) * ux + (y - cy) * uy
    fun across(x: Float, y: Float): Float = abs(-(x - cx) * uy + (y - cy) * ux)

    /** Il pezzo sta lungo la banda: il suo centro e' vicino all'asse e dentro la lunghezza. */
    fun reaches(part: Part, cellUnits: Float): Boolean {
      var mx = 0f
      var my = 0f
      for (c in part.cells) { mx += c[0]; my += c[1] }
      mx /= part.cells.size; my /= part.cells.size
      val margin = BandReach / cellUnits
      val s = project(mx, my)
      return across(mx, my) <= half + margin && s >= s0 - margin && s <= s1 + margin
    }

    fun take(part: Part) {
      part.owner = this
      if (part.appears) added += part else removed += part
      for (c in part.cells) {
        val s = project(c[0], c[1])
        s0 = minOf(s0, s - 0.5f)
        s1 = maxOf(s1, s + 0.5f)
      }
    }

    fun freeze(cell: Float): SymbolBand {
      val sx = cx + ux * s0
      val sy = cy + uy * s0
      fun at(parts: List<Part>) = FloatArray(parts.size) { k ->
        var mx = 0f
        var my = 0f
        for (c in parts[k].cells) { mx += c[0]; my += c[1] }
        (project(mx / parts[k].cells.size, my / parts[k].cells.size) - s0) * cell
      }
      return SymbolBand(sx * cell, sy * cell, ux, uy, (s1 - s0) * cell, cap * cell, drawsOn,
        added.map { hull(it.cells, cell) }, removed.map { hull(it.cells, cell) }, at(added), at(removed))
    }

    companion object {
      fun of(cells: List<FloatArray>, drawsOn: Boolean): MutableBand {
        val ax = axes(cells)
        var ux = ax[2]
        var uy = ax[3]
        var s0 = Float.MAX_VALUE
        var s1 = -Float.MAX_VALUE
        var half = 0f
        val across = FloatArray(cells.size)
        for ((k, c) in cells.withIndex()) {
          val s = (c[0] - ax[0]) * ux + (c[1] - ax[1]) * uy
          val d = abs(-(c[0] - ax[0]) * uy + (c[1] - ax[1]) * ux)
          s0 = minOf(s0, s - 0.5f); s1 = maxOf(s1, s + 0.5f); half = maxOf(half, d + 0.5f)
          across[k] = d
        }
        // La penna parte dal capo che si legge per primo: a sinistra, poi in alto (la barra della campana di Apple, da ore
        // 10); una banda piu' verticale che orizzontale parte dall'alto, cosi' le due onde della campana che suona
        // scendono insieme.
        val x0 = ax[0] + ux * s0
        val y0 = ax[1] + uy * s0
        val x1 = ax[0] + ux * s1
        val y1 = ax[1] + uy * s1
        val upright = abs(uy) > abs(ux) * 1.2f
        val k0 = if (upright) y0 * 4f + x0 else x0 * 4f + y0
        val k1 = if (upright) y1 * 4f + x1 else x1 * 4f + y1
        if (k1 < k0) {
          ux = -ux; uy = -uy
          val t = s0; s0 = -s1; s1 = -t
        }
        across.sort()
        // In un tratto pieno la distanza dall'asse e' uniforme fra 0 e la mezza larghezza: la mezza larghezza e' il doppio
        // della mediana.
        val cap = minOf(half, 2f * across[across.size / 2] + 0.5f)
        return MutableBand(ax[0], ax[1], ux, uy, s0, s1, half, cap, drawsOn)
      }
    }
  }
}

/** La maschera di un'icona intera (tutti gli strati, regola non-zero) su [n] × [n] celle del suo riquadro. */
internal fun symbolMask(geometry: SymbolGeometry, n: Int): BooleanArray {
  val mask = BooleanArray(n * n)
  val cw = geometry.viewportWidth / n
  val ch = geometry.viewportHeight / n
  for (layer in geometry.layers) {
    val minX = layer.outer.minX
    val maxX = layer.outer.maxX
    val minY = layer.outer.minY
    val maxY = layer.outer.maxY
    val gx0 = ((minX / cw).toInt() - 1).coerceAtLeast(0)
    val gx1 = ((maxX / cw).toInt() + 1).coerceAtMost(n - 1)
    val gy0 = ((minY / ch).toInt() - 1).coerceAtLeast(0)
    val gy1 = ((maxY / ch).toInt() + 1).coerceAtMost(n - 1)
    for (gy in gy0..gy1) for (gx in gx0..gx1) {
      val i = gy * n + gx
      if (mask[i]) continue
      val x = (gx + 0.5f) * cw
      val y = (gy + 0.5f) * ch
      var w = 0
      for (c in layer.contours) w += winding(c, x, y)
      if (w != 0) mask[i] = true
    }
  }
  return mask
}

/** Il numero di avvolgimento del contorno [c] attorno al punto ([x], [y]). */
private fun winding(c: SymbolContour, x: Float, y: Float): Int {
  var w = 0
  val n = c.size
  for (i in 0 until n) {
    val x0 = c.xs[i]
    val y0 = c.ys[i]
    val x1 = c.xs[(i + 1) % n]
    val y1 = c.ys[(i + 1) % n]
    val side = (x1 - x0) * (y - y0) - (x - x0) * (y1 - y0)
    if (y0 <= y) {
      if (y1 > y && side > 0f) w++
    } else if (y1 <= y && side < 0f) {
      w--
    }
  }
  return w
}
