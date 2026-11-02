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
  `Dialog` che dipinge il suo vetro); la bolla dell'indice di sezione, dalla fase 9, è un pannello come gli altri (`panelOptics(compact, overSharpPage)`): resta
  una sola eccezione, l'avviso senza host.
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
- **una faccia per tema** (fase 9) per la goccia che sta in una lastra e non su una pista colorata
  (segmentato, rail, lente dell'indice): bianca sul chiaro, sullo scuro il grigio di iOS (`GlassDropFaces`,
  `GlassDropFaceTest`); i pomelli di slider e interruttore tengono il bianco. Un modificatore solo, `glassDrop`,
  col movimento dato da una `GlassDragAnimation` o come lambda (la lente dell'indice, la cui pressione è
  l'apertura del nastro);
- **aptica**: un tick per tacca vera (scheda, segmento, voce, metà dell'interruttore, aggancio dello
  slider), mai durante il moto libero, e **mai al primo evento** di un gesto (la pressione lunga che non si muove
  non è una tacca attraversata).

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
- **La barra è ancorata** (fase 8, passi 5-8: `FluidPageBar`): una per host, ferma mentre le pagine passano; il
  tasto indietro è uno e sta fermo al pixel, le azioni di vetro diventano quelle della pagina accanto con una
  superficie Fluid-physics, il titolo grande è contenuto e viaggia con la sua pagina, quello agganciato sfuma e fa il
  passo di 16 dp; segue lo stesso progresso del gesto. **Un gesto che parte sulla barra è della barra** (mai il
  back); i tasti dentro la pagina lasciano partire il back «ovunque», come LibrePods.

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

Stato al 2026-11-02, dall'audit del codice 2.13.1 più i commit della 3.0 fino a `85e2d4e` (la barra ancorata, fase 8) e la
fase 10. Le note dicono cosa prova una cella ✓ e cosa manca alle altre; una **✗n** sarebbe una cosa ancora da fare nella
fase *n* del piano, e oggi non ce n'è nessuna.

**Regola per leggere una ✓.** Una funzione condivisa provata da un test (`fluidPressable`,
`glassControlSurface`, `glassDrop`, `panelOptics`) vale per chi la chiama senza numeri propri, e la
cella lo dice ("via …"); quello che solo l'occhio o la mano possono dire — ombre, sbiancamento del
vetro, un lampo di un fotogramma — è **?** finché non si guarda. Nessuna ✓ è "per costruzione".

**Il conto** (2026-11-02, dopo la fase 12c: 63 componenti, 567 celle): 205 ✓, 0 ✗, 271 ?, 91 —. Le cinque righe
nuove (aggiornamento in-app, riordino, scorrimento rapido, visualizzatore, elenco dell'orologio) nascono ? o —: i loro test
provano i conti e i tocchi, non il vetro né il moto. Alla fase 10 erano 58 componenti, 522 celle: 205 ✓, 0 ✗, 231 ?, 86 —. Prima della fase 10 erano 189 ✓, 41 ✗
(fase 3: 8, 5: 3, 8: 11, 9: 19), 198 ?, 85 — su 513 celle in 57 righe. Le 41 ✗ sono chiuse: **17 diventano ✓** perché c'è un
test che le prova (i token di movimento con i numeri di prima, `FluidMotionTokensTest`; la goccia in un modificatore solo,
`GlassDropFaceTest`; il segmentato da destra a sinistra, `FluidSegmentedRtlTest` e `FluidMatrixTest`; il primo evento
dell'indice, `FluidMatrixTest`; il tick per segmento, il tocco della banda e del cerchio, il foglio che chiude e il
suggerimento che si chiude, `AllGlassTest`), **una diventa ✓ e una —** nella barra ancorata (un gesto che parte sulla barra è
della barra, `FluidPageHostTest`; la barra non ha un'aptica sua) e **22 diventano ?** perché il lavoro è fatto ma lo dice solo
l'occhio o la mano — il vetro della fase 9 (gli host dei test non hanno fondale), il passaggio dal vetro dei tasti alla
superficie fisica senza lampo, il titolo grande in `ShrinkSlide`, la fluidità della ricattura, l'arrivo senza scala del
suggerimento. **Due ✓ tornano ?** perché l'arrivo è cambiato e nessuno l'ha guardato: la lente dell'indice e la sua bolla
entrano per offset e alfa, non più per scala (nessun layer scalato sul vetro). Si aggiunge una riga, l'interfaccia
dell'assistente (`engine-ai-ui`), tutta ?. Il 31 ottobre, all'audit del 2.13.1, erano 116 ✓, 120 ✗, 134 ? su 414, in 46
righe. **Cosa resta ✗: niente.** Resta il lavoro della mano: le ? si chiudono guardando, una per una, sulle beta.

### Tasti

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidButton` | — | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | `FluidPressTest` (crescita 0,10, molle 0,5/300, deriva `tanh`, rilascio immediato), `FluidButtonDragTest` (la passata scorre, un tap fa tre colpi); molle di LibrePods, non token |
| `FluidGlassButton` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `GlowRecipeTest` (luce di LibrePods), `FluidPressTest`, `FluidButtonDragTest` (tasto di vetro trascinato: scorre la lista); taglie e bersaglio da 48 dp: `FluidGlassSizeTest`, `FluidGapsTest`; su vetro chiaro la luce additiva può sbiancare la faccia: da guardare (D8) |
| `FluidGlassIconButton` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ×1,5: `FluidPressTest`; taglie 40/44/54: `FluidGlassSizeTest`; `haptic = null` per chi ha già il suo colpo |
| `FluidChip` | ? | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | tasto di vetro dove c'è un canvas (fase 9: lo scelto ha l'accento dentro il materiale), la pastiglia opaca di sempre senza fondale o sotto API 31; pressione e aptica dei tasti via `fluidPressable(Expand)` e `glassControlSurface` (provate su `FluidButton`: `FluidPressTest`), colore con `FluidMotion.color(200)`, nessun numero proprio; `AllGlassTest`: scatta, è scelto, suona `PressClick`, spento non scatta, e lo stesso senza fondale; il vetro con l'accento dentro il materiale non è stato guardato (D1) |
| `FluidBarAction` / indietro | ✓ | ✓ | ? | ✓ | ✓ | ? | ? | ? | ? | via `FluidGlassIconButton`; con `actions` è l'ancora del menu `Expand` (`FluidMenuLayer`, il pannello unico): si apre sincrono, il tasto si nasconde nel disegno e non sfuma più; un `Open` solo, e un rilascio fermo lascia il menu aperto: `FluidMenuHandOffTest` (azione della barra, `PressDown` `Open` `Tap`); `opensOn = Tap` non ha test, solo la demo; il cambio di proprietario fra tasto e finestra non è stato guardato (D6); dentro la barra ancorata (fase 8) il tasto indietro è uno e sta fermo al pixel (`FluidPageHostTest`: «il tasto indietro della barra è uno e resta fermo al pixel mentre la pagina esce», «dalla radice a una figlia il tasto indietro non è mai doppio») e le azioni passano alla pagina accanto con una superficie Fluid-physics (`FluidPageMotionTest`: il morph dei tasti sta fermo ai due capi, le icone delle due pagine non si leggono mai insieme; `ExternalGroupDriveTest`); il passaggio dal vetro del tasto alla superficie fisica, senza lampo, non è stato guardato (D3) |
| ancora del menu (`FluidMorphMenuButton`) | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | `FluidButtonDragTest`: il tenuto premuto apre senza click, il tap clicca (`PressClick`) e non apre; `FluidMenuHandOffTest`: tieni, trascina e rilascia dà `PressDown` `Open` `Tick` `Tap` e nessuna grana, un rilascio fermo, fuori dalle voci o su una voce spenta lascia aperto, il back a metà apertura chiude in silenzio; parte dal tasto premuto e si inverte dal punto in cui si trova: `FluidMenuJourneyTest`; apertura sincrona senza fotogramma morto: per costruzione, nessun fotogramma guardato (D6); `opensOnTap` senza test |
| `FluidGlassMenuButton` (deprecato) | ✓ | ✓ | ✓ | ? | ✓ | ? | ? | ? | ? | passa dal pannello, dal viaggio e dal dito dei menu nuovi (`FluidMenuLayer`, `FluidMenuAnchor`: provati dagli altri ingressi), ma ha un'apertura sua nel codice — l'`Open` dal click lungo — che nessun test chiama |

### Gocce

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| pomello di `FluidSlider` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; legge la goccia condivisa (`glassDrop`), squash bit per bit: `GlassDropSquashTest`; la faccia bianca svanisce `1 − p` bit per bit (`GlassDropFaceTest`) e le molle sono token con i numeri di prima (`FluidMotionTokensTest`): la fusione di `glassDropFace` in `glassDrop` non li ha mossi |
| pomello di `FluidSwitch` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; squash con segno /50 e fuori dalle animazioni ridotte: eccezioni note (R1); `GlassDropSquashTest`; faccia bianca e molle come lo slider (`GlassDropFaceTest`, `FluidMotionTokensTest`) |
| pillola della tab bar flottante | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | squash con segno /10 dalla goccia condivisa, fuori dalle animazioni ridotte: eccezione nota (R1), `GlassDropSquashTest`; il ritorno dell'offset (`TabTugReturn`) e le molle della goccia (`dropValue`, `DropPress`, `DropScaleX/Y`) sono token con i numeri di prima (`FluidMotionTokensTest`: stessa traiettoria a ogni passo di 25 ms) |
| pillola di `FluidSegmentedControl` | ? | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | goccia di `glassDrop` (fase 9) in una pista di vetro (lastra `Content`): bianca sul chiaro e grigia sullo scuro (`GlassDropFaceTest`: la faccia per lato, il contrasto con l'etichetta, l'alfa che svanisce con la pressione come prima); `AllGlassTest`: sceglie una volta (sulla pista di vetro e sulla pagina piatta), trascinata suona un `Tick` per segmento attraversato e si posa sul più vicino, le etichette si dicono una volta sola; da destra a sinistra la goccia e il dito vanno nel verso giusto (`FluidSegmentedRtlTest`; `FluidMatrixTest`: il tocco, il trascinamento verso sinistra fa due tick, verso destra dal primo segmento non va da nessuna parte); le molle sono token della goccia (`FluidMotionTokensTest`); la goccia sul vetro, bianca o grigia a riposo, e la lente sulle etichette non sono state guardate (D1) |
| indicatore di `FluidTabRail` | ? | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | goccia di `glassDrop` (fase 9) che viaggia come la pillola della barra (press, corsa, rilascio: `GlassDragAnimation.animateToValue`), centrata sulla scheda; la scheda scelta si ridisegna sopra senza prendersi il tocco: `AllGlassTest` (risponde a un tocco su un'altra scheda e il secondo tocco sulla scelta è ancora un `reselect`); l'alfa della lente è `FluidMotion.flick()` e le molle sono token (`FluidMotionTokensTest`); l'ombra interna dichiarata non si disegna (commento corretto); la lente sulle etichette tinte non è stata guardata (D1) |
| lente di `FluidSectionIndex` | ? | ? | ✓ | ✓ | ✓ | ? | ? | ? | ? | goccia di `glassDrop` (fase 9) con la pressione = apertura del nastro e la scala nel `layerBlock`: un `GestureStart` alla pressione lunga e un `Tick` solo per la tacca raggiunta, mai al primo evento (`FluidMatrixTest`: fermo = nessun tick e nessuna selezione; trascinato = un tick e la sezione dove sei arrivato; `AllGlassTest`: si apre e mostra la bolla); l'indice e la bolla entrano per offset e alfa e non per scala (nessun layer scalato sul vetro): com'è l'arrivo, e la goccia sul vetro, non sono stati guardati (D1, D2, D6) |
| goccia dei menu | ✓ | ✓ | ? | ✓ | ✓ | ? | ? | ? | ? | `FluidGlassMenuPanel`: la ricetta di `glassDrop` coi numeri del pomello dello slider (`GlassDropRecipe`, `glassDropSquash`: `GlassDropSquashTest`), una per pannello, che campiona il materiale del pannello con tutte le trasformazioni nel proprio `layerBlock`; `FluidMenuSelectionTest` (23: il dito decide, la voce spenta non fa tick, un rilascio fermo non sceglie), `FluidMenuPanelTest` (15: celle, sezioni, la banda non sta sotto il dito), `FluidMenuHandOffTest` (un `Tick` per voce, mai la grana); la goccia sul vetro, il suo squash e la conferma bianca prima di andarsene non sono stati guardati; senza fondale restano la scala della voce e i separatori che si spengono |

### Lastre e chrome

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| barra superiore (`FluidScreen`, `FluidCollapsingTopBar`) | ✓ | — | ✓ | — | ✓ | ? | ? | ? | ✓ | ora ancorata (fase 8): una per host, ferma mentre le pagine passano; dentro un host descrivono la barra e la disegna l'host (`FluidPageHostTest`: «il titolo grande è contenuto e viaggia con la sua pagina», «la barra passa al titolo della pagina che arriva»), fuori disegnano quella di sempre; il vetro che rifrange le due pagine insieme e l'eventuale lampo di un fotogramma nel cambio di proprietà non sono stati guardati (D6) |
| pannello della tab bar flottante | ? | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ombra di vetro (`glassShadow`) al posto di quella di piattaforma, nessun test: da guardare; resta di piattaforma senza fondale (la banda, dove c'è vetro, è il vetro della barra); la piegatura (`GooeyPulse`, `GooeyMillis`), il puck che entra (`PuckEnter`), il cambio fra barra in linea ed estesa (`FoldCrossFade`), la barra ripiegata (`TabBarFold`), l'entrata delle schede (`tabEnterAlpha`, `tabEnterBlur`) e l'anticipo del puck (`TabPuckLeadMillis`) sono token con i numeri di prima (`FluidMotionTokensTest`) |
| banda accessoria della tab bar | ? | — | ✓ | — | ✓ | ✓ | ? | ? | ? | è il vetro della barra (fase 9): ombra di vetro e niente riempimento dove c'è un fondale, la capsula opaca di prima senza (`accessoryBand`); entra e esce per alfa con `FluidMotion.flick()` (`FluidMotionTokensTest`); `AllGlassTest`: la barra con la banda la mostra e un tocco su una scheda dice `Tap` e naviga; banda e barra come un vetro solo: da guardare (D1) |
| cerchi autonomi / scheda inline della tab bar | ✓ | ? | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | fase 9: la scheda in linea prende la luce del dito e niente increspatura di Material (`indication = null`); un tocco sul cerchio, sulla scheda in linea e sulla scheda ripiegata dice `Tap` (`AllGlassTest`: il cerchio risponde con un `Tap` e scatta una volta, la scheda ripiegata risponde con un `Tap` e riapre la barra); la luce additiva sul vetro chiaro non è stata guardata (D2) |
| `FluidSidebar` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il lavaggio della selezione è `FluidMotion.flick()`, la molla di serie che prima era implicita (`FluidMotionTokensTest`: stessa traiettoria) |
| pannello di `FluidTabRail` | ✓ | — | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | nessun moto proprio: la goccia e l'alfa della lente hanno i loro token (vedi l'indicatore, `FluidMotionTokensTest`) |
| `FluidListGroup` / `FluidCard` / `FluidMetricTile` (glass) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | |
| `fluidGlassGroups` / `FluidGlassGroup` | ✓ | — | — | — | — | ? | ? | ? | ? | `FluidGlassGroupsTest` (il piano: pezzi, giunture, tetto di otto righe), `FluidGapsTest`; il taglio invisibile e il costo si guardano in mano |
| `FluidContainerScaffold` | ? | — | ✓ | — | ✓ | ✓ | ? | ? | ? | su un fondale ambientale il gruppo è una lastra di contenuto come `FluidListGroup(glass = true)` (fase 9), senza fondale il contenitore pieno di sempre: `AllGlassTest` mostra il contenuto nei due casi; la lastra non è stata guardata (D1) |
| `FluidSyncIndicator` (nella barra) | ? | — | ✓ | — | ✓ | ✓ | ? | ? | ? | capsula di vetro con il tono nel materiale e la pressione di `FluidSyncAction` (fase 9); fuori da una barra è il cerchio colorato di sempre: `AllGlassTest` (nella barra è premibile come un tasto di vetro e apre il dettaglio, fuori resta il cerchio e apre il dettaglio); il tono dentro il vetro non è stato guardato (D1) |
| `FluidFloatingCapsule` | ✓ | — | ✓ | ✓ | ✓ | ? | ? | ? | ? | `glassSurface` ruolo `Floating` con la pellicola della barra (`fluidFloatingGlass`, nessun numero proprio, letto nel codice); `FluidFloatingCapsuleTest` (margini e inset), `FluidCapsulesAndPartsTest` (azione che clicca col colpo del tasto, capsula nascosta, tocchi trattenuti); entrata e uscita con token `FluidMotion`, nessun fotogramma guardato |
| `FluidCapsuleAction` | — | ✓ | ✓ | ✓ | ✓ | — | ? | ? | ? | via `fluidPressable(Expand)`; `FluidCapsulesAndPartsTest` (clicca, disattivata non clicca e lo dice); nessuna lente per azione, ma non misurato |
| `FluidSearchField` | ✓ | ✓ | ✓ | ✓ | — | ? | ? | ? | ? | stessa pellicola della capsula; `FluidSearchFieldTest` (contatore, larghezza, dissolvenza), `FluidCapsulesAndPartsTest` (il cerchio apre col colpo di `Open`, fuoco alla comparsa, contatore e frecce, "nessun risultato"); l'apertura da un cerchio non è stata guardata fotogramma per fotogramma |

### Pannelli, modali e menu

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| menu che si trasforma (host) | ? | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | lastra col materiale del pannello (`panelOptics(compact)`: `GlassPanelMaterialTest`, `FluidPhysicsMaterialTest`) e pelle e lente che passano dal tasto (`opticsFrom`); ombra e bordo di una lastra a riposo: da guardare (D1); viaggio: `FluidMenuJourneyTest` (11: parte dal tasto premuto, gonfiore attorno al centro del pannello, un back a 100 ms torna indietro con la sua velocità, animazioni ridotte senza viaggio), `FluidMenuMotionTest` (21: i token, le finestre del contenuto), `MorphOvershootTest`; dito e aptica: `FluidMenuHandOffTest`; apertura sincrona senza fotogramma morto: per costruzione, i fotogrammi non sono stati guardati (D6); gli shader della frangia sull'emulatore: nessun errore AGSL (beta 1, `artifacts/beta1/`) |
| popover (`FluidGlassModalPortal`) | ✓ | — | ? | ? | ✓ | ? | ? | ? | ? | `panelOptics` + `panelTint` (`GlassPanelMaterialTest`); dalla 3.0 passa dalla finestra di Fluid-physics e cresce da un seme, senza layer scalato (era la trappola del pannello classico): è nel codice, e nessun test lo vede — gli host dei test non hanno fondale e ripiegano sul pannello opaco; il colpo del layer (`Open`) è tolto, nessun test lo conta; i tempi e le molle (rincorsa, assestamenti, aperture, uscite) sono token `FluidMotion.Popover*` con i numeri di prima (`FluidMotionTokensTest`) |
| finestra che si espande (card) | ? | — | ✓ | ? | ✓ | ✓ | ? | ? | ? | `panelOptics` e ombra di vetro (`fluidPhysicsShadow`, ~6× più leggera di prima: da giudicare in mano); il rimbalzo è il gonfiore uniforme coi numeri della rincorsa di prima (`FluidMenuMotionTest`: `theInflationEqualsTheOldModalValuesForTheOldNumbers`) e `MorphOvershootTest`; il layer non suona più `Open` (resta il colpo di chi apre), nessun test lo conta |
| menu contestuale + riga sollevata | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | ? | `panelOptics(compact)` e anteprima sollevata dal pannello unico (`GlassPanelMaterialTest`); cresce accanto alla riga da un seme sul perno più vicino e mai sopra (`FluidMenuJourneyTest`: `aContextMenuGrowsFromItsSeed`; `FluidMenuPanelTest`: il seme è il pannello scalato attorno al perno), e la riga sollevata è una superficie Fluid-physics sorella dell'immagine registrata: nessun layer scalato sul vetro; `FluidMenuHandOffTest`: una riga in una `LazyColumn` si sceglie trascinando, la lista resta ferma, `Open` `Tick` `Tap`; `fluidContextMenu`: tieni, rilascia e tocca, `Open` `Tap`; un tocco fuori chiude in silenzio; lo scambio fra la riga e la sua immagine sollevata, e la goccia sul vetro, non sono stati guardati (D6) |
| foglio di vetro | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `panelOptics` + `panelTint` (`GlassPanelMaterialTest`); il foglio trascinato che torna è `FluidMotion.SheetSettle` (`FluidMotionTokensTest`) |
| `FluidSheetScaffold` / `FluidConfirmSheet` | — | — | — | — | ✓ | ? | ? | ? | — | `FluidSheetScaffoldLayoutTest` (il corpo prende quello che resta, i tasti si misurano per primi), `FluidRowsAndSheetsTest` (i tasti restano in vista sotto un corpo più lungo del foglio, il corpo scorre; conferma e annulla sono due cose); leggibilità del testo sul vetro e parole congelate in uscita: da guardare |
| modale a schermo intero | — | — | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | la chiusura è un tasto di vetro (`FluidGlassIconButton`; un cerchio dipinto sotto API 31): `AllGlassTest` (chiude) |
| scrim | ✓ | — | ✓ | — | ✓ | ✓ | ? | ? | ? | |
| `FluidAlert` | ? | ? | ? | ✓ | ✓ | ? | ? | ? | ? | nell'host quando c'è (`inHost`): vetro vero e `panelOptics` nel codice, nessun test ne vede il vetro (gli host dei test non hanno fondale); `FluidMenuHandOffTest`: sta nell'host senza finestre di piattaforma, esce anche dopo che il chiamante l'ha tolto (con `FluidGlassModalStateTest`: `retire` e `finishExit`), un pulsante sceglie con un `Tap` solo; l'arrivo che si posa e il trascinamento fra i pulsanti, con la goccia, non hanno test; senza host o con `inHost = false` è il `Dialog` di prima (vetro dipinto, nessuna uscita) |
| `FluidPromptAlert` | ? | — | ? | ? | — | ? | ? | ? | — | è un `FluidAlert` (ci sta dentro, quindi nell'host); `FluidPromptAlertTest` (validazione a tre stati, testo ritagliato), `FluidRowsAndSheetsTest` (il tasto è la validazione, Invio conferma, si apre sul campo con il testo selezionato); la tastiera che sale sopra un campo nell'host non è provata (Robolectric non la porta) |
| `FluidSheet` (deprecato) | ✓ | — | — | — | ✓ | ✓ | ? | ? | ? | delega al foglio di vetro (D1 come lui: `panelOptics` + `panelTint`, `GlassPanelMaterialTest`) e dice al chiamante «chiuso» a uscita finita (`onDismissRequest`, una volta sola): `AllGlassTest`; senza host resta il foglio di piattaforma |
| `FluidNotification` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | `panelOptics(overSharpPage)` al posto della lente `Modal` (`GlassPanelMaterialTest`) |
| `FluidCoachMark` | ✓ | ✓ | ? | ✓ | ✓ | ? | ? | ? | ? | pannello da `panelOptics(overSharpPage)`; fase 9: ingresso e uscita per offset di layout e alfa (`FluidMotion.fadeIn`, `fadeOut`, `snappy`), mai una scala sul vetro; «Ok» è un tasto di vetro e la punta dipinta è tolta; `AllGlassTest`: si chiude dal suo tasto con il `Close` della capsula che sostituisce, e senza fondale resta la capsula piena e si chiude; la scala assente e l'arrivo senza lampi non sono stati guardati (D3, D6) |
| bolla dell'indice di sezione | ✓ | — | ✓ | — | ✓ | ? | ? | ? | ? | `panelOptics(compact, overSharpPage)` e `panelTint` (fase 9: non più la lente del ruolo `Modal`; `GlassPanelMaterialTest`: la base, `compact` cambia solo la lente, `overSharpPage` solo la sfocatura): è un pannello come gli altri; entra e esce per offset e alfa, non per scala: com'è l'arrivo non è stato guardato (D6) |

### Pagine e movimento

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| host di pagine, transizioni di rotta (`FluidPageHost`, `FluidNavHost`) | — | — | ✓ | — | ? | ? | ? | — | ? | `FluidPageMotionTest` (30: molle critiche, nessun buco fra la pagina sotto e quella sopra, il davanti passa a metà, il passo fra schede, il morph dei tasti), `FluidPageHostTest` (21: la molla parte con la velocità del dito, RTL, animazioni ridotte, la barra); opaca per costruzione, nessun fotogramma guardato; scorrimenti annidati provati (una riga che scorre di lato al suo inizio passa la mano al back, altrimenti scorre; un cursore non porta mai indietro), due host insieme no; `FluidRouteMotionHost` è deprecato e non sfoca più né arrotonda (`e8fb813`) |
| gesto indietro | — | ✓ | ✓ | — | ? | ? | ? | — | ? | `FluidPageHostTest`: dal bordo e da un punto qualunque la pagina sta sotto il dito a ogni fotogramma, il back di sistema con `progress` storto segue `touchX`, rilasci lenti sotto e sopra il 35%, spinte nei due versi; nessuna vibrazione nel gesto; chi possiede il dito è provato per uno scorrimento annidato (`FluidPageHostTest`: una riga che scorre di lato al suo inizio passa la mano al back, una che scorre prima scorre, un cursore non porta mai indietro) e per la barra (un gesto che parte sulla barra è della barra); il back di sistema su One UI, e due host insieme, non sono stati provati |
| barra ancorata (`FluidPageBar`) | ? | ? | ? | — | ✓ | ? | ? | ? | ? | fase 8, passi 5-8: una per host dentro `FluidPageHost`/`FluidNavHost`, ferma mentre le pagine passano. Il tasto indietro è uno e sta fermo al pixel, il titolo grande è contenuto e viaggia con la sua pagina, quello agganciato sfuma e fa il passo di 16 dp (`FluidPageHostTest` 21; `FluidPageMotionTest` 30: `barMorph` fermo ai due capi e in crescita in mezzo, le icone delle due pagine non si leggono mai insieme, il titolo che esce sfuma nella prima metà, i pezzi si accoppiano dall'esterno e chi resta solo nasce da un seme, oltre sei pezzi niente viaggio); il vetro rifrange le due pagine insieme (`FluidChromeTransitionTest` 3) e i tasti di vetro passano alla pagina accanto con una superficie Fluid-physics guidata da fuori (`ExternalGroupDriveTest` 5); **un gesto che parte sulla barra è della barra** (`FluidPageHostTest`) e i tasti della pagina lasciano partire il back «ovunque»; **da guardare**: il passaggio dal vetro dei tasti alla superficie fisica senza lampo (D1, D2, D6), il titolo grande in `ShrinkSlide` (D3), la tab bar durante il moto, la fluidità della ricattura a ogni fotogramma (D9), RTL, animazioni ridotte e testo a 1,3× (D7), il caso dei tre tasti |
| superficie Fluid-physics (Playground) | ? | ? | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | a riposo è una lastra: `physicsLensUniforms` rifà i numeri di `glassSurface` per ogni ruolo, taglia, densità, qualità e pressione (`FluidPhysicsMaterialTest`), bordo di `glassRimRecipe` (`GlassRimRecipeTest`); ombra e bordo a riposo: da guardare; una molla sola che parte alla velocità del momento, la rincorsa è il preset `House` (`FluidPhysicsJourneyTest`), oltrepasso = gonfiore uniforme (`MorphOvershootTest`), un viaggio cancellato si ferma dove si trova (`FluidPhysicsCancelTest`); gli shader della frangia sull'emulatore: nessun errore AGSL (beta 1, `artifacts/beta1/`); il pezzo che si tocca non ha test |
| scorrimento ed elastico (`FluidScrollPhysics`) | — | ✓ | ? | — | ✓ | — | ? | — | ? | da verificare contro la ricetta (10); lo scatto verso la tacca e il rimbalzo sono `FluidMotion.pixels` con i numeri di prima (`FluidMotionTokensTest`) |
| pull-to-refresh | — | ? | ? | ? | ✓ | ? | ? | — | ? | (10) |

### Contenuto e altro

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidListRow` | — | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | cede, non cresce: resta così; con `onClick` è **un nodo solo** per TalkBack, col nome (titolo e sottotitolo fusi), `Role.Button` e `selected` (`FluidMatrixTest`: un solo nodo che scatta, nell'albero fuso e in quello intero, nessun secondo nodo con ruolo o stato; senza `onClick` è un'etichetta senza ruolo); testo a 1,3× e fuoco da tastiera non provati; `leadingTile` e `FluidToneTile` pubblica (`FluidGapsTest`); con `contextActions` il click lungo suona `Open` se il menu si apre, o il `GestureStart` dell'app se no (`FluidMenuHandOffTest`) |
| `FluidToggleRow` | — | ✓ | — | ✓ | ✓ | — | ? | ? | — | `FluidRowsAndSheetsTest`: un tocco commuta una volta e vibra come l'interruttore (solo `ToggleOn`/`ToggleOff`), il tocco sul pomello commuta una volta sola, disattivata non risponde; per TalkBack un nodo solo, `Role.Switch`; testo a 1,3× non provato |
| `FluidChoiceRow` | — | ✓ | — | ✓ | ✓ | — | ? | ? | — | `FluidRowsAndSheetsTest`: un radio per TalkBack, `Tick` solo se la scelta si sposta, la riga già scelta avvisa senza vibrare |
| `fluidAppearanceSection` / `FluidAccentPicker` | — | — | — | ✓ | — | — | ? | ✓ | — | `FluidAppearanceTest` (18: AMOLED = Scuro + nero, `DYNAMIC` senza supporto è il marchio, la prova dell'aptica solo da spenta ad accesa), `FluidAppearanceSectionTest` (9: scrive solo se cambia qualcosa, il nero non risponde nel chiaro) |
| `FluidInlineMessage` | — | ✓ | — | — | — | — | ? | ? | — | cede come ogni superficie; `FluidGapsTest`: con `onClick` è un pulsante e mostra la sua azione, senza non finge |
| `FluidTextField` | — | ? | ? | ? | ✓ | ? | ? | ? | ? | pozzetto opaco sul vetro per scelta; slot a destra da 24 dp e `secret` (`FluidGapsTest`: password finché non si tocca l'occhio) |
| `FluidProgressBar` / `FluidSpinner` / indeterminata | — | — | ? | — | — | ? | ? | ✓ | ? | `FluidFillContrastTest`: ogni accento della palette si legge sulla pista, su ogni superficie, almeno 3:1 |
| `FluidEmptyState` / `FluidLoading` | — | — | ? | — | — | ? | ? | ? | ? | `FluidEmptyState(illustration)`: decorazione muta (`FluidGapsTest`) |
| pezzi piccoli: `FluidLiveDot`, `FluidSkeleton*`, `FluidSelectionMark`, `FluidPagerDots`, `FluidStepProgress` | — | — | ✓ | — | — | — | ? | ? | ? | `FluidLiveDotTest` (un respiro solo, in fase con l'orologio, fermo con le animazioni ridotte), `FluidPagerDotsTest` (i punti seguono il dito senza gradini), `FluidStepProgressTest`, `FluidSkeletonTest`, `FluidCapsulesAndPartsTest` (TalkBack: lo stato, la pagina, un blocco solo per i passi, una frase per la lista di scheletri; animazioni ridotte); il respiro è `FluidMotion.pulse` (`PulseMillis` 900, `PulseLowAlpha` 0,35, `PulseFillLowAlpha` 0,55: `FluidMotionTokensTest`); il costo del respiro non è misurato |
| suggerimenti al primo uso | ✓ | ✓ | ? | ✓ | ✓ | ? | ? | ? | ? | (vedi `FluidCoachMark`) |
| interfaccia dell'assistente (`engine-ai-ui`, opt-in) | ? | ? | ? | ? | ? | ? | ? | ? | ? | il modulo di `1e644a6`: overlay, impostazioni e chat con `FluidMarkdown`; 142 test JVM (parser, rivelazione, decisioni del fondo, ordine dei servizi, forma delle chiavi, filtri, frasi, aptica di stato, voce, alone) che non vedono né il vetro né il moto; ogni suo movimento è un token `FluidMotion` (nessun `spring(` o `tween(` letterale: fase 10) e il pallino respira a 900 ms (era 700); l'overlay nelle app non è stato guardato |
| aggiornamento in-app: `FluidUpdatePrompt`, `FluidUpdatePromptSheet`, `fluidUpdateSection` | ? | ? | ? | ? | — | ? | ? | ? | ? | fase 12c (`f941520`): foglio di vetro dell'host, tasti e righe di casa; `FluidUpdateModelTest` (24: stati, percentuali, note, «N minuti fa») e `MoreComponentsTest` provano stati e tocchi, nessun fotogramma guardato |
| riordino: `fluidReorderable` | ? | ? | ? | ? | ? | ? | ? | ? | ? | l'elemento sollevato è una goccia (ombra di ripiego senza canvas), vicini con `FluidMotion.intOffset()`, molla critica dalla velocità del dito, `Tick` per posto; `FluidReorderTest` (24) prova i conti, il vetro e il posarsi si guardano in mano |
| scorrimento rapido: `FluidFastScroller` | ? | ? | ? | ? | ? | ? | ? | ? | ? | maniglia-goccia 1:1, bolla pannello compatto, `Tick` per etichetta; `FluidFastScrollTest` (18); la sparizione dopo 1,2 s è una costante con un TODO di token (`FluidMotion.FastScrollerHideMillis`) |
| visualizzatore: `FluidImageViewer`, `fluidZoomable` | ? | ? | ? | ? | ? | ? | ? | ? | ? | nell'host a schermo intero; `FluidZoomTest` (22: limiti, doppio tocco, pizzico); lo scrim ha un'intensità fissa e lo sfondo è la pagina opaca dell'host, non nero |
| elenco dell'orologio: `FluidWearList` (engine-wear) | — | — | ? | — | — | ? | ? | ? | ? | righe `FluidWearListRow` di sempre; `FluidWearListPlanTest` (8) prova cosa si disegna in ogni stato |

## Eccezioni note

Dove la ricetta e il codice si separano apposta, con il motivo. Se ne tolgono solo con una decisione.

| Cosa | Perché |
|---|---|
| squash **con segno** `/10` nella pillola della tab bar, `/50` nell'interruttore | il metro (R1): sono il numero con cui i due controlli sono nati e il confronto fra fotogrammi prima e dopo non deve spostarsi; `GlassDropSquashTest` |
| lo squash di pillola e interruttore **ignora le animazioni ridotte** | com'era, e lo è da prima; una modifica verso la ricetta è possibile, ma è una decisione e non una correzione |
| le molle dei tasti sono **costanti di LibrePods** (`FluidPressSprings`), non token `FluidMotion` | è la ricetta del tasto; il tasto che si comporta come quello di LibrePods è il motivo della fase 4 |
| un tap fa **tre colpi** (`PressDown`, `PressRelease`, `PressClick`) | l'unica eccezione a "una vibrazione per momento": la coreografia è il tasto |
| un `FluidAlert` senza host, o con `inHost = false`, dipinge il suo vetro | è il `Dialog` di piattaforma, e non rifrange la pagina: serve a un avviso che deve stare sopra le finestre di piattaforma (un foglio, una tastiera). Nell'host è vetro vero |
| `FluidRouteMotionHost` resta, deprecato | per chi non migra a `FluidNavHost`: non sfoca più né arrotonda (`e8fb813`); dentro un `FluidNavHost` non si mette (non compila) |
| `spring(`, `tween(` e `keyframes` letterali restano in `FluidPress.kt` (`FluidPressSprings`), in `ui/page/`, in `RouteMotion.kt` e `FluidListDetail.kt`, in `fluidphysics/**` e nel renderer (`glass/backdrop/**`) | le molle dei tasti sono costanti di LibrePods (vedi sopra); il renderer è il vendored di Kyant e Fluid-physics ha il suo motore; le pagine hanno la loro meccanica (`FluidPageMotion`) e `FluidListDetail` la sua curva di passo fra pari. **Ovunque altrove** il movimento è un token di `FluidMotion` (fase 10, provato con gli stessi valori da `FluidMotionTokensTest`): un grep di `spring(`/`tween(`/`keyframes` fuori da quei file non trova niente |

## Come si aggiorna

A ogni fase chiusa: le celle toccate diventano ✓ solo con la prova (test, fotogrammi o misura citati
nella colonna delle note e in `artifacts/3.0-stato.md`), mai "per costruzione". Una cella il cui lavoro
è stato fatto ma che nessuno ha provato passa da ✗n a **?**: non è più da fare, e non è ancora vera. La
fase 10 ha chiuso tutte le ✗ (oggi non ce n'è nessuna); le celle **?** si chiudono guardando, una per una, sulle beta: un
test non le prova, perché sono vetro, fotogrammi, tatto. La parte I della 3.0 è finita quando non ne resta nessuna.
