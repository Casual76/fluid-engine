<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/vetro-backdrop.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Vetro · la libreria del backdrop

Sotto il materiale (`GlassMaterial.kt`, [vetro.md](vetro.md)) c'è una libreria che disegna davvero: **AndroidLiquidGlass** di
Kyant (`io.github.kyant0:backdrop` v2.0.0), copiata **come sorgente** in `engine-ui/…/ui/glass/` perché l'engine è Android puro e
la libreria è Compose Multiplatform (un artefatto KMP in ogni app è un modo affidabile di rompere un'app alla volta). Contiene
la cattura del fondale, la catena di `RenderEffect`, gli shader AGSL della lente e del bordo, le ombre e le primitive di gesto.
Questa scheda è il suo manuale: cos'è un `Backdrop`, come si registra e si campiona, cosa fanno gli effetti, quali parti sono
**nostre** e cosa abbiamo cambiato rispetto a monte. Un'app che usa l'engine raramente la tocca; serve a chi scrive una superficie
nuova, a chi ha un pannello fatto con un altro motore, a chi deve capire perché una cattura si rifà (o non si rifà).

Modello mentale. Un `Backdrop` è «una cosa che sa disegnarsi nello spazio di un'altra»: di norma una registrazione (`GraphicsLayer`)
che il pannello rigioca traslata di dove sta. `drawBackdrop` registra quel fondale in **un layer del pannello**, ci applica la
catena di effetti come `RenderEffect` del layer, e poi ci disegna sopra superficie e contenuto. L'unica cosa che invalida una cattura
è la **geometria** (il pannello si muove rispetto alle sorgenti, o cambia misura): il contenuto no, perché un `RenderNode` tiene i figli
per riferimento.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| registrare una porzione di UI perché del vetro la rifranga | `rememberLayerBackdrop()` + `Modifier.layerBackdrop(backdrop)`; nell'engine quasi sempre il wrapper `rememberGlassBackdrop()` + `glassBackdropSource` ([vetro.md](vetro.md)) |
| un pannello di vetro con le ottiche dell'engine | `glassSurface` ([vetro.md](vetro.md)), non `drawBackdrop` |
| un pannello di vetro con una ricetta del tutto mia (una goccia, un pomello) | `Modifier.drawBackdrop(backdrop, shape, effects, …)` con la tua catena di effetti |
| la stessa cosa **senza** bordo e ombra | `Modifier.drawPlainBackdrop(…)` |
| impilare due o più fondali (barra sopra pagina) | `rememberCombinedBackdrop(a, b)` sul `Backdrop`; sullo stato dell'engine `rememberCombinedGlassBackdrop(behind, inFront)` |
| un fondale disegnato da me (una copia ripiegata della pista) | `rememberBackdrop(backdrop) { drawBackdrop -> … }` per trasformarne uno; `rememberCanvasBackdrop { … }` per uno da zero |
| un fondale che non c'è | `emptyBackdrop()` (`rememberEmptyGlassBackdrop()` per lo stato) |
| sfocare, saturare, piegare dentro la catena | `blur`, `colorControls`, `vibrancy`, `lens` su `BackdropEffectScope`; l'AGSL tuo con `runtimeShaderEffect` |
| un anello speculare, un'ombra, un'ombra interna | `Highlight`, `Shadow`, `InnerShadow` passati a `drawBackdrop` |
| un controllo che segue il dito senza soglia di trascinamento | `GlassDragAnimation` + `inspectDragGestures` |
| il bagliore che segue il dito sul vetro | `GlassTouchHighlight` |
| sapere se il vetro ricattura troppo | `FluidGlassDiagnostics` |
| il vetro dentro il vetro | niente da fare ([vetro.md](vetro.md)); questa scheda spiega come funziona sotto |
| **non così**: aggiornare la libreria a monte sovrascrivendo i file | si fa il diff sui commenti `Fluid Engine change:` (sezione «Cosa abbiamo cambiato») |
| **non così**: un secondo shader di vetro accanto a `GlassMaterial` | la regola è «nessuno shader di vetro nuovo»; la famiglia SDF di Fluid-physics è la deroga deliberata, nel suo package |

## `Backdrop`: cos'è il fondale

**Cos'è.** Un'interfaccia, `Backdrop`, con un solo metodo da implementare (`drawBackdrop(density, coordinates, layerBlock)`) e
una bandiera, `isCoordinatesDependent` (il fondale ha bisogno di sapere dove sta il pannello). I membri **«Fluid Engine addition»** servono
a non ricatturare per niente e al vetro su vetro:

| membro | a che serve | default |
|---|---|---|
| `layerSources: List<Any>` | le registrazioni che questo fondale rigioca. Un pannello può dire se la sua cattura vale ancora **senza rifarla**: una lista vuota significa «non so dirlo» e il pannello ricattura a **ogni** fotogramma (come ogni fondale prima), con otto pannelli sono otto rigiochi a schermo intero e otto catene per fotogramma, ed è l'intero motivo per cui il cambio scheda scattava | vuota |
| `isReadyToSample()` | il fondale ha qualcosa da disegnare **adesso**? Un `LayerBackdrop` senza coordinate esce da `drawBackdrop` in silenzio; con `sampleOnce` quella cattura vuota restava per tutta la vita del nodo | `true` |
| `stackedBackdrops` (2.13.0) | i fondali che questo disegna, dal fondo in su, se è una pila; altrimenti sé stesso. Solo le pile si appiattiscono: un fondale che ne **trasforma** un altro (`rememberBackdrop`) resta una voce sola, perché il suo disegno è un gesto solo | `listOf(this)` |
| `forEachPart(block)` (2.13.0) | chiama `block` su questo fondale e su ogni fondale di cui è fatto, a ogni profondità. Un wrapper deve **inoltrare** la chiamata, o ciò che avvolge non viene mai registrato | solo sé |

**Le implementazioni.**
- `LayerBackdrop` (`rememberLayerBackdrop(graphicsLayer, onDraw)`): una registrazione. `isCoordinatesDependent = true`. È ciò che registra
  `layerBackdrop`, ciò che rigioca `drawBackdrop`. Il suo `layerCoordinates` usa `neverEqualPolicy`: ogni callback di posizionamento consegna
  **la stessa istanza** di `LayoutCoordinates` (che muta all'interno); con l'uguaglianza strutturale una sorgente che si muove è la scrittura di un
  valore uguale, nessuna lettura in disegno si invalida e un pannello che salta le catture invariate rigioca una registrazione fatta contro la
  **vecchia** posizione (una banda di fondale resta nitida finché qualcos'altro non ridisegna il pannello).
- `CombinedBackdrop` (`rememberCombinedBackdrop` a 2, 3 o n fondali): li disegna in ordine, **dal fondo in su**. Si può tenere la cattura solo se ogni parte lo sa
  dire (`layerSources` vuota se anche una sola lo è); `isReadyToSample` vale quanto il più impreparato.
- `rememberBackdrop(backdrop, onDraw)`: il fondale trasformato. `FluidSwitch`, `FluidSlider` e `FluidScrubber` lo usano per la **copia ripiegata
  della pista** che il pomello rifrange (`scale(...) { drawTrack() }`).
- `rememberCanvasBackdrop(onDraw)`: un fondale disegnato da una lambda, senza coordinate (`isCoordinatesDependent = false`).
- `emptyBackdrop()`: non disegna niente. Non è un caso d'errore: un controllo su contenuto opaco non ha niente da rifrangere e così tiene bordo,
  bagliore e risposta al dito.

## Registrare: `rememberLayerBackdrop` e `layerBackdrop`

```kotlin
@Composable fun rememberLayerBackdrop(
  graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
  onDraw: ContentDrawScope.() -> Unit = DefaultOnDraw,    // di serie: drawContent()
): LayerBackdrop
fun Modifier.layerBackdrop(backdrop: LayerBackdrop, frozen: () -> Boolean = { false }): Modifier
```

**Comportamento.**
- `layerBackdrop` disegna il contenuto **e poi** lo registra nel layer (`recordLayer`). Registrare è una traversata dell'intero sottoalbero:
  il costo scala con il **numero di operazioni di disegno**, non con i pixel, e una lista lanciata paga ogni riga a ogni fotogramma.
- `frozen`: finché `true` il sottoalbero **non** viene registrato di nuovo e il vetro tiene la cattura che ha (il primo fotogramma registra sempre). Tenuto fermo, Compose
  segue il suo percorso ordinario e riusa ogni figlio invariato. Il prezzo è un riflesso che smette di seguire mentre il contenuto scorre.
- Un nodo **riusato** (una lista lazy che ricicla il nodo per un altro elemento) azzera il suo stato (`onReset`): il flag che fa tenere la registrazione apparteneva
  al sottoalbero di prima.
- `LayerBackdrop` sa **chi lo registra** (`ownerNode`, interno) e può essere registrato **solo su richiesta** (`onDemand`, con un conto dei lettori): è così che il materiale che un pannello
  pubblica costa zero finché nessuno lo campiona. Chi registra una **pagina** (`layerBackdrop`) non è mai su richiesta: si registra sempre.
- Il materiale che un pannello pubblica da solo (`materialLayerBackdrop`) è su richiesta per costruzione.

**Trappola.** Non mettere `layerBackdrop` sull'intera schermata (le barre campionano sé stesse); non dimenticare di dipingere lo sfondo **dentro** la regione registrata.

## `drawBackdrop` e `drawPlainBackdrop`

**Cos'è.** La porta bassa: un modificatore che registra il fondale, ci applica `effects`, e disegna `onDrawBehind` → fondale → `onDrawSurface` → contenuto →
`onDrawFront`. `drawBackdrop` aggiunge, sotto, `Highlight`, `Shadow` e `InnerShadow`; `drawPlainBackdrop` no.

**Firma essenziale** (tutti i parametri: [api/vetro.md](../api/vetro.md)).

```kotlin
fun Modifier.drawBackdrop(
  backdrop: Backdrop,
  shape: () -> Shape,
  effects: BackdropEffectScope.() -> Unit,
  highlight: (() -> Highlight?)? = DefaultHighlight,     // Highlight.Default
  shadow: (() -> Shadow?)? = DefaultShadow,              // Shadow.Default
  innerShadow: (() -> InnerShadow?)? = null,
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
  exportedBackdrop: LayerBackdrop? = null,               // pubblica il materiale di questo pannello
  onDrawBehind / onDrawBackdrop / onDrawSurface / onDrawFront,
  backdropScale: Float = 1f,                             // 0,1..1
  sampleOnce: Boolean = false,
  resampleIntervalMillis: Long = 0L,
  backdropScaleFactor: () -> Float = OneScale,
  frozen: () -> Boolean = NeverFrozen,
  heldWhileMoving: () -> Boolean = NeverFrozen,
  nestInContainer: Boolean = true,
): Modifier
```

**Comportamento — la vita di una cattura.** Il nodo (`DrawBackdropNode`) decide a ogni disegno se ricatturare, nell'ordine:

1. **È ancora quella giusta?** Ricattura se: la superficie è sporca (elemento cambiato, fondale diverso, attacco nuovo, padding cambiato), la **misura registrata** cambia, oppure
   — senza `sampleOnce` — le **posizioni relative alle sorgenti** non sono quelle della registrazione. Contano solo le posizioni *relative*: un pannello e il suo fondale che si muovono insieme
   hanno già la cattura giusta (prima, il confronto sulla posizione assoluta faceva rigiocare lo schermo a ogni fotogramma di ogni swipe). Una trasformazione di layer sul pannello (l'inclinazione di
   un controllo premuto) conta, perché lo sposta rispetto alla sorgente. `layerSources` vuota = ricattura sempre.
2. **`sampleOnce`.** La posizione smette di essere un motivo: basta **una cattura valida** (`sampled`). «Valida» = il fondale era pronto.
3. **`resampleIntervalMillis`.** Un pavimento sulla frequenza, ma **mai** per una cattura di misura sbagliata o per un pannello sporco.
4. **`frozen` / `heldWhileMoving`.** Tiene la cattura se c'è, se non è più vecchia di **110 ms** (`HoldRefreshMillis`) e (se il pannello non si è mosso, o `heldWhileMoving`). Uscendo da un
   congelamento si prende **sempre** una cattura fresca: un riflesso tenuto potrebbe altrimenti durare per sempre, perché una pagina ferma non si ridisegna.
5. **Sorgente sparita.** Ricatturare senza sorgente non registra niente (`LayerBackdrop.drawBackdrop` esce senza coordinate): una cattura vuota sotto una tinta traslucida è la pagina cruda e **nitida**
   dentro la sagoma del vetro, un fotogramma di testo leggibile sotto la barra. Quindi si tiene la cattura vecchia fino a **400 ms** (`SourceGoneGraceMillis`: il caso vero dura un fotogramma — un cambio pagina smonta
   la sorgente vecchia e monta la nuova — ma un fotogramma che compone una pagina intera può durare 80 ms), poi basta: tenere un'immagine ormai falsa a tempo indeterminato è peggio di un vetro vuoto.

**Il padding è l'unica cosa che invalida la catena.** `updateEffects` rifà il `RenderEffect` ogni volta che i numeri cambiano, ma dirty-a la cattura **solo** se cambia il *padding* (il margine di registrazione che
serve alla sfocatura perché i bordi non si sporchino). Una sfocatura che sale con uno scorrimento o una lente che si ispessisce all'arrivo di un modale non costano altro che ricostruire l'effetto. Il
dettaglio del padding sta in `BackdropEffectScope` (sotto).

**Risoluzione di cattura.** `effectiveScale = fitToTexture(backdropScale × quantiseScaleFactor(backdropScaleFactor()), larghezza, altezza)`:
- `backdropScale` (0,1..1) elabora il fondale a una frazione della risoluzione del pannello: **la scala e la densità** dell'effect scope sono moltiplicate insieme, così ogni numero in pixel
  (raggio di sfocatura, spostamento della lente, raggi d'angolo letti dalla sagoma) resta coerente con una sola moltiplicazione. Con un `layerBlock` va lasciata a 1: il blocco è invertito contro la
  densità di questo scope, e una densità scalata lo sposterebbe.
- `quantiseScaleFactor`: sotto **0,7** la scala extra è **0,5** (un quarto dei pixel), altrimenti 1. **Solo due valori** perché la risoluzione decide la misura registrata e cambiarla *è* ricatturare: due
  ricatture per lancio invece di sessanta.
- `fitToTexture`: vedi «Il tetto della texture» in [vetro.md](vetro.md): lato lungo ≤ **4096 px**, pavimento **0,05**.

**Layer e ritaglio.** Il nodo misura il figlio e lo posa con un layer che **ritaglia** (`clip = true`) sulla forma nel suo **equivalente a rettangolo arrotondato** (`fastClipShape`), non sul path esatto: un
`RenderNode` ritagliato su un path generico non usa il clip hardware e fa mascherare l'intero nodo offscreen, per pannello, per fotogramma. Il clip deve solo impedire che l'immagine rifratta trabocchi; l'angolo
che l'occhio legge è la tinta e il bordo disegnati sulla superficie, che tengono la silhouette continua esatta. Una sagoma di Fluid-physics in trasformazione (`GlassClipGeometry`) porta il proprio clip
(il rettangolo del nodo): la silhouette vera la scolpisce l'alpha dello shader. Il compositing del nodo è **`Auto`**, non `Offscreen` come a monte: la pila è tutta `SrcOver` e l'isolamento non cambiava un pixel ma
ri-rasterizzava il pannello (testo compreso) a ogni fotogramma di scorrimento; `Auto` promuove a layer solo dove serve (un `alpha` animato).

**Il materiale pubblicato (`exportedBackdrop`).** Una superficie può pubblicare **sé stessa** — fondale, cattura, superficie e **non** il contenuto — in un `LayerBackdrop`. Il contenuto resta fuori per un motivo ottico,
non di risparmio: dentro c'è il vetro che campiona questo materiale, e un tasto che rifrange sé stesso è feedback ottico. Si registra solo se qualcuno lo guarda e solo quando è cambiato (geometria; elemento aggiornato; uno
stato che i disegni hanno letto, osservato da un `SnapshotStateObserver`), e la manutenzione della cattura è separata dal disegnarla, così l'export non ripete la decisione.

**Trappole.**
- `shape: () -> Shape` va **ricordata** (`remember(shape) { { shape } }`): `ShapeProvider` ha `equals`/`hashCode` sulla lambda, altrimenti l'elemento non è mai uguale a sé stesso e ogni ricomposizione di
  ogni pannello rifà `update`, che invalida la cache e ricostruisce tutta la catena (sfocatura, lente, dispersione). Lo stesso vale per `effects`, `highlight`, `shadow`: lambda ricordate.
- `frozen` e `heldWhileMoving` sono lambda **fuori dall'`equals`** apposta: un chiamante che le ricostruisce a ogni ricomposizione renderebbe l'elemento diverso ogni volta e annullerebbe il cancello che alimentano.
- Un `layerBlock` viene applicato con `graphicsLayer(layerBlock)` e invertito **solo per scala e rotazione Z** quando si campiona (la posizione si ricava dalle coordinate): una traslazione nel blocco non va sul fondale.

**Non usarlo quando.** Quasi sempre: `glassSurface` (ottica dal ruolo, tinta, regola della taglia, contrasto, qualità). `drawBackdrop` diretto è per una ricetta che non è una lastra (la goccia: sezione sotto).

## Gli effetti: `BackdropEffectScope`

`effects: BackdropEffectScope.() -> Unit` costruisce **un solo `RenderEffect`** per concatenamento, nell'ordine in cui lo scrivi (il primo è il più vicino ai pixel registrati). Lo scope porta `size`, `layoutDirection`, `shape`,
`padding`, `renderEffect` e un'implementazione di `Density` **già scalata** dalla risoluzione di cattura, e una `RuntimeShaderCache`.

| effetto | cosa fa | nota |
|---|---|---|
| `blur(radius, edgeTreatment = TileMode.Clamp)` | sfocatura gaussiana (raggio in px dello scope) | non fa niente sotto API 31 o con raggio ≤ 0. Se il bordo non è `Clamp` **o** c'è già un effetto, `padding` sale al raggio |
| `colorControls(brightness, contrast, saturation)` | matrice di colore; se i tre sono neutri non fa niente | `glassSurface` lo usa per la vividezza: `saturation = 1 + (vibrancy − 1)·intensità` |
| `vibrancy()` | saturazione fissa 1,5 | |
| `opacity(alpha)`, `colorFilter(filter)` | filtri di colore | |
| `lens(refractionHeight, refractionAmount, depthEffect = false, chromaticAberration = false)` | rifrazione: SDF della sagoma del pannello; sposta il campione lungo la normale, di più verso il bordo | **API 33**. Se `padding > 0`, `padding −= refractionHeight` (min 0): la lente «copre» il margine che la sfocatura chiedeva |
| `effect(RenderEffect)`, `runtimeShaderEffect(key, agsl, uniformShaderName) { … }` | un effetto o un AGSL tuoi, concatenati | `runtimeShaderEffect` richiede API 33; la `key` è ignorata: la cache è per **sorgente** |

**Come lavora la lente.** `d = (1 − √(1 − x²)) · refractionAmount` con `x = 1 − (distanza interna dal bordo) / refractionHeight`: zero oltre `refractionHeight` dal perimetro, massimo al bordo; il campione è
spostato di `d` lungo il gradiente dell'SDF del rettangolo arrotondato (raggio d'angolo allargato a `min(1,5·raggio, mezzo lato corto)` per leggere la normale). `depthEffect` somma alla normale un termine
diretto al centro. Con la dispersione il campione è letto in **sette** punti lungo la direzione di spostamento, pesati per i canali, con intensità ∝ `x_c·y_c / (½w · ½h)` (zero sugli assi, massima agli angoli): è il
perché del costo e del perché si spegne sotto qualità 0,75.

**Il padding, spiegato.** Il blur ha bisogno di leggere un margine fuori dal pannello perché il bordo non si sfumi nel trasparente; quel margine è lo spazio che la cattura registra in più. La lente, che di fatto
piega già l'immagine di `refractionHeight` dentro il perimetro, **copre** quel margine: `padding = max(0, sfocatura − altezza lente)`. Con le ottiche di serie e la lente accesa il padding è **zero** a ogni intensità; sotto
API 33 senza lente è il raggio, e quel margine che si muove con la taglia o l'intensità fa ricatturare a ogni fotogramma. È il perché la regola della taglia ([vetro.md](vetro.md)) alza la sfocatura **solo fin dove la lente la copre**.

**Degradazioni.** Sotto API 31 gli effetti sono tutti no-op (`isRenderEffectSupported()`), sotto API 33 `lens`/`runtimeShaderEffect` sono no-op (`isRuntimeShaderSupported()`): blur e vibrancy restano. Dalla 4.0 `lens` lo è anche dove il compilatore del dispositivo rifiuta la sua sorgente: la guardia è il verdetto della sonda (`GlassFieldHealth.usable`, una compilazione per processo e poi una lettura), e lo stesso vale per i riflessi a shader e per il bagliore del tocco. Prima un rifiuto lanciava dentro il disegno. Una
sagoma che non è `RectangleShape`, `AbsoluteRoundedCornerShape` né `CornerBasedShape` **perde la lente** e tiene sfocatura e vividezza (a monte è un'eccezione: «un design system che finisce in cinque app deve degradare»).

**Gli shader sono condivisi per processo** (`RuntimeShaderCache`): a monte ogni pannello ne ha uno, e per Skia la chiave di un programma GPU è l'**istanza**, non il sorgente, quindi ogni pannello che entrava nello schermo
ricompilava lo stesso AGSL (stalli di 100–300 ms sul render thread, uno per pannello mentre scorreva un gruppo lista, tre di fila al primo fotogramma di ogni modale). Condividere l'istanza è sicuro perché ogni utilizzatore
imposta gli uniform e li **fotografa subito** sullo stesso thread. La sonda degli shader compila nella stessa cache (la prova è la compilazione che poi disegna), e le sorgenti dell'engine passano dalla porta `obtainEngineShader`, che su un rifiuto dà `null` invece di lanciare; `obtainRuntimeShader`, pubblica, resta quella di monte. `RuntimeShader` è un'interfaccia pubblica minima (`setFloatUniform`, `setIntUniform`, `setColorUniform`, `asComposeShader`,
`asAndroidRuntimeShader`) sopra `android.graphics.RuntimeShader` (API 33).

## Highlight, Shadow, InnerShadow

- **`Highlight(width = 0,5 dp, blurRadius = width/2, alpha = 1, style = HighlightStyle.Default)`**: l'anello speculare lungo il perimetro, tratteggiato da uno shader. Tre stili: `HighlightStyle.Plain` (bianco 0,38 in
  `Plus`, senza shader: un anello uniforme); `HighlightStyle.Default` (colore, modo, `angle` 45°, `falloff` 1: intensità `|normale · luce|^falloff`, cioè pieno dove la sagoma guarda verso la luce *e* verso
  il lato opposto, zero sui fianchi); `HighlightStyle.Ambient` (solo la metà rivolta alla luce, in `SrcOver`, `intensity` 0,38). Costanti `Highlight.Default` / `Ambient` / `Plain`. `glassSurface` costruisce lo stile `Default` con
  colore e modo di `glassRimRecipe` e l'angolo dall'ottica; la goccia usa `Ambient` diviso 1,5.
- **`Shadow(radius = 24 dp, offset = (0, radius/6), color = nero 0,1, alpha, blendMode)`**: l'ombra esterna, scavata **sotto** la sagoma (disegnata fuori da essa).
- **`InnerShadow(radius = 24 dp, offset = (0, radius), color = nero 0,15, alpha, blendMode)`** e `lerp(start, stop, fraction)` per animarla: l'ombra interna, lo spessore visto dall'interno.

**Comportamento.** I tre stanno ognuno in un **layer ausiliario registrato una volta** e ridisegnato: la geometria cambia solo con taglia, forma e numeri propri, mentre `alpha` e `blendMode` sono proprietà del layer
e non richiedono nessuna ri-registrazione (a monte si ri-registrava a ogni disegno, e l'anello è disegnato con un `BlurMaskFilter`: una maschera grande quanto il pannello, sfocata su CPU e caricata di nuovo, per pannello,
per fotogramma). Sono **limitati d'area**: oltre **1024 px** di lato lungo (`HighlightLayerMaxPx`) si registrano a scala ridotta (≥ 0,1) e si ridisegnano in grande; **anche la geometria si riduce** (la densità del layer ridotto
fa atterrare i dp in scala): senza, un pannello alto aveva l'angolo dell'anello moltiplicato per l'inverso della riduzione, e due gruppi identici, uno corto e uno lungo, avevano un anello uno giusto e uno storto. Un
pannello da tablet vale ~20 MB per layer ausiliario a piena risoluzione, e una schermata di pannelli supera il budget di risorse di HWUI, con la cache che ricarica ogni piccola texture a ogni fotogramma.
Lo shader del riflesso legge i **raggi** della sagoma vera (`CornerBasedShape`), non l'involucro che la memorizza: con l'involucro ricadeva sempre nel ramo di riserva (capsula, raggio = mezzo lato corto), che su un controllo
alto 44 px è esatto per caso e su un gruppo lista largo mille pixel dava 498 invece di 58.

