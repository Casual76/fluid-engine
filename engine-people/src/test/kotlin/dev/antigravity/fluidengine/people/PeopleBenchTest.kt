package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.people.client.EnvelopePriority
import dev.antigravity.fluidengine.people.client.PeopleServerException
import dev.antigravity.fluidengine.people.crypto.Cdxb
import dev.antigravity.fluidengine.people.crypto.PeopleDetected
import dev.antigravity.fluidengine.people.crypto.Pmx1
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Il banco delle persone sul telefono (`d\server-e-chiavi.md` § 13; `fasi.md` F10.6): dispositivi finti in JVM contro
 * il server finto, che ha le regole del Worker vero.
 */
class PeopleBenchTest {
  private fun text(t: String): JsonObject = buildJsonObject { put("t", "messaggio"); put("text", t) }

  /** A chiede a B col codice, B accetta dal primo dispositivo, e tutti ritirano finché le parole sono pronte. */
  private fun befriend(a: BenchPerson, b: BenchPerson) = runBlocking {
    val card = a.client.lookup(b.code)
    a.client.request(card, "Ciao ${b.name}!")
    val req = b.client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    b.client.accept(req.request)
    a.client.pickup()
    for (d in b.devices + a.devices) d.client.pickup()
    for (d in a.devices + b.devices) d.client.pickup()
  }

  @Test
  fun `tre persone con due dispositivi - richiesta, accettazione, parole con l'impegno, messaggi su tutti e due`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco")
    val chiara = BenchPerson(server, "Chiara")

    // Col codice: la scheda si controlla sul telefono e resta «da verificare».
    val card = alessio.client.lookup(marco.code)
    assertEquals(marco.code, card.code)
    alessio.client.request(card, "Ciao Marco!")
    assertNull("prima del sì non si scrive", alessio.client.contact(marco.code)!!.theirToken)
    assertNull("le parole aspettano l'impegno", alessio.client.words(marco.code))

    // I due dispositivi di Marco vedono la richiesta; accetta il primo.
    val onM1 = marco.device(0).client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    assertEquals("Ciao Marco!", onM1.request.greeting)
    assertEquals(alessio.code, onM1.request.card.code)
    assertEquals(1, marco.device(1).client.pickup().filterIsInstance<PeopleEvent.Request>().size)
    marco.device(0).client.accept(onM1.request)

    // Alessio riceve il sì col nonce di Marco, e rivela il suo.
    val accepted = alessio.client.pickup()
    assertTrue(accepted.any { it is PeopleEvent.Accepted && it.code == marco.code })
    assertTrue(accepted.any { it is PeopleEvent.WordsReady })
    val wordsA = alessio.client.words(marco.code)!!.words

    // Marco: il primo dispositivo controlla l'impegno; il secondo ha la rubrica dal primo e apre lo stesso.
    assertTrue(marco.device(0).client.pickup().any { it is PeopleEvent.WordsReady })
    val m2 = marco.device(1).client.pickup()
    assertTrue(m2.any { it is PeopleEvent.RubricaSynced })
    assertEquals(wordsA, marco.device(0).client.words(alessio.code)!!.words)
    assertEquals(wordsA, marco.device(1).client.words(alessio.code)!!.words)
    // Anche il secondo dispositivo di Alessio, dalla rubrica del primo.
    alessio.device(1).client.pickup()
    assertEquals(wordsA, alessio.device(1).client.words(marco.code)!!.words)
    assertEquals(4, wordsA.size)
    assertFalse("a distanza resta da verificare", alessio.client.isVerified(marco.code))
    alessio.client.markWordsVerified(marco.code)
    assertTrue(alessio.client.isVerified(marco.code))

