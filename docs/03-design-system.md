# 03 · Design system

`engine-ui` è il livello visivo dell'app: non un tema Material con i colori cambiati, ma una superficie disegnata da capo sopra Material 3. Chi ci lavora deve sapere quali sono le regole, perché sono poche e sono tutte deliberate.

## Le cinque decisioni

**1. Gli angoli sono continui, non circolari.** Un angolo circolare unisce un lato dritto a un arco, e la curvatura salta da zero a `1/r` in un punto: l'occhio legge quel salto come una piega. `ContinuousCornerShape` fa salire e scendere la curvatura, senza giunzione. È metà del motivo per cui una card qui non sembra una card Material. Non usare `RoundedCornerShape` nel codice nuovo.

**2. Il carattere è Inter, con due tagli.** Testo sotto i 20sp, display sopra. Il tracking segue una curva dipendente dalla dimensione (negativo nei titoli, positivo nelle didascalie) perché Inter a tracking zero sembra largo nei titoli e stretto in piccolo. `fluidTypography()` monta tutto sugli slot di Material: si continua a chiedere `MaterialTheme.typography.titleMedium` e si ottiene la scala iOS 34/28/22/20/17/15/13/11.

**3. La palette parte da un colore solo.** Un accento genera l'intera scala di superfici, con un velo di tinta che diventa più forte solo sui contenitori piccoli e alti. Le superfici grandi restano quasi neutre. Cambiare `brand` cambia tutta l'app, coerentemente. I ruoli sono sempre coppie: un colore di sfondo non viaggia mai separato dal colore di contenuto che ci si legge sopra.

**4. Le liste sono raggruppate.** Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia il testo. La categoria della riga sta sulla piastrella dell'icona, mai sullo sfondo della riga: colorare le righe trasforma una lista ordinata in un patchwork.

**5. Il movimento è un vocabolario, non un effetto.** Tutto passa da `FluidMotion` (molle) e `FluidMotionScheme` (che le passa anche ai componenti Material). Una pagina in arrivo non sfuma mai: le transizioni di rotta sono opache e laterali, così non ci sono mai due pagine leggibili sovrapposte. `FluidMotionPolicy` spegne il movimento decorativo quando la scala di animazione di sistema è a zero — va rispettata, non aggirata.

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

Il raggio di riferimento è **2 dp**, non 16. Il numero è andato 8 → 16 → 2, e il giro di mezzo è
l'errore che vale la pena non rifare: sopra gli ~8 dp un pannello smette di trasmettere un'immagine e
trasmette la sua *media*, e un pannello che tiene una media è un riempimento — tutto il lavoro che la
lente fa sul bordo sta allora piegando un colore piatto in un altro colore piatto. Quello che
identifica il materiale è la **dislocazione** al perimetro, non la brina, quindi la lente sale a
19/29 dp (i numeri della capsula di Kyant) e il raggio scende. Le poche superfici che devono davvero
nascondere qualcosa — la barra superiore, con testo nitido che le scorre sotto — chiedono un multiplo
con `blurScale` invece di farlo pagare a tutte.

E il film del vetro **non è `MaterialTheme.surface`**. Una barra dello stesso colore del fondo non è
un materiale traslucido, è niente: su un tema AMOLED la pillola di navigazione spariva. Il Liquid
Glass è un materiale chiaro e riflettente, che anche in tema scuro si legge come un pannello *più
chiaro* del fondo. `GlassDefaults.glassFilm()` parte da due grigi fissi e prende un terzo della
palette: abbastanza da seguire l'accento, non abbastanza da farsi trascinare a fondo.

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
3. **sfocatura** — piccola (2 dp di riferimento): è il film, non il raggio, a tenere leggibile
   quello che sta sopra, e un raggio grande cancella l'immagine invece di trasmetterla;
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
| `FluidTabBar`, `FluidTabRail` | navigazione principale a pillola |
| `FluidSheet`, `FluidAlert`, `FluidGrabber` | fogli modali e avvisi |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe |
| `FluidNotificationHost` | notifiche contestuali dentro l'app |
| `ProvideFluidChrome`, `rememberFluidChromeController` | il registro della barra e del bus "torna in cima" |

