package dev.antigravity.fluidengine.net

import java.io.File
import java.net.InetAddress
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * R2 · `EngineHttp` contro server finti in locale (MockWebServer): i rinvii a mano (relativi, verso un altro host, da
 * https a http) e i corpi che non si leggono per intero. Mai la rete vera.
 */
class EngineHttpTest {
  private lateinit var server: MockWebServer
  private lateinit var folder: File
  private val body = Random(4).nextBytes(200_000)

  @Before fun setUp() {
    server = MockWebServer()
    server.start(InetAddress.getByName("localhost"), 0)
    folder = Files.createTempDirectory("http").toFile()
  }

  @After fun tearDown() {
    server.shutdown()
    folder.deleteRecursively()
  }

  private fun file(): MockResponse = MockResponse().setBody(Buffer().write(body))

  /** Il server delle release: vuole il token, poi rimanda a [cdn]. */
  private fun release(cdn: String) = object : okhttp3.mockwebserver.Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
      if (request.getHeader("Authorization") != "token s3gr3to") MockResponse().setResponseCode(401)
      else MockResponse().setResponseCode(302).setHeader("Location", cdn)
  }

  private val credentials = mapOf("Authorization" to "token s3gr3to", "Cookie" to "sessione=1", "X-Traccia" to "t1")

  @Test fun `R2 un rinvio con Location relativa si segue`() = runBlocking {
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse =
        if (request.path == "/vecchio/notes.apk") MockResponse().setResponseCode(302).setHeader("Location", "/notes.apk") else file()
    }
    val saved = EngineHttp().download(server.url("/vecchio/notes.apk").toString(), File(folder, "notes.apk"))
    assertArrayEquals(body, saved.readBytes())
  }

  @Test fun `R2 le credenziali non seguono un rinvio verso un altro host`() = runBlocking {
    val cdn = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
    try {
      cdn.enqueue(file())
      server.dispatcher = release("http://127.0.0.1:${cdn.port}/cdn/notes.apk?firma=abc")
      val saved = EngineHttp().download("http://localhost:${server.port}/release/notes.apk", File(folder, "notes.apk"), headers = credentials)
      assertArrayEquals(body, saved.readBytes())
      val atCdn = cdn.takeRequest(5, TimeUnit.SECONDS)!!
      assertNull("il CDN non deve vedere il token", atCdn.getHeader("Authorization"))
      assertNull(atCdn.getHeader("Cookie"))
      assertEquals("le altre intestazioni restano", "t1", atCdn.getHeader("X-Traccia"))
    } finally {
      cdn.shutdown()
    }
  }

  @Test fun `R2 un rinvio sullo stesso server tiene le credenziali`() = runBlocking {
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = when {
        request.getHeader("Authorization") != "token s3gr3to" -> MockResponse().setResponseCode(401)
        request.path == "/vecchio/notes.apk" -> MockResponse().setResponseCode(301).setHeader("Location", "/notes.apk")
        else -> file()
      }
    }
    val saved = EngineHttp().download(server.url("/vecchio/notes.apk").toString(), File(folder, "notes.apk"), headers = credentials)
    assertArrayEquals(body, saved.readBytes())
  }

  @Test fun `R2 un rinvio da https a http si rifiuta`() = LocalTls.trusted {
    runBlocking {
      val plain = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
      val secure = MockWebServer().apply {
        useHttps(LocalTls.serverSockets, false)
        start(InetAddress.getByName("localhost"), 0)
      }
      try {
        plain.enqueue(file())
        secure.dispatcher = release("http://127.0.0.1:${plain.port}/cdn/notes.apk")
        val error = runCatching {
          EngineHttp().download("https://localhost:${secure.port}/release/notes.apk", File(folder, "notes.apk"), headers = credentials)
        }.exceptionOrNull()
        assertNotNull("il rinvio in chiaro e' stato seguito", error)
        assertEquals("il server in chiaro non va nemmeno toccato", 0, plain.requestCount)
        assertTrue(!File(folder, "notes.apk").exists())
      } finally {
        plain.shutdown()
        secure.shutdown()
      }
    }
  }

  @Test fun `R2 readText non legge un corpo enorme per intero`() = runBlocking {
    server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(9 * 1024 * 1024) { 'a'.code.toByte() })))
    val error = runCatching { EngineHttp().readText(server.url("/manifest.json").toString()) }.exceptionOrNull()
    assertTrue("un manifest di 9 MiB e' passato: $error", error is IllegalStateException)
  }

  @Test fun `R2 readText non legge il corpo di un errore`() = runBlocking {
    // Un corpo d'errore che arriva piano (200 KiB in una ventina di secondi): non serve a niente, non si aspetta.
    server.enqueue(
      MockResponse().setResponseCode(500).setBody(Buffer().write(ByteArray(200 * 1024))).throttleBody(1024, 100, TimeUnit.MILLISECONDS),
    )
    val started = System.nanoTime()
    val error = runCatching { EngineHttp().readText(server.url("/manifest.json").toString()) }.exceptionOrNull()
    val millis = (System.nanoTime() - started) / 1_000_000L
    assertTrue("$error", error is IllegalStateException)
    assertTrue("letto per $millis ms", millis < 5_000L)
  }

  @Test fun `R2 readText legge un manifest normale`() = runBlocking {
    server.enqueue(MockResponse().setBody("""{"versione":"1.0","nota":"è così"}"""))
    assertEquals("""{"versione":"1.0","nota":"è così"}""", EngineHttp().readText(server.url("/manifest.json").toString()))
  }
}
