package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Un documento a pagine che sa aprire una pagina alla volta. Non è thread-safe: lo usa solo [FluidPageRenderQueue],
 * da un thread solo.
 */
internal interface FluidPageBackend<T> : Closeable {
  val pageCount: Int
  /** La misura della pagina [index] in punti (1/72 di pollice). Apre e chiude la pagina. */
  fun pageSize(index: Int): IntSize
  /** La pagina [index] resa a [target] pixel, su fondo bianco. Apre e chiude la pagina, anche se la resa fallisce. */
  fun render(index: Int, target: IntSize): T
}

/**
 * Il nucleo del sorgente PDF, senza Android: **un thread solo**, una pagina aperta per volta, una cache di poche
 * pagine intere e una di anteprime.
 *
 * Perché un thread solo: `PdfRenderer` non è thread-safe e accetta una pagina aperta alla volta (aprirne una seconda
 * è un `IllegalStateException`). Ogni accesso al documento passa da un esecutore a un thread, in ordine; la chiamata
 * che arriva mentre un'altra rende aspetta il suo turno senza occupare il thread principale.
 *
 * Le due cache sono LRU. Le pagine intere ne tengono al più [maxPages] e [maxBytes] in tutto (4 byte per pixel); le
 * anteprime, a lato lungo [previewLongSide], al più [maxPreviews]: sono quelle che il preset mostra mentre si scorre,
 * prima che arrivi la pagina intera. Una pagina uscita dalla cache non si ricicla (il preset potrebbe ancora
 * disegnarla): la libera il garbage collector.
 *
 * Annullare una chiamata in attesa la toglie dalla coda prima che apra la pagina; una resa già cominciata finisce (una
 * pagina di `PdfRenderer` non si interrompe a metà), si chiude e va in cache, e chi aspettava riceve l'annullamento.
 * [close] chiude il documento sul suo thread dopo l'ultima resa in corso, e ferma il thread.
 */
