package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * F6B.8b · I bersagli da 48 dp senza cambiare l'aspetto.
 *
 * Il vetro e le icone restano le misure di Apple (la lente da 46, i comandi del campo da 40, «Cancella» da 28, le
 * pillole del vassoio da 30): cresce soltanto **il bersaglio**, attorno al disegno, fino a 48 × 48 dp (la regola di
 * Android per il tocco). È la strada del tasto diviso di F6B.11 ([fluidSplitTargets]): dove due bersagli si
 * toccherebbero, lo spazio in mezzo si divide e nessuno entra nel vicino, e il bersaglio esce dal disegno verso il lato
 * libero.
 */

/** Il bersaglio minimo: 48 dp, la regola di Android per il tocco ([FluidMetrics.TouchTarget]). */
internal val FluidTargetMin: Dp get() = FluidMetrics.TouchTarget

/**
 * Quanto il bersaglio di un controllo esce dal posto del suo disegno: [start] e [end] in orizzontale (nel verso del
 * testo), [vertical] sopra e sotto. Un lato può anche essere negativo: il bersaglio lascia al vicino un pezzo del posto
 * (lo spazio fra due comandi vicini si divide). Il genitore vede la misura del posto, il tocco e TalkBack quella del
 * bersaglio.
 */
@Immutable
internal data class FluidTargetOutset(
  val start: Dp = 0.dp,
  val end: Dp = 0.dp,
  val vertical: Dp = 0.dp,
  val top: Dp = vertical,
  val bottom: Dp = vertical,
) {
  val isZero: Boolean get() = start == 0.dp && end == 0.dp && top == 0.dp && bottom == 0.dp

  companion object {
    val None = FluidTargetOutset()

    /** Il bersaglio di un disegno largo [width] e alto [height] che cresce fino a [min] in modo simmetrico. */
    fun around(width: Dp, height: Dp, min: Dp = FluidTargetMin): FluidTargetOutset {
      val h = ((min - width) / 2).coerceAtLeast(0.dp)
      return FluidTargetOutset(h, h, ((min - height) / 2).coerceAtLeast(0.dp))
    }
  }
}

/**
 * Il bersaglio che esce dal disegno per chi lo chiede (F6B.8b): il campo della barra lo dà a ogni suo comando
 * ([FluidCapsuleAction] lo legge), calcolato da [fluidDisjointTargets] perché due comandi vicini non si tocchino. Null: il
 * bersaglio è il disegno, come prima.
 */
internal val LocalFluidTargetOutset = compositionLocalOf<FluidTargetOutset?> { null }

/**
 * Posa il nodo che segue più grande del suo posto (F6B.8b): misura il resto della catena largo [outset] in più (e alto il
 * doppio di [FluidTargetOutset.vertical] in più) e lo posa spostato, mentre al genitore dice la misura del posto. Va **in
 * testa** alla catena del controllo, prima della misura, della pressione e della semantica, che così vedono il bersaglio;
 * dopo di loro [fluidTargetInner] rimette il disegno al suo posto. Il genitore non deve ritagliare con un livello
 * (`clip`): un livello che ritaglia respinge il tocco fuori dalla sua sagoma (vedi [fluidDrawClip]).
 */
internal fun Modifier.fluidTargetOutset(outset: FluidTargetOutset?): Modifier {
  if (outset == null || outset.isZero) return this
  return layout { measurable, constraints ->
    val s = outset.start.roundToPx()
    val e = outset.end.roundToPx()
    val t = outset.top.roundToPx()
    val b = outset.bottom.roundToPx()
    val inner = measurable.measure(
      Constraints(
        minWidth = 0,
        maxWidth = if (constraints.hasBoundedWidth) (constraints.maxWidth + s + e).coerceAtLeast(0) else Constraints.Infinity,
        minHeight = 0,
        maxHeight = if (constraints.hasBoundedHeight) (constraints.maxHeight + t + b).coerceAtLeast(0) else Constraints.Infinity,
      ),
    )
    val width = (inner.width - s - e).coerceAtLeast(0)
    val height = (inner.height - t - b).coerceAtLeast(0)
    layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
      inner.placeRelative(-s, -t)
    }
  }
}

/**
 * Il disegno al suo posto dentro il bersaglio di [fluidTargetOutset] (F6B.8b): misura il resto largo quanto il posto (il
 * bersaglio meno [outset]) e lo posa dove stava. Va dopo la pressione e la semantica, prima del disegno.
 */
internal fun Modifier.fluidTargetInner(outset: FluidTargetOutset?): Modifier {
  if (outset == null || outset.isZero) return this
  return layout { measurable, constraints ->
    val s = outset.start.roundToPx()
    val e = outset.end.roundToPx()
    val t = outset.top.roundToPx()
    val b = outset.bottom.roundToPx()
    val w = if (constraints.hasBoundedWidth) (constraints.maxWidth - s - e).coerceAtLeast(0) else Constraints.Infinity
    val h = if (constraints.hasBoundedHeight) (constraints.maxHeight - t - b).coerceAtLeast(0) else Constraints.Infinity
    val child = measurable.measure(Constraints(
      minWidth = if (w == Constraints.Infinity) 0 else (constraints.minWidth - s - e).coerceIn(0, w),
      maxWidth = w,
      minHeight = if (h == Constraints.Infinity) 0 else (constraints.minHeight - t - b).coerceIn(0, h),
      maxHeight = h,
    ))
    layout(constraints.constrainWidth(child.width + s + e), constraints.constrainHeight(child.height + t + b)) {
      child.placeRelative(s, t)
    }
  }
}

