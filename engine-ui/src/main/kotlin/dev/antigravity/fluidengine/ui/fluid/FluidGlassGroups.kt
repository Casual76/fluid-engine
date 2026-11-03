package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.ui.theme.FluidGroupOpenEdge
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup

/**
 * Quante righe stanno in un pannello di vetro prima che ne cominci un altro. Vedi [fluidGlassGroups].
 *
 * E' un numero di *composizione* prima ancora che di texture: un pannello e' un item solo della
 * lista pigra, e tutto il suo contenuto viene composto e misurato nel fotogramma in cui entra. A
 * dodici righe multi-riga quel fotogramma costava 50-70 ms, il singhiozzo che si vedeva a ogni
 * pannello nuovo anche su un telefono veloce. Otto tiene il pannello alla scala a cui il materiale
 * si legge e il fotogramma d'ingresso dentro il bilancio.
 */
const val FluidGlassGroupMaxRows = 8

/**
 * Una sezione di righe, dentro una lista pigra, come pannelli di vetro.
 *
 * Portata da ClasseViva Expressive, dove si chiamava allo stesso modo e faceva lo stesso mestiere:
 * sostituisce il `items()` riga per riga, e il motivo e' la regola del design system — **il vetro va
 * sul contenitore, mai sulla riga.** Venti righe erano venti superfici, cioe' venti registrazioni di
 * layer e venti catene di `RenderEffect`, per un effetto che nessuno distingue da una sola.
 *
 * ### Perche' spezza
 *
 * Un pannello di vetro registra un `GraphicsLayer` grande quanto se stesso, e un layer e' una
 * texture della GPU. Un gruppo e' alto quanto le righe che uno si trova ad avere: una pagina di
 * ottantaquattro voti sono sedicimila pixel, e oltre il tetto il pannello arriva **nero**. Non e'
 * un caso limite teorico, e' stata la prima cosa che si e' vista accendendo il vetro sulle liste.
 *
 * Quindi una sezione lunga diventa piu' pannelli. **Ma il taglio non si deve vedere**: cadrebbe ogni
 * otto righe, cioe' a un indice che nei dati non significa niente, e finirebbe fra due voci dello
 * stesso giorno. Un mese resta un pannello solo per l'occhio e diventa piu' pannelli per la GPU.
 * Servono due cose insieme:
 *
 * - i pezzi arrotondano solo gli angoli che sono davvero l'inizio e la fine ([FluidGroupSegment]);
 * - il vuoto fra un pezzo e il successivo si richiude, e dentro una `LazyColumn` c'e' un modo solo:
 *   lo spazio lo mette `Arrangement.spacedBy`, che e' una misura sola per tutta la lista e non si
 *   puo' togliere per un item soltanto, quindi il pezzo **dichiara** un'altezza minore di quella che
 *   disegna, esattamente di quello spazio. Il successivo viene posato a filo. Disegnare oltre i
 *   propri limiti e' legittimo qui: una `LazyColumn` ritaglia il viewport, non i singoli item, ed e'
 *   lo stesso motivo per cui l'ombra di una card puo' uscire da lei.
 *
 * Il prezzo e' `animateItem()`, che funziona solo su un item diretto della lista: le righe non si
 * riordinano piu' con un'animazione propria. Va bene per elenchi che arrivano gia' ordinati, e che
 * nessuno riordina mentre li guarda.
 *
 * Serve dentro [FluidScreen] (o qualunque lista che provveda [LocalFluidItemSpacing]) e un canvas
 * ambientale: senza, `glass = true` ripiega sulla superficie opaca di sempre, e il taglio resta
 * invisibile lo stesso.
 *
 * @param items le righe, nell'ordine in cui compaiono. Vuota: non emette niente.
 * @param key un prefisso per le chiavi dei pannelli (`"$key:0"`, `"$key:1"`...), per una lista che
 *   ne ha piu' di una sezione; null: senza chiave.
 * @param maxRows quante righe per pannello, [FluidGlassGroupMaxRows] di serie.
 * @param row una riga: di solito una [dev.antigravity.fluidengine.ui.theme.FluidListRow]. Il
 *   separatore fra le righe lo mette questa funzione, non va aggiunto.
 */
fun <T> LazyListScope.fluidGlassGroups(
  items: List<T>,
  key: String? = null,
  maxRows: Int = FluidGlassGroupMaxRows,
  row: @Composable (T) -> Unit,
) {
  fluidGlassGroupsIndexed(items = items, key = key, maxRows = maxRows) { _, item -> row(item) }
}

/**
 * [fluidGlassGroups] con la posizione della riga nella lista data (3.0.1), come `itemsIndexed`: per il
 * numero di una classifica, una riga che si comporta diversamente se e' la prima, un'animazione sfalsata.
 * L'indice e' quello in [items], non quello nel pannello: il taglio in pannelli resta invisibile anche qui.
 */
