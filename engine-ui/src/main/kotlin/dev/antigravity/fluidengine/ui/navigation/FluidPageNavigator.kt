package dev.antigravity.fluidengine.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination
import androidx.navigation.NavDestinationBuilder
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.NavType
import androidx.navigation.Navigator
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.FluidPageDismiss
import dev.antigravity.fluidengine.ui.page.FluidPageScope
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidZoomStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Il navigator delle pagine Fluid: fa per [FluidNavHost] quello che `ComposeNavigator` fa per
 * `NavHost`, con le stesse API pubbliche di navigation (`NavigatorState`), e niente di interno.
 *
 * Le destinazioni non portano animazioni: portano uno stile ([FluidPageTransition]) e da dove si
 * tirano indietro ([FluidBackSwipe]). Il movimento e' tutto dell'host.
 */
@Navigator.Name(FluidPageNavigator.NAME)
class FluidPageNavigator : Navigator<FluidPageNavigator.Destination>() {
  internal var addressQueue: FluidModalAddressQueue? = null

  // Il costruttore `Navigator(name)` e' riservato al gruppo di navigation (RestrictedApi): il nome lo dicono
  // `@Navigator.Name` qui sopra e `addNavigator(NAME, …)`, che e' come l'engine registra il navigator.

  private val detached = MutableStateFlow<List<NavBackStackEntry>>(emptyList())
  private val detachedTransitions = MutableStateFlow<Set<NavBackStackEntry>>(emptySet())

  /** Le voci di questo navigator nel back stack, dalla radice alla cima (anche nei grafi annidati). */
  val backStack: StateFlow<List<NavBackStackEntry>>
    get() = if (isAttached) state.backStack else detached

  /** Le voci che stanno entrando o uscendo: restano vive finche' l'host non le dichiara ferme. */
  val transitionsInProgress: StateFlow<Set<NavBackStackEntry>>
    get() = if (isAttached) state.transitionsInProgress else detachedTransitions

  override fun createDestination(): Destination = Destination(this) { }

  override fun navigate(entries: List<NavBackStackEntry>, navOptions: NavOptions?, navigatorExtras: Extras?) {
    entries.forEach { entry -> state.pushWithTransition(entry) }
  }

  override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
    // Un salto di piu' pagine (popUpTo sotto la cima): la vecchia cima deve restare viva per uscire
    // davanti agli occhi. `popWithTransition` tiene in transizione solo popUpTo e la nuova cima; la
    // cima la marchiamo noi, prima che il NavController la tolga (e la distrugga).
    val top = state.backStack.value.lastOrNull()
    if (top != null && top != popUpTo) state.prepareForTransition(top)
    state.popWithTransition(popUpTo, savedState)
  }

  /** Un gesto comincia: le due voci restano almeno STARTED finche' il gesto non si chiude. */
  fun prepareForTransition(entry: NavBackStackEntry) {
    if (isAttached && entry in state.backStack.value) state.prepareForTransition(entry)
  }

  /** L'host e' a riposo: la voce va al suo stato finale (RESUMED in cima, CREATED sotto, distrutta fuori). */
  fun onTransitionComplete(entry: NavBackStackEntry) {
    if (isAttached) state.markTransitionComplete(entry)
  }

  /**
   * Una pagina Fluid nel grafo.
   *
   * Creata dai builder `composable(...)` di questo pacchetto; il contenuto riceve la voce del back
   * stack con lo [FluidPageScope] dell'host come ricevitore.
   */
  @NavDestination.ClassType(Composable::class)
  class Destination(
    @Suppress("UNUSED_PARAMETER") navigator: FluidPageNavigator,
    internal val content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
  ) : NavDestination(NAME) {
    // Per nome e non per navigator: il costruttore col navigator legge l'annotazione @Navigator.Name
    // per riflessione, e un R8 aggressivo puo' toglierla.
    var transition: FluidPageTransition = FluidPageTransition.Slide
      internal set
    var backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly
      internal set
    var dismiss: FluidPageDismiss = FluidPageDismiss.None
      internal set

    /** La sorgente dello zoom di una voce (4.0, F5B.3): vedi `composable(zoomSource = …)`. Null: nessuna. */
    var zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null
      internal set

    /** Il preset di «un elemento che diventa pagina» (F6B.5c): vedi `composable(zoom = …)`. Null: nessuno. */
    var zoom: FluidZoomStyle? = null
      internal set
  }

  companion object {
    /** Il nome con cui il navigator sta nel `NavigatorProvider`. */
    const val NAME = "fluidPage"
  }
}

