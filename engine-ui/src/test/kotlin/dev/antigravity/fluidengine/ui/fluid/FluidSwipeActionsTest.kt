package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.page.FluidPageMotion
import dev.antigravity.fluidengine.ui.theme.FluidLegibleInkMinContrast
import dev.antigravity.fluidengine.ui.theme.fluidLegibleInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

  // --- L'aspetto di iOS 27 (F6.6) --------------------------------------------------------------------------

  private val gap = FluidSwipeCapsuleGapDp * density
  private val inset = FluidSwipeCapsuleInsetDp * density

  @Test
  fun `di serie l'aspetto e' quello di iOS 27, e l'altro resta`() {
    assertEquals("Capsule", FluidSwipeStyle.Capsule.toString())
    assertTrue(FluidSwipeStyle.Capsule.isCapsule)
    assertFalse(FluidSwipeStyle.Fill.isCapsule)
  }

  @Test
  fun `lo spazio della capsula sta fra la riga e il bordo, dal lato giusto`() {
    assertNull("riga ferma: nessuno spazio", fluidSwipeCapsuleSlot(0f, width, gap, inset))
    assertNull("meno dei due margini: ancora niente", fluidSwipeCapsuleSlot(-(gap + inset), width, gap, inset))
    val end = fluidSwipeCapsuleSlot(-200f, width, gap, inset)!!
    assertEquals(width - 200f + gap, end.start, 0.001f)
    assertEquals(width - inset, end.endInclusive, 0.001f)
    val start = fluidSwipeCapsuleSlot(200f, width, gap, inset)!!
    assertEquals(inset, start.start, 0.001f)
    assertEquals(200f - gap, start.endInclusive, 0.001f)
  }

  @Test
  fun `la capsula e' alta quanto la riga meno l'etichetta, fra 24 e 44 dp`() {
    assertEquals(32f * density, fluidSwipeCapsuleHeightPx(56f * density, density), 0.001f) // una riga di ListItem
    assertEquals(44f * density, fluidSwipeCapsuleHeightPx(72f * density, density), 0.001f) // due righe: il tetto
    assertEquals(24f * density, fluidSwipeCapsuleHeightPx(40f * density, density), 0.001f) // il pavimento
    // Apple: 34 pt in una riga di 58,6 pt, 44 pt in una di 66,5.
    assertEquals(34.6f, fluidSwipeCapsuleHeightPx(58.6f, 1f), 0.01f)
    assertEquals(42.5f, fluidSwipeCapsuleHeightPx(66.5f, 1f), 0.01f)
  }

  @Test
  fun `piena, riempie lo spazio, e in uno spazio stretto e' un tondo`() {
    val row = 56f * density
    val h = fluidSwipeCapsuleHeightPx(row, density)
    val wide = fluidSwipeCapsuleGeometry(100f, 300f, row, h, labelBlock = 0f, appear = 1f, armed = 0f)!!
    assertEquals(200f, wide.capsule.width, 0.001f)
    assertEquals(h, wide.capsule.height, 0.001f)
    assertEquals(row / 2f, wide.capsule.center.y, 0.001f)
    val narrow = fluidSwipeCapsuleGeometry(100f, 140f, row, h, labelBlock = 0f, appear = 1f, armed = 0f)!!
    assertEquals(narrow.capsule.width, narrow.capsule.height, 0.001f)
  }

  @Test
  fun `capsula ed etichetta crescono insieme attorno al centro della riga`() {
    val row = 72f * density
    val h = fluidSwipeCapsuleHeightPx(row, density)
    val label = 18f * density
    val full = fluidSwipeCapsuleGeometry(100f, 300f, row, h, labelBlock = label, appear = 1f, armed = 0f)!!
    // Il gruppo capsula + etichetta e' centrato nella riga: la capsula sta sopra il centro di meta' etichetta.
    assertEquals(row / 2f - label / 2f, full.capsule.center.y, 0.001f)
    val half = fluidSwipeCapsuleGeometry(100f, 300f, row, h, labelBlock = label, appear = 0.5f, armed = 0f)!!
    assertEquals(full.capsule.width / 2f, half.capsule.width, 0.001f)
    assertEquals(full.capsule.height / 2f, half.capsule.height, 0.001f)
    assertEquals(full.capsule.center.x, half.capsule.center.x, 0.001f)
    assertEquals(0.5f, half.scale, 0.001f)
    assertNull("non ancora nata", fluidSwipeCapsuleGeometry(100f, 300f, row, h, label, appear = 0f, armed = 0f))
  }

  @Test
  fun `oltre la soglia la capsula si gonfia del 6 per cento`() {
    val row = 56f * density
    val h = fluidSwipeCapsuleHeightPx(row, density)
    val calm = fluidSwipeCapsuleGeometry(100f, 300f, row, h, 0f, appear = 1f, armed = 0f)!!
    val armed = fluidSwipeCapsuleGeometry(100f, 300f, row, h, 0f, appear = 1f, armed = 1f)!!
    assertEquals(calm.capsule.height * (1f + FluidSwipeArmedGrowth), armed.capsule.height, 0.001f)
  }

  @Test
  fun `la capsula che nasce e' trasparente all'inizio e piena da tre quarti`() {
    assertEquals(0f, fluidSwipeAppearAlpha(0f), 0f)
    assertTrue(fluidSwipeAppearAlpha(0.3f) in 0.1f..0.6f)
    assertEquals(1f, fluidSwipeAppearAlpha(0.75f), 0f)
    assertEquals(1f, fluidSwipeAppearAlpha(1.04f), 0f)
    var previous = -1f
    for (i in 0..20) {
      val a = fluidSwipeAppearAlpha(i / 20f)
      assertTrue(a >= previous)
      previous = a
    }
  }

  @Test
  fun `il glifo sta fra 16 e 20 dp`() {
    assertEquals(16f * density, fluidSwipeGlyphPx(24f * density, density), 0.001f)
    assertEquals(19.8f * density, fluidSwipeGlyphPx(44f * density, density), 0.01f)
  }

  /**
   * La frazione di strada di una molla a [ms] millisecondi, partita ferma. La corsa e' di 1000 px: le molle della riga
   * muovono pixel e si fermano a mezzo pixel ([FluidMotion.PixelThreshold]), quindi su una corsa da 0 a 1 finirebbero
   * subito.
   */
  private fun springAt(spec: AnimationSpec<Float>, ms: Long): Float =
    TargetBasedAnimation(spec, Float.VectorConverter, 0f, 1000f).getValueFromNanos(ms * 1_000_000L) / 1000f

  @Test
  fun `il ritorno della riga e' la molla critica misurata su Apple`() {
    val spec = fluidSwipeSettleSpec(FluidSwipeStyle.Capsule)
    // Apple, due prese (f33001-f33013, f33170-f33185): 56% a 133 ms, 86% a 233, 96% a 333, senza superamento.
    assertEquals(0.56f, springAt(spec, 133), 0.05f)
    assertEquals(0.86f, springAt(spec, 233), 0.05f)
    assertEquals(0.96f, springAt(spec, 333), 0.03f)
    for (ms in 0L..1200L step 16L) assertTrue("supera a $ms ms", springAt(spec, ms) <= 1.0005f)
  }

  @Test
  fun `l'aspetto di prima tiene le sue molle`() {
    val old = fluidSwipeSettleSpec(FluidSwipeStyle.Fill)
    val now = fluidSwipeSettleSpec(FluidSwipeStyle.Capsule)
    assertTrue("la molla di prima e' piu' rapida", springAt(old, 133) > springAt(now, 133) + 0.05f)
  }

  @Test
  fun `la capsula nasce in 200 ms con un solo piccolo superamento`() {
    val spec = fluidSwipeAppearSpec()
    // Apple: 0,23 / 0,42 / 0,60 / 0,76 / 0,87 / 0,95 / 1,00 ogni 33 ms dal primo fotogramma visibile, un picco del
    // 4% a ~300 ms, poi ferma. La molla parte ~60 ms prima del primo fotogramma visibile (t0 = -1,8 fotogrammi).
    val t0 = 60L
    assertEquals(0.60f, springAt(spec, t0 + 67), 0.08f)
    assertEquals(0.87f, springAt(spec, t0 + 133), 0.06f)
    assertEquals(1.00f, springAt(spec, t0 + 200), 0.05f)
    var peak = 0f
    var peakAt = 0L
    for (ms in 0L..1500L step 4L) {
      val v = springAt(spec, ms)
      if (v > peak) {
        peak = v
        peakAt = ms
      }
    }
    assertTrue("picco $peak", peak in 1.02f..1.06f)
    assertTrue("picco a $peakAt ms", peakAt in 250L..380L)
    // Un solo superamento: la seconda oscillazione sta sotto lo 0,5%.
    val after = (peakAt + 200L..1500L step 4L).maxOf { springAt(spec, it) }
    assertTrue("seconda oscillazione $after", after < 1.005f)
  }

  @Test
  fun `la stessa azione come voce del menu contestuale`() {
    var done = 0
    val swipe = FluidSwipeAction(label = "Elimina", icon = FluidIcons.Delete, color = Color.Red, removesItem = true) { done++ }
    val item = swipe.asContextAction(destructive = true)
    assertEquals("Elimina", item.label)
    assertEquals(FluidIcons.Delete, item.icon)
    assertTrue(item.destructive)
    item.onClick()
    assertEquals(1, done)
    assertFalse(FluidSwipeAction("Archivia", FluidIcons.Delete, Color.Blue) {}.asContextAction().destructive)
  }
}
