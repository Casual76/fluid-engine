# Inventario di `engine-ui`

> **Nota.** Questo e' l'inventario storico, scritto per sezioni di versione. Per ogni famiglia la scheda in
> [`componenti/`](componenti/contenuto.md) e' piu' completa e piu' recente (e `api/` ha le firme dal codice): usa
> questo file per il contesto e per le regole trasversali, le schede per sapere come si usa un componente. Dove i due
> divergono vale la scheda, e dove la scheda e' vaga vale il sorgente.

Tutto sotto `dev.antigravity.fluidengine.ui` (`fluid/` e `theme/`). Se stai per scrivere un
componente, cerca prima qui: quasi tutto quello che serve a una schermata esiste già.

## Struttura di una schermata

| | |
|---|---|
| `FluidScreen(title, subtitle, onBack, actions, titleFacets, isRefreshing, onRefresh, overlay) { LazyListScope }` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare quando il titolo passa sotto; `overlay` riceve il backdrop per chrome flottante sicuro |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidFloatingTabBar` / `FluidTabRail` | navigazione principale a pillola (`FluidTabItem`): la barra in basso, che si ripiega scorrendo (vedi `rememberFluidFloatingTabBarScrollConnection` più sotto), e il rail per gli schermi larghi |
| `FluidGlassModalPortal` (+ `FluidGlassModalHost` alla radice), `FluidAlert`, `FluidGrabber` | pop-up e fogli in vetro dentro la composizione, e avvisi (`FluidAlertAction`): dalla 3.0 `FluidAlert` sta nell'host quando c'è (`inHost = false` per il `Dialog`), vedi «I menu liquidi» |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe (`FluidSectionAnchor`) |
| `FluidNotificationHost`, `rememberFluidNotificationHostState` | notifiche contestuali dentro l'app (`FluidNotification`, `FluidNotificationTone`, `FluidNotificationAction`); la card si trascina, vedi sotto |
| `ProvideFluidChrome`, `rememberFluidChromeController` | registro della barra, inset della tab bar, bus "torna in cima" |
| `FluidNavHost`, `rememberFluidNavController`, `composable(…)`, `FluidPageHost`, `FluidPageHostState`, `FluidPageTransition`, `FluidBackSwipe` | le pagine (3.0): opache, da destra, il back sotto il dito; vedi «Le pagine e `FluidNavHost`» più sotto |
| `FluidRouteMotionHost`, `FluidRouteMotion`, `rememberRouteMotionSignals` | il vecchio involucro delle rotte: per chi non migra a `FluidNavHost`; **deprecato**, e dalla 3.0 non sfoca più né arrotonda; dentro un `FluidNavHost` non si mette (non compila) |
| `FluidFloatingCapsule`, `FluidCapsuleAction`, `fluidCapsuleInsets`, `FluidSearchField` | la capsula di vetro con poche azioni in fondo allo schermo, e il campo di ricerca di vetro che si apre da un cerchio |
| `FluidSheetScaffold`, `FluidConfirmSheet`, `FluidPromptAlert` | il contenuto di un foglio con i tasti fermi, la conferma, l'avviso che chiede una riga di testo |
| `rememberFluidTouchOrigin`, `MotionOrigin` | apertura dal punto toccato |

**La notifica si tocca (2.12.0).** `FluidNotification(id, title, message, tone, durationMillis,
actions)`: un dito sulla card ferma il tempo di lettura, che al rilascio riparte da quello che
restava (mai meno di 1,5 s). Tirata in giù segue il dito con la curva elastica di
`FluidScrollPhysics` e si allunga di layout; oltre 72 dp, o con una spinta sopra i 1000 dp/s, si
apre: testo intero, `actions` (una o due `FluidNotificationAction(label, onClick)`, che toccate
eseguono e chiudono) e nessuna scadenza. Spinta in su, oltre 40 dp o con una spinta, viene lanciata
via e si chiude; altrimenti torna con una molla senza rimbalzi. Per TalkBack le azioni «Espandi» e
«Chiudi». **A card chiusa il messaggio mostra due righe**: il testo lungo si legge aprendola, e le
azioni compaiono solo allora.

## Contenuto

| | |
|---|---|
| `FluidListGroup { }` | il contenitore della lista raggruppata |
| `FluidListRow(title, subtitle, eyebrow, meta, tone, badge, leading, leadingTile, onClick, onLongClick)` | la riga; `leadingTile = false` lascia `leading` com'è, e il colore della piastrella lo sceglie l'app con `FluidToneTile` |
| `FluidListDivider()` | il separatore rientrato |
| `FluidCard`, `FluidHero`, `FluidEditorialCard` | superfici (`FluidCard(highlighted = true)` per un riquadro che dice una cosa, `FluidHero` per l'apertura di una schermata piena di dati) |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note: `FluidSectionHeader` è la didascalia quieta sopra un gruppo, `FluidSectionTitle` (eyebrow in accento + titolo grande) apre una sezione di pagina |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState(title, detail, illustration)`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage(…, onClick, action, leading)` | stati; con `onClick` tutto il messaggio è un bersaglio (ruolo pulsante, freccia, `action` dice cosa fa il tocco) |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`, `SyncStatus.noticeMessage()`, `SyncStatus.lastSyncLabel()` | stato della sincronizzazione |
| `FluidPillTabs`, `FluidAccentLabel` | varie |
| `FluidToggleRow`, `FluidChoiceRow`, `fluidAppearanceSection`, `FluidAccentPicker`, `FluidAppearance` | le righe delle impostazioni e la sezione «Aspetto» (3.0) |
| `FluidToneTile`, `fluidGlassGroups`, `FluidGlassGroup` | la piastrella dell'icona, pubblica; una lista lunga in pannelli di vetro da otto righe (3.0) |
| `FluidLiveDot`, `FluidSkeleton*`, `FluidSelectionMark`, `FluidPagerDots`, `FluidStepProgress` | i pezzi piccoli (3.0): pallino dal vivo, scheletri che respirano, cerchio di selezione, punti del pager, passi |

## Controlli

`FluidButton(text, onClick, style, size, enabled, loading, fillWidth, leading)` con
`FluidButtonStyle` = Filled / Tinted / Plain / Destructive. Dalla 2.11.0 **cresce** sotto il dito
(vedi "La pressione" qui sotto), contenitore compreso: prima cedeva solo l'etichetta.

`FluidSwitch(checked, onCheckedChange, enabled, backdrop)` — il LiquidToggle di Kyant0, 64x28 con
pomello a pillola 40x24. Il **pomello è l'unica lastra**: a riposo è bianco pieno, tenuto premuto il
bianco sparisce e sotto si apre una copia della pista schiacciata, che è ciò che rifrange. Dalla
2.13.1 **un solo gesto su tutto il controllo** (prima erano tre pezzi, e sul telefono si poteva solo
toccare): un tocco ovunque lo commuta; un dito sul pomello lo trascina subito, senza zona morta; un
dito sulla pista vuota lo prende a mezzo slop di lato; una passata che va prima in verticale resta
della pagina. Mentre si trascina la pagina è ferma (ogni cambio del puntatore preso è consumato). Al
rilascio va dalla parte più vicina, o da quella verso cui correva se la spinta era veloce
(`fluidSwitchLanding`). Vibra a ogni tocco che commuta e, trascinando, quando il pomello passa la metà
(`fluidSwitchSide`, con margine); al rilascio non ripete. Il down è consumato nell'Initial: il
`toggleable` resta solo per tastiera, fuoco e TalkBack, e una riga cliccabile attorno non scatta
toccando l'interruttore. Dalla 3.0.4 la riga non disegna più un interruttore decorativo (`onCheckedChange = null`): usa `FluidSwitchControl(ownsSemantics = false)` con il callback vero, così il pomello si trascina anche dentro una riga (`componenti/controlli-gesto.md`). `backdrop` di default è il canvas della schermata.
`FluidSwitchWidth` vale 64 adesso (era 56): chi riservava lo spazio a mano lo rilegga.
`FluidGlassSwitch` è deprecato — era il nome che questo controllo ha avuto per una versione.

