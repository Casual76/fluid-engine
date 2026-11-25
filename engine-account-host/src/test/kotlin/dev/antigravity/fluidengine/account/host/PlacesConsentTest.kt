package dev.antigravity.fluidengine.account.host

import android.app.Application
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.content.pm.Signature
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.account.AccountProtocol
import dev.antigravity.fluidengine.account.AccountSections
import dev.antigravity.fluidengine.account.AccountTransport
import dev.antigravity.fluidengine.account.CallResult
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.Place
import dev.antigravity.fluidengine.account.PlacesAccess
import dev.antigravity.fluidengine.account.PlacesResult
import dev.antigravity.fluidengine.account.StoreHost
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * F10.8 · Il permesso dei luoghi (scelta B8): ogni app lo chiede la prima volta, la domanda è dello Store, e dopo un
 * «no» l'app non richiede da sola. Negato, concesso, revocato; MyCloud Gallery mai. Le app vedono il cambio subito,
 * con l'avviso `places`.
 */
@RunWith(RobolectricTestRunner::class)
class PlacesConsentTest {
  @get:Rule val tmp = TemporaryFolder()

  private val weather = "dev.pampa.fluidweather"
  private val classeviva = "dev.antigravity.classevivaexpressive"
  private val gallery = "dev.pampa.mycloudgallery"

