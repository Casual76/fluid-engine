package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

/**
 * I decodificatori della piattaforma sul dispositivo, dove la JVM non arriva: `PdfRenderer` (grande, rotto,
 * protetto, da un `Uri` non ricercabile), `ImageDecoder` e `AnimatedImageDrawable` (GIF e WebP scritte da Pillow), il
 * testo da un `Uri`, i ripieghi di `open`, e un `ExoPlayer` vero dietro `FluidMedia3Session` (il modulo compilato
 * contro Media3 1.5.1, l'APK di prova con la versione di `media3Test`).
 */
@RunWith(AndroidJUnit4::class)
class FluidViewerDeviceTest {

  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private val context: Context = instrumentation.targetContext

  @Before
  fun clean() {
    File(context.cacheDir, "fluid-viewer").deleteRecursively()
  }

  private fun log(message: String) = Log.i("F6C4", message).also { println("F6C4 $message") }

  private fun asset(name: String): File {
    val file = File(context.cacheDir, name)
    InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
    return file
  }

  private fun longPdf(pages: Int, name: String = "lungo.pdf"): File {
    val file = File(context.cacheDir, name)
    val document = PdfDocument()
    val ink = Paint().apply { color = Color.BLACK; textSize = 48f }
    repeat(pages) { i ->
      val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
      page.canvas.drawRect(40f, 40f, 555f, 802f, Paint().apply { style = Paint.Style.STROKE; strokeWidth = 4f })
      page.canvas.drawText("Pagina ${i + 1}", 72f, 120f, ink)
      document.finishPage(page)
    }
    file.outputStream().use { document.writeTo(it) }
    document.close()
    return file
  }

