package dev.antigravity.fluidengine.wear.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Una schermata a elenco per l'orologio: una testata, le righe, la ghiera che le scorre, l'indicatore
 * sul bordo destro e l'ora che si nasconde mentre si scorre — il modo di fare di Wear (la libreria di
 * Spotify) in una chiamata sola.
 *
 * **Ogni elemento prende la forma dello schermo tondo** mentre esce: piu' stretto, piu' piccolo e piu'
 * sbiadito man mano che si avvicina alla cima o al fondo del cerchio, cosi' una riga non e' mai
 * tagliata dalla ghiera. Chi scrive `item {}` non se ne accorge: lo scope lo aggiunge.
 *
 * Lo sfondo e' **opaco** di proposito: una schermata aperta sopra un'altra deve coprire quella di
 * sotto mentre scivola dentro, e farla vedere solo dove lo scorrimento del dito la scopre — non lasciare
 * che un elenco e una copertina si leggano l'uno attraverso l'altro per tutta la transizione. E' la
 * stessa regola delle pagine del telefono.
 *
 * Nasce da `WatchList` di Fluidify, dove stava con i quattro stati degli elenchi che si riempiono da un
 * telefono lontano (vedi [noticeItem], [loadingItem], [failedItem], [staleItem] e [statusItems]).
 */
