<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/barre.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · Barre di navigazione e di azione

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidFloatingCapsule.kt`

- `enum class FluidFloatingCapsulePlacement` — Dove sta una [FluidFloatingCapsule] rispetto alla barra di navigazione flottante.
- `object FluidFloatingCapsuleDefaults` — I numeri della [FluidFloatingCapsule], tutti insieme perche' la barra di selezione e il lettore li condividano.
  - `val Height: Dp` — L'altezza minima: quella della barra di navigazione aperta ([FluidFloatingTabBarDefaults.OpenHeight]). Una capsula che prende il posto della barra e' alta quanto lei, e con un'azione da 48 dp piu' il margine sopra e sotto ([ContentPadding]) lo e' esattamente: non serve forzarla. Dalla 4.0 sono 62 dp (erano 60), perche' la barra e' cresciuta di 2 (R6): la capsula la segue.
  - `val ActionSize: Dp` — Il lato di un'azione ([FluidCapsuleAction]): il bersaglio minimo di un dito.
  - `val Spacing: Dp` — Fra un pezzo e il successivo: le azioni sono vicine, non sparse.
  - `val ContentPadding: PaddingValues` — Il margine dentro il vetro: sei dp ai lati, quelli della barra di navigazione ([FluidFloatingTabBarDefaults.sizes]) piu' due; sopra e sotto quanto porta un'azione da [ActionSize] all'altezza della barra ([Height]): 7 dp dalla 4.0 (erano 6 con la barra a 60).
  - `val MaxWidth: Dp` — La capsula a tutta larghezza non attraversa un tablet: resta della misura di un telefono grande, centrata. Lo stesso tetto che la barra di Fluidweather si dava a mano.
  - `fun bottomOffset(placement: FluidFloatingCapsulePlacement): Dp` — Da quanto sta staccato il bordo basso della capsula dal bordo basso dello schermo, **senza** la barra di sistema (quella la aggiunge [fluidCapsuleInsets]). Puro, perche' e' il numero che decide se una capsula copre la barra o la sfiora.
  - `fun contentInset(placement: FluidFloatingCapsulePlacement, capsuleHeight: Dp = Height): Dp` — Lo spazio che la pagina deve lasciarsi in fondo perche' la capsula non copra il suo contenuto, senza la barra di sistema. [Free] non ne chiede: non si sa dove stia.
  - `fun enter(reducedMotion: Boolean = false): EnterTransition` — Come entra: sale da sotto con la molla del chrome (critica, niente rimbalzo — una barra che rimbalza e' il segno piu' rumoroso di "non e' premium") e si accende. Con le animazioni ridotte non si muove: resta la dissolvenza.
  - `fun exit(reducedMotion: Boolean = false): ExitTransition` — Come esce: piu' svelta di come entra (chi la chiude non aspetta), stessa direzione.
- `fun Modifier.fluidCapsuleInsets(placement: FluidFloatingCapsulePlacement, avoidKeyboard: Boolean = false): Modifier` — I margini di una capsula flottante in fondo allo schermo: la barra di sistema, i margini laterali della barra di navigazione e il suo margine basso, piu' — per [FluidFloatingCapsulePlacement.AboveTabBar] — lo spazio della barra stessa.
- `@Composable fun FluidFloatingCapsule(modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), visible: Boolean = true, placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.Free, fillWidth: Boolean = false, onPhoto: Boolean = false, avoidKeyboard: Boolean = false, minHeight: Dp = FluidFloatingCapsuleDefaults.Height, contentPadding: PaddingValues = FluidFloatingCapsuleDefaults.ContentPadding, label: String? = null, leading: (@Composable RowScope.() -> Unit)? = null, trailing: (@Composable RowScope.() -> Unit)? = null, content: @Composable RowScope.() -> Unit)` — Una capsula di vetro con delle azioni dentro, che galleggia sopra la pagina: la barra di una selezione multipla, un mini player, i comandi di una foto, un avviso con un tasto.
- `@Composable fun FluidCapsuleAction(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false, tint: Color = Color.Unspecified, onLongClick: (() -> Unit)? = null, size: Dp = FluidFloatingCapsuleDefaults.ActionSize, grip: FluidPressGrip = FluidPressGrip.HoldsFinger, selected: Boolean? = null)` — Un'azione dentro una [FluidFloatingCapsule]: un'icona, la sua descrizione e il bersaglio da 48 dp.

## `engine-ui/ui/fluid/FluidFloatingTabBar.kt`

- `enum class FluidTabRole` — Una destinazione prominente conserva il suo tondo anche quando lo scorrimento ripiega la barra.
- `enum class FluidTabSearchActivation` — In SearchTabSelection la destinazione di ricerca apre il campo e al ritorno riprende la scheda precedente.
- `@Composable fun FluidFloatingTabBar(selectedTabKey: Any?, scrollConnection: FluidFloatingTabBarScrollConnection, modifier: Modifier = Modifier, tabBarContentModifier: Modifier = Modifier, inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, colors: FluidFloatingTabBarColors = FluidFloatingTabBarDefaults.colors(), glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(), shapes: FluidFloatingTabBarShapes = FluidFloatingTabBarDefaults.shapes(), sizes: FluidFloatingTabBarSizes = FluidFloatingTabBarDefaults.sizes(), elevations: FluidFloatingTabBarElevations = FluidFloatingTabBarDefaults.elevations(), backdrop: GlassBackdropState? = null, accentColor: Color? = null, foldAlignment: Alignment.Horizontal = Alignment.Start, searchMode: Boolean = false, searchBarContent: (@Composable (Modifier) -> Unit)? = null, expandedContentWidthPx: Int? = null, onExpandedWidthChanged: ((Int) -> Unit)? = null, content: FluidFloatingTabBarScope.() -> Unit)` — A floating tab bar that transitions between inline and expanded states based on scroll behavior.
- `@Composable fun FluidFloatingTabBar(selectedTabKey: Any?, scrollConnection: FluidFloatingTabBarScrollConnection, modifier: Modifier = Modifier, tabBarContentModifier: Modifier = Modifier, inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null, colors: FluidFloatingTabBarColors = FluidFloatingTabBarDefaults.colors(), glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(), shapes: FluidFloatingTabBarShapes = FluidFloatingTabBarDefaults.shapes(), sizes: FluidFloatingTabBarSizes = FluidFloatingTabBarDefaults.sizes(), elevations: FluidFloatingTabBarElevations = FluidFloatingTabBarDefaults.elevations(), backdrop: GlassBackdropState? = null, accentColor: Color? = null, foldAlignment: Alignment.Horizontal = Alignment.Start, searchMode: Boolean = false, searchBarContent: (@Composable (Modifier) -> Unit)? = null, expandedContentWidthPx: Int? = null, onExpandedWidthChanged: ((Int) -> Unit)? = null, searchActivation: FluidTabSearchActivation, content: FluidFloatingTabBarScope.() -> Unit)` — Permette alla scheda Search di aprire il campo alla selezione mantenendo la firma delle app esistenti.
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
  - `fun tab(key: Any, title: @Composable () -> Unit, icon: @Composable () -> Unit, badge: FluidTabBadge?, onClick: () -> Unit, indication: (@Composable () -> Indication)? = null)` — Una zona **col segno** (3.1.0, pm-f1/ge E01): come l'altro `tab`, piu' [badge].
  - `fun standaloneTab(key: Any, icon: @Composable () -> Unit, onClick: () -> Unit, indication: (@Composable () -> Indication)? = null, title: @Composable () -> Unit = {})` — Adds a standalone tab to the floating tab bar.
  - `fun standaloneTab(key: Any, icon: @Composable () -> Unit, role: FluidTabRole, onClick: () -> Unit, indication: (@Composable () -> Indication)? = null, title: @Composable () -> Unit = {})` — Search cede il tondo a Prominent. Il titolo serve quando la ricerca rientra nel gruppo.
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

## `engine-ui/ui/fluid/FluidSearch.kt`

- `enum class FluidSearchPlacement` — La ricerca appartiene alla barra: Automatic sceglierà basso su telefono e alto su tablet (F4B.2).
- `enum class FluidSearchBehavior` — L'app può chiedere il campo sempre visibile o il tasto iniziale, senza animarlo da sola.
- `enum class FluidSearchPurpose` — Distingue ricerca e inserimento generico mantenendo un solo contratto per la DSL (piano F4B).
- `enum class FluidSearchExpansion` — In alto il campo può inglobare i vicini oppure raggiungere la tastiera; il viaggio nasce in F4B.2.
- `data class FluidSearchSpec(val query: String, val onQueryChange: (String) -> Unit, val placement: FluidSearchPlacement = FluidSearchPlacement.Automatic, val behavior: FluidSearchBehavior = FluidSearchBehavior.Automatic, val prompt: String? = null, val onSearch: (() -> Unit)? = null, val suggestions: (@Composable () -> Unit)? = null, val scopes: (@Composable () -> Unit)? = null, val purpose: FluidSearchPurpose = FluidSearchPurpose.Search, val expansion: FluidSearchExpansion = FluidSearchExpansion.Automatic, val active: Boolean = false, val onActiveChange: ((Boolean) -> Unit)? = null)` — Dichiarazione del campo, condivisa da schermata e toolbar: F4A.2 la posa, F4B.2 ne possiede il comportamento.

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

- `data class FluidTabItem(val route: String, val label: String, val icon: ImageVector, val badge: FluidTabBadge? = null, val filledIcon: ImageVector? = null)` — Una destinazione della navigazione: la barra a elenco ([FluidFloatingTabBar]) e il rail ([FluidTabRail]).
- `data class FluidTabBadge(val count: Int? = null, val contentDescription: String? = null, val color: Color = Color.Unspecified)` — Il segno su una zona della barra o del rail: un pallino («ci sono novita'») o un numero.
  - `companion object`
    - `val Dot: FluidTabBadge` — Il pallino senza numero: «ci sono novita'».
- `object FluidTabBarDefaults` — Le misure della barra di prima e del rail.
  - `val Height`
  - `val HorizontalMargin`
  - `val BottomMargin` — Above the system's gesture handle, not against it. See [FluidFoldingTabBarDefaults].
  - `val RailWidth`
  - `val RailHeaderSpacing` — Lo spazio fra l'intestazione del rail (l'azione primaria, `FluidTabRail(header = …)`) e la capsula delle schede: 8 dp, come lo spazio fra la pillola e il cerchio accanto nella barra in basso.
  - `val RailIconSize` — L'icona di una scheda del rail: 28 dp, il token della tab bar ([FluidMetrics.TabBarIconSize]; erano 24). Un nome qui e non il token nudo perche' il rail e' un posto a se' (un'app puo' voler leggere la taglia che ci va) e perche' una prova lo tiene d'occhio: se un giorno rail e barra si separano, e' una scelta e non una dimenticanza.
  - `val ContentInset` — Vertical space a screen must leave free so its content clears the floating bar.
- `@Composable fun FluidTabBar(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {})` — **DEPRECATO: È FluidFloatingTabBar con la lista di schede, e ne ha il vetro: usa quella. Cambia la ** — La barra di prima, sotto il nome che le app già chiamano.
- `@Composable fun FluidTabRail(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {})` — Wide-screen variant: the same material and the same drop, stood on its side.
- `@Composable fun FluidTabRail(items: List<FluidTabItem>, selectedRoute: String?, onSelect: (FluidTabItem) -> Unit, backdrop: GlassBackdropState, header: (@Composable ColumnScope.() -> Unit)?, modifier: Modifier = Modifier, onReselect: (FluidTabItem) -> Unit = {})` — Il rail **con un'intestazione** (3.1.0, pm-f1/ge): come l'altro [FluidTabRail], piu' [header].
- `@Composable fun fluidTabBarPadding(): PaddingValues` — Padding a floating tab bar needs around itself.

## `engine-ui/ui/fluid/toolbar/FluidToolbar.kt`

- `annotation class FluidToolbarDsl` — Tiene le chiamate di uno scope della barra dentro il loro blocco: un `button` di un gruppo non finisce fuori.
- `enum class FluidToolbarTitleMode` — Come la pagina mostra il suo titolo (piano «iOS in Android», F4A.2): `navigationBarTitleDisplayMode` di SwiftUI.
- `object FluidToolbarIds` — Le identità che la DSL dà di serie ai tasti che hanno un ruolo solo per pagina: due pagine che hanno entrambe «indietro» hanno **lo stesso** indietro (F4A.3 lo terrà fermo al pixel fra le due), e lo stesso vale per Chiudi e la conferma. Un'app che ne vuole due diversi passa un `id` suo.
  - `val Back: Any` — Il tasto indietro.
  - `val Close: Any` — Il tasto Chiudi.
  - `val Confirm: Any` — La conferma (la spunta tinta).
- `class FluidToolbarScope internal constructor()` — La barra in alto di una pagina, **dichiarata** (piano «iOS in Android», F4A.2): la pagina dice chi sono i suoi tasti e che cosa fanno, la barra decide gruppi, vetri, misure e trabocco, e ogni cambio di stato della pagina (da «sfoglia» a «seleziona», un'azione che diventa principale, un tasto che non ci sta più) si trasforma da solo sul piano flottante: nessuna animazione scritta dalla pagina.
  - `fun title(mode: FluidToolbarTitleMode = FluidToolbarTitleMode.Large, subtitle: String? = null)` — Come si mostra il titolo ([FluidToolbarTitleMode]) e, se c'è, il sottotitolo: sotto il titolo grande finché non si aggancia, e poi sotto il titolo agganciato, più piccolo.
  - `fun subtitle(text: String?)` — Il sottotitolo: sotto il titolo grande e, agganciato, sotto quello della barra. Null: nessuno.
  - `fun back(onClick: () -> Unit, label: String? = null, id: Any = FluidToolbarIds.Back)` — Il tasto indietro: il chevron in un tondo di vetro, a sinistra, gruppo a sé. Una [FluidScreen] con `onBack` e senza un `back` dichiarato lo aggiunge da sola.
  - `fun close(onClick: () -> Unit, label: String? = null, id: Any = FluidToolbarIds.Close, placement: FluidToolbarPlacement = FluidToolbarPlacement.Navigation)` — Il tasto Chiudi: la X in un tondo di vetro, gruppo a sé, di serie a sinistra come l'indietro (in un foglio prende il suo posto). Con [placement] la si mette altrove, per esempio `ConfirmationAction` a destra.
  - `fun confirm(onClick: () -> Unit, label: String? = null, id: Any = FluidToolbarIds.Confirm, enabled: Boolean = true, tint: Color = Color.Unspecified)` — La conferma: una **spunta tinta**, da sola, a destra (R1 B5). È l'azione principale della pagina (una sola): un gruppo che la conteneva si divide quando un suo tasto lo diventa.
  - `fun primary(id: Any, icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true, tint: Color = Color.Unspecified)` — L'azione principale con un'icona sua: vetro tinto (di serie l'accento), icona chiara, da sola a destra. Una sola per barra, come la conferma, che è un'azione principale anche lei.
  - `fun button(id: Any, icon: ImageVector, label: String, onClick: () -> Unit, placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic, priority: FluidToolbarPriority = FluidToolbarPriority.Automatic, role: FluidButtonRole? = null, tint: Color = Color.Unspecified, selected: Boolean = false, enabled: Boolean = true, badge: Int? = null, badgeDot: Boolean = false, sharedBackground: Boolean = true)` — Un tasto con un'icona: vicino ad altre icone dello stesso lato divide il vetro con loro (un gruppo).
  - `fun text(id: Any, text: String, onClick: () -> Unit, placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic, priority: FluidToolbarPriority = FluidToolbarPriority.Automatic, role: FluidButtonRole? = null, tint: Color = Color.Unspecified, enabled: Boolean = true)` — Un tasto con una scritta («Seleziona», «Fine»): ha sempre il suo vetro, e non sta in un gruppo con le icone.
  - `fun item(id: Any, label: String? = null, width: Dp = FluidMetrics.GlassButtonLens, placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic, priority: FluidToolbarPriority = FluidToolbarPriority.Automatic, onClick: (() -> Unit)? = null, content: @Composable () -> Unit)` — Un elemento libero, **col suo vetro**, per ciò che la DSL non copre (un avatar, un indicatore): [content] sta in un vetro di [width] × 44 dp (di serie un tondo). Ha la sua identità come gli altri e non si unisce a nessun gruppo. Per TalkBack passare [label] e dichiarare qui [onClick] se il contenuto esegue un comando. Il bersaglio della barra porta nome e azione anche quando quel comando passa nel menu «Altro».
  - `fun spacer(width: Dp = Dp.Unspecified)` — Uno spazio fisso: spezza un gruppo in due vetri. Di serie la distanza fra due gruppi (12 dp); una misura più larga la sostituisce.
  - `fun flexibleSpacer()` — Uno spazio flessibile: spinge ciò che segue all'altro estremo della barra.
  - `fun group(id: Any, placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic, priority: FluidToolbarPriority = FluidToolbarPriority.Automatic, sharedBackground: Boolean = true, content: FluidToolbarScope.() -> Unit)` — Un gruppo esplicito: i suoi tasti stanno insieme, e non si uniscono ai vicini di fuori. [placement] e [priority] valgono per i figli che non ne dicono uno loro.
  - `fun overflow(label: String? = null, content: FluidToolbarScope.() -> Unit)` — Ciò che sta **sempre** nel menu «Altro» (il tasto coi tre puntini, a destra), qualunque sia la larghezza. [label] è il nome del tasto «Altro»; di serie le parole dell'engine.
  - `fun search(spec: FluidSearchSpec)` — La ricerca della pagina (lo stesso posto del parametro `search` di `FluidScreen`, che questa sostituisce): in F4A è il campo sotto il titolo. Posizioni in barra, suggestions, scopes, purpose ed expansion arrivano in F4B.2. FluidPageChrome e FluidCollapsingTopBar accettano la specifica, ma solo FluidScreen posa il campo.

## `engine-ui/ui/fluid/toolbar/FluidToolbarModel.kt`

- `enum class FluidToolbarPlacement` — Posizioni semantiche: la pagina dichiara l'intento e la barra sceglie il lato, anche nel trabocco.
- `enum class FluidToolbarPriority` — Il posto si libera dal meno importante; a parità resta prima il tasto più esterno (R5 B11).
- `enum class FluidToolbarSpacing` — Il fisso separa due vetri, il flessibile distribuisce i comandi agli estremi (R1 B2).
- `enum class FluidButtonRole` — Il ruolo mantiene riconoscibili conferma, uscita e azioni distruttive senza imporre icone all'app.
