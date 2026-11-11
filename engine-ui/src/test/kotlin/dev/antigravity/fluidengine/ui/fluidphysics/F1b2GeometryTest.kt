package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldSample
import dev.antigravity.fluidengine.ui.glass.field.GlassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabIsSmall
import kotlin.math.abs
import kotlin.math.max
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La geometria di F1B.2 sul motore: il raggio su cui normale e centro si pesano dove i pezzi si sovrappongono
 * ([physicsPieceSoft], le cuciture di `gruppo-diventa-menu`) e l'anello della goccia che, posato, ha gli angoli della
 * lastra che lo sostituisce ([RoundedRectPerimeter.setField], l'angolo alla posa del menu).
 */
class F1b2GeometryTest {

  private fun circle(leftPx: Float, topPx: Float, sizePx: Float) =
    FluidFormPresets.capsule(Rect(leftPx, topPx, leftPx + sizePx, topPx + sizePx))

  private fun transit(from: FluidForm, to: FluidForm, blendPx: Float, t: Float): PhysicsRenderPlan {
    val a = (from as? FluidForm.Group)?.pieces?.map { it as FluidForm.Slab } ?: listOf(from as FluidForm.Slab)
    val b = (to as? FluidForm.Group)?.pieces?.map { it as FluidForm.Slab } ?: listOf(to as FluidForm.Slab)
    val pairs = if (a.size == 1) b.map { a[0] to it } else a.map { it to b[0] }
    val plan = PhysicsRenderPlan()
    buildTransitPlan(plan, SlabTransit(pairs, blendPx, overshootCapPx = MorphInflationCapDp * 3f), t)
    return plan
  }

  @Test
  fun piecesThatDoNotTouchKeepTheSharpWeightAndOverlappingOnesGetTheSoftOne() {
    val d = 3f
    // A riposo e ai capi di un viaggio fra tasti: tre tasti a 8 dp l'uno dall'altro, nessuna sovrapposizione.
    val group = FluidForm.Group(listOf(circle(300f, 600f, 132f), circle(456f, 600f, 132f), circle(612f, 600f, 132f)), blendRadius = 16f * d)
    val panel = FluidForm.Slab(
      frame = Rect(234f, 84f, 744f, 660f),
      cornerRadii = FluidCornerRadii.all(84f),
      smoothing = ContinuousCornerShape.IosSmoothing,
    )
    val start = transit(group, panel, 16f * d, 0f)
    for (i in 1 until start.pieceCount) assertEquals("pezzo $i alla partenza", 0f, physicsPieceSoft(start, i), 0f)
    // Verso l'arrivo i tre pezzi si coprono quasi del tutto, e il collo è spento: il peso è quello pieno.
    val late = transit(group, panel, 16f * d, 0.97f)
    assertTrue("il collo verso l'arrivo vale ${late.fusion}", late.fusion < 0.2f)
    assertEquals(16f * d, late.nominalBlend, 0f)
    for (i in 1 until late.pieceCount) {
      assertEquals("pezzo $i verso l'arrivo", late.nominalBlend, physicsPieceSoft(late, i), 1e-3f)
    }
    // Il piano del campo lo porta in `pieceSoft`, alla scala della cattura; il primo pezzo non si legge.
    val field = physicsFieldPlan(late, 1f, GlassFieldPlan())
    assertEquals(0f, field.soft[0], 0f)
    assertTrue(field.soft[1] > 0f && field.blend[1] < field.soft[1])
    // Lungo il viaggio cresce senza salti.
    var last = 0f
    var t = 0f
    while (t <= 1f) {
      val plan = transit(group, panel, 16f * d, t)
      val soft = physicsPieceSoft(plan, 1)
      assertTrue("a t=$t il peso salta da $last a $soft", abs(soft - last) <= 16f * d * 0.35f)
      last = soft
      t += 0.02f
    }
  }

  @Test
  fun aDropAtRestHasTheCornersOfTheSlabThatReplacesIt() {
    // Il pannello del menu a densità 3: 170 x 192 dp, raggio 28 dp, raccordo di iOS.
    val slab = FluidForm.Slab(
      frame = Rect(234f, 84f, 744f, 660f),
      cornerRadii = FluidCornerRadii.all(84f),
      smoothing = ContinuousCornerShape.IosSmoothing,
    )
    val geometry = glassSlabGeometry(
      width = slab.frame.width,
      height = slab.frame.height,
      topLeft = 84f,
      topRight = 84f,
      bottomRight = 84f,
      bottomLeft = 84f,
      smoothing = slab.smoothing,
      continuous = true,
      small = glassSlabIsSmall(slab.frame.maxDimension),
      out = GlassSlabGeometry(),
    )
    val radii = floatArrayOf(geometry.topLeft, geometry.topRight, geometry.bottomRight, geometry.bottomLeft)
    val sample = GlassFieldSample()
    fun worstOffField(fieldCorners: Boolean): Float {
      val ring = FloatArray(DropRenderPoints * 2)
      buildDropRing(slab, LiquidDrop(), DropRenderPoints, ring, RoundedRectPerimeter(), FloatArray(DropRenderPoints), fieldCorners)
      var worst = 0f
      for (i in 0 until DropRenderPoints) {
        GlassFieldGeometry.slab(
          ring[i * 2] - slab.frame.center.x,
          ring[i * 2 + 1] - slab.frame.center.y,
          slab.frame.width / 2f,
          slab.frame.height / 2f,
          radii,
          0,
          geometry.smoothing,
          sample,
        )
        worst = max(worst, abs(sample.distance))
      }
      return worst
    }
    val before = worstOffField(fieldCorners = false)
    val after = worstOffField(fieldCorners = true)
    // Prima: archi di cerchio al raggio nominale, lontani dal raccordo del campo quasi un pixel (0,87 px a 84 px di
    // raggio): sul pixel del bordo è il salto alla posa.
    assertTrue("con gli archi di cerchio l'anello si stacca solo di $before px", before > 0.6f)
    // Dopo: i vertici stanno sul contorno della lastra (resta la precisione della distanza raccordata del campo).
    assertTrue("con gli angoli del campo l'anello si stacca di $after px", after < 0.1f)
  }

  @Test
  fun theDropFieldTurnsIntoTheSlabFieldOnlyWhileItSettles() {
    // In volo (lente viva, contorno lontano dalla sagoma a riposo) il campo è l'anello, al bit.
    assertEquals(0f, physicsDropSettle(lens = 0.3f, reachDp = 20f), 0f)
    assertEquals(0f, physicsDropSettle(lens = 0.001f, reachDp = 5f), 0f)
    assertEquals(0f, physicsDropSettle(lens = 0.2f, reachDp = 0.1f), 0f)
    // Alla soglia di quiete della goccia (lente sotto 0,002, contorno a meno di un quarto di dp) è la lastra.
    assertEquals(1f, physicsDropSettle(lens = 0.0019f, reachDp = 0.2f), 0f)
    // La coda di una goccia che si calma: la lente scende di un quarto a fotogramma, il contorno di un terzo. Il peso
    // sale senza tornare indietro e senza salti grandi, e arriva a uno prima della posa.
    var lens = 0.08f
    var reach = 4f
    var last = 0f
    var steps = 0
    var biggest = 0f
    while (lens > 0.0019f) {
      val w = physicsDropSettle(lens, reach)
      assertTrue("il peso torna indietro: $last -> $w", w >= last - 1e-6f)
      biggest = max(biggest, w - last)
      if (w > 0f && w < 1f) steps++
      last = w
      lens *= 0.75f
      reach *= 0.66f
    }
    assertEquals(1f, physicsDropSettle(lens, reach), 0f)
    assertTrue("il passaggio dura $steps fotogrammi", steps >= 6)
    assertTrue("un salto di $biggest in un fotogramma", biggest < 0.3f)
  }

  @Test
  fun theLargeVeilWalksTowardTheArrivalAtASteadyPace() {
    val veil = PhysicsLargeVeil()
    val frame = 16_666_667L
    // Il primo disegno parte dalla forma di partenza (un tasto: velo dei piccoli), anche se la meta è un pannello.
    assertEquals(0f, veil.next(origin = 0f, target = 1f, nowNanos = 0L), 0f)
    var last = 0f
    var k = 1
    while (last < 1f && k < 100) {
      val w = veil.next(origin = 0f, target = 1f, nowNanos = k * frame)
      // Un passo per fotogramma a 60 Hz: 33 livelli su 255 fra i due veli scuri, meno di due per fotogramma.
      assertTrue("passo di ${w - last} a $k", w - last <= frame / 1e9f / PhysicsLargeVeilSeconds + 1e-5f)
      last = w
      k++
    }
    assertEquals(1f, last, 0f)
    assertTrue("arriva in $k fotogrammi", k * frame / 1e9f <= PhysicsLargeVeilSeconds + 0.04f)
    // Fermo, resta. Dopo una pausa lunga (una superficie che non si ridisegna) il primo passo è di un fotogramma.
    assertEquals(1f, veil.next(origin = 0f, target = 1f, nowNanos = 10_000_000_000L), 0f)
    val back = veil.next(origin = 1f, target = 0f, nowNanos = 20_000_000_000L)
    assertEquals(1f - PhysicsLargeVeilMaxStepSeconds / PhysicsLargeVeilSeconds, back, 1e-4f)
    // Fra due forme della stessa famiglia di taglia non si muove.
    val bar = PhysicsLargeVeil()
    for (i in 0..30) assertEquals(0f, bar.next(origin = 0f, target = 0f, nowNanos = i * frame), 0f)
  }
}
