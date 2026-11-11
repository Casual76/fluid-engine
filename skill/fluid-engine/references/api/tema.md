# API · Tema, forma, tipo

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidMetrics.kt`

- `object FluidMetrics` — Le misure dei tasti, delle barre e delle icone, in un posto solo (4.0, piano «iOS in Android», F1A.11).
  - `val GlassButtonLens: Dp` — Il vetro di un tasto tondo solo icona: 44 dp, come i 44,0 pt misurati sul tasto della barra di iOS 27 (R6, fiducia alta). Invariato: il tasto non era piccolo, lo sembrava.
  - `val TouchTarget: Dp` — Il bersaglio del tocco: mai sotto 48 dp, il minimo di Android (Apple ne documenta 44). Il vetro da 44 sta al centro di questo riquadro, con 2 dp d'aria per lato che contano nei margini qui sotto.
  - `val ScreenEdgeMargin: Dp` — Dal bordo dello schermo al **vetro** del primo tasto di una barra: 16 dp (prima 8). Apple ne lascia 20,1 pt su uno schermo da 440 pt in iOS 27 e 14-16 pt in iOS 26 (R6, media); 16 avvicina il tasto al margine del contenuto (20 dp) invece di farlo sporgere di 12, ed e' la meta' della ragione per cui i tasti sembravano piccoli.
  - `val GlassSpacing: Dp` — Fra il vetro di due tasti separati della stessa barra: 8 dp (prima 6). Apple ne misura 12,1 pt (R6, alta), ma con tre azioni sciolte 12 dp sono troppi su un telefono da 360 dp: i 12 restano per due **gruppi** ([GroupSpacing]).
  - `val GroupSpacing: Dp` — Fra due gruppi di tasti (una capsula di azioni e un tondo accanto): 12 dp, i 12,1 pt di Apple (R6, alta). Lo usa la barra dichiarata a gruppi (F4A.2); oggi nessun componente dell'engine ha gruppi.
  - `const val IconLensRatio: Float` — Quanto riquadro d'icona sta in un vetro: 0,6 del lato. La sagoma di un'icona occupa circa 0,75 del suo riquadro da 24 (R6), quindi 0,6 dà una sagoma alta 0,44 del vetro: quella di Apple (0,44-0,48, alta). Prima era 0,5.
  - `val IconSize: Dp` — Un'icona fuori da ogni vetro, barra o menu: 24 dp, il riquadro in cui sono disegnate le icone Material e [dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons]. E' la taglia che `Icon` di Material dava gia' senza modificatore: una [FluidIcon] fuori dai componenti non cambia misura.
  - `val BarIconSize: Dp` — L'icona di un'azione della barra in alto ([FluidBarAction]): 26 dp, cioe' 0,6 del vetro da 44 (prima 22).
  - `val BackIconSize: Dp` — La freccia del tasto indietro: 24 dp (prima 20). Un po' sotto le altre icone perche' la freccia e' una sagoma stretta e alta: a 24 dp e' alta 18,8 dp con un tratto di 2,6, contro i 18,7 pt e 2,5 pt del chevron di iOS 27 (R6, alta).
  - `val CapsuleIconSize: Dp` — L'icona di un'azione della capsula flottante ([FluidCapsuleAction]): 26 dp (prima 24), come in barra.
  - `val TabBarIconSize: Dp` — L'icona di una scheda della tab bar: 28 dp (prima 24, quella che l'app scriveva). Le icone della tab bar di iOS 27 hanno sagome di 21-31 pt (R6, alta); con le etichette a 11 sp l'icona torna a comandare la scheda, come in Apple (icona sulla maiuscola dell'etichetta: 3,2 in Apple, 1,8 prima nell'engine).
  - `val MenuIconSize: Dp` — L'icona di una voce di menu: 22 dp (prima 20); riga e testo invariati. In Apple le icone del menu sono quelle della barra divise per 1,3 (R6): 26 / 1,3 = 20, e 22 tiene il peso un passo sopra senza toccare la riga da 48.
  - `fun iconSizeIn(lens: Dp): Dp` — Il riquadro dell'icona che sta bene in un vetro di lato [lens]: [IconLensRatio] del lato, al dp intero. 24, 26 e 32 dp per i vetri da 40, 44 e 54 dp ([FluidGlassSize]); un disco da 76 dp ne prende 46.
  - `val TabBarHeight: Dp` — L'altezza della tab bar flottante aperta: 62 dp (prima 60), i 62,6 pt di iOS 27 (R6, alta). E' un pavimento: col testo ingrandito la barra cresce.
  - `val TabBarLabelSize: TextUnit` — L'etichetta di una scheda: 11 sp (prima 13). Apple ne usa 10 pt (R6, alta); sotto gli 11 l'italiano («Aggiornamenti», «Impostazioni») non guadagna abbastanza da giustificare la lettura peggiore. Se in S2 si legge male, torna a 12 sp qui (piano, F1B).
  - `val TabBarLabelLineHeight: TextUnit` — L'interlinea dell'etichetta a 11 sp: quella di `labelSmall` del tipo dell'engine, una riga sola.
  - `val CompactTitleInset: Dp` — Il margine del titolo agganciato da ogni lato: 84 dp (prima 76). Segue i tasti: il vetro ora sta a 16 dp dal bordo e finisce a 16 + 44 = 60, piu' il respiro di sempre. Un titolo lungo si tronca 8 dp prima per lato.

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
