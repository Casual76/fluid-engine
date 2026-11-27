package dev.antigravity.fluidengine.health

import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * R3 · Le correzioni della review R3 su engine-health: la copia cifrata rovinata (8), le misure che non si sommano lette
 * come un giorno (24) e il battito che si accorcia (25).
 */
class FluidHealthR3Test {
  @get:Rule val tmp = TemporaryFolder()

  private val zone = ZoneId.of("Europe/Rome")
  private val day = LocalDate.of(2026, 11, 20)
  private fun at(h: Int, m: Int = 0): Instant = day.atTime(LocalTime.of(h, m)).atZone(zone).toInstant()
  private val now = at(22)
  private val watch = FluidHealthSource("com.example.watch", "Orologio", FluidHealthDeviceKind.Watch)
  private val scale = FluidHealthSource("com.example.scale", "Bilancia", FluidHealthDeviceKind.Scale)

  private fun steps(id: String, hour: Int, count: Int) =
    FluidHealthInterval(id, FluidHealthMetric.Steps, at(hour), at(hour, 59), count.toDouble(), watch)

  private fun heart(id: String, minute: Int, bpm: Double) =
    FluidHealthPoint(id, FluidHealthMetric.HeartRate, at(20, minute), bpm, watch)

  // --- 8 · la copia rovinata ---------------------------------------------------------------------------------------

  private fun gateway() = FluidMemoryHealthGateway().apply {
    grant(FluidHealthPermission.read(FluidHealthMetric.Steps))
    put(steps("a", 9, 1200), steps("b", 10, 800))
  }

  private fun vault() = FluidVault.create(tmp.newFolder("cassaforte"), MemoryVaultKeystore(), clock = { 0L }, io = Dispatchers.IO)

  @Test
  fun `una copia tronca nella cassaforte si rifa', non ferma la salute per sempre`() = runBlocking {
    val vault = vault()
    val section = vault.section("salute")
    val good = FluidHealthCodec.encode(FluidHealthSnapshot(listOf(steps("a", 9, 1200)), "segno-3", setOf(FluidHealthMetric.Steps), now))
    // Una copia scritta a meta' (una versione vecchia, un disco pieno): cifrata bene, ma il contenuto si ferma presto.
    section.file("salute.bin").write(good.copyOf(good.size / 2))
    val store = FluidVaultHealthStore(section)
    val sync = FluidHealthSync(gateway(), store, setOf(FluidHealthMetric.Steps), clock = { now })
    assertEquals("la copia illeggibile vale come assente", 0, sync.snapshot().samples.size)
    assertEquals(FluidHealthSyncResult.Full(2, emptySet()), sync.sync())
    assertEquals(setOf("a", "b"), sync.snapshot().samples.map { it.id }.toSet())
  }

  @Test
  fun `un pezzo cifrato rovinato sul disco si rifa' anche lui`() = runBlocking {
    val vault = vault()
    val section = vault.section("salute")
    val store = FluidVaultHealthStore(section)
    val sync = FluidHealthSync(gateway(), store, setOf(FluidHealthMetric.Steps), clock = { now })
    sync.sync()
    val file = section.file("salute.bin").file
    val bytes = file.readBytes()
    bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 0x5A).toByte()
    file.writeBytes(bytes)
    assertEquals(FluidHealthSyncResult.Full(2, emptySet()), sync.sync())
  }

  /** Una copia nel formato giusto fino al numero dei dati, che dice [count]. */
  private fun withCount(count: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
      out.writeInt(0x46484C31)
      out.writeInt(1)
      out.writeBoolean(false) // niente segno
      out.writeInt(0) // nessuna misura
      out.writeBoolean(false) // mai sincronizzata
      out.writeInt(count)
    }
    return bytes.toByteArray()
  }

  @Test
  fun `un conteggio fuori misura e' una copia illeggibile, non memoria chiesta a caso`() {
    for (count in listOf(Int.MAX_VALUE, -7, 1_000_000)) {
      // Prima: ArrayList(Int.MAX_VALUE) (OutOfMemoryError), ArrayList(-7) (IllegalArgumentException).
      assertThrows("conteggio $count", IOException::class.java) { FluidHealthCodec.decode(withCount(count)) }
    }
  }

  // --- 24 · il peso e il battito di un giorno sono l'ultima misura, non la media ----------------------------------

  @Test
  fun `il peso del giorno e' l'ultima pesata, mai la media delle pesate`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grantAll()
      put(
        FluidHealthPoint("w1", FluidHealthMetric.Weight, at(8), 72.9, scale),
        FluidHealthPoint("w2", FluidHealthMetric.Weight, at(20), 72.4, scale),
        heart("h#0", 0, 90.0), heart("h#1", 30, 60.0),
      )
    }
    val weight = gateway.day(FluidHealthMetric.Weight, day, zone, now) as FluidHealthReading.Value
    // Prima: 72,65 kg, la media delle due, che nessuna bilancia ha mai detto.
    assertEquals(72.4, weight.value, 0.0)
    assertEquals(listOf(scale), weight.sources)
    val beat = gateway.day(FluidHealthMetric.HeartRate, day, zone, now) as FluidHealthReading.Value
    assertEquals("l'ultimo battito, non 75 di media", 60.0, beat.value, 0.0)
    // I passi restano il totale del provider.
    gateway.put(steps("s1", 9, 1000), steps("s2", 11, 500))
    assertEquals(1500.0, (gateway.day(FluidHealthMetric.Steps, day, zone, now, gapRule = null) as FluidHealthReading.Value).value, 0.0)
  }

  // --- 25 · il battito corretto che ha meno campioni -------------------------------------------------------------

  @Test
  fun `un battito corretto con meno campioni toglie quelli in piu' dalla copia`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grant(FluidHealthPermission.read(FluidHealthMetric.HeartRate))
      put(*(0 until 10).map { heart("rec#$it", it, 60.0 + it) }.toTypedArray())
      put(heart("rec2#0", 40, 70.0), heart("rec2#1", 41, 71.0))
    }
    val store = FluidMemoryHealthStore()
    FluidHealthSync(gateway, store, setOf(FluidHealthMetric.HeartRate), clock = { now }).sync()
    assertEquals(12, store.load().samples.size)
    // Health Connect manda il record corretto intero: sei campioni.
    val corrected = (0 until 6).map { heart("rec#$it", it, 80.0 + it) }
    val custom = object : FluidHealthGateway by gateway {
      override suspend fun changes(token: String) = FluidHealthChanges(corrected, emptyList(), "x", false)
    }
    val result = FluidHealthSync(custom, store, setOf(FluidHealthMetric.HeartRate), clock = { now }).sync()
    assertEquals(FluidHealthSyncResult.Changes(6, 0, emptySet()), result)
    val ids = store.load().samples.map { it.id }
    assertEquals((0 until 6).map { "rec#$it" } + listOf("rec2#0", "rec2#1"), ids.sorted())
    assertFalse("rec#6..#9 non ci sono piu' in Health Connect", ids.any { it in setOf("rec#6", "rec#7", "rec#8", "rec#9") })
    assertTrue("l'altro record resta", ids.containsAll(listOf("rec2#0", "rec2#1")))
    assertEquals(85.0, (store.load().samples.first { it.id == "rec#5" } as FluidHealthPoint).value, 0.0)
  }
}
