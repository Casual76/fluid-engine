package dev.antigravity.fluidengine.transfer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.antigravity.fluidengine.foundation.FluidConditionReason
import dev.antigravity.fluidengine.foundation.FluidWork
import dev.antigravity.fluidengine.foundation.live.FluidLiveKind
import dev.antigravity.fluidengine.foundation.live.FluidLiveOutcome
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdate
import dev.antigravity.fluidengine.foundation.live.FluidLiveUpdateSink
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferFailure
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferOverview
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** F8.20 · La coda su SQLite, i vincoli di WorkManager e la notifica del servizio in primo piano, in Robolectric. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidTransferAndroidTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test fun `la coda su SQLite si rilegge uguale`() {
    val store = SqliteFluidTransferStore(context, "prova-trasferimenti.db")
    val request = FluidTransferRequest.download(
      url = "https://example.org/a.apk", target = File(context.filesDir, "a.apk"), title = "A", id = "a",
      subtitle = "Pampa Store", expectedBytes = 42L, sha256 = "abc", wifiOnly = true, chargingOnly = true,
      work = FluidWork.custom("mappe", avoidsExpensive = true, avoidsHeat = true), tag = "apk",
    )
    val record = FluidTransferRecord(
      request = request, state = FluidTransferState.Waiting, bytesDone = 21L, bytesTotal = 42L, validator = "\"v1\"",
      resumable = false, failure = FluidTransferFailure.server(503, "occupato"),
      waitingFor = listOf(FluidConditionReason.Metered, FluidConditionReason.NotCharging), attempts = 2, forced = true,
      createdAtMillis = 1L, updatedAtMillis = 2L, finishedAtMillis = null, uploadAcked = 7L,
    )
    store.save(record)
    val upload = FluidTransferRecord(
      FluidTransferRequest.upload("https://example.org/s", File(context.filesDir, "v.mp4"), "V", id = "v", mimeType = "video/mp4", resumable = true),
      createdAtMillis = 3L,
    )
    store.save(upload)
    val loaded = store.load()
    assertEquals(listOf(record, upload), loaded)
    assertEquals("mappe", loaded.first().request.work.name)
    assertTrue(loaded.first().request.work.avoidsHeat)
    store.delete("a")
    assertEquals(listOf("v"), store.load().map { it.id })
    store.close()
    assertTrue(store.file.exists())
  }

  @Test fun `il piano diventa i vincoli di WorkManager`() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder().setExecutor(SynchronousExecutor()).setMinimumLoggingLevel(android.util.Log.ERROR).build(),
    )
    FluidTransferWork.schedule(context, FluidTransferPlan(idle = false, runNow = false, unmetered = true, charging = true, delayMillis = 0L, replace = true))
    var info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(FluidTransferWork.UniqueName).get().single()
    assertEquals(NetworkType.UNMETERED, info.constraints.requiredNetworkType)
    assertTrue(info.constraints.requiresCharging())
    assertEquals(WorkInfo.State.ENQUEUED, info.state)
    FluidTransferWork.schedule(context, FluidTransferPlan(idle = false, runNow = true, unmetered = false, charging = false, delayMillis = 0L, replace = true))
    info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(FluidTransferWork.UniqueName).get().last()
    assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
  }

  @Test fun `il piano vuoto toglie il giro rimandato`() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
      context,
      Configuration.Builder().setExecutor(SynchronousExecutor()).setMinimumLoggingLevel(android.util.Log.ERROR).build(),
    )
    val work = WorkManager.getInstance(context)
    // Il controllo rimandato di chi aspettava (15 minuti).
    FluidTransferWork.schedule(context, FluidTransferPlan(idle = false, runNow = false, unmetered = true, charging = false, delayMillis = 15 * 60_000L, replace = true))
    assertEquals(WorkInfo.State.ENQUEUED, work.getWorkInfosForUniqueWork(FluidTransferWork.UniqueName).get().single().state)
    FluidTransferWork.schedule(context, FluidTransferPlan(idle = true, runNow = false, unmetered = false, charging = false, delayMillis = 0L, replace = true))
    val after = work.getWorkInfosForUniqueWork(FluidTransferWork.UniqueName).get()
    assertTrue(after.isNotEmpty() && after.all { it.state == WorkInfo.State.CANCELLED })
  }

  @Test fun `la notifica del servizio in primo piano e' di tipo dataSync`() {
    java.util.Locale.setDefault(java.util.Locale.ITALY)
    val overview = FluidTransferOverview(
      listOf(
        dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem(
          id = "a", title = "Pampa Notes 2.4", direction = dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection.Download,
          state = FluidTransferState.Running, bytesDone = 12_400_000L, bytesTotal = 48_000_000L, bytesPerSecond = 2_100_000L,
          remainingMillis = 17_000L,
        ),
      ),
    )
    val info = SimpleTransferNotifications().foregroundInfo(context, overview)
    assertEquals(SimpleTransferNotifications.NotificationId, info.notificationId)
    assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
    val extras = info.notification.extras
    assertEquals("Pampa Notes 2.4", extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    assertTrue(extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString().startsWith("12,4 MB di 48 MB"))
    assertEquals(25, extras.getInt(android.app.Notification.EXTRA_PROGRESS))
  }

  @Test fun `la notifica viva di engine-notify e' un trasferimento, coi tasti e l'esito`() {
    java.util.Locale.setDefault(java.util.Locale.ITALY)
    val finished = ArrayList<Pair<String, FluidLiveOutcome>>()
    val sink = object : FluidLiveUpdateSink {
      override fun publish(update: FluidLiveUpdate) = Unit
      override fun finish(id: String, outcome: FluidLiveOutcome) { finished += id to outcome }
      override fun remove(id: String) = Unit
      override val outside: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(true)
    }
    val built = ArrayList<Pair<FluidLiveUpdate, Int>>()
    val asked = ArrayList<String>()
    val controls = object : dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls {
      override fun pause(id: String) { asked += "pausa $id" }
      override fun resume(id: String) = Unit
      override fun retry(id: String) = Unit
      override fun force(id: String) = Unit
      override fun cancel(id: String) { asked += "annulla $id" }
      override fun clearFinished() = Unit
    }
    val live = LiveTransferNotifications(
      live = sink,
      foreground = { update, id ->
        built += update to id
        androidx.core.app.NotificationCompat.Builder(context, "prova").setSmallIcon(android.R.drawable.stat_sys_download).build()
      },
      controls = { controls },
    )
    val running = dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem(
      id = "a", title = "Pampa Notes 2.4", direction = dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection.Download,
      state = FluidTransferState.Running, bytesDone = 12_000_000L, bytesTotal = 48_000_000L, remainingMillis = 17_000L,
    )
    val info = live.foregroundInfo(context, FluidTransferOverview(listOf(running)))
    assertEquals(android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, info.foregroundServiceType)
    val (update, id) = built.single()
    assertEquals(SimpleTransferNotifications.NotificationId, id)
    assertEquals("transfers", update.id)
    assertEquals(FluidLiveKind.Transfer, update.kind)
    assertTrue("chiesto dall'utente: su Android 16 diventa Live Update", update.wantsPromotion)
    assertEquals("Pampa Notes 2.4", update.title)
    assertEquals("12 MB di 48 MB · ancora 17 s", update.text)
    assertEquals(0.25f, update.progress!!, 0.001f)
    assertEquals(listOf("Pausa", "Annulla"), update.actions.map { it.label })
    update.actions.forEach { it.onClick() }
    assertEquals(listOf("pausa a", "annulla a"), asked)

    // Finito nel giro: l'esito col nome. Un completato di prima del giro non conta.
    val old = running.copy(id = "vecchio", title = "Vecchio", state = FluidTransferState.Failed, finishedAtMillis = 1L)
    val done = running.copy(state = FluidTransferState.Completed, bytesDone = 48_000_000L, finishedAtMillis = System.currentTimeMillis() + 1)
    live.finished(context, FluidTransferOverview(listOf(done, old)))
    assertEquals("transfers", finished.single().first)
    assertTrue(finished.single().second.succeeded)
    assertEquals("Pampa Notes 2.4 scaricato", finished.single().second.message)
    // Un giro senza servizio (niente notifica) non chiude niente.
    live.finished(context, FluidTransferOverview(listOf(done)))
    assertEquals(1, finished.size)
  }
}
