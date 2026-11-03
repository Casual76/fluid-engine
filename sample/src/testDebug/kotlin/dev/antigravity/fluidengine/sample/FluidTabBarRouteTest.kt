package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La barra flottante si riapre a ogni cambio di pagina, senza che l'app chiami niente (3.0.1).
 *
 * Prima restava ripiegata finche' l'app non chiamava `settle()` ed `expand()`: su una pagina corta non
 * si riapriva piu', e il fermo del vetro restava su per lo scorrimento di una lista smontata a meta'.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidTabBarRouteTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var nav: NavHostController
  private lateinit var bar: FluidFloatingTabBarScrollConnection
  private var selected by mutableStateOf("a")

  private val tabs = listOf(
    FluidTabItem(route = "a", label = "Prima", icon = Icons.Rounded.Home),
    FluidTabItem(route = "b", label = "Seconda", icon = Icons.Rounded.Star),
  )

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        val scroll = rememberFluidFloatingTabBarScrollConnection()
        bar = scroll
        Box(Modifier.fillMaxSize().nestedScroll(scroll)) {
          FluidNavHost(navController = controller, startDestination = "a") {
            composable("a") { Text("Pagina A") }
            composable("b") { Text("Pagina B") }
          }
          FluidFloatingTabBar(
            items = tabs,
            selectedRoute = selected,
            onSelect = { selected = it.route },
            scrollConnection = scroll,
            modifier = Modifier.align(Alignment.BottomCenter),
          )
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `un cambio di rotta dell'host riapre la barra`() {
    show()
    compose.runOnIdle { bar.inline() }
    compose.waitForIdle()
    assertTrue(bar.isInline)

    compose.runOnIdle { nav.navigate("b") }
    compose.waitForIdle()
    assertFalse(bar.isInline)

    compose.runOnIdle { bar.inline() }
    compose.runOnIdle { nav.popBackStack() }
    compose.waitForIdle()
    assertFalse("anche tornando indietro", bar.isInline)
  }

  @Test
  fun `un cambio di scheda riapre la barra`() {
    show()
    compose.runOnIdle { bar.inline() }
    compose.waitForIdle()
    compose.runOnIdle { selected = "b" }
    compose.waitForIdle()
    assertFalse(bar.isInline)
  }

  @Test
  fun `la barra non si riapre da sola senza un cambio`() {
    show()
    compose.runOnIdle { bar.inline() }
    compose.waitForIdle()
    assertTrue(bar.isInline)
  }
}
