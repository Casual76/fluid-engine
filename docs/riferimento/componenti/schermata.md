<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/schermata.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Schermata e scheletro

Questa famiglia e' lo scheletro di ogni pagina dell'app: la schermata che scorre (`FluidScreen`), il titolo
grande che si ritira in una barra di vetro, il bordo elastico e il «tira per aggiornare», il fondale colorato
(`FluidAmbient`) che il vetro ha da rifrangere, e tutto cio' che serve quando la finestra e' larga: colonne,
larghezza di lettura, lista+dettaglio, pannelli e barra laterale. In fondo ci sono i due strumenti per le liste
lunghe: lo scorrimento rapido e l'indice di sezione.

**Modello mentale.** Una schermata e' *una `LazyColumn` a tutto schermo*: il titolo grande e' il primo item
della lista (contenuto, non chrome) e la barra di vetro compare sopra solo quando qualcosa passa sotto la barra
di stato. Da qui discende quasi tutto: niente anello di retroazione fra altezza della barra e padding, «torna in
cima» arriva davvero in cima, e il contenuto *deve* essere un `LazyListScope` (la regola «una lista dentro una
lista non scorre»). Sullo schermo largo la schermata non sa di essere su un tablet: le dice la larghezza un solo
numero (il margine laterale), e i pannelli stanno *sopra un fondale solo*.

Le barre di navigazione e di azione (tab bar, capsula, ricerca) sono in [barre.md](barre.md); la barra di pagina
ancorata all'host delle pagine (`FluidPageChrome` e relative) e' in [pagine.md](pagine.md) e
[pagine-back-e-barra.md](pagine-back-e-barra.md): qui si cita soltanto il punto di contatto. Il materiale e i modali: [../regole.md](../regole.md), [../design-system.md](../design-system.md).
Catalogo completo delle firme: [../api/schermata.md](../api/schermata.md).

## Chi mette cosa: inset, padding, fondale (regola trasversale)

Ogni errore di layout di questa famiglia e' un'attribuzione sbagliata fra app, shell e schermata.

| spazio | lo mette | come arriva ai contenuti |
|---|---|---|
| sopra: barra di stato + riga dei controlli (44 dp) | `FluidScreen` | `contentPadding(top = topBarHeight)`; `FluidScreenDefaults.topBarHeight()` se serve saperlo |
| sotto: barra di sistema | `FluidScreen` | `WindowInsets.navigationBars` dentro `bottomContentPadding` |
| sotto: la tab bar flottante | la **shell**, una volta | `ProvideFluidChrome(bottomInset = FluidFloatingTabBarDefaults.ContentInset)` → `LocalFluidBottomInset`, letto da ogni `FluidScreen` |
| sotto: una banda sopra la barra, o una capsula | la shell | `FluidFloatingTabBarDefaults.contentInsetWithAccessory(h)` / `FluidFloatingCapsuleDefaults.contentInset(placement, h)` come `bottomInset` (sono insiemi **comprensivi della barra**, non da sommare a `ContentInset`) |
| sotto: aria dopo l'ultima riga | `FluidScreen` | `ContentBottomSpacing` (28 dp) |
| sotto: altro che solo questa pagina sa | la pagina | `extraBottomPadding` |
| ai lati | `FluidScreen` | `fluidScreenPadding(...)`: 20 dp o, su schermo largo, quanto serve a tenere la colonna a `contentMaxWidth` |
| margini e `navigationBarsPadding()` **della barra** | chi posa la barra | mai la barra stessa (vedi [barre.md](barre.md)) |

`bottomContentPadding(extra) = barra di sistema + LocalFluidBottomInset + extra + 28 dp`. La barra di sistema c'e'
gia': non passarla come `extraBottomPadding` (la galleria di esempio lo fa, ed e' un doppione innocuo ma inutile).

**Il fondale e chi lo dipinge.** Una `FluidScreen` e' opaca (`colorScheme.background`) e registra due cose: il
proprio corpo (per il vetro della chrome) e, se ha `ambient`, un secondo layer col fondale. Se *qualcuno sopra di
lei* ha gia' messo un fondale (`FluidAmbientSurface`, quindi `LocalFluidCanvasBackdrop`), vince quello: la schermata
non dipinge il suo e non si rende opaca, perche' «una finestra, un fondale». E' cosi' che tre pannelli non
sembrano tre telefoni appoggiati con due cuciture.

**Una lista dentro una lista non scorre.** `FluidScreen` e' una `LazyColumn`: dentro, una seconda `LazyColumn` (o una
griglia pigra) con altezza non limitata fa sollevare un'eccezione a Compose, e con altezza limitata prende il gesto e la pagina resta ferma —
il titolo non si ritira, il bordo elastico non si sente. E' la trappola in cui si cade convertendo una schermata
esistente (`ADOZIONE.md`, fase 5). Si risolve emettendo gli elementi come item della lista della pagina:
`items(...)`, `fluidGridItems(...)` per le card in righe, `fluidColumns(...)` per sezioni brevi in colonne. Per una
pagina che *deve* tenere una griglia propria si usa il titolo che si ritira senza `FluidScreen` (sotto).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| una pagina che scorre col titolo grande e la barra di vetro | `FluidScreen(title) { item { … } }` (sotto `Liquid` il contenuto passa sotto la barra sciogliendosi nel colore del fondo: sezione «Il bordo dello scorrimento») |
| il bordo dello scorrimento su una barra mia (un overlay, una capsula, una barra in basso) | `FluidScrollEdge(backdrop, edge, height, exports = …)`, con il ripiego scelto da te dove `fluidScrollEdgeAvailable()` e' falso |
| una pagina di dettaglio (apri una riga, leggi, torna) | `FluidContainerScaffold(title, onBack, hero, secondary)` — o direttamente `FluidScreen(onBack = …)` |
| tira per aggiornare | `FluidScreen(isRefreshing, onRefresh)`; l'app deve far passare `isRefreshing` da `true` a `false` |
| aggiornare da tastiera (Ctrl+R / F5) | `FluidChromeController.refreshFront()` dal gestore dei tasti |
| titolo che si ritira + barra su una pagina che **non** puo' essere una `FluidScreen` (griglia propria, foto in testa) | `rememberFluidTitleCollapse` + `FluidCollapsingTitle` + `FluidCollapsingTopBar` + `fluidTitleCollapseOrigin` + `rememberFluidTitleSnapFling` |
| bordo elastico su una lista mia | `rememberFluidEdgeOverscroll` + `fluidOverscrollEdge` + `fluidOverscrollContent`, e `overscrollEffect = null` |
| una pagina che sulla tavoletta resta una colonna di lettura | niente: `contentMaxWidth` (760 dp) e' gia' il default di `FluidScreen` |
| lo stesso, ma per un elemento fuori da `FluidScreen` (pannello, overlay, corpo di una pagina intera) | `Modifier.fluidReadingWidth(max)` |
| sezioni brevi in 1–3 colonne secondo la larghezza | `rememberFluidScreenMetrics` → `FluidScreen(metrics = …)` + `fluidColumns` |
| card dello stesso genere in righe da N, restando pigre | `fluidGridItems(items, columns = metrics.columns(...), key)` |
| quante colonne per una `LazyVerticalGrid` di tessere | `fluidGridColumns(available, minItem)` |
| elenco a sinistra e cosa scelta a destra (tablet) | `FluidListDetailScaffold` (+ `FluidDetailContent`, `FluidDetailPlaceholder`) |
| barra laterale + un pannello, rail sul tablet verticale, un pannello sul telefono | `fluidPaneLayout` + `FluidPaneScaffold` + `FluidSidebar` |
| un fondale per tutta la finestra a piu' pannelli | `FluidAmbientSurface(ambient)` (lo fa gia' `FluidListDetailScaffold`) |
| un fondale per una schermata | `FluidScreen(ambient = FluidAmbient(tone, motif))` |
| salto fra sezioni (mesi, giorni, lettere) su una lista sezionata | `FluidSectionIndex` — **non** scorre, salta |
| lista lunga da scorrere trascinando una maniglia (con bolla «Marzo 2024») | `FluidFastScroller` — **non** salta fra sezioni, la posizione del dito e' la posizione della lista |
| un'azione nella barra in alto | `FluidBarAction(icon, contentDescription, onClick)` nello slot `actions` |
| «Non cosi'»: due `LazyColumn` annidate, una `Surface` attorno a una `FluidScreen`, un `Scaffold` con `bottomBar` per la tab bar | emettere item nella lista della pagina; lo `Scaffold` toglie spazio alla pagina mentre la tab bar deve galleggiare **sopra** (vedi `ADOZIONE.md`, fase 5) |

## Una schermata tipica, dalla shell al contenuto

La shell (una volta, alla radice) fornisce il registro della chrome, lo spazio della tab bar e il bus «torna in
cima»; la tab bar flottante legge il fondale della pagina davanti da `activeBackdrop`. Modello: `Gallery` in
`sample/.../GalleryActivity.kt`.

```kotlin
@Composable
fun Shell(content: @Composable () -> Unit) {
  val chrome = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val fallback = rememberGlassBackdrop()
  val backdrop = chrome.activeBackdrop.value ?: fallback
  val tabScroll = rememberFluidFloatingTabBarScrollConnection()

  Box(Modifier.fillMaxSize()) {
    ProvideFluidChrome(
      controller = chrome,
      bottomInset = FluidFloatingTabBarDefaults.ContentInset,
      scrollToTop = scrollToTop,
    ) {
      // La connessione sta su un antenato delle pagine: e' da qui che la barra sente scorrere.
      Box(Modifier.fillMaxSize().nestedScroll(tabScroll)) { content() }
    }
    Box(
      Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(
          horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
          vertical = FluidFloatingTabBarDefaults.BottomMargin,
        ),
    ) {
      FluidFloatingTabBar(
        items = tabs, selectedRoute = route, onSelect = onSelectTab,
        onReselect = { scrollToTop.request() },
        scrollConnection = tabScroll, backdrop = backdrop,
      )
    }
  }
}
```

La pagina:

