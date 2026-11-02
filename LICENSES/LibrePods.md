# LibrePods

Dalla 3.0 `engine-ui` include, **come sorgente**, due pezzi di LibrePods: il comportamento dei suoi
pulsanti — come crescono sotto il dito, come lo seguono, come vibrano — e quello delle sue pagine —
come si aprono, come tornano indietro sotto il dito, la pagina come una carta. Il codice è portato,
non riscritto da una descrizione: per questo l'engine, e ogni app che lo distribuisce, è sotto
GPL-3.0.

- Progetto: **LibrePods** — "AirPods liberated from Apple's ecosystem"
- Autori: **LibrePods contributors**, Copyright (C) 2025 LibrePods contributors
- Repository: https://github.com/librepods-org/librepods
- Ramo e commit da cui abbiamo portato: `android/rewrite`, `3d4d270` (28 ottobre 2026)
- Percorsi, relativi a `android/app/src/main/kotlin/me/kavishdevar/librepods/`:
  `presentation/components/primitives/StyledButton.kt`, `.../StyledIconButton.kt`,
  `utils/DragUtils.kt` (i pulsanti) e `presentation/navigation/SwipeBack.kt`,
  `presentation/components/primitives/StyledScaffold.kt` (le pagine)
- Licenza: **GNU General Public License, versione 3** — testo: https://www.gnu.org/licenses/gpl-3.0.html.
  I file di LibrePods dicono "versione 3, o una qualunque versione successiva"; `SwipeBack.kt` si
  dichiara "mostly AI generated" e, come i file di navigazione, non porta l'intestazione dei file di
  primitive, ma il repository è GPL-3.0 per intero e lo trattiamo così
- Copyright (C) 2025 LibrePods contributors

## Cosa questo vuol dire per chi usa l'engine

**Un'app che include `engine-ui` dalla 3.0 in poi è un'opera derivata, e va distribuita sotto
GPL-3.0**: il codice sorgente dell'app a chi riceve l'APK, con la stessa licenza. Fino alla 2.13
non era così: il comportamento di LibrePods era stato guardato e riscritto, senza copiare codice (era
la regola, e stava scritta nel `README.md` e nel `CHANGELOG.md` della 2.12.0). Dalla 3.0 la regola è
un'altra, per decisione di Alessio (31 ottobre 2026): le app Pampa che usano l'engine sono GPL-3.0, e i tasti si
comportano **esattamente** come quelli di LibrePods perché portano lo stesso codice; le pagine
partono dal suo e se ne allontanano dove l'elenco qui sotto lo dice.

