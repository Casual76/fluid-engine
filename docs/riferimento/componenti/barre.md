<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/barre.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Barre di navigazione e di azione

Questa famiglia è tutto ciò che galleggia di vetro in fondo allo schermo, o al fianco di una pagina larga, e porta
altrove o fa qualcosa:

- la **barra in basso dichiarata**: i comandi della pagina con `FluidToolbarPlacement.BottomBar`, sul piano flottante;
- la **capsula di azioni** (`FluidFloatingCapsule`), che la pagina posa da sé;
- la **ricerca**: dichiarata (`FluidSearchSpec`, che va dal tasto al campo) o come componente (`FluidSearchField`);
- la **tab bar** (`FluidFloatingTabBar`), con la piega, la bolla di selezione, il lettore, il tondo della ricerca e la
  scheda prominente;
- la **navigazione adattiva** (`FluidAdaptiveNavigation`): la stessa lista di schede che diventa tab bar, rail
  (`FluidTabRail`) o tab bar in alto che si apre in barra laterale; `fluidBackgroundExtension` continua la pagina sotto
  la colonna;
- due barre storiche deprecate, oggi involucri della tab bar (`FluidTabBar`, `FluidFoldingTabBar`).

La barra *superiore* (titolo, indietro, azioni) è di `FluidScreen`, in [schermata.md](schermata.md); i suoi tasti, e quelli
in basso, si **dichiarano** con `toolbar { … }`, in [toolbar.md](toolbar.md); la barra di un host di pagine è in
[pagine.md](pagine.md). Catalogo delle firme: [../api/barre.md](../api/barre.md).

**Il modello.** Chi usa una barra decide dove sta e quanto spazio le lascia la pagina; la barra decide come si muove.
Ogni barra è vetro del ruolo `Floating` con la pellicola `GlassDefaults.floatingTint()`: due capsule nella stessa fila
non sono due grigi. Col materiale Liquid ogni pezzo è un vetro **del piano flottante** (`FluidGlassContainer`, F3): quando
una barra cambia forma il piano interpola la sagoma, tiene la cattura del fondale per tutto il viaggio e non ricattura a
ogni fotogramma. Il contenuto che cambia identità (un glifo che diventa un altro, il segnaposto che entra) passa con la
sfocatura uniforme del piano, la stessa regola dello scambio di icone in alto. La barra non porta mai dentro di sé il
proprio margine.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| i comandi della pagina in basso, che cambiano con la pagina | `toolbar { group("…", placement = FluidToolbarPlacement.BottomBar) { … } }` |
| una capsula di azioni posata a mano (selezione multipla, comandi di una foto) | `FluidFloatingCapsule` con `FluidCapsuleAction` |
| la ricerca della pagina, in basso o in alto, che si apre dal tasto | `toolbar { search(FluidSearchSpec(…)) }` o `FluidScreen(search = …)` |
| un campo di ricerca nella pagina, anche con «3/12» | `FluidSearchField` |
| la navigazione principale a 3–5 destinazioni | `FluidFloatingTabBar(items, selectedRoute, onSelect, scrollConnection, backdrop)` |
| schede composable, un lettore sopra la fila, il tondo della ricerca, una prominente | `FluidFloatingTabBar(selectedTabKey, scrollConnection, …) { tab(…); standaloneTab(…) }` |
| che la tab bar si ripieghi mentre la pagina scorre | `rememberFluidFloatingTabBarScrollConnection()` + `Modifier.nestedScroll(…)` su un antenato delle pagine |
| che non si ripieghi mai | `inlineBehavior = FluidFloatingTabBarInlineBehavior.Never` |
| tenerla ferma (ricerca in corso, una finestra che nasce dalla banda) | `scrollConnection.locked = true` |
| un pallino o un numero su una zona | `FluidTabItem(badge = FluidTabBadge(count))` o `tab(…, badge = …)` |
| un'azione primaria («Scrivi») sempre visibile, o in testa al rail | `standaloneTab(…)` senza ruolo; sul rail `FluidTabRail(header = { … })` |
| la stessa navigazione su telefono e tablet | `FluidAdaptiveNavigation(items, route, onSelect) { insets -> … }` |
| una barra laterale chiamata a mano (anche su telefono) | `FluidSidebar` |
| «Non così»: la barra dentro `Scaffold(bottomBar = …)` | lo `Scaffold` toglie spazio alla pagina; la barra deve galleggiare **sopra**, perché il contenuto le scorra sotto sfocato |
| «Non così»: `FluidTabBar`, `FluidFoldingTabBar` + `FluidBarFold` | deprecate: sono `FluidFloatingTabBar` sotto un altro nome |

## Regole comuni

