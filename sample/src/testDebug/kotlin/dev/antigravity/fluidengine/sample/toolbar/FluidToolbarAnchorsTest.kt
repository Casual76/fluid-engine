package dev.antigravity.fluidengine.sample.toolbar

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** I due agganci F4A.5 nell'albero vero: registro del piano e sottotitolo ricevuto dalla FluidScreen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidToolbarAnchorsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var enabledBefore = false

  @Before fun start() {
    enabledBefore = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }
  @After fun stop() {
    FluidGlassDiagnostics.enabled = enabledBefore
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }
  private fun advance(ms: Long) {
    repeat((ms / 16 + 1).toInt()) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  // F6B.11 (Alessio, 22 novembre 2026: «un tasto della barra che cambia resta un vetro solo»): l'indietro della figlia
  // nasce **nello stesso posto** di «Modifica» della radice, quindi è lo stesso vetro che cambia misura e contenuto, non
  // più un vetro che nasce per lente dentro quello che esce (i due contorni uno dentro l'altro). Fra due figlie resta
  // identico; al ritorno alla radice torna «Modifica» nello stesso vetro.
  @Test fun `radice con un tasto a sinistra indietro nello stesso vetro poi identico fra due figlie e ritorno nello stesso vetro`() {
    var stack by mutableStateOf(listOf("a"))
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidPageHost(stack, onBack = { stack = stack.dropLast(1) }, modifier = Modifier.fillMaxSize()) { page ->
          FluidScreen("Titolo $page", onBack = if (page == "a") null else ({ stack = stack.dropLast(1) }),
            toolbar = {
              title(FluidToolbarTitleMode.Inline, subtitle = "Sottotitolo $page")
              if (page == "a") text("left", "Modifica", placement = FluidToolbarPlacement.Leading, onClick = {})
              button("right", FluidIcons.MoreHoriz, "Opzioni", onClick = {})
            }) { item { } }
        }
      }
    }
    advance(800)
    val title = compose.onNodeWithText("Titolo a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val subtitle = compose.onNodeWithText("Sottotitolo a", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    println("Titolo agganciato: $title; sottotitolo: $subtitle")
    // L'albero non fuso restituisce i Text, invece del riquadro della barra che contiene entrambi.
    assertTrue("sottotitolo sotto il titolo agganciato: $title / $subtitle",
      subtitle.center.y >= title.center.y + subtitle.height / 2f)
    compose.runOnIdle { stack = listOf("a", "b") }
    Snapshot.sendApplyNotifications()
    advance(80)
    val left = FluidGlassContainerDiagnostics.lastJourney.single { it.toId.toString().contains("left") }
    assertEquals("Matched", left.change)
    assertEquals("l'indietro nel vetro di «Modifica»", left.fromId, left.toId)
    assertTrue("nessun vetro nuovo per l'indietro", FluidGlassContainerDiagnostics.lastJourney
      .none { it.toId.toString().contains(FluidToolbarIds.Back.toString()) })
    advance(1_400)
    compose.onNodeWithText("Sottotitolo b").fetchSemanticsNode()
    val piece = FluidGlassContainerDiagnostics.lastRest.single { it.id == left.toId }
    val journeys = FluidGlassContainerDiagnostics.totalJourneys
    compose.runOnIdle { stack = listOf("a", "b", "c") }
    Snapshot.sendApplyNotifications()
    advance(1_400)
    val after = FluidGlassContainerDiagnostics.lastRest.single { it.id == piece.id }
    assertEquals(piece.id, after.id)
    assertEquals(piece.bounds, after.bounds)
    assertEquals("stessi vetri: nessuna lente fra le figlie", journeys, FluidGlassContainerDiagnostics.totalJourneys)
    compose.onNodeWithText("Sottotitolo c").fetchSemanticsNode()
    compose.runOnIdle { stack = listOf("a") }
    Snapshot.sendApplyNotifications()
    advance(80)
    val home = FluidGlassContainerDiagnostics.lastJourney.single { it.fromId == piece.id }
    assertEquals("Matched", home.change)
    assertEquals("«Modifica» torna nello stesso vetro", piece.id, home.toId)
    advance(1_400)
    compose.onNodeWithText("Sottotitolo a").fetchSemanticsNode()
  }

  @Test fun `None e Principal tolgono anche il sottotitolo dall'host`() {
    var principal by mutableStateOf(false)
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidPageHost(listOf("a"), onBack = {}) {
          FluidScreen("Titolo", toolbar = {
            title(if (principal) FluidToolbarTitleMode.Inline else FluidToolbarTitleMode.None, "Nascosto")
            if (principal) button("principal", FluidIcons.Search, "Principale",
              placement = FluidToolbarPlacement.Principal, onClick = {})
          }) { item { } }
        }
      }
    }
    advance(800)
    assertTrue(compose.onAllNodesWithText("Nascosto").fetchSemanticsNodes().isEmpty())
    compose.runOnIdle { principal = true }
    Snapshot.sendApplyNotifications()
    advance(800)
    assertTrue(compose.onAllNodesWithText("Nascosto").fetchSemanticsNodes().isEmpty())
  }
}
