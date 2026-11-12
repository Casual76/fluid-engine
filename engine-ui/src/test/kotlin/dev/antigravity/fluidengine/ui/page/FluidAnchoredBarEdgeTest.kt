package dev.antigravity.fluidengine.ui.page

import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo sfondo della barra ancorata fra due pagine (F2.5): pesa il bordo dello scorrimento come pesava la fascia, e
 * fra una pagina in cima e una scorsa non salta.
 */
class FluidAnchoredBarEdgeTest {

  /** Lo stesso conto della barra: la somma pesata delle intensità delle pagine, fra zero e uno. */
  private fun barIntensity(frontFraction: Float, upper: Float, lower: Float): Float =
    (anchoredBarPageWeight(PageRole.Upper, frontFraction) * upper +
      anchoredBarPageWeight(PageRole.Lower, frontFraction) * lower).coerceIn(0f, 1f)

  @Test
  fun weights_alwaysAddUpToOne() {
    for (step in 0..20) {
      val e = step / 20f
      assertEquals(1f, anchoredBarPageWeight(PageRole.Upper, e) + anchoredBarPageWeight(PageRole.Lower, e), 1e-6f)
    }
    assertEquals(0f, anchoredBarPageWeight(PageRole.Gone, 0.4f), 0f)
  }

  @Test
  fun atRest_theBarIsThePageOnTop() {
    // Una pagina scorsa sopra una in cima: a riposo la striscia è piena, come quella della pagina davanti.
    assertEquals(1f, barIntensity(frontFraction = 0f, upper = 1f, lower = 0f), 0f)
    // Tornata davanti quella sotto (in cima): niente striscia.
    assertEquals(0f, barIntensity(frontFraction = 1f, upper = 1f, lower = 0f), 0f)
  }

  @Test
  fun betweenAScrolledPageAndOneAtTheTop_theStripComesAndGoesWithoutJumps() {
    var previous = barIntensity(0f, upper = 0f, lower = 1f)
    for (step in 1..100) {
      val now = barIntensity(step / 100f, upper = 0f, lower = 1f)
      assertTrue("passo a $step", now - previous <= 0.0101f && now >= previous)
      previous = now
    }
    assertEquals(1f, previous, 1e-5f)
  }

  @Test
  fun whilePagesMove_eachCarriesItsOwnStrip() {
    // Un ritorno: la pagina sopra (scorsa, intensità 1) è a metà corsa e copre da 800 a 1600 px; quella sotto è in cima.
    val split = anchoredBarSplitOf(from = 800f, to = 1600f, upper = 1f, lower = 0f)
    assertEquals(800f, split.from, 0f)
    assertEquals(1600f, split.to, 0f)
    // Sulla pagina che esce la striscia è intera, non pesata con la corsa (che a metà la dava a 0,5).
    assertEquals(1f, split.inside, 0f)
    assertEquals(0f, split.outside, 0f)
    assertEquals(0.5f, barIntensity(frontFraction = 0.5f, upper = 1f, lower = 0f), 1e-6f)
    // Le intensità restano fra zero e uno.
    val clamped = anchoredBarSplitOf(from = 0f, to = 10f, upper = 2f, lower = -1f)
    assertEquals(1f, clamped.inside, 0f)
    assertEquals(0f, clamped.outside, 0f)
  }
}