/** Il builder di una pagina Fluid, per stringa o per classe (rotte tipizzate). */
class FluidPageDestinationBuilder : NavDestinationBuilder<FluidPageNavigator.Destination> {

  private val pageNavigator: FluidPageNavigator
  private val content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit

  var transition: FluidPageTransition = FluidPageTransition.Slide
  var backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly
  var dismiss: FluidPageDismiss = FluidPageDismiss.None
  var zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null
  var zoom: FluidZoomStyle? = null

  constructor(
    navigator: FluidPageNavigator,
    route: String,
    content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
  ) : super(navigator, route) {
    pageNavigator = navigator
    this.content = content
  }

  constructor(
    navigator: FluidPageNavigator,
    route: KClass<*>,
    typeMap: Map<KType, @JvmSuppressWildcards NavType<*>>,
    content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
  ) : super(navigator, route, typeMap) {
    pageNavigator = navigator
    this.content = content
  }

  override fun instantiateDestination(): FluidPageNavigator.Destination =
    FluidPageNavigator.Destination(pageNavigator, content)

  override fun build(): FluidPageNavigator.Destination = super.build().also { destination ->
    destination.transition = transition
    destination.backSwipe = backSwipe
    destination.dismiss = dismiss
    destination.zoomSource = zoomSource
    destination.zoom = zoom
  }
}

private fun NavGraphBuilder.pageNavigator(): FluidPageNavigator =
  provider.getNavigator(FluidPageNavigator.NAME)

/**
 * Una pagina Fluid nel grafo, per rotta stringa.
 *
 * Stesso nome di `androidx.navigation.compose.composable` di proposito: migrare un grafo vuol dire
 * cambiare un import. Le animazioni di NavHost non ci sono piu': c'e' lo stile della pagina.
 *
 * [zoomSource] (4.0, F5B.3): con `transition = FluidPageTransition.Expand`, la sorgente da cui la voce si apre con lo
 * zoom e a cui torna, letta dalla voce (di solito dai suoi argomenti: `FluidTransitionSource(entry.arguments…, ns)`).
 * Vedi `FluidPageHost(zoomSource)`.
 */
fun NavGraphBuilder.composable(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) = composable(route, arguments, deepLinks, transition, backSwipe, zoomSource, FluidPageDismiss.None, content)

/** Variante con chiusura verticale dichiarata sulla rotta. */
fun NavGraphBuilder.composable(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  dismiss: FluidPageDismiss,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) {
  destination(
    FluidPageDestinationBuilder(pageNavigator(), route, content).apply {
      arguments.forEach { (argumentName, navArgument) -> argument(argumentName, navArgument) }
      deepLinks.forEach { link -> deepLink(link) }
      this.transition = transition
      this.backSwipe = backSwipe
      this.zoomSource = zoomSource
      this.dismiss = dismiss
    },
  )
}

