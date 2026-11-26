package dev.antigravity.fluidengine.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import dev.antigravity.fluidengine.foundation.EngineBuild
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** La scelta dell'utente sulla diagnostica: chiesta una volta, cambiabile nelle impostazioni. */
enum class FluidDiagnosticsConsent {
  /** Non ancora chiesta: i rapporti si raccolgono sul telefono e non partono. */
  NotAsked,

  /** Sì: i rapporti partono verso lo sviluppatore, al massimo una volta al giorno. */
  Granted,

  /** No: niente si raccoglie e la coda si svuota. Il gestore dei crash resta e passa ogni crash al sistema. */
  Denied,
}

/**
 * Come si comporta la diagnostica di un'app (F8.24).
 *
 * @param appName il nome dell'app nel foglio del consenso («Aiuta a migliorare Pampa Notes»).
 * @param endpoint dove partono i rapporti (il Worker di `engine-diagnostics/worker`, `…/v1/reports`). Nullo: i rapporti
 *   restano sul telefono e il modulo non apre mai la rete (si vedono nella pagina dei dati e in una segnalazione).
 * @param crashes il gestore dei crash.
 * @param systemExits le chiusure decise dal sistema (ANR, memoria, crash nativi), da Android 11.
 * @param frames i fotogrammi lenti per pagina, con JankStats.
 * @param sendIntervalMillis ogni quanto partono, al massimo: di serie un giorno, come MetricKit, che consegna i rapporti
 *   una volta al giorno (developer.apple.com, MXMetricManager; fiducia alta).
 */
class FluidDiagnosticsConfig(
  val appName: String,
  val endpoint: String? = null,
  val crashes: Boolean = true,
  val systemExits: Boolean = true,
  val frames: Boolean = true,
  val maxRecords: Int = FluidDiagnosticsQueue.DefaultMaxRecords,
  val maxBytes: Long = FluidDiagnosticsQueue.DefaultMaxBytes,
  val sendIntervalMillis: Long = 24L * 60 * 60 * 1000,
) {
  init {
    require(endpoint == null || endpoint.startsWith("https://") || endpoint.startsWith("http://127.0.0.1") ||
      endpoint.startsWith("http://localhost") || endpoint.startsWith("http://10.0.2.2")) {
      "La diagnostica parte solo in HTTPS (o verso il PC, per le prove)"
    }
  }
}

/** Com'è andato un invio. */
sealed class FluidDiagnosticsSendResult {
  /** Partiti [count] rapporti, tolti dalla coda. */
  class Sent(val count: Int) : FluidDiagnosticsSendResult()

  /** La coda era vuota. */
  data object NothingToSend : FluidDiagnosticsSendResult()

  /** Senza il sì dell'utente, o senza indirizzo: niente è partito, e niente parte mai. */
  data object NotAllowed : FluidDiagnosticsSendResult()

  /** Il server ha risposto [status] (o non ha risposto: 0). I rapporti restano in coda per la prossima volta. */
  class Failed(val status: Int) : FluidDiagnosticsSendResult()
}

/**
 * Come parte un invio: un POST del [body] JSON a [url], che torna il codice HTTP (0 senza risposta). Di serie
 * `HttpURLConnection` senza cookie e senza dati del telefono nelle intestazioni; le prove ne passano uno finto.
 */
fun interface FluidDiagnosticsTransport {
  fun post(url: String, body: ByteArray): Int

  companion object {
    /** Il trasporto di serie. */
    val Http: FluidDiagnosticsTransport = FluidDiagnosticsTransport { url, body ->
      runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
          connection.requestMethod = "POST"
          connection.connectTimeout = 15_000
          connection.readTimeout = 15_000
          connection.doOutput = true
          connection.useCaches = false
          connection.instanceFollowRedirects = false
          connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
          // Un nome generico: niente modello, niente versione di Android nell'intestazione.
          connection.setRequestProperty("User-Agent", "FluidEngine-Diagnostics/1")
          connection.setFixedLengthStreamingMode(body.size)
          connection.outputStream.use { it.write(body) }
          connection.responseCode
        } finally {
          connection.disconnect()
        }
      }.getOrDefault(0)
    }
  }
}

