package dev.antigravity.fluidengine.account.host

import dev.antigravity.fluidengine.account.crypto.PampaCryptoException
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyKind
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.CustodyTamperedException
import dev.antigravity.fluidengine.account.host.custody.DoorSecret
import dev.antigravity.fluidengine.account.host.custody.FakeKeysServer
import dev.antigravity.fluidengine.account.host.custody.KeyClassPolicy
import dev.antigravity.fluidengine.account.host.custody.KeyCustody
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.server.AccountHttp
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.ServerResponse
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Il telefono delle prove R2: Store, sessione e custodia sopra il server finto dietro un vero socket. */
internal class R2Phone(tmp: TemporaryFolder, servers: MutableList<MockWebServer>, val keys: FakeKeysServer = FakeKeysServer()) {
  val fake = FakeAccountHttp(keys)
  val web = MockWebServer().apply { dispatcher = fake; start(); servers += this }
  val base = web.url("/").toString().trimEnd('/')
  val client = AccountServerClient(base, "1.10.0")
  val vault = MemoryCustodyVault()
  var clock = 0L
  val tokens = TokenCache({ clock })
  val dir: File = tmp.newFolder()
  val keyring = CustodyKeyring(vault, File(dir, "chiavi"), KeyClassPolicy.Choice.A)
  val deviceKey = SoftwareDeviceKey()
  val session = AccountSession(client, base, SessionStore(vault, File(dir, "sessione"), KeyClassPolicy.Choice.A), deviceKey, tokens, keyring, AppPolicy())

  /** Lo Store riavviato: un servizio nuovo sugli stessi file e sulla stessa cassaforte. */
  fun service() = CustodyService(client, session, keyring, AppPolicy(), { fake.accountId }, deviceId = { fake.deviceId })
  val custody = service()

  fun signIn() = session.adopt(fake.login(deviceKey.publicJwk), "Alessio", "com.pampa.store")

  /** Gli `auth` dei codici arrivati al server con `POST /v1/recovery`, in ordine. */
  fun sentAuths(): List<String> = fake.recorded.filter { it.method == "POST" && it.path == "/v1/recovery" }
    .map { Json.parseToJsonElement(it.body).jsonObject["auth"]!!.jsonPrimitive.content }

  /** La custodia che apre il server **vero** (non quello del server ostile) con [code]. */
  fun opens(code: RecoveryCode): KeyCustody = KeyCustody.openWithDoor(fake.accountId, keys.honestView(), DoorSecret.recovery(code))

  /** [block] gira mentre il server fa il lavoro di [key] ma la risposta si perde. */
  suspend fun <T> dropping(key: String, block: suspend () -> T): Result<T> {
    fake.dropping += key
    return try {
      runCatching { block() }
    } finally {
      fake.dropping -= key
    }
  }
}

/**
 * R2 (review `muse-account`): il codice di recupero impegnato sul server prima che l'utente lo veda, la prima
 * cifratura interrotta a metà, la cache dei token con due account in corsa, e le risposte storte. Su HTTP vero
 * contro il server finto, che sa perdere una risposta dopo aver fatto il lavoro ([FakeAccountHttp.dropping]).
 * Queste prove usano solo l'API di prima: sul codice di prima sono rosse.
 */
class AccountSessionR2Test {
  @get:Rule val tmp = TemporaryFolder()
  private val servers = mutableListOf<MockWebServer>()

  @After fun stop() = servers.forEach { it.shutdown() }

  private fun phone() = R2Phone(tmp, servers)

  // --- Il codice di recupero nuovo ------------------------------------------------------------------------------

