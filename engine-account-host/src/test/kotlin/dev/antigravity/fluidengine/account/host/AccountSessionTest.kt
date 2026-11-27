package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.crypto.PampaBase64
import dev.antigravity.fluidengine.account.crypto.PampaCrypto
import dev.antigravity.fluidengine.account.crypto.PampaRecordCipher
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyTamperedException
import dev.antigravity.fluidengine.account.host.custody.DoorSecret
import dev.antigravity.fluidengine.account.host.custody.EpochKind
import dev.antigravity.fluidengine.account.host.custody.FakeKeysServer
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.custody.TransferQr
import dev.antigravity.fluidengine.account.host.custody.X25519KeyPair
import dev.antigravity.fluidengine.account.host.custody.recoveryForms
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import dev.antigravity.fluidengine.account.host.server.SessionKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertNull
import org.junit.Assert.fail

/**
 * La sessione dello Store e la custodia **su HTTP**, contro il server finto dietro un vero socket: il rinnovo con la
 * prova del dispositivo, i token delle app, le uscite, `appKey` nelle due classi, il passaggio fra due telefoni, il
 * server ostile e il banco «niente segreti» su intestazioni e corpi (`b\chiavi.md` § 11, punti 5 e 9).
 */
class AccountSessionTest {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  private inner class Phone(keys: FakeKeysServer = FakeKeysServer(), choice: KeyClassPolicy.Choice = KeyClassPolicy.Choice.A) {
    val fake = FakeAccountHttp(keys)
    val web = MockWebServer().apply { dispatcher = fake; start(); servers += this }
    val base = web.url("/").toString().trimEnd('/')
    val client = AccountServerClient(base, "1.10.0")
    val vault = MemoryCustodyVault()
    var clock = 0L
    val tokens = TokenCache({ clock })
    val dir: File = tmp.newFolder()
    val keyring = dev.antigravity.fluidengine.account.host.custody.CustodyKeyring(vault, File(dir, "chiavi"), choice)
    val deviceKey = SoftwareDeviceKey()
    val notified = mutableListOf<List<String>>()
    val session = AccountSession(client, base, SessionStore(vault, File(dir, "sessione"), choice), deviceKey, tokens, keyring, AppPolicy()) { notified += it }
    val custody = CustodyService(client, session, keyring, AppPolicy(), { fake.accountId }, deviceId = { fake.deviceId })

    fun signIn() = session.adopt(fake.login(deviceKey.publicJwk), "Alessio", "com.pampa.store")
  }

  @Test
  fun `il rinnovo porta la prova del dispositivo, firmata e senza il token di rinnovo`() = runBlocking {
    val p = Phone()
    p.signIn()
    p.fake.expireAccess()
    p.clock += 16 * 60_000
    val token = p.session.storeToken()
    assertTrue(token.startsWith("at-"))
    assertEquals(1, p.fake.refreshes)
    val refresh = p.fake.recorded.single { it.path == "/v1/session/refresh" }
    assertTrue(refresh.headers.contains("pampa-device-proof"))
    // La prova porta solo l'hash del token di rinnovo; il token sta solo nel corpo della rotta di rinnovo.
    val proof = Regex("pampa-device-proof: (\\S+)").find(refresh.headers)!!.groupValues[1]
    val refreshToken = Json.parseToJsonElement(refresh.body).jsonObject["refreshToken"]!!.jsonPrimitive.content
    assertFalse(proof.contains(refreshToken))
    assertTrue(p.fake.recorded.filter { it.path != "/v1/session/refresh" }.none { it.body.contains(refreshToken) || it.headers.contains(refreshToken) })
    // Il token si tiene in memoria: la seconda volta niente rete.
    assertEquals(token, p.session.storeToken())
    assertEquals(1, p.fake.refreshes)
    // Un'altra chiave del dispositivo non rinnova: la prova non torna.
    val thief = Phone()
    thief.signIn()
    thief.fake.expireAccess()
    thief.clock += 16 * 60_000
    thief.deviceKey.destroy() // la chiave nuova non è quella registrata
    assertTrue(runCatching { thief.session.storeToken() }.isFailure)
  }