  private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (!condition()) {
      if (SystemClock.uptimeMillis() > deadline) fail("condizione non raggiunta in $timeoutMs ms")
      Thread.sleep(20)
    }
  }

  private fun cacheCopies(): List<String> = File(context.cacheDir, "fluid-viewer").list()?.toList().orEmpty()

  // --- PDF -------------------------------------------------------------------------------------------------------

  @Test
  fun aLongPdfRendersPageByPageWithPreviews() = runBlocking {
    val file = longPdf(300)
    log("PDF di prova: 300 pagine, ${file.length()} byte")
    val source = FluidPdfPageSource.open(file)
    assertEquals(300, source.count)
    assertEquals(IntSize(595, 842), source.size(0))
    assertEquals(IntSize(595, 842), source.size(299))

    // Venti pagine intere chieste insieme da più thread: il renderer le serve una alla volta.
    val start = SystemClock.elapsedRealtime()
    val pages = (0 until 20).map { i -> async(Dispatchers.Default) { source.render(i, IntSize(1190, 1684)) } }.awaitAll()
    val full = SystemClock.elapsedRealtime() - start
    pages.forEach { assertEquals(1190, it.width); assertEquals(1684, it.height) }
    val bitmap = pages[0].asAndroidBitmap()
    assertEquals(Color.WHITE, bitmap.getPixel(10, 10)) // la carta
    assertTrue("il bordo della pagina è inchiostro", Color.red(bitmap.getPixel(80, 842)) < 128)

    // Le anteprime di tutto il documento, come le chiede il foglio delle miniature.
    val previewStart = SystemClock.elapsedRealtime()
    for (i in 0 until 300) {
      val preview = source.preview(i)
      assertEquals(256, maxOf(preview.width, preview.height))
    }
    val previews = SystemClock.elapsedRealtime() - previewStart
    log("20 pagine 1190×1684 in $full ms (${full / 20} ms l'una); 300 anteprime in $previews ms (${previews / 300} ms l'una)")
    source.close()
    try {
      source.render(0, IntSize(100, 141))
      fail("dopo la chiusura le chiamate sono annullate")
    } catch (_: CancellationException) {
    }
  }

  @Test
  fun aCorruptPdfIsReportedAsCorrupt() = runBlocking<Unit> {
    val file = File(context.cacheDir, "rotto.pdf").apply { writeBytes("%PDF-1.7\n1 0 obj << /Type /Catalog >> garbage".toByteArray()) }
    val failure = runCatching { FluidPdfPageSource.open(file) }.exceptionOrNull()
    assertTrue("$failure", failure is FluidViewerException)
    assertEquals(FluidViewerFailure.Corrupt, (failure as FluidViewerException).failure)
    // Anche un PDF troncato a metà.
    val whole = longPdf(5, "intero.pdf").readBytes()
    val cut = File(context.cacheDir, "troncato.pdf").apply { writeBytes(whole.copyOf(whole.size / 2)) }
    val truncated = runCatching { FluidPdfPageSource.open(cut).use { it.render(0, IntSize(100, 141)) } }.exceptionOrNull()
    log("PDF troncato a metà: ${truncated?.javaClass?.simpleName ?: "si apre"} ${(truncated as? FluidViewerException)?.failure ?: ""}")
  }

  @Test
  fun aPasswordProtectedPdfIsReportedAsProtected() = runBlocking {
    val failure = runCatching { FluidPdfPageSource.open(asset("protetto.pdf")) }.exceptionOrNull()
    log("PDF protetto: $failure")
    assertTrue("$failure", failure is FluidViewerException)
    assertEquals(FluidViewerFailure.Protected, (failure as FluidViewerException).failure)
  }

  @Test
  fun aPdfFromAPipeIsCopiedAndTheCopyIsDeletedOnClose() = runBlocking {
    longPdf(40, "flusso.pdf")
    val uri = PipeProvider.uri("flusso.pdf")
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
    assertEquals("il fornitore dà davvero una pipe", -1L, descriptor.statSize)
    descriptor.close()
    val source = FluidPdfPageSource.open(context, uri)
    assertEquals(40, source.count)
    assertEquals(1, cacheCopies().size)
    assertEquals(595, source.render(39, IntSize(595, 842)).width)
    source.close()
    waitUntil { cacheCopies().isEmpty() }
  }

  @Test
  fun aPipeOverTheCopyLimitIsTooLargeAndLeavesNothing() = runBlocking {
    longPdf(40, "grande.pdf")
    val failure = runCatching { FluidPdfPageSource.open(context, PipeProvider.uri("grande.pdf"), maxCopyBytes = 1024) }.exceptionOrNull()
    assertEquals(FluidViewerFailure.TooLarge, (failure as FluidViewerException).failure)
    assertTrue(cacheCopies().isEmpty())
  }

  @Test
  fun aMissingUriIsUnreadable() = runBlocking {
    val failure = runCatching { FluidPdfPageSource.open(context, PipeProvider.uri("non-esiste.pdf")) }.exceptionOrNull()
    assertEquals(FluidViewerFailure.Unreadable, (failure as FluidViewerException).failure)
  }

  // --- Immagini --------------------------------------------------------------------------------------------------

  @Test
  fun gifAndWebpDecodeAsAnimatedDrawables() = runBlocking<Unit> {
    for (name in listOf("anim.gif", "anim.webp")) {
      val image = FluidDecodedImage.decode(asset(name))
      assertEquals(name, Build.VERSION.SDK_INT >= 28, image.animated)
      assertEquals(name, 64, image.width)
      assertEquals(name, 48, image.height)
      assertEquals(name, 64f / 48f, image.content(name).aspectRatio!!, 0.001f)
      // Dall'Uri come dal file.
      assertEquals(name, image.animated, FluidDecodedImage.decode(context, Uri.fromFile(File(context.cacheDir, name))).animated)
      // Senza animazione: il primo fotogramma.
      assertFalse(name, FluidDecodedImage.decode(asset(name), animate = false).animated)
    }
    assertFalse(FluidDecodedImage.decode(asset("ferma.webp")).animated)
    log("GIF e WebP animate: AnimatedImageDrawable su API ${Build.VERSION.SDK_INT}")
  }

  @Test
  fun aLargeImageIsReducedToThePixelCap() = runBlocking {
    val image = FluidDecodedImage.decode(asset("anim.webp"), maxPixels = 600)
    assertTrue(image.width * image.height <= 600)
    assertEquals(64, image.sourceWidth)
    assertEquals(64f / 48f, image.aspectRatio, 0.05f)
  }

  @Test
  fun aBrokenImageIsReportedAsCorrupt() = runBlocking {
    val file = File(context.cacheDir, "rotta.png").apply { writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 1, 2, 3, 4)) }
    val failure = runCatching { FluidDecodedImage.decode(file) }.exceptionOrNull()
    assertTrue("$failure", failure is FluidViewerException)
  }

  // --- Testo e ripieghi ------------------------------------------------------------------------------------------

  @Test
  fun utf16TextFromAPipeIsRead() = runBlocking {
    File(context.cacheDir, "nota.txt").writeBytes(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "Perché\r\nCittà".toByteArray(Charsets.UTF_16LE))
    val document = fluidReadTextFile(context, PipeProvider.uri("nota.txt"), "nota.txt")!!
    assertEquals(listOf("Perché", "Città"), document.lines)
    assertEquals(Charsets.UTF_16LE, document.charset.charset)
  }

  @Test
  fun openFallsBackOnTheBytesAndNeverDeadEnds() = runBlocking {
    asset("anim.gif").copyTo(File(context.cacheDir, "in-realta-una-gif.txt"), overwrite = true)
    File(context.cacheDir, "in-realta-testo.png").writeText("{\"a\": 1}\n")
    longPdf(3, "senza-nome")
    File(context.cacheDir, "gioco.apk").writeBytes(ByteArray(64) { 0x50 })
    File(context.cacheDir, "binario.txt").writeBytes(ByteArray(512) { (it % 7).toByte() })

    suspend fun open(name: String, mime: String? = null) =
      FluidViewerFile(Uri.fromFile(File(context.cacheDir, name)), name, mime).open(context, openWith = {}, share = {})

    val gif = open("in-realta-una-gif.txt")
    assertEquals(FluidViewerPreset.Image, gif.type.preset)
    assertTrue(gif.content is FluidViewerContent.Image)

    val text = open("in-realta-testo.png")
    assertEquals(FluidViewerPreset.Text, text.type.preset)
    assertEquals(FluidSyntax.Json, ((text.content as FluidViewerContent.Text).source as FluidTextDocument).language)

    val pdf = open("senza-nome", "application/octet-stream")
    assertEquals(FluidViewerPreset.Pages, pdf.type.preset)
    assertEquals(3, (pdf.content as FluidViewerContent.Pages).source.count)
    pdf.close()

    val apk = open("gioco.apk", "image/png")
    assertTrue(apk.type.executable)
    assertTrue(apk.content is FluidViewerContent.Unknown)
    assertNull(apk.failure)

    val binary = open("binario.txt")
    val unknown = binary.content as FluidViewerContent.Unknown
    assertNotNull(unknown.openWith)
    assertNotNull(unknown.share)
    assertEquals("binario.txt", unknown.info.name)

    val missing = open("non-esiste.pdf")
    assertTrue(missing.content is FluidViewerContent.Unknown)
    assertEquals(FluidViewerFailure.Unreadable, missing.failure?.failure)
  }

  // --- Media3 ----------------------------------------------------------------------------------------------------

  /** Un WAV PCM di [seconds] secondi a 440 Hz: ExoPlayer lo legge senza codec. */
  private fun wav(seconds: Int): File {
    val rate = 44_100
    val samples = rate * seconds
    val file = File(context.cacheDir, "tono.wav")
    RandomAccessFile(file, "rw").use { out ->
      out.setLength(0)
      fun le32(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
      fun le16(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
      out.write("RIFF".toByteArray()); le32(36 + samples * 2); out.write("WAVEfmt ".toByteArray())
      le32(16); le16(1); le16(1); le32(rate); le32(rate * 2); le16(2); le16(16)
      out.write("data".toByteArray()); le32(samples * 2)
      val pcm = ByteArray(samples * 2)
      for (i in 0 until samples) {
        val v = (sin(2 * PI * 440 * i / rate) * 8000).toInt()
        pcm[2 * i] = v.toByte(); pcm[2 * i + 1] = (v shr 8).toByte()
      }
      out.write(pcm)
    }
    return file
  }

  @Test
  fun aRealExoPlayerDrivesTheSession() {
    val file = wav(3)
    lateinit var player: ExoPlayer
    lateinit var session: FluidMedia3Session
    fun <T> main(block: () -> T): T {
      var result: Result<T>? = null
      instrumentation.runOnMainSync { result = runCatching(block) }
      return result!!.getOrThrow()
    }
    main {
      player = ExoPlayer.Builder(context).build()
      player.volume = 0f
      player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      session = FluidMedia3Session(player).apply { attach() }
    }
    log("ExoPlayer ${MediaLibraryInfo.VERSION} (modulo compilato contro Media3 1.5.1)")
    try {
      main { assertFalse(session.playing) }
      main { session.play() } // prepara e suona
      main { assertTrue("l'intenzione vale già mentre carica", session.playing) }
      waitUntil { main { !session.buffering && session.durationMillis != null } }
      main {
        assertEquals(3_000.0, session.durationMillis!!.toDouble(), 50.0)
        assertTrue(session.canSeek)
        assertNull(session.error)
      }
      waitUntil { main { session.refresh(); session.positionMillis > 300 } }
      main { session.pause() }
      main { assertFalse(session.playing) }
      main { session.seekTo(2_500) }
      main { assertEquals(2_500L, session.positionMillis) }
      main { session.seekTo(10_000) }
      main { assertEquals(3_000.0, session.positionMillis.toDouble(), 50.0) }
      main { session.play() }
      waitUntil { main { session.ended } }
      main { assertFalse(session.playing) }
      main { session.play() } // dal principio
      waitUntil { main { session.playing && session.positionMillis < 1_000 } }
      main { assertNull(session.videoAspectRatio) }
    } finally {
      main {
        session.close()
        player.release()
      }
    }
  }

  @Test
  fun aBrokenMediaFileBecomesTheSessionError() {
    val file = File(context.cacheDir, "rotto.mp4").apply { writeBytes(ByteArray(4096) { (it * 31).toByte() }) }
    lateinit var player: ExoPlayer
    lateinit var session: FluidMedia3Session
    instrumentation.runOnMainSync {
      player = ExoPlayer.Builder(context).build()
      player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
      session = FluidMedia3Session(player).apply { attach() }
      session.play()
    }
    try {
      waitUntil {
        var error: String? = null
        instrumentation.runOnMainSync { error = session.error }
        error != null
      }
      instrumentation.runOnMainSync { log("errore del player: ${session.error}"); assertFalse(session.playing) }
    } finally {
      instrumentation.runOnMainSync { session.close(); player.release() }
    }
  }
}
