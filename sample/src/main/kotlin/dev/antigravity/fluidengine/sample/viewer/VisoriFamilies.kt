package dev.antigravity.fluidengine.sample.viewer

// F6C.15 · Le famiglie della galleria «Visori»: un file per tipo di ogni modulo, presi dai corpi di prova dei pacchetti
// F6C.11–F6C.14 (copiati byte per byte in `sample/src/main/assets/visori/`). Tutti si aprono dalla loro miniatura (F6C.10)
// e hanno il pannello info della loro famiglia.
//
// engine-viewer-ar non è nella galleria: chiede la fotocamera, e Fluid Glass è un'app pubblicata che non la chiede
// (`checkArViewerManifests` lo controlla). Il tasto «AR» si prova nell'APK di prova del modulo (F6C.16).

/**
 * Una famiglia della galleria. [id] è quello della `FluidViewerFamily` del modulo (`null` per i preset di serie di
 * engine-viewer), [module] il modulo Gradle, [sentence] la frase sola che la descrive nella galleria.
 */
internal enum class VisoriFamiglia(val id: String?, val title: String, val module: String, val sentence: String) {
  Serie(null, "Di serie", "engine-viewer",
    "Foto, video, audio, PDF, testo e codice, con lo sconosciuto e l'eseguibile, senza moduli in più."),
  Office("office", "Office", "engine-viewer-office",
    "Word, Excel, PowerPoint e i loro gemelli aperti: pagine come il documento, fogli in griglia e slide."),
  Archivi("archive", "Archivi", "engine-viewer-archive",
    "ZIP, 7z, RAR, TAR e i compressi: l'elenco con le cartelle, e ogni file dentro col suo preset."),
  Dati("data", "Testi ricchi e dati", "engine-viewer-data",
    "Markdown e HTML resi, CSV come tabella, JSON e XML come albero, libri, mail, eventi e contatti."),
  Grafica("graphics", "Immagini speciali e caratteri", "engine-viewer-graphics",
    "HEIC, AVIF, RAW, TIFF, PSD e SVG, e il provino dei caratteri con gli assi variabili."),
  Scena("3d", "Modelli 3D", "engine-viewer-3d",
    "GLB, OBJ e STL con Filament: la luce e l'ombra a terra, da girare con un dito."),
  ;

  /** La rotta del suo visore e la sottocartella dei suoi file (quella di F6C.5 per i file di serie). */
  val scene: String get() = if (this == Serie) "galleria" else name.lowercase()

  /** I file di prova della famiglia, nell'ordine della galleria. */
  val files: List<VisoreFile> get() = when (this) {
    Serie -> VisoriFiles.gallery
    else -> VisoriFamilyFiles.of(this)
  }
}

private object VisoriFamilyFiles {
  /** [asset] è il nome nell'APK, se diverso: AAPT decomprime gli asset `.gz`, e il GZ viaggia come `.gz.fixture`. */
  private fun file(family: VisoriFamiglia, id: String, title: String, name: String, mime: String?, tipo: VisoreTipo,
    credit: String, asset: String = name) = VisoreFile(id, title, name, mime, null, tipo, credit,
    asset = "visori/${folder(family)}/$asset", family = family)

  private fun folder(family: VisoriFamiglia) = when (family) {
    VisoriFamiglia.Office -> "office"
    VisoriFamiglia.Archivi -> "archivi"
    VisoriFamiglia.Dati -> "dati"
    VisoriFamiglia.Grafica -> "grafica"
    VisoriFamiglia.Scena -> "scena"
    VisoriFamiglia.Serie -> error("I file di serie stanno in res/raw")
  }

  private const val OfficeCredit = "Corpo di prova di engine-viewer-office (F6C.11), CC0"
  private const val ArchiveCredit = "Corpo di prova di engine-viewer-archive (F6C.12), CC0"
  private const val DataCredit = "Corpo di prova di engine-viewer-data (F6C.13), generato per il collaudo"
  private const val GraphicsCredit = "Corpo di prova di engine-viewer-graphics (F6C.14), CC0"
  private const val SceneCredit = "Corpo di prova di engine-viewer-3d (F6C.14), CC0"

