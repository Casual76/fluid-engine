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

Legenda: **✓** conforme · **✗n** da fare nella fase *n* del piano · **—** non si applica · **?** da
verificare (è un "non lo sappiamo", non un "va bene").

## Le ricette

### La lastra
Pannelli, barre, card, gruppi di vetro, menu, tasti di vetro a riposo. È `glassSurface`,
`glassControlSurface` o `fluidPhysicsSurface`.
- **Ottica** dal ruolo (`GlassRole`) con la regola della taglia (`glassSizedOptics`): mai sfocatura o
  lente scritte a mano. I pannelli flottanti (popover, menu, finestra che si espande, foglio, notifica,
  callout, avviso) hanno **un solo materiale**: `GlassDefaults.panelOptics()` + `panelTint()` (fase 3).
- **Bordo**: uno solo, `glassRimRecipe(darkSide)` — chiaro e in `Plus` sul lato scuro, scuro e in
  `SrcOver` sul lato chiaro, col profilo dello shader (pieno sopra e sotto, zero ai fianchi). Anche
  `fluidPhysicsSurface` (fase 3).
- **Ombra** del ruolo, disegnata dal vetro; mai un'ombra di piattaforma (`Modifier.shadow`) sotto.
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
- **squash dalla velocità**, assoluta, `/10`, tetto 0,2, spento con le animazioni ridotte. Unica
  eccezione: l'interruttore conserva i numeri del port (con segno, `/50`) perché è il metro;
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
  profondità e dispersione;
- **aptica a coreografia**, con le costanti di sistema di LibrePods: `SegmentFrequentTick` al contatto e
  trascinando veloce, `Reject` al rilascio, `ContextClick` al click. È l'unica eccezione alla regola
  "una vibrazione per momento", ed è voluta;
- click lungo dove serve (menu): dito fermo entro lo slop per il tempo del click lungo, poi niente click.

### La pagina
Le transizioni fra destinazioni e il gesto indietro (fase 8).
- **Opaca**: niente sfocatura, niente trasparenza, mai due pagine leggibili sovrapposte.
- **Apertura** da destra; la pagina sotto arretra in parallasse di −⅓ della larghezza.
- **Indietro stile iOS (default)**: la pagina segue il dito **1:1 sulla posizione vera** (non sul
  `progress` del gesto di sistema, che è distorto); conferma oltre ~35% della larghezza o con una spinta
  sopra soglia (LibrePods: 0,35 W o 1000 px/s); la molla di uscita o di ritorno parte dalla velocità
  del dito. **Stile "rimpicciolisce e scivola"** per le pagine che lo chiedono.
- **La pagina è una carta**: angoli del telefono e ombra sul bordo mentre si muove.
- **La barra è ancorata**: tasto indietro fermo al pixel, azioni che si trasformano col vetro, titolo che
  sfuma; segue lo stesso progresso del gesto.

### Il pannello in movimento
Le forme di vetro che si trasformano (Fluid-physics: card che si espande, menu, Playground).
- **A riposo è una lastra** e si disegna come tale (stesso renderer): nessuna differenza misurabile.
- In viaggio: ottica, tinta, bordo e ombra **sfumano** dal mittente al destinatario; il rimbalzo è un
  gonfiore uniforme (`overshootInflation`), mai uno stiramento lungo il viaggio; le molle partono dalla
  velocità del gesto, **niente rincorsa**; il contenuto segue la sagoma.
- Il viaggio si può invertire a metà dal punto in cui si trova.
- Mai un layer scalato su un vetro: la scala sta nella geometria o nel `layerBlock` di `drawBackdrop`.

### L'aptica, riassunta
Vocabolario `FluidHaptics`; una vibrazione per momento (rete dei 70 ms); `Open` un colpo solo; la grana
sui tasti trascinati veloci; i tick per le tacche vere; la coreografia dei tasti come sopra. Nessun
`LocalHapticFeedback`/`Vibrator` fuori da `haptics/`.

## La matrice

Stato al 2026-10-31, dall'audit del codice 2.13.1 più la grana (fase 5, parte pura). Le note dicono
cosa manca; il numero è la fase del piano.

