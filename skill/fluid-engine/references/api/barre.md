# API · Barre di navigazione e di azione

_Generato sull'engine 3.0.6 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidFloatingCapsule.kt`

- `enum class FluidFloatingCapsulePlacement` — Dove sta una [FluidFloatingCapsule] rispetto alla barra di navigazione flottante.
- `object FluidFloatingCapsuleDefaults` — I numeri della [FluidFloatingCapsule], tutti insieme perche' la barra di selezione e il lettore li condividano.
  - `val Height: Dp` — L'altezza minima: quella della barra di navigazione aperta ([FluidFloatingTabBarDefaults.OpenHeight]). Una capsula che prende il posto della barra e' alta quanto lei, e con un'azione da 48 dp piu' sei dp di margine sopra e sotto e' esattamente 60: non serve forzarla.
  - `val ActionSize: Dp` — Il lato di un'azione ([FluidCapsuleAction]): il bersaglio minimo di un dito.
  - `val Spacing: Dp` — Fra un pezzo e il successivo: le azioni sono vicine, non sparse.
  - `val ContentPadding: PaddingValues` — Il margine dentro il vetro: sei dp, quelli della barra di navigazione ([FluidFloatingTabBarDefaults.sizes]) piu' due.
  - `val MaxWidth: Dp` — La capsula a tutta larghezza non attraversa un tablet: resta della misura di un telefono grande, centrata. Lo stesso tetto che la barra di Fluidweather si dava a mano.
  - `fun bottomOffset(placement: FluidFloatingCapsulePlacement): Dp` — Da quanto sta staccato il bordo basso della capsula dal bordo basso dello schermo, **senza** la barra di sistema (quella la aggiunge [fluidCapsuleInsets]). Puro, perche' e' il numero che decide se una capsula copre la barra o la sfiora.
  - `fun contentInset(placement: FluidFloatingCapsulePlacement, capsuleHeight: Dp = Height): Dp` — Lo spazio che la pagina deve lasciarsi in fondo perche' la capsula non copra il suo contenuto, senza la barra di sistema. [Free] non ne chiede: non si sa dove stia.
  - `fun enter(reducedMotion: Boolean = false): EnterTransition` — Come entra: sale da sotto con la molla del chrome (critica, niente rimbalzo — una barra che rimbalza e' il segno piu' rumoroso di "non e' premium") e si accende. Con le animazioni ridotte non si muove: resta la dissolvenza.
  - `fun exit(reducedMotion: Boolean = false): ExitTransition` — Come esce: piu' svelta di come entra (chi la chiude non aspetta), stessa direzione.
- `fun Modifier.fluidCapsuleInsets(placement: FluidFloatingCapsulePlacement, avoidKeyboard: Boolean = false): Modifier` — I margini di una capsula flottante in fondo allo schermo: la barra di sistema, i margini laterali della barra di navigazione e il suo margine basso, piu' — per [FluidFloatingCapsulePlacement.AboveTabBar] — lo spazio della barra stessa.
- `@Composable fun FluidFloatingCapsule(modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), visible: Boolean = true, placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.Free, fillWidth: Boolean = false, onPhoto: Boolean = false, avoidKeyboard: Boolean = false, minHeight: Dp = FluidFloatingCapsuleDefaults.Height, contentPadding: PaddingValues = FluidFloatingCapsuleDefaults.ContentPadding, label: String? = null, leading: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable RowScope.() -> Unit)` — Una capsula di vetro con delle azioni dentro, che galleggia sopra la pagina: la barra di una selezione multipla, un mini player, i comandi di una foto, un avviso con un tasto.
- `@Composable fun FluidCapsuleAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false, tint: Color = Color.Unspecified, onLongClick: (() -> Unit)? = null, size: Dp = FluidFloatingCapsuleDefaults.ActionSize, grip: FluidPressGrip = FluidPressGrip.HoldsFinger)` — Un'azione dentro una [FluidFloatingCapsule]: un'icona, la sua descrizione e il bersaglio da 48 dp.

## `engine-ui/ui/fluid/FluidFloatingTabBar.kt`

