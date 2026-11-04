<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/wear.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Wear OS (`engine-wear`)

`engine-wear` e' il design system Fluid su un orologio rotondo. Non e' un secondo design system: il vetro, le
molle di `FluidMotion`, le forme continue, i due tagli di Inter e l'aptica sono quelli di `engine-ui`, presi
cosi' come sono. Il modulo aggiunge la meta' che un telefono non ha mai avuto bisogno di avere: un tema
Material 3 **per Wear** ricavato dagli stessi token, **un solo vetro** (`FluidWearGlass`) al posto dei ruoli del
telefono, le misure di uno schermo da 192-240 dp, i componenti che seguono la curva del bordo (anelli, arco
di azioni, elenco trasformato dal cerchio), la ghiera, e l'always-on.

Modello mentale, in due frasi. **Due temi annidati, una sola fonte di colore**: fuori `FluidTheme` (il vetro,
`fluidPressable` e l'aptica leggono da li'), dentro il `MaterialTheme` di Wear (lo leggono `TimeText`,
`ScreenScaffold`, le liste), con lo schema mappato da quello del telefono. **Il vetro costa zero quando niente
si muove**: quello che cambia spesso (anello, ora, testo che scorre) sta fuori dalla sorgente del vetro, e gli
effetti stanno sopra le lastre.

Il modulo e' **opt-in** (vedi «Aggiungerlo al solo orologio») e dipende da `engine-foundation` e `engine-ui`
(`api project(...)` entrambi: chi dipende da `engine-wear` vede anche loro). Qui non c'e' un sample: la galleria
(`sample`) e' un'app da telefono e non monta niente di Wear, quindi gli esempi sotto sono scritti sui sorgenti,
non copiati da una demo (i nomi senza prefisso `Fluid`, come `PlayPauseGlyph`, `Cover`, `CoverTile`, `Icon` di Wear,
sono segnaposto).

**Numeri di versione.** Nei KDoc e nelle note «Dalla 2.11.0» e' il numero che il ramo `claude/sweet-ride-gmzu9g` aveva quando
nacque e **non e' mai stato un rilascio**: quei componenti (`FluidWearGlass`, `FluidWearToast`, `FluidGlassTimePill`, `FluidGlassBadge`,
`FluidEdgeGlowRing`, `FluidEdgeLevelArc`, `FluidWearAccent`, `FluidWearDimens`, `selected` e `edgeClearance`, il `burst` facoltativo) sono
entrati nell'engine con la **3.0.4**. In questa scheda «dalla 3.0.4 (KDoc: 2.11.0)». Un'app ferma a un engine precedente non li ha.
Aggiornando, **cambia l'aspetto** di dischi, capsule e pillole (vetro unico, niente esplosione sui dischi), non l'API.

