package dev.antigravity.fluidengine.sync

import dev.antigravity.fluidengine.foundation.SyncState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.21 · Il banco di prove, caso per caso: due e tre dispositivi, orologi sfasati, la rete che cade a meta', il doppio
 * invio, le cancellazioni incrociate, la compattazione, un dispositivo fermo per mesi, i conflitti sullo stesso campo e sui
 * testi lunghi, il sigillo.
 */
class SyncBenchTest {
  private val world = BenchWorld()

  @After fun close() {
    world.close()
    assertNull("il registro si tocca solo dal dispatcher dell'engine", world.wrongThread.get())
  }

  private fun assertConverged(vararg devices: BenchDevice, space: String = "spesa") {
    val first = devices.first().records(space)
    for (d in devices.drop(1)) assertEquals("$d diverge da ${devices.first()}", first, d.records(space))
    val relay = world.relayDocuments(space).filterValues { !it.isDeleted }
    assertEquals("il relay diverge", first, relay)
    for (d in devices) {
      assertEquals("$d ha modifiche in attesa", 0, d.state(space).pending)
      assertTrue("$d ha elementi da mandare", d.stored(space).none { it.dirty })
    }
  }

  @Test fun `due dispositivi si passano una lista e le sue modifiche`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    val id = phone.create { text("voce", "Latte"); bool("presa", false) }
    assertEquals("subito nel registro e nello stato, senza rete", "Latte", phone.records()[id]?.text("voce"))
    assertEquals("un elemento nuovo, anche con due campi, e' una modifica", 1, phone.state().pending)
    phone.syncNow()
    assertEquals(0, phone.state().pending)
    tablet.syncNow()
    assertEquals("Latte", tablet.records()[id]?.text("voce"))
    world.advance(60_000)
    tablet.edit(id) { bool("presa", true) }
    tablet.syncNow()
    phone.syncNow()
    assertEquals(true, phone.records()[id]?.bool("presa"))
    assertConverged(phone, tablet)
  }

  @Test fun `tre dispositivi senza rete cambiano campi diversi e convergono`() {
    val a = world.device("a")
    val b = world.device("b")
    val c = world.device("c")
    val id = a.create { text("titolo", "Gita"); text("luogo", "Firenze"); integer("posti", 30) }
    world.settle(a, b, c)
    listOf(a, b, c).forEach { it.goOffline() }
    a.edit(id) { text("titolo", "Gita a Firenze") }
    world.advance(1_000)
    b.edit(id) { text("luogo", "Uffizi") }
    world.advance(1_000)
    c.edit(id) { integer("posti", 28) }
    listOf(a, b, c).forEach { assertEquals(SyncFailure.Offline, it.syncNow().failure) }
    listOf(a, b, c).forEach { it.goOnline() }
    world.settle(a, b, c)
    val doc = a.records()[id]!!
    assertEquals("Gita a Firenze", doc.text("titolo"))
    assertEquals("Uffizi", doc.text("luogo"))
    assertEquals(28L, doc.integer("posti"))
    assertConverged(a, b, c)
  }

  @Test fun `con l'orologio indietro di tre ore una modifica fatta dopo averne vista un'altra vince`() {
    val ahead = world.device("avanti", skewMillis = 3 * 3_600_000L)
    val behind = world.device("indietro", skewMillis = -3 * 3_600_000L)
    val id = ahead.create { text("stato", "da fare") }
    ahead.syncNow()
    behind.syncNow()
    world.advance(60_000)
    // Il telefono indietro vede la modifica «del futuro» e poi scrive: il suo stamp segue quello che ha visto.
    behind.edit(id) { text("stato", "fatto") }
    behind.syncNow()
    ahead.syncNow()
    assertEquals("fatto", ahead.records()[id]?.text("stato"))
    assertConverged(ahead, behind)
  }

  @Test fun `dopo il primo contatto col relay l'ora vera ordina due modifiche che non si sono viste`() {
    val ahead = world.device("avanti", skewMillis = 3 * 3_600_000L)
    val normal = world.device("giusto")
    val id = normal.create { text("nota", "prima") }
    world.settle(normal, ahead)
    assertTrue("l'orologio avanti e' stato corretto", ahead.sync.clock.offsetMillis < -2 * 3_600_000L)
    ahead.goOffline()
    normal.goOffline()
    // Il dispositivo con l'orologio avanti scrive per primo nel tempo vero, quello giusto dieci minuti dopo.
    ahead.edit(id) { text("nota", "dal telefono avanti") }
    world.advance(10 * 60_000L)
    normal.edit(id) { text("nota", "dopo, dal telefono giusto") }
    ahead.goOnline()
    normal.goOnline()
    world.settle(ahead, normal)
    assertEquals("vince l'ultima nel tempo vero", "dopo, dal telefono giusto", ahead.records()[id]?.text("nota"))
    assertConverged(ahead, normal)
  }

  @Test fun `la rete cade a meta' dell'invio e niente si applica due volte`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    val id = phone.create { longText("testo", "latte\n") }
    world.settle(phone, tablet)
    phone.edit(id) { longText("testo", "latte\npane\n") }
    phone.net.dropPushResponses = 1
    val first = phone.syncNow()
    assertEquals("il dispositivo non ha avuto la risposta", SyncFailure.Offline, first.failure)
    val (_, appliedAfterDrop) = world.relay.counts(SyncSpaceId("spesa"))
    // L'invio e' rimasto in volo: si rimanda identico, con la stessa chiave.
    val resent = phone.syncNow()
    assertNull(resent.failure)
    val keys = phone.net.pushes.map { it.idempotencyKey }
    assertEquals("lo stesso invio, due volte", keys[keys.size - 2], keys.last())
    assertEquals("il relay non ha scritto di nuovo", appliedAfterDrop, world.relay.counts(SyncSpaceId("spesa")).second)
    tablet.syncNow()
    assertEquals("latte\npane\n", tablet.records()[id]?.text("testo"))
    assertConverged(phone, tablet)
  }

  @Test fun `il doppio invio arriva due volte e il relay scrive una volta sola`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    phone.net.duplicatePushes = 3
    val id = phone.create { longText("testo", "uno\n") }
    phone.syncNow()
    phone.edit(id) { longText("testo", "uno\ndue\n") }
    phone.syncNow()
    val (pushes, applied) = world.relay.counts(SyncSpaceId("spesa"))
    assertEquals("quattro invii arrivati", 4, pushes)
    assertEquals("due scritture vere", 2, applied)
    tablet.syncNow()
    assertEquals("uno\ndue\n", tablet.records()[id]?.text("testo"))
    assertConverged(phone, tablet)
  }

  @Test fun `una risposta persa dopo che il relay ha dimenticato la chiave non duplica il testo`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    val id = phone.create { longText("testo", "a\n") }
    world.settle(phone, tablet)
    phone.edit(id) { longText("testo", "a\nb\n") }
    phone.net.dropPushResponses = 1
    phone.syncNow()
    // 25 ore dopo il relay non ricorda piu' la chiave: il rinvio trova la propria scrittura come conflitto.
    world.advance(25 * 3_600_000L)
    val again = phone.syncNow()
    assertNull(again.failure)
    world.settle(phone, tablet)
    assertEquals("a\nb\n", phone.records()[id]?.text("testo"))
    assertEquals("a\nb\n", tablet.records()[id]?.text("testo"))
    assertConverged(phone, tablet)
  }

  @Test fun `cancellazioni incrociate - vince la cancellazione e le altre modifiche restano`() {
    val a = world.device("a")
    val b = world.device("b")
    val milk = a.create { text("voce", "Latte"); bool("presa", false) }
    val bread = a.create { text("voce", "Pane") }
    val eggs = a.create { text("voce", "Uova") }
    world.settle(a, b)
    a.goOffline()
    b.goOffline()
    // a toglie il latte, b lo spunta: vince la tolta. Tutti e due tolgono il pane. b cambia le uova.
    a.delete(milk)
    b.edit(milk) { bool("presa", true) }
    a.delete(bread)
    b.delete(bread)
    b.edit(eggs) { text("voce", "Uova (6)") }
    a.goOnline()
    b.goOnline()
    // Arriva prima la cancellazione di a: la spunta di b, ancora in attesa, e' superata.
    val reports = (0 until 3).flatMap { listOf(a.syncNow(), b.syncNow()) }
    assertTrue("la spunta di b e' stata superata dalla cancellazione", reports.sumOf { it.supersededByDelete } >= 1)
    assertNull(a.records()[milk])
    assertNull(a.records()[bread])
    assertEquals("Uova (6)", a.records()[eggs]?.text("voce"))
    assertFalse("una modifica non riporta in vita un elemento cancellato", b.edit(milk) { bool("presa", false) })
    assertConverged(a, b)
  }

  @Test fun `il registro si compatta e le pietre tombali se ne vanno dopo 90 giorni`() {
    val a = world.device("a")
    val b = world.device("b")
    val ids = (1..5).map { n -> a.create { text("voce", "voce $n") } }
    a.edit(ids[0]) { text("voce", "voce 1 cambiata") }
    assertEquals("ogni modifica e' una voce del registro", 6, a.journal().size)
    a.syncNow()
    assertEquals("confermate, le voci se ne vanno", 0, a.journal().size)
    world.settle(a, b)
    a.delete(ids[1])
    world.settle(a, b)
    assertEquals("la pietra tombale resta", 1, a.stored().count { it.local.isDeleted })
    world.advance(91 * Day)
    world.relay.purge()
    world.settle(a, b)
    assertEquals("dopo 90 giorni se ne va dal registro", 0, a.stored().count { it.local.isDeleted })
    assertEquals(0, b.stored().count { it.local.isDeleted })
    assertTrue("e dal relay", world.relay.rows(SyncSpaceId("spesa")).none { it.deleted })
    // Un elemento nato e cancellato senza mai partire non lascia niente.
    val ghost = a.create { text("voce", "fantasma") }
    a.delete(ghost)
    a.syncNow()
    assertTrue(a.stored().none { it.recordId == ghost })
    assertEquals(0, a.journal().size)
    assertConverged(a, b)
  }

  @Test fun `un dispositivo fermo per quattro mesi si riprende senza perdere niente`() {
    val a = world.device("a")
    val b = world.device("b")
    val sleeper = world.device("fermo")
    val keep = a.create { text("voce", "Resta"); integer("n", 1) }
    val doomed = a.create { text("voce", "Sara' tolta") }
    world.settle(a, b, sleeper)
    sleeper.goOffline()
    // Fermo, ma con modifiche fatte prima di spegnersi: una voce nuova e una cambiata.
    val fresh = sleeper.create { text("voce", "Nata sul fermo") }
    sleeper.edit(keep) { integer("n", 2) }
    sleeper.edit(doomed) { text("voce", "cambiata sul fermo") }
    // Intanto gli altri vanno avanti per quattro mesi.
    a.delete(doomed)
    world.advance(30 * Day)
    val later = b.create { text("voce", "Nata dopo") }
    world.settle(a, b)
    world.advance(100 * Day)
    assertTrue("il relay ha tolto la pietra tombale", world.relay.purge() >= 1)
    sleeper.goOnline()
    val report = sleeper.syncNow()
    assertTrue("ha riletto tutto", report.fullResync)
    world.settle(sleeper, a, b)
    val docs = a.records()
    assertEquals("la voce nata sul fermo c'e'", "Nata sul fermo", docs[fresh]?.text("voce"))
    assertEquals("la modifica del fermo c'e'", 2L, docs[keep]?.integer("n"))
    assertEquals("Nata dopo", sleeper.records()[later]?.text("voce"))
    assertNull("la voce tolta resta tolta", sleeper.records()[doomed])
    assertConverged(a, b, sleeper)
  }

  @Test fun `lo stesso campo - un valore corto vince l'ultimo, un testo lungo fonde passaggi diversi`() {
    val a = world.device("a")
    val b = world.device("b")
    val id = a.create {
      text("titolo", "Verbale")
      longText("testo", "Presenti: tutti.\nOrdine del giorno: gita.\nDecisioni: nessuna.\n")
    }
    world.settle(a, b)
    a.goOffline()
    b.goOffline()
    a.edit(id) {
      text("titolo", "Verbale di classe")
      longText("testo", "Presenti: tutti tranne Luca.\nOrdine del giorno: gita.\nDecisioni: nessuna.\n")
    }
    world.advance(5_000)
    b.edit(id) {
      text("titolo", "Verbale del 24")
      longText("testo", "Presenti: tutti.\nOrdine del giorno: gita.\nDecisioni: si va a Firenze.\n")
    }
    a.goOnline()
    b.goOnline()
    world.settle(a, b)
    val doc = a.records()[id]!!
    assertEquals("il titolo: l'ultimo", "Verbale del 24", doc.text("titolo"))
    assertEquals(
      "Presenti: tutti tranne Luca.\nOrdine del giorno: gita.\nDecisioni: si va a Firenze.\n",
      doc.text("testo"),
    )
    assertTrue(a.state().conflicts.isEmpty())
    assertConverged(a, b)
  }

  @Test fun `lo stesso passaggio cambiato in due modi chiede la scelta su tutti, e la scelta vale per tutti`() {
    val a = world.device("a")
    val b = world.device("b")
    val c = world.device("c")
    val id = a.create { longText("testo", "Ritrovo alle 8 davanti a scuola.\n") }
    world.settle(a, b, c)
    a.goOffline()
    b.goOffline()
    a.edit(id) { longText("testo", "Ritrovo alle 7:45 in stazione.\n") }
    world.advance(5_000)
    b.edit(id) { longText("testo", "Ritrovo alle 8:30 al parcheggio.\n") }
    a.goOnline()
    b.goOnline()
    world.settle(a, b, c)
    val conflict = c.state().conflicts.single()
    assertEquals(id, conflict.recordId)
    assertEquals(2, conflict.versions.size)
    assertEquals("l'attuale e' la piu' recente", "Ritrovo alle 8:30 al parcheggio.\n", conflict.versions[0].text)
    assertEquals("a", conflict.versions[1].node)
    assertEquals(conflict, a.state().conflicts.single())
    // Una scelta con revisioni vecchie non passa.
    val stale = runBlocking { b.space().resolve(id, "testo", listOf(conflict.revisions[1]), listOf(conflict.revisions[1])) }
    assertTrue(stale is SyncResolution.Stale)
    val done = runBlocking { b.space().resolve(id, "testo", listOf(conflict.revisions[1]), conflict.revisions) }
    assertEquals(SyncResolution.Resolved(emptyList()), done)
    world.settle(a, b, c)
    for (d in listOf(a, b, c)) {
      assertTrue("$d non ha piu' conflitti", d.state().conflicts.isEmpty())
      assertEquals("Ritrovo alle 7:45 in stazione.\n", d.records()[id]?.text("testo"))
    }
    assertConverged(a, b, c)
  }

  @Test fun `tieni tutte e due crea una copia su tutti i dispositivi`() {
    val a = world.device("a")
    val b = world.device("b")
    val id = a.create { text("titolo", "Diario"); longText("testo", "Oggi pioveva.\n") }
    world.settle(a, b)
    a.goOffline()
    a.edit(id) { longText("testo", "Oggi c'era il sole.\n") }
    world.advance(1_000)
    b.edit(id) { longText("testo", "Oggi grandinava.\n") }
    b.syncNow()
    a.goOnline()
    world.settle(a, b)
    val conflict = a.state().conflicts.single()
    val result = runBlocking { a.space().resolve(id, "testo", conflict.revisions, conflict.revisions) }
    val copies = (result as SyncResolution.Resolved).copies
    assertEquals(1, copies.size)
    world.settle(a, b)
    assertEquals("Oggi grandinava.\n", b.records()[id]?.text("testo"))
    assertEquals("Oggi c'era il sole.\n", b.records()[copies.single()]?.text("testo"))
    assertEquals("Diario", b.records()[copies.single()]?.text("titolo"))
    assertConverged(a, b)
  }

  // Visto sul tablet (25/11/2026): una voce sola diceva «3 modifiche in attesa», una per campo.
  @Test fun `le modifiche in attesa si contano per elemento, non per campo`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    phone.goOffline()
    val id = phone.create { text("voce", "Crema solare"); bool("presa", false); longText("nota", "Quella del mare.\n") }
    assertEquals("una voce nuova con tre campi e' una modifica", 1, phone.state().pending)
    assertEquals("nel registro restano tre voci", 3, phone.disk.transaction { journalSize("spesa") })
    phone.edit(id) { bool("presa", true) }
    assertEquals("un'altra modifica allo stesso elemento non si somma", 1, phone.state().pending)
    phone.create { text("voce", "Cappello") }
    assertEquals(2, phone.state().pending)
    phone.syncNow()
    assertEquals("2 modifiche in attesa: partono quando torna la rete.", phone.sync.status.value.message)
    assertEquals(2, phone.sync.overview.value.pending)
    phone.goOnline()
    world.settle(phone, tablet)
    assertEquals(0, phone.state().pending)

    // «Tieni tutte e due»: l'elemento con la scelta e la copia nuova, due modifiche.
    phone.goOffline()
    phone.edit(id) { longText("nota", "Quella con protezione 50.\n") }
    world.advance(1_000)
    tablet.edit(id) { longText("nota", "Quella nel bagno, accanto al doposole.\n") }
    tablet.syncNow()
    phone.goOnline()
    world.settle(phone, tablet)
    val conflict = phone.state().conflicts.single()
    phone.goOffline()
    val result = runBlocking { phone.space().resolve(id, "nota", conflict.revisions, conflict.revisions) }
    assertEquals(1, (result as SyncResolution.Resolved).copies.size)
    assertEquals("l'elemento e la sua copia", 2, phone.state().pending)
    assertTrue("le voci nel registro sono di piu'", phone.disk.transaction { journalSize("spesa") } > 2)
    phone.goOnline()
    world.settle(phone, tablet)
    assertEquals(0, phone.state().pending)
    assertEquals(0, tablet.state().pending)
    assertConverged(phone, tablet)
  }

  @Test fun `il relay vede solo byte sigillati, e un contenuto spostato non si apre`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    val canary = "Password del Wi-Fi di casa: canarino-7319"
    val one = phone.create { longText("testo", canary) }
    val two = phone.create { longText("testo", "Lista innocua") }
    phone.syncNow()
    val needle = "canarino-7319".toByteArray()
    for (bytes in phone.net.payloadBytes) assertFalse("testo in chiaro verso il relay", bytes.containsSlice(needle))
    for (row in world.relay.rows(SyncSpaceId("spesa"))) assertFalse(row.payload!!.bytes.containsSlice(needle))
    val inflight = phone.disk.transaction { meta("spesa", SyncSpace.MetaInflight) }
    assertNull("nessun invio rimasto in volo", inflight)
    // Un relay ostile scambia i contenuti dei due elementi: il sigillo lo scopre, e niente si applica.
    val hostile = object : SyncRelay by world.relay {
      override suspend fun pull(space: SyncSpaceId, since: Long, limit: Int): SyncPullResult {
        val rows = (world.relay.pull(space, since, limit) as SyncPullResult.Rows).rows
        val swapped = rows.map { row ->
          val other = rows.first { it.recordId != row.recordId }
          SyncRemoteRow(row.recordId, row.version, row.deleted, other.payload, row.seq)
        }
        return SyncPullResult.Rows(swapped, rows.maxOf { it.seq }, false, world.time)
      }
    }
    val victim = FluidSync(InMemorySyncStore(), hostile, world.sealer, "vittima", wall = { world.time })
    val report = runBlocking { victim.space(SyncSpaceId("spesa")).sync() }
    assertEquals("unreadable", (report.failure as SyncFailure.Rejected).code)
    assertTrue(victim.space(SyncSpaceId("spesa")).state.value.records.isEmpty())
    assertEquals(SyncState.ERROR, victim.status.value.state)
    // Il tablet, con un relay onesto, legge tutto.
    tablet.syncNow()
    assertEquals(canary, tablet.records()[one]?.text("testo"))
    assertEquals("Lista innocua", tablet.records()[two]?.text("testo"))
  }

  @Test fun `il processo muore con un invio in volo e alla ripartenza lo rimanda uguale`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    val id = phone.create { longText("testo", "uno\n") }
    phone.net.dropPushResponses = 1
    phone.syncNow()
    val saved = phone.disk.transaction { meta("spesa", SyncSpace.MetaInflight) }
    assertNotNull("l'invio e' salvato nel registro", saved)
    phone.restart()
    assertEquals("dopo il riavvio la modifica e' ancora in attesa", 1, phone.state().pending)
    phone.syncNow()
    assertEquals(1, world.relay.counts(SyncSpaceId("spesa")).second)
    tablet.syncNow()
    assertEquals("uno\n", tablet.records()[id]?.text("testo"))
    assertConverged(phone, tablet)
  }

  @Test fun `una transazione interrotta non lascia meta' modifica`() {
    val disk = InMemorySyncStore()
    var explode = false
    val fragile = object : SyncStore {
      override fun <T> transaction(block: SyncStoreTransaction.() -> T): T = disk.transaction {
        val tx = this
        val wrapped = object : SyncStoreTransaction by tx {
          override fun putRecord(space: String, record: SyncStoredRecord) {
            if (explode) throw IllegalStateException("corrente staccata")
            tx.putRecord(space, record)
          }
        }
        wrapped.block()
      }
    }
    val sync = FluidSync(fragile, world.relay, world.sealer, "fragile", wall = { world.time })
    val space = sync.space(SyncSpaceId("spesa"))
    val id = runBlocking { space.create { text("voce", "Latte") } }
    explode = true
    runCatching { runBlocking { space.edit(id) { text("voce", "Pane"); bool("presa", true) } } }
    explode = false
    val journal = disk.transaction { journal("spesa") }
    assertEquals("solo la voce della prima scrittura", 1, journal.size)
    assertEquals("Latte", disk.transaction { record("spesa", id) }!!.local.text("voce"))
  }

  @Test fun `lo stato dice offline con le modifiche in attesa, poi aggiornato`() {
    val phone = world.device("telefono")
    phone.goOffline()
    phone.create { text("voce", "Latte") }
    phone.create { text("voce", "Pane") }
    phone.create { text("voce", "Uova") }
    phone.syncNow()
    val offline = phone.sync.status.value
    assertEquals(SyncState.OFFLINE, offline.state)
    assertEquals("3 modifiche in attesa: partono quando torna la rete.", offline.message)
    assertEquals(3, phone.sync.overview.value.pending)
    phone.goOnline()
    phone.syncNow()
    val done = phone.sync.status.value
    assertEquals(SyncState.IDLE, done.state)
    assertEquals(world.time, done.lastSuccessfulSyncEpochMillis)
  }

  @Test fun `due spazi hanno registri separati e uno che fallisce non ferma l'altro`() {
    val phone = world.device("telefono")
    val tablet = world.device("tablet")
    phone.create(space = "spesa") { text("voce", "Latte") }
    phone.create(space = "ricordi") { longText("testo", "Il compleanno di Giulia e' il 3 marzo") }
    assertEquals(1, phone.journal("spesa").size)
    assertEquals(1, phone.journal("ricordi").size)
    val report = phone.syncAll()
    assertNull(report.failure)
    assertEquals(2, report.spaces.size)
    // Un dispositivo nuovo apre gli spazi che l'app conosce e li riceve.
    tablet.space("spesa")
    tablet.space("ricordi")
    tablet.syncAll()
    assertEquals(1, tablet.records("ricordi").size)
    // Dopo un avvio a freddo il lavoro in sottofondo non sa quali spazi aprira' l'app: li trova nel registro.
    phone.edit(phone.records("spesa").keys.single(), space = "spesa") { bool("presa", true) }
    phone.restart()
    assertTrue(phone.sync.openSpaces.isEmpty())
    val cold = phone.syncAll()
    assertEquals(setOf("spesa", "ricordi"), cold.spaces.map { it.space.value }.toSet())
    tablet.syncAll()
    assertEquals(true, tablet.records("spesa").values.single().bool("presa"))
  }
}

