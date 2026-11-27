@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.sample.GlassBenchDevice
import dev.antigravity.fluidengine.sample.GlassBenchHeap
import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import dev.antigravity.fluidengine.ui.theme.RouteMotionSignals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F7.32 · L'aiuto di [BalanceOrderTest]: mette nei segnali del movimento fra pagine di serie (uno per processo, quelli
 * che `FluidPageHost` timbra quando una pagina si muove) il timbro che una scena di «Misure» lascia alla prova dopo,
 * «il vetro resta al minimo fino a [Property] ms di `SystemClock.uptimeMillis()`», oppure lo toglie (0). Fuori da
 * [BalanceOrderTest] la proprietà non c'è e la prova si salta: nel lotto non tocca niente.
 *
 * La stessa configurazione di [F8BalanceTest], così gira nella stessa sandbox di Robolectric e i segnali di serie sono
 * gli stessi.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
  sdk = [35],
  qualifiers = "w411dp-h891dp-mdpi",
  application = GlassBenchDevice::class,
  shadows = [
    GlassBenchHeap::class, CountingRenderNode::class, CountingBitmap::class, CountingRenderEffect::class,
    CountingRuntimeShader::class,
  ],
)
class RouteStampForOrderTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun timbro() {
    val until = System.getProperty(Property)?.toLongOrNull()
    assumeTrue("solo dentro BalanceOrderTest", until != null)
    var signals: RouteMotionSignals? = null
    compose.setContent { signals = LocalRouteMotionSignals.current }
    compose.waitForIdle()
    checkNotNull(signals).glassRelaxAt = checkNotNull(until)
  }

  companion object {
    const val Property = "fluid.bilanci.timbro"
  }
}
