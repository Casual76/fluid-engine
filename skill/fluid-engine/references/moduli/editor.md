# engine-editor

**Modifica delle immagini** (4.0, F8.15, `ritaglia-e-regola` e `confronto-prima-dopo`): l'editor delle foto come «Modifica»
di Foto in iOS 26. Ritaglio con le proporzioni e la cornice che resta dentro la foto, rotazione di 90° e libera (il
righello di «Raddrizza» e due dita), specchio; le regolazioni (esposizione, brillantezza, luci, ombre, contrasto,
luminosità, punto di nero, saturazione, vividezza, calore, tinta, nitidezza, definizione, vignettatura) con la ruota delle
regolazioni e i valori, «Auto», annulla e ripristina; **l'originale sempre recuperabile** e il confronto tenendo premuta la
foto. Pacchetto `dev.antigravity.fluidengine.editor`.

È un **modulo opt-in** su `engine-ui`, **senza librerie nuove** (`Bitmap`, `Canvas`, `BitmapRegionDecoder`,
`ExifInterface` di Android e AGSL da Android 13) e **senza permessi**. Il **confronto a tendina** fra due immagini qualsiasi,
`FluidBeforeAfter`, sta in `engine-ui` (serve anche a chi non modifica niente: [`../componenti/contenuto-testo-e-media.md`](../componenti/contenuto-testo-e-media.md)).

**L'engine** fa l'editor, i gesti, l'anteprima mentre il dito muove, il calcolo dei pixel e il salvataggio in un file nuovo
a piena risoluzione. **L'app** dice quale foto, dove salvare, se tenere la posizione, e se tenere la ricetta per riaprire
la modifica.