- `@Composable fun FluidFloatingTabBar(selectedTabKey: Any?, scrollConnection: FluidFloatingTabBarScrollConnection, modifier: Modifier = Modifier, tabBarContentModifier: Modifier = Modifier, inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, colors: FluidFloatingTabBarColors = FluidFloatingTabBarDefaults.colors(), glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(), shapes: FluidFloatingTabBarShapes = FluidFloatingTabBarDefaults.shapes(), sizes: FluidFloatingTabBarSizes = FluidFloatingTabBarDefaults.sizes(), elevations: FluidFloatingTabBarElevations = FluidFloatingTabBarDefaults.elevations(), backdrop: GlassBackdropState? = null, accentColor: Color? = null, foldAlignment: Alignment.Horizontal = Alignment.Start, searchMode: Boolean = false, searchBarContent: (@Composable (Modifier) -> Unit)? = null, expandedContentWidthPx: Int? = null, onExpandedWidthChanged: ((Int) -> Unit)? = null, content: FluidFloatingTabBarScope.() -> Unit)` — A floating tab bar that transitions between inline and expanded states based on scroll behavior.
- `val LocalFluidTabBarBackdropFrozen` — True for exactly the frames of a tab bar transition (inline <-> expanded <-> search). The bar's glass surfaces pass it to `drawBackdrop`'s `frozen`, which makes them reuse their last backdrop capture instead of re-recording the whole screen every frame while their bounds animate.
- `data class FluidFloatingTabBarGlass(val blurRadius: Dp = DefaultGlassBlurRadius, val puckColor: Color = Color.Unspecified, val puckOpacity: Float = 0.26f)` — The two or three numbers of this bar's glass an app is allowed to move.
- `val LocalFluidFloatingTabBarGlass` — What the bar's glass is made of, for the parts of it that are composed too deep to be passed a parameter.
- `class FluidFloatingTabBarScrollConnection(initialIsInline: Boolean = false, private val scrollThresholdPx: Float, private val expandThresholdPx: Float = scrollThresholdPx, private val inlineBehavior: FluidFloatingTabBarInlineBehavior = FluidFloatingTabBarInlineBehavior.OnScrollDown) : NestedScrollConnection` — A [NestedScrollConnection] that handles scroll events to transition between inline and expanded states.
  - `var isInline by mutableStateOf(initialIsInline)`
  - `var locked by mutableStateOf(false)` — LOCAL CHANGE: while true, scrolling leaves the bar alone.
  - `var morphing by mutableStateOf(false)` — LOCAL CHANGE: true for exactly the frames the bar is changing shape.
  - `var scrolling by mutableStateOf(false)` — LOCAL CHANGE: true while the page under the bar is moving.
  - `fun expand()`
  - `fun settle()` — Ends the scroll-hold by decree.
  - `fun inline()`
- `@Composable fun rememberFluidFloatingTabBarScrollConnection(initialIsInline: Boolean = false, scrollThreshold: Dp = 50.dp, expandThreshold: Dp = 8.dp, inlineBehavior: FluidFloatingTabBarInlineBehavior = FluidFloatingTabBarInlineBehavior.OnScrollDown): FluidFloatingTabBarScrollConnection` — Creates and remembers a [FluidFloatingTabBarScrollConnection] instance.
- `enum class FluidFloatingTabBarInlineBehavior` — Defines when the floating tab bar should transition to inline state.
- `@Composable fun FluidFloatingTabBar(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, scrollConnection: FluidFloatingTabBarScrollConnection, backdrop: GlassBackdropState? = null, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {}, glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(), accentColor: Color? = null, foldAlignment: Alignment.Horizontal = Alignment.Start)` — The same bar, for a screen whose navigation is a list of destinations and nothing more.
- `interface FluidFloatingTabBarScope`
  - `fun tab(key: Any, title: @Composable () -> Unit, icon: @Composable () -> Unit, onClick: () -> Unit, indication: (@Composable () -> Indication)? = null)` — Adds a regular tab to the floating tab bar.
  - `fun standaloneTab(key: Any, icon: @Composable () -> Unit, onClick: () -> Unit, indication: (@Composable () -> Indication)? = null, title: @Composable () -> Unit = {})` — Adds a standalone tab to the floating tab bar.
