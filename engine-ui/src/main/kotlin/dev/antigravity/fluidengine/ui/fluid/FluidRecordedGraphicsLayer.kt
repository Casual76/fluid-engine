package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

// F7.29b · I layer che un lettore disegna prima che il proprietario li registri.
//
// `drawLayer` di Compose registra di nuovo un layer che non ha ancora una display list (`recreateDisplayListIfNeeded`),
// col blocco che conosce: per un layer mai registrato, il blocco di serie, che non disegna niente. Il menu contestuale
// (la riga sollevata che l'overlay disegna nel fotogramma del distacco), la card che si espande (l'origine che il pop-up
// disegna nel fotogramma dell'apertura) e il materiale esportato di un pannello (che un vetro annidato campiona nel
// fotogramma dell'entrata) venivano così registrati due volte nello stesso fotogramma: vuoti dal lettore, veri dal
// proprietario (D2, D3, D4 del rapporto F7.28). Un layer che nasce con una display list vuota non si registra più dal
// lettore: il lettore disegna il RenderNode, il proprietario lo registra una volta, e alla sincronizzazione il contenuto
// è quello vero come prima. Stessi pixel.

/** Una densità qualunque: la registrazione vuota non disegna niente e non misura niente. */
private val EmptyRecordingDensity = Density(1f)

/**
 * Registra il layer vuoto, a taglia zero: da qui il layer ha una display list e `drawLayer` non lo registra più al posto
 * del proprietario. La taglia resta zero, come per un layer mai registrato (chi la legge per sapere se il proprietario
 * l'ha già registrato vede quello di prima). Fuori dal disegno: in composizione, quando il layer nasce.
 */
internal fun GraphicsLayer.recordEmpty(): GraphicsLayer = apply {
  record(EmptyRecordingDensity, LayoutDirection.Ltr, IntSize.Zero) {}
}

/** [rememberGraphicsLayer] con la display list vuota già registrata ([recordEmpty]), una volta per layer. */
@Composable
internal fun rememberRecordedGraphicsLayer(): GraphicsLayer {
  val layer = rememberGraphicsLayer()
  remember(layer) { layer.recordEmpty() }
  return layer
}
