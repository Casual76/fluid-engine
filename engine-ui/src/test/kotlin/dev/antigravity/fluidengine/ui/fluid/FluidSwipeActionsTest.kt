package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import dev.antigravity.fluidengine.ui.theme.FluidLegibleInkMinContrast
import dev.antigravity.fluidengine.ui.theme.fluidLegibleInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Le funzioni pure dello scorrimento di lato: chi prende il dito, dove sta la riga, quando la soglia e' armata e
 * cosa decide il rilascio. Sono le decisioni che archiviano (o no) un elemento: un test per ognuna, senza un
 * albero Compose. Il gesto vero, sotto un dito finto, e' in `FluidLongListsTest` della galleria.
 */
class FluidSwipeActionsTest {

  private val density = 2f
  private val slop = 16f
  private val width = 720f // 360 dp
  private val threshold = fluidSwipeThresholdPx(width, density)
  private val fling = FluidPageMotion.FlingDpPerSecond * density

  // --- La soglia -------------------------------------------------------------------------------------------

  @Test
  fun `la soglia e' il 35 per cento della larghezza, al piu' 180 dp`() {
    assertEquals(0.35f * 720f, threshold, 0.01f)
    assertEquals(180f * density, fluidSwipeThresholdPx(4000f, density), 0.01f)
  }

  @Test
  fun `larghezza zero - soglia zero, e nessun rilascio conferma`() {
    assertEquals(0f, fluidSwipeThresholdPx(0f, density), 0f)
    assertEquals(
      FluidSwipeRelease.Cancel,
      fluidSwipeRelease(offsetPx = 100f, velocityPx = 5000f, armed = true, thresholdPx = 0f, flingPx = fling, hasStart = true, hasEnd = true),
    )
    assertFalse(fluidSwipeArmed(false, 100f, 0f, 24f, sideHasAction = true))
  }

  // --- Chi prende il dito ----------------------------------------------------------------------------------

  @Test
  fun `sotto lo slop non decide`() {
    assertEquals(FluidSwipeClaim.Undecided, fluidSwipeClaim(dx = 10f, dy = 3f, slopPx = slop))
  }

  @Test
  fun `orizzontale netto oltre lo slop e' della riga`() {
    assertEquals(FluidSwipeClaim.Take, fluidSwipeClaim(dx = 20f, dy = 4f, slopPx = slop))
    assertEquals(FluidSwipeClaim.Take, fluidSwipeClaim(dx = -20f, dy = 4f, slopPx = slop))
  }

  @Test
  fun `il verticale ha sempre la precedenza`() {
    assertEquals(FluidSwipeClaim.Leave, fluidSwipeClaim(dx = 2f, dy = 20f, slopPx = slop))
    // Anche quando lo spostamento orizzontale e' gia' oltre lo slop ma il verticale e' piu' grande.
    assertEquals(FluidSwipeClaim.Leave, fluidSwipeClaim(dx = 18f, dy = 19f, slopPx = slop))
  }

  @Test
  fun `un diagonale non e' della riga`() {
    // Orizzontale oltre lo slop ma non una volta e mezza il verticale.
    assertEquals(FluidSwipeClaim.Leave, fluidSwipeClaim(dx = 20f, dy = 15f, slopPx = slop))
  }

  @Test
  fun `il bordo dello schermo non e' della riga, ne' a sinistra ne' a destra`() {
    val edge = FluidPageMotion.EdgeZoneDp * density
    assertTrue(fluidSwipeStartsInEdge(xInRoot = 10f, rootWidth = 720f, leftExclusionPx = edge, rightExclusionPx = edge))
    assertTrue(fluidSwipeStartsInEdge(xInRoot = 715f, rootWidth = 720f, leftExclusionPx = edge, rightExclusionPx = edge))
    assertFalse(fluidSwipeStartsInEdge(xInRoot = 360f, rootWidth = 720f, leftExclusionPx = edge, rightExclusionPx = edge))
    // Con la navigazione a gesti l'inset di sistema e' piu' largo della zona dell'engine, e vince.
    assertTrue(fluidSwipeStartsInEdge(xInRoot = 50f, rootWidth = 720f, leftExclusionPx = 60f, rightExclusionPx = 60f))
  }

