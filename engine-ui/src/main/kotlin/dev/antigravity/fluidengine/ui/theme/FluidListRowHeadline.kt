package dev.antigravity.fluidengine.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * La quota della riga che le etichette accanto al titolo possono prendere, oltre la quale vanno sotto.
 *
 * Meno della meta': un titolo che scorre in quello che resta — sei decimi — va a capo fra le parole.
 * Con la meta' e una scala del testo grande si tornava alle parole spezzate a meta'.
 */
internal const val FluidRowLabelsMaxShare = 0.4f

/** Dove stanno le etichette di una [FluidListRow] rispetto al titolo. */
internal enum class FluidRowLabelPlacement { Inline, Below }

/**
 * Accanto al titolo o sotto: la decisione, separata dal disegno perche' si possa provare.
 *
 * Accanto quando il titolo ci sta intero su una riga nello spazio che resta, o quando le etichette
 * prendono al massimo [FluidRowLabelsMaxShare] della larghezza. Altrimenti sotto: e' meglio una
 * riga in piu' che un titolo stretto in una colonna di sillabe.
 */
internal fun fluidRowLabelPlacement(
  availableWidth: Int,
  titleWidth: Int,
  labelsWidth: Int,
  gap: Int,
): FluidRowLabelPlacement {
  if (labelsWidth <= 0) return FluidRowLabelPlacement.Inline
  val remaining = availableWidth - labelsWidth - gap
  if (remaining <= 0) return FluidRowLabelPlacement.Below
  if (titleWidth <= remaining) return FluidRowLabelPlacement.Inline
  return if (labelsWidth + gap <= availableWidth * FluidRowLabelsMaxShare) {
    FluidRowLabelPlacement.Inline
  } else {
    FluidRowLabelPlacement.Below
  }
}

/**
 * Il titolo di una riga con le sue etichette di stato.
 *
 * Le etichette non stanno nello slot a destra di `ListItem`: Material misura quello per primo, a
 * larghezza piena, e il titolo prende quello che avanza. Due pillole e la freccia bastavano per
 * lasciare al titolo una colonna di quattro lettere, e il testo andava a capo dentro le parole.
 * Qui si misurano insieme e si sceglie ([fluidRowLabelPlacement]): accanto, allineate alla prima
 * riga del titolo, oppure sotto, e il titolo tiene tutta la larghezza.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FluidRowHeadline(
  title: AnnotatedString,
  maxLines: Int,
  labels: (@Composable () -> Unit)?,
) {
  val titleText: @Composable () -> Unit = {
    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold,
      maxLines = maxLines,
      overflow = TextOverflow.Ellipsis,
    )
  }
  if (labels == null) {
    titleText()
    return
  }
  Layout(
    contents = listOf(
      titleText,
      {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          itemVerticalAlignment = Alignment.CenterVertically,
        ) { labels() }
      },
    ),
  ) { (titleMeasurables, labelMeasurables), constraints ->
    val gap = 8.dp.roundToPx()
    val below = 6.dp.roundToPx()
    val available = if (constraints.hasBoundedWidth) constraints.maxWidth else Constraints.Infinity
    val labelsPlaceable = labelMeasurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
    val titleMeasurable = titleMeasurables.first()
    val placement = if (available == Constraints.Infinity) {
      FluidRowLabelPlacement.Inline
    } else {
      fluidRowLabelPlacement(
        availableWidth = available,
        titleWidth = titleMeasurable.maxIntrinsicWidth(constraints.maxHeight),
        labelsWidth = labelsPlaceable.width,
        gap = gap,
      )
    }
    when (placement) {
      FluidRowLabelPlacement.Inline -> {
        val titleMax = if (available == Constraints.Infinity) {
          Constraints.Infinity
        } else {
          (available - labelsPlaceable.width - gap).coerceAtLeast(0)
        }
        val titlePlaceable = titleMeasurable.measure(Constraints(maxWidth = titleMax))
        // Allineate alla prima riga del titolo per la linea di base, non centrate sul blocco: su un
        // titolo di tre righe una pillola a meta' altezza non dice piu' di quale riga parla.
        val titleBaseline = titlePlaceable[FirstBaseline]
        val labelsBaseline = labelsPlaceable[FirstBaseline]
        val offset = if (titleBaseline != AlignmentLine.Unspecified &&
          labelsBaseline != AlignmentLine.Unspecified
        ) {
          titleBaseline - labelsBaseline
        } else {
          0
        }
        val titleY = if (offset < 0) -offset else 0
        val labelsY = if (offset > 0) offset else 0
        val width = if (available == Constraints.Infinity) {
          titlePlaceable.width + gap + labelsPlaceable.width
        } else {
          available
        }
        val height = max(titleY + titlePlaceable.height, labelsY + labelsPlaceable.height)
        layout(width, height) {
          titlePlaceable.placeRelative(0, titleY)
          labelsPlaceable.placeRelative(width - labelsPlaceable.width, labelsY)
        }
      }
      FluidRowLabelPlacement.Below -> {
        val titlePlaceable = titleMeasurable.measure(Constraints(maxWidth = available))
        val width = max(titlePlaceable.width, labelsPlaceable.width)
        layout(width, titlePlaceable.height + below + labelsPlaceable.height) {
          titlePlaceable.placeRelative(0, 0)
          labelsPlaceable.placeRelative(0, titlePlaceable.height + below)
        }
      }
    }
  }
}
