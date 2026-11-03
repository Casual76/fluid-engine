package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Le parole che l'engine dice da se' vengono dall'app (3.0.1).
 *
 * L'occhio di un campo segreto, la X di una pagina intera e il tasto indietro della barra erano scritti in
 * italiano dentro i componenti: un'app inglese li leggeva in italiano con TalkBack. Di serie restano
 * italiani; con [LocalFluidLabels] li dice l'app, una volta sola alla radice, anche dentro gli host.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidLabelsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val english = FluidLabels(
    close = "Close",
    showSecret = "Show text",
    hideSecret = "Hide text",
    back = "Back",
  )

  /** La pagina intera copre tutto: la si apre dopo aver toccato l'occhio. */
  private var pageOpen by mutableStateOf(false)

  @Composable
  private fun Screen(labels: FluidLabels?) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val content: @Composable () -> Unit = {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidPageChrome(title = "Barra", onBack = {})
            FluidTextField(value = "segreto", onValueChange = {}, secret = true)
            FluidGlassModalPortal(
              visible = pageOpen,
              onDismissRequest = {},
              presentation = FluidGlassModalPresentation.FullScreen,
              paneTitle = "Pagina intera",
            ) { Text("Dentro") }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
      if (labels == null) content() else CompositionLocalProvider(LocalFluidLabels provides labels, content = content)
    }
  }

  @Test
  fun `di serie le parole sono italiane`() {
    pageOpen = true
    compose.setContent { Screen(labels = null) }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Mostra il testo").assertExists()
    compose.onNodeWithContentDescription("Chiudi").assertExists()
    compose.onNodeWithContentDescription("Indietro").assertExists()
    assertEquals("Marzo, 3 di 12", FluidLabels().sectionPosition("Marzo", 3, 12))
  }

  @Test
  fun `un'app tradotta le passa alla radice e valgono anche dentro gli host`() {
    compose.setContent { Screen(labels = english) }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Show text").performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Hide text").assertExists()
    compose.runOnIdle { pageOpen = true }
    compose.waitForIdle()
    // La X della pagina intera sta nell'host, alla radice: legge le parole da dove l'host e' composto.
    compose.onNodeWithContentDescription("Close").assertExists()
    compose.onNodeWithContentDescription("Back").assertExists()
    compose.onNodeWithContentDescription("Chiudi").assertDoesNotExist()
  }
}
