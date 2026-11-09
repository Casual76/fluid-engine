package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroll
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidImageViewer
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidScrubber
import dev.antigravity.fluidengine.ui.fluid.FluidScrubbing
import dev.antigravity.fluidengine.ui.fluid.FluidReorderState
import dev.antigravity.fluidengine.ui.fluid.FluidUpdate
import dev.antigravity.fluidengine.ui.fluid.FluidUpdatePrompt
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateRelease
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateStage
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateChannelStyle
import dev.antigravity.fluidengine.ui.fluid.FluidUpdateUiState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidMoved
import dev.antigravity.fluidengine.ui.fluid.fluidReorderable
import dev.antigravity.fluidengine.ui.fluid.fluidUpdateSection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFastScrollTarget
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidReorderState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private class MoreHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * I pezzi generici della fase 12c dentro un albero Compose vero: l'aggiornamento (l'avviso e le righe), il
 * riordino, lo scorrimento rapido, lo scrubber e il visualizzatore.
 *
 * I conti stanno nei test puri di `engine-ui`; qui si prova che siano collegati: che il tasto giusto
 * compaia nello stato giusto e chiami la cosa giusta, che un tenuto premuto sollevi e un trascinamento
 * sposti davvero la riga nella lista dell'app, che la maniglia porti la lista dove va il dito e suoni un colpo per
 * etichetta, che lo zoom risponda a un doppio tocco, a un pizzico e alle azioni di TalkBack. Quello che nessun test
 * dice e' com'e' il vetro (gli host dei test non hanno fondale): la goccia dell'elemento sollevato e quella della
 * maniglia si guardano in mano.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MoreComponentsTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = MoreHaptics()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  private val release = FluidUpdateRelease("2.1.0", "**Novita'**\n- il vetro nuovo", 12L * 1024 * 1024)

  // ===============================================================================================
  // L'avviso di aggiornamento
  // ===============================================================================================

  private var installs = 0
  private var laters = 0
  private var skips = 0

  private fun showPrompt(state: FluidUpdateUiState, withSkip: Boolean = true) {
    compose.setContent {
      Themed {
        FluidUpdatePrompt(
          state = state,
          onInstall = { installs++ },
          onLater = { laters++ },
          onSkip = if (withSkip) ({ skips++ }) else null,
          insetBottom = false,
        )
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `a new version offers three answers and each one calls its own callback`() {
    showPrompt(FluidUpdateUiState.Available(release))

    compose.onNodeWithText("Aggiornamento 2.1.0").assertIsDisplayed()
    compose.onNodeWithText("C'è una nuova versione da installare (${FluidUpdate.sizeLabel(release.sizeBytes)}).").assertIsDisplayed()
    // Le note, ripulite dal Markdown, sotto «Novità».
    compose.onNodeWithText("Novità").assertIsDisplayed()
    compose.onNodeWithText("Novita'\n• il vetro nuovo").assertIsDisplayed()

    compose.onNodeWithText("Aggiorna").performClick()
    compose.onNodeWithText("Più tardi").performClick()
    compose.onNodeWithText("Salta questa versione").performClick()

    assertEquals(1, installs)
    assertEquals(1, laters)
    assertEquals(1, skips)
  }

  @Test
  fun `without a skip callback there is no skip button`() {
    showPrompt(FluidUpdateUiState.Available(release), withSkip = false)
    compose.onAllNodesWithText("Salta questa versione").assertCountEquals(0)
  }

  @Test
  fun `a download has no install button, says how far it is, and only offers later`() {
    val state = FluidUpdateUiState.Downloading(release, 0.4f, downloadedBytes = 5L * 1024 * 1024, totalBytes = 12L * 1024 * 1024)
    showPrompt(state)

    compose.onNodeWithText(FluidUpdate.progressText(state)!!).assertIsDisplayed()
    compose.onAllNodesWithText("Aggiorna").assertCountEquals(0)
    compose.onNodeWithText("Più tardi").assertIsDisplayed()
    // L'avviso non e' una gabbia: fermarsi e' una risposta.
    compose.onNodeWithText("Più tardi").performClick()
    assertEquals(1, laters)
    assertEquals(0, installs)
  }

  @Test
  fun `a failure shows what the system said and retrying is the same gesture as installing`() {
    showPrompt(FluidUpdateUiState.Failed(release, "Non c'è abbastanza spazio."))

    compose.onNodeWithText("Aggiornamento non riuscito").assertIsDisplayed()
    compose.onNodeWithText("Non c'è abbastanza spazio.").assertIsDisplayed()
    compose.onNodeWithText("Riprova").performClick()
    assertEquals(1, installs)
    compose.onNodeWithText("Chiudi").performClick()
    assertEquals(1, laters)
    assertTrue("l'esito si sente", FluidHapticEvent.Error in haptics.played)
  }

  @Test
  fun `a finished install says so, closes with one button and is felt`() {
    showPrompt(FluidUpdateUiState.Installed(release))

    compose.onNodeWithText("Aggiornamento installato.").assertIsDisplayed()
    compose.onAllNodesWithText("Aggiorna").assertCountEquals(0)
    compose.onNodeWithText("Chiudi").performClick()
    assertEquals(1, laters)
    assertEquals(listOf(FluidHapticEvent.Success), haptics.played.filter { it == FluidHapticEvent.Success })
  }

  @Test
  fun `a missing permission is explained before the system asks, and the button opens settings`() {
    showPrompt(FluidUpdateUiState.NeedsPermission(release))

    compose.onNodeWithText("Android chiede di consentire a questa app di installare aggiornamenti. Succede una volta sola.")
      .assertIsDisplayed()
    compose.onNodeWithText("Apri impostazioni").performClick()
    assertEquals(1, installs)
  }

  @Test
  fun `waiting for the system answer shows no install button`() {
    showPrompt(FluidUpdateUiState.Working(release, FluidUpdateStage.AwaitingUser))

    compose.onNodeWithText("Conferma l'installazione nella finestra di Android.").assertIsDisplayed()
    compose.onAllNodesWithText("Aggiorna").assertCountEquals(0)
  }

  @Test
  fun `a state with no release draws no prompt`() {
    showPrompt(FluidUpdateUiState.UpToDate)
    compose.onAllNodesWithText("Più tardi").assertCountEquals(0)
    compose.onAllNodesWithText("Chiudi").assertCountEquals(0)
  }

  // ===============================================================================================
  // Le righe degli aggiornamenti
  // ===============================================================================================

  private var checks = 0
  private val channelChanges = mutableListOf<UpdateChannel>()
  private val autoChanges = mutableListOf<Boolean>()

  private fun showSection(
    state: FluidUpdateUiState,
    channel: UpdateChannel? = UpdateChannel.STABLE,
    style: FluidUpdateChannelStyle = FluidUpdateChannelStyle.Choice,
    supported: Boolean = true,
    withSkip: Boolean = true,
  ) {
    compose.setContent {
      Themed {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
          fluidUpdateSection(
            state = state,
            onCheck = { checks++ },
            onInstall = { installs++ },
            currentVersion = "2.0.0",
            lastCheckedAtMillis = 1_000_000L,
            nowMillis = { 1_000_000L + 5 * 60_000L },
            channel = channel,
            onChannelChange = { channelChanges += it },
            channelStyle = style,
            autoDownload = false,
            onAutoDownloadChange = { autoChanges += it },
            onSkip = if (withSkip) ({ skips++ }) else null,
            supported = supported,
          )
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `an up to date app shows the version, the last check, and checks again on a tap`() {
    showSection(FluidUpdateUiState.UpToDate)

    compose.onNodeWithText("Versione installata").assertIsDisplayed()
    compose.onNodeWithText("2.0.0").assertIsDisplayed()
    compose.onNodeWithText("App aggiornata").assertIsDisplayed()
    compose.onNodeWithText("Controllato 5 minuti fa").assertIsDisplayed()
    compose.onNodeWithText("Controlla ora").performClick()
    assertEquals(1, checks)
    assertEquals(0, installs)
  }

  @Test
  fun `with a new version the same row becomes the install row, with the size and a skip row`() {
    showSection(FluidUpdateUiState.Available(release))

    compose.onNodeWithText("Versione 2.1.0 disponibile").assertIsDisplayed()
    compose.onNodeWithText("Nuova").assertIsDisplayed()
    compose.onAllNodesWithText("Controlla ora").assertCountEquals(0)
    compose.onNodeWithText("Installa la 2.1.0").performClick()
    assertEquals(1, installs)
    compose.onNodeWithText("Salta questa versione").performClick()
    assertEquals(1, skips)
  }

  @Test
  fun `while it checks the row says so and a second tap starts nothing`() {
    showSection(FluidUpdateUiState.Checking)

    compose.onNodeWithText("Sto controllando…").assertIsDisplayed()
    compose.onNodeWithText("Controlla ora").performClick()
    assertEquals(0, checks)
  }

  @Test
  fun `a download shows its progress in the group and a tap on the check row starts nothing`() {
    val state = FluidUpdateUiState.Downloading(release, 0.5f, 6L * 1024 * 1024, 12L * 1024 * 1024)
    showSection(state)

    compose.onNodeWithText(FluidUpdate.progressText(state)!!).assertIsDisplayed()
    compose.onNodeWithText("Aggiornamento in corso…").assertIsDisplayed()
    compose.onNodeWithText("Controlla ora").performClick()
    assertEquals("durante un'installazione un tocco non rilancia niente", 0, checks)
  }

  @Test
  fun `a failed install can be retried from the group`() {
    showSection(FluidUpdateUiState.Failed(release, "Disco pieno"))

    compose.onNodeWithText("Disco pieno").assertIsDisplayed()
    compose.onNodeWithText("Riprova").performClick()
    assertEquals(1, installs)
  }

  @Test
  fun `a failed check says so and offers to check again`() {
    showSection(FluidUpdateUiState.CheckFailed())

    compose.onNodeWithText("Controllo non riuscito").assertIsDisplayed()
    compose.onNodeWithText("Controlla ora").performClick()
    assertEquals(1, checks)
  }

  @Test
  fun `the channel is two choice rows, and the one already chosen reports nothing`() {
    showSection(FluidUpdateUiState.UpToDate, channel = UpdateChannel.STABLE)

    compose.onNodeWithText("Stabile").performClick()
    assertTrue("toccare il canale gia' scelto non e' un cambio", channelChanges.isEmpty())
    compose.onNodeWithText("Beta").performClick()
    assertEquals(listOf(UpdateChannel.BETA), channelChanges)
  }

  @Test
  fun `the channel can be one switch for the betas`() {
    showSection(FluidUpdateUiState.UpToDate, style = FluidUpdateChannelStyle.BetaToggle)

    compose.onAllNodesWithText("Stabile").assertCountEquals(0)
    compose.onNodeWithText("Versioni di prova").performClick()
    assertEquals(listOf(UpdateChannel.BETA), channelChanges)
  }

  @Test
  fun `the channel rows are absent when the app has no channel`() {
    showSection(FluidUpdateUiState.UpToDate, channel = null)
    compose.onAllNodesWithText("Beta").assertCountEquals(0)
    compose.onAllNodesWithText("Stabile").assertCountEquals(0)
  }

  @Test
  fun `automatic download is a switch that reports the new value`() {
    showSection(FluidUpdateUiState.UpToDate)

    compose.onNodeWithText("Scarica in automatico").performClick()
    assertEquals(listOf(true), autoChanges)
  }

  @Test
  fun `a build that does not update itself says so and has no buttons that do nothing`() {
    showSection(FluidUpdateUiState.UpToDate, supported = false)

    compose.onNodeWithText("Aggiornamenti non disponibili").assertIsDisplayed()
    compose.onAllNodesWithText("Controlla ora").assertCountEquals(0)
    compose.onAllNodesWithText("Beta").assertCountEquals(0)
  }

  // ===============================================================================================
  // Il riordino
  // ===============================================================================================

  private var rows by mutableStateOf(listOf("A", "B", "C", "D", "E"))
  private var dragEnded = 0

  private fun showColumn(enabled: Boolean = true, canMoveTo: (Int) -> Boolean = { true }): FluidReorderState {
    var captured: FluidReorderState? = null
    compose.setContent {
      Themed {
        val listState = rememberLazyListState()
        val reorder = rememberFluidReorderState(
          listState = listState,
          onMove = { from, to -> rows = rows.fluidMoved(from, to) },
          onDragEnd = { dragEnded++ },
          canMoveTo = canMoveTo,
        )
        captured = reorder
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          itemsIndexed(rows, key = { _, name -> name }) { _, name ->
            Box(
              fluidReorderable(reorder, key = name, enabled = enabled)
                .testTag("row-$name")
                .fillMaxWidth()
                .height(70.dp)
                .background(Color.Gray),
            ) { Text(name) }
          }
        }
      }
    }
    compose.waitForIdle()
    return captured!!
  }

  /** Tiene premuto [tag] oltre il click lungo e lo trascina di [total] pixel in [steps] passi, fermandosi un fotogramma a ogni passo. */
  private fun liftAndDrag(tag: String, total: Float, steps: Int = 3) {
    compose.onNodeWithTag(tag).performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    repeat(steps) {
      compose.onNodeWithTag(tag).performTouchInput { moveBy(Offset(0f, total / steps)) }
      compose.waitForIdle()
    }
    compose.onNodeWithTag(tag).performTouchInput { up() }
    compose.waitForIdle()
  }

  @Test
  fun `hold a row, drag it two places down and the app's list is reordered`() {
    val reorder = showColumn()

    // Un posto = 70 dp di riga + 8 di spazio = 78 dp = 156 px a 2x: due posti, 312 px.
    liftAndDrag("row-B", total = 312f)

    assertEquals(listOf("A", "C", "D", "B", "E"), rows)
    assertEquals("l'ordine si salva una volta, al rilascio", 1, dragEnded)
    assertEquals("la riga si e' posata", null, reorder.draggingKey)
  }

  @Test
  fun `lifting and each place crossed are felt once each, and the drop is felt`() {
    showColumn()
    haptics.played.clear()

    liftAndDrag("row-B", total = 312f)

    assertEquals(FluidHapticEvent.GestureStart, haptics.played.first())
    assertEquals(FluidHapticEvent.GestureEnd, haptics.played.last())
    assertEquals(
      "un Tick per posto attraversato",
      2,
      haptics.played.count { it == FluidHapticEvent.Tick },
    )
  }

  @Test
  fun `a row dragged up goes up`() {
    showColumn()
    liftAndDrag("row-D", total = -156f, steps = 2)
    assertEquals(listOf("A", "B", "D", "C", "E"), rows)
  }

  @Test
  fun `a touch that does not hold does not lift anything`() {
    showColumn()
    compose.onNodeWithTag("row-B").performClick()
    compose.waitForIdle()

    assertEquals(listOf("A", "B", "C", "D", "E"), rows)
    assertEquals(0, dragEnded)
    assertTrue(haptics.played.none { it == FluidHapticEvent.GestureStart })
  }

  @Test
  fun `moving before the hold is a scroll, not a lift`() {
    showColumn()
    compose.onNodeWithTag("row-B").performTouchInput {
      down(center)
      moveBy(Offset(0f, 60f))
    }
    compose.mainClock.advanceTimeBy(900)
    compose.onNodeWithTag("row-B").performTouchInput { up() }
    compose.waitForIdle()

    assertEquals(listOf("A", "B", "C", "D", "E"), rows)
    assertEquals(0, dragEnded)
  }

  @Test
  fun `with reordering off the hold does nothing`() {
    showColumn(enabled = false)
    liftAndDrag("row-B", total = 312f)
    assertEquals(listOf("A", "B", "C", "D", "E"), rows)
    assertEquals(0, dragEnded)
  }

  @Test
  fun `a place the app forbids is not taken`() {
    showColumn(canMoveTo = { it != 0 })
    liftAndDrag("row-B", total = -156f, steps = 2)
    assertEquals("la prima riga e' bloccata", listOf("A", "B", "C", "D", "E"), rows)
  }

  @Test
  fun `a screen reader moves a row with the two actions instead of dragging`() {
    showColumn()
    val node = compose.onNodeWithTag("row-B").fetchSemanticsNode()
    val actions = node.config[SemanticsActions.CustomActions]
    assertEquals(listOf("Sposta prima", "Sposta dopo"), actions.map { it.label })

    compose.runOnUiThread { actions.first { it.label == "Sposta dopo" }.action() }
    compose.waitForIdle()

    assertEquals(listOf("A", "C", "B", "D", "E"), rows)
    assertEquals(1, dragEnded)
  }

  @Test
  fun `the first row cannot go before the start and the last cannot go after the end`() {
    showColumn()
    val first = compose.onNodeWithTag("row-A").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnUiThread { assertTrue(first.first { it.label == "Sposta dopo" }.action()) }
    compose.waitForIdle()
    assertEquals(listOf("B", "A", "C", "D", "E"), rows)
    val up = compose.onNodeWithTag("row-B").fetchSemanticsNode().config[SemanticsActions.CustomActions]
    compose.runOnUiThread { assertTrue(!up.first { it.label == "Sposta prima" }.action()) }
    assertEquals(listOf("B", "A", "C", "D", "E"), rows)
  }

  // --- griglia ---------------------------------------------------------------------------------

  private var tiles by mutableStateOf(listOf("t0", "t1", "t2", "t3", "t4", "t5"))

  @Test
  fun `a tile in a grid goes to the right neighbour's place`() {
    compose.setContent {
      Themed {
        val gridState = rememberLazyGridState()
        val reorder = rememberFluidReorderState(
          gridState = gridState,
          onMove = { from, to -> tiles = tiles.fluidMoved(from, to) },
        )
        LazyVerticalGrid(
          columns = GridCells.Fixed(2),
          state = gridState,
          modifier = Modifier.fillMaxSize(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          itemsIndexed(tiles, key = { _, name -> name }) { _, name ->
            Box(
              fluidReorderable(reorder, key = name)
                .testTag("tile-$name")
                .fillMaxWidth()
                .height(120.dp)
                .background(Color.Gray),
            ) { Text(name) }
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("tile-t0").performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
    // Una colonna a destra: la tessera e' larga 176 dp (352 px) e lo spazio e' 8 dp (16 px), a 2x: il centro
    // della vicina sta a 368 + 176 px, e 256 px portano il centro della prima dentro di lei.
    repeat(3) {
      compose.onNodeWithTag("tile-t0").performTouchInput { moveBy(Offset(256f / 3f, 0f)) }
      compose.waitForIdle()
    }
    compose.onNodeWithTag("tile-t0").performTouchInput { up() }
    compose.waitForIdle()

    assertEquals(listOf("t1", "t0", "t2", "t3", "t4", "t5"), tiles)
  }

  // ===============================================================================================
  // Lo scorrimento rapido
  // ===============================================================================================

  private fun monthLabel(index: Int) = "L${index / 10}"

  private fun showScroller(count: Int, onState: (androidx.compose.foundation.lazy.LazyListState) -> Unit = {}) {
    compose.setContent {
      Themed {
        val listState = rememberLazyListState()
        onState(listState)
        Box(Modifier.fillMaxSize().testTag("stage")) {
          LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("list")) {
            items(count) { index ->
              Text("Riga $index", Modifier.fillMaxWidth().height(48.dp))
            }
          }
          FluidFastScroller(state = listState, label = ::monthLabel)
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `a short list has no fast scroller`() {
    showScroller(count = 12)
    compose.onAllNodesWithContentDescription("Scorrimento rapido").assertCountEquals(0)
  }

  @Test
  fun `a long list has one cursor for the screen reader, saying where it is`() {
    showScroller(count = 300)

    val node = compose.onNodeWithContentDescription("Scorrimento rapido").fetchSemanticsNode()
    assertEquals("L0", node.config[SemanticsProperties.StateDescription])
  }

  @Test
  fun `the screen reader can jump the list with the cursor`() {
    var state: androidx.compose.foundation.lazy.LazyListState? = null
    showScroller(count = 300) { state = it }

    compose.onNodeWithContentDescription("Scorrimento rapido")
      .performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
    compose.waitForIdle()

    val first = state!!.firstVisibleItemIndex
    assertTrue("la lista e' a meta' circa, non a $first", first in 120..180)
    val label = compose.onNodeWithContentDescription("Scorrimento rapido").fetchSemanticsNode()
      .config[SemanticsProperties.StateDescription]
    assertEquals(monthLabel(first), label)
  }

  @Test
  fun `dragging the thumb takes the list where the finger goes, one tick per label`() {
    var state: androidx.compose.foundation.lazy.LazyListState? = null
    showScroller(count = 300) { state = it }

    // La maniglia compare mentre la lista scorre. Lo scorrimento qui e' un tocco che passa dalla lista.
    compose.onNodeWithTag("list").performTouchInput { swipeUp() }
    compose.waitForIdle()
    val before = state!!.firstVisibleItemIndex
    haptics.played.clear()

    val stage = compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
    val density = 2f
    val thumbPx = 52f * density
    val travel = FluidFastScroll.travel(stage.height, thumbPx)
    val info = state!!.layoutInfo
    val fraction = FluidFastScroll.fraction(
      state!!.firstVisibleItemIndex.toFloat(), info.totalItemsCount, info.visibleItemsInfo.size,
    )
    val x = stage.width - (4f + 14f) * density
    val y = fraction * travel + thumbPx / 2f

    compose.onNodeWithTag("stage").performTouchInput {
      down(Offset(x, y))
      moveBy(Offset(0f, travel * 0.25f))
      moveBy(Offset(0f, travel * 0.25f))
    }
    compose.waitForIdle()
    compose.onNodeWithTag("stage").performTouchInput { up() }
    compose.waitForIdle()

    val after = state!!.firstVisibleItemIndex
    assertTrue("la lista non si e' mossa col dito: $before -> $after", after > before + 60)
    assertEquals(1, haptics.played.count { it == FluidHapticEvent.GestureStart })
    assertTrue("niente tick per etichetta attraversata", haptics.played.count { it == FluidHapticEvent.Tick } >= 1)
  }

  @Test
  fun `a finger on the edge of a list with no thumb scrolls the list`() {
    var state: androidx.compose.foundation.lazy.LazyListState? = null
    showScroller(count = 300) { state = it }
    // Nessuno scorrimento prima: la maniglia non c'e', e il bordo e' della lista.
    val stage = compose.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
    compose.onNodeWithTag("stage").performTouchInput {
      down(Offset(stage.width - 20f, stage.height / 2f))
      moveBy(Offset(0f, -300f))
      up()
    }
    compose.waitForIdle()
    assertTrue("la lista doveva scorrere", state!!.firstVisibleItemIndex > 0)
    assertEquals(0, haptics.played.count { it == FluidHapticEvent.GestureStart })
  }

  @Test
  fun `the scroller can be built on a target rather than a state`() {
    compose.setContent {
      Themed {
        val listState = rememberLazyListState()
        val target = rememberFluidFastScrollTarget(listState)
        Box(Modifier.fillMaxSize()) {
          LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(200) { Text("Riga $it", Modifier.height(48.dp)) }
          }
          FluidFastScroller(target = target, label = { "X$it" }, minItems = 10, contentDescription = "Cursore")
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Cursore").assertExists()
  }

  // ===============================================================================================
  // Lo scrubber
  // ===============================================================================================

  /** La posizione che dice il lettore: e' fuori dallo scrubber, che la legge e non la possiede. */
  private var external by mutableFloatStateOf(0f)
  private val seeked = mutableListOf<Float>()
  private val scrubbed = mutableListOf<Float>()

  /** L'ordine delle chiamate di un gesto: `start`, `scrub`..., `seek`, `end`. */
  private val scrubLog = mutableListOf<String>()

  /** Un brano di 3:20, in millisecondi. */
  private val millis = 0f..200_000f

  private fun showScrubber(
    range: ClosedFloatingPointRange<Float> = millis,
    step: Float = 0f,
    enabled: Boolean = true,
    withLabels: Boolean = false,
  ) {
    compose.setContent {
      Themed {
        Box(Modifier.fillMaxSize()) {
          FluidScrubber(
            position = { external },
            onSeek = {
              seeked += it
              scrubLog += "seek"
            },
            modifier = Modifier.testTag("scrubber"),
            valueRange = range,
            enabled = enabled,
            step = step,
            onScrubStart = { scrubLog += "start" },
            onScrub = {
              scrubbed += it
              scrubLog += "scrub"
            },
            onScrubEnd = { scrubLog += "end" },
            startLabel = if (withLabels) FluidScrubbing.Elapsed else null,
            endLabel = if (withLabels) FluidScrubbing.Remaining else null,
            keyStep = 10_000f,
          )
        }
      }
    }
    compose.waitForIdle()
    haptics.played.clear()
  }

  private fun scrubberNode() = compose.onNodeWithTag("scrubber").fetchSemanticsNode()

  /** Il valore che lo scrubber dice a TalkBack: quello che si mostra, non per forza quello che dice il lettore. */
  private fun shown() = scrubberNode().config[SemanticsProperties.ProgressBarRangeInfo].current

  private fun spoken() = scrubberNode().config[SemanticsProperties.StateDescription]

  private fun playerAt(value: Float) {
    compose.runOnUiThread { external = value }
    compose.waitForIdle()
  }

  /** La x del centro della maniglia a una frazione: 10 dp di margine, 40 dp di maniglia, a 2x. */
  private fun thumbX(fraction: Float): Float {
    val edge = 20f
    val thumb = 80f
    val track = scrubberNode().size.width.toFloat() - 2f * edge
    return edge + maxOf(thumb / 4f, minOf(track * fraction, track - thumb / 4f))
  }

  /** La x di un punto della pista, a una frazione: dove cade un tocco. */
  private fun trackX(fraction: Float): Float {
    val edge = 20f
    return edge + (scrubberNode().size.width.toFloat() - 2f * edge) * fraction
  }

  /** La metà della riga da 48 dp (a 2x): dove sta la pista. */
  private val trackY = 48f

  @Test
  fun `the scrubber thumb stays under the finger from the grab to the lift, and the lift seeks once`() {
    playerAt(100_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0.5f), trackY))
      // Una pista da 680 px: 68 px sono un decimo, e il dito e' 1:1.
      moveBy(Offset(68f, 0f))
      moveBy(Offset(68f, 0f))
    }
    compose.waitForIdle()

    assertEquals("un valore per movimento", 2, scrubbed.size)
    assertEquals(120_000f, scrubbed[0], 100f)
    assertEquals(140_000f, scrubbed[1], 100f)
    assertEquals("la maniglia e' dove il dito dice", 140_000f, shown(), 100f)
    assertTrue("niente salto prima del rilascio", seeked.isEmpty())

    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()

    assertEquals("un salto solo", 1, seeked.size)
    assertEquals(140_000f, seeked.single(), 100f)
    assertEquals(listOf("start", "scrub", "scrub", "seek", "end"), scrubLog)
  }

  @Test
  fun `taking the thumb off centre does not move it, and a finger going past the end and back does not leave it behind`() {
    playerAt(100_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput {
      // Quindici pixel a destra del centro: dentro la presa, e la maniglia non si sposta sotto il dito.
      down(Offset(thumbX(0.5f) + 15f, trackY))
    }
    compose.waitForIdle()
    assertEquals(100_000f, shown(), 1f)
    assertTrue("afferrare non e' muovere", scrubbed.isEmpty())

    compose.onNodeWithTag("scrubber").performTouchInput {
      // Oltre la fine (la maniglia si ferma) e di nuovo indietro: non e' tornata finche' il dito non ripassa dal
      // punto da cui e' uscito.
      moveBy(Offset(500f, 0f))
      moveBy(Offset(-100f, 0f))
    }
    compose.waitForIdle()
    assertEquals("ancora in fondo, il dito non e' ancora rientrato", 200_000f, shown(), 1f)
    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()
  }

  @Test
  fun `while the finger is down the player is ignored, and after the lift the thumb waits for the seek`() {
    playerAt(100_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0.5f), trackY))
      moveBy(Offset(68f, 0f))
    }
    compose.waitForIdle()
    assertEquals(120_000f, shown(), 100f)

    // Il lettore va avanti per conto suo, a battiti: la maniglia e il valore a voce non lo guardano.
    repeat(5) {
      playerAt(external + 1_000f)
      assertEquals("col dito giu' comanda il dito", 120_000f, shown(), 100f)
    }
    assertEquals("2:00 di 3:20", spoken())

    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(1, seeked.size)

    // Il seek non e' ancora arrivato: il lettore e' dov'era. La maniglia resta dove l'hai lasciata, niente ritorno.
    playerAt(106_000f)
    assertEquals("nessun ritorno", 120_000f, shown(), 100f)
    assertEquals("2:00 di 3:20", spoken())
    assertEquals("e nessun secondo salto", 1, seeked.size)

    // Il seek e' arrivato: il lettore e' al punto (poco piu' in la), e da qui conta di nuovo lui.
    playerAt(120_300f)
    assertEquals(120_300f, shown(), 1f)
  }

  @Test
  fun `a seek the player never makes does not hold the thumb for ever`() {
    playerAt(100_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0.5f), trackY))
      moveBy(Offset(68f, 0f))
      up()
    }
    compose.waitForIdle()
    assertEquals(120_000f, shown(), 100f)

    // L'app ha rifiutato il salto: il lettore e' sempre li'. Passato il fermo, la maniglia torna a lui.
    compose.mainClock.advanceTimeBy(FluidMotion.ScrubberHoldMillis + 200)
    compose.waitForIdle()
    assertEquals(100_000f, shown(), 1f)
  }

  @Test
  fun `a tap on the track jumps there once and the thumb stays until the player arrives`() {
    playerAt(150_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput { click(Offset(trackX(0.25f), trackY)) }
    compose.waitForIdle()

    assertEquals(1, seeked.size)
    assertEquals(50_000f, seeked.single(), 100f)
    assertEquals("un tocco non e' un trascinamento", listOf("seek"), scrubLog)
    assertEquals("la maniglia e' al punto, il lettore e' ancora a 150 000", 50_000f, shown(), 100f)
    assertEquals(listOf(FluidHapticEvent.Tap), haptics.played)

    playerAt(50_200f)
    assertEquals(50_200f, shown(), 1f)
  }

  @Test
  fun `a tap on the very end plays the edge and nothing else`() {
    playerAt(20_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput { click(Offset(trackX(1f), trackY)) }
    compose.waitForIdle()

    assertEquals(listOf(200_000f), seeked)
    assertEquals("un momento, un colpo", listOf(FluidHapticEvent.Threshold), haptics.played)
  }

  @Test
  fun `a continuous drag plays nothing under the finger, and only the arrival at an end is felt`() {
    playerAt(60_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0.3f), trackY))
      repeat(30) { moveBy(Offset(10f, 0f)) }
    }
    compose.waitForIdle()
    assertTrue("niente grana in un brano continuo: ${haptics.played}", haptics.played.isEmpty())

    compose.onNodeWithTag("scrubber").performTouchInput {
      moveBy(Offset(400f, 0f))
      moveBy(Offset(50f, 0f))
    }
    compose.waitForIdle()
    assertEquals("l'estremita' suona una volta, starci no", listOf(FluidHapticEvent.Threshold), haptics.played)
    assertEquals(200_000f, shown(), 1f)

    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(listOf(200_000f), seeked)
  }

  @Test
  fun `a stepped scrubber lands on each step with one tick, and the seek is exactly a step`() {
    // Tredici fotogrammi, come il radar: 0..12 a passi di uno.
    playerAt(0f)
    showScrubber(range = 0f..12f, step = 1f)

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0f), trackY))
      // Una pista da 680 px in dodici passi: 57 px sono poco piu' di un passo.
      repeat(3) { moveBy(Offset(57f, 0f), delayMillis = 80L) }
    }
    compose.waitForIdle()
    assertEquals(listOf(1f, 2f, 3f), scrubbed)
    assertEquals("un Tick per passo", listOf(FluidHapticEvent.Tick, FluidHapticEvent.Tick, FluidHapticEvent.Tick), haptics.played)

    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals(listOf(3f), seeked)
    assertEquals(3f, shown(), 0f)
  }

  @Test
  fun `a tap on a stepped track takes the nearest step and ticks once`() {
    playerAt(0f)
    showScrubber(range = 0f..12f, step = 1f)

    compose.onNodeWithTag("scrubber").performTouchInput { click(Offset(trackX(0.5f), trackY)) }
    compose.waitForIdle()

    assertEquals(listOf(6f), seeked)
    assertEquals(listOf(FluidHapticEvent.Tick), haptics.played)
  }

  @Test
  fun `for the screen reader the scrubber is one slider with a range, a spoken time and two ways to move`() {
    playerAt(65_000f)
    showScrubber()

    val node = scrubberNode()
    assertEquals(listOf("Posizione"), node.config[SemanticsProperties.ContentDescription])
    assertEquals("1:05 di 3:20", node.config[SemanticsProperties.StateDescription])
    val info = node.config[SemanticsProperties.ProgressBarRangeInfo]
    assertEquals(65_000f, info.current, 0f)
    assertEquals(millis, info.range)
    assertEquals("continuo: nessun passo", 0, info.steps)
    assertTrue(node.config.contains(SemanticsActions.SetProgress))
    assertEquals(listOf("Indietro", "Avanti"), node.config[SemanticsActions.CustomActions].map { it.label })
    assertTrue("il valore a voce non e' anche un secondo nodo", compose.onAllNodesWithContentDescription("Posizione").fetchSemanticsNodes().size == 1)
  }

  @Test
  fun `the screen reader can set the position, and the two actions move by the key step from where the thumb is`() {
    playerAt(65_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").performSemanticsAction(SemanticsActions.SetProgress) { it(120_000f) }
    compose.waitForIdle()
    assertEquals(listOf(120_000f), seeked)
    assertEquals("2:00 di 3:20", spoken())

    fun act(label: String) {
      val actions = scrubberNode().config[SemanticsActions.CustomActions]
      compose.runOnUiThread { actions.first { it.label == label }.action() }
      compose.waitForIdle()
    }
    act("Avanti")
    act("Avanti")
    act("Indietro")
    // Dal punto tenuto (il lettore non c'e' ancora arrivato), non dalla sua posizione: 120 + 10 + 10 - 10.
    assertEquals(listOf(120_000f, 130_000f, 140_000f, 130_000f), seeked)
    assertEquals(130_000f, shown(), 1f)
  }

  @Test
  fun `a stepped scrubber says how many steps it has and a screen reader lands on a step`() {
    playerAt(4f)
    showScrubber(range = 0f..12f, step = 1f)

    assertEquals("dodici intervalli, undici passi interni", 11, scrubberNode().config[SemanticsProperties.ProgressBarRangeInfo].steps)
    compose.onNodeWithTag("scrubber").performSemanticsAction(SemanticsActions.SetProgress) { it(7.4f) }
    compose.waitForIdle()
    assertEquals(listOf(7f), seeked)
  }

  @Test
  fun `a switched off scrubber is disabled for the screen reader and does not answer to a finger`() {
    playerAt(100_000f)
    showScrubber(enabled = false)

    val node = scrubberNode()
    assertTrue(node.config.contains(SemanticsProperties.Disabled))
    assertTrue(!node.config.contains(SemanticsActions.SetProgress))

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(0.5f), trackY))
      moveBy(Offset(100f, 0f))
      up()
    }
    compose.waitForIdle()
    assertTrue(seeked.isEmpty())
    assertTrue(scrubLog.isEmpty())
  }

  @Test
  fun `the keyboard moves the scrubber by the key step, a page or to an end`() {
    playerAt(100_000f)
    showScrubber()

    compose.onNodeWithTag("scrubber").requestFocus()
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.DirectionRight) }
    compose.waitForIdle()
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.PageUp) }
    compose.waitForIdle()
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.MoveEnd) }
    compose.waitForIdle()
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.MoveHome) }
    compose.waitForIdle()
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.DirectionLeft) }
    compose.waitForIdle()

    // 100 + 10 (freccia, il keyStep dato); +20 (pagina: un decimo di 200); fine; inizio; e indietro dall'inizio resta a zero.
    assertEquals(listOf(110_000f, 130_000f, 200_000f, 0f, 0f), seeked)
  }

  @Test
  fun `an intermediate player acknowledgement does not consume the next keyboard step`() {
    playerAt(25_000f)
    showScrubber()
    compose.onNodeWithTag("scrubber").requestFocus()
    compose.onNodeWithTag("scrubber").performKeyInput {
      pressKey(Key.DirectionRight)
      pressKey(Key.DirectionRight)
    }
    playerAt(35_000f)
    compose.onNodeWithTag("scrubber").performKeyInput { pressKey(Key.DirectionRight) }
    assertEquals(listOf(35_000f, 45_000f, 55_000f), seeked)
  }

  @Test
  fun `an intermediate player acknowledgement does not consume the next accessibility step`() {
    playerAt(75_000f)
    showScrubber()
    fun back() {
      val action = scrubberNode().config[SemanticsActions.CustomActions].first { it.label == "Indietro" }
      compose.runOnUiThread { assertTrue(action.action()) }
      compose.waitForIdle()
    }
    back()
    back()
    playerAt(65_000f)
    back()
    assertEquals(listOf(65_000f, 55_000f, 45_000f), seeked)
  }

  @Test
  fun `the texts under the track show the finger while it is down and are not read twice by the screen reader`() {
    playerAt(65_000f)
    showScrubber(withLabels = true)

    compose.onNodeWithText("1:05", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("−2:15", useUnmergedTree = true).assertExists()
    compose.onAllNodesWithText("1:05").assertCountEquals(0)

    compose.onNodeWithTag("scrubber").performTouchInput {
      down(Offset(thumbX(65_000f / 200_000f), trackY))
      moveBy(Offset(68f, 0f))
    }
    compose.waitForIdle()
    // Il dito e' a 85 000: i testi dicono il dito, non il lettore che e' ancora a 65 000.
    compose.onNodeWithText("1:25", useUnmergedTree = true).assertExists()
    compose.onNodeWithText("−1:55", useUnmergedTree = true).assertExists()
    compose.onAllNodesWithText("1:05", useUnmergedTree = true).assertCountEquals(0)

    compose.onNodeWithTag("scrubber").performTouchInput { up() }
    compose.waitForIdle()
  }

  @Test
  fun `a finger dragging the thumb on a page that scrolls does not scroll the page`() {
    lateinit var scroll: androidx.compose.foundation.ScrollState
    external = 100_000f
    compose.setContent {
      Themed {
        scroll = androidx.compose.foundation.rememberScrollState()
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
          androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(200.dp))
          FluidScrubber(
            position = { external },
            onSeek = { seeked += it },
            modifier = Modifier.padding(horizontal = 24.dp).testTag("scrubber"),
            valueRange = millis,
            onScrub = { scrubbed += it },
          )
          androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().height(2_000.dp))
        }
      }
    }
    compose.waitForIdle()

    // Un pollice vero: un po' di lato e molto in su, ben oltre lo slop.
    compose.onNodeWithTag("scrubber").performTouchInput {
      down(center)
      repeat(12) { moveBy(Offset(8f, -18f)) }
      up()
    }
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, scroll.value)
    assertTrue("il valore non ha seguito il dito: $scrubbed", scrubbed.isNotEmpty() && scrubbed.last() > 100_000f)
    assertEquals(1, seeked.size)
  }

  // ===============================================================================================
  // Il visualizzatore
  // ===============================================================================================

  private var dismissed = 0
  private val pages = mutableListOf<Int>()

  private fun showViewer(visible: Boolean = true, pageCount: Int = 3, withHost: Boolean = true) {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(if (withHost) LocalFluidGlassModalHostState provides host else LocalFluidGlassModalHostState provides null) {
          Box(Modifier.fillMaxSize()) {
            FluidImageViewer(
              visible = visible,
              onDismissRequest = { dismissed++ },
              pageCount = pageCount,
              onPageChange = { pages += it },
            ) { index ->
              Box(Modifier.fillMaxSize().testTag("page-$index"))
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun zoomPercent(): Int {
    val node = compose.onNode(SemanticsMatcher("ha uno stato di zoom") {
      it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Zoom ") == true
    }).fetchSemanticsNode()
    return node.config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix("%").toInt()
  }

  @Test
  fun `the viewer opens in the modal host with the page and the counter as title`() {
    showViewer()

    compose.onNodeWithTag("page-0").assertIsDisplayed()
    compose.onNodeWithText("1 di 3").assertIsDisplayed()
    assertEquals(100, zoomPercent())
  }

  @Test
  fun `the close button of the page asks the app to dismiss`() {
    showViewer()
    compose.onNodeWithContentDescription("Chiudi").performClick()
    assertEquals(1, dismissed)
  }

  @Test
  fun `closed, the viewer draws nothing`() {
    showViewer(visible = false)
    compose.onAllNodesWithText("1 di 3").assertCountEquals(0)
  }

  @Test
  fun `without a host there is nothing to open in and no crash`() {
    showViewer(withHost = false)
    compose.onAllNodesWithText("1 di 3").assertCountEquals(0)
  }

  @Test
  fun `one image has no counter`() {
    showViewer(pageCount = 1)
    compose.onNodeWithTag("page-0").assertIsDisplayed()
    compose.onAllNodesWithText("1 di 1").assertCountEquals(0)
  }

  @Test
  fun `a double tap zooms in, a second one zooms back out, and a tap is felt`() {
    showViewer()

    compose.onNodeWithTag("page-0").performTouchInput { doubleClick() }
    compose.waitForIdle()
    assertEquals(250, zoomPercent())
    assertTrue(FluidHapticEvent.Tap in haptics.played)

    compose.onNodeWithTag("page-0").performTouchInput { doubleClick() }
    compose.waitForIdle()
    assertEquals(100, zoomPercent())
  }

  @Test
  fun `a pinch zooms in`() {
    showViewer()

    compose.onNodeWithTag("page-0").performTouchInput {
      val c = center
      pinch(
        start0 = c - Offset(40f, 0f),
        end0 = c - Offset(200f, 0f),
        start1 = c + Offset(40f, 0f),
        end1 = c + Offset(200f, 0f),
      )
    }
    compose.waitForIdle()
    assertTrue("il pizzico non ha ingrandito: ${zoomPercent()}%", zoomPercent() > 150)
  }

  @Test
  fun `a screen reader zooms by steps with two actions`() {
    showViewer()

    val zoomNode = { compose.onNode(SemanticsMatcher("zoom") { it.config.contains(SemanticsActions.CustomActions) && it.config.getOrNull(SemanticsProperties.StateDescription)?.startsWith("Zoom ") == true }) }
    fun act(label: String) {
      val actions = zoomNode().fetchSemanticsNode().config[SemanticsActions.CustomActions]
      compose.runOnUiThread { actions.first { it.label == label }.action() }
      compose.waitForIdle()
    }

    act("Ingrandisci")
    assertEquals(250, zoomPercent())
    act("Ingrandisci")
    assertEquals(400, zoomPercent())
    act("Riduci")
    act("Riduci")
    assertEquals(100, zoomPercent())
  }

  @Test
  fun `with one finger at scale one the viewer leaves the finger to the pager`() {
    showViewer()

    compose.onNodeWithTag("page-0").performTouchInput { swipeLeft() }
    compose.waitForIdle()

    compose.onNodeWithText("2 di 3").assertIsDisplayed()
    assertTrue("la pagina non e' stata annunciata: $pages", 1 in pages)
  }
}
