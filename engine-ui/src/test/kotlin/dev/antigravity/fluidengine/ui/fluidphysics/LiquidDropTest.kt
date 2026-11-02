package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * La goccia del vetro liquido (3.0, la variante C del banco): il contorno a riposo, la fisica dei
 * punti e il patto col riposo. Pura: il tempo lo passa il test, a fotogrammi di 1/60 s.
 */
class LiquidDropTest {

  private val frame = 16_666_667L
  private val density = 2.625f

  private fun slab(l: Float, t: Float, r: Float, b: Float, radius: Float) =
    FluidForm.Slab(Rect(l, t, r, b), FluidCornerRadii.all(radius))

  private fun contour(s: FluidForm.Slab, n: Int = FluidMotion.DropPoints): FloatArray =
    FloatArray(n * 2).also { sampleRestContour(s, n, it) }

  // --- Il contorno a riposo --------------------------------------------------------------------

  @Test
  fun thePerimeterOfARoundedRectIsItsStraightSidesPlusOneCircle() {
    val p = RoundedRectPerimeter()
    p.set(Rect(0f, 0f, 200f, 100f), FluidCornerRadii.all(20f))
    val expected = 2f * (200f - 40f) + 2f * (100f - 40f) + 2f * PI.toFloat() * 20f
    assertEquals(expected, p.total, 0.01f)
  }

  @Test
  fun theRestContourStartsAtTheTopCentreAndGoesClockwiseOnTheShape() {
    val s = slab(0f, 0f, 200f, 100f, 20f)
    val c = contour(s)
    // Il primo punto e' in alto al centro, il secondo a destra di lui (senso orario sullo schermo).
    assertEquals(100f, c[0], 0.001f)
    assertEquals(0f, c[1], 0.001f)
    assertTrue(c[2] > c[0])
    // Ogni punto sta sul rettangolo arrotondato: distanza dal bordo sotto un millesimo di pixel.
    for (i in 0 until FluidMotion.DropPoints) {
      val d = sdRoundedRect(c[i * 2], c[i * 2 + 1], 100f, 50f, 100f, 50f, 20f)
      assertEquals("punto $i fuori dal bordo", 0f, d, 0.01f)
    }
  }

  @Test
  fun theDrawnRingOfADropAtRestLiesOnTheShapeWithItsCornersDense() {
    val s = slab(0f, 0f, 252f * 2.625f, 212f * 2.625f, 26f * 2.625f)
    val drop = LiquidDrop()
    val ring = FloatArray(DropRenderPoints * 2)
    val perimeter = RoundedRectPerimeter()
    val params = FloatArray(DropRenderPoints)
    buildDropRing(s, drop, DropRenderPoints, ring, perimeter, params)
    val r = 26f * 2.625f
    val cx = s.frame.center.x
    val cy = s.frame.center.y
    var cornerPoints = 0
    var worstChord = 0f
    for (i in 0 until DropRenderPoints) {
      val x = ring[i * 2]
      val y = ring[i * 2 + 1]
      assertEquals(0f, sdRoundedRect(x, y, cx, cy, s.frame.width / 2f, s.frame.height / 2f, r), 0.01f)
      if (abs(x - cx) > s.frame.width / 2f - r && abs(y - cy) > s.frame.height / 2f - r) cornerPoints++
      // La corda fra due punti consecutivi non si allontana dall'arco piu' di un quinto di pixel.
      val j = (i + 1) % DropRenderPoints
      val mx = (x + ring[j * 2]) / 2f
      val my = (y + ring[j * 2 + 1]) / 2f
      worstChord = maxOf(worstChord, -sdRoundedRect(mx, my, cx, cy, s.frame.width / 2f, s.frame.height / 2f, r))
    }
    assertTrue("angoli con $cornerPoints punti", cornerPoints >= 40)
    assertTrue("una corda si stacca dall'arco di $worstChord px", worstChord < 0.25f)
  }

  @Test
  fun belowZeroTheStartDeflatesAroundItsCentreAndBeyondOneTheTargetInflates() {
    val from = slab(100f, 300f, 200f, 344f, 22f)
    val to = slab(16f, 72f, 268f, 284f, 26f)
    val under = dropRestSlab(from, to, -0.04f, 0.55f, 0.55f)
    assertEquals(from.frame.center.x, under.frame.center.x, 0.001f)
    assertEquals(from.frame.center.y, under.frame.center.y, 0.001f)
    assertEquals(100f * (1f - 0.04f * 0.55f), under.frame.width, 0.01f)
    val over = dropRestSlab(from, to, 1.04f, 0.55f, 0.55f)
    assertEquals(252f * (1f + 0.04f * 0.55f), over.frame.width, 0.01f)
    assertEquals(to, dropRestSlab(from, to, 1f, 0.55f, 0.55f))
  }

