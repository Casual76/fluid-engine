/*
 * engine-health (4.0, F8.26): la copia dei dati sul telefono, cifrata nella cassaforte, e la sincronizzazione per
 * differenze con Health Connect. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.health

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.vault.VaultSection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * La copia dei dati com'e' in un momento: i dati, il segno delle modifiche di Health Connect, le misure che copre e quando
 * si e' sincronizzata.
 */
@Immutable
class FluidHealthSnapshot(
  val samples: List<FluidHealthSample>,
  val token: String?,
  val metrics: Set<FluidHealthMetric>,
  val syncedAt: Instant?,
) {
  /** I dati di [metric]. */
  fun of(metric: FluidHealthMetric): List<FluidHealthSample> = samples.filter { it.metric == metric }

  override fun toString(): String = "FluidHealthSnapshot(${samples.size} dati, misure: $metrics)"

  companion object {
    val Empty = FluidHealthSnapshot(emptyList(), null, emptySet(), null)
  }
}

/** Dove sta la copia. [FluidVaultHealthStore] la cifra nella cassaforte; [FluidMemoryHealthStore] la tiene in memoria. */
interface FluidHealthStore {
  suspend fun load(): FluidHealthSnapshot
  suspend fun save(snapshot: FluidHealthSnapshot)
  suspend fun clear()
}

/**
 * La copia cifrata in un file della cassaforte ([VaultSection.file]): AES-GCM a pezzi con una chiave del Keystore, fuori
 * dal backup. Una sezione che si apre col solo telefono basta (`VaultAccess.Device()`); un'app che vuole l'impronta usa una
 * sezione all'impronta, e la copia si legge solo a sezione aperta.
 *
 * ```kotlin
 * val store = FluidVaultHealthStore(FluidVault.get(context).section("salute"))
 * ```
 */
class FluidVaultHealthStore(private val section: VaultSection, private val fileName: String = "salute.bin") : FluidHealthStore {
  override suspend fun load(): FluidHealthSnapshot {
    val file = section.file(fileName)
    if (!file.exists) return FluidHealthSnapshot.Empty
    return FluidHealthCodec.decode(file.read())
  }

  override suspend fun save(snapshot: FluidHealthSnapshot) {
    section.file(fileName).write(FluidHealthCodec.encode(snapshot))
  }

  override suspend fun clear() {
    section.file(fileName).delete()
  }
}

/** La copia in memoria: per le prove e per la galleria. */
class FluidMemoryHealthStore : FluidHealthStore {
  @Volatile private var current: FluidHealthSnapshot = FluidHealthSnapshot.Empty
  override suspend fun load(): FluidHealthSnapshot = current
  override suspend fun save(snapshot: FluidHealthSnapshot) {
    current = snapshot
  }
  override suspend fun clear() {
    current = FluidHealthSnapshot.Empty
  }
}

/** Com'e' andata una sincronizzazione. */
sealed interface FluidHealthSyncResult {
  /** Health Connect non c'e' (o va aggiornato): la copia resta com'era. */
  data class Unavailable(val availability: FluidHealthAvailability) : FluidHealthSyncResult

  /** Nessuna delle misure chieste ha il permesso di lettura: niente da leggere (la copia di quelle misure si toglie). */
  data object NoPermission : FluidHealthSyncResult

  /** Letto tutto da capo (la prima volta, un segno scaduto, misure cambiate): [count] dati. */
  data class Full(val count: Int, val denied: Set<FluidHealthMetric>) : FluidHealthSyncResult

  /** Lette solo le modifiche: [upserted] dati nuovi o cambiati, [deleted] tolti. */
  data class Changes(val upserted: Int, val deleted: Int, val denied: Set<FluidHealthMetric>) : FluidHealthSyncResult
}

/**
 * La sincronizzazione per differenze: la prima volta legge [window] all'indietro e prende il segno delle modifiche di
 * Health Connect; dopo chiede solo le modifiche dal segno (i dati nuovi, cambiati e tolti), pagina per pagina. Un segno
 * scaduto (Health Connect lo tiene 30 giorni), un permesso nuovo o tolto, o misure diverse rifanno la lettura intera.
 *
 * **Permessi.** Si leggono solo le misure col permesso di lettura; quelle senza vanno in `denied`, e i loro dati escono
 * dalla copia (un permesso tolto toglie anche la copia: l'app non tiene cio' che non puo' piu' leggere).
 *
 * Una sincronizzazione alla volta ([sync] e' protetta). I dati restano fuori dal thread principale e fuori dai log.
 */
