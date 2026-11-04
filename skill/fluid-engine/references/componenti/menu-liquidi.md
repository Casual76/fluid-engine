# Menu liquidi

I menu dell'engine (3.0): una lista di voci (`FluidContextAction`) che **nasce da quello che l'ha aperto** — un tasto, un'azione
della barra, una riga — e gli torna dentro al congedo. Sono *un menu solo con quattro ingressi*: lo stesso pannello, lo
stesso viaggio, lo stesso materiale, la stessa aptica. Prima erano tre prodotti con tre storie (il tasto che si
espande, il contestuale, il menu sopra il tasto), ognuno con i suoi difetti, e nessuno si sceglieva col dito che
l'aveva aperto.

Modello mentale: **un menu si sceglie col dito che l'ha aperto, e un rilascio altrove lo lascia aperto.** Tieni premuto, il
menu si apre e prende il dito; trascina su una voce e rilascia: scelta. Rilascia fermo (o fuori dalle voci): il menu
resta e si sceglie con un tocco. La spiegazione di fondo, con il perché di ogni scelta, è in
[regole.md](../regole.md) («Un menu si sceglie col dito che l'ha aperto»): qui c'è come si usa e cosa fa il codice.

Il portale, l'host dei modali, l'avviso e i fogli stanno in [menu-modali.md](menu-modali.md). La superficie che viaggia
(`FluidPhysicsState`, la goccia, `driveExternally`) è di [fluid-physics.md](fluid-physics.md): qui si usa, non si spiega.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un tasto che apre un menu **e basta** («Ordina», tre puntini) | `FluidMorphMenuButton(opensOnTap = true)`; nella barra `FluidBarAction(actions = …, opensOn = FluidMenuOpensOn.Tap)` |
| un tasto con un'azione sua (il tocco) e un menu di varianti (il tenuto premuto) | `FluidMorphMenuButton` o `FluidBarAction(actions = …)` di serie |
| un menu su una riga di lista | `FluidListRow(contextActions = { … })` (anche `FluidVividCard`, `FluidSidebarRow`) |
| un menu su una card o su una vista che non ha click propri | `Modifier.fluidContextMenu(actions = { … })` |
| un menu su un elemento che gestisce già i suoi click | `rememberFluidContextMenu` + `Modifier.fluidContextMenuAnchor` e `controller.open()` nel suo click lungo |
| una voce che dice la scelta di adesso («Ordina per…») | `FluidContextAction(selected = true)` |
| un gruppo di voci staccato dal resto («Elimina») | `FluidContextAction(startsSection = true)` (e `destructive = true`) |
| aprire il menu da una scorciatoia, senza un tasto | `FluidMorphMenuState.open(bounds, text, icon, items)` (si sceglie con un tocco) |
| un menu che sta in una barra, vicino al bordo alto | `FluidBarAction(actions)`: si apre **sull'ancora**, verso il basso |
| **non così**: `FluidGlassMenuButton` | deprecato: `FluidMorphMenuButton` + `FluidMorphMenuHost` |
| **non così**: un pannello di contenuto fatto con `FluidGlassModalPortal(presentation = ContextMenu)` per «fare un menu» | non ha la sequenza, la goccia né la scelta col dito: sono dei menu veri |
| **non così**: molte voci | il menu non scorre: una riga è 48 dp e il pannello ha l'altezza di tutte le sue righe. Per una lista lunga un foglio o `FluidPickerRow` |

## I quattro ingressi

| ingresso | come | si apre | host | scrim |
|---|---|---|---|---|
| il tasto che si trasforma | `FluidMorphMenuButton(state, actions, onClick, …, opensOnTap)` | tenuto premuto; con `opensOnTap` al tocco (e `onClick` non viene chiamato) | `FluidMorphMenuHost`, alla radice | nessuno: la pagina resta a colori, separa il bordo del vetro |
| l'azione della barra | `FluidBarAction(icon, contentDescription, onClick, …, actions, opensOn)` | tenuto premuto; con `opensOn = Tap` al tocco | `FluidGlassModalHost` | sì, quello dei modali |
| la riga | `FluidListRow(contextActions = { … })` | tenuto premuto: la riga si solleva, il pannello cresce accanto | `FluidGlassModalHost` | sì |
| una card qualunque | `Modifier.fluidContextMenu(actions = { … })` | tenuto premuto | `FluidGlassModalHost` | sì |

Il **tenuto premuto apre sempre**, anche con `Tap`: è lui che porta il dito sul menu e permette di scegliere trascinando.
`Tap` è per il tasto il cui *unico* mestiere è il menu (cinque app se lo riscrivevano a mano); un tasto che ha un'azione
sua la tiene al tocco, e il menu si scopre tenendo premuto.

## `FluidContextAction`

**Cos'è.** Una voce: etichetta, icona opzionale, e cosa fare. È la stessa classe per tutti e quattro gli ingressi (e per il
vecchio menu): un'app che aggiorna ritrova le sue voci nel pannello nuovo.

