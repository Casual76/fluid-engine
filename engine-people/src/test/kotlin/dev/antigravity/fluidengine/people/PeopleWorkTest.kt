package dev.antigravity.fluidengine.people

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** L'Application di prova: lo Store che dà il client delle persone al lavoro in sottofondo. */
class PeopleWorkTestApp : Application(), PeopleWorkHost {
  var client: PeopleClient? = null
  override fun peopleClient(): PeopleClient? = client
}

/** La coda in uscita con WorkManager: senza rete riprova, con la rete manda, col telefono in tasca va lo stesso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = PeopleWorkTestApp::class)
class PeopleWorkTest {
  @Test
  fun `il lavoro chiede la rete, riprova senza e manda con, senza aprire la radice`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<PeopleWorkTestApp>()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    val server = FakePeopleServer()
    fun person(name: String): Pair<PeopleClient, MemoryPersonVault> {
      val dir: File = Files.createTempDirectory("lavoro").toFile()
      val vault = MemoryPersonVault()
      val device = server.newAccount().devices[0]
      val persona = PersonaStore(dir, vault, clock = { server.now }).also { it.create(name) }
      val c = PeopleClient(persona, PeopleStateStore(File(dir, "s.bin"), vault), PeopleApi(device.http, device.auth), clock = { server.now }, scheduler = PeopleWork.scheduler(context))
      runBlocking { c.publish() }
      return c to vault
    }
    val (alessio, vault) = person("Alessio")
    val (mamma, _) = person("Mamma")
    alessio.request(alessio.lookup(mamma.identity().code), "ciao")
    mamma.accept(mamma.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    alessio.pickup()
    context.client = alessio

    server.offline = true
    alessio.sendMessage(mamma.identity().code, buildJsonObject { put("t", "messaggio"); put("text", "dalla galleria") })
    val info = WorkManager.getInstance(context).getWorkInfosForUniqueWork(PeopleWork.UNIQUE).get().single()
    assertEquals("la coda ha chiesto un lavoro", WorkInfo.State.ENQUEUED, info.state)
    assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
    assertEquals(ListenableWorker.Result.retry(), TestListenableWorkerBuilder<PeopleOutboxWorker>(context).build().doWork())

    server.offline = false
    vault.motherLocked = true
    val opens = vault.rootOpens
    assertEquals(ListenableWorker.Result.success(), TestListenableWorkerBuilder<PeopleOutboxWorker>(context).build().doWork())
    assertEquals(opens, vault.rootOpens)
    assertTrue(alessio.state.outbox.isEmpty())
    assertEquals("dalla galleria", mamma.pickup().filterIsInstance<PeopleEvent.Message>().single().payload["text"]!!.jsonPrimitive.content)
  }
}
