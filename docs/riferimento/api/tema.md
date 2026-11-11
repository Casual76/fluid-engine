<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/tema.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Tema, forma, tipo

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidShape.kt`

- `class ContinuousCornerShape(topStart: CornerSize, topEnd: CornerSize, bottomEnd: CornerSize, bottomStart: CornerSize, smoothing: Float = IosSmoothing) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart)` — A rounded rectangle whose corners are *continuous* rather than circular.
  - `companion object`
    - `const val IosSmoothing: Float` — How far Apple pushes the curvature ramp. Figma's slider calls this 60%.
- `fun ContinuousCornerShape(radius: Dp, smoothing: Float = ContinuousCornerShape.IosSmoothing): ContinuousCornerShape`
- `fun ContinuousCornerShape(topStart: Dp = 0.dp, topEnd: Dp = 0.dp, bottomEnd: Dp = 0.dp, bottomStart: Dp = 0.dp, smoothing: Float = ContinuousCornerShape.IosSmoothing): ContinuousCornerShape`
- `val FluidCapsuleShape: Shape` — A capsule: the radius is always half the shorter side.
- `object FluidRadius` — The radii the app uses.
  - `val Small: Dp`
  - `val Control: Dp`
  - `val Card: Dp`
  - `val Group: Dp`
  - `val Sheet: Dp`

## `engine-ui/ui/fluid/FluidType.kt`

- `val FluidFontFamily: FontFamily` — The text cut, for anything a person reads in a paragraph or a row.
- `val FluidDisplayFontFamily: FontFamily` — The display cut, for titles at 20sp and above.
- `fun fluidTypography(): Typography` — The iOS type scale, mapped onto Material's slots.
- `object FluidTextStyles` — Extra styles that have no Material slot.
  - `val uppercaseCaption: TextStyle`
  - `val numeric: TextStyle`
  - `val largeNumeric: TextStyle`

## `engine-ui/ui/theme/FluidComponents.kt`

- `enum class FluidTone`
- `@Composable fun FluidTopHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, scrollBehavior: TopAppBarScrollBehavior? = null, titleTrailing: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` — **DEPRECATO: È una LargeTopAppBar di Material, senza vetro e col titolo che si ripiega nella barra ** — L'intestazione di Material, rimasta nel design system dal tempo in cui non ce n'era un'altra.
- `@Composable fun FluidAccentLabel(text: String, modifier: Modifier = Modifier)`
- `@Composable fun FluidPillTabs(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)`
- `@Composable fun FluidEditorialCard(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable ColumnScope.() -> Unit)`
- `@Composable fun FluidMiniChart(points: List<Float>, color: Color, modifier: Modifier = Modifier, fillAlpha: Float = 0.28f, threshold: Float? = null)`
- `@Composable fun FluidStatusBadge(label: String, modifier: Modifier = Modifier, tone: FluidTone = FluidTone.Neutral)`
- `@Composable fun FluidStatusBadge(label: String, color: Color, modifier: Modifier = Modifier)` — La pillola di stato in un colore qualunque: quello di un'etichetta scelto dall'utente, di una cartella, di una materia. I sei toni dell'engine non bastano quando il colore e' un dato.
- `@Composable fun FluidMetricTile(label: String, value: String, detail: String, modifier: Modifier = Modifier, tone: FluidTone = FluidTone.Neutral, onClick: (() -> Unit)? = null, animatePress: Boolean = true, glass: Boolean = false)`
- `@Composable fun FluidCard(modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: (() -> Unit)? = null, animateContent: Boolean = true, animatePress: Boolean = true, glass: Boolean = false, enabled: Boolean = true, sampleOnce: Boolean = true, resampleIntervalMillis: Long = 0L, content: @Composable ColumnScope.() -> Unit)`
- `@Composable fun FluidSyncIndicator(status: SyncStatus, modifier: Modifier = Modifier)` — Sync state as a bar control rather than as a mark on the page.
- `fun SyncStatus.noticeMessage(): String?` — The sentence [FluidSyncNotice] would show, or null when there is nothing to say.
- `@Composable fun FluidSyncNotice(status: SyncStatus, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null)` — The one thing the sync knows that the page cannot say for itself, said on the page.
- `@Composable fun FluidSyncAction(status: SyncStatus, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null)` — The sync indicator, and the explanation behind it.
- `@Composable fun FluidInlineMessage(message: String, modifier: Modifier = Modifier, title: String? = null, tone: FluidTone = FluidTone.Info, onDismiss: (() -> Unit)? = null, onClick: (() -> Unit)? = null, action: String? = null, leading: (@Composable () -> Unit)? = null, glass: Boolean = false)` — Un messaggio dentro la pagina: un titolo, una frase e, se serve, qualcosa da toccare.
- `fun SyncStatus.lastSyncLabel(nowEpochMillis: Long = System.currentTimeMillis(), strings: FluidSyncStrings = FluidSyncStrings()): String`
- `@Composable fun FluidQuickAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit)` — **DEPRECATO: Era una AssistChip di Material; adesso è un FluidChip non selezionato. Usa FluidChip ** — Il pulsante a pillola di prima, sotto il nome che le app già chiamano.
- `val FluidGroupOpenEdge: Dp` — Quanto ritaglia un lato aperto di [FluidListGroup], e quanto devono sovrapporsi due pezzi.
- `enum class FluidGroupSegment`
- `@Composable fun FluidListGroup(modifier: Modifier = Modifier, glass: Boolean = false, segment: FluidGroupSegment = FluidGroupSegment.Whole, content: @Composable ColumnScope.() -> Unit)` — A grouped list: rows share one rounded container, the way an inset-grouped table view does.
- `@Composable fun FluidListDivider(modifier: Modifier = Modifier)` — Separator between rows of a group, inset so it starts where the row's text does.
- `@Composable fun FluidListRow(title: String, subtitle: String, modifier: Modifier = Modifier, eyebrow: String? = null, meta: String? = null, tone: FluidTone = FluidTone.Neutral, badge: (@Composable () -> Unit)? = null, labels: (@Composable () -> Unit)? = null, titleMaxLines: Int = Int.MAX_VALUE, leading: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, contextActions: (() -> List<FluidContextAction>)? = null, animatePress: Boolean = true, animateContent: Boolean = false, selected: Boolean = false, disclosure: Boolean = true, leadingTile: Boolean = true, subtitleMaxLines: Int = Int.MAX_VALUE, customActions: List<CustomAccessibilityAction> = emptyList())`
- `@Composable fun FluidListRow(title: AnnotatedString, subtitle: String, modifier: Modifier = Modifier, eyebrow: String? = null, meta: String? = null, tone: FluidTone = FluidTone.Neutral, badge: (@Composable () -> Unit)? = null, labels: (@Composable () -> Unit)? = null, titleMaxLines: Int = Int.MAX_VALUE, leading: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null, contextActions: (() -> List<FluidContextAction>)? = null, animatePress: Boolean = true, animateContent: Boolean = false, selected: Boolean = false, disclosure: Boolean = true, leadingTile: Boolean = true, subtitleMaxLines: Int = Int.MAX_VALUE, customActions: List<CustomAccessibilityAction> = emptyList())` — La riga di un gruppo, con un titolo che puo' portare stili propri — per esempio le parole che sono cambiate dall'ultima volta.
- `val FluidToneTileSize: Dp` — Il lato della piastrella di un'icona ([FluidToneTile]): il contenuto sta in 19 dp al centro.
- `@Composable fun FluidToneTile(tone: FluidTone, modifier: Modifier = Modifier, size: Dp = FluidToneTileSize, content: @Composable () -> Unit)` — The rounded, filled square an icon sits in at the head of a list row.
- `@Composable fun FluidToneTile(color: Color, modifier: Modifier = Modifier, size: Dp = FluidToneTileSize, veil: Color = color.copy(alpha = FluidToneTileVeilAlpha), content: @Composable () -> Unit)` — La piastrella con un colore qualunque: l'icona (e il testo, via `LocalContentColor`) nel [color], e dietro un velo.
- `@Composable fun FluidHeroCard(title: String, subtitle: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null)` — **DEPRECATO: Surface di Material con l'angolo circolare e un'icona di default da scuola. Usa FluidHero ** — La scheda in evidenza di prima: un titolo, una frase e un'icona su un contenitore tinto.
- `@Composable fun FluidSectionTitle(eyebrow: String, title: String, modifier: Modifier = Modifier)`
- `@Composable fun FluidStatChip(label: String, value: String, modifier: Modifier = Modifier)`
- `@Composable fun FluidEmptyState(title: String, detail: String, modifier: Modifier = Modifier, illustration: (@Composable () -> Unit)? = null)` — Uno stato vuoto: dice **perche'** non c'e' niente e cosa si puo' fare.
- `@Composable fun FluidEmptyState(title: String, detail: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier, illustration: (@Composable () -> Unit)? = null)` — Uno stato vuoto o d'errore **con un'azione**: «Riprova», «Aggiungi un account», «Collega». Un vuoto che dice perche' ma non lascia fare niente costringe a cercare il tasto altrove, e lo stato d'errore e' il posto dove l'utente lo cerca.
- `@Composable fun FluidLoading(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary)`
- `@Composable fun FluidListItem(title: String, subtitle: String, supporting: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null)` — **DEPRECATO: Era una ListItem di Material con una gerarchia sua; adesso è una FluidListRow. Usa ** — La riga semplice di prima, sotto il nome che le app già chiamano.

## `engine-ui/ui/theme/FluidTheme.kt`

- `data class AccentPreset(val name: String, val label: String, val light: Color, val dark: Color, val poles: AccentPoles? = null)` — The seed of an app palette.
  - `fun resolve(isDark: Boolean): Color`
- `data class AccentPoles(val secondaryLight: Color, val secondaryDark: Color, val tertiaryLight: Color, val tertiaryDark: Color, val secondaryBlend: Float = 0.26f, val tertiaryBlend: Float = 0.22f)` — Where an accent's secondary and tertiary families are pulled towards.
- `val fluidAccentPresets` — Names are kept from the previous palette so a stored preference still resolves; the colours are the iOS system set, which is tuned for exactly this job.
- `val FluidDefaultBrand: AccentPreset` — The accent an app falls back to before it has one of its own.
- `fun fluidBrandAccent(isDark: Boolean, brand: AccentPreset = FluidDefaultBrand): Color`
- `@Composable fun FluidTheme(settings: EngineSettings, brand: AccentPreset = FluidDefaultBrand, presets: List<AccentPreset> = fluidAccentPresets, systemBars: Boolean = true, content: @Composable () -> Unit)`
- `fun fluidColorScheme(settings: EngineSettings, isDark: Boolean, brand: AccentPreset = FluidDefaultBrand, presets: List<AccentPreset> = fluidAccentPresets, dynamicScheme: ColorScheme? = null): ColorScheme` — The app palette, resolved outside a composition.
- `@Composable fun FluidScreenSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit)`

## `engine-ui/ui/theme/Motion.kt`

- `object FluidMotionScheme : MotionScheme` — Feeds the app's springs to every Material 3 component that reads `MaterialTheme.motionScheme`, so built-in components (switches, chips, sheets) move on the same timings as the custom chrome.

## `engine-ui/ui/theme/RouteMotion.kt`

- `data class MotionOrigin(val fractionX: Float, val fractionY: Float)` — Where a destination grows from, as a fraction of the navigation host.
  - `fun toTransformOrigin(): TransformOrigin`
  - `companion object`
    - `val Center` — Used by deep links, notifications and restored back stacks, which have no gesture behind them.
- `class FluidTouchOriginState internal constructor()` — Tracks where the finger last went down, so the next navigation knows what the user touched.
  - `var origin by mutableStateOf(MotionOrigin.Center)`
- `@Composable fun rememberFluidTouchOrigin(): FluidTouchOriginState`
- `fun Modifier.fluidTouchOriginTracker(state: FluidTouchOriginState): Modifier` — Records press positions without taking part in the gesture.
- `object FluidRouteMotion`
  - `const val ExpandInitialScale` — **DEPRECATO: Dalla 3.0: FluidPageMotion.ExpandInitialScale (e FluidPageTransition.Expand).** — How small an arriving destination starts.
  - `const val ExpandParentScale` — **DEPRECATO: Dalla 3.0 la pagina coperta non si scala: arretra di un terzo (FluidNavHost).** — How far the page being covered recedes. It is going *under* the new one, not away from it.
  - `val ExpandCornerRadius: Dp` — **DEPRECATO: Dalla 3.0 la pagina in movimento ha gli angoli del telefono (FluidNavHost), non 30 dp.** — The radius a hierarchical page carries while it is not yet the whole screen.
  - `val ExitBlur: Dp` — **DEPRECATO: Dalla 3.0 nessuna pagina si sfoca: FluidRouteMotionHost non la applica piu'.** — Defocus applied to whatever is leaving. One blurred surface at a time, never two.
- `class RouteMotionSignals internal constructor()` — Whether the movement on screen right now is a hierarchical open or close.
  - `var hierarchical: Boolean` — **DEPRECATO: Dalla 3.0 non fa piu' niente: FluidRouteMotionHost non ha piu' angoli ne' sfocatura da spegnere.**
  - `val glassQuality: Float` — Quanta ottica concede il movimento fra pagine, adesso.
- `val LocalRouteMotionSignals: ProvidableCompositionLocal<RouteMotionSignals>`
- `@Composable fun rememberRouteMotionSignals(): RouteMotionSignals`
- `val LocalFluidRouteFront: ProvidableCompositionLocal<State<Boolean>>` — Se questa schermata e' quella che occupa davvero lo schermo, adesso.
- `@Composable fun FluidRouteMotionHost(animatedVisibilityScope: AnimatedVisibilityScope, content: @Composable () -> Unit)` — **DEPRECATO: Dalla 3.0 le pagine le muove FluidNavHost (dev.antigravity.fluidengine.ui.navigation): opache, **
- `@Composable fun FluidRouteMotionHost(@Suppress("UNUSED_PARAMETER") scope: FluidPageScope, content: @Composable () -> Unit)` — **DEPRECATO: Con FluidNavHost l'involucro non serve: togli FluidRouteMotionHost e lascia il contenuto.** — Per un grafo migrato a FluidNavHost che chiama ancora l'involucro con `this@composable`: il movimento lo fa l'host, e questo non fa piu' niente.
