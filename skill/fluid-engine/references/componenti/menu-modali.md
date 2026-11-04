# Menu, pop-up, fogli, avvisi, notifiche

Tutto quello che l'app mette **sopra** la pagina per un momento: un menu di voci, un pannello che nasce da un tasto, un
foglio che sale dal basso, una pagina intera per un compito, un avviso con due pulsanti, una conferma, un messaggio
che compare in alto. La famiglia sta in `engine-ui` (`ui/fluid/`) e ha un'idea sola: **il vetro sta dentro la
composizione dell'app**. Una finestra di piattaforma (`Dialog`, `ModalBottomSheet`) non può leggere il `GraphicsLayer`
della pagina, quindi non può rifrangerla: dipinge un gradiente. Per questo i pannelli non sono finestre: si
*dichiarano* dove vivono i dati (un portale) e si *disegnano* alla radice (un host), sopra la tab bar.

Modello mentale, in due frasi: **un portale dichiara, un host disegna** (per i pannelli, i fogli, gli avvisi e i
menu), e **un tasto che apre qualcosa diventa quella cosa** (la sagoma viaggia dal tasto al pannello e torna: non
c'è mai una seconda finestra che compare accanto). La famiglia è legata a Fluid-physics (la sagoma che viaggia: vedi
[fluid-physics.md](fluid-physics.md), che qui non si spiega), al vetro (`panelOptics`, `panelTint`) e all'aptica
(`FluidHapticEvent`: «una vibrazione per momento», in [regole.md](../regole.md) e [aptica.md](aptica.md)).

## I file della famiglia

| file | contiene |
|---|---|
| questo (`menu-modali.md`) | l'host e il portale, le presentazioni (pop-up, foglio, pagina intera), `FluidSheetScaffold`, `FluidConfirmSheet`, `FluidAlert`, `FluidPromptAlert`, il foglio deprecato |
| [menu-liquidi.md](menu-liquidi.md) | i menu: il tasto che si trasforma, le azioni della barra, il contestuale di una riga, la selezione col dito, il viaggio, la goccia |
| [notifiche.md](notifiche.md) | `FluidNotificationHost`, la card che si tocca, si apre e si lancia via |

Catalogo delle firme: [api/menu-modali.md](../api/menu-modali.md) (e [api/fluid-physics.md](../api/fluid-physics.md) per
`FluidMorphMenuButton`).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un menu di voci da un tasto il cui **unico** mestiere è il menu («Ordina», «Altro») | `FluidMorphMenuButton(opensOnTap = true)` o `FluidBarAction(actions, opensOn = FluidMenuOpensOn.Tap)` → [menu-liquidi.md](menu-liquidi.md) |
| un tasto con un'azione sua *e* un menu nascosto | gli stessi, di serie: il tocco è l'azione, il **tenuto premuto** apre il menu |
| un menu su una riga di lista | `FluidListRow(contextActions = { … })` |
| un menu su una card o una vista qualunque, che non ha un click suo | `Modifier.fluidContextMenu(actions = { … })` |
| un menu su un elemento che gestisce già i suoi click | `rememberFluidContextMenu` + `Modifier.fluidContextMenuAnchor` + `open()` dal suo click lungo |
| un pannello di **contenuto** che nasce da una riga o da un tasto | `FluidGlassModalPortal(origin = …)` + `Modifier.fluidExpandOrigin` |
| un pop-up di contenuto senza origine | `FluidGlassModalPortal` con `origin` nullo (si apre al centro) |
| un foglio dal basso con testata, corpo che scorre e tasti fermi | `FluidGlassModalPortal(presentation = Sheet)` con dentro `FluidSheetScaffold` |
| una conferma («Eliminare?», «Usare i dati mobili?») | `FluidConfirmSheet` |
| un avviso breve con due o tre pulsanti (UIKit) | `FluidAlert` con `FluidAlertAction` |
| chiedere una riga di testo (rinomina, valore) | `FluidPromptAlert` |
| una pagina intera per un compito con più di tre campi | `FluidGlassModalPortal(presentation = FullScreen)` |
| un messaggio transitorio, in alto, che scade | `FluidNotificationHost` → [notifiche.md](notifiche.md) |
| un messaggio che resta nella pagina | **non è di questa famiglia**: `FluidInlineMessage` |
| un avviso che deve stare sopra *altre finestre di piattaforma* (una tastiera, un foglio di sistema) | `FluidAlert(inHost = false)` (il `Dialog`) |
| **non così**: `FluidSheet` | deprecato: con un host delega al foglio di vetro, senza è un `ModalBottomSheet` che dipinge |
| **non così**: `FluidGlassMenuButton` | deprecato → `FluidMorphMenuButton` + `FluidMorphMenuHost` |
| **non così**: un `FluidAlert` con un modulo dentro | un avviso è stretto (280 dp) e centrato: per i campi un foglio o una pagina intera |
| **non così**: `Modifier.fluidContextMenu` su un elemento con `clickable` | il click consuma il gesto e il menu non parte mai: serve il controller |

## Regola trasversale: un portale dichiara, un host disegna

### Montare l'host (una volta, alla radice)

L'app crea lo stato e lo fornisce con un `CompositionLocal`; l'host sta **sopra** il contenuto (e sopra la tab bar) e lo
stesso vale per gli altri due host. Sul modello del `GalleryActivity` del sample:

