package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Come la foto sta sullo schermo: la cornice del ritaglio e' centrata in ([frameX], [frameY]), un pixel dell'orientata
 * (dell'anteprima) vale [zoom] pixel di schermo, e il punto ([anchorX], [anchorY]) dell'orientata sta al centro della
 * cornice. Il raddrizzamento e' quello della geometria.
 *
 * A riposo la vista e' **adatta** ([CropMath.fit]): la cornice riempie lo spazio e il suo centro e' il centro del ritaglio.
 * Mentre il dito tira una maniglia la vista resta **ferma** (la foto non si muove, si muove la cornice), e quando il dito
 * si alza torna adatta con una molla, come in Foto.
 */
internal data class CropView(
  val frameX: Float,
  val frameY: Float,
  val zoom: Float,
  val anchorX: Float,
  val anchorY: Float,
) {
  fun lerp(to: CropView, t: Float): CropView {
    // Lo zoom si interpola in scala logaritmica: a meta' strada la foto e' a meta' ingrandimento percepito.
    val z = (zoom * Math.pow((to.zoom / zoom).toDouble(), t.toDouble()).toFloat())
    return CropView(
      frameX + (to.frameX - frameX) * t, frameY + (to.frameY - frameY) * t, z,
      anchorX + (to.anchorX - anchorX) * t, anchorY + (to.anchorY - anchorY) * t,
    )
  }
}

/** Le maniglie della cornice: i quattro angoli e i quattro lati. */
internal enum class CropHandle(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
  TopLeft(true, true, false, false), Top(false, true, false, false), TopRight(false, true, true, false),
  Right(false, false, true, false), BottomRight(false, false, true, true), Bottom(false, false, false, true),
  BottomLeft(true, false, false, true), Left(true, false, false, false);

  val corner: Boolean get() = (left || right) && (top || bottom)
}

/** Un rettangolo dritto sullo schermo. */
internal data class ScreenRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width get() = right - left
  val height get() = bottom - top
  val centerX get() = (left + right) / 2f
  val centerY get() = (top + bottom) / 2f
}

/**
 * I conti della cornice, puri (si provano sulla JVM): la vista adatta, il riquadro della cornice sullo schermo, le
 * maniglie, lo spostamento, il pizzico e la rotazione a due dita. Tutto in pixel dell'orientata dell'anteprima, larga
 * [ow] e alta [oh].
 */
internal object CropMath {

  /** La vista adatta: la cornice piu' grande che sta in [area] meno i margini [marginX] e [marginY]. */
  fun fit(area: ScreenRect, marginX: Float, marginY: Float, g: FluidCropGeometry, ow: Int, oh: Int): CropView {
    val w = g.width * ow
    val h = g.height * oh
    val z = min((area.width - 2 * marginX) / w, (area.height - 2 * marginY) / h).coerceAtLeast(1e-4f)
    return CropView(area.centerX, area.centerY, z, g.centerX * ow, g.centerY * oh)
  }

