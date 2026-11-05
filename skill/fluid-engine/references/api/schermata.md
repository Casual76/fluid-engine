# API · Schermata e scheletro

_Generato sull'engine 3.0.5 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidAmbient.kt`

- `data class FluidAmbient(val tone: FluidHeroTone, val motif: FluidHeroMotif, val intensity: Float = 1f, val urgent: Boolean = false, val showMotif: Boolean = true)` — Il fondale di una schermata: una lavata di colore e un motivo, e nient'altro.
- `@Composable fun FluidAmbientCanvas(ambient: FluidAmbient, modifier: Modifier = Modifier)`

## `engine-ui/ui/fluid/FluidAmbientSurface.kt`

- `@Composable fun FluidAmbientSurface(ambient: FluidAmbient?, modifier: Modifier = Modifier, content: @Composable () -> Unit)` — Un fondale solo, sotto tutta la finestra.

## `engine-ui/ui/fluid/FluidCollapsingHeader.kt`

- `class FluidTitleCollapse internal constructor(internal val morph: FluidTitleMorphState, internal val progress: State<Float>, internal val glassIntensity: State<Float>, internal val scroll: FluidCollapseScroll)` — The large title that rises into a bar of glass, for a screen that is not a [FluidScreen].
  - `val collapseProgress: Float get()` — 0 while the large title is at rest, 1 once it has docked in the bar.
- `@Composable fun rememberFluidTitleCollapse(title: String, listState: LazyListState, horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding, topBarHeight: Dp = FluidScreenDefaults.topBarHeight()): FluidTitleCollapse` — Remembers the state the title's two homes are solved against.
- `@Composable fun rememberFluidTitleCollapse(title: String, scroll: FluidCollapseScroll, horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding, topBarHeight: Dp = FluidScreenDefaults.topBarHeight()): FluidTitleCollapse` — The same, for a list that is not a `LazyColumn`.
- `interface FluidCollapseScroll` — The four things the handover needs to know about a scrolling body.
  - `val firstVisibleItemIndex: Int`
  - `val firstVisibleItemScrollOffset: Int`
  - `val hasItems: Boolean` — False before the first layout pass, when nothing about the title's position is known yet.
  - `suspend fun scrollToTop()`
- `@Composable fun rememberFluidCollapseScroll(state: LazyListState): FluidCollapseScroll` — [FluidCollapseScroll] over a `LazyListState`.
- `@Composable fun rememberFluidCollapseScroll(state: androidx.compose.foundation.lazy.grid.LazyGridState): FluidCollapseScroll` — [FluidCollapseScroll] over a `LazyGridState`, so a grid can carry the same header as a list.
- `fun Modifier.fluidTitleCollapseOrigin(collapse: FluidTitleCollapse): Modifier` — The coordinate origin both anchors are solved in. Put it on the screen's own root.
- `@Composable fun FluidCollapsingTitle(title: String, collapse: FluidTitleCollapse, subtitle: String? = null)` — The large title, to be emitted as the **first item of the caller's list**.
- `@Composable fun FluidCollapsingTopBar(title: String, collapse: FluidTitleCollapse, backdrop: GlassBackdropState, modifier: Modifier = Modifier, barTint: GlassTint? = null, onBack: (() -> Unit)? = null, titleFacets: List<String> = emptyList(), contentTranslation: () -> Float = { 0f }, onTapTitle: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {})` — The bar the title docks into: clear at the top of the page, glass once anything has passed under the status bar, and carrying the title itself once the heading has gone by.
- `@Composable fun rememberFluidTitleSnapFling(collapse: FluidTitleCollapse): FlingBehavior` — A fling that always finishes the handover.

## `engine-ui/ui/fluid/FluidColumns.kt`