```kotlin
@Composable
fun ElementiScreen(state: ElementiUiState, onOpen: (String) -> Unit, onBack: () -> Unit) {
  FluidScreen(
    title = "Elementi",
    subtitle = "Una riga che spiega la pagina",
    onBack = onBack,
    actions = { FluidBarAction(Icons.Rounded.Add, "Aggiungi", onClick = state.onAdd) },
    titleFacets = listOf("12 elementi", "Aggiornato ora"),   // ruotano nella barra a titolo agganciato
    isRefreshing = state.isRefreshing,
    onRefresh = state.onRefresh,
    ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Dots) },
  ) {
    item { FluidSectionHeader(title = "Recenti") }
    item {
      FluidListGroup {
        state.items.forEachIndexed { i, item ->
          if (i > 0) FluidListDivider()
          FluidListRow(title = item.title, onClick = { onOpen(item.id) })
        }
      }
    }
  }
}
```

(`FluidSectionHeader`, `FluidListGroup`, `FluidListRow`: [../design-system.md](../design-system.md).)

## FluidScreen

**Cos'e'.** Una schermata a tutto schermo «nella forma di quelle di Apple»: il contenuto corre per tutta l'altezza, il
titolo grande e' il primo item dello scroll, e una barra compatta di vetro si materializza sopra solo quando il
titolo e' finito sotto. Esiste con questa forma perche' un `LargeTopAppBar` cambia il padding riportato dallo
`Scaffold` mentre si chiude, e il contenuto trema sopra lo scorrimento.

**Firma essenziale.**

```kotlin
@Composable
fun FluidScreen(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  titleFacets: List<String> = emptyList(),
  listState: LazyListState = rememberLazyListState(),
  isRefreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding,     // minimo: puo' crescere
  contentMaxWidth: Dp = FluidScreenDefaults.ContentMaxWidth,         // 760 dp; Dp.Infinity = tutta
  itemSpacing: Dp = FluidScreenDefaults.ItemSpacing,
  extraBottomPadding: Dp = 0.dp,
  ambient: FluidAmbient? = null,
  overlay: @Composable BoxScope.(GlassBackdropState) -> Unit = {},
  metrics: FluidScreenMetrics? = null,
  content: LazyListScope.() -> Unit,
)
```

Tutti i parametri: [../api/schermata.md](../api/schermata.md).

**Comportamento.**

- *Il titolo.* Item 0 con chiave `"fluid:large-title"`, `heading()` per TalkBack, `displayLarge` su massimo due righe. Mentre
  si ritira **e' una sola copia**: quella della barra, trasformata sull'ancora del titolo grande (la copia nella
  lista fa solo posto e semantica). Il compatto e' il grande scalato di `0,62` (da 34 sp a circa 21), stessa
  famiglia e peso: due stili che si dissolvono l'uno nell'altro si leggevano come un titolo che «cambia font».
  L'ancora grande e' *calcolata* (padding + prima linea di base), non misurata: misurarla leggerebbe anche la
  traslazione dell'elastico e il titolo viaggerebbe al doppio della velocita' del suo contenuto. Solo l'ancora
  compatta e' misurata, da uno slot non trasformato, e convertita nelle coordinate **della schermata** (un rail a
  sinistra non la sposta piu').
- *Il morph e' saldato allo scroll* (progresso = scostamento/corsa, nessuna molla in mezzo) e **un fling finisce sempre in una delle
  due case**: `FluidTitleSnapFlingBehavior` continua dentro il fling, scatta al piu' vicino (soglia: meta' corsa)
  con la molla critica del chrome; un nuovo tocco la interrompe. Con animazioni ridotte scatta senza molla.
- *Il morph non c'e' quando*: animazioni ridotte, `fontScale >= 1,3`, titolo su piu' di una riga, o titolo compatto
  che esce dal suo slot (`hasVisualOverflow`). Allora le due copie si dissolvono con una finestra disgiunta
  (il grande sparisce fra il 60% e il 78% della corsa, il compatto entra fra l'82% e il 100%: mai entrambi leggibili);
  con animazioni ridotte il passaggio e' secco a progresso 1.
- *Il vetro compare prima del titolo agganciato*: appena qualcosa passa sotto l'orologio. Dalla 4.0, col materiale `Liquid`, il
  «vetro» della barra e' il **bordo dello scorrimento** (sezione sotto): una striscia in cui il contenuto si scioglie nel colore
  del fondo, che sostituisce la fascia, e lo stesso in fondo sopra la tab bar. Sotto Android 13 e con `Classic` resta la fascia
  di sempre. Intensita' = max di due rampe, 8 dp di zona morta + 64 dp di rampa dallo scostamento, e il progresso del titolo
  meno il suo 16% iniziale (`GlassCollapseDeadZone`); cosi' in cima la pagina resta pulita e la striscia non disegna ne' cattura.
- *`titleFacets`*: a titolo agganciato la barra mostra a rotazione, ogni 2,4 s, il nome della pagina e poi le
  faccette (un conteggio, uno stato); il nome e' sempre il primo e quello a cui si torna, e tornando in cima si
  azzera. A faccetta attiva il titolo esce verso l'alto e la faccetta entra dal basso (una ruota sola).
- *Tocco sulla riga dei controlli* (44 dp, sotto la barra di stato): torna in cima (`animateScrollToItem(0)`), con
  nome accessibile `FluidLabels.scrollToTop`. `FluidScrollToTopBus.request()` fa lo stesso su ogni schermata
  **davanti**: e' il «riseleziona la scheda» della tab bar.
- *Titoli e controlli*: a titolo agganciato il margine laterale e' `CompactTitleInset` (84 dp dalla 4.0, 76 prima: i tasti ora stanno a 16 dp dal bordo) o, se i controlli di
  un lato sono piu' larghi, la loro misura + 8 dp, **uguale sui due lati** perche' il titolo resta al centro (3.0.1:
  prima con tre azioni un titolo lungo correva sotto i tasti).
- *`contentMaxWidth`*: oltre quella larghezza il margine cresce e la colonna resta centrata, **titolo grande
  compreso** (e' un item). Non riguarda la barra agganciata e `overlay`, che appartengono allo schermo. Se la
  colonna risulta piu' larga di 760 dp (hai passato un `contentMaxWidth` piu' grande, p.es.
  `FluidColumnsDefaults.WideContentMaxWidth`) la schermata **spegne da se' il motivo** del fondale
  (`FluidAmbient.showMotif = false`): l'angolo e' occupato dalla fascia in cima e due figure accostate si leggevano
  come un fondale doppio. La lavata resta.
- *`overlay`*: chrome flottante che deve campionare la pagina **senza farne parte** (indici, scorrimento rapido,
  capsule, controlli contestuali). Riceve il backdrop del corpo. Il contenuto normale va in `content` e resta
  solido; un indice dentro `content` campionerebbe se stesso. Dentro l'overlay `LocalFluidPageBar` e' nullo.
- *Colore del contenuto*: la schermata provvede `LocalContentColor = onBackground`, barra compresa. Senza, fuori da una
  `Surface` il testo e' nero e su AMOLED il titolo grande e' nero su nero.
- *Qualita' del vetro*: provvede `LocalFluidGlassQuality`; il vetro si assottiglia mentre la pagina corre e torna intero
  quando si ferma (`fluidGlassQualityScrollConnection` sul corpo).
- *Registro della chrome*: ogni schermata si registra presso `LocalFluidChromeController` col proprio backdrop e dichiara
  se e' **davanti** (`LocalFluidRouteFront`). Durante un back predittivo restano composte tutte e due: la chrome
  rifrange la pagina che sta uscendo finche' il gesto non e' confermato. Senza host di rotte nessuno dice «dietro» e
  vale l'ultima registrata. Quando la lista torna in cima (solo se la schermata e' davanti) e quando una pagina arriva
  davvero davanti, la schermata chiama `revealBottomBar()` del controller: e' il meccanismo *storico* di
  traslazione della barra (sotto). La tab bar flottante si riapre con la **sua** regola, in [barre.md](barre.md).
- *Dentro un host di pagine* (`FluidPageHost`/`FluidNavHost`, 3.0) la barra **non si disegna qui**: la schermata ne registra la
  specifica e la disegna l'host, una per host, ferma sopra le pagine che si muovono; le ancore del titolo si misurano solo a pagina
  ferma e davanti. Vedi [pagine.md](pagine.md). Dentro un pannello o un foglio (cioe' dove `LocalFluidPageBar` e' nullo)
  disegna la sua come sempre; `FluidListDetailScaffold` mette quel nullo apposta sui due pannelli.
- *Con `metrics`* scrive in composizione `contentWidth` (colonna meno i margini) e `fontScale`: la lista che le legge
  ha le colonne giuste al primo fotogramma.

**Uso.** Vedi sopra «Una schermata tipica». Con indice o scorrimento rapido:

```kotlin
val listState = rememberLazyListState()
FluidScreen(
  title = "Archivio",
  listState = listState,
  overlay = { backdrop ->
    FluidFastScroller(
      state = listState,
      label = { index -> labelFor(index) },
      backdrop = backdrop,
      modifier = Modifier.padding(top = FluidScreenDefaults.topBarHeight(), bottom = 96.dp),
    )
  },
) {
  items(rows, key = { it.id }) { row -> RigaArchivio(row) }
}
```

**Trappole.**

- **Gli indici sono sfalsati di uno**: l'item 0 e' sempre il titolo. `FluidSectionAnchor.itemIndex`, `FluidFastScroller.label(index)` e
  `listState.firstVisibleItemIndex` contano anche lui; con `fluidColumns` a piu' colonne e `fluidGridItems` gli item
  diventano righe o blocchi, e gli indici non sono piu' quelli degli elementi.
- **Una lista dentro una lista** — sezione trasversale in cima.
- **`isRefreshing` deve davvero cambiare.** Il gesto chiama `onRefresh` e tiene aperti 56 dp; si richiudono quando
  `isRefreshing` torna `false` (un effetto agganciato a quella chiave). Se `onRefresh` non la porta a `true` e poi a `false`
  lo spazio resta aperto. Mentre `isRefreshing` e' `true` anche `refreshFront()` risponde `false`.
- **Animazioni ridotte spengono anche il tira-per-aggiornare col dito**: lo stato elastico non si apre affatto
  (nessun bordo che ceda), quindi la soglia non si raggiunge. `refreshFront()` resta. (Letto dal codice,
  `FluidEdgeOverscrollState.onPostScroll`; nessun test lo dice.)
- **Pagina piu' corta della vista**: il bordo *alto* cede sempre (e' da li' che si tira per aggiornare), quello
  *basso* no — una pagina corta non ha un fondo contro cui rimbalzare, e farlo spingeva il titolo oltre la barra di stato.
