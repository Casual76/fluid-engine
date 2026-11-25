package dev.antigravity.fluidengine.sync

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.Closeable
import java.io.File

/**
 * Il registro su disco, in SQLite della piattaforma (niente Room: nessun processore di annotazioni per l'engine o per
 * l'app). Tre tabelle: gli elementi (stato locale, base e versione), le voci del registro e i dati dell'engine (cursore,
 * invio in volo, orologio). Ogni [transaction] e' una transazione di SQLite: una modifica e la sua voce, o una pagina e il
 * cursore, arrivano su disco insieme o per niente.
 *
 * Il file sta in `noBackupFilesDir` ([file]): il backup automatico di Android non lo copia su un telefono nuovo, dove
 * un registro con le voci in attesa e l'orologio di un altro dispositivo farebbe danni. I dati tornano dal relay.
 *
 * Il database si apre alla prima transazione, quindi sul dispatcher dell'engine e mai sul thread principale.
 */
class SqliteSyncStore(context: Context, name: String = "fluid_sync.db") : SyncStore, Closeable {
  /** Il file del registro. */
  val file: File = File(context.applicationContext.noBackupFilesDir, name)

  private val helper = object : SQLiteOpenHelper(context.applicationContext, file.path, null, SchemaVersion) {
    override fun onConfigure(db: SQLiteDatabase) {
      db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        "CREATE TABLE records (space TEXT NOT NULL, id TEXT NOT NULL, local BLOB NOT NULL, base BLOB, " +
          "base_version INTEGER NOT NULL, dirty INTEGER NOT NULL, PRIMARY KEY (space, id))",
      )
      db.execSQL(
        "CREATE TABLE journal (space TEXT NOT NULL, seq INTEGER NOT NULL, record TEXT NOT NULL, entry BLOB NOT NULL, " +
          "PRIMARY KEY (space, seq))",
      )
      db.execSQL("CREATE INDEX journal_record ON journal (space, record, seq)")
      db.execSQL("CREATE TABLE meta (space TEXT NOT NULL, key TEXT NOT NULL, value BLOB NOT NULL, PRIMARY KEY (space, key))")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  override fun <T> transaction(block: SyncStoreTransaction.() -> T): T {
    val db = helper.writableDatabase
    db.beginTransaction()
    try {
      val result = Tx(db).block()
      db.setTransactionSuccessful()
      return result
    } finally {
      db.endTransaction()
    }
  }

  override fun close() = helper.close()

  private class Tx(private val db: SQLiteDatabase) : SyncStoreTransaction {
    override fun spaces(): List<String> = db.rawQuery(
      "SELECT space FROM records UNION SELECT space FROM journal ORDER BY space",
      null,
    ).use { c -> buildList { while (c.moveToNext()) c.getString(0).takeIf { it.isNotEmpty() }?.let(::add) } }

    override fun record(space: String, recordId: String): SyncStoredRecord? = db.rawQuery(
      "SELECT id, local, base, base_version, dirty FROM records WHERE space = ? AND id = ?",
      arrayOf(space, recordId),
    ).use { c -> if (c.moveToFirst()) readRecord(c) else null }

    override fun records(space: String): List<SyncStoredRecord> = db.rawQuery(
      "SELECT id, local, base, base_version, dirty FROM records WHERE space = ? ORDER BY id",
      arrayOf(space),
    ).use { c -> buildList { while (c.moveToNext()) add(readRecord(c)) } }

    private fun readRecord(c: android.database.Cursor) = SyncStoredRecord(
      recordId = c.getString(0),
      local = SyncDocumentCodec.decode(c.getBlob(1)),
      base = if (c.isNull(2)) null else SyncDocumentCodec.decode(c.getBlob(2)),
      baseVersion = c.getLong(3),
      dirty = c.getInt(4) != 0,
    )

    override fun putRecord(space: String, record: SyncStoredRecord) {
      db.insertWithOnConflict(
        "records",
        null,
        ContentValues().apply {
          put("space", space)
          put("id", record.recordId)
          put("local", SyncDocumentCodec.encode(record.local))
          if (record.base != null) put("base", SyncDocumentCodec.encode(record.base)) else putNull("base")
          put("base_version", record.baseVersion)
          put("dirty", if (record.dirty) 1 else 0)
        },
        SQLiteDatabase.CONFLICT_REPLACE,
      )
    }

    override fun removeRecord(space: String, recordId: String) {
      db.delete("records", "space = ? AND id = ?", arrayOf(space, recordId))
    }

    override fun appendJournal(space: String, entry: SyncJournalEntry) {
      db.insertOrThrow(
        "journal",
        null,
        ContentValues().apply {
          put("space", space)
          put("seq", entry.seq)
          put("record", entry.recordId)
          put("entry", SyncStoreCodec.encodeEntry(entry))
        },
      )
    }

    override fun journal(space: String): List<SyncJournalEntry> = db.rawQuery(
      "SELECT entry FROM journal WHERE space = ? ORDER BY seq",
      arrayOf(space),
    ).use { c -> buildList { while (c.moveToNext()) add(SyncStoreCodec.decodeEntry(c.getBlob(0))) } }

    override fun journalSize(space: String): Int = db.rawQuery(
      "SELECT COUNT(*) FROM journal WHERE space = ?",
      arrayOf(space),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    // L'indice journal_record (space, record, seq) copre il conto.
    override fun journalRecordCount(space: String): Int = db.rawQuery(
      "SELECT COUNT(DISTINCT record) FROM journal WHERE space = ?",
      arrayOf(space),
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    override fun lastJournalSeq(space: String, recordId: String): Long = db.rawQuery(
      "SELECT MAX(seq) FROM journal WHERE space = ? AND record = ?",
      arrayOf(space, recordId),
    ).use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }

    override fun removeJournal(space: String, recordId: String, throughSeq: Long): Int =
      db.delete("journal", "space = ? AND record = ? AND seq <= ?", arrayOf(space, recordId, throughSeq.toString()))

    override fun meta(space: String, key: String): ByteArray? = db.rawQuery(
      "SELECT value FROM meta WHERE space = ? AND key = ?",
      arrayOf(space, key),
    ).use { c -> if (c.moveToFirst()) c.getBlob(0) else null }

    override fun putMeta(space: String, key: String, value: ByteArray?) {
      if (value == null) {
        db.delete("meta", "space = ? AND key = ?", arrayOf(space, key))
      } else {
        db.insertWithOnConflict(
          "meta",
          null,
          ContentValues().apply {
            put("space", space)
            put("key", key)
            put("value", value)
          },
          SQLiteDatabase.CONFLICT_REPLACE,
        )
      }
    }
  }

  private companion object {
    const val SchemaVersion = 1
  }
}
