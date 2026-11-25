package dev.antigravity.fluidengine.sync

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Un elemento nel registro locale:
 * - [local], lo stato che l'app vede, con le modifiche non ancora confermate;
 * - [base], l'ultima versione confermata dal relay (la base comune dell'unione), nulla per un elemento mai mandato;
 * - [baseVersion], la versione di [base] sul relay (0 se non c'e');
 * - [dirty], vero se [local] ha qualcosa che il relay non ha ancora.
 */
class SyncStoredRecord(
  val recordId: String,
  val local: SyncDocument,
  val base: SyncDocument?,
  val baseVersion: Long,
  val dirty: Boolean,
) {
  internal fun copy(
    local: SyncDocument = this.local,
    base: SyncDocument? = this.base,
    baseVersion: Long = this.baseVersion,
    dirty: Boolean = this.dirty,
  ) = SyncStoredRecord(recordId, local, base, baseVersion, dirty)

  override fun equals(other: Any?): Boolean =
    other is SyncStoredRecord && other.recordId == recordId && other.local == local && other.base == base &&
      other.baseVersion == baseVersion && other.dirty == dirty

  override fun hashCode(): Int = recordId.hashCode() * 31 + local.hashCode()

  override fun toString(): String = "SyncStoredRecord($recordId v$baseVersion${if (dirty) " da mandare" else ""})"
}

/** Una modifica nel registro: cosa e' cambiato in un elemento, con lo stamp della scrittura. */
sealed class SyncChange {
  /** Il campo [field] ha preso [value] (null: cancellato). */
  data class Write(val field: String, val value: SyncValue?) : SyncChange()

  /** L'elemento e' stato cancellato. */
  data object Delete : SyncChange()

  /** Nel campo [field] in conflitto sono state tenute le revisioni [kept] (la prima resta, le altre diventano copie). */
  data class Choose(val field: String, val kept: List<String>) : SyncChange()
}

/**
 * Una voce del registro: il numero d'ordine nello spazio ([seq]), l'elemento, lo stamp e la modifica. Le voci restano
 * finche' il relay non ha confermato l'elemento che le contiene: sono le «modifiche in attesa». Poi il registro si compatta
 * e le toglie.
 */
data class SyncJournalEntry(val seq: Long, val recordId: String, val stamp: SyncHlc, val change: SyncChange)

/**
 * Dove sta il registro. Ogni chiamata dell'engine passa da una [transaction]: o si scrive tutto o niente, cosi' una modifica
 * e la sua voce nel registro, o una pagina di righe e il cursore, non si separano mai, nemmeno se il processo muore a meta'.
 *
 * L'engine chiama il deposito **solo fuori dal thread principale**. Le implementazioni di serie sono [InMemorySyncStore]
 * (per le prove e le scene) e [SqliteSyncStore] (su disco, quella per le app). Uno spazio vuoto (`""`) e' riservato ai dati
 * dell'engine (l'orologio).
 */
interface SyncStore {
  /** Esegue [block] in modo atomico. Un'eccezione dentro [block] annulla tutto quello che [block] ha scritto. */
  fun <T> transaction(block: SyncStoreTransaction.() -> T): T
}

/** Le operazioni dentro una transazione del [SyncStore]. */
interface SyncStoreTransaction {
  /** Gli spazi che hanno qualcosa nel registro. */
  fun spaces(): List<String>

  fun record(space: String, recordId: String): SyncStoredRecord?

  fun records(space: String): List<SyncStoredRecord>

  fun putRecord(space: String, record: SyncStoredRecord)

  fun removeRecord(space: String, recordId: String)

  fun appendJournal(space: String, entry: SyncJournalEntry)

  /** Le voci del registro di [space], in ordine di [SyncJournalEntry.seq]. */
  fun journal(space: String): List<SyncJournalEntry>

  /** Quante voci ha il registro di [space]. */
  fun journalSize(space: String): Int

  /** Il numero d'ordine piu' alto fra le voci di [recordId], o 0. */
  fun lastJournalSeq(space: String, recordId: String): Long

  /** Toglie le voci di [recordId] fino a [throughSeq] compreso; risponde quante ne ha tolte. */
  fun removeJournal(space: String, recordId: String, throughSeq: Long): Int

  fun meta(space: String, key: String): ByteArray?