```kotlin
val modalHost = rememberFluidGlassModalHostState()
val morphMenu = rememberFluidMorphMenuState()
val notificationHost = rememberFluidNotificationHostState()

CompositionLocalProvider(
  LocalFluidGlassModalHostState provides modalHost,
  LocalFluidMorphMenuState provides morphMenu,
  LocalFluidNotificationHostState provides notificationHost,
) {
  Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().fluidGlassModalObscured()) { /* la pagina e la tab bar */ }
    FluidGlassModalHost(state = modalHost, backdrop = backdrop)
    FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
    FluidNotificationHost(state = notificationHost, backdrop = backdrop, modifier = Modifier.align(Alignment.TopCenter))
  }
}
```

`backdrop` è quello che l'host rifrange: **il fondale della chrome, che contiene la pagina intera** (nel sample
`chrome.activeBackdrop`). `FluidGlassModalHost(backdrop = null)` è ammesso — senza fondale lo scrim diventa un velo
quasi opaco (nero con alpha 0,92) e i pannelli il loro ripiego pieno — ma è lo stato dei test, non quello di un'app.

L'ordine in `Box` conta: l'ultimo disegnato sta sopra. Il sample mette l'host dei modali, poi quello del menu che si
trasforma, poi le notifiche. **Un host che non c'è non è un errore**: è la causa di quasi tutti i «non si apre».

### Cosa succede senza host

| componente | senza `FluidGlassModalHost` in scope |
|---|---|
| `FluidGlassModalPortal` | ritorna subito: **non disegna niente, non fallisce, non avvisa** |
| `FluidConfirmSheet` | come il portale: non si apre |
| `FluidAlert` / `FluidPromptAlert` | ripiego sul `Dialog` di piattaforma (vetro dipinto) |
| `FluidSheet` (deprecato) | ripiego sul `ModalBottomSheet` |
| `FluidBarAction(actions)`, `Modifier.fluidContextMenu`, `FluidListRow(contextActions)` | il menu non si apre (e `open()` ritorna `false`: nessun colpo `Open`) |
| `FluidMorphMenuButton` senza `FluidMorphMenuHost` composto | il menu non si apre e non vibra; con `opensOnTap` il tocco non fa **niente** (non chiama nemmeno `onClick`) |

`FluidGlassModalHost` non compone niente finché non ha voci registrate: una voce si registra anche da chiusa (in un
`DisposableEffect` del portale), e una voce chiusa e finita di uscire non disegna nulla e costa una animazione di un
float.

### Il fuoco, il back, la semantica

- **Back.** Ogni layer aperto ha un `PredictiveBackHandler` (il pannello e il menu) o un `BackHandler` (l'avviso): vince il
  più recente, perché l'host compone le voci ordinate per `openedAt`. Il back predittivo arretra lo scrim (fino al
  60%) e rimpicciolisce il pannello (10%, 6% per il foglio); a gesto completato chiama `onDismissRequest`; annullato,
  torna com'era. `FluidGlassModalHostState.dismissTop()` fa la stessa cosa da codice.
- **Voci in pila.** «Più recente» = aperta per ultima, non dichiarata per ultima. Un menu aperto da dentro un modale
  sta **sopra** il modale, non lo sostituisce.
- **Fuoco e TalkBack.** Ogni layer è un `isTraversalGroup` + `focusGroup` con un bersaglio da 1 dp che prende il fuoco
  all'apertura: senza, il primo movimento da tastiera o D-pad finiva nella pagina sotto. `paneTitle` (o
  `LocalFluidLabels.current.actions`, «Azioni», per i menu) è l'annuncio del pannello.
