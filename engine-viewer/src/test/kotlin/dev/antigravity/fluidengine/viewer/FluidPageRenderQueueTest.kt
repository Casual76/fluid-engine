package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageRead
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Il nucleo del sorgente PDF con un documento finto: un thread solo, una pagina aperta per volta, le due cache,
 * l'annullamento e la chiusura delle risorse. Il `PdfRenderer` vero sta dietro la stessa interfaccia.
 */
class FluidPageRenderQueueTest {
  private val policy = TestViewerPolicy()
  private val memory = ViewerMemory(policy)


  private class TestViewerPolicy : ViewerPolicy {
    override val budget = dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget(
      dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits.Full)
    override val uiVisible = kotlinx.coroutines.flow.MutableStateFlow(true)
    override val prefetchAllowed = kotlinx.coroutines.flow.MutableStateFlow(true)
    override var trimGeneration = 0L
    override val thumbnailParallelism = 3
    override fun refreshBeforeAllocation() = Unit
    override fun visibleBudgetDenied(denied: Boolean) = Unit
  }

  private class Page(val index: Int, val size: IntSize)

  private class FakeDocument(
    private val sizes: List<IntSize> = List(12) { IntSize(595, 842) },
    var gate: CountDownLatch? = null,
    val entered: CountDownLatch = CountDownLatch(1),
  ) : FluidPageBackend<Page> {
    val open = AtomicInteger(0)
    val maxOpen = AtomicInteger(0)
    val threads: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    val rendered: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    val sizeReads = AtomicInteger(0)
    @Volatile var closed = false

    var onRender: (IntSize) -> Unit = {}
    override val pageCount: Int get() = sizes.size

    private fun <R> page(block: () -> R): R {
      check(!closed) { "documento chiuso" }
      val now = open.incrementAndGet()
      maxOpen.accumulateAndGet(now) { a, b -> maxOf(a, b) }
      // Il nome senza il suffisso «@coroutine#N» che le coroutine aggiungono in modalità di debug: il thread è lo stesso.
      threads += Thread.currentThread().name.substringBefore(" @")
      try {
        return block()
      } finally {
        open.decrementAndGet()
      }
    }

    override fun pageSize(index: Int): IntSize = page {
      sizeReads.incrementAndGet()
      sizes[index]
    }

    override fun render(index: Int, target: IntSize): Page = page {
      onRender(target)
      entered.countDown()
      gate?.await(5, TimeUnit.SECONDS)
      rendered += index
      Page(index, target)
    }

    override fun close() {
      closed = true
    }
  }

  private fun queue(document: FakeDocument, released: AtomicInteger = AtomicInteger(), maxPages: Int = 4, maxBytes: Long = Long.MAX_VALUE) =
    FluidPageRenderQueue(
      memory = memory,
      open = { document.apply { closed = false } },
      bytesOf = { it.size.width.toLong() * it.size.height * 4 },
      maxPages = maxPages,
      maxBytes = maxBytes,
      maxPreviews = 3,
      previewLongSide = 256,
      maxPixels = 4_000_000,
      threadName = "FluidPdfTest",
      release = { released.incrementAndGet() },
    )

  /** Aspetta che il thread del documento abbia finito ciò che ha in coda. */
  private fun awaitReleased(released: AtomicInteger) {
    val deadline = System.currentTimeMillis() + 5_000
    while (released.get() == 0 && System.currentTimeMillis() < deadline) Thread.sleep(5)
  }

