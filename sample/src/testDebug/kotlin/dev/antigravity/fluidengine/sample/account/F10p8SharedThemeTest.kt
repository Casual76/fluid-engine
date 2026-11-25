package dev.antigravity.fluidengine.sample.account

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.account.AccountTransport
import dev.antigravity.fluidengine.account.CallResult
import dev.antigravity.fluidengine.account.PampaAccountClient
import dev.antigravity.fluidengine.account.PampaAppearance
import dev.antigravity.fluidengine.account.PampaSharedAppearance
import dev.antigravity.fluidengine.account.StoreHost
import dev.antigravity.fluidengine.account.host.AccountProtocolHandler
import dev.antigravity.fluidengine.account.host.AccountSession
import dev.antigravity.fluidengine.account.host.AppPolicy
import dev.antigravity.fluidengine.account.host.AppearanceStore
import dev.antigravity.fluidengine.account.host.CustodyService
import dev.antigravity.fluidengine.account.host.PlacesStore
import dev.antigravity.fluidengine.account.host.custody.CustodyKeyring
import dev.antigravity.fluidengine.account.host.server.AccountServerClient
import dev.antigravity.fluidengine.account.host.server.SessionStore
import dev.antigravity.fluidengine.account.host.server.TokenCache
import dev.antigravity.fluidengine.account.host.testing.MemoryCustodyVault
import dev.antigravity.fluidengine.account.host.testing.SoftwareDeviceKey
import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.storage.EngineAppearanceLayer
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.antigravity.fluidengine.storage.EngineSharedAppearance
import dev.antigravity.fluidengine.ui.fluid.FluidSystemContrast
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassPreference
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSystemContrast
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F10.8 · Il tema comune col `FluidTheme` vero. Uno Store finto (il motore di `engine-account-host`, senza account) e
 * **due app finte sullo stesso telefono**, ognuna col suo `PampaAccountClient` e il suo `EngineSettingsStore`: lo
 * Store cambia l'aspetto comune, le due app cambiano tema insieme. Poi una prende la sua eccezione e l'altra no.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w420dp-h640dp-xhdpi")
class F10p8SharedThemeTest {
  @get:Rule val compose = createComposeRule()
  @get:Rule val tmp = TemporaryFolder()

  private val context: Context get() = ApplicationProvider.getApplicationContext()

  /** Quello che una app finta vede dentro il suo tema. */
  private class Seen {
    var dark = false
    var primary = Color.Unspecified
    var glass = Float.NaN
  }

  private inner class Phone {
    val dir: File = tmp.newFolder()
    val vault = MemoryCustodyVault()
    val notified = ArrayList<String>()
    val appearance = AppearanceStore(File(dir, "aspetto")) { notified += it }
    val client = AccountServerClient("https://account.example", "1.10.0") { _, _ -> throw java.io.IOException("niente rete") }
    val keyring = CustodyKeyring(vault, File(dir, "chiavi"))
    val session = AccountSession(client, "https://account.example", SessionStore(vault, File(dir, "s")), SoftwareDeviceKey(), TokenCache({ 0L }), keyring, AppPolicy())
    val handler = AccountProtocolHandler(
      session, CustodyService(client, session, keyring, AppPolicy(), { null }), AppPolicy(),
      PlacesStore(MemoryCustodyVault(), File(dir, "luoghi"), AppPolicy()), appearance, { emptyList() }, "1.10.0",
    )

    fun app(pkg: String) = PampaAccountClient(
      { StoreHost("com.pampa.store", "com.pampa.store.account", "1.10.0") },
      AccountTransport { _, method, request -> CallResult.Ok(runBlocking { handler.handle(pkg, method, request) }) },
    )

    /** L'avviso `appearance` dello Store, consegnato alle app (sul telefono è un broadcast esplicito). */
    fun deliver(vararg apps: PampaAccountClient) = runBlocking {
      val what = notified.distinct()
      notified.clear()
      for (a in apps) a.onChanged(what)
    }
  }

  /** La sorgente dell'aspetto comune di una app finta: il suo cliente, al posto di quello del processo. */
  private fun sourceOf(app: PampaAccountClient) = object : EngineSharedAppearance {
    override fun layer(context: Context) = app.sharedLayer
    override suspend fun setException(context: Context, followShared: Boolean, exception: EngineAppearanceLayer?) {
      app.setAppearanceException(followShared, exception?.let(PampaAppearance::exceptionValues))
    }
  }

  @Composable
  private fun AppFinta(store: EngineSettingsStore, seen: Seen) {
    val settings by store.settings.collectAsState(initial = null)
    val s = settings ?: return
    FluidTheme(s, systemBars = false) {
      seen.dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
      seen.primary = MaterialTheme.colorScheme.primary
      seen.glass = LocalFluidGlassPreference.current
    }
  }

