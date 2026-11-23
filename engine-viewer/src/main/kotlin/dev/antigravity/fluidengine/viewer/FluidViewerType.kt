package dev.antigravity.fluidengine.viewer

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import java.util.Locale

/**
 * Quale preset del visore mostra un file: [Image], [Video], [Audio], [Pages], [Text], [Grid], [Custom] o [Unknown].
 *
 * Non è un `enum` di proposito, come `FluidGroupedMaterial`: un preset nuovo (un giorno gli archivi) in un `enum`
 * pubblico romperebbe ogni `when` esaustivo di un'app. Qui un preset nuovo è una costante in più, e chi confronta
 * scrive un `else`. [Grid] e [Custom] sono arrivati così con F6C.8.
 */
@Immutable
@JvmInline
value class FluidViewerPreset private constructor(private val id: Int) {

  override fun toString(): String = when (id) {
    ImageId -> "Image"
    VideoId -> "Video"
    AudioId -> "Audio"
    PagesId -> "Pages"
    TextId -> "Text"
    GridId -> "Grid"
    CustomId -> "Custom"
    else -> "Unknown"
  }

  companion object {
    private const val UnknownId = 0
    private const val ImageId = 1
    private const val VideoId = 2
    private const val AudioId = 3
    private const val PagesId = 4
    private const val TextId = 5
    private const val GridId = 6
    private const val CustomId = 7

    /** Una foto, anche animata (GIF, WebP): `FluidViewerContent.Image`. */
    val Image: FluidViewerPreset = FluidViewerPreset(ImageId)
    /** Un video: `FluidViewerContent.Media` con la superficie. Il player è dell'app. */
    val Video: FluidViewerPreset = FluidViewerPreset(VideoId)
    /** Un audio: `FluidViewerContent.Media` senza superficie. */
    val Audio: FluidViewerPreset = FluidViewerPreset(AudioId)
    /** Un documento a pagine (PDF): `FluidViewerContent.Pages`. */
    val Pages: FluidViewerPreset = FluidViewerPreset(PagesId)
    /** Testo e codice: `FluidViewerContent.Text`. */
    val Text: FluidViewerPreset = FluidViewerPreset(TextId)
    /** Tutto il resto, e ogni eseguibile: `FluidViewerContent.Unknown`, con «Apri con…». Mai un vicolo cieco. */
    val Unknown: FluidViewerPreset = FluidViewerPreset(UnknownId)
    /** F6C.8 · Un foglio di calcolo o una tabella (XLSX, ODS, CSV): `FluidViewerContent.Grid`. Lo danno le famiglie. */
    val Grid: FluidViewerPreset = FluidViewerPreset(GridId)
    /** F6C.8 · Una pagina disegnata dal modulo (archivio, albero JSON, EPUB, font, 3D): `FluidViewerContent.Custom`. */
    val Custom: FluidViewerPreset = FluidViewerPreset(CustomId)
  }
}

/**
 * Il tipo di un file per il visore, deciso da MIME, nome e, solo quando i due tacciono, dai primi byte.
 *
 * [kind] è sempre `FluidFileKind.from(mimeType, fileName)`, senza ritocchi: la stessa icona e lo stesso avviso che
 * mostra il resto dell'engine. Per questo **un eseguibile resta tale da entrambe le fonti**: un `.apk` con MIME
 * `image/png`, o un `foto.png` con MIME `application/vnd.android.package-archive`, ha [kind] `Executable` e preset
 * [FluidViewerPreset.Unknown], con l'avviso. Anche uno script (`.sh`, `.ps1`, `.bat`), che pure è testo: chi lo vuole
 * leggere lo chiede all'app, il visore non lo declassa.
 *
 * [mimeType] è il MIME normalizzato (minuscolo, senza parametri) o `null` se mancava o era generico
 * (`application/octet-stream`, il jolly di tutti i tipi, `multipart/…`). [language] è la lingua per i colori di sintassi di un [FluidViewerPreset.Text]
 * ([FluidSyntax]), `null` se il testo è semplice o la lingua non è fra quelle note.
 *
 * F6C.8: un tipo che apre un modulo di famiglia ([FluidViewerRegistry]) porta la sua [family] («office»), il
 * [familyType] («docx») e la [label] per le persone («Documento Word»); per i preset di serie sono `null`. Per un tipo
 * di famiglia [kind] resta quello di `FluidFileKind.from`, salvo quando questo non sa niente (`Other`): allora vale
 * quello dichiarato dalla famiglia, che non è mai `Executable`.
 */
