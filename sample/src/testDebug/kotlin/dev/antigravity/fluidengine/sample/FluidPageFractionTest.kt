package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `FluidPageHostState.fractionToward` (3.0.1): quanto si e' andati verso una pagina che nasconde la tab bar.
 *
 * A riposo 0 o 1, in movimento al passo della pagina e mai fuori da 0..1, e fermo quando le due pagine
 * sono dello stesso lato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageFractionTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState

  /** La barra non c'e' sulle pagine di dettaglio. */
  private val hidden: (Any?) -> Boolean = { (it as? NavBackStackEntry)?.destination?.route?.startsWith("dettaglio") == true }

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        val state = rememberFluidPageHostState()
        pages = state
        FluidNavHost(navController = controller, startDestination = "lista", state = state) {
          composable("lista") { Text("Lista") }
          composable("dettaglio") { Text("Dettaglio") }
          composable("dettaglio2") { Text("Altro dettaglio") }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun fraction(): Float = compose.runOnIdle { pages.fractionToward(hidden) }

  @Test
  fun `a riposo dice da che parte sta la pagina, in movimento cresce col passo`() {
    show()
    assertEquals(0f, fraction(), 0f)

    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.navigate("dettaglio") }
    val seen = mutableListOf<Float>()
    repeat(40) {
      compose.mainClock.advanceTimeByFrame()
      seen += pages.fractionToward(hidden)
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    assertTrue("sempre fra 0 e 1: $seen", seen.all { it in 0f..1f })
    assertTrue("passa per il mezzo: $seen", seen.any { it > 0.05f && it < 0.95f })
    assertTrue("non torna indietro durante un push: $seen", seen.zipWithNext().all { (a, b) -> b >= a - 1e-3f })
    assertEquals(1f, fraction(), 0f)

    compose.runOnIdle { nav.popBackStack() }
    compose.waitForIdle()
    assertEquals(0f, fraction(), 0f)
  }

  @Test
  fun `fra due pagine dello stesso lato resta ferma`() {
    show()
    compose.runOnIdle { nav.navigate("dettaglio") }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.navigate("dettaglio2") }
    val seen = mutableListOf<Float>()
    repeat(20) {
      compose.mainClock.advanceTimeByFrame()
      seen += pages.fractionToward(hidden)
    }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertTrue("$seen", seen.all { it == 1f })
  }
}
