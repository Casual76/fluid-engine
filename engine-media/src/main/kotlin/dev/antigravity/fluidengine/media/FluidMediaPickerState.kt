package dev.antigravity.fluidengine.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMode
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSelectionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Gli elementi di una sorgente per una domanda, pagina dopo pagina (F8.14): la griglia chiede [loadMore] quando arriva
 * vicino alla fine. [version] cresce a ogni pagina arrivata: chi disegna si ricompone solo allora.
 */
@Stable
class FluidMediaFeed internal constructor(
  val source: FluidMediaSource,
  val query: FluidMediaQuery,
  private val scope: CoroutineScope,
  private val pageSize: Int,
  private val onItems: (List<FluidMediaItem>) -> Unit,
) {
  /** Gli elementi arrivati, nell'ordine della sorgente. */
  val items: List<FluidMediaItem> get() = loaded
  private val loaded = mutableStateListOf<FluidMediaItem>()

  /** Quante pagine sono arrivate (e quindi quante volte gli elementi sono cambiati). */
  var version: Int by mutableIntStateOf(0)
    private set

  /** Una pagina sta arrivando. */
  var loading: Boolean by mutableStateOf(false)
    private set

  /** Non ci sono altre pagine. */
  var finished: Boolean by mutableStateOf(false)
    private set

  /**
   * L'ultima pagina chiesta non e' arrivata (R1): la sorgente non risponde (il NAS spento, una scadenza) o ha lanciato.
   * Gli elementi gia' arrivati restano, la lista non si chiude: [retry] la richiede. Il selettore lo dice al posto di
   * «Nessuna foto», o in fondo alla lista se l'errore arriva a meta'.
   */
  var failed: Boolean by mutableStateOf(false)
    private set

  /** Come la sorgente ha capito la ricerca («Mare», «Estate»). */
  var interpretation: List<String> by mutableStateOf(emptyList())
    private set

  /** Quanti elementi ha in tutto, se la sorgente lo sa. */
  var total: Int? by mutableStateOf(null)
    private set

  private var next: String? = null
  private var job: Job? = null

  /** Chiede la pagina dopo, se non sta gia' arrivando e se c'e'. Dopo un errore ([failed]) serve [retry]. */
  fun loadMore() {
    if (loading || finished || failed) return
    loading = true
    job = scope.launch {
      try {
        val page = source.page(query, next, pageSize)
        val fresh = page.items.filter { it.key !in keys }
        keys += fresh.map { it.key }
        loaded += fresh
        onItems(fresh)
        if (version == 0) interpretation = page.interpretation
        total = page.total ?: total
        next = page.next
        finished = page.next == null
        version++
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        // R1: prima la lista si chiudeva qui (`finished`), e una sorgente che non risponde sembrava una libreria vuota.
        failed = true
        version++
      } finally {
        loading = false
      }
    }
  }

  /** Richiede la pagina che non era arrivata ([failed]), dal punto in cui ci si era fermati. */
  fun retry() {
    if (!failed) return
    failed = false
    loadMore()
  }

  /** Ricomincia da capo (dopo uno sblocco, un permesso nuovo, un cambio della sorgente). */
  fun reload() {
    job?.cancel()
    loading = false
    loaded.clear()
    keys.clear()
    next = null
    finished = false
    failed = false
    interpretation = emptyList()
    total = null
    version++
    loadMore()
  }

  private val keys = HashSet<String>()
}

