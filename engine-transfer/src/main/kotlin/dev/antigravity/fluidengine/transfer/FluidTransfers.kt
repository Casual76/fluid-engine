package dev.antigravity.fluidengine.transfer

import android.content.Context
import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.FluidPermit
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.net.EngineConditions
import dev.antigravity.fluidengine.net.EngineHttp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Le regole di lavoro della coda. I tempi sono a occhio (25 novembre 2026) dove non e' scritto altro. */
data class FluidTransferConfig(
  /** Quanti nuovi tentativi da soli dopo un errore che passa (la rete caduta, un server occupato); poi «Riprova». */
  val maxAutoRetries: Int = 3,
  val connectTimeoutMillis: Int = 15_000,
  val readTimeoutMillis: Int = 30_000,
  val userAgent: String = EngineHttp.DefaultUserAgent,
  /** Ogni quanto l'avanzamento arriva all'interfaccia: quattro volte al secondo, mai a ogni pezzo. */
  val progressIntervalMillis: Long = 250L,
  /** Ogni quanto l'avanzamento si scrive su disco durante un trasferimento (lo stato si scrive sempre subito). */
  val persistIntervalMillis: Long = 2_000L,
  /** Ogni quanto, durante un trasferimento, si richiede il permesso alle condizioni (il Wi-Fi che se ne va). */
  val conditionsCheckMillis: Long = 1_000L,
  val bufferBytes: Int = 64 * 1024,
  /** Caricamento a pezzi: la misura di un pezzo. Multipla di 256 KiB, come chiedono Cloud Storage e Drive. */
  val uploadChunkBytes: Int = 2 * 1024 * 1024,
  /** Quanti trasferimenti finiti restano nella lista. */
  val keepFinished: Int = 50,
  /** Un'attesa che WorkManager non sa esprimere (risparmio energetico, calore, «Usa pochi dati»): si riguarda dopo questo tempo. */
  val recheckMillis: Long = 15L * 60L * 1000L,
) {
  init {
    require(uploadChunkBytes > 0 && uploadChunkBytes % (256 * 1024) == 0) { "I pezzi sono multipli di 256 KiB: $uploadChunkBytes" }
    require(bufferBytes >= 4096) { "Un buffer di almeno 4 KiB" }
  }
}

/** Le credenziali di un trasferimento, chieste all'app a ogni tentativo (non si salvano). */
fun interface FluidTransferAuthorizer {
  suspend fun headers(request: FluidTransferRequest): Map<String, String>

  companion object {
    val None: FluidTransferAuthorizer = FluidTransferAuthorizer { emptyMap() }
  }
}

/**
 * Quando deve girare la coda, nei termini di WorkManager: subito ([runNow]) o quando c'e' una rete che non costa
 * ([unmetered]), quando il telefono e' in carica ([charging]), dopo [delayMillis]. [replace] dice se sostituire un giro
 * gia' in attesa (i vincoli sono cambiati) o accodarsi a quello in corso. [idle]: niente da fare.
 */
class FluidTransferPlan internal constructor(
  val idle: Boolean,
  val runNow: Boolean,
  val unmetered: Boolean,
  val charging: Boolean,
  val delayMillis: Long,
  val replace: Boolean,
) {
  /** Lo stesso piano, non prima di [millis]. */
  fun later(millis: Long): FluidTransferPlan =
    FluidTransferPlan(idle, runNow, unmetered, charging, maxOf(delayMillis, millis), replace)

  override fun toString(): String =
    if (idle) "FluidTransferPlan(idle)" else "FluidTransferPlan(now=$runNow, unmetered=$unmetered, charging=$charging, delay=$delayMillis, replace=$replace)"
}

/**
 * Chi fa girare la coda. Con WorkManager: [FluidTransferWork.scheduler]. Riceve anche il piano [FluidTransferPlan.idle]
 * quando la coda resta senza niente da fare (e nessun giro sta girando): e' il momento di togliere il giro in attesa.
 */
fun interface FluidTransferScheduler {
  fun schedule(plan: FluidTransferPlan)

  companion object {
    /** Nessuno: chi usa la coda chiama [FluidTransfers.runQueue] (le prove, le scene senza sottofondo). */
    val None: FluidTransferScheduler = FluidTransferScheduler { }
  }
}

/** Cosa ha fatto un giro della coda ([FluidTransfers.runQueue]). */
class FluidTransferRunResult internal constructor(
  /** I trasferimenti cominciati o ripresi. */
  val started: Int,
  /** Quelli finiti bene. */
  val completed: Int,
  /** Qualcuno riprovera' da solo fra poco (un errore che passa): WorkManager riparte con le sue pause crescenti. */
  val retryLater: Boolean,
  /** Quelli che aspettano le condizioni. */
  val waiting: Int,
) {
  override fun toString(): String = "FluidTransferRunResult(started=$started, completed=$completed, retryLater=$retryLater, waiting=$waiting)"
}

