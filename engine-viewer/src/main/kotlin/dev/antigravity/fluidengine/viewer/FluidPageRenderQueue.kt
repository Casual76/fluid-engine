package dev.antigravity.fluidengine.viewer

import android.content.ComponentCallbacks2
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageDisplay
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageRead
import kotlinx.coroutines.currentCoroutineContext
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
 * pagina di `PdfRenderer` non si interrompe a metà). Il risultato superato viene scartato prima della pubblicazione.
 * [close] chiude il documento sul suo thread dopo l'ultima resa in corso, e ferma il thread.
 */
internal class FluidPageRenderQueue<T : Any>(
  private val memory: ViewerMemory,
  private val physical: (T) -> Any = { it },
  private val discard: (T) -> Unit = {},
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
  /**
   * F8.18 · La pagina per lo schermo ([FluidPageDisplay] nel contesto di chi chiede): di serie la stessa resa. Il PDF
   * la porta nella memoria grafica sul thread del documento, così il primo disegno non carica la texture dentro il
   * fotogramma. Gira sul thread del documento, dentro la prenotazione della resa (che conta già due copie).
   */
  private val display: ((T) -> T)? = null,
) : Closeable {

  private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, threadName).apply { isDaemon = true } }
  private val thread: ExecutorCoroutineDispatcher = executor.asCoroutineDispatcher()

  // Tutto ciò che segue lo tocca solo il thread del documento.
  private var backend: FluidPageBackend<T>? = null
  private val sizes = LinkedHashMap<Int, IntSize>(16, 0.75f, true)
  private val pages = LinkedHashMap<Key, ViewerPixels<T>>(16, 0.75f, true)
  private val previews = LinkedHashMap<Key, ViewerPixels<T>>(16, 0.75f, true)
  private var pageBytes = 0L

  private val subscription = memory.watch(FluidMemoryCategory.VIEWER) { level ->
    if (level == null) {
      previews.entries.removeAll { (_, pixels) ->
        (!pixels.visible).also { if (it) pixels.close() }
      }
      pages.entries.removeAll { (_, pixels) ->
        if (pixels.visible) false else {
          pageBytes -= bytesOf(pixels.value)
          pixels.close()
          true
        }
      }
    } else clearImages()
    if (level != null && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && !closed) {
      executor.execute {
        backend?.close()
        backend = null
      }
    }
  }


  private fun clearImages() {
    pages.values.forEach { it.close() }
    previews.values.forEach { it.close() }
    pages.clear()
    previews.clear()
    pageBytes = 0
  }

  @Volatile private var closed = false
  private val released = java.util.concurrent.atomic.AtomicBoolean(false)

  /** Quante volte una pagina è stata davvero resa (non servita dalla cache). Per i test. */
  @Volatile var renders: Int = 0
    private set

  /** Quante rese per lo schermo sono state portate nella memoria grafica. Per i test e la diagnostica. */
  @Volatile var displayed: Int = 0
    private set

  private data class Key(val index: Int, val width: Int, val height: Int, val display: Boolean = false)

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
    pageCount = opened.pageCount
    opened.pageCount
  }

  /** Le pagine del documento, 0 finché [start] non è tornato. */
  @Volatile var pageCount: Int = 0
    private set

  suspend fun size(index: Int): IntSize = onThread {
    require(index in 0 until pageCount) { "Pagina $index fuori da 0 until $pageCount" }
    sizes[index] ?: run {
      memory.ensureCurrent(memory.resources.trimGeneration, false)
      document().pageSize(index).also {
        sizes[index] = it
        if (sizes.size > MaxSizes) sizes.remove(sizes.keys.first())
      }
    }
  }

  /**
   * La pagina [index] resa a [target] pixel. Un [target] oltre [maxPixels] si riduce mantenendo le proporzioni (la
   * bitmap è più piccola di quanto chiesto: il preset la scala). Un lato lungo entro [previewLongSide] va nella cache
   * delle anteprime.
   */
  suspend fun render(index: Int, target: IntSize): T {
    require(target.width > 0 && target.height > 0) { "La misura deve essere positiva: $target" }
    val size = capped(target)
    val caller = currentCoroutineContext()
    val reader = caller[FluidPageRead]
    val optional = caller[OptionalViewerWork] != null || reader?.optional == true
    val generation = memory.resources.trimGeneration
    val forDisplay = display != null && caller[FluidPageDisplay] != null
    val key = Key(index, size.width, size.height, forDisplay)
    val preview = maxOf(size.width, size.height) <= previewLongSide
    val cache = if (preview) previews else pages
    fun ready(): T? = synchronized(memory.lock) {
      if (closed) throw CancellationException("Il documento è chiuso")
      memory.ensureCurrent(generation, optional)
      cache[key]?.read(reader)
    }
    ready()?.let { return it }
    return memory.whileAllowed(optional) {
      memory.pdf.run(optional) {
        ready() ?: onThread {
          val document = document()
          require(index in 0 until pageCount) { "Pagina $index fuori da 0 until $pageCount" }
          memory.decode(FluidMemoryCategory.VIEWER, size, optional, { pixelPeak(it, 8) },
            { admitted ->
              val rendered = document.render(index, admitted).also { renders++ }
              if (forDisplay) display?.invoke(rendered)?.also { if (it !== rendered) displayed++ } ?: rendered else rendered
            }, bytesOf, physical, discard) { pixels ->
              if (closed) throw CancellationException("Il documento è chiuso")
              memory.ensureCurrent(generation, optional)
              val result = pixels.read(reader)
              cache[key] = pixels
              if (!preview) pageBytes += bytesOf(result)
              while (cache.isNotEmpty() && (cache.size > (if (preview) maxPreviews else maxPages) ||
                  !preview && pageBytes > maxBytes && cache.size > 1)) {
                val eldest = cache.remove(cache.keys.first())!!
                if (!preview) pageBytes -= bytesOf(eldest.value)
                eldest.close()
              }
              result
            } ?: noViewerMemory()
        }
      }
    }
  }

  /** L'anteprima della pagina [index]: lato lungo [previewLongSide], proporzioni della pagina. */
  suspend fun preview(index: Int): T {
    val page = size(index)
    val scale = previewLongSide.toFloat() / maxOf(page.width, page.height).coerceAtLeast(1)
    return render(index, IntSize((page.width * scale).roundToInt().coerceAtLeast(1), (page.height * scale).roundToInt().coerceAtLeast(1)))
  }

  suspend fun prefetch(index: Int) {
    if (!memory.mayPrefetch(FluidMemoryCategory.VIEWER)) return
    // Il proprietario temporaneo viene chiuso anche in caso di annullo nella consegna con cambio dispatcher.
    FluidPageRead().use { reader -> withContext(OptionalViewerWork() + reader) { preview(index) } }
  }

  /** Le pagine intere e le anteprime in cache adesso. Per i test. */
  internal suspend fun cached(): Pair<Int, Int> = synchronized(memory.lock) { pages.size to previews.size }

  internal fun capped(target: IntSize): IntSize {
    return viewerPixelSize(target.width, target.height, maxPixels)
  }

  private fun document(): FluidPageBackend<T> {
    if (closed) throw CancellationException("Il documento è chiuso")
    check(pageCount > 0 || backend != null) { "Il documento non è aperto: chiamare start()" }
    return backend ?: open().also { backend = it }
  }

  private suspend fun <R> onThread(block: suspend () -> R): R {
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
    synchronized(memory.lock) {
      if (closed) return
      closed = true
      clearImages()
      subscription.close()
    }
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
      synchronized(memory.lock) { clearImages(); subscription.close() }
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
    /**
     * F8.18 · Le misure delle pagine tenute: 256, perché un documento di migliaia di pagine si scorre avanti e indietro
     * con la maniglia (prima 64). Una misura è pochi byte; rimisurare apre la pagina nel renderer.
     */
    const val MaxSizes: Int = 256
  }
}

/** Anche un panorama largo un solo pixel resta nel numero ammesso dopo gli arrotondamenti. */
internal fun viewerPixelSize(width: Int, height: Int, maxPixels: Long): IntSize {
  require(width > 0 && height > 0 && maxPixels > 0)
  val pixels = width.toLong() * height
  if (pixels <= maxPixels) return IntSize(width, height)
  val scale = sqrt(maxPixels.toDouble() / pixels)
  val w = (width * scale).toInt().coerceAtLeast(1)
  val h = (height * scale).toInt().coerceAtLeast(1)
  return if (w >= h) IntSize(minOf(w.toLong(), maxPixels / h).toInt(), h)
    else IntSize(w, minOf(h.toLong(), maxPixels / w).toInt())
}
