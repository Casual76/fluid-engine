package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidKeyboardBar
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h750dp-xhdpi")
class FluidKeyboardBarComposeTest {
  @get:Rule val compose = createComposeRule()

  @Test fun anAboveKeyboardSearchIsMeasuredAboveTheBarInFluidScreen() {
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          FluidScreen(
            title = "Prova",
            modifier = Modifier.fillMaxSize(),
            toolbar = { keyboard("format", alwaysVisible = true) { button("done", FluidIcons.Check, "Fatto", {}) } },
            search = FluidSearchSpec.findInPage("lago", {}, FluidSearchNavigation(0, 1, {}, {}),
              active = true, onActiveChange = {}),
            barMinimize = FluidBarMinimizeBehavior.Never,
          ) {}
        }
      }
    }
    val field = compose.onNode(hasSetTextAction()).getUnclippedBoundsInRoot()
    val button = compose.onNodeWithContentDescription("Fatto").getUnclippedBoundsInRoot()
    assertTrue("Il campo finisce a ${field.bottom}, il tasto comincia a ${button.top}", field.bottom <= button.top)
  }

  @Test fun theStandaloneBarRoutesClicksToTheCurrentContext() {
    val first = mutableStateOf(true)
    val tapped = mutableListOf<String>()
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          Box(Modifier.fillMaxSize()) {
            val label = if (first.value) "Primo" else "Secondo"
            FluidKeyboardBar(context = label, alwaysVisible = true, toolbar = {
              button("command", FluidIcons.Check, label, { tapped += label })
            })
          }
        }
      }
    }
    compose.onNodeWithContentDescription("Primo").performClick()
    compose.runOnIdle { first.value = false }
    compose.onNodeWithContentDescription("Secondo").performClick()
    compose.onNodeWithContentDescription("Primo").assertDoesNotExist()
    compose.runOnIdle { assertEquals(listOf("Primo", "Secondo"), tapped) }
  }
}
