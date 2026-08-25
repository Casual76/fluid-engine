package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il contratto che risponde alla regola "niente shape morphing": il morphing è transiente. In
 * viaggio ogni cambio di progresso è un'istantanea nuova (l'identità nuova invalida la cache
 * dell'outline); a riposo l'istanza è UNA, e uno scorrimento con un pezzo fermo sopra non paga
 * niente.
 */
class RestStateDisciplineTest {

  private val circle = FluidForm.circle(Offset(200f, 200f), 100f)
  private val square = FluidForm.Slab(Rect(100f, 100f, 300f, 300f), FluidCornerRadii.all(20f))

  @Test
  fun atRestThePlanAndItsShapeAreTheSameInstanceEveryFrame() {
    val state = FluidPhysicsState(circle)
    val first = state.ensurePlan()
    val second = state.ensurePlan()
    assertSame(first, second)
    assertSame(first.shape, second.shape)
  }

  @Test
  fun everyProgressChangeIsAFreshSnapshotWhileTravelling() {
    val plan = PhysicsRenderPlan()
    val transit = SlabTransit(listOf(circle to square), blendRadius = 0f)

    buildTransitPlan(plan, transit, 0.3f)
    val early = plan.shape
    buildTransitPlan(plan, transit, 0.6f)
    val late = plan.shape

    // Identità diversa: è quello che fa ricalcolare l'outline esattamente quando serve.
    assertNotSame(early, late)
  }

  @Test
  fun theEndpointsOfATransitMatchTheEndpointForms() {
    val plan = PhysicsRenderPlan()
    val transit = SlabTransit(listOf(circle to square), blendRadius = 0f)

    buildTransitPlan(plan, transit, 0f)
    assertEquals(circle.frame.center.x, plan.pieceRects[0], 0.001f)
    assertEquals(circle.frame.width / 2f, plan.pieceRects[2], 0.001f)

    buildTransitPlan(plan, transit, 1f)
    assertEquals(square.frame.center.x, plan.pieceRects[0], 0.001f)
    assertEquals(20f, plan.pieceRadii[0], 0.001f)
  }

  @Test
  fun snapToInvalidatesTheRestPlanAndLandsOnTheNewForm() {
    val state = FluidPhysicsState(circle)
    val before = state.ensurePlan().shape

    state.snapTo(square)

    val after = state.ensurePlan()
    assertNotSame(before, after.shape)
    assertEquals(square, state.form)
    assertFalse(state.isMorphing)
    assertEquals(1f, state.progress, 0f)
  }

  @Test
  fun reducedMotionArrivesWithoutTravelling() = runBlocking {
    val state = FluidPhysicsState(circle)
    state.reducedMotion = true

    state.morphTo(square)

    assertFalse(state.isMorphing)
    assertEquals(square, state.form)
    assertEquals(1f, state.progress, 0f)
    assertEquals(square.frame.center.x, state.ensurePlan().pieceRects[0], 0.001f)
  }

  @Test
  fun aGroupRestPlanCarriesEveryPieceAndItsBlend() {
    val group = FluidForm.Group(
      pieces = listOf(circle, FluidForm.Slab(Rect(500f, 100f, 700f, 300f), FluidCornerRadii.all(40f))),
      blendRadius = 64f,
    )
    val state = FluidPhysicsState(group)
    val plan = state.ensurePlan()

    assertEquals(PlanModeSlabs, plan.mode)
    assertEquals(2, plan.pieceCount)
    assertEquals(64f, plan.blendRadius, 0f)
    assertTrue(plan.tintInShader)
    assertEquals(group.frame, plan.bounds)
  }
}
