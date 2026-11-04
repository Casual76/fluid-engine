# Aspetto, aggiornamento in-app, parole, licenze

Quattro pezzi che **ogni app finisce per riscrivere nella propria pagina «Impostazioni» e «Informazioni»** e che l'engine ha
preso a carico: la sezione **Aspetto** (tema, nero assoluto, accento, aptica, vetro) gia' mappata su `EngineSettings`,
l'**aggiornamento in-app** (avviso e righe delle impostazioni, sopra un modello neutro e un controllore), le **parole** che l'engine
dice da se' (`FluidLabels`, e gli insiemi di frasi di ogni sezione, ognuno con la sua versione inglese) e i **crediti** di terze parti
(`fluidLicensesSection`), che dalla 3.0 sono un obbligo di licenza e non una cortesia.

**Modello mentale.** Le sezioni sono **estensioni di `LazyListScope`** (`fluidAppearanceSection`, `fluidUpdateSection`,
`fluidLicensesSection`) e non schermate: l'app ha gia' la sua pagina, la sua testata e il suo ordine, e una seconda schermata sarebbe
un posto in piu' in cui andare e dimenticarsi. Sotto ogni sezione c'e' una **parte pura** (`FluidAppearance`, `FluidUpdate`,
`FluidUpdateUiState`) con le regole che le app sbagliavano in silenzio, provata sul computer senza disegnare niente.
Il tema che queste righe modificano e' descritto in [tema.md](tema.md); il modulo che fa davvero il lavoro dell'aggiornamento
(`EngineAppUpdater`, manifest, installatore) e' un altro: [../moduli/update.md](../moduli/update.md).

## Regole che valgono per tutte e quattro

- **Tutte le parole sono in italiano di serie.** Un'app in una lingua non scrive niente; un'app tradotta passa **tutte** le parole di
  quell'insieme. Una sola lasciata di serie e' una frase italiana in mezzo a una pagina inglese, e nessuno se ne accorge finche' non
  gira il telefono (e' successo nei crediti: due frasi italiane in una pagina inglese). Le sezioni sono funzioni di `LazyListScope`: **non leggono le risorse
  Android da se'**, le legge l'app prima e le passa.
- **Gli insiemi di parole e la loro versione inglese:**

  | insieme | dove si passa | inglese pronto? |
  |---|---|---|
  | `FluidLabels` (con `FluidLabels.sync`: `FluidSyncStrings`) | `CompositionLocalProvider(LocalFluidLabels provides …)` alla radice | si': `FluidLabels.english()`, `FluidSyncStrings.english()` |
  | `FluidUpdateStrings` | `strings =` di `fluidUpdateSection`, `FluidUpdatePrompt*`, `rememberFluidUpdateController` | si': `FluidUpdateStrings.english()` |
  | `FluidAppearanceStrings` | `strings =` di `fluidAppearanceSection` | **no**: va scritta tutta (13 campi) |
  | titolo e nota dei crediti | `title =`, `footnote =` di `fluidLicensesSection` | **no**: due stringhe |

  L'inglese e' un **punto di partenza** (`.copy(...)`) per un'app in un'altra lingua, non il prodotto finito.
- **Una modifica che non cambia niente non e' una modifica.** `fluidAppearanceSection` e il selettore dell'accento non chiamano
  `onSettingsChange` se la scelta non cambia niente (e il caso piu' comune: si tocca la riga gia' scelta); il canale dell'aggiornamento
  idem. Lo store non riscrive e il flusso non si muove per niente.
- **Un controllo compare solo se passi il valore *e* la sua callback.** Vetro, canale, scarico automatico, controllo all'avvio: la
  sezione non inventa uno stato che l'app non ha.
- **`glass`**: i gruppi della sezione in vetro, come `FluidListGroup(glass)`. E' una richiesta (vedi la scheda del tema): serve un canvas
  ambientale di `FluidScreen`; senza, restano opachi.
- **`keyPrefix`**: le chiavi degli `item` (`"fluid-appearance-…"`, `"fluid-update-…"`) devono essere uniche nella `LazyColumn`; se la pagina ha due
  sezioni dello stesso tipo, il secondo prefisso e' tuo. I crediti hanno chiavi fisse (`fluid-licences-header`, `-body`, `-footnote`): una
  sola sezione dei crediti per lista.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la pagina «Aspetto» completa | `fluidAppearanceSection(settings, onSettingsChange, brand, presets, …)` |
| solo il selettore dei colori | `FluidAccentPicker(settings, onSettingsChange)` (con `EngineSettings`) o `FluidAccentPicker(selected, onSelect)` (con un modello mio, `FluidAccentChoice`) |
| tradurre i miei `AppSettings` in «quale pallino e' acceso» senza riscrivere la regola | `FluidAppearance.accentChoice` / `withAccent`, `themeChoice` / `withThemeMode`, `isPureBlack` / `withPureBlack` |
| la riga «Vetro» nell'aspetto | passare **insieme** `glassEnabled` e `onGlassEnabledChange` (non e' un campo di `EngineSettings`) |
| un'app con un solo colore, senza selettore | `fluidAppearanceSection(accent = false)` |
| un'app che ha gia' l'aptica altrove | `fluidAppearanceSection(haptics = false)` |
| una pagina che si chiama gia' «Aspetto» | `fluidAppearanceSection(title = null)` (niente testata duplicata) |
| l'aggiornamento in-app, con il minimo di cablaggio | `rememberFluidUpdateController(updater, appScope, versione)` + `fluidUpdateSection(update.state, update::check, update::install, …)` |
| un avviso all'avvio, in un foglio di vetro | `FluidUpdatePromptSheet(state, onInstall, onLater, onSkip)` |
| il contenuto dell'avviso dentro un foglio/pop-up mio | `FluidUpdatePrompt(…)` dentro un `FluidGlassModalPortal` o `FluidSheetScaffold` |
| un aggiornamento con un updater che **non** e' `EngineAppUpdater` | implementare `AppUpdater` (foundation) e passarlo al controllore; il disegno e' lo stesso |
| i conti dell'aggiornamento (peso, percentuale, note senza Markdown, «controllato N min fa») | `FluidUpdate.sizeLabel` / `percent` / `plainNotes` / `notesSummary` / `lastCheckLabel` / `progressText` |
| un'app in inglese | `FluidLabels.english()` alla radice, `FluidUpdateStrings.english()`, e `FluidAppearanceStrings(...)` scritto a mano |
| i crediti nella pagina «Informazioni» | `fluidLicensesSection()` (obbligatorio dalla 3.0) |
| i crediti in una pagina fatta di `Column` | `FluidLicenseGroup()` o `FluidLicenseDetails()` |
| controllare le versioni dell'engine fuori dall'interfaccia | non qui: `engine-update` (`../moduli/update.md`) |

