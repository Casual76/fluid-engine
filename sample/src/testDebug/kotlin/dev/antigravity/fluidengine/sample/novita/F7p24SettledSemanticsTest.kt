package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Looper
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidSettledSemanticsDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidValueText
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.roundToInt
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
 * F7.24 · Con l'accessibilità accesa (come sul Tab S9 di Alessio, dove c'è «Collegamento a Windows»), un righello mosso
 * dal dito non fa ricontrollare la semantica della pagina a ogni tacca: la semantica del righello e la scritta accanto
 * ([FluidValueText]) restano ferme finché il dito e l'inerzia non si fermano, poi TalkBack legge il valore finale. Un
 * valore cambiato da TalkBack («aumenta») arriva subito.
 *
 * Il controllo «come prima» (un `Text` e un `semantics { }` che leggono il valore in composizione) prova che nella prova
 * l'accessibilità è accesa davvero: lì gli eventi di accessibilità arrivano mentre il dito si muove.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p24SettledSemanticsTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private var degrees by mutableFloatStateOf(0f)

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidSettledSemanticsDiagnostics.resetForTesting()
    // Un servizio di accessibilita' acceso, come TalkBack o «Collegamento a Windows».
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

  private fun show(settled: Boolean) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        Column(Modifier.fillMaxSize()) {
          FluidRulerDial(
            value = { degrees },
            onValueChange = { degrees = it },
            contentDescription = "Raddrizza",
            modifier = Modifier.testTag("ghiera"),
          )
          if (settled) {
            FluidValueText(text = { "Rotazione: ${degrees.roundToInt()}" }, modifier = Modifier.testTag("scritta"))
          } else {
            // Come prima di F7.24: il valore letto in composizione, un Text e una semantica che cambiano a ogni tacca.
            Text(
              "Rotazione: ${degrees.roundToInt()}",
              Modifier.testTag("scritta").semantics { stateDescription = "${degrees.roundToInt()}" },
            )
          }
        }
      }
    }
    advance(600)
  }

  /**
   * Dieci tacche verso sinistra col dito vero; [during] guarda a meta' strada (dopo 30 passi, mezzo secondo: prima del
   * passo lento di un secondo), col dito ancora giu'.
   */
  private fun dragTenTicks(during: () -> Unit) {
    val b = compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    var at = Offset(b.center.x, b.center.y)
    finger.down(at)
    advance(16)
    repeat(49) { i ->
      at += Offset(-4f, 0f)
      finger.move(at)
      advance(16)
      if (i == 29) during()
    }
    advance(160)
    finger.up(at)
    advance(1600)
  }

  private fun state(tag: String): String? =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

  private fun text(tag: String): String? =
    compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
      ?.joinToString("") { it.text }

  @Test
  fun `come prima, con l'accessibilita' accesa gli eventi arrivano a ogni tacca`() {
    show(settled = false)
    val before = events()
    var during = 0
    dragTenTicks { during = events() - before }
    println("F7p24 come prima: $during eventi col dito giu' (gradi ${degrees})")
    assertEquals(10f, degrees, 1e-4f)
    assertTrue("l'accessibilita' non e' accesa nella prova: $during eventi", during >= 2)
  }

  @Test
  fun `con la semantica a fine gesto il dito non fa scattare l'accessibilita' e TalkBack legge il valore finale`() {
    show(settled = true)
    val before = events()
    val publishedBefore = FluidSettledSemanticsDiagnostics.publishes
    var during = 0
    var publishedDuring = 0
    var stateDuring: String? = null
    dragTenTicks {
      during = events() - before
      publishedDuring = FluidSettledSemanticsDiagnostics.publishes - publishedBefore
      stateDuring = state("ghiera")
    }
    val after = events() - before
    println(
      "F7p24 a fine gesto: $during eventi e $publishedDuring semantiche date col dito giu', $after eventi in tutto, " +
        "${FluidSettledSemanticsDiagnostics.deferred} rinvii",
    )
    assertEquals(10f, degrees, 1e-4f)
    assertEquals("nessuna semantica data al sistema col dito giu'", 0, publishedDuring)
    assertEquals("nessun evento di accessibilita' col dito giu'", 0, during)
    assertEquals("col dito giu' TalkBack ha ancora il valore di partenza", "0", stateDuring)
    // Il gesto finito: il valore finale, una volta.
    assertEquals("10", state("ghiera"))
    assertEquals("Rotazione: 10", text("scritta"))
    assertTrue("a gesto finito la semantica e' data: $after eventi", after >= 1)
  }

  @Test
  fun `un valore cambiato da TalkBack arriva subito`() {
    show(settled = true)
    compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode()
      .config[SemanticsActions.CustomActions].first { it.label == "Aumenta" }.action()
    advance(16)
    assertEquals(1f, degrees, 1e-4f)
    assertEquals("1", state("ghiera"))
    assertEquals("Rotazione: 1", text("scritta"))
  }
}
