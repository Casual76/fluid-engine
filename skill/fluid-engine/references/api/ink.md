# API · engine-ink

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ink/ink/FluidInkCanvas.kt`

- `object FluidInkTags` — I tag delle prove e delle riprese.
  - `const val Canvas`
  - `const val SelectionMenu`
  - `const val Delete`
  - `const val Duplicate`
  - `const val Recolor`
  - `const val RulerAngle`
- `@Composable fun FluidInkCanvas(state: FluidInkState, sheet: FluidInkSheet, modifier: Modifier = Modifier, backdrop: GlassBackdropState? = null, contentPadding: PaddingValues = PaddingValues(0.dp), labels: FluidInkLabels = FluidInkLabels(), selectionActions: Boolean = true)` — Il foglio su cui si disegna: [sheet] sotto (un foglio bianco, una foto, le pagine di un PDF), i tratti di [state] sopra, e tutto quello che fanno dito e penna.

## `engine-ink/ink/FluidInkCodec.kt`

- `object FluidInkCodec` — Il salvataggio dei tratti, per riaprire un disegno e continuare: una nota di Pampa Notes con il suo schizzo, la firma salvata, le annotazioni di un PDF ancora da finire. Binario e compresso (un disegno di cento tratti pesa qualche decina di kB), con la versione in testa: una versione piu' nuova dell'engine legge le vecchie.
  - `const val Version: Int` — La versione che scrive questa build.
  - `fun encode(strokes: List<FluidInkStroke>): ByteArray` — I tratti in byte.
  - `fun write(strokes: List<FluidInkStroke>, output: OutputStream)` — I tratti su [output] (non lo chiude).
  - `fun decode(bytes: ByteArray): List<FluidInkStroke>` — I tratti salvati con [encode]. Un file che non e' un disegno, o rotto, da' [IOException].
  - `fun read(input: InputStream): List<FluidInkStroke>` — I tratti da [input] (non lo chiude).

## `engine-ink/ink/FluidInkDefaults.kt`

- `object FluidInkDefaults` — I numeri del disegno. Dove c'e' Apple, la fonte e la fiducia; dove non c'e', «a occhio» con la data. Si cambiano qui, non nei componenti.
  - `val PenWidths: List<Dp>` — I cinque spessori di ogni strumento, in dp sullo schermo quando comincia il tratto (lo spessore che si vede: chi ingrandisce scrive piu' fine, come in Note di iOS). Cinque perche' cinque sono i pallini della tavolozza di Markup (iOS 26, fiducia alta sul numero); le misure sono a occhio (25/11/2026) sulle riprese di Markup, fiducia bassa: Apple pubblica solo i limiti di `PKInkingTool.InkType.validWidthRange`.
  - `val HighlighterWidths: List<Dp>`
  - `val PencilWidths: List<Dp>`
  - `val EraserWidths: List<Dp>` — I diametri della gomma a pixel (la gomma a oggetti tocca con il dito: [ObjectEraserWidth]).
  - `val ObjectEraserWidth: Dp` — Il diametro della gomma a oggetti: un dito (a occhio, 25/11/2026).
  - `fun widths(tool: FluidInkTool): List<Dp>` — Lo spessore di partenza di ogni strumento: il terzo pallino per la penna, il terzo per l'evidenziatore, il secondo per la matita.
  - `fun defaultWidthIndex(tool: FluidInkTool): Int`
  - `val QuickColors: List<Color>` — I colori veloci, quelli della fila del popover dello strumento: nero, blu, verde, giallo e rosso di sistema di iOS (`UIColor.systemBlue` #007AFF, `systemGreen` #34C759, `systemYellow` #FFCC00, `systemRed` #FF3B30: fiducia alta, i valori chiari della Human Interface Guidelines). Il nero e' quello della penna di Markup.
  - `fun defaultColor(tool: FluidInkTool): Color` — I colori di partenza: penna e matita nere, evidenziatore giallo, come in Markup (fiducia alta).
  - `const val HighlighterAlpha: Float` — L'opacita' dell'evidenziatore: il colore scelto, a meta' (a occhio sulle riprese di Markup, 25/11/2026). Il tratto non si somma con se stesso (`SelfOverlap.DISCARD`): ripassare sullo stesso tratto non lo scurisce, come il marker.
  - `const val ShapeHoldMillis: Long` — Quanto resta fermo il dito alla fine di un tratto perche' diventi una forma: mezzo secondo (a occhio sulle riprese di Note, iOS 26, 25/11/2026; Apple dice solo «tieni premuto alla fine», fiducia media).
  - `val ShapeHoldSlop: Dp` — Quanto puo' tremare il dito fermo: 4 dp (a occhio, 25/11/2026).
  - `val ShapeMinSize: Dp` — La forma piu' piccola che si riconosce: 24 dp di diagonale (a occhio, 25/11/2026).
  - `const val ShapeMorphMillis: Int` — Il viaggio dal tratto a mano alla forma perfetta: 180 ms (a occhio sulle riprese di Note, 25/11/2026).
  - `val RulerHeight: Dp` — Il righello: una riga di 88 dp di altezza (le proporzioni del righello di PencilKit sull'iPhone, a occhio sulle riprese, 25/11/2026) e un tratto che comincia entro 24 dp dal bordo vi si appoggia e corre dritto.
  - `val RulerSnap: Dp`
  - `const val RulerDetentDegrees: Float` — Gli angoli dove il righello si ferma mentre gira (con un tic): ogni 45° entro 2°, come PencilKit (fiducia media).
  - `const val RulerDetentSlack: Float`
  - `const val MaxZoom: Float` — Lo zoom: da 1 (il foglio intero) a 5 volte (a occhio, 25/11/2026: Note arriva piu' o meno li').
  - `const val TwoFingerGraceMillis: Long` — Il secondo dito che arriva sul primo che disegna: entro 250 ms, o prima che il tratto passi 24 dp, il tratto si annulla e i due dita spostano e ingrandiscono la pagina, come nel foglio di PencilKit con «qualsiasi input» (a occhio, 25/11/2026). Dopo, il secondo dito e' il palmo, e si ignora.
  - `val TwoFingerGraceDistance: Dp`
  - `const val HistoryLimit: Int` — Quanti passi tiene la cronologia di annulla e ripristina (PencilKit usa `UndoManager`, senza un tetto dichiarato).
  - `val LassoDash: Dp` — Il colore della selezione del lazo (il tratteggio), e quello del contorno della gomma: l'accento lo da' il tema.

## `engine-ink/ink/FluidInkEditor.kt`

- `object FluidInkEditorTags` — I tag dell'editor.
  - `const val Editor`
  - `const val Cancel`
  - `const val Done`
  - `const val Undo`
  - `const val Redo`
  - `const val Loading`
- `@Composable fun FluidInkEditor(state: FluidInkState, sheet: FluidInkSheetLoad, onCancel: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier, title: String? = null, labels: FluidInkLabels = FluidInkLabels(), busy: Boolean = false)` — L'editor del disegno a tutto schermo, come Markup di iOS 26: in alto la «x» di vetro, annulla e ripristina, il ✓ in evidenza; sotto il foglio ([FluidInkCanvas]) con il foglio bianco, la foto o il PDF; in basso (o in alto) la tavolozza ([FluidInkPalette]). Il foglio scorre sotto le barre di vetro.
- `fun FluidInkSheet.ready(): FluidInkSheetLoad` — Un foglio gia' pronto (un foglio bianco, una foto gia' decodificata) come stato d'apertura dell'editor.

## `engine-ink/ink/FluidInkExport.kt`

- `data class FluidInkExportResult(val width: Int, val height: Int, val mimeType: String, val pages: Int = 1)` — Cosa ha scritto la consegna: la misura in pixel (o in punti, per un PDF), il tipo, le pagine.
- `object FluidInkExport` — La consegna di un disegno: un file **nuovo**, mai sopra l'originale, scritto dove dice l'app ([OutputStream]: un file suo, un `content://` di MediaStore, quello del selettore «Salva come»). Fuori dal thread principale.
  - `const val MaxPixels: Int` — Il tetto della foto annotata: 50 milioni di pixel (una foto da 50 MP passa intera; sopra si riduce). Una bitmap ARGB di 50 MP pesa 200 MB: va bene sul Tab S9 e sui telefoni recenti, e chi ha poca memoria passa un tetto piu' basso.
  - `suspend fun image(context: Context, sheet: FluidInkSheet, strokes: List<FluidInkStroke>, output: OutputStream, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 95, maxPixels: Int = MaxPixels): FluidInkExportResult` — La foto di [sheet] coi [strokes] sopra, in [format].
  - `suspend fun drawing(strokes: List<FluidInkStroke>, output: OutputStream, size: Size? = null, scale: Float = 3f, background: Color? = Color.White, cropToContent: Boolean = false, margin: Float = 12f, format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, quality: Int = 95): FluidInkExportResult` — Il solo disegno: [size] e' il foglio (null: il riquadro dei tratti), [scale] i pixel per unita' (3: un foglio in dp diventa un'immagine alla densita' di un telefono xxhdpi). Con [background] null il fondo e' trasparente (una firma da mettere su un documento, in PNG). Con [cropToContent] l'immagine e' il riquadro dei tratti piu' [margin].
  - `suspend fun pdf(sheet: FluidInkSheet, strokes: List<FluidInkStroke>, output: OutputStream, renderScale: Float = 2f): FluidInkExportResult` — Il PDF annotato: le pagine di [sheet] (un PDF, o il foglio bianco come pagina) coi [strokes] sopra.

