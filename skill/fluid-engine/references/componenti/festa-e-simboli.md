# Festa e simboli

Due elementi di movimento piccoli (F8.5, dalla 4.0): **la festa** per un successo, i coriandoli di iMessage
(`FluidCelebration`), e **gli effetti dei simboli**, il vocabolario di SF Symbols sulle icone di `FluidIcons`
(`Modifier.fluidSymbolEffect`, `Modifier.fluidSymbolEffectWhile`, `FluidSymbol`). Stanno in `engine-ui`, pacchetto
`dev.antigravity.fluidengine.ui.fluid`. Catalogo delle firme: [../api/movimento.md](../api/movimento.md) (la festa) e
[../api/icone.md](../api/icone.md) (i simboli).

**Il modello mentale.** La festa è uno strato sopra tutto che esiste solo mentre piove: si accende con `launch()`, si
spegne da solo e poi non c'è più (zero ridisegni, livello rilasciato). Un effetto dei simboli è un'animazione **del
simbolo**, non dell'app: si dice quale e quando (un valore che cambia, o «finché»), l'engine sa tempi, curve, ampiezze e
il movimento ridotto. I numeri sono di Apple, misurati fotogramma per fotogramma sulle sessioni WWDC (il KDoc di
`FluidSymbolProfile` dice quale video e quale minuto).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| festeggiare un successo (un bel voto, la lista finita) | `val festa = rememberFluidCelebrationState()`, `FluidCelebration(festa)` ultimo in un `Box` a tutto schermo, poi `festa.launch()` |
| i coriandoli che partono da un tasto (l'installazione riuscita) | `festa.launch(centroDelTastoNellaRadice)` (da `onGloballyPositioned { it.boundsInRoot().center }`) |
| far rimbalzare la scheda scelta della tab bar | `FluidIcon(icona, null, Modifier.fluidSymbolEffect(FluidSymbolEffect.Bounce, trigger = selezioni))` |
| la campanella che dondola a una notifica | `FluidSymbolEffect.wiggleRotation(pivotY = 0.12f)` col `trigger` del numero di notifiche |
| la sincronizzazione che gira finché lavora | `Modifier.fluidSymbolEffectWhile(FluidSymbolEffect.Rotate, active = sincronizza)` |
| il microfono che respira mentre ascolta | `fluidSymbolEffectWhile(FluidSymbolEffect.Breathe, ascolta)` |
| le tacche del Wi-Fi o le onde del volume in fila | `FluidSymbol(FluidIcons.Wifi, …, effect = FluidSymbolEffect.VariableColor, active = cerca)` |
| la campana che diventa barrata, il microfono muto (la Magic Replace) | `FluidSymbol(if (muto) FluidIcons.NotificationsOff else FluidIcons.Notifications, …)`: cambiare l'icona basta; fra icone parenti la barra si disegna da sola |
| play che diventa pausa (`.replace`) | `FluidSymbol(if (suona) FluidIcons.Pause else FluidIcons.PlayArrow, …)`: icone non parenti, la sostituzione sfocata |
| la spunta che si disegna (Draw On) e si cancella (Draw Off) | `FluidSymbol(FluidIcons.CheckCircle, …, drawn = salvato)` |
| un effetto due volte, o più svelto | `FluidSymbolEffect.Bounce.repeat(2)`, `.speed(1.5f)` |

## La festa: `FluidCelebration`

- **Come iMessage.** Senza punto (`launch()`) i coriandoli piovono dall'alto su tutto lo strato, scaglionati sopra il
  bordo; con un punto nella radice (`launch(origine)`) scoppiano a ventaglio verso l'alto (±55°), frenano e ricadono.
  Strisce, quadratini e qualche tondo nei colori di sistema di Apple (`FluidCelebrationDefaults.colors()`, chiari o scuri
  col tema); ogni pezzo gira nel piano e in 3D (si schiaccia e cambia luce), sente la gravità e una resistenza che
  dipende dalla faccia, così scende a strappi come la carta e ondeggia di lato. La pioggia dura ~4 s.
- **L'aptica.** Alla partenza il doppio colpetto dell'engine, `FluidHapticEvent.Success`. `haptic = false` lo spegne se
  l'app ne suona già uno suo.
- **Il costo.** Uno strato solo (un `Spacer` col suo livello), disegnato in un passaggio sul canvas nativo con un
  pennello solo: niente un composable per pezzo. I pezzi stanno in vettori preparati una volta (`FluidConfetti`,
  160 posti); il passo e il disegno non allocano (provato). Il contenuto sotto non ridisegna. Finita la festa
  `isCelebrating` torna falso, lo strato esce dalla composizione e il livello si rilascia: **zero ridisegni**.
- **I dispositivi deboli.** 120 pezzi sul telefono di riferimento; la scala di F7.10 (`particlesFor`: 120, 72, 48) è
  pronta e si collegherà con F7.11. Il numero è un parametro interno: niente manopole per l'app.
- **«Riduci movimento»**: niente pioggia. Un segno fermo e breve (metà dei coriandoli già posati attorno al punto, o in
  alto), disegnato una volta, che dopo 0,9 s svanisce in 0,25 s (solo opacità); l'aptica resta.
- **Uno strato senza misura** (a misura zero, o mai piazzato): la festa aspetta 30 fotogrammi e finisce senza niente,
  `isCelebrating` torna falso (R2).

## Gli effetti dei simboli

| effetto | Apple | quanto dura | cosa fa |
|---|---|---|---|
| `Bounce`, `BounceDown` | `.bounce` | 0,6 s | +25 % a 0,2 s, −9 % a 0,4 s, si posa (WWDC23 10197) |
| `Pulse` | `.pulse` | 2,0 s | l'opacità scende al 48 % e torna (10197) |
| `Wiggle`, `WiggleUp`, `WiggleClockwise`, `wiggleRotation(…)` | `.wiggle` | 0,9 s | dondolio smorzato, picco 7 % della taglia o 10° (WWDC24 10188) |
| `Rotate`, `RotateCounterClockwise` | `.rotate` | 1,9 s un giro | parte svelto e frena a lungo; attivo: rampa di 0,8 s e 180°/s (10188) |
| `Breathe` | `.breathe` | 3,0 s | +23 % e schiarito al 65 %, salita e discesa di 1,35 s (10188) |
| `VariableColor`, `VariableColorCumulative` | `.variableColor` | una tacca ogni 334 ms | spente al 24 %, accese in 290 ms; a uno a uno o a riempire (10197) |
| Magic Replace | `.replace` fra simboli parenti (iOS 18) | 0,7 s | resta la parte comune; la barra si disegna in 267 ms e taglia la base (o si ritira in 300), il pallino entra in dissolvenza dopo l'intaglio (pieno a 233 ms) con un rimbalzo, la base rimbalza di ±4–5 %; nitida (WWDC24 10188) |
| sostituzione | `.replace` fra simboli non parenti | 0,35 s | la vecchia esce e la nuova entra sfocate, curve R7 dello scambio d'icona |
| Draw On / Draw Off | `.drawOn`, `.drawOff` (iOS 26) | 0,8 s | la penna percorre i tratti, per strato (WWDC25 337) |

- **Due modi.** `fluidSymbolEffect(effetto, trigger)` suona una volta a ogni cambio di `trigger` (la prima composizione
  non suona), come `symbolEffect(_:value:)`; `fluidSymbolEffectWhile(effetto, active)` va finché `active` è vero e, spento,
  **finisce il ciclo in corso** (la rotazione finisce il giro frenando), come `symbolEffect(_:isActive:)`.
- **Il modificatore o `FluidSymbol`.** Il modificatore lavora sul simbolo intero, nel livello del contenuto: scala,
  spostamento, rotazione, opacità. Funziona su qualunque icona o immagine, e il contenuto non ridisegna. `FluidSymbol`
  disegna l'icona da sé e sa i suoi **strati**: il colore variabile per tacche, la sostituzione e Draw On / Off.
- **La sostituzione** (F8.5b) è la **Magic Replace** dove i tracciati lo permettono, come in iOS 18: le due icone si
  confrontano su una griglia (`SymbolMagicPlanner`, una volta per coppia); se hanno almeno il 75 % in comune sono
  parenti, e cambia solo ciò che è diverso. I pezzi nuovi o persi lunghi e stretti (la barra, le onde) diventano
  **bande** che una penna percorre o da cui si ritira, e ciò che cambia lungo la banda (il taglio nella campana) segue la
  penna; i pezzi raccolti (un pallino) compaiono o spariscono da soli, interi, e l'intaglio che fa loro posto nella
  base si apre in un fotogramma prima che il pallino arrivi e si richiude quando è svanito. Si disegna coi tracciati
  esatti (le operazioni sui tracciati), senza sfocare. Fra icone che non sono parenti (play e pausa) resta la sostituzione sfocata.
  Le icone pensate per la Magic Replace: `FluidIcons.NotificationsOff`, `FluidIcons.MicOff`,
  `FluidIcons.NotificationsUnread` (la campana col pallino; e `NotificationsActive`, `VisibilityOff`, `VolumeOff`,
  `CloudOff` che c'erano già).
- **Nitido sotto il dito.** La regola di Alessio (24 novembre): ciò che muove il dito resta nitido, si sfoca solo ciò che
  l'app muove da sola. Gli effetti dei simboli partono dall'app (un valore che cambia, uno stato): nessuno segue un gesto
  e nessuno sfoca sotto il dito; la sola sfocatura è la sostituzione fra icone non parenti, che è un cambio di contenuto.
- **Gli strati** si ricavano dal tracciato, una volta per icona (`FluidSymbolGeometry.kt`): un contorno con i suoi buchi
  è uno strato; uno strato sottile che si divide in due fianchi da un capo all'altro è un **tratto** (la spunta, gli archi
  del Wi-Fi, le onde dell'altoparlante). Il colore variabile accende i tratti dal più piccolo al più grande e lascia acceso
  il resto (il corpo dell'altoparlante). Le icone pensate per il colore variabile sono `FluidIcons.Wifi` e
  `FluidIcons.VolumeUp` (F8.5).
- **Draw On / Off** è fedele dove l'icona è fatta di tratti: la penna percorre il tratto da un capo all'altro (dal capo
  più a sinistra) con la punta tonda; una sagoma con un buco (la campanella, un anello) si disegna girando da ore 12; il
  resto (un punto, una croce o una freccia, che nel contorno di Material sono un pezzo solo) compare al suo turno.
- **Un solo orologio per icona**: un giro di fotogrammi che scrive un solo stato, letto solo nel livello e nel disegno.
  Niente ricomposizioni per fotogramma, niente allocazioni nel giro; a riposo il giro si ferma e l'icona non ridisegna.
- **«Riduci movimento»** toglie scala, spostamento e rotazione (rimbalzo, dondolio e rotazione non fanno niente, e un
  effetto continuo che muove soltanto **non chiede fotogrammi**, R2); restano le opacità: la pulsazione, il colore
  variabile, il respiro che schiarisce senza crescere. Draw On / Off sono immediati.
- **Una pagina coperta** (il ciclo di vita sotto STARTED, `LocalLifecycleOwner`: la pagina sotto in `FluidNavHost`, l'app
  in sottofondo): il giro dei fotogrammi si ferma e riparte al ritorno, anche per gli effetti continui (R2).

## Cosa fa l'engine e cosa resta all'app

- **L'engine**: i tempi, le curve, le ampiezze, la fisica dei coriandoli, il ciclo di vita dello strato e dell'orologio,
  l'aptica della festa, il movimento ridotto.
- **L'app**: quando (il voto, l'installazione, la notifica), quale effetto, e dove (il punto dello scoppio). Non
  sceglie numeri.

## Trappole

- **La festa va sopra tutto.** Ultima in un `Box` grande quanto lo schermo, fuori dalle liste che scorrono: dentro una
  riga la pioggia si ritaglia nella riga.
- **Il punto dello scoppio è nella radice**, non nello strato: `boundsInRoot()`, non `boundsInParent()`.
- **Non far rimbalzare tutto.** Apple lo dice: gli effetti stancano se sono ovunque. Uno per azione, e quelli continui
  solo mentre c'è davvero un lavoro in corso.
- **Il colore variabile vuole un'icona a tacche.** Su un'icona di un pezzo solo l'intera icona sale e scende.
- **Un trigger che non cambia non suona.** Un contatore che cresce, non un `true` riscritto.

## Come si prova

- I test puri: `F8p5SymbolEffectTest` (ogni effetto con la sua durata e il ritorno a riposo, le misure contro Apple, gli
  effetti attivi che finiscono il ciclo, il movimento ridotto, gli strati delle icone, nessuna allocazione per fotogramma)
  e `F8p5CelebrationTest` (pioggia e scoppio che finiscono da soli, la caduta lenta, il passo e il disegno senza
  allocazioni, la scala dei dispositivi deboli).
- I test con l'albero Compose vero: `F8p5FestaSimboliUiTest` della galleria (la festa che parte, finisce, esce e dopo la
  finestra non ridisegna più; il segno fermo del movimento ridotto; ogni effetto che torna a riposo senza ridisegni).
- In galleria: «Elementi nuovi» › «Festa e simboli» (`--es gallery.page NewElements --es novita.scene festa-voto`,
  `festa-installa`, `simboli-tavola`, `simboli-app`), su pagina, foto chiara e foto scura, con «Movimento ridotto
  (prova)» e «Ripeti ogni 5 s» per misurare la festa.

## Dove guardare

`engine-ui/…/ui/fluid/`: `FluidCelebration.kt` (la festa, la simulazione e i suoi numeri), `FluidSymbolEffect.kt` (gli
effetti, l'orologio, il modificatore, `FluidSymbol`, i numeri di Apple in `FluidSymbolProfile`), `FluidSymbolGeometry.kt`
(gli strati e i tratti delle icone). Le misure: `artifacts/liquid-glass/sessioni/f8-5/riferimenti/misure.md`. La galleria:
`sample/…/novita/F8p5Scenes.kt`.
