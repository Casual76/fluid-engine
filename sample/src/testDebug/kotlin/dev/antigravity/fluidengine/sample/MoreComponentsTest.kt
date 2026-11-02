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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.UpdateChannel
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroll
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidImageViewer
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
 * riordino, lo scorrimento rapido e il visualizzatore.
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
