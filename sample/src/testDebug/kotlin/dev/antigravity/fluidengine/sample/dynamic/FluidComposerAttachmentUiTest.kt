package dev.antigravity.fluidengine.sample.dynamic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAttachmentDefaults
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.sample.viewer.VisoriFiles
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidFileThumbnails
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidThumbnailLook
import dev.antigravity.fluidengine.viewer.rememberFluidThumbnailLook
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Gli allegati del composer con l'anteprima (F6B.16, la voce di Alessio sul vassoio dell'assistente: «voglio anche le
 * anteprime delle foto caricate»): un allegato foto mostra **la foto**, ritagliata nel riquadro, e un PDF **la sua prima
 * pagina**, tutte e due dalla miniatura vera di `FluidFileThumbnails` (F6C.17), sui file della galleria copiati nella
 * cartella dell'app.
 *
 * Il `PdfRenderer` della piattaforma è nativo, e sulla JVM non c'è (lo dice anche `ViewerBoardPhoneTest`): qui lo
 * sostituisce un'ombra di prova che apre il file, conta le pagine e disegna la pagina chiesta su carta bianca con l'arte
 * della prima pagina del quaderno della galleria. Tutto il resto è la strada dell'engine: `FluidFileThumbnails` →
 * `FluidPdfPageSource` → `PdfRenderer.openPage(0).render(…)` → la miniatura in cache → il riquadro dell'allegato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi", shadows = [ShadowFirstPagePdfRenderer::class, ShadowFirstPagePdfPage::class])
class FluidComposerAttachmentUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  // La cache delle miniature non si svuota: è di tutta la suite (la galleria «Visori» la usa), e i file di questa prova
  // stanno in una cartella loro ("composer-prova"), con chiavi loro.
  @Before fun clean() {
    ShadowFirstPagePdfPage.rendered.clear()
  }

  /** Il riquadro del nodo [tag] dalla finestra, con `PixelCopy` (come `ViewerBoardPhoneTest`). */
  private fun capture(tag: String): Bitmap {
    val bounds = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = PixelCopy.ERROR_UNKNOWN
    compose.runOnIdle {
      PixelCopy.request(window, Rect(0, 0, view.width, view.height), bitmap, { result = it }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals(PixelCopy.SUCCESS, result)
    return Bitmap.createBitmap(bitmap, bounds.left.roundToInt(), bounds.top.roundToInt(),
      bounds.width.roundToInt().coerceAtMost(view.width - bounds.left.roundToInt()),
      bounds.height.roundToInt().coerceAtMost(view.height - bounds.top.roundToInt()))
  }

  private fun files(): Pair<FluidViewerFile, FluidViewerFile> = runBlocking {
    val context = RuntimeEnvironment.getApplication()
    val photo = ComposerAttachmentFiles.photos.first()
    val prepared = VisoriFiles.prepare(context, listOf(photo, ComposerAttachmentFiles.document), "composer-prova")
    prepared[0] to prepared[1]
  }

  private fun close(a: Int, b: Int, tolerance: Int = 14): Boolean =
    listOf(16, 8, 0).all { shift -> abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) <= tolerance }

  @Test fun `un allegato foto mostra la foto, e un PDF la sua prima pagina`() {
    val (photo, pdf) = files()
    // Il file è un PDF vero: l'intestazione comincia con «%PDF».
    assertEquals("%PDF", String(File(requireNotNull(pdf.uri.path)).readBytes().copyOf(4)))
    var look: FluidThumbnailLook? = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        look = rememberFluidThumbnailLook()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ComposerFileAttachment(photo, "Pianta del secondo piano", photo = true, onRemove = {}, Modifier.testTag("foto"))
          ComposerFileAttachment(pdf, "Quaderno di campo", photo = false, onRemove = {}, Modifier.testTag("pdf"))
        }
      }
    }
    val px = (FluidComposerAttachmentDefaults.Height.value * RuntimeEnvironment.getApplication().resources.displayMetrics.density).roundToInt()
    compose.waitForIdle()
    val tones = requireNotNull(look)
    compose.waitUntil(20_000) {
      FluidFileThumbnails.cached(photo, px, tones) != null && FluidFileThumbnails.cached(pdf, px, tones) != null
    }
    compose.waitForIdle()

    // La foto: la miniatura è il contenuto (non l'icona del tipo), riempie il riquadro, e il centro del riquadro è il
    // centro della foto ritagliata.
    val picture = FluidFileThumbnails.cached(photo, px, tones)!!
    assertFalse("la foto non è il segnaposto", picture.isPlaceholder)
    assertTrue("la foto riempie il riquadro", picture.fillsFrame)
    val tile = capture("foto")
    val source = picture.image.asAndroidBitmap()
    val tileCenter = tile.getPixel(tile.width / 2, tile.height / 2)
    val photoCenter = source.getPixel(source.width / 2, source.height / 2)
    assertTrue("il centro del riquadro è il centro della foto: %08x contro %08x".format(tileCenter, photoCenter),
      close(tileCenter, photoCenter))
    assertTrue("nel riquadro c'è una foto, non un fondo uniforme", distinctColours(tile) > 40)

    // Il PDF: la miniatura è la sua prima pagina (la pagina 0 resa dal `PdfRenderer`), intera su carta bianca.
    val page = FluidFileThumbnails.cached(pdf, px, tones)!!
    assertFalse("il PDF non è il segnaposto", page.isPlaceholder)
    assertFalse("un foglio si mostra intero, non ritagliato", page.fillsFrame)
    assertEquals("si è resa la prima pagina, e solo lei", listOf(0), ShadowFirstPagePdfPage.rendered.distinct())
    val card = capture("pdf")
    val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
    // Il foglio sta a sinistra nella scheda, 6 dp dal bordo, largo 44 dp e alto 56: il suo centro.
    val cx = ((6f + FluidComposerAttachmentDefaults.FilePreviewWidth.value / 2f) * density).toInt()
    val cy = card.height / 2
    val shown = card.getPixel(cx, cy)
    val pageImage = page.image.asAndroidBitmap()
    val expected = pageImage.getPixel(pageImage.width / 2, pageImage.height / 2)
    assertTrue("il centro del foglio nella scheda è il centro della prima pagina: %08x contro %08x".format(shown, expected),
      close(shown, expected, tolerance = 24))
    assertTrue("sulla pagina c'è la carta bianca", whiteShare(pageImage) > 0.2f)
  }

  private fun distinctColours(bitmap: Bitmap): Int {
    val seen = HashSet<Int>()
    for (y in 0 until bitmap.height step 3) for (x in 0 until bitmap.width step 3) seen += bitmap.getPixel(x, y) and 0xF0F0F0
    return seen.size
  }

  private fun whiteShare(bitmap: Bitmap): Float {
    var white = 0
    var all = 0
    for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
      val p = bitmap.getPixel(x, y)
      all++
      if (((p shr 16) and 0xFF) > 240 && ((p shr 8) and 0xFF) > 240 && (p and 0xFF) > 240) white++
    }
    return white.toFloat() / all.coerceAtLeast(1)
  }
}

