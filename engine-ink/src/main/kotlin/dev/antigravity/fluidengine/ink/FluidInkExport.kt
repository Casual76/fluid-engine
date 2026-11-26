package dev.antigravity.fluidengine.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Cosa ha scritto la consegna: la misura in pixel (o in punti, per un PDF), il tipo, le pagine. */
@Immutable
data class FluidInkExportResult(val width: Int, val height: Int, val mimeType: String, val pages: Int = 1)

/**
 * La consegna di un disegno: un file **nuovo**, mai sopra l'originale, scritto dove dice l'app ([OutputStream]: un file
 * suo, un `content://` di MediaStore, quello del selettore «Salva come»). Fuori dal thread principale.
 *
 * - [image]: la foto annotata a piena risoluzione (fino a [MaxPixels]), coi tratti alla stessa scala che si vedeva sul
 *   foglio, in PNG o in JPEG. Il verso dell'EXIF e' gia' applicato (da Android 9), e il file nuovo non ha EXIF: la
 *   posizione e la data dello scatto non escono con l'annotazione.
 * - [drawing]: il solo disegno (uno schizzo, una firma), sul colore della carta o trasparente, intero o ritagliato attorno
 *   ai tratti.
 * - [pdf]: un PDF nuovo con le stesse pagine e le stesse misure, ogni pagina disegnata sotto e i tratti sopra **come
 *   vettori** (restano nitidi a ogni zoom). La pagina sotto diventa un'immagine ad alta risoluzione: il testo del PDF
 *   originale non si seleziona piu' nella copia annotata (la domanda ad Alessio e' nel rapporto di F8.16: scriverci dentro
 *   davvero vuole una libreria PDF, PdfBox-Android, che pesa).
 */
object FluidInkExport {
  /**
   * Il tetto della foto annotata: 50 milioni di pixel (una foto da 50 MP passa intera; sopra si riduce). Una bitmap ARGB
   * di 50 MP pesa 200 MB: va bene sul Tab S9 e sui telefoni recenti, e chi ha poca memoria passa un tetto piu' basso.
   */
  const val MaxPixels: Int = 50_000_000

  /** La foto di [sheet] coi [strokes] sopra, in [format]. */
  suspend fun image(
    context: Context,
    sheet: FluidInkSheet,
    strokes: List<FluidInkStroke>,
    output: OutputStream,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 95,
    maxPixels: Int = MaxPixels,
  ): FluidInkExportResult = withContext(Dispatchers.Default) {
    val world = sheet.size
    if (world.width <= 0f || world.height <= 0f) throw IOException("Foglio senza misura")
    val base = decodeFull(context, sheet, maxPixels)
    val canvas = Canvas(base)
    val scale = base.width / world.width
    drawStrokes(canvas, strokes, Matrix().apply { setScale(scale, scale) }, scale)
    compress(base, format, quality, output)
    FluidInkExportResult(base.width, base.height, mimeOf(format)).also { base.recycle() }
  }

  /**
   * Il solo disegno: [size] e' il foglio (null: il riquadro dei tratti), [scale] i pixel per unita' (3: un foglio in dp
   * diventa un'immagine alla densita' di un telefono xxhdpi). Con [background] null il fondo e' trasparente (una firma da
   * mettere su un documento, in PNG). Con [cropToContent] l'immagine e' il riquadro dei tratti piu' [margin].
   */
  suspend fun drawing(
    strokes: List<FluidInkStroke>,
    output: OutputStream,
    size: Size? = null,
    scale: Float = 3f,
    background: Color? = Color.White,
    cropToContent: Boolean = false,
    margin: Float = 12f,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 95,
  ): FluidInkExportResult = withContext(Dispatchers.Default) {
    val bitmap = renderDrawing(strokes, size, scale, background, cropToContent, margin)
    compress(bitmap, format, quality, output)
    FluidInkExportResult(bitmap.width, bitmap.height, mimeOf(format)).also { bitmap.recycle() }
  }

  /** Il PDF annotato: le pagine di [sheet] (un PDF, o il foglio bianco come pagina) coi [strokes] sopra. */
  suspend fun pdf(
    sheet: FluidInkSheet,
    strokes: List<FluidInkStroke>,
    output: OutputStream,
    renderScale: Float = 2f,
  ): FluidInkExportResult {
    val writer = AndroidPdfWriter()
    val result = writePdf(sheet, strokes, writer, renderScale)
    withContext(Dispatchers.IO) { writer.finish(output) }
    return result
  }

  // --- Dentro ---------------------------------------------------------------------------------------------------------