  // --- Dove sta la riga ------------------------------------------------------------------------------------

  @Test
  fun `segue il dito 1 a 1 fino alla soglia, poi con resistenza`() {
    val elastic = FluidSwipeElasticDp * density
    assertEquals(100f, fluidSwipeOffsetPx(100f, threshold, elastic, hasStart = true, hasEnd = true, reducedMotion = false), 0.001f)
    assertEquals(-threshold, fluidSwipeOffsetPx(-threshold, threshold, elastic, true, true, false), 0.001f)
    val beyond = fluidSwipeOffsetPx(threshold + 100f, threshold, elastic, true, true, false)
    assertEquals(threshold + 100f * FluidSwipeResistance, beyond, 0.001f)
  }

  @Test
  fun `dal lato senza azione solo un elastico breve, e niente col movimento ridotto`() {
    val elastic = FluidSwipeElasticDp * density
    val stretched = fluidSwipeOffsetPx(500f, threshold, elastic, hasStart = false, hasEnd = true, reducedMotion = false)
    assertTrue("l'elastico non supera il suo tetto: $stretched", stretched in 0f..elastic)
    assertEquals(0f, fluidSwipeOffsetPx(500f, threshold, elastic, hasStart = false, hasEnd = true, reducedMotion = true), 0f)
  }

  @Test
  fun `riprendere una riga che torna - il dito riparte da dove la riga e', senza salto`() {
    val elastic = FluidSwipeElasticDp * density
    for (raw in listOf(-600f, -250f, -10f, 10f, 120f, threshold, threshold + 1f, 700f)) {
      val offset = fluidSwipeOffsetPx(raw, threshold, elastic, true, true, false)
      val back = fluidSwipeRawPx(offset, threshold, elastic, true, true)
      assertEquals("andata e ritorno di $raw", raw, back, 0.01f)
    }
    val elasticOffset = fluidSwipeOffsetPx(15f, threshold, elastic, hasStart = false, hasEnd = true, reducedMotion = false)
    assertEquals(15f, fluidSwipeRawPx(elasticOffset, threshold, elastic, hasStart = false, hasEnd = true), 0.01f)
  }

  // --- La soglia armata ------------------------------------------------------------------------------------

  @Test
  fun `si arma alla soglia e si disarma solo sotto la soglia meno l'isteresi`() {
    val hysteresis = FluidSwipeHysteresisDp * density
    assertFalse(fluidSwipeArmed(false, threshold - 1f, threshold, hysteresis, true))
    assertTrue(fluidSwipeArmed(false, threshold, threshold, hysteresis, true))
    // Armata, un dito che trema di pochi px sotto la soglia resta armato: niente raffica di colpi.
    assertTrue(fluidSwipeArmed(true, threshold - hysteresis / 2f, threshold, hysteresis, true))
    assertFalse(fluidSwipeArmed(true, threshold - hysteresis - 1f, threshold, hysteresis, true))
  }

  @Test
  fun `un dito che oscilla sulla soglia attraversa una volta per verso`() {
    val hysteresis = FluidSwipeHysteresisDp * density
    var armed = false
    var crossings = 0
    val path = listOf(0f, threshold - 2f, threshold + 1f, threshold - 3f, threshold + 2f, threshold - 4f, threshold + 3f)
    for (offset in path) {
      val now = fluidSwipeArmed(armed, offset, threshold, hysteresis, true)
      if (now != armed) crossings++
      armed = now
    }
    assertEquals(1, crossings)
  }

  @Test
  fun `senza azione dal lato non si arma mai`() {
    assertFalse(fluidSwipeArmed(false, 2 * threshold, threshold, 24f, sideHasAction = false))
  }

  // --- Il rilascio -----------------------------------------------------------------------------------------

  private fun release(offset: Float, velocity: Float, armed: Boolean, hasStart: Boolean = true, hasEnd: Boolean = true) =
    fluidSwipeRelease(offset, velocity, armed, threshold, fling, hasStart, hasEnd)