`FluidSlider(value, onValueChange, valueRange, snapValues, snapThreshold, contentDescription,
stateDescription)` — il LiquidSlider di Kyant0 (dalla 2.12.0), il fratello dell'interruttore: pista
sottile da 6 dp e una pillola 40x24 **bianca e opaca a riposo** che, tenuta, diventa lente e
rifrange la pagina più una copia della pista che si dispiega. Tutta la riga (48 dp) tocca, non solo
la pillola. Un trascinamento **non fa scorrere la pagina**: un dito sulla pillola, o a 10 dp dal suo
bordo, e' suo subito e ogni suo movimento e' consumato fino al rilascio; sulla pista nuda diventa suo
a mezzo slop di lato, mentre una passata verticale resta della pagina. Con `snapValues` il valore si
attacca alla tacca piu' vicina entro `snapThreshold` (frazione dell'intervallo, 0,03 di default; `1f`
= a gradini), con un puntino sotto la pista e un tocco aptico per tacca; senza, `DragTick` a ogni
ventesimo e `Threshold` in fondo. Per TalkBack e' uno slider (`setProgress`), e
`stateDescription` («30 minuti») gli dice il valore al posto di una percentuale.

`FluidSegmentedControl(options, selected, onSelect, label)` — `content = { opzione, selezionato -> }`
per i segmenti a sole icone; `label` resta e diventa la descrizione per chi legge lo schermo, `FluidChip(label, selected, onClick)`,
`FluidTextField(…, secret)`, `FluidColorDot`, `FluidBarAction(icon, contentDescription, onClick)`,
`FluidGlassIconButton(size, haptic)`, `FluidGlassButton(size, haptic)` (`FluidGlassSize`: 40, 44, 54 dp), `glassControlSurface`, `fluidStaticGlassSurface`,
`FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`, `FluidHairline`, `FluidRowValue`.

`fluidLicensesSection()` / `FluidLicenseGroup` / `FluidLicenseDetails` — i crediti di terze parti che
l'engine porta dentro l'APK. Vanno nella pagina "informazioni" di ogni app: l'Apache-2.0 del vetro
chiede che l'avviso viaggi con la distribuzione, non che stia in un Markdown nel repo.

## Token

- `FluidRadius`: Small 10, Control 12, Card 18, Group 22, Sheet 38 · `FluidCapsuleShape`
- `ContinuousCornerShape(radius, smoothing = 0.6f)`
- `fluidTypography()` · `FluidTextStyles.uppercaseCaption / numeric / largeNumeric`
- `FluidFontFamily` (testo), `FluidDisplayFontFamily` (titoli ≥ 20sp)
- `FluidTone`: Primary, Success, Warning, Danger, Info, Neutral
- `FluidMotion` (molle), `FluidMotionScheme` (le passa a Material), `FluidMotionPolicy`; dalla fase 10 della 3.0 ogni numero di movimento ha un nome qui — `pixels`, `tuned`, `flick`, le molle della goccia
  (`dropValue`, `DropPress`, `DropScaleX/Y`), `Popover*`, `TabTugReturn`, `pulse`, `loop`: un movimento nuovo si aggiunge a
  `FluidMotion`, non al file del componente
- `glassContainer(stato)`, `glassMaterialSource(stato)`, `glassSurface(nestInContainer)`
  (2.13.0) — vetro su vetro per il contenuto che non e' figlio del suo vetro e per i pannelli
  disegnati dall'app; di serie non serve niente
- `GlassDefaults` (con `panelOptics(compact, overSharpPage)` e `panelTint(colour)` per i pannelli flottanti), `rememberGlassBackdrop`, `rememberCombinedGlassBackdrop`, `LocalGlassBackdrop`,
  `LocalFluidCanvasBackdrop`, `GlassTint`, `GlassEdge`, `GlassFalloff`, `GlassOptics`, `GlassRole`
- `LocalFluidSurfaceSide` — da che parte sta l'app, quando la palette non si puo' interrogare. Serve
  solo a chi mette una **pellicola traslucida** in `colorScheme.surface`: la luminanza ignora
  l'alpha, quindi quel design viene letto come chiaro sempre. `FluidTheme` lo fornisce da se'
- `FluidAmbient`, `FluidAmbientCanvas` — il fondale per schermata; `FluidScreen(ambient = ...)`
- `FluidGlassModalHost`, `FluidGlassModalPortal`, `FluidGlassModalPresentation`,
  `fluidExpandOrigin`, `fluidGlassModalObscured` — il pop-up in vetro dentro la composizione
- `FluidContextAction` (con `startsSection` dalla 3.0), `fluidContextMenu`, `rememberFluidContextMenu`,
  `fluidContextMenuAnchor`, e `FluidListRow(contextActions = ...)` — il menu contestuale iOS: dalla 3.0 la
  riga si solleva, il pannello cresce accanto a lei e si sceglie col dito che l'ha aperto
- `FluidMorphMenuButton` (con `opensOnTap` dalla 3.0) + `FluidMorphMenuHost` (con
  `rememberFluidMorphMenuState()`) — il tasto che si trasforma nel proprio menu: una superficie sola,
  che viaggia dal tasto al pannello e torna tasto. Lo stato e l'host stanno alla radice, accanto a
  `FluidGlassModalHost`, e l'host deve essere composto. Sostituisce `FluidGlassMenuButton`, che passa
  dall'host dei modali (dalla 3.0 con lo stesso pannello e lo stesso dito dei menu nuovi)
- `FluidMenuOpensOn` e `FluidBarAction(opensOn)` (3.0) — se il menu si apre al tocco o tenendo premuto
- `FluidFloatingTabBar`, `rememberFluidFloatingTabBarScrollConnection` — la barra di navigazione:
  una capsula che si ripiega in una pillola mentre la pagina scorre. La connessione va attaccata
  alla pagina (`Modifier.nestedScroll(connection)`); senza, la barra resta aperta. Due forme: **a
  elenco**, `FluidFloatingTabBar(items = List<FluidTabItem>, selectedRoute, onSelect,
  scrollConnection, backdrop)` per tre destinazioni con un'icona e una parola, e **a DSL**,
  `FluidFloatingTabBar(selectedTabKey, scrollConnection, backdrop = …) { tab(key, title, icon,
  onClick); standaloneTab(…) }`, che porta anche una banda sopra la fila (`inlineAccessory` /
  `expandedAccessory`), un campo di ricerca che cresce nella barra (`searchMode` +
  `searchBarContent`), il lato in cui si raccoglie (`foldAlignment`) e il materiale (`glass`,
  `accentColor`; `FluidFloatingTabBarGlass.blurRadius` dipende da chi dipinge il pannello: se lo fa
  la barra e' il raggio di *base*, 2,5 dp, che la riga nascosta e il puck moltiplicano come il
  pannello; se lo passi tu con `tabBarContentModifier` e' il raggio *finale* del tuo pannello, preso
  tal quale). Margini e
  `navigationBarsPadding()` li mette chi la posa
  (`FluidFloatingTabBarDefaults.HorizontalMargin` / `BottomMargin`, e `ContentInset` per lo spazio
  che la pagina deve lasciare). L'indicatore tiene un bordo anche a riposo
  (`GlassOptics.edgeFloor`) e il trascinamento e' sottosmorzato di proposito (smorzamento 0,5, vedi `FluidFloatingTabBar.kt`; una vecchia nota qui diceva «criticamente smorzato»)