## fluidAppearanceSection

**Cos'e'.** La sezione «Aspetto» di una pagina di impostazioni, in tre gruppi: **tema** (Sistema / Chiaro / Scuro + «Nero assoluto»),
**accento** (il nome del colore scelto e i pallini) e **interruttori** (vetro, se l'app lo da; aptica). Piu' app avevano riscritto
la stessa pagina (chi con righe a spunta, chi con un controllo a segmenti, chi con un tasto per preset) e la differenza fra una
versione e l'altra era quasi tutta nei bug.

**Firma essenziale.**

```kotlin
fun LazyListScope.fluidAppearanceSection(
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
  brand: AccentPreset = FluidDefaultBrand,
  presets: List<AccentPreset> = fluidAccentPresets,
  glassEnabled: Boolean? = null,
  onGlassEnabledChange: ((Boolean) -> Unit)? = null,
  strings: FluidAppearanceStrings = FluidAppearanceStrings(),
  glass: Boolean = false,
  dynamicAccent: Boolean = true,
  footnote: String? = null,
  keyPrefix: String = "fluid-appearance",
  title: String? = strings.title,
  pureBlack: Boolean = true,
  accent: Boolean = true,
  haptics: Boolean = true,
)
```

Tutti i parametri: [`../api/impostazioni.md`](../api/impostazioni.md).

**Uso** (dalla scheda Impostazioni della galleria, con le impostazioni sopra il tema):

```kotlin
FluidScreen(title = "Impostazioni", ambient = remember { FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards) }) {
  fluidAppearanceSection(
    settings = settings,
    onSettingsChange = onSettingsChange,   // scrive nello store; il tema, che lo osserva, fa il resto
    glass = true,
  )
  fluidLicensesSection(glass = true)
}
```

Con la riga del vetro (una scelta dell'app, perche' dice se *quella schermata* ha un canvas ambientale):

```kotlin
fluidAppearanceSection(
  settings = settings,
  onSettingsChange = { settings = it },
  glassEnabled = glassOn,
  onGlassEnabledChange = { glassOn = it },
  glass = glassOn,
)
```

**Comportamento.**

- **`onSettingsChange` riceve l'`EngineSettings` intero e nuovo**, mai un campo: l'app lo scrive (`EngineSettingsStore.update { next }`,
  oppure traduce nel suo modello) e non viene chiamata se `next == settings`.
- **Gruppo tema.** Tre righe di scelta (`FluidChoiceRow`, spunta a destra) nell'ordine di `FluidAppearance.ThemeChoices` (Sistema, Chiaro,
  Scuro); solo «Sistema» ha un sottotitolo («Segue le impostazioni del telefono»). Sotto, «Nero assoluto» e' un `FluidToggleRow`: **sempre
  presente ma spento (`enabled = false`) col tema Chiaro**, dove il nero non ha dove andare; l'interruttore c'e', non risponde.
- **Gruppo accento.** Una riga col nome della scelta (marchio, «Colori di sistema» o l'etichetta del preset) e sotto il
  `FluidAccentPicker`, con margini calcolati perche' il primo disco sia allineato al testo della riga (inset 7 dp: 16 dp di margine meno i 9 dp d'aria
  di un disco dentro il suo bersaglio da 48 dp).
- **Gruppo interruttori.** Le righe vengono da `FluidAppearance.switchRows(glassRow, haptics)`: il vetro (solo se l'app ha passato valore e
  callback) e l'aptica; **se non ne resta nessuna il gruppo non si disegna** (un contenitore senza righe e' un rettangolo vuoto con un margine).
  Accendere l'aptica fa partire subito un colpo `ToggleOn`: l'interruttore ha gia' «vibrato» nel momento del tocco ma con l'aptica ancora
  spenta, cioe' per niente, e senza il colpo di prova chi la accende non sentirebbe mai com'e'. Il primo valore letto non conta (altrimenti
  ogni apertura della pagina con l'aptica accesa vibrerebbe).
- **Ordine degli `item`**: `$keyPrefix-header` (se `title != null`), `-theme`, `-accent` (se `accent`), `-switches` (se ci sono righe),
  `-footnote` (se `footnote != null`).
- **Accessibilita'.** Il gruppo del tema e' un `selectableGroup`; ogni disco e' un `FluidColorDot` (bersaglio 48 dp, ruolo `RadioButton`, nome
  per chi legge lo schermo: un colore da solo non e' un'etichetta); la testata e' un `heading()` per TalkBack.