- **Non mettere un fondo, una `Surface` o un `Scaffold`** attorno: la schermata e' opaca da se' (a meno di un fondale
  ereditato) e dichiara il colore del contenuto.
- **`overlay` e non `content`** per cio' che campiona la pagina (`FluidFastScroller`, `FluidSectionIndex`, `FluidFloatingCapsule`).
- **`FluidBarAction(actions = …)` senza host dei modali** non apre niente: serve `LocalFluidGlassModalHostState`
  (l'host alla radice).
- **`horizontalPadding` e' un minimo**: la schermata lo allarga su schermo largo. Le ancore del titolo usano quello
  allargato; se ti costruisci la lista a mano (sotto) usa lo stesso numero.

**Non usarlo quando.** La pagina ha una griglia o una lista sua che non si puo' riscrivere a item, o e' un
visualizzatore a schermo intero (`PreviewScreen` resta su `Scaffold` in `ADOZIONE.md`): titolo che si ritira
fuori da `FluidScreen` qui sotto.

**Versione.** Dalle prime 1.x; `overlay` 1.3.0; `ambient` 1.5.0; `contentMaxWidth` 1.32.0 (760 dp); `metrics` e colonne 2.5.0; barra ancorata
all'host delle pagine 3.0.0; margine del titolo che segue i controlli 3.0.1.

## Il bordo dello scorrimento

**Cos'e'.** Sotto i tasti di una barra il contenuto che scorre non passa piu' dietro una fascia di vetro: **si scioglie nel
colore del fondo** della pagina, con una sfocatura che cresce verso il bordo dello schermo. E' `scrollEdgeEffectStyle` di
SwiftUI, nell'aspetto di iOS 26 (decisione del 2026-11-10: la fascia uniforme a taglio netto di iOS 27 non si fa). Nessuna
lastra, nessuna sagoma, nessuna lente, nessun filo: una striscia larga quanto lo schermo. Le app che usano `FluidScreen` non
scrivono niente: la barra in alto della schermata, quella che si comprime (`FluidCollapsingTopBar`), la barra ancorata
all'host delle pagine e `FluidPageChrome` lo disegnano da sole, e lo stesso fa `FluidScreen` in fondo, sopra la tab bar.

**Quando c'e'.** Solo dove `fluidScrollEdgeAvailable()` e' vero: Android 13 o piu' (AGSL), il materiale `Liquid` chiesto
(`LocalFluidGlassModel`) e un compilatore che accetta il programma della striscia **e** quelli delle lastre (un rifiuto del
corpo o della luce porta tutto il processo a `Classic`, e una striscia nuova sotto tasti di prima e' una combinazione che
nessuno ha scelto). Una scelta sola, letta nello stesso modo da tutte le barre (`fluidChromeScrollEdge()`), perche' fra una
barra e l'altra non stia mai un materiale diverso. E solo **quando serve**, come in iOS (il bordo c'e' dove un elemento
fissato copre il contenuto): in alto l'intensita' e' quella di sempre, il massimo di due rampe (8 dp di zona morta e 64 dp
di rampa dallo scostamento, e il progresso del titolo meno il suo 16% iniziale), in basso la stessa soglia e la stessa rampa
dallo scorrimento che resta verso la fine. In cima alla lista, o a fine lista in basso, l'intensita' e' zero e la striscia
**non disegna e non cattura**: una pagina ferma non paga niente.

**Com'e' fatto.** `FluidScrollEdge(backdrop, edge, height, …)` e' alto `height` (dal bordo dello schermo al bordo dei
controlli verso il contenuto: barra di stato piu' 44 dp in alto) piu' `FluidScrollEdgeDefaults.reach`, la coda in cui sfocatura
e velo finiscono: 61,6 dp sotto i tasti in alto (1,4 altezze di tasto, `H` = 44 dp) e 86,8 dp sopra la tab bar (1,4 volte i
suoi 62 dp). Chi lo posa gli lascia quello spazio: il riquadro della barra non sposta niente (titolo e tasti stanno nella
riga in cima, la lista ha il suo margine, e il riquadro non prende tocchi), ma impilato in una colonna spingerebbe giu' il
resto. Il profilo e' misurato su Apple in altezze di tasto dal bordo basso dei controlli: la sfocatura cresce verso il
bordo dello schermo e il velo verso il colore del fondo si spegne in 1,35 `H`, con la dissolvenza dell'alfa d'uscita fra 1,3 e 1,4 `H`. Si disegna
con **due passate** dello stesso programma (orizzontale, poi verticale con velo e dissolvenza) su una cattura a **piena
risoluzione**: la meta' resta una leva della fase di ottimizzazione, e alla densita' del tablet uscirebbe dalle tolleranze
(il 50% a 0,56 `H`). Il nucleo della sfocatura viene da Haze (Apache-2.0, `LICENSES/Haze.md`, con la sua voce in
`FluidEngineLicenses`). I tasti che stanno sopra rifrangono il contenuto **gia' sciolto**: la striscia lo pubblica con
`exports` e la barra lo combina con la pagina, come faceva con la fascia.

**Gli stili.** `FluidScrollEdgeStyle.Automatic` (di serie) e' il bordo morbido in alto e il velo senza sfocatura in basso; `Soft` e'
il bordo morbido anche in basso; `Hard` e' una fascia del colore del fondo che nasconde il contenuto, con un passaggio in
meno di 0,2 `H`, per le pagine con un'intestazione fissa sotto la barra (le colonne di una tabella); `Hidden` non disegna
niente, e il contenuto passa nitido sotto i tasti (`scrollEdgeEffectHidden`).

**Il velo: leggero e progressivo (scelta di Alessio).** Sul tablet il primo profilo, preso dai ritagli attorno ai tasti, era
«spintissimo»: il velo verso il bianco o il nero del tema arrivava fino al 70%, un tetto che non veniva da nessuna misura di
iOS 26. Misurato su Apple nella stessa foto che sale sotto una barra chiara, il velo e' del 32–39% dietro i tasti e cala
fino al 2% a un'altezza di tasto sotto. La scelta e': **tetto 0,36 in alto e in basso** e una **curva sola, dolce, su ogni
contenuto** (anche sul testo), liscia e con pendenza zero all'attacco, perche' su una tinta unita un velo che passa da niente
a tutto in pochi pixel si vede troppo. Nodi (distanza in `H` dal bordo basso dei tasti → velo): −0,65 → 0,36, −0,4 → 0,32,
0 → 0,26, +0,2 → 0,18, +0,4 → 0,10, +0,7 → 0,05, +1 → 0,02, +1,35 → 0, con un'interpolazione cubica monotona e una grana
di mezzo livello. Misurato sul tablet su una tinta unita: 34, 26, 18, 10, 5 e 2% a −0,5, 0, +0,2, +0,4, +0,7 e +1 `H`, senza
riga dove finisce (residuo sotto 0,3 livelli). Sopra un **contenuto scuro**, in tema chiaro, la striscia **scurisce** invece
di velare di bianco (come Apple, ×0,70–0,72 dietro i tasti). **Una decisione sola per tutta la barra** (4.0, F2.3c): la
barra e' un vetro che gira come un tasto, con la sonda della striscia (legge la pagina dietro la barra) e la legge del
giro dei tasti (le sue manopole: corpo in 300 ms, inchiostro che svanisce e risale). Dalla stessa lettura e nello stesso
istante la striscia scurisce col corpo del giro, il titolo e le azioni cambiano lato passando per il fotogramma trasparente
(col tema del lato: un'icona in `onSurface` segue anche lei), e i tasti della barra sono **ospitati**: niente sonda loro,
il lato e la misura dalla barra, il contenuto che svanisce con lei. Prima la striscia decideva da se' e scuriva in 800 ms,
il titolo cambiava a meta' della striscia e ogni tasto decideva con la sua sonda: sul tablet il titolo restava scuro su una
striscia gia' scura per circa 200 ms. Lo stesso vale per la barra ancorata fra due pagine. In tema scuro il velo va gia'
verso il fondo scuro, la sonda non si monta e i tasti girano da soli come prima. Dove la barra passa al lato scuro anche le
**icone della barra di stato** passano al chiaro (4.0, F2.3b; vedi [tema.md](tema.md)). Lo scorrimento della lista e' un
cambio dello sfondo che la sorgente annuncia da se' (vedi [vetro.md](vetro.md), «Lo scorrimento è un cambio dello
sfondo»): `FluidScreen` aggiunge solo lo scorrimento fatto dal codice. Per riguardare il profilo sul dispositivo ci sono le
manopole di debug `edgeveil`, `edgefade`, `edgecap` e `edgecurve` (`gallery.tune`); a 1, o senza, i numeri sono quelli di serie al bit.

**In basso.** Sotto la tab bar il contenuto **sfuma senza sfocarsi**, come iOS 26 sul telefono: `Automatic` in basso e'
`FadeOnly` (scelta di Alessio del 2026-11-12), cioe' il velo e la dissolvenza senza la sfocatura; `Soft` la porterebbe, `Hidden`
la spegne. In fondo conta il controllo piu' alto: la tab bar, che la schermata conosce da `LocalFluidBottomInset`, e, nella struttura, i
controlli che si registrano (`RegisterFluidScreenBottomEdge`, interno; oggi nessuno lo chiama: la capsula flottante lo fara' con
il seguito del bordo). Un controllo che arriva o se ne va accende e spegne il bordo con la molla con cui entra la capsula. Per iOS 26 l'unica fonte e' secondaria, e per iOS 27 nessuna: l'aspetto in basso e' da giudicare a occhio.
**Aperto**: oggi la tab bar rifrange la pagina cruda e non il contenuto sfumato (esportare vorrebbe dire dare alla chrome un
fondale combinato, e un export la sonda lo legge come una sorgente che non avvisa): se ne occupa il seguito del bordo
(F2.5b), con la capsula flottante che si registra in fondo e il bordo che segue la tab bar quando si piega.

**Il ripiego.** Dove il bordo non c'e' (sotto Android 13, con `FluidGlassModel.Classic`, con un compilatore che rifiuta) la
barra tiene la **fascia di vetro di sempre** (`FluidTopBarGlass`, `glassSurface(falloff = FadeDown)`, e la sua coda di 34 dp),
identica al pixel, e in basso non c'e' niente. `FluidScrollEdge` stesso, dove non puo' disegnare, **non disegna niente**: il
ripiego e' di chi lo posa.

