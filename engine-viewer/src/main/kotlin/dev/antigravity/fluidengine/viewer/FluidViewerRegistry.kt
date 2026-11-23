package dev.antigravity.fluidengine.viewer

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.Locale
import java.util.ServiceConfigurationError
import java.util.ServiceLoader
import java.util.concurrent.CopyOnWriteArrayList

/**
 * F6C.8 · Il registro dei moduli dei visori. Un modulo di famiglia aggiunge i suoi tipi al risolutore **senza
 * toccarlo**: basta che dichiari la sua [FluidViewerFamily] in `META-INF/services`, e il registro la trova con
 * `ServiceLoader` la prima volta che serve. Niente App Startup, niente codice nell'`Application`.
 *
 * **Come si decide**, per [FluidViewerType.of], `FluidViewerFile.open` e [open]:
 * 1. un **eseguibile** resta sconosciuto, sempre, prima di chiedere alle famiglie (la regola di F6C.4);
 * 2. si raccolgono i **candidati**: i tipi delle famiglie e il preset di serie, ognuno con la forza del
 *    riconoscimento. La voce del pacchetto ZIP vale più dei primi byte, che valgono più dell'estensione, che vale
 *    più del MIME;
 * 3. si **ordinano** per forza, poi per priorità; a parità vince il preset di serie, poi l'ordine di `id`. Un tipo di
 *    famiglia batte la tabella «noti ma senza decodificatore» di oggi (docx, zip, svg… → sconosciuto), che non
 *    è un candidato;
 * 4. si **provano in ordine**: un `open` che dà `null` o un'eccezione passa al candidato dopo, e la prima eccezione
 *    resta come motivo;
 * 5. il **ripiego** è lo sconosciuto con il motivo e «Apri con…».
 *
 * Senza famiglie il visore fa esattamente quello che faceva prima (F6C.4): stessi tentativi, stesse letture.
 */
object FluidViewerRegistry {
  private val installed = CopyOnWriteArrayList<FluidViewerFamily>()
  private val hidden = CopyOnWriteArrayList<String>()
  @Volatile private var discovered: List<FluidViewerFamily>? = null

  /**
   * Le famiglie trovate con `ServiceLoader` (`META-INF/services`), più quelle installate a mano con [install], meno
   * quelle tolte con [uninstall]. Una famiglia installata con l'`id` di una trovata la sostituisce.
   */
  val families: List<FluidViewerFamily>
    get() {
      val manual = installed.toList()
      val ids = manual.map { it.id }.toSet()
      return (discover().filter { it.id !in ids } + manual).filter { it.id !in hidden }
    }

  /** Aggiunge famiglie a mano: per i test, e per chi vuole un'istanza configurata al posto di quella trovata. */
  fun install(vararg family: FluidViewerFamily) {
    for (f in family) {
      installed.removeAll { it.id == f.id }
      installed += f
      hidden.remove(f.id)
    }
  }

  /** Toglie le famiglie con questi `id`, trovate o installate: il loro tipo torna allo sconosciuto. */
  fun uninstall(vararg id: String) {
    for (i in id) {
      installed.removeAll { it.id == i }
      if (i !in hidden) hidden += i
    }
  }

  /**
   * Apre [source] con il risolutore: un tipo di famiglia se un modulo lo conosce, altrimenti i preset di serie, e lo
   * sconosciuto con «Apri con…» se niente lo apre. Serve anche ai moduli per i file dentro i file (la voce di un
   * archivio, l'allegato di una mail): un PDF dentro uno ZIP usa il preset PDF, un DOCX allegato usa Office se c'è.
   * Il documento **possiede** la sorgente e la chiude con sé; se l'apertura fallisce o è annullata la chiude subito.
   */
  suspend fun open(
    context: Context,
    source: FluidViewerSource,
    options: FluidViewerOptions = FluidViewerOptions(),
    openWith: (() -> Unit)? = null,
    share: (() -> Unit)? = null,
    media: ((FluidViewerType) -> FluidViewerContent?)? = null,
  ): FluidViewerDocument {
    val app = context.applicationContext ?: context
    val uri = (source as? FluidViewerUriSource)?.uri ?: (source as? FluidViewerFileSource)?.file?.let(Uri::fromFile) ?: Uri.EMPTY
    val file = FluidViewerFile(uri, source.name, source.mimeType, source.sizeBytes)
    return fluidOpenSource(app, file, source, families, options, openWith, share, media)
  }

