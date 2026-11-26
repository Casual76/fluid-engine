package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.client.PeopleHttp
import dev.antigravity.fluidengine.people.client.PeopleRequest
import dev.antigravity.fluidengine.people.store.AppNotifier
import dev.antigravity.fluidengine.people.store.PeopleBell
import dev.antigravity.fluidengine.people.store.PeopleDispatcher
import dev.antigravity.fluidengine.people.store.PeopleProviderMethods
import dev.antigravity.fluidengine.people.store.StoreNotice
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import java.io.File
import java.io.IOException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo Store e le app, senza Android (`b\api.md` § 5.3; `d\server-e-chiavi.md` § 6): la fila delle persone con id per
 * app, mandare da Notes, il campanello finto che sveglia lo Store di chi riceve, la cosa che va a Notes e solo a Notes,
 * «Rispondi», «Togli dai condivisi», «I tuoi dispositivi», e lo Store che avvisa da sé quando l'app non c'è.
 */
class PeopleStoreTest {
  private class Notifier(val installed: Set<String>) : AppNotifier {
    val apps = mutableListOf<String>()
    val notices = mutableListOf<StoreNotice>()
    override fun notifyApp(packageName: String, what: String): Boolean {
      if (packageName !in installed) return false
      assertEquals(AccountProtocol.What.SHARE, what)
      apps += packageName
      return true
    }

    override fun show(notice: StoreNotice) {
      notices += notice
    }
  }

  private class StoreSide(val person: BenchPerson, device: Int = 0, installed: Set<String> = setOf(NOTES)) {
    val client = person.device(device).client
    val notifier = Notifier(installed)
    val methods = PeopleProviderMethods(client)
    val bell = PeopleBell(client, PeopleDispatcher(client, notifier, clock = { person.server.now }))
    suspend fun call(app: String, method: String, body: JsonObject = JsonObject(emptyMap())) = methods.handle(app, method, body)
  }

  private fun befriend(a: BenchPerson, b: BenchPerson) = runBlocking {
    a.client.request(a.client.lookup(b.code), "ciao")
    b.client.accept(b.client.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    a.client.pickup()
    b.client.pickup()
    for (d in a.devices + b.devices) d.client.pickup()
  }

  @Test
  fun `da Notes a Notes - la fila, il campanello, «Da Alessio», rispondi e togli`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val mamma = BenchPerson(server, "Mamma", deviceCount = 1)
    befriend(alessio, mamma)
    val storeA = StoreSide(alessio)
    val storeM = StoreSide(mamma)

    // La fila: «I tuoi dispositivi» in cima, poi la mamma. Gli id valgono solo per l'app che chiama.
    val row = storeA.call(NOTES, AccountProtocol.METHOD_PEOPLE_ROW)["row"]!!.jsonArray.map { it.jsonObject }
    assertEquals("devices", row[0]["kind"]!!.jsonPrimitive.content)
    assertEquals("I tuoi dispositivi", row[0]["name"]!!.jsonPrimitive.content)
    assertEquals("Mamma", row[1]["name"]!!.jsonPrimitive.content)
    assertFalse("niente codici, caselle o chiavi nella fila", row.toString().contains(mamma.code) || row.toString().contains(mamma.mailbox))
    val rowTransit = storeA.call(TRANSIT, AccountProtocol.METHOD_PEOPLE_ROW)["row"]!!.jsonArray.map { it.jsonObject }
    assertNotEquals(row[1]["id"], rowTransit[1]["id"])

    // Notes di Alessio manda una lista; un id di Transit non vale.
    val sent = storeA.call(NOTES, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject {
      put("to", row[1]["id"]!!.jsonPrimitive.content); put("kind", "live"); put("title", "La spesa"); put("payload", """{"lista":"spesa"}""")
    })
    assertEquals(true, (sent["ok"] as JsonPrimitive).content.toBoolean())
    assertEquals("unknown_person", storeA.call(NOTES, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject { put("to", rowTransit[1]["id"]!!.jsonPrimitive.content) })["reason"]!!.jsonPrimitive.content)

    // Il campanello finto sveglia lo Store della mamma, che apre e avvisa Notes, e solo Notes.
    assertEquals("high", server.ringsFor(mamma.first.device).last().priority)
    val woken = storeM.bell.ring(mapOf("k" to "posta"))
    assertEquals(setOf(NOTES), woken)
    assertEquals(listOf(NOTES), storeM.notifier.apps)
    val items = storeM.call(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED)["items"]!!.jsonArray.map { it.jsonObject }
    val item = items.single()
    assertEquals("La spesa", item["title"]!!.jsonPrimitive.content)
    assertEquals("Alessio", item["from"]!!.jsonObject["name"]!!.jsonPrimitive.content)
    assertEquals("""{"lista":"spesa"}""", item["payload"]!!.jsonPrimitive.content)
    assertTrue("Transit non vede le cose di Notes", (storeM.call(TRANSIT, AccountProtocol.METHOD_SHARE_RECEIVED)["items"] as JsonArray).isEmpty())

    // «Rispondi»: torna ad Alessio, attaccata alla cosa, e il suo Notes la riceve.
    storeM.call(NOTES, AccountProtocol.METHOD_SHARE_REPLY, buildJsonObject { put("item", item["id"]!!.jsonPrimitive.content); put("text", "Brava!") })
    storeA.bell.ring(mapOf("k" to "posta"))
    val back = storeA.call(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED)["items"]!!.jsonArray.map { it.jsonObject }.single()
    assertEquals(item["id"]!!.jsonPrimitive.content, back["inReplyTo"]!!.jsonPrimitive.content)
    assertTrue(back["payload"]!!.jsonPrimitive.content.contains("Brava!"))
    val mine = storeM.call(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED)["items"]!!.jsonArray.single().jsonObject["replies"]!!.jsonArray
    assertEquals("Brava!", mine.single().jsonObject["text"]!!.jsonPrimitive.content)

    // «Non mostrare più da Alessio in questa app»: sparisce, e la prossima non avvisa Notes.
    storeM.call(NOTES, AccountProtocol.METHOD_SHARE_HIDE, buildJsonObject { put("item", item["id"]!!.jsonPrimitive.content); put("mute", true) })
    assertTrue((storeM.call(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED)["items"] as JsonArray).isEmpty())
    storeA.call(NOTES, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject { put("to", row[1]["id"]!!.jsonPrimitive.content); put("title", "Altra") })
    storeM.notifier.apps.clear()
    storeM.bell.ring(mapOf("k" to "posta"))
    assertTrue(storeM.notifier.apps.isEmpty())
  }

