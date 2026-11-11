package dev.antigravity.fluidengine.ui.glass.field

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Ciò che il campo dice di un punto: il risultato di [GlassFieldGeometry.slab] e di [GlassFieldGeometry.field].
 *
 * È un contenitore che chi chiama riusa, non un valore restituito: gli specchi servono anche a chi calcola
 * geometria a ogni fotogramma (il banco del movimento, il piano del campo), e lì un oggetto per punto è
 * un'allocazione per punto.
 */
internal class GlassFieldSample {
  /** La distanza con segno dal contorno, in pixel: negativa dentro. */
  var distance: Float = 0f

  /** La normale che esce dalla sagoma. Unitaria su un pezzo solo, più corta nella zona di fusione. */
  var normalX: Float = 0f
  var normalY: Float = 0f

  /** Il centro del pezzo a cui il punto appartiene, mescolato nella zona di fusione. */
  var centerX: Float = 0f
  var centerY: Float = 0f
}

/**
 * Gli specchi in Kotlin puro del nucleo AGSL ([GlassFieldSlabBlock], [GlassFieldPointBlock],
 * [GlassFieldKernel1], [GlassFieldKernel8]): le stesse formule, riga per riga, negli stessi `Float`.
 *
 * Esistono per due motivi. Uno shader non si prova su JVM, e una formula che decide la sagoma di ogni vetro
 * dell'engine non può restare senza test: i test esercitano questi, e lo shader è la copia carbone. E chi
 * deve conoscere la sagoma fuori dallo shader (il banco del movimento, il piano che divide un grappolo, i
 * test di parità fra riposo e viaggio) ha bisogno della stessa funzione, non di una sua idea della funzione.
 *
 * Chi tocca una formula qui tocca la riga gemella in `GlassFieldKernel.kt`, e viceversa.
 */
internal object GlassFieldGeometry {

  /** Quanti numeri occupa un pezzo in `rects` (centro x, centro y, mezza larghezza, mezza altezza) e in `radii`. */
  const val FloatsPerPiece: Int = 4

  /** La distanza di un campo senza pezzi: quella che lo shader restituisce con `pieceCount` zero. */
  const val Empty: Float = 1000000f

  /**
   * `glassRadiusAt`: il raggio del quadrante in cui cade il punto **centrato** sul pezzo (y verso il basso).
   * Sugli assi vince il quadrante di destra e quello in alto, come nello shader.
   */
  fun radiusAt(
    relX: Float,
    relY: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
  ): Float =
    if (relX >= 0f) {
      if (relY <= 0f) topRight else bottomRight
    } else {
      if (relY <= 0f) topLeft else bottomLeft
    }

  /**
   * Lo `smoothing` che il lato corto lascia spendere a un angolo di raggio [radius] (già limitato a
   * [minHalf]). È la regola di `continuousCornerSmoothing` in `fluid/FluidShape.kt`: il campo deve cedere
   * dove cede il path, o una pillola quasi capsula avrebbe due curve diverse.
   */
  fun effectiveSmoothing(radius: Float, minHalf: Float, smoothing: Float): Float =
    if (radius > 0.0001f) min(smoothing, minHalf / radius - 1f).coerceIn(0f, 1f) else 0f

  /** Il semiasse della superellisse: quanto lato consuma l'angolo raccordato. Vedi [GlassCornerReachSquare]. */
  fun cornerReach(radius: Float, effectiveSmoothing: Float): Float {
    val s = effectiveSmoothing
    return radius * (1f + s * s * (GlassCornerReachSquare + GlassCornerReachCube * s))
  }

  /** L'esponente della superellisse: 2 è il cerchio. Vedi [GlassCornerReachSquare]. */
  fun cornerPower(effectiveSmoothing: Float): Float {
    val s = effectiveSmoothing
    return 2f + s * s * (GlassCornerPowerSquare + GlassCornerPowerCube * s)
  }

  /** `glassCircleMap`: il profilo dello spessore, 0 dove il vetro è piatto e 1 sul bordo. */
  fun circleMap(x: Float): Float = 1f - sqrt(max(1f - x * x, 0f))

  /**
   * Il peso `h` dello smooth minimum fra il campo già fuso ([fused]) e un pezzo nuovo ([piece]) con raggio
   * di fusione [blend]: 1 = vince il campo già fuso, 0 = vince il pezzo. Lo stesso `h` mescola distanza,
   * normale e centro.
   */
  fun fusionWeight(fused: Float, piece: Float, blend: Float): Float {
    val k = max(blend, 0.0001f)
    return (0.5f + 0.5f * (piece - fused) / k).coerceIn(0f, 1f)
  }

