package dev.antigravity.fluidengine.diagnostics

import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * La coda dei rapporti sul telefono (F8.24): un file di righe JSON in `noBackupFilesDir` (fuori dal backup, come i
 * rapporti di MetricKit restano sul dispositivo), con un tetto di righe e di byte. Resta qui finché l'utente non dice di
 * sì e l'invio non riesce; con il no si svuota.
 *
 * **Il tetto.** Oltre [maxRecords] righe o [maxBytes] byte si tolgono le più vecchie, prima i fotogrammi e poi il resto:
 * un crash vale più di un giorno di fotogrammi. I fotogrammi della stessa pagina, giorno e versione stanno in una riga
 * sola, che si somma ([mergeFrames]): un mese di uso non riempie la coda.
 *
 * Le scritture sono brevi e sincrone sotto un lucchetto: il gestore dei crash scrive dal filo che muore, e non c'è un
 * dopo. Gli altri chiamano da un filo in sottofondo. [count] si legge da qualunque filo (un valore intero, F7.21).
 */
class FluidDiagnosticsQueue internal constructor(
  private val file: File,
  val maxRecords: Int = DefaultMaxRecords,
  val maxBytes: Long = DefaultMaxBytes,
) {
  private val lock = Any()
  private val mutableCount = MutableStateFlow(0)

  init {
    require(maxRecords > 0 && maxBytes > 0) { "Il tetto della coda dev'essere positivo" }
  }

  /** Quanti rapporti aspettano (giusto dopo la prima lettura in sottofondo, o dopo [refreshCount]). */
  val count: StateFlow<Int> = mutableCount.asStateFlow()

  /** Rilegge il file e aggiorna [count]. Da un filo in sottofondo. */
  fun refreshCount() {
    synchronized(lock) { mutableCount.value = readUnlocked().size }
  }

  /** Tutti i rapporti in coda, dal più vecchio. Legge il file: da un filo in sottofondo. */
  fun records(): List<FluidDiagnosticsRecord> = synchronized(lock) { readUnlocked() }

  /**
   * Aggiunge [record] in fondo. [durable] chiede al disco di scriverlo davvero prima di tornare (il crash: il processo sta
   * per finire). Poi applica il tetto.
   */
  fun append(record: FluidDiagnosticsRecord, durable: Boolean = false) {
    synchronized(lock) {
      file.parentFile?.mkdirs()
      FileOutputStream(file, true).use { out ->
        out.write((record.toJsonString() + "\n").toByteArray(Charsets.UTF_8))
        if (durable) runCatching { out.fd.sync() }
      }
      enforceCapUnlocked(readUnlocked(), force = false)
    }
  }

  /** Somma [frames] alla riga della stessa pagina, giorno e versione, o ne apre una nuova con [source]. */
  fun mergeFrames(source: FluidDiagnosticsSource, now: Long, frames: List<FluidPageFrames>) {
    if (frames.isEmpty()) return
    synchronized(lock) {
      val records = readUnlocked().toMutableList()
      for (more in frames) {
        if (more.frames <= 0) continue
        val fresh = FluidDiagnosticsRecord.frames(source, now, more)
        val at = records.indexOfFirst { it.kind == FluidDiagnosticsKind.Frames && it.framesKey == fresh.framesKey }
        if (at >= 0) records[at] = records[at].withFrames(more) else records += fresh
      }
      enforceCapUnlocked(records, force = true)
    }
  }

  /** Toglie i rapporti con questi [ids] (quelli che il server ha preso). */
  fun remove(ids: Set<String>) {
    if (ids.isEmpty()) return
    synchronized(lock) {
      val before = readUnlocked()
      val after = before.filterNot { it.id in ids }
      if (after.size != before.size) writeUnlocked(after)
    }
  }

  /** Svuota la coda (il no dell'utente, «Cancella i dati»). */
  fun clear() {
    synchronized(lock) {
      file.delete()
      mutableCount.value = 0
    }
  }

  private fun enforceCapUnlocked(records: List<FluidDiagnosticsRecord>, force: Boolean) {
    val lines = records.map { it.toJsonString() }
    var bytes = lines.sumOf { it.toByteArray(Charsets.UTF_8).size + 1L }
    if (records.size <= maxRecords && bytes <= maxBytes) {
      if (force) writeUnlocked(records) else mutableCount.value = records.size
      return
    }
    val kept = records.toMutableList()
    val sizes = lines.map { it.toByteArray(Charsets.UTF_8).size + 1L }.toMutableList()
    // Prima i fotogrammi più vecchi, poi il resto più vecchio.
    while (kept.size > maxRecords || bytes > maxBytes) {
      val victim = kept.indexOfFirst { it.kind == FluidDiagnosticsKind.Frames }.takeIf { it >= 0 } ?: 0
      bytes -= sizes.removeAt(victim)
      kept.removeAt(victim)
      if (kept.isEmpty()) break
    }
    writeUnlocked(kept)
  }

  private fun readUnlocked(): List<FluidDiagnosticsRecord> {
    if (!file.exists()) return emptyList()
    return runCatching {
      file.readLines(Charsets.UTF_8).mapNotNull { line -> line.takeIf { it.isNotBlank() }?.let(FluidDiagnosticsRecord::fromJsonString) }
    }.getOrDefault(emptyList())
  }

  private fun writeUnlocked(records: List<FluidDiagnosticsRecord>) {
    file.parentFile?.mkdirs()
    val temp = File(file.parentFile, file.name + ".nuovo")
    temp.writeText(records.joinToString("") { it.toJsonString() + "\n" }, Charsets.UTF_8)
    if (!temp.renameTo(file)) {
      file.delete()
      temp.renameTo(file)
    }
    mutableCount.value = records.size
  }

  companion object {
    /** Il tetto di righe di serie: 200 rapporti (a occhio, 26/11/2026: settimane di uso senza rete). */
    const val DefaultMaxRecords: Int = 200

    /** Il tetto di byte di serie: 512 KiB (a occhio, 26/11/2026; un crash pesa circa 3 kB, un giorno di pagina 0,5). */
    const val DefaultMaxBytes: Long = 512L * 1024
  }
}