  @Test
  fun `armata al rilascio conferma l'azione del suo lato`() {
    assertEquals(FluidSwipeRelease.Start, release(threshold + 10f, 0f, armed = true))
    assertEquals(FluidSwipeRelease.End, release(-threshold - 10f, 0f, armed = true))
  }

  @Test
  fun `tornata indietro prima del rilascio non conferma`() {
    // Il ripensamento: la soglia superata e poi lasciata non basta, decide dove sta il dito al rilascio.
    assertEquals(FluidSwipeRelease.Cancel, release(threshold * 0.4f, 0f, armed = false))
  }

  @Test
  fun `una spinta contraria annulla anche oltre la soglia`() {
    assertEquals(FluidSwipeRelease.Cancel, release(threshold + 50f, -fling, armed = true))
    assertEquals(FluidSwipeRelease.Cancel, release(-threshold - 50f, fling, armed = true))
  }

  @Test
  fun `una spinta verso l'azione conferma da meta' soglia, non prima`() {
    assertEquals(FluidSwipeRelease.End, release(-threshold * 0.6f, -fling, armed = false))
    assertEquals(FluidSwipeRelease.Cancel, release(-threshold * 0.3f, -fling * 3, armed = false))
    assertEquals(FluidSwipeRelease.Cancel, release(-threshold * 0.6f, -fling / 2, armed = false))
  }

  @Test
  fun `dal lato senza azione il rilascio annulla sempre`() {
    assertEquals(FluidSwipeRelease.Cancel, release(threshold + 10f, fling, armed = true, hasStart = false))
  }

  @Test
  fun `riga ferma - nessun esito`() {
    assertEquals(FluidSwipeRelease.Cancel, release(0f, fling * 4, armed = true))
  }

  // --- L'icona ---------------------------------------------------------------------------------------------

  @Test
  fun `l'icona compare mentre lo spazio si apre`() {
    assertEquals(0f, fluidSwipeIconAlpha(0f, 48f, 40f), 0f)
    assertTrue(fluidSwipeIconAlpha(60f, 48f, 40f) in 0.01f..0.99f)
    assertEquals(1f, fluidSwipeIconAlpha(200f, 48f, 40f), 0f)
  }

  // --- L'inchiostro (anche della pillola di stato a colore libero) -----------------------------------------

  private fun contrast(a: Color, b: Color): Float {
    val high = maxOf(a.luminance(), b.luminance())
    val low = minOf(a.luminance(), b.luminance())
    return (high + 0.05f) / (low + 0.05f)
  }

  @Test
  fun `l'inchiostro fa almeno 4,5 a 1 su qualunque fondo pieno`() {
    var worst = Float.MAX_VALUE
    for (r in 0..255 step 15) for (g in 0..255 step 15) for (b in 0..255 step 15) {
      val background = Color(r, g, b)
      worst = minOf(worst, contrast(fluidLegibleInk(background), background))
    }
    assertTrue("contrasto minimo trovato: $worst", worst >= FluidLegibleInkMinContrast)
  }

  @Test
  fun `il grigio medio, dove i due inchiostri morbidi non bastano, prende il nero o il bianco puro`() {
    val grey = Color(0xFF777777)
    val ink = fluidLegibleInk(grey)
    assertTrue(ink == Color.Black || ink == Color.White)
    assertTrue(contrast(ink, grey) >= FluidLegibleInkMinContrast)
  }

  @Test
  fun `sui colori netti resta l'inchiostro morbido dei colori vividi`() {
    val onYellow = fluidLegibleInk(Color(0xFFFFEB3B))
    val onNavy = fluidLegibleInk(Color(0xFF0D1B4C))
    assertTrue("su un giallo chiaro l'inchiostro e' scuro", onYellow.luminance() < 0.1f)
    assertTrue("su un blu notte l'inchiostro e' chiaro", onNavy.luminance() > 0.9f)
    assertTrue(abs(onYellow.luminance() - Color.Black.luminance()) > 0f) // il quasi-nero, non il nero
  }
}