/**
 * Lo stato di un [FluidMediaPicker] (F8.14): le sorgenti (la prima e' quella di partenza), la richiesta dell'app, la scelta
 * in ordine ([selection], col tetto della richiesta), le pagine di risultati gia' arrivate e la consegna.
 *
 * Si crea con [rememberFluidMediaPickerState]. La scelta sopravvive alla rotazione e alla morte del processo **con i suoi
 * elementi** (R1): le chiavi e i campi degli elementi scelti stanno nel `Bundle`, perche' dopo la ricreazione le pagine
 * ripartono dalla prima e un elemento scelto da una pagina lontana, da un album o da una ricerca non tornerebbe. La
 * consegna li ricontrolla comunque con la sorgente («la vista di adesso decide»): uno sparito nel frattempo conta fra i
 * saltati. Una chiave scelta che non si ritrova (l'app l'ha messa nella scelta senza che una pagina l'abbia portata) esce
 * dalla scelta al ✓ e si dice ([notice]), mai un ✓ che non fa niente.
 */
@Stable
class FluidMediaPickerState internal constructor(
  val sources: List<FluidMediaSource>,
  val request: FluidMediaRequest,
  /** La scelta: le chiavi ([FluidMediaItem.key]) nell'ordine dei tocchi, al piu' [FluidMediaRequest.limit]. */
  val selection: FluidSelectionState<String>,
  private val scope: CoroutineScope,
  private val pageSize: Int = DefaultPageSize,
) {
  init {
    require(sources.isNotEmpty()) { "FluidMediaPicker: serve almeno una sorgente" }
  }

  private val known = HashMap<String, FluidMediaItem>()
  private val feeds = HashMap<Pair<String, FluidMediaQuery>, FluidMediaFeed>()
  private val albumLists = mutableStateMapOf<String, List<FluidMediaAlbum>>()

  /** La sorgente di partenza (la prima). */
  val primary: FluidMediaSource get() = sources.first()

  /** La sorgente con l'[id] dato. */
  fun source(id: String): FluidMediaSource? = sources.firstOrNull { it.id == id }

  /** Gli elementi di [source] per [query]: sempre lo stesso oggetto per la stessa coppia, cosi' tornando si ritrova. */
  fun feed(source: FluidMediaSource, query: FluidMediaQuery): FluidMediaFeed =
    feeds.getOrPut(source.id to query) {
      FluidMediaFeed(source, query, scope, pageSize) { fresh -> fresh.forEach { known[it.key] = it } }
    }

  /** Gli album di [source], se sono gia' arrivati; [loadAlbums] li chiede. */
  fun albumsOf(source: FluidMediaSource): List<FluidMediaAlbum>? = albumLists[source.id]

  /** Chiede gli album di [source] (una volta; [force] di nuovo). */
  fun loadAlbums(source: FluidMediaSource, force: Boolean = false) {
    if (!force && albumLists.containsKey(source.id)) return
    scope.launch {
      val list = try {
        source.albums()
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        emptyList()
      }
      list.forEach { album -> album.cover?.let { known[it.key] = it } }
      albumLists[source.id] = list
    }
  }

  /** Rilegge l'accesso di tutte le sorgenti e ricarica cio' che serve (dopo un permesso, uno sblocco). */
  fun refreshAccess() {
    scope.launch {
      for (s in sources) {
        val before = s.access
        try {
          s.refresh()
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
        }
        if (s.access != before) {
          albumLists.remove(s.id)
          feeds.filterKeys { it.first == s.id }.values.forEach { it.reload() }
        }
      }
    }
  }

  /** L'elemento con la chiave data, se e' gia' passato da una pagina. */
  fun item(key: String): FluidMediaItem? = known[key]

  /** Gli elementi scelti, nell'ordine della scelta. */
  val selectedItems: List<FluidMediaItem>
    get() = selectedKeys().mapNotNull { known[it] }

  private fun selectedKeys(): Collection<String> = (selection.mode as? FluidSelectionMode.Explicit)?.keys.orEmpty()

  /** Rimette fra gli elementi conosciuti quelli scelti prima della ricreazione ([rememberFluidMediaPickerState]). */
  internal fun restoreKnown(items: List<FluidMediaItem>) {
    for (item in items) if (item.key !in known) known[item.key] = item
  }

  /**
   * Il ✓ del selettore: consegna la scelta. Una chiave che non si ritrova (scelta dall'app, mai arrivata da una pagina)
   * esce dalla scelta e conta fra i saltati; se non resta niente, [notice] lo dice (R1: prima il ✓ non faceva niente).
   */
  internal suspend fun deliverSelection(): List<FluidPickedMedia>? {
    if (delivering) return null
    val keys = selectedKeys().toList()
    val lost = keys.filter { it !in known }
    lost.forEach { selection.deselect(it) }
    val items = keys.mapNotNull { known[it] }
    if (items.isEmpty()) {
      if (lost.isNotEmpty()) notice = FluidMediaPickerNotice(FluidMediaRefusal.NothingAvailable, lost.size)
      return null
    }
    return deliverItems(items, lost.size)
  }

  /** Una consegna e' in corso. */
  var delivering: Boolean by mutableStateOf(false)
    private set

  /** A che punto e' la consegna in corso. */
  var progress: FluidMediaProgress? by mutableStateOf(null)
    private set

  /** L'avviso dell'ultima consegna non riuscita, o dei saltati; null: niente da dire. */
  var notice: FluidMediaPickerNotice? by mutableStateOf(null)

  /** La sorgente chiusa da sbloccare, se la consegna l'ha trovata chiusa. */
  var unlockNeeded: FluidMediaAccess.Locked? by mutableStateOf(null)

  /**
   * Consegna [items] (di serie la scelta), nell'ordine dato, chiedendo a ogni sorgente i suoi. Se una sorgente rifiuta,
   * non esce niente: la scelta resta com'e' (senza l'elemento che ha fermato la consegna, se e' uno solo) e [notice] dice
   * perche'. Null se non e' uscito niente.
   */
  suspend fun deliver(items: List<FluidMediaItem> = selectedItems): List<FluidPickedMedia>? = deliverItems(items, 0)

  private suspend fun deliverItems(items: List<FluidMediaItem>, alreadySkipped: Int): List<FluidPickedMedia>? {
    if (items.isEmpty() || delivering) return null
    delivering = true
    progress = FluidMediaProgress(0, items.size)
    try {
      val bySource = LinkedHashMap<String, MutableList<FluidMediaItem>>()
      for (item in items) bySource.getOrPut(item.source) { ArrayList() } += item
      val picked = HashMap<String, FluidPickedMedia>()
      val loose = ArrayList<FluidPickedMedia>()
      var skipped = alreadySkipped
      var done = 0
      for ((sourceId, group) in bySource) {
        val source = source(sourceId)
        if (source == null) {
          skipped += group.size
          continue
        }
        val base = done
        when (val result = source.deliver(group, request) { p -> progress = FluidMediaProgress(base + p.done, items.size, p.bytesDone, p.bytesTotal) }) {
          is FluidMediaDelivery.Ready -> {
            skipped += result.skipped
            for (m in result.media) {
              val key = m.item?.key
              if (key != null) picked[key] = m else loose += m
            }
          }
          is FluidMediaDelivery.NeedsUnlock -> {
            unlockNeeded = FluidMediaAccess.Locked(result.unlock)
            return null
          }
          is FluidMediaDelivery.Refused -> {
            result.culprit?.let { selection.deselect(fluidMediaKey(sourceId, it)) }
            notice = FluidMediaPickerNotice(result.reason, skipped = 0)
            return null
          }
        }
        done += group.size
      }
      val ordered = items.mapNotNull { picked[it.key] } + loose
      if (ordered.isEmpty()) {
        notice = FluidMediaPickerNotice(FluidMediaRefusal.NothingAvailable, skipped)
        return null
      }
      notice = if (skipped > 0) FluidMediaPickerNotice(null, skipped) else null
      return ordered
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      notice = FluidMediaPickerNotice(FluidMediaRefusal.Failed, 0)
      return null
    } finally {
      delivering = false
      progress = null
    }
  }

  companion object {
    /** Quanti elementi per pagina: 120, una decina di schermate di cinque colonne. */
    const val DefaultPageSize: Int = 120
  }
}

