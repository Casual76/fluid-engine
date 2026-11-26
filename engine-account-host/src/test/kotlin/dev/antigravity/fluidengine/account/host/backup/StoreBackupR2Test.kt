package dev.antigravity.fluidengine.account.host.backup

import dev.antigravity.fluidengine.account.backup.BackupFile
import dev.antigravity.fluidengine.account.backup.BackupPackage
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.CustodyService
import dev.antigravity.fluidengine.account.host.FakeAccountHttp
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.FakeKeysServer
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 · Il backup dello Store «già a posto» per un account che non ce l'ha: sullo stesso telefono esco da A ed entro con
 * B, con le stesse app. Sul codice di prima l'impronta del contenuto era la stessa, e per B non partiva niente.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoreBackupR2Test {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  /** Un account sul telefono: il suo server, la sua sessione e la sua custodia; [state] è la cartella del telefono. */
  private inner class Account(accountId: String, state: File) {
    val fake = FakeAccountHttp(FakeKeysServer(accountId))
    val web = MockWebServer().apply { dispatcher = fake; start(); servers += this }
    val base = web.url("/").toString().trimEnd('/')
    val client = AccountServerClient(base, "1.10.0")
    val vault = MemoryCustodyVault()
    val dir: File = tmp.newFolder()
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val deviceKey = SoftwareDeviceKey()
    val session = AccountSession(client, base, SessionStore(vault, File(dir, "s")), deviceKey, TokenCache({ 0L }), keyring, AppPolicy())
    val custody = CustodyService(client, session, keyring, AppPolicy(), { fake.accountId })
    val job = StoreBackupJob(session, client, keyring, { fake.accountId }, { content() }, state)

    fun content() = BackupPackage(AppPolicy.STORE, "1.10.0", 1, 1_764_000_000_000, listOf(BackupFile("apps.json", "dev.pampa.pampai".toByteArray())))

    suspend fun ready(): Account {
      session.adopt(fake.login(deviceKey.publicJwk), "Alessio", AppPolicy.STORE)
      custody.startEncryption(RecoveryCode.generate())
      return this
    }
  }

  @Test
  fun `un altro account sullo stesso telefono, con le stesse app - il suo backup parte`() = runBlocking {
    val phone = tmp.newFolder("stato-del-telefono")
    val a = Account("acc_contoa00000000000000000", phone).ready()
    assertTrue(a.job.run() is StoreBackupJob.Outcome.Uploaded)
    assertEquals(StoreBackupJob.Outcome.Unchanged, a.job.run())
    // A esce, B entra sullo stesso telefono: le app e le preferenze sono le stesse.
    a.session.signOut()
    val b = Account("acc_contob00000000000000000", phone).ready()
    val r = b.job.run()
    assertTrue("era $r", r is StoreBackupJob.Outcome.Uploaded)
    assertEquals(1, b.fake.backups[AppPolicy.STORE]?.size)
  }

  @Test
  fun `dopo un'epoca nuova il backup si richiude con la chiave nuova`() = runBlocking {
    val a = Account("acc_contoa00000000000000000", tmp.newFolder()).ready()
    assertTrue(a.job.run() is StoreBackupJob.Outcome.Uploaded)
    a.custody.rotate()
    val r = a.job.run()
    assertTrue("era $r", r is StoreBackupJob.Outcome.Uploaded)
    assertEquals(2, a.fake.backups.getValue(AppPolicy.STORE).getValue(2).first)
  }
}
