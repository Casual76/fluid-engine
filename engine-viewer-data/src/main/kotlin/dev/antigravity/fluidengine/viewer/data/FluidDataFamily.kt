package dev.antigravity.fluidengine.viewer.data

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * F6C.13 · La famiglia «data» dei visori: testi ricchi e dati, con una **lettura propria** sopra engine-ui
 * (`docs/liquid-glass/ricerca/visori-2.md` §1.3). Si registra da sola con `META-INF/services`: basta la dipendenza
 * `implementation project(':engine-viewer-data')`, o `engine-viewer-all`.
 *
 * | tipo | pagina |
 * |---|---|
 * | Markdown | reso con `FluidMarkdown`, con «Reso \| Sorgente» |
 * | HTML | `FluidWebContent` chiuso: le risorse relative si caricano (se l'app dà la cartella), le remote mai |
 * | CSV, TSV | la griglia (`Grid`), letta a flusso: separatore, virgolette e intestazione riconosciuti |
 * | JSON, JSONC, NDJSON, XML, plist | un albero con i punti colorati per tipo, «Albero \| Testo», «Mostra altri», riga e colonna degli errori |
 * | EPUB 2 e 3 | a capitoli, con l'indice e le immagini; con un DRM è «protetto» |
 * | EML | intestazioni, corpo HTML (immagini `cid:`) o di testo, allegati che si aprono col risolutore |
 * | ICS | gli eventi come schede: fusi, giornate intere, ripetizioni in italiano |
 * | VCF | i contatti come schede, con la foto |
 *
 * Ogni tipo dà al pannello info i suoi dati (`FluidViewerOpened.details`): i fatti in capsule hanno la sezione `null`,
 * le altre righe la loro sezione; [fluidDataViewerInfo] li trasforma in un `FluidViewerInfo`.
 *
 * La famiglia trovata da `ServiceLoader` usa [FluidDataSettings] di serie; un'app che vuole dare la cartella di un HTML,
 * il permesso per le immagini remote o «Apri con…» per gli allegati installa la sua:
 * `FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(…)))`.
 *
 * **Nessuna rete, mai**: il modulo non ha il permesso INTERNET e non apre connessioni; ogni risorsa di una pagina web
 * passa da `onRequest`, che serve solo i byte del file e blocca il resto ([DataWeb.Policy]).
 */
class FluidDataFamily(val settings: FluidDataSettings = FluidDataSettings()) : FluidViewerFamily {

  override val id: String = Id

  override val types: List<FluidViewerFamilyType> = listOf(
    FluidViewerFamilyType(
      Markdown, "Documento Markdown", extensions = setOf("md", "markdown", "mdown", "mkd", "mkdn"),
      mimeTypes = setOf("text/markdown", "text/x-markdown"), kind = FluidFileKind.Text, priority = Priority,
    ),
    FluidViewerFamilyType(
      Html, "Pagina web", extensions = setOf("html", "htm", "xhtml"), mimeTypes = setOf("text/html", "application/xhtml+xml"),
      kind = FluidFileKind.Text, priority = Priority,
    ),
    FluidViewerFamilyType(
      Csv, "Tabella CSV", extensions = setOf("csv"), mimeTypes = setOf("text/csv", "application/csv", "text/comma-separated-values"),
      kind = FluidFileKind.Spreadsheet, priority = Priority, preset = FluidViewerPreset.Grid,
    ),
    FluidViewerFamilyType(
      Tsv, "Tabella TSV", extensions = setOf("tsv", "tab"), mimeTypes = setOf("text/tab-separated-values"),
      kind = FluidFileKind.Spreadsheet, priority = Priority, preset = FluidViewerPreset.Grid,
    ),
    FluidViewerFamilyType(
      Json, "Dati JSON", extensions = setOf("json", "jsonc", "json5", "geojson", "webmanifest", "har", "jsonld", "topojson"),
      mimeTypes = setOf("application/json", "application/geo+json", "application/ld+json", "application/manifest+json", "text/json"),
      kind = FluidFileKind.Text, priority = Priority,
    ),
    FluidViewerFamilyType(
      Ndjson, "Dati NDJSON", extensions = setOf("ndjson", "jsonl", "ldjson"),
      mimeTypes = setOf("application/x-ndjson", "application/jsonl", "application/x-jsonlines"), kind = FluidFileKind.Text,
      priority = Priority,
    ),
    FluidViewerFamilyType(
      Xml, "Documento XML", extensions = setOf("xml", "xsd", "xsl", "xslt", "kml", "gpx", "rss", "atom", "opf", "ncx", "xliff", "resx"),
      mimeTypes = setOf("application/xml", "text/xml", "application/rss+xml", "application/atom+xml", "application/gpx+xml",
        "application/vnd.google-earth.kml+xml"),
      kind = FluidFileKind.Text, priority = Priority,
    ),
    FluidViewerFamilyType(
      Plist, "Property list", extensions = setOf("plist"), mimeTypes = setOf("application/x-plist", "application/x-bplist"),
      signature = FluidViewerSignature.ascii("bplist00"), kind = FluidFileKind.Text, priority = Priority,
    ),
    FluidViewerFamilyType(
      Epub, "Libro EPUB", extensions = setOf("epub"), mimeTypes = setOf("application/epub+zip"),
      // L'EPUB vuole «mimetype» come prima voce, non compressa: il suo testo sta a 38 byte dall'inizio.
      signature = FluidViewerSignature.anyOf(
        FluidViewerSignature.ascii("mimetypeapplication/epub+zip", offset = 30),
        FluidViewerSignature.zipEntry("META-INF/container.xml"),
      ),
      kind = FluidFileKind.Document, priority = Priority,
    ),
    FluidViewerFamilyType(
      Eml, "Messaggio di posta", extensions = setOf("eml", "mht", "mhtml"), mimeTypes = setOf("message/rfc822"),
      kind = FluidFileKind.Document, priority = Priority,
    ),
    FluidViewerFamilyType(
      Ics, "Calendario", extensions = setOf("ics", "ical", "ifb", "vcs"), mimeTypes = setOf("text/calendar"),
      signature = FluidViewerSignature.ascii("BEGIN:VCALENDAR"), kind = FluidFileKind.Document, priority = Priority,
    ),
    FluidViewerFamilyType(
      Vcf, "Contatti", extensions = setOf("vcf", "vcard"), mimeTypes = setOf("text/vcard", "text/x-vcard", "text/directory"),
      signature = FluidViewerSignature.ascii("BEGIN:VCARD"), kind = FluidFileKind.Document, priority = Priority,
    ),
  )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened? = withContext(Dispatchers.IO) {
    val open = DataOpen(request, settings)
    when (request.type.id) {
      Markdown -> open.markdown()
      Html -> open.html()
      Csv, Tsv -> open.csv()
      Json -> open.tree(DataTreeFormat.Json)
      Ndjson -> open.tree(DataTreeFormat.Ndjson)
      Xml -> open.tree(DataTreeFormat.Xml)
      Plist -> open.tree(DataTreeFormat.Plist)
      Epub -> open.epub()
      Eml -> open.mail()
      Ics -> open.calendar()
      Vcf -> open.contacts()
      else -> null
    }
  }

  /** La copertina di un EPUB, la foto del primo contatto di un VCF; per il resto l'icona del tipo. */
  override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
    val bytes: ByteArray? = when (request.type.id) {
      Epub -> DataOpen(request, settings).zip().use { zip ->
        val book = DataEpub.read(zip)
        book.coverPath?.let { zip.read(it) }
      }
      Vcf -> {
        val text = request.source.open().use { DataText.decode(DataText.read(it, DataProfile.MaxTextBytes, request.source.sizeBytes)).text }
        DataVcard.parse(text).firstNotNullOfOrNull { it.photo }
      }
      else -> null
    }
    bytes?.let { decodePicture(it, sizePx) }
  }

  companion object {
    /** L'id della famiglia, nel pannello info e in `FluidViewerType.family`. */
    const val Id: String = "data"

    // Gli id dei tipi (`FluidViewerType.familyType`).
    const val Markdown: String = "markdown"
    const val Html: String = "html"
    const val Csv: String = "csv"
    const val Tsv: String = "tsv"
    const val Json: String = "json"
    const val Ndjson: String = "ndjson"
    const val Xml: String = "xml"
    const val Plist: String = "plist"
    const val Epub: String = "epub"
    const val Eml: String = "eml"
    const val Ics: String = "ics"
    const val Vcf: String = "vcf"

    /**
     * Davanti al preset testo di serie, che oggi apre Markdown, HTML, CSV, JSON e XML come testo: a parità di
     * riconoscimento vince la famiglia (F6C.8, «Le scelte»).
     */
    private const val Priority = 10

    /** Un'immagine ridotta a [sizePx] sul lato lungo (al più [DataProfile.MaxPictureSide]). */
    internal fun decodePicture(bytes: ByteArray, sizePx: Int = DataProfile.MaxPictureSide): ImageBitmap? {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
      if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
      val target = sizePx.coerceIn(1, DataProfile.MaxPictureSide)
      var sample = 1
      while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2
      val options = BitmapFactory.Options().apply { inSampleSize = sample }
      return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    }
  }
}