  /** Le pagine del PDF, scritte da un [InkPdfWriter] (sul telefono `PdfDocument`, nelle prove un registratore). */
  internal suspend fun writePdf(sheet: FluidInkSheet, strokes: List<FluidInkStroke>, writer: InkPdfWriter, renderScale: Float): FluidInkExportResult {
    val pages = sheet.pages
    if (pages.isEmpty()) throw IOException("Foglio senza pagine")
    var maxW = 0
    var maxH = 0
    for ((index, page) in pages.withIndex()) {
      val w = max(1, page.width.roundToInt())
      val h = max(1, page.height.roundToInt())
      maxW = max(maxW, w); maxH = max(maxH, h)
      val under = sheet.pdf?.render(index, (w * renderScale).roundToInt())
      withContext(Dispatchers.Default) {
        writer.page(w, h) { canvas ->
          if (under != null) {
            canvas.drawBitmap(under, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG))
          } else {
            canvas.drawColor((sheet.paper ?: Color.White).toArgb())
          }
          canvas.save()
          canvas.clipRect(0f, 0f, w.toFloat(), h.toFloat())
          val onPage = strokes.filter { it.bounds.overlaps(page) }
          drawStrokes(canvas, onPage, Matrix().apply { setTranslate(-page.left, -page.top) }, 1f)
          canvas.restore()
        }
        under?.recycle()
      }
    }
    return FluidInkExportResult(maxW, maxH, "application/pdf", pages.size)
  }

  /** Il disegno in una bitmap (per [drawing] e per le prove). */
  internal fun renderDrawing(
    strokes: List<FluidInkStroke>,
    size: Size?,
    scale: Float,
    background: Color?,
    cropToContent: Boolean,
    margin: Float,
  ): Bitmap {
    val content = InkGeometry.union(strokes)
    val area = when {
      cropToContent && strokes.isNotEmpty() -> Rect(content.left - margin, content.top - margin, content.right + margin, content.bottom + margin)
      size != null -> Rect(0f, 0f, size.width, size.height)
      strokes.isNotEmpty() -> Rect(min(0f, content.left), min(0f, content.top), content.right + margin, content.bottom + margin)
      else -> Rect(0f, 0f, 1f, 1f)
    }
    val w = max(1, ceil(area.width * scale).toInt())
    val h = max(1, ceil(area.height * scale).toInt())
    // Un tetto anche qui: un disegno enorme a scala 3 non deve esaurire la memoria.
    val fit = min(1f, sqrt(MaxPixels.toFloat() / (w.toFloat() * h)))
    val bitmap = Bitmap.createBitmap(max(1, (w * fit).toInt()), max(1, (h * fit).toInt()), Bitmap.Config.ARGB_8888)
    if (background != null) bitmap.eraseColor(background.toArgb())
    val s = scale * fit
    drawStrokes(Canvas(bitmap), strokes, Matrix().apply { setTranslate(-area.left, -area.top); postScale(s, s) }, s)
    return bitmap
  }

  /** I tratti su [canvas] con [toCanvas]; [pixelsPerUnit] per la precisione dei tratti rifatti da androidx.ink. */
  internal fun drawStrokes(canvas: Canvas, strokes: List<FluidInkStroke>, toCanvas: Matrix, pixelsPerUnit: Float) {
    val renderer = InkRenderer.create()
    (renderer as? InkNativeRenderer)?.epsilon = InkBrushes.epsilonFor(1f / max(pixelsPerUnit, 1e-3f))
    for (s in strokes) renderer.draw(canvas, s, toCanvas)
  }

  private fun decodeFull(context: Context, sheet: FluidInkSheet, maxPixels: Int): Bitmap {
    val uri = sheet.uri
    if (uri == null) {
      val image = sheet.image ?: throw IOException("Foglio senza foto")
      return image.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, true)
    }
    val resolver = context.contentResolver
    if (Build.VERSION.SDK_INT >= 28) {
      return ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        val pixels = info.size.width.toLong() * info.size.height
        if (pixels > maxPixels) {
          val f = sqrt(maxPixels.toDouble() / pixels)
          decoder.setTargetSize(max(1, (info.size.width * f).toInt()), max(1, (info.size.height * f).toInt()))
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = true
      }.let { if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true) }
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth.toLong() * bounds.outHeight / (sample.toLong() * sample) > maxPixels) sample *= 2
    return resolver.openInputStream(uri)?.use {
      BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample; inMutable = true })
    } ?: throw IOException("Immagine illeggibile: $uri")
  }

  private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, output: OutputStream) {
    if (!bitmap.compress(format, quality.coerceIn(0, 100), output)) throw IOException("Codifica non riuscita")
    output.flush()
  }

  private fun mimeOf(format: Bitmap.CompressFormat): String = when (format) {
    Bitmap.CompressFormat.PNG -> "image/png"
    Bitmap.CompressFormat.JPEG -> "image/jpeg"
    else -> "image/webp"
  }
}

/** Chi scrive le pagine di un PDF. */
internal interface InkPdfWriter {
  fun page(width: Int, height: Int, draw: (Canvas) -> Unit)
  fun finish(output: OutputStream)
}

/** Lo scrittore vero, con `PdfDocument` di Android (le misure in punti tipografici). */
internal class AndroidPdfWriter : InkPdfWriter {
  private val document = PdfDocument()
  private var count = 0

  override fun page(width: Int, height: Int, draw: (Canvas) -> Unit) {
    count++
    val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, count).create())
    try {
      draw(page.canvas)
    } finally {
      document.finishPage(page)
    }
  }

  override fun finish(output: OutputStream) {
    try {
      document.writeTo(output)
      output.flush()
    } finally {
      document.close()
    }
  }
}