**Deprecazioni.** `FluidScreenDefaults.ShieldDeadZone` e `ShieldRampDistance` (la barra non ha piu' uno «schermo» di vetro) sono
deprecati con un `ReplaceWith` verso `ScrollEdgeDeadZone` e `ScrollEdgeRampDistance`, **stesso valore** (8 e 64 dp): valgono
anche per la fascia di ripiego. `FluidScreenDefaults.GlassFadeTail` (34 dp) e' deprecato e resta solo per la fascia di
ripiego; sotto `Liquid` la barra lascia `FluidScrollEdgeDefaults.reach`. Il parametro `tint` di `FluidCollapsingTopBar` e' ignorato sotto `Liquid` (Kotlin non sa deprecare un parametro solo: lo dice il KDoc), perche'
la striscia non ha pellicola. Non e' ancora deprecato, perche' non e' ancora sostituito, `glassSurface(falloff = FadeDown)`.

**Se hai scritto a mano una fascia.** Un `glassSurface(falloff = FadeDown)` (o `GlassEdge`) di un'app **non cambia da solo**:
sotto il materiale nuovo resta la fascia di prima e quindi `Classic` (domanda 3 della scelta, in [vetro.md](vetro.md)).
Se vuole il bordo mette `FluidScrollEdge(backdrop, edge, height, exports = …)` con `style = Automatic`, lascia `height + reach`
di spazio e tiene la fascia come ripiego dove `fluidScrollEdgeAvailable()` e' falso; i tasti sopra la striscia campionano
`rememberCombinedGlassBackdrop(pagina, exports)`. Una app che misurava la barra con `GlassFadeTail` per il padding della lista
non deve cambiare niente: la lista ha il suo margine, e il riquadro piu' alto della barra non sposta nulla.

**Trappole.**

- **`intensity` a zero non disegna e non cattura**, nemmeno l'export: i tasti sopra non tengono una striscia vecchia, perche'
  l'effetto di quella cattura si rifa' appena l'intensita' cambia. Non usare `Modifier.alpha` per «spegnerla».
- **Il bordo non e' un vetro**: niente `GlassRole`, niente ottica, niente pellicola. Non ha senso metterci sopra un
  `glassSurface` perche' «faccia lo stesso effetto»: la sfocatura che cresce verso il bordo e' del programma.
- **Si giudica sul tablet a schermo intero**, non su un ritaglio attorno ai tasti: il velo pesante restava fuori dai ritagli
  del banco con cui il profilo era stato scelto la prima volta. La galleria ha la scena `scorrimento` della pagina «Adattivita'».

## FluidBarAction

**Cos'e'.** Un'azione della barra superiore: una *lente* sulla stessa registrazione della barra (nessun'altra
sfocatura a pagamento). Icona da 22 dp nel colore d'accento, bersaglio dei tasti di vetro, cresce sotto il dito
(`FluidPressFeedback.Expand`).

**Firma essenziale.**

```kotlin
FluidBarAction(
  icon = Icons.Rounded.MoreVert,
  contentDescription = "Altro",
  onClick = {},
  actions = { listOf(/* FluidContextAction */) },   // il menu che il tasto diventa
  opensOn = FluidMenuOpensOn.Tap,
)
```

**Comportamento.** Senza `actions` e' un tasto a un verbo. Con `actions` il **tenuto premuto** apre un menu *di cui il
tasto diventa il pannello* (stessa idea di `FluidGlassMenuButton`): il tasto si nasconde nello stesso fotogramma in
cui compare la finestra sulla sua sagoma, e il dito che ha aperto sceglie trascinando. `opensOn = Tap` fa aprire il
menu al tocco (e `onClick` non viene piu' chiamato): per l'azione il cui unico mestiere e' il menu («Ordina», tre
puntini); il tenuto premuto apre comunque e porta il dito sul menu. Apertura sincrona nel gesto, un solo `Open`
aptico. **Dalla 3.0** `opensOn`.

**Trappole.** Senza `LocalFluidGlassModalHostState` (host alla radice) il menu non si apre; con lista vuota nemmeno.
Il `contentDescription` e' anche il titolo del pannello per TalkBack: va dato.

## FluidChromeController, ProvideFluidChrome e i bus della shell

**Cos'e'.** Il registro che lega le pagine alla chrome che sta sopra l'host delle rotte (tab bar, rail, notifiche,
modali). Ogni `FluidScreen` possiede il proprio `GlassBackdropState` (un layer ha un solo scrittore: e' cio' che
rende sicuro il back predittivo con due pagine composte); la chrome rifrange **quello della pagina davanti**.

**Firma essenziale.**

```kotlin
val chrome = rememberFluidChromeController()
val scrollToTop = remember { FluidScrollToTopBus() }
ProvideFluidChrome(chrome, bottomInset = FluidFloatingTabBarDefaults.ContentInset, scrollToTop) { /* app */ }
val backdrop = chrome.activeBackdrop.value ?: fallback   // per la tab bar, i modali, le notifiche
```

**Comportamento.**

- **Lo scrittore di riserva** (4.0, F2.3c): `ProvideFluidChrome` posa le pagine in un `Box` (che passa i vincoli minimi) e
  registra le pagine dentro il `fallback` della shell quando la pagina davanti non lo registra da se' (non e' una
  `FluidScreen` e non ci posa `glassBackdropSource`). Cosi' la tab bar sopra una pagina qualunque rifrange cio' che ha
  dietro e gira. La chrome va **fuori** dal contenuto di `ProvideFluidChrome`, come nel modello sopra.
- `activeBackdrop`: l'**ultima registrazione fra quelle che si dichiarano davanti**; se nessuna lo dichiara,
  l'ultima in assoluto (anteprime, galleria, una schermata sola in un'Activity). *Davanti non e' «registrata per ultima»*:
  per tutto un back predittivo le due pagine sono composte e eleggere la piu' recente faceva rifrangere alla
  pillola la pagina in arrivo dal primo fotogramma. Funzione pura verificata (`frontMostBackdropKey`, `FluidChromeMotionTest`).
- `refreshFront()` esegue l'`onRefresh` della pagina davanti (la stessa elezione); `false` se non ne ha o e' gia' in corso.
  Nel layout a due pannelli il dettaglio non si registra (`LocalFluidChromeController = null`), quindi si aggiorna
  l'elenco, cioe' la sezione. Legalo a Ctrl+R e F5 (dalla 2.6.0).
- `FluidScrollToTopBus.request()` + `LocalFluidScrollToTop`: «riseleziona la scheda attiva» = torna in cima. Rispondono
  solo le schermate davanti.