- **La pagina sotto** va nascosta all'accessibilità con `Modifier.fluidGlassModalObscured()` (da mettere su quello che
  sta sotto l'host): un pop-up traslucido che lascia camminare il fuoco nella pagina è peggio di uno opaco, perché la
  pagina si vede e niente avvisa che non è raggiungibile. Usa `isPresenting`, che diventa falso **nell'istante in cui
  si chiede la chiusura**: va bene per l'accessibilità, è sbagliato per quello che il pannello ha preso in prestito
  dalla pagina (per quello c'è `isOnScreen`).
- **Colore del testo.** L'host sta alla radice, fuori da una `Surface`, dove `LocalContentColor` vale nero: ogni host
  fornisce `onSurface` (dalla 3.0.1), quindi un `Text` nudo nel pannello è leggibile anche sul tema scuro
  (`FluidHostContentColorTest`).
- **Parole.** Le etichette che l'engine dice da sé (`close`, `actions`, `notification…`) vengono da `LocalFluidLabels`,
  italiano di serie.

### Aptica, movimento ridotto

I portali **non suonano niente**: un momento, un colpo, e il momento è il tocco su quello che ha aperto (che ha già
il suo). Fanno eccezione i menu (`Open`, `Tick`, `Tap`: vedi [menu-liquidi.md](menu-liquidi.md)) e i pulsanti di un
`FluidAlert` nell'host (`Tick` per pulsante attraversato, `Tap` alla scelta).

Con le animazioni di sistema a zero (`LocalFluidMotionPolicy.current.reducedMotion`) ogni layer arriva e se ne va senza
viaggiare: le molle diventano `snapTo`, il foglio non scorre (resta solo il trascinamento col dito), il menu e
l'avviso sono presenti o assenti.

### La trappola: le lambda composable sono contenitori mutabili

È la prima cosa da sapere sui modali. Una lambda `@Composable` passata a una funzione è **un contenitore
per call-site** il cui corpo il compilatore sostituisce *sul posto* a ogni ricomposizione. Quindi:

- `FluidGlassModalPortal(visible = scelto != null) { Dettaglio(scelto!!) }` **non sopravvive alla propria chiusura**:
  nel fotogramma in cui `scelto` torna `null` il contenuto compone *niente*, e il pannello esce come un taglio
  secco, col testo già sparito mentre lo scrim sta ancora sfumando. Il sintomo è «il pop-up si chiude a scatto».
- La forma giusta è l'overload a `item`: il portale **tiene lui l'ultimo valore non nullo** e continua a passarlo
  alla lambda per tutta l'uscita. I valori sopravvivono a una chiusura (liste di azioni, rettangoli, titoli); la
  lambda no.
- `FluidConfirmSheet` congela da sola titolo, frase ed etichette (un `"Eliminare ${scelto?.nome}?"` non diventa
  «Eliminare ?» mentre esce) ma **non** `icon` e `content`, che sono lambda composable e si rileggono vive: se
  dipendono dall'oggetto da confermare, apri il portale a `item` e usa `FluidSheetScaffold` direttamente.
- Lo stesso vale, per costruzione, per il `content` di un `FluidAlert`: titolo, messaggio e azioni sono valori e
  restano per l'uscita, il `content` si rilegge vivo e se legge uno stato che il chiamante azzera nell'istante in cui
  toglie l'avviso, l'uscita lo mostra vuoto.

## `FluidGlassModalPortal` e `FluidGlassModalHost`

**Cos'è.** `FluidGlassModalPortal` non emette niente nel punto in cui lo chiami (nessuna taglia, nessun pixel): scrive
il proprio `content` nell'host in un `DisposableEffect` e lo toglie in `onDispose`, così una feature che esce dalla
composizione non lascia un modale orfano sopra l'app. Il `content` gira *nella composizione dell'host* e legge lo
stesso snapshot state della feature: resta vivo senza che nulla gli venga spinto dentro.

**Firma essenziale.**

```kotlin
@Composable
fun FluidGlassModalPortal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },          // da dove nasce, in coordinate della radice
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,               // l'annuncio di TalkBack e, in FullScreen, il titolo
  paneTint: Color? = null,                 // il colore della cosa da cui nasce (vetro tinto)
  footer: (@Composable ColumnScope.() -> Unit)? = null,
  pageColor: Color? = null,                // solo FullScreen
  content: @Composable ColumnScope.() -> Unit,
)

@Composable
fun <T : Any> FluidGlassModalPortal(item: T?, onDismissRequest: () -> Unit, /* …stessi, con (T) nelle lambda */)
```

Tutti i parametri: [api/menu-modali.md](../api/menu-modali.md).

**Uso** (da `MaterialTab` del sample):

```kotlin
var expandOrigin by remember { mutableStateOf<Rect?>(null) }
var expandOpen by remember { mutableStateOf(false) }

FluidGlassModalPortal(
  visible = expandOpen,
  onDismissRequest = { expandOpen = false },
  origin = { expandOrigin },
  paneTitle = "Card con tasti",
) {
  Dettaglio()
}

FluidButton(
  text = "Card",
  onClick = { expandOpen = true },
  modifier = Modifier.fluidExpandOrigin { expandOrigin = it },
)
```

e la forma a `item` per «un dettaglio di quello che ho scelto»:

```kotlin
FluidGlassModalPortal(item = scelto, onDismissRequest = { scelto = null }, paneTitle = "Dettaglio") { elemento ->
  Dettaglio(elemento)
}
```

**Comportamento.**

- **Chiusura = una richiesta.** Scrim, back, trascinamento del foglio chiamano `onDismissRequest`: il portale non si
  chiude da solo, lo chiude chi lo possiede con `visible = false`. Lo scrim smette di mangiare i tocchi non appena la
  chiusura è richiesta (la pagina è già sua mentre l'uscita finisce di sfumare).
- **Lo scrim è quasi niente, di proposito.** Sfocatura ×1,5 della base, nessuno spostamento (una lente grande quanto
  lo schermo piega gli angoli e sembra un fish-eye), velo nero 12% sul chiaro / 20% sullo scuro, `sizeResponse = 0`
  (altrimenti la regola della taglia lo sfocherebbe fino alla media della pagina). Se togliesse la pagina il vetro
  non servirebbe: un rettangolo dipinto costa meno. Arriva con un alpha sul pannello finito, **mai** con
  l'`intensity` del materiale (che cambia il raggio di sfocatura e ricatturerebbe tutto lo schermo a ogni
  fotogramma).
- **Il pannello rifrange la pagina «come l'ha lasciata lo scrim»** (un fondale combinato), non la pagina cruda: altrimenti
  fa un buco nitido nello scrim. Il vetro che sta *dentro* il pannello (una `FluidCard`, un tasto, un campo) rifrange
  il pannello, non la pagina dietro (`LocalFluidCanvasBackdrop` + `glassContainer`: vetro su vetro, dalla 2.13).
- **Tutto passa dalla finestra di Fluid-physics** (dalla 3.0, fase 7): anche il pop-up senza ancora (cresce da un seme
  al centro) e quello accanto a una riga. Niente layer scalato sul vetro. Senza fondale resta il ripiego opaco
  (`surfaceContainerHigh`).
- **Il contenuto viaggia con la sagoma, non compare dentro di lei**: trasla col centro corrente, si scala
  uniformemente fino a 0,90 e si materializza dal 45% al 90% del viaggio; la scritta della riga d'origine (la sua
  immagine registrata) parte da dov'era e svanisce entro metà viaggio. In chiusura il testo se ne va **per primo**
  (dal 94% al 62%) e il vetro rientra vuoto.
