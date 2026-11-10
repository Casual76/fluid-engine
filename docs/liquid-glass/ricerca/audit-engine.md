# R2 · Audit dell'engine 3.1.2: vetro, trasformazioni, barre, menu, tocco

Ricerca del 2026-11-10 sul worktree `t3-8663befb` (base `add6134`, engine 3.1.2). Sola lettura, nessuna build,
nessun dispositivo: ciò che a schermo non si può dedurre dal codice è dichiarato in fondo.

Abbreviazioni dei percorsi: `ui/` = `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`; `bd/` = `ui/glass/backdrop/`;
`fp/` = `ui/fluidphysics/`; `ref/` = `skill/fluid-engine/references/`; `test/` = `engine-ui/src/test/kotlin/dev/antigravity/fluidengine/ui/`;
`sample/` = `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/`; `stest/` = `sample/src/testDebug/kotlin/dev/antigravity/fluidengine/sample/`.

## Cosa conta per il piano

1. Tutto il vetro passa da **un solo nodo**, `DrawBackdropNode` (`bd/DrawBackdropModifier.kt:507`): cattura, vetro su vetro, export, qualità, tetti di texture. Le «due famiglie» sono due ricette di effetti sopra lo stesso nodo: il punto comune esiste già, sotto entrambe.
2. Le ricette in realtà sono almeno quattro (lastra di Kyant, Fluid-physics, goccia, puck della tab bar vendored) più `gooey` e il vetro dipinto; il bordo ha 5 implementazioni, l'ombra 3 (più ricette diverse sopra `ShadowNode`).
3. Il materiale di oggi: saturazione ×1,6 → sfocatura 2,5 dp × ruolo → lente SDF → **pellicola densa (34–56% di un grigio fisso)** → filo di 0,8 dp a profilo `|n·ŷ|` → un'ombra. Sul tema chiaro il filo è **nero al 28% in SrcOver**, non luce. Nessuna luce del corpo né seconda sorgente.
4. **Niente legge lo sfondo**: lato chiaro/scuro, pellicole e colore delle icone vengono dal tema (`GlassDefaults.isDarkSurface`, `ui/fluid/GlassMaterial.kt:1271`). Le tinte `…OnPhoto` si scelgono a mano e hanno un solo consumatore.
5. La fascia in alto è un solo sito (`FluidTopBarGlass`, `ui/fluid/FluidScreen.kt:1361`), ma porta anche il materiale su cui stanno le azioni (`exports = barGlass`) e il loro `LocalGlassBackdrop`.
6. Le azioni di barra sono **slot composable liberi** (`actions: RowScope.() -> Unit`): nessun modello, quindi nella stessa pagina cambiano di colpo. Fra due pagine c'è già un morph dei tasti (Fluid-physics guidato da fuori, ≤6 capsule).
7. Il menu nasce dal tasto, ma la barra non si trasforma, le voci non passano dalla lente (stanno sopra, in un nodo fratello) e la pagina dietro è velata appena: sfocatura 3,75 dp, nero 12% (chiaro) / 20% (scuro).
8. Il tocco ha già crescita, deriva `tanh`, allungamento e bagliore, ma la flessione è una scala del `layerBlock`: niente deformazione del contorno sotto il dito, niente luce che passa ai vicini.
9. Collaudo senza dispositivo: 120 file di test JVM + ~50 Robolectric, **nessun test d'aspetto**; Robolectric non esegue AGSL; una sola prova legge pixel (SDK 32).
10. Invarianti che il materiale nuovo eredita: padding di cattura fermo nei viaggi, mai un layer scalato sopra un vetro, cancello del fotogramma di confine, export a forza piena, tetti 4096/1024 px, `Shape` stabile a riposo, AGSL a sorgente costante.

---

## 1. La catena di resa di oggi

### 1.1 Schema: da `glassSurface` a `drawBackdrop`

```
Modifier.glassSurface(state, tint, shape, role|optics, intensity, opticalDepth, pressed, exports, layerBlock…)
│                                                                   ui/fluid/GlassMaterial.kt:1320
├─ API < 31 ─▶ glassFallback: clip + tint.fallback (+ hairline). Fine.                       :1376, :1919
├─ glassMask(falloff)           drawWithCache, FUORI dal renderer                            :1853
│     Uniform: saveLayer(alpha) solo se 0 < intensità < 1, gonfiato di 2× raggio d'ombra
│     FadeDown: saveLayer + rettangolo DstIn a gradiente (fadeDownStops) a OGNI fotogramma    :1879, :1907
└─ drawBackdrop(...)                                                         bd/DrawBackdropModifier.kt:129
   ├─ graphicsLayer(layerBlock)        solo se dato (tasti: crescita/deriva/allungamento)      :226
   ├─ InnerShadowNode   disegna DOPO il contenuto; layer Offscreen creato sempre, nessun ruolo lo usa
   ├─ ShadowNode        disegna PRIMA: layer Offscreen (BlurMaskFilter del contorno, scavato sotto la sagoma)
   ├─ HighlightNode     disegna DOPO il contenuto: layer dell'anello (tratto + BlurMaskFilter + shader)
   └─ DrawBackdropNode  layout: placeWithLayer(clip = fastClipShape, CompositingStrategy.Auto)  :535-557, :874
        draw()                                                                                   :878-932
        1. fitToTexture(scala × qualità quantizzata); effectScope.update → updateEffects se cambia :880-887
        2. refreshCapture: ricattura solo se la cattura non può più essere giusta                :735-846
             graphicsLayer.record(taglia·scala + 2·padding) { rigioca sampledBackdrop }
             sampledBackdrop = il fondale passato, o NestedBackdrop con il materiale del contenitore
        3. drawBackdropLayer: drawLayer(cattura)  ← RenderEffect a catena:                         :848
             ColorFilter (saturazione) → Blur (TileMode.Clamp) → RuntimeShader (lente; 7 tap con frangia)
        4. onDrawSurface: pellicola (drawRect / gradiente FadeDown) + hairline                    GlassMaterial.kt:1800
        5. drawContent: figli (icone, testo, bagliore del dito, vetro annidato)
        6. onDrawFront: null in glassSurface (Fluid-physics ci disegna il bordo)
        7. export: se qualcuno campiona il materiale, registra 3+4+6 (MAI il contenuto) in un layer  :896-931
```

A schermo, dal basso: ombra esterna → cattura con effetti → pellicola → contenuto (il bagliore del dito sta sotto le icone,
`bd/../interaction/GlassInteraction.kt:522`) → anello speculare → ombra interna (inattiva).

Il clip del nodo è il **rettangolo arrotondato equivalente** della sagoma (`bd/internal/ShapeProvider.kt`, `fastClipShape`), non il
path continuo: lo dicono i commenti a `DrawBackdropModifier.kt:537-544` («the layer clip only has to stop the refracted image spilling»);
la curva visibile dell'angolo la fanno pellicola e anello.

### 1.2 Layer e passate per superficie

| pezzo | oggetti | quando si registra | file |
|---|---|---|---|
| cattura | 1 `GraphicsLayer` con `RenderEffect` | quando cambia: elemento, taglia registrata, padding, offset *relativi* alle sorgenti (`layerSources`); con `sampleOnce` una volta; `frozen`/`resampleIntervalMillis` la rimandano | `bd/DrawBackdropModifier.kt:735-846` |
| catena effetti | 3 effetti: matrice colore, sfocatura, shader AGSL | ricostruita su cambi di densità/taglia/stati letti; **solo il padding invalida la cattura** | `:961-980` |
| posa | 1 RenderNode (clip hardware, `Auto`, non Offscreen) | — | `:535-557` |
| ombra | 1 layer Offscreen, `taglia + 4·raggio` | su taglia, forma, raggio, colore; alpha e blend sono proprietà del layer | `bd/shadow/ShadowModifier.kt:88-160` |
| anello | 1 layer, tratto `2·ceil(w)` ritagliato, `BlurMaskFilter(w/2)`, shader di profilo; ridotto oltre 1024 px | su taglia, forma, stile | `bd/highlight/HighlightModifier.kt:115-216` |
| ombra interna | 1 layer Offscreen **allocato e mai disegnato** (alfa 0 in ogni ruolo) | — | `bd/shadow/InnerShadowModifier.kt:221-228` |
| maschera | saveLayer | FadeDown sempre; Uniform durante le dissolvenze | `ui/fluid/GlassMaterial.kt:1853-1899` |
| export / materiale proprio | 1 layer su richiesta | solo con lettori, e solo se «stale» | `bd/DrawBackdropModifier.kt:896-931`, `:1183` |
| `layerBlock` | 1 graphicsLayer | — | i tasti |

Una `glassSurface` normale ha quindi **4 layer vivi + 1 inutile + 0–3 condizionali** e tre effetti GPU sulla cattura. Una sorgente di pagina
(`glassBackdropSource`) aggiunge per schermata una registrazione dell'intero corpo più un layer `Offscreen` (`GlassMaterial.kt:177-205`).

### 1.3 Da dove arrivano gli uniform

