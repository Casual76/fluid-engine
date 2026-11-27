package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfter
import dev.antigravity.fluidengine.ui.fluid.FluidBeforeAfterState
import dev.antigravity.fluidengine.ui.fluid.FluidSettledSemanticsDiagnostics
import dev.antigravity.fluidengine.ui.fluid.rememberFluidBeforeAfterState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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

/**
 * R1-C · Il confronto a tendina (F8.15) e le due regole di F7.24 (review R1, Opus D1 e D5): con l'accessibilità accesa
 * la semantica della maniglia mossa dal dito resta ferma (TalkBack legge il valore a fine gesto, un valore dato da
 * TalkBack arriva subito), e il dito che trascina non ricompone il corpo della tendina a ogni fotogramma: la linea, il
 * taglio e la maniglia si leggono nel disegno e nella posa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class R1cBeforeAfterTest {
  @get:Rule val compose = createComposeRule()

  private lateinit var view: View
  private lateinit var split: FluidBeforeAfterState

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    FluidSettledSemanticsDiagnostics.resetForTesting()
    // Un servizio di accessibilita' acceso, come TalkBack o «Collegamento a Windows» sul Tab S9.
    val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)
    val shadow = shadowOf(manager)
    shadow.setEnabled(true)
    shadow.setTouchExplorationEnabled(true)
    shadow.setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidSettledSemanticsDiagnostics.resetForTesting()
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

  private fun events(): Int {
    val manager = ApplicationProvider.getApplicationContext<Context>().getSystemService(AccessibilityManager::class.java)
    return shadowOf(manager).sentAccessibilityEvents.count { it.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED }
  }

  /** Quante volte le composizioni della finestra sono cambiate (il contatore del Recomposer). */
  private fun recompositions(): Long = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

  private fun show() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        split = rememberFluidBeforeAfterState()
        FluidBeforeAfter(
          before = { Box(Modifier.fillMaxSize().background(Color(0xFFCC3333))) },
          after = { Box(Modifier.fillMaxSize().background(Color(0xFF3366CC))) },
          modifier = Modifier.fillMaxSize().testTag("tendina"),
          state = split,
        )
      }
    }
    advance(800)
  }

  private fun handleState(): String? =
    compose.onNode(hasContentDescription("Confronto prima e dopo"), useUnmergedTree = true).fetchSemanticsNode()
      .config.getOrNull(SemanticsProperties.StateDescription)

  /** La maniglia trascinata col dito vero da meta' al 70% (160 px su 800); [during] guarda al passo 30, col dito giu'. */
  private fun dragTo70(during: () -> Unit) {
    val b = compose.onNodeWithTag("tendina", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    var at = Offset(b.center.x, b.center.y)
    finger.down(at)
    advance(16)
    repeat(40) { i ->
      at += Offset(4f, 0f)
      finger.move(at)
      advance(16)
      if (i == 29) during()
    }
    advance(160)
    finger.up(at)
    advance(1600)
  }

  @Test
  fun `col dito la semantica della maniglia resta ferma e TalkBack legge il valore a fine gesto`() {
    show()
    assertEquals("Prima al 50%", handleState())
    val before = events()
    val published = FluidSettledSemanticsDiagnostics.publishes
    var during = 0
    var publishedDuring = 0
    var stateDuring: String? = null
    dragTo70 {
      during = events() - before
      publishedDuring = FluidSettledSemanticsDiagnostics.publishes - published
      stateDuring = handleState()
    }
    println("R1c tendina: $during eventi e $publishedDuring semantiche col dito giu', ${events() - before} in tutto")
    assertEquals(0.7f, split.fraction, 0.01f)
    assertEquals("nessuna semantica data col dito giu'", 0, publishedDuring)
    assertEquals("col dito giu' TalkBack ha ancora il valore di partenza", "Prima al 50%", stateDuring)
    // Prima 95 eventi in mezzo secondo. Restano quelli delle misure: il pomello di vetro si sposta (come quello del
    // cursore), e Compose ricontrolla le misure della pagina al piu' ogni 100 ms. Quelli si tolgono solo spostando il
    // vetro nel disegno (vedi il rapporto di R1-C).
    assertTrue("eventi col dito giu': $during", during <= 6)
    assertEquals("Prima al 70%", handleState())
  }

  @Test
  fun `un valore dato da TalkBack arriva subito`() {
    show()
    compose.onNode(hasContentDescription("Confronto prima e dopo"), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.SetProgress) { it(0.3f) }
    advance(200)
    assertEquals(0.3f, split.fraction, 1e-4f)
    assertEquals("Prima al 30%", handleState())
  }

  @Test
  fun `il dito che trascina la maniglia non ricompone la tendina a ogni fotogramma`() {
    show()
    var start = 0L
    var during = 0L
    val b = compose.onNodeWithTag("tendina", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    var at = Offset(b.center.x, b.center.y)
    finger.down(at)
    advance(16)
    repeat(40) { i ->
      at += Offset(4f, 0f)
      finger.move(at)
      advance(16)
      if (i == 4) start = recompositions()
      if (i == 36) during = recompositions() - start
    }
    finger.up(at)
    advance(800)
    println("R1c tendina: $during ricomposizioni in 32 fotogrammi di trascinamento")
    assertEquals(0.7f, split.fraction, 0.01f)
    assertTrue("la tendina si ricompone a ogni fotogramma: $during in 32", during <= 3)
  }
}
