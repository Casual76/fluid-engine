# API · Pagine e navigazione

_Generato sull'engine 3.1.1 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/navigation/FluidNavHost.kt`

- `@Composable fun rememberFluidNavController(vararg navigators: Navigator<out NavDestination>): NavHostController` — Un `NavHostController` con il navigator delle pagine Fluid gia' dentro.
- `@Composable fun FluidNavHost(navController: NavHostController, startDestination: String, modifier: Modifier = Modifier, route: String? = null, peer: FluidPeerMove<NavBackStackEntry>? = null, state: FluidPageHostState = rememberFluidPageHostState(), pageColor: Color = MaterialTheme.colorScheme.background, builder: NavGraphBuilder.() -> Unit)` — `NavHost` con le pagine Fluid: il NavController resta quello (deep link, stato salvato, ViewModel per voce, `savedStateHandle`, `navigation { }` annidati, `dialog { }`), il movimento e' quello di [dev.antigravity.fluidengine.ui.page.FluidPageHost].
- `@Composable fun FluidNavHost(navController: NavHostController, startDestination: Any, modifier: Modifier = Modifier, route: KClass<*>? = null, typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(), peer: FluidPeerMove<NavBackStackEntry>? = null, state: FluidPageHostState = rememberFluidPageHostState(), pageColor: Color = MaterialTheme.colorScheme.background, builder: NavGraphBuilder.() -> Unit)` — Come sopra, con rotte tipizzate.
- `@Composable fun FluidNavHost(navController: NavHostController, graph: NavGraph, modifier: Modifier = Modifier, peer: FluidPeerMove<NavBackStackEntry>? = null, state: FluidPageHostState = rememberFluidPageHostState(), pageColor: Color = MaterialTheme.colorScheme.background)` — Con un grafo gia' costruito. Il controller deve avere il navigator delle pagine prima della costruzione del grafo: [rememberFluidNavController].

## `engine-ui/ui/navigation/FluidPageNavigator.kt`

- `class FluidPageNavigator : Navigator<FluidPageNavigator.Destination>()` — Il navigator delle pagine Fluid: fa per [FluidNavHost] quello che `ComposeNavigator` fa per `NavHost`, con le stesse API pubbliche di navigation (`NavigatorState`), e niente di interno.
  - `val backStack: StateFlow<List<NavBackStackEntry>>` — Le voci di questo navigator nel back stack, dalla radice alla cima (anche nei grafi annidati).
  - `val transitionsInProgress: StateFlow<Set<NavBackStackEntry>>` — Le voci che stanno entrando o uscendo: restano vive finche' l'host non le dichiara ferme.
  - `fun prepareForTransition(entry: NavBackStackEntry)` — Un gesto comincia: le due voci restano almeno STARTED finche' il gesto non si chiude.
  - `fun onTransitionComplete(entry: NavBackStackEntry)` — L'host e' a riposo: la voce va al suo stato finale (RESUMED in cima, CREATED sotto, distrutta fuori).
  - `class Destination(@Suppress("UNUSED_PARAMETER") navigator: FluidPageNavigator, internal val content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit) : NavDestination(NAME)` — Una pagina Fluid nel grafo.
    - `var transition: FluidPageTransition`
    - `var backSwipe: FluidBackSwipe`
  - `companion object`
    - `const val NAME` — Il nome con cui il navigator sta nel `NavigatorProvider`.
- `class FluidPageDestinationBuilder : NavDestinationBuilder<FluidPageNavigator.Destination>` — Il builder di una pagina Fluid, per stringa o per classe (rotte tipizzate).
  - `var transition: FluidPageTransition`
  - `var backSwipe: FluidBackSwipe`
