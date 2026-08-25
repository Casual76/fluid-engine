package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * La matematica di Fluid-physics, senza un solo tipo Android dentro.
 *
 * Tutto quello che decide *dove* sta il materiale — la semplificazione di un tratto a mano libera,
 * il ricampionamento, l'accoppiamento dei pezzi, il campo di distanza — vive qui come funzioni pure,
 * per la stessa ragione per cui `targetLevel` e `fluidFoldedTabWidths` vivono fuori dai loro
 * componenti: la AGSL non si può mettere sotto test, ma la sua aritmetica sì, e questi sono gli
 * specchi Kotlin delle stesse formule. Se un test qui passa e lo shader sbaglia, la differenza è
 * nella trascrizione, che è un diff di venti righe invece che un debug alla cieca sulla GPU.
 */

// --- Tratto a mano libera ----------------------------------------------------

/**
 * Ramer–Douglas–Peucker su una polilinea aperta.
 *
 * Un dito campionato a 120 Hz produce centinaia di punti quasi collineari; tenerli tutti significa
 * dare al [resampleClosed] un perimetro pieno di micro-spigoli che il raccordo poi deve nascondere.
 * Gli estremi sopravvivono sempre.
 */
internal fun rdpSimplify(points: List<Offset>, tolerance: Float): List<Offset> {
  if (points.size < 3 || tolerance <= 0f) return points
  val keep = BooleanArray(points.size)
  keep[0] = true
  keep[points.lastIndex] = true

  // Iterativo con uno stack esplicito: uno scarabocchio lungo non deve poter sfondare lo stack
  // delle chiamate.
  val stack = ArrayDeque<IntArray>()
  stack.addLast(intArrayOf(0, points.lastIndex))
  while (stack.isNotEmpty()) {
    val (first, last) = stack.removeLast()
    var maxDistance = 0f
    var index = -1
    for (i in first + 1 until last) {
      val d = perpendicularDistance(points[i], points[first], points[last])
      if (d > maxDistance) {
        maxDistance = d
        index = i
      }
    }
    if (index != -1 && maxDistance > tolerance) {
      keep[index] = true
      stack.addLast(intArrayOf(first, index))
      stack.addLast(intArrayOf(index, last))
    }
  }
  return points.filterIndexed { i, _ -> keep[i] }
}

