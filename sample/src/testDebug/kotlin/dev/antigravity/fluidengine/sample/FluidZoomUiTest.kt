package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lo zoom dalla sorgente (F5B.3) nella scena vera della galleria ([ZoomDemo]): la pagina nasce dentro la sua card e
 * arriva a schermo intero, il foglio nasce dal tasto della barra. Le posizioni sono quelle dei nodi semantici, che
 * contano le trasformazioni dei layer (la pagina in scala dentro la sagoma).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidZoomUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState

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

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val controller = rememberFluidNavController()
        nav = controller
        val state = rememberFluidPageHostState()
        pages = state
        ZoomDemo(navController = controller, state = state)
      }
    }
    compose.waitForIdle()
  }

  private fun boundsOrNull(text: String): DpRect? = runCatching {
    compose.onNodeWithText(text).getUnclippedBoundsInRoot()
  }.getOrNull()

  /**
   * I limiti **trasformati** di un nodo, in px: `getUnclippedBoundsInRoot` porta la posizione attraverso i layer ma lascia
   * la misura non scalata, e una pagina in scala dentro la sua sagoma sembrerebbe intera.
   */
  private fun visibleOrNull(text: String): Rect? = runCatching {
    compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
  }.getOrNull()

  @Test fun `la pagina nasce dentro la sua card e arriva a schermo intero`() {
    show()
    val card = compose.onNodeWithTag(zoomCardTag(0)).fetchSemanticsNode().boundsInRoot
    val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(zoomCardTag(0)).performClick()
    var first: Rect? = null
    repeat(8) {
      if (first == null) {
        compose.mainClock.advanceTimeByFrame()
        first = visibleOrNull(ZoomDetailMark)
      }
    }
    val row = requireNotNull(first) { "la pagina non e' comparsa" }
    // La pagina nasce in scala dentro la card (al piu' un paio di fotogrammi di molla dopo): la riga, che a riposo e'
    // larga quasi quanto lo schermo, e' piccola e ha il centro sopra la card.
    assertTrue("centrata sulla card: $row in $card", row.center.x > card.left && row.center.x < card.right)
    assertTrue("in scala dentro la card: $row in $card", row.width < screen.width * 0.6f)
    assertTrue("sotto la cima della card: $row in $card", row.top > card.top - 40f)
    compose.mainClock.advanceTimeBy(1_200L)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    val rest = requireNotNull(boundsOrNull(ZoomDetailMark))
    assertTrue("a riposo la pagina e' intera: $rest", rest.width > 300.dp)
    assertTrue(!pages.isMoving)
  }

  @Test fun `il foglio nasce dal tasto della barra e arriva al suo detent`() {
    show()
    val button = compose.onNodeWithContentDescription("Sposta").getUnclippedBoundsInRoot()
    val pane = SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Sposta")
    compose.mainClock.autoAdvance = false
    compose.onNodeWithContentDescription("Sposta").performClick()
    var first: DpRect? = null
    repeat(8) {
      if (first == null) {
        compose.mainClock.advanceTimeByFrame()
        first = runCatching { compose.onNode(pane).getUnclippedBoundsInRoot() }.getOrNull()
      }
    }
    val start = requireNotNull(first) { "il foglio non e' comparso" }
    val centerX = (button.left + button.right) / 2f
    val centerY = (button.top + button.bottom) / 2f
    assertTrue("il vetro parte dal tasto: $start, tasto $button", centerX in start.left..start.right && centerY in start.top..start.bottom)
    assertTrue("piccolo come il gruppo del tasto: $start", start.width < 180.dp && start.height < 120.dp)
    compose.mainClock.advanceTimeBy(1_200L)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    val rest = compose.onNode(pane).getUnclippedBoundsInRoot()
    assertTrue("a riposo e' il foglio intero: $rest", rest.width > 380.dp && rest.height > 600.dp)
    compose.onNodeWithText(ZoomSheetFirstRow).assertExists()
  }
}