class FluidHealthSync(
  private val gateway: FluidHealthGateway,
  private val store: FluidHealthStore,
  val metrics: Set<FluidHealthMetric>,
  /** Quanto indietro legge la prima volta. 30 giorni di serie, quanto Health Connect da' alle app senza il permesso dello storico. */
  val window: Duration = Duration.ofDays(30),
  private val clock: () -> Instant = Instant::now,
) {
  private val mutex = Mutex()

  /** La copia di adesso. */
  suspend fun snapshot(): FluidHealthSnapshot = store.load()

  /** Sincronizza: le modifiche se si puo', tutto da capo se serve. */
  suspend fun sync(): FluidHealthSyncResult = mutex.withLock {
    val availability = gateway.availability()
    if (availability != FluidHealthAvailability.Available) return FluidHealthSyncResult.Unavailable(availability)
    val granted = gateway.granted()
    val readable = metrics.filter { FluidHealthPermission(it) in granted }.toSet()
    val denied = metrics - readable
    val old = store.load()
    if (readable.isEmpty()) {
      if (old.samples.isNotEmpty() || old.token != null) store.save(FluidHealthSnapshot.Empty)
      return FluidHealthSyncResult.NoPermission
    }
    val token = old.token
    if (token == null || old.metrics != readable) return full(readable, denied)
    var current = LinkedHashMap<String, FluidHealthSample>().apply { for (s in old.samples) put(s.id, s) }
    var next: String = token
    var upserted = 0
    var deleted = 0
    while (true) {
      val page = gateway.changes(next)
      if (page.tokenExpired) return full(readable, denied)
      for (s in page.upserted) {
        if (s.metric !in readable) continue
        current[s.id] = s
        upserted += 1
      }
      for (id in page.deletedIds) {
        // Health Connect dice l'id del record; un battito e' «record#indice».
        val removed = current.remove(id) != null
        val prefix = "$id#"
        val keys = current.keys.filter { it.startsWith(prefix) }
        keys.forEach { current.remove(it) }
        if (removed || keys.isNotEmpty()) deleted += 1
      }
      next = page.nextToken
      if (!page.hasMore) break
    }
    // Il battito cambiato arriva intero: i suoi campioni vecchi oltre i nuovi si sono gia' sovrascritti per id.
    val horizon = clock().minus(window)
    current = LinkedHashMap(current.filterValues { !it.end.isBefore(horizon) })
    store.save(FluidHealthSnapshot(current.values.toList(), next, readable, clock()))
    return FluidHealthSyncResult.Changes(upserted, deleted, denied)
  }

  /** Dimentica la copia e il segno: la prossima [sync] rilegge tutto. */
  suspend fun reset() = mutex.withLock { store.clear() }

  private suspend fun full(readable: Set<FluidHealthMetric>, denied: Set<FluidHealthMetric>): FluidHealthSyncResult {
    val now = clock()
    val from = now.minus(window)
    // Il segno prima della lettura: le modifiche fatte mentre si legge arrivano la volta dopo (al piu' due volte, mai perse).
    val token = gateway.changesToken(readable)
    val samples = ArrayList<FluidHealthSample>()
    for (metric in readable) samples.addAll(gateway.read(metric, from, now))
    store.save(FluidHealthSnapshot(samples, token, readable, now))
    return FluidHealthSyncResult.Full(samples.size, denied)
  }
}

/**
 * Il formato della copia: binario, con una versione in testa. Le stringhe in UTF modificato di Java, gli istanti in
 * secondi e nanosecondi. Pura: si prova sulla JVM.
 */
object FluidHealthCodec {
  private const val Magic = 0x46484C31 // «FHL1»
  private const val Version = 1

