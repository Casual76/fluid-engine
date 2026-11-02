package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.catalog.CapsulesAndPartsDemo
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidLiveDot
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidPagerDots
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidSelectionMark
import dev.antigravity.fluidengine.ui.fluid.FluidSkeleton
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonList
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonRow
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonText
import dev.antigravity.fluidengine.ui.fluid.FluidSkeletonTile
import dev.antigravity.fluidengine.ui.fluid.FluidStepProgress
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private class CapsuleHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * Le capsule flottanti e i pezzi piccoli della fase 12b dentro un albero Compose vero.
 *
 * Le funzioni pure di `engine-ui` provano i numeri (margini, contatore, pesi dei punti); qui si prova
 * che siano collegati: che un'azione scatti, che una capsula nascosta non lasci niente in
 * composizione, che un tocco sul suo vetro non arrivi alla pagina sotto, che il cerchio di ricerca si
 * apra, che i punti del pager dicano la pagina che il pager ha.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidCapsulesAndPartsTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = CapsuleHaptics()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  // --- Capsula -------------------------------------------------------------------------------------

  @Test
  fun `an action in the capsule fires and plays the click of a button`() {
    var shared = 0
    compose.setContent {
      Themed {
        Box(Modifier.fillMaxSize()) {
          FluidFloatingCapsule(
            modifier = Modifier.align(Alignment.BottomCenter),
            backdrop = rememberGlassBackdrop(),
          ) {
            FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = { shared++ })
            FluidCapsuleAction(Icons.Rounded.Delete, "Elimina", onClick = {}, destructive = true)
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Condividi").performClick()
    compose.waitForIdle()

    assertEquals(1, shared)
    assertTrue(FluidHapticEvent.PressClick in haptics.played)
  }

  @Test
  fun `a disabled action does not fire and says it is disabled`() {
    var fired = 0
    compose.setContent {
      Themed {
        FluidFloatingCapsule(backdrop = rememberGlassBackdrop()) {
          FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = { fired++ }, enabled = false)
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Condividi").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Condividi").performClick()
    compose.waitForIdle()
    assertEquals(0, fired)
  }

  @Test
  fun `a hidden capsule leaves nothing in composition and comes and goes with visible`() {
    var visible by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidFloatingCapsule(visible = visible, backdrop = rememberGlassBackdrop()) {
          FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = {})
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Condividi").assertDoesNotExist()

    visible = true
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Condividi").assertExists()

    visible = false
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Condividi").assertDoesNotExist()
  }

  @Test
  fun `the capsule holds the taps its actions did not take`() {
    var page = 0
    compose.setContent {
      Themed {
        Box(Modifier.fillMaxSize()) {
          Box(
            Modifier
              .fillMaxSize()
              .clickable { page++ }
              .testTag("page"),
          )
          FluidFloatingCapsule(
            modifier = Modifier.align(Alignment.BottomCenter).testTag("capsule"),
            backdrop = rememberGlassBackdrop(),
          ) {
            FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = {})
          }
        }
      }
    }
    compose.waitForIdle()

    // A un pixel e mezzo dal bordo: dentro il vetro, sotto nessuna azione.
    compose.onNodeWithTag("capsule").performTouchInput { click(Offset(3f, centerY)) }
    compose.waitForIdle()
    assertEquals("un tocco sul vetro non deve arrivare alla pagina", 0, page)

    compose.onNodeWithTag("page").performTouchInput { click(Offset(40f, 40f)) }
    compose.waitForIdle()
    assertEquals(1, page)
  }

  // --- Ricerca -------------------------------------------------------------------------------------

  @Test
  fun `the search circle opens on a tap and says it opens with the haptic of opening`() {
    var expanded by mutableStateOf(false)
    compose.setContent {
      Themed {
        FluidSearchField(
          query = "",
          onQueryChange = {},
          backdrop = rememberGlassBackdrop(),
          expanded = expanded,
          onExpandedChange = { expanded = it },
          autoFocus = false,
        )
      }
    }
    compose.waitForIdle()
    // Chiuso: un cerchio con la lente, nessun campo.
    compose.onNode(hasSetTextAction()).assertDoesNotExist()

    compose.onNodeWithContentDescription("Cerca").performClick()
    compose.waitForIdle()

    assertTrue("il tocco deve aprire il campo", expanded)
    assertTrue(FluidHapticEvent.Open in haptics.played)
    assertFalse("aprire non e' un click da tasto", FluidHapticEvent.PressClick in haptics.played)
    compose.onNode(hasSetTextAction()).assertExists()
    compose.onNodeWithContentDescription("Chiudi la ricerca").assertExists()
  }

  @Test
  fun `the open field types, clears and closes`() {
    var query by mutableStateOf("")
    var expanded by mutableStateOf(true)
    compose.setContent {
      Themed {
        FluidSearchField(
          query = query,
          onQueryChange = { query = it },
          backdrop = rememberGlassBackdrop(),
          expanded = expanded,
          onExpandedChange = { expanded = it },
          autoFocus = false,
        )
      }
    }
    compose.waitForIdle()
    // Con la parola vuota non c'e' niente da cancellare.
    compose.onNodeWithContentDescription("Cancella").assertDoesNotExist()

    compose.onNode(hasSetTextAction()).performTextInput("ciao")
    compose.waitForIdle()
    assertEquals("ciao", query)
    compose.onNodeWithContentDescription("Cancella").assertExists()

    compose.onNodeWithContentDescription("Cancella").performClick()
    compose.waitForIdle()
    assertEquals("", query)

    compose.onNodeWithContentDescription("Chiudi la ricerca").performClick()
    compose.waitForIdle()
    assertFalse(expanded)
    compose.onNode(hasSetTextAction()).assertDoesNotExist()
  }

  @Test
  fun `an open field takes the focus on arrival`() {
    compose.setContent {
      Themed {
        FluidSearchField(query = "", onQueryChange = {}, backdrop = rememberGlassBackdrop())
      }
    }
    compose.waitForIdle()

    compose.onNode(hasSetTextAction()).assertIsFocused()
  }

  @Test
  fun `the field without a way to close is always open whatever expanded says`() {
    compose.setContent {
      Themed {
        FluidSearchField(
          query = "",
          onQueryChange = {},
          backdrop = rememberGlassBackdrop(),
          expanded = false,
          autoFocus = false,
        )
      }
    }
    compose.waitForIdle()

    compose.onNode(hasSetTextAction()).assertExists()
    compose.onNodeWithContentDescription("Chiudi la ricerca").assertDoesNotExist()
  }

  @Test
  fun `the in page search shows the counter and steps through the results`() {
    var next = 0
    var previous = 0
    compose.setContent {
      Themed {
        FluidSearchField(
          query = "storia",
          onQueryChange = {},
          backdrop = rememberGlassBackdrop(),
          autoFocus = false,
          navigation = FluidSearchNavigation(
            current = 2,
            total = 12,
            onPrevious = { previous++ },
            onNext = { next++ },
          ),
        )
      }
    }
    compose.waitForIdle()

    // «3/12» a chi vede, «3 di 12» a chi ascolta.
    compose.onNodeWithText("3/12").assertExists()
    compose.onNodeWithContentDescription("3 di 12").assertExists()

    compose.onNodeWithContentDescription("Risultato successivo").performClick()
    compose.onNodeWithContentDescription("Risultato successivo").performClick()
    compose.onNodeWithContentDescription("Risultato precedente").performClick()
    compose.waitForIdle()
    assertEquals(2, next)
    assertEquals(1, previous)
  }

  @Test
  fun `a word that finds nothing says so and switches the arrows off`() {
    compose.setContent {
      Themed {
        FluidSearchField(
          query = "xyz",
          onQueryChange = {},
          backdrop = rememberGlassBackdrop(),
          autoFocus = false,
          navigation = FluidSearchNavigation(current = 0, total = 0, onPrevious = {}, onNext = {}),
        )
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Nessun risultato").assertExists()
    compose.onNodeWithContentDescription("Risultato successivo").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Risultato precedente").assertIsNotEnabled()
  }

  // --- Pezzi piccoli ------------------------------------------------------------------------------

  @Test
  fun `the selection mark tells the state and can be silenced`() {
    var selected by mutableStateOf(false)
    compose.setContent {
      Themed {
        Column {
          FluidSelectionMark(selected = selected)
          // Muto: quando e' la riga a dire lo stato, dirlo due volte e' rumore.
          FluidSelectionMark(selected = selected, contentDescription = null)
        }
      }
    }
    compose.waitForIdle()
    assertEquals(1, compose.onAllNodesWithContentDescription("Non selezionato").fetchSemanticsNodes().size)

    selected = true
    compose.waitForIdle()
    assertEquals(1, compose.onAllNodesWithContentDescription("Selezionato").fetchSemanticsNodes().size)
    compose.onNodeWithContentDescription("Non selezionato").assertDoesNotExist()
  }

  @Test
  fun `the pager dots say the page the pager is on`() {
    lateinit var scope: CoroutineScope
    lateinit var pager: androidx.compose.foundation.pager.PagerState
    compose.setContent {
      Themed {
        scope = rememberCoroutineScope()
        pager = rememberPagerState(pageCount = { 5 })
        Column {
          // Il pager c'e' davvero: `currentPage` cambia solo quando qualcuno lo misura.
          HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(80.dp)) { page ->
            Box(Modifier.fillMaxSize()) { Text("Pagina $page") }
          }
          FluidPagerDots(state = pager)
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Pagina 1 di 5").assertExists()

    compose.runOnIdle { scope.launch { pager.scrollToPage(2) } }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Pagina 3 di 5").assertExists()
  }

  @Test
  fun `a single page has no dots to show`() {
    compose.setContent {
      Themed {
        FluidPagerDots(pageCount = 1, position = { 0f })
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Pagina 1 di 1").assertDoesNotExist()
  }

  @Test
  fun `the step header is one block that says where it is, in both shapes`() {
    var step by mutableIntStateOf(2)
    compose.setContent {
      Themed {
        Column {
          FluidStepProgress(step = step, steps = 5, modifier = Modifier.testTag("bar"))
          FluidStepProgress(step = step, steps = 5, segmented = true, pulseCurrent = true, modifier = Modifier.testTag("segments"))
        }
      }
    }
    compose.waitForIdle()

    assertEquals(2, compose.onAllNodesWithContentDescription("Passo 2 di 5").fetchSemanticsNodes().size)
    val range = compose.onNodeWithTag("bar").fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
    assertEquals(2f, range.current, 0f)
    assertEquals(1f..5f, range.range)

    step = 3
    compose.waitForIdle()
    assertEquals(2, compose.onAllNodesWithContentDescription("Passo 3 di 5").fetchSemanticsNodes().size)
  }

  @Test
  fun `the step label can be written in another language`() {
    compose.setContent {
      Themed { FluidStepProgress(step = 1, steps = 3, label = "Step 1 of 3", detail = "Converting") }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Step 1 of 3").assertExists()
    compose.onNodeWithText("Converting").assertExists()
  }

  @Test
  fun `the live dot is silent unless it is given a reason`() {
    compose.setContent {
      Themed {
        Column {
          FluidLiveDot(contentDescription = "In diretta")
          FluidLiveDot()
        }
      }
    }
    compose.waitForIdle()

    assertEquals(1, compose.onAllNodesWithContentDescription("In diretta").fetchSemanticsNodes().size)
  }

  @Test
  fun `a skeleton list names itself once and the rows stay mute`() {
    compose.setContent {
      Themed {
        Column {
          FluidSkeletonList(rows = 4, trailing = true, label = "Caricamento delle partenze")
          FluidSkeletonText(lines = 3)
          FluidSkeletonRow()
          FluidSkeletonTile(modifier = Modifier.padding(8.dp))
          FluidSkeleton(modifier = Modifier.fillMaxWidth())
        }
      }
    }
    compose.waitForIdle()

    assertEquals(1, compose.onAllNodesWithContentDescription("Caricamento delle partenze").fetchSemanticsNodes().size)
  }

  @Test
  fun `with animations reduced the breathing parts compose and settle`() {
    // Con la scala di animazione a zero niente deve girare: se un respiro fosse rimasto acceso il
    // test non arriverebbe mai a riposo, perche' un'animazione di scheletro dentro un gruppo e' infinita.
    compose.setContent {
      Themed {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = 0f)) {
          Column {
            FluidLiveDot(contentDescription = "In diretta")
            FluidSkeletonList(rows = 2)
            FluidStepProgress(step = 2, steps = 5, segmented = true, pulseCurrent = true)
            FluidSelectionMark(selected = true)
            FluidFloatingCapsule(backdrop = rememberGlassBackdrop()) {
              FluidCapsuleAction(Icons.Rounded.Share, "Condividi", onClick = {})
            }
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("In diretta").assertExists()
    compose.onNodeWithContentDescription("Selezionato").assertExists()
    compose.onNodeWithContentDescription("Condividi").assertExists()
  }

  // --- La demo -------------------------------------------------------------------------------------

  @Test
  fun `the demo composes whole, and its controls move the parts`() {
    compose.setContent {
      Themed {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
          CapsulesAndPartsDemo()
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("capsules-and-parts-demo").assertExists()
    // La barra di selezione, il lettore e le due ricerche ci sono.
    compose.onNodeWithContentDescription("Condividi").assertExists()
    compose.onNodeWithContentDescription("Riproduci").assertExists()
    compose.onNodeWithText("3/12").assertExists()

    // Il segno di selezione cambia con il tocco sulla foto.
    assertEquals(1, compose.onAllNodesWithContentDescription("Selezionato").fetchSemanticsNodes().size)
    compose.onNodeWithTag("demo-photo-2").performScrollTo().performClick()
    compose.waitForIdle()
    assertEquals(2, compose.onAllNodesWithContentDescription("Selezionato").fetchSemanticsNodes().size)

    // Il passo avanza in tutte e due le intestazioni.
    compose.onNodeWithText("Avanti").performScrollTo().performClick()
    compose.waitForIdle()
    assertEquals(2, compose.onAllNodesWithContentDescription("Passo 3 di 5").fetchSemanticsNodes().size)

    // «Nascondi la barra» toglie la capsula di selezione dalla composizione.
    compose.onNodeWithText("Nascondi la barra").performScrollTo().performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Condividi").assertDoesNotExist()
  }
}
