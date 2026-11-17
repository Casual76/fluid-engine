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

  /**
   * Un viaggio come lo guida il contenitore (F3.2c): la forma del piano di ogni fotogramma ([PhysicsPlaneShape]) col
   * collo nel tempo lungo il doppio del riposo a vista ([physicsPlaneNeckMs]) e l'esponente del piano ([PhysicsPlaneNeckPinch]),
   * nel piano di [buildPlanePlan]. Restituisce i campioni e il riposo a vista.
   */
  private fun journey(from: GlassPlaneState, to: GlassPlaneState): Pair<List<Sample>, Double> {
    val plane = GlassPlaneJourney(from, to)
    val neck = PhysicsPlaneNeck(-1f, physicsPlaneNeckMs(plane.settleMs))
    val out = ArrayList<Sample>()
    var t = 0.0
    while (t <= plane.durationMs + 20.0) {
      val frame = plane.frame(t)
      val slabs = frame.pieces.map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
      val plan = PhysicsRenderPlan()
      buildPlanePlan(plan, PhysicsPlaneShape(slabs, nominal, neck = neck.at(t)))
      out += measure(t, physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces)))
      t += 1000.0 / 120.0
    }
    return out to plane.settleMs
  }

  /** Quanto dura, a 120 Hz, il collo visibile di una riunione: unito e con la vita sotto il 95% del lobo. */
  private fun reunionNeckMs(samples: List<Sample>): Double =
    samples.count { it.connected && it.waist > 0f && it.waist < 0.95f } * 1000.0 / 120.0

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
    // F3.12a e F3.12c: una divisione è una nascita, coi tempi di Apple a tempo vero (scelta di Alessio); il riposo a
    // vista è il ritorno del superamento di chi è nato, sull'orologio corretto (540 ms), e dopo viene la coda. Fino a
    // F3.12a era 500, fino a F3.11 300.
    assertTrue("il piano dura ${duration} ms", duration in 525.0..555.0)
    assertEquals("si rompe una volta sola", 1, changes(samples))
    val broke = samples.first { !it.connected }.ms
    // La rottura di serie (0,38, la scelta di Alessio: non cambia) sull'orologio lungo il doppio del riposo a vista cade
    // a 0,76 del viaggio: ~410 ms su 540, dopo che i pezzi si sono staccati e **dopo il colmo del superamento** di chi è
    // nato (~0,68), come in Apple (R4 B2 (b), orologio corretto: colmo a ~367 ms, rottura a ~433: un poco prima di Apple,
    // la scelta «com'è oggi»).
    val apart = samples.first { it.gap > 0f }.ms
    println("F3.12c divisione a 8 dp: si staccano a $apart ms, il collo si rompe a $broke ms, riposo a vista a $duration ms")
    assertTrue("si rompe a $broke ms", broke in 380.0..440.0)
    assertTrue("si rompe dopo che si staccano ($apart ms)", broke > apart + 20.0)
    assertTrue("si rompe ${duration - broke} ms prima del riposo a vista", duration - broke in 90.0..150.0)
    assertTrue("si rompe a ${broke / duration} del viaggio, dopo il colmo del superamento", broke / duration in 0.72..0.82)
    // Prima di rompersi la vita si assottiglia: c'è un collo visibile (sotto il 95% del lobo) per almeno 40 ms.
    val neck = samples.count { it.connected && it.waist > 0f && it.waist < 0.95f } * 1000.0 / 120.0
    assertTrue("collo visibile per $neck ms", neck >= 40.0)
    assertTrue("a riposo i pezzi sono separati", !samples.last().connected)
    assertEquals("a riposo nessun raccordo", 0f, samples.last().blend, 0f)
  }

  @Test
  fun aDonorThatLeavesAWideGapBreaksTheNeckOnlyOnce() {
    // F3.12c, il rischio di F3.12a: chi è rilasciato supera il suo posto anche col lato di coda, quindi il varco col
    // donatore al colmo cresce; per un donatore che se ne va lasciando un varco finale fra ~19 e 24 dp il collo poteva
    // spezzarsi per la distanza, rifarsi al ritorno e rompersi di nuovo all'orologio. Qui il donatore va a sinistra e
    // chi nasce resta a destra, col varco finale da 12 a 26 dp: il collo si rompe una volta sola, e non si rifà.
    for (gap in listOf(12f, 16f, 19f, 21f, 22.5f, 24f, 26f)) {
      val (samples, _) = journey(state(piece("a", 158f)), state(piece("a", 110f), piece("b", 154f + gap)))
      val breaks = samples.zipWithNext().count { (a, b) -> a.connected && !b.connected }
      val joins = samples.zipWithNext().count { (a, b) -> !a.connected && b.connected }
      println("F3.12c varco finale $gap dp: rotture $breaks, riunioni $joins")
      assertEquals("varco $gap dp: si rompe una volta sola", 1, breaks)
      assertEquals("varco $gap dp: e non si rifà", 0, joins)
      assertTrue("varco $gap dp: a riposo i pezzi sono separati", !samples.last().connected)
    }
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
    // F3.2c: con i tempi nuovi del piano la riunione si vede (era 25–33 ms a 120 Hz in F3.3).
    println("F3.2c riunione da 8 dp: collo visibile per ${reunionNeckMs(samples)} ms a 120 Hz")
    assertTrue("collo visibile nella riunione per ${reunionNeckMs(samples)} ms", reunionNeckMs(samples) >= 40.0)
  }

  @Test
  fun thePlaneNeckIsThickAsAlessioChose() {
    // Scelta di Alessio, 2026-11-16 (tavola «quanto si strozza il collo»): «spesso», 1/2. Un collo solo per tutto
    // l'engine: lo stesso esponente per il piano flottante e per i viaggi di `morphTo` (F3.2c l'aveva messo solo nel piano).
    assertEquals(0.5f, PhysicsPlaneNeckPinch, 0f)
    assertEquals(0.5f, PhysicsNeckPinchExponent, 0f)
    val plan = PhysicsRenderPlan()
    val slabs = listOf(piece("a", 132f), piece("b", 184f)).map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
    buildPlanePlan(plan, PhysicsPlaneShape(slabs, nominal, neck = 0.2f))
    assertEquals(PhysicsPlaneNeckPinch, plan.neckPinch, 0f)
    val field = physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces))
    val gap = 8f * d
    assertEquals(physicsNeckPairBlend(-gap, 2f * 22f * d, nominal, 0.2f, 0.5f), field.blend[1], 1e-3f)
    // Un piano di `morphTo` ha lo stesso: un collo solo.
    val other = PhysicsRenderPlan()
    buildRestPlan(other, FluidForm.Group(slabs, nominal), physicsNeckEncode(0.2f))
    assertEquals(PhysicsPlaneNeckPinch, other.neckPinch, 0f)
    assertEquals(PhysicsNeckPinchExponent, other.neckPinch, 0f)
  }

  @Test
  fun aUnionIsOneShapeAtRestAndDistinctPiecesStayApart() {
    val slabs = listOf(piece("a", 132f), piece("b", 184f), piece("c", 236f))
      .map { FluidForm.Slab(it.bounds, it.radii, smoothing = 0f) }
    fun connectedAt(plan: PhysicsRenderPlan, x: Float): Boolean {
      val f = physicsFieldPlan(plan, 1f, GlassFieldPlan(GlassFieldMaxPieces))
      return GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, x * d, 80f * d) < 0f
    }
    val ab = 180f
    val bc = 232f
    // a e b della stessa unione (in testa), c distinto: a riposo a-b una sagoma, b-c separati.
    val union = PhysicsRenderPlan()
    buildPlanePlan(union, PhysicsPlaneShape(slabs, nominal, neck = -1f, unions = intArrayOf(0, 0, -1), resting = true))
    assertTrue("l'unione è una sagoma sola a riposo", connectedAt(union, ab))
    assertTrue("un pezzo distinto accanto all'unione resta staccato", !connectedAt(union, bc))
    // Senza unioni, a riposo, tutti separati (decisione dell'11 novembre).
    val apart = PhysicsRenderPlan()
    buildPlanePlan(apart, PhysicsPlaneShape(slabs, nominal, neck = -1f, resting = true))
    assertTrue(!connectedAt(apart, ab) && !connectedAt(apart, bc))
    // Un compagno d'unione che segue un pezzo estraneo più vicino non gli tira un ponte.
    val foreignFirst = PhysicsRenderPlan()
    val reordered = listOf(slabs[2], slabs[0], slabs[1])
    buildPlanePlan(foreignFirst, PhysicsPlaneShape(reordered, nominal, neck = -1f, unions = intArrayOf(-1, 0, 0), resting = true))
    assertTrue("nessun ponte verso l'estraneo", !connectedAt(foreignFirst, bc))
    // Il piano di riposo senza unioni dà gli stessi uniform di un gruppo fermo di `buildRestPlan`.
    val rest = PhysicsRenderPlan()
    buildRestPlan(rest, FluidForm.Group(slabs, 0f))
    val a1 = physicsFieldPlan(apart, 1f, GlassFieldPlan(GlassFieldMaxPieces))
    val a2 = physicsFieldPlan(rest, 1f, GlassFieldPlan(GlassFieldMaxPieces))
    assertEquals(a2.count, a1.count)
    for (i in 0 until a1.count) {
      assertEquals(a2.blend[i], a1.blend[i], 0f)
      assertEquals(a2.soft[i], a1.soft[i], 0f)
      assertEquals(a2.smooth[i], a1.smooth[i], 0f)
      for (k in 0 until 4) {
        assertEquals(a2.rects[i * 4 + k], a1.rects[i * 4 + k], 0f)
        assertEquals(a2.radii[i * 4 + k], a1.radii[i * 4 + k], 0f)
      }
    }
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
    for (pinch in listOf(0.5f, 2f / 3f, 1f)) {
      // Pieno e rotto sono gli stessi per ogni variante: cambia solo la strada fra i due.
      assertEquals(physicsNeckPairBlend(-gap, ends, nominal, 1f), physicsNeckPairBlend(-gap, ends, nominal, 1f, pinch), 1e-3f)
      assertEquals(2f * gap, physicsNeckPairBlend(-gap, ends, nominal, 0f, pinch), 1e-3f)
    }
    val thick = physicsNeckPairBlend(-gap, ends, nominal, 0.2f, 0.5f)
    val thin = physicsNeckPairBlend(-gap, ends, nominal, 0.2f, 1f)
    assertTrue("a metà strada lo spesso è più spesso: $thick contro $thin", thick > thin)
  }
}
