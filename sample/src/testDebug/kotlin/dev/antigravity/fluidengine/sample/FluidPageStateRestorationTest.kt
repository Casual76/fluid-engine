package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lo stato salvabile di una pagina sopravvive alla ricreazione dell'Activity (3.0.2).
 *
 * In 3.0.0/3.0.1 il testo scritto in una pagina e il passo di un onboarding (un `rememberSaveable`) si
 * perdevano a un cambio di tema o a una rotazione, che con il `NavHost` della 2.13 si tenevano: la pila
 * tornava, lo stato dentro le pagine no. Causa: l'host teneva ogni pagina dentro `key(page)`, con `page`
 * la `NavBackStackEntry`; la chiave di un `rememberSaveable` deriva dall'hash di composizione, che
 * include `hashCode()` di quella chiave, e quello della voce dipende da oggetti nuovi a ogni
 * ricreazione. Dopo la ricreazione ogni valore salvato cercava una chiave che non c'era piu'.
 *
 * La ricreazione e' quella vera, `ActivityScenario.recreate()` (un cambio di tema, una rotazione): la
 * Activity si rifa dallo stato salvato, il controller ricostruisce la pila con voci nuove.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPageStateRestorationTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController

  /** L'ultimo valore che ogni pagina ha visto nella sua composizione, per nome. */
  private val seenText = mutableMapOf<String, String>()
  private val seenField = mutableMapOf<String, String>()
  private val setText = mutableMapOf<String, (String) -> Unit>()
  private val setField = mutableMapOf<String, (String) -> Unit>()

  /** Una pagina con un valore `rememberSaveable` e un campo di testo, come una chat o un onboarding. */
  @Composable
  private fun Page(name: String) {
    var text by rememberSaveable { mutableStateOf("") }
    val field: TextFieldState = rememberTextFieldState()
    SideEffect {
      seenText[name] = text
      seenField[name] = field.text.toString()
      setText[name] = { text = it }
      setField[name] = { value -> field.edit { replace(0, length, value) } }
    }
    Box(Modifier.fillMaxSize())
  }

  /** Ricrea l'Activity e rimette lo stesso contenuto, che e' quello che fa `onCreate` dell'app. */
  private fun recreate(content: @Composable () -> Unit) {
    seenText.clear()
    seenField.clear()
    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    compose.waitForIdle()
  }

  private fun write(name: String, text: String, field: String) {
    compose.runOnIdle {
      setText.getValue(name)(text)
      setField.getValue(name)(field)
    }
    compose.waitForIdle()
  }

  private fun assertPage(name: String, text: String, field: String) {
    assertEquals("$name: rememberSaveable", text, seenText[name])
    assertEquals("$name: campo di testo", field, seenField[name])
  }

  // --- FluidNavHost -----------------------------------------------------------------------------

  private val navContent: @Composable () -> Unit = {
    FluidTheme(settings = remember { EngineSettings() }) {
      val controller = rememberFluidNavController()
      nav = controller
      FluidNavHost(navController = controller, startDestination = "a") {
        composable("a") { Page("a") }
        composable("b") { Page("b") }
      }
    }
  }

  private fun route(): String? = compose.runOnIdle { nav.currentBackStackEntry?.destination?.route }

  @Test
  fun `FluidNavHost - lo stato della pagina in cima e di quella coperta sopravvive alla ricreazione`() {
    compose.setContent(navContent)
    compose.waitForIdle()

    write("a", "ciao", "chat A")
    compose.runOnIdle { nav.navigate("b") }
    compose.waitForIdle()
    write("b", "mondo", "chat B")

    recreate(navContent)

    assertEquals("la pila e' tornata", "b", route())
    assertPage("b", "mondo", "chat B")

    compose.runOnUiThread { nav.popBackStack() }
    compose.waitForIdle()

    assertEquals("a", route())
    assertPage("a", "ciao", "chat A")
  }

  @Test
  fun `FluidNavHost - lo stato sopravvive a due ricreazioni di fila`() {
    compose.setContent(navContent)
    compose.waitForIdle()
    write("a", "uno", "due")

    repeat(2) {
      recreate(navContent)
      assertPage("a", "uno", "due")
    }
  }

  // --- FluidPageHost usato direttamente, con una pila propria ------------------------------------

  @Test
  fun `FluidPageHost - lo stato della pagina in cima e di quella coperta sopravvive alla ricreazione`() {
    var stack: List<String> = emptyList()
    var push: (String) -> Unit = {}
    var pop: () -> Unit = {}
    val content: @Composable () -> Unit = {
      FluidTheme(settings = remember { EngineSettings() }) {
        var pages by rememberSaveable { mutableStateOf(arrayListOf("a")) }
        push = { pages = ArrayList(pages + it) }
        pop = { pages = ArrayList(pages.dropLast(1)) }
        stack = pages
        FluidPageHost(stack = pages, onBack = { pop() }) { name -> Page(name) }
      }
    }
    compose.setContent(content)
    compose.waitForIdle()

    write("a", "ciao", "chat A")
    compose.runOnIdle { push("b") }
    compose.waitForIdle()
    write("b", "mondo", "chat B")

    recreate(content)

    assertEquals("la pila e' tornata", listOf("a", "b"), compose.runOnIdle { stack.toList() })
    assertPage("b", "mondo", "chat B")

    compose.runOnIdle { pop() }
    compose.waitForIdle()
    assertPage("a", "ciao", "chat A")
  }
}
