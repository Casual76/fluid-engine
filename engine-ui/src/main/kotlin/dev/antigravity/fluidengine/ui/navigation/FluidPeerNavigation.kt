package dev.antigravity.fluidengine.ui.navigation

import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder

/**
 * Passa alla scheda [route] tenendo **una pila sola** (3.0.1): dopo il passo la pila e' `[route]`, e un
 * back da una scheda esce dall'app invece di attraversare le schede viste prima.
 *
 * Tre app l'avevano scritto a mano, e le due ricette che girano sono sbagliate in due modi diversi:
 *
 *  - `popUpTo(startDestination)` funziona finche' la destinazione di partenza e' nella pila; tolta quella
 *    (un passo con `inclusive`, un deep link) non toglie piu' niente, e le schede si accumulano: A, B, C,
 *    A, B… e il back le ripercorre tutte;
 *  - `popUpTo(graph.id) { saveState = true }` sembra la ricetta di Material, ma nelle migrazioni alla 3.0 lo
 *    stato salvato di una scheda non e' mai tornato (Pampa sounds, Pampa Store): ogni ritorno ripartiva da
 *    capo, e gli stati salvati restavano li'.
 *
 * Qui: se [route] e' gia' in cima non succede niente (il secondo tocco su una scheda e' dell'app: tornare in
 * cima alla lista, per esempio); se e' nella pila sotto una pagina figlia ci si torna **con lo stesso
 * stato** (si chiudono le figlie, come il tocco sulla scheda di iOS); altrimenti la pila si svuota fino al
 * grafo compreso e si apre [route]. L'host vede la cima cambiare: con `FluidNavHost(peer = …)` e' il passo
 * fra schede (13%, la vecchia sfuma), senza e' un push.
 *
 * Non salva lo stato delle schede lasciate: una scheda riaperta riparte, come riparte la sua lista. Chi
 * vuole tenere lo stato di ogni scheda tiene un host per scheda (un `FluidNavHost` dentro ogni scheda), non
 * una pila sola. [builder] aggiunge opzioni al passo (un'animazione di navigation non conta: le pagine le
 * muove l'host).
 */
fun NavHostController.navigateToPeer(route: String, builder: NavOptionsBuilder.() -> Unit = {}) {
  if (currentDestination?.route == route) return
  if (runCatching { getBackStackEntry(route) }.isSuccess) {
    popBackStack(route, inclusive = false)
    return
  }
  navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
    builder()
  }
}

/** Come sopra, con le rotte tipizzate (`@Serializable`). */
fun <T : Any> NavHostController.navigateToPeer(route: T, builder: NavOptionsBuilder.() -> Unit = {}) {
  if (currentDestination?.hasRoute(route::class) == true) return
  if (runCatching { getBackStackEntry(route) }.isSuccess) {
    popBackStack(route, inclusive = false)
    return
  }
  navigate(route) {
    popUpTo(graph.id) { inclusive = true }
    launchSingleTop = true
    builder()
  }
}
