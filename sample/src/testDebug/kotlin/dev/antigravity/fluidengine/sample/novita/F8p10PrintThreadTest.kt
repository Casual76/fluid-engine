@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.CancellationSignal
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.F8p10LayoutCallback
import android.print.F8p10WriteCallback
import android.print.PageRange
import android.print.PrintAttributes
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.ui.fluid.output.FluidDocumentPrintAdapter
import dev.antigravity.fluidengine.ui.fluid.output.FluidImagePrintAdapter
import dev.antigravity.fluidengine.ui.fluid.output.FluidPdfStyle
import dev.antigravity.fluidengine.ui.fluid.output.fluidPdfDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * F8.10 · R2 · La stampa non lavora sul thread principale. Android chiama l'adattatore sul principale, ma lascia finire
 * il lavoro su un altro thread e rispondere da li': l'impaginazione di un documento lungo, la lettura di un'immagine e la
 * copia di un PDF non devono fermare l'interfaccia (ne' far scadere lo spooler).
 *
 * Robolectric non ha lo spooler ne' `PdfDocument`: le prove chiamano l'adattatore come farebbe lo spooler e guardano su
 * che thread arriva la risposta. La stampa vera si guarda sul dispositivo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10PrintThreadTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val a4 = PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build()
  private val main: Thread get() = Looper.getMainLooper().thread

  private val long = "Lo studente partecipa con interesse e continuità, interviene in modo pertinente e rispetta le " +
    "consegne, e nel secondo periodo cura l'esposizione orale."

  @Test fun theLayoutOfALongDocumentAnswersFromAnotherThread() {
    val document = fluidPdfDocument("Prova di stampa", locale = Locale.ITALY) { repeat(60) { paragraph("$long $long") } }
    val adapter = FluidDocumentPrintAdapter(compose.activity, document, "Prova di stampa", FluidPdfStyle.Default, null)
    val layout = F8p10LayoutCallback()
    compose.runOnUiThread {
      adapter.onStart()
      adapter.onLayout(null, a4, CancellationSignal(), layout, null)
    }
    assertTrue("la risposta arriva", layout.done.await(20, TimeUnit.SECONDS))
    assertNotNull("impaginato: ${layout.failure}", layout.info)
    assertTrue("piu' pagine: ${layout.info?.pageCount}", (layout.info?.pageCount ?: 0) > 1)
    assertTrue("l'impaginazione non ferma il thread principale: ${layout.thread}", layout.thread !== main)

    // La scrittura: Robolectric non ha PdfDocument, quindi fallisce, ma fuori dal thread principale e una volta sola.
    val file = File(compose.activity.cacheDir, "stampa-prova.pdf")
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
    val write = F8p10WriteCallback()
    compose.runOnUiThread { adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, CancellationSignal(), write) }
    assertTrue(write.done.await(20, TimeUnit.SECONDS))
    assertTrue("la scrittura non ferma il thread principale: ${write.thread}", write.thread !== main)
    Thread.sleep(100)
    assertEquals("una risposta sola", 1, write.answers)
    compose.runOnUiThread { adapter.onFinish() }
    pfd.close()
  }

  @Test fun theImageIsReadOffTheMainThread() {
    var readOn: Thread? = null
    val adapter = FluidImagePrintAdapter("Foto") {
      readOn = Thread.currentThread()
      Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
    }
    val layout = F8p10LayoutCallback()
    compose.runOnUiThread {
      adapter.onStart()
      adapter.onLayout(null, a4, CancellationSignal(), layout, null)
    }
    assertTrue(layout.done.await(20, TimeUnit.SECONDS))
    val file = File(compose.activity.cacheDir, "stampa-foto.pdf")
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
    val write = F8p10WriteCallback()
    compose.runOnUiThread { adapter.onWrite(arrayOf(PageRange.ALL_PAGES), pfd, CancellationSignal(), write) }
    assertTrue(write.done.await(20, TimeUnit.SECONDS))
    assertTrue("l'immagine si legge fuori dal thread principale: $readOn", readOn != null && readOn !== main)
    assertTrue(write.thread !== main)
    compose.runOnUiThread { adapter.onFinish() }
    pfd.close()
  }

  @Test fun aCancelledLayoutAnswersOnceAsCancelled() {
    val document = fluidPdfDocument("Prova", locale = Locale.ITALY) { repeat(40) { paragraph(long) } }
    val adapter = FluidDocumentPrintAdapter(compose.activity, document, "Prova", FluidPdfStyle.Default, null)
    val layout = F8p10LayoutCallback()
    val signal = CancellationSignal()
    compose.runOnUiThread {
      adapter.onStart()
      signal.cancel()
      adapter.onLayout(null, a4, signal, layout, null)
    }
    assertTrue(layout.done.await(20, TimeUnit.SECONDS))
    Thread.sleep(100)
    assertTrue(layout.cancelled)
    assertEquals(1, layout.answers)
  }
}