Catalogo delle firme: [`../api/editor.md`](../api/editor.md) (generato). Il visore dei file e i suoi strumenti:
[`../componenti/visori.md`](../componenti/visori.md); il righello: [`../componenti/controlli-gesto.md`](../componenti/controlli-gesto.md);
il selettore da cui di solito arriva la foto: [`media.md`](media.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| modificare una foto (la galleria di MyCloud, un allegato prima di mandarlo) | `val state = rememberFluidImageEditorState(FluidEditorSource.uri(uri))` e `FluidImageEditor(state, onCancel = …, onDone = { edit -> … })` in una pagina |
| salvare la foto modificata | `state.export(file)` o `state.export(uri)`, in una coroutine: un **file nuovo**, l'originale resta |
| riaprire una modifica dov'era | `edit.encode()` accanto al file; poi `rememberFluidImageEditorState(source, FluidImageEdit.decode(testo) ?: FluidImageEdit.Original)` |
| la foto dell'account, tonda | `state.setAspect(FluidCropAspect.Circle)` e `aspects = listOf(FluidCropAspect.Circle)`: il file esce quadrato, il tondo lo disegna chi mostra l'avatar |
| la forma di un widget (2:1) | `FluidCropAspect.custom(2, 1)` fra gli `aspects` |
| «Modifica» nel visore dei file | `val edit = rememberFluidImageEditTool()` e `tools = { fluidImageEditTool(edit, source = { i -> … }) { i, ricetta -> … } }` |
| passare la foto modificata al disegno (`engine-ink`) | `onMarkup = { scope.launch { state.export(png, FluidImageExport.Format.Png); annota(Uri.fromFile(png)) } }`: un file PNG nella cache dell'app |
| salvare senza l'editor (una ricetta salvata, un lotto in Universal Converter) | `FluidImageExport.export(context, FluidEditorSource.uri(uri), ricetta, file)` |
| confrontare due versioni della stessa immagine | `FluidBeforeAfter(before = { … }, after = { … })` di `engine-ui`, anche dentro il visore (`FluidViewerContent.Image(ratio) { FluidBeforeAfter(…) }`) |

## L'editor: `FluidImageEditor`

Una **pagina intera** (una rotta dell'app, o un livello sopra), con le misure di Foto in iOS 26 e 27 (le schermate della
guida dell'iPhone, `sessioni/f8-2/riferimenti`, 1,132 px per punto):

- **in alto** «Annulla» e «Fine» (tinta piena, l'azione principale); sotto, una capsula di vetro con annulla e ripristina
  (in «Ritaglia» specchio e rotazione di 90° a sinistra), il titolo della scheda in grigio («REGOLA», o «REIMPOSTA» quando il
  ritaglio è cambiato), le proporzioni (in «Ritaglia»), «Annota» se l'app la offre, e «•••» con «Confronto a tendina» e
  «Ripristina originale»;
- **la foto**: in «Regola» il ritaglio adatto allo spazio, e **tenendola premuta l'originale** (dopo 100 ms, cosi' un
  tocco veloce non lampeggia), con l'etichetta «ORIGINALE»; in «Ritaglia» la cornice con gli angoli a «L» e i segni sui lati,
  la foto fuori velata, la griglia dei terzi mentre il dito muove (fitta mentre si gira);
- **«Regola»**: la **ruota delle regolazioni**, tasti tondi da 50 dp col passo di 69 (la scelta al centro, il valore scritto
  dentro e l'arco dell'accento; prima voce «Auto»), il nome sopra la foto, e il righello (`FluidRulerDial`) da −100 a 100
  col neutro (0…100 per nitidezza e definizione);
- **«Ritaglia»**: il righello di «Raddrizza» (±45°) oppure, col tasto delle proporzioni, la fila delle proporzioni
  (Libero, Originale, Quadrato, Tondo, 16:9, 5:4, 7:5, 4:3, 5:3, 3:2) con verticale e orizzontale;
- **in basso** le schede «Regola» e «Ritaglia» in una capsula di vetro, col triangolo dell'accento sulla scelta.

**I gesti in «Ritaglia».** Una maniglia (entro 26 dp da un angolo o da un lato) tira la cornice, con la foto ferma; le
proporzioni bloccate si rispettano (gli angoli scalano dall'angolo opposto, i lati dal centro dell'altro asse); la cornice
non scende sotto 64 dp e **non esce mai dalla foto girata** (si ferma sul bordo e lascia scorrere l'altro asse). Lasciata,
dopo 450 ms torna a riempire lo spazio con una molla, e la foto si ingrandisce con lei, come in Foto. Un dito altrove
**sposta la foto** sotto la cornice ferma; **due dita la pizzicano** (attorno al punto fra le dita) e **la girano** (il
raddrizzamento, con un `Threshold` passando dallo zero). Il righello di «Raddrizza» stringe la cornice quanto serve per
stare nella foto girata, e la rilascia tornando indietro nello stesso gesto.

**Il gesto «indietro» di Android** (F8.15b, dal tablet). Nel formato del telefono gli angoli della cornice stanno a 24 dp
dal bordo, dentro la fascia da cui Android 10 e oltre fa partire «indietro» da tutti e due i lati: tirare un angolo
chiudeva l'editor. In «Ritaglia» i quattro angoli sono **esclusi dai gesti di sistema** (riquadri da 52 dp, 104 dp per
lato, sotto i 200 che Android concede), solo a riposo; il righello e la ruota restano del sistema ai bordi, come ogni
controllo dell'engine.

**Dito nitido, app sfocata.** Niente si sfoca sotto il dito: la ruota, il righello, la cornice e la foto si muovono nitidi
anche nell'inerzia. Si sfoca solo ciò che cambia da sé: il nome sopra la foto quando cambia regolazione, le icone della
capsula quando cambia scheda (`FluidGlassContentTransition`).

**Annulla e ripristina.** Un passo per **gesto finito** (un righello lasciato, una maniglia lasciata, un tasto), non per
fotogramma. «Ripristina originale» è un passo come gli altri: si annulla. «Annulla» in alto chiede «Annulla modifiche»
(dal tasto, `FluidConfirmationDialog`) solo se c'è qualcosa da perdere; indietro chiude prima le proporzioni e la tendina.

**«Auto».** Guarda l'istogramma dell'anteprima e propone esposizione, punto di nero, luci, ombre, contrasto, vividezza e
calore (mai oltre ±40: corregge, non stravolge). La proposta **si somma** alle modifiche a mano e spegnendo «Auto» si toglie
solo lei (`FluidImageEdit.withAuto`). Apple non dice come lo fa (è un modello suo): le regole sono a occhio, nel KDoc di
`FluidAutoAdjust`.

**TalkBack.** Ogni voce della ruota è un tasto col nome e il valore come stato; il righello è regolabile («imposta»,
«aumenta», «diminuisci»), e ogni azione è un passo della cronologia. La foto ha le azioni «Originale» e «Modificata»; in
«Ritaglia» «Ruota a sinistra», «Specchia», «Stringi il ritaglio», «Allarga il ritaglio», «Reimposta».

**Il movimento ridotto.** La cornice torna al suo posto subito, la rotazione e lo specchio sono istantanei, i margini fra
«Regola» e «Ritaglia» cambiano di colpo; i righelli perdono l'inerzia lunga (`FluidRulerDial`).

## La ricetta: `FluidImageEdit`

La modifica **non sono i pixel**: è una ricetta (`FluidCropGeometry` + `FluidAdjustments`, più la parte di «Auto»). Il file
di partenza non si tocca mai. `encode()` la scrive in una riga (`fluid-edit/1;q=1;f=0;a=-7.5;c=…;r=4:3;adj=exposure:12`);
`FluidImageEdit.decode` la rilegge, anche scritta da una versione futura (le regolazioni sconosciute si ignorano). Cosi'
MyCloud Gallery può fare come Foto: la copia modificata accanto all'originale, e «Ripristina originale» anche dopo mesi.

`FluidCropGeometry` è **in frazioni** (vale sull'anteprima e sul file): le rotazioni di 90° in senso antiorario, lo specchio
orizzontale dopo, il centro e le misure del ritaglio nella foto orientata, l'angolo di raddrizzamento, le proporzioni
bloccate. `sourceToOutput(sw, sh)` dà la matrice dai pixel della foto a quelli del file (per un'app che deve portare un
punto, un volto, un'etichetta nella foto ritagliata).

`FluidAdjustment` **non è un enum** (la regola 3.11): domani una regolazione nuova non rompe il `when` di un'app.

## Come si calcolano i pixel

- **Una formula sola.** Le curve stanno in un posto (`AdjustCurves`, interno): la CPU le mette in tre tabelle da 256
  valori (una per canale), lo shader AGSL dell'anteprima ha il testo scritto **dalle stesse costanti**; la prova
  `FluidImageExportTest` disegna le due strade sugli stessi pixel e le vuole entro 2 livelli su 255.
- **Le fonti.** Dove Core Image ha il filtro pubblico la forma viene da lì (fiducia media): l'esposizione è la
  moltiplicazione nella luce lineare di `CIExposureAdjust`, saturazione e luminanza di `CIColorControls` (Rec. 709), la
  vividezza di `CIVibrance`, calore e tinta come guadagni sui canali (`CITemperatureAndTint`), la nitidezza una maschera
  sulla luminanza (`CISharpenLuminance`), la vignettatura la caduta radiale di `CIVignette`. Quanto sposta ogni regolazione
  a ±100 è a occhio (25 novembre 2026). La definizione è il contrasto locale contro una mappa della luminanza ridotta a 32
  pixel sul lato corto (≈ 2–3% del lato corto, uguale sull'anteprima e sul file).
- **Mentre il dito muove** (da Android 13): lo shader sulla foto ridotta, ricalcolato dalla GPU a ogni fotogramma; cambiano
  solo gli uniform (nessuna allocazione, nessuna ricomposizione: la ricetta si legge nel disegno). **Sotto Android 13**: il
  calcolo sulla CPU su metà risoluzione mentre il dito muove e a risoluzione piena quando si ferma, su `Dispatchers.Default`
  diviso fra i processori, con l'ultimo valore che scarta quello vecchio.
- **Il salvataggio** (`FluidImageExport`): solo la parte del file che il ritaglio usa, letta **a strisce** da 4 megapixel
  (`BitmapRegionDecoder`) e disegnata girata nel risultato; poi le regolazioni **a fasce** di 256 righe. Non c'è mai la foto
  intera due volte in memoria. Il tetto (`defaultMaxPixels`: 12, 24 o 50 megapixel secondo la memoria del telefono) riduce
  il file e lo dice (`Result.reduced`).
- **Cosa resta del file**: il profilo colore (una foto Display P3 esce P3), i metadati di scatto (date, fotocamera,
  obiettivo, esposizione) e la posizione se `keepLocation`; il verso dell'EXIF si scrive normale (la rotazione è nei
  pixel). **Non resta l'HDR** delle foto Ultra HDR: il file esce SDR.

## Lo strumento del visore: `fluidImageEditTool`

Nel visore dei file (`FluidFileViewer`) «Modifica» compare nella capsula in basso; scelto, la pagina diventa l'editor
senza la riga di «Annulla» e «Fine» (indietro e «Fine» sono del visore). **«Fine» chiama `onDone`** con la ricetta (il
gancio `onDone` di `FluidViewerToolsScope.tool`, F8.15); indietro chiude senza salvare.

## Il passaggio al disegno (`engine-ink`, F8.16)

I due moduli non dipendono l'uno dall'altro. **L'accordo con F8.16: si passano l'URI di un file PNG nella cache
dell'app, nei due versi**, sempre un file nuovo:

- **dall'editor al disegno**: «Annota» (`onMarkup`) chiama l'app, che salva la foto modificata in PNG nella cache
  (`state.export(file, FluidImageExport.Format.Png)`, a piena risoluzione, senza perdite) e apre il disegno su quell'URI
  (in `engine-ink`, la foto che il disegno apre dal suo indirizzo);
- **dal disegno all'editor**: il disegno consegna in PNG in un file della cache, e l'editor lo riapre con
  `FluidEditorSource.uri(Uri.fromFile(file))`.

Le annotazioni finiscono nei pixel, come in Foto, e la ricetta dell'editor riparte dalla foto annotata. La cache la
svuota l'app quando il passaggio è finito. `state.render(maxSide)` (una bitmap in memoria) resta per un'anteprima veloce,
non per il passaggio.

## Cosa costa

- **L'anteprima** è una bitmap della misura dello schermo (al più 2560 px sul lato lungo), la mappa della definizione 32 px
  sul lato corto. Lo shader non alloca; la CPU (sotto Android 13) alloca una bitmap per calcolo.
- **Ferma, la pagina non ridisegna**: la foto si ridisegna solo quando cambia la ricetta o la cornice si muove.
- **Mentre il dito muove si ricompone solo ciò che cambia** (F8.15b): ogni parte legge della ricetta solo quello che
  mostra (`derivedStateOf`): la voce della ruota il suo valore, il righello di «Raddrizza» l'angolo, la seconda riga «la
  cornice è cambiata?». Spostare o pizzicare la foto ridisegna la foto, non la testata.
- **Il vetro** della testata e delle schede rifrange **solo il fondo**: la foto, che cambia a ogni fotogramma, non è una
  sorgente del vetro. **F7.24**: il fondo e la foto hanno anche uno strato loro (`graphicsLayer`). Senza, la foto
  ridisegnata a ogni valore del righello faceva riregistrare lo strato in cui stava anche la sorgente, e i cinque vetri
  ricatturavano a ogni fotogramma (sul Tab S9 ~19 passate di disegno a fotogramma sul RenderThread).
- **L'anteprima leggera** (F7.24, **spenta di serie**): se accesa, mentre il dito muove una regolazione, da Android 13
  la foto dello shader si disegna a metà risoluzione (un quarto dei pixel) in uno strato suo e si allarga; al rilascio
  torna piena e nitida. Ferma è quella di sempre, al pixel. `FluidImageEditorDiagnostics.lightPreview` (non API per le
  app) la accende per le misure: si terrà accesa solo se il tablet mostra un guadagno, perché la foto meno nitida sotto
  il dito si vede.
- **La semantica** della ruota e del numero nel tasto aspetta la fine del gesto (`fluidSettledSemantics` e
  `FluidValueText`, scheda `componenti/controlli-gesto.md`): con l'accessibilità accesa niente controllo della pagina a ogni
  tacca.
- **Il salvataggio** tiene in memoria il file che esce più una striscia (4 megapixel) e una fascia di righe.

## Trappole

- **`onDone` non salva.** Dà la ricetta: il file lo scrive l'app con `state.export(…)` (in una coroutine). Così l'app
  sceglie dove (accanto all'originale, la cache per un allegato, il MediaStore).
- **Un indirizzo di un'altra app** (MyCloud Gallery) si legge finché il permesso vale: `export` rilegge il file, quindi
  un'app che tiene l'editor aperto a lungo ne fa prima una copia.
- **Le misure di `FluidCropGeometry` sono della foto orientata**: i metodi vogliono le misure della **sorgente dritta**
  (`sw`, `sh`, già girata come dice l'EXIF), e girano da soli quando ci sono rotazioni di 90°.
- **HDR**: una foto Ultra HDR esce SDR (vedi sopra).
- **Robolectric** disegna lo shader su un `Canvas` di software (la prova lo usa); i decodificatori dei formati speciali
  (HEIF, AVIF) non ci sono, come nei visori.

## Come si prova

- `engine-editor`: `FluidAdjustMathTest` (i numeri su immagini di prova calcolati a mano: esposizione in luce lineare,
  bianco e nero Rec. 709, vividezza, calore a luminanza costante, contrasto, curve che salgono sempre, vignettatura,
  nitidezza sul gradino, definizione, la mappa, «Auto», lo shader con le stesse costanti), `FluidCropGeometryTest` (90° e
  specchio come in Foto, il ritaglio dentro la foto girata con la formula chiusa, proporzioni, la ricetta scritta e
  riletta, annulla e ripristina, «Auto» che si spegne), `FluidImageExportTest` (Robolectric: pixel del ritaglio girato,
  specchio, EXIF del verso, metadati e posizione, originale identico, tetto, strisce uguali alla lettura intera, lo shader
  uguale alla CPU).
- `sample`: `F8p15EditorUiTest` col dito vero (il righello e un passo per gesto, la ruota col tic e TalkBack, «Auto»,
  l'originale tenendo premuto, le maniglie che non escono, il pizzico, lo spostamento e la rotazione a due dita, specchio,
  rotazione, raddrizza, proporzioni, «Reimposta», «Fine» e «Annulla» con la conferma, i pixel della rotazione col movimento
  ridotto e la foto che cambia mentre il dito è giù).
- In galleria: «Elementi nuovi» › F8.15, scene `modifica-foto`, `modifica-subito`, `foto-profilo-tonda`, `visore-modifica`,
  `confronto-tendina` (`sample/…/novita/F8p15Scenes.kt`).

## Dove guardare

| file | cosa |
|---|---|
| `FluidImageEditor.kt` | la pagina dell'editor, la testata, le schede, le proporzioni, la tendina |
| `FluidImageEditorState.kt` | lo stato, la cronologia, i gesti come passi, il caricamento |
| `EditorPhoto.kt` | il disegno della foto e della cornice, i gesti, i tempi (`EditorMotion`) |
| `EditorCropMath.kt` | i conti della cornice: vista adatta, maniglie, pizzico, spostamento |
| `EditorAdjustStrip.kt` | la ruota delle regolazioni e il righello |
| `FluidAdjustment.kt`, `FluidCropGeometry.kt`, `FluidImageEdit.kt` | la ricetta |
| `FluidAdjustMath.kt` | le curve, il calcolo sulla CPU, la definizione, «Auto» |
| `FluidAdjustShader.kt` | lo shader AGSL dell'anteprima |
| `FluidEditorImage.kt`, `FluidImageExport.kt` | la lettura del file e il salvataggio a strisce |
| `FluidImageEditTool.kt` | lo strumento del visore dei file |
| `icons/` | le icone (Material Symbols allo stesso peso; l'elenco in `engine-editor/icons/icone-editor.txt`) |
