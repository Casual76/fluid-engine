package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidStyledTextSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.FileNotFoundException

/** Un file da mostrare: dove sta e cosa ne sa l'app. [sizeBytes] `null` è ignoto, zero è vuoto. */
@Immutable
data class FluidViewerFile(
  val uri: Uri,
  val name: String,
  val mimeType: String? = null,
  val sizeBytes: Long? = null,
) {
  /** Le informazioni per lo sconosciuto e per il pannello: nome, MIME, dimensione e il tipo di `FluidFileKind`. */
  val info: FluidFileInfo get() = FluidFileInfo(name, mimeType, sizeBytes)

  /** Il tipo per il visore, da MIME e nome (senza leggere il file). */
  val type: FluidViewerType get() = FluidViewerType.of(mimeType, name)
}

/** I tetti con cui il modulo apre un file. I valori di serie sono quelli dei singoli decodificatori. */
@Immutable
data class FluidViewerOptions(
  val textLimitBytes: Int = FluidTextDocument.DefaultLimitBytes,
  val textMaxLineChars: Int = FluidTextDocument.DefaultMaxLineChars,
  val imageMaxPixels: Long = FluidDecodedImage.DefaultMaxPixels,
  /** Falso: GIF e WebP animate si mostrano ferme sul primo fotogramma. */
  val animateImages: Boolean = true,
  val pdfMaxCopyBytes: Long = FluidPdfPageSource.DefaultMaxCopyBytes,
)

/**
 * Un file aperto dal modulo: il contenuto per il visore, il tipo, e se qualcosa è andato storto il motivo. Va chiuso
 * quando la pagina esce ([close]): libera il PDF e le sue copie. Il ripiego è sempre lo sconosciuto con «Apri con…»
 * e «Condividi» dell'app, mai un vicolo cieco.
 */
class FluidViewerDocument internal constructor(
  val file: FluidViewerFile,
  /** Il tipo che ha deciso la lettura: può differire da [FluidViewerFile.type] se i byte dicevano altro. */
  val type: FluidViewerType,
  val content: FluidViewerContent,
  /** Il motivo per cui il file è finito allo sconosciuto, `null` se si è aperto o se è sconosciuto davvero. */
  val failure: FluidViewerException?,
  private val resource: Closeable?,
) : Closeable {
  override fun close() {
    resource?.close()
  }
}

/** Legge un file di testo da un `Uri`, con [FluidTextDecoder.read]; `null` se i byte non sono testo. */
suspend fun fluidReadTextFile(
  context: Context,
  uri: Uri,
  fileName: String,
  mimeType: String? = null,
  sizeBytes: Long? = null,
  limitBytes: Int = FluidTextDocument.DefaultLimitBytes,
  maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars,
): FluidTextDocument? = withContext(Dispatchers.IO) {
  val input = try {
    context.applicationContext.contentResolver.openInputStream(uri)
  } catch (e: FileNotFoundException) {
    throw FluidViewerException(FluidViewerFailure.Unreadable, "Testo non leggibile: $fileName", e)
  } catch (e: SecurityException) {
    throw FluidViewerException(FluidViewerFailure.Unreadable, "Permesso negato: $fileName", e)
  } ?: throw FluidViewerException(FluidViewerFailure.Unreadable, "Testo non leggibile: $fileName")
  input.use { FluidTextDecoder.read(it, fileName, mimeType, sizeBytes, limitBytes, maxLineChars) }
}

/**
 * Apre [FluidViewerFile] col decodificatore del suo tipo: l'immagine (anche animata), il PDF, il testo. Un video o
 * un audio ha bisogno del player dell'app: [media] lo trasforma in contenuto (per esempio con [fluidMedia3Content]),
 * e senza [media] il file va allo sconosciuto.
 *
 * I ripieghi, nell'ordine: un eseguibile va subito allo sconosciuto (con l'avviso, mai letto); un file senza tipo
 * dal nome o dal MIME si riconosce dai primi byte; un file che il suo decodificatore rifiuta prova il tipo dei suoi
 * primi byte e poi il testo; se niente lo apre, lo sconosciuto con il motivo in [FluidViewerDocument.failure].
 */
