package dev.antigravity.fluidengine.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.TreeMap

/**
 * F10.8 · Il registro tenuto in memoria e salvato **tutto intero come un blob** a ogni transazione riuscita: per i
 * registri piccoli che chi chiama vuole chiudere da sé, per esempio con la cassaforte (`engine-vault`). È quello dei
 * luoghi comuni in Pampa Store: poche righe, e la casa dice dove abiti, quindi niente SQLite in chiaro.
 *
 * [load] dà il blob salvato (o `null` la prima volta), una volta sola, alla prima transazione; [save] riceve il blob
 * nuovo dopo ogni transazione che finisce. Se [save] lancia, la transazione è annullata anche in memoria: memoria e
 * disco non si separano. Una transazione che non cambia niente salva lo stesso (è semplice, e il registro è piccolo).
 *
 * Il formato è dell'engine e porta un numero di versione; un blob illeggibile è [SyncFormatException].
 */
class BlobSyncStore(
  private val load: () -> ByteArray?,
  private val save: (ByteArray) -> Unit,
) : SyncStore {
  private var data: InMemorySyncStore.Data? = null

  @Synchronized
  override fun <T> transaction(block: SyncStoreTransaction.() -> T): T {
    val current = data ?: (load()?.let(::decode) ?: InMemorySyncStore.Data()).also { data = it }
    val working = current.copy()
    val result = InMemorySyncStore.Tx(working).block()
    save(encode(working))
    data = working
    return result
  }

  internal companion object {
    private const val FORMAT = 1

    fun encode(d: InMemorySyncStore.Data): ByteArray = SyncDocumentCodec.write { out ->
      out.writeByte(FORMAT)
      val records = d.records.filterValues { it.isNotEmpty() }.toSortedMap()
      out.writeInt(records.size)
      for ((space, recs) in records) {
        SyncDocumentCodec.writeString(out, space)
        out.writeInt(recs.size)
        for (r in recs.values.sortedBy { it.recordId }) writeRecord(out, r)
      }
      val journal = d.journal.filterValues { it.isNotEmpty() }.toSortedMap()
      out.writeInt(journal.size)
      for ((space, entries) in journal) {
        SyncDocumentCodec.writeString(out, space)
        out.writeInt(entries.size)
        for (e in entries.values) SyncDocumentCodec.writeBytes(out, SyncStoreCodec.encodeEntry(e))
      }
      val meta = d.meta.filterValues { it.isNotEmpty() }.toSortedMap()
      out.writeInt(meta.size)
      for ((space, values) in meta) {
        SyncDocumentCodec.writeString(out, space)
        out.writeInt(values.size)
        for ((k, v) in values.toSortedMap()) {
          SyncDocumentCodec.writeString(out, k)
          SyncDocumentCodec.writeBytes(out, v)
        }
      }
    }

    fun decode(bytes: ByteArray): InMemorySyncStore.Data = SyncDocumentCodec.read(bytes) { input ->
      val version = input.readUnsignedByte()
      if (version != FORMAT) throw SyncFormatException("registro in formato $version")
      val d = InMemorySyncStore.Data()
      repeat(count(input)) {
        val space = SyncDocumentCodec.readString(input)
        val map = d.records.getOrPut(space) { HashMap() }
        repeat(count(input)) { readRecord(input).let { map[it.recordId] = it } }
      }
      repeat(count(input)) {
        val space = SyncDocumentCodec.readString(input)
        val map = d.journal.getOrPut(space) { TreeMap() }
        repeat(count(input)) { SyncStoreCodec.decodeEntry(SyncDocumentCodec.readBytes(input)).let { map[it.seq] = it } }
      }
      repeat(count(input)) {
        val space = SyncDocumentCodec.readString(input)
        val map = d.meta.getOrPut(space) { HashMap() }
        repeat(count(input)) { map[SyncDocumentCodec.readString(input)] = SyncDocumentCodec.readBytes(input) }
      }
      d
    }

    private fun count(input: DataInputStream): Int {
      val n = input.readInt()
      if (n < 0 || n > 1_000_000) throw SyncFormatException("conteggio non valido: $n")
      return n
    }

    private fun writeRecord(out: DataOutputStream, r: SyncStoredRecord) {
      SyncDocumentCodec.writeString(out, r.recordId)
      SyncDocumentCodec.writeDocument(out, r.local)
      out.writeBoolean(r.base != null)
      r.base?.let { SyncDocumentCodec.writeDocument(out, it) }
      out.writeLong(r.baseVersion)
      out.writeBoolean(r.dirty)
    }

    private fun readRecord(input: DataInputStream): SyncStoredRecord {
      val id = SyncDocumentCodec.readString(input)
      val local = SyncDocumentCodec.readDocument(input)
      val base = if (input.readBoolean()) SyncDocumentCodec.readDocument(input) else null
      return SyncStoredRecord(id, local, base, input.readLong(), input.readBoolean())
    }
  }
}
