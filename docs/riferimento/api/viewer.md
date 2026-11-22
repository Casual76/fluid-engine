<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/viewer.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-viewer

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

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

## `engine-viewer/viewer/FluidImageHeader.kt`

- `data class FluidImageHeader(val format: String, val width: Int, val height: Int, val animated: Boolean, val frameCount: Int?, val loopCount: Int?)` — Cosa dicono i primi byte di una GIF o di una WebP: misura, se è animata, quanti fotogrammi e quanti giri.
  - `val aspectRatio: Float? get()` — Il rapporto d'aspetto, `null` se la misura manca.
  - `companion object`
    - `fun read(bytes: ByteArray, length: Int = bytes.size): FluidImageHeader?` — Legge l'intestazione dai primi [length] byte, o `null` se non sono una GIF o una WebP (anche troncate). Non decodifica i pixel: segue i blocchi e conta i fotogrammi. Basta il primo mezzo megabyte per una GIF normale.

## `engine-viewer/viewer/FluidMedia3.kt`

- `class FluidMedia3Session(val player: Player) : FluidMediaSession, FluidVideoGeometry, AutoCloseable` — Il `Player` di Media3 dell'app come `FluidMediaSession` del visore. Il player resta **dell'app**: lo crea, lo prepara, sceglie il file e lo rilascia lei; la sessione lo legge e gli passa play, pausa e salto.
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

## `engine-viewer/viewer/FluidViewerException.kt`

- `value class FluidViewerFailure private constructor(private val id: Int)` — Perché un file non si è aperto. Non è un `enum` di proposito (regola 3.11): un motivo nuovo è una costante in più. Ogni motivo porta allo stesso ripiego, lo sconosciuto con «Apri con…»: il motivo serve al testo che lo spiega.
  - `companion object`
    - `val Unreadable: FluidViewerFailure` — Il file non si legge: sparito, permesso tolto, un `Uri` che non apre niente.
    - `val Corrupt: FluidViewerFailure` — I byte non sono quello che il tipo promette: un PDF rotto, un'immagine troncata.
    - `val Protected: FluidViewerFailure` — Un PDF con la password: il `PdfRenderer` della piattaforma non lo apre.
    - `val TooLarge: FluidViewerFailure` — Oltre il tetto di copia (un PDF da un `Uri` non ricercabile più grande del tetto).
    - `val Unsupported: FluidViewerFailure` — La piattaforma non sa decodificare questo tipo (un HEIF sotto Android 9, un formato sconosciuto).
- `class FluidViewerException(val failure: FluidViewerFailure, message: String, cause: Throwable? = null) : IOException(message, cause)` — Un file che il modulo non ha potuto aprire, con il motivo.

## `engine-viewer/viewer/FluidViewerFiles.kt`

- `data class FluidViewerFile(val uri: Uri, val name: String, val mimeType: String? = null, val sizeBytes: Long? = null)` — Un file da mostrare: dove sta e cosa ne sa l'app. [sizeBytes] `null` è ignoto, zero è vuoto.
  - `val info: FluidFileInfo get()` — Le informazioni per lo sconosciuto e per il pannello: nome, MIME, dimensione e il tipo di `FluidFileKind`.
  - `val type: FluidViewerType get()` — Il tipo per il visore, da MIME e nome (senza leggere il file).
- `data class FluidViewerOptions(val textLimitBytes: Int = FluidTextDocument.DefaultLimitBytes, val textMaxLineChars: Int = FluidTextDocument.DefaultMaxLineChars, val imageMaxPixels: Long = FluidDecodedImage.DefaultMaxPixels, val animateImages: Boolean = true, val pdfMaxCopyBytes: Long = FluidPdfPageSource.DefaultMaxCopyBytes)` — I tetti con cui il modulo apre un file. I valori di serie sono quelli dei singoli decodificatori.
- `class FluidViewerDocument internal constructor(val file: FluidViewerFile, val type: FluidViewerType, val content: FluidViewerContent, val failure: FluidViewerException?, private val resource: Closeable?) : Closeable` — Un file aperto dal modulo: il contenuto per il visore, il tipo, e se qualcosa è andato storto il motivo. Va chiuso quando la pagina esce ([close]): libera il PDF e le sue copie. Il ripiego è sempre lo sconosciuto con «Apri con…» e «Condividi» dell'app, mai un vicolo cieco.
- `suspend fun fluidReadTextFile(context: Context, uri: Uri, fileName: String, mimeType: String? = null, sizeBytes: Long? = null, limitBytes: Int = FluidTextDocument.DefaultLimitBytes, maxLineChars: Int = FluidTextDocument.DefaultMaxLineChars): FluidTextDocument?` — Legge un file di testo da un `Uri`, con [FluidTextDecoder.read]; `null` se i byte non sono testo.
- `suspend fun FluidViewerFile.open(context: Context, options: FluidViewerOptions = FluidViewerOptions(), openWith: (() -> Unit)? = null, share: (() -> Unit)? = null, media: ((FluidViewerType) -> FluidViewerContent?)? = null): FluidViewerDocument` — Apre [FluidViewerFile] col decodificatore del suo tipo: l'immagine (anche animata), il PDF, il testo. Un video o un audio ha bisogno del player dell'app: [media] lo trasforma in contenuto (per esempio con [fluidMedia3Content]), e senza [media] il file va allo sconosciuto.
- `class FluidViewerFiles internal constructor(private val context: Context, private val scope: CoroutineScope)` — I file di un visore, aperti col modulo attorno alla pagina corrente: [content] dà il contenuto della pagina, e la prima volta comincia ad aprirla. Tiene aperti i file entro [window] pagine dall'ultima chiesta e chiude gli altri (un PDF lontano non tiene il suo renderer). Si crea con [rememberFluidViewerFiles]:
  - `val count: Int get()` — Quanti file.
  - `fun document(index: Int): FluidViewerDocument?` — Il file aperto alla pagina [index], `null` finché si apre.
  - `fun content(index: Int): FluidViewerContent` — Il contenuto della pagina [index]. La prima volta comincia ad aprire il file e chiude quelli lontani.
