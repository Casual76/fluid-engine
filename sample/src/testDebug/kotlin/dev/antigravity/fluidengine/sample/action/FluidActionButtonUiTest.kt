@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.action

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionLabels
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.LocalFluidActionLabels
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Save
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import androidx.compose.runtime.CompositionLocalProvider
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * I tasti con esito nell'albero (piano «iOS in Android», F6B.3a): le parole che TalkBack dice in ogni stato («Invio in
 * corso…», «Inviato», «Invio non riuscito») con l'area viva, il tasto spento mentre lavora (né il dito né il doppio tocco
 * lo fanno ripartire), i bersagli da almeno 48 dp, nelle tre case del tasto: da solo, nella barra dichiarata, nella
 * capsula. E la ripresa dopo la ricreazione con l'esecutore che si salva da sé. Non prova come si vede: i fotogrammi
 * sono di `FluidActionFramesTest`, le catture di `FluidActionCaptureTest`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidActionButtonUiTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  /** Fa passare [ms] di tempo finto a fotogrammi da 16 ms, col filo principale al passo. */
  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun settle() {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
    advance(64)
  }

  private fun SemanticsNodeInteraction.config() = fetchSemanticsNode().config

  /** Il nodo che si chiama [name], uno solo, che è un pulsante con un bersaglio di almeno 48 dp. */
  private fun button(name: String): SemanticsNodeInteraction {
    val node = compose.onNodeWithContentDescription(name)
    node.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    assertEquals(name, Role.Button, node.config().getOrNull(SemanticsProperties.Role))
    return node
  }

  private fun SemanticsNodeInteraction.live(): LiveRegionMode? = config().getOrNull(SemanticsProperties.LiveRegion)
  private fun SemanticsNodeInteraction.disabled(): Boolean = config().contains(SemanticsProperties.Disabled)

  private fun SemanticsNode.spoken(): String? = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()

  /** Le stesse prove per ogni casa: le parole, l'area viva, il tasto spento mentre lavora. */
  private fun checkStates(idle: String, working: String, done: String, failed: String, setState: (FluidActionState) -> Unit, clicks: () -> Int) {
    // A riposo: il nome dell'azione, niente area viva, risponde.
    var node = button(idle)
    assertNull(node.live())
    assertFalse(node.disabled())
    node.performClick()
    settle()
    assertEquals(1, clicks())

    // Al lavoro: lo dice con l'area viva, è spento, il dito e il doppio tocco di TalkBack non lo fanno ripartire.
    setState(FluidActionState.Working())
    settle()
    node = button(working)
    assertEquals(LiveRegionMode.Polite, node.live())
    assertTrue("spento mentre lavora", node.disabled())
    node.performClick()
    settle()
    if (node.config().contains(SemanticsActions.OnClick)) {
      node.performSemanticsAction(SemanticsActions.OnClick)
      settle()
    }
    assertEquals("mentre lavora il tasto non risponde", 1, clicks())

    // Sotto soglia: è già spento e dice che lavora, anche se la faccia è ancora quella di riposo.
    setState(FluidActionState.Working(null, pending = true))
    settle()
    assertTrue(button(working).disabled())

    // Riuscito e non riuscito: le parole dell'esito, nell'area viva, e il tasto risponde di nuovo.
    setState(FluidActionState.Succeeded)
    settle()
    node = button(done)
    assertEquals(LiveRegionMode.Polite, node.live())
    assertFalse(node.disabled())
    setState(FluidActionState.Failed)
    settle()
    node = button(failed)
    assertEquals(LiveRegionMode.Polite, node.live())
    node.performClick()
    settle()
    assertEquals("dopo l'esito si puo riprovare", 2, clicks())

    // L'avanzamento come stato, a passi.
    setState(FluidActionState.Working { 0.6f })
    settle()
    assertEquals("50%", button(working).config().getOrNull(SemanticsProperties.StateDescription))
  }

  @Test fun `da solo, TalkBack dice ogni stato e mentre lavora il tasto e spento`() {
    var state by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    var clicks = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize().background(Color.Gray), contentAlignment = Alignment.Center) {
          FluidActionButton(FluidActionPreset.Send, state, onClick = { clicks++ })
        }
      }
    }
    settle()
    checkStates("Invia", "Invio in corso…", "Inviato", "Invio non riuscito", { state = it }, { clicks })
  }

  @Test fun `con la scritta, il nome a riposo e quello dell'app e la scritta non conta due volte`() {
    var state by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    var clicks = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize().background(Color.Gray), contentAlignment = Alignment.Center) {
          FluidActionButton(FluidActionPreset.Save, state, onClick = { clicks++ }, label = "Salva la nota")
        }
      }
    }
    settle()
    checkStates("Salva la nota", "Salvataggio in corso…", "Salvato", "Salvataggio non riuscito", { state = it }, { clicks })
    // La scritta visibile non si somma al nome: un nodo solo, con le parole dello stato.
    assertEquals(1, compose.onAllNodesWithContentDescription("Salvataggio in corso…").fetchSemanticsNodes().size)
  }

  @Test fun `nella barra dichiarata e nella capsula le stesse parole e lo stesso tasto spento`() {
    var bar by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    var capsule by mutableStateOf<FluidActionState>(FluidActionState.Idle)
    var barClicks = 0
    var capsuleClicks = 0
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidScreen(title = "Nota", toolbar = {
          button("save", FluidIcons.Save, "Salva", onClick = { barClicks++ }, action = FluidActionPreset.Save, state = bar)
        }) {}
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
          FluidFloatingCapsule {
            FluidCapsuleAction(FluidIcons.Send, "Invia", onClick = { capsuleClicks++ }, action = FluidActionPreset.Send, state = capsule)
          }
        }
      }
    }
    settle()
    advance(400)
    checkStates("Salva", "Salvataggio in corso…", "Salvato", "Salvataggio non riuscito", { bar = it }, { barClicks })
    checkStates("Invia", "Invio in corso…", "Inviato", "Invio non riuscito", { capsule = it }, { capsuleClicks })
  }

  @Test fun `le parole tradotte arrivano dalla radice`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidActionLabels provides FluidActionLabels.english()) {
          FluidActionButton(FluidActionPreset.Send, FluidActionState.Failed, onClick = {})
        }
      }
    }
    settle()
    button("Couldn't send")
  }

  @Test fun `l'esecutore riprende l'esito dopo la ricreazione e poi torna a riposo`() {
    val restore = StateRestorationTester(compose)
    restore.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val save = rememberFluidActionRunner(FluidActionPreset.Save)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          FluidActionButton(FluidActionPreset.Save, save.state, onClick = { save.run { } })
        }
      }
    }
    settle()
    button("Salva").performClick()
    settle()
    // Un lavoro istantaneo: niente anello, subito l'esito.
    button("Salvato")
    advance(600)
    restore.emulateSavedInstanceStateRestore()
    settle()
    button("Salvato")
    // La tenuta (1,5 s) riparte da dove era, non da capo: a 1,6 s dal tocco è di nuovo a riposo.
    advance(1_000)
    settle()
    button("Salva")
  }
}
