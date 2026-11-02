package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.ui.fluid.FluidAmbient
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidContainerScaffold
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarInlineBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidHeroMotif
import dev.antigravity.fluidengine.ui.fluid.FluidHeroTone
import dev.antigravity.fluidengine.ui.fluid.FluidSectionAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidSectionIndex
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSheet
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.FluidTabRail
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidSyncAction
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.tutorial.FluidCoachMark
import dev.antigravity.fluidengine.ui.tutorial.FluidCoachMarkPlacement
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorial
import dev.antigravity.fluidengine.ui.tutorial.FluidTutorialLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Cosa ha suonato il tema durante un gesto. */
private class GlassHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }
}

/**
 * Tutto in vetro vero (3.0, fase 9), dentro un albero Compose vero.
 *
 * Il pixel non si prova qui (un test Robolectric non disegna un fotogramma di vetro): si prova che
 * cio' che e' diventato vetro **funzioni ancora** — che un chip scatti, che il segmentato scelga e
 * suoni un tick per segmento, che la scheda scelta del rail risponda ancora al secondo tocco malgrado
 * la copia che la goccia ridisegna sopra, che la banda e il cerchio della barra dicano un `Tap`, che la
 * chiusura di una pagina intera e il vecchio foglio chiudano — e che ogni cosa resti corretta senza
 * fondale, che e' la meta' della richiesta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class AllGlassTest {

  @get:Rule
  val compose = createComposeRule()

  private val haptics = GlassHaptics()

  @Before
  fun reset() {
    haptics.played.clear()
  }

  @Composable
  private fun Themed(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      CompositionLocalProvider(LocalFluidHaptics provides haptics, content = content)
    }
  }

  /** [content] su un canvas di vetro: dove c'e' qualcosa da attraversare, i controlli sono di vetro. */
  @Composable
  private fun OnCanvas(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides rememberGlassBackdrop(), content = content)
  }

  // --- Chip ----------------------------------------------------------------------------------------

  @Test
  fun `a chip on a canvas is a glass button that clicks, is selected and plays the click of a button`() {
    var clicks = 0
    compose.setContent {
      Themed {
        OnCanvas {
          FluidChip(label = "Preferiti", selected = true, onClick = { clicks++ }, modifier = Modifier.testTag("chip"))
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("chip").assertIsSelected()
    compose.onNodeWithTag("chip").performClick()
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertTrue(FluidHapticEvent.PressClick in haptics.played)
  }

  @Test
  fun `a chip on a flat page is the opaque pill it always was, and still clicks`() {
    var clicks = 0
    compose.setContent {
      Themed {
        FluidChip(label = "Recenti", selected = false, onClick = { clicks++ }, modifier = Modifier.testTag("chip"))
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("chip").performClick()
    compose.waitForIdle()

    assertEquals(1, clicks)
    assertTrue(FluidHapticEvent.PressClick in haptics.played)
  }

  @Test
  fun `a disabled glass chip says it is disabled and does not click`() {
    var clicks = 0
    compose.setContent {
      Themed {
        OnCanvas {
          FluidChip(label = "Spento", selected = false, onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("chip"))
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("chip").assertIsNotEnabled()
    compose.onNodeWithTag("chip").performClick()
    compose.waitForIdle()

    assertEquals(0, clicks)
  }

  // --- Segmentato -----------------------------------------------------------------------------------

  private val segments = listOf("Giorno", "Settimana", "Mese")

  @Composable
  private fun Segmented(selected: String, onSelect: (String) -> Unit) {
    FluidSegmentedControl(
      options = segments,
      selected = selected,
      onSelect = onSelect,
      modifier = Modifier.testTag("segmented"),
      label = { it },
    )
  }

  private fun tapSelectsOnce(glass: Boolean) {
    var picked: String? = null
    var calls = 0
    compose.setContent {
      Themed {
        val body: @Composable () -> Unit = {
          var selected by remember { mutableStateOf("Giorno") }
          Segmented(selected) {
            selected = it
            picked = it
            calls++
          }
        }
        if (glass) OnCanvas(body) else body()
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Mese").performClick()
    compose.waitForIdle()

    assertEquals("Mese", picked)
    assertEquals(1, calls)
    compose.onNodeWithText("Mese").assertIsSelected()
  }

  @Test
  fun `a tap on a segment selects it once, on a glass track`() = tapSelectsOnce(glass = true)

  @Test
  fun `a tap on a segment selects it once, on a flat page`() = tapSelectsOnce(glass = false)

  @Test
  fun `dragging the drop across the track plays a tick for each segment it crosses and settles on the nearest`() {
    var selected by mutableStateOf("Giorno")
    compose.setContent {
      Themed {
        OnCanvas { Segmented(selected) { selected = it } }
      }
    }
    compose.waitForIdle()

    // 1,8 segmenti in tre passi: l'arrotondamento passa da 0 a 1 a 2, due segmenti attraversati.
    compose.onNodeWithTag("segmented").performTouchInput {
      val segment = width / 3f
      down(Offset(segment / 2f, height / 2f))
      moveBy(Offset(segment * 0.6f, 0f))
      moveBy(Offset(segment * 0.6f, 0f))
      moveBy(Offset(segment * 0.6f, 0f))
      up()
    }
    compose.waitForIdle()

    assertEquals("Mese", selected)
    assertEquals(
      "un tick per segmento attraversato: ${haptics.played}",
      2,
      haptics.played.count { it == FluidHapticEvent.Tick },
    )
  }

  @Test
  fun `a finger that lands on another segment and slides takes the drop and chooses where it lets go`() {
    // "Come iOS" (3.0): il dito scende su Settimana, scivola verso Mese e si solleva li'.
    var selected by mutableStateOf("Giorno")
    var calls = 0
    compose.setContent {
      Themed {
        OnCanvas {
          Segmented(selected) {
            selected = it
            calls++
          }
        }
      }
    }
    compose.waitForIdle()
    haptics.played.clear()

    compose.onNodeWithTag("segmented").performTouchInput {
      val segment = width / 3f
      down(Offset(segment * 1.5f, height / 2f))
      moveBy(Offset(segment * 0.2f, 0f))
      moveBy(Offset(segment * 0.6f, 0f))
      up()
    }
    compose.waitForIdle()

    assertEquals("Mese", selected)
    assertEquals("una scelta sola", 1, calls)
    // La goccia arriva su Settimana (un tick) e passa a Mese (un altro); nessun Tap: il segmento
    // sotto non ha cliccato.
    assertEquals("${haptics.played}", 2, haptics.played.count { it == FluidHapticEvent.Tick })
    assertEquals("${haptics.played}", 0, haptics.played.count { it == FluidHapticEvent.Tap })
    assertEquals("${haptics.played}", 0, haptics.played.count { it == FluidHapticEvent.PressClick })
  }

  @Test
  fun `a finger that lands on another segment and goes vertical chooses nothing`() {
    var selected by mutableStateOf("Giorno")
    compose.setContent {
      Themed {
        OnCanvas { Segmented(selected) { selected = it } }
      }
    }
    compose.waitForIdle()
    haptics.played.clear()

    compose.onNodeWithTag("segmented").performTouchInput {
      val segment = width / 3f
      down(Offset(segment * 2.5f, height / 2f))
      moveBy(Offset(0f, height.toFloat()))
      moveBy(Offset(0f, height.toFloat()))
      up()
    }
    compose.waitForIdle()

    assertEquals("Giorno", selected)
    assertEquals("${haptics.played}", 0, haptics.played.count { it == FluidHapticEvent.Tick })
  }

  @Test
  fun `the labels are said once, the copy under the lens and the one on the drop are decorative`() {
    compose.setContent {
      Themed {
        OnCanvas { Segmented("Settimana") { } }
      }
    }
    compose.waitForIdle()

    for (label in segments) compose.onAllNodesWithText(label).assertCountEquals(1)
    compose.onNodeWithText("Settimana").assertIsSelected()
    compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)).assertCountEquals(1)
  }

  // --- Rail -----------------------------------------------------------------------------------------

  private val railItems = listOf(
    FluidTabItem("home", "Home", Icons.Rounded.Home),
    FluidTabItem("star", "Preferiti", Icons.Rounded.Star),
    FluidTabItem("search", "Cerca", Icons.Rounded.Search),
  )

  @Test
  fun `the rail answers a tap on another tab, and a second tap on the chosen one is still a reselect`() {
    var selected by mutableStateOf("home")
    val picked = mutableListOf<String>()
    val reselected = mutableListOf<String>()
    compose.setContent {
      Themed {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidTabRail(
            items = railItems,
            selectedRoute = selected,
            onSelect = {
              picked += it.route
              selected = it.route
            },
            backdrop = backdrop,
            modifier = Modifier.height(192.dp),
            onReselect = { reselected += it.route },
          )
        }
      }
    }
    compose.waitForIdle()

    // La goccia a riposo ridisegna la scheda scelta sopra di se': non deve prendersi il tocco.
    compose.onNodeWithTag("top_level_home").performClick()
    compose.waitForIdle()
    assertEquals(listOf("home"), reselected)
    assertTrue(picked.isEmpty())

    compose.onNodeWithTag("top_level_star").performClick()
    compose.waitForIdle()
    assertEquals(listOf("star"), picked)
    compose.onNodeWithTag("top_level_star").assertIsSelected()

    // Ogni scheda una volta sola per chi ascolta lo schermo: la copia sulla goccia e quella sotto la
    // lente sono decorazione.
    for (item in railItems) compose.onAllNodesWithText(item.label).assertCountEquals(1)
  }

  // --- Indice di sezione --------------------------------------------------------------------------------

  @Test
  fun `holding the index opens the rail, plays the start of a gesture and shows the bubble`() {
    val sections = listOf("Gen", "Feb", "Mar", "Apr").mapIndexed { index, label ->
      FluidSectionAnchor(key = label, label = label, itemIndex = index)
    }
    compose.setContent {
      Themed {
        val backdrop = rememberGlassBackdrop()
        Box(Modifier.fillMaxSize()) {
          FluidSectionIndex(
            sections = sections,
            activeSectionKey = "Feb",
            onSelectSection = { _, _ -> },
            backdrop = backdrop,
          )
        }
      }
    }
    compose.waitForIdle()
    compose.onAllNodesWithText("Feb").assertCountEquals(0)

    val strip = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
    compose.onNode(strip).performTouchInput { down(center) }
    compose.mainClock.advanceTimeBy(900)
    compose.waitForIdle()

    assertTrue(FluidHapticEvent.GestureStart in haptics.played)
    // Che ci sia: assertIsDisplayed guarda anche i limiti, e la bolla sta fuori dalla striscia da 48 dp.
    compose.onNodeWithText("Feb").assertExists()
    compose.onNode(strip).performTouchInput { up() }
    compose.waitForIdle()
  }

  // --- Barra flottante ---------------------------------------------------------------------------------------

  @Composable
  private fun TabBar(
    scroll: dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarScrollConnection,
    selected: String,
    onTab: (String) -> Unit,
    onSearch: () -> Unit,
  ) {
    val backdrop = rememberGlassBackdrop()
    Box(Modifier.fillMaxSize()) {
      FluidFloatingTabBar(
        selectedTabKey = selected,
        scrollConnection = scroll,
        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        backdrop = backdrop,
        inlineAccessory = { band, _ -> Text("In riproduzione", band.testTag("band")) },
        expandedAccessory = { band, _ -> Text("In riproduzione", band.testTag("band")) },
      ) {
        tab(
          key = "home",
          title = { Text("Home") },
          icon = { Icon(Icons.Rounded.Home, contentDescription = null) },
          onClick = { onTab("home") },
        )
        tab(
          key = "star",
          title = { Text("Preferiti") },
          icon = { Icon(Icons.Rounded.Star, contentDescription = null) },
          onClick = { onTab("star") },
        )
        standaloneTab(
          key = "search",
          icon = { Icon(Icons.Rounded.Search, contentDescription = "Cerca") },
          onClick = onSearch,
        )
      }
    }
  }

  @Test
  fun `the bar with a band shows it, and a tap on a tab plays a Tap and navigates`() {
    var selected by mutableStateOf("home")
    compose.setContent {
      Themed {
        val scroll = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never)
        TabBar(scroll, selected, onTab = { selected = it }, onSearch = {})
      }
    }
    compose.waitForIdle()

    compose.onNodeWithTag("band").assertIsDisplayed()

    compose.onNodeWithText("Preferiti").performClick()
    compose.mainClock.advanceTimeBy(600)
    compose.waitForIdle()

    assertEquals("star", selected)
    assertTrue("un tocco su una scheda dice Tap: ${haptics.played}", FluidHapticEvent.Tap in haptics.played)
  }

  @Test
  fun `the circle answers with a Tap and fires once`() {
    var searched = 0
    compose.setContent {
      Themed {
        val scroll = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never)
        TabBar(scroll, "home", onTab = {}, onSearch = { searched++ })
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Cerca").performClick()
    compose.waitForIdle()

    assertEquals(1, searched)
    assertTrue(FluidHapticEvent.Tap in haptics.played)
  }

  @Test
  fun `the folded tab answers with a Tap and unfolds the bar`() {
    lateinit var folded: dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarScrollConnection
    compose.setContent {
      Themed {
        val scroll = rememberFluidFloatingTabBarScrollConnection(
          initialIsInline = true,
          inlineBehavior = FluidFloatingTabBarInlineBehavior.Never,
        )
        folded = scroll
        TabBar(scroll, "home", onTab = {}, onSearch = {})
      }
    }
    compose.waitForIdle()
    assertTrue(folded.isInline)

    // Ripiegata la scheda e' solo un'icona, senza titolo: e' il primo bersaglio del tocco in albero
    // (poi viene il cerchio; la banda non si tocca).
    compose.onAllNodes(hasClickAction())[0].performClick()
    compose.waitForIdle()

    assertFalse("un tocco sulla scheda ripiegata riapre la barra", folded.isInline)
    assertTrue(FluidHapticEvent.Tap in haptics.played)
  }

  // --- Suggerimento -----------------------------------------------------------------------------------------------

  private val tutorial = FluidTutorial(id = "demo", priority = 0, title = "Tieni premuto", text = "Apre il menu.")
  private val labels = FluidTutorialLabels(dismiss = "Ok", next = "Avanti", optOut = "Non mostrare piu'")

  @Test
  fun `the coach mark is dismissed by its glass button, with the Close of the capsule it replaces`() {
    var dismissed = 0
    compose.setContent {
      Themed {
        OnCanvas {
          FluidCoachMark(
            tutorial = tutorial,
            labels = labels,
            placement = FluidCoachMarkPlacement(x = 0f, y = 0f, below = true, pointerX = 40f),
            backdrop = rememberGlassBackdrop(),
            onMeasured = {},
            onDismiss = { dismissed++ },
            onOptOut = {},
          )
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Tieni premuto").assertIsDisplayed()
    compose.onNodeWithText("Ok").performClick()
    compose.waitForIdle()

    assertEquals(1, dismissed)
    assertTrue(FluidHapticEvent.Close in haptics.played)
    assertFalse("il colpo e' quello di chiusura, non quello di un tasto qualsiasi", FluidHapticEvent.PressClick in haptics.played)
  }

  @Test
  fun `without glass the coach mark keeps its solid capsule, and it still dismisses`() {
    var dismissed = 0
    compose.setContent {
      Themed {
        FluidCoachMark(
          tutorial = tutorial,
          labels = labels,
          placement = FluidCoachMarkPlacement(x = 0f, y = 0f, below = false, pointerX = 40f),
          backdrop = null,
          onMeasured = {},
          onDismiss = { dismissed++ },
          onOptOut = {},
        )
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Ok").performClick()
    compose.waitForIdle()

    assertEquals(1, dismissed)
  }

  // --- Contenitore ------------------------------------------------------------------------------------------------------

  private fun containerShowsContent(ambient: FluidAmbient?) {
    compose.setContent {
      Themed {
        FluidContainerScaffold(
          title = "Dettaglio",
          onBack = null,
          ambient = ambient,
          hero = { Text("Corpo principale") },
          secondary = { Text("Corpo secondario") },
        )
      }
    }
    compose.waitForIdle()

    compose.onNodeWithText("Corpo principale").assertIsDisplayed()
    compose.onNodeWithText("Corpo secondario").assertIsDisplayed()
  }

  @Test
  fun `the container shows its content on an ambient canvas`() =
    containerShowsContent(FluidAmbient(FluidHeroTone.Primary, FluidHeroMotif.Glow))

  @Test
  fun `the container shows its content on a plain page`() = containerShowsContent(null)

  // --- Pagina intera e foglio nell'host ---------------------------------------------------------------------------------

  @Test
  fun `the close of a full screen page is a glass button and it closes`() {
    var open by mutableStateOf(true)
    var dismissed = 0
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidGlassModalPortal(
              visible = open,
              onDismissRequest = {
                dismissed++
                open = false
              },
              presentation = FluidGlassModalPresentation.FullScreen,
              paneTitle = "Pagina",
            ) {
              Text("Corpo della pagina")
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("Corpo della pagina").assertIsDisplayed()

    compose.onNodeWithContentDescription("Chiudi").performClick()
    compose.waitForIdle()

    assertEquals(1, dismissed)
    assertTrue(FluidHapticEvent.PressClick in haptics.played)
  }

  @Suppress("DEPRECATION")
  @OptIn(ExperimentalMaterial3Api::class)
  @Test
  fun `with a host the old sheet is the glass sheet, and it tells its caller it is closed once, after its exit`() {
    var open by mutableStateOf(true)
    var dismissed = 0
    lateinit var hostState: dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHostState
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        hostState = host
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            if (open) {
              FluidSheet(
                onDismissRequest = {
                  dismissed++
                  open = false
                },
                title = "Foglio",
              ) {
                Text("Dentro il foglio")
              }
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()

    // Nell'host, dentro la composizione: il contenuto c'e' senza una finestra di piattaforma.
    compose.onNodeWithText("Dentro il foglio").assertIsDisplayed()
    assertTrue(hostState.isPresenting)

    // Due richieste di chiusura nello stesso istante (scrim e indietro): una sola risposta, e non
    // subito — il foglio deve prima uscire.
    compose.runOnIdle {
      hostState.dismissTop()
      hostState.dismissTop()
    }
    assertEquals("non prima dell'uscita", 0, dismissed)

    compose.mainClock.advanceTimeBy(500)
    compose.waitForIdle()

    assertEquals(1, dismissed)
    compose.onAllNodesWithText("Dentro il foglio").assertCountEquals(0)
  }

  // --- Sincronizzazione nella barra --------------------------------------------------------------------------------------------

  @Test
  fun `the sync indicator in a bar is pressable like a glass button and opens its details`() {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidGlassModalHostState provides host,
          LocalGlassBackdrop provides rememberGlassBackdrop(),
        ) {
          Box(Modifier.fillMaxSize()) {
            FluidSyncAction(
              status = SyncStatus(state = SyncState.IDLE, lastSuccessfulSyncEpochMillis = 1_700_000_000_000L),
              modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Sincronizzazione completata").performClick()
    compose.waitForIdle()

    assertTrue(FluidHapticEvent.PressClick in haptics.played)
    compose.onNodeWithText("Sincronizzazione").assertIsDisplayed()
  }

  @Test
  fun `outside a bar the sync indicator is the coloured circle, and it still opens its details`() {
    compose.setContent {
      Themed {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            FluidSyncAction(
              status = SyncStatus(state = SyncState.ERROR, message = "Sessione scaduta."),
              modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
            )
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()

    compose.onNodeWithContentDescription("Sincronizzazione non riuscita").performClick()
    compose.waitForIdle()

    compose.onNodeWithText("Sessione scaduta.").assertIsDisplayed()
  }
}
