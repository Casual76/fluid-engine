package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
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
 * Il dito sceglie la voce che vede anche a meta' nascita (sessione «contestuale», fase tasto). Il menu del gruppo nasce come la
 * graffetta di Apple e il contestuale come il menu sulla foto: per qualche centinaio di millisecondi il corpo e' staccato dalla
 * sorgente e le voci sono rimpicciolite nella goccia, lontane dai riquadri a riposo su cui la scelta cerca la voce. Il dito che
 * tiene premuto e scorre subito sulla voce (il gesto di S4 NO 6) deve scegliere quella che vede: la selezione riporta il punto
 * attraverso la mappa delle voci (`FluidMenuSelection.pointerMap`). Lo stesso impianto di [FluidTransitionSourceTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidMenuBirthPickTest {
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

  @Test fun `rilasciato a meta' nascita sceglie la voce che si vede, nel gruppo e nel contestuale`() {
    val picks = mutableStateListOf<String>()
    compose.mainClock.autoAdvance = false
    compose.setContent {
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            val groupActions = {
              listOf("Condividi", "Aggiungi ai preferiti", "Elimina").map { label -> FluidContextAction(label) { picks += label } }
            }
            val rowActions = {
              listOf("Copia", "Sposta", "Cestina").map { label -> FluidContextAction(label) { picks += label } }
            }
            FluidScreen("Foto", toolbar = {
              group("tools", placement = FluidToolbarPlacement.BottomBar) {
                button("share", FluidIcons.MoreHoriz, "Condividi foto", onClick = {})
                button("star", FluidIcons.MoreHoriz, "Preferiti", onClick = {})
                action("menu", FluidIcons.MoreHoriz, "Menu del gruppo", menu = groupActions)
              }
            }) {
              item {
                Box(Modifier.fillMaxWidth().height(72.dp).fluidContextMenu(actions = rowActions).testTag("riga"))
              }
            }
            // Sonda del gesto, come in FluidTransitionSourceTest: il backend Skia di Robolectric non disegna il vetro.
            FluidGlassModalHost(state = host, backdrop = backdrop, modifier = Modifier.drawWithContent { })
          }
        }
      }
    }
    advance(256)
    // Il rilascio a ~300 ms dalla nascita (le voci a ~23 dp dai loro riquadri, mezza riga) e a ~200 ms (~45-50 dp: senza la
    // mappa del dito cadrebbe di sicuro su un'altra riga). Gli scarti nel tempo li stampa `TastoTraceTest`.
    val cases = listOf(
      Triple("Menu del gruppo", "Condividi", 288),
      Triple("Menu del gruppo", "Elimina", 176),
      Triple("riga", "Copia", 288),
      Triple("riga", "Cestina", 176),
    )
    for ((source, label, afterOpen) in cases) {
      val root = compose.onRoot()
      val node = if (source == "riga") compose.onNodeWithTag(source) else compose.onNodeWithContentDescription(source)
      val downAt = node.fetchSemanticsNode().boundsInRoot.center
      root.performTouchInput { down(downAt) }
      // Il click lungo apre il menu; da li' ~300 ms di nascita: il corpo e' ancora staccato e le voci nella goccia.
      var waited = 0
      while (compose.onAllNodesWithText(label).fetchSemanticsNodes().isEmpty()) {
        advance(16)
        waited += 16
        assertTrue("il menu di $source non si apre", waited < 2_000)
      }
      advance(afterOpen)
      // Dove si vede la voce adesso (i limiti del nodo con lo spostamento e la scala del layer delle voci).
      val seen = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center
      // Nello stesso istante in cui si e' letta la voce: a ~200 ms il corpo si allarga in fretta, e i 16 ms di serie fra due
      // eventi portano il dito dove la voce era un fotogramma prima.
      root.performTouchInput {
        moveTo(seen, delayMillis = 0)
        up()
      }
      advance(900)
      assertEquals("$label, rilasciata a ~${afterOpen + 16} ms dalla nascita dove si vede", label, picks.lastOrNull())
      compose.onNodeWithText(label).assertDoesNotExist()
    }
    assertEquals(cases.map { it.second }, picks)
  }
}
