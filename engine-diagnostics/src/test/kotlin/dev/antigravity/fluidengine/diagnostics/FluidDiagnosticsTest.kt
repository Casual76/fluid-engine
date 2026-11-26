package dev.antigravity.fluidengine.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowActivityManager

/**
 * F8.24 · La diagnostica dal campo: il consenso (niente parte prima del sì), la coda offline e il suo tetto, il crash
 * passato sempre al gestore di sistema, le chiusure decise dal sistema, i rapporti senza contenuti né identità.
 */
@RunWith(RobolectricTestRunner::class)
class FluidDiagnosticsTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private var now = 1_795_702_800_000L // 26/11/2026 14:20 UTC
  private val posts = ArrayList<String>()
  private var status = 200
  private lateinit var directory: File

  private fun diagnostics(endpoint: String? = "https://diagnostica.example/v1/reports", maxRecords: Int = 200, maxBytes: Long = 512 * 1024) =
    FluidDiagnostics.create(
      context,
      FluidDiagnosticsConfig(appName = "Prova", endpoint = endpoint, maxRecords = maxRecords, maxBytes = maxBytes),
      transport = { _, body -> posts += String(body); status },
      clock = { now },
      io = Dispatchers.Unconfined,
      directory = directory,
    )

  @Before
  fun setUp() {
    directory = File(context.cacheDir, "diagnostica-prova").apply { deleteRecursively() }
    context.getSharedPreferences(FluidDiagnostics.PreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After
  fun tearDown() {
    FluidDiagnostics.resetForTesting()
    directory.deleteRecursively()
  }

  private fun boom(): Throwable = IllegalStateException("Nota «Diario di Marta» non trovata", java.io.IOException("marta@example.com"))

  @Test
  fun nothingLeavesBeforeYes() = runBlocking {
    val d = diagnostics()
    assertEquals(FluidDiagnosticsConsent.NotAsked, d.consent.value)
    d.recordCrash(boom(), mainThread = true)
    // Raccolto sul telefono…
    assertEquals(1, d.queue.records().size)
    // …ma niente parte.
    assertEquals(FluidDiagnosticsSendResult.NotAllowed, d.send())
    assertEquals(FluidDiagnosticsSendResult.NotAllowed, d.sendIfDue(force = true))
    assertTrue(posts.isEmpty())
    // Col sì parte subito tutto, e la coda si svuota.
    d.setConsent(true)
    assertEquals(1, posts.size)
    assertEquals(0, d.queue.records().size)
    val sent = Json.parseToJsonElement(posts.single()).jsonObject
    assertEquals(1, sent["schema"]!!.jsonPrimitive.content.toInt())
    assertEquals("crash", sent["reports"]!!.jsonArray.single().jsonObject["kind"]!!.jsonPrimitive.content)
  }

  @Test
  fun withoutAnEndpointNothingEverLeaves() = runBlocking {
    val d = diagnostics(endpoint = null)
    d.setConsent(true)
    d.recordCrash(boom(), mainThread = false)
    assertEquals(FluidDiagnosticsSendResult.NotAllowed, d.send())
    assertTrue(posts.isEmpty())
    assertEquals(1, d.queue.records().size)
  }

  @Test
  fun noClearsTheQueueAndStopsCollecting() = runBlocking {
    val d = diagnostics()
    d.recordCrash(boom(), mainThread = true)
    d.frameAggregator.onFrame("note", 30_000_000, 20_000_000, isJank = true)
    d.setConsent(false)
    assertEquals(0, d.queue.records().size)
    assertTrue(d.pendingFrames().isEmpty())
    d.recordCrash(boom(), mainThread = true)
    d.flushFrames()
    assertEquals(0, d.queue.records().size)
    assertFalse(d.isCollecting)
    assertTrue(posts.isEmpty())
  }

  @Test
  fun theChoiceSurvivesARestart() {
    diagnostics().setConsent(true)
    assertEquals(FluidDiagnosticsConsent.Granted, diagnostics().consent.value)
    diagnostics().setConsent(false)
    assertEquals(FluidDiagnosticsConsent.Denied, diagnostics().consent.value)
  }

  @Test
  fun aFailedSendKeepsTheQueueAndTriesAgainLater() = runBlocking {
    val d = diagnostics()
    d.recordCrash(boom(), mainThread = true)
    status = 503
    d.setConsent(true)
    assertEquals(1, d.queue.records().size)
    assertTrue(d.send() is FluidDiagnosticsSendResult.Failed)
    status = 200
    val result = d.send()
    assertTrue(result is FluidDiagnosticsSendResult.Sent)
    assertEquals(0, d.queue.records().size)
    // Una volta al giorno: un'ora dopo non riparte niente da solo.
    d.recordCrash(boom(), mainThread = true)
    now += 3_600_000
    val before = posts.size
    assertEquals(FluidDiagnosticsSendResult.NothingToSend, d.sendIfDue())
    assertEquals(before, posts.size)
    now += 24 * 3_600_000
    assertTrue(d.sendIfDue() is FluidDiagnosticsSendResult.Sent)
  }

  @Test
  fun theQueueHasACapAndDropsFramesFirst() {
    val d = diagnostics(maxRecords = 10)
    d.frameAggregator.onFrame("note", 9_000_000, 0, isJank = false)
    d.flushFrames()
    repeat(12) { d.recordCrash(IllegalArgumentException(), mainThread = true) }
    val records = d.queue.records()
    assertEquals(10, records.size)
    // I fotogrammi sono andati per primi, poi i crash più vecchi.
    assertTrue(records.all { it.kind == FluidDiagnosticsKind.Crash })
    assertEquals(10, d.queue.count.value)
  }

  @Test
  fun theQueueHasAByteCap() {
    val d = diagnostics(maxBytes = 8 * 1024)
    repeat(40) { d.recordCrash(boom(), mainThread = true) }
    val bytes = File(directory, "coda.jsonl").length()
    assertTrue("$bytes byte", bytes <= 8 * 1024)
    assertTrue(d.queue.records().isNotEmpty())
  }

  @Test
  fun framesOfTheSamePageAndDayAddUpInOneRow() {
    val d = diagnostics()
    repeat(3) {
      d.frameAggregator.onFrame("note", 10_000_000, 1_700_000, isJank = false)
      d.frameAggregator.onFrame("note", 300_000_000, 291_000_000, isJank = true)
      d.frameAggregator.onFrame("impostazioni", 8_000_000, -300_000, isJank = false)
      d.flushFrames()
    }
    val records = d.queue.records()
    assertEquals(2, records.size)
    val note = records.first { it.frames?.page == "note" }.frames!!
    assertEquals(6L, note.frames)
    assertEquals(3L, note.slow)
    assertEquals(3L, note.hangs) // 300 ms: un blocco, sotto i 700 del fotogramma bloccato
    assertEquals(0L, note.frozen)
    assertEquals(300L, note.worstMillis)
    assertEquals(6L, note.buckets.sum())
    assertTrue(note.hitchRatio > 10.0)
    // Un altro giorno è un'altra riga.
    now += 24 * 3_600_000
    d.frameAggregator.onFrame("note", 10_000_000, 0, isJank = false)
    d.flushFrames()
    assertEquals(3, d.queue.records().size)
  }

  @Test
  fun aCrashCarriesNoMessagesNoThreadNamesNoIds() {
    val d = diagnostics()
    val thrown = boom()
    d.recordCrash(thrown, mainThread = true)
    val record = d.queue.records().single()
    val json = record.toJsonString()
    assertFalse(json, json.contains("Marta"))
    assertFalse(json, json.contains("example.com"))
    assertEquals("java.lang.IllegalStateException", record.crash!!.type)
    assertEquals(listOf("java.io.IOException"), record.crash!!.causes)
    assertTrue(record.crash!!.frames.first().startsWith("dev.antigravity.fluidengine.diagnostics.FluidDiagnosticsTest.boom("))
    assertEquals(0L, record.hourMillis % 3_600_000)
    assertTrue(record.id.matches(Regex("[0-9a-f-]{36}")))
    // Nessun campo che leghi due rapporti allo stesso telefono.
    val source = Json.parseToJsonElement(json).jsonObject["source"]!!.jsonObject
    assertEquals(setOf("app", "version", "code", "engine", "build", "maker", "model", "sdk", "hz"), source.keys)
    // La stessa eccezione con un messaggio diverso ha la stessa firma.
    val again = FluidCrash.describe(IllegalStateException("altro testo").also { it.stackTrace = thrown.stackTrace }, true)
    assertEquals(record.crash!!.signature, again.signature)
    // Andata e ritorno dalla coda.
    assertEquals(json, FluidDiagnosticsRecord.fromJsonString(json)!!.toJsonString())
  }

  @Test
  fun theCrashAlwaysReachesTheSystemHandler() {
    val seen = ArrayList<Pair<Thread, Throwable>>()
    val system = Thread.UncaughtExceptionHandler { t, e -> seen += t to e }
    val d = diagnostics()
    val handler = FluidCrashHandler(system) { _, e, main -> d.recordCrash(e, main) }
    val error = boom()
    handler.uncaughtException(Thread.currentThread(), error)
    assertEquals(1, seen.size)
    assertSame(error, seen.single().second)
    assertSame(Thread.currentThread(), seen.single().first)
    assertEquals(1, d.queue.records().size)
    // Anche se la scrittura fallisce, il crash passa lo stesso, e com'era.
    val broken = FluidCrashHandler(system) { _, _, _ -> throw OutOfMemoryError("disco pieno") }
    val second = RuntimeException()
    broken.uncaughtException(Thread.currentThread(), second)
    assertEquals(2, seen.size)
    assertSame(second, seen.last().second)
  }

  @Test
  fun installingTwiceKeepsOneHandler() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    try {
      val system = Thread.UncaughtExceptionHandler { _, _ -> }
      Thread.setDefaultUncaughtExceptionHandler(system)
      val first = FluidCrashHandler.install { _, _, _ -> }
      val second = FluidCrashHandler.install { _, _, _ -> }
      assertSame(first, second)
      assertSame(first, Thread.getDefaultUncaughtExceptionHandler())
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  @Test
  fun systemExitsAreReadOnceAndOnlyTheOnesTheUserDidNotWant() {
    val manager = context.getSystemService(ActivityManager::class.java)
    val shadow: ShadowActivityManager = shadowOf(manager)
    val d = diagnostics()
    // Il primo avvio fissa il punto di partenza: le chiusure di prima non sono di questa raccolta.
    d.readSystemExits()
    assertEquals(0, d.queue.records().size)
    val trace = """
      ----- pid 1234 at 2026-11-26 14:00:00 -----
      "main" prio=5 tid=1 Sleeping
        | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72f0
        at java.lang.Thread.sleep(Native method)
        - sleeping on <0x0c8b2f6e> (a java.lang.Object)
        at dev.antigravity.fluidglass.Lento.run(Lento.kt:42)
        at android.os.Handler.handleCallback(Handler.java:959)

      "FinalizerDaemon" daemon prio=5 tid=12 Waiting
        at java.lang.Object.wait(Native method)
    """.trimIndent()
    fun exit(reason: Int, at: Long, trace: String? = null) = ShadowActivityManager.ApplicationExitInfoBuilder.newBuilder()
      .setProcessName(context.packageName)
      .setReason(reason)
      .setTimestamp(at)
      .setImportance(ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
      .setPss(100_000).setRss(150_000)
      .apply { if (trace != null) setTraceInputStream(ByteArrayInputStream(trace.toByteArray())) }
      .build()
    shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_ANR, now + 1_000, trace))
    shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_USER_REQUESTED, now + 2_000))
    shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_LOW_MEMORY, now + 3_000))
    shadow.addApplicationExitInfo(exit(ApplicationExitInfo.REASON_CRASH, now + 4_000))
    d.readSystemExits()
    val exits = d.queue.records().map { it.exit!! }
    assertEquals(listOf("anr", "low_memory"), exits.map { it.reason })
    assertEquals("foreground", exits.first().importance)
    assertEquals(
      listOf("java.lang.Thread.sleep(Native method)", "dev.antigravity.fluidglass.Lento.run(Lento.kt:42)", "android.os.Handler.handleCallback(Handler.java:959)"),
      exits.first().frames,
    )
    // Al prossimo avvio le stesse non si rileggono.
    d.readSystemExits()
    assertEquals(2, d.queue.records().size)
  }

  @Test
  fun installReturnsTheSameDiagnostics() {
    val app = ApplicationProvider.getApplicationContext<android.app.Application>()
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    try {
      assertNull(FluidDiagnostics.get())
      val first = FluidDiagnostics.install(app, FluidDiagnosticsConfig("Prova"))
      assertSame(first, FluidDiagnostics.install(app, FluidDiagnosticsConfig("Altra")))
      assertSame(first, FluidDiagnostics.get())
      assertTrue(Thread.getDefaultUncaughtExceptionHandler() is FluidCrashHandler)
    } finally {
      Thread.setDefaultUncaughtExceptionHandler(previous)
    }
  }

  @Test
  fun batchesStayUnderTheServerLimits() {
    val d = diagnostics()
    repeat(120) { d.recordCrash(boom(), mainThread = true) }
    val batches = d.batches(d.queue.records())
    assertTrue(batches.all { it.size <= FluidDiagnostics.BatchSize })
    assertTrue(batches.all { b -> b.sumOf { it.toJsonString().length } <= FluidDiagnostics.BatchBytes })
    assertEquals(120, batches.sumOf { it.size })
  }

  @Test
  fun onlyHttpsLeaves() {
    val thrown = runCatching { FluidDiagnosticsConfig("Prova", endpoint = "http://diagnostica.example/v1/reports") }.exceptionOrNull()
    assertTrue(thrown is IllegalArgumentException)
    FluidDiagnosticsConfig("Prova", endpoint = "http://127.0.0.1:8787/v1/reports")
  }
}
