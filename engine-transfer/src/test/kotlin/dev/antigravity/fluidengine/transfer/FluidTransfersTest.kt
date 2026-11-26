package dev.antigravity.fluidengine.transfer

import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * F8.20 · La coda dei trasferimenti contro un server vero (MockWebServer): pausa e ripresa con `Range`, un server che non
 * la accetta, un file cambiato sul server (`If-Range`), il riavvio a meta', gli errori e i nuovi tentativi, le condizioni
 * (rete a consumo, solo Wi-Fi, «Fallo comunque», il Wi-Fi che se ne va a meta'), il caricamento a pezzi.
 */
class FluidTransfersTest {
  private lateinit var server: MockWebServer
  private lateinit var folder: File
  private val content = Random(20).nextBytes(1_500_000)
  private var conditions = FluidConditions(online = true, connected = true)
  private val opened = ArrayList<FluidTransfers>()

  @Before fun setUp() {
    server = MockWebServer()
    server.start()
    folder = Files.createTempDirectory("transfer").toFile()
  }

  @After fun tearDown() {
    opened.forEach { it.close() }
    server.shutdown()
    folder.deleteRecursively()
  }

  private fun queue(
    store: FluidTransferStore = InMemoryFluidTransferStore(),
    retries: Int = 3,
    scheduler: FluidTransferScheduler = FluidTransferScheduler.None,
  ): FluidTransfers =
    FluidTransfers(
      store = store,
      scheduler = scheduler,
      conditions = { conditions },
      config = FluidTransferConfig(maxAutoRetries = retries, progressIntervalMillis = 50L, conditionsCheckMillis = 50L),
      dispatcher = Dispatchers.IO,
      conditionChanges = null,
    ).also(opened::add)

  /** Un server di file che sa `Range` e `If-Range` (o no), un po' lento: si fa in tempo a fermarlo a meta'. */
  private inner class FileServer(
    var body: ByteArray = content,
    var ranges: Boolean = true,
    /** Null: il server non da' validatori (niente `ETag`, niente `Last-Modified`). */
    var etag: String? = "\"v1\"",
    var bytesPerTick: Long = 32 * 1024,
    /** R2 · Un server scorretto: onora `Range` ma ignora `If-Range` (risponde 206 anche dalla versione nuova). */
    var ignoreIfRange: Boolean = false,
    /** R2 · Il corpo a pezzi, senza la misura (`Transfer-Encoding: chunked`): il totale non si sa. */
    var chunked: Boolean = false,
  ) : okhttp3.mockwebserver.Dispatcher() {
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    var failNext = 0
    var failCode = 503

    override fun dispatch(request: RecordedRequest): MockResponse {
      requests += request
      if (failNext > 0) {
        failNext--
        return MockResponse().setResponseCode(failCode)
      }
      val range = request.getHeader("Range")
      val ifRange = request.getHeader("If-Range")
      val honor = ranges && range != null && (ignoreIfRange || ifRange == null || ifRange == etag)
      if (honor) {
        val start = range!!.removePrefix("bytes=").substringBefore('-').toInt()
        if (start >= body.size) return MockResponse().setResponseCode(416)
        return MockResponse().setResponseCode(206)
          .setHeader("Content-Range", "bytes $start-${body.size - 1}/${body.size}")
          .apply { etag?.let { setHeader("ETag", it) } }
          .setBody(Buffer().write(body.copyOfRange(start, body.size)))
          .throttleBody(bytesPerTick, 20, TimeUnit.MILLISECONDS)
      }
      return MockResponse().setResponseCode(200)
        .apply { etag?.let { setHeader("ETag", it) } }
        .apply { if (ranges) setHeader("Accept-Ranges", "bytes") }
        .apply { if (chunked) setChunkedBody(Buffer().write(body), 16 * 1024) else setBody(Buffer().write(body)) }
        .throttleBody(bytesPerTick, 20, TimeUnit.MILLISECONDS)
    }
  }

  private fun FluidTransfers.waitFor(id: String, timeoutMillis: Long = 10_000, predicate: (FluidTransferItem) -> Boolean): FluidTransferItem {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      item(id)?.takeIf(predicate)?.let { return it }
      Thread.sleep(10)
    }
    error("Il trasferimento $id non e' arrivato: ${item(id)}")
  }

  private fun download(id: String = "apk", wifiOnly: Boolean = false, expected: Long = -1L, sha: String? = null) =
    FluidTransferRequest.download(
      url = server.url("/notes.apk").toString(),
      target = File(folder, "$id.apk"),
      title = "Pampa Notes 2.4",
      id = id,
      wifiOnly = wifiOnly,
      expectedBytes = expected,
      sha256 = sha,
    )

  @Test fun `scarica tutto, con la velocita' e il tempo che manca`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download(expected = content.size.toLong(), sha = FluidTransfers.sha256Of(content.toTempFile())))
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    val running = transfers.waitFor("apk") { it.state == FluidTransferState.Running && it.bytesPerSecond > 0L }
    assertTrue(running.bytesDone > 0L)
    val result = withTimeout(20_000) { run.await() }
    assertEquals(1, result.completed)
    val done = transfers.item("apk")!!
    assertEquals(FluidTransferState.Completed, done.state)
    assertEquals(content.size.toLong(), done.bytesDone)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    assertFalse(FluidTransfers.partFile(download()).exists())
    assertEquals("identity", files.requests.first().getHeader("Accept-Encoding"))
  }

  @Test fun `pausa e ripresa con Range e If-Range`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    transfers.pause("apk")
    withTimeout(10_000) { run.await() }
    val paused = transfers.item("apk")!!
    assertEquals(FluidTransferState.Paused, paused.state)
    val part = FluidTransfers.partFile(download())
    assertEquals(paused.bytesDone, part.length())
    assertTrue(paused.bytesDone in 200_000 until content.size.toLong())

    transfers.resume("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    val second = files.requests.last()
    assertEquals("bytes=${paused.bytesDone}-", second.getHeader("Range"))
    assertEquals("\"v1\"", second.getHeader("If-Range"))
    assertTrue(transfers.item("apk")!!.resumable)
  }

  @Test fun `un server che non accetta Range ricomincia da capo, giusto`() = runBlocking {
    val files = FileServer(ranges = false)
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    transfers.pause("apk")
    withTimeout(10_000) { run.await() }
    transfers.resume("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    val done = transfers.item("apk")!!
    assertEquals(FluidTransferState.Completed, done.state)
    assertFalse(done.resumable)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    assertNotNull(files.requests.last().getHeader("Range"))
  }

  @Test fun `un file cambiato sul server non si cuce a meta'`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    transfers.pause("apk")
    withTimeout(10_000) { run.await() }
    val changed = Random(99).nextBytes(1_200_000)
    files.body = changed
    files.etag = "\"v2\""
    transfers.resume("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertArrayEquals(changed, File(folder, "apk.apk").readBytes())
  }

  @Test fun `il processo muore a meta' e una coda nuova riprende da li'`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val store = InMemoryFluidTransferStore()
    val first = queue(store)
    first.enqueue(download())
    first.settle()
    val run = async(Dispatchers.IO) { runCatching { first.runQueue() } }
    first.waitFor("apk") { it.bytesDone > 300_000 }
    // Il processo «muore»: la coda si chiude dov'e', senza pausa.
    first.close()
    withTimeout(10_000) { run.await() }
    val saved = store.load().single()
    assertTrue(saved.state == FluidTransferState.Running || saved.state == FluidTransferState.Queued)
    val part = FluidTransfers.partFile(download()).length()
    assertTrue(part > 0L)

    val second = queue(store)
    second.settle()
    assertEquals(FluidTransferState.Queued, second.item("apk")!!.state)
    withTimeout(20_000) { second.runQueue() }
    assertEquals(FluidTransferState.Completed, second.item("apk")!!.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    assertEquals("bytes=$part-", files.requests.last().getHeader("Range"))
  }

  @Test fun `un errore che passa si riprova da solo, poi Riprova`() = runBlocking {
    val files = FileServer(bytesPerTick = 512 * 1024)
    files.failNext = 3
    server.dispatcher = files
    val transfers = queue(retries = 2)
    transfers.enqueue(download())
    transfers.settle()
    var result = transfers.runQueue()
    assertTrue(result.retryLater)
    var item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Queued, item.state)
    assertEquals(FluidTransferFailure.Server, item.failure?.kind)
    assertEquals(503, item.failure?.code)
    assertEquals(1, item.attempts)
    result = transfers.runQueue()
    assertTrue(result.retryLater)
    transfers.runQueue()
    item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Failed, item.state)
    assertEquals(3, item.attempts)
    transfers.retry("apk")
    transfers.settle()
    transfers.runQueue()
    item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Completed, item.state)
    assertNull(item.failure)
  }

  @Test fun `un file che non c'e' non si riprova da solo`() = runBlocking {
    val files = FileServer()
    files.failNext = 1
    files.failCode = 404
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val result = transfers.runQueue()
    assertFalse(result.retryLater)
    val item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Failed, item.state)
    assertEquals(FluidTransferFailure.NotFound, item.failure?.kind)
  }

  @Test fun `un file diverso da quello atteso non e' riuscito`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 512 * 1024)
    val transfers = queue(retries = 0)
    transfers.enqueue(download(expected = 10L))
    transfers.settle()
    transfers.runQueue()
    val item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Failed, item.state)
    assertEquals(FluidTransferFailure.Integrity, item.failure?.kind)
    assertFalse(File(folder, "apk.apk").exists())
  }

  @Test fun `su una rete a consumo aspetta, dice il perche', e Fallo comunque lo fa partire`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 512 * 1024)
    conditions = FluidConditions(online = true, connected = true, metered = true)
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val result = transfers.runQueue()
    assertEquals(1, result.waiting)
    var item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Waiting, item.state)
    assertEquals(listOf(FluidConditionReason.Metered), item.waitingFor)
    assertTrue(item.canForce)
    val plan = transfers.plan()
    assertTrue(plan.unmetered)
    assertFalse(plan.runNow)
    transfers.force("apk")
    transfers.settle()
    assertTrue(transfers.plan().runNow)
    transfers.runQueue()
    item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Completed, item.state)
  }

  @Test fun `solo Wi-Fi e senza rete`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 512 * 1024)
    conditions = FluidConditions(online = true, connected = true, metered = true)
    val transfers = queue()
    transfers.enqueue(FluidTransferRequest.download(server.url("/a").toString(), File(folder, "a"), "a", id = "a", wifiOnly = true, work = dev.antigravity.fluidengine.foundation.FluidWork.Sync))
    transfers.settle()
    transfers.runQueue()
    assertEquals(listOf(FluidConditionReason.NotWifi), transfers.item("a")!!.waitingFor)
    conditions = FluidConditions.Offline
    transfers.runQueue()
    val offline = transfers.item("a")!!
    assertEquals(FluidConditionReason.Offline, offline.waitingFor.first())
    assertFalse(offline.canForce)
    conditions = FluidConditions(online = true, connected = true)
    transfers.runQueue()
    assertEquals(FluidTransferState.Completed, transfers.item("a")!!.state)
  }

  @Test fun `il Wi-Fi se ne va a meta', aspetta, poi riprende da li'`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    conditions = FluidConditions(online = true, connected = true, metered = true)
    withTimeout(10_000) { run.await() }
    val waiting = transfers.item("apk")!!
    assertEquals(FluidTransferState.Waiting, waiting.state)
    assertEquals(listOf(FluidConditionReason.Metered), waiting.waitingFor)
    conditions = FluidConditions(online = true, connected = true)
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    assertEquals("bytes=${waiting.bytesDone}-", files.requests.last().getHeader("Range"))
  }

  @Test fun `annulla toglie il file a meta'`() = runBlocking {
    server.dispatcher = FileServer()
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 100_000 }
    transfers.cancel("apk")
    withTimeout(10_000) { run.await() }
    assertEquals(FluidTransferState.Cancelled, transfers.item("apk")!!.state)
    assertFalse(FluidTransfers.partFile(download()).exists())
    transfers.clearFinished()
    transfers.settle()
    assertNull(transfers.item("apk"))
  }

  @Test fun `la coda vuota toglie il giro rimasto in attesa`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 512 * 1024)
    conditions = FluidConditions(online = true, connected = true, metered = true)
    val plans = CopyOnWriteArrayList<FluidTransferPlan>()
    val transfers = queue(scheduler = { plans += it })
    transfers.enqueue(download())
    transfers.settle()
    transfers.runQueue()
    assertEquals(FluidTransferState.Waiting, transfers.item("apk")!!.state)
    assertTrue(plans.none { it.idle })
    // Annullato chi aspettava, la coda non ha piu' niente da fare: il piano vuoto toglie il giro rimandato.
    transfers.cancel("apk")
    transfers.settle()
    assertTrue(plans.last().idle)
    // Lo stesso togliendolo dalla lista.
    transfers.enqueue(download(id = "b"))
    transfers.settle()
    transfers.runQueue()
    assertFalse(plans.last().idle)
    transfers.remove("b")
    transfers.settle()
    assertTrue(plans.last().idle)
  }

  @Test fun `l'avanzamento non si scrive su disco a ogni pezzo`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 16 * 1024)
    val store = InMemoryFluidTransferStore()
    val transfers = FluidTransfers(
      store = store,
      conditions = { conditions },
      config = FluidTransferConfig(progressIntervalMillis = 50L, persistIntervalMillis = 2_000L),
      conditionChanges = null,
    ).also(opened::add)
    transfers.enqueue(download())
    transfers.settle()
    withTimeout(30_000) { transfers.runQueue() }
    // ~1,5 MB a pezzi da 16 KiB sono un centinaio di letture: su disco vanno gli stati e un avanzamento ogni due secondi.
    assertTrue("scritture: ${store.writes}", store.writes < 20)
  }

  @Test fun `caricamento a pezzi, pausa, il server dice a che punto e', ripresa`() = runBlocking {
    val upload = Random(7).nextBytes(5 * 256 * 1024 + 1234)
    val source = File(folder, "video.mp4").apply { writeBytes(upload) }
    val received = java.io.ByteArrayOutputStream()
    val requests = CopyOnWriteArrayList<RecordedRequest>()
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        requests += request
        val range = request.getHeader("Content-Range") ?: return MockResponse().setResponseCode(400)
        if (range.startsWith("bytes */")) {
          val acked = received.size()
          return if (acked == 0) MockResponse().setResponseCode(308)
          else MockResponse().setResponseCode(308).setHeader("Range", "bytes=0-${acked - 1}")
        }
        val (start, end, total) = parseContentRange(range)!!
        if (start != received.size().toLong()) return MockResponse().setResponseCode(400)
        received.write(request.body.readByteArray())
        Thread.sleep(60)
        return if (end + 1 == total) MockResponse().setResponseCode(201)
        else MockResponse().setResponseCode(308).setHeader("Range", "bytes=0-$end")
      }
    }
    val transfers = FluidTransfers(
      store = InMemoryFluidTransferStore(),
      conditions = { conditions },
      config = FluidTransferConfig(uploadChunkBytes = 256 * 1024, progressIntervalMillis = 20L),
      conditionChanges = null,
    ).also(opened::add)
    transfers.enqueue(FluidTransferRequest.upload(server.url("/session/1").toString(), source, "Video della gita", id = "up", resumable = true))
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("up") { it.bytesDone >= 2 * 256 * 1024 }
    transfers.pause("up")
    withTimeout(10_000) { run.await() }
    assertEquals(FluidTransferState.Paused, transfers.item("up")!!.state)
    transfers.resume("up")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("up")!!.state)
    assertArrayEquals(upload, received.toByteArray())
    assertTrue(requests.any { it.getHeader("Content-Range")!!.startsWith("bytes */") })
  }

  @Test fun `la velocita' dimentica piano e il tempo che manca aspetta le misure`() {
    val rate = TransferRate(tauMillis = 1_000.0, warmupMillis = 1_000L)
    rate.reset(0L, 0L)
    rate.sample(200L, 200_000L)
    assertNull(rate.remainingMillis(1_000_000L, 200L))
    rate.sample(1_200L, 1_200_000L)
    assertEquals(1_000_000L, rate.bytesPerSecond)
    assertEquals(1_000L, rate.remainingMillis(1_000_000L, 1_200L))
    rate.sample(2_200L, 1_200_000L) // un secondo fermo: la media scende, non crolla
    assertTrue(rate.bytesPerSecond in 300_000L..700_000L)
  }

  @Test fun `le intestazioni dei pezzi`() {
    assertEquals(Triple(100L, 199L, 1000L), parseContentRange("bytes 100-199/1000"))
    assertEquals(-1L, parseContentRange("bytes 0-9/*")!!.third)
    assertNull(parseContentRange("garbage"))
    assertEquals(1048575L, parseAckedRange("bytes=0-1048575"))
    assertNull(FluidTransfers.strongEtag("W/\"weak\""))
    assertEquals("\"v1\"", FluidTransfers.strongEtag("\"v1\""))
  }

  private fun ByteArray.toTempFile(): File = File(folder, "check.bin").apply { writeBytes(this@toTempFile) }

  // ---------------------------------------------------------------------------------------------------------------
  // R2 · Rete e trasferimenti (muse-f8-prime, 15-20 e i minori): server scorretti, rinvii, disco pieno.
  // ---------------------------------------------------------------------------------------------------------------

  private fun queueWith(
    authorizer: FluidTransferAuthorizer = FluidTransferAuthorizer.None,
    store: FluidTransferStore = InMemoryFluidTransferStore(),
    config: FluidTransferConfig = FluidTransferConfig(progressIntervalMillis = 50L, conditionsCheckMillis = 50L),
  ): FluidTransfers =
    FluidTransfers(
      store = store,
      conditions = { conditions },
      authorizer = authorizer,
      config = config,
      dispatcher = Dispatchers.IO,
      conditionChanges = null,
    ).also(opened::add)

  /** Le credenziali dell'app: tutto quello che da' l'authorizer e' segreto, anche un'intestazione sua. */
  private val secret = FluidTransferAuthorizer { mapOf("Authorization" to "Bearer s3gr3to", "X-Api-Key" to "chiave") }

  /** Il server delle release: vuole la credenziale, poi rimanda al CDN ([cdn]) con un indirizzo firmato. */
  private fun releaseServer(cdn: String) = object : okhttp3.mockwebserver.Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
      if (request.getHeader("Authorization") != "Bearer s3gr3to") MockResponse().setResponseCode(401)
      else MockResponse().setResponseCode(302).setHeader("Location", cdn)
  }

  private fun downloadFrom(url: String, id: String = "apk", target: File = File(folder, "$id.apk"), title: String = "Pampa Notes 2.4") =
    FluidTransferRequest.download(url = url, target = target, title = title, id = id)

  @Test fun `R2 le credenziali non seguono un rinvio verso un altro host`() = runBlocking {
    val cdn = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
    try {
      val files = FileServer(bytesPerTick = 512 * 1024)
      cdn.dispatcher = files
      server.dispatcher = releaseServer("http://127.0.0.1:${cdn.port}/cdn/notes.apk?firma=abc")
      val transfers = queueWith(authorizer = secret)
      transfers.enqueue(downloadFrom("http://localhost:${server.port}/release/notes.apk"))
      transfers.settle()
      withTimeout(20_000) { transfers.runQueue() }
      assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
      assertArrayEquals(content, File(folder, "apk.apk").readBytes())
      val atCdn = files.requests.single()
      assertNull("il CDN non deve vedere la credenziale", atCdn.getHeader("Authorization"))
      assertNull("nemmeno un'intestazione dell'authorizer", atCdn.getHeader("X-Api-Key"))
      assertEquals("identity", atCdn.getHeader("Accept-Encoding"))
    } finally {
      cdn.shutdown()
    }
  }

  @Test fun `R2 un rinvio sullo stesso server tiene le credenziali`() = runBlocking {
    val files = FileServer(bytesPerTick = 512 * 1024)
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse = when {
        request.getHeader("Authorization") != "Bearer s3gr3to" -> MockResponse().setResponseCode(401)
        request.path == "/vecchio/notes.apk" -> MockResponse().setResponseCode(301).setHeader("Location", "/notes.apk")
        else -> files.dispatch(request)
      }
    }
    val transfers = queueWith(authorizer = secret)
    transfers.enqueue(downloadFrom(server.url("/vecchio/notes.apk").toString()))
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertEquals("Bearer s3gr3to", files.requests.single().getHeader("Authorization"))
  }

  @Test fun `R2 un rinvio da https a http si rifiuta e la credenziale resta a casa`() = LocalTls.trusted {
    runBlocking {
      val plain = MockWebServer().apply { start(InetAddress.getByName("127.0.0.1"), 0) }
      val secure = MockWebServer().apply {
        useHttps(LocalTls.serverSockets, false)
        start(InetAddress.getByName("localhost"), 0)
      }
      try {
        val files = FileServer(bytesPerTick = 512 * 1024)
        plain.dispatcher = files
        secure.dispatcher = releaseServer("http://127.0.0.1:${plain.port}/cdn/notes.apk")
        val transfers = queueWith(authorizer = secret)
        transfers.enqueue(downloadFrom("https://localhost:${secure.port}/release/notes.apk"))
        transfers.settle()
        val result = withTimeout(20_000) { transfers.runQueue() }
        assertEquals("il server in chiaro non va nemmeno toccato", 0, files.requests.size)
        assertEquals(1, secure.requestCount)
        val item = transfers.item("apk")!!
        assertEquals(FluidTransferState.Failed, item.state)
        assertFalse(result.retryLater)
      } finally {
        plain.shutdown()
        secure.shutdown()
      }
    }
  }

  @Test fun `R2 un rinvio permanente 308 si segue`() = runBlocking {
    val files = FileServer(bytesPerTick = 512 * 1024)
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse =
        if (request.path == "/vecchio.apk") MockResponse().setResponseCode(308).setHeader("Location", "/notes.apk")
        else files.dispatch(request)
    }
    val transfers = queue()
    transfers.enqueue(downloadFrom(server.url("/vecchio.apk").toString()))
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    val item = transfers.item("apk")!!
    assertEquals("${item.failure}", FluidTransferState.Completed, item.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
  }

  @Test fun `R2 il disco pieno a meta' e' il disco e non la rete, e non si riprova da solo`() = runBlocking {
    val files = FileServer(bytesPerTick = 128 * 1024)
    server.dispatcher = files
    val transfers = queue()
    val room = 300_000L
    transfers.partOutput = { file, append -> FullDisk(FileOutputStream(file, append), room) }
    transfers.enqueue(download())
    transfers.settle()
    val result = withTimeout(20_000) { transfers.runQueue() }
    val item = transfers.item("apk")!!
    assertFalse("un disco pieno non passa da solo: niente nuovi tentativi", result.retryLater)
    assertEquals(FluidTransferState.Failed, item.state)
    assertEquals(FluidTransferFailure.Storage, item.failure?.kind)
    assertEquals(1, item.attempts)
    // Fatto spazio, «Riprova» riprende da dove il disco si era fermato.
    transfers.partOutput = { file, append -> FileOutputStream(file, append) }
    val kept = folder.listFiles()!!.single { it.name.endsWith(".part") }.length()
    assertTrue("parziale tenuto: $kept", kept in 1..room)
    transfers.retry("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
    assertEquals("bytes=$kept-", files.requests.last().getHeader("Range"))
  }

  @Test fun `R2 un server che ignora If-Range non cuce due versioni`() = runBlocking {
    val files = FileServer(ignoreIfRange = true)
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    transfers.pause("apk")
    withTimeout(10_000) { run.await() }
    // Il file cambia sul server durante la pausa, con la stessa misura: lo dice solo l'ETag.
    val changed = Random(99).nextBytes(content.size)
    files.body = changed
    files.etag = "\"v2\""
    transfers.resume("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(changed, File(folder, "apk.apk").readBytes())
  }

  @Test fun `R2 un server senza validatori che cambia misura fa ricominciare da capo`() = runBlocking {
    val files = FileServer(etag = null)
    server.dispatcher = files
    val transfers = queue()
    transfers.enqueue(download())
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("apk") { it.bytesDone > 200_000 }
    transfers.pause("apk")
    withTimeout(10_000) { run.await() }
    val changed = Random(98).nextBytes(1_200_000)
    files.body = changed
    transfers.resume("apk")
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(changed, File(folder, "apk.apk").readBytes())
  }

  @Test fun `R2 due trasferimenti verso lo stesso file non si cuciono`() = runBlocking {
    val files = FileServer()
    server.dispatcher = files
    val transfers = queue()
    val target = File(folder, "notes.apk")
    transfers.enqueue(downloadFrom(server.url("/notes.apk").toString(), id = "v1", target = target))
    transfers.settle()
    val run = async(Dispatchers.IO) { transfers.runQueue() }
    transfers.waitFor("v1") { it.bytesDone > 200_000 }
    transfers.pause("v1")
    withTimeout(10_000) { run.await() }
    val paused = transfers.item("v1")!!.bytesDone
    // Esce la versione nuova: l'app chiede lei, con un altro id, sullo stesso file.
    val v2 = Random(97).nextBytes(content.size)
    files.body = v2
    files.etag = "\"v2\""
    transfers.enqueue(downloadFrom(server.url("/notes.apk").toString(), id = "v2", target = target, title = "Pampa Notes 2.5"))
    transfers.settle()
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("v2")!!.state)
    assertArrayEquals(v2, target.readBytes())
    // Il parziale di v1 c'e' ancora, intero: v1 puo' riprendere da li'.
    assertEquals(listOf(paused), folder.listFiles()!!.filter { it.name.endsWith(".part") }.map { it.length() })
  }

  @Test fun `R2 un trasferimento non riuscito ha l'ora di fine e l'esito lo dice`() = runBlocking {
    val files = FileServer()
    files.failNext = 1
    files.failCode = 404
    server.dispatcher = files
    val transfers = queue()
    val since = System.currentTimeMillis()
    transfers.enqueue(download())
    transfers.settle()
    transfers.runQueue()
    val item = transfers.item("apk")!!
    assertEquals(FluidTransferState.Failed, item.state)
    assertNotNull("finishedAtMillis", item.finishedAtMillis)
    val outcome = fluidTransferOutcome(transfers.overview.value, since)
    assertTrue("$outcome", outcome.failed)
    assertEquals("Pampa Notes 2.4 non riuscito", outcome.message)
  }

  @Test fun `R2 una coda salvata che non scrive non nasconde il trasferimento`() = runBlocking {
    server.dispatcher = FileServer(bytesPerTick = 512 * 1024)
    val broken = object : FluidTransferStore {
      override fun load(): List<FluidTransferRecord> = emptyList()
      override fun save(record: FluidTransferRecord) {
        throw IllegalStateException("database or disk is full")
      }
      override fun delete(id: String) {
        throw IllegalStateException("database or disk is full")
      }
    }
    val transfers = queue(store = broken)
    transfers.enqueue(download())
    transfers.settle()
    assertEquals(FluidTransferState.Queued, transfers.item("apk")?.state)
    withTimeout(20_000) { transfers.runQueue() }
    assertEquals(FluidTransferState.Completed, transfers.item("apk")!!.state)
    assertArrayEquals(content, File(folder, "apk.apk").readBytes())
  }

  @Test fun `R2 Annulla che arriva all'ultima lettura annulla davvero`() = runBlocking {
    server.dispatcher = FileServer(chunked = true)
    // L'avanzamento arriva solo la prima volta e alla fine: Annulla cade dopo l'ultima lettura, con il totale ignoto.
    val transfers = queueWith(config = FluidTransferConfig(progressIntervalMillis = 600_000L, conditionsCheckMillis = 600_000L))
    transfers.enqueue(download())
    transfers.settle()
    withTimeout(20_000) {
      transfers.runQueue { if (transfers.item("apk")?.bytesDone == content.size.toLong()) transfers.cancel("apk") }
    }
    transfers.settle()
    assertEquals(FluidTransferState.Cancelled, transfers.item("apk")!!.state)
    assertFalse(File(folder, "apk.apk").exists())
    assertTrue(folder.listFiles()!!.none { it.name.endsWith(".part") })
  }

  @Test fun `R2 caricamento a pezzi, prima che il server abbia il primo pezzo su disco c'e' gia' l'avanzamento`() = runBlocking {
    val upload = Random(8).nextBytes(3 * 256 * 1024)
    val source = File(folder, "video.mp4").apply { writeBytes(upload) }
    val store = InMemoryFluidTransferStore()
    var snapshot: InMemoryFluidTransferStore? = null
    val received = java.io.ByteArrayOutputStream()
    val requests = CopyOnWriteArrayList<String>()
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse {
        val range = request.getHeader("Content-Range") ?: return MockResponse().setResponseCode(400)
        requests += range
        if (range.startsWith("bytes */")) {
          val acked = received.size()
          return if (acked == 0) MockResponse().setResponseCode(308)
          else MockResponse().setResponseCode(308).setHeader("Range", "bytes=0-${acked - 1}")
        }
        val (start, end, total) = parseContentRange(range)!!
        if (start != received.size().toLong()) return MockResponse().setResponseCode(400)
        received.write(request.body.readByteArray())
        if (snapshot == null) {
          // Il server ha il primo pezzo intero e il processo «muore» qui, prima di leggere la risposta: cosa c'e' su disco?
          snapshot = InMemoryFluidTransferStore().apply { store.load().forEach(::save) }
          return MockResponse().setResponseCode(503)
        }
        return if (end + 1 == total) MockResponse().setResponseCode(201)
        else MockResponse().setResponseCode(308).setHeader("Range", "bytes=0-$end")
      }
    }
    val config = FluidTransferConfig(uploadChunkBytes = 256 * 1024)
    val first = queueWith(store = store, config = config)
    first.enqueue(FluidTransferRequest.upload(server.url("/session/1").toString(), source, "Video della gita", id = "up", resumable = true))
    first.settle()
    withTimeout(20_000) { first.runQueue() }
    first.close()
    val saved = snapshot!!.load().single()
    assertTrue("su disco prima del primo pezzo: ${saved.bytesDone}", saved.bytesDone > 0L)
    // La coda nuova, dopo il riavvio, chiede prima a che punto e' il server: il primo pezzo non si rimanda.
    requests.clear()
    val second = queueWith(store = snapshot!!, config = config)
    second.settle()
    withTimeout(20_000) { second.runQueue() }
    assertEquals(FluidTransferState.Completed, second.item("up")!!.state)
    assertTrue(requests.first(), requests.first().startsWith("bytes */"))
    assertArrayEquals(upload, received.toByteArray())
  }

  @Test fun `R2 dove porta un rinvio e a chi vanno le credenziali`() {
    val https = URL("https://example.org/a/b.apk")
    assertEquals("https://example.org/c.apk", redirectTarget(https, "/c.apk").toString())
    assertEquals("https://example.org/a/d.apk", redirectTarget(https, "d.apk").toString())
    assertEquals("https://cdn.example.org/x?firma=1", redirectTarget(https, "https://cdn.example.org/x?firma=1").toString())
    assertNull("da https a http no", redirectTarget(https, "http://cdn.example.org/x"))
    assertNull("un protocollo che non e' http no", redirectTarget(URL("http://example.org/"), "file:///data/data/app/x"))
    assertEquals("da http a https si'", "https://example.org/y", redirectTarget(URL("http://example.org/"), "https://example.org/y").toString())
    assertTrue(sameOrigin(URL("https://example.org/a"), URL("https://EXAMPLE.org:443/b")))
    assertFalse("lo schema conta", sameOrigin(URL("http://example.org/a"), URL("https://example.org/a")))
    assertFalse("la porta conta", sameOrigin(URL("https://example.org/a"), URL("https://example.org:8443/a")))
    assertFalse("l'host conta", sameOrigin(URL("https://example.org/a"), URL("https://cdn.example.org/a")))
  }

  @Test fun `R2 ogni trasferimento ha il suo file a meta'`() {
    val one = FluidTransfers.partFile(download(id = "v1").copy(file = File(folder, "notes.apk")))
    val two = FluidTransfers.partFile(download(id = "v2").copy(file = File(folder, "notes.apk")))
    assertEquals(folder.absoluteFile, one.parentFile)
    assertTrue(one.name, one.name.matches(Regex("""notes\.apk\.[0-9a-f]{12}\.part""")))
    assertTrue(one != two)
    assertEquals(one, FluidTransfers.partFile(download(id = "v1").copy(file = File(folder, "notes.apk"))))
  }

  /** Un disco che si riempie dopo [room] byte: la scrittura lancia come su Android (`ENOSPC`). */
  private class FullDisk(out: OutputStream, private val room: Long) : FilterOutputStream(out) {
    private var written = 0L

    override fun write(b: ByteArray, off: Int, len: Int) {
      if (written + len > room) throw IOException("write failed: ENOSPC (No space left on device)")
      out.write(b, off, len)
      written += len
    }
  }
}