  @Test
  fun `i token delle app - tabella sul telefono, cache per app, app scollegata, offline`() = runBlocking {
    val p = Phone()
    p.signIn()
    val ok = p.session.appToken("dev.pampa.pampai", "sync", setOf("sync.data"))
    assertTrue(ok is TokenOutcome.Ok)
    assertEquals(ok, p.session.appToken("dev.pampa.pampai", "sync", setOf("sync.data")))
    assertEquals(1, p.fake.appTokens)
    // Un'app non chiede quello che la tabella non le dà, e la richiesta non parte nemmeno (M9).
    val before = p.fake.recorded.size
    assertEquals(TokenOutcome.NotAllowed, p.session.appToken("com.p2r3.convert", "sync", setOf("sync.data")))
    assertEquals(TokenOutcome.NotAllowed, p.session.appToken("dev.pampa.pampai", "notes", setOf("notes.sync")))
    assertEquals(TokenOutcome.NotAllowed, p.session.appToken("com.estranea.app", "sync", emptySet()))
    assertEquals(before, p.fake.recorded.size)
    // Il token di PampAI non va mai a un'altra app.
    val notes = p.session.appToken("dev.pampa.pampanotes", "notes", setOf("notes.sync")) as TokenOutcome.Ok
    assertFalse(notes.token == (ok as TokenOutcome.Ok).token)
    p.fake.disconnected += "dev.antigravity.fluidtransit"
    assertEquals(TokenOutcome.Disconnected, p.session.appToken("dev.antigravity.fluidtransit", "sync", setOf("sync.data")))
    p.web.shutdown()
    assertEquals(TokenOutcome.Offline, p.session.appToken("dev.pampa.fluidweather", "sync", setOf("sync.backup")))
  }

  @Test
  fun `5 - uscita da remoto - via chiavi e copie, le app ricevono wiped`() = runBlocking {
    val p = Phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    assertTrue(p.keyring.hasCustody)
    p.fake.revokedReason = "lost"
    p.fake.expireAccess()
    p.clock += 16 * 60_000
    assertTrue(runCatching { p.session.storeToken() }.isFailure)
    assertEquals(HostSession.SignedOut, p.session.state.value)
    assertFalse(p.keyring.hasCustody)
    assertEquals(listOf("state", "wiped"), p.notified.last())
    // L'uscita da remoto resta scritta: uno Store riavviato la conferma ancora alle app.
    assertTrue(SessionStore(p.vault, File(p.dir, "sessione")).remoteExitAt() > 0)
  }

  @Test
  fun `5 - uscita volontaria - via le chiavi, le copie restano (niente wiped)`() = runBlocking {
    val p = Phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    p.session.signOut()
    assertFalse(p.keyring.hasCustody)
    assertEquals(listOf("state"), p.notified.last())
    assertTrue(p.fake.recorded.any { it.path == "/v1/session/logout" })
  }

