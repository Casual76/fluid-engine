package dev.antigravity.fluidengine.account.host

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.CallResult
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.PeopleCalls
import dev.antigravity.fluidengine.account.PeopleRowKind
import dev.antigravity.fluidengine.account.ShareKind
import dev.antigravity.fluidengine.account.ShareResult
import dev.antigravity.fluidengine.account.StoreHost
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import dev.antigravity.fluidengine.people.PeopleClient
import dev.antigravity.fluidengine.people.PeopleEvent
import dev.antigravity.fluidengine.people.PeopleStateStore
import dev.antigravity.fluidengine.people.PersonaStore
import dev.antigravity.fluidengine.people.client.PeopleApi
import dev.antigravity.fluidengine.people.store.AndroidAppNotifier
import dev.antigravity.fluidengine.people.store.PeopleBell
import dev.antigravity.fluidengine.people.store.PeopleDispatcher
import dev.antigravity.fluidengine.people.store.PeopleProviderMethods
import dev.antigravity.fluidengine.people.testing.FakePeopleServer
import dev.antigravity.fluidengine.people.testing.MemoryPersonVault
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBinder

/**
 * Il passaggio **Store → app col ponte firmato** (`fasi.md` F10.6; `d\server-e-chiavi.md` § 6): il campanello finto sveglia
 * lo Store di chi riceve, lo Store ritira e apre la busta, avvisa Notes con il broadcast esplicito, e Notes rilegge dal
 * provider firmato dello Store e mostra **la sua** notifica. Il provider è quello vero (`AccountHostProvider`, con il
 * controllo della firma e il pacchetto dal sistema), il cliente è quello vero di `engine-account`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeopleBridgeTest {
  @get:Rule val tmp = TemporaryFolder()
  private lateinit var web: MockWebServer
  private val context get() = ApplicationProvider.getApplicationContext<Application>()

  @After fun stop() = web.shutdown()

  private class TestProvider(private val h: AccountProtocolHandler, private val packages: (Int) -> Array<String>?) : AccountHostProvider() {
    override fun handler() = h
    override fun compareSignatures(callingUid: Int, ownUid: Int) = if (callingUid == STRANGER) PackageManager.SIGNATURE_NO_MATCH else PackageManager.SIGNATURE_MATCH
    override fun packagesForUid(uid: Int) = packages(uid)
  }

  private fun person(server: FakePeopleServer, name: String): PeopleClient {
    val dir = tmp.newFolder()
    val vault = MemoryPersonVault()
    val device = server.newAccount().devices[0]
    val persona = PersonaStore(dir, vault, clock = { server.now })
    persona.create(name)
    return PeopleClient(persona, PeopleStateStore(File(dir, "persone.bin"), vault), PeopleApi(device.http, device.auth), clock = { server.now }).also { runBlocking { it.publish() } }
  }

  @Test
  fun `il campanello sveglia lo Store, che passa la cosa a Notes, e Notes mostra la sua notifica`() = runBlocking {
    // La rete delle persone (finta) e due persone: Alessio manda, la mamma riceve.
    val people = FakePeopleServer()
    val alessio = person(people, "Alessio")
    val mamma = person(people, "Mamma")
    alessio.request(alessio.lookup(mamma.identity().code), "ciao mamma")
    mamma.accept(mamma.pickup().filterIsInstance<PeopleEvent.Request>().single().request)
    alessio.pickup()
    mamma.pickup()

    // Lo Store della mamma: sessione dell'account, provider firmato coi metodi delle persone.
    val fake = FakeAccountHttp()
    web = MockWebServer().apply { dispatcher = fake; start() }
    val base = web.url("/").toString().trimEnd('/')
    val vault = MemoryCustodyVault()
    val dir = tmp.newFolder()
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val deviceKey = SoftwareDeviceKey()
    val client = AccountServerClient(base, "1.10.0")
    val session = AccountSession(client, base, SessionStore(vault, File(dir, "s")), deviceKey, TokenCache({ 0L }), keyring, AppPolicy()) { }
    val handler = AccountProtocolHandler(
      session, CustodyService(client, session, keyring, AppPolicy(), { fake.accountId }), AppPolicy(),
      PlacesStore(vault, File(dir, "luoghi"), AppPolicy()), AppearanceStore(File(dir, "aspetto")), { emptyList() }, "1.10.0",
      people = PeopleProviderMethods(mamma, hasOtherDevices = { false }),
    )
    session.adopt(fake.login(deviceKey.publicJwk), "Mamma", "com.pampa.store")
    val provider = TestProvider(handler) { uid -> when (uid) { NOTES_UID -> arrayOf(NOTES); STRANGER -> arrayOf("com.altra.firma"); else -> null } }

    // Notes della mamma: il cliente vero, che passa dal provider con l'UID di Notes.
    fun appClient(uid: Int) = PampaAccountClient(
      { StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") },
      { _, method, request ->
        ShadowBinder.setCallingUid(uid)
        try {
          val b = provider.call(method, null, Bundle().apply { putString(AccountProtocol.KEY_JSON, request.toString()) })
          CallResult.Ok(Json.parseToJsonElement(b.getString(AccountProtocol.KEY_JSON)!!) as JsonObject)
        } catch (_: SecurityException) {
          CallResult.Denied
        }
      },
    )
    val notes = appClient(NOTES_UID)
    notes.refresh()
    assertTrue("lo Store annuncia le persone", "people" in notes.storeCapabilities)
    val row = PeopleCalls.row(notes)
    assertEquals(PeopleRowKind.PERSON, row.single().kind)
    assertEquals("Alessio", row.single().name)

    // Notes mostra la sua notifica quando lo Store avvisa: rilegge dal provider, non si fida del broadcast.
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(NotificationChannel("condivisi", "Condivisi con te", NotificationManager.IMPORTANCE_DEFAULT))
    notes.addChangeListener { what ->
      if (AccountProtocol.What.SHARE in what) for (item in PeopleCalls.received(notes, 0)) {
        nm.notify(item.id.hashCode(), android.app.Notification.Builder(context, "condivisi").setSmallIcon(android.R.drawable.ic_dialog_info)
          .setContentTitle("${item.from.name} ti ha condiviso una lista").setContentText(item.title).build())
      }
    }

    // Alessio condivide la lista della spesa con la mamma, da Notes.
    PeopleProviderMethods(alessio).handle(NOTES, AccountProtocol.METHOD_SHARE_SEND, Json.parseToJsonElement(
      """{"to":"${PeopleProviderMethods(alessio).handle(NOTES, AccountProtocol.METHOD_PEOPLE_ROW, null)["row"].toString().let { Regex("\"id\":\"([^\"]+)\",\"kind\":\"person\"").find(it)!!.groupValues[1] }}","kind":"live","title":"La spesa","payload":"{}"}""",
    ) as JsonObject)

    // Notes è installata sul telefono della mamma.
    Shadows.shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = NOTES })
    // Il campanello finto: data-only e vuoto. Lo Store ritira, apre, e avvisa Notes col broadcast esplicito.
    val bell = PeopleBell(mamma, PeopleDispatcher(mamma, AndroidAppNotifier(context), clock = { people.now }))
    assertEquals(setOf(NOTES), bell.ring(mapOf("k" to "posta")))
    val sent = Shadows.shadowOf(context).broadcastIntents.single { it.action == AccountProtocol.ACTION_CHANGED }
    assertEquals(NOTES, sent.`package`)
    val what = sent.getStringArrayListExtra(AccountProtocol.EXTRA_WHAT)!!
    assertEquals(listOf(AccountProtocol.What.SHARE), what)
    // Il sistema consegna l'avviso a Notes: il suo ricevitore rilegge e notifica.
    notes.onChanged(what)
    val shown = Shadows.shadowOf(nm).allNotifications
    assertEquals(1, shown.size)
    assertEquals("Alessio ti ha condiviso una lista", Shadows.shadowOf(shown.single()).contentTitle)
    assertEquals("La spesa", Shadows.shadowOf(shown.single()).contentText)

    // «Rispondi» da Notes torna ad Alessio; un'app con un'altra firma non vede niente (M1).
    val item = PeopleCalls.received(notes, 0).single()
    assertTrue(PeopleCalls.reply(notes, item.id, "Grazie!") is ShareResult.Queued)
    assertTrue(alessio.pickup().any { it is PeopleEvent.Message })
    val stranger = appClient(STRANGER)
    assertTrue(PeopleCalls.row(stranger).isEmpty())
    assertTrue(PeopleCalls.send(stranger, row.single().id, ShareKind.COPY, "x", "{}") is ShareResult.NoPeople)
  }

  private companion object {
    const val NOTES = "dev.pampa.pampanotes"
    const val NOTES_UID = 10_002
    const val STRANGER = 10_004
  }
}
