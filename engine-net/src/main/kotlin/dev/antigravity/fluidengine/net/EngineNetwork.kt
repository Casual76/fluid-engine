package dev.antigravity.fluidengine.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Lo stato della rete come serve a un'app (F8.1, `stati-pronti`; lo riusa `stato-rete-e-offline`, F8.20).
 *
 * «Offline» su Android non è sempre vero: un Wi-Fi d'albergo senza accesso è una rete, ma non porta a internet. Per
 * questo [online] chiede anche [validated], cioè `NET_CAPABILITY_VALIDATED`: Android ha provato che quella rete arriva a
 * internet.
 *
 * @param connected c'è una rete predefinita che dichiara `NET_CAPABILITY_INTERNET`.
 * @param validated Android ha verificato che arriva davvero a internet.
 * @param metered è a consumo (dati mobili, un hotspot): un'app rimanda i download grandi.
 */
data class EngineNetworkState(
  val connected: Boolean,
  val validated: Boolean,
  val metered: Boolean,
) {
  /** Si può usare la rete: c'è, e porta a internet. */
  val online: Boolean get() = connected && validated

  companion object {
    /** Nessuna rete. */
    val Offline: EngineNetworkState = EngineNetworkState(connected = false, validated = false, metered = false)

    /** Dalle capacità della rete predefinita: la regola, pura, che le prove provano sul computer. */
    fun of(hasInternet: Boolean, validated: Boolean, notMetered: Boolean): EngineNetworkState =
      EngineNetworkState(connected = hasInternet, validated = hasInternet && validated, metered = hasInternet && !notMetered)
  }
}

/**
 * L'osservatore della rete dell'engine, **senza interfaccia**: lo stato di adesso ([current]) e quello che cambia
 * ([states]). Guarda la rete predefinita del telefono (`registerDefaultNetworkCallback`), quella che le app usano
 * davvero. Serve il permesso `ACCESS_NETWORK_STATE`, che il manifesto di `engine-net` dichiara già (è innocuo: non lo
 * vede nessuno).
 *
 * ```
 * val online by remember { EngineNetwork.states(context).map { it.online } }.collectAsState(initial = true)
 * FluidEmptyState(FluidEmptyPreset.Offline, online = online, onRetry = { repository.refresh() })
 * ```
 */
object EngineNetwork {
  /** Lo stato della rete predefinita adesso. */
  fun current(context: Context): EngineNetworkState {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return EngineNetworkState.Offline
    val network = manager.activeNetwork ?: return EngineNetworkState.Offline
    return stateOf(manager.getNetworkCapabilities(network))
  }

  /**
   * Lo stato della rete predefinita, ogni volta che cambia: parte dallo stato di adesso, non ripete uno stato uguale e
   * si stacca da sola quando chi raccoglie smette.
   */
  fun states(context: Context): Flow<EngineNetworkState> = callbackFlow {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
      trySend(EngineNetworkState.Offline)
      awaitClose { }
      return@callbackFlow
    }
    trySend(current(context))
    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        trySend(stateOf(capabilities))
      }

      override fun onLost(network: Network) {
        trySend(EngineNetworkState.Offline)
      }

      override fun onUnavailable() {
        trySend(EngineNetworkState.Offline)
      }
    }
    manager.registerDefaultNetworkCallback(callback)
    awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
  }.conflate().distinctUntilChanged()

  private fun stateOf(capabilities: NetworkCapabilities?): EngineNetworkState {
    if (capabilities == null) return EngineNetworkState.Offline
    return EngineNetworkState.of(
      hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
      validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
      notMetered = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
    )
  }
}
