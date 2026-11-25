package dev.antigravity.fluidengine.vault

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * I valori di una sezione: testi e byte per nome, tutti in un file cifrato solo (`values.fv`), scritto tutto o niente.
 * Pensati per poche decine di voci piccole: password, token, chiavi. Per un documento serve un [VaultFile].
 *
 * Il file si legge una volta e resta in memoria finché la sezione è aperta; alla chiusura la copia in chiaro si azzera.
 * Ogni lettura o scrittura di una sezione chiusa dà [VaultException.Locked] (una sezione «solo telefono» si apre da
 * sola).
 */
class VaultValues internal constructor(private val section: VaultSection) {
  private val mutex = Mutex()
  private var cache: LinkedHashMap<String, ByteArray>? = null
  private val changes = MutableStateFlow(0L)
  private val file: File get() = File(section.dir, "values.fv")
  private val associated: ByteArray
    get() = "fluid-vault/v1/${section.vaultName}/${section.name}/values".toByteArray(Charsets.UTF_8)

  /** Il valore [key] in byte, o null se non c'è. Chi lo riceve lo azzera quando ha finito. */
  suspend fun getBytes(key: String): ByteArray? = read { it[key]?.copyOf() }

  /** Scrive [value] sotto [key]; null lo toglie. */
  suspend fun putBytes(key: String, value: ByteArray?) = edit { map ->
    if (value == null) map.remove(key)?.fill(0) else map.put(key, value.copyOf())?.fill(0)
  }

  /** Il testo [key], o null se non c'è. */
  suspend fun getString(key: String): String? = read { it[key]?.toString(Charsets.UTF_8) }

  /** Scrive il testo [value] sotto [key]; null lo toglie. */
  suspend fun putString(key: String, value: String?) = putBytes(key, value?.toByteArray(Charsets.UTF_8))

  /** Toglie [key]. */
  suspend fun remove(key: String) = putBytes(key, null)

  /** I nomi dei valori che ci sono. */
  suspend fun keys(): Set<String> = read { it.keys.toSet() }

  /** Vero se c'è il valore [key]. */
  suspend fun contains(key: String): Boolean = read { key in it }

  /** Toglie tutti i valori della sezione. */
  suspend fun clear() = edit { map ->
    map.values.forEach { it.fill(0) }
    map.clear()
  }

  /**
   * Un segnale a ogni scrittura: un'interfaccia lo osserva e rilegge quello che le serve. Non porta i valori, così un
   * segreto non resta in un `Flow`.
   */
  val changed: Flow<Long> = changes

  /** Il testo [key] ogni volta che cambia, letto quando serve. Una sezione chiusa fa fallire il `Flow`. */
  fun observeString(key: String): Flow<String?> = changes.map { getString(key) }.distinctUntilChanged()

  internal fun dropCache() {
    val dropped = cache
    cache = null
    // Si azzera solo se nessuno la sta leggendo: una lettura in corso tiene la sua mappa, e la lascia al GC.
    if (dropped != null && mutex.tryLock()) {
      try {
        dropped.values.forEach { it.fill(0) }
      } finally {
        mutex.unlock()
      }
    }
  }

  private suspend fun <T> read(block: (Map<String, ByteArray>) -> T): T = section.withKey { key ->
    mutex.withLock { block(load(key)) }
  }

  private suspend fun edit(block: (MutableMap<String, ByteArray>) -> Unit) {
    section.withKey { key ->
      mutex.withLock {
        val map = load(key)
        block(map)
        save(key, map)
      }
    }
    changes.value = changes.value + 1
  }

  private fun load(key: ByteArray): LinkedHashMap<String, ByteArray> {
    cache?.let { return it }
    val loaded = LinkedHashMap<String, ByteArray>()
    if (file.exists()) {
      val valuesKey = VaultCrypto.hkdf(key, ByteArray(0), Info)
      val plain = try {
        VaultCrypto.open(valuesKey, file.readBytes(), associated)
      } finally {
        valuesKey.fill(0)
      }
      try {
        val reader = VaultRecordReader(plain)
        if (reader.int() != Magic) throw VaultException.Corrupt("non sono i valori di una sezione")
        repeat(reader.int()) {
          val name = reader.text() ?: throw VaultException.Corrupt()
          loaded[name] = reader.bytes() ?: throw VaultException.Corrupt()
        }
      } finally {
        plain.fill(0)
      }
    }
    cache = loaded
    return loaded
  }

  private fun save(key: ByteArray, map: Map<String, ByteArray>) {
    val writer = VaultRecordWriter().int(Magic).int(map.size)
    map.forEach { (name, value) -> writer.text(name).bytes(value) }
    val plain = writer.toByteArray()
    val valuesKey = VaultCrypto.hkdf(key, ByteArray(0), Info)
    try {
      VaultSection.atomicWrite(file, VaultCrypto.seal(valuesKey, plain, associated))
    } finally {
      valuesKey.fill(0)
      plain.fill(0)
    }
  }

  private companion object {
    const val Magic = 0x46565631 // "FVV1"
    val Info = "fluid-vault/v1/values".toByteArray(Charsets.UTF_8)
  }
}
