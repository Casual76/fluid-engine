package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.max
import kotlin.math.min

/**
 * Due anelli con la stessa parametrizzazione: il vertice `i` del primo viaggia verso il vertice `i`
 * del secondo, e questo è *tutto* il morphing — il resto è un lerp.
 *
 * La corrispondenza la decide il `Morph` di androidx.graphics.shapes, che accoppia le curve per
 * caratteristiche (angoli con angoli, lati con lati) invece che per posizione d'indice. Il trucco
 * che rende il per-fotogramma gratuito: le cubiche di un Morph si corrispondono una a una fra
 * progress 0 e 1 e vengono interpolate **linearmente** nei punti di controllo — e una Bézier è
 * lineare nei suoi punti di controllo — quindi campionare le cubiche agli estremi con gli stessi
 * parametri e interpolare i campioni dà, punto per punto, la stessa curva che il Morph avrebbe
 * disegnato. Il Morph si paga una volta al decollo; ogni fotogramma dopo è aritmetica su due array.
 *
 * Il campionamento è per-cubica, non per lunghezza d'arco: una cubica corta (un angolo) riceve gli
 * stessi campioni di una lunga (un lato), che è esattamente la distribuzione che serve — i lati
 * dritti sono esatti con due punti, gli angoli sono dove la fedeltà si vede.
 */
internal class MatchedRings(
  val count: Int,
  /** x,y interlacciati, [count] punti, pixel di layout. */
  val start: FloatArray,
  val end: FloatArray,
)

/** Costruisce gli anelli accoppiati di un viaggio che coinvolge almeno una sagoma libera. */
internal fun buildMatchedRings(from: FluidForm, to: FluidForm): MatchedRings {
  val viaMorph = runCatching {
    val morph = Morph(toRoundedPolygon(from), toRoundedPolygon(to))
    val cubicsStart = morph.asCubics(0f)
    val cubicsEnd = morph.asCubics(1f)
    check(cubicsStart.size == cubicsEnd.size && cubicsStart.isNotEmpty())
    sampleMatchedCubics(cubicsStart, cubicsEnd)
  }
  // Il Morph può rifiutare un input degenere (vertici coincidenti, aree nulle). La risposta non è
  // un crash a metà gesto: due anelli ricampionati per lunghezza d'arco, allineati sul punto più
  // alto, danno un viaggio più povero ma sempre continuo.
  return viaMorph.getOrElse {
    val count = FluidFormPresets.FreehandVertices
    MatchedRings(
      count = count,
      start = ringToArray(alignRingStart(fallbackRing(from, count))),
      end = ringToArray(alignRingStart(fallbackRing(to, count))),
    )
  }
}

/** L'anello di una forma ferma, per il piano di riposo delle sagome libere. */
internal fun restRing(form: FluidForm): FloatArray {
  val viaPolygon = runCatching {
    val polygon = toRoundedPolygon(form)
    val cubics = polygon.cubics
    check(cubics.isNotEmpty())
    sampleCubics(cubics)
  }
  return viaPolygon.getOrElse {
    ringToArray(fallbackRing(form, FluidFormPresets.FreehandVertices))
  }
}

private fun toRoundedPolygon(form: FluidForm): RoundedPolygon = when (form) {
  is FluidForm.Slab -> {
    val f = form.frame
    RoundedPolygon(
      vertices = floatArrayOf(
        f.left, f.top,
        f.right, f.top,
        f.right, f.bottom,
        f.left, f.bottom,
      ),
      perVertexRounding = listOf(
        CornerRounding(form.cornerRadii.topLeft, form.smoothing),
        CornerRounding(form.cornerRadii.topRight, form.smoothing),
        CornerRounding(form.cornerRadii.bottomRight, form.smoothing),
        CornerRounding(form.cornerRadii.bottomLeft, form.smoothing),
      ),
    )
  }

  is FluidForm.Poly -> {
    val f = form.frame
    val vertices = FloatArray(form.vertices.size * 2)
    for (i in form.vertices.indices) {
      val v = form.vertices[i]
      vertices[i * 2] = f.left + v.x * f.width
      vertices[i * 2 + 1] = f.top + v.y * f.height
    }
    RoundedPolygon(vertices = vertices, rounding = CornerRounding(form.rounding))
  }

  is FluidForm.Group -> throw IllegalArgumentException(
    "Un gruppo non ha un anello solo: i suoi viaggi passano dal transito a pezzi, non da qui.",
  )
}

private fun sampleMatchedCubics(cubicsStart: List<Cubic>, cubicsEnd: List<Cubic>): MatchedRings {
  val plan = samplingPlan(cubicsStart.size)
  val count = plan.sumOf { it }
  val start = FloatArray(count * 2)
  val end = FloatArray(count * 2)
  var out = 0
  for (i in cubicsStart.indices) {
    val samples = plan[i]
    if (samples == 0) continue
    for (s in 0 until samples) {
      val t = s.toFloat() / samples
      writeCubicPoint(cubicsStart[i], t, start, out)
      writeCubicPoint(cubicsEnd[i], t, end, out)
      out++
    }
  }
  return MatchedRings(count, start, end)
}

