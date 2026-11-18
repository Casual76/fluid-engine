package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidToolbarSearchHostTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun settle() {
    repeat(45) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  @Test fun bareScreenOpensBottomEditorAndClosesWithoutLosingText() {
    compose.mainClock.autoAdvance = false
    var submitted = ""
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        var query by remember { mutableStateOf("") }
        FluidScreen(title = "Note", search = FluidSearchSpec(query, { query = it },
          behavior = FluidSearchBehavior.CollapseToButton, prompt = "Scrivi una nota",
          purpose = FluidSearchPurpose.Input, onSearch = { submitted = query },
          scopes = { Text("Privata") }, suggestions = { Text("Suggerimenti") })) {}
      }
    }
    settle()
    val button = compose.onNodeWithContentDescription("Scrivi una nota")
    val below = button.getBoundsInRoot()
    assertTrue(below.top > 200.dp)
    button.performClick()
    settle()
    val editor = compose.onNode(hasSetTextAction())
    editor.assertIsFocused().performTextInput("bozza")
    settle()
    editor.performImeAction()
    assertEquals("bozza", submitted)
    val scope = compose.onNodeWithText("Privata").getBoundsInRoot()
    val bounds = editor.getBoundsInRoot()
    assertTrue(scope.top < bounds.bottom && scope.bottom > bounds.top)
    compose.onNodeWithText("Suggerimenti").assertExists()
    compose.onNodeWithContentDescription("Chiudi").performClick()
    settle()
    editor.assertDoesNotExist()
    button.performClick()
    settle()
    editor.assertTextContains("bozza")
    compose.onNodeWithContentDescription("Cancella").performClick()
    settle()
    editor.assertTextEquals("").assertIsFocused()
  }

  @Test fun hostedDSLTravelsFromTopAndPinnedActionReturns() {
    var composed = 0
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidPageHost(stack = listOf("posta"), onBack = {}) {
          var query by remember { mutableStateOf("") }
          FluidScreen(title = "Posta", barMinimize = FluidBarMinimizeBehavior.OnScrollDown, toolbar = {
            button("pin", FluidIcons.Add, "Componi", { composed++ }, placement = FluidToolbarPlacement.PinnedTrailing)
            search(FluidSearchSpec(query, { query = it }, FluidSearchPlacement.TopBar,
              FluidSearchBehavior.CollapseToButton, "Cerca posta", expansion = FluidSearchExpansion.AboveKeyboard))
          }) {}
        }
      }
    }
    settle()
    val lens = compose.onNodeWithContentDescription("Cerca posta")
    val rest = lens.getBoundsInRoot()
    val pin = compose.onNodeWithContentDescription("Componi")
    assertTrue(rest.right <= pin.getBoundsInRoot().left)
    lens.performClick()
    compose.mainClock.advanceTimeBy(65)
    compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    val editor = compose.onNode(hasSetTextAction())
    editor.assertIsFocused().performTextInput("i")
    settle()
    editor.performTextInput("n viaggio")
    settle()
    editor.assertTextEquals("in viaggio").assertIsFocused()
    val selection = TextRange(2, 3)
    editor.performTextInputSelection(selection)
    // Le due azioni semantiche leggono lo stato pubblicato dall'ultimo frame dell'orologio manuale.
    settle()
    assertEquals(selection, editor.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
    editor.performTextInput("-")
    settle()
    editor.assertTextEquals("in-viaggio")
    assertTrue(editor.getBoundsInRoot().top > rest.bottom + 100.dp)
    pin.assertDoesNotExist()
    val commands = compose.onNodeWithContentDescription("Altro").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    assertTrue(commands.single { it.label == "Componi" }.action())
    assertEquals(1, composed)
    compose.onNodeWithContentDescription("Chiudi la ricerca").performClick()
    settle()
    assertEquals(rest, lens.getBoundsInRoot())
    pin.assertExists()
  }

  @Test fun searchTabOwnsOneEditorAndReturnsToPreviousTab() {
    compose.mainClock.autoAdvance = false
    var selected by mutableStateOf("oggi")
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        var query by remember { mutableStateOf("") }
        FluidFloatingTabBar(
          selectedTabKey = selected, scrollConnection = rememberFluidFloatingTabBarScrollConnection(),
          searchActivation = FluidTabSearchActivation.SearchTabSelection,
          searchBarContent = { field -> FluidSearchField(query, { query = it }, field, autoFocus = false) },
        ) {
          tab("oggi", { Text("Oggi") }, { FluidIcon(FluidIcons.Add, null) }, { selected = "oggi" })
          standaloneTab("cerca", { FluidIcon(FluidIcons.Search, "Cerca scheda") }, FluidTabRole.Search,
            onClick = { selected = "cerca" }, title = { Text("Ricerca") })
        }
      }
    }
    settle()
    compose.onNodeWithContentDescription("Cerca scheda").performClick()
    settle()
    compose.onAllNodes(hasSetTextAction()).assertCountEquals(1)
    compose.onNode(hasSetTextAction()).assertIsFocused().performTextInput("foto")
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    settle()
    assertEquals("oggi", selected)
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
  }
}
