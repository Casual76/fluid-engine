package dev.antigravity.fluidengine.sync

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Le modifiche di una scrittura ([SyncSpace.edit]): i campi da cambiare o la cancellazione. Tutto quello che si scrive in un
 * blocco prende lo stesso stamp e finisce nel registro in una transazione sola.
 */
class SyncEditor internal constructor() {
  internal val writes = LinkedHashMap<String, SyncValue?>()
  internal var deleting = false

  /** [field] prende [value]; null lo cancella. */
  fun set(field: String, value: SyncValue?) {
    writes[field] = value
  }

  /** Un testo corto: vince l'ultimo. */
  fun text(field: String, text: String?) = set(field, text?.let(SyncValue::Text))

  /** Un testo lungo: si fonde con le modifiche degli altri dispositivi. */
  fun longText(field: String, text: String?) = set(field, text?.let(SyncValue::LongText))

  fun integer(field: String, value: Long?) = set(field, value?.let(SyncValue::Integer))

  fun real(field: String, value: Double?) = set(field, value?.let(SyncValue::Real))

  fun bool(field: String, value: Boolean?) = set(field, value?.let(SyncValue::Bool))

  /** Cancella l'elemento. E' definitivo: vince su ogni modifica arrivata insieme. */
  fun delete() {
    deleting = true
  }
}

/** Una versione di un campo in conflitto, come la mostra il foglio della scelta. */
class SyncVersion(
  /** L'identificativo della revisione: lo stamp in forma di testo. */
  val revision: String,
  val stamp: SyncHlc,
  val value: SyncValue?,
  /** Vero per la versione che l'app mostra adesso (quella con lo stamp piu' grande). */
  val current: Boolean,
) {
  /** Il dispositivo che l'ha scritta (il nodo del suo orologio): l'app sa dargli un nome («Tablet di Alessio»). */
  val node: String get() = stamp.node

  /** Quando e' stata scritta, in millisecondi dell'orologio corretto. */
  val millis: Long get() = stamp.millis

  /** Il testo, se il valore e' un testo. */
  val text: String?
    get() = when (val v = value) {
      is SyncValue.Text -> v.text
      is SyncValue.LongText -> v.text
      else -> null
    }

  override fun toString(): String = "SyncVersion($revision${if (current) ", attuale" else ""})"
}

/**
 * Un campo che aspetta una scelta: le [versions] in ordine (prima quella attuale, poi le altre dalla piu' nuova). Le
 * [revisions] vanno ripassate a [SyncSpace.resolve] come «quelle che ho visto»: se nel frattempo e' arrivata una revisione
 * nuova, la scelta non vale e torna il confronto aggiornato.
 */
class SyncConflict(
  val space: SyncSpaceId,
  val recordId: String,
  val field: String,
  val versions: List<SyncVersion>,
) {
  val revisions: List<String> get() = versions.map { it.revision }

  override fun equals(other: Any?): Boolean =
    other is SyncConflict && other.space == space && other.recordId == recordId && other.field == field &&
      other.revisions == revisions

  override fun hashCode(): Int = (recordId.hashCode() * 31 + field.hashCode()) * 31 + revisions.hashCode()

  override fun toString(): String = "SyncConflict($space/$recordId.$field: $revisions)"
}

/** Come e' andata una scelta ([SyncSpace.resolve]). */
sealed class SyncResolution {
  /** Fatto. [copies] sono gli elementi nuovi nati da «tieni tutte e due» (vuota se se n'e' tenuta una sola). */
  data class Resolved(val copies: List<String>) : SyncResolution()

  /** Nel frattempo e' cambiato qualcosa: [current] e' il confronto di adesso (null se il conflitto non c'e' piu'). */
  data class Stale(val current: SyncConflict?) : SyncResolution()

  /** L'elemento o il campo non esistono (cancellati). */
  data object NotFound : SyncResolution()
}