**Trappola.** Bordo scuro e bordo chiaro vanno girati in **due metà** (colore *e* modo di comporre); vedi il bordo in [vetro.md](vetro.md).

## Le primitive di gesto: `glass/interaction`

Ricavate dai componenti d'esempio dell'app *catalog* di Kyant e adattate. Sono la base di `FluidSwitch`, `FluidSlider`, della pillola della tab bar, del segmentato e del rail.

**`inspectDragGestures`** — riconoscimento del trascinamento **senza soglia di movimento**: il tocco è il fotogramma zero del gesto. `detectDragGestures` di piattaforma aspetta qualche pixel prima di ammettere che c'è un
trascinamento, giusto per una lista e sbagliato per un controllo che deve inclinarsi verso il dito nell'istante in cui atterra. Un cambio **consumato** (da un figlio, da `clickable`) annulla.

**`GlassDragAnimation`** — la fisica di un controllo che si può spingere. Cinque molle insieme, ed è il fatto che siano cinque e non una a far leggere l'oggetto come fisico:
- `value` segue il dito rigida; `pressProgress` è il «mi stanno tenendo», più lento, che ispessisce il vetro; `scaleX` e `scaleY` hanno smorzamenti **diversi** (da qui lo squash: una scala sola è un palloncino);
  `velocity` è tracciata a parte perché il chiamante possa stirare il controllo lungo la direzione del moto.