Altre schede: tutte le firme in [`../api/wear.md`](../api/wear.md); la regola del vetro fermo e i ruoli del
telefono in [`../design-system.md`](../design-system.md) e [`../regole.md`](../regole.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il tema di un'app dell'orologio | `FluidWearTheme(brand)`; le schermate leggono `androidx.wear.compose.material3.MaterialTheme`, non quello del telefono |
| un controllo rotondo di vetro (play, avanti, indietro) | `FluidGlassDisc(onClick, backdrop, contentDescription, size = FluidWearDimens.DiscLarge/DiscMedium/DiscArc)` |
| testo su una copertina o un'immagine (titolo, stato) | `FluidGlassCapsule(backdrop)`; **non** `FluidGlassDisc` (si piega: il testo che si muove non si legge) |
| l'ora sopra una copertina a schermo intero | `FluidGlassTimePill(backdrop)`; il `TimeText` curvo di Wear resta per le liste su nero |
| un segno che compare e svanisce sopra un'immagine (il glifo di play/pausa al tocco) | `FluidGlassBadge(backdrop)` (non e' un bottone) |
| una pillola di vetro in una home su pagina nera (Cerca, Libreria) | `FluidWearPill(onClick)`: ha gia' un backdrop vuoto di serie e `FluidWearGlass.pageTint()` |
| un avviso breve al centro («non e' andata») | `FluidWearToast(message)`; il chiamante decide quando sparisce (`null`) |
| l'avanzamento attorno al bordo | `FluidEdgeProgressRing` (anello inset, sul nero) o `FluidEdgeGlowRing` (sul bordo vero, con alone e testa, per una copertina) |
| un livello che si vede solo mentre cambia (volume, luminosita') | `FluidEdgeLevelArc(level)` su un tratto del bordo |
| una fila di azioni piccole sotto il contenuto | `FluidArcRow` (segue la ghiera) con `edgeClearance = FluidWearDimens.ArcEdgeClearance`; **non** una `Row` (taglia il cerchio con una retta) |
| leggere ghiera/corona come passi interi (volume, pagina) | `Modifier.fluidRotarySteps(onSteps)` |
| un elenco scorrevole con titolo, ghiera e indicatore | `FluidWearList(title) { ... }` con `FluidWearListRow` |
| un elenco che si riempie chiedendo al telefono (carica, vuoto, errore, dati vecchi) | `FluidWearListStatus` + `statusItems(status, items) { ... }` dentro `FluidWearList` |
| un elenco **vuoto** che deve dire perche' | `FluidWearListStatus.Notice("…")`: con `Ready` e zero elementi non si disegna niente |
| l'accento preso da una copertina | `FluidWearAccent(seed)` (alza il colore finche' si legge sul nero: `liftForBlack`) |
| un'altra pillola/pannello con lo stesso vetro | `FluidWearGlass.tint()` / `pageTint()` / `noticeTint()` con `FluidWearGlass.Optics` o `WideOptics`; **mai** `GlassDefaults` del telefono |
| una misura (disco, glifo, anello, padding) | `FluidWearDimens.*`; nessun dp a mano in una schermata dell'orologio |
| l'ambient / always-on | `rememberFluidAmbientState(activity)` + `LocalFluidWearAmbient` + `Modifier.fluidBurnInShift` |
| un'animazione | un nome di `FluidMotion`; mai `tween`/`spring` scritti a mano (c'e' un test che lo vieta) |
| un componente del telefono (tab bar, modali, menu a pannello, `FluidScreen`) | **non** su un orologio: sono per un telefono. Cio' che esiste per Wear sta in questa scheda |

## Aggiungerlo al solo orologio

`engine-wear` e' l'unico modulo (con `engine-ai-ui`) che l'installer **non include** senza essere chiesto per
nome: porta con se' Material 3 per Wear, e un'app per telefono pagherebbe dipendenze e tempo di build per uno
schermo che non ha. Due passi distinti, entrambi necessari:

```powershell
# 1) settings.gradle dell'app: include i moduli (a livello di progetto)
powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot . -Modules engine-foundation,engine-ui,engine-net,engine-update,engine-wear
```

```kotlin
// 2) build.gradle.kts del SOLO modulo dell'orologio (a livello di modulo)
dependencies { implementation(project(":engine-wear")) }
```

- **Include (settings) e dipendenza (modulo) sono cose diverse.** Il blocco `fluid-engine` di `settings.gradle`
  e' del progetto intero: un'app con un modulo telefono e uno orologio include `engine-wear` una volta, e solo
  il modulo dell'orologio lo mette in `implementation`. Il modulo telefono, senza quella riga, non lo compila.
- Le dipendenze fra moduli le chiude lo script (`engine-wear` -> `engine-foundation`, `engine-ui`), quindi
  `-Modules engine-wear` basta; l'elenco lungo sopra e' quello di un'app che usa anche rete e aggiornamento.
- `engine.properties` registra i moduli scelti (`engine.modules=`), e `engine-update.ps1` li rilegge da li':
  aggiornare l'engine **non** perde `engine-wear`. Lo perde chi rilancia `engine-install.ps1` **senza**
  `-Modules`: il default e' "tutti tranne gli opt-in" e il blocco viene riscritto. Sintomo: il modulo
  dell'orologio non trova piu' `:engine-wear`.
- L'installer stampa anche la riga `implementation` da incollare («design system su Wear OS (solo nel modulo
  dell'orologio)»).
- Versioni (`versions.gradle`): Wear Compose Material 3 **1.6.2** (`engine.wearCompose`, vale per `compose-material3`
  e `compose-foundation`) e `androidx.wear:wear` 1.4.0. La 1.7 richiede AGP 9.1 e l'engine non lo usa ancora: non
  alzare la versione a mano. Tutte e tre sono `api`, cioe' arrivano all'app senza dichiararle.
- `minSdk` dell'engine e' almeno 26. Il vetro rifrange da API 33, sfoca da 31, e **sotto 31 dipinge solo
  `GlassTint.fallback`** (un materiale quasi opaco e definito, non rotto): su un orologio con Android 11 o
  precedente (Wear OS 3) un disco e' un disco pieno, e per questo ogni tinta di `FluidWearGlass` ha un `fallback`.

## Differenze con `engine-ui` (cosa cambia davvero)

| | telefono (`engine-ui`) | orologio (`engine-wear`) |
|---|---|---|
| vetro | un ruolo per lavoro (`GlassRole.Bar/Floating/Interactive/Modal/Content`), diversi apposta | **un vetro fumé solo** (`FluidWearGlass`): stessa lente, stessa tinta, per titolo, dischi, arco, ora, volume, avvisi |
| backdrop | `FluidScreen` e le pagine lo registrano da se' | **non c'e' un host**: la schermata crea `rememberGlassBackdrop()`, lo marca con `glassBackdropSource` sull'immagine e lo passa a ogni componente; su pagina nera si passa `rememberEmptyGlassBackdrop()` |
| tinta | film chiaro/scuro del tema | velo fumé nero con **un sesto di accento** (`FluidWearGlass.tint()`); `pageTint()` su nero; `noticeTint()` quasi opaca |
| dispersione | opzionale | spenta (sette campioni per pixel costano sulle GPU degli orologi e su 50 dp si leggono come una frangia di colore) |
| tema | `FluidTheme` e `MaterialTheme` del telefono | `FluidTheme` fuori (`systemBars = false`), `MaterialTheme` di Wear dentro, schema mappato (`fluidWearColorScheme`) |
| tipografia | scala del telefono (derivata iOS) | scala **di Wear** (pensata per uno schermo a un braccio), solo il carattere e' Inter + cifre tabulari |
| impostazioni | quelle dell'app | `FluidWearDefaults.settings`: AMOLED sempre, colore del brand, colore dinamico spento |
| schermo | rettangolo | cerchio: niente barre in basso, niente righe a filo, tutto cio' che e' vicino al bordo segue la curva |
| input | tocco | tocco + **ghiera/corona** (`fluidRotarySteps`) |
| sempre acceso | non esiste | ambient: aggiornamento ~1/minuto, touch spento, burn-in (`FluidAmbientState`) |

## Regola trasversale: il vetro dell'orologio

Su un telefono ogni lastra sceglie il ruolo che le spetta e i ruoli si vedono diversi apposta (una barra e' piu'
quieta di un tasto). Su un orologio stanno tutti insieme sulla stessa copertina da 200 dp, a un centimetro, e la
differenza smette di leggersi come gerarchia e si legge come **due materiali**: una capsula nera sopra il titolo e
tasti grigi sotto. Per questo una schermata dell'orologio usa `FluidWearGlass` **ovunque** e i componenti lo
prendono di serie dalla 3.0.4 (nei KDoc: «2.11.0»).

**`FluidWearGlass.Optics`.** Fra la capsula flottante del telefono e i suoi controlli: `blurScale` 1,6, `refractionHeight`
12 dp, `refractionAmount` 16 dp, `depthEffect = true`, `dispersion = false`, `vibrancy` 1,5, filetto 0,8 dp
al 60% a 90 gradi, nessuna ombra interna, ombra 14 dp al 55%, `pressedDepthBoost` 0,5. **`WideOptics`** e' la stessa
lente con `depthEffect = false`: una cupola va bene su un disco che l'occhio legge come un'unica lente, su una
capsula fa «nuotare» il centro sotto il testo che porta.

**Le tre tinte** (`@Composable`, `remember(accent)`; `accent` e' il `primary` del tema Wear di serie):

| tinta | cosa e' | quando |
|---|---|---|
| `FluidWearGlass.tint()` | nero con un sesto di accento al 42%; `fallback` 0xFF141416 + accento al 95%; filetto bianco 18% | sopra una **copertina/immagine**: deve tenere il testo bianco su qualunque cosa ci sia sotto |
| `FluidWearGlass.pageTint()` | pellicola grigia 0xFF4A4A4E + accento al 36% | su una **pagina nera** (lista, menu): il fumé su nero sparirebbe, quello che rende visibile il vetro li' e' una pellicola accesa |
| `FluidWearGlass.noticeTint()` | 0xFF141416 + accento all'88% | un avviso che puo' atterrare su tutto e va letto subito: l'unica lastra che **non** deve lasciar vedere |

### Il backdrop sull'orologio

Non e' vero che sull'orologio non ci sia backdrop: `FluidGlassDisc`, `FluidGlassCapsule`, `FluidGlassBadge` e
`FluidGlassTimePill` **richiedono** un `GlassBackdropState` e rifrangono davvero cio' che la sorgente registra. Quello
che manca e' l'host che lo fornisce da solo: lo cabli tu.

```kotlin
val backdrop = rememberGlassBackdrop()                       // sulla copertina: rifrange davvero
Box(Modifier.fillMaxSize()) {
  Cover(Modifier.fillMaxSize().glassBackdropSource(backdrop)) // SOLO la copertina, mai l'anello o l'ora
  FluidGlassTimePill(backdrop, Modifier.align(Alignment.TopCenter).padding(top = FluidWearDimens.TimePillTop))
  // ...dischi, capsula...
}
// Su pagina nera non c'e' niente da rifrangere: il vetro tiene filetto, pellicola e luce del tocco.
FluidWearPill(onClick = { }, backdrop = rememberEmptyGlassBackdrop()) { /* icona */ }
```

Il «vetro fumé unico» e' quindi **la tinta e la lente**, non l'assenza di sfondo. Quando non c'e' niente dietro
(`rememberEmptyGlassBackdrop()`), il pannello mantiene bordo, pellicola e tutta la risposta al gesto: e' il caso
di `FluidWearPill` e `FluidWearToast`.

### Il vetro che sta fermo

Una lastra ricattura lo sfondo solo in due casi: lo sfondo si e' ridisegnato (copertina nuova, dissolvenza), o la
lastra si e' spostata **rispetto** allo sfondo (il dito che la piega, un pannello che scorre su una pagina ferma).
Dalla 2.10.0 il secondo caso guarda la posizione *relativa*: prima contava anche quella assoluta, e un trascinamento
di pagina, in cui pagina e lastre si muovono insieme, faceva ricalcolare ogni lastra a ogni fotogramma per
un'immagine identica. Ne seguono tre regole di composizione:

- **quello che cambia spesso sta fuori dalla sorgente**: anello, `TimeText`, titolo che scorre. Dentro la sorgente
  ogni loro fotogramma ricalcola ogni lastra. (Per questo `FluidEdgeProgressRing`, che si ridisegna ogni ~60 ms, non
  va dentro il `Box` marcato con `glassBackdropSource`.)
- **gli effetti stanno sopra il vetro**: `GlassTouchHighlight` e `fluidGlassBurst` ridisegnano un livello piccolo per la
  durata dell'animazione e non chiedono a nessuna lastra di ricatturare;
- un controllo premuto e' vivo esattamente per la durata della sua animazione, e solo lui.

Per verificarlo su un dispositivo: `FluidGlassDiagnostics.enabled = true` in una build di debug, si lascia lo
schermo fermo, e `FluidGlassDiagnostics.totalCaptures` non deve muoversi.

## Tema

```kotlin
FluidWearTheme(brand = AccentPreset("mio", "Mio", Color(0xFF1F9E6E), Color(0xFF2ED8A0))) {
  // schermate dell'orologio: androidx.wear.compose.material3.MaterialTheme
}
```

**Cos'e'.** `FluidTheme(settings, brand, systemBars = false)` fuori e `MaterialTheme` di Wear dentro, con
`fluidWearColorScheme(phone)`, `fluidWearTypography()` e `fluidWearShapes()` (tutte e tre pubbliche, rimappate quando
cambia il tema del telefono). `systemBars = false`: il tema non decide le icone delle barre di sistema, sull'orologio
non ci sono.

**Firma essenziale.**

```kotlin
@Composable fun FluidWearTheme(
  brand: AccentPreset = FluidDefaultBrand,
  settings: EngineSettings = FluidWearDefaults.settings,
  content: @Composable () -> Unit,
)
```

**Comportamento.**

- **`FluidWearDefaults.settings`**: `ThemeMode.AMOLED`, `AccentMode.BRAND`, `dynamicColorEnabled = false`,
  `amoledEnabled = true`, `hapticsEnabled = true`. Un orologio e' vetro nero in una ghiera nera: ogni pixel piu'
  chiaro del nero e' un pixel acceso che la batteria paga a ogni fotogramma. Lo schema Wear risultante ha
  `background = Color.Black` (provato).
- **Schema colori** (`fluidWearColorScheme`): ogni ruolo Wear viene dal ruolo omonimo del telefono; i ruoli che Wear ha
  e il telefono no (`primaryDim`, `secondaryDim`, `tertiaryDim`, `errorDim`) sono lo stesso colore spostato del 28%
  (`DimFraction`) verso lo sfondo: Wear li usa per contenitori premuti e disabilitati, che sul telefono sono
  affidati all'alpha.
- **Tipografia** (`fluidWearTypography`): le **dimensioni restano quelle di Wear**; cambia il carattere (Inter Text) e,
  per gli slot `displayLarge/Medium/Small` e `titleLarge`, Inter Display. I cinque `numeral*` usano Inter Display con
  `fontFeatureSettings = "tnum"` (cifre tabulari): sull'orologio i numeri sono orologi e contatori che non devono
  tremare mentre cambiano.
- **Forme** (`fluidWearShapes`): cinque `ContinuousCornerShape`, mai rettangoli arrotondati: `extraSmall`=`FluidRadius.Small`,
  `small`=`Control`, `medium`=`Card`, `large`=`Group`, `extraLarge`=`Sheet`.
- Il `MaterialTheme` del telefono resta in scope, ma lo deve guardare solo `engine-ui`.

**Trappole.**

- `MaterialTheme.colorScheme` e' quello che hai importato: con `androidx.compose.material3.MaterialTheme` leggi lo schema del
  **telefono**, con `androidx.wear.compose.material3.MaterialTheme` quello di Wear. Nei sorgenti del modulo il telefono e'
  sempre importato con un alias (`PhoneMaterialTheme`); fai lo stesso se ti serve.
- Passare `settings` propri (ad esempio tema chiaro) funziona ma smonta la premessa del modulo (nero, batteria): fallo
  solo sapendolo.
- `FluidWearTheme` **non** installa un `FluidMotionPolicyProvider`: vedi «Movimento ridotto e `FluidMotion` sull'orologio».

**Discrepanza nel KDoc.** `fluidWearShapes` dice che gli slot «salgono di un gradino» sulla scala `FluidRadius` rispetto
al telefono; nel codice la mappatura e' identica a quella di `FluidShapes` in `FluidTheme.kt`. Vale il codice.

**Versione.** 2.10.0 (tema, schema, tipografia, forme); le misure, `FluidWearGlass` e `FluidWearAccent` dalla 3.0.4 (KDoc: 2.11.0).

## `FluidWearAccent` e `liftForBlack`

**Cos'e'.** Presta a un pezzo di schermo un accento suo, di solito il colore della copertina in riproduzione. Prende
il colore, lo alza finche' si legge sul nero (`liftForBlack`) e lo sfuma da un brano al successivo.

```kotlin
FluidWearAccent(seed = coverColor) {   // seed null: tiene l'accento del tema
  FluidEdgeGlowRing(positionMs = { player.position }, durationMs = duration, running = playing)
}
```

**Comportamento.**

- Cambia **tutti e due** i temi: il `MaterialTheme` del telefono (`phone.copy(primary = accent)`, perche' sotto lo
  leggono il vetro e gli stati di selezione di `engine-ui`) e quello di Wear (`primary = accent`, `primaryDim` =
  accento spostato del 28% verso lo sfondo). **Cambia solo `primary`**: `primaryContainer` e gli altri ruoli restano
  quelli del brand.
- Il tema del telefono viene ricostruito con la sua tipografia, le sue forme e `FluidMotionScheme` (3.0), cosi' un
  controllo Material dentro si muove con le molle dell'engine e non con quelle di default di Material.
- `liftForBlack(color)`: se la luminanza e' sotto 0,22 (`AccentFloor`) il colore viene mescolato col bianco di
  `(0,22 - luminanza) * 2,2` (limitato a 0..1) e reso opaco; da 0,22 in su torna **invariato** (alpha compreso). La
  tinta resta nella stessa famiglia (un rosso scurissimo resta il canale piu' forte: provato).
- La dissolvenza dura 600 ms con `FluidMotion.color` (era un `tween` prima della 3.0).
- **`FluidWearGlass.tint()` legge il `primary` Wear**: dentro `FluidWearAccent` i pannelli prendono il sesto di
  accento della copertina. Mentre l'accento sfuma `tint()` produce una `GlassTint` nuova a ogni fotogramma (la
  `remember` e' sull'accento), quindi la lastra aggiorna il velo per quei 600 ms: e' atteso, non e' un difetto.

**Non usarlo quando.** Per colorare un solo componente: passa `color = …` al componente (anelli) o `tint = …` (vetro).

## Misure: `FluidWearDimens`

Una schermata non scrive mai un dp a mano. Sono dp come sul telefono, ma uno schermo da 192-240 dp visto a un
braccio di distanza ha numeri suoi: un controllo primario deve essere una pressione comoda di pollice su un polso
che si muove, e tutto cio' che sta vicino al bordo e' tagliato dalla ghiera a meno che non ne segua la curva.

| costante | dp | per |
|---|---|---|
| `DiscLarge` | 64 | l'azione principale (play/pausa); e' anche la misura di `FluidGlassBadge` |
| `DiscMedium` | 48 | azione secondaria accanto (indietro, avanti); default di `FluidGlassDisc` |
| `DiscSmall` | 40 | piccola azione sull'arco; **il layout riserva comunque `MinTouchTarget`** |
| `DiscArc` | 50 | azione sull'arco in basso, per un pollice (a 40 dp si sbagliava su un polso vero) |
| `TransportGap` | 8 | spazio fra indietro, play, avanti |
| `ArcEdgeClearance` | 6 | quanto i dischi dell'arco stanno dal bordo quando lo seguono |
| `IconLarge` / `IconMedium` / `IconSmall` | 30 / 24 / 18 | glifo del disco grande / di uno medio o d'arco / accanto al testo |
| `MinTouchTarget` | 48 | l'area che ogni disco riserva qualunque cosa disegni (il minimo di Wear) |
| `RingStroke` / `RingInset` | 4 / 3 | tratto dell'anello e quanto sta dentro il bordo |
| `EdgeRingStroke` / `EdgeRingGlow` | 3 / 18 | tratto dell'anello sul bordo vero e quanto arriva il suo alone |
| `EdgeRingClockMargin` | 6 | respiro che l'anello lascia ai lati di un'ora a ore dodici |
| `TimePillPaddingHorizontal` / `Vertical` / `Top` | 10 / 3 / 6 | imbottitura dell'ora e distanza dalla cima |
| `CapsulePaddingHorizontal` / `Vertical` | 14 / 6 | imbottitura di una capsula |
| `RowArtwork` | 40 | la piastrella di copertina/icona di una riga |
| `PillHeight` | 44 | altezza di una pillola (Cerca, Libreria) |

Dalla 3.0.4 (KDoc: 2.11.0), salvo `DiscLarge/Medium/Small`, `MinTouchTarget`, `Ring*`, `Capsule*`, `RowArtwork`, `PillHeight`, che sono della 2.10.0.

## `FluidGlassDisc`

**Cos'e'.** Il controllo rotondo di vetro: i tasti del trasporto di un player e ogni azione che deve essere una pressione
di pollice su un polso in movimento. E' il `glassControlSurface` del telefono in un cerchio, fatto di `FluidWearGlass`:
rifrange, si piega verso il dito, si gonfia mentre e' tenuto, si accende dove e' toccato.

**Firma essenziale.**

```kotlin
@Composable fun FluidGlassDisc(
  onClick: () -> Unit,
  backdrop: GlassBackdropState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = FluidWearDimens.DiscMedium,
  enabled: Boolean = true,
  selected: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  haptic: FluidHapticEvent = FluidHapticEvent.Tap,
  burst: FluidGlassBurstState? = null,        // nessuna esplosione di serie (dalla 3.0.4)
  tint: GlassTint = FluidWearGlass.tint(),
  optics: GlassOptics = FluidWearGlass.Optics,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  content: @Composable BoxScope.() -> Unit,
)
```

Tutti i parametri: [`../api/wear.md`](../api/wear.md).

**Uso.**

```kotlin
FluidGlassDisc(
  onClick = onTogglePlay,
  backdrop = backdrop,
  contentDescription = if (playing) "Pausa" else "Riproduci",
  size = FluidWearDimens.DiscLarge,
  haptic = FluidHapticEvent.Confirm,        // play/pausa vuole Confirm; gli altri il Tap di serie
) { Icon(PlayPauseGlyph, contentDescription = null, modifier = Modifier.size(FluidWearDimens.IconLarge)) }
```

**Comportamento.**

- **Una pressione sola** per il vetro e per il click: un solo `FluidPressState` (`FluidPressFeedback.Expand`) condiviso
  fra `glassControlSurface` e `fluidPressable`, come ogni tasto di vetro del telefono. Dalla 3.0 e' la pressione di
  LibrePods: **premuto al contatto** (non piu' dopo 100 ms come nella 2.12) e **tiene il dito** (`HoldsFinger`): il dito che
  scende sul disco e' suo fino al sollevamento, quindi la lista sotto non scorre; per scorrere si parte **fuori** dai dischi.
  Il click scatta se si solleva sul disco piu' un margine di 16 dp, anche dopo un trascinamento.
- **`grip = FluidPressGrip.YieldsToScroll`** per un disco dentro un `HorizontalPager` (i comandi di un player sopra un
  pager): li' non c'e' un «fuori» da cui far partire il cambio di pagina, e il disco **cede**. Dalla 3.0.
- **Layout**: il disco occupa `max(size, MinTouchTarget)` (quindi un `DiscSmall` da 40 riserva 48); il vetro e' disegnato a
  `size`, centrato; la pressione sta sul vetro.
- **Aptica**: contatto e rilascio hanno i loro colpi, il click suona `haptic` (il `Tap` di serie su un disco e'
  `PressClick`; un altro evento lo **sostituisce** e contatto e rilascio restano), il click lungo suona sempre
  `FluidHapticEvent.Threshold`.
- **`burst`**: la sola cosa che il disco non faceva prima della 2.11: fino ad allora ogni disco sparava un `FluidGlassBurst`
  (anello e otto scintille) e su un orologio vero una fila di dischi che esplodono si leggeva come un difetto. Adesso e'
  facoltativo: passa uno stato `rememberFluidGlassBurstState()` solo al controllo che lo merita. Il disco ricorda dove e'
  caduto il dito (un `pointerInput` che non consuma) e lo da' a `fire(at)`. In ambient il burst e' spento
  (`LocalFluidWearAmbient.current.isAmbient`).
- **`enabled = false`**: il vetro resta la stessa lastra (`interactive = false`, nessun gesto) e il glifo si dice
  spento con alpha 0,38.
- **TalkBack**: `Role.Button`; `contentDescription = null` non sovrascrive niente (usalo solo se il contenuto ha gia' la sua
  descrizione).
- **`selected`** passa al vetro (`glassControlSurface(selected)`), che lo rende nel suo stato di selezione.
- **Movimento ridotto**: gonfiore dimezzato, nessuna inclinazione, luce del tocco al 40% (`glassControlSurface`), burst
  senza scintille.

**Trappole.**

- Senza `backdrop` non si compila: su pagina nera passa `rememberEmptyGlassBackdrop()`.
- Un disco in una lista che scorre **blocca** lo scorrimento che parte su di lui: o lo si sposta, o `YieldsToScroll`.
- `tint`/`optics` di serie sono `FluidWearGlass.tint()` e `Optics`; passare quelli del telefono ricrea il difetto «due
  materiali» che la 3.0.4 ha tolto (**cambio d'aspetto, non d'API**).
- `contentColor` arriva ai figli via `LocalContentColor` di **Wear**: un `Icon` del telefono non lo legge.

**Non usarlo quando.** Per testo (usa `FluidGlassCapsule`), per un segno non premibile (`FluidGlassBadge`), per una pillola con
etichetta (`FluidWearPill`).

**Versione.** 2.10.0; pressione condivisa dalla 2.12.0 (con i 100 ms, poi tolti); `grip` e premuto-al-contatto dalla 3.0.0; vetro
unico, `selected`, `burst` facoltativo, `optics`, `tint` dalla 3.0.4 (nati come «2.11.0», vedi «Numeri di versione»).

## `FluidGlassCapsule`

**Cos'e'.** Vetro flottante che porta testo: il titolo di cio' che e' in riproduzione, uno stato, un'etichetta su un'immagine.

```kotlin
FluidGlassCapsule(backdrop, Modifier.widthIn(max = 140.dp), onClick = onOpenQueue) {
  Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
```

**Comportamento.** E' vetro `GlassRole.Floating`, **non control glass**: non si piega e non si gonfia, perche' il testo che si
muove sotto l'occhio non si legge. Se e' premibile (`onClick`/`onLongClick` non null) risponde con la scala di pressione del
design system (`fluidPressable`, role null: **non** annuncia un ruolo a TalkBack). Senza i due callback nessun gesto. Tinta
`FluidWearGlass.tint()` e lente `WideOptics` di serie (dalla 3.0.4; prima era il nero flottante del telefono e capsula e
dischi sotto sembravano due materiali). Imbottitura 14 / 6 dp, contenuto in una `Row` centrata; il colore del contenuto e'
`onSurface` via `LocalContentColor` di Wear.

**Trappole.** La capsula **non limita la propria larghezza** e il testo dentro non va a capo da solo: dai `widthIn(max = …)` alla
capsula e `maxLines`/`overflow` al `Text`, o su un cerchio il titolo esce dal bordo.

**Non usarla quando.** Per un'azione tonda (disco) o per un avviso (`FluidWearToast`, che usa `noticeTint`).

## `FluidGlassBadge`

**Cos'e'.** Un disco di vetro che non e' un bottone: il segno che compare e svanisce (il glifo di play/pausa su una copertina a
schermo intero quando la si tocca). Stessa lente e materiale del disco (`Optics`, `tint()`, ruolo `Interactive`), nessuna
piegatura, nessuna luce del tocco: niente ci aspetta.

```kotlin
AnimatedVisibility(visible = showGlyph) {                    // animalo tu, dal chiamante
  FluidGlassBadge(backdrop, Modifier.align(Alignment.Center)) { Icon(PlayPauseGlyph, null) }
}
```

**Comportamento.** 64 dp (`DiscLarge`) di serie. E' «vivo» solo mentre si muove: lo anima il chiamante (alpha/scala), e
ricattura solo durante l'animazione. `contentColor` come il disco. Non ha semantica propria.

**Versione.** 3.0.4.

## `FluidGlassTimePill`

**Cos'e'.** L'ora in una piccola lastra di vetro in cima a una schermata che e' un'immagine. Il `TimeText` curvo di Wear
va bene su una lista, dove sta sul nero; su una copertina e' un filo bianco su qualunque cosa ci sia a ore dodici, e un anello
che parte da ore dodici ci passa dietro. La pillola mette l'ora sullo stesso vetro del resto e le da' una **larghezza
definita**, per cui l'anello puo' lasciare un varco (`clearTop`).

```kotlin
FluidGlassTimePill(backdrop, Modifier.align(Alignment.TopCenter).padding(top = FluidWearDimens.TimePillTop))
```

**Comportamento.** Testo `labelSmall` di Wear con cifre tabulari, imbottitura 10 / 3 dp, `semantics { heading() }`. L'ora arriva
da `timeSource` (di serie `TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat())`, quello di Wear). Il cambio del minuto
**non fa ricatturare** la lastra: le cifre sono disegnate sopra il vetro, non attraverso. **`backdrop = null`** disegna l'ora da
sola, senza vetro (ma con la stessa imbottitura): e' quello che vuole l'ambient, dove nessun vetro puo' essere acceso. Non si
posiziona da sola: dalle `TimePillTop` (6 dp) in giu', dal chiamante.

**Trappole.** Se la lasci dentro la sorgente del backdrop (il `Box` con `glassBackdropSource`) la lastra campiona se stessa. La
sorgente e' **solo** la copertina.

**Versione.** 3.0.4.

## `FluidGlassBurst`

**Cos'e'.** Un anello di luce che parte dal punto toccato e corre al bordo, con un lampo al contatto e otto scintille. E' **luce, non
vernice**: sul lato scuro e' additiva (`BlendMode.Plus`: la copertina dietro un disco resta una copertina, illuminata), sul chiaro
sottrattiva (non c'e' luce da aggiungere al bianco), per lo stesso motivo per cui `GlassTouchHighlight` si rovescia.

**Firma essenziale.**

```kotlin
@Composable fun rememberFluidGlassBurstState(): FluidGlassBurstState
fun Modifier.fluidGlassBurst(state: FluidGlassBurstState, onDarkSurface: Boolean = true, enabled: Boolean = true): Modifier
// stato: fire(at: Offset? = null), value (0 al tocco, 1 svanito), isAnimating
```

**Comportamento.**

- Disegnato **sopra** il contenuto del controllo (`drawWithContent`), non dentro la catena degli effetti del vetro: sparare un burst
  ridisegna un livello piccolo per qualche centinaio di ms e **non chiede a nessuna lastra di ricatturare**; `isAnimating` dice
  esattamente quando succede.
- `fire(at)` riporta il progresso a 0 e lo anima fino a 1 con `FluidMotion.gentle()` (la molla dolce: un burst deve essere visto
  viaggiare fino al bordo). `at` null: dal centro. Ogni `fire` cambia il seme, cosi' le scintille di due burst di fila non si
  allineano.
- Numeri: anello al 55%, lampo al 35% (sparisce nel primo terzo), scintille all'80% (8, angolo d'oro 137,5 gradi fra un seme e il
  successivo), `fade = (1 - p)^2`.
- **Movimento ridotto**: niente scintille (`withSparks`), l'anello da solo dice ancora «premuto». Lo stato legge la politica
  **alla creazione** (`remember(scope, reducedMotion)`).
- `enabled = false` (da passare in ambient) non disegna niente.
- `onDarkSurface`: prendilo da `GlassDefaults.isDarkSurface()`; decide fra aggiungere e togliere luce.

**Trappole.** Un burst su ogni disco di una fila e' il difetto che la 3.0.4 ha tolto: usalo su un solo controllo.

**Versione.** 2.10.0; facoltativo (spento di serie sul disco) dalla 3.0.4.

## Anelli sul bordo

Tre componenti, un solo linguaggio (tratto, alone, testa luminosa): l'avanzamento e il livello non devono essere due
lingue visive per una stessa idea.

### `FluidEdgeProgressRing` e `FluidEdgeGlowRing`

**Cos'e'.** Quanto e' andato avanti qualcosa (un brano, un timer), disegnato attorno al bordo. `FluidEdgeGlowRing` e' lo stesso
anello come lo vuole una schermata-copertina: sul bordo vero, sottile, con un alone che cresce verso l'interno mentre si riempie e
una testa luminosa, lasciando un varco per l'ora a ore dodici.

**Firma essenziale.**

```kotlin
@Composable fun FluidEdgeProgressRing(
  positionMs: () -> Long, durationMs: Long, running: Boolean,
  modifier: Modifier = Modifier,
  ambient: Boolean = false,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = onSurface (alpha 0,18),
  stroke: Dp = FluidWearDimens.RingStroke,      // 4
  inset: Dp = FluidWearDimens.RingInset,        // 3
  clearTop: Dp = 0.dp,                          // larghezza del varco a ore dodici
  glow: Dp = 0.dp,                              // alone verso l'interno; 0 = nessun alone
  head: Boolean = false,                        // punto luminoso in fondo all'arco
)
@Composable fun FluidEdgeGlowRing(positionMs, durationMs, running, modifier, ambient, color, clearTop)
// = stroke EdgeRingStroke (3), inset 0, glow EdgeRingGlow (18), head = true, traccia al 10%
```

**Uso.**

```kotlin
FluidEdgeGlowRing(
  positionMs = { player.currentPosition },
  durationMs = player.duration,
  running = player.isPlaying,
  clearTop = 60.dp + FluidWearDimens.EdgeRingClockMargin * 2,   // il varco per FluidGlassTimePill
  modifier = Modifier.fillMaxSize(),
)
```

**Comportamento.**

- **Costa solo quello che deve.** L'arco si ridisegna solo quando la sua fine si sposterebbe di mezzo pixel
  (`ringStepMillis`: `durata * 0,5 / circonferenza`, limitato fra 16 ms e 1 s): un brano di tre minuti su ~1476 px sono ~60 ms
  fra un ridisegno e l'altro, e uno in pausa **nessuno**. Il campionamento e' un ciclo in un `LaunchedEffect` che scrive uno
  stato letto solo dal disegno: nessuna ricomposizione. `positionMs` e' chiamato **solo quando il ridisegno e' dovuto**.
- Parte a ore dodici (`RingStartAngle = -90`) in senso orario. `durationMs <= 0` -> progresso 0 e **nessun ciclo** di campionamento (come in pausa o in ambient).
- **`clearTop`**: la larghezza (corda) da lasciare vuota a ore dodici, centrata. L'arco parte dopo il varco e finisce prima;
  la traccia ha estremi tondi quando c'e' un varco (altrimenti sembrerebbe un taglio). Il varco non supera mai mezzo cerchio.
  Aggiungi tu `EdgeRingClockMargin` per lato se l'ora non ha un margine suo.
- **Alone**: lo stesso arco, piu' largo, dipinto con un gradiente radiale centrato nello schermo (segue l'anello esattamente,
  cosa che un blur approssimerebbe a molte volte il costo). Intensita' da 0,42 a 0,85 con il progresso; svanisce nei primi 16
  gradi, mascherato in un layer con un gradiente a spazzata (un passaggio solo: dividere l'inizio in gradini lasciava una
  riga a ogni giuntura). **Costo**: il layer fuori schermo viene creato a ogni ridisegno dell'arco; con `glow = 0` non c'e'.