- `class FluidScreenMetrics internal constructor()` — Quello che una [FluidScreen] sa della propria colonna, per chi costruisce la sua lista.
  - `var contentWidth: Dp by mutableStateOf(Dp.Unspecified)` — La larghezza della colonna di contenuto, margini esclusi; non specificata finche' non misurata.
  - `var fontScale: Float by mutableStateOf(1f)` — La scala del testo di sistema. Una colonna larga abbastanza per una riga a testo normale non lo e' piu' a 1.3: le parole cominciano ad andare a capo a meta', e tre colonne diventano tre colonne di sillabe. La misura minima cresce con il testo, e le colonne calano da sole.
  - `fun columns(minColumn: Dp = FluidColumnsDefaults.MinColumn, spacing: Dp = FluidColumnsDefaults.Spacing, maxColumns: Int = FluidColumnsDefaults.MaxColumns): Int` — Quante colonne stanno nella colonna di contenuto, larghe almeno [minColumn] a testo normale.
- `@Composable fun rememberFluidScreenMetrics(): FluidScreenMetrics`
- `object FluidColumnsDefaults`
  - `val MinColumn: Dp` — La colonna piu' stretta che regge una sezione di pagina: un gruppo lista con icona, titolo su due righe e un valore a destra. Sotto questa misura le righe vanno a capo a ogni parola.
  - `val Spacing: Dp` — Lo spazio fra due colonne: quello fra due sezioni della stessa pagina, un po' piu' largo.
  - `val GridSpacing: Dp` — Fra due card affiancate: quello fra due card impilate, cosi' la griglia ha un passo solo.
  - `val MinCard: Dp` — La card piu' stretta che regge un voto: il numero, la materia su due righe, il tipo e la data.
  - `const val MaxColumns: Int` — Oltre tre colonne una pagina smette di essere una pagina e diventa un pannello di controllo.
  - `val WideContentMaxWidth: Dp` — La misura di una pagina che si dispone in colonne.
- `fun fluidContentColumns(available: Dp, minColumn: Dp = FluidColumnsDefaults.MinColumn, spacing: Dp = FluidColumnsDefaults.Spacing, maxColumns: Int = FluidColumnsDefaults.MaxColumns): Int` — Quante colonne stanno in [available], larghe almeno [minColumn], con [spacing] fra loro.
- `fun LazyListScope.fluidColumns(key: Any, columns: Int, sections: List<FluidColumnSection>, spacing: Dp = FluidColumnsDefaults.Spacing, verticalSpacing: Dp = FluidScreenDefaults.ItemSpacing)` — Le sezioni di una pagina, in colonne quando c'e' posto e una sotto l'altra quando non c'e'.
- `fun <T> LazyListScope.fluidGridItems(items: List<T>, columns: Int, key: (T) -> Any, spacing: Dp = FluidColumnsDefaults.GridSpacing, contentType: Any? = null, itemContent: @Composable (T) -> Unit)` — Card dello stesso genere — voti, materie, file — in righe da [columns], dentro una lista pigra.
- `class FluidColumnSection(val key: Any, val contentType: Any? = null, val content: @Composable () -> Unit)` — Una sezione per [fluidColumns]: una chiave stabile e il suo contenuto.
- `@Composable fun FluidMasonry(columns: Int, modifier: Modifier = Modifier, horizontalSpacing: Dp = FluidColumnsDefaults.Spacing, verticalSpacing: Dp = FluidScreenDefaults.ItemSpacing, content: @Composable () -> Unit)` — Figli disposti a muratura: ognuno nella colonna in quel momento piu' corta, a parita' la prima.

## `engine-ui/ui/fluid/FluidContainerScaffold.kt`

- `@Composable fun FluidContainerScaffold(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, hero: @Composable ColumnScope.() -> Unit, secondary: @Composable ColumnScope.() -> Unit = {}, ambient: FluidAmbient? = null)` — Opaque, bounded destination for row-to-detail navigation. On a screen with an ambient canvas the group that holds the content is a pane of glass (the material of `FluidListGroup(glass = true)`), on any other it is the solid container it has always been.

## `engine-ui/ui/fluid/FluidFastScroller.kt`

