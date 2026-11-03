package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.page.FluidPageChrome
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidImageViewer
import dev.antigravity.fluidengine.ui.fluid.FluidPickerOption
import dev.antigravity.fluidengine.ui.fluid.FluidPickerRow
import dev.antigravity.fluidengine.ui.fluid.FluidSliderRow
import dev.antigravity.fluidengine.ui.fluid.FluidStepProgress
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidGlassGroupsIndexed
import dev.antigravity.fluidengine.ui.fluid.fluidUpdateSection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidCard
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Le aggiunte della 3.0.1 che hanno un comportamento: la voce scelta di un menu, la card spenta, la riga
 * che sceglie in un pop-up, la riga con lo slider, la sezione degli aggiornamenti, il fondo del
 * visualizzatore, i nomi dei passi, l'indice dei gruppi di vetro, lo stato del menu da lontano.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidApiAdditionsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Composable
  private fun Themed(dark: Boolean = false, content: @Composable () -> Unit) {
    val settings = remember(dark) { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
    FluidTheme(settings = settings, content = content)
  }

  /** Il colore e le righe con cui il testo [text] e' stato composto. */
  private fun layout(text: String): TextLayoutResult {
    val node = compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().first()
    val layouts = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    return layouts.first()
  }

  @Test
  fun `la voce scelta di un menu e' selezionata per TalkBack, le altre no`() {
    compose.setContent {
      Themed {
        val morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidMorphMenuButton(
            state = morph,
            actions = {
              listOf("Nome", "Data", "Peso").map { label ->
                FluidContextAction(label = label, selected = label == "Data", onClick = {})
              }
            },
            onClick = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).testTag("ordina"),
            text = "Ordina",
            backdrop = backdrop,
            opensOnTap = true,
          )
          FluidMorphMenuHost(state = morph, backdrop = backdrop)
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("ordina").performClick()
    compose.waitForIdle()

    compose.onNodeWithText("Data").assertIsSelected()
    compose.onNodeWithText("Nome").assert(!isSelected())
  }

  @Test
  fun `una card spenta non risponde e lo dice`() {
    var clicks = 0
    compose.setContent {
      Themed {
        FluidCard(onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("card")) {
          Text("Spenta")
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("card").assertIsNotEnabled()
    compose.onNodeWithTag("card").performClick()
    compose.waitForIdle()
    assertEquals(0, clicks)
  }

  @Test
  fun `la riga del selettore apre le alternative, sceglie e si richiude`() {
    var chosen by mutableStateOf("medio")
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidPickerRow(
              title = "Qualita'",
              selectedKey = chosen,
              options = listOf(
                FluidPickerOption("bassa", "Bassa"),
                FluidPickerOption("medio", "Media"),
                FluidPickerOption("alta", "Alta", subtitle = "File piu' grandi"),
              ),
              onSelected = { chosen = it },
              modifier = Modifier.testTag("picker"),
            )
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    // Un nodo solo: un elenco a discesa con la scelta di adesso come stato.
    compose.onNodeWithTag("picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.DropdownList))
    compose.onNodeWithTag("picker").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Media"))

    compose.onNodeWithTag("picker").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Alta").performClick()
    compose.waitForIdle()

    assertEquals("alta", chosen)
    assertTrue("il pop-up si e' chiuso", compose.onAllNodesWithText("Bassa").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun `la riga con lo slider da' allo slider il titolo come nome e il valore come stato`() {
    compose.setContent {
      Themed {
        FluidSliderRow(title = "Volume", value = 0.3f, onValueChange = {}, valueLabel = "30%")
      }
    }
    compose.waitForIdle()
    val slider = compose.onAllNodesWithText("Volume").fetchSemanticsNodes()
    assertTrue("il titolo si legge una volta sola, dallo slider", slider.isEmpty())
    val nodes = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "30%"))
      .fetchSemanticsNodes()
    assertEquals(1, nodes.size)
    assertEquals(listOf("Volume"), nodes.first().config.getOrNull(SemanticsProperties.ContentDescription))
  }

  @Test
  fun `le note di una versione nuova stanno in tre righe, e il controllo all'avvio ha la sua riga`() {
    val notes = (1..12).joinToString(" ") { "Una novita' che si dilunga, numero $it." }
    var autoCheck by mutableStateOf(false)
    compose.setContent {
      Themed {
        LazyColumn {
          fluidUpdateSection(
            state = FluidUpdateUiState.Available(FluidUpdateRelease(version = "1.2.0", notes = notes)),
            onCheck = {},
            onInstall = {},
            autoCheck = autoCheck,
            onAutoCheckChange = { autoCheck = it },
          )
        }
      }
    }
    compose.waitForIdle()
    assertTrue(layout(notes).lineCount <= 3)
    compose.onNodeWithText("Controlla all'avvio").performClick()
    compose.waitForIdle()
    assertTrue(autoCheck)
  }

  @Test
  fun `il visualizzatore su un fondo nero ha il titolo chiaro`() {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidImageViewer(
              visible = true,
              onDismissRequest = {},
              pageCount = 3,
              background = Color.Black,
            ) { Box(Modifier.fillMaxSize()) }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    assertEquals(Color.White, layout("1 di 3").layoutInput.style.color)
  }

  @Test
  fun `i nomi dei passi stanno sotto la barra, e quello in corso e' lo stato`() {
    compose.setContent {
      Themed {
        FluidStepProgress(
          step = 2,
          steps = 3,
          segmented = true,
          labels = listOf("Formato", "Opzioni", "Converti"),
          modifier = Modifier.testTag("passi"),
        )
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("passi").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Opzioni"))
    assertTrue(compose.onAllNodesWithText("Converti").fetchSemanticsNodes().isEmpty())
    // Disegnati, anche se detti dallo stato: il testo c'e' nell'albero di composizione.
    compose.onNodeWithTag("passi").assertExists()
  }

  @Test
  fun `i gruppi di vetro passano l'indice nella lista data`() {
    val items = (0 until 11).map { "voce $it" }
    compose.setContent {
      Themed {
        LazyColumn {
          fluidGlassGroupsIndexed(items = items, maxRows = 4) { index, item -> Text("$index: $item") }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("0: voce 0").assertExists()
    compose.onNodeWithText("5: voce 5").assertExists()
    compose.onNodeWithText("8: voce 8").assertExists()
  }

  @Test
  fun `una voce lunga del menu si legge intera, senza puntini`() {
    val long = "Salta i silenzi oltre 12 s: spento"
    compose.setContent {
      Themed {
        val morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidMorphMenuButton(
            state = morph,
            actions = { listOf(FluidContextAction(label = long, onClick = {}), FluidContextAction(label = "Altro", onClick = {})) },
            onClick = {},
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp).testTag("tasto"),
            text = "Opzioni",
            backdrop = backdrop,
            opensOnTap = true,
          )
          FluidMorphMenuHost(state = morph, backdrop = backdrop)
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("tasto").performClick()
    compose.waitForIdle()
    assertFalse("tagliata: ${layout(long).layoutInput.constraints}", layout(long).hasVisualOverflow)
  }

  @Test
  fun `la sezione dell'aspetto senza testata, per una pagina che si chiama gia' Aspetto`() {
    compose.setContent {
      Themed {
        LazyColumn {
          fluidAppearanceSection(settings = EngineSettings(), onSettingsChange = {}, title = null)
        }
      }
    }
    compose.waitForIdle()
    assertTrue(compose.onAllNodesWithText("Aspetto").fetchSemanticsNodes().isEmpty())
    compose.onNodeWithText("Chiaro").assertExists()
  }

  @Test
  fun `la scritta dello scrubber si tocca e lo dice`() {
    var showRemaining by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidScrubber(
          position = 0.25f,
          onSeek = {},
          endLabel = { position, range -> if (showRemaining) "-${(range.endInclusive - position) * 100}" else "totale" },
          onEndLabelClick = { showRemaining = !showRemaining },
        )
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("totale").performClick()
    compose.waitForIdle()
    assertTrue(showRemaining)
    assertTrue(compose.onAllNodesWithText("totale").fetchSemanticsNodes().isEmpty())
  }

  @Test
  fun `il titolo agganciato si ferma prima delle azioni`() {
    val title = "Un titolo di pagina molto lungo che non ci sta"
    compose.setContent {
      Themed {
        FluidPageChrome(title = title, onBack = {}) {
          FluidBarAction(icon = Icons.Rounded.Search, contentDescription = "Cerca", onClick = {})
          FluidBarAction(icon = Icons.Rounded.Share, contentDescription = "Condividi", onClick = {})
          FluidBarAction(icon = Icons.Rounded.Delete, contentDescription = "Elimina", onClick = {})
        }
      }
    }
    compose.waitForIdle()
    val text = compose.onAllNodesWithText(title, useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot
    val search = compose.onNodeWithContentDescription("Cerca").getUnclippedBoundsInRoot()
    val searchLeftPx = with(compose.density) { search.left.toPx() }
    assertTrue("il titolo finisce a ${text.right}, le azioni cominciano a $searchLeftPx", text.right <= searchLeftPx)
    assertTrue("e finisce con i puntini", layout(title).hasVisualOverflow)
  }

  @Test
  fun `il tasto prende lo stato del menu dal fornitore`() {
    lateinit var morph: FluidMorphMenuState
    compose.setContent {
      Themed {
        morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        CompositionLocalProvider(LocalFluidMorphMenuState provides morph) {
          Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp)) {
              FluidMorphMenuButton(
                actions = { listOf(FluidContextAction(label = "Uno", onClick = {})) },
                onClick = {},
                modifier = Modifier.testTag("tasto"),
                text = "Menu",
                backdrop = backdrop,
                opensOnTap = true,
              )
            }
            FluidMorphMenuHost(state = morph, backdrop = backdrop)
          }
        }
      }
    }
    compose.waitForIdle()
    assertFalse(morph.isOpen)
    compose.onNodeWithTag("tasto").performClick()
    compose.waitForIdle()
    assertTrue(morph.isOpen)
    compose.runOnIdle { morph.dismiss() }
    compose.waitForIdle()
    assertFalse(morph.isOpen)
  }
}