suspend fun FluidViewerFile.open(
  context: Context,
  options: FluidViewerOptions = FluidViewerOptions(),
  openWith: (() -> Unit)? = null,
  share: (() -> Unit)? = null,
  media: ((FluidViewerType) -> FluidViewerContent?)? = null,
): FluidViewerDocument {
  val app = context.applicationContext
  val unknown = FluidViewerContent.Unknown(info, openWith, share)
  var type = this.type
  if (type.executable) return FluidViewerDocument(this, type, unknown, null, null)
  if (type.preset == FluidViewerPreset.Unknown && type.mimeType == null) {
    val header = readHeader(app)
    if (header != null) type = FluidViewerType.of(mimeType, name, header)
  }
  var failure: FluidViewerException? = null
  val tried = mutableSetOf<FluidViewerPreset>()
  suspend fun attempt(preset: FluidViewerPreset): FluidViewerDocument? {
    if (!tried.add(preset)) return null
    return try {
      when (preset) {
        FluidViewerPreset.Image -> {
          val image = FluidDecodedImage.decode(app, uri, options.imageMaxPixels, options.animateImages)
          FluidViewerDocument(this, type.copy(preset = preset), image.content(name), null, null)
        }
        FluidViewerPreset.Pages -> {
          val pdf = FluidPdfPageSource.open(app, uri, options.pdfMaxCopyBytes)
          FluidViewerDocument(this, type.copy(preset = preset), FluidViewerContent.Pages(pdf), null, pdf)
        }
        FluidViewerPreset.Text -> {
          val text = fluidReadTextFile(app, uri, name, mimeType, sizeBytes, options.textLimitBytes, options.textMaxLineChars)
          text?.let {
            FluidViewerDocument(this, type.copy(preset = preset, language = it.language), FluidViewerContent.Text(it), null, null)
          }
        }
        FluidViewerPreset.Video, FluidViewerPreset.Audio ->
          media?.invoke(type)?.let { FluidViewerDocument(this, type, it, null, null) }
        else -> null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: FluidViewerException) {
      if (failure == null) failure = e
      null
    } catch (e: RuntimeException) {
      // Un decodificatore della piattaforma che cede in un modo non documentato (un PDF strano, un'immagine
      // patologica): è un file che non si apre, non un'app che cade.
      if (failure == null) failure = FluidViewerException(FluidViewerFailure.Corrupt, "Il file non si apre: $name", e)
      null
    }
  }
  attempt(type.preset)?.let { return it }
  val decodable = type.preset == FluidViewerPreset.Image || type.preset == FluidViewerPreset.Pages ||
    type.preset == FluidViewerPreset.Text
  if (decodable) {
    readHeader(app)?.let { FluidViewerType.sniff(it) }?.let { sniffed ->
      if (sniffed == FluidViewerPreset.Image || sniffed == FluidViewerPreset.Pages) attempt(sniffed)?.let { return it }
    }
    attempt(FluidViewerPreset.Text)?.let { return it }
  }
  return FluidViewerDocument(this, type, unknown, failure, null)
}

private suspend fun FluidViewerFile.readHeader(context: Context): ByteArray? = withContext(Dispatchers.IO) {
  try {
    context.contentResolver.openInputStream(uri)?.use { input ->
      val bytes = ByteArray(64)
      var filled = 0
      while (filled < bytes.size) {
        val read = input.read(bytes, filled, bytes.size - filled)
        if (read < 0) break
        filled += read
      }
      bytes.copyOf(filled)
    }
  } catch (_: Exception) {
    null
  }
}

/**
 * I file di un visore, aperti col modulo attorno alla pagina corrente: [content] dà il contenuto della pagina, e la
 * prima volta comincia ad aprirla. Tiene aperti i file entro [window] pagine dall'ultima chiesta e chiude gli altri
 * (un PDF lontano non tiene il suo renderer). Si crea con [rememberFluidViewerFiles]:
 *
 * ```kotlin
 * val files = rememberFluidViewerFiles(list, openWith = { open(it) }, share = { share(it) })
 * FluidFileViewer(count = files.count, content = files::content, info = { i -> Info(files.document(i)) })
 * ```
 *
 * Mentre si apre, una pagina mostra già il suo preset: il testo e le pagine aspettano i dati dentro il loro
 * sorgente (lo stesso oggetto resta quando arrivano), l'immagine è vuota sul nero finché non è decodificata.
 */
@Stable
class FluidViewerFiles internal constructor(
  private val context: Context,
  private val scope: CoroutineScope,
) {
  internal var files: List<FluidViewerFile> by mutableStateOf(emptyList())
  internal var options: FluidViewerOptions = FluidViewerOptions()
  internal var window: Int = 1
  internal var openWith: ((FluidViewerFile) -> Unit)? = null
  internal var share: ((FluidViewerFile) -> Unit)? = null
  internal var media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null

  private class Entry(val file: FluidViewerFile, val pending: Pending) {
    var document: FluidViewerDocument? = null
    var job: Job? = null
  }

  // Una mappa semplice, non uno stato di Compose: [content] la tocca durante la composizione, e una scrittura lì
  // ricomporrebbe chi legge. Chi legge si iscrive a [version], che cresce quando un file finisce di aprirsi.
  private val entries = HashMap<Int, Entry>()
  private var version by mutableIntStateOf(0)

  /** Quanti file. */
  val count: Int get() = files.size

  /** Il file aperto alla pagina [index], `null` finché si apre. */
  fun document(index: Int): FluidViewerDocument? {
    version
    return entries[index]?.takeIf { it.file == files.getOrNull(index) }?.document
  }

  /** Il contenuto della pagina [index]. La prima volta comincia ad aprire il file e chiude quelli lontani. */
  fun content(index: Int): FluidViewerContent {
    version
    val file = files[index]
    val entry = entries[index]
    if (entry != null && entry.file == file) return entry.document?.content ?: entry.pending.content
    entry?.let { release(index, it) }
    val created = Entry(file, pendingContent(file))
    entries[index] = created
    val openWith = openWith
    val share = share
    val media = media
    created.job = scope.launch {
      val document = file.open(
        context,
        options,
        openWith = openWith?.let { callback -> { callback(file) } },
        share = share?.let { callback -> { callback(file) } },
        media = media?.let { callback -> { type: FluidViewerType -> callback(file, type) } },
      )
      if (entries[index] !== created) {
        document.close()
        return@launch
      }
      created.pending.complete(document)
      // Il testo e le pagine restano lo stesso oggetto: chi li legge vede arrivare i dati. Il resto si sostituisce.
      created.document = if (created.pending.keeps()) {
        FluidViewerDocument(file, document.type, created.pending.content, null, document)
      } else {
        document
      }
      version++
    }
    evict(index)
    return created.pending.content
  }

  private fun evict(around: Int) {
    for (key in entries.keys.toList()) {
      if (key < around - window || key > around + window || key >= files.size) entries[key]?.let { release(key, it) }
    }
  }

  private fun release(index: Int, entry: Entry) {
    entry.job?.cancel()
    entry.document?.close()
    if (entries[index] === entry) entries.remove(index)
  }

  internal fun closeAll() {
    for ((key, entry) in entries.toList()) release(key, entry)
  }

  private fun pendingContent(file: FluidViewerFile): Pending {
    val type = file.type
    return when (type.preset) {
      FluidViewerPreset.Text -> PendingText(type.language)
      FluidViewerPreset.Pages -> PendingPages()
      else -> PendingImage
    }
  }

  private interface Pending {
    val content: FluidViewerContent
    fun complete(document: FluidViewerDocument) {}
    fun keeps(): Boolean = false
  }

  private object PendingImage : Pending {
    override val content: FluidViewerContent = FluidViewerContent.Image(null) {}
  }

  /** Il testo che arriva: [FluidTextSource.readLines] aspetta il documento, [lineCount] è noto quando arriva. */
  private class PendingText(private val nameLanguage: String?) : Pending, FluidStyledTextSource {
    private var document by mutableStateOf<FluidTextDocument?>(null)
    override val content: FluidViewerContent = FluidViewerContent.Text(this)
    override val language: String? get() = document?.language ?: nameLanguage
    override val lineCount: Int? get() = document?.lineCount

    private suspend fun ready(): FluidTextDocument = document ?: snapshotFlow { document }.filterNotNull().first()

    override suspend fun readLines(start: Int, count: Int): List<String> = ready().readLines(start, count)

    /** Come [FluidTextDocument.readStyledLines], quando il documento arriva. */
    override suspend fun readStyledLines(start: Int, count: Int): List<AnnotatedString> = ready().readStyledLines(start, count)

    override fun complete(document: FluidViewerDocument) {
      this.document = ((document.content as? FluidViewerContent.Text)?.source as? FluidTextDocument)
    }

    override fun keeps() = document != null
  }

  /** Le pagine che arrivano: [count] è zero finché il PDF non è aperto, la resa aspetta. */
  private class PendingPages : Pending, FluidPageSource {
    private var source by mutableStateOf<FluidPdfPageSource?>(null)
    override val content: FluidViewerContent = FluidViewerContent.Pages(this)
    override val count: Int get() = source?.count ?: 0

    private suspend fun ready(): FluidPdfPageSource = source ?: snapshotFlow { source }.filterNotNull().first()

    override suspend fun size(index: Int): IntSize = ready().size(index)
    override suspend fun render(index: Int, target: IntSize) = ready().render(index, target)

    override fun complete(document: FluidViewerDocument) {
      source = (document.content as? FluidViewerContent.Pages)?.source as? FluidPdfPageSource
    }

    override fun keeps() = source != null
  }
}

/**
 * I file di [files] per il visore, aperti col modulo attorno alla pagina corrente e chiusi quando la composizione
 * esce. [media] dà il contenuto di un video o di un audio col player dell'app (per esempio
 * `{ _, type -> fluidMedia3Content(session, video = type.preset == FluidViewerPreset.Video) }`); senza, i video vanno
 * allo sconosciuto. [openWith] e [share] diventano «Apri con…» e «Condividi» dello sconosciuto.
 */
@Composable
fun rememberFluidViewerFiles(
  files: List<FluidViewerFile>,
  options: FluidViewerOptions = FluidViewerOptions(),
  window: Int = 1,
  openWith: ((FluidViewerFile) -> Unit)? = null,
  share: ((FluidViewerFile) -> Unit)? = null,
  media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null,
): FluidViewerFiles {
  val context = LocalContext.current.applicationContext
  val scope = rememberCoroutineScope()
  val state = remember(context, scope) { FluidViewerFiles(context, scope) }
  val currentOpenWith by rememberUpdatedState(openWith)
  val currentShare by rememberUpdatedState(share)
  val currentMedia by rememberUpdatedState(media)
  state.files = files
  state.options = options
  state.window = window.coerceAtLeast(0)
  state.openWith = openWith?.let { { file: FluidViewerFile -> currentOpenWith?.invoke(file) } }
  state.share = share?.let { { file: FluidViewerFile -> currentShare?.invoke(file) } }
  state.media = media?.let { { file: FluidViewerFile, type: FluidViewerType -> currentMedia?.invoke(file, type) } }
  DisposableEffect(state) { onDispose { state.closeAll() } }
  return state
}
