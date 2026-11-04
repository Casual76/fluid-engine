<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/tema.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Tema, forma, tipo

Questa famiglia e' il **punto da cui parte ogni colore, ogni angolo e ogni carattere** dell'app. Contiene `FluidTheme` (il
composable che si mette alla radice), la derivazione dell'intera palette da **un solo colore** (`AccentPreset`), i token di forma
(`FluidRadius`, `ContinuousCornerShape`, `FluidCapsuleShape`), la tipografia Inter (`fluidTypography`, `FluidTextStyles`), il
`FluidMotionScheme` che passa le molle dell'engine ai componenti Material, e `FluidComponents.kt`: le superfici e i pezzi di
contenuto «di base» (`FluidCard`, `FluidListGroup`, `FluidListRow`, `FluidToneTile`, `FluidInlineMessage`, gli indicatori di
sincronizzazione…) che vivono nel pacchetto `ui.theme` per ragioni storiche.

**Modello mentale.** `EngineSettings` (piccolo, lo possiede l'app) + un `brand` (due colori, chiaro e scuro) entrano in
`FluidTheme`; ne esce un `MaterialTheme` completo (colori, molle, forme, tipografia) piu' qualche `CompositionLocal` che il vetro e
le aptiche leggono. Tutto cio' che l'engine disegna, e tutto cio' che Material disegna sotto quel tema, si tinge da li'. **Niente
legge `isSystemInDarkTheme()` per conto proprio**: il tema sa da che parte sta e lo dice a tutti (vedi la regola trasversale).

Con le altre famiglie: il vetro ([vetro.md](vetro.md)) e i controlli ([controlli.md](controlli.md)) leggono `MaterialTheme.colorScheme` e `LocalFluidSurfaceSide`; le
righe e le card di questa scheda sono i mattoni di `FluidScreen` ([schermata.md](schermata.md)), il resto del contenuto sta in [contenuto.md](contenuto.md); la pagina «Aspetto» che scrive in
`EngineSettings` e' in [impostazioni.md](impostazioni.md); le pagine e `FluidNavHost` sono della famiglia pagine
([pagine.md](pagine.md), qui citate solo `Motion.kt` e `RouteMotion.kt`).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| vestire un'app intera | `FluidTheme(settings, brand = …)` alla radice, una volta |
| vestire un pezzo di schermata con un'altra «parte» (testata su una copertina scura) | `FluidTheme(settings, systemBars = false) { … }`: **non** rivendica le barre di sistema |
| i colori dell'app fuori da una composizione (widget Glance, notifiche) | `fluidColorScheme(settings, isDark, brand, presets, dynamicScheme)` |
| il colore dell'app (marchio) | `AccentPreset("nome", "Etichetta", chiaro, scuro)` e `brand = …`: tutto il resto si deriva |
| sapere se la superficie e' scura **senza** interrogare il sistema | `GlassDefaults.isDarkSurface()` (legge `LocalFluidSurfaceSide`, poi la palette) |
| un angolo da iOS | `ContinuousCornerShape(FluidRadius.Card)`; mai `RoundedCornerShape` |
| una pillola che si adatta all'altezza | `FluidCapsuleShape` (raggio = meta' del lato corto) |
| un angolo di un rettangolo piccolo (sotto ~256 px) | la stessa `ContinuousCornerShape`: da sola ripiega su un `Outline.Rounded` nativo |
| il testo con la scala iOS | `MaterialTheme.typography.<slot>`: gli slot Material risolvono la rampa Apple (34/28/22/20/17/15/13/11) |
| cifre che non ballano (colonna di conteggi, date) | `FluidTextStyles.numeric` / `largeNumeric` (cifre tabulari) |
| intestazione di sezione in maiuscolo con tracking | `FluidTextStyles.uppercaseCaption` |
| muovere un componente Material sulle molle dell'engine | niente: `FluidTheme` imposta gia' `motionScheme = FluidMotionScheme` |
| un gruppo di righe in un pannello arrotondato | `FluidListGroup { FluidListRow(...); FluidListDivider(); … }` |
| un riquadro che dice una cosa | `FluidCard(highlighted = true)` o `FluidInlineMessage` (non `FluidHeroCard`, deprecata) |
| l'icona colorata in testa a una riga | `FluidToneTile` (il colore sta sulla piastrella, **mai** sullo sfondo della riga) |
| stato di sincronizzazione: nella barra / sulla pagina / con spiegazione | `FluidSyncIndicator` / `FluidSyncNotice` / `FluidSyncAction` |
| una pillola di stato non toccabile | `FluidStatusBadge` (non un chip: il chip e' un controllo) |
| uno stato vuoto che dice perche' | `FluidEmptyState(title, detail, illustration)` |
| un'intestazione di pagina di Material | **non** `FluidTopHeader` (deprecata): `FluidScreen`, o `FluidCollapsingTitle` + `FluidCollapsingTopBar` |
| ombra o spaziatura «a token» | **non esistono**: la spaziatura e' dentro i componenti, l'ombra e' del vetro (`glassShadow`) |
| i nomi «FluidAccentPreset» e «EngineThemeMode» (da vecchie guide) | non esistono: il codice ha `AccentPreset` e `ThemeMode` (vedi «Adottare il tema») |

## Regola trasversale: il tema sa da che parte sta

`FluidTheme` calcola `isDark` e lo pubblica come `LocalFluidSurfaceSide`. Chi ha bisogno di sapere «chiaro o scuro» chiama
`GlassDefaults.isDarkSurface()`. **Non** chiede `isSystemInDarkTheme()` (l'app puo' forzare Chiaro, Scuro o AMOLED
indipendentemente dal telefono) e **non** guarda la luminanza di `colorScheme.background`:

- con un'immagine dietro le pagine, `background` e' trasparente e ha luminanza zero, quindi «scuro» sempre; e' successo,
  e un'app chiara ebbe contenitori scuri con testo chiaro (`toneColors`) e le icone della barra di stato chiare su una pagina bianca;
- la luminanza ignora l'alpha: una pellicola traslucida in `colorScheme.surface` verrebbe letta come chiara sempre.

Per lo stesso motivo le icone delle barre di sistema seguono `isDark` del tema, non il flag del telefono. Lo stesso valore guida i
toni (`FluidTone`), il vetro, i pallini dell'accento e la scelta fra i due colori di un preset.

## FluidTheme

**Cos'e'.** Il tema dell'app: risolve il modo chiaro/scuro, sceglie la sorgente dell'accento, costruisce la palette e la passa a
`MaterialTheme` insieme a molle, forme e tipografia dell'engine. Il colore del marchio e' un parametro dal tempo in cui l'engine e' nato (`FluidTheme(settings, brand)`).

**Firma essenziale.**

```kotlin
@Composable
fun FluidTheme(
  settings: EngineSettings,
  brand: AccentPreset = FluidDefaultBrand,
  presets: List<AccentPreset> = fluidAccentPresets,
  systemBars: Boolean = true,
  content: @Composable () -> Unit,
)
```

Tutti i parametri: [`../api/tema.md`](../api/tema.md).

**Uso.** Alla radice dell'`Activity`, con le impostazioni sopra il tema (la galleria `sample` le tiene in uno `state` per far
cambiare l'aspetto dal vivo):

```kotlin
setContent {
  var settings by remember { mutableStateOf(EngineSettings()) }
  FluidTheme(settings = settings) {
    Gallery(settings = settings, onSettingsChange = { settings = it })
  }
}
```

**Comportamento.**

- **Modo.** `SYSTEM` segue `isSystemInDarkTheme()`; `LIGHT` e' chiaro; `DARK` e `AMOLED` sono scuri. `AMOLED` non e' un modo di
  scegliere ma un'altra tavolozza (vedi sotto).
- **Colore dinamico** solo se **tutte e tre** le condizioni: `settings.dynamicColorEnabled` e `settings.accentMode == AccentMode.DYNAMIC`
  e `Build.VERSION.SDK_INT >= S` (Android 12). Altrimenti `dynamicScheme = null` e si ripiega sul `brand`, senza errori.
- **Preset personalizzato.** `AccentMode.CUSTOM_PRESET` cerca `settings.customAccentName` in `presets` **ignorando le maiuscole**;
  se non c'e' (un preset tolto da un aggiornamento), usa **il primo** della lista; una lista vuota ricade su `fluidAccentPresets`.
- **Cosa fornisce.** Oltre a `MaterialTheme(colorScheme, motionScheme = FluidMotionScheme, shapes, typography = fluidTypography())`:
  `LocalFluidHaptics` (un solo motore aptico per tema, acceso/spento da `settings.hapticsEnabled`), `LocalFluidSurfaceSide`
  (da che parte sta) e `LocalFluidSystemContrast` (contrasto di sistema, Android 14+: il vetro si fa piu' denso e il bordo piu'
  netto; segue il cambio con l'app aperta).
- **Barre di sistema.** Con `systemBars = true` un `SideEffect` imposta `isAppearanceLightStatusBars` e
  `isAppearanceLightNavigationBars` sulla finestra dell'`Activity` (`!isDark`). Fuori da una `Activity` (finestre di dialogo) o in
  anteprima di Studio non fa niente.
- **Forme Material.** `extraSmall` = `FluidRadius.Small`, `small` = `Control`, `medium` = `Card`, `large` = `Group`,
  `extraLarge` = `Sheet`, tutte continue. Un componente Material che legge `MaterialTheme.shapes.*` ha gia' l'angolo giusto.
- **Costo.** Il tema non crea layer ne' registrazioni: ricostruisce lo schema colori a ogni cambio di `settings`/`isDark`.

**Trappole.**

- **`systemBars = false` per i temi annidati.** Lasciato `true`, vince il tema piu' interno che ha ricomposto per ultimo: un'app
  chiara che mostrava una testata scura su una copertina ebbe i glifi della barra di stato bianchi su una pagina bianca. Un tema
  applicato a un *pezzo* di schermata non ha titolo per decidere che aspetto ha l'orologio (2.4.0, CHANGELOG «theme: `FluidTheme`
  accetta `systemBars`»).
- **Un `FluidTheme` annidato rimette tutto.** Si porta dietro un'aptica nuova e toglie, per esempio, un registro aptico installato
  fuori: i decoratori vanno **dentro** il tema.
- **Il tema chiaro con `amoledEnabled = true` non cambia niente**: il nero esiste solo nel ramo scuro.
- **I due colori del marchio non sono intercambiabili.** `light` e `dark` sono scelti perche' lo stesso RGB raramente tiene
  carattere e contrasto su entrambi gli sfondi: non passare lo stesso colore a tutti e due a meno che sia davvero giusto.
- **Il preset `fluid` (blu) non e' il marchio `fluid` (verde).** `fluidAccentPresets` ha un preset di nome `fluid` (blu iOS) e
  `FluidDefaultBrand` ha lo stesso nome ma e' verde: non confliggono (il brand non passa dalla ricerca per nome), ma un
  `customAccentName = "fluid"` salvato sceglie il **blu**.

**Non usarlo quando.** Per un widget di Glance (non c'e' un `MaterialTheme` da leggere): `fluidColorScheme` o `EngineWidgetPalette`
(famiglia widget).

**Versione.** `presets` (e `AccentPoles`) dalla 1.10; `systemBars` dalla 2.4; il lato via `LocalFluidSurfaceSide` dalla 2.0; il
contrasto di sistema (`LocalFluidSystemContrast`) dalla 3.0.

## La palette da un colore solo

**Cos'e'.** L'app non descrive 30 colori: dichiara **un accento** (due valori, chiaro e scuro) e l'engine ne deriva primario,
secondario, terziario, contenitori, la scala delle superfici e le coppie sfondo/contenuto. E' quello che fa restare riconoscibile
un'app dopo il cambio di tema: il colore del marchio e' il `primary` che aveva.

```kotlin
private val AppBrand = AccentPreset(
  name = "app", label = "App",
  light = Color(0xFF3B5BDB), dark = Color(0xFFB6C4FF),
)
```

`AccentPreset(name, label, light, dark, poles)`; `resolve(isDark)` sceglie il valore. `fluidBrandAccent(isDark, brand)` e'
la stessa cosa per chi ha solo il `Color`.

**Come si deriva (numeri veri da `FluidTheme.kt`).**

- **Accento effettivo** = `primary` del colore dinamico, se attivo; altrimenti `brand` (modi `BRAND` e `DYNAMIC` senza supporto) o
  il preset scelto (`CUSTOM_PRESET`).
- **Secondario e terziario** fondono l'accento verso due «poli»: indaco iOS (`0xFF5856D6` chiaro, `0xFF5E5CE6` scuro) al **26%** e
  arancio iOS (`0xFFFF9500` chiaro, `0xFFFF9F0A` scuro) al **22%**. Per un accento blu-verde danno tre famiglie distinte
  gratis.
- **Contenitori**: `primaryContainer` = scala `low` verso l'accento al 17% (chiaro) / 28% (scuro); `secondaryContainer` = scala
  `container` verso il secondario al 14% / 22%; `tertiaryContainer` = scala `high` verso il terziario all'11% / 18%.
- **Contenuto** (`onPrimary`, `onXContainer`, `onError`…): `highestContrastContent` sceglie fra `0xFF121214` e `0xFFFDFDFF` quella
  col rapporto WCAG piu' alto. Una coppia sfondo/contenuto non e' mai separata.
- **Scala delle superfici**: un neutro (chiaro `0xFFF2F2F7` sfondo, `White` superficie; scuro `0xFF0D0D0F` / `0xFF1B1B1E`) con un
  *velo di tinta* dell'accento: `background` 1,2%, `surface` 1,4%, `lowest` 0,8%, `low` 2,4%, `container` 3,8%, `high` 5,5%,
  `highest` 7,5%. Le superfici grandi restano quiete; piu' sono piccole e alte, piu' portano il colore.
- **Errore**: `0xFFD70015` (chiaro) / `0xFFFF6961` (scuro); `errorContainer` = `container` verso l'errore al 14% / 24%.
- `surfaceTint` e' trasparente (niente tinta di elevazione di Material), `scrim` nero.
- **Colore dinamico**: contribuisce **tutte e tre le famiglie** con le loro coppie e contenitori (12 colori); si ricostruiscono solo
  le grandi superfici neutre, per tenere il carattere quieto dell'app, usando `dynamic.primary` come velo.

**`AccentPoles`.** Un accento che vive *vicino* a uno dei due poli (un viola vicino all'indaco) fa collassare l'anello: il
secondario diventa indistinguibile dal primario. Il preset allora dichiara i suoi poli:

```kotlin
AccentPreset(
  name = "amethyst", label = "Ametista",
  light = Color(0xFF9966CC), dark = Color(0xFFB88CE8),
  poles = AccentPoles(
    secondaryLight = Color(0xFF007AFF), secondaryDark = Color(0xFF0A84FF),
    tertiaryLight = Color(0xFFFF2D55), tertiaryDark = Color(0xFFFF375F),
    secondaryBlend = 0.45f, tertiaryBlend = 0.40f,
  ),
)
```

I `Blend` sono piu' alti dei default (0,26 / 0,22) perche' un polo scelto per **contrastare** vuole una spinta piu' forte di uno
scelto per armonizzare. Senza `poles` la derivazione e' quella storica **al bit** (un test lo blocca: un'app che non ne ha mai
chiesti non deve cambiare colore all'aggiornamento).

**Nero assoluto.** `amoled = isDark && (themeMode == AMOLED || amoledEnabled)`. Con il nero lo sfondo e' esattamente `Color.Black`
(test) e la tinta dello sfondo e' **zero**; le altre superfici salgono di pochi gradini (`0xFF0E0E10`, `0xFF121214`, `0xFF161618`,
`0xFF1B1B1E`, `0xFF222225`) e l'outline si scurisce (`0xFF343438`). Il vetro non usa `surface` come film proprio (una barra del
colore dello sfondo su AMOLED sparirebbe): vedi `GlassDefaults.glassFilm()` in `../regole.md`.

