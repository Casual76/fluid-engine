package dev.antigravity.fluidengine.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10.8 · Il registro salvato tutto intero come un blob, che chi chiama chiude da sé (i luoghi di Pampa Store). */
class BlobSyncStoreTest {

  private class Disk {
    var blob: ByteArray? = null
    var saves = 0
    var failNext = false
    val store get() = BlobSyncStore(load = { blob }, save = {
      if (failNext) {
        failNext = false
        throw IllegalStateException("disco pieno")
      }
      saves++
      blob = it
    })
  }

  private fun engine(store: SyncStore, relay: SyncRelay) = FluidSync(store, relay, AesTestSealer(), "dispositivo_a")

  @Test
  fun `quello che si scrive torna uguale a un avvio dopo, con le modifiche in attesa e l'orologio`() = runBlocking {
    val disk = Disk()
    val relay = MemorySyncRelay()
    val first = engine(disk.store, relay)
    val space = first.space(SyncSpaceId("luoghi"))
    val id = space.create { text("name", "Casa"); real("lat", 43.77) }
    space.edit(id) { text("address", "Via Roma 1") }
    assertTrue(disk.saves > 0)
    val before = disk.blob!!.copyOf()

    val second = engine(disk.store, relay)
    val again = second.space(SyncSpaceId("luoghi"))
    again.load()
    val doc = again.state.value.records.getValue(id)
    assertEquals("Casa", doc.text("name"))
    assertEquals(43.77, doc.real("lat")!!, 0.0)
    assertEquals("Via Roma 1", doc.text("address"))
    assertEquals(1, again.state.value.pending)
    // La sincronizzazione parte da lì: le modifiche in attesa arrivano al relay.
    assertEquals(1, again.sync().pushed)
    assertEquals(0, again.state.value.pending)
    // Il formato si rilegge e si riscrive uguale.
    assertArrayEquals(before, BlobSyncStore.encode(BlobSyncStore.decode(before)))
  }

  @Test
  fun `se il salvataggio cade, la transazione e annullata anche in memoria`() = runBlocking {
    val disk = Disk()
    val sync = engine(disk.store, MemorySyncRelay())
    val space = sync.space(SyncSpaceId("luoghi"))
    val id = space.create { text("name", "Casa") }
    disk.failNext = true
    val failed = runCatching { space.edit(id) { text("name", "Casa nuova") } }
    assertTrue(failed.isFailure)
    // Il registro sul disco è quello di prima; un avvio nuovo non vede la scrittura persa.
    val reopened = engine(disk.store, MemorySyncRelay()).space(SyncSpaceId("luoghi"))
    reopened.load()
    assertEquals("Casa", reopened.state.value.records.getValue(id).text("name"))
  }

  @Test
  fun `un blob vuoto o di un altro formato`() {
    assertNull(Disk().blob)
    val d = BlobSyncStore.decode(BlobSyncStore.encode(InMemorySyncStore.Data()))
    assertTrue(d.records.isEmpty() && d.journal.isEmpty() && d.meta.isEmpty())
    assertTrue(runCatching { BlobSyncStore.decode(byteArrayOf(9, 0, 0, 0)) }.exceptionOrNull() is SyncFormatException)
  }
}
