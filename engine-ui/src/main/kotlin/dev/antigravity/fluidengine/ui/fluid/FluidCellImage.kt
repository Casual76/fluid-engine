package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

// F7.29 · Le celle che entrano in vista senza layer di ritaglio.
//
// In una griglia pigra che scorre, ogni cella che entra ha i suoi layer nuovi: Compose ne crea uno per l'elemento (la
// griglia lo sposta senza ridisegnarlo) e uno per ogni `graphicsLayer` della cella, anche quelli che servono solo a
// ritagliare. `clipToBounds()` e `clip(RectangleShape)` sono un `graphicsLayer(clip = true)`, e `Image` mette sempre il
// suo `clipToBounds()`: la cella di una foto della griglia di F8.4 e del selettore di F8.14 ne aveva tre o quattro (le
// guardie di F7.28, rapporto F7.29). Un rettangolo si ritaglia anche nel disegno, con lo stesso rettangolo nelle stesse
// coordinate: stessi pixel, nessun RenderNode in più da creare, registrare e attraversare a ogni fotogramma.

/**
 * Il ritaglio al riquadro del nodo nel disegno, senza layer: lo stesso rettangolo di `clipToBounds()` (da zero alla
 * taglia del nodo, nelle sue coordinate). Una sola istanza: non alloca niente, né in composizione né nel disegno.
 */
internal val FluidClipToBoundsInDraw: Modifier = Modifier.drawWithContent {
  clipRect { this@drawWithContent.drawContent() }
}

/** La misura di `Image`: la taglia minima dei vincoli (con `fillMaxSize`, il riquadro intero). */
private val CellImageMeasurePolicy = MeasurePolicy { _, constraints ->
  layout(constraints.minWidth, constraints.minHeight) {}
}

/**
 * `Image` di Compose senza il suo layer di ritaglio (F7.29): la stessa catena (`semantics` con la descrizione e il ruolo
 * di immagine, il ritaglio al riquadro, `paint`), la stessa misura, gli stessi pixel; il ritaglio è nel disegno
 * ([FluidClipToBoundsInDraw]) invece che in un `graphicsLayer(clip = true)`. Per le immagini delle celle che entrano in
 * vista scorrendo ([FluidAsyncImage]).
 */
@Composable
internal fun FluidCellImage(
  painter: Painter,
  contentDescription: String?,
  contentScale: ContentScale,
  modifier: Modifier = Modifier,
  alignment: Alignment = Alignment.Center,
) {
  val semantics = if (contentDescription != null) {
    remember(contentDescription) {
      Modifier.semantics {
        this.contentDescription = contentDescription
        this.role = Role.Image
      }
    }
  } else {
    Modifier
  }
  Layout(
    modifier = modifier
      .then(semantics)
      .then(FluidClipToBoundsInDraw)
      .paint(painter, alignment = alignment, contentScale = contentScale),
    measurePolicy = CellImageMeasurePolicy,
  )
}