- **Il pallino dei «Colori di sistema»** mostra **il colore vero** del telefono (non un grigio con una parola: scegliere e' guardare) e non si
  disegna sotto Android 12 o con `dynamicAccent = false`.

**Trappole.**

- **`presets` va passato anche qui se l'hai passato a `FluidTheme`**: la sezione e il tema devono parlare della stessa lista, altrimenti il
  selettore accende pallini che il tema non usa. E non passare liste vuote: il tema ricade su `fluidAccentPresets`, ma il selettore non
  disegna nessun pallino preset.
- **La sezione non ha parole inglesi pronte** (`FluidAppearanceStrings` non ha `english()`): un'app tradotta le scrive tutte e 13, altrimenti
  «Aspetto», «Nero assoluto»… compaiono in italiano nella pagina inglese. `title = null` e' l'unico modo di togliere la testata.
- **Il vetro non e' un campo di `EngineSettings`**, di proposito: alcune app lo hanno a livelli, altre automatico. Se ti aspetti di trovarlo
  in `settings` non c'e'.
- **L'aptica spenta non e' «niente»**: `hapticsEnabled = false` spegne il motore di `LocalFluidHaptics` per tutto il tema (e' un interruttore
  condiviso fra le app).

**Non usarla quando.** Un'app con un'unica preferenza «tema» in una riga: `FluidAppearance.withThemeMode` + una `FluidChoiceRow` tua (la
traduzione resta quella dell'engine, non riscriverla).

**Versione.** 3.0. `title` 3.0.1 (null = niente testata); `pureBlack`, `accent`, `haptics` 3.0.4 (e il gruppo che sparisce se non ha righe).

## FluidAppearance, FluidAccentChoice, FluidAppearanceStrings

**Cos'e'.** La traduzione pura fra «quello che una pagina Aspetto mostra» e `EngineSettings`: le regole che le app sbagliavano. Un'app con un
suo modello di impostazioni ma che usa il selettore le chiama dal proprio store (`store.update { FluidAppearance.withAccent(it, choice) }`).

**`FluidAccentChoice`** (sealed): `Brand`, `Dynamic`, `Preset(name)`. `EngineSettings` descrive l'accento con una modalita' piu' un nome che
ha senso in una sola delle tre piu' un interruttore che ha senso in un'altra: chi disegna un selettore ragiona per «quale pallino e' acceso».

**Regole (tutte provate dai test).**

| funzione | cosa garantisce |
|---|---|
| `ThemeChoices` | **tre** voci (Sistema, Chiaro, Scuro), mai quattro: `AMOLED` non e' un tema ma «scuro con il nero vero», un modo, e quattro righe direbbero che il nero e' un'alternativa a «Scuro» |
| `themeChoice(settings)` | `AMOLED` si legge come `DARK` |
| `isPureBlack(settings)` | `amoledEnabled` **oppure** il tema `AMOLED` (producono la stessa tavolozza) |
| `withThemeMode(settings, mode)` | sceglie il tema **senza perdere il nero** di chi lo aveva: chi era in `AMOLED` e sceglie «Chiaro» non vuole il nero scuro quando tornera' allo scuro, quindi `AMOLED` si piega in `amoledEnabled = true`; chiedere `AMOLED` vuol dire scuro e nero |
| `withPureBlack(settings, on)` | acceso/spento; spegnerlo da `AMOLED` porta a `DARK` (lasciare `AMOLED` con `amoledEnabled = false` darebbe un interruttore spento sopra una tavolozza ancora nera) |
| `presetName(name, presets)` | il preset che il tema usera' davvero: stesso nome a meno delle maiuscole, **altrimenti il primo**; lista vuota → quella dell'engine. Un selettore che accende «niente» perche' il nome salvato non c'e' piu' dice una cosa falsa |
| `accentChoice(settings, presets, dynamicSupported)` | `BRAND` → `Brand`; **`DYNAMIC` senza supporto, o con `dynamicColorEnabled = false`, → `Brand`** (e' quello che il tema disegna: costava un bug); `CUSTOM_PRESET` → `Preset(presetName)` |
| `withAccent(settings, choice)` | `Dynamic` **riaccende** `dynamicColorEnabled` (chi tocca «Colori di sistema» vuole quelli; un interruttore spento altrove non deve farli ripiegare in silenzio sul marchio); `Brand` e `Preset` cambiano solo l'accento |
| `accentLabel(choice, brandLabel, dynamicLabel, presets)` | il nome da mostrare |
| `hapticsPreview(previous, now)` | vero solo se si passa da spento (`false`) ad acceso: il primo valore letto (`null`) non vibra |
| `switchRows(glassRow, haptics)` | le righe del gruppo interruttori nell'ordine in cui si disegnano |

**`FluidAppearanceStrings`**: i 13 campi (`title`, `themeSystem`, `themeSystemDetail`, `themeLight`, `themeDark`, `pureBlack`, `pureBlackDetail`,
`accent`, `accentDynamic`, `glass`, `glassDetail`, `haptics`, `hapticsDetail`) e `themeLabel(mode)`, che dice «Scuro» anche per `AMOLED`
(non c'e' una quarta voce).

## FluidAccentPicker

**Cos'e'.** La fila di pallini: marchio dell'app, colori di sistema, preset. Due forme, perche' le app si dividono in due: `FluidAccentChoice`
per chi ha un suo modello di impostazioni, `EngineSettings` per chi usa lo store dell'engine.

```kotlin
FluidAccentPicker(
  settings = settings,
  onSettingsChange = { next -> scope.launch { store.update { next } } },
  brand = AppBrand,
)
```

**Comportamento.**

- Ogni pallino e' un `FluidColorDot` (disco da ~30 dp in un bersaglio da 48, ruolo `RadioButton`, nome = etichetta); i dischi vanno a capo
  (`FlowRow`) se non ci stanno tutti, con 4 dp fra i bersagli. Il gruppo e' un `selectableGroup`.
- Il colore di ogni disco sceglie la meta' chiara o scura **dal lato del tema** (`GlassDefaults.isDarkSurface()`, non dalla luminanza del
  fondo: con un fondo trasparente ogni pallino sarebbe quello scuro).
- `dynamicColor`: `null` nasconde il pallino. Di serie lo chiede al telefono (`fluidDynamicAccentColor`): `null` sotto Android 12.
- La forma a `EngineSettings` non chiama la callback se la scelta non cambia niente.

## fluidUpdateSection

**Cos'e'.** Le righe degli aggiornamenti per una pagina di impostazioni: il canale, il controllo con l'installazione, «controllato N minuti fa»,
lo scarico automatico. Otto app si erano disegnata la propria (chi con un `Dialog`, chi con una scheda, chi con tre tasti in fila) con
la stessa differenza di una versione all'altra, quasi tutta nei bug: un `Idle` che compariva a meta' (e che l'engine non ha mai avuto), un errore
che finiva nel ramo «in corso» senza un modo di riprovare, un download senza peso che mostrava «0%» per tutto il tempo.

**Firma essenziale.**

```kotlin
fun LazyListScope.fluidUpdateSection(
  state: FluidUpdateUiState,
  onCheck: () -> Unit,
  onInstall: () -> Unit,
  currentVersion: String? = null,
  lastCheckedAtMillis: Long? = null,
  channel: UpdateChannel? = null,
  onChannelChange: ((UpdateChannel) -> Unit)? = null,
  channelStyle: FluidUpdateChannelStyle = FluidUpdateChannelStyle.Choice,
  autoDownload: Boolean? = null,
  onAutoDownloadChange: ((Boolean) -> Unit)? = null,
  autoCheck: Boolean? = null,
  onAutoCheckChange: ((Boolean) -> Unit)? = null,
  onSkip: (() -> Unit)? = null,
  supported: Boolean = true,
  strings: FluidUpdateStrings = FluidUpdateStrings(),
  glass: Boolean = false,
  footnote: String? = null,
  keyPrefix: String = "fluid-update",
)
```

`nowMillis` (l'orologio, per i test) e gli altri parametri: [`../api/impostazioni.md`](../api/impostazioni.md).

**Uso** (cablaggio vero, con il controllore):

```kotlin
val update = rememberFluidUpdateController(updater, appScope, BuildConfig.VERSION_NAME.substringBefore("-"))
LazyColumn {
  fluidUpdateSection(
    state = update.state,
    onCheck = update::check,
    onInstall = update::install,
    currentVersion = BuildConfig.VERSION_NAME,
    lastCheckedAtMillis = update.lastCheckedAtMillis,
    channel = channel,
    onChannelChange = { channel = it },
  )
}
```

**Comportamento.**

- **Il controllo e l'installazione sono la stessa riga** per chi legge («c'e' una versione nuova, tocca e arriva»): l'azione cambia con lo
  `state`. Senza novita' dice **«Controlla ora»** (sottotitolo: «Controllato 5 minuti fa», o «Sto controllando…» mentre lavora, con un
  `FluidSpinner` a destra); con una versione dice **«Installa la 1.2.0»** (sottotitolo: il peso); dopo un errore **«Riprova»**; col permesso da
  dare **«Apri impostazioni»**. Mentre controlla o installa la riga **resta ma non risponde** (`onClick = null`) e dice cosa sta facendo: un
  secondo tocco non rilancia niente.
- **La riga di stato** sopra l'azione dipende dallo stato: `Idle`/`Checking` nessuna; `UpToDate` «App aggiornata»; `CheckFailed` «Controllo non
  riuscito» con il testo tecnico se c'e' (altrimenti una frase di ripiego); `Available` «Versione X disponibile» con le **note riassunte in tre
  righe al piu'** (le note intere stanno nell'avviso: un primo paragrafo di dieci righe spingeva l'azione fuori dallo schermo) e il segno **«Nuova»**
  (`FluidStatusBadge`, tono `Primary`); `Downloading`/`Working` «Aggiornamento in corso…» + la frase di avanzamento; `Failed` «Aggiornamento non
  riuscito» + il messaggio; `NeedsPermission` la versione + la frase del permesso. L'installazione in corso mostra la sua **barra dentro il
  gruppo**, non in una scheda a parte.
- **`supported = false`** (una build che non si aggiorna da sola: debug, store): **una riga che lo dice e niente altro** (la sezione fa `return`
  dopo, senza canale, scarico o nota); e' meglio di una sezione fatta di tasti che non fanno niente.
- **Canale.** Compare solo con `channel` **e** `onChannelChange`. `Choice` = due righe di scelta «Stabile» / «Beta» con la spunta a destra
  (si vede cosa si riceve e cosa no); `BetaToggle` = un solo interruttore «Versioni di prova», per chi ha il canale stabile e le beta come un di piu'. La
  callback riceve il canale **nuovo** e **non e' chiamata** se tocchi quello gia' scelto.
- **Controllo all'avvio e scarico automatico** (stesso gruppo, il primo sopra): `autoCheck` (3.0.1) e `autoDownload` sono **due cose**: cercare
  una versione nuova a ogni apertura non vuol dire scaricarla senza chiedere. La sezione **mostra** l'interruttore; chi lo onora e' l'app (o
  `rememberFluidUpdateController(checkOnStart = …)` per il controllo).
- **`onSkip`** («Salta questa versione»): una riga semplice sotto l'azione quando c'e' una novita'. La tiene l'app (`ignoredVersion`): la versione
  saltata non torna finche' non ne esce un'altra.
- **Ordine degli `item`**: `-header`, `-status` (versione installata, stato, barra, azione, salta), `-channel`, `-auto`, `-footnote`.

**Trappole.**

- **`currentVersion` del controllore va passato senza il suffisso delle build di debug** (`…substringBefore("-")`): il manifest conosce solo le
  release. Quello della sezione e' solo una riga di testo.
- **La sezione non fa niente da sola**: non controlla, non scarica, non ricorda il canale. Senza controllore (o un equivalente tuo), il tocco chiama
  due lambda che sono tue.
- **Con uno scope di schermata un download si ferma quando si cambia pagina.** Per un'installazione che sopravvive alla navigazione passa uno
  scope dell'app e chiama il controllore alla radice (vedi sotto).
- **`FluidUpdateStrings` va passato a tutti i pezzi** (sezione, avviso, controllore): quello del controllore serve per `unknownError`.

**Non usarla quando.** Un'app distribuita da uno store che si aggiorna da sola: `supported = false` o niente sezione.

**Versione.** 3.0 (fase 12c). `autoCheck` e il riassunto in tre righe: 3.0.1. Le parole inglesi: 3.0.4.

## FluidUpdatePrompt e FluidUpdatePromptSheet

**Cos'e'.** L'avviso di aggiornamento. `FluidUpdatePrompt` e' il **contenuto** (versione, note, peso, avanzamento, tasti) per un foglio, un pop-up o
un avviso che possiedi tu; `FluidUpdatePromptSheet` e' lo stesso dentro un foglio di vetro che sale dal basso: una chiamata sola per l'avviso
all'avvio.

```kotlin
FluidUpdatePromptSheet(
  state = update.state,
  onInstall = update::install,
  onLater = { /* congeda: tasto, indietro, tocco fuori, trascinare giu' */ },
  onSkip = { /* salta questa versione */ },
)
```

**Comportamento.**

- **I tasti seguono lo stato.** `Available`: «Aggiorna», «Piu' tardi» e, se passi `onSkip`, «Salta questa versione» (tre risposte e nessuna e' una
  trappola: si fa, si rimanda al prossimo avvio, o si salta per sempre). Scarica/installa: il tasto principale non c'e' piu', una riga disabilitata con
  spinner dice «Aggiornamento in corso…» e resta «Piu' tardi» (**l'avviso non diventa una gabbia**: fermare un download e' una scelta legittima, e cosa
  vuol dire — annullare o lasciar finire — e' dell'app, che riceve `onLater`). `Failed`: «Riprova» (e' ancora `onInstall`: per l'app e' lo stesso
  gesto della prima volta) e «Chiudi». `NeedsPermission`: lo dice **prima** (Android lo chiede una volta e una finestra di sistema senza spiegazione
  sembra un errore); «Apri impostazioni» e' ancora `onInstall`. `Installed`: la conferma e «Chiudi» (`onLater`).
