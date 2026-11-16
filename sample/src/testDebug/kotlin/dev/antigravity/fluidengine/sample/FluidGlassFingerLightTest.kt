package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.FluidGlassProbeDiagnostics
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
 * La luce del dito nel piano flottante (F3.10, scelta di Alessio del 16 novembre), nell'albero: un tasto di vetro del
 * contenitore che, premuto, fa nascere un secondo vetro porta il bagliore della pressione dentro il viaggio; lo stesso
 * cambio scritto dall'app senza un tocco parte dal livello del carattere (nessun bagliore).
 *
 * Che cosa prova: il tocco vero (giù, tenuto, su: il click arriva al rilascio) arriva al conducente del contenitore, che
 * fa cominciare il campo del tasto dal bagliore che il vetro fermo mostrava (`FluidGlassContainerDiagnostics.lastFinger`).
 * Come si vede (la campana che riempie il vetro e si spegne col carattere) lo dicono `FluidGlassJourneyLightTest` (la
 * curva) e la tavola dal banco; sul tablet `sessioni/f3-10/riprese.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidGlassFingerLightTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private var diagnosticsWere = false

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After
  fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassProbeDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  @Test
  fun aTouchThatOpensAJourneyLightsItsFieldAndAChangeWithoutTouchDoesNot() {
    var two by mutableStateOf(false)
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val page = rememberGlassBackdrop()
        val ns = rememberFluidGlassNamespace()
        Box(Modifier.fillMaxSize()) {
          Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color(0xFF7FA7D9)))
          FluidGlassContainer(Modifier.padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FluidGlassIconButton(
                onClick = { two = !two },
                modifier = Modifier.testTag("tasto").fluidGlassId("tasto", ns),
                backdrop = page,
              ) { Box(Modifier.size(20.dp).background(Color(0xFF121214))) }
              if (two) Box(Modifier.size(44.dp).fluidGlassId("nuovo", ns).fluidGlass(backdrop = page))
            }
          }
        }
      }
    }
    advance(1_000)
    assertEquals("la prima misura non viaggia", 0L, FluidGlassContainerDiagnostics.totalJourneys)

    // Il dito scende sul tasto, lo tiene, e si alza: il click arriva al rilascio, lo stato cambia, il viaggio parte.
    compose.onNodeWithTag("tasto").performTouchInput { down(center) }
    advance(160)
    compose.onNodeWithTag("tasto").performTouchInput { up() }
    advance(64)
    assertEquals("il tocco ha aperto un viaggio", 1L, FluidGlassContainerDiagnostics.totalJourneys)
    val lit = FluidGlassContainerDiagnostics.lastFinger
    assertTrue("il campo del tasto toccato parte dal bagliore della pressione: $lit", lit > 0.05f)
    advance(1_000)

    // Lo stesso cambio scritto dall'app, senza un tocco: il viaggio parte dal livello del carattere.
    two = false
    Snapshot.sendApplyNotifications()
    advance(64)
    assertEquals(2L, FluidGlassContainerDiagnostics.totalJourneys)
    assertEquals("senza tocco nessun bagliore", 0f, FluidGlassContainerDiagnostics.lastFinger, 0f)
  }
}
