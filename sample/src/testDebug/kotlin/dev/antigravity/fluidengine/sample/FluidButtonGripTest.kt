package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidPressGrip
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

/** Cosa ha suonato il tema durante un gesto. */
private class GripHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/** Un pulsante con la sua firma di prova: il modificatore con l'etichetta, la presa, il click. */
private typealias Kind = @Composable (Modifier, FluidPressGrip, () -> Unit) -> Unit

/**
 * `grip` sui pulsanti dell'engine (3.0): un pulsante ripetuto su ogni riga di una lista che scorre
 * cede la lista al dito, e uno che sta da solo lo tiene.
 *
 * Fino a oggi solo `Modifier.fluidPressable` esponeva `grip`: un `FluidButton` in una riga di
 * `LazyColumn` teneva sempre il dito, e per scorrere si doveva partire fuori dalla riga — cioe' mai,
 * in una lista di soli pulsanti. Qui ogni pulsante dell'engine che cresce sotto il dito
 * (`FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, `FluidCapsuleAction`) sta in una
 * `LazyColumn` con un esemplare su ogni riga, sotto un dito finto, e si guarda:
 *
 *  * di serie (`HoldsFinger`) la lista **non** scorre sotto un dito partito dal pulsante, e il
 *    pulsante risponde;
 *  * con `YieldsToScroll` la lista scorre, il pulsante si scioglie (`PressRelease`) e non clicca.
 *
 * E `FluidButton(haptic = null)`, che prima non esisteva: un tocco non suona niente.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidButtonGripTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = GripHaptics()

  private val button: Kind = { modifier, grip, onClick ->
    FluidButton(text = "Riserva", onClick = onClick, modifier = modifier, size = FluidButtonSize.Small, grip = grip)
  }

  private val glassButton: Kind = { modifier, grip, onClick ->
    FluidGlassButton(text = "Riserva", onClick = onClick, modifier = modifier, grip = grip)
  }

  private val glassIconButton: Kind = { modifier, grip, onClick ->
    FluidGlassIconButton(onClick = onClick, modifier = modifier, grip = grip) {
      Icon(Icons.Rounded.Add, contentDescription = "Aggiungi")
    }
  }

  private val capsuleAction: Kind = { modifier, grip, onClick ->
    FluidCapsuleAction(
      icon = Icons.Rounded.Add,
      contentDescription = "Aggiungi",
      onClick = onClick,
      modifier = modifier,
      grip = grip,
    )
  }

  /** Quaranta righe alte 72 dp, un pulsante su ognuna: la lista di un catalogo con un tasto per voce. */
  private fun showRows(list: LazyListState, kind: Kind, grip: FluidPressGrip, onClick: () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            items(40) { i ->
              Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.CenterEnd) {
                kind(Modifier.testTag("button$i"), grip, onClick)
              }
            }
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun scrolled(list: LazyListState) = list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 0

  /** Un pollice che parte dal pulsante della terza riga e corre verso il fondo della lista, e resta giu'. */
  private fun dragFromRow3() {
    compose.onNodeWithTag("button3").performTouchInput {
      down(center)
      advanceEventTime(40L)
      repeat(10) { moveBy(Offset(0f, -30f), delayMillis = 8L) }
    }
    compose.waitForIdle()
  }

  private fun liftAnywhere() {
    compose.onNodeWithTag("button3").performTouchInput { up() }
    compose.waitForIdle()
  }

  private fun holdsTheFinger(kind: Kind) {
    val list = LazyListState()
    var clicks = 0
    showRows(list, kind, FluidPressGrip.HoldsFinger) { clicks++ }

    dragFromRow3()

    assertTrue("la lista ha scorso sotto un dito che era del pulsante", !scrolled(list))
    assertEquals("il pulsante risponde dal contatto", FluidHapticEvent.PressDown, haptics.played.first())
    assertEquals("e' ancora giu': niente rilascio", listOf(FluidHapticEvent.PressDown), haptics.played)
    liftAnywhere()
    assertEquals("sollevato lontano dal pulsante: nessun click", 0, clicks)
    assertTrue("la lista ha scorso al rilascio", !scrolled(list))
  }

  private fun yieldsToTheList(kind: Kind) {
    val list = LazyListState()
    var clicks = 0
    showRows(list, kind, FluidPressGrip.YieldsToScroll) { clicks++ }

    dragFromRow3()

    assertTrue("la lista non ha scorso: il pulsante teneva il dito", scrolled(list))
    assertEquals(
      "il pulsante si e' sciolto appena la lista ha preso il dito, a dito ancora giu'",
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease),
      haptics.played,
    )
    liftAnywhere()
    assertEquals("la lista ha scorso: nessun click", 0, clicks)
    assertEquals("e il sollevamento non suona niente di nuovo", 2, haptics.played.size)
  }

  private fun stillTaps(kind: Kind) {
    var clicks = 0
    showRows(LazyListState(), kind, FluidPressGrip.YieldsToScroll) { clicks++ }

    compose.onNodeWithTag("button3").performTouchInput {
      down(center)
      advanceEventTime(60L)
      up()
    }
    compose.waitForIdle()

    assertEquals("un tocco fermo clicca anche su un pulsante che cede", 1, clicks)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick),
      haptics.played,
    )
  }

  // --- di serie: il pulsante tiene il dito ------------------------------------------------------

  @Test fun `FluidButton di serie tiene il dito dentro una lista`() = holdsTheFinger(button)

  @Test fun `FluidGlassButton di serie tiene il dito dentro una lista`() = holdsTheFinger(glassButton)

  @Test fun `FluidGlassIconButton di serie tiene il dito dentro una lista`() = holdsTheFinger(glassIconButton)

  @Test fun `FluidCapsuleAction di serie tiene il dito dentro una lista`() = holdsTheFinger(capsuleAction)

  // --- YieldsToScroll: la lista scorre dai suoi pulsanti ----------------------------------------

  @Test fun `FluidButton con YieldsToScroll cede la lista al dito`() = yieldsToTheList(button)

  @Test fun `FluidGlassButton con YieldsToScroll cede la lista al dito`() = yieldsToTheList(glassButton)

  @Test fun `FluidGlassIconButton con YieldsToScroll cede la lista al dito`() = yieldsToTheList(glassIconButton)

  @Test fun `FluidCapsuleAction con YieldsToScroll cede la lista al dito`() = yieldsToTheList(capsuleAction)

  // --- e un tocco e' sempre un tocco ------------------------------------------------------------

  @Test fun `un tocco su FluidButton con YieldsToScroll clicca una volta, con i tre colpi`() = stillTaps(button)

  @Test fun `un tocco su FluidGlassButton con YieldsToScroll clicca una volta, con i tre colpi`() = stillTaps(glassButton)

  @Test fun `un tocco su FluidGlassIconButton con YieldsToScroll clicca una volta, con i tre colpi`() = stillTaps(glassIconButton)

  // --- haptic su FluidButton --------------------------------------------------------------------

  @Test
  fun `FluidButton con haptic null clicca e non suona niente`() {
    var clicks = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          FluidButton(text = "Avvia", onClick = { clicks++ }, haptic = null, modifier = Modifier.testTag("button"))
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60L)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals("un controllo che ha il suo colpo non ne ha tre in piu': ${haptics.played}", emptyList<FluidHapticEvent>(), haptics.played)
  }

  @Test
  fun `FluidButton con un altro evento sostituisce il click e tiene contatto e rilascio`() {
    var clicks = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides haptics) {
          FluidButton(text = "Apri", onClick = { clicks++ }, haptic = FluidHapticEvent.Open, modifier = Modifier.testTag("button"))
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("button").performTouchInput {
      down(center)
      advanceEventTime(60L)
      up()
    }
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertEquals(
      listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.Open),
      haptics.played,
    )
  }
}
