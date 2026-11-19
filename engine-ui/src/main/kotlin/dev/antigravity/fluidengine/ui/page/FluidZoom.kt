package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import kotlin.math.max
import kotlin.math.min

// Lo zoom dalla sorgente (4.0, piano «iOS in Android», F5B.3): la geometria, scritta una volta. Le pagine
// (`FluidPageHostState`) e il foglio di vetro (`FluidGlassModalSheet`) la usano con i loro orologi; i preset di F6B
// («un elemento che diventa pagina») ci appoggiano i loro. Qui non c'è tempo né stato: solo sagome e un dito.

/**
 * Una sagoma dello zoom: il riquadro, in pixel nelle coordinate di chi disegna, e i raggi dei quattro angoli
 * (circolari, assoluti). È la cosa che viaggia: parte dalla sorgente, arriva alla pagina o al foglio, e torna.
 *
 * I raggi sono circolari di proposito: il ritaglio di un layer è un rettangolo arrotondato che l'hardware fa gratis,
 * come la carta delle pagine. Una sorgente con angoli continui entra col suo raggio circolare equivalente
 * ([FluidZoom.cornersOf]), così al primo fotogramma la sagoma copre la sorgente senza che si veda un angolo diverso.
 */
@Immutable
data class FluidZoomShape(
  val bounds: Rect,
  val corners: FluidPageCorners = FluidPageCorners.Zero,
) {
  /** Una sagoma con area e numeri finiti: l'unica che si può disegnare o da cui si può partire. */
  val isUsable: Boolean get() = bounds.isFinite && bounds.width > 0f && bounds.height > 0f

  /** La stessa sagoma spostata (per passare dalle coordinate della radice a quelle di chi disegna). */
  fun translate(offset: Offset): FluidZoomShape = copy(bounds = bounds.translate(offset))

  /** Il contorno, con i raggi limitati a mezzo lato corto: quello che un ritaglio o un'ombra usano. */
  fun toRoundRect(): RoundRect {
    val half = max(0f, min(bounds.width, bounds.height) / 2f)
    fun r(value: Float) = CornerRadius(value.coerceIn(0f, half))
    return RoundRect(
      rect = bounds,
      topLeft = r(corners.topLeft),
      topRight = r(corners.topRight),
      bottomRight = r(corners.bottomRight),
      bottomLeft = r(corners.bottomLeft),
    )
  }
}

/**
 * Dove sta il contenuto dell'arrivo (grande quanto il suo riquadro di riposo) dentro una sagoma: una scala uniforme
 * attorno al **centro** del riquadro di riposo e uno spostamento, gli stessi numeri di un `graphicsLayer` con
 * `TransformOrigin.Center`. Così chi già legge la geometria di una pagina (scala attorno al centro più spostamento) la
 * legge uguale anche durante uno zoom.
 */
@Immutable
data class FluidZoomContent(val scale: Float, val translation: Offset) {
  companion object {
    val Identity = FluidZoomContent(1f, Offset.Zero)
  }
}

/**
 * Lo zoom: «una sagoma e un dito» (R8, idea 12). Funzioni pure, senza tempo: l'orologio è di chi le chiama.
 *
 * - **Una sagoma**: [lerp] fra due [FluidZoomShape], con la frazione di un orologio qualunque ([travelFraction] per
 *   le pagine, la molla dell'host per il foglio). Ogni viaggio parte dalla sagoma **che si vede adesso**: un push
 *   interrotto da un pop, un ritorno ripreso dal dito, ripartono da dove sono, senza salti.
 * - **Un dito**: [follow] tiene la sagoma sotto il dito mentre la si tira indietro: il punto toccato resta sotto il
 *   dito, la sagoma si rimpicciolisce con la distanza ([followScale]) e, se il dito torna indietro, si riapre verso la
 *   pagina intera.
 * - **Il contenuto**: [content] mette la pagina d'arrivo dentro la sagoma, intera e in scala (la copre, ancorata in
 *   alto al centro); [contentClip] dà la sagoma nelle coordinate della pagina non scalata, per ritagliarla.
 */
object FluidZoom {

