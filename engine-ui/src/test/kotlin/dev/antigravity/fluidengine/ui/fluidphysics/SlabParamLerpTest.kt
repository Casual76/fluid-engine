package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlabParamLerpTest {

  private val circle = FluidForm.circle(center = androidx.compose.ui.geometry.Offset(100f, 100f), radius = 50f)
  private val square = FluidForm.Slab(
    frame = Rect(300f, 300f, 500f, 500f),
    cornerRadii = FluidCornerRadii.all(12f),
  )

  @Test
  fun theJourneyIsExactAtBothEnds() {
    val atStart = lerpSlab(circle, square, 0f)
    assertEquals(circle.frame.left, atStart[0], 0f)
    assertEquals(circle.frame.top, atStart[1], 0f)
    assertEquals(circle.frame.width, atStart[2], 0f)
    assertEquals(50f, atStart[4], 0f)

    val atEnd = lerpSlab(circle, square, 1f)
    assertEquals(square.frame.left, atEnd[0], 0f)
    assertEquals(square.frame.width, atEnd[2], 0f)
    assertEquals(12f, atEnd[4], 0f)
  }

  @Test
  fun radiiNeverOutgrowHalfTheShortSideMidJourney() {
    // Un cerchio piccolo verso un rettangolo basso e largo: a metà strada il raggio interpolato
    // supererebbe la mezza altezza, e il campo di distanza disegnerebbe un vetro impossibile.
    val small = FluidForm.circle(androidx.compose.ui.geometry.Offset(0f, 0f), radius = 80f)
    val flat = FluidForm.Slab(Rect(0f, 0f, 400f, 60f), FluidCornerRadii.all(0f))
    for (step in 0..10) {
      val t = step / 10f
      val v = lerpSlab(small, flat, t)
      val half = minOf(v[2], v[3]) / 2f
      for (r in 4..7) {
        assertTrue("raggio oltre il mezzo lato a t=$t", v[r] <= half + 0.001f)
        assertTrue("raggio negativo a t=$t", v[r] >= 0f)
      }
    }
  }

  @Test
  fun overshootStretchesTheFrameButNeverBreaksThePhysics() {
    // Le molle della casa oltrepassano l'arrivo: la geometria deve estrapolare senza degenerare.
    val v = lerpSlab(circle, square, 1.08f)
    assertTrue(v[2] >= MinSlabSidePx)
    assertTrue(v[3] >= MinSlabSidePx)
    val half = minOf(v[2], v[3]) / 2f
    for (r in 4..7) assertTrue(v[r] in 0f..half + 0.001f)
  }

  @Test
  fun aCapsuleEndpointArrivesAsATrueCapsule() {
    val capsule = FluidFormPresets.capsule(Rect(0f, 0f, 300f, 100f))
    val v = lerpSlab(circle, capsule, 1f)
    // Raggio == mezza altezza: la capsula è una capsula, non una squircle accidentale.
    assertEquals(50f, v[4], 0.001f)
    assertEquals(50f, v[7], 0.001f)
  }

  @Test
  fun aShrinkingJourneyNeverCollapsesToNothing() {
    val point = FluidForm.Slab(Rect(100f, 100f, 100.5f, 100.5f), FluidCornerRadii.Zero)
    val v = lerpSlab(circle, point, 1f)
    assertTrue(v[2] >= MinSlabSidePx)
    assertTrue(v[3] >= MinSlabSidePx)
  }

  // --- Transit.formAt (3.0): la forma di un transito a progresso t ---------------------------

  private fun assertFrame(expected: Rect, actual: Rect, delta: Float = 0.001f) {
    assertEquals("left", expected.left, actual.left, delta)
    assertEquals("top", expected.top, actual.top, delta)
    assertEquals("right", expected.right, actual.right, delta)
    assertEquals("bottom", expected.bottom, actual.bottom, delta)
  }

  @Test
  fun formAtTheEndsOfASlabTransitIsTheEndForms() {
    val transit = SlabTransit(listOf(circle to square), blendRadius = 0f)

    val atStart = transit.formAt(0f) as FluidForm.Slab
    assertFrame(circle.frame, atStart.frame)
    assertEquals(50f, atStart.cornerRadii.topLeft, 0.001f)

    val atEnd = transit.formAt(1f) as FluidForm.Slab
    assertFrame(square.frame, atEnd.frame)
    assertEquals(12f, atEnd.cornerRadii.bottomRight, 0.001f)
  }

  @Test
  fun formAtHalfwayIsTheMidpointOfTheFrames() {
    val transit = SlabTransit(listOf(circle to square), blendRadius = 0f)

    val mid = transit.formAt(0.5f) as FluidForm.Slab

    // circle: 50..150, square: 300..500 -> 175..325 per asse; il raggio fra 50 e 12 e' 31, sotto il
    // mezzo lato (75).
    assertFrame(Rect(175f, 175f, 325f, 325f), mid.frame)
    assertEquals(31f, mid.cornerRadii.topLeft, 0.001f)
  }

  @Test
  fun formAtIsTheSameGeometryThePlanDraws() {
    // Il piano e la forma sono due viste dello stesso lerp: se divergono, un ritargeting parte da un
    // punto diverso da quello che l'occhio vedeva.
    val transit = SlabTransit(listOf(circle to square), blendRadius = 0f)
    val plan = PhysicsRenderPlan()
    for (t in listOf(0f, 0.2f, 0.5f, 0.83f, 1f, 1.08f)) {
      buildTransitPlan(plan, transit, t)
      val form = transit.formAt(t) as FluidForm.Slab
      assertEquals("cx a t=$t", form.frame.center.x, plan.pieceRects[0], 0.01f)
      assertEquals("cy a t=$t", form.frame.center.y, plan.pieceRects[1], 0.01f)
      assertEquals("half w a t=$t", form.frame.width / 2f, plan.pieceRects[2], 0.01f)
      assertEquals("half h a t=$t", form.frame.height / 2f, plan.pieceRects[3], 0.01f)
      assertEquals("raggio a t=$t", form.cornerRadii.topLeft, plan.pieceRadii[0], 0.01f)
    }
  }

  @Test
  fun formAtOfSeveralPiecesIsAGroupThatKeepsTheBlend() {
    val other = FluidForm.Slab(Rect(0f, 0f, 40f, 40f), FluidCornerRadii.all(20f))
    val transit = SlabTransit(listOf(circle to square, other to square), blendRadius = 36f)

    val form = transit.formAt(0.4f)

    assertTrue(form is FluidForm.Group)
    form as FluidForm.Group
    assertEquals(2, form.pieces.size)
    assertEquals(36f, form.blendRadius, 0f)
  }

  @Test
  fun formAtOfARingTransitIsAPolyThatEndsOnTheTarget() {
    val target = FluidForm.Slab(Rect(400f, 400f, 600f, 600f), FluidCornerRadii.Zero)
    val transit = RingTransit(buildMatchedRings(circle, target))

    val start = transit.formAt(0f)
    val end = transit.formAt(1f)

    assertTrue(start is FluidForm.Poly)
    assertTrue(end is FluidForm.Poly)
    // Gli anelli campionano il perimetro: a t=1 il riquadro dei vertici e' quello del traguardo
    // entro il passo del campionamento, a t=0 e' quello del cerchio.
    assertEquals(target.frame.center.x, end.frame.center.x, 2f)
    assertEquals(target.frame.center.y, end.frame.center.y, 2f)
    assertEquals(circle.frame.center.x, start.frame.center.x, 2f)
    assertEquals(circle.frame.center.y, start.frame.center.y, 2f)
  }
}
