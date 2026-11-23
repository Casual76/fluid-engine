@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.activity

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.dynamic.ActivityPillScene
import dev.antigravity.fluidengine.sample.dynamic.ActivitySceneTags
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F6B.15 · i puntini di «Altro» che sparivano per un fotogramma. Sul Tab S9 in scuro, all'avvio della sincronizzazione
 * (la pillola entra al posto del titolo) e alla sua fine, il disco di «Altro» restava vuoto per un fotogramma. La causa:
 * la barra componeva i vetri nell'ordine dei gruppi, e il vetro della pillola, che nasce prima di «Filtri» e «Altro», li
 * faceva **rifare**: uscivano dal piano e rientravano nello stesso fotogramma, e il pezzo che rientra nasconde il suo
 * contenuto fino al passo dopo del piano. Ora i vetri si compongono nell'ordine in cui sono nati
 * (`FluidToolbarMemory.composeOrder`): chi resta non esce mai dal piano.
 *
 * Si conta dalla diagnostica dell'engine (`piece.detach`), come in `GlassNodeCountTest`; il piano vero
 * (`forceShaderVerdictsForTesting(accept = true)`), senza disegno, nella scena vera «Posta».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1205dp-h753dp-xhdpi")
class FluidActivityBarStaysTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null
  private var mark = 0

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
    FluidGlassDiagnostics.resetForTesting()
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

  /** I vetri di «Filtri» e «Altro» usciti dal piano da [mark]. */
  private fun leftThePlane(): List<String> {
    val logs = ShadowLog.getLogsForTag("FluidGlassFrame")
    val fresh = logs.drop(mark)
    mark = logs.size
    return fresh.map { it.msg }.filter { " kind=piece.detach " in it && ("filtri#" in it || "FluidToolbarOverflowId" in it) }
  }

  private fun click(node: SemanticsNode) {
    val click = node.config[SemanticsActions.OnClick].action
    compose.runOnUiThread { check(click?.invoke() == true) }
  }

  private fun button(label: String) = compose.onAllNodesWithContentDescription(label).fetchSemanticsNodes()
    .first { SemanticsActions.OnClick in it.config }

  @Test fun `Filtri e Altro restano sul piano quando la pillola entra e quando se ne va`() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = ThemeMode.DARK, dynamicColorEnabled = false) }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            ActivityPillScene(onBack = {})
            FluidGlassModalHost(modals, backdrop = null)
          }
        }
      }
    }
    advance(900)
    leftThePlane()
    // La prima sincronizzazione: la pillola entra al posto di «Posta».
    click(compose.onNodeWithTag(ActivitySceneTags.Start).fetchSemanticsNode())
    advance(600)
    assertEquals("all'avvio della sincronizzazione nessuno esce dal piano", emptyList<String>(), leftThePlane())
    // «Altro» aperto e chiuso, poi la fine della sincronizzazione: la pillola se ne va.
    click(button("Altro"))
    advance(1_200)
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(1_500)
    leftThePlane()
    advance(9_000)
    assertEquals("alla fine della sincronizzazione nessuno esce dal piano", emptyList<String>(), leftThePlane())
    // La sincronizzazione dopo, col menu già chiuso: il caso delle riprese.
    click(compose.onNodeWithTag(ActivitySceneTags.Start).fetchSemanticsNode())
    advance(600)
    assertEquals("alla sincronizzazione dopo nessuno esce dal piano", emptyList<String>(), leftThePlane())
  }
}
