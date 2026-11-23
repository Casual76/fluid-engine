package dev.antigravity.fluidengine.viewer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * F6C.8 · (5) `FluidViewerSource` per i file che non si possono spostare: la copia ricercabile in cache si fa una volta,
 * e tutto si chiude anche dopo un annullamento o un errore. Il descrittore vero (`ParcelFileDescriptor`) e un `Uri` a
 * pipe si provano sul dispositivo (`FluidViewerSourceDeviceTest`); qui la copia, il flusso e il canale.
 */
class FluidViewerSourceTest {

  private fun cache(): File = Files.createTempDirectory("f6c8-cache").toFile()

  /** Un flusso che dà [size] byte a pezzi di [chunk], con una pausa, e dice quando è stato chiuso. */
  private class SlowStream(private val size: Int, private val chunk: Int = 1024, private val pauseMs: Long = 0) : InputStream() {
    @Volatile var closed = false
    @Volatile var position = 0
    val started = CompletableDeferred<Unit>()

    override fun read(): Int {
      val one = ByteArray(1)
      return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (closed) throw java.io.IOException("chiuso")
      started.complete(Unit)
      if (position >= size) return -1
      if (pauseMs > 0) Thread.sleep(pauseMs)
      val n = minOf(len, chunk, size - position)
      for (i in 0 until n) b[off + i] = ((position + i) % 251).toByte()
      position += n
      return n
    }

    override fun close() {
      closed = true
    }
  }

  @Test
  fun `the copy is made once, shared by concurrent callers, and deleted on close`() = runBlocking {
    val dir = cache()
    val reads = AtomicInteger()
    val copy = FluidSeekableCopy(dir, 1L shl 20, "flusso") { reads.incrementAndGet(); SlowStream(200_000) }
    val files = (1..8).map { async(Dispatchers.Default) { copy.file() } }.awaitAll()
    assertEquals("una copia sola", 1, reads.get())
    assertEquals(1, files.toSet().size)
    val file = files.first()
    assertEquals(200_000L, file.length())
    assertEquals(listOf(file.name), dir.list()?.toList())
    copy.close()
    assertFalse(file.exists())
    assertEquals(emptyList<String>(), dir.list()?.toList())
    try {
      copy.file()
      fail("dopo la chiusura")
    } catch (e: FluidViewerException) {
      assertEquals(FluidViewerFailure.Unreadable, e.failure)
    }
  }

  @Test
  fun `a cancelled copy closes the stream and leaves no file`() = runBlocking {
    val dir = cache()
    val stream = SlowStream(50_000_000, chunk = 4096, pauseMs = 2)
    val copy = FluidSeekableCopy(dir, Long.MAX_VALUE, "lento") { stream }
    val job = launch(Dispatchers.Default) { copy.file() }
    withTimeout(5_000) { stream.started.await() }
    job.cancelAndJoin()
    assertTrue("il flusso è chiuso", stream.closed)
    assertEquals("niente copie a metà", emptyList<String>(), dir.list()?.toList())
    assertNull(copy.current)
    // La sorgente resta usabile: un'altra richiesta rifà la copia da capo.
    val again = FluidSeekableCopy(dir, Long.MAX_VALUE, "di nuovo") { SlowStream(10_000) }
    assertEquals(10_000L, again.file().length())
    again.close()
  }

  @Test
  fun `closing the source while it copies stops the copy and leaves nothing`() = runBlocking {
    val dir = cache()
    val stream = SlowStream(50_000_000, chunk = 4096, pauseMs = 2)
    val copy = FluidSeekableCopy(dir, Long.MAX_VALUE, "lento") { stream }
    val result = async(Dispatchers.Default) { runCatching { copy.file() } }
    withTimeout(5_000) { stream.started.await() }
    copy.close()
    val outcome = withTimeout(10_000) { result.await() }
    val error = outcome.exceptionOrNull()
    assertTrue("la copia si ferma con un errore, non finisce: $outcome", error is FluidViewerException)
    assertEquals(FluidViewerFailure.Unreadable, (error as FluidViewerException).failure)
    assertTrue(stream.closed)
    assertEquals(emptyList<String>(), dir.list()?.toList())
  }

  @Test
  fun `a stream over the cap is too large and leaves nothing`() = runBlocking {
    val dir = cache()
    val stream = SlowStream(100_000)
    val copy = FluidSeekableCopy(dir, 10_000, "grande") { stream }
    try {
      copy.file()
      fail("oltre il tetto")
    } catch (e: FluidViewerException) {
      assertEquals(FluidViewerFailure.TooLarge, e.failure)
    }
    assertTrue(stream.closed)
    assertEquals(emptyList<String>(), dir.list()?.toList())
  }

