package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDialDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.abs
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

/**
 * F8.15c · La ghiera a righello non si ricompone a ogni valore. Sul tablet il righello dell'esposizione dell'editor dava
 * il 16% di fotogrammi in ritardo, quasi tutti per il thread dell'interfaccia: la ghiera intera si ricomponeva a ogni
 * valore (l'effetto che segue il valore ripartiva, la catena dei modificatori e la semantica si rifacevano). Qui un
 * trascinamento di dieci tacche col dito vero, contando:
 * - le composizioni del corpo della ghiera, che restano **ferme** (con la forma col numero e con quella con la lambda);
 * - le composizioni della scritta sopra l'indice: zero (da F7.24 la scritta si disegna, F8.15c ne contava una per scritta);
 * - le composizioni del chiamante che passa la lambda: **zero**;
 * - e TalkBack, che legge il valore nuovo anche senza ricomposizione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p15cRulerRecompositionTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private var degrees by mutableFloatStateOf(0f)
  private var callerCompositions = 0
  private var finished = 0

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidRulerDialDiagnostics.resetForTesting()
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidRulerDialDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  private fun show(lambda: Boolean) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize()) {
          SideEffect { callerCompositions++ }
          if (lambda) {
            FluidRulerDial(
              value = { degrees },
              onValueChange = { degrees = it },
              onValueChangeFinished = { finished++ },
              contentDescription = "Raddrizza",
              modifier = Modifier.testTag("ghiera"),
            )
          } else {
            FluidRulerDial(
              value = degrees,
              onValueChange = { degrees = it },
              onValueChangeFinished = { finished++ },
              contentDescription = "Raddrizza",
              modifier = Modifier.testTag("ghiera"),
            )
          }
        }
      }
    }
    advance(600)
  }

  /** Dieci tacche verso sinistra col dito vero (mezzo slop, poi 4 px per evento: 18 px per tacca a densita' 2). */
  private fun dragTenTicks() {
    val b = compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    val start = Offset(b.center.x, b.center.y)
    finger.down(start)
    advance(16)
    var at = start
    repeat(49) {
      at += Offset(-4f, 0f)
      finger.move(at)
      advance(16)
    }
    advance(160)
    finger.up(at)
    advance(1200)
  }

  private fun stateDescription(): String? =
    compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  private fun check(lambda: Boolean) {
    show(lambda)
    FluidRulerDialDiagnostics.resetForTesting()
    val callerBefore = callerCompositions
    dragTenTicks()
    println(
      "F8p15c ${if (lambda) "lambda" else "numero"}: ghiera ${FluidRulerDialDiagnostics.compositions}, scritta " +
        "${FluidRulerDialDiagnostics.labelCompositions}, disegni delle tacche ${FluidRulerDialDiagnostics.tickDraws}, " +
        "chiamante ${callerCompositions - callerBefore}",
    )
    assertEquals("il dito porta la ghiera di dieci tacche", 10f, degrees, 1e-4f)
    assertTrue("a fine gesto", finished >= 1)
    // Il corpo della ghiera non si ricompone: ne' per il dito, ne' per l'inerzia, ne' per la posa.
    assertEquals("composizioni della ghiera durante il trascinamento", 0, FluidRulerDialDiagnostics.compositions)
    // F7.24: la scritta sopra l'indice si disegna (si legge la posizione nel disegno): non si ricompone mai.
    assertEquals("composizioni della scritta", 0, FluidRulerDialDiagnostics.labelCompositions)
    // TalkBack legge il valore nuovo anche se niente si e' ricomposto.
    assertEquals("10", stateDescription())
    if (lambda) assertEquals("il chiamante con la lambda non si ricompone", callerBefore, callerCompositions)
  }

  @Test
  fun conIlNumeroLaGhieraNonSiRicomponeAOgniValore() = check(lambda = false)

  @Test
  fun conLaLambdaNonSiRicomponeNiente() = check(lambda = true)

  @Test
  fun unValoreCambiatoDaFuoriPortaIlRighelloSenzaRicomporreLaGhiera() {
    show(lambda = true)
    FluidRulerDialDiagnostics.resetForTesting()
    degrees = 20f
    advance(1200)
    assertEquals(0, FluidRulerDialDiagnostics.compositions)
    assertEquals("20", stateDescription())
    // Poi il dito parte da li': dieci tacche in piu'.
    dragTenTicks()
    assertTrue("da 20 a 30: $degrees", abs(degrees - 30f) < 1e-4f)
  }
}