- `fun NavGraphBuilder.composable(route: String, arguments: List<NamedNavArgument> = emptyList(), deepLinks: List<NavDeepLink> = emptyList(), transition: FluidPageTransition = FluidPageTransition.Slide, backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly, content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit)` — Una pagina Fluid nel grafo, per rotta stringa.
- `fun <T : Any> NavGraphBuilder.composable(route: KClass<T>, typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(), deepLinks: List<NavDeepLink> = emptyList(), transition: FluidPageTransition = FluidPageTransition.Slide, backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly, content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit)` — Una pagina Fluid nel grafo, per rotta tipizzata (la classe della rotta, serializzabile).
- `inline fun <reified T : Any> NavGraphBuilder.composable(typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(), deepLinks: List<NavDeepLink> = emptyList(), transition: FluidPageTransition = FluidPageTransition.Slide, backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly, noinline content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit)` — Una pagina Fluid nel grafo, per rotta tipizzata: `composable<Dettaglio> { entry -> ... }`.

## `engine-ui/ui/navigation/FluidPeerNavigation.kt`

- `fun NavHostController.navigateToPeer(route: String, builder: NavOptionsBuilder.() -> Unit = {})` — Passa alla scheda [route] tenendo **una pila sola** (3.0.1): dopo il passo la pila e' `[route]`, e un back da una scheda esce dall'app invece di attraversare le schede viste prima.
- `fun <T : Any> NavHostController.navigateToPeer(route: T, builder: NavOptionsBuilder.() -> Unit = {})` — Come sopra, con le rotte tipizzate (`@Serializable`).

## `engine-ui/ui/page/FluidPageAnchoredBar.kt`

- `@Composable fun FluidPageChrome(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, backdrop: GlassBackdropState? = null, barTint: GlassTint? = null, actions: @Composable RowScope.() -> Unit = {})` — La barra di una pagina che non e' una `FluidScreen` (3.0): titolo agganciato, tasto indietro, azioni, e il vetro se c'e' una registrazione da rifrangere ([backdrop]).

## `engine-ui/ui/page/FluidPageHost.kt`

- `interface FluidPageScope` — Quello che una pagina sa di se' dentro l'host.
  - `val transition: FluidPageHostState` — Lo stato dell'host: che movimento c'e', quanto, se c'e' un dito.
  - `val isCovered: Boolean` — Questa pagina e' quella sotto, coperta, a riposo: composta ma non disegnata.
  - `val isFront: Boolean` — Questa pagina e' quella davanti adesso (passa di mano a meta' uscita).
- `val LocalFluidPageTransition: ProvidableCompositionLocal<FluidPageHostState?>` — L'host di pagine piu' vicino, o null fuori da un host.
- `@Composable fun <K : Any> FluidPageHost(stack: List<K>, onBack: () -> Unit, modifier: Modifier = Modifier, transition: (K) -> FluidPageTransition = { FluidPageTransition.Slide }, backSwipe: (K) -> FluidBackSwipe = { FluidBackSwipe.EdgeOnly }, peer: FluidPeerMove<K>? = null, onTransitionStart: (List<K>) -> Unit = {}, onSettled: () -> Unit = {}, leaving: Set<K>? = null, state: FluidPageHostState = rememberFluidPageHostState(), pageColor: Color = MaterialTheme.colorScheme.background, content: @Composable FluidPageScope.(K) -> Unit)` — Un host di pagine: la pila che riceve diventa due strati opachi, quello sopra e quello sotto, e ogni cambio diventa un movimento con una molla. Non sa niente di navigation: per un `NavController` c'e' `FluidNavHost`, che lo usa.

## `engine-ui/ui/page/FluidPageHostState.kt`

