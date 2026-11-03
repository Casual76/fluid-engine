package dev.antigravity.fluidengine.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'altezza di un [FluidButton] e' un minimo (3.0.4): con il testo a scala grande l'etichetta deve restare
 * dentro il pulsante, che cresce. A scala normale il pulsante resta alto quanto il suo disegno.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidButtonFontScaleTest {

  @get:Rule
  val compose = createComposeRule()

  private val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

  private fun setButton(fontScale: Float) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
          FluidButton(text = "Conferma", onClick = {})
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `a scala normale il pulsante resta alto quanto il suo disegno`() {
    setButton(1f)
    compose.onNode(buttonRole, useUnmergedTree = true)
      .assertHeightIsEqualTo(FluidButtonDefaults.height(FluidButtonSize.Medium))
  }

  @Test
  fun `con il testo a 200 per cento l'etichetta sta dentro il pulsante`() {
    setButton(2f)
    val button = compose.onNode(buttonRole, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val label = compose.onNodeWithText("Conferma", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(
      "etichetta $label fuori dal pulsante $button",
      label.left >= button.left && label.top >= button.top &&
        label.right <= button.right && label.bottom <= button.bottom,
    )
  }
}
