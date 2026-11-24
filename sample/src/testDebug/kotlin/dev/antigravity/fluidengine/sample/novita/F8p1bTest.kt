@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidFingerDrive
import dev.antigravity.fluidengine.ui.fluid.FluidGauge
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidNumericTextDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidWheelPicker
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
 * F8.1b · Le scelte di Alessio del 24 novembre: il badge scorre come gli altri numeri, il numero del quadrante scorre, e un
 * valore che cambia troppo spesso (i decibel) non scorre ma cambia di colpo, come prima. E la regola nuova: ciò che muove il
 * dito resta nitido, quindi un numero guidato da un cursore cambia di colpo finché il dito è giù, e quello di una ruota o di
 * un righello lanciati dal dito resta nitido per tutta l'inerzia, fino alla posa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w400dp-h800dp-xhdpi")
class F8p1bTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View

  @Before
  fun glass() {
    // Come le prove dei gesti di F8.2: le maniglie e la ghiera sono vetro dell'engine, coi verdetti degli shader accettati.
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun noGlass() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(millis: Long) {
    repeat(((millis + 15) / 16).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) { content() }
    }
    advance(300)
    FluidNumericTextDiagnostics.rolls = 0
    FluidNumericTextDiagnostics.jumps = 0
  }

  @Test
  fun `il badge scorre quando il numero cambia`() {
    var count by mutableIntStateOf(3)
    show { FluidBadge(count = count) }
    count = 4
    advance(800)
    assertEquals("3 → 4 scorre", 1, FluidNumericTextDiagnostics.rolls)
    count = 12
    advance(800)
    assertEquals("4 → 12 scorre (la capsula si allarga)", 2, FluidNumericTextDiagnostics.rolls)
    assertEquals(0, FluidNumericTextDiagnostics.jumps)
  }

  @Test
  fun `un valore che cambia otto volte al secondo non scorre, e torna a scorrere quando rallenta`() {
    var value by mutableIntStateOf(60)
    show { FluidNumericText(value) }
    repeat(8) {
      value += 3
      advance(125)
    }
    assertEquals("il primo cambio scorre", 1, FluidNumericTextDiagnostics.rolls)
    assertEquals("gli altri sette cambiano di colpo", 7, FluidNumericTextDiagnostics.jumps)
    advance(600)
    value += 1
    advance(800)
    assertEquals("calmo di nuovo: scorre", 2, FluidNumericTextDiagnostics.rolls)
  }

  @Test
  fun `il numero del quadrante scorre`() {
    var steady by mutableFloatStateOf(18f)
    show {
      Box(Modifier.width(220.dp)) { FluidGauge(value = steady, range = 0f..100f, style = FluidGaugeStyle.Arc, label = "Assenze") }
    }
    steady = 21f
    advance(900)
    assertEquals("il valore che cambia di rado scorre", 1, FluidNumericTextDiagnostics.rolls)
  }

  @Test
  fun `i decibel del quadrante con la lambda non scorrono`() {
    val db = mutableFloatStateOf(60f)
    show {
      Box(Modifier.width(220.dp)) {
        FluidGauge(value = { db.floatValue }, range = 30f..110f, style = FluidGaugeStyle.Arc, label = "Livello", valueText = { "${it.toInt()} dB" })
      }
    }
    repeat(8) { i ->
      db.floatValue = 60f + (i + 1) * 3f
      advance(125)
    }
    assertTrue("otto volte al secondo: al più il primo scorre (${FluidNumericTextDiagnostics.rolls})", FluidNumericTextDiagnostics.rolls <= 1)
    assertTrue("gli altri cambiano di colpo (${FluidNumericTextDiagnostics.jumps})", FluidNumericTextDiagnostics.jumps >= 6)
  }

  @Test
  fun `il numero guidato dal dito non scorre, quello mosso dall'app sì`() {
    var steady by mutableFloatStateOf(18f)
    show {
      Column {
        Box(Modifier.width(220.dp)) { FluidGauge(value = steady, range = 0f..100f, style = FluidGaugeStyle.Arc, label = "Assenze") }
        FluidSlider(
          value = steady / 100f,
          onValueChange = { steady = it * 100f },
          modifier = Modifier.width(360.dp).testTag("cursore"),
        )
      }
    }
    // Trascinato piano: un passo ogni 400 ms, più dei 300 che bastano a un numero per scorrere.
    compose.onNodeWithTag("cursore").performTouchInput {
      down(Offset(width * 0.18f, centerY))
      repeat(3) { moveBy(Offset(12f, 0f)) }
    }
    advance(400)
    repeat(5) {
      compose.onNodeWithTag("cursore").performTouchInput { moveBy(Offset(16f, 0f)) }
      advance(400)
    }
    assertEquals("il dito è giù", 1, FluidFingerDrive.active)
    val moved = steady
    assertTrue("il cursore ha seguito il dito: $moved", moved > 24f)
    assertEquals("sotto il dito il numero non scorre", 0, FluidNumericTextDiagnostics.rolls)
    assertTrue("cambia di colpo (${FluidNumericTextDiagnostics.jumps})", FluidNumericTextDiagnostics.jumps >= 4)

    compose.onNodeWithTag("cursore").performTouchInput { up() }
    advance(400)
    assertEquals("il dito è su", 0, FluidFingerDrive.active)
    val jumps = FluidNumericTextDiagnostics.jumps
    steady = 70f
    advance(900)
    assertEquals("lo muove l'app: scorre", 1, FluidNumericTextDiagnostics.rolls)
    assertEquals(jumps, FluidNumericTextDiagnostics.jumps)
  }

  /** Un lancio col dito vero (eventi del sistema): passi di [stepX], [stepY] px ogni 16 ms, e il dito si alza in corsa. */
  private fun fling(from: Offset, stepX: Float, stepY: Float, steps: Int) {
    val finger = MaterialFinger(view)
    var at = from
    finger.down(at)
    repeat(steps) {
      advance(16)
      at = Offset(at.x + stepX, at.y + stepY)
      finger.move(at)
    }
    finger.up(at)
  }

  @Test
  fun `la ruota lanciata dal dito, il numero resta nitido per tutta l'inerzia, posa compresa`() {
    var minute by mutableIntStateOf(10)
    show {
      Column {
        FluidNumericText(minute)
        FluidWheelPicker(
          count = 60,
          selected = minute,
          onSelectedChange = { minute = it },
          label = { it.toString().padStart(2, '0') },
          contentDescription = "Minuti",
          modifier = Modifier.testTag("ruota"),
        )
      }
    }
    val b = compose.onNodeWithTag("ruota", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    // Come il lancio delle prove di F8.2: sei passi da 40 px, e il dito si alza in corsa.
    fling(Offset(b.center.x, b.center.y + 120f), 0f, -40f, steps = 6)
    advance(48)
    val inFlight = FluidFingerDrive.active
    assertEquals("durante l'inerzia la ruota non ha ancora detto la voce", 10, minute)
    advance(6000)
    assertTrue("si è posata oltre il dito: $minute", minute >= 13)
    // La ruota dice la voce solo alla posa, a dito alzato da un pezzo: quel cambio è del dito, e non scorre.
    assertEquals("la posa di un lancio non scorre", 0, FluidNumericTextDiagnostics.rolls)
    assertEquals("cambia di colpo, una volta", 1, FluidNumericTextDiagnostics.jumps)
    assertEquals("dito su, ruota in corsa: la corsa contava come il dito", 1, inFlight)
    assertEquals("posata: il conto è a zero", 0, FluidFingerDrive.active)
    // L'app sceglie un'altra voce: la ruota la segue da sola, e il numero scorre.
    minute = 30
    advance(900)
    assertEquals("cambiato dall'app: scorre", 1, FluidNumericTextDiagnostics.rolls)
    assertEquals(1, FluidNumericTextDiagnostics.jumps)
  }

  @Test
  fun `la ghiera lanciata dal dito, i gradi cambiano di colpo per tutta l'inerzia`() {
    var degrees by mutableFloatStateOf(0f)
    show {
      Column {
        FluidNumericText(degrees.roundToInt())
        FluidRulerDial(
          value = degrees,
          onValueChange = { degrees = it },
          contentDescription = "Raddrizza",
          modifier = Modifier.testTag("ghiera"),
        )
      }
    }
    val b = compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    fling(Offset(b.center.x + 200f, b.center.y), -36f, 0f, steps = 6)
    advance(48)
    val atRelease = degrees
    val inFlight = FluidFingerDrive.active
    advance(5000)
    assertTrue("l'inerzia l'ha portata oltre il dito: $atRelease -> $degrees", degrees > atRelease)
    assertEquals("né col dito né nell'inerzia: nessuno scorrimento", 0, FluidNumericTextDiagnostics.rolls)
    assertTrue("cambia di colpo (${FluidNumericTextDiagnostics.jumps})", FluidNumericTextDiagnostics.jumps >= 2)
    assertEquals("dito su, ghiera in corsa: la corsa contava come il dito", 1, inFlight)
    assertEquals("posata: il conto è a zero", 0, FluidFingerDrive.active)
    val jumps = FluidNumericTextDiagnostics.jumps
    degrees = 0f
    advance(900)
    assertEquals("cambiato dall'app: scorre", 1, FluidNumericTextDiagnostics.rolls)
    assertEquals(jumps, FluidNumericTextDiagnostics.jumps)
  }

  @Test
  fun `un tocco sulla pista del cursore, il valore arriva al sollevamento e cambia di colpo`() {
    var steady by mutableFloatStateOf(18f)
    show {
      Column {
        Box(Modifier.width(220.dp)) { FluidGauge(value = steady, range = 0f..100f, style = FluidGaugeStyle.Arc, label = "Assenze") }
        FluidSlider(
          value = steady / 100f,
          onValueChange = { steady = it * 100f },
          modifier = Modifier.width(360.dp).testTag("cursore"),
        )
      }
    }
    // Lontano dalla maniglia: lo slider dice il valore quando il dito si alza, dopo che l'osservatore ha chiuso il conto.
    compose.onNodeWithTag("cursore").performTouchInput { click(Offset(width * 0.75f, centerY)) }
    advance(900)
    assertTrue("il tocco ha spostato il valore: $steady", steady > 50f)
    assertEquals("un tocco del dito non scorre", 0, FluidNumericTextDiagnostics.rolls)
    assertEquals(1, FluidNumericTextDiagnostics.jumps)
  }
}