- **La barra dice la verita'**: determinata solo se si sa quanto pesa il file (`Downloading.progress != null`), senza fine per verifica e installazione,
  **assente** mentre Android aspetta una risposta (`AwaitingUser`: niente si muove, e una barra che gira mentirebbe).
- **Aptica**: `Success` quando l'installazione finisce, `Error` quando si ferma, **una volta per tipo di stato** (non per percentuale: un colpo per
  ognuna sarebbe la grana di un download). Un aggiornamento termina mentre si guarda altrove, e l'esito si sente.
- Le note si leggono intere nel corpo del foglio, che scorre da solo: i tasti restano fermi sotto, anche con quaranta righe di novita'.
- `Prompt` senza versione nello stato (`Idle`, `Checking`, `UpToDate`, `CheckFailed`) **non disegna niente**.
- `PromptSheet`: **lo `state` e' anche l'interruttore**: aperto finche' lo stato ha una versione (`release != null`), si chiude quando l'app lo porta a
  `null`, `Idle` o `UpToDate`. Resta intero mentre esce (il portale a `item` tiene l'ultimo stato), quindi non diventa un titolo vuoto per i 200 ms
  dell'uscita.

**Trappole.**

- **`PromptSheet` senza un `FluidGlassModalHost` in scope non si apre e non va in crash** (la regola del portale): niente avviso, e nessun errore.
- **`insetBottom`**: vero in un foglio (lascia lo spazio della barra di navigazione), **falso** in un pop-up a mezz'aria (vedi `FluidSheetScaffold`).
- `onLater` e' sia il tasto sia il congedo: per l'app sono la stessa risposta; non distinguerli.

## FluidUpdateController e rememberFluidUpdateController

**Cos'e'.** La colla fra un `AppUpdater` (interfaccia di `engine-foundation`, che `EngineAppUpdater` implementa e che un'app puo' implementare da se') e
la vista: tiene lo stato, fa il controllo, fa l'installazione con gli errori presi. Tre app (Pampa sounds, KeyVoice, Codex) avevano scritto la stessa
classe di settanta righe con le stesse scelte, e quelle scelte sono ora **regole garantite**:

- un controllo che non trova niente e' `UpToDate`, **non un errore**; uno che non riesce e' `CheckFailed`, **non** «nessun aggiornamento»;
- un secondo tocco mentre lavora **non rilancia niente** (`check()` e `install()` escono se `state.isBusy`);
- un'eccezione dell'`updater` diventa `CheckFailed`/`Failed` (**la cancellazione no**: si propaga);
- dopo un errore, e con il permesso d'installare da dare (`NeedsPermission`: l'installatore apre da se' le impostazioni), **lo stesso tocco riprova**;
- `install()` senza una versione in mano non fa niente; `reset()` (cambio di canale) torna a `Idle`, ma non mentre lavora.

```kotlin
class FluidUpdateController(updater: AppUpdater, scope: CoroutineScope, currentVersion: String, strings: FluidUpdateStrings = …, now: () -> Long = …) {
  var state: FluidUpdateUiState         // da passare a fluidUpdateSection(state = …)
  var lastCheckedAtMillis: Long?        // quando e' finito l'ultimo controllo (anche se e' fallito)
  var available: AvailableAppUpdate?    // la versione che install() installa
  var channel: UpdateChannel            // dove cerca check()
  var ignoredVersion: String            // la versione saltata: check() non la offre
  fun check(); fun install(); fun reset()
}
```

**`rememberFluidUpdateController(updater, scope, currentVersion, channel, ignoredVersion, checkOnStart, strings)`** ricorda il controllore nella
composizione, con chiave `(updater, scope, currentVersion)`.

**Comportamento che il nome non dice.**

- **Dove sta e perche'.** In `engine-ui` e non in `engine-update`: li' avrebbe voluto Compose e `engine-ui`, due dipendenze che quel modulo non ha e che
  un'app senza interfaccia non deve tirarsi dentro. Parla l'`AppUpdater` di foundation e produce il `FluidUpdateUiState` di qui.
- **Lo scope decide quanto vive un download.** Di serie `rememberCoroutineScope()`: l'installazione si ferma quando il punto da cui e' chiamato esce di
  scena. Con uno scope dell'app (un `CoroutineScope` del processo o l'`applicationScope` che l'app ha gia') continua, e tornando nelle impostazioni la
  sezione dice ancora dov'e' arrivata: va chiamato **alla radice**.
- `channel` e `ignoredVersion` si riscrivono sul controllore a ogni ricomposizione (`SideEffect`) e valgono per il prossimo `check()`.
- `checkOnStart`: **un solo controllo per controllore**, non a ogni ricomposizione (`LaunchedEffect(controller)`).

**Trappole.**

- **Chiave del `remember`**: `strings`, `channel` e `ignoredVersion` non la cambiano. Le `strings` sono lette alla creazione (una lingua cambiata
  a sessione aperta non le aggiorna dentro il controllore: tocca solo `unknownError`).
- **`ignoredVersion` non e' persistito**: lo ricorda l'app (e `onSkip` deve scriverlo dove sta).
- **`currentVersion` senza suffisso di debug** (vedi sopra).

**Versione.** 3.0.1: il controllore e `rememberFluidUpdateController`; il permesso d'installare come `NeedsPermission` (non piu' «non riuscito»).

