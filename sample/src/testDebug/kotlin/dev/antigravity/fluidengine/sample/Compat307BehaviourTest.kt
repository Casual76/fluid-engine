package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSidebarRow
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
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
 * Le chiamate scritte per la 3.0.7 si comportano come sulla 3.0.7: la lambda finale arriva al parametro di
 * sempre in un albero Compose vero.
 *
 * La candidata 3.1.0 aveva aggiunto un parametro dopo l'ultima lambda di `FluidTabRail` (`header`),
 * `FluidSidebarRow` (`minHeight`) e `tab(…)` (`badge`); per il rail una lambda finale che ignora il suo
 * argomento compilava ancora, ma diventava l'intestazione e il reselect spariva. Che queste chiamate
 * **compilino** lo prova `Compat307SourceTest` di `engine-ui`; qui si prova dove vanno a finire.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class Compat307BehaviourTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private fun show(content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private val railItems = listOf(
    FluidTabItem("home", "Home", Icons.Rounded.Home),
    FluidTabItem("star", "Preferiti", Icons.Rounded.Star),
    FluidTabItem("search", "Cerca", Icons.Rounded.Search),
  )

  // --- Rail -------------------------------------------------------------------------------------------

  @Test
  fun `la lambda finale del rail che ignora il suo argomento e' il reselect, non un'intestazione`() {
    var reselects = 0
    val picked = mutableListOf<String>()
    show {
      val backdrop = rememberGlassBackdrop()
      // La forma silenziosa: nessun parametro, nessun `it`. Sulla candidata compilava e finiva nell'intestazione.
      FluidTabRail(railItems, "home", { picked += it.route }, backdrop, Modifier.height(192.dp).testTag("rail")) {
        reselects++
      }
    }

    // Nessuna intestazione: il rail e' quello di sempre, alto quanto gli si chiede.
    assertEquals(192f, compose.onNodeWithTag("rail").getBoundsInRoot().let { it.bottom - it.top }.value, 0.5f)
    compose.onNodeWithTag("top_level_home").performClick()
    compose.waitForIdle()
    assertEquals(1, reselects)
    assertTrue(picked.isEmpty())

    compose.onNodeWithTag("top_level_star").performClick()
    compose.waitForIdle()
    assertEquals(listOf("star"), picked)
    assertEquals(1, reselects)
  }

  @Test
  fun `la lambda finale del rail con it riceve la zona riscelta`() {
    val reselected = mutableListOf<String>()
    show {
      FluidTabRail(railItems, "star", {}, rememberGlassBackdrop(), Modifier.height(192.dp)) { reselected += it.route }
    }
    compose.onNodeWithTag("top_level_star").performClick()
    compose.waitForIdle()
    assertEquals(listOf("star"), reselected)
  }

  // --- Barra laterale --------------------------------------------------------------------------------

  @Test
  fun `la lambda finale della riga laterale e' il menu, e la riga e' quella di sempre`() {
    val picked = mutableListOf<String>()
    show {
      Column {
        FluidSidebarRow("Posta", false, {}, Modifier.testTag("menu"), icon = Icons.Rounded.Folder) {
          listOf(FluidContextAction("Sposta") { picked += "Sposta" })
        }
        FluidSidebarRow("Posta", false, {}, Modifier.testTag("plain"), icon = Icons.Rounded.Folder)
      }
    }

    // Senza menu la riga non ha la pressione lunga; col menu si'.
    compose.onNodeWithTag("plain").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    compose.onNodeWithTag("menu").assert(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
    // Stessa riga: 44 dp (non la 48 della variante comoda), stessa larghezza.
    val menu = compose.onNodeWithTag("menu").getBoundsInRoot()
    val plain = compose.onNodeWithTag("plain").getBoundsInRoot()
    assertEquals(FluidSidebarDefaults.RowHeight.value, (menu.bottom - menu.top).value, 0.5f)
    assertEquals((plain.bottom - plain.top).value, (menu.bottom - menu.top).value, 0.5f)
    assertEquals((plain.right - plain.left).value, (menu.right - menu.left).value, 0.5f)

    compose.onNodeWithTag("menu").performSemanticsAction(SemanticsActions.OnLongClick)
    compose.waitForIdle()
    compose.onNodeWithText("Sposta").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Sposta"), picked)
  }

  // --- Barra a pillola ---------------------------------------------------------------------------------

  @Test
  fun `la lambda finale di tab e' l'indicazione della zona in linea`() {
    var indications = 0
    var selected by mutableStateOf("home")
    lateinit var scroll: FluidFloatingTabBarScrollConnection
    show {
      scroll = rememberFluidFloatingTabBarScrollConnection()
      Box(Modifier.fillMaxSize().nestedScroll(scroll)) {
        FluidFloatingTabBar(
          selectedTabKey = selected,
          scrollConnection = scroll,
          modifier = Modifier.align(Alignment.BottomCenter),
        ) {
          tab("home", { Text("Home") }, { Icon(Icons.Rounded.Home, null) }, { selected = "home" }) {
            indications++
            LocalIndication.current
          }
          tab(key = "star", title = { Text("Preferiti") }, icon = { Icon(Icons.Rounded.Star, null) }, onClick = { selected = "star" })
        }
      }
    }

    // Aperta, la riga ignora l'indicazione (la pillola e' l'indicazione) e il tocco naviga.
    compose.onNodeWithText("Preferiti").performClick()
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()
    assertEquals("star", selected)

    // In linea la zona scelta usa la sua indicazione: quella passata come lambda finale.
    compose.runOnIdle { selected = "home" }
    compose.waitForIdle()
    compose.runOnIdle { scroll.inline() }
    compose.mainClock.advanceTimeBy(1_000)
    compose.waitForIdle()
    assertTrue("l'indicazione della lambda finale e' stata chiesta dalla zona in linea", indications > 0)
  }
}
