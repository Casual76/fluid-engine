package dev.antigravity.fluidengine.viewer

import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * F8.18 · Il bilancio del PDF nella coda delle pagine: la resa per lo schermo ([FluidPageDisplay]) passa dalla
 * conversione (nel PDF vero, la memoria grafica) sul thread del documento, ha la sua voce in cache e non arriva mai a
 * chi rende per la stampa; le misure di migliaia di pagine restano (256) senza rileggere il documento.
 */
class FluidPageDisplayTest {
  private class Policy : ViewerPolicy {
    override val budget = FluidMemoryBudget(FluidMemoryLimits.Full)
    override val uiVisible = MutableStateFlow(true)
    override val prefetchAllowed = MutableStateFlow(true)
    override var trimGeneration = 0L
    override val thumbnailParallelism = 3
    override fun refreshBeforeAllocation() = Unit
    override fun visibleBudgetDenied(denied: Boolean) = Unit
  }

  private class Page(val index: Int, val size: IntSize, val shown: Boolean = false, val thread: String = "")

  private class Document(private val count: Int) : FluidPageBackend<Page> {
    val sizeReads = AtomicInteger()
    override val pageCount: Int get() = count
    override fun pageSize(index: Int): IntSize {
      sizeReads.incrementAndGet()
      return IntSize(595, 842)
    }
    override fun render(index: Int, target: IntSize) = Page(index, target)
    override fun close() = Unit
  }

  private val memory = ViewerMemory(Policy())

  private fun queue(document: Document) = FluidPageRenderQueue(
    memory = memory,
    open = { document },
    bytesOf = { it.size.width.toLong() * it.size.height * 4 },
    threadName = "FluidPdfDisplayTest",
    display = { page -> Page(page.index, page.size, shown = true, thread = Thread.currentThread().name.substringBefore(" @")) },
  )

  @Test
  fun `the screen render is converted on the document thread and printing never sees it`() = runBlocking {
    val queue = queue(Document(5000)).apply { start() }
    val shown = withContext(FluidPageDisplay) { queue.render(4321, IntSize(1080, 1527)) }
    assertTrue(shown.shown)
    assertEquals("FluidPdfDisplayTest", shown.thread)
    assertEquals(1, queue.displayed)
    // La stampa (nessun FluidPageDisplay nel contesto) riceve la pagina normale, resa a parte.
    val printed = queue.render(4321, IntSize(1080, 1527))
    assertFalse(printed.shown)
    assertEquals(2, queue.renders)
    // Di nuovo per lo schermo: dalla cache, senza rendere né convertire.
    assertTrue(withContext(FluidPageDisplay) { queue.render(4321, IntSize(1080, 1527)) } === shown)
    assertEquals(2, queue.renders)
    assertEquals(1, queue.displayed)
    queue.close()
  }

  @Test
  fun `a source without the conversion renders the same page for the screen`() = runBlocking {
    val document = Document(10)
    val queue = FluidPageRenderQueue(memory = memory, open = { document },
      bytesOf = { it.size.width.toLong() * it.size.height * 4 }, threadName = "FluidPdfPlain").apply { start() }
    val shown = withContext(FluidPageDisplay) { queue.render(3, IntSize(600, 848)) }
    assertFalse(shown.shown)
    assertEquals(0, queue.displayed)
    assertTrue(queue.render(3, IntSize(600, 848)) === shown)
    queue.close()
  }

  @Test
  fun `the sizes of a long document stay for the last 256 pages`() = runBlocking {
    val document = Document(5000)
    val queue = queue(document).apply { start() }
    for (i in 0 until 300) queue.size(i)
    assertEquals(300, document.sizeReads.get())
    // Le ultime 256 restano: tornare indietro di duecento pagine non rilegge il documento.
    for (i in 100 until 300) queue.size(i)
    assertEquals(300, document.sizeReads.get())
    queue.size(0)
    assertEquals(301, document.sizeReads.get())
    assertEquals(256, FluidPageRenderQueue.MaxSizes)
    queue.close()
  }
}