## Il modello neutro: FluidUpdateUiState, FluidUpdateRelease, FluidUpdate

**Stati, non percentuali.** Quasi tutto un'installazione non e' una percentuale: le pause lunghe sono la verifica e la finestra di conferma del sistema,
e una barra ferma al 100% e' il modo piu' comune in cui un aggiornamento fuori da uno store sembra rotto mentre funziona.

| stato | significato |
|---|---|
| `Idle` | non si e' ancora controllato (non esiste «a meta'») |
| `Checking` | si sta chiedendo al manifest |
| `UpToDate` | controllato, niente di piu' nuovo (**non un errore**) |
| `CheckFailed(message?)` | controllo fallito (rete, manifest): si ritenta con un tocco |
| `Available(release)` | c'e' una versione e non si e' toccato niente |
| `Downloading(release, progress?, downloadedBytes, totalBytes)` | `progress` 0..1, **null se il peso non si conosce** (barra senza fine) |
| `Working(release, stage, message)` | `stage`: `Verifying`, `Installing`, `AwaitingUser` |
| `Installed(release)` | installata; l'app, di norma, sta per essere sostituita |
| `Failed(release, message)` | l'installazione si e' fermata, si puo' riprovare (ha una versione in mano che si e' fermata a meta') |
| `NeedsPermission(release)` | serve il permesso d'installare da questa app (una volta sola) |

