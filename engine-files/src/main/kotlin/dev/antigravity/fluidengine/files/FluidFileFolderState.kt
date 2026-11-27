package dev.antigravity.fluidengine.files

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Come si mostra una cartella: **Icone** (la griglia con le miniature vere) o **Elenco**, i due modi di File di iOS 26
 * («Colonne» è dell'iPad e non c'è). Non è un `enum` (regola 3.11).
 */
@Immutable
@JvmInline
value class FluidFileLayout private constructor(private val id: Int) {
  override fun toString(): String = if (id == ListId) "List" else "Grid"

  companion object {
    private const val GridId = 0
    private const val ListId = 1

    val Grid: FluidFileLayout = FluidFileLayout(GridId)
    val List: FluidFileLayout = FluidFileLayout(ListId)
  }
}

/**
 * **Una cartella aperta**: gli elementi arrivati finora dalla sua [source], pagina dopo pagina, l'ordine, la ricerca,
 * l'errore con «Riprova». Lo tiene [FluidFileBrowserState], così tornando indietro la cartella è ancora lì, allo
 * stesso punto ([listState]).
 *
 * - **A pagine**: la prima pagina all'apertura ([start]), le altre quando la lista si avvicina alla fine ([loadMore]).
 *   [complete] è vero quando la sorgente non ne ha altre.
 * - **L'ordine** ([sortBy]): una sorgente che ordina da sé si rilegge dall'inizio; le altre si riordinano in memoria.
 * - **La ricerca** ([search]): una sorgente che cerca da sé riceve le parole; per le altre si legge tutta la cartella e
 *   si filtrano i nomi, senza badare a maiuscole e accenti. [items] è ciò che si vede, [files] solo i file (per il
 *   visore, nello stesso ordine).
 * - **Lenta e fallibile**: [loading] mentre aspetta; [failure] se la sorgente ha lanciato (le righe già arrivate
 *   restano), e [retry] riprova dal punto in cui si era fermata.
 */
@Stable
class FluidFileFolderState internal constructor(
  val source: FluidFileSource,
  val folder: String?,
  val name: String,
  private val scope: CoroutineScope,
  initialSort: FluidFileSort,
  private val locale: Locale = Locale.getDefault(),
) {
  var sort: FluidFileSort by mutableStateOf(initialSort)
    private set
  var query: String by mutableStateOf("")
    private set
  var loading: Boolean by mutableStateOf(false)
    private set
  var failure: Throwable? by mutableStateOf(null)
    private set
  var complete: Boolean by mutableStateOf(false)
    private set

  /** Il punto della lista, che resta quando si torna alla cartella. */
  val listState: LazyListState = LazyListState()

  private var loaded by mutableStateOf<List<FluidFileItem>>(emptyList())
  private var next: String? = null
  private var job: Job? = null
  private var generation = 0
  private var wantsAll = false

  /** Gli elementi che si vedono: nell'ordine scelto e, se c'è una ricerca, solo quelli che rispondono. */
  val items: List<FluidFileItem> by derivedStateOf {
    val ordered = if (source.sorts) loaded else loaded.sortedWith(sort.comparator(locale))
    val words = fluidFileSearchWords(query, locale)
    if (words.isEmpty() || source.searches) ordered else ordered.filter { fluidFileMatches(it, words, locale) }
  }

  /** Solo i file di [items], nello stesso ordine: le pagine del visore. */
  val files: List<FluidFileItem> by derivedStateOf { items.filter { !it.isFolder && it.uri != null } }

  /** Quanti elementi sono arrivati dalla sorgente (prima della ricerca). */
  val loadedCount: Int get() = loaded.size

  /** Apre la cartella: la prima pagina, se non c'è ancora niente. */
  fun start() {
    if (loaded.isEmpty() && !loading && !complete && failure == null) loadMore()
  }

  /** La pagina dopo, se c'è e se non se ne sta già leggendo una. Dopo un errore serve [retry]. */
  fun loadMore() {
    if (loading || complete || failure != null) return
    val token = generation
    loading = true
    job = scope.launch {
      try {
        val page = source.list(FluidFileQuery(folder, sort, next, FluidFileQuery.DefaultLimit,
          query.takeIf { source.searches && it.isNotBlank() }))
        if (token != generation) return@launch
        loaded = loaded + page.items
        next = page.next
        complete = page.next == null
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (error: Exception) {
        if (token == generation) failure = error
      } finally {
        if (token == generation) loading = false
      }
      if (token == generation && wantsAll && !complete && failure == null) loadMore()
    }
  }

  /** Riprova dopo un errore, dal punto in cui ci si era fermati. */
  fun retry() {
    failure = null
    loadMore()
  }

  /** Rilegge la cartella dall'inizio (la sorgente ha avvisato che è cambiata, o è cambiato l'ordine). */
  fun refresh() {
    generation++
    job?.cancel()
    loading = false
    failure = null
    complete = false
    next = null
    loaded = emptyList()
    // R2: tutta la cartella solo se c'è una ricerca da fare in memoria; dopo una ricerca si torna alle pagine.
    wantsAll = needsAll()
    loadMore()
  }

  /** Cambia l'ordine. Lo stesso ordine non fa niente. */
  fun sortBy(sort: FluidFileSort) {
    if (sort == this.sort) return
    this.sort = sort
    if (source.sorts) refresh()
  }

  /**
   * Cerca [query] nella cartella. Una sorgente che cerca da sé si rilegge con le parole; per le altre si legge il resto
   * della cartella (per trovare anche ciò che non è ancora arrivato) e si filtra in memoria.
   */
  fun search(query: String) {
    if (query == this.query) return
    this.query = query
    wantsAll = needsAll()
    if (source.searches) {
      refresh()
    } else if (wantsAll) {
      loadMore()
    }
  }

  /** Per cercare in memoria serve tutta la cartella: solo con una ricerca, e solo se la sorgente non cerca da sé. */
  private fun needsAll(): Boolean = query.isNotBlank() && !source.searches

  /** La lista è a [index] di [items]: se manca poco alla fine, chiede la pagina dopo. */
  fun nearEnd(index: Int) {
    if (index >= items.size - PrefetchDistance) loadMore()
  }

  private companion object {
    /** A quanti elementi dalla fine si chiede la pagina dopo: 40, a occhio (25 novembre 2026). */
    const val PrefetchDistance = 40
  }
}
