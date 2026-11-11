<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/barre.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Barre di navigazione e di azione

Questa famiglia e' tutto cio' che galleggia di vetro in fondo allo schermo (o al fianco di una pagina larga) e ti porta
altrove o fa qualcosa: la **tab bar flottante** (`FluidFloatingTabBar`), la sua forma da schermo largo (`FluidTabRail`),
la **capsula di azioni** (`FluidFloatingCapsule`), il **campo di ricerca** di vetro (`FluidSearchField`) e le due barre
storiche, oggi deprecate e ridotte a un involucro della prima (`FluidTabBar`, `FluidFoldingTabBar`). La barra
*superiore* (titolo, indietro, azioni) e' di `FluidScreen`, in [schermata.md](schermata.md); la barra di pagina ancorata
all'host delle pagine (`FluidPageChrome` e relative) e' in [pagine.md](pagine.md) e [pagine-back-e-barra.md](pagine-back-e-barra.md).

**Modello mentale.** Chi *usa* la barra decide dove sta e quanto spazio le lascia la pagina; la barra decide come si
ripiega, e lo fa guardando **la pagina che scorre sotto**, attraverso una `NestedScrollConnection` che l'app attacca a
un antenato delle pagine. Tutte queste barre hanno la stessa pellicola di vetro (ruolo `Floating`, `GlassDefaults.floatingTint()`):
due pillole nella stessa fila non sono due grigi. La barra non porta mai dentro di se' il proprio margine.

Catalogo completo delle firme: [../api/barre.md](../api/barre.md).

## Regole trasversali: dove sta, quanto spazio prende, chi la guida