- **Dalla 3.0.1**: `FluidPickerRow(title, selectedKey, options, onSelected)` (la riga che diventa il
  pop-up di scelta) e `FluidSliderRow`; `leadingTile` su `FluidChoiceRow`/`FluidToggleRow`;
  `FluidContextAction(selected)` (spunta e stato per TalkBack); `glass` su `FluidInlineMessage` e sulle
  licenze; `FluidCard(enabled)`; `FluidStepProgress(labels)`; `fluidGlassGroupsIndexed`;
  `FluidImageViewer(background)`; `LocalFluidMorphMenuState`; `navigateToPeer(route)` per le schede con una
  pila sola e `fractionToward(isHidden)` per la barra che esce con la pagina; `LocalFluidLabels` per le
  parole dell'engine; `rememberFluidUpdateController` per `fluidUpdateSection`.
- **Deprecati** (restano per tutta la 3.x): `FluidTabBar` e `FluidFoldingTabBar` — delegano a
  `FluidFloatingTabBar`, quindi chi li chiama prende il vetro nuovo senza toccare niente, ma la
  barra e' una capsula grande quanto le schede (non da lato a lato, 60 dp e non 64) e si ripiega solo
  se le dai una connessione. Le sue schede dicono ancora `selected` ma non portano più il ruolo `Tab`
  né il tag di test `top_level_<rotta>` (il rail li tiene): un test cerca l'etichetta;
  `FluidGlassMenuButton` → `FluidMorphMenuButton` + host (non delega: serve lo
  stato); `FluidSheet` → `FluidGlassModalPortal`; `FluidGlassSwitch` → `FluidSwitch`;
  `FluidQuickAction` → `FluidChip(selected = false)`; `FluidListItem` → `FluidListRow` (il vecchio
  `subtitle` e' l'`eyebrow`, `supporting` e' il `subtitle`, `trailing` e' il `badge`);
  `FluidTopHeader` → `FluidScreen`, o `FluidCollapsingTitle` + `FluidCollapsingTopBar` (non
  delega); `FluidHeroCard` → `FluidHero`, o `FluidCard(highlighted = true)` (non delega). Nel
  codice nuovo non si scrivono. `FluidSyncAction` non e' deprecato: il suo dettaglio si apre come
  foglio di vetro se c'e' un host dei modali, altrimenti come foglio di piattaforma
- `AccentPreset`, `fluidAccentPresets`, `FluidDefaultBrand`, `fluidBrandAccent(isDark, brand)`
- `fluidColorScheme(settings, isDark, brand, dynamicScheme)` — la palette fuori da una composizione,
  per widget e notifiche
- Modificatori: `fluidPressable` (con `feedback` e `pressState` dalla 2.11.0, `longClickHaptic` dalla
  2.12.0), `fluidRowPressable`; `LocalFluidPressImmediate` (dalla 3.0 non fa più niente)
- La pressione (2.11.0): `FluidPressFeedback` (`Expand` per i pulsanti col testo, `ExpandRound` per
  i tasti tondi dalla 3.0, `yielding(scala)` per le superfici, `None`), `rememberFluidPressState`,
  `FluidPressState.progress`

## Regola del vetro

Chrome e overlay lo hanno sempre: top bar, tab bar/rail, azioni, notifiche, indici, modali.

**Dalla 1.5.0 anche il contenuto puo' averlo**, ma solo alle sue condizioni. `FluidCard`,
`FluidListGroup` e `FluidMetricTile` accettano `glass = true`, e lo ottengono soltanto dove la
schermata ha un canvas ambientale (`FluidScreen(ambient = ...)`): senza, disegnano la superficie
opaca di sempre. Il motivo e' che il vetro sopra il grigio e' invisibile per costruzione, e il canvas
e' quello che si guarda *attraverso*.

Il vincolo tecnico che rende tutto questo possibile sono **due registrazioni invece di una**. Il
canvas si disegna e si registra prima della lista, quindi non puo' contenerla; il corpo contiene
tutto e lo rifrange la chrome. Una card nel corpo che campionasse il corpo campionerebbe se stessa —
feedback ottico, che nel vetro non si nasconde. `FluidScreen` tiene le due cose separate e le
distribuisce come `LocalFluidCanvasBackdrop` (il canvas, per il contenuto) e `LocalGlassBackdrop`
(il corpo, per la chrome).

**Il vetro va sul contenitore, mai sulla riga.** Un gruppo di dodici righe e' un nodo di vetro, non
dodici. Testo e icone sopra il vetro restano opachi al 100%.

Un overlay che deve campionare la schermata va nello slot `FluidScreen.overlay`.

**Il vetro sta su quello che ha sotto, non su tre strati sotto** (vetro su vetro, automatico dalla
2.13.0). Un tasto su un pop-up, una card su un foglio, il pomello di un interruttore su una card di
vetro campionano il *materiale* del pannello su cui stanno — sfocato, rifratto, tinto — e non la
pagina: sfocano la sfocatura invece di bucarla. Lo fa il nodo di vetro da solo, cercando il vetro
piu' vicino fra gli antenati, quindi vale anche quando l'app passa ai controlli lo stato della
pagina (`backdrop = backdrop`) e anche per un pannello che l'app si fa con `glassSurface`. Il
materiale si registra solo se dentro c'e' vetro, e solo quando cambia. Un cablaggio a mano
(`exports` + `rememberCombinedGlassBackdrop`, come fanno `FluidScreen`, la tab bar e la notifica)
vince e non viene raddoppiato. Due casi lo dichiarano: il contenuto che non e' figlio del suo vetro
(`exports = stato` sulla superficie, `glassContainer(stato)` sul contenuto: la finestra di
Fluid-physics, il menu' che si trasforma) e il pannello che un'app disegna con un altro motore
(`glassMaterialSource(stato)` sul nodo che porta solo il suo materiale). `nestInContainer = false`
lo spegne per la superficie che ricostruisce da se' il materiale del pannello (il puck della barra
flottante). Il perche' in `regole.md`.

