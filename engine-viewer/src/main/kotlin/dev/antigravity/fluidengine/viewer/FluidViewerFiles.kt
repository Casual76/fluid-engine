package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.collectAsState
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
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
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** Un file da mostrare: dove sta e cosa ne sa l'app. [sizeBytes] `null` è ignoto, zero è vuoto. */
@Immutable
data class FluidViewerFile(
  val uri: Uri,
  val name: String,
  val mimeType: String? = null,
  val sizeBytes: Long? = null,
  val modifiedAtMillis: Long? = null,
) {
  /** Le informazioni per lo sconosciuto e per il pannello: nome, MIME, dimensione e il tipo di `FluidFileKind`. */
  val info: FluidFileInfo get() = FluidFileInfo(name, mimeType, sizeBytes, modifiedAtMillis = modifiedAtMillis)

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
  /**
   * F6C.8: il tetto della copia in cache di una sorgente che non si può spostare (una pipe, un flusso), quando una
   * famiglia o un preset chiede `FluidViewerSource.seekable`.
   */
  val sourceMaxCopyBytes: Long = FluidViewerSource.DefaultMaxCopyBytes,
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
  /**
   * F6C.8: le righe del pannello info date dalla famiglia che l'ha aperto («Pagine 9»); vuote per i preset di serie.
   * Possono cambiare dopo l'apertura ([FluidViewerLiveDetails]): per una copia ferma, [FluidViewerLiveDetails.current].
   */
  val details: List<FluidViewerDetail> = emptyList(),
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
 *
 * F6C.8: con un modulo di famiglia nell'app ([FluidViewerRegistry.families]) decide il registro, con le sue regole
 * (firma, estensione, MIME, priorità, ripiego al candidato dopo); i preset di serie sono uno dei candidati, con i
 * ripieghi di sopra. Senza famiglie è esattamente l'apertura di F6C.4.
 */
suspend fun FluidViewerFile.open(
  context: Context,
  options: FluidViewerOptions = FluidViewerOptions(),
  openWith: (() -> Unit)? = null,
  share: (() -> Unit)? = null,
  media: ((FluidViewerType) -> FluidViewerContent?)? = null,
): FluidViewerDocument {
  val app = context.applicationContext
  ViewerMemory.get(app)
  val families = FluidViewerRegistry.families
  if (families.isNotEmpty() || type.executable) {
    val source = FluidViewerSource.of(app, uri, name, mimeType, sizeBytes, options.sourceMaxCopyBytes)
    return fluidOpenSource(app, this, source, families, options, openWith, share, media)
  }
  val unknown = FluidViewerContent.Unknown(info, openWith, share)
  val failures = FluidViewerFailures()
  val result = fluidOpenBuiltin(type, mimeType, name, FluidUriAccess(app, this, options), failures, media)
  val attempt = result.attempt ?: return FluidViewerDocument(this, result.type, unknown, failures.first, null)
  return FluidViewerDocument(this, attempt.type, attempt.content, null, attempt.resource)
}

/** Come il preset di serie legge un file: dall'`Uri` dell'app come in F6C.4, o da una sorgente del registro. */
internal interface FluidBuiltinAccess {
  /** I primi byte, `null` se il file non si legge. */
  suspend fun header(): ByteArray?
  suspend fun image(): FluidDecodedImage
  suspend fun pages(): FluidPdfPageSource
  /** `null` se i byte non sono testo. */
  suspend fun text(): FluidTextDocument?
}

/** L'esito del preset di serie: il tipo a cui è arrivato (anche dai primi byte) e, se ha aperto, il tentativo. */
internal class FluidBuiltinResult(val type: FluidViewerType, val attempt: FluidViewerAttempt?)

/**
 * I preset di serie con i ripieghi di F6C.4, senza Android: il preset di [start]; per un file senza tipo dal nome o
 * dal MIME, quello dei primi byte; per un'immagine, un PDF o un testo che non si apre, il tipo della firma e poi il
 * testo. Ogni motivo finisce in [failures] (il primo vince).
 */
internal suspend fun fluidOpenBuiltin(
  start: FluidViewerType,
  mimeType: String?,
  name: String,
  access: FluidBuiltinAccess,
  failures: FluidViewerFailures,
  media: ((FluidViewerType) -> FluidViewerContent?)?,
): FluidBuiltinResult {
  var type = start
  if (type.executable) return FluidBuiltinResult(type, null)
  if (type.preset == FluidViewerPreset.Unknown && type.mimeType == null) {
    val header = access.header()
    if (header != null) type = FluidViewerType.builtin(mimeType, name, header).type
  }
  val tried = mutableSetOf<FluidViewerPreset>()
  suspend fun attempt(preset: FluidViewerPreset): FluidViewerAttempt? {
    if (!tried.add(preset)) return null
    return try {
      when (preset) {
        FluidViewerPreset.Image -> {
          val image = access.image()
          FluidViewerAttempt(type.copy(preset = preset), image.content(name), emptyList(), null)
        }
        FluidViewerPreset.Pages -> {
          val pdf = access.pages()
          FluidViewerAttempt(type.copy(preset = preset), FluidViewerContent.Pages(pdf), emptyList(), pdf)
        }
        FluidViewerPreset.Text -> access.text()?.let {
          FluidViewerAttempt(type.copy(preset = preset, language = it.language), FluidViewerContent.Text(it), emptyList(), null)
        }
        FluidViewerPreset.Video, FluidViewerPreset.Audio ->
          media?.invoke(type)?.let { FluidViewerAttempt(type, it, emptyList(), null) }
        else -> null
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: FluidViewerException) {
      failures.record(e)
      null
    } catch (e: RuntimeException) {
      // Un decodificatore della piattaforma che cede in un modo non documentato (un PDF strano, un'immagine
      // patologica): è un file che non si apre, non un'app che cade.
      failures.record(FluidViewerException(FluidViewerFailure.Corrupt, "Il file non si apre: $name", e))
      null
    }
  }
  attempt(type.preset)?.let { return FluidBuiltinResult(it.type, it) }
  val decodable = type.preset == FluidViewerPreset.Image || type.preset == FluidViewerPreset.Pages ||
    type.preset == FluidViewerPreset.Text
  if (decodable) {
    access.header()?.let { FluidViewerType.sniff(it) }?.let { sniffed ->
      if (sniffed == FluidViewerPreset.Image || sniffed == FluidViewerPreset.Pages) attempt(sniffed)?.let { return FluidBuiltinResult(it.type, it) }
    }
    attempt(FluidViewerPreset.Text)?.let { return FluidBuiltinResult(it.type, it) }
  }
  return FluidBuiltinResult(type, null)
}

/** I preset di serie sull'`Uri` di un [FluidViewerFile], come in F6C.4. */
private class FluidUriAccess(
  private val context: Context,
  private val file: FluidViewerFile,
  private val options: FluidViewerOptions,
) : FluidBuiltinAccess {
  override suspend fun header(): ByteArray? = file.readHeader(context)
  override suspend fun image() = FluidDecodedImage.decode(context, file.uri, options.imageMaxPixels, options.animateImages)
  override suspend fun pages() = FluidPdfPageSource.open(context, file.uri, options.pdfMaxCopyBytes)
  override suspend fun text() = fluidReadTextFile(context, file.uri, file.name, file.mimeType, file.sizeBytes,
    options.textLimitBytes, options.textMaxLineChars)
}

/**
 * I preset di serie su una sorgente del registro: l'`Uri` dell'app passa dalle stesse funzioni di F6C.4, un file
 * dell'app dalle sue, un'altra sorgente dal suo flusso (l'immagine da una copia in cache, il PDF dal descrittore).
 */
internal fun fluidBuiltinAccess(
  context: Context,
  source: FluidViewerSource,
  options: FluidViewerOptions,
  header: ByteArray?,
): FluidBuiltinAccess = when (source) {
  is FluidViewerUriSource -> object : FluidBuiltinAccess {
    private val uri = FluidUriAccess(context, FluidViewerFile(source.uri, source.name, source.mimeType, source.sizeBytes), options)
    override suspend fun header() = header ?: uri.header()
    override suspend fun image() = uri.image()
    override suspend fun pages() = uri.pages()
    override suspend fun text() = uri.text()
  }
  is FluidViewerFileSource -> object : FluidBuiltinAccess {
    override suspend fun header() = header ?: FluidViewerProbe.readHeader(source)
    override suspend fun image() = FluidDecodedImage.decode(source.file, options.imageMaxPixels, options.animateImages)
    override suspend fun pages() = FluidPdfPageSource.open(source.file)
    override suspend fun text() = source.readText(options)
  }
  else -> object : FluidBuiltinAccess {
    override suspend fun header() = header ?: FluidViewerProbe.readHeader(source)

    override suspend fun image(): FluidDecodedImage {
      val copy = FluidSeekableCopy(File(context.cacheDir, "fluid-viewer"), options.sourceMaxCopyBytes, source.name) { source.open() }
      return copy.use {
        val file = withViewerCopyCredit(context) { it.file() }
        FluidDecodedImage.decode(file, options.imageMaxPixels, options.animateImages)
      }
    }

    override suspend fun pages(): FluidPdfPageSource {
      val descriptor = withViewerCopyCredit(context) { source.seekable() }
      return FluidPdfPageSource.open(descriptor)
    }

    override suspend fun text() = source.readText(options)
  }
}

private suspend fun <T> withViewerCopyCredit(context: Context, copy: suspend () -> T): T {
  val memory = ViewerMemory.get(context)
  val reservation = memory.reserve(dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory.VIEWER,
    65536L, false) ?: noViewerMemory()
  return reservation.use { copy() }
}

private suspend fun FluidViewerSource.readText(options: FluidViewerOptions): FluidTextDocument? = withContext(Dispatchers.IO) {
  try {
    open().use { FluidTextDecoder.read(it, name, mimeType, sizeBytes, options.textLimitBytes, options.textMaxLineChars) }
  } catch (e: FluidViewerException) {
    throw e
  } catch (e: IOException) {
    throw FluidViewerException(FluidViewerFailure.Unreadable, "Testo non leggibile: $name", e)
  }
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
  internal var shown by mutableStateOf(true)
  internal var options: FluidViewerOptions = FluidViewerOptions()
  internal var window: Int = 1
  internal var openWith: ((FluidViewerFile) -> Unit)? = null
  internal var share: ((FluidViewerFile) -> Unit)? = null
  internal var media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null
  /** F8.18: chi vuole sapere che un file non si è aperto (la ripresa toglie la posizione di un file sparito). */
  internal var onFailure: ((FluidViewerFile, FluidViewerException) -> Unit)? = null

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
    if (!shown) return entry?.document?.content ?: entry?.pending?.content ?: pendingContent(file).content
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
      if (entries[index] !== created || files.getOrNull(index) != file || !shown) {
        document.close()
        return@launch
      }
      document.failure?.let { failure -> onFailure?.invoke(file, failure) }
      created.pending.complete(document)
      // Il testo e le pagine restano lo stesso oggetto: chi li legge vede arrivare i dati. Il resto si sostituisce.
      created.document = if (created.pending.keeps()) {
        FluidViewerDocument(file, document.type, created.pending.content, null, document, document.details)
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

  internal fun trim() {
    for ((key, entry) in entries.toList()) {
      // Il PDF tiene posizione e conteggio; la coda sa sganciare bitmap e renderer.
      if ((entry.document?.content as? FluidViewerContent.Pages)?.source !is PendingPages) release(key, entry)
    }
    version++
  }

  internal fun closeAll() {
    for ((key, entry) in entries.toList()) release(key, entry)
  }

  private fun pendingContent(file: FluidViewerFile): Pending {
    val type = file.type
    // F6C.8: un tipo di famiglia ha il suo contenuto, che arriva intero: intanto la pagina vuota del tema.
    if (type.family != null) return PendingFamily
    return when (type.preset) {
      FluidViewerPreset.Text -> PendingText(type.language)
      FluidViewerPreset.Pages -> PendingPages()
      FluidViewerPreset.Image, FluidViewerPreset.Video -> PendingImage
      else -> PendingFamily
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

  private object PendingFamily : Pending {
    override val content: FluidViewerContent = FluidViewerContent.Custom {}
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
    override val countFinal: Boolean get() = source != null

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
  val resources = remember(context) { FluidGlassResources.get(context) }
  state.shown = resources.uiVisible.collectAsState().value
  DisposableEffect(state) {
    val trim = resources.observeTrim { level ->
      if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) state.trim()
    }
    onDispose { trim.close(); state.closeAll() }
  }
  return state
}
