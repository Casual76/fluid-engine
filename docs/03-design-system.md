# 03 · Design system

`engine-ui` è il livello visivo dell'app: non un tema Material con i colori cambiati, ma una superficie disegnata da capo sopra Material 3. Chi ci lavora deve sapere quali sono le regole, perché sono poche e sono tutte deliberate.

## Le cinque decisioni

**1. Gli angoli sono continui, non circolari.** Un angolo circolare unisce un lato dritto a un arco, e la curvatura salta da zero a `1/r` in un punto: l'occhio legge quel salto come una piega. `ContinuousCornerShape` fa salire e scendere la curvatura, senza giunzione. È metà del motivo per cui una card qui non sembra una card Material. Non usare `RoundedCornerShape` nel codice nuovo.

**2. Il carattere è Inter, con due tagli.** Testo sotto i 20sp, display sopra. Il tracking segue una curva dipendente dalla dimensione (negativo nei titoli, positivo nelle didascalie) perché Inter a tracking zero sembra largo nei titoli e stretto in piccolo. `fluidTypography()` monta tutto sugli slot di Material: si continua a chiedere `MaterialTheme.typography.titleMedium` e si ottiene la scala iOS 34/28/22/20/17/15/13/11.

**3. La palette parte da un colore solo.** Un accento genera l'intera scala di superfici, con un velo di tinta che diventa più forte solo sui contenitori piccoli e alti. Le superfici grandi restano quasi neutre. Cambiare `brand` cambia tutta l'app, coerentemente. I ruoli sono sempre coppie: un colore di sfondo non viaggia mai separato dal colore di contenuto che ci si legge sopra.

**4. Le liste sono raggruppate.** Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia il testo. La categoria della riga sta sulla piastrella dell'icona, mai sullo sfondo della riga: colorare le righe trasforma una lista ordinata in un patchwork.

**5. Il movimento è un vocabolario, non un effetto.** Tutto passa da `FluidMotion` (molle) e `FluidMotionScheme` (che le passa anche ai componenti Material). Una pagina in arrivo non sfuma mai: le pagine sono opache (da destra, di serie) e quella che si muove sta sotto il dito, così non ci sono mai due pagine leggibili sovrapposte né un back che corre davanti al dito (vedi «Le pagine e `FluidNavHost`»). `FluidMotionPolicy` spegne il movimento decorativo quando la scala di animazione di sistema è a zero — va rispettata, non aggirata.

## Fluid Glass: dove vive e dove no

Il vetro è chrome — barra superiore, tab bar/rail, azioni, notifica globale, indice laterale, modali
— **e, dalla 1.5.0, anche contenuto**, ma solo dove c'è qualcosa da guardare attraverso.

Fino alla 1.4.0 la regola era "solo chrome", e non era una scelta estetica: le app della famiglia
sono pile di superfici grigie, e il vetro sopra il grigio è invisibile per costruzione. Una superficie
che rifrange una pagina piatta produce una pagina piatta, quindi il materiale non aveva niente da
fare e la sua assenza si leggeva come un difetto. La risposta non è stata mettere vetro su tutto: è
stata dare alle pagine **un fondale che vale la pena guardare attraverso**, e solo dopo metterci il
vetro sopra.

### Due fondali, non uno

È il vincolo che decide tutto il resto. Se una card di vetro stesse dentro il corpo registrato,
campionerebbe una registrazione **che contiene se stessa**: feedback ottico, la cosa che nel vetro si
vede immediatamente e non si può nascondere. Quindi `FluidScreen` registra due cose diverse:

```
┌─ FluidScreen ──────────────────────────────────────────────┐
│  ① canvas ambientale (opaco) ──registrato──▶ canvasBackdrop │
│     lavata di colore della sezione + motivo             │   │
│  ② LazyColumn del contenuto  ──registrato──▶ bodyBackdrop│  │
│     ├─ FluidListGroup(glass = true) ─rifrange─◀──────────┼──┘
│     └─ righe, testo (solidi)                            │
│  ③ chrome: barra, tab bar, modali ──rifrange─◀───────────┘
└────────────────────────────────────────────────────────────┘
```

Il canvas è disegnato e registrato *prima* della lista, quindi non la contiene. Il corpo contiene
tutto e lo rifrange la chrome, come sempre. **La pagina nel suo insieme resta opaca**, perché il
canvas è opaco: la traslucenza è *interna* alla pagina, mai fra pagine — che è quello che tiene in
piedi l'invariante delle transizioni di rotta e i test che la verificano.

Il tetto di costo che ne segue: **il vetro va sul contenitore, mai sulla riga.** Un `FluidListGroup`
di dodici righe è *un* nodo di vetro, non dodici.

### La taratura

Il raggio di riferimento è **2,5 dp**, non 16. Il numero è andato 8 → 16 → 2 → 2,5 (il mezzo dp
dell'ultimo passo è una taratura a occhio: il vetro di base un filo più smerigliato), e il giro di
mezzo è l'errore che vale la pena non rifare: sopra gli ~8 dp un pannello smette di trasmettere
un'immagine e trasmette la sua *media*, e un pannello che tiene una media è un riempimento — tutto il
lavoro che la lente fa sul bordo sta allora piegando un colore piatto in un altro colore piatto. Quello che
identifica il materiale è la **dislocazione** al perimetro, non la brina, quindi la lente sale a
19/29 dp (i numeri della capsula di Kyant) e il raggio scende. Le poche superfici che devono davvero
nascondere qualcosa — la barra superiore, con testo nitido che le scorre sotto — chiedono un multiplo
con `blurScale` invece di farlo pagare a tutte.

**La taglia conta (2.12.0).** Un pannello grande smerigliato come uno piccolo sembra un pannello
piccolo ingrandito: il bordo piega l'immagine di pochi dp su una superficie larga mezzo schermo, e lo
spessore non si legge più. Quindi sotto i 64 dp di lato corto un pannello resta com'è (tasti, chip,
pomelli e la pillola di navigazione: guadagno esattamente zero), e sopra il guadagno sale a
smoothstep fino ai 320 dp, dove la sfocatura è ×2, l'altezza di rifrazione ×1,6 (mai oltre mezzo lato
corto) e lo spostamento al bordo ×1,8. Una formula sola, `glassSizedOptics`, per `glassSurface` e
`fluidPhysicsSurface` (nel morph conta la taglia della sagoma, non quella del layer). La sfocatura
non supera mai l'altezza di lente che la copre, quindi il padding della cattura resta zero a ogni
taglia, anche mentre una sagoma cresce. `GlassOptics.sizeResponse = 0` tira fuori una superficie
dalla regola (lo scrim dei modali copre lo schermo ma non è un pannello grande, e lo fa); sotto API
33, senza lente, la regola è spenta. I numeri per ruolo a taglia piena sono nel codice, con il
motivo accanto, perché sono valori di partenza da giudicare su un dispositivo: un `Modal` grande
arriva a 17,5 dp di sfocatura, oltre gli ~8 dp di cui sopra, ed è voluto solo per i pannelli grandi.

E il film del vetro **non è `MaterialTheme.surface`**. Una barra dello stesso colore del fondo non è
un materiale traslucido, è niente: su un tema AMOLED la pillola di navigazione spariva. Il Liquid
Glass è un materiale chiaro e riflettente, che anche in tema scuro si legge come un pannello *più
chiaro* del fondo. `GlassDefaults.glassFilm()` parte da due grigi fissi e prende un terzo della
palette: abbastanza da seguire l'accento, non abbastanza da farsi trascinare a fondo.

### Due famiglie, un bordo, un pannello (3.0)

Dalla 3.0 il vetro ha **due famiglie**, e ogni elemento è dell'una o dell'altra. La **lastra** è ciò che
sta: pannelli, barre, card, gruppi, menu, i tasti di vetro a riposo. La **goccia** è ciò che si afferra
e scorre: i pomelli di slider e interruttore, la pillola della tab bar, quella del segmentato, la goccia
dei menu; a riposo è piena, sotto il dito diventa lente (ricetta di Kyant, `glass/interaction/GlassDrop.kt`,
interna). Cosa vale per l'una e per l'altra, le eccezioni note e lo stato di ogni componente sono in
[`10-coerenza.md`](10-coerenza.md); il perché in `regole.md` della skill («La goccia e la lastra»).

**Un materiale per i pannelli.** Popover, menu contestuale, finestra che si espande, foglio, anteprima
sollevata, notifica, callout, menu che si trasforma: un pannello flottante chiede
`GlassDefaults.panelOptics(compact = false, overSharpPage = false)` e `GlassDefaults.panelTint(colour = null)`,
e non scrive numeri suoi. È la ricetta che il popover aveva dopo le sue tarature (sfocatura 2,5, lente
14/18 dp, bordo 0,8 dp al 45%, nessuna ombra interna, ombra 18 dp al 36%) con la regola della taglia
sopra. `compact` (lente 7/9 dp) per un menu di poche righe, dove 14/18 mostrava due versioni dello
stesso oggetto nello stesso vetro; `overSharpPage` (sfocatura 3,5, o 2,8 se anche compatto) per un
pannello che nessuno scrim ha sfocato prima, come una notifica o un callout. `colour` tinge la pellicola
col colore della cosa da cui il pannello è nato. `modalTint()` e `floatingTint()` restano pubblici.

**Un bordo solo, un'ombra del vetro.** `glassSurface` e `fluidPhysicsSurface` disegnano lo stesso bordo
(`glassRimRecipe`, interna): luce bianca in `Plus` sul lato scuro, una riga scura (nero al 28%) in
`SrcOver` sul chiaro, col profilo dello shader — pieno sopra e sotto, zero ai fianchi. Prima la stessa
lastra, ferma o in viaggio, aveva due bordi. Niente ombra interna insieme a un bordo speculare. L'ombra
esterna è del vetro (`glassShadowColor`: nero al 16% sul chiaro, al 10% sullo scuro, per il peso del ruolo):
mai un `Modifier.shadow` sotto una superficie trasparente, che trapela dentro la sagoma. Sulla
**finestra che si espande** l'ombra è ora circa sei volte più leggera di prima: si giudica in mano.

### Il costo, e come si tiene

Una superficie di vetro rifà la propria cattura **solo quando si muove o cambia misura**. Un
`RenderNode` tiene i figli per riferimento, quindi quando la sorgente si ridisegna ogni superficie
che la sta rigiocando vede già il nuovo contenuto: l'unica cosa che invalida davvero una cattura è la
geometria. Ri-registrare a ogni frame, che è quello che il renderer faceva prima della 1.5.0,
significava otto rigiocate a schermo intero e otto catene di `RenderEffect` per fotogramma.

Segue una regola per chi scrive animazioni sul vetro: **non animare `intensity` su una superficie
grande.** `intensity` scala il raggio di sfocatura, il raggio decide il padding del layer, e il
padding è l'unica cosa che invalida la cattura — quindi ogni fotogramma della dissolvenza
ri-registra tutto. Per far *arrivare* un pannello si anima un `alpha` sul risultato finito.