**I preset di serie.** `fluidAccentPresets` = quattro preset con **nomi conservati dalla palette precedente** (cosi' una
preferenza salvata si risolve ancora) e colori del set di sistema iOS:

| `name` | `label` | chiaro | scuro |
|---|---|---|---|
| `fluid` | Blu | `0xFF007AFF` | `0xFF0A84FF` |
| `ember` | Arancio | `0xFFFF9500` | `0xFFFF9F0A` |
| `ocean` | Indaco | `0xFF5856D6` | `0xFF7D7AFF` |
| `jade` | Verde | `0xFF34C759` | `0xFF30D158` |

`FluidDefaultBrand` (nome `fluid`, etichetta «Fluid», `0xFF1F9E6E` / `0xFF2ED8A0`, verde) e' il marchio di ripiego prima che
l'app ne abbia uno. Un'app con un'identita' passa il **suo** preset a `FluidTheme(brand = …)`; un'app che vuole altri preset nel
selettore passa `presets = …` **sia a `FluidTheme` sia a `fluidAppearanceSection`/`FluidAccentPicker`**, altrimenti selettore e
tema parlano di liste diverse.

**Trappole.**

- **Il marchio si prende dall'app, non si inventa.** Sono il `primary` del suo vecchio `lightColorScheme` e `darkColorScheme`.
- **Preset con nomi che cambiano** = preferenze orfane: il tema usa il **primo** preset, e un selettore costruito con
  `FluidAppearance.presetName` lo sa dire (vedi [impostazioni.md](impostazioni.md)).