  /**
   * Quanto si rimpicciolisce al massimo la pagina sotto il dito, in frazione: 0,4 (fino al 60% a una larghezza
   * intera di trascinamento). Al rilascio di serie (0,35 W) la pagina è all'≈77%. A occhio: Apple non pubblica il
   * ritorno interattivo dello zoom e nei video non c'è una clip a tempo vero (`sessioni/f5b-3/rapporto.md`).
   */
  const val FollowShrink: Float = 0.4f

  /**
   * Entro quanto una sagoma che torna nella sorgente ci è «arrivata», in dp: 1. Lì il viaggio finisce e la sorgente
   * rinasce, invece di aspettare la coda della molla (sotto il dp non si vede più muovere, ma la pagina in miniatura
   * resterebbe sulla card).
   */
  const val ArrivalDp: Float = 1f

  /**
   * La dissolvenza di Apple (F5B.3; di serie dal 19 novembre 2026, scelta di Alessio sui video a tempo vero): la pagina si
   * dissolve nella sorgente dentro la sagoma, da circa un terzo della strada fra pagina intera e sorgente ([CrossfadeFrom])
   * fino a quasi l'arrivo ([CrossfadeTo]). Misurata a occhio su WWDC24 10145 (1:46, il ritorno col dito nella cella; il
   * video è a metà velocità, vedi [FluidPageMotion.ResponseZoomSeconds]): il contenuto della cella compare nel riquadro
   * quando la sagoma ha fatto ~0,3 della strada, poco prima del rilascio, e quello della pagina non si vede più verso
   * 0,85–0,9; a tempo vero la dissolvenza dura ≈125 ms. Si legge dalla strada e non dal tempo, quindi l'orologio del video
   * non la sposta, e vale anche all'apertura: lì Apple sbiadisce la cella fra 0,07 e 0,60 della strada (≈90 ms), qui fra
   * 0,1 e 0,67 (≈100 ms). Due soglie per verso farebbero saltare l'opacità quando un push si inverte in pop: una sola.
   */
  internal const val CrossfadeFrom: Float = 0.33f
  internal const val CrossfadeTo: Float = 0.9f

  /**
   * Quanto della sorgente si vede dentro [shape] (0 la pagina, 1 la sorgente), dalla strada fatta fra [full] e [source]:
   * si legge dalla sagoma e non dal tempo, così vale uguale col tasto, col dito e all'apertura, e non salta fra le fasi.
   */
  internal fun crossfade(shape: FluidZoomShape, full: FluidZoomShape, source: FluidZoomShape): Float {
    val total = distance(full, source)
    if (total < 1e-3f) return 1f
    val done = 1f - distance(shape, source) / total
    return ((done - CrossfadeFrom) / (CrossfadeTo - CrossfadeFrom)).coerceIn(0f, 1f)
  }

  /**
   * Dove va la fotografia della sorgente (grande [snapshot]) dentro la sagoma [clip], nelle stesse coordinate di [clip]:
   * larga quanto la sagoma, in scala uniforme, in alto (dove la card e la pagina hanno la foto). All'arrivo la sagoma è
   * la sorgente e la fotografia la copre esattamente.
   */
  internal fun snapshotRect(clip: Rect, snapshot: Size): Rect {
    if (snapshot.width <= 0f || snapshot.height <= 0f) return Rect(clip.left, clip.top, clip.left, clip.top)
    val scale = clip.width / snapshot.width
    return Rect(clip.left, clip.top, clip.right, clip.top + snapshot.height * scale)
  }

  /** Quanto sono lontane due sagome: lo spostamento del bordo che si sposta di più, in px. */
  fun distance(a: FluidZoomShape, b: FluidZoomShape): Float = maxOf(
    kotlin.math.abs(a.bounds.left - b.bounds.left),
    kotlin.math.abs(a.bounds.top - b.bounds.top),
    kotlin.math.abs(a.bounds.right - b.bounds.right),
    kotlin.math.abs(a.bounds.bottom - b.bounds.bottom),
  )

