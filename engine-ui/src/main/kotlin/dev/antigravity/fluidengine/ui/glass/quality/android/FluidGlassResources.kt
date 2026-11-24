package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.view.Window
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityDecision
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.Closeable
import java.util.concurrent.CopyOnWriteArrayList

/** Letto dal renderer. Una variazione invalida anche un vetro fermo. */
internal class GlassRenderState(optics: GlassOptics) {
  var optics by mutableStateOf(optics)
  var scrolling by mutableStateOf(false)
  var windowMotion by mutableStateOf(false)
  var deniedCaptures = 0
}

/** Limiti condivisi dai moduli Fluid nello stesso processo. Accessibile anche prima del primo Composable. */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class FluidGlassResources private constructor(private val app: Application) {
  private val signals = AndroidGlassSignals(app)
  private var reading = signals.read()
  private val sessionCeiling = if (signals.previousExit == null) GlassOptics.Full else GlassOptics.Protected
  private var processDenied = false
  private fun newPolicy() = GlassQualityPolicy(reading.capacity, reading.conditions, reading.ceiling, sessionCeiling).also {
    if (processDenied) it.setVisibleBudgetDenied(System.nanoTime(), true)
  }
  private val initial = newPolicy()
  private val initialRender = GlassRenderState(initial.decision.optics)
  private val windows = LinkedHashMap<Window, GlassWindow>()
  private var row = initial.decision.memoryProfile
  private var heap = initial.decision.heapReferenceBytes
  @Volatile private var lastReadingNs = System.nanoTime()
  private var pressured = false
  private val shown = MutableStateFlow(true)
  /** Il trim UI_HIDDEN sospende i lettori fino al prossimo ingresso in primo piano. */
  val uiVisible: StateFlow<Boolean> = shown.asStateFlow()
  /** Cambia prima di ogni consegna ai clienti, anche mentre si iscrive un decoder. */
  @Volatile var trimGeneration: Long = 0
    private set
  /** Posti condivisi dalle miniature secondo il profilo effettivo del processo. */
  val thumbnailParallelism: Int @Synchronized get() = when (row) {
    GlassMemoryProfile.Full -> 3
    GlassMemoryProfile.Reduced -> 2
    GlassMemoryProfile.Protected -> 1
  }
  private var hidden = false

  /** Un solo registro per tutti i layer e i decoder, comprese finestre simultanee. */
  val budget = FluidMemoryBudget(row.limits().forHeap(heap))

  private val prefetchState = MutableStateFlow(false)
  /** Controllare il valore prima di accodare. I produttori in vita possono osservare anche lo stop. */
  val prefetchAllowed: StateFlow<Boolean> = prefetchState.asStateFlow()

  private val trimClients = CopyOnWriteArrayList<(Int) -> Unit>()
  private var thread: HandlerThread? = null
  private var handler: Handler? = null

  init {
    app.registerComponentCallbacks(object : ComponentCallbacks2 {
      override fun onTrimMemory(level: Int) = trim(level)
      override fun onLowMemory() = trim(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
      override fun onConfigurationChanged(config: Configuration) = Unit
    })
    app.registerActivityLifecycleCallbacks(GlassActivities(this))
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) = refresh()
    }
    val powerChanges = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
    // Prima di API 33 Android protegge questo broadcast di sistema. Non richiedere all'host
    // un permesso privato per riceverlo; il valore viene comunque riletto dal PowerManager.
    if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, powerChanges, Context.RECEIVER_NOT_EXPORTED)
    else app.registerReceiver(receiver, powerChanges)
    if (Build.VERSION.SDK_INT >= 29) {
      try {
        signals.power?.addThermalStatusListener { status -> refresh(status) }
      } catch (_: RuntimeException) {
        // Un servizio non disponibile verrà riletto al prossimo ingresso o richiesta grande.
      }
    }
  }

  /** Registrarsi mentre il modulo possiede cache o lavoro. Chiudere sul main thread alla disattivazione. */
  @MainThread
  fun observeTrim(onTrim: (Int) -> Unit): Closeable {
    // Ogni iscrizione ha un'identità propria, anche se due proprietari usano la stessa funzione.
    // Il controllo di attività durante una consegna consente a un modulo di disiscriverne un altro.
    var active = true
    val registered: (Int) -> Unit = { level -> if (active) onTrim(level) }
    trimClients.add(registered)
    return Closeable { active = false; trimClients.remove(registered) }
  }

  @Suppress("DEPRECATION")
  @MainThread
  internal fun trim(level: Int) {
    synchronized(this) {
      trimGeneration++
      when {
        level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
          hidden = true
          shown.value = false
          windows.values.toList().forEach { it.setVisible(false) }
        }
        level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
          pressured = true
          applyReading(signals.read().let { it.copy(conditions = it.conditions.copy(lowMemory = true)) })
        }
      }
      publish()
    }
    trimClients.forEach { it(level) }
  }

  internal val allocationReadingFresh: Boolean get() = System.nanoTime() - lastReadingNs < 1_000_000_000L

  /** Ricontrolla disponibilità e heap prima di una richiesta grande se l'ultima lettura è vecchia. Fuori dal draw. */
  @Synchronized
  fun refreshBeforeAllocation() {
    // Un secondo è la massima età scelta per il campione su richiesta (F7.11, 24 novembre 2026).
    if (!allocationReadingFresh) refresh()
  }

  @Synchronized
  internal fun refresh(thermalStatus: Int? = null) {
    refreshReading(signals.read(thermalStatus), System.nanoTime())
  }

  /** Chiamata sotto il lock del processo dal monitor del moto. Una lettura ogni cinque secondi
   * al massimo fra tutte le finestre degradate, senza timer in quiete né nuove richieste di frame. */
  internal fun refreshForRecovery(policy: GlassQualityPolicy, now: Long) {
    if (policy.needsRecoveryReading && now - lastReadingNs >= RecoveryReadingIntervalNs) {
      refreshReading(signals.read(), now)
    }
  }

  private fun refreshReading(value: GlassReading, now: Long) {
    applyReading(value, now)
    pressured = false
    publish()
  }

  private fun applyReading(value: GlassReading, now: Long = System.nanoTime()) {
    lastReadingNs = now
    reading = value
    fun update(policy: GlassQualityPolicy) {
      policy.updateCapacity(now, value.capacity)
      policy.updateConditions(now, value.conditions)
      policy.setCeilings(now, value.ceiling, sessionCeiling)
    }
    update(initial)
    windows.values.forEach { update(it.policy) }
  }

  /** Segnale del produttore visibile. Un rifiuto restringe le quote prima del tentativo successivo. */
  @Synchronized
  fun visibleBudgetDenied(window: Window?, denied: Boolean) {
    val owner = windows[window]
    if (owner == null) processDenied = denied else owner.visibleDenied = denied
    publishDenials()
  }

  /** Ogni lettore segnala una coppia rifiuto/ammissione. Il visore usa il suo segnale indipendente. */
  @Synchronized
  internal fun visibleCaptureDenied(window: Window?, denied: Boolean) {
    val state = renderState(window)
    state.deniedCaptures += if (denied) 1 else -1
    check(state.deniedCaptures >= 0)
    publishDenials()
  }

  private fun publishDenials() {
    val now = System.nanoTime()
    val denied = processDenied || initialRender.deniedCaptures > 0
    initial.setVisibleBudgetDenied(now, denied)
    for (owner in windows.values) owner.policy.setVisibleBudgetDenied(now,
      denied || owner.visibleDenied || owner.render.deniedCaptures > 0)
    publish()
  }

  @MainThread
  @Synchronized
  internal fun scrollStarted(window: Window): Closeable {
    val lease = acquire(window)
    val watch = windows.getValue(window)
    watch.scrollSources++
    watch.scrollChanged()
    return Closeable {
      synchronized(this) {
        watch.scrollSources--
        watch.scrollChanged()
        lease.close()
      }
    }
  }

  /** Snapshot della finestra, oppure del primo lavoro ancora privo di finestra. */
  @Synchronized
  internal fun decision(window: Window?): GlassQualityDecision = windows[window]?.policy?.decision ?: initial.decision

  @Synchronized
  internal fun renderState(window: Window?): GlassRenderState = windows[window]?.render ?: initialRender

  @MainThread
  @Synchronized
  internal fun acquire(window: Window): Closeable {
    val watch = windows[window] ?: GlassWindow(this, window, newPolicy().also {
      if (initialRender.deniedCaptures > 0) it.setVisibleBudgetDenied(System.nanoTime(), true)
    }).also {
      windows[window] = it
      it.install()
    }
    watch.owners++
    var open = true
    return Closeable {
      synchronized(this) {
        if (open && --watch.owners == 0) {
          watch.uninstall()
          windows.remove(window)
          publish()
        }
        open = false
      }
    }
  }

  @MainThread
  @Synchronized
  internal fun foreground() {
    hidden = false
    shown.value = true
    refresh()
    windows.values.toList().forEach { it.checkVisibility() }
  }

  internal fun mayShow() = !hidden

  internal fun metricsHandler(): Handler = handler ?: HandlerThread("FluidGlassFrames").let {
    it.start()
    thread = it
    Handler(it.looper).also { h -> handler = h }
  }

  /** Sotto il lock del processo, solo ai cambi di decisione o visibilità. */
  internal fun publish() {
    var active = false
    var nextRow = GlassMemoryProfile.Full
    var nextHeap = Long.MAX_VALUE
    var prefetch = sessionCeiling == GlassOptics.Full && !pressured && !hidden
    initialRender.optics = initial.decision.optics
    for (window in windows.values) {
      val decision = window.policy.decision
      window.render.optics = decision.optics
      if (!window.visible) continue
      nextRow = minOf(nextRow, decision.memoryProfile)
      nextHeap = minOf(nextHeap, decision.heapReferenceBytes)
      prefetch = prefetch && decision.allowPrefetch
      active = true
    }
    if (!active) {
      nextRow = initial.decision.memoryProfile
      nextHeap = initial.decision.heapReferenceBytes
      thread?.quitSafely()
      thread = null
      handler = null
    }
    if (row != nextRow || heap != nextHeap) {
      budget.updateLimits(nextRow.limits().forHeap(nextHeap))
      row = nextRow
      heap = nextHeap
    }
    prefetchState.value = active && prefetch
  }

  companion object {
    private const val RecoveryReadingIntervalNs = 5_000_000_000L

    @Volatile
    private var process: FluidGlassResources? = null

    /** Registro inizializzato da Startup o da get(context), anche per gli adattatori che ricevono solo un File. */
    fun current(): FluidGlassResources = checkNotNull(process) { "Inizializzare FluidGlassResources.get(context) in questo processo" }

    /** Inizializzazione sincrona, idempotente anche nei processi secondari senza AndroidX Startup. */
    fun get(context: Context): FluidGlassResources = process ?: synchronized(this) {
      process ?: FluidGlassResources(context.applicationContext as Application).also { process = it }
    }
  }
}

private fun GlassMemoryProfile.limits() = when (this) {
  GlassMemoryProfile.Full -> FluidMemoryLimits.Full
  GlassMemoryProfile.Reduced -> FluidMemoryLimits.Reduced
  GlassMemoryProfile.Protected -> FluidMemoryLimits.Protected
}
