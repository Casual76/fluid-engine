package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'oltrepasso e' un gonfiore uniforme attorno al centro del traguardo (3.0), mai il lerp estrapolato
 * oltre l'1. Il lerp estrapolato proseguiva il viaggio: una riga larga che diventa una card stretta,
 * oltre l'arrivo, aveva i fianchi in dentro e il fondo in fuori — uno stiramento, non un rimbalzo.
 */
class MorphOvershootTest {

  /** Una riga larga e bassa, in basso. */
  private val row = FluidForm.Slab(Rect(20f, 900f, 1060f, 1020f), FluidCornerRadii.all(30f))

  /** Una card stretta e alta, in alto. */
  private val card = FluidForm.Slab(Rect(240f, 200f, 840f, 800f), FluidCornerRadii.all(40f))

  private fun PhysicsRenderPlan.centerX() = pieceRects[0]
  private fun PhysicsRenderPlan.centerY() = pieceRects[1]
  private fun PhysicsRenderPlan.halfWidth() = pieceRects[2]
  private fun PhysicsRenderPlan.halfHeight() = pieceRects[3]

  @Test
  fun pastTheTargetTheSlabIsTheTargetInflatedAroundItsCentre() {
    val transit = SlabTransit(listOf(row to card), blendRadius = 0f)
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, transit, 1.05f)

    // Il centro e' quello del traguardo: il pannello non prosegue verso l'alto.
    assertEquals(card.frame.center.x, plan.centerX(), 0.001f)
    assertEquals(card.frame.center.y, plan.centerY(), 0.001f)
    // Gonfiato di (t - 1) * guadagno su ENTRAMBI gli assi: le proporzioni restano quelle della card.
    val scale = 1f + 0.05f * MorphInflationGain
    assertEquals(card.frame.width / 2f * scale, plan.halfWidth(), 0.01f)
    assertEquals(card.frame.height / 2f * scale, plan.halfHeight(), 0.01f)
    assertEquals(40f * scale, plan.pieceRadii[0], 0.01f)