Estensioni: `release` (la versione di cui parla lo stato, o null), `isBusy` (`Checking`, `Downloading`, `Working` — **anche l'attesa della conferma**: finche' il
sistema non ha avuto risposta, ripartire vorrebbe dire due finestre di conferma), `canInstall` (`Available`, `Failed`, `NeedsPermission`).

**Dai tipi di foundation.** `AvailableAppUpdate.toFluidUpdateRelease()` e `AppUpdateInstallState.toFluidUpdateState(release)` sono **l'unico posto** in cui i sei
passi dell'installatore diventano i cinque della vista (cosi' le app non lo riscrivono ognuna col suo `else`): `Downloading` con `totalBytes <= 0` non ha
percentuale; `Verifying`, `Installing`, `AwaitingUserAction` sono i tre `FluidUpdateStage` col messaggio dell'installatore; `Error` con
`AppUpdateErrorKind.InstallPermission` e' `NeedsPermission`, qualunque altro `Failed`. Non legano `engine-ui` a `engine-update`: parlano solo tipi di foundation.

**`FluidUpdate` (conti puri, provati sul computer).**

| funzione | cosa fa |
|---|---|
| `sizeLabel(bytes, locale)` | «812 KB», «12,4 MB» (base 1024; B senza decimali, KB 0, MB e GB 1); **null se `<= 0`**: la riga che lo dice sparisce, non diventa «0 B» |
| `percent(progress)` | intero **per difetto**, fra 0 e 100: a 0,996 e' 99, non 100 (arrivare a cento prima di aver finito e' lo stesso difetto della barra ferma in fondo) |
| `plainNotes(notes)` | note senza Markdown: via grassetto e intestazioni, punti elenco «•», righe vuote ridotte (nel changelog dello store il grassetto serve; in una riga sono solo asterischi) |
| `notesSummary(notes)` | il primo paragrafo ripulito; vuoto senza note |
| `lastCheckLabel(lastCheckedAtMillis, nowMillis, strings)` | sotto il minuto «adesso», poi minuti, ore, giorni; **una data nel futuro** (orologio spostato) vale «adesso», non «-3 minuti»; null o `<= 0` = «mai controllato» |
| `progressText(state, strings, locale)` | **una frase sola**, dalla stessa tabella, per l'avviso e per la riga delle impostazioni, cosi' i due posti non dicono cose diverse della stessa installazione; null per `Available`, `Idle`, `Checking`, `UpToDate`, `CheckFailed` |

**`FluidUpdateStrings`** (47 campi): avviso, impostazioni, e le frasi con un numero dentro sono **funzioni** (`promptTitle`, `availableMessage`, `downloading`,
`available`, `installVersion`, `lastCheckedMinutes/Hours/Days`) perche' «1 minuto» e «2 minuti» non sono la stessa frase con un numero diverso. `autoCheck`,
`autoCheckDetail` e `unknownError` dalla 3.0.1.

## FluidLabels, LocalFluidLabels, FluidSyncStrings

**Cos'e'.** Le parole che l'engine dice **da se'**, quelle che nessun parametro passava (3.0.1): i nomi per TalkBack di tasti e pannelli che i componenti
disegnano senza chiedere niente all'app. L'occhio di un campo segreto, la X di una pagina intera e il tasto indietro della barra erano scritti in italiano
dentro i componenti: un'app inglese li leggeva in italiano con TalkBack.

| campo di `FluidLabels` | letto da | di serie |
|---|---|---|
| `close` | la X della pagina intera (`FluidGlassModalPresentation.FullScreen`) | «Chiudi» |
| `showSecret` / `hideSecret` | l'occhio di `FluidTextField(secret = true)` | «Mostra il testo» / «Nascondi il testo» |
| `actions` | il titolo per TalkBack di un menu di azioni senza titolo (contestuale, del tasto, della barra) | «Azioni» |
| `back` | il tasto indietro (`FluidScreen`, `FluidPageChrome`, la barra dell'host, `FluidTopHeader`) | «Indietro» |
| `scrollToTop` | il tocco sul titolo che riporta la lista in cima | «Torna all'inizio» |
| `notification`, `notificationExpand`, `notificationDismiss`, `notificationClose` | la notifica in-app e le azioni TalkBack «Espandi»/«Chiudi» | |
| `sectionIndex`, `previousSection`, `nextSection`, `sectionPosition(label, position, total)` | l'indice di sezione («Marzo, 3 di 12») | |
| `hide` | la X di `FluidInlineMessage(onDismiss)` | «Nascondi» |
| `clear` | il tasto che svuota un campo di testo su una riga | «Cancella» |
| `message` | il titolo di un `FluidInlineMessage` che non ne riceve uno (3.0.4) | «Messaggio» |
| `sync` (`FluidSyncStrings`) | `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`, `lastSyncLabel` (3.0.4) | italiano |

**Uso.** Una volta alla radice, **sopra gli host** (che le leggono dove sono composti: menu e pop-up compresi), e **tutte**:

```kotlin
CompositionLocalProvider(LocalFluidLabels provides FluidLabels.english()) { App() }
// un'app in un'altra lingua parte dall'inglese:
CompositionLocalProvider(LocalFluidLabels provides FluidLabels.english().copy(close = stringResource(R.string.close))) { App() }
```

**Comportamento.**

- **Dove un componente ha gia' un parametro per la stessa parola vince il parametro** (`FluidPromptAlert(cancelLabel)`, `FluidSlider(contentDescription)`,
  `FluidSearchLabels`, `FluidUpdateStrings`): queste sono solo le parole che prima stavano dentro il componente.
