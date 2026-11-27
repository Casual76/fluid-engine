<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/condivisione.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Condivisione: foglio, copia preparata, immagine, PDF e stampa

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/output/FluidOffscreen.kt`

- `val LocalFluidPrinting: ProvidableCompositionLocal<Boolean>` — Vero dentro un'uscita dell'engine (l'immagine da condividere, un pezzo di PDF): fuori dallo schermo il vetro non ha uno sfondo da piegare, e un componente che lo sa disegna il materiale piatto ([FluidPrintPanel]) al suo posto.

## `engine-ui/ui/fluid/output/FluidPdf.kt`

- `class FluidPdfResult(val pages: Int, val bytes: Long)` — Quante pagine sono uscite e quanto pesa il file.
- `object FluidPdf` — Il PDF impaginato dell'engine (4.0, F8.10): il «Salva PDF» e la «Stampa» di una pagina Compose qualunque, non solo del contenuto web. Il testo e' testo vero (il `Canvas` di `PdfDocument` lo scrive come testo, coi caratteri dentro il file), le pagine hanno l'intestazione e il numero, e nessuna riga e' tagliata a meta'.
  - `suspend fun write(context: Context, document: FluidPdfDocument, out: OutputStream, style: FluidPdfStyle = FluidPdfStyle.Default): FluidPdfResult` — Scrive [document] su [out] e ritorna quante pagine sono uscite. Lavora su `Dispatchers.Default` (impaginare) e `Dispatchers.IO` (scrivere): mai sul thread principale. Un documento con viste Compose lancia `IllegalArgumentException`: per quelle c'e' [FluidPdfWriter].
  - `fun layout(document: FluidPdfDocument, style: FluidPdfStyle = FluidPdfStyle.Default): FluidPdfLayout` — L'impaginazione di [document] senza scrivere il file: quante pagine, e ogni pagina disegnata su una tela qualunque (l'anteprima delle pagine prima di salvare, una miniatura). Senza viste Compose; per quelle [FluidPdfWriter.layout].
  - `suspend fun writeToFile(context: Context, document: FluidPdfDocument, fileName: String, style: FluidPdfStyle = FluidPdfStyle.Default): File` — [write] in un file nuovo della cartella delle uscite ([FluidShareFiles]).
- `class FluidPdfWriter internal constructor(private val view: View, private val parent: CompositionContext, val style: FluidPdfStyle)` — Lo scrittore di PDF di una pagina Compose: conosce il tema (carattere e accento, [rememberFluidPdfStyle]) e sa disegnare i pezzi [FluidPdfBlock.Compose]. Si ottiene con [rememberFluidPdfWriter].
  - `suspend fun write(document: FluidPdfDocument, out: OutputStream): FluidPdfResult` — Scrive [document] su [out]. Le viste Compose si disegnano sul thread principale, il resto fuori.
  - `suspend fun writeToFile(document: FluidPdfDocument, fileName: String): File` — [write] in un file nuovo della cartella delle uscite, pronto per il foglio di condivisione.
  - `fun layout(document: FluidPdfDocument): FluidPdfLayout` — L'impaginazione di [document], anche con le viste Compose (vedi [FluidPdf.layout]). Dal thread principale.
  - `fun print(document: FluidPdfDocument, jobName: String = document.title): android.print.PrintJob?` — Apre l'anteprima di stampa di Android per [document] ([FluidPrint.print]).
- `@Composable fun rememberFluidPdfWriter(): FluidPdfWriter` — Lo scrittore di PDF della pagina, con lo stile del suo tema.
- `class FluidPdfLayout internal constructor(val document: FluidPdfDocument, private val style: FluidPdfStyle, private val composer: FluidPdfComposer?)` — Un documento impaginato (4.0, F8.10): le pagine sono decise, e ognuna si disegna su una tela qualunque in punti (1/72 di pollice; per una bitmap si scala prima). E' la stessa impaginazione del PDF e della stampa: stesse righe, stessi tagli fra le pagine, stessa intestazione e stesso numero.
  - `val pageCount: Int get()` — Quante pagine.
  - `fun draw(number: Int, canvas: Canvas)` — Disegna la pagina [number] (da 1) su [canvas], con l'origine in alto a sinistra della pagina e i punti come unita'.

## `engine-ui/ui/fluid/output/FluidPdfDocument.kt`

- `class FluidPdfPageSize(val name: String, val widthPt: Int, val heightPt: Int)` — Il formato di una pagina, in punti (1/72 di pollice). [A4] di serie; [Letter] per chi stampa negli Stati Uniti.
  - `companion object`
    - `val A4: FluidPdfPageSize`
    - `val Letter: FluidPdfPageSize`
- `sealed class FluidPdfBlock` — Un pezzo di un documento da impaginare. Il testo ([Paragraph], i titoli, le righe) si scrive come testo vero, che nel PDF si seleziona e si cerca; un [Compose] e' una vista dell'app disegnata come vettori e testo (un grafico, una card col suo stile). Le pagine si spezzano solo **fra le righe** di un paragrafo o fra un pezzo e l'altro: mai una riga tagliata a meta'.
  - `class Title(val text: String, val subtitle: String? = null) : FluidPdfBlock()` — Il titolo del documento, nell'accento, con una riga sotto facoltativa.
  - `class Heading(val text: String) : FluidPdfBlock()` — Il titolo di una sezione: non resta mai da solo in fondo a una pagina.
  - `class Paragraph(val text: String) : FluidPdfBlock()` — Un paragrafo: va a capo e si spezza fra le righe, con almeno due righe per parte quando si puo'.
  - `class Row(val label: String, val value: String, val detail: String? = null) : FluidPdfBlock()` — Una riga «etichetta — valore» (una materia e il suo voto), con una nota sotto facoltativa. Non si spezza.
  - `object Divider : FluidPdfBlock()` — Un filo sottile fra due parti.
  - `class Space(val heightPt: Float) : FluidPdfBlock()` — Uno spazio vuoto di [heightPt] punti.
  - `class Compose(val content: @Composable () -> Unit) : FluidPdfBlock()` — Una vista Compose dell'app, larga quanto la colonna del testo e alta quanto serve: un dp vale 0,75 punti, cosi' un corpo da 16 sp esce a 12 pt. Non si spezza: se e' piu' alta di una pagina si riduce fino a starci. Serve un [FluidPdfWriter] (una pagina Compose che lo fornisce), e si disegna sul thread principale.
- `class FluidPdfDocument(val title: String, val blocks: List<FluidPdfBlock>, val header: String? = title, val footer: Boolean = true, val pageSize: FluidPdfPageSize = FluidPdfPageSize.A4, val marginPt: Float = 48f, val locale: Locale = Locale.getDefault(), val pageLabel: (page: Int, pages: Int) -> String = { page, pages -> "Pagina $page di $pages" })` — Un documento da impaginare in PDF o da stampare (4.0, F8.10): un titolo, un'intestazione ripetuta in cima a ogni pagina, il numero di pagina in fondo, e i pezzi in fila. Si scrive con [fluidPdfDocument].
  - `val needsCompose: Boolean get()` — Vero se qualche pezzo e' una vista Compose (che va disegnata sul thread principale).
  - `fun withPageSize(size: FluidPdfPageSize): FluidPdfDocument` — Lo stesso documento su un'altra pagina (la stampa sceglie il foglio).
- `class FluidPdfBuilder internal constructor()` — Il costruttore di [FluidPdfDocument]: un pezzo per chiamata, nell'ordine.
  - `fun title(text: String, subtitle: String? = null)`
  - `fun heading(text: String)`
  - `fun paragraph(text: String)`
  - `fun row(label: String, value: String, detail: String? = null)`
  - `fun divider()`
  - `fun space(heightPt: Float)`
  - `fun compose(content: @Composable () -> Unit)`
- `fun fluidPdfDocument(title: String, header: String? = title, pageSize: FluidPdfPageSize = FluidPdfPageSize.A4, locale: Locale = Locale.getDefault(), build: FluidPdfBuilder.() -> Unit): FluidPdfDocument` — Un documento con i pezzi scritti in [build]: `fluidPdfDocument("Pagella") { title("Primo quadrimestre"); row("Matematica", "8") }`.
- `class FluidPdfStyle(val typeface: Typeface = Typeface.DEFAULT, val boldTypeface: Typeface = Typeface.DEFAULT_BOLD, accent: Int = 0xFF0A63D1.toInt(), val ink: Int = 0xFF1C1C1E.toInt(), val secondaryInk: Int = 0xFF5E5E63.toInt(), val rule: Int = 0x33000000)` — Lo stile della carta: il carattere dell'app e il suo accento, e l'inchiostro scuro della stampa. La carta e' sempre chiara, anche con il tema scuro: un PDF si legge e si stampa su bianco. L'accento si scurisce finche' sul bianco non arriva al contrasto 4,5:1 delle regole dell'engine.
  - `val accent: Int` — L'accento leggibile sul bianco.
  - `companion object`
    - `val Default: FluidPdfStyle`
- `@Composable fun rememberFluidPdfStyle(): FluidPdfStyle` — Lo stile della carta preso dal tema: il carattere del corpo e dei titoli e l'accento.

## `engine-ui/ui/fluid/output/FluidPrint.kt`

- `object FluidPrint` — La stampa con l'anteprima di Android (4.0, F8.10), per ogni pagina Compose e non solo per il contenuto web: come `UIPrintInteractionController`. Android mostra l'anteprima, il foglio e la stampante, e la stessa anteprima ha «Salva come PDF». Chiamare dal thread principale, con il contesto di un'attivita'.
  - `fun print(context: Context, document: FluidPdfDocument, jobName: String = document.title, style: FluidPdfStyle = FluidPdfStyle.Default): PrintJob?` — Stampa [document]. L'impaginazione segue il foglio scelto nell'anteprima (A4, Letter, …): i margini e lo stile restano quelli del documento. Un documento con viste Compose si stampa con [FluidPdfWriter.print].
  - `fun printImage(context: Context, image: Bitmap, jobName: String = "Immagine"): PrintJob?` — Stampa un'immagine (un'immagine da condividere, una foto) al centro del foglio, intera, coi margini del documento. La bitmap resta del chiamante.
  - `fun printImage(context: Context, uri: Uri, jobName: String = "Immagine"): PrintJob?` — Stampa l'immagine di [uri], letta alla misura che serve a un foglio a 300 dpi.
  - `fun printPdf(context: Context, uri: Uri, jobName: String = "Documento"): PrintJob?` — Stampa un PDF gia' fatto (un `content://`): Android lo passa com'e' alla stampante.

