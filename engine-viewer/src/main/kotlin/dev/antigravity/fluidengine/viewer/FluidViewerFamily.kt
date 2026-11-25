package dev.antigravity.fluidengine.viewer

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import java.io.Closeable
import java.util.Locale

/**
 * F6C.8 · Una famiglia di tipi in un modulo facoltativo (`engine-viewer-office`, `-archive`, `-data`, `-graphics`,
 * `-3d`). **Si registra da sola**: il modulo la dichiara in
 * `META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily` e [FluidViewerRegistry] la trova con
 * `ServiceLoader` la prima volta che risolve. Basta la dipendenza Gradle; chi non la aggiunge non ha niente, e il tipo
 * resta allo sconosciuto con «Apri con…».
 *
 * La classe ha un costruttore senza argomenti ed è senza stato: il registro ne tiene un'istanza per processo.
 *
 * ```kotlin
 * class OfficeFamily : FluidViewerFamily {
 *   override val id = "office"
 *   override val types = listOf(
 *     FluidViewerFamilyType("docx", "Documento Word", extensions = setOf("docx", "docm"),
 *       signature = FluidViewerSignature.zipEntry("word/document.xml"), preset = FluidViewerPreset.Pages),
 *   )
 *   override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? = …
 * }
 * ```
 */
interface FluidViewerFamily {
  /** «office», «archive», «data», «graphics», «3d»: unico; compare nel pannello info (`FluidViewerType.family`). */
  val id: String

  /** I tipi che sa aprire, ognuno con le sue regole di riconoscimento. */
  val types: List<FluidViewerFamilyType>

  /**
   * Apre il file di [FluidViewerRequest.source] come [FluidViewerRequest.type]. `null`, oppure una
   * [FluidViewerException], vuol dire «non questo»: il registro prova il candidato dopo, e tiene la prima eccezione
   * come motivo. Un'altra eccezione vale come un file rotto ([FluidViewerFailure.Corrupt]). La sorgente non si chiude
   * qui: è del documento, che la chiude con [FluidViewerOpened.close].
   */
  suspend fun open(request: FluidViewerRequest): FluidViewerOpened?

  /** La miniatura per la griglia dell'app e per l'apertura (F6C.10), larga al più [sizePx]. `null`: l'icona del tipo. */
  suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap? = null
}

/**
 * Un tipo di una [FluidViewerFamily] e come si riconosce. Il registro sceglie fra i candidati con quest'ordine:
 * **la firma** ([signature], dai primi byte o da una voce del pacchetto ZIP) vale più dell'**estensione**
 * ([extensions], senza il punto), che vale più del **MIME** ([mimeTypes]; «image/» seguito dall'asterisco vale per il gruppo); a parità vince la
 * [priority] più alta, e a parità anche di quella il preset di serie di engine-viewer. Così un `.zip` che contiene
 * `word/document.xml` è un Word, e una famiglia che vuole stare **dietro** al preset di serie (l'HEIC di `graphics`,
 * che serve solo dove `ImageDecoder` non lo legge) dichiara una priorità negativa; una che vuole stargli **davanti**
 * (il CSV come tabella invece che come testo) una positiva.
 *
 * [kind] dà l'icona e l'avviso quando `FluidFileKind.from` non sa niente del file; non può essere `Executable`, e
 * nessuna estensione o MIME di un eseguibile può comparire qui: **un eseguibile non arriva mai a una famiglia**.
 * [preset] è la forma del contenuto che la famiglia dà (`Pages`, `Grid`, `Custom`, …): serve a chi mostra il file
 * prima di aprirlo.
 */
@Immutable
data class FluidViewerFamilyType(
  val id: String,
  val label: String,
  val extensions: Set<String> = emptySet(),
  val mimeTypes: Set<String> = emptySet(),
  val signature: FluidViewerSignature? = null,
  val kind: FluidFileKind = FluidFileKind.Document,
  val priority: Int = 0,
  val preset: FluidViewerPreset = FluidViewerPreset.Custom,
) {
  init {
    require(id.isNotBlank()) { "Un tipo di famiglia vuole un id" }
    require(kind != FluidFileKind.Executable) { "Un eseguibile non arriva mai a una famiglia: $id" }
    for (extension in extensions) {
      require(FluidFileKind.from(null, "file.${normalizedExtension(extension)}") != FluidFileKind.Executable) {
        "L'estensione di un eseguibile non arriva mai a una famiglia: $extension ($id)"
      }
    }
    for (mime in mimeTypes) {
      require(FluidFileKind.from(mime, "") != FluidFileKind.Executable) {
        "Il MIME di un eseguibile non arriva mai a una famiglia: $mime ($id)"
      }
    }
  }

  internal companion object {
    fun normalizedExtension(extension: String): String = extension.trim().removePrefix(".").lowercase(Locale.ROOT)
  }
}

/**
 * Come si riconosce un tipo dai suoi byte. Due specie:
 * - **i primi byte** ([bytes], [ascii]) a una posizione, entro i primi [HeaderBytes];
 * - **una voce del pacchetto** ([zipEntry]): il file è uno ZIP e il suo indice ha quella voce. Vale più dei primi
 *   byte, perché dice cosa c'è dentro: un DOCX è anche uno ZIP, e vince chi lo riconosce come Word.
 *
 * [anyOf] ne accetta più d'una (un TIFF «II*» o «MM*»). Due firme uguali sono `equals`.
 */
