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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
  internal val byteCount: Long = try {
    image.asAndroidBitmap().allocationByteCount.toLong()
  } catch (_: Exception) {
    image.width.toLong() * image.height * 4
  }

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
  ): FluidFileThumbnail? = withContext(Dispatchers.IO + look) {
    permits.withPermit {
      fluidThumbnailOf(context, source, FluidThumbnailPainter.clampSize(sizePx), options, everything = true)
        ?.takeUnless { it.isPlaceholder }
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

  private class Key(
    val uri: String,
    val name: String,
    val mime: String?,
    val size: Long?,
    val bucket: Int,
    val look: FluidThumbnailLook,
    val families: String,
  ) {
    override fun equals(other: Any?): Boolean = other is Key && uri == other.uri && name == other.name &&
      mime == other.mime && size == other.size && bucket == other.bucket && look == other.look && families == other.families

    override fun hashCode(): Int = listOf(uri, name, mime, size, bucket, look, families).hashCode()
  }

  private class Entry(val thumbnail: FluidFileThumbnail, val expiresAt: Long)

  private val lock = Any()
  private val entries = LinkedHashMap<Key, Entry>(64, 0.75f, true)
  private var bytes = 0L
  private val inflight = HashMap<Key, Deferred<FluidFileThumbnail>>()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val permits = Semaphore(Parallelism)

  /** Quante miniature e quanti byte tiene la cache adesso: per i test e per chi misura. */
  val cachedCount: Int get() = synchronized(lock) { entries.size }
  val cachedBytes: Long get() = synchronized(lock) { bytes }

  /** La miniatura già pronta, senza leggere niente: per la prima composizione di una tessera. `null` se non c'è. */
  fun cached(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook = FluidThumbnailLook.Light): FluidFileThumbnail? =
    synchronized(lock) { live(key(file, sizePx, look)) }

  /**
   * La miniatura di [file], larga al più [sizePx] sul lato lungo (fra 32 e 1024), con i colori [look]. Dalla cache se
   * c'è; altrimenti letta e disegnata fuori dal filo principale, entro [timeoutMillis]. Non lancia: un file rotto, che
   * manca o che supera il tempo dà l'icona del documento con l'estensione.
   */
  suspend fun load(
    context: Context,
    file: FluidViewerFile,
    sizePx: Int,
    look: FluidThumbnailLook = FluidThumbnailLook.Light,
    timeoutMillis: Long = DefaultTimeoutMillis,
    options: FluidViewerOptions = FluidViewerOptions(),
  ): FluidFileThumbnail {
    require(timeoutMillis > 0) { "Il tempo massimo deve essere positivo" }
    val app = context.applicationContext ?: context
    val key = key(file, sizePx, look)
    val job = synchronized(lock) {
      live(key)?.let { return it }
      inflight.getOrPut(key) {
        scope.async { produce(app, file, key, timeoutMillis, options) }.also { deferred ->
          deferred.invokeOnCompletion { synchronized(lock) { if (inflight[key] === deferred) inflight.remove(key) } }
        }
      }
    }
    return job.await()
  }

  /** Toglie dalla cache le miniature di [file], a ogni misura e con ogni colore. */
  fun evict(file: FluidViewerFile) {
    val uri = file.uri.toString()
    synchronized(lock) {
      val gone = entries.keys.filter { it.uri == uri }
      for (key in gone) remove(key)
    }
  }

  /** Svuota la cache. */
  fun clear() {
    synchronized(lock) {
      entries.clear()
      bytes = 0
    }
  }

  private fun key(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook): Key {
    val size = FluidThumbnailPainter.clampSize(sizePx)
    val bucket = (size + 31) / 32 * 32
    val families = FluidViewerRegistry.families.joinToString(",") { it.id }
    return Key(file.uri.toString(), file.name, file.mimeType, file.sizeBytes, bucket.coerceAtMost(FluidThumbnailPainter.MaxSizePx),
      look, families)
  }

  private fun live(key: Key): FluidFileThumbnail? {
    val entry = entries[key] ?: return null
    if (entry.expiresAt <= SystemClock.elapsedRealtime()) {
      remove(key)
      return null
    }
    return entry.thumbnail
  }

  private suspend fun produce(context: Context, file: FluidViewerFile, key: Key, timeoutMillis: Long,
    options: FluidViewerOptions): FluidFileThumbnail {
    // Il lavoro è un fratello: allo scadere del tempo si torna subito, anche se il decodificatore non si accorge
    // dell'annullamento finché non finisce la sua lettura.
    val work = scope.async {
      permits.withPermit {
        FluidViewerSource.of(context, file.uri, file.name, file.mimeType, file.sizeBytes, options.sourceMaxCopyBytes).use { source ->
          withContext(key.look) { fluidThumbnailOf(context, source, key.bucket, options, everything = true) }
        }
      }
    }
    var timedOut = false
    val made = try {
      withTimeoutOrNull(timeoutMillis) { work.await() }.also { if (it == null) timedOut = true }
    } catch (e: CancellationException) {
      work.cancel()
      // Annullato chi aspettava (la tessera è uscita): l'annullamento passa; il tempo scaduto invece no.
      if (!currentCoroutineContext().isActiveSafe()) throw e
      null
    } catch (_: Throwable) {
      null
    }
    if (made == null) work.cancel()
    val thumbnail = made ?: fluidPlaceholderThumbnail(file.name, key.bucket)
    val expires = if (timedOut) SystemClock.elapsedRealtime() + RetryAfterTimeoutMillis else Long.MAX_VALUE
    synchronized(lock) { put(key, Entry(thumbnail, expires)) }
    return thumbnail
  }

  private fun put(key: Key, entry: Entry) {
    if (entry.thumbnail.byteCount > memoryLimitBytes) return
    remove(key)
    entries[key] = entry
    bytes += entry.thumbnail.byteCount
    trim()
  }

  private fun remove(key: Key) {
    entries.remove(key)?.let { bytes -= it.thumbnail.byteCount }
  }

  private fun trim() {
    val iterator = entries.entries.iterator()
    while (bytes > memoryLimitBytes && iterator.hasNext()) {
      val eldest = iterator.next()
      bytes -= eldest.value.thumbnail.byteCount
      iterator.remove()
    }
  }
}

private fun CoroutineContext.isActiveSafe(): Boolean = this[kotlinx.coroutines.Job]?.isActive != false

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
  val state = remember(file, sizePx, look) { mutableStateOf(file?.let { FluidFileThumbnails.cached(it, sizePx, look) }) }
  LaunchedEffect(state) {
    if (file != null && state.value == null) state.value = FluidFileThumbnails.load(context, file, sizePx, look, timeoutMillis)
  }
  return state.value
}

/** [thumbnail] nella sua tessera: ritagliata se è una figura, intera se è un foglio o un simbolo. */
@Composable
fun FluidFileThumbnailImage(thumbnail: FluidFileThumbnail, modifier: Modifier = Modifier.fillMaxSize(),
  contentDescription: String? = null) {
  Image(thumbnail.image, contentDescription, modifier, contentScale = thumbnail.contentScale)
}
