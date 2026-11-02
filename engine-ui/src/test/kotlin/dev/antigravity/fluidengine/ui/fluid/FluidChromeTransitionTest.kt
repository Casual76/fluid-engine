package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Il fondale della chrome mentre due pagine si muovono (3.0): l'host delle pagine mette
 * l'impilamento delle due registrazioni, e finche' c'e' scavalca l'elezione di chi e' davanti.
 */
class FluidChromeTransitionTest {

  private fun backdrop() = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)

  private fun controller() = FluidChromeController(bottomBarVelocityThresholdPx = 0f, densityPx = 1f)

  @Test
  fun `durante il movimento la chrome rifrange le due pagine insieme`() {
    val chrome = controller()
    val page = backdrop()
    val both = backdrop()
    val host = Any()
    chrome.registerBackdrop("a", page)
    chrome.setBackdropFront("a", true)
    assertSame(page, chrome.activeBackdrop.value)

    chrome.setTransitionBackdrop(host, both)
    assertSame(both, chrome.activeBackdrop.value)
    // Il davanti che passa di mano a meta' non cambia niente finche' l'impilamento c'e'.
    chrome.registerBackdrop("b", backdrop())
    chrome.setBackdropFront("b", true)
    assertSame(both, chrome.activeBackdrop.value)
  }

  @Test
  fun `a riposo torna l'elezione di sempre`() {
    val chrome = controller()
    val a = backdrop()
    val b = backdrop()
    val host = Any()
    chrome.registerBackdrop("a", a)
    chrome.registerBackdrop("b", b)
    chrome.setBackdropFront("b", true)
    chrome.setTransitionBackdrop(host, backdrop())
    chrome.setTransitionBackdrop(host, null)
    assertSame(b, chrome.activeBackdrop.value)
  }

  @Test
  fun `solo chi l'ha messo lo toglie`() {
    val chrome = controller()
    val both = backdrop()
    chrome.registerBackdrop("a", backdrop())
    chrome.setTransitionBackdrop("host 1", both)
    chrome.setTransitionBackdrop("host 2", null)
    assertSame(both, chrome.activeBackdrop.value)
  }
}
