<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/ink.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-ink

**Disegno e annotazioni** (4.0, F8.16, `disegno-e-annotazioni`): il foglio su cui si disegna col dito o con la penna e la
tavolozza di vetro che galleggia, come Markup di iOS 26 (PencilKit: `PKCanvasView` e `PKToolPicker`). Si usa da solo (uno
schizzo, una firma) o per annotare una foto o le pagine di un PDF, e consegna un file nuovo. Pacchetto
`dev.antigravity.fluidengine.ink`.

È un **modulo opt-in** su `engine-ui`, con **androidx.ink 1.0.0** (Apache-2.0, `LICENSES/AndroidxInk.md`)
per il tratto a bassa latenza, i pennelli e la resa, e la predizione del tratto di `androidx.input`. Porta la libreria
nativa `libink.so` (circa 1,3 MB per ABI). **Nessun permesso**. I crediti: `FluidInkLicenses` in `fluidLicensesSection`.

**L'engine** fa il tratto (bassa latenza, pressione, inclinazione, palmo), gli strumenti, la gomma e il lazo, le forme,
annulla e ripristina, la tavolozza che si sposta e si riduce, il righello, lo zoom, il salvataggio dei tratti, la resa in
un'immagine o in un PDF nuovo. **L'app** decide da dove arriva la foto o il PDF, dove scrive il file nuovo e cosa farne,
e se tenere i tratti per riaprirli.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un editor a tutto schermo come Markup (la «x», annulla e ripristina, il ✓, la tavolozza) | `FluidInkEditor(state, sheet, onCancel, onDone)` con `val state = rememberFluidInkState()` |
| annotare una foto | `val load = rememberFluidInkImageSheet(uri)` e `FluidInkEditor(state, load, …)`; al ✓ `FluidInkExport.image(context, sheet, state.strokes, output)` |
| annotare (o firmare) un PDF | `rememberFluidInkPdfSheet(uri)`; al ✓ `FluidInkExport.pdf(sheet, state.strokes, output)` |
| uno schizzo su un foglio bianco | `FluidInkSheet.blank()` (grande quanto la vista) o `blank(Size(595f, 842f))` (un A4 in punti), poi `.ready()` per l'editor |
| una firma in un riquadro della pagina | `FluidInkCanvas(state, FluidInkSheet.blank(paper = Color.Transparent), Modifier…)` con `FluidInkDrawingPolicy.AnyInput`; `FluidInkExport.drawing(strokes, output, background = null, cropToContent = true)` |
| il foglio e la tavolozza in un layout mio | `FluidInkCanvas(state, sheet, backdrop = …)` e `FluidInkPalette(state)` sopra, col `LocalGlassBackdrop` del foglio |
| salvare i tratti e riaprirli (una nota di Pampa Notes) | `state.encode()` → byte; `state.load(FluidInkCodec.decode(byte))` |
| sapere se c'è da salvare | `state.revision` cambia a ogni modifica; `state.isEmpty` |
| solo la penna disegna, il dito scorre (sempre) | `state.drawingPolicy = FluidInkDrawingPolicy.StylusOnly`; `AnyInput` per il contrario; `Auto` (di serie) passa da solo alla penna |
| tratti scritti dall'app (una forma, una firma salvata) | `FluidInkStroke(id = state.newStrokeId(), tool, color, size, FluidInkPoints.of(…))` e `state.add(…)` |
| le parole in un'altra lingua | `FluidInkLabels(…)` a editor, foglio e tavolozza |
| **non così**: una `Canvas` di Compose con `detectDragGestures` e un `Path` | latenza di un fotogramma in piu', niente pressione, niente palmo, niente forme; il tratto qui è nel front buffer |
| **non così**: salvare lo stato nel `Bundle` | un disegno grande supera il limite delle transazioni di Android: `encode()` in un file o nel database dell'app |

## Il foglio: `FluidInkCanvas`

