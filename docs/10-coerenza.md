# La coerenza della 3.0: ricette e matrice

Questo documento è il contratto della 3.0. Dice **come deve comportarsi ogni elemento dell'engine** —
cinque ricette — e **dove siamo** per ogni componente, in una matrice che si aggiorna a ogni fase. La
parte I della 3.0 (il nucleo) è finita quando ogni cella è ✓, o — con il motivo.

Il metro è la qualità della pillola della tab bar tenuta premuta, di `FluidSlider` e di `FluidSwitch`
(port di Kyant0/AndroidLiquidGlass), e dei tasti e della navigazione di LibrePods (studiati in
`docs/riferimenti-3.0.md`, portati sotto GPL-3.0).

## Le dimensioni

| | Dimensione | Domanda |
|---|---|---|
| D1 | Materiale | È una lastra o una goccia? Ottica, bordo, ombra, tinta e taglia vengono dalla ricetta della sua famiglia, mai da numeri propri? |
| D2 | Dito | Pressione, luce del dito, trascinamento e rilascio sono quelli della sua ricetta? |
| D3 | Movimento | Solo token `FluidMotion`; ciò che segue un gesto parte con la velocità del dito; niente rincorse; deformazioni solo dalla velocità; contenuto attaccato alla superficie? |
| D4 | Aptica | Una vibrazione per momento (salvo la coreografia dei tasti), solo dal vocabolario? |
| D5 | Gesto | È chiaro chi possiede il dito, e la pagina si comporta come la ricetta dice? |
| D6 | Arrivi e uscite | Continuità: niente comparse secche, niente lampi di un fotogramma, pagine opache? |
| D7 | Accessibilità | TalkBack, tastiera e fuoco, animazioni ridotte, testo a 1,3×, contrasto? |
| D8 | Temi e ripiego | Chiaro, scuro, AMOLED, accenti; senza vetro (API < 31 o nessun fondale) una superficie corretta? |
| D9 | Costo | A schermo fermo nessuna ricattura; nessuna regressione sugli scenari di `tools/qa/`? |

Legenda: **✓** conforme, con la prova nella colonna delle note · **✗n** da fare nella fase *n* del
piano · **—** non si applica · **?** da verificare (è un "non lo sappiamo", non un "va bene").

## Le ricette

### La lastra
Pannelli, barre, card, gruppi di vetro, menu, tasti di vetro a riposo. È `glassSurface`,
`glassControlSurface` o `fluidPhysicsSurface`.
- **Ottica** dal ruolo (`GlassRole`) con la regola della taglia (`glassSizedOptics`): mai sfocatura o
  lente scritte a mano. I pannelli flottanti (popover, menu contestuale, finestra che si espande,
  foglio, anteprima sollevata, menu che si trasforma, notifica, callout) hanno **un solo materiale**:
  `GlassDefaults.panelOptics(compact, overSharpPage)` + `panelTint()` (fase 3, `GlassPanelMaterialTest`).
  `FluidAlert` nell'host è un pannello come gli altri (senza host, o con `inHost = false`, resta il
  `Dialog` che dipinge il suo vetro); la bolla dell'indice ha la pellicola del pannello ma ancora l'ottica
  del ruolo `Modal`: sono due eccezioni, e restano nella matrice finché non si chiudono.
- **Bordo**: uno solo, `glassRimRecipe(darkSide)` — chiaro e in `Plus` sul lato scuro, scuro (nero al
  28%) e in `SrcOver` sul lato chiaro, col profilo dello shader (pieno sopra e sotto, zero ai fianchi).
  Anche `fluidPhysicsSurface`, che lo disegna con `glassRimRamp` (fase 3, `GlassRimRecipeTest`).
- **Ombra** del ruolo, disegnata dal vetro (`glassShadowColor`: nero al 16% sul chiaro, al 10% sullo
  scuro; `glassShadow` per chi non può disegnarla nel proprio pannello, `fluidPhysicsShadow` per la
  finestra in viaggio); mai un'ombra di piattaforma (`Modifier.shadow`) sotto il vetro. Resta di
  piattaforma dove il vetro non c'è: la barra senza fondale, la banda opaca.
- **Niente ombra interna**: con un bordo speculare si leggono due bordi.
- Chiaro o scuro si decide solo con `GlassDefaults.isDarkSurface()`.