- `LocalFluidBottomInset` e `LocalFluidItemSpacing` (quest'ultimo pubblicato da `FluidScreen`: un helper che emette
  *piu'* item che devono leggersi come *uno* — `FluidGroupSegment` — deve sapere quanta aria richiudere).
- **Meccanismo storico**: `bottomBarOffsetPx`, `updateBottomBarTravel`, `onBottomBarScroll`, `settleBottomBar`,
  `rememberFluidChromeScrollConnection` traslano una barra in basso fuori schermo scorrendo (nasconde dopo 36 dp
  nello stesso verso, rivela dopo 10, soglia di velocita' 200 dp/s). La barra flottante **non** lo usa (ha la sua
  connessione, vedi [barre.md](barre.md)); `FluidScreen` si limita a chiamare `revealBottomBar()` quando la lista e'
  in cima e quando la pagina arriva davanti.

**Trappole.** Fuori dalla shell (`LocalFluidChromeController` nullo) la schermata funziona uguale ma non c'e' nessuno a
leggerne il fondale. `bottomInset` e' lo spazio **della barra**, non quello della barra di sistema.

## Titolo che si ritira fuori da FluidScreen

**Cos'e'.** Lo stesso titolo che sale in una barra di vetro, per una pagina che ha gia' la sua lista, la sua griglia e
la sua testata. Non c'e' una seconda implementazione: chiamano esattamente cio' che chiama `FluidScreen`.

**Firma essenziale.**

```kotlin
val collapse = rememberFluidTitleCollapse(title, listState)            // o (title, scroll: FluidCollapseScroll) per una griglia
Modifier.fluidTitleCollapseOrigin(collapse)                            // sulla radice della pagina
FluidCollapsingTitle(title, collapse, subtitle = null)                 // PRIMO item della lista
FluidCollapsingTopBar(title, collapse, backdrop, onBack = …, actions = …)  // ultimo nel Box, sopra la lista
rememberFluidTitleSnapFling(collapse)                                  // flingBehavior della lista
```

**Uso.**

```kotlin
@Composable
fun PaginaPropria(rows: List<String>, onBack: () -> Unit) {
  val listState = rememberLazyListState()
  val collapse = rememberFluidTitleCollapse(title = "Elementi", listState = listState)
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.fillMaxSize().fluidTitleCollapseOrigin(collapse)) {
    LazyColumn(
      state = listState,
      flingBehavior = rememberFluidTitleSnapFling(collapse),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = FluidScreenDefaults.topBarHeight()),
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(backdrop)                 // la registrazione NON contiene la barra
        .background(MaterialTheme.colorScheme.background),   // e' opaca dove lo e' la pagina
    ) {
      item { FluidCollapsingTitle(title = "Elementi", collapse = collapse) }
      items(rows) { Text(it) }
    }
    FluidCollapsingTopBar(title = "Elementi", collapse = collapse, backdrop = backdrop, onBack = onBack)
  }
}
```

**Comportamento.** `collapse.collapseProgress` (0 titolo a riposo, 1 agganciato) serve a chi ha qualcosa di proprio da
sfumare con il passaggio (chip, avatar, testata): **leggilo in un layer o in un blocco di disegno, mai in composizione**
(cambia a ogni fotogramma). `FluidCollapseScroll` astrae «quale item e' primo, quanto dentro, c'e' qualcosa, torna in
cima» perche' `LazyListState` e `LazyGridState` rispondono alle stesse quattro domande e non hanno un supertipo in
comune (`rememberFluidCollapseScroll(state)` per l'una o l'altra). Il tocco sulla barra torna in cima
(`onTapTitle` lo sostituisce). `barTint` per una barra su una foto a tutta pagina (`GlassDefaults.darkBarTint`,
perche' la pellicola chiara della barra sopra una foto e' sbagliata). `contentTranslation` e' l'offset dell'elastico se la
lista ne ha uno: il titolo agganciato e' saldato anche al suo rimbalzo.

**Trappole.**

- `FluidCollapsingTitle` **primo e senza niente sopra**: il progresso e' derivato dalla posizione misurata di quell'item.
- `horizontalPadding` e `topBarHeight` di `rememberFluidTitleCollapse` devono essere **quelli che la tua lista usa davvero**:
  l'ancora a riposo e' calcolata da essi, non misurata.
- Il backdrop **non deve contenere la barra** (la barra che sfoca la foto di se stessa) e deve essere opaco dove lo e' la pagina
  (sfocare trasparenza lascia un alone che l'originale nitido attraversa).
- Senza `rememberFluidTitleSnapFling` il titolo puo' fermarsi a meta' sopra la barra: lo stato piu' evidentemente non finito.
- Le intestazioni sono `heading()` per TalkBack dalla 3.0.4 (`FluidHeadingSemanticsTest`).

**Versione.** Dalla 1.15.0.

## Il bordo elastico e il tira-per-aggiornare

**Cos'e'.** `FluidEdgeOverscrollState` e' *un solo* bordo elastico per i due estremi e il gesto che aggiorna: e' stato
`NestedScrollConnection`. Sostituisce l'unione di `PullToRefreshBox` (spostava la pagina di dodici dp fissi, con
l'indicatore posato per conto suo: il bordo non cedeva e il cerchio «attraversava» il contenuto) e dell'effetto di
stiramento della piattaforma. Qui c'e' uno spostamento solo, la soglia e' un punto lungo di esso e l'indicatore vive
nello spazio che lo spostamento apre.

**Firma essenziale.**

```kotlin
val overscroll = rememberFluidEdgeOverscroll(canScroll = { listState.canScrollForward || listState.canScrollBackward })
LazyColumn(
  state = listState,
  overscrollEffect = null,                                     // o la piattaforma stira sotto di noi
  modifier = Modifier.fluidOverscrollEdge(overscroll),         // nestedScroll + misura della vista
)
Column(Modifier.fluidOverscrollContent(overscroll)) { … }      // SOLO cio' che deve muoversi
```

Per il tira-per-aggiornare fuori da `FluidScreen` si assegnano i campi pubblici dello stato: `refreshTriggerPx`
(0 = nessun gesto), `refreshHoldPx`, `onRefresh`; `refreshPull` (0..1) pilota l'indicatore e `endRefresh()` chiude lo
spazio a lavoro finito.

**Comportamento (numeri veri).**

- *Gomma di UIKit*: `f(x) = x·d·c / (d + c·x)` con `c = 0,55` (resistenza iniziale) e asintoto `d = 24%` dell'altezza della
  vista (`MaximumBounceFraction`): proporzionale alla superficie, mai un tetto in dp. Non c'e' un punto in cui il contenuto si
  ferma di botto.
- *Rilascio e fling*: il fling non e' tagliato ma **assorbito**: la velocita' residua (clampata a 5500 px/s) va a una molla
  criticamente smorzata (`FluidMotion.pixels(DampingChrome, ResponseStandard)`). Prima, il residuo accumulato fotogramma
  per fotogramma raggiungeva il tetto in due fotogrammi e leggeva come uno scatto.
- *Ritorno prima del movimento*: se il contenuto e' spostato, un dito in direzione opposta consuma prima lo spostamento e solo
  dopo scorre la lista: il contenuto non scorre mai mentre e' ancora fuori posto.
- *Soglia di refresh*: `RefreshTrigger` 76 dp di spostamento visivo; al rilascio oltre la soglia restano aperti
  `RefreshHold` 56 dp finche' dura il lavoro, e in quel tempo l'apertura non e' piu' dell'utente (la lista scorre
  *sotto* come sotto un inset). Con la pagina corta il bordo alto cede comunque, il basso no.
- *Solo `NestedScrollSource.UserInput`* viene intercettato; il resto passa.
- *Animazioni ridotte*: nessun elastico (vedi trappola in `FluidScreen`).

**Trappole.** `overscrollEffect = null` obbligatorio. `fluidOverscrollContent` **solo** sul contenuto che si muove e mai sullo
sfondo opaco: un fondo dentro il layer che trasla trascina una banda trasparente da sotto la barra, e la barra di
vetro sfoca un buco. `canScroll` e' catturata nel `remember` (chiave: movimento ridotto): passa una lambda che *legge stato*
a ogni chiamata, non un valore catturato.

**Versione.** Pubblico dalla 1.14.0 (`rememberFluidEdgeOverscroll` + i due modificatori); in `FluidScreen` c'era dall'inizio.

## Fondale: FluidAmbient, FluidAmbientCanvas, FluidAmbientSurface

**Cos'e'.** Il vetro sopra una pagina grigia e' invisibile per costruzione (rifrange una pagina piatta e ne ottiene una
piatta): il fondale e' cio' che si guarda *attraverso*. `FluidAmbient(tone, motif, intensity, urgent, showMotif)` e' una lavata
di colore piu' un motivo procedurale (anello dei toni e motivi di `FluidHero`: stessa mappa sezione→colore, quindi
testata e fondale non divergono), segue accento dinamico, AMOLED e Material You senza un asset.

**Firma essenziale.**

```kotlin
FluidAmbient(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Dots, intensity = 1f)
FluidScreen(ambient = …)                       // un fondale per questa schermata
FluidAmbientSurface(ambient) { /* pannelli */ } // uno per la finestra intera
```

**Comportamento.**

- **Opaco, sempre**: l'invariante che regge le transizioni di rotta (una pagina leggibile a ogni pixel di ogni fotogramma).
  La traslucenza del disegno e' *interna* alla pagina.
- `intensity = 0` e' *esattamente* il fondo piatto di prima (`FluidAmbientTest`); sotto 1 il motivo si diluisce col colore.
  `urgent` promuove ogni tono alla famiglia dell'errore.
- Disegno: il fondo, tre aloni radiali **fuori asse** (alto-centro `0,75` del contenitore del tono; alto-destra `0,17` dell'accento;
  sinistra a un terzo d'altezza `0,10`) — un gradiente verticale darebbe a ogni riga lo stesso colore — e il motivo
  320×300 dp in alto a destra, sfumato verso il basso con un `DstIn` in un layer offscreen. La lavata resta concentrata in
  alto: li' sta la chrome e *non* il grosso del testo lungo (oltre `0,17` l'accento in tema chiaro toglie contrasto).
- **Cosa compra a una `FluidScreen`**: il canvas viene dipinto e registrato **prima** del corpo, in un layer suo; il corpo smette
  di essere opaco; la chrome rifrange i due impilati (ancora un'immagine opaca). Frutto: `LocalFluidCanvasBackdrop` per
  tutto il sottoalbero, quindi una card *dentro la lista* puo' essere di vetro senza campionare una registrazione che la
  contiene. Senza `ambient` le superfici di contenuto restano piene.
- `FluidAmbientSurface` registra il canvas in un layer proprio e lo offre al sottoalbero: ogni `FluidScreen` dentro
  non dipinge il suo ne' si rende opaca. `ambient = null` = comportamento di prima (ognuna il suo).
- In una barra il canvas non c'e' (la barra mette `LocalFluidCanvasBackdrop = null`): chip, segmentato, campo di ricerca e tasti di
  vetro in barra trovano il vetro della barra (3.0.1).

**Trappole.** `showMotif` lo spegne `FluidScreen` da se' quando la colonna supera 760 dp. `FluidAmbientCanvas` da solo non registra
nulla: e' il disegno. Sotto API 31 (niente `RenderEffect`) il vetro ripiega e il fondale resta una lavata.

## FluidContainerScaffold

**Cos'e'.** La destinazione opaca e delimitata per «apri una riga → dettaglio»: una `FluidScreen` con un unico gruppo contenente
`hero` e `secondary` (due `ColumnScope`, 20 dp di padding, 14 dp fra i blocchi).

```kotlin
FluidContainerScaffold(
  title = "Elemento", onBack = onBack, ambient = ambienteDellaSezione,
  hero = { Text("…") }, secondary = { /* facoltativo */ },
)
```

**Comportamento.** Su un fondale ambientale e con `RenderEffect` il gruppo e' una **lastra di vetro** (la stessa di
`FluidListGroup(glass = true)`, una per il gruppo mai una per riga, il testo sopra resta opaco); altrimenti e' il contenitore pieno
`surfaceContainerHigh` con angolo `FluidRadius.Group`. `ambient` e' il fondale della sezione da cui si arriva: senza, il dettaglio era
l'unica pagina grigia in un'app dove ogni pagina ha la sua lavata. `onBack` puo' essere `null` per un dettaglio che sta in un
pannello. La pagina e' completa al primo fotogramma (un ritardo di 170 ms sul blocco secondario e' stato tolto: si vedeva solo come
contenuto che arriva dopo che la pagina si e' posata). **Versione:** `ambient` 2.5.3, `onBack` nullo 2.5.0, lastra di vetro 3.0.0.

## Schermi larghi

Quattro livelli indipendenti, dal piu' piccolo al piu' grande. Nessuno richiede gli altri.

| livello | cosa decide | strumento |
|---|---|---|
| margine laterale | quanto e' larga la colonna di lettura | `contentMaxWidth` / `fluidScreenPadding` / `fluidReadingWidth` |
| colonne dentro la pagina | quante sezioni/card affiancate | `FluidScreenMetrics`, `fluidColumns`, `fluidGridItems`, `FluidMasonry`, `fluidGridColumns` |
| elenco + cosa scelta | due pannelli dello stesso schermo | `FluidListDetailScaffold` |
| sezioni dell'app | barra laterale o rail + un pannello | `fluidPaneLayout`, `FluidPaneScaffold`, `FluidSidebar` |

Numeri di soglia (dp) in un colpo: pagina a colonne 340 (colonna minima) × 3, massimo 1120; elenco+dettaglio ≥ 840 (360 + 480);
pannelli: 600 rail, 1000 barra laterale.

### Larghezza di lettura

`fluidScreenPadding(available, horizontalPadding, contentMaxWidth) = max(horizontalPadding, (available - contentMaxWidth)/2)`, e
`horizontalPadding` se una misura non e' finita (meglio un fotogramma da telefono che un salto). E' **l'unica riga che
distingue una pagina da telefono allargata da una da tablet** (la schermata non sa di essere su un tablet). `ContentMaxWidth`
760 dp e' una colonna di lettura comoda a `bodyLarge` (oltre 80 caratteri si legge peggio e un tablet in orizzontale ne
fa duecento); `Dp.Infinity` spegne il centraggio per una pagina che vuole tutta la larghezza.

