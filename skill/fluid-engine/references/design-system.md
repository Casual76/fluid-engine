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
| `FluidRouteMotionHost`, `FluidRouteMotion`, `rememberRouteMotionSignals` | transizioni di rotta |
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
| `FluidListRow(title, subtitle, eyebrow, meta, tone, badge, leading, onClick, onLongClick)` | la riga |
| `FluidListDivider()` | il separatore rientrato |
| `FluidCard`, `FluidHero`, `FluidEditorialCard` | superfici (`FluidCard(highlighted = true)` per un riquadro che dice una cosa, `FluidHero` per l'apertura di una schermata piena di dati) |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note: `FluidSectionHeader` è la didascalia quieta sopra un gruppo, `FluidSectionTitle` (eyebrow in accento + titolo grande) apre una sezione di pagina |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState(title, detail)`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`, `SyncStatus.noticeMessage()`, `SyncStatus.lastSyncLabel()` | stato della sincronizzazione |
| `FluidPillTabs`, `FluidAccentLabel` | varie |

## Controlli

`FluidButton(text, onClick, style, size, enabled, loading, fillWidth, leading)` con
`FluidButtonStyle` = Filled / Tinted / Plain / Destructive. Dalla 2.11.0 **cresce** sotto il dito
(vedi "La pressione" qui sotto), contenitore compreso: prima cedeva solo l'etichetta.

`FluidSwitch(checked, onCheckedChange, enabled, backdrop)` — il LiquidToggle di Kyant0, 64x28 con
pomello a pillola 40x24. Il **pomello è l'unica lastra**: a riposo è bianco pieno, tenuto premuto il
bianco sparisce e sotto si apre una copia della pista schiacciata, che è ciò che rifrange. Un tocco
ovunque sul controllo lo commuta; trascinarlo è il di più, e dalla 2.12.0 un pomello trascinato non
fa scorrere la pagina (un dito caduto sul pomello e mosso di uno slop è suo fino al rilascio; sotto
lo slop il tocco commuta ancora). `onCheckedChange = null` quando è la riga
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
`FluidTextField`, `FluidColorDot`, `FluidBarAction(icon, contentDescription, onClick)`,
`FluidGlassIconButton`, `FluidGlassButton`, `glassControlSurface`, `fluidStaticGlassSurface`,
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
- `GlassDefaults`, `rememberGlassBackdrop`, `rememberCombinedGlassBackdrop`, `LocalGlassBackdrop`,
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
  2.12.0), `fluidRowPressable`; `LocalFluidPressImmediate` per la chrome in cui non scorre niente
- La pressione (2.11.0): `FluidPressFeedback` (`Expand` per i pulsanti, `yielding(scala)` per le
  superfici, `None`), `rememberFluidPressState`, `FluidPressState.progress`

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

Due tempi, e la differenza è **chi sta aspettando**:

- **Morph avviati da un tocco secco** (il Playground): il viaggio della casa, cioè il default di
  `morphTo` con `spec = null` — rincorsa in accelerazione, molla sottosmorzata, una posata sola.
- **Cose che l'utente sta già aspettando** (un long-press di 400 ms, un modale che si apre): la
  rincorsa lunga si legge come lentezza. Molla sola, o rincorsa breve e ripida.

E la correzione a una regola che questa nota dava per assoluta: **la card ancorata rimbalza, ed è
voluto** — richiesta esplicita di Alessio, "eccessivamente in fuori su tutti i lati, poi torna".
Il rimbalzo è *geometrico* (`overshootInflation`, un gonfiore uniforme della silhouette che si
posa) e scala con la taglia del pannello, con un tetto sull'asse X perché al picco la card non
finisca a ridosso dei bordi. Resta vero che una superficie di vetro **non si scala col layer** per
ottenere un rimbalzo: vedi le tre trappole in `regole.md`.

Il banco di prova è la scheda Playground dell'app Fluid Glass (il modulo `sample/` del repo
dell'engine), pubblicata sul Pampa Store.

## La pressione (2.11.0, e di chi è il dito dalla 2.12.0)

