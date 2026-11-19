package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
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
 * Il contestuale in RTL visto da S4b sul Tab S9 (NO 5), con lo stesso impianto di [FluidMenuBirthPickTest]: il vetro si
 * posava dove deve, accanto all'elemento (coordinate della finestra), e le voci dall'altra parte dello schermo, perche' il
 * pannello era posato con `Modifier.offset`, che in RTL specchia la x. Il ritorno dopo una scelta (NO 6) si misura sulla
 * sagoma vera, al banco JVM (`ContestualeCongedoTest`): qui le voci escono dalla semantica appena si sceglie.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidContextMenuS4bTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat(ms / 16) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      compose.waitForIdle()
    }
  }

  /** Una riga stretta al bordo d'inizio, col suo contestuale; [direction] per tutto l'albero. */
  private fun scene(direction: LayoutDirection, picks: MutableList<String>) {
    compose.setContent {
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      CompositionLocalProvider(LocalLayoutDirection provides direction) {
        FluidTheme(settings = remember { EngineSettings() }) {
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            Box(Modifier.fillMaxSize()) {
              Box(
                Modifier
                  .align(Alignment.TopStart)
                  .padding(top = 240.dp)
                  .size(width = 120.dp, height = 72.dp)
                  .fluidContextMenu(actions = {
                    listOf("Copia", "Sposta", "Cestina").map { label -> FluidContextAction(label) { picks += label } }
                  })
                  .testTag("riga"),
              )
              // Sonda del gesto, come in FluidTransitionSourceTest: il backend Skia di Robolectric non disegna il vetro.
              FluidGlassModalHost(state = host, backdrop = backdrop, modifier = Modifier.drawWithContent { })
            }
          }
        }
      }
    }
    advance(256)
  }

  private fun rowBounds(): Rect = compose.onNodeWithTag("riga").fetchSemanticsNode().boundsInRoot

  private fun waitForMenu(label: String) {
    var waited = 0
    while (compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) {
      advance(16)
      waited += 16
      assertTrue("il menu non si apre", waited < 2_000)
    }
  }

  @Test fun `in RTL le voci stanno nel vetro, accanto all'elemento`() {
    val picks = mutableStateListOf<String>()
    scene(LayoutDirection.Rtl, picks)
    val row = rowBounds()
    val screenMid = compose.onRoot().fetchSemanticsNode().boundsInRoot.center.x
    // In RTL la riga d'inizio sta a destra.
    assertTrue("la riga e' a destra in RTL: $row", row.center.x > screenMid)
    compose.onRoot().performTouchInput { longClick(row.center) }
    waitForMenu("Copia")
    advance(1_400)
    val voice = compose.onNodeWithText("Copia").fetchSemanticsNode().boundsInRoot
    // Il pannello si apre accanto alla riga, dentro i margini: la voce sta dalla sua parte dello schermo, non specchiata.
    assertTrue("la voce sta dalla parte della riga: voce $voice, riga $row", voice.center.x > screenMid)
    assertTrue("la voce sta sotto la riga, nel pannello: voce $voice, riga $row", voice.top >= row.bottom - 1f)
  }
}