@Composable
fun FluidWearList(
  title: String?,
  modifier: Modifier = Modifier,
  content: FluidWearListScope.() -> Unit,
) {
  val state = rememberTransformingLazyColumnState()
  val spec = rememberTransformationSpec()
  ScreenScaffold(scrollState = state, modifier = modifier.background(MaterialTheme.colorScheme.background)) { padding ->
    TransformingLazyColumn(state = state, contentPadding = padding) {
      val scope = FluidWearListScope(this, spec)
      if (title != null) {
        scope.item {
          ListHeader { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        }
      }
      scope.content()
    }
  }
}

/**
 * Lo scope della colonna con ogni elemento dentro la trasformazione dello schermo tondo: la parte
 * del contenitore (larghezza e posizione) fuori, quella del contenuto (scala e dissolvenza) dentro,
 * come annidano i pulsanti di Wear.
 */
class FluidWearListScope internal constructor(
  private val inner: TransformingLazyColumnScope,
  private val spec: TransformationSpec,
) {
  fun item(key: Any? = null, content: @Composable TransformingLazyColumnItemScope.() -> Unit) {
    inner.item(key) { Transformed(spec) { content() } }
  }

  fun <T> items(
    items: List<T>,
    key: ((item: T) -> Any)? = null,
    itemContent: @Composable TransformingLazyColumnItemScope.(item: T) -> Unit,
  ) {
    inner.items(
      count = items.size,
      key = key?.let { k -> { index: Int -> k(items[index]) } },
    ) { index -> Transformed(spec) { itemContent(items[index]) } }
  }
}

@Composable
private fun TransformingLazyColumnItemScope.Transformed(
  spec: TransformationSpec,
  content: @Composable TransformingLazyColumnItemScope.() -> Unit,
) {
  Box(
    Modifier
      .transformedHeight(this, spec)
      .graphicsLayer { with(spec) { applyContainerTransformation(scrollProgress) } },
  ) {
    Box(Modifier.graphicsLayer { with(spec) { applyContentTransformation(scrollProgress) } }) {
      content()
    }
  }
}

/** Una riga di testo quieto in un elenco: sta caricando, e' vuoto, o dice perche' manca qualcosa. */
fun FluidWearListScope.noticeItem(text: String) {
  item {
    Box(
      Modifier.fillMaxWidth().padding(vertical = NoticeVertical, horizontal = NoticeHorizontal),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** Una riga di testo quieto con un cerchio che gira accanto: qualcosa sta arrivando e non c'e' ancora niente da mostrare. */
fun FluidWearListScope.loadingItem(text: String) {
  item {
    Row(
      modifier = Modifier.fillMaxWidth().padding(vertical = NoticeVertical, horizontal = NoticeHorizontal),
      horizontalArrangement = Arrangement.spacedBy(NoticeGap, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      CircularProgressIndicator(modifier = Modifier.size(LoadingGlyph))
      Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

/** Perche' non e' arrivato niente, e un modo di chiedere di nuovo. */
fun FluidWearListScope.failedItem(text: String, retryText: String, onRetry: () -> Unit) {
  noticeItem(text)
  item { RetryRow(title = retryText, subtitle = null, onRetry = onRetry) }
}

/**
 * Un avviso sopra un elenco che mostra quello che l'orologio si e' tenuto, perche' chiedere di nuovo al
 * telefono non e' riuscito: i dati ci sono, sono solo vecchi, e **nasconderli dietro un errore** sarebbe
 * peggio che mostrarli datati.
 */
fun FluidWearListScope.staleItem(text: String, retryText: String, onRetry: () -> Unit) {
  item { RetryRow(title = text, subtitle = retryText, onRetry = onRetry) }
}

@Composable
private fun RetryRow(title: String, subtitle: String?, onRetry: () -> Unit) {
  FluidWearListRow(
    title = title,
    subtitle = subtitle,
    onClick = onRetry,
    leading = {
      Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(RetryGlyph))
    },
  )
}

// ---------------------------------------------------------------------------------------------
// Lo stato di un elenco che si riempie da lontano
// ---------------------------------------------------------------------------------------------

/**
 * Cosa sta succedendo a un elenco che l'orologio riempie chiedendolo al telefono.
 *
 * Quattro stati e non una `Boolean` e un `String?`, perche' sono quattro storie che chi guarda legge
 * in modo diverso, e perche' **la stessa condizione vuol dire due cose** secondo se c'e' qualcosa da
 * mostrare: un errore su un elenco vuoto e' "non e' arrivato niente" (e un modo di riprovare), su un
 * elenco pieno e' "ti mostro quello che avevo" ([FluidWearListPart.Stale]). [fluidWearListPlan] e'
 * quella regola, pura.
 */
@Immutable
sealed interface FluidWearListStatus {
  /** Niente da dire: l'elenco e' quello che e'. */
  data object Ready : FluidWearListStatus

  /** Sta arrivando. Con elementi gia' in mano non li copre: la riga sta in coda. */
  data class Loading(val text: String) : FluidWearListStatus

  /** Una frase che spiega perche' non c'e' niente (non e' un errore: vuoto, o non ancora). */
  data class Notice(val text: String) : FluidWearListStatus

  /** Chiedere non e' riuscito. [onRetry] riprova. */
  data class Failed(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListStatus
}

/** Un pezzo dell'elenco, nell'ordine in cui si disegna. */
@Immutable
sealed interface FluidWearListPart {
  /** Una frase quieta. */
  data class Notice(val text: String) : FluidWearListPart

  /** Una frase con un cerchio che gira. */
  data class Loading(val text: String) : FluidWearListPart

  /** Un errore senza niente sotto: la frase e il tasto per riprovare. */
  data class Failed(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListPart

  /** Un errore sopra gli elementi che l'orologio si era tenuto. */
  data class Stale(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListPart

  /** Gli elementi. */
  data object Items : FluidWearListPart
}

/**
 * Cosa disegnare, e in che ordine, per [status] con [itemCount] elementi in mano.
 *
 *  * **pronto**: gli elementi, e basta;
 *  * **in arrivo**: senza elementi la riga che dice che sta arrivando; con elementi, **dopo** di loro, e
 *    mai al posto loro (chi ha gia' qualcosa da leggere lo legge mentre il resto arriva);
 *  * **avviso**: sopra gli elementi, se ce ne sono, altrimenti da solo;
 *  * **fallito**: senza elementi la frase e il tasto per riprovare; con elementi un avviso `Stale` in
 *    cima, e gli elementi sotto: i dati vecchi valgono piu' di un errore.
 */
fun fluidWearListPlan(status: FluidWearListStatus, itemCount: Int): List<FluidWearListPart> {
  val hasItems = itemCount > 0
  return when (status) {
    FluidWearListStatus.Ready -> if (hasItems) listOf(FluidWearListPart.Items) else emptyList()
    is FluidWearListStatus.Loading ->
      if (hasItems) listOf(FluidWearListPart.Items, FluidWearListPart.Loading(status.text))
      else listOf(FluidWearListPart.Loading(status.text))
    is FluidWearListStatus.Notice ->
      if (hasItems) listOf(FluidWearListPart.Notice(status.text), FluidWearListPart.Items)
      else listOf(FluidWearListPart.Notice(status.text))
    is FluidWearListStatus.Failed ->
      if (hasItems) {
        listOf(FluidWearListPart.Stale(status.text, status.retryText, status.onRetry), FluidWearListPart.Items)
      } else {
        listOf(FluidWearListPart.Failed(status.text, status.retryText, status.onRetry))
      }
  }
}

/**
 * Disegna [items] con i loro stati, secondo [fluidWearListPlan]: la riga giusta nel posto giusto, senza
 * che ogni schermata riscriva il `when`.
 */
fun <T> FluidWearListScope.statusItems(
  status: FluidWearListStatus,
  items: List<T>,
  key: ((item: T) -> Any)? = null,
  itemContent: @Composable TransformingLazyColumnItemScope.(item: T) -> Unit,
) {
  fluidWearListPlan(status, items.size).forEach { part ->
    when (part) {
      is FluidWearListPart.Notice -> noticeItem(part.text)
      is FluidWearListPart.Loading -> loadingItem(part.text)
      is FluidWearListPart.Failed -> failedItem(part.text, part.retryText, part.onRetry)
      is FluidWearListPart.Stale -> staleItem(part.text, part.retryText, part.onRetry)
      FluidWearListPart.Items -> items(items, key, itemContent)
    }
  }
}

private val NoticeVertical = 12.dp
private val NoticeHorizontal = 8.dp
private val NoticeGap = 8.dp
private val LoadingGlyph = 18.dp

/** L'icona di riprova: quella di una riga di Wear (24 dp), dentro la piastrella da 40 che la riga riserva. */
private val RetryGlyph = 24.dp