  @Test
  fun `due app sullo stesso telefono cambiano tema insieme, e l'eccezione di una vale solo per lei`() {
    val phone = Phone()
    val weather = phone.app("dev.pampa.fluidweather")
    val pampai = phone.app("dev.pampa.pampai")
    runBlocking { weather.refresh(); pampai.refresh() }
    val storeWeather = EngineSettingsStore(context, sourceOf(weather))
    val storePampai = EngineSettingsStore(context, sourceOf(pampai))
    val a = Seen()
    val b = Seen()
    compose.setContent {
      Row {
        AppFinta(storeWeather, a)
        AppFinta(storePampai, b)
      }
    }
    compose.waitUntil(5_000) { !a.primary.isUnspecified && !b.primary.isUnspecified }
    // All'inizio, senza scelte comuni: il tema dell'app (chiaro, nel sistema chiaro di Robolectric) e il vetro dell'app.
    assertTrue(!a.dark && !b.dark)
    assertEquals(0.5f, a.glass)
    val brand = a.primary

    // Lo Store cambia l'aspetto comune: scuro, vetro colorato, accento arancio.
    phone.appearance.setGlobal(mapOf("appearance.mode" to "dark", "appearance.glass" to "tinted", "appearance.accent" to "#FF9500"))
    phone.deliver(weather, pampai)
    compose.waitUntil(5_000) { a.dark && b.dark }
    compose.waitForIdle()
    assertEquals(0.8f, a.glass)
    assertEquals(0.8f, b.glass)
    assertEquals(a.primary, b.primary)
    assertTrue(a.primary != brand)

    // PampAI prende la sua eccezione (chiaro) dalla sua sezione Aspetto: la scrive lo Store, vale solo per PampAI.
    runBlocking { storePampai.setSharedException(followShared = false, exception = EngineAppearanceLayer(themeMode = ThemeMode.LIGHT)) }
    phone.deliver(weather, pampai)
    compose.waitUntil(5_000) { !b.dark }
    compose.waitForIdle()
    assertTrue(a.dark)
    // Il resto dell'eccezione segue il comune: il vetro resta colorato anche per PampAI.
    assertEquals(0.8f, b.glass)

    // Lo Store cambia ancora il comune: Weather segue, PampAI tiene il suo chiaro.
    phone.appearance.setGlobal(mapOf("appearance.mode" to "light", "appearance.glass" to "clear"))
    phone.deliver(weather, pampai)
    compose.waitUntil(5_000) { !a.dark && a.glass == 0.5f && b.glass == 0.5f }
    phone.appearance.setGlobal(mapOf("appearance.mode" to "dark"))
    phone.deliver(weather, pampai)
    compose.waitUntil(5_000) { a.dark }
    compose.waitForIdle()
    assertTrue(!b.dark)

    // «Segui Pampa Store»: PampAI torna al comune.
    runBlocking { storePampai.setSharedException(followShared = true) }
    phone.deliver(weather, pampai)
    compose.waitUntil(5_000) { b.dark }
  }

  @Test
  fun `senza Store ogni app ha il suo tema, e con engine-account la sorgente si trova da sola`() {
    // engine-account e' nelle dipendenze dei test: ServiceLoader trova la sua sorgente, senza codice nell'app.
    assertTrue(EngineSharedAppearance.current() is PampaSharedAppearance)
    val noStore = PampaAccountClient({ null }, AccountTransport { _, _, _ -> CallResult.Missing })
    runBlocking { noStore.refresh() }
    val store = EngineSettingsStore(context, sourceOf(noStore))
    val seen = Seen()
    compose.setContent { AppFinta(store, seen) }
    compose.waitUntil(5_000) { !seen.primary.isUnspecified }
    assertTrue(!seen.dark)
    assertEquals(0.5f, seen.glass)
  }

  @Test
  fun `i campi nuovi arrivano al tema, e di serie il tema resta quello di prima`() {
    var settings by mutableStateOf(EngineSettings())
    var glass = Float.NaN
    var fontScale = Float.NaN
    var contrast = Float.NaN
    var reduced = true
    compose.setContent {
      // Un'app che fornisce da se' il cursore del vetro attorno al tema: di serie il tema non lo tocca.
      CompositionLocalProvider(LocalFluidGlassPreference provides 0.3f) {
        FluidTheme(settings, systemBars = false) {
          glass = LocalFluidGlassPreference.current
          fontScale = LocalDensity.current.fontScale
          contrast = LocalFluidSystemContrast.current
          reduced = LocalFluidMotionPolicy.current.reducedMotion
        }
      }
    }
    compose.waitForIdle()
    assertEquals(0.3f, glass)
    assertEquals(1f, fontScale)
    assertEquals(FluidSystemContrast.Standard, contrast)
    assertEquals(false, reduced)

    settings = EngineSettings(glassPreference = 0.8f, textScale = 1.25f, highContrast = true, reducedMotion = true)
    compose.waitForIdle()
    assertEquals(0.8f, glass)
    assertEquals(1.25f, fontScale)
    assertEquals(FluidSystemContrast.High, contrast)
    assertEquals(true, reduced)
  }

  @Test
  fun `l'accento comune viaggia come colore e diventa il preset con quel colore`() {
    var name by mutableStateOf("ember")
    var primary = Color.Unspecified
    compose.setContent {
      FluidTheme(EngineSettings(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = name), systemBars = false) {
        primary = MaterialTheme.colorScheme.primary
      }
    }
    compose.waitForIdle()
    val ember = primary
    name = "fluid"
    compose.waitForIdle()
    assertTrue(primary != ember)
    name = "#ff9500"
    compose.waitForIdle()
    assertEquals(ember, primary)
    // Un arancio appena diverso: il preset più vicino.
    name = "#ff9a10"
    compose.waitForIdle()
    assertEquals(ember, primary)
  }
}
