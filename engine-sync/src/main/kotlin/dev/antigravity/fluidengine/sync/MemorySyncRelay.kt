package dev.antigravity.fluidengine.sync

/**
 * La destinazione in memoria: un relay completo dentro il processo, fedele al contratto del relay dell'Account Pampa
 * (`b/api.md` § 4.10, `b/architettura.md` § 5.3):
 * - una riga per elemento, con la versione che cresce di uno a ogni scrittura accettata;
 * - una scrittura passa solo se la riga e' ancora alla `baseVersion` dell'invio, altrimenti torna come conflitto;
 * - la stessa chiave d'idempotenza da' la stessa risposta per [idempotencyMillis] (24 ore);
 * - **la cancellazione e' definitiva**: una scrittura su una pietra tombale torna come conflitto;
 * - le pietre tombali restano [tombstoneRetentionMillis] (90 giorni), poi [purge] le toglie; un cursore piu' vecchio
 *   dell'ultima tolta risponde [SyncPullResult.CursorExpired];
 * - una scrittura su un elemento che non c'e' piu' (pietra tombale gia' tolta) torna come conflitto cancellato.
 *
 * Tiene solo byte sigillati: non sa leggere quello che custodisce. Serve alle prove, alle scene della galleria e a un'app
 * che vuole provare la sincronizzazione fra due spazi dello stesso processo.
 */
class MemorySyncRelay(
  private val now: () -> Long = System::currentTimeMillis,
  val tombstoneRetentionMillis: Long = 90L * 24 * 60 * 60 * 1000,
  val idempotencyMillis: Long = 24L * 60 * 60 * 1000,
) : SyncRelay {
  private class Row(
    val version: Long,
    val deleted: Boolean,
    val payload: SyncSealedPayload?,
    val seq: Long,
    val deletedAt: Long,
  )

  private class Space {
    val rows = HashMap<String, Row>()
    var seq = 0L
    var purgedThrough = 0L
    val replies = HashMap<String, Pair<SyncPushResult, Long>>()
    var pushes = 0
    var applied = 0
  }

  private val spaces = HashMap<String, Space>()

  override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult = synchronized(this) {
    require(limit > 0) { "limit positivo" }
    val s = spaces[space.value] ?: return@synchronized SyncPullResult.Rows(emptyList(), since, false, now())
    if (since in 1 until s.purgedThrough) return@synchronized SyncPullResult.CursorExpired(now())
    val after = s.rows.entries.filter { it.value.seq > since }.sortedBy { it.value.seq }
    val page = after.take(limit)
    SyncPullResult.Rows(
      rows = page.map { (id, row) -> row.toRemote(id) },
      next = page.lastOrNull()?.value?.seq ?: maxOf(since, 0),
      hasMore = after.size > page.size,
      serverTimeMillis = now(),
    )
  }

  override suspend fun push(space: SyncSpaceId, batch: SyncPushBatch): SyncPushResult = synchronized(this) {
    val s = spaces.getOrPut(space.value) { Space() }
    s.pushes++
    val t = now()
    s.replies.entries.removeAll { t - it.value.second > idempotencyMillis }
    s.replies[batch.idempotencyKey]?.let { return@synchronized it.first }
    val accepted = ArrayList<SyncAccepted>()
    val conflicts = ArrayList<SyncRemoteRow>()
    for (change in batch.changes) {
      val row = s.rows[change.recordId]
      val current = row?.version ?: 0L
      when {
        // L'elemento non c'e' piu': la sua pietra tombale e' gia' stata tolta.
        row == null && change.baseVersion > 0 ->
          conflicts += SyncRemoteRow(change.recordId, 0, deleted = true, payload = null, seq = s.seq)
        row != null && row.deleted && !change.deleted -> conflicts += row.toRemote(change.recordId)
        row != null && row.deleted && change.deleted && change.baseVersion != current -> conflicts += row.toRemote(change.recordId)
        change.baseVersion != current -> conflicts += row!!.toRemote(change.recordId)
        else -> {
          val version = current + 1
          val seq = ++s.seq
          s.rows[change.recordId] = Row(version, change.deleted, change.payload, seq, if (change.deleted) t else 0L)
          accepted += SyncAccepted(change.recordId, version, seq)
          s.applied++
        }
      }
    }
    val result = SyncPushResult(accepted, conflicts, t)
    s.replies[batch.idempotencyKey] = result to t
    result
  }

  /**
   * Il lavoro di ogni notte: toglie le pietre tombali piu' vecchie di [tombstoneRetentionMillis]. Risponde quante.
   * Dopo, un dispositivo con un cursore piu' vecchio dell'ultima tolta rilegge tutto.
   */
  fun purge(): Int = synchronized(this) {
    val t = now()
    var removed = 0
    for (s in spaces.values) {
      val old = s.rows.filter { it.value.deleted && t - it.value.deletedAt >= tombstoneRetentionMillis }
      for ((id, row) in old) {
        s.rows.remove(id)
        s.purgedThrough = maxOf(s.purgedThrough, row.seq)
        removed++
      }
    }
    removed
  }

  /** Le righe di [space] come le tiene il relay, in ordine: per le prove e per la scena che mostra cosa vede il relay. */
  fun rows(space: SyncSpaceId): List<SyncRemoteRow> = synchronized(this) {
    spaces[space.value]?.rows?.entries?.sortedBy { it.value.seq }?.map { (id, row) -> row.toRemote(id) }.orEmpty()
  }

  /** Quanti invii ha ricevuto [space] (ripetizioni comprese) e quante scritture ha applicato davvero. */
  fun counts(space: SyncSpaceId): Pair<Int, Int> = synchronized(this) {
    spaces[space.value]?.let { it.pushes to it.applied } ?: (0 to 0)
  }

  private fun Row.toRemote(id: String) = SyncRemoteRow(id, version, deleted, payload, seq)
}
