package dev.antigravity.fluidengine.ui.navigation

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.Navigator
import androidx.navigation.compose.DialogHost
import androidx.navigation.compose.DialogNavigator
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.ui.page.FluidPageHostImpl
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Un `NavHostController` con il navigator delle pagine Fluid gia' dentro.
 *
 * Serve a chi costruisce il grafo da se' (l'overload di [FluidNavHost] con `graph`): i builder
 * `composable(...)` cercano il navigator nel provider mentre il grafo si costruisce. Gli overload con
 * `startDestination` lo aggiungono da soli a qualunque controller.
 */
@Composable
fun rememberFluidNavController(
  vararg navigators: Navigator<out NavDestination>,
): NavHostController {
  val pageNavigator = remember { FluidPageNavigator() }
  return rememberNavController(pageNavigator, *navigators)
}

/**
 * `NavHost` con le pagine Fluid: il NavController resta quello (deep link, stato salvato, ViewModel
 * per voce, `savedStateHandle`, `navigation { }` annidati, `dialog { }`), il movimento e' quello di
 * [dev.antigravity.fluidengine.ui.page.FluidPageHost].
 *
 * NavHost non puo' seguire il dito: il suo back predittivo cerca la transizione sul `progress` di
 * sistema passato per gli easing, e una pagina che corre 2,5 volte il dito a un quinto del gesto non
 * sta sotto il dito. Qui la pagina sta sotto il dito, e alla fine la molla parte con la sua velocita'.
 *
 * Le destinazioni sono i `composable(...)` di questo pacchetto (stesso nome, altro import).
 *
 * - [peer]: l'ordine delle schede, se la pila ne contiene; due schede fanno il passo laterale.
 * - [state]: da leggere per le shell (la tab bar che cambia fra due pagine legge `progress`).
 * - [pageColor]: il fondo di ogni pagina, di serie `colorScheme.background` (l'host la dipinge, la
 *   pagina e' carta). Un colore trasparente — o [Color.Unspecified] — non dipinge niente: serve a
 *   un'app che ha un fondale suo dietro l'host (un canvas ambientale condiviso, l'artwork di un
 *   player) e vuole che ogni pagina lo lasci vedere. Il prezzo e' nelle transizioni: le pagine non
 *   sono piu' due strati opachi, e mentre una scivola sopra l'altra si vedono per trasparenza; va
 *   bene dove le pagine disegnano da se' il proprio fondo, o dove il fondale e' lo stesso per tutte.
 *   Come in [dev.antigravity.fluidengine.ui.page.FluidPageHost]. Dalla 3.0.
 */
@Composable
fun FluidNavHost(
  navController: NavHostController,
  startDestination: String,
  modifier: Modifier = Modifier,
  route: String? = null,
  peer: FluidPeerMove<NavBackStackEntry>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  builder: NavGraphBuilder.() -> Unit,
) {
  ensurePageNavigator(navController)
  val graph = remember(route, startDestination, builder) {
    NavGraphBuilder(navController.navigatorProvider, startDestination, route).apply(builder).build()
  }
  FluidNavHost(navController, graph, modifier, peer, state, pageColor)
}

/** Come sopra, con rotte tipizzate. */
@Composable
fun FluidNavHost(
  navController: NavHostController,
  startDestination: Any,
  modifier: Modifier = Modifier,
  route: KClass<*>? = null,
  typeMap: Map<KType, @JvmSuppressWildcards NavType<*>> = emptyMap(),
  peer: FluidPeerMove<NavBackStackEntry>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
  builder: NavGraphBuilder.() -> Unit,
) {
  ensurePageNavigator(navController)
  val graph = remember(route, startDestination, builder) {
    NavGraphBuilder(navController.navigatorProvider, startDestination, route, typeMap)
      .apply(builder)
      .build()
  }
  FluidNavHost(navController, graph, modifier, peer, state, pageColor)
}

/**
 * Con un grafo gia' costruito. Il controller deve avere il navigator delle pagine prima della
 * costruzione del grafo: [rememberFluidNavController].
 */
