# Fluid-physics: il motore dentro

Seconda parte della scheda [fluid-physics.md](fluid-physics.md): quella per chi deve **modificare** il motore o capire un fotogramma sbagliato. Chi
deve solo usarlo non ha bisogno di leggerla. Qui non c'è API nuova: quasi tutto è `internal`, e se ne parla solo perché spiega il
comportamento di cose pubbliche (`FluidPhysicsState`, `Modifier.fluidPhysicsSurface`). I nomi interni sono in `code`
senza garanzia di stabilità. Catalogo delle firme pubbliche: [../api/fluid-physics.md](../api/fluid-physics.md). Il materiale (ottica, ruoli, regola
della taglia, `glassRimRecipe`, `glassShadowColor`) è di [vetro.md](vetro.md); i menu sopra il motore sono di [menu-modali.md](menu-modali.md).

## Le tre discipline non negoziabili

Ereditate dal contratto di `DrawBackdropModifier` e scritte in testa a `FluidPhysicsShaders.kt`. Un fotogramma sbagliato di
solito ne viola una.

1. **Sorgenti fisse.** La cache degli shader è di processo e ha come chiave il *testo sorgente*: le stringhe AGSL sono costanti,
   e ogni cosa che cambia per fotogramma passa dagli **uniform**. Una sorgente costruita a runtime ricompila a ogni fotogramma.
2. **Un solo passaggio per pixel.** Il gradiente del campo si accumula nello stesso loop della distanza (mix pesato dallo stesso
   `h` dello smooth minimum), mai per differenze finite: tre valutazioni extra del campo per pixel su un tablet si vedono.
3. **La maschera è dello shader.** Il clip hardware del nodo resta il rettangolo del nodo (`GlassClipGeometry`) per tutta la vita
   della superficie; è l'alpha calcolato nello shader a ritagliare la silhouette. Per questo la sagoma può cambiare a ogni
   fotogramma senza costringere il layer a re-registrare. (Al tier `Balanced`, senza shader a mascherare, il clip torna la sagoma esatta:
   `FluidPhysicsSilhouetteShape.fastClipOutline` con `clipToBounds = false`.)

Corollari che si pagano: il **padding della cattura non cambia mai** durante il morph (la sfocatura non supera mai l'altezza di
lente che la copre: `max(0, sfocatura − altezza)` vale zero a ogni taglia e a ogni miscela, e sotto ¼ px è portato a zero per non
rifare la cattura per puro arrotondamento: `physicsLensPadding`, `CapturePaddingSnapPx`); **a riposo l'istanza di `Shape` è una sola**
(l'identità diversa invalida la cache dell'`Outline`, ed è voluta *solo* in viaggio); **uno shader di vetro nuovo accanto a
`GlassMaterial` è vietato** e la famiglia SDF di Fluid-physics è la deroga deliberata, nel suo package, il cui preludio deriva da quello
Apache-2.0 di Kyant (`LICENSES/AndroidLiquidGlass.md`).

## Il piano di resa

`PhysicsRenderPlan` è «tutto quello che serve a disegnare un fotogramma, in array riusati»: si riscrive, non si rialloca. L'unica
allocazione per cambio di progresso è l'istantanea di `shape`, voluta. Contiene: `mode` (`PlanModeSlabs` 0 o `PlanModePoly` 1), fino a
6 pezzi (rettangolo come centro+mezze misure, quattro raggi, raggio di fusione per pezzo), fino a 96 vertici di un anello, `soften`
(erosione del campo poligonale: `PolySoftenPx` 3 px, per sciogliere le faccette del campionamento), `silhouette` (il `Path` condiviso da tinta,
bordo e maschera: *per costruzione raccontano la stessa sagoma*), i riquadri `bounds` (quello del corpo) e `contentBounds`
(dove posare il contenuto), `contentClipRect`, `dropRest`/`dropReach`, `silhouetteRoundRect` (un `RoundRect` solo se la forma è davvero una
lastra: serve a `fluidPhysicsClip` perché un `Outline.Rounded` prende il clip hardware e un `Outline.Generic` fa mascherare il nodo fuori schermo).

`FluidPhysicsState.ensurePlan()` sceglie il piano, cache per cache:

1. **Drive esterno** (`driveExternally`): cache su (transito, `t`, rettangolo di partenza vivo, versione della goccia). Se la goccia
   è viva e c'è una lastra per parte → piano della goccia; a `t ≥ 1` → piano di riposo (a un gruppo, il traguardo fermo; a una lastra, il
   traguardo gonfiato, e in punto all'1 la lastra col raccordo continuo della casa); con una partenza viva → lastre dal rettangolo di adesso;
   altrimenti il lerp.
2. **Nessun transito**: se la goccia non si è posata → `settlePlan` (la sagoma ferma disegnata dalla goccia, in un piano a parte); sennò
   `restPlan`, istanza stabile, ricostruito solo quando cambia la forma (o `maskInShader`, o `liquidAllowed`).
3. **Transito di `morphTo`**: cache su (transito, `t`, versione goccia); goccia o lerp.

`restFormNow()` dice se la superficie è *ferma*: falso finché la goccia non è quieta o c'è un transito; un drive esterno arrivato esattamente all'1 è a
riposo. È ciò che permette a bordo e ombra di registrarsi una volta sola per una lastra ferma.

## Transiti

- **`SlabTransit`**: una o più coppie (sorgente, destinazione) di lastre (`matchSlabPairs`, costo minimo, deterministico), con il raggio di
  fusione massimo dei due gruppi. L'interpolazione è `lerpSlab`: otto numeri, con i lati che non scendono sotto `MinSlabSidePx` (1 px) e i raggi
  fra 0 e mezzo lato (`t` *non* è vincolato a 0..1: l'overshoot è la vita del morph, sono i limiti fisici che non saltano mai).
- **`RingTransit`**: per ogni viaggio che coinvolge una sagoma libera. `buildMatchedRings` fa il `Morph` di `androidx.graphics.shapes` una volta al
  decollo (accoppia le curve per caratteristiche: angoli con angoli, lati con lati) e campiona le cubiche agli estremi **con gli stessi parametri**;
  poiché una Bézier è lineare nei propri punti di controllo, interpolare i campioni dà punto per punto la stessa curva del `Morph`: **ogni
  fotogramma dopo il decollo è aritmetica su due array**. Campionamento per cubica (non per lunghezza d'arco: gli angoli sono dove si vede), con il totale
  sotto 64 vertici (`samplingPlan`). Se il `Morph` rifiuta un input degenere (vertici coincidenti, aree nulle) l'anello è ricampionato per lunghezza
  d'arco e allineato sul punto più alto: viaggio più povero, mai un crash a metà gesto. La silhouette di un anello è tracciata con quadratiche fra i
  punti medi dei lati (la tinta e il bordo la *tracciano*, e una corda di quattro pixel su un cerchio grande si vedrebbe come un vertice).
- **Oltrepasso**: oltre l'1 è il traguardo gonfiato attorno al centro **dell'arrivo intero** (per un gruppo, il centro dell'unione: i pezzi si allontanano mentre
  crescono, come un'immagine ingrandita) di `morphOvershootScale(t, frame, capPx)`; le coppie diventano (gonfiato, gonfiato) e il lerp le disegna esatte. I raggi
  scalano del fattore minore (una capsula resta capsula) e non superano mai mezzo lato. `Transit.formAt(t)` dà la stessa geometria *come `FluidForm`*: è il
  punto di partenza di un ritargeting e permette a un test di confrontare il piano senza un frame clock.
- **Scalo** (`physicsLayoverFor`): l'unica coppia servita è gruppo↔sagoma libera (il campo fuso parla rettangoli, l'anello una sagoma sola). La goccia
  intermedia è ancorata sul footprint della **destinazione**: ancorata all'origine si leggeva al contrario, «la forma tornava indietro e poi ripartiva».
- **Ogni transito possiede il proprio `Animatable`, nato a zero.** Con un progresso condiviso il transito nuovo si mostrava per un fotogramma col valore
  vecchio (1): la sagoma d'arrivo a taglia piena, un bordo del traguardo che lampeggia e sparisce. Installazione e progresso zero atterrano nello stesso snapshot.

## La goccia

Vedi la descrizione d'uso in [fluid-physics.md](fluid-physics.md), «A riposo una lastra, in viaggio una goccia». Come lavora `LiquidDrop`:

- 48 punti (`FluidMotion.DropPoints`) campionati per lunghezza d'arco sul perimetro del rettangolo arrotondato a riposo di quell'istante
  (`RoundedRectPerimeter`: nove tratti dall'alto al centro in senso orario; stessi indici fra un fotogramma e l'altro).
- Ogni punto è una molla verso il suo punto a riposo con rigidezza `kFar + (kNear − kFar) · w`, `w = exp(−distanza dall'ancora / portata)` (`DropAnchorReachDp`
  110 dp: l'ancora è il centro della *partenza*, e la goccia che si posa dopo l'arrivo tiene l'ultima), smorzamento `2ζ√k` **misto**: relativo al moto
  della sagoma vicino all'ancora (segue senza ritardo), assoluto lontano (resiste: è la coda). Fra i vicini, una tensione del bordo che agisce
  sullo **scarto dal riposo e non sulla posizione**: sulla posizione un bordo teso stringe gli angoli anche da fermo, l'equilibrio non è più la sagoma, la goccia non
  si posa mai sulla lastra e gli angoli si smussano (i bordi «esageratamente smussati» di Alessio). Con 0,2 del banco ogni punto segue quasi da solo il suo punto e gli angoli restano definiti.
- Integrazione semi-implicita a passi di 1/240 s; un fotogramma più lungo di 1/30 s (uno stallo) si integra per 1/30. Il tempo è quello delle animazioni
  (`MotionDurationScale`); con scala 0, o fuori da `liquidAllowed` (animazioni ridotte, `LocalFluidPhysicsLiquid` falso, nessuna superficie che l'abbia chiesto), la goccia si azzera.
- **Si posa** (`settled`) quando la sagoma a riposo è ferma, ogni punto è entro `DropRestPx` (0,25 px) dal suo, la velocità sotto `DropRestSpeedDp` (3 dp/s) e la lente
  sotto 0,002: allora ogni scarto vale zero esatto e il piano torna quello di riposo *al bit*. `quiet` e `version` sono stato di snapshot letto in disegno: scriverli invalida
  il disegno, ed è la chiave della cache del piano.
- Il conducente `runLiquid` aspetta con `snapshotFlow` che la sagoma a riposo cambi e solo allora chiede fotogrammi: **a riposo, nessun fotogramma**. Uno per stato (due
  conducenti alimenterebbero lo stesso fotogramma due volte, e la goccia scarta il secondo).
- Si **disegna** con 96 punti (`DropRenderPoints`: il doppio di quelli simulati), un numero fisso per angolo (metà del totale divisa fra gli angoli) e il resto sui lati, spostati dello scarto
  interpolato in Catmull-Rom periodica: gli angoli del pannello restano definiti anche in viaggio, mentre la goccia si simula con 48 punti equidistanti. Il disegno passa dallo
  shader poligonale (senza erosione, path a segmenti: maschera, pellicola e bordo sulla stessa sagoma), con una **fascia** attorno alla sagoma a riposo fuori dalla quale lo shader non ha
  lati da cercare (`dropReach` + 1,25 px) e un riquadro di uscita anticipata.
- **Il contenuto** sta sulla sagoma a riposo spostata dello scarto del baricentro (`contentBounds`) e si ritaglia sul rettangolo arrotondato che sta *dentro* il vetro di adesso
  (`dropInnerClip`: per ogni lato diritto, il punto della goccia più rientrato): dove la goccia è rimasta indietro il testo non sta mai sopra la pagina. `travelClipIn` lo
  consegna ai menu e vale `null` dal progresso 1 in poi (a riposo il corpo è più grande del contenuto e un ritaglio sarebbe lavoro per niente).
- **Il bordo** della goccia è una fascia di triangoli (`DropRimMesh`, anelli di 1 px, al più 20) col profilo esatto del bordo a riposo (`restRimProfile`: tratto doppio sfocato e ritagliato):
  alla posa nessun gradino.
- **La lente in moto** (`liquidMaterial`, solo al tier `Full`): `lens` segue la velocità del baricentro a riposo (piena a 700 dp/s, inseguita in 0,06 s); a peso zero `liquidMaterial` torna *la stessa istanza*, quindi
  a riposo niente cambia. Altezza +60%, spostamento +80%, sfocatura −45% (la sfocatura scende mentre l'altezza sale: il patto del margine regge per costruzione), cupola che si accende, frangia a 0,5, ingrandimento 3,5% (`liquidMagnify`).
- Il gonfiore oltre l'1 e la partenza sgonfiata sotto lo 0 (`dropRestSlab`): `1 + gain·(t − 1)` per asse oltre l'1, `1 + gain·t` sotto lo 0 (con un minimo di 0,5) attorno al centro della partenza.

## Gli shader

Quattro sorgenti in `FluidPhysicsShaders.kt`, tutte AGSL costanti: `SlabGroupRefractionShaderString` e `PolyMorphRefractionShaderString`, e le due varianti con la frangia cromatica
`SlabGroupDispersionShaderString`, `PolyMorphDispersionShaderString` (sette volte i campioni nella banda della lente: provate a parte e solo se le altre passano, e solo sopra la qualità 0,75 come in
`glassSurface`). Il preludio SDF (`radiusAt`, `sdRoundedRect`, `gradSdRoundedRect`) deriva da quello del renderer vendored di Kyant, ri-dichiarato perché lì è `private`; la struttura della rifrazione (circleMap, banda
di displacement al bordo, cupola) è la stessa: **lo stesso vetro anche mentre cambia forma**, più la maschera.

- **Gruppo**: fino a `PhysicsMaxPieces` (6) rettangoli, ognuno con rettangolo, quattro raggi e raggio di fusione; il campo è lo smooth minimum dei campi, e il ponte è materiale che nessun path descrive
  → con più pezzi la **tinta sta nello shader** (`tintInShader`), non in un `drawPath`. Il raggio effettivo (`effectiveBlendRadius`) scala col varco con segno (`slabMinGap`): pieno da staccati, spento
  a compenetrazione.
- **Poligono**: fino a `PhysicsMaxRingVertices` (96) vertici (64 per una sagoma libera, 96 per l'anello della goccia), con le normali mediate fra lati vicini su 4 dp (`PolyNormalSmooth`) per sciogliere lo spigolo fra due lati.
- I loop AGSL vogliono **indici costanti** negli array uniform: il vertice precedente si *trasporta*, non si indicizza (una delle lezioni pagate sul dispositivo).
- I numeri di fotogramma sono in pixel di *layout* nel piano e in pixel di *scope* nello shader: lo scope degli effetti può lavorare a risoluzione ridotta, quindi ogni geometria si moltiplica per
  `scopeFactor` (larghezza dello scope / larghezza di layout; identità a scope pieno, e un nodo degenere ripiega sull'identità invece di avvelenare gli uniform: `UniformScaleMappingTest`).
  `refractionAmount` entra negato; l'antialias della maschera è 1,25 px di scope; gli array uniform si riempiono per intero (uno slot oltre `pieceCount` non è letto, ma un residuo del fotogramma prima
  non deve poter diventare un pezzo fantasma).
- Gli specchi Kotlin in `FluidPhysicsGeometry.kt` (`sdRoundedRect`, `smin`, `sdPolygon`, `sdPolygonGradient`) sono le stesse formule: i test li esercitano, lo shader è la copia carbone. **Una modifica allo shader
  si porta anche lì**, o `PolySdfMathTest` non guarda più la verità.
- **Sonda di salute** (`FluidPhysicsShaderHealth`): una compilazione di prova dei due shader di base, una volta per processo (`@Volatile`, verdetto per sempre perché le sorgenti sono costanti); la frangia ha la sua. Il verdetto
  decide il tier.

## Materiale, bordo, ombra

- **Materiale** (`FluidPhysicsMaterial.kt`): `PhysicsMaterial` = ottica + due *pesi* (cupola e frangia), perché i `Boolean` di `GlassOptics` non si sfumano e uno shader prende `float`. `lerpGlassOptics` fa `a + (b − a)·f` (non il `lerp` di Compose,
  che fra due capi uguali sbaglia di un ulp: un'altezza un ulp sotto la sfocatura apre un margine di cattura di mezzo milionesimo di pixel, abbastanza per rifare la cattura); i due Boolean passano al capo d'arrivo a metà. `physicsLensUniforms` = *la formula di
  `glassSurface`*: `glassSizedOptics` per sfocatura/altezza/spostamento, profondità = intensità × qualità, spostamento ×`(1 + pressione · pressedDepthBoost)` (la pressione approfondisce lo spostamento, **mai** l'altezza, che regge il padding), frangia solo sopra qualità 0,75.
  `PhysicsMaterialSource` memorizza l'ultimo risultato: in un fotogramma il materiale lo chiedono in tre (effetti, bordo, ombra) e la miscela è la stessa.
- **Bordo** (`PhysicsRimRenderer`): a riposo, per una lastra, registra l'anello **una volta** in un layer con la ricetta del renderer (`HighlightNode`: tratto doppio ritagliato sulla sagoma, sfocato di mezza larghezza, colorato dallo shader del riflesso di Kyant con l'angolo della luce,
  ridotto sopra il tetto d'area) e poi lo ridisegna: alfa e modo di comporre sono proprietà del layer e non registrano niente. In viaggio, per una lastra, la stessa banda interna con lo stesso shader aggiornato per uniform e **senza** la mezza larghezza di sfocatura (su due o tre pixel in movimento non si vede);
  per un gruppo o una sagoma libera, il gradiente lungo la sagoma (`glassRimRamp`). La banda dentro la sagoma è `ceil(larghezza)`, mai oltre mezzo lato corto (`physicsRimBandPx`): il bordo fisico di una volta era un tratto *centrato* sul contorno, mezzo fuori sopra la pagina, senza sfocatura.
- **Ombra** (`PhysicsShadowLayers`, usata da `fluidPhysicsSurface`): come `ShadowNode`: contorno della forma ferma, colore del lato del tema, `BlurMaskFilter` del raggio dell'ottica, spostato di un sesto del raggio in giù, scavato sotto la sagoma, in un layer fuori schermo, **registrato una volta per forma, raggio e colore**;
  in viaggio i layer della partenza e dell'arrivo (due posti) si stirano dal loro riquadro a quello della sagoma corrente e si sfumano col progresso — una texture scalata, non una maschera rifatta. La variante `Modifier.fluidPhysicsShadow` (interna, quella della finestra dei modali) sfoca **una volta** un bitmap a mezza risoluzione per taglia di pannello e lo stira sulla
  sagoma a ogni fotogramma (il `BlurMaskFilter` sul path che cambia a ogni fotogramma era il lag dell'apertura: HWUI rasterizza la maschera daccapo ogni volta). Soglia 0,02 sotto cui non si disegna niente (un fotogramma di confine con i default produrrebbe una displaylist con un lampo). Colore `glassShadowColor`: nero al 16% sul chiaro, al 10% sullo scuro.
- **Bagliore del dito** (`pressState`): un gradiente radiale che campiona la campana dello shader `GlassTouchHighlight` (`smoothstep`, 8 tratti) ritagliato sul path della sagoma; funziona anche sotto API 33.

## Il ripiego

- `Balanced` (SDK 31–32 o shader rifiutato): `blur` + `colorControls` (vividezza) in catena, niente lente; tinta e bordo come path. La regola della taglia è spenta (`lensAvailable = false`): senza niente che copra il margine un raggio che
  crescesse con la sagoma lo sposterebbe a ogni fotogramma.
- `Lite` (sotto SDK 31): `physicsFallback` — `drawPath(plan.silhouette, tint.fallback)` e `drawContent()`; niente fondale, bordo né ombra. Anche qui la goccia anima il contorno.

## Se modifichi il motore

1. **A riposo, bit identico.** Qualunque cosa cambi nel disegno di un fotogramma fermo cambia lo scalino di partenza e di arrivo. `RestStateDisciplineTest`, `DropPlanTest`, `FluidPhysicsMaterialTest` ne sono i guardiani.
2. **Niente allocazioni per fotogramma** oltre l'istantanea di `shape`; array riusati; niente `Path.op` per fotogramma se non per i gruppi (e lì `mergePieceIntoSilhouette` esiste perché sotto-path separati farebbero tracciare al bordo *entrambi* i contorni di due pezzi sovrapposti: una doppia linea).
3. **`PhysicsMaxPieces` = `FluidForm.Group.MaxPieces`, `PhysicsMaxVertices` = `FluidFormPresets.MaxVertices`**: l'array uniform è dimensionato su quelle costanti e devono restare uguali.
4. **Nessuna molla nuova scritta a mano fuori da `FluidMotion`**, salvo qui: `spring(`, `tween(` e `keyframes` letterali sono ammessi in `fluidphysics/**` (come nel renderer): per questo `FluidPhysicsJourney` può avere la rincorsa. Se un numero lo vede l'utente in mano, diventa token di `FluidMotion`.
5. **Mai** il lerp estrapolato oltre l'1 né una fusione che estrapola oltre il coincidere (`smin(a, a, k) = a − k/4` gonfia l'unione).
6. **Giudicare in mano**: dopo ogni modifica al disegno, la [ricetta di QA a fotogrammi](fluid-physics.md) (cattura densa, primi cinque fotogrammi, collage a 5× e a 1×). I test non hanno fondale.
7. La documentazione di `docs/10-coerenza.md` ha la matrice dei comportamenti della superficie Fluid-physics e dei menu: se cambia un numero (le finestre del contenuto, le molle), cambia anche lì.

## Cosa è verificato dai test

Vedi la sezione omonima di [fluid-physics.md](fluid-physics.md). I test del motore che qui servono: `RestStateDisciplineTest` (istanza stabile a riposo, snapshot nuovo in viaggio), `FluidPhysicsMaterialTest` (la lente a riposo = quella di `glassSurface`, il padding che non si muove), `LiquidDropTest` (coda, zero esatto, tensione del bordo), `DropPlanTest`
(piano della posa = lastra al bit, annullo a metà), `DropRimTest` (il profilo del bordo), `ExternalDriveStartFrameTest` e `ExternalGroupDriveTest` (la guida esterna), `PolySdfMathTest` (gli specchi del campo), `MatchedRingsTest` (la parametrizzazione degli anelli), `UniformScaleMappingTest` (lo spazio dello scope).

## Dove guardare

| file | cosa c'è |
|---|---|
| `FluidPhysicsState.kt` | `ensurePlan`, `PhysicsRenderPlan`, `SlabTransit`/`RingTransit`, `physicsLayoverFor`, `buildTransitPlan`, `buildRestPlan`, il conducente `runLiquid` |
| `FluidPhysicsLiquid.kt` | `LiquidDrop`, `RoundedRectPerimeter`, `buildDropRing`, `dropInnerClip`, `dropRestSlab`, la lente in moto (`liquidMaterial`) |
| `FluidPhysicsJourney.kt` | `HouseJourneySpec`, `inheritedProgressVelocity`, `morphOvershootScale`, `inflateSlab`/`inflateRing` |
| `FluidPhysicsGeometry.kt` | RDP, ricampionamento, `lerpSlab`, `matchPieces`, `slabMinGap`, `effectiveBlendRadius`, gli specchi del campo di distanza |
| `FluidPhysicsRings.kt` | `MatchedRings`, `buildMatchedRings`, `restRing`, il fallback senza `Morph` |
| `FluidPhysicsShaders.kt`, `FluidPhysicsEffects.kt` | le quattro sorgenti AGSL; `slabGroupMorphLens`/`polyMorphLens` che impostano gli uniform |
| `FluidPhysicsMaterial.kt` | `lerpGlassOptics`, `physicsLensUniforms`, `physicsLensPadding` |
| `FluidPhysicsRim.kt`, `FluidPhysicsShadow.kt` | il bordo (`PhysicsRimRenderer`, `DropRimMesh`) e l'ombra (`PhysicsShadowLayers`, `fluidPhysicsShadow`) |
| `FluidPhysicsShape.kt`, `FluidPhysicsClip.kt` | `GlassClipGeometry`, la sagoma del clip del contenuto |
| `FluidPhysicsTier.kt` | `resolvePhysicsTier`, `FluidPhysicsShaderHealth` |