  // --- La fisica dei punti -----------------------------------------------------------------------

  /** Trasla la sagoma a velocita' costante [u] (px/s) lungo x per [frames] fotogrammi. */
  private fun translate(drop: LiquidDrop, base: FluidForm.Slab, u: Float, frames: Int, anchorX: Float, anchorY: Float, tension: Float) {
    for (i in 0..frames) {
      val dx = u * i / 60f
      val s = FluidForm.Slab(base.frame.translate(dx, 0f), base.cornerRadii)
      drop.step(i * frame, contour(s), anchorX + dx, anchorY, FluidMotion.DropAnchorReachDp * density, density, tension)
    }
  }

  @Test
  fun farFromTheAnchorTheTailLagsByTwoZetaUOverRootKMin() {
    // L'ancora lontanissima: tutti i punti hanno la molla morbida e lo smorzamento assoluto. Senza
    // tensione del bordo, a regime ogni punto resta indietro di 2ζu/√kMin.
    val drop = LiquidDrop()
    val base = slab(0f, 0f, 200f, 100f, 20f)
    val u = 600f
    translate(drop, base, u, frames = 120, anchorX = 1e6f, anchorY = 1e6f, tension = 0f)
    val expected = 2f * FluidMotion.DropDamping * u / sqrt(FluidMotion.DropStiffnessFar)
    // Euler semi-implicito a passi di 1/240 s: il punto si sposta DOPO la spinta, quindi a regime e'
    // indietro di un passo di moto in meno.
    val step = u * FluidMotion.DropStepSeconds
    for (i in 0 until drop.points) {
      assertEquals("punto $i", -(expected - step), drop.displacementX[i], expected * 0.01f)
      assertEquals(0f, drop.displacementY[i], 0.05f)
    }
  }

  @Test
  fun nextToTheAnchorThePointsFollowWithoutLag() {
    // L'ancora sul corpo che si muove con lui: i punti vicini hanno lo smorzamento relativo (seguono
    // senza ritardo), i lontani restano indietro. E' la coda.
    val drop = LiquidDrop()
    val base = slab(0f, 0f, 600f, 100f, 20f)
    translate(drop, base, u = 600f, frames = 120, anchorX = 600f, anchorY = 50f, tension = 0f)
    var near = 0
    var far = 0
    for (i in 0 until drop.points) {
      if (drop.anchorWeight(i) > drop.anchorWeight(near)) near = i
      if (drop.anchorWeight(i) < drop.anchorWeight(far)) far = i
    }
    assertTrue(abs(drop.displacementX[near]) < 0.25f * abs(drop.displacementX[far]))
    assertTrue(drop.displacementX[far] < -10f)
  }

  @Test
  fun aDropThatStopsSettlesToExactlyZeroAndStopsAskingForFrames() {
    val drop = LiquidDrop()
    val from = slab(100f, 600f, 300f, 644f, 22f)
    val to = slab(40f, 200f, 292f, 400f, 26f)
    var lastAlive = -1
    var maxDev = 0f
    for (i in 0..240) {
      val t = (i / 18f).coerceAtMost(1f)
      val s = dropRestSlab(from, to, t, 0f, 0f)
      val alive = drop.step(i * frame, contour(s), 200f, 622f, FluidMotion.DropAnchorReachDp * density, density)
      if (alive) lastAlive = i
      for (k in 0 until drop.points) maxDev = maxOf(maxDev, hypot(drop.displacementX[k], drop.displacementY[k]))
    }
    assertTrue("la goccia non si e' deformata: $maxDev", maxDev > 10f)
    assertTrue("la goccia non si e' mai posata", lastAlive in 18 until 240)
    assertTrue(drop.quiet)
    for (k in 0 until drop.points) {
      assertEquals(0f, drop.displacementX[k], 0f)
      assertEquals(0f, drop.displacementY[k], 0f)
    }
    assertEquals(0f, drop.offsetX, 0f)
    assertEquals(0f, drop.lens, 0f)
  }

