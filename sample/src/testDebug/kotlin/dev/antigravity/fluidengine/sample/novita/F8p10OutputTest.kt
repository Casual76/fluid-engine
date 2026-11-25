package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.text.MeasuredText
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdf
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdfDocument
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdfWriter
import dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageFormat
import dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageFrame
import dev.antigravity.fluidengine.ui.fluid.output.FluidShareImageRenderer
import dev.antigravity.fluidengine.ui.fluid.output.fluidPdfDocument
import dev.antigravity.fluidengine.ui.fluid.output.rememberFluidPdfWriter
import dev.antigravity.fluidengine.ui.fluid.output.rememberFluidShareImageRenderer
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * F8.10 · Le uscite: l'immagine da condividere nei tre formati (i pixel veri, il fondo dell'accento, il materiale piatto
 * del pannello) e il PDF impaginato (testo scritto come testo, intestazione e numeri su ogni pagina, nessuna riga
 * tagliata fra due pagine, niente di invisibile nel testo che si seleziona).
 *
 * Robolectric non ha `PdfDocument` (le sue funzioni native non ci sono): le pagine si disegnano con la stessa
 * impaginazione del PDF ([dev.antigravity.fluidengine.ui.fluid.output.FluidPdfLayout]) su una tela che registra ogni
 * testo scritto. Il PDF vero si rilegge sul tablet (la scena «La pagella in PDF», `PdfRenderer` e il testo estratto).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi", application = GlassBenchDevice::class, shadows = [GlassBenchHeap::class])
