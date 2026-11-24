package dev.antigravity.fluidengine.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull

/** Un solo indirizzo valido in attesa per controller. Un secondo link aggiorna la meta durante il ritorno. */
internal class FluidModalAddressQueue(
  private val modals: FluidGlassModalHostState,
  request: FluidAddressRequest? = null,
  router: FluidAddressNavigation<*>? = null,
) {
  private class Delivery(val router: FluidAddressNavigation<*>, val request: FluidAddressRequest)

  private var pending by mutableStateOf(if (router != null && request != null) Delivery(router, request) else null)

  fun deferIfNeeded(addresses: FluidAddressNavigation<*>, request: FluidAddressRequest): Boolean {
    if (pending == null && !modals.rootOnScreen) return false
    pending = Delivery(addresses, request)
    return true
  }

  suspend fun drain(nav: NavHostController) {
    snapshotFlow { pending }.filterNotNull().collectLatest { delivery ->
      modals.awaitRootDismissal()
      if (pending === delivery) {
        pending = null
        // La risorsa o la sessione potrebbero essere cambiate durante l'uscita.
        delivery.router.open(nav, delivery.request, queue = null)
      }
    }
  }

  fun savedFor(declared: FluidAddressNavigation<*>?): List<Any> {
    val delivery = pending ?: return emptyList()
    return if (delivery.router === declared) listOf(delivery.request.url, delivery.request.trusted) else emptyList()
  }
}

@Composable
internal fun rememberFluidModalAddressQueue(
  nav: NavHostController,
  navigator: FluidPageNavigator,
  declared: FluidAddressNavigation<*>?,
): FluidModalAddressQueue? {
  val modals = LocalFluidGlassModalHostState.current ?: return null
  val queue = rememberSaveable(nav, modals, saver = listSaver(
    save = { it: FluidModalAddressQueue -> it.savedFor(declared) },
    restore = { FluidModalAddressQueue(modals, FluidAddressRequest(it[0] as String, it[1] as Boolean), declared) },
  )) { FluidModalAddressQueue(modals) }
  DisposableEffect(navigator, queue) {
    navigator.addressQueue = queue
    onDispose { if (navigator.addressQueue === queue) navigator.addressQueue = null }
  }
  LaunchedEffect(queue) { queue.drain(nav) }
  return queue
}