  @Test
  fun theFirstStepStartsExactlyOnTheRestShape() {
    val drop = LiquidDrop()
    val s = slab(100f, 600f, 300f, 644f, 22f)
    drop.step(0L, contour(s), 200f, 622f, 300f, density)
    val c = contour(s)
    for (i in 0 until drop.points) {
      assertEquals(c[i * 2], drop.pointX(i), 0f)
      assertEquals(c[i * 2 + 1], drop.pointY(i), 0f)
    }
    assertTrue(drop.quiet)
  }

  @Test
  fun aStillShapeSettlesAtOnce() {
    val drop = LiquidDrop()
    val c = contour(slab(0f, 0f, 100f, 100f, 50f))
    assertTrue(drop.step(0L, c, 50f, 50f, 300f, density))
    assertFalse(drop.step(frame, c, 50f, 50f, 300f, density))
    assertTrue(drop.quiet)
  }

  @Test
  fun aStallIsIntegratedAsAtMostAThirtiethOfASecond() {
    val drop = LiquidDrop()
    val a = contour(slab(0f, 0f, 100f, 100f, 50f))
    val b = contour(slab(300f, 0f, 400f, 100f, 50f))
    drop.step(0L, a, 50f, 50f, 300f, density)
    drop.step(frame, a, 50f, 50f, 300f, density)
    // Mezzo secondo di stallo e un salto: la goccia non esplode.
    drop.step(frame + 500_000_000L, b, 350f, 50f, 300f, density)
    for (i in 0 until drop.points) assertTrue(drop.pointX(i).isFinite() && abs(drop.displacementX[i]) < 400f)
  }

  @Test
  fun theDrawnRingNeverStraysFromItsRestShapeFartherThanItsReach() {
    // La scorciatoia dello shader (la fascia della goccia): fuori dalla fascia larga `reach` attorno
    // alla sagoma a riposo il campo della goccia ha lo stesso segno e la stessa "profondita'" del
    // rettangolo arrotondato. Qui il campo vero (il poligono) e quello a riposo, su una griglia, a
    // ogni fotogramma di un viaggio che deforma molto.
    val drop = LiquidDrop()
    val from = slab(100f, 600f, 300f, 644f, 22f)
    val to = slab(40f, 200f, 292f, 400f, 26f)
    val ring = FloatArray(DropRenderPoints * 2)
    val perimeter = RoundedRectPerimeter()
    val params = FloatArray(DropRenderPoints)
    var maxReach = 0f
    for (i in 0..30) {
      val t = (i / 18f).coerceAtMost(1f)
      val s = dropRestSlab(from, to, t, 0f, 0f)
      drop.step(i * frame, contour(s), 200f, 622f, FluidMotion.DropAnchorReachDp * density, density)
      val reach = buildDropRing(s, drop, DropRenderPoints, ring, perimeter, params)
      maxReach = maxOf(maxReach, reach)
      val poly = List(DropRenderPoints) { androidx.compose.ui.geometry.Offset(ring[it * 2], ring[it * 2 + 1]) }
      val f = s.frame
      var y = f.top - 60f
      while (y <= f.bottom + 60f) {
        var x = f.left - 60f
        while (x <= f.right + 60f) {
          val dp = sdPolygon(x, y, poly)
          val dr = sdRoundedRect(x, y, f.center.x, f.center.y, f.width / 2f, f.height / 2f, s.cornerRadii.topLeft)
          assertTrue(
            "fotogramma $i, ($x, $y): poligono $dp, riposo $dr, fascia $reach",
            abs(dp - dr) <= reach + DropReachMarginPx,
          )
          x += 7f
        }
        y += 7f
      }
    }
    assertTrue("la goccia non si e' allontanata: $maxReach", maxReach > 10f)
  }

  @Test
  fun theEdgeTensionPullsNeighboursTogether() {
    // Con la tensione del bordo la goccia e' piu' liscia: lo scarto fra punti vicini e' piu' piccolo.
    fun roughness(tension: Float): Float {
      val drop = LiquidDrop()
      val from = slab(100f, 600f, 300f, 644f, 22f)
      val to = slab(40f, 200f, 292f, 400f, 26f)
      var worst = 0f
      for (i in 0..12) {
        val s = dropRestSlab(from, to, i / 12f, 0f, 0f)
        drop.step(i * frame, contour(s), 200f, 622f, FluidMotion.DropAnchorReachDp * density, density, tension)
        for (k in 0 until drop.points) {
          val j = (k + 1) % drop.points
          worst = maxOf(worst, hypot(drop.displacementX[j] - drop.displacementX[k], drop.displacementY[j] - drop.displacementY[k]))
        }
      }
      return worst
    }
    assertTrue(roughness(2f) < roughness(0f))
  }
}