    // Un messaggio arriva a tutti e due i dispositivi di Marco, una volta sola.
    alessio.client.sendMessage(marco.code, text("ci vediamo alle sei"))
    for (d in marco.devices) {
      val msg = d.client.pickup().filterIsInstance<PeopleEvent.Message>().single()
      assertEquals("ci vediamo alle sei", msg.payload["text"]!!.jsonPrimitive.content)
      assertEquals(alessio.code, msg.code)
      assertTrue(d.client.pickup().none { it is PeopleEvent.Message })
    }
    // Quando tutti e due hanno ritirato, la casella non tiene più niente.
    assertTrue(server.mailboxes[marco.mailbox]!!.envelopes.isEmpty())

    // Chiara chiede ad Alessio; Alessio blocca. Le sue richieste e buste spariscono in silenzio.
    chiara.client.request(chiara.client.lookup(alessio.code), "Ciao!")
    val fromChiara = alessio.client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    alessio.client.block(fromChiara.request.card.mailbox)
    chiara.client.request(chiara.client.lookup(alessio.code), "Ancora io")
    assertTrue("a chi è bloccato il server risponde come a tutti", chiara.client.flush().undeliverable.isEmpty())
    assertTrue(alessio.client.pickup().none { it is PeopleEvent.Request })

    // Alessio toglie Marco: le buste di Marco vengono scartate senza dirlo.
    alessio.client.remove(marco.code)
    marco.client.sendMessage(alessio.code, text("ci sei?"))
    assertTrue(alessio.client.pickup().none { it is PeopleEvent.Message })
    alessio.device(1).client.pickup()
    assertNull("anche l'altro dispositivo lo dimentica", alessio.device(1).client.contact(marco.code))
  }

  @Test
  fun `un telefono nuovo ritrova persona e rubrica dalle copie sotto K_com, e scrive subito`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    val kRubrica = dev.antigravity.fluidengine.account.crypto.PampaCrypto.random(32)
    val persona = alessio.first.persona.exportSealed(alessio.account.id, 1, alessio.kPersona, 2)
    val rubrica = alessio.client.exportRubrica(alessio.account.id, 1, kRubrica, 1)
    // Il telefono nuovo entra nell'account: niente chiavi copiate a mano, solo le due copie e le chiavi comuni.
    val fresh = BenchDevice(alessio, alessio.account.addDevice())
    fresh.persona.importSealed(alessio.account.id, persona, 2) { alessio.kPersona }
    fresh.client.importRubrica(alessio.account.id, rubrica, 1) { kRubrica }
    assertEquals(alessio.client.words(marco.code)!!.words, fresh.client.words(marco.code)!!.words)
    fresh.client.sendMessage(marco.code, text("dal telefono nuovo"))
    assertEquals("dal telefono nuovo", marco.client.pickup().filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)
    // Spostata a un'altra versione non si apre.
    assertTrue(runCatching { fresh.client.importRubrica(alessio.account.id, rubrica, 2) { kRubrica } }.isFailure)
  }

  @Test
  fun `i tuoi dispositivi - una pagina mandata al tablet arriva solo ai tuoi`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco")
    befriend(alessio, marco)
    alessio.device(0).client.sendToMyDevices(buildJsonObject { put("address", "https://pampa-apps.com/a/transit/fermata/123") })
    val onTablet = alessio.device(1).client.pickup().filterIsInstance<PeopleEvent.FromMyDevices>().single()
    assertEquals("https://pampa-apps.com/a/transit/fermata/123", onTablet.payload["address"]!!.jsonPrimitive.content)
    assertTrue("chi l'ha mandata non la riapre", alessio.device(0).client.pickup().none { it is PeopleEvent.FromMyDevices })
    assertTrue(marco.client.pickup().none { it is PeopleEvent.FromMyDevices })
  }

  @Test
  fun `il QR verifica tutti e due subito, e la prova lega chi l'ha inquadrato`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val mamma = BenchPerson(server, "Mamma", deviceCount = 1)
    val qr = alessio.client.newQr()
    mamma.client.requestFromQr(dev.antigravity.fluidengine.people.crypto.PersonQr.decode(qr.encode())!!, "sono io")
    assertTrue("chi inquadra ha la chiave intera", mamma.client.isVerified(alessio.code))
    // Alessio riconosce il suo QR e la prova: entra da sé, verificata anche per lui.
    val events = alessio.client.pickup()
    assertTrue(events.any { it is PeopleEvent.Accepted })
    assertTrue(alessio.client.isVerified(mamma.code))
    assertEquals(ContactVia.QR, alessio.client.contact(mamma.code)!!.via)
    // Il server conosce il gettone (viaggia in chiaro) ma non il segreto: una richiesta col gettone e senza prova resta
    // una richiesta da accettare, e non è verificata.
    val other = BenchPerson(server, "Estraneo", deviceCount = 1)
    val qr2 = alessio.client.newQr()
    other.client.request(other.client.lookup(alessio.code), "ciao", invite = qr2.invite)
    val req = alessio.client.pickup().filterIsInstance<PeopleEvent.Request>().single()
    alessio.client.accept(req.request)
    assertFalse(alessio.client.isVerified(other.code))
  }

  @Test
  fun `il rinnovo dopo un furto - parole cambiate e da rifare, il telefono rubato non apre niente`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    marco.client.markWordsVerified(alessio.code)
    val before = marco.client.words(alessio.code)!!.words
    assertTrue(marco.client.isVerified(alessio.code))

    // Il tablet di Alessio è rubato: si fotografano le sue chiavi, poi viene fatto uscire.
    val stolen = alessio.device(1)
    val stolenWorks = stolen.persona.identity()!!.works.toMap()
    val stolenChat = stolen.client.contact(marco.code)!!.chatKeys.toMap()
    server.revoke(stolen.device)
    alessio.device(0).client.rotateWork()

    // Marco riceve le chiavi nuove: la radice è la stessa, quindi niente allarme; le parole sono da rifare.
    val events = marco.client.pickup()
    assertTrue(events.any { it is PeopleEvent.WordsChanged && it.byThem })
    assertEquals(ContactNotice.WORDS_CHANGED_THEIRS, marco.client.contact(alessio.code)!!.notice)
    assertFalse("a distanza la verifica è da rifare, come Signal", marco.client.isVerified(alessio.code))
    alessio.client.pickup()
    marco.client.pickup()
    val after = marco.client.words(alessio.code)!!.words
    assertNotEquals(before, after)
    assertEquals(after, alessio.client.words(marco.code)!!.words)
    marco.client.markWordsVerified(alessio.code)
    assertTrue(marco.client.isVerified(alessio.code))

    // Quello che arriva dopo è per le chiavi nuove: il tablet rubato non lo apre, e non può nemmeno ritirare.
    marco.client.sendMessage(alessio.code, text("dopo il furto"))
    val env = server.mailboxes[alessio.mailbox]!!.envelopes.last()
    val data = PampaBase64.decode(env.data)
    assertNull(Pmx1.open(stolenChat, marco.mailbox, alessio.mailbox, marco.client.contact(alessio.code)!!.chatId, data))
    assertEquals(2, Pmx1.epochOf(data))
    try {
      stolen.client.pickup()
      fail("il dispositivo uscito non ritira")
    } catch (e: PeopleServerException) {
      assertEquals("session_revoked", e.code)
    }
    // Una CDXB nuova per Alessio è per l'epoca di lavoro 2, che il rubato non ha.
    val toAlessio = dev.antigravity.fluidengine.people.crypto.Cdxb.seal(marco.client.identity().work, marco.mailbox, marco.client.identity().card,
      dev.antigravity.fluidengine.people.crypto.CdxbRecipient.of(alessio.client.identity().card), buildJsonObject { put("t", "x") })
    try {
      Cdxb.open(alessio.mailbox, stolenWorks, marco.mailbox, toAlessio)
      fail("il rubato non apre")
    } catch (e: PeopleDetected) {
      assertEquals("busta per una chiave che non ho", e.what)
    }
    assertEquals(alessio.device(0).client.identity().card.work.epoch, 2)
  }

  @Test
  fun `un gruppo di 30 e uno di 100 - QR del gruppo, approvazione, messaggi a tutti, uscite con l'epoca nuova`() = runBlocking {
    for (size in listOf(30, 100)) {
      val server = FakePeopleServer()
      val admin = BenchPerson(server, "Prof", deviceCount = 1)
      val members = (1 until size).map { BenchPerson(server, "Studente $it", deviceCount = 1) }
      val g = admin.client.createGroup("3ªB · gita a Firenze")
      val qr = admin.client.groupInvite(g.id)
      // Oltre le 20 richieste al giorno: l'invito del gruppo passa lo stesso.
      for (m in members) m.client.joinGroup(qr)
      val joins = admin.client.pickup().filterIsInstance<PeopleEvent.Join>()
      assertEquals(size - 1, joins.size)
      admin.client.approve(g.id, joins)
      for (m in members) assertTrue(m.client.pickup().any { it is PeopleEvent.GroupUpdated })
      assertEquals("3ªB · gita a Firenze", members[0].client.state.groups[g.id]!!.name)

      admin.client.groupMessage(g.id, text("partenza alle 7"))
      for (m in members) assertEquals("partenza alle 7", m.client.pickup().filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)

      // Tolgo i primi due: epoca nuova, e loro sentono solo «fuori».
      val gone = members.take(2)
      admin.client.removeFromGroup(g.id, gone.map { it.code })
      for (m in gone) assertTrue(m.client.pickup().any { it is PeopleEvent.RemovedFromGroup })
      for (m in members.drop(2)) assertEquals(2, m.client.pickup().filterIsInstance<PeopleEvent.GroupUpdated>().single().epoch)
      admin.client.groupMessage(g.id, text("chi esce perde il futuro"))
      for (m in gone) assertTrue(m.client.pickup().none { it is PeopleEvent.Message })
      for (m in members.drop(2)) assertEquals(1, m.client.pickup().filterIsInstance<PeopleEvent.Message>().size)
      // Chi è uscito scrive col gettone vecchio: le caselle non lo accettano più.
      gone[0].client.groupMessage(g.id, text("ci sono ancora?"))
      assertTrue(members[2].client.pickup().none { it is PeopleEvent.Message })
    }
  }

  @Test
  fun `i tetti - 20 codici al giorno, 10 richieste mandate, 20 ricevute, saluto da 280`() = runBlocking {
    val server = FakePeopleServer()
    val me = BenchPerson(server, "Alessio", deviceCount = 1)
    val others = (1..21).map { BenchPerson(server, "P$it", deviceCount = 1) }
    others.take(20).forEach { me.client.lookup(it.code) }
    try {
      me.client.lookup(others[20].code)
      fail("il 21° codice del giorno")
    } catch (e: PeopleServerException) {
      assertEquals("rate_limited", e.code)
    }
    // Lo stesso codice di prima non conta di nuovo.
    me.client.lookup(others[0].code)
    try {
      me.client.request(me.client.lookup(others[1].code), "x".repeat(281))
      fail("saluto troppo lungo")
    } catch (_: IllegalArgumentException) {
    }
    // Dieci richieste mandate al giorno; l'undicesima aspetta in coda, non si perde.
    for (p in others.take(11)) me.client.request(me.client.lookup(p.code), "ciao")
    val report = me.client.flush()
    assertEquals(1, report.kept)
    server.now += 86_400_000L
    assertEquals(0, me.client.flush().kept)
    // Venti ricevute: la ventunesima è rifiutata alla casella, e aspetta anche lei.
    val target = others[20]
    for (p in others.take(20)) p.client.request(p.client.lookup(target.code), "ciao")
    val late = BenchPerson(server, "Ritardatario", deviceCount = 1)
    late.client.request(late.client.lookup(target.code), "ciao")
    assertEquals(1, late.client.flush().kept)
  }

  @Test
  fun `il server ostile - scheda cambiata, busta spostata o col mittente cambiato`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    val chiara = BenchPerson(server, "Chiara", deviceCount = 1)
    // Il server rende la scheda di Chiara per il codice di Marco.
    val chiaraCard = chiara.client.identity().card
    server.hostile.swapLookup = { code -> if (code == marco.code) chiaraCard.raw to chiaraCard.sig else null }
    try {
      alessio.client.lookup(marco.code)
      fail("la scheda di un altro col codice di Marco")
    } catch (e: PeopleDetected) {
      assertEquals("scheda con un altro codice", e.what)
    }
    server.hostile.swapLookup = null
    befriend(alessio, marco)
    befriend(alessio, chiara)
    // Il server dice che il messaggio di Marco viene da Chiara.
    server.hostile.tamperPickup = { e -> FakePeopleServer.Envelope(e.n, e.kind, chiara.mailbox, e.data, e.size, e.via, e.minStore, e.priority, e.at) }
    marco.client.sendMessage(alessio.code, text("sono Marco"))
    val ev = alessio.client.pickup()
    assertTrue(ev.none { it is PeopleEvent.Message })
    assertTrue(ev.any { it is PeopleEvent.Detected })
    server.hostile.tamperPickup = null
    // Il server sposta nella casella di Alessio una busta che era per Chiara.
    marco.client.request(marco.client.lookup(chiara.code), "ciao Chiara")
    val forChiara = server.mailboxes[chiara.mailbox]!!.envelopes.last()
    server.mailboxes[alessio.mailbox]!!.envelopes += FakePeopleServer.Envelope(99_999, "request", marco.mailbox, forChiara.data, forChiara.size, null, forChiara.minStore, "high", server.now)
    val moved = alessio.client.pickup()
    assertTrue(moved.any { it is PeopleEvent.Detected && it.what.startsWith("busta per una chiave che non ho") || it is PeopleEvent.Detected && it.what.startsWith("busta che non si apre") })
    assertTrue(moved.none { it is PeopleEvent.Request })
  }

  @Test
  fun `l'impegno delle parole - una rivelazione cambiata dopo aver visto il nonce dell'altro viene scoperta`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    alessio.client.request(alessio.client.lookup(marco.code), "ciao")
    marco.client.accept(marco.client.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    // Chi sta in mezzo cambia il nonce rivelato: è come scegliere le parole dopo, e l'impegno non torna più.
    alessio.client.edit { st -> st.contacts[marco.code] = st.contacts[marco.code]!!.let { it.copy(words = it.words!!.copy(myNonce = ByteArray(16) { 9 })) } }
    alessio.client.pickup()
    val ev = marco.client.pickup()
    assertTrue(ev.any { it is PeopleEvent.Detected && it.what == "l'impegno delle parole non torna" })
    assertNull(marco.client.words(alessio.code))
  }

  @Test
  fun `il campanello finto - sveglie raggruppate, alta e normale, niente sveglia col canale aperto`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    server.bells.clear()
    server.now += 60_000
    marco.client.sendMessage(alessio.code, text("uno"), EnvelopePriority.NORMAL)
    server.now += 2_000
    marco.client.sendMessage(alessio.code, text("due"), EnvelopePriority.HIGH)
    for (d in alessio.devices) {
      val rings = server.ringsFor(d.device)
      assertEquals("una sveglia sola nei 10 secondi", 1, rings.size)
      assertEquals("con la priorità più alta arrivata", "high", rings.single().priority)
    }
    server.now += 11_000
    alessio.device(0).device.channelOpen = true
    marco.client.sendMessage(alessio.code, text("tre"), EnvelopePriority.NORMAL)
    assertEquals(1, server.ringsFor(alessio.device(0).device).size)
    assertEquals(2, server.ringsFor(alessio.device(1).device).size)
    assertEquals("normal", server.ringsFor(alessio.device(1).device).last().priority)
    // Lo Store svegliato ritira tutto.
    assertEquals(3, alessio.device(1).client.onBell().filterIsInstance<PeopleEvent.Message>().size)
  }

  @Test
  fun `la coda in uscita - buste firmate subito, partono quando torna la rete, anche col telefono in tasca`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    server.offline = true
    try {
      alessio.client.sendMessage(marco.code, text("in galleria"))
    } catch (_: java.io.IOException) {
      fail("senza rete la busta resta in coda, senza errori")
    }
    assertEquals(1, alessio.client.state.outbox.size)
    assertTrue(alessio.client.flush().offline)
    // Il telefono va in tasca: la radice si chiude. La coda non ne ha bisogno: le buste sono già firmate.
    alessio.first.vault.motherLocked = true
    server.offline = false
    val opens = alessio.first.vault.rootOpens
    assertEquals(1, alessio.client.flush().sent)
    assertEquals(opens, alessio.first.vault.rootOpens)
    assertEquals("in galleria", marco.client.pickup().filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)
    // La persona se ne va da Pampa: la busta non arriverà mai, e la coda la toglie dicendolo.
    alessio.client.sendMessage(marco.code, text("addio"))
    server.mailboxes[marco.mailbox]!!.closed = true
    alessio.client.sendMessage(marco.code, text("ci sei?"))
    assertTrue(alessio.client.state.outbox.isEmpty())
  }

  @Test
  fun `uno Store vecchio - la busta per uno Store piu nuovo diventa «Aggiorna Pampa Store»`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio", deviceCount = 1)
    val marco = BenchPerson(server, "Marco", deviceCount = 1)
    befriend(alessio, marco)
    val c = alessio.client.contact(marco.code)!!
    val data = Pmx1.seal(c.chatKeys.getValue(c.chatEpoch), c.chatEpoch, alessio.mailbox, marco.mailbox, c.chatId, text("nuovo").toString().toByteArray())
    alessio.client.peopleApi.send(marco.mailbox, listOf(dev.antigravity.fluidengine.people.client.OutEnvelope(marco.mailbox, PampaBase64.encode(data), token = c.theirToken, minStore = "9.0.0")))
    val ev = marco.client.pickup()
    assertEquals("9.0.0", ev.filterIsInstance<PeopleEvent.TooNew>().single().minStore)
    assertTrue(PeopleClient.newer("1.10.0", "1.9.3"))
    assertFalse(PeopleClient.newer("1.0.0", "1.0.0"))
  }

  @Test
  fun `niente chiavi private nelle richieste al server`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val marco = BenchPerson(server, "Marco")
    befriend(alessio, marco)
    alessio.client.sendMessage(marco.code, text("ciao"))
    alessio.client.rotateWork()
    for (p in listOf(alessio, marco)) {
      val me = p.client.identity()
      val secrets = buildList {
        add(PampaBase64.encode(p.first.persona.root().privateBytes()))
        for (w in me.works.values) { add(PampaBase64.encode(w.enc.privateBytes())); add(PampaBase64.encode(w.sign.privateBytes())) }
        for (c in p.client.state.contacts.values) { c.chatKeys.values.forEach { add(PampaBase64.encode(it)) }; c.words?.myNonce?.let { if (!c.words!!.ready) add(PampaBase64.encode(it)) } }
      }
      for (r in server.requestLog) {
        val body = r.body?.toString().orEmpty() + r.path
        for (s in secrets) assertFalse("un segreto in ${r.method} ${r.path}", body.contains(s))
      }
    }
    assertNotNull(server.requestLog.firstOrNull())
  }
}
