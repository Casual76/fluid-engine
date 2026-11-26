package dev.antigravity.fluidengine.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidThermal
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

/**
 * I segnali del telefono che non sono la rete: risparmio dati, risparmio energetico, calore, corrente. Letti tutti
 * insieme a ogni cambio.
 */
internal data class EngineDeviceSignals(
  /** `getRestrictBackgroundStatus() == RESTRICT_BACKGROUND_STATUS_ENABLED`: il risparmio dati limita quest'app. */
  val restrictBackground: Boolean = false,
  val powerSave: Boolean = false,
  /** `PowerManager.THERMAL_STATUS_*`, o 0 sotto l'API 29. */
  val thermalStatus: Int = 0,
  /** Attaccato alla corrente (`EXTRA_PLUGGED` diverso da zero). */
  val plugged: Boolean = false,
)

/**
 * Le condizioni del telefono lette da Android (F8.20, `pochi-dati-poca-batteria`): la rete ([EngineNetwork], con la sua
 * tenuta), il risparmio dati, il risparmio energetico, il calore e la corrente, in un [FluidConditions] solo. La regola
 * che le usa sta in `engine-foundation` ([FluidConditions.allows]).
 *
 * Il posto solo del processo e' [FluidDeviceConditions]: [install] lo tiene aggiornato, e da li' leggono i pezzi
 * dell'engine che si regolano da soli (la luce del vetro che segue l'inclinazione, l'aptica continua, gli stati pronti che
 * riprovano, i trasferimenti). Senza [install] quei pezzi fanno come prima.
 *
 * ```
 * // nell'Application, una volta:
 * EngineConditions.install(this)
 * // prima di un lavoro pesante:
 * val permit = FluidDeviceConditions.allows(FluidWork.LargeDownload)
 * if (permit.waiting) showWhy(permit.reasons) // e «Fallo comunque»: allows(…, force = true)
 * ```
 *
 * Nessun permesso oltre a `ACCESS_NETWORK_STATE` (gia' nel manifesto di `engine-net`); nessuna libreria.
 *
 * **Il calore** non e' affidabile su tutti i telefoni: dove `getCurrentThermalStatus` non c'e' (sotto l'API 29) o resta
 * a zero, il telefono non risulta mai caldo, e niente si ferma per quello.
 */
object EngineConditions {
  private const val RetryMillis = 1_000L
  private const val MaxRetryMillis = 60_000L
  private val installLock = Any()
  private var installed: Job? = null

  /** Le condizioni adesso (una lettura, senza la tenuta della rete). */
  fun current(context: Context): FluidConditions =
    conditionsOf(EngineNetwork.current(context), readSignals(context.applicationContext))

  /**
   * Le condizioni ogni volta che cambiano. La rete ha la tenuta di [EngineNetwork.states] ([graceMillis]): una
   * micro-caduta non ferma niente.
   */
  fun states(context: Context, graceMillis: Long = EngineNetworkDefaults.OfflineGraceMillis): Flow<FluidConditions> {
    val app = context.applicationContext
    return combine(EngineNetwork.states(app, graceMillis), signals(app)) { network, signals -> conditionsOf(network, signals) }
      .distinctUntilChanged()
  }