```kotlin
data class FluidContextAction(
  val label: String,
  val icon: ImageVector? = null,
  val destructive: Boolean = false,    // colore d'errore, al più una per menu
  val enabled: Boolean = true,         // spenta: niente tick, non si sceglie
  val startsSection: Boolean = false,  // banda sopra di lei (sulla prima voce non conta)
  val selected: Boolean = false,       // spunta a destra, «selezionato» per TalkBack
  val onClick: () -> Unit,
)
```

**Uso** (da `MenusDemo`):

```kotlin
private fun demoActions(pick: (String) -> Unit): List<FluidContextAction> = listOf(
  FluidContextAction(label = "Condividi", icon = Icons.Rounded.Share) { pick("Condividi") },
  FluidContextAction(label = "Modifica (spenta)", icon = Icons.Rounded.Edit, enabled = false) { pick("Modifica") },
  FluidContextAction(label = "Elimina", icon = Icons.Rounded.Delete, destructive = true, startsSection = true) { pick("Elimina") },
)
```

**Comportamento.**

- **`startsSection`** sta *prima* di `onClick` (e `selected` pure) perché la lambda in coda resti l'azione: una chiamata che passa
  `onClick` per posizione, dopo la 3.0, **non compila**; per la JVM la firma è comunque cambiata, quindi un modulo già
  compilato va ricompilato.
- **`selected`** (3.0.1) è per i menu che scelgono fra alternative («Ordina per», «Vista»): prima ogni app metteva
  un'icona di spunta a mano, che TalkBack non leggeva. La spunta va a destra, nel colore della voce; il pannello si allarga
  per tenerla.
- **`destructive`** è un colore, non una conferma: l'azione parte alla scelta. Per una cosa che non si disfa, la voce apre un
  `FluidConfirmSheet` o un `FluidAlert`.
- **L'azione parte dopo la richiesta di congedo**, nello stesso istante: `pick` fa `Tap`, chiede la chiusura e chiama
  `onClick`. Se `onClick` apre un altro modale, questo sta **sopra** il menu che sta uscendo.
- **`actions` è una lambda, valutata all'apertura**, non in composizione: legge lo stato del momento. Lista vuota =
  nessun menu (`open()` ritorna `false`, nessun `Open`, e il chiamante può ripiegare sul suo click lungo).
- Etichetta su una riga sola, con i puntini oltre il tetto (vedi le misure).

## Il tasto che si trasforma: `FluidMorphMenuButton`, `FluidMorphMenuHost`, `FluidMorphMenuState`

**Cos'è.** Il tasto che *si espande* nel proprio menu, la prima integrazione vera di Fluid-physics nell'interfaccia: la capsula del
tasto viaggia fino a essere il pannello e al congedo torna capsula. Il predecessore (`FluidGlassMenuButton`) faceva comparire
il pannello *sopra* il tasto: due superfici e una staffetta. Qui la superficie è una.

**Firma essenziale.**

```kotlin
@Composable
fun FluidMorphMenuButton(
  state: FluidMorphMenuState = LocalFluidMorphMenuState.current ?: rememberFluidMorphMenuState(),
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  backdrop: GlassBackdropState,           // obbligatorio: il fondale del tasto
  enabled: Boolean = true,
  opensOnTap: Boolean = false,
)

@Composable
fun FluidMorphMenuHost(state: FluidMorphMenuState, backdrop: GlassBackdropState, modifier: Modifier = Modifier)

val LocalFluidMorphMenuState: ProvidableCompositionLocal<FluidMorphMenuState?>
```

Tutti i parametri: [api/fluid-physics.md](../api/fluid-physics.md).

**Uso** (da `MenusDemo`: «Azione» si tiene premuto, «Ordina» si apre al tocco).

```kotlin
val menu = rememberFluidMorphMenuState()
CompositionLocalProvider(LocalFluidMorphMenuState provides menu) {
  FluidMorphMenuButton(
    actions = { listOf("Per data", "Per nome").map { label -> FluidContextAction(label = label, selected = label == ordine) { ordine = label } } },
    onClick = {},
    text = "Ordina",
    backdrop = backdrop,
    opensOnTap = true,
  )
  FluidMorphMenuHost(state = menu, backdrop = backdrop)   // alla radice, o nel palco: il menu cresce dentro l'host
}
```

**Comportamento.**

- **L'ancora** è una capsula di vetro con i gesti dei tasti (`FluidPressFeedback.Expand`: cresce e si illumina sotto il dito,
  44 dp di altezza, 20 dp di margine laterale, la stessa taglia di `FluidGlassButton`). Tocco = `onClick` (un `PressClick`
  come ogni tasto), tenuto premuto = menu (un solo `Open`, e il dito passa al menu), `opensOnTap` = il tocco apre (con un
  `Open`, non un `Tap`: un'apertura è un'apertura).