### Tasti

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidButton` | — | ✗4 | ✗4 | ✗4 | ✗4 | ✓ | ? | ? | ✓ | crescita 6 dp/14% → ricetta del tasto |
| `FluidGlassButton` | ✓ | ✗4 | ✗4 | ✗4 | ✗4 | ✓ | ? | ? | ? | lente del tasto, luce grande |
| `FluidGlassIconButton` | ✓ | ✗4 | ✗4 | ✗4 | ✗4 | ✓ | ? | ? | ? | ×1,5; taglie (12) |
| `FluidChip` | ✗9 | ✗4 | ✗9 | ✗4 | ✗4 | ✓ | ? | ? | ? | opaco → tasto di vetro |
| `FluidBarAction` / indietro | ✓ | ✗4 | ✗8 | ✗7 | ✗4 | ✗7 | ? | ? | ? | staffetta a dissolvenza; GestureStart+Open+Open |
| ancora del menu (`FluidMorphMenuButton`) | ✓ | ✗4 | ✗7 | ✗7 | ✗4 | ✗7 | ? | ? | ? | Open al tocco; sparisce ancora gonfia |
| `FluidGlassMenuButton` (deprecato) | ✓ | ✗4 | ✗7 | ✗7 | ✗4 | ✗7 | ? | ? | ? | delega al menu nuovo |

### Gocce

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| pomello di `FluidSlider` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; da far leggere la ricetta condivisa (3) |
| pomello di `FluidSwitch` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | il metro; squash con segno /50 conservato |
| pillola della tab bar flottante | ✓ | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | molle scritte a mano → token; commento sullo smorzamento da correggere |
| pillola di `FluidSegmentedControl` | ✗9 | ✗9 | ✗3 | ✗5 | ✓ | ✓ | ? | ✗3 | ? | lastra Interactive 8/8 → goccia; nessun tick per segmento; chiaro/scuro a mano |
| indicatore di `FluidTabRail` | ✗9 | ✗9 | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | senza lente; ombra interna dichiarata che non si disegna |
| lente di `FluidSectionIndex` | ✗9 | ✗9 | ✓ | ✗5 | ✓ | ✓ | ? | ? | ? | tick al primo evento |
| goccia dei menu (nuova) | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | non esiste ancora |

### Lastre e chrome

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| barra superiore (`FluidScreen`, `FluidCollapsingTopBar`) | ✓ | — | ✓ | — | ✓ | ✗8 | ? | ? | ✓ | viaggia con la pagina → ancorata |
| pannello della tab bar flottante | ✗3 | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | ombra di piattaforma sotto il vetro |
| banda accessoria della tab bar | ✗9 | — | ✗3 | — | ✓ | ✓ | ? | ? | ? | opaca |
| cerchi autonomi / scheda inline della tab bar | ✓ | ✗9 | ✓ | ✗5 | ✓ | ✓ | ? | ? | ? | ripple Material; nessuna aptica al tocco |
| `FluidSidebar` | ✓ | ✓ | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | colore animato con spec di default |
| pannello di `FluidTabRail` | ✓ | — | ✗3 | ✓ | ✓ | ✓ | ? | ? | ? | molle scritte a mano |
| `FluidListGroup` / `FluidCard` / `FluidMetricTile` (glass) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | |
| `FluidContainerScaffold` | ✗9 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | opaco sul fondale ambientale |
| `FluidSyncIndicator` (nella barra) | ✗9 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | cerchio opaco |

### Pannelli, modali e menu

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| menu che si trasforma (host) | ✗3 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ? | ? | ? | salto alla partenza, fotogramma morto, voci che spariscono |
| popover (`FluidGlassModalPortal`) | ✗3 | — | ✗7 | ✗5 | ✓ | ✗7 | ? | ? | ? | il pannello classico scala il vetro |
| finestra che si espande (card) | ✗3 | — | ✗6 | ✗5 | ✓ | ✓ | ? | ? | ? | ombra propria più scura; Tap+Open |
| menu contestuale + riga sollevata | ✗3 | ✗7 | ✗7 | ✗7 | ✗7 | ✗7 | ? | ? | ? | scala il vetro; GestureStart vs Open |
| foglio di vetro | ✗3 | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | |
| modale a schermo intero | — | — | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | tasto di chiusura opaco (9) |
| scrim | ✓ | — | ✓ | — | ✓ | ✓ | ? | ? | ? | |
| `FluidAlert` | ✗7 | ✗7 | ✗7 | ✓ | ✓ | ✗7 | ? | ? | ? | finestra di sistema, vetro dipinto, nessuna uscita |
| `FluidSheet` (deprecato) | ✗9 | — | — | — | ✓ | ✓ | ? | ? | ? | delega al foglio di vetro |
| `FluidNotification` | ✗3 | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ? | sfocatura 3,5 → pannello unico |
| `FluidCoachMark` | ✗3 | ✗9 | ✗9 | ✓ | ✓ | ✗9 | ? | ? | ? | `scaleIn` sul vetro; freccia e chiusura opache |
| bolla dell'indice di sezione | ✗3 | — | ✓ | — | ✓ | ✓ | ? | ? | ? | tinta flottante su ruolo Modal |

### Pagine e movimento

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| transizioni di rotta | — | — | ✗8 | — | ✗8 | ✗8 | ? | — | ✗8 | sfocatura 6 dp sull'uscita; ogni app scrive le sue |
| gesto indietro | — | ✗8 | ✗8 | — | ✗8 | ✗8 | ? | — | ? | non segue il dito 1:1 |
| barra ancorata (nuova) | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | ✗8 | non esiste ancora |
| superficie Fluid-physics (Playground) | ✗6 | ✗6 | ✗6 | ✓ | ✓ | ✓ | ? | ? | ? | ottica e bordo propri; rincorsa; nessuna risposta al dito |
| scorrimento ed elastico (`FluidScrollPhysics`) | — | ✓ | ? | — | ✓ | — | ? | — | ? | da verificare contro la ricetta (10) |
| pull-to-refresh | — | ? | ? | ? | ✓ | ? | ? | — | ? | (10) |

### Contenuto e altro

| Componente | D1 | D2 | D3 | D4 | D5 | D6 | D7 | D8 | D9 | Note |
|---|---|---|---|---|---|---|---|---|---|---|
| `FluidListRow` | — | ✓ | ✓ | ✓ | ✓ | ✓ | ? | ? | ✓ | cede, non cresce: resta così |
| `FluidTextField` | — | ? | ? | ? | ✓ | ? | ? | ? | ? | pozzetto opaco sul vetro per scelta |
| `FluidProgressBar` / `FluidSpinner` / indeterminata | — | — | ? | — | — | ? | ? | ✗12 | ? | contrasto del riempimento |
| `FluidEmptyState` / `FluidLoading` | — | — | ? | — | — | ? | ? | ? | ? | |
| suggerimenti al primo uso | ✗3 | ✗9 | ✗9 | ✓ | ✓ | ✗9 | ? | ? | ? | (vedi `FluidCoachMark`) |

## Come si aggiorna

A ogni fase chiusa: le celle toccate diventano ✓ solo con la prova (test, fotogrammi o misura citati
in `artifacts/3.0-stato.md`), mai "per costruzione". Le celle **?** si chiudono nella fase 10, una per
una; la fase 10 finisce quando non ne resta nessuna.