- `@Composable fun rememberFluidViewerFiles(files: List<FluidViewerFile>, options: FluidViewerOptions = FluidViewerOptions(), window: Int = 1, openWith: ((FluidViewerFile) -> Unit)? = null, share: ((FluidViewerFile) -> Unit)? = null, media: ((FluidViewerFile, FluidViewerType) -> FluidViewerContent?)? = null): FluidViewerFiles` — I file di [files] per il visore, aperti col modulo attorno alla pagina corrente e chiusi quando la composizione esce. [media] dà il contenuto di un video o di un audio col player dell'app (per esempio `{ _, type -> fluidMedia3Content(session, video = type.preset == FluidViewerPreset.Video) }`); senza, i video vanno allo sconosciuto. [openWith] e [share] diventano «Apri con…» e «Condividi» dello sconosciuto.

## `engine-viewer/viewer/FluidViewerType.kt`

- `value class FluidViewerPreset private constructor(private val id: Int)` — Quale preset del visore mostra un file: [Image], [Video], [Audio], [Pages], [Text] o [Unknown].
  - `companion object`
    - `val Image: FluidViewerPreset` — Una foto, anche animata (GIF, WebP): `FluidViewerContent.Image`.
    - `val Video: FluidViewerPreset` — Un video: `FluidViewerContent.Media` con la superficie. Il player è dell'app.
    - `val Audio: FluidViewerPreset` — Un audio: `FluidViewerContent.Media` senza superficie.
    - `val Pages: FluidViewerPreset` — Un documento a pagine (PDF): `FluidViewerContent.Pages`.
    - `val Text: FluidViewerPreset` — Testo e codice: `FluidViewerContent.Text`.
    - `val Unknown: FluidViewerPreset` — Tutto il resto, e ogni eseguibile: `FluidViewerContent.Unknown`, con «Apri con…». Mai un vicolo cieco.
- `data class FluidViewerType(val preset: FluidViewerPreset, val kind: FluidFileKind, val mimeType: String?, val language: String?)` — Il tipo di un file per il visore, deciso da MIME, nome e, solo quando i due tacciono, dai primi byte.
  - `val executable: Boolean get()` — Vero se il file è un eseguibile: il preset è sempre [FluidViewerPreset.Unknown] e mostra l'avviso.
  - `companion object`
    - `fun of(mimeType: String?, fileName: String, header: ByteArray? = null): FluidViewerType` — Risolve un file. Precedenza, come in `FluidFileKind.from`: l'eseguibile vince sempre; poi l'estensione (l'ultima: `archivio.tar.gz` è `gz`), poi il MIME, poi la firma dei primi byte ([header], se c'è). Un MIME con parametri o in maiuscolo (`Text/Plain; charset=UTF-8`) vale come quello pulito; un MIME generico o malformato vale come nessun MIME. Un MIME in conflitto con l'estensione perde, salvo che sia eseguibile.
    - `fun sniff(header: ByteArray): FluidViewerPreset?` — Il preset dalla firma dei primi byte (almeno 12 servono a WebP e ai contenitori ISO), o `null` se non la riconosce. Serve al ripiego: un file senza estensione e senza MIME, o un `.txt` che in realtà è un PNG. Non sa riconoscere il testo (lo decide [FluidTextDecoder.looksBinary] sui byte veri).
