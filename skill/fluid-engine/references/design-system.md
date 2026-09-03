# Inventario di `engine-ui`

Tutto sotto `dev.antigravity.fluidengine.ui` (`fluid/` e `theme/`). Se stai per scrivere un
componente, cerca prima qui: quasi tutto quello che serve a una schermata esiste già.

## Struttura di una schermata

| | |
|---|---|
| `FluidScreen(title, subtitle, onBack, actions, titleFacets, isRefreshing, onRefresh, overlay) { LazyListScope }` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare quando il titolo passa sotto; `overlay` riceve il backdrop per chrome flottante sicuro |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidTabBar` / `FluidTabRail` | navigazione principale a pillola (`FluidTabItem`) |
| `FluidSheet`, `FluidAlert`, `FluidGrabber` | fogli modali e avvisi (`FluidAlertAction`) |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe (`FluidSectionAnchor`) |
| `FluidNotificationHost`, `rememberFluidNotificationHostState` | notifiche contestuali dentro l'app (`FluidNotification`, `FluidNotificationTone`) |
| `ProvideFluidChrome`, `rememberFluidChromeController` | registro della barra, inset della tab bar, bus "torna in cima" |
| `FluidRouteMotionHost`, `FluidRouteMotion`, `rememberRouteMotionSignals` | transizioni di rotta |
| `rememberFluidTouchOrigin`, `MotionOrigin` | apertura dal punto toccato |

## Contenuto

| | |
|---|---|
| `FluidListGroup { }` | il contenitore della lista raggruppata |
| `FluidListRow(title, subtitle, eyebrow, meta, tone, badge, leading, onClick, onLongClick)` | la riga |
| `FluidListDivider()` | il separatore rientrato |
| `FluidListItem` | riga semplice, senza gruppo |
| `FluidCard`, `FluidHeroCard`, `FluidEditorialCard` | superfici |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState(title, detail)`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`, `SyncStatus.noticeMessage()`, `SyncStatus.lastSyncLabel()` | stato della sincronizzazione |
| `FluidQuickAction`, `FluidTopHeader`, `FluidPillTabs`, `FluidAccentLabel` | varie |

## Controlli

`FluidButton(text, onClick, style, size, enabled, loading, fillWidth, leading)` con
`FluidButtonStyle` = Filled / Tinted / Plain / Destructive.

`FluidSwitch(checked, onCheckedChange)` — passa `onCheckedChange = null` quando è la riga a possedere
il `toggleable` e il target da 48dp.

`FluidSegmentedControl(options, selected, onSelect, label)`, `FluidChip(label, selected, onClick)`,
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
- `GlassDefaults`, `rememberGlassBackdrop`, `rememberCombinedGlassBackdrop`, `LocalGlassBackdrop`,
  `LocalFluidCanvasBackdrop`, `GlassTint`, `GlassEdge`, `GlassFalloff`, `GlassOptics`, `GlassRole`
- `FluidAmbient`, `FluidAmbientCanvas` — il fondale per schermata; `FluidScreen(ambient = ...)`
- `FluidGlassModalHost`, `FluidGlassModalPortal`, `FluidGlassModalPresentation`,
  `fluidExpandOrigin`, `fluidGlassModalObscured` — il pop-up in vetro dentro la composizione
- `FluidContextAction`, `fluidContextMenu`, `rememberFluidContextMenu`, `fluidContextMenuAnchor`,
  e `FluidListRow(contextActions = ...)` — il menu contestuale iOS
- `FluidGlassMenuButton` — il tasto che si trasforma nel proprio menu
- `FluidFoldingTabBar`, `rememberFluidBarFold` — la barra che si piega scorrendo
- `AccentPreset`, `fluidAccentPresets`, `FluidDefaultBrand`, `fluidBrandAccent(isDark, brand)`
- `fluidColorScheme(settings, isDark, brand, dynamicScheme)` — la palette fuori da una composizione,
  per widget e notifiche
- Modificatori: `fluidPressable`, `fluidRowPressable`

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

**Il vetro sta su quello che ha sotto, non su tre strati sotto.** Un controllo appoggiato a una
barra deve rifrangere *la barra*: darle la pagina ci apre dentro un buco, ed è esattamente quello
che sembra. La barra pubblica il proprio materiale con `exports = unAltroGlassBackdropState`, e chi
ci sta sopra lo compone con `rememberCombinedGlassBackdrop(pagina, barra)`. `FluidScreen` e
`FluidTabBar` lo fanno già e passano il risultato in `LocalGlassBackdrop`, quindi un
`FluidBarAction` dentro `actions` è a posto senza che l'app tocchi niente.

**La taratura sta nella lente, non nella sfocatura.** Il raggio di riferimento e' 2 dp: sopra gli
~8 dp il vetro trasmette la media di quello che ha dietro invece dell'immagine, e una media e' un
riempimento. La lente invece e' grande (19/29 dp sulla capsula). Chi deve davvero nascondere qualcosa
— la barra superiore, con testo nitido che le scorre sotto — chiede un multiplo con `blurScale`.
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
  `FluidGlassMenuButton` dove serve la trasformazione.
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