- Numeri (token di `FluidMotion`, invariati dalla sorgente): valore (1, 1000), pressione (1, 1000), scala X (0,6, 250), scala Y (0,7, 250), velocità (smorzamento dato, 300). `velocityDampingRatio` di serie 0,5
  (sottosmorzato: la velocità *oscilla* dopo ogni cambio, e su una lente trascinata su una fila di schede l'indicatore fa «gelatina» a ogni incrocio); 1 è critico e lo stiramento si ferma quando si ferma il dito.
- `release()` **aspetta** che il valore sia quasi arrivato (entro il 2,5% della corsa) e che la pressione sia arrivata ad almeno `FluidPressMotion.ArrivalFraction` prima di rilassare qualunque cosa: lasciar crollare la pressione
  appena il dito parte fa sembrare il controllo *lasciato cadere* invece che *lasciato andare*. Un `press()` durante l'attesa vince su un `release()` vecchio (`pressGeneration`): prima il pomello si sgonfiava sotto un dito ancora sopra.
- `isAnimating` (2.10.0) è vero finché una qualsiasi molla si muove: la finestra in cui il vetro che questa anima è davvero vivo (si usa come `frozen`/`heldWhileMoving` al contrario).
- `modifier` aggancia `inspectDragGestures`; `press()`, `release()`, `updateValue`, `animateToValue`, `snapToValue`.

**`GlassTouchHighlight`** — il bagliore che segue il dito. Due cose insieme: la faccia intera si alza un poco (`veil`) e un punto radiale morbido segue il tocco (`spot`). Su scuro entrambi additivi (`Plus`): un riflesso speculare
*aggiunge* luce, scurire al tocco è l'idioma dei bottoni di plastica. Quattro ricette (`GlowRecipe`, dal sorgente):

| caso | tono · modo | velo | punto | raggio | nocciolo | ripiego senza shader |
|---|---|---|---|---|---|---|
| scuro (vetro o no) — Kyant, identico | bianco · `Plus` | 0,08 | 0,15 | 1,5 lati corti | 0,5 | 0,25 |
| chiaro, **non** vetro (riga, pannello opaco) | nero · `SrcOver` | come Kyant | | | | |
| chiaro **su vetro** (`onGlass`, 2.12.0) | bianco · `Plus`, piccolo e stretto | 0,015 | 0,055 | 1,15 lati corti | 0 (campana) | 0,08 |
| **pulsante di vetro** (`button = true`, 3.0, LibrePods) | bianco · `Plus` | 0,10 | 0,15 | il **lato lungo** della superficie | 0,5 | 0,25 |

Perché la ricetta «chiaro su vetro»: il nero sopra un vetro non era un bagliore, era **inchiostro** (~22% di nero su una faccia che trasmette: il tasto diventava un riempimento grigio-verde con l'etichetta spenta); ma i
numeri di Kyant sono tarati su un fondo medio e sommano 0,23 su una faccia che ne ha 20–35 livelli di margine — il verde e il blu toccano il tetto per primi, la faccia diventa un ciano-bianco piatto e il plateau
largo mezzo raggio copre un pulsante largo cento dp. Il picco qui è 0,07 (circa 18 livelli). `Screen` è stato provato e scartato: aggiunge `alpha·(1 − fondo)`, pochissimo sul chiaro e moltissimo su un vetro tinto d'accento.
**Il pulsante di vetro di LibrePods è bianco additivo anche su chiaro**: è una scelta di Alessio («come LibrePods») e il rischio è dichiarato — se ritorna il bianco piatto, i numeri di prima sono in `glowRecipe`.
Parametri: `strength` (0 spegne senza cambiare altro), `position` (la tab bar lo pina all'indicatore), `onDarkSurface` (un flag, non un colore: **il blend gira col colore**; `GlassDefaults.isDarkSurface()` al sito di chiamata),
`progress`/`touch` (una pressione guidata da fuori: il controllo che possiede la pressione dice la stessa cosa al vetro e alla luce, 2.11.0). Sotto API 33 non c'è AGSL: il bagliore è una lavata uniforme, e dalla 4.0 lo è anche dove il compilatore rifiuta la sua sorgente (`GlassGlowShaderString`).
`isAnimating` (2.10.0) come sopra.

**La goccia** (`glass/interaction/GlassDrop.kt`, interna, ma è la ricetta di tutto ciò che si afferra e scorre: pomelli, pillola della tab bar, segmentato, rail, lente dell'indice, goccia dei menu e dei pulsanti degli avvisi).
`glassDrop` è un modificatore solo: **a riposo piena** (faccia bianca, lente spenta, sfocatura 8 dp), **sotto il dito diventa lente** (sfocatura `8 dp × (1 − p)`, lente 10/14 dp per lo slider e 5/10 per l'interruttore × `p`,
dispersione, `Highlight.Ambient` ÷ 1,5 con alfa `p`, ombra interna `4 dp × p`, ombra fissa 4 dp al 5%), e **cresce e si deforma** nel `layerBlock` (scala X/Y delle molle più lo squash dalla velocità: `/10`, +0,75 sull'asse e
−0,25 sull'altro, tetto 0,2, spento con le animazioni ridotte; l'interruttore tiene `/50` con segno; la pillola della tab bar il segno con `/10`). Non prende l'ottica di un ruolo: `GlassRole.Interactive` non è la goccia. Una goccia
**in una lastra** ha la faccia del tema (`GlassDropFaces`: bianca sul chiaro, 0xFF636366 sullo scuro: un bianco pieno su una pista scura è un faro e l'etichetta `onSurface` non si leggerebbe). La trasformazione sta nel
`layerBlock` e **non** in un `graphicsLayer` antenato: il renderer la inverte quando campiona, quindi la lente ha in mano la pista che c'è davvero.

## Il vetro su vetro, sotto il cofano (`GlassNesting.kt`, nostro)

Un nodo di `drawBackdrop` è anche un **contenitore** (`GlassContainer`, un `TraversableNode` con chiave `GlassContainerKey`) che i nodi sotto di lui trovano fra gli antenati (`findNearestAncestor`).

- **Chi è il contenitore** lo dice l'albero, non la composizione. `resolveContainer` gira all'attacco, quando cambia il fondale e quando cambia il pannello di sopra; mai a ogni fotogramma.
- **`planNesting`** (pura): `Alone` se non c'è contenitore o la superficie ha chiesto `nestInContainer = false`; `AlreadyIncluded` se il fondale contiene già il materiale del contenitore (cablaggio a mano: vince);
  altrimenti `Insert(index)`, con `index` = il primo strato registrato **dentro** il contenitore (`liesInside`: l'owner del layer è un discendente) o la fine della pila. Fuori è dietro il pannello, dentro è davanti.
- **`NestedBackdrop`**: la pila del fondale col materiale infilato a `insertAt`, **per nodo, mai condiviso** (due tasti con lo stesso fondale possono stare su due pannelli diversi). Si può tenere la cattura se ogni strato sa dire di
  cosa è fatto; il materiale è un layer, quindi lo dice sempre.
- **Il materiale pubblicato** è creato al **primo lettore** (`materialFor` → `createOwnMaterial`), mai prima: un pannello senza vetro dentro non ha un layer in più. `LayerBackdrop.addReader/removeReader` tengono il conto
  (`ReaderCount`, mai negativo: sotto zero un pannello smetterebbe di registrare per sempre e da fuori si vedrebbe come «il tasto ha perso il vetro») e al primo lettore svegliano il pannello (`onFirstReader`).
- **`glassContainer`** / **`glassMaterialSource`** (le API pubbliche, in `GlassMaterial.kt`) sono due nodi: `GlassContainerScopeNode` (un contenitore che dichiara su quale materiale sta il suo contenuto, anche se non è figlio
  della superficie che lo disegna) e `GlassMaterialRecordingNode` (registra ciò che disegnano i modificatori seguenti **solo** finché `wantsRecording`, e disegna il layer: una volta sola). Se il vetro compare *sopra* del vetro che
  c'era già (un modificatore acceso dopo), il contenitore avvisa chi gli sta dentro (`adoptNestedReaders`, che si ferma al primo contenitore di ogni ramo e non lancia mai: un albero a metà costruzione non vale un crash).

## Cosa abbiamo cambiato rispetto a monte (`Fluid Engine change:`)

Ogni file copiato ha in testa l'avviso Apache-2.0 e l'elenco delle modifiche; ogni divergenza nel codice è marcata con un commento che comincia con **`Fluid Engine change:`** (o `Fluid Engine addition:`): bastano quelli per fare
il diff con una versione nuova a monte, che è l'unico modo di aggiornare (si riporta a mano). Le modifiche, in breve (l'elenco numerato completo, con il perché, è in `LICENSES/AndroidLiquidGlass.md`):

1. **KMP fuso in un solo source set Android**: `expect`/`actual` spariscono; package `com.kyant.backdrop` → `dev.antigravity.fluidengine.ui.glass.backdrop`.
2. **Niente `io.github.kyant0:shapes`** nella lente: l'engine ha `ContinuousCornerShape`.
3. **`recordLayer` prende il nodo come parametro**, non come *context parameter* (sperimentale: accenderlo per `engine-ui` lo accenderebbe per ogni app).
4. **La lente non lancia più**: il rettangolo vale raggi zero, ogni altra sagoma non descrivibile perde la rifrazione.
5. **`@SuppressLint("NewApi")`** sulla cache degli shader, con il contratto scritto; **cache per processo** (non per nodo).
6. **Le superfici grandi non muoiono né strozzano**: `fitToTexture`, `Auto` al posto di `Offscreen`, i layer ausiliari limitati d'area.
7. **`layerSources`, `isReadyToSample`, `stackedBackdrops`, `forEachPart`** sul `Backdrop`; `neverEqualPolicy` sulle coordinate; cattura tenuta/ferma (`sampleOnce`, `resampleIntervalMillis`, `frozen`, `heldWhileMoving`,
   sorgente sparita); `backdropScale` e `backdropScaleFactor`; `ShapeProvider` con identità; clip a rettangolo arrotondato (`fastClipShape`, `GlassClipGeometry`).
8. **Il preludio SDF vive anche in una copia first-party** (`FluidPhysicsShaders.kt`, famiglia di Fluid-physics): stessa struttura di rifrazione, perché il materiale resti lo stesso vetro mentre cambia forma.
9. **Una pressione arriva sempre in fondo** (2.11.0): `GlassTouchHighlight` passa da `FluidPressMotion`, `GlassDragAnimation.release` aspetta la pressione.
10. **I port dei controlli** (`FluidSwitch`, `FluidSlider`): stesse costruzioni e stessi numeri; cambia ciò che l'engine chiede a ogni controllo (accento del tema, `enabled`, aptica, semantica, movimento ridotto, ripiego senza `RenderEffect`).
11. **Vetro su vetro** (2.13.0): `GlassNesting.kt` accanto ai sorgenti copiati.

## Licenza e attribuzione

**Apache-2.0**, «Copyright 2025 Kyant». Cosa si deve fare in pratica: (1) tenere `LICENSES/AndroidLiquidGlass.md` nel repo e l'avviso di testa in ogni file copiato (sezione 4 della licenza); (2) riportare
«AndroidLiquidGlass — Apache-2.0 — Copyright 2025 Kyant» fra le licenze di terze parti nella schermata «informazioni» dell'app. L'engine la fornisce già: `fluidLicensesSection` / `FluidLicenseGroup`
elencano da sole tutte le voci di `FluidEngineLicenses`, quindi anche questa (e l'elenco va tenuto allineato ai file di `LICENSES/`: un avviso che esiste solo in un Markdown di un repository non ha accompagnato la distribuzione).
A differenza del vetro, i **pulsanti** di LibrePods sono **GPL-3.0** (`LICENSES/LibrePods.md`): un'app che li distribuisce è GPL-3.0. **Square** (github.com/Lelonio/Square), che per primo ha mostrato che la strada giusta
era la libreria di Kyant copiata come sorgente, è un debito d'indirizzo e non di righe: il suo repo è GPL-3.0 e non ne è stato copiato codice.

## Diagnostica

`FluidGlassDiagnostics` (oggetto, spento di serie: costa una lettura volatile per cattura): con `enabled = true` conta `totalCaptures` (ricatture dei pannelli) e `totalExports` (materiali pubblicati). Il progetto è che un pannello cattura solo
quando qualcosa che mostra è cambiato; **a schermo fermo i due numeri devono smettere di muoversi**, ed è così che lo si verifica su un dispositivo invece di crederci. Da accendere solo nelle build di debug.

Dalla 4.0 lo stesso oggetto dice anche **se gli shader del vetro compilano su questo dispositivo**, e queste letture non dipendono da `enabled`: `shaderSourceNames` elenca le sorgenti AGSL dell'engine, `shaderVerdicts` dà i verdetti già chiesti in questo processo
(nome → `null` se compila, o l'errore del compilatore), `compileAllShaders()` le compila tutte adesso. Un rifiuto scrive comunque una riga d'errore in logcat col tag `FluidGlass`, e chi usa quella sorgente disegna come sotto API 33 invece di lanciare. `compileAllShaders()` costa una compilazione per sorgente: non va chiamata all'avvio.

## Cosa è verificato dai test

- `GlassNestingTest`: senza contenitore il fondale è com'è, chi rinuncia non si appoggia, un cablaggio a mano che contiene già il materiale resta com'è, la pagina dietro un pop-up va sotto il materiale e ciò che è registrato dentro
  resta sopra, un fondale vuoto sta sul solo materiale, le pile si appiattiscono mentre le trasformazioni restano intere, le parti si trovano attraverso pile e trasformazioni, un layer normale registra sempre e uno su richiesta solo
  finché è letto, il primo lettore sveglia il pannello e il conto non scende sotto zero.
- `BackdropTextureCapTest`: i pannelli normali sono registrati come richiesto, uno più alto di una texture si registra più piccolo e il lato non supera il tetto, il tetto non alza mai la scala chiesta, le misure degeneri sono lasciate
  stare, il fattore di scala prende solo due valori (e uno malformato non toglie mai risoluzione a una pagina ferma), un pannello assurdo si ferma al pavimento e non a zero.
- `SourceGoneHoldTest`: un pannello tiene la cattura nel fotogramma in cui la sua sorgente viene smontata, una sorgente pronta non si tiene mai, chi non ha niente da tenere continua a registrare, una sorgente che non torna non si tiene per sempre.
- `GlassDropFaceTest` e `GlassDropSquashTest`: la faccia per lato, il contrasto con l'etichetta, l'alfa `1 − p` bit per bit; lo squash dei tre posti che lo avevano, rifatto **come stava scritto prima** e confrontato bit per bit con la funzione condivisa
  (slider, interruttore, pillola), tetto 0,2, movimento ridotto, i numeri di Kyant. `GlassShaderFallbackTest`: lente, riflessi e bagliore con la sorgente rifiutata prendono il ramo di API 33 senza eccezioni, con la sorgente accettata chiedono lo shader di prima, il verdetto si chiede una volta e nessun file arriva al compilatore senza un verdetto. `GlowRecipeTest`: ogni ricetta del bagliore (scuro = Kyant, chiaro non vetro = inchiostro, chiaro vetro = luce bianca che non scurisce mai la faccia e sta nel margine di una faccia chiara, pulsante = LibrePods).
- **Non verificato**: l'aspetto (gli host dei test non hanno fondale), il costo reale su un dispositivo (si misura con `FluidGlassDiagnostics` e con Perfetto), e i fotogrammi di una goccia o di un viaggio.

## Dove guardare

- `glass/backdrop/Backdrop.kt` (l'interfaccia e i membri nostri), `backdrops/` (`LayerBackdrop`, `layerBackdrop`, `CombinedBackdrop`, `CanvasBackdrop`, `EmptyBackdrop`, `rememberBackdrop`), `DrawBackdropModifier.kt` (`drawBackdrop`, il nodo, la vita di una cattura, `fitToTexture`).
- `glass/backdrop/BackdropEffectScope.kt`, `effects/` (`Blur`, `ColorFilter`, `Lens`, `RenderEffect`), `internal/Shaders.kt` (gli AGSL: SDF, rifrazione, dispersione, anello), `RuntimeShader.kt`, `RuntimeShaderCache.kt`, `Platform.kt` (API 31 / 33).
- `glass/backdrop/highlight/`, `shadow/` (anello, ombra, ombra interna, i layer ausiliari), `internal/` (`ShapeProvider`, `InverseLayerScope`, `LayerRecorder`, `Outline`).
- `glass/backdrop/GlassNesting.kt` (vetro su vetro), `FluidGlassDiagnostics.kt`.
- `glass/interaction/GlassInteraction.kt` (`inspectDragGestures`, `GlassDragAnimation`, `GlassTouchHighlight`, le ricette del bagliore), `GlassDrop.kt` (la goccia).
- `LICENSES/AndroidLiquidGlass.md`, `LICENSES/LibrePods.md`, `engine-ui/…/ui/fluid/FluidLicenses.kt`.