**La taratura sta nella lente, non nella sfocatura.** Il raggio di riferimento e' 2,5 dp (2 fino alla
2.11.0): sopra gli ~8 dp il vetro trasmette la media di quello che ha dietro invece dell'immagine, e
una media e' un riempimento. La lente invece e' grande (19/29 dp sulla capsula). Chi deve davvero
nascondere qualcosa — la barra superiore, con testo nitido che le scorre sotto — chiede un multiplo
con `blurScale`. Per ruolo, col riferimento di serie: Bar 12,5 dp, Floating 4,5, Interactive 3,5,
Modal 8,75, Content 2, StackedContent 4,5. **I pannelli grandi pagano di piu'** (regola della taglia,
2.12.0): sotto i 64 dp di lato corto niente cambia, fino ai 320 dp sfocatura ×2, altezza di
rifrazione ×1,6 e spostamento al bordo ×1,8; `GlassOptics.sizeResponse = 0` se ne tira fuori (lo
scrim). Il dettaglio e il perche' sono in `regole.md`.
E il film non e' `MaterialTheme.surface`: vedi `GlassDefaults.glassFilm()` e la regola in
`regole.md`, perche' una barra del colore del fondo su AMOLED semplicemente sparisce.

**Sopra un testo piccolo, niente sfocatura e niente lente a riposo.** L'indicatore della tab bar sta
sopra una scritta alta sei pixel: qualsiasi raggio di blur e quella diventa l'unica parola
illeggibile della barra. Vetro fermo = lo sfondo ridisegnato 1:1; la lente arriva solo con il dito
(`opticalDepth = { pressProgress }`).

Non c'è più l'eccezione sugli angoli: la lente legge i raggi da qualsiasi `CornerBasedShape`, quindi
anche vetro e superfici di pagina usano `ContinuousCornerShape` e la stessa capsula.

**Due famiglie di vetro, e un solo materiale per i pannelli (3.0).** La **lastra** è ciò che sta
(pannelli, barre, card, gruppi, menu, i tasti di vetro a riposo: `glassSurface`, `glassControlSurface`,
`fluidPhysicsSurface`); la **goccia** è ciò che si afferra e scorre (pomelli di slider e interruttore,
pillola della tab bar, pillola del segmentato, goccia dei menu): piena a riposo, lente sotto il dito.
La ricetta di ciascuna, le eccezioni note e lo stato di ogni componente sono in
`docs/10-coerenza.md`; il perché in `regole.md` («La goccia e la lastra»). Un **pannello flottante**
(popover, menu contestuale, finestra che si espande, foglio, notifica, callout, menu che si trasforma)
chiede `GlassDefaults.panelOptics(compact = false, overSharpPage = false)` e
`GlassDefaults.panelTint(colour = null)` e non scrive numeri suoi: sfocatura 2,5, lente 14/18 dp, bordo
0,8 dp al 45%, ombra 18 dp al 36%; `compact` (lente 7/9 dp) per un menu di poche righe, `overSharpPage`
(sfocatura 3,5) per chi sta sopra una pagina che nessuno scrim ha sfocato (notifica, callout). Il bordo
è uno (`glassRimRecipe`: luce in `Plus` sul lato scuro, una riga scura in `SrcOver` sul chiaro) per
`glassSurface` e per `fluidPhysicsSurface`, e l'ombra è del vetro (`glassShadowColor`), mai un
`Modifier.shadow` sotto una superficie trasparente.

**Il contrasto di sistema** (Android 14+, «Contrasto del colore»): le pellicole di `GlassDefaults` e il ripiego salgono verso il pieno (medio il 40%, alto l'85% della strada che manca: `GlassTint.forContrast`) e il bordo si fa più netto; a standard non cambia niente, e il tema segue il cambio con l'app aperta (`rememberFluidSystemContrast()` → `LocalFluidSystemContrast`, che si forza dentro il tema). Non lo seguono ancora `fluidStaticGlassSurface`, lo scrim dei modali e la goccia del segmentato.

L'ottica la disegna la libreria `backdrop` di Kyant (Apache-2.0) copiata in `ui/glass/backdrop/`.
Non scrivere shader di vetro nuovi accanto a quella: se serve un'ottica diversa, è un `GlassOptics`
diverso.

## Il tema

```kotlin
FluidTheme(
  settings = EngineSettings(themeMode = …, accentMode = …),
  brand = AccentPreset("app", "App", Color(0xFF…), Color(0xFF…)),
) { /* … */ }
```

`EngineSettings` è piccolo apposta. Se l'app ha già un suo modello di impostazioni, mappalo al bordo
con un `map { }` sul flow che esiste già — non sostituirlo e non allargare `EngineSettings` con campi
che riguardano una sola app.

## Schema di una schermata tipica

```kotlin
@Composable
fun SchermataDemo(state: DemoUiState, onApri: (String) -> Unit) {
  FluidScreen(
    title = "Demo",
    subtitle = "Una riga che spiega la pagina",
    isRefreshing = state.isRefreshing,
    onRefresh = state.onRefresh,
  ) {
    if (state.notice != null) {
      item { FluidSyncNotice(status = state.syncStatus, onRetry = state.onRetry) }
    }
    item { FluidSectionHeader(title = "Sezione") }
    item {
      FluidListGroup {
        state.items.forEachIndexed { index, item ->
          if (index > 0) FluidListDivider()
          FluidListRow(
            title = item.title,
            subtitle = item.subtitle,
            tone = item.tone,
            onClick = { onApri(item.id) },
          )
        }
      }
    }
    if (state.items.isEmpty()) {
      item { FluidEmptyState(title = "Niente qui", detail = "…") }
    }
  }
}
```

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
`progress`), di serie dal bordo (3.0.3; da un punto qualunque con `Anywhere`); il rilascio conferma oltre il 35% della larghezza (tetto
180 dp) o con una spinta di 400 dp/s, una spinta contraria annulla; la molla parte dalla velocità del dito
e non rimbalza. Mentre si muove la pagina è una **carta**: angoli del telefono, ombra da 36 dp; a riposo
niente. Mai alpha, mai sfocatura, mai scrim. Fra due schede c'è il passo laterale di sempre (13%, la
vecchia sfuma in 150 ms), senza gesto. Nessuna vibrazione nel gesto. Con le animazioni ridotte i moti
autonomi saltano e il dito segue 1:1.

**Gli stili e i gesti.** `FluidPageTransition`: `Slide` (di serie, da destra), `ShrinkSlide`
(rimpicciolisce al 90% nel primo 30% della corsa, poi scivola: per la pagina che dice «sono una cosa sola
che si chiude»), `Expand` (si apre dal punto toccato, scala 0,91 → 1, opaca). `FluidBackSwipe`:
`EdgeOnly` (**di serie dalla 3.0.3**, come il gesto di sistema: i primi 20 dp del bordo d'ingresso),
`Anywhere` (opzione esplicita: da qualunque punto, se nessun figlio — un cursore, un interruttore, una riga che
scorre di lato — si è preso il dito; uno scorrimento orizzontale arrivato al suo inizio passa la mano; era di serie
dalla 3.0.0 alla 3.0.2), `Off`. Un trascinamento orizzontale che parte su un tasto non porta indietro: il tasto tiene il dito. **Il back a dito passa dal dispatcher** come quello di sistema: oltre la soglia un `BackHandler` (o `PredictiveBackHandler`) abilitato dopo l'host risponde per primo, e la pagina torna sotto il dito con la molla dell'annullo mentre lui decide; senza gestori esce come sempre. Una conferma di uscita non spegne più il gesto. `FluidNavHost(pageColor)`: di serie lo sfondo del tema; trasparente, la pagina non dipinge e nelle transizioni le due pagine si vedono per trasparenza.

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
anche al menu delle azioni, che si sceglie trascinando); il back a dito parte dal bordo (3.0.3); con `Anywhere` i tasti
dentro la pagina lo lasciano partire dal vuoto. Uno scroller orizzontale al suo inizio passa il gesto al back (con `Anywhere`); uno slider mai. Con
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