    // E non e' il lerp estrapolato, che avrebbe stretto i fianchi (la riga e' piu' larga della card).
    val extrapolated = lerpSlab(row, card, 1.05f)
    assertNotEquals(extrapolated[2] / 2f, plan.halfWidth(), 1f)
    assertTrue(plan.halfWidth() > card.frame.width / 2f)
  }

  @Test
  fun atOneAndBelowNothingChanges() {
    val transit = SlabTransit(listOf(row to card), blendRadius = 0f)
    val plan = PhysicsRenderPlan()
    for (t in listOf(0f, 0.4f, 0.9f, 1f)) {
      buildTransitPlan(plan, transit, t)
      val v = lerpSlab(row, card, t)
      assertEquals("t=$t", v[0] + v[2] / 2f, plan.centerX(), 0.001f)
      assertEquals("t=$t", v[2] / 2f, plan.halfWidth(), 0.001f)
    }
  }

  @Test
  fun theInflationIsCappedInFractionAndInPixels() {
    // Una molla viva o una velocita' ereditata alta: il tetto in frazione trattiene il picco.
    val free = SlabTransit(listOf(row to card), blendRadius = 0f)
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, free, 1.9f)
    assertEquals(card.frame.width / 2f * (1f + MorphMaxInflation), plan.halfWidth(), 0.01f)

    // Il tetto per lato, in pixel: il lato lungo esce al massimo di capPx per parte, e il corto lo
    // segue con la stessa scala.
    val capped = SlabTransit(listOf(row to card), blendRadius = 0f, overshootCapPx = 6f)
    buildTransitPlan(plan, capped, 1.1f)
    val longSide = maxOf(card.frame.width, card.frame.height)
    val scale = 1f + 2f * 6f / longSide
    assertEquals(card.frame.width / 2f * scale, plan.halfWidth(), 0.01f)
    assertEquals(card.frame.height / 2f * scale, plan.halfHeight(), 0.01f)
    assertTrue(plan.halfWidth() - card.frame.width / 2f <= 6f + 0.01f)
  }

  @Test
  fun theOvershootScaleIsOneUpToTheTargetAndGrowsSmoothlyAfter() {
    val frame = card.frame
    assertEquals(1f, morphOvershootScale(0.5f, frame, Float.POSITIVE_INFINITY), 0f)
    assertEquals(1f, morphOvershootScale(1f, frame, Float.POSITIVE_INFINITY), 0f)
    assertEquals(1f, morphOvershootScale(Float.NaN, frame, Float.POSITIVE_INFINITY), 0f)
    var previous = 1f
    for (i in 1..40) {
      val s = morphOvershootScale(1f + i * 0.005f, frame, 24f)
      assertTrue(s >= previous)
      previous = s
    }
  }

  @Test
  fun aSplitInflatesTheWholeGroupAroundItsCentre() {
    // Un pezzo che si divide in due: oltre l'arrivo i due pezzi crescono E si allontanano dal centro
    // dell'unione, come un'immagine ingrandita — non ognuno attorno al proprio centro.
    val left = FluidForm.Slab(Rect(100f, 400f, 300f, 500f), FluidCornerRadii.all(50f))
    val right = FluidForm.Slab(Rect(500f, 400f, 700f, 500f), FluidCornerRadii.all(50f))
    val transit = SlabTransit(listOf(card to left, card to right), blendRadius = 40f)
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, transit, 1.1f)

    val scale = 1f + 0.1f * MorphInflationGain
    val center = 400f
    assertEquals(center + (200f - center) * scale, plan.pieceRects[0], 0.01f)
    assertEquals(center + (600f - center) * scale, plan.pieceRects[4], 0.01f)
    assertEquals(100f * scale, plan.pieceRects[2], 0.01f)
    // Il centro dell'unione non si muove.
    assertEquals(400f, plan.bounds.center.x, 0.01f)
  }

  @Test
  fun aMergePastTheTargetIsOneContourWithTheBridgeOff() {
    val transit = SlabTransit(listOf(row to card, card to card), blendRadius = 40f)
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, transit, 1.06f)
    // I due pezzi sono lo stesso traguardo gonfiato: coincidono, e il ponte e' spento.
    assertEquals(plan.pieceRects[0], plan.pieceRects[4], 0.001f)
    assertEquals(plan.pieceRects[2], plan.pieceRects[6], 0.001f)
    assertEquals(0f, plan.blendRadius, 0.001f)
  }

  @Test
  fun aRingPastTheTargetIsTheTargetRingInflated() {
    val star = FluidFormPresets.star(Rect(300f, 300f, 700f, 700f))
    val transit = RingTransit(buildMatchedRings(card, star))
    val atTarget = transit.formAt(1f)
    val past = transit.formAt(1.1f)
    val scale = 1f + 0.1f * MorphInflationGain
    assertEquals(atTarget.frame.center.x, past.frame.center.x, 0.5f)
    assertEquals(atTarget.frame.center.y, past.frame.center.y, 0.5f)
    assertEquals(atTarget.frame.width * scale, past.frame.width, 1f)
    assertEquals(atTarget.frame.height * scale, past.frame.height, 1f)
  }

  @Test
  fun formAtPastTheTargetIsWhatThePlanDraws() {
    val transit = SlabTransit(listOf(row to card), blendRadius = 0f)
    val plan = PhysicsRenderPlan()
    for (t in listOf(1.02f, 1.08f, 1.2f)) {
      buildTransitPlan(plan, transit, t)
      val form = transit.formAt(t) as FluidForm.Slab
      assertEquals(form.frame.center.x, plan.centerX(), 0.01f)
      assertEquals(form.frame.width / 2f, plan.halfWidth(), 0.01f)
      assertEquals(form.cornerRadii.topLeft, plan.pieceRadii[0], 0.01f)
    }
  }

  @Test
  fun anExternalDriveWithoutInflationStopsOnTheTargetInsteadOfStretching() {
    val state = FluidPhysicsState(row)
    state.driveExternally(row, card, progress = { 1.08f })
    val plan = state.ensurePlan()
    assertEquals(card.frame.center.x, plan.centerX(), 0.001f)
    assertEquals(card.frame.center.y, plan.centerY(), 0.001f)
    assertEquals(card.frame.width / 2f, plan.halfWidth(), 0.001f)
    assertEquals(card.frame.height / 2f, plan.halfHeight(), 0.001f)
  }

  @Test
  fun aSettledDriveIsAStableRestingSlab() {
    val state = FluidPhysicsState(row)
    var start = Rect(10f, 890f, 1070f, 1030f)
    state.driveExternally(row, card, progress = { 1f }, startFrame = { start })
    val first = state.ensurePlan().shape
    // La partenza si muove ancora (la pressione che si scioglie): all'1 non entra nella geometria,
    // e l'istanza resta una.
    start = row.frame
    assertTrue(first === state.ensurePlan().shape)
    assertEquals(card, state.restSlabNow())
  }

  @Test
  fun theSlabInflationKeepsACapsuleACapsule() {
    val capsule = FluidFormPresets.capsule(Rect(0f, 0f, 300f, 100f))
    val inflated = inflateSlab(capsule, 150f, 50f, 1.1f, 1.1f)
    assertEquals(inflated.frame.height / 2f, inflated.cornerRadii.topLeft, 0.001f)
    assertEquals(Offset(150f, 50f), inflated.frame.center)
  }
}
