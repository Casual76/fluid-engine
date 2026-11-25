package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.people.spaces.SharedListItem
import dev.antigravity.fluidengine.people.spaces.SharedSpace
import dev.antigravity.fluidengine.people.spaces.SharedSpaces
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.InMemorySyncStore
import dev.antigravity.fluidengine.sync.SyncFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gli spazi condivisi sopra `engine-sync` (`d\server-e-chiavi.md` § 11): una lista fra tre persone, col server ostile. */
class SharedSpaceTest {
  private class Member(val person: BenchPerson) {
    val spaces = SharedSpaces(person.client)
    val sync = FluidSync(InMemorySyncStore(), spaces.relay, spaces.sealer, node = person.name, wall = { person.server.now }, dispatcher = Dispatchers.Unconfined)
    fun list(id: String): SharedSpace<SharedListItem> = spaces.open(id, sync, SharedListItem.Codec)
  }

  private fun befriend(a: BenchPerson, b: BenchPerson) = runBlocking {
    a.client.request(a.client.lookup(b.code), "ciao")
    b.client.accept(b.client.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    a.client.pickup()
    b.client.pickup()
  }

  @Test
  fun `una lista fra tre persone - chi aggiunge firma, chi vede soltanto non scrive, chi esce perde il futuro`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = Member(BenchPerson(server, "Alessio", deviceCount = 1))
    val marco = Member(BenchPerson(server, "Marco", deviceCount = 1))
    val mamma = Member(BenchPerson(server, "Mamma", deviceCount = 1))
    befriend(alessio.person, marco.person)
    befriend(alessio.person, mamma.person)

    val m = alessio.spaces.create("lista")
    alessio.spaces.share(m.id, listOf(marco.person.code, mamma.person.code), readOnly = setOf(mamma.person.code))
    assertTrue(marco.person.client.pickup().any { it is PeopleEvent.SpaceKeys && it.space == m.id })
    assertTrue(mamma.person.client.pickup().any { it is PeopleEvent.SpaceKeys })
    assertTrue(marco.spaces.membership(m.id)!!.canWrite)
    assertFalse(mamma.spaces.membership(m.id)!!.canWrite)

    val listA = alessio.list(m.id)
    val listM = marco.list(m.id)
    val listMamma = mamma.list(m.id)
    val latte = listA.add(SharedListItem("latte"))
    listA.sync()
    listM.sync()
    assertEquals("latte", listM.snapshot()[latte]!!.text)
    // Marco aggiunge e spunta; la firma dentro il record dice che è lui.
    val pane = listM.add(SharedListItem("pane", assignee = "@mamma"))
    listM.put(latte, SharedListItem("latte", done = true))
    listM.sync()
    listA.sync()
    listMamma.sync()
    assertEquals(true, listA.snapshot()[latte]!!.done)
    assertEquals("@mamma", listMamma.snapshot()[pane]!!.assignee)
    // Chi vede soltanto non scrive: lo spazio lo rifiuta sul telefono, e il relay non riceverebbe niente comunque.
    assertTrue(runCatching { listMamma.add(SharedListItem("cioccolata")) }.exceptionOrNull() is IllegalStateException)
    try {
      mamma.spaces.relay.push(dev.antigravity.fluidengine.sync.SyncSpaceId(m.id), dev.antigravity.fluidengine.sync.SyncPushBatch("k", emptyList()))
      org.junit.Assert.fail("senza gettone il relay non manda")
    } catch (e: dev.antigravity.fluidengine.sync.SyncRelayException) {
      assertTrue(e.failure is SyncFailure.Rejected)
    }
    listA.sync()
    assertTrue(listA.snapshot().values.none { it.text == "cioccolata" })

    // Togliere Marco: epoca nuova, chiave e gettone nuovi solo a chi resta.
    alessio.spaces.removeMembers(m.id, listOf(marco.person.code), keep = listOf(mamma.person.code))
    mamma.person.client.pickup()
    assertEquals(2, mamma.spaces.membership(m.id)!!.epoch)
    listA.add(SharedListItem("uova"))
    listA.sync()
    listMamma.sync()
    assertTrue(listMamma.snapshot().values.any { it.text == "uova" })
    // Marco ha il passato e non il futuro: non apre l'epoca 2 e non scrive più.
    listM.sync()
    assertTrue(listM.snapshot().values.none { it.text == "uova" })
    assertEquals("pane", listM.snapshot()[pane]!!.text)
    assertNull(marco.spaces.membership(m.id)!!.keys[2])
    listM.add(SharedListItem("birra"))
    listM.sync()
    assertTrue(listM.sync.state.value.failure is SyncFailure.Rejected)
  }

  @Test
  fun `il server ostile - un record vecchio rimandato, rinominato o di un altro spazio non passa`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = Member(BenchPerson(server, "Alessio", deviceCount = 1))
    val marco = Member(BenchPerson(server, "Marco", deviceCount = 1))
    befriend(alessio.person, marco.person)
    val m = alessio.spaces.create("lista")
    val other = alessio.spaces.create("lista")
    alessio.spaces.share(m.id, listOf(marco.person.code))
    alessio.spaces.share(other.id, listOf(marco.person.code))
    marco.person.client.pickup()
    val list = alessio.list(m.id)
    val id = list.add(SharedListItem("latte"))
    list.sync()
    val old = server.spaces[m.id]!!.records[id]!!.let { FakePeopleServer.SpaceRecord(it.recordId, it.version, it.epoch, it.ciphertext, it.deleted, it.seq) }
    list.put(id, SharedListItem("latte di soia"))
    list.sync()
    val listM = marco.list(m.id)
    listM.sync()
    assertEquals("latte di soia", listM.snapshot()[id]!!.text)

    // Il server rimanda il record vecchio con la sua versione vecchia, come nuovo.
    list.put(id, SharedListItem("latte intero"))
    list.sync()
    server.hostile.tamperSpace = { _, rows -> rows + FakePeopleServer.SpaceRecord(old.recordId, old.version, old.epoch, old.ciphertext, false, 999) }
    listM.sync()
    assertNotNull("il telefono lo dice", listM.sync.state.value.failure)
    assertEquals("il vecchio non torna", "latte intero", listM.snapshot()[id]!!.text)
    // Rinominato (lo stesso contenuto sotto un altro id) o preso da un altro spazio: non si apre.
    server.hostile.tamperSpace = { space, rows -> if (space == other.id) listOf(FakePeopleServer.SpaceRecord("rubato", old.version, old.epoch, old.ciphertext, false, 1)) else rows }
    val listOther = marco.list(other.id)
    listOther.sync()
    assertTrue(listOther.snapshot().isEmpty())
    assertNotNull(listOther.sync.state.value.failure)
    assertTrue(PampaBase64.decode(old.ciphertext).isNotEmpty())
  }
}
