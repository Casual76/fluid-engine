package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Contratto Compose senza fingere che Robolectric esegua il renderer Chromium. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidWebContentTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun emptyAtTwoHundredPercentDarkAndReducedMotion() {
    val state = FluidWebContentState()
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = ThemeMode.DARK), systemBars = false) {
        CompositionLocalProvider(LocalDensity provides Density(2f, 2f)) {
          FluidMotionPolicyProvider(FluidMotionPolicy(0f)) {
            FluidWebContent("", {}, state = state, placeholder = { Text("loading") })
          }
        }
      }
    }
    compose.runOnIdle {
      assertEquals(FluidWebPhase.Empty, state.phase)
      assertNull(state.printAdapter("test"))
    }
  }

  @Test fun oversizedDocumentUsesAppFailureWithoutCreatingWebView() {
    val state = FluidWebContentState()
    compose.setContent {
      FluidWebContent("x".repeat(FluidWebContentDefaults.MaxHtmlCharacters + 1), {}, state = state,
        placeholder = { Text("loading") }, failure = { Text("failure:$it") })
    }
    compose.waitUntil(5_000) { state.phase == FluidWebPhase.Error }
    compose.onNodeWithText("failure:ContentTooLarge").assertExists()
    compose.runOnIdle { state.retry() }
    compose.waitUntil(5_000) { state.phase == FluidWebPhase.Error }
    assertEquals(FluidWebFailure.ContentTooLarge, state.failure)
  }
}
