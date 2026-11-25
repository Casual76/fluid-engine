package dev.antigravity.fluidengine.sync

import android.content.Context
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.foundation.text.FluidTextDiff
import dev.antigravity.fluidengine.storage.EngineInstallId
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Le regole di lavoro della sincronizzazione. I valori di serie vengono dall'Account Pampa (`b/architettura.md` § 5.3). */
data class SyncConfig(
  /** Righe per pagina in lettura (il relay ne da' al massimo 500). */
  val pageSize: Int = 200,
  /** Elementi per invio (il relay accetta 1 MB per invio). */
  val pushBatchSize: Int = 100,
  /** Quanti giri di invio in una sincronizzazione, contando quelli dopo un conflitto. */
  val maxPushRounds: Int = 4,
  /** Quanto resta una pietra tombale confermata: 90 giorni, come le lapidi del relay e di Notes (`TOMBSTONE_RETENTION_DAYS`). */
  val tombstoneRetentionMillis: Long = 90L * 24 * 60 * 60 * 1000,
  /** Il tetto di lavoro per fondere un testo lungo; oltre, si chiede la scelta ([FluidTextDiff.DefaultMaxEdits]). */
  val maxMergeEdits: Int = FluidTextDiff.DefaultMaxEdits,
) {
  init {
    require(pageSize in 1..500) { "pageSize fra 1 e 500: $pageSize" }
    require(pushBatchSize in 1..1000) { "pushBatchSize fra 1 e 1000: $pushBatchSize" }
    require(maxPushRounds >= 1) { "almeno un giro di invio" }
    require(tombstoneRetentionMillis > 0) { "le pietre tombali restano per un tempo positivo" }
  }
}

/** Chi fa partire un invio quando c'e' qualcosa da mandare. Con WorkManager: [FluidSyncWork.scheduler]. */
fun interface SyncScheduler {
  fun requestSync()

  companion object {
    /** Nessuno: l'app chiama [FluidSync.syncAll] quando vuole (le prove, le scene). */
    val None: SyncScheduler = SyncScheduler { }
  }
}

/** Lo stato di tutti gli spazi insieme. */
data class SyncOverview(
  /** Gli elementi con modifiche in attesa, in tutti gli spazi ([SyncSpaceState.pending]). */
  val pending: Int = 0,
  /** I conflitti che aspettano una scelta. */
  val conflicts: Int = 0,
  val syncing: Boolean = false,
  /** L'ultima sincronizzazione riuscita di tutti gli spazi (la piu' vecchia fra le loro), o null. */
  val lastSuccessMillis: Long? = null,
  /** Il primo errore fra gli spazi, o null. */
  val failure: SyncFailure? = null,
  /** Gli spazi la cui ultima sincronizzazione non e' riuscita. */
  val failedSpaces: List<SyncSpaceId> = emptyList(),
  /** Gli spazi aperti. */
  val spaces: Int = 0,
)

/** Le frasi dello stato, nella lingua dell'app: di serie italiane, [english] per l'inglese. */
data class SyncPhrases(
  val pendingOffline: (Int) -> String = { n ->
    if (n == 1) "Una modifica in attesa: parte quando torna la rete." else "$n modifiche in attesa: partono quando torna la rete."
  },
  val offline: String = "Sei offline: le modifiche restano qui.",
  val retryLater: String = "Il servizio chiede di aspettare: riprovo da solo.",
  val rejected: String = "La sincronizzazione si è fermata.",
  val unreadable: String = "Un elemento arrivato non si apre: resta com'era.",
  /** Il sigillo di chi chiama non c'e' ancora (la chiave dell'account): le modifiche aspettano sul telefono. */
  val waitingForKey: String = "Le modifiche aspettano la chiave dell'account: restano qui.",
) {
  companion object {
    fun english() = SyncPhrases(
      pendingOffline = { n -> if (n == 1) "One change waiting: it goes out when you're back online." else "$n changes waiting: they go out when you're back online." },
      offline = "You're offline: your changes stay here.",
      retryLater = "The service asked to wait: retrying on its own.",
      rejected = "Sync stopped.",
      unreadable = "Something that arrived can't be opened: it was left as it was.",
      waitingForKey = "Your changes are waiting for the account key: they stay here.",
    )
  }
}