- **Mentre il menu è in scena l'ancora non disegna niente** (`drawWithContent { if (!hidden) drawContent() }`: un cambio di
  proprietario, non un'uscita) e al suo posto, allo stesso pixel, sta la superficie dell'host. Mai un alpha di un layer sopra il vetro:
  un vetro che sfuma sopra un altro vetro non è né l'uno né l'altro. Lo spazio resta riservato.
- **Il pannello sta sopra il tasto se ci sta, altrimenti sotto**, col centro il più vicino possibile al tasto e sempre
  dentro i margini dell'host (16 dp); un menu che nasce da un tasto in basso cresce verso l'alto.
- **L'apertura è sincrona.** `openFrom` risolve il pannello con le misure che l'host ha scritto al suo ultimo layout, installa il
  viaggio e nasconde il tasto *nello stesso istante del gesto*: il primo fotogramma dell'host è già la sagoma sul tasto.
  Prima l'host si misurava da sé a ogni apertura, e per un fotogramma non c'era né il tasto né il menu, o c'erano tutti e due.
- **Un tocco fuori, ovunque**, chiude (in silenzio). L'acchiappa-tocco dell'host copre solo i limiti dell'host: se è montato in
  un palco, le quattro fasce attorno le disegna l'host dei modali (`outsideCatchers`), l'unico che vede tutto lo schermo.
  Il back (`BackHandler`) chiude. Un tasto che esce dalla composizione col menu aperto se lo porta via.
- **Riaprire dallo stesso tasto mentre si chiude è un'inversione**: il viaggio torna verso il pannello da dove si trova.
- Il colore del testo dentro l'host è `onSurface` (3.0.1).

**`FluidMorphMenuState`.** `isOpen` (dal gesto alla richiesta di congedo), `isOnScreen` (finché c'è qualcosa in scena, ritorno
compreso: l'ancora resta nascosta), `dismiss()`, e `open(bounds, text, icon, items, content)`: apre dalla sagoma di `bounds` (pixel
della radice) **senza il dito** — per un tasto tuo che apre al tocco, pubblico dalla 1.13. Chi lo usa deve nascondere la
propria ancora finché `isOnScreen` è vero. `LocalFluidMorphMenuState` (3.0.1) evita di passare lo stato per ogni
livello: l'app lo fornisce una volta e il tasto lo prende da solo; chi apre da lontano lo chiede lì.

**Trappole.**

