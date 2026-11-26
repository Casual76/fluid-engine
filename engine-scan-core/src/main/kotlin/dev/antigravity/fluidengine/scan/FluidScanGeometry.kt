/*
 * engine-scan-core (4.0, F8.17; il nucleo senza fotocamera da F8.17d): lo scanner di documenti come la scansione di Note
 * e File di iOS (VisionKit, `VNDocumentCameraViewController`). Qui la geometria del foglio: i quattro angoli,
 * l'omografia, le proporzioni vere. Codice del Fluid Engine, senza librerie: niente OpenCV.
 */
package dev.antigravity.fluidengine.scan

import androidx.compose.runtime.Immutable
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Un punto dell'immagine, normalizzato: x e y da 0 a 1, l'origine in alto a sinistra. */
@Immutable
data class FluidScanPoint(val x: Float, val y: Float) {
  operator fun plus(other: FluidScanPoint): FluidScanPoint = FluidScanPoint(x + other.x, y + other.y)
  operator fun minus(other: FluidScanPoint): FluidScanPoint = FluidScanPoint(x - other.x, y - other.y)
  operator fun times(factor: Float): FluidScanPoint = FluidScanPoint(x * factor, y * factor)

  /** Lo stesso punto con x e y dentro 0..1. */
  fun clamped(): FluidScanPoint = FluidScanPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * I quattro angoli di un foglio in un'immagine, normalizzati (0..1) e in senso orario a partire da quello in alto a
 * sinistra **del foglio**: è l'ordine in cui il foglio raddrizzato si legge. [ordered] mette in quest'ordine quattro punti
 * qualunque, come li trova il riconoscimento dei bordi.
 */
@Immutable
data class FluidScanQuad(
  val topLeft: FluidScanPoint,
  val topRight: FluidScanPoint,
  val bottomRight: FluidScanPoint,
  val bottomLeft: FluidScanPoint,
) {
  /** Gli angoli, nell'ordine del foglio. */
  val points: List<FluidScanPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

  /** L'angolo [index] (0 in alto a sinistra, poi in senso orario). */
  operator fun get(index: Int): FluidScanPoint = when (index.mod(4)) {
    0 -> topLeft
    1 -> topRight
    2 -> bottomRight
    else -> bottomLeft
  }

  /** Lo stesso foglio con l'angolo [index] spostato in [point] (il ritaglio a mano). */
  fun with(index: Int, point: FluidScanPoint): FluidScanQuad = when (index.mod(4)) {
    0 -> copy(topLeft = point)
    1 -> copy(topRight = point)
    2 -> copy(bottomRight = point)
    else -> copy(bottomLeft = point)
  }

  /** L'area in un'immagine di [aspect] (larghezza su altezza) con l'altezza 1: il formato conta, la scala no. */
  fun area(aspect: Float = 1f): Float {
    var sum = 0f
    val p = points
    for (i in 0 until 4) {
      val a = p[i]
      val b = p[(i + 1) % 4]
      sum += a.x * aspect * b.y - b.x * aspect * a.y
    }
    return abs(sum) / 2f
  }

  /** Vero se il quadrilatero è convesso e non si incrocia: il solo che un foglio vero può dare. */
  fun isConvex(aspect: Float = 1f): Boolean {
    val p = points
    var sign = 0
    for (i in 0 until 4) {
      val a = p[i]
      val b = p[(i + 1) % 4]
      val c = p[(i + 2) % 4]
      val cross = (b.x - a.x) * aspect * (c.y - b.y) - (b.y - a.y) * (c.x - b.x) * aspect
      if (abs(cross) < 1e-9f) return false
      val s = if (cross > 0) 1 else -1
      if (sign == 0) sign = s else if (s != sign) return false
    }
    return true
  }

  /** Il centro (la media dei quattro angoli). */
  val center: FluidScanPoint get() = FluidScanPoint((topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f,
    (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f)

  /**
   * La distanza più grande fra gli angoli di due fogli, in frazioni della diagonale di un'immagine di [aspect]: 0 se
   * sono lo stesso foglio. La usa l'inseguimento per dire se il telefono è fermo.
   */
  fun distanceTo(other: FluidScanQuad, aspect: Float = 1f): Float {
    val diagonal = hypot(aspect, 1f)
    var worst = 0f
    for (i in 0 until 4) {
      val a = this[i]
      val b = other[i]
      worst = max(worst, hypot((a.x - b.x) * aspect, a.y - b.y) / diagonal)
    }
    return worst
  }

  /** Il foglio fra questo e [other], a [fraction] (0 questo, 1 l'altro): l'ammorbidimento dal vivo. */
  fun lerp(other: FluidScanQuad, fraction: Float): FluidScanQuad = FluidScanQuad(
    topLeft + (other.topLeft - topLeft) * fraction,
    topRight + (other.topRight - topRight) * fraction,
    bottomRight + (other.bottomRight - bottomRight) * fraction,
    bottomLeft + (other.bottomLeft - bottomLeft) * fraction,
  )

  /**
   * Lo stesso foglio nell'immagine girata di [degrees] in senso orario (0, 90, 180, 270): un fotogramma della
   * fotocamera arriva nel verso del sensore e si legge girato di `rotationDegrees`. Gli angoli restano quelli del
   * foglio: girare l'immagine non cambia quale angolo è in alto a sinistra **sul foglio**.
   */
  fun rotated(degrees: Int): FluidScanQuad {
    val turns = (degrees / 90).mod(4)
    if (turns == 0) return this
    fun turn(p: FluidScanPoint): FluidScanPoint {
      var q = p
      repeat(turns) { q = FluidScanPoint(1f - q.y, q.x) }
      return q
    }
    return FluidScanQuad(turn(topLeft), turn(topRight), turn(bottomRight), turn(bottomLeft))
  }

  /**
   * Il foglio girato di un quarto di giro in senso orario **sul foglio** (il tasto «Ruota» della revisione): gli
   * stessi quattro punti dell'immagine, con l'angolo in alto a sinistra che diventa quello che era in basso a sinistra.
   */
  fun turnedClockwise(): FluidScanQuad = FluidScanQuad(bottomLeft, topLeft, topRight, bottomRight)

  /** Lo stesso foglio con ogni angolo dentro l'immagine. */
  fun clamped(): FluidScanQuad = FluidScanQuad(topLeft.clamped(), topRight.clamped(), bottomRight.clamped(), bottomLeft.clamped())

  /**
   * La prospettiva di questo foglio: porta un punto della pagina raddrizzata nella foto (F8.17d). `null` se gli angoli
   * non fanno un foglio (tre punti in fila).
   */
  fun pageMap(): FluidScanPageMap? = FluidScanPageMap.of(this)

  /**
   * Il foglio rientrato di [fraction] della pagina su ogni lato, **in prospettiva**: il bordo che si toglie è largo uguale
   * sulla pagina raddrizzata, anche dove la foto lo vede stretto. Lo stesso foglio se la prospettiva non si calcola.
   */
  fun inset(fraction: Float): FluidScanQuad {
    if (fraction <= 0f) return this
    val map = pageMap() ?: return this
    val e = fraction.coerceAtMost(0.25f)
    return FluidScanQuad(map.photoPoint(e, e), map.photoPoint(1f - e, e), map.photoPoint(1f - e, 1f - e), map.photoPoint(e, 1f - e))
  }

  companion object {
    /** Tutta l'immagine: il foglio di serie quando i bordi non si trovano (come VisionKit, che allora scatta tutto). */
    val Full: FluidScanQuad = FluidScanQuad(FluidScanPoint(0f, 0f), FluidScanPoint(1f, 0f), FluidScanPoint(1f, 1f), FluidScanPoint(0f, 1f))

    /**
     * Quattro punti qualunque messi nell'ordine del foglio: in alto a sinistra quello con x + y più piccolo, poi in senso
     * orario attorno al centro. Con un foglio girato di quasi 45° vince l'ordine angolare, non la somma.
     */
    fun ordered(points: List<FluidScanPoint>, aspect: Float = 1f): FluidScanQuad {
      require(points.size == 4) { "Servono quattro angoli" }
      val cx = points.sumOf { it.x.toDouble() }.toFloat() / 4f
      val cy = points.sumOf { it.y.toDouble() }.toFloat() / 4f
      // In senso orario sullo schermo (y verso il basso): angolo crescente di atan2(y, x).
      val sorted = points.sortedBy { kotlin.math.atan2((it.y - cy).toDouble(), ((it.x - cx) * aspect).toDouble()) }
      val start = sorted.indices.minBy { sorted[it].x * aspect + sorted[it].y }
      val r = List(4) { sorted[(start + it) % 4] }
      return FluidScanQuad(r[0], r[1], r[2], r[3])
    }
  }
}

/**
 * Le proporzioni vere del foglio (larghezza su altezza) viste dalla foto: con la prospettiva i lati che si vedono non
 * dicono il formato. È il metodo di Zhang e He (*Whiteboard scanning and image enhancement*, Digital Signal Processing
 * 17, 2007, § 3): dai quattro angoli si stima la focale e da lì il rapporto, col centro ottico al centro della foto. Se
 * la stima non regge (il foglio quasi di fronte, una focale impossibile) si usa la media dei lati opposti, come fa ogni
 * scanner senza la calibrazione della fotocamera.
 *
 * [width] e [height] sono i pixel della foto; il risultato è vicino a 0,707 per un A4 in verticale.
 */
fun FluidScanQuad.estimatedAspect(width: Int, height: Int): Float {
  val w = width.toDouble()
  val h = height.toDouble()
  val p = points.map { doubleArrayOf(it.x * w, it.y * h) }
  val sides = sideAspect(p)
  // Zhang & He, con i quattro angoli m1 (alto sx), m2 (alto dx), m3 (basso sx), m4 (basso dx) e u0, v0 al centro.
  val u0 = w / 2
  val v0 = h / 2
  val m1 = doubleArrayOf(p[0][0] - u0, p[0][1] - v0, 1.0)
  val m2 = doubleArrayOf(p[1][0] - u0, p[1][1] - v0, 1.0)
  val m3 = doubleArrayOf(p[3][0] - u0, p[3][1] - v0, 1.0)
  val m4 = doubleArrayOf(p[2][0] - u0, p[2][1] - v0, 1.0)
  val k2 = dot(cross(m1, m4), m3) / dot(cross(m2, m4), m3)
  val k3 = dot(cross(m1, m4), m2) / dot(cross(m3, m4), m2)
  val n2 = DoubleArray(3) { k2 * m2[it] - m1[it] }
  val n3 = DoubleArray(3) { k3 * m3[it] - m1[it] }
  val denominator = n2[2] * n3[2]
  if (abs(denominator) < 1e-9 || !k2.isFinite() || !k3.isFinite()) return sides
  val f2 = -(n2[0] * n3[0] + n2[1] * n3[1]) / denominator
  // La focale plausibile di un telefono: fra mezza e quattro volte il lato lungo della foto.
  val longSide = max(w, h)
  if (!(f2 > 0) || sqrt(f2) < longSide * 0.5 || sqrt(f2) > longSide * 4.0) return sides
  val a = n2[0] * n2[0] + n2[1] * n2[1] + n2[2] * n2[2] * f2
  val b = n3[0] * n3[0] + n3[1] * n3[1] + n3[2] * n3[2] * f2
  if (!(a > 0) || !(b > 0)) return sides
  val ratio = sqrt(a / b).toFloat()
  // Una stima che si scosta troppo dai lati visti (oltre il 40%) è rumore della focale, non prospettiva.
  return if (ratio.isFinite() && ratio > 0f && abs(ratio - sides) / sides < 0.4f) ratio else sides
}

private fun sideAspect(p: List<DoubleArray>): Float {
  fun len(a: DoubleArray, b: DoubleArray) = hypot(a[0] - b[0], a[1] - b[1])
  val top = len(p[0], p[1])
  val bottom = len(p[3], p[2])
  val left = len(p[0], p[3])
  val right = len(p[1], p[2])
  val width = (top + bottom) / 2
  val height = (left + right) / 2
  return if (height > 0) (width / height).toFloat() else 1f
}

private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
  a[1] * b[2] - a[2] * b[1],
  a[2] * b[0] - a[0] * b[2],
  a[0] * b[1] - a[1] * b[0],
)

private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

/**
 * Un'omografia del piano (3 × 3, l'ultima voce 1): porta un punto del foglio raddrizzato nella foto, o il contrario con
 * [inverse]. È la stessa trasformazione che `android.graphics.Matrix.setPolyToPoly` costruisce coi quattro angoli; qui
 * serve senza Android, per i conti del ritaglio e per le prove.
 */
internal class ScanHomography(private val m: DoubleArray) {
  fun map(x: Double, y: Double): DoubleArray {
    val d = m[6] * x + m[7] * y + m[8]
    return doubleArrayOf((m[0] * x + m[1] * y + m[2]) / d, (m[3] * x + m[4] * y + m[5]) / d)
  }

  fun inverse(): ScanHomography {
    val a = m
    val det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6])
    val inv = doubleArrayOf(
      (a[4] * a[8] - a[5] * a[7]) / det, (a[2] * a[7] - a[1] * a[8]) / det, (a[1] * a[5] - a[2] * a[4]) / det,
      (a[5] * a[6] - a[3] * a[8]) / det, (a[0] * a[8] - a[2] * a[6]) / det, (a[2] * a[3] - a[0] * a[5]) / det,
      (a[3] * a[7] - a[4] * a[6]) / det, (a[1] * a[6] - a[0] * a[7]) / det, (a[0] * a[4] - a[1] * a[3]) / det,
    )
    return ScanHomography(inv)
  }

