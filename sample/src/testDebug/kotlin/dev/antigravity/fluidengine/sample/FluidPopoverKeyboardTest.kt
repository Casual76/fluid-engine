package dev.antigravity.fluidengine.sample

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
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
