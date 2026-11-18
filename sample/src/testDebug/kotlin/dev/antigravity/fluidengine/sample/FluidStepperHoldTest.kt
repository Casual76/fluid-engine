package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidStepper
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private class StepperHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * Lo stepper tenuto premuto ripete (correzione dopo S9, sezione «liste»).
 *
 * S9 sul tablet: «+» di «Persone» tenuto per 3 s, il valore restava a 4 mentre il vetro cresceva; il tocco su «Copie»
 * funzionava. La causa era nel tracciatore del dito: leggeva il passaggio Final **del down stesso**, che il `clickable`
 * ha gia' consumato, e lo prendeva per una lista che ha preso il dito: la ripetizione smetteva prima del primo passo.
 * Un tocco breve non se ne accorge (il click e' un'altra strada), una pressione lunga si'.
 *
 * Qui il dito vero (giu', fermo, tempo che passa) sotto un `FluidStepper` nel vetro e fuori, e si guarda:
 *
 *  * tenuto sul «+» il valore sale, e continua a salire finche' il dito e' giu';
 *  * tenuto sul «−» scende; al capo si ferma;
 *  * al rilascio smette; un tocco breve fa un passo solo;
 *  * se una lista prende il dito per scorrere, la ripetizione smette anche se il dito e' ancora giu'.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidStepperHoldTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = StepperHaptics()
  private var value by mutableIntStateOf(0)
  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
  }

  /** Il tempo del test e quello del tavolo (i `delay` della ripetizione) vanno avanti insieme, un fotogramma alla volta. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  /**
   * Lo stepper da solo, o in una lista che scorre (come in una riga). [glass] lo mette sul canvas di una pagina (la
   * lastra di vetro), altrimenti e' la pastiglia opaca.
   */
  private fun show(
    glass: Boolean,
    start: Int = 0,
    range: IntRange = 0..1_000,
    inList: Boolean = false,
    list: LazyListState = LazyListState(),
    repeatOnHold: Boolean = true,
  ) {
    // La lastra di vetro c'e' solo dove il RenderEffect e' disponibile: senza, il test proverebbe due volte la pastiglia.
    if (glass) assertTrue("il vetro richiede il RenderEffect (API 31)", isRenderEffectSupported())
    value = start
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          val page = if (glass) rememberGlassBackdrop() else null
          CompositionLocalProvider(LocalFluidCanvasBackdrop provides page) {
            Box(Modifier.fillMaxSize()) {
              if (page != null) Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFF7FA7D9)))
              val stepper: @Composable () -> Unit = {
                FluidStepper(
                  value = value,
                  onValueChange = { value = it },
                  range = range,
                  label = "Persone",
                  repeatOnHold = repeatOnHold,
                  modifier = Modifier.testTag("stepper"),
                )
              }
              if (inList) {
                LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
                  items(40) {
                    Row(
                      Modifier.fillMaxWidth().height(72.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.End,
                    ) {
                      // Lo stepper sta sulla terza riga: le altre sono solo altezza da scorrere.
                      if (it == 2) stepper()
                    }
                  }
                }
              } else {
                Box(Modifier.align(Alignment.Center)) { stepper() }
              }
            }
          }
        }
      }
    }
    advance(160)
  }

  private fun pressRight() = compose.onNodeWithTag("stepper").performTouchInput { down(Offset(width * 0.8f, height / 2f)) }

  private fun pressLeft() = compose.onNodeWithTag("stepper").performTouchInput { down(Offset(width * 0.2f, height / 2f)) }

  private fun lift() = compose.onNodeWithTag("stepper").performTouchInput { up() }

  private fun holdRightKeepsCounting(glass: Boolean) {
    show(glass = glass, start = 4)
    pressRight()
    advance(1_000)
    val after1s = value
    assertTrue("dopo 1 s col dito sul «+» il valore e' ancora $after1s (partiva da 4)", after1s >= 4 + 3)
    advance(2_000)
    val after3s = value
    assertTrue("il valore deve continuare a salire col dito giu': $after1s a 1 s, $after3s a 3 s", after3s >= after1s + 15)
    lift()
    advance(200)
    val lifted = value
    advance(1_500)
    assertEquals("al rilascio la ripetizione smette", lifted, value)
    // Il primo passo e' il colpo del click lungo; i passi ripetuti hanno il loro Tick.
    val ticks = haptics.played.count { it == FluidHapticEvent.Tick }
    assertTrue("ogni passo ripetuto ha il suo Tick: $ticks per ${lifted - 4} passi", ticks >= (lifted - 4) - 1)
  }

  @Test
  fun `tenuto sul piu' il valore sale e continua a salire, sulla pastiglia opaca`() = holdRightKeepsCounting(glass = false)

  @Test
  fun `tenuto sul piu' il valore sale e continua a salire, nella lastra di vetro`() = holdRightKeepsCounting(glass = true)

  @Test
  fun `tenuto sul meno il valore scende e al capo si ferma`() {
    show(glass = false, start = 12, range = 0..20)
    pressLeft()
    advance(4_000)
    assertEquals("scende fino al capo e li' resta", 0, value)
    lift()
    advance(200)
    assertEquals(0, value)
  }

  @Test
  fun `tenuto sul piu' al capo si ferma al massimo`() {
    show(glass = true, start = 7, range = 0..10)
    pressRight()
    advance(3_000)
    assertEquals(10, value)
    lift()
  }

  @Test
  fun `senza repeatOnHold un passo per tocco anche tenendo premuto`() {
    show(glass = false, start = 4, repeatOnHold = false)
    pressRight()
    advance(3_000)
    assertEquals("senza ripetizione, tenuto premuto il valore non si muove", 4, value)
    lift()
    advance(300)
    assertEquals("al rilascio e' il tocco di sempre: un passo solo", 5, value)
  }

  @Test
  fun `un tocco breve fa un passo solo, sul piu' e sul meno`() {
    show(glass = false, start = 4)
    pressRight()
    advance(80)
    lift()
    advance(1_500)
    assertEquals(5, value)
    pressLeft()
    advance(80)
    lift()
    advance(1_500)
    assertEquals(4, value)
  }

  private fun listTakesTheFinger(glass: Boolean) {
    val list = LazyListState()
    show(glass = glass, start = 4, inList = true, list = list)
    pressRight()
    advance(1_000)
    val before = value
    assertTrue("prima dello scorrimento la ripetizione parte: $before", before >= 4 + 3)
    // Il pollice, ancora giu', corre verso il fondo: la lista prende il dito.
    compose.onNodeWithTag("stepper").performTouchInput { repeat(10) { moveBy(Offset(0f, -30f), delayMillis = 8L) } }
    val scrolled = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0
    assertTrue("la lista doveva prendere il dito e scorrere", scrolled)
    val atScroll = value
    advance(1_500)
    assertEquals("la lista ha preso il dito: la ripetizione smette anche con il dito ancora giu'", atScroll, value)
    lift()
  }

  @Test
  fun `se la lista prende il dito la ripetizione smette, sulla pastiglia opaca`() = listTakesTheFinger(glass = false)

  @Test
  fun `se la lista prende il dito la ripetizione smette, nella lastra di vetro`() = listTakesTheFinger(glass = true)
}