  /** Dove sta la cornice sullo schermo, con la vista [v]. */
  fun frame(v: CropView, g: FluidCropGeometry, ow: Int, oh: Int): ScreenRect {
    val rad = Math.toRadians(g.angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    val dx = g.centerX * ow - v.anchorX
    val dy = g.centerY * oh - v.anchorY
    val cx = v.frameX + v.zoom * (co * dx - si * dy)
    val cy = v.frameY + v.zoom * (si * dx + co * dy)
    val hw = v.zoom * g.width * ow / 2f
    val hh = v.zoom * g.height * oh / 2f
    return ScreenRect(cx - hw, cy - hh, cx + hw, cy + hh)
  }

  /**
   * La matrice dai pixel dell'anteprima (larga [pw], alta [ph]) allo schermo: `T(cornice) · S(zoom) · R(angolo) ·
   * T(−ancora) · (rotazioni di 90° e specchio)`. Scrive i nove valori di `android.graphics.Matrix` in [out], senza
   * allocare (si chiama a ogni fotogramma).
   */
  fun matrix(v: CropView, g: FluidCropGeometry, orient: FluidAffine, out: FloatArray) {
    val rad = Math.toRadians(g.angle.toDouble())
    val co = cos(rad).toFloat() * v.zoom
    val si = sin(rad).toFloat() * v.zoom
    // R·S applicata a (p − ancora), poi + cornice; p = orient(q).
    val a = co * orient.a - si * orient.b
    val b = si * orient.a + co * orient.b
    val c = co * orient.c - si * orient.d
    val d = si * orient.c + co * orient.d
    val ox = orient.tx - v.anchorX
    val oy = orient.ty - v.anchorY
    val tx = co * ox - si * oy + v.frameX
    val ty = si * ox + co * oy + v.frameY
    out[0] = a; out[1] = c; out[2] = tx
    out[3] = b; out[4] = d; out[5] = ty
    out[6] = 0f; out[7] = 0f; out[8] = 1f
  }

  /** La maniglia sotto il dito in ([x], [y]), entro [touch] pixel dagli angoli e dai lati; null se nessuna. */
  fun handleAt(frame: ScreenRect, x: Float, y: Float, touch: Float): CropHandle? {
    val nearL = abs(x - frame.left) <= touch
    val nearR = abs(x - frame.right) <= touch
    val nearT = abs(y - frame.top) <= touch
    val nearB = abs(y - frame.bottom) <= touch
    val inX = x >= frame.left - touch && x <= frame.right + touch
    val inY = y >= frame.top - touch && y <= frame.bottom + touch
    return when {
      nearL && nearT -> CropHandle.TopLeft
      nearR && nearT -> CropHandle.TopRight
      nearL && nearB -> CropHandle.BottomLeft
      nearR && nearB -> CropHandle.BottomRight
      nearL && inY -> CropHandle.Left
      nearR && inY -> CropHandle.Right
      nearT && inX -> CropHandle.Top
      nearB && inX -> CropHandle.Bottom
      else -> null
    }
  }

  /** Vero se il ritaglio [g] sta tutto nella foto girata (con mezzo pixel di tolleranza). */
  fun inside(g: FluidCropGeometry, ow: Int, oh: Int): Boolean {
    val o = Oriented(g, ow, oh)
    val ex = o.extentX(1f)
    val ey = o.extentY(1f)
    val eps = 0.5f
    return o.cx - ex >= -eps && o.cx + ex <= ow + eps && o.cy - ey >= -eps && o.cy + ey <= oh + eps
  }

  /**
   * La cornice tirata dalla maniglia [handle] di ([dx], [dy]) pixel di schermo, con la vista ferma [v], dalla geometria
   * di quando il dito e' sceso ([start]). Con le proporzioni bloccate ([ratio], larghezza/altezza) gli angoli scalano
   * dall'angolo opposto e i lati dal centro dell'altro asse. Non scende sotto [minScreen] pixel e non esce dalla foto:
   * se uscirebbe si ferma sul bordo, lasciando scorrere l'altro asse.
   */
  fun resized(
    start: FluidCropGeometry, v: CropView, handle: CropHandle, dx: Float, dy: Float,
    ow: Int, oh: Int, ratio: Float?, minScreen: Float,
  ): FluidCropGeometry {
    fun attempt(tx: Float, ty: Float): FluidCropGeometry = applyDrag(start, v, handle, dx * tx, dy * ty, ow, oh, ratio, minScreen)
    if (inside(attempt(1f, 1f), ow, oh)) return attempt(1f, 1f)
    return if (ratio != null) {
      val t = search { inside(attempt(it, it), ow, oh) }
      attempt(t, t)
    } else {
      val tx = search { inside(attempt(it, 0f), ow, oh) }
      val ty = search { inside(attempt(tx, it), ow, oh) }
      attempt(tx, ty)
    }
  }

  private fun search(fits: (Float) -> Boolean): Float {
    if (!fits(0f)) return 0f
    var lo = 0f
    var hi = 1f
    repeat(18) {
      val mid = (lo + hi) / 2f
      if (fits(mid)) lo = mid else hi = mid
    }
    return lo
  }

  private fun applyDrag(
    g: FluidCropGeometry, v: CropView, h: CropHandle, dx: Float, dy: Float, ow: Int, oh: Int, ratio: Float?,
    minScreen: Float,
  ): FluidCropGeometry {
    val f = frame(v, g, ow, oh)
    var l = f.left
    var t = f.top
    var r = f.right
    var b = f.bottom
    if (h.left) l = min(l + dx, r - minScreen)
    if (h.right) r = max(r + dx, l + minScreen)
    if (h.top) t = min(t + dy, b - minScreen)
    if (h.bottom) b = max(b + dy, t + minScreen)
    if (ratio != null) {
      val w0 = f.width
      val h0 = f.height
      if (h.corner) {
        val s = max((r - l) / w0, (b - t) / h0).coerceAtLeast(minScreen / min(w0, h0))
        val nw = w0 * s
        val nh = h0 * s
        if (h.left) l = r - nw else r = l + nw
        if (h.top) t = b - nh else b = t + nh
      } else if (h.left || h.right) {
        val nh = (r - l) / ratio
        t = f.centerY - nh / 2f
        b = f.centerY + nh / 2f
      } else {
        val nw = (b - t) * ratio
        l = f.centerX - nw / 2f
        r = f.centerX + nw / 2f
      }
    }
    return fromScreen(g, v, ScreenRect(l, t, r, b), ow, oh)
  }

  /** La geometria della cornice [rect] sullo schermo, con la vista [v]. */
  fun fromScreen(g: FluidCropGeometry, v: CropView, rect: ScreenRect, ow: Int, oh: Int): FluidCropGeometry {
    val rad = Math.toRadians(g.angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    val sx = (rect.centerX - v.frameX) / v.zoom
    val sy = (rect.centerY - v.frameY) / v.zoom
    // R(−angolo): dallo schermo all'orientata.
    val cx = v.anchorX + co * sx + si * sy
    val cy = v.anchorY - si * sx + co * sy
    return g.copy(
      centerX = cx / ow, centerY = cy / oh,
      width = rect.width / v.zoom / ow, height = rect.height / v.zoom / oh,
    )
  }

  /** La foto trascinata sotto la cornice ferma: il ritaglio va dall'altra parte, senza uscire. */
  fun panned(g: FluidCropGeometry, v: CropView, dx: Float, dy: Float, ow: Int, oh: Int): FluidCropGeometry {
    val rad = Math.toRadians(g.angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    val ox = -(co * dx + si * dy) / v.zoom
    val oy = -(-si * dx + co * dy) / v.zoom
    val (sw, sh) = sourceDims(g, ow, oh)
    return g.moved(ox, oy, sw, sh)
  }

  /**
   * Il pizzico di [scale] attorno a ([focusX], [focusY]) sullo schermo, con la cornice ferma: la foto si ingrandisce (il
   * ritaglio si stringe) tenendo fermo il punto sotto le dita. Non oltre la foto intera, non sotto [minPixels] pixel
   * dell'orientata sul lato corto del ritaglio.
   */
  fun pinched(
    g: FluidCropGeometry, v: CropView, scale: Float, focusX: Float, focusY: Float, ow: Int, oh: Int, minPixels: Float,
  ): FluidCropGeometry {
    val w = g.width * ow
    val h = g.height * oh
    val ratio = w / h
    val (sw, sh) = sourceDims(g, ow, oh)
    val largest = g.largestWithRatio(ratio, sw, sh)
    val maxW = largest.width * ow
    var nw = (w / scale).coerceAtMost(maxW)
    nw = nw.coerceAtLeast(min(maxW, minPixels * max(1f, ratio)))
    val s = w / nw
    val nh = h / s
    val rad = Math.toRadians(g.angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    val px = (focusX - v.frameX)
    val py = (focusY - v.frameY)
    // Il punto sotto le dita nell'orientata, prima e dopo.
    val qx = v.anchorX + (co * px + si * py) / v.zoom
    val qy = v.anchorY + (-si * px + co * py) / v.zoom
    val z2 = v.zoom * s
    val cx = qx - (co * px + si * py) / z2
    val cy = qy - (-si * px + co * py) / z2
    return g.copy(centerX = cx / ow, centerY = cy / oh, width = nw / ow, height = nh / oh).moved(0f, 0f, sw, sh)
  }

  /** Le misure della sorgente che danno l'orientata [ow] × [oh] (i metodi di [FluidCropGeometry] vogliono quelle). */
  fun sourceDims(g: FluidCropGeometry, ow: Int, oh: Int): Pair<Int, Int> = if (g.quarterTurns % 2 == 0) ow to oh else oh to ow
}