- **Il contenuto non scorre da solo** (a parte `FullScreen`): in un pop-up o in un foglio un corpo più alto del
  pannello viene tagliato. È il mestiere di `FluidSheetScaffold`.

### Le presentazioni

| `FluidGlassModalPresentation` | com'è | misure vere |
|---|---|---|
| `Popover` (di serie) | **con** `origin`: la sagoma parte dal rettangolo esatto dell'ancora, il pannello si posa *sopra* di lei (il bordo da cui si allontana resta fermo); **senza**: al centro, da un seme a 0,42 | largo quanto serve fino a 360 dp con origine (620 dp senza), mai oltre lo schermo meno 16 dp per lato; alto al più il 78% dell'host meno i margini; raggio `FluidRadius.Group` (22 dp) |
| `Sheet` | dal basso, a tutta larghezza, con grabber, trascinabile **da qualunque punto** (negoziato col contenuto scorrevole via nested scroll) | larghezza ≤ 620 dp (poi sta in mezzo), altezza ≤ 640 dp, angoli alti `FluidRadius.Sheet` (38 dp); si chiude oltre 110 dp di trascinamento o 900 dp/s di spinta |
| `FullScreen` | una pagina intera: barra in alto (titolo al centro, chiusura di vetro a destra), corpo che **scorre da solo**, `footer` fermo sotto, congedo trascinando | **opaca** (`surface`, o `pageColor`, o `surface` col 8% della tinta): una pagina non galleggia, copre. Da 600 dp di larghezza si stacca: margine 24 dp, larghezza di lettura (760 dp), angoli tutti tondi. Dentro non va una `LazyColumn` |
| `Expand` | come `Popover` con origine, ma a capsula (il raggio d'inizio è metà dell'altezza dell'ancora) e con la larghezza di un menu (210–300 dp). Senza azioni (e dal portale non ce ne sono) presenta il `content` | per i tasti che «diventano» un pannello |
| `ContextMenu` | cresce **accanto** all'ancora (mai sopra: la riga sollevata sta lì), senza tinta | come `Expand`. Dal portale è raro: i menu veri passano da `rememberFluidContextMenu` |

Arrivo e uscita (valori da `FluidMotion`): il `Popover`/`Expand` con origine fa una **rincorsa** di 130 ms in ease-in
fino al 92% del viaggio, poi due molle sottosmorzate di assestamento (X 0,55/300, Y 0,62/300: «due molle diverse
danno lo squash, due uguali un pallone»); senza origine solo le molle (X 0,62/260, Y 0,74/260). La dissolvenza
d'entrata è 110 ms (arriva ben prima della geometria, così il contenuto è già leggibile). In chiusura con origine la
sagoma rientra nella riga con una molla critica rigida (620) mentre la dissolvenza dura 280 ms: **deve atterrare
prima che finisca**, altrimenti il pannello svanisce per aria e la riga ricompare di colpo. Senza origine: critica a
420 e dissolvenza 240 ms. Il foglio è la sua molla: nessuna dissolvenza d'entrata, l'alpha vale solo per gli ultimi
pixel dell'uscita, ed è l'unico a cui il layer aspetta la coda delle molle prima di smontarsi.

**Tastiera e inset.** Il pop-up, il foglio e l'avviso hanno `imePadding()` (solo in basso, così l'origine resta quella
dell'host): un campo di ricerca nel pannello non finisce sotto la tastiera (3.0.1). Il pop-up rispetta anche la barra
di navigazione di sistema nel limite basso (3.0.3); il foglio a pagina intera lascia sotto la barra di sistema
l'ultima cosa (i tasti del `footer`, o la coda del contenuto).

**Trappole.**

- *Il pop-up non si apre e nessuno lo dice*: manca `LocalFluidGlassModalHostState` (o l'host non è composto).
- *Il pop-up si chiude a scatto, col testo già andato*: è la trappola delle lambda sopra → overload a `item`.
- *`origin` è una lambda*, letta mentre il portale è aperto: passa `{ rect }` (uno stato che `fluidExpandOrigin` aggiorna), non un valore
  catturato prima che la riga sia misurata.
- *`FullScreen` non è vetro* (la tinta, se c'è, resta un velo dell'8%) e scorre da solo: una `LazyColumn` dentro lancia (altezza illimitata
  nello `verticalScroll`).
- *`pageColor` solo per `FullScreen`*: le altre presentazioni sono vetro e lo ignorano. Il titolo e la chiusura prendono
  il colore che ci si legge sopra (`fluidContentColorOn`).
- *Una riga d'origine che sparisce*: il layer la ritrova per coincidenza di rettangoli (entro 4 px su centro, larghezza
  e altezza) fra le ancore registrate da `fluidExpandOrigin`; se non la trova il pannello si apre lo stesso, senza
  immagine in uscita.

**Non usarlo quando.** Per un menu di voci (→ [menu-liquidi.md](menu-liquidi.md): la sequenza, la goccia e la scelta col
dito non sono nel portale); per un avviso con pulsanti (`FluidAlert`); per un messaggio che scade.

**Versione.** Portale e host dalla 1.5.0; overload a `item` e morph dal rettangolo dell'ancora dalla 1.8; `FullScreen` e
`footer` dalla 1.32; `pageColor`, colore del testo dell'host e `imePadding` del pop-up dalla 3.0.1; limite alla barra di
navigazione 3.0.3. Dalla 3.0 ogni pannello passa dalla finestra di Fluid-physics.

### `Modifier.fluidExpandOrigin`

**Cos'è.** Riporta i limiti dell'elemento in coordinate della radice, perché un modale cresca *da lì*. Fa anche altro che il
nome non dice: **registra la riga presso l'host** e, quando la finestra nata da lei è in scena, **smette di
disegnarsi** (è un `draw`, non un layout: lo spazio resta riservato, nulla sotto si muove). La sua immagine
registrata in un `GraphicsLayer` è quella che parte col viaggio. Senza questo la riga si vedeva attraverso il pannello
di vetro: due bordi uno dentro l'altro e il titolo leggibile due volte.

```kotlin
@Composable
fun Modifier.fluidExpandOrigin(open: () -> Boolean = { false }, onMeasured: (Rect) -> Unit): Modifier
```

`open` è la dissolvenza legacy (120 ms) per chi la passava: oggi la finestra nasconde la riga da sola, senza cablare
niente. Costa poco: riporta solo quando la riga si muove.

### `FluidGlassModalHostState`

`isPresenting` (c'è una voce aperta: per la pagina sotto), `isOnScreen` (c'è ancora qualcosa disegnato, uscita compresa:
per ciò che il modale ha preso in prestito), `dismissTop()` (chiude la più recente). Il resto è interno. Lo si crea con
`rememberFluidGlassModalHostState()`.

## Fogli e conferme

### `FluidSheetScaffold`

**Cos'è.** Il *contenuto* di un foglio o di un pop-up di vetro: una testata (con la piastrella dell'icona, se c'è), un
corpo che scorre e dei tasti che **non** scorrono. Tre app se n'erano scritto uno ciascuna, e a ciascuna mancava un
pezzo che l'altra aveva (chi la larghezza massima, chi lo scorrimento, chi i tasti fermi).

```kotlin
@Composable
fun FluidSheetScaffold(
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  icon: (@Composable () -> Unit)? = null,
  tone: FluidTone = FluidTone.Primary,
  maxWidth: Dp = FluidSheetScaffoldDefaults.MaxWidth,   // 560 dp
  contentPadding: PaddingValues = FluidSheetScaffoldDefaults.ContentPadding,
  scrollable: Boolean = true,
  insetBottom: Boolean = true,
  actions: (@Composable ColumnScope.() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit = {},
)
```

**Uso** (`FluidConfirmSheet` ne è il caso più comune; direttamente quando serve un corpo vero):

```kotlin
FluidGlassModalPortal(visible = aperto, onDismissRequest = { aperto = false }, presentation = FluidGlassModalPresentation.Sheet) {
  FluidSheetScaffold(
    title = "Impostazioni",
    message = "Una frase sotto il titolo, dentro il corpo.",
    actions = {
      FluidButton(text = "Fatto", onClick = { aperto = false }, size = FluidButtonSize.Large, fillWidth = true)
    },
  ) {
    FluidListGroup { /* righe */ }
  }
}
```

**Comportamento.**

- **I tasti non scorrono, il corpo sì.** Testata e tasti si misurano *per primi* e il corpo prende quello che avanza
  (`fluidSheetBodyBudget`): non un `weight`, che in una colonna senza altezza massima dà al corpo altezza zero e il foglio
  sembra vuoto. Senza tetto (il foglio sta in uno scorrimento, in un test) il corpo ha un tetto finito (32 768 px),
  perché un `verticalScroll` misurato all'infinito lancia.
- **Un vuoto sta solo fra due cose che ci sono**: un corpo vuoto non lascia un buco sotto la testata, dei tasti assenti non
  lasciano un buco in fondo.
- **Il testo è `onSurface`**, anche come `LocalContentColor` (fuori da una `Surface` varrebbe nero): un `Text` nudo nel
  corpo è leggibile senza che l'app lo sappia. Il tono sta sulla piastrella dell'icona (`FluidToneTile`), mai sul foglio.
- Titolo con semantica `heading()`.
- `insetBottom`: **vero in un foglio** (sta attaccato al bordo, lascia la barra di navigazione), **falso in un pop-up** (sta
  a mezz'aria, sarebbe aria a vuoto). `FluidConfirmSheet` lo decide da solo dalla presentazione.

**Trappole.** `scrollable = false` se il corpo è una `LazyColumn` (o scorre da sé): due scorrimenti annidati lanciano.
Il corpo è una `ColumnScope`: `FluidListGroup`, `FluidCard`, `FluidTextField` ci stanno come in una schermata, e sul vetro
sono vetro su vetro da soli.

**Versione.** 3.0.0.

### `FluidConfirmSheet`

**Cos'è.** Portale + `FluidSheetScaffold` e basta: le quattro conferme che le app si erano scritte (eliminare,
condividere, usare i dati mobili, approvare) erano tutte questo, più un corpo. Un titolo, una frase, due tasti.

```kotlin
@Composable
fun FluidConfirmSheet(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  title: String,
  onConfirm: () -> Unit,
  message: String? = null,
  icon: (@Composable () -> Unit)? = null,
  tone: FluidTone? = null,                  // di serie Danger se destructive, altrimenti Primary
  confirmLabel: String = "Conferma",
  cancelLabel: String = "Annulla",
  destructive: Boolean = false,
  confirmEnabled: Boolean = true,
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Sheet,
  origin: () -> Rect? = { null },
  content: @Composable ColumnScope.() -> Unit = {},
)
```

**Uso** (da `RowsAndSheetsDemo`):

```kotlin
FluidConfirmSheet(
  visible = overlay == DemoOverlay.Destroy,
  onDismissRequest = { overlay = null },
  title = "Eliminare 12 elementi?",
  message = "Non si può annullare.",
  icon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
  destructive = true,
  confirmLabel = "Elimina dal telefono",
  onConfirm = { overlay = null },
) {
  FluidListGroup { /* l'elenco degli effetti: va sul foglio, non in una frase più lunga */ }
}
```

**Comportamento.**

- **La conferma non chiude il foglio: lo chiude chi lo possiede.** `onDismissRequest` vale «annulla», `onConfirm` vale
  «fatto»; far chiudere da qui avrebbe voluto dire che `onDismissRequest` vale due cose.
- **Un tasto di conferma solo, e dice cosa fa** («Elimina dal telefono», non «OK»). Con `destructive` è
  `FluidButtonStyle.Destructive` e la piastrella prende `FluidTone.Danger`: l'engine non ha un secondo registro per le azioni che
  non si disfano, ha il rosso, e vale al più una volta per schermata. L'annulla è `Tinted`, sotto, in fondo.
- **Le parole restano mentre esce** (titolo, frase, etichette, `destructive`, tono, `confirmEnabled`); `icon` e `content` no
  (vedi la trappola delle lambda).
- Senza host non si apre e non va in crash.

**Non usarlo quando.** La decisione ha più di due esiti (→ `FluidAlert` con tre azioni), o serve un modulo (→ foglio con
`FluidSheetScaffold`), o una riga di testo (→ `FluidPromptAlert`).

**Versione.** 3.0.0.

### `FluidSheet` (deprecato) e `FluidGrabber`

`FluidSheet` è un `ModalBottomSheet`: una finestra di piattaforma, quindi vetro **dipinto** (`fluidStaticGlassSurface`).
Dalla 3.0 (fase 9) con un host alla radice **non apre una finestra**: chiede all'host il foglio di vetro
(`Sheet`), e `sheetState`, `modifier` e `showGrabber` vengono ignorati (il foglio di vetro ha il suo trascinamento, il suo
grabber, i suoi inset). Alla richiesta di chiusura il foglio esce con l'animazione dell'host e **solo dopo (300 ms)**
chiama `onDismissRequest`: chi lo toglie prima dalla composizione perde l'uscita. Senza host è il foglio di piattaforma di
sempre. Sostituto: `FluidGlassModalPortal(presentation = Sheet)` + `FluidSheetScaffold`.

`FluidGrabber` è la maniglia: capsula 36×5 dp al 18% di `onSurface` (quella di Material è quasi il doppio, ed è la prima
cosa che tradisce un foglio). Il foglio di vetro la mette da solo.

## Avvisi

### `FluidAlert` e `FluidAlertAction`

**Cos'è.** Un avviso alla UIKit: centrato, stretto, **fatto di righe sottili e non di spazio** — i pulsanti sono celle di
una griglia che arriva ai bordi della card, non pillole in un angolo (Material mette i pulsanti in basso a destra, e quella
sola differenza basta a collocare un dialogo sulla piattaforma sbagliata). Due azioni affiancate, tre o più (e una sola)
in pila, perché a tre le etichette non stanno più su una riga.

```kotlin
@Composable
fun FluidAlert(
  onDismissRequest: () -> Unit,
  title: String,
  modifier: Modifier = Modifier,
  message: String? = null,
  actions: List<FluidAlertAction>,
  inHost: Boolean = true,
  content: (@Composable ColumnScope.() -> Unit)? = null,
)

data class FluidAlertAction(
  val label: String, val onClick: () -> Unit,
  val emphasis: FluidAlertAction.Emphasis = FluidAlertAction.Emphasis.Normal,   // Normal, Preferred, Destructive
  val enabled: Boolean = true,
)
```

**Uso** (da `MenusDemo`): l'avviso esiste finché è composto.

```kotlin
if (mostra) {
  FluidAlert(
    onDismissRequest = { mostra = false },
    title = "Eliminare la nota?",
    message = "Non si può annullare.",
    actions = listOf(
      FluidAlertAction(label = "Annulla", onClick = { mostra = false }),
      FluidAlertAction(
        label = "Elimina",
        emphasis = FluidAlertAction.Emphasis.Destructive,
        onClick = { elimina(); mostra = false },
      ),
    ),
  )
}
```

**Comportamento.**

- **Composto = aperto.** Non c'è un `visible`: chi lo toglie dalla composizione lo chiude, **e nell'host l'uscita si vede lo
  stesso, fino in fondo**: la voce si ritira (`retire`) invece di sparire e la toglie il layer a uscita finita
  (`finishExit`). Prima, con `unregister`, un avviso tolto spariva di colpo, testo e vetro insieme.
- **Nessun pulsante chiude da solo**: ogni `onClick` deve togliere l'avviso. Scrim e back chiamano `onDismissRequest`
  (come il `Dialog`).
- **Nell'host (3.0)**: vetro vero (`panelOptics`, la pagina rifratta), arrivo alla iOS (la sagoma parte dal pannello gonfiato
  dell'8% e si posa con `FluidMotion.snappy()`, il materiale entra in 140 ms, il contenuto la segue in scala uniforme
  0,9–1,1), uscita in 160 ms (la sagoma scende al 96% mentre tutto si spegne). **I pulsanti hanno la goccia**: la goccia
  segue il dito di pulsante in pulsante (in orizzontale fra due affiancati, in verticale nella pila), un `Tick` per
  pulsante attraversato, un `Tap` alla scelta, poi l'azione. Si sceglie col dito che ha toccato: tocca, trascina,
  rilascia su un pulsante (stessa regola dei menu).