/**
 * Una pagina che nasce da un elemento con un preset (F6B.5c): `composable(route, zoomSource = …, zoom =
 * FluidZoomStyle.Rise)`. Di serie [transition] è [FluidPageTransition.Expand] (lo zoom) e [dismiss] quella del preset
 * ([FluidZoomStyle.dismiss]: trascinando in giù). [zoomSource] è la sorgente da cui la voce si apre, letta dalla voce;
 * con [FluidZoomStyle.fromToolbar] non serve. È una rotta vera: back stack, [deepLinks] e stato salvato come le altre;
 * aperta da un collegamento o ripristinata dopo la morte del processo è già aperta, e il ritorno cerca la sorgente o usa
 * il ripiego.
 */
fun NavGraphBuilder.composable(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Expand,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  zoom: FluidZoomStyle,
  dismiss: FluidPageDismiss = zoom.dismiss,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) {
  destination(
    FluidPageDestinationBuilder(pageNavigator(), route, content).apply {
      arguments.forEach { (argumentName, navArgument) -> argument(argumentName, navArgument) }
      deepLinks.forEach { link -> deepLink(link) }
      this.transition = transition
      this.backSwipe = backSwipe
      this.zoomSource = zoomSource
      this.dismiss = dismiss
      this.zoom = zoom
    },
  )
}

/** Una pagina Fluid nel grafo, per rotta tipizzata (la classe della rotta, serializzabile). */
fun <T : Any> NavGraphBuilder.composable(
  route: KClass<T>,
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) = composable(route, typeMap, deepLinks, transition, backSwipe, zoomSource, FluidPageDismiss.None, content)

/** Variante tipizzata con chiusura verticale. */
fun <T : Any> NavGraphBuilder.composable(
  route: KClass<T>,
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  dismiss: FluidPageDismiss,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) {
  destination(
    FluidPageDestinationBuilder(pageNavigator(), route, typeMap, content).apply {
      deepLinks.forEach { link -> deepLink(link) }
      this.transition = transition
      this.backSwipe = backSwipe
      this.zoomSource = zoomSource
      this.dismiss = dismiss
    },
  )
}

/** Variante tipizzata con un preset di «un elemento che diventa pagina» (F6B.5c). */
fun <T : Any> NavGraphBuilder.composable(
  route: KClass<T>,
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Expand,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  zoom: FluidZoomStyle,
  dismiss: FluidPageDismiss = zoom.dismiss,
  content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) {
  destination(
    FluidPageDestinationBuilder(pageNavigator(), route, typeMap, content).apply {
      deepLinks.forEach { link -> deepLink(link) }
      this.transition = transition
      this.backSwipe = backSwipe
      this.zoomSource = zoomSource
      this.dismiss = dismiss
      this.zoom = zoom
    },
  )
}

/** Una pagina Fluid nel grafo, per rotta tipizzata: `composable<Dettaglio> { entry -> ... }`. */
inline fun <reified T : Any> NavGraphBuilder.composable(
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  noinline zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  noinline content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) {
  composable(T::class, typeMap, deepLinks, transition, backSwipe, zoomSource, content)
}

/** Variante reified con chiusura verticale. */
inline fun <reified T : Any> NavGraphBuilder.composable(
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Slide,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  noinline zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  dismiss: FluidPageDismiss,
  noinline content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) = composable(T::class, typeMap, deepLinks, transition, backSwipe, zoomSource, dismiss, content)

/** Variante reified con un preset di «un elemento che diventa pagina» (F6B.5c). */
inline fun <reified T : Any> NavGraphBuilder.composable(
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  deepLinks: List<NavDeepLink> = emptyList(),
  transition: FluidPageTransition = FluidPageTransition.Expand,
  backSwipe: FluidBackSwipe = FluidBackSwipe.EdgeOnly,
  noinline zoomSource: ((NavBackStackEntry) -> FluidTransitionSource?)? = null,
  zoom: FluidZoomStyle,
  dismiss: FluidPageDismiss = zoom.dismiss,
  noinline content: @Composable FluidPageScope.(NavBackStackEntry) -> Unit,
) = composable(T::class, typeMap, deepLinks, transition, backSwipe, zoomSource, zoom, dismiss, content)