  /**
   * La miniatura del contenuto di [source], larga al più [sizePx]: quella della famiglia che lo apre, nell'ordine dei
   * candidati, e (F6C.17) quelle di engine-viewer di serie, cioè le prime righe del testo e del codice coi colori e
   * l'icona di un APK. `null` se niente ne dà una: per le foto e i PDF la miniatura è dell'app, e un tipo che niente
   * legge non ha contenuto da mostrare. Non chiude [source].
   *
   * Per una griglia usare [FluidFileThumbnails.load], che aggiunge la cache, il tempo massimo, le immagini e i PDF, e
   * dà sempre un'immagine (l'icona del documento con l'estensione, come File, se non c'è altro).
   */
  suspend fun thumbnail(context: Context, source: FluidViewerSource, sizePx: Int, options: FluidViewerOptions = FluidViewerOptions()): ImageBitmap? =
    fluidThumbnailOf(context, source, sizePx, options, everything = false)?.takeUnless { it.isPlaceholder }?.image

  private fun discover(): List<FluidViewerFamily> {
    discovered?.let { return it }
    synchronized(this) {
      discovered?.let { return it }
      val found = ArrayList<FluidViewerFamily>()
      // La forma che R8 riconosce: `ServiceLoader.load(X::class.java, X::class.java.classLoader)` e il suo iteratore.
      val iterator = ServiceLoader.load(FluidViewerFamily::class.java, FluidViewerFamily::class.java.classLoader).iterator()
      var errors = 0
      while (errors < MaxDiscoveryErrors) {
        try {
          if (!iterator.hasNext()) break
          val family = iterator.next()
          if (found.none { it.id == family.id }) found += family
        } catch (e: ServiceConfigurationError) {
          // Una famiglia che non si carica (una classe tolta da R8 a mano, un costruttore che cade) non ferma le altre.
          errors++
          Log.w(Tag, "Famiglia del visore non caricata", e)
        }
      }
      val result = found.toList()
      discovered = result
      return result
    }
  }

  private const val Tag = "FluidViewerRegistry"
  private const val MaxDiscoveryErrors = 16
}

/** Un candidato del risolutore: un tipo di famiglia, o il preset di serie ([family] `null`). */
internal class FluidViewerCandidate(
  val type: FluidViewerType,
  val strength: Int,
  val priority: Int,
  val family: FluidViewerFamily?,
  val familyType: FluidViewerFamilyType?,
) {
  override fun toString(): String = "${family?.id ?: "serie"}:${familyType?.id ?: type.preset} forza $strength priorità $priority"
}

