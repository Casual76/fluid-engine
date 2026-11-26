package dev.antigravity.fluidengine.people

import dev.antigravity.fluidengine.people.client.PeopleAuth
import dev.antigravity.fluidengine.people.client.PeopleSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R2 · Il canale aperto senza tempo di lettura (`PeopleSocket`): se la rete muore in silenzio (niente chiusura, niente
 * «pong»), la lettura restava appesa per sempre e il canale sembrava vivo. Ora scade dopo due ping e mezzo.
 */
class PeopleSocketR2Test {
  @Test
  fun `un canale che non risponde piu ai ping si chiude da solo`() = runBlocking {
    val server = MockWebServer()
    val opened = CountDownLatch(1)
    // Un server che apre il canale e poi tace: come una rete morta senza chiudere niente.
    server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        opened.countDown()
      }
    }))
    server.start()
    try {
      val url = server.url("/v1/casella/ws").toString().replace("http://", "ws://")
      val ch = PeopleSocket(url, PeopleAuth { "tok_prova" }, "1.0.0", pingEveryMillis = 200)
      val run = async(Dispatchers.IO) { ch.run { } }
      assertTrue(opened.await(5, TimeUnit.SECONDS))
      val closed = withTimeout(5_000) { run.await() }
      assertEquals(1006, closed.code)
    } finally {
      server.shutdown()
    }
  }
}
