package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidExperimentalRowGlass
import dev.antigravity.fluidengine.ui.fluid.FluidFastScroller
import dev.antigravity.fluidengine.ui.fluid.FluidGroupedItemScope
import dev.antigravity.fluidengine.ui.fluid.FluidGroupedMaterial
import dev.antigravity.fluidengine.ui.fluid.FluidJumpPill
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeAction
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidItemSpacing
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGroupedItems
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeActions
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSwipeState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidGroupSegment
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidStatusBadge
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

private class LongListsHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * Liste lunghe e righe (F1.2a) in un albero Compose vero, sotto un dito finto: quello che le funzioni pure di
 * `FluidGroupedItemsTest` e `FluidSwipeActionsTest` non possono dire.
 *
 * - `fluidGroupedItems`: un item per riga, righe a filo con la fascia richiusa che riceve il tocco, l'inserimento
 *   in cima che non sposta la riga che si sta leggendo, la rimozione che richiude angoli e pezzi, lo stato di una
 *   riga che segue la sua chiave dopo una ricreazione, il movimento ridotto senza animazione di posizionamento;
 * - `fluidSwipeActions`: soglia con un solo `Threshold`, ripensamento, verticale alla lista, pressione lunga al
 *   menu, bordo dello schermo, azioni TalkBack sullo stesso nodo insieme a quelle della riga (E05), spento, testo al
 *   200% col tema scuro, movimento ridotto;
 * - `FluidJumpPill`, `FluidEmptyState` con l'azione (E06), `FluidStatusBadge` a colore libero (E07).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidLongListsTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = LongListsHaptics()

  @Before
  fun reset() {
    haptics.played.clear()
  }

  @Composable
  private fun Themed(
    reducedMotion: Boolean = false,
    dark: Boolean = false,
    fontScale: Float? = null,
    content: @Composable () -> Unit,
  ) {
    val settings = remember(dark) { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
    FluidTheme(settings = settings) {
      val density = LocalDensity.current
      CompositionLocalProvider(
        LocalFluidHaptics provides haptics,
        LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reducedMotion) 0f else 1f),
        LocalDensity provides Density(density.density, fontScale ?: density.fontScale),
        content = content,
      )
    }
  }

  // ---------------------------------------------------------------------------------------------
  // fluidGroupedItems
  // ---------------------------------------------------------------------------------------------

  private var ids by mutableStateOf(List(5) { it.toLong() })
  private val segments = mutableMapOf<Long, FluidGroupSegment>()
  private val clicks = mutableListOf<Long>()

  @Composable
  private fun GroupedList(
    list: LazyListState,
    row: @Composable FluidGroupedItemScope.(id: Long) -> Unit = { id ->
      FluidListRow(title = "Riga $id", subtitle = "", onClick = { clicks += id }, modifier = Modifier.testTag("row-$id"))
    },
  ) {
    LazyColumn(
      state = list,
      modifier = Modifier.fillMaxSize(),
      verticalArrangement = Arrangement.spacedBy(LocalFluidItemSpacing.current),
    ) {
      item(key = "head") { Text("Testa", modifier = Modifier.testTag("head")) }
      fluidGroupedItems(count = ids.size, key = { index -> ids[index] }) { index ->
        val id = ids[index]
        segments[id] = segment
        row(id)
      }
    }
  }

  private fun top(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().top
  private fun bottom(tag: String) = compose.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom

  @Test
  fun `una riga e' un item della lista`() {
    val list = LazyListState()
    compose.setContent { Themed { GroupedList(list) } }
    compose.runOnIdle { assertEquals(1 + 5, list.layoutInfo.totalItemsCount) }
  }

  @Test
  fun `le righe sono a filo, i pezzi giusti, e la fascia richiusa riceve il tocco`() {
    val list = LazyListState()
    compose.setContent { Themed { GroupedList(list) } }
    compose.waitForIdle()
    for (id in 0L until 4L) {
      assertEquals("riga $id a filo con la successiva", bottom("row-$id").value, top("row-${id + 1}").value, 0.5f)
    }
    assertEquals(FluidGroupSegment.First, segments[0L])
    assertEquals(FluidGroupSegment.Middle, segments[2L])
    assertEquals(FluidGroupSegment.Last, segments[4L])

    // In fondo alla prima riga, dentro la fascia che l'item non dichiara (la spaziatura richiusa).
    compose.onNodeWithTag("row-0").performTouchInput { click(Offset(centerX, height - 4.dp.toPx())) }
    compose.runOnIdle { assertEquals(listOf(0L), clicks) }
  }

  @Test
  fun `una riga che entra in cima non sposta quella che si sta leggendo`() {
    ids = List(200) { it.toLong() }
    val list = LazyListState()
    lateinit var scroll: (Int) -> Unit
    compose.setContent {
      val scope = rememberCoroutineScope()
      scroll = { index -> scope.launch { list.scrollToItem(index, 20) } }
      Themed { GroupedList(list) }
    }
    compose.runOnIdle { scroll(61) }
    compose.waitForIdle()
    val reading = ids[list.firstVisibleItemIndex - 1]
    val before = top("row-$reading")
    val indexBefore = list.firstVisibleItemIndex

    ids = listOf(1002L, 1001L, 1000L) + ids
    compose.waitForIdle()

    assertEquals("la riga letta non si e' mossa", before.value, top("row-$reading").value, 0.5f)
    assertEquals(indexBefore + 3, list.firstVisibleItemIndex)
  }

  @Test
  fun `l'ultima riga che esce lascia la penultima ultima, a filo, senza vuoti`() {
    val list = LazyListState()
    compose.setContent { Themed(reducedMotion = true) { GroupedList(list) } }
    compose.waitForIdle()
    ids = ids.dropLast(1)
    compose.waitForIdle()
    assertEquals(FluidGroupSegment.Last, segments[3L])
    assertEquals(FluidGroupSegment.Middle, segments[2L])
    assertEquals(bottom("row-2").value, top("row-3").value, 0.5f)
  }

  @Test
  fun `lo stato di una riga segue la sua chiave, anche dopo una ricreazione`() {
    val restoration = StateRestorationTester(compose)
    val list = LazyListState()
    restoration.setContent {
      Themed {
        GroupedList(list) { id ->
          var taps by rememberSaveable { mutableIntStateOf(0) }
          FluidListRow(title = "Riga $id · $taps", subtitle = "", onClick = { taps++ }, modifier = Modifier.testTag("row-$id"))
        }
      }
    }
    compose.onNodeWithTag("row-2").performClick()
    compose.onNodeWithText("Riga 2 · 1").assertIsDisplayed()

    // Arriva una riga in cima: la riga 2 cambia indice, non stato.
    ids = listOf(99L) + ids
    compose.waitForIdle()
    restoration.emulateSavedInstanceStateRestore()

    compose.onNodeWithText("Riga 2 · 1").assertIsDisplayed()
    compose.onNodeWithText("Riga 99 · 0").assertIsDisplayed()
    compose.onNodeWithText("Riga 1 · 0").assertIsDisplayed()
  }

  @Test
  fun `50 000 righe con lo scorrimento rapido - un salto lontano trova la riga e il suo pezzo`() {
    ids = List(50_000) { it.toLong() }
    val list = LazyListState()
    lateinit var scroll: (Int) -> Unit
    compose.setContent {
      val scope = rememberCoroutineScope()
      scroll = { index -> scope.launch { list.scrollToItem(index) } }
      Themed {
        Box(Modifier.fillMaxSize()) {
          GroupedList(list)
          FluidFastScroller(state = list, label = { index -> "Blocco ${index / 1_000}" }, modifier = Modifier.align(Alignment.CenterEnd))
        }
      }
    }
    compose.runOnIdle { assertEquals(1 + 50_000, list.layoutInfo.totalItemsCount) }
    compose.runOnIdle { scroll(1 + 40_000) }
    compose.waitForIdle()
    compose.onNodeWithTag("row-40000").assertIsDisplayed()
    assertEquals(FluidGroupSegment.Middle, segments[40_000L])
    compose.runOnIdle { scroll(1 + 49_999) }
    compose.waitForIdle()
    compose.onNodeWithTag("row-49999").assertIsDisplayed()
    assertEquals(FluidGroupSegment.Last, segments[49_999L])
  }

  @OptIn(FluidExperimentalRowGlass::class)
  @Test
  fun `senza canvas i tre fondi ripiegano sull'opaco e le righe restano a filo`() {
    var shown by mutableStateOf(FluidGroupedMaterial.Opaque)
    compose.setContent {
      Themed {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(LocalFluidItemSpacing.current)) {
          fluidGroupedItems(count = 3, material = shown) { index ->
            FluidListRow(title = "Riga $index", subtitle = "", modifier = Modifier.testTag("m-$index"))
          }
        }
      }
    }
    for (material in listOf(FluidGroupedMaterial.Opaque, FluidGroupedMaterial.Tinted, FluidGroupedMaterial.GlassSegments)) {
      shown = material
      compose.waitForIdle()
      assertEquals("$material", bottom("m-0").value, top("m-1").value, 0.5f)
      assertEquals("$material", bottom("m-1").value, top("m-2").value, 0.5f)
    }
  }

  private fun placementAfterOneFrame(reducedMotion: Boolean): Pair<Float, Float> {
    val list = LazyListState()
    compose.setContent { Themed(reducedMotion = reducedMotion) { GroupedList(list) } }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    ids = listOf(42L) + ids
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    val early = top("row-0").value
    compose.mainClock.advanceTimeBy(3_000)
    val settled = top("row-0").value
    compose.mainClock.autoAdvance = true
    return early to settled
  }

  @Test
  fun `col movimento ridotto le righe vanno al loro posto senza animazione`() {
    val (early, settled) = placementAfterOneFrame(reducedMotion = true)
    assertEquals(settled, early, 0.5f)
  }

  @Test
  fun `senza movimento ridotto le righe ci vanno con la molla`() {
    val (early, settled) = placementAfterOneFrame(reducedMotion = false)
    assertTrue("nessuna animazione: $early contro $settled", abs(settled - early) > 1f)
  }

  // ---------------------------------------------------------------------------------------------
  // fluidSwipeActions
  // ---------------------------------------------------------------------------------------------

  private var started = 0
  private var ended = 0
  private var longClicks = 0
  private var rowClicks = 0
  private lateinit var swipe: FluidSwipeState

  @Composable
  private fun SwipeRow(
    enabled: Boolean = true,
    removes: Boolean = true,
    extra: List<CustomAccessibilityAction> = emptyList(),
  ) {
    swipe = rememberFluidSwipeState()
    FluidListRow(
      title = "Una riga",
      subtitle = "Da scorrere",
      onClick = { rowClicks++ },
      onLongClick = { longClicks++ },
      customActions = extra,
      modifier = Modifier
        .fluidSwipeActions(
          state = swipe,
          startAction = FluidSwipeAction("Segna come letto", Icons.Rounded.MarkEmailRead, Color(0xFF1565C0)) { started++ },
          endAction = FluidSwipeAction("Archivia", Icons.Rounded.Archive, Color(0xFF2E7D32), removesItem = removes) { ended++ },
          enabled = enabled,
        )
        .testTag("swipe-row"),
    )
  }

  private fun threshold() = haptics.played.count { it == FluidHapticEvent.Threshold }

  /**
   * Un trascinamento orizzontale di [steps] passi da [stepPx] (negativo: verso sinistra), che parte a 40 dp dal lato
   * da cui si allontana (fuori dalla zona del bordo, e senza uscire dallo schermo), con un eventuale ritorno.
   */
  private fun dragFromCenter(steps: Int, stepPx: Float, then: Pair<Int, Float>? = null) {
    compose.onNodeWithTag("swipe-row").performTouchInput {
      val inset = 40.dp.toPx()
      down(Offset(if (stepPx < 0f) width - inset else inset, centerY))
      repeat(steps) { moveBy(Offset(stepPx, 0f)) }
      then?.let { (backSteps, backPx) -> repeat(backSteps) { moveBy(Offset(backPx, 0f)) } }
      up()
    }
  }

  @Test
  fun `oltre la soglia la riga esce e l'azione parte una volta, con un solo Threshold`() {
    compose.setContent { Themed { Box(Modifier.fillMaxWidth()) { SwipeRow() } } }
    dragFromCenter(steps = 25, stepPx = -20f)
    compose.waitForIdle()
    assertEquals(1, ended)
    assertEquals(0, started)
    assertEquals("il click della riga non deve partire", 0, rowClicks)
    assertEquals("un momento, un colpo: ${haptics.played}", 1, threshold())
    compose.runOnIdle { assertTrue(swipe.isDismissed) }
  }

  @Test
  fun `un'azione che non toglie la riga parte e la riga torna a posto`() {
    compose.setContent { Themed { SwipeRow() } }
    dragFromCenter(steps = 25, stepPx = 20f)
    compose.waitForIdle()
    assertEquals(1, started)
    compose.runOnIdle {
      assertEquals(0f, swipe.offset, 0f)
      assertTrue(!swipe.isDismissed)
    }
  }

  @Test
  fun `tornando indietro prima di rilasciare non succede niente`() {
    compose.setContent { Themed { SwipeRow() } }
    dragFromCenter(steps = 25, stepPx = -20f, then = 24 to 20f)
    compose.waitForIdle()
    assertEquals(0, ended)
    assertEquals(0, started)
    assertEquals("armata e disarmata: ${haptics.played}", 2, threshold())
    compose.runOnIdle { assertEquals(0f, swipe.offset, 0f) }
  }

  @Test
  fun `lo scorrimento verticale e' della lista, non della riga`() {
    ids = List(30) { it.toLong() }
    val list = LazyListState()
    val states = mutableMapOf<Long, FluidSwipeState>()
    compose.setContent {
      Themed {
        GroupedList(list) { id ->
          val state = rememberFluidSwipeState()
          states[id] = state
          FluidListRow(
            title = "Riga $id",
            subtitle = "Una riga",
            onClick = {},
            modifier = Modifier
              .fluidSwipeActions(state, endAction = FluidSwipeAction("Archivia", Icons.Rounded.Archive, Color(0xFF2E7D32)) { ended++ })
              .testTag("row-$id"),
          )
        }
      }
    }
    compose.onNodeWithTag("row-3").performTouchInput {
      down(center)
      repeat(12) { moveBy(Offset(-3f, -30f)) }
      up()
    }
    compose.waitForIdle()
    assertTrue("la lista non ha scorso", list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0)
    assertEquals(0, ended)
    compose.runOnIdle { assertTrue(states.values.all { it.offset == 0f }) }
  }

  @Test
  fun `la pressione lunga resta del menu, e il dito dopo non scorre la riga`() {
    compose.setContent { Themed { SwipeRow() } }
    compose.onNodeWithTag("swipe-row").performTouchInput {
      down(center)
      advanceEventTime(1_000)
      repeat(20) { moveBy(Offset(-20f, 0f)) }
      up()
    }
    compose.waitForIdle()
    assertEquals(1, longClicks)
    assertEquals(0, ended)
    assertEquals(0, threshold())
    compose.runOnIdle { assertEquals(0f, swipe.offset, 0f) }
  }

  @Test
  fun `dal bordo dello schermo la riga non parte`() {
    compose.setContent { Themed { SwipeRow() } }
    compose.onNodeWithTag("swipe-row").performTouchInput {
      down(Offset(8.dp.toPx(), centerY))
      repeat(25) { moveBy(Offset(20f, 0f)) }
      up()
    }
    compose.waitForIdle()
    assertEquals(0, started)
    compose.runOnIdle { assertEquals(0f, swipe.offset, 0f) }
  }

  @Test
  fun `le azioni dello scorrimento e quelle della riga stanno sullo stesso nodo, per TalkBack`() {
    var pinned = 0
    compose.setContent {
      Themed { SwipeRow(extra = listOf(CustomAccessibilityAction("Fissa") { pinned++; true })) }
    }
    val actions = compose.onNodeWithTag("swipe-row").fetchSemanticsNode().config.getOrNull(SemanticsActions.CustomActions)
    assertEquals(setOf("Fissa", "Segna come letto", "Archivia"), actions.orEmpty().map { it.label }.toSet())

    compose.runOnIdle { actions!!.first { it.label == "Archivia" }.action() }
    compose.waitForIdle()
    assertEquals(1, ended)
    compose.runOnIdle { actions!!.first { it.label == "Fissa" }.action() }
    assertEquals(1, pinned)
  }

  @Test
  fun `una riga senza azioni non ha azioni personalizzate, come prima`() {
    compose.setContent { Themed { FluidListRow(title = "Riga", subtitle = "", onClick = {}, modifier = Modifier.testTag("plain")) } }
    compose.onNodeWithTag("plain").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
  }

  @Test
  fun `spenta non scorre e non offre azioni`() {
    compose.setContent { Themed { SwipeRow(enabled = false) } }
    compose.onNodeWithTag("swipe-row").assert(SemanticsMatcher.keyNotDefined(SemanticsActions.CustomActions))
    dragFromCenter(steps = 25, stepPx = -20f)
    compose.waitForIdle()
    assertEquals(0, ended)
    compose.runOnIdle { assertEquals(0f, swipe.offset, 0f) }
  }

  @Test
  fun `testo al 200 per cento e tema scuro - la riga si scorre e archivia`() {
    compose.setContent { Themed(dark = true, fontScale = 2f) { SwipeRow() } }
    dragFromCenter(steps = 25, stepPx = -20f)
    compose.waitForIdle()
    assertEquals(1, ended)
    compose.onNodeWithText("Una riga").assertIsDisplayed()
  }

  @Test
  fun `col movimento ridotto una riga lasciata torna in un fotogramma`() {
    compose.setContent { Themed(reducedMotion = true) { SwipeRow() } }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("swipe-row").performTouchInput {
      down(center)
      repeat(5) { moveBy(Offset(-20f, 0f)) }
      up()
    }
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    assertEquals(0f, swipe.offset, 0f)
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun `senza movimento ridotto la riga lasciata torna con la molla`() {
    compose.setContent { Themed { SwipeRow() } }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("swipe-row").performTouchInput {
      down(center)
      repeat(5) { moveBy(Offset(-20f, 0f)) }
      up()
    }
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    assertNotEquals(0f, swipe.offset)
    compose.mainClock.advanceTimeBy(2_000)
    assertEquals(0f, swipe.offset, 0f)
    compose.mainClock.autoAdvance = true
  }

  // ---------------------------------------------------------------------------------------------
  // FluidJumpPill, FluidEmptyState con l'azione, FluidStatusBadge a colore libero
  // ---------------------------------------------------------------------------------------------

  @Test
  fun `la pillola compare quando serve, si annuncia senza interrompere e porta al punto`() {
    var visible by mutableStateOf(false)
    var jumps = 0
    compose.setContent {
      Themed { FluidJumpPill(visible = visible, label = "3 nuove in alto", onClick = { jumps++ }) }
    }
    compose.onAllNodesWithText("3 nuove in alto").assertCountEquals(0)
    visible = true
    compose.waitForIdle()
    compose.onNodeWithText("3 nuove in alto")
      .assertIsDisplayed()
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .performClick()
    assertEquals(1, jumps)
  }

  @Test
  fun `col movimento ridotto la pillola compare subito, senza transizione`() {
    var visible by mutableStateOf(false)
    compose.setContent { Themed(reducedMotion = true) { FluidJumpPill(visible = visible, label = "1 nuova", onClick = {}) } }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    compose.runOnIdle {
      visible = true
      Snapshot.sendApplyNotifications()
    }
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithText("1 nuova").assertIsDisplayed()
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun `lo stato vuoto con l'azione ha il suo tasto, quello di sempre nessuno`() {
    var retries = 0
    var withAction by mutableStateOf(false)
    compose.setContent {
      Themed(fontScale = 2f) {
        Column {
          if (withAction) {
            FluidEmptyState(title = "Errore", detail = "Non e' stato possibile.", actionLabel = "Riprova", onAction = { retries++ })
          } else {
            FluidEmptyState(title = "Vuoto", detail = "Niente qui.")
          }
        }
      }
    }
    compose.onAllNodes(hasClickAction()).assertCountEquals(0)
    withAction = true
    compose.waitForIdle()
    compose.onNodeWithText("Riprova").assertIsDisplayed().performClick()
    assertEquals(1, retries)
  }

  @Test
  fun `la pillola di stato a colore libero si disegna accanto a quella a tono`() {
    compose.setContent {
      Themed {
        Column {
          FluidStatusBadge("Lavoro", color = Color(0xFFFFD54F))
          FluidStatusBadge("Casa", color = Color(0xFF1A237E))
          FluidStatusBadge("Tono")
        }
      }
    }
    compose.onNodeWithText("Lavoro").assertIsDisplayed()
    compose.onNodeWithText("Casa").assertIsDisplayed()
    compose.onNodeWithText("Tono").assertIsDisplayed()
  }
}
