package dev.antigravity.fluidengine.transfer

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import java.io.Closeable
import java.io.File

/**
 * Dove sta la coda: un trasferimento per riga. Si legge una volta all'avvio ([load]) e si scrive a ogni cambio che conta
 * (stato, e l'avanzamento ogni qualche secondo). Lo usa solo il dispatcher della coda, mai il thread principale.
 */
interface FluidTransferStore {
  fun load(): List<FluidTransferRecord>

  fun save(record: FluidTransferRecord)

  fun delete(id: String)
}

/** La coda in memoria: per le prove e per le scene. Sopravvive a un `FluidTransfers` nuovo, non al processo. */
class InMemoryFluidTransferStore : FluidTransferStore {
  private val records = LinkedHashMap<String, FluidTransferRecord>()

  /** Quante scritture ha ricevuto (le prove controllano che l'avanzamento non scriva a ogni pezzo). */
  @Volatile var writes: Int = 0
    private set

  @Synchronized override fun load(): List<FluidTransferRecord> = records.values.toList()

  @Synchronized override fun save(record: FluidTransferRecord) {
    writes++
    records[record.id] = record
  }

  @Synchronized override fun delete(id: String) {
    records.remove(id)
  }
}

/**
 * La coda su disco, in SQLite della piattaforma (come il registro di `engine-sync`: niente Room e niente processore di
 * annotazioni nell'app). Sta in `noBackupFilesDir`: una coda copiata su un telefono nuovo punterebbe a file che li' non ci
 * sono.
 */
class SqliteFluidTransferStore(context: Context, private val name: String = "fluid_transfers.db") : FluidTransferStore, Closeable {
  private val app = context.applicationContext

  /** Il file del database. Si chiede la prima volta che serve, fuori dal thread principale: crea la cartella. */
  val file: File by lazy { File(app.noBackupFilesDir, name) }

  // Pigro: aprire la coda (di solito nell'Application, sul thread principale) non tocca il disco.
  private val opened = lazy { Helper(app, file.absolutePath) }
  private val helper by opened

  private class Helper(context: Context, path: String) : SQLiteOpenHelper(context, path, null, Version) {
    override fun onCreate(db: SQLiteDatabase) {
      db.execSQL(
        """
        CREATE TABLE transfers (
          id TEXT PRIMARY KEY NOT NULL, direction TEXT NOT NULL, url TEXT NOT NULL, path TEXT NOT NULL, title TEXT NOT NULL,
          subtitle TEXT, expected INTEGER NOT NULL, sha256 TEXT, wifi_only INTEGER NOT NULL, charging_only INTEGER NOT NULL,
          work TEXT NOT NULL, tag TEXT, mime TEXT, method TEXT NOT NULL, resumable_upload INTEGER NOT NULL,
          state TEXT NOT NULL, done INTEGER NOT NULL, total INTEGER NOT NULL, validator TEXT, resumable INTEGER NOT NULL,
          failure_kind TEXT, failure_retryable INTEGER, failure_code INTEGER, failure_message TEXT, waiting TEXT,
          attempts INTEGER NOT NULL, forced INTEGER NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL,
          finished INTEGER, upload_acked INTEGER NOT NULL
        )
        """.trimIndent(),
      )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
  }

  override fun load(): List<FluidTransferRecord> {
    val out = ArrayList<FluidTransferRecord>()
    helper.readableDatabase.rawQuery("SELECT * FROM transfers ORDER BY created, id", null).use { cursor ->
      while (cursor.moveToNext()) runCatching { cursor.record() }.getOrNull()?.let(out::add)
    }
    return out
  }

  override fun save(record: FluidTransferRecord) {
    helper.writableDatabase.insertWithOnConflict("transfers", null, record.values(), SQLiteDatabase.CONFLICT_REPLACE)
  }

  override fun delete(id: String) {
    helper.writableDatabase.delete("transfers", "id = ?", arrayOf(id))
  }

  override fun close() {
    if (opened.isInitialized()) helper.close()
  }