  /** Uno Store senza account e due app che lo chiamano col loro pacchetto, come lo darebbe il sistema. */
  private inner class Phone {
    val dir: File = tmp.newFolder()
    val vault = MemoryCustodyVault()
    val notified = ArrayList<List<String>>()
    val places = PlacesStore(MemoryCustodyVault(), File(dir, "luoghi"), AppPolicy()) { notified += it }
    val client = AccountServerClient("https://account.example", "1.10.0") { _, _ -> throw java.io.IOException("niente rete nelle prove") }
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val session = AccountSession(client, "https://account.example", SessionStore(vault, File(dir, "s")), SoftwareDeviceKey(), TokenCache({ 0L }), keyring, AppPolicy())
    val handler = AccountProtocolHandler(session, CustodyService(client, session, keyring, AppPolicy(), { null }), AppPolicy(), places, AppearanceStore(File(dir, "aspetto")), { emptyList() }, "1.10.0")
    val consent = PlacesConsent(places, AppPolicy())

    fun app(pkg: String) = PampaAccountClient(
      { StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") },
      AccountTransport { _, method, request -> CallResult.Ok(runBlocking { handler.handle(pkg, method, request) }) },
    )

    /** L'avviso dello Store, consegnato a mano alle app (in un telefono vero è `AccountChangedBroadcaster`). */
    suspend fun deliver(vararg apps: PampaAccountClient) {
      val what = notified.flatten().distinct()
      notified.clear()
      for (a in apps) a.onChanged(what)
    }
  }

  @Test
  fun `la prima volta l'app non li ha, la domanda e dello Store, e un no resta un no`() = runBlocking {
    val phone = Phone()
    phone.places.put(null, Place("casa", "Casa", Place.HOME, 43.77, 11.25))
    phone.places.put(null, Place("scuola", "Scuola", Place.SCHOOL, 43.78, 11.26))
    val app = phone.app(weather).also { it.refresh() }
    assertEquals(PlacesAccess.NOT_ASKED, app.placesAccess())
    assertEquals(PlacesResult.NotAllowed, app.places())

    val ask = phone.consent.begin(weather, write = false) as PlacesConsent.Step.Ask
    assertEquals("Fluid Weather vuole usare i tuoi luoghi (Casa, Scuola). Consenti?", ask.question)
    assertEquals(PlacesAccess.DENIED, phone.consent.answer(ask, allow = false))
    phone.deliver(app)
    assertEquals(PlacesAccess.DENIED, app.placesAccess())
    assertEquals(PlacesResult.NotAllowed, app.places())
    // L'app chiede di nuovo: lo Store non fa una seconda domanda.
    assertEquals(PlacesConsent.Step.Decided(PlacesAccess.DENIED), phone.consent.begin(weather, write = false))
  }

  @Test
  fun `concesso, poi revocato dalla pagina dell'account - l'app lo vede all'avviso`() = runBlocking {
    val phone = Phone()
    phone.places.put(null, Place("casa", "Casa", Place.HOME, 43.77, 11.25))
    val app = phone.app(weather).also { it.refresh() }
    val seen = ArrayList<PlacesResult>()
    val flow = app.placesFlow
    seen += flow.first()
    val ask = phone.consent.begin(weather, write = false) as PlacesConsent.Step.Ask
    assertEquals(PlacesAccess.READ, phone.consent.answer(ask, allow = true))
    phone.deliver(app)
    seen += flow.first()
    val ok = seen.last() as PlacesResult.Ok
    assertEquals(listOf("Casa"), ok.places.map { it.name })
    assertFalse(ok.canWrite)
    // Revocato: l'utente lo toglie dallo Store.
    assertTrue(phone.places.setAccess(weather, PlaceAccess.NONE))
    phone.deliver(app)
    assertEquals(PlacesResult.NotAllowed, flow.first())
    assertEquals(PlacesAccess.DENIED, app.placesAccess())
    // «Chiedi di nuovo»: l'app torna a poter chiedere.
    phone.places.forget(weather)
    assertEquals(PlacesAccess.NOT_ASKED, app.placesAccess())
    assertEquals(listOf(PlacesResult.NotAllowed, ok), seen)
  }

  @Test
  fun `chi legge e vuole anche scrivere - una domanda in piu, e un no lascia la lettura`() = runBlocking {
    val phone = Phone()
    val app = phone.app(classeviva).also { it.refresh() }
    phone.consent.answer(phone.consent.begin(classeviva, write = false) as PlacesConsent.Step.Ask, allow = true)
    assertEquals(PlacesConsent.Step.Decided(PlacesAccess.READ), phone.consent.begin(classeviva, write = false))
    val write = phone.consent.begin(classeviva, write = true) as PlacesConsent.Step.Ask
    assertEquals("ClasseViva Expressive vuole usare e aggiungere i tuoi luoghi. Consenti?", write.question)
    assertEquals(PlacesAccess.READ, phone.consent.answer(write, allow = false))
    assertEquals(PlacesResult.NotAllowed, app.putPlace(Place("scuola", "Scuola", Place.SCHOOL, 43.0, 11.0)))
    assertEquals(PlacesAccess.READ_WRITE, phone.consent.answer(write, allow = true))
    assertEquals(listOf("Scuola"), (app.putPlace(Place("scuola", "Scuola", Place.SCHOOL, 43.0, 11.0)) as PlacesResult.Ok).places.map { it.name })
  }

  @Test
  fun `MyCloud Gallery mai, e chi non si sa non riceve domande`() = runBlocking {
    val phone = Phone()
    val app = phone.app(gallery).also { it.refresh() }
    assertEquals(PlacesAccess.NEVER, app.placesAccess())
    assertEquals(PlacesConsent.Step.Decided(PlacesAccess.NEVER), phone.consent.begin(gallery, write = false))
    assertFalse(phone.places.setAccess(gallery, PlaceAccess.READ))
    assertEquals(PlacesConsent.Step.Refused, phone.consent.begin(null, write = false))
    assertEquals(PlacesConsent.Step.Refused, phone.consent.begin("com.altra.app", write = false))
    assertEquals(PlacesAccess.NEVER, phone.app("com.altra.app").also { it.refresh() }.placesAccess())
    // L'intent della richiesta: la sezione giusta, e senza NEW_TASK (il risultato deve tornare a chi chiede).
    val intent = phone.app(weather).also { it.refresh() }.placesRequestIntent(write = true)!!
    assertTrue(PlacesConsent.isRequest(intent))
    assertTrue(PlacesConsent.wantsWrite(intent))
    assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    assertEquals(AccountSections.PLACES_REQUEST, intent.getStringExtra(AccountProtocol.EXTRA_SECTION))
  }

  @Test
  fun `l'avviso va solo alle app col ricevitore e la firma dello Store`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val pm = shadowOf(app.packageManager)
    val mine = Signature("cafe")
    fun install(pkg: String, sig: Signature) = pm.installPackage(PackageInfo().apply {
      packageName = pkg
      signatures = arrayOf(sig)
      applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = pkg }
    })
    install(app.packageName, mine)
    install("app.uno", mine)
    install("app.due", mine)
    install("altra.firma", Signature("beef"))
    for (pkg in listOf("app.uno", "app.due", "altra.firma", app.packageName)) {
      pm.addResolveInfoForIntent(Intent(AccountProtocol.ACTION_CHANGED), ResolveInfo().apply {
        activityInfo = ActivityInfo().apply { packageName = pkg; name = "$pkg.R" }
      })
    }
    val broadcaster = AccountChangedBroadcaster(app)
    assertEquals(listOf("app.uno", "app.due"), broadcaster.receivers())
    assertEquals(2, broadcaster.send(listOf("appearance")))
    val sent = shadowOf(app).broadcastIntents.filter { it.action == AccountProtocol.ACTION_CHANGED }
    assertEquals(listOf("app.uno", "app.due"), sent.map { it.`package` })
    assertEquals(arrayListOf("appearance"), sent.first().getStringArrayListExtra(AccountProtocol.EXTRA_WHAT))
    assertNull(sent.firstOrNull { it.`package` == "altra.firma" })
  }
}
