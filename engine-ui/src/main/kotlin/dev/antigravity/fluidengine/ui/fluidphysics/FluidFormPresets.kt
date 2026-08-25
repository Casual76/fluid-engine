package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Il catalogo di forme del Playground, e la porta d'ingresso per quelle disegnate a mano.
 *
 * Ogni preset è costruibile su qualsiasi frame, perché un morph parte sempre da dove la forma
 * *è adesso*: un preset che imponesse la propria posizione farebbe saltare il pezzo prima di
 * trasformarlo.
 */
object FluidFormPresets {

  /**
   * Il tetto dei vertici, ed è il numero dell'array uniform nello shader poligonale: oltre questo,
   * il campo di distanza non li sa portare. I preset stanno molto sotto; il tratto a mano libera ci
   * si ricampiona dentro.
   */
  const val MaxVertices: Int = 64

  /**
   * A quanti vertici si ricampiona un tratto a mano libera. Abbastanza da tenere le anse di uno
   * scarabocchio vero, abbastanza pochi da lasciare respiro al loop dello shader.
   */
  const val FreehandVertices: Int = 48

  /** Un quadrato centrato nel frame, col lato del lato corto. */
  fun square(frame: Rect, radius: Float = 0f): FluidForm.Slab {
    val side = min(frame.width, frame.height)
    val cx = frame.center.x
    val cy = frame.center.y
    return FluidForm.Slab(
      frame = Rect(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f),
      cornerRadii = FluidCornerRadii.all(radius.coerceIn(0f, side / 2f)),
    )
  }

  fun rectangle(frame: Rect, radius: Float = 0f): FluidForm.Slab = FluidForm.Slab(
    frame = frame,
    cornerRadii = FluidCornerRadii.all(radius.coerceIn(0f, frame.minDimension / 2f)),
  )

  /** Una capsula: il raggio è metà del lato corto per definizione, mai un numero fisso. */
  fun capsule(frame: Rect): FluidForm.Slab = FluidForm.Slab(
    frame = frame,
    cornerRadii = FluidCornerRadii.all(frame.minDimension / 2f),
  )

  /** Un trapezio isoscele: il lato corto sopra, rientrato di [topInsetFraction] per parte. */
  fun trapezoid(
    frame: Rect,
    topInsetFraction: Float = 0.22f,
    rounding: Float = 12f,
  ): FluidForm.Poly {
    val inset = topInsetFraction.coerceIn(0f, 0.45f)
    return FluidForm.Poly(
      frame = frame,
      vertices = listOf(
        Offset(inset, 0f),
        Offset(1f - inset, 0f),
        Offset(1f, 1f),
        Offset(0f, 1f),
      ),
      rounding = rounding.coerceAtLeast(0f),
    )
  }

  /** Una stella a [points] punte. [innerRatio] è il raggio delle valli rispetto alle punte. */
  fun star(
    frame: Rect,
    points: Int = 5,
    innerRatio: Float = 0.45f,
    rounding: Float = 8f,
  ): FluidForm.Poly {
    val n = points.coerceIn(3, MaxVertices / 2)
    val inner = innerRatio.coerceIn(0.1f, 0.95f)
    val vertices = ArrayList<Offset>(n * 2)
    for (i in 0 until n * 2) {
      // Da mezzogiorno, in senso orario di schermo (la y cresce verso il basso).
      val angle = -PI.toFloat() / 2f + i * PI.toFloat() / n
      val radius = if (i % 2 == 0) 0.5f else 0.5f * inner
      vertices += Offset(0.5f + radius * cos(angle), 0.5f + radius * sin(angle))
    }
    return FluidForm.Poly(frame = frame, vertices = vertices, rounding = rounding.coerceAtLeast(0f))
  }

  /**
   * Una macchia organica, deterministica per [seed]: lo stesso numero dà sempre la stessa macchia,
   * perché una forma che cambia sotto le dita a ogni ricomposizione sembra un bug, non natura.
   */
  fun blob(
    frame: Rect,
    seed: Int = 0,
    irregularity: Float = 0.35f,
    rounding: Float = 10f,
  ): FluidForm.Poly {
    val n = 12
    val wobble = irregularity.coerceIn(0f, 0.6f)
    val vertices = ArrayList<Offset>(n)
    for (i in 0 until n) {
      val angle = -PI.toFloat() / 2f + i * 2f * PI.toFloat() / n
      // Hash intero → [-1, 1]. Un LCG basta: serve varieta' ripetibile, non qualita' statistica.
      val noise = hashToUnit(seed * 31 + i)
      val radius = 0.5f * (1f - wobble * 0.5f + wobble * 0.5f * noise)
      vertices += Offset(0.5f + radius * cos(angle), 0.5f + radius * sin(angle))
    }
    return FluidForm.Poly(frame = frame, vertices = vertices, rounding = rounding.coerceAtLeast(0f))
  }

  private fun hashToUnit(value: Int): Float {
    var h = value * -0x61c88647
    h = h xor (h ushr 16)
    h *= -0x7ee3623b
    h = h xor (h ushr 13)
    return (h and 0xFFFF).toFloat() / 0x7FFF.toFloat() - 1f
  }

  /**
   * Un tratto a mano libera reso trasformabile: semplificato (Ramer–Douglas–Peucker), chiuso,
   * ricampionato a [FreehandVertices] passi uguali di perimetro, col verso normalizzato.
   *
   * Sotto tre punti distinti degrada a un cerchio nel riquadro del tratto — la richiesta era una
   * forma, e un cerchio è la forma più onesta che si possa ricavare da un tocco.
   */
  fun fromFreehand(points: List<Offset>, rounding: Float = 6f): FluidForm {
    val bounds = if (points.isEmpty()) Rect.Zero else boundsOf(points)
    val diagonal = hypot(bounds.width, bounds.height)
    if (countDistinct(points, tolerance = diagonal * 0.01f + 1f) < 3 || diagonal < 24f) {
      val radius = (diagonal / 2f).coerceAtLeast(48f)
      return FluidForm.circle(if (points.isEmpty()) Offset.Zero else bounds.center, radius)
    }

    // Il tratto arriva aperto: se il dito è tornato vicino alla partenza, l'ultimo tratto è già la
    // chiusura e il punto doppio va tolto prima che il ricampionamento gli dedichi un segmento.
    var stroke = rdpSimplify(points, tolerance = diagonal * 0.015f)
    if (stroke.size > 2 &&
      hypot(stroke.last().x - stroke.first().x, stroke.last().y - stroke.first().y) < diagonal * 0.05f
    ) {
      stroke = stroke.dropLast(1)
    }

    val ring = ensureClockwise(resampleClosed(stroke, FreehandVertices))
    val ringBounds = boundsOf(ring)
    return FluidForm.Poly(
      frame = ringBounds,
      vertices = normalizeToBounds(ring, ringBounds),
      rounding = rounding.coerceAtLeast(0f),
    )
  }
}
