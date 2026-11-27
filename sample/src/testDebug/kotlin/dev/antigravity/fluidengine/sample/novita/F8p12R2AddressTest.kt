package dev.antigravity.fluidengine.sample.novita

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidAddressPage
import dev.antigravity.fluidengine.foundation.FluidAddressParameters
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.foundation.FluidAddressTable
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.navigation.FluidAddressNavigation
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * R2 (F8.12) · gli indirizzi delle pagine nei casi che le prove di F8.12 non attraversavano: il ripristino della coda dei
 * modali **senza** un link in attesa (il bloccante della review), una rotta dell'app che il grafo non ha, e l'indirizzo
 * iniziale che arriva una composizione dopo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p12R2AddressTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController

  /** Le rotte del grafo: la pagina «nascosta» dell'app non c'è (una pagina tolta in una versione nuova, un errore). */
  private val graphRoutes = listOf("home", "quaderno", "nota", "mancante", "accesso")

  private val addresses = FluidAddressNavigation(FluidAddressTable("app", listOf(
    FluidAddressPage<String, String>(String::class, "pagina",
      { it.segments.singleOrNull()?.takeIf { page -> page in graphRoutes || page == "nascosta" } },
      { FluidAddressParameters(listOf(it)) },
      parent = { when (it) { "home" -> null; "nota", "nascosta" -> "quaderno"; else -> "home" } }, public = true),
  )), { it }, "mancante", "accesso")

  private fun stack() = nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }

  private fun settle() {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
  }

  @Composable
  private fun Tree(initial: FluidAddressRequest?, withModalHost: Boolean) {
    FluidTheme(settings = remember { EngineSettings() }) {
      nav = rememberFluidNavController()
      val content: @Composable () -> Unit = {
        FluidNavHost(nav, "home", addresses, initial, Modifier.fillMaxSize()) {
          for (route in graphRoutes) composable(route) { Text(route) }
        }
      }
      if (withModalHost) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(modals, null)
          }
        }
      } else {
        content()
      }
    }
  }

  /**
   * Il bloccante di R2: con l'host modale la coda degli indirizzi si salva sempre, e senza un link in attesa salva una lista
   * vuota. Il ripristino non deve leggerla (Compose 1.11 non salva le liste vuote di `listSaver`: questa prova lo tiene).
   */
  @Test fun laCodaVuotaSiRipristinaSenzaCadere() {
    val restore = StateRestorationTester(compose)
    restore.setContent { Tree(initial = null, withModalHost = true) }
    settle()
    compose.runOnIdle { nav.navigate("quaderno") }
    settle()
    val before = nav
    restore.emulateSavedInstanceStateRestore()
    settle()
    compose.runOnIdle {
      assertNotSame(before, nav)
      assertEquals(listOf("home", "quaderno"), stack())
      // E la coda nuova funziona: un link dopo il ripristino apre la sua pila.
      assertTrue(addresses.open(nav, FluidAddressRequest("app://pagina/nota")))
    }
    settle()
    compose.runOnIdle { assertEquals(listOf("home", "quaderno", "nota"), stack()) }
  }

  /** La stessa cosa con una ricreazione vera dell'Activity (il `Bundle` di Android, non il registro della prova). */
  @Test fun laCodaVuotaSopravviveAllaRicreazioneVera() {
    // La stessa funzione di contenuto le due volte: le chiavi di `rememberSaveable` vengono dal posto nel sorgente.
    val content: @Composable () -> Unit = { Tree(initial = null, withModalHost = true) }
    compose.setContent(content)
    settle()
    compose.runOnIdle { nav.navigate("quaderno") }
    settle()
    compose.activityRule.scenario.recreate()
    // Quello che fa `onCreate` dell'app: lo stesso contenuto, che ritrova il suo stato salvato.
    compose.setContent(content)
    settle()
    compose.runOnIdle { assertEquals(listOf("home", "quaderno"), stack()) }
  }

  /**
   * #46: una pagina della tabella la cui rotta non è nel grafo. La pila non resta a metà e l'app non cade: i genitori validi
   * restano e in cima c'è la pagina «non c'è» dell'app.
   */
  @Test fun unaRottaFuoriDalGrafoFinisceSullaPaginaNonCe() {
    compose.setContent { Tree(initial = null, withModalHost = false) }
    settle()
    compose.runOnIdle { nav.navigate("nota") }
    settle()
    compose.runOnIdle { assertTrue(addresses.open(nav, FluidAddressRequest("app://pagina/nascosta"))) }
    settle()
    compose.runOnIdle { assertEquals(listOf("home", "quaderno", "mancante"), stack()) }
  }

  /** Il minore di FluidNavHost: l'indirizzo iniziale nullo alla prima composizione e pronto alla seconda si apre. */
  @Test fun lIndirizzoInizialeCheArrivaDopoSiApre() {
    var initial by mutableStateOf<FluidAddressRequest?>(null)
    compose.setContent { Tree(initial = initial, withModalHost = false) }
    settle()
    compose.runOnIdle { assertEquals(listOf("home"), stack()) }
    initial = FluidAddressRequest("app://pagina/nota")
    settle()
    compose.runOnIdle { assertEquals(listOf("home", "quaderno", "nota"), stack()) }
    // Consumato una volta: lo stesso valore riletto non riapre niente.
    compose.runOnIdle { nav.popBackStack() }
    settle()
    compose.runOnIdle { assertEquals(listOf("home", "quaderno"), stack()) }
  }
}
