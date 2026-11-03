package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cosa rifrange un controllo di vetro di serie (3.0.1).
 *
 * `FluidSearchField` e `FluidGlassButton` chiedevano sempre la chrome (`currentGlassBackdrop()`) mentre i
 * chip, il segmentato e lo slider chiedono prima il canvas della pagina: su una pagina ambientale i primi
 * due rifrangevano il corpo della pagina, se stessi compresi, se l'app non passava il canvas a mano. Ora
 * tutti chiedono prima il canvas; e perche' un controllo in una barra resti sul vetro della barra, ogni
 * barra toglie il canvas a quello che ci sta dentro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidControlBackdropTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val canvasSeen = mutableMapOf<String, GlassBackdropState?>()
  private val chromeSeen = mutableMapOf<String, GlassBackdropState?>()
  private val defaultSeen = mutableMapOf<String, GlassBackdropState>()

  /** Quello che un controllo vede dove sta, e cosa sceglierebbe di serie. */
  @Composable
  private fun Probe(name: String) {
    canvasSeen[name] = LocalFluidCanvasBackdrop.current
    chromeSeen[name] = LocalGlassBackdrop.current
    defaultSeen[name] = currentCanvasBackdrop() ?: currentGlassBackdrop()
    Text(name)
  }

  @Test
  fun `nella pagina il controllo rifrange il canvas, nella barra il vetro della barra`() {
    lateinit var canvas: GlassBackdropState
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        canvas = rememberGlassBackdrop()
        CompositionLocalProvider(LocalFluidCanvasBackdrop provides canvas) {
          FluidScreen(title = "Pagina", actions = { Probe("bar") }) {
            item { Probe("body") }
          }
        }
      }
    }
    compose.waitForIdle()

    assertSame("nel corpo: il canvas", canvas, defaultSeen["body"])
    assertNull("nella barra niente canvas", canvasSeen["bar"])
    assertNotNull(chromeSeen["bar"])
    assertSame("nella barra: il vetro della barra", chromeSeen["bar"], defaultSeen["bar"])
  }

  @Test
  fun `nella barra ancorata di un host le azioni stanno sul vetro della barra`() {
    lateinit var canvas: GlassBackdropState
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        canvas = rememberGlassBackdrop()
        val nav = rememberFluidNavController()
        CompositionLocalProvider(LocalFluidCanvasBackdrop provides canvas) {
          FluidNavHost(navController = nav, startDestination = "a") {
            composable("a") {
              FluidScreen(title = "Pagina", actions = { Probe("screen bar") }) {
                item { Probe("body") }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    assertSame(canvas, defaultSeen["body"])
    assertNull(canvasSeen["screen bar"])
    assertNotNull(chromeSeen["screen bar"])
  }

  @Test
  fun `una FluidPageChrome da sola tiene le azioni sul suo vetro`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val canvas = rememberGlassBackdrop()
        CompositionLocalProvider(LocalFluidCanvasBackdrop provides canvas) {
          FluidPageChrome(title = "Barra", backdrop = rememberGlassBackdrop(), actions = { Probe("chrome") })
        }
      }
    }
    compose.waitForIdle()
    assertNull(canvasSeen["chrome"])
    assertNotNull(chromeSeen["chrome"])
  }
}