### Aggiornamento in-app, riordino, scorrimento rapido, visore, scrubber, Markdown

| | |
|---|---|
| `FluidUpdatePrompt(state, onInstall, onLater, …)` · `FluidUpdatePromptSheet(state?, onInstall, onLater, …)` · `LazyListScope.fluidUpdateSection(state, onCheck, onInstall, currentVersion, …)` | l'aggiornamento in-app: l'avviso (contenuto di un foglio) e le righe delle impostazioni, sopra il modello neutro `FluidUpdateUiState` / `FluidUpdateRelease` / `FluidUpdateStrings` / `FluidUpdate`; `AvailableAppUpdate.toFluidUpdateRelease()` e `AppUpdateInstallState.toFluidUpdateState(release)` traducono dai tipi di foundation |
| `rememberFluidReorderState(listState \| gridState, onMove, …)` · `LazyItemScope.fluidReorderable(state, key, …)` (anche griglia) · `fluidMoved` · `FluidReorder` | il riordino: tenuto premuto l'elemento si solleva come goccia di vetro, i vicini scalano, un `Tick` per posto, la lista scorre ai bordi; due azioni per TalkBack |
| `FluidFastScroller(state, label, …)` · `FluidFastScrollTarget` · `FluidFastScroll` | lo scorrimento rapido: maniglia-goccia 1:1, bolla di vetro compatta con un `Tick` per etichetta, compare mentre si scorre |
| `FluidImageViewer(visible, onDismissRequest, …, page)` · `Modifier.fluidZoomable` · `FluidZoomState` · `FluidZoom` | il visualizzatore nell'host dei modali: pizzico, doppio tocco, pager, congedo trascinando giù; le immagini le passa l'app |
| `FluidScrubber(position: () -> Float, onSeek, valueRange, enabled, buffered, step, marks, onScrubStart, onScrub, onScrubEnd, startLabel, endLabel, contentDescription, valueDescription, keyStep, …)` · `FluidScrubbing` · `FluidScrubberDefaults` | la barra della posizione di un brano o di una cronologia: la goccia dello slider su una pista sottile. **La posizione non è sua**: a riposo mostra quella da fuori (lambda), col dito solo il dito, dopo il rilascio il punto lasciato finché il lettore non ci arriva. `onSeek` una volta; `onScrub` per l'anteprima viva; `step` per i fotogrammi (un `Tick` per passo, nessuna aptica nel continuo), `buffered` per il pronto, `marks` per l'«adesso»; testi sotto la pista dal valore mostrato; per TalkBack uno slider con «1:05 di 3:20», `setProgress` e due azioni |
| `FluidMarkdown(markdown, …, streaming, animateFirst, style, strings, options, onLinkClick, selectable)` · `FluidMarkdownParser` · `FluidMarkdownStyle` · `FluidMarkdownDefaults.style(…)` / `documentHeadings()` · `FluidMarkdownOptions` · `FluidRevealingText` | il Markdown disegnato, senza libreria: titoli (due misure, o una per livello con `headings`), elenchi anche annidati, grassetto/corsivo/barrato, codice in linea e a blocco con «copia», link (con callback), citazioni, tabelle che scorrono, righe; `streaming` sfuma dentro le parole nuove, `selectable` per una nota; il parser è puro e si prova sul computer. I marcatori `[[chip]]` di serie restano (li toglie `engine-ai-ui`) |

## Tutto in vetro (3.0, fase 9)

