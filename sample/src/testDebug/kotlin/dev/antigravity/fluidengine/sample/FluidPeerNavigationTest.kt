package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.navigateToPeer
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPeerMove
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il passo fra schede con una pila sola (3.0.1): `navigateToPeer`.
 *
 * Le due ricette che le app avevano scritto si rompevano cosi': `popUpTo(startDestination)` accumulava le
 * schede appena la partenza non era piu' nella pila, e `popUpTo(graph.id) { saveState }` non tornava mai
 * allo stato salvato. Qui la pila e' sempre una scheda sola, piu' le sue pagine figlie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPeerNavigationTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private val tabs = listOf("a", "b", "c")

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        FluidNavHost(
          navController = controller,
          startDestination = "a",
          peer = FluidPeerMove { from, to ->
            val i = tabs.indexOf(from.destination.route)
            val j = tabs.indexOf(to.destination.route)
            if (i < 0 || j < 0) 0 else j - i
          },
        ) {
          for (tab in tabs) composable(tab) { Text("Scheda $tab") }
          composable("dettaglio") { Text("Dettaglio") }
        }
      }
    }
    compose.waitForIdle()
  }

  /** Le pagine nella pila, senza il grafo. */
  private fun pages(): List<String?> = compose.runOnIdle {
    nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }
  }

  private fun go(block: NavHostController.() -> Unit) {
    compose.runOnIdle { nav.block() }
    compose.waitForIdle()
  }

  @Test
  fun `le schede non si accumulano, nemmeno quando la partenza non c'e' piu'`() {
    show()
    go { navigateToPeer("b") }
    assertEquals(listOf("b"), pages())
    go { navigateToPeer("c") }
    assertEquals(listOf("c"), pages())
    // La partenza ("a") non e' piu' nella pila: popUpTo(startDestination) qui non toglieva niente.
    go { navigateToPeer("a") }
    go { navigateToPeer("b") }
    assertEquals(listOf("b"), pages())
  }

  @Test
  fun `una figlia si chiude passando a un'altra scheda`() {
    show()
    go { navigate("dettaglio") }
    assertEquals(listOf("a", "dettaglio"), pages())
    go { navigateToPeer("c") }
    assertEquals(listOf("c"), pages())
  }

  @Test
  fun `la scheda sotto una figlia si ritrova con la sua voce, e la scheda in cima non cambia`() {
    show()
    val root = compose.runOnIdle { nav.currentBackStackEntry }
    go { navigate("dettaglio") }
    go { navigateToPeer("a") }
    assertEquals(listOf("a"), pages())
    assertSame("la stessa voce, col suo stato", root, compose.runOnIdle { nav.currentBackStackEntry })
    go { navigateToPeer("a") }
    assertSame(root, compose.runOnIdle { nav.currentBackStackEntry })
  }
}
