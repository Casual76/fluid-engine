package dev.antigravity.fluidengine.ui.navigation

import android.app.Activity
import android.content.ContentProvider
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.NavOptionsBuilder
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.foundation.FluidAddressResult
import dev.antigravity.fluidengine.foundation.FluidAddressTable
import dev.antigravity.fluidengine.foundation.fluidAddressCallerTrusted

private const val PendingUrl = "fluid.address.pending.url"
private const val PendingTrusted = "fluid.address.pending.trusted"

private fun Intent?.addressRequest(verifiedPackage: String?, own: String, pm: PackageManager, allowed: Set<String>): FluidAddressRequest? {
  if (this == null || (action != Intent.ACTION_VIEW && action != null)) return null
  val url = dataString ?: return null
  return FluidAddressRequest(url, fluidAddressCallerTrusted(verifiedPackage, allowed) {
    pm.checkSignatures(own, it) == PackageManager.SIGNATURE_MATCH
  })
}

/**
 * Consuma l'intent di lancio di un'Activity dedicata agli indirizzi, prima di installare il grafo.
 * Navigation riceve un intent vuoto, anche per un URL rifiutato. Su Android 15+ il mittente può
 * condividere l'identità con ActivityOptions. In assenza di identità l'ingresso è pubblico.
 */
fun Activity.takeInitialFluidAddress(allowedPackages: Set<String> = emptySet()): FluidAddressRequest? {
  val launch = intent
  setIntent(Intent())
  val caller = if (Build.VERSION.SDK_INT >= 35) initialCaller?.`package` else null
  return launch.addressRequest(caller, packageName, packageManager, allowedPackages)
}

/** Da chiamare dentro onNewIntent. Conta esclusivamente il chiamante di questa consegna. */
fun Activity.newFluidAddress(intent: Intent, allowedPackages: Set<String> = emptySet()): FluidAddressRequest? {
  val caller = if (Build.VERSION.SDK_INT >= 35) {
    // Anche un callback inoltrato senza l'identità di piattaforma resta un ingresso pubblico.
    try { currentCaller?.`package` } catch (_: IllegalStateException) { null }
  } else null
  return intent.addressRequest(caller, packageName, packageManager, allowedPackages)
}

/**
 * Alternativa per le app Pampa anche prima di Android 15. Dentro ContentProvider.call, sul thread
 * Binder, Android verifica il nome contro l'UID. Consegnare l'esito all'host per un canale interno.
 */
fun ContentProvider.fluidAddressFromCall(url: String, allowedPackages: Set<String>): FluidAddressRequest {
  val own = requireNotNull(context)
  return FluidAddressRequest(url, fluidAddressCallerTrusted(callingPackage, allowedPackages) {
    own.packageManager.checkSignatures(own.packageName, it) == PackageManager.SIGNATURE_MATCH
  })
}

/**
 * Collega la tabella dell'app alle sue rotte in FluidNavHost. Le rotte di servizio [missingRoute]
 * e [signInRoute] mostrano rispettivamente lo stato gentile e l'accesso forniti dall'app.
 * Le rotte possono essere stringhe già pronte o oggetti serializzabili di Navigation.
 */
class FluidAddressNavigation<P : Any>(
  private val table: FluidAddressTable<P>,
  private val routeOf: (P) -> Any,
  private val missingRoute: Any,
  private val signInRoute: Any,
) {
  /** Sostituisce la pila col percorso naturale in un solo turno del thread UI. Un rifiuto conserva la pila. */
  fun open(nav: NavHostController, request: FluidAddressRequest): Boolean {
    val result = table.resolve(request)
    val routes = when (result) {
      is FluidAddressResult.Open -> result.stack.map(routeOf)
      is FluidAddressResult.Missing -> result.parents.map(routeOf) + missingRoute
      is FluidAddressResult.SignIn -> result.parents.map(routeOf) + signInRoute
      FluidAddressResult.Rejected -> return false
    }
    routes.forEachIndexed { index, route ->
      val options: NavOptionsBuilder.() -> Unit = {
        if (index == 0) popUpTo(nav.graph.id) { inclusive = true }
      }
      // Sono due overload di Navigation: con Any anche una stringa verrebbe serializzata come oggetto.
      when (route) {
        is String -> nav.navigate(route, options)
        else -> nav.navigate(route, options)
      }
    }
    if (result is FluidAddressResult.SignIn) {
      val handle = checkNotNull(nav.currentBackStackEntry).savedStateHandle
      handle[PendingUrl] = result.pending.url
      handle[PendingTrusted] = result.pending.trusted
    }
    return true
  }

  /** Rivaluta l'indirizzo dopo l'accesso. Indietro o un nuovo indirizzo annullano l'attesa originale. */
  fun resumeAfterSignIn(nav: NavHostController): Boolean {
    val entry = nav.currentBackStackEntry ?: return false
    val onSignIn = if (signInRoute is String) entry.destination.route == signInRoute
      else entry.destination.hasRoute(signInRoute::class)
    if (!onSignIn) return false
    val handle = entry.savedStateHandle
    val url = handle.get<String>(PendingUrl) ?: return false
    return open(nav, FluidAddressRequest(url, handle.get<Boolean>(PendingTrusted) == true))
  }
}