/**
 * Lo stato di uno spazio, come lo legge l'interfaccia: gli elementi vivi, i conflitti, le modifiche in attesa e come e'
 * andata l'ultima sincronizzazione.
 */
data class SyncSpaceState(
  /** Gli elementi non cancellati, per identificativo. */
  val records: Map<String, SyncDocument>,
  val conflicts: List<SyncConflict>,
  /** Le voci del registro non ancora confermate dal relay: «3 modifiche in attesa». */
  val pending: Int,
  val syncing: Boolean,
  /** L'ora corretta dell'ultima sincronizzazione riuscita, o null. */
  val lastSuccessMillis: Long?,
  /** Perche' l'ultima sincronizzazione non e' riuscita; null dopo una riuscita. */
  val failure: SyncFailure?,
  /** Vero finche' il registro non e' stato letto dal disco. */
  val loading: Boolean = false,
) {
  companion object {
    internal val Loading = SyncSpaceState(emptyMap(), emptyList(), 0, false, null, null, loading = true)
  }
}

/** Cosa ha fatto una sincronizzazione di uno spazio. */
class SyncSpaceReport(
  val space: SyncSpaceId,
  /** Elementi accettati dal relay. */
  val pushed: Int,
  /** Righe arrivate e applicate. */
  val pulled: Int,
  /** Elementi fusi (modifiche di qui e di la' sullo stesso elemento). */
  val merged: Int,
  /** Conflitti nuovi da scegliere. */
  val conflicts: Int,
  /** Voci del registro superate da una cancellazione arrivata da un altro dispositivo. */
  val supersededByDelete: Int,
  /** Pietre tombali tolte dal registro. */
  val purged: Int,
  /** Vero se il dispositivo era fermo da troppo e ha riletto tutto. */
  val fullResync: Boolean,
  val failure: SyncFailure?,
) {
  override fun toString(): String =
    "SyncSpaceReport($space: mandati $pushed, arrivati $pulled, fusi $merged, conflitti $conflicts, " +
      "superati $supersededByDelete, tolti $purged${if (fullResync) ", riletto tutto" else ""}${failure?.let { ", $it" } ?: ""})"
}

/**
 * Uno spazio: i dati di un'app di una persona, oppure uno spazio condiviso. Si apre con [FluidSync.space].
 *
 * **Prima il registro, poi la rete.** [edit], [create] e [delete] scrivono subito nel registro locale, in una transazione
 * con la voce che le ricorda, e l'interfaccia le vede subito ([state]); l'app funziona tutta senza rete. Poi chiedono un
 * invio ([SyncScheduler]): con l'app su WorkManager ([FluidSyncWork]) parte quando c'e' rete.
 *
 * **La sincronizzazione** ([sync]) fa, in ordine:
 * 1. rimanda l'invio rimasto in volo, identico e con la stessa chiave: se il relay l'aveva gia' applicato risponde come la
 *    prima volta, e niente si scrive due volte;
 * 2. legge le novita' dal cursore e le fonde con le modifiche in attesa ([SyncMerge]); se il cursore e' scaduto (fermo per
 *    mesi) rilegge tutto, e un elemento che il relay non ha piu' era stato cancellato;
 * 3. manda le modifiche, sigillate da chi chiama ([SyncSealer]), salvando l'invio prima di partire; i conflitti tornano
 *    indietro, si fondono e ripartono;
 * 4. compatta il registro: le voci confermate se ne vanno, le pietre tombali dopo il loro tempo.
 *
 * Tutto il lavoro (registro, unione, sigillo) gira sul dispatcher dell'engine, mai sul thread principale.
 */