  /**
   * Lo smooth minimum polinomiale: l'unione liquida di due campi. Mai sopra il minimo vero, uguale al minimo
   * quando i due distano più di [blend], e più basso di un quarto di [blend] dove coincidono (è il ponte, ed
   * è anche il motivo per cui a riposo [blend] va a zero).
   */
  fun smoothMin(fused: Float, piece: Float, blend: Float): Float {
    val k = max(blend, 0.0001f)
    val h = fusionWeight(fused, piece, blend)
    return piece + (fused - piece) * h - k * h * (1f - h)
  }

  /**
   * `glassSlab`: distanza e normale di un pezzo nel punto ([relX], [relY]) centrato su di esso.
   *
   * @param radii i quattro raggi nominali in pixel a partire da [radiiAt]: alto-sinistra, alto-destra,
   *   basso-destra, basso-sinistra.
   * @param smoothing lo `smoothing` chiesto, 0..1: la riduzione del lato corto si fa qui.
   * @param normalSpread di serie quello dello shader. Con 1 la normale è il gradiente vero della distanza:
   *   serve ai test, che lo confrontano con le differenze finite.
   * @param out riceve distanza e normale; il centro non si tocca (un pezzo non conosce la propria posizione).
   */
  fun slab(
    relX: Float,
    relY: Float,
    halfWidth: Float,
    halfHeight: Float,
    radii: FloatArray,
    radiiAt: Int,
    smoothing: Float,
    out: GlassFieldSample,
    normalSpread: Float = GlassFieldNormalSpread,
  ) {
    val minHalf = min(halfWidth, halfHeight)
    val widest = max(max(radii[radiiAt], radii[radiiAt + 1]), max(radii[radiiAt + 2], radii[radiiAt + 3]))
    if (widest > minHalf * GlassFieldWideSlack && isWide(halfWidth, halfHeight, radii, radiiAt)) {
      wideSlab(relX, relY, halfWidth, halfHeight, radii, radiiAt, out, normalSpread)
      return
    }
    val nominal = radiusAt(relX, relY, radii[radiiAt], radii[radiiAt + 1], radii[radiiAt + 2], radii[radiiAt + 3])
    val radius = max(min(nominal, minHalf), 0f)
    val s = effectiveSmoothing(radius, minHalf, smoothing)
    val reach = cornerReach(radius, s)
    val power = cornerPower(s)
    val ax = abs(relX)
    val ay = abs(relY)
    val sideX = if (relX < 0f) -1f else 1f
    val sideY = if (relY < 0f) -1f else 1f

    val qx = ax - (halfWidth - reach)
    val qy = ay - (halfHeight - reach)
    out.distance = when {
      qx <= 0f || qy <= 0f -> max(qx, qy) - reach
      s <= 0f -> hypot(qx, qy) - reach
      else -> {
        val qpx = qx.pow(power - 1f)
        val qpy = qy.pow(power - 1f)
        val sum = max(qpx * qx + qpy * qy, 1.0e-20f)
        val norm = sum.pow(1f / power)
        val slope = hypot(qpx, qpy) * norm / sum
        val m = norm - reach
        val t = (-m / reach).coerceIn(0f, 1f)
        m / (slope + (1f - slope) * t)
      }
    }

    // La normale: lo stesso pezzo con l'angolo allargato, mai oltre mezzo lato corto.
    val wide = min(reach * normalSpread, minHalf)
    val wx = ax - (halfWidth - wide)
    val wy = ay - (halfHeight - wide)
    val nx: Float
    val ny: Float
    if (wx <= 0f || wy <= 0f) {
      nx = if (wx >= wy) 1f else 0f
      ny = 1f - nx
    } else if (s <= 0f) {
      val length = hypot(wx, wy)
      nx = wx / length
      ny = wy / length
    } else {
      val wpx = wx.pow(power - 1f)
      val wpy = wy.pow(power - 1f)
      val length = hypot(wpx, wpy)
      if (length > 1.0e-20f) {
        nx = wpx / length
        ny = wpy / length
      } else {
        nx = 0.70710678f
        ny = 0.70710678f
      }
    }
    out.normalX = sideX * nx
    out.normalY = sideY * ny
  }