| cosa | regola |
|---|---|
| **icone** | l'app mette l'icona, la taglia arriva dal posto: 28 dp in una scheda (`FluidMetrics.TabBarIconSize`), 26 nella capsula e nella barra in basso, 0,6 del vetro in un tasto di vetro. L'inchiostro è quello del testo; l'accento dice la scelta. `FluidIcons` è il set dell'engine, `FluidIcons.Filled.*` le gemelle piene; un `Icon(…)` con una taglia scritta resta com'è |
| **posa della tab bar** | la barra non porta margine laterale, margine basso né barra di sistema: `Box(Modifier.align(BottomCenter).navigationBarsPadding().padding(horizontal = HorizontalMargin, vertical = BottomMargin))`, 14 e 16 dp in `FluidFloatingTabBarDefaults` |
| **spazio della pagina** | `FluidFloatingTabBarDefaults.ContentInset` = 62 + 16 = **78 dp**, senza barra di sistema; con una banda `contentInsetWithAccessory(h)` = 78 + h + 8. Si dà una volta, alla shell: `ProvideFluidChrome(bottomInset = …)`. La barra in basso dichiarata e la ricerca in basso si posano sopra questo spazio da sole |
| **il fondale** | `backdrop = chromeController.activeBackdrop.value ?: fallback`. Senza `backdrop` la tab bar è una capsula piatta, senza lente |
| **una vibrazione per momento** | un `Tap` al tocco di una scheda o del tondo; un `Tick` per scheda attraversata trascinando la bolla, niente al rilascio; `Open` quando un campo richiudibile si apre |
| **movimento ridotto** | tutte le barre leggono `LocalFluidMotionPolicy`: la piega, la bolla, la conversione in barra laterale e il campo vanno alla posa d'arrivo in un fotogramma; la capsula entra ed esce in sola dissolvenza (160/120 ms) |
| **sopra la tastiera** | `Modifier.fluidCapsuleInsets(placement, avoidKeyboard)` per un vetro posato a mano; la ricerca dichiarata segue da sé l'inset animato dell'IME |

## La barra in basso dichiarata

Una pagina mette i suoi comandi in basso dichiarandoli con `FluidToolbarPlacement.BottomBar`: è la stessa DSL della barra
in alto, con lo stesso modello, la stessa memoria delle identità e lo stesso scambio dei glifi
([toolbar.md](toolbar.md), «La barra in basso»). La sede è una sola per tutto l'host: in un `FluidPageHost` fra
«Raccolta» e «Foto» il più che c'è in tutte e due resta fermo, i comandi che mancano all'arrivo escono stringendo il
gruppo, quelli nuovi entrano per lente. Uno `spacer()` fisso stacca un comando dal gruppo, e il vetro si divide col collo;
toglierlo lo riunisce.

```kotlin
FluidScreen(title = if (detail) "Foto" else "Raccolta", onBack = back, toolbar = {
  group("comandi", placement = FluidToolbarPlacement.BottomBar) {
    button("elenco", if (detail) FluidIcons.Search else FluidIcons.FormatListBulleted,
      if (detail) "Torna alla raccolta" else "Mostra foto", onClick = { photo = !detail })
    flexibleSpacer()
    if (!detail) {
      button("cuore", FluidIcons.Favorite, "Preferito", onClick = { favourite = !favourite }, selected = favourite)
      button("condividi", FluidIcons.IosShare, "Condividi", onClick = { shared = !shared }, selected = shared)
      if (separate) spacer()
    }
    button("aggiungi", FluidIcons.Add, "Cambia gruppo", onClick = { separate = !separate })
  }
}) { /* righe */ }
```

Ogni posto misura 48 dp con l'icona da 26 (in Apple 47,8 pt). La sede sta sopra la navigazione di sistema e, se l'app
ha dato l'inset della tab bar, sopra la tab bar; la lista riserva lo spazio per l'ultima riga. Col ritiro della barra
(`barMinimize`) i comandi in basso scendono fuori dallo schermo con la stessa corsa di quelli in alto.

## La capsula di azioni

`FluidFloatingCapsule` è la capsula che la pagina posa da sé: la barra di una selezione multipla, un mini lettore, i
comandi di una foto, un avviso con un tasto. È **una lastra sola** sul piano: quando cambiano i figli il pannello anima
la larghezza (conservando il centro, se il genitore la centra) e il contenuto segue il layout d'arrivo; le azioni non
sono vetri, sono icone piatte (`FluidCapsuleAction`, 26 dp) che crescono sotto il dito. Il cambio di glifo di
un'azione passa con lo scambio sfocato.

```kotlin
FluidFloatingCapsule {
  FluidCapsuleAction(if (expanded) FluidIcons.Check else FluidIcons.Edit, "Modifica la capsula",
    onClick = { expanded = !expanded })
  if (expanded) FluidCapsuleAction(FluidIcons.Favorite, "Contrassegna", onClick = { favourite = !favourite },
    selected = favourite)
}
```

| parametro | cosa fa |
|---|---|
| `placement` | `Free` (nessun margine, la posi tu), `ReplacingTabBar` (margini e altezza della tab bar), `AboveTabBar` (8 dp sopra la tab bar). `bottomOffset` senza barra di sistema: 0, **16**, **86**; `contentInset(placement, h)`, comprensivo della barra: 0, **78**, **148** con l'altezza di serie |
| `visible` | entra dal basso con la molla della chrome, senza rimbalzo, ed esce più svelta; falso non lascia niente in composizione |
| `fillWidth`, `onPhoto`, `avoidKeyboard` | fino a 560 dp col contenuto a `weight`; pellicola per una foto a tutta pagina; sale sopra la tastiera |
| `label` | il titolo del pannello per TalkBack («3 selezionate») |
| `FluidCapsuleAction(selected = …)` | un interruttore: ruolo `Switch` e disco dell'accento al 16% dietro l'icona; `null` di serie è l'azione semplice |
| `grip` | `FluidPressGrip.YieldsToScroll` sopra una lista o un pager che deve partire anche da lì |