Quello che era opaco o dipinto accanto al vetro diventa vetro, e quello che si afferra e scorre diventa la goccia
di Kyant (piena a riposo, lente sotto il dito). Un modificatore interno solo la disegna, `glassDrop` (`glass/interaction/GlassDrop.kt`):
i pomelli di slider e interruttore, la pillola della barra, il segmentato, il rail e la lente dell'indice la
condividono, ognuno con la sua faccia (`GlassDropFaces`: bianca sul chiaro, il grigio di iOS sullo scuro per una goccia
in una lastra). Nel segmentato, come in iOS, un dito che scende su un segmento non scelto e scivola di lato si prende
la goccia: gli viene sotto, lo segue con un tick per segmento e sceglie dove la lascia; fermo, è un tocco; in verticale
è della pagina. **Nessuna firma pubblica cambia.** La demo è `AllGlassDemo` (`sample/…/catalog/`, nella galleria da
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

Quattro ingressi, un menu solo: il tasto che si trasforma, l'azione della barra, la riga e una card
qualunque — più il vecchio `FluidGlassMenuButton` — passano dallo stesso **pannello**, dallo stesso
**viaggio** e dallo stesso **materiale**. E **il menu si sceglie col dito che l'ha aperto**: tieni
premuto, trascina su una voce, rilascia; un rilascio altrove lo lascia aperto, e si sceglie con un
tocco. La regola e il suo perché sono in `regole.md` («Un menu si sceglie col dito che l'ha aperto»), la
demo è `sample/…/catalog/MenusDemo.kt` (in galleria: Chrome › Catalogo della 3.0 › «Menù liquidi»).

| | |
|---|---|
| `FluidContextAction(label, icon, destructive, enabled, startsSection, onClick)` | una voce. `destructive` è rossa (al massimo una per menu); `enabled = false` non fa tick e non si sceglie; `startsSection` apre una sezione, con una banda al posto della riga sottile come nei gruppi di un menu di iOS («Copia, Condividi» \| «Elimina»), e sulla prima voce non conta; sta prima di `onClick`, così la lambda in coda resta l'azione |
| `FluidMorphMenuButton(state, actions, onClick, modifier, text, icon, backdrop, enabled, opensOnTap)` + `FluidMorphMenuHost(state, backdrop)` | il tasto che si trasforma nel proprio menu, con `rememberFluidMorphMenuState()`. **L'host deve essere composto** (alla radice, o nel palco dove il menu deve crescere): l'apertura è sincrona e legge le sue misure, e senza host il tenuto premuto non apre niente e non vibra. `opensOnTap`: il tocco apre il menu e `onClick` non viene chiamato |
| `FluidBarAction(icon, contentDescription, onClick, …, actions, opensOn)` | l'azione della barra; con `actions` è l'ancora di un menu (`Expand`) che cresce dal tasto premuto, nell'host dei modali. `opensOn = FluidMenuOpensOn.Tap` per l'azione il cui unico mestiere è il menu (i tre puntini, «Ordina»): il tocco apre il menu e `onClick` non viene chiamato |
| `FluidListRow(contextActions = …)`, `Modifier.fluidContextMenu(actions)`, `rememberFluidContextMenu` | il menu contestuale: la riga si solleva (una superficie Fluid-physics) e il pannello cresce accanto a lei, da un seme sul perno più vicino |
| `FluidMenuOpensOn` | `LongPress` (di serie: il tocco resta l'azione del tasto, il menu si apre tenendo premuto) e `Tap`. Il tenuto premuto apre in tutti e due i casi: è lui che porta il dito sul menu |
| `longClickHaptic` su `fluidPressable`, `fluidRowPressable`, `FluidGlassIconButton` | cosa si sente al click lungo (`GestureStart` di serie), `null` per chi ha un colpo suo: un menu suona `Open` solo se si apre. `combinedClickable` non somma più il `LongPress` di piattaforma |
| `FluidAlert(…, inHost)` | l'avviso nell'host dei modali (di serie); `inHost = false` per il `Dialog`. `FluidPromptAlert` ci sta dentro |

**Il dito.** Tenuto premuto, il menu si apre e prende il dito. Nessuna riga da cambiare in un'app: un
relè in testa all'ancora (`Modifier.fluidMenuPointerRelay`, `FluidMenuHandOff`: **interni**) vede il
dito prima di qualunque gesto del tasto, lo passa a `FluidMenuSelection` quando il click lungo apre, e
consuma i suoi cambi già nel passaggio Initial, così il tasto nascosto, la lista attorno e la grana
restano fermi mentre il dito scorre sul menu. Una goccia di vetro lo segue di voce in voce. Un rilascio
senza essersi mossi oltre lo slop, fuori dalle voci, su un separatore o su una voce spenta lascia il menu
aperto; un tocco fuori, il back o lo scrim lo chiudono in silenzio.

**Un momento, un colpo.** `Open` all'apertura (uno solo, e solo se il menu si apre: lo suona il gesto che
apre, mai il layer), `Tick` per voce attraversata, `Tap` alla scelta, niente al congedo. Sui tasti alla
LibrePods resta il `PressDown` al contatto, che è del tasto. Un tasto che apre al tocco suona `Open` al
posto del `PressClick`.

**Il pannello, il viaggio, il materiale** sono interni (`FluidGlassMenuPanel`, `FluidMenuJourney`,
`FluidMenuLayer`, `FluidMenuAnchor`) e li usano tutti gli ingressi.

- *Il pannello*: righe ad altezza fissa che segue il font (48 dp a taglia di serie), separatori
  disegnati (mai scatole da mezzo dp: aggiungerebbero altezza che le celle non contano, e il dito sceglierebbe
  la riga sbagliata), sezioni, e **una** goccia che campiona il materiale del pannello (vetro su vetro)
  con tutte le sue trasformazioni nel proprio `layerBlock`.
- *Il viaggio*: parte dal tasto **premuto** (`fluidPressedBounds`: la sagoma che il dito sta gonfiando,
  non quella a riposo), il rimbalzo è un gonfiore uniforme attorno al centro del pannello, un congedo a
  metà apertura torna indietro dal punto in cui si trova con la sua velocità, e con le animazioni ridotte
  arriva senza viaggiare. Il menu che si trasforma e le azioni `Expand` partono dal tasto e lo coprono
  (si aprono sotto il dito); il contestuale cresce da un seme accanto alla riga e mai sopra.
- *Il materiale*: `panelOptics(compact)`, e la pelle e la lente passano dal tasto al pannello
  (`opticsFrom`): la faccia del tasto se ne va nel primo terzo del viaggio, le voci arrivano dal quarto.

**L'apertura è sincrona**: azioni, ancora, sequenza e viaggio nello stesso istante del gesto, e il tasto si
nasconde nel disegno (`hidden`, mai l'alpha di un layer sopra il vetro) nello stesso fotogramma in cui la
superficie compare sulla sua sagoma. Chi scrive un'ancora nuova dentro l'engine segue lo stesso schema:
un `FluidMenuAnchor`, il relè in testa, `drawWithContent { if (!anchor.hidden) drawContent() }`, e
l'apertura che suona `Open` solo se è riuscita.

**`FluidAlert` nell'host.** Con un `FluidGlassModalHost` in scope non apre più una finestra di
piattaforma: è una voce dell'host, quindi vetro vero (la pagina rifratta dietro, `panelOptics`), l'arrivo
che si posa e l'uscita che si vede anche dopo che il chiamante l'ha tolto dalla composizione
(`FluidGlassModalHostState.retire` / `finishExit`); i pulsanti hanno la goccia e l'aptica dei menu. Per un
avviso che deve stare sopra le finestre di piattaforma (un foglio, una tastiera) `inHost = false`, e senza
host c'è il `Dialog` di sempre. Con `FluidPromptAlert` la tastiera sale dentro l'host.

**Cosa cambia a vista in un'app che aggiorna**, senza toccare niente: i menu contestuali, le azioni di
barra con `actions` e `FluidGlassMenuButton` hanno il pannello, la goccia e il viaggio nuovi; il contestuale
non scala più il vetro; ogni `FluidAlert` è nell'host se l'app ne ha uno. **Firme**: parametri nuovi con
default (`startsSection` prima di `onClick` in `FluidContextAction`, `inHost` prima di `content` in
`FluidAlert`): il sorgente compila com'era, la JVM no, quindi un modulo già compilato va ricompilato.
`FluidMorphMenuButton` e `FluidMorphMenuState.open` vogliono un `FluidMorphMenuHost` composto.

**Provato, e non**: `FluidMenuSelectionTest`, `FluidMenuRelayTest`, `FluidMenuPanelTest`,
`FluidMenuJourneyTest`, `FluidMenuMotionTest` (puri) e `FluidMenuHandOffTest` (Robolectric, con l'aptica
registrata). Non sono provati il vetro e la goccia sul vetro (gli host dei test non hanno fondale),
`opensOn`/`opensOnTap`, il trascinamento fra i pulsanti di un avviso e la tastiera sopra un avviso con un
campo: sono nelle voci della Beta 2 di «Da provare».

## Fluid-physics — il vetro che cambia forma (1.9.0, adottato dai preset in 1.9.7)

`dev.antigravity.fluidengine.ui.fluidphysics`: qualsiasi silhouette di vetro diventa qualsiasi
altra, con la rifrazione che segue la forma. Il vocabolario:

- `FluidForm.Slab` (rettangoli arrotondati: quadrato, cerchio, capsula), `FluidForm.Poly` (sagome
  libere; `FluidFormPresets.fromFreehand` per i tratti a mano), `FluidForm.Group` (fino a sei Slab
  resi come una superficie sola, ponte liquido compreso).
- `rememberFluidPhysicsState(initial)` + `state.morphTo(target, spec)` — sospende fino alla
  posa; ritargeting a metà volo legittimo; nessuna coppia vietata (gruppo↔sagoma passa da uno
  scalo automatico).
- `Modifier.fluidPhysicsSurface(state, backdrop, tint, role, optics, tier, tintFrom, tintBlend)` —
  stessa grammatica di `glassSurface`. `Modifier.fluidPhysicsContent(state, role)` per il
  contenuto: viaggia col centro della sagoma, zoom uniforme, mai stirato.
- `FluidPhysicsTier`: Full (SDK 33+) / Balanced / Lite. La geometria è identica su tutti e tre.

### Quello che arriva gratis aggiornando l'engine (1.9.7)

**Non serve chiamare niente di nuovo.** I preset che già usi sono passati al motore, quindi ogni
app che aggiorna se li ritrova trasformati:

- **La card che si espande** (`FluidGlassModalPortal` con `origin`, e la presentazione `Expand`):
  la finestra è una superficie Fluid-physics che viaggia dal rettangolo dell'ancora al pannello con
  la rifrazione addosso. `Modifier.fluidExpandOrigin` fa il resto **da solo**: registra l'immagine
  della riga e la nasconde mentre la finestra è in scena, così il tasto *diventa* il pop-up invece
  di restare lì sotto. Il call-site non cambia di una riga.
- **`FluidMorphMenu`** (`FluidMorphMenuButton` + `FluidMorphMenuHost` alla radice): il tasto che si
  espande nel proprio menù contestuale. Questo va chiamato, ed è il sostituto di
  `FluidGlassMenuButton` (deprecato dalla 2.12.0) dove serve la trasformazione.
- **Restano com'erano, per scelta**: `FluidGlassModalPresentation.Sheet`. (Il menù contestuale delle righe
  restava com'era fino alla 2.13; dalla 3.0 ha il pannello, la goccia e il viaggio dei menu nuovi, e la riga
  sollevata è una superficie Fluid-physics: vedi «I menu liquidi».)

`state.driveExternally(from, to, progress, overshootInflationX/Y)` è il gancio per un componente
che un orologio ce l'ha già: la fisica diventa la *finestra* e il progresso resta quello delle
molle del chiamante. Due orologi sulla stessa superficie sono un disallineamento garantito.

### La molla, e quando i contenitori rimbalzano

Dalla 3.0 il default di `morphTo` è **una molla sola** (`FluidPhysicsJourney.Spring`: 0,62 / 0,36 s, i token
del menu) che parte alla velocità del momento — un ritargeting eredita quella del viaggio interrotto, un gesto
passa la sua con `initialVelocity`. La rincorsa della casa è un preset esplicito, `FluidPhysicsJourney.House`,
per i morph dimostrativi avviati da un tocco secco (il Playground). Oltre l'1 la sagoma è il traguardo gonfiato
attorno al suo centro (guadagno 0,6, tetto 8% e 12 dp per lato), e in viaggio il corpo è liquido (sotto). Nuovi su
`fluidPhysicsSurface`: `opticsFrom`/`opticsBlend` (il materiale che viaggia) e `pressState` (lente e bagliore
sotto il dito). A riposo una superficie fisica è una lastra: stessi numeri, bordo e ombra di `glassSurface`.

**Vetro liquido: la goccia (3.0, dopo la beta 3).** Una superficie che viaggia fra due lastre è una goccia: 48 punti presi dalla sagoma a riposo di quell'istante, ognuno su una molla verso il suo punto, dura accanto all'ancora (il tasto, la riga, il centro di partenza: k 1500) e morbida lontano (k 320, L 110 dp), ζ 0,72, smorzamento relativo vicino e assoluto lontano. Vicino segue senza ritardo, lontano resta indietro: la coda, che fa da collo; lo smooth min resta solo ai gruppi. Una tensione del bordo leggera (0,2 di quella del banco, sullo scarto dal riposo) tiene insieme i vicini senza smussare gli angoli. Andata ~0,28 s (`FluidMotion.dropOpen()`, dalla 3.0.4; era 0,42), ritorno ~0,50 s (`dropClose()`), gonfiore `1 + 0,55·(m − 1)` oltre l'1, lente in moto. Le voci arrivano mentre il corpo si forma (0,4–0,75, dalla 3.0.4), se ne vanno per prime (0,94–0,62), seguono il baricentro della goccia e stanno sempre dentro il vetro di adesso (`dropInnerClip`). Primo fotogramma = lastra del mittente; sotto ¼ px e ferma torna la lastra al bit, senza gradino (il bordo è una fascia col profilo di quello a riposo) e senza fotogrammi chiesti. Rallenta con la scala dell'animatore; animazioni ridotte: niente goccia; sotto API 31 lo stesso contorno come path. Vale per menu del tasto, azioni della barra, contestuale (il menu esce dal punto del dito, la riga sollevata resta una lastra), card, avviso, popover e `morphTo` fra lastre; restano geometrici i gruppi della barra ancorata, le sagome libere e `FluidSearchField`. Token `FluidMotion.Drop*` e `LiquidContent*`; `LocalFluidPhysicsLiquid` e `LocalFluidPhysicsDropTuning` sono manopole di debug della galleria.

E la correzione a una regola che questa nota dava per assoluta: **la card ancorata rimbalza, ed è
voluto** — richiesta esplicita di Alessio, "eccessivamente in fuori su tutti i lati, poi torna".
Il rimbalzo è *geometrico* (`overshootInflation`, un gonfiore uniforme della silhouette che si
posa) e scala con la taglia del pannello, con un tetto sull'asse X perché al picco la card non
finisca a ridosso dei bordi. Resta vero che una superficie di vetro **non si scala col layer** per
ottenere un rimbalzo: vedi le tre trappole in `regole.md`.

Il banco di prova è la scheda Playground dell'app Fluid Glass (il modulo `sample/` del repo
dell'engine), pubblicata sul Pampa Store.

## La pressione (2.11.0, e come LibrePods dalla 3.0)

Tutto in `ui/fluid/FluidPress.kt` (la pressione, la trasformazione, lo stato) e
`ui/fluid/FluidButtonGesture.kt` (il gesto). **I pulsanti crescono** (`FluidPressFeedback.Expand`
col testo, `ExpandRound` per i tasti tondi solo icona), seguono il dito e vibrano. **Le superfici
cedono** (`yielding`, il default di `fluidPressable`): una card che crescesse invaderebbe le vicine.
Animazioni ridotte: crescita dimezzata, niente deriva né allungamento, pressione comunque intera.

**Dalla 3.0 un pulsante è quello di LibrePods** — scelta di Alessio, e il codice è portato da lì
(GPL-3.0, `LICENSES/LibrePods.md`). Quello che cambia rispetto alla 2.12:

- **Premuto al contatto.** Senza ritardo, dentro una lista come in una barra (prima: 100 ms di
  attesa dentro una pagina che scorre, e `LocalFluidPressImmediate` per la chrome — oggi non fa più
  niente).
- **Tiene il dito** (scelta di Alessio dopo la beta 2). Un dito che scende su un pulsante è del
  pulsante fino al sollevamento: la lista sotto (`LazyColumn`, `verticalScroll`, un pager, il back a
  scorrimento) non scorre, il pulsante cresce, segue il dito e vibra; per scorrere si parte fuori dai
  pulsanti. È la regola della 2.12 senza i 100 ms di attesa (`FluidPressGrip.HoldsFinger`, di serie):
  il gesto consuma gli spostamenti nel passaggio Main, prima di ogni antenato. LibrePods non prende mai
  il gesto, e la pagina scorreva mentre si teneva un tasto: in mano non piaceva. **`FluidChip` e
  `FluidColorDot` cedono il dito alla fila** (`fluidPressable(grip = FluidPressGrip.YieldsToScroll)`):
  appena la fila prende il gesto si sciolgono (calano, smettono di seguire il dito, `PressRelease`) e
  non cliccano, come in iOS. Righe e card non cambiano. Un pulsante che il chiamante ripete su ogni riga di una lista che scorre, o che sta in un pager, passa `grip = FluidPressGrip.YieldsToScroll` (i pulsanti dell'engine lo prendono tutti, il «Riserva» di `FluidModelPickerPortal` lo fa già): lì non c'è un fuori da cui partire. I tondi crescono ×1,5 fino a 48 dp; oltre, di 24 dp in tutto (12 per lato, `RoundLongSideCapDp`); le misure fuori dalle tre taglie: `FluidGlassIconButton(size = …dp)`.
- **Il click**: di `clickable` per un tocco, del gesto per un dito che ha trascinato (`clickable`
  annulla alla vista di un cambio consumato): scatta se il dito si solleva sul pulsante più 16 dp
  (`FluidPressClickSlack`, o il tocco minimo se più largo), anche dopo aver vagato, con lo stesso
  `PressClick`; oltre il margine solo `PressRelease`. Tastiera (Invio, Spazio, fuoco, D-pad), TalkBack e
  ruolo restano di `clickable`. Il **click lungo** (`fluidPressable(onLongClick = ...)`) scatta se il dito è rimasto
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
zero mentre il telefono vibra, e le vibrazioni di tocco le filtra gia' il sistema (dalla 2.11.0
anche su Android 11-12, dove partono come `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`).

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
vibrazione per momento» è vero? — scrivendo cosa è stato chiesto al vibratore e quando. È un decoratore
(`TracingFluidHaptics`, da `FluidHapticTrace.wrap`): inoltra ogni chiamata al motore vero tale e quale e ne
tiene una copia in una lista limitata e osservabile (`entries`, `rows()`, `recording`, `clear()`); senza
`ProvideFluidHapticTrace(trace) { }` nessuno lo costruisce e non costa niente. Va **dentro** `FluidTheme`
(è lui a installare l'aptica) e un `FluidTheme` annidato lo toglie: dentro, `ProvideFluidHapticTrace { }`
senza argomenti rimette quello ereditato (`LocalFluidHapticTrace`). L'esito di ogni voce
(`FluidHapticTraceOutcome`: suonata, fusa, scartata) è una **stima**: il registro non può chiedere al motore
se un evento è suonato, quindi rifà le sue due reti con le sue costanti, e dice cosa *avrebbe* fatto il
motore di serie. La galleria lo mostra in Controlli › Registro aptico e in piccolo sopra a tutto; lì la
grana si sceglie fra `LibrePods` e `Fluid` e, per `Fluid`, si tara con i cursori (partenza, spegnimento,
passo, forza).

Regole: non chiamare mai `LocalHapticFeedback` direttamente in un'app (il vocabolario e' uno);
`fluidPressable(haptic = null)` per un controllo che vibra gia' per conto suo (per un tasto che cresce
spegne anche il contatto, il rilascio e la grana) (una pillola che fa
`Threshold` allo swipe); i tick continui (`Tick`, `FrequentTick`, `WaitTick`, `DragTick`)
spariscono in risparmio energetico e si diradano a 40 ms da soli. `DragTick` (2.11.0) e' la tacca
di un dito che scorre su un controllo premuto o di uno slider che passa un passo. L'interruttore e' `EngineSettings.hapticsEnabled`,
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

## Schermi larghi (2.5.0)

Una navigazione sola su ogni formato: la pillola. Lo spazio di un tablet si usa dentro le pagine.

```kotlin
// Elenco che apre cose: due pannelli su uno schermo largo, l'elenco e basta su uno stretto.
FluidListDetailScaffold(
  ambient = identita.ambient(),
  list = { twoPane ->
    FluidScreen(title = "Elenco") {
      items(cose) { cosa ->
        FluidListRow(
          title = cosa.titolo, subtitle = cosa.sotto,
          selected = twoPane && cosa.id == scelta?.id,
          disclosure = !twoPane,
          onClick = { scelta = cosa },          // su un pannello solo: apri come prima
        )
      }
    }
  },
  detail = {
    FluidDetailContent(item = scelta, key = { it?.id }) { cosa ->
      if (cosa == null) FluidDetailPlaceholder("Niente di aperto", "Scegli qualcosa dall'elenco.")
      else FluidScreen(title = cosa.titolo) { /* ... */ }
    }
  },
)

// Cruscotto: sezioni in colonne quando c'e' posto.
val metrics = rememberFluidScreenMetrics()
FluidScreen(title = "Home", contentMaxWidth = FluidColumnsDefaults.WideContentMaxWidth, metrics = metrics) {
  fluidColumns("home", metrics.columns(), listOf(
    FluidColumnSection("a") { /* header + gruppo */ },
    FluidColumnSection("b") { /* ... */ },
  ))
  fluidGridItems(card, metrics.columns(FluidColumnsDefaults.MinCard), key = { it.id }) { Card(it) }
}
```

Regole: **un fondale per finestra** (lo scaffold lo fa da se'); **sul telefono non cambia niente**
(una colonna = gli item di sempre); un elenco lungo non va in `fluidColumns`, va in
`fluidGridItems` o resta un elenco. Dettagli in `docs/03-design-system.md`.

**Tastiera.** `FluidChromeController.refreshFront()` aggiorna la pagina davanti (la stessa che la
barra rifrange) chiamando il suo `onRefresh`: l'app lo lega a Ctrl+R / F5 nell'`onKeyDown`
dell'Activity. Una pagina che sta gia' aggiornando non viene richiamata; il dettaglio di due
pannelli non si registra, quindi si aggiorna l'elenco.

## Wear OS (2.10.0)

`engine-wear`, opt-in: entra solo nel modulo dell'orologio (`engine-install.ps1 -Modules ...,engine-wear`).
Dettagli in `docs/09-wear.md`.

- **Tema**: `FluidWearTheme(brand)` mette `FluidTheme` fuori (il vetro, l'aptica e `fluidPressable` lo
  leggono da li') e Material 3 per Wear dentro, con schema colori mappato dal telefono
  (`fluidWearColorScheme`), tipografia Wear in Inter (`fluidWearTypography`) e forme continue
  (`fluidWearShapes`). Le schermate leggono `androidx.wear.compose.material3.MaterialTheme`.
- **Componenti**: `FluidGlassDisc` (controllo rotondo: `glassControlSurface`; il `FluidGlassBurst` e' facoltativo e spento di serie dalla 3.0.4),
  `FluidGlassCapsule` (vetro flottante con testo), `FluidEdgeProgressRing` (si ridisegna solo quando
  l'arco si sposta di mezzo pixel), `FluidArcRow` (azioni lungo il bordo), `Modifier.fluidRotarySteps`
  (ghiera a scatti e corona continua, entrambe in passi), `FluidWearListRow`, `FluidWearPill`,
  `FluidWearList` (3.0: l'elenco con gli stati avviso, caricamento, errore con «riprova» e dati vecchi).
- **Misure**: `FluidWearDimens`, mai dp a mano in una schermata dell'orologio.
- **Always-on**: `rememberFluidAmbientState(activity)` + `LocalFluidWearAmbient`; in ambient niente
  vetro vivo, niente animazioni; `Modifier.fluidBurnInShift` solo dove il pannello lo chiede.

**Il vetro fermo.** Una lastra ricattura lo sfondo solo se lo sfondo si e' ridisegnato o se lei si e'
spostata *rispetto* allo sfondo (2.10.0: prima contava anche la posizione assoluta, e ogni swipe di
pagina ricalcolava tutte le lastre a ogni fotogramma per un'immagine identica). Quindi:

- quello che cambia spesso (anello, orologio, testo che scorre) sta **fuori** dalla sorgente del vetro,
  o ogni suo fotogramma ricalcola ogni lastra;
- gli effetti disegnati *sopra* il vetro (`GlassTouchHighlight`, `FluidGlassBurst`) non ricatturano
  niente;
- `FluidGlassDiagnostics.enabled` + `totalCaptures` (e, dalla 2.13.0, `totalExports`: i materiali
  registrati per il vetro su vetro) in una build di debug: a schermo fermo il numero
  non deve muoversi.