- **Effetti** (`GlassMaterial.kt:1391-1438`): `amount` = intensità; `q` = `FluidGlassQuality.level`; `depth = opticalDepth·amount·q`.
  `colorControls(saturation = 1 + (vibrancy−1)·amount)` (vividezza 1,6 in tutti i ruoli) → `blur(sized.blurPx)` → `lens(h·depth, a·depth·(1+press·pressedDepthBoost), depthEffect, frangia se dispersion && q > 0,75)`.
  I tre numeri vengono da `glassSizedOptics` (`:1759-1796`): `blur = 2,5 dp × blurScale × amount × (0,45 + 0,55·q)`, poi la regola della taglia
  (guadagno smoothstep fra 64 e 320 dp di lato corto: sfocatura ×2, altezza ×1,6 fino a metà lato corto, spostamento ×1,8), con la sfocatura che
  non supera mai la copertura della lente (`cover = altezza·amount·q`).
- **Lente** (`bd/effects/Lens.kt:42-90`): `size`, `offset = −padding`, `cornerRadii` letti dalla `Shape` (**solo** `RectangleShape` e `CornerBasedShape`:
  ogni altra forma perde la lente in silenzio, `:86`, `:113-139`), `refractionHeight`, `refractionAmount` negato, `depthEffect` 0/1. Il padding scende
  dell'altezza (`:51-53`).
- **Shader della lente** (`bd/internal/Shaders.kt:51-85`; frangia `:87-158`): SDF del rettangolo arrotondato; nella banda `−sd < h`:
  `d = circleMap(1 − |sd|/h)·amount`, normale = `normalize(gradSd(gradRadius = min(1,5r, mezzo lato)) + depthEffect·normalize(centro))`, campione spostato di `d·normale`.
- **Anello** (`GlassMaterial.kt:1460-1483`, `bd/highlight/HighlightStyle.kt:86-112`, `Shaders.kt:161-180`): colore e blend da `glassRimRecipe(darkSide)`
  (`:486`), larghezza 0,8 dp, sfocatura 0,4 dp, alfa `highlightAlpha × amount × edgeDepth × q`; intensità per pixel `|dot(grad, (cos 90°, sin 90°))|`:
  piena sopra e sotto, zero sui fianchi, **simmetrica**.
- **Ombra** (`GlassMaterial.kt:1492-1506`, `bd/shadow/Shadow.kt:40-46`): raggio del ruolo, scostamento `raggio/6` in basso, colore `glassShadowColor`
  (nero 16% chiaro / 10% scuro, `:502`), alfa del layer `shadowAlpha × amount × edgeDepth × q`.
- **Pellicola** (`:1524`, `:1800-1840`): `overlay × amount`; FadeDown la tiene piena fino al 55% dell'altezza e poi a zero.
- **Risoluzione della cattura** (`:1545-1549`, `:1620`): `glassResolutionScale(2,5·blurScale)` (`1 − 0,6·min(1, r/10 dp)`) × `backdropResolution` ×
  qualità quantizzata (0,5 sotto q 0,7, `bd/DrawBackdropModifier.kt:443-452`), tetto `fitToTexture` 4096 px (`:428-458`). Con un `layerBlock` la scala è 1.

| ricetta | sfocatura base (dp) | risol. cattura | lente h/a (dp) | cupola / frangia | filo α | ombra raggio·α (effettiva chiaro/scuro) | pellicola α chiaro/scuro | riga |
|---|---|---|---|---|---|---|---|---|
| `Bar` | 12,5 | 0,40 | 20/16 | no/no | 0,50 | — | `barTint` 44/40% | `GlassMaterial.kt:599` |
| `Floating` | 4,5 | 0,73 | 19/29 | no/no | 0,60 | 20·0,7 (11,2/7%) | `floatingTint` 52/48% | `:632` |
| `Interactive` | 3,5 | 0,79 | 10/14 (premuto +60%) | sì/sì | 0,55 | 12·0,5 (8/5%) | `controlTint` 38/34%, 20% d'accento | `:663` |
| `buttonOptics` | 3,5 | 0,79 | 12/14 → 24 premuto | sì/sì | 0,55 | 12·0,5 | `controlTint` | `:708` |
| `Modal` | 8,75 | 0,48 | 20/28 | no | 0,60 | 32·0,8 (12,8/8%) | `modalTint` 54/50% | `:722` |
| `Content` | 2,0 | 0,88×0,5 | 18/22 | no | 0,50 | — | `contentTint` 56/52% | `:757` |
| `stackedContent` | 4,5 | ×0,6 | 14/18 | no | 0,60 | — | 34/30% | `:805` |
| `panelOptics` | 6,25 (8,75 su pagina nitida) | 0,63 | 14/18 (compatto 7/9) | no | 0,45 | 18·0,36 (5,8/3,6%) | `panelTint` = `modalTint` | `:847-940` |
| scrim dei modali | 3,75 | 0,78 | 0 | — | 0 | — | nero 12/20% | `ui/fluid/FluidGlassModal.kt:1001-1022` |
| goccia | 8 → 0 premuta | 1 | 10/14 o 5/10 × p | no/sì | `Ambient`÷1,5 × p | 4 dp, nero 5%, fissa | faccia bianca `1−p` | `ui/glass/interaction/GlassDrop.kt:52` |

Il «grigio» delle pellicole è `glassFilm()` (`GlassMaterial.kt:992-1005`): `lerp(#FAFAFA, surfaceContainerLowest, 0,34)` sul chiaro, `lerp(#4A4A4E, surfaceContainerHigh, 0,34)`
sullo scuro. Tutte le tinte di serie passano da `forSystemContrast()` (`ui/fluid/GlassContrast.kt`).

### 1.4 Cosa scala con `FluidGlassQuality`

`ui/fluid/FluidGlassQuality.kt`: livello 1 a pagina ferma, giù fino a 0,25 fra 24 e 140 px/fotogramma, scende subito e risale del 12% a fotogramma, con un
tetto esterno (le transizioni di rotta, `FluidScreen.kt:912-916`). **Scalano**: profondità della lente (altezza e spostamento), alfa di anello, ombra e
ombra interna, sfocatura (fra 0,45 e 1), frangia (spenta sotto 0,75), risoluzione della cattura (metà sotto 0,7). **Non scala**: la pellicola.
È collegata **solo** al corpo di `FluidScreen` (unico `fluidGlassQualityScrollConnection`, `:915`): altrove vale 1.

### 1.5 I ripieghi

| livello | `glassSurface` | Fluid-physics | altri |
|---|---|---|---|
| API ≥ 33 | tutto | tier `Full` (se la sonda del driver passa) | — |
| API 31–32 | saturazione + sfocatura; `lens()` esce (`Lens.kt:48`); regola della taglia spenta; **padding = raggio di sfocatura** (`Blur.kt:42-46`, la cattura si allarga); anello senza shader = tratto uniforme tutto intorno (`HighlightStyle.kt:100-112` torna null) | `Balanced`: sfocatura e vividezza, tinta e bordo come path, clip = sagoma esatta | goccia: sfocatura senza lente |
| API < 31 | `glassFallback`: tinta quasi opaca ritagliata, niente bordo né ombra | `Lite`: `drawPath(silhouette, tint.fallback)` | scrim = muro nero 92% |
| altra finestra (`ModalBottomSheet`, `Dialog`) | — | — | `fluidStaticGlassSurface`: tre gradienti dipinti (`ui/fluid/FluidGlassControls.kt:315-399`) |

### 1.6 Correzioni e aggiunte allo studio iniziale

- «Un filo speculare bianco al 50–60%»: è bianco solo sul lato scuro (bianco 0,5 × `highlightAlpha` 0,45–0,6 = 22–30% in `Plus`). Sul **chiaro** è
  **nero** 0,28 × 0,45–0,6 = 13–17% in `SrcOver` (`glassRimRecipe`, `GlassMaterial.kt:486-491`). Vero il resto: una direzione fissa (90°), simmetrico.
- «Ombra nero 10–16%»: è il colore; il peso effettivo è × `shadowAlpha` del ruolo = 3,6–12,8%. `Bar` e `Content` non ne hanno.
- «Tinta: pellicola fissa col 20% dell'accento»: vale solo per i controlli. Barre, flottanti, pannelli e card sono un grigio fisso al 40–56% di alfa:
  a separare è la **pellicola**, non la sfocatura. È la leva che un materiale «quasi chiaro» deve ripensare per prima.
- La regola della taglia ha guadagno **zero sotto i 64 dp**: tasti, chip e pillola di navigazione non ne beneficiano.
- La lente vale solo per rettangoli arrotondati (vedi 1.3); una `GenericShape` resta sfocatura + tinta.

---

## 2. Le famiglie di shader

### 2.1 Confronto