- `data class FluidFloatingTabBarColors(val backgroundColor: Color, val accessoryBackgroundColor: Color)` — Represents the colors used in [FluidFloatingTabBar].
- `data class FluidFloatingTabBarShapes(val tabBarShape: Shape, val tabShape: Shape, val standaloneTabShape: Shape, val accessoryShape: Shape)` — Represents the shapes used in [FluidFloatingTabBar].
- `data class FluidFloatingTabBarElevations(val inlineElevation: Dp, val expandedElevation: Dp)` — Represents the elevations used in [FluidFloatingTabBar].
- `data class FluidFloatingTabBarSizes(val tabBarContentPadding: PaddingValues, val tabInlineContentPadding: PaddingValues, val tabExpandedContentPadding: PaddingValues, val componentSpacing: Dp, val tabSpacing: Dp, val tabWidth: Dp = FluidFloatingTabBarDefaults.TabWidth, val barHeight: Dp = FluidFloatingTabBarDefaults.OpenHeight, val inlineHeight: Dp = FluidFloatingTabBarDefaults.FoldedHeight, val inlineSoloHeight: Dp = FluidFloatingTabBarDefaults.FoldedSoloHeight)` — Represents the sizes and spacing used in [FluidFloatingTabBar].
- `object FluidFloatingTabBarDefaults` — Contains the default values used by [FluidFloatingTabBar].
  - `val TabWidth: Dp` — Width of one expanded tab slot at full size. Callers that have to fit the pill plus the standalone tab into a narrow screen shrink below this — see [com.convx.music.ui.component.AppFloatingNavBar] — so it is the ceiling, not a guarantee.
  - `val OpenHeight: Dp` — How tall the bar stands when it is open.
  - `val FoldedHeight: Dp` — How tall it stands once a scroll has folded it back to its inline shape.
  - `val FoldedSoloHeight: Dp` — The same, when the folded pill has nothing beside it.
  - `val Spacing: Dp` — The gap between the bar and the accessory band above it.
  - `val BottomMargin: Dp` — Above the system's gesture handle, not against it.
  - `val HorizontalMargin: Dp` — The bar's side margin, which is a screen's gutter and not the bar's own.
  - `val ContentInset: Dp` — Vertical space a screen must leave free so its content clears the bar at its tallest.
  - `fun contentInsetWithAccessory(accessoryHeight: Dp): Dp` — The same, for a bar carrying an accessory band — a now-playing pill, say.
  - `@Composable fun colors(backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, accessoryBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh): FluidFloatingTabBarColors` — Creates a [FluidFloatingTabBarColors] that represents the default colors used in a [FluidFloatingTabBar].
  - `@Composable fun shapes(tabBarShape: Shape = FluidCapsuleShape, tabShape: Shape = FluidCapsuleShape, standaloneTabShape: Shape = FluidCapsuleShape, accessoryShape: Shape = FluidCapsuleShape): FluidFloatingTabBarShapes` — Creates a [FluidFloatingTabBarShapes] that represents the default shapes used in a [FluidFloatingTabBar].
  - `@Composable fun sizes(tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.d…, tabInlineContentPadding: PaddingValues = PaddingValues(10.dp), tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 6.d…, componentSpacing: Dp = 8.dp, tabSpacing: Dp = 0.dp, tabWidth: Dp = TabWidth, barHeight: Dp = OpenHeight, inlineHeight: Dp = FoldedHeight, inlineSoloHeight: Dp = FoldedSoloHeight): FluidFloatingTabBarSizes` — Creates a [FluidFloatingTabBarSizes] that represents the default sizes used in a [FluidFloatingTabBar].
  - `@Composable fun labelStyle(): TextStyle` — What a tab's label is written in.
  - `@Composable fun elevations(inlineElevation: Dp = 6.dp, expandedElevation: Dp = 12.dp): FluidFloatingTabBarElevations` — Creates a [FluidFloatingTabBarElevations] that represents the default elevations used in a [FluidFloatingTabBar].

## `engine-ui/ui/fluid/FluidFoldingTabBar.kt`

- `enum class FluidFoldAlignment` — Dove si raccoglie la barra quando si ripiega.
- `@Composable fun FluidFoldingTabBar(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, fold: () -> Float, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {}, onExpandRequest: (() -> Unit)? = null, @Suppress("UNUSED_PARAMETER") trailing: (@Composable () -> Unit)? = null, tabIcon: (@Composable (item: FluidTabItem, selected: Boolean) -> Unit)? = null, accessory: (@Composable () -> Unit)? = null, @Suppress("UNUSED_PARAMETER") accessoryHeight: Dp = FluidFoldingTabBarDefaults.AccessoryHeight, searchMode: Boolean = false, searchContent: (@Composable (Modifier) -> Unit)? = null, foldAlignment: FluidFoldAlignment = FluidFoldAlignment.Start, @Suppress("UNUSED_PARAMETER") tint: GlassTint = GlassDefaults.floatingTint())` — **DEPRECATO: Use FluidFloatingTabBar, which this now is. It also does what this signature cannot ask for: ** — The folding tab bar, on top of [FluidFloatingTabBar].
- `object FluidFoldingTabBarDefaults` — The numbers a screen needs in order to leave room for this bar. The new bar's own, now.
  - `val OpenHeight: Dp get()`
  - `val FoldedHeight: Dp get()`
  - `val SoloFoldedHeight: Dp get()`
  - `val Spacing: Dp get()`
  - `val Inset: Dp`
  - `val AccessoryHeight: Dp`
  - `val HorizontalMargin: Dp get()`
  - `val BottomMargin: Dp get()`
  - `val ContentInset: Dp get()` — Vertical space a screen must leave free so its content clears the bar at its tallest.
  - `fun contentInsetWithAccessory(accessoryHeight: Dp = AccessoryHeight): Dp` — The same, for a bar carrying an accessory band of [AccessoryHeight].
