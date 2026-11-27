package dev.antigravity.fluidengine.ink

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * F8.16 · La consegna, coi pixel veri (Robolectric, grafica nativa). Sul computer androidx.ink non ha la sua libreria
 * nativa: i tratti li disegna il ripiego dell'engine, con gli stessi punti, colori e spessori, e le prove guardano quelli.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class FluidInkExportTest {
  private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

  private fun stroke(id: Long, color: Color, size: Float, vararg xy: Float, tool: FluidInkTool = FluidInkTool.Pen) =
    FluidInkStroke(id, tool, color, size, FluidInkPoints.of(*xy), FluidInkInput.Touch)

  private fun alpha(argb: Int) = argb ushr 24
  private fun red(argb: Int) = (argb shr 16) and 0xFF
  private fun green(argb: Int) = (argb shr 8) and 0xFF
  private fun blue(argb: Int) = argb and 0xFF

  @Test fun onTheComputerTheFallbackDraws() {
    assertFalse(FluidInk.nativeAvailable)
  }

  @Test fun aSignatureComesOutTransparentAndCropped() = runBlocking {
    val strokes = listOf(stroke(1, Color.Blue, 4f, 100f, 100f, 200f, 100f))
    val out = ByteArrayOutputStream()
    val result = FluidInkExport.drawing(strokes, out, scale = 2f, background = null, cropToContent = true, margin = 10f)
    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    // Il riquadro dei tratti (100 × 0, piu' lo spessore e il margine) a scala 2.
    assertEquals(result.width, bitmap.width)
    assertTrue("larghezza ${bitmap.width}", bitmap.width in 230..270)
    assertEquals("image/png", result.mimeType)
    // Trasparente fuori dal tratto, blu sul tratto.
    assertEquals(0, alpha(bitmap.getPixel(2, 2)))
    val mid = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
    assertTrue("al centro ${Integer.toHexString(mid)}", alpha(mid) > 200 && blue(mid) > 200 && red(mid) < 60)
  }

  @Test fun theAnnotatedPhotoKeepsItsSizeAndTheStrokesLandWhereTheyWereDrawn() = runBlocking {
    // Una «foto» 400 × 300 grigia, data come bitmap; i tratti sono nei suoi pixel.
    val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF808080.toInt()) }
    val sheet = FluidInkSheet.image(photo.asImageBitmap(), IntSize(400, 300))
    val strokes = listOf(stroke(1, Color.Red, 10f, 50f, 150f, 350f, 150f))
    val out = ByteArrayOutputStream()
    val result = FluidInkExport.image(context, sheet, strokes, out, Bitmap.CompressFormat.PNG)
    val bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    assertEquals(400, result.width)
    assertEquals(300, bitmap.height)
    val on = bitmap.getPixel(200, 150)
    val off = bitmap.getPixel(200, 50)
    assertTrue("sul tratto ${Integer.toHexString(on)}", red(on) > 220 && green(on) < 40)
    assertEquals(0x80, red(off))
  }

  @Test fun theHighlighterIsTransparentAndDoesNotDarkenWhereItCrossesItself() = runBlocking {
    val color = Color(0xFFFFCC00).copy(alpha = 0.5f)
    // Un tratto che torna su se stesso: dove passa due volte non deve essere piu' scuro.
    val strokes = listOf(stroke(1, color, 20f, 20f, 50f, 180f, 50f, 20f, 50f, tool = FluidInkTool.Highlighter))
    val bitmap = FluidInkExport.renderDrawing(strokes, Size(200f, 100f), 1f, Color.White, cropToContent = false, margin = 0f)
    val once = bitmap.getPixel(100, 50)
    // Bianco + giallo a meta': circa (255, 230, 128); con due passate il blu scenderebbe verso 64.
    assertTrue("evidenziatore ${Integer.toHexString(once)}", blue(once) in 110..150 && red(once) > 240)
  }

  @Test fun theAnnotatedPdfHasTheSamePagesAndEachStrokeOnItsPage() = runBlocking {
    val pages = FakePages(listOf(Size(300f, 400f), Size(300f, 400f)))
    val sheet = FluidInkSheet.pdf(pages, uri = null)
    val gap = FluidInkSheet.PdfPageGap
    // Un tratto sulla prima pagina, uno sulla seconda (in coordinate del foglio: la seconda comincia a 400 + gap).
    val strokes = listOf(
      stroke(1, Color.Red, 6f, 50f, 100f, 250f, 100f),
      stroke(2, Color.Blue, 6f, 50f, 400f + gap + 200f, 250f, 400f + gap + 200f),
    )
    val writer = RecordingWriter()
    val result = FluidInkExport.writePdf(sheet, strokes, writer, renderScale = 1f)
    assertEquals(2, result.pages)
    assertEquals(listOf(300 to 400, 300 to 400), writer.pages.map { it.width to it.height })
    val first = writer.pages[0]
    val second = writer.pages[1]
    // La pagina sotto c'e' (la pagina finta e' verde chiara), e ogni tratto sta sulla sua pagina, alla sua altezza.
    assertTrue(green(first.getPixel(10, 10)) > 200 && red(first.getPixel(10, 10)) < 220)
    assertTrue(red(first.getPixel(150, 100)) > 220 && blue(first.getPixel(150, 100)) < 60)
    assertTrue(blue(second.getPixel(150, 200)) > 220 && red(second.getPixel(150, 200)) < 60)
    // Il tratto della seconda pagina non finisce sulla prima, e viceversa.
    assertTrue(blue(first.getPixel(150, 200)) < 120)
    assertTrue(red(second.getPixel(150, 100)) < 220 || green(second.getPixel(150, 100)) > 200)
  }

  @Test fun theBlankSheetBecomesAOnePagePdf() = runBlocking {
    val sheet = FluidInkSheet.blank(Size(595f, 842f))
    val writer = RecordingWriter()
    val result = FluidInkExport.writePdf(sheet, listOf(stroke(1, Color.Black, 3f, 10f, 10f, 500f, 800f)), writer, 1f)
    assertEquals(1, result.pages)
    assertEquals(595, writer.pages.single().width)
    assertEquals(0xFF, red(writer.pages.single().getPixel(590, 5)))
  }

  @Test fun anExportThatStopsHalfwayClosesThePdf() = runBlocking {
    // R1-C (review R1, Sol): la seconda pagina non si disegna. Il documento gia' aperto si chiude lo stesso.
    val broken = object : InkPdfPages(listOf(Size(300f, 400f), Size(300f, 400f))) {
      override fun draw(index: Int, bitmap: Bitmap) {
        if (index == 1) throw IllegalStateException("pagina rotta")
        bitmap.eraseColor(0xFFC8F064.toInt())
      }
    }
    val writer = RecordingWriter()
    try {
      FluidInkExport.pdfWith(FluidInkSheet.pdf(broken, uri = null), emptyList(), ByteArrayOutputStream(), 1f, writer)
      fail("la pagina rotta ferma la consegna")
    } catch (_: IllegalStateException) {
    }
    assertEquals(1, writer.pages.size)
    assertFalse(writer.finished)
    assertTrue("il documento si chiude anche se la consegna si ferma", writer.closed)
  }

  @Test fun aCancelledExportClosesThePdf() = runBlocking {
    // La seconda pagina e' lenta (un PDF pesante): l'annullo arriva mentre si disegna.
    val pages = object : InkPdfPages(List(4) { Size(300f, 400f) }) {
      override fun draw(index: Int, bitmap: Bitmap) {
        if (index == 1) Thread.sleep(1_500)
        bitmap.eraseColor(0xFFC8F064.toInt())
      }
    }
    val writer = RecordingWriter()
    val job = launch(Dispatchers.Default) {
      FluidInkExport.pdfWith(FluidInkSheet.pdf(pages, uri = null), emptyList(), ByteArrayOutputStream(), 1f, writer)
    }
    // Annullata dopo la prima pagina (l'utente chiude il foglio di condivisione).
    while (writer.pages.isEmpty()) delay(5)
    job.cancelAndJoin()
    assertEquals(1, writer.pages.size)
    assertFalse(writer.finished)
    assertTrue("il documento si chiude anche annullando", writer.closed)
  }

  @Test fun aCompleteExportClosesThePdfOnce() = runBlocking {
    val writer = RecordingWriter()
    FluidInkExport.pdfWith(FluidInkSheet.pdf(FakePages(listOf(Size(300f, 400f))), uri = null), emptyList(), ByteArrayOutputStream(), 1f, writer)
    assertTrue(writer.finished)
    assertTrue(writer.closed)
  }

  /** Pagine di PDF finte: ognuna verde chiara con un bordo. */
  private class FakePages(sizes: List<Size>) : InkPdfPages(sizes) {
    override fun draw(index: Int, bitmap: Bitmap) {
      bitmap.eraseColor(0xFFC8F064.toInt())
    }
  }

  /** Lo scrittore delle prove: ogni pagina e' una bitmap alla misura in punti. */
  private class RecordingWriter : InkPdfWriter {
    val pages = mutableListOf<Bitmap>()
    var finished = false
    /** Come `PdfDocument`: chiuderlo due volte non fa niente. */
    var closed = false
    override fun page(width: Int, height: Int, draw: (Canvas) -> Unit) {
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      draw(Canvas(bitmap))
      pages += bitmap
    }

    /** Come lo scrittore vero: la fine chiude il documento. */
    override fun finish(output: OutputStream) {
      finished = true
      close()
    }

    override fun close() {
      closed = true
    }
  }
}
