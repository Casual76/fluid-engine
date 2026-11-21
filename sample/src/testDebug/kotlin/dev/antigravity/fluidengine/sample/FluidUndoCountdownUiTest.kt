@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.graphics.RenderNode
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.catalog.NotificationSendDemo
import dev.antigravity.fluidengine.sample.catalog.SendDraftText
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationCountdown
import dev.antigravity.fluidengine.ui.fluid.FluidUndoCountdown
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidUndoCountdownState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
@Config(sdk = [35], qualifiers = "w400dp-h720dp-xhdpi")
class FluidUndoCountdownUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(millis: Long) {
    repeat(((millis + 15) / 16).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  @Test fun sendClosesComposerAndUndoRestoresTheDraft() {
    compose.mainClock.autoAdvance = false
    compose.setContent { NotificationSendDemo() }
    advance(850)
    compose.onNodeWithContentDescription("Nuova mail").performClick()
    advance(850)
    compose.onNodeWithContentDescription("Invia").performClick()
    advance(1_150)
    compose.onNodeWithText("Riunione di domani").assertIsDisplayed()
    compose.onNodeWithContentDescription("Invia").assertDoesNotExist()
    compose.onNodeWithContentDescription("Annulla invio,", substring = true)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
      .performClick()
    advance(850)
    compose.onNodeWithText(SendDraftText).assertIsDisplayed()
    compose.onNodeWithContentDescription("Invia").assertIsDisplayed()
  }

  @Test fun expiryBringsBackTheOriginalToolbarFace() {
    compose.mainClock.autoAdvance = false
    compose.setContent { NotificationSendDemo() }
    advance(850)
    compose.onNodeWithContentDescription("Nuova mail").performClick()
    advance(850)
    compose.onNodeWithContentDescription("Invia").performClick()
    advance(1_150)
    compose.onNodeWithContentDescription("Annulla invio,", substring = true).assertIsDisplayed()
    advance(8_500)
    compose.onNodeWithContentDescription("Annulla invio,", substring = true).assertDoesNotExist()
    compose.onNodeWithContentDescription("Nuova mail").assertIsDisplayed()
  }

  @Test fun capsuleAndActionShareTheSavedWindowAndAcceptOneUndo() {
    val restoration = StateRestorationTester(compose)
    val timing = FluidNotificationCountdown(8_000L, SystemClock.elapsedRealtime())
    var undos = 0
    var idleClicks = 0
    compose.mainClock.autoAdvance = false
    restoration.setContent {
      FluidTheme(EngineSettings()) {
        val state = rememberFluidUndoCountdownState(timing)
        val countdown = remember(state) { FluidUndoCountdown(state, { undos++ }, accessibilityLabel = "Annulla invio") }
        Row {
          FluidCapsuleAction(FluidIcons.Add, "Capsula", onClick = { idleClicks++ }, undo = countdown)
          FluidActionButton(FluidActionPreset.Save, FluidActionState.Idle, onClick = { idleClicks++ },
            undo = countdown.copy(accessibilityLabel = "Annulla salvataggio"))
        }
      }
    }
    advance(2_700)
    compose.mainClock.autoAdvance = true
    restoration.emulateSavedInstanceStateRestore()
    compose.mainClock.autoAdvance = false
    advance(500)
    compose.onNodeWithContentDescription("Annulla invio,", substring = true).assertIsDisplayed()
    compose.onNodeWithContentDescription("Annulla salvataggio,", substring = true).performClick()
    advance(600)
    assertEquals(1, undos)
    compose.onNodeWithContentDescription("Annulla invio,", substring = true).assertDoesNotExist()
    compose.onNodeWithContentDescription("Capsula").performClick()
    assertEquals(1, idleClicks)
  }

  @Test fun tickingTheFillKeepsTheBackdropCaptureStill() {
    val diagnostics = FluidGlassDiagnostics.enabled
    val warmUp = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    try {
      val timing = FluidNotificationCountdown(8_000L, SystemClock.elapsedRealtime())
      compose.mainClock.autoAdvance = false
      compose.setContent {
        FluidTheme(remember { EngineSettings() }) {
          val backdrop = rememberGlassBackdrop()
          val countdown = FluidUndoCountdown(rememberFluidUndoCountdownState(timing), {})
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFFCEE3ED)))
            FluidActionButton(FluidActionPreset.Save, FluidActionState.Idle, onClick = {},
              backdrop = backdrop, undo = countdown)
          }
        }
      }
      advance(800)
      val view = compose.activity.window.decorView
      val node = RenderNode("F6B.4")
      node.setPosition(0, 0, view.width, view.height)
      fun draw() = compose.runOnUiThread {
        val canvas = node.beginRecording()
        try { view.draw(canvas) } finally { node.endRecording() }
      }
      try {
        val baseline = FluidGlassDiagnostics.totalCaptures
        draw()
        advance(16)
        draw()
        val afterSettle = FluidGlassDiagnostics.totalCaptures
        assertTrue("la scena deve campionare almeno una volta", afterSettle > baseline)
        repeat(150) {
          advance(16)
          draw()
        }
        assertEquals(afterSettle, FluidGlassDiagnostics.totalCaptures)
      } finally {
        node.discardDisplayList()
      }
    } finally {
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.enabled = diagnostics
      FluidGlassDiagnostics.warmUpEnabled = warmUp
    }
  }
}
