package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.icons.Archive
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il push di `FluidNavHost` da una pagina che dichiara una barra in basso (F4B.1) deve arrivare a riposo, come senza.
 *
 * Trovato in F5B.3 (la diagnosi è in `sessioni/f5b-3/diagnosi/`): con la barra in basso dichiarata sulla pagina di
 * partenza la corsa del push avanza due fotogrammi (1000 → 964 → 881 px con l'Expand, 800 → 771 → 705 con Slide) e poi
 * resta ferma, la fase resta `Pushing`; con l'orologio automatico Compose non torna mai a riposo e dopo ~180 s cade su
 * «Key … was used multiple times» in `SaveableStateHolder` (la stessa sequenza che F4B.2 ha visto nei suoi test di
 * navigazione). Senza barra in basso lo stesso push arriva a riposo. Nessuno zoom: `Slide` semplice.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidNavHostBottomBarPushTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState

  private fun show(bottomBar: Boolean) {
    val toolbar: (FluidToolbarScope.() -> Unit)? = if (bottomBar) {
      { group("azioni") { button("archivia", FluidIcons.Archive, "Archivia", onClick = {}) } }
    } else {
      null
    }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        val state = rememberFluidPageHostState()
        pages = state
        FluidNavHost(navController = controller, startDestination = "a", modifier = Modifier.fillMaxSize(), state = state) {
          composable("a") {
            FluidScreen(title = "Partenza", toolbar = toolbar) {
              item(key = "riga") { Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF6D8B74))) }
            }
          }
          composable("b", transition = FluidPageTransition.Slide) {
            FluidScreen(title = "Arrivo", onBack = { controller.popBackStack() }) {
              item(key = "riga") { Text("Arrivo") }
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun pushAndWait() {
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.navigate("b") }
    repeat(120) { compose.mainClock.advanceTimeByFrame() }
    val moving = pages.isMoving
    compose.mainClock.autoAdvance = true
    assertFalse("dopo 2 s il push e' ancora in corsa", moving)
  }

  @Test fun `il push senza barra in basso arriva a riposo`() {
    show(bottomBar = false)
    pushAndWait()
  }

  @Test fun `il push da una pagina con la barra in basso arriva a riposo`() {
    show(bottomBar = true)
    pushAndWait()
  }
}
