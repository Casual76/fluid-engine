package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La terza correzione di Haze (R3 cap. 2, F3.3): l'ottica di un campo non viene dalla taglia del layer allargato (il
 * palco), e la banda della lente non supera mai mezzo lato corto del pezzo più piccolo.
 */
class PhysicsFieldOpticsTest {

  private val d = 3f

  private fun inputsFor(form: FluidForm): Pair<PhysicsRenderPlan, Float> {
    val plan = PhysicsRenderPlan()
    buildRestPlan(plan, form, physicsNeckEncode(1f))
    val shortDp = physicsRecipeShortPx(plan) / d
    val recipe = glassRecipe(
      role = GlassRole.Interactive, widthDp = shortDp, heightDp = shortDp, darkSide = false,
      optics = GlassDefaults.buttonOptics(),
    )
    return plan to physicsLiquidInputs(plan, recipe, 1f, d, 0f).thickness
  }

  private fun button(x: Float) = FluidFormPresets.capsule(Rect(x * d, 0f, (x + 44f) * d, 44f * d))

  @Test
  fun theRecipeComesFromThePiecesNotFromTheStage() {
    // Due tasti a 8 dp e due tasti a 200 dp: il palco è cinque volte più largo, la ricetta è la stessa.
    val near = FluidForm.Group(listOf(button(0f), button(52f)), blendRadius = 16f * d)
    val far = FluidForm.Group(listOf(button(0f), button(244f)), blendRadius = 16f * d)
    val (nearPlan, nearBand) = inputsFor(near)
    val (farPlan, farBand) = inputsFor(far)
    assertEquals(44f * d, physicsRecipeShortPx(nearPlan), 1e-3f)
    assertEquals(physicsRecipeShortPx(nearPlan), physicsRecipeShortPx(farPlan), 1e-3f)
    assertEquals(nearBand, farBand, 1e-3f)
  }

  @Test
  fun theLensBandNeverPassesHalfTheShortSideOfTheSmallestPiece() {
    // Un tasto da 44 dp che entra in un pannello da 200 x 160: la ricetta è quella del pannello, la banda no.
    val panel = FluidForm.Slab(Rect(60f * d, 0f, 260f * d, 160f * d), FluidCornerRadii.all(28f * d))
    val mixed = FluidForm.Group(listOf(button(0f), panel), blendRadius = 16f * d)
    val (_, band) = inputsFor(mixed)
    assertTrue("la banda è $band px, il tasto ne ha ${22f * d} di mezzo lato", band <= 22f * d + 1e-3f)
    // Un pezzo solo: la regola non tocca niente, la banda è quella della ricetta.
    val (_, alone) = inputsFor(panel)
    assertTrue("il pannello da solo ha la sua banda: $alone", alone > 22f * d)
  }
}
