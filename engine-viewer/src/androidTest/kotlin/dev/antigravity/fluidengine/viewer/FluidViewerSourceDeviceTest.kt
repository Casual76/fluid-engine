package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import kotlin.random.Random

/**
 * F6C.8 · (5) sul dispositivo: `FluidViewerSource` su un `Uri` che è una pipe ([PipeProvider]) si copia una volta in
 * `cacheDir/fluid-viewer`, dà descrittori ricercabili e cancella la copia alla chiusura, anche dopo un annullamento.
 * Si lancia con `:engine-viewer:connectedDebugAndroidTest` (F6C.6).
 */
@RunWith(AndroidJUnit4::class)
class FluidViewerSourceDeviceTest {
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

  private fun copies(): List<String> = File(context.cacheDir, "fluid-viewer").list()?.filter { it.startsWith("src-") }.orEmpty()

  private fun piped(name: String, bytes: ByteArray): Uri {
    File(context.cacheDir, name).writeBytes(bytes)
    return PipeProvider.uri(name)
  }

  @Before
  fun clean() {
    File(context.cacheDir, "fluid-viewer").listFiles()?.forEach { it.delete() }
  }

  @Test
  fun aPipeIsCopiedOnceAndTheCopyGoesAwayOnClose() = runBlocking {
    val bytes = Random(8).nextBytes(3 * 1024 * 1024)
    val source = FluidViewerSource.of(context, piped("flusso.bin", bytes), "flusso.bin")
    val first = source.seekable()
    val second = source.seekable()
    assertTrue("ricercabile", runCatching { Os.lseek(first.fileDescriptor, 0, OsConstants.SEEK_END) }.isSuccess)
    assertEquals(bytes.size.toLong(), Os.lseek(first.fileDescriptor, 0, OsConstants.SEEK_END))
    Os.lseek(second.fileDescriptor, 0, OsConstants.SEEK_SET)
    assertArrayEquals(bytes, FileInputStream(second.fileDescriptor).readBytes())
    first.close()
    second.close()
    assertEquals("una copia sola", 1, copies().size)
    assertArrayEquals("dopo la copia si legge la copia", bytes, source.open().use { it.readBytes() })
    source.close()
    assertEquals(emptyList<String>(), copies())
  }

  @Test
  fun aSeekableUriIsNotCopied() = runBlocking {
    val file = File(context.cacheDir, "fermo.bin").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
    val source = FluidViewerSource.of(context, Uri.fromFile(file), "fermo.bin")
    source.seekable().use { pfd: ParcelFileDescriptor -> assertEquals(4096L, pfd.statSize) }
    assertEquals(emptyList<String>(), copies())
    source.close()
  }

  @Test
  fun aCancelledCopyLeavesNothing() = runBlocking {
    val source = FluidViewerSource.of(context, piped("grande.bin", ByteArray(64 * 1024 * 1024)), "grande.bin")
    val job = launch(Dispatchers.IO) { runCatching { source.seekable().close() } }
    delay(30)
    job.cancelAndJoin()
    source.close()
    assertEquals(emptyList<String>(), copies())
  }

  @Test
  fun theRegistryOpensAPdfFromAPipeThroughTheBuiltInPreset() = runBlocking {
    val pdf = File(context.cacheDir, "da-pipe.pdf")
    android.graphics.pdf.PdfDocument().apply {
      finishPage(startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(200, 300, 1).create()))
      pdf.outputStream().use { writeTo(it) }
      close()
    }
    val document = FluidViewerRegistry.open(context, FluidViewerSource.of(context, PipeProvider.uri(pdf.name), pdf.name, "application/pdf"))
    assertEquals(FluidViewerPreset.Pages, document.type.preset)
    document.close()
  }
}
