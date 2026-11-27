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
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
  class Rendered(val image: ImageBitmap, val widthPx: Int) {
    /** La sua parte nel tetto dei visori ([memory]), restituita quando la pagina esce da [ready]. */
    internal var credit: Closeable? = null
  }

  /** Le pagine pronte: si leggono nel disegno, e ridisegnano il foglio quando arrivano. Si tolgono con [release]. */
  val ready = mutableStateMapOf<Int, Rendered>()
  @Volatile protected var closed = false

  /**
   * R1 · **Il tetto delle pagine pronte**: quello dei visori, la categoria `VIEWER` del registro del processo
   * (`FluidGlassResources.budget`, lo stesso del PDF di engine-viewer: 48 MiB nel profilo Pieno, 24 nel Ridotto, 14 nel
   * Protetto, piu' bassi con un heap piccolo). Prima non c'era: sul Tab S9 a zoom 1,6 sei pagine A4 larghe 4096 px
   * facevano 384 MiB. Null: nessun tetto (un processo senza il registro, le prove che non lo vogliono).
   */
  internal var memory: FluidMemoryBudget? = runCatching { FluidGlassResources.current().budget }.getOrNull()

  /** Disegna la pagina [index] in [bitmap] (gia' bianca), scalata alla sua misura. */
  protected abstract fun draw(index: Int, bitmap: Bitmap)

  /**
   * Chiude le risorse vere (il `PdfRenderer`, il descrittore). R3: **mai mentre una pagina si disegna**: [close] dal filo
   * principale (l'utente esce, l'attivita' si ricrea) mentre [draw] lavora sul filo IO la rimanda alla fine del disegno.
   */
  protected open fun closeResources() {}

  /** R3: chi disegna adesso (al piu' uno, [render] passa dal lucchetto) e se le risorse sono gia' chiuse. */
  private val use = Any()
  private var drawing = false
  private var released = false

  /** Disegna la pagina [index] larga [widthPx] (con la carta bianca sotto, come Anteprima). */
  suspend fun render(index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
    lock.withLock {
      if (closed || index !in sizes.indices) return@withLock null
      val s = sizes[index]
      val w = widthPx.coerceIn(16, MaxPagePixels)
      val h = (w * s.height / s.width).roundToInt().coerceIn(16, MaxPagePixels)
      val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
      bitmap.eraseColor(android.graphics.Color.WHITE)
      val open = synchronized(use) {
        if (!closed) drawing = true
        !closed
      }
      if (!open) {
        bitmap.recycle()
        return@withLock null
      }
      try {
        draw(index, bitmap)
      } finally {
        val release = synchronized(use) {
          drawing = false
          (closed && !released).also { if (it) released = true }
        }
        if (release) closeResources()
      }
      bitmap
    }
  }

  /**
   * Come [render], ma mette la pagina fra quelle pronte, **dentro il tetto** ([memory]): se la pagina larga [widthPx] non
   * entra, prima escono le pronte che non stanno in [keep] (se dato: le pagine di adesso), poi si prova alla larghezza
   * piu' grande che entra nel posto libero (o a meta'), fino a [MinPagePixels], come fa il visore. Una pagina gia' pronta
   * larga almeno cosi' resta com'e'.
   */
  suspend fun prepare(index: Int, widthPx: Int, keep: Collection<Int>? = null) {
    if (index !in sizes.indices) return
    var width = widthPx.coerceIn(MinPagePixels, MaxPagePixels)
    var evicted = false
    while (true) {
      val current = ready[index]
      if (current != null && current.widthPx >= width) return
      val budget = memory
      val reservation = budget?.tryReserve(FluidMemoryCategory.VIEWER, pageBytes(index, width))
      if (budget == null || reservation != null) {
        val bitmap = try {
          render(index, width)
        } catch (e: Throwable) {
          reservation?.close()
          throw e
        }
        if (bitmap == null) {
          reservation?.close()
          return
        }
        val credit = reservation?.confirm(bitmap.byteCount.toLong())
        if (reservation == null || credit != null) {
          install(index, Rendered(bitmap.asImageBitmap(), bitmap.width).also { it.credit = credit })
          return
        }
        reservation.close()
        bitmap.recycle()
      } else if (!evicted && keep != null) {
        evicted = true
        val spare = ready.keys.filter { it != index && it !in keep }
        if (spare.isNotEmpty()) {
          spare.forEach { release(it) }
          continue
        }
      }
      if (width <= MinPagePixels) return
      // La larghezza piu' grande che entra in cio' che resta del tetto; se non c'e' posto nemmeno cosi', meta'.
      val fit = budget?.let { fitWidth(index, width, it) } ?: 0
      width = if (fit in MinPagePixels until width) fit else max(MinPagePixels, width / 2)
    }
  }

  /** La larghezza piu' grande (sotto [width]) a cui la pagina [index] entra nel posto libero del tetto, o 0. */
  private fun fitWidth(index: Int, width: Int, budget: FluidMemoryBudget): Int {
    val group = budget.usage(FluidMemoryCategory.VIEWER)
    val process = budget.usage()
    val free = minOf(group.limitBytes - group.usedBytes, process.limitBytes - process.usedBytes)
    if (free <= 0L) return 0
    var w = (width * sqrt(free.toDouble() / pageBytes(index, width))).toInt().coerceAtMost(width - 1)
    while (w > 0 && pageBytes(index, w) > free) w = (w * 0.97).toInt()
    return w
  }

  /** Toglie la pagina [index] da quelle pronte e ne restituisce la parte nel tetto. */
  fun release(index: Int) {
    ready.remove(index)?.credit?.close()
  }

  /** I byte della pagina [index] disegnata larga [widthPx], con le stesse misure di [render]. */
  private fun pageBytes(index: Int, widthPx: Int): Long {
    val s = sizes[index]
    val w = widthPx.coerceIn(16, MaxPagePixels)
    val h = (w * s.height / s.width).roundToInt().coerceIn(16, MaxPagePixels)
    return w.toLong() * h * 4L
  }

  private fun install(index: Int, page: Rendered) {
    if (closed) {
      page.credit?.close()
      return
    }
    // La pagina di prima (piu' sfocata) esce: la sua parte torna subito, i pixel col prossimo disegno.
    ready.put(index, page)?.credit?.close()
  }

  /**
   * Chiude il foglio: niente piu' pagine, le pronte restituiscono la loro parte nel tetto. Le risorse vere
   * ([closeResources]) si chiudono subito, o alla fine del disegno in corso (R3). Si puo' chiamare due volte.
   */
  final override fun close() {
    val release = synchronized(use) {
      closed = true
      (!drawing && !released).also { if (it) released = true }
    }
    ready.values.forEach { it.credit?.close() }
    ready.clear()
    if (release) closeResources()
  }

  companion object {
    /** Il lato piu' lungo di una pagina disegnata per la vista: 4096 px (il tetto delle texture dei telefoni deboli). */
    const val MaxPagePixels = 4096

    /**
     * La larghezza piu' piccola a cui una pagina scende quando il tetto e' pieno: 256 px, una miniatura che si legge
     * ancora come pagina (a occhio, 26 novembre 2026; le miniature del visore sono a 192).
     */
    const val MinPagePixels = 256
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

  /** R3: dopo l'ultimo disegno, mai sotto di lui ([InkPdfPages.close]). */
  override fun closeResources() {
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
        // Il registro del processo (R1): lo stesso tetto dei visori, anche se nessuno l'ha ancora aperto.
        InkPdfRendererPages.open(fd).also { it.memory = FluidGlassResources.get(context).budget }
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
