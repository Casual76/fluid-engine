package dev.antigravity.fluidengine.ui.fluid.output

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintJob
import android.print.PrintManager
import dev.antigravity.fluidengine.ui.fluid.share.FluidImageSample
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

/**
 * La stampa con l'anteprima di Android (4.0, F8.10), per ogni pagina Compose e non solo per il contenuto web: come
 * `UIPrintInteractionController`. Android mostra l'anteprima, il foglio e la stampante, e la stessa anteprima ha «Salva
 * come PDF». Chiamare dal thread principale, con il contesto di un'attivita'.
 *
 * **I thread** (R2). Android chiama l'adattatore sul thread principale, ma lascia finire il lavoro altrove e rispondere da
 * li': l'impaginazione di un documento senza viste Compose, la sua scrittura, la lettura di un'immagine e la copia di un
 * PDF vanno su `Dispatchers.Default`, e l'annullamento dello spooler li ferma. Un documento con viste Compose resta sul
 * thread principale, dove le viste si compongono.
 */
object FluidPrint {
  /**
   * Stampa [document]. L'impaginazione segue il foglio scelto nell'anteprima (A4, Letter, …): i margini e lo stile
   * restano quelli del documento. Un documento con viste Compose si stampa con [FluidPdfWriter.print].
   */
  fun print(
    context: Context,
    document: FluidPdfDocument,
    jobName: String = document.title,
    style: FluidPdfStyle = FluidPdfStyle.Default,
  ): PrintJob? = printWith(context, document, jobName, style, null)

  internal fun printWith(
    context: Context,
    document: FluidPdfDocument,
    jobName: String,
    style: FluidPdfStyle,
    composer: FluidPdfComposer?,
  ): PrintJob? {
    require(!document.needsCompose || composer != null) { "Un documento con viste Compose si stampa con FluidPdfWriter.print" }
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return null
    val attributes = PrintAttributes.Builder()
      .setMediaSize(if (document.pageSize.name == FluidPdfPageSize.Letter.name) PrintAttributes.MediaSize.NA_LETTER else PrintAttributes.MediaSize.ISO_A4)
      .build()
    return manager.print(jobName, FluidDocumentPrintAdapter(context, document, jobName, style, composer), attributes)
  }

  /**
   * Stampa un'immagine (un'immagine da condividere, una foto) al centro del foglio, intera, coi margini del documento.
   * La bitmap resta del chiamante.
   */
  fun printImage(context: Context, image: Bitmap, jobName: String = "Immagine"): PrintJob? {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return null
    return manager.print(jobName, FluidImagePrintAdapter(jobName) { image }, PrintAttributes.Builder().build())
  }

  /** Stampa l'immagine di [uri], letta alla misura che serve a un foglio a 300 dpi. */
  fun printImage(context: Context, uri: Uri, jobName: String = "Immagine"): PrintJob? {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return null
    val resolver = context.applicationContext.contentResolver
    return manager.print(jobName, FluidImagePrintAdapter(jobName) { fluidDecodeForPrint(resolver, uri) }, PrintAttributes.Builder().build())
  }

  /** Stampa un PDF gia' fatto (un `content://`): Android lo passa com'e' alla stampante. */
  fun printPdf(context: Context, uri: Uri, jobName: String = "Documento"): PrintJob? {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return null
    val resolver = context.applicationContext.contentResolver
    return manager.print(jobName, FluidPdfFilePrintAdapter(jobName) { out ->
      requireNotNull(resolver.openInputStream(uri)) { "Il PDF non si legge" }.use { input -> input.copyTo(out) }
    }, PrintAttributes.Builder().build())
  }
}

/**
 * Un adattatore di stampa che lavora fuori dal thread principale (R2): [background] fa il lavoro su `Dispatchers.Default`
 * e risponde **una volta sola**, da li': fatto, fallito, o annullato (dallo spooler col [CancellationSignal], o da
 * [onFinish]). Il lavoro bloccante non si interrompe a meta', ma un lavoro annullato non risponde mai «fatto».
 */
internal abstract class FluidBackgroundPrintAdapter : PrintDocumentAdapter() {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  protected fun <T> background(
    signal: CancellationSignal?,
    work: () -> T,
    cancelled: () -> Unit,
    answer: (Result<T>) -> Unit,
  ) {
    val once = AtomicBoolean(false)
    fun reply(block: () -> Unit) {
      if (once.compareAndSet(false, true)) block()
    }
    val job = scope.launch {
      val result = runCatching(work)
      if (!isActive || signal?.isCanceled == true) reply(cancelled) else reply { answer(result) }
    }
    // Annullato prima di partire, o a meta': la risposta («annullato») c'e' sempre, e una volta sola.
    job.invokeOnCompletion { reply(cancelled) }
    signal?.setOnCancelListener { job.cancel() }
  }

  override fun onFinish() {
    scope.cancel()
  }
}

/** Un PDF fatto, scritto com'e' da [copy] nel file dello spooler. */
internal class FluidPdfFilePrintAdapter(
  private val jobName: String,
  private val copy: (java.io.OutputStream) -> Unit,
) : FluidBackgroundPrintAdapter() {
  override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
    callback.onLayoutFinished(
      PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
      false,
    )
  }

  override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
    if (cancellationSignal?.isCanceled == true) return callback.onWriteCancelled()
    background(cancellationSignal, { FileOutputStream(destination.fileDescriptor).use(copy) }, callback::onWriteCancelled) { result ->
      if (result.isSuccess) callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback.onWriteFailed("Il PDF non si legge")
    }
  }
}