- `class FluidBarFold internal constructor(val progress: State<Float>, private val target: MutableFloatState, private val density: Float)` — The fold, and the scroll connection that moves it.
  - `val folded: Boolean get()`
  - `var locked: Boolean by mutableStateOf(false)` — While this is true the bar stops answering the page, and stays exactly where it is.
  - `val connection: NestedScrollConnection`
  - `fun unfold()`
  - `fun foldNow()`
- `@Composable fun rememberFluidBarFold(): FluidBarFold`

## `engine-ui/ui/fluid/FluidSearchField.kt`

- `object FluidSearchFieldDefaults` — Le misure del [FluidSearchField].
  - `val Height: Dp` — L'altezza: 52 dp, la stessa che il campo di ricerca di Fluid-transit si dava a mano.
  - `val IconSize: Dp` — La lente d'ingrandimento.
  - `val IconInset: Dp` — Il margine a sinistra della lente: quello che la mette **al centro** del cerchio quando il campo e' chiuso — `(Height - IconSize) / 2` — e che resta come margine di testo quando si apre. Il cerchio che diventa campo non deve spostare la lente di un pixel.
  - `val ActionSize: Dp` — Il lato dei comandi dentro il campo (precedente, successivo, chiudi): quattro in 332 dp non ci stanno a 48.
  - `val ExpandedFallbackWidth: Dp` — La larghezza di apertura quando il genitore non ne da' una (dentro uno scorrimento orizzontale).
- `data class FluidSearchLabels(val search: String = "Cerca", val clear: String = "Cancella", val close: String = "Chiudi la ricerca", val previous: String = "Risultato precedente", val next: String = "Risultato successivo", val noResults: String = "Nessun risultato", val counterSpoken: (current: Int, total: Int) -> String = { current, total -> "$current di $total" })` — Le parole del campo, tutte insieme: un'app in un'altra lingua ne passa una e non cinque parametri. Il default e' l'italiano dell'engine ("Chiudi", "Indietro", ...).
- `class FluidSearchNavigation(val current: Int, val total: Int, val onPrevious: () -> Unit, val onNext: () -> Unit)` — La navigazione fra i risultati di una ricerca dentro la pagina: «3/12» e i due tasti che vanno avanti e indietro.
- `@Composable fun FluidSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop…, placeholder: String? = null, labels: FluidSearchLabels = FluidSearchLabels(), onSearch: (() -> Unit)? = null, expanded: Boolean = true, onExpandedChange: ((Boolean) -> Unit)? = null, navigation: FluidSearchNavigation? = null, autoFocus: Boolean = true, onPhoto: Boolean = false, keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))` — Un campo di ricerca in vetro: la capsula con la lente a sinistra, il testo, la crocetta che svuota e — se serve — «3/12» con i due tasti per scorrere i risultati dentro la pagina.

## `engine-ui/ui/fluid/FluidTabBar.kt`

- `data class FluidTabItem(val route: String, val label: String, val icon: ImageVector)`
- `object FluidTabBarDefaults` — Le misure della barra di prima e del rail.
  - `val Height`
  - `val HorizontalMargin`
  - `val BottomMargin` — Above the system's gesture handle, not against it. See [FluidFoldingTabBarDefaults].
  - `val RailWidth`
  - `val ContentInset` — Vertical space a screen must leave free so its content clears the floating bar.
- `@Composable fun FluidTabBar(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {})` — **DEPRECATO: È FluidFloatingTabBar con la lista di schede, e ne ha il vetro: usa quella. Cambia la ** — La barra di prima, sotto il nome che le app già chiamano.
- `@Composable fun FluidTabRail(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {})` — Wide-screen variant: the same material and the same drop, stood on its side.
- `@Composable fun fluidTabBarPadding(): PaddingValues` — Padding a floating tab bar needs around itself.