fun <T> LazyListScope.fluidGlassGroupsIndexed(
  items: List<T>,
  key: String? = null,
  maxRows: Int = FluidGlassGroupMaxRows,
  row: @Composable (index: Int, item: T) -> Unit,
) {
  val plan = fluidGlassGroupPlan(items.size, maxRows)
  plan.forEachIndexed { chunk, piece ->
    item(
      key = key?.let { "$it:$chunk" },
      contentType = "fluid-glass-group",
    ) {
      // Lo spazio da riassorbire piu' due volte il ritaglio dei lati aperti: il pezzo che segue
      // deve risalire abbastanza da coprire il proprio ritaglio e quello del pezzo precedente,
      // altrimenti al posto della banda chiara resta una fessura chiara, che non e' un progresso.
      val density = LocalDensity.current
      val gapPx = fluidGlassGroupGapPx(
        spacingPx = with(density) { LocalFluidItemSpacing.current.roundToPx() },
        openEdgePx = with(density) { FluidGroupOpenEdge.roundToPx() },
      )
      FluidListGroup(
        glass = true,
        segment = piece.segment,
        // L'ultimo pezzo e' l'unico che tiene lo spazio dopo di se': li' e' la fine della lista e
        // l'aria ci vuole. Gli altri la riassorbono, cosi' il pezzo dopo atterra a filo.
        modifier = if (!piece.joinToNext) {
          Modifier
        } else {
          Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, fluidGlassGroupJoinedHeight(placeable.height, gapPx)) {
              placeable.place(0, 0)
            }
          }
        },
      ) {
        for (index in piece.from until piece.toExclusive) {
          if (index > piece.from) FluidListDivider()
          row(index, items[index])
        }
        if (piece.joinToNext) {
          // Il separatore della giuntura, che senza sarebbe l'unico confine fra due righe a non
          // averlo. Sta dentro il pannello e **sopra** la fascia ritagliata: appoggiato in fondo
          // verrebbe tagliato via insieme al bordo.
          FluidListDivider()
          Spacer(modifier = Modifier.height(FluidGroupOpenEdge))
        }
      }
    }
  }
}

/**
 * Le stesse righe in un pannello solo, fuori da una lista pigra: per una sezione breve che sta in
 * una colonna ([fluidColumns]) accanto ad altre.
 *
 * Senza spezzare: e' fatto per le sezioni di un cruscotto, che hanno quattro o sei righe. Un elenco
 * lungo resta in [fluidGlassGroups], o oltre il tetto della texture il pannello arriva nero.
 */
@Composable
fun <T> FluidGlassGroup(
  items: List<T>,
  modifier: Modifier = Modifier,
  row: @Composable (T) -> Unit,
) {
  if (items.isEmpty()) return
  FluidListGroup(glass = true, modifier = modifier) {
    items.forEachIndexed { index, value ->
      if (index > 0) FluidListDivider()
      row(value)
    }
  }
}

/**
 * Un pannello di [fluidGlassGroups]: le righe da [from] a [toExclusive] (indici nella lista data),
 * di che pezzo si tratta, e se il vuoto verso il successivo va richiuso.
 */
internal data class FluidGlassGroupPiece(
  val from: Int,
  val toExclusive: Int,
  val segment: FluidGroupSegment,
  val joinToNext: Boolean,
)

/**
 * Come si taglia una lista di [count] righe in pannelli da [maxRows]: pura, perche' e' qui che
 * si sbaglia (un angolo tondo nel mezzo, un'ultima riga che resta sola) e un test non deve
 * montare una lista pigra per vederlo.
 *
 * Una lista che sta in un pannello e' [FluidGroupSegment.Whole]; altrimenti il primo pezzo e'
 * `First`, l'ultimo `Last` e quelli in mezzo `Middle`, e tutti tranne l'ultimo si uniscono al
 * successivo. Una lista vuota non ha pezzi.
 */
internal fun fluidGlassGroupPlan(count: Int, maxRows: Int = FluidGlassGroupMaxRows): List<FluidGlassGroupPiece> {
  require(maxRows >= 1) { "maxRows deve essere almeno 1: era $maxRows" }
  if (count <= 0) return emptyList()
  val pieces = (count + maxRows - 1) / maxRows
  val last = pieces - 1
  return List(pieces) { piece ->
    FluidGlassGroupPiece(
      from = piece * maxRows,
      toExclusive = minOf(count, (piece + 1) * maxRows),
      segment = when {
        last == 0 -> FluidGroupSegment.Whole
        piece == 0 -> FluidGroupSegment.First
        piece == last -> FluidGroupSegment.Last
        else -> FluidGroupSegment.Middle
      },
      joinToNext = piece != last,
    )
  }
}

/** Lo spazio da riassorbire dopo un pezzo che si unisce al successivo: la spaziatura piu' due ritagli. */
internal fun fluidGlassGroupGapPx(spacingPx: Int, openEdgePx: Int): Int = spacingPx + openEdgePx * 2

/** L'altezza che un pezzo unito dichiara: quella che disegna meno [gapPx], mai negativa. */
internal fun fluidGlassGroupJoinedHeight(drawnHeight: Int, gapPx: Int): Int =
  (drawnHeight - gapPx).coerceAtLeast(0)
