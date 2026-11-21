@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.action

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAnimation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionGlyphContent
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionLook
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionPresentation
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.CloudUpload
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
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
 * Il conto delle catture dei tasti con esito (F6B.3a, sottocategoria F6B-i): l'anello che gira, l'arco che avanza e la
 * percentuale sono **contenuto**, e non ricatturano il vetro né ne reinstallano la catena. Le stesse sonde di F6.2
 * (`FingerGlowCaptureTest`): `FluidGlassDiagnostics.totalCaptures` per le catture e `glassBodyProbe` per i corpi
 * installati, con un `draw()` della finestra a ogni fotogramma.
 *
 * In scena le tre case del tasto: due posti di vetro in un contenitore (come i gruppi della barra dichiarata), il tasto
 * da solo e la capsula, tutti sopra lo stesso fondale. Il conto è diviso in quattro tratti: il riposo (controllo: deve
 * già essere zero), lo scambio verso il lavoro, il lavoro fermo (60 fotogrammi con l'anello che gira e la percentuale che
 * sale a ogni fotogramma) e il fallimento con la scossa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h480dp-mdpi")
class FluidActionCaptureTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Composable
  private fun Piece(preset: FluidActionPreset, state: FluidActionState, page: GlassBackdropState) {
    Box(Modifier.size(46.dp).fluidGlass(FluidGlass.Regular.interactive(), backdrop = page), contentAlignment = Alignment.Center) {
      val presentation = rememberFluidActionPresentation(preset, state, live = true)
      FluidActionGlyphContent(preset, state, presentation, FluidActionLook(28.dp, Color.Black, Color.Red, reducedMotion = false))
    }
  }

  @Test fun `anello e percentuale non ricatturano il vetro`() {
    val enabled = FluidGlassDiagnostics.enabled
    val warm = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    val previousProbe = glassBodyProbe
    var bodies = 0
    glassBodyProbe = { _, _ -> bodies++ }
    // Uno stato di Compose, come l'avanzamento dell'esecutore: il disegno lo legge e si ridisegna da solo.
    var progress by mutableFloatStateOf(0f)
    val reader = { progress }
    var measured by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    var spinning by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    try {
      compose.mainClock.autoAdvance = false
      compose.setContent {
        FluidTheme(settings = remember { EngineSettings() }) {
          val page = rememberGlassBackdrop()
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.Gray))
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
              FluidGlassContainer(spacing = 0.dp, animation = FluidGlassAnimation.None) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                  Piece(FluidActionPreset.Download, measured, page)
                  Piece(FluidActionPreset.Save, spinning, page)
                }
              }
              FluidActionButton(FluidActionPreset.Save, measured, onClick = {}, backdrop = page)
              FluidFloatingCapsule(backdrop = page) {
                FluidCapsuleAction(FluidIcons.CloudUpload, "Carica", onClick = {}, action = FluidActionPreset.Upload, state = measured)
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
      val node = RenderNode("F6B.3a")
      node.setPosition(0, 0, view.width, view.height)
      fun draw() = compose.runOnUiThread {
        val canvas = node.beginRecording()
        try { view.draw(canvas) } finally { node.endRecording() }
      }
      try {
        val start = FluidGlassDiagnostics.totalCaptures
        draw(); frame(); draw()
        val ready = FluidGlassDiagnostics.totalCaptures
        assertTrue("controllo positivo: le catture iniziali dei vetri in scena, delta ${ready - start}", ready - start >= 3)
        assertTrue("corpi installati prima del lavoro", bodies >= 3)

        // 1. Il riposo: niente si muove, niente si ricattura (controllo del conto).
        val restBodies = bodies
        repeat(30) { frame(); draw() }
        val rested = FluidGlassDiagnostics.totalCaptures
        assertEquals("a riposo nessuna cattura", ready, rested)

        // 2. Lo scambio verso il lavoro: le facce si sfocano e si incrociano, sono contenuto.
        compose.runOnUiThread {
          measured = FluidActionState.Working(reader)
          spinning = FluidActionState.Working()
        }
        repeat(30) { frame(); draw() }
        val swapped = FluidGlassDiagnostics.totalCaptures
        val swapBodies = bodies

        // 3. Il lavoro fermo: l'anello gira, l'arco e le cifre avanzano a ogni fotogramma, senza ricomporre.
        repeat(60) { i ->
          progress = (i + 1) / 60f
          frame(); draw()
        }
        val steady = FluidGlassDiagnostics.totalCaptures
        val steadyBodies = bodies

        // 4. Il fallimento: la tinta d'errore e la scossa del solo contenuto.
        compose.runOnUiThread {
          measured = FluidActionState.Failed
          spinning = FluidActionState.Failed
        }
        repeat(40) { frame(); draw() }
        val failed = FluidGlassDiagnostics.totalCaptures
        val failedBodies = bodies
        println("F6B.3a catture: iniziali=${ready - start} riposo=${rested - ready} scambio=${swapped - rested} " +
          "lavoro=${steady - swapped} fallimento=${failed - steady}; corpi: iniziali=$restBodies scambio=${swapBodies - restBodies} " +
          "lavoro=${steadyBodies - swapBodies} fallimento=${failedBodies - steadyBodies}")
        assertEquals("lo scambio delle facce non ricattura il vetro", rested, swapped)
        assertEquals("anello e percentuale non ricatturano il vetro", swapped, steady)
        assertEquals("anello e percentuale non reinstallano la catena", swapBodies, steadyBodies)
        assertEquals("la scossa del contenuto non ricattura il vetro", steady, failed)
      } finally {
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