/**
 * Il ritaglio di [shape] **solo sul disegno** (F6B.8b): come `clip(shape)` per ciò che si vede, ma senza un livello che
 * ritaglia, quindi senza respingere il tocco fuori dalla sagoma. Serve al vetro del campo, i cui comandi da 40 dp hanno
 * il bersaglio da 48 che esce di 4 dp sopra e sotto il vetro da 40.
 */
internal fun Modifier.fluidDrawClip(shape: Shape): Modifier = drawWithCache {
  val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
  onDrawWithContent { clipPath(path) { this@onDrawWithContent.drawContent() } }
}

/**
 * I bersagli disgiunti di una fila di controlli (F6B.8b). Pura.
 *
 * [glyphs] sono i disegni in fila (gli intervalli in dp, in ordine, senza sovrapporsi: l'icona da 22 dp di un comando da
 * 40, il disco da 30 di «Invia», il cerchio da 17 di «Cancella»); [lower] e [upper] dove i bersagli possono arrivare
 * (il testo prima, il margine dello schermo o il vicino dopo). Ogni bersaglio è largo almeno [min] e **contiene il suo
 * disegno**; due bersagli non si toccano. Il bersaglio sta centrato sul disegno quando c'è posto; se due si
 * toccherebbero si spostano il meno possibile, verso il lato libero (la regressione isotonica: ogni bersaglio resta il
 * più vicino possibile al suo centro). Se nemmeno così ci stanno, lo spazio fra due disegni si divide a metà e il
 * bersaglio resta più stretto di [min].
 */
internal fun fluidDisjointTargets(
  glyphs: List<ClosedFloatingPointRange<Float>>,
  min: Float,
  lower: Float,
  upper: Float,
): List<ClosedFloatingPointRange<Float>> {
  val n = glyphs.size
  if (n == 0) return emptyList()
  val widths = FloatArray(n) { maxOf(min, glyphs[it].endInclusive - glyphs[it].start) }
  val before = FloatArray(n)
  for (i in 1 until n) before[i] = before[i - 1] + widths[i - 1]
  val total = before[n - 1] + widths[n - 1]
  if (total <= upper - lower + 0.001f) {
    // z_i = p_i - before_i non decrescente (i bersagli in fila senza toccarsi): la regressione isotonica dei centri.
    val ideal = DoubleArray(n) { i -> ((glyphs[i].start + glyphs[i].endInclusive) / 2f - widths[i] / 2f - before[i]).toDouble() }
    val z = isotonic(ideal)
    val solved = List(n) { i ->
      val p = z[i].toFloat().coerceIn(lower, upper - total) + before[i]
      p..(p + widths[i])
    }
    val contains = solved.indices.all { i -> solved[i].start <= glyphs[i].start + 0.001f && solved[i].endInclusive >= glyphs[i].endInclusive - 0.001f }
    if (contains) return solved
  }
  // Non ci stanno: lo spazio fra due disegni a metà, e alle due estremità fino a [min] verso il lato libero.
  return List(n) { i ->
    val g = glyphs[i]
    val left = if (i == 0) null else (glyphs[i - 1].endInclusive + g.start) / 2f
    val right = if (i == n - 1) null else (g.endInclusive + glyphs[i + 1].start) / 2f
    val r = right ?: minOf(upper, maxOf(g.endInclusive, (left ?: lower) + min))
    val l = left ?: maxOf(lower, minOf(g.start, r - min))
    l..r
  }
}

/** La regressione isotonica (non decrescente) a pesi uguali: pool adjacent violators. */
private fun isotonic(values: DoubleArray): DoubleArray {
  val n = values.size
  val means = DoubleArray(n)
  val counts = IntArray(n)
  var blocks = 0
  for (v in values) {
    means[blocks] = v
    counts[blocks] = 1
    blocks++
    while (blocks > 1 && means[blocks - 2] > means[blocks - 1]) {
      val c = counts[blocks - 2] + counts[blocks - 1]
      means[blocks - 2] = (means[blocks - 2] * counts[blocks - 2] + means[blocks - 1] * counts[blocks - 1]) / c
      counts[blocks - 2] = c
      blocks--
    }
  }
  val out = DoubleArray(n)
  var k = 0
  for (b in 0 until blocks) repeat(counts[b]) { out[k++] = means[b] }
  return out
}

/**
 * Quanto un bersaglio [target] esce dal posto [cell] del suo controllo (F6B.8b), in dp: a sinistra e a destra (negativo
 * se lascia al vicino un pezzo del posto) e in verticale fino a [min] attorno all'altezza [height].
 */
internal fun fluidTargetOutsetOf(
  cell: ClosedFloatingPointRange<Float>,
  target: ClosedFloatingPointRange<Float>,
  height: Float,
  min: Float = FluidTargetMin.value,
): FluidTargetOutset = FluidTargetOutset(
  start = (cell.start - target.start).dp,
  end = (target.endInclusive - cell.endInclusive).dp,
  vertical = ((min - height) / 2f).coerceAtLeast(0f).dp,
)
