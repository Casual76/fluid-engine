package dev.antigravity.fluidengine.viewer

import android.content.ComponentCallbacks2
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageRead
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.InputStream
import android.graphics.Bitmap
import java.lang.ref.WeakReference
import java.lang.ref.ReferenceQueue
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** Le API storiche consegnano anche Bitmap/Drawable senza close. L'ultimo riferimento Java protegge i loro byte. */
private object LegacyPixelReaders {
  private val queue = ReferenceQueue<Any>()
  private val references = HashSet<Reader>()
  private class Reader(value: Any, val credit: Closeable) : WeakReference<Any>(value, queue)
  init {
    Thread({
      while (true) {
        val done = queue.remove() as Reader
        synchronized(references) { references.remove(done) }
        done.credit.close()
      }
    }, "FluidPixelReaders").apply { isDaemon = true; start() }
  }
  // Anche un client storico può riciclare esplicitamente la sua bitmap prima del GC.
  fun reapReleased(): Boolean = synchronized(references) {
    var freed = false
    val iterator = references.iterator()
    while (iterator.hasNext()) {
      val entry = iterator.next()
      val value = entry.get()
      if (value == null || value is Bitmap && value.isRecycled) {
        iterator.remove()
        entry.credit.close()
        freed = true
      }
    }
    freed
  }

  fun keep(value: Any, credit: Closeable) {
    synchronized(references) { references.add(Reader(value, credit)) }
  }
}

/** Una sola spesa fisica. Le mani della cache, della composizione e dei lettori storici sono indipendenti. */
internal class ViewerPixels<T : Any>(
  val value: T, private val physical: Any, private val owner: FluidMemoryBudget.Resident,
  private val readers: java.util.concurrent.atomic.AtomicInteger = java.util.concurrent.atomic.AtomicInteger(),
) : Closeable {
  private var legacyReader = false
  val visible get() = readers.get() > 0
  fun retain() = ViewerPixels(value, physical, owner.retain(), readers)
  fun read(reader: FluidPageRead?): T {
    if (reader != null) reader.take(owner.retain(), readers)
    else if (!legacyReader) {
      LegacyPixelReaders.keep(physical, owner.retain())
      legacyReader = true
    }
    return value
  }
  override fun close() = owner.close()
}

internal val ImageBitmap.physical: Any get() = try { asAndroidBitmap() } catch (_: RuntimeException) { this }
internal val ImageBitmap.pixelBytes: Long get() {
  val measured = try { (physical as? Bitmap)?.allocationByteCount?.toLong() } catch (_: RuntimeException) { null }
  return measured?.takeIf { it > 0 } ?: pixelPeak(IntSize(width, height), 8, 0)
}

internal class ThumbnailAllocation(val edge: Int) : AbstractCoroutineContextElement(Key), Closeable {
  var denied = false
  private val temporaries = ArrayList<Closeable>()
  @Synchronized fun hold(credit: Closeable) { temporaries += credit }
  override fun close() { temporaries.forEach { it.close() }; temporaries.clear() }
  companion object Key : CoroutineContext.Key<ThumbnailAllocation>
}

internal class OptionalViewerWork : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<OptionalViewerWork>
}

internal interface ViewerPolicy {
  val budget: FluidMemoryBudget
  val uiVisible: StateFlow<Boolean>
  val prefetchAllowed: StateFlow<Boolean>
  val trimGeneration: Long
  val thumbnailParallelism: Int
  /** F8.18b: le pagine per lo schermo nella memoria grafica, secondo la fascia del dispositivo. Di serie no. */
  val hardwarePages: Boolean get() = false
  fun refreshBeforeAllocation()
  fun visibleBudgetDenied(denied: Boolean)
}

private class ProcessViewerPolicy(val process: FluidGlassResources) : ViewerPolicy {
  override val budget get() = process.budget
  override val uiVisible get() = process.uiVisible
  override val prefetchAllowed get() = process.prefetchAllowed
  override val trimGeneration get() = process.trimGeneration
  override val thumbnailParallelism get() = process.thumbnailParallelism
  override val hardwarePages get() = process.viewerHardwarePages
  override fun refreshBeforeAllocation() = process.refreshBeforeAllocation()
  override fun visibleBudgetDenied(denied: Boolean) = process.visibleBudgetDenied(null, denied)
}

