package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPressImmediate
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
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
import kotlin.math.abs

/** Cosa ha suonato il tema durante un gesto. */
private class TickHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  val ticks: Int get() = played.count { it == FluidHapticEvent.DragTick }
}

/**
 * Un pulsante e' di chi lo tiene premuto (2.12.0), sotto un dito finto dentro un albero Compose vero.
 *
 * La regola: una passata veloce che parte su un pulsante e' della pagina; un dito che resta giu'
 * 100 ms senza che la pagina se lo sia preso ha premuto il pulsante, e da li' il gesto e' suo fino
 * al rilascio — segue il dito anche fuori dai suoi bordi, e la pagina sotto (una `LazyColumn` o una
 * `Column` con `verticalScroll`, le due che le app usano) non vede mai un movimento con cui partire.
 * Al rilascio il click scatta se il dito e' dentro il pulsante piu' 16 dp, altrimenti no.
 *
 * Come in `FluidTouchTest`, e' quello che una funzione pura non puo' dire. La logica di possesso
 * pura (tempi, slop, slack) sta in `FluidPressTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidButtonDragTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = TickHaptics()

  /** Vero nei test di tastiera: la sessione parte in modalita' tastiera, dove il fuoco esiste. */
  private var keyboardAttached = false

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  /** Con una tastiera attaccata il fuoco esiste: in modalita' touch un clickable non lo prende. */
  @OptIn(ExperimentalComposeUiApi::class)
  @Composable
  private fun KeyboardMode() {
    val input = LocalInputModeManager.current
    SideEffect { input.requestInputMode(InputMode.Keyboard) }
  }

  /** Una lista lunga con il pulsante nel terzo item: abbastanza in alto da poter scorrere sotto. */
  private fun showInLazyColumn(listState: LazyListState, button: @Composable (Modifier) -> Unit) {
    compose.setContent {
      Themed {
        if (keyboardAttached) KeyboardMode()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
          items(2) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
          item { button(Modifier.padding(horizontal = 24.dp).testTag("button")) }
          items(30) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun showInVerticalScroll(scroll: ScrollState, button: @Composable (Modifier) -> Unit) {
    compose.setContent {
      Themed {
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
          Spacer(Modifier.fillMaxWidth().height(240.dp))
          button(Modifier.padding(horizontal = 24.dp).testTag("button"))
          Spacer(Modifier.fillMaxWidth().height(2_000.dp))
        }
      }
    }
    compose.waitForIdle()
  }

  /** Un pulsante vero, largo quanto la riga: 48 dp di altezza, 312 di larghezza. */
  private fun counting(onClick: () -> Unit): @Composable (Modifier) -> Unit = { modifier ->
    FluidButton(text = "Vai", onClick = onClick, modifier = modifier, fillWidth = true)
  }

  private fun scrolled(list: LazyListState) = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0

  private fun SemanticsNodeInteraction.dragHeld(
    steps: Int,
    step: Offset,
    holdMillis: Long = 150L,
    lift: Boolean = true,
  ) = performTouchInput {
    down(center)
    advanceEventTime(holdMillis)
    repeat(steps) { moveBy(step) }
    if (lift) up()
  }

  // (a) FluidButton in una LazyColumn ------------------------------------------------------------

  @Test
  fun `un pulsante tenuto e trascinato in diagonale non fa scorrere la LazyColumn, e al rilascio dentro clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    // Un pollice vero: un po' di lato e molto in su (verso il fondo della lista, l'unica direzione
    // in cui scorrerebbe), ben oltre lo slop della pagina (16 px). Finisce 24 px sopra il bordo del
    // pulsante (alto 96 px) ma dentro i 32 di slack: il rilascio e' ancora un click.
    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(10f, -9f))
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemIndex)
    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemScrollOffset)
    assertEquals("un click, al rilascio", 1, clicks)
    assertTrue("il pulsante non ha seguito il dito (nessuna tacca)", haptics.ticks > 0)
  }

  @Test
  fun `il pulsante segue il dito fuori dai suoi bordi senza far scorrere la pagina, e al rilascio fuori non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    // Quasi duecento pixel in su: fuori dal pulsante, dalla sua area di tocco e dallo slack.
    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(6f, -18f))
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemIndex)
    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemScrollOffset)
    assertEquals("rilasciato fuori, nessun click", 0, clicks)
    assertTrue("il pulsante ha smesso di seguire il dito", haptics.ticks > 0)
  }

  @Test
  fun `la pressione resta in fondo finche' il dito c'e' e si scioglie al rilascio`() {
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      press = rememberFluidPressState()
      Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = { clicks++ }, pressState = press))
    }

    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(8f, -22f), lift = false)
    compose.waitForIdle()
    assertTrue("la pressione non e' in fondo: ${press.progress}", press.progress >= 0.96f)

    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertTrue("non si e' sciolta: ${press.progress}", abs(press.progress) < 0.02f)
    assertEquals("rilasciato lontano: nessun click", 0, clicks)
  }

  // (b) lo stesso dentro verticalScroll ----------------------------------------------------------

  @Test
  fun `un pulsante tenuto e trascinato non fa scorrere una Column con verticalScroll`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(-14f, -6f))
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, scroll.value)
    assertEquals(1, clicks)
    assertTrue(haptics.ticks > 0)
  }

  @Test
  fun `in una Column con verticalScroll il pulsante segue il dito lontano e non clicca`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(0f, -30f))
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, scroll.value)
    assertEquals(0, clicks)
  }

  // (c) una passata veloce e' della pagina -------------------------------------------------------

  @Test
  fun `una passata veloce che parte su un pulsante scorre la lista, senza pressione e senza click`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      // Oltre lo slop in meno di 40 ms, e poi avanti: un flick.
      moveBy(Offset(0f, -30f), delayMillis = 16)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scrolled(list))
    assertEquals("un flick non e' un click", 0, clicks)
    assertEquals("il pulsante non doveva muoversi: ${haptics.played}", 0, haptics.ticks)
  }

  @Test
  fun `una passata che parte su un pulsante di una Column con verticalScroll la scorre`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scroll.value > 0)
    assertEquals(0, clicks)
  }

  @Test
  fun `dopo un gesto del pulsante la pagina scorre ancora`() {
    val list = LazyListState()
    showInLazyColumn(list, counting { })

    compose.onNodeWithTag("button").dragHeld(steps = 6, step = Offset(0f, -30f))
    compose.waitForIdle()
    assertEquals(0, list.firstVisibleItemIndex)

    // Un dito che non e' su un pulsante, lo stesso gesto: la lista e' di nuovo di tutti.
    compose.onNodeWithTag("button").performTouchInput {
      down(Offset(centerX, centerY - 150f))
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertTrue("la pagina non scorre piu'", scrolled(list))
  }

  // (d) un tap e' un click solo -------------------------------------------------------------------

  @Test
  fun `un tap rapido in una lista clicca una volta sola`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      up()
    }
    compose.waitForIdle()
    assertEquals(1, clicks)

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
    assertEquals(2, clicks)
  }

  @Test
  fun `un tap tenuto oltre il possesso clicca una volta sola, senza raddoppiare`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(250)
      up()
    }
    compose.waitForIdle()
    assertEquals(1, clicks)
  }

  @Test
  fun `un secondo dito sullo stesso pulsante non fa un secondo click`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(0, center)
      advanceEventTime(150)
      down(1, center + Offset(20f, 0f))
      advanceEventTime(30)
      up(1)
      advanceEventTime(30)
      up(0)
    }
    compose.waitForIdle()
    assertEquals(1, clicks)
  }

  @Test
  fun `un tap rapido mostra la pressione intera`() {
    lateinit var press: FluidPressState
    var clicks = 0
    compose.mainClock.autoAdvance = false
    showInLazyColumnManual { modifier ->
      press = rememberFluidPressState()
      Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = { clicks++ }, pressState = press))
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      up()
    }
    val seen = List(50) {
      compose.mainClock.advanceTimeByFrame()
      press.progress
    }

    assertEquals(1, clicks)
    assertTrue("la pressione si e' fermata a ${seen.max()}", seen.max() >= 0.96f)
    assertTrue("non e' tornata a riposo: ${seen.last()}", abs(seen.last()) < 0.02f)
  }

  /** Come [showInLazyColumn], ma con l'orologio fermo: i fotogrammi li fa passare il test. */
  private fun showInLazyColumnManual(button: @Composable (Modifier) -> Unit) {
    compose.setContent {
      Themed {
        LazyColumn(Modifier.fillMaxSize()) {
          items(2) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
          item { button(Modifier.padding(horizontal = 24.dp).testTag("button")) }
          items(30) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
        }
      }
    }
    compose.mainClock.advanceTimeByFrame()
  }

  @Test
  fun `con un mouse il pulsante e' premuto subito e clicca una volta sola`() {
    lateinit var state: FluidPressState
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      state = rememberFluidPressState()
      Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = { clicks++ }, pressState = state))
    }

    compose.onNodeWithTag("button").performMouseInput {
      moveTo(center)
      press()
    }
    compose.waitForIdle()
    // Niente attesa dei 100 ms: con un mouse non c'e' un trascinamento che la pagina deva poter prendere.
    assertTrue("la pressione non e' partita subito: ${state.progress}", state.progress >= 0.96f)

    compose.onNodeWithTag("button").performMouseInput { release() }
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertTrue("non si e' sciolta: ${state.progress}", abs(state.progress) < 0.02f)
  }

  // La chrome, dove non scorre niente: premuto al tocco ------------------------------------------

  /**
   * Un pulsante fuori da qualunque scorrevole, a orologio fermo: la pressione dopo ogni fotogramma
   * che segue il dito sceso. [immediate] e' `LocalFluidPressImmediate`.
   */
  private fun pressAfterDown(immediate: Boolean, frames: Int): List<Float> {
    lateinit var state: FluidPressState
    compose.mainClock.autoAdvance = false
    compose.setContent {
      Themed {
        CompositionLocalProvider(LocalFluidPressImmediate provides immediate) {
          state = rememberFluidPressState()
          Box(Modifier.size(200.dp, 48.dp).testTag("button").fluidPressable(onClick = { }, pressState = state))
        }
      }
    }
    compose.mainClock.advanceTimeByFrame()

    compose.onNodeWithTag("button").performTouchInput { down(center) }
    return List(frames) {
      compose.mainClock.advanceTimeByFrame()
      state.progress
    }
  }

  @Test
  fun `in una chrome che lo chiede il pulsante e' premuto entro un fotogramma dal tocco`() {
    // Due fotogrammi (32 ms) dopo il dito sceso: ben prima dei 100 ms.
    val seen = pressAfterDown(immediate = true, frames = 2)
    assertTrue("la pressione non e' partita: $seen", seen.last() > 0.01f)
  }

  @Test
  fun `di default il pulsante aspetta i 100 ms prima di essere premuto, poi lo e'`() {
    val seen = pressAfterDown(immediate = false, frames = 14)
    // Fino a 80 ms dal tocco non si sa ancora se e' uno scorrimento: nessuna pressione.
    assertEquals("premuto prima dei 100 ms: ${seen.take(5)}", 0f, seen.take(5).max(), 0f)
    assertTrue("non e' mai partita: $seen", seen.max() > 0.5f)
  }

  // (e) la pressione lunga ------------------------------------------------------------------------

  @Test
  fun `un tasto di vetro tenuto fermo fa il click lungo e non il click`() {
    var clicks = 0
    var longClicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      FluidGlassIconButton(
        onClick = { clicks++ },
        onLongClick = { longClicks++ },
        modifier = modifier,
      ) { Box(Modifier.size(20.dp)) }
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(600)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, longClicks)
    assertEquals("dopo il click lungo non c'e' click al rilascio", 0, clicks)
  }

  @Test
  fun `un dito che si muove oltre lo slop non tiene premuto, e al rilascio dentro clicca`() {
    var clicks = 0
    var longClicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      FluidGlassIconButton(
        onClick = { clicks++ },
        onLongClick = { longClicks++ },
        modifier = modifier,
      ) { Box(Modifier.size(20.dp)) }
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(150)
      // 50 px a destra: oltre lo slop (32 px in questo ambiente), dentro i 44 dp (88 px) del tasto
      // piu' lo slack.
      moveBy(Offset(50f, 0f))
      advanceEventTime(600)
      up()
    }
    compose.waitForIdle()

    assertEquals("un dito che trascina non sta tenendo premuto", 0, longClicks)
    assertEquals(1, clicks)
  }

  @Test
  fun `un tap rapido su un tasto con click lungo e' un click`() {
    var clicks = 0
    var longClicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      FluidGlassIconButton(
        onClick = { clicks++ },
        onLongClick = { longClicks++ },
        modifier = modifier,
      ) { Box(Modifier.size(20.dp)) }
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(80)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(0, longClicks)
  }

  @Test
  fun `il tasto che si trasforma in menu si apre ancora col tenuto premuto, senza click`() {
    lateinit var menu: FluidMorphMenuState
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      menu = rememberFluidMorphMenuState()
      FluidMorphMenuButton(
        state = menu,
        actions = { listOf(FluidContextAction(label = "Uno", onClick = {})) },
        onClick = { clicks++ },
        modifier = modifier,
        text = "Menu",
        backdrop = currentGlassBackdrop(),
      )
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(700)
      up()
    }
    compose.waitForIdle()

    assertTrue("il menu non si e' aperto", menu.isOpen)
    assertEquals(0, clicks)
  }

  @Test
  fun `un tap sul tasto che si trasforma in menu e' il suo click, e non apre il menu`() {
    lateinit var menu: FluidMorphMenuState
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      menu = rememberFluidMorphMenuState()
      FluidMorphMenuButton(
        state = menu,
        actions = { listOf(FluidContextAction(label = "Uno", onClick = {})) },
        onClick = { clicks++ },
        modifier = modifier,
        text = "Menu",
        backdrop = currentGlassBackdrop(),
      )
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(90)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertTrue(!menu.isOpen)
  }

  // (f) un tasto di vetro con la pressione condivisa ----------------------------------------------

  @Test
  fun `un tasto di vetro tenuto e trascinato non fa scorrere la lista, segue il dito e al rilascio dentro clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    // Il tasto di vetro e' alto 48 dp (96 px): 8 passi da 6 px in su restano dentro.
    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(14f, -6f))
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemIndex)
    assertEquals("la pagina ha preso il gesto", 0, list.firstVisibleItemScrollOffset)
    assertEquals(1, clicks)
    assertTrue("il tasto di vetro non ha seguito il dito", haptics.ticks > 0)
  }

  @Test
  fun `un tasto di vetro rilasciato lontano dal dito che lo ha trascinato non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(0f, -24f))
    compose.waitForIdle()

    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
    assertEquals(0, clicks)
  }

  @Test
  fun `una passata veloce su un tasto di vetro scorre la lista`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue(scrolled(list))
    assertEquals(0, clicks)
  }

  // Quello che non e' il puntatore e non deve essersi perso ---------------------------------------

  @Test
  fun `la tastiera, TalkBack e il click lungo di TalkBack arrivano ancora`() {
    var clicks = 0
    var longClicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      FluidGlassIconButton(
        onClick = { clicks++ },
        onLongClick = { longClicks++ },
        modifier = modifier,
      ) { Box(Modifier.size(20.dp)) }
    }

    compose.onNode(hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    assertEquals(1, clicks)
    compose.onNode(hasClickAction()).performSemanticsAction(SemanticsActions.OnLongClick)
    assertEquals(1, longClicks)
    assertEquals(1, clicks)
  }

  @Test
  fun `Invio su un pulsante col fuoco clicca`() {
    var clicks = 0
    keyboardAttached = true
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNode(hasClickAction()).requestFocus()
    compose.onNode(hasClickAction()).assertIsFocused()
    compose.onNode(hasClickAction()).performKeyInput { pressKey(Key.Enter) }
    compose.waitForIdle()

    assertEquals(1, clicks)
  }

  @Test
  fun `Invio su una superficie che cede col fuoco clicca ancora`() {
    var clicks = 0
    keyboardAttached = true
    showInLazyColumn(LazyListState()) { modifier ->
      Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = { clicks++ }))
    }

    compose.onNode(hasClickAction()).requestFocus()
    compose.onNode(hasClickAction()).assertIsFocused()
    compose.onNode(hasClickAction()).performKeyInput { pressKey(Key.Enter) }
    compose.waitForIdle()

    assertEquals(1, clicks)
  }

  @Test
  fun `un pulsante disabilitato non fa niente e la lista scorre`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidButton(text = "Vai", onClick = { clicks++ }, modifier = modifier, fillWidth = true, enabled = false)
    }

    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(0f, -40f))
    compose.waitForIdle()

    assertEquals(0, clicks)
    assertEquals(0, haptics.ticks)
    assertTrue("un pulsante spento non deve tenere la pagina", scrolled(list))
  }
}
