package dev.antigravity.fluidengine.editor

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Le proporzioni del ritaglio, come la fila di «Ritaglia» in Foto di iOS 26 (Libero, Originale, Quadrato, 16:9, 4:3…,
 * con il tasto che gira fra verticale e orizzontale), più **Tondo** per la foto di un account (la scheda dell'idea): un
 * quadrato con la maschera tonda nell'editor. Il file che esce resta quadrato: il tondo lo disegna chi mostra l'avatar.
 *
 * Non e' un enum (la regola 3.11): [custom] fa le proporzioni di un'app (la forma di un widget).
 *
 * @property width e [height] il rapporto; 0 e 0 per [Freeform], −1 e −1 per [Original] (quello della foto).
 */
class FluidCropAspect private constructor(
  val id: String,
  val width: Int,
  val height: Int,
  val round: Boolean = false,
) {
  /** Vero se il rapporto e' libero: le maniglie spostano ogni lato da solo. */
  val isFreeform: Boolean get() = width == 0

  /** Il rapporto larghezza/altezza, per una foto orientata di proporzioni [imageRatio]; null se libero. */
  fun ratio(imageRatio: Float): Float? = when {
    isFreeform -> null
    width < 0 -> imageRatio
    else -> width.toFloat() / height
  }

  /** Lo stesso rapporto girato (16:9 → 9:16); i quadrati e il libero restano se stessi. */
  fun rotated(): FluidCropAspect =
    if (width <= 0 || width == height) this else FluidCropAspect("${height}:${width}", height, width, round)

  /** Vero se il rapporto e' verticale (piu' alto che largo); [Original] lo decide la foto. */
  fun isPortrait(imageRatio: Float): Boolean = (ratio(imageRatio) ?: imageRatio) < 1f

  override fun equals(other: Any?): Boolean =
    other is FluidCropAspect && other.width == width && other.height == height && other.round == round &&
      (width > 0 || other.id == id)

  override fun hashCode(): Int = (width * 31 + height) * 2 + if (round) 1 else 0
  override fun toString(): String = "FluidCropAspect($id)"

  companion object {
    val Freeform = FluidCropAspect("freeform", 0, 0)
    val Original = FluidCropAspect("original", -1, -1)
    val Square = FluidCropAspect("1:1", 1, 1)
    /** Il quadrato con la maschera tonda (la foto di un account). */
    val Circle = FluidCropAspect("circle", 1, 1, round = true)
    val W16x9 = FluidCropAspect("16:9", 16, 9)
    val W5x4 = FluidCropAspect("5:4", 5, 4)
    val W7x5 = FluidCropAspect("7:5", 7, 5)
    val W4x3 = FluidCropAspect("4:3", 4, 3)
    val W5x3 = FluidCropAspect("5:3", 5, 3)
    val W3x2 = FluidCropAspect("3:2", 3, 2)

    /** La fila di serie, nell'ordine di Foto (Libero, Originale, Quadrato, poi i rapporti), con il tondo dopo il quadrato. */
    val Presets: List<FluidCropAspect> = listOf(Freeform, Original, Square, Circle, W16x9, W5x4, W7x5, W4x3, W5x3, W3x2)

    /** Un rapporto dell'app (per esempio la forma di un widget, 2:1). */
    fun custom(width: Int, height: Int, round: Boolean = false): FluidCropAspect {
      require(width > 0 && height > 0) { "Proporzioni non valide: $width:$height" }
      return FluidCropAspect("$width:$height", width, height, round)
    }

    internal fun parse(id: String): FluidCropAspect? {
      Presets.firstOrNull { it.id == id }?.let { return it }
      val parts = id.split(':')
      if (parts.size != 2) return null
      val w = parts[0].toIntOrNull() ?: return null
      val h = parts[1].toIntOrNull() ?: return null
      return if (w > 0 && h > 0) custom(w, h) else null
    }
  }
}

/**
 * La geometria della modifica: le rotazioni di 90°, lo specchio, il raddrizzamento e il ritaglio. **Tutto in frazioni**,
 * cosi' la stessa geometria vale sull'anteprima ridotta e sul file a piena risoluzione.
 *
 * Le immagini, in ordine:
 * 1. la **sorgente** S, gia' girata come dice il suo EXIF (larga `sw`, alta `sh`);
 * 2. l'**orientata** O: S girata [quarterTurns] volte di 90° in senso antiorario (il tasto di Foto gira a sinistra), poi
 *    specchiata in orizzontale se [flipped] (lo specchio di Foto e' sempre orizzontale sullo schermo);
 * 3. il **ritaglio**: un rettangolo con centro ([centerX], [centerY]) e misure ([width], [height]) in frazioni di O
 *    (larghezze per la larghezza di O, altezze per l'altezza), girato di [angle] gradi rispetto a O. Sullo schermo il
 *    ritaglio e' dritto e la foto sotto e' girata di [angle] in senso orario.
 *
 * Il ritaglio sta sempre tutto dentro la foto girata: [fitted] lo stringe attorno al suo centro quando il raddrizzamento
 * lo farebbe uscire, come Foto che ingrandisce la foto mentre si gira il righello.
 *
 * @property angle il raddrizzamento, −45…45 gradi come il righello di «Raddrizza» in Foto (F8.2).
 * @property aspect le proporzioni bloccate (di serie libere), ricordate per la prossima apertura.
 */
data class FluidCropGeometry(
  val quarterTurns: Int = 0,
  val flipped: Boolean = false,
  val angle: Float = 0f,
  val centerX: Float = 0.5f,
  val centerY: Float = 0.5f,
  val width: Float = 1f,
  val height: Float = 1f,
  val aspect: FluidCropAspect = FluidCropAspect.Freeform,
) {
  init {
    require(quarterTurns in 0..3) { "quarterTurns fuori da 0..3: $quarterTurns" }
  }

  /** Vero se la geometria lascia la foto com'e' (le proporzioni scelte non contano finche' il ritaglio e' intero). */
  val isIdentity: Boolean
    get() = quarterTurns == 0 && !flipped && angle == 0f && near(centerX, 0.5f) && near(centerY, 0.5f) &&
      near(width, 1f) && near(height, 1f)

  /** Le misure di O per una sorgente di [sw] × [sh]. */
  fun orientedSize(sw: Int, sh: Int): Pair<Int, Int> = if (quarterTurns % 2 == 0) sw to sh else sh to sw

  /** Le misure in pixel del file che esce da una sorgente di [sw] × [sh] (almeno 1 × 1). */
  fun outputSize(sw: Int, sh: Int): Pair<Int, Int> {
    val (ow, oh) = orientedSize(sw, sh)
    return max(1, (width * ow).roundToInt()) to max(1, (height * oh).roundToInt())
  }

  /** Il rapporto larghezza/altezza del ritaglio per una sorgente di [sw] × [sh]. */
  fun outputRatio(sw: Int, sh: Int): Float {
    val (ow, oh) = orientedSize(sw, sh)
    return (width * ow) / (height * oh)
  }

  /**
   * Gira tutto di 90° in senso antiorario (il tasto di Foto): la foto, il ritaglio e le proporzioni scelte. Il
   * raddrizzamento resta, perche' e' relativo alla cornice.
   */
  fun rotatedLeft(): FluidCropGeometry = copy(
    quarterTurns = Math.floorMod(quarterTurns + if (flipped) -1 else 1, 4),
    centerX = centerY,
    centerY = 1f - centerX,
    width = height,
    height = width,
    aspect = aspect.rotated(),
  )

  /** Lo specchio orizzontale: il ritaglio si specchia con la foto e il raddrizzamento cambia verso. */
  fun flippedHorizontally(): FluidCropGeometry =
    copy(flipped = !flipped, centerX = 1f - centerX, angle = if (angle == 0f) 0f else -angle)

  /**
   * Lo stesso ritaglio stretto attorno al suo centro quanto basta per stare dentro la foto girata di [angle] (mai
   * allargato). Il centro, se e' troppo vicino al bordo, rientra.
   */
  fun fitted(sw: Int, sh: Int): FluidCropGeometry {
    val (ow, oh) = orientedSize(sw, sh)
    val geo = Oriented(this, ow, oh)
    val cx = geo.cx.coerceIn(0f, ow.toFloat())
    val cy = geo.cy.coerceIn(0f, oh.toFloat())
    val ex = geo.extentX(1f)
    val ey = geo.extentY(1f)
    val room = min(min(cx, ow - cx) / ex, min(cy, oh - cy) / ey)
    val scale = min(1f, room)
    if (scale >= 1f && cx == geo.cx && cy == geo.cy) return this
    return copy(centerX = cx / ow, centerY = cy / oh, width = width * scale, height = height * scale)
  }

  /**
   * Il ritaglio piu' grande con le proporzioni [ratio] (larghezza/altezza in pixel) che sta nella foto girata di
   * [angle], centrato il piu' vicino possibile al centro di adesso.
   */
  fun largestWithRatio(ratio: Float, sw: Int, sh: Int): FluidCropGeometry {
    val (ow, oh) = orientedSize(sw, sh)
    val rad = Math.toRadians(angle.toDouble())
    val c = abs(cos(rad)).toFloat()
    val s = abs(sin(rad)).toFloat()
    // Altezza h, larghezza r·h: le mezze ingombranti sono (r·h·c + h·s)/2 e (r·h·s + h·c)/2.
    val h = min(ow / (ratio * c + s), oh / (ratio * s + c))
    val w = ratio * h
    val ex = (w * c + h * s) / 2f
    val ey = (w * s + h * c) / 2f
    val cx = clampCenter(centerX * ow, ex, ow.toFloat())
    val cy = clampCenter(centerY * oh, ey, oh.toFloat())
    return copy(centerX = cx / ow, centerY = cy / oh, width = w / ow, height = h / oh)
  }

  /** Il ritaglio piu' grande nella foto, rispettando le proporzioni scelte ([aspect]) e il raddrizzamento. */
  fun reset(sw: Int, sh: Int): FluidCropGeometry {
    val (ow, oh) = orientedSize(sw, sh)
    val ratio = aspect.ratio(ow.toFloat() / oh) ?: (ow.toFloat() / oh)
    return copy(centerX = 0.5f, centerY = 0.5f).largestWithRatio(ratio, sw, sh)
  }

  /** Sposta il ritaglio di ([dx], [dy]) pixel di O, senza farlo uscire dalla foto girata. */
  fun moved(dx: Float, dy: Float, sw: Int, sh: Int): FluidCropGeometry {
    val (ow, oh) = orientedSize(sw, sh)
    val geo = Oriented(this, ow, oh)
    val ex = geo.extentX(1f)
    val ey = geo.extentY(1f)
    val cx = clampCenter(geo.cx + dx, ex, ow.toFloat())
    val cy = clampCenter(geo.cy + dy, ey, oh.toFloat())
    return copy(centerX = cx / ow, centerY = cy / oh)
  }

  /**
   * La matrice dalla sorgente (pixel di S, larga [sw] e alta [sh]) al file che esce (pixel del ritaglio, [outputSize]).
   * In forma affine: `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty`.
   */
  fun sourceToOutput(sw: Int, sh: Int): FluidAffine {
    val (ow, oh) = orientedSize(sw, sh)
    var m = FluidAffine.Identity
    // 1. Le rotazioni di 90° antiorarie: (x, y) -> (y, W - x), con W la larghezza del momento.
    var curW = sw
    var curH = sh
    repeat(quarterTurns) {
      m = FluidAffine(0f, -1f, 1f, 0f, 0f, curW.toFloat()).then(m)
      val t = curW
      curW = curH
      curH = t
    }
    // 2. Lo specchio orizzontale di O.
    if (flipped) m = FluidAffine(-1f, 0f, 0f, 1f, ow.toFloat(), 0f).then(m)
    // 3. Il ritaglio: d = R(angle)·(p − c) + (w/2, h/2), con R che gira in senso orario sullo schermo (y in giu').
    val geo = Oriented(this, ow, oh)
    val rad = Math.toRadians(angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    m = FluidAffine(1f, 0f, 0f, 1f, -geo.cx, -geo.cy).then(m)
    m = FluidAffine(co, si, -si, co, 0f, 0f).then(m)
    val (outW, outH) = outputSize(sw, sh)
    m = FluidAffine(1f, 0f, 0f, 1f, outW / 2f, outH / 2f).then(m)
    return m
  }

  /** Lo stesso per l'orientata: da O (pixel) al ritaglio. */
  internal fun orientedToOutput(ow: Int, oh: Int): FluidAffine {
    val geo = Oriented(this, ow, oh)
    val rad = Math.toRadians(angle.toDouble())
    val co = cos(rad).toFloat()
    val si = sin(rad).toFloat()
    return FluidAffine(1f, 0f, 0f, 1f, geo.w / 2f, geo.h / 2f)
      .then(FluidAffine(co, si, -si, co, 0f, 0f))
      .then(FluidAffine(1f, 0f, 0f, 1f, -geo.cx, -geo.cy))
  }

  /** Da S (pixel) a O (pixel): solo le rotazioni di 90° e lo specchio. */
  internal fun sourceToOriented(sw: Int, sh: Int): FluidAffine {
    val (ow, _) = orientedSize(sw, sh)
    var m = FluidAffine.Identity
    var curW = sw
    var curH = sh
    repeat(quarterTurns) {
      m = FluidAffine(0f, -1f, 1f, 0f, 0f, curW.toFloat()).then(m)
      val t = curW
      curW = curH
      curH = t
    }
    if (flipped) m = FluidAffine(-1f, 0f, 0f, 1f, ow.toFloat(), 0f).then(m)
    return m
  }

  internal companion object {
    fun near(a: Float, b: Float) = abs(a - b) < 1e-4f

    /**
     * Il centro [v] tenuto a [extent] dai bordi di un lato lungo [size]. Se il ritaglio e' largo quanto il lato (anche
     * per un millesimo di pixel di arrotondamento), il centro e' a meta'.
     */
    fun clampCenter(v: Float, extent: Float, size: Float): Float =
      if (extent * 2f >= size - 1e-3f) size / 2f else v.coerceIn(extent, size - extent)
  }
}

/** Il ritaglio in pixel di O, con le mezze misure ingombranti del rettangolo girato. */
internal class Oriented(g: FluidCropGeometry, ow: Int, oh: Int) {
  val cx = g.centerX * ow
  val cy = g.centerY * oh
  val w = g.width * ow
  val h = g.height * oh
  private val rad = Math.toRadians(g.angle.toDouble())
  private val c = abs(cos(rad)).toFloat()
  private val s = abs(sin(rad)).toFloat()

  /** La mezza larghezza del riquadro dritto che contiene il ritaglio girato, a scala [scale]. */
  fun extentX(scale: Float) = scale * (w * c + h * s) / 2f
  fun extentY(scale: Float) = scale * (w * s + h * c) / 2f
}

/**
 * Una trasformazione affine del piano, `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty` (le colonne di `android.graphics.Matrix`:
 * `a` = MSCALE_X, `c` = MSKEW_X, `b` = MSKEW_Y, `d` = MSCALE_Y). Pura, per i conti della geometria e le prove.
 */
data class FluidAffine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float) {
  fun mapX(x: Float, y: Float) = a * x + c * y + tx
  fun mapY(x: Float, y: Float) = b * x + d * y + ty

  /** La composizione `this ∘ other`: prima [other], poi questa (`a.then(b)` applicato a p e' `a(b(p))`). */
  fun then(other: FluidAffine): FluidAffine = FluidAffine(
    a = a * other.a + c * other.b,
    b = b * other.a + d * other.b,
    c = a * other.c + c * other.d,
    d = b * other.c + d * other.d,
    tx = a * other.tx + c * other.ty + tx,
    ty = b * other.tx + d * other.ty + ty,
  )

  /** L'inversa (la matrice non e' mai singolare: rotazioni, specchi, scale e traslazioni). */
  fun inverted(): FluidAffine {
    val det = a * d - b * c
    val ia = d / det
    val ib = -b / det
    val ic = -c / det
    val id = a / det
    return FluidAffine(ia, ib, ic, id, -(ia * tx + ic * ty), -(ib * tx + id * ty))
  }

  /** I nove valori di `android.graphics.Matrix.setValues`. */
  fun toMatrixValues(): FloatArray = floatArrayOf(a, c, tx, b, d, ty, 0f, 0f, 1f)

  companion object {
    val Identity = FluidAffine(1f, 0f, 0f, 1f, 0f, 0f)
  }
}
