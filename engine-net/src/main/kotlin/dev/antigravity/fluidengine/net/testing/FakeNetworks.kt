package dev.antigravity.fluidengine.net.testing

import dev.antigravity.fluidengine.net.EngineNetwork
import dev.antigravity.fluidengine.net.EngineNetworkCaps
import dev.antigravity.fluidengine.net.EngineNetworkDefaults
import dev.antigravity.fluidengine.net.EngineNetworkRule
import dev.antigravity.fluidengine.net.EngineNetworkState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reti finte per le prove e per la simulazione della galleria (F8.20): un Wi-Fi, i dati mobili, una VPN sopra, un portale
 * di login, una micro-caduta, il cambio da una rete all'altra. **Non tocca il telefono**: la regola e la tenuta sono
 * quelle vere di [EngineNetwork] (Wi-Fi senza internet, VPN, cambio di rete e micro-cadute passano dallo stesso codice
 * dell'osservatore di Android).
 *
 * ```
 * val nets = FakeNetworks()
 * val online by nets.states.map { it.online }.collectAsState(initial = true)
 * nets.wifi(validated = false)      // un Wi-Fi d'albergo: c'e', ma non porta a internet
 * nets.microCut(600)                // una caduta di 0,6 s: la capsula non lampeggia
 * ```
 *
 * Thread-safe; le funzioni sospese aspettano con il `delay` delle coroutine (il tempo delle prove, nelle prove).
 *
 * @param graceMillis la tenuta di [states] ([EngineNetworkDefaults.OfflineGraceMillis]).
 * @param now l'orologio della tenuta, in millisecondi (quello delle prove, nelle prove).
 */
class FakeNetworks(
  private val graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis,
  private val now: () -> Long = { System.nanoTime() / 1_000_000L },
) {
  private class Net(val name: String, val caps: EngineNetworkCaps)

  private val lock = Any()
  private val networks = LinkedHashMap<String, Net>()
  private var default: String? = null
  private val _raw = MutableStateFlow(EngineNetworkState.Offline)

  init {
    wifi()
  }

  /** Lo stato senza tenuta: ogni cambio com'e'. */
  val raw: StateFlow<EngineNetworkState> = _raw.asStateFlow()

  /** Lo stato come lo da' [EngineNetwork.states]: con la tenuta contro le micro-cadute. */
  val states: Flow<EngineNetworkState> get() = EngineNetwork.steady(raw, graceMillis, now)

  /** La rete predefinita adesso: «wifi», «cellular», «vpn», o null. */
  val current: String? get() = synchronized(lock) { default }

  /** Un Wi-Fi diventa la rete predefinita (con una VPN accesa, va sotto la VPN). */
  fun wifi(validated: Boolean = true, metered: Boolean = false, captivePortal: Boolean = false) = change {
    networks[Wifi] = Net(Wifi, EngineNetworkCaps(internet = true, validated = validated, notMetered = !metered, captivePortal = captivePortal))
    if (default != Vpn) default = Wifi
  }

  /** I dati mobili diventano la rete predefinita (con una VPN accesa, vanno sotto la VPN). */
  fun cellular(validated: Boolean = true, roaming: Boolean = false, suspended: Boolean = false) = change {
    networks[Cellular] = Net(
      Cellular,
      EngineNetworkCaps(internet = true, validated = validated, notMetered = false, notRoaming = !roaming, notSuspended = !suspended),
    )
    if (default != Vpn) default = Cellular
  }

  /**
   * Una VPN sopra le reti che ci sono. [claimsValidated] e' quello che la VPN dice di se' (Android spesso la segna
   * validata senza provarla): conta la rete sotto.
   */
  fun vpn(claimsValidated: Boolean = true) = change {
    networks[Vpn] = Net(Vpn, EngineNetworkCaps(internet = true, validated = claimsValidated, notMetered = true, vpn = true))
    default = Vpn
  }

  /** Spegne la VPN: torna predefinita la rete sotto (il Wi-Fi se c'e'). */
  fun vpnOff() = change {
    networks.remove(Vpn)
    if (default == Vpn) default = pickUnderlying()
  }

  /** Perde la rete [name] («wifi», «cellular», «vpn»); se era la predefinita, nessuna lo e' (come `onLost`). */
  fun lose(name: String) = change {
    networks.remove(name)
    if (default == name) default = null
  }

  /** Nessuna rete. */
  fun disconnect() = change {
    networks.clear()
    default = null
  }

  /**
   * Una micro-caduta: la rete predefinita sparisce per [millis] e torna com'era. Con la tenuta di [states] una caduta
   * piu' corta di [graceMillis] non si vede.
   */
  suspend fun microCut(millis: Long = 600L) {
    val saved = synchronized(lock) { default?.let { networks[it] } to default }
    val (net, name) = saved
    if (net == null || name == null) return
    lose(name)
    delay(millis)
    change {
      networks[name] = net
      if (default == null) default = name
    }
  }

  /**
   * Il passaggio dal Wi-Fi ai dati mobili com'e' su un telefono vero: il Wi-Fi si perde, i dati diventano predefiniti
   * dopo [gapMillis] e sono validati dopo altri [validationMillis].
   */
  suspend fun handover(gapMillis: Long = 400L, validationMillis: Long = 700L) {
    lose(Wifi)
    delay(gapMillis)
    cellular(validated = false)
    delay(validationMillis)
    cellular(validated = true)
  }

  private fun pickUnderlying(): String? =
    networks.values.firstOrNull { !it.caps.vpn && it.caps.notMetered }?.name ?: networks.values.firstOrNull { !it.caps.vpn }?.name

  private inline fun change(block: () -> Unit) {
    val state = synchronized(lock) {
      block()
      val defaultCaps = default?.let { networks[it]?.caps }
      EngineNetworkRule.stateOf(defaultCaps, networks.values.filter { it.name != default }.map { it.caps })
    }
    _raw.value = state
  }

  companion object {
    const val Wifi: String = "wifi"
    const val Cellular: String = "cellular"
    const val Vpn: String = "vpn"
  }
}