  /** Vero se una lastra con questi raggi prende il ramo largo di `glassSlab` (un angolo oltre mezzo lato corto). */
  fun isWide(halfWidth: Float, halfHeight: Float, radii: FloatArray, radiiAt: Int): Boolean {
    val topLeft = radii[radiiAt]
    val topRight = radii[radiiAt + 1]
    val bottomRight = radii[radiiAt + 2]
    val bottomLeft = radii[radiiAt + 3]
    val widest = max(max(topLeft, topRight), max(bottomRight, bottomLeft))
    if (widest <= min(halfWidth, halfHeight) * GlassFieldWideSlack) return false
    // I raggi devono stare nei lati, come li consegna Compose: un 999 chiesto per una capsula non è «largo».
    val roomX = halfWidth * (2f * GlassFieldWideSlack)
    val roomY = halfHeight * (2f * GlassFieldWideSlack)
    return max(topLeft + topRight, bottomLeft + bottomRight) <= roomX &&
      max(topLeft + bottomLeft, topRight + bottomRight) <= roomY
  }

  /** `glassWideSpread`: il raggio su cui il ramo largo calcola la normale. */
  private fun wideSpread(radius: Float, minHalf: Float, roomX: Float, roomY: Float, normalSpread: Float): Float =
    max(radius, min(min(radius * normalSpread, minHalf), min(roomX, roomY)))

  /** `glassWideBox`. */
  private fun wideBox(relX: Float, relY: Float, halfWidth: Float, halfHeight: Float, held: GlassFieldSample) {
    val bx = abs(relX) - halfWidth
    val by = abs(relY) - halfHeight
    val sideX = if (relX < 0f) -1f else 1f
    val sideY = if (relY < 0f) -1f else 1f
    if (bx > 0f && by > 0f) {
      val length = hypot(bx, by)
      held.distance = length
      held.normalX = sideX * (bx / length)
      held.normalY = sideY * (by / length)
    } else {
      val gx = if (bx >= by) 1f else 0f
      held.distance = max(bx, by)
      held.normalX = sideX * gx
      held.normalY = sideY * (1f - gx)
    }
  }

  /** `glassWideCorner`: l'arco di un angolo, se il punto sta nella sua zona e l'arco vince. */
  private fun wideCorner(
    held: GlassFieldSample,
    relX: Float,
    relY: Float,
    halfWidth: Float,
    halfHeight: Float,
    sideX: Float,
    sideY: Float,
    radius: Float,
  ) {
    val qx = relX * sideX - (halfWidth - radius)
    val qy = relY * sideY - (halfHeight - radius)
    if (qx <= 0f || qy <= 0f) return
    val length = hypot(qx, qy)
    if (length - radius <= held.distance) return
    held.distance = length - radius
    held.normalX = sideX * (qx / length)
    held.normalY = sideY * (qy / length)
  }

  /**
   * `glassWideSlab`: una lastra con un angolo oltre mezzo lato corto. La sagoma è l'intersezione dei quattro
   * quadranti arrotondati: il più grande fra la scatola e l'arco di ogni angolo nella sua zona. Vedi
   * [GlassFieldSlabBlock], «Il ramo largo».
   */
  private fun wideSlab(
    relX: Float,
    relY: Float,
    halfWidth: Float,
    halfHeight: Float,
    radii: FloatArray,
    radiiAt: Int,
    out: GlassFieldSample,
    normalSpread: Float,
  ) {
    val tl = max(radii[radiiAt], 0f)
    val tr = max(radii[radiiAt + 1], 0f)
    val br = max(radii[radiiAt + 2], 0f)
    val bl = max(radii[radiiAt + 3], 0f)
    // Il contenitore del chiamante fa da appoggio: il termine che vince finora, distanza e normale.
    val held = out
    wideBox(relX, relY, halfWidth, halfHeight, held)
    val boxD = held.distance
    val boxNx = held.normalX
    val boxNy = held.normalY
    wideCorner(held, relX, relY, halfWidth, halfHeight, -1f, -1f, tl)
    wideCorner(held, relX, relY, halfWidth, halfHeight, 1f, -1f, tr)
    wideCorner(held, relX, relY, halfWidth, halfHeight, 1f, 1f, br)
    wideCorner(held, relX, relY, halfWidth, halfHeight, -1f, 1f, bl)
    val distance = held.distance

    val minHalf = min(halfWidth, halfHeight)
    val wholeX = halfWidth * 2f
    val wholeY = halfHeight * 2f
    held.distance = boxD
    held.normalX = boxNx
    held.normalY = boxNy
    wideCorner(held, relX, relY, halfWidth, halfHeight, -1f, -1f, wideSpread(tl, minHalf, wholeX - tr, wholeY - bl, normalSpread))
    wideCorner(held, relX, relY, halfWidth, halfHeight, 1f, -1f, wideSpread(tr, minHalf, wholeX - tl, wholeY - br, normalSpread))
    wideCorner(held, relX, relY, halfWidth, halfHeight, 1f, 1f, wideSpread(br, minHalf, wholeX - bl, wholeY - tr, normalSpread))
    wideCorner(held, relX, relY, halfWidth, halfHeight, -1f, 1f, wideSpread(bl, minHalf, wholeX - br, wholeY - tl, normalSpread))
    out.distance = distance
  }