private fun perpendicularDistance(point: Offset, lineStart: Offset, lineEnd: Offset): Float {
  val dx = lineEnd.x - lineStart.x
  val dy = lineEnd.y - lineStart.y
  val lengthSquared = dx * dx + dy * dy
  if (lengthSquared <= 1e-12f) return hypot(point.x - lineStart.x, point.y - lineStart.y)
  // Distanza dal segmento, non dalla retta: la coda di un tratto ricurvo non deve essere giudicata
  // da un prolungamento su cui il dito non è mai passato.
  val t = (((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lengthSquared)
    .coerceIn(0f, 1f)
  return hypot(point.x - (lineStart.x + t * dx), point.y - (lineStart.y + t * dy))
}

/**
 * Ricampiona un anello chiuso a [count] punti equidistanti lungo il perimetro.
 *
 * L'equidistanza è quello che rende due anelli confrontabili punto a punto: è la
 * parametrizzazione condivisa su cui il morphing interpola. L'ultimo punto d'ingresso NON deve
 * ripetere il primo; l'anello si chiude da sé.
 */
internal fun resampleClosed(points: List<Offset>, count: Int): List<Offset> {
  require(count >= 3) { "Un anello ha almeno tre punti." }
  if (points.isEmpty()) return emptyList()
  if (points.size == 1) return List(count) { points[0] }

  val segments = points.size
  val lengths = FloatArray(segments)
  var perimeter = 0f
  for (i in 0 until segments) {
    val a = points[i]
    val b = points[(i + 1) % segments]
    lengths[i] = hypot(b.x - a.x, b.y - a.y)
    perimeter += lengths[i]
  }
  if (perimeter <= 1e-6f) return List(count) { points[0] }

  val step = perimeter / count
  val result = ArrayList<Offset>(count)
  var segment = 0
  var travelled = 0f
  for (i in 0 until count) {
    val target = i * step
    while (segment < segments - 1 && travelled + lengths[segment] < target) {
      travelled += lengths[segment]
      segment++
    }
    val a = points[segment]
    val b = points[(segment + 1) % segments]
    val within = if (lengths[segment] <= 1e-6f) 0f else (target - travelled) / lengths[segment]
    result += Offset(a.x + (b.x - a.x) * within, a.y + (b.y - a.y) * within)
  }
  return result
}

/**
 * Area con segno (shoelace) nello spazio dello schermo: positiva se l'anello gira in senso orario
 * con la y che cresce verso il basso.
 */
internal fun signedArea(points: List<Offset>): Float {
  var doubled = 0f
  for (i in points.indices) {
    val a = points[i]
    val b = points[(i + 1) % points.size]
    doubled += (b.x - a.x) * (b.y + a.y)
  }
  // Il segno dello shoelace classico vale per la y che cresce verso l'alto; sullo schermo cresce
  // verso il basso, quindi si inverte — l'orario di schermo deve uscire positivo.
  return -doubled / 2f
}

/**
 * Consegna l'anello in senso orario di schermo, qualunque verso avesse il dito.
 *
 * Il verso è parte della parametrizzazione condivisa: due anelli che girano in versi opposti si
 * accoppierebbero punto a punto in diagonale e il morph passerebbe per un nodo a farfalla.
 */
internal fun ensureClockwise(points: List<Offset>): List<Offset> =
  if (signedArea(points) >= 0f) points else points.reversed()

/** Il rettangolo minimo che contiene tutti i punti. */
internal fun boundsOf(points: List<Offset>): Rect {
  var left = Float.POSITIVE_INFINITY
  var top = Float.POSITIVE_INFINITY
  var right = Float.NEGATIVE_INFINITY
  var bottom = Float.NEGATIVE_INFINITY
  for (p in points) {
    left = min(left, p.x)
    top = min(top, p.y)
    right = max(right, p.x)
    bottom = max(bottom, p.y)
  }
  return Rect(left, top, right, bottom)
}

/** I punti espressi come frazioni 0..1 di [bounds]. Un lato degenere collassa al suo centro. */
internal fun normalizeToBounds(points: List<Offset>, bounds: Rect): List<Offset> {
  val width = bounds.width
  val height = bounds.height
  return points.map { p ->
    Offset(
      if (width <= 1e-6f) 0.5f else (p.x - bounds.left) / width,
      if (height <= 1e-6f) 0.5f else (p.y - bounds.top) / height,
    )
  }
}

/** Quanti punti *distinti* (oltre una tolleranza) ci sono nel tratto. */
internal fun countDistinct(points: List<Offset>, tolerance: Float): Int {
  if (points.isEmpty()) return 0
  var count = 1
  var last = points[0]
  for (i in 1 until points.size) {
    val p = points[i]
    if (hypot(p.x - last.x, p.y - last.y) > tolerance) {
      count++
      last = p
    }
  }
  return count
}

// --- Interpolazione degli Slab ----------------------------------------------

/**
 * La geometria di uno [FluidForm.Slab] a metà viaggio, come otto numeri.
 *
 * `t` non è vincolato a 0..1 di proposito: le molle della casa hanno overshoot, e l'overshoot in
 * spazio di forma — un quadrato che oltrepassa appena il cerchio e torna — è esattamente la vita
 * che distingue un morph fluido da un cross-fade. Quello che non può mai uscire dai binari sono le
 * grandezze fisiche: i lati non scendono sotto un minimo e i raggi restano fra zero e mezzo lato.
 */
internal fun lerpSlab(start: FluidForm.Slab, end: FluidForm.Slab, t: Float): FloatArray {
  fun lerp(a: Float, b: Float) = a + (b - a) * t
  val left = lerp(start.frame.left, end.frame.left)
  val top = lerp(start.frame.top, end.frame.top)
  val right = lerp(start.frame.right, end.frame.right)
  val bottom = lerp(start.frame.bottom, end.frame.bottom)
  val width = max(right - left, MinSlabSidePx)
  val height = max(bottom - top, MinSlabSidePx)
  val half = min(width, height) / 2f
  fun radius(a: Float, b: Float) = lerp(a, b).coerceIn(0f, half)
  return floatArrayOf(
    left, top, width, height,
    radius(start.cornerRadii.topLeft, end.cornerRadii.topLeft),
    radius(start.cornerRadii.topRight, end.cornerRadii.topRight),
    radius(start.cornerRadii.bottomRight, end.cornerRadii.bottomRight),
    radius(start.cornerRadii.bottomLeft, end.cornerRadii.bottomLeft),
  )
}

/** Sotto questo lato uno slab in viaggio smette di stringersi: un pezzo non degenera mai a zero. */
internal const val MinSlabSidePx: Float = 1f

// --- Accoppiamento dei pezzi -------------------------------------------------

/**
 * Chi viaggia verso chi, quando un gruppo di [sourceCount] pezzi diventa uno di [targetCount].
 *
 * Il risultato ha `max(sourceCount, targetCount)` coppie `(sorgente, destinazione)`: in una fusione
 * 2→1 entrambe le sorgenti puntano alla stessa destinazione e il ponte le salda per strada; in una
 * scissione 1→2 due copie della sorgente partono insieme e divergono. Nessun pezzo resta orfano.
 *
 * L'assegnazione è al costo minimo (distanza dei centri più differenza di taglia) ed è
 * **deterministica**: a parità di costo vince l'indice più basso, così lo stesso viaggio produce
 * sempre la stessa coreografia — un morph che sceglie a caso chi va dove sembra un bug anche
 * quando non lo è.
 */
internal fun matchPieces(
  sourceCenters: List<Offset>,
  sourceSizes: List<Float>,
  targetCenters: List<Offset>,
  targetSizes: List<Float>,
): List<IntArray> {
  val sourceCount = sourceCenters.size
  val targetCount = targetCenters.size
  require(sourceCount > 0 && targetCount > 0) { "Un accoppiamento vuole almeno un pezzo per lato." }

  fun cost(s: Int, t: Int): Float =
    hypot(sourceCenters[s].x - targetCenters[t].x, sourceCenters[s].y - targetCenters[t].y) +
      abs(sourceSizes[s] - targetSizes[t])

  val pairs = ArrayList<IntArray>(max(sourceCount, targetCount))
  if (sourceCount >= targetCount) {
    // Prima ogni destinazione riceve la sorgente più vicina non ancora assegnata (nessun target
    // orfano), poi le sorgenti rimaste scelgono la loro destinazione più vicina (nessuna sorgente
    // orfana). L'ordine di servizio è per indice: è il tie-break.
    val taken = BooleanArray(sourceCount)
    val assignment = IntArray(sourceCount) { -1 }
    for (t in 0 until targetCount) {
      var best = -1
      var bestCost = Float.POSITIVE_INFINITY
      for (s in 0 until sourceCount) {
        if (taken[s]) continue
        val c = cost(s, t)
        if (c < bestCost) {
          bestCost = c
          best = s
        }
      }
      taken[best] = true
      assignment[best] = t
    }
    for (s in 0 until sourceCount) {
      if (assignment[s] == -1) {
        var best = 0
        var bestCost = Float.POSITIVE_INFINITY
        for (t in 0 until targetCount) {
          val c = cost(s, t)
          if (c < bestCost) {
            bestCost = c
            best = t
          }
        }
        assignment[s] = best
      }
    }
    for (s in 0 until sourceCount) pairs += intArrayOf(s, assignment[s])
  } else {
    // Speculare: più destinazioni che sorgenti. Ogni sorgente va servita, poi ogni destinazione
    // scoperta pesca la sorgente più vicina (che quindi si sdoppia).
    val taken = BooleanArray(targetCount)
    val assignment = IntArray(targetCount) { -1 }
    for (s in 0 until sourceCount) {
      var best = -1
      var bestCost = Float.POSITIVE_INFINITY
      for (t in 0 until targetCount) {
        if (taken[t]) continue
        val c = cost(s, t)
        if (c < bestCost) {
          bestCost = c
          best = t
        }
      }
      taken[best] = true
      assignment[best] = s
    }
    for (t in 0 until targetCount) {
      if (assignment[t] == -1) {
        var best = 0
        var bestCost = Float.POSITIVE_INFINITY
        for (s in 0 until sourceCount) {
          val c = cost(s, t)
          if (c < bestCost) {
            bestCost = c
            best = s
          }
        }
        assignment[t] = best
      }
    }
    for (t in 0 until targetCount) pairs += intArrayOf(assignment[t], t)
  }
  return pairs
}

/**
 * Il varco minimo con segno fra i pezzi di un piano: positivo = il più vicino dei pezzi è staccato
 * di tanto, negativo = qualcuno si compenetra di tanto.
 *
 * Esiste per la proprietà scomoda dello smooth minimum: smin(a, a, k) = a − k/4. Due campi
 * coincidenti — la fine di ogni fusione — non danno l'unione, danno l'unione GONFIATA di un quarto
 * del raggio di fusione: un alone di materiale oltre il bordo, che a riposo sparisce di colpo.
 * Quindi il raggio di fusione effettivo scala col varco: pieno quando i pezzi sono vicini e
 * staccati (il ponte), spento man mano che si compenetrano (dove l'unione vera basta).
 */
internal fun slabMinGap(rects: FloatArray, count: Int): Float {
  if (count < 2) return Float.POSITIVE_INFINITY
  var minGap = Float.POSITIVE_INFINITY
  for (i in 0 until count) {
    for (j in i + 1 until count) {
      val dx = abs(rects[i * 4] - rects[j * 4]) - (rects[i * 4 + 2] + rects[j * 4 + 2])
      val dy = abs(rects[i * 4 + 1] - rects[j * 4 + 1]) - (rects[i * 4 + 3] + rects[j * 4 + 3])
      // Per rettangoli allineati agli assi: si compenetrano solo se entrambe le componenti sono
      // negative, quindi il varco con segno è la componente maggiore.
      minGap = min(minGap, max(dx, dy))
    }
  }
  return minGap
}

/** Il raggio di fusione effettivo dato il varco: pieno da staccati, spento a compenetrazione [blend]. */
internal fun effectiveBlendRadius(blend: Float, minGap: Float): Float {
  if (blend <= 0f || minGap >= 0f) return blend.coerceAtLeast(0f)
  return (blend + minGap).coerceAtLeast(0f)
}

// --- Specchi del campo di distanza -------------------------------------------
//
// Le stesse formule che stanno in FluidPhysicsShaders.kt, trascritte in Kotlin. I test le
// esercitano qui; lo shader è la copia carbone.

/** SDF di un rettangolo arrotondato centrato in (cx, cy). Negativo dentro. */
internal fun sdRoundedRect(
  x: Float,
  y: Float,
  cx: Float,
  cy: Float,
  halfWidth: Float,
  halfHeight: Float,
  radius: Float,
): Float {
  val qx = abs(x - cx) - (halfWidth - radius)
  val qy = abs(y - cy) - (halfHeight - radius)
  val outside = hypot(max(qx, 0f), max(qy, 0f)) - radius
  val inside = min(max(qx, qy), 0f)
  return outside + inside
}

/** Smooth minimum polinomiale: l'unione liquida di due campi. Mai sopra il minimo vero. */
internal fun smin(a: Float, b: Float, k: Float): Float {
  if (k <= 0f) return min(a, b)
  val h = ((0.5f + 0.5f * (b - a) / k)).coerceIn(0f, 1f)
  return b + (a - b) * h - k * h * (1f - h)
}

/** SDF di un poligono (Inigo Quilez): negativo dentro, per qualsiasi poligono semplice. */
internal fun sdPolygon(x: Float, y: Float, vertices: List<Offset>): Float {
  var d = Float.POSITIVE_INFINITY
  var sign = 1f
  var j = vertices.size - 1
  for (i in vertices.indices) {
    val vi = vertices[i]
    val vj = vertices[j]
    val ex = vj.x - vi.x
    val ey = vj.y - vi.y
    val wx = x - vi.x
    val wy = y - vi.y
    val t = ((wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-12f)).coerceIn(0f, 1f)
    val bx = wx - ex * t
    val by = wy - ey * t
    d = min(d, bx * bx + by * by)
    // Winding: il segno cambia una volta per ogni attraversamento della riga orizzontale.
    val c1 = y >= vi.y
    val c2 = y < vj.y
    val c3 = ex * wy > ey * wx
    if ((c1 && c2 && c3) || (!c1 && !c2 && !c3)) sign = -sign
    j = i
  }
  return sign * sqrt(d)
}

/**
 * Gradiente del campo poligonale: la direzione dal punto più vicino del perimetro, col segno del
 * campo. È quello che lo shader ricava *dentro* lo stesso loop della distanza, senza valutazioni
 * extra del campo.
 */
internal fun sdPolygonGradient(x: Float, y: Float, vertices: List<Offset>): Offset {
  var best = Float.POSITIVE_INFINITY
  var nearestX = 0f
  var nearestY = 0f
  var j = vertices.size - 1
  for (i in vertices.indices) {
    val vi = vertices[i]
    val vj = vertices[j]
    val ex = vj.x - vi.x
    val ey = vj.y - vi.y
    val wx = x - vi.x
    val wy = y - vi.y
    val t = ((wx * ex + wy * ey) / (ex * ex + ey * ey + 1e-12f)).coerceIn(0f, 1f)
    val px = vi.x + ex * t
    val py = vi.y + ey * t
    val dx = x - px
    val dy = y - py
    val d = dx * dx + dy * dy
    if (d < best) {
      best = d
      nearestX = px
      nearestY = py
    }
    j = i
  }
  val dx = x - nearestX
  val dy = y - nearestY
  val length = hypot(dx, dy)
  if (length <= 1e-6f) return Offset.Zero
  val inside = sdPolygon(x, y, vertices) < 0f
  val sign = if (inside) -1f else 1f
  return Offset(sign * dx / length, sign * dy / length)
}

// --- Spazio degli uniform ----------------------------------------------------

/**
 * Porta un valore in pixel di layout nello spazio in cui lo shader lavora davvero.
 *
 * Lo scope degli effetti può essere in scala ridotta ([dev.antigravity.fluidengine.ui.fluid
 * .GlassOptics.backdropResolution], la quantizzazione della qualità): ogni pixel che entra negli
 * uniform DEVE passare di qui, con il fattore `scopeSize / layoutSize`, o la lente si sposta
 * rispetto alla sagoma esattamente quando la superficie prova a risparmiare.
 */
internal fun toScopeSpace(layoutPx: Float, scopeFactor: Float): Float = layoutPx * scopeFactor

/** Il fattore di scala fra il layout e lo scope degli effetti. 1 quando nessuno sta risparmiando. */
internal fun scopeFactor(scopeWidth: Float, layoutWidth: Float): Float =
  if (layoutWidth <= 0f || !scopeWidth.isFinite() || scopeWidth <= 0f) 1f
  else scopeWidth / layoutWidth