  @Test
  fun `a stream source reads from the start, copies once for seeking, then reads the copy`() = runBlocking {
    val dir = cache()
    val bytes = Random(8).nextBytes(70_000)
    val reads = AtomicInteger()
    val source = FluidViewerSource.of("voce.bin", dir, sizeBytes = bytes.size.toLong()) { reads.incrementAndGet(); bytes.inputStream() }
    assertEquals("voce.bin", source.name)
    assertEquals(70_000L, source.sizeBytes)
    assertArrayEquals(bytes, source.open().use { it.readBytes() })
    assertEquals(1, reads.get())
    // Il canale (lo usa l'indice dello ZIP) passa dalla copia.
    val channel = source.channel()
    assertEquals(70_000L, channel.size())
    channel.close()
    assertEquals(2, reads.get())
    assertArrayEquals("dopo la copia si legge la copia", bytes, source.open().use { it.readBytes() })
    assertEquals(2, reads.get())
    assertEquals(1, dir.list()?.size)
    source.close()
    assertEquals(emptyList<String>(), dir.list()?.toList())
  }

  @Test
  fun `the index of a zip that comes as a stream is read through the copy, which goes away with the source`() = runBlocking {
    val dir = cache()
    val out = java.io.ByteArrayOutputStream()
    ZipOutputStream(out).use { z ->
      for (name in listOf("mimetype", "content.xml", "Pictures/a.png")) {
        z.putNextEntry(ZipEntry(name))
        z.write(name.toByteArray())
        z.closeEntry()
      }
    }
    val source = FluidViewerSource.of("documento.odt", dir) { out.toByteArray().inputStream() }
    val families = listOf(object : FluidViewerFamily {
      override val id = "office"
      override val types = listOf(FluidViewerFamilyType("odt", "Documento ODF", setOf("odt"), signature = FluidViewerSignature.zipEntry("content.xml")))
      override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? = null
    })
    val probe = FluidViewerProbe.read(source, families)
    assertEquals(setOf("mimetype", "content.xml", "Pictures/a.png"), probe.entries)
    assertNotNull(probe.header)
    assertTrue(FluidZipDirectory.looksLikeZip(probe.header))
    assertEquals(1, dir.list()?.size)
    source.close()
    assertEquals(emptyList<String>(), dir.list()?.toList())
  }

  @Test
  fun `a file source opens the file, and deletes it on close only when asked`() = runBlocking {
    val dir = cache()
    val kept = File(dir, "tenuto.txt").apply { writeText("ciao") }
    val extracted = File(dir, "estratto.txt").apply { writeText("ciao") }
    FluidViewerSource.of(kept).use { assertEquals("ciao", it.open().use { s -> s.readBytes().decodeToString() }) }
    assertTrue(kept.exists())
    val source = FluidViewerSource.of(extracted, mimeType = "text/plain", deleteOnClose = true)
    assertEquals("estratto.txt", source.name)
    assertEquals(4L, source.sizeBytes)
    assertEquals("text/plain", source.mimeType)
    source.close()
    assertFalse(extracted.exists())
    try {
      FluidViewerSource.of(File(dir, "sparito.txt")).open()
      fail("un file sparito")
    } catch (e: FluidViewerException) {
      assertEquals(FluidViewerFailure.Unreadable, e.failure)
    }
  }

  @Test
  fun `the zip index reader survives broken, empty and huge inputs`() {
    val dir = cache()
    fun names(bytes: ByteArray): Set<String>? {
      val file = File(dir, "z${bytes.size}-${Random.nextInt()}.zip").apply { writeBytes(bytes) }
      return java.io.RandomAccessFile(file, "r").channel.use { FluidZipDirectory.names(it) }
    }
    assertNull(names(ByteArray(0)))
    assertNull(names("PK\u0003\u0004 troncato".toByteArray()))
    assertNull(names(Random(3).nextBytes(100_000)))
    val empty = java.io.ByteArrayOutputStream().also { ZipOutputStream(it).close() }.toByteArray()
    assertEquals(emptySet<String>(), names(empty))
    val withComment = java.io.ByteArrayOutputStream().also { out ->
      ZipOutputStream(out).use { z ->
        z.setComment("un commento lungo ".repeat(100))
        z.putNextEntry(ZipEntry("word/document.xml"))
        z.closeEntry()
      }
    }.toByteArray()
    assertEquals(setOf("word/document.xml"), names(withComment))
    // Un indice che si dice ZIP64 (0xFFFF voci) senza il localizzatore è rifiutato, senza leggerlo.
    val tooMany = withComment.copyOf().also { bytes ->
      val end = (bytes.size - 22 downTo 0).first { i ->
        bytes[i] == 0x50.toByte() && bytes[i + 1] == 0x4B.toByte() && bytes[i + 2] == 5.toByte() && bytes[i + 3] == 6.toByte()
      }
      bytes[end + 10] = 0xFF.toByte()
      bytes[end + 11] = 0xFF.toByte()
    }
    assertNull(names(tooMany))
    assertFalse(FluidZipDirectory.looksLikeZip("PK".toByteArray()))
    assertTrue(FluidZipDirectory.looksLikeZip(empty))
  }
}
