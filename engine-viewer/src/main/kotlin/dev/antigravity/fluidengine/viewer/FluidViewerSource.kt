package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/**
 * Il file da aprire con il registro (F6C.8): un `Uri` dell'app, un file suo, una voce di un archivio, un allegato.
 * Si legge sempre dall'inizio con [open]; chi ha bisogno di spostarsi (uno ZIP, il `PdfRenderer`) chiede [seekable].
 *
 * - **[open]** dà un flusso nuovo a ogni chiamata, che chi chiama chiude.
 * - **[seekable]** dà un descriptor nuovo a ogni chiamata, che chi chiama chiude. Una sorgente che non si può
 *   spostare (una pipe, un flusso) **si copia una volta nella cache dell'app**; la copia serve alle chiamate dopo e
 *   si cancella con [close].
 * - **[close]** libera tutto, anche dopo un annullamento: la copia a metà non resta. Il documento aperto dal registro
 *   possiede la sua sorgente e la chiude con sé.
 *
 * Le sorgenti di serie si fanno con [of]. Un'app o un modulo di famiglia può scrivere la sua (una voce di un archivio
 * letta al volo); il registro usa solo questi quattro membri.
 */
interface FluidViewerSource : Closeable {
  /** Il nome con l'estensione vera, come lo vede chi usa l'app. */
  val name: String
  val mimeType: String? get() = null
  /** `null` è ignoto, zero è vuoto. */
  val sizeBytes: Long? get() = null

  suspend fun open(): InputStream

  suspend fun seekable(): ParcelFileDescriptor

  override fun close() {}

  companion object {
    /**
     * Il tetto della copia di una sorgente che non si può spostare: 512 MB come il PDF di F6C.4, a occhio, 22
     * novembre 2026. Più grande, `FluidViewerFailure.TooLarge`: è un caso da «Apri con…».
     */
    const val DefaultMaxCopyBytes: Long = 512L * 1024 * 1024

    /**
     * Un `Uri` (`content://`, `file://`, `android.resource://`). [seekable] apre il descrittore del provider e, se
     * è una pipe o un flusso, copia il file nella cache dell'app (`cacheDir/fluid-viewer`), al più [maxCopyBytes].
     */
    fun of(
      context: Context,
      uri: Uri,
      name: String,
      mimeType: String? = null,
      sizeBytes: Long? = null,
      maxCopyBytes: Long = DefaultMaxCopyBytes,
    ): FluidViewerSource = FluidViewerUriSource(context.applicationContext, uri, name, mimeType, sizeBytes, maxCopyBytes)

    /** Un file dell'app. Con [deleteOnClose] il file si cancella alla chiusura (una voce estratta in cache). */
    fun of(
      file: File,
      name: String = file.name,
      mimeType: String? = null,
      deleteOnClose: Boolean = false,
    ): FluidViewerSource = FluidViewerFileSource(file, name, mimeType, deleteOnClose)

    /**
     * Un flusso che si può solo leggere dall'inizio (una voce di un archivio, un allegato decodificato): [open] lo
     * chiede di nuovo a [read] ogni volta, [seekable] lo copia una volta in [cacheDir], al più [maxCopyBytes].
     */
    fun of(
      name: String,
      cacheDir: File,
      mimeType: String? = null,
      sizeBytes: Long? = null,
      maxCopyBytes: Long = DefaultMaxCopyBytes,
      read: suspend () -> InputStream,
    ): FluidViewerSource = FluidViewerStreamSource(name, mimeType, sizeBytes, FluidSeekableCopy(cacheDir, maxCopyBytes, name, read), read)
  }
}

/** Le sorgenti di serie danno al registro un canale per leggere l'indice di uno ZIP senza passare dal descriptor. */
internal interface FluidViewerChannelSource {
  suspend fun channel(): FileChannel
}

/** Il canale di una sorgente qualunque: dal suo file se ne ha uno, altrimenti da [FluidViewerSource.seekable]. */
internal suspend fun FluidViewerSource.channel(): FileChannel =
  if (this is FluidViewerChannelSource) channel()
  else withContext(Dispatchers.IO) { ParcelFileDescriptor.AutoCloseInputStream(seekable()).channel }

/**
 * La copia ricercabile di un flusso, fatta una volta sola. Chi la chiede insieme aspetta la stessa copia; un
 * annullamento o un errore a metà chiude il flusso e cancella il file; [close] cancella la copia e ferma quella in
 * corso, che non lascia niente. Dopo [close] ogni richiesta è un errore `Unreadable`.
 */
