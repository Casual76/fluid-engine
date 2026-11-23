package dev.antigravity.fluidengine.ui.fluid.viewer

import android.content.Context
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * F6C.19 · «Stampa» del menu del file per un documento a pagine ([FluidViewerContent.Pages]: un PDF, un documento Word,
 * le slide). Il visore la fa da sé: ogni pagina scelta nel dialogo di sistema si rende con la sua sorgente
 * ([FluidPageSource.render]) a [Dpi] punti per pollice e si posa, intera e centrata, nell'area stampabile del foglio.
 * Una pagina alla volta, così la memoria resta quella di una pagina.
 */
internal object ViewerPrintProfile {
  /** 200 punti per pollice: un A4 è 1654 × 2339 pixel, ≈15 MB per pagina. A occhio, 23 novembre 2026. */
  const val Dpi = 200f

  /** Il lato più lungo che si chiede alla sorgente, per un foglio grande (A3, un poster). */
  const val MaxSidePx = 4096
}

/**
 * Apre il dialogo di stampa di sistema per [source]. [context] deve essere un'attività (lo chiede [PrintManager]):
 * altrimenti l'eccezione arriva al menu, che dice «Impossibile completare l'azione».
 */
internal fun viewerPrintPages(context: Context, name: String, source: FluidPageSource, scope: CoroutineScope) {
  val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
  manager.print(name, ViewerPagesPrintAdapter(context, name, source, scope), null)
}

/** Il nome del documento per la coda di stampa: quello del file, che finisca con `.pdf`. Pura. */
internal fun viewerPrintFileName(title: String): String =
  title.ifBlank { "documento" }.let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }

/** Le pagine (da zero) di [ranges] fra le [count] del documento. Pura. */
internal fun viewerPrintPagesIn(ranges: Array<PageRange>, count: Int): List<Int> =
  (0 until count).filter { page -> ranges.any { it == PageRange.ALL_PAGES || page in it.start..it.end } }

/**
 * Il riquadro (in punti del foglio) dove sta una pagina larga [width] e alta [height] dentro [box]: intera, centrata.
 * Pura.
 */
internal fun viewerPrintFit(box: RectF, width: Float, height: Float): RectF {
  if (width <= 0f || height <= 0f) return RectF(box)
  val scale = min(box.width() / width, box.height() / height)
  val w = width * scale
  val h = height * scale
  val left = box.left + (box.width() - w) / 2f
  val top = box.top + (box.height() - h) / 2f
  return RectF(left, top, left + w, top + h)
}

private class ViewerPagesPrintAdapter(
  private val context: Context,
  private val name: String,
  private val source: FluidPageSource,
  private val scope: CoroutineScope,
) : PrintDocumentAdapter() {
  private var attributes: PrintAttributes? = null

  override fun onLayout(
    oldAttributes: PrintAttributes?,
    newAttributes: PrintAttributes,
    cancellationSignal: CancellationSignal,
    callback: LayoutResultCallback,
    extras: Bundle?,
  ) {
    if (cancellationSignal.isCanceled) {
      callback.onLayoutCancelled()
      return
    }
    attributes = newAttributes
    val count = source.count
    if (count <= 0) {
      callback.onLayoutFailed(null)
      return
    }
    val info = PrintDocumentInfo.Builder(viewerPrintFileName(name))
      .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
      .setPageCount(count)
      .build()
    callback.onLayoutFinished(info, newAttributes != oldAttributes)
  }

  override fun onWrite(
    pages: Array<PageRange>,
    destination: ParcelFileDescriptor,
    cancellationSignal: CancellationSignal,
    callback: WriteResultCallback,
  ) {
    val printAttributes = attributes ?: run {
      callback.onWriteFailed(null)
      return
    }
    val job = scope.launch {
      val document = PrintedPdfDocument(context, printAttributes)
      try {
        val chosen = viewerPrintPagesIn(pages, source.count)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        for (index in chosen) {
          ensureActive()
          val page = document.startPage(index)
          val sheet = source.size(index)
          val target = viewerPrintFit(RectF(page.info.contentRect), sheet.width.toFloat(), sheet.height.toFloat())
          // I punti del foglio sono 1/72 di pollice.
          val scale = min(ViewerPrintProfile.Dpi / 72f,
            ViewerPrintProfile.MaxSidePx / max(target.width(), target.height()).coerceAtLeast(1f))
          val pixels = IntSize((target.width() * scale).roundToInt().coerceAtLeast(1),
            (target.height() * scale).roundToInt().coerceAtLeast(1))
          val bitmap = source.render(index, pixels).asAndroidBitmap()
          page.canvas.drawBitmap(bitmap, null, target, paint)
          document.finishPage(page)
        }
        withContext(Dispatchers.IO) {
          FileOutputStream(destination.fileDescriptor).use { document.writeTo(it) }
        }
        val written = chosen.toRanges()
        callback.onWriteFinished(written)
      } catch (cancelled: CancellationException) {
        callback.onWriteCancelled()
      } catch (failure: Exception) {
        callback.onWriteFailed(failure.message)
      } finally {
        document.close()
      }
    }
    cancellationSignal.setOnCancelListener { job.cancel() }
  }

  private fun List<Int>.toRanges(): Array<PageRange> {
    if (isEmpty()) return arrayOf()
    val ranges = mutableListOf<PageRange>()
    var start = first()
    var end = start
    for (page in drop(1)) {
      if (page == end + 1) end = page else {
        ranges += PageRange(start, end)
        start = page
        end = page
      }
    }
    ranges += PageRange(start, end)
    return ranges.toTypedArray()
  }
}
