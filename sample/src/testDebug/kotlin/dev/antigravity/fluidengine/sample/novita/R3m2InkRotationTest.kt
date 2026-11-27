package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ink.FluidInkEditor
import dev.antigravity.fluidengine.ink.FluidInkSheet
import dev.antigravity.fluidengine.ink.FluidInkState
import dev.antigravity.fluidengine.ink.FluidInkTags
import dev.antigravity.fluidengine.ink.FluidInkTool
import dev.antigravity.fluidengine.ink.ready
import dev.antigravity.fluidengine.ink.rememberFluidInkState
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
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
 * R3 (review R3, Muse 14) · La ricetta di serie della scheda (`rememberFluidInkState()` e `FluidInkEditor`) in
 * un'attivita' **senza** `configChanges` (quella della galleria gestisce da se' la rotazione, e lo nascondeva): il dito
 * disegna, la persona sceglie l'evidenziatore, il telefono gira e l'attivita' si ricrea davvero
 * (`ActivityScenario.recreate()`). I tratti, lo strumento e la cronologia restano, e il foglio nuovo continua a disegnare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class R3m2InkRotationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private var state: FluidInkState? = null

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  /** Quello che fa `onCreate` di un'app che segue la scheda. */
  private val content: @Composable () -> Unit = {
    view = LocalView.current
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          val ink = rememberFluidInkState()
          SideEffect { state = ink }
          FluidInkEditor(ink, FluidInkSheet.blank().ready(), onCancel = {}, onDone = {})
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun line(from: Offset, to: Offset) {
    compose.mainClock.autoAdvance = false
    val finger = MaterialFinger(view)
    finger.down(from)
    advance(16)
    for (i in 1..12) {
      finger.move(from + (to - from) * (i / 12f))
      advance(16)
    }
    finger.up(to)
    advance(300)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
  }

  private fun middle(): Offset = compose.onNodeWithTag(FluidInkTags.Canvas, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

  @Test fun conLaRicettaDiSerieIlDisegnoRestaDopoLaRotazione() {
    compose.setContent(content)
    compose.waitForIdle()
    val c = middle()
    line(c - Offset(120f, 40f), c + Offset(120f, 40f))
    compose.runOnIdle { state!!.selectTool(FluidInkTool.Highlighter) }
    val before = compose.runOnIdle { state!!.strokes.map { it.id } }
    assertEquals("il dito ha disegnato un tratto", 1, before.size)

    // Il telefono gira: l'attivita' si rifa' da capo, come in un'app senza configChanges.
    state = null
    compose.activityRule.scenario.recreate()
    compose.setContent(content)
    compose.waitForIdle()

    val after = compose.runOnIdle { state!! }
    assertEquals("dopo la rotazione il disegno c'e' ancora", before, compose.runOnIdle { after.strokes.map { it.id } })
    assertEquals("lo strumento scelto resta", FluidInkTool.Highlighter, compose.runOnIdle { after.tool })
    assertTrue("annulla c'e' ancora", compose.runOnIdle { after.canUndo })
    // Il foglio nuovo disegna sullo stesso stato.
    val m = middle()
    line(m - Offset(120f, -80f), m + Offset(120f, 120f))
    assertEquals(2, compose.runOnIdle { after.strokes.size })
    assertEquals(FluidInkTool.Highlighter, compose.runOnIdle { after.strokes.last().tool })
  }
}