internal class ViewerMemory(val resources: ViewerPolicy) {
  // Invalidazione, inserimento e acquisizione del lettore condividono questo lock in tutti i visori.
  val lock = Any()
  private val caches = LinkedHashMap<(Int?) -> Unit, FluidMemoryCategory>()
  val thumbnails = ViewerDecodeGate { resources.thumbnailParallelism }
  val pdf = ViewerDecodeGate { 1 }
  private val waitingForVisible = java.util.EnumSet.noneOf(FluidMemoryCategory::class.java)

  fun trim(level: Int) {
    if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) synchronized(lock) {
      if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
        waitingForVisible.add(FluidMemoryCategory.THUMBNAILS)
        waitingForVisible.add(FluidMemoryCategory.VIEWER)
      }
      caches.keys.toList().forEach { it(level) }
    }
  }

  fun watch(category: FluidMemoryCategory, onEvict: (trimLevel: Int?) -> Unit): Closeable = synchronized(lock) {
    caches[onEvict] = category
    Closeable { synchronized(lock) { caches.remove(onEvict) } }
  }

  fun mayPrefetch(category: FluidMemoryCategory): Boolean = synchronized(lock) {
    resources.prefetchAllowed.value && category !in waitingForVisible
  }

  fun ensureCurrent(generation: Long, optional: Boolean) {
    if (generation != resources.trimGeneration || !resources.uiVisible.value ||
      optional && !resources.prefetchAllowed.value) throw CancellationException("Lettura superata")
  }

  fun reserve(category: FluidMemoryCategory, bytes: Long, optional: Boolean): FluidMemoryBudget.Reservation? {
    resources.refreshBeforeAllocation()
    val process = resources.budget.usage()
    val group = resources.budget.usage(category)
    val processShort = bytes > process.limitBytes - process.usedBytes
    val groupShort = bytes > group.limitBytes - group.usedBytes
    // Prima togli le copie ricostruibili: una LRU piena non deve declassare il profilo del telefono.
    if ((processShort || groupShort) && bytes <= process.limitBytes && bytes <= group.limitBytes) {
      LegacyPixelReaders.reapReleased()
      synchronized(lock) {
        caches.filterValues { processShort || it == category }.keys.forEach { it(null) }
      }
    }
    val pending = resources.budget.tryReserve(category, bytes)
    if (pending == null && !optional) resources.visibleBudgetDenied(true)
    return pending
  }

  suspend fun <T> whileAllowed(optional: Boolean, block: suspend () -> T): T = coroutineScope {
    val flow = if (optional) resources.prefetchAllowed else resources.uiVisible
    if (!flow.value) throw CancellationException("Lettura sospesa")
    val worker = coroutineContext[Job]!!
    val stop = launch(start = CoroutineStart.UNDISPATCHED) {
      flow.first { !it }
      worker.cancel(CancellationException("Lettura sospesa"))
    }
    try { block() } finally { stop.cancel() }
  }

  /** Il produttore mantiene la prenotazione fino al ritorno effettivo del decoder, anche se annullato. */
  suspend fun <T : Any, R> decode(
    category: FluidMemoryCategory, target: IntSize, optional: Boolean,
    peak: (IntSize) -> Long, decode: suspend (IntSize) -> T?,
    bytes: (T) -> Long, physical: (T) -> Any, discard: (T) -> Unit = {},
    minSide: Int = 1, retryEmpty: () -> Boolean = { false }, publish: (ViewerPixels<T>) -> R,
  ): R? {
    val generation = resources.trimGeneration
    val caller = currentCoroutineContext()
    var size = target
    while (true) {
      caller.ensureActive()
      ensureCurrent(generation, optional)
      if (optional && !mayPrefetch(category)) throw CancellationException("In attesa del visibile")
      val pending = reserve(category, peak(size), optional)
      if (pending != null) pending.use { work ->
        if (!optional) resources.visibleBudgetDenied(false)
        val value = decode(size)
        if (value == null) {
          if (retryEmpty()) return@use
          return null
        }
        var installed = false
        try {
          synchronized(lock) {
            caller.ensureActive()
            ensureCurrent(generation, optional)
            val owner = work.confirm(bytes(value))
            if (owner != null) {
              try {
                val result = publish(ViewerPixels(value, physical(value), owner))
                installed = true
                if (!optional) waitingForVisible.remove(category)
                return result
              } finally { if (!installed) owner.close() }
            }
          }
        } finally { if (!installed) discard(value) }
      }
      if (size.width <= minSide && size.height <= minSide) return null
      size = IntSize(maxOf(minSide.coerceAtMost(size.width), size.width / 2),
        maxOf(minSide.coerceAtMost(size.height), size.height / 2))
    }
  }

  companion object {
    @Volatile private var instance: ViewerMemory? = null
    fun get(context: Context? = null): ViewerMemory {
      val process = context?.let(FluidGlassResources::get) ?: FluidGlassResources.current()
      val current = instance
      if ((current?.resources as? ProcessViewerPolicy)?.process === process) return current!!
      return synchronized(this) {
        val previous = instance
        if ((previous?.resources as? ProcessViewerPolicy)?.process === process) previous!!
        else ViewerMemory(ProcessViewerPolicy(process)).also { memory ->
          val install = Runnable { process.observeTrim(memory::trim) }
          if (Looper.myLooper() == Looper.getMainLooper()) install.run() else Handler(Looper.getMainLooper()).post(install)
          instance = memory
        }
      }
    }
  }
}

