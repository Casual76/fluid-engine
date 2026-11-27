package dev.antigravity.fluidengine.map

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.26d · Dal tablet del 26/11/2026 (sera): «© OpenStreetMap» sempre in vista sulla cartolina piccola, i crediti della
 * cartolina su un fondo e dietro un tocco, il luogo scelto che la mappa porta in vista (sopra il foglio, fuori dai
 * grappoli), e il vetro dei crediti sotto i segnaposto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "it-rIT-w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FluidMapCreditsRevealTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val px = 2.625f
  private val duomo = FluidGeoPoint(43.77310, 11.25600)
  private val signoria = FluidGeoPoint(43.76956, 11.25581)
  private val pitti = FluidGeoPoint(43.76510, 11.25000)

  private fun show(content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(EngineSettings()) {
        CompositionLocalProvider(LocalFluidMapEngine provides FluidSketchMapEngine()) {
          val host = rememberFluidGlassModalHostState()
          CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
            Box(Modifier.fillMaxSize()) {
              content()
              FluidGlassModalHost(state = host, backdrop = null)
            }
          }
        }
      }
    }
    settle()
  }

  private fun settle(ms: Long = 1_200) {
    var t = 0L
    while (t < ms) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      t += 16
    }
    compose.waitForIdle()
  }

  private fun layoutOf(node: SemanticsNode): TextLayoutResult {
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    return results.single()
  }

  /** Le scritte dei crediti delle cartoline (i nodi di testo con «OpenStreetMap»), nell'ordine. */
  private fun creditTexts(): List<SemanticsNode> =
    compose.onAllNodes(hasText("OpenStreetMap", substring = true), useUnmergedTree = true).fetchSemanticsNodes()

  @Test
  fun `la cartolina piu' piccola tiene OpenStreetMap su una riga, e la larga i crediti interi`() {
    // La piu' stretta garantita (80 dp), quella di una riga della galleria (156 x 88) e una larga (360 x 160).
    show {
      Column {
        FluidMapPostcard(signoria, Modifier.size(FluidMapPostcardDefaults.MinWidth, FluidMapPostcardDefaults.MinWidth), title = "Signoria")
        FluidMapPostcard(signoria, Modifier.size(156.dp, 88.dp), title = "Signoria")
        FluidMapPostcard(signoria, Modifier.size(360.dp, 160.dp), title = "Signoria")
      }
    }
    val postcards = compose.onAllNodesWithTag(FluidMapTags.Postcard, useUnmergedTree = true).fetchSemanticsNodes()
    val texts = creditTexts()
    assertEquals(3, texts.size)
    texts.forEachIndexed { i, node ->
      val text = node.config[SemanticsProperties.Text].joinToString("") { it.text }
      val layout = layoutOf(node)
      assertTrue("$i: «$text»", text.contains("© OpenStreetMap"))
      // Su una riga, intera: prima la scritta andava a capo in tre pezzi e la cartolina piccola ne mostrava due.
      assertEquals("$i: «$text» su una riga", 1, layout.lineCount)
      assertFalse("$i: «$text» intera", layout.hasVisualOverflow)
      val card = postcards[i].boundsInRoot
      val b = node.boundsInRoot
      assertTrue("$i: dentro la cartolina $b in $card", b.left >= card.left && b.right <= card.right && b.bottom <= card.bottom)
    }
    val wide = texts[2].config[SemanticsProperties.Text].joinToString("") { it.text }
    assertTrue(wide, wide.contains("OpenFreeMap") && wide.contains("OpenMapTiles"))
  }

  @Test
  fun `i crediti della cartolina sono un tasto che apre quelli interi`() {
    show { FluidMapPostcard(signoria, Modifier.size(156.dp, 88.dp), title = "Signoria") }
    val credits = compose.onNodeWithTag(FluidMapTags.PostcardCredits).fetchSemanticsNode()
    assertTrue(credits.config.contains(SemanticsActions.OnClick))
    compose.onNodeWithTag(FluidMapTags.PostcardCredits).performClick()
    settle()
    compose.onNodeWithText("Tessere di OpenFreeMap").assertExists()
    compose.onNodeWithText("Stile © OpenMapTiles").assertExists()
  }

  private val markers = listOf(
    FluidMapMarker("duomo", duomo, "Duomo"),
    FluidMapMarker("signoria", signoria, "Piazza della Signoria"),
    FluidMapMarker("pitti", pitti, "Palazzo Pitti"),
  )

  /**
   * Aspetta i segnaposto, al piu' [timeoutMs] di tempo **vero**: i grappoli si calcolano in sottofondo
   * (`Dispatchers.Default`), e col PC carico arrivano dopo l'attesa a tempo finto di [settle] (F8.26d: una corsa con il PC
   * pieno di build non trovava il Duomo prima del tocco).
   */
  private fun awaitMarkers(timeoutMs: Long = 10_000) {
    val markers = SemanticsMatcher("un segnaposto") {
      it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("fluid-map:segnaposto:") == true
    }
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      if (compose.onAllNodes(markers, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
      settle(48)
      Thread.sleep(20)
    }
  }

  private fun markerBottom(id: String): Float =
    compose.onNodeWithTag(FluidMapTags.marker(id), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.bottom

  @Test
  fun `il luogo scelto sotto il foglio sale nella parte che si vede`() {
    // Il Duomo al centro: Palazzo Pitti in basso, sopra il bordo dello schermo ma sotto il foglio a meta'.
    val state = FluidMapState(FluidMapCamera(duomo, 14.5))
    var selected by mutableStateOf<String?>(null)
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers, selectedMarkerId = selected) }
    awaitMarkers()
    val sheet = 463f
    state.bottomObscured = sheet.dp
    settle(200)
    val sheetTop = (891f - sheet) * px
    assertTrue("prima sotto il foglio: ${markerBottom("pitti")} > $sheetTop", markerBottom("pitti") > sheetTop)
    selected = "pitti"
    settle(1_500)
    assertTrue("dopo sopra il foglio: ${markerBottom("pitti")} < $sheetTop", markerBottom("pitti") < sheetTop)
    assertEquals("lo zoom resta", 14.5, state.camera.zoom, 1e-6)
  }

  @Test
  fun `il luogo scelto che si vede gia' non muove la mappa`() {
    val state = FluidMapState(FluidMapCamera(duomo, 14.5))
    var selected by mutableStateOf<String?>(null)
    show { FluidMap(state, Modifier.fillMaxSize(), markers = markers, selectedMarkerId = selected) }
    awaitMarkers()
    val before = state.camera
    selected = "duomo"
    settle(1_000)
    assertEquals(before, state.camera)
  }

  @Test
  fun `il luogo scelto dentro un grappolo si apre fin dove e' da solo`() {
    // Due luoghi a una trentina di metri: a zoom 13 sono un grappolo solo.
    val near = listOf(
      FluidMapMarker("a", signoria, "Piazza della Signoria"),
      FluidMapMarker("b", FluidGeoPoint(signoria.latitude + 0.0003, signoria.longitude), "Loggia dei Lanzi"),
    )
    val state = FluidMapState(FluidMapCamera(signoria, 13.0))
    var selected by mutableStateOf<String?>(null)
    show { FluidMap(state, Modifier.fillMaxSize(), markers = near, selectedMarkerId = selected) }
    awaitMarkers()
    assertEquals(0, compose.onAllNodesWithTag(FluidMapTags.marker("a"), useUnmergedTree = true).fetchSemanticsNodes().size)
    selected = "a"
    settle(2_000)
    assertEquals(1, compose.onAllNodesWithTag(FluidMapTags.marker("a"), useUnmergedTree = true).fetchSemanticsNodes().size)
    assertTrue("${state.camera.zoom}", state.camera.zoom > 13.0)
  }

  @Test
  fun `il vetro dei crediti sta sotto i segnaposto`() {
    // L'ordine dei figli e' l'ordine del disegno: i crediti prima dei segnaposto, i segnaposto sopra.
    show { FluidMap(FluidMapState(FluidMapCamera(duomo, 14.5)), Modifier.fillMaxSize(), markers = markers) }
    awaitMarkers()
    val root = compose.onNodeWithTag(FluidMapTags.Map, useUnmergedTree = true).fetchSemanticsNode()
    val tags = root.children.map { it.config.getOrNull(SemanticsProperties.TestTag).orEmpty() }
    val credits = tags.indexOf(FluidMapTags.Attribution)
    val firstMarker = tags.indexOfFirst { it.startsWith("fluid-map:segnaposto:") }
    assertTrue("$tags", credits >= 0 && firstMarker >= 0)
    assertTrue("crediti sotto i segnaposto: $tags", credits < firstMarker)
  }
}