  companion object {
    /** L'omografia che porta i quattro punti [from] nei quattro punti [to] (x, y alternati, otto numeri ciascuno). */
    fun of(from: DoubleArray, to: DoubleArray): ScanHomography? {
      // Il sistema lineare 8 × 8 di Heckbert, risolto con l'eliminazione di Gauss col pivot parziale.
      val a = Array(8) { DoubleArray(9) }
      for (i in 0 until 4) {
        val x = from[2 * i]
        val y = from[2 * i + 1]
        val u = to[2 * i]
        val v = to[2 * i + 1]
        a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
        a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
      }
      for (col in 0 until 8) {
        var pivot = col
        for (row in col + 1 until 8) if (abs(a[row][col]) > abs(a[pivot][col])) pivot = row
        if (abs(a[pivot][col]) < 1e-12) return null
        val t = a[pivot]; a[pivot] = a[col]; a[col] = t
        for (row in 0 until 8) {
          if (row == col) continue
          val factor = a[row][col] / a[col][col]
          if (factor == 0.0) continue
          for (k in col until 9) a[row][k] -= factor * a[col][k]
        }
      }
      val h = DoubleArray(9) { if (it < 8) a[it][8] / a[it][it] else 1.0 }
      return ScanHomography(h)
    }

    /** Dal rettangolo [width] × [height] (pixel del foglio raddrizzato) ai quattro angoli di [quad] nella foto. */
    fun sheetToPhoto(quad: FluidScanQuad, photoWidth: Int, photoHeight: Int, width: Int, height: Int): ScanHomography? {
      val from = doubleArrayOf(0.0, 0.0, width.toDouble(), 0.0, width.toDouble(), height.toDouble(), 0.0, height.toDouble())
      val to = DoubleArray(8)
      quad.points.forEachIndexed { i, p ->
        to[2 * i] = p.x.toDouble() * photoWidth
        to[2 * i + 1] = p.y.toDouble() * photoHeight
      }
      return of(from, to)
    }
  }
}