Altezza minima 62 dp (un'azione da 48 più 7 sopra e sotto), 4 dp fra i pezzi, 6 ai lati. Un'azione spenta resta al suo
posto velata a 0,38: una capsula che cambia larghezza quando un'azione si spegne fa saltare la vicina. La capsula
**tiene i tocchi** che non sono di un'azione (toccare fra due icone non apre la foto dietro) e lascia passare i
trascinamenti. Per un'azione che deve staccarsi (il «Fatto» di una selezione) si mette nelle fessure un tasto di vetro
vero, `FluidGlassIconButton`: campiona il materiale della capsula.

**Trappole.** La capsula non si allinea da sola: `Modifier.align(…)` nel `Box` genitore. Va sopra la pagina, nello slot
`overlay` di `FluidScreen`, non nel contenuto (campionerebbe se stessa). `contentInset(AboveTabBar)` comprende già la
tab bar: non va sommato a `ContentInset`.

## La ricerca

### Dichiarata, dal tasto al campo

La pagina dichiara la ricerca una volta, `search(FluidSearchSpec(…))` nella toolbar o `FluidScreen(search = …)`; la
toolbar ha la precedenza. `query` e `onQueryChange` restano dell'app. Il campo è un vetro del piano con un'identità
stabile: la lente da 44 dp si allunga nella capsula del campo, alta 40 dp (la zona di tocco resta 44), mentre i vicini
le fanno posto raccogliendosi in «Altro»; il contenuto sta al layout d'arrivo e il segnaposto, la X e gli ambiti entrano
sfocati e scalati, nitidi verso la fine del moto. Un solo editor attraversa tutte le pose, anche quando cambia quota.

| `placement` | a riposo | attiva |
|---|---|---|
| `Automatic` | in basso sotto 600 dp di finestra, in alto a destra da 600 dp | segue la sede risolta |
| `BottomBar` | sopra la navigazione e l'eventuale tab bar | sale sopra l'IME seguendone l'inset animato |
| `TopBar` | sul lato finale della testata | resta in alto inglobando i vicini; con `expansion = AboveKeyboard` scende sopra l'IME |
| `Principal` | sotto il titolo | segue il ritiro della testata |

`behavior = Automatic` parte dal campo con al più due vicini e dal tasto con più di due; `CollapseToButton` è la ricerca
**minimizzata** (sempre un tasto a riposo), `AlwaysVisible` il campo fisso. `scopes` sta sulla riga dell'editor (in
linea, come in iOS 27), `suggestions` accanto alla barra. La X o il ritorno chiudono conservando la query. Con
`purpose = Input` è un campo di inserimento generico: la matita al posto della lente, «Fine» sulla tastiera, `onSearch`
come conferma. `active` e `onActiveChange`, passati insieme, danno all'app l'apertura.

```kotlin
toolbar = {
  search(FluidSearchSpec(query, { query = it }, placement = FluidSearchPlacement.BottomBar, prompt = "Cerca nelle foto",
    scopes = { Text(if (favouritesOnly) "Preferite" else "Tutte", Modifier.clickable { favouritesOnly = !favouritesOnly }) },
    suggestions = { if (query.isNotBlank()) Text("Cerca «$query» nella raccolta") }))
  group("comandi", placement = FluidToolbarPlacement.BottomBar) { /* … */ }
}
```

