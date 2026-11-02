package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private class MatrixHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * Le prove con cui la matrice della 3.0 si chiude (fase 10), dentro un albero Compose vero.
 *
 * Tre cose che un test puro non vede: la **semantica di una riga** (un nodo solo per TalkBack, con nome,
 * ruolo e stato: il `ListItem` fonde la sua e il `clickable` fonde tutto), il **segmentato da destra a
 * sinistra** (il layout si specchia da solo, la goccia e il dito no) e il **primo evento dell'indice di
 * sezione** (una pressione lunga che non si muove non e' una tacca attraversata).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidMatrixTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = MatrixHaptics()

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

  @Composable
  private fun OnCanvas(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides rememberGlassBackdrop(), content = content)
  }

  // --- La riga: un nodo solo --------------------------------------------------------------------------

  private val noRole = SemanticsMatcher.keyNotDefined(SemanticsProperties.Role)
  private val hasRole = SemanticsMatcher.keyIsDefined(SemanticsProperties.Role)
  private val hasSelectedState = SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)

  @Test
  fun `a row that opens something is one node for a screen reader, with its name, its role and its state`() {
    var clicks = 0
    compose.setContent {
      Themed {
        FluidListRow(
          title = "Compito di matematica",
          subtitle = "Per domani",
          onClick = { clicks++ },
          selected = true,
          modifier = Modifier.testTag("row"),
        )
      }
    }
    compose.waitForIdle()

    // Un solo nodo che scatta, sia nell'albero fuso (quello che legge TalkBack) sia in quello intero.
    compose.onAllNodes(hasClickAction()).assertCountEquals(1)
    compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)

    // Ha il nome (il testo della riga, fuso), il ruolo e lo stato.
    val row = compose.onNodeWithTag("row")
    row.assert(hasText("Compito di matematica"))
    row.assert(hasText("Per domani"))
    row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    row.assertIsSelected()

    // E non resta nessun secondo nodo con un ruolo o con uno stato, senza nome, accanto a lui.
    compose.onAllNodes(hasRole, useUnmergedTree = true).assertCountEquals(1)
    compose.onAllNodes(hasSelectedState, useUnmergedTree = true).assertCountEquals(1)

    row.performClick()
    compose.waitForIdle()
    assertEquals(1, clicks)
  }

  @Test
  fun `a row with nothing to open is a label, not a button`() {
    compose.setContent {
      Themed {
        FluidListRow(title = "Versione", subtitle = "3.0.0", modifier = Modifier.testTag("row"))
      }
    }
    compose.waitForIdle()

    compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    compose.onNodeWithTag("row").assert(hasText("Versione")).assert(noRole)
    compose.onAllNodes(hasRole, useUnmergedTree = true).assertCountEquals(0)
  }

  @Test
  fun `a row that opens something and is not the chosen one says so only by what it is`() {
    compose.setContent {
      Themed {
        FluidListRow(title = "Voti", subtitle = "Secondo quadrimestre", onClick = {}, modifier = Modifier.testTag("row"))
      }
    }
    compose.waitForIdle()

    compose.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    compose.onAllNodes(hasSelectedState, useUnmergedTree = true).assertCountEquals(0)
    compose.onNodeWithTag("row").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  // --- Il segmentato da destra a sinistra ---------------------------------------------------------------

  private val segments = listOf("Giorno", "Settimana", "Mese")

  @Composable
  private fun Segmented(selected: String, onSelect: (String) -> Unit) {
    FluidSegmentedControl(
      options = segments,
      selected = selected,
      onSelect = onSelect,
      modifier = Modifier.testTag("segmented"),
      label = { it },
    )
  }

  @Test
  fun `from right to left a tap on a segment selects it`() {
    var selected by mutableStateOf("Giorno")
    compose.setContent {
      Themed {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          OnCanvas { Segmented(selected) { selected = it } }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Mese").performClick()
    compose.waitForIdle()

    assertEquals("Mese", selected)
    compose.onNodeWithText("Mese").assertIsSelected()
  }

  @Test
  fun `from right to left the drop starts on the right and dragging it toward the left reaches the last segment`() {
    var selected by mutableStateOf("Giorno")
    compose.setContent {
      Themed {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          OnCanvas { Segmented(selected) { selected = it } }
        }
      }
    }
    compose.waitForIdle()

    // Il primo segmento sta a destra, e li' sta la goccia: 1,8 segmenti verso sinistra in tre passi,
    // l'arrotondamento passa da 0 a 1 a 2 — e il dito e' sempre sopra la goccia.
    compose.onNodeWithTag("segmented").performTouchInput {
      val segment = width / 3f
      down(Offset(width - segment / 2f, height / 2f))
      moveBy(Offset(-segment * 0.6f, 0f))
      moveBy(Offset(-segment * 0.6f, 0f))
      moveBy(Offset(-segment * 0.6f, 0f))
      up()
    }
    compose.waitForIdle()

    assertEquals("Mese", selected)
    assertEquals(
      "un tick per segmento attraversato: ${haptics.played}",
      2,
      haptics.played.count { it == FluidHapticEvent.Tick },
    )
  }

  @Test
  fun `from right to left dragging toward the right from the first segment goes nowhere`() {
    var selected by mutableStateOf("Giorno")
    compose.setContent {
      Themed {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
          OnCanvas { Segmented(selected) { selected = it } }
        }
      }
    }
    compose.waitForIdle()

    // Il primo segmento e' gia' all'estremita' destra: trascinare ancora a destra non c'e' dove andare.
    compose.onNodeWithTag("segmented").performTouchInput {
      val segment = width / 3f
      down(Offset(width - segment / 2f, height / 2f))
      moveBy(Offset(segment * 0.3f, 0f))
      moveBy(Offset(segment * 0.3f, 0f))
      up()
    }
    compose.waitForIdle()

    assertEquals("Giorno", selected)
    assertEquals(0, haptics.played.count { it == FluidHapticEvent.Tick })
  }

  // --- L'indice di sezione: il primo evento -------------------------------------------------------------

  private val sections = listOf("Gen", "Feb", "Mar", "Apr").mapIndexed { index, label ->
    FluidSectionAnchor(key = label, label = label, itemIndex = index)
  }

  private val strip = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

  private fun mountIndex(picked: MutableList<String>) {
    compose.setContent {
      Themed {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidSectionIndex(
            sections = sections,
            activeSectionKey = "Feb",
            onSelectSection = { section, _ -> picked += section.key },
            backdrop = backdrop,
          )
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `holding the index without moving plays the start of a gesture and no tick, and selects nothing`() {
    val picked = mutableListOf<String>()
    mountIndex(picked)

    compose.onNode(strip).performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    compose.onNode(strip).performTouchInput { up() }
    compose.waitForIdle()

    assertEquals(1, haptics.played.count { it == FluidHapticEvent.GestureStart })
    assertEquals(
      "il primo evento a dito fermo non e' una tacca attraversata: ${haptics.played}",
      0,
      haptics.played.count { it == FluidHapticEvent.Tick },
    )
    assertTrue("la sezione attiva non va riselezionata: $picked", picked.isEmpty())
  }

  @Test
  fun `moving the held finger across the marks plays one tick and selects where it went`() {
    val picked = mutableListOf<String>()
    mountIndex(picked)

    compose.onNode(strip).performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    // Molto oltre la fine del nastro: la frazione si ferma a 1, cioe' l'ultima sezione, in un evento solo.
    compose.onNode(strip).performTouchInput { moveBy(Offset(0f, 2_000f)) }
    compose.waitForIdle()
    compose.onNode(strip).performTouchInput { up() }
    compose.waitForIdle()

    assertEquals(listOf("Apr"), picked)
    assertEquals(
      "un tick per la tacca raggiunta: ${haptics.played}",
      1,
      haptics.played.count { it == FluidHapticEvent.Tick },
    )
  }
}
