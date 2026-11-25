package dev.antigravity.fluidengine.account.host.backup

import android.content.Context
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.antigravity.fluidengine.account.backup.BackupFile
import dev.antigravity.fluidengine.account.backup.BackupPackage
import dev.antigravity.fluidengine.account.backup.PampaBackupFormat
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.CustodyService
import dev.antigravity.fluidengine.account.host.FakeAccountHttp
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Il lavoro del backup dello Store (`b\chiavi.md` § 7): una volta al giorno con la rete, solo se è cambiato qualcosa,
 * cifrato, gli ultimi numeri che crescono; col telefono in tasca con la raccomandazione A di Q7, e chiuso con la C.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreBackupTest {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  private inner class Store(choice: KeyClassPolicy.Choice = KeyClassPolicy.Choice.A) {
    val fake = FakeAccountHttp()
    val web = MockWebServer().apply { dispatcher = fake; start(); servers += this }
    val base = web.url("/").toString().trimEnd('/')
    val client = AccountServerClient(base, "1.10.0")
    val vault = MemoryCustodyVault()
    val dir: File = tmp.newFolder()
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"), choice)
    val deviceKey = SoftwareDeviceKey()
    val session = AccountSession(client, base, SessionStore(vault, File(dir, "s"), choice), deviceKey, TokenCache({ 0L }), keyring, AppPolicy())
    val custody = CustodyService(client, session, keyring, AppPolicy(), { fake.accountId })
    var apps = listOf("dev.pampa.pampai")
    val job = StoreBackupJob(session, client, keyring, { fake.accountId }, { content() }, File(dir, "stato"))

    fun content() = BackupPackage(AppPolicy.STORE, "1.10.0", 1, System.currentTimeMillis(), listOf(BackupFile("apps.json", apps.joinToString(",").toByteArray())))

    fun signIn() = session.adopt(fake.login(deviceKey.publicJwk), "Alessio", AppPolicy.STORE)
  }

  @Test
  fun `il primo backup parte cifrato, il secondo uguale no, uno cambiato prende il numero dopo`() = runBlocking {
    val s = Store()
    assertEquals(StoreBackupJob.Outcome.SignedOut, StoreBackupJob(s.session, s.client, s.keyring, { null }, { s.content() }, tmp.newFolder()).run())
    s.signIn()
    assertEquals(StoreBackupJob.Outcome.NoE2e, s.job.run())
    s.custody.startEncryption(RecoveryCode.generate())
    val first = s.job.run()
    assertEquals(1L, (first as StoreBackupJob.Outcome.Uploaded).n)
    val stored = s.fake.backups.getValue(AppPolicy.STORE).getValue(1)
    assertEquals(1, stored.first)
    assertFalse("il server non legge l'elenco delle app", stored.second.decodeToString().contains("dev.pampa.pampai"))
    assertEquals(StoreBackupJob.Outcome.Unchanged, s.job.run())
    s.apps = listOf("dev.pampa.pampai", "dev.antigravity.fluidtransit")
    assertEquals(2L, (s.job.run() as StoreBackupJob.Outcome.Uploaded).n)
    // Il telefono nuovo, con la chiave, lo riapre.
    val keys = s.keyring.workKeys(AppPolicy.STORE, AppKeyPurpose.BACKUP)!!.keys
    val back = PampaBackupFormat.open(s.fake.backups.getValue(AppPolicy.STORE).getValue(2).second, keys::get, s.fake.accountId, AppPolicy.STORE, 2)
    assertEquals("dev.pampa.pampai,dev.antigravity.fluidtransit", back.file("apps.json")!!.bytes.decodeToString())
  }

  @Test
  fun `col telefono in tasca - con la A il backup parte, con la C aspetta lo sblocco`() = runBlocking {
    val a = Store(KeyClassPolicy.Choice.A)
    a.signIn()
    a.custody.startEncryption(RecoveryCode.generate())
    a.vault.locked = true
    assertTrue(a.job.run() is StoreBackupJob.Outcome.Uploaded)
    assertEquals(0, a.vault.opened[dev.antigravity.fluidengine.account.host.custody.KeyClass.MOTHER] ?: 0)

    val c = Store(KeyClassPolicy.Choice.C)
    c.signIn()
    c.custody.startEncryption(RecoveryCode.generate())
    c.vault.locked = true
    assertEquals(StoreBackupJob.Outcome.Locked, c.job.run())
    c.vault.locked = false
    assertTrue(c.job.run() is StoreBackupJob.Outcome.Uploaded)
  }

  @Test
  fun `offline - si riprova piu' tardi, e il backup di un'app la vede solo lei`() = runBlocking {
    val s = Store()
    s.signIn()
    s.custody.startEncryption(RecoveryCode.generate())
    s.fake.down = true
    assertEquals(StoreBackupJob.Outcome.Offline, s.job.run())
    s.fake.down = false
    assertTrue(s.job.run() is StoreBackupJob.Outcome.Uploaded)
    val dir = ServerBackupDirectory(s.session, s.client)
    assertEquals(listOf(1L), dir.available(AppPolicy.STORE)!!.map { it.n })
    assertEquals(emptyList<Long>(), dir.available("dev.pampa.pampai")!!.map { it.n })
  }

  @Test
  fun `il calendario - un lavoro solo, una volta al giorno, con la rete`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    WorkManagerTestInitHelper.initializeTestWorkManager(context)
    BackupScheduler.schedule(context)
    BackupScheduler.schedule(context)
    val infos = WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackupScheduler.WORK_NAME).get()
    assertEquals(1, infos.size)
    assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    assertEquals(24 * 3600_000L, infos.single().periodicityInfo!!.repeatIntervalMillis)
    BackupScheduler.cancel(context)
    assertEquals(WorkInfo.State.CANCELLED, WorkManager.getInstance(context).getWorkInfosForUniqueWork(BackupScheduler.WORK_NAME).get().single().state)
  }

  @Test
  fun `il contenuto dello Store - solo le app Pampa installate e le preferenze, niente altro`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = shadowOf(context.packageManager)
    for (p in listOf("dev.pampa.pampai", "dev.pampa.fluidweather", "com.altra.app")) pm.installPackage(PackageInfo().apply { packageName = p })
    val p = StoreBackupJob.storeContent(context, AppPolicy(), "1.10.0", mapOf("canale" to "stabile"))
    val apps = Json.parseToJsonElement(p.file("apps.json")!!.bytes.decodeToString()).jsonArray.map { it.jsonObject["package"]!!.jsonPrimitive.content }
    assertEquals(listOf("dev.pampa.fluidweather", "dev.pampa.pampai"), apps)
    assertEquals("stabile", Json.parseToJsonElement(p.file("preferenze.json")!!.bytes.decodeToString()).jsonObject["canale"]!!.jsonPrimitive.content)
  }
}