- **Con `presets = emptyList()`** il tema ricade su `fluidAccentPresets`, ma `FluidAccentPicker` non disegna nessun pallino
  preset: l'inconsistenza e' nel codice (segnalata nel messaggio di consegna), non passare liste vuote.
- **Success e Warning di `FluidTone` non seguono il marchio** (vedi «Il tono»): sono colori fissi.

**Versione.** `AccentPoles` e la lista `presets` dalla 1.10 (anche `fluidColorScheme` le accetta); `fluidColorScheme` e' la stessa
derivazione che usa il tema (la funzione interna `resolveFluidColorScheme`), cosi' widget e notifiche dipingono con gli stessi colori.

## I token di forma

**`FluidRadius`**: i raggi dell'app, **piu' grandi** degli equivalenti Material di proposito, perche' un angolo continuo dello
stesso numero sembra piu' stretto (la curva sta piu' a lungo vicino al bordo).

| token | dp | dove |
|---|---|---|
| `Small` | 10 | piastrelle d'icona, badge piccoli; `Shapes.extraSmall` |
| `Control` | 12 | campi, controlli; `Shapes.small` |
| `Card` | 18 | `FluidCard`, `FluidMetricTile`, `FluidStatChip`; `Shapes.medium` |
| `Group` | 22 | `FluidListGroup`, `FluidEditorialCard`; `Shapes.large` |
| `Sheet` | 38 | fogli; `Shapes.extraLarge` |

**`ContinuousCornerShape`** (la sagoma da iOS). Un angolo circolare unisce un lato dritto a un arco, quindi la curvatura salta da 0 a
`1/r` in un punto: l'occhio lo legge come una piega. Quello continuo fa **salire e scendere** la curvatura (costruzione di Figma:
curva di raccordo, arco accorciato, curva speculare). `smoothing = 0` e' un angolo circolare; `IosSmoothing` = 0,6 (il 60% dello
slider di Figma) e' il default. Costruttori: `ContinuousCornerShape(radius)` e
`ContinuousCornerShape(topStart, topEnd, bottomEnd, bottomStart)` (tutti `Dp`, default 0), o i `CornerSize` per costruire
da `CornerBasedShape`; `RTL` scambia gli angoli iniziali e finali.

Comportamento che il nome non dice:

- **Sotto ~256 px sul lato maggiore diventa un `Outline.Rounded` nativo**, non un path. Non e' una ritirata di design: un path
  generico cosi' piccolo passa dall'atlante di maschere di Skia, rasterizzate sulla **CPU** e ricache'ate con la fase sub-pixel;
  una lista che scorre cambia la fase a ogni fotogramma, quindi ogni badge, pillola e piastrella ri-rasterizzava e ricaricava la
  sua maschera a ogni fotogramma. «Un centinaio di maschere a fotogramma» fece scorrere a cinque fotogrammi al secondo la
  bacheca su un tablet. A quella taglia lo smoothing che si perde sta dentro un pixel.
- **Il raggio ripiegato e' ridotto a 0,82×** (`roundedEquivalent`) per sembrare uguale: usare i raggi di `FluidRadius` tali e
  quali su un rettangolo arrotondato circolare gonfiava ogni angolo, e si vedeva su ogni badge. Una capsula non si riduce.
- **Quando due angoli si toccano cede lo smoothing, non il raggio.** L'angolo consuma `(1 + s) · r` per lato e ne e'
  disponibile solo mezzo: cedendo il raggio, un 50% si fermava al 62,5% del mezzo lato e una capsula veniva uno squircle con le
  estremita' piatte (corretto nella 1.4.0). Lo smoothing si esaurisce proprio quando l'angolo e' un semicerchio.
- E' una `CornerBasedShape`: **il vetro ne legge i raggi** (la lente segue la stessa sagoma), quindi vetro e superfici di pagina
  condividono la silhouette.

**`FluidCapsuleShape`.** Raggio sempre meta' del lato corto: interruttori, segmentati, pillole, badge. Non dare mai un raggio
fisso a una pillola: si vede appena il controllo cambia altezza.

**Non usarli quando.** `RoundedCornerShape` e `MaterialTheme.shapes.large` letti a mano come angolo di una superficie di
contenuto: sono circolari e violano la prima regola del design system (successe dentro l'engine stesso, in `FluidCard` e
`FluidListGroup`).

## La tipografia

**`fluidTypography()`** restituisce un `Typography` Material **con i nomi degli slot di sempre e la rampa Apple**: 34 / 28 / 22 /
20 / 17 / 15 / 13 / 11 sp invece di 57 / 45 / 36…, troppo grande per un telefono e l'altra meta' del perche' un'app sembrava un
esempio Material. Il carattere e' **Inter** (OFL: Roboto e' il primo segnale che un'app e' Android; Inter ha le proporzioni di San
Francisco e puo' essere distribuito).