/**
 * Il `PdfRenderer` sulla JVM, per la prova: apre il descrittore senza pdfium, dice sei pagine (quelle del quaderno della
 * galleria) e apre la pagina chiesta. Non è un'API per le app.
 */
@Implements(PdfRenderer::class)
class ShadowFirstPagePdfRenderer {
  @RealObject private lateinit var real: PdfRenderer

  /** Il descrittore resta del renderer, come col `PdfRenderer` vero; pdfium non c'è e niente lo legge qui. */
  @Implementation protected fun __constructor__(input: ParcelFileDescriptor) = Unit

  @Implementation protected fun getPageCount(): Int = 6

  @Implementation protected fun openPage(index: Int): PdfRenderer.Page = ReflectionHelpers.callConstructor(
    PdfRenderer.Page::class.java,
    ClassParameter.from(PdfRenderer::class.java, real),
    ClassParameter.from(Int::class.javaPrimitiveType, index),
  )

  @Implementation protected fun close() = Unit
}

/** La pagina del `PdfRenderer` di prova: A4 in punti, disegnata su carta bianca con l'arte della prima pagina. */
@Implements(PdfRenderer.Page::class)
class ShadowFirstPagePdfPage {
  private var index = -1

  @Implementation protected fun __constructor__(renderer: PdfRenderer, index: Int) {
    this.index = index
  }

  @Implementation protected fun getIndex(): Int = index
  @Implementation protected fun getWidth(): Int = 595
  @Implementation protected fun getHeight(): Int = 842

  @Implementation protected fun render(destination: Bitmap, destClip: Rect?, transform: Matrix?, renderMode: Int) {
    synchronized(rendered) { rendered += index }
    val canvas = Canvas(destination)
    if (transform != null) canvas.concat(transform)
    canvas.drawRect(0f, 0f, 595f, 842f, Paint().apply { color = android.graphics.Color.WHITE })
    val art = BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources, R.drawable.viewer_notes_cover)
    if (art != null) canvas.drawBitmap(art, null, RectF(60f, 120f, 535f, 520f), Paint(Paint.FILTER_BITMAP_FLAG))
  }

  @Implementation protected fun close() = Unit

  companion object {
    /** Le pagine rese, nell'ordine. */
    val rendered: MutableList<Int> = mutableListOf()
  }
}
