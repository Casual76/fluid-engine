package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidTokenFieldInteractionTest {
  @get:Rule val compose = createComposeRule()
  private var value by mutableStateOf(FluidTokenFieldValue())
  private var suggested = ""
  private lateinit var host: android.view.View
  private fun setField(scale: Float = 1f, rtl: Boolean = false, editable: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = dev.antigravity.fluidengine.foundation.ThemeMode.DARK) }) {
        host = androidx.compose.ui.platform.LocalView.current
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, scale),
          LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
          LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            FluidTokenField(value, { value = it }, { raw, _ -> FluidToken(raw, raw, raw == "errore") },
              label = "Etichette", onEditToken = if (editable) { token ->
                value = value.copy(tokens = value.tokens.filterNot { it.id == token.id },
                  draft = androidx.compose.ui.text.input.TextFieldValue(token.label))
              } else null, suggestions = listOf(FluidTokenSuggestion("one", "Uno", "Suggerimento")),
              onSuggestionClick = { suggested = it.id; value = value.copy(tokens = value.tokens + FluidToken(it.id, it.title)) })
            FluidTextField("", {}, label = "Altro")
          }
        }
      }
    }
  }
  private fun input() = compose.onNode(hasSetTextAction() and hasContentDescription("Etichette"))

  @Test fun separatorPasteAndFocusLoss() {
    setField()
    input().performTextInput("uno,")
    compose.runOnIdle { assertEquals(listOf("uno"), value.tokens.map { it.label }) }
    input().performTextInput("due;tre")
    compose.runOnIdle { assertEquals(3, value.tokens.size) }
    input().performTextInput("bozza")
    compose.onAllNodes(hasSetTextAction())[1].performClick()
    compose.runOnIdle { assertEquals(4, value.tokens.size); assertEquals("", value.draft.text) }
  }
  @Test fun backspaceSelectsThenRemoves() {
    value = FluidTokenFieldValue(listOf(FluidToken("one", "Uno")))
    setField()
    input().performClick().performKeyInput { pressKey(Key.Backspace) }
    compose.runOnIdle { assertEquals("one", value.selectedTokenId); assertEquals(1, value.tokens.size) }
    input().performKeyInput { pressKey(Key.Backspace) }
    compose.runOnIdle { assertTrue(value.tokens.isEmpty()) }
  }
  @Test fun softKeyboardCompositionAndEmptyDelete() {
    setField()
    input().performClick()
    lateinit var connection: android.view.inputmethod.InputConnection
    compose.runOnIdle {
      connection = host.onCreateInputConnection(android.view.inputmethod.EditorInfo())!!
      connection.setComposingText("caff?,", 1)
    }
    compose.runOnIdle { assertTrue(value.tokens.isEmpty()); assertNotNull(value.draft.composition) }
    compose.runOnIdle { connection.finishComposingText() }
    compose.runOnIdle { assertEquals("caff?", value.tokens.single().label) }
    compose.runOnIdle { connection.deleteSurroundingText(1, 0) }
    compose.runOnIdle { assertEquals("caff?", value.selectedTokenId); assertEquals(1, value.tokens.size) }
    compose.runOnIdle { connection.deleteSurroundingText(1, 0) }
    compose.runOnIdle { assertTrue(value.tokens.isEmpty()) }
  }
  @Test fun invalidSemanticsCountAndRemoveAction() {
    value = FluidTokenFieldValue(listOf(FluidToken("bad", "Errore", true)))
    setField()
    input().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Gettoni: 1"))
    val token = compose.onNodeWithContentDescription("Errore")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, "non valido"))
    compose.runOnIdle {
      val actions = token.fetchSemanticsNode().config[SemanticsActions.CustomActions]
      assertEquals("Rimuovi", actions.single().label); actions.single().action()
    }
    compose.runOnIdle { assertTrue(value.tokens.isEmpty()) }
  }
  @Test fun suggestionsRemainInPageAndKeyboardSelects() {
    setField()
    input().performClick()
    compose.onNodeWithText("Uno").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
    input().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.Enter) }
    compose.runOnIdle { assertEquals("one", suggested) }
    input().assertIsFocused()
  }
  @Test fun removeTargetAndOptionalEditing() {
    value = FluidTokenFieldValue(listOf(FluidToken("one", "Uno"), FluidToken("two", "Due")))
    setField(editable = true)
    compose.onNodeWithContentDescription("Due").performTouchInput {
      click(androidx.compose.ui.geometry.Offset(width - 10f, height / 2f))
    }
    compose.runOnIdle { assertEquals(listOf("one"), value.tokens.map { it.id }) }
    val token = compose.onNodeWithContentDescription("Uno")
    val actions = token.fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnIdle { actions.first { it.label == "Modifica" }.action() }
    compose.runOnIdle { assertTrue(value.tokens.isEmpty()); assertEquals("Uno", value.draft.text) }
    input().assertIsFocused()
  }
  @Test fun largeTextRtlAndManyTokensHaveBoundedWidthAndGrow() {
    value = FluidTokenFieldValue((1..30).map { FluidToken("$it", "مرحبا بالعالم · etichetta molto lunga $it") })
    setField(2f, true)
    val token = compose.onNodeWithContentDescription("مرحبا بالعالم · etichetta molto lunga 1")
    token.assertHeightIsAtLeast(48.dp).assertIsDisplayed()
    val bounds = token.fetchSemanticsNode().boundsInRoot
    assertTrue(bounds.width <= compose.density.run { 280.dp.toPx() } + 1f)
    assertEquals(30, value.tokens.size)
  }
}
