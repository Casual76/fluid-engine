package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.server.AccountServerException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * R2 · Il codice di recupero «in sospeso» (`CustodyService.pendingRecoveryCode`, `recoveryCodeSeen`): l'API nuova che
 * lo Store usa per rimostrare il codice dopo un riavvio, una rete caduta o un'attesa di sicurezza.
 */
class CustodyPendingR2Test {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  private fun phone() = R2Phone(tmp, servers)

  @Test
  fun `il codice nuovo si ritrova da mostrare finche' l'utente non dice di averlo scritto`() = runBlocking {
    val p = phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    p.custody.recoveryCodeSeen()
    assertNull(p.custody.pendingRecoveryCode())
    assertTrue(p.dropping("POST /v1/recovery") { p.custody.createRecoveryCode() }.isFailure)
    val shown = p.service().pendingRecoveryCode()!!
    assertEquals(shown.auth, p.sentAuths().last())
    assertEquals(shown, p.service().createRecoveryCode())
    assertEquals("finito l'invio, resta da mostrare", shown, p.service().pendingRecoveryCode())
    p.service().recoveryCodeSeen()
    assertNull(p.service().pendingRecoveryCode())
    assertFalse("il file in sospeso se ne va", java.io.File(p.dir, "chiavi/in-sospeso.bin").exists())
    // Dopo, un codice nuovo è davvero nuovo.
    assertNotEquals(shown, p.custody.createRecoveryCode())
  }

  @Test
  fun `l'attesa di sicurezza - il codice che varra' fra 24 ore resta da mostrare, e non si rimanda`() = runBlocking {
    val p = phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    p.custody.recoveryCodeSeen()
    p.fake.waitingRecovery = true
    val e = runCatching { p.custody.createRecoveryCode() }.exceptionOrNull()
    assertTrue("era $e", e is AccountServerException && e.isWaiting)
    val waiting = p.custody.pendingRecoveryCode()!!
    assertEquals(waiting.auth, p.sentAuths().last())
  }

  @Test
  fun `la prima cifratura a meta' con un altro codice - lo Store non mostra un codice che il server non ha`() = runBlocking {
    val p = phone()
    p.signIn()
    val first = RecoveryCode.generate()
    assertTrue(p.dropping("POST /v1/recovery") { p.custody.startEncryption(first) }.isFailure)
    // Il server ha già la porta del primo codice: un codice diverso adesso sarebbe un codice che non apre niente.
    assertTrue(runCatching { p.custody.startEncryption(RecoveryCode.generate()) }.exceptionOrNull() is IllegalStateException)
    assertEquals(first, p.custody.pendingRecoveryCode())
    val custody = p.custody.startEncryption(p.custody.pendingRecoveryCode())
    assertArrayEquals(p.opens(first).cd(1), custody.cd(1))
  }

  @Test
  fun `l'uscita cancella anche il lavoro in sospeso`() = runBlocking {
    val p = phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    assertTrue(java.io.File(p.dir, "chiavi/in-sospeso.bin").exists())
    p.session.signOut()
    assertFalse(java.io.File(p.dir, "chiavi/in-sospeso.bin").exists())
  }
}