/** I primi byte e, per gli ZIP, i nomi delle voci: letti una volta, solo se servono. */
internal class FluidViewerProbe(val header: ByteArray?, val entries: Set<String>?) {
  companion object {
    val Empty = FluidViewerProbe(null, null)

    /**
     * Legge la testa di [source] se una famiglia ha una firma, o se lo chiede il preset di serie (file senza tipo dal
     * nome o dal MIME); poi l'indice dello ZIP, se la testa è di uno ZIP e qualche firma cerca una voce.
     */
    suspend fun read(source: FluidViewerSource, families: List<FluidViewerFamily>, always: Boolean = false): FluidViewerProbe {
      val signatures = families.flatMap { f -> f.types.mapNotNull { it.signature } }
      if (signatures.isEmpty() && !always) return Empty
      val header = readHeader(source) ?: return Empty
      val entries = if (signatures.any { it.needsEntries } && FluidZipDirectory.looksLikeZip(header)) {
        try {
          withContext(Dispatchers.IO) { source.channel().use { FluidZipDirectory.names(it) } }
        } catch (e: CancellationException) {
          throw e
        } catch (_: Exception) {
          null
        }
      } else {
        null
      }
      return FluidViewerProbe(header, entries)
    }

    suspend fun readHeader(source: FluidViewerSource): ByteArray? = try {
      withContext(Dispatchers.IO) {
        source.open().use { input ->
          val bytes = ByteArray(FluidViewerSignature.HeaderBytes)
          var filled = 0
          while (filled < bytes.size) {
            val n = input.read(bytes, filled, bytes.size - filled)
            if (n < 0) break
            filled += n
          }
          bytes.copyOf(filled)
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
  }
}

/** Il risolutore puro: dai tipi delle famiglie e dal preset di serie ai candidati in ordine. */
internal object FluidViewerResolver {
  /**
   * I candidati per un file, nell'ordine in cui si provano. Vuoto per un eseguibile. [header] e [entries] possono
   * mancare (`FluidViewerType.of` non legge il file): allora le firme non si riconoscono.
   */
  fun candidates(
    families: List<FluidViewerFamily>,
    mimeType: String?,
    fileName: String,
    header: ByteArray?,
    entries: Set<String>?,
  ): List<FluidViewerCandidate> {
    val builtin = FluidViewerType.builtin(mimeType, fileName, header)
    if (builtin.type.executable) return emptyList()
    val mime = FluidViewerType.normalizeMime(mimeType)
    val leaf = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase(Locale.ROOT)
    val extension = FluidViewerType.extensionOf(leaf)
    val baseKind = FluidFileKind.from(mimeType, fileName)
    val out = ArrayList<FluidViewerCandidate>()
    if (builtin.type.preset != FluidViewerPreset.Unknown) {
      // La firma che conferma il preset deciso dal nome o dal MIME lo porta alla forza della firma: l'HEIC di serie
      // resta davanti a quello di `graphics` anche quando la famiglia lo riconosce dai byte.
      val confirmed = header != null && FluidViewerType.sniff(header) == builtin.type.preset
      val strength = if (confirmed) maxOf(builtin.strength, FluidViewerStrength.Bytes) else builtin.strength
      out += FluidViewerCandidate(builtin.type, strength, 0, null, null)
    }
    for (family in families) {
      for (type in family.types) {
        var strength = 0
        type.signature?.let { strength = maxOf(strength, it.strength(header, entries)) }
        if (extension.isNotEmpty() && type.extensions.any { wanted ->
            val clean = FluidViewerFamilyType.normalizedExtension(wanted)
            clean == extension || clean.contains('.') && leaf.endsWith(".$clean")
          }
        ) strength = maxOf(strength, FluidViewerStrength.Extension)
        if (mime != null && type.mimeTypes.any { mimeMatches(it, mime) }) strength = maxOf(strength, FluidViewerStrength.Mime)
        if (strength == 0) continue
        val kind = if (baseKind == FluidFileKind.Other) type.kind else baseKind
        val resolved = FluidViewerType(type.preset, kind, mime, null, family = family.id, label = type.label, familyType = type.id)
        out += FluidViewerCandidate(resolved, strength, type.priority, family, type)
      }
    }
    return out.sortedWith(
      compareByDescending<FluidViewerCandidate> { it.strength }
        .thenByDescending { it.priority }
        .thenBy { if (it.family == null) 0 else 1 }
        .thenBy { it.family?.id.orEmpty() }
        .thenBy { it.familyType?.id.orEmpty() },
    )
  }

  private fun mimeMatches(pattern: String, mime: String): Boolean {
    val clean = pattern.substringBefore(';').trim().lowercase(Locale.ROOT)
    return if (clean.endsWith("/*")) mime.startsWith(clean.dropLast(1)) else clean == mime
  }
}

/** Ciò che un tentativo ha aperto: il tipo che l'ha deciso, il contenuto, i dettagli e ciò che va chiuso. */
internal class FluidViewerAttempt(
  val type: FluidViewerType,
  val content: FluidViewerContent,
  val details: List<FluidViewerDetail>,
  val resource: Closeable?,
)

/** Il primo motivo per cui un tentativo non è andato: lo mostra lo sconosciuto. */
internal class FluidViewerFailures {
  var first: FluidViewerException? = null
    private set

  fun record(e: FluidViewerException) {
    if (first == null) first = e
  }
}

/** L'esito del giro sui candidati: un tentativo riuscito, oppure il tipo e il motivo per lo sconosciuto. */
internal class FluidViewerOutcome(val type: FluidViewerType, val attempt: FluidViewerAttempt?, val failure: FluidViewerException?)

/**
 * Il giro sui candidati, senza Android: [builtin] prova il preset di serie con i suoi ripieghi (F6C.4), [family] il
 * tipo di una famiglia. Il primo che apre vince. Un `null` o una [FluidViewerException] passano al dopo; un'altra
 * eccezione vale `Corrupt`, un `OutOfMemoryError` `TooLarge`. Con nessun candidato aperto, l'esito porta il tipo del
 * primo candidato (o [fallback]) e il primo motivo.
 */
internal suspend fun fluidOpenCandidates(
  candidates: List<FluidViewerCandidate>,
  fallback: FluidViewerType,
  name: String,
  failures: FluidViewerFailures,
  builtin: suspend (FluidViewerType) -> FluidViewerAttempt?,
  family: suspend (FluidViewerCandidate) -> FluidViewerOpened?,
): FluidViewerOutcome {
  for (candidate in candidates) {
    if (candidate.family == null) {
      builtin(candidate.type)?.let { return FluidViewerOutcome(it.type, it, null) }
      continue
    }
    val opened = try {
      family(candidate)
    } catch (e: CancellationException) {
      throw e
    } catch (e: FluidViewerException) {
      failures.record(e)
      null
    } catch (e: Exception) {
      failures.record(FluidViewerException(FluidViewerFailure.Corrupt, "Il file non si apre: $name", e))
      null
    } catch (e: OutOfMemoryError) {
      failures.record(FluidViewerException(FluidViewerFailure.TooLarge, "Il file è troppo grande da aprire: $name", e))
      null
    } catch (e: StackOverflowError) {
      failures.record(FluidViewerException(FluidViewerFailure.Corrupt, "Il file non si apre: $name", e))
      null
    } catch (e: LinkageError) {
      // La libreria nativa della famiglia (libarchive, Filament) non si carica su questo dispositivo: non è il file,
      // è il dispositivo. Si passa al candidato dopo e, in fondo, allo sconosciuto con «Apri con…».
      failures.record(FluidViewerException(FluidViewerFailure.Unsupported, "Questo dispositivo non apre il file: $name", e))
      null
    }
    if (opened != null) {
      return FluidViewerOutcome(candidate.type, FluidViewerAttempt(candidate.type, opened.content, opened.details, opened), null)
    }
  }
  return FluidViewerOutcome(candidates.firstOrNull()?.type ?: fallback, null, failures.first)
}

/** Un file aperto dal registro, prima di diventare un [FluidViewerDocument]. */
internal class FluidViewerOpening(
  val type: FluidViewerType,
  val content: FluidViewerContent,
  val failure: FluidViewerException?,
  val resource: Closeable?,
  val details: List<FluidViewerDetail>,
)

/** L'apertura col registro, nel documento del [file] che l'app conosce. */
internal suspend fun fluidOpenSource(
  context: Context,
  file: FluidViewerFile,
  source: FluidViewerSource,
  families: List<FluidViewerFamily>,
  options: FluidViewerOptions,
  openWith: (() -> Unit)?,
  share: (() -> Unit)?,
  media: ((FluidViewerType) -> FluidViewerContent?)?,
): FluidViewerDocument {
  val opening = fluidOpenWithRegistry(context, source, families, options, openWith, share, media)
  return FluidViewerDocument(file, opening.type, opening.content, opening.failure, opening.resource, opening.details)
}

/**
 * L'apertura col registro di un file già in una sorgente: i candidati di [families], i preset di serie su [source]
 * (per un `Uri` dell'app, le funzioni di F6C.4) e lo sconosciuto. Un eseguibile non si legge nemmeno. La sorgente
 * passa al documento, o si chiude subito se niente la tiene: dopo un ripiego, un errore o un annullamento.
 */
internal suspend fun fluidOpenWithRegistry(
  context: Context,
  source: FluidViewerSource,
  families: List<FluidViewerFamily>,
  options: FluidViewerOptions,
  openWith: (() -> Unit)?,
  share: (() -> Unit)?,
  media: ((FluidViewerType) -> FluidViewerContent?)?,
): FluidViewerOpening {
  val info = FluidFileInfo(source.name, source.mimeType, source.sizeBytes)
  val unknown = FluidViewerContent.Unknown(info, openWith, share)
  var handed = false
  try {
    val start = FluidViewerType.builtin(source.mimeType, source.name, null).type
    if (start.executable) return FluidViewerOpening(start, unknown, null, null, emptyList())
    val needsHeader = start.preset == FluidViewerPreset.Unknown && start.mimeType == null
    val probe = FluidViewerProbe.read(source, families, always = needsHeader)
    val candidates = FluidViewerResolver.candidates(families, source.mimeType, source.name, probe.header, probe.entries)
    val failures = FluidViewerFailures()
    val access = fluidBuiltinAccess(context, source, options, probe.header)
    val outcome = fluidOpenCandidates(
      candidates,
      fallback = FluidViewerType.builtin(source.mimeType, source.name, probe.header).type,
      name = source.name,
      failures = failures,
      builtin = { type -> fluidOpenBuiltin(type, source.mimeType, source.name, access, failures, media).attempt },
      family = { candidate ->
        val request = FluidViewerRequest(context, source, requireNotNull(candidate.familyType), options, openWith, share, media)
        requireNotNull(candidate.family).open(request)
      },
    )
    val attempt = outcome.attempt ?: return FluidViewerOpening(outcome.type, unknown, outcome.failure, null, emptyList())
    handed = true
    return FluidViewerOpening(attempt.type, attempt.content, null, FluidClosing(attempt.resource, source), attempt.details)
  } finally {
    if (!handed) source.close()
  }
}

/** Chiude prima ciò che il contenuto teneva aperto, poi la sorgente. */
internal class FluidClosing(private vararg val parts: Closeable?) : Closeable {
  override fun close() {
    var first: Throwable? = null
    for (part in parts) {
      try {
        part?.close()
      } catch (t: Throwable) {
        if (first == null) first = t
      }
    }
    first?.let { throw it }
  }
}
