package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidBarAction
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.Open
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.PressDown
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.PressRelease
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.Tap
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent.Tick
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListRow
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

/** Quello che il tema ha suonato, e quanti granelli della grana: un menu non ne deve far suonare. */
private class MenuHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  var grains = 0
    private set

  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    grains++
  }
}

/**
 * I menu liquidi (3.0, fase 7) sotto un dito finto dentro un albero Compose vero: **il menu si sceglie
 * col dito che l'ha aperto, e un rilascio altrove lo lascia aperto.**
 *
 * Tieni premuto: il menu si apre (un `Open`, uno solo). Trascina su una voce e rilascia: e' la scelta
 * (un `Tick` per voce attraversata, un `Tap` alla scelta). Rilascia senza esserti mosso, o fuori dalle
 * voci, o su una voce spenta: il menu resta aperto, e si sceglie con un tocco. Il congedo non suona
 * niente. Vale per le quattro entrate — il tasto che si trasforma, l'azione della barra (`Expand`), la
 * riga con `contextActions` e `Modifier.fluidContextMenu` — e i pulsanti dell'avviso hanno la stessa
 * goccia e la stessa aptica.
 *
 * Il tasto che si trasforma e l'azione della barra sono tasti LibrePods: il contatto suona il loro
 * `PressDown` (e' del tasto, non del menu), il rilascio dopo un click lungo non suona niente. La riga e
 * la card no: sono superfici, e la traccia e' esattamente quella del menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidMenuHandOffTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val haptics = MenuHaptics()
  private val picked = mutableListOf<String>()
  private var clicks = 0

  private fun actions(disabled: String? = null): List<FluidContextAction> =
    listOf("Uno", "Due", "Tre").map { label ->
      FluidContextAction(label = label, enabled = label != disabled, onClick = { picked += label })
    }

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  /** Il tasto che si trasforma, in basso al centro, col suo host. */
  private lateinit var morph: FluidMorphMenuState

  private fun showMorph(disabled: String? = null) {
    compose.setContent {
      Themed {
        morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidMorphMenuButton(
            state = morph,
            actions = { actions(disabled) },
            onClick = { clicks++ },
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 48.dp)
              .testTag("anchor"),
            text = "Menu",
            backdrop = backdrop,
          )
          FluidMorphMenuHost(state = morph, backdrop = backdrop)
        }
      }
    }
    compose.waitForIdle()
  }

  /**
   * Il tasto che si trasforma dentro una `LazyColumn` (3.0, "il tasto tiene il dito"): e' il caso in
   * cui il pulsante deve tenere il dito *e* passarlo al menu, e la lista non deve muoversi.
   */
  private fun showMorphInList(list: LazyListState) {
    compose.setContent {
      Themed {
        morph = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            items(2) { Box(Modifier.fillMaxWidth().height(120.dp)) }
            item {
              FluidMorphMenuButton(
                state = morph,
                actions = { actions() },
                onClick = { clicks++ },
                modifier = Modifier.padding(horizontal = 24.dp).testTag("anchor"),
                text = "Menu",
                backdrop = backdrop,
              )
            }
            items(30) { Box(Modifier.fillMaxWidth().height(120.dp)) }
          }
          FluidMorphMenuHost(state = morph, backdrop = backdrop)
        }
      }
    }
    compose.waitForIdle()
  }

  /** Un contenuto qualunque dentro l'host dei modali (senza fondale: il ripiego opaco). */
  private fun showInModalHost(content: @Composable () -> Unit) {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
  }

  /** Il dito scende su [tag] e resta fermo ben oltre il click lungo: il menu si apre a dito giu'. */
  private fun hold(tag: String) {
    compose.onNodeWithTag(tag).performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()
  }

  /** Il centro di [text] in coordinate di [tag]: dove portare il dito che e' sceso su [tag]. */
  private fun centerOf(text: String, inside: String): Offset {
    val target = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.center
    val origin = compose.onNodeWithTag(inside).fetchSemanticsNode().boundsInRoot.topLeft
    return target - origin
  }

  /** Porta il dito (sceso su [tag]) su [text] e lo solleva li'. */
  private fun dragTo(text: String, tag: String) {
    val local = centerOf(text, tag)
    compose.onNodeWithTag(tag).performTouchInput {
      moveTo(local)
      up()
    }
    compose.waitForIdle()
  }

  private fun lift(tag: String) {
    compose.onNodeWithTag(tag).performTouchInput { up() }
    compose.waitForIdle()
  }

  // --- Il tasto che si trasforma ---------------------------------------------------------------

  @Test
  fun `tieni, trascina sulla seconda voce e rilascia - scelta una volta, Open Tick Tap`() {
    showMorph()

    hold("anchor")
    assertTrue("il menu non si e' aperto", morph.isOpen)
    dragTo("Due", "anchor")

    assertEquals(listOf("Due"), picked)
    assertEquals("il tocco del tasto non e' un click", 0, clicks)
    assertFalse(morph.isOpen)
    assertEquals(listOf(PressDown, Open, Tick, Tap), haptics.played)
    assertEquals("trascinare sul menu non e' trascinare il tasto: niente grana", 0, haptics.grains)
  }

  @Test
  fun `in una LazyColumn - tieni, trascina sulla seconda voce e rilascia, e la lista resta ferma`() {
    val list = LazyListState()
    showMorphInList(list)

    hold("anchor")
    assertTrue("il menu non si e' aperto", morph.isOpen)
    dragTo("Due", "anchor")

    assertEquals(listOf("Due"), picked)
    assertEquals("il tocco del tasto non e' un click", 0, clicks)
    assertFalse(morph.isOpen)
    assertEquals(listOf(PressDown, Open, Tick, Tap), haptics.played)
    assertEquals("la lista non deve scorrere sotto il menu", 0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
  }

  @Test
  fun `in una LazyColumn - un dito che trascina sul tasto prima del click lungo non apre il menu e non fa scorrere`() {
    val list = LazyListState()
    showMorphInList(list)

    // Veloce e verso l'alto (la direzione in cui la lista scorre): il dito e' del tasto, e non e' un
    // click lungo; si solleva ben fuori dal margine, quindi non e' nemmeno un click.
    compose.onNodeWithTag("anchor").performTouchInput {
      down(center)
      advanceEventTime(40)
      repeat(8) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertFalse("un trascinamento non e' un click lungo", morph.isOpen)
    assertEquals(0, clicks)
    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
    assertEquals(listOf(PressDown, PressRelease), haptics.played)
  }

  @Test
  fun `tieni e rilascia fermo - il menu resta aperto e si sceglie con un tocco, Open Tap`() {
    showMorph()

    hold("anchor")
    lift("anchor")
    assertTrue("rilasciato senza muoversi, il menu deve restare aperto", morph.isOpen)
    assertTrue(picked.isEmpty())
    assertEquals(listOf(PressDown, Open), haptics.played)

    compose.onNodeWithText("Uno").performClick()
    compose.waitForIdle()

    assertEquals(listOf("Uno"), picked)
    assertFalse(morph.isOpen)
    assertEquals(listOf(PressDown, Open, Tap), haptics.played)
  }

  @Test
  fun `trascina fuori dalle voci e rilascia - resta aperto, e un tocco fuori lo chiude in silenzio`() {
    showMorph()

    hold("anchor")
    // Sotto il tasto (nel margine fino al bordo): il pannello sta sopra, li' non c'e' nessuna voce.
    compose.onNodeWithTag("anchor").performTouchInput {
      moveTo(Offset(centerX, height + 40f))
      up()
    }
    compose.waitForIdle()

    assertTrue(morph.isOpen)
    assertTrue(picked.isEmpty())
    assertEquals(listOf(PressDown, Open), haptics.played)

    compose.onRoot().performTouchInput { click(Offset(20f, 20f)) }
    compose.waitForIdle()

    assertFalse(morph.isOpen)
    assertFalse("a ritorno finito il menu non e' piu' in scena", morph.isOnScreen)
    assertTrue(picked.isEmpty())
    assertEquals("il congedo non vibra", listOf(PressDown, Open), haptics.played)
  }

  @Test
  fun `una voce spenta non fa tick e non si sceglie`() {
    showMorph(disabled = "Due")

    hold("anchor")
    dragTo("Due", "anchor")

    assertTrue(picked.isEmpty())
    assertTrue("un rilascio su una voce spenta lascia aperto", morph.isOpen)
    assertEquals(listOf(PressDown, Open), haptics.played)
  }

  @Test
  fun `il back a meta' apertura chiude tornando indietro, senza scelte e senza colpi`() {
    showMorph()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("anchor").performTouchInput { down(center) }
    var waited = 0
    while (!morph.isOpen && waited < 1_500) {
      compose.mainClock.advanceTimeBy(16)
      waited += 16
    }
    assertTrue("il menu non si e' aperto", morph.isOpen)
    // Cento millisecondi di apertura, poi il back.
    compose.mainClock.advanceTimeBy(100)
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.mainClock.advanceTimeByFrame()

    assertFalse(morph.isOpen)
    assertTrue("il ritorno si vede: la sagoma e' ancora in scena", morph.isOnScreen)

    compose.mainClock.advanceTimeBy(1_500)
    compose.onNodeWithTag("anchor").performTouchInput { up() }
    compose.mainClock.advanceTimeBy(100)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    assertFalse(morph.isOnScreen)
    assertTrue(picked.isEmpty())
    assertEquals(listOf(PressDown, Open), haptics.played)
  }

  // --- L'azione della barra (Expand) ----------------------------------------------------------

  @Test
  fun `l'azione della barra si apre sotto il dito, e un rilascio fermo la lascia aperta con un solo Open`() {
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        FluidBarAction(
          icon = Icons.Rounded.Star,
          contentDescription = "Altro",
          onClick = { clicks++ },
          actions = { actions() },
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(16.dp)
            .testTag("bar"),
        )
      }
    }

    hold("bar")
    lift("bar")

    compose.onNodeWithText("Uno").assertExists()
    assertEquals("un Open, uno solo", listOf(PressDown, Open), haptics.played)
    assertEquals(0, clicks)

    compose.onNodeWithText("Tre").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Tre"), picked)
    assertEquals(listOf(PressDown, Open, Tap), haptics.played)
  }

  // --- La riga e la card --------------------------------------------------------------------

  @Test
  fun `una riga in una LazyColumn si sceglie trascinando, e la lista resta ferma`() {
    val list = LazyListState()
    showInModalHost {
      LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
        items(30) { i ->
          FluidListRow(
            title = "Riga $i",
            subtitle = "",
            onClick = { clicks++ },
            contextActions = { actions() },
            modifier = Modifier.testTag("row$i"),
          )
        }
      }
    }

    hold("row1")
    dragTo("Due", "row1")

    assertEquals(listOf("Due"), picked)
    assertEquals(0, clicks)
    assertEquals("la lista non deve scorrere sotto il menu", 0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
    assertEquals(listOf(Open, Tick, Tap), haptics.played)
  }

  @Test
  fun `fluidContextMenu - tieni, rilascia e tocca, Open Tap`() {
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(160.dp)
            .fluidContextMenu(actions = { actions() })
            .testTag("card"),
        )
      }
    }

    hold("card")
    lift("card")
    compose.onNodeWithText("Uno").assertExists()
    assertEquals(listOf(Open), haptics.played)

    compose.onNodeWithText("Uno").performClick()
    compose.waitForIdle()
    assertEquals(listOf("Uno"), picked)
    assertEquals(listOf(Open, Tap), haptics.played)
  }

  @Test
  fun `un tocco fuori chiude il menu contestuale`() {
    showInModalHost {
      Box(Modifier.fillMaxSize()) {
        Box(
          Modifier
            .align(Alignment.Center)
            .size(160.dp)
            .fluidContextMenu(actions = { actions() })
            .testTag("card"),
        )
      }
    }

    hold("card")
    lift("card")
    compose.onNodeWithText("Uno").assertExists()

    compose.onRoot().performTouchInput { click(Offset(10f, 10f)) }
    compose.waitForIdle()

    compose.onNodeWithText("Uno").assertDoesNotExist()
    assertTrue(picked.isEmpty())
    assertEquals(listOf(Open), haptics.played)
  }

  // --- L'avviso nell'host ---------------------------------------------------------------------

  @Test
  fun `l'avviso sta nell'host, senza finestre, ed esce anche dopo che il chiamante l'ha tolto`() {
    var show by mutableStateOf(true)
    showInModalHost {
      if (show) {
        FluidAlert(
          onDismissRequest = { show = false },
          title = "Eliminare la nota?",
          message = "Non si puo' annullare.",
          actions = listOf(
            FluidAlertAction(label = "Annulla", onClick = { show = false }),
            FluidAlertAction(
              label = "Elimina",
              onClick = {
                picked += "Elimina"
                show = false
              },
              emphasis = FluidAlertAction.Emphasis.Destructive,
            ),
          ),
        )
      }
    }

    compose.onNodeWithText("Eliminare la nota?").assertExists()
    compose.onAllNodes(isDialog()).assertCountEquals(0)

    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { show = false }
    compose.mainClock.advanceTimeByFrame()
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithText("Eliminare la nota?").assertExists()

    compose.mainClock.advanceTimeBy(500)
    compose.onAllNodesWithText("Eliminare la nota?").assertCountEquals(0)
    compose.mainClock.autoAdvance = true
  }

  @Test
  fun `un pulsante dell'avviso sceglie con un Tap, poi l'azione`() {
    var show by mutableStateOf(true)
    showInModalHost {
      if (show) {
        FluidAlert(
          onDismissRequest = { show = false },
          title = "Eliminare la nota?",
          actions = listOf(
            FluidAlertAction(label = "Annulla", onClick = { show = false }),
            FluidAlertAction(
              label = "Elimina",
              onClick = {
                picked += "Elimina"
                show = false
              },
            ),
          ),
        )
      }
    }

    compose.onNodeWithText("Elimina").performClick()
    compose.waitForIdle()

    assertEquals(listOf("Elimina"), picked)
    assertEquals(listOf(Tap), haptics.played)
    compose.onNodeWithText("Eliminare la nota?").assertDoesNotExist()
  }
}
