package dev.antigravity.fluidengine.viewer.office

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.antigravity.fluidengine.viewer.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F6C.15b: the Office thumbnail is the saved one only when it says something; a blank, tiny, broken or missing one is
 * replaced by the first page, slide or sheet drawn by the module. Drawn thumbnails go to `build/office-thumbnails`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OfficeThumbnailTest {
  @org.junit.Before fun visualBudget() {
    val app = RuntimeEnvironment.getApplication()
    val am = app.getSystemService(android.app.ActivityManager::class.java)
    org.robolectric.Shadows.shadowOf(am).setMemoryClass(512)
    // Questi test verificano i pixel alla misura richiesta. La quota stretta ha prove dedicate.
    dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources.get(app).budget
      .updateLimits(dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits.Full)
  }

  private val corpus = File("src/test/resources/corpus")
  private val output = File("build/office-thumbnails").also { it.mkdirs() }

  private fun thumbnail(file: File, size: Int = 512): Bitmap? = runBlocking {
    val started = System.nanoTime()
    val image: ImageBitmap? =
      FluidViewerSource.of(file).use { FluidViewerRegistry.thumbnail(RuntimeEnvironment.getApplication(), it, size) }
    println("miniatura ${file.name}: ${(System.nanoTime() - started) / 1_000_000} ms")
    image?.asAndroidBitmap()
  }

  /** A copy of [original] named [name] whose saved thumbnail is [bytes] (`null`: none at all). */
  private fun withSaved(original: File, name: String, bytes: ByteArray?): File {
    val copy = File(output, name)
    ZipFile(original).use { zip ->
      ZipOutputStream(copy.outputStream()).use { out ->
        for (entry in zip.entries()) {
          if (entry.name == "docProps/thumbnail.jpeg") continue
          out.putNextEntry(ZipEntry(entry.name))
          zip.getInputStream(entry).use { it.copyTo(out) }
          out.closeEntry()
        }
        if (bytes != null) {
          out.putNextEntry(ZipEntry("docProps/thumbnail.jpeg"))
          out.write(bytes)
          out.closeEntry()
        }
      }
    }
    return copy
  }

  private fun jpeg(width: Int, height: Int, draw: (Canvas) -> Unit): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    draw(Canvas(bitmap))
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
  }

  private val white = jpeg(395, 512) { it.drawColor(Color.WHITE) }

  /** Nearly white: JPEG noise and one faint speck, as some editors save it. */
  private val nearlyWhite = jpeg(256, 192) {
    it.drawColor(Color.rgb(252, 252, 252))
    it.drawRect(10f, 10f, 12f, 12f, Paint().apply { color = Color.rgb(200, 200, 200) })
  }

  /** A telling picture: magenta on the left, cyan bars on the right. Nothing the module would draw. */
  private val telling = jpeg(256, 192) { canvas ->
    canvas.drawColor(Color.WHITE)
    canvas.drawRect(0f, 0f, 128f, 192f, Paint().apply { color = Magenta })
    val bar = Paint().apply { color = Color.CYAN }
    for (y in 0 until 192 step 24) canvas.drawRect(140f, y.toFloat(), 250f, y + 12f, bar)
  }

  private fun save(bitmap: Bitmap, name: String) {
    File(output, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
  }

  private fun assertDrawnPortrait(result: Bitmap?, label: String) {
    assertNotNull("$label: nessuna miniatura", result)
    assertTrue("$label: la miniatura disegnata è bianca", saysSomething(result!!))
    assertTrue("$label: la prima pagina è verticale", result.height > result.width)
    assertNotEquals("$label: non è la miniatura salvata", Magenta, result.getPixel(result.width / 4, result.height / 2))
  }

  @Test
  fun blankSavedThumbnailIsReplacedByTheDrawnFirstPage() {
    val docx = withSaved(File(corpus, "Canale di accesso.docx"), "bianca.docx", white)
    val page = thumbnail(docx)
    assertDrawnPortrait(page, "DOCX con la miniatura bianca")
    assertEquals(512, page!!.height)
    save(page, "Canale di accesso.jpg")

    val pptx = withSaved(File(corpus, "Rotte di novembre.pptx"), "bianca.pptx", nearlyWhite)
    val slide = thumbnail(pptx)
    assertNotNull(slide)
    assertTrue("la slide disegnata è bianca", saysSomething(slide!!))
    assertTrue("la slide è orizzontale", slide.width > slide.height)
    assertEquals(512, slide.width)
    save(slide, "Rotte di novembre.jpg")
  }

  @Test
  fun tellingSavedThumbnailIsUsed() {
    val docx = withSaved(File(corpus, "Canale di accesso.docx"), "buona.docx", telling)
    val saved = thumbnail(docx)
    assertNotNull(saved)
    // The document is portrait, the saved picture landscape: the result is the picture, at its own proportions.
    assertTrue(saved!!.width > saved.height)
    assertEquals(256, saved.width)
    val left = saved.getPixel(64, 96)
    assertTrue("a sinistra il magenta salvato", Color.red(left) > 200 && Color.green(left) < 60 && Color.blue(left) > 100)
    val odt = thumbnail(File(corpus, "Canale.odt"))
    assertNotNull("la miniatura ODF salvata resta", odt)
  }

  @Test
  fun tinyBrokenOrMissingSavedThumbnailIsDrawn() {
    val original = File(corpus, "Canale di accesso.docx")
    val tiny = jpeg(12, 16) { it.drawColor(Magenta) }
    assertDrawnPortrait(thumbnail(withSaved(original, "minuscola.docx", tiny)), "minuscola")
    assertDrawnPortrait(thumbnail(withSaved(original, "rotta.docx", "non è un'immagine".toByteArray())), "rotta")
    assertDrawnPortrait(thumbnail(withSaved(original, "assente.docx", null)), "assente")
    assertDrawnPortrait(thumbnail(File(corpus, "Calibri.docx")), "Calibri.docx, bianca da Word")
  }

  @Test
  fun workbooksAndRtfWithoutThumbnailDrawTheirStart() {
    val sheet = thumbnail(File(corpus, "Bilancio 2026.xlsx"))
    assertNotNull("XLSX senza miniatura", sheet)
    assertTrue("il foglio disegnato è bianco", saysSomething(sheet!!))
    save(sheet, "Bilancio 2026.jpg")
    val rtf = thumbnail(File(corpus, "Canale.rtf"))
    assertNotNull("RTF", rtf)
    assertTrue(saysSomething(rtf!!))
    assertTrue("Canale.rtf è orizzontale", rtf.width > rtf.height)
  }

  @Test
  fun blankAndTellingSamplesAreTold() {
    fun bitmap(draw: (Canvas) -> Unit) =
      Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
    assertFalse(saysSomething(bitmap { it.drawColor(Color.WHITE) }))
    assertFalse(saysSomething(bitmap { it.drawColor(Color.TRANSPARENT) }))
    assertFalse("una tinta unita, anche scura", saysSomething(bitmap { it.drawColor(Color.DKGRAY) }))
    // One line of title text on a page: about one sample in a hundred.
    assertTrue(saysSomething(bitmap {
      it.drawColor(Color.WHITE)
      it.drawRect(40f, 40f, 240f, 52f, Paint().apply { color = Color.BLACK })
    }))
  }

  @Test
  fun demoFilesCarryATellingThumbnail() {
    for (name in listOf("Canale di accesso.docx", "Rotte di novembre.pptx")) {
      ZipFile(File(corpus, name)).use { zip ->
        val saved = savedThumbnail(zip, OfficePackage(zip), 512)
        assertNotNull("$name: la miniatura salvata è vuota", saved)
      }
    }
  }

  private companion object {
    val Magenta = Color.rgb(224, 32, 138)
  }
}
