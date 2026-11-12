package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il collo liquido del materiale nuovo **mentre** i pezzi si separano e si riuniscono (giro di correzione unico di F1B,
 * rilievo 3 del critico A; scelta di Alessio: collo in viaggio, pezzi staccati a riposo).
 *
 * In (a), un tasto da 44 dp che diventa due tasti a 8 dp l'uno dall'altro (fusione 16 dp, la traccia `tasto-diventa-due`),
 * dopo F1B.1 la sagoma era per quasi tutto il viaggio l'unione **netta** di due cerchi che si compenetrano: due cuspidi a
 * V, poi due cerchi staccati in un fotogramma. Qui, sul campo che lo shader riceve ([physicsFieldPlan], con lo specchio
 * Kotlin del suo `glassField`):
 *  - finché i due capi arrotondati si toccano (la V c'è) il raccordo la riempie: la tacca in mezzo, in alto, è meno di
 *    metà di quella dell'unione netta;
 *  - il ponte sopravvive al contatto: dopo che i cerchi si sono staccati il varco resta coperto per un tratto, poi si
 *    rompe prima dell'arrivo;
 *  - a riposo e ai due capi del viaggio nessun raccordo; pezzi che coincidono non si gonfiano.
 */
class PhysicsNeckTest {

  private val density = 3f
  private fun px(dp: Float) = dp * density
  private fun circle(leftDp: Float, topDp: Float): FluidForm.Slab =
    FluidFormPresets.capsule(Rect(px(leftDp), px(topDp), px(leftDp + 44f), px(topDp + 44f)))

  private val one = circle(128f, 128f)
  private val left = circle(102f, 128f)
  private val right = circle(154f, 128f)
  private val blend = px(16f)

  private fun field(t: Float, plan: PhysicsRenderPlan = PhysicsRenderPlan()): GlassFieldPlan {
    buildSlabPlan(plan, listOf(one to left, one to right), blend, t, fusion = travelFusion(t, PhysicsTestNeck))
    return physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces))
  }

  /** L'altezza del contorno in alto alla colonna [x]: la distanza nulla, per bisezione dal centro verso l'alto. */
  private fun top(f: GlassFieldPlan, x: Float, sharp: Boolean = false): Float {
    val blend = if (sharp) FloatArray(f.blend.size) else f.blend
    val cy = one.frame.center.y
    var inside = cy
    var outside = cy - px(40f)
    repeat(40) {
      val mid = (inside + outside) / 2f
      if (GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, blend, f.smooth, x, mid) < 0f) inside = mid else outside = mid
    }
    return (inside + outside) / 2f
  }

  /** Quanto il contorno, in alto, scende in mezzo ai due pezzi rispetto ai due centri: la tacca della V. */
  private fun notch(f: GlassFieldPlan, sharp: Boolean = false): Float {
    val a = f.rects[0]
    val b = f.rects[4]
    val mid = top(f, (a + b) / 2f, sharp)
    val lobes = minOf(top(f, a, sharp), top(f, b, sharp))
    return mid - lobes
  }

  @Test
  fun whileTheTwoEndsTouchTheNeckFillsTheV() {
    var checked = 0
    var t = 0.30f
    while (t <= 0.84f) {
      val f = field(t)
      val gap = (f.rects[4] - f.rects[0]) - (f.rects[2] + f.rects[6])
      val depth = -gap
      if (depth > px(2f) && depth < px(20f)) {
        val sharp = notch(f, sharp = true)
        val soft = notch(f)
        assertTrue("t=$t: la tacca netta è ${sharp / density} dp", sharp > px(0.5f))
        // Col raccordo di prima (spento dalla compenetrazione, collo a 0,5): 1 a t = 0,5, 0,77 a t = 0,7.
        val limit = if (t <= 0.7f) 0.4f else 0.6f
        assertTrue("t=$t: col collo la tacca è ${soft / density} dp, l'unione netta ${sharp / density} dp", soft < sharp * limit)
        checked++
      }
      t += 0.02f
    }
    assertTrue("nessun progresso con i capi che si toccano: $checked", checked >= 5)
  }

  @Test
  fun theBridgeOutlivesTheContactAndBreaksBeforeTheEnd() {
    var bridgedApart = 0
    var brokeAt = Float.NaN
    var t = 0.80f
    while (t <= 1.0f) {
      val f = field(t)
      val gap = (f.rects[4] - f.rects[0]) - (f.rects[2] + f.rects[6])
      val midX = (f.rects[0] + f.rects[4]) / 2f
      val bridged = GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, midX, f.rects[1]) < 0f
      if (gap > 0f && bridged) bridgedApart++
      if (gap > 0f && !bridged && brokeAt.isNaN()) brokeAt = t
      t += 0.005f
    }
    assertTrue("il ponte non sopravvive al contatto", bridgedApart >= 3)
    assertTrue("il ponte si rompe prima dell'arrivo (a $brokeAt)", brokeAt.isFinite() && brokeAt < 0.98f)
  }

  @Test
  fun atRestAndAtBothEndsThereIsNoBlendAndCoincidentPiecesDoNotSwell() {
    val plan = PhysicsRenderPlan()
    for (t in listOf(0f, 1f)) {
      val f = field(t, plan)
      assertEquals("t=$t", 0f, f.blend[1], 0f)
    }
    buildRestPlan(plan, FluidForm.Group(listOf(left, right), blendRadius = blend))
    assertEquals(0f, physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces)).blend[1], 0f)
    // Due pezzi uguali e sovrapposti del tutto, a collo pieno (la fine di una riunione): nessun raccordo, nessun alone.
    buildSlabPlan(plan, listOf(one to one, one to one), blend, 0.5f, fusion = 1f)
    assertEquals(0f, physicsNeckBlend(plan, 1), 0f)
    // Staccati: il raggio del gruppo.
    buildSlabPlan(plan, listOf(left to left, right to right), blend, 0.5f, fusion = 1f)
    assertEquals(blend, physicsNeckBlend(plan, 1), 1e-3f)
  }

  private companion object {
    /** Il collo di serie: vedi `GlassLiquidNeck`. */
    const val PhysicsTestNeck = 0.32f
  }
}