  private val office by lazy {
    val o = VisoriFamiglia.Office
    listOf(
      file(o, "office-docx", "Canale di accesso", "Canale di accesso.docx",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document", VisoreTipo.Office, OfficeCredit),
      file(o, "office-xlsx", "Bilancio 2026", "Bilancio 2026.xlsx",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", VisoreTipo.Foglio, OfficeCredit),
      file(o, "office-pptx", "Rotte di novembre", "Rotte di novembre.pptx",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation", VisoreTipo.Presentazione,
        OfficeCredit),
      file(o, "office-odt", "Canale", "Canale.odt", "application/vnd.oasis.opendocument.text", VisoreTipo.Office,
        OfficeCredit),
      file(o, "office-ods", "Bilancio", "Bilancio.ods", "application/vnd.oasis.opendocument.spreadsheet",
        VisoreTipo.Foglio, OfficeCredit),
      file(o, "office-odp", "Rotte", "Rotte.odp", "application/vnd.oasis.opendocument.presentation",
        VisoreTipo.Presentazione, OfficeCredit),
      file(o, "office-rtf", "Canale", "Canale.rtf", "application/rtf", VisoreTipo.Office, OfficeCredit),
    )
  }

  private val archives by lazy {
    val a = VisoriFamiglia.Archivi
    listOf(
      file(a, "archivio-zip", "Progetto", "progetto.zip", "application/zip", VisoreTipo.Archivio, ArchiveCredit),
      file(a, "archivio-7z", "Cartelle", "cartelle.7z", "application/x-7z-compressed", VisoreTipo.Archivio,
        ArchiveCredit),
      file(a, "archivio-rar", "Archivio RAR 5", "rar5.rar", "application/vnd.rar", VisoreTipo.Archivio, ArchiveCredit),
      file(a, "archivio-tar", "Cartelle", "cartelle.tar", "application/x-tar", VisoreTipo.Archivio, ArchiveCredit),
      file(a, "archivio-tgz", "Cartelle compresse", "cartelle.tgz", "application/gzip", VisoreTipo.Archivio,
        ArchiveCredit),
      file(a, "archivio-gz", "Nota compressa", "nota.txt.gz", "application/gzip", VisoreTipo.Archivio, ArchiveCredit,
        asset = "nota.txt.gz.fixture"),
    )
  }

  private val data by lazy {
    val d = VisoriFamiglia.Dati
    listOf(
      file(d, "dati-md", "Nota", "nota.md", "text/markdown", VisoreTipo.Dati, DataCredit),
      file(d, "dati-html", "Pagina", "pagina.html", "text/html", VisoreTipo.Dati, DataCredit),
      file(d, "dati-csv", "Spese", "spese.csv", "text/csv", VisoreTipo.Dati, DataCredit),
      file(d, "dati-tsv", "Misure", "misure.tsv", "text/tab-separated-values", VisoreTipo.Dati, DataCredit),
      file(d, "dati-json", "Configurazione", "config.json", "application/json", VisoreTipo.Dati, DataCredit),
      file(d, "dati-ndjson", "Eventi", "eventi.ndjson", "application/x-ndjson", VisoreTipo.Dati, DataCredit),
      file(d, "dati-xml", "Catalogo", "catalogo.xml", "application/xml", VisoreTipo.Dati, DataCredit),
      file(d, "dati-plist", "Info", "Info.plist", "application/x-plist", VisoreTipo.Dati, DataCredit),
      file(d, "dati-epub", "Libro", "libro3.epub", "application/epub+zip", VisoreTipo.Libro, DataCredit),
      file(d, "dati-eml", "Messaggio", "messaggio.eml", "message/rfc822", VisoreTipo.Dati, DataCredit),
      file(d, "dati-ics", "Riunioni", "riunioni.ics", "text/calendar", VisoreTipo.Dati, DataCredit),
      file(d, "dati-vcf", "Contatti", "contatti.vcf", "text/vcard", VisoreTipo.Dati, DataCredit),
    )
  }

  private val graphics by lazy {
    val g = VisoriFamiglia.Grafica
    listOf(
      file(g, "grafica-heic", "Studio", "studio.heic", "image/heic", VisoreTipo.Grafica, GraphicsCredit),
      file(g, "grafica-avif", "Studio", "studio.avif", "image/avif", VisoreTipo.Grafica, GraphicsCredit),
      file(g, "grafica-dng", "Fotocamera", "camera.dng", "image/x-adobe-dng", VisoreTipo.Grafica, GraphicsCredit),
      file(g, "grafica-tiff", "Studio a pagine", "studio-pages.tiff", "image/tiff", VisoreTipo.Grafica, GraphicsCredit),
      file(g, "grafica-psd", "Composito", "composito.psd", "image/vnd.adobe.photoshop", VisoreTipo.Grafica,
        GraphicsCredit),
      file(g, "grafica-svg", "Tracce", "tracce.svg", "image/svg+xml", VisoreTipo.Grafica, GraphicsCredit),
      file(g, "font-ttf", "Studio", "studio.ttf", "font/ttf", VisoreTipo.Carattere, GraphicsCredit),
      file(g, "font-variabile", "Studio variabile", "studio-variable.ttf", "font/ttf", VisoreTipo.Carattere,
        GraphicsCredit),
      file(g, "font-otf", "Studio", "studio.otf", "font/otf", VisoreTipo.Carattere, GraphicsCredit),
      file(g, "font-ttc", "Collezione", "studio.ttc", "font/collection", VisoreTipo.Carattere, GraphicsCredit),
      file(g, "font-woff", "Studio", "studio.woff", "font/woff", VisoreTipo.Carattere, GraphicsCredit),
    )
  }

  private val scene by lazy {
    val s = VisoriFamiglia.Scena
    listOf(
      file(s, "modello-glb", "Anelli", "anelli.glb", "model/gltf-binary", VisoreTipo.Modello, SceneCredit),
      // L'OBJ di engine-viewer-ar: quello del 3D chiama il suo materiale accanto (materiali.mtl), che una sorgente
      // semplice non dà; questo non ne ha bisogno.
      file(s, "modello-obj", "Piramide", "piramide.obj", "model/obj", VisoreTipo.Modello,
        "Corpo di prova di engine-viewer-ar (F6C.16), CC0: 36 cm di lato, in metri"),
      file(s, "modello-stl", "Piramide", "piramide-binaria.stl", "model/stl", VisoreTipo.Modello, SceneCredit),
    )
  }

  fun of(family: VisoriFamiglia): List<VisoreFile> = when (family) {
    VisoriFamiglia.Office -> office
    VisoriFamiglia.Archivi -> archives
    VisoriFamiglia.Dati -> data
    VisoriFamiglia.Grafica -> graphics
    VisoriFamiglia.Scena -> scene
    VisoriFamiglia.Serie -> VisoriFiles.gallery
  }
}

/** I file della scena «Senza moduli»: quelli di tutte le famiglie, aperti con il solo engine-viewer. */
internal val VisoriSenzaModuliFiles: List<VisoreFile>
  get() = VisoriFamiglia.entries.filter { it != VisoriFamiglia.Serie }.flatMap { it.files }
