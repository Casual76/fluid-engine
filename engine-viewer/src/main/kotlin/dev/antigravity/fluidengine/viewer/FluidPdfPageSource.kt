package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import androidx.annotation.RestrictTo
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Un PDF per il preset delle pagine, con il `PdfRenderer` della piattaforma (`androidx.pdf` è in alfa e non si usa).
 *
 * - **Una pagina alla volta, su un thread solo** ([FluidPageRenderQueue]): `PdfRenderer` non è thread-safe e tiene
 *   aperta una pagina per volta. Le chiamate del preset arrivano dal thread principale e aspettano il loro turno.
 * - **Una cache di poche pagine** (4 intere, al più 64 MB) e **anteprime a bassa risoluzione** ([preview], lato lungo
 *   256 px, 32 in cache) per lo scorrimento e il foglio delle miniature.
 * - [size] è in punti PDF (1/72 di pollice): per il rapporto d'aspetto e per scegliere quanti pixel chiedere. [render]
 *   dà una bitmap su fondo bianco (la carta del PDF), ridotta se supera 12 MP.
 * - **Va chiuso** ([close]): il documento, il descrittore e l'eventuale copia temporanea. Una resa in corso finisce
 *   prima della chiusura; le chiamate dopo la chiusura sono annullate (`CancellationException`).
 *
 * Si apre con [open]. Un file rotto, protetto da password o illeggibile dà una [FluidViewerException] col motivo.
 */
