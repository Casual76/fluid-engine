package dev.antigravity.fluidengine.ink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Cosa sta sotto i tratti: un foglio bianco, una foto, le pagine di un PDF. Da' le pagine nelle coordinate del foglio
 * (per una foto i suoi pixel, per un PDF i punti tipografici), il modo in cui entrano nella vista e il fondo da disegnare.
 * La consegna ([FluidInkExport]) riparte da qui: la foto intera, il PDF originale.
 */
@Stable
class FluidInkSheet internal constructor(
  pages: List<Rect>,
  /** Vero se il foglio entra nella vista per larghezza e scorre in verticale (un PDF); falso se entra intero (una foto). */
  internal val fitWidth: Boolean,
  /** Il colore della carta (null per una foto: si vede la foto). */
  val paper: Color?,
  internal val image: ImageBitmap?,
  internal val pdf: InkPdfPages?,
  /** Un foglio bianco grande quanto la vista: le pagine le misura il foglio la prima volta. */
  internal val adaptive: Boolean,
  /** L'indirizzo della foto o del PDF di partenza (null per un foglio bianco o una foto data come bitmap). */
  val uri: Uri?,
  /** Il tipo del file di partenza: `image/…`, `application/pdf`, o null. */
  val mimeType: String?,
) {
  private var measured by mutableStateOf(pages)

  /** Le pagine, dall'alto in basso, nelle coordinate del foglio. */
  val pages: List<Rect> get() = measured

  /** Il riquadro di tutte le pagine. */
  val size: Size get() = measured.fold(Size.Zero) { acc, r -> Size(max(acc.width, r.right), max(acc.height, r.bottom)) }

  /** Il foglio bianco adattato alla vista: le sue misure in dp, una volta sola (poi restano, anche se la vista gira). */
  internal fun adapt(widthDp: Float, heightDp: Float) {
    if (!adaptive || measured.isNotEmpty() || widthDp <= 0f || heightDp <= 0f) return
    measured = listOf(Rect(0f, 0f, widthDp, heightDp))
  }

  companion object {
    /**
     * Un foglio bianco: [size] nelle sue unita' (per esempio 595 × 842 per un A4 in punti), o null per un foglio grande
     * quanto la vista la prima volta che si misura (uno schizzo, una firma), in dp. La carta e' [paper]: bianca anche in
     * scuro, come un foglio vero (la domanda ad Alessio e' nel rapporto di F8.16).
     */
    fun blank(size: Size? = null, paper: Color = Color.White): FluidInkSheet = FluidInkSheet(
      pages = if (size == null) emptyList() else listOf(Rect(0f, 0f, size.width, size.height)),
      fitWidth = false, paper = paper, image = null, pdf = null, adaptive = size == null, uri = null, mimeType = null,
    )

    /**
     * Una foto gia' decodificata. [size] e' la misura vera della foto (i tratti stanno nei suoi pixel), se [image] e' una
     * copia ridotta per lo schermo; [uri] e' la foto intera per la consegna.
     */
    fun image(image: ImageBitmap, size: IntSize = IntSize(image.width, image.height), uri: Uri? = null, mimeType: String? = null): FluidInkSheet =
      FluidInkSheet(
        pages = listOf(Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())),
        fitWidth = false, paper = null, image = image, pdf = null, adaptive = false, uri = uri, mimeType = mimeType,
      )

    /** Un PDF dalle sue pagine (le prove, o pagine gia' aperte). */
    internal fun pdf(pages: InkPdfPages, uri: Uri?): FluidInkSheet = FluidInkSheet(
      pages = pdfLayout(pages.sizes), fitWidth = true, paper = Color.White, image = null, pdf = pages,
      adaptive = false, uri = uri, mimeType = "application/pdf",
    )

    /** La distanza fra due pagine di un PDF, in punti (a occhio, 25/11/2026, come Anteprima su iPhone). */
    const val PdfPageGap: Float = 12f
  }
}

/**
 * Le pagine di un PDF aperto, disegnate quando servono alla risoluzione che serve (fuori dal thread principale, una
 * pagina alla volta: `PdfRenderer` non regge due fili). Astratta perche' sul computer non c'e' `PdfRenderer`: le prove
 * passano pagine finte ([draw]), il telefono quelle vere ([InkPdfRendererPages]).
 */
internal abstract class InkPdfPages(val sizes: List<Size>) : Closeable {
  private val lock = Mutex()

  /** La pagina disegnata, con la larghezza in pixel con cui e' stata chiesta. */
  class Rendered(val image: ImageBitmap, val widthPx: Int)

  /** Le pagine pronte: si leggono nel disegno, e ridisegnano il foglio quando arrivano. */
  val ready = mutableStateMapOf<Int, Rendered>()
  @Volatile protected var closed = false

  /** Disegna la pagina [index] in [bitmap] (gia' bianca), scalata alla sua misura. */
  protected abstract fun draw(index: Int, bitmap: Bitmap)

