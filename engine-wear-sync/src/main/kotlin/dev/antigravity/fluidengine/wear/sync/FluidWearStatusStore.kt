package dev.antigravity.fluidengine.wear.sync

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Gli stati pubblicati dal telefono, tenuti sull'orologio: l'ultimo per chiave, su disco.
 *
 * È da qui che leggono le Tile e le complicazioni di `engine-wear` (`FluidStatusTileService`,
 * `FluidStatusComplicationService`): un file locale, letto in un attimo e senza chiedere niente a Play services, quindi
 * lo stato c'è anche ad app chiusa, col telefono lontano e subito dopo un riavvio.
 *
 * Un aggiornamento più vecchio di quello tenuto (stesso [FluidWearStatus.key], [FluidWearStatus.updatedAtMillis] minore)
 * si scarta: il Data Layer non promette l'ordine.
 */
class FluidWearStatusStore(private val store: FluidWearStore) {
  private val lock = Any()
  private val state = MutableStateFlow(load())

  /** Gli stati tenuti, per chiave. */
  val statuses: StateFlow<Map<String, FluidWearStatus>> = state.asStateFlow()

  /** Lo stato [key], o `null`. */
  fun get(key: String): FluidWearStatus? = state.value[key]

  /** Tiene [status] se è più nuovo di quello che c'è. Torna vero se ha cambiato qualcosa. */
  fun put(status: FluidWearStatus): Boolean = synchronized(lock) {
    val held = state.value[status.key]
    if (held != null && (held.updatedAtMillis > status.updatedAtMillis || held == status)) return@synchronized false
    commit(state.value + (status.key to status))
    true
  }

  /** Dimentica lo stato [key] (il telefono l'ha tolto). Torna vero se c'era. */
  fun remove(key: String): Boolean = synchronized(lock) {
    if (key !in state.value) return@synchronized false
    commit(state.value - key)
    true
  }

  private fun commit(next: Map<String, FluidWearStatus>) {
    val frozen = java.util.Collections.unmodifiableMap(LinkedHashMap(next))
    store.write(FileName, encode(frozen.values))
    state.value = frozen
  }

  private fun load(): Map<String, FluidWearStatus> {
    val bytes = store.read(FileName) ?: return emptyMap()
    return try {
      decode(bytes)
    } catch (e: IOException) {
      // Un archivio rotto si ricomincia: il telefono ripubblica alla prossima occasione.
      emptyMap()
    }
  }

  private companion object {
    const val FileName = "stati.fws"
    const val FileVersion = 1

    fun encode(values: Collection<FluidWearStatus>): ByteArray {
      val buffer = ByteArrayOutputStream()
      DataOutputStream(buffer).use { out ->
        out.writeInt(FileVersion)
        out.writeInt(values.size)
        for (s in values) {
          val bytes = FluidWearFormat.encode(s)
          out.writeInt(bytes.size)
          out.write(bytes)
        }
      }
      return buffer.toByteArray()
    }

    fun decode(bytes: ByteArray): Map<String, FluidWearStatus> {
      val input = DataInputStream(ByteArrayInputStream(bytes))
      if (input.readInt() != FileVersion) throw IOException("versione degli stati sconosciuta")
      val count = input.readInt()
      if (count < 0 || count > 10_000) throw IOException("conto degli stati fuori misura: $count")
      val map = LinkedHashMap<String, FluidWearStatus>(count)
      repeat(count) {
        val size = input.readInt()
        if (size < 0 || size > input.available()) throw IOException("stato fuori misura")
        val raw = ByteArray(size).also { input.readFully(it) }
        val status = (FluidWearFormat.decode(raw) as? FluidWearDecoded.Status)?.status
          ?: throw IOException("stato illeggibile")
        map[status.key] = status
      }
      return java.util.Collections.unmodifiableMap(map)
    }
  }
}