  fun encode(snapshot: FluidHealthSnapshot): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
      out.writeInt(Magic)
      out.writeInt(Version)
      out.writeOptString(snapshot.token)
      out.writeInt(snapshot.metrics.size)
      snapshot.metrics.forEach { out.writeUTF(it.id) }
      out.writeOptInstant(snapshot.syncedAt)
      out.writeInt(snapshot.samples.size)
      for (s in snapshot.samples) {
        out.writeByte(when (s) { is FluidHealthInterval -> 1; is FluidHealthPoint -> 2; is FluidHealthSession -> 3 })
        out.writeUTF(s.id)
        out.writeUTF(s.metric.id)
        out.writeSource(s.source)
        out.writeOptInstant(s.lastModified)
        when (s) {
          is FluidHealthInterval -> { out.writeInstant(s.start); out.writeInstant(s.end); out.writeDouble(s.value) }
          is FluidHealthPoint -> { out.writeInstant(s.time); out.writeDouble(s.value) }
          is FluidHealthSession -> {
            out.writeInstant(s.start); out.writeInstant(s.end)
            out.writeOptString(s.title)
            out.writeInt(s.exerciseType ?: Int.MIN_VALUE)
            out.writeInt(s.stages.size)
            for (st in s.stages) { out.writeInstant(st.start); out.writeInstant(st.end); out.writeInt(st.type) }
          }
        }
      }
    }
    return bytes.toByteArray()
  }

  fun decode(bytes: ByteArray): FluidHealthSnapshot {
    DataInputStream(ByteArrayInputStream(bytes)).use { input ->
      if (input.readInt() != Magic) throw IOException("Non e' una copia dei dati di salute")
      val version = input.readInt()
      if (version != Version) throw IOException("Versione della copia sconosciuta: $version")
      val token = input.readOptString()
      val metrics = (0 until input.readInt()).map { FluidHealthMetric(input.readUTF()) }.toSet()
      val syncedAt = input.readOptInstant()
      val count = input.readInt()
      val samples = ArrayList<FluidHealthSample>(count)
      repeat(count) {
        val kind = input.readByte().toInt()
        val id = input.readUTF()
        val metric = FluidHealthMetric(input.readUTF())
        val source = input.readSource()
        val modified = input.readOptInstant()
        samples.add(
          when (kind) {
            1 -> FluidHealthInterval(id, metric, input.readInstant(), input.readInstant(), input.readDouble(), source, modified)
            2 -> FluidHealthPoint(id, metric, input.readInstant(), input.readDouble(), source, modified)
            3 -> {
              val start = input.readInstant()
              val end = input.readInstant()
              val title = input.readOptString()
              val type = input.readInt().takeIf { it != Int.MIN_VALUE }
              val stages = (0 until input.readInt()).map { FluidSleepStage(input.readInstant(), input.readInstant(), input.readInt()) }
              FluidHealthSession(id, metric, start, end, source, title, type, stages, modified)
            }
            else -> throw IOException("Dato di tipo sconosciuto: $kind")
          },
        )
      }
      return FluidHealthSnapshot(samples, token, metrics, syncedAt)
    }
  }

  private fun DataOutputStream.writeOptString(v: String?) {
    writeBoolean(v != null)
    if (v != null) writeUTF(v)
  }

  private fun DataInputStream.readOptString(): String? = if (readBoolean()) readUTF() else null

  private fun DataOutputStream.writeInstant(v: Instant) {
    writeLong(v.epochSecond)
    writeInt(v.nano)
  }

  private fun DataInputStream.readInstant(): Instant = Instant.ofEpochSecond(readLong(), readInt().toLong())

  private fun DataOutputStream.writeOptInstant(v: Instant?) {
    writeBoolean(v != null)
    if (v != null) writeInstant(v)
  }

  private fun DataInputStream.readOptInstant(): Instant? = if (readBoolean()) readInstant() else null

  private fun DataOutputStream.writeSource(s: FluidHealthSource) {
    writeUTF(s.packageName)
    writeOptString(s.appLabel)
    writeByte(s.device.ordinal)
    writeOptString(s.deviceName)
  }

  private fun DataInputStream.readSource(): FluidHealthSource {
    val pkg = readUTF()
    val label = readOptString()
    val device = FluidHealthDeviceKind.entries.getOrElse(readByte().toInt()) { FluidHealthDeviceKind.Unknown }
    val name = readOptString()
    return FluidHealthSource(pkg, label, device, name)
  }
}
