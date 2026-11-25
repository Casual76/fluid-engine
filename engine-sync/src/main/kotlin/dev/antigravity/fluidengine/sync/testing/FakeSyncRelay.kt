package dev.antigravity.fluidengine.sync.testing

import dev.antigravity.fluidengine.sync.MemorySyncRelay
import dev.antigravity.fluidengine.sync.SyncFailure
import dev.antigravity.fluidengine.sync.SyncPullResult
import dev.antigravity.fluidengine.sync.SyncPushBatch
import dev.antigravity.fluidengine.sync.SyncPushResult
import dev.antigravity.fluidengine.sync.SyncRelay
import dev.antigravity.fluidengine.sync.SyncRelayException
import dev.antigravity.fluidengine.sync.SyncSpaceId
import kotlinx.coroutines.delay

/**
 * La destinazione finta per le prove: sta davanti a un relay vero ([MemorySyncRelay] di serie) e sa comportarsi come la
 * rete di tutti i giorni.
 * - [online] falso: ogni chiamata fallisce prima di partire ([SyncFailure.Offline]).
 * - [dropRequests]: le prossime N chiamate si perdono all'andata (il relay non le vede).
 * - [dropResponses]: le prossime N chiamate (letture o invii) arrivano al relay e si perdono al ritorno: **la rete che cade
 *   a meta'**. [dropPushResponses] fa lo stesso solo con gli invii: il relay applica, il dispositivo non sa se e' passato.
 * - [duplicatePushes]: i prossimi N invii arrivano due volte (un proxy che rimanda): **il doppio invio**.
 * - [latencyMillis]: un'attesa prima di ogni chiamata.
 * - [beforeCall]: un gancio che gira prima di ogni chiamata (per far succedere qualcosa «mentre la rete lavora»).
 *
 * Registra tutto quello che passa ([pushes], [payloadBytes]): le prove ci cercano il testo in chiaro e non devono trovarlo.
 * Ogni dispositivo finto ha la sua [FakeSyncRelay] davanti allo stesso relay, cosi' la rete di uno cade senza toccare
 * gli altri.
 */
class FakeSyncRelay(val inner: SyncRelay = MemorySyncRelay()) : SyncRelay {
  @Volatile var online: Boolean = true
  @Volatile var dropRequests: Int = 0
  @Volatile var dropResponses: Int = 0
  @Volatile var dropPushResponses: Int = 0
  @Volatile var duplicatePushes: Int = 0
  @Volatile var latencyMillis: Long = 0
  @Volatile var failure: SyncFailure = SyncFailure.Offline
  @Volatile var beforeCall: (suspend () -> Unit)? = null

  private val _pushes = ArrayList<SyncPushBatch>()
  private val _payloads = ArrayList<ByteArray>()

  /** Ogni invio partito (anche quelli persi al ritorno), nell'ordine. */
  val pushes: List<SyncPushBatch> get() = synchronized(this) { _pushes.toList() }

  /** Tutti i byte di contenuto passati da qui, all'andata e al ritorno. */
  val payloadBytes: List<ByteArray> get() = synchronized(this) { _payloads.map { it.copyOf() } }

  /** Quante chiamate sono passate di qui, riuscite o no. */
  @Volatile var calls: Int = 0
    private set

  override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult {
    before()
    val result = inner.pull(space, since, limit)
    if (result is SyncPullResult.Rows) record(result.rows.mapNotNull { it.payload?.bytes })
    after()
    return result
  }

  override suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult {
    before()
    synchronized(this) {
      _pushes += batch
      batch.changes.mapNotNullTo(_payloads) { it.payload?.bytes }
    }
    var result = inner.push(space, batch)
    if (takeDuplicate()) result = inner.push(space, batch)
    record(result.conflicts.mapNotNull { it.payload?.bytes })
    val dropPush = synchronized(this) { if (dropPushResponses > 0) { dropPushResponses--; true } else false }
    if (dropPush) throw SyncRelayException(failure)
    after()
    return result
  }

  private suspend fun before() {
    synchronized(this) { calls++ }
    beforeCall?.invoke()
    if (latencyMillis > 0) delay(latencyMillis)
    if (!online) throw SyncRelayException(failure)
    val drop = synchronized(this) { if (dropRequests > 0) { dropRequests--; true } else false }
    if (drop) throw SyncRelayException(failure)
  }

  private fun after() {
    val drop = synchronized(this) { if (dropResponses > 0) { dropResponses--; true } else false }
    if (drop) throw SyncRelayException(failure)
  }

  private fun takeDuplicate(): Boolean = synchronized(this) {
    if (duplicatePushes > 0) { duplicatePushes--; true } else false
  }

  private fun record(bytes: List<ByteArray>) = synchronized(this) { _payloads += bytes }
}
