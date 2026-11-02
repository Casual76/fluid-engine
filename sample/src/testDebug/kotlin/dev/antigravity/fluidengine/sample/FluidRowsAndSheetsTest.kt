package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmSheet
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidPromptAlert
import dev.antigravity.fluidengine.ui.fluid.FluidPromptValidation
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class RowsHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * Le righe, i fogli e gli avvisi della fase 12b dentro un albero Compose vero.
 *
 * Quello che qui si prova e' cio' che le funzioni pure non possono dire: che **un tocco commuti una
 * volta sola** anche quando cade sull'interruttore (era il difetto dei cinque modi di scriverla),
 * che una riga di scelta sia un radio per TalkBack, che l'avviso con un campo si apra sul campo e
 * confermi con la tastiera, e che i tasti di un foglio restino in vista sotto un corpo troppo lungo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidRowsAndSheetsTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = RowsHaptics()

  @Before
  fun reset() {
    haptics.played.clear()
  }

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  // ---------------------------------------------------------------------------------------------
  // FluidToggleRow
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `un tocco sulla riga commuta una volta e vibra come l'interruttore`() {
    var checked by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidListGroup {
          FluidToggleRow(
            title = "Notifiche",
            subtitle = "Un avviso quando cambia qualcosa.",
            checked = checked,
            onCheckedChange = { checked = it },
          )
        }
      }
    }
    compose.onNode(isToggleable()).assertIsOff().performClick()
    compose.onNode(isToggleable()).assertIsOn()
    assertTrue(checked)
    // Una vibrazione sola, quella dell'interruttore: niente `Tap` della riga sotto.
    assertEquals(listOf(FluidHapticEvent.ToggleOn), haptics.played)

    compose.onNode(isToggleable()).performClick()
    compose.onNode(isToggleable()).assertIsOff()
    assertEquals(listOf(FluidHapticEvent.ToggleOn, FluidHapticEvent.ToggleOff), haptics.played)
  }

  @Test
  fun `un tocco sull'interruttore stesso commuta una volta sola`() {
    var changes = 0
    var checked by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidToggleRow(
          title = "Notifiche",
          checked = checked,
          onCheckedChange = {
            changes++
            checked = it
          },
        )
      }
    }
    // L'estremo destro della riga e' dove sta il pomello: la riga e' il bersaglio, non lui.
    compose.onNode(isToggleable()).performTouchInput { click(Offset(width - 60f, height / 2f)) }
    compose.waitForIdle()
    assertEquals(1, changes)
    assertTrue(checked)
  }

  @Test
  fun `la riga e' un interruttore per chi legge lo schermo, con titolo e sottotitolo come nome`() {
    compose.setContent {
      Themed {
        FluidToggleRow(title = "Notifiche", subtitle = "Un avviso", checked = true, onCheckedChange = {})
      }
    }
    compose.onNode(isToggleable()).assertIsOn()
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsDisplayed()
    // Un nodo solo: il testo e' il titolo, la descrizione e' quello che TalkBack legge.
    compose.onNodeWithText("Notifiche").assertIsDisplayed()
    compose.onNodeWithContentDescription("Notifiche, Un avviso").assertIsDisplayed()
    compose.onAllNodes(isToggleable()).assertCountEquals(1)
  }

  @Test
  fun `una riga disattivata non risponde e non vibra`() {
    var checked by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidToggleRow(title = "Notifiche", checked = checked, onCheckedChange = { checked = it }, enabled = false)
      }
    }
    compose.onNode(isToggleable()).assertIsNotEnabled().performClick()
    compose.onNode(isToggleable()).assertIsOff()
    assertTrue(haptics.played.isEmpty())
  }

  // ---------------------------------------------------------------------------------------------
  // FluidChoiceRow
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `una riga di scelta e' un radio, e scegliere un'altra sposta la selezione con un Tick`() {
    var selected by mutableStateOf("Data")
    var taps = 0
    compose.setContent {
      Themed {
        FluidListGroup(modifier = Modifier.selectableGroup()) {
          listOf("Nome", "Data", "Dimensione").forEachIndexed { index, option ->
            if (index > 0) FluidListDivider()
            FluidChoiceRow(
              title = option,
              selected = selected == option,
              onSelect = {
                taps++
                selected = option
              },
            )
          }
        }
      }
    }
    // Tre righe, tre radio: uno per riga, e non due nodi per riga (la semantica del `ListItem` e' azzerata).
    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton) and isSelectable())
      .assertCountEquals(3)
    compose.onNodeWithText("Data").assertIsSelected()
    compose.onNodeWithText("Nome").assertIsNotSelected()

    compose.onNodeWithText("Dimensione").performClick()
    compose.onNodeWithText("Dimensione").assertIsSelected()
    compose.onNodeWithText("Data").assertIsNotSelected()
    assertEquals(listOf(FluidHapticEvent.Tick), haptics.played)
    assertEquals(1, taps)
  }

  @Test
  fun `toccare la riga gia' scelta non vibra ma avvisa comunque`() {
    var taps = 0
    compose.setContent {
      Themed {
        FluidChoiceRow(title = "Data", selected = true, onSelect = { taps++ })
      }
    }
    compose.onNodeWithText("Data").performClick()
    assertEquals(1, taps)
    assertTrue("niente e' cambiato, niente vibra", haptics.played.isEmpty())
  }

  @Test
  fun `una riga di scelta disattivata non risponde`() {
    var taps = 0
    compose.setContent {
      Themed {
        FluidChoiceRow(title = "Data", selected = false, onSelect = { taps++ }, enabled = false)
      }
    }
    compose.onNodeWithText("Data").assertIsNotEnabled().performClick()
    assertEquals(0, taps)
  }

  // ---------------------------------------------------------------------------------------------
  // FluidPromptAlert
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `l'avviso con un campo conferma solo con qualcosa di scritto, e consegna il testo ritagliato`() {
    var result: String? = null
    compose.setContent {
      Themed {
        FluidPromptAlert(
          title = "Rinomina",
          onDismissRequest = {},
          onConfirm = { result = it },
        )
      }
    }
    compose.onNodeWithText("Salva").assertIsNotEnabled()
    compose.onNode(hasSetTextAction()).performTextInput("   ")
    compose.onNodeWithText("Salva").assertIsNotEnabled()

    compose.onNode(hasSetTextAction()).performTextInput("  Cucina ")
    compose.onNodeWithText("Salva").assertIsEnabled().performClick()
    assertEquals("Cucina", result)
  }

  @Test
  fun `l'avviso si apre sul campo, con il testo iniziale tutto selezionato`() {
    compose.setContent {
      Themed {
        FluidPromptAlert(
          title = "Rinomina",
          initialValue = "Cucina",
          onDismissRequest = {},
          onConfirm = {},
        )
      }
    }
    compose.waitForIdle()
    compose.onNode(hasSetTextAction()).assertIsFocused()
    compose.onNode(
      SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 6)),
    ).assertExists()
  }

  @Test
  fun `il tasto invio della tastiera conferma, se la validazione lo permette`() {
    var result: String? = null
    compose.setContent {
      Themed {
        FluidPromptAlert(
          title = "Rinomina",
          onDismissRequest = {},
          onConfirm = { result = it },
        )
      }
    }
    // Vuoto: l'azione della tastiera non fa niente.
    compose.onNode(hasSetTextAction()).performImeAction()
    assertNull(result)

    compose.onNode(hasSetTextAction()).performTextInput("Sala")
    compose.onNode(hasSetTextAction()).performImeAction()
    assertEquals("Sala", result)
  }

  @Test
  fun `un valore fuori misura mostra la sua frase e tiene spento il tasto`() {
    var result: String? = null
    compose.setContent {
      Themed {
        FluidPromptAlert(
          title = "Taratura",
          confirmLabel = "Calibra",
          validate = { text ->
            val value = text.replace(',', '.').toDoubleOrNull()
            when {
              text.isBlank() -> FluidPromptValidation.Incomplete
              value == null || value !in 20.0..140.0 -> FluidPromptValidation.Invalid("Un valore tra 20 e 140.")
              else -> FluidPromptValidation.Valid
            }
          },
          onDismissRequest = {},
          onConfirm = { result = it },
        )
      }
    }
    // Vuoto non e' un errore: il campo tace.
    compose.onNodeWithText("Un valore tra 20 e 140.").assertDoesNotExist()

    compose.onNode(hasSetTextAction()).performTextInput("200")
    compose.onNodeWithText("Un valore tra 20 e 140.").assertIsDisplayed()
    compose.onNodeWithText("Calibra").assertIsNotEnabled()
    compose.onNode(hasSetTextAction()).performImeAction()
    assertNull(result)

    compose.onNode(hasSetTextAction()).performTextInput("0")
    compose.onNodeWithText("Calibra").assertIsNotEnabled()
  }

  @Test
  fun `annulla chiama la chiusura e non la conferma`() {
    var dismissed = 0
    var confirmed = 0
    compose.setContent {
      Themed {
        FluidPromptAlert(
          title = "Rinomina",
          initialValue = "Cucina",
          onDismissRequest = { dismissed++ },
          onConfirm = { confirmed++ },
        )
      }
    }
    compose.onNodeWithText("Annulla").performClick()
    assertEquals(1, dismissed)
    assertEquals(0, confirmed)
  }

  // ---------------------------------------------------------------------------------------------
  // FluidSheetScaffold
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `i tasti restano in vista sotto un corpo piu' lungo del foglio, e il corpo scorre`() {
    compose.setContent {
      Themed {
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.height(320.dp)) {
            FluidSheetScaffold(
              title = "Eliminare?",
              insetBottom = false,
              actions = { FluidButton(text = "Conferma", onClick = {}, fillWidth = true) },
            ) {
              repeat(40) { Text("Riga $it") }
            }
          }
        }
      }
    }
    compose.onNodeWithText("Eliminare?").assertIsDisplayed()
    compose.onNodeWithText("Conferma").assertIsDisplayed()
    compose.onNodeWithText("Riga 0").assertIsDisplayed()
    compose.onNodeWithText("Riga 39").assertIsNotDisplayed()
  }

  @Test
  fun `senza un'altezza massima il corpo non sparisce`() {
    // Il caso in cui un `weight` darebbe al corpo altezza zero: un foglio dentro uno scorrimento.
    compose.setContent {
      Themed {
        Column(Modifier.verticalScroll(rememberScrollState())) {
          FluidSheetScaffold(
            title = "Titolo",
            insetBottom = false,
            actions = { FluidButton(text = "Conferma", onClick = {}, fillWidth = true) },
          ) {
            Text("Il corpo")
          }
        }
      }
    }
    compose.onNodeWithText("Il corpo").assertIsDisplayed()
    compose.onNodeWithText("Conferma").assertIsDisplayed()
  }

  @Test
  fun `il contenuto si ferma alla larghezza massima, al netto del margine`() {
    compose.setContent {
      Themed {
        FluidSheetScaffold(
          title = "Titolo",
          maxWidth = 200.dp,
          insetBottom = false,
          modifier = Modifier.testTag("sheet"),
        )
      }
    }
    // 200 dp di tetto meno i 20 dp di margine per lato.
    compose.onNodeWithText("Titolo").assertWidthIsEqualTo(160.dp)
  }

  @Test
  fun `la testata porta la piastrella dell'icona quando c'e'`() {
    compose.setContent {
      Themed {
        FluidSheetScaffold(
          title = "Titolo",
          icon = { Icon(Icons.Rounded.Delete, contentDescription = "cestino") },
          insetBottom = false,
        )
      }
    }
    compose.onNodeWithContentDescription("cestino").assertIsDisplayed()
    compose.onNodeWithText("Titolo").assertIsDisplayed()
  }

  // ---------------------------------------------------------------------------------------------
  // FluidConfirmSheet
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `il foglio di conferma si apre nell'host, conferma e annulla sono due cose`() {
    var open by mutableStateOf(false)
    var confirmed = 0
    var dismissed = 0
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidConfirmSheet(
              visible = open,
              onDismissRequest = {
                dismissed++
                open = false
              },
              title = "Eliminare 12 elementi?",
              message = "Non si puo' annullare.",
              destructive = true,
              confirmLabel = "Elimina dal telefono",
              onConfirm = {
                confirmed++
                open = false
              },
            )
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.onNodeWithText("Eliminare 12 elementi?").assertDoesNotExist()

    compose.runOnIdle { open = true }
    compose.waitForIdle()
    compose.onNodeWithText("Eliminare 12 elementi?").assertIsDisplayed()
    compose.onNodeWithText("Non si puo' annullare.").assertIsDisplayed()

    compose.onNodeWithText("Elimina dal telefono").performClick()
    assertEquals(1, confirmed)
    assertEquals("la conferma non e' un annulla", 0, dismissed)
  }
}