/**
 * **Il centro trasferimenti** (F8.20, `centro-trasferimenti`): una coda sola e persistita per gli scaricamenti e i
 * caricamenti lunghi. Va avanti con l'app chiusa (WorkManager, col servizio in primo piano di tipo `dataSync`) e riprende
 * dopo un riavvio; si mette in pausa e riprende davvero (`Range` e `If-Range`); rispetta le condizioni (solo Wi-Fi, solo
 * in carica, e la regola di [FluidConditions.allows]: un trasferimento che non puo' partire aspetta, dice il perche' e
 * offre «Fallo comunque»); mostra la velocita' e il tempo che manca; un errore ha il suo motivo e si riprova. Il
 * riferimento Apple sono le sessioni in sottofondo di `URLSession` e gli elenchi di download di Safari e dell'App Store.
 *
 * ```
 * // una volta, di solito nell'Application:
 * val transfers = FluidTransfers.open(context, authorizer = { req -> mapOf("Authorization" to token()) })
 * transfers.enqueue(FluidTransferRequest.download(url, File(filesDir, "notes.apk"), title = "Pampa Notes 2.4", wifiOnly = true))
 * // l'interfaccia:
 * val items by transfers.items.collectAsState()
 * FluidTransfersPage(items, controls = transfers, onBack = …)          // engine-ui
 * ```
 *
 * Tutto il lavoro gira su [dispatcher] (di serie `Dispatchers.IO`), mai sul thread principale; i comandi
 * ([pause], [resume]…) si possono chiamare dall'interfaccia e tornano subito.
 *
 * @param conditions le condizioni adesso (di serie il posto solo del processo, [FluidDeviceConditions]).
 * @param conditionChanges quando cambiano, i trasferimenti in attesa si riguardano subito (con l'app viva); null per le
 *   prove.
 * @param wall l'ora, per le date dei trasferimenti.
 * @param ticks un orologio monotono in millisecondi, per la velocita'.
 */
