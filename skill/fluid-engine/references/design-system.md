# Inventario di `engine-ui`

Tutto sotto `dev.antigravity.fluidengine.ui` (`fluid/` e `theme/`). Se stai per scrivere un
componente, cerca prima qui: quasi tutto quello che serve a una schermata esiste già.

## Struttura di una schermata

| | |
|---|---|
| `FluidScreen(title, subtitle, onBack, actions, titleFacets, isRefreshing, onRefresh, overlay) { LazyListScope }` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare quando il titolo passa sotto; `overlay` riceve il backdrop per chrome flottante sicuro |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidFloatingTabBar` / `FluidTabRail` | navigazione principale a pillola (`FluidTabItem`): la barra in basso, che si ripiega scorrendo (vedi `rememberFluidFloatingTabBarScrollConnection` più sotto), e il rail per gli schermi larghi |
| `FluidGlassModalPortal` (+ `FluidGlassModalHost` alla radice), `FluidAlert`, `FluidGrabber` | pop-up e fogli in vetro dentro la composizione, e avvisi (`FluidAlertAction`) |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe (`FluidSectionAnchor`) |
| `FluidNotificationHost`, `rememberFluidNotificationHostState` | notifiche contestuali dentro l'app (`FluidNotification`, `FluidNotificationTone`, `FluidNotificationAction`); la card si trascina, vedi sotto |
| `ProvideFluidChrome`, `rememberFluidChromeController` | registro della barra, inset della tab bar, bus "torna in cima" |
| `FluidNavHost`, `rememberFluidNavController`, `composable(…)`, `FluidPageHost`, `FluidPageHostState`, `FluidPageTransition`, `FluidBackSwipe` | le pagine (3.0): opache, da destra, il back sotto il dito; vedi «Le pagine e `FluidNavHost`» più sotto |
| `FluidRouteMotionHost`, `FluidRouteMotion`, `rememberRouteMotionSignals` | il vecchio involucro delle rotte: per chi non migra a `FluidNavHost`, e **sfoca ancora di 6 dp** la pagina che esce; dentro un `FluidNavHost` non si mette (non compila) |
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
toccando l'interruttore. `onCheckedChange = null` quando è la riga
a possedere il `toggleable` e il target da 48dp. `backdrop` di default è il canvas della schermata.
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
- `FluidMotion` (molle), `FluidMotionScheme` (le passa a Material), `FluidMotionPolicy`
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
- `FluidContextAction`, `fluidContextMenu`, `rememberFluidContextMenu`, `fluidContextMenuAnchor`,
  e `FluidListRow(contextActions = ...)` — il menu contestuale iOS
- `FluidMorphMenuButton` + `FluidMorphMenuHost` (con `rememberFluidMorphMenuState()`) — il tasto
  che si trasforma nel proprio menu: una superficie sola, che viaggia dal tasto al pannello e torna
  tasto. Lo stato e l'host stanno alla radice, accanto a `FluidGlassModalHost`. Sostituisce
  `FluidGlassMenuButton`, che mostra il pannello *sopra* il tasto (due superfici)
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
  (`GlassOptics.edgeFloor`) e il trascinamento e' criticamente smorzato
  (`GlassDragAnimation.velocityDampingRatio`)
- **Deprecati** (restano fino alla 3.0): `FluidTabBar` e `FluidFoldingTabBar` — delegano a
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

L'ottica la disegna la libreria `backdrop` di Kyant (Apache-2.0) copiata in `ui/glass/backdrop/`.
Non scrivere shader di vetro nuovi accanto a quella: se serve un'ottica diversa, è un `GlassOptics`
diverso.

## Il tema

```kotlin
FluidTheme(
  settings = engineSettings,                 // EngineSettings: themeMode, accentMode, …
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
un terzo. Si torna indietro come in iOS: la pagina segue la **posizione vera del dito** (`touchX`, mai il
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
| `FluidPageMotion` | le costanti e le funzioni pure dell'host (`CommitFraction`, `FlingDpPerSecond`, `backRelease`, `pageFrame`, `classifyStackChange`, …) |

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
   pagine Fluid non hanno, quindi non compilano. `FluidRouteMotionHost` resta com'era per chi non migra,
   e sfoca ancora di 6 dp la pagina che esce.
5. `peer = FluidPeerMove { from, to -> … }` dalla funzione che ordina le schede, che l'app ha già
   (positivo se `to` sta dopo `from`, zero se non sono pari).
6. Una shell con una tab bar che cambia visibilità fra due pagine legge `FluidPageHostState.progress`
   (si passa lo `state` a `FluidNavHost`).

Resta tutto di navigation: deep link, stato salvato, `ViewModel` per voce, `savedStateHandle`,
`navigation { }` annidati, `dialog { }`. `engine-ui` espone `navigation-compose` 2.9.8 come `api`: la
versione che hanno già tutte le app tranne Fluidify (2.8.5, che passa a 2.9.8). Cosa cambia a vista in
un'app che migra: pagine opache, angoli del telefono, la pagina sotto che arretra di un terzo, il back
che non corre più davanti al dito. **Non c'è ancora la barra ancorata** (passo successivo): ogni
`FluidScreen` disegna la sua barra, che quindi viaggia con la pagina. La demo è la riga «Pagine» nella
scheda Chrome della galleria (`PagesDemoActivity`).

## I pezzi che le app si riscrivevano da sole (3.0)

Ogni app aveva scritto a mano un pezzo che l'engine non aveva, e nel proprio KDoc diceva perché. Qui sono
tutti, con la ricetta della 3.0 (una pressione, una vibrazione, vetro su vetro da solo) e **nessuna firma
rotta**: dove un componente esistente cambia, sono parametri con default in coda. Le demo stanno in
`sample/…/catalog/` (`RowsAndSheetsDemo`, `CapsulesAndPartsDemo`, non ancora nella galleria) e nella
scheda Controlli (`gapsDemoItems()`).

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

Il respiro (`FluidPulseMillis` 900, `FluidPulseLowAlpha` 0,35) è ancora una costante interna con un TODO:
va portato in `FluidMotion` come token.

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
- **Restano com'erano, per scelta**: il menù contestuale delle righe (`fluidContextMenu`, la sua
  riga sollevata è già la sua storia) e `FluidGlassModalPresentation.Sheet`.

`state.driveExternally(from, to, progress, overshootInflationX/Y)` è il gancio per un componente
che un orologio ce l'ha già: la fisica diventa la *finestra* e il progresso resta quello delle
molle del chiamante. Due orologi sulla stessa superficie sono un disallineamento garantito.

### La molla, e quando i contenitori rimbalzano

Dalla 3.0 il default di `morphTo` è **una molla sola** (`FluidPhysicsJourney.Spring`: 0,62 / 0,36 s, i token
del menu) che parte alla velocità del momento — un ritargeting eredita quella del viaggio interrotto, un gesto
passa la sua con `initialVelocity`. La rincorsa della casa è un preset esplicito, `FluidPhysicsJourney.House`,
per i morph dimostrativi avviati da un tocco secco (il Playground). Il rimbalzo è sempre geometrico: oltre l'1
la sagoma è il traguardo gonfiato attorno al suo centro (guadagno 0,6, tetto 8% e 12 dp per lato). Nuovi su
`fluidPhysicsSurface`: `opticsFrom`/`opticsBlend` (il materiale che viaggia) e `pressState` (lente e bagliore
sotto il dito). A riposo una superficie fisica è una lastra: stessi numeri, bordo e ombra di `glassSurface`.

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
- **Componenti**: `FluidGlassDisc` (controllo rotondo: `glassControlSurface` + `FluidGlassBurst`),
  `FluidGlassCapsule` (vetro flottante con testo), `FluidEdgeProgressRing` (si ridisegna solo quando
  l'arco si sposta di mezzo pixel), `FluidArcRow` (azioni lungo il bordo), `Modifier.fluidRotarySteps`
  (ghiera a scatti e corona continua, entrambe in passi), `FluidWearListRow`, `FluidWearPill`.
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