  private fun FluidTransferRecord.values(): ContentValues = ContentValues().apply {
    val r = request
    put("id", r.id)
    put("direction", r.direction.name)
    put("url", r.url)
    put("path", r.file.absolutePath)
    put("title", r.title)
    put("subtitle", r.subtitle)
    put("expected", r.expectedBytes)
    put("sha256", r.sha256)
    put("wifi_only", if (r.wifiOnly) 1 else 0)
    put("charging_only", if (r.chargingOnly) 1 else 0)
    put("work", TransferWorkCodec.encode(r.work))
    put("tag", r.tag)
    put("mime", r.mimeType)
    put("method", r.method)
    put("resumable_upload", if (r.resumableUpload) 1 else 0)
    put("state", state.name)
    put("done", bytesDone)
    put("total", bytesTotal)
    put("validator", validator)
    put("resumable", if (resumable) 1 else 0)
    put("failure_kind", failure?.kind)
    put("failure_retryable", failure?.let { if (it.retryable) 1 else 0 })
    put("failure_code", failure?.code)
    put("failure_message", failure?.message)
    put("waiting", waitingFor.joinToString(",") { it.name }.ifEmpty { null })
    put("attempts", attempts)
    put("forced", if (forced) 1 else 0)
    put("created", createdAtMillis)
    put("updated", updatedAtMillis)
    put("finished", finishedAtMillis)
    put("upload_acked", uploadAcked)
  }

  private fun Cursor.record(): FluidTransferRecord {
    fun str(name: String): String? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
    fun long(name: String): Long? = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getLong(it) }
    fun bool(name: String): Boolean = (long(name) ?: 0L) != 0L
    val request = FluidTransferRequest(
      id = str("id")!!,
      direction = FluidTransferDirection.named(str("direction")!!),
      url = str("url")!!,
      file = File(str("path")!!),
      title = str("title")!!,
      subtitle = str("subtitle"),
      expectedBytes = long("expected") ?: -1L,
      sha256 = str("sha256"),
      wifiOnly = bool("wifi_only"),
      chargingOnly = bool("charging_only"),
      work = TransferWorkCodec.decode(str("work")),
      tag = str("tag"),
      mimeType = str("mime"),
      method = str("method") ?: "GET",
      resumableUpload = bool("resumable_upload"),
    )
    val failureKind = str("failure_kind")
    return FluidTransferRecord(
      request = request,
      state = FluidTransferState.named(str("state")!!),
      bytesDone = long("done") ?: 0L,
      bytesTotal = long("total") ?: -1L,
      validator = str("validator"),
      resumable = bool("resumable"),
      failure = failureKind?.let {
        FluidTransferFailure.restore(it, bool("failure_retryable"), long("failure_code")?.toInt(), str("failure_message"))
      },
      waitingFor = str("waiting")?.split(',')?.mapNotNull(FluidConditionReason::named).orEmpty(),
      attempts = (long("attempts") ?: 0L).toInt(),
      forced = bool("forced"),
      createdAtMillis = long("created") ?: 0L,
      updatedAtMillis = long("updated") ?: 0L,
      finishedAtMillis = long("finished"),
      uploadAcked = long("upload_acked") ?: 0L,
    )
  }

  private companion object {
    const val Version = 1
  }
}

/** Il genere di lavoro su disco: il nome e cosa evita, cosi' anche un lavoro su misura si rilegge uguale. */
internal object TransferWorkCodec {
  fun encode(work: FluidWork): String = listOf(
    work.name,
    work.needsNetwork, work.avoidsExpensive, work.avoidsConstrained, work.avoidsLowPower, work.avoidsHeat,
    work.lowPowerWhileCharging,
  ).joinToString("|") { if (it is Boolean) (if (it) "1" else "0") else it.toString() }

  fun decode(text: String?): FluidWork {
    val parts = text?.split('|') ?: return FluidWork.LargeDownload
    if (parts.size < 6) return FluidWork.LargeDownload
    val known = listOf(FluidWork.LargeDownload, FluidWork.LargeUpload, FluidWork.Prefetch, FluidWork.RichMedia, FluidWork.Sync)
      .firstOrNull { it.name == parts[0] }
    if (known != null) return known
    fun flag(i: Int) = parts[i] == "1"
    // Il settimo segno (F8.20b) manca nelle righe scritte prima: vale «no».
    return FluidWork.custom(parts[0], flag(1), flag(2), flag(3), flag(4), flag(5), parts.size > 6 && flag(6))
  }
}