Dalla 1.4.0 l'ottica non è più nostra: la disegna la libreria `backdrop` di Kyant
([AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0), copiata come
sorgente in `ui/glass/backdrop/` — vedi `LICENSES/AndroidLiquidGlass.md`. `GlassMaterial` non
disegna niente: decide **quanto** materiale riceve ogni tipo di superficie.

Un pannello, nell'ordine in cui lo vedono i pixel:

1. **cattura** — `glassBackdropSource` registra il corpo della schermata in un `GraphicsLayer`;
   ogni pannello campiona la stessa registrazione, trasformata nelle proprie coordinate;
2. **vividezza** — la saturazione sale sopra 1: il vetro concentra il colore che trasmette, e senza
   questo passaggio un pannello sopra una foto sembra plastica grigia;
3. **sfocatura** — piccola (2,5 dp di riferimento, che i pannelli grandi moltiplicano con la regola
   della taglia): è il film, non il raggio, a tenere leggibile quello che sta sopra, e un raggio
   grande cancella l'immagine invece di trasmetterla;
4. **lente** — un campo di distanza della forma stessa del pannello sposta il campione sempre di più
   verso il bordo, così lo sfondo si *piega* dentro il perimetro. È il passaggio che mancava;
5. **tinta, bordo speculare, ombre** — anello illuminato da un angolo, ombra interna che dà spessore,
   ombra esterna che stacca dalla pagina.

`GlassOptics` descrive tutto questo in termini fisici: `refractionHeight` è quanto in profondità dal
bordo si sente la piega, `refractionAmount` quanto lontano trascina il campione. `GlassRole` sceglie
la ricetta: `Bar` (larga, quieta, senza ombra), `Floating` (la capsula di navigazione, trattamento
completo), `Interactive` (quasi trasparente, lente al massimo, dispersione cromatica accesa),
`Modal` (sfocatura profonda, smusso largo), `Content` (card e gruppi lista dentro la pagina: nessuna
ombra esterna, perché una card è *nella* pagina e non sopra).

I controlli non sono più lenti dipinte: `glassControlSurface` piega davvero, si inclina verso il
dito con un `tanh`, si gonfia e si schiaccia sotto pressione e si illumina dove è stata toccata
(additivo — una riflessione speculare *aggiunge* luce; scurire al tocco è il linguaggio dei bottoni
di plastica). Un controllo che sta su una barra rifrange **la barra**, non la pagina: la barra
esporta il proprio materiale con `exports`, e chi ci sta sopra lo compone con
`rememberCombinedGlassBackdrop`. In pratica basta che la chrome fornisca `LocalGlassBackdrop`, cosa
che `FluidScreen` fa da sola.

**Vetro su vetro (2.13.0).** Dalla 2.13.0 non serve nemmeno quello: ogni superficie di vetro
(`glassSurface`, `glassControlSurface`, `fluidPhysicsSurface`, i pomelli di interruttore e cursore)
cerca il nodo di vetro piu' vicino fra i suoi antenati e ne campiona il **materiale** — il fondale
come lo vede lui, sfocato, rifratto e tinto, contenuto escluso — sopra a quello che le e' stato
passato. Un tasto su un pop-up, su un foglio, su una card di vetro o su una notifica sfoca quindi la
sfocatura del pannello invece di bucarla, anche quando l'app gli passa lo stato della pagina. Il
materiale entra nella pila sopra a cio' che sta fuori dal pannello e sotto a cio' che e' registrato
dentro (la pista di un interruttore resta visibile); un cablaggio a mano che lo contiene gia' non
viene toccato. Costa zero finche' dentro non c'e' vetro: un pannello registra il proprio materiale
solo mentre qualcuno lo campiona, e solo quando cambia.

Due casi lo dichiarano invece di ottenerlo da soli:

```kotlin
// Il contenuto non e' figlio del vetro su cui sta (una superficie che si trasforma, un fratello):
val pane = rememberGlassBackdrop()
Box(Modifier.fillMaxSize().fluidPhysicsSurface(state, backdrop, tint, exports = pane))
Column(Modifier.glassContainer(pane)) { FluidGlassButton(...) }

// Il pannello lo disegna l'app con un altro motore: un nodo con solo il materiale, poi il contenuto.
Box(modifier) {
  Box(Modifier.matchParentSize().glassMaterialSource(pane).liquidGlass(...))
  Column(Modifier.glassContainer(pane)) { /* vetro dell'engine */ }
}
```

`glassSurface(nestInContainer = false)` spegne il comportamento per la rara superficie che
ricostruisce da se' il materiale del pannello su cui sta (il puck della barra flottante).

Serve API 31 per sfocatura e vividezza, API 33 per la lente. Sotto la 31 `glassSurface` dipinge
`GlassTint.fallback`: un materiale definito e quasi opaco, non una trasparenza rotta chiamata vetro.

Vetro e resto dell'interfaccia usano finalmente **la stessa silhouette**. La vecchia eccezione — le
superfici live dovevano usare angoli circolari perché lo shader del rim non sapeva descriverne
altri — non esiste più: la lente legge i raggi da qualsiasi `CornerBasedShape`, quindi anche da
`ContinuousCornerShape`, e il rettangolo nudo di una barra a tutta larghezza è gestito come forma a
raggi zero.

Una superficie deve essere disegnata **dopo** il body che campiona. Per gli overlay appartenenti a
una schermata usare lo slot dedicato:

```kotlin
FluidScreen(
  title = "Archivio",
  overlay = { backdrop ->
    FluidSectionIndex(
      sections = sections,
      activeSectionKey = active,
      onSelectSection = onSelect,
      backdrop = backdrop,
      modifier = Modifier.align(Alignment.CenterEnd),
    )
  },
) { /* contenuto solido della LazyColumn */ }
```

Mettere un `glassSurface` dentro la `LazyColumn` registrata significa far campionare il materiale da
se stesso. Non farlo: spostare il controllo nello slot `overlay` oppure lasciarlo solido.

## Cosa c'è dentro

**Struttura di una schermata**

| | |
|---|---|
| `FluidScreen` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare solo quando il titolo passa sotto |
| `FluidScreen.overlay` | chrome flottante disegnato dopo il body, con il `GlassBackdropState` corretto per quella destinazione |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidFloatingTabBar` (+ `rememberFluidFloatingTabBarScrollConnection`), `FluidTabRail` | navigazione principale a pillola: la barra in basso, che si ripiega scorrendo, e il rail per gli schermi larghi |
| `FluidGlassModalPortal` (+ `FluidGlassModalHost` alla radice), `FluidAlert`, `FluidGrabber` | pop-up e fogli in vetro dentro la composizione, e avvisi: dalla 3.0 `FluidAlert` sta nell'host quando c'è (`inHost = false` per il `Dialog`), vedi «I menu liquidi» |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe |
| `FluidNotificationHost` | notifiche contestuali dentro l'app (`FluidNotification`, con `actions` dalla 2.12.0): la card si trascina, vedi sotto |
| `ProvideFluidChrome`, `rememberFluidChromeController` | il registro della barra e del bus "torna in cima" |
| `FluidNavHost`, `rememberFluidNavController`, `composable(…)` · `FluidPageHost`, `FluidPageHostState` | le pagine della 3.0: opache, da destra, il back sotto il dito; vedi «Le pagine e `FluidNavHost`» |
| `FluidFloatingCapsule`, `FluidCapsuleAction`, `fluidCapsuleInsets` | la capsula di vetro con poche azioni in fondo allo schermo: selezione, mini player, comandi di una foto |
| `FluidSearchField` | il campo di ricerca di vetro, che si apre da un cerchio |
| `FluidSheetScaffold`, `FluidConfirmSheet`, `FluidPromptAlert` | il contenuto di un foglio con i tasti fermi, la conferma, l'avviso che chiede una riga di testo |

**La notifica si tocca (2.12.0).** Un dito sulla card ferma il tempo di lettura, e al rilascio
riparte da quello che restava, mai da meno di 1,5 s. Tirata in giù segue il dito con la curva
elastica di `FluidScrollPhysics` e si allunga di layout (mai di scala): oltre 72 dp, o con una
spinta sopra i 1000 dp/s, si apre — testo intero, `FluidNotification.actions` (una o due
`FluidNotificationAction(label, onClick)`, che toccate eseguono e chiudono) e nessuna scadenza.
Spinta in su segue il dito uno a uno: oltre 40 dp, o con una spinta, viene lanciata via e la
notifica si chiude. Altrimenti torna con una molla senza rimbalzi. Tick aptico alla soglia di
apertura, `Threshold` al lancio; per TalkBack le azioni «Espandi» e «Chiudi». A card chiusa il
messaggio mostra al massimo due righe: scriverlo più lungo va bene, il resto si legge aprendola.

**Contenuto**

| | |
|---|---|
| `FluidListGroup`, `FluidListRow`, `FluidListDivider` | la lista raggruppata |
| `FluidCard`, `FluidHero`, `FluidEditorialCard` | superfici |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note: `FluidSectionHeader` è la didascalia quieta sopra un gruppo; `FluidSectionTitle` (eyebrow in accento + titolo grande) apre una sezione di pagina |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState` (con `illustration`), `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` (con `onClick`, `action`, `leading`) | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction` | stato della sincronizzazione |
| `FluidToggleRow`, `FluidChoiceRow`, `fluidAppearanceSection`, `FluidAccentPicker` | le righe delle impostazioni (l'interruttore è la riga, la scelta ha la spunta a destra) e la sezione «Aspetto» |
| `FluidToneTile`, `fluidGlassGroups`, `FluidGlassGroup` | la piastrella dell'icona, pubblica; una lista lunga in pannelli di vetro da otto righe |
| `FluidLiveDot`, `FluidSkeleton*`, `FluidSelectionMark`, `FluidPagerDots`, `FluidStepProgress` | i pezzi piccoli: pallino dal vivo, scheletri che respirano, cerchio di selezione, punti del pager, passi |

**Controlli**

`FluidButton` (Filled/Tinted/Plain/Destructive), `FluidSwitch` (il LiquidToggle di Kyant0: il pomello e' l'unica lastra, e un tocco ovunque lo commuta), `FluidSlider` (il LiquidSlider di Kyant0: una pillola bianca a riposo che sotto il dito diventa lente sulla pista, con tacche di scatto facoltative, e un trascinamento non lascia scorrere la pagina), `FluidChip`, `FluidSegmentedControl`, `FluidTextField` (con `secret`), `FluidColorDot`, `FluidBarAction`, `FluidGlassButton`, `FluidGlassIconButton` (con `FluidGlassSize` e `haptic`), `FluidMorphMenuButton` (+ `FluidMorphMenuHost` alla radice: il tasto che diventa il proprio menù), `FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`. Come rispondono al dito: vedi "La pressione" qui sotto.

**Token**

`FluidRadius` (Small 10, Control 12, Card 18, Group 22, Sheet 38), `FluidCapsuleShape`, `FluidTextStyles` (`uppercaseCaption`, `numeric`, `largeNumeric` con cifre tabulari), `FluidTone` (Primary/Success/Warning/Danger/Info/Neutral), `GlassDefaults` (`panelOptics`, `panelTint` per i pannelli flottanti), `GlassOptics` e `GlassRole` per il materiale traslucido.

**Deprecati**

Restano, compilano e funzionano — la rimozione è prevista per la 3.0 — ma non vanno nel codice nuovo. Dove la sostituzione è fedele il vecchio nome *delega* al nuovo: chi aggiorna l'engine prende il look giusto senza toccare una riga, e il compilatore dice dove andare quando c'è un motivo per andarci.

| Deprecato | Usa | |
|---|---|---|
| `FluidTabBar` | `FluidFloatingTabBar(items = …, scrollConnection = …)` | delega. La barra diventa una capsula grande quanto le schede, al centro (non più da lato a lato), alta 60 dp invece di 64; non si ripiega finché non le dai una `rememberFluidFloatingTabBarScrollConnection()` attaccata alla pagina con `nestedScroll`. Margini e `navigationBarsPadding()` restano di chi la posa. Le schede della barra dicono ancora se sono selezionate, ma non portano più il ruolo `Tab` né il tag di test `top_level_<rotta>` (il rail li tiene): un test cerca la scheda dall'etichetta. `FluidTabRail` resta |
| `FluidFoldingTabBar` | `FluidFloatingTabBar` | delega (dalla 2.0.0) |
| `FluidGlassMenuButton` | `FluidMorphMenuButton` + `FluidMorphMenuHost` | **non delega**: il nuovo vuole uno stato (`rememberFluidMorphMenuState()`) e un host alla radice, accanto a `FluidGlassModalHost`. Dalla 3.0 il vecchio passa dal pannello e dal dito dei menu nuovi (si sceglie trascinando e rilasciando). `FluidBarAction(actions = …)` non passa di qui e non cambia |
| `FluidSheet` | `FluidGlassModalPortal` | non delega: il foglio di piattaforma vive in una finestra sua e non rifrange la pagina. Resta per chi ha davvero bisogno di una finestra separata |
| `FluidGlassSwitch` | `FluidSwitch` | delega |
| `FluidQuickAction` | `FluidChip(selected = false)` | delega. La pillola è quella di `FluidChip`: velatura neutra, alta almeno 48 dp (erano 32), etichetta su una riga |
| `FluidListItem` | `FluidListRow` | delega. Il vecchio `subtitle` (sopra il titolo) è l'`eyebrow`, il vecchio `supporting` è il `subtitle`, `trailing` va nel `badge` |
| `FluidTopHeader` | `FluidScreen`, o `FluidCollapsingTitle` + `FluidCollapsingTopBar` | non delega: è una `LargeTopAppBar` di Material, e la barra giusta non è un componente da mettere in cima ma la schermata |
| `FluidHeroCard` | `FluidHero`, o `FluidCard(highlighted = true)` | non delega: una `Surface` di Material con angolo circolare e un'icona di default da scuola |

`FluidSyncAction` non è deprecato, ma cambia dentro: il dettaglio della sincronizzazione si apre come foglio di vetro (`FluidGlassModalPortal`) se l'app ha già un host dei modali alla radice, altrimenti resta il foglio di piattaforma di prima. Non serve aggiungere niente.

## Regole per chi scrive codice nuovo

- Colori solo da `MaterialTheme.colorScheme`. Un `Color(0xFF…)` in una schermata è un colore che non seguirà l'accento, non avrà una variante scura e non passerà i controlli di contrasto. Le uniche eccezioni sono i toni fissi warning/success, che nello schema Material non hanno un ruolo.
- Testo solo da `MaterialTheme.typography` o `FluidTextStyles`. Niente `fontSize` a mano.
- Angoli da `FluidRadius` attraverso `ContinuousCornerShape`, o da `MaterialTheme.shapes` (che è già mappato su quelli).
- Animazioni da `FluidMotion`. Una `tween` scritta a mano nel mezzo di una schermata è il modo più rapido per far sembrare quella schermata di un'altra app.
- Feedback al tocco con `fluidPressable` o `fluidRowPressable` (tinta, per le righe di una lista raggruppata). Una riga che cambia misura rompe la sagoma del gruppo. Un pulsante cresce (`feedback = FluidPressFeedback.Expand`), una superficie cede: vedi "La pressione".
- Il contrasto è testato: `FluidContrastTest` verifica le coppie. Se aggiungi un ruolo, aggiungi il caso.

## La pressione (2.11.0, e come LibrePods dalla 3.0)

Tutto in `ui/fluid/FluidPress.kt` (la pressione, la trasformazione, lo stato) e
`ui/fluid/FluidButtonGesture.kt` (il gesto). **I pulsanti crescono** (`FluidPressFeedback.Expand`
col testo, `ExpandRound` per i tasti tondi solo icona), seguono il dito e vibrano. **Le superfici
cedono** (`yielding`, il default di `fluidPressable`): una card che crescesse invaderebbe le vicine.
Animazioni ridotte: crescita dimezzata, niente deriva né allungamento, pressione comunque intera.

**Dalla 3.0 un pulsante è quello di LibrePods** — scelta di Alessio, e il codice è portato da lì
(GPL-3.0, `LICENSES/LibrePods.md`). Quello che era diverso nella 2.12 e non lo è più:

- **Premuto al contatto.** Senza ritardo, dentro una lista come in una barra (prima: 100 ms di
  attesa dentro una pagina che scorre, e `LocalFluidPressImmediate` per la chrome — oggi non fa più
  niente).
- **Non prende mai il gesto.** Non consuma niente: una passata che parte su un pulsante scorre la
  lista, *e* il pulsante cresce, segue il dito e vibra mentre scorre, fino al rilascio. **La regola
  della 2.12 ("un pulsante è di chi lo tiene premuto") non c'è più**: sostituita, perché una lista
  fatta di pulsanti si scorreva solo dai margini e il tasto che aspettava 100 ms "arrivava" invece
  di "scattare".
- **Il click è di `clickable`**: scatta se il dito si solleva sul pulsante e nessuno ha consumato,
  quindi non scatta dopo uno scorrimento. Tastiera (Invio, Spazio, fuoco, D-pad), TalkBack e ruolo
  restano suoi. Il **click lungo** (`fluidPressable(onLongClick = ...)`) scatta se il dito è rimasto
  entro lo slop per il tempo del click lungo — slop misurato sulla strada del dito sullo schermo —
  e dopo non c'è click al rilascio.
- **Numeri di LibrePods**: crescita `1 + maxScale` (0,10 col testo, **0,5 per un tasto tondo**; un
  tetto di 16 dp sul lato lungo fa da 0,05 ai tasti a tutta larghezza), molla (0,5; 300) in salita e
  in discesa che parte subito dal valore e dalla velocità del momento (non aspetta più di arrivare in
  cima), deriva elastica `m · tanh(0,05 · d / m)` che torna con una molla critica (1; 300),
  allungamento lineare e senza tetto. La deriva è la **somma dei movimenti del dito sullo schermo**:
  se la pagina scorre sotto un dito fermo sul tasto, il tasto lo segue.
- **Vetro**: la lente sotto il dito è quella di LibrePods (12/24 dp con profondità e dispersione,
  `GlassDefaults.buttonOptics()`), il bagliore è il suo (raggio = lato lungo, velo 0,10, punto 0,15).
  Un tasto di vetro ha **una** pressione: `val p = rememberFluidPressState()` passato sia a
  `glassControlSurface(pressState = p)` sia a `fluidPressable(pressState = p)`; senza, il vetro segue
  il dito da solo, e non consuma niente nemmeno lui.
- **Aptica a coreografia**, con le costanti di sistema: `PressDown` (`SegmentFrequentTick`) al
  contatto, un `SegmentFrequentTick` per ogni evento del dito sopra sqrt(350) px
  (`FluidHapticGrainStyle.LibrePods`, di serie; `Fluid` è la grana a velocità misurata di prima),
  `PressRelease` (`Reject`) al rilascio — anche di un tap che poi clicca —, `PressClick`
  (`ContextClick`) al click. L'ordine di un tap è `PressDown`, `PressRelease`, `PressClick`. È
  l'**unica eccezione a "una vibrazione per momento"**. `fluidPressable(haptic = X)` sostituisce
  `PressClick` con X (un menu passa `Open`); `haptic = null` spegne tutto. Dopo un click lungo non c'è
  `PressRelease`: quel momento ha il suo colpo.

Le superfici che cedono (card, tessere, `fluidRowPressable`) non cambiano: `clickable` anche per il
puntatore, ritardo nelle liste compreso, e un solo `Tap`.

Regole: `fluidPressable` va **prima** di `clip`/`background`, o si muove solo il contenuto; il vetro
non si scala mai da un antenato (la trasformazione sta nel `layerBlock` di `drawBackdrop`). Il banco
di prova è la sezione "Tocco" della scheda Controlli del sample; i test Compose (pulsante, slider e
interruttore in `LazyColumn`/`verticalScroll`, tap in lista, notifica trascinata) stanno in
`sample/src/testDebug` per non portare Robolectric nelle app.

**Lo slider e' il LiquidSlider di Kyant0 (2.12.0).** Una pista sottile da 6 dp, registrata nel suo
layer, e una pillola 40x24 **bianca e opaca a riposo** che, tenuta, diventa una lente: rifrange la
pagina piu' una copia della pista che si dispiega, e si allunga con la velocita' (col modulo, non col
segno). La riga e' alta 48 dp. Con `snapValues` il valore si attacca alla tacca piu' vicina entro
`snapThreshold` (una frazione dell'intervallo, 0,03 di default; `1f` e' uno slider a gradini), con un
puntino sotto la pista e un solo tocco aptico per tacca; senza, `DragTick` a ogni ventesimo e
`Threshold` alle estremita'. Per un lettore di schermo e' uno slider (`progressBarRangeInfo` e
`setProgress`), con `contentDescription` e `stateDescription` («30 minuti») dati dall'app.

Il dito non e' della pagina: un dito sulla pillola, o a 10 dp dal suo bordo, e' suo subito, e ogni
cambio di quel puntatore viene consumato fino al rilascio, su entrambi gli assi; un dito sulla pista
nuda diventa suo a mezzo slop di lato (e piu' di lato che in verticale), mentre una passata che va in
verticale per prima resta della pagina; sollevato senza essersi mosso e' un tocco, e porta li' la
pillola. `FluidSwitch` segue la stessa regola (dalla 2.13.1, con un solo gesto su tutto il
controllo): un dito sul pomello e' suo subito e il pomello lo segue senza zona morta; un dito sulla
pista vuota lo prende a mezzo slop di lato e porta il pomello da dove sta; una passata che va prima
in verticale resta della pagina; sollevato prima di uno slop e' un tocco e commuta, ovunque cada.
Al rilascio il pomello va dalla parte piu' vicina, o da quella verso cui correva se la spinta era
veloce. Vibra (`ToggleOn`/`ToggleOff`) a ogni tocco che commuta e, trascinando, quando il pomello
passa la meta'; al rilascio non ripete la stessa vibrazione.

## Le pagine e `FluidNavHost` (3.0)

`ui/page/` (l'host, senza dipendenze da navigation) e `ui/navigation/` (`FluidNavHost`). Le pagine hanno
la loro meccanica, e non è più quella di `NavHost`, per un motivo preciso: il back predittivo di
`NavHost` cerca la transizione sul `progress` di sistema, che il sistema smussa e scala, passato per un
easing. Con `EaseEmphasized` a un quinto del gesto la pagina era a mezzo schermo: **fino a 2,5 volte il
dito**. Con `FluidNavHost` la pagina sta sotto il dito.

**Cosa fa.** Due strati **opachi** — la pagina in cima e quella sotto, composta ma non disegnata mentre è
coperta — e un orologio solo (`travel`, i pixel percorsi verso l'uscita): il dito, la molla e il tasto
indietro disegnano lo stesso fotogramma. Si apre da destra (da sinistra in RTL), la pagina sotto arretra di
un terzo (con `ShrinkSlide` ed `Expand` sta ferma: attorno a una pagina rimpicciolita si vede lei). Si torna indietro come in iOS: la pagina segue la **posizione vera del dito** (`touchX`, mai il
`progress`), da un punto qualunque o dal bordo; il rilascio conferma oltre il 35% della larghezza (tetto
180 dp) o con una spinta di 400 dp/s, una spinta contraria annulla; la molla parte dalla velocità del dito
e non rimbalza. Mentre si muove la pagina è una **carta**: angoli del telefono, ombra da 36 dp; a riposo
niente. Mai alpha, mai sfocatura, mai scrim. Fra due schede c'è il passo laterale di sempre (13%, la
vecchia sfuma in 150 ms), senza gesto. Nessuna vibrazione nel gesto. Con le animazioni ridotte i moti
autonomi saltano e il dito segue 1:1.

**Gli stili e i gesti.** `FluidPageTransition`: `Slide` (di serie, da destra), `ShrinkSlide`
(rimpicciolisce al 90% nel primo 30% della corsa, poi scivola: per la pagina che dice «sono una cosa sola
che si chiude»), `Expand` (si apre dal punto toccato, scala 0,91 → 1, opaca). `FluidBackSwipe`:
`Anywhere` (di serie: da qualunque punto, se nessun figlio — un cursore, un interruttore, una riga che
scorre di lato — si è preso il dito; uno scorrimento orizzontale arrivato al suo inizio passa la mano),
`EdgeOnly` (i primi 20 dp, per le pagine piene di gesti propri), `Off`. I tasti non consumano, quindi un
trascinamento orizzontale che parte su un tasto fa partire il back: come in LibrePods, e da giudicare
in mano.

**L'inventario.**

| | |
|---|---|
| `FluidNavHost(navController, startDestination, modifier, route, peer, state) { }` | il `NavHost` con le pagine Fluid; l'overload tipizzato (`startDestination: Any`, `route: KClass`, `typeMap`) e quello con un `graph: NavGraph` già costruito |
| `rememberFluidNavController(vararg navigators)` | un `NavHostController` col navigator delle pagine già dentro, per chi costruisce il grafo da sé |
| `NavGraphBuilder.composable(route, arguments, deepLinks, transition, backSwipe) { entry -> }` | una pagina del grafo; per stringa, per `KClass` e `composable<T>` con `reified`. **Stesso nome di quella di navigation-compose, altro import** (`dev.antigravity.fluidengine.ui.navigation.composable`) |
| `FluidPageNavigator` ("fluidPage") | il navigator: `navigate` = `pushWithTransition`, `popBackStack` = `popWithTransition`, solo API pubbliche di `NavigatorState` |
| `FluidPageHost(stack, onBack, modifier, transition, backSwipe, peer, onTransitionStart, onSettled, leaving, state, pageColor) { key -> }` | l'host senza navigation, per chi ha una pila sua: prende la pila obiettivo, fa il confronto (cima nuova = push, cima che c'era = pop, pari = passo fra schede) e un gesto confermato chiama `onBack` *prima* che la pagina finisca di uscire |
| `FluidPageHostState` / `rememberFluidPageHostState()` | `kind`, `progress`, `velocity`, `isInteractive`, `isMoving`, `from`, `to`: si legge dalle shell che devono sapere cosa si muove (la tab bar che cambia visibilità fra due pagine); leggerne `progress` in composizione ricompone a ogni fotogramma |
| `FluidPageScope`, `LocalFluidPageTransition` | dentro una pagina: `isCovered` (coperta, a riposo), `isFront` (davanti adesso, passa di mano a metà uscita), `transition` (lo stato dell'host) |
| `FluidPageTransition`, `FluidBackSwipe`, `FluidPageMove`, `FluidPeerMove<K>` | gli stili, i gesti, il movimento in corso (`None`, `Push`, `Pop`, `Back`, `Peer`) e l'ordine fra le schede |
| `FluidPageMotion` | le costanti e le funzioni pure dell'host (`CommitFraction`, `FlingDpPerSecond`, `backRelease`, `pageFrame`, `classifyStackChange`, `barMorph`, …) |
| `FluidPageChrome(title, modifier, onBack, backdrop, barTint, actions)` | la barra di una pagina che non è una `FluidScreen`: titolo agganciato, indietro, azioni, e il vetro se c'è una registrazione da rifrangere (`backdrop`); dentro un `FluidPageHost` si descrive e la disegna l'host, fuori si disegna qui (da mettere in un `Box` sopra il contenuto) |
| `LocalFluidPageBar` (interno) | il posto della barra che l'host dà a ogni pagina: la pagina vi registra la sua specifica (titolo, azioni, numeri del titolo agganciato), l'host la disegna una volta sola |


**La migrazione di un'app a `FluidNavHost`:**

1. `NavHost(` → `FluidNavHost(`, con lo stesso `navController`, `startDestination`, `route` e grafo.
   `rememberNavController()` resta, ma per un grafo costruito a mano con `NavGraph` serve
   `rememberFluidNavController()`.
2. L'import di `composable` diventa `dev.antigravity.fluidengine.ui.navigation.composable`: stesso nome,
   quindi le etichette `this@composable` compilano. Se resta un `composable` di
   `androidx.navigation.compose` nel grafo, in debug l'app si ferma con un errore che dice quali rotte
   (in release un log: quelle destinazioni non si vedrebbero).
3. Via le quattro transizioni (`enterTransition`, `exitTransition`, `popEnterTransition`,
   `popExitTransition`) e i loro helper: c'è lo stile della pagina (`transition = …`, `backSwipe = …`).
   `FluidPageTransition.Expand` dove si vuole tenere la crescita dal tocco, e via la tubatura
   dell'origine in `savedStateHandle`.
4. Via i `FluidRouteMotionHost` attorno alle destinazioni: vogliono un `AnimatedVisibilityScope` che le
   pagine Fluid non hanno, quindi non compilano. `FluidRouteMotionHost` resta, deprecato, per chi non migra:
   non sfoca più né arrotonda.
5. `peer = FluidPeerMove { from, to -> … }` dalla funzione che ordina le schede, che l'app ha già
   (positivo se `to` sta dopo `from`, zero se non sono pari).
6. Una shell con una tab bar che cambia visibilità fra due pagine legge `FluidPageHostState.progress`
   (si passa lo `state` a `FluidNavHost`).

Resta tutto di navigation: deep link, stato salvato, `ViewModel` per voce, `savedStateHandle`,
`navigation { }` annidati, `dialog { }`. `engine-ui` espone `navigation-compose` 2.9.8 come `api`: la
versione che hanno già tutte le app tranne Fluidify (2.8.5, che passa a 2.9.8). Cosa cambia a vista in
un'app che migra: pagine opache, angoli del telefono, la pagina sotto che arretra di un terzo, il back
che non corre più davanti al dito, e la barra che resta ferma (qui sotto).

**La barra ancorata (fase 8, passi 5-8).** Dentro `FluidPageHost`/`FluidNavHost` la barra non viaggia più con la
pagina: **una per host, ferma**. La pagina la *descrive* (`FluidScreen` e `FluidCollapsingTopBar` registrano una
specifica in `LocalFluidPageBar`, interno) e la disegna l'host, sopra le pagine e sotto la chrome dell'app. Il vetro
rifrange le due pagine insieme mentre si muovono (e la tab bar con lui: `FluidChromeController.setTransitionBackdrop`),
il titolo grande è contenuto e viaggia con la sua pagina, quello agganciato sfuma e scivola di 16 dp, il tasto indietro
è uno e sta fermo al pixel, i tasti di vetro diventano quelli della pagina accanto con una superficie Fluid-physics
(`FluidPhysicsState.driveExternally(from, to, progress)` fra gruppi di lastre). `FluidScreen` e
`FluidCollapsingTopBar` non cambiano firma: fuori da un host disegnano la barra di sempre. `FluidPageChrome(title,
modifier, onBack, backdrop, barTint, actions)` è la stessa cosa per una pagina che non è una `FluidScreen`: dentro un host si
descrive, fuori si disegna in cima a chi la chiama. **Un gesto che parte sulla barra è della barra** (mai il back: serve
anche al menu delle azioni, che si sceglie trascinando); i tasti dentro la pagina lasciano partire il back «ovunque»,
come LibrePods. Uno scroller orizzontale al suo inizio passa il gesto al back; uno slider mai. Con
`FluidListDetailScaffold` a due pannelli ogni pannello disegna la sua barra. **Deprecati**: `FluidRouteMotionHost` (ora
senza sfocatura né angoli), `FluidRouteMotion.ExitBlur`/`Expand*`, `RouteMotionSignals.hierarchical` e
`FluidMotion.DurationRouteFadeOut`. `ui.page.FluidCornerRadii` si chiama `FluidPageCorners` (il nome era già di
Fluid-physics). La demo è la riga «Pagine» nella scheda Chrome della galleria (`PagesDemoActivity`: indietro fermo,
azioni da una a tre che si trasformano, titolo grande che viaggia, gesto partito sulla barra).


## I pezzi che le app si riscrivevano da sole (3.0)

Ogni app aveva scritto a mano un pezzo che l'engine non aveva, e nel proprio KDoc diceva perché. Qui sono
tutti, con la ricetta della 3.0 (una pressione, una vibrazione, vetro su vetro da solo) e **nessuna firma
rotta**: dove un componente esistente cambia, sono parametri con default in coda. Le demo stanno in
`sample/…/catalog/` (`RowsAndSheetsDemo`, `CapsulesAndPartsDemo`, `MenusDemo`: nella galleria da Chrome ›
Catalogo della 3.0 e dalle voci di «Da provare») e nella scheda Controlli (`gapsDemoItems()`).

### Righe, aspetto, fogli, avviso con un campo

| | |
|---|---|
| `FluidToggleRow(title, checked, onCheckedChange, subtitle, enabled, tone, leading)` | la riga **è** l'interruttore: un bersaglio solo, `ToggleOn`/`ToggleOff` e niente `Tap` sopra, `Role.Switch` per TalkBack con un nodo solo («titolo, sottotitolo»); disattivata sfuma e non risponde. Cede, non cresce |
| `FluidChoiceRow(title, selected, onSelect, subtitle, enabled, tone, leading)` | scelta singola con la **spunta a destra** (spazio sempre riservato), non un radio; `Role.RadioButton`, un `Tick` solo se la scelta si sposta, `onSelect` anche sulla riga già scelta (il chiamante è spesso un foglio che deve chiudersi). Il gruppo, `FluidListGroup(Modifier.selectableGroup())` |
| `fluidAppearanceSection(settings, onSettingsChange, brand, presets, glassEnabled, onGlassEnabledChange, strings, glass, dynamicAccent, footnote, keyPrefix)` | la sezione «Aspetto» di una pagina di impostazioni (estensione di `LazyListScope`): tema in tre righe di scelta + «Nero assoluto» (spento nel chiaro), accento, aptica con un colpo di prova all'accensione, e il vetro **solo se l'app passa valore e callback** (non è un campo di `EngineSettings`). Non scrive se la scelta non cambia niente |
| `FluidAccentPicker(selected, onSelect, …)` / `FluidAccentPicker(settings, onSettingsChange, …)` | la fila di pallini (`FluidColorDot` in un `selectableGroup`, il pallino dei colori di sistema mostra il colore vero); due forme: `FluidAccentChoice` (`Brand`, `Dynamic`, `Preset(name)`) per un'app col suo modello, `EngineSettings` per chi usa lo store |
| `FluidAppearance`, `FluidAppearanceStrings` | la traduzione pura: `themeChoice`, `withThemeMode`, `isPureBlack`, `withPureBlack` (AMOLED = Scuro + nero, senza perdere il nero), `accentChoice`, `withAccent` (`DYNAMIC` senza supporto o spento è il marchio; scegliere i colori di sistema riaccende il colore dinamico), `presetName`, `accentLabel`, `hapticsPreview`; le parole, in italiano di serie |
| `FluidSheetScaffold(title, message, icon, tone, maxWidth, contentPadding, scrollable, insetBottom, actions) { }` | il contenuto di un foglio o di un pop-up: testata con la piastrella dell'icona, corpo che scorre e **tasti che non scorrono** (testata e tasti si misurano per primi, il corpo prende il resto, con un tetto finito senza altezza massima); larghezza massima 560 dp, testo `onSurface` |
| `FluidConfirmSheet(visible, onDismissRequest, title, onConfirm, message, icon, tone, confirmLabel, cancelLabel, destructive, confirmEnabled, presentation, origin) { }` | portale + scaffold: un tasto di conferma solo che dice cosa fa, `destructive` rosso con piastrella `Danger`; **non chiude da sé** (`onDismissRequest` = annulla, `onConfirm` = fatto: chiude chi lo possiede); le parole restano congelate mentre il foglio esce. Senza un `FluidGlassModalHost` non si apre e non va in crash |
| `FluidPromptAlert(title, onDismissRequest, onConfirm, message, initialValue, placeholder, confirmLabel, cancelLabel, validate, keyboardOptions, trim, selectAll, singleLine)` | un `FluidAlert` che chiede una riga di testo: il tasto è la validazione (`FluidPromptValidation.Valid` / `Incomplete` — non ha ancora scritto, il campo tace — / `Invalid(message)`; di serie `fluidPromptNotBlank`), il testo si ritaglia, Invio conferma, si apre sul campo col testo selezionato. Finestra di piattaforma: dipinge il suo vetro |

Una riga è **un nodo semantico solo**: il `ListItem` di Material fonde la propria semantica e Compose non
fonde un figlio che a sua volta fonde, quindi la semantica interna è azzerata (`clearAndSetSemantics`) e
conta solo ciò che la riga dice di sé (titolo e sottotitolo). L'informazione che serve a chi non vede sta
nel sottotitolo.

### La capsula, la ricerca e i pezzi piccoli

| | |
|---|---|
| `FluidFloatingCapsule(modifier, backdrop, visible, placement, fillWidth, onPhoto, avoidKeyboard, minHeight, contentPadding, label, leading, trailing) { }` | la capsula di vetro con poche azioni (una selezione, un mini player, i comandi di una foto): `glassSurface` ruolo `Floating` con la pellicola della barra, `visible` con entrata e uscita dai token, **tiene i tocchi** che nessuna azione ha preso |
| `FluidFloatingCapsulePlacement` · `fluidCapsuleInsets(placement, avoidKeyboard)` · `FluidFloatingCapsuleDefaults` | `Free`, `ReplacingTabBar` (stessi margini e altezza della barra), `AboveTabBar` (a `Spacing` sopra di lei); gli inset valgono per qualunque pezzo di vetro che galleggia lì; `contentInset()` dice lo spazio che la pagina si lascia |
| `FluidCapsuleAction(icon, contentDescription, onClick, enabled, destructive, tint, onLongClick, size)` | l'icona piatta che cresce sotto il dito, bersaglio da 48 dp: **niente lente per azione**; per quella che deve staccarsi (il «Fatto», il play) si mette nella fessura un `FluidGlassIconButton`, vetro su vetro da solo |
| `FluidSearchField(query, onQueryChange, modifier, backdrop, placeholder, labels, onSearch, expanded, onExpandedChange, navigation, autoFocus, onPhoto, keyboardOptions)` | il campo di ricerca di vetro: lente, testo, crocetta, e per la ricerca in pagina «3/12» con i due tasti (`FluidSearchNavigation(current, total, onPrevious, onNext)`, l'indice è da zero). **Si apre da un cerchio** (`onExpandedChange`, molla `standard`), prende il fuoco e la tastiera alla comparsa e li lascia alla chiusura. Le parole: `FluidSearchLabels` |
| `FluidLiveDot(modifier, color, size, pulsing, contentDescription)` | il pallino dal vivo: un respiro solo (900 ms, in fase con l'orologio: tutti i pallini battono insieme), solo l'opacità fra 0,35 e pieno, fermo e pieno con le animazioni ridotte |
| `FluidSkeleton`, `FluidSkeletonText`, `FluidSkeletonRow`, `FluidSkeletonTile`, `FluidSkeletonList` · `FluidSkeletonLeading` | gli scheletri di caricamento: **respirano**, non scorrono, tutti insieme, con l'altezza della riga vera; la lista dice una frase sola a TalkBack (`label`) |
| `FluidSelectionMark(selected, size, onPhoto, tint, contentDescription)` | il cerchio di selezione: un nodo solo, molla `fluid`, spunta disegnata; `contentDescription = null` lo rende muto (quando è la riga a dire lo stato) |
| `FluidPagerDots(state: PagerState, …)` / `FluidPagerDots(pageCount, position: () -> Float, …)` | i punti che seguono il dito (a metà scorrimento due punti sono mezzo correnti); oltre sette la fila scorre e i bordi si rimpiccioliscono |
| `FluidStepProgress(step, steps, modifier, label, detail, segmented, pulseCurrent, color)` | «Passo 2 di 5» e una barra che dice a che punto si è (il passo 2 di 5 è il 40%); continua o a segmenti fino a dodici; il passo in corso può respirare |

Il respiro è un token (`FluidMotion.PulseMillis` 900, `PulseLowAlpha` 0,35 per un pallino, `PulseFillLowAlpha` 0,55 per un
riempimento, `pulse()` per la molla): tutti i pezzi che respirano battono insieme.

### I buchi che le app dichiaravano

| | |
|---|---|
| `FluidGlassSize` · `FluidGlassIconButton(size, haptic)`, `FluidGlassButton(size, haptic)` | `Compact` 40, `Regular` 44 (48 di altezza con un testo), `Large` 54 dp; la ricetta del tasto vale **in proporzione** e il **bersaglio del tocco non scende mai sotto i 48 dp** (`touchTarget`). `haptic = null` per un tasto la cui azione ha già il suo colpo. A `Regular` l'albero è quello di prima |
| `FluidInlineMessage(…, onClick, action, leading)` | il messaggio che si tocca: tutto è un bersaglio, ruolo pulsante, freccia a destra (o la chiusura, con `onDismiss`), `action` dice cosa fa il tocco, `leading` è una piastrella |
| `FluidTextField(…, secret)` | i puntini e un occhio che mostra e nasconde; parte nascosto a ogni apertura, niente tasto che svuota, tastiera delle password; lo slot a destra è da 24 dp (era 18) |
| `FluidProgressBar`, `FluidIndeterminateBar` | il riempimento fa almeno 3:1 sulla pista, su pagina, card e pannello di vetro (`legibleFill`): del minimo che serve, tenendo la tinta |
| `FluidToneTile(tone, …) { }` o `FluidToneTile(color, modifier, size, veil) { }` · `FluidListRow(leadingTile = false)` | la piastrella dell'icona, pubblica: un tono dell'engine o un colore qualunque; il colore sta sulla piastrella e mai sulla riga |
| `FluidEmptyState(…, illustration)` | un disegno sopra il titolo; con l'illustrazione la colonna si centra |
| `fluidGlassGroups(items, key, maxRows, row)` · `FluidGlassGroup(items)` | una lista lunga in pannelli di vetro da otto righe dal taglio invisibile (`FluidGlassGroupMaxRows`); il prezzo è `animateItem()`. `FluidGlassGroup` è un pannello solo per una sezione breve |
| `fluidHapticsFor(context, enabled)` | l'aptica dell'engine per il codice a `View` (tastiera, servizio): ripiego sugli effetti di fabbrica del vibratore; da creare una volta |

## Tutto in vetro (3.0, fase 9)

Quello che era opaco o dipinto accanto al vetro diventa vetro, e quello che si afferra e scorre diventa la goccia
di Kyant (piena a riposo, lente sotto il dito). Un modificatore interno solo la disegna, `glassDrop` (`glass/interaction/GlassDrop.kt`):
i pomelli di slider e interruttore, la pillola della barra, il segmentato, il rail e la lente dell'indice la
condividono, ognuno con la sua faccia (`GlassDropFaces`: bianca sul chiaro, il grigio di iOS sullo scuro per una goccia
in una lastra). **Nessuna firma pubblica cambia.** La demo è `AllGlassDemo` (`sample/…/catalog/`, nella galleria da
Chrome › Catalogo della 3.0 › «Tutto in vetro»), con le voci di «Da provare».

| Componente | Cos'è diventato |
|---|---|
| `FluidChip` | un tasto di vetro dove c'è un canvas (lo scelto ha l'accento dentro il materiale, non un riempimento che lo sostituisce), pressione e aptica dei tasti; la pastiglia opaca senza fondale o sotto API 31 |
| `FluidSegmentedControl` | pista di vetro (lastra `Content`) e pillola a goccia, bianca sul chiaro e grigia sullo scuro, con un `Tick` per segmento attraversato; da destra a sinistra la goccia e il dito vanno nel verso giusto |
| `FluidTabRail` | l'indicatore è una goccia che viaggia come la pillola della barra al tocco di una scheda (press, corsa, rilascio), centrata sulla scheda |
| `FluidSectionIndex` | la lente è una goccia (pressione = apertura del nastro, scala nel `layerBlock`), la bolla ha il materiale dei pannelli (`panelOptics(compact, overSharpPage)`); indice e bolla entrano per offset e alfa, mai per scala; la pressione lunga che non si muove non suona un `Tick` |
| `FluidFloatingTabBar` (banda e cerchi) | la banda accessoria è il vetro della barra (ombra di vetro, niente riempimento); la scheda in linea prende la luce del dito; niente increspatura di Material (`indication = null`); un tocco su una scheda, sul cerchio e sulla scheda ripiegata dice `Tap`. Puck e pillola non si toccano (R1) |
| `FluidCoachMark` | ingresso e uscita per offset di layout e alfa, mai scala sul vetro; «Ok» è un tasto di vetro; la punta dipinta è tolta |
| `FluidContainerScaffold` | su un fondale ambientale il gruppo è una lastra di contenuto come `FluidListGroup(glass = true)`; senza, il contenitore pieno di sempre |
| `FluidSyncIndicator` (nella barra) | capsula di vetro col tono nel materiale e la pressione di `FluidSyncAction`; fuori da una barra è il cerchio colorato |
| modale a pagina intera | la chiusura è un tasto di vetro (un cerchio dipinto sotto API 31) |
| `FluidSheet` (deprecato) | con un host dei modali delega al foglio di vetro e dice al chiamante «chiuso» (`onDismissRequest`) a uscita finita; senza host resta il foglio di piattaforma |

Quello che nessun test dice è com'è il vetro: gli host dei test non hanno fondale. `docs/10-coerenza.md` tiene le
celle di questa fase a **?** finché qualcuno non le guarda in mano.

## I menu liquidi (3.0)

Fino alla 2.13 i menu erano quattro prodotti: il tasto che si trasforma aveva un viaggio suo, il menu
contestuale scalava il proprio vetro, le azioni della barra sfumavano il tasto con una molla sua e
`FluidGlassMenuButton` mostrava un pannello sopra il tasto. Ognuno suonava un numero diverso di
vibrazioni e nessuno si sceglieva col dito che l'aveva aperto. Dalla 3.0 sono **un menu solo**, con
quattro ingressi: lo stesso pannello, lo stesso viaggio, lo stesso materiale.

**Come si usa.** Quasi sempre non c'è niente da scrivere di nuovo: un'app che aggiorna ha già le sue
voci (`FluidContextAction`), e le ritrova nel pannello nuovo.

| Ingresso | Come | Si apre |
|---|---|---|
| il tasto che si trasforma | `FluidMorphMenuButton(state, actions, onClick, …, opensOnTap)` + `FluidMorphMenuHost(state, backdrop)` alla radice, con `rememberFluidMorphMenuState()` | tenendo premuto; con `opensOnTap` al tocco, e `onClick` non viene chiamato |
| l'azione della barra | `FluidBarAction(icon, contentDescription, onClick, …, actions, opensOn)` | tenendo premuto; con `opensOn = FluidMenuOpensOn.Tap` al tocco |
| la riga | `FluidListRow(contextActions = { … })` | tenendo premuto: la riga si solleva, il pannello cresce accanto a lei |
| una card qualunque | `Modifier.fluidContextMenu(actions = { … })` | tenendo premuto |

`FluidMenuOpensOn.Tap` è per il tasto il cui **unico** mestiere è il menu — i tre puntini, «Ordina»,
«Altro» — ed è quello che cinque app si riscrivevano a mano; un tasto che ha un'azione sua la tiene al
tocco, e il menu si scopre tenendo premuto. Il tenuto premuto apre in tutti e due i casi, perché è lui che
porta il dito sul menu. **L'host del tasto che si trasforma deve essere composto**: l'apertura è sincrona e
legge le misure che l'host scrive a ogni layout, e senza host il tenuto premuto non apre niente e non
vibra (prima l'host si misurava da sé a ogni apertura, e il tasto si nascondeva per sempre). Gli altri tre
ingressi usano l'host dei modali (`FluidGlassModalHost`), che un'app ha già.

Una voce è `FluidContextAction(label, icon, destructive, enabled, startsSection, onClick)`.
`destructive` la fa rossa e vale al massimo una per menu; `enabled = false` non fa tick e non si sceglie;
`startsSection` apre una sezione — sopra di lei, al posto della riga sottile, una banda, come nei gruppi
di un menu di iOS («Copia, Condividi» | «Elimina») — e sulla prima voce non conta. Sta prima di `onClick`,
così la lambda in coda resta l'azione.

**Il menu si sceglie col dito che l'ha aperto.** Tieni premuto, il menu si apre e il dito ci passa
sopra: una goccia di vetro lo segue di voce in voce, un tick per voce, e al rilascio su una voce la
sceglie. Se il dito si solleva senza essersi mosso oltre lo slop, fuori dalle voci, su un separatore o
su una voce spenta, il menu **resta aperto** e si sceglie con un tocco; un tocco fuori, il back o lo scrim
lo chiudono in silenzio. È il gesto di iOS, e senza non c'è modo di sbagliare in nessuno dei due versi:
chi tiene premuto per aprire e poi solleva il dito non ha scelto niente. Sotto, un relè in testa
all'ancora (`Modifier.fluidMenuPointerRelay`, interno) vede il dito prima di qualunque gesto del tasto, lo
passa al menu quando il click lungo apre e consuma i suoi cambi già nel passaggio Initial: il tasto
nascosto, la lista attorno e la grana restano fermi mentre il dito scorre sul menu.

**Un momento, un colpo.** `Open` all'apertura (uno solo, e solo se il menu si apre: lo suona il gesto
che apre, mai il layer), un `Tick` per voce attraversata (la spenta non ne fa), un `Tap` alla scelta,
niente al congedo. Sui tasti alla LibrePods resta il `PressDown` al contatto, perché è del tasto e non
del menu. Prima una riga con menu contestuale suonava `GestureStart` dal click lungo, `Open` dal menu e il
`LongPress` di piattaforma che `combinedClickable` somma da sé: tre colpi per un momento. Ora
`fluidPressable`, `fluidRowPressable` e `FluidGlassIconButton` hanno `longClickHaptic` (`GestureStart` di
serie, `null` per chi ha un colpo suo) e `combinedClickable` non somma più il `LongPress`.

**Un pannello, un viaggio, un materiale** (interni: `FluidGlassMenuPanel`, `FluidMenuJourney`,
`FluidMenuLayer`). Il *pannello* ha le righe ad altezza fissa che segue il font (48 dp a taglia di serie),
i separatori disegnati — una scatola da mezzo dp aggiungerebbe altezza che le celle non contano, e il dito
sceglierebbe la riga sbagliata —, le sezioni, e **una** goccia che campiona il materiale del pannello
(vetro su vetro) con tutte le trasformazioni — posto, scala del viaggio, schiacciamento — nel proprio
`layerBlock`, mai dentro il layer delle voci. Il *viaggio* parte dal tasto **premuto**: la sagoma che il
dito sta gonfiando, e non il tasto a riposo; il rimbalzo è un gonfiore uniforme attorno al centro del
pannello; un congedo a metà apertura torna indietro dal punto in cui si trova, con la sua velocità; con
le animazioni ridotte arriva senza viaggiare. Il menu che si trasforma e le azioni della barra coprono il
loro tasto e si aprono sotto il dito; il contestuale cresce accanto alla riga, mai sopra, da un seme (se
stesso a 0,42 attorno al perno più vicino a ciò che si è toccato) e per geometria, non con un layer
scalato. Il *materiale* è `panelOptics(compact)` (vedi sopra): la pelle e la lente passano dal tasto al
pannello (`opticsFrom`), la faccia del tasto se ne va nel primo terzo, le voci arrivano dal quarto.

**L'apertura è sincrona.** Azioni, ancora, sequenza e viaggio nello stesso istante del gesto, e il tasto
si nasconde nel disegno (`hidden`) nello stesso fotogramma in cui la superficie compare sulla sua sagoma:
è un cambio di proprietario, non un'uscita, e per questo non c'è un alpha di mezzo (un vetro che sfuma
sopra un altro vetro non è né l'uno né l'altro). Prima passava da un `LaunchedEffect`: per un fotogramma
non c'era né il tasto né il menu, o c'erano tutti e due.

**`FluidAlert` nell'host.** Con un `FluidGlassModalHost` in scope l'avviso non apre più una finestra di
piattaforma: è una voce dell'host, e il vetro è vero — la pagina rifratta dietro, `panelOptics` —,
l'arrivo si posa come su iOS, i pulsanti hanno la goccia e la stessa aptica dei menu (un `Tick` per
pulsante attraversato, un `Tap` alla scelta) e **l'uscita si vede anche dopo che il chiamante l'ha tolto
dalla composizione**: la voce si ritira (`FluidGlassModalHostState.retire`) e la toglie il layer a uscita
finita (`finishExit`). Con `unregister` un avviso tolto spariva di colpo, testo e vetro insieme, nel
fotogramma stesso. `inHost = false` per restare nel `Dialog` di sempre, per un avviso che deve stare sopra
le finestre di piattaforma (un foglio, una tastiera); senza host c'è comunque il `Dialog`.
`FluidPromptAlert` è un `FluidAlert` e ci sta dentro con il suo campo, e la tastiera sale con lui.

**Cosa cambia a vista**, senza toccare niente: i menu contestuali, le azioni di barra con `actions` e
`FluidGlassMenuButton` hanno il pannello, la goccia e il viaggio nuovi, e il contestuale non scala più il
vetro; ogni `FluidAlert` è nell'host se l'app ne ha uno; ogni pannello di vetro dell'host passa dalla
finestra di Fluid-physics, e il pannello classico resta solo come ripiego opaco senza fondale. **Firme**:
`startsSection` sta prima di `onClick` in `FluidContextAction` e `inHost` prima di `content` in
`FluidAlert`, con un default: una chiamata che passa la lambda in coda compila com'era, una che passa
`onClick` o `content` per posizione no, e la JVM cambia comunque, quindi un modulo già compilato va
ricompilato.

**Provato, e non.** `FluidMenuSelectionTest` (23: il dito che decide, lo slop, la voce spenta),
`FluidMenuRelayTest` (il consumo già nell'Initial, il sollevamento, l'annullo), `FluidMenuPanelTest`,
`FluidMenuJourneyTest` e `FluidMenuMotionTest` (le celle, il seme, il tasto premuto, il viaggio e
l'inversione, i token) sono puri; `FluidMenuHandOffTest` li mette insieme sotto un dito finto in un albero
Compose vero, con l'aptica registrata. Non sono provati il vetro e la goccia sul vetro — gli host dei test
non hanno fondale — né `opensOn`/`opensOnTap`, il trascinamento fra i pulsanti di un avviso, la tastiera
sopra un `FluidPromptAlert` nell'host, e nessun fotogramma: sono nelle voci della Beta 2 di «Da provare»
nella galleria, e la demo è `MenusDemo` (Chrome › Catalogo della 3.0 › «Menù liquidi»).

## Fluid-physics — il vetro che cambia forma

Dalla 1.9.0 l'engine sa trasformare qualsiasi silhouette di vetro in qualsiasi altra — rettangolo
in cerchio, cerchio in stella, una sagoma disegnata col dito, due tasti che si fondono in un
pannello — con la rifrazione che segue la forma mentre viaggia. Vive in
`dev.antigravity.fluidengine.ui.fluidphysics`, e la scheda Playground dell'app Fluid Glass è il suo
banco di prova.

**Il vocabolario.** `FluidForm` descrive la silhouette: `Slab` (famiglia dei rettangoli
arrotondati: quadrato, cerchio, capsula, angoli continui), `Poly` (sagome libere: preset o
`FluidFormPresets.fromFreehand` per i tratti a mano), `Group` (fino a sei Slab resi come una
superficie sola, che si versano l'uno nell'altro con un ponte liquido). `FluidPhysicsState`
possiede il viaggio: `morphTo(target, spec)` sospende finché la molla non si posa, il ritargeting
a metà volo è legittimo, e **non esistono coppie vietate** — il viaggio che il renderer non sa fare
in un colpo (gruppo↔sagoma libera) passa da solo per uno scalo. `Modifier.fluidPhysicsSurface`
veste il viaggio di vetro con la stessa grammatica di `glassSurface` (ruoli, ottiche, tinte,
qualità); `Modifier.fluidPhysicsContent` dà al contenuto il contratto dei modali — dissolvenza e
zoom uniforme, mai stirato dalla sagoma.

**Il contratto di performance.** Un fotogramma di morph è nuovi uniform su uno shader già
compilato: il clip del layer resta un rettangolo fermo per tutta la vita della superficie (la
silhouette la scolpisce l'alpha dello shader), il padding della cattura non si muove mai, e a
riposo la superficie tiene un'istanza di `Shape` stabile — è la risposta alla regola "niente shape
morphing", che per le schermate resta valida: il morphing è transiente per contratto, e vive solo
qui.

**I tre livelli.** `FluidPhysicsTier`: `Full` (SDK 33+, rifrazione dal campo di distanza e ponti
smin), `Balanced` (SDK 31–32 o shader rifiutato dal driver: sfocatura, vividezza, tinta e bordo),
`Lite` (sotto SDK 31: tinta piena sul path). La geometria del viaggio è identica su tutti e tre;
una richiesta esplicita può solo scendere di livello.

**La deroga sugli shader.** "Nessuno shader di vetro nuovo accanto a `GlassMaterial`" resta la
regola; Fluid-physics è la deroga deliberata — una seconda famiglia (rettangoli fusi con lo smooth
minimum, poligoni ad anello di vertici) nel suo package, con il suo concern ottico, derivata dal
preludio SDF di Kyant e annotata in `LICENSES/AndroidLiquidGlass.md`.

## L'interfaccia dell'assistente: `engine-ai-ui` (3.0, opt-in)

ClasseViva, PampAI, FluidWeather e Fluid-transit si erano scritte ognuna la propria interfaccia sopra `engine-ai` (che
non ne ha una), e le copie divergevano. `engine-ai-ui` è **l'unione**: quello che un'app aveva e le altre no diventa un
parametro o uno slot, mai il minimo comune. Niente dominio dentro (prompt, strumenti, nomi, pagine, Room): i testi
arrivano da oggetti `Fluid*Strings` con i default italiani, come `FluidAppearanceStrings`. È un modulo a parte: porta
con sé `engine-ui`, `engine-ai` ed `engine-foundation` (`tools/engine-install.ps1 -Modules engine-ai-ui`), e le app che
non lo chiedono non cambiano.

- **overlay** — `FluidAssistantOverlay` (senza ViewModel: callback), aureola in cima e campo di luce sul perimetro
  (`FluidAssistantHalo`, `FluidHaloField`), card, barra di scrittura, pillole, `FluidAssistantDock` (il pannello
  ancorato), `FluidSpeaker` (voce), aptica di stato, resoconto e foglio «Dettagli» di un errore;
- **impostazioni** — `fluidAiSettingsSection` (`LazyListScope`): chiavi con avviso sulla forma, ordine dei servizi con
  frecce e trascinamento, picker dei modelli nel vetro, dettagli di OpenRouter, diagnostica, `FluidAiLock`;
- **chat** — `FluidFollowBottom`, bolle (il testo è `FluidMarkdown` di `engine-ui`, con `FluidAssistantMarkdownOptions`
  che toglie i marcatori `[[chip]]`), `FluidComposer` di vetro.

Il pallino che respira passa da 700 a 900 ms (il respiro dell'engine, `FluidMotion.PulseMillis`). FluidWeather e
Fluid-transit devono passare agli stati di `engine-ai` **prima** di usare l'overlay. Il diff delle quattro copie e cosa
sostituisce ogni app: `artifacts/design/ai-ui-diff.md`. Il modulo ha i suoi 103 test su JVM (il parser e la rivelazione sono saliti in `engine-ui` con i loro 43); il vetro e il moto si
guardano in mano (una riga ? nella matrice di `docs/10-coerenza.md`).

## Cosa non c'è, e perché

Niente `FeatureHero`, niente `GradePill`, niente componente che sappia cos'è una materia o un voto. Erano nell'app da cui l'engine è stato estratto e ci sono rimasti: un componente che conosce il dominio non è un componente di design system, è una schermata scritta a metà.

## Aptica (1.19.0)

`FluidHaptics` e' il vocabolario di cio' che si sente sotto il dito, uno per tema: i componenti
dell'engine lo usano da soli (un tasto che cresce fa `PressDown`, `PressRelease` e `PressClick` dalla
3.0 — solo costanti di piattaforma, come LibrePods —, una superficie che cede `Tap`, uno switch
`ToggleOn`/`ToggleOff`, un menu che si apre `Open`, l'indice di sezione `Tick`), le app aggiungono i loro momenti con
`rememberFluidHaptics().play(FluidHapticEvent.X)`: `Threshold` per una soglia raggiunta,
`GestureStart`/`GestureEnd` per un trascinamento, `Success`/`Warning`/`Error` per gli esiti,
`AlertWatch`/`AlertAlarm`/`AlertClear` per un'allerta che cambia livello.

Il permesso `VIBRATE` lo dichiara `engine-ui`: senza, ogni composizione fallisce con una
SecurityException che il motore ingoia, e l'aptica non si sente da nessuna parte (successo davvero,
1.20.2 -> 1.21.0). L'impostazione tattile di sistema non si legge per decidere: su One UI resta a
zero mentre il telefono vibra, e le vibrazioni di tocco le filtra gia' il sistema — dalla 2.11.0
anche fra Android 11 e 12, dove ora partono come uso "tocco" (`AudioAttributes`
`ASSISTANCE_SONIFICATION`) e il "Feedback tattile" spento le spegne.

**Una vibrazione per momento (3.0).** Due eventi di controllo entro 70 ms (`Tap`, `Confirm`, `Reject`,
`ToggleOn`/`ToggleOff`, `Threshold`, `Open`, `Close`, `GestureStart`/`GestureEnd`) sono lo stesso momento
detto da due componenti, e il secondo non suona: era il «toc-toc» che faceva sembrare un errore l'apertura
di un menu. Esiti e allerte non si scartano mai, i tick continui hanno il loro cancello a 40 ms, `Open` è
un colpo solo (un click pieno, non una rampa più un tick). L'eccezione sono i tasti: `PressDown`,
`PressRelease` e `PressClick` sono solo di piattaforma, non fondibili, e non armano la rete. Una riga che
è l'interruttore (`FluidToggleRow`) suona solo `ToggleOn`/`ToggleOff`. Per il codice che non è Compose
(una tastiera, un `View`, un servizio) `fluidHapticsFor(context, enabled)` dà lo stesso motore. Il perché
è in `regole.md`.

**Il registro (3.0).** `FluidHapticTrace` risponde a una domanda che a dito non ha risposta — «una
vibrazione per momento» è vero? — perché un tocco può far parlare tre componenti nello stesso istante
(il tasto, il pannello che apre, il livello del modale), la rete dei 70 ms ne lascia suonare uno, e dal
polpastrello non si distingue «ne è arrivato uno» da «ne sono arrivati tre e due sono stati fusi». È un
**decoratore** (`TracingFluidHaptics`, da `FluidHapticTrace.wrap`): inoltra ogni chiamata al motore vero
tale e quale e ne tiene una copia in una lista limitata e osservabile (`entries`, `rows()`, `recording`,
`clear()`); non cambia cosa suona né quando, e senza `ProvideFluidHapticTrace(trace) { }` nessuno lo
costruisce, quindi non costa niente. Va **dentro** `FluidTheme` — è lui a installare l'aptica, e un registro
messo fuori sarebbe sovrascritto — e un `FluidTheme` annidato lo toglie a quello che contiene: dentro,
`ProvideFluidHapticTrace { }` senza argomenti rimette quello ereditato (`LocalFluidHapticTrace`). L'esito di
ogni voce (`FluidHapticTraceOutcome`: suonata, fusa, scartata) è una **stima**: il registro non può chiedere
al motore se un evento è suonato, quindi rifà da solo le due reti del motore di serie con le stesse
costanti e un orologio suo, e con un motore sotto che non è quello — o spento dall'interruttore dell'app —
dice cosa *avrebbe* fatto il motore di serie. `fluidHapticTraceRows` mette i granelli della grana in una
fila sola («grana LibrePods ×14»). La galleria lo mostra in Controlli › Registro aptico e in piccolo sopra
a tutto; lì la grana si sceglie fra `LibrePods` e `Fluid` e, per `Fluid`, si tara con i cursori (partenza,
spegnimento, passo, forza, applicati al rilascio).

Regole: non chiamare mai `LocalHapticFeedback` direttamente in un'app (il vocabolario e' uno);
`fluidPressable(haptic = null)` per un controllo che vibra gia' per conto suo (una pillola che fa
`Threshold` allo swipe; per un tasto che cresce spegne anche contatto, rilascio e grana); i tick continui (`Tick`, `FrequentTick`, `WaitTick`, `DragTick`)
spariscono in risparmio energetico e si diradano a 40 ms da soli. `DragTick` (2.11.0) e' il dito
che scorre su un controllo premuto o la maniglia di uno slider che passa un passo: il piu' leggero
dei tick, perche' arriva a raffica. L'interruttore e' `EngineSettings.hapticsEnabled`,
da esporre in Aspetto accanto al vetro.

## Suggerimenti al primo uso (1.20.0)

`FluidTutorialHost` mostra **un callout per volta**, agganciato a un elemento, la prima volta che
una persona incontra una funzione. Il componente sa disegnare e decidere il momento; **cosa e' gia'
stato visto lo ricorda l'app**.

Come si monta: alla radice,
`CompositionLocalProvider(LocalFluidTutorialHostState provides state) { ... }`, il contenuto dentro
un `Box(Modifier.fillMaxSize().fluidTutorialTouches(state))` e in fondo
`FluidTutorialHost(state, labels, backdrop) { modalHost.isOnScreen }`. I tocchi si osservano dal
**contenitore** e mai da sopra: un fratello a tutto schermo con un `pointerInput` si prende
l'intero percorso di hit test, e l'app sotto smette di rispondere al dito (successo davvero,
1.20.1 -> 1.20.2). Ogni schermata dichiara la
sua chiave (`state.screenChanged("home")`), offre i candidati non ancora visti (`state.offer(...)`)
e marca gli elementi con `Modifier.fluidTutorialAnchor("id")`.

Quando parla, in `FluidTutorialPolicy`: l'ancora e' sullo schermo, sono passati 600 ms dall'ultima
interazione e dall'ultimo caricamento, non c'e' un pannello dell'engine in scena, e dalla chiusura
del precedente c'e' stata almeno un'interazione. Fra piu' candidati vince la priorita' piu' alta.
Tutto puro e provato con un orologio finto: `FluidTutorialPolicyTest`.

Il callout non e' un modale: nessuno scrim, la pagina resta leggibile e **un tocco fuori chiude
senza consumare il tocco**. Dentro: due parole di titolo, una frase sola, il gesto disegnato
(`FluidGestureHint`: `Tap`, `LongPress`, `SwipeHorizontal`, `DragReorder`, `Scrub`,
`LongPressAndTap`, fermo immagine con le animazioni ridotte), "Ok" e il link che li spegne tutti
(`onDismissed(id, optOut = true)`).

## Misura, pagine intere, cursore (1.32.0)

Tre aggiunte nate dalla stessa app su un tablet, e dallo stesso giudizio: «su tablet è brutta».

**La misura di lettura.** `FluidScreen` ha `contentMaxWidth` (760 dp): oltre quella larghezza il
margine cresce e la colonna resta centrata, titolo grande compreso. È una riga sola, in un posto
solo, e sistema ogni schermata dell'app che passa da `FluidScreen`. Chi non ci passa — un overlay,
un pannello — ha `Modifier.fluidReadingWidth()`, che fa lo stesso con `layout {}` e funziona in
qualunque genitore. La funzione pura è `fluidScreenPadding(available, horizontalPadding,
contentMaxWidth)`, con i suoi test. Una schermata che vuole davvero tutta la larghezza passa
`Dp.Infinity`.

**La pagina intera.** `FluidGlassModalPresentation.FullScreen` è un foglio che prende tutta
l'altezza e porta con sé quello che un foglio lascia al chiamante: barra in cima con titolo e
chiusura, contenuto che scorre, congedo trascinando. Sotto i 600 dp sta a filo dei bordi; sopra si
stacca con un margine e la misura di lettura. È **opaca**, l'unica presentazione che lo è: il vetro
sta sulle cose che galleggiano sopra un contenuto, e una pagina non galleggia, copre. Il criterio
per sceglierla è il compito: un modulo
con più di tre campi è una pagina, una scelta fra due opzioni è un `Popover` sul tasto.

**Il cursore.** `FluidTextField` ha l'overload a `TextFieldValue`; quello a `String` ne è un
guscio e nessuna chiamata esistente cambia. `FluidTextEdit` (`insert`, `wrap`, `toggleLinePrefix`)
sono le operazioni di un editor, pure: un pulsante «grassetto» che scrive in fondo al testo invece
che al cursore è peggio di nessun pulsante, e con queste non c'è più motivo di scriverlo così.

## I pannelli (1.33.0)

Fino a qui l'engine non aveva un layer adattivo: niente split view, niente sidebar, nessuna misura
oltre la quale una pagina smettesse di essere una pagina da telefono allargata. Adesso c'è, e sta
tutto in una funzione pura più tre componenti.

**`fluidPaneLayout(available, sizes, hasSide, hasRail, showDetail)`** decide cosa sta in scena:
sotto i 600 dp un pannello solo (e la barra in basso è affare dell'app); fino a 1000 dp il rail di
fianco a un pannello; oltre, **la barra laterale più uno** — o l'elenco, o quello che si è aperto.

Due pannelli al massimo, e la barra laterale è uno dei due. Tre cose aperte insieme si leggono come
tre pagine appiccicate, e con una pagina di impostazioni diventano tre livelli della stessa gerarchia
visibili in una volta: l'indice, la sezione, e un menù che con quella sezione non c'entra niente.
Con due, c'è anche **un solo tasto indietro**, che è l'altra metà dello stesso difetto.

`FluidPaneLayout.splits` dice che la finestra tiene elenco e dettaglio come due posti distinti: è una
capacità, non ciò che si vede, e serve all'app per sapere dove mandare una rotta. Il pannello fuori
scena resta **composto**, largo zero, così il suo navigation host conserva grafo e stack — smontarlo
significa un controller senza grafo e una rotta che esplode, che è esattamente come si è scoperto.

**`FluidPaneScaffold(layout, list, detail, side, rail)`** emette **solo** i pannelli in scena — un
pannello nascosto non è un pannello largo zero, è un pannello che non c'è, così la sua `FluidScreen`
non registra un fondale che nessuno vede — e per ognuno dichiara `LocalFluidPaneRole` e
`LocalFluidRouteFront`. I controller di navigazione stanno fuori, nella shell dell'app: un
`NavHost` che sparisce perché il suo pannello non c'è conserva lo stack.

**`FluidSidebar`**, **`FluidSidebarRow`**, **`FluidSidebarSection`**: la barra laterale è chrome,
quindi di vetro come il rail e la pillola, mentre le liste della pagina non lo sono più. La
selezione è un velo dell'accento, non un secondo vetro dentro il vetro.

**`fluidGridColumns(available, minItem = 150 dp)`**: la misura della tessera è la costante, il
numero di colonne ne discende. Mai zero, mai più di cinque.

**`FluidVividEffect.Ruled`**: righe di quaderno, tenuissime, sul colore pieno, con il margine a
sinistra. Su tutte le tessere di una griglia va bene: non è una decorazione, è quello che sono.

## Un fondale solo (1.34.0)

Il primo tablet a tre pannelli ha reso evidente una cosa che su una pagina sola non si vedeva: se
ogni pannello dipinge il proprio fondale, la finestra si legge come **tre telefoni appoggiati uno
accanto all'altro**, con due cuciture verticali nel mezzo e tre colori che non c'entrano fra loro.

`FluidAmbientSurface(ambient) { … }` dipinge un fondale solo, continuo da bordo a bordo, e lo offre
al sottoalbero come `LocalFluidCanvasBackdrop`. Ogni `FluidScreen` dentro lo trova, **smette di
dipingere il proprio fondo opaco** e lo combina con la registrazione del proprio corpo: il vetro
della chrome continua a rifrangere un'immagine opaca — fondale più contenuto — che è la condizione
che il materiale ha sempre avuto. Un `ambient` passato a una schermata che sta dentro una superficie
condivisa non viene dipinto: una finestra, un fondale.

Su una pagina sola non cambia niente: senza superficie condivisa, ogni schermata si dipinge il suo
come prima.

## Elenco+dettaglio e pagine a colonne (2.5.0)

Nate da ClasseViva Expressive su un tablet in orizzontale, con una decisione di Alessio che vale
per tutte le app: **la navigazione resta una sola** (la pillola in basso), e lo spazio si guadagna
*dentro* le pagine. La sidebar di `fluidPaneLayout` resta per chi la vuole, ma non e' questa strada.

**`FluidListDetailScaffold(ambient, list = { twoPane -> }, detail = { })`**: su uno schermo largo
l'elenco a sinistra e la cosa scelta accanto, su uno stretto l'elenco e basta. Le misure stanno in
`fluidListDetailLayout(available, sizes)`, pura e provata: due pannelli solo se *entrambi* restano
quello che sono (elenco >= 360 dp, dettaglio >= 480 dp), quindi un tablet in verticale resta una
pagina sola. Tre decisioni dentro:

- **un fondale solo**: i due pannelli stanno in una `FluidAmbientSurface`, il motivo compare una
  volta per la finestra e non cambia scegliendo un'altra cosa;
- **la chrome guarda l'elenco**: il dettaglio non si registra presso il controller, cosi' la pillola
  in basso a sinistra rifrange la pagina che ha davvero sotto;
- **il dettaglio non esiste finche' non serve**: su un pannello solo non e' composto, e `list`
  riceve `twoPane` per decidere se una riga *seleziona* o *apre*.

`FluidDetailContent(item, key, order)` e' il cambio di cosa mostrata: un movimento fra pari, e la
pagina che arriva compare quando quella che esce se n'e' quasi andata — dentro un fondale condiviso
nessuna pagina e' opaca, e due pagine trasparenti non devono mai leggersi insieme.
`FluidDetailPlaceholder` e' il pannello quando non c'e' niente di scelto. `FluidListRow` ha
`selected` (un velo dell'accento) e `disclosure` (la freccia, che in un elenco+dettaglio promette
una pagina che non arriva).

**Pagine a colonne.** `FluidScreen(metrics = rememberFluidScreenMetrics())` scrive quanto e' larga
la sua colonna, e il contenuto — che e' un `LazyListScope` e non puo' chiederlo — lo legge come uno
stato. Con quello:

- `fluidColumns(key, columns, sections)`: sezioni brevi a muratura (`FluidMasonry`); con una colonna
  sono item uno per uno, esattamente come prima;
- `fluidGridItems(items, columns, key)`: card dello stesso genere in righe, pigre riga per riga;
- `FluidColumnsDefaults.WideContentMaxWidth` (1120 dp): la misura di una pagina a colonne, contro i
  760 della colonna di lettura.

Quando una pagina si allarga oltre la misura di lettura, `FluidScreen` spegne il motivo del proprio
fondale (`FluidAmbient.showMotif`): l'angolo in alto a destra e' occupato dalla fascia in cima, e
due figure dello stesso disegno accostate si leggono come un fondale doppio.

**Fogli.** La presentazione `Sheet` si ferma a 620 dp e resta in mezzo: a tutta larghezza su un
tablet era una striscia bassa e larga quanto la finestra.

## Il testo che si accende (1.35.0)

`FluidSpokenText(text, words, positionMs)`: quello che e' gia' stato detto e' nel colore pieno,
quello che deve ancora venire e' velato, e il confine si muove parola per parola. Per un'app che
tiene insieme una registrazione e la sua trascrizione e' l'unica cosa che dice «sei qui» senza che
nessuno debba cercarlo.

Due dettagli che sono tutto il componente. **La posizione arriva come lambda**, non come parametro:
a cinque battiti al secondo un parametro rimisurerebbe il testo cinque volte al secondo, mentre
letta dentro il disegno invalida solo il disegno. E il testo si disegna **due volte**: il `Text`
vero e proprio nel colore velato — quindi accessibilita', selezione e layout restano quelli di
sempre — e poi la parte gia' detta, ritagliata sulle righe intere piu' il rettangolo parziale della
riga corrente, ridipinta nel colore pieno.

`fluidSpokenChars(words, positionMs)` e' la funzione pura che dice fin dove: ricerca binaria sulle
parole, e dentro una parola il confine avanza in proporzione, cosi' l'evidenziazione scorre invece
di scattare. Ha i suoi test.

## Come si varia, senza perdere la parentela

Tutte le regole di questo documento sono regole di uniformità, e un'app che le segue tutte somiglia
alle altre che le seguono. Va bene fino a quando qualcuno dice «le mie app sono tutte uguali». Allora
la domanda è quali manopole esistono, e la risposta è: più di quante se ne usino.

| Manopola | Dove | Cosa cambia |
|---|---|---|
| L'accento | `FluidTheme(brand = AccentPreset)` con `AccentMode.BRAND` | Tutta l'app, coerentemente: tasti, pillola, selezioni, tinte del vetro. È la manopola che nessuna app gira, e quella che si vede di più. Un accento **che cambia con il contesto** — la materia, il progetto, la persona — è quello che rende un'app riconoscibile a colpo d'occhio. |
| Tono × motivo | `FluidAmbient(tone, motif, intensity)` su ogni `FluidScreen` | Il fondale della pagina: 49 combinazioni, più l'intensità continua. Una schermata senza `ambient` è una pagina bianca; con l'ambient è *quella* pagina. |
| La card viva | `FluidVividCard`, `FluidHeroBand` | Colore pieno con la luce sopra (`Sheen`) o un motivo. Per la cosa che la pagina presenta, non per l'elenco. |
| Il vetro | `glass = true`, `FluidGlassIconButton`, i modali | Solo sugli elementi che galleggiano: comandi, barre, pannelli. Il contenuto resta opaco, altrimenti il vetro non ha niente da rifrangere. |
| La forma | i preset di `FluidRadius` | Angoli continui sempre, ma la scala cambia la voce: `Control` è secco, `Group` è morbido, `Sheet` è un oggetto. |

Quello che **non** si tocca: Inter, le molle di `FluidMotion`, le liste raggruppate, gli angoli
continui. È la grammatica che rende le app parenti. Variare il lessico e tenere la grammatica: le
app si riconoscono come una famiglia, e ognuna come sé stessa.
