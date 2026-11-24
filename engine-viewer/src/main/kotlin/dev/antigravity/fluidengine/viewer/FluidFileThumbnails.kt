package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageRead
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * F6C.17 · La miniatura di un file, come la mostra File di iOS: il contenuto in piccolo (le prime righe, la tabella,
 * l'albero, la pagina, la copertina, l'icona dell'app, il modello) oppure, per un tipo che niente legge, l'icona del
 * documento con l'estensione. Si chiede con [FluidFileThumbnails.load].
 *
 * [fillsFrame] dice come sta nella tessera: vero per una figura (foto, fotogramma, modello 3D), che riempie la tessera
 * e si ritaglia ([ContentScale.Crop]); falso per un foglio o un simbolo, che si mostra intero ([ContentScale.Fit]).
 * [isPlaceholder] è vero quando l'immagine dice solo il tipo: un tipo sconosciuto, un file rotto, una lettura oltre il
 * tempo massimo.
 */
@Immutable
class FluidFileThumbnail internal constructor(
  val image: ImageBitmap,
  val fillsFrame: Boolean,
  val isPlaceholder: Boolean,
) {
  /** [ContentScale.Crop] per una figura, [ContentScale.Fit] per un foglio o un simbolo. */
  val contentScale: ContentScale get() = if (fillsFrame) ContentScale.Crop else ContentScale.Fit

  /** Quanto pesa in memoria: conta per il tetto della cache. */
  internal val byteCount: Long = image.pixelBytes

  internal companion object {
    fun of(image: ImageBitmap, fillsByKind: Boolean): FluidFileThumbnail {
      val fit = try {
        FluidThumbnailPainter.fitOf(image.asAndroidBitmap())
      } catch (_: Exception) {
        null
      }
      return FluidFileThumbnail(image, fit?.fills ?: fillsByKind, fit?.placeholder ?: false)
    }
  }
}

/**
 * I colori dell'app per le miniature che ne hanno (la tinta leggera dell'archivio, dei simboli): l'accento, il secondo
 * accento, la superficie e il tema. La carta dei documenti resta bianca in tutti e due i temi.
 *
 * È anche un elemento del contesto delle coroutine: il registro chiama la miniatura di una famiglia dentro
 * `withContext(look)`, e la famiglia lo legge con [current]. Così un modulo di famiglia riceve i colori senza che la
 * firma di `FluidViewerFamily.thumbnail` cambi.
 */
@Immutable
data class FluidThumbnailLook(
  val accent: Color,
  val accentSecondary: Color,
  val surface: Color,
  val dark: Boolean,
) : AbstractCoroutineContextElement(FluidThumbnailLook) {
  companion object Key : CoroutineContext.Key<FluidThumbnailLook> {
    /** Il chiaro di serie: systemBlue e systemIndigo di Apple sul grigio di sistema. */
    val Light: FluidThumbnailLook get() = DefaultLight

    /** Lo scuro di serie. */
    val Dark: FluidThumbnailLook get() = DefaultDark

    /** I colori del contesto, o [Light]. */
    suspend fun current(): FluidThumbnailLook = currentCoroutineContext()[Key] ?: Light
  }
}

private val DefaultLight = FluidThumbnailLook(Color(0xFF007AFF), Color(0xFF5856D6), Color(0xFFF2F2F7), dark = false)
private val DefaultDark = FluidThumbnailLook(Color(0xFF0A84FF), Color(0xFF5E5CE6), Color(0xFF1C1C1E), dark = true)

/** I colori del tema Material corrente per le miniature: l'accento, il terziario, la superficie e il tema. */
@Composable
fun rememberFluidThumbnailLook(): FluidThumbnailLook {
  val scheme = MaterialTheme.colorScheme
  return remember(scheme.primary, scheme.tertiary, scheme.surface) {
    FluidThumbnailLook(scheme.primary, scheme.tertiary, scheme.surface, dark = scheme.surface.luminance() < 0.5f)
  }
}

/**
 * F6C.17 · Le miniature dei file per l'app: **una chiamata, sempre un'immagine**.
 *
 * ```kotlin
 * // In una griglia: la prima composizione legge la cache, così tornando dal visore la tessera è già piena.
 * val thumbnail = rememberFluidFileThumbnail(file, sizePx = with(LocalDensity.current) { 160.dp.roundToPx() })
 * Box(Modifier.size(160.dp, 106.dp).fluidViewerThumbnail(key, shape).clip(shape)) {
 *   thumbnail?.let { FluidFileThumbnailImage(it, Modifier.fillMaxSize()) }
 * }
 *
 * // Fuori da Compose:
 * val thumbnail = FluidFileThumbnails.load(context, file, sizePx = 480)
 * ```
 *
 * **Che cosa dà**, nell'ordine:
 * 1. la miniatura del modulo di famiglia che apre il file (Office, dati, grafica, 3D, archivi: `FluidViewerFamily.thumbnail`);
 * 2. quelle di engine-viewer di serie: le prime righe del testo e del codice coi colori, l'icona di un APK, il primo
 *    fotogramma di un'immagine, la prima pagina di un PDF, un fotogramma di un video, la copertina di un audio;
 * 3. altrimenti **l'icona del documento con l'estensione**, come File ([FluidFileThumbnail.isPlaceholder]). Un file
 *    rotto o che non si legge dà lo stesso ripiego: la chiamata non lancia mai, se non per l'annullamento.
 *
 * **Come lavora.**
 * - **Fuori dal filo principale**: legge e disegna su `Dispatchers.IO`, al più [Parallelism] file alla volta, così
 *   una griglia che scorre veloce non apre cinquanta decodificatori insieme.
 * - **Un tempo massimo** ([DefaultTimeoutMillis], o `timeoutMillis`): oltre, la chiamata torna subito col ripiego,
 *   senza aspettare il decodificatore, che si annulla. Il ripiego per tempo scaduto si riprova dopo
 *   [RetryAfterTimeoutMillis].
 * - **La cache in memoria**, con un tetto in byte ([memoryLimitBytes]): le meno usate escono per prime. La chiave è il
 *   file (`Uri`, nome, MIME, dimensione), il lato lungo arrotondato a 32 px, i colori ([FluidThumbnailLook]) e i
 *   moduli presenti (la scena «senza moduli» ha le sue). Due richieste uguali insieme fanno un lavoro solo.
 * - Un file che cambia tenendo lo stesso `Uri` e la stessa dimensione va tolto con [evict].
 */
object FluidFileThumbnails {
  /**
   * Read a content thumbnail from a source already owned by the caller, for example an item of an archive.
   * The caller keeps the source open until the read completes. Returns null when only a placeholder is available.
   */
  suspend fun read(
    context: Context,
    source: FluidViewerSource,
    sizePx: Int,
    look: FluidThumbnailLook = FluidThumbnailLook.Light,
    options: FluidViewerOptions = FluidViewerOptions(),
  ): FluidFileThumbnail? = readSource(context, source, sizePx, look, options, everything = true)

  internal suspend fun readSource(context: Context, source: FluidViewerSource, sizePx: Int, look: FluidThumbnailLook,
    options: FluidViewerOptions, everything: Boolean): FluidFileThumbnail? {
    val memory = memory(context)
    val caller = currentCoroutineContext()
    val optional = caller[OptionalViewerWork] != null
    val side = FluidThumbnailPainter.clampSize(sizePx)
    return withContext(Dispatchers.IO + look) {
      memory.whileAllowed(optional) {
        memory.resources.refreshBeforeAllocation()
        memory.thumbnails.run(optional) {
          decode(memory, side, optional, { edge ->
            fluidThumbnailOf(context, source, edge, options, everything)?.takeUnless { it.isPlaceholder }
          }) { pixels -> pixels.use { it.read(caller[FluidPageRead]) } }
        }
      }
    }
  }

  /**
   * Il tempo massimo di serie: 2,5 s. Office si dà 2 s per disegnare una pagina (F6C.15b) e un file grande deve
   * ancora copiarsi; oltre, l'icona è meglio di una tessera vuota. A occhio, 23 novembre 2026.
   */
  const val DefaultTimeoutMillis: Long = 2_500L

  /** Dopo quanto si riprova un file che aveva superato il tempo: 30 s, a occhio, 23 novembre 2026. */
  const val RetryAfterTimeoutMillis: Long = 30_000L

  /** Quanti file si leggono insieme: 3, come i nuclei grandi di un telefono di fascia media. A occhio, 23 novembre 2026. */
  const val Parallelism: Int = 3

  /**
   * Il tetto della cache in byte. Di serie un ottavo della memoria dell'app, al più 48 MB: una griglia di sessanta
   * tessere da 450 px sta intera. Abbassarlo toglie subito le miniature in più.
   */
  var memoryLimitBytes: Long = minOf(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024)
    set(value) {
      require(value >= 0) { "Il tetto non può essere negativo" }
      field = value
      synchronized(lock) { trim() }
    }

  private data class Key(
    val uri: String,
    val name: String,
    val mime: String?,
    val size: Long?,
    val modified: Long?,
    val bucket: Int,
    val look: FluidThumbnailLook,
    val families: String,
  ) {
    fun supersedes(old: Key) = uri == old.uri && (size != old.size || modified != old.modified)
  }

  private class Entry(val pixels: ViewerPixels<FluidFileThumbnail>, val expiresAt: Long)
  private class Flight(val generation: Long, val optional: Boolean) {
    lateinit var job: Deferred<Unit>
    var waiters = 0
    var result: ViewerPixels<FluidFileThumbnail>? = null
    var retry = false
    var timedOut = false
  }

  private val lock = Any()
  private val entries = LinkedHashMap<Key, Entry>(64, 0.75f, true)
  private var bytes = 0L
  private var emergency: ViewerPixels<FluidFileThumbnail>? = null
  private val inflight = HashMap<Key, Flight>()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var watched: ViewerMemory? = null
  private var subscription: java.io.Closeable? = null

  private fun memory(context: Context): ViewerMemory {
    val memory = ViewerMemory.get(context)
    synchronized(memory.lock) {
      if (watched !== memory) {
        subscription?.close()
        clear()
        subscription = memory.watch(FluidMemoryCategory.THUMBNAILS) { level ->
          synchronized(lock) {
            if (level != null) {
              cancelFlights { true }
              emergency?.close()
              emergency = null
              emptyEntries()
            } else entries.filterValues { !it.pixels.visible }.keys.forEach(::remove)
          }
        }
        watched = memory
      }
    }
    return memory
  }

  /** Quante miniature e quanti byte tiene la cache adesso: per i test e per chi misura. */
  val cachedCount: Int get() = synchronized(lock) { entries.size }
  val cachedBytes: Long get() = synchronized(lock) { bytes }

  /** La miniatura già pronta, senza leggere niente: per la prima composizione di una tessera. `null` se non c'è. */
  fun cached(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook = FluidThumbnailLook.Light): FluidFileThumbnail? =
    synchronized(lock) { live(key(file, sizePx, look))?.read(null) }

  /**
   * La miniatura di [file], larga al più [sizePx] sul lato lungo (fra 32 e 1024), con i colori [look]. Dalla cache se
   * c'è; altrimenti letta e disegnata fuori dal filo principale, entro [timeoutMillis]. Un file rotto, che
   * manca o che supera il tempo dà l'icona del documento. Con quota già nulla alla prima chiamata,
   * anche il simbolo minimo può essere rifiutato ([FluidViewerFailure.TooLarge]).
   */
  suspend fun load(
    context: Context,
    file: FluidViewerFile,
    sizePx: Int,
    look: FluidThumbnailLook = FluidThumbnailLook.Light,
    timeoutMillis: Long = DefaultTimeoutMillis,
    options: FluidViewerOptions = FluidViewerOptions(),
  ): FluidFileThumbnail = fetch(context, file, sizePx, look, timeoutMillis, options, optional = false) ?: noViewerMemory()

  /** Prepara una tessera fuori schermo solo finché il processo permette il prefetch. */
  suspend fun prefetch(context: Context, file: FluidViewerFile, sizePx: Int,
    look: FluidThumbnailLook = FluidThumbnailLook.Light) {
    try { fetch(context, file, sizePx, look, DefaultTimeoutMillis, FluidViewerOptions(), optional = true) }
    catch (_: CancellationException) { currentCoroutineContext().ensureActive() }
  }

  private suspend fun fetch(context: Context, file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook,
    timeoutMillis: Long, options: FluidViewerOptions, optional: Boolean): FluidFileThumbnail? {
    require(timeoutMillis > 0) { "Il tempo massimo deve essere positivo" }
    val app = context.applicationContext ?: context
    val memory = memory(app)
    if (optional && !memory.mayPrefetch(FluidMemoryCategory.THUMBNAILS)) return null
    val key = key(file, sizePx, look)
    val reader = currentCoroutineContext()[FluidPageRead]
    val flight = synchronized(lock) {
      cancelFlights(key::supersedes)
      entries.keys.filter(key::supersedes).forEach(::remove)
      live(key)?.let { return if (optional) null else it.read(reader) }
      if (!optional && inflight[key]?.optional == true) cancelFlights { it == key }
      inflight.getOrPut(key) {
        Flight(memory.resources.trimGeneration, optional).apply {
          job = scope.async(start = CoroutineStart.LAZY) { produce(memory, app, file, key, this@apply, timeoutMillis, options) }
        }
      }.also { it.waiters++ }
    }
    try {
      flight.job.await()
      return synchronized(lock) {
        if (inflight[key] !== flight || flight.generation != memory.resources.trimGeneration)
          throw CancellationException("Miniatura superata")
        if (optional) null else flight.result?.read(reader)
      }
    } finally {
      synchronized(lock) {
        if (--flight.waiters == 0) {
          if (inflight[key] === flight) inflight.remove(key)
          flight.job.cancel()
          flight.result?.close()
        }
      }
    }
  }

  internal fun cachedForReader(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook, reader: FluidPageRead) =
    synchronized(lock) { live(key(file, sizePx, look))?.read(reader) }

  /** Toglie dalla cache le miniature di [file], a ogni misura e con ogni colore. */
  fun evict(file: FluidViewerFile) {
    val uri = file.uri.toString()
    synchronized(lock) {
      cancelFlights { it.uri == uri }
      val gone = entries.keys.filter { it.uri == uri }
      for (key in gone) remove(key)
    }
  }

  /** Svuota la cache. */
  fun clear() {
    synchronized(lock) {
      cancelFlights { true }
      emergency?.close()
      emergency = null
      emptyEntries()
    }
  }

  private fun cancelFlights(matches: (Key) -> Boolean) {
    val iterator = inflight.entries.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      if (matches(entry.key)) {
        iterator.remove()
        entry.value.job.cancel()
      }
    }
  }

  private fun emptyEntries() {
    entries.values.forEach { it.pixels.close() }
    entries.clear()
    bytes = 0
  }

  private fun key(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook): Key {
    val size = FluidThumbnailPainter.clampSize(sizePx)
    val bucket = (size + 31) / 32 * 32
    val families = FluidViewerRegistry.families.joinToString(",") { it.id }
    return Key(file.uri.toString(), file.name, file.mimeType, file.sizeBytes, file.modifiedAtMillis, bucket.coerceAtMost(FluidThumbnailPainter.MaxSizePx),
      look, families)
  }

  private fun live(key: Key): ViewerPixels<FluidFileThumbnail>? {
    val entry = entries[key] ?: return null
    if (entry.expiresAt <= SystemClock.elapsedRealtime()) {
      remove(key)
      return null
    }
    return entry.pixels
  }

  private suspend fun <R> decode(memory: ViewerMemory, side: Int, optional: Boolean,
    make: suspend (Int) -> FluidFileThumbnail?, publish: (ViewerPixels<FluidFileThumbnail>) -> R): R? {
    var retryEmpty = false
    return memory.decode(FluidMemoryCategory.THUMBNAILS, IntSize(side, side), optional,
      // Il campione FP16 può avere lati quasi doppi, più copia orientata/composta e I/O.
      { pixelPeak(it, 40) }, { size ->
        ThumbnailAllocation(size.width).use { allocation ->
          val made = withContext(allocation) { make(size.width) }
          retryEmpty = allocation.denied
          made?.takeUnless { retryEmpty && it.isPlaceholder && size.width > FluidThumbnailPainter.MinSizePx }
        }
      }, { it.byteCount }, { it.image.physical }, minSide = FluidThumbnailPainter.MinSizePx,
      retryEmpty = { retryEmpty }, publish = publish)
  }

  private suspend fun produce(memory: ViewerMemory, context: Context, file: FluidViewerFile, key: Key,
    flight: Flight, timeoutMillis: Long, options: FluidViewerOptions) {
    fun install(pixels: ViewerPixels<FluidFileThumbnail>, fallback: Boolean) = synchronized(lock) {
      if (inflight[key] !== flight || flight.timedOut && !fallback ||
        flight.optional && !memory.resources.prefetchAllowed.value ||
        flight.generation != memory.resources.trimGeneration) throw CancellationException("Miniatura superata")
      flight.result = pixels
      val expires = if (flight.timedOut || flight.retry) SystemClock.elapsedRealtime() + RetryAfterTimeoutMillis else Long.MAX_VALUE
      put(key, Entry(pixels.retain(), expires))
      true
    }
    if (!flight.optional) seedFallback(memory, flight.generation)
    // Il decoder nativo può ignorare l'annullo: possiede prenotazione e posto fino all'uscita reale.
    val work = scope.async(if (flight.optional) OptionalViewerWork() else kotlin.coroutines.EmptyCoroutineContext) {
      memory.whileAllowed(flight.optional) {
        memory.resources.refreshBeforeAllocation()
        memory.thumbnails.run(flight.optional) {
          var attempted = false
          val published = decode(memory, key.bucket, flight.optional, { edge ->
            attempted = true
            FluidViewerSource.of(context, file.uri, file.name, file.mimeType, file.sizeBytes, options.sourceMaxCopyBytes).use { source ->
              withContext(key.look) { fluidThumbnailOf(context, source, edge, options, everything = true) }
            }
          }) { install(it, false) }
          if (!attempted) synchronized(lock) { flight.retry = true }
          published
        }
      }
    }
    try {
      if (withTimeoutOrNull(timeoutMillis) { work.await() ?: false } == true) return
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // File mancante o decoder non disponibile: prova il simbolo entro la quota.
    } finally {
      synchronized(lock) { if (flight.result == null) flight.timedOut = !work.isCompleted }
      work.cancel()
    }
    currentCoroutineContext().ensureActive()
    if (flight.optional) return
    synchronized(lock) { if (flight.result != null) return }
    // Anche l'icona passa per il registro; non crea mai una bitmap fuori quota.
    memory.decode(FluidMemoryCategory.THUMBNAILS, IntSize(key.bucket, key.bucket), false,
      { pixelPeak(it, 40) }, { if (it.width >= FluidThumbnailPainter.MinSizePx) fluidPlaceholderThumbnail(file.name, it.width) else null },
      { it.byteCount }, { it.image.physical }, minSide = FluidThumbnailPainter.MinSizePx) { install(it, true) } ?: run {
        val minimum = FluidThumbnailPainter.MinSizePx
        val small = memory.decode(FluidMemoryCategory.THUMBNAILS, IntSize(minimum, minimum), false,
          { pixelPeak(it, 4, 0) }, { fluidPlaceholderThumbnail(file.name, minimum) }, { it.byteCount },
          { it.image.physical }, minSide = minimum) { install(it, true) }
        if (small == true) return
        synchronized(lock) {
          emergency?.retain()?.let { pixels ->
            try { install(pixels, true) } catch (error: Throwable) { pixels.close(); throw error }
            return
          }
        }
        val usage = memory.resources.budget.usage(FluidMemoryCategory.THUMBNAILS)
        throw FluidViewerException(FluidViewerFailure.TooLarge,
          "Miniature: ${usage.usedBytes}/${usage.limitBytes} byte, ${usage.inFlightBytes} in decodifica")
      }
  }

  // Conserva un solo simbolo piccolo prima di avviare il nativo. Un calo di quota durante quel lavoro
  // può vietare persino una nuova bitmap da 32 px, senza invalidare i pixel già ammessi.
  private fun seedFallback(memory: ViewerMemory, generation: Long) {
    memory.resources.refreshBeforeAllocation()
    synchronized(lock) {
      if (emergency != null) return
      memory.ensureCurrent(generation, false)
      val edge = FluidThumbnailPainter.MinSizePx
      memory.resources.budget.tryReserve(FluidMemoryCategory.THUMBNAILS, pixelPeak(IntSize(edge, edge), 4, 0))?.use {
        val icon = fluidPlaceholderThumbnail("", edge)
        val owner = it.confirm(icon.byteCount)
        if (owner == null) icon.image.asAndroidBitmap().recycle()
        else emergency = ViewerPixels(icon, icon.image.physical, owner)
      }
    }
  }

  private fun put(key: Key, entry: Entry) {
    if (entry.pixels.value.byteCount > memoryLimitBytes) { entry.pixels.close(); return }
    remove(key)
    entries[key] = entry
    bytes += entry.pixels.value.byteCount
    trim()
  }

  private fun remove(key: Key) {
    entries.remove(key)?.let { bytes -= it.pixels.value.byteCount; it.pixels.close() }
  }

  private fun trim() {
    val iterator = entries.entries.iterator()
    while (bytes > memoryLimitBytes && iterator.hasNext()) {
      val eldest = iterator.next()
      bytes -= eldest.value.pixels.value.byteCount
      eldest.value.pixels.close()
      iterator.remove()
    }
  }
}



