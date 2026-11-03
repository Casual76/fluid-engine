package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCollapsingTitle
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.rememberFluidTitleCollapse
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il titolo di una sezione e il titolo grande di una pagina sono intestazioni per TalkBack (3.0.4): chi scorre
 * lo schermo per titoli li deve trovare. Il dettaglio sotto il titolo di sezione non lo e'.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidHeadingSemanticsTest {

  @get:Rule
  val compose = createComposeRule()

  private val isHeading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

  @Test
  fun `il titolo di una sezione e' un'intestazione, il dettaglio no`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidSectionHeader(title = "Dati", detail = "Quello che resta sul telefono")
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("DATI").assert(isHeading)
    compose.onNodeWithText("Quello che resta sul telefono")
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
  }

  @Test
  fun `il titolo grande di una pagina e' un'intestazione`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val listState = rememberLazyListState()
        val collapse = rememberFluidTitleCollapse(title = "Impostazioni", listState = listState)
        LazyColumn(state = listState) {
          item { FluidCollapsingTitle(title = "Impostazioni", collapse = collapse) }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Impostazioni").assert(isHeading)
  }
}
