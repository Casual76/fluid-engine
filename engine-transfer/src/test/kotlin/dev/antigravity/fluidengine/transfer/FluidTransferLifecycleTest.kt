package dev.antigravity.fluidengine.transfer

import android.app.Application
import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.antigravity.fluidengine.foundation.FluidConditions
import dev.antigravity.fluidengine.foundation.FluidDeviceConditions
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.net.EngineConditions
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** L'app delle prove: tiene la coda (senza disco e senza WorkManager), come un'app con credenziali o notifiche sue. */
class TransferHostApp : Application(), FluidTransferHost {
  override val fluidTransfers: FluidTransfers by lazy {
    FluidTransfers(
      store = InMemoryFluidTransferStore(),
      conditions = { FluidConditions(online = true, connected = true) },
      conditionChanges = null,
    )
  }
}

/** R2 · Il lavoro di WorkManager e la coda dell'app lungo la vita del processo, in Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = TransferHostApp::class)
class FluidTransferLifecycleTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private lateinit var server: MockWebServer

  @Before fun setUp() {
    server = MockWebServer()
    server.start()
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder().setExecutor(SynchronousExecutor()).setMinimumLoggingLevel(android.util.Log.ERROR).build(),
    )
  }

  @After fun tearDown() {
    server.shutdown()
    EngineConditions.uninstall()
    FluidDeviceConditions.reset()
  }

  @Test fun `R2 un giro con un completato e uno da riprovare lascia l'esito`() = runBlocking {
    server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
      override fun dispatch(request: RecordedRequest): MockResponse =
        if (request.path == "/a.apk") MockResponse().setBody("ciao".repeat(1000)) else MockResponse().setResponseCode(503)
    }
    val transfers = (context as TransferHostApp).fluidTransfers
    val finished = CopyOnWriteArrayList<FluidTransferOverview>()
    transfers.notifications = object : SimpleTransferNotifications() {
      override fun finished(context: Context, overview: FluidTransferOverview) {
        finished += overview
      }
    }
    transfers.enqueue(FluidTransferRequest.download(server.url("/a.apk").toString(), File(context.filesDir, "a.apk"), "A", id = "a"))
    transfers.enqueue(FluidTransferRequest.download(server.url("/b.apk").toString(), File(context.filesDir, "b.apk"), "B", id = "b"))
    transfers.settle()
    val worker = TestListenableWorkerBuilder<FluidTransferWorker>(context).build()
    val result = worker.doWork()
    assertEquals(ListenableWorker.Result.retry(), result)
    val outcome = finished.singleOrNull()
    assertNotNull("l'esito del giro non e' arrivato", outcome)
    assertEquals(FluidTransferState.Completed, outcome!!.items.single { it.id == "a" }.state)
    assertEquals(FluidTransferState.Queued, outcome.items.single { it.id == "b" }.state)
  }

  @Test fun `R2 dopo close la coda dell'app si riapre e lavora`() = runBlocking {
    val first = FluidTransfers.open(context)
    first.close()
    val second = FluidTransfers.open(context)
    assertNotSame("open ha restituito la coda chiusa", first, second)
    second.enqueue(FluidTransferRequest.download(server.url("/x.apk").toString(), File(context.filesDir, "x.apk"), "X", id = "x"))
    second.settle()
    assertNotNull(second.item("x"))
    second.close()
  }

  @Test fun `R2 la notifica dei trasferimenti segue la scelta della persona sulla schermata di blocco`() {
    val running = FluidTransferItem(
      id = "a", title = "Pampa Notes 2.4", direction = FluidTransferDirection.Download, state = FluidTransferState.Running,
      bytesDone = 1L, bytesTotal = 2L,
    )
    val notification = SimpleTransferNotifications().foregroundInfo(context, FluidTransferOverview(listOf(running))).notification
    // Privata: con «nascondi i contenuti sensibili» il sistema mostra solo l'app, senza il nome del file.
    assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
  }
}
