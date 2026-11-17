package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPlaneDriver
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSwell
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.fluidGlassStagePlane
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidNeck
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Il **contorno unito** del palco nei primi e negli ultimi fotogrammi (giro di correzione di F3, C R6 e B 9): il campo
 * vero di ogni fotogramma (lo specchio Kotlin di `glassField`, dalla forma che il contenitore dà alla superficie, cioè il
 * conducente di produzione e `fluidGlassStagePlane`), misurato come lo misura una tavola: il ponte, la vita, il bordo alto.
 * Non i token della formula: la sagoma.
 *
 * Le geometrie sono quelle della galleria a densità 3: «Modifica» (44 dp) e «Fatto» (88 dp) a 8 dp.
 */
class PlaneContourTest {

  private val d = 3f
  private val nominal = PhysicsPlaneFusionDp * d
  private val frameMs = 1000.0 / 120.0

  private fun piece(id: String, x: Float, w: Float = 44f) = GlassPlanePiece(
    key = id, id = id, bounds = Rect(x * d, 58f * d, (x + w) * d, 102f * d), radii = FluidCornerRadii.all(22f * d),
  )

  private fun state(vararg pieces: GlassPlanePiece) =
    GlassPlaneState(pieces.toList(), nominal, Rect(0f, 0f, 360f * d, 160f * d))

  private val edit = piece("modifica", 44f)
  private val done = piece("fatto", 96f, w = 88f)