`Modifier.fluidReadingWidth(max = ContentMaxWidth)` fa lo stesso per **un elemento qualunque** fuori da `FluidScreen`: con `layout {}` e non
`widthIn`+`align`, cosi' funziona in qualunque genitore (anche uno che non sa allineare): l'elemento continua a occupare tutta la
larghezza che riceve e mette il contenuto nel mezzo. Senza un limite di larghezza dal genitore si misura come prima.

### Colonne dentro la pagina

```kotlin
val metrics = rememberFluidScreenMetrics()
FluidScreen(
  title = "Panoramica",
  metrics = metrics,
  contentMaxWidth = FluidColumnsDefaults.WideContentMaxWidth,
) {
  fluidColumns(
    key = "sezioni",
    columns = metrics.columns(),
    sections = listOf(
      FluidColumnSection(key = "a") { SezioneA() },
      FluidColumnSection(key = "b") { SezioneB() },
    ),
  )
  fluidGridItems(items = cards, columns = metrics.columns(minColumn = FluidColumnsDefaults.MinCard), key = { it.id }) { Card(it) }
}
```

- `FluidScreenMetrics` e' il ponte perche' il contenuto e' un `LazyListScope` (non componibile, non puo' chiedere quanto e' largo): la
  schermata ci scrive `contentWidth` (margini esclusi) e `fontScale`, la lista li legge **come stato** e si ricostruisce da sola
  a ogni rotazione o finestra divisa. Senza una schermata che lo aggiorna `contentWidth` e' `Dp.Unspecified` e ogni domanda sulle
  colonne risponde **una**: la pagina del telefono.
- `columns(minColumn = 340 dp, spacing = 20 dp, maxColumns = 3)` = `fluidContentColumns(contentWidth, minColumn * max(1, fontScale), …)`:
  `((available + spacing) / (minColumn + spacing))` troncato e limitato a 1..max. **La misura minima cresce col testo**: a 1,3 tre
  colonne diventerebbero tre colonne di sillabe, quindi su un tablet in orizzontale si scende a due (2.5.1). Mai zero; larghezza non
  nota o infinita: una.
- `fluidColumns`: con **una** colonna ogni sezione e' un item come se fosse scritta a mano (sul telefono non cambia niente, nemmeno la
  composizione anticipata); con piu' colonne le sezioni stanno in **un item solo** e si dispongono a **muratura** (`FluidMasonry`: ognuna
  nella colonna piu' corta, a parita' la prima, nell'ordine scritto, quindi la lettura resta sinistra→destra, alto→basso). Per sezioni
  *brevi*: un elenco lungo in colonne non e' piu' pigro. Una griglia a righe lascerebbe buchi grandi come la differenza di altezza.
- `fluidGridItems`: con una colonna e' `items(items, key)`; con piu' colonne **ogni riga e' un item**, quindi la lista resta pigra con
  ottanta card. L'ultima riga incompleta lascia vuoto il posto, non allarga le altre; le altezze di una riga **non** si pareggiano
  (una card con una nota in piu' e' piu' alta, e va bene). Chiave di riga: `"fluid:grid-row:" + key(primo)`.
- `FluidColumnsDefaults`: `MinColumn` 340, `Spacing` 20, `GridSpacing` 14 (come `ItemSpacing`: la griglia ha un passo solo),
  `MinCard` 300, `MaxColumns` 3 («oltre tre una pagina smette di essere pagina e diventa un pannello di controllo»),
  `WideContentMaxWidth` 1120 (la misura di una pagina che si dispone in colonne; 760 lascerebbe due strisce vuote grandi come un
  telefono).
- `fluidGridColumns(available, minItem = 150 dp, spacing = 12 dp, maxColumns = 5)` e' un'altra cosa: per una `LazyVerticalGrid` di
  **tessere**, il numero discende dalla misura della tessera, non il contrario (due colonne fisse fanno tessere da mezzo schermo su un
  tablet). Mai zero, mai oltre il massimo.

**Trappole.** `FluidScreenMetrics` ha costruttore interno: si ottiene con `rememberFluidScreenMetrics()`. `fluidColumns` con piu'
colonne fa un item solo: gli indici di `FluidFastScroller`/`FluidSectionIndex` non corrispondono piu' agli elementi.

### Elenco + dettaglio: FluidListDetailScaffold

**Cos'e'.** Una pagina che elenca e ne mostra una: sul telefono l'elenco e basta, sullo schermo largo l'elenco a sinistra e la cosa scelta accanto.

```kotlin
FluidListDetailScaffold(
  ambient = ambiente,
  list = { twoPane -> Elenco(selezionaInvecDiAprire = twoPane) },
  detail = {
    val scelto = selezione
    if (scelto == null) FluidDetailPlaceholder(title = "Scegli un elemento", message = "Il dettaglio comparira' qui")
    else FluidDetailContent(item = scelto, key = { it.id }, order = { elementi.indexOf(it) }) { FluidScreen(title = it.nome, onBack = null) { … } }
  },
)
```

**Comportamento.**

- `fluidListDetailLayout(available, sizes)`: **due pannelli solo se restano quello che sono** — elenco ≥ `listMin` 360 e dettaglio ≥ `detailMin` 480,
  cioe' `available >= 840`. L'elenco e' `listFraction` 0,4 della finestra fra `listMin` 360 e `listMax` 500, e mai oltre `available - detailMin`.
  Un tablet in verticale (800) **non** ci arriva e resta una pagina sola e piu' larga (meglio che due colonne strette da telefono);
  in orizzontale ci arriva sempre. Larghezza non finita: un pannello.
- Tre decisioni: **un fondale solo** (i pannelli stanno in una `FluidAmbientSurface`); **la chrome guarda l'elenco** (il dettaglio ha
  `LocalFluidChromeController = null`, quindi non vince l'elezione e la pillola non rifrange una pagina che non ha sotto); **il
  dettaglio non esiste finche' non serve** (su un pannello solo `detail` non e' composto: la cosa scelta si apre come prima, un pop-up
  dalla riga, una pagina). Il parametro di `list` dice in quale dei due mondi si e': una riga toccata *seleziona* o *apre*.
- Ognuno dei due pannelli ha **la sua barra** (`LocalFluidPageBar = null`): due `FluidScreen` affiancate che si registrassero entrambe
  presso l'host delle pagine ne mostrerebbero una sola, larga come la finestra. Il pannello dell'elenco riceve `FluidPaneRole.List`,
  l'altro `Detail` (`LocalFluidPaneRole`).
- **L'elenco e' spostato, non ricomposto** (`movableContentOf`) quando la finestra attraversa la soglia: scritto in due punti dell'albero,
  perdeva scorrimento e scheda scelta a ogni rotazione (2.5.3).
- `FluidDetailContent`: il cambio di cosa mostrata e' un movimento fra **pari** (come le schede): niente cresce, niente arretra. Quella
  nuova arriva di `FluidMotion.PeerSlideFraction` dalla parte in cui sta nell'elenco (`order`: piu' in basso da destra, piu' in alto da
  sinistra), quella vecchia se ne va dall'altra sfumando. La nuova **non sfuma mai** ma compare `110 ms` dopo (il tempo in cui la
  vecchia se ne va), perche' dentro un fondale condiviso nessuna pagina e' opaca e le due non devono leggersi una attraverso l'altra.
  `key` decide se parte il movimento (cambia l'identita') o la pagina si aggiorna dove sta (cambia solo il dato). Chi esce si tiene
  il *suo* contenuto. Con animazioni ridotte: cambio a durata zero.
- `FluidDetailPlaceholder`: non e' un errore e non deve sembrarlo — icona velata (55%), due righe, **niente titolo grande e niente barra**
  (una pagina vuota con la chrome completa sembra non aver finito di caricare). Largo al massimo 360 dp.

**Non usarlo quando.** Servono la barra laterale e/o il rail: `FluidPaneScaffold`.

### Pannelli, rail e barra laterale: fluidPaneLayout, FluidPaneScaffold, FluidSidebar

**Cos'e'.** Il livello adattivo dell'app: `fluidPaneLayout(available, sizes, hasSide, hasRail, showDetail)` e' una funzione **pura** che dice
quali pannelli stanno in scena e quanto larghi; `FluidPaneScaffold(layout, list, detail, side, rail)` li emette.

**Comportamento.**

| larghezza disponibile | cosa c'e' |
|---|---|
| `< 600` (`MediumBreakpoint`) o non finita | **un pannello** (`List`, o `Detail` se `showDetail`), niente rail: la barra in basso e' affare dell'app |
| `600 … < 1000` | un pannello + il rail (`rail` = `RailWidth` 84 + 2×`HorizontalMargin` 14 = 112) se `hasRail` |
| `>= 1000` (`ExpandedBreakpoint`) con `hasSide` | `Side` (280) + **un** pannello (`List` o `Detail`), niente rail; se `available - side < detailMin` (420) la barra laterale si sfila e torna il rail |
| `>= 1000` senza `hasSide` | un pannello + il rail |

**Due pannelli al massimo, e la barra laterale e' uno dei due**: accanto alla barra sta *o* l'elenco *o* quello che si e' aperto, mai
tutti e due (tre cose aperte insieme si leggono come tre pagine appiccicate, e con le impostazioni diventano tre livelli della stessa gerarchia
visibili in una volta). L'elenco+dettaglio **affiancati** sono `FluidListDetailScaffold`, non questo.

- `FluidPaneSizes(side 280, list 380, detailMin 420, rail 112)`: `list` serve solo a calcolare `splits`; le larghezze reali con la barra laterale
  sono `pane = available - side`. Il dettaglio non si comprime mai sotto `detailMin`: si sfila prima la barra, poi la lista.
- `FluidPaneLayout.splits` e' una **capacita'**, non cio' che si vede: «la finestra tiene l'elenco e la cosa aperta come due posti distinti»
  (`available - list >= detailMin`, solo da 1000 in su). Con `splits` il pannello fuori scena resta **composto e largo zero**: il suo `NavHost` conserva grafo e pila,
  ri-mostrarlo e' un cambio di misura; smontarlo darebbe un controller senza grafo e una rotta verso di lui che esplode. Senza `splits`
  aprire una nota la *impila* sopra l'elenco, come sul telefono.