- *Il tasto non apre niente e non vibra*: manca il `FluidMorphMenuHost` composto (l'apertura legge le sue misure). Con `opensOnTap`
  il tocco è morto (non chiama `onClick`).
- *Il tasto ha il proprio stato*: senza un `LocalFluidMorphMenuState` e senza `state` passato, il default è
  `rememberFluidMorphMenuState()` — uno stato che nessun host pilota. Il tasto fa `onClick` e il menu non si apre.
- *Un solo menu per stato*: se un menu è in scena e un secondo tasto prova ad aprire, la richiesta è ignorata
  (`openFrom` ritorna falso e il colpo `Open` non suona). Con due menu simultanei servono due stati.
- *Il tasto non riappare appena il menu si chiude*: è voluto. `finishClose` aspetta che anche la coda della goccia
  (`physics.liquid.quiet`) si sia posata sulla sagoma del tasto; se tornasse prima, il tasto vero comparirebbe sopra una goccia ancora in moto.
- *Il pannello non scorre.* Con molte voci cresce oltre quello che ci sta.

**Non usarlo quando.** Il tasto non è una capsula di vetro (usa `FluidBarAction(actions)` nella barra, o un controller
contestuale), o non hai un punto in cui montare l'host.

**Versione.** Il tasto dalla 1.9.4 (portale/host come i modali); `open` pubblico dalla 1.13.0; `opensOnTap`, la scelta col dito
e il viaggio nuovo dalla 3.0; `LocalFluidMorphMenuState` dalla 3.0.1.

## L'azione della barra: `FluidBarAction(actions, opensOn)` e `FluidMenuOpensOn`

**Cos'è.** L'azione in una barra (una lente di vetro da 48 dp sul fondale condiviso della barra) con un menu opzionale. Una
barra ha posto per due o tre lenti, e l'app di verbi ne ha di più: gli altri non esistevano. Con `actions` non nullo l'azione
diventa l'**ancora di un menu `Expand`** dell'host dei modali.

```kotlin
@Composable
fun FluidBarAction(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  actions: (() -> List<FluidContextAction>)? = null,
  opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
)

enum class FluidMenuOpensOn { LongPress, Tap }
```

**Uso** (da `MenusDemo`): la stella si tiene premuta, i tre puntini si aprono al tocco.

```kotlin
FluidBarAction(icon = Icons.Rounded.Star, contentDescription = "Preferiti", onClick = { }, actions = { voci })
FluidBarAction(icon = Icons.Rounded.MoreVert, contentDescription = "Altro", onClick = {}, actions = { voci }, opensOn = FluidMenuOpensOn.Tap)
```

**Comportamento.** Il pannello si posa **sull'ancora** (`OverAnchor`: il bordo da cui si allontana resta fermo, così il tasto non salta nel
fotogramma in cui si apre) verso il basso se ci sta, altrimenti verso l'alto dal bordo basso. La sagoma parte dal tasto
*premuto* (la lente com'è gonfiata dal dito, `FluidPressFeedback.ExpandRound`), con la pelle e la lente del tasto
(`tintFrom`/`opticsFrom`), e la faccia (l'icona, 22 dp) se ne va entro il 35% del viaggio. Con `Tap` il tocco apre senza il dito
(il menu si sceglie toccando) e suona `Open`; con `LongPress` il click lungo apre e passa il dito, suonando `Open` solo se il
menu si apre. `FluidMenuOpensOn` vale solo per questo ingresso: il tasto che si trasforma ha il booleano `opensOnTap`.

**Trappole.** *Senza `FluidGlassModalHost` il menu non si apre*; con `opensOn = Tap` e senza host il tasto è **morto** (il tocco chiama
l'apertura, non `onClick`). Con `actions = null` è il vecchio `FluidGlassIconButton` e nient'altro.

**Versione.** `actions` (il menu `Expand` sui tasti barra) dalla 1.8.0; `opensOn`, il dito, il viaggio nuovo dalla 3.0.

## Il menu di una riga o di una card: `rememberFluidContextMenu`, `fluidContextMenuAnchor`, `fluidContextMenu`

**Cos'è.** Il menu contestuale: tieni premuta una riga, la riga si **solleva** (diventa una card di vetro sorella della sua immagine
registrata), il pannello cresce accanto a lei da una goccia appesa al suo bordo, sotto il dito. Due pezzi e non uno, e la
divisione non è ordine: un `detectTapGestures { onLongPress }` accanto al `combinedClickable` di una riga **non scatta mai**
(il click consuma il gesto e il click lungo viene mangiato in silenzio, com'è successo la prima volta). Chi gestisce già i click deve aprire il
menu dal **proprio** click lungo: è il mestiere di `FluidContextMenuController.open()`. `Modifier.fluidContextMenu` è la forma comoda per gli
elementi che *non* gestiscono click.

```kotlin
@Composable
fun rememberFluidContextMenu(actions: () -> List<FluidContextAction>, tint: Color? = null): FluidContextMenuController

@Composable
fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier

@Composable
fun Modifier.fluidContextMenu(actions: () -> List<FluidContextAction>, enabled: Boolean = true): Modifier

class FluidContextMenuController { val isOpen: Boolean; fun open(): Boolean; fun dismiss() }
```

**Uso.** La forma comoda, per una card senza click propri (da `MenusDemo`):

```kotlin
Box(
  modifier = Modifier
    .fillMaxWidth().height(96.dp)
    .fluidContextMenu(actions = {
      listOf(
        FluidContextAction(label = "Copia", icon = Icons.Rounded.Share) { copia() },
        FluidContextAction(label = "Mi piace", icon = Icons.Rounded.Favorite) { piace() },
      )
    }),
)
```

La forma per un elemento con click propri (è quello che fa `FluidListRow` dietro `contextActions`):

```kotlin
val menu = rememberFluidContextMenu(actions = { voci })
Box(
  modifier = Modifier
    .fluidContextMenuAnchor(menu)
    .combinedClickable(onClick = { apri() }, onLongClick = { menu.open() }),
)
```

**Comportamento.**

- **L'ancora registra solo mentre un dito è giù.** `fluidContextMenuAnchor` osserva il tocco nel passaggio *Initial* senza consumare
  niente, e dipinge l'elemento in un `GraphicsLayer` fra il tocco e il rilascio: a riposo una lista di ancore non costa nulla.
  Mentre il dito è giù la riga si gonfia (2%) e accende la luce speculare dei controlli di vetro: senza, un click lungo
  era mezzo secondo di niente seguito da un overlay intero (e mezzo secondo è quanto basta per decidere che il controllo è rotto).
  Con il movimento ridotto la riga non si gonfia.
- **`open()` ritorna `false`** senza host, con un menu già aperto o con zero voci: chi chiama suona `Open` **solo se ritorna vero**
  (`FluidListRow` altrimenti ripiega sul suo `onLongClick` con `GestureStart`).
- **Aperto, la riga non si disegna più** nel suo posto (`lifted`: lo spazio resta riservato, nulla sotto si muove). La riga che vedi è
  la sua *registrazione* in una superficie Fluid-physics: sagoma dalla riga alla riga gonfiata del 3% attorno al centro del bordo
  contro cui si apre il menu; a scalare è solo l'immagine, che non è vetro. La riga torna alla pagina **solo a uscita finita**
  (`isOnScreen`): restituirla alla richiesta di congedo la mostrava sotto un pannello che le si stava ancora ripiegando addosso.
- **La goccia del contestuale**: un cerchio da 28 dp appeso al bordo della riga che guarda il pannello, sotto il dito (o al centro
  della riga), che entra nella riga di 4 dp; il corpo del menu esce da lì come una goccia e a progresso 0,15 è tutta fuori. Prima il seme era
  il pannello a 0,42 attorno al perno: un pannello piccolo che compariva dal nulla accanto alla riga, due oggetti. Il punto d'origine è
  **fermato all'apertura**: seguire il dito sulle voci sposterebbe la partenza in volo.
- **Il pannello sta accanto, mai sopra** (`BesideAnchor`): sotto la riga se ci sta o se sotto c'è più spazio che sopra, altrimenti sopra;
  centrato sull'ancora e dentro i margini.
- **Il colore della cosa che ha alzato il menu** (`tint`, che `FluidVividCard` passa da sola) va sulla riga sollevata, **non** sul pannello:
  il menu è chrome, e la chrome non prende il colore di ciò che l'ha chiamata (una lista di azioni tinta come la cosa da cui
  nasce diventa il secondo oggetto colorato della scena).
- L'ancora usa `boundsInRoot` (ritagliati dagli antenati), mentre i tasti `Expand` usano i limiti non ritagliati: una riga mezza fuori
  dal bordo di una lista alza la parte visibile.
- **Il dito**: il relè è in testa all'ancora e consuma già nell'Initial: la `LazyColumn` attorno resta ferma mentre il dito scorre
  sul menu (provato con una riga in lista).

**Trappole.**

- *Il menu non parte mai da una riga cliccabile*: hai usato `Modifier.fluidContextMenu` su un elemento che consuma i click.
- *Non si apre e non vibra*: manca `FluidGlassModalHost` (`rememberFluidContextMenu` prende `LocalFluidGlassModalHostState`).
- *L'ancora esce dalla composizione a dito giù* (una riga di `LazyColumn` rimossa): il gesto si annulla, il menu **resta aperto** e
  nessuno sceglie.
- *Un `GraphicsLayer` rilasciato*: il pannello lo ridisegna dentro un `runCatching`; se la riga è sparita il pannello si apre
  comunque, senza immagine e con le sue azioni.

**Versione.** Controller e ancora dalla 1.5.0 (`tint` dalla 1.12.0); il dito, il seme per geometria, la goccia e il viaggio dalla 3.0.

## `FluidGlassMenuButton` (deprecato)

Il tasto che fa comparire il pannello *sopra* il tasto (due superfici). Dalla 3.0 passa comunque dal pannello, dal viaggio e dal dito dei
menu nuovi, ma ha un'apertura sua e il suo `entry`. `shape` e `contentDescription` non hanno corrispondente nel nuovo (la superficie che viaggia
parte sempre da una capsula; l'etichetta del tasto è già quella che si legge). Migrazione, dal KDoc: alla radice, accanto a
`FluidGlassModalHost`, `rememberFluidMorphMenuState()` + `FluidMorphMenuHost(state, backdrop)` sopra la tab bar; dove c'era
`FluidGlassMenuButton(actions, onClick, text, icon)`, ora `FluidMorphMenuButton(state, actions, onClick, text, icon, backdrop)`. Le azioni di
barra con `FluidBarAction(actions)` **non passano di qui** e non cambiano.

## Come funziona dentro

### Il viaggio (`FluidMenuJourney`)

Un progresso geometrico per i due versi (0 = l'ancora, 1 = il pannello, può superare 1) che guida la finestra di Fluid-physics con
`driveExternally`. Tempi (3.0.4, in mano erano «si chiudono alla velocità giusta e si aprono lenti»):

| cosa | valore |
|---|---|
| apertura della goccia | molla `FluidMotion.dropOpen()`: smorzamento 0,72, rigidezza 500 (`DropOpenStiffness`), risposta **~0,28 s**; era ~0,42 s (k 224) fino alla 3.0.3 |
| ritorno | `FluidMotion.dropClose()`: stesso smorzamento, rigidezza 158 (`DropCloseStiffness`), **~0,50 s**, un filo più lento dell'andata («troppo veloce») |
| misurato sull'emulatore, 30 fps, «Ordina» | ~267 ms di moto in apertura (come la chiusura, 267–333 ms); voci leggibili a ~170 ms dal primo fotogramma |
| le voci arrivano | fra 0,4 e 0,75 del viaggio (`LiquidContentOpenFrom/To`; erano 0,55–0,9), sempre ritagliate dentro il corpo |
| le voci se ne vanno (per prime) | da 0,94 a 0,62 (`LiquidContentCloseFrom/To`): prima restavano leggibili mentre il pannello si ritirava e le scritte galleggiavano sulla pagina |
| la faccia del tasto | piena alla partenza, spenta entro 0,35 (`MenuFaceTo`): faccia e voci non sono mai leggibili insieme |
| il materiale | smoothstep fino al 60% (`opticsFrom`/`tintFrom` → pannello): niente scalino di luminosità quando il viaggio si posa |
| scala del contenuto | `0,86 + 0,14·opacità` (`LiquidContentMinScale`): un menu che si avvicina, non un menu in miniatura |
| oltrepasso | gonfiore uniforme attorno al centro del pannello, 0,55 per unità (`DropInflationGain`), tenuto a 6 dp dai fianchi (mai il lerp oltre 1) |
| scrim del layer | entra in 110 ms, esce in 280 ms (`MenuFadeOutMillis`) |
| movimento ridotto | `snapTo`: arriva senza viaggiare |

**La partenza è il tasto *premuto*,** non il tasto a riposo (`fluidPressedBounds`): la sagoma che il dito sta gonfiando; partire dal
rettangolo a riposo voleva dire un primo fotogramma più piccolo del tasto che il dito stava ancora gonfiando, un sussulto all'indietro
proprio dove l'occhio guarda. Mentre il viaggio corre la pressione si scioglie verso il riposo.

**Un congedo a metà apertura torna indietro da dove si trova**, con la sua velocità (`retarget` la legge *prima* che la molla vecchia
venga cancellata: un `Animatable` cancellato azzera la sua velocità). Prima della 3.0 un viaggio cancellato saltava al traguardo per un
fotogramma, e il menu chiuso a metà apertura compariva come pannello pieno: oggi vale la regola «un viaggio che si cancella si ferma dove
si trova» (`regole.md`).

**KDoc fuori fase**: `FluidMenuJourney` dice che l'apertura è `FluidMotion.menuOpen`; il codice (`run`) usa `dropOpen`/`dropClose`.
`menuOpen`/`menuClose` esistono ancora e sono i token della molla di serie di `FluidPhysicsJourney`.

### Il pannello (`FluidGlassMenuPanel`)

UNO per tutti, con le voci, i separatori e **una** goccia. Il pannello non è il vetro: il vetro è la superficie di Fluid-physics che l'host fa
viaggiare; qui c'è quello che sta sopra, in due fratelli — la goccia (che campiona la pagina con dentro il materiale del pannello:
vetro su vetro) e la colonna delle voci.

| misura | valore |
|---|---|
| altezza di una riga | `max(48 dp, lineHeight + 24 dp)`: segue il font (al testo grande la riga cresce invece di tagliare) |
| padding del pannello / banda di sezione | 6 dp / 8 dp |
| larghezza | 252 dp, o quanto serve alla voce più lunga (etichetta + icona 20 dp + spunta 20 dp + 12 dp fra i segni + 16 dp per lato) fino a **340 dp**, mai oltre lo schermo meno 16 dp per lato; oltre, i puntini (3.0.1) |
| distanza dall'ancora / margine | 12 dp / 16 dp |
| raggio | `FluidRadius.Group` (22 dp) |
| separatori | linea da 0,5 dp al 12% di `onSurface`, rientrata di 16 dp; fra due sezioni una **banda** (5% sul chiaro, 8% sullo scuro) |
| materiale | `GlassDefaults.panelOptics(compact = true)` (lente 7/9 dp, non 14/18: una lente assoluta su un menu di due righe sarebbe un terzo del pannello e si vedrebbero due versioni dello stesso oggetto) + `panelTint` |

I separatori sono **disegnati, non scatole**: una scatola da mezzo dp aggiungerebbe altezza che l'aritmetica delle celle non conta, e il
dito sceglierebbe la riga sopra quella che vede. Le **celle** (la geometria del hit-test) sono solo le voci: padding, banda e separatori non sono
mai sotto il dito; sono semiaperte (a un confine vince la riga sotto).

**La goccia** è la ricetta di `glassDrop` coi numeri del pomello dello slider: bianca a riposo, lente 10/14 dp sotto il dito, capsula
rientrata 4×2 dp dalla cella, ombra fissa 4 dp al 5%, ombra interna solo premuta, crescita 4% (`FluidMenuDropPressedScale`). **Nasce sotto
il dito** e non ci scivola da un'altra voce, segue la voce attraversata, e al rilascio si posa **bianca** per un attimo (al più 140 ms) prima di
andarsene: è la conferma della scelta. La voce sotto la goccia si fa avanti del 5% dal bordo d'attacco, e i due separatori accanto si spengono
(la goccia è la voce, e una riga che la taglia la fa sembrare due). Ha tutte le sue trasformazioni — posto, scala del viaggio, schiacciamento — nel proprio
`layerBlock`, mai dentro il layer delle voci: un vetro scalato da un antenato rifrange quello che non c'è più. Presenza: entra in 110 ms, esce in 160.
Senza fondale (API < 31) non c'è goccia: restano la scala della voce e i separatori che si spengono.

### La selezione col dito (`FluidMenuSelection` e il relè)

Interni, ma sono la regola 3.0: un proprietario del dito alla volta.

| chi ha il dito | come | cosa succede al rilascio |
|---|---|---|
| **HandOff**: il dito che ha aperto | l'ancora glielo passa (`FluidMenuHandOff.attach`); il pannello è comparso a dito giù e non ha mai visto quel down | sceglie **solo se «armato»**: il dito si è mosso oltre lo slop (`ViewConfiguration.touchSlop`) dal punto in cui il menu l'ha preso, e **resta armato** anche se torna dov'era |
| **Panel**: un dito sceso sul pannello | il pannello consuma il down nell'Initial | sceglie se si solleva su una voce abilitata |
| nessuno | | il menu resta com'è |

Un rilascio **fuori dalle voci, nel padding, su un separatore o su una voce spenta** lascia il menu aperto. Un rilascio veloce, lontano
dall'ultimo movimento, conta come mosso (si arma anche al sollevamento). Un puntatore che sparisce dall'evento senza un sollevamento è un
annullo: il menu resta com'è e nessuno sceglie. Un secondo proprietario non scavalca né annulla il primo. Se il dito si muove prima che il
pannello abbia scritto la sua geometria, l'ultimo `hover` resta in attesa e viene rigiocato quando la geometria arriva; un rilascio senza geometria
lascia aperto.

**Il relè** (`Modifier.fluidMenuPointerRelay`, in testa all'ancora) esiste perché Compose sceglie i nodi di un puntatore *al down*: un
pannello che compare a dito giù non vede mai quel dito, il percorso dell'ancora sì. Legge **ignorando il consumato** (un discendente che ha
già preso il gesto — il pulsante, `clickable` — non deve privare il menu del dito), consuma i cambi di quel dito **già nel passaggio Initial**
(i tasti tengono il dito ma leggono nel Main: senza un consumo già nell'Initial il tasto nascosto continuava a seguire il dito sul menu e a
suonare la sua grana sotto i tick delle voci) e consuma anche il sollevamento che chiude l'hand-off, o `clickable` lo leggerebbe come
un click sull'ancora.

### L'aptica

Un momento, un colpo: `Open` all'apertura (**uno solo, e solo se il menu si apre**: lo suona il gesto che apre, mai il layer), `Tick` per ogni
voce **abilitata** attraversata (mai sul down di un tocco nel pannello, mai su una voce spenta, mai uscendo da tutte, mai restando sulla
stessa), `Tap` alla scelta, **niente al congedo**. Sui tasti alla LibrePods resta il `PressDown` al contatto: è del tasto, non del menu.
Prima una riga con menu suonava `GestureStart`, `Open` e il `LongPress` di piattaforma: tre colpi per un momento.

### Accessibilità

Ogni voce è un `clickable(role = Button)` per TalkBack e tastiera (dal dito il click non scatta mai: il pannello consuma il down prima, e la
scelta la fa la selezione, **una volta sola**); la voce `selected` dice «selezionato»; la voce spenta lo dice. Il pannello è un
`isTraversalGroup` con `paneTitle` = il tuo, o `LocalFluidLabels.current.actions` («Azioni»). Il fuoco entra nel layer (bersaglio da
1 dp). Con il movimento ridotto il viaggio e la goccia sono `snapTo`.

### Costi

A riposo niente: il host del tasto che si trasforma è un nodo vuoto che tiene le misure, quello dei modali non compone voci
chiuse, l'ancora del contestuale non registra nulla senza un dito. In viaggio: una superficie di Fluid-physics (contorno di 48 punti
integrati a 1/240 s, un fotogramma più lungo di 1/30 s è uno stallo e non si integra) più la goccia; senza fondale la superficie è
`FluidPhysicsTier.Lite`. Lo scrim dei menu dell'host dei modali è la superficie più grande del sistema: la sua forza è un alpha sul pannello
finito, mai l'`intensity` del materiale (vedi [menu-modali.md](menu-modali.md)).

## Token, costanti, Defaults

Quasi tutto è interno: **non c'è un `object …Defaults`** per i menu. Le costanti che un'app può leggere sono i token di `FluidMotion` (se le
legge si lega a numeri che cambiano: vedi la nota del KDoc di `FluidPhysicsDropTuning`, «solo per la galleria»).

| token | valore |
|---|---|
| `FluidMotion.DropOpenStiffness` / `DropCloseStiffness` / `DropJourneyDamping` | 500 / 158 / 0,72 |
| `FluidMotion.LiquidContentOpenFrom/To`, `LiquidContentCloseFrom/To` | 0,4–0,75 / 0,94–0,62 |
| `FluidMotion.LiquidContentMinScale` | 0,86 |
| `FluidMotion.DropInflationGain`, `DropPeakOvershoot` | 0,55, 0,0384 |
| `FluidMotion.MenuFaceTo`, `MenuFadeOutMillis` | 0,35, 280 ms |
| `FluidMotion.DropPoints`, `DropStiffnessNear/Far`, `DropDamping`, `DropEdgeTensionDefault` | 48, 1500 / 320, 0,72, 0,2 (la goccia della sagoma: di Fluid-physics) |
| `FluidMotion.LiftedSwellX` / `LiftedSwellY` | 0,25 / 0,6 (il gonfiore della riga sollevata all'oltrepasso) |
| `FluidMotion.MenuOpenDamping`, `MenuOpenStiffness`, `menuOpen()`, `menuClose()` | 0,62, risposta 0,36 s: **non** la molla dei menu di oggi (vedi sopra) |
| `FluidMotion.MenuContentOpenFrom/To`, `MenuContentCloseFrom/To` | `@Deprecated`: usa i `LiquidContent…` |

## Cosa è verificato dai test

Puri, in `engine-ui` (JVM):

- `FluidMenuSelectionTest`: il dito che decide, lo slop, l'armo che resta, la voce spenta, il rilascio che lascia aperto, l'hover che arriva prima della
  geometria, un proprietario alla volta.
- `FluidMenuGeometryTest`: il hit-test (padding e separatore non sono celle, i confini vanno alla riga sotto), il centro della goccia fra le celle.
- `FluidMenuRelayTest`: il consumo già nell'Initial, il click lungo che scatta durante un evento, il sollevamento consumato, il puntatore che sparisce = annullo.
- `FluidMenuPanelTest`, `FluidMenuPanelWidthTest`: la riga da 48 dp che segue il font, le sezioni, il pannello sopra/sotto/sull'ancora/accanto, il seme, la larghezza
  (252 → 340, mai oltre lo schermo).
- `FluidMenuJourneyTest` (orologio finto): l'apertura oltrepassa e si posa, la partenza dal tasto premuto, il gonfiore, **l'inversione a 100 ms**, riaprire mentre si chiude.
- `FluidMenuMotionTest`: le finestre di opacità e il loro verso, la mappa del contenuto, il gonfiore, i token.

Nel `sample` (Robolectric, aptica registrata):

- `FluidMenuHandOffTest`: tieni-trascina-rilascia dà `Open` `Tick` `Tap` e nessuna grana; rilascio fermo, fuori dalle voci o su una voce spenta lascia aperto;
  una riga in `LazyColumn` resta ferma; `fluidContextMenu`; il back a metà apertura chiude in silenzio; l'azione della barra.
- `FluidApiAdditionsTest`: `selected` è «selezionato» per TalkBack (e le altre no), `opensOnTap` apre, una voce lunga si legge intera, il tasto prende lo stato dal
  fornitore. `FluidButtonDragTest`: il tocco sul tasto che si trasforma fa i tre colpi di un tasto e non apre.

**Non è provato**: il vetro e la goccia sul vetro (gli host dei test non hanno fondale), il cambio di proprietario fra tasto e finestra, nessun fotogramma;
`FluidBarAction(opensOn = Tap)` ha solo la demo. Il KDoc delle schede vecchie (`regole.md`) dice «`opensOn` e `opensOnTap` hanno solo la demo»: `opensOnTap` è provato da
`FluidApiAdditionsTest`.

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/…`) | cosa c'è dentro |
|---|---|
| `fluid/FluidMenu.kt` | `FluidMenuOpensOn`; il modello puro della selezione: `FluidMenuSelection`, `FluidMenuHandOff`, il relè (`FluidMenuRelayTracker`) |
| `fluid/FluidMenuJourney.kt` | le funzioni pure del viaggio (finestre di opacità, mappa del contenuto, gonfiore, posto del pannello, seme, goccia del contestuale), `FluidMenuAnchor`, `FluidMenuJourney` |
| `fluid/FluidMenuLayer.kt` | il layer dell'host per i menu `Expand` e contestuale (`FluidMenuLayer`), e per l'avviso |
| `fluid/FluidGlassMenuPanel.kt` | il pannello, le voci, i separatori, la goccia, l'area dei gesti |
| `fluid/FluidGlassModal.kt` | `FluidContextAction`, `rememberFluidContextMenu`, `fluidContextMenuAnchor`, `fluidContextMenu`, la riga sollevata (`FluidLiftedPreview`) |
| `fluid/FluidScreen.kt` | `FluidBarAction(actions, opensOn)` |
| `fluid/FluidGlassMenuButton.kt` | il tasto deprecato |
| `fluidphysics/FluidMorphMenu.kt` | `FluidMorphMenuState`, `FluidMorphMenuButton`, `FluidMorphMenuHost`, `LocalFluidMorphMenuState` |
| `fluid/FluidMotion.kt` | i token (sezioni «Menus (3.0)» e «La goccia») |

Sample: `catalog/MenusDemo.kt` (i quattro ingressi e l'avviso, con un palco per il tasto che si trasforma), raggiungibile dalla galleria in Chrome ›
Catalogo della 3.0 › «Menù liquidi».