Tutto in `ui/fluid/FluidPress.kt`. **I pulsanti crescono** (`FluidPressFeedback.Expand`: 6 dp sul
lato corto, max 14 sul lungo, mai oltre il 14%), si inclinano verso il dito, si allungano nel verso
in cui scorre e scattano (`DragTick` ogni 10 dp, mai piu' di uno ogni 55 ms). **Le superfici
cedono** (`yielding`, il default di `fluidPressable`): una card che crescesse invaderebbe le vicine.
Per tutti, **la pressione arriva sempre in fondo**: un rilascio a meta' corsa aspetta (un tap di
20 ms mostra la pressione intera). Animazioni ridotte: crescita dimezzata, niente inclinazione,
pressione comunque intera.

**Un pulsante si trascina sempre (2.12.0).** Per i pulsanti che crescono il puntatore e'
`fluidButtonGesture`, non `clickable`: per **100 ms** il gesto non e' di nessuno e non si consuma
niente (una passata veloce che parte sul pulsante scorre ancora la pagina, senza pressione ne'
click); un dito che resta giu' quel tanto senza che la pagina se lo sia preso ha *premuto* il
pulsante, e da li' il gesto e' **suo fino al rilascio** — segue il dito anche fuori dai bordi, si
inclina, scatta, e ogni cambio e' consumato, quindi una `LazyColumn` o un `verticalScroll` attorno
non partono. Al rilascio il click scatta se il dito e' dentro il pulsante **piu' 16 dp** (o il
margine dell'area di tocco minima, se maggiore), altrimenti la pressione si scioglie e basta; un tap
piu' breve di 100 ms mostra la pressione intera e clicca. Il click lungo scatta solo se il dito e'
rimasto entro lo slop da dove e' sceso (`fluidPressable(longClickHaptic = ...)`, `null` per un
controllo col suo colpo), e dopo non c'e' click. Un mouse e' del pulsante subito. `clickable` resta
per ruolo, TalkBack, tastiera e fuoco; le superfici che cedono (card, tessere, `fluidRowPressable`)
non cambiano.

**Nella chrome, subito.** Dove non scorre niente — una barra, un'intestazione, un foglio — i 100 ms
sono solo una pressione in ritardo: `LocalFluidPressImmediate provides true` li azzera. L'engine se lo
da' da solo (tasto indietro e azioni della barra di `FluidScreen`, intestazione dei pannelli a pagina
intera; il corpo di `FluidScreen` lo rimette a `false`, `FluidHero` resta com'e'); un'app lo da' alla
sua chrome fissa e **mai attorno a qualcosa che scorre**, o la lista si scorrerebbe solo dai margini.

Regole: `fluidPressable` va **prima** di `clip`/`background`, o si muove solo il contenuto. Un tasto
di vetro ha **una** pressione: `val p = rememberFluidPressState()` passato sia a
`glassControlSurface(pressState = p)` sia a `fluidPressable(pressState = p)`; senza, il vetro segue
il dito da solo come sempre. Il banco di prova e' la sezione "Tocco" della scheda Controlli del
sample; i test Compose (pulsante, slider e interruttore in `LazyColumn`/`verticalScroll`, tap rapido
in lista, notifica trascinata) stanno in `sample/src/testDebug` per non portare Robolectric nelle app.

## Aptica (1.19.0)

`FluidHaptics` e' il vocabolario di cio' che si sente sotto il dito, uno per tema: i componenti
dell'engine lo usano da soli (un tasto di vetro fa `Tap`, uno switch `ToggleOn`/`ToggleOff`, un
menu che si apre `Open`, l'indice di sezione `Tick`), le app aggiungono i loro momenti con
`rememberFluidHaptics().play(FluidHapticEvent.X)`: `Threshold` per una soglia raggiunta,
`GestureStart`/`GestureEnd` per un trascinamento, `Success`/`Warning`/`Error` per gli esiti,
`AlertWatch`/`AlertAlarm`/`AlertClear` per un'allerta che cambia livello.

Il permesso `VIBRATE` lo dichiara `engine-ui`: senza, ogni composizione fallisce con una
SecurityException che il motore ingoia, e l'aptica non si sente da nessuna parte (successo davvero,
1.20.2 -> 1.21.0). L'impostazione tattile di sistema non si legge per decidere: su One UI resta a
zero mentre il telefono vibra, e le vibrazioni di tocco le filtra gia' il sistema (dalla 2.11.0
anche su Android 11-12, dove partono come `AudioAttributes.USAGE_ASSISTANCE_SONIFICATION`).

Regole: non chiamare mai `LocalHapticFeedback` direttamente in un'app (il vocabolario e' uno);
`fluidPressable(haptic = null)` per un controllo che vibra gia' per conto suo (una pillola che fa
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