## `engine-ink/ink/FluidInkLabels.kt`

- `data class FluidInkLabels(val pen: String = "Penna", val highlighter: String = "Evidenziatore", val pencil: String = "Matita", val monoline: String = "Monolinea", val eraser: String = "Gomma", val lasso: String = "Lazo", val ruler: String = "Righello", val color: String = "Colore", val undo: String = "Annulla", val redo: String = "Ripristina", val done: String = "Fine", val cancel: String = "Chiudi", val more: String = "Altre opzioni", val width: (Int) -> String = { "Spessore ${it + 1} di 5" }, val opacity: String = "Opacità", val objectEraser: String = "Gomma per oggetti", val areaEraser: String = "Gomma a pixel", val delete: String = "Elimina", val duplicate: String = "Duplica", val recolor: String = "Colora", val clearAll: String = "Cancella tutto", val stylusOnly: String = "Disegna solo con la penna", val autoMinimize: String = "Riduci automaticamente", val shapes: String = "Forme perfette", val moveToTop: String = "Sposta in alto", val moveToBottom: String = "Sposta in basso", val expand: String = "Mostra gli strumenti", val discardTitle: String = "Eliminare le modifiche?", val discard: String = "Elimina le modifiche", val keepEditing: String = "Continua", val canvas: (Int) -> String = { n -> when (n) { 0 -> "Foglio da disegno, vuot…, val selected: (Int) -> String = { n -> if (n == 1) "Un tratto selezionato" else…, val rulerAngle: (Int) -> String = { "$it°" }, val loading: String = "Apertura…")` — Le parole del disegno, in italiano di serie (le app le traducono passando le loro). Sono quelle di Markup in italiano dove ci sono («Penna», «Evidenziatore», «Matita», «Gomma», «Lazo», «Righello»), e le dice anche TalkBack.
  - `fun of(tool: FluidInkTool): String` — Il nome di uno strumento.