- **Misure**: larghezza 280 dp (meno 24 dp di margine per lato su schermi stretti), altezza al più l'86%; il messaggio
  scorre nello spazio che i pulsanti lasciano (**i pulsanti sono l'unica uscita**, e non devono mai finire fuori
  dallo schermo: un changelog lungo spingeva i pulsanti fuori e l'avviso diventava una trappola). Altezza minima di un
  pulsante 44 dp; l'etichetta va su due righe. `Preferred` è semibold (non cambia colore), `Destructive` prende il colore
  d'errore, `enabled = false` al 35%.
- **Sopra la tastiera** (`imePadding`), quindi con un campo nel `content` non gli finisce sotto.
- **A11y**: i pulsanti sono `clickable(role = Button)` per TalkBack e tastiera (dal dito sceglie la selezione: il pannello consuma il
  down prima, un click solo); il titolo è il `paneTitle`; il fuoco entra nella card e lo prende il primo figlio che lo vuole (il
  campo di un `FluidPromptAlert`, e con lui la tastiera).
- **Senza host, o `inHost = false`**: il `Dialog` di piattaforma — vetro dipinto, arriva da 1,12 (scende a 1), card ≤ 280 dp.

**Trappole.** Una `FluidAlert` con zero azioni ha come unica uscita lo scrim e il back. Con una azione sola sta in pila,
non affiancata. `content` è una lambda composable (vedi sopra). Un avviso nell'host sta **sotto** un menu aperto dopo di lui e
sopra una voce aperta prima.

**Non usarlo quando.** C'è un modulo (→ foglio), o una decisione da accompagnare con un elenco (→ `FluidConfirmSheet` con
`content`).

