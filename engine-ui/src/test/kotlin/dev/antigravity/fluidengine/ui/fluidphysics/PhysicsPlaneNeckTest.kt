package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidNeck
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneJourney
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Il collo dei viaggi del piano flottante (F3.3): l'orologio [PhysicsPlaneNeck] sul tempo del viaggio di
 * `GlassPlaneJourney`, e la distanza come seconda coordinata del collo ([physicsNeckReachCoordinate]).
 *
 * Le geometrie sono quelle delle tracce di F3.3: due tasti da 44 dp (densità 3) che si dividono e si riuniscono a 8 dp
 * (la distanza fra due vetri di un gruppo) e a 40 dp (la geometria di F3.1), fusione di serie ([PhysicsPlaneFusionDp]).
 * Il campo è lo specchio Kotlin di `glassField`, campionato a 120 Hz.
 */
class PhysicsPlaneNeckTest {

  private val d = 3f
  private val nominal = PhysicsPlaneFusionDp * d

  private fun piece(id: String, x: Float) = GlassPlanePiece(
    key = id, id = id, bounds = Rect(x * d, 58f * d, (x + 44f) * d, 102f * d), radii = FluidCornerRadii.all(22f * d),
  )

  private fun state(vararg pieces: GlassPlanePiece) =
    GlassPlaneState(pieces.toList(), 12f * d, Rect(0f, 0f, 360f * d, 160f * d))

  private class Sample(val ms: Double, val connected: Boolean, val waist: Float, val gap: Float, val blend: Float)

