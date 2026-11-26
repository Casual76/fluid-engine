package dev.antigravity.fluidengine.viewer.data

import android.content.Context
import android.os.ParcelFileDescriptor
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerOptions
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.ReadableByteChannel
import java.nio.channels.WritableByteChannel
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * R2 · I file malformati o costruiti apposta della famiglia «data»: una riga di CSV enorme, un CSV in UTF-16 oltre il
 * tetto delle copie, un XML e un plist annidati oltre il tetto della profondità, una voce di EPUB tronca o rovinata.
 * Ognuna è una prova rossa sul codice di e769ea0.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataHostileFilesTest {
  private lateinit var context: Context
  private lateinit var dir: File

  @Before fun setUp() {
    context = RuntimeEnvironment.getApplication()
    dir = File(context.cacheDir, "prove-r2").apply { mkdirs() }
  }

  @After fun tearDown() {
    dir.deleteRecursively()
  }

  // --- 1 · CSV: una riga enorme -------------------------------------------------------------------------------------

  @Test fun `a csv row of sixteen megabytes is read only at its start when the grid asks for it`() = runBlocking {
    val file = File(dir, "blob.csv")
    file.outputStream().buffered().use { out ->
      out.write("a,b\n\"".toByteArray())
      val chunk = ByteArray(64 * 1024) { 'x'.code.toByte() }
      repeat(256) { out.write(chunk) }
      out.write("\",2\nfine,3\n".toByteArray())
    }
    val channel = CountingChannel(RandomAccessFile(file, "r").channel)
    val grid = DataCsvGrid("blob.csv", channel, { FileInputStream(file) }, 0L, CsvDialect(','), Charsets.UTF_8,
      header = true, widths = emptyMap(), initialColumns = 2)
    try {
      grid.awaitIndex()
      assertEquals(3, grid.rowCount)
      val cells = grid.cells(0, 0..2, 0..1)
      // In memoria non arriva mai la riga intera: al più qualche MiB per lettura.
      assertTrue("lettura più grande: ${channel.largestRead} byte", channel.largestRead <= 4 * 1024 * 1024)
      val blob = cells.first { it.row == 1 && it.column == 0 }.text
      assertTrue(blob.length <= DataProfile.MaxCellChars + 1)
      assertTrue(blob.startsWith("xxxx") && blob.endsWith("…"))
      // La riga dopo si legge com'è.
      assertEquals("fine", cells.first { it.row == 2 && it.column == 0 }.text)
      assertEquals("3", cells.first { it.row == 2 && it.column == 1 }.text)
      assertEquals("a", cells.first { it.row == 0 && it.column == 0 }.text)
    } finally {
      grid.close()
    }
  }

  @Test fun `a csv field of millions of characters does not grow past the cell cap`() {
    val text = "a,\"" + "y".repeat(8_000_000) + "\",b\n"
    val probe = AllocationProbe()
    val before = probe.now()
    val rows = DataCsv.records(text, CsvDialect(','))
    val used = probe.now() - before
    assertEquals(1, rows.size)
    assertEquals(DataProfile.MaxCellChars + 1, rows[0][1].length)
    assertEquals("b", rows[0][2])
    assertTrue("allocati $used byte", used < 2_000_000)
  }

  // --- 2 · CSV in UTF-16: la trascrizione in cache ------------------------------------------------------------------

  @Test fun `a utf-16 csv beyond the copy cap is not transcribed whole and leaves nothing in the cache`() = runBlocking {
    val file = File(dir, "grande-utf16.csv")
    file.outputStream().buffered().use { out ->
      out.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
      out.write("id;nome;nota\r\n".toByteArray(Charsets.UTF_16LE))
      for (i in 0 until 20_000) out.write("$i;riga $i;una nota\r\n".toByteArray(Charsets.UTF_16LE))
    }
    val cache = File(context.cacheDir, "fluid-viewer-data")
    val before = cache.listFiles()?.size ?: 0
    val document = FluidViewerRegistry.open(context, FluidViewerSource.of(file), FluidViewerOptions(sourceMaxCopyBytes = 64 * 1024))
    try {
      // Oltre il tetto la famiglia rinuncia (il testo di serie lo mostra); la copia a metà non resta.
      assertFalse(document.content is FluidViewerContent.Grid)
    } finally {
      document.close()
    }
    assertEquals(before, cache.listFiles()?.size ?: 0)
  }

  @Test fun `the utf-16 transcription stops when the opening is cancelled`() = runBlocking {
    val readsAfterCancel = AtomicInteger(-1)
    val job = JobHolder()
    val source = object : FluidViewerSource {
      override val name = "lento.csv"
      override val mimeType = "text/csv"
      override suspend fun open(): InputStream = SlowUtf16(onRead = { reads ->
        if (reads == 40) {
          readsAfterCancel.set(0)
          job.get().cancel()
        } else if (readsAfterCancel.get() >= 0) {
          readsAfterCancel.incrementAndGet()
        }
      })
      override suspend fun seekable(): ParcelFileDescriptor = error("non serve")
    }
    val opening = launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
      try {
        FluidViewerRegistry.open(context, source).close()
      } catch (_: CancellationException) {
      }
    }
    job.set(opening)
    opening.start()
    withTimeout(30_000) { opening.join() }
    assertTrue("letture dopo l'annullamento: ${readsAfterCancel.get()}", readsAfterCancel.get() in 0..32)
  }

  // --- 3 · Alberi XML e plist: la profondità ------------------------------------------------------------------------

  @Test fun `an xml nested beyond the depth cap is an error with its line, and the tree stays shallow`() {
    val depth = 20_000
    val xml = "<r>" + "<a>".repeat(depth) + "</a>".repeat(depth) + "</r>"
    val tree = DataXml.parse(ByteArrayInputStream(xml.toByteArray()))
    assertTrue(tree.error?.message.orEmpty(), tree.error?.message.orEmpty().contains("profondità"))
    assertTrue("profondità ${tree.stats.maxDepth}", tree.stats.maxDepth <= DataProfile.MaxTreeDepth + 1)
    val root = tree.root!!
    // Le ricorsioni a valle («Testo», le righe con tutto aperto) restano entro il tetto.
    val open = HashSet<Int>()
    val queue = ArrayDeque(listOf(root))
    while (queue.isNotEmpty()) {
      val node = queue.removeFirst()
      open += node.id
      node.children?.let(queue::addAll)
    }
    assertTrue(DataTreeText.render(root).isNotEmpty())
    // Un nodo e la riga «…» del suo resto per livello.
    assertTrue(dataTreeRows(root, open, emptyMap()).size <= 2 * (DataProfile.MaxTreeDepth + 1))
  }

  @Test fun `a plist nested beyond the depth cap is an error, not a stack overflow`() {
    val depth = 100_000
    val plist = "<?xml version=\"1.0\"?><plist version=\"1.0\">" + "<array>".repeat(depth) + "</array>".repeat(depth) + "</plist>"
    val tree = DataXml.parse(ByteArrayInputStream(plist.toByteArray()))
    assertTrue(tree.error?.message.orEmpty(), tree.error?.message.orEmpty().contains("profondità"))
  }

  // --- 10 · EPUB: una voce tronca o con il CRC sbagliato ------------------------------------------------------------

  @Test fun `an epub entry with a wrong crc or cut short is an error, not half a chapter`() {
    val text = "Capitolo uno, con parole qualunque. ".repeat(3000).toByteArray()
    val good = epub(text)
    zipFile("sano.epub", good).use { zip ->
      assertTrue(zip.read("OEBPS/uno.xhtml")!!.contentEquals(text))
      assertTrue(zip.read("OEBPS/due.xhtml")!!.contentEquals(text))
    }
    // Un byte cambiato nella voce memorizzata: il CRC dell'indice non torna.
    val flipped = good.clone()
    val local = ByteBuffer.wrap(good).order(ByteOrder.LITTLE_ENDIAN)
    val dataStart = 30 + (local.getShort(26).toInt() and 0xFFFF) + (local.getShort(28).toInt() and 0xFFFF)
    flipped[dataStart + 100] = (flipped[dataStart + 100].toInt() xor 0x20).toByte()
    zipFile("cambiato.epub", flipped).use { zip ->
      assertThrows(IOException::class.java) { zip.read("OEBPS/uno.xhtml") }
      assertTrue(zip.read("OEBPS/due.xhtml")!!.contentEquals(text))
    }
    // La voce compressa tagliata a metà (l'indice dice metà dei byte compressi): il gonfiaggio si ferma prima.
    val cut = good.clone()
    val central = centralEntry(cut, "OEBPS/due.xhtml")
    val buffer = ByteBuffer.wrap(cut).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(central + 20, buffer.getInt(central + 20) / 2)
    zipFile("tagliato.epub", cut).use { zip ->
      assertThrows(IOException::class.java) { zip.read("OEBPS/due.xhtml") }
    }
  }

  private fun epub(text: ByteArray): ByteArray = ByteArrayOutputStream().also { bytes ->
    ZipOutputStream(bytes).use { zip ->
      val stored = ZipEntry("OEBPS/uno.xhtml").apply {
        method = ZipEntry.STORED
        size = text.size.toLong()
        compressedSize = text.size.toLong()
        crc = CRC32().apply { update(text) }.value
      }
      zip.putNextEntry(stored)
      zip.write(text)
      zip.closeEntry()
      zip.putNextEntry(ZipEntry("OEBPS/due.xhtml"))
      zip.write(text)
      zip.closeEntry()
    }
  }.toByteArray()

  private fun zipFile(name: String, bytes: ByteArray): DataZip {
    val file = File(dir, name).apply { writeBytes(bytes) }
    return DataZip(FileInputStream(file).channel)
  }

  /** Dove comincia la voce [name] nell'indice in fondo allo ZIP. */
  private fun centralEntry(bytes: ByteArray, name: String): Int {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    for (i in 0..bytes.size - 46) {
      if (buffer.getInt(i) != 0x02014b50) continue
      val length = buffer.getShort(i + 28).toInt() and 0xFFFF
      if (String(bytes, i + 46, length, Charsets.UTF_8) == name) return i
    }
    error("voce $name non trovata")
  }

  /** Un canale che ricorda la lettura posizionale più grande chiesta (la memoria che `cells` alloca in una volta). */
  private class CountingChannel(private val inner: FileChannel) : FileChannel() {
    @Volatile var largestRead = 0

    override fun read(dst: ByteBuffer, position: Long): Int {
      largestRead = maxOf(largestRead, dst.remaining())
      return inner.read(dst, position)
    }

    override fun read(dst: ByteBuffer): Int = inner.read(dst)
    override fun read(dsts: Array<out ByteBuffer>, offset: Int, length: Int): Long = inner.read(dsts, offset, length)
    override fun write(src: ByteBuffer): Int = inner.write(src)
    override fun write(srcs: Array<out ByteBuffer>, offset: Int, length: Int): Long = inner.write(srcs, offset, length)
    override fun write(src: ByteBuffer, position: Long): Int = inner.write(src, position)
    override fun position(): Long = inner.position()
    override fun position(newPosition: Long): FileChannel = apply { inner.position(newPosition) }
    override fun size(): Long = inner.size()
    override fun truncate(size: Long): FileChannel = apply { inner.truncate(size) }
    override fun force(metaData: Boolean) = inner.force(metaData)
    override fun transferTo(position: Long, count: Long, target: WritableByteChannel): Long = inner.transferTo(position, count, target)
    override fun transferFrom(src: ReadableByteChannel, position: Long, count: Long): Long = inner.transferFrom(src, position, count)
    override fun map(mode: FileChannel.MapMode, position: Long, size: Long): MappedByteBuffer = inner.map(mode, position, size)
    override fun lock(position: Long, size: Long, shared: Boolean): FileLock = inner.lock(position, size, shared)
    override fun tryLock(position: Long, size: Long, shared: Boolean): FileLock? = inner.tryLock(position, size, shared)
    override fun implCloseChannel() = inner.close()
  }

  /** Un CSV UTF-16 di 8 MB, dato 4 KB alla volta; [onRead] sente il numero di ogni lettura. */
  private class SlowUtf16(private val onRead: (Int) -> Unit) : InputStream() {
    private val row = "1;riga;una nota\r\n".toByteArray(Charsets.UTF_16LE)
    private var at = -2L
    private var reads = 0
    private val total = 8L * 1024 * 1024

    override fun read(): Int {
      val one = ByteArray(1)
      return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (at >= total) return -1
      onRead(++reads)
      val n = minOf(len, 4096, (total - at).toInt())
      for (i in 0 until n) {
        val p = at + i
        b[off + i] = when (p) {
          -2L -> 0xFF.toByte()
          -1L -> 0xFE.toByte()
          else -> row[(p % row.size).toInt()]
        }
      }
      at += n
      return n
    }
  }

  /** Il lavoro dell'apertura, dato al flusso dopo il lancio. */
  private class JobHolder {
    @Volatile private var job: Job? = null
    fun set(value: Job) { job = value }
    fun get(): Job = checkNotNull(job)
  }

  /** I byte allocati dal filo, dal contatore di HotSpot (java.lang.management non è nella classpath delle prove Android). */
  private class AllocationProbe {
    private val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    private val method = Class.forName("com.sun.management.ThreadMXBean")
      .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val thread = Thread.currentThread().id

    fun now(): Long = method.invoke(bean, thread) as Long
  }
}