- **Un componente non legge le risorse da se'**: non sa in che lingua e' l'app, e un engine che indovinasse dalla lingua del telefono metterebbe frasi inglesi
  in un'app italiana su un telefono inglese. Le passa l'app.
- `LocalFluidLabels` e' un `staticCompositionLocalOf` con default `FluidLabels()` (italiano): cambiare il valore ricompone tutto il sotto-albero.
- **`FluidSyncStrings`** (3.0.4): le frasi dell'indicatore di sincronizzazione, dell'avviso e del suo dettaglio; `lastSyncLabel(strings = …)` le prende da li'. Le frasi con un
  numero sono funzioni (`minutesAgo`, `hoursAgo`, `notUpdated`, `updatedOn`).

**Trappole.**

- **Le due frasi `noticeGuidance` e `yearNotOpen` sono del dominio dell'app da cui l'engine e' nato** (parlano di una fonte dati che «non ha ancora aperto l'anno»): un'app con
  un'altra fonte le riscrive, anche in italiano. Il resto dell'italiano di `FluidSyncStrings` e' neutro.
- **Una sola parola lasciata di serie in un'app tradotta e' una frase italiana in una pagina inglese.** Il test sulle versioni inglesi la intercetta per l'engine, non per le tue stringhe.
- **Un campo nuovo di `FluidLabels` richiede due cose**: il valore italiano e quello in `FluidLabels.english()`; se e' una funzione, anche un campionatore nel test, o la build cade.

**Il test che fa cadere la build** (`FluidEnglishStringsTest`, in `engine-ui`): legge per **riflessione** i campi di `FluidLabels`, `FluidSyncStrings` (annidato) e `FluidUpdateStrings`
nelle due lingue e richiede (a) **gli stessi campi**, (b) **nessun campo inglese uguale all'italiano** (unica eccezione dichiarata: `channelBeta`, «Beta» in tutte e due), (c) nessun
campo inglese vuoto, (d) nessuna eccezione dichiarata che non esiste piu'. Un campo aggiunto domani senza la sua traduzione non passa; **un campo funzione senza un campionatore nel
test cade lo stesso**, perche' altrimenti nessuno guarderebbe cosa dice. (Il CHANGELOG dice anche «o perde un segnaposto»: il test non controlla i segnaposto uno per uno, ma
confronta l'output dei campionatori.)

**Versione.** `FluidLabels`/`LocalFluidLabels` 3.0.1; `message`, `sync`, `FluidSyncStrings` e tutte le versioni `english()` 3.0.4. Restano in italiano di serie le frasi di
`FluidAppearanceStrings` e i default dei crediti.

## fluidLicensesSection, FluidLicenseGroup, FluidLicenseDetails

**Cos'e'.** I crediti di terze parti che l'engine porta **dentro l'APK**, come righe di una pagina «Informazioni». `FluidEngineLicenses` e' la lista (4 voci):

| opera | autore | licenza | cosa fa nell'app |
|---|---|---|---|
| AndroidLiquidGlass (backdrop) | Kyant | Apache-2.0 | il vetro: cattura dello sfondo, lente rifrattiva, bordo speculare |
| **LibrePods** | LibrePods contributors | **GPL-3.0** | i pulsanti (come crescono sotto il dito, lo seguono, vibrano) **e le pagine** (apertura, back sotto il dito) |
| Inter | Rasmus Andersson | SIL OFL 1.1 | il carattere di tutta l'interfaccia |
| Square | Lelonio | «GPL-3.0 (no code taken)» | da qui si e' imparato come si porta il vetro su Android (nessun codice preso: la qualifica sta dentro la licenza perche' va letta *insieme* al nome) |

**Firma essenziale.**

```kotlin
fun LazyListScope.fluidLicensesSection(
  title: String = "Licenze e crediti",
  footnote: String? = "Il Fluid Engine ${EngineBuild.VERSION} include queste opere di terze parti, fra cui codice GPL-3.0 (LibrePods): …",
  licences: List<FluidLicense> = FluidEngineLicenses,
  glass: Boolean = false,
)
```

`FluidLicenseGroup(licences, modifier, glass)` e' la stessa lista fuori da una lazy list (pagina fatta di `Column`); `FluidLicenseDetails(licences, modifier)` e' la forma lunga, con **cosa
fa** ogni opera (`FluidLicense.role`) e non solo chi l'ha scritta: per una pagina di crediti vera invece di un elenco di conformita'.

**Perche' e' obbligatoria dalla 3.0.** *Non e' una cortesia: e' una condizione di licenza.*

