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
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPressImmediate
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
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

  /** I granelli della grana (3.0): il segno che il tasto ha seguito un dito che correva. */
  var grains = 0
    private set
  val grainStyles = mutableListOf<FluidHapticGrainStyle>()

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    grains++
    grainStyles += style
  }
}

/**
 * Un pulsante e' LibrePods (3.0), sotto un dito finto dentro un albero Compose vero.
 *
 * La regola, che sostituisce quella della 2.12 ("un pulsante e' di chi lo tiene premuto"): il
 * pulsante e' premuto **al contatto**, senza ritardo, e **non prende mai il gesto**. Se il dito
 * scorre, la pagina (una `LazyColumn` o una `Column` con `verticalScroll`, le due che le app
 * usano) scorre e il pulsante risponde insieme: resta premuto, segue il dito, vibra, e al
 * sollevamento si scioglie senza cliccare — il click e' di `clickable`, e `clickable` annulla se la
 * pagina ha scorso. Tre colpi per tocco con le costanti di sistema: contatto, rilascio, click.
 *
 * Come in `FluidTouchTest`, e' quello che una funzione pura non puo' dire. La logica pura (la
 * trasformazione, il click lungo) sta in `FluidPressTest`.
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

  /** Un pulsante nudo con la sua pressione in mano: il test la legge per sapere quanto e' premuto. */
  private fun probed(
    onPress: (FluidPressState) -> Unit,
    onClick: () -> Unit,
  ): @Composable (Modifier) -> Unit = { modifier ->
    val press = rememberFluidPressState()
    SideEffect { onPress(press) }
    Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = onClick, pressState = press))
  }

  private fun scrolled(list: LazyListState) = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0

  // Un passo ogni 8 ms: un dito che corre. Dalla 3.0 la grana di serie e' quella di LibrePods, un
  // granello per ogni evento sopra sqrt(350) = 18,7 px: i passi qui sotto lo superano, o no, apposta.
  private fun SemanticsNodeInteraction.dragHeld(
    steps: Int,
    step: Offset,
    holdMillis: Long = 150L,
    lift: Boolean = true,
    stepMillis: Long = 8L,
  ) = performTouchInput {
    down(center)
    advanceEventTime(holdMillis)
    repeat(steps) { moveBy(step, delayMillis = stepMillis) }
    if (lift) up()
  }

  // (a) il pulsante risponde mentre la pagina scorre -----------------------------------------------

  @Test
  fun `un dito che parte su un pulsante e trascina in verticale scorre la LazyColumn, e il pulsante risponde insieme`() {
    val list = LazyListState()
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(list, probed({ press = it }, { clicks++ }))

    // Un pollice vero: molto in su, verso il fondo della lista (l'unica direzione in cui scorre),
    // ben oltre lo slop della pagina. Il dito resta giu'.
    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(4f, -30f), lift = false)
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scrolled(list))
    assertTrue("il pulsante si e' sciolto sotto un dito ancora giu': ${press.progress}", press.progress >= 0.96f)
    assertEquals("il pulsante non ha mai colpito: ${haptics.played}", FluidHapticEvent.PressDown, haptics.played.first())
    assertTrue("il pulsante non ha seguito il dito (nessun granello)", haptics.grains > 0)
    assertEquals("e' ancora giu': niente rilascio", listOf(FluidHapticEvent.PressDown), haptics.played)

    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals("la pagina ha scorso: nessun click", 0, clicks)
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
    assertTrue("non si e' sciolta: ${press.progress}", abs(press.progress) < 0.02f)
  }

  @Test
  fun `un FluidButton vero trascinato in diagonale scorre la LazyColumn e non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(10f, -26f))
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scrolled(list))
    assertEquals(0, clicks)
    assertTrue("il pulsante non ha seguito il dito (nessun granello)", haptics.grains > 0)
    assertEquals(FluidHapticEvent.PressDown, haptics.played.first())
    assertEquals(FluidHapticEvent.PressRelease, haptics.played.last())
    assertTrue(FluidHapticEvent.PressClick !in haptics.played)
  }

  @Test
  fun `un pulsante tenuto fermo dentro una lista e' premuto subito e non la fa scorrere`() {
    val list = LazyListState()
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(list, probed({ press = it }, { clicks++ }))

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(300)
    }
    compose.waitForIdle()
    assertTrue("la pressione non e' arrivata: ${press.progress}", press.progress >= 0.96f)
    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)

    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals("un tap tenuto clicca", 1, clicks)
  }

  @Test
  fun `la pressione resta in fondo finche' il dito c'e' anche se la pagina scorre, e si scioglie al rilascio`() {
    val list = LazyListState()
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(list, probed({ press = it }, { clicks++ }))

    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(8f, -22f), lift = false)
    compose.waitForIdle()
    assertTrue("la pressione non e' in fondo: ${press.progress}", press.progress >= 0.96f)

    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertTrue("non si e' sciolta: ${press.progress}", abs(press.progress) < 0.02f)
    assertEquals("rilasciato dopo uno scorrimento: nessun click", 0, clicks)
  }

  @Test
  fun `un pulsante trascinato piano non vibra, e trascinato veloce si', col tick di LibrePods`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    // A passo d'uomo (2 px ogni 16 ms): il tasto segue il dito in silenzio. Il click c'e': il
    // dito resta sul pulsante e la lista, in orizzontale, non parte.
    compose.onNodeWithTag("button").dragHeld(steps = 20, step = Offset(2f, 0f), stepMillis = 16L)
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertEquals("un dito lento non deve suonare", 0, haptics.grains)

    // Lo stesso tasto, un dito che corre (20 px per evento: 400 > 350): un tick per ogni evento.
    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(20f, 0f), stepMillis = 8L)
    compose.waitForIdle()
    assertEquals("un tick per ogni evento sopra la soglia", 12, haptics.grains)
    assertTrue(haptics.grainStyles.all { it == FluidHapticGrainStyle.LibrePods })
  }

  // (b) lo stesso dentro verticalScroll ----------------------------------------------------------

  @Test
  fun `un pulsante trascinato dentro una Column con verticalScroll la scorre, e risponde`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(-4f, -30f))
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scroll.value > 0)
    assertEquals(0, clicks)
    assertTrue(haptics.grains > 0)
    assertEquals(FluidHapticEvent.PressDown, haptics.played.first())
  }

  @Test
  fun `un dito che esce dal pulsante scorrendo verticalScroll non clicca al rilascio`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(0f, -30f))
    compose.waitForIdle()

    assertTrue(scroll.value > 0)
    assertEquals(0, clicks)
  }

  // (c) una passata veloce -------------------------------------------------------------------------

  @Test
  fun `una passata veloce che parte su un pulsante scorre la lista, e il pulsante risponde senza cliccare`() {
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
    // Il pulsante risponde dal contatto, anche se la pagina scorre: tre colpi non sono tre, sono due.
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
    assertTrue("il pulsante non ha seguito il dito", haptics.grains > 0)
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

    // Un gesto del pulsante che non e' uno scorrimento: in orizzontale, la lista sta ferma.
    compose.onNodeWithTag("button").dragHeld(steps = 6, step = Offset(30f, 0f))
    compose.waitForIdle()
    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)

    // Un dito che non e' su un pulsante, lo stesso gesto in verticale: la lista e' di tutti.
    compose.onNodeWithTag("button").performTouchInput {
      down(Offset(centerX, centerY - 150f))
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertTrue("la pagina non scorre piu'", scrolled(list))
  }

  // (d) un tap e' un click solo, e tre colpi -----------------------------------------------------

  @Test
  fun `un tap suona contatto, rilascio e click, in quest'ordine, e niente altro`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )
    assertEquals(0, haptics.grains)
  }

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
  fun `un tap tenuto a lungo clicca una volta sola, senza raddoppiare`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(250)
      up()
    }
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )
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
  fun `un tap parte al contatto e rilascia subito come LibrePods, senza aspettare di arrivare in cima`() {
    lateinit var press: FluidPressState
    var clicks = 0
    compose.mainClock.autoAdvance = false
    showInLazyColumnManual(probed({ press = it }, { clicks++ }))

    compose.onNodeWithTag("button").performTouchInput { down(center) }
    // Tre fotogrammi (48 ms): la molla di LibrePods e' a meta' strada, e il pulsante gia' cresce.
    compose.mainClock.advanceTimeBy(48)
    val atRelease = press.progress
    assertTrue("non e' partita al contatto: $atRelease", atRelease > 0.05f)
    assertTrue("a 48 ms non puo' essere in cima: $atRelease", atRelease < 0.96f)

    compose.onNodeWithTag("button").performTouchInput { up() }
    val seen = List(80) {
      compose.mainClock.advanceTimeByFrame()
      press.progress
    }

    assertEquals(1, clicks)
    // Il rilascio parte dal valore e dalla velocita' che trova: il picco e' quello di una corsa
    // interrotta, sotto l'arrivo, e poi il ritorno scende sotto il riposo.
    assertTrue("ha aspettato di arrivare in cima: ${seen.max()}", seen.max() < 0.96f)
    assertTrue("non e' scesa sotto il riposo: ${seen.min()}", seen.min() < -0.005f)
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
    assertTrue("la pressione non e' partita subito: ${state.progress}", state.progress >= 0.96f)

    compose.onNodeWithTag("button").performMouseInput { release() }
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertTrue("non si e' sciolta: ${state.progress}", abs(state.progress) < 0.02f)
  }

  // Premuto al contatto: in una lista come in una chrome ---------------------------------------------

  /**
   * Un pulsante fuori da qualunque scorrevole, a orologio fermo: la pressione dopo ogni fotogramma
   * che segue il dito sceso. [immediate] e' `LocalFluidPressImmediate`, che dalla 3.0 non conta piu'.
   */
  private fun pressAfterDown(immediate: Boolean, inList: Boolean, frames: Int): List<Float> {
    lateinit var state: FluidPressState
    compose.mainClock.autoAdvance = false
    val button: @Composable (Modifier) -> Unit = { modifier ->
      state = rememberFluidPressState()
      Box(modifier.size(200.dp, 48.dp).fluidPressable(onClick = { }, pressState = state))
    }
    if (inList) {
      compose.setContent {
        Themed {
          CompositionLocalProvider(LocalFluidPressImmediate provides immediate) {
            LazyColumn(Modifier.fillMaxSize()) { item { button(Modifier.testTag("button")) } }
          }
        }
      }
    } else {
      compose.setContent {
        Themed {
          CompositionLocalProvider(LocalFluidPressImmediate provides immediate) {
            button(Modifier.testTag("button"))
          }
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
  fun `il pulsante e' premuto entro tre fotogrammi dal tocco dentro una lista, senza aspettare i 100 ms`() {
    // Dentro una lista la pressione partiva ai 100 ms: qui, a 48 ms dal dito, la molla e' gia' in viaggio.
    val seen = pressAfterDown(immediate = false, inList = true, frames = 3)
    assertTrue("la pressione non e' partita: $seen", seen.last() > 0.05f)
  }

  @Test
  fun `il pulsante e' premuto entro tre fotogrammi dal tocco anche fuori da una lista`() {
    val seen = pressAfterDown(immediate = false, inList = false, frames = 3)
    assertTrue("la pressione non e' partita: $seen", seen.last() > 0.05f)
  }

  @Test
  fun `LocalFluidPressImmediate a vero non cambia piu' niente perche' era gia' cosi'`() {
    val seen = pressAfterDown(immediate = true, inList = true, frames = 3)
    assertTrue("la pressione non e' partita: $seen", seen.last() > 0.05f)
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
    // Contatto, e il colpo del click lungo: quel momento ha il suo, il rilascio non ne aggiunge un altro.
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.GestureStart), haptics.played)
  }

  @Test
  fun `un dito che si muove oltre lo slop non tiene premuto, e al rilascio dentro clicca`() {
    var clicks = 0
    var longClicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      // Largo 312 dp (624 px): il dito puo' fare molta strada e restare sul pulsante, che e' dove
      // `clickable` conta il click. In un tasto di 44 dp lo slop dell'ambiente non si supera da dentro.
      Box(
        modifier.size(312.dp, 48.dp).fluidPressable(
          onClick = { clicks++ },
          onLongClick = { longClicks++ },
          feedback = FluidPressFeedback.Expand,
        ),
      )
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(150)
      // 120 px a destra: ben oltre lo slop, ancora dentro il pulsante.
      moveBy(Offset(120f, 0f))
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
  fun `una pagina che scorre sotto il dito non e' un click lungo`() {
    val list = LazyListState()
    var longClicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassIconButton(onClick = { }, onLongClick = { longClicks++ }, modifier = modifier) { Box(Modifier.size(20.dp)) }
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      repeat(6) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      // Il dito si ferma, la pagina e' ferma, ma ha gia' percorso lo schermo: non tiene premuto.
      advanceEventTime(700)
      up()
    }
    compose.waitForIdle()

    assertTrue(scrolled(list))
    assertEquals(0, longClicks)
  }

  @Test
  fun `il tasto che si trasforma in menu si apre ancora col tenuto premuto, senza click`() {
    lateinit var menu: FluidMorphMenuState
    var clicks = 0
    // Con il suo host (3.0): l'apertura e' sincrona e risolve il pannello con le misure dell'host, e
    // senza un host composto il tasto non apre niente (e non vibra) invece di nascondersi per sempre.
    compose.setContent {
      Themed {
        menu = rememberFluidMorphMenuState()
        val backdrop = currentGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(2) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
            item {
              FluidMorphMenuButton(
                state = menu,
                actions = { listOf(FluidContextAction(label = "Uno", onClick = {})) },
                onClick = { clicks++ },
                modifier = Modifier.padding(horizontal = 24.dp).testTag("button"),
                text = "Menu",
                backdrop = backdrop,
              )
            }
            items(30) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
          }
          FluidMorphMenuHost(state = menu, backdrop = backdrop)
        }
      }
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
    // Il tocco e' il click del tasto, con i tre colpi di ogni tasto (3.0, fase 7): `Open` e' del
    // momento in cui il menu si apre — il tenuto premuto, o il tocco di un tasto che apre al tocco.
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )
  }

  // (f) un tasto di vetro con la pressione condivisa ----------------------------------------------

  @Test
  fun `un tasto di vetro trascinato in verticale scorre la lista, segue il dito e non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(14f, -26f))
    compose.waitForIdle()

    assertTrue("la pagina non ha scorso", scrolled(list))
    assertEquals(0, clicks)
    assertTrue("il tasto di vetro non ha seguito il dito", haptics.grains > 0)
  }

  @Test
  fun `un tap su un tasto di vetro e' un click`() {
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(70)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )
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

  // (g) l'aptica: chi sceglie il suo colpo, chi non vuole niente, chi cede ---------------------------

  @Test
  fun `un pulsante che passa il suo evento non fa PressClick, e uno che passa null non vibra per niente`() {
    var own = 0
    var silent = 0
    showInLazyColumn(LazyListState()) { modifier ->
      Column {
        Box(
          modifier.size(312.dp, 48.dp).fluidPressable(onClick = { own++ }, haptic = FluidHapticEvent.Close, feedback = FluidPressFeedback.Expand),
        )
        Box(
          Modifier.testTag("quiet").size(312.dp, 48.dp).fluidPressable(
            onClick = { silent++ },
            haptic = null,
            feedback = FluidPressFeedback.Expand,
          ),
        )
      }
    }

    compose.onNodeWithTag("button").performTouchInput { down(center); advanceEventTime(60); up() }
    compose.waitForIdle()
    assertEquals(1, own)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.Close),
      haptics.played,
    )

    haptics.played.clear()
    compose.onNodeWithTag("quiet").performTouchInput {
      down(center)
      advanceEventTime(60)
      moveBy(Offset(30f, 0f))
      up()
    }
    compose.waitForIdle()
    assertEquals(1, silent)
    assertTrue("un controllo col suo colpo non vibra: ${haptics.played}", haptics.played.isEmpty())
    assertEquals(0, haptics.grains)
  }

  @Test
  fun `una superficie che cede fa ancora un Tap solo, senza i tre colpi del pulsante`() {
    var clicks = 0
    showInLazyColumn(LazyListState()) { modifier ->
      Box(modifier.size(312.dp, 48.dp).fluidPressable(onClick = { clicks++ }))
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(listOf(FluidHapticEvent.Tap), haptics.played)
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
  fun `Invio su un pulsante col fuoco clicca, con il solo colpo del click`() {
    var clicks = 0
    keyboardAttached = true
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNode(hasClickAction()).requestFocus()
    compose.onNode(hasClickAction()).assertIsFocused()
    compose.onNode(hasClickAction()).performKeyInput { pressKey(Key.Enter) }
    compose.waitForIdle()

    assertEquals(1, clicks)
    // Dalla tastiera non c'e' un contatto ne' un rilascio: c'e' il click.
    assertEquals(listOf(FluidHapticEvent.PressClick), haptics.played)
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
    assertEquals(0, haptics.grains)
    assertTrue("un pulsante spento non deve vibrare: ${haptics.played}", haptics.played.isEmpty())
    assertTrue("un pulsante spento non deve tenere la pagina", scrolled(list))
  }
}