/**
 * Lo stato da mostrare con i componenti che ci sono gia' (`FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction`):
 * - in corso: [SyncState.SYNCING];
 * - senza rete: [SyncState.OFFLINE], con le modifiche in attesa nella frase;
 * - un errore in tutti gli spazi: [SyncState.ERROR]; in alcuni: [SyncState.PARTIAL] con gli spazi in `failedSections`;
 * - altrimenti [SyncState.IDLE], con l'ora dell'ultima riuscita.
 *
 * [label] da' il nome di uno spazio per `failedSections` (di serie il suo identificativo).
 */
fun SyncOverview.toSyncStatus(
  phrases: SyncPhrases = SyncPhrases(),
  label: (SyncSpaceId) -> String = { it.value },
): SyncStatus {
  if (syncing) return SyncStatus(state = SyncState.SYNCING, lastSuccessfulSyncEpochMillis = lastSuccessMillis)
  val failure = failure ?: return SyncStatus(state = SyncState.IDLE, lastSuccessfulSyncEpochMillis = lastSuccessMillis)
  val message = when (failure) {
    SyncFailure.Offline -> if (pending > 0) phrases.pendingOffline(pending) else phrases.offline
    is SyncFailure.RetryLater -> phrases.retryLater
    is SyncFailure.Rejected -> when (failure.code) {
      "unreadable" -> phrases.unreadable
      "seal" -> phrases.waitingForKey
      else -> phrases.rejected
    }
  }
  val state = when {
    failure == SyncFailure.Offline -> SyncState.OFFLINE
    failedSpaces.size in 1 until spaces -> SyncState.PARTIAL
    else -> SyncState.ERROR
  }
  return SyncStatus(
    state = state,
    lastSuccessfulSyncEpochMillis = lastSuccessMillis,
    message = message,
    failedSections = if (state == SyncState.PARTIAL) failedSpaces.map(label) else emptyList(),
  )
}

/** Cosa ha fatto [FluidSync.syncAll]: un resoconto per spazio. */
class SyncReport(val spaces: List<SyncSpaceReport>) {
  /** Il primo errore, o null. */
  val failure: SyncFailure? get() = spaces.firstNotNullOfOrNull { it.failure }

  /** Vero se conviene riprovare piu' tardi (rete o relay occupato); falso se e' andato tutto bene o se il relay ha rifiutato. */
  val shouldRetry: Boolean
    get() = spaces.any { it.failure == SyncFailure.Offline || it.failure is SyncFailure.RetryLater }

  override fun toString(): String = "SyncReport($spaces)"
}

/**
 * Il motore della sincronizzazione di un dispositivo: apre gli spazi ([space]), li sincronizza ([syncAll]) e ne riassume lo
 * stato ([overview], [status]).
 *
 * Si crea una volta per processo (di solito nell'`Application`, che implementa [FluidSyncHost] per il lavoro in
 * sottofondo):
 * ```
 * val sync = FluidSync.open(context, relay = account.syncRelay(), sealer = account.syncSealer())
 * val notes = sync.space(SyncSpaceId("notes"))
 * notes.edit(id) { text("title", "Spesa"); longText("body", "latte\npane\n") }
 * ```
 *
 * @param store il registro ([SqliteSyncStore] su disco, [InMemorySyncStore] per le prove).
 * @param relay la destinazione ([SyncRelay]).
 * @param sealer il sigillo di chi chiama ([SyncSealer]): l'engine non vede chiavi.
 * @param node il nome del dispositivo per l'orologio logico, unico per installazione ([EngineInstallId]).
 * @param dispatcher dove gira tutto il lavoro: mai il thread principale.
 */