class FluidViewerSignature private constructor(
  private val offset: Int,
  private val bytes: ByteArray?,
  private val entries: List<String>,
  private val alternatives: List<FluidViewerSignature>,
) {
  /** Vero se serve l'indice dello ZIP. */
  internal val needsEntries: Boolean get() = entries.isNotEmpty() || alternatives.any { it.needsEntries }

  /** La forza con cui questi byte e queste voci la riconoscono: 0 no, [FluidViewerStrength.Bytes] o [FluidViewerStrength.Entry]. */
  internal fun strength(header: ByteArray?, zipEntries: Set<String>?): Int {
    var best = 0
    if (bytes != null && header != null && header.size >= offset + bytes.size &&
      bytes.indices.all { header[offset + it] == bytes[it] }
    ) best = FluidViewerStrength.Bytes
    if (entries.isNotEmpty() && zipEntries != null && entries.any { wanted ->
        if (wanted.endsWith("/")) zipEntries.any { it.startsWith(wanted) } else wanted in zipEntries
      }
    ) best = FluidViewerStrength.Entry
    for (alternative in alternatives) best = maxOf(best, alternative.strength(header, zipEntries))
    return best
  }

  override fun equals(other: Any?): Boolean = other is FluidViewerSignature && offset == other.offset &&
    (bytes?.contentEquals(other.bytes ?: ByteArray(0)) ?: (other.bytes == null)) && entries == other.entries &&
    alternatives == other.alternatives

  override fun hashCode(): Int = ((offset * 31 + (bytes?.contentHashCode() ?: 0)) * 31 + entries.hashCode()) * 31 + alternatives.hashCode()

  override fun toString(): String = when {
    alternatives.isNotEmpty() -> alternatives.joinToString(" | ", "anyOf(", ")")
    entries.isNotEmpty() -> entries.joinToString(", ", "zipEntry(", ")")
    else -> "bytes(@$offset: ${bytes?.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }})"
  }

  companion object {
    /** Quanti byte di testa legge il registro: le firme stanno qui dentro (il TAR, a 257, ci sta). */
    const val HeaderBytes: Int = 4096

    /** I byte [bytes] (0–255) a partire da [offset]. */
    fun bytes(vararg bytes: Int, offset: Int = 0): FluidViewerSignature {
      require(bytes.isNotEmpty() && bytes.all { it in 0..255 }) { "Byte da 0 a 255" }
      require(offset >= 0 && offset + bytes.size <= HeaderBytes) { "La firma sta nei primi $HeaderBytes byte" }
      return FluidViewerSignature(offset, ByteArray(bytes.size) { bytes[it].toByte() }, emptyList(), emptyList())
    }

    /** Il testo [text] in ASCII a partire da [offset] («%PDF-», «glTF», «8BPS»). */
    fun ascii(text: String, offset: Int = 0): FluidViewerSignature {
      require(text.isNotEmpty() && text.all { it.code in 0..127 }) { "Solo ASCII" }
      return bytes(*IntArray(text.length) { text[it].code }, offset = offset)
    }

    /** Uno ZIP con almeno una di queste voci; un nome che finisce con «/» vale per ogni voce sotto quella cartella. */
    fun zipEntry(vararg names: String): FluidViewerSignature {
      require(names.isNotEmpty() && names.all { it.isNotBlank() }) { "Almeno una voce" }
      return FluidViewerSignature(0, null, names.map { it.removePrefix("/") }, emptyList())
    }

    /** Una qualunque di queste: vale la più forte che si riconosce. */
    fun anyOf(vararg signatures: FluidViewerSignature): FluidViewerSignature {
      require(signatures.isNotEmpty())
      return FluidViewerSignature(0, null, emptyList(), signatures.toList())
    }
  }
}

/** Le forze del riconoscimento, dalla più debole: l'ordine di F6C.8 (firma, poi estensione, poi MIME). */
internal object FluidViewerStrength {
  const val Mime = 1
  const val Extension = 2
  const val Bytes = 3
  const val Entry = 4
}

/**
 * Quello che una famiglia riceve per aprire un file: la [source] (che resta del documento), il [type] riconosciuto,
 * i tetti dell'app in [options] e il suo [context] (l'applicazione). [openWith], [share] e [media] sono quelli che
 * l'app ha dato al registro: una famiglia che apre un file dentro il suo (la voce di un archivio, un allegato) li
 * passa a [FluidViewerRegistry.open] insieme a una nuova [FluidViewerSource].
 */
class FluidViewerRequest internal constructor(
  val context: Context,
  val source: FluidViewerSource,
  val type: FluidViewerFamilyType,
  val options: FluidViewerOptions,
  val openWith: (() -> Unit)?,
  val share: (() -> Unit)?,
  val media: ((FluidViewerType) -> FluidViewerContent?)?,
)

/**
 * Un file aperto da una famiglia: il [content] per il visore (`Pages`, `Grid`, `Text`, `Image`, `Custom`…), i
 * [details] per il pannello info («Pagine 9», «Calibri → Carlito») e la chiusura ([onClose]), che il documento chiama
 * una volta sola quando la pagina esce.
 *
 * Righe che cambiano dopo l'apertura, da un filo in sottofondo (il conto di un CSV, le pagine contate): una
 * [FluidViewerLiveDetails], mai una lista di Compose cambiata da fuori (F7.20).
 */
class FluidViewerOpened(
  val content: FluidViewerContent,
  val details: List<FluidViewerDetail> = emptyList(),
  private val onClose: () -> Unit = {},
) : Closeable {
  private var closed = false

  override fun close() {
    if (closed) return
    closed = true
    onClose()
  }
}

/** Una riga del pannello info: «Pagine» → «9». [section] le raggruppa («Documento», «Caratteri»), `null` in testa. */
@Immutable
data class FluidViewerDetail(
  val label: String,
  val value: String,
  val section: String? = null,
)