/**
 * La prospettiva di un foglio (F8.17d): porta un punto della pagina raddrizzata nella foto, tutti e due normalizzati
 * (0..1, l'origine in alto a sinistra). È il raddrizzamento di [FluidScanImages.straighten] al contrario: serve a
 * riportare sulla foto quello che si trova sulla pagina, come le righe che «Leggi testo» legge sulla pagina raddrizzata
 * di una foto storta. Le scale delle due immagini non contano: in coordinate normalizzate la prospettiva è la stessa.
 */
class FluidScanPageMap private constructor(private val homography: ScanHomography) {
  /** Il punto della foto sotto il punto ([u], [v]) della pagina. */
  fun photoPoint(u: Float, v: Float): FluidScanPoint {
    val p = homography.map(u.toDouble(), v.toDouble())
    return FluidScanPoint(p[0].toFloat(), p[1].toFloat())
  }

  companion object {
    private val UnitSquare = doubleArrayOf(0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)

    /** La prospettiva del foglio [quad], o `null` se gli angoli non la danno. */
    fun of(quad: FluidScanQuad): FluidScanPageMap? {
      val to = DoubleArray(8)
      quad.points.forEachIndexed { i, p ->
        to[2 * i] = p.x.toDouble()
        to[2 * i + 1] = p.y.toDouble()
      }
      return ScanHomography.of(UnitSquare, to)?.let(::FluidScanPageMap)
    }
  }
}

/** Il lato più corto e il più lungo di [quad] in pixel di una foto [width] × [height]. */
internal fun FluidScanQuad.sideRange(width: Int, height: Int): Pair<Float, Float> {
  var shortest = Float.MAX_VALUE
  var longest = 0f
  for (i in 0 until 4) {
    val a = this[i]
    val b = this[i + 1]
    val l = hypot((a.x - b.x) * width, (a.y - b.y) * height)
    shortest = min(shortest, l)
    longest = max(longest, l)
  }
  return shortest to longest
}