class FluidSync internal constructor(
  internal val store: SyncStore,
  private val relay: SyncRelay,
  internal val sealer: SyncSealer,
  private val nodeProvider: () -> String,
  private val wall: () -> Long,
  internal val dispatcher: CoroutineDispatcher,
  val config: SyncConfig,
  private val scheduler: SyncScheduler,
) {
  constructor(
    store: SyncStore,
    relay: SyncRelay,
    sealer: SyncSealer,
    node: String,
    wall: () -> Long = System::currentTimeMillis,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    config: SyncConfig = SyncConfig(),
    scheduler: SyncScheduler = SyncScheduler.None,
  ) : this(store, relay, sealer, { node }, wall, dispatcher, config, scheduler)

  /**
   * L'orologio logico del dispositivo. Nasce alla prima scrittura o lettura, e alla prima apertura di uno spazio riprende
   * l'ultimo stamp salvato nel registro: monotono anche fra un avvio e l'altro. Il nodo si legge solo allora, fuori dal
   * thread principale.
   */
  val clock: SyncClock by lazy { SyncClock(node = nodeProvider(), wall = wall) }

  @Volatile
  private var clockRestored = false

  /** Riprende l'ultimo stamp salvato. Si chiama dentro il dispatcher, alla prima apertura di uno spazio. */
  internal fun restoreClock() {
    if (clockRestored) return
    synchronized(this) {
      if (clockRestored) return
      store.transaction { SyncStoreCodec.decodeHlc(meta("", SyncSpace.MetaClock)) }?.let(clock::observe)
      clockRestored = true
    }
  }

  private val spaces = ConcurrentHashMap<SyncSpaceId, SyncSpace>()
  private val _overview = MutableStateFlow(SyncOverview())

  /** Lo stato di tutti gli spazi aperti. */
  val overview: StateFlow<SyncOverview> = _overview.asStateFlow()

  private val _status = MutableStateFlow(SyncStatus())

  /** Lo stato nella forma dei componenti dell'engine ([toSyncStatus] con le frasi italiane). */
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  /** Il nome di questo dispositivo nell'orologio logico: e' il [SyncVersion.node] delle sue revisioni. */
  val node: String get() = clock.node

  /** Lo spazio [id], aperto la prima volta che si chiede. */
  fun space(id: SyncSpaceId): SyncSpace = spaces.getOrPut(id) { SyncSpace(id, this) }

  /** Gli spazi aperti. */
  val openSpaces: List<SyncSpace> get() = spaces.values.sortedBy { it.id.value }

  /**
   * Sincronizza ogni spazio aperto e ogni spazio che ha qualcosa nel registro (dopo un avvio a freddo, il lavoro in
   * sottofondo non sa quali spazi l'app aprira'). Uno spazio che fallisce non ferma gli altri.
   */
  suspend fun syncAll(): SyncReport {
    val known = kotlinx.coroutines.withContext(dispatcher) {
      restoreClock()
      store.transaction { spaces() }
    }
    known.forEach { space(SyncSpaceId(it)) }
    return SyncReport(openSpaces.map { it.sync() })
  }

  internal fun requestSync() = scheduler.requestSync()

  internal suspend fun callPull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult {
    val before = clock.wallMillis() - clock.offsetMillis
    val result = relay.pull(space, since, limit)
    adjustClock(before, result.serverTimeMillis)
    return result
  }

  internal suspend fun callPush(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult {
    val before = clock.wallMillis() - clock.offsetMillis
    val result = relay.push(space, batch)
    adjustClock(before, result.serverTimeMillis)
    return result
  }

  /** L'ora del relay a meta' della chiamata contro l'orologio del telefono: la correzione dell'orologio logico. */
  private fun adjustClock(beforeRaw: Long, serverTime: Long) {
    if (serverTime <= 0) return
    val afterRaw = clock.wallMillis() - clock.offsetMillis
    clock.offsetMillis = serverTime - (beforeRaw + afterRaw) / 2
  }

  @Synchronized
  internal fun onSpaceChanged() {
    val states = spaces.values.map { it.id to it.state.value }.filter { !it.second.loading }
    val failed = states.filter { it.second.failure != null }.map { it.first }.sortedBy { it.value }
    val overview = SyncOverview(
      pending = states.sumOf { it.second.pending },
      conflicts = states.sumOf { it.second.conflicts.size },
      syncing = states.any { it.second.syncing },
      lastSuccessMillis = states.mapNotNull { it.second.lastSuccessMillis }.minOrNull()
        ?.takeIf { states.all { s -> s.second.lastSuccessMillis != null } },
      failure = states.firstNotNullOfOrNull { it.second.failure },
      failedSpaces = failed,
      spaces = states.size,
    )
    _overview.value = overview
    _status.value = overview.toSyncStatus()
  }

  companion object {
    /**
     * Il motore per un'app: il registro su disco in `noBackupFilesDir` ([SqliteSyncStore], fuori dal backup automatico:
     * un registro copiato su un telefono nuovo si crederebbe un altro dispositivo), il nodo dell'installazione
     * ([EngineInstallId]) e l'invio con WorkManager ([FluidSyncWork]).
     */
    fun open(
      context: Context,
      relay: SyncRelay,
      sealer: SyncSealer,
      config: SyncConfig = SyncConfig(),
    ): FluidSync {
      val app = context.applicationContext
      return FluidSync(
        store = SqliteSyncStore(app),
        relay = relay,
        sealer = sealer,
        nodeProvider = { EngineInstallId.get(app) },
        wall = System::currentTimeMillis,
        dispatcher = Dispatchers.IO,
        config = config,
        scheduler = FluidSyncWork.scheduler(app),
      )
    }
  }
}
