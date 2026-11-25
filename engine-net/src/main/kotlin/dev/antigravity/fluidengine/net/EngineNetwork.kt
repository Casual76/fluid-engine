package dev.antigravity.fluidengine.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/**
 * Lo stato della rete come serve a un'app (F8.1, `stati-pronti`; F8.20, `stato-rete-e-offline`).
 *
 * «Offline» su Android non è sempre vero: un Wi-Fi d'albergo senza accesso è una rete, ma non porta a internet. Per
 * questo [online] chiede anche [validated], cioè `NET_CAPABILITY_VALIDATED`: Android ha provato che quella rete arriva a
 * internet.
 *
 * @param connected c'è una rete predefinita che dichiara `NET_CAPABILITY_INTERNET`.
 * @param validated Android ha verificato che arriva davvero a internet (con una VPN: la rete sotto la VPN).
 * @param metered è a consumo (dati mobili, un hotspot): un'app rimanda i download grandi.
 * @param vpn la rete predefinita è una VPN (F8.20).
 * @param roaming la rete è in roaming (F8.20).
 */
data class EngineNetworkState(
  val connected: Boolean,
  val validated: Boolean,
  val metered: Boolean,
  val vpn: Boolean = false,
  val roaming: Boolean = false,
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

/** I tempi dell'osservatore. */
object EngineNetworkDefaults {
  /**
   * Quanto deve durare una caduta prima di diventare «offline»: **2,5 s**, a occhio il 25 novembre 2026 (F8.20).
   *
   * Il passaggio dal Wi-Fi ai dati mobili (il Wi-Fi si perde prima che la rete nuova sia validata: da poche centinaia di
   * millisecondi a un paio di secondi), un ascensore, un treno sotto un ponte: una caduta più corta non si vede, e la
   * capsula non lampeggia. Il ritorno in linea invece si dice subito. Apple non ha un tempo pubblico: `NWPathMonitor`
   * riporta ogni cambio, e sono Mail e Note a non mostrarlo.
   */
  const val OfflineGraceMillis: Long = 2_500L
}

/**
 * Le capacità di una rete, quanto basta alla regola ([EngineNetworkRule]). Le costruiscono l'osservatore di Android e le
 * reti finte delle prove (`testing.FakeNetworks`).
 */
internal data class EngineNetworkCaps(
  val internet: Boolean,
  val validated: Boolean,
  val notMetered: Boolean,
  val vpn: Boolean = false,
  val notRoaming: Boolean = true,
  val notSuspended: Boolean = true,
  val captivePortal: Boolean = false,
) {
  /** Porta a internet adesso: validata, non sospesa (una chiamata sul 3G), non dietro un portale di login. */
  val reaches: Boolean get() = internet && validated && notSuspended && !captivePortal
}

/**
 * La regola, pura: lo stato della rete predefinita ([default]) sapendo le altre reti che non sono VPN ([underlying]).
 *
 * - **Wi-Fi senza internet**: la rete c'è (`connected`) ma non è validata, o è dietro un portale: non è online.
 * - **VPN**: Android segna spesso la VPN come validata senza provarla, e alcune VPN non lo dicono mai. Se si sa cosa
 *   c'è sotto, conta quello: online se almeno una rete sotto porta a internet (anche se la VPN non si dice validata),
 *   offline se nessuna (anche se la VPN si dice validata: il Wi-Fi sotto non ha internet). Se non si sa, si crede alla
 *   VPN. A consumo e in roaming come la rete sotto che porta a internet, se c'è.
 * - **Sospesa** (`NOT_SUSPENDED` assente: i dati durante una chiamata su certe reti): non è online, ma di solito dura
 *   poco, e la tenuta di [EngineNetwork.steady] la nasconde.
 */
internal object EngineNetworkRule {
  fun stateOf(default: EngineNetworkCaps?, underlying: Collection<EngineNetworkCaps>): EngineNetworkState {
    if (default == null || !default.internet) return EngineNetworkState.Offline
    if (!default.vpn) {
      return EngineNetworkState(
        connected = true,
        validated = default.reaches,
        metered = !default.notMetered,
        vpn = false,
        roaming = !default.notRoaming,
      )
    }
    val below = underlying.filter { !it.vpn && it.internet }
    if (below.isEmpty()) {
      return EngineNetworkState(
        connected = true,
        validated = default.reaches,
        metered = !default.notMetered,
        vpn = true,
        roaming = !default.notRoaming,
      )
    }
    val reaching = below.filter { it.reaches }
    // La rete che la VPN usa e' quella che il sistema preferisce: una che non costa, se c'e'.
    val carrier = reaching.firstOrNull { it.notMetered } ?: reaching.firstOrNull()
    return EngineNetworkState(
      connected = true,
      validated = carrier != null && default.notSuspended,
      metered = carrier?.let { !it.notMetered } ?: !default.notMetered,
      vpn = true,
      roaming = carrier?.let { !it.notRoaming } ?: !default.notRoaming,
    )
  }
}

/**
 * L'osservatore della rete dell'engine, **senza interfaccia**: lo stato di adesso ([current]) e quello che cambia
 * ([states]). Guarda la rete predefinita del telefono (`registerDefaultNetworkCallback`), quella che le app usano
 * davvero, e le reti sotto una VPN. Serve il permesso `ACCESS_NETWORK_STATE`, che il manifesto di `engine-net` dichiara
 * già (è innocuo: non lo vede nessuno).
 *
 * ```
 * val online by remember { EngineNetwork.states(context).map { it.online } }.collectAsState(initial = true)
 * FluidEmptyState(FluidEmptyPreset.Offline, online = online, onRetry = { repository.refresh() })
 * ```
 *
 * Per tutte le condizioni del telefono insieme (rete, risparmio dati ed energetico, calore, carica): `EngineConditions`.
 */
object EngineNetwork {
  /** Lo stato della rete predefinita adesso (senza tenuta: è una lettura). */
  fun current(context: Context): EngineNetworkState {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return EngineNetworkState.Offline
    val network = manager.activeNetwork ?: return EngineNetworkState.Offline
    val default = capsOf(manager.getNetworkCapabilities(network)) ?: return EngineNetworkState.Offline
    val underlying = if (default.vpn) {
      @Suppress("DEPRECATION")
      manager.allNetworks.filter { it != network }.mapNotNull { capsOf(manager.getNetworkCapabilities(it)) }
    } else {
      emptyList()
    }
    return EngineNetworkRule.stateOf(default, underlying)
  }

  /**
   * Lo stato della rete predefinita, ogni volta che cambia: parte dallo stato di adesso, non ripete uno stato uguale e
   * si stacca da sola quando chi raccoglie smette.
   *
   * **Non lampeggia** (F8.20): una caduta più corta di [graceMillis] ([EngineNetworkDefaults.OfflineGraceMillis]) non
   * diventa «offline» — il cambio dal Wi-Fi ai dati, una micro-caduta —, il ritorno in linea si dice subito. Un Wi-Fi
   * senza internet non è online; una VPN vale quanto la rete sotto (vedi [EngineNetworkState]). Con `graceMillis = 0`
   * ogni cambio passa com'è.
   */
  fun states(context: Context, graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis): Flow<EngineNetworkState> =
    steady(rawStates(context), graceMillis)

  /**
   * La tenuta di [states] su un flusso qualsiasi di stati ([raw]): passa subito tutto, tranne il passaggio da online a
   * non online, che aspetta [graceMillis] e si annulla se intanto la rete torna. [now] è l'orologio in millisecondi
   * (quello delle prove, nelle prove).
   */
  @OptIn(ExperimentalCoroutinesApi::class)
  fun steady(
    raw: Flow<EngineNetworkState>,
    graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis,
    now: () -> Long = { System.nanoTime() / 1_000_000L },
  ): Flow<EngineNetworkState> {
    if (graceMillis <= 0L) return raw.distinctUntilChanged()
    return flow {
      // Lo stato di ogni raccolta e' suo: due schermate che raccolgono lo stesso flusso non si pestano.
      var shown: EngineNetworkState? = null
      // Da quando la rete non e' piu' online mentre si mostra ancora online: la tenuta conta da li', non dall'ultimo cambio.
      var downSince = -1L
      emitAll(
        raw.transformLatest { next ->
          val previous = shown
          if (previous != null && previous.online && !next.online) {
            val start = if (downSince >= 0L) downSince else now().also { downSince = it }
            val left = graceMillis - (now() - start)
            if (left > 0L) delay(left)
          }
          if (next.online) downSince = -1L
          if (shown != next) {
            shown = next
            if (!next.online) downSince = -1L
            emit(next)
          }
        },
      )
    }.distinctUntilChanged()
  }

  /** Il flusso senza tenuta: ogni callback di Android, tradotto dalla regola. */
  internal fun rawStates(context: Context): Flow<EngineNetworkState> = callbackFlow {
    val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    if (manager == null) {
      trySend(EngineNetworkState.Offline)
      awaitClose { }
      return@callbackFlow
    }
    val lock = Any()
    var defaultNetwork: Network? = null
    var defaultCaps: EngineNetworkCaps? = null
    val others = HashMap<Network, EngineNetworkCaps>()

    fun publish() {
      val state = synchronized(lock) { EngineNetworkRule.stateOf(defaultCaps, others.values.toList()) }
      trySend(state)
    }

    val defaultCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        synchronized(lock) {
          if (defaultNetwork != network) {
            defaultNetwork = network
            defaultCaps = capsOf(manager.getNetworkCapabilities(network))
          }
        }
        publish()
      }

      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        synchronized(lock) {
          defaultNetwork = network
          defaultCaps = capsOf(capabilities)
        }
        publish()
      }

      override fun onLost(network: Network) {
        synchronized(lock) {
          if (defaultNetwork == network) {
            defaultNetwork = null
            defaultCaps = null
          }
        }
        publish()
      }

      override fun onUnavailable() {
        synchronized(lock) {
          defaultNetwork = null
          defaultCaps = null
        }
        publish()
      }
    }
    // Le reti che non sono VPN (il costruttore di serie chiede NOT_VPN): servono a sapere cosa c'e' sotto una VPN.
    val othersCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
        val caps = capsOf(capabilities) ?: return
        synchronized(lock) { others[network] = caps }
        publish()
      }

      override fun onLost(network: Network) {
        synchronized(lock) { others.remove(network) }
        publish()
      }
    }
    trySend(current(context))
    manager.registerDefaultNetworkCallback(defaultCallback)
    val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
    val othersRegistered = runCatching { manager.registerNetworkCallback(request, othersCallback) }.isSuccess
    awaitClose {
      runCatching { manager.unregisterNetworkCallback(defaultCallback) }
      if (othersRegistered) runCatching { manager.unregisterNetworkCallback(othersCallback) }
    }
  }.conflate().distinctUntilChanged()

  internal fun capsOf(capabilities: NetworkCapabilities?): EngineNetworkCaps? {
    if (capabilities == null) return null
    fun has(capability: Int) = capabilities.hasCapability(capability)
    return EngineNetworkCaps(
      internet = has(NetworkCapabilities.NET_CAPABILITY_INTERNET),
      validated = has(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
      notMetered = has(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
        (Build.VERSION.SDK_INT >= 30 && has(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)),
      vpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
      notRoaming = Build.VERSION.SDK_INT < 28 || has(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING),
      notSuspended = Build.VERSION.SDK_INT < 28 || has(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED),
      captivePortal = has(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
    )
  }
}
