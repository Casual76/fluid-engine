package dev.antigravity.fluidengine.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/**
 * F6C.17 · Le miniature di engine-viewer: il testo e il codice disegnati, l'icona del documento per il resto, la cache
 * con il suo tetto, il tempo massimo, il file rotto che dà il ripiego senza cadere. Le immagini disegnate vanno in
 * `build/thumbnails`, per guardarle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidFileThumbnailsTest {
  private val context get() = RuntimeEnvironment.getApplication()
  private val output = File("build/thumbnails").also { it.mkdirs() }
  private val dir: File = Files.createTempDirectory("f6c17").toFile()

  @Before fun clean() {
    FluidFileThumbnails.clear()
  }

  @After fun restore() {
    FluidViewerRegistry.uninstall("lenta", "rotta", "contata")
    FluidFileThumbnails.clear()
    FluidFileThumbnails.memoryLimitBytes = 48L * 1024 * 1024
  }

  private fun file(name: String, text: String, mime: String? = null): FluidViewerFile = file(name, text.toByteArray(), mime)

  private fun file(name: String, bytes: ByteArray, mime: String? = null): FluidViewerFile {
    val disk = File(dir, name).apply { writeBytes(bytes) }
    return FluidViewerFile(Uri.fromFile(disk), name, mime, disk.length())
  }

  private fun load(file: FluidViewerFile, size: Int = 360, timeout: Long = FluidFileThumbnails.DefaultTimeoutMillis) =
    runBlocking { FluidFileThumbnails.load(context, file, size, timeoutMillis = timeout) }

  private fun save(name: String, image: ImageBitmap) {
    File(output, "$name.png").outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  /** Vero se l'immagine ha qualcosa: almeno l'1% dei campioni si stacca dal colore principale. */
  private fun hasInk(image: ImageBitmap): Boolean {
    val bitmap = image.asAndroidBitmap()
    val counts = HashMap<Int, Int>()
    val samples = ArrayList<Int>()
    val step = maxOf(1, maxOf(bitmap.width, bitmap.height) / 120)
    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) {
      val c = bitmap.getPixel(x, y)
      samples += c
      counts[c] = (counts[c] ?: 0) + 1
    }
    val main = counts.maxBy { it.value }.key
    val ink = samples.count { c ->
      maxOf(Math.abs(Color.red(c) - Color.red(main)), Math.abs(Color.green(c) - Color.green(main)),
        Math.abs(Color.blue(c) - Color.blue(main)), Math.abs(Color.alpha(c) - Color.alpha(main))) > 24
    }
    return ink >= samples.size / 100
  }

  @Test fun `il codice e il testo hanno le prime righe su un foglio`() {
    val code = load(file("Visore.kt", """
      package dev.antigravity.fluidengine.sample

      // Il visore della galleria
      internal fun visore(files: List<String>): Int {
        val conto = files.count { it.endsWith(".kt") }
        return conto + 42
      }
    """.trimIndent()))
    save("codice", code.image)
    assertFalse(code.isPlaceholder)
    assertFalse(code.fillsFrame)
    assertTrue("Il codice è disegnato", hasInk(code.image))
    assertTrue("Il foglio è verticale", code.image.height > code.image.width)
    val codeColors = colors(code.image)
    assertTrue("Il codice ha i colori della sintassi: $codeColors", codeColors >= 2)

    val prose = load(file("Diario di bordo.txt", "Lunedì 3 novembre. Il vento gira a nord-ovest e la duna si muove.\n\n" +
      "Martedì 4. Misuriamo la cresta: dodici metri e quaranta. Il GPS perde il segnale vicino al bosco."))
    save("testo", prose.image)
    assertFalse(prose.isPlaceholder)
    assertTrue(hasInk(prose.image))
  }

  @Test fun `il registro da il testo e il codice anche senza famiglie, come prima le famiglie`() {
    val kt = file("Senza.kt", "fun main() = println(\"ciao\")")
    val image = runBlocking {
      FluidViewerSource.of(File(requireNotNull(kt.uri.path))).use { FluidViewerRegistry.thumbnail(context, it, 240) }
    }
    assertNotNull(image)
    // Le foto restano dell'app: il registro non le disegna (FluidFileThumbnails sì).
    val png = file("punto.png", png())
    val photo = runBlocking {
      FluidViewerSource.of(File(requireNotNull(png.uri.path))).use { FluidViewerRegistry.thumbnail(context, it, 240) }
    }
    assertNull(photo)
  }

  @Test fun `un tipo sconosciuto ha l'icona del documento con l'estensione`() {
    val blend = load(file("Scena.blend", ByteArray(512) { (it * 37).toByte() }))
    save("sconosciuto", blend.image)
    assertTrue(blend.isPlaceholder)
    assertFalse(blend.fillsFrame)
    assertTrue(hasInk(blend.image))
    // Gli angoli sono trasparenti: è un simbolo, non una tessera grigia.
    assertEquals(0, Color.alpha(blend.image.asAndroidBitmap().getPixel(0, 0)))
  }

  @Test fun `un APK che non si legge da l'icona del documento e non cade`() {
    val apk = load(file("Aggiornamento.apk", ByteArray(2048) { 7 }, "application/vnd.android.package-archive"))
    save("apk-rotto", apk.image)
    assertTrue(apk.isPlaceholder)
    assertTrue(hasInk(apk.image))
  }

  @Test fun `un'immagine si riduce e riempie la tessera`() {
    val photo = load(file("punto.png", png()), size = 128)
    save("foto", photo.image)
    assertFalse(photo.isPlaceholder)
    assertTrue(photo.fillsFrame)
    assertTrue(maxOf(photo.image.width, photo.image.height) <= 128)
  }

  @Test fun `un file che manca o una famiglia che cade danno il ripiego senza cadere`() {
    val missing = FluidViewerFile(Uri.fromFile(File(dir, "sparito.pdf")), "sparito.pdf", "application/pdf", null)
    val gone = load(missing)
    assertTrue(gone.isPlaceholder)
    assertTrue(hasInk(gone.image))

    FluidViewerRegistry.install(Family("rotta", "rotta") { error("decodificatore rotto") })
    val broken = load(file("dati.rotta", "qualcosa"))
    assertTrue(broken.isPlaceholder)
    save("rotto", broken.image)
  }

  @Test fun `la cache da la stessa miniatura, anche subito, e una richiesta doppia lavora una volta`() {
    val family = Family("contata", "contata")
    FluidViewerRegistry.install(family)
    val f = file("uno.contata", "x")
    val both = runBlocking {
      coroutineScope {
        (1..2).map { async { FluidFileThumbnails.load(context, f, 300) } }.awaitAll()
      }
    }
    assertSame(both[0], both[1])
    assertEquals("Un lavoro solo per due richieste uguali", 1, family.calls.get())
    assertSame(both[0], FluidFileThumbnails.cached(f, 300))
    // La stessa misura arrotondata a 32 px è la stessa voce.
    assertSame(both[0], FluidFileThumbnails.cached(f, 290))
    assertSame(both[0], load(f, 300))
    assertEquals(1, family.calls.get())
    // Un altro tema è un'altra miniatura.
    assertNull(FluidFileThumbnails.cached(f, 300, FluidThumbnailLook.Dark))
    FluidFileThumbnails.evict(f)
    assertNull(FluidFileThumbnails.cached(f, 300))
  }

  @Test fun `senza famiglie la stessa miniatura e un'altra voce`() {
    val family = Family("contata", "contata")
    FluidViewerRegistry.install(family)
    val f = file("due.contata", ByteArray(256) { it.toByte() })
    val with = load(f)
    assertFalse(with.isPlaceholder)
    FluidViewerRegistry.uninstall("contata")
    assertNull("Senza il modulo la cache non dà la sua miniatura", FluidFileThumbnails.cached(f, 360))
    val without = load(f)
    assertTrue("Senza il modulo: l'icona del documento", without.isPlaceholder)
  }

  @Test fun `il tetto di memoria toglie le meno usate`() {
    val first = file("a.kt", "val a = 1")
    val one = load(first, 512)
    val limit = one.byteCount * 2 + 1
    FluidFileThumbnails.memoryLimitBytes = limit
    load(file("b.kt", "val b = 2"), 512)
    load(file("c.kt", "val c = 3"), 512)
    assertTrue("Sta nel tetto: ${FluidFileThumbnails.cachedBytes} ≤ $limit", FluidFileThumbnails.cachedBytes <= limit)
    assertEquals(2, FluidFileThumbnails.cachedCount)
    assertNull("La meno usata è uscita", FluidFileThumbnails.cached(first, 512))
  }

  @Test fun `oltre il tempo massimo torna subito col ripiego`() {
    val slow = Family("lenta", "lenta") { Thread.sleep(3_000); it }
    FluidViewerRegistry.install(slow)
    val f = file("piano.lenta", "x")
    val started = System.nanoTime()
    val thumbnail = load(f, timeout = 200)
    val millis = (System.nanoTime() - started) / 1_000_000
    assertTrue("Torna allo scadere, non dopo il decodificatore: $millis ms", millis < 1_500)
    assertTrue(thumbnail.isPlaceholder)
    // Il ripiego per tempo scaduto resta in cache (non si riprova a ogni scorrimento), per un po'.
    assertSame(thumbnail, FluidFileThumbnails.cached(f, 360))
  }

  private fun colors(image: ImageBitmap): Int {
    val bitmap = image.asAndroidBitmap()
    val hues = HashSet<Int>()
    for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
      val c = bitmap.getPixel(x, y)
      val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
      if (maxOf(r, g, b) - minOf(r, g, b) < 60) continue
      hues += (if (r >= g && r >= b) 0 else if (g >= b) 1 else 2) * 10 + (if (r > b) 1 else 0)
    }
    return hues.size
  }

  private fun png(): ByteArray {
    val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    for (y in 0 until 300) for (x in 0 until 400) bitmap.setPixel(x, y, Color.rgb(x * 255 / 400, y * 255 / 300, 128))
    return java.io.ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
  }

  /** Una famiglia di prova per l'estensione [extension]: la sua miniatura è un foglio con una riga, dopo [around]. */
  private class Family(override val id: String, extension: String, private val around: (ImageBitmap) -> ImageBitmap = { it }) :
    FluidViewerFamily {
    val calls = AtomicInteger()
    override val types = listOf(FluidViewerFamilyType(id, id, setOf(extension), kind = FluidFileKind.Document))
    override suspend fun open(request: FluidViewerRequest): FluidViewerOpened = FluidViewerOpened(FluidViewerContent.Custom {})
    override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap {
      calls.incrementAndGet()
      return around(FluidThumbnailPainter.prosePage("La miniatura di ${request.source.name}", sizePx))
    }
  }
}