| | Kyant: `glassSurface`, `glassControlSurface` | Fluid-physics: `fluidPhysicsSurface` (`fp/FluidPhysicsSurface.kt:76`) |
|---|---|---|
| geometria | un rettangolo arrotondato per nodo, raggi letti dalla `Shape` alla taglia del nodo | piano in array uniform (`PhysicsRenderPlan`, `fp/FluidPhysicsState.kt:888`): ≤6 lastre fuse con smin a raggio per pezzo (`fp/FluidPhysicsShaders.kt:100`), o un anello di ≤64 vertici (sagoma libera) / 96 (goccia) (`:202`); il nodo può essere più grande della sagoma |
| maschera | clip hardware del layer | alpha di copertura nello shader (AA 1,25 px, `fp/FluidPhysicsEffects.kt:24`); clip del nodo = suo rettangolo (`GlassClipGeometry`, `fp/FluidPhysicsShape.kt:23`) |
| lente | `circleMap` sulla banda, normale dell'SDF, cupola 0/1 | stessa formula; cupola come peso; normali mediate su 4 dp nel poligono; `magnify` della lente in moto |
| frangia | 7 tap, sorgente separata | 7 tap (`PhysicsDispersionBlock`, `:331`), varianti separate e sondate a parte |
| tinta | `drawRect` in `onDrawSurface` | `drawPath(silhouette)`; **nello shader** con più pezzi o con la goccia (`tintInShader`, `FluidPhysicsState.kt:962`) |
| bordo | `HighlightNode` | `PhysicsRimRenderer` (`fp/FluidPhysicsRim.kt:82`): a riposo = HighlightNode al pixel, registrato una volta; lastra in viaggio = stessa banda senza sfocatura; gruppo/poligono = gradiente verticale `glassRimRamp`; goccia = mesh di triangoli |
| ombra | `ShadowNode` (BlurMaskFilter per taglia) | `PhysicsShadowLayers` (`fp/FluidPhysicsShadow.kt:188`, due layer stirati e sfumati) o `fluidPhysicsShadow` (bitmap a metà risoluzione stirato, `:67`) |
| trasformazioni | `layerBlock` invertito nel campionamento (crescita del tasto) | scalare è vietato: l'oltrepasso è gonfiore della geometria |
| contorno vivo | — | goccia elastica a 48 molle (`fp/FluidPhysicsLiquid.kt:92`) |
| falloff | `Uniform`, `FadeDown` | solo uniforme |
| pressione | crescita/deriva nel `layerBlock`, lente più profonda, bagliore | lente più profonda e bagliore, niente crescita (`FluidPhysicsSurface.kt:115-127`) |
| livelli | API 31/33 | `Full`/`Balanced`/`Lite` + sonda del driver (`fp/FluidPhysicsTier.kt:16-117`) |

Limiti di Fluid-physics: gruppo ≤6 pezzi **solo `Slab`** (`fp/FluidForm.kt:122-147`), costanti appaiate `PhysicsMaxPieces`/`MaxPieces` e
`PhysicsMaxVertices`/`MaxVertices` (`FluidPhysicsShaders.kt:31-44`), scalo obbligato gruppo↔sagoma libera, loop AGSL a indice costante.

### 2.2 Altre ricette sullo stesso nodo

- **Goccia** (`ui/glass/interaction/GlassDrop.kt:239`): pomelli, pillole di segmentato e rail, indice, riga riordinata (8 siti). Faccia piena → lente sotto il dito.
- **Goccia del menu** (`ui/fluid/FluidGlassMenuPanel.kt:525`): la stessa ricetta riscritta inline con `drawBackdrop`.
- **Tab bar flottante** (vendored, `ui/fluid/FluidFloatingTabBar.kt`): riga nascosta tinta registrata e campionata col proprio blur (`:2347`), puck con lente 15/18 × p (`:2527`), `nestInContainer = false`.
- **`gooey`** (`ui/fluid/FluidGooey.kt:82`): sfocatura + soglia d'alfa su un layer Offscreen, per la piega della tab bar (`FluidFloatingTabBar.kt:451`). È una terza tecnica di fusione.
- **Vetro dipinto** (`fluidStaticGlassSurface`): fogli e avvisi di piattaforma.

### 2.3 Cosa condividono già

`DrawBackdropNode` (cattura, annidamento, export, qualità, tetti); `glassSizedOptics` (regola della taglia); `physicsLensUniforms`
(`fp/FluidPhysicsMaterial.kt:145`) che rifà la formula degli effetti di `glassSurface`; `GlassOptics`/`GlassTint`/`GlassRole`, `sanitized()`;
`glassRimRecipe`, `glassShadowColor`, `glassRimRamp` (`GlassMaterial.kt:486-546`); lo shader `HighlightStyle.Default` usato dal bordo a riposo;
`layerResolutionScale`/`HighlightLayerMaxPx`; `glassButtonGlowRecipe`; l'aggancio `GlassClipGeometry` dentro lo `ShapeProvider` vendored.
**Duplicati**: il preludio SDF (privato in `Shaders.kt`, ridichiarato in `FluidPhysicsShaders.kt:47`), `circleMap`, il blocco della frangia, gli specchi Kotlin
(`fp/FluidPhysicsGeometry.kt`) che esistono solo per Fluid-physics.

### 2.4 Ostacoli concreti a un motore solo

1. **Geometria**: la lente di Kyant prende la forma da una `Shape` sulla taglia del nodo; Fluid-physics da un piano di uniform indipendente dal nodo.
2. **Maschera vs clip**: Kyant ritaglia in hardware; Fluid-physics calcola la copertura per pixel su tutto il nodo (che per menu e modali è lo schermo). Uno shader unico con maschera paga per pixel ciò che oggi è un clip.
3. **Tinta**: rettangolo, gradiente FadeDown o shader, a seconda del caso.
4. **Bordo**: cinque strade (HighlightNode, riposo, viaggio, rampa verticale per gruppi, mesh della goccia). Sui gruppi il bordo è un'approssimazione, non il profilo per pixel.
5. **Ombra**: tre strade (`ShadowNode`; due layer stirati e sfumati; bitmap a metà risoluzione stirato), con sopra `ShadowNode` ricette diverse: quella del ruolo, `glassShadow` (`GlassMaterial.kt:1582`), l'ombra fissa della goccia e del puck.
6. **Trasformazioni**: il tasto cresce per `layerBlock`; la superficie fisica non può scalare (prima trappola).
7. **Padding**: Fluid-physics esige sfocatura ≤ altezza di lente (padding zero e fermo); `glassSurface` lo tollera (lo scrim ha lente 0 e padding = sfocatura; sotto API 33 padding sempre).
8. **Contenuto**: figlio del nodo in `glassSurface`; nodo fratello con `exports` + `glassContainer` in Fluid-physics.
9. **Ricette d'identità bloccate**: goccia, pomelli, puck sono «il metro» (R1) con test bit per bit (`test/…/GlassDropSquashTest.kt`, `GlassDropFaceTest.kt`).
10. **Tier e sonda** esistono solo in Fluid-physics; il renderer di Kyant non ha un ripiego per driver che rifiutano.

---

## 3. Chi usa cosa

### 3.1 engine-ui