/** Il visibile già in attesa passa prima dei vicini. I lavori nativi in corso conservano il posto fino alla fine. */
internal class ViewerDecodeGate(private val limit: () -> Int) {
  private val changes = MutableStateFlow(0L)
  private var active = 0
  private var visibleWaiting = 0
  suspend fun <T> run(optional: Boolean, block: suspend () -> T): T {
    var acquired = false
    synchronized(this) { if (!optional) visibleWaiting++ }
    try {
      while (!acquired) {
        val version = synchronized(this) {
          if (active < limit() && (!optional || visibleWaiting == 0)) {
            active++
            if (!optional) visibleWaiting--
            acquired = true
          }
          changes.value
        }
        if (!acquired) changes.first { it != version }
      }
      return block()
    } finally {
      synchronized(this) {
        if (acquired) active-- else if (!optional) visibleWaiting--
        changes.value++
      }
    }
  }
}

// Riga arrotondata a 64 byte, più un buffer di I/O. RGBA8: quattro byte, FP16: otto.
internal fun pixelPeak(size: IntSize, bytesPerPixel: Int, scratch: Long = 64L * 1024): Long {
  val row = (size.width.toLong() * bytesPerPixel + 63) / 64 * 64
  if (row > (Long.MAX_VALUE - scratch) / size.height) return Long.MAX_VALUE
  return row * size.height + scratch
}

internal fun noViewerMemory(): Nothing =
  throw FluidViewerException(FluidViewerFailure.TooLarge, "Memoria disponibile insufficiente per questa anteprima")