**Versione.** `inHost`, il vetro vero, la goccia dei pulsanti e l'uscita che si vede anche dopo la rimozione: 3.0.0.

### `FluidPromptAlert`

**Cos'è.** Un `FluidAlert` con un `FluidTextField` nel suo slot, e tutto quello che ogni app si riscriveva intorno: il tasto
«Salva» che si accende solo quando ha senso, il testo che si ritaglia, la tastiera che conferma.

```kotlin
@Composable
fun FluidPromptAlert(
  title: String,
  onDismissRequest: () -> Unit,
  onConfirm: (String) -> Unit,
  message: String? = null,
  initialValue: String = "",
  placeholder: String? = null,
  confirmLabel: String = "Salva",
  cancelLabel: String = "Annulla",
  validate: (String) -> FluidPromptValidation = ::fluidPromptNotBlank,
  keyboardOptions: KeyboardOptions = /* Sentences + Done */,
  trim: Boolean = true,
  selectAll: Boolean = true,
  singleLine: Boolean = true,
)
```

**Uso** (da `RowsAndSheetsDemo`):

```kotlin
if (overlay == DemoOverlay.Calibrate) {
  FluidPromptAlert(
    title = "Calibra",
    placeholder = "Es. 54,3",
    confirmLabel = "Calibra",
    validate = ::validateReading,   // Incomplete se vuoto, Invalid("…") se fuori misura, Valid altrimenti
    onDismissRequest = { overlay = null },
    onConfirm = { valore -> overlay = null; applica(valore) },
  )
}
```