  @Test
  fun `senza l'app giusta avvisa lo Store, e «I tuoi dispositivi» arriva al tablet`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val mamma = BenchPerson(server, "Mamma", deviceCount = 1)
    befriend(alessio, mamma)
    val phone = StoreSide(alessio, 0)
    val tablet = StoreSide(alessio, 1)
    val storeM = StoreSide(mamma, installed = emptySet())
    val row = phone.call(TRANSIT, AccountProtocol.METHOD_PEOPLE_ROW)["row"]!!.jsonArray.map { it.jsonObject }
    phone.call(TRANSIT, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject { put("to", row[1]["id"]!!.jsonPrimitive.content); put("kind", "live"); put("title", "Alessio è sul 12") })
    assertTrue(storeM.bell.ring(mapOf("k" to "posta")).contains(TRANSIT))
    assertTrue("la mamma non ha Transit: lo Store mostra la notifica", storeM.notifier.notices.any { it.app == TRANSIT })
    // «I tuoi dispositivi»: la pagina va al tablet, che la apre con un tocco.
    phone.call(TRANSIT, AccountProtocol.METHOD_DEVICES_SEND, buildJsonObject { put("address", "https://pampa-apps.com/a/transit/fermata/123"); put("title", "Fermata Duomo") })
    tablet.bell.ring(emptyMap())
    val n = tablet.notifier.notices.single()
    assertEquals("Fermata Duomo", n.title)
    assertEquals("https://pampa-apps.com/a/transit/fermata/123", n.address)
    // Il campanello dell'account (`k = account`) non è per le persone.
    assertTrue(tablet.bell.ring(mapOf("k" to "account")).isEmpty())
  }

  /**
   * R1 (Sol, bloccante): il ritiro a pagine. La prima pagina porta una condivisione, la seconda cade (rete, o la
   * scadenza del campanello FCM). Il cursore e gli id visti sono già salvati: la condivisione deve essere salvata con
   * loro, e arrivare a Notes al giro dopo, anche se lo Store nel frattempo è ripartito.
   */
  @Test
  fun `R1 - una condivisione letta prima di un errore del ritiro arriva al giro dopo, anche dopo un riavvio`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val mamma = BenchPerson(server, "Mamma", deviceCount = 1)
    befriend(alessio, mamma)
    val storeA = StoreSide(alessio)
    val to = storeA.call(NOTES, AccountProtocol.METHOD_PEOPLE_ROW)["row"]!!.jsonArray[1].jsonObject["id"]!!.jsonPrimitive.content
    for (title in listOf("Uno", "Due")) storeA.call(NOTES, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject { put("to", to); put("title", title) })

    // Il ritiro della mamma a pagine da una busta; la seconda pagina cade una volta.
    val d = mamma.first
    var pages = 0
    val flaky = PeopleHttp { r ->
      if (r.method == "GET" && r.path.startsWith("/v1/casella/buste") && !r.path.contains("limit=")) {
        pages++
        if (pages == 2) throw IOException("rete persa a metà ritiro")
        d.device.http.execute(PeopleRequest(r.method, r.path + "&limit=1", r.body, r.token))
      } else {
        d.device.http.execute(r)
      }
    }
    val notifier = Notifier(setOf(NOTES))
    val client = PeopleClient(d.persona, d.stateStore, PeopleApi(flaky, d.device.auth), clock = { server.now })
    val bell = PeopleBell(client, PeopleDispatcher(client, notifier, clock = { server.now }))
    assertTrue("il primo giro cade a metà", runCatching { bell.ring(mapOf("k" to "posta")) }.exceptionOrNull() is IOException)

    // Lo Store riparte: stato riletto dal disco, client e campanello nuovi.
    val restarted = PeopleClient(d.persona, PeopleStateStore(File(d.dir, "persone.bin"), d.vault), PeopleApi(flaky, d.device.auth), clock = { server.now })
    val bell2 = PeopleBell(restarted, PeopleDispatcher(restarted, notifier, clock = { server.now }))
    assertEquals(setOf(NOTES), bell2.ring(mapOf("k" to "posta")))
    val items = PeopleProviderMethods(restarted).handle(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED, JsonObject(emptyMap()))["items"]!!.jsonArray
    assertEquals(listOf("Uno", "Due"), items.map { it.jsonObject["title"]!!.jsonPrimitive.content })
    // Un altro giro non la ripete.
    restarted.pickup()
    bell2.ring(mapOf("k" to "posta"))
    assertEquals(2, PeopleProviderMethods(restarted).handle(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED, JsonObject(emptyMap()))["items"]!!.jsonArray.size)
  }

  /** R1: lo stesso tratto interrotto dalla scadenza del campanello (l'annullamento a metà del ritiro). */
  @Test
  fun `R1 - una condivisione letta prima della scadenza del campanello arriva al giro dopo`() = runBlocking {
    val server = FakePeopleServer()
    val alessio = BenchPerson(server, "Alessio")
    val mamma = BenchPerson(server, "Mamma", deviceCount = 1)
    befriend(alessio, mamma)
    val storeA = StoreSide(alessio)
    val to = storeA.call(NOTES, AccountProtocol.METHOD_PEOPLE_ROW)["row"]!!.jsonArray[1].jsonObject["id"]!!.jsonPrimitive.content
    for (title in listOf("Uno", "Due")) storeA.call(NOTES, AccountProtocol.METHOD_SHARE_SEND, buildJsonObject { put("to", to); put("title", title) })
    val d = mamma.first
    var pages = 0
    val slow = PeopleHttp { r ->
      if (r.method == "GET" && r.path.startsWith("/v1/casella/buste") && !r.path.contains("limit=")) {
        pages++
        if (pages == 2) awaitCancellation()
        d.device.http.execute(PeopleRequest(r.method, r.path + "&limit=1", r.body, r.token))
      } else {
        d.device.http.execute(r)
      }
    }
    val notifier = Notifier(setOf(NOTES))
    val client = PeopleClient(d.persona, d.stateStore, PeopleApi(slow, d.device.auth), clock = { server.now })
    val bell = PeopleBell(client, PeopleDispatcher(client, notifier, clock = { server.now }))
    assertEquals(null, withTimeoutOrNull(2_000) { bell.ring(mapOf("k" to "posta")) })
    bell.ring(mapOf("k" to "posta"))
    val items = PeopleProviderMethods(client).handle(NOTES, AccountProtocol.METHOD_SHARE_RECEIVED, JsonObject(emptyMap()))["items"]!!.jsonArray
    assertEquals(listOf("Uno", "Due"), items.map { it.jsonObject["title"]!!.jsonPrimitive.content })
  }

  companion object {
    const val NOTES = "dev.pampa.pampanotes"
    const val TRANSIT = "dev.antigravity.fluidtransit"
  }
}
