package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidChromeController
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Una `FluidScreen` dentro una pagina (la galleria «Menù liquidi» ha la sezione «Il gruppo che diventa menu», uno schermo
 * «Foto» alto 320 dp dentro la lista) non diventa il fondale della chrome. Fino a S4b si registrava come pagina davanti
 * dopo la pagina vera, e vinceva l'elezione (`frontMostBackdropKey`): modali, menu e tab bar campionavano solo lei, e la
 * sfocatura del vetro (`Clamp`) ne stirava l'ultima riga su tutto lo schermo. Sul Tab S9 erano le bande verticali marroni
 * dietro il contestuale (S4b, NO 1) e la pagina bianca dietro «Altro»; al banco non c'era una pagina con uno schermo dentro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidNestedScreenChromeTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun `uno schermo dentro la pagina non prende il fondale della chrome`() {
    val nested = mutableStateOf(false)
    lateinit var controller: FluidChromeController
    compose.setContent {
      controller = rememberFluidChromeController()
      val bus = remember { FluidScrollToTopBus() }
      FluidTheme(settings = remember { EngineSettings() }) {
        ProvideFluidChrome(controller = controller, bottomInset = 0.dp, scrollToTop = bus) {
          FluidScreen("Pagina") {
            item { Box(Modifier.fillMaxWidth().height(80.dp)) }
            if (nested.value) {
              item {
                FluidScreen("Foto", modifier = Modifier.height(320.dp)) {
                  item { Box(Modifier.fillMaxWidth().height(200.dp)) }
                }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    val page = controller.activeBackdrop.value
    assertNotNull("la pagina vera e' il fondale della chrome", page)
    nested.value = true
    compose.waitForIdle()
    assertSame("con lo schermo «Foto» dentro, la chrome campiona ancora la pagina", page, controller.activeBackdrop.value)
    nested.value = false
    compose.waitForIdle()
    assertSame("e quando lo schermo dentro se ne va, resta la pagina", page, controller.activeBackdrop.value)
  }

  @Test fun `uno schermo con una chrome sua dentro la pagina la serve ancora`() {
    lateinit var outer: FluidChromeController
    lateinit var inner: FluidChromeController
    compose.setContent {
      outer = rememberFluidChromeController()
      inner = rememberFluidChromeController()
      val bus = remember { FluidScrollToTopBus() }
      FluidTheme(settings = remember { EngineSettings() }) {
        ProvideFluidChrome(controller = outer, bottomInset = 0.dp, scrollToTop = bus) {
          FluidScreen("Pagina") {
            item {
              // Come lo zoom della galleria: un host di pagine con la sua chrome, dentro una pagina.
              ProvideFluidChrome(controller = inner, bottomInset = 0.dp, scrollToTop = bus) {
                FluidScreen("Dentro", modifier = Modifier.height(320.dp)) {
                  item { Box(Modifier.fillMaxWidth().height(200.dp)) }
                }
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    assertNotNull("la chrome esterna ha la pagina", outer.activeBackdrop.value)
    assertNotNull("la chrome interna ha lo schermo che le appartiene", inner.activeBackdrop.value)
  }
}