  @Test
  fun `the document opens on its thread and sizes are read once`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document)
    assertEquals(0, queue.pageCount)
    assertEquals(12, queue.start())
    assertEquals(12, queue.pageCount)
    assertEquals(IntSize(595, 842), queue.size(3))
    assertEquals(IntSize(595, 842), queue.size(3))
    assertEquals(1, document.sizeReads.get())
    queue.close()
  }

  @Test
  fun `a page served twice is rendered once`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document).apply { start() }
    val first = queue.render(0, IntSize(1000, 1414))
    val second = queue.render(0, IntSize(1000, 1414))
    assertTrue(first === second)
    assertEquals(1, queue.renders)
    // Un'altra misura è un'altra resa.
    queue.render(0, IntSize(500, 707))
    assertEquals(2, queue.renders)
    queue.close()
  }

  @Test
  fun `the page cache keeps the most recent few pages`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document, maxPages = 3).apply { start() }
    for (i in 0 until 5) queue.render(i, IntSize(1000, 1414))
    assertEquals(3 to 0, queue.cached())
    // Le pagine 2, 3, 4 sono in cache; la 0 è uscita e si rende di nuovo.
    queue.render(4, IntSize(1000, 1414))
    assertEquals(5, queue.renders)
    queue.render(0, IntSize(1000, 1414))
    assertEquals(6, queue.renders)
    queue.close()
  }

  @Test
  fun `the byte budget evicts before the page count`() = runBlocking {
    val document = FakeDocument()
    val page = 1000L * 1414 * 4
    val queue = queue(document, maxPages = 10, maxBytes = page * 2).apply { start() }
    for (i in 0 until 4) queue.render(i, IntSize(1000, 1414))
    assertEquals(2 to 0, queue.cached())
    queue.close()
  }

  @Test
  fun `previews are low resolution and have their own cache`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document).apply { start() }
    val preview = queue.preview(2)
    assertEquals(256, maxOf(preview.size.width, preview.size.height))
    assertEquals(IntSize(181, 256), preview.size)
    for (i in 0 until 5) queue.preview(i)
    queue.render(0, IntSize(1000, 1414))
    assertEquals(1 to 3, queue.cached())
    queue.close()
  }

  @Test
  fun `an oversized target is reduced to the pixel cap keeping the ratio`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document).apply { start() }
    val page = queue.render(0, IntSize(4000, 5656))
    assertTrue(page.size.width.toLong() * page.size.height <= 4_000_000)
    assertEquals(4000f / 5656, page.size.width.toFloat() / page.size.height, 0.01f)
    queue.close()
  }

  @Test
  fun `concurrent callers are served one page at a time on one thread`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document, maxPages = 12).apply { start() }
    (0 until 12).map { i -> async(Dispatchers.Default) { queue.render(i, IntSize(300 + i, 400)) } }.awaitAll()
    (0 until 12).map { i -> async(Dispatchers.Default) { queue.size(i) } }.awaitAll()
    assertEquals(1, document.maxOpen.get())
    assertEquals(setOf("FluidPdfTest"), document.threads.toSet())
    assertEquals(12, document.rendered.size)
    queue.close()
  }

  @Test
  fun `a call cancelled while waiting never opens its page`() = runBlocking {
    val gate = CountDownLatch(1)
    val document = FakeDocument(gate = gate)
    val queue = queue(document).apply { start() }
    val first = async(Dispatchers.Default) { queue.render(0, IntSize(100, 141)) }
    assertTrue(document.entered.await(5, TimeUnit.SECONDS))
    val waiting = launch(Dispatchers.Default) { queue.render(1, IntSize(100, 141)) }
    Thread.sleep(50)
    waiting.cancel()
    gate.countDown()
    first.await()
    waiting.join()
    queue.size(0) // un giro sul thread: tutto ciò che era in coda è passato
    assertEquals(listOf(0), document.rendered.toList())
    queue.close()
  }

  @Test
  fun `a render cancelled midway closes its page and is discarded`() = runBlocking {
    val gate = CountDownLatch(1)
    val document = FakeDocument(gate = gate)
    val queue = queue(document).apply { start() }
    val running = async(Dispatchers.Default) { queue.render(0, IntSize(400, 566)) }
    assertTrue(document.entered.await(5, TimeUnit.SECONDS))
    running.cancel()
    gate.countDown()
    try {
      running.await()
      fail("doveva essere annullata")
    } catch (_: CancellationException) {
    }
    queue.size(0)
    assertEquals(0, document.open.get())
    assertEquals(0 to 0, queue.cached())
    document.gate = null
    queue.render(0, IntSize(400, 566))
    assertEquals(2, queue.renders)
    queue.close()
  }

  @Test
  fun `close waits for the render in progress then releases once`() = runBlocking {
    val gate = CountDownLatch(1)
    val released = AtomicInteger()
    val document = FakeDocument(gate = gate)
    val queue = queue(document, released).apply { start() }
    val running = async(Dispatchers.Default) { queue.render(0, IntSize(100, 141)) }
    assertTrue(document.entered.await(5, TimeUnit.SECONDS))
    queue.close()
    queue.close()
    assertFalse(document.closed)
    gate.countDown()
    assertTrue(runCatching { running.await() }.exceptionOrNull() is CancellationException)
    awaitReleased(released)
    assertTrue(document.closed)
    assertEquals(1, released.get())
    assertTrue(queue.isReleased)
    try {
      queue.render(1, IntSize(100, 141))
      fail("dopo la chiusura le chiamate sono annullate")
    } catch (_: CancellationException) {
    }
  }

  @Test
  fun `calls queued behind close are cancelled, not rendered`() = runBlocking {
    val gate = CountDownLatch(1)
    val released = AtomicInteger()
    val document = FakeDocument(gate = gate)
    val queue = queue(document, released).apply { start() }
    val first = async(Dispatchers.Default) { queue.render(0, IntSize(100, 141)) }
    assertTrue(document.entered.await(5, TimeUnit.SECONDS))
    val queued = async(Dispatchers.Default) { runCatching { queue.render(1, IntSize(100, 141)) } }
    Thread.sleep(50)
    queue.close()
    gate.countDown()
    assertTrue(runCatching { first.await() }.exceptionOrNull() is CancellationException)
    assertTrue(queued.await().exceptionOrNull() is CancellationException)
    awaitReleased(released)
    assertEquals(listOf(0), document.rendered.toList())
  }

  @Test
  fun `evicting a cache-only page admits the visible page at full size`() = runBlocking {
    policy.budget.updateLimits(FluidMemoryLimits.Full.copy(viewerBytes = 2_300_000))
    val queue = queue(FakeDocument()).apply { start() }
    val target = IntSize(512, 512)
    try {
      repeat(2) { i -> FluidPageRead().use { reader ->
        assertEquals(target, withContext(reader) { queue.render(i, target) }.size)
      } }
      assertEquals(0L, policy.budget.usage().refusals)
      assertEquals(1 to 0, queue.cached())
    } finally { queue.close() }
    assertEquals(0L, policy.budget.usage().usedBytes)
  }

  @Test
  fun `three two or one thumbnail slots and visible ahead of queued prefetch`() = runBlocking {
    for (limit in 1..3) {
      val gate = ViewerDecodeGate { limit }
      val release = kotlinx.coroutines.CompletableDeferred<Unit>()
      val active = AtomicInteger()
      val entered = CountDownLatch(limit)
      val order = Collections.synchronizedList(mutableListOf<String>())
      val running = (1..limit).map {
        launch(Dispatchers.Default) {
          gate.run(false) {
            active.incrementAndGet()
            entered.countDown()
            release.await()
            active.decrementAndGet()
          }
        }
      }
      assertTrue(entered.await(5, TimeUnit.SECONDS))
      assertEquals(limit, active.get())
      val optional = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { gate.run(true) { order += "optional" } }
      val visible = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { gate.run(false) { order += "visible" } }
      assertTrue(order.isEmpty())
      release.complete(Unit)
      running.forEach { it.join() }
      visible.join(); optional.join()
      assertEquals(listOf("visible", "optional"), order)
    }
  }

  @Test
  fun `full page range survives eviction and a new render accounts for the old visible page`() = runBlocking {
    val target = IntSize(512, 512)
    val firstBytes = 512L * 512 * 4
    policy.budget.updateLimits(FluidMemoryLimits.Full.copy(viewerBytes = 2_300_000))
    val document = FakeDocument(sizes = List(12_000) { IntSize(595, 842) })
    val queue = queue(document)
    val visible = FluidPageRead()
    val next = FluidPageRead()
    try {
      assertEquals(12_000, queue.start())
      document.onRender = { size ->
        val usage = policy.budget.usage(FluidMemoryCategory.VIEWER)
        assertTrue(usage.inFlightBytes >= pixelPeak(size, 8))
      }
      withContext(visible) { queue.render(0, target) }
      document.onRender = { size ->
        assertEquals(firstBytes, policy.budget.usage(FluidMemoryCategory.VIEWER).residentBytes)
        assertEquals(IntSize(256, 256), size)
      }
      withContext(next) { queue.render(11_999, target) }
      assertEquals(2 to 0, queue.cached())
      visible.close()
      next.close()
      memory.trim(40)
      assertEquals(0L, policy.budget.usage().usedBytes)
      assertEquals(12_000, queue.pageCount)
      assertEquals(IntSize(595, 842), queue.size(11_999))
    } finally { visible.close(); next.close(); queue.close() }
  }

  @Test
  fun `optional native render holds credit until it really stops and never fills cache`() = runBlocking {
    val gate = CountDownLatch(1)
    val document = FakeDocument(gate = gate)
    val queue = queue(document).apply { start() }
    try {
      val prefetch = async(Dispatchers.Default) { queue.prefetch(2) }
      assertTrue(document.entered.await(5, TimeUnit.SECONDS))
      policy.prefetchAllowed.value = false
      assertTrue(policy.budget.usage().inFlightBytes > 0)
      gate.countDown()
      assertTrue(runCatching { prefetch.await() }.exceptionOrNull() is CancellationException)
      assertEquals(0 to 0, queue.cached())
      assertEquals(0L, policy.budget.usage().usedBytes)
      queue.prefetch(3)
      assertEquals(listOf(2), document.rendered.toList())
    } finally { gate.countDown(); queue.close() }
  }

  @Test
  fun `after hidden the first rebuilt page is visible even if prefetch resumes first`() = runBlocking {
    val document = FakeDocument()
    val queue = queue(document).apply { start() }
    try {
      queue.prefetch(1)
      policy.trimGeneration++
      memory.trim(20)
      assertEquals(0 to 0, queue.cached())
      queue.prefetch(2)
      assertEquals(listOf(1), document.rendered.toList())
      FluidPageRead().use { reader -> withContext(reader) { queue.render(7, IntSize(400, 600)) } }
      queue.prefetch(8)
      assertEquals(listOf(1, 7, 8), document.rendered.toList())
    } finally { queue.close() }
  }

  @Test
  fun `trim drops late native render and renderer failure returns its reservation`() = runBlocking {
    val gate = CountDownLatch(1)
    val document = FakeDocument(gate = gate)
    val queue = queue(document).apply { start() }
    try {
      val render = async(Dispatchers.Default) { queue.render(0, IntSize(200, 300)) }
      assertTrue(document.entered.await(5, TimeUnit.SECONDS))
      policy.trimGeneration++
      memory.trim(40)
      gate.countDown()
      assertTrue(runCatching { render.await() }.exceptionOrNull() is CancellationException)
      assertEquals(0L, policy.budget.usage().usedBytes)
      assertEquals(0 to 0, queue.cached())
      document.onRender = { throw IOException("page") }
      assertTrue(runCatching { queue.render(1, IntSize(200, 300)) }.exceptionOrNull() is IOException)
      assertEquals(0L, policy.budget.usage().usedBytes)
    } finally { gate.countDown(); queue.close() }
  }

  @Test
  fun `a document that fails to open releases its resources`() = runBlocking {
    val released = AtomicInteger()
    val queue = FluidPageRenderQueue<Page>(
      memory = memory,
      open = { throw IOException("non è un PDF") },
      bytesOf = { 0 },
      release = { released.incrementAndGet() },
    )
    try {
      queue.start()
      fail("doveva fallire")
    } catch (e: IOException) {
      assertEquals("non è un PDF", e.message)
    }
    assertEquals(1, released.get())
    queue.close()
    assertEquals(1, released.get())
    try {
      withTimeout(2_000) { queue.size(0) }
      fail("un documento mai aperto non risponde")
    } catch (_: CancellationException) {
    }
  }

  @Test
  fun `an open cancelled midway still releases on close`() = runBlocking {
    val released = AtomicInteger()
    val gate = CountDownLatch(1)
    val queue = FluidPageRenderQueue<Page>(
      memory = memory,
      open = { gate.await(5, TimeUnit.SECONDS); FakeDocument() },
      bytesOf = { 0 },
      release = { released.incrementAndGet() },
    )
    val opening = async(Dispatchers.Default) { queue.start() }
    Thread.sleep(50)
    opening.cancel()
    gate.countDown()
    runCatching { opening.await() }
    queue.close()
    awaitReleased(released)
    assertEquals(1, released.get())
  }
}