@Composable
fun FluidNavHost(
  navController: NavHostController,
  graph: NavGraph,
  modifier: Modifier = Modifier,
  peer: FluidPeerMove<NavBackStackEntry>? = null,
  state: FluidPageHostState = rememberFluidPageHostState(),
  pageColor: Color = MaterialTheme.colorScheme.background,
) {
  val lifecycleOwner = LocalLifecycleOwner.current
  val viewModelStoreOwner = checkNotNull(LocalViewModelStoreOwner.current) {
    "FluidNavHost ha bisogno di un ViewModelStoreOwner in LocalViewModelStoreOwner"
  }
  CheckNoComposeDestinations(graph)

  // L'ordine e' quello di NavHost: prima il ViewModelStore, poi il grafo, poi il ciclo di vita.
  navController.setViewModelStore(viewModelStoreOwner.viewModelStore)
  navController.graph = graph

  val navigator = navController.navigatorProvider.navigators[FluidPageNavigator.NAME] as? FluidPageNavigator
    ?: return

  DisposableEffect(lifecycleOwner) {
    navController.setLifecycleOwner(lifecycleOwner)
    onDispose { }
  }

  val saveableStateHolder = rememberSaveableStateHolder()
  val backStack by navigator.backStack.collectAsState()
  val transitions by navigator.transitionsInProgress.collectAsState()
  val leaving = remember(backStack, transitions) { transitions - backStack.toSet() }

  FluidPageHostImpl(
    stack = backStack,
    onBack = { navController.popBackStack() },
    modifier = modifier,
    transition = { entry -> (entry.destination as? FluidPageNavigator.Destination)?.transition ?: FluidPageTransition.Slide },
    backSwipe = { entry -> (entry.destination as? FluidPageNavigator.Destination)?.backSwipe ?: FluidBackSwipe.Anywhere },
    peer = peer,
    onTransitionStart = { entries -> entries.forEach(navigator::prepareForTransition) },
    // A riposo ogni voce in transizione va al suo stato finale: la cima RESUMED, quella sotto
    // CREATED, quelle uscite distrutte (e il loro ViewModelStore pulito).
    onSettled = { navigator.transitionsInProgress.value.forEach(navigator::onTransitionComplete) },
    leaving = leaving,
    state = state,
    pageColor = pageColor,
    ownOwners = false,
  ) { entry ->
    // La voce da' alla pagina ViewModelStore, ciclo di vita (CREATED quando e' coperta) e stato
    // salvabile, come in NavHost.
    entry.LocalOwnersProvider(saveableStateHolder) {
      (entry.destination as? FluidPageNavigator.Destination)?.content?.invoke(this, entry)
    }
  }

  // Per tipo e non per nome: DialogNavigator.NAME e' interno a navigation-compose.
  val dialogNavigator = navController.navigatorProvider.navigators.values
    .firstOrNull { it is DialogNavigator } as? DialogNavigator
  if (dialogNavigator != null) DialogHost(dialogNavigator)
}

/** Aggiunge il navigator delle pagine al controller, una volta, prima che il grafo si costruisca. */
@Composable
private fun ensurePageNavigator(navController: NavHostController) {
  remember(navController) {
    val provider = navController.navigatorProvider
    provider.navigators[FluidPageNavigator.NAME] as? FluidPageNavigator
      ?: FluidPageNavigator().also { provider.addNavigator(FluidPageNavigator.NAME, it) }
  }
}

/**
 * Una destinazione di `androidx.navigation.compose.composable` dentro FluidNavHost non si vedrebbe:
 * il suo contenuto e' interno a navigation-compose e nessuno lo disegna. E' l'errore piu' probabile
 * di una migrazione (un import dimenticato), quindi in debug si ferma subito e dice quale.
 */
@Composable
private fun CheckNoComposeDestinations(graph: NavGraph) {
  val context = LocalContext.current
  remember(graph) {
    val stray = graph.composeRoutes()
    if (stray.isNotEmpty()) {
      val message = "FluidNavHost: queste destinazioni sono di androidx.navigation.compose.composable e " +
        "non verrebbero disegnate: $stray. Importa dev.antigravity.fluidengine.ui.navigation.composable."
      val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
      if (debuggable) error(message) else Log.e("FluidNavHost", message)
    }
    stray
  }
}

private fun NavGraph.composeRoutes(): List<String> = flatMap { destination ->
  when {
    destination is NavGraph -> destination.composeRoutes()
    destination.navigatorName == "composable" -> listOf(destination.route ?: destination.displayName)
    else -> emptyList()
  }
}