Il nome e il logo di LibrePods non ci appartengono: non li usiamo se non qui, per dire da dove viene il
codice, e senza dire che il progetto abbia a che fare con le nostre app.

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/.../ui/fluid/FluidPress.kt` | le molle (0,5; 300 per la scala, 1; 300 per la deriva), la deriva elastica `m·tanh(0,05·d/m)`, l'allungamento nel verso del dito, la scala `1 + maxScale` (0,10 i tasti con testo, 0,5 i tondi), il dito come somma di spostamenti | `StyledButton.kt:230-255`, `:326-389`; `StyledIconButton.kt:134-137`, `:158-177`, `:194-244` |
| `engine-ui/.../ui/fluid/FluidButtonGesture.kt` | il ciclo del puntatore: pressione al contatto, nessun consumo, una sola fine per rilascio e annullo | `StyledButton.kt:326-389`; `StyledIconButton.kt:194-244`; `utils/DragUtils.kt` |
| `engine-ui/.../ui/haptics/FluidHaptics.kt`, `AndroidFluidHaptics.kt`, `FluidHapticGrain.kt` | i momenti e le costanti aptiche: `SegmentFrequentTick` al contatto, `Reject` al rilascio, `ContextClick` al click, un `SegmentFrequentTick` per ogni evento con distanza al quadrato > 350 px² | `StyledButton.kt:321`, `:336-338`, `:361-363`, `:382-384`; `StyledIconButton.kt:154`, `:198`, `:219`, `:235-239` |
| `engine-ui/.../ui/glass/interaction/GlassInteraction.kt` (`glassButtonGlowRecipe`) | i numeri del bagliore sotto il dito: velo 0,10, punto 0,15, raggio il lato lungo, plateau mezzo raggio. Lo shader è di Kyant (Apache-2.0) e non è cambiato | `StyledButton.kt:277-311` |
| `engine-ui/.../ui/fluid/GlassMaterial.kt` (`GlassDefaults.buttonOptics`) | la lente del pulsante: altezza 12 dp, spostamento 24 dp, profondità, dispersione cromatica | `StyledButton.kt:220-229` |
| `engine-ui/.../ui/page/FluidPageHost.kt` | i due strati — la pagina sotto e quella sopra in una `Box`, traslate in `graphicsLayer` — e la carta: gli angoli del telefono (`rootWindowInsets.getRoundedCorner`) e l'ombra da 36 dp, nera su superficie chiara e grigia su scura, poi il ritaglio | `SwipeBack.kt:198-262`; `StyledScaffold.kt:111-127`, `:227-241` |
| `engine-ui/.../ui/page/FluidPageHostState.kt` | il flusso avanti, indietro e predittivo: la corsa in pixel (parte da W in avanti, da 0 indietro, e va a 0 o a W), il back che segue durante il gesto e conferma o annulla alla fine, la corsa che si accumula e non scende mai sotto zero, il rilascio | `SwipeBack.kt:103-136`, `:152-182`, `:184-196`, `:226-257` |
| `engine-ui/.../ui/page/FluidPageInput.kt` | il trascinamento «ovunque» (un `draggable` orizzontale sulla pagina, vince quello che un figlio consuma) e la sua regola di rilascio | `SwipeBack.kt:152-182`, `:184-196`, `:226-257` |
| `engine-ui/.../ui/page/FluidPageMotion.kt` | la direzione di un cambio di pila (indietro se la cima c'era già), la parallasse della pagina sotto `−(W/3)·(1 − p)`, la regola del rilascio (0,35 W oppure una spinta), gli angoli della carta e la sua ombra | `SwipeBack.kt:56-60`, `:209-210`, `:232`; `StyledScaffold.kt:111-127`, `:227-241` |

Ogni file porta in testa il commento di provenienza: il file e le righe di LibrePods, il commit, la
licenza e l'elenco di quello che abbiamo cambiato.

## Cosa abbiamo cambiato

- **I nomi, la lingua dei commenti e il formato.** Le formule e le costanti sono le loro.
- **La pressione è uno stato condiviso** (`FluidPressState`), letto insieme dal click, dal layer del
  vetro e dal bagliore: in LibrePods ogni pulsante ha tre `Animatable` dentro il composable. La scala
  massima non è un parametro del chiamante ma una proprietà del feedback (`FluidPressFeedback.maxScale`),
  con un tetto sulla crescita del lato lungo che fa il lavoro del `maxScale = 0,05` che LibrePods
  passa a mano ai suoi tasti a larghezza piena.
- **L'aptica è il vocabolario dell'engine** (`FluidHapticEvent.PressDown`, `PressRelease`, `PressClick`,
  solo di piattaforma) invece di `LocalHapticFeedback` chiamato dal pulsante, ed è dietro l'interruttore
  delle app e al risparmio energetico. I momenti e le costanti sono quelli di LibrePods. Un tasto che
  apre qualcosa di suo (un menu) mette il proprio evento al posto del click.
- **Il click lungo**, che LibrePods non ha e che i nostri menu usano: scatta se il dito è rimasto entro lo
  slop per il tempo del click lungo, e dopo non c'è click al rilascio.
- **L'accento, l'accessibilità, il movimento ridotto**: il tasto cresce la metà e non segue il dito;
  tastiera, fuoco e TalkBack restano a `clickable`.
- **Il vetro è il nostro** (Kyant, Apache-2.0): la lente di LibrePods vale sotto il dito, e a riposo il
  pulsante tiene la lente di sempre; `GlassRole.Interactive` non è cambiato.
- **La grana in movimento ha due stili**: quello di LibrePods (di serie) e quello che l'engine aveva
  scritto prima, a velocità misurata (`FluidHapticGrainStyle`), per poterli confrontare in mano.
- **Le pagine non stanno sotto il `progress`, ma sotto il dito.** Il back di sistema segue `touchX`, la
  posizione vera del dito, e non il `progress` che LibrePods legge (`SwipeBack.kt:156-162`): il sistema
  lo smussa e lo scala, e una pagina agganciata a lui corre davanti al dito. Il riconoscitore del
  trascinamento sta sull'host e non sulla pagina che si muove (dentro un layer traslato le posizioni
  del dito sono relative al layer), la corsa è `dito − contatto` dal fotogramma che supera la soglia, e
  il back di sistema arriva dal dispatcher di `navigationevent` e non da `PredictiveBackHandler`, così
  i gestori interni dell'app restano prima di noi.
- **Le molle partono dalla velocità del dito**, al posto dei `tween(150)` e `tween(220)` di durata
  fissa (`SwipeBack.kt:128-131`, `:165-177`, `:236-249`). Il rilascio ha un tetto in dp (sui tablet 0,35 W
  è mezzo braccio) e una spinta contraria annulla anche oltre la distanza; la spinta è in dp/s e non in
  px/s.
- **Un gesto confermato chiama `onBack()` prima della molla**, e la molla continua mentre la pila si
  accorcia: niente canale di richieste (`SwipeBack.kt:139-149`), l'host fa il confronto fra le pile.
- **La carta c'è solo mentre si muove**, e ha gli angoli del telefono solo dove l'host tocca un angolo
  dello schermo (un pannello di dettaglio non li ha); la pagina sotto è composta ma non disegnata
  mentre è coperta, fuori dalla semantica e col ciclo di vita a `CREATED`.
- **Gli stili `ShrinkSlide` ed `Expand`, il passo fra schede e la classificazione di sostituzioni e
  salti di più pagine** sono nostri: LibrePods ha un solo movimento.

## Cosa NON abbiamo preso

Il tasto tondo su Haze Glass, la barra ancorata, le righe, l'interruttore e il cursore di LibrePods:
`docs/riferimenti-3.0.md` li descrive e li confronta con i nostri, ma qui non c'è codice loro. Delle
pagine non abbiamo portato il tasto indietro in overlay e il suo «fantasma» verso la radice
(`StyledScaffold.kt:263-353`), il `SharedTransitionLayout` e `NavDisplay` (`AppNavGraph.kt:88-120`), il
titolo in `Crossfade` (`StyledScaffold.kt:380-387`) e la guardia del doppio push
(`RenderScreenContent.kt:92`): la nostra barra ancorata, quando arriva, è un'altra costruzione. Se un
giorno se ne porta un pezzo, qui si aggiunge la riga.

## Cosa fare in pratica

Riportare **LibrePods — GPL-3.0 — Copyright (C) 2025 LibrePods contributors** fra le licenze di terze
parti nella schermata "informazioni" dell'app, e dire che l'app è distribuita sotto GPL-3.0. L'engine
fornisce già la voce: `fluidLicensesSection()` elenca da sola tutte quelle di `FluidEngineLicenses`, e
quindi anche questa.

## Kyant e LibrePods

LibrePods, a sua volta, ha preso da Kyant0 ([AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
Apache-2.0) l'algoritmo di `inspectDragGestures`, le formule di deriva e allungamento (`LiquidButton.kt`)
e lo shader del bagliore. Quelle parti le avevamo già, dalla fonte, e restano elencate in
`LICENSES/AndroidLiquidGlass.md`.