  /**
   * La sagoma a [fraction] fra [from] (0) e [to] (1). Non è limitata: una molla che supera porta la sagoma appena
   * oltre l'arrivo, come porta oltre il resto del movimento; il riquadro resta comunque dritto (destra ≥ sinistra).
   */
  fun lerp(from: FluidZoomShape, to: FluidZoomShape, fraction: Float): FluidZoomShape {
    val f = if (fraction.isFinite()) fraction else 1f
    fun l(a: Float, b: Float) = a + (b - a) * f
    val left = l(from.bounds.left, to.bounds.left)
    val top = l(from.bounds.top, to.bounds.top)
    val right = max(left, l(from.bounds.right, to.bounds.right))
    val bottom = max(top, l(from.bounds.bottom, to.bounds.bottom))
    val a = from.corners
    val b = to.corners
    return FluidZoomShape(
      Rect(left, top, right, bottom),
      FluidPageCorners(
        topLeft = max(0f, l(a.topLeft, b.topLeft)),
        topRight = max(0f, l(a.topRight, b.topRight)),
        bottomRight = max(0f, l(a.bottomRight, b.bottomRight)),
        bottomLeft = max(0f, l(a.bottomLeft, b.bottomLeft)),
      ),
    )
  }

  /**
   * La frazione di un viaggio su un orologio di corsa: 0 a [fromTravel], 1 a [toTravel], in linea (oltre se la molla
   * supera). Se partenza e arrivo coincidono il viaggio è già finito: 1.
   */
  fun travelFraction(travel: Float, fromTravel: Float, toTravel: Float): Float {
    val span = toTravel - fromTravel
    if (kotlin.math.abs(span) < 1e-3f || !travel.isFinite()) return 1f
    return (travel - fromTravel) / span
  }

  /**
   * Quanto è grande la sagoma sotto il dito, a [dragPx] di trascinamento verso l'uscita: 1 al contatto, poi giù con
   * una curva che rallenta, fino a `1 − FollowShrink` a una larghezza intera. Sotto zero (il dito è tornato oltre il
   * contatto) vale 1: lì la sagoma si riapre con [follow].
   */
  fun followScale(dragPx: Float, width: Float): Float {
    if (width <= 0f || !dragPx.isFinite()) return 1f
    val x = (dragPx / width).coerceIn(0f, 1f)
    val eased = 1f - (1f - x) * (1f - x)
    return 1f - FollowShrink * eased
  }

  /**
   * La sagoma sotto il dito.
   *
   * [base] è la sagoma che si vedeva quando il dito l'ha presa, [grabTravel] la corsa di allora, [dragPx] quanto il
   * dito si è spostato da lì verso l'uscita ([direction] +1 se l'uscita è a destra), [pivot] dove il dito l'ha presa.
   * - Verso l'uscita la sagoma si rimpicciolisce attorno al punto toccato e trasla col dito: **il punto toccato resta
   *   sotto il dito**, come la pagina che segue il dito pixel per pixel (regola delle pagine).
   * - Indietro oltre il contatto (una sagoma ripresa a metà di un ritorno, [grabTravel] > 0) si riapre verso [full],
   *   la pagina intera, che raggiunge quando la corsa torna a zero.
   */
  fun follow(
    base: FluidZoomShape,
    full: FluidZoomShape,
    dragPx: Float,
    grabTravel: Float,
    direction: Int,
    pivot: Offset,
    width: Float,
  ): FluidZoomShape {
    if (!dragPx.isFinite()) return base
    if (dragPx < 0f) {
      val f = if (grabTravel <= 0f) 1f else (-dragPx / grabTravel).coerceIn(0f, 1f)
      return lerp(base, full, f)
    }
    val k = followScale(dragPx, width)
    val dx = dragPx * (if (direction < 0) -1f else 1f)
    val b = base.bounds
    return FluidZoomShape(
      Rect(
        left = pivot.x + (b.left - pivot.x) * k + dx,
        top = pivot.y + (b.top - pivot.y) * k,
        right = pivot.x + (b.right - pivot.x) * k + dx,
        bottom = pivot.y + (b.bottom - pivot.y) * k,
      ),
      base.corners,
    )
  }