@Immutable
data class FluidViewerType(
  val preset: FluidViewerPreset,
  val kind: FluidFileKind,
  val mimeType: String?,
  val language: String?,
  val family: String? = null,
  val label: String? = null,
  val familyType: String? = null,
) {
  /** Vero se il file è un eseguibile: il preset è sempre [FluidViewerPreset.Unknown] e mostra l'avviso. */
  val executable: Boolean get() = kind == FluidFileKind.Executable

  companion object {
    /**
     * Risolve un file. Precedenza, come in `FluidFileKind.from`: l'eseguibile vince sempre; poi l'estensione
     * (l'ultima: `archivio.tar.gz` è `gz`), poi il MIME, poi la firma dei primi byte ([header], se c'è). Un MIME con
     * parametri o in maiuscolo (`Text/Plain; charset=UTF-8`) vale come quello pulito; un MIME generico o malformato
     * vale come nessun MIME. Un MIME in conflitto con l'estensione perde, salvo che sia eseguibile.
     *
     * Le immagini che la piattaforma non decodifica (SVG, TIFF, PSD) vanno allo sconosciuto: niente decodificatori
     * pesanti, e «Apri con…» c'è sempre.
     *
     * F6C.8: se un modulo di famiglia conosce il tipo ([FluidViewerRegistry.families]), vince il primo candidato del
     * registro (firma dei primi byte, estensione, MIME, priorità): un `.docx` con `engine-viewer-office` è un
     * documento a pagine di «office». Le firme sulle voci di uno ZIP qui non si leggono: le guarda `open`. Senza
     * famiglie il risultato è quello di sempre.
     */
    fun of(mimeType: String?, fileName: String, header: ByteArray? = null): FluidViewerType {
      val builtin = builtin(mimeType, fileName, header)
      if (builtin.type.executable) return builtin.type
      val families = FluidViewerRegistry.families
      if (families.isEmpty()) return builtin.type
      return FluidViewerResolver.candidates(families, mimeType, fileName, header, null).firstOrNull()?.type ?: builtin.type
    }

    /** Il preset di serie (F6C.4), senza le famiglie, e la forza con cui l'ha deciso (per l'ordine del registro). */
    internal fun builtin(mimeType: String?, fileName: String, header: ByteArray? = null): FluidViewerBuiltin {
      val kind = FluidFileKind.from(mimeType, fileName)
      val mime = normalizeMime(mimeType)
      val leaf = fileName.substringAfterLast('/').substringAfterLast('\\')
      val extension = extensionOf(leaf)
      if (kind == FluidFileKind.Executable) return FluidViewerBuiltin(FluidViewerType(FluidViewerPreset.Unknown, kind, mime, null), 0)
      val byName = presetForExtension(extension) ?: presetForName(leaf)
      val byMime = mime?.let(::presetForMime)
      val bySignature = if (byName == null && byMime == null && header != null) sniff(header) else null
      // Fra video e audio decide il MIME: un `.mp4` o un `.webm` può essere solo audio, e chi ha il file lo sa.
      val media = byName == FluidViewerPreset.Video || byName == FluidViewerPreset.Audio
      val mediaMime = byMime == FluidViewerPreset.Video || byMime == FluidViewerPreset.Audio
      val preset = (if (media && mediaMime) byMime else byName) ?: byMime ?: bySignature ?: FluidViewerPreset.Unknown
      val strength = when {
        byName != null -> FluidViewerStrength.Extension
        byMime != null -> FluidViewerStrength.Mime
        bySignature != null -> FluidViewerStrength.Bytes
        else -> 0
      }
      val language = if (preset == FluidViewerPreset.Text) {
        FluidSyntax.languageFor(leaf, mime)
      } else {
        null
      }
      return FluidViewerBuiltin(FluidViewerType(preset, kind, mime, language), strength)
    }

    /**
     * Il preset dalla firma dei primi byte (almeno 12 servono a WebP e ai contenitori ISO), o `null` se non la
     * riconosce. Serve al ripiego: un file senza estensione e senza MIME, o un `.txt` che in realtà è un PNG.
     * Non sa riconoscere il testo (lo decide [FluidTextDecoder.looksBinary] sui byte veri).
     */
    fun sniff(header: ByteArray): FluidViewerPreset? {
      fun at(offset: Int, vararg bytes: Int): Boolean =
        header.size >= offset + bytes.size && bytes.indices.all { header[offset + it].toInt() and 0xFF == bytes[it] }
      fun ascii(offset: Int, text: String): Boolean = at(offset, *text.map { it.code }.toIntArray())
      return when {
        ascii(0, "%PDF-") -> FluidViewerPreset.Pages
        at(0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> FluidViewerPreset.Image
        at(0, 0xFF, 0xD8, 0xFF) -> FluidViewerPreset.Image
        ascii(0, "GIF87a") || ascii(0, "GIF89a") -> FluidViewerPreset.Image
        ascii(0, "RIFF") && ascii(8, "WEBP") -> FluidViewerPreset.Image
        ascii(0, "RIFF") && ascii(8, "WAVE") -> FluidViewerPreset.Audio
        ascii(0, "RIFF") && ascii(8, "AVI ") -> FluidViewerPreset.Video
        ascii(0, "BM") && bitmapHeader(header) -> FluidViewerPreset.Image
        ascii(4, "ftyp") -> isoBrand(header)
        at(0, 0x1A, 0x45, 0xDF, 0xA3) -> FluidViewerPreset.Video // Matroska e WebM
        ascii(0, "ID3") || ascii(0, "fLaC") || ascii(0, "OggS") -> FluidViewerPreset.Audio
        at(0, 0xFF) && header.size >= 2 && (header[1].toInt() and 0xE0) == 0xE0 -> FluidViewerPreset.Audio // MPEG audio
        else -> null
      }
    }

    // «BM» da solo è l'inizio di troppi testi: serve anche la misura dell'intestazione DIB, che ha pochi valori.
    private fun bitmapHeader(header: ByteArray): Boolean {
      if (header.size < 18) return false
      val dib = (14 until 18).fold(0) { acc, i -> acc or ((header[i].toInt() and 0xFF) shl (8 * (i - 14))) }
      return dib in setOf(12, 16, 40, 52, 56, 64, 108, 124)
    }

    private fun isoBrand(header: ByteArray): FluidViewerPreset? {
      if (header.size < 12) return null
      val brand = String(header, 8, 4, Charsets.US_ASCII).lowercase(Locale.ROOT)
      return when (brand) {
        "heic", "heix", "heim", "heis", "hevc", "hevx", "mif1", "msf1", "avif", "avis" -> FluidViewerPreset.Image
        "m4a ", "m4b ", "m4p " -> FluidViewerPreset.Audio
        else -> FluidViewerPreset.Video
      }
    }

    // `application/octet-stream` e simili non dicono niente: chi li manda non sapeva il tipo.
    private val GenericMimes = setOf(
      "application/octet-stream", "binary/octet-stream", "application/unknown", "application/x-unknown",
      "application/binary", "application/download", "application/force-download", "application/x-download",
      "content/unknown", "unknown/unknown", "*/*", "application/*",
    )

    internal fun normalizeMime(mimeType: String?): String? {
      val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
      val slash = mime.indexOf('/')
      if (slash <= 0 || slash == mime.lastIndex || mime.indexOf('/', slash + 1) >= 0 || ' ' in mime) return null
      return mime.takeUnless { it in GenericMimes || it.startsWith("multipart/") }
    }

    // Come `fluidFileExtension` di engine-ui (che è interno): l'ultima, senza il punto, minuscola; un nome che
    // comincia col punto (`.gitignore`) non ha estensione.
    internal fun extensionOf(leaf: String): String {
      val dot = leaf.lastIndexOf('.')
      return if (dot > 0 && dot < leaf.lastIndex) leaf.substring(dot + 1).lowercase(Locale.ROOT) else ""
    }

    private val ImageExtensions = setOf(
      "jpg", "jpeg", "jpe", "jfif", "png", "apng", "gif", "webp", "bmp", "dib", "heic", "heif", "avif", "ico", "wbmp",
    )
    private val VideoExtensions = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "3g2", "avi")
    private val AudioExtensions = setOf("mp3", "m4a", "m4b", "aac", "wav", "ogg", "oga", "opus", "flac", "amr", "weba")
    private val TextExtensions = setOf(
      "txt", "text", "log", "md", "markdown", "mdown", "json", "jsonc", "json5", "geojson", "ndjson", "webmanifest",
      "xml", "xsd", "xsl", "xslt", "plist", "html", "htm", "xhtml", "kml", "gpx", "rss", "atom",
      "css", "scss", "less", "js", "mjs", "cjs", "jsx", "ts", "tsx", "kt", "kts", "java", "gradle", "groovy",
      "py", "pyw", "c", "h", "cpp", "cc", "cxx", "hpp", "hh", "cs", "swift", "go", "rs", "rb", "php", "dart", "lua",
      "sql", "yaml", "yml", "toml", "ini", "cfg", "conf", "properties", "env", "csv", "tsv", "srt", "vtt", "diff",
      "patch", "tex", "bib", "gitignore", "gitattributes", "editorconfig", "pro",
    )
    private val TextNames = setOf(
      "readme", "license", "licence", "copying", "authors", "changelog", "notice", "makefile", "dockerfile",
      "gemfile", "procfile", ".gitignore", ".gitattributes", ".editorconfig", ".gitconfig", ".env", "gradlew",
    )

    private fun presetForExtension(extension: String): FluidViewerPreset? = when (extension) {
      "" -> null
      in ImageExtensions -> FluidViewerPreset.Image
      in VideoExtensions -> FluidViewerPreset.Video
      in AudioExtensions -> FluidViewerPreset.Audio
      "pdf" -> FluidViewerPreset.Pages
      in TextExtensions -> FluidViewerPreset.Text
      // Note ma senza un decodificatore leggero: lo sconosciuto, deciso, senza che il MIME lo ribalti.
      "svg", "svgz", "tif", "tiff", "psd", "raw", "dng", "cr2", "nef", "doc", "docx", "odt", "rtf", "pages",
      "xls", "xlsx", "ods", "numbers", "ppt", "pptx", "odp", "key", "epub", "mobi", "zip", "rar", "7z", "tar", "gz",
      "bz2", "xz", "zst", "usdz", "glb", "gltf", "obj", "stl", "ttf", "otf", "woff", "woff2", "db", "sqlite", "bin",
      -> FluidViewerPreset.Unknown
      else -> null
    }

    private fun presetForName(leaf: String): FluidViewerPreset? {
      val name = leaf.lowercase(Locale.ROOT)
      return if (name in TextNames || name.substringBeforeLast('.') in TextNames) FluidViewerPreset.Text else null
    }

    private val DecodableImageMimes = setOf(
      "image/jpeg", "image/jpg", "image/pjpeg", "image/png", "image/apng", "image/gif", "image/webp", "image/bmp",
      "image/x-bmp", "image/x-ms-bmp", "image/heic", "image/heif", "image/heic-sequence", "image/heif-sequence",
      "image/avif", "image/x-icon", "image/vnd.microsoft.icon", "image/vnd.wap.wbmp",
    )
    private val TextMimes = setOf(
      "application/json", "application/xml", "application/javascript", "application/x-javascript",
      "application/ecmascript", "application/x-yaml", "application/yaml", "application/toml", "application/sql",
      "application/x-sql", "application/x-subrip", "application/x-ndjson", "application/x-properties",
      "application/x-tex", "application/x-latex",
    )

    private fun presetForMime(mime: String): FluidViewerPreset? = when {
      mime == "application/pdf" || mime == "application/x-pdf" -> FluidViewerPreset.Pages
      mime in DecodableImageMimes -> FluidViewerPreset.Image
      mime.startsWith("image/") -> FluidViewerPreset.Unknown
      mime.startsWith("video/") -> FluidViewerPreset.Video
      mime.startsWith("audio/") -> FluidViewerPreset.Audio
      mime == "text/rtf" || mime == "text/richtext" -> FluidViewerPreset.Unknown
      mime.startsWith("text/") -> FluidViewerPreset.Text
      mime in TextMimes || mime.endsWith("+json") || mime.endsWith("+xml") -> FluidViewerPreset.Text
      else -> null
    }
  }
}

/** Il preset di serie deciso da `FluidViewerType.builtin` e la forza del riconoscimento (`FluidViewerStrength`). */
internal class FluidViewerBuiltin(val type: FluidViewerType, val strength: Int)