/**
 * La miniatura di [file] in Compose: la cache alla prima composizione (la tessera che torna dal visore è già piena,
 * e l'apertura dalla miniatura parte dalla stessa immagine), poi [FluidFileThumbnails.load]. `null` finché non c'è.
 */
@Composable
fun rememberFluidFileThumbnail(
  file: FluidViewerFile?,
  sizePx: Int,
  look: FluidThumbnailLook = rememberFluidThumbnailLook(),
  timeoutMillis: Long = FluidFileThumbnails.DefaultTimeoutMillis,
): FluidFileThumbnail? {
  val context = LocalContext.current
  val memory = remember(context) { ViewerMemory.get(context) }
  val visible = memory.resources.uiVisible.collectAsState().value
  val reader = remember(file, sizePx, look, visible) { FluidPageRead() }
  val state = remember(reader) { mutableStateOf(if (file != null && visible) FluidFileThumbnails.cachedForReader(file, sizePx, look, reader) else null) }
  LaunchedEffect(reader) {
    if (visible && file != null && state.value == null)
      state.value = try {
        withContext(reader) { FluidFileThumbnails.load(context, file, sizePx, look, timeoutMillis) }
      } catch (_: FluidViewerException) { null }
  }
  return state.value
}

/** [thumbnail] nella sua tessera: ritagliata se è una figura, intera se è un foglio o un simbolo. */
@Composable
fun FluidFileThumbnailImage(thumbnail: FluidFileThumbnail, modifier: Modifier = Modifier.fillMaxSize(),
  contentDescription: String? = null) {
  Image(thumbnail.image, contentDescription, modifier, contentScale = thumbnail.contentScale)
}
