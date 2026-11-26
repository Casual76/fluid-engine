package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.GalleryLaunch
import dev.antigravity.fluidengine.sample.TracedFluidHaptics
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticsImpl
import dev.antigravity.fluidengine.ui.haptics.HapticPort
import dev.antigravity.fluidengine.ui.haptics.HapticPrimitiveStep
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * F8.15d · L'interruttore dell'aptica per le misure sul tablet (`gallery.haptics off`, solo debug e bench) e il righello
 * senza aptica: il valore arriva uguale, il motore non vibra mai. Con l'aptica accesa si conta quante vibrazioni chiede
 * un trascinamento di dieci tacche (le sezioni `FluidHaptics.play` della traccia della bench sono queste).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p15dHapticsSwitchTest {

  @get:Rule
  val compose = createComposeRule()

  private lateinit var view: View
  private var degrees by mutableFloatStateOf(0f)

  /** Una porta finta: conta le vibrazioni chieste al sistema. */
  private class CountingPort : HapticPort {
    var vibrations = 0
    var platform = 0
    override val hasVibrator = true
    override fun supportedPrimitives(ids: IntArray): Set<Int> = ids.toSet()
    override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean {
      vibrations++
      return true
    }
    override fun platform(type: HapticFeedbackType) {
      platform++
    }
    override val systemHapticsEnabled = true
    override val powerSave = false
  }

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  @Test
  fun `l'extra gallery_haptics spegne e riaccende l'aptica`() {
    fun launch(vararg pairs: Pair<String, String>) = GalleryLaunch.parse { key -> pairs.firstOrNull { it.first == key }?.second }
    assertFalse(launch("gallery.haptics" to "off").settings().hapticsEnabled)
    assertFalse(launch("gallery.haptics" to " OFF ").settings().hapticsEnabled)
    assertTrue(launch("gallery.haptics" to "on").settings(EngineSettings(hapticsEnabled = false)).hapticsEnabled)
    assertTrue("senza extra resta di serie", launch().settings().hapticsEnabled)
    assertTrue("un valore che non si riconosce si ignora", launch("gallery.haptics" to "boh").settings().hapticsEnabled)
    assertFalse("l'aptica da sola e' una richiesta", launch("gallery.haptics" to "off").isEmpty)
    val dark = launch("gallery.theme" to "dark", "gallery.haptics" to "off").settings()
    assertFalse("col tema chiesto insieme", dark.hapticsEnabled)
  }

  private fun dragWith(on: Boolean): Pair<CountingPort, TracedFluidHaptics> {
    val port = CountingPort()
    val traced = TracedFluidHaptics(FluidHapticsImpl(port, enabled = { on }))
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidHaptics provides traced) {
          Box(Modifier.fillMaxSize()) {
            FluidRulerDial(
              value = { degrees },
              onValueChange = { degrees = it },
              contentDescription = "Esposizione",
              modifier = Modifier.testTag("ghiera"),
            )
          }
        }
      }
    }
    advance(600)
    val b = compose.onNodeWithTag("ghiera", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val finger = MaterialFinger(view)
    var at = Offset(b.center.x, b.center.y)
    finger.down(at)
    advance(16)
    repeat(49) {
      at += Offset(-4f, 0f)
      finger.move(at)
      advance(16)
    }
    advance(160)
    finger.up(at)
    advance(1200)
    return port to traced
  }

  @Test
  fun `il righello con l'aptica accesa la chiede a ogni tacca`() {
    val (port, traced) = dragWith(on = true)
    println("F8p15d aptica accesa: ${traced.plays} chiamate, ${port.vibrations} vibrazioni, ${port.platform} di sistema")
    assertEquals(10f, degrees, 1e-4f)
    assertTrue("chiamate all'aptica: ${traced.plays}", traced.plays >= 5)
    assertTrue("vibrazioni: ${port.vibrations + port.platform}", port.vibrations + port.platform >= 1)
  }

  @Test
  fun `il righello con l'aptica spenta porta il valore e non vibra mai`() {
    val (port, traced) = dragWith(on = false)
    println("F8p15d aptica spenta: ${traced.plays} chiamate, ${port.vibrations} vibrazioni, ${port.platform} di sistema")
    assertEquals("il valore arriva uguale", 10f, degrees, 1e-4f)
    assertEquals("nessuna vibrazione", 0, port.vibrations + port.platform)
  }
}
