# API · engine-editor

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-editor/editor/EditorAdjustStrip.kt`

- `object FluidImageEditorTags` — I tag delle prove e delle riprese.
  - `const val Photo`
  - `const val Strip`
  - `const val Ruler`
  - `const val Straighten`
  - `const val Cancel`
  - `const val Done`
  - `const val Undo`
  - `const val Redo`
  - `const val ModeAdjust`
  - `const val ModeCrop`
  - `const val Rotate`
  - `const val Flip`
  - `const val Aspect`
  - `const val Reset`
  - `const val More`
  - `const val Label`
  - `const val Split`
  - `fun item(id: String)`
  - `fun aspect(id: String)`

## `engine-editor/editor/FluidAdjustMath.kt`

- `object FluidAutoAdjust` — «Auto», come la bacchetta di «Regola» in Foto: guarda l'istogramma della foto e propone esposizione, punto di nero, luci, ombre, contrasto, vividezza e calore. Apple non dice come (e' un modello suo); qui sono regole semplici sui percentili e sulla media dei colori, a occhio (F8.15), che non spingono mai oltre ±40: «Auto» corregge, non stravolge.
  - `fun suggest(pixels: IntArray): FluidAdjustments` — La proposta per una foto, dai suoi pixel ARGB (una versione ridotta basta: bastano qualche migliaio di pixel).

## `engine-editor/editor/FluidAdjustment.kt`

- `class FluidAdjustment private constructor(val id: String, val range: ClosedFloatingPointRange<Float>, internal val index: Int)` — Una regolazione dell'editor: «Esposizione», «Contrasto», «Calore»… Le voci e il loro ordine sono quelli di «Regola» in Foto di iOS 26 (la fila di tasti tondi sotto la foto), senza «Riduzione del rumore», che con un filtro serio costa troppo per un'anteprima a 120 Hz (F8.15: domanda per Alessio).
  - `val positiveOnly: Boolean get()` — Vero per le regolazioni che vanno solo da 0 in su (nitidezza, definizione).
  - `fun clamp(value: Float): Float` — Porta [value] dentro [range], al passo intero del righello di Foto.
  - `companion object`
    - `val Exposure` — La luce, in stop: la moltiplicazione nella luce lineare di `CIExposureAdjust` (±100 = ±1,5 stop, a occhio).
    - `val Brilliance` — «Brillantezza»: schiarisce le ombre, recupera le luci e aggiunge un po' di contrasto, insieme.
    - `val Highlights` — «Luci»: la parte alta della curva. In negativo recupera i cieli bruciati.
    - `val Shadows` — «Ombre»: la parte bassa della curva. In positivo apre i neri chiusi.
    - `val Contrast` — «Contrasto»: la curva a S attorno al grigio medio.
    - `val Brightness` — «Luminosità»: i toni medi, lasciando fermi il nero e il bianco.
    - `val BlackPoint` — «Punto di nero»: in positivo i grigi scuri diventano neri, in negativo il nero si alza.
    - `val Saturation` — «Saturazione»: tutti i colori; −100 e' il bianco e nero.
    - `val Vibrance` — «Vividezza»: i colori spenti piu' di quelli gia' vivi (`CIVibrance`).
    - `val Warmth` — «Calore»: il bilanciamento del bianco fra blu e arancio, a luminosita' costante.
    - `val Tint` — «Tinta»: il bilanciamento fra verde (negativo) e magenta (positivo).
    - `val Sharpness` — «Nitidezza»: la maschera di contrasto a raggio piccolo, sulla luminanza.
    - `val Definition` — «Definizione»: il contrasto locale a raggio grande (2% del lato corto), sulla luminanza.
    - `val Vignette` — «Vignettatura»: in positivo scurisce i bordi, in negativo li schiarisce.
    - `val All: List<FluidAdjustment>` — Tutte, nell'ordine della fila di Foto.
    - `fun byId(id: String): FluidAdjustment?` — La regolazione col nome [id], o null (una ricetta scritta da una versione futura).
- `class FluidAdjustments private constructor(private val values: FloatArray)` — I valori di tutte le regolazioni, immutabili: una foto appena aperta ha [Neutral]. Si legge con `values[Exposure]`, si cambia con [with] (che da' un oggetto nuovo). Due oggetti coi valori uguali sono uguali.
  - `operator fun get(adjustment: FluidAdjustment): Float`
  - `fun with(adjustment: FluidAdjustment, value: Float): FluidAdjustments` — Gli stessi valori con [adjustment] a [value] (portato dentro l'intervallo e al passo intero).
  - `val isNeutral: Boolean get()` — Vero se nessuna regolazione si discosta dal neutro: la foto esce coi colori di partenza.
  - `val changed: List<FluidAdjustment> get()` — Le regolazioni non neutre, nell'ordine di [FluidAdjustment.All].
  - `companion object`
    - `val Neutral` — Tutte a zero.
    - `fun of(vararg pairs: Pair<FluidAdjustment, Float>): FluidAdjustments` — Dai valori per nome; i nomi sconosciuti si ignorano.

## `engine-editor/editor/FluidCropGeometry.kt`

- `class FluidCropAspect private constructor(val id: String, val width: Int, val height: Int, val round: Boolean = false)` — Le proporzioni del ritaglio, come la fila di «Ritaglia» in Foto di iOS 26 (Libero, Originale, Quadrato, 16:9, 4:3…, con il tasto che gira fra verticale e orizzontale), più **Tondo** per la foto di un account (la scheda dell'idea): un quadrato con la maschera tonda nell'editor. Il file che esce resta quadrato: il tondo lo disegna chi mostra l'avatar.
  - `val isFreeform: Boolean get()` — Vero se il rapporto e' libero: le maniglie spostano ogni lato da solo.
  - `fun ratio(imageRatio: Float): Float?` — Il rapporto larghezza/altezza, per una foto orientata di proporzioni [imageRatio]; null se libero.
  - `fun rotated(): FluidCropAspect` — Lo stesso rapporto girato (16:9 → 9:16); i quadrati e il libero restano se stessi.
  - `fun isPortrait(imageRatio: Float): Boolean` — Vero se il rapporto e' verticale (piu' alto che largo); [Original] lo decide la foto.
  - `companion object`
    - `val Freeform`
    - `val Original`
    - `val Square`
    - `val Circle` — Il quadrato con la maschera tonda (la foto di un account).
    - `val W16x9`
    - `val W5x4`
    - `val W7x5`
    - `val W4x3`
    - `val W5x3`
    - `val W3x2`
    - `val Presets: List<FluidCropAspect>` — La fila di serie, nell'ordine di Foto (Libero, Originale, Quadrato, poi i rapporti), con il tondo dopo il quadrato.
    - `fun custom(width: Int, height: Int, round: Boolean = false): FluidCropAspect` — Un rapporto dell'app (per esempio la forma di un widget, 2:1).
- `data class FluidCropGeometry(val quarterTurns: Int = 0, val flipped: Boolean = false, val angle: Float = 0f, val centerX: Float = 0.5f, val centerY: Float = 0.5f, val width: Float = 1f, val height: Float = 1f, val aspect: FluidCropAspect = FluidCropAspect.Freeform)` — La geometria della modifica: le rotazioni di 90°, lo specchio, il raddrizzamento e il ritaglio. **Tutto in frazioni**, cosi' la stessa geometria vale sull'anteprima ridotta e sul file a piena risoluzione.
  - `val isIdentity: Boolean` — Vero se la geometria lascia la foto com'e' (le proporzioni scelte non contano finche' il ritaglio e' intero).
  - `fun orientedSize(sw: Int, sh: Int): Pair<Int, Int>` — Le misure di O per una sorgente di [sw] × [sh].
  - `fun outputSize(sw: Int, sh: Int): Pair<Int, Int>` — Le misure in pixel del file che esce da una sorgente di [sw] × [sh] (almeno 1 × 1).
  - `fun outputRatio(sw: Int, sh: Int): Float` — Il rapporto larghezza/altezza del ritaglio per una sorgente di [sw] × [sh].
  - `fun rotatedLeft(): FluidCropGeometry` — Gira tutto di 90° in senso antiorario (il tasto di Foto): la foto, il ritaglio e le proporzioni scelte. Il raddrizzamento resta, perche' e' relativo alla cornice.
  - `fun flippedHorizontally(): FluidCropGeometry` — Lo specchio orizzontale: il ritaglio si specchia con la foto e il raddrizzamento cambia verso.
  - `fun fitted(sw: Int, sh: Int): FluidCropGeometry` — Lo stesso ritaglio stretto attorno al suo centro quanto basta per stare dentro la foto girata di [angle] (mai allargato). Il centro, se e' troppo vicino al bordo, rientra.
  - `fun largestWithRatio(ratio: Float, sw: Int, sh: Int): FluidCropGeometry` — Il ritaglio piu' grande con le proporzioni [ratio] (larghezza/altezza in pixel) che sta nella foto girata di [angle], centrato il piu' vicino possibile al centro di adesso.
  - `fun reset(sw: Int, sh: Int): FluidCropGeometry` — Il ritaglio piu' grande nella foto, rispettando le proporzioni scelte ([aspect]) e il raddrizzamento.
  - `fun moved(dx: Float, dy: Float, sw: Int, sh: Int): FluidCropGeometry` — Sposta il ritaglio di ([dx], [dy]) pixel di O, senza farlo uscire dalla foto girata.
  - `fun sourceToOutput(sw: Int, sh: Int): FluidAffine` — La matrice dalla sorgente (pixel di S, larga [sw] e alta [sh]) al file che esce (pixel del ritaglio, [outputSize]). In forma affine: `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty`.
- `data class FluidAffine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float)` — Una trasformazione affine del piano, `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty` (le colonne di `android.graphics.Matrix`: `a` = MSCALE_X, `c` = MSKEW_X, `b` = MSKEW_Y, `d` = MSCALE_Y). Pura, per i conti della geometria e le prove.
  - `fun mapX(x: Float, y: Float)`
  - `fun mapY(x: Float, y: Float)`
  - `fun then(other: FluidAffine): FluidAffine` — La composizione `this ∘ other`: prima [other], poi questa (`a.then(b)` applicato a p e' `a(b(p))`).
  - `fun inverted(): FluidAffine` — L'inversa (la matrice non e' mai singolare: rotazioni, specchi, scale e traslazioni).
  - `fun toMatrixValues(): FloatArray` — I nove valori di `android.graphics.Matrix.setValues`.
  - `companion object`
    - `val Identity`

## `engine-editor/editor/FluidEditorImage.kt`

- `class FluidEditorSource private constructor(internal val uri: Uri?, internal val bitmap: Bitmap?)` — Da dove viene la foto da modificare: un indirizzo (`content://` del selettore di `engine-media`, di MyCloud Gallery, del Photo Picker di Android, o un `file://`) oppure una bitmap che l'app ha gia' in memoria (gia' girata per il verso giusto).
  - `companion object`
    - `fun uri(uri: Uri): FluidEditorSource`
    - `fun bitmap(bitmap: Bitmap): FluidEditorSource`