- **Dalla 3.0 `engine-ui` contiene codice di LibrePods, GPL-3.0** (`FluidPress.kt`, `FluidButtonGesture.kt` e le parti di aptica, bagliore e lente; le pagine di `ui/page/*`): i tasti si
  comportano *esattamente* come quelli di LibrePods perche' portano lo stesso codice. **Un'app che distribuisce l'engine 3.0 e' un'opera derivata e va distribuita sotto
  GPL-3.0**: i sorgenti a chi riceve l'APK, con la stessa licenza. E' una scelta di Alessio (31 ottobre 2026), per tutte le app Pampa che usano l'engine; un'app che non puo' essere
  GPL-3.0 resta sulla 2.13. La pagina «Informazioni» lo dice con questa sezione (e `LICENSES/LibrePods.md` e `LICENSES/` nel repo dell'engine hanno i testi).
- L'Apache-2.0 del vetro chiede che **l'avviso viaggi con la distribuzione**: un avviso che esiste solo in un Markdown nel repository non ha accompagnato la distribuzione, che e'
  la cosa che la licenza chiedeva. La OFL di Inter chiede la riga di copyright.
- La lista sta **nell'engine e non in ogni app**: e' cio' che impedisce che sia giusta in un'app e vecchia di tre versioni nelle altre quattro. Tienila allineata ai file di `LICENSES/`.

**Comportamento.**

- Ogni riga e' una `FluidListRow` (titolo = nome, sottotitolo = «autore · licenza», chevron) che apre l'indirizzo con `LocalUriHandler.openUri`: senza «Apri» scritto (la chevron lo
  dice gia').
- **Il default di `footnote` dice che l'app e' GPL-3.0** e include la versione dell'engine (`EngineBuild.VERSION`). **Un'app che passa una `footnote` sua deve aggiungere la frase nella
  sua lingua**: passare un testo che non la contiene toglie dalla pagina proprio l'avviso per cui la sezione e' obbligatoria. `footnote = null` la toglie del tutto.
- `title` e `footnote` sono **in italiano di serie**: un'app tradotta li passa (due frasi italiane nel mezzo di una pagina inglese: successe). Le righe di `FluidLicenseGroup`
  mostrano nomi propri e identificatori di licenza (non si traducono); **`FluidLicense.role` e' in italiano e compare solo in `FluidLicenseDetails`**: una pagina inglese che la usa
  passa una lista `licences` con i suoi `role`.
- `glass` (3.0.1): era l'unico gruppo opaco di una pagina «Informazioni» tutta di vetro.

**Trappole.**

- **Non togliere LibrePods dalla lista** passando una `licences` tua: la voce di LibrePods (e la nota) stanno in `FluidEngineLicenses`; una lista che la ometta e' una pagina che non
  dice quello che la licenza richiede.
- Un'app che mostra `fluidLicensesSection()` **cambia a vista** nella 3.0 (nota e ruolo di LibrePods nuovi): se passa una `footnote` sua deve aggiornarla.

**Versione.** `fluidLicensesSection`, `FluidLicenseGroup`, `FluidLicenseDetails`: 1.4.0. La voce LibrePods, la nota GPL e il ruolo «i pulsanti e le pagine»: **3.0** (BREAKING).
`glass`: 3.0.1.

## Cosa e' verificato dai test

- `FluidAppearanceTest` (`engine-ui`, 19 casi): le voci di tema sono tre e `AMOLED` non e' una di loro; il nero vale per il tema `AMOLED` come per l'interruttore; scegliere un tema
  non perde il nero; `DYNAMIC` senza supporto, o spento, e' il marchio; un preset con nome sparito accende il primo; `Dynamic` riaccende il colore dinamico; la scelta e la sua
  traduzione si rincorrono; la prova dell'aptica parte solo da spento ad acceso; il gruppo degli interruttori ha solo le righe che l'app vuole.
- `FluidAppearanceSectionTest` (`sample`, Robolectric, 9): la sezione parte dal valore delle impostazioni, scegliere «Scuro» cambia solo il tema, toccare la voce gia' scelta non
  scrive, il nero non risponde nel chiaro e risponde nello scuro, un `AMOLED` salvato si legge «Scuro» + nero, un preset scrive l'accento, la riga del vetro compare solo se l'app la porta,
  l'aptica accesa fa sentire un colpo e spenta no.
- `FluidUpdateModelTest`: i conti puri (peso sconosciuto = niente, percentuale per difetto e fra 0 e 100, note senza Markdown, «mai controllato», data nel futuro), la
  traduzione dai tipi di foundation (progress null senza peso, i tre passi d'attesa, permesso ≠ fallimento), `isBusy` anche per l'attesa del sistema, `canInstall`.
- `FluidUpdateControllerTest`: nessuna novita' = aggiornata (non errore); un controllo che non riesce, anche se l'updater lancia, e' `CheckFailed`; un secondo tocco non rilancia;
  canale e versione saltata arrivano all'updater; si installa fino in fondo; il permesso e' `NeedsPermission` e lo stesso tocco riprova; un'eccezione dell'installatore e' `Failed`
  col suo messaggio.
- `FluidEnglishStringsTest` (`engine-ui`): vedi sopra (le versioni inglesi non lasciano campi italiani; l'italiano resta il default; `lastSyncLabel` dice le frasi che gli si danno).
- `FluidLabelsTest` (`sample`): di serie le parole sono italiane; un'app tradotta le passa alla radice e valgono anche dentro gli host (la X della pagina intera, l'indietro, l'occhio).
- **Non coperti**: i crediti (nessun test su `fluidLicensesSection`, ne' sul fatto che la lista sia allineata a `LICENSES/`); il disegno della sezione di aggiornamento su un
  telefono; il vetro dei gruppi.

## Dove guardare

| percorso (da `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`) | cosa c'e' dentro |
|---|---|
| `FluidAppearance.kt` | `fluidAppearanceSection`, `FluidAppearance`, `FluidAccentChoice`, `FluidAccentPicker`, `FluidAppearanceStrings`, `FluidAppearanceSwitch` |
| `FluidUpdateModel.kt` | `FluidUpdateUiState`, `FluidUpdateRelease`, `FluidUpdateStage`, `FluidUpdateStrings` (+ `english()`), `FluidUpdate`, le traduzioni da foundation |
| `FluidUpdateController.kt` | `FluidUpdateController`, `rememberFluidUpdateController` |
| `FluidUpdateUi.kt` | `fluidUpdateSection`, `FluidUpdatePrompt`, `FluidUpdatePromptSheet`, `FluidUpdateChannelStyle` |
| `FluidLabels.kt` | `FluidLabels`, `FluidSyncStrings`, `LocalFluidLabels` (+ `english()`) |
| `FluidLicenses.kt` | `FluidLicense`, `FluidEngineLicenses`, `fluidLicensesSection`, `FluidLicenseGroup`, `FluidLicenseDetails` |
| `engine-foundation/…/AppUpdate.kt` | `AppUpdater`, `AvailableAppUpdate`, `AppUpdateInstallState`, `AppUpdateErrorKind`, `UpdateChannel` |
| `LICENSES/` (radice del repo) | i testi di LibrePods, AndroidLiquidGlass, Inter |
| `sample/…/SettingsTab.kt`, `catalog/RowsAndSheetsDemo.kt`, `catalog/MoreComponentsDemo.kt` | aspetto, crediti e aggiornamento cablati come in un'app vera |
| `../api/impostazioni.md` | tutte le firme di questa scheda |