class F8p10OutputTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var renderer: FluidShareImageRenderer
  private lateinit var writer: FluidPdfWriter

  private fun setUp(dark: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
        renderer = rememberFluidShareImageRenderer()
        writer = rememberFluidPdfWriter()
      }
    }
    compose.waitForIdle()
  }

  /** Dove le prove lasciano le immagini, per le tavole di Alessio: `sample/build/f8-10/`. */
  private val out = File("build/f8-10").apply { mkdirs() }

  private fun luminance(argb: Int): Float =
    (0.2126f * AndroidColor.red(argb) + 0.7152f * AndroidColor.green(argb) + 0.0722f * AndroidColor.blue(argb)) / 255f

  @Test fun theImageComesOutInTheThreeFormatsWithFlatPrintMaterial() {
    setUp()
    for (format in FluidShareImageFormat.all) {
      var bitmap: Bitmap? = null
      compose.runOnUiThread {
        bitmap = renderer.render(format) {
          FluidShareImageFrame(signature = "ClasseViva") {
            Text("Matematica")
            Text("8½")
          }
        }
      }
      val image = bitmap!!
      assertEquals(format.widthPx, image.width)
      assertEquals(format.heightPx, image.height)
      image.compress(Bitmap.CompressFormat.PNG, 100, File(out, "prova-${format.name.replace(':', 'x')}.png").outputStream())
      // Il fondo dell'accento: un colore, non trasparente e non bianco.
      val corner = image.getPixel(4, 4)
      assertEquals(255, AndroidColor.alpha(corner))
      assertTrue("fondo colorato: ${Integer.toHexString(corner)}", AndroidColor.red(corner) != AndroidColor.green(corner) || AndroidColor.green(corner) != AndroidColor.blue(corner))
      // Il pannello «stampa» al centro: chiaro, pieno e piatto. Una riga orizzontale vicino al fondo del pannello (sotto
      // il testo e fuori dal riflesso in cima) ha lo stesso colore da un bordo all'altro: niente sfocatura, niente
      // campione di uno sfondo.
      val cy = image.height / 2
      var bottom = cy
      while (bottom < image.height - 1 && luminance(image.getPixel(image.width / 2, bottom + 1)) > 0.8f) bottom++
      val row = bottom - 30
      val samples = (image.width / 4..image.width * 3 / 4 step 20).map { image.getPixel(it, row) }
      val first = samples.first()
      assertTrue("pannello chiaro: ${Integer.toHexString(first)}", luminance(first) > 0.85f)
      for (pixel in samples) {
        assertTrue("pannello piatto: ${Integer.toHexString(first)} contro ${Integer.toHexString(pixel)}",
          abs(AndroidColor.red(pixel) - AndroidColor.red(first)) <= 2 && abs(AndroidColor.blue(pixel) - AndroidColor.blue(first)) <= 2)
      }
      // Il testo c'e': pixel scuri dentro il pannello.
      val dark = (0 until image.width step 3).count { x -> (cy - 200..cy + 200 step 3).any { y -> luminance(image.getPixel(x, y)) < 0.35f } }
      assertTrue("testo nel pannello", dark > 10)
      image.recycle()
    }
  }

  /** Le immagini della galleria (il voto e l'impegno di ClasseViva) nei tre formati, per le tavole: `build/f8-10/immagini`. */
  private fun writeGalleryImages(dark: Boolean) {
    setUp(dark)
    val folder = File(out, "immagini").apply { mkdirs() }
    for ((name, content) in listOf<Pair<String, @androidx.compose.runtime.Composable () -> Unit>>("Voto" to { GradeImage() }, "Impegno" to { EventImage() })) {
      for (format in FluidShareImageFormat.all) {
        var bitmap: Bitmap? = null
        compose.runOnUiThread { bitmap = renderer.render(format, content) }
        val image = bitmap!!
        assertEquals(format.widthPx, image.width)
        image.compress(Bitmap.CompressFormat.PNG, 100, File(folder, "$name ${format.name.replace(':', 'x')}${if (dark) " scuro" else ""}.png").outputStream())
        image.recycle()
      }
    }
  }

  @Test fun theGalleryImagesInLight() = writeGalleryImages(dark = false)

  @Test fun theGalleryImagesInDark() = writeGalleryImages(dark = true)

  @Test fun inDarkTheImageIsDark() {
    setUp(dark = true)
    var bitmap: Bitmap? = null
    compose.runOnUiThread { bitmap = renderer.render(FluidShareImageFormat.Square) { FluidShareImageFrame { Text("Verifica di storia") } } }
    val image = bitmap!!
    image.compress(Bitmap.CompressFormat.PNG, 100, File(out, "prova-scuro-1x1.png").outputStream())
    assertTrue("pannello scuro", luminance(image.getPixel(image.width / 2, image.height / 2 + 40)) < 0.3f)
  }

  /** Una tela che scrive davvero e intanto registra ogni testo: dove (in punti della pagina) e che cosa. */
  private class TextCanvas(bitmap: Bitmap) : Canvas(bitmap) {
    class Run(val text: String, val baseline: Float, val top: Float, val bottom: Float)
    val runs = ArrayList<Run>()
    var bitmaps = 0
    private val matrix = Matrix()

    private fun record(text: CharSequence, y: Float, paint: Paint) {
      @Suppress("DEPRECATION") getMatrix(matrix)
      val pts = floatArrayOf(0f, y)
      matrix.mapPoints(pts)
      val scale = FloatArray(9).also(matrix::getValues)[Matrix.MSCALE_Y]
      val metrics = paint.fontMetrics
      runs += Run(text.toString(), pts[1], pts[1] + metrics.ascent * scale, pts[1] + metrics.descent * scale)
    }

    override fun drawText(text: CharArray, index: Int, count: Int, x: Float, y: Float, paint: Paint) {
      record(String(text, index, count), y, paint); super.drawText(text, index, count, x, y, paint)
    }
    override fun drawText(text: String, x: Float, y: Float, paint: Paint) { record(text, y, paint); super.drawText(text, x, y, paint) }
    override fun drawText(text: String, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
      record(text.substring(start, end), y, paint); super.drawText(text, start, end, x, y, paint)
    }
    override fun drawText(text: CharSequence, start: Int, end: Int, x: Float, y: Float, paint: Paint) {
      record(text.subSequence(start, end), y, paint); super.drawText(text, start, end, x, y, paint)
    }
    override fun drawTextRun(text: CharArray, index: Int, count: Int, contextIndex: Int, contextCount: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
      record(String(text, index, count), y, paint); super.drawTextRun(text, index, count, contextIndex, contextCount, x, y, isRtl, paint)
    }
    override fun drawTextRun(text: CharSequence, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
      record(text.subSequence(start, end), y, paint); super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
    }
    override fun drawTextRun(text: MeasuredText, start: Int, end: Int, contextStart: Int, contextEnd: Int, x: Float, y: Float, isRtl: Boolean, paint: Paint) {
      runs += Run("<misurato>", y, y, y); super.drawTextRun(text, start, end, contextStart, contextEnd, x, y, isRtl, paint)
    }
    override fun drawBitmap(bitmap: Bitmap, left: Float, top: Float, paint: Paint?) { bitmaps++; super.drawBitmap(bitmap, left, top, paint) }
  }

  private val long = "Lo studente partecipa con interesse e continuità, interviene in modo pertinente e rispetta le " +
    "consegne. Nel secondo periodo si consiglia di curare l'esposizione orale e di approfondire gli argomenti con " +
    "letture personali, che renderanno più solide le competenze già raggiunte."

  private fun longDocument(sections: Int): FluidPdfDocument = fluidPdfDocument("Pagella di prova", locale = Locale.ITALY) {
    title("Pagella del primo quadrimestre", "Classe 4ª · documento di prova")
    repeat(sections) { i ->
      heading("Materia ${i + 1}")
      row("Scritto", "8½", "Novembre 2026")
      row("Orale", "7")
      paragraph("$long $long")
      divider()
    }
  }

  @Test fun thePdfIsRealTextOnManyPagesWithNoLineCutInHalf() {
    setUp()
    val document = longDocument(sections = 18)
    val layout = FluidPdf.layout(document)
    assertTrue("piu' pagine: ${layout.pageCount}", layout.pageCount >= 3)
    val size = document.pageSize
    val contentTop = document.marginPt
    val contentBottom = size.heightPt - document.marginPt
    val written = StringBuilder()
    for (page in 1..layout.pageCount) {
      val bitmap = Bitmap.createBitmap(size.widthPt, size.heightPt, Bitmap.Config.ARGB_8888)
      val canvas = TextCanvas(bitmap)
      canvas.drawColor(AndroidColor.WHITE)
      layout.draw(page, canvas)
      if (page <= 2) bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(out, "pdf-pagina-$page.png").outputStream())
      bitmap.recycle()
      assertEquals("nessuna immagine nelle pagine di testo", 0, canvas.bitmaps)
      val runs = canvas.runs
      // L'intestazione in cima e il numero in fondo, su ogni pagina.
      assertTrue("intestazione a pagina $page", runs.any { it.text == "Pagella di prova" && it.baseline < contentTop + 12 })
      assertTrue("numero a pagina $page", runs.any { it.text == "Pagina $page di ${layout.pageCount}" && it.baseline > contentBottom })
      // Ogni riga del corpo sta intera fra il margine in alto e quello in basso: nessuna tagliata, nessuna nascosta.
      for (run in runs.filter { it.text != "Pagella di prova" && !it.text.startsWith("Pagina ") }) {
        assertTrue("«${run.text}» a pagina $page sopra il margine: ${run.top}", run.top >= contentTop - 0.5f)
        assertTrue("«${run.text}» a pagina $page sotto il margine: ${run.bottom} > $contentBottom", run.bottom <= contentBottom + 0.5f)
        written.append(run.text)
      }
    }
    // Tutto il testo c'e', una volta sola: i paragrafi spezzati fra due pagine non perdono ne' ripetono una riga.
    val expected = long.replace(" ", "")
    val all = written.toString().replace(" ", "").replace("\n", "")
    // Ogni paragrafo di prova porta la frase due volte: 18 sezioni, 36 volte, ne' una di piu' ne' una di meno.
    assertEquals("ogni paragrafo una volta sola", 36, Regex(Regex.escape(expected.take(60))).findAll(all).count())
    assertTrue(all.contains("Pagelladelprimoquadrimestre"))
  }

  @Test fun theReportCardOfTheSceneLaysOutWithItsComposeChart() {
    setUp()
    var pages = 0
    compose.runOnUiThread {
      val layout = writer.layout(sampleReportCard())
      pages = layout.pageCount
      val bitmap = Bitmap.createBitmap(595 * 2, 842 * 2, Bitmap.Config.ARGB_8888)
      val canvas = TextCanvas(bitmap)
      canvas.drawColor(AndroidColor.WHITE)
      canvas.scale(2f, 2f)
      layout.draw(1, canvas)
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(out, "pagella-pagina-1.png").outputStream())
      // Il grafico Compose dentro la pagina scrive il suo testo come testo (le sigle delle materie).
      assertTrue("testo del grafico: ${canvas.runs.map { it.text }}", canvas.runs.any { it.text.contains("Medie per materia") })
      assertTrue(canvas.runs.any { it.text == "MAT" })
    }
    assertTrue("la pagella e' lunga: $pages pagine", pages >= 3)
  }
}
