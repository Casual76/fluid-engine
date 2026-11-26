# API · engine-scan e engine-scan-core

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-scan-core/scan/FluidScanEdges.kt`

- `class FluidScanFrame(val width: Int, val height: Int, val luma: ByteArray, val stride: Int = width, val rotationDegrees: Int = 0, val timestampMillis: Long = 0L)` — Un fotogramma in toni di grigio: [width] × [height] byte di luminanza (0..255), una riga ogni [stride] byte. Arriva nel verso del sensore: [rotationDegrees] (0, 90, 180, 270, in senso orario) lo porta dritto, come `ImageInfo.rotationDegrees` di CameraX. Il riconoscimento consegna gli angoli già nel verso dritto.
  - `val uprightAspect: Float get()` — Larghezza su altezza **dritta**, dopo [rotationDegrees].
  - `companion object`
    - `fun of(bitmap: Bitmap, maxLongSide: Int = FluidScanEdges.AnalysisLongSide): FluidScanFrame` — La luminanza di una bitmap (Rec. 601, come il piano Y della fotocamera), già dritta.
- `data class FluidScanDetection(val quad: FluidScanQuad, val confidence: Float, val aspect: Float)` — Il foglio trovato in un fotogramma: gli angoli dritti e la fiducia (0..1, il voto dei bordi).
- `class FluidScanEdges private constructor()` — Il riconoscimento dei bordi del foglio. Uno per fotocamera ([FluidScanEdges.create]): tiene i buffer fra un fotogramma e l'altro, così l'analisi dal vivo non alloca a ogni fotogramma. Non è thread-safe: lo chiama un solo filo (quello dell'analisi).
  - `fun detect(frame: FluidScanFrame): FluidScanDetection?` — Il foglio nel fotogramma, o `null` se non c'è un foglio credibile.
  - `companion object`
    - `const val AnalysisLongSide: Int` — Il lato lungo dell'analisi, in pixel. 320, a occhio (25 novembre 2026): sotto i 10 ms per fotogramma su un A52s.
    - `const val MinConfidence: Float` — Il voto minimo per credere a un foglio: due terzi dei bordi sostenuti. A occhio, sulle foto di prova.
    - `const val MinAreaFraction: Float` — Il foglio più piccolo che si cerca: l'8% dell'immagine; sotto, la scritta chiede di avvicinarsi.
    - `fun create(): FluidScanEdges` — Un riconoscitore nuovo, coi suoi buffer.
    - `fun detect(bitmap: Bitmap): FluidScanDetection?` — Il foglio in una foto intera (lo scatto, o una foto importata), ridotta per l'analisi.

## `engine-scan-core/scan/FluidScanGeometry.kt`

- `data class FluidScanPoint(val x: Float, val y: Float)` — Un punto dell'immagine, normalizzato: x e y da 0 a 1, l'origine in alto a sinistra.
  - `operator fun plus(other: FluidScanPoint): FluidScanPoint`
  - `operator fun minus(other: FluidScanPoint): FluidScanPoint`
  - `operator fun times(factor: Float): FluidScanPoint`
  - `fun clamped(): FluidScanPoint` — Lo stesso punto con x e y dentro 0..1.
- `data class FluidScanQuad(val topLeft: FluidScanPoint, val topRight: FluidScanPoint, val bottomRight: FluidScanPoint, val bottomLeft: FluidScanPoint)` — I quattro angoli di un foglio in un'immagine, normalizzati (0..1) e in senso orario a partire da quello in alto a sinistra **del foglio**: è l'ordine in cui il foglio raddrizzato si legge. [ordered] mette in quest'ordine quattro punti qualunque, come li trova il riconoscimento dei bordi.
  - `val points: List<FluidScanPoint> get()` — Gli angoli, nell'ordine del foglio.
  - `operator fun get(index: Int): FluidScanPoint` — L'angolo [index] (0 in alto a sinistra, poi in senso orario).
  - `fun with(index: Int, point: FluidScanPoint): FluidScanQuad` — Lo stesso foglio con l'angolo [index] spostato in [point] (il ritaglio a mano).
  - `fun area(aspect: Float = 1f): Float` — L'area in un'immagine di [aspect] (larghezza su altezza) con l'altezza 1: il formato conta, la scala no.
  - `fun isConvex(aspect: Float = 1f): Boolean` — Vero se il quadrilatero è convesso e non si incrocia: il solo che un foglio vero può dare.
  - `val center: FluidScanPoint get()` — Il centro (la media dei quattro angoli).
  - `fun distanceTo(other: FluidScanQuad, aspect: Float = 1f): Float` — La distanza più grande fra gli angoli di due fogli, in frazioni della diagonale di un'immagine di [aspect]: 0 se sono lo stesso foglio. La usa l'inseguimento per dire se il telefono è fermo.
  - `fun lerp(other: FluidScanQuad, fraction: Float): FluidScanQuad` — Il foglio fra questo e [other], a [fraction] (0 questo, 1 l'altro): l'ammorbidimento dal vivo.
  - `fun rotated(degrees: Int): FluidScanQuad` — Lo stesso foglio nell'immagine girata di [degrees] in senso orario (0, 90, 180, 270): un fotogramma della fotocamera arriva nel verso del sensore e si legge girato di `rotationDegrees`. Gli angoli restano quelli del foglio: girare l'immagine non cambia quale angolo è in alto a sinistra **sul foglio**.
  - `fun turnedClockwise(): FluidScanQuad` — Il foglio girato di un quarto di giro in senso orario **sul foglio** (il tasto «Ruota» della revisione): gli stessi quattro punti dell'immagine, con l'angolo in alto a sinistra che diventa quello che era in basso a sinistra.
  - `fun clamped(): FluidScanQuad` — Lo stesso foglio con ogni angolo dentro l'immagine.
  - `fun pageMap(): FluidScanPageMap?` — La prospettiva di questo foglio: porta un punto della pagina raddrizzata nella foto (F8.17d). `null` se gli angoli non fanno un foglio (tre punti in fila).
  - `fun inset(fraction: Float): FluidScanQuad` — Il foglio rientrato di [fraction] della pagina su ogni lato, **in prospettiva**: il bordo che si toglie è largo uguale sulla pagina raddrizzata, anche dove la foto lo vede stretto. Lo stesso foglio se la prospettiva non si calcola.
  - `companion object`
    - `val Full: FluidScanQuad` — Tutta l'immagine: il foglio di serie quando i bordi non si trovano (come VisionKit, che allora scatta tutto).
    - `fun ordered(points: List<FluidScanPoint>, aspect: Float = 1f): FluidScanQuad` — Quattro punti qualunque messi nell'ordine del foglio: in alto a sinistra quello con x + y più piccolo, poi in senso orario attorno al centro. Con un foglio girato di quasi 45° vince l'ordine angolare, non la somma.
- `fun FluidScanQuad.estimatedAspect(width: Int, height: Int): Float` — Le proporzioni vere del foglio (larghezza su altezza) viste dalla foto: con la prospettiva i lati che si vedono non dicono il formato. È il metodo di Zhang e He (*Whiteboard scanning and image enhancement*, Digital Signal Processing 17, 2007, § 3): dai quattro angoli si stima la focale e da lì il rapporto, col centro ottico al centro della foto.
- `class FluidScanPageMap private constructor(private val homography: ScanHomography)` — La prospettiva di un foglio (F8.17d): porta un punto della pagina raddrizzata nella foto, tutti e due normalizzati (0..1, l'origine in alto a sinistra). È il raddrizzamento di [FluidScanImages.straighten] al contrario: serve a riportare sulla foto quello che si trova sulla pagina, come le righe che «Leggi testo» legge sulla pagina raddrizzata di una foto storta.
  - `fun photoPoint(u: Float, v: Float): FluidScanPoint` — Il punto della foto sotto il punto ([u], [v]) della pagina.
  - `companion object`
    - `fun of(quad: FluidScanQuad): FluidScanPageMap?` — La prospettiva del foglio [quad], o `null` se gli angoli non la danno.

## `engine-scan-core/scan/FluidScanImages.kt`

- `value class FluidScanFilter private constructor(val id: String)` — Il filtro di una pagina: i quattro di VisionKit (Color, Grayscale, Black & White, Photo). Non è un `enum` di proposito (regola 3.11): un filtro nuovo è una costante in più.
  - `companion object`
    - `val Document: FluidScanFilter` — Il foglio pulito a colori: carta bianca, ombre tolte, inchiostro più deciso. Quello di serie, come in VisionKit.
    - `val Grayscale: FluidScanFilter` — Come [Document], in scala di grigi.
    - `val BlackAndWhite: FluidScanFilter` — Solo bianco e nero, con la soglia che segue la luce di ogni zona: per i testi da archiviare o da faxare.
    - `val Photo: FluidScanFilter` — La foto raddrizzata, senza ritocchi: per le foto e i disegni.
    - `val All: List<FluidScanFilter>` — I filtri nell'ordine del menu.
    - `fun of(id: String?): FluidScanFilter` — Il filtro col nome [id], o [Document].
- `object FluidScanImages` — Raddrizzare e pulire le pagine. Tutto fuori dal filo principale: sono millisecondi su milioni di pixel.
  - `const val DefaultMaxLongSide: Int` — Il lato lungo della pagina raddrizzata: 2400 pixel, circa 205 punti per pollice su un A4. A occhio (25 novembre 2026): si legge e si stampa bene, il testo si riconosce, e una pagina in memoria resta sotto i 17 MB.
  - `fun outputSize(quad: FluidScanQuad, width: Int, height: Int, maxLongSide: Int = DefaultMaxLongSide): Pair<Int, Int>` — La misura della pagina raddrizzata dal foglio [quad] in una foto [width] × [height]: le proporzioni vere ([estimatedAspect]) e il lato lungo dei pixel che la foto ha davvero, mai più di [maxLongSide].
  - `const val EdgeInset: Float` — Quanto della pagina si toglie su ogni lato prima di raddrizzare: lo 0,5%, misurato sulla pagina dritta (un A4 perde un millimetro per lato). Sul Tab S9 (26/11/2026) i bordi trovati, che sbagliano al più dello 0,27% della diagonale della foto, lasciavano in alto e a sinistra un filo di tavolo che il filtro «Documento» faceva nero e tratteggiato. Anche gli scanner di Note e File tagliano un filo di bordo.
  - `fun straighten(photo: Bitmap, quad: FluidScanQuad, quarterTurns: Int = 0, maxLongSide: Int = DefaultMaxLongSide, edgeInset: Float = EdgeInset): Bitmap` — La pagina raddrizzata: il foglio [quad] di [photo] portato a un rettangolo con le proporzioni vere, girato di [quarterTurns] quarti in senso orario. Il campionamento è bilineare; da una foto molto più grande della pagina si riduce prima a metà quante volte serve, così i caratteri piccoli non si sgranano.
  - `fun rotate(bitmap: Bitmap, quarterTurns: Int): Bitmap` — [bitmap] girata di [quarterTurns] quarti di giro in senso orario (la stessa se zero).
  - `fun filter(bitmap: Bitmap, filter: FluidScanFilter): Bitmap` — La pagina col filtro [filter]: una bitmap nuova (o la stessa con [FluidScanFilter.Photo]).

## `engine-scan/scan/FluidCameraXScanCamera.kt`

- `@Composable fun rememberFluidCameraXScanCamera(): FluidScanCamera` — La fotocamera vera dello scanner, accesa finché [FluidScanCamera.Preview] è in composizione. Il permesso `CAMERA` lo dichiara l'app e lo chiede lo scanner, col tasto, nel punto in cui serve (F8.11): senza il permesso [FluidScanCamera.problem] lo dice e lo scanner offre «Consenti la fotocamera».

## `engine-scan/scan/FluidDocumentScanner.kt`

- `data class FluidScanStrings(val cancel: String = "Annulla", val save: String = "Salva", val saveCount: (Int) -> String = { n -> "Salva ($n)" }, val shutter: String = "Scatta", val auto: String = "Automatico", val manual: String = "Manuale", val autoDescription: String = "Scatto automatico", val torch: String = "Flash", val torchOn: String = "Accendi la torcia", val torchOff: String = "Spegni la torcia", val filter: String = "Filtri", val filterNames: (FluidScanFilter) -> String = { f -> when (f) { FluidScanFilter.Grayscale ->…, val hintFind: String = "Inquadra un documento", val hintCloser: String = "Avvicinati", val hintSteady: String = "Tieni fermo", val hintCapturing: String = "Scansione…", val pages: (Int) -> String = { n -> if (n == 1) "1 pagina" else "$n pagine" }, val openPages: String = "Rivedi le pagine", val pageOf: (Int, Int) -> String = { i, n -> "$i di $n" }, val page: (Int, Int) -> String = { i, n -> "Pagina $i di $n" }, val back: String = "Torna alla fotocamera", val crop: String = "Ritaglia", val rotate: String = "Ruota", val retake: String = "Riprendi", val delete: String = "Elimina", val done: String = "Fine", val reset: String = "Rileva i bordi", val wholePhoto: String = "Tutta la foto", val corners: List<String> = listOf("Angolo in alto a sinistra", "Angolo in…, val moveLeft: String = "Sposta a sinistra", val moveRight: String = "Sposta a destra", val moveUp: String = "Sposta in alto", val moveDown: String = "Sposta in basso", val discardTitle: (Int) -> String = { n -> if (n == 1) "Eliminare la scansione?" el…, val discard: String = "Elimina", val keepScanning: String = "Continua a scansionare", val permissionTitle: String = "Per scansionare serve la fotocamera", val permissionAction: String = "Consenti la fotocamera", val unavailable: String = "La fotocamera non è disponibile", val saving: String = "Salvataggio…", val pageAdded: (Int) -> String = { n -> "Pagina $n scansionata" }, val documentFound: String = "Documento trovato", val captureFailed: String = "Scatto non riuscito, riprova", val pagesFull: (Int) -> String = { n -> "Hai raggiunto il massimo di $n pagine" }, val saveFailedTitle: String = "Non è stato possibile salvare", val saveFailed: (List<Int>) -> String = { pages -> when (pages.size) { 0 -> "Il documen…, val ok: String = "OK", val pageFailed: String = "Pagina non leggibile")` — Le parole dello scanner, in italiano di serie.
- `object FluidScanTags` — I tag dei test dello scanner.
  - `const val Scanner`
  - `const val Camera`
  - `const val Shutter`
  - `const val Save`
  - `const val Cancel`
  - `const val Auto`
  - `const val Hint`
  - `const val Thumbnail`
  - `const val Quad`
  - `const val Review`
  - `const val Strip`
  - `const val Crop`
  - `const val Rotate`
  - `const val Filter`
  - `const val Delete`
  - `const val Retake`
  - `const val CropEditor`
  - `const val CropDone`
  - `fun stripPage(index: Int): String`
  - `fun corner(index: Int): String`
  - `fun filter(filter: FluidScanFilter): String`
- `@Composable fun FluidDocumentScanner(state: FluidScanState, camera: FluidScanCamera, onDone: (FluidScanResult) -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier, strings: FluidScanStrings = FluidScanStrings(), openReview: Boolean = false)` — Lo scanner di documenti a tutto schermo, come la scansione di Note e File di iOS (VisionKit): - **la fotocamera di vetro**: il foglio trovato dal vivo, colorato; la scritta che guida («Inquadra un documento», «Avvicinati», «Tieni fermo»); lo scatto da solo quando il foglio è fermo («Auto») o col tasto («Manuale»); la torcia e il filtro; la pagina raddrizzata che vola nella miniatura;

## `engine-scan/scan/FluidScanActivity.kt`

- `data class FluidScanRequest(val output: FluidScanOutput = FluidScanOutput.Pdf, val filter: FluidScanFilter = FluidScanFilter.Document, val autoCapture: Boolean = true, val title: String? = null)` — Cosa chiede l'app allo scanner aperto col contratto.
- `class FluidScanDocumentContract : ActivityResultContract<FluidScanRequest, FluidScanResult?>()` — Il contratto dello scanner: si lancia con una [FluidScanRequest] e torna una [FluidScanResult] (senza i file: solo gli indirizzi, già concessi in lettura), o `null` se l'utente annulla.
- `class FluidScanActivity : ComponentActivity()` — L'attività dello scanner del contratto (non esportata). Il tema è quello di serie dell'engine; un'app che vuole il suo lo mette in [FluidScanActivity.theme] all'avvio (o usa [FluidDocumentScanner] in una sua pagina).
  - `companion object`
    - `var theme: @Composable (content: @Composable () -> Unit) -> Unit` — Il tema dello scanner del contratto: di serie [FluidTheme] con le impostazioni di serie.

## `engine-scan/scan/FluidScanCamera.kt`

- `interface FluidScanCamera` — La fotocamera dello scanner: l'anteprima, i fotogrammi per l'analisi e lo scatto. Lo scanner non sa da dove arrivano: la fotocamera vera ([rememberFluidCameraXScanCamera]), oppure foto di fogli già scattate ([FluidStillScanCamera]) per le prove, la galleria e un telefono senza fotocamera.
  - `val aspect: Float` — Larghezza su altezza della scena dritta (per esempio 3/4 in verticale). Pubblicato come stato di Compose.
  - `val ready: Boolean` — Vero quando la fotocamera dà immagini; falso mentre parte o se non c'è (allora [problem] dice perché).
  - `val problem: FluidScanCameraProblem? get()` — Perché la fotocamera non c'è; `null` se va.
  - `fun permissionChanged()` — Il permesso della fotocamera potrebbe essere cambiato (lo scanner lo chiama dopo averlo chiesto).
  - `val hasTorch: Boolean get()` — Vero se c'è il flash da tenere acceso (la torcia).
  - `var torch: Boolean` — La torcia accesa.
  - `@Composable fun Preview(modifier: Modifier)` — Il disegno dell'anteprima, che riempie [modifier] tagliando i bordi. La fotocamera vera si accende quando entra in composizione e si spegne quando esce.
  - `suspend fun analyze(analyzer: (FluidScanFrame) -> Unit)` — Fa arrivare i fotogrammi d'analisi a [analyzer] finché la sospensione non viene annullata. [analyzer] gira fuori dal filo principale, un fotogramma alla volta: quelli che arrivano mentre lavora si saltano.
  - `suspend fun capture(): Bitmap` — Scatta: la foto intera e dritta, alla risoluzione più alta che la fotocamera dà per un documento. Gira fuori dal filo principale. Chi la riceve la possiede (la ricicla quando ha finito).
  - `fun shake(timeMillis: Long): FluidScanPoint` — Lo spostamento finto della scena, in frazioni dell'immagine: lo usa solo [FluidStillScanCamera] per far tremare la mano. Il riconoscimento lo aggiunge agli angoli trovati. Zero per la fotocamera vera.
- `value class FluidScanCameraProblem private constructor(private val id: Int)` — Perché la fotocamera non c'è. Non è un `enum` di proposito (regola 3.11).
  - `companion object`
    - `val Permission: FluidScanCameraProblem` — Manca il permesso `CAMERA`: lo scanner offre «Consenti la fotocamera».
    - `val Unavailable: FluidScanCameraProblem` — Non c'è una fotocamera posteriore, o non si apre.
- `class FluidStillScanCamera(private val sheets: List<suspend () -> Bitmap>, private val table: (suspend () -> Bitmap)? = null, private val shakeAmount: Float = 0.025f, private val frameMillis: Long = 66L) : FluidScanCamera` — La fotocamera finta: mostra una per volta le foto di fogli in [sheets] (ognuna una funzione che la carica), con la mano che trema un poco e poi si ferma, e dopo ogni scatto toglie il foglio per un momento mostrando [table] (il tavolo vuoto), come chi cambia pagina. Serve alle prove, alla galleria (che non chiede la fotocamera) e alle riprese.
  - `val sheetIndex: Int get()` — L'indice del foglio mostrato adesso (per le prove).
  - `val remaining: Int get()` — Quanti fogli restano da mostrare.

## `engine-scan/scan/FluidScanLicenses.kt`

- `val FluidScanLicenses: List<FluidLicense>` — Le librerie di terzi di `engine-scan`: solo CameraX. I bordi, la prospettiva, i filtri e il PDF sono codice dell'engine (niente OpenCV).

## `engine-scan/scan/FluidScanOutput.kt`

- `class FluidScanFileProvider : FileProvider()` — Il FileProvider delle scansioni (`${applicationId}.fluidscan`): solo la cartella `cache/fluid-scan/`.
- `value class FluidScanOutput private constructor(private val bits: Int)` — Cosa consegna lo scanner: il PDF, le immagini delle pagine (JPEG), o tutte e due. Non è un `enum` di proposito (regola 3.11).
  - `val pdf: Boolean get()`
  - `val images: Boolean get()`
  - `companion object`
    - `val Pdf: FluidScanOutput` — Un PDF con una pagina per foglio: quello che Note di iOS salva, e che Universal Converter e Pampa Notes leggono.
    - `val Images: FluidScanOutput` — Un JPEG per pagina.
    - `val PdfAndImages: FluidScanOutput`
- `data class FluidScanResult(val pdf: Uri?, val images: List<Uri>, val pageCount: Int, val title: String, val pdfFile: File? = null, val imageFiles: List<File> = emptyList())` — Una scansione consegnata. Gli indirizzi sono `content://` del FileProvider del modulo, in sola lettura: l'app li apre con il `ContentResolver`, li copia dove vuole o li passa a un'altra app con [grantTo]. I file restano nella cache dell'app finché non chiama [FluidScanState.clear] o finché Android non la svuota.
  - `val uris: List<Uri> get()` — Tutti gli indirizzi, il PDF per primo.
  - `fun grantTo(intent: Intent): Intent` — Un intent con gli indirizzi e il permesso di lettura, per consegnarli a un'altra app o come esito d'attività.

## `engine-scan/scan/FluidScanState.kt`

- `data class FluidScanPage(val id: String, val quad: FluidScanQuad, val quarterTurns: Int = 0, val filter: FluidScanFilter = FluidScanFilter.Document, val photo: File, val photoWidth: Int, val photoHeight: Int)` — Una pagina della fila: la foto scattata ([photo], sul disco), il foglio dentro la foto ([quad]), quanti quarti di giro ([quarterTurns]) e il filtro. La pagina pulita si ricalcola quando cambia uno dei tre.
- `data class FluidScanOptions(val output: FluidScanOutput = FluidScanOutput.Pdf, val filter: FluidScanFilter = FluidScanFilter.Document, val autoCapture: Boolean = true, val maxLongSide: Int = FluidScanImages.DefaultMaxLongSide, val jpegQuality: Int = 85, val maxPages: Int = 100, val title: String? = null)` — Le scelte dello scanner.
- `class FluidScanState internal constructor(private val context: Context, private val scope: CoroutineScope, val options: FluidScanOptions, internal val session: String)` — Lo stato di una scansione: la fila delle pagine, le loro miniature e la consegna. Si crea con [rememberFluidScanState]; le pagine sopravvivono alla rotazione e alla ricreazione dell'attività (sono sul disco). Tutto ciò che legge Compose è stato di snapshot, scritto sul filo principale.
  - `val pages: List<FluidScanPage> get()` — Le pagine, nell'ordine della fila.
  - `var working by mutableStateOf(false)` — Vero mentre una pagina si sta pulendo o la consegna si sta scrivendo.
  - `fun thumbnail(id: String): ImageBitmap?` — La miniatura pulita della pagina [id] (lato lungo 480 px), `null` finché non è pronta.
  - `suspend fun add(photo: Bitmap, quad: FluidScanQuad? = null): FluidScanPage?` — Aggiunge in coda la foto [photo] col foglio [quad] (quello trovato, o tutta la foto). La foto va sul disco e la pagina pulita si calcola fuori dal filo principale; la pagina è nella fila subito, con la miniatura che arriva dopo. Restituisce la pagina, o `null` se la fila è piena (o se la scansione è stata buttata nel frattempo).
  - `fun remove(id: String)` — Toglie la pagina [id].
  - `fun move(from: Int, to: Int)` — Sposta la pagina da [from] a [to] (il riordino col dito o con TalkBack).
  - `fun setQuad(id: String, quad: FluidScanQuad)` — Cambia il foglio della pagina [id] (il ritaglio a mano).
  - `fun rotate(id: String)` — Gira la pagina [id] di un quarto in senso orario.
  - `fun setFilter(id: String, filter: FluidScanFilter)` — Il filtro della pagina [id].
  - `fun setFilterForAll(filter: FluidScanFilter)` — Il filtro di tutte le pagine (il menu dei filtri sulla fotocamera vale per tutte, come VisionKit).
  - `suspend fun retake(id: String, photo: Bitmap, quad: FluidScanQuad? = null)` — Rifà la pagina [id] con una foto nuova (il «Riprendi» di VisionKit): stessa posizione nella fila. Lo scatto nuovo prende il posto della foto solo quando la pagina nuova è pronta: annullato a metà (Indietro mentre si scrive), lascia la foto e la pagina di prima.
  - `suspend fun awaitProcessed()` — Aspetta che tutte le pagine pulite siano sul disco (o fallite).
  - `suspend fun export(output: FluidScanOutput = options.output, title: String = options.title ?: defaultTitle()): FluidScanResult` — Scrive la consegna (il PDF, le immagini o tutte e due, come dice [output]) e la restituisce. Le pagine pulite che mancano si finiscono prima (e quelle fallite si rifanno una volta). Gira fuori dal filo principale.
  - `suspend fun cleanPage(id: String, maxLongSide: Int = Int.MAX_VALUE): Bitmap?` — La pagina pulita [id] intera, dal disco (per la revisione a tutto schermo); `null` se non c'è o non si legge.
  - `suspend fun photo(id: String, maxLongSide: Int): Bitmap?` — La foto della pagina [id], ridotta a [maxLongSide] (per il ritaglio); `null` se non c'è o non si legge.
  - `fun clear()` — Toglie tutte le pagine e i file della scansione. I file si cancellano in sottofondo anche dopo che la schermata si è chiusa, dopo i lavori che li stavano scrivendo.
  - `fun defaultTitle(): String` — «Scansione 25 nov 2026, 14:32», nella lingua del telefono.
- `@Composable fun rememberFluidScanState(options: FluidScanOptions = FluidScanOptions()): FluidScanState` — Lo stato di una scansione, ricordato fra le ricreazioni dell'attività: le pagine sono sul disco, e si ritrovano. Con [options] le scelte (cosa consegnare, il filtro, lo scatto automatico).

## `engine-scan/scan/FluidScanTracker.kt`

- `value class FluidScanHint private constructor(private val id: Int)` — Cosa dice la scritta sopra la fotocamera, come VisionKit («Position the document in view», «Move closer», «Hold still»). Non è un `enum` di proposito (regola 3.11): un consiglio nuovo è una costante in più.
  - `companion object`
    - `val Find: FluidScanHint` — Nessun foglio: «Inquadra un documento».
    - `val Closer: FluidScanHint` — Un foglio piccolo: «Avvicinati».
    - `val Steady: FluidScanHint` — Un foglio che si muove ancora: «Tieni fermo».
    - `val Capturing: FluidScanHint` — Lo scatto automatico in corso: «Scansione…».
    - `val Ready: FluidScanHint` — Manuale, foglio trovato: nessuna scritta, si scatta col tasto.
- `data class FluidScanTrack(val quad: FluidScanQuad?, val aspect: Float, val steadiness: Float, val shouldCapture: Boolean, val hint: FluidScanHint)` — Lo stato dell'inseguimento dopo un fotogramma.
- `class FluidScanTracker(private val steadyMillis: Long = FluidScanTrackerDefaults.SteadyMillis, private val stillDistance: Float = FluidScanTrackerDefaults.StillDistance, private val smoothing: Float = FluidScanTrackerDefaults.Smoothing, private val lostMillis: Long = FluidScanTrackerDefaults.LostMillis)` — L'inseguimento del foglio: ammorbidisce gli angoli (il riconoscimento trema di qualche pixel), conta da quanto il telefono è fermo e dice quando scattare da solo. Dopo uno scatto aspetta un foglio nuovo: che quello di prima sparisca o si sposti, come VisionKit, che non fotografa due volte la stessa pagina.
  - `fun update(detection: FluidScanDetection?, timeMillis: Long, auto: Boolean = true): FluidScanTrack` — Il fotogramma nuovo: [detection] trovato all'istante [timeMillis] (o `null`), con lo scatto automatico [auto].
  - `fun capturedManually()` — Uno scatto fatto col tasto: il foglio di adesso non si riscatta da solo.
  - `fun reset()` — Ricomincia da zero (la fotocamera che riparte).
- `object FluidScanTrackerDefaults` — Le misure dell'inseguimento.
  - `const val SteadyMillis: Long` — Quanto deve restare fermo il foglio prima dello scatto automatico: 900 ms. A occhio (25 novembre 2026), sulle riprese di Note di iOS 18 e 26, dove fra «Tieni fermo» e lo scatto passa circa un secondo. Fiducia media.
  - `const val StillDistance: Float` — Il tremito che vale ancora come fermo: l'1,5% della diagonale fra due fotogrammi. A occhio: una mano che regge il telefono trema di circa mezzo grado.
  - `const val Smoothing: Float` — L'ammorbidimento degli angoli: ogni fotogramma ne prende il 45%. A occhio, senza ritardo visibile a 30 fps.
  - `const val LostMillis: Long` — Dopo quanto un foglio che non si vede più è perso: 350 ms. A occhio: tre o quattro fotogrammi d'analisi.
  - `const val NewSheetDistance: Float` — Lo spostamento che fa un foglio nuovo dopo uno scatto: un quinto della diagonale. A occhio.
  - `const val CloserAreaFraction: Float` — Sotto questa parte dell'immagine la scritta chiede di avvicinarsi: il 18%. A occhio, come «Move closer».
