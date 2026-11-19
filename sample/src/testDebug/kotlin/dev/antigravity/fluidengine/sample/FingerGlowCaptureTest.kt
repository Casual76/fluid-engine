@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAnimation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import dev.antigravity.fluidengine.ui.glass.interaction.FingerGlow
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp-mdpi")
class FingerGlowCaptureTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** Tre lastre vere: muoviamo solo il dito per separare il costo della luce dalla geometria di F6.1. */
  @Test fun lightAloneLeavesAllThreeRecordedCapturesAlone() {
    val enabled = FluidGlassDiagnostics.enabled
    val warm = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    val previousProbe = glassBodyProbe
    var bodies = 0
    glassBodyProbe = { _, _ -> bodies++ }
    try {
      lateinit var finger: FingerGlow
      compose.mainClock.autoAdvance = false
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val page = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.Gray))
            FluidGlassContainer(spacing = 0.dp, animation = FluidGlassAnimation.None) {
              val host = LocalFluidGlassContainer.current!!
              SideEffect { finger = host.fingerLight!! }
              Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(3) {
                  Box(Modifier.size(44.dp).testTag("lastra-$it")
                    .fluidGlass(FluidGlass.Regular.interactive(it == 0), backdrop = page))
                }
              }
            }
          }
        }
      }
      fun frame() {
        compose.mainClock.advanceTimeByFrame()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
        Snapshot.sendApplyNotifications()
      }
      repeat(36) { frame() }
      val view = compose.activity.window.decorView
      val node = RenderNode("F6.2")
      node.setPosition(0, 0, view.width, view.height)
      fun draw() = compose.runOnUiThread {
        val canvas = node.beginRecording()
        try { view.draw(canvas) } finally { node.endRecording() }
      }
      try {
        val before = FluidGlassDiagnostics.totalCaptures
        draw()
        frame()
        draw()
        val ready = FluidGlassDiagnostics.totalCaptures
        assertTrue("controllo positivo: tre catture iniziali, delta ${ready - before}", ready - before >= 3)
        val initialBodies = bodies
        assertTrue("corpi installati prima del gesto", initialBodies >= 3)
        val litFields = mutableSetOf<Rect>()
        finger.onFieldDrawForTesting = { litFields.add(it) }
        val key = Any()
        val bounds = Rect(0f, 0f, 44f, 44f)
        compose.runOnUiThread { finger.down(key, bounds, Offset(36f, 22f), false) }
        draw()
        assertTrue(finger.levels.local > 0f)
        repeat(24) { frame(); draw() }
        assertTrue(finger.levels.neighbours > 0.99f)
        compose.runOnUiThread { finger.down(key, bounds, Offset(28f, 18f), false); finger.up(key) }
        repeat(38) { frame(); draw() }
        assertEquals(0f, finger.levels.local, 0f)
        val end = FluidGlassDiagnostics.totalCaptures
        assertEquals("tutti e tre i campi hanno davvero disegnato la luce", 3, litFields.size)
        assertEquals("lo strato del dito non reinstalla la catena", initialBodies, bodies)
        println("F6.2: catture iniziali=${ready - before}, disegni del gesto=63, ulteriori catture=${end - ready}")
        assertEquals(ready, end)
        // Ora entra dal tasto vero. Il primo draw dopo il down deve trovare già accesa la luce.
        litFields.clear()
        compose.onNodeWithTag("lastra-0").performTouchInput { down(center) }
        frame(); draw()
        assertTrue("contatto al primo frame", finger.levels.local > 0f && litFields.isNotEmpty())
        compose.onNodeWithTag("lastra-0").performTouchInput { up() }
        repeat(38) { frame() }
        assertEquals(0f, finger.levels.local, 0f)
      } finally {
        finger.onFieldDrawForTesting = null
        node.discardDisplayList()
      }
    } finally {
      glassBodyProbe = previousProbe
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.enabled = enabled
      FluidGlassDiagnostics.warmUpEnabled = warm
    }
  }
}