## `engine-editor/editor/FluidImageEdit.kt`

- `data class FluidImageEdit(val geometry: FluidCropGeometry = FluidCropGeometry(), val adjustments: FluidAdjustments = FluidAdjustments.Neutral, val autoAdjustments: FluidAdjustments? = null)` — La **ricetta** di una modifica: la geometria e le regolazioni, non i pixel. Il file di partenza non si tocca mai; la foto modificata e' sempre un file nuovo, fatto da questa ricetta ([FluidImageExport]). Salvata accanto al file ([encode]), riapre la modifica dov'era, e «Ripristina originale» e' semplicemente [Original] — come in Foto, dove una foto modificata si riporta all'originale anche dopo mesi.
  - `val isOriginal: Boolean get()` — Vero se la ricetta lascia la foto com'e'.
  - `val isAuto: Boolean get()` — Vero se «Auto» e' acceso.
  - `fun with(adjustment: FluidAdjustment, value: Float): FluidImageEdit` — La stessa ricetta con [adjustment] a [value] (il totale che mostra il righello).
  - `fun withAuto(suggestion: FluidAdjustments?): FluidImageEdit` — «Auto» acceso con i valori [suggestion] sommati a quelli fatti a mano, o spento togliendo solo la sua parte.
  - `fun encode(): String` — La ricetta in una riga di testo, da salvare accanto al file (un campo del database dell'app, un file `.fluidedit`). Una versione futura dell'engine la rilegge; una regolazione che questa versione non conosce si ignora.
  - `companion object`
    - `val Original` — La foto com'e': nessuna modifica.
    - `fun decode(text: String): FluidImageEdit?` — Rilegge una ricetta scritta da [encode]; null se il testo non e' una ricetta.
- `class FluidEditHistory(initial: FluidImageEdit = FluidImageEdit.Original, private val limit: Int = 100)` — Annulla e ripristina: la cronologia delle ricette, un passo per gesto finito (un righello lasciato, una maniglia lasciata, un tasto), non un passo per fotogramma. Come in Foto, «Ripristina originale» e' un passo come gli altri: si annulla.
  - `var current: FluidImageEdit` — La ricetta di adesso.
  - `val canUndo: Boolean get()`
  - `val canRedo: Boolean get()`
  - `fun commit(edit: FluidImageEdit): Boolean` — Un passo nuovo; se e' uguale a quello di adesso non succede niente. Toglie i passi da ripristinare.
  - `fun undo(): FluidImageEdit?`
  - `fun redo(): FluidImageEdit?`

## `engine-editor/editor/FluidImageEditTool.kt`

- `class FluidImageEditTool internal constructor()` — Il legame fra il visore dei file e l'editor: tiene lo stato dell'editor aperto, cosi' «Fine» del visore sa che ricetta dare all'app. Uno per visore, con [rememberFluidImageEditTool].
  - `var state: FluidImageEditorState? by mutableStateOf(null)` — Lo stato dell'editor aperto nel visore, o null se lo strumento e' chiuso.
- `@Composable fun rememberFluidImageEditTool(): FluidImageEditTool` — Il legame per un visore dei file.
- `fun FluidViewerToolsScope.fluidImageEditTool(tool: FluidImageEditTool, source: (index: Int) -> FluidEditorSource?, initial: (index: Int) -> FluidImageEdit = { FluidImageEdit.Original }, label: String = "Modifica", strings: FluidImageEditorStrings = FluidImageEditorStrings(), aspects: List<FluidCropAspect> = FluidCropAspect.Presets, id: String = "fluid-image-edit", onDone: (index: Int, edit: FluidImageEdit) -> Unit)` — **«Modifica» come strumento del visore dei file** (la scheda dell'idea: il visore ha il posto per gli strumenti). Il tasto compare nella capsula in basso del visore; scelto, la pagina diventa l'editor (la sua seconda riga, la foto, la ruota o la cornice, le schede) e la capsula del visore tiene «Fine». **«Fine» chiama [onDone]** con la ricetta; indietro chiude lo strumento senza salvare, come «Annulla».

## `engine-editor/editor/FluidImageEditor.kt`

- `@Composable fun FluidImageEditor(state: FluidImageEditorState, onCancel: () -> Unit, onDone: (FluidImageEdit) -> Unit, modifier: Modifier = Modifier, strings: FluidImageEditorStrings = FluidImageEditorStrings(), aspects: List<FluidCropAspect> = FluidCropAspect.Presets, onMarkup: (() -> Unit)? = null)` — L'**editor delle foto**, come «Modifica» di Foto in iOS 26 (idea `ritaglia-e-regola`, F8.15): una pagina intera con - in alto «Annulla» e «Fine»; sotto, annulla e ripristina (o, in «Ritaglia», specchio e rotazione di 90°), il titolo della scheda, le proporzioni, «Annota» se l'app la offre ([onMarkup]) e «•••» (Ripristina originale, il confronto a tendina);

## `engine-editor/editor/FluidImageEditorState.kt`

- `class FluidEditorMode private constructor(val id: String)` — Le schede dell'editor, come la barra in basso di Foto. Non un enum (la regola 3.11): domani «Filtri».
  - `companion object`
    - `val Adjust` — «Regola»: la ruota delle regolazioni e il righello.
    - `val Crop` — «Ritaglia»: la cornice, le proporzioni, la rotazione e il raddrizzamento.
- `class FluidImageEditorState internal constructor(val source: FluidEditorSource, val initial: FluidImageEdit, restored: FluidImageEdit, private val appContext: Context)` — Lo stato dell'editor: la ricetta di adesso ([edit]), la cronologia per annulla e ripristina, la scheda, la regolazione scelta, il confronto. Si crea con [rememberFluidImageEditorState]; l'app lo tiene per tutta la modifica (sopravvive alla rotazione dello schermo: la ricetta, la scheda e la regolazione si salvano, la cronologia no).
  - `var edit: FluidImageEdit by mutableStateOf(restored)` — La ricetta di adesso, anche a meta' di un gesto. Letta nel disegno, non ricompone l'editor a ogni fotogramma.
  - `var mode: FluidEditorMode by mutableStateOf(FluidEditorMode.Adjust)` — La scheda di adesso.
  - `var selected: FluidAdjustment? by mutableStateOf<FluidAdjustment?>(FluidAdjustment.Exposure)` — La regolazione sotto il righello (quella al centro della ruota); null se al centro c'e' «Auto».
  - `var showingOriginal: Boolean by mutableStateOf(false)` — Vero mentre il dito tiene premuta la foto: si vede l'originale.
  - `var splitCompare: Boolean by mutableStateOf(false)` — Il confronto a tendina: a sinistra senza regolazioni, a destra con.
  - `var aspectPanel: Boolean by mutableStateOf(false)` — Vero mentre e' aperta la fila delle proporzioni (in «Ritaglia»).
  - `var interacting: Boolean by mutableStateOf(false)` — Vero mentre un dito muove qualcosa (il righello, la ruota, la cornice): l'anteprima e' quella veloce.
  - `var loadError: Throwable? by mutableStateOf<Throwable?>(null)` — Perche' la foto non si e' aperta, o null.
  - `val isReady: Boolean get()` — Vero quando la foto e' pronta (l'anteprima e' caricata).
  - `val sourceSize: Pair<Int, Int>? get()` — Le misure vere della foto (dritta), o null finche' non e' pronta.
  - `val canUndo: Boolean get()`
  - `val canRedo: Boolean get()`
  - `val hasChanges: Boolean get()` — Vero se la ricetta e' diversa da quella di partenza: «Annulla» chiede conferma.
  - `fun setAdjustment(adjustment: FluidAdjustment, value: Float)` — Cambia [adjustment] durante un gesto (il righello): niente cronologia finche' non arriva [commit].
  - `fun toggleAuto()` — «Auto»: acceso aggiunge la proposta per questa foto, spento la toglie lasciando le modifiche a mano.
  - `fun commit()` — Scrive il passo nella cronologia (a gesto finito, o dopo un tasto).
  - `fun undo()`
  - `fun redo()`
  - `fun revertToOriginal()` — «Ripristina originale»: la foto com'era, come passo che si puo' annullare.
  - `fun rotateLeft()` — Gira a sinistra di 90°, come il tasto di Foto.
  - `fun flip()` — Lo specchio orizzontale.
  - `fun setAspect(aspect: FluidCropAspect)` — Blocca le proporzioni [aspect]: il ritaglio diventa il piu' grande con quelle proporzioni.
  - `fun setAspectPortrait(portrait: Boolean)` — Gira le proporzioni scelte fra verticale e orizzontale (il tasto accanto alla fila di Foto).
  - `fun resetGeometry()` — «Reimposta»: il ritaglio intero, dritto, senza rotazioni e senza specchio.
  - `fun setStraighten(degrees: Float)` — Il raddrizzamento durante un gesto: il ritaglio si stringe dalla cornice di quando il gesto e' cominciato.
  - `suspend fun render(maxSide: Int = 2048): Bitmap` — La foto modificata in memoria, col lato lungo al piu' [maxSide], per un'anteprima veloce (il passaggio al disegno di `engine-ink` usa invece un file PNG nella cache: [export]). Fuori dal thread principale; la bitmap e' di chi chiama.
  - `suspend fun export(target: File, format: FluidImageExport.Format = FluidImageExport.Format.jpeg(), keepLocation: Boolean = true): FluidImageExport.Result` — Salva la foto modificata a piena risoluzione in [target] (un file nuovo: l'originale non si tocca).
  - `suspend fun export(target: Uri, format: FluidImageExport.Format = FluidImageExport.Format.jpeg(), keepLocation: Boolean = true): FluidImageExport.Result` — Lo stesso in un indirizzo aperto in scrittura (MediaStore, «Salva come»).
- `@Composable fun rememberFluidImageEditorState(source: FluidEditorSource, initial: FluidImageEdit = FluidImageEdit.Original, previewMaxSide: Int = 0): FluidImageEditorState` — Lo stato di un editor per la foto [source], aperto con la ricetta [initial] (di serie la foto com'e'; una ricetta salvata riapre la modifica). L'anteprima si carica fuori dal thread principale, col lato lungo grande quanto lo schermo (al piu' [previewMaxSide]).

## `engine-editor/editor/FluidImageEditorStrings.kt`

- `data class FluidImageEditorStrings(val cancel: String = "Annulla", val done: String = "Fine", val adjust: String = "Regola", val crop: String = "Ritaglia", val undo: String = "Annulla l'ultima modifica", val redo: String = "Ripristina", val more: String = "Altro", val revert: String = "Ripristina originale", val splitCompare: String = "Confronto a tendina", val splitCompareOff: String = "Chiudi il confronto", val markup: String = "Annota", val auto: String = "Auto", val autoOn: String = "Auto acceso", val autoOff: String = "Auto spento", val original: String = "Originale", val edited: String = "Modificata", val reset: String = "Reimposta", val rotate: String = "Ruota a sinistra", val flip: String = "Specchia", val aspect: String = "Proporzioni", val straighten: String = "Raddrizza", val portrait: String = "Verticale", val landscape: String = "Orizzontale", val loading: String = "Apertura della foto…", val loadFailed: String = "Impossibile aprire la foto", val discardTitle: String = "Annullare le modifiche?", val discard: String = "Annulla modifiche", val keepEditing: String = "Continua a modificare", val cropFrame: String = "Ritaglio", val zoomIn: String = "Stringi il ritaglio", val zoomOut: String = "Allarga il ritaglio", val photo: String = "Foto", val holdToCompare: String = "Tieni premuto per vedere l'originale", val adjustmentName: (FluidAdjustment) -> String = ::italianName, val aspectName: (FluidCropAspect) -> String = ::italianAspect, val degrees: (Float) -> String = { v -> "${if (v < 0) "−" else ""}${kotlin.math.…)` — Le parole dell'editor, in italiano di serie: quelle di Foto in iOS 26 in italiano dove ci sono («Annulla», «Fine», «Regola», «Ritaglia», «Ripristina originale»), le altre scritte allo stesso modo. Un'app in un'altra lingua le passa tutte.

## `engine-editor/editor/FluidImageExport.kt`

- `object FluidImageExport` — Il salvataggio: la foto modificata come **file nuovo**, a piena risoluzione, fuori dal thread principale. Il file di partenza non si tocca; dove salvare (accanto all'originale, una copia nella galleria, la cache per un allegato) lo decide l'app.
  - `class Format private constructor(internal val compress: Bitmap.CompressFormat, val quality: Int, val mimeType: String)` — Il formato del file che esce.
    - `companion object`
  - `data class Result(val width: Int, val height: Int, val reduced: Boolean, val bytes: Long, val mimeType: String)` — Com'e' uscito il file: le misure, se e' stato ridotto per la memoria ([reduced]), il peso e il tipo.
  - `fun defaultMaxPixels(context: Context): Long` — Il tetto di pixel del file che esce, secondo la memoria del telefono: 12, 24 o 50 megapixel.
  - `suspend fun export(context: Context, source: FluidEditorSource, edit: FluidImageEdit, target: File, format: Format = Format.jpeg(), maxPixels: Long = defaultMaxPixels(context), keepLocation: Boolean = true): Result` — Salva la foto [source] modificata da [edit] nel file [target] (sovrascritto).
  - `suspend fun export(context: Context, source: FluidEditorSource, edit: FluidImageEdit, target: Uri, format: Format = Format.jpeg(), maxPixels: Long = defaultMaxPixels(context), keepLocation: Boolean = true): Result` — Salva in un indirizzo dell'app ([target], aperto in scrittura: un file del MediaStore appena creato, un documento scelto con «Salva come»). Passa da un file nella cache, per poter scrivere i metadati.
  - `suspend fun render(context: Context, source: FluidEditorSource, edit: FluidImageEdit, maxSide: Int): Bitmap` — La foto modificata in memoria, col lato lungo al piu' [maxSide]: per passarla a un'altra fase senza un file (il disegno di `engine-ink`, un'anteprima di condivisione). La bitmap e' di chi chiama.

## `engine-editor/editor/icons/FluidIconsFilled.kt`

- `val FluidIcons.Filled.AutoFixHigh: ImageVector` — `auto_fix_high`, pieno. Nel set per: «Auto», la bacchetta; piena quando e' accesa.

## `engine-editor/editor/icons/FluidIconsOutline.kt`

- `val FluidIcons.AspectRatio: ImageVector` — `aspect_ratio`, al tratto. Nel set per: le proporzioni.
- `val FluidIcons.AutoFixHigh: ImageVector` — `auto_fix_high`, al tratto. Nel set per: «Auto», la bacchetta; piena quando e' accesa.
- `val FluidIcons.Brightness1: ImageVector` — `brightness_1`, al tratto. Nel set per: punto di nero.
- `val FluidIcons.Brightness6: ImageVector` — `brightness_6`, al tratto. Nel set per: luminosita'.
- `val FluidIcons.ChangeHistory: ImageVector` — `change_history`, al tratto. Nel set per: nitidezza.
- `val FluidIcons.Compare: ImageVector` — `compare`, al tratto. Nel set per: il confronto a tendina.
- `val FluidIcons.Contrast: ImageVector` — `contrast`, al tratto. Nel set per: contrasto. Si gira da sola nelle lingue da destra a sinistra.
- `val FluidIcons.Crop: ImageVector` — `crop`, al tratto. Nel set per: la scheda «Ritaglia».
- `val FluidIcons.CropLandscape: ImageVector` — `crop_landscape`, al tratto. Nel set per: le proporzioni in orizzontale.
- `val FluidIcons.CropPortrait: ImageVector` — `crop_portrait`, al tratto. Nel set per: le proporzioni in verticale.
- `val FluidIcons.DarkMode: ImageVector` — `dark_mode`, al tratto. Nel set per: ombre.
- `val FluidIcons.Details: ImageVector` — `details`, al tratto. Nel set per: definizione.
- `val FluidIcons.Draw: ImageVector` — `draw`, al tratto. Nel set per: «Annota», il passaggio al disegno di engine-ink.
- `val FluidIcons.Exposure: ImageVector` — `exposure`, al tratto. Nel set per: esposizione.
- `val FluidIcons.Flare: ImageVector` — `flare`, al tratto. Nel set per: brillantezza.
- `val FluidIcons.Flip: ImageVector` — `flip`, al tratto. Nel set per: lo specchio orizzontale.
- `val FluidIcons.Gradient: ImageVector` — `gradient`, al tratto. Nel set per: saturazione.
- `val FluidIcons.InvertColors: ImageVector` — `invert_colors`, al tratto. Nel set per: vividezza.
- `val FluidIcons.LightMode: ImageVector` — `light_mode`, al tratto. Nel set per: luci.
- `val FluidIcons.Rotate90DegreesCcw: ImageVector` — `rotate_90_degrees_ccw`, al tratto. Nel set per: ruota a sinistra di 90 gradi.
- `val FluidIcons.Straighten: ImageVector` — `straighten`, al tratto. Nel set per: il raddrizzamento.
- `val FluidIcons.Thermostat: ImageVector` — `thermostat`, al tratto. Nel set per: calore. Si gira da sola nelle lingue da destra a sinistra.
- `val FluidIcons.Vignette: ImageVector` — `vignette`, al tratto. Nel set per: vignettatura.
- `val FluidIcons.WaterDrop: ImageVector` — `water_drop`, al tratto. Nel set per: tinta.