/** Cosa dire dopo una consegna: il rifiuto ([refusal], null se e' andata) e quanti elementi non c'erano piu'. */
data class FluidMediaPickerNotice(val refusal: FluidMediaRefusal?, val skipped: Int)

/**
 * Lo stato di un [FluidMediaPicker] con queste [sources] (la prima e' quella di partenza) e questa [request]. Un cambio
 * delle sorgenti o della richiesta fa uno stato nuovo; la scelta resta alla rotazione e alla morte del processo, con i
 * suoi elementi (al piu' 200: oltre, quelli in piu' si dicono al ✓ come non piu' disponibili).
 */
@Composable
fun rememberFluidMediaPickerState(
  sources: List<FluidMediaSource>,
  request: FluidMediaRequest = FluidMediaRequest(),
): FluidMediaPickerState {
  val selection = rememberFluidSelectionState<String>(limit = request.limit)
  val scope = rememberCoroutineScope()
  // R1: le chiavi da sole (nel Bundle della scelta) non ritrovano, dopo la ricreazione, gli elementi arrivati da una
  // pagina lontana, da un album o da una ricerca. Si salvano anche i loro campi, solo quelli scelti.
  val saver = remember(sources, request, selection, scope) {
    Saver<FluidMediaPickerState, Any>(
      save = { state -> mediaItemsToSaved(state.selectedItems) },
      restore = { saved ->
        FluidMediaPickerState(sources, request, selection, scope).also { it.restoreKnown(mediaItemsFromSaved(saved)) }
      },
    )
  }
  return rememberSaveable(sources, request, selection, saver = saver) {
    FluidMediaPickerState(sources, request, selection, scope)
  }
}

