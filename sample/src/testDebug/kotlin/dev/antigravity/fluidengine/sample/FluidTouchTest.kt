package dev.antigravity.fluidengine.sample

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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidPressState
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Cosa ha suonato il tema durante un gesto. */
private class RecordingHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * I controlli della 2.11.0 sotto un dito finto, dentro un albero Compose vero.
 *
 * Quello che qui si prova e' quello che una funzione pura non puo' dire: che lo slider tenga ferma
 * una pagina che *potrebbe* scorrere (una `LazyColumn` e una `Column` con `verticalScroll`, le due
 * che le app usano), che non diventi pero' una fascia da cui la pagina non si scorre piu', e che un
 * tap piu' rapido del ritardo di `clickable` dentro una lista mostri comunque tutta la pressione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidTouchTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = RecordingHaptics()

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  /** Una lista lunga con uno slider a meta' maniglia nel terzo item. */
  private fun showSliderInLazyColumn(listState: LazyListState, read: (Float) -> Unit) {
    compose.setContent {
      Themed {
        var value by remember { mutableFloatStateOf(0.5f) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
          items(2) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
          item {
            FluidSlider(
              value = value,
              onValueChange = {
                value = it
                read(it)
              },
              modifier = Modifier.padding(horizontal = 24.dp).testTag("slider"),
            )
          }
          items(30) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
        }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun `trascinare la maniglia in diagonale non fa scorrere la LazyColumn`() {
    val listState = LazyListState()
    var value = 0.5f
    showSliderInLazyColumn(listState) { value = it }

    // A meta' valore la maniglia sta al centro dello slider. Un pollice vero: un po' di lato e
    // molto in giu', ben oltre lo slop della pagina.
    compose.onNodeWithTag("slider").performTouchInput {
      down(center)
      repeat(12) { moveBy(Offset(8f, 18f)) }
      up()
    }
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, listState.firstVisibleItemIndex)
    assertEquals("la pagina ha preso il gesto", 0, listState.firstVisibleItemScrollOffset)
    assertTrue("il valore non ha seguito il dito: $value", value > 0.5f)
  }

  @Test
  fun `trascinare la maniglia non fa scorrere una Column con verticalScroll`() {
    lateinit var scroll: ScrollState
    var value = 0.5f
    compose.setContent {
      Themed {
        var current by remember { mutableFloatStateOf(0.5f) }
        scroll = rememberScrollState()
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
          Spacer(Modifier.fillMaxWidth().height(200.dp))
          FluidSlider(
            value = current,
            onValueChange = {
              current = it
              value = it
            },
            modifier = Modifier.padding(horizontal = 24.dp).testTag("slider"),
          )
          Spacer(Modifier.fillMaxWidth().height(2_000.dp))
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("slider").performTouchInput {
      down(center)
      repeat(12) { moveBy(Offset(-8f, -18f)) }
      up()
    }
    compose.waitForIdle()

    assertEquals("la pagina ha preso il gesto", 0, scroll.value)
    assertTrue("il valore non ha seguito il dito: $value", value < 0.5f)
  }

  @Test
  fun `un gesto preso di lato sulla pista resta dello slider anche se poi va in verticale`() {
    val listState = LazyListState()
    var value = 0.5f
    showSliderInLazyColumn(listState) { value = it }

    compose.onNodeWithTag("slider").performTouchInput {
      // Sulla pista nuda, lontano dalla maniglia che sta al centro.
      down(Offset(width * 0.12f, centerY))
      moveBy(Offset(14f, 2f))
      moveBy(Offset(14f, 2f))
      repeat(8) { moveBy(Offset(0f, 30f)) }
      up()
    }
    compose.waitForIdle()

    assertEquals(0, listState.firstVisibleItemIndex)
    assertEquals(0, listState.firstVisibleItemScrollOffset)
    assertTrue("la maniglia non e' venuta sotto il dito: $value", value < 0.3f)
  }

  @Test
  fun `una passata verticale partita sulla pista scorre ancora la pagina`() {
    val listState = LazyListState()
    var value = 0.5f
    showSliderInLazyColumn(listState) { value = it }

    compose.onNodeWithTag("slider").performTouchInput {
      down(Offset(width * 0.12f, centerY))
      repeat(10) { moveBy(Offset(0f, -40f)) }
      up()
    }
    compose.waitForIdle()

    assertTrue(
      "uno slider a tutta larghezza non deve diventare una fascia che non scorre",
      listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0,
    )
    assertEquals("lo slider non doveva muoversi", 0.5f, value, 0f)
  }

  @Test
  fun `trascinando si sentono tacche leggere e non un ronzio`() {
    val listState = LazyListState()
    showSliderInLazyColumn(listState) { }

    compose.onNodeWithTag("slider").performTouchInput {
      down(center)
      // Un trascinamento rapido verso destra fino in fondo: molti piu' gradini che tacche possibili.
      repeat(30) { moveBy(Offset(12f, 0f)) }
      up()
    }
    compose.waitForIdle()

    val ticks = haptics.played.count { it == FluidHapticEvent.DragTick }
    assertTrue("nessuna tacca durante il trascinamento", ticks > 0)
    // 30 eventi a 16 ms l'uno: con una tacca ogni 55 ms al massimo, non piu' di 9.
    assertTrue("$ticks tacche in 480 ms: e' un ronzio", ticks <= 9)
    assertTrue("il colpo in fondo non e' arrivato", FluidHapticEvent.Threshold in haptics.played)
  }

  @Test
  fun `un tap piu' rapido del ritardo di una lista mostra comunque tutta la pressione`() {
    lateinit var press: FluidPressState
    var clicks = 0
    compose.mainClock.autoAdvance = false
    compose.setContent {
      Themed {
        LazyColumn(Modifier.fillMaxSize()) {
          item {
            press = rememberFluidPressState()
            Box(
              Modifier
                .padding(24.dp)
                .size(160.dp, 48.dp)
                .testTag("button")
                .fluidPressable(onClick = { clicks++ }, pressState = press),
            )
          }
          items(30) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
        }
      }
    }
    compose.mainClock.advanceTimeByFrame()

    // Giu' e su nello stesso istante: dentro una lista clickable consegna pressione e rilascio
    // insieme, ed era il caso in cui il tasto non si muoveva affatto.
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
    assertNotEquals("non e' mai partita", 0f, seen.max())
  }
}