  /**
   * `glassField`: il campo dei primi [count] pezzi nel punto ([x], [y]) del sistema del campo.
   *
   * Gli array sono gli uniform, nella stessa forma: [rects] e [radii] con [FloatsPerPiece] numeri per pezzo,
   * [blend] e [smooth] con uno. Possono essere più lunghi di [count]: ciò che sta oltre non si legge, come
   * nello shader, che esce dal ciclo su `pieceCount`. Con un pezzo solo è [GlassFieldKernel1].
   */
  fun field(
    count: Int,
    rects: FloatArray,
    radii: FloatArray,
    blend: FloatArray,
    smooth: FloatArray,
    x: Float,
    y: Float,
    out: GlassFieldSample,
    normalSpread: Float = GlassFieldNormalSpread,
  ) {
    var d = Empty
    var normalX = 0f
    var normalY = 0f
    var centerX = 0f
    var centerY = 0f
    for (i in 0 until GlassFieldMaxPieces) {
      if (i >= count) break
      val at = i * FloatsPerPiece
      val pieceX = rects[at]
      val pieceY = rects[at + 1]
      slab(x - pieceX, y - pieceY, rects[at + 2], rects[at + 3], radii, at, smooth[i], out, normalSpread)
      if (i == 0) {
        d = out.distance
        normalX = out.normalX
        normalY = out.normalY
        centerX = pieceX
        centerY = pieceY
      } else {
        // Il raggio di fusione è del pezzo: lo unisce a tutto ciò che il ciclo ha già fuso.
        val k = max(blend[i], 0.0001f)
        val h = fusionWeight(d, out.distance, blend[i])
        d = out.distance + (d - out.distance) * h - k * h * (1f - h)
        normalX = out.normalX + (normalX - out.normalX) * h
        normalY = out.normalY + (normalY - out.normalY) * h
        centerX = pieceX + (centerX - pieceX) * h
        centerY = pieceY + (centerY - pieceY) * h
      }
    }
    out.distance = d
    out.normalX = normalX
    out.normalY = normalY
    out.centerX = centerX
    out.centerY = centerY
  }

  /** Solo la distanza di [field], per chi non vuole un contenitore. Alloca: non è per i cicli caldi. */
  fun fieldDistance(
    count: Int,
    rects: FloatArray,
    radii: FloatArray,
    blend: FloatArray,
    smooth: FloatArray,
    x: Float,
    y: Float,
  ): Float {
    val sample = GlassFieldSample()
    field(count, rects, radii, blend, smooth, x, y, sample)
    return sample.distance
  }

  /**
   * `glassFieldPoint`, una coordinata alla volta: dalla coordinata del layer al sistema del campo.
   *
   * @param flex la matrice inversa della flessione per righe, `m00, m01, m10, m11`; l'identità a riposo.
   * @return la x del punto; la y è [fieldPointY].
   */
  fun fieldPointX(
    coordX: Float,
    coordY: Float,
    offsetX: Float,
    offsetY: Float,
    flex: FloatArray,
    flexCenterX: Float,
    flexCenterY: Float,
  ): Float {
    val relX = coordX + offsetX - flexCenterX
    val relY = coordY + offsetY - flexCenterY
    return flexCenterX + (flex[0] * relX + flex[1] * relY)
  }

  /** Vedi [fieldPointX]. */
  fun fieldPointY(
    coordX: Float,
    coordY: Float,
    offsetX: Float,
    offsetY: Float,
    flex: FloatArray,
    flexCenterX: Float,
    flexCenterY: Float,
  ): Float {
    val relX = coordX + offsetX - flexCenterX
    val relY = coordY + offsetY - flexCenterY
    return flexCenterY + (flex[2] * relX + flex[3] * relY)
  }

  /** La copertura di un punto a distanza [distance]: la maschera che chi disegna ricava dal campo. */
  fun coverage(distance: Float, aa: Float): Float = (0.5f - distance / max(aa, 0.0001f)).coerceIn(0f, 1f)
}
