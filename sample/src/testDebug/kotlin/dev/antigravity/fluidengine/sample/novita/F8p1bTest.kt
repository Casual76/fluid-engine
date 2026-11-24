@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidGauge
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidNumericTextDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.1b · Le scelte di Alessio del 24 novembre: il badge scorre come gli altri numeri, il numero del quadrante scorre, e un
 * valore che cambia troppo spesso (i decibel) non scorre ma cambia di colpo, come prima.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w400dp-h800dp-xhdpi")
class F8p1bTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(millis: Long) {
    repeat(((millis + 15) / 16).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  private fun show(content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent { FluidTheme(settings = remember { EngineSettings() }) { content() } }
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
}
