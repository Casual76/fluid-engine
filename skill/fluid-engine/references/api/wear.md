# API · engine-wear

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-wear/wear/ambient/FluidAmbient.kt`

- `class FluidAmbientState internal constructor()` — Whether the watch is in ambient (always-on) mode, and what that mode asks of the screen.
  - `var isAmbient: Boolean by mutableStateOf(false)`
  - `var burnInProtectionRequired: Boolean by mutableStateOf(false)` — The panel asks for lit pixels to move now and then; see [fluidBurnInShift].
  - `var lowBitAmbient: Boolean by mutableStateOf(false)` — The panel can only show a few colours in ambient: draw outlines, no images or gradients.
  - `var updateTick: Long by mutableLongStateOf(0L)`
  - `companion object`
    - `fun interactive(): FluidAmbientState` — A state that is never ambient: previews, tests, phone-sized hosts.
    - `fun preview(isAmbient: Boolean, burnInProtectionRequired: Boolean = false, lowBitAmbient: Boolean = false): FluidAmbientState` — A fixed state for previews and screenshot tests, which have no activity to observe.
- `val LocalFluidWearAmbient` — The ambient state of the screen this composition is on. Never ambient unless provided.
- `@Composable fun rememberFluidAmbientState(activity: ComponentActivity): FluidAmbientState` — Observes [activity]'s ambient mode for the lifetime of the composition.
- `fun Modifier.fluidBurnInShift(state: FluidAmbientState, reach: Dp = BurnInReach): Modifier` — Moves what it is applied to by a few pixels on every ambient refresh, when the panel asks for it.
- `val BurnInReach: Dp` — How far [fluidBurnInShift] moves at most, on each axis.

## `engine-wear/wear/components/FluidArcRow.kt`

- `@Composable fun FluidArcRow(modifier: Modifier = Modifier, radiusFraction: Float = DefaultArcRadiusFraction, centerAngle: Float = 90f, spacing: Float = 45f, edgeClearance: Dp? = null, content: @Composable () -> Unit)` — Lays its children out along an arc of the round screen, left to right.
- `const val DefaultArcRadiusFraction` — Where the bottom row of a player sits: inside the bezel, clear of the transport.

## `engine-wear/wear/components/FluidEdgeLevelArc.kt`

- `@Composable fun FluidEdgeLevelArc(level: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha…, startAngle: Float = LevelStartAngle, sweepAngle: Float = LevelSweepAngle, stroke: Dp = FluidWearDimens.EdgeRingStroke, glow: Dp = FluidWearDimens.EdgeRingGlow)` — A level — the volume, a brightness — drawn on the edge of a round screen while it changes.

## `engine-wear/wear/components/FluidEdgeProgressRing.kt`

- `@Composable fun FluidEdgeProgressRing(positionMs: () -> Long, durationMs: Long, running: Boolean, modifier: Modifier = Modifier, ambient: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha…, stroke: Dp = FluidWearDimens.RingStroke, inset: Dp = FluidWearDimens.RingInset, clearTop: Dp = 0.dp, glow: Dp = 0.dp, head: Boolean = false)` — How far a song has gone, drawn around the edge of a round screen.
- `@Composable fun FluidEdgeGlowRing(positionMs: () -> Long, durationMs: Long, running: Boolean, modifier: Modifier = Modifier, ambient: Boolean = false, color: Color = MaterialTheme.colorScheme.primary, clearTop: Dp = 0.dp)` — [FluidEdgeProgressRing] as a cover screen wants it: on the very edge of the glass, thin, with a halo that grows inwards as the song fills and a lit head, leaving room for a clock at twelve.

## `engine-wear/wear/components/FluidRotary.kt`

- `@Composable fun Modifier.fluidRotarySteps(onSteps: (Int) -> Unit, enabled: Boolean = true): Modifier` — Turns the bezel or the crown into steps.

## `engine-wear/wear/components/FluidWearList.kt`

- `@Composable fun FluidWearList(title: String?, modifier: Modifier = Modifier, content: FluidWearListScope.() -> Unit)` — Una schermata a elenco per l'orologio: una testata, le righe, la ghiera che le scorre, l'indicatore sul bordo destro e l'ora che si nasconde mentre si scorre — il modo di fare di Wear (la libreria di Spotify) in una chiamata sola.
- `class FluidWearListScope internal constructor(private val inner: TransformingLazyColumnScope, private val spec: TransformationSpec)` — Lo scope della colonna con ogni elemento dentro la trasformazione dello schermo tondo: la parte del contenitore (larghezza e posizione) fuori, quella del contenuto (scala e dissolvenza) dentro, come annidano i pulsanti di Wear.
  - `fun item(key: Any? = null, content: @Composable TransformingLazyColumnItemScope.() -> Unit)`
  - `fun <T> items(items: List<T>, key: ((item: T) -> Any)? = null, itemContent: @Composable TransformingLazyColumnItemScope.(item: T) -> Unit)`
- `fun FluidWearListScope.noticeItem(text: String)` — Una riga di testo quieto in un elenco: sta caricando, e' vuoto, o dice perche' manca qualcosa.
- `fun FluidWearListScope.loadingItem(text: String)` — Una riga di testo quieto con un cerchio che gira accanto: qualcosa sta arrivando e non c'e' ancora niente da mostrare.
- `fun FluidWearListScope.failedItem(text: String, retryText: String, onRetry: () -> Unit)` — Perche' non e' arrivato niente, e un modo di chiedere di nuovo.
- `fun FluidWearListScope.staleItem(text: String, retryText: String, onRetry: () -> Unit)` — Un avviso sopra un elenco che mostra quello che l'orologio si e' tenuto, perche' chiedere di nuovo al telefono non e' riuscito: i dati ci sono, sono solo vecchi, e **nasconderli dietro un errore** sarebbe peggio che mostrarli datati.
- `sealed interface FluidWearListStatus` — Cosa sta succedendo a un elenco che l'orologio riempie chiedendolo al telefono.
  - `data object Ready : FluidWearListStatus` — Niente da dire: l'elenco e' quello che e'.
  - `data class Loading(val text: String) : FluidWearListStatus` — Sta arrivando. Con elementi gia' in mano non li copre: la riga sta in coda.
  - `data class Notice(val text: String) : FluidWearListStatus` — Una frase che spiega perche' non c'e' niente (non e' un errore: vuoto, o non ancora).
  - `data class Failed(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListStatus` — Chiedere non e' riuscito. [onRetry] riprova.
- `sealed interface FluidWearListPart` — Un pezzo dell'elenco, nell'ordine in cui si disegna.
  - `data class Notice(val text: String) : FluidWearListPart` — Una frase quieta.
  - `data class Loading(val text: String) : FluidWearListPart` — Una frase con un cerchio che gira.
  - `data class Failed(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListPart` — Un errore senza niente sotto: la frase e il tasto per riprovare.
  - `data class Stale(val text: String, val retryText: String, val onRetry: () -> Unit) : FluidWearListPart` — Un errore sopra gli elementi che l'orologio si era tenuto.
  - `data object Items : FluidWearListPart` — Gli elementi.
- `fun fluidWearListPlan(status: FluidWearListStatus, itemCount: Int): List<FluidWearListPart>` — Cosa disegnare, e in che ordine, per [status] con [itemCount] elementi in mano.
- `fun <T> FluidWearListScope.statusItems(status: FluidWearListStatus, items: List<T>, key: ((item: T) -> Any)? = null, itemContent: @Composable TransformingLazyColumnItemScope.(item: T) -> Unit)` — Disegna [items] con i loro stati, secondo [fluidWearListPlan]: la riga giusta nel posto giusto, senza che ogni schermata riscriva il `when`.

## `engine-wear/wear/components/FluidWearRows.kt`

- `@Composable fun FluidWearListRow(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, leading: (@Composable () -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null, selected: Boolean = false)` — A row of a watch list: a cover or an icon, a title, maybe a line under it.
- `@Composable fun FluidWearPill(onClick: () -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = rememberEmptyGlassBackdrop(), label: String? = null, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, icon: @Composable () -> Unit)` — A pill of glass with an icon and an optional label: the Search and Library buttons at the top of a watch home.

## `engine-wear/wear/components/FluidWearToast.kt`

- `@Composable fun FluidWearToast(message: String?, modifier: Modifier = Modifier, icon: ImageVector? = null)` — A short notice in the middle of the screen: something the person asked for did not happen.

## `engine-wear/wear/glass/FluidGlassBadge.kt`

- `@Composable fun FluidGlassBadge(backdrop: GlassBackdropState, modifier: Modifier = Modifier, size: Dp = FluidWearDimens.DiscLarge, contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable BoxScope.() -> Unit)` — A disc of glass that is not a button: a sign that something just happened.

## `engine-wear/wear/glass/FluidGlassBurst.kt`

- `class FluidGlassBurstState internal constructor(private val scope: CoroutineScope, private val reducedMotion: Boolean)` — A small burst of light from where a glass control was pressed.
  - `val value: Float get()` — 0 at the moment of the press, 1 when the burst has faded out.
  - `val isAnimating: Boolean get()` — True while a burst is on screen.
  - `fun fire(at: Offset? = null)`
- `@Composable fun rememberFluidGlassBurstState(): FluidGlassBurstState`
- `fun Modifier.fluidGlassBurst(state: FluidGlassBurstState, onDarkSurface: Boolean = true, enabled: Boolean = true): Modifier` — Draws [state]'s burst over this control's content.

## `engine-wear/wear/glass/FluidGlassCapsule.kt`

- `@Composable fun FluidGlassCapsule(backdrop: GlassBackdropState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, tint: GlassTint = FluidWearGlass.tint(), optics: GlassOptics = FluidWearGlass.WideOptics, contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable RowScope.() -> Unit)` — A capsule of glass that carries text: the title of what is playing, a status, a label over a picture.

## `engine-wear/wear/glass/FluidGlassDisc.kt`

- `@Composable fun FluidGlassDisc(onClick: () -> Unit, backdrop: GlassBackdropState, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = FluidWearDimens.DiscMedium, enabled: Boolean = true, selected: Boolean = false, onLongClick: (() -> Unit)? = null, haptic: FluidHapticEvent = FluidHapticEvent.Tap, burst: FluidGlassBurstState? = null, tint: GlassTint = FluidWearGlass.tint(), optics: GlassOptics = FluidWearGlass.Optics, contentColor: Color = MaterialTheme.colorScheme.onSurface, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, content: @Composable BoxScope.() -> Unit)` — A round control made of glass: the transport buttons of a watch player, and any other action that has to be a thumb-press on a moving wrist.

## `engine-wear/wear/glass/FluidGlassTimePill.kt`

- `@Composable fun FluidGlassTimePill(backdrop: GlassBackdropState?, modifier: Modifier = Modifier, timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDef…)` — The time, in a small pane of glass at the top of a screen that is a picture.

## `engine-wear/wear/glass/FluidWearGlass.kt`

- `object FluidWearGlass` — The one glass a watch is made of.
  - `val Optics: GlassOptics` — The lens every pane shares.
  - `val WideOptics: GlassOptics` — [Optics] for a pane wider than it is tall — the title capsule, the clock.
  - `@Composable fun tint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint` — The smoke every pane is filled with.
  - `@Composable fun pageTint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint` — The same glass on a black page — a list, a menu — where there is no picture to smoke.
  - `@Composable fun noticeTint(accent: Color = MaterialTheme.colorScheme.primary): GlassTint` — A notice that can land over anything — a cover, a list, a sheet — and has to be read the moment it does. The page film made nearly opaque: the one pane that should not show through.

## `engine-wear/wear/theme/FluidWearAccent.kt`

- `@Composable fun FluidWearAccent(seed: Color?, content: @Composable () -> Unit)` — Lends [content] an accent of its own — usually the colour of the cover that is playing.
- `fun liftForBlack(color: Color): Color` — Brightens [color] until it stands out on black: a ring, a halo or a lit heart in a near-black cover colour would simply not be there.

## `engine-wear/wear/theme/FluidWearDimens.kt`

- `object FluidWearDimens` — The measures of a round watch screen.
  - `val DiscLarge: Dp` — The main action on a screen: play/pause.
  - `val DiscMedium: Dp` — A secondary action beside it: previous, next.
  - `val DiscSmall: Dp` — A small action on the bottom arc. Still a full touch target.
  - `val DiscArc: Dp` — An action on the bottom arc of a player, sized for a thumb rather than to stay out of the way. 40 dp turned out to be a miss on a real wrist. Since 2.11.0.
  - `val TransportGap: Dp` — Space between the transport discs (previous, play, next). Since 2.11.0.
  - `val ArcEdgeClearance: Dp` — How far the arc's discs keep from the edge of the screen when they hug it. Since 2.11.0.
  - `val IconLarge: Dp` — The glyph in the main disc (play/pause). Since 2.11.0.
  - `val IconMedium: Dp` — The glyph in a secondary or arc disc. Since 2.11.0.
  - `val IconSmall: Dp` — A glyph beside text in a row or a capsule. Since 2.11.0.
  - `val MinTouchTarget: Dp` — The touch target every disc keeps whatever it draws.
  - `val RingStroke: Dp` — The progress ring's stroke.
  - `val RingInset: Dp` — How far the ring sits in from the edge of the screen.
  - `val EdgeRingStroke: Dp` — The stroke of a ring drawn on the very edge of the screen (`FluidEdgeGlowRing`). Since 2.11.0.
  - `val EdgeRingGlow: Dp` — How far that ring's halo reaches in from the edge. Since 2.11.0.
  - `val EdgeRingClockMargin: Dp` — Room the ring leaves on each side of a clock at twelve o'clock. Since 2.11.0.
  - `val TimePillPaddingHorizontal: Dp` — Padding around the time in [dev.antigravity.fluidengine.wear.glass.FluidGlassTimePill]. Since 2.11.0.
  - `val TimePillPaddingVertical: Dp` — See [TimePillPaddingHorizontal]. Since 2.11.0.
  - `val TimePillTop: Dp` — Where the clock pill sits below the top of the screen. Since 2.11.0.
  - `val CapsulePaddingHorizontal: Dp` — Horizontal padding a capsule keeps around its text.
  - `val CapsulePaddingVertical: Dp` — Vertical padding a capsule keeps around its text.
  - `val RowArtwork: Dp` — A list row's cover or icon tile.
  - `val PillHeight: Dp` — Height of a pill button (Search, Library).

## `engine-wear/wear/theme/FluidWearTheme.kt`

- `object FluidWearDefaults` — The settings a watch starts from.
  - `val settings: EngineSettings`
- `@Composable fun FluidWearTheme(brand: AccentPreset = FluidDefaultBrand, settings: EngineSettings = FluidWearDefaults.settings, content: @Composable () -> Unit)` — The Fluid design system on a watch.
- `fun fluidWearColorScheme(phone: PhoneColorScheme): WearColorScheme` — The Wear colour roles, read off the phone's.
- `fun fluidWearTypography(): WearTypography` — Wear's type scale, set in Inter.
- `fun fluidWearShapes(): WearShapes` — Wear's shape slots, in continuous corners.

## `engine-wear/wear/tiles/FluidComplications.kt`

- `object FluidComplications` — Le complicazioni Fluid: i dati di uno stato ([FluidWearStatus]) nella forma che chiede il quadrante.
  - `const val ShortTextMaxChars: Int` — Il limite del testo breve sui quadranti (documentazione di `ShortTextComplicationData`: «up to 7 characters»).
  - `val SupportedTypes: List<ComplicationType>` — I tipi che le complicazioni Fluid sanno dare.
  - `fun dataFor(type: ComplicationType, status: FluidWearStatus, nowMillis: Long, colors: FluidTileColors = fluidTileColors(), tapAction: PendingIntent? = null): ComplicationData` — I dati di [status] per il tipo [type] a [nowMillis], con il tocco [tapAction]. `NoData` per un tipo che non c'è in [SupportedTypes], o se il tipo è l'intervallo e lo stato non ha un avanzamento.
  - `fun accentRamp(colors: FluidTileColors = fluidTileColors()): ColorRamp` — La rampa dell'accento per un intervallo: dall'accento spento all'accento pieno, sfumata.
  - `fun preview(type: ComplicationType, colors: FluidTileColors = fluidTileColors()): ComplicationData` — L'anteprima di un tipo, per il selettore delle complicazioni del quadrante.
- `abstract class FluidStatusComplicationService(private val key: String, private val brand: AccentPreset = FluidDefaultBrand) : SuspendingComplicationDataSourceService()` — Il servizio di una complicazione che mostra lo stato [key] pubblicato dal telefono (`engine-wear-sync`): legge l'archivio locale del ponte, quindi risponde subito e anche ad app chiusa. Si dichiara nel manifest come ogni sorgente di complicazioni (`android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST`, i tipi in `SUPPORTED_TYPES`: `SHORT_TEXT,LONG_TEXT,RANGED_VALUE`, `UPDATE_PERIOD_SECONDS` a 0:…

## `engine-wear/wear/tiles/FluidTile.kt`

- `data class FluidTileContent(val title: String, val value: String, val detail: String? = null, val progress: Float? = null, val action: FluidTileAction? = null, val onClick: ActionBuilders.Action? = null, val stale: Boolean = false, val contentDescription: String? = null)` — Che cosa mostra una Tile Fluid: un titolo piccolo in alto, un **valore** dentro il disco di vetro al centro, una riga sotto, l'**anello sul bordo** per l'avanzamento e un tasto sul bordo in basso.
  - `val spoken: String` — La frase per TalkBack.
  - `companion object`
    - `const val ValueMaxChars: Int` — Oltre questi caratteri il valore scende dal carattere numerale a quello del titolo, per stare nel disco.
    - `const val StaleDetail: String` — La riga di uno stato vecchio.
    - `fun of(status: FluidWearStatus, nowMillis: Long, action: FluidTileAction? = null, onClick: ActionBuilders.Action? = null)` — La Tile di uno stato pubblicato dal telefono ([FluidWearStatus]): titolo, valore, riga sotto, anello. A [nowMillis] oltre [FluidWearStatus.staleAfterMillis] la Tile si spegne.
- `data class FluidTileAction(val label: String, val launch: ComponentName? = null, val id: String = "fluid-tile-action")` — Il tasto sul bordo in basso: [label] corta («Apri», «Chiedi», «Pausa»). [launch] è l'attività che apre (dell'app dell'orologio); `null` ricarica la Tile con l'id [id] (lo legge `requestParams.currentState.lastClickableId`).
  - `fun toAction(): ActionBuilders.Action` — L'azione di ProtoLayout.
- `fun fluidTileLaunch(activity: ComponentName): ActionBuilders.Action` — L'azione che apre l'attività [activity] dalla Tile.
- `object FluidTileDimens` — Le misure della Tile Fluid, in dp e in frazioni dello schermo.
  - `const val DiscFraction: Float` — Il diametro del disco, in frazione della larghezza dello schermo: 0,42 senza riga sotto, 0,36 con. A occhio, 26/11/2026 (sta nello slot principale di `primaryLayout` a 192 dp col tasto sul bordo).
  - `const val DiscFractionWithDetail: Float`
  - `const val RingStroke: Float` — Il tratto dell'anello sul bordo: `FluidWearDimens.RingStroke`, 4 dp.
  - `const val DiscRim: Float` — Il filetto del disco: 0,8 dp, quello del vetro dell'orologio (`FluidWearGlass.Optics.highlightWidth`).
- `fun MaterialScope.fluidTile(content: FluidTileContent, colors: FluidTileColors = fluidTileColors()): LayoutElement` — La Tile Fluid di [content], dentro un `MaterialScope` (quello che `Material3TileService` passa a `tileResponse`, o `materialScope(...)` in una prova). I colori sono [colors]; di serie quelli di [fluidTileColors] col marchio di serie.
- `fun fluidTileEdgeRing(progress: Float, colors: FluidTileColors = fluidTileColors()): LayoutElement` — L'anello sul bordo: la pista intera e l'avanzamento dalle ore dodici in senso orario, a filo del bordo dello schermo (come `FluidEdgeGlowRing`, senza l'alone: ProtoLayout non sfuma). Lo spessore è [FluidTileDimens.RingStroke].

## `engine-wear/wear/tiles/FluidTileColors.kt`

- `class FluidTileColors internal constructor(val scheme: ColorScheme, val accent: LayoutColor, val discFill: LayoutColor, val discRim: LayoutColor, val ringTrack: LayoutColor, val ring: LayoutColor, val onDisc: LayoutColor, val secondary: LayoutColor, val stale: LayoutColor, val argb: FluidTileArgb)` — I colori di una Tile e di una complicazione Fluid, già risolti: ProtoLayout non ha un tema da leggere al momento del disegno (come Glance per i widget del telefono, il layout va al sistema come dati), quindi tutto si decide qui, una volta, dagli stessi token di [dev.antigravity.fluidengine.wear.theme.FluidWearTheme].
- `data class FluidTileArgb(val background: Int, val accent: Int, val accentDim: Int, val onAccent: Int, val discFill: Int, val discRim: Int, val ringTrack: Int, val onDisc: Int, val secondary: Int, val stale: Int)` — I colori di [FluidTileColors] come interi ARGB.
- `fun fluidTileColors(brand: AccentPreset = FluidDefaultBrand, settings: EngineSettings = FluidWearDefaults.settings): FluidTileColors` — I colori Fluid per le Tile e le complicazioni, dal marchio [brand] dell'app. [settings] serve solo per l'accento (marchio o preimpostato): il fondo resta nero (vedi [FluidTileColors]).

## `engine-wear/wear/tiles/FluidTileService.kt`

- `abstract class FluidTileService(private val brand: AccentPreset = FluidDefaultBrand) : Material3TileService(allowDynamicTheme = false, defaultColorScheme = fluidTileColors(brand).scheme)` — Il servizio di una Tile Fluid: l'app scrive solo [content], cioè che cosa mostrare; il disegno (disco di vetro, anello sul bordo, tasto sul bordo, colori dal suo marchio) lo fa [fluidTile].
  - `companion object`
    - `const val DefaultFreshnessMillis: Long` — 15 minuti. A occhio, 26/11/2026: il sistema raggruppa comunque gli aggiornamenti.
- `abstract class FluidStatusTileService(private val key: String, brand: AccentPreset = FluidDefaultBrand, private val action: FluidTileAction? = null, private val waiting: FluidTileContent = FluidTileContent(title = "In attesa", value = "…) : FluidTileService(brand)` — La Tile di uno stato pubblicato dal telefono con `engine-wear-sync` ([FluidWearStatus], chiave [key]): la lezione dopo, le mail non lette, il timer. Legge l'archivio locale del ponte, quindi c'è anche ad app chiusa e col telefono lontano; finché il telefono non ha pubblicato niente dice [waiting].
- `object FluidWearSurfaces` — Le richieste di ridisegno delle superfici dell'orologio, quando un dato cambia.
  - `fun refresh(context: Context, tiles: List<Class<out TileService>> = emptyList(), complications: List<Class<*>> = emptyList())` — Chiede di ridisegnare le Tile [tiles] e tutte le complicazioni dei servizi [complications]. Si chiama da `onStatusChanged` di `FluidWearListenerService`, sull'orologio. Il sistema decide quando (le Tile visibili subito, le altre al prossimo passaggio).
