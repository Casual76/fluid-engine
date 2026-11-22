package dev.antigravity.fluidengine.sample.activity

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidBarMinimizeBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivity
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHost
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityHostState
import dev.antigravity.fluidengine.ui.fluid.activity.FluidActivityOutcome
import dev.antigravity.fluidengine.ui.fluid.activity.activity
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
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
 * La pillola di attività nell'albero (piano «iOS in Android», F6B.10): al posto del titolo della barra dichiarata, con le
 * parole di TalkBack (il titolo, lo stato, le altre attività, l'azione «Mostra i dettagli», l'esito come area viva) e un
 * bersaglio da almeno 48 dp; la card estesa che nasce al tocco, coi suoi comandi raggiungibili da TalkBack; il titolo
 * che torna quando l'attività finisce; `activity(hidden = true)`; l'anellino a barra ritirata (un tasto che apre la card) e
 * la pillola che torna compatta; una pillola sola con due barre in vista, di serie in quella del dettaglio. Non prova come si vede: i fotogrammi sono le tracce
 * del banco (`FluidActivityTraceTest` di engine-ui).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp-xhdpi")
class FluidActivityPillUiTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun settle(ms: Long = 640) {
    Snapshot.sendApplyNotifications()
    compose.waitForIdle()
    advance(ms)
  }

  private fun SemanticsNodeInteraction.config() = fetchSemanticsNode().config

  /** Una schermata col titolo in linea, la ricerca, l'host delle attività e quello dei modali. */
  private fun screen(activities: FluidActivityHostState, hidden: () -> Boolean = { false }) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          FluidActivityHost(activities) {
            Box(Modifier.fillMaxSize()) {
              FluidScreen(title = "Posta", onBack = {}, toolbar = {
                title(FluidToolbarTitleMode.Inline)
                button("search", FluidIcons.Search, "Cerca", onClick = {})
                if (hidden()) activity(hidden = true)
              }) {}
              FluidGlassModalHost(modals, backdrop = null)
            }
          }
        }
      }
    }
    settle()
  }

  private fun titles(): Int = compose.onAllNodesWithText("Posta").fetchSemanticsNodes().size

  @Test fun `la pillola prende il posto del titolo e TalkBack la legge`() {
    val activities = FluidActivityHostState()
    screen(activities)
    val before = titles()
    assertTrue("il titolo in linea c'è", before >= 1)

    activities.publish(FluidActivity.sync(progress = { 0.5f }))
    // Una seconda della stessa importanza non ruba la pillola: è il contatore.
    activities.publish(FluidActivity.sync(id = "calendar", title = "Calendario"))
    settle()
    val pill = compose.onNodeWithContentDescription("Sincronizzo")
    pill.assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    val config = pill.config()
    assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
    assertEquals("50%, e un'altra attività", config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals("Mostra i dettagli", config.getOrNull(SemanticsActions.OnClick)?.label)
    assertNull("l'avanzamento non è un'area viva", config.getOrNull(SemanticsProperties.LiveRegion))
    assertEquals("il titolo ha ceduto il posto", before - 1, titles())

    // L'esito: l'area viva gentile, poi la pillola esce e il titolo torna.
    activities.remove("calendar")
    activities.finish("sync")
    settle()
    val done = compose.onNodeWithContentDescription("Sincronizzo").config()
    assertEquals("Fatto", done.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(LiveRegionMode.Polite, done.getOrNull(SemanticsProperties.LiveRegion))
    settle(2_400)
    assertEquals(0, compose.onAllNodesWithContentDescription("Sincronizzo").fetchSemanticsNodes().size)
    assertEquals("il titolo è tornato", before, titles())
  }

  @Test fun `al tocco la card nasce dalla pillola e i suoi comandi rispondono`() {
    val activities = FluidActivityHostState()
    screen(activities)
    var paused = 0
    var cancelled = 0
    activities.publish(FluidActivity.transfer("download", progress = { 0.3f }, detail = "3 di 10 file",
      onPause = { paused++ }, onCancel = { cancelled++ }))
    settle()
    compose.onNodeWithContentDescription("Scaricamento").performSemanticsAction(SemanticsActions.OnClick)
    settle()
    assertTrue(activities.expanded)
    // La riga del titolo è un nodo solo, con lo stato.
    val header = compose.onAllNodesWithContentDescription("Scaricamento").fetchSemanticsNodes()
      .map { it.config.getOrNull(SemanticsProperties.StateDescription) }
    assertTrue("la riga della card dice lo stato: $header", "25%" in header)
    compose.onNodeWithText("Pausa").assertHasClickAction().performClick()
    compose.onNodeWithText("Annulla").assertHasClickAction().performClick()
    settle()
    assertEquals(1, paused)
    assertEquals(1, cancelled)
    // L'app toglie l'attività: la card si chiude e la pillola esce.
    activities.remove("download")
    settle()
    assertTrue(!activities.expanded)
    assertEquals(0, compose.onAllNodesWithText("Pausa").fetchSemanticsNodes().size)
  }

  @Test fun `una pagina che non la vuole tiene il titolo`() {
    val activities = FluidActivityHostState()
    screen(activities, hidden = { true })
    val before = titles()
    activities.publish(FluidActivity.sync())
    settle()
    assertEquals(0, compose.onAllNodesWithContentDescription("Sincronizzo").fetchSemanticsNodes().size)
    assertEquals(before, titles())
  }

  @Test fun `un fallimento si legge e resta di piu`() {
    val activities = FluidActivityHostState()
    screen(activities)
    activities.publish(FluidActivity.sync())
    settle()
    activities.finish("sync", FluidActivityOutcome.failed("Rete assente"))
    settle(2_000)
    val failed = compose.onNodeWithContentDescription("Sincronizzo").config()
    assertEquals("Rete assente", failed.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(LiveRegionMode.Polite, failed.getOrNull(SemanticsProperties.LiveRegion))
    settle(2_000)
    assertEquals(0, compose.onAllNodesWithContentDescription("Sincronizzo").fetchSemanticsNodes().size)
  }

  // ---------------------------------------------------------------------------------------------------------------
  // Secondo giro: l'anellino a barra ritirata e una pillola sola con due barre in vista
  // ---------------------------------------------------------------------------------------------------------------

  private fun pills(): List<SemanticsNode> = compose.onAllNodesWithContentDescription("Sincronizzo").fetchSemanticsNodes()

  /** Una lista lunga sotto una barra che si ritira scorrendo (F4A.4). */
  private fun retractingScreen(activities: FluidActivityHostState) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          FluidActivityHost(activities) {
            Box(Modifier.fillMaxSize().testTag("posta")) {
              FluidScreen(title = "Posta", onBack = {}, barMinimize = FluidBarMinimizeBehavior.OnScrollDown, toolbar = {
                title(FluidToolbarTitleMode.Inline)
                button("search", FluidIcons.Search, "Cerca", onClick = {})
              }) {
                items(60) { Text("Messaggio $it", Modifier.fillMaxWidth().height(56.dp)) }
              }
              FluidGlassModalHost(modals, backdrop = null)
            }
          }
        }
      }
    }
    settle()
  }

  @Test fun `a barra ritirata resta l'anellino, che apre la card, e al ritorno torna la pillola`() {
    val activities = FluidActivityHostState()
    retractingScreen(activities)
    activities.publish(FluidActivity.sync(progress = { 0.5f }))
    settle()
    val rest = pills().single().boundsInRoot
    compose.onNodeWithTag("posta").performTouchInput { swipeUp(startY = bottom * 0.75f, endY = bottom * 0.3f) }
    settle(1_200)
    assertEquals("la barra è ritirata", 0, compose.onAllNodesWithContentDescription("Cerca").fetchSemanticsNodes().size)
    // Della pillola resta l'anellino: un nodo solo, un tasto, sopra il posto della pillola.
    val ring = pills().single()
    assertEquals("Mostra i dettagli", ring.config.getOrNull(SemanticsActions.OnClick)?.label)
    assertEquals("50%", ring.config.getOrNull(SemanticsProperties.StateDescription))
    assertTrue("l'anellino sta più in alto della pillola: ${ring.boundsInRoot} su $rest",
      ring.boundsInRoot.center.y < rest.center.y)
    assertTrue("e più piccolo", ring.boundsInRoot.width < rest.width)
    compose.onNodeWithContentDescription("Sincronizzo").performSemanticsAction(SemanticsActions.OnClick)
    settle()
    assertTrue("il tocco apre la card", activities.expanded)
    activities.collapse()
    settle()
    // La barra torna: la pillola torna compatta, al suo posto, e l'anellino esce dall'albero.
    compose.onNodeWithTag("posta").performTouchInput { swipeDown(startY = bottom * 0.3f, endY = bottom * 0.75f) }
    settle(1_200)
    assertEquals(1, compose.onAllNodesWithContentDescription("Cerca").fetchSemanticsNodes().size)
    val back = pills().single().boundsInRoot
    assertEquals(rest.width, back.width, 1f)
    assertEquals(rest.center.y, back.center.y, 1f)
  }

  @Test
  @Config(qualifiers = "w900dp-h700dp-xhdpi")
  fun `con due barre in vista la pillola sta in quella del dettaglio, o in quella che l'app sceglie`() {
    val activities = FluidActivityHostState()
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val modals = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          FluidActivityHost(activities) {
            Row(Modifier.fillMaxSize()) {
              for ((role, title) in listOf(FluidPaneRole.List to "Posta", FluidPaneRole.Detail to "Riunione")) {
                Box(Modifier.weight(1f).fillMaxHeight()) {
                  CompositionLocalProvider(LocalFluidPaneRole provides role) {
                    FluidScreen(title = title, toolbar = { title(FluidToolbarTitleMode.Inline) }) {}
                  }
                }
              }
            }
          }
        }
      }
    }
    settle()
    val half = compose.onRoot().fetchSemanticsNode().boundsInRoot.width / 2f
    val listTitles = compose.onAllNodesWithText("Posta").fetchSemanticsNodes().size
    val detailTitles = compose.onAllNodesWithText("Riunione").fetchSemanticsNodes().size
    activities.publish(FluidActivity.sync())
    settle()
    assertTrue("di serie nella barra del dettaglio", pills().single().boundsInRoot.left >= half)
    assertEquals("la lista tiene il titolo", listTitles, compose.onAllNodesWithText("Posta").fetchSemanticsNodes().size)
    assertEquals(detailTitles - 1, compose.onAllNodesWithText("Riunione").fetchSemanticsNodes().size)
    activities.pane = FluidPaneRole.List
    settle()
    assertTrue("l'app la sposta nella lista", pills().single().boundsInRoot.right <= half)
    assertEquals(detailTitles, compose.onAllNodesWithText("Riunione").fetchSemanticsNodes().size)
  }
}
