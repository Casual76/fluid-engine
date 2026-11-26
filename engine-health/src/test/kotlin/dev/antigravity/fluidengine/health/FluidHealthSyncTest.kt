package dev.antigravity.fluidengine.health

import dev.antigravity.fluidengine.vault.FluidVault
import dev.antigravity.fluidengine.vault.MemoryVaultKeystore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Duration
import java.time.Instant

/**
 * F8.26 · La sincronizzazione per differenze (la prima volta tutto, poi solo le modifiche; un segno scaduto o un permesso
 * tolto rifanno tutto), i permessi negati e parziali, e la copia cifrata nella cassaforte, che sul disco non si legge.
 */
class FluidHealthSyncTest {
  @get:Rule val tmp = TemporaryFolder()

  private val now = Instant.parse("2026-11-20T18:00:00Z")
  private val watch = FluidHealthSource("com.example.watch", "Orologio", FluidHealthDeviceKind.Watch, "Pixel Watch")
  private val sounds = FluidHealthSource("dev.pampa.sounds", "Pampa Sounds", FluidHealthDeviceKind.Phone)

  private fun steps(id: String, hour: Long, count: Int) =
    FluidHealthInterval(id, FluidHealthMetric.Steps, now.minus(Duration.ofHours(hour)), now.minus(Duration.ofHours(hour - 1)), count.toDouble(), watch)

  private fun heart(id: String, minutesAgo: Long, bpm: Double) =
    FluidHealthPoint(id, FluidHealthMetric.HeartRate, now.minus(Duration.ofMinutes(minutesAgo)), bpm, watch)

  private val metrics = setOf(FluidHealthMetric.Steps, FluidHealthMetric.HeartRate, FluidHealthMetric.Sleep)