- **Testa**: cerchio di raggio `0,95 * stroke`, 60% verso il bianco, con un bagliore (`glow * 0,7`) se c'e' alone.
- **`ambient = true`**: disegna **una volta**, sottile (meta' del tratto), senza traccia, senza alone, senza testa, e **non fa
  mai tick**. Per aggiornarlo ogni minuto va ricomposto dal chiamante con una `positionMs` **nuova** (vedi trappole).
- Vive **fuori** dal vetro: i suoi ridisegni non fanno ricatturare nessuna lastra.

**Trappole.**

- Con la riproduzione in **pausa**, un seek o un brano nuovo muovono l'anello solo se `positionMs` e' un'istanza nuova
  (l'effetto e' agganciato all'identita' della lambda: `LaunchedEffect(positionMs, durationMs)`). Una lambda `remember`ata che legge
  uno stato non basta: letto dentro l'effetto non e' osservato. Una lambda che cattura il valore (`{ position }`) cambia a ogni
  ricomposizione e funziona.
- Idem in ambient: leggi `LocalFluidWearAmbient.current.updateTick` in composizione e passa `positionMs = { position }` ricreata.
- Non metterlo dentro la sorgente del backdrop.
- `FluidEdgeGlowRing` non espone `stroke`/`inset`/`glow`/`head`: se li vuoi diversi usa `FluidEdgeProgressRing`.

**Versione.** `FluidEdgeProgressRing` 2.10.0; `clearTop`, `glow`, `head` e `FluidEdgeGlowRing` dalla 3.0.4.

### `FluidEdgeLevelArc`

**Cos'e'.** Un livello (volume, luminosita') disegnato sul bordo mentre cambia: stesso tratto, alone e testa di
`FluidEdgeGlowRing`, su un tratto di bordo. Di serie il quarto centrato a ore tre, dal lato in cui il pollice gira la corona o la
ghiera, riempiendosi dal basso verso l'alto.

```kotlin
if (showVolume) FluidEdgeLevelArc(level = volume)   // 0..1; la comparsa/scomparsa e' del chiamante
```

**Comportamento.** `startAngle = 45` e `sweepAngle = -90` (gradi, orari da ore tre; il segno negativo riempie in senso
antiorario, cioe' verso l'alto sul lato destro). Tratto `EdgeRingStroke` per **1,6** (un livello si legge al volo mentre si
muove: un po' piu' marcato dell'anello), alone `EdgeRingGlow`, testa quando `level > 0`, traccia al 16%. Disegna **il livello
che gli dai**: non anima, non ha stato; se vuoi una transizione la anima il chiamante.

**Non usarlo quando.** Per un avanzamento nel tempo: `FluidEdgeGlowRing`.

**Versione.** 3.0.4.

## `FluidArcRow`

**Cos'e'.** Dispone i figli lungo un arco dello schermo rotondo, da sinistra a destra. Una fila di piccole azioni che segue la
ghiera invece di tagliare il cerchio con una retta, che sprecherebbe il centro o taglierebbe i lati contro il bordo. Ogni figlio e'
centrato sull'arco, quindi elementi di misure diverse restano allineati lungo la curva.

**Firma essenziale.**

```kotlin
@Composable fun FluidArcRow(
  modifier: Modifier = Modifier,
  radiusFraction: Float = DefaultArcRadiusFraction,   // 0,71 del raggio dello schermo
  centerAngle: Float = 90f,                           // 90 = in basso, 270 = in alto (gradi orari da ore tre)
  spacing: Float = 45f,                               // gradi fra figli vicini
  edgeClearance: Dp? = null,                          // se dato, radiusFraction e' ignorata
  content: @Composable () -> Unit,
)
```

**Uso.**

```kotlin
FluidArcRow(Modifier.fillMaxSize(), edgeClearance = FluidWearDimens.ArcEdgeClearance) {
  FluidGlassDisc(onClick = onOutput, backdrop = backdrop, contentDescription = "Uscita", size = FluidWearDimens.DiscArc) { Icon(OutputGlyph, null) }
  FluidGlassDisc(onClick = onBrowse, backdrop = backdrop, contentDescription = "Sfoglia", size = FluidWearDimens.DiscArc) { Icon(BrowseGlyph, null) }
  FluidGlassDisc(onClick = onAdd, backdrop = backdrop, contentDescription = "Aggiungi", size = FluidWearDimens.DiscArc) { Icon(AddGlyph, null) }
}
```

**Comportamento.**

- **Misura**: usa la larghezza e l'altezza **massime** del vincolo e prende come schermo il lato corto: va messa in un
  contenitore grande quanto lo schermo (`fillMaxSize()`), non attorno ai figli. Senza vincolo, larghezza = somma dei figli e altezza = larghezza.
- **Ordine**: il primo figlio e' il piu' a sinistra, sopra e sotto. Sulla meta' bassa l'angolo deve **diminuire** perche' la x cresca
  (135 gradi e' in basso a sinistra, 45 in basso a destra); sulla meta' alta aumenta. La direzione si sceglie dal seno di `centerAngle`: lo
  stesso componente va bene sopra e sotto. Conteggio pari: centrato (due figli a 112,5 e 67,5).
- **`edgeClearance`**: ogni figlio sta con il proprio lato esterno a quella distanza dal bordo, **qualunque sia lo schermo**
  (raggio = raggio schermo - clearance - lato massimo del figlio / 2, mai negativo). Su un 192 dp tiene la fila lontana dal trasporto, su un 240
  dp la fa aderire al bordo; con `radiusFraction` (0,71) la posizione e' invece proporzionale e cambia con lo schermo.
- Con `edgeClearance` il raggio dipende dal lato massimo di **ciascun** figlio: figli di misure diverse non stanno sullo stesso
  cerchio ma ognuno a filo del bordo.
- Calcolo, non misurato: con tre dischi da 50 dp a 45 gradi su uno schermo da 192 dp il raggio e' 65 dp e la distanza fra i centri ~50 dp:
  si toccano. Aumenta `spacing` o usa `DiscSmall` se serve aria.

**Trappole.** Sul 192 dp la fila aderente e il trasporto si avvicinano: guarda lo schermo piccolo, non solo il 240. La x di un
elemento non e' un padding: nessun `offset` aiuta, l'angolo e' l'unico controllo.

**Versione.** 2.10.0; `edgeClearance` dalla 3.0.4.

## `Modifier.fluidRotarySteps`

**Cos'e'.** Trasforma la ghiera o la corona in passi interi con segno. Le due hardware non concordano su cosa sia un evento: la ghiera
del Galaxy Watch (fisica o touch) **scatta**, ogni evento e' un passo qualunque valore in pixel dichiari; la corona e' liscia e
dichiara pixel, spesso una frazione di passo per evento. Quindi la ghiera si conta a eventi e la corona a distanza accumulata, e
da qui escono entrambe come passi interi.

```kotlin
Modifier.fluidRotarySteps(onSteps = { steps -> volume = (volume + steps).coerceIn(0, 15) })   // + = orario
```

**Comportamento.**

- Come distingue: `PackageManager.hasSystemFeature("android.hardware.rotaryencoder.lowres")` (una volta, `remember(context)`).
  Ghiera a scatti: +1 o -1 per evento. Corona: **32 dp di rotazione per passo**, con resto trattenuto fra gli eventi.
- **Cambio di direzione**: scarta cio' che era stato accumulato dall'altra parte (mezzo passo orario e un tocco indietro e' la mano
  che si assesta, non un passo). `NaN`/infiniti/zero sono ignorati.
- Ogni evento che produce almeno un passo suona un **`FluidHapticEvent.Tick`** (uno per evento, non per passo); poi chiama `onSteps` con un
  numero **diverso da zero**.
- L'evento e' **sempre consumato** (`true`), anche quando non e' arrivato un passo intero: nessun antenato scorrevole lo vede.
- Il fuoco segue la gerarchia di Wear (`requestFocusOnHierarchyActive()` + `focusable()`), non un `requestFocus()` all'avvio: in un
  pager la pagina in primo piano prende la ghiera e quelle accanto la lasciano. Il vecchio `requestFocus()` lasciava che la pagina
  vicina, composta in anticipo, la rubasse.
- `enabled = false` ritorna il modificatore invariato (ascolto e fuoco rilasciati).

**Trappole.** Non metterlo sopra un `FluidWearList`/`TransformingLazyColumn`: la lista legge gia' la ghiera da se' e lo
consumeresti tu. Serve su schermate che **non** scorrono (volume, selettore).

**Versione.** 2.10.0; fuoco per gerarchia dalla 2.10.1.

## `FluidWearList`

**Cos'e'.** Una schermata a elenco per l'orologio: una testata, le righe, la ghiera che le scorre, l'indicatore sul bordo destro e
l'ora che si nasconde mentre si scorre, in una chiamata. Nasce da una `WatchList` di un'app di prova, insieme ai quattro stati degli
elenchi riempiti da un telefono lontano.

```kotlin
FluidWearList(title = "Elementi") {
  items(elementi, key = { it.id }) { elemento ->
    FluidWearListRow(title = elemento.nome, subtitle = elemento.dettaglio, onClick = { apri(elemento) })
  }
}
```

**Comportamento.**

- E' un `ScreenScaffold` + `TransformingLazyColumn` di Wear: ghiera, indicatore di scorrimento e `TimeText` sono quelli di Wear (la
  ghiera **non** passa da `fluidRotarySteps`). Il KDoc promette «l'ora che si nasconde mentre si scorre»: il modulo non crea un
  `TimeText`, quindi l'ora c'e' solo se l'app ha il suo scaffold di Wear con il `TimeText` (in Wear M3 vive nell'`AppScaffold`:
  verificalo sull'app).
- **Ogni elemento prende la forma dello schermo tondo** mentre esce: piu' stretto, piu' piccolo e piu' sbiadito avvicinandosi alla
  cima o al fondo del cerchio, cosi' una riga non e' mai tagliata dalla ghiera. Lo scope (`FluidWearListScope`, con `item` e `items`)
  lo applica da solo: la parte del contenitore (larghezza, posizione) fuori, quella del contenuto (scala, dissolvenza) dentro,
  come annidano i pulsanti di Wear.
- **Sfondo opaco di proposito**: una schermata aperta sopra un'altra deve coprire quella di sotto mentre scivola dentro, e
  mostrarla solo dove il dito la scopre: stessa regola delle pagine del telefono (due pagine leggibili insieme sono la cosa che
  fa sembrare un prototipo). Usa `MaterialTheme.colorScheme.background` (nero con i default).
- `title` va in un `ListHeader` (massimo due righe, centrato, ellipsis); `null` toglie la testata.

**Trappole.** Non annidare un secondo elenco scorrevole. Un disco o una pillola dentro una riga hanno `grip = HoldsFinger` di serie e
tengono il dito: dove la pagina deve poter scorrere partendo da loro passa `YieldsToScroll`.

**Versione.** 3.0.0.

### Gli stati di un elenco che si riempie da lontano

Quattro stati e non una `Boolean` e una `String?`: sono quattro storie, e **la stessa condizione vuol dire due cose** secondo se
c'e' qualcosa da mostrare. Un errore su un elenco vuoto e' «non e' arrivato niente» (con un modo di riprovare); su un elenco
pieno e' «ti mostro quello che avevo». `fluidWearListPlan(status, itemCount)` e' quella regola, pura, e `statusItems` la applica.

```kotlin
FluidWearList(title = "Elementi") {
  statusItems(status, elementi, key = { it.id }) { elemento ->
    FluidWearListRow(title = elemento.nome, onClick = { apri(elemento) })
  }
}
// status = FluidWearListStatus.Ready | Loading("Un attimo") | Notice("Nessun elemento") | Failed("Il telefono non risponde", "Riprova", onRetry)
```

| stato | senza elementi | con elementi |
|---|---|---|
| `Ready` | **niente** (schermata vuota) | gli elementi |
| `Loading(text)` | la riga con il cerchio che gira | elementi, **poi** la riga (mai al posto loro: chi ha gia' qualcosa da leggere lo legge) |
| `Notice(text)` | la frase | la frase **sopra**, poi gli elementi |
| `Failed(text, retryText, onRetry)` | la frase + un tasto per riprovare (`FluidWearListPart.Failed`) | un avviso `Stale` **in cima** (testo come titolo, `retryText` come sottotitolo, icona di aggiorna) e gli elementi sotto |

**Perche' `Stale`.** I dati ci sono, sono solo vecchi: **nasconderli dietro un errore** sarebbe peggio che mostrarli datati.

Le righe singole si possono usare anche fuori da `statusItems`: `noticeItem(text)`, `loadingItem(text)` (cerchio da 18 dp +
testo quieto), `failedItem(text, retryText, onRetry)`, `staleItem(text, retryText, onRetry)`. Il «riprova» e' una
`FluidWearListRow` con icona di aggiorna in una piastrella da 40.

**Trappole.** `Ready` con zero elementi disegna **niente**: per dire «vuoto, perche'» usa `Notice`. `Failed` richiede
`onRetry` anche quando non si puo' riprovare (passa una lambda che non fa niente e un `retryText` onesto, oppure usa `Notice`).

## `FluidWearListRow` e `FluidWearPill`

### `FluidWearListRow`

**Cos'e'.** La riga di un elenco dell'orologio: una copertina o un'icona, un titolo, forse una riga sotto. **Senza contenitore**
di proposito: una lista Wear e' una colonna di cose sul nero, e incorniciare ogni riga trasforma un cerchio da 200 dp in una pila
di lastre grigie. La piastrella iniziale porta la forma, cosi' l'occhio trova comunque un bordo.

```kotlin
FluidWearListRow(
  title = "Elemento", subtitle = "Dettaglio",
  onClick = { }, selected = isCurrent,
  leading = { CoverTile() },                        // piastrella da 40 dp, ritagliata a angolo continuo
)
```

**Comportamento.** Titolo `titleMedium` (due righe, ellipsis), sottotitolo `bodySmall` su `onSurfaceVariant` (una riga). `selected`:
il titolo prende l'accento e, **se non c'e' un `trailing`**, compare un segno di spunta (disegnato nel modulo, nessun set di icone).
Con `onClick`/`onLongClick` risponde con la scala di pressione (`fluidPressable`, senza ruolo). `trailing` e' una lambda `RowScope`
che sostituisce la spunta. Imbottitura verticale 6 dp, spazio 10 dp.

**Versione.** 2.10.0; `selected` dalla 3.0.4.

### `FluidWearPill`

**Cos'e'.** Una pillola di vetro con un'icona e, se vuoi, un'etichetta: i pulsanti Cerca e Libreria in cima a una home. E' vetro di
controllo sul `backdrop` che le dai: su pagina nera (**di serie** `rememberEmptyGlassBackdrop()`) mostra bordo, pellicola e luce del
tocco, cioe' il materiale senza niente da piegare; sopra un'immagine rifrange.

```kotlin
FluidWearPill(onClick = onSearch, label = "Cerca") { Icon(SearchGlyph, contentDescription = null) }
```

**Comportamento.** Altezza 44 dp (`PillHeight`), imbottitura orizzontale 16. Tinta `FluidWearGlass.pageTint()` e lente `WideOptics`
(senza dispersione) dalla 3.0.4, non piu' la pellicola di controllo del telefono. Una pressione sola per vetro e click
(`FluidPressFeedback.Expand`): la pillola cresce e si accende sulla stessa linea del tempo in cui il tocco e' confermato, non con il vetro
che si gonfia mentre l'etichetta dentro si restringeva per conto suo. `grip` come il disco (`YieldsToScroll` per una pillola in un pager o in
una riga che scorre).

**Trappole.** **Non ha `contentDescription`**: una pillola solo-icona (senza `label`) e' muta per TalkBack a meno che l'icona non abbia la sua
descrizione. Non dichiara un ruolo.

**Versione.** 2.10.0; pressione condivisa (`Expand`) dalla 2.11.0 dell'engine; `grip` dalla 3.0.0; vetro unico dalla 3.0.4.

## `FluidWearToast`

**Cos'e'.** Un avviso breve al centro dello schermo: qualcosa che la persona aveva chiesto non e' successo. E' la risposta dell'orologio
alla snackbar. La snackbar sta sul bordo basso, e su uno schermo rotondo il bordo basso e' dove c'e' l'arco delle azioni e dove la curva taglia il
testo; un avviso al centro, sul vetro dell'orologio reso quasi opaco, si legge dovunque atterri.

```kotlin
var avviso by remember { mutableStateOf<String?>(null) }
Box(Modifier.fillMaxSize()) {
  Contenuto(onFallito = { avviso = "Non e' andata" })
  FluidWearToast(message = avviso)
}
LaunchedEffect(avviso) { if (avviso != null) { delay(2_500); avviso = null } }   // lo spegne il chiamante
```

**Comportamento.**

- **Non si spegne da solo**: `message = null` lo nasconde, e lo decide il chiamante. Tiene l'ultima frase durante l'uscita (non diventa
  bianco mentre sfuma).
- Riempie tutto lo spazio che gli dai (`fillMaxSize`) e **non consuma tocchi**; la capsula e' al massimo il 78% della larghezza, fino a tre
  righe (`bodySmall`), con imbottitura 14 / 12 dp, e un'icona facoltativa da 18 dp (`icon`).
- Vetro: `noticeTint()`, `WideOptics`, ruolo `Floating`, **backdrop vuoto**: non rifrange niente, e' un velo quasi opaco.
- Entrata: dissolvenza (`FluidMotion.fadeIn()`) + scala da 0,9 (`FluidMotion.snappy()`); uscita: dissolvenza (`FluidMotion.fadeOut()`) + scala a 0,9.
- **Movimento ridotto**: la scala diventa 1 (`fluidWearToastScale`): l'avviso **solo sfuma**. Vale la regola 3.0: la scala e' decorazione.
- **TalkBack**: `liveRegion = Polite`, annunciato quando compare.

**Trappole.** Se non c'e' la `LaunchedEffect` che lo spegne, resta per sempre. E' un posto solo: due avvisi insieme non esistono.

**Versione.** 3.0.4.

## Always-on (ambient)

In ambient lo schermo si aggiorna **circa una volta al minuto**, il touch e' spento e su molti pannelli solo una frazione di pixel puo' restare
accesa a lungo. Una schermata che lo legge nasconde cio' che li' non puo' funzionare (controlli, vetro vivo, animazioni) e tiene cio' che vale la
pena guardare.

```kotlin
val activity = LocalContext.current as ComponentActivity
val ambient = rememberFluidAmbientState(activity)
CompositionLocalProvider(LocalFluidWearAmbient provides ambient) {
  Box(Modifier.fillMaxSize().fluidBurnInShift(ambient)) {
    if (ambient.isAmbient) AmbientFace() else LiveFace()
  }
}
```

**`FluidAmbientState`** (costruttore interno): `isAmbient`, `burnInProtectionRequired` (il pannello chiede che i pixel accesi si muovano ogni
tanto), `lowBitAmbient` (pochi colori in ambient: contorni, niente immagini o gradienti), `updateTick` (un `Long` che aumenta a **ogni** aggiornamento
ambient: entrata, `onUpdateAmbient`, uscita; leggilo in composizione o in disegno per essere invalidato). `FluidAmbientState.interactive()` e' uno stato
mai ambient (anteprime, test, host da telefono); `preview(isAmbient, burnInProtectionRequired, lowBitAmbient)` e' uno stato fisso per anteprime e
screenshot test.

**`rememberFluidAmbientState(activity)`** registra un `AmbientLifecycleObserver` per la vita della composizione, e **registrare l'osservatore e' il modo
in cui un'app dice a Wear OS che supporta l'ambient**: senza, il sistema lascia l'app e mostra il quadrante quando lo schermo si attenua. Il permesso di
`WAKE_LOCK` e' un requisito di `androidx.wear`, non dell'engine: controllalo nella documentazione di Wear.

**`LocalFluidWearAmbient`** e' `staticCompositionLocalOf` con uno stato **mai ambient** di serie: se non lo fornisci, ogni componente crede di essere
sveglio.

**`Modifier.fluidBurnInShift(state, reach = BurnInReach)`** sposta il contenuto di pochi pixel a ogni aggiornamento, **solo** se `isAmbient &&
burnInProtectionRequired`. Il burn-in viene dai *medesimi* pixel accesi per ore: una copertina che cambia a ogni brano e' gia' quasi tutta la
protezione, questa e' il resto, e si accende solo dove il pannello dichiara di averne bisogno. Lo spostamento percorre un quadrato fisso di otto
posizioni (`(0,0) (1,0) (1,1) (0,1) (-1,1) (-1,0) (-1,-1) (0,-1)` per `reach`), ciascuna vicina alla precedente: mai abbastanza grande da sembrare un
difetto. `BurnInReach` = 6 dp; avviene nel **posizionamento** (nessuna ricomposizione) e non cambia la misura: lascia un margine, o il contenuto
puo' uscire dai bordi.

**Cosa l'engine fa da solo e cosa no.** Pochissimo: **solo `FluidGlassDisc` legge `LocalFluidWearAmbient`**, e solo per spegnere il burst. Il vetro,
il toast, le capsule, l'ora **non si nascondono da soli** in ambient, e nessun componente guarda `lowBitAmbient` o `updateTick`. L'anello ha il suo
parametro esplicito (`ambient = true`) e `FluidGlassTimePill` accetta `backdrop = null`. «In ambient niente vetro vivo, niente animazioni» e' una
convenzione che la schermata deve applicare: scegli un'altra faccia (`if (ambient.isAmbient)`) e passa `ambient = ambient.isAmbient` all'anello.

## Movimento ridotto e `FluidMotion` sull'orologio

- **Niente movimento scritto a mano.** I componenti dell'orologio usano nomi di `FluidMotion` (`fadeIn`, `fadeOut`, `snappy`, `gentle`, `color`), e un test (`FluidWearMotionTest`) fa
  fallire la build se un sorgente di `engine-wear` contiene `tween(`, `spring(`, `keyframes(` o `infiniteRepeatable(` fuori da un commento. Chi aggiunge un componente
  usa un token, come sul telefono.
- **Cosa sparisce con la scala di animazione a zero**: l'inclinazione del disco e meta' del suo gonfiore, le scintille del burst, la scala d'ingresso del
  toast (resta la dissolvenza). Il resto (anello, ghiera) non anima per decorazione.
- **Trappola grossa, verificata nel sorgente.** `LocalFluidMotionPolicy` e' una `staticCompositionLocalOf` con **scala 1 di serie**, e **nessun tema dell'engine la fornisce**:
  ne' `FluidTheme` ne' `FluidWearTheme` mettono un `FluidMotionPolicyProvider` (l'unica definizione e' nel suo file). Quindi con la scala di animazione di
  sistema a zero i componenti sopra si muovono **comunque**, a meno che l'app avvolga la radice in `FluidMotionPolicyProvider { ... }` (di serie legge la scala dal
  contesto della coroutine con `rememberFluidMotionPolicy()`). Mettilo subito dentro `FluidWearTheme`.
- `FluidWearAccent` ricostruisce il tema del telefono con `FluidMotionScheme`, cosi' un controllo Material dentro un orologio usa le molle dell'engine.

## Token, costanti, Defaults

| cosa | valore | dove |
|---|---|---|
| `FluidWearDefaults.settings` | AMOLED, brand, colore dinamico spento, aptica accesa | `theme/FluidWearTheme.kt` |
| `FluidWearGlass.Optics` / `WideOptics` | lente unica (vedi sopra) / senza cupola | `glass/FluidWearGlass.kt` |
| `FluidWearGlass.tint()` / `pageTint()` / `noticeTint()` | smoke 0,42 / pellicola 0,36 / 0,88; un sesto di accento (`AccentShare` 0,16); filetto 18% | idem (costanti private) |
| `DefaultArcRadiusFraction` | 0,71 (pubblica, `const`) | `components/FluidArcRow.kt` |
| `BurnInReach` | 6 dp (pubblica) | `ambient/FluidAmbient.kt` |
| `DimFraction` | 0,28, **interna**; vale per i `*Dim` e per `primaryDim` di `FluidWearAccent` | `theme/FluidWearTheme.kt` |
| `AccentFloor` / `LiftGain` | 0,22 / 2,2, private; `AccentFadeMillis` 600 | `theme/FluidWearAccent.kt` |
| passo della ghiera liscia | 32 dp (privata) | `components/FluidRotary.kt` |
| `ringStepMillis` | 16 ms ... 1000 ms (interna) | `components/FluidEdgeProgressRing.kt` |

Le soglie che servono a chi tara (alone 0,42-0,85, varco massimo 180 gradi, `LevelStrokeScale` 1,6, `MaxWidthFraction` 0,78 del toast) sono tutte **private**:
non si tarano dall'app. Se serve cambiarle e' una modifica all'engine.

## Cosa e' verificato dai test

Otto file in `engine-wear/src/test`, tutti senza dispositivo (`returnDefaultValues = true`); **nessun test visivo, nessun test strumentato** del vetro:

- `FluidWearListPlanTest` (8): cosa va in quale riga e in che ordine per ogni stato, e soprattutto «fallito» contro «ho gia' qualcosa» (l'avviso `Stale` tiene gli elementi).
- `FluidEdgeProgressRingTest`: progresso sicuro con durata sconosciuta, sweep, il passo di mezzo pixel (~60 ms per un brano di tre minuti), il limite 16 ms - 1 s,
  il varco (un accordo lungo come il raggio taglia sessanta gradi) e il tetto di mezzo cerchio, l'alone che si illumina.
- `FluidArcRowTest`: l'ordine da sinistra a destra sopra e sotto, conteggio pari centrato, fila vuota, la quota `edgeClearance`.
- `RotaryAccumulatorTest`: la ghiera conta eventi, la corona distanza, il cambio di direzione butta il resto, `NaN` e infiniti ignorati.
- `BurnInOffsetTest`: il passo resta nel raggio, si ripete ogni otto aggiornamenti, tick negativi non rompono.
- `FluidWearColorSchemeTest`: sfondo nero con i default, ruoli presi dal telefono, contrasto >= 4,5 di testo su sfondo, `*Dim` fra il ruolo e il fondo.
- `FluidWearAccentTest`: un colore quasi nero viene alzato finche' si vede (luminanza > 0,15) restando nella famiglia, uno chiaro non si tocca.
- `FluidWearMotionTest`: avviso con movimento ridotto senza scala; nessun `tween`/`spring`/`keyframes`/`infiniteRepeatable` nei sorgenti del modulo.

**Non provato**: il vetro stesso, il costo dell'alone dell'anello, il comportamento con ghiera vera, l'ambient su dispositivo, TalkBack, RTL, testo grande, il
ripiego sotto API 31, e che `FluidMotionPolicy` arrivi ai componenti (vedi la trappola sopra). Lo schermo si giudica su un orologio, non per costruzione.

## Dove guardare

Radice `engine-wear/src/main/kotlin/dev/antigravity/fluidengine/wear/`:

- `theme/FluidWearTheme.kt`: `FluidWearTheme`, `FluidWearDefaults`, `fluidWearColorScheme`, `fluidWearTypography`, `fluidWearShapes`, `DimFraction`.
- `theme/FluidWearAccent.kt`: `FluidWearAccent`, `liftForBlack`. `theme/FluidWearDimens.kt`: tutte le misure.
- `glass/FluidWearGlass.kt`: lente e tinte; `FluidGlassDisc.kt`, `FluidGlassCapsule.kt`, `FluidGlassBadge.kt`, `FluidGlassTimePill.kt`, `FluidGlassBurst.kt`.
- `components/FluidEdgeProgressRing.kt`: l'anello, `FluidEdgeGlowRing`, `drawGlowArc` (alone, testa), e le funzioni pure testate; `FluidEdgeLevelArc.kt`.
- `components/FluidArcRow.kt`: layout e matematica dell'arco. `components/FluidRotary.kt`: `fluidRotarySteps` e `RotaryAccumulator`.
- `components/FluidWearList.kt`: l'elenco, lo scope, gli stati e `fluidWearListPlan`. `FluidWearRows.kt`: `FluidWearListRow`, `FluidWearPill`. `FluidWearToast.kt`.
- `ambient/FluidAmbient.kt`: `FluidAmbientState`, `rememberFluidAmbientState`, `fluidBurnInShift`.
- Build: `engine-wear/build.gradle` (dipendenze `api`), `versions.gradle` (`wearCompose`, `wear`), `tools/engine-install.ps1` (`$OptInModules`).
- Storia: `CHANGELOG.md` (2.10.0 il modulo, 3.0.0 per `grip` e `FluidWearList`, 3.0.4 per i componenti del ramo `sweet-ride`); contesto lungo `docs/09-wear.md`.
