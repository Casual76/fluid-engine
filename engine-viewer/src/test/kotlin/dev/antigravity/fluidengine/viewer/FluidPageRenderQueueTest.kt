package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.unit.IntSize
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
      open = { document },
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
  fun `a render cancelled midway closes its page and is cached`() = runBlocking {
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
    assertEquals(1 to 0, queue.cached())
    document.gate = null
    queue.render(0, IntSize(400, 566))
    assertEquals(1, queue.renders)
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
    running.await() // la resa cominciata finisce
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
    first.await()
    assertTrue(queued.await().exceptionOrNull() is CancellationException)
    awaitReleased(released)
    assertEquals(listOf(0), document.rendered.toList())
  }

  @Test
  fun `a document that fails to open releases its resources`() = runBlocking {
    val released = AtomicInteger()
    val queue = FluidPageRenderQueue<Page>(
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