  /**
   * Il contenuto d'arrivo, grande come [target], dentro [shape]: la scala che **copre** la sagoma (nessun vuoto ai
   * lati, la pagina si vede intera in larghezza o in altezza) e l'ancoraggio **in alto al centro**, perché la parte di
   * una pagina che corrisponde alla sua sorgente (la foto, il titolo) sta in cima. Lo spostamento è attorno al centro
   * di [target] (vedi [FluidZoomContent]). Alla sagoma d'arrivo vale l'identità.
   */
  fun content(shape: FluidZoomShape, target: Rect): FluidZoomContent {
    val w = target.width
    val h = target.height
    if (w <= 0f || h <= 0f || !shape.isUsable) return FluidZoomContent.Identity
    val s = max(shape.bounds.width / w, shape.bounds.height / h)
    val center = target.center
    return FluidZoomContent(
      scale = s,
      translation = Offset(
        x = shape.bounds.center.x - center.x,
        y = shape.bounds.top - (center.y - s * h / 2f),
      ),
    )
  }

  /**
   * [shape] nelle coordinate del contenuto **non scalato** (origine in alto a sinistra di [target]), con i raggi divisi
   * per la scala: ritagliato lì e poi scalato da [content], torna esattamente [shape] sullo schermo.
   */
  fun contentClip(shape: FluidZoomShape, target: Rect, content: FluidZoomContent): FluidZoomShape {
    val s = content.scale
    if (s <= 0f || !s.isFinite()) return shape
    val c = target.center
    fun x(v: Float) = c.x + (v - c.x - content.translation.x) / s - target.left
    fun y(v: Float) = c.y + (v - c.y - content.translation.y) / s - target.top
    val b = shape.bounds
    val k = shape.corners
    return FluidZoomShape(
      Rect(x(b.left), y(b.top), x(b.right), y(b.bottom)),
      FluidPageCorners(k.topLeft / s, k.topRight / s, k.bottomRight / s, k.bottomLeft / s),
    )
  }

  /**
   * La sagoma di una sorgente ([FluidTransitionSourceGeometry], riquadro nella radice) nelle coordinate di chi disegna,
   * che stanno a [origin] nella radice.
   */
  fun shapeOf(
    geometry: FluidTransitionSourceGeometry,
    origin: Offset,
    density: Density,
    layoutDirection: LayoutDirection,
  ): FluidZoomShape {
    val bounds = geometry.bounds.translate(-origin)
    return FluidZoomShape(bounds, cornersOf(geometry.shape, bounds.size, density, layoutDirection))
  }

  /**
   * I raggi circolari di [shape] alla taglia [size]. Una forma continua ([ContinuousCornerShape]) dà il suo raggio
   * circolare equivalente (lo stesso che usa da piccola, 0,82 del continuo; una capsula resta mezzo lato); un'altra
   * forma ad angoli dà i suoi; il resto ripiega sul contorno (rettangolo arrotondato o niente).
   */
  fun cornersOf(shape: Shape, size: Size, density: Density, layoutDirection: LayoutDirection): FluidPageCorners {
    if (size.width <= 0f || size.height <= 0f) return FluidPageCorners.Zero
    val half = size.minDimension / 2f
    if (shape is CornerBasedShape) {
      val rtl = layoutDirection == LayoutDirection.Rtl
      // Sinistra è start in LTR ed end in RTL.
      fun px(left: Boolean, top: Boolean): Float {
        val corner = when {
          top && (left != rtl) -> shape.topStart
          top -> shape.topEnd
          left != rtl -> shape.bottomStart
          else -> shape.bottomEnd
        }
        val raw = corner.toPx(size, density).coerceIn(0f, half)
        return if (shape is ContinuousCornerShape) ContinuousCornerShape.roundedEquivalent(raw, size).x else raw
      }
      return FluidPageCorners(
        topLeft = px(left = true, top = true),
        topRight = px(left = false, top = true),
        bottomRight = px(left = false, top = false),
        bottomLeft = px(left = true, top = false),
      )
    }
    return when (val outline = shape.createOutline(size, layoutDirection, density)) {
      is Outline.Rounded -> outline.roundRect.let {
        FluidPageCorners(it.topLeftCornerRadius.x, it.topRightCornerRadius.x, it.bottomRightCornerRadius.x, it.bottomLeftCornerRadius.x)
      }
      else -> FluidPageCorners.Zero
    }
  }
}