### La goccia
Ciò che si afferra e scorre: pomelli di slider e interruttore, pillola della tab bar, pillola del
segmentato, indicatore del rail, lente dell'indice, goccia dei menu, goccia dei pulsanti degli avvisi.
Ricetta di Kyant (`glass/interaction/GlassDrop.kt`, fase 3):
- **a riposo piena** (bianca nel chiaro; per il segmentato e il rail, il riempimento del ruolo), sotto il
  dito **diventa lente**: sfocatura `8 dp × (1 − p)`, lente `10 dp × p` / `14 dp × p` con dispersione,
  `Highlight.Ambient` ÷ 1,5 con alpha `p`, ombra interna `4 dp × p` solo premuta, ombra fissa 4 dp nero 5%;
- **molle** di `GlassDragAnimation`: valore (1, 1000), pressione (1, 1000), scala X (0,6, 250), scala Y
  (0,7, 250), velocità (0,5, 300);
- **squash dalla velocità**, assoluta, `/10`, tetto 0,2, spento con le animazioni ridotte
  (`glassDropSquash`; `GlassDropSquashTest` lo confronta bit per bit con come stava scritto nei tre
  posti che lo avevano). **Tre eccezioni note, tutte per il metro** (R1: slider, interruttore e pillola
  cambiano solo verso i riferimenti, mai allontanandosene): l'interruttore conserva i numeri del port
  (con segno, `/50`); la pillola della tab bar conserva lo squash **con segno**, `/10`; e lo squash
  dell'interruttore e della pillola **non guarda le animazioni ridotte**, come prima;
- **aptica**: un tick per tacca vera (scheda, segmento, voce, metà dell'interruttore, aggancio dello
  slider), mai durante il moto libero.

### Il tasto
Tutto ciò che cresce sotto il dito (`FluidPressFeedback.Expand`): tasti, tasti di vetro, icone, chip,
azioni della barra, ancore dei menu. Ricetta di LibrePods (fase 4, GPL-3.0):
- **pressione al contatto**, nessun ritardo; il tasto **non si prende mai il gesto**: se il dito scorre,
  la pagina scorre e il tasto reagisce insieme;
- crescita `lerp(1, 1 + maxScale, p)` (tondi ×1,5), molla di pressione (0,5, 300), elastico
  `maxOffset · tanh(0,05 · offset / maxOffset)` con `maxOffset` = lato corto e ritorno (1, 300),
  allungamento direzionale; luce del dito grande (raggio = lato lungo); sul vetro lente 12/24 dp con
  profondità e dispersione. Le molle sono **costanti di LibrePods** in `FluidPressSprings`, non token di
  `FluidMotion`: è la ricetta, e per i tasti D3 vuol dire "come la ricetta";
- **aptica a coreografia**, con le costanti di sistema di LibrePods: `SegmentFrequentTick` al contatto e
  trascinando veloce, `Reject` al rilascio, `ContextClick` al click. È l'unica eccezione alla regola
  "una vibrazione per momento", ed è voluta;
- click lungo dove serve (menu): dito fermo entro lo slop per il tempo del click lungo, poi niente click;
  il tenuto premuto apre il menu e **gli passa il dito** (`FluidMenuHandOff`: si sceglie trascinando e
  rilasciando, e un rilascio altrove lascia il menu aperto); il colpo del click lungo è `longClickHaptic`,
  `null` se lo suona il menu;
- il tasto di vetro ha una **taglia** (`FluidGlassSize`: 40, 44, 54 dp) e la ricetta vale in
  proporzione: il bersaglio del tocco non scende mai sotto i 48 dp.

### La pagina
Le transizioni fra destinazioni e il gesto indietro (fase 8: `FluidPageHost`, `FluidNavHost`).
- **Opaca**: niente sfocatura, niente trasparenza, mai due pagine leggibili sovrapposte (fa eccezione il
  passo fra due schede, dove solo la vecchia sfuma).
- **Apertura** da destra; la pagina sotto arretra in parallasse di −⅓ della larghezza.
- **Indietro stile iOS (default)**: la pagina segue il dito **1:1 sulla posizione vera** (`touchX`, non
  il `progress` del gesto di sistema, che è distorto: passato per un easing faceva correre la pagina fino
  a 2,5× il dito); conferma oltre il 35% della larghezza (con un tetto di 180 dp) o con una spinta di
  400 dp/s, e una spinta contraria annulla anche oltre la distanza; la molla di uscita o di ritorno
  parte dalla velocità del dito, criticamente smorzata. **Stile "rimpicciolisce e scivola"**
  (`ShrinkSlide`) per le pagine che lo chiedono; `Expand` si apre dal punto toccato.
