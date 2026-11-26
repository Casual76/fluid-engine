package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.people.client.OutEnvelope
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.client.PeopleHttp
import dev.antigravity.fluidengine.people.crypto.Cdxb
import dev.antigravity.fluidengine.people.crypto.CdxbRecipient
import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.PeoplePrints
import dev.antigravity.fluidengine.people.crypto.SpaceRecordCipher
import dev.antigravity.fluidengine.people.spaces.SharedListItem
import dev.antigravity.fluidengine.people.spaces.SharedSpace
import dev.antigravity.fluidengine.people.spaces.SharedSpaces
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import dev.antigravity.fluidengine.sync.FluidSync
import dev.antigravity.fluidengine.sync.InMemorySyncStore
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 · I difetti delle persone della review R2 (`review\R2\muse-account.md`), uno per prova: ognuna è l'attacco o il caso
 * del rapporto (server ostile, busta malformata, ritorno indietro), che sulla base passava e ora è respinto.
 */
class PeopleR2Test {
  private fun text(t: String): JsonObject = buildJsonObject { put("t", "messaggio"); put("text", t) }

  /** Il server ostile (o un mittente qualsiasi) mette una busta nella casella di [to], col mittente [from]. */
  private fun inject(server: FakePeopleServer, to: BenchPerson, from: String, data: ByteArray, kind: String = "envelope") {
    val box = server.mailboxes[to.mailbox]!!
    box.envelopes += FakePeopleServer.Envelope(box.next++, kind, from, PampaBase64.encode(data), data.size, null, OutEnvelope.MIN_STORE, "normal", server.now)
  }

  private fun befriend(a: BenchPerson, b: BenchPerson) = runBlocking {
    a.client.request(a.client.lookup(b.code), "Ciao ${b.name}!")
    val req = b.client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    b.client.accept(req.request)
    a.client.pickup()
    for (d in b.devices + a.devices) d.client.pickup()
    for (d in a.devices + b.devices) d.client.pickup()
  }

  // --- Bloccante · una busta malformata blocca la casella per sempre -------------------------------------------------

  @Test
  fun `una busta malformata non ferma la casella - si scarta una volta e quelle dopo si aprono`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val estraneo = BenchPerson(server, "Estraneo", deviceCount = 1)
    val chiara = BenchPerson(server, "Chiara", deviceCount = 1)
    val me = alessio.client.identity()
    val x = estraneo.client.identity()
    fun fromStranger(p: JsonObject) = Cdxb.seal(x.work, x.mailbox, x.card, CdxbRecipient.of(me.card), p)
    val meMember = PeopleState.memberJson(GroupMember(me.card.name, me.code, me.mailbox, me.card.rootB64, PampaBase64.encode(me.card.work.enc), PampaBase64.encode(me.card.work.sign), 1, PeoplePrints.newToken()))
    // Una richiesta (le permette chiunque) con la chiave della conversazione che non è base64.
    inject(server, alessio, x.mailbox, fromStranger(buildJsonObject { put("t", "richiesta"); put("token", PeoplePrints.newToken()); put("chatId", "c"); put("chatKey", "!!! non è base64 !!!") }), kind = "request")
    // Lo stato di un gruppo coi membri che non sono una lista, e uno con le epoche delle chiavi che non sono numeri.
    inject(server, alessio, x.mailbox, fromStranger(buildJsonObject { put("t", "gruppo"); put("group", "g1"); put("epoch", 1); put("members", "nessuno"); putJsonObject("keys") {} }))
    inject(server, alessio, x.mailbox, fromStranger(buildJsonObject {
      put("t", "gruppo"); put("group", "g2"); put("epoch", 1)
      putJsonArray("members") { add(meMember) }
      putJsonObject("keys") { put("uno", PampaBase64.encode(PampaCrypto.random(32))) }
    }))
    // Una busta da me a me (un altro mio dispositivo) senza il contatto dentro.
    inject(server, alessio, me.mailbox, Cdxb.seal(me.work, me.mailbox, me.card, CdxbRecipient(me.mailbox, me.card.work.enc, me.card.work.epoch), buildJsonObject { put("t", "rubrica"); put("origin", "un-altro") }))
    // Dopo, una richiesta vera.
    chiara.client.request(chiara.client.lookup(alessio.code), "ciao")