  @Test
  fun `appKey - prima della cifratura, in tasca con la A, in tasca con la C, Keystore perso, app estranea`() = runBlocking {
    val a = Phone()
    a.signIn()
    assertEquals(AppKeyOutcome.NoE2e, a.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP))
    a.custody.startEncryption(RecoveryCode.generate())
    a.vault.locked = true
    val keys = a.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP)
    assertTrue(keys is AppKeyOutcome.Ok)
    assertEquals(AppKeyOutcome.NotAllowed, a.custody.appKey("dev.pampa.fluidweather", AppKeyPurpose.APP))
    assertEquals(AppKeyOutcome.NotAllowed, a.custody.appKey("com.p2r3.convert", AppKeyPurpose.BACKUP))
    a.vault.invalidate()
    assertEquals(AppKeyOutcome.NeedsDoor, a.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP))

    val c = Phone(choice = KeyClassPolicy.Choice.C)
    c.signIn()
    c.custody.startEncryption(RecoveryCode.generate())
    c.vault.locked = true
    assertEquals(AppKeyOutcome.Locked, c.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP))
  }

  @Test
  fun `il passaggio fra due telefoni su HTTP, e il nuovo legge i dati del vecchio`() = runBlocking {
    val keys = FakeKeysServer()
    val old = Phone(keys)
    old.signIn()
    old.custody.startEncryption(RecoveryCode.generate())
    old.custody.rotate()
    val set = (old.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) as AppKeyOutcome.Ok).keys
    val field = PampaRecordCipher(keys.accountId, "dev.pampa.pampai").seal(set.keys.getValue(set.current), set.current, "memories", "r", 1, "ciao".toByteArray())

    val new = Phone(keys)
    new.signIn()
    val offer = new.custody.beginTransfer()
    val qr = TransferQr.parse(offer.qr)!!
    assertEquals("Telefono nuovo", old.custody.transferInfo(qr)["deviceName"]!!.jsonPrimitive.content)
    old.custody.confirmTransfer(qr)
    new.custody.completeTransfer(offer)
    val got = (new.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) as AppKeyOutcome.Ok).keys
    assertEquals("ciao", PampaRecordCipher(keys.accountId, "dev.pampa.pampai").open({ got.keys[it] }, "memories", "r", 1, field).decodeToString())
    // Il corpo che apre il passaggio è vuoto: né N né s.
    assertEquals("{}", new.fake.recorded.single { it.path == "/v1/keys/transfers" }.body)
  }

  @Test
  fun `9 su HTTP - un giro completo senza segreti in nessuna intestazione e in nessun corpo`() = runBlocking {
    val keys = FakeKeysServer()
    val p = Phone(keys)
    p.signIn()
    val code = RecoveryCode.generate()
    p.custody.startEncryption(code)
    val prf = PampaCrypto.random(32)
    p.custody.addPrfDoor("cred1", prf, synced = true)
    p.custody.rotate()
    keys.lostPending = true
    p.custody.rotate(EpochKind.LOST)
    val second = p.custody.createRecoveryCode()
    val other = Phone(keys)
    other.signIn()
    val offer = other.custody.beginTransfer()
    val qr = TransferQr.parse(offer.qr)!!
    p.custody.confirmTransfer(qr)
    other.custody.completeTransfer(offer)
    p.fake.expireAccess()
    p.clock += 16 * 60_000
    p.session.appToken("dev.pampa.pampai", "sync", setOf("sync.data"))
    val everything = (p.fake.recorded + other.fake.recorded).joinToString("\n") { "${it.method} ${it.path}\n${it.headers}\n${it.body}" }
    val custody = p.keyring.loadCustody()!!
    val otherCustody = other.keyring.loadCustody()!!
    val secrets = recoveryForms(code) + recoveryForms(second) + listOf(PampaBase64.encode(prf), PampaBase64.encode(qr.s)) +
      custody.epochs.map { PampaBase64.encode(custody.cd(it)!!) } +
      // I segreti delle porte dei dispositivi (14): restano nella custodia.
      listOf(PampaBase64.encode(custody.deviceSecret()!!), PampaBase64.encode(otherCustody.deviceSecret()!!))
    for (s in secrets) assertFalse("è partito un segreto («${s.take(6)}…»)", everything.contains(s))
    assertTrue(everything.contains(code.auth))
    assertTrue(everything.contains(second.auth))
  }

  @Test
  fun `14 su HTTP - le porte dei dispositivi - l'epoca nuova arriva in silenzio all'altro dispositivo`() = runBlocking {
    val keys = FakeKeysServer()
    val phone = Phone(keys)
    phone.signIn()
    phone.custody.startEncryption(RecoveryCode.generate())
    val tablet = Phone(keys)
    tablet.signIn()
    val offer = tablet.custody.beginTransfer()
    phone.custody.confirmTransfer(TransferQr.parse(offer.qr)!!)
    tablet.custody.completeTransfer(offer)
    // Tutti e due hanno la loro porta, creata da sé dopo aver avuto la chiave.
    val doors = keys.honestView().doors.map { it.doorId }.toSet()
    assertTrue(doors.containsAll(setOf("device:${phone.fake.deviceId}", "device:${tablet.fake.deviceId}")))
    // Il telefono ruota; il tablet la riceve senza codice, passkey o QR.
    phone.custody.rotate()
    val caught = tablet.custody.catchUp()
    assertEquals(2, caught.current)
    val set = (tablet.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) as AppKeyOutcome.Ok).keys
    assertEquals(2, set.current)
    // La porta l'ha scritta ognuno per sé, e il corpo di PUT non contiene il segreto.
    assertTrue(tablet.fake.recorded.any { it.method == "PUT" && it.path == "/v1/keys/doors/device:${tablet.fake.deviceId}" })
  }

  @Test
  fun `server ostile su HTTP - una porta con la pubblica sostituita, e niente parte`() = runBlocking {
    val keys = FakeKeysServer()
    val p = Phone(keys)
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    val evil = PampaBase64.encode(X25519KeyPair.random().publicKey)
    keys.tamper = { v -> v.copy(doors = v.doors.map { it.copy(pub = evil) }) }
    val before = p.fake.recorded.count { it.path == "/v1/keys/epochs" }
    val doorsBefore = p.fake.recorded.count { it.method == "PUT" && it.path.startsWith("/v1/keys/doors/") }
    assertTrue(runCatching { p.custody.rotate() }.exceptionOrNull() is CustodyTamperedException)
    assertTrue(runCatching { p.custody.addPrfDoor("c", PampaCrypto.random(32), true) }.exceptionOrNull() is CustodyTamperedException)
    assertEquals(before, p.fake.recorded.count { it.path == "/v1/keys/epochs" })
    assertEquals(doorsBefore, p.fake.recorded.count { it.method == "PUT" && it.path.startsWith("/v1/keys/doors/") })
  }

  @Test
  fun `7 su HTTP - il Keystore ha perso la custodia, lo Store riapre dalla porta del codice`() = runBlocking {
    val p = Phone()
    p.signIn()
    val code = RecoveryCode.generate()
    val first = p.custody.startEncryption(code)
    p.vault.invalidate()
    assertEquals(AppKeyOutcome.NeedsDoor, p.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP))
    val reopened = p.custody.openWithDoor(DoorSecret.recovery(code))
    assertEquals(first.epochs, reopened.epochs)
    assertNotNull((p.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) as AppKeyOutcome.Ok).keys)
  }
  @Test
  fun `F10_7 - il telefono nuovo guarda prima del sigillo - null, l'offerta resta buona, poi ritira e conferma`() = runBlocking {
    val keys = FakeKeysServer()
    val old = Phone(keys)
    old.signIn()
    old.custody.startEncryption(RecoveryCode.generate())
    val new = Phone(keys)
    new.signIn()
    val offer = new.custody.beginTransfer()
    // Il QR è sullo schermo, il telefono vecchio non l'ha ancora inquadrato: «non ancora», senza toccare l'offerta.
    assertNull(new.custody.pollTransfer(offer))
    try {
      new.custody.completeTransfer(offer)
      fail("senza sigillo completeTransfer deve dire che aspetta")
    } catch (e: TransferPendingException) {
      assertEquals(offer.transferId, e.transferId)
    }
    old.custody.confirmTransfer(TransferQr.parse(offer.qr)!!)
    val got = new.custody.pollTransfer(offer)
    assertNotNull(got)
    assertTrue(new.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
    // F10.7b: dopo il salvataggio la conferma, e il sigillo non c'è più. Chi ci riprova sa che il passaggio è finito.
    assertEquals(1, keys.transferConfirmations)
    try {
      new.custody.pollTransfer(offer)
      fail("un passaggio già ritirato non dà un altro sigillo")
    } catch (e: TransferEndedException) {
      assertEquals("taken", e.state)
    }
  }

  @Test
  fun `F10_7b - la risposta del ritiro si perde - lo stesso QR ritrova il sigillo, poi la conferma lo toglie`() = runBlocking {
    // R3-S2, difetto 11; scelta A di Alessio del 27/11. Il server dà il sigillo, ma la risposta non arriva.
    val keys = FakeKeysServer()
    val old = Phone(keys)
    old.signIn()
    old.custody.startEncryption(RecoveryCode.generate())
    val new = Phone(keys)
    new.signIn()
    val offer = new.custody.beginTransfer()
    old.custody.confirmTransfer(TransferQr.parse(offer.qr)!!)
    val pickUp = "GET /v1/keys/transfers/${offer.transferId}"
    new.fake.dropping += pickUp
    try {
      new.custody.pollTransfer(offer)
      fail("la risposta persa è un errore di rete")
    } catch (e: TransferEndedException) {
      fail("una risposta persa non chiude il passaggio")
    } catch (_: Exception) {
      // L'errore di rete: lo Store tiene l'offerta e riprova.
    }
    assertTrue("il server ha dato il sigillo almeno una volta", keys.transferPickups >= 1)
    assertEquals("ancora nessuna conferma", 0, keys.transferConfirmations)
    assertEquals("sealed", keys.transferState(offer.transferId))
    new.fake.dropping -= pickUp
    // Il tentativo dopo, con la stessa offerta (lo stesso QR sullo schermo): stesso sigillo, chiave chiusa, conferma.
    assertNotNull(new.custody.pollTransfer(offer))
    assertTrue(new.keyring.hasCustody)
    assertTrue(new.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
    assertEquals(1, keys.transferConfirmations)
    assertEquals("taken", keys.transferState(offer.transferId))
    assertTrue(new.fake.recorded.any { it.method == "POST" && it.path == "/v1/keys/transfers/${offer.transferId}/received" })
    try {
      new.custody.pollTransfer(offer)
      fail("dopo la conferma il sigillo non c'è più")
    } catch (e: TransferEndedException) {
      assertEquals("taken", e.state)
    }
  }

  @Test
  fun `F10_7b - con un server di prima la conferma manca, e il passaggio riesce lo stesso`() = runBlocking {
    val keys = FakeKeysServer()
    val old = Phone(keys)
    old.signIn()
    old.custody.startEncryption(RecoveryCode.generate())
    val new = Phone(keys)
    new.signIn()
    new.fake.transferBeforeF107b = true
    val offer = new.custody.beginTransfer()
    old.custody.confirmTransfer(TransferQr.parse(offer.qr)!!)
    assertNotNull(new.custody.pollTransfer(offer))
    assertTrue(new.keyring.hasCustody)
    // La conferma è partita, il server di prima ha risposto 404, e non è un errore.
    assertTrue(new.fake.recorded.any { it.method == "POST" && it.path == "/v1/keys/transfers/${offer.transferId}/received" })
    assertEquals("taken", keys.transferState(offer.transferId))
    try {
      new.custody.pollTransfer(offer)
      fail("un passaggio già ritirato non dà un altro sigillo")
    } catch (e: TransferEndedException) {
      assertEquals("taken", e.state)
    }
  }

  @Test
  fun `F10_7 - con la sessione di recupero la porta del dispositivo aspetta, e dopo promote si scrive`() = runBlocking {
    val keys = FakeKeysServer()
    val old = Phone(keys)
    old.signIn()
    val code = RecoveryCode.generate()
    old.custody.startEncryption(code)
    // Il telefono nuovo entra col codice: la sessione è di recupero (b\api.md § 2).
    val fresh = Phone(keys)
    fresh.session.adopt(JsonObject(fresh.fake.login(fresh.deviceKey.publicJwk) + ("kind" to JsonPrimitive("recovery"))), "Alessio", "com.pampa.store")
    assertEquals(SessionKind.RECOVERY, (fresh.session.state.value as HostSession.SignedIn).kind)
    fresh.custody.openWithDoor(DoorSecret.recovery(code))
    assertTrue("la chiave dei dati è aperta", fresh.keyring.hasCustody)
    assertTrue(fresh.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
    assertTrue("niente porta del dispositivo da una sessione di recupero", fresh.fake.recorded.none { it.path.startsWith("/v1/keys/doors/device:") })
    // Una passkey aggiunta (o Google collegato) l'ha resa normale sul server: lo Store la promuove e scrive la porta.
    fresh.notified.clear()
    fresh.session.promote()
    assertEquals(SessionKind.FULL, (fresh.session.state.value as HostSession.SignedIn).kind)
    assertEquals(listOf(listOf("state")), fresh.notified.toList())
    fresh.custody.ensureDeviceDoor()
    assertTrue(keys.honestView().doors.any { it.doorId == "device:${fresh.fake.deviceId}" })
    // Promuovere una sessione già normale non cambia niente.
    fresh.notified.clear()
    fresh.session.promote()
    assertTrue(fresh.notified.isEmpty())
  }
}