  private fun journey(from: GlassPlaneState, to: GlassPlaneState): Pair<List<Sample>, Double> {
    val plane = GlassPlaneJourney(from, to)
    val neck = PhysicsPlaneNeck(-1f, plane.durationMs)
    val out = ArrayList<Sample>()
    var t = 0.0
    while (t <= plane.durationMs + 20.0) {
      val frame = plane.frame(t)
      val slabs = frame.pieces.map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
      val plan = PhysicsRenderPlan()
      buildRestPlan(plan, FluidForm.Group(slabs, nominal), physicsNeckEncode(neck.at(t)))
      out += measure(t, physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces)))
      t += 1000.0 / 120.0
    }
    return out to plane.durationMs
  }

  private fun measure(ms: Double, f: GlassFieldPlan): Sample {
    val ax = minOf(f.rects[0], f.rects[4])
    val bx = maxOf(f.rects[0], f.rects[4])
    val cy = f.rects[1]
    val mid = (ax + bx) / 2f
    val gap = abs(bx - ax) - (f.rects[2] + f.rects[6])
    fun inside(x: Float, y: Float) = GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, x, y) < 0f
    val connected = inside(mid, cy)
    fun height(x: Float): Float {
      if (!inside(x, cy)) return 0f
      var lo = 0f
      var hi = f.rects[3] * 1.6f
      repeat(32) {
        val m = (lo + hi) / 2f
        if (inside(x, cy - m)) lo = m else hi = m
      }
      return lo
    }
    val lobe = minOf(height(ax), height(bx))
    return Sample(ms, connected, if (lobe > 0f) height(mid) / lobe else 0f, gap, f.blend[1])
  }

  private fun changes(samples: List<Sample>) = samples.zipWithNext().count { (a, b) -> a.connected != b.connected }

  @Test
  fun aSplitBetweenNeighboursThinsAndBreaksOnceAbout200msBeforeRest() {
    val (samples, duration) = journey(state(piece("a", 158f)), state(piece("a", 132f), piece("b", 184f)))
    assertTrue("il piano dura ${duration} ms", duration in 280.0..320.0)
    assertEquals("si rompe una volta sola", 1, changes(samples))
    val broke = samples.first { !it.connected }.ms
    // R4 B2 (d): ≈200 ms prima del riposo (fiducia media). Con la rottura di serie (0,38) cade a ~114 ms di 300.
    assertTrue("si rompe a $broke ms", broke in 95.0..135.0)
    assertTrue("si rompe ${duration - broke} ms prima del riposo", duration - broke in 150.0..215.0)
    // Prima di rompersi la vita si assottiglia: c'è un collo visibile (sotto il 95% del lobo) per almeno 40 ms.
    val neck = samples.count { it.connected && it.waist > 0f && it.waist < 0.95f } * 1000.0 / 120.0
    assertTrue("collo visibile per $neck ms", neck >= 40.0)
    assertTrue("a riposo i pezzi sono separati", !samples.last().connected)
    assertEquals("a riposo nessun raccordo", 0f, samples.last().blend, 0f)
  }

  @Test
  fun aSplitThatTheGeometryPullsApartPinchesAndRetractsWithoutFrozenTips() {
    val (samples, _) = journey(state(piece("a", 144f)), state(piece("a", 112f), piece("b", 196f)))
    assertEquals("si rompe una volta sola", 1, changes(samples))
    val after = samples.dropWhile { it.connected }
    // Dopo la rottura il raccordo non fa più ponte e si ritira: le punte non crescono con la distanza.
    after.forEach { assertTrue("t=${it.ms}: raccordo ${it.blend} contro varco ${it.gap}", it.blend <= 2f * it.gap + 0.01f) }
    after.zipWithNext().forEach { (a, b) ->
      if (b.gap > a.gap) assertTrue("t=${b.ms}: il raccordo risale ${a.blend} -> ${b.blend}", b.blend <= a.blend + 0.5f)
    }
    assertEquals(0f, after.last { it.gap >= PhysicsNeckReach * nominal }.blend, 0f)
  }

  @Test
  fun aReunionBridgesBeforeContactAndJoinsOnce() {
    val (samples, _) = journey(state(piece("a", 132f), piece("b", 184f)), state(piece("a", 158f)))
    assertTrue("alla partenza i pezzi sono separati", !samples.first().connected)
    assertEquals("un'unione sola", 1, changes(samples))
    assertTrue("un ponte prima del contatto", samples.any { it.connected && it.gap > 0f })
  }

  @Test
  fun anInterruptedPlaneJourneyRestartsFromTheNeckItHad() {
    val first = PhysicsPlaneNeck(-1f, 300.0)
    for (at in listOf(0.0, 20.0, 60.0, 110.0, 150.0, 400.0)) {
      val next = first.retarget(at, 280.0)
      assertEquals("a $at ms", first.at(at), next.at(0.0), 1e-6f)
      assertEquals(-1f, next.at(280.0), 0f)
    }
    assertEquals(GlassLiquidNeck, first.breakAt, 0f)
    assertEquals("un viaggio senza tempo è a riposo", -1f, PhysicsPlaneNeck(-1f, 0.0).at(10.0), 0f)
  }

  @Test
  fun theDistanceIsASecondNeckCoordinate() {
    val r = 66f
    val ends = 2f * r
    for (neck in listOf(-1f, -0.4f, 0f, 0.3f, 0.7f, 1f)) {
      // Fino a un raggio di fusione di varco il collo è quello dell'orologio: la distanza non lo tocca.
      for (gap in listOf(0.5f, 10f, nominal)) {
        assertEquals("nu=$neck gap=$gap", physicsNeckPairBlend(-gap, ends, 1e6f, neck),
          physicsNeckPairBlend(-gap, ends, nominal, neck), 1e-3f)
      }
      // Oltre il doppio non arriva.
      assertEquals(0f, physicsNeckPairBlend(-PhysicsNeckReach * nominal - 1f, ends, nominal, neck), 0f)
    }
    // Con l'orologio pieno, la distanza rompe il collo a un raggio di fusione e mezzo: prima ponte, dopo no.
    assertTrue(physicsNeckPairBlend(-1.4f * nominal, ends, nominal, 1f) > 2f * 1.4f * nominal)
    assertTrue(physicsNeckPairBlend(-1.6f * nominal, ends, nominal, 1f) < 2f * 1.6f * nominal)
    // Ed è continua nel varco.
    var previous = physicsNeckPairBlend(-0.01f, ends, nominal, 1f)
    var gap = 0.01f
    while (gap < PhysicsNeckReach * nominal + 5f) {
      val now = physicsNeckPairBlend(-gap, ends, nominal, 1f)
      assertTrue("gap=$gap: $previous -> $now", abs(now - previous) < 3f)
      previous = now
      gap += 0.25f
    }
  }

  @Test
  fun pastTheGeometricLimitTheBlendRetractsInsteadOfGrowing() {
    val r = 66f
    val ends = 2f * r
    var previous = Float.POSITIVE_INFINITY
    var gap = 1.3f * r
    var bridged = true
    while (gap < 3f * r) {
      // Una distanza di fusione enorme: decide solo la geometria.
      val k = physicsNeckPairBlend(-gap, ends, 1e6f, 1f)
      if (k <= 2f * gap) {
        bridged = false
        assertTrue("gap=${gap / r} r: $previous -> $k", k <= previous + 1e-3f)
        previous = k
      } else {
        assertTrue("una volta rotto non torna a fare ponte (gap=${gap / r} r)", bridged)
      }
      gap += 0.02f * r
    }
    assertEquals(0f, physicsNeckPairBlend(-3f * r, ends, 1e6f, 1f), 0f)
    assertTrue(max(previous, 0f) == 0f)
  }

  @Test
  fun theTravelMaterialFollowsTheSpeedOfThePlaneAndNeverJumps() {
    assertEquals("da fermi", 0f, physicsPlaneMotion(0f, 500f), 0f)
    assertEquals("alla partenza", 0f, physicsPlaneMotion(800f, 0f), 0f)
    assertEquals("in corsa", 1f, physicsPlaneMotion(PhysicsPlaneMovingDpPerSecond, 200f), 0f)
    assertEquals(0f, physicsPlaneMotion(Float.NaN, 10f), 0f)
    // Lungo un viaggio vero del piano: continuo, e a zero quando la sagoma si posa.
    val plane = GlassPlaneJourney(state(piece("a", 158f)), state(piece("a", 132f), piece("b", 184f)))
    var previous = 0f
    var t = 0.0
    while (t <= plane.durationMs) {
      val speed = plane.frame(t).pieces.maxOf { p ->
        val v = p.velocity.bounds
        maxOf(maxOf(abs(v.left), abs(v.right)), maxOf(abs(v.top), abs(v.bottom)))
      } / d
      val now = physicsPlaneMotion(speed, t.toFloat())
      assertTrue("t=$t: $previous -> $now", abs(now - previous) < 0.35f)
      previous = now
      t += 1000.0 / 120.0
    }
    assertEquals(0f, previous, 0.01f)
  }

  @Test
  fun theTasteVariantsChangeOnlyHowTheWaistThins() {
    val r = 66f
    val ends = 2f * r
    val gap = 0.36f * r
    for (pinch in listOf(0.5f, PhysicsNeckPinchExponent, 1f)) {
      // Pieno e rotto sono gli stessi per ogni variante: cambia solo la strada fra i due.
      assertEquals(physicsNeckPairBlend(-gap, ends, nominal, 1f), physicsNeckPairBlend(-gap, ends, nominal, 1f, pinch), 1e-3f)
      assertEquals(2f * gap, physicsNeckPairBlend(-gap, ends, nominal, 0f, pinch), 1e-3f)
    }
    val thick = physicsNeckPairBlend(-gap, ends, nominal, 0.2f, 0.5f)
    val thin = physicsNeckPairBlend(-gap, ends, nominal, 0.2f, 1f)
    assertTrue("a metà strada lo spesso è più spesso: $thick contro $thin", thick > thin)
  }
}