  @Test
  fun `il server prende il codice nuovo e la risposta si perde, lo Store muore - al riavvio il codice e' quello del server`() = runBlocking {
    val p = phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    val before = p.sentAuths().size
    assertTrue(p.dropping("POST /v1/recovery") { p.custody.createRecoveryCode() }.exceptionOrNull() is IOException)
    // Il server ha già sostituito il codice; lo Store riparte da capo, sugli stessi file.
    val again = p.service().createRecoveryCode()
    assertEquals("al server è arrivato un solo codice nuovo, ed è quello che lo Store ha in mano", setOf(again.auth), p.sentAuths().drop(before).toSet())
    assertArrayEquals(p.keyring.loadCustody()!!.cd(1), p.opens(again).cd(1))
    // Lo stesso corpo con la stessa chiave d'idempotenza: il server vero rende la stessa risposta.
    val keysUsed = p.fake.recorded.filter { it.method == "POST" && it.path == "/v1/recovery" }.drop(before)
      .map { Regex("idempotency-key: (\\S+)").find(it.headers)?.groupValues?.get(1) }
    assertTrue(keysUsed.size >= 2)
    assertEquals(1, keysUsed.toSet().size)
    assertTrue(keysUsed.first() != null)
  }

  // --- La prima cifratura a metà ------------------------------------------------------------------------------