Sopra un fondo carico il campo aperto si vela secondo l'inchiostro che il piano gli dà (nero al 40% sotto un inchiostro
chiaro, bianco al 16% sotto uno scuro), in proporzione all'apertura: sul ritaglio carico di prova del banco il testo
tiene almeno 7,9:1 in chiaro e 5,3:1 in scuro. Sopra la tastiera il campo non la rifrange (è un'altra finestra). Il dettaglio delle
sedi, del ritiro con `Principal` e dell'host è in [toolbar.md](toolbar.md), «La ricerca».

### `FluidSearchField`, il campo nella pagina

Un campo di ricerca di vetro: la capsula con la lente, il testo, la X che svuota e, se serve, «3/12» con le frecce. Il
campo **è** la lastra (`fluidFloatingGlass`), non un pozzetto dentro un vetro.

```kotlin
FluidSearchField(query = query, onQueryChange = { query = it }, backdrop = stage, placeholder = "Cerca nelle foto",
  expanded = expanded, onExpandedChange = { expanded = it }, autoFocus = false)

FluidSearchField(query = q, onQueryChange = { q = it; current = 0 }, backdrop = stage, autoFocus = false,
  navigation = FluidSearchNavigation(current = current, total = total,
    onPrevious = { if (total > 0) current = (current - 1 + total) % total },
    onNext = { if (total > 0) current = (current + 1) % total }))
```

- Con `onExpandedChange` è richiudibile: la lente da 44 dp si apre in un campo alto 40, sul piano, con la cattura tenuta;
  segnaposto e chiusura entrano sfocati. Il campo sempre aperto è alto 52 dp. Il modificatore del chiamante decide il
  lato da cui cresce.
- `autoFocus` prende il fuoco all'apertura; il fuoco si toglie solo quando il campo si chiude. «Cerca» sulla tastiera
  chiama `onSearch`; senza, nasconde la tastiera e lascia il testo.
- Il contatore: niente a query vuota, «3/12» entro i limiti, «Nessun risultato» con le frecce spente; TalkBack «3 di
  12». Comandi interni da 40 dp.
- Di serie il fondale è il canvas della pagina, se c'è; altrimenti la chrome.
- Nello slot `searchBarContent` della tab bar prende misura, identità e materiale del tondo:
  `searchBarContent = { field -> FluidSearchField(query, { query = it }, field) }`.

**Trappole.** `onExpandedChange` non svuota la ricerca. Un campo sempre in pagina vuole `autoFocus = false`, o la
tastiera copre i risultati. Al posto della tab bar si posa con `fluidCapsuleInsets(placement, avoidKeyboard = true)`.

## La tab bar

`FluidFloatingTabBar` è la barra di navigazione dell'app Fluidify portata nell'engine (da *FloatingTabBar* di Elyes
Mansour, con la pillola liquida di Kyant0), a quattro spazi come l'originale e con le modifiche locali segnate
(`LOCAL CHANGE`). Ha due forme.

```kotlin
// A elenco: le destinazioni come dati.
FluidFloatingTabBar(items = tabs, selectedRoute = route, onSelect = { route = it.route },
  scrollConnection = rememberFluidFloatingTabBarScrollConnection(), backdrop = backdrop,
  onReselect = { scrollToTop.request() })

// A DSL: tre schede, il tondo della ricerca, la prominente e il lettore.
FluidFloatingTabBar(
  selectedTabKey = selected, scrollConnection = scroll, backdrop = backdrop,
  modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 432.dp).padding(horizontal = 14.dp, vertical = 16.dp),
  inlineAccessory = { band, _ -> Box(band, contentAlignment = Alignment.Center) { Text("In ascolto") } },
  expandedAccessory = { band, _ -> Box(band.fillMaxWidth().height(46.dp), contentAlignment = Alignment.Center) { Text("In ascolto") } },
  searchActivation = FluidTabSearchActivation.SearchTabSelection,
  searchBarContent = { field -> BasicTextField(value = query, onValueChange = { query = it }, modifier = field.padding(12.dp)) },
) {
  tab("Oggi", { Text("Oggi") }, { FluidIcon(FluidIcons.CalendarMonth, null) }, { selected = "Oggi" })
  tab("Raccolta", { Text("Raccolta") }, { FluidIcon(FluidIcons.Filled.PhotoLibrary, null) }, { selected = "Raccolta" })
  standaloneTab("Cerca", { FluidIcon(FluidIcons.Search, "Cerca") }, FluidTabRole.Search,
    onClick = { selected = "Cerca" }, title = { Text("Cerca") })
  standaloneTab("Preferiti", { FluidIcon(FluidIcons.Filled.Star, "Preferiti") }, FluidTabRole.Prominent,
    onClick = { selected = "Preferiti" })
}
```

### Tre pose e un campo

La barra ha tre pose: aperta, ripiegata e ricerca attiva. Col materiale Liquid e il pannello di serie (da Android 13)
**gruppo, tondo e lettore sono tre vetri dello stesso piano**, con lo stesso riferimento al fondale: il piano misura solo
la posa d'arrivo e li porta ciascuno con la sua sagoma, la sua luce e la sua ombra, senza ricatture durante il cambio.

- **La piega** (234 ms, `FluidMotion.FloatingTabFold`, dalla piega di Musica): il gruppo si stringe nel tondo a sinistra;
  l'etichetta della scheda scelta resta leggibile nei primi 67 ms e si spegne fra 67 e 100, sempre sotto il glifo; il
  glifo scelto va dalla sua cella al centro del tondo e resta nitido, mentre le altre icone e i titoli si sfocano fino a
  6 dp. Il lettore scende fra i due tondi (`FluidMotion.TabBarPlayer`) e il suo contorno passa davanti ai due vetri.
- **La ricerca** (133 ms, `FluidMotion.TabBarSearch`, dalla ricerca di Foto): il gruppo si raccoglie nel tondo della
  scheda corrente mentre il tondo della ricerca si allunga nel campo. La pagina e la tastiera sono dell'app; per la
  pagina che sbianca c'è `FluidMotion.FloatingTabSearchPage`.
- Con un pannello disegnato dall'app (`tabBarContentModifier`) o col materiale Classic resta la piega di prima, a elementi
  condivisi con l'impulso gommoso da 12 dp. Per confrontare le due pieghe sulla stessa build c'è il tag diagnostico
  `log.tag.FluidTabBarGooey=DEBUG`, letto alla composizione della barra.

### Quando si ripiega

La decisione è di `FluidFloatingTabBarScrollConnection`, che l'app attacca a un **antenato delle pagine** con
`Modifier.nestedScroll(connection)` e passa alla barra per argomento (la barra è sorella delle pagine, non genitrice:
attaccata alla barra non sente scorrere niente).

| | |
|---|---|
| `inlineBehavior` | `OnScrollDown` (di serie): si ripiega scorrendo in giù, si riapre risalendo; `OnScrollUp`; `Never` |
| soglie | ripiega dopo **50 dp** di scorrimento netto in un verso (`scrollThreshold`), si riapre dopo **8 dp** in su (`expandThreshold`): asimmetriche di proposito, perché la barra che torna è quella che si cerca |
| cosa conta | ciò che il contenuto ha davvero scorso (`consumed` in `onPostScroll`): una pagina che non scorre non ripiega la barra; tirata giù in cima la riapre |
| cambio di pagina o di scheda | l'host delle pagine (`FluidNavHost`, `FluidPageHost`) e la barra stessa chiamano `routeChanged()`: fine dello scorrimento e barra aperta. `settle()` + `expand()` a mano non servono più |
| `locked` | lo scorrimento non tocca la barra: per la ricerca in corso (ripiegare toglierebbe il campo) e per chi misura la barra |
| `isInline`, `expand()`, `inline()` | lo stato, e i due comandi per la piega comandata |
| `scrolling` | vero mentre la pagina sotto si muove: il vetro tiene l'ultima cattura invece di rifare sfocatura e lente a ogni fotogramma |
| `morphing` | vero nei fotogrammi in cui la barra cambia forma, per il pannello che l'app costruisce da sé |

`rememberFluidFloatingTabBarScrollConnection(initialIsInline, scrollThreshold, expandThreshold, inlineBehavior)` ha chiave
su questi valori: cambiare `initialIsInline` ricrea la connessione e ne azzera lo stato.

**Ripiegata** si vede solo la scheda scelta (52 dp), più la banda e il tondo se ci sono; da sola sale a 64 dp. Se la
scelta è il tondo, la barra mostra l'ultima scheda normale davvero scelta. Un tocco sulla scheda ripiegata riapre la barra
e chiama anche il suo `onClick` (nella forma a elenco `onReselect`). Tenendo premuto il glifo e trascinando si scorre fra
le destinazioni e si sceglie al rilascio. `foldAlignment` (di serie `Start`) dice da che parte si raccoglie: ripiegata è
l'unico bersaglio, e dove si posa decide se il pollice ci arriva; aperta il gruppo è sempre centrato.

### La fila aperta e la bolla

- Le schede sono larghe uguali; `TabWidth` 88 dp è un **tetto**: su un telefono stretto `fittedTabWidth` divide lo spazio
  che resta dopo il tondo. L'etichetta è una riga (11 sp, `labelMedium`) che si scala fino a 0,75 prima di tagliarsi.
- **La bolla** è trascinabile: si assesta sulla scheda più vicina e naviga solo dopo un vero trascinamento; tira fino a
  4 dp nel verso del dito e rientra con una molla sottosmorzata (0,5), la gelatina di slider e interruttore. Al tocco di
  una scheda parte subito e la pagina aspetta `TabPuckLeadMillis` (160 ms) per costruirsi; le pagine delle schede vanno
  tenute vive o precomposte ([../regole.md](../regole.md)).
- **Premuta, la bolla Liquid diventa lente**: la sua scala segue la pressione ottica (×78/56 della cella, circa +39%
  della larghezza, formata a 133–167 ms), rilegge i glifi dove li attraversa e aumenta la rifrazione fino alla
  dispersione; al rilascio torna in circa 80 ms. A riposo e premuta campiona lo stesso fondale con la stessa ricetta.
  Rientro di 0,076 H, altezza circa 0,85 H; sul telefono di R5 M4 la pillola è 219 livelli su una pagina a 238. Il
  colore della scelta usa per l'icona il contrasto del testo (4,5:1 sulla pellicola). Senza fondale resta il disco
  piatto; la bolla Classic tiene la riga nascosta tinta.
- Icone ed etichette prendono il colore dell'app; se nessuno l'ha deciso (`LocalContentColor` di serie), `onSurface`.
  Un'app che fornisce davvero il nero puro è letta come «non deciso».

### La banda, il tondo e la prominente

- **La banda** (`inlineAccessory`, `expandedAccessory`) riceve un `Modifier` da mettere sulla sua radice: porta il vetro,
  l'ombra e il ritaglio. In linea sta fra la scheda e il tondo, aperta sopra la fila, larga quanto lei. Col materiale
  Liquid è il terzo vetro del piano; altrimenti, con tutte e due le bande, viaggia come elemento condiviso (e le due copie
  restano composte per tutta la piega).
- **`standaloneTab` senza ruolo** è un tasto accanto alla bolla, non una zona («Scrivi»): TalkBack lo dice pulsante, la sua
  `key` non va passata come `selectedTabKey`, il nome sta nella `contentDescription` dell'icona.
- **`FluidTabRole.Prominent`** è una destinazione nel tondo a destra: diametro della barra aperta, solo il glifo, varco di
  0,115 H (circa 7 dp), le proporzioni del telefono in R5 T6; resta a barra ripiegata. Scelta, prende l'accento e TalkBack
  la dice scheda selezionata. Su una tela larga il gruppo di tre schede arriva a 5,23 H e resta centrato.
- **`FluidTabRole.Search`** è la ricerca: con una prominente prende posto fra le destinazioni col suo `title`, senza
  prende il tondo. Con `searchActivation = FluidTabSearchActivation.SearchTabSelection` sceglierla apre `searchBarContent`
  (il modificatore ricevuto va sul campo o su un antenato, perché possa prendere il fuoco) e Indietro torna all'ultima
  destinazione visitata, anche alla prominente. Con `Automatic` il campo lo guida l'app con `searchMode`.

### Il materiale

| caso | cosa succede |
|---|---|
| `backdrop` e nessun `tabBarContentModifier` | la barra si fa il vetro da sola (`Floating`, `floatingTint`) e il riempimento diventa trasparente |
| `tabBarContentModifier` | il pannello è dell'app; `FluidFloatingTabBarGlass.blurRadius` è il suo raggio **finale** |
| nessun `backdrop` | capsula piena di `colors.backgroundColor`, ombra di piattaforma, pillola piatta |

`FluidFloatingTabBarGlass(blurRadius, puckColor, puckOpacity = 0,26)`: con il pannello della barra `blurRadius` è il
raggio di **base** (2,5 dp, ×1,8 del ruolo: 4,5 come il pannello); col pannello dell'app è quello finale. Col materiale
nuovo `puckColor` non specificato non lava la bolla; scritto dall'app diventa una pellicola di quel colore.

### Segni, ripiego e movimento ridotto

- **`FluidTabBadge`**: `count = null` è il pallino (`FluidTabBadge.Dot`), un numero diventa «99+» oltre 99, zero o meno
  niente. Lo disegna la barra in tutte le copie (riga nascosta, icona sopra la bolla), all'angolo dell'icona senza
  cambiarne la misura; **solo a barra aperta**, e TalkBack lo dice una volta sul nodo della scheda («Chat, novità»). Nella
  DSL è l'overload `tab(key, title, icon, badge, onClick)`: la firma della 3.0 resta identica.
- **Movimento ridotto**: la piega è un cambio in un fotogramma, la bolla arriva al suo posto senza viaggiare e la pagina
  non aspetta; restano il dito che trascina la bolla e il suo schiacciamento.

**Trappole.** La connessione va sull'antenato delle pagine. Una pagina che cambia senza passare dall'host e senza cambiare
`selectedTabKey` può lasciare `scrolling` alzato: `settle()`. `FluidTabBarDefaults.ContentInset` (80) non è quello della
tab bar flottante (78). `standaloneTab` senza ruolo chiamato due volte sovrascrive il primo. Ogni scheda vuole una `key`
unica e stabile. Le schede a elenco dicono `selected` ma non hanno `Role.Tab` né il tag `top_level_<rotta>` (il rail sì):
un test le cerca dall'etichetta.

**Non usarla quando** c'è un solo livello di navigazione (lista → dettaglio): bastano `onBack` e `FluidScreen`.

## Telefono e tablet: la navigazione adattiva

`FluidAdaptiveNavigation` dispone le stesse `FluidTabItem` secondo la larghezza che riceve (le soglie di
`FluidPaneDefaults`): **sotto 600 dp** la tab bar flottante, **da 600 a 999 dp** il rail, **da 1000 dp** la tab bar
**in alto**, flottante e centrata, col tasto che la **apre in una barra laterale**. La rotta e le azioni sono dell'app;
la scelta del pannello si salva con l'Activity e ritorna quando la finestra si riallarga.

```kotlin
Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding()) {
  FluidAdaptiveNavigation(destinations, route, { route = it.route }) { insets ->
    Box(Modifier.fillMaxSize()) {
      Canvas(Modifier.fillMaxSize().fluidBackgroundExtension()) { drawRect(Brush.linearGradient(colors)) }
      Column(Modifier.align(Alignment.BottomStart).padding(insets).padding(36.dp)) { Text(title) }
    }
  }
}
```

**La conversione è un viaggio del piano, in due tratte.** La capsula e la colonna sono lo stesso pannello: all'apertura
la capsula va prima al margine iniziale mentre la pagina le fa posto (400 ms), poi la lastra scende fino in fondo (500
ms) e le voci passano da riga a colonna, sfocate nel cambio; alla chiusura la colonna si raccoglie (430 ms) e la capsula
torna al centro (400 ms). Le curve sono misurate su Musica per iPad (WWDC25 208). Un secondo tocco a metà inverte dal
punto raggiunto, anche dentro la prima tratta. Le voci, il tasto, il velo della selezione e lo spazio della pagina
seguono il rettangolo corrente del vetro; il tasto resta azionabile durante la sfocatura. Il contenuto riceve in
`insets` lo spazio sopra la capsula o sotto la tab bar; lo spazio laterale lo riserva il contenitore.

La barra laterale resta **flottante e monocroma**, con 12 dp di margine: divergenza scelta da iPadOS 27, dove arriva ai
bordi con le icone d'accento. Il pannello prende la luce della pagina: col materiale Liquid la sonda del fondale tinge
l'ombra e un velo del colore medio che arriva al 12% sulla colonna. `FluidSidebar` è la stessa barra laterale chiamata a
mano (anche su telefono, se l'app la sceglie).

**`fluidBackgroundExtension(start = null, end = 0.dp)`** va sull'immagine o sulla decorazione a tutta larghezza, prima
del suo disegno: registra il disegno una volta, mostra l'originale al centro e ne ripete il bordo ribaltato e sfocato
(12 dp) sotto la colonna. Sul bordo della pagina il riflesso parte nitido e si sfoca solo nell'estensione, così i colori
si raccordano durante il viaggio. Il margine viene dal contenitore; `start` ed `end` espliciti servono a un ispettore
posato dall'app. Rispetta il verso di lettura. Sotto Android 12 resta il riflesso senza sfocatura. Il testo va su un altro
nodo, e resta nitido.

**`FluidTabRail`** è la forma delle larghezze di mezzo: 84 dp di larghezza, una scheda ogni 64 dp con l'icona da 28 a
inchiostro pieno, una goccia di vetro come indicatore che preme, corre e rilascia; senza una scheda corrispondente alla
rotta la goccia non si mostra. Ogni scheda ha `Role.Tab`, `selected` e il tag `top_level_<rotta>`. Non si ripiega. Con
`header = { … }` un'azione sta in testa, fuori dal vetro, 8 dp sopra la capsula. Fuori da `FluidAdaptiveNavigation` si usa
dentro `FluidPaneScaffold` (`rail = { FluidTabRail(…) }`, larghezza di layout 112 dp, [schermata.md](schermata.md)).

## Le barre deprecate

- **`FluidTabBar(items, selectedRoute, onSelect, backdrop, onReselect)`**, deprecata dalla 2.12.0 con `ReplaceWith`: è la
  tab bar a elenco con la connessione `Never`. Rispetto alla barra storica è una capsula grande quanto le schede,
  alta 62 dp, senza margini propri, che non si ripiega e senza `Role.Tab`.
- **`FluidFoldingTabBar` e `FluidBarFold`**, deprecate dalla 2.0.0: la tab bar a DSL con una connessione **chiusa a
  chiave**, la cui piega viene da `fold: () -> Float` (da 0,5 in su `inline()`, sotto `expand()`). `FluidBarFold`
  (`rememberFluidBarFold()`) è la piega con la sua connessione: 36 dp per nascondere, 10 per rivelare, solo il dito.
  `tint`, `trailing` e `accessoryHeight` sono ignorati.

Codice nuovo usa `FluidFloatingTabBar` con la sua connessione.

## Misure e costanti

| oggetto | valori (dp se non detto) |
|---|---|
| `FluidFloatingTabBarDefaults` | `TabWidth` 88 (tetto), `OpenHeight` 62 (pavimento: col testo al 130% cresce), `FoldedHeight` 52, `FoldedSoloHeight` 64, `Spacing` 8, `BottomMargin` 16, `HorizontalMargin` 14, `ContentInset` 78, `contentInsetWithAccessory(h)` = 78 + h + 8 |
| `FluidFloatingTabBarGlass` | `blurRadius` 2,5 (base o finale, vedi sopra), `puckOpacity` 0,26 |
| connessione | `scrollThreshold` 50, `expandThreshold` 8, `inlineBehavior` `OnScrollDown` |
| tempi (`FluidMotion`) | `FloatingTabFold` 234 ms, `TabBarSearch` 133 ms, `TabBarPlayer`, `FloatingTabSearchPage`, `TabPuckLeadMillis` 160, `TabTugReturn`; la piega di prima: `GooeyMillis` 300, `GooeyPulse` 12 |
| bolla | premuta ×78/56 della cella, icona ×1,12; inchiostro ≥ 3:1; rientro 0,076 H |
| scheda prominente | tondo 1,00 H, varco 0,115 H; gruppo di tre 5,23 H su una tela larga |
| `FluidFloatingCapsuleDefaults` | `Height` 62, `ActionSize` 48, `Spacing` 4, `ContentPadding` 6 e 7, `MaxWidth` 560 |
| ricerca dichiarata | lente 44, campo 40 (tocco 44), velo 40% nero o 16% bianco secondo l'inchiostro |
| `FluidSearchFieldDefaults` | `Height` 52, `IconSize` 22, `ActionSize` 40, `ExpandedFallbackWidth` 320 |
| navigazione adattiva | soglie 600 e 1000; tratte 400 + 500 ms all'apertura, 430 + 400 al ritorno; margine della colonna 12; riflesso 12; velo della colonna 12% |
| `FluidTabBarDefaults` (rail e barra storica) | `Height` 64, `RailWidth` 84, `RailIconSize` 28, `RailHeaderSpacing` 8, `ContentInset` 80 |

## Quanto siamo vicini ad Apple

Le tracce `tools/glassbench/traces/f4b-5/` portano la geometria dell'engine accanto ai video di Apple, con `pair`,
`first`, `last` e `curve` (`glassmotion.py`, vedi `tools/glassbench/TRACE.md`):

| traccia | Apple | lettura |
|---|---|---|
| `tabbar-piega` | Musica, WWDC25 284 f5414 | stesse fasi alle stesse ore: gruppo nel tondo a 67–100 ms, lettore in linea a 200–234 |
| `tabbar-ricerca` | Foto, 284 f30302 | il gruppo si raccoglie e il campo si allunga in 133 ms come in Apple; la salita con la tastiera è dell'app |
| `ricerca-apre` | Salute, WWDC25 256 f7320 | la lente arriva a sinistra ~33 ms prima, il testo è leggibile a ~233 ms come in Apple; i vicini vanno in «Altro» e non nel tondo della scheda |
| `bolla-pressione`, `bolla-cambio` | Musica iOS 26, registrazione a 60 Hz (R1 T2) | lente formata a 133–167 ms e rientro in ~80 ms come in Apple; nel cambio la bolla arriva un fotogramma prima |
| `tabbar-in-barra-laterale`, `barra-laterale-in-tabbar` | Musica per iPad, WWDC25 208 | le due tratte nei due versi entro un fotogramma |
| `capsula-cambia` | Foto, WWDC25 219 f8551 | il gruppo si stringe e il più resta fermo; in Apple il più scivola di 0,16 H |

## Cosa è verificato dai test

- `FluidFloatingTabBarScrollTest` (engine-ui): piega sul solo scorrimento consumato, riapertura in cima, `locked`, cambio
  di pagina. `FluidTabBarRouteTest` (sample): l'host e il cambio di scheda riaprono la barra, e solo loro.
- `FluidFloatingTabBarLayoutTest`: larghezze delle schede, etichette che si riducono, `ContentInset` che copre l'altezza
  vera. `FluidFloatingTabBarBlurTest`: i due significati di `blurRadius`. `FluidPuckInkTest`, `FluidFloatingTabBarInkTest`:
  inchiostro leggibile su ogni accento e sul vetro.
- `FluidFloatingTabBarFieldTest`: la piega e la ricerca come viaggi del piano (larghezze a sei istanti, inchiostro che
  si spegne, il titolo scelto fino a 67 ms e sotto il glifo), la bolla premuta e lo scambio; esporta le tracce.
- `FluidFloatingCapsuleTest`: margini e inset delle tre sedi, azioni in gruppi annidati, ingombro. `FluidBottomBarTest`
  (sample): una sede in alto e una in basso, la capsula centrata che cambia larghezza, il più fermo fra «Raccolta» e
  «Foto», push e pop identici con e senza barra in basso.
- `FluidSearchFieldTest`, `FluidToolbarSearchTest`: contatore e larghezze del campo; sede automatica, vicini, stato
  attivo, ritorno interrotto, finestra traslata dalla tastiera. `FluidToolbarSearchHostTest` (sample): schermata senza
  DSL, host dichiarato, tondo della tab bar, digitazione e sostituzione.
- `FluidAdaptiveNavigationTest`: quale forma a quale larghezza, le pose e la traiettoria delle due tratte; esporta le
  tracce. `FluidTabBarExtensionsTest` (sample): segni, pulsante, movimento ridotto, rail con l'azione in testa, riga e
  colonna alle ordinate giuste, la rotta ripristinata attraverso il rail e la ricreazione, i pixel dell'estensione nei due versi di lettura e
  il raccordo nitido-sfocato.
- `FluidBottomGalleryTest` (sample): le sedi della ricerca della galleria e i suoi quattro capitoli.
- Da guardare in mano (S4): la ricerca con la tastiera vera, la bolla trascinata, la piega durante uno scorrimento, la
  conversione in barra laterale con le inversioni.

## Dove guardare

Sotto `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`:

- `FluidFloatingTabBar.kt`: le due forme, `FluidTabRole`, `FluidTabSearchActivation`, la connessione, i tre vetri del
  piano, la bolla, i `Defaults` (vendored, a quattro spazi). `FluidGooey.kt`: la piega di prima.
- `FluidFloatingCapsule.kt`: capsula, azione, `fluidCapsuleInsets`. `toolbar/FluidBottomBar.kt`: la sede in basso.
- `FluidSearch.kt`: `FluidSearchSpec` e le sue enumerazioni. `toolbar/FluidToolbarSearch.kt`: le sedi, il viaggio, la
  tastiera. `FluidSearchField.kt`: il campo.
- `FluidAdaptiveNavigation.kt`, `FluidSidebar.kt`, `FluidTabBar.kt` (`FluidTabItem`, rail, barra storica),
  `FluidBackgroundExtension.kt`, `FluidFoldingTabBar.kt`.

La galleria: **Chrome → «Barra in basso»** (`--es gallery.page BottomBar`, `sample/…/bottom/`) in quattro capitoli, la
barra in basso e la capsula, la ricerca nelle sue sedi, la tab bar sul palco, il passaggio alla **navigazione adattiva**
(`--es gallery.page TabletNavigation`, «Atlante cromatico»). Le stesse schede della tab bar stanno anche nella sezione
«Scheda prominente» di «Pannello laterale e schede», e la ricerca fissata sotto il titolo nella pagina «Ricerca» di
«Barre fra pagine».
