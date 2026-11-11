# «iOS in Android»: il piano

Fonti: [riferimenti.md](riferimenti.md) (studio iniziale e indice dei frame),
[ricerca/inventario-ios.md](ricerca/inventario-ios.md) (R1), [ricerca/audit-engine.md](ricerca/audit-engine.md) (R2),
[ricerca/fattibilita-android.md](ricerca/fattibilita-android.md) (R3),
[ricerca/riferimenti-misure.md](ricerca/riferimenti-misure.md) (R4),
[ricerca/ios27-differenze.md](ricerca/ios27-differenze.md) (R5: differenze di iOS 27 e misure sui video di WWDC26),
[ricerca/icone-e-misure.md](ricerca/icone-e-misure.md) (R6: peso delle icone e taglie dei tasti, Apple contro l'engine).
Copertura voce per voce:
[copertura-inventario.md](copertura-inventario.md). Stato dell'incarico e configurazione del workflow:
`artifacts/liquid-glass/stato.md`.

**Come si legge.** Percorsi abbreviati come nell'audit: `ui/` = `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`;
`bd/` = `ui/glass/backdrop/`; `fp/` = `ui/fluidphysics/`; `gf/` = `ui/glass/field/` (**nuovo**); `test/` =
`engine-ui/src/test/kotlin/dev/antigravity/fluidengine/ui/`; `sample/` = `sample/src/main/kotlin/dev/antigravity/fluidengine/sample/`;
`ref/` = `skill/fluid-engine/references/`; `rif/` = `artifacts/liquid-glass/riferimenti/`. Le misure di R4 sono citate con la
scheda e il grado di fiducia fra parentesi, per esempio (R4 A1, alta); quelle di R5 allo stesso modo (R5 M1, alta), e
`rif/ios27/` è la cartella dei suoi ritagli e collage. Dove né R4 né R5 misurano, il piano scrive «**a occhio**»: taratura con
confronti in chat, e il bersaglio diventa la scelta registrata di Alessio (sezione 5). Le misure di R6 sono citate come
(R6, fiducia); `rif/icone/` è la cartella dei suoi ritagli. Nei pacchetti: **G** = serve un posto Gradle; **D** = serve il dispositivo;
modelli: **Fable** = Fable 5.2 `high`, **Opus** = Opus 5.5 `xhigh`, **Sol** = GPT-6.1 Sol `xhigh`, **Haiku** = Haiku 5.5 `max`.

## 1. Obiettivo

Portare il Fluid Engine il più vicino possibile al Liquid Glass di Apple: Alessio lo ha detto così, «deve
arrivare a ricreare iOS in Android». Si misura su cinque cose, nell'ordine in cui le ha dette:

1. **tridimensionalità**: un elemento di vetro deve sembrare un oggetto, da solo e rispetto alla pagina, anche
   su uno sfondo piatto;
2. **trasformazioni**: qualsiasi elemento può diventare un altro (un tasto due, una barra, una ricerca, un
   menu) e gli elementi vicini interagiscono fra loro **da soli**;
3. **libreria dichiarativa**: chi scrive un'app dice dove sta una cosa, che ruolo ha e da cosa nasce; vetro,
   gruppi e animazioni li decide l'engine, «come scrivere per Apple»;
4. **adattività**: vetro, ombre e icone si adattano a ciò che hanno dietro;
5. **le animazioni comuni di iOS**, tutte: niente lasciato indietro.

## 2. Decisioni già prese (non si ridiscutono nel piano)

- Prima il **massimo della resa**, poi l'**ottimizzazione** in una fase dedicata. Senza sprechi gratuiti prima.
- Il materiale nuovo vale da **Android 13** (API 33, AGSL). Sotto resta il vetro di oggi, intatto, **fascia in
  alto compresa** (il bordo dello scorrimento nuovo chiede AGSL).
- **`engine-wear` resta sul vetro di oggi** (modello `Classic`) in questa tornata: deve solo continuare a
  compilare e a comportarsi come prima. Portare il materiale nuovo sull'orologio è un incarico a parte.
- **Versione di arrivo: 4.0.0** (cambia l'aspetto di ogni app che aggiorna).
- Le **immagini di riferimento del banco** si tracciano nel repo (una ventina di PNG, qualche MB).
- Il **menu di modifica del testo** resta quello della piattaforma: incarico a parte.
- La **fascia di vetro in alto** sparisce: al suo posto il bordo dello scorrimento (sfocatura leggera che
  cresce verso il bordo), in alto e in basso.
- **Tinta** sui tasti: possibile, non di serie, solo per l'azione principale.
- **Goccia e rimbalzo**: come Apple (un solo superamento piccolo, nessuna seconda oscillazione).
- **Vetro sul contenuto** (card, gruppi lista): resta disponibile, non è più di serie né consigliato; le app
  esistenti non cambiano aspetto da sole.
- **Accessibilità**: non è una priorità adesso (non va però rotta quella che c'è). **Qualità grafica**: priorità
  massima. **Fedeltà ad Apple**: alta sulle cose che scegliamo di rendere uguali (la maggior parte).
- **Nomi**: prefisso `Fluid` e convenzioni dell'engine; la *forma* delle API ricalca SwiftUI, con una tabella
  di corrispondenza. Comportamenti di serie = i default di Apple.
- **Codice di terzi**: si può portare codice Apache-2.0 (Haze, Kyant) con attribuzione in `LICENSES/`, come
  già fatto; l'engine è GPL-3.0.
- **Niente beta pubblicate.** Alessio giudica video e collage in chat; le scelte di gusto gli si portano come
  confronti fra cui scegliere.
- **Dispositivi**: poche sessioni concentrate (tablet ora, telefono più avanti); in mezzo, render sul PC.
- **Menu contestuale da pressione lunga** (Alessio, 2026-11-10): la pagina dietro si **scurisce con una sfocatura
  leggera**, «anche se non è esattamente pari ad Apple» (iOS 26 scurisce soltanto: R1 X2). Resta di gusto solo *quanto*
  (F5A).
- **Versione di riferimento: iOS 27 dove differisce da iOS 26** (Alessio, 2026-11-10). La base misurata resta R4 (frame di
  iOS 26); dove iOS 27 fa diversamente il bersaglio è iOS 27, con i valori di R5 (`ricerca/ios27-differenze.md`, arrivata
  il 2026-11-10 e integrata in questo piano). Le due voci qui sotto dicono dove vale e dove no.
- **Materiale: quello di iOS 27** (Alessio, 2026-11-10): più smerigliato, corpo che prende il colore dello sfondo, filo scuro
  su tutto il perimetro sul chiaro con riflesso interno in alto e in basso, ombra corta. I bersagli ottici a riposo sono
  quelli di R5; R4 resta per ciò che R5 dichiara invariato e per il movimento. Dove R5 non misura (tema scuro, ombra sopra
  il testo, raggio di sfocatura, menu e pannelli) è taratura a occhio con confronti in chat.
- **Bordo dello scorrimento: l'aspetto di iOS 26** (Alessio, 2026-11-10): il contenuto si dissolve sotto i tasti, nessuna
  fascia. È un'**eccezione esplicita** a «iOS 27 dove differisce»: la fascia uniforme con taglio netto di iOS 27 (R5 E1)
  **non si fa**. `Hard` resta quello di iOS 26.
- **Icone delle azioni nelle barre: monocrome** (Alessio, 2026-11-10).
- **Icone: un set curato dell'engine, al tratto, peso 500** (Alessio, 2026-11-10, su R6: «solo B, più grassetto»).
  `FluidIcons` sono Material Symbols (Apache-2.0) portate come `ImageVector`, tratto di circa 2,5 dp a 26 dp, ed è ciò
  che si raccomanda alle app al posto delle icone di oggi. **Non** si fa l'ingrossamento automatico degli `ImageVector`
  delle app. Il set copre le icone dei componenti dell'engine e quelle che le app usano davvero, e nasce da un generatore
  ripetibile. La migrazione delle icone app per app resta alle app; la scheda dice come si fa.
- **Taglie** (decise dall'orchestratore il 2026-11-10 sulle misure di R6): icona nel tasto da 22 a **26 dp**
  (freccia indietro **24**), monocroma; vetro del tasto tondo **44 dp** e bersaglio di tocco **48 dp** invariati; margine
  del vetro dal bordo dello schermo da 8 a **16 dp**; distanza fra due vetri **8 dp**, **12 dp** fra gruppi; tab bar alta
  **62 dp** con icone da **28 dp a inchiostro pieno** anche se non scelte ed etichetta da **11 sp**; nei menu icona da
  **22 dp**, riga invariata. Le misure di R6 dicono perché: il tratto delle icone di oggi è già quello di Apple (1,6–1,9 dp
  contro 1,6–2,1 pt) e i tasti sono già 44; sembrano sottili e piccoli perché le icone sono più piccole del 20–30%, nel
  colore d'accento (contrasto 3,5–6,7:1 contro 15–17:1), e i tasti stanno a 8 dp dal bordo.
- **Luce che segue il movimento del telefono: sì, spenta di serie** (Alessio, 2026-11-10): la accende l'app o l'utente
  dalle impostazioni dell'aspetto (F6.5).
- **Il difetto `half4 flat` è un fatto** (confermato sul Tab S9, API 36, il 2026-11-10; corretto nel worktree: la variabile
  ora è `flatSample`). Dalla 3.0.0 alla 3.1.2 tutto Fluid-physics ha girato al livello `Balanced` su ogni dispositivo: goccia
  e trasformazioni giudicate finora erano senza lente e senza ponti. Il livello `Full` non è mai stato visto su un
  dispositivo: può avere difetti suoi e un costo mai misurato, e ogni giudizio su «cosa resta di Fluid-physics» si dà su
  quello.

## 3. Architettura di arrivo

### 3.1 In una pagina

**Un campo, tre strati, una porta**, come nello scheletro, con queste precisazioni (i motivi nei paragrafi indicati):

- **Un materiale, due modelli.** `FluidGlassModel.Liquid` è il materiale nuovo (API 33+ e shader sani);
  `FluidGlassModel.Classic` è il vetro di oggi, che resta nel codice **così com'è** perché è il ripiego sotto API 33, il
  ripiego per un driver che rifiuta, e ciò che `engine-wear` continua a usare (3.11).
- **Il campo** è una funzione `campo(p) → (distanza, gradiente)` con due nuclei: **a lastre** (1, 2, 4, 8 pezzi fusi con
  smooth minimum: quattro sorgenti dalla stessa stringa) e **ad anello** (96 vertici: la goccia in viaggio e le sagome
  libere di Fluid-physics). Tutto il resto dello shader è identico fra i nuclei (3.4, 3.7, 3.8). Il nucleo a lastre
  conosce il **raccordo continuo** degli angoli dell'engine: a riposo la sagoma `Liquid` segue la curva di
  `ContinuousCornerShape`, non un arco di cerchio (3.7).
- **Corpo** = catena di `RenderEffect` sulla cattura: saturazione → sfocatura → mescola nitido/sfocato a monte → **uno
  shader** (campo, lente, dispersione, velo, tinta, ombreggiatura del corpo, maschera). Si riesegue quando cambiano
  cattura, sagoma o un uniform del corpo (3.5, 3.6).
- **Luce** = shader **senza ingresso di sfondo**, disegnato dal nodo sopra il corpo e sotto il contenuto: filo scuro sul
  perimetro, riflessi chiari interni (due lobi, di serie in alto e in basso: è il bordo di iOS 27), bagliore del dito. A
  riposo è l'unico strato che si anima. **In viaggio la
  luce si calcola dentro lo shader del corpo** (il campo si valuta una volta sola; in viaggio il corpo si riesegue
  comunque a ogni fotogramma) (3.5).
- **Ombra** = fuori dalla sagoma, due componenti (contatto e ambiente): a riposo due maschere in cache, registrate come
  sola alfa, con alfa e colore applicati dal layer in disegno; in viaggio nello shader (3.5).
- **La porta** resta `DrawBackdropNode` (`bd/DrawBackdropModifier.kt:507`): cattura, vetro su vetro, export, qualità,
  tetti. Le ricette `Liquid` sono **una** (`glassRecipe`), chiamata da `glassSurface`, `glassControlSurface`,
  `fluidPhysicsSurface`, goccia, puck, piano flottante.
- **Una cattura per campo, un palco per trasformazione**: a riposo ogni grappolo di pezzi fusi ha la sua cattura; durante
  una trasformazione i pezzi coinvolti passano a un palco comune (3.7).
- **Fluid-physics resta come motore del movimento** (forme, transiti, guida esterna, goccia a molle, viaggi di serie) e
  perde tutto ciò che disegna in proprio (shader, bordo in cinque modi, ombra in tre) (3.8).
- **Sopra il materiale**: sonda dello sfondo con isteresi (3.9), striscia del bordo dello scorrimento (3.10), piano
  flottante, barre dichiarate, presentazioni dalla sorgente, tocco, geometria (sezione 6).

### 3.2 I tipi nuovi e dove vivono

Interni, in `gf/` (pacchetto nuovo `dev.antigravity.fluidengine.ui.glass.field`, formattazione a 2 spazi dell'engine, non
quella a 4 del renderer vendored):

| file | contenuto | nasce in |
|---|---|---|
| `gf/GlassFieldKernel.kt` | blocchi AGSL condivisi: preludio SDF (uno solo: oggi è duplicato fra `bd/internal/Shaders.kt` e `fp/FluidPhysicsShaders.kt:47`), nucleo a lastre con `MAX_PIECES` sostituito e raccordo continuo per pezzo, nucleo ad anello, `circleMap`, deformazione del punto interrogato; le **firme** dei blocchi di luce e d'ombra che il corpo include | F1A.1, anello in F1B.1 |
| `gf/GlassFieldGeometry.kt` | specchi Kotlin puri del campo (`sdRoundedRect`, `smin`, gradiente, deformazione): la verità dei test e del banco del movimento. Assorbe gli specchi di `fp/FluidPhysicsGeometry.kt` | F1A.1 |
| `gf/GlassBodyShader.kt` | `GlassBody1/2/4/8ShaderString`, `GlassBodyRingShaderString` | F1A.2, F1B.1 |
| `gf/GlassLightShader.kt` | `GlassLightBlock` (condiviso col corpo) e `GlassLight1/2/4/8ShaderString` | F1A.1 (blocco vuoto, perché il corpo compili), F1A.3 |
| `gf/GlassShadow.kt` | nodo dell'ombra a due maschere in cache (sola alfa; colore e alfa dal layer); blocco AGSL dell'ombra in viaggio | F1A.1 (blocco vuoto), F1A.4 |
| `gf/GlassRecipe.kt`, `gf/GlassLiquidProfile.kt` | `glassRecipe(...)`: da `GlassOptics` + `GlassTint` + ruolo + taglia + lato + stato adattivo agli uniform di corpo, luce e ombra. **Pura**, senza allocazioni per fotogramma. Il profilo tiene i numeri che `GlassOptics` non sa esprimere (filo scuro, riflessi, velo, due ombre). I profili delle ricette particolari stanno in file propri: `gf/GlassDropProfile.kt`, `gf/GlassPuckProfile.kt`, `gf/GlassPaintedProfile.kt` (F1B.3–5) | F1A.5 |
| `gf/GlassFieldEffects.kt` | `BackdropEffectScope.glassBody(plan, recipe)`: costruisce la catena e imposta gli uniform. Prima passa da `glassDrawInputs(...)`, **pura**: taglia, fattore di scope, raggi e ricetta così come arrivano allo shader (è il punto che il test di parità osserva: 4.4) | F1A.6 |
| `gf/GlassFieldPlan.kt` | `GlassFieldPlan`: pezzi (centro, mezze misure, 4 raggi, raccordo continuo, raggio di fusione), o anello; riquadri; deformazione. Contiene anche la regola **forma → piano** (3.7). È `PhysicsRenderPlan` (`fp/FluidPhysicsState.kt:888`) generalizzato e portato a 8 pezzi | F1A.6, F1B.1 |
| `gf/GlassFieldHealth.kt` | sonda per sorgente, verdetto per variante, riscaldamento | F0.1, F1A.7 |
| `gf/GlassProbe.kt`, `gf/GlassProbeScheduler.kt`, `gf/GlassAdaptiveFilter.kt` | miniatura dello sfondo e statistiche; quando leggere (puro: eventi di cattura e di revisione della sorgente, 3.9); media mobile e isteresi (pura) | F2.1 |
| `gf/GlassScrollEdgeShader.kt` | `GlassEdgeBlurShaderString` (nucleo separabile a raggio per pixel, portato da Haze) | F2.4 |
| `gf/GlassPlane.kt` | logica pura del piano flottante: registro dei pezzi, grappoli, accoppiamento per identità, classificazione delle transizioni, palco | F3.1 |
| `gf/GlassContentLensShader.kt` | deformazione e sfocatura del **contenuto** in viaggio (voci del menu «dentro la lente») | F5A.2 |
| `gf/trace/GlassTrace.kt` | modello e scrittore delle tracce del banco del movimento | F0.5 |

Pubblici, in `ui/fluid/` (prefisso `Fluid`, firme in 3.13):

| file | API | nasce in |
|---|---|---|
| `FluidGlassModel.kt` | `enum class FluidGlassModel { Liquid, Classic }`, `LocalFluidGlassModel` | F1A.6 |
| `FluidGlass.kt` | `FluidGlass` (`Regular`, `Clear`, `Identity`, `tint`, `interactive`), `Modifier.fluidGlass` | F2.2 (tipo), F3.2 (modificatore) |
| `FluidGlassAdaptive.kt` | `FluidGlassAppearance`, `LocalFluidGlassPreference`, inchiostro adattivo | F2.2, F2.3 |
| `icons/FluidIcons.kt` e i file generati accanto | `object FluidIcons`: il set curato, al tratto a peso 500 e pieno per la tab bar (generato: 3.14) | F0.8 |
| `FluidIcon.kt`, `FluidMetrics.kt` | `FluidIcon` (taglia e inchiostro dall'ambiente), `LocalFluidIconSize` (la forniscono i vetri dell'engine), `object FluidMetrics` (le taglie di R6; F3.5 lo completa con margini e forme) | F1A.11 |
| `FluidScrollEdge.kt` | `FluidScrollEdgeStyle`, `FluidScrollEdge`, `FluidSafeAreaBar` | F2.4, F2.5 |
| `FluidGlassContainer.kt` | `FluidGlassContainer`, `fluidGlassId`, `fluidGlassTransition`, `fluidGlassUnion`, `rememberFluidGlassNamespace`, `FluidGlassAnimation` | F3.2 |
| `FluidConcentricShape.kt` | `FluidConcentricShape`, `Modifier.fluidContainerShape`, `rememberFluidConcentricRadii` | F3.5 |
| `toolbar/FluidToolbar.kt`, `toolbar/FluidToolbarModel.kt` | `FluidToolbarScope`, `FluidToolbarPlacement`, `FluidButtonRole`, modello interno | F4A.1, F4A.2 |
| `FluidSearch.kt` | `FluidSearchSpec`, `FluidSearchPlacement`, `FluidSearchBehavior`: **tipo e contratto** | F4A.1 |
| `toolbar/FluidToolbarSearch.kt` | `FluidToolbarSearchSlot` (interno): il punto unico in cui schermata, barra in alto e barra in basso posano la ricerca | F4A.2 (posa il campo di oggi), F4B.2 (comportamento) |
| `FluidTransitionSource.kt` | `Modifier.fluidTransitionSource`, `FluidTransitionSource` | F5A.1 |
| `FluidGlassSheet.kt` | `FluidGlassSheet`, `FluidSheetDetent` | F5B.2 |

Tipi di oggi che cambiano: `GlassTint` guadagna **un** campo con default in coda, `mode: GlassTintMode = GlassTintMode.Fixed`
(`Fixed` = la pellicola dipinta com'è, cioè il comportamento di oggi; `Neutral` = il velo lo decide il materiale; `Colored` =
tinta alla Apple). `GlassOptics` non guadagna campi. `FluidForm.Group.MaxPieces` e `PhysicsMaxPieces` salgono insieme da 6 a 8.
`GlassRole` non cambia (aggiungere valori rompe i `when` esaustivi delle app: R2 §10). Aggiunte piccole a tipi pubblici che
esistono, tutte con default o di sola lettura: `drawBackdrop(…, onDrawLight = null)` (3.3); `FluidGlassDiagnostics` espone i
verdetti degli shader, i contatori di superfici per modello e di layer vivi, `compileAllShaders()` (servono ai test che
stanno in un altro modulo: 4.4) e `simulateApiLevel(level)`, che vale solo nelle build di debug e fa disegnare all'engine i
suoi ripieghi di Android 12 e 11 su un dispositivo più nuovo (4.4); `FluidContextAction` guadagna campi e un costruttore compatibile (3.11);
`fluidAppearanceSection` guadagna righe facoltative (F2.2, F6.5). Interni: `LayerBackdrop` porta una **revisione** (3.9);
`ContinuousCornerShape` rende leggibile all'engine il proprio `smoothing`.

I tipi di configurazione nuovi (`FluidGlass`, `FluidSearchSpec`, `FluidSheetDetent`, gli elementi della barra) sono
`@Immutable` con uguaglianza strutturale: un'istanza ricreata a ogni ricomposizione non deve invalidare niente.

### 3.3 La catena di resa: com'è oggi, come diventa

Oggi (R2 §1.1): una `glassSurface` = 4 layer vivi + 1 inutile (ombra interna mai disegnata) + 0–3 condizionali, tre
effetti sulla cattura, sagoma ritagliata dal clip hardware, pellicola e filo disegnati fuori dallo shader.

Modello `Liquid`:

```
glassSurface / glassControlSurface / fluidPhysicsSurface / goccia / puck / piano flottante
│   tutti: glassRecipe(...) + GlassFieldPlan  →  drawBackdrop(...)
├─ GlassShadowNode        PRIMA del nodo, fuori dal suo clip: due maschere in cache (contatto, ambiente), sola alfa;
│                         alfa e colore li mette il layer in disegno (adattivi senza ri-registrare). Assente in viaggio.
├─ graphicsLayer(layerBlock)   solo traslazione (deriva del tasto); mai scala (invariante 5)
└─ DrawBackdropNode
     layout: placeWithLayer(clip)   clip hardware sul rettangolo arrotondato dove la lastra non sporge mai;
                                    spento (`clip = false`) dove la lastra cede o viaggia: lì ritaglia lo shader
     draw:
     1. fitToTexture, updateEffects se cambia uno stato letto dagli effetti          (come oggi)
     2. refreshCapture: cattura = taglia + 2·padding, padding = max(0, sfocatura − altezza) + margine del campo
     3. drawLayer(cattura)  ← RenderEffect:
          ColorFilter(saturazione) → Blur(Clamp) → BlendMode(Plus, w·nitido, (1−w)·sfocato) → RuntimeShader(corpo)
     4. onDrawSurface: solo il filetto di `GlassEdge`, se chiesto                                (come oggi)
     4b. onDrawLight (aggiunta): drawRect con lo shader della LUCE (nessun ingresso; uniform letti in disegno) ← a riposo
     5. drawContent: icone, testo, vetro annidato
     6. onDrawFront: niente
     7. export su richiesta: 3 + 4 + 6, mai il contenuto e mai la luce                     (come oggi)
```

Cosa sparisce dal percorso `Liquid`: `HighlightNode` e il suo layer, `ShadowNode` a una maschera, `InnerShadowNode` (il
layer mai disegnato), la pellicola come `drawRect`, `glassMask` con il `saveLayer` della fascia. Il conto dei layer vivi di
una lastra ferma scende da 4(+1) a **3** (cattura, posa, ombra a due maschere nello stesso nodo); con l'ombra spenta (ruolo
`Content`) a 2. È un vincolo di fase, non un auspicio: lo verifica il test che conta i nodi (F1A.7), perché il dato di Haze e
quello dell'S25 dicono che a saturare è il numero di layer e passate prima dell'area (R3 cap. 9).

Cosa **non** cambia: quando la cattura si rifà (invarianti 1–3 di R2 §8), i tetti di texture, l'annidamento, l'export a forza
piena degli scrim, `sampleOnce`/`frozen`/`heldWhileMoving`, `FluidGlassQuality`.

**Dove sta la luce rispetto al contenuto.** Sotto. Oggi l'anello è sopra il contenuto e il bagliore sotto
(`ui/glass/interaction/GlassInteraction.kt:522`); con un solo strato di luce la scelta è una, e il bagliore sopra le icone le
slaverebbe. Il filo occupa l'ultimo punto percentuale del bordo, dove non c'è contenuto.

**Un solo modo di comporre.** Lo shader della luce restituisce un colore premoltiplicato e si posa in `SrcOver`: può
schiarire (riflessi, bagliore) e scurire (filo scuro) con la stessa passata. Sparisce la coppia «colore scuro
+ `SrcOver` sul chiaro, bianco + `Plus` sullo scuro» di `glassRimRecipe` (`ui/fluid/GlassMaterial.kt:486`) e con lei la
trappola dell'invariante 11. Il bordo di serie è quello di **iOS 27** (decisione, sezione 2): sul chiaro un filo scuro su
tutto il perimetro più un riflesso chiaro interno in alto e in basso (R5 M1, M4); sullo scuro il solo riflesso (R5 M3,
bassa). Curiosità utile: il filetto scuro che l'engine disegna oggi sul tema chiaro (nero al 28%, R2 §1.6) è più vicino a
iOS 27 di quanto lo fosse a iOS 26; ciò che manca è il riflesso, lo spessore assoluto e l'uniformità sul perimetro (oggi è
pieno sopra e sotto e spento sui fianchi). I due archi diagonali di iOS 26 (R4 A1) restano esprimibili con gli stessi
uniform e sono la variante «iOS 26» delle tavole di gusto.

**La luce non entra nel materiale esportato.** Oggi il nodo registra nell'export `onDrawBehind`, la cattura con gli effetti,
`onDrawSurface` e `onDrawFront` (`bd/DrawBackdropModifier.kt:922-927`); l'anello è un nodo a parte e il bagliore sta nel
contenuto, quindi nessuno dei due è esportato. Se la luce andasse in `onDrawSurface` il vetro annidato campionerebbe anche
filo e bagliore del pannello che lo ospita, e ogni fotogramma di bagliore renderebbe vecchio l'export, cioè rifarebbe la
catena di tutti i figli. Per questo `drawBackdrop` guadagna un parametro con default, `onDrawLight`, disegnato dopo
`onDrawSurface` e **fuori** dalla registrazione dell'export: il vetro su vetro resta quello della 2.13.0 (invariante 8). È
l'unica modifica certa al nodo vendored in F1A.6, marcata «Fluid Engine addition» come le altre.

**Dove vanno `intensity`, `opticalDepth`, la qualità, `edge` e `falloff` nel ramo `Liquid`.**

| ingresso di `glassSurface` | oggi (R2 §1.3) | `Liquid` |
|---|---|---|
| `intensity` (0 = assente, 1 = pieno) | scala saturazione, **raggio di sfocatura**, lente, pellicola, filo, ombra | scala saturazione, la mescola `w` (da nitido a smerigliato), lente, velo, tinta, forza della luce, alfa dell'ombra. Il raggio **non** cambia: il padding resta fermo (è l'uniform `materialize`, più `w` nella catena) |
| `opticalDepth` | lente, filo, ombre | lente, luce, ombra; non velo né smerigliatura |
| `FluidGlassQuality` | lente, alfa di filo e ombra, sfocatura fra 0,45 e 1, frangia spenta sotto 0,75, cattura a metà sotto 0,7; mai la pellicola | identico: lente, luce, ombra, raggio fra 0,45 e 1, dispersione, risoluzione; mai il velo |
| `pressed` | approfondisce lo spostamento | identico (`refraction`) |
| `edge` e `tint.hairline` | filetto di mezzo dp in alto o in basso (`drawGlassTint`, `ui/fluid/GlassMaterial.kt:1800-1840`) | identico, in `onDrawSurface` |
| `falloff = FadeDown` | la fascia | **resta `Classic`** qualunque sia il modello, da F1A fino a F2.5; da F2.5 diventa la striscia del bordo (3.10) |
| `glassMask` (il `saveLayer` delle dissolvenze) | attorno al renderer | non serve: la dissolvenza è `materialize` |

### 3.4 Gli shader

Tutte le sorgenti sono `const val` Kotlin, composte con i template `$Nome` che il banco sa già risolvere
(`tools/glassbench/kotlin_sources.py`): **la sorgente è una sola**, il banco la legge con `kotlin:Nome`. Discipline ereditate
da `fp/FluidPhysicsShaders.kt:1-22`: sorgenti costanti, un passaggio del campo per pixel, cicli a limite costante con uscita
su uniform, indici costanti, distanze in `float` e colori in `half`, niente derivate.

| programma | ingressi | varianti | a cosa serve |
|---|---|---|---|
| `GlassBody{1,2,4,8}ShaderString` | `uniform shader content` (la mescola di 3.6) | 4, da `MAX_PIECES` | corpo a lastre: fermo, gruppi, viaggi fra lastre |
| `GlassBodyRingShaderString` | `content` | 1 | corpo ad anello: goccia in viaggio, sagome libere |
| `GlassLight{1,2,4,8}ShaderString` | nessuno | 4 | luce a riposo delle lastre |
| `GlassEdgeBlurShaderString` | `content` | 1 (direzione come uniform) | bordo dello scorrimento, due passate |
| `GlassContentLens{Slab,Ring}ShaderString` | `content` (il layer del contenuto) | 2 | contenuto deformato dalla lente in viaggio (F5A) |

**Dopo S1 (cancello C3): le varianti a lastre sono due, `1` e `8`**, perché la variante a 8 con uscita su `pieceCount`
costa come quella tagliata sul conteggio. I programmi scendono da dodici a otto: `GlassBody1`, `GlassBody8`,
`GlassBodyRing`, `GlassLight1`, `GlassLight8`, `GlassEdgeBlur`, `GlassContentLensSlab`, `GlassContentLensRing`. Dove il
piano scrive `Body2` o `Body4` si legge `Body8`. Si scaldano dopo il primo fotogramma, non all'avvio (cancello C6).

Dodici programmi nel disegno di partenza. Una **sagoma libera ferma** (`FluidForm.Poly` a riposo: `restFormNow()` la restituisce,
`fp/FluidPhysicsState.kt:425-432`) non ha una variante di luce: tiene `lightInBody = 1` anche a riposo, cioè il suo bagliore
ripaga la catena. È il Playground e poco altro; un tredicesimo programma (`GlassLightRing`) si aggiunge solo se in F1B una
sagoma libera ferma e toccata risulta fuori dal fotogramma. La dispersione non è più una sorgente a parte: è un uniform (tre campioni invece di sette, R3 cap. 9), con
un ramo su uniform. Ogni programma nuovo è uno scatto alla prima compilazione: si scaldano all'avvio (F1A.7), e la misura (g)
della sessione S1 dice quanto pesa.

**Uniform del corpo** (oltre alla geometria del nucleo, che è quella di oggi più il raccordo: `offset`, `pieceCount`,
`pieceRect[N]`, `pieceRadii[N]`, `pieceBlend[N]`, `pieceSmooth[N]` per le lastre; `vertCount`, `verts[96]`, `bbox`, `normalSmooth`, `soften`, `restCenter`,
`restHalfSize`, `restRadii`, `dropReach` per l'anello, come in `fp/FluidPhysicsEffects.kt:120-160`, `:232-299`):

| uniform | significato | viene da |
|---|---|---|
| `flex` (`float4`), `flexCenter` | matrice inversa 2×2 della flessione attorno a un centro: `p' = c + S⁻¹(p − c)`; identità a riposo | F6.1 |
| `thickness` | fin dove agisce il bordo (px) | `refractionHeight` con la regola della taglia |
| `refraction` | spostamento massimo al bordo, negato | `refractionAmount · (1 + pressione · pressedDepthBoost)` |
| `dome` | peso della cupola 0..1 | `depthEffect` |
| `dispersion` | 0..1, tre campioni | `dispersion` |
| `magnify` | ingrandimento della lente in moto (c'è già) | goccia |
| `materialize` | 0 = l'ingresso nitido, 1 = materiale pieno: scala insieme lente, velo, tinta, luce in corpo, ombra in corpo | `intensity` (3.3) e comparsa (F3.3) |
| `veilColor`, `veilAdapt` | velo del corpo e quanto segue la luminanza dell'ingresso | profilo + lato + sonda |
| `tintColor`, `tintMode` | 0 = pellicola fissa (compatibilità), 1 = tinta la cui luminosità segue lo sfondo | `GlassTint.mode` |
| `bodyShade` | gradiente del corpo verso i bordi | profilo |
| `dim` | scurimento sotto la variante `Clear` | `FluidGlass.Clear` |
| `lightInBody` + gli uniform della luce e dell'ombra | 1 solo in viaggio: il blocco `GlassLightBlock` gira dentro il corpo | motore |
| `aa` | antialias della maschera (1,25 px di scope, come oggi) | costante |

**Uniform della luce**: la stessa geometria e la stessa `flex`; `rimWidth` (px, **assoluto**), `rimDark` (colore e forza
del filo scuro sul perimetro: zero sul lato scuro), `reflexDir` (asse dei due lobi di riflesso: di serie verticale, alto e
basso; ruotabile dal sensore e per la variante «iOS 26»), `reflexWidths` (ampiezza dei due lobi), `reflexStrength`,
`reflexDepth` (quanto il riflesso rientra verso l'interno prima di tornare al corpo), `reflexFloor` (quanto riflesso resta
fuori dai lobi), `glowCenter`, `glowRadius`, `glow`, `glowFill` (quanto il bagliore ha riempito l'elemento), `lightColor`,
`materialize`, `aa`; per l'ombra in viaggio `shadowOffset`, `shadowRadii` (contatto, ambiente), `shadowAlphas`,
`shadowColor`.

### 3.5 Corpo, luce, ombra: chi fa cosa e quando si riesegue

| strato | contiene | si riesegue quando | costo a riposo |
|---|---|---|---|
| corpo | lente, dispersione, velo adattivo per pixel, tinta, ombreggiatura, maschera | cattura nuova; taglia o sagoma; un uniform del corpo (pressione che approfondisce la lente, comparsa, flessione) | nullo: HWUI tiene l'uscita finché né contenuto né effetto cambiano |
| luce | filo scuro, riflessi interni, bagliore del dito, luce dal sensore | a piacere: un `drawRect` con uno shader senza letture di texture | il costo dello shader a ogni fotogramma in cui lo schermo si ridisegna (non è un layer in cache): lo misura S1 (b); se pesa, la luce ferma si registra in un layer come fa oggi l'anello (F7, leva 1) |
| ombra | contatto + ambiente | taglia, forma o raggio (maschere); alfa e colore mai | nullo |

Perché la luce è separata **solo a riposo**: R3 cap. 1 dimostra che un uniform che cambia è un `RenderEffect` nuovo, e un
`RenderEffect` nuovo rifà la sfocatura. Bagliore, luce dal sensore e luce che corre sul bordo sono proprio le cose che si
animano mentre la lastra è ferma: stanno fuori dalla catena. In viaggio la geometria cambia a ogni fotogramma, il corpo si
riesegue comunque, e tenere la luce fuori vorrebbe dire valutare il campo due volte (per l'anello: due cicli da 96 lati per
pixel). Lo stesso blocco AGSL (`GlassLightBlock`) entra nelle due sorgenti: al passaggio viaggio→riposo la differenza attesa
è l'arrotondamento a 8 bit di un livello sul filo. Se sul tablet si vede uno scalino (S2), il ripiego è tenere
`lightInBody = 1` anche a riposo sulle sole superfici fisiche, pagando il loro bagliore con la catena.

**Deciso dopo S1 (cancello C1): il corpo resta a un layer**, e la luce a riposo non va in un layer (0,1 ms per tre
elementi). Il capoverso che segue resta come ripiego, da rileggere solo se S2 lo riapre.

**Il rischio che può cambiare questa sezione.** Se la misura (c) di S1 dice che un cambio di soli uniform sul corpo non sta
nel fotogramma sulle lastre che ne hanno bisogno (pressione, flessione, comparsa), il corpo passa a **due layer**: un primo
layer con cattura e sfocatura (stabile, tenuto da HWUI), un secondo che lo disegna con il solo shader. Un layer in più per
lastra, in cambio di gesti che non ripagano la sfocatura. È un cambio dentro `gf/GlassFieldEffects.kt` e
`bd/DrawBackdropModifier.kt`, non della ricetta né degli shader: per questo la decisione si prende in F0 e non blocca il
lavoro sul banco.

**Ombra.** A riposo due maschere sfumate in un nodo solo, come fa oggi `ShadowNode` con una
(`bd/shadow/ShadowModifier.kt:88-160`), estese con il tetto d'area dei layer ausiliari (`HighlightLayerMaxPx`, 1024 px). In
viaggio nello shader (campo valutato nel punto spostato): sostituisce `PhysicsShadowLayers` e `fluidPhysicsShadow`
(`fp/FluidPhysicsShadow.kt:188`, `:67`). «Più forte sul testo» non chiede il secondo ingresso: è la varianza di luminanza
della miniatura della sonda (R3 cap. 6, **non provato**: la prova sta in F2.1).

Due precisazioni sull'ombra. (i) In `ShadowNode` sono proprietà del layer solo alfa e modo di comporre; il **colore** sta
nel `Paint` registrato ed è fra le condizioni che rifanno la maschera (`recordedColor`,
`bd/shadow/ShadowModifier.kt:109, 128-129, 137`). Un colore guidato dalla sonda ogni 100–150 ms rifarebbe e ricaricherebbe
due maschere sfocate su CPU a quella cadenza. Quindi il nodo nuovo registra le maschere in **sola alfa** (bianco) e applica
colore e alfa in disegno con il filtro di colore del layer: nessuno dei due ri-registra. (ii) Riposo e viaggio usano due
algoritmi (maschera sfumata contro campo nello shader), quindi due profili di caduta: con l'ombra corta di iOS 27 (2–4%)
lo scarto è piccolo, ma sotto un menu o sul testo può vedersi. La parità dell'ombra al passaggio viaggio→riposo è una prova
di F1B.6 e una voce di S2, come quella della luce.

### 3.6 Sfondo nitido e sfondo sfocato: via A

Scelta: **via A di R3**, la mescola a monte.
`createBlendModeEffect(dst = filtroColore(1 − w) ∘ sfocatura, src = filtroColore(w), Plus)`, poi lo shader con un ingresso.

Perché, oltre al costo nullo: la mescola `w` è ciò che permette di **animare la smerigliatura senza toccare il raggio di
sfocatura**. Il raggio decide il padding della cattura, e un padding che cambia rifà la cattura a ogni fotogramma (invariante
1). Con `w` la comparsa per lente (da nitido a smerigliato), il materiale «limpido in viaggio e smerigliato negli ultimi
100–150 ms» (R4 B1, media) e la variante `Clear` sono tutti lo stesso raggio con un peso diverso.

Quando si passa a **B** (due nodi runtime sommati con `Plus`, il campo valutato due volte): solo se, nel confronto di F1A fra
banco e ritagli Apple (`rif/ritagli/A_lente_griglia_f1485_f1700.jpg`, `A5_smerigliatura.jpg`), la banda rifratta al bordo dei
piccoli risulta impastata dalla sfocatura e i critici o Alessio la segnano. In quel caso B entra **solo sui piccoli**
(tasti, capsule), come variante della ricetta (`sharpRim`), e il costo si misura in S2. Quando si passa a **C** (atlante):
solo se B serve e in S2 non regge il fotogramma; C non l'ha provata nessuno su HWUI, quindi prima una prova nella pagina
«Misure». Onestà: né R4 né R5 sanno misurare la forza della lente o il raggio di sfocatura (R5 M1: il confronto fra le due
versioni «non è riducibile a una gaussiana»), quindi questa decisione si prende a occhio su tavole di confronto, non su un
numero.

**`w` è una dissolvenza, non una sfocatura intermedia.** Con `w = 0,26` resta esattamente il 26% del contrasto, ma con le
lettere nitide e grigie sopra la loro copia sfocata: passerebbe la misura del contrasto e non sembrerebbe smerigliato (ed è
un'immagine doppia, vietata da A2). Regola: **a riposo la variante `Regular` ha `w = 0`** e la smerigliatura la fa il
raggio; `w > 0` vive solo nei transitori (comparsa, ultimi 100–150 ms di un viaggio) e nella variante `Clear`. Per questo
la smerigliatura si misura su due grandezze, contrasto residuo **e** nitidezza (4.2), e i transitori si guardano con
`first` e `last` per l'immagine doppia (F3.3).

### 3.7 Il campo: pezzi, tetto, grappoli, palco

- **Tetto**: 8 pezzi per campo (la variante più grande), **4 di serie** come soglia oltre la quale il piano preferisce
  dividere. Motivi: Haze fonde al massimo 4 (R3 cap. 2); la barra di Apple che cambia contesto ne ha 5–6 in transito; la
  stima di costo dice 5,3 a 4 pezzi e 8,2 a 8 contro 2,4 a uno (R3, in istruzioni di CPU: il rapporto vero su GPU lo dà la
  misura (d) di S1). Oltre 8 pezzi a distanza di fusione il grappolo si divide nel punto di varco più largo e le due metà
  non si fondono fra loro: si vede come due gruppi vicini, non come un errore.
- **Variante scelta per conteggio** (dopo S1, cancello C3): 1 pezzo → `Body1` (un rettangolo arrotondato senza ciclo),
  2–8 → `Body8` con uscita su `pieceCount`. Nel disegno di partenza erano quattro (2 → `Body2`, 3–4 → `Body4`,
  5–8 → `Body8`). Il cambio di variante a metà gesto è un cambio di programma: i programmi sono scaldati, e il piano sceglie
  la variante **del viaggio intero** alla partenza (il massimo fra partenza e arrivo), mai a metà.
- **Grappolo** = pezzi dello stesso contenitore con stessa variante di vetro, a distanza ≤ `spacing`. **A riposo ogni
  grappolo è un campo con la sua cattura** (un gruppo di una barra è già *un* pezzo: una capsula con dentro N icone, non N
  vetri).
- **Palco** = durante una trasformazione i pezzi coinvolti (partenza e arrivo) spengono il proprio vetro e li disegna **una**
  superficie, grande quanto l'unione dei riquadri di partenza e arrivo più il margine dell'oltrepasso, tenuta ferma per tutto
  il viaggio (`heldWhileMoving`, rinfresco a 110 ms). È il meccanismo che la barra ancorata ha già (`FluidBarPiece`,
  `FluidBarPieceSink`, `FluidBarJourney`, `ui/page/FluidPageBar.kt:127-152`, `ui/page/FluidPageAnchoredBar.kt:409-433`),
  generalizzato.
- Perché non «un palco fisso grande quanto il contenitore»: una barra in alto su tablet è larga 2560 px con due gruppi agli
  estremi; una cattura sola sfocherebbe 2560 px per mostrare due capsule. Il prezzo della scelta è il **passaggio di
  consegne** riposo↔palco (due catture, una all'inizio e una alla fine) che deve essere invisibile: lo è per costruzione solo
  perché riposo e palco usano la stessa ricetta e lo stesso shader (test di parità in F1B.6). Ripiego se in S3 il passaggio
  si vede: palco permanente per le barre (sono sottili), tenendo i grappoli solo per i contenitori liberi.
- **Margine del campo**: le lastre che cedono o viaggiano hanno bisogno di pixel fuori dal proprio riquadro. Il meccanismo
  c'è: la cattura è già `taglia + 2·padding` con `offset = −padding` nello shader (`bd/effects/Lens.kt:51-53`,
  `:72`). Il margine si somma al padding ed è **fisso per la vita della superficie** (non per fotogramma): zero per le lastre
  che non cedono, una frazione del lato corto per le altre (il numero in F6.1).
- **Raccordo continuo.** Le forme dell'engine sono `ContinuousCornerShape` (`ui/fluid/FluidShape.kt:38`) e `FluidForm.Slab`
  porta `smoothing`, «onorato a riposo» (`fp/FluidForm.kt:63-69`). Oggi la curva che si vede all'angolo la disegnano
  pellicola e anello sul path esatto, e il clip hardware ripete la stessa scelta per non mostrare due curve
  (`bd/internal/ShapeProvider.kt:129-142`). Nel modello `Liquid` maschera e filo escono dal campo, quindi il campo deve
  conoscere quella curva:
  - **piccoli** (lato lungo sotto `SmallShapeCutoffPx`, 256 px): l'engine disegna già un rettangolo arrotondato col raggio
    equivalente (×0,82, `ui/fluid/FluidShape.kt:73-83`, `:117-133`); il campo usa lo stesso raggio. Niente cambia. Una
    capsula è un semicerchio esatto in entrambi i casi;
  - **grandi a riposo** (pannelli, menu, fogli, card): `pieceSmooth` porta il profilo dell'angolo da cerchio a una
    superellisse adattata alla costruzione dell'engine. Prova fatta per questo piano
    (`artifacts/liquid-glass/bench/piano/angolo_continuo.py`): con `smoothing` 0,6 l'arco di cerchio al raggio nominale si
    scosta dalla curva continua fino all'**1,25% del raggio** (≈1 px a 84 px di raggio, cioè 28 dp a densità 3); una
    superellisse di semiasse 1,18 r ed esponente 2,45 fino allo **0,5%** (≈0,4 px allo stesso raggio, 0,7 px a 144 px). La
    distanza fuori dal contorno è approssimata, il contorno no: lo spessore del filo varia di qualche punto percentuale
    lungo l'angolo. Il clip hardware resta il rettangolo arrotondato al raggio nominale, come oggi;
  - **in viaggio** l'angolo è circolare, come oggi (`FluidForm.Slab.smoothing`): `pieceSmooth` sale da 0 al valore di
    riposo nell'ultimo tratto del viaggio invece di scattare all'arrivo, che è meglio di oggi (dove il path continuo arriva
    «in punto all'1»);
  - **se mezzo pixel si vede** sui raggi più grandi (fogli): quelle sole superfici passano a riposo al nucleo ad anello,
    campionato dal path continuo, con la luce registrata in un layer. È il ripiego, non la partenza: costa un ciclo da 96
    lati per pixel di fascia.
  La scena `f1a/angolo-continuo` del banco mette accanto path dell'engine, campo circolare e campo raccordato, ingranditi,
  e un test JVM misura lo scarto sul contorno (F1A.1).
- **Forma → piano.** `RectangleShape` → raggi zero. `CornerBasedShape` (comprese `ContinuousCornerShape`, col suo
  `smoothing`, e le capsule) → i quattro raggi alla taglia del nodo, tetto a mezzo lato corto. `FluidConcentricShape` → si
  risolve al layout in quattro raggi e rientra nel caso precedente; da sola vale il proprio minimo. Ogni altra `Shape` il
  cui `Outline` è generico → **quella superficie resta `Classic`** (oggi lì la lente si spegne in silenzio,
  `bd/effects/Lens.kt:139`): nessun componente dell'engine ci mette del vetro, e portarla sull'anello non è in questo piano.

### 3.8 Fluid-physics: cosa resta e cosa si sostituisce

Premessa obbligata: dalla 3.0.0 il motore ha girato a `Balanced`. Tutto ciò che segue è una decisione di progetto che la
sessione S1 deve confermare **guardando il livello `Full`** (cancello C2 di F0).

| resta | perché |
|---|---|
| `FluidForm` (`Slab`, `Poly`, `Group`), `FluidCornerRadii` | API pubblica; è il vocabolario del campo |
| `FluidPhysicsState`: `morphTo`, `snapTo`, `driveExternally`, transiti (`SlabTransit`, `RingTransit`), scalo, oltrepasso come gonfiore della geometria | è la parte che dà «la sagoma in viaggio non è mai un rettangolo» (R4 B1, alta) senza scalare un layer |
| `LiquidDrop` (48 molle, `fp/FluidPhysicsLiquid.kt:92`) e l'anello a 96 punti | è l'unica cosa nell'engine che produce la sagoma «panciuta e storta» dei frame; le sue molle di serie (`dropOpen`: smorzamento 0,72, rigidezza 500, `ui/fluid/FluidMotion.kt:219-228`) cadono dentro l'intervallo che R4 ricava per il menu (0,65–0,75 e 500–800, **bassa**) |
| `FluidPhysicsJourney`, accoppiamento dei pezzi, specchi geometrici, `fluidPhysicsContent`, `fluidPhysicsClip` | logica provata da 18 file di test |
| `FluidPhysicsTier` | `Full` diventa «materiale `Liquid`»; `Balanced` e `Lite` restano come sono |

| si sostituisce | con |
|---|---|
| le quattro sorgenti AGSL di `fp/FluidPhysicsShaders.kt` | i nuclei di `gf/` con il blocco di materiale unico |
| `physicsLensUniforms` (`fp/FluidPhysicsMaterial.kt:145`), che rifà a mano la formula di `glassSurface` | `glassRecipe` |
| `PhysicsRimRenderer`, `DropRimMesh` (`fp/FluidPhysicsRim.kt`): bordo a riposo, in viaggio, a rampa, a mesh | la luce: strato a riposo, blocco in corpo in viaggio |
| `PhysicsShadowLayers`, `fluidPhysicsShadow` | l'ombra unica |
| tinta come `drawPath` o nello shader secondo i casi (`tintInShader`, `fp/FluidPhysicsState.kt:962`) | sempre nello shader |
| limite «gruppo ≤ 6 pezzi» | 8 |

Si poteva scegliere l'opposto: buttare l'anello e fare tutto con lastre fuse più deformazione. Non lo faccio perché l'anello è
la sola via per una sagoma irregolare *non* ottenibile come unione di rettangoli (la goccia inclinata, il «fagiolo» di R4 B1),
e perché costa già solo nella fascia attorno alla sagoma (`PhysicsDropBandBlock`). Il prezzo: un nucleo in più da tenere
vivo. **Cosa farebbe cambiare idea** (S1): l'anello a `Full` che non sta nel fotogramma sulla finestra di un menu grande, o
difetti di resa propri del livello mai visto (normali mediate, fascia, frangia). Ripiego: il viaggio usa il nucleo a lastre
con la deformazione `flex` guidata dai modi bassi della goccia (baricentro, allungamento, inclinazione) e l'anello resta alle
sole sagome libere.

### 3.9 Adattività

Due velocità, come R3 cap. 3:

- **nello shader, per pixel, senza latenza**: il velo del corpo segue la luminanza dell'ingresso sfocato (`veilAdapt`); la
  tinta `Colored` tiene la tonalità e fa seguire la luminosità (R4 A7, alta: 100% su bianco, 58% su nero);
- **sulla CPU, poche volte al secondo**: `GlassProbe` registra una miniatura (8×8 o 16×16) del fondale del pannello in un
  `GraphicsLayer`, la legge con `toImageBitmap()`, ne ricava luminanza media, **varianza** e colore medio. Al più ogni
  100–150 ms, mai durante un lancio veloce. Da lì: il **lato** (chiaro/scuro) dei piccoli con isteresi a due soglie e
  permanenza minima, l'**inchiostro** di icone e testo, l'alfa dell'ombra (dalla varianza), il colore dell'ombra (dalla
  media).

**Dopo S1 (cancello C5): la lettura non passa da `toImageBitmap()`.** Sul Tab S9 ferma il filo principale circa 5 ms a
lettura, qualunque sia la taglia della miniatura, e chiamata da un altro filo fa cadere HWUI (registra un nodo suo sul
filo che la chiama: SIGSEGV riprodotto). Le miniature si **registrano sul filo principale** e si **leggono con un
`HardwareRenderer` che rende in un `ImageReader`**: uno per finestra, una casella per sonda, la richiesta fatta dopo il
disegno della finestra, i conti su un `HandlerThread`. Provato in S1-bis (`artifacts/liquid-glass/sessioni/S1-bis.md`;
il prototipo è `sample/…/measure/ProbeReader.kt`): nessun fotogramma perso in più con 4 sonde ogni 100 ms, pixel
identici, 3 ms dalla richiesta al numero. Un `RenderNode` dell'albero della finestra si può disegnare dentro un secondo
`HardwareRenderer`: era l'incognita, e regge. Il resto di questa sezione (segnali, schedulatore, filtro) non
cambia; dove il testo dice `toImageBitmap()` si legge «la lettura asincrona».

**Quando si sveglia la sonda.** Non «quando la superficie ricattura»: una sorgente che si ri-registra è raccolta per
riferimento dalle superfici che la rigiocano, senza che nessuna ricatturi (`bd/DrawBackdropModifier.kt:749-772`; è
l'invariante 3, che resta). Sotto un vetro fermo lo sfondo può passare da bianco a nero, il corpo lo mostra subito, e una
sonda agganciata a `needsRecord` non lo saprebbe mai. I segnali sono tre, nessuno dei quali chiede una cattura in più:

1. **revisione della sorgente**: `LayerBackdrop` porta un contatore che `LayerBackdropNode` incrementa quando ri-registra
   il proprio layer (`bd/backdrops/LayerBackdropModifier.kt:104-110`), e avvisa chi si è iscritto. Una sorgente tenuta ferma
   (`frozen`) non ri-registra e quindi non sveglia nessuno: giusto così;
2. **spostamento relativo**: la sonda è un nodo che conosce la propria posizione rispetto alle sorgenti, come la superficie;
3. **sorgenti che non sono registrazioni** (un `CanvasBackdrop`, per cui anche la superficie ricattura a ogni fotogramma):
   lettura a cadenza fissa e bassa finché il nodo è in scena.

**Chi la possiede e come scorre.** La sonda è un nodo a sé (`GlassProbeNode`, interno, in `gf/GlassProbe.kt`), che il ramo
`Liquid` di `glassSurface` mette accanto a `drawBackdrop` e che riceve lo stesso `Backdrop`: non tocca la firma pubblica di
`drawBackdrop` né lo stato privato di `refreshCapture`. Sonda solo il vetro «radice»: una superficie annidata in un pannello
di vetro o iscritta a un contenitore prende lato e inchiostro da chi la ospita. `toImageBitmap()` è una funzione sospensiva
e non può stare nel disegno, quindi: gli avvisi (che arrivano durante il disegno della sorgente) non scrivono stato e non
lavorano, lasciano solo un segnale in un canale a un posto; una coroutine del nodo, partita in `onAttach`, li consuma,
chiede a `GlassProbeScheduler` (puro) se e quando leggere, registra la miniatura **solo** se è cambiata la geometria (la
miniatura tiene le sorgenti per riferimento: dopo una revisione basta rileggerla), legge, calcola le statistiche, le passa
al filtro e scrive lo stato di snapshot (`FluidGlassAppearance`) fuori dal disegno. Il cancello C5 misura il costo della
lettura; la correttezza del flusso la provano il test dello schedulatore e la prova «bianco → nero» di F2.1.

La decisione è del **contenitore** (una barra gira tutta insieme: R1 C1, «adattamento uniforme») e **solo dei piccoli**:
`GlassLiquidProfile` marca i ruoli che girano (`Interactive`, `Floating`, barre e tab bar) e quelli che scuriscono in
continuo (`Modal`, pannelli, barre laterali: R4 A6, alta). Oggi il lato viene dal tema in un punto solo,
`GlassDefaults.isDarkSurface()` (`ui/fluid/GlassMaterial.kt:1271`): resta la sorgente **iniziale** e il ripiego; sotto `Liquid`
lo sovrascrive lo stato adattivo della superficie. La soglia del giro è incerta anche su iOS 27: in R5 M3 una barra chiara
sopra una foto a luma 51 resta chiara (corpo 141–146), cioè o la soglia non è raggiunta o il giro è più restio che in
iOS 26; un'inquadratura non decide. Si tara in S3.

### 3.10 Bordo dello scorrimento

Una **striscia**, non una lastra: nessuna sagoma, nessuna lente, nessun filo. Catturata con `drawPlainBackdrop`
(`bd/DrawBackdropModifier.kt:84`) a metà risoluzione, due nodi runtime in catena (orizzontale, verticale) con raggio per
pixel da una rampa, velo verso il colore del fondo e scurimento sul contenuto scuro nella seconda passata. Il nucleo è quello
di Haze (`SemanticBlurKernel`, Apache-2.0: 20 coppie più il centro per passata), portato con intestazione e voce
`LICENSES/Haze.md`. La striscia **esporta** se stessa come fa oggi la fascia (`exports = barGlass`,
`ui/fluid/FluidScreen.kt` in `FluidTopBar`): i tasti della barra rifrangono il contenuto già dissolto, non la pagina cruda.
Sotto `Classic` e sotto API 33 resta `FluidTopBarGlass` con `GlassFalloff.FadeDown`.

**Dopo S1 (cancello C4): confermate le due passate; la risoluzione (piena o metà) si sceglie a occhio in F2.4, partendo
dalla piena, perché sul tablet stanno nel fotogramma tutte e due.** Il primo fotogramma lento della striscia visto in
S1 era la compilazione a freddo del suo programma, non la nascita delle superfici (S1-bis: una seconda striscia costa
3,9 ms a metà risoluzione): il programma della striscia entra nel riscaldamento di F1A.7, e non serve altro.

**L'aspetto è quello di iOS 26, per decisione** (sezione 2): il contenuto si dissolve sotto i tasti con sfocatura e velo
progressivi (R4 B7), senza fascia e senza taglio. iOS 27 ha sostituito questo aspetto con una fascia uniforme a taglio netto
(R5 E1: dalla cima a 0,21 D sotto i tasti, velo 0,62–0,81, sfocatura uniforme, filo separatore): è proprio ciò che Alessio
ha chiesto di togliere, e **non si fa**. Conseguenze: `Automatic` vale il comportamento di iOS 26 (morbido sul telefono);
`Soft` e `Hard` sono quelli di iOS 26; su contenuto scuro la striscia **scurisce** (R4 B7), non schiarisce come la fascia di
iOS 27. Il materiale dei tasti che ci stanno sopra è invece quello di iOS 27: la combinazione non esiste su nessuna
versione di iOS, quindi il suo aspetto insieme (filo scuro del tasto sopra il contenuto dissolto) si giudica a occhio in F2.

### 3.11 Compatibilità

**Le API pubbliche di oggi compilano tutte.** Regola: nessuna firma tolta, nessun parametro tolto o riordinato, campi nuovi
con default in coda; l'unica eccezione alla coda è `FluidContextAction`, e ha la sua riga qui sotto.

| API | cosa succede |
|---|---|
| `glassSurface(state, tint, shape, edge, falloff, intensity, role, optics, opticalDepth, pressed, exports, layerBlock, sampleOnce, resampleIntervalMillis, frozen, heldWhileMoving, nestInContainer)` | firma identica (`ui/fluid/GlassMaterial.kt:1320-1370`). Dentro: `if (modello == Liquid) percorsoNuovo else corpoDiOggi` (il corpo di oggi resta verbatim) |
| `GlassOptics` (17 campi) | invariata. Sotto `Liquid` è **tradotta**: `blurScale`, `refractionHeight/Amount`, `depthEffect`, `dispersion`, `vibrancy`, `pressedDepthBoost`, `edgeFloor`, `backdropResolution`, `sizeResponse` valgono come oggi; `shadowRadius/Alpha` e `highlightAlpha/Angle` **pesano** l'ombra e la luce del profilo (rapporto col valore di serie del ruolo); `highlightWidth` non allarga più il filo oltre il tetto del profilo; `innerShadow*` è ignorata (è zero in ogni ruolo) |
| `GlassTint(overlay, fallback, hairline)` | costruttore a tre argomenti valido; `mode = Fixed` di default: una tinta scritta a mano resta la pellicola dipinta. Le tinte di `GlassDefaults` passano a `Neutral` |
| `GlassDefaults` (`optics`, `buttonOptics`, `panelOptics`, `stackedContentOptics`, le 16 tinte, `isDarkSurface`) | firme identiche; i valori servono a `Classic` e alla traduzione |
| `GlassRole` | invariato |
| `GlassFalloff.FadeDown`, `GlassEdge`, i parametri `barTint`, `FluidScreenDefaults.GlassFadeTail/ShieldDeadZone/ShieldRampDistance` | restano, deprecati. Una `glassSurface(falloff = FadeDown)` resta `Classic` da F1A a F2.5 (la fascia di oggi, intatta); da F2.5 sotto `Liquid` diventa la striscia del bordo morbido; `barTint` è ignorato |
| `fluidPhysicsSurface(state, backdrop, tint, role, optics, tier, intensity, tintFrom, tintBlend, opticsFrom, opticsBlend, sampleOnce, exports, pressState)` | firma identica (`fp/FluidPhysicsSurface.kt:76`) |
| il pacchetto `glass.backdrop` (tutto pubblico: `drawBackdrop`, `lens`, `blur`, `Highlight`, `Shadow`…) | invariato; `lens()` continua a esistere per chi chiama `drawBackdrop` a mano, con il difetto `radiusAt` corretto (F0.2) |
| `actions: @Composable RowScope.() -> Unit` | resta; `toolbar = { }` è un parametro **accanto** (F4A) |
| `FluidContextAction(label, icon, destructive, enabled, startsSection, selected, onClick)` (`ui/fluid/FluidGlassModal.kt:310-329`) | i campi nuovi (`subtitle`, `children`, `keepsMenuOpen`) vanno **prima** di `onClick`, come vuole il tipo («prima di `onClick`, così la lambda in coda continua a essere l'azione»): dopo, la lambda in coda non compilerebbe più da nessuna parte. Ma così la chiamata posizionale a sette argomenti `FluidContextAction("X", null, false, true, false, false, {})` passerebbe la lambda a `subtitle`. Quindi resta un **costruttore secondario con la firma di oggi** a sette parametri, che delega al primario; la chiamata posizionale e quella con tutti e sette i nomi risolvono su di lui. Garantite: costruzione e `copy` con i nomi. Non garantita: la destrutturazione posizionale (`component7` non è più `onClick`); nel repo le chiamate sono nominate o con la lambda in coda. Il caso posizionale entra in `GlassCompatSignaturesTest` (F5A.3) |

**`engine-wear`.** `FluidWearGlass.kt:37` costruisce un `GlassOptics` con 14 argomenti nominati e tre `GlassTint` a tre
argomenti: continuano a compilare perché nessun campo è stato tolto e l'unico nuovo ha un default. `FluidWearTheme` fornisce
`LocalFluidGlassModel provides FluidGlassModel.Classic`: l'orologio non cambia aspetto in questa tornata (GPU della serie W,
schermi da 200 dp, nessun riferimento Apple misurato per watchOS). Portare `Liquid` sull'orologio è un incarico a parte.

**Sotto API 33** (minSdk dell'engine: 26). `currentFluidGlassModel()` vale `Classic` se `!isRuntimeShaderSupported()`
(`bd/Platform.kt`), se la sonda di salute ha rifiutato una variante, o se un antenato lo chiede. API 31–32 e sotto 31
restano **identici a oggi** (R2 §1.5). Le API dichiarative nuove funzionano ovunque: la geometria dei viaggi è la stessa sui
tre livelli di Fluid-physics, cambia quanta ottica la veste. Senza AGSL restano spenti: adattività, bordo dello scorrimento
nuovo (resta la fascia), flessione a gel, luce ai vicini, contenuto deformato dalla lente (le icone si incrociano in
dissolvenza sotto API 31, con la sola sfocatura su 31–32).

**Come si prova che il ripiego è intatto.** Non con le immagini del banco, che rende solo AGSL: con i controlli di
selezione del percorso di 4.4 (API 26, 31–32, `Classic` forzato, sonda fallita), e con ciò che resta dichiaratamente non
verificabile senza un dispositivo sotto Android 13.

**Un driver che rifiuta.** La sonda (`gf/GlassFieldHealth.kt`, erede di `FluidPhysicsShaderHealth`) dà un verdetto per
sorgente. Se fallisce una variante del corpo o della luce: `Classic` per tutto il processo. Il fallimento **si vede**: una
voce in `FluidGlassDiagnostics`, una riga di log a livello errore, una striscia rossa nella galleria, un test strumentato che
fallisce (4.2). Mai più un ripiego silenzioso.

### 3.12 Invarianti ereditate e regole da riscrivere

Le 25 invarianti di R2 §8 valgono tutte, con questi aggiornamenti dichiarati (le schede cambiano in F1B.6 e F8.1):

| invariante | diventa |
|---|---|
| 11 «un bordo solo: sul chiaro colore scuro e `SrcOver` insieme» | un solo strato di luce, premoltiplicato, `SrcOver`; `glassRimRecipe` resta per `Classic` |
| 13 «pomelli e pillola sono il metro, bit per bit» | la **geometria** (squash, facce) resta fissata dai test; il **materiale** della lente passa alla ricetta unica |
| 15 «nessuno shader di vetro nuovo accanto a `GlassMaterial`: Fluid-physics è la deroga» | «gli shader del vetro vivono in `gf/` e in nessun altro posto»; la deroga sparisce |
| 18 «clip rounded-rect hardware» | resta dove la lastra non sporge; si spegne (non diventa `Generic`) dove ritaglia lo shader |
| 5 «mai un layer scalato sopra un vetro» | rafforzata: la crescita del tasto passa dal `layerBlock` alla geometria (F6.1); nel `layerBlock` resta la sola traslazione |
| 10 «il vetro va sul contenitore, mai sulla riga», 8 «vetro su vetro» | restano; in più la regola d'uso di Apple (vetro solo sullo strato di navigazione) entra nelle schede come raccomandazione. Il materiale pubblicato resta corpo e tinta **senza contenuto e senza luce** (3.3) |
| 3 «una sorgente che si ridisegna non invalida niente» | resta; la sonda adattiva non la aggira: riceve la revisione della sorgente come segnale, non come cattura (3.9) |
| «`Shape` stabile a riposo», curva dell'angolo fatta dal path esatto (R2 §1.1) | a riposo la curva la fa il campo col raccordo continuo (3.7); il clip resta il rettangolo arrotondato equivalente |

### 3.13 Mappa delle API: SwiftUI → Fluid

Firma essenziale; «c'è» = esiste nella 3.1.2 e resta, eventualmente con un parametro in più. Le firme nuove sono il contratto
che le fasi devono rispettare; i nomi dei parametri secondari li fissa il pacchetto che le scrive.

| SwiftUI | Fluid | stato |
|---|---|---|
| `glassEffect(_:in:)` | `@Composable fun Modifier.fluidGlass(glass: FluidGlass = FluidGlass.Regular, shape: Shape = FluidCapsuleShape, backdrop: GlassBackdropState = currentGlassBackdrop()): Modifier` | F3.2 |
| `Glass.regular / .clear / .identity`, `.tint(_)`, `.interactive(_)` | `@Immutable class FluidGlass` con `Regular`, `Clear`, `Identity`; `fun tint(color: Color?): FluidGlass`; `fun interactive(enabled: Boolean = true): FluidGlass` | F2.2 |
| `GlassEffectContainer(spacing:)` | `@Composable fun FluidGlassContainer(modifier: Modifier = Modifier, spacing: Dp = FluidGlassContainerDefaults.Spacing, animation: FluidGlassAnimation = FluidGlassAnimation.Default, content: @Composable () -> Unit)` | F3.2 |
| `@Namespace`, `glassEffectID(_:in:)` | `@Composable fun rememberFluidGlassNamespace(): FluidGlassNamespace`; `fun Modifier.fluidGlassId(id: Any, namespace: FluidGlassNamespace): Modifier` | F3.2 |
| `glassEffectTransition(_:)` | `fun Modifier.fluidGlassTransition(transition: FluidGlassTransition): Modifier`; `enum class FluidGlassTransition { MatchedGeometry, Materialize, Identity }` | F3.2 |
| `glassEffectUnion(id:namespace:)` | `fun Modifier.fluidGlassUnion(id: Any, namespace: FluidGlassNamespace): Modifier` | F3.2 |
| `withAnimation` | nessuna chiamata: il contenitore anima da solo i cambi; `FluidGlassAnimation.Default / None / Custom(spec)` | F3.2 |
| `.buttonStyle(.glass / .glassProminent / .glass(_:))` | `FluidGlassButton(…, style: FluidGlassButtonStyle = FluidGlassButtonStyle.Glass)` e lo stesso su `FluidGlassIconButton`; `Glass`, `Prominent`, `Clear`, `Glass(glass)`; `LocalFluidGlassButtonStyle` | c'è + F6.4 |
| `controlSize`, `buttonSizing`, `buttonBorderShape` | `FluidGlassSize` (c'è: `Compact`, `Regular`, `Large`) + `LocalFluidGlassSize`; `sizing: FluidButtonSizing = Fitted \| Flexible`; forma di serie `FluidCapsuleShape` (c'è) | c'è + F6.4 |
| `.toolbar { ToolbarItem; ToolbarItemGroup; ToolbarSpacer }` | parametro `toolbar: (FluidToolbarScope.() -> Unit)? = null` su `FluidScreen`, `FluidCollapsingTopBar`, `FluidPageChrome`; `interface FluidToolbarScope { fun action(id: Any, icon: ImageVector, label: String, placement: FluidToolbarPlacement = Automatic, role: FluidButtonRole? = null, prominent: Boolean = false, tint: Color = Color.Unspecified, badge: FluidTabBadge? = null, enabled: Boolean = true, sharedBackground: Boolean = true, menu: (() -> List<FluidContextAction>)? = null, onClick: () -> Unit); fun text(id: Any, label: String, …); fun item(id: Any, placement: …, content: @Composable () -> Unit); fun group(placement: …, content: FluidToolbarGroupScope.() -> Unit); fun spacer(sizing: FluidToolbarSpacing = Fixed, placement: …); fun search(spec: FluidSearchSpec) }` | F4A.2 |
| `ToolbarItemPlacement` | `enum class FluidToolbarPlacement { Automatic, Navigation, Leading, Principal, Trailing, PrimaryAction, SecondaryAction, ConfirmationAction, CancellationAction, DestructiveAction, BottomBar, PinnedTrailing }` | F4A.1 |
| `ButtonRole.confirm / .close / .cancel / .destructive` | `enum class FluidButtonRole { Confirm, Close, Cancel, Destructive }` (spunta tinta, X, testo, rosso) | F4A.2 |
| `sharedBackgroundVisibility`, `badge`, `matchedTransitionSource` su `ToolbarContent` | parametri `sharedBackground`, `badge` di `action`; ogni elemento è una sorgente per il suo `id` | F4A.2, F5A.1 |
| `UIBarButtonItem.identifier` | `id` obbligatorio su ogni elemento: è la chiave dell'accoppiamento fra stati e fra pagine | F4A.1 |
| `navigationTitle`, `navigationSubtitle`, `toolbarTitleDisplayMode` | `FluidScreen(title, subtitle, titleFacets)` (c'è: titolo grande che scorre col contenuto e si aggancia) + `titleMode: FluidTitleMode = Automatic \| Inline \| Large` | c'è + F4A.2 |
| `visibilityPriority(.low / .automatic / .high)`, `ToolbarOverflowMenu`, `.topBarPinnedTrailing` (27) | `priority: FluidToolbarPriority = Automatic` con `Low`, `Automatic`, `High` (escono prima i più bassi); `overflow { }` per ciò che sta sempre nel menu «Altro»; `PinnedTrailing` resta a destra e va nel trabocco solo con la ricerca attiva (R5, documentato). Il menu di trabocco è un menu normale | F4A.1 |
| `toolbarMinimizationBehavior(_:for:)`, `toolbarMinimizationSafeAreaAdjustment` (27; R1 lo chiamava `toolbarMinimizeBehavior`, nome della prima beta) | `FluidChromeController` (c'è, `ui/fluid/FluidScreen.kt:144`) + `barMinimize: FluidBarMinimizeBehavior = Automatic` con `Automatic`, `Never`, `OnScrollDown`, `OnScrollUp`; `Automatic` ritira la barra **solo** quando la ricerca è in posizione principale; `barMinimizeAdjustsInsets: Boolean = true` (il contenuto risale nello spazio liberato) | F4A.4 |
| `TabView { Tab; Tab(role: .search) }` | `FluidFloatingTabBar(selectedTabKey, scrollConnection, …) { tab(key, title, icon, badge, onClick); standaloneTab(key, icon, onClick, role = FluidTabRole.Standard) }` con `FluidTabRole { Standard, Search, Prominent }` | c'è + F4B.3 |
| `tabBarMinimizeBehavior(_:)` | `rememberFluidFloatingTabBarScrollConnection(inlineBehavior = …)` | c'è |
| `tabViewBottomAccessory`, `tabViewBottomAccessoryPlacement` | slot `inlineAccessory` / `expandedAccessory` | c'è |
| `tabViewSearchActivation` | `searchMode`, `searchBarContent` (c'è) + `searchActivation: FluidTabSearchActivation` | c'è + F4B.3 |
| `TabRole.prominent` (27) | `standaloneTab(…, role = FluidTabRole.Prominent)`: una sola, una scheda qualsiasi; se manca, il trattamento va alla ricerca; resta visibile a barra ripiegata | F4B.3 |
| `.sidebarAdaptable` | `FluidTabRail`, `FluidSidebar` (c'è) + `FluidAdaptiveNavigation(items, …)` che sceglie barra, rail o barra laterale dalla larghezza; su finestre larghe la tab bar sta **in alto** e un suo tasto la converte in barra laterale e ritorno (R1 T7), con un viaggio del piano | c'è + F4B.4 |
| `searchable(text:placement:prompt:)`, `searchToolbarBehavior`, `DefaultToolbarItem(kind: .search)` | `class FluidSearchSpec(query: String, onQueryChange: (String) -> Unit, placement: FluidSearchPlacement = Automatic, behavior: FluidSearchBehavior = Automatic, prompt: String? = null, onSearch: (() -> Unit)? = null)`; parametro `search: FluidSearchSpec? = null` su `FluidScreen`; `search(spec)` nello scope della barra; il campo è `FluidSearchField` (c'è) | F4A.1 (tipo), F4A.2 (parametro, scope, posa del campo di oggi), F4B.2 (comportamento) |
| `searchSuggestions`, `searchScopes`, token | slot `suggestions`, `scopes` di `FluidSearchSpec` (gli ambiti stanno **in linea**, sulla riga del campo, come in iOS 27); `FluidTokenField` (c'è) | F4A.1 (campi del tipo), F4B.2 |
| `Menu { } label: { } primaryAction: { }` | `FluidBarAction(icon, contentDescription, onClick, actions, opensOn)` e `FluidMorphMenuButton(actions, onClick)` (c'è); nello scope della barra `action(…, menu = { })` | c'è + F5A.2 |
| `Section`, sottomenu, `Toggle`/`Picker` in menu | `FluidContextAction(label, icon, destructive, enabled, startsSection, selected, onClick)` (c'è) + campi con default prima di `onClick`: `subtitle: String? = null`, `children: List<FluidContextAction> = emptyList()`, `keepsMenuOpen: Boolean = false` | c'è + F5A.3 |
| `menuOrder`, `menuActionDismissBehavior`, `preferredElementSize` | `enum class FluidMenuOrder { Automatic, Priority, Fixed }`; `keepsMenuOpen`; `enum class FluidMenuElementSize { Small, Medium, Large }` | F5A.3 |
| `contextMenu(menuItems:preview:)`, `contentShape(.contextMenuPreview, _)` | `rememberFluidContextMenu(actions, tint)` e i parametri `contextActions` (c'è) + `@Composable fun Modifier.fluidContextMenu(actions: () -> List<FluidContextAction>, previewShape: Shape? = null, preview: (@Composable () -> Unit)? = null): Modifier` | c'è + F5A.4 |
| `sheet`, `presentationDetents`, `presentationDragIndicator`, `presentationBackgroundInteraction` | `@Composable fun FluidGlassSheet(visible: Boolean, onDismissRequest: () -> Unit, detents: Set<FluidSheetDetent> = setOf(FluidSheetDetent.Large), dragIndicator: Boolean = true, undimmedUpTo: FluidSheetDetent? = null, source: FluidTransitionSource? = null, content: @Composable ColumnScope.() -> Unit)`; `FluidSheetDetent.Medium / Large / Fraction(f) / Height(dp)`; sotto resta `FluidGlassModalPortal(presentation = Sheet)` | F5B.2 |
| `matchedTransitionSource(id:in:)`, `navigationTransition(.zoom(sourceID:in:))` | `fun Modifier.fluidTransitionSource(id: Any, namespace: FluidGlassNamespace): Modifier`; `source: FluidTransitionSource?` su fogli, popover, avvisi; per le pagine `FluidPageTransition.Expand` (c'è) con la sorgente | F5A.1, F5B.3 |
| `alert`, `confirmationDialog` | `FluidAlert(…)`, `FluidConfirmSheet(…, origin)` (c'è) + `source`; `@Composable fun FluidConfirmationDialog(visible: Boolean, onDismissRequest: () -> Unit, source: FluidTransitionSource?, title: String? = null, actions: List<FluidAlertAction>)` (in linea, senza Annulla). Le varianti di iOS 27 «legate a un elemento» sono in realtà legate a un **dato** (`item:` opzionale, R5): nessun cambio visivo né di ancoraggio. Equivalente: sovraccarichi `FluidAlert(item: T?, …)` e `FluidConfirmationDialog(item: T?, …)`, come c'è già per `FluidGlassModalPortal(item: T?, …)` | c'è + F5B.1 |
| `popover`, `presentationCompactAdaptation` | `FluidGlassModalPortal(visible, onDismissRequest, origin, presentation = Popover \| Expand, …)` (c'è) + `source`, `compactAdaptation: FluidCompactAdaptation = Sheet \| Popover` | c'è + F5B.1 |
| `Toggle`, `Slider(neutralValue:ticks:)`, `Picker(.segmented)`, `Stepper` | `FluidSwitch`, `FluidSlider(value, onValueChange, valueRange, snapValues, …)`, `FluidSegmentedControl` (c'è) + su `FluidSlider`: `neutralValue: Float? = null`, `ticks: Boolean = false`, `thumbless: Boolean = false` | c'è + F6.3 |
| `List`, `Form`, `swipeActions`, `swipeActionsContainer()` (27) | `FluidListGroup`, `FluidGroupedItems`, `FluidListRow`, `Modifier.fluidSwipeActions` (c'è). Divergenza che resta: nell'engine l'azione parte al rilascio oltre la soglia e la riga non resta aperta, quindi il contenitore «una aperta alla volta» non ha un equivalente | c'è + F6.6 |
| `reorderable()`, `reorderContainer` (27) | `fluidReorderable`, `rememberFluidReorderState` (c'è) | c'è |
| `navigationTransition(.crossFade)` su un foglio (27) | `FluidGlassSheet(…, transition = FluidSheetTransition.CrossFade)`; documentato, nessun video: a occhio | F5B.2 |
| `scrollEdgeEffectStyle(_:for:)`, `scrollEdgeEffectHidden` | parametro `scrollEdge: FluidScrollEdgeStyle = FluidScrollEdgeStyle.Automatic` su `FluidScreen` (applicato da solo sotto barre e tab bar; `Automatic` è l'aspetto di **iOS 26**, per decisione: sezione 2 e 3.10); per le liste proprie `@Composable fun FluidScrollEdge(backdrop: GlassBackdropState, edge: FluidScrollEdgeSide, height: Dp, modifier: Modifier = Modifier, style: FluidScrollEdgeStyle = Automatic, exports: GlassBackdropState? = null)`; `enum class FluidScrollEdgeStyle { Automatic, Soft, Hard, Hidden }` | F2.4, F2.5 |
| `safeAreaBar(edge:)` | `@Composable fun FluidSafeAreaBar(edge: FluidScrollEdgeSide, modifier: Modifier = Modifier, content: @Composable () -> Unit)`: registra la misura, riceve insets e bordo | F2.5 |
| `backgroundExtensionEffect()` | `fun Modifier.fluidBackgroundExtension(): Modifier` (specchia e sfoca sotto barra laterale e ispettore) | F4B.4 |
| `ConcentricRectangle`, `.concentric(minimum:)`, `containerShape(_:)` | `class FluidConcentricShape(minimum: Dp = 0.dp) : Shape` (da sola vale il minimo; dentro un contenitore la risolvono i modificatori dell'engine); `fun Modifier.fluidContainerShape(shape: Shape): Modifier` | F3.5 |
| `accessibilityReduceTransparency / Motion`, contrasto | `LocalFluidMotionPolicy`, `LocalFluidSystemContrast` (c'è) + trasparenza ridotta letta dal sistema dentro `glassRecipe` | c'è + F6.7 |
| Cursore dell'utente in Impostazioni › Aspetto › Liquid Glass (27; sostituisce la scelta a due della 26.1) | `LocalFluidGlassPreference` (0 = ultra trasparente, 1 = del tutto colorato, di serie 0,5: muove insieme diffusione e velo) e una riga facoltativa in `fluidAppearanceSection(…, glassPreference, onGlassPreferenceChange)`, come c'è già per `glassEnabled` | F2.2 |
| luce che segue il movimento del dispositivo (nessuna API) | `LocalFluidGlassMotionLight` (di serie **spenta**) e una riga facoltativa in `fluidAppearanceSection(…, glassMotionLight, onGlassMotionLightChange)` | F6.5 |
| simboli di sistema (`Image(systemName:)`, peso e scala dei simboli) | `object FluidIcons` (al tratto a peso 500; pieni per la tab bar) e `@Composable fun FluidIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current, size: Dp = Dp.Unspecified)`: senza `size` prende `LocalFluidIconSize`, che tasti, azioni di barra, capsula e tab bar forniscono (0,6 del vetro; 28 dp nella tab bar), e 24 dp fuori da un vetro. Nessun parametro di peso: il peso è del set. `Icon` di Material resta lecito ma non segue l'ambiente | F0.8, F1A.11 |
| `controlSize` e `buttonSizing` che tornano ai valori di serie dentro fogli e popover (27) | le presentazioni dell'host azzerano `LocalFluidGlassSize` e lo stile d'ambiente dei tasti | F6.4 |
| `GeometryProxy.concentricCornerRadii` (27) | `@Composable fun rememberFluidConcentricRadii(minimum: Dp = 0.dp): FluidCornerRadii` | F3.5 |

### 3.14 Le icone

Non è materiale, ma decide quanto il materiale sembra giusto: un tasto è un vetro con un'icona dentro. R6 ha misurato che
il tratto non era il problema; lo erano taglia, colore e margini. La decisione di Alessio aggiunge un set proprio, più
grassetto.

- **Cosa.** `FluidIcons`: Material Symbols, stile arrotondato, riempimento 0 (al tratto) a **peso 500**, e riempimento 1 per
  le sole icone da tab bar (in Apple le barre sono al tratto e la tab bar piena: R6). `ImageVector` in un riquadro da 24,
  come quelli di oggi: l'API `icon: ImageVector` dell'engine non cambia.
- **Il generatore.** `tools/icons/gen_fluid_icons.py`: un elenco di nomi (`tools/icons/fluid-icons.txt`, un nome per riga,
  con il segno di chi serve anche piena) → i file Kotlin del set, generati e mai scritti a mano, più gli stessi tracciati
  in JSON per il banco (`tools/glassbench/icons/fluid-icons.json`), così le tavole di F1A hanno le icone vere. Le sorgenti
  vengono da `google/material-design-icons` a un commit fissato nello script; la rete serve solo quando si rigenera, i
  file generati stanno nel repo. `--check` dice se il set è vecchio rispetto all'elenco, come fa `gen_api_catalog.py`.
  Licenza e commit in `LICENSES/MaterialSymbols.md`. Un'app che vuole altre icone allo stesso peso lancia lo stesso
  script sul proprio elenco, verso un pacchetto suo.
- **Dove vive il set: in `engine-ui`**, nel pacchetto `ui/fluid/icons/`. Motivi: i componenti di `engine-ui` devono usarlo,
  quindi un modulo a parte sarebbe comunque una sua dipendenza e peserebbe uguale; ogni icona è una proprietà costruita al
  primo uso (come le Material di oggi), quindi chi non la nomina non la paga e R8 la toglie; e `engine-ui` espone già
  `material-icons-extended` come `api` (`engine-ui/build.gradle:51`), che è migliaia di icone: poche centinaia di KB di
  vettori non cambiano l'ordine di grandezza. **Criterio per cambiare idea**: se l'elenco supera le duecento icone, o se
  un'app senza R8 misura più di mezzo MB di differenza, le icone che i componenti dell'engine non usano passano a un
  modulo `engine-icons` facoltativo. `material-icons-extended` resta `api`: toglierla romperebbe le app.
- **Cosa copre.** Il censimento esatto è il primo passo del pacchetto. Un conteggio per `Icons.<stile>.<nome>` dà 88 nomi
  diversi fra `engine-ui` (41, in 17 file), `engine-ai-ui` (13), `engine-richtext` (12) e la galleria (44); R6 ne conta 55
  nel solo `engine-ui`. In più i verbi delle barre e le icone che le app usano davvero: le sorgenti delle app non sono in
  questo repo, quindi quell'elenco lo porta l'orchestratore (un censimento in sola lettura dei repo delle app) o lo
  aggiungono le app rigenerando.
- **Le taglie** (sezione 2) stanno in `FluidMetrics`. `FluidIcon` senza `size` prende la taglia dall'ambiente
  (`LocalFluidIconSize`): 0,6 del vetro in cui sta (R6: la sagoma Material occupa ≈0,75 del riquadro, quindi 26 dp danno
  0,44 del tasto, contro 0,44–0,48 di Apple, alta), 28 dp nella tab bar. Un `Icon` di Material con la sua taglia scritta
  resta com'è: la scheda dice di passare a `FluidIcon` per seguire.
- **L'ordine.** Il set e i tracciati per il banco nascono in **F0.8**, perché le tavole di F1A.5 devono giudicare il
  materiale con le icone giuste. Componente, adozione nei componenti dell'engine, taglie e margini dei tasti arrivano in
  **F1A.11**, e le misure della tab bar in **F1B.4**: prima di S2, che è la prima volta in cui Alessio vede il materiale
  sul dispositivo. R6 li proponeva più avanti (un pacchetto prima di F4A, la tab bar in F4B.3): anticiparli evita che in
  S2 il giudizio sul vetro sia sporcato proprio dalle due impressioni che R6 ha spiegato. L'inchiostro adattivo resta in
  F2.3 (prima c'è l'inchiostro monocromo del tema), il passo nel gruppo e i 12 dp fra gruppi in F4A.2, l'icona dei menu
  in F5A.2, `FluidGlassSize.iconSize` e la taglia d'ambiente in F6.4.
| `sensoryFeedback(_:trigger:)` | `FluidHapticEvent` e il modulo aptico (c'è) | c'è |

## 4. Verificare senza dispositivo

### 4.1 Quattro strumenti, e cosa prova ciascuno

| strumento | prova | non prova |
|---|---|---|
| **banco fermo** (`tools/glassbench/glassbench.py`) | che ogni shader compila; l'ottica a riposo accanto ai ritagli Apple, con le stesse misure di R4 e R5; le regressioni degli shader e dei numeri che li guidano, contro immagini di riferimento | tempi, precisione della GPU, P3, tutto ciò che è Compose, e **quale percorso** l'engine sceglie su un dato Android (il banco rende solo AGSL: il ripiego sotto API 33 non lo vede) |
| **banco del movimento** (`tools/glassbench/glassmotion.py`, da costruire) | fasi, tempi, superamenti, sagome fotogramma per fotogramma accanto ai collage Apple; i primi cinque fotogrammi | il dito vero, la cadenza reale, gli scatti del RenderThread |
| **test di logica** (JVM in `engine-ui/src/test`, Robolectric in `sample/src/testDebug`) | geometria, ricetta, isteresi, accoppiamento, modello delle barre, compatibilità delle firme, **selezione del percorso** per livello di API | l'aspetto (Robolectric non esegue AGSL) |
| **sessioni sul dispositivo** | tutto il resto | — |

### 4.2 Il banco fermo

**Controllo di compilazione permanente** (F0.1, F0.3). Tre barriere, perché `flat` ne ha passate zero per otto giorni:

1. `glassbench.py check` compila ogni sorgente AGSL dell'engine e del banco ed esce con 1 se una fallisce. Chi tocca uno
   shader lo lancia prima di consegnare (non chiede Gradle). Va esteso a leggere `gf/*.kt` (oggi legge solo
   `bd/internal/Shaders.kt` e `fp/FluidPhysicsShaders.kt`).
2. `test/glass/field/ShaderSourceLintTest.kt` (JVM, gira con i test normali): estrae ogni `const val …ShaderString`,
   segnala le parole riservate di SkSL usate come identificatori (`flat`, `noperspective`, `input`, `half` come nome…), i
   cicli senza limite costante, gli indici di array non costanti, `dFdx`/`fwidth`. Non compila: è la rete che gira anche
   quando nessuno ha lanciato il banco.
3. Sul dispositivo: `sample/src/androidTest/…/GlassShaderCompileInstrumentedTest.kt` compila ogni sorgente con
   `android.graphics.RuntimeShader` e **fallisce** elencando sorgente ed errore; la sonda di salute pubblica i verdetti in
   `FluidGlassDiagnostics` e la pagina «Misure» li mostra in rosso. Si lancia a ogni sessione. Le sorgenti sono `internal`
   in `engine-ui` e il test sta in un altro modulo: passa da `FluidGlassDiagnostics.compileAllShaders()`, pubblica e di
   sola diagnostica (nome della sorgente → errore o niente).

**Scene di riferimento alla stessa inquadratura dei frame** (F0.3), in `tools/glassbench/scenes/rif/`:

| scena | ricostruisce | ritaglio o frame Apple |
|---|---|---|
| `rif-tondo-chiaro` | tasto tondo su fondo 174–184 | `rif/ritagli/A1_bordo_di_luce.jpg`, `f8632` |
| `rif-capsula-chiara` | capsula su fondo 200–210 | `f28855` |
| `rif-capsula-piatto`, `rif-capsula-testo` | la stessa capsula su fondo piatto e su testo nero | `rif/ritagli/A3_ombra.jpg`, `A5_smerigliatura.jpg`, `f21570`, `f21300` |
| `rif-barra-menu-foto` | barra e menu sulla stessa foto e sulla striscia bianca | `f13040`, `f13080` |
| `rif-bianco-nero` | vetro chiaro e scuro su bianco e nero puri; pannello grande | `rif/ritagli/A6_chiaro_scuro.jpg`, `f27430`, `f27466`, `f27520`, `f27850` |
| `rif-tinta` | rosso su bianco e su scacchi, verde su nero | `rif/ritagli/A7_tinta.jpg`, `f29795`, `f29865` |
| `rif-lente-griglia` | lente grande su griglia | `rif/ritagli/A_lente_griglia_f1485_f1700.jpg` |
| `rif-tondo-scuro` | tasto scuro su fondo 28–50 | `f12384` |
| `rif-bordo-scorrimento` | testo che scorre sotto una riga di tasti | `f16636`–`f16700`, `f17058`, `f17132` |
| `rif27-tabbar` | tab bar e accessorio sopra pagina 235 e arte colorata: **il confronto di Apple fra iOS 26 e iOS 27 sulla stessa scena** | `rif/ios27/ritagli/M1_tabbar_ios26_sopra_ios27_sotto.jpg` |
| `rif27-tabbar-telefono` | tab bar su pagina 238 con il tondo a destra | `rif/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` |
| `rif27-tasto-bianco` | tasto tondo su bianco 253 | `rif/ios27/ritagli/E1_bordo_scorrimento_alto_278.jpg` |
| `rif27-scuro` | tab bar scura su griglia colorata; barra chiara sopra una foto scura | `rif/ios27/ritagli/M3_tab_bar_scura_292_0632.jpg`, `B11_barra_e_trabocco_269.jpg` |
| `pagina-chiara`, `pagina-scura`, `pagina-nera` | gli stessi tre elementi (tasto, capsula, menu) sui **fondi veri dell'engine**: `#F2F2F7`, `#0D0D0F` (`ui/theme/FluidTheme.kt:352-353`) e nero assoluto, più bianco puro | nessun frame: è dove si decide se il materiale stacca davvero dalla pagina delle app |

Per ogni scena il banco calcola **gli stessi numeri di R4 e di R5 con lo stesso metodo**: `glassbench.py measure` porta
dentro il repo gli attrezzi che servono (`rif/tools/rim.py` profili radiali del filo, `profile.py`, `probe.py` luminanza e
nitidezza, `scrolledge.py` inviluppo del contrasto, `ios27_misure.py` per le schede M1, M4, T6 di R5; oggi stanno in
`artifacts/`, ignorata da git) e li applica al PNG reso. Il giudizio B1 parte da una tabella «misurato sul banco / misurato
su Apple / fiducia», non da un'impressione. La smerigliatura si misura sempre su **due** grandezze: contrasto che resta
(deviazione standard) e nitidezza che resta (gradiente medio); una sola non distingue un vetro smerigliato da una
dissolvenza (3.6).

**Ritagli ingranditi obbligatori.** Un render intero arriva ai critici rimpicciolito e un filo da 2–3 px non sopravvive. Per
A1, A2 e per le sottocategorie del bordo ogni tavola porta, accanto all'intero, i ritagli a 6× di un bordo dritto e di un
angolo (`render --crop … --zoom 6`, che il banco ha già).

**I numeri delle scene vengono dall'engine.** Gli uniform di una scena `material` non si scrivono a mano nel JSON: li
calcola `glassRecipe` e li scrive lo stesso scrittore delle tracce (una scena ferma è una traccia di un fotogramma:
`test/glass/field/trace/GlassSceneExportTest.kt`, con la stessa variabile d'ambiente di 4.3). Così una tavola scelta da
Alessio e una misura contro R5 descrivono ciò che l'engine disegnerà, e un ritocco al profilo in F2, F3 o F6 muove le
immagini di riferimento. Eccezione dichiarata: nel tratto F1A.2–F1A.4, prima che la ricetta esista, gli shader si provano
con scene scritte a mano (`scenes/f1a/prova-*`), che non diventano mai immagini di riferimento.

**Immagini di riferimento** contro le regressioni: `tools/glassbench/golden/`, tracciate in git, una ventina di scene a
densità 2 senza fotografie esterne al repo, e `glassbench.py compare` (differenza media e massima per scena, tolleranza
stretta: Skia in software è deterministico a parità di versione; F0.3 porta `requirements.txt` da intervalli, com'è oggi
(`skia-python>=144,<145`), a versioni esatte, e le scrive in un manifesto accanto alle immagini). Due famiglie, con ciò che
provano davvero:

- `golden/classic/`: **parità ottica degli shader e dei numeri di `Classic` sul PC**. Coprono le sorgenti AGSL di oggi e i
  numeri che il banco legge da `ui/fluid/GlassMaterial.kt` (le ottiche dei ruoli, la regola della taglia). **Non** coprono
  il disegno Kotlin (pellicola, filetto, anello e ombra a path, che il banco ridisegna per conto suo), né quale ramo
  `glassSurface` prende su un dato Android, né il ripiego sotto API 33, che il banco non rende. Non sono «la prova di E1»:
  sono una delle tre, con i test del vetro di oggi che restano verdi senza modifiche e con i controlli di selezione del
  percorso di 4.4;
- `golden/liquid/`: generate dalla ricetta, si rigenerano a ogni fase che cambia il materiale di proposito, con il diff
  nel rapporto di fase.

**Estensioni che servono** (F0.3): elementi `kind: "material"` che compongono ombra + corpo + luce nell'ordine dell'engine;
shader senza ingressi (oggi `render_shader` dà per scontato l'ingresso `content`, `tools/glassbench/render.py`); il modo di
comporre per strato; la mescola a monte della via A fra gli ingressi; il contenuto con sfocatura e scala proprie e con un
**effetto a shader** sul proprio layer (servirà alla lente del contenuto di F5A); il **path continuo dell'engine** (oggi il
banco disegna rettangoli arrotondati circolari: per la scena dell'angolo serve il porto dell'emettitore di
`ui/fluid/FluidShape.kt`); le versioni esatte.

### 4.3 Il banco del movimento

**Il principio.** Ogni grandezza che arriva a uno shader in un fotogramma è `mappa(progresso, parametri)`: `mappa` è una
funzione **pura** e interna dell'engine, il `progresso` viene da una `AnimationSpec` di `FluidMotion` o da un integratore a
passo fisso (la goccia: 1/240 s, già così). I composable usano quelle stesse funzioni; il banco valuta la stessa spec in
forma chiusa (`spec.vectorize(Float.VectorConverter).getValueFromNanos(...)`, che è matematica pura e gira su JVM: lo fa
già `FluidMotionTokensTest`). Non esiste una seconda descrizione del movimento: se la traccia è giusta e il dispositivo no, il difetto è nel tempo (cadenza,
fotogrammi persi), non nella forma. Molto è già scritto così: `fluidMenuContentAlpha`, `fluidMenuMaterialBlend`,
`fluidMenuInflation`, `fluidMenuDropSeed` (`ui/fluid/FluidMenuJourney.kt:63-476`), `Transit.formAt(t)`, `LiquidDrop`,
`barPieceJourney` (`ui/page/FluidPageMotion.kt:469`), `fluidPressTransform` (`ui/fluid/FluidPress.kt:516`).

Due limiti onesti del principio. Le tracce «di oggi» di F0.5 nascono prima che i composable chiamino una funzione di
fotogramma: per le parti che oggi vivono dentro i composable (l'assemblaggio del viaggio del menu) il test le ricompone, ed
è a tutti gli effetti una seconda descrizione; la copre la convalida contro la registrazione di S1 (F0-a), e sparisce
quando F1B e F5A fanno chiamare ai composable le stesse funzioni. E la goccia: è a passo fisso, quindi sul PC è
riproducibile; che coincida col dispositivo, dove il passo insegue il tempo reale, lo dice solo F0-a.

**Le funzioni pure**, una famiglia per fase (interne, in `gf/trace/` o accanto al codice che le usa):

| scenario | funzione | fase |
|---|---|---|
| pressione e rilascio di un tasto | `pressFrame(tMs, taglia, percorsoDelDito)` | F0.5 (oggi), F6.1 |
| menu dal tasto, apertura e chiusura | `menuJourneyFrame(tMs, ancora, pannello, verso)` | F0.5 (oggi), F5A.2 |
| barra fra due stati | `planeJourneyFrame(tMs, statoA, statoB, interruzione = null)`: con `interruzione` (istante e stato nuovo) il viaggio riparte da dove si trova | F3.1, F4A |
| fusione e divisione | `planeJourneyFrame` con pezzi sovrapposti | F3.3 |
| comparsa per lente | `materializeFrame(tMs, verso)` | F3.3 |
| giro chiaro/scuro | `adaptiveFrame(tMs, serieDiLuminanza)` | F2.1 |
| bordo dello scorrimento | `scrollEdgeFrame(scorrimentoPx)` | F2.4 |
| interruttore, cursore | `dropFrame(tMs, gesto)` | F6.3 |
| foglio, popover, avviso | `presentationFrame(tMs, sorgente, arrivo)` | F5B |

**Il formato di scambio** («traccia»), un file JSON per scenario:

```json
{ "schema": 1, "name": "menu-apertura", "phase": "F5A", "fps": 60, "durationMs": 620,
  "density": 3.0, "theme": "light", "size": [411, 300],
  "scene": "tools/glassbench/scenes/rif/rif-barra-menu-foto.json",
  "apple": { "collage": "artifacts/liquid-glass/riferimenti/collage/B1_menu_foto_f13036-13075.jpg",
             "t0Frame": 13046, "fps": 29.97 },
  "probes": [ { "name": "bordoAlto", "kind": "edge", "line": "v:205" } ],
  "frames": [ { "t": 0.0, "elements": [ { "kind": "material", "body": "kotlin:GlassBodyRingShaderString",
                "x": 0, "y": 0, "w": 411, "h": 300, "uniforms": { }, "light": { }, "shadow": { },
                "content": [ { "icon": "plus", "x": 0, "y": 0, "blurDp": 0, "alpha": 1, "scale": 1,
                               "effect": { "shader": "kotlin:GlassContentLensSlabShaderString", "uniforms": { } } } ] } ] } ] }
```

`scene` dà sfondo e pagina (una scena del banco fermo); ogni fotogramma porta gli `elements` nello stesso vocabolario delle
scene, con gli uniform già calcolati. `effect` sul contenuto è generico (uno shader con un ingresso applicato al layer del
contenuto): nasce in F0.4 insieme allo schema, così la lente del contenuto di F5A si rende sul banco senza riaprire il
banco. Lo schema è documentato in `tools/glassbench/TRACE.md`. Lo scrive
`test/glass/field/trace/GlassTraceExportTest.kt`: un test JVM che **non scrive niente** se la variabile d'ambiente
`GLASS_TRACE_DIR` non c'è (così i giri normali di test non lasciano file) e, quando c'è, scrive tutte le tracce registrate.
Produrre tracce chiede quindi un posto Gradle: `./gradlew.bat --no-daemon --no-watch-fs --max-workers 2
:engine-ui:testDebugUnitTest --tests "*GlassTraceExportTest*"`.

**I comandi** di `glassmotion.py` (Python nell'ambiente `artifacts/liquid-glass/venv`, `ffmpeg` già installato):

- `render <traccia>`: un PNG per fotogramma;
- `video <traccia> [--slow 4]`: `.mp4` a velocità vera e rallentato;
- `collage <traccia> --step-ms 33.4 --cols 8`: collage alla **cadenza di Apple** (29,97 fps), allineato a `t0Frame`;
- `pair <traccia>`: il nostro collage sopra i fotogrammi di Apple, con i millisecondi sotto ogni fotogramma. I fotogrammi
  di Apple si estraggono **dal video** a partire da `t0Frame` (`rif/video/`, con `ffmpeg`), non dal collage già fatto: i
  collage di R4 sono griglie che partono prima dell'istante zero (`B1_menu_foto` parte da `f13036`, lo zero è `f13046`) e
  non si allineano fotogramma a fotogramma. Il collage di R4 resta il riferimento che i critici aprono;
- `first <traccia>` e `last <traccia>`: i primi e gli ultimi cinque fotogrammi a 60 fps ingranditi (lampi e scalini alla
  partenza e all'arrivo: A2, C1, e i passaggi di consegne);
- `curve <traccia>`: CSV delle sonde (posizione di un bordo, larghezza di una capsula, luminanza di un rettangolo) e, accanto,
  i numeri di R4: superamento in percentuale, istante del picco, istante del riposo. È il controllo **numerico** di B2.

**Dove finiscono i file** (tutto sotto `artifacts/`, ignorata da git):
`artifacts/liquid-glass/moto/<fase>/<scenario>/` con `trace.json`, `frames/`, `<scenario>.mp4`, `<scenario>-lento.mp4`,
`<scenario>-collage.jpg`, `<scenario>-vs-apple.jpg`, `<scenario>-primi5.png`, `<scenario>-ultimi5.png`, `curve.csv`. I confronti fermi in
`artifacts/liquid-glass/bench/<fase>/`; le tavole di gusto in `artifacts/liquid-glass/gusto/<fase>/`; i rapporti di sessione
in `artifacts/liquid-glass/sessioni/S<n>.md` con i video in `artifacts/liquid-glass/sessioni/S<n>/`.

**Limite da dire prima.** I riferimenti sono a 29,97 fps: ogni tempo ha ±33 ms e le fasi più corte di un frame hanno un
errore del 30–50% (R4). Le molle si tarano quindi su tre osservabili robusti (superamento massimo, istante del picco, istante
del riposo), non sulla curva.

### 4.4 I test di logica

Un file di test per ogni funzione pura nuova, nello stile di quelli che ci sono. I test di `engine-ui` sono JVM puri (junit
e coroutines-test, niente Robolectric, niente regola di Compose: `engine-ui/build.gradle`); i test che montano composable
stanno in `sample/src/testDebug` (Robolectric, SDK 35 di serie, `@Config(sdk = …)` per gli altri livelli); ciò che chiede
AGSL vero sta in `sample/src/androidTest` e gira nelle sessioni. I guardiani dell'intero progetto, ognuno con il posto in
cui gira e l'aggancio che lo rende possibile:

| test | dove gira | cosa prova e con quale aggancio |
|---|---|---|
| `GlassRecipeTest` | JVM, `test/glass/field/` | la traduzione di `GlassOptics`, la regola della taglia su ombra e smerigliatura, `intensity` e qualità (tabella di 3.3) |
| `GlassRecipeParityTest` | JVM, `test/glass/field/` | **non** che la ricetta sia uguale a se stessa: che i tre chiamanti le passino la stessa cosa. Ognuno (`glassSurface`, `fluidPhysicsSurface`, palco) ha un adattatore sottile dal proprio stato a `glassDrawInputs(...)` (pura: taglia, fattore di scope, raggi, margine, ricetta); il test dà ai tre la stessa capsula, ferma, dentro nodi di taglia diversa e a scope ridotto, e confronta gli ingressi a meno della traslazione. È la terza correzione di Haze (l'ottica non si calcola dalla taglia del layer allargato) messa sotto test |
| parità al pixel | dispositivo, pagina «Parità» della galleria (F1B.6), S2 | la stessa lastra dai tre percorsi, affiancata e fotografata: è A3 dal vero |
| `GlassModelSelectionTest` | JVM, `test/glass/field/` | la tabella di verità di `resolveFluidGlassModel(sdk, shaderSani, richiesto, falloff, formaGenerica)`, pura come `resolvePhysicsTier` (`fp/FluidPhysicsTier.kt:34-47`): API 26, 31, 32, 33+; `Classic` forzato da un antenato; sonda fallita; `FadeDown` prima di F2.5 |
| `GlassPathSelectionTest` | Robolectric, `sample/src/testDebug`, con `@Config(sdk = [26, 31, 32, 35])` | monta un tasto, una capsula, un menu e una superficie fisica e legge da `FluidGlassDiagnostics` quante superfici hanno preso quale modello; su SDK 26 e 31–32 disegna in un bitmap come fa `FluidPageBarIconDrawTest` (nessun crash, pixel non vuoti); su SDK 35 Robolectric non esegue AGSL (R2 §9), quindi il test non si affida a ciò che la sonda di salute fa lì: forza i due verdetti con il gancio di test (sano: le superfici risultano `Liquid`, senza disegnarle; rifiutato, il caso «driver che rifiuta»: tutto risulta `Classic` e si disegna) |
| `GlassNodeCountTest` | Robolectric, `sample/src/testDebug`, più il gemello strumentato | i contatori di `FluidGlassDiagnostics` (nodi e layer vivi, incrementati dove i layer si creano e si rilasciano). Sotto Robolectric contano ciò che si alloca all'aggancio dei nodi, per `Classic` e per `Liquid` forzato con un verdetto di salute finto; i layer che nascono al primo disegno li conta solo il gemello sul dispositivo (S2) |
| `GlassCompatSignaturesTest` | JVM, `test/compat/` | le chiamate di oggi scritte per esteso, comprese quelle di `engine-wear` e la `FluidContextAction` posizionale a sette argomenti: se smettono di compilare, il test non compila |
| `ContinuousCornerFieldTest` | JVM, `test/glass/field/` | lo scarto fra il contorno del campo raccordato e i punti della curva dell'engine, entro la tolleranza di 3.7; i punti escono da una funzione pura estratta dall'emettitore di `ui/fluid/FluidShape.kt` (il `Path` di Android su JVM non si può campionare) |
| `GlassProbeSchedulerTest`, `GlassAdaptiveFilterTest` | JVM, `test/glass/field/` | una revisione della sorgente senza alcuna cattura produce una lettura; niente eventi, niente letture; cadenza massima; isteresi su gradino, rampa, scacchiera, rumore attorno alla soglia |
| `GlassProbeInstrumentedTest` | dispositivo, `sample/src/androidTest`, S3 | taglia e coordinate fisse, sfondo che passa da bianco a nero e ritorno: il lato pubblicato gira entro il tempo previsto, senza che la superficie si muova |
| `GlassPlaneTest`, `FluidToolbarModelTest` | JVM | grappoli, accoppiamento, classificazione; raggruppamento, trabocco, differenze fra stati |
| `FluidPageInterruptTest` | JVM, `test/page/` | un gesto indietro o un pop durante un push conserva posizione e velocità (F4A.6); scritto **prima** della correzione, deve fallire sul codice di oggi |

**Non verificabile in questa tornata (NV), dichiarato.** Cosa disegna davvero il ripiego su un Android 12 o precedente: non
c'è un dispositivo sotto API 33 e l'emulatore è escluso. Lo coprono, senza provarlo: il codice di `Classic` lasciato
verbatim, la selezione del percorso sotto test a tutti i livelli, il disegno senza crash sotto Robolectric, e il confronto
in S2 fra l'APK di riferimento della 3.1.2 e la build nuova con `Classic` forzato sul tablet (che esercita il ramo
`Classic` di API 33+, non quello senza AGSL), e il **livello simulato**: le due domande che decidono i ripieghi del vetro
passano da due funzioni sole (`isRenderEffectSupported()` e `isRuntimeShaderSupported()`, `bd/Platform.kt:33`, `:36`), e
`FluidGlassDiagnostics.simulateApiLevel(32 o 30)`, ascoltato solo nelle build di debug, le fa rispondere come su Android
12 o 11. Sul tablet l'engine disegna allora i **suoi** rami senza AGSL e senza `RenderEffect`, accanto all'APK di
riferimento con la stessa simulazione assente: si vede se un innesto nuovo ha cambiato quei rami. Non prova la piattaforma
(uno Skia più vecchio, un altro driver): quella resta NV. Se Alessio ha un dispositivo con Android 12, dieci minuti in una
sessione chiudono la voce.

### 4.5 Le sessioni sul dispositivo

Sei, concentrate. Fra una e l'altra ogni fase scrive le proprie voci nel «Backlog delle prove sul dispositivo» di
`artifacts/liquid-glass/stato.md`; una fase grafica si chiude **con riserva di dispositivo** e la riserva si scioglie alla
sessione che la copre (i difetti trovati lì riaprono la fase per un giro di correzione). Il Tab S9 fa anche da telefono con
`wm size` (e `wm size reset` alla fine) finché l'S25 non è concesso. Ogni sessione: un posto Gradle per
`:sample:assembleDebug`, installazione, `gallery.page` per andare dritti alla pagina, registrazione con `screenrecord`,
fotogrammi con `-fps_mode passthrough` (`tools/qa/qa-common.sh`), e sempre il test strumentato degli shader.

| sessione | quando | ordine del giorno | durata attesa |
|---|---|---|---|
| **S1** | F0 | (h) Fluid-physics al livello `Full` vero: registrare menu, menu morph, barra fra pagine, goccia del Playground, a confronto con le stesse scene a `Balanced` forzato; elenco dei difetti mai visti. Poi l'ordine di R3 cap. 9: (b) fotogramma di RenderThread del vetro di oggi contro il prototipo «corpo + luce» sui tre elementi del banco; (c) costo di un cambio di soli uniform, su tasto, capsula, pannello, a risoluzione piena e ridotta; (d) 1, 2, 4, 8 pezzi; (e) striscia del bordo in alto e in basso durante un lancio, a 2560 px e in formato telefono; (f) `toImageBitmap()` di una miniatura, sul main e in parallelo; (g) scatto alla prima compilazione di ogni variante; (i) il prototipo su una foto vera: bande, colori sul pannello P3, doppio antialias del filo con il clip acceso; (j) gli **APK di riferimento** installati e conservati (F0.7) | 2–3 h |
| **S2** | fine F1B | il materiale vero su ogni componente della galleria, chiaro e scuro, sulle pagine vere dell'engine; le tavole di gusto di F1A riviste sul pannello; bande e precisione (`half`); lo scalino viaggio→riposo della luce **e dell'ombra**; il filo con il clip acceso e spento; l'angolo continuo dei pannelli grandi; costo di una schermata tipica contro la baseline di S1; parità dei tre percorsi (pagina «Parità»); conta dei layer dal vero; `Classic` forzato accanto all'APK di riferimento della 3.1.2, e la galleria con il livello simulato a 32 e a 30 (i ripieghi dell'engine: 4.4); le icone e le misure nuove di barre e tab bar | 2–3 h |
| **S3** | fine F3 | adattività durante uno scorrimento vero su contenuto misto e sullo scacchiera (sfarfallio: tarare soglie e permanenza); la prova «bianco → nero» sotto un vetro fermo (`GlassProbeInstrumentedTest` e registrazione); bordo dello scorrimento durante un lancio; piano flottante: fusione, divisione, comparsa; il passaggio di consegne riposo↔palco nei primi cinque fotogrammi | 2–3 h |
| **S4** | fine F5B | barre che cambiano contesto nella pagina e fra pagine, ricerca, tab bar; il push interrotto dal gesto indietro e dal pop (F4A.6: nessun salto); la tab bar in alto sul tablet e la sua conversione in barra laterale; menu dal tasto e dal gruppo, contestuale, popover, avvisi, fogli; ogni viaggio registrato e confrontato con la sua traccia del banco | 3 h |
| **S5** | fine F6, apre F7 | tocco: flessione, bagliore, luce ai vicini, controlli; prima volta sull'**S25** (Adreno 830 contro 740, 120 Hz): prima l'APK di riferimento della 3.1.2 conservato in F0 (la colonna «prima» che sul telefono non esiste ancora), poi la build nuova, sugli scenari di F7.1, con Perfetto | 3–4 h (tablet e telefono) |
| **S6** | fine F7 e F8 | bilanci verificati su tablet e telefono; giro completo della galleria in ogni combinazione (`tools/qa/gallery-tour.sh`); accettazione finale di Alessio sui video dal dispositivo | 2–3 h |

### 4.6 Cosa si porta ad Alessio, fase per fase

Solo cose che si vedono in chat. Le scelte di gusto sono **tavole**: le varianti affiancate sugli stessi **sei** fondi, con
il ritaglio Apple accanto, i ritagli ingranditi del bordo, e una domanda a scelta («A, B o C?»). I sei fondi: le due pagine
vere dell'engine (`#F2F2F7` chiaro, `#0D0D0F` scuro), bianco e nero puri, testo, foto. Il grigio medio dei frame di Apple
serve alle misure, non alle scelte: è sulla pagina quasi bianca delle app che un filo chiaro e un velo spariscono, ed è lì
che si decide.

| fase | cosa vede | scelte |
|---|---|---|
| F0 | collage di Fluid-physics a `Full` accanto a `Balanced` dal tablet (ciò che non ha mai visto); i primi collage del banco del movimento accanto ad Apple, per dire se lo strumento gli basta | la goccia a `Full` gli piace? cosa tenere |
| F1A | tavole del materiale a riposo accanto al confronto di Apple fra iOS 26 e iOS 27 (`M1_tabbar_ios26_sopra_ios27_sotto`) e ai ritagli di R4 | bordo, velo e ombra nelle varianti «iOS 27 / iOS 26 / oggi»; lente; smerigliatura; tema scuro; grandi; poi la tavola con tutto insieme |
| F1B | collage dei viaggi di oggi rivestiti del materiale nuovo; dopo S2, video dal tablet | superamento della goccia |
| F2 | video del giro chiaro/scuro e del bordo dello scorrimento (quello di iOS 26) accanto ad Apple | altezza e forza del bordo; soglia e tempi del giro; ombra sopra il testo; il cursore trasparente/colorato |
| F3 | video di uno→due, fusione, divisione, comparsa accanto a `B2_select_done`, `B3_comparsa_play` | distanza di fusione, quanto si strozza il collo |
| F4A, F4B | video della barra che cambia contesto, della barra che si ritira, della ricerca, della tab bar accanto ai `B2_barra_*` e ai collage `V5_*` di R5 | misure dei gruppi e passo delle icone nel gruppo, margini |
| F5A, F5B | video del menu e del contestuale accanto ai `B1_menu_*`; fogli e avvisi | quanto scurire e sfocare dietro il contestuale; superamento del menu |
| F6 | video di tocco, interruttore, cursore accanto ai `B4_*`, `B5_*` | crescita del tasto, intensità del bagliore, ampiezza della luce dal movimento (che resta spenta di serie) |
| F7 | tabella prima/dopo dei tempi, e la prova che l'aspetto non è cambiato (stessi collage) | dove tagliare, se serve tagliare |
| F8 | video finale del giro dal dispositivo | versione, cosa migrare per prima |

## 5. Criteri di approvazione

Voti da 0 a 10 di tre critici (Fable: grafica; Sol: generalista; Muse: funzionale). Una fase passa quando
**ogni categoria principale da A a E ha media ≥ 8**, **ogni sottocategoria raggiunge il suo minimo** e non resta
aperto nessun rilievo Bloccante o Maggiore confermato. Le categorie F (costo) e G (accessibilità) sono
sorvegliate, non prioritarie in questa tornata: per loro vale solo il minimo scritto in tabella. I voti di Muse
sulle categorie grafiche (A, B1, B2, C1) non bloccano: se troppo bassi non entrano nella media, ma i problemi
che segnala si verificano e, se veri, si correggono. Tre giri di correzione per fase, poi si chiede ad Alessio.
Le soglie non si comunicano ai critici.

Tre precisazioni:

- **Il bersaglio di B1 e B2** sono le misure di R4 e, dove vale iOS 27, quelle di R5. Dove la ricerca dà fiducia
  bassa o una stima a occhio (molle, forza della lente, raggio di sfocatura, scala dei controlli, tema scuro) il
  bersaglio è la **scelta di gusto registrata** di Alessio, scritta nel piano o nel file di stato: i critici non
  votano contro un numero che non esiste.
- **Chiusa con riserva di dispositivo.** Una fase grafica approvata sul banco si chiude con riserva; la riserva
  si scioglie alla sessione sul dispositivo che la copre (4.5). Un difetto trovato in sessione riapre la fase per
  **un** giro di correzione, che non conta nei tre.
- **E2** comprende le immagini di riferimento del banco (`tools/glassbench/golden/`), tracciate nel repo.

| categoria | sottocategoria | minimo |
|---|---|---|
| **A. Qualità grafica** | A1 tridimensionalità a riposo (bordo di luce, corpo, ombra) | 9 |
| | A2 pulizia: nessun artefatto (bande, immagini doppie, bordi seghettati, lampi al primo fotogramma) | 9 |
| | A3 un solo materiale: componenti diversi sembrano la stessa cosa | 8,5 |
| | A4 tema chiaro e tema scuro | 8 |
| **B. Fedeltà ad Apple** | B1 ottica a riposo contro le misure di R4 (e di R5 dove vale iOS 27) | 8,5 |
| | B2 movimento: fasi e tempi contro le misure di R4 (e di R5 dove vale iOS 27) | 8,5 |
| | B3 comportamento e regole d'uso contro l'inventario R1 (dove si è scelto di divergere vale la scelta scritta) | 8 |
| **C. Movimento e tocco** | C1 continuità: nessuno scatto, salto o fotogramma sbagliato | 9 |
| | C2 risposta al dito (prontezza, interruzione, ripresa al volo) | 8,5 |
| | C3 movimento ridotto rispettato | 7 |
| **D. API e automatismo** | D1 dichiarativo: l'app non scrive animazioni né geometria | 8,5 |
| | D2 compatibilità: le API di oggi compilano e si comportano come prima dove non devono cambiare | 8,5 |
| | D3 KDoc, scheda, demo nella galleria | 8 |
| **E. Solidità** | E1 nessuna regressione nei componenti esistenti | 9 |
| | E2 test nuovi (logica pura, banco) | 8 |
| | E3 ripieghi (API < 33, qualità ridotta) e casi limite | 8 |
| **F. Costo** | F1 nessuno spreco gratuito (passate, catture, layer inutili) | 7 fino alla fase di ottimizzazione, poi 8,5 su misure del dispositivo |
| **G. Accessibilità** | G1 non si rompe ciò che c'è (contrasto del testo sul vetro, movimento ridotto, semantica) | 7 |

Ogni fase aggiunge le proprie sottocategorie specifiche (sezione 6). Una sottocategoria che in una fase non si
applica si segna NA.

## 6. Le fasi

### 6.0 Ordine, dipendenze, regole comuni

```
F0 ─▶ F1A ─▶ F1B ─▶ F2 ─▶ F3 ─▶ F4A ─▶ F4B ─▶ F5A ─▶ F5B ─▶ F6 ─▶ F7 ─▶ F8
 S1            S2          S3                        S4      S5    S6
```

| fase | titolo | pacchetti | con Gradle | sessione |
|---|---|---|---|---|
| F0 | Fondamenta e verità di base | 8 | 5 | S1 |
| F1A | Il materiale a riposo | 11 | 6 | — |
| F1B | Un solo motore | 7 | 7 | S2 |
| F2 | Adattività e bordo dello scorrimento | 6 | 6 | — |
| F3 | Piano flottante e geometria | 7 | 7 | S3 |
| F4A | Barre dichiarate in alto e fra pagine | 7 | 7 | — |
| F4B | In basso: capsula, ricerca, tab bar | 5 | 5 | — |
| F5A | Menu e menu contestuale | 6 | 6 | — |
| F5B | Popover, avvisi, fogli, zoom | 5 | 5 | S4 |
| F6 | Controlli e tocco | 9 | 9 | S5 |
| F7 | Ottimizzazione | 7 | 7 | S5, S6 |
| F8 | Consegna | 6 | 4 | S6 |

In tutto **84 pacchetti** (83 nel piano di partenza, più F4A.7 nato dalle misure di S1-bis), **74** dei quali chiedono almeno una volta un posto Gradle, e **6 sessioni** per 14–19 ore
previste sul dispositivo (è il programma iniziale: una fase riaperta da una sessione può chiederne un'altra, da concordare). Con tre critici per fase sono almeno 36 valutazioni, fino a 144 se ogni fase usasse i tre giri. Il
piano non dà una durata totale: dipende dalle code di Gradle, dalla RAM e da quante fasi si riaprono, e inventarla non
aiuterebbe nessuno.

Regole comuni a tutti i pacchetti:

- **Perimetro di scrittura.** Ogni pacchetto elenca i file che può scrivere. Due pacchetti che girano insieme non
  condividono un file; dove un file serve a due, la tabella dice chi lo tocca e in che ordine. Le scene del banco e le pagine
  della galleria hanno un nome di file per pacchetto.
- **Gradle.** I posti sono due e condivisi: l'orchestratore non lancia insieme più pacchetti che compilano. Un pacchetto «G»
  lavora senza Gradle finché può (banco, lettura, scrittura) e chiede il posto alla fine, con le regole di
  `C:\VibeCoded Projects\.gradle-coordinatore\REGOLE.md` (`gradle-slot.ps1 -Acquire`, al più 30 minuti,
  `--no-daemon --no-watch-fs --max-workers 2`, rilascio in `finally`). Il minimo per chiudere un pacchetto di codice:
  `:engine-ui:testDebugUnitTest`; per una fase: anche `:sample:testDebugUnitTest`, `:sample:assembleDebug`,
  `:engine-wear:compileDebugKotlin`, `:engine-ai-ui:compileDebugKotlin`.
- **Shader.** Chi tocca una sorgente AGSL lancia `glassbench.py check` e `compare` prima di consegnare e allega i diff.
- **Consegna di un pacchetto.** Codice, test, scene o tracce, voci per il backlog del dispositivo, una riga nella scheda.
  «Giusto per costruzione» non è una prova: i viaggi si giudicano dai fotogrammi (`ref/regole.md:711-715`).
- **Numeri di gusto.** Vivono nei file di profilo di `gf/` e in `FluidMotion`, mai sparsi; ogni numero tarato su R4 o R5
  porta nel KDoc la scheda e la fiducia, e ogni numero scelto a occhio porta la data della scelta di Alessio.
- **Scene dalla ricetta.** Tavole, misure e immagini di riferimento del materiale nuovo si rendono da scene generate
  dall'engine (4.2), mai da uniform ricopiati a mano.

---

### F0 · Fondamenta e verità di base

**Scopo.** Partire da ciò che è vero: Fluid-physics al livello `Full` (mai visto), gli strumenti che permettono di giudicare
senza dispositivo, e le sette misure che decidono l'architettura. **Entra**: controllo permanente degli shader; correzione
di `radiusAt`; banco fermo esteso e immagini di riferimento; banco del movimento; tracce dei viaggi di oggi; pagina «Misure»
con i prototipi; il set di icone e il suo generatore (3.14); S1. **Resta fuori**: qualunque cambio all'aspetto dell'engine
(i prototipi stanno solo in `sample/`; il set di icone nasce ma nessun componente lo usa ancora).

**Progetto tecnico.**

- La sonda diventa per sorgente. `FluidPhysicsShaderHealth` (`fp/FluidPhysicsTier.kt:65-117`) oggi dà due verdetti (le due
  sorgenti di base insieme, e a parte le due con la frangia: `dispersionHealthy`) e registra con `Log.w`: si generalizza in
  `gf/GlassFieldHealth.kt`, che li assorbe entrambi, con `fun verdicts(): Map<String, Throwable?>`, errore a livello
  `Log.e`, e `FluidGlassDiagnostics` (`bd/FluidGlassDiagnostics.kt`) che espone in sola lettura verdetti e
  `compileAllShaders()` (4.2). Il livello `Full` resta condizionato alle sorgenti che servono a quella famiglia.
- `radiusAt(coord, cornerRadii)` negli shader di Kyant (`bd/internal/Shaders.kt:70, 107, 173, 195`) riceve la coordinata del
  layer invece di quella centrata e sceglie sempre `radii.z`: si passa la coordinata centrata nei quattro punti. Modifica a
  codice Apache-2.0: nota in `LICENSES/AndroidLiquidGlass.md`. Tocca `Classic`: è l'unico cambio d'aspetto ammesso sotto
  API 33+ nel percorso vecchio, ed è una correzione (fogli arrotondati solo in alto).
- Banco: 4.2 e 4.3.
- Tracce «di oggi»: tre scenari resi con gli shader di oggi (`kotlin:SlabGroupRefractionShaderString`,
  `kotlin:PolyMorphRefractionShaderString`): apertura del menu Expand, pezzi della barra fra due pagine, pressione di un
  tasto. Servono a due cose: provare la catena traccia→video prima che esista il materiale nuovo, e avere il «prima» da
  mettere accanto al «dopo». La verità con cui si convalida il banco è la registrazione di S1 degli stessi tre viaggi.
- Pagina «Misure» (`sample/…/measure/`): scene fisse, lanciabili con `gallery.page`, con un contatore di tempi di
  fotogramma (`Window.addOnFrameMetricsAvailableListener`) scritto a schermo e in logcat, e sezioni di traccia per Perfetto.
- **I prototipi misurano la catena che il cancello C1 deve decidere.** La sonda del banco
  (`tools/glassbench/shaders/sonda-materiale.agsl`) ha **due** ingressi, `content` e `blurred`: su Skia funziona, su Android
  un `RenderEffect` ne collega uno (R3 cap. 1), quindi copiarla com'è misurerebbe altro. Il prototipo è una variante a **un
  ingresso** (`sonda-via-a.agsl`, nuova, compilata dal banco e copiata come risorsa del solo `sample`) e passa da
  `drawBackdrop` con la catena vera della via A: `colorControls` → `blur` → mescola a monte con
  `RenderEffect.createBlendModeEffect` → lo shader. La luce è un secondo shader senza ingressi (`sonda-luce.agsl`),
  disegnato sopra. Con questo si misurano: (b) corpo più luce contro il vetro di oggi sugli stessi tre elementi; (c) un
  cambio di soli uniform a cattura ferma (pressione, comparsa con `w`), **in due montaggi affiancati**: corpo a un layer e
  corpo a due layer (3.5), perché C1 sceglie fra quei due; (d) 1, 2, 4, 8 pezzi; (e) striscia del bordo a due passate;
  (f) lettura della miniatura; (g) prima compilazione di ogni variante; interruttore `Full`/`Balanced` per Fluid-physics.
- **Cosa di C1 resta da misurare dopo S1**, dichiarato: il costo degli shader **definitivi** (il prototipo ha i termini
  principali, non il velo adattivo, il raccordo degli angoli né la dispersione a tre campioni tarata): si rimisura in S2;
  il gesto continuo su una capsula larga con la flessione, che non esiste prima di F6: S5; e se Skia riusa la sfocatura
  quando cambiano i soli uniform non si osserva direttamente, si deduce dai tempi dei due montaggi.
- **APK di riferimento.** S1 conserva in `artifacts/liquid-glass/baseline/` due APK della galleria con il loro commit e lo
  stato dichiarato: la 3.1.2 com'è stata rilasciata (`flat` presente, Fluid-physics a `Balanced`) e la 3.1.2 con la sola
  correzione di `flat` (`Full`). Servono in S2 (confronto con `Classic` forzato) e in S5 (la colonna «prima» sull'S25, dove
  la 3.1.2 non è mai stata misurata).

**Bersagli di R4.** Nessuno: F0 non cambia l'aspetto. Fissa però la baseline numerica del vetro di oggi sulle scene `rif/`
(la colonna «oggi» di tutte le tabelle successive).

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F0.1 | Controllo permanente degli shader: sonda per sorgente, diagnostica, lint JVM, test strumentato | `gf/GlassFieldHealth.kt`, `fp/FluidPhysicsTier.kt`, `bd/FluidGlassDiagnostics.kt`, `test/glass/field/ShaderSourceLintTest.kt`, `test/fluidphysics/FluidPhysicsTierTest.kt`, `sample/src/androidTest/…/GlassShaderCompileInstrumentedTest.kt` | — | G | Opus |
| F0.2 | `radiusAt` corretto, con scena a raggi diversi | `bd/internal/Shaders.kt`, `LICENSES/AndroidLiquidGlass.md`, `tools/glassbench/scenes/f0-raggi-diversi.json` | — | — | Opus |
| F0.3 | Banco fermo 2: elementi `material`, shader senza ingressi, mescola a monte, effetto a shader sul contenuto, path continuo, lettura di `gf/`, `measure` (con nitidezza e le schede di R5), `compare`, versioni esatte, scene `rif/` (frame di R4, di R5 e pagine vere dell'engine), immagini di riferimento `classic` | `tools/glassbench/glassbench.py`, `render.py`, `kotlin_sources.py`, `measure.py`, `requirements.txt`, `scenes/rif/`, `shaders/sonda-via-a.agsl`, `shaders/sonda-luce.agsl`, `golden/`, `README.md` | F0.2 (per le immagini `classic`) | — | Fable |
| F0.4 | Banco del movimento: schema (con l'effetto sul contenuto e le interruzioni), `render`, `video`, `collage`, `pair` dai fotogrammi del video, `first`, `last`, `curve` | `tools/glassbench/glassmotion.py`, `tools/glassbench/TRACE.md` | F0.3 (usa `render.py` senza modificarlo) | — | Fable |
| F0.5 | Tracce: modello, scrittore (tracce e scene ferme), i tre scenari di oggi | `gf/trace/GlassTrace.kt`, `test/glass/field/trace/` | F0.4 (schema) | G | Opus |
| F0.6 | Pagina «Misure» e prototipi a un ingresso, nei due montaggi | `sample/…/measure/`, `sample/…/GalleryNav.kt` (una voce), `sample/src/main/res/raw/` | F0.1, F0.3 (le due sonde) | G | Fable |
| F0.8 | `FluidIcons`: censimento, elenco, generatore, set al tratto a peso 500 e pieno per la tab bar, tracciati per il banco, licenza | `tools/icons/`, `ui/fluid/icons/`, `tools/glassbench/icons/`, `LICENSES/MaterialSymbols.md`, `test/fluid/icons/FluidIconsTest.kt` | — | G | Opus (generatore), Haiku (censimento) |
| F0.7 | **S1** e rapporto, con i cancelli decisi e gli APK di riferimento conservati | `artifacts/liquid-glass/sessioni/S1.md`, `artifacts/liquid-glass/baseline/`, `stato.md` | tutti | G, D | orchestratore + Opus |

F0.1, F0.2, F0.3 e F0.8 partono insieme (un «G» alla volta fra loro); F0.4 dopo F0.3; F0.5 e F0.6 dopo, uno alla volta su
Gradle. Il banco (F0.3) disegna le icone dai tracciati di F0.8 quando ci sono, e dai suoi disegni di servizio finché non
ci sono.

**Cancelli che F0 deve chiudere** (scritti nel rapporto S1 e riportati in 3.x prima di aprire F1A):

| cancello | misura | se va bene | se va male |
|---|---|---|---|
| C1 costo dei gesti | (c), sui due montaggi | corpo a un layer (3.5) | corpo a due layer; si decide prima di F1A.6 e si riconferma in S2 sugli shader veri |
| C2 Fluid-physics a `Full` | (h) | resta come in 3.8 | ripiego di 3.8; i difetti del solo disegno muoiono in F1B, quelli del moto si correggono in F1B.1 |
| C3 pezzi | (d) | tetto 8, soglia 4 | tetto 4 |
| C4 bordo dello scorrimento | (e) | due passate | tre sfocature native incrociate (R3 cap. 4), resa più lontana da Apple |
| C5 sonda | (f) | `toImageBitmap()` | `HardwareRenderer` + `ImageReader`, o frequenza più bassa |
| C6 prima compilazione | (g) | riscaldamento all'avvio | riscaldamento pigro per famiglia + meno varianti (4 e 8 fuse in una) |

**Cancelli chiusi dopo S1 (2026-11-11).** Decisi dall'orchestratore sui numeri di `artifacts/liquid-glass/sessioni/S1.md`
(Tab S9, Adreno 740, 120 Hz; tre finestre da 8 s per misura: le mediane di `render_ms` e `gpu_ms` concordano entro
0,15 ms, `total_ms` e `ui_ms` no; la prima finestra delle scene ferme è fuori e si scarta, dichiarandolo; qualche
finestra isolata è fuori ed è segnata nelle tabelle di S1). Un solo dispositivo, con uno specchio dello schermo attivo. Dove questo riquadro contraddice
una frase delle sezioni 3.x o di una fase, vale questo riquadro.

| cancello | esito | decisione | cosa resta da riconfermare |
|---|---|---|---|
| C1 costo dei gesti | **bene** | **corpo a un layer** (3.5). Su tutte le lastre, anche 360×480 dp, un cambio di soli uniform sta nel fotogramma (p90 7,4–7,8 ms con la regola severa, nessun fotogramma perso in più). Il montaggio a due layer non si costruisce; se in S2 la comparsa di un pannello grande non regge, il ripiego è `two-composited`, non `two` (che nella comparsa costa più di `one`). La leva «nodi a monte fermi» non serve: Skia non riusa la sfocatura. La luce a riposo costa 0,1 ms per tre elementi: non si registra in un layer | shader definitivi in S2; flessione in S5; S25 |
| C2 Fluid-physics a `Full` | **bene** | **resta come in 3.8**: l'anello sta nella finestra di un menu (GPU circa ×2 rispetto a `Balanced`, stessi fotogrammi persi, 2–3%). Sagome e tempi sono identici a `Balanced`: i difetti sono del disegno e muoiono in F1B | GPU del menu al p90 (9 ms) sull'S25 in S5 |
| C3 pezzi | **bene, indicativo** (pezzi piccoli, shader di prova, senza ombra in viaggio) | **tetto 8, soglia 4**. GPU 1 : 1,1 : 1,4 : 1,9 per 1, 2, 4, 8 pezzi; il RenderThread non cresce. **In più**: la variante a 8 con uscita su `pieceCount` non si distingue da quella tagliata sul conteggio (differenze di 0,01–0,02 ms, sotto la dispersione fra finestre), quindi le varianti a lastre partono in **due, `1` e `8`** (corpo e luce), non quattro: è una scelta di lavoro, non una misura chiusa | **condizione di S2, non rinviabile**: nuclei veri e palco largo 2560 px; se a 2 pezzi la variante a 8 costa in modo misurabile, torna la `2` |
| C4 bordo dello scorrimento | **bene, indicativo** (shader di prova, un dispositivo) | **due passate.** La risoluzione **non è ancora decisa**: a metà la striscia in alto e in basso costa +1,0 ms di GPU sul tablet (+0,75 in formato telefono), a piena +3,5 (+2,2); con la regola di misura corretta **stanno nel fotogramma tutte e due** (a piena: GPU al p90 5,2 ms, `over_pct` 0,6 / 0,1 / 0,0 contro 0,5 / 0,1 / 0,1 del pavimento). Nessuno ha ancora guardato la striscia a metà risoluzione. Siccome la resa viene prima del costo (sezione 2), **F2.4 parte dalla piena e sceglie a occhio** fra piena e metà, su tavole del banco e sul tablet; la metà resta la leva di F7 e il ripiego se un dispositivo non regge | la scelta a occhio in F2 (sottocategoria F2-b); S25 in S5. Il primo fotogramma lento della striscia era compilazione a freddo (S1-bis): il suo programma entra nel riscaldamento |
| C5 sonda | **male in S1, chiuso bene in S1-bis** | `toImageBitmap()` **non è la via**: ferma il filo principale circa 5 ms a lettura (4 sonde a 100 ms: 10% di fotogrammi persi), e chiamata da un altro filo fa cadere HWUI. **Via adottata: `HardwareRenderer` + `ImageReader`, un lettore per finestra** con una casella per sonda; miniature registrate sul principale, richiesta dopo il disegno della finestra, statistiche su un `HandlerThread`. Misurato (S1-bis), 4 sonde ogni 100 ms: con il lettore unico `over_pct` 0,3 / 0,1 / 0,1, con quattro lettori distinti 0,6 / 0,1 / 0,1, contro 0,4–0,8 / 0,1 / 0,1 del pavimento; pixel identici a `toImageBitmap()` su 2 575 confronti; un cambio chiaro ↔ scuro visto 92 volte su 92 in 3,2 ms dalla richiesta; nessun crash in 47 corse. Il periodo di 100–150 ms del piano regge. Ripiego, se su un altro dispositivo non reggesse: una lettura ogni 250 ms per finestra con `toImageBitmap()` | è provato il meccanismo, non la sonda dell'engine: fondale sintetico, sola luminanza media, cadenza fissa. In F2.1 la prima prova sul dispositivo è su una pagina vera (liste che si ri-registrano, più sorgenti, `exports`), e il caso in cui il lettore si prende un ridisegno che spettava alla finestra va costruito apposta o escluso per costruzione; S25 e pipeline Vulkan in S5; schermo spento e rotazione in S3; varianza, colore medio e isteresi in F2.1 |
| C6 prima compilazione | **a metà** | niente riscaldamento nel percorso dell'avvio: i cinque programmi {`body1`, `body8`, `light1`, `light8`, striscia} costano a freddo 139–141 ms di RenderThread (due corse), a caldo 26–30 ms in tutto. **Riscaldamento dopo il primo fotogramma, per famiglia, a filo fermo, striscia compresa.** `light8` e la striscia non erano lenti: HWUI scrive la cache dei programmi su disco 4 s dopo una compilazione, e la misura di S1 fermava il processo prima (con 12 s di attesa: 29 → 5,7 ms e 45 → 8,5 ms). Conseguenza per l'engine: un'app chiusa entro 4 s dal primo vetro ricompila al lancio seguente; non c'è rimedio dal lato nostro, va saputo | a caldo il primo fotogramma di una variante costa ancora 12–19 ms sul filo principale: quanto è della scena di misura e quanto dell'engine si separa in S2 (F1A.7 scalda anche il percorso del principale: un disegno fuori schermo per programma) |

**Regola di misura, corretta da S1.** Sul tablet la scadenza di un fotogramma è 16,67 ms (due vsync a 120 Hz): un
`total_ms` di 10–12 ms non perde fotogrammi. «Sta nel fotogramma» d'ora in poi vuol dire: **`over_pct` non sopra il
pavimento della stessa scena**, e p90 di `ui + render` e di `gpu` ciascuno sotto il periodo; il p90 di `total_ms` si riporta
ma non decide. `over_pct` confronta ogni fotogramma con la **sua** scadenza, che per un fotogramma partito tardi si
allunga: per questo accanto a `over_pct` si riporta sempre il **numero di fotogrammi della finestra** contro quello atteso
(a 120 Hz, 955–960 in 8 s): una finestra con meno fotogrammi ne ha persi, qualunque cosa dica `over_pct`. Le scene a
fondale fermo si misurano con `warmup 5`; dove una misura è stata fatta con `warmup 2` valgono le finestre 2 e 3.

**Difetti del motore di oggi visti a `Full`** (S1, sezione C2), con chi li chiude:

| difetto | dove | chi lo chiude |
|---|---|---|
| contorno della forma di prima dopo A → B → A, e la forma perde la sua ombra | `Full` e `Balanced`, dalla 3.0.0 | **chiuso**: commit `72be580` e `b56d158`; provato sul tablet il 2026-11-11 su cinque andate e ritorni, tema chiaro, `Full`: nel palco nessun pixel oltre 2 livelli su 255 fra la forma prima e dopo (in una coppia 32 pixel differiscono di 1–2 livelli; oltre soglia cambia solo la barra di stato), contro 83 000–140 000 prima. Tema scuro, `Balanced` e il ritorno predittivo annullato: nella prossima sessione sul tablet |
| doppio bordo dentro il ponte fra due pezzi, e moiré nel collo | solo `Full` | F1B.1 (nuclei nuovi: un campo solo, un filo solo); il ponte resta solo in viaggio (scelta di Alessio, «Scelte di gusto») |
| il collo fra due pezzi ha l'orlo molle e non ha filo né ombra propri: netto sui cerchi, sfumato nel punto stretto | solo `Full` | F1B.1; prova in F1B.6: orlo di larghezza costante lungo il collo, misurato sul banco |
| l'ombra a riposo compare in un fotogramma circa 270 ms dopo l'arrivo | `Full` e `Balanced` | F1B.6 (parità viaggio→riposo), voce di S2 |
| nel viaggio del menu i divisori sporgono dal bordo destro del vetro di 10–15 px e le voci sono tagliate in volo (`S1/collage/full-balanced/menu-ordina-meta-viaggio-1a1.jpg`) | tutti e due | F5A: il contenuto del menu sta dentro la sagoma in viaggio, deformato dalla lente ma intero (3.4, `GlassContentLens`); prova sul banco del movimento |
| **ogni push ha un primo fotogramma lungo**: 65–73 ms di misura/layout a caldo (89–101 ms in tutto, un buco di 83–92 ms sullo schermo), 88–111 ms al primo push di un processo; il pop no. Dal codice la pagina che entra si compone dentro la misura (`ui/fluid/FluidScreen.kt:683`, `:952`): riguarda ogni pagina aperta con `FluidScreen`, non solo la barra | tutti e due, da prima della 3.0 | **F4A, pacchetto nuovo F4A.7** (la pagina che entra si compone prima che il viaggio parta, o il viaggio parte dal fotogramma dopo): prima un profilo (Perfetto) in S2, perché la causa viene dalla lettura del codice e non da una misura |
| il menu parte circa 30 ms dopo il click lungo: due fotogrammi con la superficie ferma alla taglia premuta e due fotogrammi lunghi (34 e 25 ms) prima che il vetro si allarghi | tutti e due | F5A (l'apertura non deve aspettare la composizione del pannello); `framestats` in S2 |
| alla fine del cambio di pagina, quando la superficie condivisa riconsegna il vetro ai tasti, un fotogramma **senza vetro** (restano le icone) | tutti e due | F4A (passaggio di consegne riposo↔palco, 3.7), prova in F1B.6 |

**Convalida del banco del movimento (F0-a): parziale.** Tre scene che descrivono esattamente ciò che S1 ha registrato
(`oggi-*-galleria`, 120 Hz, densità 2,125), rese e misurate contro i fotogrammi veri
(`artifacts/liquid-glass/moto/f0/convalida-F0-a.md`; il confronto dei contorni, fatto con lo strumento del banco, è in
`convalida-F0-a-contorni.md`). Il criterio era «stessa sagoma, stessi istanti entro un fotogramma»: **non è rispettato per
intero**. Viaggio per viaggio:

| viaggio | cosa è convalidato | cosa no |
|---|---|---|
| menu | il riquadro della sagoma alla presa, al picco (554 × 464 px nei due lati) e a riposo (entro 1 px); la fase premuta entro un fotogramma | gli istanti del viaggio: il vero è in ritardo di **24–40 ms** (mediana 28), perché dopo il click lungo il tablet tiene ferma la superficie per due fotogrammi lunghi; tolta la mediana restano fino a 12 ms a metà viaggio |
| tasto | crescita, picco (+0,4 ms) e presa (entro 2 px, a parte ombra e bagliore che il banco non rende) | il ritorno: nel vero comincia 16,5 ms prima e si posa 25 ms prima; causa non chiusa (il tempo vero fra contatto e rilascio non è registrato) |
| barra fra due pagine | lo stato a riposo prima e dopo | **il cambio di forma: non convalidato.** Il tablet non mostra i primi 150 ms del push, e nell'unico fotogramma di mezzo il vero ha pezzi a piena taglia e velati dove la traccia li ha al 67% e opachi |

Quindi: il banco si usa per **sagoma, ordine degli eventi e durata del viaggio una volta partito**, e la sua fedeltà è
provata per il menu e per la presa del tasto, non per la barra. La puntualità della partenza e i fotogrammi lunghi si
giudicano solo sul dispositivo. **Condizioni prima di approvare un viaggio sui collage del banco** (cioè prima della
review di F1B): il `pair` del banco legge i tempi veri di una registrazione a cadenza variabile e confronta i contorni
(pacchetto F0.4c); il ritorno del tasto si rimisura con l'ingresso registrato (`getevent -lt`); la barra si riprende con
le animazioni rallentate, perché il cambio di forma arrivi allo schermo. Le due riprese sono della prossima sessione sul
tablet.

**Risultati osservabili.** `glassbench.py check` passa su tutte le sorgenti e fallisce se si reintroduce `flat` (prova:
un test del banco con una sorgente sbagliata apposta); il lint JVM fallisce sullo stesso caso; `FluidPhysicsTierTest` copre i
verdetti per sorgente; tre video e tre collage «di oggi» accanto ai collage Apple; la pagina «Misure» installata; il rapporto
S1 con una tabella di tempi per ogni misura e i sei cancelli decisi; `gen_fluid_icons.py --check` verde e una tavola del
set accanto alle icone di oggi e a quelle di Apple (`rif/icone/confronto_barra.png` è il precedente di R6).

**Come si verifica.** Banco: `check`, `compare` sulle `golden/classic/` appena create (differenza zero), `measure` sulle
scene `rif/` con il vetro di oggi. Movimento: `pair` di `oggi-menu-apertura` con
`rif/collage/B1_menu_telefono_apertura_f9143-9166.jpg` e `B1_menu_foto_f13036-13075.jpg`; `oggi-barra-pagine` con
`B2_barra_render_4m48_f8632-8659.jpg`; `oggi-tasto` con `B4_tocco_tasto_f6940-7018_ogni2.jpg`. Convalida del banco: il
collage di `oggi-menu-apertura` accanto ai fotogrammi veri di S1 (stessa sagoma, stessi istanti entro un fotogramma).
Backlog del dispositivo: è S1 stessa.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F0-a | il banco del movimento riproduce un viaggio vero: sagoma e istanti del collage contro la registrazione di S1 | 8,5 |
| F0-b | il controllo degli shader ferma davvero un errore (provato con un errore messo apposta, sul PC e sul dispositivo) | 9 |
| F0-c | le misure di S1 sono ripetibili (scena fissa, tre ripetizioni, dispersione dichiarata) e bastano a chiudere i sei cancelli | 8,5 |
| F0-d | il vetro `Classic` non è cambiato (salvo `radiusAt`, dichiarato) | 9 |

A1, B1, B2, C2, D1 sono NA in F0.

**Rischi e ripieghi.** Il banco non riproduce il viaggio vero entro un fotogramma (la goccia dipende dal passo di
integrazione e dal tempo reale): si convalida su viaggi senza goccia e la goccia si giudica solo sul dispositivo, dichiarato
in ogni fase che la tocca. RAM del PC satura durante `assembleDebug`: un pacchetto «G» alla volta, niente emulatore. Il
livello `Full` mostra difetti che rendono inguardabili i viaggi: non si rattoppa il disegno vecchio oltre un giro; si
registra e si passa, perché F1B lo sostituisce.

**Scelte di gusto.** Una sola, dopo S1: i collage `Full` contro `Balanced` di menu, barra e goccia, con la domanda «cosa di
questo vuoi tenere?» (risposte a scelta: la goccia com'è; la goccia più composta; solo la sagoma, materiale da rifare).

**Risposta di Alessio (2026-11-11): la goccia più composta.** Parole sue: l'animazione del livello `Full` (il collo
liquido fra i pezzi) «mi piace leggermente di più come animazione, però deve separarsi completamente poi». Quindi: **il
collo esiste solo in viaggio; a riposo due pezzi distinti sono separati del tutto**, senza ponte né contorni residui.
Vincolo per F1B.1 (la fusione `pieceBlend` scende a zero entro l'arrivo, con la stessa curva del resto del viaggio, mai
di scatto) e prova in F1B.6: a riposo, fra due pezzi che non si toccano, il fondale è nudo al pixel. Due pezzi dichiarati
dall'app come un gruppo solo (F3, distanza di fusione) restano un'altra cosa: lì la fusione è una scelta di chi scrive.

---

### F1A · Il materiale a riposo

**Scopo.** Dare a una lastra ferma il corpo, la luce e l'ombra che la fanno sembrare un oggetto anche su un fondo piatto, e
metterlo sotto `glassSurface` e `glassControlSurface` senza toccare una firma. È la fase che decide l'aspetto di tutto il
resto. Il bersaglio è il **materiale di iOS 27** (decisione, sezione 2). **Entra**: nucleo a lastre con il raccordo
continuo, e specchi; shader del corpo e della luce; ombra a due maschere; ricetta e profilo; innesto dietro
`LocalFluidGlassModel`; salute e riscaldamento; selezione del percorso e conta dei nodi; compatibilità e `engine-wear`;
pagina della galleria; tavole di gusto. **Resta fuori**: Fluid-physics, goccia, puck (F1B); adattività (in F1A lato e velo
vengono dal tema: F2); flessione (il tasto cresce ancora per `layerBlock`: F6); la fascia `FadeDown`, che resta `Classic`
fino a F2.5; il clip hardware resta acceso ovunque.

**Progetto tecnico.**

- *Nucleo.* `GlassFieldKernel.kt`: il campo a N lastre è quello di `SlabGroupRefractionShaderString`
  (`fp/FluidPhysicsShaders.kt:100`): smooth minimum polinomiale, gradiente mescolato con lo stesso `h` (esatto nella zona di
  fusione: R3 cap. 2). La variante a 1 pezzo non ha ciclo. `radiusAt` è quello corretto, con la coordinata centrata.
  Ogni pezzo porta il proprio **raccordo** (`pieceSmooth`): il profilo dell'angolo passa dal cerchio alla superellisse di
  3.7. Per poterlo provare su JVM, i punti di controllo della curva dell'engine escono dall'emettitore di
  `ui/fluid/FluidShape.kt` in una funzione pura (nessun cambio di comportamento) e `ContinuousCornerShape` rende leggibile
  il proprio `smoothing`. Lo stesso pacchetto crea, vuoti, i blocchi di luce e d'ombra che il corpo include
  (`GlassLightBlock`, il blocco d'ombra), con le loro firme: così F1A.2, F1A.3 e F1A.4 lavorano insieme su file diversi e
  il corpo compila dal primo giorno; il cancello `lightInBody` lo scrive F1A.2 contro quelle firme.
- *Corpo.* Dentro la banda `−sd < thickness` lo spostamento `circleMap` di oggi lungo la normale; cupola come peso;
  dispersione a tre campioni. Poi il **velo** (`veilColor` con alfa di ruolo), l'**ombreggiatura del corpo** (bordi alto e
  basso più chiari del centro) e la tinta. La maschera è l'alfa di copertura con `aa`.
- *Luce.* **Filo scuro**: banda di larghezza `rimWidth` sul contorno, uniforme su tutto il perimetro, di colore e forza
  `rimDark` (zero sul lato scuro). **Riflesso**: subito dentro il filo, due lobi centrati su `reflexDir` e sul suo opposto
  (di serie in alto e in basso), larghi `reflexWidths`, che rientrano verso il corpo su `reflexDepth` e lasciano fuori dai
  lobi `reflexFloor`. Con `rimDark = 0`, lobi ruotati in diagonale e più stretti, gli stessi uniform danno il bordo di
  iOS 26 (due archi di luce): è una variante di profilo, non un altro shader. **Bagliore**: campana attorno a `glowCenter`
  (in F1A è quello di `glassButtonGlowRecipe`, `ui/glass/interaction/GlassInteraction.kt:404`, portato nello shader).
- *Ombra.* Due maschere dal contorno della forma ferma: contatto (stretta, poco scostata) e ambiente (larga, scostata in
  basso), scavate sotto la sagoma, in un layer `Offscreen` solo, con il tetto di 1024 px. Le maschere sono **sola alfa**;
  colore e alfa li applica il layer in disegno (3.5). Con l'ombra corta di iOS 27 la componente d'ambiente pesa poco: resta
  perché serve ai grandi e alla variante «iOS 26» delle tavole.
- *Ricetta.* `glassRecipe` applica la regola della taglia di oggi (`glassSizedOptics`, guadagno smoothstep fra 64 e 320 dp:
  `ui/fluid/GlassMaterial.kt:1665-1796`) anche a ombra e smerigliatura, **non** al filo.
- *Innesto.* `glassSurface`: ramo `Liquid` che passa a `drawBackdrop` `highlight = null`, `shadow = null`,
  `innerShadow = null`, effetti = `glassBody`, `onDrawSurface` = il solo filetto di `GlassEdge`, `onDrawLight` = luce; il
  nodo dell'ombra nuovo davanti. `intensity`, `opticalDepth`, qualità, `edge` e `falloff` come nella tabella di 3.3
  (`FadeDown` resta `Classic`). In `bd/DrawBackdropModifier.kt` le modifiche sono minime e marcate «Fluid Engine addition»
  come le altre: una è certa, il parametro `onDrawLight` disegnato fuori dall'export (3.3); l'altra, l'aggancio del
  margine, solo se serve. La forma → piano è quella di 3.7.

**Bersagli.** Il materiale è quello di iOS 27: valgono le misure di R5 dove R5 misura, quelle di R4 dove R5 dichiara
invariato, e la taratura a occhio dove non misura nessuno dei due. Scena per scena, i numeri si confrontano con `measure`.

| grandezza | bersaglio | fonte, fiducia |
|---|---|---|
| spessore del filo | 1–2% dell'altezza di una barra, **assoluto**, uguale su tasto, capsula e menu | R4 A1, alta; invariato e rimisurato in R5 (il filo scuro ha lo stesso spessore) |
| filo scuro | su fondo chiaro, **tutto il perimetro**: −20…−30% di luminanza (−45…−80 livelli al pixel); sul tondo uguale su tutti i lati | R5 M1, M4, alta |
| riflesso | chiaro, **interno**, in alto e in basso: +10…+16 sul corpo, torna al corpo in ≈0,08 H | R5 M1, M4, media (misurato su capsule; su un tondo su bianco non si distingue) |
| riflesso sopra un fondo scuro (vetro chiaro) | +26…+27 sul corpo | R5 M3, media (un'inquadratura) |
| corpo su pagina chiara | +8 su 235; +2…+5 su grigio chiaro 238 | R5 M1, M4, alta |
| corpo su bianco puro | 4–11 livelli **sotto** il bianco (243–249 su 253): il tasto è appena grigio, non più indistinguibile | R5 M4, alta |
| velo su colore saturo | ≈0,5 | R5 M1, alta (±0,04) |
| corpo chiaro su fondo scuro | 141–146 su luma 51 | R5 M3, media |
| gradiente del corpo | più scuro verso i bordi (tab bar disegnata); tondo su bianco 243 in alto e 249 in basso | R5 M4, media. Il gradiente diagonale del tondo di iOS 26 (R4 A2: 216 → 200) non è rimisurato: **a occhio** |
| ombra su fondo piatto | 2–4% al bordo, nulla a 0,2–0,25 H; sotto un tondo su bianco meno dell'1% entro 0,15 D | R5 M1, M4, alta |
| ombra dei grandi | in iOS 26 il menu ne aveva ≈7× la barra (R4 A3, media); in iOS 27 il menu non è misurato | **a occhio**, con confronto in chat |
| smerigliatura di una barra, contrasto | resta il 6–13% del contrasto di ciò che sta sotto | R5 M1: alta nel rapporto con iOS 26 (circa la metà), media nel valore assoluto |
| smerigliatura, nitidezza | i dettagli fini al 35–45% di quelli di iOS 26; in iOS 26 sotto la capsula restava il 10% della nitidezza del testo (gradiente medio 2,20 → 0,22: le parole si riconoscono come forme, le lettere no) | R5 M1, alta nel rapporto; R4 A5 |
| quanto si spande il colore del fondo | su ≈0,7–0,9 H dentro l'elemento | R5 M1, media |
| smerigliatura di menu e pannelli | non misurata in iOS 27 (in iOS 26: 13% su foto, R4 A5); non meno smerigliati di una barra | **a occhio** |
| raggio di sfocatura | non ricavabile né da R4 né da R5 | **a occhio**, sulle due misure qui sopra |
| lente | centro non distorto, piega nell'ultimo 10–15% di H (R4 A2, bassa); in iOS 27 «semmai più forte» (R5 M1) | **a occhio** |
| tema scuro | corpo 58–78 sopra un varco nero e 47–157 secondo il fondo; bordo alto +10…+25 sul corpo; nessun filo scuro visibile | R5 M3, bassa (una grafica in HD); in R4 un solo tasto. **A occhio**, con confronto in chat |
| cosa cresce con la taglia | smerigliatura e ombra sì, il filo no | R4 A4; invarianza dedotta in R5, non verificata |

Non sono più il bersaglio di serie, e restano come variante «iOS 26» delle tavole: i due archi di luce diagonali (R4 A1:
≈110° e ≈70°, +21…+53 sul corpo chiaro, e la capsula con tutti e quattro i lati accesi), il velo al 30–45%, l'ombra larga
(3,7% a 0,2 H, estesa 0,75 H). L'ombra «più forte sul testo» è adattività: in F1A si tara su fondo piatto, il resto in F2.

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G | modello |
|---|---|---|---|---|---|
| F1A.1 | Nucleo a lastre con il raccordo continuo, specchi, blocchi vuoti di luce e d'ombra | `gf/GlassFieldKernel.kt`, `gf/GlassFieldGeometry.kt`, `gf/GlassLightShader.kt` e `gf/GlassShadow.kt` (solo i blocchi vuoti, poi passano a F1A.3 e F1A.4), `ui/fluid/FluidShape.kt` (funzione pura dei punti dell'angolo, lettura di `smoothing`), `test/glass/field/GlassFieldGeometryTest.kt`, `test/glass/field/ContinuousCornerFieldTest.kt`, `tools/glassbench/scenes/f1a/prova-angolo-continuo.json` | F0 | — | Fable |
| F1A.2 | Shader del corpo, quattro varianti, con l'inclusione dei blocchi di luce e d'ombra dietro `lightInBody`, sul banco | `gf/GlassBodyShader.kt`, `tools/glassbench/scenes/f1a/prova-corpo-*.json` | F1A.1 | — | Fable |
| F1A.3 | Shader della luce (filo scuro, riflessi, bagliore), quattro varianti, sul banco | `gf/GlassLightShader.kt`, `tools/glassbench/scenes/f1a/prova-luce-*.json` | F1A.1 | — | Fable |
| F1A.4 | Ombra a due maschere in sola alfa, colore dal layer; blocco dell'ombra in viaggio | `gf/GlassShadow.kt`, `tools/glassbench/scenes/f1a/prova-ombra-*.json`, `test/glass/field/GlassShadowTest.kt` | F1A.1 | — | Fable |
| F1A.5 | Ricetta e profilo, tarati con `measure` contro R5 e R4; scene e tavole **generate dalla ricetta**; in coda un solo giro di test JVM che copre anche F1A.1 e F1A.4 e scrive le scene | `gf/GlassRecipe.kt`, `gf/GlassLiquidProfile.kt`, `test/glass/field/GlassRecipeTest.kt`, `test/glass/field/trace/GlassSceneExportTest.kt`, `tools/glassbench/scenes/f1a/` (le generate), `tools/glassbench/golden/liquid/` | F1A.2–4 | G | Fable |
| F1A.6 | Innesto: `glassSurface`, `glassControlSurface`, modello e sua risoluzione pura, `GlassTint.mode`, catena degli effetti, `glassDrawInputs`, `onDrawLight`, forma → piano | `ui/fluid/GlassMaterial.kt`, `ui/fluid/FluidGlassModel.kt`, `ui/fluid/FluidGlassControls.kt`, `gf/GlassFieldEffects.kt`, `gf/GlassFieldPlan.kt`, `gf/GlassShadow.kt` (i ritocchi che l'innesto chiede al nodo: F1A.4 è già chiuso), `bd/DrawBackdropModifier.kt`, `test/glass/field/GlassModelSelectionTest.kt` | F1A.5, cancello C1 | G | Fable |
| F1A.7 | Salute delle varianti, riscaldamento, contatori di diagnostica, livello simulato per le build di debug, selezione del percorso e conta dei nodi (4.4) | `gf/GlassFieldHealth.kt`, `bd/FluidGlassDiagnostics.kt`, `bd/Platform.kt` (le due funzioni ascoltano il livello simulato), `test/glass/field/ShaderSourceLintTest.kt`, `sample/src/testDebug/…/GlassPathSelectionTest.kt`, `sample/src/testDebug/…/GlassNodeCountTest.kt`, `sample/src/androidTest/…/GlassNodeCountInstrumentedTest.kt`, il test strumentato degli shader | F1A.6 | G | Opus |
| F1A.8 | Compatibilità e `engine-wear` | `engine-wear/…/theme/FluidWearTheme.kt`, `test/compat/GlassCompatSignaturesTest.kt`, test di `GlassDefaults` | F1A.6 | G | Opus |
| F1A.9 | Galleria «Materiale»: `Classic` accanto a `Liquid`, i sei fondi di 4.6, manopole di taratura, interruttori che forzano `Classic` e il livello simulato su tutta la galleria (servono a S2) | `sample/…/material/`, `sample/…/GalleryNav.kt` (una voce) | F1A.6 | G | Opus |
| F1A.10 | Schede: `ref/componenti/vetro.md` (sezione nuova), KDoc, rigenerazione del catalogo | `ref/componenti/vetro.md`, `ref/api/` | F1A.6; il catalogo si rigenera dopo F1A.11, che aggiunge API | — | Opus (scheda), Haiku (catalogo) |
| F1A.11 | Icone nei componenti: `FluidIcon`, `FluidMetrics` con le taglie di R6, i componenti dell'engine passano a `FluidIcons` (sostituzione meccanica in `engine-ui`, `engine-ai-ui`, `engine-richtext`), la galleria fa lo stesso con le sue (è la prima app che migra, e in S2 non devono convivere due pesi nella stessa barra), icona delle azioni a 26 dp e freccia indietro a 24, icona della capsula a 26, `LocalFluidIconSize` fornita dai tasti di vetro, inchiostro monocromo del tema al posto dell'accento, margine dei tasti dal bordo a 16 dp e distanza a 8 con il titolo agganciato che li segue (`CompactTitleInset` da 76 a 84 dp); pagina «Icone» della galleria | `ui/fluid/FluidIcon.kt`, `ui/fluid/FluidMetrics.kt`, `ui/fluid/FluidScreen.kt`, `ui/fluid/FluidFloatingCapsule.kt`, `ui/fluid/FluidGlassControls.kt` (un fornitore), ogni file dei tre moduli e della galleria che nomina `Icons.…` (solo quelle righe), `sample/…/icons/`, `sample/…/GalleryNav.kt`, `test/fluid/FluidChromeMotionTest.kt` (gli inset) | F1A.6–F1A.9 chiusi, F0.8 | G | Haiku (sostituzione), Opus (componente, taglie, inset) |

F1A.2, F1A.3 e F1A.4 girano insieme dopo F1A.1, tutti sul banco e senza Gradle, con scene di prova scritte a mano
(`prova-*`). È il tratto in cui si itera di più: decine di render, nessuna build. La luce si giudica sopra il corpo: finché
il corpo di F1A.2 non c'è, F1A.3 la rende sopra il corpo della sonda `sonda-via-a`, e la tavola combinata vera è di
F1A.5. Tavole e scene portano sul vetro le icone di `FluidIcons` a 26 dp, nere o bianche (R6): altrimenti il confronto con i
ritagli di Apple giudicherebbe anche le icone. F1A.11 gira **da solo e per ultimo**: tocca molti file per poche righe
ciascuno, e nessun altro pacchetto deve scrivere negli stessi moduli mentre lavora.

**Risultati osservabili.** Un tasto da 44 dp si stacca dalla **pagina vera dell'engine** (`#F2F2F7` in chiaro, `#0D0D0F`
in scuro) e dal bianco puro per corpo, filo e ombra, sul banco; le tabelle `measure` delle scene `rif/` e `rif27-*` accanto
ai numeri di R5 e R4; un pannello grande ha l'angolo continuo e non un arco di cerchio; gli shader e i numeri di `Classic`
identici al pixel (`golden/classic/`); la selezione del percorso verde a ogni livello di API; `engine-wear`,
`engine-ai-ui` e `sample` compilano senza una riga cambiata oltre al tema dell'orologio; la conta dei nodi di una lastra
ferma è 3, e 2 senza ombra; le azioni delle barre e il tasto indietro hanno icone del set, a 26 e 24 dp, monocrome, a 16 dp
dal bordo, e né i componenti dell'engine né la galleria nominano più un'icona Material.

**Come si verifica.** Banco fermo: le scene `rif27-*` e `pagina-*` per prime, poi le `rif/` di R4 tranne
`rif-bordo-scorrimento`; `measure` (contrasto **e** nitidezza per la smerigliatura) e `sheet` con
`rif/ios27/ritagli/M1_tabbar_ios26_sopra_ios27_sotto.jpg`, `T6_scheda_prominente_269_0519.jpg`,
`E1_bordo_scorrimento_alto_278.jpg`, `M3_tab_bar_scura_292_0632.jpg` e, per la variante «iOS 26» e per la lente,
`rif/ritagli/A1_bordo_di_luce.jpg`, `A3_ombra.jpg`, `A5_smerigliatura.jpg`, `A_lente_griglia_f1485_f1700.jpg`; i ritagli a
6× di bordo e angolo su ogni tavola; la scena dell'angolo continuo; le scene di oggi (`oggi-grigio`, `oggi-testo`,
`oggi-foto`, chiaro e scuro) rese con i due modelli. Movimento: `oggi-tasto` reso col materiale nuovo (pressione e
bagliore), `first` per i primi cinque fotogrammi. Test: `GlassRecipeTest`, `GlassFieldGeometryTest`,
`ContinuousCornerFieldTest`, `GlassModelSelectionTest`, `GlassPathSelectionTest`, `GlassNodeCountTest`,
`GlassCompatSignaturesTest`, tutti i test del vetro di oggi verdi senza modifiche (`GlassMaterialStateTest`,
`GlassSizeRuleTest`, `GlassPanelMaterialTest`, `GlassRimRecipeTest`…). Backlog del dispositivo: bande sul gradiente del
corpo; il filo da 2–3 px su un pannello vero, con il clip acceso; l'angolo continuo dei grandi; colori su P3; costo di una
lastra ferma (la luce si ridisegna a ogni fotogramma) e di una premuta; scatto della prima compilazione.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F1A-a | il tasto su fondo **piatto**: si legge come oggetto senza niente da rifrangere, giudicato sulle pagine vere dell'engine (`#F2F2F7`, `#0D0D0F`) e su bianco e nero puri, non sul grigio medio | 9 |
| F1A-b | il bordo è un filo, non una fascia né un alone: spessore assoluto e costante su tasto, capsula e menu; sul chiaro scuro e continuo su tutto il perimetro, con il riflesso chiaro interno in alto e in basso; sullo scuro il riflesso; nessun doppio contorno, nessuna seghettatura, e all'angolo dei grandi la curva continua dell'engine (se Alessio sceglie un'altra variante del bordo, vale la scelta scritta) | 9 |
| F1A-c | le misure del banco stanno negli intervalli di R5 (e di R4 dove R5 dichiara invariato) con fiducia alta, e fuori solo dove una scelta di gusto scritta lo dice | 8,5 |
| F1A-d | spessore con la taglia: tasto, capsula, menu, pannello sembrano lo stesso vetro in quattro spessori | 8,5 |
| F1A-e | `Classic` identico a prima e il percorso giusto a ogni livello di API (immagini `classic`, test del vetro di oggi, selezione del percorso; il disegno sotto Android 13 è NV: 4.4) | 9 |
| F1A-f | costo: non più nodi né più passate di oggi per lastra ferma | 7 |
| F1A-g | accessibilità: contrasto del testo sopra il velo nuovo non peggiore di oggi (`forSystemContrast` applicato una volta) | 7 |

**Rischi e ripieghi.** Il materiale «quasi chiaro» perde leggibilità del testo sopra (oggi la tiene una pellicola al
34–56%): il velo ha un pavimento per ruolo e il contrasto di sistema lo alza; se non basta su foto, l'inchiostro adattivo di
F2 è il rimedio vero e F1A lo dichiara come debito. Il filo di 2–3 px sfarfalla sugli angoli stretti: `aa` più largo solo
sul filo. La traduzione di `GlassOptics` scritte a mano dà un aspetto inatteso a un'app: il test di compatibilità rende le
ottiche di `engine-wear` e del Playground sui due modelli e le mette in tavola. C1 chiuso male: F1A.6 parte con il corpo a
due layer. Il raccordo a superellisse lascia mezzo pixel di scarto sui raggi più grandi: se si vede sul banco a 6× o in S2,
quelle superfici passano all'anello a riposo (3.7). Il filo scuro sopra il corpo più scuro di iOS 27 su una pagina
`#F2F2F7` rischia di leggersi come un contorno disegnato: è il punto della prima tavola, e la ragione per cui si giudica
sulle pagine vere.

**Scelte di gusto** (una tavola per scelta, a fine F1A.5, prima di F1A.6; sui sei fondi di 4.6, con i ritagli a 6× e il
confronto di Apple accanto):

| scelta | varianti |
|---|---|
| il bordo | iOS 27 (filo scuro sul perimetro e riflesso interno in alto e in basso) / iOS 26 (due archi di luce) / oggi (filetto scuro pieno sopra e sotto) |
| il velo | iOS 27 (+8 su pagina chiara, appena grigio su bianco) / iOS 26 (+15…+27) / oggi (pellicola) |
| l'ombra | iOS 27 (corta, 2–4%) / iOS 26 (larga) / oggi |
| la smerigliatura | iOS 27 (6–13% di contrasto) / iOS 26 (26%) / ancora più smerigliata |
| la lente (né R4 né R5 la misurano) | leggera / media / marcata, sulla griglia e sul testo |
| il tema scuro (quasi non misurato) | solo il riflesso / riflesso e un filo chiaro sottile / com'è oggi; velo scuro più chiaro o più profondo |
| i grandi (menu, pannelli: non misurati in iOS 27) | smerigliatura e ombra come una barra / un passo in più / due passi in più |
| tutto insieme | **una tavola finale** con la combinazione scelta, perché le tavole sopra variano una cosa alla volta e la combinazione altrimenti si vedrebbe per la prima volta in S2 |

---

### F1B · Un solo motore

**Scopo.** Far disegnare allo stesso materiale tutto ciò che oggi ha una ricetta sua: le superfici di Fluid-physics, la
goccia dei controlli, la goccia delle voci di menu, il puck della tab bar, il vetro dipinto. Alla fine un bordo solo e
un'ombra sola. **Entra**: nucleo ad anello; Fluid-physics sul materiale con 8 pezzi; luce in corpo in viaggio; ombra in
viaggio; goccia; puck e misure della tab bar (R6); vetro dipinto, scrim e notifica riallineati; test di parità e pagina
«Parità»; regole riscritte; S2. **Resta fuori**: ogni comportamento nuovo (i viaggi restano quelli di oggi, cambia cosa li
veste).

**Progetto tecnico.**

- `fp/FluidPhysicsEffects.kt`: `slabGroupMorphLens` e `polyMorphLens` diventano due chiamate a `glassBody` con il nucleo
  giusto; `PhysicsRenderPlan` cresce a 8 pezzi e i suoi array alimentano `GlassFieldPlan`. `PhysicsMaterialSource`
  (`fp/FluidPhysicsMaterial.kt`) calcola una `GlassRecipe` mescolata fra partenza e arrivo (`opticsFrom`, `tintFrom`) con la
  stessa aritmetica `a + (b − a)·f` di `lerpGlassOptics`: il patto del padding resta garantito dalla regola «la sfocatura non
  supera la copertura della lente» ai due capi.
- In viaggio `lightInBody = 1`: filo e ombra seguono la sagoma per pixel, anche sui ponti (oggi sui gruppi il bordo è una
  rampa verticale approssimata, `glassRimRamp`). A riposo (`restFormNow() != null`) lo strato della luce e le maschere; una
  sagoma libera ferma (`FluidForm.Poly`, che `restFormNow()` può restituire: `fp/FluidPhysicsState.kt:425-432`) tiene la luce
  in corpo anche da ferma (3.4).
- **Riposo e viaggio devono essere lo stesso vetro in due punti in più della luce.** L'ombra: a riposo due maschere sfumate,
  in viaggio il campo valutato nello shader; il blocco in viaggio prende gli stessi due profili di caduta delle maschere
  (stessa larghezza, stesso scostamento), e la parità si misura sul banco sopra il testo e sotto un menu, dove l'ombra pesa.
  Il filo: lo shader antialiasa la maschera con `aa`, e a riposo il clip hardware resta acceso; i due antialias si
  moltiplicano proprio dove vive un filo da 2–3 px, e in viaggio il clip si spegne. Il banco non ha il clip hardware: si
  guarda in S1 (i) e in S2, e se si vede il filo a riposo si disegna mezzo pixel dentro il clip.
- La **piega di luce sui ponti** (R3 cap. 2: l'asse mediano del campo fuso fa cresta sotto l'ombreggiatura del corpo) si
  affronta qui per la prima volta: profilo dello spessore attenuato in funzione di `h` dello smooth minimum.
- Goccia (`ui/glass/interaction/GlassDrop.kt:239`, e la copia inline in `ui/fluid/FluidGlassMenuPanel.kt:525`): la faccia
  piena → lente resta com'è nella geometria e nei tempi; la lente passa a `Body1` con una ricetta `drop`
  (`gf/GlassDropProfile.kt`), e le due copie diventano una.
- Puck e riga nascosta della tab bar (`ui/fluid/FluidFloatingTabBar.kt:2347`, `:2527`): solo il materiale
  (`gf/GlassPuckProfile.kt`); il file è vendored e si tocca con il minimo di righe, marcate `LOCAL CHANGE` come le altre.
  `gooey` (`ui/fluid/FluidGooey.kt:82`) resta fino a F4B.3.
- **Misure della tab bar** (sezione 2, R6): `OpenHeight` da 60 a 62 dp (`:3262`; `ContentInset` è la somma e sale da solo di
  2 dp, `:3287`); la barra fornisce `LocalFluidIconSize` = 28 dp alle sue schede; l'icona non scelta perde l'opacità 0,6
  (`:2153`, `:2194`) e resta a inchiostro pieno: la scelta la dicono pillola e accento; etichetta a 11 sp (`labelStyle()`,
  `:3382`, oggi `labelMedium` a 13 sp). Stesse misure in `FluidTabBar` (`ui/fluid/FluidTabBar.kt:392`, dove l'icona è un
  `ImageVector` e la taglia si impone direttamente). R6 le metteva in F4B.3; stanno qui perché S2 è la prima volta in cui
  Alessio giudica il materiale sul dispositivo, e la tab bar è il vetro che si guarda di più.
- `fluidStaticGlassSurface` (`ui/fluid/FluidGlassControls.kt:315-399`), che serve a fogli e avvisi in un'altra finestra dove
  non c'è niente da catturare: i gradienti dipinti si ritarano perché a colpo d'occhio sia lo stesso vetro di iOS 27 (velo,
  filo scuro sul perimetro e riflesso interno in alto e in basso sul chiaro, il solo riflesso sullo scuro, ombra corta), con
  i numeri in `gf/GlassPaintedProfile.kt` derivati dalla ricetta e non ricopiati.
- La notifica che scala un antenato del vetro (`scaleIn(0,97)`, `ui/fluid/FluidNotification.kt:629-655`, contro
  `ref/regole.md:394`): la comparsa passa alla geometria.

**Bersagli.** Quelli di F1A sugli stessi elementi resi da Fluid-physics (R5 per il materiale; il moto è di R4, che R5
dichiara invariato senza rimisurarlo), più: materiale in viaggio limpido e
rifrangente, smerigliatura e ombra piena negli ultimi 100–150 ms (B1, media); un solo superamento del 3–6% verso il lato
lontano dal tasto, nessuna seconda oscillazione (B1, alta) sui viaggi che esistono già; molla dell'apertura smorzamento
0,65–0,75 e rigidezza 500–800 (B1, **bassa**: ordine di grandezza). R5 B11 ha rimisurato solo la salita di un menu di
trabocco (larghezza al 93% a 100 ms, altezza +1,6% al picco): compatibile con B1. Il «punto di luce forte» sulla goccia
limpida fra 33 e 67 ms (R4 B1 b) è in F5A. Tab bar: 62 dp, icona 28 dp a inchiostro pieno, etichetta 11 sp (R6: Apple
62,6 pt, sagome 21–31 pt, 10 pt; alta sulle misure in punti di iOS 27, bassa sul modello del telefono).

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F1B.1 | Nucleo ad anello; Fluid-physics sul materiale; 8 pezzi; correzione dei ponti; difetti del moto emersi in S1 | `gf/GlassFieldKernel.kt` (anello), `gf/GlassBodyShader.kt` (tutto il file: variante ad anello e ritocchi alle lastre in viaggio), `gf/GlassFieldEffects.kt`, `fp/FluidPhysicsShaders.kt`, `fp/FluidPhysicsEffects.kt`, `fp/FluidPhysicsMaterial.kt`, `fp/FluidForm.kt`, `fp/FluidPhysicsState.kt`, `fp/FluidPhysicsGeometry.kt`, `fp/FluidPhysicsLiquid.kt` | F1A, cancello C2 | G | Fable |
| F1B.2 | Bordo e ombra di Fluid-physics → luce e ombra uniche; parità riposo↔viaggio dell'ombra | `fp/FluidPhysicsRim.kt`, `fp/FluidPhysicsShadow.kt`, `fp/FluidPhysicsSurface.kt`, `gf/GlassLightShader.kt` (il blocco sull'anello), `gf/GlassShadow.kt` (blocco in viaggio), `tools/glassbench/scenes/f1b/ombra-riposo-viaggio-*.json` | F1B.1 | G | Fable |
| F1B.3 | Goccia dei controlli e delle voci | `ui/glass/interaction/GlassDrop.kt`, `ui/fluid/FluidGlassMenuPanel.kt`, `gf/GlassDropProfile.kt` | F1A | G | Fable |
| F1B.4 | Tab bar: puck e riga nascosta; misure di R6 (62 dp, icona 28 dp a inchiostro pieno, etichetta 11 sp) | `ui/fluid/FluidFloatingTabBar.kt`, `ui/fluid/FluidTabBar.kt`, `gf/GlassPuckProfile.kt`, `test/fluid/FluidFloatingTabBarLayoutTest.kt` | F1A (F1A.11 per `LocalFluidIconSize`) | G | Opus |
| F1B.5 | Vetro dipinto, scrim, notifica | `ui/fluid/FluidGlassControls.kt` (solo `fluidStaticGlassSurface`), `ui/fluid/FluidSheet.kt`, `ui/fluid/FluidNotification.kt`, `gf/GlassPaintedProfile.kt` | F1A | G | Opus |
| F1B.6 | Parità (ingressi dei tre chiamanti: 4.4), pagina «Parità» della galleria, tracce rivestite, regole e schede | `test/glass/field/GlassRecipeParityTest.kt`, `test/fluidphysics/` (gli attesi di materiale), `test/glass/field/trace/`, `sample/…/parity/`, `sample/…/GalleryNav.kt`, `ref/regole.md`, `ref/componenti/fluid-physics-motore.md`, `ref/componenti/vetro.md` | F1B.1–5 | G | Opus |
| F1B.7 | **S2** e rapporto | `artifacts/liquid-glass/sessioni/S2.md`, `stato.md` | tutti | G, D | orchestratore + Opus |

F1B.1 → F1B.2 in fila (stesso motore); F1B.3, F1B.4, F1B.5 hanno perimetri disgiunti e possono scrivere insieme, compilando
uno alla volta: ognuno ha il **suo** file di profilo, perché `gf/GlassLiquidProfile.kt` non è nel perimetro di nessuno dei
tre (le ricette nuove lo leggono, non lo scrivono).

**Risultati osservabili.** Menu Expand, menu morph, barra fra pagine, popover: stessi viaggi, materiale nuovo, luce e ombra
che seguono la sagoma anche sui ponti; pomello dell'interruttore, del cursore, pillola del segmentato e puck con la stessa
luce dei tasti; la tab bar è alta 62 dp con icone da 28 dp tutte a inchiostro pieno ed etichette da 11 sp; `PhysicsRimRenderer`,
`DropRimMesh`, `PhysicsShadowLayers`, `fluidPhysicsShadow` e le quattro sorgenti vecchie non hanno più chiamanti sotto
`Liquid` (restano per `Balanced`/`Lite` dove servono, il resto si toglie in F8.5).

**Come si verifica.** Banco fermo: `fluidphysics-gruppo-oggi` reso con i due modelli; scene nuove `f1b/gruppo-2`,
`gruppo-4`, `gruppo-8`, `ponte-stretto`, `goccia-a-meta`, `pomello`, `puck`, `ombra-riposo-viaggio-testo` e `-menu` (la
stessa lastra con l'ombra a maschere e con l'ombra nello shader, differenza con `compare`), `tabbar-misure` accanto a
`rif/icone/confronto_tabbar.png`; `golden/liquid/` aggiornate. Movimento: le tre tracce di F0 rivestite, `pair` con gli
stessi collage Apple, `curve` per superamento e istanti; `first` e `last` su ogni traccia, più le tracce con interruzione
(4.3) per F1B-d.
Test: `GlassRecipeParityTest`; `RestStateDisciplineTest`, `DropPlanTest`, `LiquidDropTest`, `GlassDropSquashTest`,
`GlassDropFaceTest` verdi (la geometria non è cambiata); gli attesi di materiale di `FluidPhysicsMaterialTest` aggiornati e
motivati; `FluidFloatingTabBarLayoutTest` con le misure nuove. Backlog → S2: lo scalino viaggio→riposo della luce e
dell'ombra; il filo con il clip acceso e spento; la piega sui ponti dal vero; il costo dell'anello a `Full` su un menu
grande; la goccia (se F0-a ha detto che il banco non la riproduce); la pagina «Parità» fotografata.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F1B-a | una lastra ferma e la stessa lastra in viaggio sono lo stesso vetro: nessuno scalino di luce, d'ombra o di filo a partenza e arrivo | 9 |
| F1B-b | i ponti fra pezzi fusi: luce e ombra continue, nessuna piega, nessuna doppia linea | 9 |
| F1B-c | tasto, pomello, puck, menu, foglio dipinto messi in fila sembrano un materiale | 8,5 |
| F1B-d | i viaggi di oggi non hanno perso niente (tempi, interruzione, annullo a metà) | 9 |
| F1B-e | costo in viaggio: non più passate di oggi a `Full` (misurato in S2) | 7 |

**Rischi e ripieghi.** L'anello con luce e ombra in corpo non sta nel fotogramma su una finestra grande: luce in corpo solo
sulla fascia (c'è già l'uscita anticipata), ombra del viaggio ridotta alla sola componente di ambiente; se non basta, il
ripiego di 3.8. I test «bit per bit» della goccia confondono geometria e materiale: si separano gli attesi prima di toccare
il disegno, in un commit suo. La tab bar vendored diverge dall'upstream: le modifiche restano dentro le due funzioni che
disegnano e nelle costanti delle misure. La tab bar più alta di 2 dp e le etichette più piccole sono un cambio visibile
in ogni app: chi ha scritto a mano 76 dp di margine resta corto di 2 (va nel CHANGELOG, F8.3); se in S2 l'etichetta a 11 sp
si legge male in italiano, torna a 12 sp con una riga in `FluidMetrics`.

**Scelte di gusto.** Dopo S2, sui video dal tablet: il superamento della goccia (com'è / più composto / quasi nullo); la
conferma sul pannello vero delle scelte di F1A; con le icone nuove, se il vetro del tasto a 44 dp sembra ancora piccolo
(44 / 46: R6 lo lascia aperto, e 46 alza la riga dei controlli).

---

### F2 · Adattività e bordo dello scorrimento

**Scopo.** Il vetro sa cosa ha dietro, e la fascia in alto lascia il posto al bordo dello scorrimento. **Entra**: sonda,
schedulatore e filtro; velo, ombra e tinta adattivi; variante `Clear`; il cursore dell'utente fra trasparente e colorato;
giro chiaro/scuro dei piccoli con l'inchiostro; striscia del bordo morbido e duro **con l'aspetto di iOS 26** (decisione,
sezione 2), in alto e in basso; innesto in `FluidScreen`, `FluidCollapsingTopBar`, `FluidPageChrome`, barra ancorata;
deprecazioni. **Resta fuori**: l'adattamento uniforme di un contenitore (F3: fino ad allora ogni vetro radice gira per
conto suo); la luce del contenuto che si riversa sulle barre laterali (R1 M9: F4B.4); la fascia uniforme di iOS 27, che
non si fa.

**Progetto tecnico.**

- *Sonda* (3.9). `GlassProbeNode` (`gf/GlassProbe.kt`) è un nodo a sé accanto a `drawBackdrop`, con lo stesso `Backdrop`:
  non si aggancia a `refreshCapture`, perché una sorgente che si ri-registra non fa ricatturare nessuno
  (`bd/DrawBackdropModifier.kt:749-772`). La svegliano tre cose: la **revisione della sorgente** (un contatore su
  `LayerBackdrop` che `LayerBackdropNode` incrementa quando ri-registra il layer, `bd/backdrops/LayerBackdropModifier.kt:104-110`,
  con un elenco di ascoltatori), lo **spostamento relativo**, e una cadenza bassa e fissa per le sorgenti che non sono
  registrazioni. Nessuna chiede una cattura in più. L'avviso arriva durante il disegno della sorgente: lascia un segnale in
  un canale a un posto e basta; una coroutine del nodo chiede a `GlassProbeScheduler` (puro: cadenza massima 100–150 ms,
  silenzio durante un lancio veloce, niente letture senza eventi) se leggere, registra la miniatura solo se è cambiata la
  geometria, legge con `toImageBitmap()` (sospensiva: fuori dal disegno), e pubblica `GlassBackdropStats(luminanza,
  varianza, colore)` come stato di snapshot. Sospesa sotto una soglia di `FluidGlassQuality`.
- *La statistica separa testo e fondo piatto?* Prima di scrivere il nodo, una prova sul banco
  (`tools/glassbench/probe_stats.py`): la miniatura a 8×8 e a 16×16, calcolata come la calcolerà l'engine (stessa riduzione,
  stessa formula), sulle scene `rif-capsula-testo`, `rif-capsula-piatto`, su un menu alto 900 px sopra il testo e su una
  foto. Se la varianza non distingue il testo dal piatto (una miniatura che media bene lo cancella), l'ombra «più forte sul
  testo» prende un'altra misura (energia dei gradienti della miniatura a 16×16) o si riduce al ruolo, e lo si scrive.
- *Filtro* (`gf/GlassAdaptiveFilter.kt`, puro): media mobile esponenziale, due soglie distanti, permanenza minima, uscita
  come bersaglio animato. Punti di partenza da R3 cap. 3 (costante di tempo 150–250 ms; soglie attorno a 0,45 e 0,62;
  permanenza 300–500 ms): sono **ipotesi da tarare in S3**, non misure.
- *Giro* (R4 B6, alta): ritardo 130 ms, corpo in 300 ms, inchiostro in 500–700 ms passando per un fotogramma a contrasto
  zero. L'inchiostro è un `LocalContentColor` fornito dal vetro (`FluidGlassAppearance`), animato.
- *Inchiostro monocromo* (deciso: sezione 2). Da F1A.11 le icone delle azioni non sono più `primary`: leggono l'inchiostro
  dall'ambiente, ed è quello del tema. Qui diventa **adattivo**: il vetro fornisce `LocalContentColor` dal proprio lato,
  animato; l'accento resta all'azione principale; un `tint` scritto dall'app resta quello dell'app.
- *Cursore dell'utente* (R5 M2): `LocalFluidGlassPreference`, da 0 (ultra trasparente) a 1 (del tutto colorato), di serie
  0,5. Muove insieme diffusione e velo, e soprattutto la diffusione (R5: fra i due estremi il velo cambia poco). Una riga
  facoltativa in `fluidAppearanceSection` (`ui/fluid/FluidAppearance.kt:366`), come c'è già per `glassEnabled`: l'engine
  disegna il cursore e chiama `onGlassPreferenceChange`, il valore lo conserva l'app (nessun campo nuovo nelle
  impostazioni dell'engine).
- *Striscia* (3.10). Altezza e rampa dai numeri di R4 B7 in unità di H = altezza dei tasti della barra: è l'aspetto di
  iOS 26. Sopra ci stanno tasti col materiale di iOS 27: una coppia che Apple non ha mai mostrato, da giudicare a occhio.
- *Innesto.* `FluidTopBarGlass` (`ui/fluid/FluidScreen.kt:1361`) sotto `Liquid` diventa `FluidScrollEdge(edge = Top,
  exports = barGlass)`: resta l'export per i tasti e resta `LocalGlassBackdrop` combinato. `calculateGlassIntensity`
  (`:1153`) resta: decide quando la striscia c'è (appare «solo quando un elemento fissato si sovrappone al contenuto che
  scorre»: R1 E1). In basso nasce l'equivalente, guidato dalla presenza di una barra, di una capsula o della tab bar. La
  barra ancorata pesa la striscia fra le due pagine come oggi pesa la fascia (`ui/page/FluidPageAnchoredBar.kt:136-150`).

**Bersagli.**

| grandezza | bersaglio | scheda, fiducia |
|---|---|---|
| giro dei piccoli | ritardo 130 ms, corpo in 300 ms (quasi tutto nei primi 200), icona al 90% a 600 ms, un fotogramma a contrasto zero | R4 B6, alta; non rimisurato in R5 (invarianza dedotta) |
| soglia del giro | in iOS 26 il vetro piccolo gira quando lo scuro copre il 70–85% dell'elemento (R4 A6, media: clip sintetica, ±10 punti); in iOS 27 una barra chiara sopra una foto a luma 51 **non** gira (R5 M3, un'inquadratura) | **a occhio**, in S3, con confronto in chat |
| corpo del vetro scuro | iOS 26: 22 su nero puro, 45 su fondo 28–50 (R4 A6, alta); iOS 27: 58–78 sopra un varco nero (R5 M3, bassa: una grafica in HD) | **a occhio**: vale la scelta sul tema scuro fatta in F1A |
| i grandi | non girano: da 143 a 48 su nero in ≈1,5 s; il velo segue la quota di nero (61 / 89 / 111) | R4 A6, alta; non rimisurato |
| inchiostro | scuro 14–20 sul vetro chiaro, chiaro 236 sul vetro scuro | R4 A6, alta |
| ombra sopra il testo | in iOS 26 18% contro 3,7% su fondo piatto (×4,9), stessa forma e stesso scostamento (R4 A3, alta). In iOS 27 l'ombra su fondo piatto è 2–4% e **sul testo non è misurata** (R5: nessuna scena adatta) | **a occhio**: tavola «quanto si rinforza» |
| tinta | tonalità fissa; luminosità 100% su bianco, 58% su nero | R4 A7, alta; non rimisurata, API identica (R5) |
| tasto tinto: bordo e ombra | in iOS 26 il filo è più chiaro e meno saturo del corpo e l'alone prende il suo colore (R4 A7); in iOS 27 il filo dei tasti è scuro e il tasto tinto non è misurato | **a occhio**; l'ombra del colore del tasto è già possibile (colore dell'ombra dal filtro del layer, 3.5) |
| `Clear` | nessuna smerigliatura; oscuramento 35% sotto (HIG, R1 M2; documentazione invariata in R5) | R1 D; l'aspetto a occhio su R4 A5 |
| cursore dell'utente | di serie a metà; estremo colorato: resta ≈9% dell'intervallo di luma, nessuna forma, velo 0,57–0,71; estremo trasparente: ≈28%, il testo si legge piegato dalla lente, velo ≈0,5 | R5 M2, **bassa** (gli stati non hanno lo stesso fondo; Apple ha ritoccato l'estremo trasparente nella beta 5) |
| bordo morbido: contrasto del testo | pieno da 0,95 H sotto i tasti; 50% a 0,43 H; 20% al bordo basso dei tasti; 8% al centro; 3–5% sopra | R4 B7, alta (iOS 26, per decisione) |
| bordo morbido: velo verso il colore del fondo | 36% a 0,17 H, 22% a 0,4 H, 13% a 0,6 H, 6% a 0,85 H, 0 a 1,1 H | R4 B7, alta |
| su contenuto scuro | scurisce: ≈0,8 del valore al bordo basso dei tasti | R4 B7, **bassa** (indiretta). In iOS 27 la fascia schiarisce (R5 E1): non vale qui |
| bordo duro | fascia uniforme (contrasto 1%), taglio in meno di 0,2 H | R4 B7, media; non rimisurato su iOS 27 |
| in basso | in iOS 26 sfuma **senza sfocare** (R1 E1, osservato, fonte secondaria); in iOS 27 nessuna conferma: l'unico indizio dice «nessun effetto visibile» (R5 E1, bassa) | **a occhio** |
| stile `Automatic` | è il bordo morbido di iOS 26, **per decisione**. La fascia uniforme di iOS 27 (R5 E1, alta: dalla cima a 0,21 D sotto i tasti, taglio ≤0,025 D, velo 0,62–0,81, σ ≈ 0,13 D) non si fa | sezione 2 |
| tasto di vetro sopra la striscia | Apple lo mostra solo sopra la fascia di iOS 27 (corpo −13…−24 rispetto alla fascia, filo −88…−98: R5 M4, media); sopra il bordo morbido non esiste | **a occhio** |

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G | modello |
|---|---|---|---|---|---|
| F2.1 | Sonda, schedulatore e filtro; revisione delle sorgenti; prova della statistica sul banco | `gf/GlassProbe.kt`, `gf/GlassProbeScheduler.kt`, `gf/GlassAdaptiveFilter.kt`, `bd/backdrops/LayerBackdrop.kt` e `bd/backdrops/LayerBackdropModifier.kt` (contatore di revisione e ascoltatori, marcati «Fluid Engine addition»), `test/glass/field/GlassProbeSchedulerTest.kt`, `test/glass/field/GlassAdaptiveFilterTest.kt`, `sample/src/androidTest/…/GlassProbeInstrumentedTest.kt`, `tools/glassbench/probe_stats.py` | F1B, cancello C5 | G | Fable |
| F2.2 | Materiale adattivo: velo, ombra, tinta `Colored`, `Clear`, cursore dell'utente e sua riga nelle impostazioni d'aspetto; tipo `FluidGlass` | `gf/GlassRecipe.kt`, `gf/GlassLiquidProfile.kt`, `gf/GlassBodyShader.kt`, `ui/fluid/FluidGlass.kt`, `ui/fluid/FluidGlassAdaptive.kt`, `ui/fluid/FluidAppearance.kt`, `test/glass/field/GlassRecipeTest.kt` | F2.1 | G | Fable |
| F2.3 | Giro e inchiostro: lato per superficie, colore del contenuto adattivo (le icone lo leggono già da F1A.11) | `ui/fluid/GlassMaterial.kt` (`isDarkSurface` e consumatori), `ui/fluid/FluidGlassControls.kt`, `ui/fluid/FluidFloatingCapsule.kt`, `ui/glass/interaction/GlassInteraction.kt` | F2.2 | G | Fable |
| F2.4 | Striscia del bordo: shader, nodo, API | `gf/GlassScrollEdgeShader.kt`, `ui/fluid/FluidScrollEdge.kt`, `LICENSES/Haze.md`, `tools/glassbench/scenes/f2/bordo-*.json` | F1B, cancello C4 | G | Fable |
| F2.5 | Innesto nelle schermate e deprecazioni | `ui/fluid/FluidScreen.kt`, `ui/fluid/FluidCollapsingHeader.kt`, `ui/page/FluidPageAnchoredBar.kt` (barra ancorata e `FluidPageChrome`, che sta lì), `test/fluid/FluidChromeMotionTest.kt`, `test/fluid/FluidScrollPhysicsTest.kt` | F2.3, F2.4 | G | Fable |
| F2.6 | Galleria, tracce (`giro`, `bordo`), schede | `sample/…/adaptive/`, `sample/…/GalleryNav.kt`, `test/glass/field/trace/`, `ref/componenti/vetro.md`, `ref/componenti/schermata.md` | F2.5 | G | Opus |

F2.4 non dipende dall'adattività e parte insieme a F2.1: lavora sul banco e chiede Gradle solo alla fine, per compilare il
Kotlin nuovo (la regola di 6.0 vale anche per lui). `FluidScreen.kt` lo tocca solo F2.5. La pagina della galleria di F2.6
ha anche la prova «bianco → nero»: un vetro fermo sopra un fondo che cambia colore senza muoversi.

**Risultati osservabili.** Una capsula che passa da una foto chiara a una scura gira corpo e icone con ritardo e
dissolvenza, senza sfarfallare sullo scacchiera sintetico; lo stesso giro avviene sotto un vetro **fermo** quando è lo
sfondo a cambiare; un pannello grande scurisce senza girare; l'ombra si rinforza sul testo quanto scelto; un tasto tinto
resta del suo colore su bianco e su nero; il cursore porta il vetro da quasi limpido a lastra del colore del fondo; in
`FluidScreen` non c'è più una fascia di vetro: il
testo si scioglie nel colore del fondo sotto i tasti, in alto e in basso; sotto API 33 e sotto `Classic` c'è ancora la
fascia.

**Come si verifica.** Banco fermo: `rif-bianco-nero`, `rif-tinta`, `rif-capsula-testo` contro `rif-capsula-piatto`,
`rif-bordo-scorrimento` con `measure` (inviluppo del contrasto e velo ai cinque punti di R4 B7), `sheet` con
`rif/ritagli/A6_chiaro_scuro.jpg`, `A7_tinta.jpg`; la sonda di R3 `sonda-bordo-scorrimento` come controprova (maschera
d'alfa = immagine doppia). Movimento: traccia `giro` (una serie di luminanza che riproduce 6:52–6:53) con `curve` contro la
tabella di B6; traccia `bordo` (scorrimento a passi) contro B7. Non c'è un collage Apple dedicato a B6 (R4 lo dice): il
confronto è numerico più il ritaglio A6. Per il cursore: `rif/ios27/ritagli/M2_cursore_tre_stati.jpg` accanto ai tre stati
resi. `probe_stats.py` con la sua tabella (testo contro piatto). Test: `GlassProbeSchedulerTest` (una revisione senza alcuna
cattura produce una lettura; niente eventi, niente letture; cadenza massima), `GlassAdaptiveFilterTest` (gradino, rampa,
scacchiera, rumore attorno alla soglia), `FluidChromeMotionTest` e `FluidScrollPhysicsTest` adattati;
`GlassProbeInstrumentedTest` scritto qui e fatto girare in S3 (taglia e coordinate fisse, sfondo bianco → nero e ritorno).
Backlog → S3: la prova «bianco → nero» dal vero; sfarfallio su contenuto vero;
frequenza sostenibile con tre o quattro contenitori; costo della striscia doppia in un lancio; la tenuta a 100 ms su una
striscia così sottile.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F2-a | il giro chiaro/scuro: mai uno scatto, mai uno sfarfallio, mai un'icona illeggibile per più di un fotogramma | 9 |
| F2-b | il bordo dello scorrimento: nessuna immagine doppia, nessun disegno dei campioni, nessuna riga dove finisce | 9 |
| F2-c | tempi del giro e profilo del bordo contro B6 e B7 | 8,5 |
| F2-d | i grandi non girano: scuriscono in continuo; il lato segue lo sfondo anche quando il vetro è fermo e cambia solo ciò che ha dietro | 8,5 |
| F2-e | leggibilità di testo e icone sul vetro in ogni stato del giro (contrasto misurato agli estremi) | 7,5 |
| F2-f | costo: la sonda non gira quando niente cambia; la striscia sostituisce la fascia, non si somma | 7 |

**Rischi e ripieghi.** Sfarfallio che nessuna isteresi doma (R3 rischio 5): il giro vale solo per vetro fermo sopra contenuto
fermo, e durante lo scorrimento il lato resta quello dell'ultimo riposo. `toImageBitmap()` blocca o costa troppo (C5): il
ripiego scelto in F0. La striscia a due passate non regge 2560 px (C4): tre sfocature native incrociate. La statistica
della miniatura non separa testo e piatto: l'ombra adattiva si riduce al ruolo (lo dice la prova sul banco, prima del
codice). Una sorgente che non è una registrazione cambia senza avvisare: per quelle c'è solo la cadenza fissa, e il ritardo
massimo del giro è quella cadenza. Il filo scuro dei tasti sopra il contenuto dissolto del bordo morbido pesa troppo: sopra
la striscia `rimDark` si attenua con il velo locale, come variante da scegliere.

**Scelte di gusto.** Bordo morbido: profilo di R4 / più corto / più lungo; velo verso il colore del fondo (Apple) / più
neutro. Il tasto di iOS 27 sopra il bordo di iOS 26: filo pieno / attenuato. Giro: tempi di R4 / più svelto; soglia: restia
come in R5 M3 / pronta come in R4 A6. Ombra sopra il testo: ×4,9 come iOS 26 / a metà / nessun rinforzo. Tema scuro del
vetro che ha girato: come la scelta di F1A / più profondo. Il cursore: come si presentano i due estremi. Bordo in basso:
sfuma senza sfocare / nessun effetto.

---

### F3 · Piano flottante e geometria

**Scopo.** Il meccanismo unico da cui escono tutte le trasformazioni: contenitore, identità, spaziatura. Chi scrive l'app
dichiara cosa c'è; il piano decide chi si fonde, chi nasce da chi, chi compare. Più le forme concentriche, che servono alle
barre. **Entra**: logica del piano; `FluidGlassContainer` e i suoi modificatori; palco e passaggio di consegne; collo che
si strozza; comparsa per lente; contenuto che si sfoca e si incrocia; unione; adattamento uniforme; forme concentriche e
misure; S3. **Resta fuori**: barre, menu, fogli (F4–F5), che ne sono i clienti.

**Progetto tecnico.**

- *Registro.* Ogni `Modifier.fluidGlass` dentro un contenitore si iscrive come pezzo: riquadro, raggi, variante di vetro,
  `id`, `unionId`, transizione. È `FluidBarPiece` con più campi; `glassControlSurface` continua a iscriversi allo stesso
  registro (oggi lo fa per la barra ancorata, `ui/fluid/FluidGlassControls.kt:195-202`), e non più solo se è una capsula.
  Il registro dice anche, per ogni pezzo, a quale grappolo appartiene e qual è il riquadro del grappolo: F4A dà un `id` ai
  gruppi della barra, F5A chiede al registro il gruppo del tasto che apre il menu, e nessuno dei due scrive nel piano.
- *Ordine dei modificatori.* `fluidGlassId`, `fluidGlassUnion` e `fluidGlassTransition` valgono per il `fluidGlass` che li
  **segue** nella stessa catena; scritti dopo non hanno effetto, e in debug lo dice un avviso. È contratto: sta nel KDoc,
  nella scheda e in un test.
- *Adattamento uniforme.* Lato e inchiostro li decide il contenitore, con una sonda sola sul riquadro del grappolo più
  grande: i pezzi li ricevono e non sondano (3.9). È qui, e non in F2, che «una barra gira tutta insieme» diventa vero.
- *Quando qualcosa cambia.* Il contenuto è già al suo posto d'arrivo (invariante 23: non si stira mai). Il piano confronta
  lo stato di prima e quello di adesso: stesso `id` → stesso pezzo che si sposta e cambia forma; `id` nuovo entro `spacing`
  da un pezzo esistente → **nasce da lui** (`MatchedGeometry`: parte sovrapposto al vicino e se ne stacca); `id` nuovo più
  lontano → **comparsa per lente** (`Materialize`); pezzi senza `id` → accoppiati per lato e posizione come fa oggi
  `barPieceJourney`. Poi un viaggio di Fluid-physics guidato da fuori fra i due gruppi
  (`driveExternally`, `fp/FluidPhysicsState.kt:176`), sul palco.
- *Collo.* Fusione e divisione passano per un collo che si assottiglia mentre i lobi restano tondi. Il raggio di fusione
  efficace scala già col varco con segno (`effectiveBlendRadius`, `slabMinGap` in `fp/FluidPhysicsGeometry.kt`): si tara
  perché la rottura cada dove dice R4, e si portano le tre correzioni di Haze (R3 cap. 2): rifrazione attenuata sui ponti
  sottili, fusione attenuata nella sovrapposizione, ottica non calcolata dalla taglia del layer allargato.
- *Comparsa.* `materialize` 0→1 con un filo di scala nella geometria (la sagoma parte più larga) e il simbolo che si
  condensa; l'opacità del corpo non entra mai.
- *Contenuto.* Chi esce si sfoca e svanisce, chi entra arriva sfocato e si fa nitido, incrociandosi al massimo della
  sfocatura; il contenuto con la stessa chiave nei due stati non si tocca. Sfocatura con `RenderEffect` sul layer del
  contenuto (API 31+), dissolvenza semplice sotto.
- *Concentricità.* `FluidConcentricShape(minimum)`: raggio = raggio del contenitore − distanza dal suo bordo, mai sotto il
  minimo. Il contenitore si dichiara con `Modifier.fluidContainerShape` (un `ModifierLocal`); i modificatori dell'engine che
  ricevono la forma la risolvono al layout; `rememberFluidConcentricRadii(minimum)` dà gli stessi raggi a chi disegna da
  sé (iOS 27). Una forma concentrica risolta è una forma ad angoli tondi come le altre: il piano la riceve già risolta
  (3.7). `FluidMetrics` c'è da F1A.11 con le taglie di R6; qui si completa: 12 dp fra due gruppi, passo di 44 dp delle
  icone dentro un gruppo, margine della capsula dai bordi, bersaglio minimo 48 dp (l'engine è già sopra i 44 pt di Apple).

**Bersagli di R4.** R5 dichiara invariati contenitore, identità, unione e transizioni del vetro (documentazione invariata,
nessuna clip nuova): l'invarianza è dedotta, non rimisurata.

| grandezza | bersaglio | scheda, fiducia |
|---|---|---|
| fusione e divisione | collo che si strozza, lobi tondi, rottura ≈200 ms prima del riposo; prima un collo, un fotogramma dopo l'altro | B2 (d), media (clip rallentata: vale l'ordine delle fasi, non i tempi assoluti) |
| capsule che cambiano larghezza | in continuo; superamento finale ≤1,3%; rientro iniziale di ≈0,2 H prima di allargarsi | B2 (c), media |
| cambio di contesto, durata | 270–330 ms sul telefono; 430 ms per le icone | B2 (a, c), alta |
| icone | sfocate e incrociate in 200–330 ms; al massimo della sfocatura a 67–133 ms; chi resta nei due stati non si sfoca | B2, alta |
| comparsa | 430 ms; contorno 8–10% più largo all'inizio; simbolo da 2× a 1×; opacità del corpo ferma | B3, media |
| scomparsa | 470 ms: simbolo via in 130 ms, rifrazione a zero entro 400 ms, poi il contorno | B3, media |
| carattere del cambio di contesto | durante il viaggio il vetro **schiarisce** e il tondo si gonfia di ≈10% | B2, descritto nel contenuto della scheda senza una curva: **a occhio** (è il materiale limpido in viaggio di 3.6 più il rigonfiamento della geometria) |
| distanza di fusione di serie | **non documentata** (R1 C1: esempi Apple 40 e 20 pt) | scelta di gusto |

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F3.1 | Logica del piano, pura: registro, grappoli (con identità e riquadro), accoppiamento, classificazione, palco, `planeJourneyFrame` con interruzione | `gf/GlassPlane.kt`, `test/glass/field/GlassPlaneTest.kt` | F1B | G | Sol |
| F3.2 | Contenitore, modificatori e loro ordine, palco, passaggio di consegne, adattamento uniforme (una sonda per contenitore) | `ui/fluid/FluidGlassContainer.kt`, `ui/fluid/FluidGlass.kt` (il modificatore), `ui/fluid/FluidGlassControls.kt` (iscrizione al registro), `gf/GlassProbe.kt` (la sonda del contenitore) | F3.1, F2 | G | Fable |
| F3.3 | Movimento del campo: collo, correzioni dei ponti, comparsa | `gf/GlassBodyShader.kt`, `gf/GlassLightShader.kt`, `gf/GlassFieldKernel.kt`, `fp/FluidPhysicsGeometry.kt`, `tools/glassbench/scenes/f3/` | F1B, F3.1 (le tracce) | G | Fable |
| F3.4 | Contenuto che si sfoca e si incrocia | `ui/fluid/FluidGlassContentTransition.kt`, il suo test | F3.1 | G | Opus |
| F3.5 | Forme concentriche, `rememberFluidConcentricRadii`, misure dei gruppi in `FluidMetrics` | `ui/fluid/FluidConcentricShape.kt`, `ui/fluid/FluidShape.kt`, `ui/fluid/FluidMetrics.kt`, `test/fluid/FluidConcentricShapeTest.kt` | F1A.11 (il file delle misure) | G | Sol |
| F3.6 | Galleria «Piano flottante», tracce, scheda nuova | `sample/…/plane/`, `sample/…/GalleryNav.kt`, `test/glass/field/trace/`, `ref/componenti/piano-flottante.md` | F3.2–4 | G | Opus |
| F3.7 | **S3** e rapporto | `artifacts/liquid-glass/sessioni/S3.md`, `stato.md` | tutti | G, D | orchestratore + Opus |

F3.1 e F3.5 sono logica pura e possono partire durante F2. F3.3 lavora sul banco con le tracce di F3.1, e per questo ne
dipende.

**Risultati osservabili.** Nella galleria, con un `if` e nessuna animazione scritta: un tasto diventa due, due capsule si
fondono e si dividono, un tasto compare per lente, tre viste formano una sagoma sola; le icone si incrociano sfocandosi; a
riposo ogni grappolo ha la sua cattura e nei primi e negli ultimi cinque fotogrammi del viaggio non c'è un salto; una
barra di tre vetri sopra una foto che cambia gira tutta nello stesso fotogramma.

**Come si verifica.** Banco fermo: `f3/ponte-*` (collo a cinque larghezze), `f3/comparsa-*` (la sonda `sonda-comparsa` di R3
rifatta col materiale vero), `f3/unione`. Movimento: `uno-due` e `divisione` con `pair` contro
`rif/collage/B2_select_done_f9029-9076.jpg` e `B8_divisione_tasto_tinto_f30440-30500.jpg` (che R4 segnala inquadrato male:
vale solo per l'alone); `contesto-capsule` contro `B2_barra_contesto_16m02_f28855-28890.jpg` con `curve` sulle larghezze;
`comparsa` contro `B3_comparsa_play_f5397-5420.jpg` e `B3_comparsa_play_f5290-5400_ogni2.jpg`; `first` e `last` su tutte
(F3-b giudica i primi e gli ultimi cinque fotogrammi); `uno-due-interrotto` e `divisione-interrotta`, le stesse tracce con
un cambio di stato a metà (4.3), per F3-f; `comparsa-su-testo` e gli ultimi 150 ms di `uno-due` a 6× per l'immagine
doppia di `w`. I confronti con i collage Apple usano i fotogrammi presi dal video e allineati su `t0` (4.3), non la griglia
già montata. Test: `GlassPlaneTest`, `FluidConcentricShapeTest`, un test Robolectric in `sample/src/testDebug` che cambia lo
stato di un contenitore e controlla che i pezzi si accoppino come dichiarato e che l'ordine dei modificatori sia quello del
contratto. Backlog → S3: il passaggio riposo↔palco; la piega sui ponti; il costo di un palco con 4 e con 8 pezzi; la
comparsa su una foto vera; un viaggio sopra un contenuto che scorre (la cattura del palco si rinfresca ogni 110 ms).

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F3-a | il collo: si strozza e si rompe come un liquido, senza pieghe di luce né gradini | 9 |
| F3-b | passaggio di consegne riposo↔palco invisibile nei primi e negli ultimi cinque fotogrammi | 9 |
| F3-c | le icone si sfocano e si incrociano; quella comune resta nitida | 8,5 |
| F3-d | l'app scrive solo lo stato: nessuna animazione, nessun riquadro, nessun `id` oltre a quelli che dichiara | 9 |
| F3-e | il piano decide bene da solo: nasce dal vicino giusto, compare per lente quando è lontano, non incrocia i pezzi | 8,5 |
| F3-f | interruzione: uno stato che cambia a metà viaggio riparte da dove si trova (invariante 16) | 8,5 |
| F3-g | adattamento uniforme: i pezzi di un contenitore girano insieme, nello stesso fotogramma, con una sonda sola | 8,5 |

**Rischi e ripieghi.** Il passaggio di consegne si vede: palco permanente per i contenitori sottili (3.7). I pezzi stanno
in una lista pigra o cambiano taglia per il testo: il piano legge i riquadri al posizionamento e rimanda il viaggio di un
fotogramma se l'arrivo non è ancora misurato. Più di 8 pezzi: divisione del grappolo, dichiarata. L'accoppiamento senza `id`
sbaglia: la scheda dice di dare gli `id`, e la galleria mostra il caso. La mescola `w` è una dissolvenza, non una
sfocatura intermedia: durante la comparsa e negli ultimi 100–150 ms di un viaggio il testo sotto può apparire doppio per
qualche fotogramma; se si vede a 6×, `w` segue una curva più ripida (il tratto ambiguo dura due fotogrammi) o il raggio
della sfocatura scende insieme a `w`, pagando il costo solo in quel tratto. Il palco tiene la cattura e la rinfresca ogni
110 ms: sopra un contenuto che si muove la rifrazione andrebbe a scatti, quindi durante uno scorrimento il palco ricattura
a ogni fotogramma (lo fa già la lastra ferma) e il costo si misura in S3.

**Scelte di gusto.** Distanza di fusione di serie (stretta: si fondono solo quando si toccano / media / larga: fusi già a
riposo quando sono vicini). Quanto si strozza il collo prima di rompersi. Scala iniziale della comparsa (8–10% di R4 /
meno).

---

### F4A · Barre dichiarate in alto e fra pagine

**Scopo.** Le azioni di una barra smettono di essere composable liberi e diventano un modello: gruppi, spaziatori, azione
principale, badge, ruoli. Con il modello, un cambio nella stessa pagina e un cambio di pagina si trasformano da soli.
**Entra**: modello e risoluzione delle posizioni; DSL `toolbar`; resa della barra in alto sul piano; tasto indietro e Chiudi;
azione principale tinta; badge; titolo e sottotitolo; trabocco con priorità; il **tipo** della ricerca e il suo posto nella
barra; viaggio fra pagine per `id`; barra che si ritira; il push che si lascia interrompere senza salti (R1 V1). **Resta
fuori**: barra in basso, **comportamento** della ricerca, tab bar (F4B); menu dal gruppo (F5A).

**Progetto tecnico.**

- *Modello* (puro): lista di elementi con `id`, posizione semantica, ruolo, priorità. La risoluzione produce gruppi per lato
  secondo le regole di R1 B1: i tasti con icona condividono un vetro; un tasto di testo, l'azione principale, conferma e
  chiusura hanno il proprio; uno spaziatore fisso spezza un gruppo, uno flessibile spinge e separa; `sharedBackground =
  false` toglie il vetro; se non ci stanno, escono prima gli elementi a priorità più bassa (`Low`, poi `Automatic`, poi
  `High`: R5 B11, documentato), ciò che sta in `overflow { }` è sempre nel menu «Altro», e `PinnedTrailing` resta a destra
  finché la ricerca non è attiva. Il menu «Altro» è un menu normale.
- *Ricerca: chi possiede cosa.* Il tipo `FluidSearchSpec` e i suoi enum nascono **qui**, in F4A.1 (`ui/fluid/FluidSearch.kt`,
  solo dichiarazioni), perché la DSL di F4A.2 li nomina e deve compilare. F4A.2 aggiunge il parametro `search` a
  `FluidScreen` e `search(spec)` allo scope, e li porta tutti e due a un punto solo, `FluidToolbarSearchSlot`
  (`ui/fluid/toolbar/FluidToolbarSearch.kt`), che in F4A posa il `FluidSearchField` di oggi così com'è. F4B.2 riscrive
  **quel file** e il campo (posizioni, da tasto a campo, vicini, tastiera) senza riaprire `FluidScreen.kt` né la DSL.
- *Resa.* Ogni gruppo è un pezzo del piano (una capsula con dentro le icone) con un `id` suo, derivato dagli `id` dei suoi
  elementi, che il registro del piano espone (serve a F5A); le icone si disegnano con `FluidIcon` a 26 dp, il passo dentro
  il gruppo è di 44 dp e fra due gruppi ci sono 12 dp (`FluidMetrics`, R6). Ogni elemento ha la sua chiave di contenuto:
  quando il modello cambia, i gruppi cambiano larghezza in continuo e le icone si incrociano sfocandosi, senza che la pagina
  scriva altro. `FluidTopBarActions` (`ui/fluid/FluidScreen.kt:1454`) resta per lo slot `actions` di oggi; se la pagina
  passa `toolbar`, vince `toolbar`.
- *Fra pagine.* `FluidPageBarSpec` (`ui/page/FluidPageBar.kt:30`) porta anche il modello; `FluidBarJourney` accoppia per
  `id` e solo in mancanza per lato; cade il limite «solo capsule, al più 6 coppie» (`ui/page/FluidPageAnchoredBar.kt:428`).
- *Tasto indietro e Chiudi*: chevron e X in un tondo di vetro, gruppo a sé; l'azione di conferma è una spunta tinta, da
  sola, a destra.
- *Titolo.* Il titolo grande che scorre col contenuto e si aggancia c'è già (`FluidTitleMorphState`); si aggiunge il modo
  dichiarato e il sottotitolo sotto il titolo agganciato.
- *Barra che si ritira* (iOS 27, R5 V5). `FluidChromeController` c'è. `barMinimize = Automatic` ritira la barra **solo**
  quando la ricerca è in posizione principale, come in iOS 27; `OnScrollDown` e `OnScrollUp` la ritirano sempre, `Never`
  mai. Titolo e tasti non escono scivolando in blocco: traslano con lo scorrimento e intanto si sfocano e svaniscono (la
  stessa grammatica delle icone di F3.4); il campo di ricerca resta, si ferma sotto la barra di stato e passa dallo stile
  del contenuto al vetro. Di serie il contenuto risale nello spazio liberato (`barMinimizeAdjustsInsets`). In iOS 27 sopra
  il campo fissato si forma la fascia uniforme; qui c'è il bordo morbido di F2: a occhio.
- *Il push che si lascia interrompere* (R1 V1). Oggi un gesto indietro o un pop che arriva durante un push lo fa finire di
  colpo e riparte da zero: `beginDrag` nel ramo che non è una ripresa chiama `finishNow()` e mette `travelPx = 0f`
  (`ui/page/FluidPageHostState.kt:451-459`), e `beginPop` fa lo stesso (`:293-315`). Ma push e pop misurano la stessa corsa
  (`travelPx` va da `exitTravel()` a 0 nel push e da 0 a `exitTravel()` nel pop, sulla stessa pagina in cima): quando la
  pagina che sta entrando è quella che il gesto o il pop portano via, la corsa e la velocità si **tengono**, cambia solo il
  verso (`opening`), e le voci che `finishNow` teneva in vita restano vive fino al riposo. Dove apertura e chiusura non
  disegnano lo stesso fotogramma alla stessa corsa (`pageFrame` dipende da `opening`, `:237`), la ripresa parte dalla corsa
  di chiusura che dà la stessa traslazione; se una transizione non lo permette, finisce l'apertura come oggi, e lo si
  dichiara per nome.

**Bersagli.** Quelli di F3 sul cambio di contesto (B2 a, b, c), più:

| grandezza | bersaglio | fonte, fiducia |
|---|---|---|
| tre icone → una | la capsula si chiude da sinistra verso l'icona che resta fra 67 e 267 ms; cerchio formato a 267–333 ms | R4 B2 (b), alta |
| capsula che si allarga | il bordo sinistro rientra di 0,19 H prima di allargarsi; la larghezza supera il riposo dell'1,3% a 600–900 ms | R4 B2 (c), media |
| regole d'uso | al più tre gruppi, una sola azione principale a destra, simboli e testo mai nello stesso gruppo | R1 B1–B10, documentato |
| trabocco | tre livelli di priorità, «sempre nel menu», «fissato a destra»; composizione osservata su iPhone: indietro, un tondo, un gruppo di tre, il tasto «…» | R5 B11, documentato e osservato |
| menu di trabocco | un menu normale: larghezza al 93% a 100 ms, altezza al massimo a 100 ms con +1,6% | R5 B11, media (coda non misurata); compatibile con R4 B1 |
| barra che si ritira | titolo e tasti sfocano e svaniscono in 170–230 ms seguendo lo scorrimento; il campo resta e diventa vetro | R5 V5, media: lo scorrimento della clip è programmato, col dito i tempi seguono il gesto |
| barra che ritorna | il campo lascia il vetro a 200–270 ms; titolo dalla sfocatura al nitido in ≈300–400 ms; tutto a riposo in 600–800 ms | R5 V5, media |
| `OnScrollDown` senza ricerca | l'intera barra esce | R5 V5, osservato su iPad, non misurato: **a occhio** |
| titolo in linea | compare in ≈100–200 ms quando il titolo grande passa sotto la barra, sparisce in ≈200–270 ms | R5 E1, ±1 fotogramma |
| icone e tasti | icona 26 dp, indietro 24, vetro 44, margine 16 dp, 12 dp fra gruppi | R6 (sezione 2) |
| passo delle icone nel gruppo | 44 dp, cioè 1,0 H come in iOS 26; in iOS 27 una barra sola mostra 62 pt, con simboli larghi | R6: media per iOS 26, **bassa** per iOS 27 (una barra): scelta di gusto |
| altezze e raggi delle barre | **non documentati** (R1 G3); R6 dà il tasto (44,0 pt) e i margini, non il resto | a occhio, sulle proporzioni dei frame |
| push interrotto | nessun salto di posizione, velocità conservata | R1 V1, documentato («fluida e interrompibile»); nessuna misura |

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G | modello |
|---|---|---|---|---|---|
| F4A.1 | Modello della barra: elementi, risoluzione, raggruppamento, priorità e trabocco, differenze fra stati; **tipo e contratto della ricerca** (solo dichiarazioni) | `ui/fluid/toolbar/FluidToolbarModel.kt`, `ui/fluid/FluidSearch.kt`, `test/fluid/toolbar/FluidToolbarModelTest.kt` | F3.1 | G | Sol |
| F4A.2 | DSL e barra in alto: `toolbar` su `FluidScreen` e fratelli, ruoli, principale, badge, indietro, titolo; gruppi con `id` e misure di `FluidMetrics`; parametro `search` e `search(spec)` cablati su `FluidToolbarSearchSlot`, che posa il campo di oggi | `ui/fluid/toolbar/FluidToolbar.kt`, `ui/fluid/toolbar/FluidToolbarSearch.kt`, `ui/fluid/FluidScreen.kt`, `ui/fluid/FluidCollapsingHeader.kt` | F4A.1, F3 | G | Fable |
| F4A.3 | Fra pagine: modello nella barra dell'host, accoppiamento per `id` | `ui/page/FluidPageBar.kt`, `ui/page/FluidPageAnchoredBar.kt` (anche `FluidPageChrome`), `ui/page/FluidPageMotion.kt`, `test/page/` (tranne il file di F4A.6) | F4A.2 | G | Fable |
| F4A.4 | Barra che si ritira scorrendo: `barMinimize`, inset che seguono, campo di ricerca che resta e diventa vetro | `ui/fluid/FluidScreen.kt` (dopo F4A.2), `ui/fluid/toolbar/FluidToolbarSearch.kt` (dopo F4A.2: lo stato «fissato»), `test/fluid/FluidChromeMotionTest.kt` | F4A.2 | G | Opus |
| F4A.5 | Galleria «Barre», tracce, scheda | `sample/…/toolbar/`, `sample/…/GalleryNav.kt`, `test/glass/field/trace/`, `ref/componenti/barre.md`, `ref/componenti/schermata.md` | F4A.2–4 | G | Opus |
| F4A.6 | Push interrotto da gesto o pop: posizione e velocità conservate. Prima il test, che sul codice di oggi deve fallire; poi la correzione | `ui/page/FluidPageHostState.kt`, `test/page/FluidPageInterruptTest.kt`, `ref/componenti/pagine.md` (una riga) | — (non dipende dal materiale: può partire in qualunque momento prima di S4) | G | Sol |
| F4A.7 | Il primo fotogramma di un push (difetto misurato in S1-bis: 65–73 ms di misura/layout): profilo in S2, poi la pagina che entra si compone prima che il viaggio parta, senza cambiare l'API di `FluidScreen`; prova con `framestats` (nessun fotogramma oltre scadenza nei primi 100 ms di un push a caldo) | `ui/fluid/FluidScreen.kt`, `ui/page/FluidPageHost.kt` e i file che il profilo indica (da dichiarare prima di scrivere), `test/page/FluidPagePushFirstFrameTest.kt` | F4A.6, profilo di S2 | G, D | Fable |

**Risultati osservabili.** Una pagina che passa da «sfoglia» a «seleziona» cambia gruppi e icone senza un'animazione
scritta; due pagine con barre diverse si passano i tasti per identità; l'azione principale è una spunta tinta da sola a
destra; quando il posto non basta escono prima gli elementi a priorità bassa e finiscono nel menu «Altro»; una pagina con
la ricerca in posizione principale ritira titolo e tasti scorrendo e lascia il campo; una pagina che dichiara `search`
compila e mostra il campo di oggi; un gesto indietro partito a metà di un push riprende la pagina dove si trova; le pagine
che usano ancora `actions` sono identiche a prima (con le icone e i margini di F1A.11).

**Come si verifica.** Movimento: `barra-tre-stati` con `pair` contro `rif/collage/B2_barra_render_4m48_f8632-8659.jpg` e
`B2_barra_telefono_f8536-8570.jpg`; `barra-contesto` contro `B2_barra_contesto_16m02_f28855-28890.jpg` con `curve` sulle
larghezze delle capsule (lo stesso metodo di `rif/tools/bar.py`); `barra-fra-pagine`; `barra-si-ritira` e `barra-ritorna`
contro `rif/ios27/collage/V5_ritiro_denso_278_f20258-20293.jpg` e `V5_ritorno_denso_278_f20352-20410_ogni2.jpg`, con
`curve` su sfocatura e opacità del titolo; `barra-contesto-interrotta` (4.3). Test: `FluidToolbarModelTest`
(raggruppamento, spaziatori, priorità, trabocco, fissato, differenze), `FluidPageMotionTest` e `FluidChromeTransitionTest`
adattati, `FluidPageInterruptTest` (a metà push un gesto e un pop: la posizione disegnata subito dopo è quella di subito
prima, la velocità ha lo stesso segno e modulo; rosso sul codice di oggi), un test Robolectric che cambia il modello e
controlla semantica e bersagli da 48 dp. Banco fermo: `f4a/barra-*` per le misure di gusto, accanto a
`rif/icone/confronto_barra.png` e a `rif/ios27/ritagli/B11_barra_e_trabocco_269.jpg`. Backlog → S4: i tre stati dal vero;
il cambio durante uno scorrimento; il tasto indietro fermo al pixel fra due pagine; il push interrotto registrato a
120 Hz; la barra che si ritira col dito.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F4A-a | la barra è un piano: i gruppi cambiano larghezza in continuo, niente compare o sparisce in un fotogramma | 9 |
| F4A-b | il sistema raggruppa come Apple a partire dalle sole posizioni e ruoli | 8,5 |
| F4A-c | le pagine con `actions` non sono cambiate | 9 |
| F4A-d | la DSL basta per le barre delle app di oggi senza ricorrere a `item { }` libero (verificato riscrivendo tre barre della galleria) | 8,5 |
| F4A-e | titolo, inset e bersagli restano giusti con ogni combinazione di elementi e con il testo ingrandito | 8 |
| F4A-f | un push interrotto dal gesto indietro o da un pop non salta: posizione e velocità continue | 9 |
| F4A-g | la barra che si ritira: titolo e tasti si sfocano seguendo il dito, il campo resta, gli inset seguono | 8,5 |

**Rischi e ripieghi.** La DSL non copre un caso reale (un avatar, un indicatore): `item(id) { }` libero resta, con vetro
proprio. Il titolo agganciato calcola gli inset sulle larghezze di inizio e fine riga (`FluidTopBarControls`): durante un
cambio di larghezza segue il bersaglio, non il fotogramma, per non far ballare il titolo. Il tipo della ricerca dichiarato
in F4A.1 risulta stretto quando F4B.2 lo usa: si allarga con campi in coda e valori di serie, come ogni tipo di
configurazione (3.2). La ripresa del push non è continua per una transizione (`Expand`): per quella resta il comportamento
di oggi, scritto nella scheda.

**Scelte di gusto.** Passo delle icone nel gruppo: 44 dp (iOS 26) / più largo (verso i 62 pt dell'unica barra di iOS 27);
distanza dello spaziatore fisso; l'azione principale: spunta tinta piena / tinta più leggera; il bordo morbido sopra il
campo fissato quando la barra è ritirata: com'è nelle altre pagine / più pieno.

---

### F4B · In basso: capsula, ricerca, tab bar

**Scopo.** Lo stesso modello in basso, la ricerca che si estende da tasto a campo e interagisce con i vicini, la tab bar
sul piano. **Entra**: barra in basso dichiarata; capsula che cambia contenuto animando; ricerca automatica, in basso,
minimizzata, in alto; tab bar: materiale, piano, ruoli, bolla di selezione, accessorio, scheda di ricerca, scheda
prominente; su tablet rail, barra laterale, estensione dello sfondo. **Resta fuori**: tastiera di sistema (è un'altra
finestra: R1 O1).

**Progetto tecnico.**

- La capsula flottante oggi ridispone la riga senza animazione e ricattura per la taglia nuova
  (`ui/fluid/FluidFloatingCapsule.kt:224`): diventa un contenitore del piano, e gli elementi con posizione `BottomBar` ci
  entrano da soli.
- La ricerca oggi anima la larghezza in un modificatore di layout e ricattura a ogni fotogramma
  (`ui/fluid/FluidSearchField.kt:214`, `:440`): la sagoma passa al campo (un pezzo che cambia larghezza sul palco, cattura
  tenuta), il contenuto resta al layout d'arrivo. Posizione `Automatic`: in basso sul telefono, in alto a destra sul
  tablet; con più di due elementi vicini parte come tasto; attiva sale sopra la tastiera e mostra il tasto di chiusura.
  Gli ambiti stanno in linea, sulla riga del campo (iOS 27, note di rilascio: R5). Il tipo c'è da F4A.1 e il cablaggio da
  F4A.2: questo pacchetto scrive il comportamento dentro `FluidToolbarSearchSlot` e il campo, e non riapre
  `FluidScreen.kt` né la DSL. Se serve un campo in più sul tipo, si aggiunge in coda a `FluidSearch.kt`, che da qui è suo.
- Tab bar (`ui/fluid/FluidFloatingTabBar.kt`, 3399 righe vendored): i tre stati `INLINE`/`EXPANDED`/`SEARCH_EXPANDED` ci
  sono, come l'accessorio e la ricerca. Si sostituisce la fusione `gooey` della piega con il campo (pannello, scheda isolata
  e accessorio come pezzi), si rifinisce la bolla di selezione trascinabile (R1 T2) e si aggiunge `FluidTabRole`. La
  scheda **prominente** (iOS 27) è il tondo a destra reso ruolo generico: una sola, una scheda qualsiasi; se manca, il
  trattamento va alla ricerca; resta visibile a barra ripiegata. Le misure di R6 (62 dp, icone, etichette) ci sono già
  da F1B.4.
- Tablet: `FluidAdaptiveNavigation` sceglie fra tab bar, `FluidTabRail` e `FluidSidebar` dalla larghezza; la luce del
  contenuto che si riversa sui grandi (R1 M9) è l'ombra e il velo che prendono il colore medio della sonda;
  `Modifier.fluidBackgroundExtension()`.
- **R1 T7, per esteso.** Su una finestra larga la tab bar sta **in alto**, flottante e centrata, e porta un tasto che la
  **converte in barra laterale**; la barra laterale ha il tasto che la riporta tab bar. La conversione è un viaggio del
  piano: la capsula della tab bar diventa il pannello laterale e le voci passano da una riga a una colonna incrociandosi
  (F3.4). Il rail verticale di oggi (`ui/fluid/FluidTabBar.kt:468-492`) resta per le larghezze di mezzo. In iOS 27 le barre
  laterali di iPad arrivano ai bordi della finestra e le loro icone tornano del colore d'accento (R5, detto da Apple, non
  misurato): **divergenza scelta**, la barra laterale dell'engine resta flottante e monocroma come il resto, e la riga
  T7 della copertura lo dice. La barra laterale su telefono (iOS 27: se l'app la sceglie) è `FluidSidebar` chiamata a mano:
  c'è.

**Bersagli.** R4 non ha misure dedicate a ricerca e tab bar: valgono quelle di F3 (larghezze continue, icone, superamento
≤1,3%) e l'inventario R1 T1–T7, R1–R5 come comportamento. R5 dichiara invariati bolla di selezione, minimizzazione,
accessorio e ricerca della tab bar (documentazione e HIG), salvo la scheda prominente.

| grandezza | bersaglio | fonte, fiducia |
|---|---|---|
| scheda prominente | tondo di diametro 1,00 H, a 0,11–0,12 H dalla capsula; solo glifo, senza etichetta; stesso corpo, filo e ombra della capsula; la capsula riempie lo spazio che il tondo lascia (5,23 H con tre schede su uno schermo largo 7,0 H) | R5 T6, alta |
| margini della tab bar | 0,30 H dai lati e ≈0,32 H dal fondo in iOS 27 (≈19 e 20 pt); l'engine tiene 14 dp ai lati e 16 dp sopra la barra di sistema | R5 T6, alta; R6: invariati (il fondo dipende dalla barra dei gesti di Android) |
| scheda prominente scelta, e il passaggio da e verso di lei | non compaiono in nessuna clip | **a occhio** |
| pillola della scheda scelta | più scura della pagina di 19 livelli (219 su 238), rientrata di 0,076 H, alta 0,85 H | R5 M4, alta (tema chiaro) |
| bolla di selezione trascinabile | descritta solo da recensioni, nessun numero (R1 T2) | **a occhio** |
| capsula in basso | azione da 48 dp con icona da 26 dp (già da F1A.11) | R6: Apple 47,8 pt |
| ricerca in basso, ricerca attiva, tastiera | nessuna misura in R4 né in R5; comportamento da R1 R1–R5 | **a occhio** |
| ambiti della ricerca | in linea, sulla riga del campo | R5, documentato (note di rilascio) |
| tab bar in alto sul tablet e conversione in barra laterale | comportamento di R1 T7; nessuna misura | **a occhio** |

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G | modello |
|---|---|---|---|---|---|
| F4B.1 | Barra in basso dichiarata e capsula sul piano | `ui/fluid/FluidFloatingCapsule.kt`, `ui/fluid/toolbar/FluidBottomBar.kt`, `test/fluid/FluidFloatingCapsuleTest.kt` | F4A | G | Fable |
| F4B.2 | Ricerca, il comportamento: posizioni, da tasto a campo, vicini, minimizzata, attiva sopra la tastiera, ambiti in linea, campo sul campo | `ui/fluid/toolbar/FluidToolbarSearch.kt`, `ui/fluid/FluidSearch.kt` (solo campi in coda), `ui/fluid/FluidSearchField.kt`, `test/fluid/FluidSearchFieldTest.kt`, `test/fluid/toolbar/FluidToolbarSearchTest.kt` | F4B.1, F4A.4 (che ha scritto per ultimo nello stesso file) | G | Fable |
| F4B.3 | Tab bar: campo al posto di `gooey`, bolla, ruoli, scheda prominente con le misure di R5 T6 | `ui/fluid/FluidFloatingTabBar.kt`, `ui/fluid/FluidGooey.kt`, `test/fluid/FluidFloatingTabBar*Test.kt` | F3 | G | Fable |
| F4B.4 | Tablet: navigazione adattiva, **tab bar in alto e sua conversione in barra laterale e ritorno**, luce del contenuto, estensione dello sfondo | `ui/fluid/FluidAdaptiveNavigation.kt`, `ui/fluid/FluidSidebar.kt`, `ui/fluid/FluidTabBar.kt`, `ui/fluid/FluidBackgroundExtension.kt`, `test/fluid/FluidAdaptiveNavigationTest.kt` | F2, F3 | G | Fable |
| F4B.5 | Galleria, tracce, schede | `sample/…/bottom/`, `sample/…/GalleryNav.kt`, `test/glass/field/trace/`, `ref/componenti/barre.md` | F4B.1–4 | G | Opus |

F4B.3 e F4B.4 non toccano i file di F4B.1–2 e possono scrivere in parallelo. `FluidScreen.kt` e `toolbar/FluidToolbar.kt`
non sono nel perimetro di nessun pacchetto di F4B: ciò che la ricerca chiede a schermata e DSL passa da
`FluidToolbarSearchSlot`.

**Risultati osservabili.** Il tasto di ricerca diventa campo allargandosi mentre i vicini si stringono o si raccolgono in
un tasto; la capsula in basso cambia azioni senza scatti; la piega della tab bar è una fusione del campo, con luce e ombra
continue; una scheda con ruolo `Prominent` è un tondo a destra della capsula e resta quando la barra si ripiega; su tablet
la stessa dichiarazione dà una tab bar in alto, e il suo tasto la apre in una barra laterale di vetro e la richiude.

**Come si verifica.** Movimento: `ricerca-apre`, `capsula-cambia`, `tabbar-piega`, `tabbar-ricerca` con `first` e `curve`
sulle larghezze, più `tabbar-in-barra-laterale`; non ci sono collage Apple dedicati in R4 (si usa `B2_barra_contesto_16m02`
come riferimento di carattere). Banco fermo: `f4b/scheda-prominente` con `measure` contro R5 T6, accanto a
`rif/ios27/ritagli/T6_scheda_prominente_269_0519.jpg` e al collage `T6_scheda_prominente_278_1051-1057_ogni4.jpg`. Test:
`FluidSearchFieldTest`, `FluidToolbarSearchTest` (posizione automatica, vicini, stato attivo), `FluidFloatingCapsuleTest`,
`FluidFloatingTabBarLayoutTest`/`ScrollTest`/`InkTest` adattati, `FluidAdaptiveNavigationTest` (quale forma a quale
larghezza, lo stato convertito sopravvive alla rotazione), `FluidTabBarExtensionsTest` in `sample/src/testDebug`.
Backlog → S4: la ricerca con la tastiera vera; la bolla trascinata; la piega durante uno scorrimento; il tablet in
orizzontale con la tab bar in alto, il tasto, la conversione e il ritorno, registrati.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F4B-a | ricerca: da tasto a campo come una sola sagoma che si allunga, vicini compresi | 9 |
| F4B-b | tab bar: piega, scheda isolata e accessorio sono un solo campo | 8,5 |
| F4B-c | niente ricatture per fotogramma durante i cambi di taglia (i tre «costi senza difesa» di R2 §11 spariscono) | 8 |
| F4B-d | le app che usano `FluidFloatingTabBar`, `FluidFloatingCapsule`, `FluidSearchField` come oggi non cambiano comportamento | 9 |
| F4B-e | tablet: la stessa dichiarazione regge telefono e tablet; la tab bar in alto si converte in barra laterale e ritorna come una sola sagoma | 8 |

**Rischi e ripieghi.** Togliere `gooey` dalla tab bar vendored rompe la coreografia della piega (`SharedTransitionScope`,
impulso da 12 dp): si tiene `gooey` dietro un interruttore interno e si confrontano i due collage; decide Alessio. Il campo
di ricerca sopra la tastiera non può rifrangerla: velo più pieno su quel bordo. La conversione tab bar → barra laterale
come viaggio del piano non regge (troppi pezzi, taglie troppo diverse): dissolvenza incrociata fra le due con la sagoma
che cresce, dichiarata come divergenza nella copertura.

**Scelte di gusto.** Larghezza del campo di ricerca rispetto ai vicini; la piega della tab bar (campo / `gooey` di oggi);
la scheda prominente quando è scelta (pillola dentro il tondo / tondo tinto); la barra laterale del tablet: flottante e
monocroma (la proposta) / ai bordi con icone d'accento (iOS 27).

---

### F5A · Menu e menu contestuale

**Scopo.** I due esempi che Alessio ha fatto per nome: il tasto del menu che trasforma **tutta la barra** nel menu, e il
menu contestuale da pressione lunga con l'elemento in risalto e la pagina scurita con una sfocatura leggera. **Entra**:
sorgenti; menu dal tasto e dal gruppo; voci dentro la lente; struttura (sezioni, sottomenu, taglie, ordine, chiusura);
contestuale con sollevamento, sfondo, anteprima. **Resta fuori**: popover, avvisi, fogli (F5B).

**Progetto tecnico.**

- *Preparazione.* `ui/fluid/FluidGlassModal.kt` ha 2411 righe e contiene scrim, anteprima sollevata, portale e host: si
  spezza in file (stesso pacchetto, nessun cambio di comportamento) perché F5A e F5B possano lavorare senza pestarsi.
- *Dal gruppo.* Con la barra dichiarata il tasto del menu sta in un gruppo che è un pezzo del piano: la sorgente del viaggio
  è **il gruppo intero** (R1 N2), le altre icone si sfocano e spariscono nei primi 67–100 ms, la capsula si stringe e
  diventa la goccia. Fuori da una barra la sorgente è il tasto, come oggi. Il viaggio resta `FluidMenuJourney`
  (`ui/fluid/FluidMenuJourney.kt:582`) con la goccia. **Chi dà cosa**: l'`id` del gruppo lo assegna F4A.2; il riquadro del
  gruppo lo espone il registro del piano (F3.1, F3.2: il grappolo di un pezzo); F5A.1 registra la sorgente dell'elemento e,
  se l'elemento sta in un gruppo, la sorgente del viaggio è il riquadro del gruppo letto dal registro. Le altre icone non
  le spegne il menu: «menu aperto» è un cambio di stato della barra in cui il gruppo sorgente si dichiara vuoto, e la
  transizione del contenuto di F3.4 le sfoca e le toglie nei tempi del bersaglio; alla chiusura tornano nello stesso modo.
- *Le voci dentro la lente.* Oggi le voci stanno in un nodo fratello con alfa e mappa del viaggio
  (`ui/fluid/FluidGlassMenuPanel.kt:278-312`) e compaiono fra 0,4 e 0,75 del progresso. Nei frame sono dentro dal primo
  fotogramma, piccole, sfocate e piegate. Si ottiene mettendo sul layer del contenuto un `RenderEffect`: sfocatura + lo
  shader `GlassContentLens` che sposta i campioni con lo stesso campo della sagoma; gli uniform cambiano a ogni fotogramma ma
  la catena lavora solo sul layer delle voci, non sulla pagina. La scelta col dito che ha aperto (`FluidMenuHandOff`) non
  cambia: la geometria delle celle è quella d'arrivo. Il banco del movimento sa applicare uno shader al layer del contenuto
  da F0.4 (campo `effect` della traccia, 4.3) e legge `GlassContentLens` dal Kotlin come ogni altra sorgente: la
  deformazione delle voci si giudica sul banco, non solo in S4.
- *Icone delle righe*: da 20 a 22 dp (sezione 2, R6), riga invariata: `FluidMenuRowGlyph` (`ui/fluid/FluidMenuJourney.kt:507`)
  e le due `Icon` di `ui/fluid/FluidGlassMenuPanel.kt:442`, `:447`, che passano a `FluidIcon`; la larghezza del pannello
  dipende da quel numero e la fissa `FluidMenuPanelWidthTest`.
- *Struttura.* `FluidContextAction` guadagna `subtitle`, `children` (un livello di sottomenu), `keepsMenuOpen`, con valore
  di serie e **prima** di `onClick`; resta un costruttore secondario con la firma a sette parametri di oggi, perché la
  chiamata posizionale continui a compilare (3.11; la fissa `GlassCompatSignaturesTest`). Fila di
  simboli in cima (`FluidMenuElementSize.Small`/`Medium`); ordine `Priority` che capovolge il menu quando si apre verso
  l'alto.
- *Contestuale.* L'anteprima sollevata c'è (`FluidLiftedPreview`, scala 1,03); lo scrim c'è (sfocatura 3,75 dp, nero
  12/20%, `sizeResponse = 0`). La decisione di Alessio è «scurito con una sfocatura leggera»: lo scrim del contestuale
  prende un livello suo, più scuro di quello dei menu, da scegliere fra tre tavole; la sfocatura resta sotto la soglia
  oltre cui un pannello a tutto schermo diventa una media (invariante 20). Anteprima personalizzata e forma del ritaglio
  come parametri.

**Bersagli di R4.**

| grandezza | bersaglio | scheda, fiducia |
|---|---|---|
| apertura | 330–370 ms sul telefono; 400–430 ms per un menu di sette righe | B1 (a, b), alta |
| superamento | uno solo: bordo alto +6% dell'altezza, larghezza +3,4% a 100–167 ms, verso il lato lontano dal tasto; nessuna seconda oscillazione sopra lo 0,2% | B1 (a), alta |
| chiusura | ≈200 ms di sagoma (crolla in due frame fra 67 e 133 ms), icona nitida a 300 ms | B1 (a), alta. È la misura di Apple, **non** il bersaglio di serie: l'engine chiude in ≈0,5 s per una scelta registrata di Alessio (sotto) |
| contenuto | dentro dal primo frame, sfocato e deformato; leggibile a ≈170–200 ms, nitido a ≈270 ms; prima e ultima riga deformate fino a ≈230 ms | B1, alta |
| gruppo della barra | si stringe al 60% nei primi 33 ms, icone sfocate e sparite entro 67–100 ms | B1 (b), alta |
| materiale | limpido in viaggio; smerigliatura e ombra piena negli ultimi 100–150 ms | B1, media |
| molla | smorzamento 0,65–0,75, rigidezza 500–800 | B1, **bassa** |
| punto di luce | sulla goccia limpida, fra 33 e 67 ms, un punto di luce forte (visibile in `B1_menu_foto`, `f13048`–`f13049`) | B1 (b), osservato su due fotogrammi: **a occhio**. Si rende col bagliore dello strato della luce acceso sul lato lontano dal tasto, senza un termine speculare nuovo; scelta: con / senza |
| iOS 27 | salita del menu di trabocco: larghezza al 93% a 100 ms, altezza +1,6% al picco; coda non misurata | R5 B11, media: compatibile con B1, nessun cambio rilevabile |
| icone delle righe | 22 dp, riga da 48 dp e testo da 17 sp invariati | R6 (Apple: 42 pt, 17 pt, 15–19 pt di sagoma) |
| contestuale: ritardo, scala del sollevamento, durate | **non documentati** (R1 X1), non misurati da R4 | scelta di gusto |
| sfondo del contestuale | scurito con sfocatura leggera | decisione di Alessio (sezione 2) |

Oggi: `dropOpen` (0,72 / 500), `dropClose` (rigidezza 158, ≈0,5 s), `DropPeakOvershoot` 0,0384, contenuto fra 0,4 e 0,75
(`ui/fluid/FluidMotion.kt:219-253`). L'apertura è già nell'intervallo. Il contenuto entra tardi: è lo scarto da chiudere.
La **chiusura** è più lenta di quella misurata (≈0,5 s contro 200 ms), ma non è uno scarto: è una preferenza registrata
nel codice (`ui/fluid/FluidMotion.kt:221`: «un filo più lenta dell'andata (Alessio: "troppo veloce")»), presa guardando il
livello `Balanced`. Resta com'è finché Alessio non sceglie sul confronto (scelte di gusto, sotto).

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G | modello |
|---|---|---|---|---|---|
| F5A.0 | Spezzare `FluidGlassModal.kt` in file, senza cambi di comportamento | `ui/fluid/FluidGlassModal.kt` → `FluidGlassModalScrim.kt`, `FluidLiftedPreview.kt`, `FluidGlassModalPortal.kt`, `FluidGlassModalHost.kt` | — | G | Opus |
| F5A.1 | Sorgenti: `fluidTransitionSource`, sorgenti degli elementi di barra; la sorgente di un elemento in un gruppo è il riquadro del gruppo, letto dal registro del piano | `ui/fluid/FluidTransitionSource.kt`, `ui/fluid/toolbar/FluidToolbar.kt` (un aggancio), il suo test | F4A, F3.2 (in sola lettura) | G | Opus |
| F5A.2 | Menu dal tasto e dal gruppo; voci nella lente; icone delle righe a 22 dp; punto di luce; tempi di chiusura **solo dopo la scelta** | `ui/fluid/FluidMenuLayer.kt`, `ui/fluid/FluidMenuJourney.kt`, `ui/fluid/FluidGlassMenuPanel.kt`, `gf/GlassContentLensShader.kt`, `fp/FluidMorphMenu.kt`, `ui/fluid/FluidMotion.kt` (i token del menu) | F5A.1, F4A | G | Fable |
| F5A.3 | Struttura: sottomenu, taglie, ordine, chiusura; `FluidContextAction` con i campi nuovi e il costruttore compatibile | `ui/fluid/FluidMenu.kt`, `ui/fluid/FluidGlassModalPortal.kt` (`FluidContextAction`), `test/fluid/FluidMenu*Test.kt`, `test/compat/GlassCompatSignaturesTest.kt` (il caso posizionale) | F5A.0, dopo F5A.2 sui file comuni | G | Opus |
| F5A.4 | Contestuale: sollevamento, sfondo, anteprima, `Modifier.fluidContextMenu` | `ui/fluid/FluidGlassModalScrim.kt`, `ui/fluid/FluidLiftedPreview.kt`, `ui/fluid/FluidContextMenu.kt` | F5A.0 | G | Fable |
| F5A.5 | Galleria, tracce, schede | `sample/…/catalog/MenusDemo.kt`, `test/glass/field/trace/`, `ref/componenti/menu-liquidi.md`, `ref/componenti/menu-modali.md` | F5A.2–4 | G | Opus |

**Risultati osservabili.** Tocco sul tasto «…» di un gruppo di tre: l'intera capsula si stringe, diventa goccia e si apre
nel menu con le voci già dentro, piegate e sfocate, poi nitide; chiusura nel tempo scelto; pressione lunga su una riga: la
riga si
solleva, la pagina dietro si scurisce con una sfocatura leggera, il menu nasce accanto; un sottomenu si apre dentro il
menu.

**Come si verifica.** Movimento: `menu-apertura` e `menu-chiusura` con `pair` contro
`rif/collage/B1_menu_telefono_apertura_f9143-9166.jpg`, `B1_menu_telefono_chiusura_f9185-9200.jpg`,
`B1_menu_foto_f13036-13075.jpg` (i render `B1_menu_apertura_render_f9336-9367.jpg` e `B1_menu_chiusura_render_f9391-9414.jpg`
solo per la sagoma: sono ≈2× più lenti); `curve` sul bordo alto e sulla larghezza contro la tabella di B1 (a), con lo stesso
metodo degli slit-scan in `rif/slit/`; `contestuale-apre`; `first` e `last` ovunque; `menu-apertura` con l'effetto sul
contenuto (le voci passano dalla lente) e `menu-interrotto` (chiusura chiesta a metà apertura). I confronti usano i
fotogrammi del video allineati su `t0` (`B1_menu_foto` comincia dieci fotogrammi prima). Avvertenza: se F0-a ha detto che
il banco non riproduce la goccia entro un fotogramma, il giudizio finale di B2 su questa fase è di S4. Test: `FluidMenuJourneyTest`, `FluidMenuMotionTest`,
`FluidMenuGeometryTest`, `FluidMenuSelectionTest`, `FluidMenuRelayTest` verdi o adattati con motivo; `FluidMenuHandOffTest`
e `FluidMorphMenuBackOrderTest` in `sample/src/testDebug`. Banco fermo: tre tavole dello sfondo del contestuale. Backlog →
S4: il menu dal vero a 120 Hz; il dito che apre e sceglie senza staccare; il contestuale su una lista vera; il back
predittivo.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F5A-a | la sagoma in viaggio non è mai un rettangolo che cambia misura | 9 |
| F5A-b | le voci sono dentro dal primo fotogramma e passano dalla lente; mai una dissolvenza su un pannello vuoto | 9 |
| F5A-c | tempi e superamento contro B1 (a): apertura, picco, riposo, chiusura | 8,5 |
| F5A-d | è il gruppo intero a diventare menu, e ritorna gruppo senza scalini | 9 |
| F5A-e | contestuale: l'elemento in risalto si stacca dalla pagina; lo sfondo è quello scelto | 8,5 |
| F5A-f | il dito che ha aperto sceglie; back predittivo; annullo a metà (i comportamenti della 3.0 restano) | 9 |

**Rischi e ripieghi.** Il contenuto deformato costa o sfarfalla sul testo piccolo: deformazione solo finché il progresso è
sotto 0,6 e poi la sola sfocatura; al limite le sole righe estreme. Chiusura in 200 ms con la goccia (solo se Alessio la
sceglie): se la coda della goccia non fa in tempo a posarsi, la chiusura usa il transito a lastre (senza goccia) e la
goccia resta all'apertura. Il gruppo sorgente non si trova nel registro (una barra scritta con `actions`, un tasto fuori
da un contenitore): la sorgente è il tasto, come oggi.

**Scelte di gusto.** Sfondo del contestuale: tre livelli di scurimento e sfocatura (com'è oggi nei menu / più scuro / più
scuro e un po' più sfocato). **Chiusura del menu**: 200 ms (Apple) / com'è oggi (≈0,5 s) / via di mezzo, su tre video
affiancati al livello `Full`. Superamento del menu: R4 (3–6%) / più composto. Punto di luce sulla goccia: con / senza.
Scala del sollevamento: 1,03 di oggi / più marcata.

---

### F5B · Popover, avvisi, fogli, zoom

**Scopo.** Tutto nasce dalla sorgente: il popover prende il posto del suo tasto, avvisi e conferme escono dall'elemento che
li apre, il foglio è di vetro a mezza altezza e opaco a tutta altezza, una pagina o un foglio si aprono ingrandendo la
sorgente. **Entra**: popover; avviso; dialogo di conferma in linea; modalità e oscuramento; foglio con detent, maniglia,
rientro e passaggio a opaco; zoom per fogli e pagine; S4. **Resta fuori**: fogli di piattaforma in un'altra finestra
(restano dipinti: F1B.5).

**Progetto tecnico.**

- *Popover* (R1 P6): la sorgente si nasconde nel disegno finché il popover è aperto (lo fa già `FluidBarActionImpl`,
  `ui/fluid/FluidScreen.kt:1866`); sul telefono si adatta in foglio salvo richiesta.
- *Avviso e conferma* (P4, P5): oggi l'avviso nell'host entra in dissolvenza con geometria `snappy`
  (`ui/fluid/FluidMenuLayer.kt:467-640`); prende il viaggio dalla sorgente. `FluidConfirmationDialog` sta sopra l'elemento,
  senza Annulla, si chiude toccando fuori; senza sorgente è centrato e con Annulla. Le varianti «legate a un elemento» di
  iOS 27 sono legate a un **dato** (`item:` opzionale: R5, documentato) e non cambiano aspetto né ancoraggio: qui sono due
  sovraccarichi, `FluidAlert(item: T?, …)` e `FluidConfirmationDialog(item: T?, …)`, come c'è già per
  `FluidGlassModalPortal(item: T?, …)`.
- *Modalità* (P7): oscuramento se il compito interrompe, niente se è in parallelo (`undimmedUpTo`).
- *Foglio* (P1, P2): a detent parziale rientrato dai bordi con vetro e angoli inferiori concentrici allo schermo
  (`FluidConcentricShape`); salendo a tutta altezza il velo cresce fino a opaco e il foglio si ancora ai bordi; maniglia;
  lo scorrimento del contenuto lo espande.
- *Zoom* (P3, V4): sorgente dichiarata, arrivo = il foglio o la pagina; per le pagine `FluidPageTransition.Expand` c'è già e
  prende la sorgente dichiarata.
- *Foglio in dissolvenza* (iOS 27, `navigationTransition(.crossFade)`): `FluidGlassSheet(…, transition =
  FluidSheetTransition.CrossFade)`. Documentato, mai mostrato in un video (R5): tempi e curva a occhio.

**Bersagli.** R4: foglio che sale in ≈330 ms, supera appena e rientra (B8, **a occhio**); per avvisi e popover valgono le
fasi del menu (B1). Raggio, rientro e soglia dell'opacità del foglio **non sono documentati** (R1 P1): gusto. R5 non trova
differenze dichiarate per fogli, detent e popover (documentazione invariata, non rimisurati). Di iOS 27 entrano due cose,
tutte e due senza misure: gli avvisi e i dialoghi aperti da un dato (solo API) e il foglio in dissolvenza (a occhio).

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F5B.1 | Popover che sostituisce il tasto; avviso e conferma dalla sorgente e i sovraccarichi `item`; modalità | `ui/fluid/FluidGlassModalPortal.kt`, `ui/fluid/FluidGlassModalHost.kt`, `ui/fluid/FluidMenuLayer.kt` (la parte degli avvisi), `ui/fluid/FluidSheet.kt` (`FluidAlert`), `ui/fluid/FluidConfirmationDialog.kt` | F5A | G | Fable |
| F5B.2 | Foglio di vetro: detent, maniglia, rientro, opaco a tutta altezza; transizione in dissolvenza | `ui/fluid/FluidGlassSheet.kt`, `ui/fluid/FluidSheetScaffold.kt`, il suo test | F5A.0, F3.5 | G | Fable |
| F5B.3 | Zoom dalla sorgente per fogli e pagine | `ui/page/FluidPageTransition.kt`, `ui/page/FluidPageMotion.kt`, `ui/page/FluidPageHost.kt` | F5A.1 | G | Opus |
| F5B.4 | Galleria, tracce, schede | `sample/…/catalog/RowsAndSheetsDemo.kt`, `sample/…/presentations/`, `test/glass/field/trace/`, `ref/componenti/menu-modali.md` | F5B.1–3 | G | Opus |
| F5B.5 | **S4** e rapporto | `artifacts/liquid-glass/sessioni/S4.md`, `stato.md` | tutti | G, D | orchestratore + Opus |

**Risultati osservabili.** Un tasto di barra si apre in un popover che ne prende il posto e vi rientra; «Elimina» apre la
conferma sopra di sé; un foglio a mezza altezza è una lastra di vetro staccata dai bordi che diventa pagina opaca salendo;
una card si apre in pagina ingrandendosi.

**Come si verifica.** Movimento: `popover`, `avviso`, `conferma`, `foglio-sale`, `foglio-a-tutta-altezza`, `zoom` con
`first`, `last` e `curve`, più `foglio-trascinato-interrotto` per F5B-e; per il foglio l'unico riferimento è il tratto
4:05,6–4:06,1 del video (collage da produrre con
`rif/tools/sheet.py` in F5B.4). Test: `FluidGlassModalStateTest`, `FluidGlassModalContentTest`, `FluidPopoverKeyboardTest`,
un test dei detent. Backlog → S4: tutto, con la tastiera e il back predittivo.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F5B-a | ogni presentazione nasce dalla sua sorgente e vi ritorna, senza un fotogramma in cui sorgente e arrivo si vedono insieme | 9 |
| F5B-b | il foglio: vetro rientrato ai detent parziali, opaco e ancorato a tutta altezza, passaggio continuo | 8,5 |
| F5B-c | oscuramento secondo la modalità, non per abitudine | 8 |
| F5B-d | `FluidGlassModalPortal`, `FluidAlert`, `FluidConfirmSheet`, `FluidSheet` chiamati come oggi si comportano come oggi | 9 |
| F5B-e | trascinamento del foglio: segue il dito, si interrompe, riprende al volo | 8,5 |

**Rischi e ripieghi.** Il foglio che passa da vetro a opaco cambia taglia della cattura a ogni fotogramma di trascinamento:
cattura tenuta durante il gesto e velo come uniform. Sorgente fuori schermo o sparita alla chiusura: si torna alla
presentazione senza sorgente (centrata o dal basso), mai un salto.

**Scelte di gusto.** Rientro e raggio del foglio a mezza altezza; a che altezza comincia a diventare opaco; quanto oscura
un avviso; durata del foglio in dissolvenza.

---

### F6 · Controlli e tocco

**Scopo.** Il vetro che risponde: cede come un gel sotto il dito, si accende dal punto di contatto e passa la luce ai
vicini; i controlli salgono nel vetro mentre li si tocca; i tasti hanno gli stili di Apple. **Entra**: flessione; bagliore
e luce ai vicini; interruttore, cursore, segmentato; stili, larghezza e taglia d'ambiente dei tasti; luce che si muove
(**spenta di serie**: sezione 2); liste, campi e
selettori riallineati; modificatori di accessibilità del materiale; S5. **Resta fuori**: aptica nuova (R1 H6: Apple non ne
documenta; resta quella dell'engine).

**Progetto tecnico.**

- *Flessione* (R3 cap. 5): si deforma il punto interrogato, `p' = c + S⁻¹(p − c)`, con `S` allungamento lungo la velocità e
  schiacciamento sull'asse ortogonale, più un rigonfiamento verso il dito. La crescita del tasto esce dal `layerBlock`
  (`ui/fluid/FluidGlassControls.kt:274-292`) ed entra nella geometria; nel `layerBlock` resta la traslazione. Le lastre che
  cedono spengono il clip hardware e ricevono il margine del campo; il contenuto segue con una trasformazione del proprio
  layer. Tetto della deformazione 1,2: oltre, il campo di un rettangolo scalato non è più una distanza credibile (R3).
  Al tocco anche l'**icona** risponde: si sfoca appena e cresce con il vetro (R4 B4), con una sfocatura sul layer del
  contenuto (API 31+) e la stessa scala della geometria.
- *I numeri di LibrePods.* Pressione, crescita (tondi +50%, tetto 24 dp), deriva e allungamento vengono da LibrePods
  (GPL-3.0, `ui/fluid/FluidPress.kt:1-29`) e li fissa `FluidPressTest`. Apple misura ×1,2. Si cambia solo dopo la scelta di
  Alessio; il codice resta GPL comunque.
- *Bagliore* (H2): funzione della distanza dal dito valutata nello spazio del **contenitore**: i pezzi dello stesso campo
  la ricevono da soli, le lastre separate dello stesso contenitore ricevono lo stesso punto e lo stesso raggio. Sta nello
  strato della luce: non rifà la sfocatura.
- *Controlli* (H4): pomello bianco → lente limpida che cresce oltre la traccia → richiusura. La goccia c'è e i suoi tempi
  sono fissati dai test: si confrontano con B5 e si ritoccano dove si scostano. Cursore: inerzia, tacche, valore neutro,
  senza pomello.
- *Luce che si muove* (M4). **Spenta di serie, per decisione** (sezione 2): `LocalFluidGlassMotionLight` vale `false`
  finché l'app non lo fornisce o l'utente non lo accende dalla riga facoltativa di `fluidAppearanceSection(…,
  glassMotionLight, onGlassMotionLightChange)`, come per il cursore di F2.2. Accesa: `TYPE_GAME_ROTATION_VECTOR` (o
  accelerometro con passa-basso) ruota la direzione dei riflessi (`reflexDir`) di pochi gradi; uno stato per finestra;
  il sensore si registra solo se la luce è accesa e c'è un vetro in scena, e si ferma a telefono fermo, in risparmio
  energetico, con le animazioni ridotte. La luce che corre sul bordo alla comparsa di un elemento importante non dipende
  dal sensore e resta di serie.
- *Taglia d'ambiente* (F6.4). `LocalFluidGlassSize` porta la taglia dei tasti; `FluidGlassSize.iconSize` passa da metà a
  **0,6 del vetro** (24 / 26 / 32 dp, R6; oggi 20 / 22 / 27: `ui/fluid/FluidGlassControls.kt:92`) e `LocalFluidIconSize` ne
  discende; fogli e popover dell'host riportano taglia e stile ai valori di serie (iOS 27: un foglio non eredita lo stile
  della barra che lo apre).
- *Azioni a scorrimento* (L3). In iOS 27 l'azione è un tondo colorato con il glifo bianco e l'etichetta sotto, e la riga si
  accorcia e si arrotonda (R5 L3, osservato, senza confronto con iOS 26 e senza tempi): l'aspetto si allinea a occhio.
  `swipeActionsContainer` (una riga aperta alla volta) **non ha un equivalente**: nell'engine l'azione parte al rilascio
  oltre la soglia e la riga non resta aperta. È una divergenza che resta, scritta nella copertura.
- *Accessibilità del materiale* (M11): trasparenza ridotta → più velo; contrasto aumentato → bordo di contrasto (c'è:
  `ui/fluid/GlassContrast.kt`); movimento ridotto → niente elastico, niente flessione, niente sensore.

**Bersagli di R4.**

| grandezza | bersaglio | scheda, fiducia |
|---|---|---|
| pressione, tondo | ×1,19–1,20 in 133–167 ms, senza superamento | B4, media |
| bagliore | quasi al bianco in 233 ms; continua a salire finché il dito resta; parte sotto il dito e riempie una capsula in ≈330 ms | B4, media |
| tenuto | segue il dito: spostato di ≈0,23 D, ellisse ≈1,15:1 | B4, **a occhio** |
| rilascio | taglia di riposo a +167 ms; −2,7% a +167–200 ms; riposo a +330 ms; posizione oltre il riposo dell'11% dello spostamento | B4, media |
| molla del rilascio | smorzamento 0,55–0,60, rigidezza 150–200 | B4, **bassa** |
| bagliore al rilascio | si dimezza ogni ≈100 ms, spento a 500 ms | B4, media |
| bagliore propagato | si accende in ≈300 ms e si spegne in ≈300 ms | B4, **bassa** (prospettiva) |
| interruttore | scatto di ≈530 ms: lente in 70–100 ms, corsa 200 ms, chiusura 130 ms; lente ≈×1,5 il pomello | B5, media (la scala a occhio) |
| cursore | lente allungata ≈×1,5 nei tratti veloci; torna pomello in 130–200 ms | B5, a occhio |
| luce sul bordo | l'arco fa ¾ di giro in 8,5 s nella dimostrazione | B8, a occhio |
| luce dal movimento del dispositivo | detta, **non misurabile** (R4, R5) | spenta di serie (decisione); l'ampiezza a occhio |
| icona al tocco | si sfoca e cresce insieme al vetro | B4, osservato: **a occhio** |
| iOS 27 | tocco, bagliore, rilascio e controlli non rimisurati; Apple estende lo stesso comportamento al Mac | R5: invarianza dedotta |
| icona nel tasto | 0,6 del vetro: 24 / 26 / 32 dp per `Compact` / `Regular` / `Large` | R6 (altezza della sagoma 0,42–0,55 del tasto in Apple) |

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F6.1 | Flessione a gel; crescita nella geometria; margine e clip | `gf/GlassBodyShader.kt`, `gf/GlassLightShader.kt`, `gf/GlassFieldKernel.kt`, `gf/GlassFieldGeometry.kt`, `ui/fluid/FluidPress.kt`, `ui/fluid/FluidGlassControls.kt`, `bd/DrawBackdropModifier.kt`, `fp/FluidPhysicsSurface.kt` (pressione), `test/fluid/FluidPressTest.kt` | F5B, scelta sulla crescita | G | Fable |
| F6.2 | Bagliore e luce ai vicini | `ui/glass/interaction/GlassInteraction.kt`, `ui/fluid/FluidGlassContainer.kt` (stato del tocco), `test/glass/interaction/GlowRecipeTest.kt` | F6.1 | G | Fable |
| F6.3 | Controlli che salgono nel vetro; cursore | `ui/fluid/FluidSwitch.kt`, `ui/fluid/FluidSlider.kt`, `ui/fluid/FluidControls.kt`, `ui/glass/interaction/GlassDrop.kt`, i test della goccia | F1B.3 | G | Fable |
| F6.4 | Stili, larghezza e taglia d'ambiente dei tasti: `LocalFluidGlassSize`, `iconSize` a 0,6 del vetro, valori di serie dentro fogli e popover; la scelta 44 / 46 dp del vetro, se è rimasta aperta | `ui/fluid/FluidGlassButtonStyle.kt`, `ui/fluid/FluidGlassControls.kt` (dopo F6.1), `ui/fluid/FluidIcon.kt`, `ui/fluid/FluidMetrics.kt`, `ui/fluid/FluidGlassModalHost.kt` e `ui/fluid/FluidGlassSheet.kt` (un fornitore ciascuno) | F6.1 | G | Opus |
| F6.5 | Luce che si muove: sensore **spento di serie**, `LocalFluidGlassMotionLight`, riga nelle impostazioni d'aspetto; corsa sul bordo | `gf/GlassLightEnvironment.kt`, `ui/theme/FluidTheme.kt` (un fornitore), `ui/fluid/FluidAppearance.kt` (una riga), `test/glass/field/GlassLightEnvironmentTest.kt` | F1B | G | Opus |
| F6.6 | Contenuto: metriche di liste e moduli, campi, stepper di vetro (non c'è), azioni a scorrimento con l'aspetto di iOS 27 (R5 L3) e coerenti col contestuale | `ui/theme/FluidComponents.kt`, `ui/fluid/FluidGroupedItems.kt`, `ui/fluid/FluidTextField.kt`, `ui/fluid/FluidSwipeActions.kt`, `ui/fluid/FluidStepper.kt` | — | G | Opus |
| F6.7 | Accessibilità del materiale; il vetro che recede quando la finestra non è attiva (solo in multi-finestra) | `gf/GlassRecipe.kt`, `ui/fluid/GlassContrast.kt`, `test/fluid/FluidSystemContrastTest.kt` | F6.1 | G | Opus |
| F6.8 | Galleria (`TouchLab`), tracce, schede | `sample/…/TouchLab.kt`, `sample/…/ControlsTab.kt`, `test/glass/field/trace/`, `ref/componenti/controlli.md`, `ref/componenti/controlli-gesto.md` | F6.1–7 | G | Opus |
| F6.9 | **S5** e rapporto (tocco; prima volta sull'S25: prima l'APK di riferimento della 3.1.2 conservato in F0, poi la build nuova, sugli scenari di F7.1) | `artifacts/liquid-glass/sessioni/S5.md`, `stato.md` | tutti, e F7.1 | G, D | orchestratore + Opus |

F6.3, F6.5 e F6.6 hanno perimetri disgiunti da F6.1 e possono scrivere mentre F6.1 lavora. S5 usa gli scenari di misura:
F7.1 si scrive **prima** di S5, appena F6.8 è chiuso.

**Risultati osservabili.** Un tasto premuto cresce, si accende dal punto del dito, lo segue allungandosi, e al rilascio
torna con un solo piccolo sottopasso; in una barra il bagliore arriva ai gruppi vicini; il pomello dell'interruttore
diventa una lente che lascia vedere la traccia; un tasto principale è tinto con la tinta che segue lo sfondo; di serie
inclinando il dispositivo non succede niente, e con la luce dal movimento accesa dalle impostazioni d'aspetto i riflessi
si spostano di poco; un foglio aperto da una barra con tasti `Compact` ha i tasti alla taglia di serie.

**Come si verifica.** Movimento: `tasto` contro `rif/collage/B4_tocco_tasto_f6940-7018_ogni2.jpg` con `curve` su taglia e
luminanza; `capsula-trascinata` contro `B4_flessione_capsula_f7300-7384_ogni2.jpg`; `bagliore-vicini` contro
`B4_bagliore_propagato_f21776-21880_ogni3.jpg` e `B4_bagliore_play_f21934-22036_ogni3.jpg`; `interruttore` contro
`B5_interruttore_f7752-7880_ogni2.jpg`; `cursore` contro `B5_cursore_f8040-8146_ogni2.jpg`; `luce-bordo` contro
`B8_luce_sul_bordo_f19975-20235_ogni8.jpg`. Banco fermo: `f6/flessione-*` (cinque deformazioni fino a 1,2: lo spessore del
filo non deve variare a vista). Test: `FluidPressTest` (numeri nuovi, se scelti), `GlassDropSquashTest`,
`GlassDropFaceTest`, `GlowRecipeTest`, `GlassLightEnvironmentTest` (spenta di serie: nessun ascoltatore del sensore
registrato; accesa: registrato solo con un vetro in scena), `FluidTouchTest` in `sample/src/testDebug`; `tasto-interrotto`
(rilascio a metà crescita) con `first` e `last`. Backlog → S5: il tocco è la cosa che il banco giudica peggio; in S5 si
registra tutto a 120 Hz su tablet e telefono, e sul telefono prima la 3.1.2 di riferimento.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F6-a | flessione: il contorno cede, non è una scala; il filo resta dello stesso spessore | 9 |
| F6-b | risposta al dito: la luce parte nel fotogramma del contatto; il rilascio ha un solo sottopasso | 9 |
| F6-c | la luce arriva ai vicini e non rifà la sfocatura di nessuno (contato) | 8,5 |
| F6-d | controlli: pomello → lente → pomello senza scalini, contro B5 | 8,5 |
| F6-e | movimento ridotto e trasparenza ridotta rispettati dal materiale nuovo | 7,5 |
| F6-f | costo di un gesto continuo su un tasto e su una capsula larga (misurato in S5) | 7 |

**Rischi e ripieghi.** Il gesto continuo ripaga la catena a ogni fotogramma (rischio 1): se C1 è stato chiuso con il corpo a
un layer e S5 lo smentisce sulle capsule larghe, la flessione dei grandi si riduce alla luce e allo spostamento, e la
deformazione resta ai piccoli. Il contenuto che segue con una trasformazione di layer si stacca dal vetro a deformazioni
forti: tetto più basso per le lastre con testo. Il sensore consuma: è spento di serie per decisione, e S5 misura quanto
costa acceso, per scriverlo nella scheda accanto all'interruttore.

**Scelte di gusto.** Crescita del tasto: ×1,2 (Apple) / ×1,5 (oggi) / via di mezzo. Intensità del bagliore sul tema chiaro.
Luce dal movimento (spenta di serie, già deciso): quanto si spostano i riflessi quando è accesa. Quanto lontano arriva la
luce ai vicini. L'icona al tocco: si sfoca e cresce (Apple) / cresce soltanto. Azioni a scorrimento: tondo con etichetta
sotto (iOS 27) / com'è oggi.

---

### F7 · Ottimizzazione

**Scopo.** Far stare tutto nel fotogramma su tablet e telefono senza cambiare ciò che si vede. **Entra**: misure su Tab S9 e
S25; le leve; i bilanci per fotogramma; le guardie contro le regressioni di costo. **Resta fuori**: qualunque cambio di resa
non imposto da un bilancio (e in quel caso passa da Alessio come confronto).

**Progetto tecnico.** Le leve, nell'ordine in cui si provano (ognuna misurata da sola, prima e dopo, sugli stessi scenari):

1. **meno nodi**: luce a riposo registrata una volta in un layer e ridisegnata (come fa oggi il bordo a riposo); ombre
   condivise fra lastre uguali;
2. **una cattura per campo** dove ne restano di più; fusione solo in movimento;
3. **risoluzione della cattura** con isteresi alla Haze (punteggio = pixel × aggiornamenti ravvicinati, due livelli, soglia
   d'uscita più bassa di quella d'ingresso) al posto della soglia secca di `quantiseScaleFactor`
   (`bd/DrawBackdropModifier.kt:443-452`);
4. **nodo di sfocatura stabile** quando cambiano solo gli uniform (o il corpo a due layer, se non è già stato adottato);
5. **dispersione solo sui piccoli e spenta in movimento** (c'è già), **ombra nello shader solo in viaggio** (è già così);
6. **riscaldamento** degli shader all'avvio, fuori schermo;
7. **sonda sospesa** quando la cattura non cambia; frequenza per contenitore;
8. **sorgenti a schermo intero registrate solo se qualcuno le legge** (il candidato 2.14 mai fatto,
   `ref/componenti/vetro.md:600-605`): è il costo noto più grande dell'S25 (47 passate GPU, misura del 2026-10-13);
9. **bordo dello scorrimento**: tenuta della cattura, metà risoluzione, striscia solo quando il contenuto è sotto.

**Bilanci.** Non si inventano adesso: si fissano in S5 sulla baseline, con due regole. (i) Nessuno scenario è peggio della
3.1.2 misurata **sullo stesso dispositivo** con l'APK di riferimento conservato in F0 (sul tablet in S1, sul telefono in
S5, dove l'S25 entra per la prima volta), salvo dove il materiale nuovo fa una cosa che prima non esisteva. Le colonne
sono tre e non si confondono: 3.1.2 di riferimento, materiale nuovo prima delle leve, materiale nuovo dopo. (ii) Il
bersaglio è stare nel periodo del display al novantesimo percentile sugli scenari fissati (scorrimento di una lista con
barra e tab bar; apertura e chiusura di un menu; cambio di contesto di una barra; pressione continua su una capsula; foglio
trascinato; pagina con otto lastre). Dove il periodo è 8,3 ms (120 Hz) e la baseline di oggi è già 7–10 ms di RenderThread
(R2 §11), il bersaglio realistico lo dice S5 e si scrive qui.

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F7.1 | Misura: scenari fissi nella pagina «Misure», script di cattura e riassunto dei tempi. Si scrive **prima di S5**, che li usa | `sample/…/measure/`, `tools/qa/perf-glass.sh`, `tools/qa/perf-summary.py` | F6.1–F6.8 (non S5) | G, D | Opus |
| F7.2 | Risoluzione con isteresi; nodo di sfocatura stabile | `bd/DrawBackdropModifier.kt`, `ui/fluid/FluidGlassQuality.kt`, `test/glass/backdrop/BackdropTextureCapTest.kt`, `test/fluid/FluidGlassQualityTest.kt` | F7.1 | G | Fable |
| F7.3 | Nodi e catture: luce in cache a riposo, ombre condivise, palco e fusione solo in movimento | `gf/GlassShadow.kt`, `gf/GlassFieldEffects.kt`, `gf/GlassPlane.kt`, `ui/fluid/FluidGlassContainer.kt` | F7.1 | G | Fable |
| F7.4 | Riscaldamento, sonda, bordo dello scorrimento | `gf/GlassFieldHealth.kt`, `gf/GlassProbe.kt`, `ui/fluid/FluidScrollEdge.kt` | F7.1 | G | Opus |
| F7.5 | Sorgenti a schermo intero senza lettori | `ui/fluid/GlassMaterial.kt` (`glassBackdropSource`), `bd/backdrops/LayerBackdropModifier.kt` | F7.1 | G | Opus |
| F7.6 | Guardie: bilanci scritti, conta dei nodi per componente, rapporto prima/dopo | `sample/src/testDebug/…/GlassNodeCountTest.kt`, `ref/componenti/vetro.md` (costi), `artifacts/liquid-glass/sessioni/F7-bilanci.md` | F7.2–5 | G | Sol |
| F7.7 | Verifica su tablet e telefono (parte di **S6**) | `artifacts/liquid-glass/sessioni/S6.md` | tutti | G, D | orchestratore + Opus |

F7.2–F7.5 toccano file diversi salvo `bd/DrawBackdropModifier.kt` (F7.2): gli altri non lo scrivono. F7.1 è l'unico
pacchetto di F7 che gira prima di S5; gli altri partono dalla baseline che S5 ha misurato.

**Risultati osservabili.** Una tabella per scenario e per dispositivo: prima, dopo, bilancio, esito; gli stessi collage e le
stesse immagini di riferimento di F1–F6 **invariati** (`compare` a differenza zero dove la leva non tocca la resa, e sotto
tolleranza dichiarata dove la tocca).

**Come si verifica.** Dispositivo: è la fase che si verifica lì. Banco: `compare` su tutte le `golden/liquid/`; `pair` di
tre tracce a campione per provare che il movimento non è cambiato. Test: conta dei nodi, tetti di texture, qualità.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F7-a | ogni scenario dentro il suo bilancio su Tab S9 e S25 (è F1 «dopo»: 8,5 su misure del dispositivo) | 8,5 |
| F7-b | la resa non è cambiata: immagini e collage identici o dentro la tolleranza scritta | 9 |
| F7-c | ogni leva è misurata da sola e resta solo se guadagna | 8,5 |
| F7-d | nessuno scatto alla prima apparizione di un elemento (compilazione, prima cattura) | 8,5 |
| F7-e | memoria: layer e cache GPU non crescono con il numero di lastre oltre il bilancio | 8 |

**Rischi e ripieghi.** Un bilancio non si raggiunge senza toccare la resa: si porta ad Alessio un confronto «pieno / ridotto»
con il guadagno accanto, per i soli elementi coinvolti (per esempio dispersione, numero di pezzi fusi, altezza del bordo). Le
due GPU si comportano diversamente: i livelli di qualità sono per dispositivo, decisi dalla sonda di costo all'avvio.

**Scelte di gusto.** Solo se serve tagliare: quale delle rese ridotte è accettabile, elemento per elemento.

---

### F8 · Consegna

**Scopo.** Rendere il lavoro usabile da chi scrive un'app e da chi mantiene l'engine. **Entra**: schede e regole; tabella
SwiftUI ↔ Fluid; note di migrazione; catalogo delle API; galleria; pulizia del codice morto; licenze; changelog e versione;
S6. **Resta fuori**: la migrazione delle app (incarico a parte, da decidere con Alessio) e il rilascio sullo store.

**Progetto tecnico.**

- Schede: `ref/componenti/vetro.md` e `vetro-backdrop.md` riscritte attorno al materiale; `fluid-physics-motore.md`
  (il motore senza il suo disegno); `piano-flottante.md` (nuova); `barre.md`, `menu-liquidi.md`, `menu-modali.md`,
  `controlli.md`, `schermata.md`; `ref/regole.md` con le regole di 3.12; `ref/ricette.md` con le ricette «come si scrive per
  Apple»; `ref/limiti.md` (cosa Android non può: un'altra finestra, sotto API 33). `SKILL.md` e `ref/mappa.md` aggiornati.
- `ref/swiftui-fluid.md`: la tabella di 3.13 completata con un esempio a fronte per riga.
- `ref/migrazione-liquid.md`: cosa cambia da solo, cosa conviene cambiare (da `actions` a `toolbar`, da `barTint` a niente,
  da vetro sul contenuto a contenuto senza vetro), cosa è deprecato. **Icone**: come un'app passa al set
  (`FluidIcons.X` al posto di `Icons.Rounded.X`, `FluidIcon` al posto di `Icon` per seguire taglia e inchiostro, il
  generatore di 3.14 sul proprio elenco per le icone che il set non ha); la migrazione app per app non è di questa tornata.
  **Cambi visibili senza toccare codice**, da annunciare: icone delle azioni più grandi e monocrome, barre rientrate di
  8 dp per lato, titolo agganciato che si tronca 16 dp prima (`CompactTitleInset` 84 dp), tab bar più alta di 2 dp con
  icone a inchiostro pieno ed etichette a 11 sp (chi ha scritto a mano 76 dp di margine resta corto di 2).
- `skill/fluid-engine/scripts/gen_api_catalog.py`, `check_docs.py`, `sync_docs.py`; `CHANGELOG.md` con le voci **BREAKING** (nessuna firma
  rotta; i cambi d'aspetto sì, compresi quelli di icone e misure, che valgono anche sotto Android 13);
  `LICENSES/Haze.md` e `LICENSES/MaterialSymbols.md`; versione con `tools/engine-release.ps1`. Versione **4.0.0**
  (sezione 2), perché l'aspetto di ogni app cambia anche se il codice compila.
- Pulizia: le sorgenti e i renderer senza più chiamanti (quelli di F1B), i prototipi della pagina «Misure» che non servono a
  F7, le sonde del banco.

**Pacchetti.**

| id | cosa | perimetro di scrittura | dipende da | G/D | modello |
|---|---|---|---|---|---|
| F8.1 | Schede e regole | `ref/componenti/`, `ref/regole.md`, `ref/ricette.md`, `ref/limiti.md`, `SKILL.md`, `ref/mappa.md` | F7 | — | Opus |
| F8.2 | Tabella SwiftUI ↔ Fluid e note di migrazione | `ref/swiftui-fluid.md`, `ref/migrazione-liquid.md`, `ADOZIONE.md` | F7 | — | Sol |
| F8.3 | Catalogo, controlli della documentazione, changelog, licenze, versione: **ultimo** prima di S6, perché la pulizia può ancora togliere simboli e file | `ref/api/`, `docs/riferimento/`, `CHANGELOG.md`, `LICENSES/`, `ENGINE_VERSION` | F8.1, F8.2, F8.4, F8.5 | G | Haiku |
| F8.4 | Galleria: pagina «iOS in Android» che percorre tutto, giro in ogni combinazione | `sample/…/` **tranne** `sample/…/measure/` (che è di F8.5), `tools/qa/gallery-tour.sh` | F7 | G | Opus |
| F8.5 | Pulizia del codice morto e delle deprecazioni | `fp/FluidPhysicsRim.kt`, `fp/FluidPhysicsShadow.kt`, `fp/FluidPhysicsShaders.kt`, `sample/…/measure/`, `tools/glassbench/shaders/` | F7; non tocca i file di F8.4 e può girare insieme | G | Opus |
| F8.6 | **S6**: accettazione finale su tablet e telefono | `artifacts/liquid-glass/sessioni/S6.md`, `stato.md` | tutti | G, D | orchestratore + Opus |

**Risultati osservabili.** `check_docs.py` verde; il catalogo rigenerato; un'app di prova scritta solo con le API dichiarative
(una pagina con barra, ricerca, menu, foglio) che sta in una schermata di codice e non contiene un'animazione; il video
finale dal dispositivo.

**Come si verifica.** `gen_api_catalog.py --check`, `check_docs.py`; la galleria in ogni combinazione di tema, testo e
movimento ridotto senza crash; `compare` e tutti i test; lettura della tabella SwiftUI ↔ Fluid riga per riga contro R1 §4.

**Sottocategorie specifiche.**

| id | sottocategoria | minimo |
|---|---|---|
| F8-a | chi non ha seguito il progetto scrive una pagina «come per Apple» con la sola scheda (provato con un agente a contesto fresco) | 8,5 |
| F8-b | la tabella SwiftUI ↔ Fluid è completa e ogni riga compila | 8,5 |
| F8-c | niente codice morto, niente documentazione che nomina simboli spariti | 8,5 |
| F8-d | la copertura dell'inventario è vera: ogni voce «fatta» si trova nella galleria | 9 |

**Rischi e ripieghi.** La pulizia toglie qualcosa che `Balanced` o `Lite` usano ancora: si toglie solo ciò che il test di
copertura dei chiamanti dà a zero su tutti e tre i livelli.

**Scelte di gusto.** Nessuna di resa. Da decidere con Alessio: il numero di versione, quale app migrare per prima.

## 7. Copertura dell'inventario

La tabella voce per voce sta in [copertura-inventario.md](copertura-inventario.md): una riga per ogni voce numerata di R1
con la fase e il pacchetto che la realizza, oppure «c'è già» con il riferimento all'audit, oppure «non si fa» con il motivo.
Le voci numerate nella tabella riassuntiva di R1 sono **94**, non 95 (il conto di 95 comprende la riga senza numero
«Notifiche, Centro di controllo» della famiglia 3.15, che è in tabella anche lei). Riassunto per famiglia:

| famiglia | voci | si fanno | c'è già (con ritocco di materiale) | non si fanno | dove |
|---|---|---|---|---|---|
| M · materiale | 12 | 12 (M12 al minimo: il vetro recede solo in multi-finestra) | — | — | F1A, F1B, F2, F3.3, F4B.4, F6.5, F6.7 |
| C · contenitori e trasformazioni | 6 | 6 | — | — | F3, F4A |
| B · barre | 11 | 10 | 1 (B7 titolo) | — | F4A, F1A.11 e F2.3 (B5: icone del set, monocrome) |
| T · tab bar | 7 | 3 | 4 | — | F1B.4 (materiale e misure), F4B.3, F4B.4 |
| R · ricerca | 5 | 5 | — | — | F4B.2 |
| N · menu | 7 | 4 | 2 (N5, N6) | 1 (N7 menu di modifica del testo: è della piattaforma) | F5A |
| X · menu contestuale | 4 | 3 | 1 (X4 regole) | — | F5A.4 |
| P · fogli, avvisi, popover | 7 | 7 | — | — | F5B |
| K · controlli | 7 | 5 | 2 (K2 forma e taglie, K7 campi) | — | F1A.11, F6.3, F6.4, F6.6 |
| L · liste e contenuto | 3 | 1 | 2 | — | F6.6 |
| E · bordo dello scorrimento | 4 | 4 (con l'aspetto di iOS 26, per decisione) | — | — | F2.4, F2.5, F4B.4 |
| G · geometria | 3 | 3 | — | — | F3.5 |
| H · tocco e movimento | 6 | 4 | 2 (H5 molle come token, H6 aptica) | — | F6 |
| V · navigazione | 6 | 4 (V1 in parte c'è: si completa l'interruzione del push) | 2 (V2, V6) | — | F4A.3, F4A.4, F4A.6, F5B.3 |
| O · altro | 6 | 1 | — | 5 (tastiera, lente del testo, schermata di blocco, icone, player: di sistema o fuori dall'engine; ciò che insegnano è entrato altrove) | F5B.2 |

In tutto **72** voci si fanno, **16** ci sono già, **6** non si fanno (più la riga senza numero). Nessuna voce di priorità
alta è «non si fa». Le voci toccate da R5 portano nella tabella i suoi valori, oppure «a occhio» dove R5 non misura; in
fondo alla tabella stanno le voci di iOS 27 che R1 non aveva, e ciò che R6 aggiunge su icone e taglie.

**Le correzioni di R5 a R1**, già applicate qui e nella copertura:

- **V5**: il modificatore si chiama `toolbarMinimizationBehavior(_:for:)`; `toolbarMinimizeBehavior` era il nome della
  prima beta. La priorità non è più bassa: di serie accade in ogni pagina con la ricerca in posizione principale.
- **P4, P5**: avvisi e dialoghi «legati a un elemento» sono legati a un **dato** (`item:`), non a un elemento visivo:
  nessun cambio d'aspetto né di ancoraggio.
- **M11**: il cursore dell'utente sta in Impostazioni › Aspetto › Liquid Glass, non in Schermo (fonti secondarie).
- **E1**: «in basso sfuma senza sfocare» vale per iOS 26; per iOS 27 non c'è conferma.

## 8. Rischi

In ordine di quanto possono cambiare il piano.

| # | rischio | cosa lo fa scattare | quando si scopre | ripiego |
|---|---|---|---|---|
| 1 | **Il costo dei gesti**: ogni uniform del corpo rifà la sfocatura | misura (c): un cambio di soli uniform fuori dal fotogramma su capsula o pannello | S1 | corpo a due layer (3.5); se non basta, flessione e comparsa ridotte sui grandi |
| 2 | **Fluid-physics a `Full` non è mai stato visto**: difetti e costo ignoti | viaggi a `Full` con artefatti o fuori dal fotogramma | S1 | 3.8: anello solo per le sagome libere, viaggi a lastre con deformazione; i difetti del disegno muoiono in F1B |
| 3 | **Il banco inganna in positivo**: bande, P3, `half` a 16 bit, fili da 2 px | resa approvata sul PC che sul pannello ha bande o colori diversi | S2 | sgranatura minima nel velo; filo con `aa` proprio; la fase si riapre per un giro |
| 4 | **Il passaggio riposo↔palco si vede** | uno scalino nei primi o negli ultimi cinque fotogrammi di un viaggio | F3 (banco), S3 | palco permanente per i contenitori sottili |
| 5 | **Il bordo dello scorrimento su 2560 px** | misura (e) fuori dal fotogramma in un lancio | S1, poi S3 | tre sfocature native incrociate; rampa più corta |
| 6 | **Lo sfarfallio dell'adattività** | giro che oscilla su contenuto misto o a scacchi | S3 | giro solo a contenuto fermo; i grandi non girano comunque |
| 7 | **La goccia sul banco**: è a passo fisso, quindi sul PC dovrebbe essere riproducibile, ma nessuno l'ha ancora provato | in F0-a il collage del banco e la registrazione divergono oltre un fotogramma | F0 | se diverge, la goccia si giudica solo in sessione e B2 dei menu è provvisorio fino a S4; se coincide, il banco vale anche per lei |
| 8 | **La piega di luce sui ponti** | cresta visibile sull'asse del ponte con l'ombreggiatura del corpo | F1B (banco) | profilo dello spessore più dolce sui ponti; luce attenuata con `h` |
| 9 | **Dodici programmi da compilare** | scatto alla prima comparsa di un elemento | S1 (g) | riscaldamento all'avvio; varianti 4 e 8 fuse |
| 10 | **Regressioni nei componenti di oggi** (120 file di test in `engine-ui`, 53 nel `sample`, nessuno guarda l'aspetto) | un componente cambia aspetto senza che un test lo dica | ogni fase | `golden/classic/` (parità ottica sul PC) e `golden/liquid/`; selezione del percorso sotto test a ogni livello di API; conta dei nodi; `Classic` verbatim; livello simulato in S2 |
| 11 | **Driver che rifiuta una variante** (Adreno 740 contro 830, dispositivi delle app) | la sonda fallisce | S1, S5, poi sul campo | `Classic` per il processo, visibile in diagnostica e nei test |
| 12 | **R5 misura una beta, e non misura tutto**. I video sono di giugno 2026 (prima beta) e Apple ha già ritoccato l'estremo trasparente del cursore nella beta 5. Non sono misurati: tema scuro (un fermo immagine), ombra sopra il testo, raggio di sfocatura, menu e pannelli, riflessi su un tondo, soglia del giro, bordo in basso | la 27.0 definitiva si scosta dai numeri; i critici non hanno un numero per B1 in quelle voci | F1A (la più toccata: filo, velo, ombra e smerigliatura sono tutti di R5), poi F2, F4A, F4B | le voci non misurate sono dichiarate «a occhio» una per una nelle tabelle dei bersagli e si decidono con confronti in chat; i numeri stanno nei file di profilo, quindi un ritocco dopo un confronto con un dispositivo vero è di taratura, non di architettura |
| 13 | **Né R4 né R5 misurano ciò che decide il carattere** (forza della lente, raggio di sfocatura, molle, tema scuro; le misure in punti le dà R6 solo per tasti, icone, margini e tab bar) | i critici non hanno un numero su cui votare B1/B2 | F1A, F5A, F6 | quelle grandezze sono scelte di gusto dichiarate: decide Alessio sulle tavole, e la scelta scritta è il bersaglio |
| 14 | **Il contenuto deformato dalla lente** costa o si legge male | voci del menu che sfarfallano o fotogrammi lunghi | F5A (banco), S4 | deformazione solo nel primo tratto; poi sola sfocatura |
| 15 | **La tab bar vendored** (3399 righe) | la sostituzione di `gooey` rompe la piega | F4B.3 | `gooey` resta dietro un interruttore; decide Alessio sui due collage |
| 16 | **Gradle e RAM**: due posti condivisi, PC spesso saturo; le tracce chiedono Gradle | pacchetti in coda, build oltre i 30 minuti | sempre | un pacchetto «G» alla volta; lavoro sul banco senza Gradle; tracce prodotte in un giro solo per fase |
| 17 | **Le dimensioni**: dodici fasi, ognuna con tre critici e fino a tre giri | una fase che non chiude al terzo giro | ogni fase | si chiede ad Alessio (regola della sezione 5); F4B.4 e F6.5 sono i primi candidati a slittare senza bloccare il resto |
| 18 | **Licenze**: codice Haze e tracciati Material Symbols (Apache-2.0) in un engine GPL-3.0; numeri di LibrePods (GPL-3.0) cambiati | — | F0.8, F2.4, F6.1 | compatibile in questo verso; intestazione, avviso delle modifiche, `LICENSES/Haze.md`, `LICENSES/MaterialSymbols.md` con il commit di origine; niente dell'engine torna a una libreria Apache |
| 19 | **Compose 1.13** ha la sfocatura progressiva di piattaforma, ma in alfa e solo sul contenuto proprio | tentazione di dipenderne | — | il piano non dipende da un cambio di BOM (oggi 2026.03.00) |
| 20 | **L'angolo continuo nel campo**: il raccordo a superellisse approssima la curva dell'engine entro lo 0,5% del raggio (prova numerica, non sul pannello) | mezzo pixel di scarto visibile sui raggi grandi, o una mezzaluna fra filo e clip | F1A (banco a 6×), S2 | quelle superfici passano all'anello a riposo (3.7); al limite il filo dei grandi sul path esatto |
| 21 | **La sonda non si sveglia**: una sorgente che cambia senza ri-registrare e senza muoversi | lato e inchiostro restano quelli di prima sotto un vetro fermo | F2.1 (test dello schedulatore), S3 (prova «bianco → nero») | cadenza fissa e bassa per le sorgenti che non sono registrazioni; il ritardo massimo del giro è quella cadenza |
| 22 | **La luce si ridisegna a ogni fotogramma**: è un rettangolo pieno che valuta il campo per pixel, dove oggi l'anello è una registrazione | durante uno scorrimento la luce di molte lastre pesa più dell'anello di oggi | S1 (b), S2 | leva 1 di F7 (luce a riposo registrata una volta); uscita anticipata fuori dalla fascia del bordo, che c'è dal primo giorno |
| 23 | **`w` è una dissolvenza**, non una sfocatura intermedia | testo doppio per qualche fotogramma in comparsa e negli ultimi 100–150 ms di un viaggio | F3.3 (banco a 6×), S3 | curva di `w` più ripida; raggio che scende insieme a `w` solo in quel tratto |
| 24 | **Il palco sopra un contenuto che si muove**: la cattura tenuta si rinfresca ogni 110 ms | rifrazione a scatti durante un viaggio sopra una lista che scorre | S3 | ricattura a ogni fotogramma solo durante lo scorrimento |
| 25 | **Materiale di iOS 27 sopra il bordo dello scorrimento di iOS 26**: una coppia che Apple non ha mai mostrato | il filo scuro dei tasti sopra il contenuto dissolto pesa o stona | F2 (banco), S3 | variante con il filo attenuato dal velo locale; decide Alessio sulla tavola |
| 26 | **Icone e misure cambiano in ogni app**, anche sotto Android 13: icone più grandi e monocrome, barre rientrate, tab bar più alta. E finché un'app non migra, nella stessa barra convivono icone del set (peso 500) e icone sue (peso 400, piene) | un'app che aggiorna l'engine si ritrova titoli troncati prima, o due pesi d'icona affiancati | S2, poi nelle app | CHANGELOG e note di migrazione (F8); il set copre le icone che le app usano davvero (censimento di F0.8); l'etichetta della tab bar può tornare a 12 sp con una riga |
| 27 | **Il generatore delle icone dipende da un repo esterno** | il formato delle sorgenti di Material Symbols cambia, o un nome dell'elenco non esiste a peso 500 | F0.8, e a ogni rigenerazione | commit di origine fissato nello script; i file generati stanno nel repo e la build non tocca mai la rete; un nome mancante ferma il generatore con l'elenco di quelli mancanti |

## 9. Scostamenti dallo scheletro

**Divisioni e spostamenti.**

- **F1 divisa in F1A e F1B.** Lo scheletro metteva in una fase campo, corpo, luce, ombra, la migrazione di cinque ricette e
  la compatibilità: troppo per un giro di review, e le scelte di gusto sul materiale devono arrivare *prima* che lo si porti
  su tutto. F1A si chiude sul materiale a riposo, F1B sul motore unico e su S2.
- **F4 divisa in F4A e F4B, F5 in F5A e F5B.** Stesso motivo: barre in alto e fra pagine sono un lavoro, capsula, ricerca e
  tab bar un altro (file diversi, una libreria vendored); menu e contestuale sono i due esempi di Alessio e meritano un giro
  loro.
- **Geometria da F6 a F3** (pacchetto F3.5): le forme concentriche e le misure servono alle barre di F4 e ai fogli di F5B, ed
  è logica pura che può partire presto.
- **«Icone che si sfocano e si incrociano»** resta in F3 ma come pacchetto a sé (F3.4): è contenuto, non campo.
- **F0 non «corregge `flat`»**: è già corretto. F0 parte dal livello `Full` vero, lo registra, e costruisce il controllo
  permanente. Resta la correzione di `radiusAt`.
- **Icone e taglie (R6) prima di dove R6 e l'orchestratore le indicavano.** L'indicazione era: numeri in `FluidMetrics`
  (F3.5), un pacchetto prima di F4A per componente e set, la tab bar in F4B.3, `iconSize` in F6.4. Il piano mette il set e
  il generatore in **F0.8** (le tavole di F1A.5 si rendono con le icone vere), componente, adozione, taglie e margini dei
  tasti in **F1A.11**, le misure della tab bar in **F1B.4**: tutto prima di S2. Motivo: S2 è la prima volta in cui Alessio
  giudica il materiale sul dispositivo, e R6 ha misurato che l'impressione «sottile e piccolo» viene proprio da taglia,
  colore e margini delle icone; lasciarle com'erano fino a F4 avrebbe sporcato S2 e S3. Restano dove indicato: il passo
  nel gruppo e i 12 dp fra gruppi (F3.5 per i numeri, F4A.2 per l'uso), l'inchiostro adattivo (F2.3), l'icona dei menu
  (F5A.2, nel pacchetto che ha già quei file), `iconSize` e la taglia d'ambiente (F6.4). Il pacchetto «F4A.0» che una
  versione intermedia di questo piano nominava non esiste più.
- **V1 non è «c'è già»**: l'host delle pagine c'è, ma l'interruzione di un push salta. Nasce F4A.6.
- **Il tipo della ricerca nasce in F4A**, il comportamento in F4B: una fase non può compilare contro un tipo della
  successiva.
- **F7.1 prima di S5**: gli scenari di misura servono alla sessione che fissa i bilanci.

**Precisazioni di architettura rispetto allo scheletro.**

- «Le ricette diventano una, un modo di disegnare il bordo invece di cinque, un'ombra invece di tre»: vero per il modello
  `Liquid`. Il codice di oggi **resta** come modello `Classic`, perché la decisione «sotto Android 13 il vetro di oggi,
  intatto» lo impone e perché serve da ripiego per i driver e per l'orologio. Nel codice i modi restano più d'uno; a schermo,
  su Android 13+, uno.
- «La sagoma la ritaglia lo shader, non il layer, esteso a tutte le lastre»: lo shader calcola sempre la maschera, ma il
  clip hardware **resta acceso** dove la lastra non sporge mai (card, pannelli, fogli), perché è lui a ritagliare il
  contenuto figlio e costa zero (invariante 18). Si spegne solo sulle lastre che cedono o viaggiano.
- «Strato della luce: non dipende dallo sfondo, quindi si anima senza rifare la sfocatura»: vero a riposo. In viaggio la
  luce si calcola dentro il corpo, per non valutare il campo due volte.
- «Varianti dello shader a 1, 2, 4, 8 pezzi»: confermate, più un nucleo **ad anello** per la goccia e le sagome libere
  (3.8).
- «Una cattura per contenitore o per elemento»: né l'una né l'altra. Una per **campo** a riposo, un **palco** per
  trasformazione (3.7).
- **Sessioni**: lo scheletro dice che ogni fase grafica si chiude davvero solo sul tablet, e insieme «poche sessioni». Con sei
  sessioni per dodici fasi le fasi si chiudono «con riserva di dispositivo» e la riserva si scioglie alla sessione
  successiva (4.5).
- **Banco del movimento**: in più dello scheletro, `curve` dà un controllo numerico di B2 e `first` i primi cinque
  fotogrammi; e si dichiara che la goccia va convalidata in F0 contro una registrazione prima di fidarsi del banco.
- **Inventario**: le voci numerate di R1 sono 94.

**Proposte di modifica alle sezioni 1, 2 e 5.** Decise dall'orchestratore il 2026-11-10: **applicate** la 1, la
2, la 3, la 4, la 5 e la 8 (sezioni 2 e 5 aggiornate; in più: versione di arrivo 4.0.0, immagini di riferimento
tracciate, menu di modifica del testo fuori). La 6 (icone delle azioni monocrome) è **decisa** da Alessio il
2026-11-10, insieme al set di icone e alle taglie (sezione 2). **Restano scelte di Alessio**, da portargli come
confronti: la 7 (riconferma di goccia e rimbalzo dopo i collage di S1) e la 9 (crescita del tasto premuto, in F6).

1. Sezione 5, B1 e B2: aggiungere «e di R5 dove vale iOS 27», ora che la versione di riferimento è decisa.
2. Sezione 5: aggiungere lo stato «chiusa con riserva di dispositivo» e dire che un difetto trovato in sessione riapre la
   fase per un giro che non conta nei tre.
3. Sezione 5, B2: scrivere che dove R4 dà fiducia **bassa** o «a occhio» (molle, lente, scale dei controlli) il bersaglio è
   la scelta di gusto registrata, così i critici non votano contro un numero che non esiste.
4. Sezione 5, E2: nominare le immagini di riferimento del banco fra i test.
5. Sezione 2, «sotto resta il vetro di oggi, intatto»: dire esplicitamente che sotto Android 13 resta anche la **fascia in
   alto** (il bordo dello scorrimento nuovo chiede AGSL).
6. Sezione 2, tinta: la conseguenza è che le icone delle azioni, oggi del colore d'accento, diventano monocrome. È un cambio
   visibile in ogni app e vale una conferma esplicita.
7. Sezione 2, «goccia e rimbalzo come Apple»: la decisione è stata presa guardando il livello `Balanced`. Proposta:
   riconfermarla dopo i collage di S1.
8. Sezione 2: aggiungere che `engine-wear` resta sul modello `Classic` in questa tornata.
9. Sezione 2: la crescita del tasto premuto passa da ×1,5 (numeri di LibrePods) a ×1,2 (misura di R4) solo se Alessio lo
   sceglie sul confronto.

### Correzioni dopo il giro 0

Un giro solo, il 2026-11-10, dopo i tre critici (nessun Bloccante, sette Maggiori confermati), R5, R6 e le decisioni di
Alessio. «A», «B», «C» sono i critici (grafica, generalista, funzionale); il numero è quello del loro rilievo.

**Decisioni e ricerche arrivate dopo il giro 0.**

| cosa | cosa è cambiato | dove |
|---|---|---|
| bordo dello scorrimento con l'aspetto di iOS 26 | eccezione esplicita a «iOS 27 dove differisce»; la fascia uniforme di iOS 27 non si fa; `Automatic` e `Hard` sono quelli di iOS 26 | sezione 2, 3.10, 3.13, F2 (bersagli, rischi, scelte), copertura E1 ed E2 |
| materiale di iOS 27 | bersagli di R5; R4 dove R5 dichiara invariato e per il moto; «a occhio» voce per voce dove nessuno misura (tema scuro, ombra sul testo, raggio di sfocatura, lente, menu e pannelli, soglia del giro, bordo in basso, scheda prominente scelta) | sezione 2, 3.1, 3.4, 3.6, F1A (tabella dei bersagli), F2, F4A, F4B, F5A, F5B, F6 |
| icone monocrome, set al tratto a peso 500, taglie di R6 | 3.14 nuova; `FluidIcons` con il suo generatore, `FluidIcon`, `LocalFluidIconSize`, `FluidMetrics`; nessun ingrossamento automatico | sezione 2, 3.2, 3.13, 3.14, F0.8, F1A.11, F1B.4, F3.5, F4A.2, F5A.2, F6.4, F8, rischi 26 e 27 |
| luce dal movimento spenta di serie | `LocalFluidGlassMotionLight`, riga facoltativa nelle impostazioni d'aspetto, sensore registrato solo se accesa | sezione 2, 3.13, F6.5 |
| R5: ogni «bersaglio da R5» sostituito | valori e fiducia di R5 al posto del segnaposto; le correzioni di R5 a R1 riportate | 3.13, F1A, F2, F4A, F4B, F5B, sezione 7, rischio 12, copertura |

**I sette Maggiori.**

| rilievo | cosa è cambiato | dove |
|---|---|---|
| C1 (A1): i bersagli di F1A erano di iOS 26 | tabella dei bersagli riscritta su R5; tavole di gusto con le varianti «iOS 27 / iOS 26 / oggi»; F1A-b riscritta senza la forma del bordo di iOS 26; F1A nel rischio 12 | F1A, 4.6, sezione 8 |
| C2 (B R1): V1 non è «c'è già» | V1 «si fa, in parte c'è»; pacchetto F4A.6 che conserva posizione e velocità da push a gesto o pop; `FluidPageInterruptTest` scritto prima della correzione; voce di S4; sottocategoria F4A-f | F4A, 4.4, 4.5, sezione 7, copertura V1 |
| C3 (B R2, C9): la sonda non può dipendere dalla ricattura | nodo a sé, svegliato dalla revisione della sorgente (contatore su `LayerBackdrop`), dallo spostamento relativo e da una cadenza bassa; nessuna cattura in più; lato asincrono descritto; test dello schedulatore e prova «bianco → nero» a taglia e coordinate fisse | 3.9, F2 (progetto, F2.1, verifica), 4.4, S3, rischio 21 |
| C4 (B R3): ricerca fra F4A e F4B | tipo e contratto in F4A.1; parametro, scope e posa del campo di oggi in F4A.2 attraverso `FluidToolbarSearchSlot`; comportamento in F4B.2, che scrive solo quel file e il campo | 3.2, 3.13, F4A, F4B |
| C5 (B R4, A8, C4): `golden/classic` non prova il ripiego | separate la parità ottica di `Classic` sul PC e la verifica del percorso Android; `GlassModelSelectionTest` (JVM) e `GlassPathSelectionTest` (Robolectric, SDK 26, 31, 32, 35, `Classic` forzato, sonda fallita); livello simulato sul tablet in S2; la piattaforma vera sotto Android 13 dichiarata NV | 4.2, 4.4, 4.5, F1A.7, F1A.9, F1A-e |
| C6 (C1): il campo non conosce l'angolo continuo | raccordo `pieceSmooth` nel nucleo: superellisse adattata alla curva dell'engine (scarto ≤0,5% del raggio contro l'1,25% del cerchio nominale, prova in `artifacts/liquid-glass/bench/piano/angolo_continuo.py`), a riposo e in viaggio; regola forma → piano per i tre casi (una forma generica resta `Classic`); scena del banco e `ContinuousCornerFieldTest` | 3.2, 3.4, 3.7, F1A.1, 4.2, 4.4, rischio 20 |
| C7 (C2): il prototipo di S1 copiava una sonda a due ingressi | due sonde nuove a un ingresso (`sonda-via-a`, `sonda-luce`) montate come la catena della via A, a un layer e a due; scritto cosa del cancello C1 resta da misurare dopo S1 | F0 (progetto, F0.6, cancello C1) |

**I minori e i cosmetici accolti.**

| rilievo | cosa è cambiato | dove |
|---|---|---|
| A2: tavole e F1A-a su un grigio che non è la pagina | sei fondi: le due pagine vere (`#F2F2F7`, `#0D0D0F`), bianco e nero puri, testo, foto; F1A-a giudicata lì | 4.2, 4.6, F1A |
| A3, C4: numeri della scena ricopiati a mano | le scene del materiale nuovo escono dalla ricetta (`GlassSceneExportTest`); regola in 6.0 | 4.2, 6.0, F1A.5 |
| A4: smerigliatura misurata solo come contrasto | `measure` dà anche la nitidezza; a riposo `Regular` ha `w = 0`; «`w` è una dissolvenza» scritto con il suo rischio | 3.6, 4.2, F1A, F3, rischio 23 |
| A5: sei cose viste nei frame senza bersaglio | una riga ciascuna: capsula con i quattro lati accesi e gradiente diagonale (F1A, variante iOS 26 e «a occhio»), tasto tinto (F2), vetro che schiarisce nel cambio di contesto (F3), punto di luce sulla goccia (F5A), icona che si sfoca e cresce (F6) | F1A, F2, F3, F5A, F6 |
| A6: chiusura del menu a 200 ms contro una preferenza registrata (`ui/fluid/FluidMotion.kt:221`) | non è più uno scarto da chiudere: è una scelta di gusto di F5A, e fino alla scelta resta com'è | F5A |
| A7: ombra e filo fra riposo e viaggio | stessi profili di caduta nei due modi, scene di parità, doppio antialias del filo con il clip acceso in S1 (i) e S2 | 3.5, F1B, 4.5 |
| A9: ciò che vedono i critici | ritagli a 6× obbligatori; `last` accanto a `first`; tracce con interruzione; `pair` dai fotogrammi del video allineati su `t0` | 4.2, 4.3, F0.4, F1B, F3, F5A |
| A10: tre buchi di perimetro | blocchi vuoti di luce e ombra creati da F1A.1; `GlassBodyShader.kt` intero a F1B.1; un file di profilo ciascuno a F1B.3, F1B.4, F1B.5; dichiarato che le tracce «di oggi» di F0.5 sono una seconda descrizione, convalidata da F0-a | F1A, F1B, 4.3 |
| A11: ombra «più forte sul testo» dalla varianza di una miniatura | prova sul banco prima del codice (`probe_stats.py`), con ripiego scritto | F2 |
| A12: cosmetici | icone monocrome decise (non più «prima di F1A» contro «in F2»); la goccia non è più data per non convalidabile prima di F0; «+21…+53» al posto di «+40…+55»; riga per il tema scuro fra le scelte di F1A | sezione 9, rischio 7, F1A |
| A, rischi teorici | tavola finale «tutto insieme» in F1A; immagini di riferimento senza fotografie esterne al repo; `w` e palco fra i rischi | F1A, 4.2, rischi 23 e 24 |
| B R5: `FluidContextAction` con campi nuovi prima di `onClick` | costruttore secondario con la firma a sette parametri di oggi; il caso posizionale in `GlassCompatSignaturesTest`; detto cosa cambia della data class | 3.11, 3.13, F5A.3 |
| B R6: T7 senza un risultato esplicito | tab bar in alto sul tablet, tasto, conversione in barra laterale e ritorno come pacchetto, risultato e voce di S4; dichiarata la divergenza sulla barra laterale di iOS 27 | 3.13, F4B.4, 4.5, copertura T7 |
| B R7: manca la baseline 3.1.2 dell'S25 | APK di riferimento identificati e conservati in F0; in S5 prima la 3.1.2 e poi la build nuova; tre colonne distinte in F7 | F0, 4.5, F6.9, F7 |
| B R8: perimetri di F8 | F8.4 esclude `measure/`; F8.3 per ultimo, dopo F8.4 e F8.5 | F8 |
| B, P7: sei sessioni | scritte come programma iniziale | 6.0 |
| C3: tre test guardiani senza aggancio | tabella «dove gira e con quale aggancio»: contatori e verdetti pubblici in sola lettura (`FluidGlassDiagnostics`), `glassDrawInputs` come punto di osservazione della parità | 3.2, 4.4, F1A.7 |
| C5: la lente del contenuto senza proprietario sul banco | effetto a shader sul layer del contenuto nello schema delle tracce e nel banco da F0.3 e F0.4 | 4.2, 4.3, F5A |
| C6: la luce entrava nel materiale esportato | parametro `onDrawLight`, disegnato fuori dall'export | 3.3, 3.12, F1A.6 |
| C7: il colore dell'ombra non è una proprietà del layer | maschere in sola alfa, colore dal filtro del layer | 3.1, 3.5, F1A.4 |
| C8: una sagoma libera ferma non ha una variante di luce | `Poly` a riposo tiene la luce in corpo; tredicesimo programma solo se serve | 3.4, F1B |
| C10: `intensity`, `edge`, `falloff` nel ramo `Liquid` | tabella di traduzione; `FadeDown` resta `Classic` fino a F2.5 | 3.3, 3.11, F1A |
| C11: quattro incoerenze | F2-d riscritta e l'adattamento uniforme passato a F3-g; F3.3 dipende anche da F3.1; F2.4 marcato G; F7.1 prima di S5 | F2, F3, F7, 6.0 |
| C12: imprecisioni | la sonda di salute ha già due verdetti; versioni esatte in `requirements.txt` da F0.3 | F0, 4.2 |
| C, rischi teorici | la preferenza dell'utente ha una riga nelle impostazioni d'aspetto e il valore lo conserva l'app; tipi di configurazione `@Immutable`; ordine dei modificatori del vetro contrattato; costo della luce fra i rischi | 3.2, F2, F3.2, rischio 22 |
| C, P7: nessun totale in un punto solo | 83 pacchetti, 73 con Gradle, 6 sessioni | 6.0 |

**Non accolti, o accolti in altro modo.**

| rilievo | perché |
|---|---|
| A1, ultima proposta: portare ad Alessio la scelta fra bordo morbido e fascia uniforme prima di F2.4 | superata: Alessio ha già scelto il bordo di iOS 26 |
| C8, alternativa: un tredicesimo programma `GlassLightRing` per le sagome libere ferme | scelta l'altra strada (luce in corpo): le sagome libere ferme sono rare e un programma in più pesa su sonda di salute e riscaldamento; resta come ripiego scritto |
| C12 (c): chiedere delle icone monocrome «in F2» invece che «prima di F1A» | superato dalla decisione; e le icone arrivano anzi prima (F1A.11), per il motivo scritto negli scostamenti |
| A, rischio teorico: lo Skia del banco (m144) è più nuovo di quello di Android 13 | nessun cambio al banco: non esiste una ruota di `skia-python` allineata a un Android preciso. Lo ferma il test strumentato degli shader, a ogni sessione, e il lint delle sorgenti |
| A, rischio teorico: i critici non guardano i video | resta così: vedono collage densi, `first`, `last` e le curve. Uno scatto fra due fotogrammi a 29,97 fps passa fino alla sessione: è il senso della riserva di dispositivo |
| C, rischio teorico: sul tablet con `wm size` densità e gamut restano quelli del tablet | nessun cambio: è dichiarato in 4.5, e S5 sull'S25 è la prima verifica a densità di telefono |
| R6: pacchetto F4A.0 e tab bar in F4B.3 | accolto il contenuto, non il posto: vedi «Icone e taglie» negli scostamenti |
| R6: ingrossamento automatico degli `ImageVector` delle app | escluso da Alessio («solo B»): l'engine dà un set e un generatore, non ritocca le icone altrui |