| slot | sp / interlinea | peso |
|---|---|---|
| `displayLarge`, `displayMedium` | 34 / 41 | Bold |
| `displaySmall` | 30 / 36 | Bold |
| `headlineLarge` / `Medium` / `Small` | 28/34, 26/32, 22/28 | Bold |
| `titleLarge` | 20 / 25 | SemiBold |
| `titleMedium` | 17 / 22 | SemiBold (la riga di lista) |
| `titleSmall` | 15 / 20 | SemiBold |
| `bodyLarge` / `Medium` / `Small` | 17/23, 15/21, 13/18 | Normal |
| `labelLarge` / `Medium` / `Small` | 15/20 SemiBold, 13/17 Medium, 11/14 SemiBold (tracking 0,5 sp) | |

Due cose rendono il testo «SF» e non «un bel grottesco»:

- **Taglio ottico.** Dai 20 sp in su (`display = sizeSp >= 20f`) si usa `FluidDisplayFontFamily` (Inter Display: spaziatura piu'
  stretta, aperture piu' chiuse); sotto, `FluidFontFamily` (Inter Text). Apple cambia taglio a 20 pt.
- **Tracking per dimensione**, interpolato linearmente fra i punti ancora (sp → tracking sp): 11 → +0,30; 13 → 0; 15 → −0,15; 17 →
  −0,25; 20 → −0,35; 24 → −0,45; 28 → −0,55; 34 → −0,70; fuori dall'intervallo si fissa al valore estremo. Inter a 0 piatto
  sembra largo nei titoli e stretto nelle didascalie.
- L'interlinea e' **centrata** nella riga (`LineHeightStyle.Alignment.Center`, `Trim.None`) e `includeFontPadding = false`: un 17 sp in
  una riga da 22 non porta tutti i 5 sp di aria sopra, e un titolo in una riga di altezza fissa non sembra basso.

**`FluidTextStyles`**: stili senza slot Material. `uppercaseCaption` (12/16, SemiBold, tracking 0,6: le maiuscole non hanno
ascendenti e discendenti e vogliono molto piu' tracking di quello della curva), `numeric` (17/22 SemiBold con `tnum`) e
`largeNumeric` (34/38 Bold con `tnum`): le cifre tabulari tengono in colonna voti, date, conteggi senza «tremare» mentre cambiano.

**Trappola.** I font sono risorse di `engine-ui` (`R.font.inter_*`): un'app che cambia `FluidFontFamily` sostituendolo altrove
perde l'ottica a 20 sp. Se ti serve un `TextStyle` a mano, parti da uno slot, non da `TextStyle()`: altrimenti perdi tracking,
interlinea centrata e carattere.

## Le molle: `FluidMotionScheme`

`FluidTheme` imposta `motionScheme = FluidMotionScheme`, quindi **ogni componente Material** che legge
`MaterialTheme.motionScheme` (interruttori, chip, fogli) si muove sugli stessi tempi del resto. Mappa (tutte da `FluidMotion`):

| slot Material | molla dell'engine | risposta / smorzamento |
|---|---|---|
| `defaultSpatialSpec` | `FluidMotion.standard()` | 0,38 s, 0,9 |
| `fastSpatialSpec` | `FluidMotion.snappy()` | 0,30 s, critico |
| `slowSpatialSpec` | `FluidMotion.smooth()` | 0,50 s, 0,9 |
| `defaultEffectsSpec` | `FluidMotion.snappy()` | 0,30 s, critico |
| `fastEffectsSpec` | `FluidMotion.instant()` | 0,20 s, critico |
| `slowEffectsSpec` | `FluidMotion.smooth()` | 0,50 s, 0,9 |

Le molle «effetto» (colore, alfa) non rimbalzano (smorzamento critico): una barra di navigazione che rimbalza e' il segnale piu'
forte che un'app non e' curata. Il resto del movimento (menu, gocce, pagine) e' in `FluidMotion` (famiglia movimento,
[movimento.md](movimento.md)). `Motion.kt` e `RouteMotion.kt` stanno nel pacchetto `ui.theme` ma sono della famiglia pagine: li' vivono
`FluidRouteMotion` e `FluidRouteMotionHost`, **deprecati dalla 3.0** (le pagine le muove `FluidNavHost`; il vecchio involucro non
sfoca piu' ne' arrotonda e dentro un `FluidNavHost` non si mette).

## Il tono: `FluidTone`

`FluidTone` = `Primary`, `Success`, `Warning`, `Danger`, `Info`, `Neutral`. E' la lingua con cui righe, badge, tessere e messaggi
dicono «che tipo di cosa sono» senza dipingersi da soli. Colori (contenitore → contenuto), da `toneColors`:

| tono | contenitore | contenuto |
|---|---|---|
| `Primary` | `primaryContainer` | `onPrimaryContainer` |
| `Success` | `0xFFE8F5E9` chiaro / `0xFF0F2D1D` scuro | `0xFF1B5E20` / `0xFF81C784` |
| `Warning` | `0xFFFFF3E0` / `0xFF332000` | `0xFFE65100` / `0xFFFFB74D` |
| `Danger` | `errorContainer` | **`error`** (non `onErrorContainer`: sul chiaro e' un bruno quasi nero) |
| `Info` | `secondaryContainer` | `onSecondaryContainer` |
| `Neutral` | `surfaceContainerHigh` | `onSurfaceVariant` |

**`Success` e `Warning` sono colori fissi**: non seguono il marchio ne' il colore dinamico (un verde e un arancio devono restare
verde e arancio). Gli altri quattro si tingono col tema.

## Adottare il tema in un'app che ne ha gia' uno

**Non cambiare i punti di chiamata.** Si tiene la funzione di tema che l'app ha gia' (stesso nome, stessa firma) e se ne riscrive
il **corpo**: i call-site non si toccano e il diff resta in un solo file (se non convince, `git checkout` di quel file).

```kotlin
import dev.antigravity.fluidengine.foundation.ThemeMode as EngineThemeMode

private val AppBrand = AccentPreset("app", "App", Color(0xFF3B5BDB), Color(0xFFB6C4FF))

@Composable
fun MioTemaApp(themeMode: ThemeMode, dynamicColor: Boolean, content: @Composable () -> Unit) {
  FluidTheme(
    settings = EngineSettings(
      themeMode = themeMode.toEngine(),
      accentMode = if (dynamicColor) AccentMode.DYNAMIC else AccentMode.BRAND,
      dynamicColorEnabled = dynamicColor,
    ),
    brand = AppBrand,
    content = content,
  )
}

private fun ThemeMode.toEngine(): EngineThemeMode = when (this) {
  ThemeMode.System -> EngineThemeMode.SYSTEM
  ThemeMode.Light -> EngineThemeMode.LIGHT
  ThemeMode.Dark -> EngineThemeMode.DARK
}
```

**Il nome vero del modo e' `ThemeMode`** (in `dev.antigravity.fluidengine.foundation`, con `SYSTEM`, `LIGHT`, `DARK`, `AMOLED`). Non
esiste nessun tipo chiamato `EngineThemeMode`: nella documentazione di integrazione e' un **alias d'importazione** (`import …
as EngineThemeMode`), necessario perche' un'app ha quasi sempre un suo `ThemeMode`. Chi cerca `EngineThemeMode` nel codice non lo
trova. Stessa cosa per «FluidAccentPreset»: la classe si chiama `AccentPreset`.

Tre cose che fanno la differenza:

1. **I colori del marchio si prendono dall'app** (il `primary` dei suoi vecchi schemi chiaro e scuro), non si inventano.
2. **Mappa al bordo, non allargare `EngineSettings`**: se l'app ha un suo modello di impostazioni, un `map { }` sul flow che gia'
   esiste; `EngineSettings` e' piccolo apposta e non accoglie campi di una sola app.
3. **Cancella le vecchie `lightColorScheme`/`darkColorScheme`/`Shapes`** nello stesso commit: resterebbero codice morto a far
   credere che siano ancora loro a decidere.

Verifica sempre nell'ordine: `engine-doctor.ps1`, poi `:app:assembleDebug :app:testDebugUnitTest`
(procedura completa: `../integrazione.md`).

## `EngineSettings` → tema

`EngineSettings` (in `engine-foundation`, quindi niente Android UI) e' **tutto cio' che l'engine deve sapere per dipingere**:

| campo | default | effetto sul tema |
|---|---|---|
| `themeMode: ThemeMode` | `SYSTEM` | `SYSTEM` segue il telefono; `LIGHT`; `DARK`; `AMOLED` = scuro + nero |
| `accentMode: AccentMode` | `BRAND` | `BRAND` = marchio dell'app; `DYNAMIC` = colori di sistema (Android 12+, altrimenti `BRAND`); `CUSTOM_PRESET` = un preset per nome |
| `customAccentName: String` | `"fluid"` | il preset per `CUSTOM_PRESET`; nome ignoto → primo preset |
| `dynamicColorEnabled: Boolean` | `true` | serve **insieme** ad `accentMode = DYNAMIC` |
| `amoledEnabled: Boolean` | `false` | nero assoluto sotto il tema scuro (stessa tavolozza del modo `AMOLED`) |
| `hapticsEnabled: Boolean` | `true` | accende il motore aptico di `LocalFluidHaptics` (default acceso: un'impostazione che compare non deve cambiare quello che un telefono faceva) |

`ThemeMode.AMOLED` e `amoledEnabled` producono la **stessa** tavolozza: il secondo esiste per offrire il nero come interruttore
sotto «Scuro» invece che come quarto pulsante. `EngineSettingsStore` (`engine-storage`) salva e rilegge i sei campi con DataStore
e un valore salvato che non nomina piu' niente (una voce di enum tolta da un aggiornamento) torna al default invece di lanciare.
Per un'app con un suo store basta mappare. La traduzione fra quello che mostra una pagina «Aspetto» e questi campi (`AMOLED`
= «Scuro» + nero, `DYNAMIC` senza supporto = marchio) e' **in** `FluidAppearance`, descritta in
[impostazioni.md](impostazioni.md): non riscriverla.

## Le superfici e i pezzi di contenuto (`FluidComponents.kt`)

**Cos'e'.** Il file non «ridefinisce componenti Material»: l'unico modo in cui il tema ridefinisce Material e' passare
`colorScheme`, `motionScheme`, `shapes` e `typography` a `MaterialTheme` (quindi uno `Switch`, un `Chip` o un `AlertDialog` Material
presi nudi prendono colori, molle, angoli e carattere dell'engine). `FluidComponents.kt` contiene invece i **componenti Fluid di
contenuto**, costruiti su pochi mattoni Material (`Surface`, `ListItem`, `HorizontalDivider`) e, in coda, i **vecchi nomi** che
prima erano wrapper di Material e oggi sono deprecati e delegano. Tutti sono nel pacchetto `dev.antigravity.fluidengine.ui.theme`
(i controlli, `FluidButton`, `FluidChip`… stanno in `ui.fluid`).

**La regola del vetro, che questi componenti applicano.** `glass = true` e' una **richiesta**, non un'istruzione: ha effetto solo
dove `FluidScreen(ambient = …)` ha registrato un canvas (`LocalFluidCanvasBackdrop`) **e** c'e' `RenderEffect` (API 31+); senza,
il componente disegna la superficie opaca di sempre. Per questo `glass = true` si puo' aggiungere ovunque. Il vetro va sul
**contenitore, mai sulla riga**: un gruppo di dodici righe e' un nodo, non dodici (ogni pannello costa una registrazione di layer e
una catena di `RenderEffect` sulla sua area). Testo e icone sopra il vetro restano opachi. Se il fondale e' gia' un pannello (foglio,
pop-up), il contenuto diventa **vetro impilato** (`stackedContentTint`/`stackedContentOptics`).

### FluidCard

**Cos'e'.** La card di contenuto: angolo `FluidRadius.Card`, `surfaceContainerLow` (`surfaceContainerHigh` se `highlighted`),
padding 16 dp, colonna con 10 dp fra i figli. Non e' `Card` di Material: niente elevazione.

```kotlin
FluidCard(
  modifier: Modifier = Modifier,
  highlighted: Boolean = false,
  onClick: (() -> Unit)? = null,
  animateContent: Boolean = true,
  animatePress: Boolean = true,
  glass: Boolean = false,
  enabled: Boolean = true,
  sampleOnce: Boolean = true,
  resampleIntervalMillis: Long = 0L,
  content: @Composable ColumnScope.() -> Unit,
)
```

**Uso.**

```kotlin
FluidCard(highlighted = true, onClick = { /* apri */ }) {
  Text("Titolo", style = MaterialTheme.typography.titleMedium)
  Text("Una frase che spiega.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

**Comportamento.**

- Con `onClick` cede sotto il dito (`fluidPressable`, scala 0,974 se `animatePress`); senza, non e' un bersaglio.
- `animateContent` fa crescere/rimpicciolire l'altezza con la molla `FluidMotion.intSize()` quando il contenuto cambia.
- `highlighted` su vetro **sopravvive come velo** (`surfaceContainerHigh` al 28%), non come riempimento: una card in evidenza che
  diventasse opaca sarebbe l'unica della pagina fatta di un altro materiale e si leggerebbe come un errore.
- `enabled = false` (3.0.1): la card non risponde e il **contenuto** sfuma a metà (alfa 0,5), non la card: un alfa sul vetro farebbe
  sfumare anche cio' che il vetro rifrange. Per TalkBack e' «disattivato».
- `sampleOnce` / `resampleIntervalMillis` (3.0.4) sono i controlli del vetro di livello piu' basso: campiona il fondale **una
  volta** e tiene la cattura finche' la card si muove (di serie, perche' il fondale di una pagina e' una lavata ferma); `false` per una card sopra un fondale
  che cambia da solo (canvas animato, immagine che scorre), altrimenti porta in giro una cattura vecchia.

**Trappole.** Il rischio e' `glass = true` su molte card in una lista che scorre: una registrazione ciascuna (per le liste lunghe
`FluidListGroup` o `fluidGlassGroups`). Su un fondale animato, ricordati `sampleOnce = false`.

**Non usarla quando.** Una riga di lista (usa `FluidListRow` in un `FluidListGroup`) o un numero con un'etichetta (`FluidMetricTile`).

### FluidListGroup, FluidListDivider, FluidListRow

**Cos'e'.** Il trio della **lista raggruppata** (stile iOS inset-grouped): un contenitore con angolo `FluidRadius.Group`, righe che lo
condividono, un separatore rientrato.

```kotlin
FluidListGroup(modifier = Modifier, glass = false, segment = FluidGroupSegment.Whole) { /* righe */ }

FluidListRow(
  title: String, subtitle: String,
  eyebrow: String? = null, meta: String? = null,
  tone: FluidTone = FluidTone.Neutral,
  badge: (@Composable () -> Unit)? = null,
  labels: (@Composable () -> Unit)? = null,
  titleMaxLines: Int = Int.MAX_VALUE,
  leading: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,
  selected: Boolean = false,
  disclosure: Boolean = true,
  leadingTile: Boolean = true,
  subtitleMaxLines: Int = Int.MAX_VALUE,
  /* animatePress, animateContent: vedi api/tema.md */
)
```

Esiste anche l'overload con `title: AnnotatedString` (stili propri nel titolo, per esempio le parole cambiate dall'ultima volta);
quello con `String` delega a lui. Tutti i parametri: [`../api/tema.md`](../api/tema.md).

**Uso** (schema della galleria):

```kotlin
FluidListGroup(glass = true) {
  FluidListRow(
    title = "Conferma semplice",
    subtitle = "Foglio dal basso con icona, frase e due tasti.",
    tone = FluidTone.Primary,
    leading = { Icon(Icons.Rounded.Speed, contentDescription = null) },
    onClick = { /* … */ },
  )
  FluidListDivider()
  FluidListRow(title = "Archivio", subtitle = "Restano", disclosure = false)
}
```

**Comportamento (la riga).**

- **Un nodo semantico solo**, per costruzione. Il `ListItem` di Material fonde da se' la propria semantica e Compose non fonde dentro
  un nodo che fonde un figlio che a sua volta fonde: con un `clickable` su un contenitore la riga erano **due nodi** (uno col clic e
  senza nome, uno col nome e senza clic). Qui il modificatore (clic, ruolo, stato, velo) sta sul `ListItem` stesso, dove nome
  (titolo, sottotitolo, pillole, badge), ruolo, stato e clic coesistono. `Role.Button` solo se c'e' `onClick`: senza e' una etichetta.
- **Layout dei testi**: `eyebrow` (sopra, nel colore del tono, `labelMedium`), `title` (`titleMedium` SemiBold), `subtitle`
  (`bodyMedium`, `onSurfaceVariant`), `meta` (`bodySmall`). Un `subtitle` vuoto **e** un `meta` vuoto non riservano la riga di sostegno
  (altrimenti una voce con solo titolo sembrerebbe avere un buco sotto).
- **`leading`** sta nella `FluidToneTile` del `tone`; con `leadingTile = false` sta com'e' e il colore lo sceglie l'app con una
  `FluidToneTile` sua (un colore per cartella, per conto) (3.0).
- **A destra**: `badge` (un valore, un interruttore; prende la sua larghezza per primo, tienilo stretto) e, se c'e' `onClick` e
  `disclosure`, la chevron (18 dp, `onSurfaceVariant` al 55%). `disclosure = false` in un elenco+dettaglio: la riga sceglie cosa
  mostrare accanto, non apre una pagina.
- **`labels`** (pillole di stato) sta accanto al titolo se c'e' posto e **sotto** se ruberebbe la riga: accanto quando il titolo ci sta
  intero su una riga nello spazio che resta, o quando le etichette prendono al massimo **un quarto** della larghezza
  (`FluidRowLabelsMaxShare`); altrimenti sotto. Il quarto e' misurato su un tablet a tre colonne: con i quattro decimi di prima il
  titolo restava una striscia di due parole per riga. Allineate alla prima linea di base del titolo, non centrate sul blocco.
- **`selected`**: velo dell'accento (12%) dietro la riga, come la barra laterale; serve nello schermo largo dove l'elenco resta
  visibile accanto al dettaglio (`FluidListDetailScaffold`).
- **Pressione**: la riga **vira di tinta**, non scala (`fluidRowPressable`): rimpicciolire una riga di una pila rompe la sagoma del
  gruppo. Il tono sta sulla piastrella e sull'eyebrow, **mai sullo sfondo della riga**: tingerlo trasformava un gruppo ordinato in un
  patchwork di blocchi colorati.
- **`contextActions`** (lambda che restituisce `FluidContextAction`): la riga registra la propria immagine, si solleva dalla pagina
  sfocata e appende il menu; **un parametro solo e' tutta l'iscrizione**. Un click lungo suona `Open` se il menu si apre (e il dito
  passa al menu: si sceglie trascinando e rilasciando), altrimenti `GestureStart` e poi `onLongClick`. Preferiscilo a `onLongClick`
  da solo: un click lungo che fa una sola cosa senza dirlo non lo scopre nessuno.
- **Gruppo**: `Surface` con `surfaceContainerLow` (trasparente su vetro), `Surface` ritaglia gia' sull'angolo (un secondo clip
  creava un layer offscreen enorme per tutto il gruppo, e costava in liste lunghe di catalogo). `FluidListDivider` e' una
  `HorizontalDivider` da 0,5 dp, 10% di `onSurface`, rientrata di 16 dp (comincia dove comincia il testo).
- **`FluidGroupSegment`** (`Whole`/`First`/`Middle`/`Last`) esiste per **tagliare una lista lunga in piu' pannelli**: un pannello e' un
  `GraphicsLayer`, e oltre il tetto di texture la registrazione torna vuota e il pannello disegna **nero**. `fluidGlassGroups` (famiglia
  contenuto) lo usa da solo; un lato aperto non ha angoli tondi e viene **ritagliato di `FluidGroupOpenEdge` = 2 dp** per togliere di mezzo
  il bordo speculare del vetro (due bordi a filo mettevano una banda bianca di sei pixel); chi taglia sovrappone i pezzi di 2 dp.

**Trappole.**

- **Non passare `segment` se non sei il codice che taglia**: un gruppo con due angoli tondi sembrera' rotto il giorno in cui la lista
  sopra o sotto cambia lunghezza.
- **`FluidListRow` non e' un pulsante con un bordo**: `badge` + chevron + `labels` si litigano la riga stretta; per piu' di una pillola
  usa `labels`, non `badge`.
- Il titolo ha `maxLines` infinito di serie: una riga con titolo lungo cresce; `titleMaxLines`/`subtitleMaxLines` mettono i puntini
  (il testo intero vive nel dettaglio).
- Il `ListItem` di Material ha gia' 16 dp di margine orizzontale e 56 dp di altezza minima: in un gruppo di sole etichette
  l'altezza non scende sotto quella.

**Non usarli quando.** Righe di impostazioni con interruttore o scelta (`FluidToggleRow`, `FluidChoiceRow`, famiglia controlli: la riga *e'*
l'interruttore, un bersaglio solo) o righe di riordino (`fluidReorderable`).

**Versione.** `FluidGroupSegment` dalla 1.9.11 (un primo tentativo fu ritirato nella 1.9.8: un pannello con angoli misti disegnava
due sagome e il bordo speculare restava staccato); `FluidGroupOpenEdge` e' il rimedio al bordo doppio. `leadingTile` 3.0;
`subtitleMaxLines` 3.0.1; con la 3.0 `contextActions` solleva la riga e fa crescere il pannello accanto a lei.

### FluidToneTile

**Cos'e'.** Il quadrato arrotondato e colmo in cui sta l'icona in testa a una riga. Dalla 3.0 e' **pubblica** (era `ToneIconTile`,
privata): le app la riscrivevano a 30 dp con un velo diverso e i colori del tono mappati a mano.

```kotlin
FluidToneTile(tone = FluidTone.Success) { Icon(Icons.Rounded.Folder, contentDescription = null) }
FluidToneTile(color = Color(0xFFE91E63)) { Icon(Icons.Rounded.Mic, contentDescription = null) }
FluidToneTile(color = Color(0xFF3949AB), veil = Color(0xFF3949AB).copy(alpha = 0.30f), size = 48.dp) { /* … */ }
```

**Comportamento.** Lato di serie `FluidToneTileSize` = 32 dp; il contenuto sta in **19 dp al centro**; angolo `FluidRadius.Small`
(10 dp a 32) e contenuto **scalano col lato** (un 10 dp fisso su una piastrella da 56 e' un quadrato). Il velo e' il colore al 16% (costante
interna); `veil` separato perche' non sempre coincide col colore (un riempimento e un inchiostro scelti per leggersi l'uno
sull'altro: il 16% dell'inchiostro non e' il suo riempimento). Il contenuto riceve `LocalContentColor` = colore.

**Non usarla quando.** Un'icona senza riquadro in una barra (`FluidBarAction`) o un indicatore di stato (`FluidStatusBadge`).

### FluidInlineMessage

**Cos'e'.** Un messaggio dentro la pagina: titolo, frase e, se serve, qualcosa da toccare. E' una `FluidCard(highlighted = tone != Neutral)`.

```kotlin
FluidInlineMessage(
  message: String, modifier: Modifier = Modifier,
  title: String? = null, tone: FluidTone = FluidTone.Info,
  onDismiss: (() -> Unit)? = null, onClick: (() -> Unit)? = null,
  action: String? = null, leading: (@Composable () -> Unit)? = null, glass: Boolean = false,
)
```

**Comportamento.**

- Con `onClick` **tutto il messaggio e' un bersaglio** (cede sotto il dito, ruolo pulsante, chevron a destra): un avviso toccabile
  senza un segno e' una promessa che nessuno scopre. `action` («Apri le impostazioni») e' una riga nel colore del tono, e **conta solo con
  `onClick`**: da sola sarebbe una riga di testo che finge di essere cliccabile.
- `onDismiss` sostituisce la chevron (un messaggio che si puo' chiudere e toccare tiene la chiusura: l'azione che non si puo' sbagliare);
  la X ha il nome `LocalFluidLabels.hide` («Nascondi»).
- Senza `title` dice `LocalFluidLabels.message` («Messaggio», 3.0.4: era un titolo italiano scritto dentro).
- `leading` e' una `FluidToneTile` del tono (distingue un avviso da un errore senza leggere); `glass` come `FluidCard` (3.0.1, era
  l'unico gruppo opaco di una pagina di vetro).

### Sincronizzazione: FluidSyncIndicator, FluidSyncNotice, FluidSyncAction, `SyncStatus.lastSyncLabel`

**Cos'e'.** Lo stato dell'ultimo aggiornamento dati (`SyncStatus` di `engine-foundation`) in tre gradi di volume: un glifo nella barra
(`FluidSyncIndicator`), una frase sulla pagina (`FluidSyncNotice`), il glifo toccabile con la spiegazione dietro (`FluidSyncAction`).

**Il perche'.** Un pallino colorato accanto al titolo grande non significa niente senza la vista dei colori, e finisce **sotto** il
titolo appena questo viaggia al centro della barra. Un rosso che non si puo' interrogare «e' una voce, non uno stato».

**Comportamento.**

- **Tono** (stesso per i tre): se c'e' `notice` → `Info`; `IDLE` → `Warning` se mai completata, altrimenti `Success`; `SYNCING` →
  `Info`; `PARTIAL` → `Warning`; altrimenti (`ERROR`, `OFFLINE`) → `Danger`. Un anno che la fonte non ha ancora aperto non e' un
  guasto: nessuno puo' ripararlo e riportarlo in rosso come una sessione scaduta insegna a ignorare il rosso.
- **Glifo** di `FluidSyncIndicator` (26 dp): `notice` → `Info`; `SYNCING` → `FluidSpinner` da 14 dp; `OFFLINE` → nuvola barrata (13 dp);
  `ERROR`/`PARTIAL` → punto esclamativo (13 dp); `IDLE` → un punto (8 dp, 7 se mai completata). **Forma e colore**: dice quale delle quattro
  cose succede anche a chi non distingue i colori. Il nome per TalkBack e' la frase di `FluidLabels.sync`.
- **Nella barra e' un tasto di vetro** (3.0): `glassControlSurface` sul fondale della barra con il tono nel materiale al **55%**
  (`SyncIndicatorToneShare`: il vetro resta vetro, il glifo sopra resta leggibile). Fuori da una barra, o senza `RenderEffect` (API <31), e' il
  cerchio colorato di prima.
- **`FluidSyncNotice`**: non disegna niente se `noticeMessage()` e' nullo (`SYNCING`, oppure `message` vuoto). Il titolo e' `notice`
  (o «Nessuna connessione» / «Aggiornamento non riuscito» / «Dati non disponibili»); il **retry compare solo per i fallimenti che
  ritentare puo' risolvere** (`PARTIAL`, `ERROR`, `OFFLINE`) **e solo se passi `onRetry`**. Con `notice` si dice la frase della fonte piu' una guida su cosa
  si puo' fare («ritentare darebbe la stessa risposta»).
- **`FluidSyncAction`**: il dettaglio si apre come **foglio di vetro dentro la composizione** (`FluidGlassModalPortal`, variante a
  `item`: il pannello esce intero col testo che aveva) **se c'e' un `FluidGlassModalHost` alla radice**; senza, il foglio di piattaforma
  (`FluidSheet`, deprecato). L'app non aggiunge niente per l'uno o l'altro: aggiornare l'engine basta. Il dettaglio dice quando, il
  messaggio, le sezioni non arrivate e «Riprova adesso».
- **`SyncStatus.lastSyncLabel(now, strings)`**: `SYNCING` → «in corso»; mai → «Mai aggiornato»; sotto il minuto «adesso»; sotto l'ora «N min fa»;
  sotto il giorno «N h fa»; poi la data `dd/MM HH:mm` nel fuso del telefono. Le frasi vengono da `FluidSyncStrings` (vedi
  [impostazioni.md](impostazioni.md)).

**Trappole.**

- **`notice` non basta per `FluidSyncNotice`**: serve anche `message` (la card disegna `notice` come titolo e `message` come corpo): con solo
  `notice` l'indicatore dice «info» e la card non compare. Controlla con `noticeMessage()`: esiste apposta, perche' un item che non
  disegna niente prende comunque la sua parte di spaziatura dell'arrangiamento (un buco in cima alla pagina senza spiegazione).
- Due frasi di `FluidSyncStrings` (`noticeGuidance`, `yearNotOpen`) parlano del dominio dell'app da cui l'engine e' nato: un'app con
  un'altra fonte le riscrive.
- `FluidSyncAction` come tasto non e' dentro un `fluidPressable` di vetro se non c'e' `LocalGlassBackdrop`: fuori da una barra e'
  un cerchio pieno che cresce (`ExpandRound`).

### Piccoli pezzi

| componente | cosa fa e cosa non dice il nome |
|---|---|
| `FluidStatusBadge(label, tone)` | pillola `FluidCapsuleShape`, `labelMedium` SemiBold, padding 10×4, **una riga**. Non e' un chip disabilitato: il chip e' un controllo e uno spento e' disegnato per sembrare non disponibile, segnale sbagliato per un'etichetta mai pensata per essere toccata |
| `FluidMetricTile(label, value, detail, tone, onClick, glass)` | etichetta in maiuscolo + valore `headlineSmall` + dettaglio; contenitore del **tono** (su vetro al 34%, perche' una tessera `Warning` resti riconoscibile e non diventi lo stesso pannello delle vicine); angolo `Card`; con `onClick` scala 0,968; l'altezza anima con la molla |
| `FluidStatChip(label, value)` | etichetta in maiuscolo nel colore primario + valore `titleLarge`, su `surfaceContainerHigh`, angolo `Card` (non e' un chip interattivo: nome storico) |
| `FluidEditorialCard(color, contentColor)` | **un riempimento, non una card**: niente elevazione (e' un'idea di Material: un'ombra sotto ogni blocco e una tinta per profondita' fanno sembrare le schermate una pila di lastre); angolo `Group`, padding 20, 12 dp fra i figli; fornisce `LocalContentColor` |
| `FluidMiniChart(points, color, fillAlpha, threshold)` | grafico a linea in un `Canvas` **alto 136 dp** (il `modifier` del chiamante vince perche' e' applicato dopo); cinque righe guida, linea 2,5 dp, punti da 4 dp, soglia opzionale; **la scala parte da 0 e arriva ad almeno 1** (`max(maxPoint, 1)`): una serie in 0,2…0,4 appare piatta in basso |
| `FluidSectionTitle(eyebrow, title)` / `FluidAccentLabel(text)` | `eyebrow` maiuscolo in accento (`labelLarge` Bold) sopra un titolo `titleLarge`; `FluidAccentLabel` e' solo l'etichetta. Apre una sezione *di pagina*; per la didascalia quieta sopra un gruppo e' `FluidSectionHeader` (famiglia contenuto) |
| `FluidPillTabs(options, selected, onSelect)` | **e' un `FluidSegmentedControl`** con le etichette uguali alle opzioni: il nome resta perche' e' quello che le app chiamano. Era una pastiglia con un'ombra sotto, la sola superficie a fingere il rilievo; ora il segmento scelto e' *visto attraverso* il vetro ed e' trascinabile |
| `FluidEmptyState(title, detail, illustration)` | dice **perche'** non c'e' niente e cosa si puo' fare. Senza illustrazione: allineato a sinistra come una riga; con (3.0): colonna centrata, 24 dp di aria, l'illustrazione in `onSurfaceVariant` e **muta per TalkBack** (decorazione). L'engine non puo' decidere cosa disegnare, ma da' un posto |
| `FluidLoading(color)` | un `FluidSpinner` da 24 dp |
| `FluidScreenSurface` (in `FluidTheme.kt`) | una `Surface` col colore `background` del tema: lo sfondo della finestra |

### Deprecati (restano per tutta la 3.x)

| nome vecchio | cosa e' adesso |
|---|---|
| `FluidTopHeader` | ancora una `LargeTopAppBar` di Material, senza vetro e col titolo che si ripiega nella barra invece che nella pagina. **Non delega.** → `FluidScreen`, o `FluidCollapsingTitle` + `FluidCollapsingTopBar` |
| `FluidQuickAction(label, onClick)` | delega a `FluidChip(selected = false)`: pillola quieta alta ≥ 48 dp, una riga sola (una `AssistChip` ne disegnava 32 in un bersaglio da 48). Per un'azione che deve spiccare: `FluidButton(style = Tinted)` |
| `FluidHeroCard` | **non delega**: una `Surface` col `MaterialTheme.shapes.extraLarge` (circolare) e un'icona di default da laureato (un'idea di dominio che l'engine non dovrebbe avere). → `FluidHero`, o `FluidCard(highlighted = true)` |
| `FluidListItem(title, subtitle, supporting, trailing)` | delega a `FluidListRow`: il vecchio `subtitle` e' l'`eyebrow`, `supporting` e' il `subtitle`, `trailing` e' il `badge` (e **sostituisce** la chevron: `disclosure = trailing == null`) |

## Cosa e' verificato dai test

- `FluidThemeColorSchemeTest.kt` (la classe dentro si chiama `AppThemeColorSchemeTest`): il colore dinamico conserva **ogni** coppia di
  sistema (le tre famiglie) e ricostruisce solo le superfici; le palette fisse tingono i contenitori e tengono **≥ 4,5:1** fra contenuto
  e sfondo; con il dinamico spento si ricade sul marchio; un preset **senza poli** tiene la derivazione storica *al bit*; un viola con
  poli tiene le tre famiglie distanti (distanza per canale, non solo disuguaglianza); un preset si risolve nella lista **iniettata**; il
  modo `AMOLED` ha lo sfondo `Color.Black`.
- `FluidListRowLabelsTest`: dove stanno le etichette di una riga (accanto, sotto, la soglia a un quarto con lo spazio compreso, etichette piu'
  larghe della riga sempre sotto).
- `FluidMatrixTest` (sample, Robolectric): una riga che apre qualcosa e' **un nodo** con nome, ruolo e stato; una senza `onClick` e'
  un'etichetta, non un pulsante.
- `FluidHostContentColorTest` (sample): il testo nei pannelli dei modali e nelle card ha il colore della superficie.
- `FluidSystemContrastTest`, `FluidVividColorsTest`, `FluidContrastTest`, `FluidFillContrastTest`: contrasto di sistema, coppie leggibili
  agli estremi dei gradienti, riempimenti ≥ 3:1.
- **Non verificato dai test**: com'e' il vetro (gli host dei test non hanno fondale), l'angolo continuo a occhio, il tracking dei caratteri, le
  barre di sistema su un telefono vero. `docs/10-coerenza.md` tiene le celle visive a «?» finche' qualcuno non le guarda in mano.

## Dove guardare

| percorso | cosa c'e' dentro |
|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/theme/FluidTheme.kt` | `FluidTheme`, `AccentPreset`, `AccentPoles`, `fluidAccentPresets`, `FluidDefaultBrand`, la derivazione (`resolveFluidColorScheme`), `fluidColorScheme`, `FluidScreenSurface` |
| `…/ui/theme/FluidComponents.kt` | `FluidTone`, card, gruppo/riga/divisore, piastrella, messaggio, sincronizzazione, pezzi piccoli e deprecati |
| `…/ui/theme/FluidListRowHeadline.kt` | il titolo di una riga con le sue etichette (interno: la regola del quarto) |
| `…/ui/theme/Motion.kt` | `FluidMotionScheme` |
| `…/ui/theme/RouteMotion.kt` | (pagine) `FluidRouteMotion`, `FluidRouteMotionHost`, `MotionOrigin`, `RouteMotionSignals`: citato, non descritto qui |
| `…/ui/fluid/FluidShape.kt` | `ContinuousCornerShape`, `FluidCapsuleShape`, `FluidRadius` e il costruttore del path continuo |
| `…/ui/fluid/FluidType.kt` | `FluidFontFamily`, `FluidDisplayFontFamily`, `fluidTypography`, `FluidTextStyles`, il tracking |
| `engine-foundation/…/EngineSettings.kt` | `EngineSettings`, `ThemeMode`, `AccentMode` |
| `engine-storage/…/EngineStore.kt` | `EngineSettingsStore` |
| `sample/…/GalleryActivity.kt`, `SettingsTab.kt` | il tema alla radice e la pagina che lo cambia dal vivo |
| `../api/tema.md` | tutte le firme di questa scheda |
