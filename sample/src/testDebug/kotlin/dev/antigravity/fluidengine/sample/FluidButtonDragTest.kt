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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPressImmediate
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.page.FluidBackSwipe
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidListRow
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
 * Un pulsante e' LibrePods (3.0) e tiene il dito, sotto un dito finto dentro un albero Compose vero.
 *
 * La regola, scelta in mano da Alessio ("il tasto tiene il dito"): il pulsante e' premuto **al
 * contatto**, senza ritardo, e un dito che scende su di lui e' **suo fino al sollevamento**. La pagina
 * attorno (una `LazyColumn`, una `Column` con `verticalScroll`, un pager) non scorre mentre quel dito
 * e' giu': il pulsante cresce, segue il dito e vibra, e per scorrere si parte fuori dai pulsanti. Al
 * sollevamento il click scatta se il dito e' sul pulsante piu' un margine di 16 dp, **anche dopo un
 * trascinamento** (e' il gesto a cliccare, perche' `clickable` annulla alla vista dei suoi consumi);
 * fuori dal margine si scioglie e basta. Tre colpi per tocco con le costanti di sistema: contatto,
 * rilascio, click.
 *
 * Le due eccezioni sono volute. Una superficie che cede (una riga, una card) non consuma niente e la
 * lista scorre se il dito parte da lei. Un chip sta in una fila che scorre e non ha un fuori da cui
 * partire: cede il dito alla fila (`FluidPressGrip.YieldsToScroll`), si scioglie e non clicca.
 *
 * Come in `FluidTouchTest`, e' quello che una funzione pura non puo' dire. La logica pura (la
 * trasformazione, il click lungo, il possesso del dito) sta in `FluidPressTest`.
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
  fun `un dito che parte su un pulsante e trascina in verticale non scorre la LazyColumn, e il pulsante lo segue`() {
    val list = LazyListState()
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(list, probed({ press = it }, { clicks++ }))

    // Un pollice vero: molto in su, verso il fondo della lista (l'unica direzione in cui scorre),
    // ben oltre lo slop della pagina. Il dito resta giu'.
    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(4f, -30f), lift = false)
    compose.waitForIdle()

    assertTrue("la pagina ha scorso sotto un dito che era di un pulsante", !scrolled(list))
    assertEquals(0, list.firstVisibleItemIndex)
    assertEquals(0, list.firstVisibleItemScrollOffset)
    assertTrue("il pulsante si e' sciolto sotto un dito ancora giu': ${press.progress}", press.progress >= 0.96f)
    assertEquals("il pulsante non ha mai colpito: ${haptics.played}", FluidHapticEvent.PressDown, haptics.played.first())
    assertTrue("il pulsante non ha seguito il dito (nessun granello)", haptics.grains > 0)
    assertEquals("e' ancora giu': niente rilascio", listOf(FluidHapticEvent.PressDown), haptics.played)

    // Il dito si solleva 250 px sopra il pulsante: ben oltre il margine, quindi nessun click.
    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertEquals("sollevato lontano dal pulsante: nessun click", 0, clicks)
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
    assertTrue("non si e' sciolta: ${press.progress}", abs(press.progress) < 0.02f)
    assertTrue("la pagina ha scorso al rilascio", !scrolled(list))
  }

  @Test
  fun `un FluidButton vero trascinato in diagonale non scorre la LazyColumn e non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(10f, -26f))
    compose.waitForIdle()

    assertTrue("la pagina ha scorso", !scrolled(list))
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
  fun `la pressione resta in fondo finche' il dito c'e' e la pagina sta ferma, e si scioglie al rilascio`() {
    val list = LazyListState()
    lateinit var press: FluidPressState
    var clicks = 0
    showInLazyColumn(list, probed({ press = it }, { clicks++ }))

    compose.onNodeWithTag("button").dragHeld(steps = 10, step = Offset(8f, -22f), lift = false)
    compose.waitForIdle()
    assertTrue("la pressione non e' in fondo: ${press.progress}", press.progress >= 0.96f)
    assertTrue("la pagina ha scorso", !scrolled(list))

    compose.onNodeWithTag("button").performTouchInput { up() }
    compose.waitForIdle()
    assertTrue("non si e' sciolta: ${press.progress}", abs(press.progress) < 0.02f)
    assertEquals("rilasciato lontano dal pulsante: nessun click", 0, clicks)
  }

  @Test
  fun `un pulsante trascinato piano non vibra, e trascinato veloce si', col tick di LibrePods`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    // A passo d'uomo (2 px ogni 16 ms): il tasto segue il dito in silenzio. Il click c'e': il
    // dito resta sul pulsante e si solleva li' (il gesto, che ha consumato, clicca).
    compose.onNodeWithTag("button").dragHeld(steps = 20, step = Offset(2f, 0f), stepMillis = 16L)
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertEquals("un dito lento non deve suonare", 0, haptics.grains)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )

    // Lo stesso tasto, un dito che corre (20 px per evento: 400 > 350): un tick per ogni evento.
    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(20f, 0f), stepMillis = 8L)
    compose.waitForIdle()
    assertEquals("un tick per ogni evento sopra la soglia", 12, haptics.grains)
    assertTrue(haptics.grainStyles.all { it == FluidHapticGrainStyle.LibrePods })
  }

  // (b) lo stesso dentro verticalScroll ----------------------------------------------------------

  @Test
  fun `un pulsante trascinato dentro una Column con verticalScroll non la scorre, e risponde`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(-4f, -30f))
    compose.waitForIdle()

    assertEquals("la pagina ha scorso sotto un dito che era di un pulsante", 0, scroll.value)
    assertEquals(0, clicks)
    assertTrue(haptics.grains > 0)
    assertEquals(FluidHapticEvent.PressDown, haptics.played.first())
  }

  @Test
  fun `un dito che esce dal pulsante trascinando non clicca al rilascio, e verticalScroll sta fermo`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").dragHeld(steps = 12, step = Offset(0f, -30f))
    compose.waitForIdle()

    assertEquals(0, scroll.value)
    assertEquals(0, clicks)
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
  }

  // (c) una passata veloce -------------------------------------------------------------------------

  @Test
  fun `una passata veloce che parte su un pulsante non scorre la lista, e il pulsante risponde senza cliccare`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      // Oltre lo slop in meno di 40 ms, e poi avanti: un flick. Parte da un pulsante, ed e' del pulsante.
      moveBy(Offset(0f, -30f), delayMillis = 16)
      moveBy(Offset(0f, -30f), delayMillis = 16)
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la pagina ha scorso sotto un flick partito da un pulsante", !scrolled(list))
    assertEquals("un flick lontano dal pulsante non e' un click", 0, clicks)
    // Il pulsante risponde dal contatto: tre colpi non sono tre, sono due.
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
    assertTrue("il pulsante non ha seguito il dito", haptics.grains > 0)
  }

  @Test
  fun `una passata che parte su un pulsante di una Column con verticalScroll non la scorre`() {
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

    assertEquals("la pagina ha scorso sotto un flick partito da un pulsante", 0, scroll.value)
    assertEquals(0, clicks)
  }

  @Test
  fun `dopo un gesto del pulsante la pagina scorre ancora, partendo fuori dai pulsanti`() {
    val list = LazyListState()
    showInLazyColumn(list, counting { })

    // Un gesto del pulsante: la lista sta ferma, anche in orizzontale.
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

  // Il click dopo un trascinamento, e il margine -----------------------------------------------------
  //
  // Il pulsante che tiene il dito consuma, e `clickable` annulla alla vista di un cambio consumato: il
  // click di un dito che ha trascinato e' del gesto, con lo stesso `PressClick` di un tocco.

  private val tapSequence = listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick)

  @Test
  fun `un dito che trascina e si solleva sul pulsante in una LazyColumn clicca una volta sola, con PressClick, e la lista sta ferma`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    // 24 px in su e 40 a destra: dentro il pulsante (96 px di altezza), ben oltre il tremito e lo slop.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(120)
      moveBy(Offset(0f, -12f), delayMillis = 16)
      moveBy(Offset(20f, -12f), delayMillis = 16)
      moveBy(Offset(20f, 0f), delayMillis = 16)
      up()
    }
    compose.waitForIdle()

    assertEquals("un trascinamento che finisce sul pulsante clicca, una volta sola", 1, clicks)
    assertEquals(tapSequence, haptics.played)
    assertTrue("la pagina ha scorso", !scrolled(list))
  }

  @Test
  fun `un dito che trascina e si solleva sul pulsante in una Column con verticalScroll clicca una volta sola`() {
    val scroll = ScrollState(0)
    var clicks = 0
    showInVerticalScroll(scroll, counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(120)
      repeat(4) { moveBy(Offset(0f, -8f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
    assertEquals("la pagina ha scorso", 0, scroll.value)
  }

  @Test
  fun `un dito che vaga fuori dal pulsante e torna a sollevarsi sopra clicca, e conta solo dove si solleva`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(100)
      // Fuori dal margine, in alto e a sinistra, e poi di nuovo sul pulsante.
      moveTo(Offset(-30f, -150f), delayMillis = 16)
      moveTo(Offset(10f, -60f), delayMillis = 16)
      moveTo(Offset(centerX + 30f, centerY), delayMillis = 16)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
  }

  @Test
  fun `il click vale fino a 16 dp oltre il bordo, e oltre il margine il pulsante si scioglie e basta`() {
    var clicks = 0
    showInLazyColumn(LazyListState(), counting { clicks++ })

    // Il pulsante e' alto 96 px (48 dp, a 2 px per dp): il margine e' 32 px sotto il bordo.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(100)
      moveTo(Offset(centerX, height + 26f), delayMillis = 16)
      up()
    }
    compose.waitForIdle()
    assertEquals("a 13 dp dal bordo e' ancora un click", 1, clicks)
    assertEquals(tapSequence, haptics.played)

    haptics.played.clear()
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(100)
      moveTo(Offset(centerX, height + 40f), delayMillis = 16)
      up()
    }
    compose.waitForIdle()
    assertEquals("a 20 dp dal bordo non e' un click", 1, clicks)
    assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease), haptics.played)
  }

  @Test
  fun `un tremito di un tocco vero e' ancora il click di clickable, uno solo`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    // 3 px: sotto meta' dello slop (8 px a xhdpi). Il gesto non ha consumato niente, e clicca `clickable`.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(50)
      moveBy(Offset(2f, 1f), delayMillis = 16)
      moveBy(Offset(-1f, 1f), delayMillis = 16)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
    assertTrue(!scrolled(list))
  }

  @Test
  fun `con un mouse un trascinamento sul pulsante e il rilascio sopra e' un click solo`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list, counting { clicks++ })

    compose.onNodeWithTag("button").performMouseInput {
      moveTo(center)
      press()
      moveBy(Offset(0f, -10f))
      moveBy(Offset(20f, 0f))
      release()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertTrue(!scrolled(list))
  }

  @Test
  fun `un pulsante dentro un pager non lo sfoglia, e partendo fuori dal pulsante si`() {
    val pager = PagerState { 3 }
    var clicks = 0
    compose.setContent {
      Themed {
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
          Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.fillMaxWidth().height(200.dp).testTag("free$page"))
            FluidButton(
              text = "Vai",
              onClick = { clicks++ },
              modifier = Modifier.padding(horizontal = 24.dp).testTag("button$page"),
              fillWidth = true,
            )
          }
        }
      }
    }
    compose.waitForIdle()

    // Da un pulsante, in orizzontale e a lungo: il pager sta fermo.
    compose.onNodeWithTag("button0").performTouchInput {
      down(center)
      advanceEventTime(100)
      // 350 px a sinistra: oltre il margine del pulsante (312 + 32), e ancora dentro lo schermo.
      repeat(7) { moveBy(Offset(-50f, 0f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertEquals("il pager ha girato sotto un dito che era di un pulsante", 0, pager.currentPage)
    assertEquals(0f, pager.currentPageOffsetFraction, 0.0001f)
    assertEquals("sollevato fuori dal margine, nessun click", 0, clicks)

    // Dal vuoto sopra il pulsante: il pager gira.
    compose.onNodeWithTag("free0").performTouchInput {
      down(center)
      repeat(8) { moveBy(Offset(-60f, 0f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertTrue("il pager non gira piu'", pager.currentPage > 0)
  }

  @Test
  fun `un pulsante in una pagina non porta indietro col suo trascinamento, e partendo fuori dal pulsante si`() {
    var clicks = 0
    lateinit var nav: NavHostController
    compose.setContent {
      Themed {
        val controller = rememberFluidNavController()
        nav = controller
        FluidNavHost(
          navController = controller,
          startDestination = "a",
          modifier = Modifier.fillMaxSize(),
          state = rememberFluidPageHostState(),
        ) {
          composable("a") {
            FluidScreen(title = "Radice") { item { Spacer(Modifier.fillMaxWidth().height(40.dp)) } }
          }
          // `Anywhere`: da 3.0.3 di serie si tira dal bordo, e qui si tiene fermo il "da qualunque punto".
          composable("b", backSwipe = FluidBackSwipe.Anywhere) {
            FluidScreen(title = "Pagina", onBack = { controller.popBackStack() }) {
              item {
                FluidButton(
                  text = "Vai",
                  onClick = { clicks++ },
                  modifier = Modifier.padding(24.dp).testTag("button"),
                  fillWidth = true,
                )
              }
              item { Spacer(Modifier.fillMaxWidth().height(500.dp).testTag("free")) }
            }
          }
        }
      }
    }
    compose.waitForIdle()
    compose.runOnIdle { nav.navigate("b") }
    compose.waitForIdle()
    val route = { compose.runOnIdle { nav.currentBackStackEntry?.destination?.route } }
    assertEquals("b", route())

    // Dal pulsante verso destra, la direzione del back, a lungo: la pagina resta dov'e'. Il dito si
    // solleva oltre il margine del pulsante (largo 544 px, e 32 di margine), quindi nemmeno un click.
    // Nota: `clickable` consuma gia' il down del pulsante, e il riconoscitore del back legge un down
    // consumato come "un figlio se l'e' preso": da un pulsante il back non parte nemmeno senza il
    // possesso del dito. Il test lo tiene fermo, e tiene fermo che dal vuoto invece parte.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(100)
      repeat(7) { moveBy(Offset(50f, 0f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertEquals("un trascinamento partito da un pulsante ha portato indietro la pagina", "b", route())
    assertEquals(0, clicks)

    // Lo stesso dal vuoto della pagina: il back scorre.
    compose.onNodeWithTag("free").performTouchInput {
      down(Offset(60f, 100f))
      repeat(30) { moveBy(Offset(12f, 0f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()
    assertEquals("dal vuoto della pagina il back non e' partito", "a", route())
  }

  // Le superfici cedono, i chip si sciolgono -----------------------------------------------------------

  @Test
  fun `una riga in una LazyColumn scorre ancora la lista, partendo da lei`() {
    val list = LazyListState()
    var clicks = 0
    compose.setContent {
      Themed {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
          items(40) { i ->
            FluidListRow(
              title = "Riga $i",
              subtitle = "",
              onClick = { clicks++ },
              modifier = Modifier.testTag("row$i"),
            )
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("row2").performTouchInput {
      down(center)
      advanceEventTime(30)
      repeat(10) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la lista non scorre piu' da una riga", scrolled(list))
    assertEquals("una riga che ha fatto scorrere la lista non clicca", 0, clicks)
    assertTrue("una riga non e' un pulsante: niente colpi del pulsante", FluidHapticEvent.PressDown !in haptics.played)
  }

  @Test
  fun `un chip in una LazyRow che scorre si scioglie, smette di seguire il dito e non clicca`() {
    val row = LazyListState()
    var clicks = 0
    showChipRow(row) { clicks++ }

    // Il dito parte su un chip e va in orizzontale, nel verso in cui la fila scorre, e resta giu'.
    compose.onNodeWithTag("chip1").performTouchInput {
      down(center)
      advanceEventTime(50)
      repeat(8) { moveBy(Offset(-30f, 0f), delayMillis = 16) }
    }
    compose.waitForIdle()

    assertTrue("la fila non ha scorso", scrolled(row))
    assertEquals(
      "il chip si e' sciolto appena la fila ha preso il dito, a dito ancora giu'",
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease),
      haptics.played,
    )
    val grainsWhenReleased = haptics.grains

    // Il dito continua (il chip, intanto, e' scorso via con la fila), e il chip non lo segue piu'.
    compose.onRoot().performTouchInput {
      repeat(6) { moveBy(Offset(-30f, 0f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertEquals("un chip sciolto non vibra piu' sotto il dito", grainsWhenReleased, haptics.grains)
    assertEquals("un chip sciolto non vibra al sollevamento", 2, haptics.played.size)
    assertEquals("la fila ha scorso: nessun click", 0, clicks)
  }

  @Test
  fun `un tocco su un chip e' un click solo, con i tre colpi`() {
    var clicks = 0
    showChipRow(LazyListState()) { clicks++ }

    compose.onNodeWithTag("chip1").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
  }

  @Test
  fun `un chip trascinato dove la fila non scorre segue il dito e clicca al rilascio sopra`() {
    val row = LazyListState()
    var clicks = 0
    showChipRow(row) { clicks++ }

    // In verticale una LazyRow non scorre: nessuno prende il dito, e il chip risponde come sempre.
    compose.onNodeWithTag("chip1").performTouchInput {
      down(center)
      advanceEventTime(60)
      repeat(3) { moveBy(Offset(0f, -4f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la fila non doveva scorrere", !scrolled(row))
    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
  }

  /** Una fila di chip che scorre in orizzontale: sono piu' larghi dello schermo, quindi scorre. */
  private fun showChipRow(row: LazyListState, onClick: () -> Unit) {
    compose.setContent {
      Themed {
        LazyRow(state = row, modifier = Modifier.fillMaxWidth().height(120.dp)) {
          items(12) { i ->
            FluidChip(
              label = "Filtro numero $i",
              selected = false,
              onClick = onClick,
              modifier = Modifier.padding(horizontal = 4.dp).testTag("chip$i"),
            )
          }
        }
      }
    }
    compose.waitForIdle()
  }

  // Il tasto del menu, vecchio e nuovo -------------------------------------------------------------------

  @Suppress("DEPRECATION")
  @Test
  fun `il vecchio tasto del menu tiene il dito come gli altri, e un tocco clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      dev.antigravity.fluidengine.ui.fluid.FluidGlassMenuButton(
        actions = { listOf(FluidContextAction(label = "Uno", onClick = {})) },
        onClick = { clicks++ },
        modifier = modifier,
        text = "Menu",
        backdrop = currentGlassBackdrop(),
      )
    }

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(0f, -30f))
    compose.waitForIdle()
    assertTrue("la pagina ha scorso sotto un dito che era di un tasto", !scrolled(list))
    assertEquals(0, clicks)

    haptics.played.clear()
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      up()
    }
    compose.waitForIdle()
    assertEquals(1, clicks)
    assertEquals(tapSequence, haptics.played)
  }

  @Test
  fun `il tasto che si trasforma in menu tiene il dito in una lista, e un trascinamento sopra clicca`() {
    val list = LazyListState()
    lateinit var menu: FluidMorphMenuState
    var clicks = 0
    showInLazyColumn(list) { modifier ->
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

    // Veloce: non e' un click lungo. Il dito resta sul tasto, la lista sta ferma.
    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60)
      repeat(3) { moveBy(Offset(0f, -8f), delayMillis = 16) }
      up()
    }
    compose.waitForIdle()

    assertTrue("la lista ha scorso", !scrolled(list))
    assertTrue("non e' un click lungo: il menu non si apre", !menu.isOpen)
    assertEquals(1, clicks)
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
      // Largo 312 dp (624 px): il dito puo' fare molta strada e restare sul pulsante. Il click qui lo
      // fa il gesto, che ha consumato (`clickable` ha annullato): senza, un trascinamento non cliccava.
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
  fun `un dito che ha trascinato sul tasto non e' un click lungo, anche se poi si ferma`() {
    val list = LazyListState()
    var longClicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassIconButton(onClick = { }, onLongClick = { longClicks++ }, modifier = modifier) { Box(Modifier.size(20.dp)) }
    }

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      repeat(6) { moveBy(Offset(0f, -40f), delayMillis = 16) }
      // Il dito si ferma, ma ha gia' percorso lo schermo: non tiene premuto.
      advanceEventTime(700)
      up()
    }
    compose.waitForIdle()

    assertTrue("la pagina ha scorso sotto un dito che era di un tasto", !scrolled(list))
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
  fun `un tasto di vetro trascinato in verticale non scorre la lista, segue il dito e non clicca`() {
    val list = LazyListState()
    var clicks = 0
    showInLazyColumn(list) { modifier ->
      FluidGlassButton(text = "Vai", onClick = { clicks++ }, modifier = modifier.fillMaxWidth())
    }

    compose.onNodeWithTag("button").dragHeld(steps = 8, step = Offset(14f, -26f))
    compose.waitForIdle()

    assertTrue("la pagina ha scorso sotto un dito che era di un tasto di vetro", !scrolled(list))
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
  fun `una passata veloce su un tasto di vetro non scorre la lista`() {
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

    assertTrue("la pagina ha scorso", !scrolled(list))
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