/** Quanti elementi scelti si salvano con la scelta: 200, ben sotto il limite di un `Bundle` (circa 40 kB). */
internal const val MaxSavedMediaItems = 200

private const val SavedItemFields = 13

/** Gli elementi in una lista piatta di valori che un `Bundle` sa tenere. */
internal fun mediaItemsToSaved(items: List<FluidMediaItem>): ArrayList<Any?> {
  val out = ArrayList<Any?>(minOf(items.size, MaxSavedMediaItems) * SavedItemFields)
  for (item in items.take(MaxSavedMediaItems)) {
    out += item.source
    out += item.id
    out += item.kind.id
    out += item.mimeType
    out += item.width
    out += item.height
    out += item.durationMillis
    out += item.takenAtMillis
    out += item.sizeBytes
    out += item.name
    out += item.backupOnly
    out += item.favorite
    out += item.description
  }
  return out
}

/** Il contrario di [mediaItemsToSaved]; un formato che non torna da' una lista vuota, mai un'eccezione. */
internal fun mediaItemsFromSaved(saved: Any?): List<FluidMediaItem> {
  val list = saved as? List<*> ?: return emptyList()
  if (list.size % SavedItemFields != 0) return emptyList()
  return (0 until list.size / SavedItemFields).mapNotNull { i ->
    val f = list.subList(i * SavedItemFields, (i + 1) * SavedItemFields)
    val source = f[0] as? String ?: return@mapNotNull null
    val id = f[1] as? String ?: return@mapNotNull null
    FluidMediaItem(
      source = source,
      id = id,
      kind = FluidMediaKind.of(f[2] as? String ?: FluidMediaKind.Photo.id),
      mimeType = f[3] as? String,
      width = (f[4] as? Number)?.toInt(),
      height = (f[5] as? Number)?.toInt(),
      durationMillis = (f[6] as? Number)?.toLong(),
      takenAtMillis = (f[7] as? Number)?.toLong(),
      sizeBytes = (f[8] as? Number)?.toLong(),
      name = f[9] as? String,
      backupOnly = f[10] as? Boolean ?: false,
      favorite = f[11] as? Boolean ?: false,
      description = f[12] as? String,
    )
  }
}