- `interface FluidFastScrollTarget` — Cio' che lo scorrimento rapido deve sapere di una lista, e saperla muovere.
  - `val totalItems: Int` — Quanti elementi ha la lista.
  - `val visibleItems: Int` — Quanti se ne vedono adesso (anche in parte).
  - `val firstIndex: Int` — L'indice del primo elemento visibile.
  - `val fractionalFirstIndex: Float` — `firstIndex` piu' la frazione (0..1) del primo elemento che e' gia' scorsa fuori: continuo, non a gradini.
  - `val isScrollInProgress: Boolean` — Vero mentre la lista si muove (un dito, uno scatto, una molla).
  - `suspend fun scrollToIndex(index: Int)` — Porta [index] in cima. Sospende; una chiamata nuova interrompe la precedente.
- `@Composable fun rememberFluidFastScrollTarget(state: LazyListState): FluidFastScrollTarget` — Un [FluidFastScrollTarget] su una `LazyColumn` o `LazyRow`. Una per stato.
- `@Composable fun rememberFluidFastScrollTarget(state: LazyGridState): FluidFastScrollTarget` — Un [FluidFastScrollTarget] su una `LazyVerticalGrid`. Una per stato.
- `object FluidFastScroll` — I conti dello scorrimento rapido, puri (`FluidFastScrollTest`): dove sta la maniglia, dove porta il dito, quando c'e'.
  - `fun maxIndex(totalItems: Int, visibleItems: Int): Int` — L'ultimo indice che puo' stare in cima: oltre, la lista e' finita e gli elementi visibili sono gli ultimi. Zero se tutta la lista sta a schermo (non c'e' niente da scorrere).
  - `fun fractionalFirstIndex(firstIndex: Int, firstOffsetPx: Int, firstSizePx: Int): Float` — L'indice del primo elemento con la parte che e' gia' scorsa: 3 elementi e un quarto sono 3,25. Il quarto e' [firstOffsetPx] su [firstSizePx], tenuto fra 0 e 1 (un elemento di altezza zero non ne ha).
  - `fun fraction(fractionalFirstIndex: Float, totalItems: Int, visibleItems: Int): Float` — Dov'e' la lista, 0 in cima e 1 in fondo. Zero se non c'e' niente da scorrere.
  - `fun indexForFraction(fraction: Float, totalItems: Int, visibleItems: Int): Int` — L'indice a cui porta una frazione: l'inversa di [fraction], arrotondata, mai oltre [maxIndex].
  - `fun scrubbedFraction(startFraction: Float, draggedPx: Float, travelPx: Float): Float` — La frazione sotto il dito: da dov'era la maniglia quando e' stata presa, di quanto si e' mosso il dito su [travelPx] di corsa. **Relativa**, non assoluta: la maniglia non salta sotto il dito all'atto di prenderla (si prende per il bordo, e il bordo non e' il centro), e un dito che esce dalla pista e rientra non la lascia indietro.
  - `fun travel(trackPx: Float, thumbPx: Float): Float` — Quanto puo' scendere la maniglia: la pista meno lei, mai meno di un pixel.
  - `fun isScrollable(totalItems: Int, visibleItems: Int, minItems: Int): Boolean` — Se la lista e' abbastanza lunga da meritare la maniglia: scorre davvero, e ha almeno [minItems] elementi.
  - `fun labelCrossed(previous: String?, next: String?): Boolean` — Se il dito ha attraversato un'etichetta: [next] c'e' ed e' diversa da quella di prima. Cio' che suona il tick.
- `object FluidFastScrollerDefaults` — Le misure dello scorrimento rapido.
  - `const val MinItems: Int` — Sotto questo numero di elementi la lista si scorre a mano: la maniglia sarebbe arredamento.
  - `val ThumbWidth: Dp` — La maniglia: una goccia alta quanto un pollice.
  - `val ThumbHeight: Dp`
  - `val EdgeInset: Dp` — Quanto la maniglia sta lontana dal bordo.
  - `val BubbleGap: Dp` — Fra la maniglia e la bolla.
  - `const val PressedScale: Float` — Quanto la maniglia cresce sotto il dito (la goccia: scala e lente dalla ricetta).
  - `val AccessibilityStrip: Dp` — La striscia che lo scorrimento rapido offre a TalkBack, sempre, anche con la maniglia nascosta.
- `@Composable fun FluidFastScroller(target: FluidFastScrollTarget, label: (index: Int) -> String?, modifier: Modifier = Modifier, backdrop: GlassBackdropState? = null, minItems: Int = FluidFastScrollerDefaults.MinItems, contentDescription: String = "Scorrimento rapido")` — Lo **scorrimento rapido**: una maniglia di vetro sul bordo di una lista lunga che la porta in un colpo dove si vuole, con una bolla che dice dov'e' si e' arrivati.
- `@Composable fun FluidFastScroller(state: LazyListState, label: (index: Int) -> String?, modifier: Modifier = Modifier, backdrop: GlassBackdropState? = null, minItems: Int = FluidFastScrollerDefaults.MinItems, contentDescription: String = "Scorrimento rapido")` — [FluidFastScroller] su una `LazyColumn` o `LazyRow`.
- `@Composable fun FluidFastScroller(state: LazyGridState, label: (index: Int) -> String?, modifier: Modifier = Modifier, backdrop: GlassBackdropState? = null, minItems: Int = FluidFastScrollerDefaults.MinItems, contentDescription: String = "Scorrimento rapido")` — [FluidFastScroller] su una `LazyVerticalGrid`.

## `engine-ui/ui/fluid/FluidGridColumns.kt`

- `fun fluidGridColumns(available: Dp, minItem: Dp = 150.dp, spacing: Dp = 12.dp, maxColumns: Int = 5): Int` — Quante colonne stanno in [available] dp, con tessere larghe almeno [minItem].

## `engine-ui/ui/fluid/FluidListDetail.kt`

- `data class FluidListDetailSizes(val listMin: Dp = 360.dp, val listMax: Dp = 500.dp, val listFraction: Float = 0.4f, val detailMin: Dp = 480.dp)` — Le misure di una pagina elenco+dettaglio.
- `data class FluidListDetailLayout(val twoPane: Boolean, val listWidth: Dp, val detailWidth: Dp)` — Cosa sta in scena in una pagina elenco+dettaglio: uno o due pannelli, e quanto larghi.
- `fun fluidListDetailLayout(available: Dp, sizes: FluidListDetailSizes = FluidListDetailSizes()): FluidListDetailLayout` — Quanto e' largo l'elenco in [available] dp, e se accanto c'e' posto per il dettaglio.
- `@Composable fun FluidListDetailScaffold(ambient: FluidAmbient?, modifier: Modifier = Modifier, sizes: FluidListDetailSizes = FluidListDetailSizes(), list: @Composable (twoPane: Boolean) -> Unit, detail: @Composable () -> Unit)` — Una pagina che elenca cose e ne mostra una: su un telefono l'elenco e basta, su uno schermo largo l'elenco a sinistra e la cosa scelta accanto.
- `@Composable fun <T> FluidDetailContent(item: T, modifier: Modifier = Modifier, key: (T) -> Any? = { it }, order: (T) -> Int? = { null }, content: @Composable (T) -> Unit)` — Il contenuto del pannello di dettaglio, che cambia quando si sceglie un'altra cosa.
- `@Composable fun FluidDetailPlaceholder(title: String, modifier: Modifier = Modifier, message: String? = null, icon: ImageVector? = null)` — Il pannello di dettaglio quando non c'e' niente di scelto.

## `engine-ui/ui/fluid/FluidOverscroll.kt`

- `@Composable fun rememberFluidEdgeOverscroll(canScroll: () -> Boolean = { true }): FluidEdgeOverscrollState` — The elastic edge, for a screen that is not a [FluidScreen].
- `fun Modifier.fluidOverscrollEdge(state: FluidEdgeOverscrollState): Modifier` — Put on the scrolling container: it takes what the list could not use, and learns how tall the viewport is so the give is a fraction of it rather than a fixed number of pixels.
- `fun Modifier.fluidOverscrollContent(state: FluidEdgeOverscrollState): Modifier` — Put on what should actually move — and only on that.

## `engine-ui/ui/fluid/FluidPaneScaffold.kt`

- `enum class FluidPaneRole` — Il ruolo di un pannello: chi elenca, chi mostra, chi sta di lato.
- `val LocalFluidPaneRole: ProvidableCompositionLocal<FluidPaneRole?>` — Dentro quale pannello si sta componendo, o null fuori da un [FluidPaneScaffold].
- `object FluidPaneDefaults`
  - `val MediumBreakpoint: Dp` — Da qui in su c'e' posto per il rail al fianco di una pagina.
  - `val ExpandedBreakpoint: Dp` — Da qui in su c'e' posto per due pannelli affiancati, e per la barra laterale.
- `data class FluidPaneSizes(val side: Dp = 280.dp, val list: Dp = 380.dp, val detailMin: Dp = 420.dp, val rail: Dp = FluidTabBarDefaults.RailWidth + FluidTabBarDefa…)` — Le misure dei pannelli: quelle fisse, e il minimo sotto cui il dettaglio non si comprime.
- `data class FluidPaneLayout(val panes: List<FluidPaneRole>, val showRail: Boolean, val railWidth: Dp, val sideWidth: Dp, val listWidth: Dp, val detailWidth: Dp, val splits: Boolean = false)` — Cosa sta in scena e quanto e' largo. Solo i pannelli elencati in [panes] vengono emessi: un pannello nascosto non e' un pannello largo zero, e' un pannello che non c'e', cosi' la sua `FluidScreen` non registra un fondale che nessuno vede.
  - `val showSide: Boolean get()`
  - `val showList: Boolean get()`
  - `val showDetail: Boolean get()`
- `fun fluidPaneLayout(available: Dp, sizes: FluidPaneSizes = FluidPaneSizes(), hasSide: Boolean = false, hasRail: Boolean = false, showDetail: Boolean = false): FluidPaneLayout` — Quali pannelli stanno in [available] dp, e quanto larghi.
- `@Composable fun FluidPaneScaffold(layout: FluidPaneLayout, list: @Composable () -> Unit, detail: @Composable () -> Unit, modifier: Modifier = Modifier, side: (@Composable () -> Unit)? = null, rail: (@Composable () -> Unit)? = null)` — I pannelli di una pagina larga, affiancati.

## `engine-ui/ui/fluid/FluidReadingWidth.kt`

- `fun fluidScreenPadding(available: Dp, horizontalPadding: Dp, contentMaxWidth: Dp): Dp` — Il margine laterale di una pagina, data la sua larghezza.
- `fun Modifier.fluidReadingWidth(max: Dp = FluidScreenDefaults.ContentMaxWidth): Modifier` — Tiene questo elemento alla larghezza di lettura, centrato, per chi non passa da [FluidScreen]: un overlay, un pannello, il corpo di una pagina intera.

## `engine-ui/ui/fluid/FluidScreen.kt`

- `class FluidChromeController internal constructor(private val bottomBarVelocityThresholdPx: Float, private val densityPx: Float)`
  - `val activeBackdrop: State<GlassBackdropState?>`
  - `val bottomBarOffsetPx: State<Float>`
  - `fun refreshFront(): Boolean` — Aggiorna la pagina che sta davanti, la stessa che la barra delle schede rifrange: e' quello che fa Ctrl+R su una tastiera fisica, cioe' il gesto di tirare giu' la pagina senza toccarla.
  - `fun updateBottomBarTravel(travelPx: Float)`
  - `fun onBottomBarScroll(availableY: Float)`
  - `fun revealBottomBar()`
  - `suspend fun settleBottomBar(velocityY: Float)`
- `@Composable fun rememberFluidChromeController(): FluidChromeController`
- `@Composable fun rememberFluidChromeScrollConnection(controller: FluidChromeController, enabled: Boolean): NestedScrollConnection`
- `val LocalFluidChromeController: ProvidableCompositionLocal<FluidChromeController?>`
- `class FluidScrollToTopBus` — Lets the shell ask whichever screen is on top to return to its top.
  - `fun request()`
- `val LocalFluidScrollToTop: ProvidableCompositionLocal<FluidScrollToTopBus?>`
- `val LocalFluidBottomInset: ProvidableCompositionLocal<Dp>` — Room a screen must leave at the bottom for the floating tab bar. Provided by the app shell so no screen has to know whether it is currently hosted under one.
- `val LocalFluidItemSpacing` — Quanto spazio questa schermata mette fra due item della sua lista.
- `object FluidScreenDefaults`
  - `val ControlRowHeight: Dp` — Height of the control row of the bar, matching the 44pt UIKit navigation bar.
  - `val ShieldDeadZone: Dp` — Initial travel kept optically clear, so a tiny touch does not flash the material on.
  - `val ShieldRampDistance: Dp` — Travel after the dead zone over which the top material reaches full strength.
  - `val GlassFadeTail: Dp` — Soft tail below the 44 dp control row; it removes the hard edge of a rectangular top bar.
  - `val CompactTitleInset: Dp` — Room the docked title leaves on each side for bar buttons. Wider than a bar button because the compact title is now large enough that a near miss reads as a collision.
  - `val HorizontalPadding: Dp`
  - `val ContentMaxWidth: Dp` — Quanto puo' allargarsi il contenuto di una pagina prima che il margine cresca al posto suo.
  - `val ItemSpacing: Dp`
  - `val TitleTopSpacing: Dp`
  - `val RefreshTrigger: Dp` — How far the top edge must be pulled open before releasing it starts a refresh.
  - `val RefreshHold: Dp` — How much of that stays open while the refresh runs, holding the indicator in view.
  - `val TitleBottomSpacing: Dp`
  - `val ContentBottomSpacing: Dp`
  - `@Composable fun topBarHeight(): Dp` — Total height of the top chrome, including the status bar it sits under.
  - `@Composable fun bottomContentPadding(extra: Dp = 0.dp): Dp` — Bottom padding a scrolling body needs so its last item clears the system and tab bars.
- `@Composable fun FluidScreen(title: String, modifier: Modifier = Modifier, subtitle: String? = null, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}, titleFacets: List<String> = emptyList(), listState: LazyListState = rememberLazyListState(), isRefreshing: Boolean = false, onRefresh: (() -> Unit)? = null, horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding, contentMaxWidth: Dp = FluidScreenDefaults.ContentMaxWidth, itemSpacing: Dp = FluidScreenDefaults.ItemSpacing, extraBottomPadding: Dp = 0.dp, ambient: FluidAmbient? = null, overlay: @Composable BoxScope.(GlassBackdropState) -> Unit = {}, metrics: FluidScreenMetrics? = null, content: LazyListScope.() -> Unit)` — An edge-to-edge screen in the shape Apple gives its own: content runs the full height of the display, a large title sits at the top of the scroll as ordinary content, and a compact bar of frosted glass materialises over it only once the title has scrolled underneath.
- `@Composable fun FluidBarAction(icon: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, actions: (() -> List<FluidContextAction>)? = null, opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress)` — An action in the top bar. It is a draw-only lens over the bar's shared backdrop, so every action gets a physical rim without paying for another blur pass.
- `@Composable fun ProvideFluidChrome(controller: FluidChromeController, bottomInset: Dp, scrollToTop: FluidScrollToTopBus, content: @Composable () -> Unit)` — Provides the chrome registry, tab-bar allowance and scroll-to-top bus to every screen below.

## `engine-ui/ui/fluid/FluidScrollPhysics.kt`

- `class FluidEdgeOverscrollState(private val reducedMotion: Boolean, private val canScroll: () -> Boolean = { true }) : NestedScrollConnection` — One elastic edge for both ends of a scrolling body, and the gesture that refreshes it.
  - `var offsetPx by mutableFloatStateOf(0f)` — Positive pushes content down (top edge), negative pushes it up (bottom edge).
  - `var refreshTriggerPx: Float` — Zero disables the gesture entirely, for screens that have nothing to refresh.
  - `var refreshHoldPx: Float`
  - `var onRefresh: (() -> Unit)?`
  - `val refreshPull: Float` — How close the pull is to arming a refresh, from 0 to 1. Drives the indicator's own reveal.
  - `fun updateViewport(heightPx: Float)`
  - `suspend fun endRefresh()` — Called when the work the gesture started has finished, so the opened space can close.

## `engine-ui/ui/fluid/FluidSectionIndex.kt`

- `data class FluidSectionAnchor(val key: String, val label: String, val itemIndex: Int)` — A semantic destination in a long, sectioned [androidx.compose.foundation.lazy.LazyColumn].
- `enum class FluidSectionSelectionMotion` — Whether a section selection should settle spatially or follow a finger without queueing motion.
- `@Composable fun FluidSectionIndex(sections: List<FluidSectionAnchor>, activeSectionKey: String?, onSelectSection: (FluidSectionAnchor, FluidSectionSelectionMotion) -> Unit, modifier: Modifier = Modifier, visible: Boolean = true, maxVisibleAnchors: Int = FluidSectionIndexDefaults.MaxVisibleAnchors, backdrop: GlassBackdropState? = null)` — A draggable section index for semantic jumps such as weekdays or months.
- `object FluidSectionIndexDefaults`
  - `const val MaxVisibleAnchors: Int`
  - `val TouchWidth`
  - `val MarkSize`
  - `val ActiveMarkWidth`
  - `val MarkSpacing`
  - `val RestBarWidth` — La barretta a riposo.
  - `val RestBarHeight`
  - `const val RestTrackFraction: Float` — Quanta altezza percorre la barretta a riposo, e i limiti entro cui resta leggibile.
  - `val RestTrackMinHeight`
  - `val RestTrackMaxHeight`
  - `const val ExpandedTrackFraction: Float` — Quanto si apre il nastro quando lo tieni premuto.
  - `val ExpandedTrackMinHeight`
  - `val ExpandedTrackMaxHeight`
  - `val EdgePadding`
  - `val MinTouchWidth` — Narrowest the rail's hit target may be, however thin the marks it is resting as.
  - `val HitMargin` — Quanto margine sopra e sotto la barretta e' ancora "la barretta", per un pollice.
  - `val ExpandedMargin` — How far the unfolded rail stays from the strip's vertical ends.
  - `val ExpandedMarkInset` — Inset of the first and last station inside the unfolded rail.
  - `val ExpandedTrackWidth` — Width of the unfolded rail.
  - `val LensSize` — Diameter of the lens that rides the rail.
- `fun activeFluidSectionForItemIndex(sections: List<FluidSectionAnchor>, firstVisibleItemIndex: Int): FluidSectionAnchor?`

## `engine-ui/ui/fluid/FluidSidebar.kt`

- `object FluidSidebarDefaults`
  - `val Margin: Dp` — L'aria fra la barra e i bordi della finestra: galleggia, non e' incollata.
  - `val RowHeight: Dp`
- `@Composable fun FluidSidebar(backdrop: GlassBackdropState, modifier: Modifier = Modifier, header: (@Composable ColumnScope.() -> Unit)? = null, footer: (@Composable ColumnScope.() -> Unit)? = null, content: LazyListScope.() -> Unit)` — La barra laterale di una pagina larga: le sezioni dell'app e le sue cose di primo livello, in una colonna di vetro.
- `@Composable fun FluidSidebarSection(title: String, modifier: Modifier = Modifier)` — Il titolo di un gruppo di righe nella barra laterale: «Materie», «Altro».
- `@Composable fun FluidSidebarRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, tint: Color? = null, detail: String? = null, badge: String? = null, contextActions: (() -> List<FluidContextAction>)? = null)` — Una riga della barra laterale: una sezione dell'app, o una materia con la sua piastrella.