/**
 * La diagnostica dal campo di un'app (F8.24), come MetricKit: crash, blocchi, chiusure decise dal sistema e fotogrammi
 * lenti per pagina, col consenso chiesto una volta ([FluidDiagnosticsConsentPrompt]), senza contenuti né identità, in
 * coda sul telefono ([queue]) finché l'utente non dice di sì.
 *
 * Si accende una volta nell'`Application`:
 *
 * ```kotlin
 * override fun onCreate() {
 *   super.onCreate()
 *   FluidDiagnostics.install(this, FluidDiagnosticsConfig(appName = "Pampa Notes", endpoint = "https://…/v1/reports"))
 * }
 * ```
 *
 * Poi le pagine si segnano con [FluidDiagnosticsPage], e le impostazioni mostrano [FluidDiagnosticsSettingsGroup].
 * Il lavoro (leggere la coda, le chiusure di sistema, l'invio) va su un filo in sottofondo; l'installazione costa le
 * `SharedPreferences` e due registrazioni.
 */
class FluidDiagnostics internal constructor(
  private val context: Context,
  val config: FluidDiagnosticsConfig,
  private val preferences: SharedPreferences,
  /** La coda dei rapporti sul telefono. */
  val queue: FluidDiagnosticsQueue,
  internal val source: FluidDiagnosticsSource,
  private val transport: FluidDiagnosticsTransport,
  private val clock: () -> Long,
  private val io: CoroutineDispatcher,
) {
  private val scope = CoroutineScope(SupervisorJob() + io)
  private val sendLock = Mutex()
  internal val frameAggregator = FluidFrameAggregator()
  internal var frameTracker: FluidFrameTracker? = null

  private val mutableConsent = MutableStateFlow(readConsent())

  /** La scelta dell'utente. */
  val consent: StateFlow<FluidDiagnosticsConsent> = mutableConsent.asStateFlow()

  /** Vero se i rapporti si raccolgono (prima della scelta e col sì; col no mai). */
  val isCollecting: Boolean get() = mutableConsent.value != FluidDiagnosticsConsent.Denied

  /**
   * La scelta dell'utente: [granted] vero per il sì, falso per il no. Col no la coda si svuota subito e non si raccoglie
   * più niente; col sì parte un invio (se c'è un indirizzo e qualcosa in coda).
   */
  fun setConsent(granted: Boolean) {
    val value = if (granted) FluidDiagnosticsConsent.Granted else FluidDiagnosticsConsent.Denied
    preferences.edit().putInt(KeyConsent, value.ordinal).putLong(KeyConsentAt, clock()).apply()
    mutableConsent.value = value
    if (granted) {
      scope.launch { sendIfDue(force = true) }
    } else {
      frameAggregator.snapshot(FluidDiagnosticsRecord.dayOf(clock()), reset = true)
      scope.launch { queue.clear() }
    }
  }

  /**
   * Torna a «non ancora chiesta»: il foglio del consenso si riapre. Per quando cambia cosa si raccoglie (una scelta data
   * per una cosa non vale per un'altra). La coda resta com'è: col no di prima è già vuota.
   */
  fun askAgain() {
    preferences.edit().remove(KeyConsent).putLong(KeyConsentAt, clock()).apply()
    mutableConsent.value = FluidDiagnosticsConsent.NotAsked
  }

  /** Cancella i rapporti in coda e i fotogrammi non ancora passati alla coda («Cancella i dati»). */
  fun clear() {
    frameAggregator.snapshot(FluidDiagnosticsRecord.dayOf(clock()), reset = true)
    scope.launch { queue.clear() }
  }

  /** I fotogrammi di oggi non ancora passati alla coda, per pagina (la galleria li mostra dal vivo). */
  fun pendingFrames(): List<FluidPageFrames> = frameAggregator.snapshot(FluidDiagnosticsRecord.dayOf(clock()), reset = false)

  /**
   * Manda i rapporti in coda adesso, se l'utente ha detto di sì e c'è un indirizzo. A gruppi di [BatchSize]; il server
   * li prende per [FluidDiagnosticsRecord.id], quindi un gruppo mandato due volte conta una volta sola.
   */
  suspend fun send(): FluidDiagnosticsSendResult = withContext(io) {
    sendLock.withLock { sendUnlocked() }
  }

  /** [send], se è passato [FluidDiagnosticsConfig.sendIntervalMillis] dall'ultimo invio riuscito (o [force]). */
  internal suspend fun sendIfDue(force: Boolean = false): FluidDiagnosticsSendResult {
    if (mutableConsent.value != FluidDiagnosticsConsent.Granted || config.endpoint == null) return FluidDiagnosticsSendResult.NotAllowed
    val last = preferences.getLong(KeyLastSent, 0)
    if (!force && clock() - last < config.sendIntervalMillis) return FluidDiagnosticsSendResult.NothingToSend
    return send()
  }

  private fun sendUnlocked(): FluidDiagnosticsSendResult {
    val endpoint = config.endpoint
    if (mutableConsent.value != FluidDiagnosticsConsent.Granted || endpoint == null) return FluidDiagnosticsSendResult.NotAllowed
    val records = queue.records()
    if (records.isEmpty()) return FluidDiagnosticsSendResult.NothingToSend
    var sent = 0
    for (batch in batches(records)) {
      val body = buildJsonObject {
        put("schema", SchemaVersion)
        put("reports", JsonArray(batch.map { it.toJson() }))
      }.toString().toByteArray(Charsets.UTF_8)
      val status = transport.post(endpoint, body)
      if (status !in 200..299) return FluidDiagnosticsSendResult.Failed(status)
      queue.remove(batch.map { it.id }.toSet())
      sent += batch.size
    }
    preferences.edit().putLong(KeyLastSent, clock()).apply()
    return FluidDiagnosticsSendResult.Sent(sent)
  }

  /** I gruppi da mandare: al massimo [BatchSize] rapporti e [BatchBytes] byte ciascuno. */
  internal fun batches(records: List<FluidDiagnosticsRecord>): List<List<FluidDiagnosticsRecord>> {
    val out = ArrayList<List<FluidDiagnosticsRecord>>()
    var current = ArrayList<FluidDiagnosticsRecord>()
    var bytes = 0
    for (record in records) {
      val size = record.toJsonString().length
      if (current.isNotEmpty() && (current.size >= BatchSize || bytes + size > BatchBytes)) {
        out += current
        current = ArrayList()
        bytes = 0
      }
      current += record
      bytes += size
    }
    if (current.isNotEmpty()) out += current
    return out
  }

  /** Scrive il crash di [error] nella coda, se si raccoglie. Dal filo che muore: sincrono. */
  internal fun recordCrash(error: Throwable, mainThread: Boolean) {
    if (!isCollecting) return
    queue.append(FluidDiagnosticsRecord.crash(source, clock(), FluidCrash.describe(error, mainThread)), durable = true)
  }

  /** Legge le chiusure decise dal sistema dopo l'ultimo avvio (Android 11+) e le mette in coda. */
  internal fun readSystemExits() {
    if (Build.VERSION.SDK_INT < 30) return
    val manager = context.getSystemService(ActivityManager::class.java) ?: return
    val since = preferences.getLong(KeyExitsSeen, -1)
    if (since < 0) {
      // Il primo avvio con la diagnostica: le chiusure di prima non sono di questa raccolta.
      preferences.edit().putLong(KeyExitsSeen, clock()).apply()
      return
    }
    val (exits, newest) = FluidExitReasons.read(manager, context.packageName, since)
    if (newest > since) preferences.edit().putLong(KeyExitsSeen, newest).apply()
    if (!isCollecting) return
    exits.forEach { (at, info) -> queue.append(FluidDiagnosticsRecord.exit(source, at, info)) }
  }

  /** Passa i fotogrammi sommati alla coda (l'app è uscita dallo schermo). */
  internal fun flushFrames() {
    val frames = frameAggregator.snapshot(FluidDiagnosticsRecord.dayOf(clock()), reset = true)
    if (!isCollecting) return
    queue.mergeFrames(source, clock(), frames)
  }

  internal fun launchBackground(block: suspend () -> Unit) {
    scope.launch { block() }
  }

  private fun readConsent(): FluidDiagnosticsConsent =
    FluidDiagnosticsConsent.entries.getOrNull(preferences.getInt(KeyConsent, 0)) ?: FluidDiagnosticsConsent.NotAsked

  companion object {
    /** La versione del formato che parte verso il server. */
    const val SchemaVersion: Int = 1

    /** Quanti rapporti al massimo in un invio. */
    const val BatchSize: Int = 50

    /** Quanti byte al massimo in un invio (il server ne accetta 128 KiB). */
    const val BatchBytes: Int = 96 * 1024

    /** Il file delle `SharedPreferences` della diagnostica. */
    const val PreferencesName: String = "fluid-diagnostics"

    private const val KeyConsent = "consenso"
    private const val KeyConsentAt = "consenso-quando"
    private const val KeyLastSent = "ultimo-invio"
    private const val KeyExitsSeen = "chiusure-viste"

    /** Quanto aspetta il primo lavoro dopo l'avvio, per non pesare sul primo fotogramma (a occhio, 26/11/2026). */
    internal const val StartDelayMillis: Long = 5_000

    @Volatile
    private var installed: FluidDiagnostics? = null

    /** La diagnostica installata in questo processo, o nulla. */
    fun get(): FluidDiagnostics? = installed

    /**
     * Accende la diagnostica nel processo, una volta: il gestore dei crash, i fotogrammi delle attività e, cinque
     * secondi dopo, la lettura delle chiusure di sistema e l'invio (solo col sì). Una seconda chiamata torna la prima.
     */
    fun install(application: Application, config: FluidDiagnosticsConfig): FluidDiagnostics {
      installed?.let { return it }
      synchronized(this) {
        installed?.let { return it }
        val diagnostics = create(application, config, FluidDiagnosticsTransport.Http, System::currentTimeMillis, Dispatchers.IO)
        diagnostics.start(application)
        installed = diagnostics
        return diagnostics
      }
    }

    /** La diagnostica senza installarla nel processo: le prove e le scene della galleria. */
    internal fun create(
      context: Context,
      config: FluidDiagnosticsConfig,
      transport: FluidDiagnosticsTransport,
      clock: () -> Long,
      io: CoroutineDispatcher,
      directory: File = File(context.noBackupFilesDir, "fluid-diagnostics"),
    ): FluidDiagnostics {
      val app = context.applicationContext
      return FluidDiagnostics(
        context = app,
        config = config,
        preferences = app.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE),
        queue = FluidDiagnosticsQueue(File(directory, "coda.jsonl"), config.maxRecords, config.maxBytes),
        source = describeSource(app),
        transport = transport,
        clock = clock,
        io = io,
      )
    }

    /** L'app e il telefono, senza niente della persona. */
    internal fun describeSource(context: Context): FluidDiagnosticsSource {
      val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
      val code = if (info == null) 0L else if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
      val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
      val display = runCatching { context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) }.getOrNull()
      return FluidDiagnosticsSource(
        appId = context.packageName,
        appVersion = info?.versionName ?: "",
        versionCode = code,
        engineVersion = EngineBuild.VERSION,
        buildType = if (debuggable) "debug" else "release",
        manufacturer = Build.MANUFACTURER.orEmpty().lowercase(),
        model = Build.MODEL.orEmpty(),
        sdk = Build.VERSION.SDK_INT,
        refreshRateHz = Math.round(display?.refreshRate ?: 60f),
      )
    }

    /** Solo per le prove: dimentica l'installazione del processo. */
    internal fun resetForTesting() {
      installed = null
    }
  }

  internal fun start(application: Application) {
    if (config.crashes) {
      FluidCrashHandler.install { _, error, mainThread -> recordCrash(error, mainThread) }
    }
    if (config.frames) {
      val tracker = FluidFrameTracker(frameAggregator) { launchBackground { flushFrames() } }
      frameTracker = tracker
      application.registerActivityLifecycleCallbacks(tracker)
    }
    launchBackground {
      queue.refreshCount()
      delay(StartDelayMillis)
      if (config.systemExits) readSystemExits()
      sendIfDue()
    }
  }
}