  /** Il campo del palco di ogni fotogramma a 120 Hz, dalla partenza alla fine, come lo guida il contenitore. */
  private fun stage(from: GlassPlaneState, to: GlassPlaneState): List<Pair<Double, GlassFieldPlan>> {
    val motion = GlassPlaneMotion()
    val driver = FluidGlassPlaneDriver()
    driver.step(0L, from, motion)
    var now = 8_333_333L
    check(driver.step(now, to, motion) == FluidGlassPlaneDriver.Change.Started)
    val out = ArrayList<Pair<Double, GlassFieldPlan>>()
    while (true) {
      val journey = driver.journey ?: break
      val field = journey.fields.single()
      val shape = fluidGlassStagePlane(
        frame = driver.frame!!, flights = field.flights, origin = Offset.Zero, smoothing = { 0f }, spacingPx = nominal,
        neck = driver.neckNow(field.union),
        swell = FluidGlassSwell(driver.journeyCharacter, driver.journeySwell, FluidMotion.PlaneSwellMaxDp * d, d),
        density = d, movingMs = driver.movingMs.toFloat(),
      )
      val plan = PhysicsRenderPlan()
      buildPlanePlan(plan, shape)
      out += driver.elapsedMs to physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces))
      now += 8_333_333L
      if (driver.step(now, to, motion) == FluidGlassPlaneDriver.Change.Finished) break
    }
    return out
  }

  private fun inside(f: GlassFieldPlan, x: Float, y: Float): Boolean =
    GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, x, y) < 0f

  /** Quanto sale il bordo alto sopra la riga di mezzo [cy] alla colonna [x], in pixel (zero fuori dal vetro). */
  private fun top(f: GlassFieldPlan, x: Float, cy: Float): Float {
    if (!inside(f, x, cy)) return 0f
    var lo = 0f
    var hi = 200f * d
    repeat(30) {
      val m = (lo + hi) / 2f
      if (inside(f, x, cy - m)) lo = m else hi = m
    }
    return lo
  }

  /** Il pezzo [i] del campo come (sinistra, destra, centro y). */
  private fun span(f: GlassFieldPlan, i: Int) =
    Triple(f.rects[i * 4] - f.rects[i * 4 + 2], f.rects[i * 4] + f.rects[i * 4 + 2], f.rects[i * 4 + 1])

  /** La vita fra i primi due pezzi: l'altezza del contorno a metà del varco, sulla più bassa delle due; zero se staccati. */
  private fun waist(f: GlassFieldPlan): Float {
    val (al, ar, cy) = span(f, 0)
    val (bl, br, _) = span(f, 1)
    val left = if (al < bl) ar else br
    val right = if (al < bl) bl else al
    val mid = (left + right) / 2f
    val lobe = min(top(f, (al + ar) / 2f, cy), top(f, (bl + br) / 2f, cy))
    return if (lobe > 0f) top(f, mid, cy) / lobe else 0f
  }

  /**
   * Quante volte il bordo alto, letto da sinistra a destra fra [from] e [to], **risale dopo essere sceso** di più di
   * [tolerance] px: zero per una sagoma tonda o una capsula (sale, magari piano, poi scende), uno o più per un'«orecchia».
   */
  private fun ears(f: GlassFieldPlan, from: Float, to: Float, cy: Float, tolerance: Float = 0.6f): Int {
    var count = 0
    var peak = -1f
    var valley = 0f
    var descending = false
    var x = from
    while (x <= to) {
      val h = top(f, x, cy)
      if (!descending) {
        if (h >= peak) {
          peak = h
        } else if (peak - h > tolerance) {
          descending = true
          valley = h
        }
      } else if (h < valley) {
        valley = h
      } else if (h - valley > tolerance) {
        count++
        descending = false
        peak = h
      }
      x += 1f
    }
    return count
  }

  @Test
  fun theConfluenceBridgeGrowsFromZeroAcrossFramesInsteadOfSnapping() {
    // S4 di F3, f316 → f317: «due punte» a +8 ms e il collo pieno a +17. La vita misurata sul contorno nei primi
    // fotogrammi della confluenza (Fatto confluisce in Modifica): parte staccata, poi il ponte nasce sottile e cresce.
    val frames = stage(state(edit, done), state(edit))
    val first = frames.take(8).map { (ms, f) -> ms to waist(f) }
    println("F3 giro di correzione, confluenza, vita nei primi fotogrammi: " +
      first.joinToString { (ms, w) -> "${"%.1f".format(ms)} ms ${"%.2f".format(w)}" })
    assertEquals("il primo fotogramma è il riposo: staccati", 0f, first.first().second, 0f)
    first.zipWithNext().forEach { (a, b) ->
      assertTrue("${a.first} → ${b.first} ms: la vita salta da ${a.second} a ${b.second}", b.second - a.second < 0.4f)
    }
    val born = first.firstOrNull { it.second > 0f }
    assertTrue("il ponte nasce nei primi fotogrammi", born != null)
    assertTrue("e nasce sottile: ${born!!.second}", born.second < 0.55f)
  }

  @Test
  fun theLastFramesHaveNoEarsAndLandOnTheRestShape() {
    // F3.12c §4: nella prima versione della confluenza chi accoglie aveva due «orecchie» sul fianco da ~300 a ~500 ms, e
    // i test non le vedevano. Qui il bordo alto di chi accoglie, negli ultimi fotogrammi e a metà della coda: sale e
    // scende una volta sola. E l'ultimo fotogramma è il riposo: la sagoma di «Modifica» da solo.
    val merge = stage(state(edit, done), state(edit))
    val restHalf = 22f * d
    for ((ms, f) in merge.filter { it.first >= 280.0 }) {
      val (l, r, cy) = span(f, 0)
      assertEquals("t=$ms: orecchie sul bordo di chi accoglie", 0, ears(f, l - 30f, r + 30f, cy))
    }
    val (lastMs, last) = merge.last()
    val (l, r, cy) = span(last, 0)
    assertEquals("t=$lastMs: alla fine il tondo di riposo", restHalf, top(last, (l + r) / 2f, cy), 0.6f)
    // La nascita: negli ultimi fotogrammi i due vetri sono staccati e tondi, ognuno sale e scende una volta.
    val birth = stage(state(edit), state(edit, done))
    for ((ms, f) in birth.takeLast(5) + birth.filter { it.first in 360.0..380.0 }) {
      for (i in 0 until f.count) {
        val (pl, pr, py) = span(f, i)
        assertEquals("t=$ms pezzo $i: orecchie", 0, ears(f, pl - 2f, pr + 2f, py))
      }
    }
    val end = birth.last().second
    assertFalse("alla fine staccati", inside(end, (span(end, 0).second + span(end, 1).first) / 2f, span(end, 0).third))
  }

  @Test
  fun theBirthWaistNarrowsUntilTheBreakWithoutReopening() {
    // Punto 10 del giro (scelta di Alessio, «C corretta»): la vita della goccia, misurata sul contorno, si stringe da
    // ~200 ms fino alla rottura senza mai riaprirsi (il prototipo C la riapriva fra 333 e 367 ms), e la rottura resta
    // dov'era (~415 ms).
    val birth = stage(state(edit), state(edit, done))
    val waists = birth.filter { it.first in 200.0..460.0 }.map { (ms, f) -> ms to waist(f) }
    println("F3 giro di correzione, nascita, vita (H del lobo più basso): " +
      waists.filter { (it.first - 200.0) % 33.0 < frameMs }.joinToString { (ms, w) -> "${"%.0f".format(ms)} ms ${"%.3f".format(w)}" })
    val connected = waists.filter { it.second > 0f }
    connected.filter { it.first >= 300.0 }.zipWithNext().forEach { (a, b) ->
      assertTrue("${a.first} → ${b.first} ms: la vita si riapre (${a.second} → ${b.second})", b.second <= a.second + 0.01f)
    }
    val broke = waists.first { it.first > 300.0 && it.second == 0f }.first
    assertTrue("si rompe a $broke ms", broke in 395.0..440.0)
    assertTrue(GlassLiquidNeck > 0f)
    val narrowAt333 = waists.minByOrNull { abs(it.first - 333.0) }!!.second
    assertTrue("a 333 ms la vita è stretta come in Apple (0,54–0,59 H): $narrowAt333", narrowAt333 < 0.75f)
  }
}
