package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.people.client.ChannelClosed
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.client.PeopleAuth
import dev.antigravity.fluidengine.people.client.PeopleRequest
import dev.antigravity.fluidengine.people.client.PeopleSocket
import dev.antigravity.fluidengine.people.client.UrlPeopleHttp
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il client vero su HTTP vero: il server finto dietro MockWebServer, e il canale aperto col WebSocket di OkHttp. */
class PeopleHttpTest {

  private fun mock(fake: FakePeopleServer): MockWebServer = MockWebServer().apply {
    dispatcher = object : Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val body = request.body.readUtf8().takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it) as JsonObject }
        val token = request.getHeader("authorization")?.removePrefix("Bearer ")
        val r = runBlocking { fake.http.execute(PeopleRequest(request.method!!, request.path!!, body, token)) }
        return MockResponse().setResponseCode(r.status).setHeader("content-type", "application/json").setBody(r.json?.toString() ?: "{}")
      }
    }
    start()
  }

  private fun device(fake: FakePeopleServer, d: FakePeopleServer.Device, base: String, name: String): PeopleClient {
    val dir: File = Files.createTempDirectory("http").toFile()
    val vault = MemoryPersonVault()
    val persona = PersonaStore(dir, vault, clock = { fake.now })
    persona.create(name)
    return PeopleClient(persona, PeopleStateStore(File(dir, "s.bin"), vault), PeopleApi(UrlPeopleHttp(base, "1.0.0"), d.auth), clock = { fake.now })
  }

  @Test
  fun `richiesta, accettazione e messaggio passano da HTTP vero, con le intestazioni del contratto`() = runBlocking {
    val fake = FakePeopleServer()
    val server = mock(fake)
    try {
      val base = server.url("/").toString()
      val a = device(fake, fake.newAccount().devices[0], base, "Alessio")
      val b = device(fake, fake.newAccount().devices[0], base, "Marco")
      a.publish(); b.publish()
      a.request(a.lookup(b.identity().code), "ciao")
      b.accept(b.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
      a.pickup()
      b.pickup()
      assertEquals(a.words(b.identity().code)!!.words, b.words(a.identity().code)!!.words)
      a.sendMessage(b.identity().code, buildJsonObject { put("t", "messaggio"); put("text", "su HTTP") })
      assertEquals("su HTTP", b.pickup().filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)
      val recorded = (1..server.requestCount).map { server.takeRequest(1, TimeUnit.SECONDS)!! }
      assertTrue(recorded.all { it.getHeader("pampa-store-version") == "1.0.0" && it.getHeader("authorization")!!.startsWith("Bearer ") })
      // Il codice cercato sta nel corpo, mai nell'indirizzo.
      assertTrue(recorded.none { it.path!!.contains("PMP-") })
    } finally {
      server.shutdown()
    }
  }

  @Test
  fun `il canale aperto - «c'è posta», ping, e la chiusura del server`() = runBlocking {
    val server = MockWebServer()
    val received = CopyOnWriteArrayList<String>()
    val opened = CountDownLatch(1)
    var socket: WebSocket? = null
    server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        socket = webSocket
        opened.countDown()
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        received += text
        if (text == "ping") webSocket.send("pong")
      }
    }))
    server.start()
    try {
      val url = server.url("/v1/casella/ws").toString().replace("http://", "ws://")
      val ch = PeopleSocket(url, PeopleAuth { "tok_prova" }, "1.0.0", pingEveryMillis = 200)
      val mail = CopyOnWriteArrayList<Long>()
      val run = async(kotlinx.coroutines.Dispatchers.IO) { ch.run { mail += it } }
      assertTrue(opened.await(5, TimeUnit.SECONDS))
      socket!!.send("""{"t":"posta","n":7}""")
      socket!!.send("""{"t":"altro"}""")
      socket!!.send("""{"t":"posta","n":9}""")
      withTimeout(5_000) { while (mail.size < 2 || received.none { it == "ping" }) kotlinx.coroutines.delay(20) }
      assertEquals(listOf(7L, 9L), mail.toList())
      socket!!.close(4003, "dispositivo uscito")
      val closed: ChannelClosed = withTimeout(5_000) { run.await() }
      assertEquals(4003, closed.code)
      assertEquals("dispositivo uscito", closed.reason)
      val upgrade = server.takeRequest()
      assertEquals("Bearer tok_prova", upgrade.getHeader("authorization"))
      assertFalse(upgrade.path!!.contains("tok_prova"))
    } finally {
      server.shutdown()
    }
  }
}