  /** Scrive (o, con null, toglie) un dato dell'engine per [space]. */
  fun putMeta(space: String, key: String, value: ByteArray?)
}

/**
 * Il registro in memoria: per le prove, le scene e i dispositivi finti. Le transazioni lavorano su una copia e la tengono
 * solo se finiscono, come un database. Lo stesso oggetto passato a due `FluidSync` uno dopo l'altro fa da «disco» fra due
 * avvii del processo.
 */
class InMemorySyncStore : SyncStore {
  private var data = Data()

  @Synchronized
  override fun <T> transaction(block: SyncStoreTransaction.() -> T): T {
    val working = data.copy()
    val result = Tx(working).block()
    data = working
    return result
  }

  private class Data(
    val records: HashMap<String, HashMap<String, SyncStoredRecord>> = HashMap(),
    val journal: HashMap<String, java.util.TreeMap<Long, SyncJournalEntry>> = HashMap(),
    val meta: HashMap<String, HashMap<String, ByteArray>> = HashMap(),
  ) {
    fun copy(): Data = Data(
      HashMap(records.mapValuesTo(HashMap()) { HashMap(it.value) }),
      HashMap(journal.mapValuesTo(HashMap()) { java.util.TreeMap(it.value) }),
      HashMap(meta.mapValuesTo(HashMap()) { HashMap(it.value) }),
    )
  }

  private class Tx(private val d: Data) : SyncStoreTransaction {
    override fun spaces(): List<String> =
      (d.records.filterValues { it.isNotEmpty() }.keys + d.journal.filterValues { it.isNotEmpty() }.keys)
        .filter { it.isNotEmpty() }.distinct().sorted()

    override fun record(space: String, recordId: String) = d.records[space]?.get(recordId)

    override fun records(space: String): List<SyncStoredRecord> =
      d.records[space]?.values?.sortedBy { it.recordId }.orEmpty()

    override fun putRecord(space: String, record: SyncStoredRecord) {
      d.records.getOrPut(space) { HashMap() }[record.recordId] = record
    }

    override fun removeRecord(space: String, recordId: String) {
      d.records[space]?.remove(recordId)
    }

    override fun appendJournal(space: String, entry: SyncJournalEntry) {
      d.journal.getOrPut(space) { java.util.TreeMap() }[entry.seq] = entry
    }

    override fun journal(space: String): List<SyncJournalEntry> = d.journal[space]?.values?.toList().orEmpty()

    override fun journalSize(space: String): Int = d.journal[space]?.size ?: 0

    override fun lastJournalSeq(space: String, recordId: String): Long =
      d.journal[space]?.values?.lastOrNull { it.recordId == recordId }?.seq ?: 0L

    override fun removeJournal(space: String, recordId: String, throughSeq: Long): Int {
      val entries = d.journal[space] ?: return 0
      val gone = entries.values.filter { it.recordId == recordId && it.seq <= throughSeq }
      gone.forEach { entries.remove(it.seq) }
      return gone.size
    }

    override fun meta(space: String, key: String): ByteArray? = d.meta[space]?.get(key)?.copyOf()

    override fun putMeta(space: String, key: String, value: ByteArray?) {
      if (value == null) d.meta[space]?.remove(key) else d.meta.getOrPut(space) { HashMap() }[key] = value.copyOf()
    }
  }
}

/** Le codifiche interne del registro: voci, invii in volo, numeri. Stesso formato in memoria e su disco. */
internal object SyncStoreCodec {
  fun encodeEntry(entry: SyncJournalEntry): ByteArray = SyncDocumentCodec.write { out ->
    out.writeByte(1)
    out.writeLong(entry.seq)
    SyncDocumentCodec.writeString(out, entry.recordId)
    SyncDocumentCodec.writeHlc(out, entry.stamp)
    writeChange(out, entry.change)
  }

  fun decodeEntry(bytes: ByteArray): SyncJournalEntry = SyncDocumentCodec.read(bytes) { input ->
    val version = input.readUnsignedByte()
    if (version != 1) throw SyncFormatException("voce del registro in formato $version")
    SyncJournalEntry(input.readLong(), SyncDocumentCodec.readString(input), SyncDocumentCodec.readHlc(input), readChange(input))
  }