/** Il foglio scelto nell'anteprima, in punti, come formato di pagina dell'engine. */
internal fun fluidPageSizeOf(attributes: PrintAttributes, fallback: FluidPdfPageSize): FluidPdfPageSize {
  val media = attributes.mediaSize ?: return fallback
  val portrait = media.asPortrait()
  val width = portrait.widthMils * 72 / 1000
  val height = portrait.heightMils * 72 / 1000
  if (width < 144 || height < 144) return fallback
  return FluidPdfPageSize(media.id ?: "stampa", width, height)
}

/** L'adattatore di stampa di un [FluidPdfDocument]: impagina per il foglio scelto, scrive tutte le pagine. */
internal class FluidDocumentPrintAdapter(
  private val context: Context,
  private val document: FluidPdfDocument,
  private val jobName: String,
  private val style: FluidPdfStyle,
  private val composer: FluidPdfComposer?,
) : FluidBackgroundPrintAdapter() {
  // Scritte dal lavoro dell'impaginazione e lette dalla scrittura: lo spooler non chiede di scrivere prima che
  // l'impaginazione abbia risposto.
  @Volatile private var laidOut: FluidPdfDocument = document
  @Volatile private var pages: List<FluidPdfPage> = emptyList()

  override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
    val sized = document.withPageSize(fluidPageSizeOf(newAttributes, document.pageSize))
    fun answer(result: Result<List<FluidPdfPage>>) {
      val laid = result.getOrElse { return callback.onLayoutFailed(it.message) }
      laidOut = sized
      pages = laid
      callback.onLayoutFinished(
        PrintDocumentInfo.Builder(fluidPdfName(jobName)).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(laid.size).build(),
        true,
      )
    }
    // Le viste Compose si compongono sul thread principale; il resto no (R2).
    if (composer != null) answer(runCatching { fluidLayoutPdf(sized, style, composer) })
    else background(cancellationSignal, { fluidLayoutPdf(sized, style, null) }, callback::onLayoutCancelled, ::answer)
  }

  override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
    if (cancellationSignal?.isCanceled == true) return callback.onWriteCancelled()
    val budget = FluidGlassResources.get(context).budget
    val document = laidOut
    val write = {
      val built = fluidBuildPdf(document, style, composer, budget)
      FileOutputStream(destination.fileDescriptor).use { built.writeAndClose(it) }
    }
    fun answer(result: Result<FluidPdfResult>) {
      when {
        cancellationSignal?.isCanceled == true -> callback.onWriteCancelled()
        result.isFailure -> callback.onWriteFailed(result.exceptionOrNull()?.message)
        else -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
      }
    }
    if (composer != null) answer(runCatching(write)) else background(cancellationSignal, write, callback::onWriteCancelled, ::answer)
  }
}

/** L'adattatore di un'immagine: una pagina, l'immagine intera al centro. L'immagine si legge fuori dal thread principale. */
internal class FluidImagePrintAdapter(private val jobName: String, private val image: () -> Bitmap?) : FluidBackgroundPrintAdapter() {
  @Volatile private var size: FluidPdfPageSize = FluidPdfPageSize.A4

  override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
    size = fluidPageSizeOf(newAttributes, FluidPdfPageSize.A4)
    callback.onLayoutFinished(
      PrintDocumentInfo.Builder("$jobName.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(),
      true,
    )
  }

  override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
    if (cancellationSignal?.isCanceled == true) return callback.onWriteCancelled()
    val page = size
    background(cancellationSignal, { fluidWriteImagePdf(image, page, destination) }, callback::onWriteCancelled) { result ->
      val error = result.exceptionOrNull()
      if (error != null) callback.onWriteFailed(error.message) else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
  }
}

/** La pagina di un'immagine nel file dello spooler; lancia se l'immagine non si legge o il PDF non si scrive. */
private fun fluidWriteImagePdf(image: () -> Bitmap?, size: FluidPdfPageSize, destination: ParcelFileDescriptor) {
  val bitmap = runCatching { image() }.getOrNull() ?: throw IllegalStateException("L'immagine non si legge")
  val pdf = PdfDocument()
  try {
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(size.widthPt, size.heightPt, 1).create())
    fluidDrawImageOnPage(page.canvas, bitmap, size, margin = 36f)
    pdf.finishPage(page)
    FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
  } finally {
    pdf.close()
  }
}

/** L'immagine intera al centro della pagina, dentro i margini. */
internal fun fluidDrawImageOnPage(canvas: Canvas, bitmap: Bitmap, size: FluidPdfPageSize, margin: Float) {
  val availW = size.widthPt - margin * 2
  val availH = size.heightPt - margin * 2
  val scale = min(availW / bitmap.width, availH / bitmap.height)
  val w = bitmap.width * scale
  val h = bitmap.height * scale
  val left = (size.widthPt - w) / 2f
  val top = (size.heightPt - h) / 2f
  canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
}

/**
 * L'immagine di [uri] ridotta a non piu' di [maxSide] pixel di lato (3508: un A4 a 300 dpi), letta a campioni da
 * [FluidImageSample]. Prima la lettura delle misure faceva uscire sempre null, e la stampa di un'immagine non partiva.
 */
internal fun fluidDecodeForPrint(resolver: android.content.ContentResolver, uri: Uri, maxSide: Int = 3508): Bitmap? =
  FluidImageSample.atMost(resolver, uri, maxSide)
