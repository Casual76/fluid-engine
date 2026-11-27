package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.FakeKeysServer
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.custody.TransferQr
import dev.antigravity.fluidengine.account.host.server.SessionKind
import dev.antigravity.fluidengine.account.host.server.SessionStore
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * R3 (review `sol-ai`, n. 10 e 11) · La promozione della sessione non ripubblica il conto di prima quando un'entrata
 * con un altro account le passa in mezzo; un sigillo del passaggio già ritirato non si perde se la rete cade prima che
 * sia chiuso nel Keystore.
 */
class AccountSessionR3Test {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  /** Lo stato pubblicato da [session], con [before] chiamato prima di ogni pubblicazione (sul filo che pubblica). */
  private class HookedState(private val inner: MutableStateFlow<HostSession>, private val before: (HostSession) -> Unit) :
    MutableStateFlow<HostSession> by inner {
    override var value: HostSession
      get() = inner.value
      set(v) {
        before(v)
        inner.value = v
      }
  }

  private fun hook(session: AccountSession, before: (HostSession) -> Unit) {
    val field = AccountSession::class.java.getDeclaredField("_state").apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    field.set(session, HookedState(field.get(session) as MutableStateFlow<HostSession>, before))
  }

  // --- n. 10 · promote e adopt in corsa ---------------------------------------------------------------------------

  @Test
  fun `promote e adopt in corsa - lo stato pubblicato e' quello della sessione salvata`() {
    val p = R2Phone(tmp, servers)
    p.session.adopt(JsonObject(p.fake.login(p.deviceKey.publicJwk) + ("kind" to JsonPrimitive("recovery"))), "Alessio", "com.pampa.store")
    val a = p.fake.accountId
    val inside = CountDownLatch(1)
    val go = CountDownLatch(1)
    // La promozione di A si ferma proprio quando sta per pubblicare.
    hook(p.session) { s ->
      if (s is HostSession.SignedIn && s.accountId == a && s.kind == SessionKind.FULL) {
        inside.countDown()
        go.await(10, TimeUnit.SECONDS)
      }
    }
    val promoting = thread { p.session.promote() }
    assertTrue(inside.await(10, TimeUnit.SECONDS))
    // In quel momento si entra con l'account B.
    p.fake.accountId = "acc_contob00000000000000000"
    val b = p.fake.login(p.deviceKey.publicJwk)
    val adopting = thread { p.session.adopt(b, "Bea", "com.pampa.store") }
    adopting.join(500)
    go.countDown()
    promoting.join(10_000)
    adopting.join(10_000)
    val saved = SessionStore(p.vault, File(p.dir, "sessione"), KeyClassPolicy.Choice.A).load()
    assertEquals("acc_contob00000000000000000", saved!!.accountId)
    val shown = p.session.state.value as HostSession.SignedIn
    assertEquals("lo stato dice il conto salvato, non quello di prima", saved.accountId, shown.accountId)
    assertEquals("Bea", shown.displayName)
  }

  // --- n. 11 · il sigillo già ritirato -----------------------------------------------------------------------------

  @Test
  fun `il sigillo e' ritirato e la rete cade prima del Keystore - il ritiro dopo lo chiude senza un QR nuovo`() = runBlocking {
    val keys = FakeKeysServer()
    val old = R2Phone(tmp, servers, keys)
    old.signIn()
    old.custody.startEncryption(RecoveryCode.generate())
    val new = R2Phone(tmp, servers, keys)
    new.signIn()
    val offer = new.custody.beginTransfer()
    old.custody.confirmTransfer(TransferQr.parse(offer.qr)!!)
    // Il server dà il sigillo (e lo toglie), poi la lettura delle chiavi resta senza risposta.
    val first = new.dropping("GET /v1/keys") { new.custody.pollTransfer(offer) }
    assertTrue("era ${first.exceptionOrNull()}", first.exceptionOrNull() is IOException)
    assertTrue("niente di cifrato prima della verifica", !new.keyring.hasCustody)
    // Torna la rete: lo stesso passaggio si chiude, con lo stesso QR.
    val got = new.custody.pollTransfer(offer)
    assertNotNull(got)
    assertTrue(new.keyring.hasCustody)
    assertTrue(new.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
    // Dopo, il passaggio vale una volta sola come prima: l'offerta è dimenticata.
    val again = runCatching { new.custody.pollTransfer(offer) }.exceptionOrNull()
    assertTrue("era $again", again is TransferEndedException || again is IllegalStateException)
  }
}