  /** Disegna la pagina [index] larga [widthPx] (con la carta bianca sotto, come Anteprima). */
  suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    lock.withLock {
      if (closed || index !in sizes.indices) return@withLock null
      val s = sizes[index]
      val w = widthPx.coerceIn(16, MaxPagePixels)
      val h = (w * s.height / s.width).roundToInt().coerceIn(16, MaxPagePixels)
      val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(android.graphics.Color.WHITE)
      draw(index, bitmap)
      bitmap
    }
  }

  /** Come [render], ma mette la pagina fra quelle pronte. */
  suspend fun prepare(index: Int, widthPx: Int) {
    val current = ready[index]
    if (current != null && current.widthPx >= widthPx) return
    val bitmap = render(index, widthPx) ?: return
    ready[index] = Rendered(bitmap.asImageBitmap(), bitmap.width)
  }

  override fun close() {
    closed = true
  }

  companion object {
    /** Il lato piu' lungo di una pagina disegnata per la vista: 4096 px (il tetto delle texture dei telefoni deboli). */
    const val MaxPagePixels = 4096
  }
}

/** Le pagine vere, con `PdfRenderer`. */
internal class InkPdfRendererPages private constructor(
  private val descriptor: ParcelFileDescriptor,
  private val renderer: PdfRenderer,
) : InkPdfPages(List(renderer.pageCount) { i -> renderer.openPage(i).use { Size(it.width.toFloat(), it.height.toFloat()) } }) {

  override fun draw(index: Int, bitmap: Bitmap) {
    renderer.openPage(index).use { it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
  }

  override fun close() {
    super.close()
    try {
      renderer.close()
    } catch (_: RuntimeException) {
    }
    descriptor.close()
  }

  companion object {
    fun open(descriptor: ParcelFileDescriptor): InkPdfRendererPages = InkPdfRendererPages(descriptor, PdfRenderer(descriptor))
  }
}

/** Le pagine di un PDF in fila, con [FluidInkSheet.PdfPageGap] fra l'una e l'altra, centrate sulla piu' larga. */
internal fun pdfLayout(sizes: List<Size>): List<Rect> {
  val width = sizes.maxOfOrNull { it.width } ?: 0f
  var y = 0f
  return sizes.map { s ->
    val left = (width - s.width) / 2f
    Rect(left, y, left + s.width, y + s.height).also { y += s.height + FluidInkSheet.PdfPageGap }
  }
}

/** Lo stato dell'apertura di un foglio da un file: in corso, pronto o fallito. */
@Stable
sealed interface FluidInkSheetLoad {
  data object Loading : FluidInkSheetLoad
  class Ready(val sheet: FluidInkSheet) : FluidInkSheetLoad
  class Failed(val error: Throwable) : FluidInkSheetLoad
}

/**
 * Apre la foto [uri] per annotarla: la misura vera (i tratti stanno nei suoi pixel) e una copia ridotta per lo schermo,
 * col verso giusto (EXIF, da Android 9). Fuori dal thread principale. Un indirizzo dato da un'altra app (MyCloud Gallery)
 * si legge finche' il permesso vale: l'annotazione lo apre subito, e la consegna lo rilegge una volta alla fine.
 */
@Composable
fun rememberFluidInkImageSheet(uri: Uri, maxDisplaySide: Int = 2560): FluidInkSheetLoad {
  val context = LocalContext.current.applicationContext
  return produceState<FluidInkSheetLoad>(FluidInkSheetLoad.Loading, uri, maxDisplaySide) {
    value = try {
      val (image, size) = withContext(Dispatchers.IO) { decodeForDisplay(context, uri, maxDisplaySide) }
      FluidInkSheetLoad.Ready(
        FluidInkSheet.image(image.asImageBitmap(), size, uri, context.contentResolver.getType(uri) ?: "image/*"),
      )
    } catch (e: Exception) {
      FluidInkSheetLoad.Failed(e)
    }
  }.value
}

/** Apre il PDF [uri] per annotarlo: le pagine in fila, disegnate quando servono. Si chiude quando esce dalla composizione. */
@Composable
fun rememberFluidInkPdfSheet(uri: Uri): FluidInkSheetLoad {
  val context = LocalContext.current.applicationContext
  val load = produceState<FluidInkSheetLoad>(FluidInkSheetLoad.Loading, uri) {
    value = try {
      val pages = withContext(Dispatchers.IO) {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IOException("PDF illeggibile: $uri")
        InkPdfRendererPages.open(fd)
      }
      FluidInkSheetLoad.Ready(FluidInkSheet.pdf(pages, uri))
    } catch (e: Exception) {
      FluidInkSheetLoad.Failed(e)
    }
  }.value
  DisposableEffect(load) {
    onDispose { (load as? FluidInkSheetLoad.Ready)?.sheet?.pdf?.close() }
  }
  return load
}

/** La misura vera della foto e una copia ridotta col lato lungo al piu' [maxSide], col verso dell'EXIF da Android 9. */
internal fun decodeForDisplay(context: Context, uri: Uri, maxSide: Int): Pair<Bitmap, IntSize> {
  val resolver = context.contentResolver
  if (Build.VERSION.SDK_INT >= 28) {
    var full = IntSize.Zero
    val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
      full = IntSize(info.size.width, info.size.height)
      val scale = maxSide.toFloat() / max(info.size.width, info.size.height)
      if (scale < 1f) decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
      decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    return bitmap to full
  }
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
  if (bounds.outWidth <= 0) throw IOException("Immagine illeggibile: $uri")
  var sample = 1
  while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
  val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    ?: throw IOException("Immagine illeggibile: $uri")
  return bitmap to IntSize(bounds.outWidth, bounds.outHeight)
}