**Contenuto**

| | |
|---|---|
| `FluidListGroup`, `FluidListRow`, `FluidListDivider`, `FluidListItem` | la lista raggruppata |
| `FluidCard`, `FluidHeroCard`, `FluidEditorialCard` | superfici |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction` | stato della sincronizzazione |

**Controlli**

`FluidButton` (Filled/Tinted/Plain/Destructive), `FluidSwitch`, `FluidChip`, `FluidSegmentedControl`, `FluidTextField`, `FluidColorDot`, `FluidBarAction`, `FluidGlassIconButton`, `FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`.

**Token**

`FluidRadius` (Small 10, Control 12, Card 18, Group 22, Sheet 38), `FluidCapsuleShape`, `FluidTextStyles` (`uppercaseCaption`, `numeric`, `largeNumeric` con cifre tabulari), `FluidTone` (Primary/Success/Warning/Danger/Info/Neutral), `GlassDefaults`, `GlassOptics` e `GlassRole` per il materiale traslucido.

## Regole per chi scrive codice nuovo

- Colori solo da `MaterialTheme.colorScheme`. Un `Color(0xFF…)` in una schermata è un colore che non seguirà l'accento, non avrà una variante scura e non passerà i controlli di contrasto. Le uniche eccezioni sono i toni fissi warning/success, che nello schema Material non hanno un ruolo.
- Testo solo da `MaterialTheme.typography` o `FluidTextStyles`. Niente `fontSize` a mano.
- Angoli da `FluidRadius` attraverso `ContinuousCornerShape`, o da `MaterialTheme.shapes` (che è già mappato su quelli).
- Animazioni da `FluidMotion`. Una `tween` scritta a mano nel mezzo di una schermata è il modo più rapido per far sembrare quella schermata di un'altra app.
- Feedback al tocco con `fluidPressable` (scala) o `fluidRowPressable` (tinta, per le righe di una lista raggruppata). Una riga che si rimpicciolisce rompe la sagoma del gruppo.
- Il contrasto è testato: `FluidContrastTest` verifica le coppie. Se aggiungi un ruolo, aggiungi il caso.

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

## Cosa non c'è, e perché

Niente `FeatureHero`, niente `GradePill`, niente componente che sappia cos'è una materia o un voto. Erano nell'app da cui l'engine è stato estratto e ci sono rimasti: un componente che conosce il dominio non è un componente di design system, è una schermata scritta a metà.

## Aptica (1.19.0)

`FluidHaptics` e' il vocabolario di cio' che si sente sotto il dito, uno per tema: i componenti
dell'engine lo usano da soli (un tasto di vetro fa `Tap`, uno switch `ToggleOn`/`ToggleOff`, un
menu che si apre `Open`, l'indice di sezione `Tick`), le app aggiungono i loro momenti con
`rememberFluidHaptics().play(FluidHapticEvent.X)`: `Threshold` per una soglia raggiunta,
`GestureStart`/`GestureEnd` per un trascinamento, `Success`/`Warning`/`Error` per gli esiti,
`AlertWatch`/`AlertAlarm`/`AlertClear` per un'allerta che cambia livello.

Regole: non chiamare mai `LocalHapticFeedback` direttamente in un'app (il vocabolario e' uno);
`fluidPressable(haptic = null)` per un controllo che vibra gia' per conto suo (una pillola che fa
`Threshold` allo swipe); i tick continui (`Tick`, `FrequentTick`, `WaitTick`) spariscono in
risparmio energetico e si diradano a 40 ms da soli. L'interruttore e' `EngineSettings.hapticsEnabled`,
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
