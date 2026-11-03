package dev.antigravity.fluidengine.sample

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il tasto che svuota un campo (3.0.4): ha un nome per TalkBack, e' un pulsante, e il suo bersaglio e' di 48 dp
 * anche se il disegno e' di 28. Un tocco nella parte che non si disegna lo svuota.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidClearButtonTest {

  @get:Rule
  val compose = createComposeRule()

  private var text by mutableStateOf("ciao")

  private fun setField(labels: FluidLabels = FluidLabels()) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidLabels provides labels) {
          FluidTextField(value = text, onValueChange = { text = it })
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `il tasto che svuota ha un nome, e' un pulsante e ha un bersaglio di 48 dp`() {
    setField()
    val clear = compose.onNodeWithContentDescription("Cancella")
    clear.assertHasClickAction()
    clear.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    clear.assertWidthIsAtLeast(48.dp)
    clear.assertHeightIsAtLeast(48.dp)
  }

  @Test
  fun `toccarlo svuota il campo`() {
    setField()
    compose.onNodeWithContentDescription("Cancella").performClick()
    compose.waitForIdle()
    assertEquals("", text)
  }

  @Test
  fun `un tocco nel bersaglio fuori dal disegno svuota il campo`() {
    setField()
    // Il disegno sta nei 28 dp centrali dei 48: a un decimo della larghezza il dito e' gia' fuori dal cerchio.
    compose.onNodeWithContentDescription("Cancella")
      .performTouchInput { click(Offset(width * 0.1f, height / 2f)) }
    compose.waitForIdle()
    assertEquals("", text)
  }

  @Test
  fun `un'app tradotta gli da' il suo nome`() {
    setField(FluidLabels(clear = "Clear"))
    compose.onNodeWithContentDescription("Clear").assertHasClickAction()
    compose.onNodeWithContentDescription("Cancella").assertDoesNotExist()
  }
}