| componente | porta del vetro | ottica | riga |
|---|---|---|---|
| barra in alto (`FluidScreen`, `FluidCollapsingTopBar`, `FluidPageChrome`, barra ancorata) | `FluidTopBarGlass` → `glassSurface(FadeDown)` | `Bar`, `barTint`, `exports` | `ui/fluid/FluidScreen.kt:1370` |
| `FluidBarAction`, `FluidGlassIconButton`, `FluidGlassButton` | `glassControlSurface` | `buttonOptics` | `ui/fluid/FluidGlassControls.kt:553`, `:633` |
| `glassControlSurface` stesso | `glassSurface` + `layerBlock` | `Interactive` | `:264` |
| `FluidChip` | `glassControlSurface` | | `ui/fluid/FluidControls.kt:758` |
| `FluidSegmentedControl` | pista `glassSurface`, pillola `glassDrop` | `Content` | `:318`, `:570` |
| `FluidCard`, `FluidListGroup`, `FluidMetricTile` | `fluidContentGlass` → `glassSurface` | `Content`, `sampleOnce` | `ui/theme/FluidComponents.kt:176` |
| `FluidSyncIndicator` | `glassControlSurface` | | `ui/theme/FluidComponents.kt:707` |
| righe di `FluidGroupedItems` | `glassSurface` | `Content`, `sampleOnce` | `ui/fluid/FluidGroupedItems.kt:324` |
| `FluidFloatingCapsule`, `FluidSearchField` | `fluidFloatingGlass` → `glassSurface` | `Floating` | `ui/fluid/FluidFloatingCapsule.kt:392` |
| `FluidFloatingTabBar` | pannello `glassSurface` (se l'app non lo dà); riga nascosta e puck `drawBackdrop`; `gooey`; 4 `GlassTouchHighlight` | `Floating` | `ui/fluid/FluidFloatingTabBar.kt:407`, `:2347`, `:2527`, `:451` |
| `FluidTabRail`/`FluidTabBar` | `glassSurface` + indicatore `glassDrop` | `Floating`, `exports` | `ui/fluid/FluidTabBar.kt:592`, `:629` |
| `FluidSidebar` | `glassSurface` | `Floating` | `ui/fluid/FluidSidebar.kt:82` |
| `FluidSwitch`, `FluidSlider`, `FluidScrubber`, `FluidReorderable` | `glassDrop` | goccia | `FluidSwitch.kt:437`, `FluidSlider.kt:494`, `FluidScrubber.kt:685`, `FluidReorderable.kt:766` |
| `FluidFastScroller` | bolla `glassSurface`, pomello `glassDrop` | `panelOptics(compact)` | `ui/fluid/FluidFastScroller.kt:403`, `:439` |
| `FluidSectionIndex` | pista `glassSurface` (intensità = apertura), lente `glassDrop`, bolla `glassSurface` | `Interactive`, panel | `ui/fluid/FluidSectionIndex.kt:260`, `:355`, `:394` |
| `FluidCoachMark` | `glassSurface` | panel su pagina nitida | `ui/tutorial/FluidCoachMark.kt:294` |
| `FluidNotification` | `glassSurface` | panel su pagina nitida, `exports` | `ui/fluid/FluidNotification.kt:1123` |
| `FluidDrawer` | scrim + `glassSurface` | panel su pagina nitida | `ui/fluid/FluidDrawer.kt:451` |
| host dei modali | scrim `glassSurface`; popover/Expand con contenuto `fluidPhysicsShadow` + `fluidPhysicsSurface`; foglio `glassSurface`; riga sollevata `fluidPhysicsSurface` | panel | `ui/fluid/FluidGlassModal.kt:1063`, `:1299-1306`, `:1915`, `:1716` |
| menu (Expand, contestuale) e avviso nell'host | `fluidPhysicsSurface` | panel compatto, `opticsFrom`/`tintFrom` | `ui/fluid/FluidMenuLayer.kt:366`, `:600` |
| goccia delle voci | `drawBackdrop` diretto | goccia | `ui/fluid/FluidGlassMenuPanel.kt:525` |
| `FluidMorphMenuButton` / `FluidMorphMenuHost` | `glassControlSurface` / `fluidPhysicsSurface` | panel compatto | `fp/FluidMorphMenu.kt:362`, `:489` |
| `FluidGlassMenuButton` (deprecato) | `glassControlSurface` | | `ui/fluid/FluidGlassMenuButton.kt:170` |
| pezzi della barra ancorata in viaggio | `fluidPhysicsSurface` | `Interactive`, `buttonOptics` | `ui/page/FluidPageAnchoredBar.kt:204` |
| `FluidSheet` e avviso fuori dall'host | `fluidStaticGlassSurface` | dipinto | `ui/fluid/FluidSheet.kt:134`, `:370` |

Conteggio dei siti diretti in engine-ui: `glassSurface` 16 (3 sono involucri: `glassControlSurface`, `fluidFloatingGlass`, `fluidContentGlass`),
`glassControlSurface` 6, `fluidPhysicsSurface` 6, `glassDrop` 8, `drawBackdrop` diretto 3, `fluidStaticGlassSurface` 2, `fluidPhysicsShadow` 1.
Un materiale nuovo dentro `glassSurface` + `fluidPhysicsSurface` copre quasi tutto; restano fuori goccia, tab bar, goccia del menu, `gooey` e il dipinto.

### 3.2 Altri moduli e galleria

- **engine-ai-ui**: `glassSurface` 5, `glassControlSurface` 4, `FluidGlassIconButton` 3 (`FluidComposer`, `FluidAssistantDock/Card/Parts/TextBar`).
- **engine-wear**: `glassSurface` 4, `glassControlSurface` 2, e soprattutto `engine-wear/…/wear/glass/FluidWearGlass.kt:37` che costruisce a mano
  un `GlassOptics(...)` e tre `GlassTint(...)`: si rompe con qualunque cambio di quei costruttori.
- **sample** (98 file, 31,5 k righe): `glassSurface` 2 (`GlassOnGlassDemo.kt`) + 1 in androidTest, `glassControlSurface` 1, `fluidPhysicsSurface` 1
  (`playground/PlaygroundStage.kt:93`, con `optics(Floating).copy(...)`), `drawBackdrop` 0; per componente: `rememberGlassBackdrop` 43,
  `glassBackdropSource` 12, `FluidGlassIconButton` 30, `FluidGlassButton` 11, `FluidBarAction` 22, `FluidFloatingCapsule` 10,
  `FluidFloatingTabBar` 8, `FluidSearchField` 8, `FluidMorphMenu*` 35, `FluidGlassModal*` 51, `FluidScreen` 31, `GlassDefaults.` 19.

---

## 4. Le barre

### 4.1 La barra in alto e la sua fascia

- Riquadro alto **barra di stato + 44 dp + 34 dp** di coda (`FluidScreenDefaults.GlassFadeTail`, `ui/fluid/FluidScreen.kt:417`, `:1301`).
- `FluidTopBarGlass` (`:1361-1379`): `glassSurface(falloff = FadeDown)` → ruolo `Bar` (sfocatura 12,5 dp, lente 20/16, filo 0,5, cattura al 40%),
  pellicola `barTint` (44/40%) piena fino al 55% e poi a zero; tutto il materiale passa in un `saveLayer` con un gradiente `DstIn`
  (`fadeDownStops`: tenuta = copertura × 0,62, copertura = smoothstep(intensità), `GlassMaterial.kt:1879-1916`) **a ogni fotogramma**.
- Intensità (`calculateGlassIntensity`, `:1153`): max fra lo scorrimento (zona morta 8 dp, rampa 64 dp) e il titolo che si aggancia oltre il 16%.
- Cosa porta oltre all'aspetto: (i) `exports = barGlass` e `controlBackdrop = combined(pagina, barGlass)` (`:1295-1296`) — le azioni rifrangono la
  barra, non la pagina; (ii) `LocalGlassBackdrop` e `LocalFluidCanvasBackdrop = null` per i controlli nella barra (`:1309-1315`); (iii) la leggibilità del
  titolo agganciato. Nota: il materiale esportato **non porta la maschera FadeDown** (la maschera sta fuori dal nodo).
- Consumatori unici: `GlassFalloff.FadeDown` è usato solo a `FluidScreen.kt:1374`; `barTint` solo a `FluidScreen.kt:1292` e
  `ui/page/FluidPageAnchoredBar.kt:134`, `:556`. Toglierla tocca: `FluidTopBar` (`:1247`), la barra ancorata (`FluidPageAnchoredBar.kt:179`), `FluidPageChrome`
  (`:563`, intensità fissa 1), i parametri pubblici `barTint` (`FluidCollapsingTopBar`, `ui/fluid/FluidCollapsingHeader.kt:252`; `FluidPageChrome`, `:496`),
  `GlassFalloff`, `GlassEdge`, `FluidScreenDefaults.GlassFadeTail/ShieldDeadZone/ShieldRampDistance`, i test `test/…/FluidChromeMotionTest.kt` e
  `FluidScrollPhysicsTest.kt`. Le azioni, senza contenitore, campionerebbero la pagina (l'annidamento non trova un antenato di vetro).

### 4.2 `FluidCollapsingTopBar`, `FluidTopBarControls`, `FluidBarAction`

- `FluidCollapsingTopBar` (`FluidCollapsingHeader.kt:241`): la stessa `FluidTopBar` per chi ha una lista sua; il fondale lo passa l'app.
- `FluidTopBarControls` (`FluidScreen.kt:1430`): interno, misura soltanto le larghezze di inizio e fine riga perché il titolo agganciato si fermi prima (3.0.1).
  Non è un modello di dichiarazione.
- `FluidBarAction` (`:1672`, variante con contenuto `:1749`): un `FluidGlassIconButton` da 44 dp (bersaglio 48, `ExpandRound`). Con `actions` diventa un
  menu Expand dell'host (`FluidBarActionImpl`, `:1787`): apertura sincrona nel gesto (`:1834`), lente nascosta nel disegno (`:1866`), materiale di partenza
  `buttonOptics` + `controlTint` (`:1817-1818`).

### 4.3 La barra ancorata dell'host

- Una per host (`ui/page/FluidPageAnchoredBar.kt:95`). Le pagine si registrano con `FluidPageBarSpec` (`ui/page/FluidPageBar.kt:30`) da
  `FluidTopBar` (`FluidScreen.kt:1268`).
- Vetro: `FluidTopBarGlass` sulle due registrazioni impilate durante il moto, intensità pesata fra le due pagine (`:136-150`, ricattura a ogni fotogramma);
  lo stesso impilamento va alla chrome (`setTransitionBackdrop`, `:128-131`).
- **Il viaggio dei pezzi**: ogni `glassControlSurface` dentro la barra si iscrive come `FluidBarPiece` (capsula solo se `shape == FluidCapsuleShape`,
  `ui/fluid/FluidGlassControls.kt:195-202`) e durante il moto toglie il proprio vetro (`:206`). `FluidBarJourney.recompute` (`FluidPageAnchoredBar.kt:428`)
  accoppia i pezzi per lato dall'esterno verso l'interno (`barPieceJourney`, `ui/page/FluidPageMotion.kt:469`; chi resta solo nasce da un seme di 1 px) e
  guida un `fluidPhysicsSurface` con `driveExternally` e `barMorph(e) = smoothstep((e−0,1)/0,8)` (`:415`). Oltre 6 coppie o con un controllo non capsula:
  niente morph. Le icone si **incrociano in dissolvenza**: uscita `1 − m/0,35`, entrata `(m − 0,25)/0,5` (`:422`).
- Solo fra due pagine: a riposo `recompute` esce subito (`FluidPageAnchoredBar.kt:430-433`).

### 4.4 In basso

- `FluidFloatingTabBar` (`FluidFloatingTabBar.kt:207`, 3399 righe vendored, formattazione a 4 spazi tenuta apposta): tre stati `INLINE`/`EXPANDED`/
  `SEARCH_EXPANDED` (`:173`) con `SharedTransitionScope` e l'impulso `gooey` (12 dp). La ricerca: `searchMode` + `searchBarContent` riducono il gruppo
  all'icona corrente e allargano lo slot isolato. Il pannello proprio **non è congelato durante la piega** (`:395-401`: ricattura per fotogramma).
  Le schede si dichiarano con un DSL (`FluidFloatingTabBarScope.tab(key, …)`, `:945`): l'unica barra dichiarativa dell'engine.
- `FluidFoldingTabBar`: deprecato, è la `FluidFloatingTabBar`.
- `FluidFloatingCapsule` (`FluidFloatingCapsule.kt:224`): una lastra `Floating`; azioni piatte (`FluidCapsuleAction`, `:307`), vetro vero solo se l'app mette
  un `FluidGlassIconButton` negli slot (vetro su vetro). Posizioni `Free`/`ReplacingTabBar`/`AboveTabBar`; entra e esce con `AnimatedVisibility` (slide + fade).
  Se il contenuto cambia, la riga si ridispone senza animazione e il vetro ricattura per la taglia nuova.
- `FluidSearchField` (`ui/fluid/FluidSearchField.kt:195`): da cerchio a campo; la larghezza è un `animateFloatAsState(FluidMotion.standard)` in un modificatore di
  layout (`:214`, `:440`), stessa lastra per tutta l'apertura, quindi **ricattura a ogni fotogramma**; `BackHandler` chiude. Non si fonde con i vicini.

### 4.5 Come si dichiarano le azioni, e cosa succede quando cambiano

- In alto: `actions: @Composable RowScope.() -> Unit` su `FluidScreen` (`:631`), `FluidCollapsingTopBar`, `FluidPageChrome` (`:497`). Nessuna nozione
  di gruppo, spaziatore, azione principale.
- In basso: DSL delle schede; slot `leading`/`content`/`trailing` della capsula.
- **Stessa pagina**: la `Row` di `FluidTopBarActions` (`FluidScreen.kt:1454`, `spacedBy(2.dp)`) si ricompone; i tasti compaiono e spariscono in un fotogramma,
  il titolo cambia inset. Nessun codice dell'engine anima il cambio.
- **Fra due pagine**: morph dei pezzi (4.3), titolo agganciato in dissolvenza con un passo di 16 dp, tasto indietro fermo al pixel se ce l'hanno entrambe.

---

## 5. Menu e modali

| componente | apertura / chiusura | sfondo | back | righe |
|---|---|---|---|---|
| menu Expand (`FluidBarAction(actions)`, menu dell'host) | `FluidMenuJourney`: molla `dropOpen` (ζ 0,72, k 500, ~0,28 s) / `dropClose` (k 158, ~0,5 s); finestra = `fluidPhysicsSurface` guidata da fuori dal tasto **premuto** al pannello sull'ancora, contorno a goccia; materiale tasto→pannello entro p 0,6; faccia del tasto spenta entro 0,35; voci da 0,4 a 0,75 (chiusura 0,94→0,62); l'uscita aspetta la goccia ferma | scrim: sfocatura 3,75 dp, nessuna lente, nero 12/20%, in dissolvenza 110/280 ms; la finestra si dipinge la stessa quota di nero | `PredictiveBackHandler`: scrim × (1 − 0,6·p), poi congedo | `ui/fluid/FluidMenuLayer.kt:103-440`, `ui/fluid/FluidMenuJourney.kt:63-93`, `:582-728`, `ui/fluid/FluidMotion.kt:219-253` |
| menu contestuale (riga, card, `FluidListRow(contextActions)`) | stesso layer; pannello accanto alla riga, da un seme di 28 dp sotto il dito (`FluidMenuJourney.kt:455`); riga sollevata = immagine registrata scalata ×1,03 + gonfiore, più una lastra Fluid-physics sorella col colore della riga | lo stesso scrim | predittivo | `ui/fluid/FluidGlassModal.kt:1666-1770`, `:2307` |
| `FluidMorphMenuButton`/`Host` | stesso viaggio, stesso pannello | **nessuno scrim**: fasce invisibili che chiudono al tocco fuori | `BackHandler` non predittivo | `fp/FluidMorphMenu.kt:391-555` |
| `FluidGlassMenuButton` | deprecato: pressione lunga → Expand dell'host | — | — | `ui/fluid/FluidGlassMenuButton.kt:79` |
| `FluidGlassModalPortal`: Popover, Expand con contenuto, Sheet, FullScreen | due molle di scala X/Y spaiate + dissolvenza; con origine la finestra Fluid-physics viaggia dall'ancora (rincorsa ease-in + molla), ombra `fluidPhysicsShadow`; Sheet sale; FullScreen opaca | scrim | predittivo | `ui/fluid/FluidGlassModal.kt:257-306`, `:710-930`, `:1186` |
| `FluidSheet` | con host → foglio di vetro dell'host; senza → `ModalBottomSheet` | senza host: scrim nero 42%, vetro dipinto | di piattaforma | `ui/fluid/FluidSheet.kt:93-140` |
| avviso nell'host | dissolvenza + geometria `snappy`; `fluidPhysicsSurface` panel | scrim | `BackHandler` | `ui/fluid/FluidMenuLayer.kt:467-640` |
| `FluidDrawer` | spostamento di layout, molla `DrawerTravel` criticamente smorzata; vetro `glassSurface` panel | scrim con intensità = corsa | predittivo | `ui/fluid/FluidDrawer.kt:348`, `:400`, `:451` |
| `FluidNotification` | `AnimatedVisibility`: slide −1/3, fade, **`scaleIn(0,97)` attorno al vetro** | nessuno | — | `ui/fluid/FluidNotification.kt:629-655` |

Fatti trasversali:
- La pagina dietro un menu è **appena** velata (3,75 dp, 12/20%): la pressione lunga di iOS chiede una sfocatura e uno scurimento più forti, e lo scrim ha
  `sizeResponse = 0` proprio per non diventare una media (`FluidGlassModal.kt:1018-1022`).
- Le voci non stanno *dentro* la lente: `FluidGlassMenuPanel` le disegna in un fratello con alfa e mappa del viaggio, ritagliate sul corpo
  (`ui/fluid/FluidGlassMenuPanel.kt:278-312`). Il «testo deformato dalla lente» dei riferimenti non c'è.
- Nessun menu trasforma la barra: gli altri tasti della barra restano dove sono, a piena opacità.
- La notifica viola alla lettera la regola «un ingresso non scala un vetro» (`ref/regole.md:394`): `scaleIn` di un antenato del vetro.

---

## 6. Tocco

**Tasto di vetro premuto** (`glassControlSurface` + `FluidPressState`):
- pressione al contatto, molle di LibrePods `spring(0,5, 300)` su e giù, rilascio immediato (`ui/fluid/FluidPress.kt:186-276`);
- crescita: tasti con testo 10% (tetto 16 dp sul lato lungo), tondi 50% (tetto 24 dp in tutto: 44→66, 54→78) (`:117-161`, `:556-591`);
- deriva `m·tanh(0,05·spostamento/m)` per asse, ritorno `spring(1, 300)` (`:541-542`); allungamento `+0,1·|cos θ·Δx/M|·min(w/h, 1)`, senza tetto (`:539-540`);
- tutto nel `layerBlock` di `drawBackdrop` (`ui/fluid/FluidGlassControls.kt:274-292`), che il renderer inverte quando campiona (`bd/backdrops/LayerBackdrop.kt`, `inverseTransform`);
- lente 12/14 dp → 24 dp premuto (`pressedDepthBoost` 0,714), cupola e frangia (`GlassMaterial.kt:708-711`);
- bagliore `glassButtonGlowRecipe` (`ui/glass/interaction/GlassInteraction.kt:404`): velo 0,10 + punto 0,15, raggio = lato lungo, core 0,5, bianco `Plus`
  anche sul chiaro; segue il dito (`press.glow`); sotto API 33 lavata piatta 0,25.
- Altri bagliori: Kyant sullo scuro (velo 0,08, punto 0,15, raggio 1,5 lati corti); vetro chiaro (0,015/0,055, raggio 1,15, core 0); superficie chiara non vetro: nero `SrcOver` (`:344-382`).

**Trascinato** (`GlassDragAnimation`, `:161`): cinque molle — valore (1, 1000), pressione (1, 1000), scala X (0,6, 250), scala Y (0,7, 250), velocità
(ζ, 300); gesti senza soglia (`inspectDragGestures`); squash della goccia dalla velocità `/10`, tetto 0,2 (interruttore `/50` con segno)
(`ui/glass/interaction/GlassDrop.kt:135`).

**«Gel» che esiste**: l'allungamento direzionale (scala per asse, non il contorno), lo squash della goccia, e la goccia elastica di Fluid-physics
(48 molle, dure vicino all'ancora e morbide lontano, `fp/FluidPhysicsLiquid.kt`), che però vive **solo nei viaggi**: sotto il dito una superficie fisica
approfondisce la lente e accende il bagliore, nient'altro. **Luce che esiste**: il bagliore sul solo elemento premuto; nessuna propagazione ai vicini, nessuna
luce che corre sul bordo, angolo del filo fisso a 90°.

---

## 7. Chiaro e scuro

- **Decisione**: `isDarkSurface() = LocalFluidSurfaceSide ?: surface.luminance() < 0,5` (`GlassMaterial.kt:1271-1281`); `FluidTheme` fornisce
  `LocalFluidSurfaceSide = isDark` (`ui/theme/FluidTheme.kt:169`). In pratica: il modo del tema.
- **Chi la usa**: `glassFilm`, `glassRimRecipe`, `glassShadowColor`, le ricette del bagliore (come flag passato a mano), le facce della goccia
  (`GlassDropFaces`, `GlassDrop.kt:163`), il vetro dipinto, la superficie della notifica.
- **Tinte `…OnPhoto`**: `barTintOnPhoto`/`floatingTintOnPhoto` scelgono il ramo col lato del tema (`GlassMaterial.kt:1083-1119`); l'unico consumatore è
  `fluidFloatingGlass` con `onPhoto = true` (`FluidFloatingCapsule.kt:395`), esposto da `FluidFloatingCapsule` e `FluidSearchField`; le barre in alto
  accettano un `barTint` qualsiasi.
- **Contrasto di sistema**: `LocalFluidSystemContrast` (`FluidTheme.kt:171`), `forSystemContrast` alza pellicola e ripiego (velo 0,40 / 0,85) e il bordo
  sottile fino a ×2,5 (`ui/fluid/GlassContrast.kt`).
- **Lettura dello sfondo**: **nessuna**. Nessun `PixelCopy`, `readPixels`, `toImageBitmap` o simile in engine-ui; `luminance()` si chiede solo a colori del tema.
- **Colore di icone e testo sul vetro**: dal tema. Icone di `FluidBarAction` = `primary` (`FluidScreen.kt:1695`); testo della barra `onBackground`
  (`:919`, `FluidPageAnchoredBar.kt:189`); capsula `onSurface` (`FluidFloatingCapsule.kt:272`) con azioni `primary`/`error`; host dei modali `onSurface`
  (`FluidGlassModal.kt:628`); pagina intera `fluidContentColorOn(background)` (`:1992`). Le uniche correzioni d'inchiostro sono a 3:1 contro colori noti:
  icona del puck premuto (`legibleInk`, `FluidFloatingTabBar.kt:3016`) e riempimento delle barre di avanzamento (`ui/fluid/FluidFillContrast.kt`).

---

## 8. Regole che non si toccano

| # | invariante | dove è scritto | perché esiste |
|---|---|---|---|
| 1 | padding di cattura `max(0, sfocatura − altezza)`, **fermo** durante un viaggio; la sfocatura non supera la copertura della lente; sotto ¼ px vale zero | `GlassMaterial.kt:1750-1758`; `fp/FluidPhysicsMaterial.kt:189-211`; `bd/effects/Lens.kt:51-53`; `ref/regole.md:203-207`; `ref/componenti/fluid-physics-motore.md:22-27` | un padding che cambia rifà la cattura a ogni fotogramma |
| 2 | solo il padding invalida la cattura, non i numeri degli effetti | `bd/DrawBackdropModifier.kt:966-979` | animare sfocatura o lente costava una ricattura per fotogramma (il menu che scattava) |
| 3 | la cattura vale finché non cambiano taglia, elemento, padding o offset **relativi** alle sorgenti; una sorgente che si ridisegna non invalida niente | `:749-772`; `bd/Backdrop.kt` (`layerSources`) | otto superfici = otto rigiochi dello schermo per fotogramma |
| 4 | tetti: cattura ≤ 4096 px di lato lungo, scala ≥ 0,05; layer ausiliari ≤ 1024 px; scala di qualità a due soli valori | `:428-458`; `bd/highlight/HighlightModifier.kt:106-113`; `ref/regole.md:163-182` | oltre il tetto la registrazione torna vuota = rettangolo nero; la cache HWUI esplode |
| 5 | **mai un layer scalato sopra un vetro**: la scala sta nella geometria o nel `layerBlock` di `drawBackdrop` | `ref/regole.md:697-700`, `:394`; `docs/10-coerenza.md:124` | il fondale campionato si muoverebbe con la scala |
| 6 | cancello del fotogramma di confine: `drawWithContent { if (amount > 0,004) drawContent() }` | `ref/regole.md:701-705`; `FluidGlassModal.kt:1041-1045` | lampo a piena forza misurato (luminanza 214→190) |
| 7 | gli `exports` di uno scrim sono registrati a forza piena: chi campiona dipinge da sé la propria quota | `ref/regole.md:706-709`; `FluidMenuLayer.kt:249-254` | il pannello restava scuro mentre lo scrim sfumava |
| 8 | vetro su vetro: il materiale pubblicato **esclude il contenuto**; il cablaggio a mano vince; `nestInContainer = false` per chi ricostruisce da sé | `ref/regole.md:226-278`; `bd/DrawBackdropModifier.kt:896-900` | feedback ottico; doppia sfocatura (2.12.1) |
| 9 | due registrazioni: il canvas si registra prima della lista; la sorgente è opaca dentro la registrazione | `GlassMaterial.kt:172-175`, `:279-293`; `FluidScreen.kt:935-975`; `ref/regole.md:150-157` | una card che campiona se stessa; testo su trasparenza = sbavatura |
| 10 | il vetro va sul contenitore, mai sulla riga | `ref/regole.md:142-148` | 8 superfici diventano 80 |
| 11 | un bordo solo (`glassRimRecipe`): sul chiaro colore scuro **e** `SrcOver` insieme; niente ombra interna con un bordo; ombra del vetro, mai di piattaforma | `ref/regole.md:312-347` | girare il solo colore «compila e non disegna niente»; due bordi invece di uno spessore |
| 12 | un materiale per i pannelli flottanti (`panelOptics`, `panelTint`), mai numeri propri | `ref/regole.md:280-310`; `test/…/GlassPanelMaterialTest.kt` | cinque vetri per la stessa famiglia |
| 13 | goccia e lastra non si mescolano; lo squash è della goccia; pomelli e pillola sono «il metro» (R1), bit per bit | `ref/regole.md:349-394`; `docs/10-coerenza.md:262-272` | confronti fra fotogrammi prima/dopo |
| 14 | discipline di Fluid-physics: sorgenti AGSL costanti, un passaggio per pixel, maschera nello shader, `Shape` stabile a riposo, niente allocazioni per fotogramma, costanti appaiate, mai il lerp oltre l'1, riposo bit-identico | `fp/FluidPhysicsShaders.kt:1-22`; `ref/componenti/fluid-physics-motore.md:9-27`, `:146-154` | cache shader per testo; re-clip per fotogramma; scalino a partenza e arrivo |
| 15 | «nessuno shader di vetro nuovo accanto a `GlassMaterial`»: Fluid-physics è la deroga | `ref/regole.md:675-678` | un materiale solo |
| 16 | un viaggio cancellato si ferma dove si trova; ogni transito ha il suo `Animatable` nato a zero | `ref/regole.md:667-673`; `fluid-physics-motore.md:71-72` | il menu chiuso a metà saltava al pannello pieno |
| 17 | `sampleOnce` solo su fondale statico; `frozen` rinfresca ogni 110 ms e riprende sempre una cattura fresca; sorgente sparita tenuta 400 ms; `onReset` sui nodi riciclati | `bd/DrawBackdropModifier.kt:153-167`, `:463-505`, `:798-802`, `:1006-1016` | copie vecchie della pagina; testo nitido per un fotogramma al cambio pagina; tessere senza vetro |
| 18 | layer del pannello `Auto`, non `Offscreen`; clip rounded-rect hardware, non `Generic`; `ShapeProvider` uguale a se stesso | `:545-556`; `bd/internal/ShapeProvider.kt` | 18 → 85 ms per fotogramma sul tablet; mascheratura offscreen per pannello |
| 19 | la pellicola non è `MaterialTheme.surface` | `GlassMaterial.kt:974-990` | su AMOLED il vetro spariva |
| 20 | base 2,5 dp; oltre ~8 dp un pannello trasmette la media | `GlassMaterial.kt:108-131`; `ref/regole.md:184-224` | l'errore del 16 dp |
| 21 | lo scrim arriva per alfa, non per intensità; `sizeResponse = 0` | `FluidGlassModal.kt:1017-1045` | ricattura dello schermo intero a ogni fotogramma; scrim mediato |
| 22 | il contrasto di sistema si applica una volta sola | `ref/componenti/vetro.md:458-462` | due veli su ripiego e bordo |
| 23 | il contenuto non si stira mai: layout alla taglia d'arrivo, dissolvenza e zoom uniforme | `fp/FluidPhysicsSurface.kt:454-501`; `fp/FluidPhysicsState.kt:45-48` | «la tendina tirata su un cartello già scritto» (1.8.x) |
| 24 | licenze: il renderer è Kyant (Apache-2.0); pressione, `ButtonOptics` e bagliore dei tasti vengono da **LibrePods, GPL-3.0** («chi distribuisce un'app con questo file la distribuisce sotto GPL-3.0») | `ui/fluid/FluidPress.kt:1-29`; `GlassMaterial.kt:694-711`; `GlassInteraction.kt:384-403` | vincolo legale già presente |
| 25 | metodo: un fotogramma si giudica nei fotogrammi (`-fps_mode passthrough`, `signalstats`), non «per costruzione» | `ref/regole.md:711-715`; `docs/10-coerenza.md:276-280` | diagnosi sbagliate su cronologie riordinate |

---

## 9. Test e collaudo

- **engine-ui**: 120 file in `engine-ui/src/test`, **JVM puri** (junit + coroutines-test, `returnDefaultValues = true`, nessun Robolectric:
  `engine-ui/build.gradle:21-28`, `:70-71`).
  - vetro: `GlassMaterialStateTest` (ordine dei ruoli, `sanitized`, lato), `GlassSizeRuleTest`, `FluidGlassSizeTest`, `GlassPanelMaterialTest`,
    `GlassRimRecipeTest`, `FluidGlassQualityTest`, `FluidSystemContrastTest`, `FluidFillContrastTest`, `GlassNestingTest`, `BackdropTextureCapTest`,
    `SourceGoneHoldTest`, `GlassDropFaceTest`, `GlassDropSquashTest`, `GlowRecipeTest`, `DepthEffectWeightTest`, `FluidGlassControlsTest`,
    `FluidGlassGroupsTest`, `FluidPuckInkTest`, `FluidFloatingTabBarBlurTest/InkTest/LayoutTest/ScrollTest`;
  - Fluid-physics: `RestStateDisciplineTest`, `FluidPhysicsMaterialTest`, `LiquidDropTest`, `DropPlanTest`, `DropRimTest`, `ExternalDriveStartFrameTest`,
    `ExternalGroupDriveTest`, `PolySdfMathTest`, `MatchedRingsTest`, `UniformScaleMappingTest`, `MorphOvershootTest`, `PieceMatchingTest`,
    `SlabParamLerpTest`, `FluidFormGeometryTest`, `FluidPhysicsCancelTest`, `FluidPhysicsJourneyTest`, `FluidPhysicsTierTest`;
  - barre e pagine: `FluidChromeMotionTest`, `FluidChromeTransitionTest`, `FluidFloatingCapsuleTest`, `FluidSearchFieldTest`, `FluidPageMotionTest`,
    `FluidPageInteractionTest`, `FluidScrollPhysicsTest`;
  - menu e modali: `FluidMenuGeometryTest`, `FluidMenuJourneyTest`, `FluidMenuMotionTest`, `FluidMenuPanelTest`, `FluidMenuPanelWidthTest`,
    `FluidMenuRelayTest`, `FluidMenuSelectionTest`, `FluidGlassModalContentTest`, `FluidGlassModalStateTest`, `FluidNotification*Test`,
    `FluidDrawerRulesTest`; tocco: `FluidPressTest`, `FluidMotionTokensTest`.
- **sample/testDebug**: 53 file, ~50 con Robolectric 4.17, SDK 35 (`sample/src/testDebug/resources/robolectric.properties`), `GraphicsMode.NATIVE`, 360 dp.
  Provano **funzione sotto un dito finto**, non aspetto: `AllGlassTest` («il pixel non si prova qui», `stest/AllGlassTest.kt:94-104`),
  `FluidControlBackdropTest`, `FluidGlassFreeSizeTest`, `FluidMenuHandOffTest`, `FluidMorphMenuBackOrderTest`, `FluidPageHostTest`, `FluidTouchTest`,
  `FluidDrawerTest`, `FluidPopoverKeyboardTest`, `FluidTabBarExtensionsTest`…
  **Una sola prova legge pixel**: `FluidPageBarIconDrawTest` (SDK 32, `view.draw(Canvas(bitmap))`, conta pixel visibili;
  «API 32 esercita la maschera RenderEffect senza gli shader AGSL non supportati da Robolectric», `stest/FluidPageBarIconDrawTest.kt:37-44`, `:90-102`).
  `captureToImage` non gira sotto Robolectric (`stest/FluidNavHostPageColorTest.kt:48`).
- **androidTest**: solo il contenuto web. **Test di screenshot**: nessuno (niente Roborazzi/Paparazzi nei build file).
- **Galleria**: «Da provare» (`sample/ToTry.kt`, aree Tasti, Menù, Pannelli e materiale, Barra in basso, Pagine…), «Menù liquidi»
  (`sample/catalog/MenusDemo.kt`), «Tutto in vetro» (`sample/catalog/AllGlassDemo.kt`), «Capsule e pezzi piccoli», «Pannello laterale e schede»,
  «Avvisi con azione»; schede Materiale (`sample/MaterialTab.kt`), Playground (`sample/playground/PlaygroundTab.kt`, `PlaygroundStage.kt`: interruttore del
  liquido, manopole della goccia), Controlli (`ControlsTab.kt`, `TouchLab.kt`), Chrome (`ChromeTab.kt`); `GlassOnGlassDemo.kt`, `GlassGrowthContrastDemo.kt`;
  pagine in un'attività a parte (`PagesDemo.kt`). Lancio diretto con extra `gallery.page`, `gallery.theme`… (`sample/GalleryLaunch.kt`).
- **tools/qa**: `baseline.sh` (registrazioni delle animazioni su emulatore Medium_Phone API 37, coordinate fisse), `gallery-tour.sh` (ogni pagina in ogni
  combinazione della matrice, screenshot e controllo di crash), `qa-common.sh` (screenrecord, fotogrammi, luminanza, dump UI), `web-network-tests.sh`.
  Tutti chiedono un emulatore o un dispositivo. Diagnostica: `FluidGlassDiagnostics` (catture ed export, `bd/FluidGlassDiagnostics.kt`).
- **Cosa manca per collaudare un materiale nuovo senza dispositivo**: un renderer degli shader AGSL fuori dal telefono (è il compito di R3,
  `tools/glassbench/`); immagini di riferimento; uno specchio Kotlin dello shader di Kyant (esiste solo per l'SDF di Fluid-physics,
  `fp/FluidPhysicsGeometry.kt` + `PolySdfMathTest`); un test che conti layer e passate di una superficie; una misura dei tempi di fotogramma.

---

## 10. API pubblica

Catalogo in `ref/api/vetro.md` (153 dichiarazioni), `controlli.md`, `barre.md`, `menu-modali.md`, `fluid-physics.md`, `schermata.md`.

**Possono restare identiche con un materiale nuovo sotto**: `rememberGlassBackdrop`, `rememberCombinedGlassBackdrop`, `glassBackdropSource`,
`glassContainer`, `glassMaterialSource`, `LocalGlassBackdrop`, `currentGlassBackdrop`, `rememberEmptyGlassBackdrop`, `LocalFluidCanvasBackdrop`,
`LocalFluidCanvasIsGlass`, `FluidGlassQuality` e simili, `FluidGlassDiagnostics`; la firma di `glassSurface` (stato, tinta, forma, ruolo, intensità,
`opticalDepth`, `pressed`, `exports`, `layerBlock`, `sampleOnce`, `resampleIntervalMillis`, `frozen`, `heldWhileMoving`, `nestInContainer`);
`glassControlSurface`, `FluidGlassIconButton`, `FluidGlassButton`, `FluidGlassSize`, `FluidBarAction`, `FluidFloatingCapsule`, `FluidCapsuleAction`,
`FluidSearchField`, `FluidFloatingTabBar`, `FluidMorphMenu*`, `FluidGlassModal*`, `FluidDrawer`, `FluidNotification*`, `fluidPhysicsSurface`,
`FluidPhysicsState`, `FluidForm`. `GlassRole` resta (aggiungere valori rompe i `when` esaustivi delle app).

**Legate al materiale di oggi**:
- `GlassOptics` (data class, 17 campi nel vocabolario di Kyant: `refractionHeight/Amount`, `depthEffect`, `dispersion`, `highlightWidth/Alpha/Angle`,
  `innerShadow*`, `shadowRadius/Alpha`, `pressedDepthBoost`, `edgeFloor`, `backdropResolution`, `sizeResponse`): nessun campo per luce del corpo, seconda
  sorgente, ombre a più strati, adattività. Costruita a mano in `FluidWearGlass.kt:37`, `copy` nel Playground. Campi nuovi con default tengono la
  compatibilità per argomenti nominati; cambiare il significato di quelli esistenti cambia l'aspetto in silenzio.
- `GlassTint` (`overlay`, `fallback`, `hairline`): colori fissi; una tinta adattiva chiede altro. Costruita a mano in engine-wear (3 volte).
- `GlassDefaults` (`optics`, `buttonOptics`, `panelOptics`, `stackedContentOptics`, 16 tinte compresi i `…OnPhoto`, `isDarkSurface`): le firme possono
  restare, i valori no.
- Legate alla fascia: `GlassFalloff.FadeDown`, `GlassEdge`, `FluidScreenDefaults.GlassFadeTail/ShieldDeadZone/ShieldRampDistance`, i parametri `barTint`.
- Il pacchetto `glass.backdrop` è **tutto pubblico** (`drawBackdrop`, `drawPlainBackdrop`, `BackdropEffectScope`, `blur`/`lens`/`colorControls`/`vibrancy`/
  `effect`/`runtimeShaderEffect`, `Highlight`, `HighlightStyle`, `Shadow`, `InnerShadow`, `Backdrop`, `LayerBackdrop`, `layerBackdrop`,
  `rememberCombinedBackdrop`, `rememberCanvasBackdrop`, `GlassDragAnimation`, `GlassTouchHighlight`, `inspectDragGestures`): la galleria non chiama
  `drawBackdrop`; le app fuori dal repo non sono verificabili da qui.
- Le barre dichiarate chiedono un parametro o un DSL nuovo accanto a `actions: RowScope.() -> Unit`.

---

## 11. Costi noti

**Dove si paga**: ogni superficie è una registrazione più una catena di `RenderEffect` pagata per pixel, la sfocatura è la parte più cara
(`ref/componenti/vetro.md:559-562`); ogni sorgente di pagina è una traversata del sottoalbero a ogni registrazione (`bd/backdrops/LayerBackdropModifier.kt`).

**Misure scritte**: tablet, pagina Voti 85 → 27 ms per fotogramma togliendo il layer offscreen del contenuto (1.5.3) e 18 → 85 ms con il layer del pannello
`Offscreen` (`bd/DrawBackdropModifier.kt:545-556`); bacheca del tablet, ~300 ms di render thread per otto rigiochi del corpo senza la sorgente composta
offscreen (`GlassMaterial.kt:197-204`); Galaxy S25, 44 ms per fotogramma con una cattura ciascuno, 61 ms con nove vive (`:776-782`); barra che si piega,
9 fotogrammi da 250–450 ms contro 77 da 42 ms con la presa (`:186-190`); Perfetto sull'S25 (MyCloud Gallery, 2026-10-13): RenderThread 7–10 ms,
47 passate GPU fra fondali a schermo intero e sfocature di barra, segmenti e pillola, picchi 20–36 ms da `vkAllocateMemory` (`ref/componenti/vetro.md:600-605`).

**Difese esistenti**: ricattura solo quando serve (offset relativi, padding); `sampleOnce`, `resampleIntervalMillis`, `frozen`/`heldWhileMoving`
(110 ms); qualità guidata dalla velocità; scala di cattura guidata dalla sfocatura e quantizzata; `backdropResolution`; tetti di texture; layer ausiliari
registrati una volta e ridotti oltre 1024 px; export su richiesta (2.13.0); niente `Offscreen` sul pannello; clip hardware; `ShapeProvider` stabile;
Fluid-physics con un passaggio per pixel, uniform soli per fotogramma, ombra stirata, bordo registrato una volta a riposo; `fluidGlassGroups` in pannelli
da otto righe; tab bar congelata durante le transizioni (`LocalFluidTabBarBackdropFrozen`, `FluidFloatingTabBar.kt:439`).

**Costi senza difesa, dal codice**: sorgenti a schermo intero registrate sempre anche senza lettori (`ref/componenti/vetro.md:600-605`, candidato
2.14 mai fatto); il `saveLayer` della fascia FadeDown a ogni fotogramma; ricattura per fotogramma durante le animazioni di taglia (pannello della tab bar
nella piega, campo di ricerca, capsula che cambia, drawer in corsa); un layer d'ombra interna allocato per ogni `glassSurface` e mai disegnato.

---

## Punti di innesto

**(a) Materiale nuovo, più tridimensionale, su Android 13+.**
Dove: le tre lambda di `glassSurface` (`GlassMaterial.kt:1391-1531`) e i nodi vendored che disegnano anello e ombre (`bd/highlight/HighlightModifier.kt`,
`bd/shadow/*`); per le superfici fisiche `physicsLensUniforms`, `PhysicsRimRenderer`, `PhysicsShadowLayers` e gli shader di `fp/FluidPhysicsShaders.kt`;
i valori in `GlassDefaults`; il cancello è `isRuntimeShaderSupported()` (`bd/Platform.kt`). Si porta dietro: il patto del padding, i tetti, la ricetta del
bordo per lato, `edgeFloor`, la qualità che scala la forma e non la pellicola, la regola della taglia, il contrasto di sistema sulle tinte, l'export senza
contenuto, i test che fissano l'uguaglianza a riposo fra le due strade (`FluidPhysicsMaterialTest`, `RestStateDisciplineTest`, `GlassRimRecipeTest`),
le ricette «metro» della goccia, il codice GPL di LibrePods.

**(b) Un solo motore e un contenitore in cui i tasti si fondono e si dividono.**
Dove: `DrawBackdropNode` è già la base comune; `fluidPhysicsSurface` riproduce già `glassSurface` a riposo (`fp/FluidPhysicsSurface.kt:171-245`); l'aggancio
`GlassClipGeometry` sta nello `ShapeProvider`; `FluidForm.Group` porta 6 lastre fuse; il meccanismo della barra ancorata (`FluidBarPiece`/`FluidBarPieceSink`,
`FluidBarJourney`, `barPieceJourney`) sa già raccogliere le lenti dei tasti, spegnerne il vetro e accoppiarle. Si porta dietro: 6 pezzi solo `Slab`, pezzi
solo capsula, il costo della maschera su tutto il nodo, la crescita del tasto per `layerBlock` contro il divieto di scalare, il contratto del contenuto
fratello (`glassContainer`), la sonda dei driver, le discipline del motore.

**(c) Barre dichiarate in alto e in basso.**
Dove: lo slot `actions` di `FluidScreen` (`:631`), `FluidTopBar`/`FluidTopBarActions` (`:1247`, `:1454`), `FluidPageBarSpec` (`ui/page/FluidPageBar.kt:30`),
`BarActions` della barra ancorata (`FluidPageAnchoredBar.kt:318`), gli slot della capsula, il DSL e la `searchMode` della tab bar, l'espansione di
`FluidSearchField`. Si porta dietro: il registro della barra ancorata e la regola «una per host», la misura del titolo (`FluidTopBarControls`), il tasto
indietro unico, la tab bar vendored (3399 righe, formattazione tenuta per i diff con upstream), i numeri di posa della capsula, il bersaglio ≥ 48 dp,
il relè del dito dei menu.

**(d) Vetro, ombre e icone che si adattano allo sfondo.**
Dove: un solo punto di decisione, `GlassDefaults.isDarkSurface()`, e i suoi consumatori (`glassFilm`, `glassRimRecipe`, `glassShadowColor`, flag del bagliore,
`GlassDropFaces`), più i `LocalContentColor` forniti da barre, capsula e host. Un campionatore non esiste: andrebbe costruito sulla cattura che ogni pannello
ha già (`DrawBackdropNode.graphicsLayer`). Si porta dietro: la semantica di `LocalFluidSurfaceSide`, le tinte `…OnPhoto` scelte a mano, il contrasto di
sistema, le tinte di scrim volutamente non derivate dalla palette (`GlassMaterial.kt:1035-1039`).

**(e) Fascia in alto sostituita dalla sfocatura al bordo della pagina.**
Dove: `FluidTopBarGlass` (`FluidScreen.kt:1361`) è l'unico sito; intorno `calculateGlassIntensity`, `GlassFadeTail`, l'export `barGlass` per le azioni, la
pesatura fra due pagine e lo sfondo di transizione della barra ancorata, `FluidCollapsingTopBar`/`FluidPageChrome`. Un effetto di bordo starebbe sulla
sorgente (`glassBackdropSource`, `GlassMaterial.kt:177`) o sul corpo della pagina. Si porta dietro: un materiale per i tasti della barra, la leggibilità del
titolo agganciato, i test `FluidChromeMotionTest`/`FluidScrollPhysicsTest`, l'API `barTint`/`FadeDown`; in basso oggi non esiste nulla di equivalente.

**(f) Menu che escono dal tasto e trasformano la barra; contestuale con pagina sfocata e scurita.**
Dove: `FluidMenuLayer` (`:187`), `FluidMenuJourney` (`:582`), `FluidBarActionImpl` (`FluidScreen.kt:1787`), `FluidGlassModalScrim` (`FluidGlassModal.kt:996`:
1,5 × 2,5 dp, nero 12/20%), `FluidLiftedPreview` (`:1666`, scala 1,03), `FluidGlassMenuPanel` (voci in un fratello, non rifratte). Fra il menu e la barra non c'è
alcun legame: il sink dei pezzi della barra ancorata è l'unico canale che sa dove stanno i tasti vicini. Si porta dietro: la terza trappola (export a forza
piena), il back predittivo, la scelta col dito che ha aperto (`FluidMenuHandOff`), l'ancora nascosta nel disegno, l'host alla radice.

**(g) Tocco: flessione e luce.**
Dove: `fluidPressTransform` (`FluidPress.kt:516`), il `layerBlock` di `glassControlSurface` (`FluidGlassControls.kt:274-292`), `GlassTouchHighlight`
(`GlassInteraction.kt:432`), il `pressState` delle superfici fisiche (`FluidPhysicsSurface.kt:318`), la goccia elastica (`fp/FluidPhysicsLiquid.kt:92`) come
base di un contorno che cede. Per la luce ai vicini non c'è un registro dei vicini, a parte i pezzi della barra ancorata. Si porta dietro: i numeri GPL di
LibrePods e i test che li fissano (`FluidPressTest`, `GlassDropSquashTest`), il divieto di scalare fuori dal `layerBlock`, la coreografia aptica dei tasti.

---

## Cosa non ho potuto stabilire

- L'aspetto a schermo di qualunque cosa (per esempio se il filo superiore della barra in alto, sotto la barra di stato, si vede): niente dispositivo.
- Se il clip del layer di posa ritagli anche la cattura disegnata dallo stesso nodo: mi sono affidato ai commenti di `DrawBackdropModifier.kt:537-544`.
- Quante passate GPU costa davvero una sfocatura (dipende da Skia/HWUI) e se l'uscita di un `RenderEffect` resti in cache a nodo invariato: il codice
  lo suggerisce (`:629-637`), non lo dimostra.
- Il costo del `saveLayer` della fascia FadeDown: nessuna misura nei documenti.
- Se app fuori da questo repo chiamino `drawBackdrop`, costruiscano `GlassOptics`/`GlassTint` a mano o usino `GlassFalloff.FadeDown`.
- Lo stato delle celle della matrice di `docs/10-coerenza.md` (non letta per intero).
