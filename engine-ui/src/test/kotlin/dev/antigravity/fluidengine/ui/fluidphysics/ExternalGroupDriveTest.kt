package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * `driveExternally` fra gruppi di lastre (3.0): i tasti di vetro della barra ancorata, quando la
 * pagina cambia, viaggiano come un'unica superficie guidata dal progresso del dito.
 *
 * Quello che si controlla: i pezzi del piano sono quanti ne servono (il massimo dei due lati), un
 * seme da un pixel nasce o si spegne sul posto, alla fine c'e' esattamente il traguardo, e ogni
 * progresso nuovo e' un piano nuovo mentre lo stesso progresso riusa quello che c'e'.
 */
class ExternalGroupDriveTest {

  private fun capsule(left: Float, top: Float = 10f, size: Float = 44f) =
    FluidForm.Slab(Rect(left, top, left + size, top + size), FluidCornerRadii.all(size / 2f))

  private fun seed(at: FluidForm.Slab): FluidForm.Slab {
    val c = at.frame.center
    return FluidForm.Slab(Rect(c.x - 0.5f, c.y - 0.5f, c.x + 0.5f, c.y + 0.5f), FluidCornerRadii.all(0.5f))
  }

  private fun group(vararg pieces: FluidForm.Slab) = FluidForm.Group(pieces.toList(), blendRadius = 12f)

  private fun PhysicsRenderPlan.centerX(i: Int) = pieceRects[i * 4]
  private fun PhysicsRenderPlan.halfWidth(i: Int) = pieceRects[i * 4 + 2]

  private fun centersOf(plan: PhysicsRenderPlan): List<Float> =
    (0 until plan.pieceCount).map { plan.centerX(it) }.sorted()

  @Test
  fun dueTasiDiventanoTre() {
    val from = group(capsule(200f), capsule(250f))
    val to = group(capsule(150f), capsule(200f), capsule(250f))
    var t = 0f
    val state = FluidPhysicsState(from)
    state.driveExternally(from, to, progress = { t })

    // Un pezzo per coppia: tre, anche se la partenza ne ha due (uno si divide).
    assertEquals(3, state.ensurePlan().pieceCount)
    assertEquals(PlanModeSlabs, state.ensurePlan().mode)

    t = 1f
    val end = state.ensurePlan()
    assertEquals(3, end.pieceCount)
    assertEquals(listOf(172f, 222f, 272f), centersOf(end).map { Math.round(it).toFloat() })
  }

  @Test
  fun treTastiSiSpengonoSulPostoVersoISemi() {
    val a = capsule(150f)
    val b = capsule(200f)
    val c = capsule(250f)
    val from = group(a, b, c)
    val to = group(seed(a), seed(b), seed(c))
    var t = 0f
    val state = FluidPhysicsState(from)
    state.driveExternally(from, to, progress = { t })

    t = 0.5f
    val half = state.ensurePlan()
    assertEquals(3, half.pieceCount)
    // Ogni pezzo resta sul proprio centro mentre si stringe: nessuno attraversa la barra.
    assertEquals(listOf(172f, 222f, 272f), centersOf(half).map { Math.round(it).toFloat() })
    for (i in 0 until 3) assertEquals(11.25f, half.halfWidth(i), 0.01f)

    t = 1f
    val end = state.ensurePlan()
    for (i in 0 until 3) assertEquals(0.5f, end.halfWidth(i), 0.01f)
  }

  @Test
  fun seiPezziPerParte() {
    val from = group(*Array(6) { capsule(40f + it * 50f) })
    val to = group(*Array(6) { capsule(40f + it * 50f, top = 20f) })
    var t = 0.25f
    val state = FluidPhysicsState(from)
    state.driveExternally(from, to, progress = { t })
    val plan = state.ensurePlan()
    assertEquals(6, plan.pieceCount)
  }

  @Test
  fun unPianoNuovoPerOgniProgresso() {
    val from = group(capsule(200f), capsule(250f))
    val to = group(capsule(150f), capsule(250f))
    var t = 0.2f
    val state = FluidPhysicsState(from)
    state.driveExternally(from, to, progress = { t })

    val first = state.clipShapeNow()
    assertSame("stesso progresso, stesso piano", first, state.clipShapeNow())
    t = 0.3f
    assertNotSame("progresso nuovo, piano nuovo", first, state.clipShapeNow())
  }

  @Test
  fun dueLastreRestanoIlViaggioDiSempre() {
    // Lastra → lastra passa dalla strada a un pezzo: stessa geometria della guida classica.
    val from = capsule(200f)
    val to = capsule(100f, size = 88f)
    val state = FluidPhysicsState(from)
    state.driveExternally(from as FluidForm, to as FluidForm, progress = { 1f })
    val plan = state.ensurePlan()
    assertEquals(1, plan.pieceCount)
    assertEquals(144f, plan.centerX(0), 0.01f)
    assertEquals(44f, plan.halfWidth(0), 0.01f)
  }
}
