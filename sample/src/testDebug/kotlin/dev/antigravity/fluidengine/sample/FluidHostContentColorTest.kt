package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.text.TextLayoutResult
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidNotification
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il colore del testo dentro ogni host, sul tema scuro (3.0.1).
 *
 * Gli host stanno alla radice, quasi sempre fuori da una `Surface`, e li' `LocalContentColor` vale nero:
 * un `Text` senza colore in un pop-up (il selettore dei formati di universal_converter) o il titolo
 * agganciato di una `FluidPageChrome` erano neri sul nero. Qui nessuna `Surface` avvolge niente, apposta:
 * ogni host deve dire da se' il colore di quello che ci sta sopra.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidHostContentColorTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  /** Quello che ogni sonda ha letto, e i colori del tema. */
  private val seen = mutableMapOf<String, Color>()
  private var onSurface = Color.Unspecified
  private var onBackground = Color.Unspecified

  @Composable
  private fun Probe(name: String) {
    seen[name] = LocalContentColor.current
    Text(name)
  }

  @Composable
  private fun Dark(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK) }) {
      onSurface = MaterialTheme.colorScheme.onSurface
      onBackground = MaterialTheme.colorScheme.onBackground
      content()
    }
  }

  /** Il colore con cui il testo [text] e' stato davvero composto. */
  private fun textColor(text: String): Color {
    val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.first().layoutInput.style.color
  }

  private fun assertLight(what: String, color: Color) {
    // Il tema scuro: il colore giusto e' chiaro, e il difetto era il nero di `LocalContentColor`.
    assertNotEquals("$what: nero", Color.Black, color)
    assertTrue("$what: troppo scuro ($color)", color.red + color.green + color.blue > 1.5f)
  }

  @Test
  fun `il testo nei pannelli dei modali ha il colore della superficie`() {
    compose.setContent {
      Dark {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidGlassModalPortal(visible = true, onDismissRequest = {}) { Probe("popover") }
            FluidGlassModalPortal(
              visible = true,
              onDismissRequest = {},
              presentation = FluidGlassModalPresentation.Sheet,
            ) { Probe("sheet") }
            FluidGlassModalPortal(
              visible = true,
              onDismissRequest = {},
              presentation = FluidGlassModalPresentation.FullScreen,
              paneTitle = "Pagina intera",
            ) { Probe("fullscreen") }
            FluidAlert(
              onDismissRequest = {},
              title = "Avviso",
              actions = listOf(FluidAlertAction("Ok", onClick = {})),
            ) { Probe("alert") }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()

    for (name in listOf("popover", "sheet", "fullscreen", "alert")) {
      assertEquals(name, onSurface, seen[name])
    }
    assertLight("popover", seen.getValue("popover"))
  }

  @Test
  fun `il contenuto di un avviso di piattaforma ha il colore della card`() {
    compose.setContent {
      Dark {
        FluidAlert(
          onDismissRequest = {},
          title = "Avviso",
          actions = listOf(FluidAlertAction("Ok", onClick = {})),
          inHost = false,
        ) { Probe("dialog") }
      }
    }
    compose.waitForIdle()
    assertEquals(onSurface, seen["dialog"])
  }

  @Test
  fun `il titolo di una notifica ha il colore della superficie`() {
    compose.setContent {
      Dark {
        val state = rememberFluidNotificationHostState()
        LaunchedEffect(state) {
          state.show(FluidNotification(id = "n", title = "Notifica scura", message = "Il testo"))
        }
        FluidNotificationHost(state = state)
      }
    }
    compose.waitUntil(5_000) { compose.onAllNodesWithText("Notifica scura").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(onSurface, textColor("Notifica scura"))
  }

  @Test
  fun `una pagina senza FluidScreen ha il titolo agganciato e il testo del fondo`() {
    compose.setContent {
      Dark {
        val nav = rememberFluidNavController()
        FluidNavHost(navController = nav, startDestination = "a") {
          composable("a") {
            Box(Modifier.fillMaxSize()) {
              Probe("page")
              FluidPageChrome(title = "Titolo agganciato")
            }
          }
        }
      }
    }
    compose.waitForIdle()

    assertEquals(onBackground, seen["page"])
    assertEquals(onBackground, textColor("Titolo agganciato"))
    assertLight("titolo", textColor("Titolo agganciato"))
  }

  @Test
  fun `fuori da un host la FluidPageChrome ha il titolo del fondo`() {
    compose.setContent {
      Dark {
        FluidPageChrome(title = "Barra da sola")
      }
    }
    compose.waitForIdle()
    assertEquals(onBackground, textColor("Barra da sola"))
  }
}