class FluidTransfers(
  private val store: FluidTransferStore,
  private val scheduler: FluidTransferScheduler = FluidTransferScheduler.None,
  private val conditions: () -> FluidConditions = { FluidDeviceConditions.current },
  private val authorizer: FluidTransferAuthorizer = FluidTransferAuthorizer.None,
  val config: FluidTransferConfig = FluidTransferConfig(),
  private val wall: () -> Long = System::currentTimeMillis,
  private val ticks: () -> Long = { System.nanoTime() / 1_000_000L },
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
  conditionChanges: Flow<FluidConditions>? = FluidDeviceConditions.state,
) : FluidTransferControls {
  private val scope = CoroutineScope(SupervisorJob() + dispatcher)
  private val http = TransferHttp(config)
  private val lock = Any()
  private val records = LinkedHashMap<String, FluidTransferRecord>()
  private val loadMutex = Mutex()
  private val runMutex = Mutex()
  private val active = ConcurrentHashMap<String, TransferControl>()

  @Volatile private var loaded = false

  /** I trasferimenti gia' provati nel giro in corso: un comando che li rimette in coda li toglie da qui. */
  private val triedThisRun: MutableSet<String> = ConcurrentHashMap.newKeySet()

  /** La velocita' di quello in corso. */
  @Volatile private var live: Pair<String, TransferRate>? = null

  /**
   * I comandi, uno dopo l'altro nell'ordine in cui sono stati chiesti: «togli» seguito da «metti in coda» non si
   * scavalcano. Li esegue un solo consumatore sul dispatcher della coda; [settle] aspetta che arrivino.
   */
  private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)

  private val _items = MutableStateFlow<List<FluidTransferItem>>(emptyList())
  private val _overview = MutableStateFlow(FluidTransferOverview())
  private val _isLoaded = MutableStateFlow(false)

  /** I trasferimenti, nell'ordine in cui sono entrati in coda. */
  val items: StateFlow<List<FluidTransferItem>> = _items.asStateFlow()

  /** Tutti insieme: per la pillola e per la notifica. */
  val overview: StateFlow<FluidTransferOverview> = _overview.asStateFlow()

  /** Vero quando la coda salvata e' stata letta. */
  val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

  /**
   * La notifica del servizio in primo piano: di serie una notifica semplice ([SimpleTransferNotifications]). `engine-notify`
   * (F8.19) la sostituisce con la sua notifica viva, dallo stesso modello ([FluidTransferOverview]).
   */
  @Volatile var notifications: FluidTransferNotifications = SimpleTransferNotifications()

  /** Dove si scrive il file a meta' (il file vero; le prove ci mettono un disco che si riempie). */
  internal var partOutput: (File, Boolean) -> java.io.OutputStream = { file, append -> FileOutputStream(file, append) }

  init {
    scope.launch {
      ensureLoaded()
      for (next in commands) {
        try {
          next()
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (error: Exception) {
          // Un comando che fallisce non ferma quelli dopo; resta scritto nel registro.
          warn("un comando della coda non e' riuscito", error)
        }
      }
    }
    if (conditionChanges != null) {
      scope.launch { conditionChanges.drop(1).collect { onConditionsChanged() } }
    }
  }

  /** Un comando: gira sul dispatcher della coda, dopo la lettura della coda salvata e dopo i comandi chiesti prima. */
  private fun command(block: suspend () -> Unit) {
    commands.trySend(block)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // I comandi
  // ---------------------------------------------------------------------------------------------------------------

  /** Mette in coda [request] e restituisce il suo id. Un id gia' in coda non si duplica: torna quello. */
  fun enqueue(request: FluidTransferRequest): String {
    command {
      val now = wall()
      val existing = synchronized(lock) { records[request.id] }
      if (existing != null && !existing.state.finished) return@command
      write(FluidTransferRecord(request = request, createdAtMillis = now, updatedAtMillis = now))
      trimFinished()
      schedule()
    }
    return request.id
  }

  override fun pause(id: String) {
    // Il freno subito, dal thread di chi chiama: una lettura bloccata si ferma senza aspettare la coda.
    active[id]?.stop(TransferStop.Pause)
    command {
      if (active[id] == null) {
        edit(id) { if (it.state.active) it.copy(state = FluidTransferState.Paused, waitingFor = emptyList()) else it }
      }
      schedule()
    }
  }

  override fun resume(id: String) {
    command {
      edit(id) { if (it.state == FluidTransferState.Paused) it.copy(state = FluidTransferState.Queued) else it }
      triedThisRun.remove(id)
      schedule()
    }
  }

  override fun retry(id: String) {
    command {
      edit(id) {
        if (it.state == FluidTransferState.Failed) {
          it.copy(state = FluidTransferState.Queued, failure = null, attempts = 0, finishedAtMillis = null)
        } else {
          it
        }
      }
      triedThisRun.remove(id)
      schedule()
    }
  }

  override fun force(id: String) {
    command {
      edit(id) {
        if (it.state.active || it.state == FluidTransferState.Paused) {
          it.copy(forced = true, state = if (it.state == FluidTransferState.Running) it.state else FluidTransferState.Queued)
        } else {
          it
        }
      }
      triedThisRun.remove(id)
      schedule()
    }
  }

  override fun cancel(id: String) {
    active[id]?.stop(TransferStop.Cancel)
    command {
      if (active[id] == null) {
        val record = synchronized(lock) { records[id] } ?: return@command
        if (record.state.finished) return@command
        discardPartial(record)
        edit(id) { it.copy(state = FluidTransferState.Cancelled, finishedAtMillis = wall(), waitingFor = emptyList()) }
      }
      schedule()
    }
  }

  override fun clearFinished() {
    command {
      val gone = synchronized(lock) {
        records.values.filter { it.state.finished }.map { it.id }.onEach { records.remove(it) }
      }
      gone.forEach(::forget)
      publish()
      schedule()
    }
  }

  /** Toglie [id] dalla lista: annulla se e' in giro (e cancella il file a meta'). */
  fun remove(id: String) {
    active[id]?.stop(TransferStop.Cancel)
    command {
      // Chi era in corso si ferma da se' (e toglie il file a meta'): si aspetta che lo abbia fatto.
      while (active.containsKey(id)) kotlinx.coroutines.delay(10L)
      val record = synchronized(lock) { records.remove(id) } ?: return@command
      if (!record.state.finished) discardPartial(record)
      forget(id)
      publish()
      schedule()
    }
  }

  /** Il trasferimento [id] adesso, o null. */
  fun item(id: String): FluidTransferItem? = _items.value.firstOrNull { it.id == id }

  /** Aspetta che i comandi gia' chiesti siano arrivati alla coda (per le prove e per chi deve leggere subito dopo). */
  suspend fun settle() {
    val done = CompletableDeferred<Unit>()
    if (commands.trySend { done.complete(Unit) }.isFailure) return
    done.await()
  }

  /**
   * Ferma tutto (per le prove): i trasferimenti in corso si fermano dove sono. Se era la coda dell'app, il prossimo
   * [open] ne apre una nuova (dalla stessa coda salvata) invece di restituire questa, che non lavora piu'.
   */
  fun close() {
    active.values.forEach { it.stop(TransferStop.Shutdown) }
    commands.close()
    scope.cancel()
    synchronized(Companion) { if (shared === this) shared = null }
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Il giro della coda
  // ---------------------------------------------------------------------------------------------------------------

  /**
   * Fa girare la coda: uno alla volta, nell'ordine d'arrivo, ogni trasferimento che puo' partire; quelli che non possono
   * restano in attesa col loro perche'. Torna quando non c'e' piu' niente da fare adesso. Un giro alla volta.
   *
   * [onProgress] riceve il riassunto quattro volte al secondo al massimo: il lavoro di WorkManager ci aggiorna la notifica.
   */
  suspend fun runQueue(onProgress: suspend (FluidTransferOverview) -> Unit = {}): FluidTransferRunResult =
    withContext(dispatcher) {
      runMutex.withLock {
        ensureLoaded()
        triedThisRun.clear()
        var started = 0
        var completed = 0
        var retryLater = false
        while (true) {
          coroutineContext.ensureActive()
          val next = synchronized(lock) {
            records.values
              .filter { it.id !in triedThisRun && (it.state == FluidTransferState.Queued || it.state == FluidTransferState.Waiting) }
              .minByOrNull { it.createdAtMillis }
          } ?: break
          triedThisRun += next.id
          val permit = permitFor(next)
          if (!permit.allowed) {
            edit(next.id) { it.copy(state = FluidTransferState.Waiting, waitingFor = permit.reasons) }
            continue
          }
          started++
          when (execute(next, onProgress)) {
            TransferOutcome.Completed -> completed++
            TransferOutcome.RetryLater -> retryLater = true
            TransferOutcome.Other -> Unit
          }
        }
        triedThisRun.clear()
        val waiting = synchronized(lock) { records.values.count { it.state == FluidTransferState.Waiting } }
        FluidTransferRunResult(started, completed, retryLater, waiting)
      }
    }

  /** Il piano per il prossimo giro, dato lo stato della coda (lo chiama il lavoro di WorkManager alla fine del suo giro). */
  fun plan(replace: Boolean = !runMutex.isLocked): FluidTransferPlan {
    val list = synchronized(lock) { records.values.toList() }
    val now = list.any { it.state == FluidTransferState.Queued || it.state == FluidTransferState.Running }
    if (now) return FluidTransferPlan(false, true, false, false, 0L, replace)
    val waiting = list.filter { it.state == FluidTransferState.Waiting }
    if (waiting.isEmpty()) return FluidTransferPlan(true, false, false, false, 0L, replace)
    val unmetered = waiting.all { item -> item.waitingFor.any { it in UnmeteredReasons } }
    val charging = waiting.all { item -> FluidConditionReason.NotCharging in item.waitingFor }
    val opaque = waiting.any { item -> item.waitingFor.any { it in OpaqueReasons } }
    return FluidTransferPlan(false, false, unmetered, charging, if (opaque) config.recheckMillis else 0L, replace)
  }

  private fun schedule() {
    val plan = plan()
    // Con la coda vuota il piano «niente da fare» va lo stesso a chi la fa girare: toglie il giro rimasto in attesa (il
    // controllo rimandato di chi aspettava), cosi' dopo «Togli» o «Annulla» nessun lavoro resta in coda. Mai mentre un
    // giro sta girando: quello finisce da se' e non ne chiede un altro.
    if (!plan.idle || !runMutex.isLocked) scheduler.schedule(plan)
  }

  private fun onConditionsChanged() {
    command {
      val waiting = synchronized(lock) { records.values.filter { it.state == FluidTransferState.Waiting } }
      var changed = false
      for (record in waiting) {
        val permit = permitFor(record)
        if (permit.allowed) {
          edit(record.id) { if (it.state == FluidTransferState.Waiting) it.copy(state = FluidTransferState.Queued, waitingFor = emptyList()) else it }
          triedThisRun.remove(record.id)
          changed = true
        } else if (permit.reasons != record.waitingFor) {
          edit(record.id) { it.copy(waitingFor = permit.reasons) }
          changed = true
        }
      }
      if (changed) schedule()
    }
  }

  private fun permitFor(record: FluidTransferRecord): FluidPermit =
    conditions().allows(record.request.work, record.needs, force = record.forced)

  private suspend fun execute(start: FluidTransferRecord, onProgress: suspend (FluidTransferOverview) -> Unit): TransferOutcome {
    val control = TransferControl()
    // Prima il freno, poi lo stato: una pausa che arriva in mezzo trova l'uno o l'altro.
    active[start.id] = control
    val running = edit(start.id) {
      if (it.state == FluidTransferState.Queued || it.state == FluidTransferState.Waiting) {
        it.copy(state = FluidTransferState.Running, waitingFor = emptyList())
      } else {
        it
      }
    }
    if (running?.state != FluidTransferState.Running) {
      active.remove(start.id)
      return TransferOutcome.Other
    }
    val rate = TransferRate()
    live = start.id to rate
    val session = Session(start.id, control, rate, onProgress)
    try {
      val done = stopOnCancel(control) {
        if (start.request.direction == FluidTransferDirection.Upload) upload(session) else download(session)
      }
      edit(start.id) {
        it.copy(
          state = FluidTransferState.Completed,
          bytesDone = done,
          bytesTotal = if (it.bytesTotal > 0L) it.bytesTotal else done,
          failure = null,
          waitingFor = emptyList(),
          finishedAtMillis = wall(),
        )
      }
      trimFinished()
      return TransferOutcome.Completed
    } catch (cancelled: CancellationException) {
      // Il sistema ha fermato il lavoro (o la coda si chiude): si riprende da qui al prossimo giro.
      withContext(NonCancellable) {
        session.flush()
        edit(start.id) { if (it.state == FluidTransferState.Running) it.copy(state = FluidTransferState.Queued) else it }
      }
      throw cancelled
    } catch (error: Throwable) {
      session.flush()
      val stop = control.reason
      if (stop != null) return stopped(start.id, stop)
      val failure = when (error) {
        is TransferException -> error.failure
        is java.net.SocketTimeoutException -> FluidTransferFailure.network("tempo scaduto")
        is java.net.UnknownHostException -> FluidTransferFailure.network("server non trovato")
        is java.io.FileNotFoundException -> FluidTransferFailure.storage(error.message)
        is IOException -> FluidTransferFailure.network(error.message)
        else -> FluidTransferFailure.unknown(error.message ?: error.javaClass.simpleName)
      }
      // La rete se n'e' andata a meta': non e' un errore, e' un'attesa.
      val permit = permitFor(synchronized(lock) { records[start.id] } ?: start)
      if (failure.kind == FluidTransferFailure.Network && !permit.allowed) {
        edit(start.id) { it.copy(state = FluidTransferState.Waiting, waitingFor = permit.reasons) }
        return TransferOutcome.Other
      }
      var outcome = TransferOutcome.Other
      edit(start.id) {
        val attempts = it.attempts + 1
        if (failure.retryable && attempts <= config.maxAutoRetries) {
          outcome = TransferOutcome.RetryLater
          it.copy(state = FluidTransferState.Queued, failure = failure, attempts = attempts)
        } else {
          // L'ora di fine anche qui: l'esito del giro (la notifica viva, la pillola) conta i finiti da quell'ora.
          it.copy(state = FluidTransferState.Failed, failure = failure, attempts = attempts, finishedAtMillis = wall())
        }
      }
      return outcome
    } finally {
      active.remove(start.id)
      if (live?.first == start.id) live = null
      publish()
    }
  }

  /**
   * Fa [block] fermando il trasferimento se la coroutine viene annullata (il sistema che ferma il lavoro): una lettura di
   * rete bloccata non sente l'annullamento, la connessione chiusa si'.
   */
  private suspend fun <T> stopOnCancel(control: TransferControl, block: suspend () -> T): T = coroutineScope {
    val finished = java.util.concurrent.atomic.AtomicBoolean(false)
    val watcher = launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        awaitCancellation()
      } finally {
        if (!finished.get()) control.stop(TransferStop.Shutdown)
      }
    }
    try {
      block()
    } finally {
      finished.set(true)
      watcher.cancel()
    }
  }

  private fun stopped(id: String, stop: TransferStop): TransferOutcome {
    when (stop) {
      TransferStop.Pause -> edit(id) { it.copy(state = FluidTransferState.Paused) }
      TransferStop.Cancel -> {
        synchronized(lock) { records[id] }?.let(::discardPartial)
        edit(id) { it.copy(state = FluidTransferState.Cancelled, finishedAtMillis = wall()) }
      }
      is TransferStop.Conditions -> edit(id) { it.copy(state = FluidTransferState.Waiting, waitingFor = stop.reasons) }
      TransferStop.Shutdown -> edit(id) { it.copy(state = FluidTransferState.Queued) }
    }
    return TransferOutcome.Other
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Scaricare
  // ---------------------------------------------------------------------------------------------------------------

  private suspend fun download(session: Session): Long {
    val request = session.record.request
    val target = request.file
    val folder = target.absoluteFile.parentFile ?: throw TransferException(FluidTransferFailure.storage("cartella"))
    if (!folder.exists() && !folder.mkdirs()) throw TransferException(FluidTransferFailure.storage("cartella"))
    // Il parziale e' di questo trasferimento (il suo id nel nome): un altro verso lo stesso file non ci cuce la sua coda.
    val part = partFile(request)
    var offset = if (part.exists()) part.length() else 0L
    var restarted = false
    while (true) {
      val headers = HashMap<String, String>()
      if (offset > 0L) {
        headers["Range"] = "bytes=$offset-"
        session.record.validator?.let { headers["If-Range"] = it }
      }
      val response = http.open("GET", request.url, headers, authorizer.headers(request), session.control::attach)
      try {
        val total: Long
        var resumable = session.record.resumable
        val validator = strongEtag(response.header("ETag")) ?: response.header("Last-Modified")
        when (response.code) {
          206 -> {
            val range = parseContentRange(response.header("Content-Range"))
            // Un server che ignora `If-Range` risponde 206 anche dalla versione nuova: la coda si cuce solo allo stesso
            // file, cioe' con lo stesso validatore e lo stesso totale di prima (quando si sanno).
            val saved = session.record.validator
            val known = session.record.bytesTotal
            val changed = (saved != null && validator != null && saved != validator) ||
              (range != null && range.third > 0L && known > 0L && range.third != known)
            if (range == null || range.first != offset || changed) {
              if (restarted) throw TransferException(FluidTransferFailure.server(206, "Content-Range"))
              part.delete()
              offset = 0L
              restarted = true
              continue
            }
            total = range.third.takeIf { it > 0L }
              ?: response.contentLength.takeIf { it > 0L }?.let { offset + it }
              ?: request.expectedBytes
            resumable = true
          }
          200 -> {
            // Il server non riprende (o il file e' cambiato, e `If-Range` l'ha detto): si ricomincia da capo.
            if (offset > 0L) {
              offset = 0L
              resumable = false
            }
            total = response.contentLength.takeIf { it > 0L } ?: request.expectedBytes
          }
          416 -> {
            val known = session.record.bytesTotal
            if (known > 0L && offset >= known) return finishDownload(session, part, target, offset)
            if (restarted) throw TransferException(FluidTransferFailure.server(416))
            part.delete()
            offset = 0L
            restarted = true
            continue
          }
          else -> throw TransferException(failureFor(response.code))
        }
        // Un 200 e' un file intero nuovo: il suo validatore (anche nessuno) prende il posto di quello di prima.
        val fresh = if (response.code == 200) validator else validator ?: session.record.validator
        session.update { it.copy(bytesTotal = total, validator = fresh, resumable = resumable, bytesDone = offset) }
        session.rate.reset(ticks(), offset)
        var done = offset
        val buffer = ByteArray(config.bufferBytes)
        response.connection.inputStream.use { input ->
          // Il disco pieno a meta' e' il disco, non la rete: `DiskOutputStream` lo dice (e non si riprova da solo).
          DiskOutputStream(onDisk { partOutput(part, offset > 0L) }).use { output ->
            while (true) {
              session.control.check()
              val read = input.read(buffer)
              if (read < 0) break
              output.write(buffer, 0, read)
              done += read
              session.progress(done)
            }
          }
        }
        session.progress(done, force = true)
        // Una pausa o un annullamento arrivati con l'ultima lettura valgono: il parziale non diventa il file.
        session.control.check()
        if (total > 0L && done < total) {
          // Un taglio a meta' non e' un errore del file: si riprende da qui.
          throw TransferException(FluidTransferFailure.network("connessione chiusa a ${done} di $total byte"))
        }
        return finishDownload(session, part, target, done)
      } finally {
        response.connection.disconnect()
      }
    }
  }

  private fun finishDownload(session: Session, part: File, target: File, done: Long): Long {
    val request = session.record.request
    if (request.expectedBytes > 0L && done != request.expectedBytes) {
      part.delete()
      throw TransferException(FluidTransferFailure.integrity("$done byte invece di ${request.expectedBytes}"))
    }
    request.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
      val actual = onDisk { sha256Of(part) }
      if (!actual.equals(expected.trim(), ignoreCase = true)) {
        part.delete()
        throw TransferException(FluidTransferFailure.integrity("impronta diversa"))
      }
    }
    if (target.exists() && !target.delete()) throw TransferException(FluidTransferFailure.storage("il file vecchio non si toglie"))
    if (!part.renameTo(target)) throw TransferException(FluidTransferFailure.storage("il file non si rinomina"))
    return done
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Caricare
  // ---------------------------------------------------------------------------------------------------------------

  private suspend fun upload(session: Session): Long {
    val request = session.record.request
    val source = request.file
    if (!source.isFile) throw TransferException(FluidTransferFailure.storage("il file da mandare non c'e'"))
    val total = source.length()
    return if (request.resumableUpload && total > 0L) uploadInChunks(session, total) else uploadWhole(session, total)
  }

  /** Un invio solo: dopo una pausa ricomincia da capo (il server non sa riprendere). */
  private suspend fun uploadWhole(session: Session, total: Long): Long {
    val request = session.record.request
    session.update { it.copy(bytesTotal = total, bytesDone = 0L, resumable = false) }
    session.rate.reset(ticks(), 0L)
    val headers = HashMap<String, String>()
    request.mimeType?.let { headers["Content-Type"] = it }
    var sent = 0L
    val response = http.open(request.method, request.url, headers, authorizer.headers(request), session.control::attach) { connection ->
      // Un rinvio che tiene il corpo (307, 308) lo rimanda intero: l'avanzamento riparte da zero.
      sent = 0L
      connection.doOutput = true
      connection.setFixedLengthStreamingMode(total)
      connection.outputStream.use { output ->
        onDisk { request.file.inputStream() }.use { input ->
          val buffer = ByteArray(config.bufferBytes)
          while (true) {
            session.control.check()
            val read = onDisk { input.read(buffer) }
            if (read < 0) break
            output.write(buffer, 0, read)
            sent += read
            session.progressBlocking(sent)
          }
        }
      }
    }
    try {
      if (response.code !in 200..299) throw TransferException(failureFor(response.code))
      session.progress(total, force = true)
      return total
    } finally {
      response.connection.disconnect()
    }
  }

  /**
   * A pezzi con `Content-Range`, come le sessioni riprendibili di Cloud Storage e Drive: ogni pezzo riceve `308` con il
   * `Range` confermato; dopo una pausa o un riavvio si chiede al server a che punto e' (un `PUT` vuoto con
   * `Content-Range: bytes *` e il totale).
   */
  private suspend fun uploadInChunks(session: Session, total: Long): Long {
    val request = session.record.request
    var acked = 0L
    if (session.record.uploadAcked > 0L || session.record.bytesDone > 0L) {
      val headers = hashMapOf("Content-Range" to "bytes */$total")
      val response = http.open("PUT", request.url, headers, authorizer.headers(request), session.control::attach) { connection ->
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(0L)
        connection.outputStream.close()
      }
      try {
        when (response.code) {
          308 -> acked = parseAckedRange(response.header("Range"))?.plus(1L) ?: 0L
          in 200..299 -> {
            session.update { it.copy(bytesTotal = total, uploadAcked = total) }
            session.progress(total, force = true)
            return total
          }
          404, 410 -> throw TransferException(FluidTransferFailure.notFound(response.code).withMessage("sessione di caricamento scaduta"))
          else -> throw TransferException(failureFor(response.code))
        }
      } finally {
        response.connection.disconnect()
      }
    }
    session.update { it.copy(bytesTotal = total, uploadAcked = acked, bytesDone = acked, resumable = true) }
    session.rate.reset(ticks(), acked)
    var stalls = 0
    onDisk { RandomAccessFile(request.file, "r") }.use { file ->
      while (acked < total) {
        session.control.check()
        val end = minOf(acked + config.uploadChunkBytes, total) - 1L
        val length = end - acked + 1L
        val headers = hashMapOf("Content-Range" to "bytes $acked-$end/$total")
        request.mimeType?.let { headers["Content-Type"] = it }
        val from = acked
        val response = http.open("PUT", request.url, headers, authorizer.headers(request), session.control::attach) { connection ->
          connection.doOutput = true
          connection.setFixedLengthStreamingMode(length)
          connection.outputStream.use { output ->
            val buffer = ByteArray(config.bufferBytes)
            onDisk { file.seek(from) }
            var left = length
            while (left > 0L) {
              session.control.check()
              val read = onDisk { file.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt()) }
              if (read < 0) throw TransferException(FluidTransferFailure.storage("il file si e' accorciato"))
              output.write(buffer, 0, read)
              left -= read
              session.progressBlocking(from + (length - left))
            }
          }
        }
        try {
          when (response.code) {
            308 -> {
              val next = parseAckedRange(response.header("Range"))?.plus(1L) ?: 0L
              stalls = if (next <= acked) stalls + 1 else 0
              if (stalls >= MaxUploadStalls) throw TransferException(FluidTransferFailure.server(308, "il server non tiene i pezzi"))
              acked = next
            }
            in 200..299 -> acked = total
            404, 410 -> throw TransferException(FluidTransferFailure.notFound(response.code).withMessage("sessione di caricamento scaduta"))
            else -> throw TransferException(failureFor(response.code))
          }
        } finally {
          response.connection.disconnect()
        }
        session.update { it.copy(uploadAcked = acked) }
        session.progress(acked, force = true)
      }
    }
    return total
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Lo stato
  // ---------------------------------------------------------------------------------------------------------------

  /** Un trasferimento in corso: l'avanzamento in memoria, scritto su disco ogni tanto e mostrato quattro volte al secondo. */
  private inner class Session(
    val id: String,
    val control: TransferControl,
    val rate: TransferRate,
    val onProgress: suspend (FluidTransferOverview) -> Unit,
  ) {
    private var lastShown = Long.MIN_VALUE / 2
    private var lastSaved = Long.MIN_VALUE / 2
    private var lastChecked = ticks()
    private var lastSample = Long.MIN_VALUE / 2
    private var pendingBytes: Long? = null

    val record: FluidTransferRecord get() = synchronized(lock) { records.getValue(id) }

    fun update(change: (FluidTransferRecord) -> FluidTransferRecord) {
      edit(id, change)
    }

    /** L'avanzamento a [bytes]: in memoria sempre, all'interfaccia ogni [FluidTransferConfig.progressIntervalMillis]. */
    suspend fun progress(bytes: Long, force: Boolean = false) {
      val now = ticks()
      if (progressInMemory(bytes, now, force)) onProgress(_overview.value)
    }

    /** Lo stesso, da dentro una scrittura bloccante (il corpo di un caricamento): senza la notifica. */
    fun progressBlocking(bytes: Long) {
      progressInMemory(bytes, ticks(), force = false)
    }

    private fun progressInMemory(bytes: Long, now: Long, force: Boolean): Boolean {
      if (now - lastSample >= RateSampleMillis) {
        rate.sample(now, bytes)
        lastSample = now
      }
      if (now - lastChecked >= config.conditionsCheckMillis) {
        lastChecked = now
        val permit = permitFor(record)
        if (!permit.allowed) control.stop(TransferStop.Conditions(permit.reasons))
      }
      pendingBytes = bytes
      if (!force && now - lastShown < config.progressIntervalMillis) return false
      lastShown = now
      val save = force || now - lastSaved >= config.persistIntervalMillis
      if (save) lastSaved = now
      synchronized(lock) { records[id]?.let { records[id] = it.copy(bytesDone = bytes, updatedAtMillis = wall()) } }
      if (save) synchronized(lock) { records[id] }?.let(::persist)
      pendingBytes = null
      publish()
      return true
    }

    /** Scrive l'ultimo avanzamento rimasto in memoria (prima di una pausa, di un errore, di uno stop). */
    fun flush() {
      val bytes = pendingBytes
      synchronized(lock) { records[id]?.let { if (bytes != null) records[id] = it.copy(bytesDone = bytes) } }
      synchronized(lock) { records[id] }?.let(::persist)
      pendingBytes = null
    }
  }

  private suspend fun ensureLoaded() {
    if (loaded) return
    loadMutex.withLock {
      if (loaded) return
      val saved = runCatching { store.load() }.getOrDefault(emptyList())
      synchronized(lock) {
        saved.sortedBy { it.createdAtMillis }.forEach { record ->
          // Un trasferimento «in corso» in una coda appena letta e' stato interrotto (il processo e' morto): riparte.
          records[record.id] = if (record.state == FluidTransferState.Running) record.copy(state = FluidTransferState.Queued) else record
        }
      }
      loaded = true
      _isLoaded.value = true
      publish()
      if (saved.any { it.state.active || it.state == FluidTransferState.Running }) schedule()
    }
  }

  private fun write(record: FluidTransferRecord) {
    synchronized(lock) { records[record.id] = record }
    persist(record)
    publish()
  }

  /**
   * Scrive [record] nella coda salvata. Una coda salvata che non scrive (SQLite pieno o rotto) non ferma quella in
   * memoria: il trasferimento si vede e va avanti, e l'errore resta nel registro. Si perderebbe solo col processo.
   */
  private fun persist(record: FluidTransferRecord) {
    try {
      store.save(record)
    } catch (error: Exception) {
      warn("la coda salvata non ha scritto ${record.id}", error)
    }
  }

  /** Toglie [id] dalla coda salvata, con la stessa regola di [persist]. */
  private fun forget(id: String) {
    try {
      store.delete(id)
    } catch (error: Exception) {
      warn("la coda salvata non ha tolto $id", error)
    }
  }

  /** Cambia [id] con [change], lo salva e lo mostra. Null se non c'e'. */
  private fun edit(id: String, change: (FluidTransferRecord) -> FluidTransferRecord): FluidTransferRecord? {
    val updated = synchronized(lock) {
      val current = records[id] ?: return null
      val next = change(current)
      if (next == current) return current
      next.copy(updatedAtMillis = wall()).also { records[id] = it }
    }
    persist(updated)
    publish()
    return updated
  }

  private fun trimFinished() {
    val gone = synchronized(lock) {
      val finished = records.values.filter { it.state.finished }.sortedByDescending { it.finishedAtMillis ?: it.updatedAtMillis }
      finished.drop(config.keepFinished).map { it.id }.onEach { records.remove(it) }
    }
    gone.forEach(::forget)
  }

  private fun publish() {
    val running = live
    val list = synchronized(lock) {
      records.values.map { record ->
        if (running != null && running.first == record.id) {
          val left = if (record.bytesTotal > 0L) record.bytesTotal - record.bytesDone else -1L
          record.toItem(running.second.bytesPerSecond, running.second.remainingMillis(left, ticks()))
        } else {
          record.toItem()
        }
      }
    }
    _items.value = list
    _overview.value = FluidTransferOverview(list)
  }

  private fun discardPartial(record: FluidTransferRecord) {
    if (record.request.direction == FluidTransferDirection.Download) partFile(record.request).delete()
  }

  private enum class TransferOutcome { Completed, RetryLater, Other }

  companion object {
    @Volatile private var shared: FluidTransfers? = null

    /**
     * La coda dell'app, una per processo: su disco ([SqliteFluidTransferStore]), con WorkManager
     * ([FluidTransferWork.scheduler]) e le condizioni del telefono lette da `engine-net` ([EngineConditions.install]).
     * La prima chiamata decide le opzioni; le altre restituiscono la stessa coda.
     */
    fun open(
      context: Context,
      authorizer: FluidTransferAuthorizer = FluidTransferAuthorizer.None,
      config: FluidTransferConfig = FluidTransferConfig(),
      notifications: FluidTransferNotifications? = null,
    ): FluidTransfers {
      shared?.let { return it }
      synchronized(this) {
        shared?.let { return it }
        val app = context.applicationContext
        EngineConditions.install(app)
        return FluidTransfers(
          store = SqliteFluidTransferStore(app),
          scheduler = FluidTransferWork.scheduler(app),
          authorizer = authorizer,
          config = config,
        ).also { transfers ->
          notifications?.let { transfers.notifications = it }
          shared = transfers
        }
      }
    }

    /** La coda per il lavoro in sottofondo: quella dell'`Application` se e' un [FluidTransferHost], se no [open]. */
    fun from(context: Context): FluidTransfers =
      (context.applicationContext as? FluidTransferHost)?.fluidTransfers ?: open(context)

    /**
     * Il file a meta' dello scaricamento [request]: accanto al file, col nome del file e un'impronta del suo id
     * (`notes.apk.3f2a9c41d07b.part`). Ogni trasferimento ha il suo: due richieste verso lo stesso file non si cuciono.
     */
    fun partFile(request: FluidTransferRequest): File {
      val target = request.file
      val digest = MessageDigest.getInstance("SHA-256").digest(request.id.toByteArray(Charsets.UTF_8))
      val tag = digest.take(6).joinToString("") { "%02x".format(it) }
      return File(target.absoluteFile.parentFile, "${target.name}.$tag.part")
    }

    private const val LogTag = "FluidTransfers"

    /** Un errore che la coda non mostra ma non tace: nel registro di Android (nelle prove sulla JVM, da nessuna parte). */
    private fun warn(message: String, error: Throwable) {
      runCatching { android.util.Log.w(LogTag, message, error) }
    }

    private val UnmeteredReasons = setOf(
      FluidConditionReason.Metered, FluidConditionReason.NotWifi, FluidConditionReason.Roaming, FluidConditionReason.DataSaver,
    )

    /** Le attese che WorkManager non sa esprimere: si riguardano dopo [FluidTransferConfig.recheckMillis]. */
    private val OpaqueReasons = setOf(FluidConditionReason.PowerSave, FluidConditionReason.Hot, FluidConditionReason.LowData)

    private const val RateSampleMillis = 200L
    private const val MaxUploadStalls = 3

    internal fun failureFor(code: Int): FluidTransferFailure = when (code) {
      401, 403, 407 -> FluidTransferFailure.unauthorized(code)
      404, 410 -> FluidTransferFailure.notFound(code)
      else -> FluidTransferFailure.server(code)
    }

    /** Un `ETag` forte (un `If-Range` con un `ETag` debole non vale), o null. */
    internal fun strongEtag(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("W/") }

    internal fun sha256Of(file: File): String {
      val digest = MessageDigest.getInstance("SHA-256")
      file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
          val read = input.read(buffer)
          if (read < 0) break
          digest.update(buffer, 0, read)
        }
      }
      return digest.digest().joinToString("") { "%02x".format(it) }
    }
  }
}

/** Perche' un trasferimento in corso si ferma. */
internal sealed class TransferStop {
  data object Pause : TransferStop()
  data object Cancel : TransferStop()
  data object Shutdown : TransferStop()
  data class Conditions(val reasons: List<FluidConditionReason>) : TransferStop()
}

/**
 * Il freno di un trasferimento in corso: un comando ([stop]) chiude anche la connessione, cosi' una lettura bloccata
 * (un server lento) si ferma subito.
 */
internal class TransferControl {
  @Volatile var reason: TransferStop? = null
    private set

  @Volatile private var connection: HttpURLConnection? = null

  fun attach(connection: HttpURLConnection) {
    this.connection = connection
    if (reason != null) runCatching { connection.disconnect() }
  }

  fun stop(why: TransferStop) {
    if (reason == null) reason = why
    connection?.let { runCatching { it.disconnect() } }
  }

  /** Lancia se qualcuno ha chiesto di fermarsi. */
  fun check() {
    if (reason != null) throw IOException("fermato: $reason")
  }
}
