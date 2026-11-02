package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `FluidNavHost(pageColor = )` (3.0): il fondo di ogni pagina non e' piu' `colorScheme.background`
 * scritto dentro l'host.
 *
 * Fluidify ha un fondale d'artwork *dietro* il NavHost e un tema con `background = Transparent`: con
 * il colore fisso ogni pagina lo copriva. `FluidPageHost` il parametro lo aveva gia'; qui si guarda
 * che `FluidNavHost` lo passi davvero, fino al modificatore `background` che la pagina porta:
 *
 *  * di serie la pagina e' dipinta col `background` del tema, come sempre;
 *  * con un colore, la pagina e' di quel colore;
 *  * trasparente, la pagina non copre niente; con `Color.Unspecified` non dipinge proprio, e il primo
 *    fondo che si trova salendo e' quello del fondale che sta dietro l'host.
 *
 * Niente pixel: `captureToImage` non gira sotto Robolectric (la ridisegnata forzata non arriva), e
 * il parametro e' un passaggio di mano, non un disegno. Il colore si legge dal nodo `background`
 * del layout, con l'ispettore di Compose, che e' quello che usano gli strumenti.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidNavHostPageColorTest {

  @get:Rule
  val compose = createComposeRule()

  /** Senza questo gli elementi dei modificatori non dicono come si chiamano ne' cosa portano. */
  @OptIn(InternalComposeUiApi::class)
  @Before
  fun inspectorOn() {
    isDebugInspectorInfoEnabled = true
  }

  @OptIn(InternalComposeUiApi::class)
  @After
  fun inspectorOff() {
    isDebugInspectorInfoEnabled = false
  }

  private val backdrop = Color(0xFF00C853)
  private var themeBackground: Color = Color.Unspecified

  /** Un host con una pagina vuota, davanti a un fondale verde; `pageColor` null e' il default. */
  private fun show(pageColor: Color?) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        themeBackground = MaterialTheme.colorScheme.background
        Box(Modifier.fillMaxSize().background(backdrop).testTag("stage")) {
          Host(pageColor)
        }
      }
    }
    compose.waitForIdle()
  }

  @Composable
  private fun Host(pageColor: Color?) {
    val nav = rememberFluidNavController()
    val page: androidx.navigation.NavGraphBuilder.() -> Unit = {
      composable("a") { Box(Modifier.fillMaxSize().testTag("page")) }
    }
    if (pageColor == null) {
      FluidNavHost(navController = nav, startDestination = "a", builder = page)
    } else {
      FluidNavHost(navController = nav, startDestination = "a", pageColor = pageColor, builder = page)
    }
  }

  /**
   * Il primo fondo che si incontra salendo dal contenuto della pagina: quello della pagina stessa,
   * o — se la pagina non ne porta — quello del fondale. Null se non ce n'e' nessuno.
   */
  private fun nearestBackground(): Color? {
    var layout: LayoutInfo? = compose.onNodeWithTag("page").fetchSemanticsNode().layoutInfo
    while (layout != null) {
      for (info in layout.getModifierInfo()) {
        val inspectable = info.modifier as? InspectableValue ?: continue
        if (inspectable.nameFallback != "background") continue
        return inspectable.inspectableElements.firstOrNull { it.name == "color" }?.value as? Color
      }
      layout = layout.parentInfo
    }
    return null
  }

  /** Per il messaggio di un errore: i modificatori che stanno sopra la pagina, per nome. */
  private fun seen(): String {
    val names = mutableListOf<String>()
    var layout: LayoutInfo? = compose.onNodeWithTag("page").fetchSemanticsNode().layoutInfo
    while (layout != null) {
      layout.getModifierInfo().forEach { info ->
        val inspectable = info.modifier as? InspectableValue
        names += "${info.modifier.javaClass.simpleName}(${inspectable?.nameFallback})"
      }
      layout = layout.parentInfo
    }
    return names.joinToString()
  }

  private fun assertBackground(message: String, expected: Color) {
    val found = nearestBackground()
    assertNotNull("$message: nessun modificatore background salendo dalla pagina; " + seen(), found)
    assertEquals(message, expected, found)
  }

  @Test
  fun `di serie la pagina e' dipinta col background del tema`() {
    show(pageColor = null)
    assertNotEquals("il fondale di prova deve distinguersi dal tema", backdrop, themeBackground)
    assertBackground("pagina di serie", themeBackground)
  }

  @Test
  fun `con un colore la pagina e' di quel colore`() {
    val red = Color(0xFFD50000)
    show(pageColor = red)
    assertBackground("pagina rossa", red)
  }

  @Test
  fun `trasparente la pagina non copre il fondale dietro l'host`() {
    show(pageColor = Color.Transparent)
    assertBackground("pagina trasparente", Color.Transparent)
  }

  @Test
  fun `Unspecified non dipinge niente e il primo fondo e' quello del fondale`() {
    show(pageColor = Color.Unspecified)
    assertBackground("fondale", backdrop)
  }
}