  @Test
  fun `la prima cifratura - il server prende l'epoca e la risposta si perde - si riprende la stessa chiave`() = runBlocking {
    val p = phone()
    p.signIn()
    val code = RecoveryCode.generate()
    assertTrue(p.dropping("POST /v1/keys/epochs") { p.custody.startEncryption(code) }.exceptionOrNull() is IOException)
    assertFalse("finché il server non ha detto sì, niente di cifrato parte (K2)", p.keyring.hasCustody)
    assertEquals(1, p.keys.honestView().epoch)
    // Lo Store riparte e riprova col codice che ha mostrato: la chiave è quella del server, e il codice la apre.
    val custody = p.service().startEncryption(code)
    assertArrayEquals(p.opens(code).cd(1), custody.cd(1))
    assertTrue(p.keyring.hasCustody)
    assertTrue(p.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
  }

  @Test
  fun `la prima cifratura - l'impronta da rifare prima del server - si riparte col codice nuovo`() = runBlocking {
    val p = phone()
    p.signIn()
    p.fake.refuseOnce["POST /v1/recovery"] = 401 to "reauth_required"
    assertTrue(runCatching { p.custody.startEncryption(RecoveryCode.generate()) }.isFailure)
    // Lo Store di oggi rifà il blocco dopo l'impronta, con un codice nuovo: il server non aveva niente, va bene così.
    val second = RecoveryCode.generate()
    val custody = p.custody.startEncryption(second)
    assertArrayEquals(p.opens(second).cd(1), custody.cd(1))
  }

  // --- La sessione con due account in corsa -----------------------------------------------------------------------

  @Test
  fun `un rinnovo in volo mentre si entra con un altro account - la cache resta del conto nuovo`() = runBlocking {
    val p = phone()
    p.signIn()
    p.fake.expireAccess()
    p.clock += 16 * 60_000
    val inside = CountDownLatch(1)
    val go = CountDownLatch(1)
    p.fake.beforeReply = { if (it == "POST /v1/session/refresh") { inside.countDown(); go.await(10, TimeUnit.SECONDS) } }
    val renewing = async(Dispatchers.IO) { p.session.storeToken() }
    assertTrue(inside.await(10, TimeUnit.SECONDS))
    p.fake.beforeReply = null
    // Esce ed entra con l'account B mentre il rinnovo di A è ancora in volo.
    p.fake.accountId = "acc_contob00000000000000000"
    val b = p.fake.login(p.deviceKey.publicJwk)
    p.session.adopt(b, "Bea", "com.pampa.store")
    go.countDown()
    val inFlight = runCatching { renewing.await() }
    val bToken = b["accessToken"]!!.jsonPrimitive.content
    assertEquals("dopo il cambio la cache ha il token di B", bToken, p.session.storeToken())
    assertEquals("anche chi aspettava riceve quello di B", bToken, inFlight.getOrNull())
  }

  @Test
  fun `un token d'app in volo mentre si entra con un altro account - non resta in cache quello di A`() = runBlocking {
    val p = phone()
    p.signIn()
    val pkg = "dev.pampa.pampai"
    val inside = CountDownLatch(1)
    val go = CountDownLatch(1)
    p.fake.beforeReply = { if (it == "POST /v1/apps/$pkg/token") { inside.countDown(); go.await(10, TimeUnit.SECONDS) } }
    val asking = async(Dispatchers.IO) { p.session.appToken(pkg, "sync", setOf("sync.data")) }
    assertTrue(inside.await(10, TimeUnit.SECONDS))
    p.fake.beforeReply = null
    p.fake.accountId = "acc_contob00000000000000000"
    p.session.adopt(p.fake.login(p.deviceKey.publicJwk), "Bea", "com.pampa.store")
    go.countDown()
    asking.await()
    val ofA = "app-$pkg-1"
    val after = p.session.appToken(pkg, "sync", setOf("sync.data")) as TokenOutcome.Ok
    assertNotEquals("il token emesso per A non va all'app dopo il cambio", ofA, after.token)
  }

  @Test
  fun `unauthorized sul token di un'app - si toglie il token dello Store e si rinnova una volta`() = runBlocking {
    val p = phone()
    p.signIn()
    // Il server non accetta più il token dello Store che sta in cache (per esempio è scaduto prima sul server).
    p.fake.expireAccess()
    val r = p.session.appToken("dev.pampa.pampai", "sync", setOf("sync.data"))
    assertTrue("era $r", r is TokenOutcome.Ok)
    assertEquals(1, p.fake.refreshes)
  }

  // --- Risposte e file storti -----------------------------------------------------------------------------------

  @Test
  fun `chiavi di lavoro che si aprono ma non si leggono - non pronte, non un crash`() = runBlocking {
    val p = phone()
    p.signIn()
    p.custody.startEncryption(RecoveryCode.generate())
    val work = File(p.dir, "chiavi/lavoro.bin")
    val cls = KeyClassPolicy.classOf(CustodyKeyKind.WORK_KEYS, KeyClassPolicy.Choice.A)
    work.writeBytes(p.vault.seal(cls, """{"current":1,"sets":"strano"}""".toByteArray(), "pampa/v1/custody|lavoro".toByteArray()))
    assertEquals(AppKeyOutcome.Locked, runCatching { p.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) }.getOrElse { it })
    // Lo Store le ricava di nuovo dalla madre, e tornano.
    assertTrue(p.custody.refreshWorkKeys())
    assertTrue(p.custody.appKey("dev.pampa.pampai", AppKeyPurpose.APP) is AppKeyOutcome.Ok)
    // Una custodia che si apre ma ha una forma inattesa: un errore della custodia, non un NullPointerException.
    val file = File(p.dir, "chiavi/custodia.bin")
    val mother = KeyClassPolicy.classOf(CustodyKeyKind.DATA_KEY, KeyClassPolicy.Choice.A)
    file.writeBytes(p.vault.seal(mother, """{"v":1,"epochs":[]}""".toByteArray(), "pampa/v1/custody|custodia".toByteArray()))
    val e = runCatching { p.keyring.loadCustody() }.exceptionOrNull()
    assertTrue("era $e", e is PampaCryptoException)
  }

  @Test
  fun `il passaggio con una risposta del server di forma storta - rifiutato pulito`() = runBlocking {
    val p = phone()
    p.signIn()
    val offer = p.custody.beginTransfer()
    // Un server rotto (o ostile) risponde 200 con sigillo e mac che non sono testi. (Senza sigillo, dalla F10.7 è
    // «aspetta ancora»: `TransferPendingException`.)
    val bad = AccountServerClient(p.base, "1.10.0", AccountHttp { _, _ -> ServerResponse(200, "{\"sealed\":1,\"mac\":2}".toByteArray(), null) })
    val service = CustodyService(bad, p.session, p.keyring, AppPolicy(), { p.fake.accountId })
    val e = runCatching { service.completeTransfer(offer) }.exceptionOrNull()
    assertTrue("era $e", e is CustodyTamperedException)
  }
}