| cosa | regola |
|---|---|
| **le icone** | un'app mette l'icona e **la taglia arriva dal posto**: `FluidIcon(FluidIcons.Home, contentDescription = null)` dentro una scheda prende 28 dp (`FluidMetrics.TabBarIconSize`), dentro la capsula 26, in un tasto di vetro 0,6 del vetro, in un menu 22, fuori 24; l'inchiostro e' quello del testo (nero sul chiaro, bianco sullo scuro), non piu' l'accento. `FluidIcons` e' il set dell'engine (al tratto, peso 500; `FluidIcons.Filled.*` le gemelle piene); le icone Material (`Icons.Rounded.*`) **continuano a funzionare** alla taglia nuova e un `Icon(…)` con una taglia scritta resta com'e'. Schede: `FluidTabItem(…, icon = FluidIcons.Home, filledIcon = FluidIcons.Filled.Home)` per la gemella piena, che la tab bar e il rail disegnano **su ogni scheda**, scelta o no, come in Apple (la scelta la dicono pillola e colore) |
| **posa** | la barra **non porta** margine laterale, margine basso ne' barra di sistema: li mette chi la posa. Per la tab bar: `Box(Modifier.align(BottomCenter).navigationBarsPadding().padding(horizontal = HorizontalMargin, vertical = BottomMargin))`. Margini di serie in `FluidFloatingTabBarDefaults`: laterale 14 dp, basso 16 dp «sopra la maniglia dei gesti, non contro di essa» |
| **spazio da lasciare alla pagina** | `FluidFloatingTabBarDefaults.ContentInset` = `OpenHeight` 62 + `BottomMargin` 16 = **78 dp** (dalla 4.0; erano 60 e 76: chi aveva scritto 76 a mano resta corto di 2), *senza* barra di sistema (la aggiunge `FluidScreen`). Con una banda sopra le schede `contentInsetWithAccessory(accessoryHeight)` = `ContentInset` + banda + `Spacing` 8. Lo si da' **una volta**, alla shell: `ProvideFluidChrome(bottomInset = …)`, vedi [schermata.md](schermata.md) |
| **la connessione di scorrimento** | `rememberFluidFloatingTabBarScrollConnection()` va su un **antenato delle pagine** con `Modifier.nestedScroll(connection)`; la barra le e' *sorella*, non genitrice, quindi la riceve per argomento. Senza, la barra resta aperta |
| **il fondale** | `backdrop = chromeController.activeBackdrop.value ?: fallback`: la pagina davanti (vedi `FluidChromeController` in [schermata.md](schermata.md)). Senza `backdrop` la barra e' una capsula piatta, senza vetro e senza lente |
| **una vibrazione per momento** | tocco su una scheda, sul cerchio o sulla scheda ripiegata: un `Tap` (vocabolario, la rete dei 70 ms fonde quello della barra con quello che l'app suona a sua volta); trascinando la pillola un `Tick` per scheda attraversata, nessun colpo al rilascio. Il campo di ricerca chiuso suona `Open` quando si apre; i tasti della capsula suonano i loro (`fluidPressable`) |
| **animazioni ridotte** | `FluidFloatingTabBar` **non legge** `LocalFluidMotionPolicy`: piega, pillola e sfocatura gommosa si muovono comunque (letto dal codice; nessun test). La capsula (`enter`/`exit`) e il campo (`FluidMotion.standard()`) la rispettano: restano la dissolvenza e un'apertura di colpo |
| **margini della capsula/campo al posto della barra** | `Modifier.fluidCapsuleInsets(placement, avoidKeyboard)`: barra di sistema + margini laterali della barra + il suo margine basso (piu' lo spazio della barra se `AboveTabBar`) |

I numeri che la pagina deve conoscere, in dp: `OpenHeight` 62 (`FluidMetrics.TabBarHeight`; un **pavimento**: con testo al 130% cresce invece di tagliare l'etichetta; 60 fino alla 3.x),
`FoldedHeight` 52, `FoldedSoloHeight` 64 (ripiegata e *sola*: niente banda e niente cerchio accanto), `TabWidth` 88 (un **tetto**), `Spacing` 8.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la navigazione principale a 3–5 destinazioni con icona e parola | `FluidFloatingTabBar(items, selectedRoute, onSelect, scrollConnection, backdrop)` (forma a elenco, `FluidTabItem`) |
| schede che sono composable qualunque, un cerchio di ricerca, una banda sopra la fila (mini player), una ricerca che cresce nella barra | `FluidFloatingTabBar(selectedTabKey, scrollConnection, …) { tab(…); standaloneTab(…) }` (forma a DSL) |
| che la barra si ripieghi mentre la pagina scorre | `rememberFluidFloatingTabBarScrollConnection()` + `Modifier.nestedScroll(...)` sull'antenato delle pagine |
| che **non** si ripieghi mai | `inlineBehavior = FluidFloatingTabBarInlineBehavior.Never` (o non attaccare la connessione) |
| tenere ferma la barra (ricerca in corso, finestra che nasce dalla banda) | `scrollConnection.locked = true` |
| la navigazione su uno schermo largo | `FluidTabRail` (dentro `FluidPaneScaffold`, [schermata.md](schermata.md)) |
| un pallino o un numero su una zona («Chat, novità») | `FluidTabItem(badge = FluidTabBadge(count))` o `tab(badge = …)`: lo disegna la barra, **solo a barra aperta** (sezione «Segni, pulsante e movimento ridotto») |
| un'azione primaria («Scrivi») che resta visibile a barra ripiegata, o in testa al rail | `standaloneTab(…)` (il cerchio accanto alla pillola: è un **pulsante**, non una zona); su schermo largo `FluidTabRail(header = { … })` |
| un'azione della capsula che si accende e si spegne (grassetto, filtro) | `FluidCapsuleAction(selected = …)`: ruolo `Switch`, disco dell'accento |
| un pannello laterale modale su una finestra stretta (cartelle, account) | `FluidDrawer` — [menu-modali.md](menu-modali.md) |
| la pillola «3 nuove ↑» sopra una lista | `FluidJumpPill` — [contenuto-righe-liste.md](contenuto-righe-liste.md) |
| una barra di selezione multipla, un mini player, comandi di una foto | `FluidFloatingCapsule` con `FluidCapsuleAction` |
| un campo di ricerca di vetro, anche richiudibile in un cerchio, anche con «3/12» | `FluidSearchField` |
| un campo di ricerca *dentro* la barra (`searchMode`) | `searchBarContent` — attenzione: `FluidSearchField` porta il **suo** pannello (vedi trappole) |
| la barra superiore col titolo e le azioni | `FluidScreen` / `FluidBarAction` — [schermata.md](schermata.md) |
| la barra di una pagina che non e' una `FluidScreen`, dentro un host di pagine | `FluidPageChrome` — [pagine.md](pagine.md) |
| «Non cosi'»: `FluidTabBar` | deprecata: e' `FluidFloatingTabBar` a elenco senza ripiegamento. Usa `FluidFloatingTabBar` direttamente |
| «Non cosi'»: `FluidFoldingTabBar` + `FluidBarFold` | deprecata: e' `FluidFloatingTabBar` con la piega guidata da te. Usa `FluidFloatingTabBar` e la sua connessione |
| «Non cosi'»: la barra dentro uno `Scaffold(bottomBar = …)` | lo `Scaffold` toglie spazio alla pagina, ma la barra deve galleggiare **sopra** perche' il contenuto le scorra sotto e si veda sfocato |

## FluidFloatingTabBar

**Cos'e'.** La barra di navigazione dell'app Fluidify portata nell'engine (sorgente di Elyes Mansour *FloatingTabBar* v1.0.1, con la pillola liquida di
Kyant0 innestata: rifrazione sotto la pillola, riga nascosta tinta che la pillola campiona attraverso se stessa, stiramento che segue il dito). Esiste perche' un
engine i cui componenti sono peggiori di quelli dell'app non lo usa nessuno. Una capsula di schede che **si ripiega in una pillola** quando la pagina scorre e si riapre
quando serve, con una pillola trascinabile che indica la scheda scelta. Formattata a quattro spazi (come l'originale, perche' un confronto con l'upstream sia un diff e non
un'archeologia) e con le modifiche locali segnate dove succedono (`LOCAL CHANGE`).

**Firma essenziale** (le due forme).

```kotlin
// A elenco: tre destinazioni con un'icona e una parola.
FluidFloatingTabBar(
  items: List<FluidTabItem>,                 // FluidTabItem(route, label, icon)
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  scrollConnection: FluidFloatingTabBarScrollConnection,
  backdrop: GlassBackdropState? = null,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},   // la scheda gia' scelta: «torna in cima»
  glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(),
  accentColor: Color? = null,
  foldAlignment: Alignment.Horizontal = Alignment.Start,
)

// A DSL: ogni scheda e' una coppia di composable.
FluidFloatingTabBar(
  selectedTabKey: Any?,
  scrollConnection: FluidFloatingTabBarScrollConnection,
  modifier: Modifier = Modifier,
  tabBarContentModifier: Modifier = Modifier,      // il pannello, se lo dipinge l'app
  inlineAccessory / expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
  backdrop: GlassBackdropState? = null,
  glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(),
  foldAlignment: Alignment.Horizontal = Alignment.Start,
  searchMode: Boolean = false, searchBarContent: (@Composable (Modifier) -> Unit)? = null,
  expandedContentWidthPx: Int? = null, onExpandedWidthChanged: ((Int) -> Unit)? = null,
  content: FluidFloatingTabBarScope.() -> Unit,    // tab(key, title, icon, onClick); standaloneTab(key, icon, onClick)
)
```

Tutti i parametri (colori, forme, taglie, ombre): [../api/barre.md](../api/barre.md).

**Uso** (dal `sample`, `GalleryActivity.kt` e `AllGlassDemo.kt`).

```kotlin
val tabScroll = rememberFluidFloatingTabBarScrollConnection()       // sul chiamante, vedi sotto dove attaccarla

FluidFloatingTabBar(
  items = tabs, selectedRoute = route,
  onSelect = { navigateTo(it.route) },
  onReselect = { scrollToTop.request() },
  scrollConnection = tabScroll, backdrop = backdrop,
)

// A DSL, con una banda sopra le schede e un cerchio di ricerca:
FluidFloatingTabBar(
  selectedTabKey = selected, scrollConnection = tabScroll, backdrop = backdrop,
  inlineAccessory = { band, _ -> MiniPlayer(band) },     // `band` e' il Modifier da mettere sulla radice della banda
  expandedAccessory = { band, _ -> MiniPlayer(band) },
) {
  tab(key = "home", title = { Text("Home") }, icon = { Icon(Icons.Rounded.Home, null) }, onClick = { selected = "home" })
  tab(key = "star", title = { Text("Preferiti") }, icon = { Icon(Icons.Rounded.Star, null) }, onClick = { selected = "star" })
  standaloneTab(key = "search", icon = { Icon(Icons.Rounded.Search, "Cerca") }, onClick = { searching = true })
}
```

### Come si ripiega (e quando no)

Sono **tre stati visivi** su una sola scena: `INLINE` (ripiegata), `EXPANDED` (tutte le schede) e `SEARCH_EXPANDED`. Il passaggio e' un `AnimatedContent`
con dissolvenza (`FluidMotion.FoldCrossFade`, rigidezza media-bassa critica) piu' elementi condivisi (`tabGroup`, `standaloneTab`, `accessory`, l'icona della scheda scelta), un
impulso di sfocatura «gommosa» che sale a 12 dp a meta' e torna a zero (`GooeyPeakBlur`, `FluidMotion.GooeyMillis` = 300 ms) e la pillola che *cresce* in 300 ms (`PuckEnter`, scala di partenza 0,82):
non fa parte dell'elemento condiviso, quindi l'unica cosa che la tiene con le altre e' la durata uguale.

**La decisione sta in `FluidFloatingTabBarScrollConnection`** (la connessione che l'app attacca alla pagina):

| | |
|---|---|
| `inlineBehavior` | `OnScrollDown` (di serie): si ripiega scorrendo in giu', si riapre appena si risale. `OnScrollUp`: il contrario, con soglia unica. `Never`: mai |
| soglia di piega | `scrollThreshold` **50 dp** di scorrimento netto in un verso (la direzione che cambia azzera il conto) |
| soglia di riapertura | `expandThreshold` **8 dp** verso l'alto: asimmetrica di proposito. Ripiegarsi vuole isteresi (una scorsa nervosa non deve farla sfarfallare, e la distanza e' gratis: ti stai gia' allontanando); tornare e' l'opposto, la barra e' cio' che stai cercando, e farti trascinare altri cinquanta pixel la fa sembrare lenta. Un tempo questo lato valeva zero (tornava solo in cima): il parametro era documentato e non veniva letto |
| cosa conta | `onPostScroll` col **`consumed`**, cioe' cio' che il contenuto ha *davvero* scorso (piu' l'elastico di una `FluidScreen`). Prima era il dito di `onPreScroll`: una pagina che non scorre ripiegava la barra a ogni trascinamento |
| a pagina in cima | un trascinamento verso il basso con niente piu' da scorrere (`available.y > 0` dopo il contenuto) **la riapre**: e' l'unico momento in cui la barra e' chiesta e non d'intralcio |
| cambio di pagina o scheda | `routeChanged()` (3.0.1): chiude lo scorrimento in corso e riapre la barra (se non `locked`). Lo chiamano da se' l'host delle pagine (`FluidNavHost`, `FluidPageHost`, via `FluidRouteSignal`, per finestra: un host in un `Dialog` non tocca la barra dell'app) e la barra stessa a ogni cambio di `selectedTabKey`. **`settle()` + `expand()` a mano non servono piu'**: chi non li faceva si ritrovava la barra ripiegata su una pagina corta che non poteva piu' riaprirla |
| `locked` | mentre e' vero lo scorrimento non tocca la barra: serve per la ricerca (la barra porta il campo; ripiegarla mentre si scrive lo distrugge, risalire lo ricrea, il campo chiede il fuoco e sale la tastiera: a ogni scorsa). Con `locked` un cambio di pagina chiude solo lo scorrimento, **non** apre |
| `isInline` | stato osservabile (lettura; si scrive con `expand()`/`inline()`) |
| `scrolling` | vero mentre la pagina sotto si muove: **il vetro tiene l'ultima cattura** invece di rifare sfocatura e lente 60 volte al secondo (misurato: 57 ms a fotogramma col vetro vivo, 25 senza). Si abbassa in `onPreFling`; se una lista viene distrutta a meta' gesto il fling non arriva e il flag resta alzato (il bug «il contenuto non c'e' quando torno») — per questo `routeChanged()`/`settle()` |
| `morphing` | vero per i fotogrammi in cui la barra cambia forma. **Per il pannello che l'app costruisce da se'**: la barra non puo' leggerlo da dentro (il pannello e' applicato fuori dalla sua composizione), e lo legge qui per congelarsi |

`rememberFluidFloatingTabBarScrollConnection(initialIsInline = false, scrollThreshold = 50.dp, expandThreshold = 8.dp, inlineBehavior = OnScrollDown)` ricorda l'istanza
con chiave sulle soglie in pixel, su `inlineBehavior` e su `initialIsInline`: **cambiare `initialIsInline` ricrea la connessione e azzera lo stato**.

**Ripiegata, cosa si vede.** Solo la scheda **scelta** (la sua icona, in una capsula `FoldedHeight` 52) e, se ci sono, la banda e il cerchio. Da sola (nessuna banda ne' cerchio) sale a
`FoldedSoloHeight` **64** e si squadra: «e' la navigazione intera, va trovata e colpita senza guardare; a 52 sembrava un avanzo». Con qualcosa accanto torna alla misura della fila (una fra le altre).
Se la scheda scelta non e' una scheda normale (sei sul cerchio), la barra mostra **l'ultima scheda normale davvero scelta**, mai «la prima» (non direbbe una cosa falsa); prima di aver scelto
qualcosa, nessuna scheda in linea.
**Un tocco sulla scheda ripiegata riapre la barra e chiama anche `onClick` della scheda**: nella forma a elenco, essendo la scelta, e' `onReselect` — «torna in cima»/«chiudi la pagina» parte insieme all'apertura.

**`foldAlignment`** (default `Alignment.Start`, **BREAKING dalla 2.3.0**: prima, ignorato, si raccoglieva al centro): da che parte si raccoglie la pillola. Non e' estetica ma una domanda su
quale mano tiene il telefono: ripiegata e' l'unico bersaglio rimasto, e dove si posa decide se il pollice ci arriva. Ignorato se c'e' una banda (la riga occupa tutta la larghezza e non ha dove
raccogliersi). Aperta, il gruppo schede+cerchio e' sempre **centrato** (stile iOS 26).

### L'aperta: schede, pillola, larghezza

- Le schede hanno larghezza uguale: `TabWidth` 88 dp e' un **tetto, non una promessa**. Su un telefono stretto (o a dimensione di schermo grande) `fittedTabWidth` divide quello che resta dopo
  cerchio, spazio e padding fra le schede. Misurata **un fotogramma dopo** la prima composizione (il peso del figlio tiene insieme il primo). Senza cerchio non si lascia lo spazio per uno:
  prima richiedeva `circleWidthPx > 0` e una barra senza cerchio non si adattava mai (cinque schede a 88 = 448 dp contro i ~383 del telefono: l'ultima fuori dalla pillola, le etichette a capo,
  la barra «alta con cinque voci»).
- L'etichetta e' una riga sola: stile imposto `labelMedium` a 11 sp (`FluidMetrics.TabBarLabelSize`, 13 fino alla 3.x) centrato — `Text(item.label)` senza stile prenderebbe `bodyLarge` e «Bacheca» andrebbe a capo, raddoppiando la barra di trenta dp — e
  `OneLine` la misura senza limite e la **scala** fino a `MinLabelScale` 0,75 per farla stare; oltre, la parola e' troppo lunga per essere un'etichetta e viene tagliata. L'altezza e' sempre quella di una riga. Il colore del tuo `Text` vince.
- **La pillola** e' trascinabile. Rilasciata, si assesta sulla scheda piu' vicina e **naviga solo se c'e' stato un vero trascinamento** (un tocco che passa attraverso la pillola non deve rinavigare). Un `Tick` per scheda
  attraversata, letto dal valore arrotondato (lo stesso istante in cui si assesterebbe). Tutta la pillola si sposta di un massimo di 4 dp nel verso del trascinamento (`EaseOut`), poi rientra
  (`FluidMotion.TabTugReturn`). Una molla sottosmorzata **di proposito** (lo smorzamento di libreria, 0,5): lo stiramento oltrepassa e oscilla dopo ogni cambio di velocita', ed e' la gelatina giusta per la mano — la stessa di slider e interruttore.
  Squash con segno e diviso 10, non cancellato dalle animazioni ridotte (eccezione nota: la pillola e' uno dei tre metri della ricetta).
- **Un tocco su una scheda** muove la pillola *subito* (`animateToValue`: preme, viaggia, rilascia, come se l'avesse trascinata il dito) e **la pagina aspetta** `FluidMotion.TabPuckLeadMillis` (160 ms)
  prima di costruirsi: comporre una pagina nuova occupa il thread principale e il fotogramma successivo puo' arrivare con 80 ms di ritardo; l'animazione e' corretta ma avanza di 80 ms in un colpo, e si vede la
  pillola sulla scheda che hai lasciato e poi a tre quarti, senza niente in mezzo. Le due cose sono messe in fila invece che in gara. Un tocco sulla scheda gia' scelta chiama `onClick` **subito** (non c'e' niente da attendere).
  Se l'app rifiuta il cambio, l'effetto di sincronizzazione riporta la pillola.
- **Selezione fatta altrove** (back, deep link, un'altra schermata): la pillola arriva con `animateToValue` (preme e si assesta come dopo un rilascio), perche' nient'altro la annuncia. La prima volta si posa senza animare.
- **Rilasciare la pillola sulla scheda gia' scelta** chiama `onClick` di quella scheda: nella forma a elenco e' `onReselect`.
- **Sulla scheda niente increspatura** (`indication = null` di serie dalla 3.0, fase 9): la pillola *e'* l'indicazione. La scheda **in linea** e il cerchio rispondono col bagliore del dito sul loro vetro e un `Tap`.
- **Premuta, la pillola diventa lente.** Una *riga nascosta* (copia tinta d'accento della fila, **composta solo mentre la pillola e' premuta**: a riposo costava 8 ms a fotogramma) e' cio' che la
  lente campiona, cosi' l'icona scelta si vede attraverso il vetro nel colore d'accento; la riga si registra due fotogrammi e poi si ferma (`tintedRowSettled`): serve un'immagine da rifrangere, non una viva.
  L'icona scelta e' ridisegnata nitida *sopra* la pillola e sfuma in un terzo della rampa di pressione. L'inchiostro e' l'accento portato verso nero (chiaro) o bianco (scuro) del minimo che regge **3:1**
  contro la pellicola del pannello sopra la pagina peggiore (`legibleInk`, `PuckInkMinContrast`): da 1,1:1 a 7,8:1 su un riquadro verde. Senza `backdrop` la pillola e' un disco piatto (`backgroundColor` da 50% a 100%).
- **Il colore di icone ed etichette: quello dell'app, e `onSurface` solo se nessuno l'ha deciso** (`floatingBarContentColor`). Le schede leggono `LocalContentColor`, che fuori da una `Surface` vale il suo valore di serie, `Color.Black`: su vetro scuro, nero su grigio scuro. Se il valore e' quello di serie la barra imposta `onSurface` del tema; qualunque altro valore (il colore di una `Surface`, un `LocalContentColor provides Ink` in radice) resta, cosi' un'app che aggiorna a parametri invariati non cambia aspetto. **Caso limite:** un'app che fornisce davvero il nero puro e' indistinguibile da una che non ha detto niente (un CompositionLocal non dice se e' stato impostato), quindi quel nero diventa `onSurface`; sul tema chiaro la differenza e' minima, e per tenerlo basta un colore non esattamente `Color.Black`. Mai un colore a mano.
- La luce del dito sul vetro e' **additiva** (`GlassTouchHighlight(onGlass = true)`, bianca e stretta); sulla fila opaca di prima era nero all'8%, e su tema chiaro tenere premuta la pillola ingrigiva la fascia chiara in alto. Sul tema scuro non cambia niente.

### La banda (accessory)

`inlineAccessory` e `expandedAccessory` ricevono `(Modifier, AnimatedVisibilityScope)` in uno `SharedTransitionScope`. Il `Modifier` **va sulla radice della banda**: porta ombra di vetro, ritaglio
e — dove c'e' un pannello — il vetro stesso, e quello che ci metti sopra resta opaco al 100%. La banda e' **il vetro della barra** (3.0, fase 9; senza pannello e' la capsula piena di prima).
In linea la riga e' `[scheda][banda ‑ weight][cerchio]` a tutta larghezza e l'altezza la decide la banda; aperta la banda sta **sopra** la pillola, larga quanto lei (misurata: un fotogramma di `fillMaxWidth`).
Con **tutte e due** le bande l'elemento condiviso `accessory` viaggia fra gli stati; con una sola, o dissolve (`animateEnterExitAccessory`). **Costo vero**: per far viaggiare un elemento condiviso vanno
composte entrambe le copie per tutta la piega; l'alternativa dichiarata e' non avere due barre (e' dove va `FluidFoldingTabBar`...). Contenuto pesante (copertina, due righe, progresso) nella banda = lavoro doppio per 300 ms.

### La ricerca nella barra

`searchMode = true` **e** `searchBarContent != null`: lo stato aperto diventa `SEARCH_EXPANDED` — il gruppo schede si riduce all'icona della scheda corrente (la stessa della forma in linea) e `searchBarContent(modifier)`
prende il resto della riga (alta 48 dp). Il `Modifier` ricevuto va sulla **radice** del campo: porta peso, elemento condiviso `standaloneTab` (e' cosi' che il cerchio *cresce* in un campo invece di sparire e comparire),
ombra, riempimento, ritaglio e pannello. La larghezza resta quella della barra aperta: la riga la ricorda da `onExpandedWidthChanged` e la ripassa in `expandedContentWidthPx` (altrimenti riempirebbe tutto lo
spazio). Lo stato ripiegato per scorrimento **non cambia** con `searchMode`. Mentre si cerca tieni `scrollConnection.locked = true` (sopra).

### Il materiale: tre casi e la regola del raggio

| caso | cosa succede |
|---|---|
| `backdrop` e nessun `tabBarContentModifier` | la barra **si fa il vetro da sola** (`glassSurface`, `Floating`, `floatingTint`) e il suo riempimento (`colors.backgroundColor`) diventa trasparente: un riempimento opaco dietro il vetro e' un muro nella registrazione che la pillola guarda, e premendo la pillola il vetro rifratto diventava una lastra d'accento piatta |
| `tabBarContentModifier` passato | il pannello e' **dell'app** (un suo `liquidGlass`): resta com'e', e `FluidFloatingTabBarGlass.blurRadius` e' il suo **raggio finale** in dp |
| nessun `backdrop` | capsula piena di `colors.backgroundColor` (`surfaceContainerHigh`), ombra di piattaforma, pillola piatta |

`FluidFloatingTabBarGlass(blurRadius, puckColor, puckOpacity = 0,26)` sono i tre numeri che l'app puo' muovere. **`blurRadius` ha due significati** perche' la riga nascosta e la pillola a riposo devono sfocare
*esattamente* quanto il pannello che coprono, e quanto sfoca lo sa solo chi lo dipinge: se lo fa la barra e' il raggio di **base** (`DefaultGlassBlurRadius` 2,5 dp; la barra moltiplica per il ruolo `Floating` ×1,8 e
applica la regola della taglia e la qualita' di scorrimento: con il valore di serie 4,5 dp, come il pannello); se lo passi tu e' il raggio **finale**, preso tal quale: passa lo stesso numero che dai al tuo pannello.
La 2.12.0 li aveva confusi (la pillola sfocava 1,8 volte la barra intorno), la 2.12.1 li ha rimessi. Il puck premuto non aggiunge sfocatura (la riga ha gia' quella del pannello: una in piu' sfocherebbe anche le icone).
`puckColor` non specificato = l'accento (`primary`): una lavata d'accento al `puckOpacity` che scende a un quarto mentre e' premuta; un tempo erano due grigi rigidi, e su un tema chiaro la scheda scelta sedeva sotto un disco grigio-nero che non
apparteneva all'app. `accentColor` (default `primary`) tinge la riga nascosta. `LocalFluidTabBarBackdropFrozen` e' vero esattamente nei fotogrammi di una transizione: le superfici di vetro riusano l'ultima cattura invece di
rifare lo schermo a ogni fotogramma mentre i loro limiti si animano; un pannello dell'app lo raggiunge leggendo `scrollConnection.morphing`.

**Comportamento (riassunto).** Tre stati, una sola connessione; la pillola e' una lente; l'altezza e' un pavimento (`FluidFloatingTabBarSizes.barHeight`, `inlineHeight`, `inlineSoloHeight`); il gruppo e' centrato; la scheda in linea e' sempre quella scelta; la
pagina aspetta la pillola 160 ms; con cinque schede su 360 dp le etichette si restringono fino al 75% prima di tagliarsi.

**Trappole.**

- **La connessione deve stare su un antenato delle pagine**, non sulla barra: se la attacchi alla barra non sente scorrere niente e la barra non si ripiega mai.
- **Chiamare `settle()` + `expand()` a ogni cambio di rotta** era la toppa pre-3.0.1: si toglie. Ma `routeChanged` e' `internal`: se la pagina cambia **senza** passare dall'host (un tuo `AnimatedContent` come host di pagine) cambia comunque `selectedTabKey`, e quello basta; se neanche quello cambia, il flag `scrolling` puo' restare alzato e il vetro tenere la cattura per sempre: chiama `settle()`.
- **`ContentInset` non e' `FluidTabBarDefaults.ContentInset`**: quest'ultimo (64+16) ne riserva 4 dp in piu' (vecchia altezza). `OpenHeight` era 64 e dichiarata ma non rispettata (la barra ne stava ~90 con etichette a capo): chi ha scritto a mano un inset da quei numeri lo rilegga (2.1.0, **BREAKING**).
- **`FluidFloatingTabBarGlass.blurRadius` e' un'altra cosa a seconda di chi dipinge il pannello** (sopra): una barra con pannello dell'app e il default 2,5 sfoca meno del pannello.
- **`standaloneTab` chiamato due volte sovrascrive la prima**; il suo `title` non si mostra mai (e' sempre un cerchio senza testo, alto almeno 48 dp, quadrato a partire dall'altezza).
- **Una scheda ha bisogno di una `key` unica e stabile**: `selectedTabKey` che non corrisponde a nessuna scheda lascia la pillola dov'era (alla prima composizione, sulla prima).
- **`FluidFloatingTabBar` calcola le schede a ogni composizione** (non le ricorda: le lambda chiudono su stato e colori, e una chiave dimenticata dava un'icona vecchia su un solo percorso di navigazione). Costo trascurabile con poche schede.
- **`searchBarContent` e `FluidSearchField`**: `FluidSearchField` porta **il proprio pannello** di vetro e il modificatore della barra gliene mette un altro: nello slot servirebbe un campo «nudo» (lente, testo, crocetta, fuoco), che l'engine non ha
  (e' una lacuna annotata in `artifacts/design/migrazione-3.0.md`). Nessun esempio del `sample` usa `searchMode`.
- **Accessibilita'**: la forma a elenco dice `selected` alla scheda scelta; le schede **non** hanno `Role.Tab` ne' il tag di test `top_level_<rotta>` (proprieta' del nodo scheda, che la barra costruisce da sola e sul quale una proprieta' messa su un discendente non sale):
  un test cerca la scheda dall'etichetta. Le copie nascoste della riga e l'icona ridisegnata sopra la pillola sono `clearAndSetSemantics {}`: non si raddoppia.
- Il focus da tastiera: Tab entra nella barra e una scheda col fuoco ha un velo d'accento.

**Non usarla quando.** Hai un solo livello di navigazione (lista → dettaglio): bastano `onBack` e `FluidScreen`. Su uno schermo largo con la barra laterale o il rail: `FluidTabRail` / `FluidSidebar`.

**Versione.** `FluidFloatingTabBar` dalla 2.0.0 (porting da Fluidify); altezze 60/52 e pavimento 2.1.0; `foldAlignment` e raccolta a sinistra 2.3.0; `FoldedSoloHeight` 2.3.0; forma a elenco 2.12.0; pannello proprio e `blurRadius`
2.12.x; ripiegamento sul consumato e `routeChanged` 3.0.1; banda come vetro, niente increspatura, luce del dito 3.0.0.

## FluidTabRail, FluidTabItem, FluidTabBarDefaults, fluidTabBarPadding

**Cos'e'.** La forma della navigazione per gli schermi larghi: lo stesso materiale e la stessa goccia della pillola, **in piedi su un lato**. `FluidTabItem(route, label, icon, badge, filledIcon)` e' la voce comune a rail e barra a elenco (`filledIcon`, dalla 4.0: la gemella piena che ogni scheda disegna, scelta o no; null = la stessa icona).

```kotlin
FluidTabRail(items = tabs, selectedRoute = route, onSelect = { go(it.route) }, backdrop = backdrop, onReselect = { top() })
```

**Comportamento.** Largo `FluidTabBarDefaults.RailWidth` 84 dp, una scheda per `FluidTabBarDefaults.Height` 64 dp con l'icona a `RailIconSize` 28 dp (= `FluidMetrics.TabBarIconSize`; 24 fino alla 3.x) e **tutte a inchiostro pieno**, come la tab bar (le non scelte non sono piu' in grigio: la scelta la dicono la goccia e la gemella piena), pannello di vetro (`Floating`, angoli 28 dp) col **proprio** `backdrop`. L'indicatore e' una **goccia** (`glassDrop`: piena e bianca sul
chiaro, grigia sullo scuro, lente mentre viaggia) che si muove come la pillola della barra quando si tocca una scheda: preme, corre, rilascia; la scheda scelta e' ridisegnata sopra la goccia a riposo e sfuma tre volte piu' in fretta della pressione.
La prima volta la goccia nasce al suo posto (altrimenti lampeggerebbe da lente appena compare); con animazioni ridotte salta. **Senza una scheda che corrisponda alla rotta la lente non si mostra** (il rail resta in scena anche su una nota, e una lente
parcheggiata sulla prima scheda direbbe una cosa falsa). Ogni scheda ha `Role.Tab`, `selected` e il tag di test `top_level_<rotta>` (a differenza della barra a elenco). **Non si ripiega** e non ha equivalente nella barra; il rail
si usa dentro `FluidPaneScaffold` (`rail = { FluidTabRail(...) }`, larghezza di layout `FluidPaneSizes.rail` = `RailWidth` + 2×`HorizontalMargin` = 112 dp, [schermata.md](schermata.md)).
`FluidTabBarDefaults`: `Height` 64, `RailWidth` 84, `RailIconSize` 28, `HorizontalMargin` 14, `BottomMargin` 16, `ContentInset` 80 (64+16, **obsoleta come numero per la barra in basso**: usa `FluidFloatingTabBarDefaults.ContentInset`).
`fluidTabBarPadding()` = `PaddingValues(horizontal = 14, vertical = 16)`: i margini di serie attorno a una barra flottante.

**Trappole.** Con un'unica scheda l'intervallo della goccia sarebbe vuoto (la molla dividerebbe per zero): la barra usa `coerceAtLeast(1)`. Il rail non vede il fondale da solo: passagli `backdrop`.

**Versione.** Rail dalle 1.x; goccia 3.0.0 (fase 9); lente nascosta senza selezione 1.33.0.

## Segni, pulsante e movimento ridotto (Fase 1 di Pampa Mail, 3.1.0)

Quattro aggiunte alla barra e al rail, tutte **opt-in**: chi non le usa ha lo stesso albero e le stesse misure di prima. Dove l'ultimo parametro di una firma era una lambda
(`tab(…)`: `indication`; `FluidTabRail`: `onReselect`) la novità è un **overload** e non un parametro in coda: la firma della 3.0 resta identica, e la lambda finale va dove andava
(provato da `Compat307SourceTest` in `engine-ui` e `Compat307BehaviourTest` nella galleria). File: `FluidFloatingTabBar.kt`, `FluidTabBar.kt`.

### Segni sulle zone: `FluidTabBadge`

**Cos'è.** Il pallino o il numero su una zona che ha qualcosa di nuovo mentre si è altrove (la chat con un messaggio, gli aggiornamenti di un negozio). `FluidTabItem(route, label, icon, badge = null)` nella forma a elenco e nel rail,
`tab(key, title, icon, badge, onClick, indication = null)` nella forma a DSL: un overload con `badge` **obbligatorio** (nullable: `badge = if (nuove) FluidTabBadge.Dot else null`) al **quarto**
posto, dove la firma della 3.0 ha `onClick` (così nessuna sua chiamata, nemmeno con un `null` al quinto posto, lo raggiunge); `indication` resta l'ultima e si passa per nome; `tab(key, title, icon, onClick, indication = null)` è quella di sempre. Un'implementazione dell'interfaccia scritta per la 3.0 compila com'era: il
membro nuovo ha un corpo di serie che inoltra al `tab` di sempre, senza segno. `FluidTabBadge(count: Int? = null, contentDescription: String? = null, color = Color.Unspecified)`: `count = null` è il **pallino**
(`FluidTabBadge.Dot`), `> 0` il numero («99+» oltre 99), `≤ 0` niente (né segno né frase), come `FluidBadge`.

**Perché lo disegna la barra e non l'app.** L'icona è un composable dell'app, ma la scheda la costruisce la barra: la riga nascosta che la pillola guarda e l'icona ridisegnata sopra di lei sono copie che l'app non vede, e un
segno disegnato dall'app dentro l'icona **spariva sotto la pillola**; TalkBack non lo leggeva (una proprietà su un discendente non sale al nodo della scheda). Qui il segno è un `FluidBadge` messo in tutte le copie, appoggiato
all'angolo alto dell'icona **senza cambiarne la misura** (sporge e si disegna fuori: scheda, riga nascosta e icona restano allineate al pixel con o senza segno, e un numero che col testo al 200% si allarga non sposta niente; a
sinistra in RTL).

**Solo a barra aperta (scelta voluta).** Ripiegata, la pillola mostra la sola zona in cui si è, e su quella il segno non compare: un segno discreto deve potersi ignorare per sempre, e una pillola ripiegata che si accende per una
zona che non si vede direbbe una cosa che non si può leggere. Si riapre la barra (scorrendo verso l'alto, o toccandola) e il segno è lì. **Anche TalkBack lo dice solo a barra aperta.** Il **rail** non si ripiega: il segno si vede sempre.

**Accessibilità.** Il segno è muto e **non è un bersaglio**: il tocco è della zona, e la zona si annuncia col suo nome e lo stato del segno («Chat, novità»), una volta sola, sul nodo della scheda (`stateDescription`). Parole di
serie: `FluidLabels.tabBadgeNew`, `FluidLabels.tabBadgeCount`; `contentDescription` le sostituisce. Per un numero la frase dice la **quantità vera** anche quando il disegno dice «99+». Il segno non si attenua con la scheda non
scelta: al 60% non reggerebbe più il suo contrasto, e su una zona che non è quella in cui si è (il caso per cui esiste) sarebbe il segno meno leggibile.

### `standaloneTab` come pulsante, azione in testa al rail

`standaloneTab` non è una zona: è un **tasto accanto alla pillola**, la ricerca che cresce nel campo o un'azione primaria come «Scrivi» che resta visibile anche a barra ripiegata. Per TalkBack si annuncia come
**pulsante** (`Role.Button`), mai come scheda scelta o da scegliere. Tre regole: la sua `key` **non** va passata come `selectedTabKey` (non cambia la zona in cui si è); il nome per TalkBack sta nella `contentDescription`
dell'icona (il `title` non si mostra); non si usa con `searchMode` (il cerchio crescerebbe nel campo di ricerca). Un'azione primaria deve avere un posto fisso anche su schermo largo: da 600 dp la barra diventa il rail, e
**`FluidTabRail(…, backdrop = b, header = { … })`** (un overload: `header` è obbligatorio, nullable, al **quinto** posto dove la firma della 3.0 ha il `Modifier`, e la lambda finale resta `onReselect`) mette l'azione **in testa** al rail, sopra la capsula, **fuori dal vetro del rail** con `FluidTabBarDefaults.RailHeaderSpacing` (8 dp) sotto, come lo spazio fra la pillola e il cerchio nella
barra in basso. È un tasto, non una zona (niente goccia, niente selezione): l'app ci mette il suo `FluidGlassIconButton` con la sua `contentDescription`, centrato nella larghezza del rail. Con `header` il `modifier` va al
contenitore di tutti e due e la capsula prende l'altezza che resta; senza (`null`, di serie) il rail è quello di prima.

### La pillola e il movimento ridotto

La barra legge `LocalFluidMotionPolicy`. Con `reducedMotion` la piega fra barra in linea e aperta è **un cambio in un fotogramma** (niente dissolvenza, niente elementi condivisi in viaggio, niente impulso gommoso), la pillola
nasce **intera** e al tocco, a una selezione fatta altrove o al rilascio di un trascinamento **arriva al suo posto senza viaggiare**; la pagina non aspetta il viaggio e parte subito. Restano il dito che trascina la pillola (1:1)
e il suo squash con segno. A scala normale tutto è come prima (la chiave della transizione è fissa). **Trappola:** spegnere solo le dissolvenze non basta, perché i limiti di un elemento condiviso sono un'animazione della
transizione e la terrebbero in moto: per questo ogni stato ha la sua transizione, nuova, che nasce già arrivata. La politica vale scala 1 di serie: cambia solo in un'app che installa `FluidMotionPolicyProvider`
(`movimento.md`) e ha le animazioni di sistema a zero.

## FluidFoldingTabBar e FluidBarFold (deprecati)

**Cos'e'.** La barra «a piega continua» che l'engine aveva, tenuta col suo nome e oggi **`FluidFloatingTabBar` sotto un altro nome**: nulla qui decide come appare la barra. Deprecata e non cancellata perche'
cancellarla avrebbe fatto pagare a ogni app un miglioramento che non aveva chiesto, con una scadenza che non aveva scelto; chi la chiamava ha preso il vetro vero senza toccare una riga. Si sceglie *di
andare* verso la barra nuova quando serve cio' che questa firma non sa chiedere: una banda dentro la barra, un campo di ricerca che cresce, schede come composable.

**Cosa delega e cosa ignora.** Costruisce una `FluidFloatingTabBar` a DSL (una `tab` per voce, titolo `Text(item.label)`, icona `tabIcon` o `item.icon`) con una connessione sua e **chiusa a chiave** (`locked = true`: la piega e' di chi la guida; due cose
sulla stessa piega litigano). `fold: () -> Float` (0..1) viene riletto in un effetto — non in composizione: ricomporrebbe tutta la barra a ogni fotogramma di una animazione — e **a `>= 0,5` chiama `inline()`, sotto `expand()`**: il numero e' ridiventato una decisione, allo stesso punto di mezzo di prima.
`accessory` va a **tutte e due** le bande; `searchMode`/`searchContent` ai due omologhi; `foldAlignment` (`FluidFoldAlignment`: `Start`, `Center`, `End`) alla `Alignment.Horizontal`, **default `Start`** (era `Center` e veniva ignorato, 2.3.0).
**Ignorati** (e il KDoc lo dice): `tint` (il materiale e' della barra, non un colore che passa il chiamante), `trailing` (nella barra nuova il cerchio e' una *scheda* con chiave e selezione: trasformare un composable qualsiasi in una scheda sarebbe una sorpresa peggiore che ometterlo) e `accessoryHeight`.
**Un tocco sulla scheda ripiegata** chiama `onExpandRequest` se c'e' (aprire, non selezionare: chi ha appena toccato una barra chiusa non vuole «torna in cima»), altrimenti `onReselect`/`onSelect`.

```kotlin
val fold = rememberFluidBarFold()
Box(Modifier.nestedScroll(fold.connection)) { /* pagine */ }
FluidFoldingTabBar(items = tabs, selectedRoute = route, onSelect = go, backdrop = backdrop, fold = { fold.progress.value }, onExpandRequest = fold::unfold)
```

**`FluidBarFold`** (`rememberFluidBarFold()`): la piega e la connessione che la muove. `progress` e' un `State<Float>` 0..1 animato con `FluidMotion.TabBarFold` (molla 0,9, mai oltre), a salti con animazioni ridotte; `folded` e' `target > 0,5`; `unfold()`/`foldNow()`
a mano; `connection` da attaccare alla pagina. **Giu' piega, su apre, ovunque nella pagina** (la prima versione riapriva solo quando la lista finiva di contenuto sopra, e arrivare alla navigazione da meta' di una lista lunga voleva dire
tornare in cima). Cio' che impedisce lo sfarfallio e' una **soglia di commit asimmetrica**: **36 dp** per nascondere, **10 dp** per rivelare, azzerata a ogni cambio di direzione; solo il dito (`UserInput`: un fling riporta delta propri,
l'ultimo dei quali punta all'altro verso e riaprirebbe la barra a fine lancio). `locked` ferma la risposta e **scarta** lo scorrimento (non lo accumula: rilasciare il blocco non deve riprodurre una piega che nessuno chiede piu'): serve quando
qualcos'altro *misura* la barra (una superficie che cresce dal rettangolo di una banda, un elemento condiviso in volo) e una barra che si piega sotto sposterebbe cio' da cui parte il viaggio.
`FluidFoldingTabBarDefaults` e' un alias dei numeri della barra nuova: `OpenHeight`, `FoldedHeight`, `SoloFoldedHeight`, `Spacing`, `HorizontalMargin`, `BottomMargin`, `ContentInset`, `contentInsetWithAccessory(h = AccessoryHeight 50)`, `Inset` 4.

**Non usarli quando.** Scrivi codice nuovo: `FluidFloatingTabBar` con la sua connessione. `FluidBarFold` non ha un altro consumatore nel repo: serve soltanto a pilotare questo involucro (o controlli tuoi che seguano la piega).

**Versione.** `FluidFoldingTabBar` dalla 1.5.0; deprecata e delegata alla barra flottante dalla 2.0.0.

## FluidTabBar (deprecata)

**Cos'e'.** «La barra di prima, sotto il nome che le app gia' chiamano» (`FluidTabBar(items, selectedRoute, onSelect, backdrop, onReselect)`): oggi e' **`FluidFloatingTabBar` nella forma a elenco con una scroll connection `Never`**
(`rememberFluidFloatingTabBarScrollConnection(inlineBehavior = Never)`). Deprecata dalla 2.12.0 con `ReplaceWith` che compila; il KDoc diceva «rimozione prevista per la 3.0» ma **nella 3.0.4 c'e' ancora**.

**Cosa si vede cambiare** (il perche' di ogni riga e' nel KDoc di `FluidTabBar.kt`): **larghezza** — una capsula grande quanto le schede (fino a 88 dp ciascuna), centrata nella larghezza offerta, non una barra da lato a lato (con tre
schede su un telefono e' piu' stretta); **altezza** — 62 dp (60 fino alla 3.x) invece di 64, e un pavimento; **margini** — restano di chi la posa (ne' la vecchia ne' la nuova portano margine laterale, basso o barra di sistema); **non si ripiega** — nessuna pagina la guida,
resta aperta come prima; **accessibilita'** — dice `selected` ma ha perso `Role.Tab` e il tag `top_level_<rotta>` (un test cerca l'etichetta, `hasText`; il rail li tiene).
Il rail ([sopra](#fluidtabrail-fluidtabitem-fluidtabbardefaults-fluidtabbarpadding)) non ha equivalente e resta.

**Non usarla quando.** Sempre: `FluidFloatingTabBar(items = …, scrollConnection = rememberFluidFloatingTabBarScrollConnection(), …)`. Se la barra deve **restare** aperta, passa `inlineBehavior = Never`.

## FluidFloatingCapsule e FluidCapsuleAction

**Cos'e'.** Una capsula di vetro con delle azioni dentro, che galleggia sopra la pagina: la barra di una selezione multipla, un mini player, i comandi di una foto, un avviso con un tasto. Quattro app se l'erano scritta
da sole e ognuna sbagliava una cosa diversa (un bersaglio da 40 dp, un margine che ignorava la barra di sistema, la capsula che lasciava passare i tocchi al contenuto sotto): qui e' una cosa sola.

**Firma essenziale.**

```kotlin
FluidFloatingCapsule(
  modifier: Modifier = Modifier,                 // la allinea il chiamante: Modifier.align(BottomCenter) dentro un Box
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  visible: Boolean = true,                       // entra/esce con FluidFloatingCapsuleDefaults.enter/exit; falso non lascia niente in composizione
  placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.Free,
  fillWidth: Boolean = false,                    // fino a MaxWidth 560 dp, centrata: il content usa Modifier.weight(1f)
  onPhoto: Boolean = false,                      // pellicola `floatingTintOnPhoto` sopra una foto a tutta pagina
  avoidKeyboard: Boolean = false,
  minHeight: Dp = FluidFloatingCapsuleDefaults.Height, contentPadding: PaddingValues = ContentPadding,
  label: String? = null,                         // titolo del pannello per TalkBack («3 selezionate»)
  leading: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null,
  content: @Composable RowScope.() -> Unit,
)

FluidCapsuleAction(icon, contentDescription, onClick, enabled = true, destructive = false, tint = Color.Unspecified,
  onLongClick = null, size = 48.dp, grip = FluidPressGrip.HoldsFinger, selected: Boolean? = null)
```

**Uso** (da `CapsulesAndPartsDemo.kt`).

```kotlin
FluidFloatingCapsule(
  modifier = Modifier.align(Alignment.BottomCenter),
  backdrop = backdrop,                           // FluidScreen.overlay lo passa
  placement = FluidFloatingCapsulePlacement.AboveTabBar,
  visible = selecting, label = "3 selezionate",
  leading = { FluidCapsuleAction(Icons.Rounded.Close, "Annulla la selezione", onClick = { selecting = false }, tint = MaterialTheme.colorScheme.onSurface) },
) {
  Text("3 selezionate", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
  FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = {})
  FluidCapsuleAction(Icons.Rounded.Delete, "Elimina", onClick = {}, destructive = true)
}
```

**Comportamento.**

- **Una lastra sola**: il vetro sta sulla capsula (`glassSurface`, `Floating`, la pellicola della barra: le due non si distinguono), **mai sulle azioni**: `FluidCapsuleAction` e' un'icona piatta (26 dp presi dal posto, `FluidMetrics.CapsuleIconSize`; dalla 4.0 nell'inchiostro del testo, e nell'accento solo se `selected` o con un `tint`; `error` se `destructive`) che cresce sotto il dito
  (`FluidPressFeedback.Expand`); ogni lente in piu' e' un layer. Per un'azione che deve staccarsi dal resto (il «Fatto» di una selezione, il play) si mette nelle fessure un tasto di vetro vero (`FluidGlassIconButton`, `FluidGlassButton`): dentro la capsula
  campiona il suo materiale (vetro su vetro, dalla 2.13.0) e riceve il backdrop come `LocalGlassBackdrop`.
- **`placement`** — la capsula conosce lo schermo solo se glielo dici: `Free` (nessun margine, la posi tu), `ReplacingTabBar` (stessi margini e stessa altezza della barra: la pagina non cambia lo spazio che si e' lasciata), `AboveTabBar` (a `Spacing` 8 dp sopra la barra).
  Numeri (`bottomOffset` senza barra di sistema): `Free` 0, `ReplacingTabBar` **16**, `AboveTabBar` **86** (`ContentInset` 78 + 8). Spazio per la pagina (`contentInset(placement, capsuleHeight)`, **comprensivo della barra**): `Free` 0; `ReplacingTabBar` 16 + max(h, 62) = 78 con la capsula standard; `AboveTabBar` `contentInsetWithAccessory(h)` = 78 + h + 8 = **148** alla altezza standard 62.
  La barra di sistema la aggiunge `fluidCapsuleInsets`, e la `FluidScreen` per la pagina.
- **Tiene i tocchi**: un tocco in un punto della capsula che non e' un'azione non arriva alla card sotto (senza, toccare fra due icone apriva la foto dietro); i trascinamenti passano (una pagina che parte a scorrere da qui scorre lo stesso).
- Altezza minima `Height` = `OpenHeight` 62: un'azione da 48 + 7 + 7. `Spacing` 4 fra i pezzi, `ContentPadding` 6 ai lati e 7 sopra e sotto (`(Height - ActionSize) / 2`), `ActionSize` 48; `size` sotto 48 stringe il layout ma Compose estende comunque l'area toccabile a 48 (serve a quattro azioni su un telefono stretto).
  Un'azione spenta resta al suo posto ma velata a 0,38: una capsula che cambia larghezza quando un'azione non c'e' fa saltare il vicino.
- Entrata: sale da sotto con la molla del chrome (critica, niente rimbalzo: «una barra che rimbalza e' il segno piu' rumoroso di "non e' premium"»), uscita piu' svelta; i margini stanno **dentro** l'animazione, quindi sale da sotto il bordo dello schermo, non da dieci dp piu' in basso.
  Animazioni ridotte: solo la dissolvenza (160/120 ms).
- `grip`: `FluidPressGrip.YieldsToScroll` se la capsula sta sopra una lista o un pager che deve partire anche da li' (dalla 3.0).
- `fluidCapsuleInsets(placement, avoidKeyboard)` e' pubblico: non e' solo della capsula, vale per **qualunque** pezzo di vetro che galleggia in quel posto (un campo di ricerca al posto della barra). `avoidKeyboard` usa `imePadding` *dopo* la barra di sistema
  (l'inset della tastiera la comprende gia'); per `AboveTabBar` non ha senso (la barra sotto la tastiera non si vede).

**Trappole.** La capsula **non si allinea da sola**: serve `Modifier.align(...)` nel `Box` genitore. Va sopra la pagina, non dentro `content` (campionerebbe se stessa): nello slot `overlay` di `FluidScreen`, con il backdrop che riceve. Con `Free` la pagina non sa quanto spazio riservare:
niente `contentInset`. `contentInset(AboveTabBar)` **include gia' la barra**: non sommarlo a `ContentInset`.

**`FluidCapsuleAction(selected)`: l'azione che si accende e si spegne** (3.1.0). Il grassetto di una barra di formattazione sopra la tastiera, un filtro. `selected = null` (di serie) è l'azione semplice di sempre;
con un valore è un **interruttore**: ruolo `Switch` e lo stato che TalkBack dice (acceso/spento, nella lingua di sistema), e da accesa ha un **disco dell'accento** dietro l'icona (16% dell'accento). Il segno **non è solo di
colore**: un disco pieno e un'icona sola si distinguono anche in scala di grigi e a chi non vede i colori. Il disco sta dentro il bersaglio da 48 dp (3 dp d'aria) e cresce con l'icona sotto il dito; compare e sparisce con
`FluidMotion.flick()` (secco col movimento ridotto). `selected` è l'ultimo parametro e di serie nullo: nessuna chiamata esistente cambia. Trappola: lo stato lo tiene l'app; l'azione non si accende da sola.

**Versione.** Dalla 3.0.0.

## FluidSearchField

**Cos'e'.** Un campo di ricerca di vetro: la capsula con la lente a sinistra, il testo, la crocetta che svuota e — se serve — «3/12» con i due tasti per scorrere i risultati dentro la pagina. Tre app se l'erano scritto (una senza il fuoco automatico, una con il
`FluidTextField` dentro un vetro, cioe' un pozzetto opaco su una lastra, una con due X). Qui il campo e' **la lastra stessa**: lo stesso materiale della capsula e della barra (`fluidFloatingGlass`), quindi due pillole nella stessa fila non sono due grigi.

**Firma essenziale.**

```kotlin
FluidSearchField(
  query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
  placeholder: String? = null, labels: FluidSearchLabels = FluidSearchLabels(),
  onSearch: (() -> Unit)? = null,                  // il tasto «cerca» della tastiera; null: nasconde la tastiera
  expanded: Boolean = true, onExpandedChange: ((Boolean) -> Unit)? = null,   // chi lo passa rende il campo richiudibile (cerchio ↔ campo)
  navigation: FluidSearchNavigation? = null,       // FluidSearchNavigation(current da zero, total, onPrevious, onNext)
  autoFocus: Boolean = true, onPhoto: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
)
```

**Uso** (da `CapsulesAndPartsDemo.kt`).

```kotlin
// Un cerchio che si apre in campo:
FluidSearchField(query = query, onQueryChange = { query = it }, backdrop = stage,
  placeholder = "Cerca", expanded = expanded, onExpandedChange = { expanded = it }, autoFocus = false)

// Una ricerca dentro la pagina, con «3/12»:
FluidSearchField(query = q, onQueryChange = { q = it; current = 0 }, backdrop = stage, autoFocus = false,
  navigation = FluidSearchNavigation(current = current, total = total,
    onPrevious = { if (total > 0) current = (current - 1 + total) % total },
    onNext = { if (total > 0) current = (current + 1) % total }))
```

**Comportamento.**

- *Si apre da un cerchio.* Con `onExpandedChange` il campo e' richiudibile: `expanded` falso e' un **cerchio** di vetro `Height` 52 con la lente, che toccato si allarga fino a tutto lo spazio con la molla di `FluidMotion.standard()` — lo stesso vetro per
  tutta l'apertura, senza un cambio di superficie a meta' — e prende il fuoco con la tastiera. Aperto ha una X a destra; «indietro» (`BackHandler`) fa lo stesso. Dove sta il cerchio (a destra, al centro) lo decide chi lo contiene: cresce dal lato opposto a quello
  a cui e' allineato. **La lente non si sposta di un pixel** (`IconInset` = `(Height - IconSize)/2` = 15 dp: al centro del cerchio chiuso, margine del testo da aperto). Il contenuto compare dal 20% dell'apertura e se ne va per primo chiudendo (`fluidSearchContentAlpha`).
  Nessun `onExpandedChange`: sempre aperto, `expanded` ignorato. Senza animazioni ridotte; con, apertura di colpo. Aptica `Open`; il cerchio chiuso e' tutto un tasto (`FluidPressFeedback.Expand`, la crescita sta *dentro* il vetro).
- *Il fuoco e la tastiera.* Con `autoFocus` il campo prende il fuoco quando si apre o compare (`runCatching`: il nodo del fuoco puo' non essere ancora collegato al primo istante); chiudendosi fuoco e tastiera vanno via. Il fuoco si toglie **solo quando il campo si chiude**, non quando compare gia' chiuso: un
  cerchio che entra in una pagina con un altro campo in primo piano non gli strappa il cursore. «Cerca» sulla tastiera chiama `onSearch`; senza, nasconde la tastiera e lascia il testo (la ricerca era gia' live). Con `navigation` `onSearch` non nasconde la tastiera (Invio di solito e' «avanti»).
- *Il testo e' del motore* (`bodyLarge`, cursore d'accento): non e' `FluidTextField`, che porta un pozzetto — un incavo dentro un vetro e' un rettangolo appiccicato sopra la lastra.
- *Il contatore* `fluidSearchCounter`: nulla a query vuota; «3/12» (indice da zero + 1 su totale, tenuto entro i limiti: mai «13/12»); «Nessun risultato» se c'e' una parola e `total == 0` (frecce spente). Area viva `Polite` per TalkBack («3 di 12»). Cifre tabulari.
  Bersagli dei comandi interni 40 dp (`ActionSize`: quattro in 332 dp non ci stanno a 48).
- *Il fondale* (3.0.1): di serie il canvas della pagina se c'e' (come chip, segmentato, slider), altrimenti la chrome in scope. Prima era sempre la chrome, e su una pagina ambientale il campo rifrangeva il corpo invece della lavata sotto di lui. In una barra il canvas non c'e'.
- `FluidSearchLabels(search, clear, close, previous, next, noResults, counterSpoken)`: le parole, tutte insieme (italiano di serie; un'app tradotta ne passa **una**). `ExpandedFallbackWidth` 320 dp se il genitore non da' larghezza (dentro uno scorrimento orizzontale).
- Tiene i tocchi che non ha preso (un tocco sul margine del vetro non arriva alla card sotto).

**Trappole.** `onExpandedChange` **non svuota** la ricerca: lo decide l'app. Si posa con `fluidCapsuleInsets` se prende il posto della barra, e sale con la tastiera con `avoidKeyboard` (che e' della capsula, non del campo: metti `fluidCapsuleInsets(placement, avoidKeyboard = true)` sul `Modifier`).
Un campo sempre in pagina vuole `autoFocus = false`, o una tastiera che sale da sola copre i risultati. Nello slot `searchBarContent` della tab bar **non** e' un campo nudo (porta il suo pannello).

**Versione.** Dalla 3.0.0; sfondo di serie sul canvas 3.0.1.

## Token, costanti, Defaults

| oggetto | valori (dp se non detto) |
|---|---|
| `FluidFloatingTabBarDefaults` | `TabWidth` 88 (tetto), `OpenHeight` 62 (pavimento, `FluidMetrics.TabBarHeight`), `FoldedHeight` 52, `FoldedSoloHeight` 64, `Spacing` 8, `BottomMargin` 16, `HorizontalMargin` 14, `ContentInset` 78, `contentInsetWithAccessory(h)` = 78 + h + 8; `colors()` (`surfaceContainerHigh`), `shapes()` (`FluidCapsuleShape`), `sizes()` (padding barra 4, scheda in linea 10, scheda aperta 6, `componentSpacing` 8, `tabSpacing` 0), `elevations()` 6 / 12, `labelStyle()` = `labelMedium` centrato |
| `FluidFloatingTabBarGlass` | `blurRadius` = `DefaultGlassBlurRadius` 2,5 (significato doppio), `puckColor` non specificato = `primary`, `puckOpacity` 0,26 |
| `rememberFluidFloatingTabBarScrollConnection` | `scrollThreshold` 50, `expandThreshold` 8, `inlineBehavior` `OnScrollDown` (`Never`, `OnScrollUp`), `initialIsInline` falso |
| token di movimento della barra (`FluidMotion`) | `GooeyMillis` 300, `GooeyPulse` (picco 12 dp a meta'), `PuckEnter` 300 ms, `FoldCrossFade`, `TabTugReturn`, `TabPuckLeadMillis` 160, `TabBarFold` (0,9, rigidezza media-bassa) |
| costanti interne che fanno il comportamento | etichetta ≥ 0,75; inchiostro ≥ 3:1 (`PuckInkMinContrast`); pillola premuta × 78/56, icona × 1,12; scheda in linea 10 dp di padding; cerchio minimo 48 |
| `FluidFloatingCapsuleDefaults` | `Height` 62, `ActionSize` 48, `Spacing` 4, `ContentPadding` 6 ai lati e 7 sopra e sotto, `MaxWidth` 560; `bottomOffset(placement)`, `contentInset(placement, h)`, `enter(reduced)`, `exit(reduced)` |
| `FluidSearchFieldDefaults` | `Height` 52, `IconSize` 22, `IconInset` 15, `ActionSize` 40, `ExpandedFallbackWidth` 320 |
| `FluidTabBarDefaults` (rail e barra storica) | `Height` 64, `RailWidth` 84, `HorizontalMargin` 14, `BottomMargin` 16, `ContentInset` 80 |
| `FluidFoldingTabBarDefaults` | alias dei numeri della barra flottante; `AccessoryHeight` 50, `Inset` 4 |
| `FluidBarFold` | commit 36 dp per nascondere, 10 dp per rivelare; piegata oltre 0,5 |

## Cosa e' verificato dai test

- `FluidFloatingTabBarScrollTest` (engine-ui, 7): su una pagina che non scorre il dito non ripiega; una che scorre davvero ripiega oltre la soglia; **conta solo la parte che scorre**; tirata giu' in cima si riapre anche se l'elastico del bordo consuma il resto; `locked` non si muove e
  un cambio di pagina chiude lo scorrimento senza toccare la piega; un cambio di pagina la riapre e chiude lo scorrimento. `FluidTabBarRouteTest` (sample, Robolectric, 3): un cambio di rotta dell'host riapre la barra, un cambio di scheda pure, e **non si riapre da sola** senza un cambio.
- `FluidFloatingTabBarLayoutTest` (10): cinque schede su un telefono si adattano senza cerchio; con spazio resta il tetto; il cerchio e il suo spazio escono dalla quota delle schede; niente misura = tetto; mai una scheda negativa; etichetta che sta/poco larga (ridotta esattamente quanto serve)/disperata (smette di ridursi); **l'`ContentInset` dichiarato copre l'altezza che la barra mantiene davvero**.
  `FluidFloatingTabBarBlurTest` (8): pannello dell'app = esattamente il raggio dato (regressione 2.12), nessun moltiplicatore di ruolo, indipendente dalla qualita'; pannello della barra = esattamente `glassSurface` a ogni taglia e qualita'; la risoluzione della riga nascosta segue il raggio usato.
  `FluidPuckInkTest` (5): inchiostro leggibile su tutti gli accenti, su entrambi i lati, tinta conservata.
  `FluidFloatingTabBarInkTest` (6): il ramo di ripiego (nessun colore deciso) legge sulla pellicola del vetro in chiaro, scuro e nero (>= 4,5:1 l'etichetta, >= 3:1 l'icona, sulle pagine del tema e su un grigio di mezzo); un colore deciso dall'app resta; il nero puro e' letto come «non deciso».
- `AllGlassTest` (sample): la barra con la banda si mostra e un tocco su una scheda suona `Tap` e naviga; il cerchio risponde con `Tap` una volta; la scheda ripiegata risponde con `Tap` e riapre; il rail risponde al tocco e il secondo tocco e' ancora un reselect.
- `FluidFloatingCapsuleTest` (10, puri): margini e inset di `Free`, `ReplacingTabBar`, `AboveTabBar` (la capsula sta a `Spacing` sopra la barra; l'inset della pagina e' esattamente il suo bordo alto e coincide con la regola della banda), l'altezza di serie tiene un'azione piena col suo padding, il bersaglio non scende sotto 48.
  `FluidCapsulesAndPartsTest` (Robolectric): azione che scatta e suona il click, azione spenta, capsula nascosta che non lascia niente in composizione, tocchi tenuti, cerchio che si apre con l'aptica di apertura, campo che scrive/svuota/chiude, fuoco all'arrivo, ricerca nella pagina con contatore, «nessun risultato» che spegne le frecce.
- `FluidSearchFieldTest` (10, puri): contatore (vuoto, posizione da uno su totale, niente risultati, mai oltre il totale), larghezza dal cerchio allo spazio pieno e sempre entro i due estremi, contenuto che entra tardi ed esce per primo, la lente al centro del cerchio, parole di serie in italiano.
- `FluidTabBarExtensionsTest` (sample, Robolectric, 15; 3.1.0): il pallino si legge sulla zona a barra aperta una volta sola; a barra ripiegata il segno non c'è, né disegnato né detto; il numero dice la quantità vera oltre il «99+»,
  anche in inglese; una frase dell'app vince e un numero a zero non dice niente; il segno non è un bersaglio e non cambia le zone, anche nella forma a DSL; il cerchio accanto alla pillola è un pulsante, non una zona, e non cambia la zona
  scelta (anche a barra ripiegata); col movimento ridotto la piega è già chiusa dopo quattro fotogrammi e un tocco sceglie subito, a scala normale no; il rail porta l'azione in testa e il segno, e senza intestazione è quello di prima.
- **Non coperti**: `searchMode`/`searchBarContent`, le due bande insieme come elemento condiviso, la pillola trascinata (gesto e tick), la piega continua di `FluidFoldingTabBar`/`FluidBarFold`, `FluidTabRail` in un layout largo, tutti i fotogrammi (da guardare in mano).

## Dove guardare

Percorsi sotto `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`:

- `FluidFloatingTabBar.kt` (3181 righe) — in ordine: la funzione pubblica a DSL (righe ~190–490: i tre stati e il materiale), `FluidFloatingTabBarGlass` e `FluidFloatingTabBarScrollConnection` (~510–760: soglie, `locked`, `morphing`,
  `scrolling`, `routeChanged`), la forma a elenco (~830), `InlineBar`/`InlineTab` (~970–1280), `ExpandedBar` e `SearchExpandedBar` (~1320–1590), `ExpandedTabs` con la pillola, la riga nascosta e le molle (~1690–2570), i conti del raggio e `labelScale`/`legibleInk`/`fittedTabWidth` (~2575–2840),
  la scope e i `Defaults` (~2905–3181). Vendored a quattro spazi.
- `FluidTabBar.kt` — `FluidTabItem`, `FluidTabBarDefaults`, `FluidTabBar` (deprecata), `FluidTabRail`, `fluidTabBarPadding`. `FluidFoldingTabBar.kt` — l'involucro deprecato, `FluidBarFold`, `FluidFoldAlignment`.
- `FluidFloatingCapsule.kt` — capsula, azione, `fluidCapsuleInsets`, `Defaults`, il vetro condiviso con il campo (`fluidFloatingGlass`). `FluidSearchField.kt` — campo, etichette, navigazione, conti puri.
- `FluidMotion.kt` — i token (`GooeyMillis`, `TabPuckLeadMillis`, …). `FluidGooey.kt` — la sfocatura gommosa. Esempi: `sample/.../GalleryActivity.kt` (la shell con la barra), `catalog/AllGlassDemo.kt` (banda, rail),
  `catalog/CapsulesAndPartsDemo.kt` (capsula, ricerca).
