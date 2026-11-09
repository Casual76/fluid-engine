package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidTabBadge
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.platform.testTag

/**
 * Le estensioni della barra della fase 1 (pm-f1/ge): il segno sulle zone (E01), la barra col movimento
 * ridotto (E02), il cerchio che e' un pulsante e l'azione in testa al rail (E03).
 *
 * Tre zone come in un'app di posta (Posta, Cerca, Chat) e un cerchio «Scrivi» accanto. Il segno si cerca
 * dal suo stato sul nodo della zona (`stateDescription`), perche' e' li' che TalkBack lo legge: il segno
 * disegnato e' muto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidTabBarExtensionsTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var bar: FluidFloatingTabBarScrollConnection
  private var selected by mutableStateOf("mail")
  private var chatBadge by mutableStateOf<FluidTabBadge?>(FluidTabBadge.Dot)
  private var writes = 0

  private fun items() = listOf(
    FluidTabItem(route = "mail", label = "Posta", icon = Icons.Rounded.Inbox),
    FluidTabItem(route = "search", label = "Cerca", icon = Icons.Rounded.Search),
    FluidTabItem(route = "chat", label = "Chat", icon = Icons.Rounded.Forum, badge = chatBadge),
  )

  private fun stateIs(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)
  private val anyState = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)

  private fun showList(policy: FluidMotionPolicy = FluidMotionPolicy.Default, labels: FluidLabels = FluidLabels()) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides policy, LocalFluidLabels provides labels) {
          val scroll = rememberFluidFloatingTabBarScrollConnection()
          bar = scroll
          Box(Modifier.fillMaxSize().nestedScroll(scroll)) {
            FluidFloatingTabBar(
              items = items(),
              selectedRoute = selected,
              onSelect = { selected = it.route },
              scrollConnection = scroll,
              modifier = Modifier.align(Alignment.BottomCenter),
            )
          }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun showDsl() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val scroll = rememberFluidFloatingTabBarScrollConnection()
        bar = scroll
        Box(Modifier.fillMaxSize().nestedScroll(scroll)) {
          FluidFloatingTabBar(
            selectedTabKey = selected,
            scrollConnection = scroll,
            modifier = Modifier.align(Alignment.BottomCenter),
          ) {
            tab(key = "mail", title = { Text("Posta") }, icon = { Icon(Icons.Rounded.Inbox, null) }, onClick = { selected = "mail" })
            tab(
              key = "chat",
              title = { Text("Chat") },
              icon = { Icon(Icons.Rounded.Forum, null) },
              onClick = { selected = "chat" },
              badge = FluidTabBadge(count = 3),
            )
            standaloneTab(key = "write", icon = { Icon(Icons.Rounded.Edit, "Scrivi") }, onClick = { writes++ })
          }
        }
      }
    }
    compose.waitForIdle()
  }

  // --- E01: il segno sulle zone -------------------------------------------------------------------

  @Test
  fun `il pallino si legge sulla zona a barra aperta, una volta sola`() {
    showList()
    compose.onNodeWithText("Chat").assert(stateIs("Novità"))
    // Le copie (la riga nascosta, l'icona sopra la pillola) sono mute: lo stato e' su un nodo solo.
    compose.onAllNodes(anyState).assertCountEquals(1)
    compose.onNodeWithText("Posta").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
  }

  @Test
  fun `a barra ripiegata il segno non c'e', ne' disegnato ne' detto`() {
    showList()
    compose.runOnIdle { bar.inline() }
    compose.waitForIdle()
    compose.onAllNodesWithText("Chat").assertCountEquals(0)
    compose.onAllNodes(anyState).assertCountEquals(0)

    // Riaperta, torna.
    compose.runOnIdle { bar.expand() }
    compose.waitForIdle()
    compose.onNodeWithText("Chat").assert(stateIs("Novità"))
  }

  @Test
  fun `il numero dice la quantita' vera, anche oltre il 99+, nella lingua dell'app`() {
    chatBadge = FluidTabBadge(count = 150)
    showList()
    compose.onNodeWithText("Chat").assert(stateIs("150 novità"))
    // Il disegno dice «99+», ma muto: nell'albero che legge TalkBack (quello unito) non c'e'.
    compose.onAllNodesWithText("99+").assertCountEquals(0)
  }

  @Test
  fun `in inglese le parole del segno sono inglesi`() {
    chatBadge = FluidTabBadge(count = 3)
    showList(labels = FluidLabels.english())
    compose.onNodeWithText("Chat").assert(stateIs("3 new"))
  }

  @Test
  fun `una frase dell'app vince, e un numero a zero non dice niente`() {
    chatBadge = FluidTabBadge(count = 2, contentDescription = "2 messaggi")
    showList()
    compose.onNodeWithText("Chat").assert(stateIs("2 messaggi"))

    compose.runOnIdle { chatBadge = FluidTabBadge(count = 0) }
    compose.waitForIdle()
    compose.onAllNodes(anyState).assertCountEquals(0)
  }

  @Test
  fun `il segno non e' un bersaglio e non cambia le zone`() {
    showList()
    val withBadge = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
    compose.runOnIdle { chatBadge = null }
    compose.waitForIdle()
    val without = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
    assertEquals("le zone toccabili sono le stesse, il segno non ne aggiunge", without, withBadge)
    compose.onAllNodes(anyState).assertCountEquals(0)
    // Toccare la zona col segno la sceglie: il segno non si mette in mezzo.
    compose.runOnIdle { chatBadge = FluidTabBadge.Dot }
    compose.waitForIdle()
    compose.onNodeWithText("Chat").performClick()
    compose.waitForIdle()
    assertEquals("chat", selected)
  }

  @Test
  fun `nella forma a DSL il segno sta sulla zona`() {
    showDsl()
    compose.onNodeWithText("Chat").assert(stateIs("3 novità"))
    compose.onAllNodes(anyState).assertCountEquals(1)
  }

  // --- E03: il cerchio e' un pulsante -------------------------------------------------------------

  @Test
  fun `il cerchio accanto alla pillola e' un pulsante, non una zona, e non cambia la zona scelta`() {
    showDsl()
    compose.onNodeWithContentDescription("Scrivi")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
      .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    // Le zone restano come erano: nessun ruolo nuovo.
    compose.onNodeWithText("Posta").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))

    compose.onNodeWithContentDescription("Scrivi").performClick()
    compose.waitForIdle()
    assertEquals(1, writes)
    assertEquals("mail", selected)
  }

  @Test
  fun `il cerchio resta un pulsante a barra ripiegata`() {
    showDsl()
    compose.runOnIdle { bar.inline() }
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Scrivi")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
  }

  // --- E02: il movimento ridotto ------------------------------------------------------------------

  @Test
  fun `col movimento ridotto la piega e' gia' chiusa dopo quattro fotogrammi`() {
    showList(policy = FluidMotionPolicy(durationScale = 0f))
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { bar.inline(); Snapshot.sendApplyNotifications() }
    repeat(4) { compose.mainClock.advanceTimeByFrame() }
    compose.onAllNodesWithText("Cerca").assertCountEquals(0)
    compose.mainClock.autoAdvance = true
  }

  /** Il controllo: a scala normale, quattro fotogrammi dopo, la barra aperta sta ancora uscendo. */
  @Test
  fun `a scala normale la piega e' ancora in viaggio dopo quattro fotogrammi`() {
    showList()
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { bar.inline(); Snapshot.sendApplyNotifications() }
    repeat(4) { compose.mainClock.advanceTimeByFrame() }
    assertTrue(
      "la barra aperta sta ancora uscendo",
      compose.onAllNodesWithText("Cerca").fetchSemanticsNodes().isNotEmpty(),
    )
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    compose.onAllNodesWithText("Cerca").assertCountEquals(0)
  }

  @Test
  fun `col movimento ridotto un tocco sceglie la zona subito, senza aspettare il viaggio`() {
    showList(policy = FluidMotionPolicy(durationScale = 0f))
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Cerca").performClick()
    compose.mainClock.advanceTimeBy(32)
    assertEquals("search", selected)
    compose.mainClock.autoAdvance = true
  }

  /** Il controllo: a scala normale la pagina aspetta la pillola (160 ms). */
  @Test
  fun `a scala normale il tocco aspetta il viaggio della pillola`() {
    showList()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText("Cerca").performClick()
    compose.mainClock.advanceTimeBy(32)
    assertEquals("mail", selected)
    compose.mainClock.advanceTimeBy(400)
    assertEquals("search", selected)
    compose.mainClock.autoAdvance = true
  }

  // --- Il rail: segno e azione in testa ----------------------------------------------------------

  @Composable
  private fun Rail(header: Boolean) {
    val backdrop = rememberGlassBackdrop()
    FluidTabRail(
      items = items(),
      selectedRoute = selected,
      onSelect = { selected = it.route },
      backdrop = backdrop,
      modifier = Modifier.testTag("rail").height(FluidTabBarDefaults.Height * 5),
      header = if (header) {
        {
          Box(Modifier.testTag("rail-header")) {
            Text("Scrivi", modifier = Modifier)
          }
        }
      } else {
        null
      },
    )
  }

  @Test
  fun `il rail porta l'azione in testa e il segno sulla zona`() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { Rail(header = true) }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("rail-header").assertExists()
    compose.onNodeWithTag("top_level_chat")
      .assert(stateIs("Novità"))
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    compose.onAllNodes(anyState).assertCountEquals(1)
    compose.onNodeWithTag("top_level_search").performClick()
    compose.waitForIdle()
    assertEquals("search", selected)
  }

  @Test
  fun `senza intestazione il rail e' quello di prima`() {
    chatBadge = null
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { Rail(header = false) }
    }
    compose.waitForIdle()
    compose.onAllNodesWithText("Scrivi").assertCountEquals(0)
    compose.onAllNodes(anyState).assertCountEquals(0)
    compose.onNodeWithTag("top_level_mail").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
  }
}