class SyncSpace internal constructor(
  val id: SyncSpaceId,
  private val engine: FluidSync,
) {
  private val key = id.value
  private val store get() = engine.store
  private val clock get() = engine.clock
  private val config get() = engine.config

  /** Le scritture e le applicazioni nel registro, una alla volta. */
  private val mutex = Mutex()

  /** Una sincronizzazione alla volta per spazio. */
  private val syncMutex = Mutex()

  private val mirror = java.util.TreeMap<String, SyncDocument>()
  private var loaded = false
  private var pending = 0
  private var syncing = false
  private var lastSuccess: Long? = null
  private var failure: SyncFailure? = null

  private val _state = MutableStateFlow(SyncSpaceState.Loading)

  /** Lo stato dello spazio: cambia dopo ogni scrittura, ogni sincronizzazione e ogni scelta. */
  val state: StateFlow<SyncSpaceState> = _state.asStateFlow()

  /** Legge il registro dal disco, se non l'ha gia' fatto. Le altre chiamate lo fanno da sole. */
  suspend fun load() = withContext(engine.dispatcher) { mutex.withLock { ensureLoadedLocked() } }

  /** Il documento di [recordId] com'e' adesso (anche una pietra tombale), o null. */
  suspend fun document(recordId: String): SyncDocument? = withContext(engine.dispatcher) {
    mutex.withLock {
      ensureLoadedLocked()
      store.transaction { record(key, recordId)?.local }
    }
  }

  /**
   * Scrive in [recordId] le modifiche di [block]. Risponde falso se non c'era niente da scrivere o se l'elemento e' stato
   * cancellato (la cancellazione e' definitiva). Un elemento che non c'e' nasce.
   */
  suspend fun edit(recordId: String, block: SyncEditor.() -> Unit): Boolean {
    require(recordId.isNotBlank() && recordId.length <= 200) { "elemento non valido: «$recordId»" }
    val editor = SyncEditor().apply(block)
    if (editor.writes.isEmpty() && !editor.deleting) return false
    val changed = withContext(engine.dispatcher) {
      mutex.withLock {
        ensureLoadedLocked()
        val result = store.transaction {
          val rec = record(key, recordId)
          val local = rec?.local ?: SyncDocument.Empty
          if (local.isDeleted) return@transaction null
          var doc = local
          var seq = nextSeq()
          val stamp = clock.now()
          if (editor.deleting) {
            doc = SyncDocument.tombstone(stamp)
            appendJournal(key, SyncJournalEntry(seq++, recordId, stamp, SyncChange.Delete))
          } else {
            for ((field, value) in editor.writes) {
              val prev = doc.fields[field]
              if (prev != null && prev.value == value) continue
              doc = doc.with(field, SyncField(value, stamp, prev?.alternatives.orEmpty()))
              appendJournal(key, SyncJournalEntry(seq++, recordId, stamp, SyncChange.Write(field, value)))
            }
          }
          if (doc == local && rec != null) return@transaction null
          putMeta(key, MetaSeq, SyncStoreCodec.encodeLong(seq - 1))
          putMeta("", MetaClock, SyncStoreCodec.encodeHlc(clock.current))
          val next = (rec ?: SyncStoredRecord(recordId, local, null, 0, false)).copy(local = doc, dirty = true)
          putRecord(key, next)
          next to journalSize(key)
        } ?: return@withLock false
        updateMirrorLocked(listOf(result.first), result.second)
        true
      }
    }
    if (changed) engine.requestSync()
    return changed
  }

  /** Crea un elemento nuovo con un identificativo casuale e ci scrive [block]. Risponde l'identificativo. */
  suspend fun create(block: SyncEditor.() -> Unit): String {
    val id = newRecordId()
    edit(id) {
      block()
    }
    return id
  }

  /** Cancella [recordId]. Definitivo: vince su ogni modifica arrivata insieme da un altro dispositivo. */
  suspend fun delete(recordId: String): Boolean = edit(recordId) { delete() }

  /**
   * Sceglie in un conflitto. [keep] sono le revisioni da tenere: una sola («tieni questa»), oppure piu' d'una («tieni tutte
   * e due»: la prima resta nell'elemento, ogni altra diventa un elemento nuovo, copia di questo con quella versione, come
   * «Mantieni entrambi» di File). [seen] sono le revisioni che la persona aveva davanti: se non sono piu' quelle di adesso
   * (e' arrivata una revisione nuova a foglio aperto) la scelta non si applica e torna [SyncResolution.Stale] col confronto
   * aggiornato.
   */
  suspend fun resolve(recordId: String, field: String, keep: List<String>, seen: Collection<String>): SyncResolution {
    require(keep.isNotEmpty()) { "scegli almeno una revisione" }
    val outcome = withContext(engine.dispatcher) {
      mutex.withLock {
        ensureLoadedLocked()
        val result = store.transaction {
          val rec = record(key, recordId) ?: return@transaction SyncResolution.NotFound to null
          if (rec.local.isDeleted) return@transaction SyncResolution.NotFound to null
          val current = rec.local.fields[field] ?: return@transaction SyncResolution.NotFound to null
          val now = conflictOf(id, recordId, field, current)
          if (now == null || now.revisions.toSet() != seen.toSet()) {
            return@transaction SyncResolution.Stale(now) to null
          }
          require(keep.all { it in now.revisions }) { "revisione sconosciuta in $keep" }
          val byRevision = now.versions.associateBy { it.revision }
          val stamp = clock.now()
          var seq = nextSeq()
          val chosen = byRevision.getValue(keep.first()).value
          val doc = rec.local.with(field, SyncField(chosen, stamp, emptyList()))
          appendJournal(key, SyncJournalEntry(seq++, recordId, stamp, SyncChange.Choose(field, keep)))
          val touched = ArrayList<SyncStoredRecord>()
          val updated = rec.copy(local = doc, dirty = true)
          putRecord(key, updated)
          touched += updated
          val copies = ArrayList<String>()
          for (revision in keep.drop(1)) {
            val copyId = newRecordId()
            val fields = doc.fields.mapValues { (_, f) -> SyncField(f.value, stamp, emptyList()) } +
              (field to SyncField(byRevision.getValue(revision).value, stamp, emptyList()))
            val copy = SyncStoredRecord(copyId, SyncDocument(fields), null, 0, true)
            for ((name, f) in copy.local.fields) {
              appendJournal(key, SyncJournalEntry(seq++, copyId, stamp, SyncChange.Write(name, f.value)))
            }
            putRecord(key, copy)
            touched += copy
            copies += copyId
          }
          putMeta(key, MetaSeq, SyncStoreCodec.encodeLong(seq - 1))
          putMeta("", MetaClock, SyncStoreCodec.encodeHlc(clock.current))
          SyncResolution.Resolved(copies) to (touched to journalSize(key))
        }
        result.second?.let { (touched, size) -> updateMirrorLocked(touched, size) }
        result.first
      }
    }
    if (outcome is SyncResolution.Resolved) engine.requestSync()
    return outcome
  }

  /** Il conflitto di [recordId].[field] com'e' adesso, o null. */
  fun conflict(recordId: String, field: String): SyncConflict? =
    _state.value.conflicts.firstOrNull { it.recordId == recordId && it.field == field }

  /** Sincronizza adesso: vedi la descrizione della classe. Non lancia per gli errori di rete: li mette nel resoconto e nello stato. */
  suspend fun sync(): SyncSpaceReport = withContext(engine.dispatcher) {
    syncMutex.withLock {
      mutex.withLock { ensureLoadedLocked() }
      setSyncing(true)
      val report = ReportBuilder()
      try {
        resendInflight(report)
        pull(report)
        push(report)
        compact(report)
        mutex.withLock {
          lastSuccess = clock.wallMillis()
          failure = null
          store.transaction { putMeta(key, MetaLastSuccess, SyncStoreCodec.encodeLong(lastSuccess!!)) }
          publishLocked()
        }
      } catch (e: Exception) {
        // Un errore della rete o del relay, un sigillo che non si fa (la chiave dell'account non c'e' ancora) o un dato
        // rovinato nel registro: diventa lo stato, e la sincronizzazione riprova la prossima volta. Il resto (un difetto,
        // una cancellazione della coroutine) sale.
        val failed = when (e) {
          is SyncRelayException -> e.failure
          is SyncSealException -> SyncFailure.Rejected("seal", e.message)
          is SyncFormatException -> SyncFailure.Rejected("corrupt", e.message)
          else -> throw e
        }
        report.failure = failed
        mutex.withLock {
          failure = failed
          publishLocked()
        }
      } finally {
        setSyncing(false)
      }
      report.build(id)
    }
  }

  // --- La sincronizzazione, passo per passo ------------------------------------------------------------------------

  private suspend fun resendInflight(report: ReportBuilder) {
    val inflight = mutex.withLock {
      store.transaction { meta(key, MetaInflight) }?.let(SyncStoreCodec::decodeInflight)
    } ?: return
    val result = engine.callPush(id, inflight.batch())
    applyPush(inflight, result, report)
  }

  private suspend fun pull(report: ReportBuilder) {
    var cursor = mutex.withLock { SyncStoreCodec.decodeLong(store.transaction { meta(key, MetaCursor) }) ?: 0L }
    // Una lettura da zero e' sempre completa: alla fine, un elemento confermato che il relay non ha piu' e' stato
    // cancellato. Vale anche per un dispositivo che ha solo mandato e mai letto (cursore a zero, versioni confermate).
    var full = cursor == 0L
    val seen = HashSet<String>()
    while (true) {
      val page = engine.callPull(id, cursor, config.pageSize)
      when (page) {
        is SyncPullResult.CursorExpired -> {
          if (cursor == 0L) throw SyncRelayException(SyncFailure.Rejected("cursor_expired", "il relay rifiuta anche la lettura completa"))
          full = true
          report.fullResync = true
          cursor = 0L
          seen.clear()
          continue
        }
        is SyncPullResult.Rows -> {
          val opened = ArrayList<Pair<SyncRemoteRow, SyncDocument>>(page.rows.size)
          var stopped: SyncFailure? = null
          for (row in page.rows) {
            val doc = runCatching { openRow(row) }.getOrElse { error ->
              stopped = unreadable(row, error)
              null
            } ?: break
            opened += row to doc
          }
          val next = if (stopped == null) page.next else opened.lastOrNull()?.first?.seq ?: cursor
          mutex.withLock {
            val touched = store.transaction {
              val out = ArrayList<SyncStoredRecord>()
              for ((row, doc) in opened) applyRemoteRow(row, doc, report)?.let(out::add)
              if (!full) putMeta(key, MetaCursor, SyncStoreCodec.encodeLong(next))
              putMeta("", MetaClock, SyncStoreCodec.encodeHlc(clock.current))
              out to journalSize(key)
            }
            updateMirrorLocked(touched.first, touched.second)
          }
          opened.forEach { seen += it.first.recordId }
          stopped?.let { throw SyncRelayException(it) }
          cursor = next
          if (!page.hasMore) break
        }
      }
    }
    if (full) {
      // Rilette tutte le righe: un elemento che il relay aveva e che ora non ha piu' e' stato cancellato, e la sua pietra
      // tombale e' gia' scaduta. Vale la cancellazione, come se l'avessimo vista in tempo.
      mutex.withLock {
        val touched = store.transaction {
          val out = ArrayList<SyncStoredRecord>()
          for (rec in records(key)) {
            if (rec.baseVersion > 0 && rec.recordId !in seen && !rec.local.isDeleted) {
              val dropped = removeJournal(key, rec.recordId, Long.MAX_VALUE)
              if (rec.dirty) report.supersededByDelete += dropped
              val tomb = SyncDocument.tombstone(SyncHlc.Zero)
              val next = rec.copy(local = tomb, base = tomb, baseVersion = 0, dirty = false)
              putRecord(key, next)
              out += next
            }
          }
          putMeta(key, MetaCursor, SyncStoreCodec.encodeLong(cursor))
          out to journalSize(key)
        }
        updateMirrorLocked(touched.first, touched.second)
      }
    }
  }

  private suspend fun push(report: ReportBuilder) {
    repeat(config.maxPushRounds) {
      // 1. Cosa mandare: gli elementi con modifiche, i piu' vecchi prima. Un elemento nato e cancellato senza mai partire
      //    non ha niente da dire al relay: se ne va col suo registro.
      val ready = mutex.withLock {
        val picked = store.transaction {
          val dropped = ArrayList<SyncStoredRecord>()
          val out = ArrayList<Pair<SyncStoredRecord, Long>>()
          val dirty = records(key).filter { it.dirty }
          for (rec in dirty) {
            if (rec.local.isDeleted && rec.baseVersion == 0L) {
              removeJournal(key, rec.recordId, Long.MAX_VALUE)
              removeRecord(key, rec.recordId)
              dropped += rec
              continue
            }
            out += rec to lastJournalSeq(key, rec.recordId)
          }
          Triple(out.sortedBy { it.second }.take(config.pushBatchSize), dropped, journalSize(key))
        }
        if (picked.second.isNotEmpty()) {
          picked.second.forEach { mirror.remove(it.recordId) }
          pending = picked.third
          publishLocked()
        }
        picked.first
      }
      if (ready.isEmpty()) return
      // 2. Il sigillo, fuori dal registro: lo mette chi chiama.
      val changes = ready.map { (rec, through) ->
        val version = rec.baseVersion + 1
        val payload = engine.sealer.seal(
          SyncSealContext(id, rec.recordId, version),
          SyncDocumentCodec.encode(rec.local),
        )
        SyncInflightChange(rec.recordId, rec.baseVersion, rec.local.isDeleted, payload, rec.local, through)
      }
      val inflight = SyncInflight(UUID.randomUUID().toString(), changes)
      // 3. Salvato prima di partire: se il processo muore o la rete cade, si rimanda identico.
      mutex.withLock { store.transaction { putMeta(key, MetaInflight, SyncStoreCodec.encodeInflight(inflight)) } }
      val result = engine.callPush(id, inflight.batch())
      applyPush(inflight, result, report)
    }
  }

  private suspend fun applyPush(inflight: SyncInflight, result: SyncPushResult, report: ReportBuilder) {
    // I conflitti tornano sigillati: si aprono fuori dal registro. Uno che non si apre lascia l'elemento com'era.
    val opened = ArrayList<Pair<SyncRemoteRow, SyncDocument>>()
    var unreadable: SyncFailure? = null
    for (row in result.conflicts) {
      runCatching { openRow(row) }
        .onSuccess { opened += row to it }
        .onFailure { unreadable = unreadable(row, it) }
    }
    mutex.withLock {
      val touched = store.transaction {
        val out = ArrayList<SyncStoredRecord>()
        for (accepted in result.accepted) {
          val change = inflight.change(accepted.recordId) ?: continue
          val rec = record(key, accepted.recordId) ?: continue
          val base = change.document
          val next = rec.copy(
            base = base,
            baseVersion = accepted.version,
            dirty = rec.local != base && !(base.isDeleted && rec.local.isDeleted),
          )
          removeJournal(key, rec.recordId, change.throughSeq)
          if (!next.dirty) removeJournal(key, rec.recordId, Long.MAX_VALUE)
          putRecord(key, next)
          out += next
          report.pushed++
        }
        for ((row, doc) in opened) {
          val mine = inflight.change(row.recordId)
          val rec = record(key, row.recordId)
          if (mine != null && rec != null && !row.deleted && doc == mine.document && row.version > rec.baseVersion) {
            // Il «conflitto» e' la nostra stessa scrittura, applicata quando la risposta si era persa e il relay ha gia'
            // dimenticato la chiave: e' una conferma, e la base e' quello che avevamo mandato.
            val next = rec.copy(base = doc, baseVersion = row.version, dirty = rec.local != doc)
            removeJournal(key, rec.recordId, mine.throughSeq)
            if (!next.dirty) removeJournal(key, rec.recordId, Long.MAX_VALUE)
            putRecord(key, next)
            out += next
            report.pushed++
            continue
          }
          applyRemoteRow(row, doc, report, conflict = true)?.let(out::add)
        }
        putMeta(key, MetaInflight, null)
        putMeta("", MetaClock, SyncStoreCodec.encodeHlc(clock.current))
        out to journalSize(key)
      }
      updateMirrorLocked(touched.first, touched.second)
    }
    unreadable?.let { throw SyncRelayException(it) }
  }

  /** Applica una riga del relay nel registro. Risponde l'elemento cambiato, o null se non c'era niente da fare. */
  private fun SyncStoreTransaction.applyRemoteRow(
    row: SyncRemoteRow,
    remote: SyncDocument,
    report: ReportBuilder,
    conflict: Boolean = false,
  ): SyncStoredRecord? {
    val rec = record(key, row.recordId)
    // Una riga che conosciamo gia' (la nostra scrittura che torna, un invio ripetuto, un relay che ripete): niente.
    if (rec != null && row.version <= rec.baseVersion && !row.deleted) return null
    if (rec != null && rec.local.isDeleted && !rec.dirty && row.deleted) return null
    clock.observe(remote.latestStamp)
    if (!conflict) report.pulled++
    val next = when {
      rec == null -> SyncStoredRecord(row.recordId, remote, remote, row.version, dirty = false)
      remote.isDeleted -> {
        val dropped = removeJournal(key, row.recordId, Long.MAX_VALUE)
        if (rec.dirty && !rec.local.isDeleted) report.supersededByDelete += dropped
        rec.copy(local = remote, base = remote, baseVersion = row.version, dirty = false)
      }
      !rec.dirty -> rec.copy(local = remote, base = remote, baseVersion = row.version, dirty = false)
      else -> {
        val before = rec.local.conflictedFields.toSet()
        val merged = SyncMerge.merge(rec.base, rec.local, remote, clock::now, config.maxMergeEdits)
        report.merged++
        if (merged.conflictedFields.any { it !in before }) report.conflicts++
        val clean = merged == remote
        if (clean) removeJournal(key, row.recordId, Long.MAX_VALUE)
        rec.copy(local = merged, base = remote, baseVersion = row.version, dirty = !clean)
      }
    }
    putRecord(key, next)
    return next
  }

  private suspend fun openRow(row: SyncRemoteRow): SyncDocument {
    val payload = row.payload
    if (payload == null) {
      if (row.deleted) return SyncDocument.tombstone(SyncHlc.Zero)
      throw SyncFormatException("riga ${row.recordId} senza contenuto")
    }
    val plain = engine.sealer.open(SyncSealContext(id, row.recordId, row.version), payload)
    val doc = SyncDocumentCodec.decode(plain)
    if (row.deleted && !doc.isDeleted) return SyncDocument.tombstone(doc.latestStamp)
    return doc
  }

  private fun unreadable(row: SyncRemoteRow, error: Throwable): SyncFailure {
    if (error is SyncRelayException) throw error
    if (error !is SyncSealException && error !is SyncFormatException) throw error
    return SyncFailure.Rejected("unreadable", "${row.recordId}: ${error.message}")
  }

  private suspend fun compact(report: ReportBuilder) {
    val horizon = clock.wallMillis() - config.tombstoneRetentionMillis
    mutex.withLock {
      val removed = store.transaction {
        val gone = ArrayList<String>()
        for (rec in records(key)) {
          val deleted = rec.local.deleted ?: continue
          if (rec.dirty) continue
          // Una pietra tombale confermata resta il tempo del relay, poi se ne va; una mai arrivata al relay subito.
          if (rec.baseVersion == 0L || deleted.millis < horizon) {
            removeJournal(key, rec.recordId, Long.MAX_VALUE)
            removeRecord(key, rec.recordId)
            gone += rec.recordId
          }
        }
        gone to journalSize(key)
      }
      report.purged += removed.first.size
      removed.first.forEach { mirror.remove(it) }
      pending = removed.second
      publishLocked()
    }
  }

  // --- Lo stato ------------------------------------------------------------------------------------------------------

  private fun ensureLoadedLocked() {
    if (loaded) return
    engine.restoreClock()
    val (records, size, last) = store.transaction {
      Triple(records(key), journalSize(key), SyncStoreCodec.decodeLong(meta(key, MetaLastSuccess)))
    }
    mirror.clear()
    for (rec in records) mirror[rec.recordId] = rec.local
    pending = size
    lastSuccess = last
    loaded = true
    publishLocked()
  }

  private fun updateMirrorLocked(touched: List<SyncStoredRecord>, journalSize: Int) {
    for (rec in touched) mirror[rec.recordId] = rec.local
    pending = journalSize
    publishLocked()
  }

  private suspend fun setSyncing(value: Boolean) {
    mutex.withLock {
      syncing = value
      publishLocked()
    }
  }

  private fun publishLocked() {
    val alive = java.util.TreeMap<String, SyncDocument>()
    val conflicts = ArrayList<SyncConflict>()
    for ((recordId, doc) in mirror) {
      if (doc.isDeleted) continue
      alive[recordId] = doc
      for ((name, field) in doc.fields) {
        if (field.inConflict) conflictOf(id, recordId, name, field)?.let(conflicts::add)
      }
    }
    _state.value = SyncSpaceState(
      records = java.util.Collections.unmodifiableMap(alive),
      conflicts = conflicts,
      pending = pending,
      syncing = syncing,
      lastSuccessMillis = lastSuccess,
      failure = failure,
    )
    engine.onSpaceChanged()
  }

  private fun SyncStoreTransaction.nextSeq(): Long = (SyncStoreCodec.decodeLong(meta(key, MetaSeq)) ?: 0L) + 1

  private class ReportBuilder {
    var pushed = 0
    var pulled = 0
    var merged = 0
    var conflicts = 0
    var supersededByDelete = 0
    var purged = 0
    var fullResync = false
    var failure: SyncFailure? = null

    fun build(space: SyncSpaceId) =
      SyncSpaceReport(space, pushed, pulled, merged, conflicts, supersededByDelete, purged, fullResync, failure)
  }

  internal companion object {
    const val MetaSeq = "seq"
    const val MetaCursor = "cursor"
    const val MetaInflight = "inflight"
    const val MetaLastSuccess = "last-success"
    const val MetaClock = "clock"

    /** Un identificativo casuale: 22 caratteri, nessun titolo o nome dentro (sul relay sta in chiaro). */
    fun newRecordId(): String {
      val uuid = UUID.randomUUID()
      val bytes = java.nio.ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()
      return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun conflictOf(space: SyncSpaceId, recordId: String, field: String, current: SyncField): SyncConflict? {
      if (!current.inConflict) return null
      val versions = ArrayList<SyncVersion>(current.alternatives.size + 1)
      versions += SyncVersion(current.revision, current.stamp, current.value, current = true)
      for (alt in current.alternatives.sortedByDescending { it.stamp }) {
        versions += SyncVersion(alt.revision, alt.stamp, alt.value, current = false)
      }
      return SyncConflict(space, recordId, field, versions)
    }
  }
}
