package dev.antigravity.fluidengine.diagnostics

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * R3 (review `sol-ai`, n. 9) · Il «no» detto mentre parte un gruppo di rapporti ferma i gruppi dopo, e un «no» già
 * salvato quando il processo muore prima di svuotare la coda la svuota al prossimo avvio.
 */
@RunWith(RobolectricTestRunner::class)
class FluidDiagnosticsR3Test {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val now = 1_795_702_800_000L
  private val posts = ArrayList<String>()
  private var onPost: (() -> Unit)? = null
  private lateinit var directory: File

  /** Un filo che non fa mai girare niente: il processo muore prima che il lavoro in sottofondo parta. */
  private object NeverRuns : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) = Unit
  }

  private fun diagnostics(io: CoroutineDispatcher = Dispatchers.Unconfined) = FluidDiagnostics.create(
    context,
    FluidDiagnosticsConfig(appName = "Prova", endpoint = "https://diagnostica.example/v1/reports", crashes = false, systemExits = false, frames = false),
    transport = { _, body ->
      posts += String(body)
      onPost?.invoke()
      200
    },
    clock = { now },
    io = io,
    directory = directory,
  )

  @Before
  fun setUp() {
    directory = File(context.cacheDir, "diagnostica-r3").apply { deleteRecursively() }
    context.getSharedPreferences(FluidDiagnostics.PreferencesName, Context.MODE_PRIVATE).edit().clear().commit()
  }

  @After
  fun tearDown() {
    FluidDiagnostics.resetForTesting()
    directory.deleteRecursively()
  }

  @Test
  fun noWhileTheFirstBatchIsLeavingStopsTheNextOnes() = runBlocking {
    val d = diagnostics()
    repeat(60) { d.recordCrash(IllegalStateException(), mainThread = true) }
    assertTrue("almeno due gruppi", d.batches(d.queue.records()).size >= 2)
    // L'utente dice di no mentre il primo POST è in volo.
    onPost = {
      onPost = null
      d.setConsent(false)
    }
    d.setConsent(true)
    assertEquals("dopo il no non parte un altro gruppo", 1, posts.size)
    assertEquals(FluidDiagnosticsConsent.Denied, d.consent.value)
    assertEquals(0, d.queue.records().size)
  }

  @Test
  fun aSavedNoEmptiesTheQueueAtTheNextStart() {
    val before = diagnostics(io = NeverRuns)
    before.recordCrash(IllegalStateException(), mainThread = true)
    before.setConsent(false)
    // Il no è salvato, ma la pulizia non è partita: il processo muore qui.
    assertEquals(1, before.queue.records().size)
    // Il prossimo avvio.
    val after = diagnostics()
    assertEquals(FluidDiagnosticsConsent.Denied, after.consent.value)
    after.start(ApplicationProvider.getApplicationContext<Application>())
    assertEquals("col no salvato la coda si svuota all'avvio", 0, after.queue.records().size)
    assertTrue(posts.isEmpty())
  }
}