- **La pagina è una carta**: angoli del telefono (dove l'host tocca un angolo dello schermo) e ombra da
  36 dp mentre si muove; a riposo il layer non ritaglia né proietta.
- **La barra è ancorata** *(non ancora costruita, passo 5)*: tasto indietro fermo al pixel, azioni che
  si trasformano col vetro, titolo che sfuma; segue lo stesso progresso del gesto.

### Il pannello in movimento
Le forme di vetro che si trasformano (Fluid-physics: card che si espande, menu, Playground).
- **A riposo è una lastra** e si disegna come tale (stesso renderer): nessuna differenza misurabile.
- In viaggio: ottica, tinta, bordo e ombra **sfumano** dal mittente al destinatario; il rimbalzo è un
  gonfiore uniforme (`overshootInflation`), mai uno stiramento lungo il viaggio; le molle partono dalla
  velocità del gesto, **niente rincorsa**; il contenuto segue la sagoma.
- Il viaggio si può invertire a metà dal punto in cui si trova; **un viaggio cancellato si ferma dove si
  trova** invece di saltare al traguardo, e il successivo parte da quella forma
  (`FluidPhysicsCancelTest`).
- Mai un layer scalato su un vetro: la scala sta nella geometria o nel `layerBlock` di `drawBackdrop`.
- **I menu sono questa ricetta con un dito** (fase 7): un pannello (`FluidGlassMenuPanel`), un viaggio
  (`FluidMenuJourney`: parte dal tasto *premuto*, gonfiore uniforme attorno al centro del pannello,
  inversione dal punto in cui si trova con la sua velocità), un materiale (`panelOptics(compact)`, la pelle
  e la lente passano dal tasto con `opticsFrom`). L'apertura è sincrona e l'ancora si nasconde **nel
  disegno** (`hidden`), mai con l'alpha di un layer sopra il vetro; il contestuale cresce da un seme per
  geometria, e la riga sollevata è una superficie Fluid-physics.

### L'aptica, riassunta
Vocabolario `FluidHaptics`; una vibrazione per momento (rete dei 70 ms); `Open` un colpo solo; la grana
sui tasti trascinati veloci; i tick per le tacche vere; la coreografia dei tasti come sopra. **Un menu**:
`Open` solo se si apre, un `Tick` per voce attraversata, un `Tap` alla scelta, niente al congedo; lo stesso
per i pulsanti di un `FluidAlert` nell'host. `FluidHapticTrace` (3.0) è lo strumento per verificarlo: un
decoratore che scrive cosa è stato chiesto al vibratore, con l'esito stimato delle due reti. Nessun
`LocalHapticFeedback`/`Vibrator` fuori da `haptics/`; per il codice che non è Compose c'è
`fluidHapticsFor(context)`.

## La matrice

Stato al 2026-11-02, dall'audit del codice 2.13.1 più i commit della 3.0 fino a `cef79a2` (i menu liquidi, fase 7, e Fluid-physics rimodellato, fase 6). Le note dicono
cosa prova una cella ✓ e cosa manca alle altre; il numero è la fase del piano.

**Regola per leggere una ✓.** Una funzione condivisa provata da un test (`fluidPressable`,
`glassControlSurface`, `glassDrop`, `panelOptics`) vale per chi la chiama senza numeri propri, e la
cella lo dice ("via …"); quello che solo l'occhio o la mano possono dire — ombre, sbiancamento del
vetro, un lampo di un fotogramma — è **?** finché non si guarda. Nessuna ✓ è "per costruzione".

**Il conto** (2026-11-02, 57 componenti, 513 celle): 189 ✓, 41 ✗ (fase 3: 8, 5: 3, 8: 11, 9: 19),
198 ?, 85 —. Prima dei menu e di Fluid-physics erano 171 ✓, 84 ✗ (fase 3: 8, 5: 5, 6: 4, 7: 37, 8: 11, 9: 19),
173 ?: le 37 ✗ della fase 7, le 4 della fase 6 e due della fase 5 (il colpo `Open` che il layer dei modali
sommava a quello di chi apriva, tolto) sono chiuse, **18 diventano ✓** perché c'è un test che le prova e
**25 diventano ?** perché il lavoro è fatto ma lo dice solo l'occhio o la mano — il vetro (gli host dei
test non hanno fondale), il cambio di proprietario fra tasto e finestra, l'arrivo di un avviso, la
tastiera sopra un campo nell'host, ogni fotogramma. Il 31 ottobre, all'audit del 2.13.1, erano 116 ✓, 120 ✗, 134 ? su 414, in 46 righe. Le otto ✗ della fase 3
che restano sono le molle scritte a mano da portare a token (pillola, segmentato, rail, pannello e banda
della tab bar, barra laterale) e l'ottica della bolla dell'indice: la fase 3 ha chiuso materiale, bordo,
ombra e goccia, non quelle.