- `FluidPaneScaffold` emette solo i pannelli di `layout.panes` (un pannello nascosto non e' largo zero, non c'e': la sua `FluidScreen` non registra un
  fondale che nessuno vede), e per ciascuno dichiara `LocalFluidPaneRole` e `LocalFluidRouteFront` (due pannelli sono davanti entrambi: senza dirlo
  la chrome eleggerebbe l'ultimo registrato in un momento qualsiasi; cosi' fra i davanti l'ultimo e' il dettaglio). I controller di navigazione stanno
  **fuori**, nella shell.
- `FluidSidebar(backdrop, header, footer) { LazyListScope }`: chrome, quindi **di vetro** (ruolo `Floating`, `GlassDefaults.floatingTint`), `Margin` 12 dp dai bordi
  e dentro gli inset di sistema; contenuto `LazyColumn` (cento voci scorrono, testa e piede stanno fermi). `FluidSidebarSection(title)` titola un gruppo (maiuscolo);
  `FluidSidebarRow(label, selected, onClick, icon, tint, detail, badge, contextActions)` e' una riga da `RowHeight` 44 dp con piastrella d'icona, ruolo `Tab` e
  `selected` per TalkBack. La selezione e' **un velo dell'accento al 14%**, non una pillola di vetro: dentro una barra gia' di vetro un secondo vetro non si vede.
  **Dalla 3.1.0 (Pampa Mail), tutto opt-in:** `FluidSidebarRow(label, selected, onClick, modifier, icon, tint, detail, badge, level = 0, leading = null, trailing = null,
  minHeight = FluidSidebarDefaults.RowHeight, contextActions = null)` è un **overload**: i parametri nuovi stanno prima di `contextActions`, che resta l'ultimo, quindi la lambda finale è il menu in
  tutte e due le firme, e la firma della 3.0 (`contextActions` nono) resta identica (con i valori di serie le due disegnano la stessa riga). `FluidSidebarSection(title, modifier, leading = null,
  detail = null, uppercase = true)` ha i parametri in coda (la sua ultima non era una lambda).
  - **`level`** annida: ogni livello sposta la riga di `FluidSidebarDefaults.IndentPerLevel` (16 dp) fino a `FluidSidebarDefaults.MaxIndentLevels` (6: oltre, le righe non si spostano più: una barra da 360 dp con una cartella a dieci
    livelli non deve lasciare al nome tre lettere). Per le cartelle dentro le cartelle. Il velo della selezione resta largo quanto la riga: è la riga a essere scelta, non il rientro. Negativo vale 0.
  - **`leading`** è un contenuto **al posto** della piastrella (un avatar, un pallino di colore, la freccia che apre un gruppo): se c'è, `icon` e `tint` non si disegnano. **`trailing`** è un contenuto **dopo** `detail` e `badge`
    (un `FluidBadge` coi non letti); non cambia la semantica della riga: **se porta un numero, la riga lo dice una volta sola** (nel nome, non anche nel badge).
  - **`minHeight`**: di serie 44 dp (`RowHeight`); una riga che è il bersaglio principale di una barra usata col dito passa `FluidSidebarDefaults.ComfortableRowHeight` (48 dp, QLT-21); col testo grande la riga cresce comunque.
    `RowHeight` resta a 44 perché cambiarla sposterebbe tutte le barre che esistono. Il pannello `FluidDrawer` ([`menu-modali.md`](menu-modali.md)) usa le stesse righe.
  - **Sezione**: `leading` (l'avatar o il colore di un account, quando il gruppo è «le cartelle di questo account»; non è toccabile: un'intestazione che si apre è una `FluidSidebarRow`), `detail` (una parola o un numero a destra:
    «12», l'indirizzo), `uppercase = false` quando il titolo è un nome proprio o un indirizzo, che le maiuscole deformano.

```kotlin
FluidSidebar(backdrop = backdrop, header = { Text("Menu") }) {
  item { FluidSidebarSection("Sezioni") }
  item { FluidSidebarRow(label = "Panoramica", selected = true, onClick = {}, icon = Icons.Rounded.Home, detail = "12") }
}
```

**Trappole.** `FluidPaneScaffold` da' ai pannelli `weight(1f)`: la misura reale di un pannello e' *il resto della riga*, e `listWidth`/`detailWidth`/`railWidth`
del layout servono a chi le legge (e a `splits`). `hasRail = true` senza passare `rail =` non disegna il rail. Il `backdrop` della barra laterale e' di norma quello
della chrome (`activeBackdrop`), non uno suo. Il controller di navigazione non vive dentro il pannello: un pannello smontato lo perderebbe.

## Liste lunghe: scorrimento rapido e indice di sezione

I due componenti stanno **sopra** la lista (nello slot `overlay` di `FluidScreen`, o in un `Box` che fa da genitore) e fanno cose diverse:

| | `FluidFastScroller` | `FluidSectionIndex` |
|---|---|---|
| mestiere | **scorre**: la posizione del dito e' la posizione della lista | **salta** fra sezioni semantiche |
| unita' | elementi (frazione continua) | ancore (`FluidSectionAnchor(key, label, itemIndex)`), al piu' 8 |
| chi sposta la lista | lui (`scrollToIndex`) | **l'app**, in `onSelectSection` |
| compare | mentre la lista scorre + 1,2 s, non prende tocchi finche' non si vede | sempre (barretta a riposo) |
| presa | al contatto con la maniglia | **tenendo premuto** (pressione lunga) |
| soglia lista | `minItems` 30 | 2 ancore |

### FluidFastScroller

**Cos'e'.** Una maniglia di vetro sul bordo di una lista lunga che la porta in un colpo dove si vuole, con una bolla che dice dov'e' si e' arrivati
(«Marzo 2024», «M»). Due app se l'erano scritto, e ognuna aveva l'altra meta': una la bolla e il vetro ma la posizione in frazioni da fornire,
l'altra contava le righe ma non diceva dove si era.

```kotlin
FluidFastScroller(
  state = listState,                           // LazyListState | LazyGridState | target
  label = { index -> etichettaDi(index) },     // null o vuota: niente bolla e niente colpo
  backdrop = backdrop,                         // solo se disegnato dall'overlay di FluidScreen
  modifier = Modifier.padding(vertical = 8.dp),
)
```

**Comportamento.**

- **Posizione in elementi, non in pixel**: una lista pigra conosce l'altezza solo di cio' che e' a schermo. Con righe quasi uguali sono indistinguibili; con righe
  diverse la maniglia e' approssimata, ma **il dito resta 1:1 con la maniglia** perche' durante il gesto la maniglia non legge la lista: si sposta di quanto si sposta il dito
  (`FluidFastScroll.scrubbedFraction`, **relativa**: non salta sotto il dito alla presa, e un dito che esce dalla pista e rientra non la lascia indietro).
  `fractionalFirstIndex` conta anche la parte gia' scorsa del primo elemento (3,25) cosi' la maniglia non fa un gradino per elemento alto.
- `FluidFastScrollTarget` e' l'interfaccia (totale, visibili, primo, primo frazionario, in-scorrimento, `scrollToIndex`) per una lista, una griglia o un contenitore tuo
  (`rememberFluidFastScrollTarget`, una per stato). **Tutto cio' che si legge deve essere stato di snapshot**: si legge nel disegno, a ogni fotogramma, senza ricomporre.
- Compare quando `isScrollInProgress` o si tiene la maniglia; sparisce dopo `FluidMotion.FastScrollerHideMillis` (1,2 s). **Non prende i tocchi finche' non e' visibile
  (> 0,3)**: un dito sul bordo di una lista senza maniglia scorre la lista. Il dito che prende la maniglia e' consumato: non va alla lista ne' al back della pagina.
- Maniglia = **goccia** (`glassDrop`): piena a riposo, lente e +15% sotto il dito; senza `backdrop` o sotto API 31 e' una pastiglia dipinta. Bolla = pannello compatto
  (`panelOptics(compact = true, overSharpPage = true)`), solo durante la trascinata. Misure: 28×52 dp, 4 dp dal bordo, 10 dp alla bolla.
- Le richieste di scorrimento passano da un canale **conflated**: una coroutine per evento le accodava e la lista arrivava dove il dito era mezzo secondo prima.
- **Una vibrazione per momento**: `GestureStart` alla presa, un `Tick` per ogni **etichetta** attraversata (non per pixel ne' per elemento: tre righe con la stessa etichetta fanno un colpo),
  niente al rilascio. Animazioni ridotte: niente dissolvenza.
- **TalkBack**: un cursore sempre presente (striscia di 24 dp, senza tocchi propri) con l'etichetta come stato e `setProgress`.
- Occupa tutto il genitore e **non prende tocchi** se non quelli della maniglia: si accorcia con `Modifier.padding` (sotto una barra, sopra una tab bar).

**Trappole.** `backdrop` solo dall'overlay: dentro il corpo registrato campionerebbe se stessa. In una `FluidScreen` gli indici partono da 1 (il titolo e' l'item 0): `label(0)` deve poter dare `null`.
Sotto 30 elementi, o se tutta la lista sta a schermo, non si vede: ed e' voluto.

### FluidSectionIndex

**Cos'e'.** Un indice trascinabile per salti semantici (giorni, mesi). A riposo e' **una barretta** (nessun pannello, nessuna pillola, niente che competa con la pagina); toccato si
**dispiega nella barra di scorrimento che e'**: le stazioni si aprono su una pista di vetro a tutta altezza, una lente viaggia sotto il dito e la bolla la segue.

```kotlin
val sezioni = listOf(FluidSectionAnchor("gen", "Gen", itemIndex = 1), FluidSectionAnchor("feb", "Feb", itemIndex = 9))
val attiva = activeFluidSectionForItemIndex(sezioni, listState.firstVisibleItemIndex)?.key
FluidSectionIndex(
  sections = sezioni,
  activeSectionKey = attiva,
  onSelectSection = { anchor, motion ->
    scope.launch {
      if (motion == FluidSectionSelectionMotion.Animated) listState.animateScrollToItem(anchor.itemIndex)
      else listState.scrollToItem(anchor.itemIndex)           // mentre il dito trascina: niente movimento in coda
    }
  },
  modifier = Modifier.align(Alignment.CenterEnd),
  backdrop = backdrop,
)
```

**Comportamento (e il perche').**

- **Si prende solo tenendo premuto** (pressione lunga, `longPressTimeoutMillis`, dito entro lo slop). Prima prendeva al tocco e doveva (lo scorrevole della lista rivendica alla *sua* soglia), ma
  un controllo che rivendica al tocco rivendica anche cio' che non era per lui: sul bordo destro, all'altezza del pollice, la lista saltava a una sezione a caso circa un gesto su cinque.
  Chi vuole scorrere si muove subito, chi vuole l'indice si ferma. Il nodo condivide i puntatori con i fratelli (`sharePointerInputWithSiblings`): la lista sotto vede lo stesso gesto.
- **Il bersaglio e' la barretta e viaggia con lei**: `MinTouchWidth` 28 dp × (`RestBarHeight` 50 + `HitMargin` 16), non 48×136 appoggiati al bordo: ogni dp di quel riquadro e' un dp in cui scorrere non funziona. Una striscia da 48 dp (`TouchWidth`) e' il genitore di layout.
- Il trascinamento e' **relativo** al punto della presa: si apre dove sei e la barretta a riposo e il nastro aperto non hanno la stessa altezza, quindi mappare la y assoluta farebbe saltare la pagina senza muovere un millimetro.
  Le stazioni esistono solo da aperto (a riposo e' un indicatore: otto tacche invitano gia' al tocco mentre si scorre). Il nastro aperto e' poco piu' di meta' schermo (`ExpandedTrackFraction` 0,55, 260–460 dp), non tutto: un nastro alto come il display costringe il pollice a percorrere l'altezza del telefono.
- Il campionamento: piu' di `maxVisibleAnchors` (8) ancore → `sampleFluidSectionAnchors` tiene prima e ultima, distribuisce le altre, e **tiene l'attiva** fra le visibili.
  Meno di 2 ancore (dopo il campionamento): niente indice.
- Ingresso e uscita per **offset e alfa**, mai una scala: un layer scalato sopra un vetro rifrange cio' che non c'e' piu' (il `scaleIn(0,92)` faceva «respirare» il fondale). In RTL verso il bordo opposto.
- Aptica: `GestureStart` alla pressione lunga, un `Tick` solo per una **tacca attraversata** (il primo evento a dito fermo non lo e': non suona un secondo colpo ne' riseleziona la sezione dov'era gia').
  `FluidSectionSelectionMotion.Animated` per la selezione da TalkBack/azioni, `Immediate` mentre il dito trascina.
- TalkBack: **un solo cursore** (`progressBarRangeInfo` con i passi, `stateDescription` «posizione», azioni «sezione precedente/successiva») invece di quattro puntini sovrapposti. Rispetta gli inset dei gesti di sistema a destra.
- `backdrop`: **solo dall'overlay di `FluidScreen`** (un indice dentro il corpo registrato campionerebbe se stesso). Senza, la pista e' un riempimento `surfaceContainerHigh`.

**Trappole.** Lo scorrimento e' **dell'app** («callers own scrolling»): l'indice non conosce item statici in testa (il titolo di `FluidScreen`!). `itemIndex` deve contare tutto cio' che sta sopra. `activeFluidSectionForItemIndex` sceglie l'ancora con `itemIndex`
piu' grande non oltre il primo item visibile.

## Token, costanti, Defaults

| oggetto | valori |
|---|---|
| `FluidScreenDefaults` | `ControlRowHeight` 44, `ScrollEdgeDeadZone` 8, `ScrollEdgeRampDistance` 64 (i vecchi `ShieldDeadZone` e `ShieldRampDistance`, deprecati, hanno lo stesso valore), `GlassFadeTail` 34 (deprecato: solo per la fascia di ripiego), `CompactTitleInset` 84, `HorizontalPadding` 20, `ContentMaxWidth` 760, `ItemSpacing` 14, `TitleTopSpacing` 4, `TitleBottomSpacing` 10, `ContentBottomSpacing` 28, `RefreshTrigger` 76, `RefreshHold` 56; `topBarHeight()`, `bottomContentPadding(extra)` |
| `FluidColumnsDefaults` | `MinColumn` 340, `Spacing` 20, `GridSpacing` 14, `MinCard` 300, `MaxColumns` 3, `WideContentMaxWidth` 1120 |
| `FluidPaneDefaults` | `MediumBreakpoint` 600, `ExpandedBreakpoint` 1000; `FluidPaneSizes`: side 280, list 380, detailMin 420, rail 112 |
| `FluidListDetailSizes` | `listMin` 360, `listMax` 500, `listFraction` 0,4, `detailMin` 480 |
| `FluidSidebarDefaults` | `Margin` 12, `RowHeight` 44 |
| `FluidFastScrollerDefaults` | `MinItems` 30, `ThumbWidth` 28, `ThumbHeight` 52, `EdgeInset` 4, `BubbleGap` 10, `PressedScale` 1,15, `AccessibilityStrip` 24; nascondi dopo `FluidMotion.FastScrollerHideMillis` 1200 ms |
| `FluidSectionIndexDefaults` | `MaxVisibleAnchors` 8, `TouchWidth` 48, `MinTouchWidth` 28, `RestBarWidth` 5 × `RestBarHeight` 50, `HitMargin` 16, `RestTrackFraction` 0,42 (160–300 dp), `ExpandedTrackFraction` 0,55 (260–460 dp), `ExpandedTrackWidth` 20, `LensSize` 34, `MarkSize` 4, `ActiveMarkWidth` 16, `MarkSpacing` 10, `EdgePadding` 4, `ExpandedMargin` 18, `ExpandedMarkInset` 16 |
| elastico (interno, in `FluidScrollPhysics.kt`) | resistenza 0,55; tetto 24% della vista; velocita' massima 5500 px/s; morph solo se `fontScale < 1,3`; compatto = 0,62 × grande; faccette ogni 2400 ms |

## Cosa e' verificato dai test

- `FluidScrollPhysicsTest` (engine-ui, 16): progresso del titolo lineare e limitato, binario prima di misurare; **nessun punto di riposo fra le due case**; quando il morph e' sostituito
  (testo grande, a capo, animazioni ridotte); **una sola copia del titolo a ogni progresso**; copie disgiunte nel passaggio per l'accessibilita'; gomma di UIKit e sua inversa, simmetrica sui due bordi; pagina
  corta: niente bordo basso ma bordo alto aperto per il refresh; soglia asimmetrica della barra (nascondere chiede piu' che rivelare).
- `FluidChromeMotionTest` (11): intensita' del vetro (zona morta e rampa), offset e assestamento della barra storica, **elezione del fondale davanti** (davanti non e' il nuovo; passa di mano a conferma; resta a gesto annullato; ripiego).
  `FluidChromeTransitionTest` (3): durante il movimento la chrome rifrange le due pagine.
- `FluidColumnsTest` (6), `FluidGridColumnsTest` (3), `FluidReadingWidthTest` (5), `FluidListDetailLayoutTest` (5), `FluidPaneLayoutTest` (7): tutti **numeri puri** — colonne per larghezza e per scala del testo, muratura, margine di lettura, soglie dei pannelli, barra laterale che si sfila.
  Nessun test compone `FluidListDetailScaffold`, `FluidPaneScaffold`, `FluidSidebar` o `FluidMasonry`.
- `FluidAmbientTest` (5): intensita' 0 = fondo piatto, sette toni sette colori, `urgent`, polarita' del fondo rispettata, motivo in scala. `FluidFastScrollTest` (18, puri) e `MoreComponentsTest` (lista corta senza maniglia, target proprio, `minItems`).
  `FluidSectionIndexTest` (6) e, in Robolectric, `FluidMatrixTest`/`AllGlassTest`: tenere premuto apre il nastro, `GestureStart` senza `Tick`, il tick per tacca, la bolla.
- `FluidHeadingSemanticsTest`: il titolo grande e' `heading()`.
- Il bordo dello scorrimento: `FluidScrollEdgeTest` (la disponibilita' e il ripiego, il profilo di ogni stile, `reach`), `FluidScreenScrollEdgeTest` (17: la taglia del riquadro della barra, i nomi deprecati, l'intensita' in alto e in basso, lo scorrimento che resta, chi sta in fondo e il suo registro, il lato dalla sonda), `FluidAnchoredBarEdgeTest` (3: la barra ancorata pesa la striscia fra due pagine come pesava la fascia), `GlassScrollEdgeTest`, `GlassScrollEdgeSceneTest` e `GlassScrollEdgeTuningTest` (il profilo, il velo e le manopole sul banco). **Non provato dai test**: lo scurimento sul contenuto scuro sul dispositivo (e' provato sul banco), e il costo della striscia doppia in un lancio.
- **Non provati da test**: refresh col dito e `FluidEdgeOverscrollState` composto in una pagina vera, `FluidScreen` con `metrics`, tutti i fotogrammi del morph (da guardare sul dispositivo).

## Dove guardare

Percorsi sotto `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`:

- `FluidScreen.kt` — `FluidScreen`, `FluidBarAction`, `FluidChromeController`, `ProvideFluidChrome`, `FluidScrollToTopBus`, `FluidScreenDefaults`, la barra (`FluidTopBar*`, `FluidTopBarBackground`: bordo o fascia), il titolo grande (`FluidLargeTitle`), `FluidTitleMorphState`, facette, intensita' del vetro, il bordo in basso.
- `FluidScrollEdge.kt` — il bordo dello scorrimento (`FluidScrollEdge`, `FluidScrollEdgeStyle`, `FluidScrollEdgeDefaults`, `fluidScrollEdgeAvailable()`); il programma e il profilo sono in `ui/glass/field/GlassScrollEdgeShader.kt`.
- `FluidCollapsingHeader.kt` — il titolo che si ritira fuori da `FluidScreen` (`FluidTitleCollapse`, `FluidCollapseScroll`).
- `FluidScrollPhysics.kt` / `FluidOverscroll.kt` — gomma, `FluidEdgeOverscrollState`, snap del fling, regole dei due titoli; i due modificatori pubblici.
- `FluidAmbient.kt`, `FluidAmbientSurface.kt` — fondale e fondale di finestra. `FluidContainerScaffold.kt` — dettaglio opaco.
- `FluidReadingWidth.kt`, `FluidColumns.kt`, `FluidGridColumns.kt` — larghezza, colonne, muratura, tessere.
- `FluidListDetail.kt`, `FluidPaneScaffold.kt`, `FluidSidebar.kt` — schermi larghi.
- `FluidFastScroller.kt`, `FluidSectionIndex.kt` — liste lunghe.
- Esempi: `sample/.../GalleryActivity.kt` (shell), `catalog/AllGlassDemo.kt` (indice, rail), `catalog/MoreComponentsDemo.kt` (scorrimento rapido).
