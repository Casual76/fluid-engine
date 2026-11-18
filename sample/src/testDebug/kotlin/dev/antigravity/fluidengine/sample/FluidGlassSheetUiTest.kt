package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il foglio di vetro con i detent (F5B.2) in un albero Compose vero: che la dichiarazione arrivi al foglio dell'host
 * (altezze dei detent), che la maniglia e l'app spostino il foglio, che il dito lo porti in cima o lo chiuda, e che il
 * foglio di oggi (un portale `Sheet`) resti com'era: alto quanto il suo contenuto, a filo dei bordi (piano, F5B-d).
 *
 * Finestra da 360 × 640 dp: tutta altezza = 640 − barra di stato − 6 dp, a meta' = 52% di 640 = 332,8 dp; il corpo e'
 * il foglio meno la fascia della maniglia (21 dp) e la barra di navigazione (le barre si leggono dalla finestra del
 * test, di solito zero sotto Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidGlassSheetUiTest {

  @get:Rule
  val compose = createComposeRule()

  private val grabberStrip = 21.dp
  private var statusTop = 0.dp
  private var navBottom = 0.dp
  private fun root() = compose.onRoot().getUnclippedBoundsInRoot()
  private val medium get() = (root().bottom - root().top) * 0.52f - navBottom
  private val large get() = (root().bottom - root().top) - statusTop - 6.dp - navBottom

  @Composable
  private fun Hosted(content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val density = LocalDensity.current
      statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
      navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  private fun bodyHeight(): Dp = compose.onNodeWithTag("corpo").getUnclippedBoundsInRoot().let { it.bottom - it.top }

  private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Dp = 2.dp) {
    assertTrue("$what: atteso $expected, letto $actual", (expected - actual).value.let { kotlin.math.abs(it) } <= tolerance.value)
  }

  @Test
  fun `si apre al detent piu' basso e la maniglia lo porta a tutta altezza`() {
    var chosen: FluidSheetDetent? = null
    compose.setContent {
      Hosted {
        FluidGlassSheet(
          visible = true,
          onDismissRequest = {},
          detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
          onSelectedDetentChange = { chosen = it },
        ) {
          Box(Modifier.fillMaxSize().testTag("corpo"))
        }
      }
    }
    compose.waitForIdle()
    assertNear("a meta'", medium - grabberStrip, bodyHeight())

    // Un tocco sulla maniglia, sopra il corpo.
    val body = compose.onNodeWithTag("corpo").getUnclippedBoundsInRoot()
    compose.onRoot().performTouchInput {
      click(Offset(((body.left + body.right) / 2f).toPx(), (body.top - grabberStrip / 2f).toPx()))
    }
    compose.waitForIdle()
    assertEquals(FluidSheetDetent.Large, chosen)
    assertNear("in cima", large - grabberStrip, bodyHeight())
  }

  @Test
  fun `il contatto ferma la molla prima di qualsiasi trascinamento`() {
    compose.setContent {
      Hosted {
        FluidGlassSheet(
          visible = true,
          onDismissRequest = {},
          detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
        ) {
          Box(Modifier.fillMaxSize().testTag("corpo"))
        }
      }
    }
    compose.waitForIdle()
    val body = compose.onNodeWithTag("corpo").getUnclippedBoundsInRoot()
    compose.mainClock.autoAdvance = false
    try {
      compose.onRoot().performTouchInput {
        click(Offset(((body.left + body.right) / 2f).toPx(), (body.top - grabberStrip / 2f).toPx()))
      }
      compose.mainClock.advanceTimeBy(96)
      val inFlight = bodyHeight()
      assertTrue(inFlight > medium - grabberStrip && inFlight < large - grabberStrip)
      compose.onRoot().performTouchInput { down(center) }
      compose.mainClock.advanceTimeByFrame()
      val held = bodyHeight()
      compose.mainClock.advanceTimeBy(300)
      assertNear("fermo sotto il dito", held, bodyHeight(), 0.5.dp)
      compose.onRoot().performTouchInput { up() }
    } finally {
      compose.mainClock.autoAdvance = true
    }
  }

  @Test
  fun `il detent scelto dall'app porta il foglio li', e torna`() {
    var selected by mutableStateOf<FluidSheetDetent>(FluidSheetDetent.Medium)
    compose.setContent {
      Hosted {
        FluidGlassSheet(
          visible = true,
          onDismissRequest = {},
          detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
          selectedDetent = selected,
        ) {
          Box(Modifier.fillMaxSize().testTag("corpo"))
        }
      }
    }
    compose.waitForIdle()
    assertNear("a meta'", medium - grabberStrip, bodyHeight())
    compose.runOnIdle { selected = FluidSheetDetent.Large }
    compose.waitForIdle()
    assertNear("in cima", large - grabberStrip, bodyHeight())
    compose.runOnIdle { selected = FluidSheetDetent.Medium }
    compose.waitForIdle()
    assertNear("di nuovo a meta'", medium - grabberStrip, bodyHeight())
  }

  @Test
  fun `il dito lo porta in cima, e spinto giu' oltre la soglia lo chiude`() {
    var chosen: FluidSheetDetent? = null
    var dismissed = 0
    compose.setContent {
      Hosted {
        FluidGlassSheet(
          visible = true,
          onDismissRequest = { dismissed++ },
          detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
          onSelectedDetentChange = { chosen = it },
        ) {
          Box(Modifier.fillMaxSize().testTag("corpo"))
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithTag("corpo").performTouchInput { swipeUp(startY = centerY, endY = centerY - 260.dp.toPx(), durationMillis = 300L) }
    compose.waitForIdle()
    assertEquals(FluidSheetDetent.Large, chosen)
    assertNear("in cima", large - grabberStrip, bodyHeight())

    // Giu' fino a meta' e poi oltre la soglia sotto il detent piu' basso: si chiude.
    compose.onNodeWithTag("corpo").performTouchInput {
      swipeDown(startY = top + 40.dp.toPx(), endY = top + 40.dp.toPx() + (large - medium + 160.dp).toPx(), durationMillis = 900L)
    }
    compose.waitForIdle()
    assertEquals(1, dismissed)
  }

  @Test
  fun `il foglio di oggi resta com'era, alto quanto il contenuto e a filo dei bordi`() {
    compose.setContent {
      Hosted {
        FluidGlassModalPortal(
          visible = true,
          onDismissRequest = {},
          presentation = FluidGlassModalPresentation.Sheet,
        ) {
          Box(Modifier.fillMaxWidth().height(200.dp).testTag("corpo"))
        }
      }
    }
    compose.waitForIdle()
    val body = compose.onNodeWithTag("corpo").getUnclippedBoundsInRoot()
    assertNear("altezza", 200.dp, body.bottom - body.top, 0.5.dp)
    // A filo dei lati e del fondo: il rientro e' del foglio con i detent.
    assertNear("sinistra", 0.dp, body.left, 0.5.dp)
    assertNear("destra", root().right, body.right, 0.5.dp)
    assertNear("fondo", root().bottom, body.bottom, 0.5.dp)
  }
}