### Tasti

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidButton` | — | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | `FluidPressTest` (crescita 0,10, molle 0,5/300, deriva `tanh`, rilascio immediato), `FluidButtonDragTest` (la passata scorre, un tap fa tre colpi); molle di LibrePods, non token |
| `FluidGlassButton` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `GlowRecipeTest` (luce di LibrePods), `FluidPressTest`, `FluidButtonDragTest` (tasto di vetro trascinato: scorre la lista); taglie e bersaglio da 48 dp: `FluidGlassSizeTest`, `FluidGapsTest`; su vetro chiaro la luce additiva può sbiancare la faccia: da guardare (D8) |
| `FluidGlassIconButton` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ×1,5: `FluidPressTest`; taglie 40/44/54: `FluidGlassSizeTest`; `haptic = null` per chi ha già il suo colpo |
| `FluidChip` | ✗9 | ✓ | ✗9 | ✓ | ✓ | ✓ | ? | ? | ? | ricetta del tasto via `fluidPressable(Expand)` (provata su `FluidButton`); opaco → tasto di vetro |
| `FluidBarAction` / indietro | ✓ | ✓ | ✗8 | ✓ | ✓ | ? | ? | ? | ? | via `FluidGlassIconButton`; con `actions` è l'ancora del menu `Expand` (`FluidMenuLayer`, il pannello unico): si apre sincrono, il tasto si nasconde nel disegno e non sfuma più; un `Open` solo, e un rilascio fermo lascia il menu aperto: `FluidMenuHandOffTest` (azione della barra, `PressDown` `Open` `Tap`); `opensOn = Tap` non ha test, solo la demo; il cambio di proprietario fra tasto e finestra non è stato guardato (D6); D3 resta ✗8 per indietro e le azioni dentro la barra ancorata |
| ancora del menu (`FluidMorphMenuButton`) | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | `FluidButtonDragTest`: il tenuto premuto apre senza click, il tap clicca (`PressClick`) e non apre; `FluidMenuHandOffTest`: tieni, trascina e rilascia dà `PressDown` `Open` `Tick` `Tap` e nessuna grana, un rilascio fermo, fuori dalle voci o su una voce spenta lascia aperto, il back a metà apertura chiude in silenzio; parte dal tasto premuto e si inverte dal punto in cui si trova: `FluidMenuJourneyTest`; apertura sincrona senza fotogramma morto: per costruzione, nessun fotogramma guardato (D6); `opensOnTap` senza test |
| `FluidGlassMenuButton` (deprecato) | ✓ | ✓ | ✓ | ? | ✓ | ? | ? | ? | ? | passa dal pannello, dal viaggio e dal dito dei menu nuovi (`FluidMenuLayer`, `FluidMenuAnchor`: provati dagli altri ingressi), ma ha un'apertura sua nel codice — l'`Open` dal click lungo — che nessun test chiama |

### Gocce

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| pomello di `FluidSlider` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; legge la goccia condivisa (`glassDrop`), squash bit per bit: `GlassDropSquashTest` |
| pomello di `FluidSwitch` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; squash con segno /50 e fuori dalle animazioni ridotte: eccezioni note (R1); `GlassDropSquashTest` |
| pillola della tab bar flottante | ✓ | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | squash con segno /10 dalla goccia condivisa, fuori dalle animazioni ridotte: eccezione nota (R1), `GlassDropSquashTest`; resta una molla scritta a mano, `spring(1f, 300f, 0.5f)` del ritorno dell'offset → token |
| pillola di `FluidSegmentedControl` | ✗9 | ✗9 | ✗3 | ✗5 | ✓ | ✓ | ? | ? | ? | lastra Interactive 8/8 → goccia; nessun tick per segmento; chiaro/scuro con `isDarkSurface()` (nessun test) |
| indicatore di `FluidTabRail` | ✗9 | ✗9 | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | senza lente; l'ombra interna dichiarata non si disegna (commento corretto) |
| lente di `FluidSectionIndex` | ✗9 | ✗9 | ✓ | ✗5 | ✓ | ✓ | ? | ? | ? | tick al primo evento |
| goccia dei menu | ✓ | ✓ | ? | ✓ | ✓ | ? | ? | ? | ? | `FluidGlassMenuPanel`: la ricetta di `glassDrop` coi numeri del pomello dello slider (`GlassDropRecipe`, `glassDropSquash`: `GlassDropSquashTest`), una per pannello, che campiona il materiale del pannello con tutte le trasformazioni nel proprio `layerBlock`; `FluidMenuSelectionTest` (23: il dito decide, la voce spenta non fa tick, un rilascio fermo non sceglie), `FluidMenuPanelTest` (15: celle, sezioni, la banda non sta sotto il dito), `FluidMenuHandOffTest` (un `Tick` per voce, mai la grana); la goccia sul vetro, il suo squash e la conferma bianca prima di andarsene non sono stati guardati; senza fondale restano la scala della voce e i separatori che si spengono |

### Lastre e chrome

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| barra superiore (`FluidScreen`, `FluidCollapsingTopBar`) | ✓ | — | ✓ | — | ✓ | ✗8 | ? | ? | ✓ | viaggia con la pagina → ancorata (passo 5) |
| pannello della tab bar flottante | ? | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | ombra di vetro (`glassShadow`) al posto di quella di piattaforma, nessun test: da guardare; resta di piattaforma senza fondale e per la banda opaca; molle scritte a mano |
| banda accessoria della tab bar | ✗9 | — | ✗3 | — | ✓ | ✓ | ? | ? | ? | opaca |
| cerchi autonomi / scheda inline della tab bar | ✓ | ✗9 | ✓ | ✗5 | ✓ | ✓ | ? | ? | ? | ripple Material; nessuna aptica al tocco |
| `FluidSidebar` | ✓ | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | colore animato con spec di default |
| pannello di `FluidTabRail` | ✓ | — | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | molle scritte a mano |
| `FluidListGroup` / `FluidCard` / `FluidMetricTile` (glass) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | |
| `fluidGlassGroups` / `FluidGlassGroup` | ✓ | — | — | — | — | ? | ? | ? | ? | `FluidGlassGroupsTest` (il piano: pezzi, giunture, tetto di otto righe), `FluidGapsTest`; il taglio invisibile e il costo si guardano in mano |
| `FluidContainerScaffold` | ✗9 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | opaco sul fondale ambientale |
| `FluidSyncIndicator` (nella barra) | ✗9 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | cerchio opaco |
| `FluidFloatingCapsule` | ✓ | — | ✓ | ✓ | ✓ | ? | ? | ? | ? | `glassSurface` ruolo `Floating` con la pellicola della barra (`fluidFloatingGlass`, nessun numero proprio, letto nel codice); `FluidFloatingCapsuleTest` (margini e inset), `FluidCapsulesAndPartsTest` (azione che clicca col colpo del tasto, capsula nascosta, tocchi trattenuti); entrata e uscita con token `FluidMotion`, nessun fotogramma guardato |
| `FluidCapsuleAction` | — | ✓ | ✓ | ✓ | ✓ | — | ? | ? | ? | via `fluidPressable(Expand)`; `FluidCapsulesAndPartsTest` (clicca, disattivata non clicca e lo dice); nessuna lente per azione, ma non misurato |
| `FluidSearchField` | ✓ | ✓ | ✓ | ✓ | — | ? | ? | ? | ? | stessa pellicola della capsula; `FluidSearchFieldTest` (contatore, larghezza, dissolvenza), `FluidCapsulesAndPartsTest` (il cerchio apre col colpo di `Open`, fuoco alla comparsa, contatore e frecce, "nessun risultato"); l'apertura da un cerchio non è stata guardata fotogramma per fotogramma |

### Pannelli, modali e menu

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| menu che si trasforma (host) | ? | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | lastra col materiale del pannello (`panelOptics(compact)`: `GlassPanelMaterialTest`, `FluidPhysicsMaterialTest`) e pelle e lente che passano dal tasto (`opticsFrom`); ombra e bordo di una lastra a riposo: da guardare (D1); viaggio: `FluidMenuJourneyTest` (11: parte dal tasto premuto, gonfiore attorno al centro del pannello, un back a 100 ms torna indietro con la sua velocità, animazioni ridotte senza viaggio), `FluidMenuMotionTest` (21: i token, le finestre del contenuto), `MorphOvershootTest`; dito e aptica: `FluidMenuHandOffTest`; apertura sincrona senza fotogramma morto: per costruzione, i fotogrammi non sono stati guardati (D6); gli shader della frangia sull'emulatore: nessun errore AGSL (beta 1, `artifacts/beta1/`) |
| popover (`FluidGlassModalPortal`) | ✓ | — | ? | ? | ✓ | ? | ? | ? | ? | `panelOptics` + `panelTint` (`GlassPanelMaterialTest`); dalla 3.0 passa dalla finestra di Fluid-physics e cresce da un seme, senza layer scalato (era la trappola del pannello classico): è nel codice, e nessun test lo vede — gli host dei test non hanno fondale e ripiegano sul pannello opaco; il colpo del layer (`Open`) è tolto, nessun test lo conta |
| finestra che si espande (card) | ? | — | ✓ | ? | ✓ | ✓ | ? | ? | ? | `panelOptics` e ombra di vetro (`fluidPhysicsShadow`, ~6× più leggera di prima: da giudicare in mano); il rimbalzo è il gonfiore uniforme coi numeri della rincorsa di prima (`FluidMenuMotionTest`: `theInflationEqualsTheOldModalValuesForTheOldNumbers`) e `MorphOvershootTest`; il layer non suona più `Open` (resta il colpo di chi apre), nessun test lo conta |
| menu contestuale + riga sollevata | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | `panelOptics(compact)` e anteprima sollevata dal pannello unico (`GlassPanelMaterialTest`); cresce accanto alla riga da un seme sul perno più vicino e mai sopra (`FluidMenuJourneyTest`: `aContextMenuGrowsFromItsSeed`; `FluidMenuPanelTest`: il seme è il pannello scalato attorno al perno), e la riga sollevata è una superficie Fluid-physics sorella dell'immagine registrata: nessun layer scalato sul vetro; `FluidMenuHandOffTest`: una riga in una `LazyColumn` si sceglie trascinando, la lista resta ferma, `Open` `Tick` `Tap`; `fluidContextMenu`: tieni, rilascia e tocca, `Open` `Tap`; un tocco fuori chiude in silenzio; lo scambio fra la riga e la sua immagine sollevata, e la goccia sul vetro, non sono stati guardati (D6) |
| foglio di vetro | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `panelOptics` + `panelTint` (`GlassPanelMaterialTest`) |
| `FluidSheetScaffold` / `FluidConfirmSheet` | — | — | — | — | ✓ | ? | ? | ? | — | `FluidSheetScaffoldLayoutTest` (il corpo prende quello che resta, i tasti si misurano per primi), `FluidRowsAndSheetsTest` (i tasti restano in vista sotto un corpo più lungo del foglio, il corpo scorre; conferma e annulla sono due cose); leggibilità del testo sul vetro e parole congelate in uscita: da guardare |
| modale a schermo intero | — | — | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | tasto di chiusura opaco (9) |
| scrim | ✓ | — | ✓ | — | ✓ | ✓ | ? | ? | ? | |
| `FluidAlert` | ? | ? | ? | ✓ | ✓ | ? | ? | ? | ? | nell'host quando c'è (`inHost`): vetro vero e `panelOptics` nel codice, nessun test ne vede il vetro (gli host dei test non hanno fondale); `FluidMenuHandOffTest`: sta nell'host senza finestre di piattaforma, esce anche dopo che il chiamante l'ha tolto (con `FluidGlassModalStateTest`: `retire` e `finishExit`), un pulsante sceglie con un `Tap` solo; l'arrivo che si posa e il trascinamento fra i pulsanti, con la goccia, non hanno test; senza host o con `inHost = false` è il `Dialog` di prima (vetro dipinto, nessuna uscita) |
| `FluidPromptAlert` | ? | — | ? | ? | — | ? | ? | ? | — | è un `FluidAlert` (ci sta dentro, quindi nell'host); `FluidPromptAlertTest` (validazione a tre stati, testo ritagliato), `FluidRowsAndSheetsTest` (il tasto è la validazione, Invio conferma, si apre sul campo con il testo selezionato); la tastiera che sale sopra un campo nell'host non è provata (Robolectric non la porta) |
| `FluidSheet` (deprecato) | ✗9 | — | — | — | ✓ | ✓ | ? | ? | ? | delega al foglio di vetro |
| `FluidNotification` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `panelOptics(overSharpPage)` al posto della lente `Modal` (`GlassPanelMaterialTest`) |
| `FluidCoachMark` | ✓ | ✗9 | ✗9 | ✓ | ✓ | ✗9 | ? | ? | ? | pannello da `panelOptics(overSharpPage)`; `scaleIn` sul vetro; freccia e chiusura opache |
| bolla dell'indice di sezione | ✗3 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | pellicola del pannello (`panelTint`), ma ottica ancora quella del ruolo `Modal` |

### Pagine e movimento

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| host di pagine, transizioni di rotta (`FluidPageHost`, `FluidNavHost`) | — | — | ✓ | — | ? | ? | ? | — | ? | `FluidPageMotionTest` (molle critiche, nessun buco fra la pagina sotto e quella sopra, il davanti passa a metà), `FluidPageHostTest` (la molla parte con la velocità del dito, RTL, animazioni ridotte); opaca per costruzione, nessun fotogramma guardato; scorrimenti annidati e due host non provati; il vecchio `FluidRouteMotionHost` sfoca ancora 6 dp l'uscita |
| gesto indietro | — | ✓ | ✓ | — | ? | ? | ? | — | ? | `FluidPageHostTest`: dal bordo e da un punto qualunque la pagina sta sotto il dito a ogni fotogramma, il back di sistema con `progress` storto segue `touchX`, rilasci lenti sotto e sopra il 35%, spinte nei due versi; nessuna vibrazione nel gesto; chi possiede il dito con uno scorrimento annidato: non provato |
| barra ancorata (nuova) | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | non esiste ancora (passo 5, dopo le fasi 3 e 6) |
| superficie Fluid-physics (Playground) | ? | ? | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | a riposo è una lastra: `physicsLensUniforms` rifà i numeri di `glassSurface` per ogni ruolo, taglia, densità, qualità e pressione (`FluidPhysicsMaterialTest`), bordo di `glassRimRecipe` (`GlassRimRecipeTest`); ombra e bordo a riposo: da guardare; una molla sola che parte alla velocità del momento, la rincorsa è il preset `House` (`FluidPhysicsJourneyTest`), oltrepasso = gonfiore uniforme (`MorphOvershootTest`), un viaggio cancellato si ferma dove si trova (`FluidPhysicsCancelTest`); gli shader della frangia sull'emulatore: nessun errore AGSL (beta 1, `artifacts/beta1/`); il pezzo che si tocca non ha test |
| scorrimento ed elastico (`FluidScrollPhysics`) | — | ✓ | ? | — | ✓ | — | ? | — | ? | da verificare contro la ricetta (10) |
| pull-to-refresh | — | ? | ? | ? | ✓ | ? | ? | — | ? | (10) |

### Contenuto e altro

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidListRow` | — | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | cede, non cresce: resta così; con `onClick` forse due nodi semantici (il `ListItem` fonde da sé); `leadingTile` e `FluidToneTile` pubblica (`FluidGapsTest`); con `contextActions` il click lungo suona `Open` se il menu si apre, o il `GestureStart` dell'app se no (`FluidMenuHandOffTest`) |
| `FluidToggleRow` | — | ✓ | — | ✓ | ✓ | — | ? | ? | — | `FluidRowsAndSheetsTest`: un tocco commuta una volta e vibra come l'interruttore (solo `ToggleOn`/`ToggleOff`), il tocco sul pomello commuta una volta sola, disattivata non risponde; per TalkBack un nodo solo, `Role.Switch`; testo a 1,3× non provato |
| `FluidChoiceRow` | — | ✓ | — | ✓ | ✓ | — | ? | ? | — | `FluidRowsAndSheetsTest`: un radio per TalkBack, `Tick` solo se la scelta si sposta, la riga già scelta avvisa senza vibrare |
| `fluidAppearanceSection` / `FluidAccentPicker` | — | — | — | ✓ | — | — | ? | ✓ | — | `FluidAppearanceTest` (18: AMOLED = Scuro + nero, `DYNAMIC` senza supporto è il marchio, la prova dell'aptica solo da spenta ad accesa), `FluidAppearanceSectionTest` (9: scrive solo se cambia qualcosa, il nero non risponde nel chiaro) |
| `FluidInlineMessage` | — | ✓ | — | — | — | — | ? | ? | — | cede come ogni superficie; `FluidGapsTest`: con `onClick` è un pulsante e mostra la sua azione, senza non finge |
| `FluidTextField` | — | ? | ? | ? | ✓ | ? | ? | ? | ? | pozzetto opaco sul vetro per scelta; slot a destra da 24 dp e `secret` (`FluidGapsTest`: password finché non si tocca l'occhio) |
| `FluidProgressBar` / `FluidSpinner` / indeterminata | — | — | ? | — | — | ? | ? | ✓ | ? | `FluidFillContrastTest`: ogni accento della palette si legge sulla pista, su ogni superficie, almeno 3:1 |
| `FluidEmptyState` / `FluidLoading` | — | — | ? | — | — | ? | ? | ? | ? | `FluidEmptyState(illustration)`: decorazione muta (`FluidGapsTest`) |
| pezzi piccoli: `FluidLiveDot`, `FluidSkeleton*`, `FluidSelectionMark`, `FluidPagerDots`, `FluidStepProgress` | — | — | ✓ | — | — | — | ? | ? | ? | `FluidLiveDotTest` (un respiro solo, in fase con l'orologio, fermo con le animazioni ridotte), `FluidPagerDotsTest` (i punti seguono il dito senza gradini), `FluidStepProgressTest`, `FluidSkeletonTest`, `FluidCapsulesAndPartsTest` (TalkBack: lo stato, la pagina, un blocco solo per i passi, una frase per la lista di scheletri; animazioni ridotte); il costo del respiro non è misurato |
| suggerimenti al primo uso | ✓ | ✗9 | ✗9 | ✓ | ✓ | ✗9 | ? | ? | ? | (vedi `FluidCoachMark`) |

## Eccezioni note

Dove la ricetta e il codice si separano apposta, con il motivo. Se ne tolgono solo con una decisione.

| Cosa | Perché |
|---|---|
| squash **con segno** `/10` nella pillola della tab bar, `/50` nell'interruttore | il metro (R1): sono il numero con cui i due controlli sono nati e il confronto fra fotogrammi prima e dopo non deve spostarsi; `GlassDropSquashTest` |
| lo squash di pillola e interruttore **ignora le animazioni ridotte** | com'era, e lo è da prima; una modifica verso la ricetta è possibile, ma è una decisione e non una correzione |
| le molle dei tasti sono **costanti di LibrePods** (`FluidPressSprings`), non token `FluidMotion` | è la ricetta del tasto; il tasto che si comporta come quello di LibrePods è il motivo della fase 4 |
| un tap fa **tre colpi** (`PressDown`, `PressRelease`, `PressClick`) | l'unica eccezione a "una vibrazione per momento": la coreografia è il tasto |
| un `FluidAlert` senza host, o con `inHost = false`, dipinge il suo vetro | è il `Dialog` di piattaforma, e non rifrange la pagina: serve a un avviso che deve stare sopra le finestre di piattaforma (un foglio, una tastiera). Nell'host è vetro vero |
| `FluidRouteMotionHost` sfoca ancora 6 dp la pagina che esce | resta per chi non migra a `FluidNavHost`; dentro un `FluidNavHost` non si mette (non compila) |

## Come si aggiorna

A ogni fase chiusa: le celle toccate diventano ✓ solo con la prova (test, fotogrammi o misura citati
nella colonna delle note e in `artifacts/3.0-stato.md`), mai "per costruzione". Una cella il cui lavoro
è stato fatto ma che nessuno ha provato passa da ✗n a **?**: non è più da fare, e non è ancora vera. Le
celle **?** si chiudono nella fase 10, una per una; la fase 10 finisce quando non ne resta nessuna.