**Comportamento.**

- **Il tasto è la validazione.** `validate` vede il testo (ritagliato se `trim`) a ogni modifica: finché non risponde
  `FluidPromptValidation.Valid` il tasto di conferma è spento. **Tre stati e non un booleano**: `Incomplete` (campo vuoto: non
  ha sbagliato, non ha ancora scritto — tasto spento e campo muto: dirgli «non può essere vuoto» mentre l'avviso si apre
  è rimproverare chi non ha cominciato), `Invalid(message)` (la frase sotto il campo, in errore). Di serie
  `fluidPromptNotBlank`.
- **Validazione e conferma vedono lo stesso testo** (`fluidPromptText`): se la validazione guardasse il campo grezzo e la
  conferma quello ritagliato, «  » sarebbe valido e confermerebbe una stringa vuota.
- **L'invio conferma**, solo se la validazione lo permette (`ImeAction.Done`; `Go` o `Send` se lo dai a `keyboardOptions`).
- **Si apre pronto a scrivere**: il campo prende il fuoco da solo (con `runCatching`: una finestra non pronta non fa cadere
  l'app) e con `selectAll` il testo iniziale è tutto selezionato (rinominare è sostituire).
- `onConfirm` riceve il testo già ritagliato e **non chiude** l'avviso.
- Sta nell'host come ogni `FluidAlert`, e sale sopra la tastiera.

**Trappole.** Il campo è ricordato con `remember(initialValue)`: **cambiare `initialValue` mentre l'avviso è aperto azzera
quello che l'utente ha scritto**. Un `FluidPromptAlert` è composto = aperto, come ogni avviso.

**Versione.** 3.0.0 (KDoc ripulito in 3.0.1).

## Token, costanti, Defaults

Le misure dei pannelli (`FluidPopoverMargin` 16 dp, `FluidPopoverGap` 10 dp, 620/360 dp, 78% di altezza, 38 dp del foglio,
110 dp e 900 dp/s del congedo) sono `private` nel file: **non sono API**. Quello che un'app può leggere:

| cosa | valore |
|---|---|
| `FluidSheetScaffoldDefaults.MaxWidth` | 560 dp |
| `FluidSheetScaffoldDefaults.ContentPadding` | 20 / 16 / 20 / 16 dp (in alto 16: sotto il grabber ce n'è già un po', ma un pop-up non ha niente sopra e il suo angolo è da 22 dp) |
| `FluidSheetScaffoldDefaults.IconTileSize` | 44 dp (più della riga, 32: qui è l'unico segno) |
| `FluidSheetScaffoldDefaults.HeaderGap` / `BodyGap` / `HeaderToBody` / `BodyToActions` / `ActionGap` | 14 / 12 / 12 / 16 / 8 dp |
| `FluidMotion.PopoverFadeInMillis` / `PopoverFadeOutMillis` / `PopoverWindowFadeOutMillis` | 110 / 240 / 280 ms |
| `FluidMotion.PopoverWindowRunUp`, `PopoverWindowRunUpEnd` | 130 ms ease-in, fino al 92% del viaggio |
| `FluidMotion.PopoverWindowSettleX` / `PopoverWindowSettleY` | molle (0,55, 300) / (0,62, 300) |
| `FluidMotion.PopoverOpenX` / `PopoverOpenY` | molle (0,62, 260) / (0,74, 260), per il pop-up senza origine |
| `FluidMotion.PopoverWindowExit` / `PopoverExit` | critiche, 620 / 420 |
| `FluidMotion.SheetSettle` | il foglio trascinato che torna: (0,9, 500) |

## Cosa è verificato dai test

- `FluidGlassModalStateTest` (engine-ui): una voce si registra una sola volta; lo smontaggio la porta via; il back chiude
  la più **recente**; una voce chiusa non è «presente»; `retire` chiude e mantiene la voce fino a `finishExit`.
- `FluidGlassModalContentTest`: `Expand` con contenuto e senza azioni disegna il contenuto (non un pannello vuoto); un
  pop-up e un foglio disegnano sempre il contenuto; foglio e pagina intera arrivano dal basso, il resto nasce da un'ancora.
- `FluidSheetScaffoldLayoutTest` e `FluidRowsAndSheetsTest` (sample, Robolectric): corpo = quello che resta dopo testata e
  tasti; tasti in vista sotto un corpo più lungo del foglio; larghezza massima; conferma e annulla sono due cose;
  il tasto invio conferma solo se valido; un valore fuori misura mostra la frase e tiene spento il tasto.
- `FluidPromptAlertTest`: vuoto = `Incomplete`; trim uguale per validare e confermare; selezione d'apertura.
- `FluidPopoverKeyboardTest`: il pop-up si posa sopra la tastiera. `FluidHostContentColorTest`: il testo negli host ha il
  colore della superficie sul tema scuro.
- `FluidMenuHandOffTest` (sample): l'avviso sta nell'host senza finestre di piattaforma, esce anche dopo la rimozione,
  un suo pulsante sceglie con un `Tap` e poi l'azione.
- `AllGlassTest`: la chiusura della pagina intera è un tasto di vetro e chiude; con un host `FluidSheet` è il foglio di
  vetro e dice «chiuso» una volta sola, dopo l'uscita.

**Non è provato da nessun test**: il vetro (gli host dei test hanno fondale nullo), i fotogrammi dell'arrivo e
dell'uscita, la goccia dei pulsanti dell'avviso sul vetro, il trascinamento fra i pulsanti, la tastiera sopra un
`FluidPromptAlert` nell'host. Si guardano in mano.

## Dove guardare

Sorgenti in `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`:

| file | cosa c'è dentro |
|---|---|
| `FluidGlassModal.kt` | stato dell'host, portale (due overload), `FluidGlassModalPresentation`, `FluidContextAction`, `fluidExpandOrigin`, `FluidGlassModalHost`, lo scrim, la finestra ancorata (`FluidAnchoredPopover`), il foglio e la pagina intera, il menu contestuale (controller, ancora, modificatore), i numeri privati |
| `FluidMenuLayer.kt` | i layer dell'host per i menu (`Expand`, contestuale) e per l'avviso (`FluidAlertLayer`, la card, i pulsanti con la goccia) |
| `FluidSheet.kt` | `FluidSheet` (deprecato), `FluidGrabber`, `FluidAlert`, `FluidAlertAction`, il `Dialog` di ripiego |
| `FluidSheetScaffold.kt` | `FluidSheetScaffold`, `FluidSheetScaffoldDefaults`, `FluidConfirmSheet`, l'aritmetica pura del budget |
| `FluidPromptAlert.kt` | `FluidPromptAlert`, `FluidPromptValidation`, `fluidPromptNotBlank` |
| `FluidMotion.kt` | i token (`Popover*`, `SheetSettle`, e i menu: vedi [menu-liquidi.md](menu-liquidi.md)) |

Il sample che li usa: `MaterialTab.kt` (portali con e senza origine, foglio), `catalog/RowsAndSheetsDemo.kt` (conferme e
prompt), `catalog/MenusDemo.kt` (l'avviso a due e a tre pulsanti), `catalog/AllGlassDemo.kt` (la pagina intera),
`GalleryActivity.kt` (la radice con i tre host).
