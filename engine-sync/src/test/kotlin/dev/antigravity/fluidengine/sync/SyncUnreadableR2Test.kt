package dev.antigravity.fluidengine.sync

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 · «Una riga illeggibile ferma la sincronizzazione di uno spazio per sempre» (`review\R2\muse-account.md`): una riga
 * che questo telefono non sa aprire (la chiave di un'epoca che non ha ancora, un engine più nuovo, un contenuto rovinato)
 * fermava il ritiro a quella riga e non faceva partire l'invio, a ogni giro. Ora ferma lo spazio per qualche giro, poi
 * resta da parte, e si applica quando si apre.
 */
class SyncUnreadableR2Test {
  private val world = BenchWorld()

  @After fun close() = world.close()

  /** Un sigillo che non sa aprire le righe di [blocked] finché [unlocked] è falso: la chiave che arriva dopo. */
  private class LateKeySealer(private val inner: SyncSealer, private val blocked: Set<String>) : SyncSealer {
    @Volatile var unlocked = false

    override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload = inner.seal(context, plaintext)

    override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray {
      if (!unlocked && context.recordId in blocked) throw SyncSealException("record di un'epoca che non ho")
      return inner.open(context, payload)
    }
  }

  @Test fun `una riga che non si apre ferma lo spazio solo per qualche giro, poi le altre passano e si manda`() {
    val phone = world.device("telefono")
    val a = phone.create { text("voce", "Latte") }
    val b = phone.create { text("voce", "Pane") }
    val c = phone.create { text("voce", "Uova") }
    phone.syncNow()
    // Il tablet non sa aprire la riga di mezzo (la sua chiave arriverà dopo).
    val sealer = LateKeySealer(world.sealer, setOf(b))
    val tablet = FluidSync(InMemorySyncStore(), world.relay, sealer, "tablet", wall = { world.time })
    val space = tablet.space(SyncSpaceId("spesa"))
    runBlocking { space.create { text("voce", "Mele") } }
    val first = runBlocking { space.sync() }
    assertEquals("unreadable", (first.failure as SyncFailure.Rejected).code)
    var report = first
    repeat(SyncConfig().unreadableRetries) { report = runBlocking { space.sync() } }
    assertNull("dopo qualche giro lo spazio va avanti: ${report.failure}", report.failure)
    val records = space.state.value.records
    assertEquals("Latte", records[a]?.text("voce"))
    assertEquals("Uova", records[c]?.text("voce"))
    assertEquals("la modifica del tablet è partita", 0, space.state.value.pending)
    assertTrue(world.relay.rows(SyncSpaceId("spesa")).size == 4)
    assertEquals(listOf(b), space.state.value.unreadable)
    assertEquals(listOf(b), report.unreadable)

    // Arriva la chiave: la riga messa da parte si apre e si applica, e non è più fra le illeggibili.
    sealer.unlocked = true
    val after = runBlocking { space.sync() }
    assertNull(after.failure)
    assertEquals("Pane", space.state.value.records[b]?.text("voce"))
    assertTrue(space.state.value.unreadable.isEmpty())
  }

  @Test fun `una riga messa da parte resta da parte anche dopo un riavvio, e una versione nuova la supera`() {
    val phone = world.device("telefono")
    val b = phone.create { text("voce", "Pane") }
    phone.syncNow()
    val store = InMemorySyncStore()
    val sealer = LateKeySealer(world.sealer, setOf(b))
    var tablet = FluidSync(store, world.relay, sealer, "tablet", wall = { world.time })
    repeat(SyncConfig().unreadableRetries + 1) { runBlocking { tablet.space(SyncSpaceId("spesa")).sync() } }
    assertEquals(listOf(b), tablet.space(SyncSpaceId("spesa")).state.value.unreadable)
    // Il processo riparte: la riga è ancora da parte, e il giro non si ferma.
    tablet = FluidSync(store, world.relay, sealer, "tablet", wall = { world.time })
    val space = tablet.space(SyncSpaceId("spesa"))
    runBlocking { space.load() }
    assertEquals(listOf(b), space.state.value.unreadable)
    assertNull(runBlocking { space.sync() }.failure)
    // Il telefono la cambia; la chiave arriva; vale la versione nuova, e la vecchia se ne va.
    world.advance(60_000)
    phone.edit(b) { text("voce", "Pane integrale") }
    phone.syncNow()
    sealer.unlocked = true
    assertNull(runBlocking { space.sync() }.failure)
    assertEquals("Pane integrale", space.state.value.records[b]?.text("voce"))
    assertTrue(space.state.value.unreadable.isEmpty())
    assertNotNull(store.transaction { meta("spesa", SyncSpace.MetaUnreadable) })
  }
}