- `class FluidPageHostState internal constructor()` — Lo stato di un [FluidPageHost]: cosa si sta muovendo, quanto, e se c'e' un dito.
  - `var kind: FluidPageMove by mutableStateOf(FluidPageMove.None)` — Che movimento c'e' adesso.
  - `var isInteractive: Boolean by mutableStateOf(false)` — Il dito tiene la pagina (in-app o col gesto di sistema).
  - `var from: Any? by mutableStateOf<Any?>(null)` — La pagina che sta lasciando il davanti (la chiave della pila), o null a riposo.
  - `var to: Any? by mutableStateOf<Any?>(null)` — La pagina che sta prendendo il davanti, o null a riposo.
  - `val progress: Float` — Quanto del movimento da [from] a [to] e' fatto: 0 all'inizio, 1 alla fine, 0 a riposo. Un back annullato torna verso 0.
  - `val velocity: Float get()` — La velocita' della pagina sopra lungo l'uscita, in px/s (positiva = verso fuori), mentre una molla la muove. Al primo fotogramma dopo il rilascio e' quella del dito.
  - `val isMoving: Boolean get()` — Se qualcosa si muove (un back annullato che torna compreso).
  - `fun fractionToward(isHidden: (Any?) -> Boolean): Float` — Quanto si e' andati verso le pagine per cui [isHidden] e' vero (3.0.1): 0 su una pagina dove una cosa c'e', 1 su una dove non c'e', e in mezzo mentre le pagine si muovono, al passo di [progress] — un back a dito compreso, che puo' tornare indietro.
- `@Composable fun rememberFluidPageHostState(): FluidPageHostState` — Uno stato per host, ricordato.

## `engine-ui/ui/page/FluidPageMotion.kt`

- `data class FluidPageFrame(val translation: Float, val scale: Float)` — Dove sta la pagina sopra in un fotogramma: spostamento lungo l'uscita (px, ≥ 0) e scala.
- `data class FluidPageCorners(val topLeft: Float = 0f, val topRight: Float = 0f, val bottomRight: Float = 0f, val bottomLeft: Float = 0f)` — I raggi della carta, in px, in coordinate assolute (non start/end).
  - `val isZero: Boolean get()`
  - `companion object`
    - `val Zero`
