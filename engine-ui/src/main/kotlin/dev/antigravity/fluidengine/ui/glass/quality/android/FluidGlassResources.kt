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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.MainThread
import androidx.annotation.RestrictTo
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryBudget
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
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
  @Volatile var rejectedUpgrade: RejectedGlassUpgrade? = null
}

/** Picco realmente rifiutato dopo la promozione, fino al cambio della spesa o all’uscita del lettore. */
internal class RejectedGlassUpgrade(val reader: Any, val bytes: Long, val optics: GlassOptics)

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
  /** F8.18b: la regola delle pagine nella memoria grafica, rifatta a ogni [publish]; letta senza lock dal PDF. */
  @Volatile private var hardwarePages =
    viewerHardwarePagesFor(initial.decision.memoryProfile, initial.decision.optics, reading.capacity.gpu)
  @Volatile private var lastReadingNs = System.nanoTime()
  /**
   * La stessa lettura sull'orologio di sistema ([android.os.SystemClock.elapsedRealtimeNanos]): da qui si misura se
   * e' abbastanza fresca per una cattura. Sul telefono e' lo stesso tempo monotono (conta anche il sonno, che invecchia
   * davvero la lettura); sulle prove segue l'orologio dei fotogrammi invece del carico del PC (F8.5d: con `nanoTime`
   * due prove del vetro cadevano solo nei lotti con la JVM lenta).
   */
  @Volatile private var allocationReadingAtNs = android.os.SystemClock.elapsedRealtimeNanos()
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
  /**
   * F8.18 · Il profilo di memoria del processo per i visori a pagine: 0 Pieno, 1 Ridotto, 2 Protetto. Il preset delle
   * pagine sceglie da qui la risoluzione di lettura e quella delle anteprime, senza regolazioni da esporre.
   */
  internal val viewerProfile: Int @Synchronized get() = when (row) {
    GlassMemoryProfile.Full -> 0
    GlassMemoryProfile.Reduced -> 1
    GlassMemoryProfile.Protected -> 2
  }
  /**
   * F8.18b · Se le pagine dei documenti vanno nella memoria grafica, secondo la fascia del processo adesso: la regola è
   * [viewerHardwarePagesFor], con la riga di memoria, il livello ottico più basso fra le finestre in vista e (F7.26) la
   * classe della GPU. Cambia con la fascia (una pressione, una termica severa la accendono); il PDF la legge a ogni pagina
   * resa, senza lock.
   */
  val viewerHardwarePages: Boolean get() = hardwarePages
  private var hidden = false

  /**
   * Un solo registro per le catture del vetro e i decoder, comprese finestre simultanee.
   *
   * Che cosa conta, detto preciso (F7.26, review R2): nella categoria `GLASS` entrano la cattura e il campione di viaggio
   * di ogni vetro grande (quelle sopra 262.144 pixel logici o 1 MiB di stima). **Non** entrano: la sorgente a schermo
   * intero di [dev.antigravity.fluidengine.ui.fluid.glassBackdropSource] quando qualcuno la campiona (un layer fuori
   * schermo grande quanto la pagina, che al renderer costa da uno a due buffer di quella misura: misurati sul Tab S9
   * con una sorgente sola, 19,4 MiB a 1080 × 2340 (due) e 15,6 MiB a 1600 × 2560 (uno), F7.26d ed e. La seconda copia,
   * quando c'è, è nella cache di Skia; la pagina privata dei lettori di una sorgente su richiesta è una registrazione e
   * non ne aggiunge), i materiali esportati dei pannelli con vetro dentro, le maschere d'ombra e le
   * catture piccole. Sono un costo fisso della schermata, fuori dal tetto: il tetto
   * governa quanto il vetro **aggiunge** sopra quel costo, non la memoria grafica intera del vetro.
   */
  val budget = FluidMemoryBudget(row.limits().forHeap(heap))

  /**
   * F7.18: quanti pannelli **davanti** (un modale, un popover, un menu, un foglio, un avviso: il contenuto di
   * `FluidGlassModalHost`) hanno avuto una cattura negata e sono ancora attaccati. Finché è positivo le catture della
   * pagina sotto, velata o coperta, cedono il credito: si rifanno una volta a scala bassa ([GlassYieldScale]) invece di
   * tenere la texture intera. Il pannello davanti ha il tetto prima della pagina che copre. Letto nel disegno dei soli
   * vetri della pagina: un cambio li ridisegna, nessun timer.
   */
  internal val frontDemand = mutableIntStateOf(0)
  private val frontOwners = HashSet<Any>()

  /** Un pannello davanti negato chiede spazio. Idempotente per proprietario. Sul main thread. */
  @MainThread
  internal fun frontDenied(owner: Any) {
    if (frontOwners.add(owner)) {
      frontDemand.intValue = frontOwners.size
      dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("glassFrontDemand", owner) {
        "owners=${frontOwners.size} ${usageLine()}"
      }
    }
  }

  /** Il pannello davanti se ne va (distacco o riuso del nodo): la pagina torna alla sua scala. */
  @MainThread
  internal fun frontReleased(owner: Any) {
    if (frontOwners.remove(owner)) {
      frontDemand.intValue = frontOwners.size
      dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("glassFrontReleased", owner) {
        "owners=${frontOwners.size} ${usageLine()}"
      }
    }
  }

  /** Per la diagnostica: il vetro e il processo nel registro, in byte. */
  internal fun usageLine(): String {
    val glass = budget.usage(FluidMemoryCategory.GLASS)
    val process = budget.usage()
    return "glassUsed=${glass.usedBytes} glassLimit=${glass.limitBytes} processUsed=${process.usedBytes} processLimit=${process.limitBytes}"
  }

  private val prefetchState = MutableStateFlow(false)
  /** Controllare il valore prima di accodare. I produttori in vita possono osservare anche lo stop. */
  val prefetchAllowed: StateFlow<Boolean> = prefetchState.asStateFlow()

  private val trimClients = CopyOnWriteArrayList<(Int) -> Unit>()
  private var thread: HandlerThread? = null
  private var handler: Handler? = null

  init {
    // F7.18: la lettura e la scelta del primo avvio, per misurare la fascia sul dispositivo (`log.tag.FluidGlassFrame`).
    dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("glassBudgetStart", this) {
      val c = reading.capacity
      val m = reading.conditions
      "totalRam=${c.totalRamBytes} maxHeap=${c.maxHeapBytes} memoryClassMiB=${c.heapClassMiB} lowRam=${c.lowRamDevice} " +
        "mediaClass=${c.mediaPerformanceClass} avail=${m.availableBytes} threshold=${m.pressureThresholdBytes} " +
        "heapFraction=${m.heapFraction} thermal=${m.thermal} powerSave=${m.powerSave} previousExit=${signals.previousExit} " +
        "tier=${c.memoryCeiling} row=$row heapRef=$heap optics=${initial.decision.optics} ${signals.soc} " +
        "pages=${if (hardwarePages) "on" else "off"} ${usageLine()}"
    }
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

  internal val allocationReadingFresh: Boolean
    get() = android.os.SystemClock.elapsedRealtimeNanos() - allocationReadingAtNs < 1_000_000_000L

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
    allocationReadingAtNs = android.os.SystemClock.elapsedRealtimeNanos()
    reading = value
    fun update(policy: GlassQualityPolicy) {
      policy.updateCapacity(now, value.capacity)
      policy.updateConditions(now, value.conditions)
      policy.setCeilings(now, value.ceiling, sessionCeiling)
      // F7.26 (R2): dove non si vede niente (nessuna finestra, o la finestra in fondo) si risale subito.
      policy.riseUnseen(now)
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
  internal fun visibleCaptureDenied(
    window: Window?, denied: Boolean, reader: Any? = null, bytes: Long = 0L, now: Long = System.nanoTime(),
  ) {
    val owner = windows[window]
    val state = owner?.render ?: initialRender
    val previous = owner?.policy?.decision?.optics
    state.deniedCaptures += if (denied) 1 else -1
    check(state.deniedCaptures >= 0)
    publishDenials(now)
    if (denied && reader != null && bytes > 0 && owner != null && previous != null &&
      previous > owner.policy.decision.optics && owner.policy.budgetRollbackAt(now)) {
      state.rejectedUpgrade = RejectedGlassUpgrade(reader, bytes, previous)
    }
  }

  @Synchronized
  internal fun forgetRejectedCapture(window: Window?, reader: Any) {
    val state = windows[window]?.render ?: return
    if (state.rejectedUpgrade?.reader === reader) state.rejectedUpgrade = null
  }

  /** Alla posa, preventivo sul profilo candidato: le altre finestre e i byte già in volo contano ancora. */
  internal fun retryFits(policy: GlassQualityPolicy, state: GlassRenderState?): Boolean {
    val demand = state?.rejectedUpgrade ?: return true
    if (policy.nextOptics < demand.optics || policy.nextOptics <= policy.decision.optics) return true
    var profile = policy.nextMemoryProfile
    var heapBytes = policy.decision.heapReferenceBytes
    for (other in windows.values) if (other.visible && other.policy !== policy) {
      profile = minOf(profile, other.policy.decision.memoryProfile)
      heapBytes = minOf(heapBytes, other.policy.decision.heapReferenceBytes)
    }
    val candidate = profile.limits().forHeap(heapBytes)
    // Preventivo soltanto. La prenotazione effettiva resta atomica sul registro di processo.
    return demand.bytes <= candidate.glassBytes - budget.usage(FluidMemoryCategory.GLASS).usedBytes &&
      demand.bytes <= candidate.processBytes - budget.usage().usedBytes
  }

  private fun publishDenials(now: Long = System.nanoTime()) {
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
    var nextOptics = GlassOptics.Full
    var prefetch = sessionCeiling == GlassOptics.Full && !pressured && !hidden
    initialRender.optics = initial.decision.optics
    for (window in windows.values) {
      val decision = window.policy.decision
      window.render.optics = decision.optics
      if (!window.visible) continue
      nextRow = minOf(nextRow, decision.memoryProfile)
      nextHeap = minOf(nextHeap, decision.heapReferenceBytes)
      nextOptics = minOf(nextOptics, decision.optics)
      prefetch = prefetch && decision.allowPrefetch
      active = true
    }
    if (!active) {
      nextRow = initial.decision.memoryProfile
      nextHeap = initial.decision.heapReferenceBytes
      nextOptics = initial.decision.optics
      thread?.quitSafely()
      thread = null
      handler = null
    }
    hardwarePages = viewerHardwarePagesFor(nextRow, nextOptics, reading.capacity.gpu)
    noteLevels()
    if (row != nextRow || heap != nextHeap) {
      budget.updateLimits(nextRow.limits().forHeap(nextHeap))
      row = nextRow
      heap = nextHeap
      // F7.18: la riga e l'heap scelti, per leggere il tetto sul dispositivo senza dedurlo dai rifiuti.
      dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics.event("glassBudgetLimits", this) {
        "row=$row heap=$heap optics=${initial.decision.optics} ${usageLine()}"
      }
    }
    prefetchState.value = active && prefetch
  }

  private var initialNoted: GlassQualityDecision? = null

  /** F7.26: la memoria del processo e il registro, coi picchi, per la riga del profilo simulato. Sul main thread. */
  private fun memoryLine(): String {
    val runtime = Runtime.getRuntime()
    val glass = budget.usage(FluidMemoryCategory.GLASS)
    val process = budget.usage()
    val mib = 1_048_576.0
    fun f(bytes: Long) = "%.1f".format(java.util.Locale.ROOT, bytes / mib)
    return "memoria javaUsata=${f(runtime.totalMemory() - runtime.freeMemory())} javaMax=${f(runtime.maxMemory())} " +
      "nativa=${f(android.os.Debug.getNativeHeapAllocatedSize())} heapRif=${f(heap)} " +
      "vetro=${f(glass.usedBytes)}/${f(glass.peakBytes)}/${f(glass.limitBytes)} " +
      "processo=${f(process.usedBytes)}/${f(process.peakBytes)}/${f(process.limitBytes)} rifiuti=${process.refusals} MiB"
  }

  /**
   * F7.26: una riga per ogni cambio di livello di una finestra (e della decisione d'avvio), con il motivo. Solo nelle
   * build di debug e bench, e solo col profilo simulato acceso ([GlassVariantSignals.note]): in release non costa niente.
   */
  private fun noteLevels() {
    if (!GlassVariantSignals.noting) return
    if (initial.decision !== initialNoted) {
      initialNoted = initial.decision
      GlassVariantSignals.note { levelLine("avvio", initial) }
    }
    for (window in windows.values) {
      val decision = window.policy.decision
      if (decision === window.noted) continue
      window.noted = decision
      GlassVariantSignals.note { levelLine("finestra@${Integer.toHexString(System.identityHashCode(window))}", window.policy) }
    }
  }

  private fun levelLine(owner: String, policy: GlassQualityPolicy): String {
    val d = policy.decision
    return "livello $owner ottica=${d.optics} riga=${d.memoryProfile} prefetch=${d.allowPrefetch} " +
      "motivo=${policy.lastReason ?: "avvio"} pagine=${if (hardwarePages) "accese" else "spente"} ${usageLine()}"
  }

  /**
   * F7.26: il profilo di dispositivo simulato delle build di prova arriva con l'extra `fluid.glass.device` della prima
   * Activity ([GlassVariantSignals.launch]), prima del suo contenuto: la lettura si rifà subito, e il registro prende la
   * riga del profilo prima della prima cattura. In release [GlassVariantSignals.launch] non fa niente.
   */
  @MainThread
  internal fun launchedWith(activity: android.app.Activity) {
    if (!GlassVariantSignals.launch(activity, refresh = { refresh() }, sample = ::memoryLine)) return
    synchronized(this) {
      refresh()
      GlassVariantSignals.note {
        val c = reading.capacity
        "profilo applicato totalRam=${c.totalRamBytes} maxHeap=${c.maxHeapBytes} memoryClassMiB=${c.heapClassMiB} " +
          "lowRam=${c.lowRamDevice} gpu=${c.gpu} tier=${c.memoryCeiling} thermal=${reading.conditions.thermal} " +
          "ottica=${initial.decision.optics} riga=$row pagine=${if (hardwarePages) "accese" else "spente"} " +
          "letto ${signals.soc} ${usageLine()}"
      }
    }
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

/**
 * F8.18b · **Le pagine nella memoria grafica, per fascia del dispositivo.** Le pagine di un documento per lo schermo (il
 * PDF di engine-viewer) passano nella memoria grafica (`Bitmap.Config.HARDWARE`) sul thread del documento solo sui
 * dispositivi più deboli: così lì il primo disegno di una pagina non carica la texture dentro il fotogramma. Scelta di
 * Alessio del 25 novembre 2026; la fascia è quella che il vetro calcola già (F7), senza una nuova.
 *
 * - **Accese** se la riga di memoria del processo non è Piena ([GlassMemoryProfile.Reduced] o
 *   [GlassMemoryProfile.Protected]: RAM fino a 6 GiB, heap fino a 256 MiB, `isLowRamDevice`, una lettura incompleta, una
 *   pressione vera o un'uscita per memoria della sessione prima), oppure se il vetro è sceso a [GlassOptics.Protected]
 *   (termica severa, fotogrammi persi di fila, una pressione critica): il dispositivo fatica, e la copia fatta prima
 *   toglie lavoro al fotogramma.
 * - **Accese** (F7.26) anche con la riga Piena se la GPU non è di punta ([GlassGpuClass]): il Galaxy S22 (Exynos 2200 o
 *   Snapdragon 8 Gen 1, scaldano e rallentano, [GlassGpuClass.Mid]), le serie A ([GlassGpuClass.Low]), e un SoC che la
 *   tabella non conosce ([GlassGpuClass.Unknown]) finché il vetro non arriva a [GlassOptics.Full] con i fotogrammi
 *   misurati: parte prudente, e se regge le spegne.
 * - **Spente** altrimenti: riga Piena, GPU di punta ([GlassGpuClass.High]) e vetro almeno Ridotto, come il Tab S9 e il
 *   Galaxy S25 di Alessio. Sul Tab S9 non c'era un guadagno misurabile (25 novembre 2026, 120 Hz, slot saltati 6,0 e
 *   6,7 % accese contro 3,2 e 7,7 % spente), e senza la copia il thread del documento lavora meno e non tiene due copie
 *   della pagina nel momento della copia.
 *
 * Fino a F7.26 la fascia guardava solo la memoria e il vetro, e il Galaxy S22 (8 GB, heap 512 MiB) contava come alto.
 */
internal fun viewerHardwarePagesFor(memory: GlassMemoryProfile, optics: GlassOptics, gpu: GlassGpuClass): Boolean =
  memory != GlassMemoryProfile.Full || optics == GlassOptics.Protected || when (gpu) {
    GlassGpuClass.High -> false
    GlassGpuClass.Unknown -> optics != GlassOptics.Full
    GlassGpuClass.Mid, GlassGpuClass.Low -> true
  }