- **Chi disegna** (`FluidInkDrawingPolicy`, come `PKCanvasViewDrawingPolicy`). Con `Auto`, di serie, il dito disegna
  finché non arriva la penna; dalla prima volta che la penna tocca il foglio (`state.stylusSeen`) disegna solo la penna, e
  **il dito scorre e ingrandisce la pagina** («Disegna solo con Apple Pencil», acceso da solo). Con la penna sul foglio un
  dito che tocca è il palmo e non fa niente, e se il palmo si appoggia **prima** della punta (succede quasi sempre) il suo
  gesto si lascia appena la punta scende, e disegna la penna; un palmo che il sistema riconosce (Android 13+, `FLAG_CANCELED`) annulla il
  tratto. L'estremità della gomma della penna (`TOOL_TYPE_ERASER`) e il **tasto laterale tenuto premuto** (la S Pen:
  `BUTTON_STYLUS_PRIMARY`) cancellano senza cambiare strumento.
- **Due dita** spostano e ingrandiscono (da 1 a 5 volte il foglio intero). Un secondo dito che arriva entro 250 ms o
  prima che il tratto passi 24 dp trasforma il tratto in un pizzico; dopo, è il palmo. La pagina lanciata scivola (il dito
  l'ha lanciata: nessuna sfocatura); con «Riduci movimento» si ferma dove il dito la lascia.
- **Il tratto** segue il dito con la latenza più bassa del telefono: lo disegna androidx.ink nel front buffer
  (`InProgressStrokesView`), con la predizione di un fotogramma (`MotionEventPredictor`) e la consegna degli eventi senza
  aspettare il fotogramma (`requestUnbufferedDispatch`). Finito, passa al foglio **nello stesso fotogramma** in cui il
  tratto veloce lo lascia (`wetHeld`): niente lampi, niente tratto doppio. Dal dito niente pressione (PencilKit la ignora);
  dalla penna pressione, inclinazione e orientamento.
- **Lo spessore** è quello che si vede sullo schermo quando comincia il tratto: chi ingrandisce scrive più fine. Nel
  modello è nelle unità del foglio (dp di un foglio bianco, pixel di una foto, punti di un PDF).
- **Le forme**: un tratto tenuto fermo alla fine per mezzo secondo (`ShapeHoldMillis`, entro 4 dp) diventa la forma
  perfetta, con un tic e un viaggio di 180 ms dal tratto a mano (senza viaggio con «Riduci movimento»): linea, cerchio,
  ellisse, triangolo, rettangolo, quadrato, poligono di 5–6 lati. La linea segue il dito finché non si alza. Si spengono con
  `state.shapeRecognition = false`.
- **Il righello** (`state.rulerVisible`): si sposta con un dito e gira con due, si ferma ogni 45° con un tic e dice
  l'angolo; un tratto che comincia entro 24 dp dal bordo corre dritto lungo il bordo.
- **La gomma**: a pixel (di serie, come Markup di iOS 26: toglie la parte toccata, e il tratto diventa due) o a oggetti
  (toglie il tratto toccato), `state.eraserMode`. **Il lazo** sceglie i tratti che circonda (almeno metà dei punti dentro); la selezione si
  trascina e ha Elimina, Duplica, Colora sopra di sé.
- **Ogni gesto è un passo** di annulla e ripristina (la gomma che passa, la selezione trascinata), fino a 200 passi.
- **Il vetro**: con `backdrop` il foglio è la sorgente del vetro della tavolozza e delle barre, **ferma mentre si disegna o
  si scorre** (`glassBackdropSource(frozen = …)`): il vetro si aggiorna una volta a gesto finito. Il tratto veloce non passa
  sopra la tavolozza e la barra dell'editor (`inkMasks`): sembra disegnato sotto, come quando è finito.
- **TalkBack**: il foglio dice quanti tratti ha e quanti sono scelti, e ha le azioni Annulla, Ripristina, Elimina.

## La tavolozza: `FluidInkPalette`

Una barra di vetro a capsula alta 68 dp, larga quanto lo schermo meno 12 dp per lato (fino a 560), col piano flottante e la
maniglia in cima (iOS 27): gli strumenti in fila come penne vere, nell'ordine di Markup di iOS 26
e 27 (penna, monolinea, evidenziatore, gomma, lazo, righello, matita: la guida dell'iPhone, «Draw and handwrite with
Markup»), con la punta del colore scelto, quello scelto 10 dp più alto; il pozzetto del colore (F8.7); «•••». Sta in
336 dp, un telefono da 360 dp. Un tocco sullo strumento già scelto apre le sue opzioni, che **nascono dallo strumento**: i cinque spessori,
l'opacità e i cinque colori veloci di iOS; per la gomma, a pixel o a oggetti. «•••»: solo con la penna, riduci
automaticamente, forme perfette, sposta in alto/in basso, cancella tutto. Tenuta premuta e trascinata va in alto o in basso
(`state.paletteAtTop`); ridotta (`state.paletteMinimized`, anche da sola mentre si disegna con `autoMinimize`) è un tondo
con lo strumento scelto. Per TalkBack ogni strumento è un tasto di scelta col suo nome e lo spessore, il righello un
interruttore.

| strumento | pennello di androidx.ink | di serie |
|---|---|---|
| Penna | `StockBrushes.pressurePen()` V1 (più larga con la pressione) | nero, 3,5 dp |
| Monolinea | `StockBrushes.marker()` V1 (lo stesso spessore dall'inizio alla fine) | nero, 3,5 dp |
| Evidenziatore | `StockBrushes.highlighter(SelfOverlap.DISCARD)` (non si somma con se stesso) | giallo a metà, 18 dp |
| Matita | una famiglia dell'engine: più chiara se si preme poco, più larga e chiara inclinata | nero all'86 %, 2 dp |

## La consegna: `FluidInkExport`

Sempre un **file nuovo**, sull'`OutputStream` che dà l'app, fuori dal thread principale.

| funzione | cosa scrive |
|---|---|
| `image(context, sheet, strokes, output, format, quality, maxPixels)` | la foto intera (fino a 50 MP, sopra si riduce) coi tratti alla stessa scala del foglio; il verso dell'EXIF applicato, **senza EXIF** nel file nuovo (posizione e data non escono) |
| `drawing(strokes, output, size, scale, background, cropToContent, margin)` | il solo disegno, sulla carta o trasparente, intero o ritagliato attorno ai tratti (una firma) |
| `pdf(sheet, strokes, output, renderScale)` | un PDF nuovo con le stesse pagine e le stesse misure: la pagina sotto come immagine ad alta risoluzione, i tratti sopra **come vettori** |

## Il contratto con F8.14 e F8.15

La foto arriva dal selettore di `engine-media` come `FluidPickedMedia.uri` (un `content://` leggibile finché il permesso
vale): `rememberFluidInkImageSheet(media.uri)` la apre subito, e `FluidInkExport.image` la rilegge una volta alla fine.
La modifica delle immagini (F8.15, `engine-editor`) e il disegno si passano la foto **per indirizzo**: ognuno consegna un
file nuovo, e chi viene dopo apre quel file. Nessuno dei due scrive sopra l'originale.

## Cosa costa

- La libreria nativa: circa 1,3 MB per ABI nell'APK (4,8 MB con le quattro ABI di un APK universale).
- Un foglio fermo non si ridisegna (i tratti si leggono nel disegno). Mentre si disegna si ridisegna solo il tratto veloce,
  nel suo front buffer; i tratti finiti si fanno una volta (`Stroke` di androidx.ink, tenuti finché il tratto è nel
  disegno). Niente allocazioni per punto: i punti stanno in un `FloatArray` che raddoppia quando serve.
- Una foto annotata a 50 MP vuole 200 MB per la bitmap della consegna: chi ha poca memoria passa un `maxPixels` più basso.

## Trappole

- **Sul computer non c'è la libreria nativa** (androidx.ink la dà per Android e per Linux, non per Windows):
  `FluidInk.nativeAvailable` è falso e il foglio disegna col ripiego dell'engine (un `Path` per tratto). Il modello, la
  gomma, il lazo, le forme e la consegna sono gli stessi: le prove in Robolectric guardano i pixel del ripiego. L'aspetto di
  androidx.ink (pressione, punte) si giudica sul telefono.
- **Robolectric non ha `PdfRenderer` né `PdfDocument`**: le prove del PDF passano pagine finte e un registratore delle
  pagine; il PDF vero si guarda sul tablet.
- **Il PDF annotato è appiattito**: la pagina sotto diventa un'immagine, e il testo del PDF di partenza non si seleziona più
  nella copia. Scriverci dentro davvero vuole una libreria PDF (PdfBox-Android, Apache-2.0), che pesa: è una domanda aperta.
- **`CanvasStrokeRenderer.draw` non applica la matrice al canvas** (la usa solo per la precisione dei bordi): chi disegna
  un tratto di androidx.ink deve metterla sul canvas lui. L'engine lo fa in `drawInStrokeSpace` (F8.16c: senza, sul
  telefono i tratti finiti uscivano piccoli di un fattore pari alla scala e verso l'angolo, e le prove sul computer non lo
  vedevano, perché il ripiego porta i punti da sé). Una prova a 420 dpi guarda che il tratto cada sotto il dito.
- **La matita** usa il costruttore di `BrushFamily` che androidx.ink 1.0 segna come interno (è pubblico dalla 1.1): un
  salto di versione della libreria va provato sulla matita.
- **Il foglio bianco è bianco anche in scuro**, come un foglio vero; la tavolozza e le barre seguono il tema.
- `FluidInkCanvas` dentro una lista che scorre (la firma in `FluidScreen`): il foglio prende il dito che lo tocca, la
  pagina non scorre partendo dal riquadro. Partendo fuori, sì.

## Come si prova

- `engine-ink` (`testDebugUnitTest`): il modello (annulla e ripristina, un gesto un passo, il tetto della cronologia, la
  gomma a oggetti e a pixel, il lazo, duplica, colora), le forme su tratti tremolanti, il salvataggio, la consegna coi
  pixel (la firma trasparente ritagliata, la foto alla sua misura, l'evidenziatore che non si somma, il PDF con ogni tratto
  sulla sua pagina).
- `sample` (`F8p16InkUiTest`): il dito vero e la penna finta (tipo di strumento, pressione, inclinazione, gomma della penna,
  tasto laterale), il dito che scorre dopo la penna, due dita che ingrandiscono, il lazo che sceglie, sposta e toglie, le
  forme tenendo fermo, annulla e ripristina dai tasti, TalkBack, «Riduci movimento», la consegna della scena della foto.
- La galleria: «Elementi nuovi › F8.16» (`--es novita.scene disegno-foglio`, `annota-foto`, `annota-pdf`, `firma`).
- **La latenza sul telefono**: `adb shell setprop log.tag.FluidInkLatency DEBUG`, poi una riga nel log a ogni tratto
  consegnato (`logcat -s FluidInkLatency`): l'ingresso (dal sensore al foglio), la resa (da androidx.ink ai pixel) e il
  totale, con mediana, 95° percentile e massimo. Spenta di serie (non registra né alloca). I dati di resa li dà androidx.ink
  **solo** coi punti passati come `MotionEvent` (la 1.0 non li compila per quelli a gruppi): per questo il tratto veloce
  passa gli eventi veri, tranne lungo il righello (F8.16b). E il disegnatore del front buffer (Android 13+) non stima
  l'istante dei pixel: la resa e il totale arrivano **fino al disegno nel front buffer** (il punto va sullo schermo al
  passaggio successivo della scansione, entro un refresh), e la riga del log lo dice (F8.16c).

## Dove guardare

| file | cosa |
|---|---|
| `FluidInkModel.kt` | strumenti, ingressi, gomme, chi disegna, forme, i punti e il loro costruttore, il tratto |
| `FluidInkState.kt` | lo stato: tratti, cronologia, selezione, stili, righello, vista, tavolozza |
| `FluidInkDefaults.kt` | i numeri, con la fonte e la fiducia |
| `FluidInkCanvas.kt` | il foglio: la vista, i gesti, il tratto veloce, la resa e i disegni sopra |
| `FluidInkPalette.kt` | la tavolozza, le opzioni, gli strumenti disegnati |
| `FluidInkEditor.kt` | l'editor a tutto schermo |
| `FluidInkSheet.kt` | il foglio bianco, la foto, le pagine del PDF |
| `FluidInkExport.kt` | la consegna in PNG, JPEG e PDF |
| `FluidInkShapes.kt`, `FluidInkGeometry.kt` | il riconoscimento delle forme, la gomma e il lazo |
| `FluidInkRender.kt` | androidx.ink (pennelli, tratti, resa) e il ripiego |
| `FluidInkCodec.kt` | il salvataggio dei tratti |
| `FluidInkLabels.kt`, `FluidInkLicenses.kt` | le parole, i crediti |