  /**
   * Tiene aggiornato [FluidDeviceConditions] per tutta la vita del processo. Si chiama una volta (nell'`Application`, o
   * da chi ne ha bisogno: `FluidTransfers.open` lo fa da se'); le chiamate dopo la prima non fanno niente. Costa un
   * callback di rete e un ricevitore: niente lavoro finche' non cambia qualcosa.
   */
  fun install(context: Context) {
    synchronized(installLock) {
      if (installed?.isActive == true) return
      val app = context.applicationContext
      // Tutto fuori dal thread principale: la prima lettura arriva qualche millisecondo dopo, e intanto vale `Unknown`.
      installed = CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        states(app)
          .retryWhen { cause, attempt ->
            // Android puo' rifiutare un callback o un ricevitore (un'app con troppe richieste aperte riceve
            // `TooManyRequestsException`): non e' un motivo per far cadere l'app. Si riprova con pause crescenti, da
            // un secondo a un minuto; intanto vale l'ultima lettura.
            if (cause is CancellationException) return@retryWhen false
            runCatching { android.util.Log.w("EngineConditions", "lettura delle condizioni rifiutata, si riprova", cause) }
            delay((RetryMillis shl attempt.coerceAtMost(6L).toInt()).coerceAtMost(MaxRetryMillis))
            true
          }
          .collect { FluidDeviceConditions.publish(it) }
      }
    }
  }

  /** Vero se [install] sta aggiornando [FluidDeviceConditions]. */
  val isInstalled: Boolean get() = synchronized(installLock) { installed?.isActive == true }

  /** Smette di aggiornare [FluidDeviceConditions] (per le prove). */
  fun uninstall() {
    synchronized(installLock) {
      installed?.cancel()
      installed = null
    }
  }

  /**
   * La regola, pura: dalla rete e dai segnali alle condizioni.
   * - il risparmio dati conta solo su una rete a consumo (Android limita solo li');
   * - il calore: nessuno e leggero → `Nominal`, moderato → `Fair`, severo → `Serious`, critico e oltre → `Critical`
   *   (i gradini di Apple; vedi [FluidThermal]).
   */
  internal fun conditionsOf(network: EngineNetworkState, signals: EngineDeviceSignals): FluidConditions {
    val expensive = network.online && (network.metered || network.roaming)
    return FluidConditions(
      online = network.online,
      connected = network.connected,
      metered = network.online && network.metered,
      roaming = network.online && network.roaming,
      dataSaver = signals.restrictBackground && expensive,
      powerSave = signals.powerSave,
      thermal = thermalOf(signals.thermalStatus),
      charging = signals.plugged,
      known = true,
    )
  }

  internal fun thermalOf(status: Int): FluidThermal = when {
    status <= PowerManagerThermalLight -> FluidThermal.Nominal
    status == PowerManagerThermalModerate -> FluidThermal.Fair
    status == PowerManagerThermalSevere -> FluidThermal.Serious
    else -> FluidThermal.Critical
  }

  // Le costanti di PowerManager (API 29), scritte qui per leggerle anche sotto.
  private const val PowerManagerThermalLight = 1
  private const val PowerManagerThermalModerate = 2
  private const val PowerManagerThermalSevere = 3

  internal fun readSignals(app: Context): EngineDeviceSignals {
    val connectivity = app.getSystemService(ConnectivityManager::class.java)
    val power = app.getSystemService(PowerManager::class.java)
    val battery = runCatching { app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
    return EngineDeviceSignals(
      restrictBackground = runCatching {
        connectivity?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
      }.getOrDefault(false),
      powerSave = runCatching { power?.isPowerSaveMode == true }.getOrDefault(false),
      thermalStatus = if (Build.VERSION.SDK_INT >= 29) runCatching { power?.currentThermalStatus ?: 0 }.getOrDefault(0) else 0,
      plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
    )
  }

  /** I segnali, riletti a ogni annuncio di Android che li riguarda. */
  internal fun signals(app: Context): Flow<EngineDeviceSignals> = callbackFlow {
    trySend(readSignals(app))
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        trySend(readSignals(app))
      }
    }
    val filter = IntentFilter().apply {
      addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
      addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED)
      addAction(Intent.ACTION_POWER_CONNECTED)
      addAction(Intent.ACTION_POWER_DISCONNECTED)
    }
    // Annunci di sistema: dall'API 33 si dichiara che non arrivano da altre app.
    if (Build.VERSION.SDK_INT >= 33) {
      app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
      app.registerReceiver(receiver, filter)
    }
    val power = app.getSystemService(PowerManager::class.java)
    val thermal: Any? = if (Build.VERSION.SDK_INT >= 29 && power != null) {
      val listener = PowerManager.OnThermalStatusChangedListener { trySend(readSignals(app)) }
      runCatching { power.addThermalStatusListener(DirectExecutor, listener) }.map { listener }.getOrNull()
    } else {
      null
    }
    awaitClose {
      runCatching { app.unregisterReceiver(receiver) }
      if (Build.VERSION.SDK_INT >= 29 && thermal is PowerManager.OnThermalStatusChangedListener) {
        runCatching { power?.removeThermalStatusListener(thermal) }
      }
    }
  }.conflate().distinctUntilChanged()

  private object DirectExecutor : Executor {
    override fun execute(command: Runnable) = command.run()
  }
}