- `enum class FluidSwipeEdge` — Da quale bordo e' partito il gesto di sistema.
- `enum class FluidStackChangeKind` — Che cosa e' cambiato fra due pile.
- `data class FluidStackChange<K>(val kind: FluidStackChangeKind, val from: K?, val to: K?, val direction: Int = 0, val skipped: Int = 0)`
- `object FluidPageMotion` — Costanti e funzioni pure delle pagine. **Un orologio solo**: `travel`, i pixel che la pagina sopra ha percorso verso l'uscita; ogni cosa che si vede e' una funzione di travel, cosi' il dito, la molla e il tasto indietro disegnano la stessa cosa nello stesso punto.
  - `const val BelowParallaxFraction: Float` — Quanto arretra la pagina sotto, in frazione della larghezza (LibrePods SwipeBack.kt:209-210).
  - `const val EdgeZoneDp: Float` — La zona del bordo che reclama il dito prima dei figli (navigazione a 3 tasti).
  - `const val CommitFraction: Float` — Oltre questa frazione della larghezza un rilascio lento conferma (LibrePods: 0,35 W).
  - `const val CommitCapDp: Float`
  - `const val FlingDpPerSecond: Float` — La spinta che decide da sola, in dp/s. LibrePods usa 1000 px/s, ≈ 380 dp/s sul suo telefono: in dp la soglia vale uguale su ogni densita'. Una spinta all'indietro della stessa forza annulla anche se la pagina e' gia' oltre la meta': il dito ha cambiato idea, e la molla gli da' ragione.
  - `const val ShrinkTravelFraction: Float` — [FluidPageTransition.ShrinkSlide]: il primo 30% della corsa rimpicciolisce e basta.
  - `const val ShrinkAmount: Float` — Quanto rimpicciolisce: fino al 90%.
  - `const val ShrinkExitFraction: Float` — Dove finisce la corsa di ShrinkSlide ed Expand, in larghezze. Rimpicciolita al 90% e spostata di 0,95 W, la pagina ha il bordo sinistro esattamente sul bordo destro dello schermo.
  - `const val ExpandInitialScale: Float` — Da dove parte [FluidPageTransition.Expand]: la stessa scala che aveva la crescita di rotta.
  - `const val CardElevationDp: Float` — L'ombra della carta (LibrePods StyledScaffold.kt:227-241).
  - `const val CardThresholdPx: Float` — Sotto mezzo pixel di corsa la pagina e' a riposo: niente angoli, niente ombra. La carta esiste solo mentre si muove, cosi' a riposo il layer non ritaglia niente.
  - `const val SystemStillPx: Float` — Il dito del gesto di sistema e' fermo se non si allontana di tanto dal punto in cui si e' fermato.
  - `const val SystemCancelFalls: Int` — Quanti eventi di fila, col dito fermo, il progresso deve scendere prima di dire "annullato" (3.0.4).
  - `const val FrontHandoff: Float` — La pagina sopra resta "davanti" (vetro, barra) fino a meta' della sua uscita.
  - `const val ResponsePageSeconds: Double` — La risposta della molla delle pagine, in secondi: fra `standard` (0,38) e `snappy` (0,30). Una pagina e' la cosa piu' grande che si muove, ma un push non deve sembrare una tenda.
  - `val ResponsePage: Float`
  - `const val VisibilityThresholdPx: Float` — Le molle lavorano in pixel: sotto mezzo pixel il movimento e' finito.
  - `fun page(): SpringSpec<Float>` — Push, pop, tap-back, conferma del dito. Criticamente smorzata: una pagina non rimbalza mai.
  - `fun pageReturn(): SpringSpec<Float>` — Il ritorno di un back annullato: piu' corto, la pagina "torna a posto".
  - `fun exitTravel(transition: FluidPageTransition, width: Float): Float` — Quanta corsa serve perche' la pagina sopra sia del tutto fuori.
  - `fun exitFraction(transition: FluidPageTransition, travel: Float, width: Float): Float` — La frazione d'uscita, 0 a riposo e 1 fuori. Larghezza zero: gia' fuori.
  - `fun inAppTravel(fingerX: Float, downX: Float, direction: Int, startTravel: Float = 0f): Float` — Il trascinamento in-app: la distanza del dito da dove si e' posato, verso l'uscita, dal fotogramma in cui supera la soglia di movimento (non da li': il dito e il bordo della pagina restano alla stessa distanza che avevano al contatto). [startTravel] e' la corsa che la pagina aveva quando il dito l'ha ripresa a meta' di un annullo.
  - `fun systemDirection(edge: FluidSwipeEdge, layoutDirection: Int): Int` — Il verso dell'uscita col gesto di sistema: quello del dito (dal bordo destro, a sinistra).
  - `fun systemAnchor(firstTouchX: Float, edge: FluidSwipeEdge, hostLeft: Float, hostRight: Float, windowWidth: Float, tolerancePx: Float = 1f): Float` — Il punto da cui si misura il dito del gesto di sistema.
  - `fun systemTravel(touchX: Float, anchor: Float, direction: Int, exitTravel: Float): Float` — La corsa dal gesto di sistema: il dito meno l'ancora, verso l'uscita, mai oltre la fine.
  - `fun inferSystemCancel(falls: Int, reach: Float, farthest: Float): Boolean` — Il sistema annulla un back con ~200 ms di ritardo: prima riporta il suo `progress` a zero con un'animazione sua, a dito gia' alzato, poi chiama l'annullo. Per non lasciare la pagina ferma un decimo di secondo e piu' l'annullo si deduce prima: il progresso e' sceso [falls] eventi di fila (almeno [SystemCancelFalls]) col dito fermo **nel punto piu' lontano del gesto** ([reach], il `touchX` nel verso dell'uscita, non so…
  - `fun backRelease(travel: Float, velocity: Float, width: Float, density: Float): Boolean` — Il rilascio del dito: confermare o tornare.
  - `fun pageFrame(transition: FluidPageTransition, travel: Float, width: Float, opening: Boolean = false): FluidPageFrame` — La pagina sopra, in funzione della corsa.
  - `fun belowParallax(exitFraction: Float, width: Float): Float` — La pagina sotto: −(W/3)·(1 − e), lungo l'uscita (il chiamante moltiplica per il verso).
  - `fun belowOffset(transition: FluidPageTransition, exitFraction: Float, width: Float): Float` — La pagina sotto per [transition]. Con [FluidPageTransition.Slide] arretra di un terzo ([belowParallax]). Quando la pagina sopra si rimpicciolisce ([FluidPageTransition.ShrinkSlide], [FluidPageTransition.Expand]) sta ferma: una pagina sopra piu' piccola dello schermo lascia scoperte due strisce, sopra e sotto, e una pagina sotto spostata di lato le lascerebbe vuote — lo sfondo nero della finestra in alto e in basso a…
  - `fun pageIsFront(exitFraction: Float, isUpper: Boolean): Boolean` — Chi e' davanti (vetro della barra, sorgente della chrome): passa di mano a meta' uscita.
  - `fun peerFrame(time: Float, eased: Float, width: Float, direction: Int): Triple<Float, Float, Float>` — Il passo fra schede, invariato: la pagina che arriva sta sopra, opaca, e percorre il 13% della larghezza; quella che esce va dall'altra parte dello stesso tanto e sfuma nei suoi 150 ms.
  - `fun pageCardRadii(hostLeft: Float, hostTop: Float, hostRight: Float, hostBottom: Float, windowWidth: Float, windowHeight: Float, display: FluidPageCorners, tolerancePx: Float = 1f): FluidPageCorners` — Gli angoli della carta: quelli del telefono, ma solo dove l'host tocca davvero un angolo dello schermo. Un pannello di dettaglio a destra ha gli angoli del telefono a destra e retti a sinistra; un host sotto una barra di sistema opaca non ne ha in alto. Senza i raggi del display (sotto API 31, o un display rettangolare) la carta ha angoli retti e resta l'ombra.
  - `fun <K> classifyStackChange(old: List<K>, new: List<K>, peer: ((K, K) -> Int)? = null): FluidStackChange<K>` — Che cosa e' successo alla pila (LibrePods SwipeBack.kt:56-60: indietro se la cima c'era gia').
  - `const val BarTitleShiftDp: Float` — Quanto si sposta di lato un titolo agganciato mentre le pagine cambiano.
  - `fun barMorph(e: Float): Float` — Il morph dei tasti di vetro della barra in funzione della frazione d'uscita [e] della pagina sopra: fermo nel primo e nell'ultimo 10% (il dito appena posato non deve gia' deformare i tasti), poi una smoothstep. 0 = i tasti della pagina sopra, 1 = quelli della pagina sotto.
  - `fun barIconAlpha(m: Float, ofLower: Boolean): Float` — Le icone sopra i pezzi che viaggiano: quelle della pagina sopra se ne vanno presto (1 − m/0,35), quelle della pagina sotto arrivano tardi ((m − 0,25)/0,5). Le due non sono mai leggibili insieme, in entrambi i versi del viaggio.
  - `fun barTitleFrame(e: Float, isUpper: Boolean, forward: Boolean, shiftPx: Float, direction: Int): Pair<Float, Float>` — Il titolo agganciato di una delle due pagine: (alpha, traslazione x in px).
  - `fun barPieceJourney(from: List<Rect>, to: List<Rect>, barWidth: Float, maxPieces: Int = 6): List<Pair<Rect, Rect>>?` — Le coppie dei pezzi di vetro della barra fra due pagine, nelle coordinate della barra.

## `engine-ui/ui/page/FluidPageTransition.kt`

- `enum class FluidPageTransition` — Come una pagina entra e, soprattutto, come se ne va col dito.
- `enum class FluidBackSwipe` — Da dove si puo' tirare indietro una pagina col dito, dentro l'app.
- `fun interface FluidPeerMove<in K>` — L'ordine fra pari: le schede.
  - `fun direction(from: K, to: K): Int`
- `enum class FluidPageMove` — Che movimento sta facendo l'host, adesso.
