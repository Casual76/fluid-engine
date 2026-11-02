package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSize
import dev.antigravity.fluidengine.ui.fluid.FluidTextField
import dev.antigravity.fluidengine.ui.fluid.fluidGlassGroups
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidToneTile
import dev.antigravity.fluidengine.ui.theme.FluidTone
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I buchi che le app dichiaravano (fase 12a), dentro un albero Compose vero.
 *
 * La logica pura (le taglie, il contrasto del riempimento, il piano dei pannelli, la mappa
 * dell'aptica senza View) sta in `engine-ui/src/test`; qui c'e' quello che una funzione pura non
 * puo' dire: che il riquadro misura quanto dice, che un campo segreto e' una password per chi legge
 * lo schermo finche' non si tocca l'occhio, che un messaggio toccabile e' un pulsante.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidGapsTest {

  @get:Rule
  val compose = createComposeRule()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }, content = content)
  }

  @Test
  fun `un tasto tondo di vetro occupa 48 dp anche quando il vetro e' da 40`() {
    compose.setContent {
      Themed {
        Column {
          FluidGlassSize.entries.forEach { size ->
            FluidGlassIconButton(
              onClick = {},
              size = size,
              modifier = Modifier.testTag("icon-${size.name}"),
            ) {}
          }
        }
      }
    }
    compose.waitForIdle()
    FluidGlassSize.entries.forEach { size ->
      compose.onNodeWithTag("icon-${size.name}")
        .assertWidthIsEqualTo(size.touchTarget)
        .assertHeightIsEqualTo(size.touchTarget)
      // Il click sta sul vetro, dentro il riquadro: e' il figlio che ha l'azione.
      compose.onNodeWithTag("icon-${size.name}").onChildren().onFirst().assertHasClickAction()
    }
    compose.onNodeWithTag("icon-Compact").assertWidthIsEqualTo(48.dp)
    compose.onNodeWithTag("icon-Large").assertWidthIsEqualTo(54.dp)
  }

  @Test
  fun `un tocco nel margine attorno a un vetro da 40 dp clicca lo stesso`() {
    // La prova che il bersaglio e' da 48 dp e non da 40: l'angolo del riquadro sta a 4 dp dal
    // vetro per lato, e Compose da' a ogni bersaglio piccolo il suo minimo da 48.
    var clicks = 0
    compose.setContent {
      Themed {
        FluidGlassIconButton(
          onClick = { clicks++ },
          size = FluidGlassSize.Compact,
          modifier = Modifier.testTag("compact"),
        ) {}
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("compact").performTouchInput { click(Offset(3f, 3f)) }
    compose.waitForIdle()
    assertEquals("il margine fuori dal vetro non clicca", 1, clicks)
  }

  @Test
  fun `un tasto di vetro con testo non e' mai piu' basso di 48 dp da toccare`() {
    compose.setContent {
      Themed {
        Column {
          FluidGlassSize.entries.forEach { size ->
            // L'ingombro nel layout lo misura chi sta attorno: il tag sul tasto stesso leggerebbe
            // il bordo di un nodo, e quale dipende dall'ordine dei modificatori.
            Box(Modifier.testTag("text-${size.name}")) {
              FluidGlassButton(text = size.name, onClick = {}, size = size)
            }
          }
        }
      }
    }
    compose.waitForIdle()
    // Compact: il vetro e' da 40, il riquadro che il tasto occupa e' da 48.
    compose.onNodeWithTag("text-Compact").assertHeightIsAtLeast(48.dp)
    // Regular e' l'altezza di sempre; Large e' piu' alto.
    compose.onNodeWithTag("text-Regular").assertHeightIsEqualTo(48.dp)
    compose.onNodeWithTag("text-Large").assertHeightIsEqualTo(54.dp)
  }

  @Test
  fun `un campo segreto e' una password finche' non si tocca l'occhio, poi si legge`() {
    var value by mutableStateOf("hunter2")
    compose.setContent {
      Themed {
        FluidTextField(
          value = value,
          onValueChange = { value = it },
          secret = true,
          modifier = Modifier.testTag("field"),
        )
      }
    }
    compose.waitForIdle()
    val isPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
    val dots = "\u2022".repeat(7)
    compose.onNode(hasSetTextAction()).assert(isPassword)
    compose.onNode(hasSetTextAction()).assert(shows(dots))
    // L'occhio dice cosa fara', non cosa e': il testo e' nascosto, quindi "Mostra".
    compose.onNodeWithContentDescription("Nascondi il testo").assertDoesNotExist()
    compose.onNodeWithContentDescription("Mostra il testo").performClick()
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Nascondi il testo").assertExists()
    compose.onNode(hasSetTextAction()).assert(shows("hunter2"))
    // Il testo del chiamante non e' mai stato toccato dal gioco dell'occhio.
    assertEquals("hunter2", value)

    compose.onNodeWithContentDescription("Nascondi il testo").performClick()
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).assert(isPassword)
    compose.onNode(hasSetTextAction()).assert(shows(dots))
  }

  @Test
  fun `con una tastiera scelta dal chiamante il campo segreto e' nascosto e l'occhio lo mostra`() {
    // Senza la tastiera delle password di serie il campo e' comunque una password finche' ci sono i
    // puntini, e l'occhio lo mostra: le due cose non dipendono dalla tastiera.
    compose.setContent {
      Themed {
        FluidTextField(
          value = "chiave",
          onValueChange = {},
          secret = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
      }
    }
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
    compose.onNode(hasSetTextAction()).assert(shows("•".repeat(6)))
    compose.onNodeWithContentDescription("Mostra il testo").performClick()
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).assert(shows("chiave"))
  }

  /** Quello che il campo mostra, dopo la trasformazione visiva: puntini o testo. */
  private fun shows(expected: String) = SemanticsMatcher("mostra $expected") { node ->
    // `EditableText` e' il testo gia' trasformato (puntini o chiaro); `InputText` e' quello vero.
    node.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
  }

  @Test
  fun `un campo normale non ha l'occhio`() {
    compose.setContent {
      Themed { FluidTextField(value = "ciao", onValueChange = {}) }
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Mostra il testo").assertDoesNotExist()
    compose.onNodeWithContentDescription("Nascondi il testo").assertDoesNotExist()
  }

  @Test
  fun `un messaggio con onClick e' un pulsante che clicca e mostra la sua azione`() {
    var taps = 0
    compose.setContent {
      Themed {
        FluidInlineMessage(
          title = "Sei offline",
          message = "Mostro quello che c'era.",
          action = "Riprova adesso",
          onClick = { taps++ },
          modifier = Modifier.testTag("message"),
        )
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Riprova adesso").assertExists()
    compose.onNodeWithTag("message").assertHasClickAction().performClick()
    compose.waitForIdle()
    assertEquals(1, taps)
  }

  @Test
  fun `un messaggio senza onClick non si tocca e non finge di avere un'azione`() {
    compose.setContent {
      Themed {
        FluidInlineMessage(
          title = "Solo da leggere",
          message = "Niente da toccare.",
          // Senza onClick l'azione non conta: una riga di testo che finge di essere un tasto.
          action = "Riprova adesso",
          modifier = Modifier.testTag("message"),
        )
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("message").assertHasNoClickAction()
    compose.onNodeWithText("Riprova adesso").assertDoesNotExist()
  }

  @Test
  fun `la piastrella pubblica misura 32 dp con 19 dp di contenuto, e scala con la taglia`() {
    compose.setContent {
      Themed {
        Column {
          FluidToneTile(tone = FluidTone.Success, modifier = Modifier.testTag("tone")) {
            Box(Modifier.fillMaxSize().testTag("tone-content"))
          }
          FluidToneTile(color = Color.Magenta, size = 64.dp, modifier = Modifier.testTag("big")) {
            Box(Modifier.fillMaxSize().testTag("big-content"))
          }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("tone").assertWidthIsEqualTo(32.dp).assertHeightIsEqualTo(32.dp)
    compose.onNodeWithTag("tone-content").assertWidthIsEqualTo(19.dp)
    compose.onNodeWithTag("big").assertWidthIsEqualTo(64.dp)
    compose.onNodeWithTag("big-content").assertWidthIsEqualTo(38.dp)
  }

  @Test
  fun `lo stato vuoto ha l'illustrazione solo se gliela si da`() {
    var withArt by mutableStateOf(true)
    compose.setContent {
      Themed {
        FluidEmptyState(
          title = "Niente qui",
          detail = "Aggiungi qualcosa.",
          illustration = if (withArt) {
            { Box(Modifier.height(40.dp).fillMaxWidth().testTag("art")) }
          } else {
            null
          },
        )
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Niente qui").assertExists()
    // L'illustrazione e' decorazione: fuori dall'albero letto da TalkBack, dentro quello vero.
    compose.onNodeWithTag("art", useUnmergedTree = true).assertExists()
    withArt = false
    compose.waitForIdle()
    compose.onNodeWithTag("art", useUnmergedTree = true).assertDoesNotExist()
    compose.onNodeWithText("Niente qui").assertExists()
  }

  @Test
  fun `una lista lunga diventa un pannello ogni otto righe dentro la lista pigra`() {
    val state = LazyListState()
    compose.setContent {
      Themed {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
          fluidGlassGroups(items = (0 until 20).toList(), key = "demo") { index ->
            Box(Modifier.fillMaxWidth().height(40.dp).testTag("row-$index"))
          }
        }
      }
    }
    compose.waitForIdle()
    // Venti righe, otto per pannello: tre item della lista, non venti.
    assertEquals(3, state.layoutInfo.totalItemsCount)
    compose.onNodeWithTag("row-0").assertExists()
    // La riga di confine fra il primo e il secondo pannello e' nella lista come tutte le altre.
    compose.onNodeWithTag("row-8").assertExists()
  }
}
