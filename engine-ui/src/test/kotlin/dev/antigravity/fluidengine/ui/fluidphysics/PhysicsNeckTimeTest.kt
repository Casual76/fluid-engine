package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldGeometry
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldMaxPieces
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldPlan
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidNeck
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Un fotogramma ogni 8 ms di tempo virtuale (~120 Hz). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
private class NeckFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(8L)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

/**
 * Il collo che dura (F1B.9): il collo fra due pezzi dei viaggi di `morphTo` vive per un tempo suo, misurato sul tempo
 * che manca alla posa della molla, e non sul progresso (che la molla di serie consuma in ~160 ms su ~660).
 *
 * Le geometrie sono quelle dei due viaggi d'oro sul palco della galleria sul tablet (2,125 px per dp): (a) il cerchio
 * del playground che diventa due cerchi, (c) il menu della dimostrazione pratica che torna due tasti, e i loro
 * contrari. La molla è quella di serie, campionata a 120 Hz con le formule di Compose; il campo è lo specchio Kotlin di
 * `glassField`.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PhysicsNeckTimeTest {

  private val spring = FloatSpringSpec(FluidMotion.MenuOpenDamping, FluidMotion.MenuOpenStiffness, JourneyVisibilityThreshold)
  private val frameNanos = 1_000_000_000L / 120L

  // (a): il cerchio del playground (palco quadrato di 1530 px) e i due cerchi del preset «Due pezzi».
  private val stageA = 1530f
  private val big = FluidForm.circle(Offset(stageA / 2f, stageA / 2f), stageA * 0.30f)
  private val left = FluidForm.circle(Offset(stageA / 2f - stageA * 0.18f, stageA / 2f), stageA * 0.15f)
  private val right = FluidForm.circle(Offset(stageA / 2f + stageA * 0.18f, stageA / 2f), stageA * 0.15f)
  private val blendA = stageA * 0.12f

  // (c): i due tasti e il menu della dimostrazione pratica (palco 1530 x 1224 px).
  private val w = 1530f
  private val h = 1224f
  private val keyLeft: FluidForm.Slab
  private val keyRight: FluidForm.Slab
  private val menu: FluidForm.Slab
  private val blendC = minOf(w, h) * 0.11f

  init {
    val bw = w * 0.34f
    val bh = h * 0.17f
    val gap = w * 0.07f
    val top = h * 0.5f - bh / 2f
    keyLeft = FluidForm.Slab(Rect(w / 2f - gap / 2f - bw, top, w / 2f - gap / 2f, top + bh), FluidCornerRadii.all(bh / 2f))
    keyRight = FluidForm.Slab(Rect(w / 2f + gap / 2f, top, w / 2f + gap / 2f + bw, top + bh), FluidCornerRadii.all(bh / 2f))
    val mw = w * 0.58f
    val mh = h * 0.62f
    menu = FluidForm.Slab(Rect(w / 2f - mw / 2f, h * 0.48f - mh / 2f, w / 2f + mw / 2f, h * 0.48f + mh / 2f), FluidCornerRadii.all(22f * 2.125f))
  }

  /** Un fotogramma del viaggio: istante, se i due pezzi sono uniti, vita/lobo, varco con segno, raccordo. */
  private class Sample(val ms: Float, val connected: Boolean, val waist: Float, val gap: Float, val blend: Float)

  /** Il viaggio da [pairs] con la molla di serie partita da ferma, fino alla posa, un campione per fotogramma. */
  private fun journey(pairs: List<Pair<FluidForm.Slab, FluidForm.Slab>>, blend: Float, breakAt: Float = GlassLiquidNeck): List<Sample> {
    val clock = PhysicsNeckClock.of(FluidPhysicsJourney.Spring, 0f)
    val total = spring.getDurationNanos(0f, 1f, 0f)
    val plan = PhysicsRenderPlan()
    val field = GlassFieldPlan(GlassFieldMaxPieces)
    val out = ArrayList<Sample>()
    var t = 0L
    while (t <= total + frameNanos) {
      val p = if (t >= total) 1f else spring.getValueFromNanos(t, 0f, 1f, 0f)
      val v = if (t >= total) 0f else spring.getVelocityFromNanos(t, 0f, 1f, 0f)
      val neck = physicsNeckAt(clock.elapsed(p, v), -1f, breakAt)
      // L'oltrepasso qui non gonfia il traguardo: la geometria resta quella d'arrivo, il collo segue il suo orologio.
      buildSlabPlan(plan, pairs, blend, p.coerceIn(0f, 1f), fusion = physicsNeckEncode(neck))
      physicsFieldPlan(plan, 1f, field)
      out += measure(t / 1e6f, field)
      t += frameNanos
    }
    return out
  }

  private fun measure(ms: Float, f: GlassFieldPlan): Sample {
    val ax = f.rects[0]
    val bx = f.rects[4]
    val cy = f.rects[1]
    val mid = (ax + bx) / 2f
    val gap = abs(bx - ax) - (f.rects[2] + f.rects[6])
    val connected = GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, mid, cy) < 0f
    val reach = max(f.rects[3], f.rects[7]) * 1.6f
    fun height(x: Float): Float {
      if (GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, x, cy) >= 0f) return 0f
      var inside = 0f
      var outside = reach
      repeat(36) {
        val m = (inside + outside) / 2f
        if (GlassFieldGeometry.fieldDistance(f.count, f.rects, f.radii, f.blend, f.smooth, x, cy - m) < 0f) inside = m else outside = m
      }
      return inside
    }
    val lobe = minOf(height(ax), height(bx))
    val waist = if (lobe > 0f) height(mid) / lobe else 0f
    return Sample(ms, connected, waist, gap, f.blend[1])
  }

  /** Quanti ms il collo si vede: fotogrammi coi pezzi uniti e una vita sotto il 95% del lobo, a centri distinti. */
  private fun neckMillis(samples: List<Sample>): Float =
    samples.count { it.connected && it.waist < 0.95f && it.waist > 0f } * 1000f / 120f

  /** Quante volte i due pezzi passano da uniti a staccati o al contrario. */
  private fun topologyChanges(samples: List<Sample>): Int = samples.zipWithNext().count { (a, b) -> a.connected != b.connected }

  @Test
  fun aSplitHasANeckThatLastsAtLeast75msAndBreaksOnce() {
    for ((name, pairs, blend) in listOf(
      Triple("(a) un tasto che diventa due", listOf(big to left, big to right), blendA),
      Triple("(c) il menu che torna due tasti", listOf(menu to keyLeft, menu to keyRight), blendC),
    )) {
      val samples = journey(pairs, blend)
      val neck = neckMillis(samples)
      assertTrue("$name: il collo si vede $neck ms", neck >= 75f)
      assertEquals("$name: il collo si rompe una volta sola", 1, topologyChanges(samples))
      val last = samples.last()
      assertTrue("$name: a riposo i pezzi sono separati", !last.connected)
      assertEquals("$name: a riposo nessun raccordo", 0f, last.blend, 0f)
      // Si rompe dopo che i pezzi sono arrivati, non quando la geometria li stacca.
      val brokeAt = samples.first { !it.connected && it.ms > 20f }.ms
      assertTrue("$name: si rompe a $brokeAt ms", brokeAt > 180f && brokeAt < 290f)
    }
  }

  @Test
  fun aUnionBridgesBeforeTheFirstContactAndJoinsOnce() {
    for ((name, pairs, blend) in listOf(
      Triple("(a) due tasti che diventano uno", listOf(left to big, right to big), blendA),
      Triple("(c) due tasti che diventano un menu", listOf(keyLeft to menu, keyRight to menu), blendC),
    )) {
      val samples = journey(pairs, blend)
      assertTrue("$name: alla partenza i pezzi sono separati", !samples.first().connected)
      assertEquals("$name: un'unione sola", 1, topologyChanges(samples))
      // Niente V al primo contatto: il ponte c'è già quando i due pezzi sono ancora staccati.
      assertTrue("$name: nessun ponte prima del contatto", samples.any { it.connected && it.gap > 0f })
    }
  }

  @Test
  fun theLawStartsWhereItWasIsMonotoneOnceRisenAndIsMinusOneAtRest() {
    for (breakAt in listOf(0.05f, 0.2f, GlassLiquidNeck, 0.38f, 0.5f)) {
      for (start in listOf(-1f, -0.4f, 0f, 0.35f, 1f)) {
        // Un viaggio interrotto o invertito riparte dal collo che c'era.
        assertEquals("b=$breakAt s=$start", start, physicsNeckAt(0f, start, breakAt), 1e-6f)
        assertEquals("b=$breakAt s=$start", -1f, physicsNeckAt(1f, start, breakAt), 0f)
        var previous = physicsNeckAt(0f, start, breakAt)
        var x = 0.001f
        while (x <= 1f) {
          val now = physicsNeckAt(x, start, breakAt)
          assertTrue("b=$breakAt s=$start x=$x: $previous -> $now", abs(now - previous) < 0.08f)
          if (x > PhysicsNeckRise) assertTrue("b=$breakAt s=$start x=$x: risale $previous -> $now", now <= previous + 1e-6f)
          previous = now
          x += 0.001f
        }
      }
      // Da fermi il collo si rompe esattamente a breakAt, una volta sola.
      if (breakAt >= PhysicsNeckRise) assertEquals(0f, physicsNeckAt(breakAt, -1f, breakAt), 1e-4f)
    }
  }

  @Test
  fun theClockReadsTheTimeLeftFromTheSpringState() {
    val clock = PhysicsNeckClock.of(FluidPhysicsJourney.Spring, 0f)
    val total = spring.getDurationNanos(0f, 1f, 0f)
    assertTrue("la molla di serie si posa in ${total / 1e6} ms", total in 600_000_000L..720_000_000L)
    var t = 0L
    while (t < total) {
      val p = spring.getValueFromNanos(t, 0f, 1f, 0f)
      val v = spring.getVelocityFromNanos(t, 0f, 1f, 0f)
      val expected = t.toFloat() / total.toFloat()
      assertEquals("t=${t / 1e6} ms", expected, clock.elapsed(p, v), 0.01f)
      t += frameNanos
    }
    // Un viaggio che non è una molla segue il progresso.
    val tween = PhysicsNeckClock.of(FluidPhysicsJourney.House, 0f)
    assertEquals(0.4f, tween.elapsed(0.4f, 3f), 0f)
  }

  @Test
  fun thePairBlendIsZeroAtRestContinuousAtContactAndNeverSwellsTheLobes() {
    val r = 100f
    val ends = 2f * r
    for (neck in listOf(-1f, -0.5f, 0f, 0.3f, 0.7f, 1f)) {
      // Al contatto le due regole (sovrapposti, staccati) danno lo stesso raccordo.
      val over = physicsNeckPairBlend(0.01f, ends, 48f, neck)
      val apart = physicsNeckPairBlend(-0.01f, ends, 48f, neck)
      assertEquals("nu=$neck", over, apart, 0.5f)
    }
    for (depth in listOf(150f, 40f, 0f, -30f, -90f, -200f)) {
      assertEquals("a riposo (nu = -1) depth=$depth", 0f, physicsNeckPairBlend(depth, ends, 1000f, -1f), 0f)
    }
    // Pezzi che coincidono (la fine di una riunione): nessun raccordo, nessun alone.
    assertEquals(0f, physicsNeckPairBlend(ends, ends, 1000f, 1f), 0f)
    // Oltre due raggi di fusione di varco il collo non arriva.
    assertEquals(0f, physicsNeckPairBlend(-100f, ends, 48f, 1f), 0f)

    // Il collo pieno non porta mai il contorno fuori dal filo dei lobi, e il tetto non è troppo prudente.
    for (cOverR in listOf(0.5f, 0.8f, 1f, 1.2f, 1.5f)) {
      val full = physicsNeckFull(cOverR) * r
      assertTrue("c/r=$cOverR: col collo pieno il contorno sale a ${topBetween(r, cOverR, full)}", topBetween(r, cOverR, full) <= r * 1.004f)
      assertTrue("c/r=$cOverR: un raccordo più grande del 10% resta dentro", topBetween(r, cOverR, full * 1.1f) > r * 1.001f)
    }
  }

  /** Il punto più alto del contorno fra i centri di due cerchi di raggio [r] a distanza 2·[cOverR]·[r], raccordo [k]. */
  private fun topBetween(r: Float, cOverR: Float, k: Float): Float {
    val c = cOverR * r
    val rects = floatArrayOf(-c, 0f, r, r, c, 0f, r, r)
    val radii = floatArrayOf(r, r, r, r, r, r, r, r)
    val blend = floatArrayOf(0f, k)
    val smooth = floatArrayOf(0f, 0f)
    var best = 0f
    var x = 0f
    while (x <= c) {
      var inside = 0f
      var outside = 2f * r
      if (GlassFieldGeometry.fieldDistance(2, rects, radii, blend, smooth, x, 0f) < 0f) {
        repeat(36) {
          val m = (inside + outside) / 2f
          if (GlassFieldGeometry.fieldDistance(2, rects, radii, blend, smooth, x, -m) < 0f) inside = m else outside = m
        }
        best = max(best, inside)
      }
      x += c / 200f
    }
    return best
  }

  @Test
  fun anInterruptedOrInvertedJourneyRestartsFromTheNeckItHad() = runTest {
    val state = FluidPhysicsState(big)
    launch(NeckFrames(testScheduler)) { state.morphTo(FluidForm.Group(listOf(left, right), blendA)) }
    // Dentro il collo vivo: ~150 ms, i pezzi già quasi staccati.
    advanceTimeBy(150L)
    runCurrent()
    val before = state.ensurePlan().fusion
    assertTrue("in viaggio il piano porta il collo nel tempo: $before", physicsNeckIsTimed(before))
    // Invertito: torna al cerchio. Il primo piano del ritorno ha lo stesso collo.
    launch(NeckFrames(testScheduler)) { state.morphTo(big) }
    runCurrent()
    val after = state.ensurePlan().fusion
    assertEquals(physicsNeckDecode(before), physicsNeckDecode(after), 1e-4f)
    // E arriva al cerchio senza collo.
    advanceTimeBy(1500L)
    runCurrent()
    assertEquals(0f, state.ensurePlan().fusion, 0f)
  }
}
