package dev.antigravity.fluidengine.viewer.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * R1 (e R2, n. 11) · «Leggi testo» nei punti trovati dalla review: i log senza il nome del file, la copia dei pixel
 * fuori dal filo di chi chiama, la preparazione senza tre copie intere dell'immagine, le parole di Tesseract che non
 * lasciano memoria nativa, e una lettura annullata a metà che non si butta.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OcrR1Test {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
  private val previous = FluidLiveText.recognizer

  @After fun restore() {
    FluidLiveText.recognizer = previous
    FluidLiveText.clearCache()
  }

  private fun allocated(): Long = Jvm.allocated(Thread.currentThread())

  @Test fun unaLetturaFallitaNonScriveNeiLogIlNomeDelFile() {
    ShadowLog.clear()
    val state = FluidLiveTextState()
    runBlocking {
      state.read { throw java.io.FileNotFoundException("/storage/emulated/0/DCIM/Camera/Mario Rossi passaporto.jpg: open failed: ENOENT") }
    }
    assertEquals(FluidLiveTextStatus.Unavailable, state.status)
    val logs = ShadowLog.getLogsForTag("FluidLiveText")
    assertTrue("la lettura fallita si dice nei log", logs.isNotEmpty())
    for (item in logs) {
      assertNull("niente eccezione intera (col percorso) nei log", item.throwable)
      assertFalse("niente nome del file nei log: ${item.msg}", item.msg.contains("Mario") || item.msg.contains("/storage"))
    }
    // L'app la ritrova intera, in memoria, per i suoi log.
    assertTrue(FluidLiveText.lastError is java.io.FileNotFoundException)
  }

  @Test fun recognizeNonCopiaIPixelSulFiloDiChiChiama() {
    val recognizer = FluidTesseractRecognizer(context)
    assertTrue(recognizer.available)
    runBlocking { recognizer.recognize(Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)) }
    val photo = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
    val before = allocated()
    runBlocking { recognizer.recognize(photo) }
    val used = allocated() - before
    assertTrue("$used byte allocati sul filo di chi chiama (i pixel sono ${2000L * 1500 * 4})", used < 2000L * 1500)
  }

  @Test fun laPreparazioneDaGliStessiByteSenzaTreCopieDellImmagine() {
    val w = 2000
    val h = 1500
    // Una pagina finta: carta con l'ombra a sinistra e righe scure di «testo».
    val pixels = IntArray(w * h) { i ->
      val x = i % w
      val y = i / w
      val paper = 120 + (120 * x / w)
      val ink = if ((y / 9) % 4 == 1 && (x / 13) % 5 != 0) 60 else 0
      val v = (paper - ink).coerceIn(0, 255)
      (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    val expected = Reference.prepare(pixels.copyOf(), w, h, 2400)
    val input = pixels.copyOf()
    val before = allocated()
    val gray = OcrImage.prepare(input, w, h, 2400)
    val used = allocated() - before
    assertEquals(expected.width, gray.width)
    assertEquals(expected.height, gray.height)
    assertEquals(expected.scale, gray.scale, 0f)
    assertArrayEquals("gli stessi byte di prima", expected.bytes, gray.bytes)
    assertTrue("$used byte per preparare (l'immagine è ${w.toLong() * h * 4})", used < w.toLong() * h * 4)
  }

  @Test fun leParoleDiTesseractNonLascianoMemoriaNativa() {
    runBlocking(TesseractHost.dispatcher) {
      val image = javaClass.classLoader!!.getResourceAsStream("ocr/lettera.png").use { BitmapFactory.decodeStream(it) }!!
      val w = image.width
      val h = image.height
      val px = IntArray(w * h).also { image.getPixels(it, 0, w, 0, 0, w, h) }
      val gray = OcrImage.prepare(px, w, h, FluidTesseractRecognizer.DefaultTargetLongSide)
      val tess = TesseractHost.engine(context, "ita")
      tess.SetImage(gray.bytes, gray.width, gray.height, 1, gray.width)
      tess.SetSourceResolution(300)
      assertEquals(0, tess.Recognize(null))
      val count = TesseractHost.words(tess).size
      assertTrue("parole: $count", count > 40)
      // Il primo giro assesta lo heap nativo dopo la lettura (nelle misure del 26/11 scendeva di 9–25 MB): non conta.
      repeat(3300) { TesseractHost.words(tess) }
      val start = nativeBytes()
      repeat(3000) { TesseractHost.words(tess) }
      val middle = nativeBytes()
      repeat(3000) { TesseractHost.words(tess) }
      val end = nativeBytes()
      tess.Clear()
      // Col giro di prima (deallocate() e close(), che non liberavano niente) erano 9,4–9,9 MB ogni 4000 giri.
      assertTrue("la memoria nativa cresce di ${(middle - start) / 1000} e ${(end - middle) / 1000} kB in due volte 3000 giri di $count parole",
        end - start < 4_000_000)
    }
  }

  /** La memoria del processo (impegnata), tolto quello che tiene la JVM: quasi solo la nativa. */
  private fun nativeBytes(): Long {
    System.gc()
    Thread.sleep(100)
    return Jvm.processCommitted() - Jvm.jvmCommitted()
  }

  /**
   * I contatori della JVM delle prove, per riflessione: `java.lang.management` non è nell'android.jar con cui si
   * compilano le prove, ma c'è nella JVM che le fa girare (HotSpot).
   */
  private object Jvm {
    private val factory = Class.forName("java.lang.management.ManagementFactory")
    private val threads = factory.getMethod("getThreadMXBean").invoke(null)
    private val threadAllocated = Class.forName("com.sun.management.ThreadMXBean")
      .getMethod("getThreadAllocatedBytes", java.lang.Long.TYPE)
    private val os = factory.getMethod("getOperatingSystemMXBean").invoke(null)
    private val committed = Class.forName("com.sun.management.OperatingSystemMXBean").getMethod("getCommittedVirtualMemorySize")
    private val memory = factory.getMethod("getMemoryMXBean").invoke(null)
    private val memoryBean = Class.forName("java.lang.management.MemoryMXBean")
    private val heap = memoryBean.getMethod("getHeapMemoryUsage")
    private val nonHeap = memoryBean.getMethod("getNonHeapMemoryUsage")
    private val usageCommitted = Class.forName("java.lang.management.MemoryUsage").getMethod("getCommitted")

    fun allocated(thread: Thread): Long = threadAllocated.invoke(threads, thread.id) as Long

    /** Su Windows è la memoria privata impegnata del processo (PagefileUsage). */
    fun processCommitted(): Long = committed.invoke(os) as Long

    fun jvmCommitted(): Long = usageCommitted.invoke(heap.invoke(memory)) as Long + usageCommitted.invoke(nonHeap.invoke(memory)) as Long
  }

  /** Come Tesseract: una lettura lunga che non si interrompe (il filo dorme fino in fondo). */
  private class SlowRecognizer(private val ms: Long, private val calls: AtomicInteger) : FluidTextRecognizer {
    override val available: Boolean = true
    override suspend fun recognize(image: Bitmap): FluidRecognizedText = withContext(Dispatchers.IO) {
      calls.incrementAndGet()
      Thread.sleep(ms)
      FluidRecognizedText(emptyList(), image.width, image.height)
    }
  }

  private fun page(): Bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)

  /** Aspetta [condition] in tempo vero, con un tetto largo (il lavoro gira su `Dispatchers.IO`). */
  private fun awaitReal(what: String, condition: () -> Boolean) {
    val end = System.nanoTime() + 20_000_000_000L
    while (!condition()) {
      assertTrue("tempo scaduto: $what", System.nanoTime() < end)
      Thread.sleep(5)
    }
  }

  @Test fun unaLetturaAnnullataAMetaSiRicordaLoStesso() {
    FluidLiveText.clearCache()
    val calls = AtomicInteger()
    FluidLiveText.recognizer = { SlowRecognizer(300, calls) }
    // La ricerca cambia mentre Tesseract legge la pagina: chi l'aveva chiesta se ne va, la lettura finisce lo stesso.
    val job = CoroutineScope(Dispatchers.Default).launch { FluidLiveText.read(context, "r1:pagina-0") { page() } }
    // F7.32 · Si annulla quando la lettura è partita davvero, e si aspetta che finisca, invece di due sonni da 120 e
    // 400 ms: sul PC carico la lettura poteva non essere ancora partita, o non ancora finita.
    awaitReal("la lettura parte") { calls.get() == 1 }
    job.cancel()
    runBlocking { job.join() }
    awaitReal("il lavoro già fatto non si butta") { FluidLiveText.cached("r1:pagina-0") != null }
    // La ricerca che riparte trova la pagina già letta.
    runBlocking { FluidLiveText.read(context, "r1:pagina-0") { error("non serve rileggerla") } }
    assertEquals(1, calls.get())
  }

  @Test fun laPaginaInVistaAspettaAlPiuUnaPaginaDellaRicerca() {
    FluidLiveText.clearCache()
    val calls = AtomicInteger()
    FluidLiveText.recognizer = { SlowRecognizer(300, calls) }
    runBlocking(Dispatchers.Default) {
      // La ricerca nelle prime dieci pagine, una lettura alla volta.
      val search = launch { for (i in 0 until 10) FluidLiveText.read(context, "r1:ricerca-$i") { page() } }
      delay(450)
      // A metà della seconda pagina della ricerca, la pagina in vista chiede la sua lettura.
      val start = System.nanoTime()
      val before = calls.get()
      val view = async { FluidLiveText.read(context, "r1:in-vista") { page() } }
      view.await()
      val waited = (System.nanoTime() - start) / 1_000_000
      // Le pagine della ricerca partite mentre la pagina in vista aspettava (la sua lettura è una delle chiamate).
      val overtaken = calls.get() - before - 1
      search.cancel()
      // Il lucchetto è equo: la pagina in vista passa dopo quella in corso, non dopo tutta la ricerca (2,7 s). F7.32: si
      // contano le pagine partite fra la richiesta e la fine della sua lettura (al più due: una che partiva proprio mentre
      // la chiedeva, una appena dopo, quando il lucchetto si libera; senza l'equità sarebbero otto), non i
      // millisecondi (era «meno di 1100 ms»: sul PC carico i sonni da 300 ms durano di più).
      println("OCR R1: la pagina in vista ha aspettato $waited ms, passata da $overtaken pagine della ricerca")
      assertTrue("la pagina in vista è stata passata da $overtaken pagine della ricerca ($waited ms)", overtaken <= 2)
    }
  }

  /** La preparazione di prima (F8.17, `e769ea0`), con la luminanza e la luce della carta in due mappe intere. */
  private object Reference {
    fun prepare(pixels: IntArray, w: Int, h: Int, targetLongSide: Int): OcrGray {
      val long = max(w, h)
      val scale = when {
        long < targetLongSide -> min(2f, targetLongSide.toFloat() / long)
        long > targetLongSide * 1.3f -> targetLongSide * 1.3f / long
        else -> 1f
      }
      val ow = max(1, (w * scale).roundToInt())
      val oh = max(1, (h * scale).roundToInt())
      val luma = FloatArray(w * h) { i ->
        val c = pixels[i]
        (0.299f * (c shr 16 and 0xFF) + 0.587f * (c shr 8 and 0xFF) + 0.114f * (c and 0xFF))
      }
      val light = paperLight(luma, w, h)
      val out = ByteArray(ow * oh)
      for (y in 0 until oh) {
        val sy = ((y + 0.5f) / scale - 0.5f).coerceIn(0f, (h - 1).toFloat())
        val y0 = sy.toInt(); val y1 = min(h - 1, y0 + 1); val ty = sy - y0
        for (x in 0 until ow) {
          val sx = ((x + 0.5f) / scale - 0.5f).coerceIn(0f, (w - 1).toFloat())
          val x0 = sx.toInt(); val x1 = min(w - 1, x0 + 1); val tx = sx - x0
          val i00 = y0 * w + x0; val i01 = y0 * w + x1; val i10 = y1 * w + x0; val i11 = y1 * w + x1
          val v = (luma[i00] / light[i00]) * (1 - tx) * (1 - ty) + (luma[i01] / light[i01]) * tx * (1 - ty) +
            (luma[i10] / light[i10]) * (1 - tx) * ty + (luma[i11] / light[i11]) * tx * ty
          out[y * ow + x] = (v * 255f).roundToInt().coerceIn(0, 255).toByte()
        }
      }
      return OcrGray(out, ow, oh, scale, w, h)
    }

    fun paperLight(luma: FloatArray, w: Int, h: Int): FloatArray {
      val cell = max(8, max(w, h) / 32)
      val gw = (w + cell - 1) / cell
      val gh = (h + cell - 1) / cell
      val grid = FloatArray(gw * gh)
      val histogram = IntArray(64)
      for (gy in 0 until gh) for (gx in 0 until gw) {
        java.util.Arrays.fill(histogram, 0)
        var count = 0
        var y = gy * cell
        while (y < min(h, (gy + 1) * cell)) {
          var x = gx * cell
          while (x < min(w, (gx + 1) * cell)) { histogram[(luma[y * w + x].toInt() shr 2).coerceIn(0, 63)]++; count++; x += 2 }
          y += 2
        }
        val target = (count * 0.9f).toInt()
        var sum = 0
        var value = 63
        for (i in 0 until 64) { sum += histogram[i]; if (sum > target) { value = i; break } }
        grid[gy * gw + gx] = (value * 4 + 2).toFloat()
      }
      var smooth = FloatArray(grid.size) { i ->
        val gx = i % gw; val gy = i / gw
        var m = 0f
        for (dy in -1..1) for (dx in -1..1) m = max(m, grid[(gy + dy).coerceIn(0, gh - 1) * gw + (gx + dx).coerceIn(0, gw - 1)])
        m
      }
      repeat(2) {
        val prev = smooth
        smooth = FloatArray(prev.size) { i ->
          val gx = i % gw; val gy = i / gw
          var s = 0f
          for (dy in -1..1) for (dx in -1..1) s += prev[(gy + dy).coerceIn(0, gh - 1) * gw + (gx + dx).coerceIn(0, gw - 1)]
          s / 9f
        }
      }
      val light = FloatArray(w * h)
      for (y in 0 until h) {
        val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
        val y0 = fy.toInt(); val y1 = min(gh - 1, y0 + 1); val ty = fy - y0
        for (x in 0 until w) {
          val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
          val x0 = fx.toInt(); val x1 = min(gw - 1, x0 + 1); val tx = fx - x0
          val a = smooth[y0 * gw + x0] * (1 - tx) + smooth[y0 * gw + x1] * tx
          val b = smooth[y1 * gw + x0] * (1 - tx) + smooth[y1 * gw + x1] * tx
          light[y * w + x] = max(32f, a * (1 - ty) + b * ty)
        }
      }
      return light
    }
  }
}
