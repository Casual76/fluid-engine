package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite

/**
 * Quello che una [FluidScreen] sa della propria colonna, per chi costruisce la sua lista.
 *
 * Il contenuto di una pagina e' un `LazyListScope`, cioe' non e' componibile e non puo' chiedere a
 * nessuno quanto e' largo. Questo e' il ponte: la schermata ci scrive la larghezza della colonna
 * nel momento in cui la misura, e la lista la legge come uno stato — quindi quando la finestra
 * cambia (rotazione, finestra divisa) la lista si ricostruisce da sola con le colonne giuste.
 *
 * Senza una schermata che lo aggiorna, [contentWidth] resta non specificato e ogni domanda sulle
 * colonne risponde **una**: la pagina di un telefono, che e' sempre una risposta corretta.
 */
@Stable
class FluidScreenMetrics internal constructor() {
  /** La larghezza della colonna di contenuto, margini esclusi; non specificata finche' non misurata. */
  var contentWidth: Dp by mutableStateOf(Dp.Unspecified)
    internal set

  /** Quante colonne stanno nella colonna di contenuto, larghe almeno [minColumn]. */
  fun columns(
    minColumn: Dp = FluidColumnsDefaults.MinColumn,
    spacing: Dp = FluidColumnsDefaults.Spacing,
    maxColumns: Int = FluidColumnsDefaults.MaxColumns,
  ): Int = fluidContentColumns(contentWidth, minColumn, spacing, maxColumns)
}

@Composable
fun rememberFluidScreenMetrics(): FluidScreenMetrics = remember { FluidScreenMetrics() }

object FluidColumnsDefaults {
  /**
   * La colonna piu' stretta che regge una sezione di pagina: un gruppo lista con icona, titolo su
   * due righe e un valore a destra. Sotto questa misura le righe vanno a capo a ogni parola.
   */
  val MinColumn: Dp = 340.dp

  /** Lo spazio fra due colonne: quello fra due sezioni della stessa pagina, un po' piu' largo. */
  val Spacing: Dp = 20.dp

  /** Oltre tre colonne una pagina smette di essere una pagina e diventa un pannello di controllo. */
  const val MaxColumns: Int = 3

  /**
   * La misura di una pagina che si dispone in colonne.
   *
   * [FluidScreenDefaults.ContentMaxWidth] e' la misura di *lettura*: una colonna di testo. Una
   * pagina fatta di sezioni affiancate non e' una riga di testo lunga, e tenerla a 760 dp su un
   * tablet in orizzontale lascia due strisce vuote grandi quanto un telefono ai lati. Questa e' la
   * misura oltre cui anche una pagina a colonne smette di allargarsi: tre colonne comode.
   */
  val WideContentMaxWidth: Dp = 1120.dp
}

/**
 * Quante colonne stanno in [available], larghe almeno [minColumn], con [spacing] fra loro.
 *
 * Mai zero e mai piu' di [maxColumns]; una larghezza non nota o non finita da' una colonna sola.
 */
fun fluidContentColumns(
  available: Dp,
  minColumn: Dp = FluidColumnsDefaults.MinColumn,
  spacing: Dp = FluidColumnsDefaults.Spacing,
  maxColumns: Int = FluidColumnsDefaults.MaxColumns,
): Int {
  if (available == Dp.Unspecified || !available.isFinite || available <= 0.dp) return 1
  return ((available + spacing) / (minColumn + spacing)).toInt().coerceIn(1, maxColumns.coerceAtLeast(1))
}

/**
 * Le sezioni di una pagina, in colonne quando c'e' posto e una sotto l'altra quando non c'e'.
 *
 * Con **una** colonna ogni sezione e' un item della lista, esattamente come se fossero state scritte
 * a mano una per una: sul telefono non cambia niente, nemmeno quanto la lista compone in anticipo.
 * Con piu' colonne le sezioni stanno in un item solo e si dispongono a muratura ([FluidMasonry]):
 * ognuna va nella colonna piu' corta, nell'ordine in cui sono scritte, cosi' la pagina si riempie
 * in modo uniforme e l'ordine di lettura — da sinistra a destra, dall'alto in basso — resta quello
 * scritto.
 *
 * Fatto per le pagine di sezioni brevi (un cruscotto, un indice). Un elenco lungo non va qui dentro:
 * in colonne smette di essere pigro, e in ogni caso un elenco lungo si legge dall'alto in basso.
 */
fun LazyListScope.fluidColumns(
  key: Any,
  columns: Int,
  sections: List<FluidColumnSection>,
  spacing: Dp = FluidColumnsDefaults.Spacing,
  verticalSpacing: Dp = FluidScreenDefaults.ItemSpacing,
) {
  if (sections.isEmpty()) return
  if (columns <= 1) {
    sections.forEach { section ->
      item(key = section.key, contentType = section.contentType) { section.content() }
    }
    return
  }
  item(key = key, contentType = "fluid:columns") {
    FluidMasonry(
      columns = columns,
      horizontalSpacing = spacing,
      verticalSpacing = verticalSpacing,
    ) {
      sections.forEach { section -> section.content() }
    }
  }
}

/** Una sezione per [fluidColumns]: una chiave stabile e il suo contenuto. */
class FluidColumnSection(
  val key: Any,
  val contentType: Any? = null,
  val content: @Composable () -> Unit,
)

/**
 * Figli disposti a muratura: ognuno nella colonna in quel momento piu' corta, a parita' la prima.
 *
 * Non una griglia a righe: sezioni di altezza molto diversa — tre righe accanto a dodici — in una
 * griglia lasciano buchi grandi quanto la differenza, e la pagina sembra finita a meta'.
 */
@Composable
fun FluidMasonry(
  columns: Int,
  modifier: Modifier = Modifier,
  horizontalSpacing: Dp = FluidColumnsDefaults.Spacing,
  verticalSpacing: Dp = FluidScreenDefaults.ItemSpacing,
  content: @Composable () -> Unit,
) {
  Layout(content = content, modifier = modifier) { measurables, constraints ->
    val count = columns.coerceAtLeast(1)
    val hGap = horizontalSpacing.roundToPx()
    val vGap = verticalSpacing.roundToPx()
    val width = constraints.maxWidth
    val columnWidth = ((width - hGap * (count - 1)) / count).coerceAtLeast(0)
    val childConstraints = Constraints(minWidth = columnWidth, maxWidth = columnWidth)
    val heights = IntArray(count)
    val placed = measurables.map { measurable ->
      val placeable = measurable.measure(childConstraints)
      val column = fluidMasonryColumn(heights)
      val y = heights[column]
      heights[column] = y + placeable.height + vGap
      Triple(placeable, column, y)
    }
    val height = ((heights.maxOrNull() ?: 0) - vGap).coerceAtLeast(0)
    layout(width, height.coerceIn(constraints.minHeight, constraints.maxHeight)) {
      placed.forEach { (placeable, column, y) ->
        placeable.placeRelative(column * (columnWidth + hGap), y)
      }
    }
  }
}

/** La colonna in cui va il prossimo figlio: la piu' corta, e a parita' quella piu' a sinistra. */
internal fun fluidMasonryColumn(heights: IntArray): Int {
  var best = 0
  for (index in 1 until heights.size) {
    if (heights[index] < heights[best]) best = index
  }
  return best
}