## `engine-ink/ink/FluidInkLicenses.kt`

- `val FluidInkLicenses: List<FluidLicense>` — Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-ink.

## `engine-ink/ink/FluidInkModel.kt`

- `value class FluidInkTool internal constructor(val id: Int)` — Uno strumento della tavolozza. Una classe di valori invece di un `enum`: gli strumenti di PencilKit sono cresciuti negli anni (il pennarello, il monolinea, la penna stilografica, l'acquerello, il pastello), e uno strumento nuovo qui non deve rompere i `when` delle app.
  - `val isInking: Boolean get()` — Vero per la penna, l'evidenziatore e la matita: gli strumenti che lasciano un tratto.
  - `companion object`
    - `val Pen` — La penna: tratto pieno, piu' largo con la pressione e un filo con la velocita' (`PKInkingTool.InkType.pen`).
    - `val Highlighter` — L'evidenziatore: punta a scalpello, colore trasparente che non si somma con se stesso (`.marker`).
    - `val Pencil` — La matita: tratto sottile e morbido, piu' chiaro se si preme poco e piu' largo se la penna e' inclinata (`.pencil`).
    - `val Monoline` — Il monolinea: lo stesso spessore dall'inizio alla fine, senza pressione ne' velocita' (`.monoline`, iOS 17).
    - `val Eraser` — La gomma: a oggetti (toglie il tratto toccato) o a pixel (toglie solo la parte toccata), [FluidInkEraserMode].
    - `val Lasso` — Il lazo: si gira attorno ai tratti per sceglierli, poi si spostano, si duplicano, si tolgono o si ricolorano.
    - `val inking: List<FluidInkTool>` — Gli strumenti che disegnano.
    - `val all: List<FluidInkTool>` — Tutti gli strumenti, nell'ordine della tavolozza di Markup di iOS 26 e 27 da sinistra (la guida dell'iPhone, «Draw and handwrite with Markup», fiducia alta): penna, monolinea, pennarello (qui l'evidenziatore), gomma, lazo, (il righello), matita.
    - `fun fromId(id: Int): FluidInkTool?` — Lo strumento con questo [id], o null (un salvataggio di una versione piu' nuova).
- `value class FluidInkInput internal constructor(val id: Int)` — Da dove arriva un tratto: il dito, la penna (lo stilo), il mouse. I valori sono quelli di `MotionEvent.TOOL_TYPE_*` e di `androidx.ink.brush.InputToolType`, cosi' la conversione e' un numero.
  - `companion object`
    - `val Unknown`
    - `val Mouse`
    - `val Touch`
    - `val Stylus`
    - `val Synthetic` — Un tratto fatto dall'engine: una forma riconosciuta, una copia, un tratto scritto dall'app.
    - `fun fromId(id: Int): FluidInkInput`
- `value class FluidInkEraserMode internal constructor(val id: Int)` — Come cancella la gomma: il tratto intero toccato ([Object], la gomma «vettoriale» di PencilKit) o solo la parte toccata ([Area]).
  - `companion object`
    - `val Object` — `PKEraserTool(.vector)`: toglie ogni tratto che la gomma tocca («Gomma per oggetti», da scegliere).
    - `val Area` — `PKEraserTool(.bitmap)`: toglie solo la parte toccata, e un tratto tagliato a meta' diventa due. Quella di serie di Markup in iOS 26 e 27 («Erase pixels: scrub over…»; la gomma per oggetti si sceglie toccando di nuovo la gomma).
- `value class FluidInkDrawingPolicy internal constructor(val id: Int)` — Chi disegna. Come `PKCanvasViewDrawingPolicy`: con [Auto] il dito disegna finche' non si usa la penna; dalla prima volta che la penna tocca il foglio disegna solo la penna, e il dito scorre e ingrandisce la pagina («Disegna solo con Apple Pencil», acceso da solo). Con [AnyInput] disegnano tutti; con [StylusOnly] solo la penna, dall'inizio.
  - `companion object`
    - `val Auto`
    - `val AnyInput`
    - `val StylusOnly`
- `value class FluidInkShapeKind internal constructor(val id: Int)` — La forma in cui si e' trasformato un tratto tenuto fermo alla fine (null: un tratto a mano libera).
  - `companion object`
    - `val Line`
    - `val Ellipse`
    - `val Circle`
    - `val Triangle`
    - `val Rectangle`
    - `val Square`
    - `val Polygon` — Un quadrilatero storto, un pentagono, un esagono: i lati diventano dritti e gli angoli restano dove sono.
    - `fun fromId(id: Int): FluidInkShapeKind?`
- `class FluidInkPoints internal constructor(internal val data: FloatArray, val size: Int, val hasPressure: Boolean, val hasTilt: Boolean, val hasOrientation: Boolean)` — I punti di un tratto, impacchettati: per ogni punto la posizione, il tempo dall'inizio del tratto (ms), la pressione (0..1), l'inclinazione della penna (radianti, 0 = dritta sul foglio) e l'orientamento (radianti). Un valore che il dispositivo non da' vale [None] in tutto il tratto ([hasPressure], [hasTilt], [hasOrientation]): androidx.ink vuole le stesse proprieta' su tutti i punti di un tratto.
  - `fun x(index: Int): Float`
  - `fun y(index: Int): Float`
  - `fun time(index: Int): Float` — Il tempo del punto, in ms dall'inizio del tratto.
  - `fun pressure(index: Int): Float`
  - `fun tilt(index: Int): Float`
  - `fun orientation(index: Int): Float`
  - `val bounds: Rect by lazy(LazyThreadSafetyMode.NONE)` — Il riquadro dei centri dei punti (senza lo spessore).
  - `val length: Float by lazy(LazyThreadSafetyMode.NONE)` — La lunghezza del percorso.
  - `fun translated(dx: Float, dy: Float): FluidInkPoints` — Gli stessi punti spostati di ([dx], [dy]).
  - `fun slice(from: Int, to: Int): FluidInkPoints` — I punti da [from] (compreso) a [to] (escluso), coi tempi che ripartono da zero.
  - `fun averagePressure(): Float` — La pressione media, o [None] se il tratto non ne ha.
  - `companion object`
    - `const val Stride: Int` — I valori per punto: x, y, tempo, pressione, inclinazione, orientamento.
    - `const val None: Float` — Il valore di una proprieta' che il dispositivo non da' (lo stesso di `StrokeInput.NO_PRESSURE` di androidx.ink).
    - `val Empty: FluidInkPoints`
    - `fun of(vararg xy: Float): FluidInkPoints` — Punti da coppie x, y, a 8 ms l'uno dall'altro (un tratto scritto dall'app o una prova). Senza pressione ne' inclinazione.
- `class FluidInkPointsBuilder(initialCapacity: Int = 64)` — Costruisce i [FluidInkPoints] di un tratto che arriva: un punto alla volta, senza allocare per punto (la memoria raddoppia quando serve). Pulisce l'ingresso come vuole androidx.ink: un punto nello stesso posto e nello stesso istante del precedente si scarta, un tempo che torna indietro si allinea al precedente, e le proprieta' facoltative valgono per tutto il tratto come il primo punto le ha date.
  - `var size: Int`
  - `val lastX: Float get()`
  - `val lastY: Float get()`
  - `val lastTime: Float get()`
  - `fun add(x: Float, y: Float, timeMillis: Float, pressure: Float = FluidInkPoints.None, tilt: Float = FluidInkPoints.None, orientation: Float = FluidInkPoints.None): Boolean` — Aggiunge un punto; falso se e' stato scartato (doppione).
  - `fun x(index: Int): Float`
  - `fun y(index: Int): Float`
  - `fun time(index: Int): Float`
  - `fun pressure(index: Int): Float`
  - `fun tilt(index: Int): Float`
  - `fun orientation(index: Int): Float`
  - `fun clear()` — Toglie tutto, tenendo la memoria (il prossimo tratto non alloca).
  - `fun build(): FluidInkPoints`
- `class FluidInkStroke(val id: Long, val tool: FluidInkTool, val color: Color, val size: Float, val points: FluidInkPoints, val input: FluidInkInput = FluidInkInput.Unknown, val shape: FluidInkShapeKind? = null)` — Un tratto finito: lo strumento, il colore (con la sua opacita'), lo spessore nelle unita' del foglio, i punti e da dove sono arrivati. [shape] dice se il tratto e' una forma riconosciuta. L'identita' ([id]) resta uguale quando il tratto si sposta col lazo; una gomma a pixel che lo taglia ne fa dei tratti nuovi, con identita' nuove.
  - `val bounds: Rect by lazy(LazyThreadSafetyMode.NONE)` — Il riquadro che il tratto copre, spessore compreso.
  - `fun copy(id: Long = this.id, tool: FluidInkTool = this.tool, color: Color = this.color, size: Float = this.size, points: FluidInkPoints = this.points, input: FluidInkInput = this.input, shape: FluidInkShapeKind? = this.shape): FluidInkStroke`

## `engine-ink/ink/FluidInkPalette.kt`

- `object FluidInkPaletteTags` — I tag delle prove e delle riprese della tavolozza.
  - `const val Palette`
  - `const val Minimized`
  - `const val Ruler`
  - `const val Color`
  - `const val More`
  - `const val Options`
  - `fun tool(tool: FluidInkTool)`
  - `fun width(index: Int)`
- `object FluidInkPaletteDefaults` — Le misure della tavolozza, prese dalla schermata ufficiale di Markup di iOS 26 sull'iPhone (la guida dell'iPhone, 1,24 px per punto, `sessioni/f8-16/riferimenti/`, fiducia media): la barra di vetro a capsula alta 68 pt e larga quasi quanto lo schermo (20 pt per lato), gli strumenti a passo di 37 pt col corpo largo 16, quello scelto 10 pt piu' alto, il pozzetto del colore e il tondo grigio a destra.
  - `val Height: Dp`
  - `val ToolWidth: Dp`
  - `val MaxWidth: Dp` — Su un iPhone la barra e' larga quanto lo schermo meno 20 pt per lato; sugli schermi larghi si ferma qui.
  - `val ToolRaise: Dp`
  - `val MinimizedSize: Dp`
  - `val SideMargin: Dp`
  - `val BottomMargin: Dp`
- `@Composable fun FluidInkPalette(state: FluidInkState, modifier: Modifier = Modifier, labels: FluidInkLabels = FluidInkLabels(), tools: List<FluidInkTool> = FluidInkTool.all, showsRuler: Boolean = true)` — La tavolozza di vetro di Markup (`PKToolPicker` di iOS 26): gli strumenti in fila come penne vere, nell'ordine di Apple (penna, monolinea, evidenziatore, gomma, lazo, righello, matita), la punta col colore scelto, quello scelto piu' alto; il pozzetto del colore; «•••» nel tondo grigio. In cima la maniglia (iOS 27); su uno schermo largo anche i colori veloci in fila.

## `engine-ink/ink/FluidInkRender.kt`

- `object FluidInk` — Le cose del disegno che non sono di un componente: se c'e' la resa di androidx.ink, e i crediti della libreria.
  - `val nativeAvailable: Boolean by lazy` — Vero se la libreria nativa di androidx.ink si carica: sul telefono sempre (c'e' per tutte le ABI di Android), sul computer no (androidx.ink la da' per Linux, non per Windows; e il modulo usa la variante Android). Senza, il foglio disegna i tratti col ripiego dell'engine (un `Path` per tratto, lo spessore con la pressione), con lo stesso modello: gomma, lazo, forme, annulla e la consegna non cambiano.

## `engine-ink/ink/FluidInkSheet.kt`

- `class FluidInkSheet internal constructor(pages: List<Rect>, internal val fitWidth: Boolean, val paper: Color?, internal val image: ImageBitmap?, internal val pdf: InkPdfPages?, internal val adaptive: Boolean, val uri: Uri?, val mimeType: String?)` — Cosa sta sotto i tratti: un foglio bianco, una foto, le pagine di un PDF. Da' le pagine nelle coordinate del foglio (per una foto i suoi pixel, per un PDF i punti tipografici), il modo in cui entrano nella vista e il fondo da disegnare. La consegna ([FluidInkExport]) riparte da qui: la foto intera, il PDF originale.
  - `val pages: List<Rect> get()` — Le pagine, dall'alto in basso, nelle coordinate del foglio.
  - `val size: Size get()` — Il riquadro di tutte le pagine.
  - `companion object`
    - `fun blank(size: Size? = null, paper: Color = Color.White): FluidInkSheet` — Un foglio bianco: [size] nelle sue unita' (per esempio 595 × 842 per un A4 in punti), o null per un foglio grande quanto la vista la prima volta che si misura (uno schizzo, una firma), in dp. La carta e' [paper]: bianca anche in scuro, come un foglio vero (la domanda ad Alessio e' nel rapporto di F8.16).
    - `fun image(image: ImageBitmap, size: IntSize = IntSize(image.width, image.height), uri: Uri? = null, mimeType: String? = null): FluidInkSheet` — Una foto gia' decodificata. [size] e' la misura vera della foto (i tratti stanno nei suoi pixel), se [image] e' una copia ridotta per lo schermo; [uri] e' la foto intera per la consegna.
    - `const val PdfPageGap: Float` — La distanza fra due pagine di un PDF, in punti (a occhio, 25/11/2026, come Anteprima su iPhone).
- `sealed interface FluidInkSheetLoad` — Lo stato dell'apertura di un foglio da un file: in corso, pronto o fallito.
  - `data object Loading : FluidInkSheetLoad`
  - `class Ready(val sheet: FluidInkSheet) : FluidInkSheetLoad`
  - `class Failed(val error: Throwable) : FluidInkSheetLoad`
- `@Composable fun rememberFluidInkImageSheet(uri: Uri, maxDisplaySide: Int = 2560): FluidInkSheetLoad` — Apre la foto [uri] per annotarla: la misura vera (i tratti stanno nei suoi pixel) e una copia ridotta per lo schermo, col verso giusto (EXIF, da Android 9). Fuori dal thread principale. Un indirizzo dato da un'altra app (MyCloud Gallery) si legge finche' il permesso vale: l'annotazione lo apre subito, e la consegna lo rilegge una volta alla fine.
- `@Composable fun rememberFluidInkPdfSheet(uri: Uri): FluidInkSheetLoad` — Apre il PDF [uri] per annotarlo: le pagine in fila, disegnate quando servono. Si chiude quando esce dalla composizione.

## `engine-ink/ink/FluidInkState.kt`

- `data class FluidInkToolStyle(val color: Color, val widthIndex: Int)` — Lo stile di uno strumento che disegna: il colore (con l'opacita') e quale dei cinque spessori.
- `data class FluidInkRuler(val center: Offset, val angle: Float)` — Il righello: dove sta il suo centro (nelle coordinate del foglio) e com'e' girato (radianti, 0 = orizzontale). Si sposta con un dito e gira con due, come in PencilKit; un tratto che comincia vicino a un suo bordo corre dritto.
- `class FluidInkState(initial: List<FluidInkStroke> = emptyList(), tool: FluidInkTool = FluidInkTool.Pen, drawingPolicy: FluidInkDrawingPolicy = FluidInkDrawingPolicy.Auto)` — Lo stato di un disegno: i tratti, la cronologia di annulla e ripristina, la selezione del lazo, lo strumento con i suoi stili, il righello, chi disegna e la vista (zoom e spostamento). Una per foglio; si crea con [rememberFluidInkState], o fuori dalla composizione (in un ViewModel) col costruttore.
  - `var strokes: List<FluidInkStroke> by mutableStateOf(initial)` — I tratti, dal primo (sotto) all'ultimo (sopra).
  - `var revision: Int by mutableIntStateOf(0)` — Cresce a ogni modifica del disegno (anche annulla e ripristina): per sapere se c'e' qualcosa da salvare.
  - `val canUndo: Boolean get()`
  - `val canRedo: Boolean get()`
  - `val isEmpty: Boolean get()`
  - `var tool: FluidInkTool by mutableStateOf(tool)` — Lo strumento scelto. Cambiarlo toglie la selezione del lazo (come in Markup).
  - `fun selectTool(tool: FluidInkTool)`
  - `fun styleOf(tool: FluidInkTool): FluidInkToolStyle` — Lo stile di [tool] (il colore e lo spessore che la tavolozza gli ha dato).
  - `fun setStyle(tool: FluidInkTool, style: FluidInkToolStyle)`
  - `val color: Color get()` — Il colore dello strumento scelto (o della penna, se lo strumento non disegna).
  - `val width: Dp get()` — Lo spessore sullo schermo dello strumento scelto.
  - `var eraserMode: FluidInkEraserMode by mutableStateOf(FluidInkEraserMode.Area)` — La gomma: a pixel (di serie, come Markup di iOS 26) o a oggetti.
  - `var drawingPolicy: FluidInkDrawingPolicy by mutableStateOf(drawingPolicy)` — Chi disegna: [FluidInkDrawingPolicy].
  - `var stylusSeen: Boolean by mutableStateOf(false)` — Vero dalla prima volta che la penna ha toccato il foglio: con [FluidInkDrawingPolicy.Auto] da li' il dito scorre.
  - `val fingerDraws: Boolean` — Vero se adesso il dito disegna (e non scorre): legge [drawingPolicy] e [stylusSeen].
  - `var shapeRecognition: Boolean by mutableStateOf(true)` — Le forme riconosciute tenendo fermo il tratto (di serie accese, come in Note).
  - `var rulerVisible: Boolean by mutableStateOf(false)` — Il righello acceso.
  - `var ruler: FluidInkRuler? by mutableStateOf(null)` — Dove sta il righello (null: al centro della vista, la prima volta che si accende).
  - `var isInteracting: Boolean by mutableStateOf(false)` — Vero mentre un dito o la penna lavora sul foglio (disegna, cancella, gira il lazo, sposta): la tavolozza si fa piccola e il vetro sta fermo.
  - `var zoom: Float by mutableFloatStateOf(1f)` — Lo zoom sopra il foglio intero (1 = tutto il foglio nella vista, fino a [FluidInkDefaults.MaxZoom]).
  - `var pan: Offset by mutableStateOf(Offset.Unspecified)` — Dove sta l'angolo in alto a sinistra del foglio nella vista, in pixel (Unspecified: dove lo mette la vista, centrato o in cima). La vista lo tiene sempre dentro i suoi limiti.
  - `fun resetView()` — Rimette il foglio intero nella vista.
  - `var paletteMinimized: Boolean by mutableStateOf(false)` — La tavolozza ridotta al tondo dello strumento (si riapre toccandola).
  - `var autoMinimize: Boolean by mutableStateOf(false)` — La tavolozza si riduce da sola quando si comincia a disegnare («Riduci automaticamente» di `PKToolPicker`).
  - `var paletteAtTop: Boolean by mutableStateOf(false)` — La tavolozza in alto invece che in basso (la si trascina, o lo si sceglie dal menu).
  - `var selection: Set<Long> by mutableStateOf(emptySet())` — Le identita' dei tratti scelti col lazo.
  - `val hasSelection: Boolean get()`
  - `val selectionBounds: Rect?` — Il riquadro dei tratti scelti (null senza selezione).
  - `fun clearSelection()`
  - `fun selectInLasso(polygon: FloatArray): Boolean` — Sceglie i tratti dentro il lazo [polygon] (coppie x, y nelle coordinate del foglio). Vero se ne ha preso qualcuno.
  - `fun select(ids: Set<Long>)` — Sceglie i tratti con queste identita' (per un'app che li sceglie da se').
  - `fun deleteSelection()` — Toglie i tratti scelti.
  - `fun duplicateSelection(offset: Offset = Offset(16f, 16f))` — Duplica i tratti scelti un po' piu' in la' (16 unita'), e sceglie le copie, come «Duplica» di Markup.
  - `fun recolorSelection(color: Color)` — Ricolora i tratti scelti con [color] (l'evidenziatore tiene la sua trasparenza).
  - `fun moveSelection(dx: Float, dy: Float)` — Sposta i tratti scelti di ([dx], [dy]). Dentro un gesto ([beginEdit]) e' una parte dello stesso passo.
  - `fun newStrokeId(): Long` — Una identita' nuova per un tratto.
  - `fun add(stroke: FluidInkStroke)` — Aggiunge un tratto (in cima).
  - `fun replace(stroke: FluidInkStroke)` — Sostituisce il tratto con l'identita' di [stroke] (una forma che prende il posto del tratto a mano).
  - `fun clear()` — Toglie tutto, in un passo che si annulla.
  - `fun load(strokes: List<FluidInkStroke>)` — Sostituisce il disegno con [strokes] e svuota la cronologia (un disegno riaperto).
  - `fun encode(): ByteArray` — I tratti salvati ([FluidInkCodec]).
  - `fun undo()`
  - `fun redo()`
  - `fun beginEdit()` — Comincia un gesto che cambia il disegno a ogni fotogramma (la gomma, lo spostamento del lazo): fino a [endEdit] le modifiche non entrano nella cronologia una per una, e alla fine sono un passo solo.
  - `fun endEdit()` — Chiude il gesto: se ha cambiato qualcosa, e' un passo della cronologia.
  - `fun cancelEdit()` — Annulla il gesto in corso: il disegno torna com'era al [beginEdit].
  - `fun eraseObjects(a: Offset, b: Offset, radius: Float): Boolean` — La gomma a oggetti lungo il segmento a–b (raggio [radius], nelle unita' del foglio). Vero se ha tolto qualcosa.
  - `fun eraseArea(a: Offset, b: Offset, radius: Float): Boolean` — La gomma a pixel lungo il segmento a–b: taglia i tratti e toglie solo la parte toccata. Vero se ha cambiato qualcosa.
- `@Composable fun rememberFluidInkState(initial: List<FluidInkStroke> = emptyList(), tool: FluidInkTool = FluidInkTool.Pen, drawingPolicy: FluidInkDrawingPolicy = FluidInkDrawingPolicy.Auto): FluidInkState` — Lo stato di un disegno, che sopravvive alla ricomposizione (non alla ricreazione: vedi [FluidInkState]).
