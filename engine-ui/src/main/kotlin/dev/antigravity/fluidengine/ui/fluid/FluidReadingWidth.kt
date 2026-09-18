package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isFinite

/**
 * Il margine laterale di una pagina, data la sua larghezza.
 *
 * Il minimo e' [horizontalPadding]. Da quando la pagina supera [contentMaxWidth], il margine cresce
 * al posto del contenuto, che resta a quella misura e centrato: una riga di testo oltre gli ottanta
 * caratteri si legge peggio, e un tablet in orizzontale ne fa duecento. Una larghezza non finita —
 * misura non ancora nota, contenitore senza limite — da' il minimo: meglio una pagina da telefono
 * per un fotogramma che un salto.
 *
 * Funzione a se' perche' e' l'unica riga che distingue una pagina da telefono allargata da una
 * pagina da tablet, e vale la pena provarla senza disegnare.
 */
fun fluidScreenPadding(available: Dp, horizontalPadding: Dp, contentMaxWidth: Dp): Dp {
  if (!available.isFinite || !contentMaxWidth.isFinite) return horizontalPadding
  return maxOf(horizontalPadding, (available - contentMaxWidth) / 2)
}

/** Dove sta una colonna di lettura dentro uno spazio: da dove parte e quanto e' larga, in pixel. */
internal data class FluidReadingSpan(val start: Int, val width: Int)

internal fun fluidReadingSpan(available: Int, max: Int): FluidReadingSpan {
  val width = minOf(available, max).coerceAtLeast(0)
  return FluidReadingSpan(start = (available - width) / 2, width = width)
}

/**
 * Tiene questo elemento alla larghezza di lettura, centrato, per chi non passa da [FluidScreen]:
 * un overlay, un pannello, il corpo di una pagina intera.
 *
 * Con `layout {}` e non con `widthIn` piu' `align`, perche' cosi' funziona in qualunque genitore,
 * anche uno che non sa allineare: l'elemento continua a occupare tutta la larghezza che gli viene
 * data, e mette il suo contenuto nel mezzo. Senza un limite di larghezza dal genitore non c'e'
 * niente da centrare, e si misura come prima.
 */
fun Modifier.fluidReadingWidth(max: Dp = FluidScreenDefaults.ContentMaxWidth): Modifier =
  layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth || !max.isFinite) {
      val placeable = measurable.measure(constraints)
      return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
    val span = fluidReadingSpan(available = constraints.maxWidth, max = max.roundToPx())
    val placeable = measurable.measure(
      constraints.copy(minWidth = minOf(constraints.minWidth, span.width), maxWidth = span.width),
    )
    layout(constraints.maxWidth, placeable.height) {
      placeable.placeRelative(span.start + (span.width - placeable.width) / 2, 0)
    }
  }