## `engine-ui/ui/fluid/output/FluidPrintPanel.kt`

- `class FluidPrintColors(val fill: Color, val rim: Color, val sheen: Color, val shadow: Color, val content: Color, val secondaryContent: Color)` — I colori del materiale piatto «stampa» (4.0, F8.10): il vetro di un'uscita, dove il vetro non ha niente da piegare.
  - `companion object`
    - `@Composable fun of(tint: Color = MaterialTheme.colorScheme.primary): FluidPrintColors` — Il materiale per [tint] (di serie l'accento), sul chiaro o sullo scuro del tema.
- `@Composable fun FluidPrintPanel(modifier: Modifier = Modifier, shape: Shape = ContinuousCornerShape(FluidRadius.Sheet), colors: FluidPrintColors = FluidPrintColors.of(), contentPadding: PaddingValues = PaddingValues(24.dp), shadowRadius: Dp = 28.dp, content: @Composable ColumnScope.() -> Unit)` — Un pannello di materiale piatto «stampa»: il posto del vetro in un'immagine da condividere o in una pagina stampata ([LocalFluidPrinting]). Dentro il testo prende `onSurface`, come su un pannello di vetro.
- `@Composable fun FluidShareImageFrame(modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary, signature: String? = null, panelColors: FluidPrintColors = FluidPrintColors.of(accent), content: @Composable ColumnScope.() -> Unit)` — La cornice di un'immagine da condividere ([FluidShareImageRenderer]): il fondo dell'accento, il pannello «stampa» al centro col contenuto e, in fondo, la firma (il nome dell'app). Come le card che Fitness o Musica fanno per le storie: il contenuto e' dell'app, la cornice dell'engine.

## `engine-ui/ui/fluid/output/FluidShareImage.kt`

- `class FluidShareImageFormat(val name: String, val widthPx: Int, val heightPx: Int, val widthDp: Float = 360f)` — Il formato di un'immagine da condividere: la tela in pixel e la larghezza in dp a cui si compone il contenuto. Le tre di serie sono quelle delle chat e delle storie: [Square] (1080 × 1080), [Portrait] (4:5, 1080 × 1350, la piu' alta che un post mostra intera) e [Story] (9:16, 1080 × 1920). A 360 dp di larghezza il contenuto ha la misura di un telefono: un dp e' 3 pixel, e il testo si legge come sullo schermo.
  - `val density: Float get()` — I pixel per dp della tela.
  - `val bytes: Long get()` — I byte della bitmap ARGB del formato.
  - `companion object`
    - `val Square: FluidShareImageFormat`
    - `val Portrait: FluidShareImageFormat`
    - `val Story: FluidShareImageFormat`
    - `val all: List<FluidShareImageFormat>` — I tre di serie, nell'ordine dei selettori.
- `class FluidOutputTooLargeException(message: String) : IllegalStateException(message)` — La tela non entra nella memoria che l'engine lascia alle uscite (`FluidMemoryBudget`): meglio un errore che un OOM.
- `class FluidShareImageRenderer internal constructor(private val view: View, private val parent: CompositionContext)` — Fa un'immagine da una vista Compose (4.0, F8.10), come `ImageRenderer` di SwiftUI: il voto o l'impegno «come immagine, senza fare lo screenshot» della nota di Alessio. Si ottiene con [rememberFluidShareImageRenderer] nella pagina che condivide, e la vista nuova eredita il tema, la lingua e le parole della pagina.
  - `fun render(format: FluidShareImageFormat, content: @Composable () -> Unit): Bitmap` — La bitmap di [content] nel [format]. Chi la riceve la ricicla quando ha finito. Dal thread principale.
  - `suspend fun renderToFile(format: FluidShareImageFormat, fileName: String, content: @Composable () -> Unit): File` — [render] e poi il PNG in un file nuovo della cartella delle uscite dell'engine ([FluidShareFiles]), pronto per [dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem.file]. La bitmap si ricicla appena scritta. Un PNG che non si scrive per intero (il disco pieno) si cancella e lancia `IOException`: mai un file tronco da condividere.
  - `val context: Context get()` — Il contesto della pagina, per chi costruisce l'oggetto da condividere.
- `@Composable fun rememberFluidShareImageRenderer(): FluidShareImageRenderer` — Il renderer delle immagini da condividere per questa pagina (vedi [FluidShareImageRenderer]).

## `engine-ui/ui/fluid/share/FluidPrepareCopy.kt`

- `class FluidCopyFormat(val key: String, val title: String)` — Un formato fra cui scegliere («Originale», «JPEG piu' leggero», «PDF»).
- `class FluidCopyOption(val key: String, val title: String, val subtitle: String = "", val included: Boolean = true)` — Un dato che si puo' includere o lasciare a casa («Posizione», «Registrazione audio», «Allegati»). [included] e' la scelta di partenza. Un dato lasciato a casa chiede all'app la prova che nella copia non c'e' ([FluidCopyCheck]).
- `data class FluidCopyChoice(val format: String?, val included: Set<String>)` — La scelta: il formato e i dati inclusi (per chiave).
  - `fun includes(key: String): Boolean`
- `class FluidCopyCheck(val label: String, val value: String, val passed: Boolean = true, val option: String? = null)` — Una verifica fatta dall'app **sulla copia vera**, non sulle opzioni: «Posizione — tolta», «Peso — 1,2 MB». [option] e' la chiave del dato a cui si riferisce, se c'e'; [passed] falso blocca l'invio.
- `class FluidPreparedCopy(val item: FluidShareItem, val checks: List<FluidCopyCheck>)` — La copia preparata: l'oggetto da passare al foglio di condivisione e le verifiche fatte su di lui.
- `data class FluidPrepareCopyLabels(val title: String = "Opzioni", val message: String? = "Scegli cosa parte. Il riepilogo dice cosa c'è…, val format: String = "Formato", val include: String = "Includi", val preparing: String = "Preparo la copia…", val ready: String = "Pronta da condividere", val blocked: String = "La copia non corrisponde alle opzioni: non par…, val notVerified: String = "Non verificato: %s", val failed: String = "La copia non si è potuta preparare.", val share: String = "Condividi", val retry: String = "Rifai la copia", val cancel: String = "Annulla", val done: String = "Fine", val back: String = "Indietro")` — Le parole del foglio, di serie in italiano.
  - `companion object`
    - `fun english(): FluidPrepareCopyLabels`
- `object FluidPrepareCopyTags` — I tag dei test e delle riprese.
  - `const val Sheet`
  - `const val Status`
  - `const val Share`
  - `const val Retry`
  - `const val Page` — La pagina «Opzioni» dentro il foglio di condivisione.
  - `const val Done` — La sua conferma.
  - `fun option(key: String): String`
  - `fun check(index: Int): String`
- `sealed class FluidCopyState` — A che punto e' la copia.
  - `object Preparing : FluidCopyState()`
  - `class Ready(val copy: FluidPreparedCopy) : FluidCopyState()`
  - `class Blocked(val copy: FluidPreparedCopy, val problems: List<String>) : FluidCopyState()`
  - `class Failed(val message: String?) : FluidCopyState()`
- `fun fluidCopyProblems(options: List<FluidCopyOption>, choice: FluidCopyChoice, copy: FluidPreparedCopy, labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels()): List<String>` — Cosa non va in [copy] rispetto a [choice]: una verifica fallita, o un dato lasciato a casa senza una verifica passata che lo dica. Vuoto vuol dire che la copia corrisponde alle opzioni. E' la regola della scheda: una spunta da sola non toglie un dato dal file, quindi senza la prova l'invio e' bloccato.
- `@Composable fun FluidPrepareCopySheet(visible: Boolean, onDismissRequest: () -> Unit, onReady: (FluidShareItem) -> Unit, prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy, formats: List<FluidCopyFormat> = emptyList(), options: List<FluidCopyOption> = emptyList(), source: FluidTransitionSource? = null, labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels())` — Il foglio «Opzioni» prima della condivisione (4.0, F8.10, `prepara-la-copia`).
- `class FluidShareCopyOptions(val prepare: suspend (FluidCopyChoice) -> FluidPreparedCopy, val formats: List<FluidCopyFormat> = emptyList(), val options: List<FluidCopyOption> = emptyList(), val labels: FluidPrepareCopyLabels = FluidPrepareCopyLabels())` — Le «Opzioni ›» del foglio di condivisione (4.0, F8.10): con questo, [FluidShareSheet] mostra «Opzioni ›» sotto il nome e le apre come una pagina **dentro lo stesso foglio** ([FluidSheetPages]), come il foglio di Foto di iOS: arriva da destra, indietro in alto a sinistra, la spunta in alto a destra.
- `object FluidCopyTools` — Gli attrezzi per le copie delle foto (4.0, F8.10): togliere la posizione da un JPEG e verificare che non c'e' piu', leggendo il file e non le opzioni. Usano `android.media.ExifInterface`; chiamare fuori dal thread principale.
  - `val LocationTags: List<String>` — I campi EXIF che dicono dove e quando e' stata fatta una foto col GPS.
  - `fun hasLocation(file: File): Boolean` — Vero se [file] porta ancora un dato di posizione.
  - `fun copy(resolver: ContentResolver, source: Uri, target: File)` — Copia l'immagine di [source] in [target].
  - `fun copyWithoutLocation(resolver: ContentResolver, source: Uri, target: File)` — Copia un JPEG di [source] in [target] senza i dati di posizione; il resto dei metadati resta.

## `engine-ui/ui/fluid/share/FluidQr.kt`

- `class FluidQrMatrix(val size: Int, private val dark: BooleanArray)` — Un codice QR gia' calcolato: [size] × [size] moduli, scuri dove [get] e' vero, senza la zona vuota intorno (la aggiunge chi lo disegna, [FluidQrCode]). Lo produce un [FluidQrEncoder]; l'engine non porta un codificatore in `engine-ui`.
  - `operator fun get(x: Int, y: Int): Boolean` — Vero se il modulo in colonna [x] e riga [y] e' scuro.
- `fun interface FluidQrEncoder` — Chi trasforma un testo in un QR. `engine-ui` non lo porta: lo da' il modulo facoltativo `engine-qr` (ZXing core, Apache-2.0), che l'app include se vuole «Mostra come QR». Senza codificatore il foglio non mostra quell'azione.
  - `fun encode(text: String): FluidQrMatrix?` — Il QR di [text], o null se il testo non entra in un codice.
- `val LocalFluidQrEncoder: ProvidableCompositionLocal<FluidQrEncoder?>` — Il codificatore dei QR della composizione; null (di serie) toglie «Mostra come QR» dal foglio.
- `@Composable fun FluidQrCode(matrix: FluidQrMatrix, contentDescription: String?, modifier: Modifier = Modifier, color: Color = Color.Black, background: Color = Color.White)` — Un QR disegnato: moduli [color] su [background], con la zona vuota di 4 moduli che i lettori chiedono (ISO/IEC 18004). Il codice si fa bitmap una volta sola, un pixel per modulo, e si ingrandisce senza filtro: i bordi restano netti a ogni taglia, e un ridisegno non ricalcola niente. Il contrasto e' quello dell'app: [color] scuro su [background] chiaro, mai il contrario (molti lettori non leggono un QR invertito).

## `engine-ui/ui/fluid/share/FluidShareFiles.kt`

- `class FluidShareFileProvider : FileProvider()` — Il FileProvider dell'engine per cio' che l'app manda fuori (4.0, F8.10): le immagini da condividere, i PDF, le copie preparate. Una sottoclasse propria, con l'autorita' `<applicationId>.fluid.share`, cosi' non litiga con il `FileProvider` che l'app dichiara gia' (due provider con lo stesso nome di classe non si fondono nel manifest).
- `object FluidShareFiles` — La cartella delle uscite dell'engine: `cacheDir/fluid-share/<una cartella per uscita>/<nome>`. Una cartella per file tiene il nome leggibile («Voto di matematica.png») senza che due uscite con lo stesso nome si sovrascrivano. Sta nella cache: Android la puo' svuotare, e [clean] toglie le uscite vecchie (di serie un giorno).
  - `const val Folder: String` — La cartella sotto `cacheDir`, la stessa di `res/xml/fluid_share_paths.xml`.
  - `fun authority(context: Context): String` — L'autorita' del FileProvider dell'engine per questa app.
  - `fun root(context: Context): File` — La radice delle uscite.
  - `fun newFile(context: Context, name: String): File` — Un file nuovo e vuoto per un'uscita chiamata [name] (il nome si ripulisce dai caratteri che un file non ammette).
  - `fun uriFor(context: Context, file: File): Uri` — L'Uri `content://` di un file sotto [root] (o un'altra cartella dichiarata nel provider dell'engine). Per un file fuori lancia `IllegalArgumentException`: per un file qualsiasi dell'app c'e' [FluidShareItem.file], che lo copia qui al momento della condivisione.
  - `fun isShareable(context: Context, file: File): Boolean` — Vero se [file] sta sotto [root], dove il FileProvider dell'engine lo espone senza copia. Il confronto e' sui percorsi canonici, cartella per cartella: `fluid-share2/` non e' dentro `fluid-share/`, e `..` non fa uscire.
  - `const val DefaultCopyLimitBytes: Long` — Il tetto di serie di una copia per la condivisione: 512 MB. Un file dell'app fuori da [root] si copia qui prima di partire, e oltre il tetto il foglio dice perche' non parte invece di riempire la cache. A occhio (24 novembre 2026): un video di qualche minuto in 4K ci sta, un archivio intero no.
  - `suspend fun stage(context: Context, item: FluidShareItem, limitBytes: Long = DefaultCopyLimitBytes): FluidShareStaged` — Prepara [item] per la consegna: i suoi [FluidShareItem.localFiles] fuori da [root] si copiano qui (una cartella per file, il nome resta), quelli gia' dentro si espongono cosi' come sono, e tutti diventano Uri `content://` del provider dell'engine. Lavora su `Dispatchers.IO` e si puo' annullare (la copia a meta' si cancella).
  - `fun overrideUrisForTesting(uriFor: ((File) -> Uri)?)` — Solo per le prove: sul JVM di Windows il `FileProvider` non trova le sue radici (cerca «/» e i percorsi hanno «\»), quindi le prove del foglio danno qui un Uri finto per ogni file. `null` torna al provider vero. Mai in un'app.
  - `fun clean(context: Context, olderThanMillis: Long = 24L * 60 * 60 * 1000, now: Long = System.currentTimeMillis())` — Toglie le uscite piu' vecchie di [olderThanMillis]. Da chiamare fuori dal thread principale.
- `enum class FluidShareFileProblem` — Perche' un file dell'app non si puo' condividere (4.0, F8.10): il foglio lo dice con parole sue ([FluidShareLabels]).
- `sealed class FluidShareStaged` — L'esito di [FluidShareFiles.stage].
  - `class Ready(val item: FluidShareItem) : FluidShareStaged()` — Pronto: [item] ha solo Uri `content://` e nessun file locale.
  - `class Failed(val problem: FluidShareFileProblem, val fileName: String, val sizeBytes: Long?, val limitBytes: Long) : FluidShareStaged()` — Non parte, per [problem]. [sizeBytes] e' il peso da copiare quando conta (oltre il tetto, senza spazio).

## `engine-ui/ui/fluid/share/FluidShareIntents.kt`

- `class FluidShareChooserAction(val label: String, val icon: Icon, val action: PendingIntent)` — Un'azione dell'app dentro il foglio di Android («Altre app…»), come le azioni di `UIActivityViewController`: su Android 14 e oltre entra nel foglio di sistema (`EXTRA_CHOOSER_CUSTOM_ACTIONS`), prima non c'e'. L'azione e' un [PendingIntent] perche' il foglio di sistema vive in un altro processo.
- `object FluidShareIntents` — Gli intent della condivisione, in un posto solo (4.0, F8.10). Tutto cio' che manda un file passa da qui, e qui stanno le due regole che un'app dimentica: **il permesso di lettura** (`FLAG_GRANT_READ_URI_PERMISSION`, piu' il `ClipData` con tutti gli Uri, senza il quale `SEND_MULTIPLE` concede solo il primo) e **mai la propria app** nel foglio di Android.
  - `fun send(item: FluidShareItem): Intent` — `SEND` o `SEND_MULTIPLE` con il tipo, il testo, l'oggetto e i file di [item], col permesso di lettura.
  - `fun sendTo(item: FluidShareItem, target: FluidShareTarget): Intent` — [send] verso un'attivita' precisa (una app della fila del foglio).
  - `fun chooser(context: Context, item: FluidShareItem, title: String? = null, customActions: List<FluidShareChooserAction> = emptyList()): Intent` — Il foglio di Android per [item] («Altre app…»), con le azioni dell'app su Android 14 e oltre, e senza le attivita' di questa app che ricevono una condivisione (mandarsi qualcosa da soli non e' mai la cosa voluta).
  - `fun clip(context: Context, item: FluidShareItem): ClipData` — Cio' che «Copia» mette negli appunti: il testo, o gli Uri (che un'app che incolla legge col permesso concesso). Con [FluidShareItem.sensitive] il clip e' segnato delicato (`ClipDescription.EXTRA_IS_SENSITIVE`, Android 13 e oltre): Android non ne mostra il testo nell'anteprima della copia ne' nei suggerimenti della tastiera (R2).
  - `fun createDocument(item: FluidShareItem): Intent` — «Salva in File» di un oggetto solo: il selettore di Android per scegliere dove e con che nome.

## `engine-ui/ui/fluid/share/FluidShareItem.kt`

- `data class FluidShareItem(val title: String, val mimeType: String, val uris: List<Uri> = emptyList(), val text: String? = null, val subject: String? = null, val sizeBytes: Long? = null, val fileName: String? = null, val preview: ImageBitmap? = null, val print: FluidPdfDocument? = null, val detail: String? = null, val localFiles: List<File> = emptyList(), val sensitive: Boolean = false)` — Cio' che il foglio di condivisione manda: uno o piu' file ([uris], letti con il permesso di lettura che l'engine passa a chi riceve), un testo o un link ([text]). Come l'oggetto di `ShareLink` di SwiftUI: l'anteprima in cima al foglio ([title], tipo e peso, [preview]) viene da qui, e la consegna resta agli intent di Android.
  - `val hasFiles: Boolean get()` — Vero se si mandano dei file.
  - `val needsCopy: Boolean get()` — Vero se qualche file va ancora copiato nella cartella dell'engine prima di partire ([FluidShareFiles.stage]): i [localFiles], e un Uri `file://` messo in [uris] (R2: Android vieta di mandarlo a un'altra app, e `startActivity` lancerebbe `FileUriExposedException`; la copia lo fa diventare un `content://`).
  - `val fileCount: Int get()` — Quanti file si mandano.
  - `val isLink: Boolean get()` — Vero se e' un link solo: un testo che e' un indirizzo web.
  - `val saveName: String get()` — Il nome che va in «Salva in File».
  - `companion object`
    - `fun text(text: String, title: String = text.lineSequence().first().take(60), subject: String? = null): FluidShareItem` — Un testo.
    - `fun link(url: String, title: String = url): FluidShareItem` — Un link: il foglio mostra il dominio sotto il titolo e «Mostra come QR».
    - `fun file(uri: Uri, fileName: String, mimeType: String, sizeBytes: Long? = null, preview: ImageBitmap? = null, title: String = fileName): FluidShareItem` — Un file gia' esposto da un FileProvider (o da un altro `ContentProvider` dell'app).
    - `fun file(context: Context, file: File, mimeType: String, preview: ImageBitmap? = null, title: String = file.name): FluidShareItem` — Un file qualsiasi dell'app. Nella cartella dell'engine ([FluidShareFiles.newFile]) l'Uri c'e' subito; altrove (una foto in `filesDir`, un documento dell'app) il file resta in [localFiles] e si copia quando si condivide. Non lancia mai: un file che non c'e' o non si legge lo dice il foglio, al posto delle azioni.
    - `fun files(context: Context, files: List<File>, title: String, mimeType: String, preview: ImageBitmap? = null): FluidShareItem` — Piu' file dell'app insieme, dentro o fuori la cartella dell'engine, come [file]: quelli fuori si copiano quando si condivide.
    - `fun files(uris: List<Uri>, title: String, mimeType: String, sizeBytes: Long? = null, preview: ImageBitmap? = null): FluidShareItem` — Piu' file insieme (le foto di un album): `SEND_MULTIPLE`. [mimeType] e' il tipo comune (per foto miste «image» con l'asterisco).

## `engine-ui/ui/fluid/share/FluidShareSheet.kt`

- `class FluidShareAction(val id: String, val title: String, val icon: ImageVector, val dismisses: Boolean = true, val onClick: (FluidShareItem) -> Unit)` — Un'azione dell'app nel foglio, sopra quelle dell'engine (come «Aggiungi a Nota rapida» sopra «Copia» in iOS). [dismisses] falso lascia il foglio aperto dopo il tocco.
- `data class FluidShareOptions(val copy: Boolean = true, val saveToFiles: Boolean = true, val qr: Boolean = true, val print: Boolean = true, val apps: Boolean = true, val actionsAsList: Boolean = false, val copyLimitBytes: Long = FluidShareFiles.DefaultCopyLimitBytes)` — Le azioni dell'engine da mostrare; tutte accese di serie. Ognuna si nasconde da sola dove non ha senso.
- `sealed class FluidShareOutcome` — Cosa e' successo nel foglio, per l'app che vuole saperlo (un contatore, un'analisi, un «Annulla»).
  - `class SentTo(val target: FluidShareTarget) : FluidShareOutcome()`
  - `object Chooser : FluidShareOutcome()`
  - `object Copied : FluidShareOutcome()`
  - `class Saved(val uris: List<Uri>) : FluidShareOutcome()`
  - `object Printed : FluidShareOutcome()`
  - `object QrShown : FluidShareOutcome()`
  - `class Action(val id: String) : FluidShareOutcome()`
- `data class FluidShareLabels(val copy: String = "Copia", val copied: String = "Copiato", val saveToFiles: String = "Salva in File", val saved: String = "Salvato", val saveFailed: String = "Non si è salvato", val showQr: String = "Mostra come QR", val print: String = "Stampa", val moreApps: String = "Altre app…", val more: String = "Altro", val close: String = "Chiudi", val done: String = "Fine", val qrHint: String = "Inquadralo con la fotocamera di un altro telef…, val qrDescription: String = "Codice QR di %s", val previewOf: String = "Anteprima di %s", val text: String = "Testo", val image: String = "Immagine", val pdf: String = "Documento PDF", val video: String = "Video", val audio: String = "Audio", val file: String = "File", val photos: String = "%d foto", val items: String = "%d elementi", val chooserTitle: String = "Condividi", val options: String = "Opzioni", val preparingFile: String = "Preparo il file…", val fileMissing: String = "Il file non c'è più: forse è stato spostato o…, val fileUnreadable: String = "Il file non si legge, quindi non si può condividere.", val fileTooLarge: String = "Il file è troppo grande da condividere da qui:…, al massimo %2\$s.", val fileNoSpace: String = "Non c'è abbastanza spazio libero per preparare…)` — Le parole del foglio, di serie in italiano; [english] per l'inglese.
  - `companion object`
    - `fun english(): FluidShareLabels`
- `object FluidShareSheetTags` — I tag dei test e delle riprese.
  - `const val Sheet`
  - `const val Header`
  - `const val Thumbnail`
  - `const val Close`
  - `const val Apps`
  - `const val Copy`
  - `const val Save`
  - `const val Qr`
  - `const val QrCode`
  - `const val QrDone` — «Fine» sotto il QR.
  - `const val Print`
  - `const val MoreApps`
  - `const val Options`
  - `const val Preparing` — L'attesa della copia di un file dell'app.
  - `const val Problem` — Il messaggio di un file che non parte.
  - `fun app(packageName: String): String`
  - `fun action(id: String): String`
- `@Composable fun FluidShareSheet(item: FluidShareItem?, onDismissRequest: () -> Unit, source: FluidTransitionSource? = null, actions: List<FluidShareAction> = emptyList(), options: FluidShareOptions = FluidShareOptions(), trust: FluidShareTrust = FluidShareTrust.SameSigner, chooserActions: List<FluidShareChooserAction> = emptyList(), labels: FluidShareLabels = FluidShareLabels(), copyOptions: FluidShareCopyOptions? = null, onShared: (FluidShareOutcome) -> Unit = {}, people: (@Composable (item: FluidShareItem, dismiss: () -> Unit) -> Unit)? = null)` — Il foglio di condivisione (4.0, F8.10, `foglio-condividi`): il foglio di vetro di iOS 26 (`ShareLink`, `UIActivityViewController`) con la consegna di Android.

## `engine-ui/ui/fluid/share/FluidShareTargets.kt`

- `class FluidShareTarget(val packageName: String, val activityName: String, val label: String, val icon: ImageBitmap?)` — Un'app della fila in cima al foglio: l'attivita' che riceve la condivisione, col nome e l'icona di Android.
- `class FluidShareTrust private constructor(internal val sameSigner: Boolean, internal val sha256: Set<String>)` — Di quali app fidarsi per la fila del foglio: la stessa verifica di `engine-ai-bridge`, la firma.
  - `companion object`
    - `val SameSigner: FluidShareTrust` — Le app firmate con la stessa chiave di questa.
    - `fun certificates(sha256: Set<String>): FluidShareTrust` — Le app con un certificato di firma fra queste impronte SHA-256.
- `object FluidShareTargets` — La fila delle app Pampa (4.0, F8.10). Android non lascia elencare tutte le app (servirebbe `QUERY_ALL_PACKAGES`, che l'engine non chiede): `engine-ui` dichiara nel manifest un `<queries>` per le sole app che ricevono `SEND`, e fra quelle il foglio tiene le app fidate ([FluidShareTrust]). Le altre restano al foglio di Android («Altre app…»).
  - `const val MaxTargets: Int` — Quante app al massimo nella fila.
  - `fun find(context: Context, item: FluidShareItem, trust: FluidShareTrust = FluidShareTrust.SameSigner, limit: Int = MaxTargets): List<FluidShareTarget>` — Le app fidate che ricevono [item], senza questa. Legge il `PackageManager`: chiamare fuori dal thread principale.
  - `fun remember(context: Context, target: FluidShareTarget)` — Ricorda che [target] e' stato scelto: la prossima volta sta in cima.
- `@Composable fun rememberFluidShareTargets(item: FluidShareItem?, trust: FluidShareTrust = FluidShareTrust.SameSigner): State<List<FluidShareTarget>?>` — Le app della fila per [item], lette fuori dal thread principale: null finche' la lettura non finisce.