internal class FluidSeekableCopy(
  private val directory: File,
  private val maxBytes: Long,
  private val label: String,
  private val read: suspend () -> InputStream,
) : Closeable {
  private val lock = Mutex()
  @Volatile private var copy: File? = null
  @Volatile private var closed = false

  /** Il file copiato, già pronto o fatto adesso. */
  suspend fun file(): File = lock.withLock {
    if (closed) throw FluidViewerException(FluidViewerFailure.Unreadable, "Sorgente chiusa: $label")
    copy?.let { return@withLock it }
    var created: File? = null
    val made = try {
      withContext(Dispatchers.IO) { copyNow().also { created = it } }
    } catch (error: Throwable) {
      created?.delete()
      throw error
    }
    if (closed) {
      made.delete()
      throw FluidViewerException(FluidViewerFailure.Unreadable, "Sorgente chiusa: $label")
    }
    copy = made
    // [close] può arrivare fra il controllo qui sopra e l'assegnazione: uno dei due vede l'altro e cancella.
    if (closed) {
      made.delete()
      copy = null
      throw FluidViewerException(FluidViewerFailure.Unreadable, "Sorgente chiusa: $label")
    }
    made
  }

  private suspend fun copyNow(): File {
    directory.mkdirs()
    val file = File.createTempFile("src-", ".bin", directory)
    try {
      read().use { source ->
        file.outputStream().use { sink ->
          val buffer = ByteArray(64 * 1024)
          var total = 0L
          while (true) {
            currentCoroutineContext().ensureActive()
            if (closed) throw FluidViewerException(FluidViewerFailure.Unreadable, "Sorgente chiusa: $label")
            val n = source.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw FluidViewerException(FluidViewerFailure.TooLarge, "Oltre $maxBytes byte da un flusso: $label")
            sink.write(buffer, 0, n)
          }
        }
      }
      return file
    } catch (t: Throwable) {
      file.delete()
      throw t
    }
  }

  /** Il file della copia, se c'è: per i test e per il canale. */
  val current: File? get() = copy

  override fun close() {
    closed = true
    copy?.delete()
    copy = null
  }
}

private fun ParcelFileDescriptor.seekable(): Boolean = try {
  Os.lseek(fileDescriptor, 0, OsConstants.SEEK_CUR)
  true
} catch (_: ErrnoException) {
  false
}

private fun Closeable.closeQuietly() {
  try {
    close()
  } catch (_: IOException) {
  }
}

/** Un `Uri`: il descrittore del provider se si sposta, la copia in cache se è una pipe. */
internal class FluidViewerUriSource(
  internal val context: Context,
  internal val uri: Uri,
  override val name: String,
  override val mimeType: String?,
  override val sizeBytes: Long?,
  maxCopyBytes: Long,
) : FluidViewerSource, FluidViewerChannelSource {
  private val copy = FluidSeekableCopy(File(context.cacheDir, "fluid-viewer"), maxCopyBytes, name) { openStream() }

  private suspend fun openStream(): InputStream = withContext(Dispatchers.IO) {
    try {
      context.contentResolver.openInputStream(uri)
    } catch (e: FileNotFoundException) {
      throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name", e)
    } catch (e: SecurityException) {
      throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato: $name", e)
    } ?: throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name")
  }

  override suspend fun open(): InputStream = copy.current?.let { withContext(Dispatchers.IO) { FileInputStream(it) } } ?: openStream()

  override suspend fun seekable(): ParcelFileDescriptor {
    copy.current?.let { return withContext(Dispatchers.IO) { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) } }
    val direct = withContext(Dispatchers.IO) {
      try {
        context.contentResolver.openFileDescriptor(uri, "r")
      } catch (e: FileNotFoundException) {
        throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name", e)
      } catch (e: SecurityException) {
        throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato: $name", e)
      }
    }
    if (direct != null && direct.seekable()) return direct
    direct?.closeQuietly()
    val file = copy.file()
    return withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
  }

  override suspend fun channel(): FileChannel {
    val pfd = seekable()
    return withContext(Dispatchers.IO) { ParcelFileDescriptor.AutoCloseInputStream(pfd).channel }
  }

  override fun close() = copy.close()
}

/** Un file dell'app. */
internal class FluidViewerFileSource(
  internal val file: File,
  override val name: String,
  override val mimeType: String?,
  private val deleteOnClose: Boolean,
) : FluidViewerSource, FluidViewerChannelSource {
  override val sizeBytes: Long? get() = if (file.isFile) file.length() else null

  override suspend fun open(): InputStream = withContext(Dispatchers.IO) {
    try {
      FileInputStream(file)
    } catch (e: FileNotFoundException) {
      throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name", e)
    }
  }

  override suspend fun seekable(): ParcelFileDescriptor = withContext(Dispatchers.IO) {
    try {
      ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    } catch (e: FileNotFoundException) {
      throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name", e)
    }
  }

  override suspend fun channel(): FileChannel = withContext(Dispatchers.IO) {
    try {
      RandomAccessFile(file, "r").channel
    } catch (e: FileNotFoundException) {
      throw FluidViewerException(FluidViewerFailure.Unreadable, "File non leggibile: $name", e)
    }
  }

  override fun close() {
    if (deleteOnClose) file.delete()
  }
}

/** Un flusso che si legge solo dall'inizio, con la copia per chi deve spostarsi. */
internal class FluidViewerStreamSource(
  override val name: String,
  override val mimeType: String?,
  override val sizeBytes: Long?,
  internal val copy: FluidSeekableCopy,
  private val read: suspend () -> InputStream,
) : FluidViewerSource, FluidViewerChannelSource {
  override suspend fun open(): InputStream = copy.current?.let { withContext(Dispatchers.IO) { FileInputStream(it) } } ?: read()

  override suspend fun seekable(): ParcelFileDescriptor {
    val file = copy.file()
    return withContext(Dispatchers.IO) { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
  }

  override suspend fun channel(): FileChannel {
    val file = copy.file()
    return withContext(Dispatchers.IO) { RandomAccessFile(file, "r").channel }
  }

  override fun close() = copy.close()
}
