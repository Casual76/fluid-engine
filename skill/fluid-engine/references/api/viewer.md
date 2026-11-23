# API · engine-viewer

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-viewer-3d/viewer/scene/FluidSceneFamily.kt`

- `val FluidSceneViewerLicenses: List<FluidLicense>` — Licenze da aggiungere alla lista di [dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection].
- `class FluidSceneFamily : FluidViewerFamily` — Famiglia opt-in dei modelli 3D, scoperta dal registro dei visori.

## `engine-viewer-3d/viewer/scene/FluidSceneModel.kt`

- `interface FluidSceneSource : FluidViewerSource` — Sorgente di un modello con file a fianco. L'app apre solo percorsi relativi nella cartella concessa.
  - `suspend fun openRelative(path: String): InputStream?`
- `data class FluidSceneStats(val triangles: Long, val vertices: Long, val materials: Int, val textures: Int, val animations: Int, val width: Double, val height: Double, val depth: Double)` — Contatori e misure del glTF preparato, nelle unità originali del modello.
- `class FluidSceneModel internal constructor(private val bytes: ByteArray, private val sidecars: Map<String, ByteArray>, val stats: FluidSceneStats)` — Modello riusabile, letto senza Filament. [load] crea un'istanza per l'engine dell'app, senza applicare l'inquadratura o il piano d'ombra del visore. Funziona anche dopo la chiusura della sorgente.
  - `fun load(engine: Engine): FluidLoadedScene` — Da chiamare sul thread dell'engine. L'istanza va chiusa sullo stesso thread, prima dell'engine.
  - `companion object`
    - `const val MaxTriangles` — Tetto del visore per glTF e GLB. Si contano anche le istanze ripetute della stessa mesh.
- `class FluidLoadedScene internal constructor(val asset: FilamentAsset, private val loader: AssetLoader, private val resources: ResourceLoader, private val materials: UbershaderProvider) : Closeable` — Istanza legata a un engine Filament. L'app può usare [asset] e collegarlo a una scena qui o nel proprio renderer.
  - `fun attach(scene: Scene)`
  - `fun detach(scene: Scene)`
  - `fun updateResources(): Float` — Da chiamare a ogni fotogramma fino a 1, mentre Filament decodifica le texture.
- `suspend fun openFluidSceneModel(source: FluidViewerSource): FluidSceneModel` — Prepara GLB, glTF, OBJ o STL. Chi chiama mantiene la proprietà della sorgente e la chiude quando gli serve.

## `engine-viewer-3d/viewer/scene/FluidSceneViewerExtension.kt`

- `interface FluidSceneViewerExtension` — Optional companion mode for a 3D file, discovered once through [ServiceLoader].
  - `fun create(model: FluidSceneModel): FluidSceneViewerControls` — A separate set of controls for each opened model, with no UI side effects until composed.
- `interface FluidSceneViewerControls` — Connects a companion mode to the existing viewer toolbar and the page lifecycle.
  - `val overlayVisible: Boolean`
  - `fun actions(toolbar: FluidToolbarScope)`
  - `@Composable fun Overlay(active: Boolean)` — [active] is false for a page that is off screen or being dismissed.

## `engine-viewer-ar/viewer/ar/FluidArSceneExtension.kt`

- `val FluidArViewerLicenses: List<FluidLicense>` — Licenze e condizioni per la sezione licenze dell'app che include questo modulo.
- `class FluidArSceneExtension : FluidSceneViewerExtension` — Provider scoperto dal visore 3D quando l'app include questo modulo.

## `engine-viewer-archive/viewer/archive/FluidArchiveFamily.kt`

- `data class FluidArchiveEntry(val path: String, val sizeBytes: Long?)` — A regular file that the archive can safely offer to the app's extraction destination. [path] is relative.
- `data class FluidArchiveProgress(val completedFiles: Int, val totalFiles: Int, val writtenBytes: Long)` — Results written so far by [FluidArchiveExtraction.writeFiles]. Totals exclude links and unsafe entries.
- `class FluidArchiveExtraction internal constructor(val files: List<FluidArchiveEntry>, private val copy: suspend (suspend (FluidArchiveEntry) -> OutputStream) -> Unit)` — The app chooses a destination and owns its I/O policy, collision handling and rollback. Use [writeFiles] once, after choosing the destination, inside the extraction callback. Each output is closed after its file. An error or cancellation can leave partial output in the app's destination. Empty folders are omitted.
  - `suspend fun writeFiles(openOutput: suspend (FluidArchiveEntry) -> OutputStream)`
- `class FluidArchiveFamily @JvmOverloads constructor(private val onExtractAll: (suspend (FluidArchiveExtraction) -> Unit)? = null) : FluidViewerFamily` — Optional archive viewer discovered automatically by [FluidViewerRegistry]. Install a configured instance with `FluidViewerRegistry.install(FluidArchiveFamily(onExtractAll = …))` when the app offers «Estrai tutto». The callback runs on the UI dispatcher, and [FluidArchiveExtraction.writeFiles] copies on the I/O dispatcher.
- `@Composable fun FluidViewerDocument.fluidArchiveViewerInfo(): FluidViewerInfo?` — Use in `FluidViewerInfoProvider` to show the archive facts, sections, and available actions.
- `class ArchivePreviewProvider : FileProvider()` — A dedicated, non-exported FileProvider grants read access only to previews explicitly sent out of the viewer.

## `engine-viewer-archive/viewer/archive/FluidArchiveLicenses.kt`

- `val FluidArchiveLicenses: List<FluidLicense>` — Add to `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` when the app includes the archive family.

## `engine-viewer-data/viewer/data/FluidDataFamily.kt`

- `class FluidDataFamily(val settings: FluidDataSettings = FluidDataSettings()) : FluidViewerFamily` — F6C.13 · La famiglia «data» dei visori: testi ricchi e dati, con una **lettura propria** sopra engine-ui (`docs/liquid-glass/ricerca/visori-2.md` §1.3). Si registra da sola con `META-INF/services`: basta la dipendenza `implementation project(':engine-viewer-data')`, o `engine-viewer-all`.
  - `companion object`
    - `const val Id: String` — L'id della famiglia, nel pannello info e in `FluidViewerType.family`.
    - `const val Markdown: String`
    - `const val Html: String`
    - `const val Csv: String`
    - `const val Tsv: String`
    - `const val Json: String`
    - `const val Ndjson: String`
    - `const val Xml: String`
    - `const val Plist: String`
    - `const val Epub: String`
    - `const val Eml: String`
    - `const val Ics: String`
    - `const val Vcf: String`

## `engine-viewer-data/viewer/data/FluidDataInfo.kt`

- `fun fluidDataViewerInfo(document: FluidViewerDocument, actions: List<FluidViewerInfoAction> = emptyList(), thumbnail: ImageBitmap? = null): FluidViewerInfo` — F6C.13 · Il pannello info (D · Mix, `visori-2.md` §5.2) di un documento aperto dal registro: i [FluidViewerDetail] della famiglia diventano un `FluidViewerInfo`. Le righe senza sezione sono i **fatti in capsule** (il loro valore), le altre le **sezioni**, nell'ordine in cui arrivano; [actions] sono quelle dell'app («Apri con…», «Condividi»).
- `fun fluidDataViewerInfo(file: FluidFileInfo, details: List<FluidViewerDetail>, label: String? = null, actions: List<FluidViewerInfoAction> = emptyList(), thumbnail: ImageBitmap? = null): FluidViewerInfo` — Lo stesso, da un [FluidFileInfo] e da una lista di dettagli (per un'app che li tiene da sé).

## `engine-viewer-data/viewer/data/FluidDataSettings.kt`

- `data class FluidDataSettings(val resources: ((source: FluidViewerSource, path: String) -> InputStream?)? = null, val remoteImages: ((Uri) -> FluidWebResponse)? = null, val openLink: ((String) -> Unit)? = null, val openFile: ((name: String, mimeType: String?, file: File) -> Unit)? = null, val strings: FluidDataStrings = FluidDataStrings())` — F6C.13 · Ciò che un'app può dare alla famiglia «data». Di serie niente: la famiglia trovata con `ServiceLoader` apre ogni file da sola, **senza rete** e senza leggere fuori dal file. Un'app che vuole di più installa un'istanza sua:
- `data class FluidDataStrings(val rendered: String = "Reso", val source: String = "Sorgente", val tree: String = "Albero", val text: String = "Testo", val find: String = "Trova", val showMore: (shown: Int, total: Int) -> String = { shown, total -> "Mostra altri ${minOf(DataPro…, val truncated: (nodes: Int) -> String = { "Il file continua: mostrati i primi ${DataTex…, val invalid: String = "Il file non è valido", val showInText: String = "Mostra nel testo", val loadImages: String = "Carica immagini", val remoteBlocked: (count: Int) -> String = { if (it == 1) "1 immagine remota bloccata" els…, val contents: String = "Indice", val previousChapter: String = "Capitolo precedente", val nextChapter: String = "Capitolo successivo", val chapter: (current: Int, count: Int, percent: Int) -> String = { current, count, percent -> "Capitolo $current…, val attachments: String = "Allegati", val backToMessage: String = "Torna al messaggio", val from: String = "Da", val to: String = "A", val cc: String = "Cc", val date: String = "Data", val noSubject: String = "(senza oggetto)", val addToCalendar: String = "Aggiungi al calendario", val addToContacts: String = "Aggiungi ai contatti", val allDay: String = "Tutto il giorno", val copied: String = "Copiato", val empty: String = "Il file è vuoto", val webFailed: String = "La pagina non si può mostrare qui", val loading: String = "Caricamento", val protectedTitle: String = "Protetto da DRM", val openWith: String = "Apri con…", val share: String = "Condividi")` — Le parole delle pagine della famiglia «data». Come le altre `Fluid*Strings`: un'app tradotta le passa tutte.

## `engine-viewer-graphics/viewer/graphics/FluidGraphicsFamily.kt`

- `val FluidGraphicsViewerLicenses: List<FluidLicense>` — Licenze da aggiungere alla lista di [dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection].
- `class FluidGraphicsFamily : FluidViewerFamily` — Famiglia opt-in delle immagini speciali e dei caratteri, scoperta dal registro dei visori.

## `engine-viewer-office/viewer/office/FluidOfficeFamily.kt`

- `class FluidOfficeFamily : FluidViewerFamily` — Optional offline Office readers. ServiceLoader discovers this class when the application includes the module.

## `engine-viewer-office/viewer/office/OfficeDocument.kt`

- `val FluidOfficeLicenses: List<FluidLicense>` — Pass FluidEngineLicenses + FluidOfficeLicenses to fluidLicensesSection when distributing Office.

## `engine-viewer-office/viewer/office/OfficeViewerInfo.kt`

- `fun FluidViewerDocument.officeInfo(): FluidViewerInfo` — Metadata for FluidViewerInfoProvider. Read it inside composition to follow progressive pagination.

## `engine-viewer/viewer/FluidDecodedImage.kt`

- `class FluidDecodedImage private constructor(val width: Int, val height: Int, val sourceWidth: Int, val sourceHeight: Int, private val bitmap: ImageBitmap?, val animation: Drawable?)` — Un'immagine decodificata dal modulo, ferma o animata, pronta per il preset immagine.
  - `val animated: Boolean get()` — Vero se è un contenuto che si riproduce (una GIF o una WebP animata, decodificata su Android 9+).
  - `val still: ImageBitmap? get()` — La bitmap di un'immagine ferma, `null` se è animata.
  - `val aspectRatio: Float get()` — Il rapporto d'aspetto, per il telaio (limiti del pan, elemento condiviso).
  - `fun content(contentDescription: String? = null): FluidViewerContent.Image` — Il contenuto per il visore: un `FluidViewerContent.Image` col rapporto d'aspetto. Animata, passa dal preset immagine di engine-ui (`fluidAnimatedImage`: parte solo sulla pagina ferma, l'azione TalkBack «Riproduci»); ferma, il disegno di [Body].
  - `@Composable fun Body(contentDescription: String?, modifier: Modifier = Modifier)` — Disegna l'immagine adattata al posto che ha; se è animata la fa partire (salvo il movimento ridotto).
  - `companion object`
    - `const val DefaultMaxPixels: Long` — Il tetto di serie: 12 MP. Una foto di telefono normale si decodifica intera; una da 50 o 200 MP si riduce (per quelle i tasselli sono dell'app: il suo composable va come contenuto `Image`). A occhio, 22 novembre 2026.
    - `suspend fun decode(context: Context, uri: Uri, maxPixels: Long = DefaultMaxPixels, animate: Boolean = true): FluidDecodedImage` — Decodifica da un `Uri` (`content://`, `file://`, `android.resource://`).
    - `suspend fun decode(file: File, maxPixels: Long = DefaultMaxPixels, animate: Boolean = true): FluidDecodedImage` — Decodifica da un file dell'app.

## `engine-viewer/viewer/FluidFileThumbnails.kt`

- `class FluidFileThumbnail internal constructor(val image: ImageBitmap, val fillsFrame: Boolean, val isPlaceholder: Boolean)` — F6C.17 · La miniatura di un file, come la mostra File di iOS: il contenuto in piccolo (le prime righe, la tabella, l'albero, la pagina, la copertina, l'icona dell'app, il modello) oppure, per un tipo che niente legge, l'icona del documento con l'estensione. Si chiede con [FluidFileThumbnails.load].
  - `val contentScale: ContentScale get()` — [ContentScale.Crop] per una figura, [ContentScale.Fit] per un foglio o un simbolo.
- `data class FluidThumbnailLook(val accent: Color, val accentSecondary: Color, val surface: Color, val dark: Boolean) : AbstractCoroutineContextElement(FluidThumbnailLook)` — I colori dell'app per le miniature che ne hanno (la tinta leggera dell'archivio, dei simboli): l'accento, il secondo accento, la superficie e il tema. La carta dei documenti resta bianca in tutti e due i temi.
  - `companion object Key : CoroutineContext.Key<FluidThumbnailLook>`
    - `val Light: FluidThumbnailLook get()` — Il chiaro di serie: systemBlue e systemIndigo di Apple sul grigio di sistema.
    - `val Dark: FluidThumbnailLook get()` — Lo scuro di serie.
    - `suspend fun current(): FluidThumbnailLook` — I colori del contesto, o [Light].
- `@Composable fun rememberFluidThumbnailLook(): FluidThumbnailLook` — I colori del tema Material corrente per le miniature: l'accento, il terziario, la superficie e il tema.
- `object FluidFileThumbnails` — F6C.17 · Le miniature dei file per l'app: **una chiamata, sempre un'immagine**.
  - `const val DefaultTimeoutMillis: Long` — Il tempo massimo di serie: 2,5 s. Office si dà 2 s per disegnare una pagina (F6C.15b) e un file grande deve ancora copiarsi; oltre, l'icona è meglio di una tessera vuota. A occhio, 23 novembre 2026.
  - `const val RetryAfterTimeoutMillis: Long` — Dopo quanto si riprova un file che aveva superato il tempo: 30 s, a occhio, 23 novembre 2026.
  - `const val Parallelism: Int` — Quanti file si leggono insieme: 3, come i nuclei grandi di un telefono di fascia media. A occhio, 23 novembre 2026.
  - `var memoryLimitBytes: Long` — Il tetto della cache in byte. Di serie un ottavo della memoria dell'app, al più 48 MB: una griglia di sessanta tessere da 450 px sta intera. Abbassarlo toglie subito le miniature in più.
  - `val cachedCount: Int get()` — Quante miniature e quanti byte tiene la cache adesso: per i test e per chi misura.
  - `val cachedBytes: Long get()`
  - `fun cached(file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook = FluidThumbnailLook.Light): FluidFileThumbnail?` — La miniatura già pronta, senza leggere niente: per la prima composizione di una tessera. `null` se non c'è.
  - `suspend fun load(context: Context, file: FluidViewerFile, sizePx: Int, look: FluidThumbnailLook = FluidThumbnailLook.Light, timeoutMillis: Long = DefaultTimeoutMillis, options: FluidViewerOptions = FluidViewerOptions()): FluidFileThumbnail` — La miniatura di [file], larga al più [sizePx] sul lato lungo (fra 32 e 1024), con i colori [look]. Dalla cache se c'è; altrimenti letta e disegnata fuori dal filo principale, entro [timeoutMillis]. Non lancia: un file rotto, che manca o che supera il tempo dà l'icona del documento con l'estensione.
  - `fun evict(file: FluidViewerFile)` — Toglie dalla cache le miniature di [file], a ogni misura e con ogni colore.
  - `fun clear()` — Svuota la cache.
- `@Composable fun rememberFluidFileThumbnail(file: FluidViewerFile?, sizePx: Int, look: FluidThumbnailLook = rememberFluidThumbnailLook(), timeoutMillis: Long = FluidFileThumbnails.DefaultTimeoutMillis): FluidFileThumbnail?` — La miniatura di [file] in Compose: la cache alla prima composizione (la tessera che torna dal visore è già piena, e l'apertura dalla miniatura parte dalla stessa immagine), poi [FluidFileThumbnails.load]. `null` finché non c'è.
- `@Composable fun FluidFileThumbnailImage(thumbnail: FluidFileThumbnail, modifier: Modifier = Modifier.fillMaxSize(), contentDescription: String? = null)` — [thumbnail] nella sua tessera: ritagliata se è una figura, intera se è un foglio o un simbolo.

## `engine-viewer/viewer/FluidImageHeader.kt`

- `data class FluidImageHeader(val format: String, val width: Int, val height: Int, val animated: Boolean, val frameCount: Int?, val loopCount: Int?)` — Cosa dicono i primi byte di una GIF o di una WebP: misura, se è animata, quanti fotogrammi e quanti giri.
  - `val aspectRatio: Float? get()` — Il rapporto d'aspetto, `null` se la misura manca.
  - `companion object`
    - `fun read(bytes: ByteArray, length: Int = bytes.size): FluidImageHeader?` — Legge l'intestazione dai primi [length] byte, o `null` se non sono una GIF o una WebP (anche troncate). Non decodifica i pixel: segue i blocchi e conta i fotogrammi. Basta il primo mezzo megabyte per una GIF normale.

## `engine-viewer/viewer/FluidMedia3.kt`

- `class FluidMedia3Session(val player: Player) : FluidMediaSession, FluidMediaDurationLoader, FluidVideoGeometry, AutoCloseable` — Il `Player` di Media3 dell'app come `FluidMediaSession` del visore. Il player resta **dell'app**: lo crea, lo prepara, sceglie il file e lo rilascia lei; la sessione lo legge e gli passa play, pausa e salto.
  - `var buffering: Boolean by mutableStateOf(false)` — Vero mentre il player carica (`STATE_BUFFERING`).
  - `var ended: Boolean by mutableStateOf(false)` — Vero quando il file è finito (`STATE_ENDED`): [play] riparte da capo.
  - `fun attach()` — Comincia ad ascoltare il player. Si chiama una volta; [close] smette.
  - `fun refresh()` — Rilegge tutto dal player. La chiamano gli eventi; serve a mano solo a chi non usa [attach].
  - `suspend fun followPosition()` — Aggiorna la posizione a ogni fotogramma mentre suona, e si ferma quando smette. Non torna mai: si annulla.
- `@Composable fun rememberFluidMedia3Session(player: Player): FluidMedia3Session` — La sessione di [player] per questa composizione: ascolta il player finché resta, e tiene la posizione al passo coi fotogrammi mentre suona. Il player non si rilascia qui: è dell'app.
- `@Composable fun FluidMedia3Video(session: FluidMedia3Session, modifier: Modifier = Modifier, surfaceView: Boolean = false)` — La superficie video di [session], centrata e col suo rapporto d'aspetto.
- `fun fluidMedia3Content(session: FluidMedia3Session, video: Boolean = true, surfaceView: Boolean = false, artwork: (@Composable () -> Unit)? = null): FluidViewerContent.Media` — Il contenuto del visore per un file che [session] riproduce: un video con la superficie ([video] vero, di serie) o un audio con la sua copertina ([artwork]). È il `FluidViewerContent.Media` che i preset video e audio leggono.

## `engine-viewer/viewer/FluidPdfPageSource.kt`

- `class FluidPdfPageSource private constructor(private val queue: FluidPageRenderQueue<ImageBitmap>) : FluidPageSource, Closeable` — Un PDF per il preset delle pagine, con il `PdfRenderer` della piattaforma (`androidx.pdf` è in alfa e non si usa).
  - `suspend fun preview(index: Int): ImageBitmap` — L'anteprima della pagina [index], a lato lungo 256 px: quella da mostrare mentre si scorre.
  - `companion object`
    - `const val DefaultMaxCopyBytes: Long` — Il tetto della copia di un PDF che arriva da un `Uri` non ricercabile (un flusso, una pipe): `PdfRenderer` vuole un descrittore su cui spostarsi, quindi il file si copia nella cache dell'app. 512 MB, a occhio, 22 novembre 2026: un PDF più grande da un flusso è un caso da «Apri con…».
    - `suspend fun open(descriptor: ParcelFileDescriptor): FluidPdfPageSource` — Apre un PDF da un descrittore, che passa al sorgente (lo chiude lui, anche se l'apertura fallisce). Il descrittore deve essere ricercabile: per un `Uri` qualunque usare l'altro [open], che copia quando serve.
    - `suspend fun open(file: File): FluidPdfPageSource` — Apre un PDF da un file dell'app.
    - `suspend fun open(context: Context, uri: Uri, maxCopyBytes: Long = DefaultMaxCopyBytes): FluidPdfPageSource` — Apre un PDF da un `Uri` (`content://` o `file://`). Se il descrittore non è ricercabile copia il file nella cache dell'app, al più [maxCopyBytes], e cancella la copia alla chiusura. La copia si interrompe se chi chiama è annullato, e non lascia file a metà.

## `engine-viewer/viewer/FluidSyntax.kt`

- `value class FluidSyntaxRole private constructor(private val id: Int)` — Il ruolo di un pezzo di codice, da cui il preset testo sceglie il colore col tema ([fluidSyntaxAnnotated]).
  - `companion object`
    - `val Keyword: FluidSyntaxRole` — Parole riservate, letterali come `true`, `null`; nel Markdown i segni degli elenchi.
    - `val Type: FluidSyntaxRole` — Nomi di tipo (per convenzione con la maiuscola), le ancore e i tag di YAML.
    - `val String: FluidSyntaxRole` — Stringhe e caratteri, i valori fra virgolette.
    - `val Number: FluidSyntaxRole` — Numeri.
    - `val Comment: FluidSyntaxRole` — Commenti; nel Markdown le citazioni.
    - `val Tag: FluidSyntaxRole` — I tag di XML e HTML, le sezioni `[…]` di INI e TOML.
    - `val Attribute: FluidSyntaxRole` — Attributi, chiavi di JSON e YAML, annotazioni (`@Composable`), direttive (`#include`).
    - `val Heading: FluidSyntaxRole` — I titoli del Markdown.
    - `val Emphasis: FluidSyntaxRole` — Corsivo e grassetto del Markdown.
    - `val Link: FluidSyntaxRole` — I collegamenti del Markdown.
    - `val Code: FluidSyntaxRole` — Il codice dentro il Markdown (in linea e nei blocchi).
- `data class FluidSyntaxSpan(val start: Int, val end: Int, val role: FluidSyntaxRole)` — Un pezzo colorato di una riga, da [start] incluso a [end] escluso, in unità UTF-16 come `String`.
- `data class FluidSyntaxLine(val spans: List<FluidSyntaxSpan>, val endState: Int)` — I pezzi di una riga e lo stato con cui comincia la successiva (un commento a blocco aperto, una stringa lunga).
- `class FluidSyntaxHighlighter internal constructor(val language: String, private val lexer: Lexer)` — Colora una riga alla volta. Lo stato è un intero opaco: `0` è l'inizio del file, e lo stato con cui comincia una riga è l'[FluidSyntaxLine.endState] della precedente. Così un file letto a pezzi si colora a pezzi, e chi tiene gli stati all'inizio dei pezzi riparte da metà file senza ricolorare tutto.
  - `fun line(text: String, state: Int = 0): FluidSyntaxLine` — I pezzi colorati di [text], partendo da [state]. Una riga senza niente da colorare dà una lista vuota.
- `object FluidSyntax` — I colori di sintassi per poche lingue, senza librerie: lessici scritti qui, una riga alla volta, che riconoscono parole riservate, stringhe, numeri, commenti (anche a blocco su più righe), tag, attributi e i segni del Markdown. Non sono parser: un costrutto che non riconoscono resta del colore del testo, mai un errore.
  - `const val Kotlin`
  - `const val Java`
  - `const val Groovy`
  - `const val JavaScript`
  - `const val TypeScript`
  - `const val C`
  - `const val Cpp`
  - `const val CSharp`
  - `const val Swift`
  - `const val Go`
  - `const val Rust`
  - `const val Dart`
  - `const val Python`
  - `const val Json`
  - `const val Xml`
  - `const val Html`
  - `const val Markdown`
  - `const val Yaml`
  - `const val Ini`
  - `val languages: Set<String>` — Le lingue che sa colorare.
  - `fun languageFor(fileName: String, mimeType: String? = null): String?` — La lingua di un file dal nome (l'ultima estensione) e, se il nome tace, dal MIME; `null` se non la conosce.
  - `fun guessLanguage(sample: String): String?` — La lingua dal contenuto, per un testo senza nome utile: un oggetto o una lista JSON, un documento XML o HTML, uno script Python con lo shebang. Prudente: nel dubbio `null`.
  - `fun highlighter(language: String?): FluidSyntaxHighlighter?` — Il colorante di una lingua, o `null` se la lingua è `null` o sconosciuta.
  - `fun highlight(lines: List<String>, language: String?): List<List<FluidSyntaxSpan>>` — Colora righe consecutive di un file, dalla prima. Per un file letto a pezzi usare [highlighter] e gli stati.

## `engine-viewer/viewer/FluidSyntaxAnnotations.kt`

- `const val FluidSyntaxAnnotation: String` — Il tag delle annotazioni di sintassi: lo stesso che legge il preset testo di engine-ui (F6C.2, `FluidTextPresetDefaults.SyntaxAnnotation`). Il valore di ogni annotazione è il ruolo in minuscolo ([FluidSyntaxRole.annotation]: `keyword`, `type`, `string`, `number`, `comment`, `tag`, `attribute`, `heading`, `emphasis`, `link`, `code`).
- `val FluidSyntaxRole.annotation: String get()` — Il valore dell'annotazione di questo ruolo: il nome in minuscolo.
- `fun fluidSyntaxAnnotated(line: String, spans: List<FluidSyntaxSpan>): AnnotatedString` — [line] con un'annotazione [FluidSyntaxAnnotation] per ogni pezzo. Un pezzo fuori dalla riga si ignora.

## `engine-viewer/viewer/FluidTextDecoder.kt`

- `data class FluidTextCharset(val charset: Charset, val bomLength: Int, val certain: Boolean)` — Il charset con cui leggere un testo, e da dove viene.
- `object FluidTextDecoder` — Il riconoscimento del charset e la lettura di un testo con un tetto, in Kotlin puro (si prova sulla JVM).
  - `const val SampleBytes: Int` — Quanti byte guarda la stima: abbastanza per un sorgente intero, pochi per non leggere un file due volte.
  - `val Fallback: Charset by lazy` — Il ripiego per i byte che non sono UTF valido: windows-1252, o Latin-1 dove la piattaforma non lo avesse.
  - `fun detect(sample: ByteArray, length: Int = sample.size, declared: String? = null, complete: Boolean = true): FluidTextCharset` — Il charset dei primi [length] byte di [sample]. [declared] è il `charset=` del MIME (anche il MIME intero: `text/plain; charset=ISO-8859-1`). [complete] dice se [sample] è il file intero: se non lo è, una sequenza UTF-8 tagliata in fondo non conta come errore.
  - `fun looksBinary(sample: ByteArray, length: Int, charset: FluidTextCharset): Boolean` — Vero se i byte non sono un testo nel [charset] dato: un NUL dove il charset non lo prevede (in UTF-8 e nei charset a un byte), o più di un carattere di controllo su dieci (esclusi tab, a capo, ritorno, salto pagina ed escape). È la prova che separa un testo da un binario prima di mostrarlo.
  - `fun read(input: InputStream, fileName: String = "", mimeType: String? = null, totalBytes: Long? = null, limitBytes: Int = FluidTextDocument.DefaultLimitBytes, maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars, language: String? = FluidSyntax.languageFor(fileName, mimeType)): FluidTextDocument?` — Legge un testo con un tetto: al più [limitBytes] byte di [input], poi il charset, i caratteri e le righe. Restituisce `null` se i byte non sono testo ([looksBinary]). Non chiude [input].
  - `fun decode(bytes: ByteArray, length: Int = bytes.size, truncated: Boolean = false, fileName: String = "", mimeType: String? = null, totalBytes: Long? = null, limitBytes: Int = FluidTextDocument.DefaultLimitBytes, maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars, language: String? = FluidSyntax.languageFor(fileName, mimeType)): FluidTextDocument?` — Come [read], su byte già letti; [truncated] dice se il file continua oltre [length].
- `class FluidTextDocument internal constructor(val lines: List<String>, override val language: String?, val charset: FluidTextCharset, val truncated: Boolean, val bytesRead: Long, val totalBytes: Long?, val malformed: Boolean, val splitLines: Int, val limitBytes: Int) : dev.antigravity.fluidengine.ui.fluid.viewer.FluidStyledTextSource` — Un file di testo letto, pronto per il preset testo: tutte le righe in memoria (il tetto le tiene poche), la lingua per i colori, il charset e cosa è successo leggendo. Si crea con [FluidTextDecoder.read] o [fluidReadTextFile]; si passa al visore come `FluidViewerContent.Text(document)`.
  - `fun syntaxSpans(start: Int, count: Int): List<List<FluidSyntaxSpan>>` — I pezzi colorati delle righe da [start] per [count], o liste vuote se la lingua è assente o sconosciuta. Colora in avanti dalla prima riga non ancora vista e ricorda gli stati, così un pezzo a metà file costa solo le righe nuove.
  - `companion object`
    - `const val DefaultLimitBytes: Int` — Il tetto di serie: 4 MiB. Un sorgente o un registro normale ci sta intero; uno di centinaia di MB si apre subito e dice di essere troncato. In memoria sono al più circa 8 MB di caratteri. A occhio, 22 novembre 2026 (Universal Converter usava 256 KiB, che tagliava già un registro di un giorno).
    - `const val DefaultMaxLineChars: Int` — La riga più lunga che il preset riceve intera: oltre si spezza. A occhio, 22 novembre 2026.

## `engine-viewer/viewer/FluidThumbnailPainter.kt`

- `object FluidThumbnailPainter` — F6C.17 · Gli attrezzi con cui engine-viewer e i moduli di famiglia disegnano le miniature, nella grammatica di File e Quick Look di iOS 26 (`docs/liquid-glass/ricerca/visori-2.md` §2 e §3): - **il foglio** ([page]): la miniatura di un documento è la sua prima pagina, su carta bianca in chiaro e in scuro, come Quick Look. Testo, codice, Markdown, tabelle, alberi e mail si disegnano qui sopra;
  - `const val PageAspect: Float` — Il foglio dei documenti: 3:4, verticale, come le miniature di File per testo e codice. A occhio, 23 novembre 2026 (fiducia bassa sulle proporzioni esatte di Apple, che dipendono dal formato della pagina).
  - `const val TableAspect: Float` — Il foglio delle tabelle (CSV, TSV): 4:3, orizzontale, perché si legga «un pezzo di tabella». A occhio, 23 novembre 2026.
  - `const val MinSizePx: Int` — Il lato lungo più piccolo e più grande di una miniatura, in pixel: oltre, un'anteprima costa più di quanto mostra.
  - `const val MaxSizePx: Int`
  - `const val Paper: Int` — La carta: bianca anche nel tema scuro, come Quick Look (§2, «per i documenti è bianco»).
  - `const val Ink: Int` — L'inchiostro sulla carta, e quello attenuato (date, didascalie, intestazioni secondarie). Dai grigi di sistema di Apple.
  - `const val InkSecondary: Int`
  - `const val Rule: Int` — Le righe sottili (la griglia di una tabella, il bordo dell'icona del documento) e il fondo delle intestazioni.
  - `const val Fill: Int`
  - `const val Link: Int` — Il blu dei collegamenti sulla carta (systemBlue di Apple, chiaro).
  - `const val PageMargin: Float` — Il margine del foglio, sul lato corto: 8%, a occhio, 23 novembre 2026.
  - `const val CodeLinesPerPage: Int` — Quante righe di testo stanno nell'altezza di un foglio: 17 per il codice, 15 per la prosa. Abbastanza da «leggere le prime righe» su un tablet e da vedere la forma del testo su un telefono. A occhio, 23 novembre 2026.
  - `const val ProseLinesPerPage: Int`
  - `fun clampSize(sizePx: Int): Int` — Il lato lungo di [sizePx] riportato fra [MinSizePx] e [MaxSizePx].
  - `fun page(sizePx: Int, aspect: Float = PageAspect, clip: Boolean = true, draw: (canvas: Canvas, content: RectF, unit: Float) -> Unit): ImageBitmap` — Un foglio bianco di lato lungo [sizePx] e proporzioni [aspect] (larghezza su altezza). [draw] riceve la tela, il rettangolo dentro i margini e l'unità del testo (l'altezza di una riga di prosa). Con [clip] la tela è tagliata ai margini (il testo lungo non esce); senza, si può disegnare a vivo (una copertina).
  - `fun symbol(sizePx: Int, placeholder: Boolean = false, draw: (canvas: Canvas, size: Float) -> Unit): ImageBitmap` — Una tela quadrata trasparente di lato [sizePx] per un simbolo. [placeholder] vero: non mostra il contenuto.
  - `fun picture(bitmap: Bitmap): ImageBitmap` — [bitmap] come figura che riempie la tessera (una foto, un fotogramma, un modello disegnato).
  - `fun paint(textSize: Float, color: Int = Ink, monospace: Boolean = false, bold: Boolean = false): TextPaint` — Un pennello per il testo sulla carta.
  - `fun layout(text: CharSequence, paint: TextPaint, width: Int, wrap: Boolean = true, spacing: Float = 1.18f): StaticLayout` — Il testo [text], con i suoi span (colori, grassetto, dimensioni), largo [width]. Con [wrap] falso ogni riga resta intera e il foglio la taglia, come il codice nel visore.
  - `fun draw(canvas: Canvas, layout: StaticLayout, x: Float, y: Float): Float` — Disegna [layout] con l'angolo in alto a sinistra in ([x], [y]); dà la y sotto l'ultima riga.
  - `fun ellipsize(text: CharSequence, paint: TextPaint, width: Float): CharSequence` — [text] tagliato con «…» per stare in [width].
  - `fun syntaxColor(role: FluidSyntaxRole): Int` — Il colore di un ruolo di sintassi sulla carta: i colori di Xcode in chiaro, gli stessi della tavola D (parole chiave viola, tipi blu, stringhe rosse, numeri blu scuro, commenti grigi). Fissi, perché la carta è bianca in tutti e due i temi. A occhio, 23 novembre 2026.
  - `fun syntaxText(lines: List<String>, highlighter: FluidSyntaxHighlighter?): CharSequence` — Le righe [lines] colorate con [highlighter] (o in un colore solo, senza), una per riga, pronte per [layout].
  - `fun codePage(lines: List<String>, highlighter: FluidSyntaxHighlighter?, sizePx: Int): ImageBitmap` — Un foglio con le prime righe di codice, coi colori: carattere a spaziatura fissa, senza a capo, [CodeLinesPerPage] righe nell'altezza.
  - `fun linesPage(text: CharSequence, sizePx: Int, monospace: Boolean = true, linesPerPage: Int = CodeLinesPerPage, aspect: Float = PageAspect): ImageBitmap` — Un foglio di righe che non vanno a capo ([text], una per `\n`, coi suoi span): [linesPerPage] righe nell'altezza, a spaziatura fissa se [monospace]. Serve al codice, agli alberi di dati e ai testi strutturati.
  - `fun prosePage(text: CharSequence, sizePx: Int, aspect: Float = PageAspect): ImageBitmap` — Un foglio di prosa: [text] (anche con i suoi span) va a capo nella colonna, [ProseLinesPerPage] righe nell'altezza.
  - `fun documentIcon(extension: String, sizePx: Int): ImageBitmap` — L'icona di File per un tipo che non mostra il contenuto: un foglio bianco con l'angolo piegato, il bordo grigio e [extension] in maiuscolo sotto il centro. Uguale nei due temi, come in iOS. È un segnaposto: dice il tipo, non il contenuto. Le misure (foglio alto il 62% del lato, piega del 26%) sono a occhio, 23 novembre 2026.
  - `fun kindSymbol(glyph: ImageVector, label: String?, sizePx: Int, look: FluidThumbnailLook = FluidThumbnailLook.Light, placeholder: Boolean = false): ImageBitmap` — Il simbolo di un tipo nello stile della pagina «D · Mix»: una tessera arrotondata con la tinta leggera del file (la stessa sfumatura della pagina dell'archivio, più piena perché la tessera è piccola: [FluidThumbnailLook.accent] al 30% in alto, al 10% al centro, [FluidThumbnailLook.accentSecondary] al 30% in basso, sulla superficie; a occhio, 23 novembre 2026), il glifo nell'accento e, sotto, [label] in una capsula.
  - `fun drawGlyph(canvas: Canvas, vector: ImageVector, bounds: RectF, color: Int)` — Disegna l'icona [vector] (un `ImageVector` di `FluidIcons`) dentro [bounds], nel colore [color].
  - `fun blend(a: Int, b: Int, t: Float): Int` — [a] verso [b] di [t] (0–1), alfa compresa.

## `engine-viewer/viewer/FluidViewerException.kt`

- `value class FluidViewerFailure private constructor(private val id: Int)` — Perché un file non si è aperto. Non è un `enum` di proposito (regola 3.11): un motivo nuovo è una costante in più. Ogni motivo porta allo stesso ripiego, lo sconosciuto con «Apri con…»: il motivo serve al testo che lo spiega.
  - `companion object`
    - `val Unreadable: FluidViewerFailure` — Il file non si legge: sparito, permesso tolto, un `Uri` che non apre niente.
    - `val Corrupt: FluidViewerFailure` — I byte non sono quello che il tipo promette: un PDF rotto, un'immagine troncata.
    - `val Protected: FluidViewerFailure` — Un PDF con la password: il `PdfRenderer` della piattaforma non lo apre.
    - `val TooLarge: FluidViewerFailure` — Oltre il tetto di copia (un PDF da un `Uri` non ricercabile più grande del tetto).
    - `val Unsupported: FluidViewerFailure` — La piattaforma non sa decodificare questo tipo (un HEIF sotto Android 9, un formato sconosciuto).
- `class FluidViewerException(val failure: FluidViewerFailure, message: String, cause: Throwable? = null) : IOException(message, cause)` — Un file che il modulo non ha potuto aprire, con il motivo.

## `engine-viewer/viewer/FluidViewerFamily.kt`

- `interface FluidViewerFamily` — F6C.8 · Una famiglia di tipi in un modulo facoltativo (`engine-viewer-office`, `-archive`, `-data`, `-graphics`, `-3d`). **Si registra da sola**: il modulo la dichiara in `META-INF/services/dev.antigravity.fluidengine.viewer.FluidViewerFamily` e [FluidViewerRegistry] la trova con `ServiceLoader` la prima volta che risolve. Basta la dipendenza Gradle;
  - `val id: String` — «office», «archive», «data», «graphics», «3d»: unico; compare nel pannello info (`FluidViewerType.family`).
  - `val types: List<FluidViewerFamilyType>` — I tipi che sa aprire, ognuno con le sue regole di riconoscimento.
  - `suspend fun open(request: FluidViewerRequest): FluidViewerOpened?` — Apre il file di [FluidViewerRequest.source] come [FluidViewerRequest.type]. `null`, oppure una [FluidViewerException], vuol dire «non questo»: il registro prova il candidato dopo, e tiene la prima eccezione come motivo. Un'altra eccezione vale come un file rotto ([FluidViewerFailure.Corrupt]). La sorgente non si chiude qui: è del documento, che la chiude con [FluidViewerOpened.close].
  - `suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap?` — La miniatura per la griglia dell'app e per l'apertura (F6C.10), larga al più [sizePx]. `null`: l'icona del tipo.
- `data class FluidViewerFamilyType(val id: String, val label: String, val extensions: Set<String> = emptySet(), val mimeTypes: Set<String> = emptySet(), val signature: FluidViewerSignature? = null, val kind: FluidFileKind = FluidFileKind.Document, val priority: Int = 0, val preset: FluidViewerPreset = FluidViewerPreset.Custom)` — Un tipo di una [FluidViewerFamily] e come si riconosce. Il registro sceglie fra i candidati con quest'ordine: **la firma** ([signature], dai primi byte o da una voce del pacchetto ZIP) vale più dell'**estensione** ([extensions], senza il punto), che vale più del **MIME** ([mimeTypes]; «image/» seguito dall'asterisco vale per il gruppo);
- `class FluidViewerSignature private constructor(private val offset: Int, private val bytes: ByteArray?, private val entries: List<String>, private val alternatives: List<FluidViewerSignature>)` — Come si riconosce un tipo dai suoi byte. Due specie: - **i primi byte** ([bytes], [ascii]) a una posizione, entro i primi [HeaderBytes]; - **una voce del pacchetto** ([zipEntry]): il file è uno ZIP e il suo indice ha quella voce. Vale più dei primi byte, perché dice cosa c'è dentro: un DOCX è anche uno ZIP, e vince chi lo riconosce come Word.
  - `companion object`
    - `const val HeaderBytes: Int` — Quanti byte di testa legge il registro: le firme stanno qui dentro (il TAR, a 257, ci sta).
    - `fun bytes(vararg bytes: Int, offset: Int = 0): FluidViewerSignature` — I byte [bytes] (0–255) a partire da [offset].
    - `fun ascii(text: String, offset: Int = 0): FluidViewerSignature` — Il testo [text] in ASCII a partire da [offset] («%PDF-», «glTF», «8BPS»).
    - `fun zipEntry(vararg names: String): FluidViewerSignature` — Uno ZIP con almeno una di queste voci; un nome che finisce con «/» vale per ogni voce sotto quella cartella.
    - `fun anyOf(vararg signatures: FluidViewerSignature): FluidViewerSignature` — Una qualunque di queste: vale la più forte che si riconosce.
- `class FluidViewerRequest internal constructor(val context: Context, val source: FluidViewerSource, val type: FluidViewerFamilyType, val options: FluidViewerOptions, val openWith: (() -> Unit)?, val share: (() -> Unit)?, val media: ((FluidViewerType) -> FluidViewerContent?)?)` — Quello che una famiglia riceve per aprire un file: la [source] (che resta del documento), il [type] riconosciuto, i tetti dell'app in [options] e il suo [context] (l'applicazione). [openWith], [share] e [media] sono quelli che l'app ha dato al registro: una famiglia che apre un file dentro il suo (la voce di un archivio, un allegato) li passa a [FluidViewerRegistry.open] insieme a una nuova [FluidViewerSource].
- `class FluidViewerOpened(val content: FluidViewerContent, val details: List<FluidViewerDetail> = emptyList(), private val onClose: () -> Unit = {}) : Closeable` — Un file aperto da una famiglia: il [content] per il visore (`Pages`, `Grid`, `Text`, `Image`, `Custom`…), i [details] per il pannello info («Pagine 9», «Calibri → Carlito») e la chiusura ([onClose]), che il documento chiama una volta sola quando la pagina esce.
- `data class FluidViewerDetail(val label: String, val value: String, val section: String? = null)` — Una riga del pannello info: «Pagine» → «9». [section] le raggruppa («Documento», «Caratteri»), `null` in testa.

## `engine-viewer/viewer/FluidViewerFiles.kt`

- `data class FluidViewerFile(val uri: Uri, val name: String, val mimeType: String? = null, val sizeBytes: Long? = null)` — Un file da mostrare: dove sta e cosa ne sa l'app. [sizeBytes] `null` è ignoto, zero è vuoto.
  - `val info: FluidFileInfo get()` — Le informazioni per lo sconosciuto e per il pannello: nome, MIME, dimensione e il tipo di `FluidFileKind`.
  - `val type: FluidViewerType get()` — Il tipo per il visore, da MIME e nome (senza leggere il file).
- `data class FluidViewerOptions(val textLimitBytes: Int = FluidTextDocument.DefaultLimitBytes, val textMaxLineChars: Int = FluidTextDocument.DefaultMaxLineChars, val imageMaxPixels: Long = FluidDecodedImage.DefaultMaxPixels, val animateImages: Boolean = true, val pdfMaxCopyBytes: Long = FluidPdfPageSource.DefaultMaxCopyBytes, val sourceMaxCopyBytes: Long = FluidViewerSource.DefaultMaxCopyBytes)` — I tetti con cui il modulo apre un file. I valori di serie sono quelli dei singoli decodificatori.
- `class FluidViewerDocument internal constructor(val file: FluidViewerFile, val type: FluidViewerType, val content: FluidViewerContent, val failure: FluidViewerException?, private val resource: Closeable?, val details: List<FluidViewerDetail> = emptyList()) : Closeable` — Un file aperto dal modulo: il contenuto per il visore, il tipo, e se qualcosa è andato storto il motivo. Va chiuso quando la pagina esce ([close]): libera il PDF e le sue copie. Il ripiego è sempre lo sconosciuto con «Apri con…» e «Condividi» dell'app, mai un vicolo cieco.
- `suspend fun fluidReadTextFile(context: Context, uri: Uri, fileName: String, mimeType: String? = null, sizeBytes: Long? = null, limitBytes: Int = FluidTextDocument.DefaultLimitBytes, maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars): FluidTextDocument?` — Legge un file di testo da un `Uri`, con [FluidTextDecoder.read]; `null` se i byte non sono testo.
- `suspend fun FluidViewerFile.open(context: Context, options: FluidViewerOptions = FluidViewerOptions(), openWith: (() -> Unit)? = null, share: (() -> Unit)? = null, media: ((FluidViewerType) -> FluidViewerContent?)? = null): FluidViewerDocument` — Apre [FluidViewerFile] col decodificatore del suo tipo: l'immagine (anche animata), il PDF, il testo. Un video o un audio ha bisogno del player dell'app: [media] lo trasforma in contenuto (per esempio con [fluidMedia3Content]), e senza [media] il file va allo sconosciuto.
- `class FluidViewerFiles internal constructor(private val context: Context, private val scope: CoroutineScope)` — I file di un visore, aperti col modulo attorno alla pagina corrente: [content] dà il contenuto della pagina, e la prima volta comincia ad aprirla. Tiene aperti i file entro [window] pagine dall'ultima chiesta e chiude gli altri (un PDF lontano non tiene il suo renderer). Si crea con [rememberFluidViewerFiles]:
  - `val count: Int get()` — Quanti file.
  - `fun document(index: Int): FluidViewerDocument?` — Il file aperto alla pagina [index], `null` finché si apre.
  - `fun content(index: Int): FluidViewerContent` — Il contenuto della pagina [index]. La prima volta comincia ad aprire il file e chiude quelli lontani.
- `@Composable fun rememberFluidViewerFiles(files: List<FluidViewerFile>, options: FluidViewerOptions = FluidViewerOptions(), window: Int = 1, openWith: ((FluidViewerFile) -> Unit)? = null, share: ((FluidViewerFile) -> Unit)? = null, media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null): FluidViewerFiles` — I file di [files] per il visore, aperti col modulo attorno alla pagina corrente e chiusi quando la composizione esce. [media] dà il contenuto di un video o di un audio col player dell'app (per esempio `{ _, type -> fluidMedia3Content(session, video = type.preset == FluidViewerPreset.Video) }`); senza, i video vanno allo sconosciuto. [openWith] e [share] diventano «Apri con…» e «Condividi» dello sconosciuto.

## `engine-viewer/viewer/FluidViewerRegistry.kt`

- `object FluidViewerRegistry` — F6C.8 · Il registro dei moduli dei visori. Un modulo di famiglia aggiunge i suoi tipi al risolutore **senza toccarlo**: basta che dichiari la sua [FluidViewerFamily] in `META-INF/services`, e il registro la trova con `ServiceLoader` la prima volta che serve. Niente App Startup, niente codice nell'`Application`.
  - `val families: List<FluidViewerFamily>` — Le famiglie trovate con `ServiceLoader` (`META-INF/services`), più quelle installate a mano con [install], meno quelle tolte con [uninstall]. Una famiglia installata con l'`id` di una trovata la sostituisce.
  - `fun install(vararg family: FluidViewerFamily)` — Aggiunge famiglie a mano: per i test, e per chi vuole un'istanza configurata al posto di quella trovata.
  - `fun uninstall(vararg id: String)` — Toglie le famiglie con questi `id`, trovate o installate: il loro tipo torna allo sconosciuto.
  - `suspend fun open(context: Context, source: FluidViewerSource, options: FluidViewerOptions = FluidViewerOptions(), openWith: (() -> Unit)? = null, share: (() -> Unit)? = null, media: ((FluidViewerType) -> FluidViewerContent?)? = null): FluidViewerDocument` — Apre [source] con il risolutore: un tipo di famiglia se un modulo lo conosce, altrimenti i preset di serie, e lo sconosciuto con «Apri con…» se niente lo apre. Serve anche ai moduli per i file dentro i file (la voce di un archivio, l'allegato di una mail): un PDF dentro uno ZIP usa il preset PDF, un DOCX allegato usa Office se c'è. Il documento **possiede** la sorgente e la chiude con sé;
  - `suspend fun thumbnail(context: Context, source: FluidViewerSource, sizePx: Int, options: FluidViewerOptions = FluidViewerOptions()): ImageBitmap?` — La miniatura del contenuto di [source], larga al più [sizePx]: quella della famiglia che lo apre, nell'ordine dei candidati, e (F6C.17) quelle di engine-viewer di serie, cioè le prime righe del testo e del codice coi colori e l'icona di un APK. `null` se niente ne dà una: per le foto e i PDF la miniatura è dell'app, e un tipo che niente legge non ha contenuto da mostrare. Non chiude [source].

## `engine-viewer/viewer/FluidViewerSource.kt`

- `interface FluidViewerSource : Closeable` — Il file da aprire con il registro (F6C.8): un `Uri` dell'app, un file suo, una voce di un archivio, un allegato. Si legge sempre dall'inizio con [open]; chi ha bisogno di spostarsi (uno ZIP, il `PdfRenderer`) chiede [seekable].
  - `val name: String` — Il nome con l'estensione vera, come lo vede chi usa l'app.
  - `val mimeType: String? get()`
  - `val sizeBytes: Long? get()` — `null` è ignoto, zero è vuoto.
  - `suspend fun open(): InputStream`
  - `suspend fun seekable(): ParcelFileDescriptor`
  - `companion object`
    - `const val DefaultMaxCopyBytes: Long` — Il tetto della copia di una sorgente che non si può spostare: 512 MB come il PDF di F6C.4, a occhio, 22 novembre 2026. Più grande, `FluidViewerFailure.TooLarge`: è un caso da «Apri con…».
    - `fun of(context: Context, uri: Uri, name: String, mimeType: String? = null, sizeBytes: Long? = null, maxCopyBytes: Long = DefaultMaxCopyBytes): FluidViewerSource` — Un `Uri` (`content://`, `file://`, `android.resource://`). [seekable] apre il descrittore del provider e, se è una pipe o un flusso, copia il file nella cache dell'app (`cacheDir/fluid-viewer`), al più [maxCopyBytes].
    - `fun of(file: File, name: String = file.name, mimeType: String? = null, deleteOnClose: Boolean = false): FluidViewerSource` — Un file dell'app. Con [deleteOnClose] il file si cancella alla chiusura (una voce estratta in cache).
    - `fun of(name: String, cacheDir: File, mimeType: String? = null, sizeBytes: Long? = null, maxCopyBytes: Long = DefaultMaxCopyBytes, read: suspend () -> InputStream): FluidViewerSource` — Un flusso che si può solo leggere dall'inizio (una voce di un archivio, un allegato decodificato): [open] lo chiede di nuovo a [read] ogni volta, [seekable] lo copia una volta in [cacheDir], al più [maxCopyBytes].

## `engine-viewer/viewer/FluidViewerType.kt`

- `value class FluidViewerPreset private constructor(private val id: Int)` — Quale preset del visore mostra un file: [Image], [Video], [Audio], [Pages], [Text], [Grid], [Custom] o [Unknown].
  - `companion object`
    - `val Image: FluidViewerPreset` — Una foto, anche animata (GIF, WebP): `FluidViewerContent.Image`.
    - `val Video: FluidViewerPreset` — Un video: `FluidViewerContent.Media` con la superficie. Il player è dell'app.
    - `val Audio: FluidViewerPreset` — Un audio: `FluidViewerContent.Media` senza superficie.
    - `val Pages: FluidViewerPreset` — Un documento a pagine (PDF): `FluidViewerContent.Pages`.
    - `val Text: FluidViewerPreset` — Testo e codice: `FluidViewerContent.Text`.
    - `val Unknown: FluidViewerPreset` — Tutto il resto, e ogni eseguibile: `FluidViewerContent.Unknown`, con «Apri con…». Mai un vicolo cieco.
    - `val Grid: FluidViewerPreset` — F6C.8 · Un foglio di calcolo o una tabella (XLSX, ODS, CSV): `FluidViewerContent.Grid`. Lo danno le famiglie.
    - `val Custom: FluidViewerPreset` — F6C.8 · Una pagina disegnata dal modulo (archivio, albero JSON, EPUB, font, 3D): `FluidViewerContent.Custom`.
- `data class FluidViewerType(val preset: FluidViewerPreset, val kind: FluidFileKind, val mimeType: String?, val language: String?, val family: String? = null, val label: String? = null, val familyType: String? = null)` — Il tipo di un file per il visore, deciso da MIME, nome e, solo quando i due tacciono, dai primi byte.
  - `val executable: Boolean get()` — Vero se il file è un eseguibile: il preset è sempre [FluidViewerPreset.Unknown] e mostra l'avviso.
  - `companion object`
    - `fun of(mimeType: String?, fileName: String, header: ByteArray? = null): FluidViewerType` — Risolve un file. Precedenza, come in `FluidFileKind.from`: l'eseguibile vince sempre; poi l'estensione (l'ultima: `archivio.tar.gz` è `gz`), poi il MIME, poi la firma dei primi byte ([header], se c'è). Un MIME con parametri o in maiuscolo (`Text/Plain; charset=UTF-8`) vale come quello pulito; un MIME generico o malformato vale come nessun MIME. Un MIME in conflitto con l'estensione perde, salvo che sia eseguibile.
    - `fun sniff(header: ByteArray): FluidViewerPreset?` — Il preset dalla firma dei primi byte (almeno 12 servono a WebP e ai contenitori ISO), o `null` se non la riconosce. Serve al ripiego: un file senza estensione e senza MIME, o un `.txt` che in realtà è un PNG. Non sa riconoscere il testo (lo decide [FluidTextDecoder.looksBinary] sui byte veri).
