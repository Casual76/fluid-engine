package dev.antigravity.fluidengine.sample.novita

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.fluid.FluidPermissionState
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeSheet
import dev.antigravity.fluidengine.ui.fluid.FluidWelcomeState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.rememberFluidWelcomeState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R2 (F8.11) · la prima volta nei confini che le prove di F8.11 non attraversavano: la rotazione mentre la spiegazione o il
 * dialogo di Android sono sopra, il permesso già negato per sempre prima che l'engine lo chieda, il doppio tocco rapido, e
 * la memoria della prima volta che non si legge o non si scrive.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-xhdpi")
class F8p11R2FirstUseTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Il dialogo di Android finto, come in `F8p11PermissionUiTest`: ricorda cosa gli si chiede e risponde a comando. */
  private inner class FakeRegistry : ActivityResultRegistry() {
    val asked = mutableListOf<List<String>>()
    private var code = -1

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      @Suppress("UNCHECKED_CAST")
      asked += (input as Array<String>).toList()
      code = requestCode
    }

    fun answer(permissions: List<String>, granted: Set<String>, rationale: Boolean) {
      val app = shadowOf(compose.activity.application)
      val pm = shadowOf(compose.activity.packageManager)
      permissions.forEach { permission ->
        if (permission in granted) app.grantPermissions(permission) else app.denyPermissions(permission)
        pm.setShouldShowRequestPermissionRationale(permission, rationale && permission !in granted)
      }
      compose.runOnIdle { dispatchResult(code, permissions.associateWith { it in granted }) }
      settle()
    }

    /** La richiesta interrotta: Android risponde con le liste vuote (l'Activity ricreata, un'altra richiesta in corso). */
    fun cancel() {
      compose.runOnIdle { dispatchResult(code, emptyMap<String, Boolean>()) }
      settle()
    }
  }

  private val registry = FakeRegistry()
  private val owner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = registry
  }
  private val location = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
  private val reason = "Fluid Transit usa la posizione approssimativa per mostrarti le fermate qui vicino."

  private fun settle() {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
  }

  @Composable
  private fun Hosted(memory: FluidFirstUseMemory, content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidGlassModalHostState provides host,
        LocalFluidFirstUseMemory provides memory,
        LocalActivityResultRegistryOwner provides owner,
      ) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  // --- #40 · la rotazione -------------------------------------------------------------------------------------------

  /** La spiegazione aperta e l'Activity ricreata: la spiegazione torna (prima spariva, già segnata «spiegata»). */
  @Test fun laSpiegazioneResisteAllaRotazione() {
    val memory = FluidFirstUseMemory.inMemory()
    lateinit var state: FluidPermissionState
    val restore = StateRestorationTester(compose)
    restore.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText(reason).assertExists()
    restore.emulateSavedInstanceStateRestore()
    settle()
    compose.onNodeWithText(reason).assertExists()
    compose.onNodeWithText("Continua").performClick()
    settle()
    assertEquals(listOf(location), registry.asked)
  }

  /**
   * Il dialogo di Android aperto e l'Activity ricreata: la risposta arriva allo stato nuovo. Lo stato si aggiorna e
   * `onResult` riceve il sì; l'azione del tocco (una funzione dell'albero di prima) non si può rifare, e l'app che deve
   * partire da sola lo fa da `onResult`.
   */
  @Test fun ilSiDatoDuranteLaRotazioneArrivaAdOnResult() {
    val memory = FluidFirstUseMemory.inMemory()
    lateinit var state: FluidPermissionState
    val results = mutableListOf<FluidPermissionStatus>()
    val restore = StateRestorationTester(compose)
    restore.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, explain = false, onResult = { results += it })
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    assertEquals(1, registry.asked.size)
    restore.emulateSavedInstanceStateRestore()
    settle()
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(FluidPermissionStatus.Granted, state.status)
    assertEquals(listOf(FluidPermissionStatus.Granted), results)
  }

  // --- #40, scelta B di Alessio: `onGranted` che sopravvive alla ricreazione ------------------------------------------

  /**
   * Il dialogo di Android aperto e l'Activity ricreata davvero (una rotazione): al sì parte `onGranted` dell'albero nuovo,
   * una volta. Il tocco dopo, a permesso concesso, lo fa partire subito.
   */
  @Test fun onGrantedParteDopoUnaRicreazioneVeraColDialogoAperto() {
    val memory = FluidFirstUseMemory.inMemory()
    var ran = 0
    lateinit var state: FluidPermissionState
    // La stessa funzione di contenuto le due volte: le chiavi di `rememberSaveable` vengono dal posto nel sorgente.
    val content: @Composable () -> Unit = {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, explain = false, onGranted = { ran++ })
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    compose.setContent(content)
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    assertEquals(1, registry.asked.size)
    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    settle()
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(FluidPermissionStatus.Granted, state.status)
    assertEquals("il si' fa partire l'azione dell'albero nuovo", 1, ran)
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    assertEquals(2, ran)
  }

  /** La spiegazione aperta e l'Activity ricreata davvero: la spiegazione torna, «Continua», sì, e parte `onGranted`. */
  @Test fun onGrantedParteDopoUnaRicreazioneVeraConLaSpiegazioneAperta() {
    val memory = FluidFirstUseMemory.inMemory()
    var ran = 0
    lateinit var state: FluidPermissionState
    val content: @Composable () -> Unit = {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, onGranted = { ran++ })
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    compose.setContent(content)
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText(reason).assertExists()
    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    settle()
    compose.onNodeWithText(reason).assertExists()
    compose.onNodeWithText("Continua").performClick()
    settle()
    assertEquals(1, registry.asked.size)
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(1, ran)
  }

  /**
   * Nello stesso albero l'azione data a `request { }` vince su `onGranted`: parte una sola azione. Un «Non ora» non fa
   * partire niente, neanche dopo.
   */
  @Test fun nelloStessoAlberoParteLAzioneDiRequestEBasta() {
    val memory = FluidFirstUseMemory.inMemory()
    var fromRequest = 0
    var fromParameter = 0
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, onGranted = { fromParameter++ })
        FluidButton("Fermate qui vicino", onClick = { state.request { fromRequest++ } }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText("Non ora").performClick()
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(1, fromRequest)
    assertEquals(0, fromParameter)
  }

  // --- #41 · negato per sempre prima dell'engine ---------------------------------------------------------------------

  /**
   * Il permesso è già negato per sempre (un codice dell'app di prima, o tolto dal manifest): Android risponde «no» senza
   * mostrare niente, come quando si chiude il dialogo senza scegliere. La prima volta l'engine non lo sa distinguere; la
   * seconda risposta uguale è un no vero: negato per sempre e «Apri Impostazioni», subito.
   */
  @Test fun negatoPerSempreGiaPrimaPortaAlleImpostazioni() {
    val memory = FluidFirstUseMemory.inMemory()
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, explain = false)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    registry.answer(location, granted = emptySet(), rationale = false)
    assertEquals(FluidPermissionStatus.NeverAsked, state.status)
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    assertEquals(2, registry.asked.size)
    registry.answer(location, granted = emptySet(), rationale = false)
    assertEquals(FluidPermissionStatus.DeniedForever, state.status)
    compose.onNodeWithText("Accesso alla posizione non consentito").assertExists()
    assertEquals("1", runBlocking { memory.read(FluidFirstUseKeys.permissionAsked("location-coarse")) })
  }

  /** Una richiesta interrotta (le liste vuote di Android) non conta come una risposta: resta «mai chiesto». */
  @Test fun unaRichiestaInterrottaNonContaComeUnNo() {
    val memory = FluidFirstUseMemory.inMemory()
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason, explain = false)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    repeat(3) {
      compose.onNodeWithText("Fermate qui vicino").performClick()
      settle()
      registry.cancel()
    }
    assertEquals(3, registry.asked.size)
    assertEquals(FluidPermissionStatus.NeverAsked, state.status)
    compose.onNodeWithText("Accesso alla posizione non consentito").assertDoesNotExist()
  }

  // --- #42 · il doppio tocco -----------------------------------------------------------------------------------------

  /** Due tocchi rapidi: una spiegazione sola, nessun dialogo sopra, e l'azione che parte è quella del primo tocco. */
  @Test fun ilDoppioToccoNonApreIlDialogoSopraLaSpiegazione() {
    val memory = FluidFirstUseMemory.inMemory()
    lateinit var state: FluidPermissionState
    var first = 0
    var second = 0
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    compose.runOnIdle {
      state.request { first++ }
      state.request { second++ }
    }
    settle()
    compose.onNodeWithText(reason).assertExists()
    assertTrue("nessun dialogo di Android sopra la spiegazione", registry.asked.isEmpty())
    compose.onNodeWithText("Continua").performClick()
    settle()
    assertEquals(1, registry.asked.size)
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(1, first)
    assertEquals(0, second)
  }

  // --- #48 · la memoria che non risponde -----------------------------------------------------------------------------

  /** Una memoria rotta (il file del DataStore corrotto, un errore di IO): legge e scrive lanciando. */
  private class BrokenMemory(private val readFails: Boolean = true) : FluidFirstUseMemory {
    override suspend fun read(key: String): String? {
      if (readFails) throw IOException("file corrotto")
      return null
    }

    override suspend fun write(key: String, value: String?) {
      throw IOException("disco pieno")
    }
  }

  @Test fun ilBenvenutoConLaMemoriaRottaDecideENonCade() {
    lateinit var state: FluidWelcomeState
    compose.setContent {
      Hosted(BrokenMemory()) {
        state = rememberFluidWelcomeState("1.0.0", hasNews = true, freshInstall = false)
        FluidWelcomeSheet(state.isShowing, state::dismiss, "Novità di Note", emptyList())
      }
    }
    settle()
    assertTrue("la decisione non resta appesa", state.isDecided)
    assertFalse("senza memoria niente foglio a ogni avvio", state.isShowing)
  }

  @Test fun ilBenvenutoChePuoLeggereMaNonScrivereSiMostraLostesso() {
    lateinit var state: FluidWelcomeState
    compose.setContent {
      Hosted(BrokenMemory(readFails = false)) {
        state = rememberFluidWelcomeState("1.0.0", hasNews = false, freshInstall = true)
        FluidWelcomeSheet(state.isShowing, state::dismiss, "Benvenuto in Note", emptyList())
      }
    }
    settle()
    assertTrue(state.isDecided)
    assertTrue("il primo avvio ha il suo benvenuto anche se non si può ricordare", state.isShowing)
  }

  @Test fun ilPermessoConLaMemoriaRottaChiedeLostesso() {
    lateinit var state: FluidPermissionState
    compose.setContent {
      Hosted(BrokenMemory()) {
        state = rememberFluidPermission(FluidPermission.ApproximateLocation, reason = reason)
        FluidButton("Fermate qui vicino", onClick = { state.request() }, modifier = Modifier.fluidPermissionSource(state))
      }
    }
    settle()
    assertTrue(state.isLoaded)
    compose.onNodeWithText("Fermate qui vicino").performClick()
    settle()
    compose.onNodeWithText(reason).assertExists()
    compose.onNodeWithText("Continua").performClick()
    settle()
    registry.answer(location, granted = location.toSet(), rationale = false)
    assertEquals(FluidPermissionStatus.Granted, state.status)
  }

  // --- Il minore del benvenuto: visto prima di comparire ------------------------------------------------------------

  /** Una memoria che scrive solo quando la prova lo dice: la scrittura lenta di un DataStore al primo avvio. */
  private class SlowMemory : FluidFirstUseMemory {
    val gate = CompletableDeferred<Unit>()
    val values = mutableMapOf<String, String?>()
    override suspend fun read(key: String): String? = values[key]
    override suspend fun write(key: String, value: String?) {
      gate.await()
      values[key] = value
    }
  }

  /** Il foglio compare solo con la versione già scritta: chi chiude l'app nei primi istanti non lo rivede. */
  @Test fun ilBenvenutoComparePoiCheESegnatoVisto() {
    val memory = SlowMemory()
    lateinit var state: FluidWelcomeState
    compose.setContent {
      Hosted(memory) {
        state = rememberFluidWelcomeState("1.0.0", hasNews = false, freshInstall = true)
        FluidWelcomeSheet(state.isShowing, state::dismiss, "Benvenuto in Note", emptyList())
      }
    }
    settle()
    assertTrue(state.isDecided)
    assertFalse("non ancora scritto: il foglio aspetta", state.isShowing)
    assertNull(memory.values[FluidFirstUseKeys.welcomeSeen()])
    memory.gate.complete(Unit)
    settle()
    assertEquals("1.0.0", memory.values[FluidFirstUseKeys.welcomeSeen()])
    assertTrue(state.isShowing)
  }
}