private fun sampleCubics(cubics: List<Cubic>): FloatArray {
  val plan = samplingPlan(cubics.size)
  val count = plan.sumOf { it }
  val ring = FloatArray(count * 2)
  var out = 0
  for (i in cubics.indices) {
    val samples = plan[i]
    if (samples == 0) continue
    for (s in 0 until samples) {
      writeCubicPoint(cubics[i], s.toFloat() / samples, ring, out)
      out++
    }
  }
  return ring
}

/**
 * Quanti campioni prende ogni cubica, con il totale sotto [PhysicsMaxVertices].
 *
 * Quando le cubiche sono più dei vertici disponibili, ognuna tiene almeno il proprio punto di
 * partenza finché c'è posto: sacrificare cubiche intere aprirebbe buchi nel perimetro.
 */
private fun samplingPlan(cubicCount: Int): IntArray {
  if (cubicCount >= PhysicsMaxVertices) {
    return IntArray(cubicCount) { if (it < PhysicsMaxVertices) 1 else 0 }
  }
  val perCubic = max(1, PhysicsMaxVertices / cubicCount)
  return IntArray(cubicCount) { perCubic }
}

private fun writeCubicPoint(cubic: Cubic, t: Float, out: FloatArray, index: Int) {
  val mt = 1f - t
  val a = mt * mt * mt
  val b = 3f * mt * mt * t
  val c = 3f * mt * t * t
  val d = t * t * t
  out[index * 2] = a * cubic.anchor0X + b * cubic.control0X + c * cubic.control1X + d * cubic.anchor1X
  out[index * 2 + 1] = a * cubic.anchor0Y + b * cubic.control0Y + c * cubic.control1Y + d * cubic.anchor1Y
}

// --- Fallback senza Morph ----------------------------------------------------

private fun fallbackRing(form: FluidForm, count: Int): List<Offset> = when (form) {
  is FluidForm.Slab -> resampleClosed(slabPolyline(form), count)
  is FluidForm.Poly -> {
    val f = form.frame
    resampleClosed(form.vertices.map { Offset(f.left + it.x * f.width, f.top + it.y * f.height) }, count)
  }
  is FluidForm.Group -> throw IllegalArgumentException("Vedi toRoundedPolygon: i gruppi non passano da qui.")
}

/** Il perimetro di uno slab come polilinea densa: lati dritti più archi a otto passi. */
private fun slabPolyline(slab: FluidForm.Slab): List<Offset> {
  val f = slab.frame
  val half = min(f.width, f.height) / 2f
  val r = slab.cornerRadii
  val tl = r.topLeft.coerceIn(0f, half)
  val tr = r.topRight.coerceIn(0f, half)
  val br = r.bottomRight.coerceIn(0f, half)
  val bl = r.bottomLeft.coerceIn(0f, half)
  val points = ArrayList<Offset>(40)
  fun arc(cx: Float, cy: Float, radius: Float, fromDeg: Float) {
    if (radius <= 0f) {
      points += Offset(cx, cy)
      return
    }
    for (step in 0..8) {
      val angle = Math.toRadians((fromDeg + step * 90.0 / 8.0))
      points += Offset(
        cx + radius * kotlin.math.cos(angle).toFloat(),
        cy + radius * kotlin.math.sin(angle).toFloat(),
      )
    }
  }
  // In senso orario di schermo, dall'angolo in alto a sinistra.
  arc(f.left + tl, f.top + tl, tl, fromDeg = 180f)
  arc(f.right - tr, f.top + tr, tr, fromDeg = 270f)
  arc(f.right - br, f.bottom - br, br, fromDeg = 0f)
  arc(f.left + bl, f.bottom - bl, bl, fromDeg = 90f)
  return points
}

/** Ruota l'anello perché parta dal punto più in alto (a parità, più a sinistra), senza cambiarne il verso. */
internal fun alignRingStart(ring: List<Offset>): List<Offset> {
  if (ring.size < 2) return ring
  var best = 0
  for (i in 1 until ring.size) {
    val p = ring[i]
    val q = ring[best]
    if (p.y < q.y || (p.y == q.y && p.x < q.x)) best = i
  }
  if (best == 0) return ring
  return ring.subList(best, ring.size) + ring.subList(0, best)
}

private fun ringToArray(ring: List<Offset>): FloatArray {
  val out = FloatArray(ring.size * 2)
  for (i in ring.indices) {
    out[i * 2] = ring[i].x
    out[i * 2 + 1] = ring[i].y
  }
  return out
}

/** Una [FluidForm.Poly] sintetica ricavata da un anello in pixel: il punto di partenza di un ritargeting a metà volo. */
internal fun polyFromRing(ring: FloatArray, count: Int, rounding: Float = 0f): FluidForm.Poly {
  val points = ArrayList<Offset>(count)
  for (i in 0 until count) points += Offset(ring[i * 2], ring[i * 2 + 1])
  val bounds = boundsOf(points)
  val safeBounds = if (bounds.width < 1f || bounds.height < 1f) {
    Rect(bounds.left, bounds.top, bounds.left + max(1f, bounds.width), bounds.top + max(1f, bounds.height))
  } else {
    bounds
  }
  return FluidForm.Poly(
    frame = safeBounds,
    vertices = normalizeToBounds(points, safeBounds).let {
      // Il tetto dei vertici vale anche per gli anelli sintetici.
      if (it.size <= FluidFormPresets.MaxVertices) it else resampleClosed(it, FluidFormPresets.MaxVertices)
    },
    rounding = rounding,
  )
}