    val events = alessio.client.pickup()
    assertEquals(4, events.count { it is PeopleEvent.Detected })
    assertEquals("Chiara", events.filterIsInstance<PeopleEvent.Request>().single().request.card.name)
    assertTrue("la casella non le ripresenta", alessio.client.pickup().isEmpty())
    assertTrue(alessio.client.state.groups.isEmpty())
  }

  // --- Bloccante · la scheda di un contatto può tornare indietro ------------------------------------------------------

  @Test
  fun `una busta vecchia di Marco rimandata dopo il suo rinnovo non rimette la scheda di prima`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    alessio.client.request(alessio.client.lookup(marco.code), "ciao")
    marco.client.accept(marco.client.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    // L'«accetta» di Marco, genuina, dell'epoca 1: il server se la tiene.
    val old = server.mailboxes[alessio.mailbox]!!.envelopes.last { it.from == marco.mailbox }
    alessio.client.pickup()
    marco.client.pickup()
    alessio.client.pickup()
    // Il telefono di Marco è rubato: rinnova le chiavi (epoca 2), e Alessio le riceve.
    marco.client.rotateWork()
    alessio.client.pickup()
    assertEquals(2, alessio.client.contact(marco.code)!!.card.work.epoch)
    // Il server rimanda la busta vecchia.
    inject(server, alessio, marco.mailbox, PampaBase64.decode(old.data))
    alessio.client.pickup()
    assertEquals("la scheda non torna all'epoca del telefono rubato", 2, alessio.client.contact(marco.code)!!.card.work.epoch)
    // E quello che Alessio gli manda adesso è per le chiavi nuove.
    alessio.client.sendCdxb(marco.code, buildJsonObject { put("t", "prova") })
    val sent = PampaBase64.decode(server.mailboxes[marco.mailbox]!!.envelopes.last { it.from == alessio.mailbox }.data)
    assertEquals(2, ((sent[8].toInt() and 0xFF) shl 24) or ((sent[9].toInt() and 0xFF) shl 16) or ((sent[10].toInt() and 0xFF) shl 8) or (sent[11].toInt() and 0xFF))
  }

  @Test
  fun `uno stato vecchio di uno spazio rimandato non rimette il gettone di prima ne chi era stato tolto`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val chiara = BenchPerson(server, "Chiara", deviceCount = 1)
    befriend(alessio, marco)
    befriend(alessio, chiara)
    val spaces = SharedSpaces(alessio.client)
    val m = spaces.create("lista")
    spaces.share(m.id, listOf(marco.code, chiara.code))
    val old = server.mailboxes[marco.mailbox]!!.envelopes.last { it.from == alessio.mailbox }
    marco.client.pickup()
    val oldToken = SharedSpaces(marco.client).membership(m.id)!!.writeToken
    // Chiara esce: epoca 2, gettone nuovo.
    spaces.removeMembers(m.id, listOf(chiara.code), keep = listOf(marco.code))
    marco.client.pickup()
    val fresh = SharedSpaces(marco.client).membership(m.id)!!
    assertEquals(2, fresh.epoch)
    assertNotEquals(oldToken, fresh.writeToken)
    // Il server rimanda lo stato dell'epoca 1.
    inject(server, marco, alessio.mailbox, PampaBase64.decode(old.data))
    marco.client.pickup()
    val after = SharedSpaces(marco.client).membership(m.id)!!
    assertEquals(fresh.writeToken, after.writeToken)
    assertFalse("chi è uscito non torna fra gli scriventi", chiara.code in after.writers)
    assertEquals(2, after.epoch)
  }

  @Test
  fun `uno stato vecchio di un gruppo rimandato non rimette dentro chi era uscito`() = runBlocking {
    val server = FakePeopleServer()
    val prof = BenchPerson(server, "Prof", deviceCount = 1)
    val anna = BenchPerson(server, "Anna", deviceCount = 1)
    val bruno = BenchPerson(server, "Bruno", deviceCount = 1)
    val g = prof.client.createGroup("gita")
    val qr = prof.client.groupInvite(g.id)
    anna.client.joinGroup(qr)
    bruno.client.joinGroup(qr)
    prof.client.approve(g.id, prof.client.pickup().filterIsInstance<PeopleEvent.Join>())
    val old = server.mailboxes[anna.mailbox]!!.envelopes.last { it.from == prof.mailbox }
    anna.client.pickup()
    prof.client.removeFromGroup(g.id, listOf(bruno.code))
    anna.client.pickup()
    assertEquals(2, anna.client.state.groups[g.id]!!.epoch)
    inject(server, anna, prof.mailbox, PampaBase64.decode(old.data))
    anna.client.pickup()
    val now = anna.client.state.groups[g.id]!!
    assertEquals(2, now.epoch)
    assertFalse(bruno.code in now.members)
    // La casella di Anna accetta ancora il gettone nuovo: il vecchio stato non l'ha tolto.
    prof.client.groupMessage(g.id, text("ci siete?"))
    assertEquals(1, anna.client.pickup().filterIsInstance<PeopleEvent.Message>().size)
  }

  // --- Maggiore · negli spazi, chi rinnova le chiavi diventa illeggibile -----------------------------------------------

  private class Member(val person: BenchPerson) {
    val spaces = SharedSpaces(person.client)
    val sync = FluidSync(InMemorySyncStore(), spaces.relay, spaces.sealer, node = person.name, wall = { person.server.now }, dispatcher = Dispatchers.Unconfined)
    fun list(id: String): SharedSpace<SharedListItem> = spaces.open(id, sync, SharedListItem.Codec)
  }

  @Test
  fun `chi rinnova le chiavi della persona resta leggibile nello spazio, anche per chi non e suo contatto`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = Member(BenchPerson(server, "Alessio", deviceCount = 1))
    val marco = Member(BenchPerson(server, "Marco", deviceCount = 1))
    val chiara = Member(BenchPerson(server, "Chiara", deviceCount = 1))
    befriend(alessio.person, marco.person)
    befriend(alessio.person, chiara.person)
    val m = alessio.spaces.create("lista")
    alessio.spaces.share(m.id, listOf(marco.person.code, chiara.person.code))
    marco.person.client.pickup()
    chiara.person.client.pickup()
    // Marco rinnova le chiavi di lavoro (un furto, o i sei mesi), poi scrive.
    marco.person.client.rotateWork()
    val listM = marco.list(m.id)
    val pane = listM.add(SharedListItem("pane"))
    listM.sync()
    // Chiara ha ancora la scheda vecchia di Marco fra gli scriventi, e non è sua contatto.
    val listC = chiara.list(m.id)
    listC.sync()
    assertNull(listC.sync.state.value.failure)
    assertEquals("pane", listC.snapshot()[pane]!!.text)
  }

  @Test
  fun `un record con la firma che non e base64 e un rifiuto pulito`() {
    val key = PampaCrypto.random(32)
    val nonce = PampaCrypto.random(12)
    val plain = """{"d":"eA","w":"PMP-AAAA-BBBB","e":1,"s":"!!! non è base64 !!!"}""".toByteArray()
    val ct = nonce + PampaCrypto.aesGcmSeal(key, nonce, plain, SpaceRecordCipher.aad("sp", 1, "r", 1))
    val e = runCatching { SpaceRecordCipher.open(key, "sp", 1, "r", 1, ct) }.exceptionOrNull()
    assertTrue("$e", e is PeopleDetected)
  }

  // --- Maggiore · persona e rubrica riprese dal server possono tornare indietro --------------------------------------

  @Test
  fun `una copia vecchia della persona non toglie le chiavi di lavoro nuove`() {
    val now = longArrayOf(1_764_000_000_000)
    val phone = PersonaStore(Files.createTempDirectory("p").toFile(), MemoryPersonVault(), clock = { now[0] })
    phone.create("Alessio")
    val k = PampaCrypto.random(32)
    val v1 = phone.exportSealed("acc_1", 1, k, 1)
    val tablet = PersonaStore(Files.createTempDirectory("t").toFile(), MemoryPersonVault(), clock = { now[0] })
    tablet.importSealed("acc_1", v1, 1) { k }
    now[0] += 60_000
    phone.rotateWork()
    tablet.importSealed("acc_1", phone.exportSealed("acc_1", 1, k, 2), 2) { k }
    assertEquals(2, tablet.identity()!!.card.work.epoch)
    // Il server rimanda la copia della versione 1.
    tablet.importSealed("acc_1", v1, 1) { k }
    assertEquals("la scheda resta quella nuova", 2, tablet.identity()!!.card.work.epoch)
    assertNotNull("la privata dell'epoca 2 resta", tablet.identity()!!.works[2])
  }

  @Test
  fun `una copia vecchia della rubrica non rimette un contatto tolto, e una piu vecchia dell'ultima e rifiutata`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    val kRubrica = PampaCrypto.random(32)
    val v1 = alessio.client.exportRubrica(alessio.account.id, 1, kRubrica, 1)
    server.now += 1_000
    alessio.client.remove(marco.code)
    val v2 = alessio.client.exportRubrica(alessio.account.id, 1, kRubrica, 2)
    // Il server rende allo stesso telefono la copia di prima.
    alessio.client.importRubrica(alessio.account.id, v1, 1) { kRubrica }
    assertNull("il contatto tolto non torna", alessio.client.contact(marco.code))
    // Un telefono nuovo riprende la 2, poi il server gli rimanda la 1.
    val fresh = BenchDevice(alessio, alessio.account.addDevice())
    fresh.persona.importSealed(alessio.account.id, alessio.first.persona.exportSealed(alessio.account.id, 1, alessio.kPersona, 3), 3) { alessio.kPersona }
    fresh.client.importRubrica(alessio.account.id, v2, 2) { kRubrica }
    assertNull(fresh.client.contact(marco.code))
    val replay = runCatching { fresh.client.importRubrica(alessio.account.id, v1, 1) { kRubrica } }.exceptionOrNull()
    assertTrue("$replay", replay is PampaCryptoException)
    assertNull(fresh.client.contact(marco.code))
  }

  // --- Maggiore · un contatto tolto torna dalla sincronizzazione coi miei dispositivi --------------------------------

  @Test
  fun `un contatto tolto dal telefono non torna con la rubrica vecchia del tablet`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 2)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    val phone = alessio.device(0)
    val tablet = alessio.device(1)
    // Il tablet verifica Marco: la sua rubrica parte alla prossima coda (è in tasca, senza rete).
    tablet.client.markWordsVerified(marco.code)
    server.now += 1_000
    // Dal telefono, Marco si toglie.
    phone.client.remove(marco.code)
    server.now += 1_000
    // Il tablet torna in rete: la sua busta vecchia arriva dopo la «via».
    tablet.client.flush()
    phone.client.pickup()
    tablet.client.pickup()
    assertNull("il telefono non lo rimette", phone.client.contact(marco.code))
    assertNull(tablet.client.contact(marco.code))
    // Aggiungerlo di nuovo dopo, invece, vale.
    server.now += 1_000
    phone.client.request(phone.client.lookup(marco.code), "di nuovo")
    tablet.client.pickup()
    assertNotNull(tablet.client.contact(marco.code))
  }

  // --- Maggiore · bloccare o togliere senza rete non blocca niente ---------------------------------------------------

  @Test
  fun `bloccare togliere e chiedere senza rete valgono subito, e il server lo sa quando torna`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val chiara = BenchPerson(server, "Chiara", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val luca = BenchPerson(server, "Luca", deviceCount = 1)
    befriend(alessio, marco)
    chiara.client.request(chiara.client.lookup(alessio.code), "ciao")
    val req = alessio.client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    val lucaCard = alessio.client.lookup(luca.code)
    val marcoToken = alessio.client.contact(marco.code)!!.myToken

    server.offline = true
    alessio.client.block(req.request.card.mailbox)
    alessio.client.remove(marco.code)
    alessio.client.request(lucaCard, "ciao Luca")
    assertTrue(chiara.mailbox in alessio.client.state.blocked)
    assertTrue(alessio.client.state.requests.isEmpty())
    assertNull(alessio.client.contact(marco.code))
    assertNotNull(alessio.client.contact(luca.code))

    server.offline = false
    alessio.client.flush()
    val box = server.mailboxes[alessio.mailbox]!!
    assertEquals("blocked", box.prints[PeoplePrints.blocked(chiara.mailbox)])
    assertNull("il gettone di Marco non vale più", box.prints[PeoplePrints.delivery(PampaBase64.decode(marcoToken))])
    // La richiesta a Luca parte, e il suo «sì» entra col gettone che la casella ora conosce.
    luca.client.accept(luca.client.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    assertTrue(alessio.client.pickup().any { it is PeopleEvent.Accepted && it.code == luca.code })
  }

  // --- Maggiore · un messaggio arrivato prima della sua chiave si perde ----------------------------------------------

  @Test
  fun `un messaggio arrivato prima della sua chiave si apre quando la chiave arriva`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    marco.client.rotateWork()
    marco.client.sendMessage(alessio.code, text("dopo il rinnovo"))
    // La «chiave-chat» resta indietro (la coda di un altro dispositivo, un server lento): arriva prima il messaggio.
    val box = server.mailboxes[alessio.mailbox]!!
    val key = box.envelopes.first { it.from == marco.mailbox && Cdxb.isCdxb(PampaBase64.decode(it.data)) }
    box.envelopes.remove(key)
    assertTrue(alessio.client.pickup().none { it is PeopleEvent.Message })
    box.envelopes += FakePeopleServer.Envelope(box.next++, key.kind, key.from, key.data, key.size, key.via, key.minStore, key.priority, key.at)
    val events = alessio.client.pickup()
    assertEquals("dopo il rinnovo", events.filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)
    assertTrue("una volta sola", alessio.client.pickup().none { it is PeopleEvent.Message })
  }

  // --- Varie · la coda fermata a metà (WorkManager sostituisce il lavoro) rimandava i doppioni ---------------------

  @Test
  fun `una coda fermata a meta non rimanda le buste gia partite`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val chiara = BenchPerson(server, "Chiara", deviceCount = 1)
    befriend(alessio, marco)
    befriend(alessio, chiara)
    server.offline = true
    alessio.client.sendMessage(marco.code, text("a Marco"))
    alessio.client.sendMessage(chiara.code, text("a Chiara"))
    server.offline = false
    // Il lavoro in sottofondo parte; dopo la prima consegna arriva una richiesta nuova e WorkManager lo ferma.
    var job: Job? = null
    val http = PeopleHttp { r ->
      currentCoroutineContext().ensureActive()
      val response = server.http.execute(r)
      if (r.method == "POST" && r.path.endsWith("/buste")) job?.cancel()
      response
    }
    val d = alessio.first
    val stopping = PeopleClient(d.persona, d.stateStore, PeopleApi(http, d.device.auth), clock = { server.now })
    job = launch { stopping.flush() }
    job!!.join()
    // Il campanello ritira (o il processo riparte): lo stato si rilegge dal disco, poi parte il lavoro nuovo.
    d.stateStore.reload()
    alessio.client.flush()
    val sent = listOf(marco, chiara).sumOf { p -> server.mailboxes[p.mailbox]!!.envelopes.count { it.from == alessio.mailbox } }
    assertEquals("una busta per uno, senza doppioni", 2, sent)
  }

  // --- Minore · ri-aggiungere un contatto che c'è già lo sostituisce in silenzio ------------------------------------

  @Test
  fun `chiedere di nuovo a un contatto che c'e gia non lo sostituisce`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    alessio.client.markWordsVerified(marco.code)
    val before = alessio.client.contact(marco.code)!!
    alessio.client.request(alessio.client.lookup(marco.code), "di nuovo")
    val after = alessio.client.contact(marco.code)!!
    assertEquals(before.chatId, after.chatId)
    assertEquals(before.myToken, after.myToken)
    assertEquals(before.theirToken, after.theirToken)
    assertNotNull("la verifica resta", after.verification)
    assertTrue(alessio.client.isVerified(marco.code))
  }
}
