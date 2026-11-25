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
import java.io.FileOutputStream
import kotlin.math.min

/**
 * La stampa con l'anteprima di Android (4.0, F8.10), per ogni pagina Compose e non solo per il contenuto web: come
 * `UIPrintInteractionController`. Android mostra l'anteprima, il foglio e la stampante, e la stessa anteprima ha «Salva
 * come PDF». Chiamare dal thread principale, con il contesto di un'attivita'.
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
    return manager.print(jobName, object : PrintDocumentAdapter() {
      override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
        if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
        callback.onLayoutFinished(
          PrintDocumentInfo.Builder(jobName).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(),
          false,
        )
      }

      override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
        val ok = runCatching {
          resolver.openInputStream(uri)!!.use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
        }.isSuccess
        if (ok) callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) else callback.onWriteFailed("Il PDF non si legge")
      }
    }, PrintAttributes.Builder().build())
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
) : PrintDocumentAdapter() {
  private var laidOut: FluidPdfDocument = document
  private var pages: List<FluidPdfPage> = emptyList()

  override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
    val result = runCatching {
      laidOut = document.withPageSize(fluidPageSizeOf(newAttributes, document.pageSize))
      pages = fluidLayoutPdf(laidOut, style, composer)
    }
    if (result.isFailure) return callback.onLayoutFailed(result.exceptionOrNull()?.message)
    callback.onLayoutFinished(
      PrintDocumentInfo.Builder(fluidPdfName(jobName)).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages.size).build(),
      true,
    )
  }

  override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
    val result = runCatching {
      val built = fluidBuildPdf(laidOut, style, composer, FluidGlassResources.get(context).budget)
      FileOutputStream(destination.fileDescriptor).use { built.writeAndClose(it) }
    }
    when {
      cancellationSignal?.isCanceled == true -> callback.onWriteCancelled()
      result.isFailure -> callback.onWriteFailed(result.exceptionOrNull()?.message)
      else -> callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
  }
}

/** L'adattatore di un'immagine: una pagina, l'immagine intera al centro. */
internal class FluidImagePrintAdapter(private val jobName: String, private val image: () -> Bitmap?) : PrintDocumentAdapter() {
  private var size: FluidPdfPageSize = FluidPdfPageSize.A4

  override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes, cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
    if (cancellationSignal?.isCanceled == true) return callback.onLayoutCancelled()
    size = fluidPageSizeOf(newAttributes, FluidPdfPageSize.A4)
    callback.onLayoutFinished(
      PrintDocumentInfo.Builder("$jobName.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_PHOTO).setPageCount(1).build(),
      true,
    )
  }

  override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal?, callback: WriteResultCallback) {
    val bitmap = runCatching { image() }.getOrNull() ?: return callback.onWriteFailed("L'immagine non si legge")
    val pdf = PdfDocument()
    val result = runCatching {
      val page = pdf.startPage(PdfDocument.PageInfo.Builder(size.widthPt, size.heightPt, 1).create())
      fluidDrawImageOnPage(page.canvas, bitmap, size, margin = 36f)
      pdf.finishPage(page)
      FileOutputStream(destination.fileDescriptor).use { pdf.writeTo(it) }
    }
    pdf.close()
    if (result.isFailure) callback.onWriteFailed(result.exceptionOrNull()?.message) else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
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