internal class FluidPageRenderQueue<T : Any>(
  private val open: () -> FluidPageBackend<T>,
  private val bytesOf: (T) -> Long,
  val maxPages: Int = DefaultMaxPages,
  val maxBytes: Long = DefaultMaxBytes,
  val maxPreviews: Int = DefaultMaxPreviews,
  val previewLongSide: Int = DefaultPreviewLongSide,
  val maxPixels: Long = DefaultMaxPixels,
  threadName: String = "FluidPdf",
  /** Gira una volta sola, sul thread del documento, dopo la sua chiusura (o dopo un'apertura fallita o annullata). */
  private val release: () -> Unit = {},
) : Closeable {

  private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName).apply { isDaemon = true } }
  private val thread: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

  // Tutto ciò che segue lo tocca solo il thread del documento.
  private var backend: FluidPageBackend<T>? = null
  private var sizes: Array<IntSize?> = emptyArray()
  private val pages = LinkedHashMap<Key, T>(16, 0.75f, true)
  private val previews = LinkedHashMap<Key, T>(16, 0.75f, true)
  private var pageBytes = 0L

  @Volatile private var closed = false
  private val released = java.util.concurrent.atomic.AtomicBoolean(false)

  /** Quante volte una pagina è stata davvero resa (non servita dalla cache). Per i test. */
  @Volatile var renders: Int = 0
    private set

  private data class Key(val index: Int, val width: Int, val height: Int)

  /** Apre il documento sul suo thread. Un errore qui (file rotto, protetto) chiude tutto e risale a chi chiama. */
  suspend fun start(): Int = onThread {
    val opened = try {
      open()
    } catch (t: Throwable) {
      closed = true
      finish()
      executor.shutdown()
      throw t
    }
    backend = opened
    sizes = arrayOfNulls(opened.pageCount)
    pageCount = opened.pageCount
    opened.pageCount
  }

  /** Le pagine del documento, 0 finché [start] non è tornato. */
  @Volatile var pageCount: Int = 0
    private set

  suspend fun size(index: Int): IntSize = onThread {
    val document = document()
    require(index in sizes.indices) { "Pagina $index fuori da 0 until ${sizes.size}" }
    sizes[index] ?: document.pageSize(index).also { sizes[index] = it }
  }

  /**
   * La pagina [index] resa a [target] pixel. Un [target] oltre [maxPixels] si riduce mantenendo le proporzioni (la
   * bitmap è più piccola di quanto chiesto: il preset la scala). Un lato lungo entro [previewLongSide] va nella cache
   * delle anteprime.
   */
  suspend fun render(index: Int, target: IntSize): T {
    require(target.width > 0 && target.height > 0) { "La misura deve essere positiva: $target" }
    val size = capped(target)
    return onThread {
      val document = document()
      require(index in sizes.indices) { "Pagina $index fuori da 0 until ${sizes.size}" }
      val key = Key(index, size.width, size.height)
      val preview = maxOf(size.width, size.height) <= previewLongSide
      val cache = if (preview) previews else pages
      cache[key]?.let { return@onThread it }
      val image = document.render(index, size)
      renders++
      if (preview) {
        previews[key] = image
        while (previews.size > maxPreviews) previews.remove(previews.keys.first())
      } else {
        pages[key] = image
        pageBytes += bytesOf(image)
        while (pages.size > maxPages || pageBytes > maxBytes && pages.size > 1) {
          val eldest = pages.keys.first()
          pageBytes -= bytesOf(pages.remove(eldest)!!)
        }
      }
      image
    }
  }

  /** L'anteprima della pagina [index]: lato lungo [previewLongSide], proporzioni della pagina. */
  suspend fun preview(index: Int): T {
    val page = size(index)
    val scale = previewLongSide.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
    return render(index, IntSize((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1)))
  }

  /** Le pagine intere e le anteprime in cache adesso. Per i test. */
  internal suspend fun cached(): Pair<Int, Int> = onThread { pages.size to previews.size }

  internal fun capped(target: IntSize): IntSize {
    val pixels = target.width.toLong() * target.height
    if (pixels <= maxPixels) return target
    val scale = sqrt(maxPixels.toDouble() / pixels)
    return IntSize((target.width * scale).toInt().coerceAtLeast(1), (target.height * scale).toInt().coerceAtLeast(1))
  }

  private fun document(): FluidPageBackend<T> {
    if (closed) throw CancellationException("Il documento è chiuso")
    return checkNotNull(backend) { "Il documento non è aperto: chiamare start()" }
  }

  private suspend fun <R> onThread(block: () -> R): R {
    if (closed) throw CancellationException("Il documento è chiuso")
    return try {
      withContext(thread) {
        coroutineContext.ensureActive()
        block()
      }
    } catch (e: java.util.concurrent.RejectedExecutionException) {
      throw CancellationException("Il documento è chiuso").apply { initCause(e) }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
    try {
      executor.execute { finish() }
    } catch (_: java.util.concurrent.RejectedExecutionException) {
      // Già fermo: start() è fallito e ha chiuso da sé.
    }
    executor.shutdown()
  }

  /** Vero quando il documento è chiuso e le sue risorse rilasciate. Per i test. */
  internal val isReleased: Boolean get() = released.get()

  private fun finish() {
    try {
      backend?.close()
    } finally {
      backend = null
      pages.clear()
      previews.clear()
      pageBytes = 0
      if (released.compareAndSet(false, true)) release()
    }
  }

  companion object {
    /** Le pagine intere in cache: quella sullo schermo, le due vicine e una di margine. A occhio, 22 novembre 2026. */
    const val DefaultMaxPages: Int = 4
    /** Il tetto in byte delle pagine intere: quattro pagine A4 a 2560 px di altezza. A occhio, 22 novembre 2026. */
    const val DefaultMaxBytes: Long = 64L * 1024 * 1024
    /** Le anteprime in cache: un foglio di miniature pieno. A occhio, 22 novembre 2026. */
    const val DefaultMaxPreviews: Int = 32
    /** Il lato lungo di un'anteprima, in pixel. A occhio, 22 novembre 2026. */
    const val DefaultPreviewLongSide: Int = 256
    /** La pagina più grande che si rende: 12 MP (48 MB), oltre si riduce. A occhio, 22 novembre 2026. */
    const val DefaultMaxPixels: Long = 12_000_000
  }
}
