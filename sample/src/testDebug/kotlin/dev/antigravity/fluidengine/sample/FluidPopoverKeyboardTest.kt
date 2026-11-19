package dev.antigravity.fluidengine.sample

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il pop-up sale sopra la tastiera (3.0.1), come il foglio e l'avviso.
 *
 * Un pop-up con un campo di ricerca (il selettore dei formati di universal_converter) finiva sotto la
 * tastiera: solo i fogli avevano `imePadding`. Qui la tastiera e' un inserto `ime` mandato alla vista di
 * Compose, come lo manda il sistema.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPopoverKeyboardTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var root: View

  @Test
  fun `la conferma vive sopra il comando e lascia attivo il resto della pagina`() {
    var backgroundClicks = 0
    val visible = mutableStateOf(false)
    val anchored = mutableStateOf(true)
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val namespace = rememberFluidGlassNamespace()
        val source = remember(namespace) { FluidTransitionSource("delete", namespace) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.BottomCenter)
              .fluidTransitionSource("delete", namespace).size(48.dp).testTag("origin"))
            Box(Modifier.align(Alignment.TopStart).size(48.dp).testTag("background")
              .clickable { backgroundClicks++ })
            FluidConfirmationDialog(
              visible = visible.value, onDismissRequest = { visible.value = false },
              source = source.takeIf { anchored.value }, title = "Eliminare la voce?",
              actions = listOf(FluidAlertAction("Elimina", {})),
            )
            FluidGlassModalHost(host, backdrop = null)
          }
        }
      }
    }
    compose.runOnIdle { visible.value = true }
    val origin = compose.onNodeWithTag("origin").getUnclippedBoundsInRoot()
    val action = compose.onNodeWithText("Elimina").getUnclippedBoundsInRoot()
    assertTrue("conferma sopra la sorgente: $action / $origin", action.bottom < origin.top)
    compose.onNodeWithText("Annulla").assertDoesNotExist()
    compose.onNodeWithTag("background").performTouchInput { click() }
    compose.onNodeWithText("Elimina").assertDoesNotExist()
    compose.runOnIdle {
      assertEquals(1, backgroundClicks)
      anchored.value = false
      visible.value = true
    }
    compose.onNodeWithText("Annulla").assertExists()
  }

  @Test
  fun `un pop-up aperto si posa sopra la tastiera`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidGlassModalPortal(visible = true, onDismissRequest = {}) {
              Box(Modifier.size(width = 200.dp, height = 160.dp).testTag("pane"))
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    val before = compose.onNodeWithTag("pane").getUnclippedBoundsInRoot()
    // Senza tastiera, al centro dello schermo da 640 dp.
    assertTrue("al centro: $before", before.bottom.value > 380f)

    // La tastiera: 400 dp dal basso (800 px a densita' 2).
    compose.runOnUiThread {
      val keyboard = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, 800))
        .setVisible(WindowInsetsCompat.Type.ime(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, keyboard)
    }
    compose.waitForIdle()

    val after = compose.onNodeWithTag("pane").getUnclippedBoundsInRoot()
    assertTrue("sopra la tastiera (240 dp): $after", after.bottom.value <= 240f)
  }
}
