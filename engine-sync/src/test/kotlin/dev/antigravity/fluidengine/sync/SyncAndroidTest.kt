package dev.antigravity.fluidengine.sync

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.antigravity.fluidengine.storage.EngineInstallId
import dev.antigravity.fluidengine.sync.testing.FakeSyncRelay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** L'app di prova: ha un motore di sincronizzazione per il lavoro in sottofondo. */
class SyncTestApp : Application(), FluidSyncHost {
  val relay = FakeSyncRelay(MemorySyncRelay())
  val sealer = AesTestSealer()

  override val fluidSync: FluidSync by lazy {
    FluidSync(SqliteSyncStore(this, "worker.db"), relay, sealer, node = "app", dispatcher = Dispatchers.IO)
  }
}

/**
 * F8.21 · Le parti Android: il registro su SQLite (sopravvive alla chiusura, una transazione interrotta non lascia niente,
 * fuori dal backup) e il lavoro di WorkManager (vincolo di rete, riprova senza rete, riesce con la rete).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = SyncTestApp::class)
class SyncAndroidTest {
  private val context: Context = ApplicationProvider.getApplicationContext()
  private val sealer = AesTestSealer()

  @After fun clean() {
    context.noBackupFilesDir.listFiles()?.forEach { it.deleteRecursively() }
  }

  @Test fun `il registro su SQLite sopravvive alla chiusura e sta fuori dal backup`() = runBlocking {
    val relay = MemorySyncRelay()
    val store = SqliteSyncStore(context, "prova.db")
    assertTrue(store.file.path.startsWith(context.noBackupFilesDir.path))
    val sync = FluidSync(store, relay, sealer, node = "tel", dispatcher = Dispatchers.IO)
    val space = sync.space(SyncSpaceId("spesa"))
    val id = space.create { text("voce", "Latte"); longText("nota", "intera\n") }
    space.edit(id) { bool("presa", true) }
    store.close()

    val reopened = SqliteSyncStore(context, "prova.db")
    val again = FluidSync(reopened, relay, sealer, node = "tel", dispatcher = Dispatchers.IO)
    val back = again.space(SyncSpaceId("spesa"))
    back.load()
    val state = back.state.value
    assertEquals("Latte", state.records[id]?.text("voce"))
    assertEquals(true, state.records[id]?.bool("presa"))
    assertEquals("tre voci nel registro, un elemento solo: una modifica in attesa", 1, state.pending)
    assertEquals(3, reopened.transaction { journalSize("spesa") })
    assertTrue("l'orologio riprende da dove era", again.clock.now() > sync.clock.current)
    assertNull(back.sync().failure)
    assertEquals(0, back.state.value.pending)
    assertEquals(1, relay.rows(SyncSpaceId("spesa")).size)
    reopened.close()
  }

  @Test fun `una transazione interrotta su SQLite non lascia niente`() {
    val store = SqliteSyncStore(context, "interrotta.db")
    val stamp = SyncHlc(1, 0, "a")
    runCatching {
      store.transaction {
        putRecord("s", SyncStoredRecord("r", SyncDocument.Empty, null, 0, true))
        appendJournal("s", SyncJournalEntry(1, "r", stamp, SyncChange.Delete))
        error("corrente staccata")
      }
    }
    assertNull(store.transaction { record("s", "r") })
    assertEquals(0, store.transaction { journalSize("s") })
    store.transaction {
      appendJournal("s", SyncJournalEntry(1, "r", stamp, SyncChange.Write("t", SyncValue.Text("x"))))
      appendJournal("s", SyncJournalEntry(2, "r", stamp, SyncChange.Choose("t", listOf("a", "b"))))
      putMeta("s", "k", byteArrayOf(1, 2))
    }
    assertEquals(
      listOf(SyncChange.Write("t", SyncValue.Text("x")), SyncChange.Choose("t", listOf("a", "b"))),
      store.transaction { journal("s") }.map { it.change },
    )
    assertEquals(2L, store.transaction { lastJournalSeq("s", "r") })
    assertEquals("due voci dello stesso elemento: uno", 1, store.transaction { journalRecordCount("s") })
    assertEquals(1, store.transaction { removeJournal("s", "r", 1) })
    assertEquals(listOf("s"), store.transaction { spaces() })
    store.close()
  }

  @Test fun `l'identita' dell'installazione e' il nodo dell'orologio`() {
    val id = EngineInstallId.get(context)
    assertTrue(SyncHlc.isValidNode(id))
    assertEquals(id, EngineInstallId.get(context))
  }

  @Test fun `il lavoro in sottofondo chiede la rete, riprova senza e riesce con`() = runBlocking {
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    FluidSyncWork.request(context, delayMillis = 0)
    val info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(FluidSyncWork.UniqueName).get().single()
    assertEquals(WorkInfo.State.ENQUEUED, info.state)
    assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)

    val app = context as SyncTestApp
    app.fluidSync.space(SyncSpaceId("spesa")).create { text("voce", "Pane") }
    app.relay.online = false
    val offline = TestListenableWorkerBuilder<FluidSyncWorker>(context).build().doWork()
    assertEquals(ListenableWorker.Result.retry(), offline)
    assertEquals(1, app.fluidSync.overview.value.pending)
    app.relay.online = true
    val online = TestListenableWorkerBuilder<FluidSyncWorker>(context).build().doWork()
    assertEquals(ListenableWorker.Result.success(), online)
    assertEquals(0, app.fluidSync.overview.value.pending)
    assertFalse(app.fluidSync.overview.value.syncing)
  }
}