  @Test
  fun `la prima volta legge tutto, poi solo le modifiche`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grant(FluidHealthPermission.read(FluidHealthMetric.Steps), FluidHealthPermission.read(FluidHealthMetric.HeartRate))
      put(steps("a", 5, 1200), steps("b", 4, 900), heart("rec1#0", 30, 61.0), heart("rec1#1", 29, 63.0))
    }
    val store = FluidMemoryHealthStore()
    val sync = FluidHealthSync(gateway, store, metrics, clock = { now })
    val first = sync.sync()
    assertTrue("$first", first is FluidHealthSyncResult.Full)
    assertEquals(4, (first as FluidHealthSyncResult.Full).count)
    assertEquals(setOf(FluidHealthMetric.Sleep), first.denied) // il sonno non e' consentito: parziale
    val reads = gateway.reads

    gateway.put(steps("c", 2, 400), steps("a", 5, 1250)) // uno nuovo, uno cambiato
    val second = sync.sync()
    assertEquals(FluidHealthSyncResult.Changes(upserted = 2, deleted = 0, denied = setOf(FluidHealthMetric.Sleep)), second)
    assertEquals("nessuna lettura intera in piu'", reads, gateway.reads)
    val copy = store.load()
    assertEquals(5, copy.samples.size)
    assertEquals(1250.0, (copy.samples.first { it.id == "a" } as FluidHealthInterval).value, 0.0)

    // Un battito tolto in Health Connect toglie tutti i suoi campioni («record#indice»).
    gateway.remove("rec1")
    gateway.remove("b")
    val third = sync.sync() as FluidHealthSyncResult.Changes
    assertEquals(1, third.deleted) // "rec1" non c'era come record intero nella memoria finta: conta il "b"
    assertFalse(store.load().samples.any { it.id == "b" })
  }

  @Test
  fun `un record di battito tolto toglie i suoi campioni dalla copia`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grant(FluidHealthPermission.read(FluidHealthMetric.HeartRate))
      put(heart("rec9#0", 10, 60.0), heart("rec9#1", 9, 62.0), heart("altro#0", 8, 70.0))
    }
    val store = FluidMemoryHealthStore()
    val sync = FluidHealthSync(gateway, store, setOf(FluidHealthMetric.HeartRate), clock = { now })
    sync.sync()
    // Health Connect dice l'id del record, non dei campioni.
    gateway.put() // niente
    val changes = FluidHealthChanges(emptyList(), listOf("rec9"), "x", false)
    // Si applica a mano come farebbe sync(): passa dal gateway con un segno finto.
    val custom = object : FluidHealthGateway by gateway {
      override suspend fun changes(token: String) = changes
    }
    val result = FluidHealthSync(custom, store, setOf(FluidHealthMetric.HeartRate), clock = { now }).sync()
    assertEquals(FluidHealthSyncResult.Changes(0, 1, emptySet()), result)
    assertEquals(listOf("altro#0"), store.load().samples.map { it.id })
  }

  @Test
  fun `un segno scaduto rifa' la lettura intera`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grant(FluidHealthPermission.read(FluidHealthMetric.Steps))
      put(steps("a", 3, 100))
    }
    val sync = FluidHealthSync(gateway, FluidMemoryHealthStore(), setOf(FluidHealthMetric.Steps), clock = { now })
    sync.sync()
    gateway.expireTokens()
    gateway.put(steps("b", 2, 50))
    val result = sync.sync()
    assertEquals(FluidHealthSyncResult.Full(2, emptySet()), result)
  }

  @Test
  fun `un permesso tolto toglie anche la copia di quella misura, e senza permessi la copia si svuota`() = runBlocking {
    val gateway = FluidMemoryHealthGateway().apply {
      grant(FluidHealthPermission.read(FluidHealthMetric.Steps), FluidHealthPermission.read(FluidHealthMetric.HeartRate))
      put(steps("a", 3, 100), heart("h#0", 5, 66.0))
    }
    val store = FluidMemoryHealthStore()
    val sync = FluidHealthSync(gateway, store, metrics, clock = { now })
    sync.sync()
    assertEquals(2, store.load().samples.size)
    gateway.revoke(FluidHealthPermission.read(FluidHealthMetric.HeartRate))
    val partial = sync.sync()
    assertEquals(FluidHealthSyncResult.Full(1, setOf(FluidHealthMetric.HeartRate, FluidHealthMetric.Sleep)), partial)
    assertEquals(listOf("a"), store.load().samples.map { it.id })
    gateway.revoke(FluidHealthPermission.read(FluidHealthMetric.Steps))
    assertEquals(FluidHealthSyncResult.NoPermission, sync.sync())
    assertTrue(store.load().samples.isEmpty())
  }

  @Test
  fun `Health Connect assente o da aggiornare non tocca la copia`() = runBlocking {
    val gateway = FluidMemoryHealthGateway(FluidHealthAvailability.NeedsUpdate)
    val result = FluidHealthSync(gateway, FluidMemoryHealthStore(), metrics, clock = { now }).sync()
    assertEquals(FluidHealthSyncResult.Unavailable(FluidHealthAvailability.NeedsUpdate), result)
  }

  @Test
  fun `leggere senza permesso e' un errore, scrivere da' la fonte di questa app`() = runBlocking {
    val gateway = FluidMemoryHealthGateway()
    try {
      gateway.read(FluidHealthMetric.Steps, now.minusSeconds(3600), now)
      fail("doveva dire permesso negato")
    } catch (_: SecurityException) {
    }
    gateway.grant(FluidHealthPermission.write(FluidHealthMetric.Sleep))
    val id = gateway.writeSleep(now.minus(Duration.ofHours(8)), now, title = "Timer della notte")
    val written = gateway.all().single { it.id == id } as FluidHealthSession
    assertEquals("Timer della notte", written.title)
    assertEquals("dev.antigravity.fluidglass", written.source.packageName)
  }

  @Test
  fun `la copia nella cassaforte e' cifrata sul disco e torna intera`() = runBlocking {
    val vault = FluidVault.create(tmp.newFolder("cassaforte"), MemoryVaultKeystore(), clock = { 0L }, io = Dispatchers.IO)
    val store = FluidVaultHealthStore(vault.section("salute"))
    val stages = listOf(FluidSleepStage(now.minus(Duration.ofHours(7)), now.minus(Duration.ofHours(6)), 5))
    val snapshot = FluidHealthSnapshot(
      samples = listOf(
        steps("passi-segreti", 3, 4321),
        heart("battito#0", 3, 61.5),
        FluidHealthSession("notte", FluidHealthMetric.Sleep, now.minus(Duration.ofHours(8)), now, sounds, "Timer della notte", stages = stages),
      ),
      token = "segno-7", metrics = metrics, syncedAt = now,
    )
    store.save(snapshot)
    val raw = tmp.root.walkTopDown().filter { it.isFile }.map { it.readBytes() }.toList()
    assertTrue(raw.isNotEmpty())
    for (bytes in raw) {
      val text = String(bytes, Charsets.ISO_8859_1)
      for (secret in listOf("passi-segreti", "Pampa Sounds", "dev.pampa.sounds", "Timer della notte", "segno-7", "Pixel Watch")) {
        assertFalse("«$secret» in chiaro sul disco", text.contains(secret))
      }
    }
    val back = store.load()
    assertEquals(FluidHealthCodec.encode(snapshot).toList(), FluidHealthCodec.encode(back).toList())
    assertEquals("Timer della notte", (back.samples[2] as FluidHealthSession).title)
    assertEquals(stages, (back.samples[2] as FluidHealthSession).stages)
    // Un'altra sezione non la apre.
    val other = FluidVaultHealthStore(vault.section("altra"))
    other.save(FluidHealthSnapshot.Empty)
    val stolen = vault.section("altra").file("salute.bin").file
    store.load() // la propria si apre ancora
    stolen.writeBytes(vault.section("salute").file("salute.bin").file.readBytes())
    try {
      other.load()
      fail("una sezione non deve aprire la copia di un'altra")
    } catch (_: Exception) {
    }
  }

  @Test
  fun `il formato della copia torna uguale e rifiuta un file che non e' suo`() {
    val snapshot = FluidHealthSnapshot(listOf(steps("a", 2, 10)), null, setOf(FluidHealthMetric.Steps), null)
    val back = FluidHealthCodec.decode(FluidHealthCodec.encode(snapshot))
    assertEquals(snapshot.samples, back.samples)
    assertEquals(null, back.token)
    try {
      FluidHealthCodec.decode(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
      fail()
    } catch (_: java.io.IOException) {
    }
  }
}