/** F8.21 · Senza la chiave dell'account il sigillo non si fa: le modifiche aspettano sul telefono, poi partono. */
class SyncSealWaitTest {
  @Test fun `senza la chiave le modifiche restano in attesa e lo stato lo dice, poi partono`() {
    val world = BenchWorld()
    var unlocked = false
    val locked = object : SyncSealer {
      override suspend fun seal(context: SyncSealContext, plaintext: ByteArray): SyncSealedPayload {
        if (!unlocked) throw SyncSealException("la chiave dell'app non c'e' ancora")
        return world.sealer.seal(context, plaintext)
      }

      override suspend fun open(context: SyncSealContext, payload: SyncSealedPayload): ByteArray = world.sealer.open(context, payload)
    }
    val sync = FluidSync(InMemorySyncStore(), world.relay, locked, "chiuso", wall = { world.time })
    val space = sync.space(SyncSpaceId("spesa"))
    runBlocking { space.create { text("voce", "Latte") } }
    val waiting = runBlocking { space.sync() }
    assertEquals(SyncFailure.Rejected("seal", "la chiave dell'app non c'e' ancora"), waiting.failure)
    assertEquals(1, space.state.value.pending)
    assertTrue(world.relay.rows(SyncSpaceId("spesa")).isEmpty())
    assertEquals(SyncState.ERROR, sync.status.value.state)
    assertEquals("Le modifiche aspettano la chiave dell'account: restano qui.", sync.status.value.message)
    unlocked = true
    assertNull(runBlocking { space.sync() }.failure)
    assertEquals(0, space.state.value.pending)
    assertEquals(1, world.relay.rows(SyncSpaceId("spesa")).size)
    world.close()
  }
}

private fun ByteArray.containsSlice(needle: ByteArray): Boolean {
  if (needle.isEmpty() || needle.size > size) return false
  outer@ for (i in 0..size - needle.size) {
    for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
    return true
  }
  return false
}