/** Adattatore dei moduli di famiglia per una superficie a misura riducibile e il suo scratch simultaneo. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
object FluidViewerRaster {
  /** Anche le famiglie invocate direttamente usano gli stessi posti e la stessa quota delle tessere del registro. */
  suspend fun thumbnail(context: Context, side: Int, draw: suspend (Int) -> ImageBitmap?): ImageBitmap? {
    if (side <= 0) return null
    val caller = currentCoroutineContext()
    caller[ThumbnailAllocation]?.let { return draw(minOf(side, it.edge)) }
    val memory = ViewerMemory.get(context)
    val optional = caller[OptionalViewerWork] != null || caller[FluidPageRead]?.optional == true
    val edge = FluidThumbnailPainter.clampSize(side)
    return memory.whileAllowed(optional) {
      memory.resources.refreshBeforeAllocation()
      memory.thumbnails.run(optional) {
        memory.decode(FluidMemoryCategory.THUMBNAILS, IntSize(edge, edge), optional, { pixelPeak(it, 40) },
          { size -> if (size.width < FluidThumbnailPainter.MinSizePx) null else ThumbnailAllocation(size.width).use {
            kotlinx.coroutines.withContext(it) { draw(size.width) }
          } }, { it.pixelBytes }, { it.physical }, minSide = FluidThumbnailPainter.MinSizePx) { pixels -> pixels.use { it.read(caller[FluidPageRead]) } }
      }
    }
  }

  /** Prenota scratch aggiuntivo di una famiglia fino al ritorno dal decoder della miniatura. */
  suspend fun thumbnailScratch(bytes: Long): (Long) -> Boolean {
    val caller = currentCoroutineContext()
    val memory = ViewerMemory.get()
    val scope = checkNotNull(caller[ThumbnailAllocation])
    val optional = caller[OptionalViewerWork] != null || caller[FluidPageRead]?.optional == true
    fun reserve(size: Long): Boolean {
      caller.ensureActive()
      if (size == 0L) return true
      val work = memory.reserve(FluidMemoryCategory.THUMBNAILS, size, optional)
      if (work == null) { scope.denied = true; return false }
      scope.hold(work)
      return true
    }
    if (!reserve(bytes)) noViewerMemory()
    return ::reserve
  }

  /** Invalida le cache di famiglia su trim o quando un decoder deve fare spazio al visibile. */
  fun observeCachePressure(context: Context, evict: () -> Unit): Closeable =
    ViewerMemory.get(context).watch(FluidMemoryCategory.THUMBNAILS) { evict() }

  /** Copia in RAM una sorgente a lunghezza incerta. Prenota blocchi e array finale mentre entrambi esistono. */
  suspend fun readBytes(input: InputStream, limit: Int): ByteArray {
    val memory = ViewerMemory.get()
    val caller = currentCoroutineContext()
    val optional = caller[OptionalViewerWork] != null || caller[FluidPageRead]?.optional == true
    val category = if (caller[ThumbnailAllocation] == null) FluidMemoryCategory.VIEWER else FluidMemoryCategory.THUMBNAILS
    fun book(bytes: Int): FluidMemoryBudget.Reservation {
      val pending = memory.reserve(category, bytes.toLong(), optional)
      if (pending == null) {
        caller[ThumbnailAllocation]?.denied = true
        noViewerMemory()
      }
      return pending
    }
    val generation = memory.resources.trimGeneration
    val parts = ArrayList<Pair<ByteArray, Int>>()
    val credits = ArrayList<FluidMemoryBudget.Reservation>()
    var total = 0
    try {
      while (true) {
        caller.ensureActive()
        memory.ensureCurrent(generation, optional)
        val first = input.read()
        if (first < 0) break
        if (total >= limit) noViewerMemory()
        val count = minOf(32768, limit - total)
        credits += book(count)
        val chunk = ByteArray(count)
        chunk[0] = first.toByte()
        var used = 1
        while (used < count) {
          val n = input.read(chunk, used, count - used)
          if (n <= 0) break
          used += n
        }
        parts += chunk to used
        total += used
      }
      if (total == 0) return ByteArray(0)
      val pending = book(total)
      return pending.use {
        val result = ByteArray(total)
        var offset = 0
        for ((chunk, used) in parts) {
          chunk.copyInto(result, offset, 0, used)
          offset += used
        }
        memory.ensureCurrent(generation, optional)
        val owner = checkNotNull(it.confirm())
        val thumbnail = caller[ThumbnailAllocation]
        if (thumbnail == null) LegacyPixelReaders.keep(result, owner) else thumbnail.hold(owner)
        result
      }
    } finally {
      parts.clear()
      credits.forEach { it.close() }
    }
  }

  /** Il sorgente riceve la misura ammessa prima di creare pixel. Lo scratch fisso include strisce o buffer del formato. */
  suspend fun render(context: Context?, target: IntSize, bytesPerPixel: Int = 8, scratch: Long = 65536L,
    draw: suspend (IntSize) -> ImageBitmap?): ImageBitmap? {
    val memory = ViewerMemory.get(context)
    val caller = currentCoroutineContext()
    val optional = caller[OptionalViewerWork] != null || caller[FluidPageRead]?.optional == true
    val thumbnail = caller[ThumbnailAllocation]
    if (thumbnail != null) {
      // L'output rientra nella prenotazione della tessera. Il formato sa quanto scratch fisso aggiungere.
      val extra = (scratch - 65536L).coerceAtLeast(0)
      if (extra == 0L) return draw(target)
      val reservation = memory.reserve(FluidMemoryCategory.THUMBNAILS, extra, optional)
      if (reservation == null) { thumbnail.denied = true; return null }
      return reservation.use { draw(target) }
    }
    return memory.whileAllowed(optional) {
      memory.decode(FluidMemoryCategory.VIEWER, target, optional, { pixelPeak(it, bytesPerPixel, scratch) }, draw,
        { it.pixelBytes }, { it.physical }, { (it.physical as? Bitmap)?.recycle() }) {
        it.use { pixels -> pixels.read(caller[FluidPageRead]) }
      }
    }
  }
}
