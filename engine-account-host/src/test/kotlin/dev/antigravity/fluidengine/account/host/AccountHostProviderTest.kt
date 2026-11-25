package dev.antigravity.fluidengine.account.host

import android.content.pm.PackageManager
import android.os.Bundle
import dev.antigravity.fluidengine.account.AccountCache
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.AccountState
import dev.antigravity.fluidengine.account.AppKeyResult
import dev.antigravity.fluidengine.account.CallResult
import dev.antigravity.fluidengine.account.KeyPurpose
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.Place
import dev.antigravity.fluidengine.account.PlacesResult
import dev.antigravity.fluidengine.account.StoreHost
import dev.antigravity.fluidengine.account.TokenResult
import dev.antigravity.fluidengine.account.host.custody.AppKeyPurpose
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.custody.RecoveryCode
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/**
 * Il provider firmato dello Store da capo a fondo, col cliente vero di `engine-account` dall'altra parte: uno Store
 * finto e firme diverse (minacce M1: «ogni metodo deve rifiutare»), il pacchetto dal sistema, la tabella delle app,
 * le chiavi solo di chi chiama (K3), i luoghi col permesso, l'aspetto senza account, e `wiped` confermato con lo Store.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountHostProviderTest {
  @get:Rule val tmp = TemporaryFolder()
  private lateinit var web: MockWebServer

  @After fun stop() = web.shutdown()

  private class TestProvider(
    private val h: AccountProtocolHandler,
    private val signature: (Int) -> Int,
    private val packages: (Int) -> Array<String>?,
  ) : AccountHostProvider() {
    override fun handler() = h
    override fun compareSignatures(callingUid: Int, ownUid: Int) = signature(callingUid)
    override fun packagesForUid(uid: Int) = packages(uid)
  }

  private class MemoryCache : AccountCache {
    val map = HashMap<String, String>()
    override suspend fun read(key: String) = map[key]
    override suspend fun write(key: String, value: String?) {
      if (value == null) map.remove(key) else map[key] = value
    }
  }

  private val pampai = 10_001
  private val notes = 10_002
  private val gallery = 10_003
  private val stranger = 10_004
  private val shared = 10_005
  private val weather = 10_006

  private inner class Store {
    val fake = FakeAccountHttp()
    val base: String
    init {
      web = MockWebServer().apply { dispatcher = fake; start() }
      base = web.url("/").toString().trimEnd('/')
    }
    val client = AccountServerClient(base, "1.10.0")
    val vault = MemoryCustodyVault()
    val dir: File = tmp.newFolder()
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val deviceKey = SoftwareDeviceKey()
    val notified = mutableListOf<List<String>>()
    val session = AccountSession(client, base, SessionStore(vault, File(dir, "s")), deviceKey, TokenCache({ 0L }), keyring, AppPolicy()) { notified += it }
    val custody = CustodyService(client, session, keyring, AppPolicy(), { fake.accountId })
    val places = PlacesStore(vault, File(dir, "luoghi"), AppPolicy())
    val appearance = AppearanceStore(File(dir, "aspetto"))
    val handler = AccountProtocolHandler(session, custody, AppPolicy(), places, appearance, { emptyList() }, "1.10.0")
    val provider = TestProvider(
      handler,
      signature = { uid -> if (uid == stranger) PackageManager.SIGNATURE_NO_MATCH else PackageManager.SIGNATURE_MATCH },
      packages = { uid ->
        when (uid) {
          pampai -> arrayOf("dev.pampa.pampai")
          notes -> arrayOf("dev.pampa.pampanotes")
          gallery -> arrayOf("dev.pampa.mycloudgallery")
          weather -> arrayOf("dev.pampa.fluidweather")
          shared -> arrayOf("dev.pampa.pampai", "dev.pampa.pampanotes")
          stranger -> arrayOf("com.altra.firma")
          else -> null
        }
      },
    )

    fun signIn() = session.adopt(fake.login(deviceKey.publicJwk), "Alessio Pampaloni", "com.pampa.store")

    fun clientFor(uid: Int, cache: AccountCache? = null) = PampaAccountClient(
      { StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") },
      { _, method, request ->
        ShadowBinder.setCallingUid(uid)
        try {
          val b = provider.call(method, null, Bundle().apply { putString(AccountProtocol.KEY_JSON, request.toString()) })
          CallResult.Ok(kotlinx.serialization.json.Json.parseToJsonElement(b.getString(AccountProtocol.KEY_JSON)!!) as JsonObject)
        } catch (_: SecurityException) {
          CallResult.Denied
        }
      },
      cache,
    )
  }

  private val allMethods = listOf(
    AccountProtocol.METHOD_NEGOTIATE, AccountProtocol.METHOD_STATE, AccountProtocol.METHOD_TOKEN, AccountProtocol.METHOD_GOOGLE,
    AccountProtocol.METHOD_GRANTS, AccountProtocol.METHOD_APPEARANCE, AccountProtocol.METHOD_SET_APPEARANCE, AccountProtocol.METHOD_APP_KEY,
    AccountProtocol.METHOD_PLACES_LIST, AccountProtocol.METHOD_PLACES_PUT, AccountProtocol.METHOD_PLACES_DELETE,
    AccountProtocol.METHOD_BACKUP_AVAILABLE, AccountProtocol.METHOD_OPEN,
  )

  @Test
  fun `M1 - un'app con un'altra firma riceve SecurityException da ogni metodo, e vede lo Store assente`() = runBlocking {
    val store = Store()
    store.signIn()
    ShadowBinder.setCallingUid(stranger)
    for (m in allMethods) {
      try {
        store.provider.call(m, null, Bundle())
        fail("$m doveva rifiutare")
      } catch (_: SecurityException) {
      }
    }
    assertEquals(AccountState.NoStore, store.clientFor(stranger).refresh())
    // Un UID con due pacchetti non si sa chi sia: rifiutato anche lui.
    ShadowBinder.setCallingUid(shared)
    assertTrue(runCatching { store.provider.call(AccountProtocol.METHOD_STATE, null, Bundle()) }.exceptionOrNull() is SecurityException)
  }

  @Test
  fun `senza account - lo stato dice SignedOut, l'aspetto comune risponde lo stesso`() = runBlocking {
    val store = Store()
    store.appearance.setGlobal(mapOf("appearance.mode" to "dark", "chiave.sconosciuta" to "x"))
    val client = store.clientFor(pampai)
    assertEquals(AccountState.SignedOut, client.refresh())
    assertEquals("dark", client.appearance.value.global["appearance.mode"])
    assertFalse(client.appearance.value.global.containsKey("chiave.sconosciuta"))
    assertEquals(TokenResult.SignedOut, client.token("sync", setOf("sync.data")))
    // L'eccezione di un'app vale solo per lei.
    client.setAppearanceException(false, mapOf("appearance.mode" to "light"))
    assertEquals("light", client.appearance.value.effective("appearance.mode"))
    assertEquals("dark", store.clientFor(notes).also { it.refresh() }.appearance.value.effective("appearance.mode"))
  }

  @Test
  fun `con l'account - chi sei, e un token solo per cio' che e' tuo`() = runBlocking {
    val store = Store()
    store.signIn()
    val client = store.clientFor(pampai)
    val state = client.refresh() as AccountState.SignedIn
    assertEquals("AP", state.profile.initials)
    assertEquals("Alessio Pampaloni", state.profile.displayName)
    assertTrue(client.token("sync", setOf("sync.data")) is TokenResult.Ok)
    assertEquals(TokenResult.NotAllowed, client.token("notes", setOf("notes.sync")))
    val notesClient = store.clientFor(notes).also { it.refresh() }
    assertTrue(notesClient.token("notes", setOf("notes.sync")) is TokenResult.Ok)
    assertEquals(TokenResult.NotAllowed, notesClient.token("sync", setOf("sync.data")))
    assertEquals(TokenResult.NotAllowed, store.clientFor(gallery).also { it.refresh() }.token("sync", setOf("sync.backup")))
  }

  @Test
  fun `K3 - appKey da' a ogni app solo le sue chiavi, anche a telefono bloccato`() = runBlocking {
    val store = Store()
    store.signIn()
    val client = store.clientFor(pampai).also { it.refresh() }
    assertEquals(AppKeyResult.NoE2e, client.appKey(KeyPurpose.APP))
    val custody = store.custody.startEncryption(RecoveryCode.generate())
    store.vault.locked = true
    val mine = client.appKey(KeyPurpose.APP) as AppKeyResult.Ok
    assertArrayEquals(custody.appKeys("dev.pampa.pampai", AppKeyPurpose.APP).keys.getValue(1), mine.currentKey)
    assertFalse(mine.currentKey.contentEquals(custody.appKeys("dev.antigravity.fluidtransit", AppKeyPurpose.APP).keys.getValue(1)))
    assertFalse(mine.currentKey.contentEquals(custody.appKeys("dev.pampa.pampai", AppKeyPurpose.BACKUP).keys.getValue(1)))
    assertEquals(AppKeyResult.NotAllowed, store.clientFor(weather).also { it.refresh() }.appKey(KeyPurpose.APP))
    assertTrue(store.clientFor(weather).also { it.refresh() }.appKey(KeyPurpose.BACKUP) is AppKeyResult.Ok)
    assertEquals(AppKeyResult.NotAllowed, store.clientFor(notes).also { it.refresh() }.appKey(KeyPurpose.APP))
  }

  @Test
  fun `i luoghi - solo col permesso, scrittura a parte, MyCloud Gallery mai`() = runBlocking {
    val store = Store()
    store.places.put(null, Place("casa", "Casa", Place.HOME, 43.77, 11.25, "Via Roma 1"))
    val client = store.clientFor(pampai).also { it.refresh() }
    assertEquals(PlacesResult.NotAllowed, client.places())
    store.places.setAccess("dev.pampa.pampai", PlaceAccess.READ)
    val read = client.places() as PlacesResult.Ok
    assertEquals(listOf("Casa"), read.places.map { it.name })
    assertFalse(read.canWrite)
    assertEquals(PlacesResult.NotAllowed, client.putPlace(Place("scuola", "Scuola", Place.SCHOOL, 43.0, 11.0)))
    store.places.setAccess("dev.pampa.pampai", PlaceAccess.READ_WRITE)
    assertEquals(2, (client.putPlace(Place("scuola", "Scuola", Place.SCHOOL, 43.0, 11.0)) as PlacesResult.Ok).places.size)
    assertFalse(store.places.setAccess("dev.pampa.mycloudgallery", PlaceAccess.READ))
    assertEquals(PlacesResult.NotAllowed, store.clientFor(gallery).also { it.refresh() }.places())
  }

  @Test
  fun `wiped - solo dopo un'uscita da remoto confermata dallo Store, e una volta sola`() = runBlocking {
    val store = Store()
    store.signIn()
    val cache = MemoryCache()
    val client = store.clientFor(pampai, cache).also { it.refresh() }
    var wiped = 0
    client.onWiped { wiped++ }
    // Un broadcast falso con dentro «wiped», mentre si è dentro: lo Store non lo conferma.
    client.onChanged(listOf("wiped"))
    assertEquals(0, wiped)
    // Uscita volontaria: niente wiped.
    store.session.signOut()
    client.onChanged(listOf("state", "wiped"))
    assertEquals(0, wiped)
    // Entrata di nuovo e poi uscita da remoto: confermata, una volta.
    store.signIn()
    store.session.onRevoked("remote")
    assertEquals(listOf("state", "wiped"), store.notified.last())
    client.onChanged(listOf("state", "wiped"))
    assertEquals(1, wiped)
    client.onChanged(listOf("wiped"))
    assertEquals(1, wiped)
    assertEquals(AccountState.SignedOut, client.state.value)
    // Un cliente senza copia locale non cancella due volte la stessa uscita.
    val bare = store.clientFor(pampai).also { it.refresh() }
    var bareWiped = 0
    bare.onWiped { bareWiped++ }
    bare.onChanged(listOf("wiped"))
    bare.onChanged(listOf("wiped"))
    assertEquals(1, bareWiped)
  }
}