  private fun writeChange(out: DataOutputStream, change: SyncChange) {
    when (change) {
      is SyncChange.Write -> {
        out.writeByte(1)
        SyncDocumentCodec.writeString(out, change.field)
        SyncDocumentCodec.writeValue(out, change.value)
      }
      SyncChange.Delete -> out.writeByte(2)
      is SyncChange.Choose -> {
        out.writeByte(3)
        SyncDocumentCodec.writeString(out, change.field)
        out.writeInt(change.kept.size)
        change.kept.forEach { SyncDocumentCodec.writeString(out, it) }
      }
    }
  }

  private fun readChange(input: DataInputStream): SyncChange = when (val tag = input.readUnsignedByte()) {
    1 -> SyncChange.Write(SyncDocumentCodec.readString(input), SyncDocumentCodec.readValue(input))
    2 -> SyncChange.Delete
    3 -> {
      val field = SyncDocumentCodec.readString(input)
      val n = input.readInt()
      if (n < 0 || n > 10_000) throw SyncFormatException("revisioni: $n")
      SyncChange.Choose(field, List(n) { SyncDocumentCodec.readString(input) })
    }
    else -> throw SyncFormatException("modifica sconosciuta: $tag")
  }

  fun encodeLong(value: Long): ByteArray = SyncDocumentCodec.write { it.writeLong(value) }

  fun decodeLong(bytes: ByteArray?): Long? = bytes?.let { SyncDocumentCodec.read(it) { input -> input.readLong() } }

  fun encodeHlc(hlc: SyncHlc): ByteArray = SyncDocumentCodec.write { SyncDocumentCodec.writeHlc(it, hlc) }

  fun decodeHlc(bytes: ByteArray?): SyncHlc? = bytes?.let { SyncDocumentCodec.read(it) { input -> SyncDocumentCodec.readHlc(input) } }

  fun encodeInflight(inflight: SyncInflight): ByteArray = SyncDocumentCodec.write { out ->
    out.writeByte(1)
    SyncDocumentCodec.writeString(out, inflight.key)
    out.writeInt(inflight.changes.size)
    for (c in inflight.changes) {
      SyncDocumentCodec.writeString(out, c.recordId)
      out.writeLong(c.baseVersion)
      out.writeBoolean(c.deleted)
      out.writeBoolean(c.payload != null)
      c.payload?.let {
        out.writeInt(it.keyVersion)
        SyncDocumentCodec.writeBytes(out, it.raw)
      }
      SyncDocumentCodec.writeDocument(out, c.document)
      out.writeLong(c.throughSeq)
    }
  }

  fun decodeInflight(bytes: ByteArray): SyncInflight = SyncDocumentCodec.read(bytes) { input ->
    val version = input.readUnsignedByte()
    if (version != 1) throw SyncFormatException("invio in volo in formato $version")
    val key = SyncDocumentCodec.readString(input)
    val n = input.readInt()
    if (n < 0 || n > 100_000) throw SyncFormatException("modifiche in volo: $n")
    SyncInflight(key, List(n) {
      val id = SyncDocumentCodec.readString(input)
      val base = input.readLong()
      val deleted = input.readBoolean()
      val payload = if (input.readBoolean()) {
        val keyVersion = input.readInt()
        SyncSealedPayload(SyncDocumentCodec.readBytes(input), keyVersion)
      } else null
      val document = SyncDocumentCodec.readDocument(input)
      SyncInflightChange(id, base, deleted, payload, document, input.readLong())
    })
  }
}

/** Un invio salvato prima di partire: si rimanda identico, con la stessa chiave, finche' non arriva la risposta. */
internal class SyncInflight(val key: String, val changes: List<SyncInflightChange>) {
  fun batch(): SyncPushBatch = SyncPushBatch(key, changes.map { SyncPushChange(it.recordId, it.baseVersion, it.deleted, it.payload) })

  fun change(recordId: String): SyncInflightChange? = changes.firstOrNull { it.recordId == recordId }
}

/** Una modifica in volo: il documento sigillato e mandato, e fin dove arriva nel registro ([throughSeq]). */
internal class SyncInflightChange(
  val recordId: String,
  val baseVersion: Long,
  val deleted: Boolean,
  val payload: SyncSealedPayload?,
  val document: SyncDocument,
  val throughSeq: Long,
)