class FluidPdfPageSource private constructor(
  private val queue: FluidPageRenderQueue<ImageBitmap>,
) : FluidPageSource, Closeable {

  override val count: Int get() = queue.pageCount

  override suspend fun size(index: Int): IntSize = queue.size(index)

  override suspend fun render(index: Int, target: IntSize): ImageBitmap = queue.render(index, target)

  /** L'anteprima della pagina [index], a lato lungo 256 px: quella da mostrare mentre si scorre. */
  suspend fun preview(index: Int): ImageBitmap = queue.preview(index)

  /** Preriscalda una pagina vicina a bassa risoluzione finché la finestra ha margine. */
  suspend fun prefetch(index: Int) {
    try { queue.prefetch(index) }
    catch (_: CancellationException) { kotlinx.coroutines.currentCoroutineContext().ensureActive() }
  }

  override fun close() = queue.close()

  companion object {
    /**
     * Il tetto della copia di un PDF che arriva da un `Uri` non ricercabile (un flusso, una pipe): `PdfRenderer`
     * vuole un descrittore su cui spostarsi, quindi il file si copia nella cache dell'app. 512 MB, a occhio,
     * 22 novembre 2026: un PDF più grande da un flusso è un caso da «Apri con…».
     */
    const val DefaultMaxCopyBytes: Long = 512L * 1024 * 1024

    /**
     * F8.18 · Le pagine per lo schermo nella memoria grafica (`Bitmap.Config.HARDWARE`), portate lì sul thread del PDF:
     * il primo disegno di una pagina non carica la texture dentro il fotogramma del RenderThread. Di serie (null)
     * **secondo la fascia del dispositivo** (F8.18b): accese sui più deboli, spente su quelli alti, con la regola di
     * `viewerHardwarePages` di engine-ui (la riga di memoria non Piena, o il vetro sceso a Protetto). Vero o falso le
     * forza, solo per le prove di confronto sul dispositivo (la galleria in debug e la bench).
     *
     * F7.22 · Le misure del Tab S9 (PDF di 3000 pagine, formato telefono): spente, ogni pagina nitida (975 × 1380) si
     * carica *dentro* un fotogramma del RenderThread in 2,6–7,0 ms (`Texture upload`); accese, il lavoro va sul thread
     * del PDF (`allocateHardwareBitmap` 2,1 ms di media) e il RenderThread non lo vede. Sul Tab S9 i tre buffer
     * nascondono il picco. Se accenderle anche sui dispositivi alti lo decide Alessio (domanda del 26/11).
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @Volatile
    @JvmStatic
    var hardwareDisplay: Boolean? = null

    /**
     * Apre un PDF da un descrittore, che passa al sorgente (lo chiude lui, anche se l'apertura fallisce). Il
     * descrittore deve essere ricercabile: per un `Uri` qualunque usare l'altro [open], che copia quando serve.
     */
    suspend fun open(descriptor: ParcelFileDescriptor): FluidPdfPageSource = open(descriptor, temporary = null)

    /** Apre un PDF da un file dell'app. */
    suspend fun open(file: File): FluidPdfPageSource {
      val descriptor = acquireDescriptor {
        try {
          ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: FileNotFoundException) {
          throw FluidViewerException(FluidViewerFailure.Unreadable, "PDF non leggibile: ${file.name}", e)
        }
      }
      return open(checkNotNull(descriptor), temporary = null)
    }

    /**
     * Apre un PDF da un `Uri` (`content://` o `file://`). Se il descrittore non è ricercabile copia il file nella
     * cache dell'app, al più [maxCopyBytes], e cancella la copia alla chiusura. La copia si interrompe se chi chiama
     * è annullato, e non lascia file a metà.
     */
    suspend fun open(context: Context, uri: Uri, maxCopyBytes: Long = DefaultMaxCopyBytes): FluidPdfPageSource {
      ViewerMemory.get(context)
      val resolver = context.applicationContext.contentResolver
      val descriptor = acquireDescriptor {
        try {
          resolver.openFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
          throw FluidViewerException(FluidViewerFailure.Unreadable, "PDF non leggibile: $uri", e)
        } catch (e: SecurityException) {
          throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato: $uri", e)
        }
      } ?: throw FluidViewerException(FluidViewerFailure.Unreadable, "PDF non leggibile: $uri")
      if (descriptor.seekable()) return open(descriptor, temporary = null)
      descriptor.closeQuietly()
      val copy = copyToCache(context, uri, maxCopyBytes)
      val copied = try {
        ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
      } catch (e: FileNotFoundException) {
        copy.delete()
        throw FluidViewerException(FluidViewerFailure.Unreadable, "Copia del PDF non leggibile", e)
      }
      return open(copied, temporary = copy)
    }

    private suspend fun open(descriptor: ParcelFileDescriptor, temporary: File?): FluidPdfPageSource {
      val memory = try { ViewerMemory.get() } catch (error: Throwable) {
        descriptor.closeQuietly()
        temporary?.delete()
        throw error
      }
      val queue = FluidPageRenderQueue<ImageBitmap>(
        memory = memory,
        physical = { it.physical },
        discard = { it.asAndroidBitmap().recycle() },
        open = {
          val duplicate = ParcelFileDescriptor.dup(descriptor.fileDescriptor)
          try { PdfRendererBackend(duplicate) } catch (error: Throwable) { duplicate.closeQuietly(); throw error }
        },
        bytesOf = { it.pixelBytes },
        release = {
          descriptor.closeQuietly()
          temporary?.delete()
        },
        display = { page -> pdfDisplayBitmap(page, pdfHardwarePages(memory.resources)) },
      )
      try {
        queue.start()
      } catch (t: Throwable) {
        queue.close()
        throw when (t) {
          is CancellationException, is FluidViewerException -> t
          is SecurityException -> FluidViewerException(FluidViewerFailure.Protected, "PDF protetto da password", t)
          is IOException -> FluidViewerException(FluidViewerFailure.Corrupt, "PDF rotto o non valido", t)
          is IllegalArgumentException -> FluidViewerException(FluidViewerFailure.Unreadable, "Descrittore non valido", t)
          else -> t
        }
      }
      return FluidPdfPageSource(queue)
    }

    private suspend fun acquireDescriptor(open: () -> ParcelFileDescriptor?): ParcelFileDescriptor? {
      var opened: ParcelFileDescriptor? = null
      try {
        withContext(Dispatchers.IO) { opened = open() }
        return opened
      } catch (error: Throwable) {
        opened?.closeQuietly()
        throw error
      }
    }

    private fun ParcelFileDescriptor.seekable(): Boolean = try {
      Os.lseek(fileDescriptor, 0, OsConstants.SEEK_CUR)
      true
    } catch (_: ErrnoException) {
      false
    }

    private fun ParcelFileDescriptor.closeQuietly() {
      try {
        close()
      } catch (_: IOException) {
      }
    }

    private suspend fun copyToCache(context: Context, uri: Uri, maxBytes: Long): File {
      var copy: File? = null
      try {
        withContext(Dispatchers.IO) {
          val memory = ViewerMemory.get(context)
          val pending = memory.reserve(FluidMemoryCategory.VIEWER, 65536, false) ?: noViewerMemory()
          pending.use {
            val dir = File(context.cacheDir, "fluid-viewer").apply { mkdirs() }
            val file = File.createTempFile("pdf-", ".pdf", dir).also { copy = it }
            val input = try { context.contentResolver.openInputStream(uri) }
            catch (error: SecurityException) { throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato: $uri", error) }
            catch (error: FileNotFoundException) { throw FluidViewerException(FluidViewerFailure.Unreadable, "PDF non leggibile: $uri", error) }
            if (input == null) throw FluidViewerException(FluidViewerFailure.Unreadable, "PDF non leggibile: $uri")
            input.use { source ->
              file.outputStream().use { sink ->
                val buffer = ByteArray(65536)
                var total = 0L
                while (true) {
                  coroutineContext.ensureActive()
                  val read = source.read(buffer)
                  if (read < 0) break
                  total += read
                  if (total > maxBytes) throw FluidViewerException(FluidViewerFailure.TooLarge, "PDF oltre $maxBytes byte da un flusso")
                  sink.write(buffer, 0, read)
                }
              }
            }
          }
        }
        return checkNotNull(copy)
      } catch (error: Throwable) {
        copy?.delete()
        throw error
      }
    }

  }
}

/**
 * F8.18b · Se le pagine di un PDF per lo schermo vanno nella memoria grafica: la scelta della prova
 * ([FluidPdfPageSource.hardwareDisplay]) se c'è, se no la fascia del dispositivo ([ViewerPolicy.hardwarePages]). Letta a
 * ogni pagina resa: se la fascia cambia (una termica severa, una pressione), cambiano le pagine dopo.
 */
internal fun pdfHardwarePages(policy: ViewerPolicy): Boolean = FluidPdfPageSource.hardwareDisplay ?: policy.hardwarePages

/**
 * F8.18 · La pagina resa per lo schermo, portata nella memoria grafica sul thread del PDF se [hardware] (la fascia del
 * dispositivo, o la scelta della prova): la copia la fa l'uploader della piattaforma, fuori dal RenderThread. Se la
 * copia non riesce resta la bitmap normale. La bitmap software si ricicla subito: la prenotazione della resa contava già
 * le due copie (8 byte per pixel).
 */
internal fun pdfDisplayBitmap(page: ImageBitmap, hardware: Boolean): ImageBitmap {
  if (!hardware) return page
  val software = page.asAndroidBitmap()
  val hardware = try {
    software.copy(Bitmap.Config.HARDWARE, false)
  } catch (_: RuntimeException) {
    null
  } ?: return page
  software.recycle()
  return hardware.asImageBitmap()
}

/** `PdfRenderer` dietro [FluidPageBackend]. Lo usa un thread solo; ogni pagina si apre e si chiude con `use`. */
internal class PdfRendererBackend(descriptor: ParcelFileDescriptor) : FluidPageBackend<ImageBitmap> {
  private val renderer = PdfRenderer(descriptor)

  override val pageCount: Int = renderer.pageCount

  override fun pageSize(index: Int): IntSize = renderer.openPage(index).use { IntSize(it.width, it.height) }

  override fun render(index: Int, target: IntSize): ImageBitmap = renderer.openPage(index).use { page ->
    val bitmap = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
    try {
      // La carta del PDF: una pagina è trasparente dove non ha inchiostro, e nel tema scuro sparirebbe.
      bitmap.eraseColor(android.graphics.Color.WHITE)
      val matrix = Matrix().apply {
        setScale(target.width / page.width.coerceAtLeast(1).toFloat(), target.height / page.height.coerceAtLeast(1).toFloat())
      }
      page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
      bitmap.asImageBitmap()
    } catch (error: Throwable) {
      bitmap.recycle()
      throw error
    }
  }

  override fun close() = renderer.close()
}
