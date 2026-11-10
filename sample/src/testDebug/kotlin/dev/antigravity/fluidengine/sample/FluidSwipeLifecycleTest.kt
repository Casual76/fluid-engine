package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeAction
import dev.antigravity.fluidengine.ui.fluid.FluidSwipeState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidSwipeActions
import dev.antigravity.fluidengine.ui.fluid.rememberFluidSwipeState
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'azione di una riga che esce scorrendo parte **una volta sola** anche se la riga lascia la composizione mentre
 * sta uscendo (la lista scorre o si aggiorna, la schermata si chiude): prima restava nello scope della riga e,
 * smontata la riga, la mail «archiviata» restava dov'era.
 *
 * Il tempo e' del test (`mainClock`): il gesto confermato, poi la riga smontata dopo 0/100/200/300/500 ms
 * dell'uscita (circa 430 ms) o mai.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidSwipeLifecycleTest {
  @get:Rule val compose = createComposeRule()

  private class Run {
    var callbacks = 0
    var allOnMainThread = true
    lateinit var swipe: FluidSwipeState
    lateinit var scope: CoroutineScope
  }

  private var visible by mutableStateOf(true)

  private fun mount(run: Run, reducedMotion: Boolean = false) {
    visible = true
    compose.setContent {
      FluidTheme(EngineSettings(), systemBars = false) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides FluidMotionPolicy(if (reducedMotion) 0f else 1f)) {
          // Lo scope della prova sta fuori dalla riga: sopravvive al suo smontaggio.
          run.scope = rememberCoroutineScope()
          if (visible) Box(Modifier.fillMaxWidth()) {
            run.swipe = rememberFluidSwipeState()
            FluidListRow(
              "Riga", "Scorri per archiviare",
              modifier = Modifier
                .fluidSwipeActions(
                  run.swipe,
                  endAction = FluidSwipeAction("Archivia", Icons.Rounded.Archive, Color.Green, removesItem = true) {
                    run.callbacks++
                    if (Looper.myLooper() !== Looper.getMainLooper()) run.allOnMainThread = false
                  },
                )
                .testTag(RowTag),
            )
          }
        }
      }
    }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
  }

  /** Il dito parte dal bordo destro (fuori dalla fascia dei gesti di sistema) e va oltre la soglia, poi si alza. */
  private fun dragEnd(pastThreshold: Boolean) {
    compose.onNodeWithTag(RowTag).performTouchInput {
      down(Offset(width - 40.dp.toPx(), centerY))
      repeat(if (pastThreshold) 25 else 3) { moveBy(Offset(-20f, 0f)) }
      up()
    }
    compose.mainClock.advanceTimeByFrame()
  }

  private fun unmountAfter(millis: Long) {
    compose.mainClock.advanceTimeBy(millis)
    compose.runOnIdle { visible = false }
    compose.mainClock.advanceTimeByFrame()
  }

  private fun settle() {
    compose.mainClock.advanceTimeBy(3_000)
    compose.waitForIdle()
    compose.mainClock.autoAdvance = true
  }

  @Test fun completamentoNormale_unaVolta_sulThreadPrincipale() {
    val run = Run()
    mount(run)
    dragEnd(pastThreshold = true)
    compose.runOnIdle { assertTrue("gesto confermato", run.swipe.isDismissed) }
    assertEquals("non prima della fine dell'uscita", 0, run.callbacks)
    settle()
    assertEquals(1, run.callbacks)
    assertTrue("sul thread principale", run.allOnMainThread)
  }

  @Test fun smontataDopoIlCompletamento_nonParteUnaSecondaVolta() {
    val run = Run()
    mount(run)
    dragEnd(pastThreshold = true)
    compose.mainClock.advanceTimeBy(1_500)
    assertEquals(1, run.callbacks)
    unmountAfter(0)
    settle()
    assertEquals("l'uscita era finita: lo smontaggio non la ripete", 1, run.callbacks)
  }

  private fun unmountedDuringExit(millis: Long, reducedMotion: Boolean = false) {
    val run = Run()
    mount(run, reducedMotion)
    dragEnd(pastThreshold = true)
    compose.runOnIdle { assertTrue("gesto confermato", run.swipe.isDismissed) }
    unmountAfter(millis)
    settle()
    assertEquals("smontata a $millis ms (movimento ridotto: $reducedMotion)", 1, run.callbacks)
    assertTrue("sul thread principale", run.allOnMainThread)
  }

  @Test fun smontataSubito() = unmountedDuringExit(0)
  @Test fun smontataA100ms() = unmountedDuringExit(100)
  @Test fun smontataA200ms() = unmountedDuringExit(200)
  @Test fun smontataA300ms() = unmountedDuringExit(300)
  @Test fun smontataA500ms() = unmountedDuringExit(500)
  @Test fun smontataSubito_conIlMovimentoRidotto() = unmountedDuringExit(0, reducedMotion = true)

  @Test fun azioneDiTalkBack_smontataDuranteLUscita_parteUnaVolta() {
    val run = Run()
    mount(run)
    compose.runOnIdle {
      val actions = compose.onNodeWithTag(RowTag).fetchSemanticsNode().config[SemanticsActions.CustomActions]
      assertTrue(actions.first { it.label == "Archivia" }.action())
    }
    compose.mainClock.advanceTimeByFrame()
    unmountAfter(100)
    settle()
    assertEquals(1, run.callbacks)
    assertTrue(run.allOnMainThread)
  }

  @Test fun gestoSottoLaSoglia_nonPartePiu_nemmenoSeLaRigaSiSmonta() {
    val run = Run()
    mount(run)
    dragEnd(pastThreshold = false)
    compose.runOnIdle { assertFalse("non confermato", run.swipe.isDismissed) }
    unmountAfter(50)
    settle()
    assertEquals(0, run.callbacks)
  }

  @Test fun gestoRiportatoIndietroColDito_nonParte() {
    val run = Run()
    mount(run)
    compose.onNodeWithTag(RowTag).performTouchInput {
      down(Offset(width - 40.dp.toPx(), centerY))
      repeat(25) { moveBy(Offset(-20f, 0f)) }
      repeat(25) { moveBy(Offset(20f, 0f)) }
      up()
    }
    compose.mainClock.advanceTimeByFrame()
    compose.runOnIdle { assertFalse("il dito e' tornato: non confermato", run.swipe.isDismissed) }
    unmountAfter(50)
    settle()
    assertEquals(0, run.callbacks)
  }

  @Test fun rimessaAPostoDallApp_durante_lUscita_nonParte() {
    val run = Run()
    mount(run)
    dragEnd(pastThreshold = true)
    compose.mainClock.advanceTimeBy(100)
    compose.runOnIdle { assertTrue(run.swipe.isDismissed) }
    // L'app ha cambiato idea mentre la riga usciva («Annulla»): reset prima che l'azione sia partita.
    val reset: Job = run.scope.launch { run.swipe.reset() }
    compose.mainClock.advanceTimeBy(1_000)
    assertTrue(reset.isCompleted)
    unmountAfter(0)
    settle()
    assertEquals(0, run.callbacks)
  }

  private companion object {
    const val RowTag = "swipe-row"
  }
}
