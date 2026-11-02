package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Un fotogramma ogni 16 ms di tempo virtuale. */
private class JourneyFrames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
  override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
    delay(16L)
    return onFrame(scheduler.currentTime * 1_000_000L)
  }
}

/**
 * Il movimento della 3.0: il default di `morphTo` e' una molla sola che parte alla velocita' del
 * momento; la rincorsa della casa resta come preset esplicito; un ritargeting eredita la velocita'
 * del viaggio che interrompe.
 *
 * Il primo fotogramma di un `animateTo` fissa l'orologio (tempo di gioco zero): dopo `16 * n + 1` ms
 * di tempo virtuale la molla ha giocato `16 * (n - 1)` ms.
 */
class FluidPhysicsJourneyTest {

  private val capsule = FluidForm.Slab(Rect(100f, 600f, 300f, 644f), FluidCornerRadii.all(22f))
  private val panel = FluidForm.Slab(Rect(40f, 200f, 292f, 400f), FluidCornerRadii.all(22f))
  private val far = FluidForm.Slab(Rect(40f, 0f, 292f, 120f), FluidCornerRadii.all(22f))

  /** Il progresso di un viaggio da [from] a [to] dopo [millis] di tempo virtuale. */
  private fun progressAfter(
    from: FluidForm,
    to: FluidForm,
    millis: Long,
    spec: AnimationSpec<Float>? = null,
    initialVelocity: Float? = null,
  ): Float {
    var result = Float.NaN
    runTest {
      val state = FluidPhysicsState(from)
      val job = launch(JourneyFrames(testScheduler)) { state.morphTo(to, spec, initialVelocity) }
      advanceTimeBy(millis)
      runCurrent()
      result = state.progress
      job.cancel()
    }
    return result
  }

  @Test
  fun theDefaultLeavesAtOnceInsteadOfRunningUp() {
    // La molla nasce alla massima accelerazione; la rincorsa della casa parte piano. Dopo due
    // fotogrammi di gioco la molla di serie ha fatto molta piu' strada.
    val spring = progressAfter(capsule, panel, 49L)
    val house = progressAfter(capsule, panel, 49L, FluidPhysicsJourney.House)
    assertTrue("molla $spring, casa $house", spring > house * 2f)
    assertTrue("molla $spring", spring > 0.1f)
  }

  @Test
  fun theDefaultSpringOvershootsAndSettlesOnTheTarget() = runTest {
    val state = FluidPhysicsState(capsule)
    var peak = 0f
    val job = launch(JourneyFrames(testScheduler)) { state.morphTo(panel) }
    repeat(90) {
      advanceTimeBy(16L)
      runCurrent()
      peak = maxOf(peak, state.progress)
    }
    job.join()
    assertFalse(state.isMorphing)
    assertEquals(panel, state.form)
    // Il picco della molla del menu: ~8% oltre il traguardo, reso come gonfiore.
    assertTrue("picco $peak", peak in 1.03f..1.15f)
  }

  @Test
  fun theHousePresetStartsSlowHitsTheRunUpEndAndLandsExactly() {
    val house = HouseJourneySpec()
    val runUp = JourneyRunUpMillis * 1_000_000L
    assertEquals(0f, house.getValueFromNanos(0L, 0f, 1f, 0f), 0f)
    assertEquals(JourneyRunUpEnd, house.getValueFromNanos(runUp, 0f, 1f, 0f), 1e-3f)
    // Piano in partenza: nel primo quinto della rincorsa ha fatto pochissima strada.
    assertTrue(house.getValueFromNanos(runUp / 5, 0f, 1f, 0f) < 0.05f)
    val end = house.getDurationNanos(0f, 1f, 0f)
    assertTrue(end > runUp)
    assertEquals(1f, house.getValueFromNanos(end, 0f, 1f, 0f), 2e-3f)
  }

  @Test
  fun theHousePresetHandsItsSpeedToTheSpringWithoutAStep() {
    val house = HouseJourneySpec()
    val runUp = JourneyRunUpMillis * 1_000_000L
    val before = house.getVelocityFromNanos(runUp - 1_000_000L, 0f, 1f, 0f)
    val after = house.getVelocityFromNanos(runUp + 1_000_000L, 0f, 1f, 0f)
    assertTrue("prima $before, dopo $after", abs(before - after) < 0.25f * abs(before))
    // E la velocita' e' quella che fa il rimbalzo: la rincorsa esce ripida.
    assertTrue("prima $before", before > 4f)
    // Il valore e' continuo nel passaggio.
    val a = house.getValueFromNanos(runUp - 1_000L, 0f, 1f, 0f)
    val b = house.getValueFromNanos(runUp + 1_000L, 0f, 1f, 0f)
    assertEquals(a, b, 1e-3f)
  }

  @Test
  fun theHousePresetStillOvershootsLikeBefore() {
    val house = HouseJourneySpec()
    val end = house.getDurationNanos(0f, 1f, 0f)
    var peak = 0f
    var t = 0L
    while (t <= end) {
      peak = maxOf(peak, house.getValueFromNanos(t, 0f, 1f, 0f))
      t += 4_000_000L
    }
    assertTrue("picco $peak", peak > 1.02f && peak < 1.25f)
  }

  @Test
  fun aJourneyStillArrivesWithoutTravellingWhenMotionIsReduced() = runTest {
    val state = FluidPhysicsState(capsule)
    state.reducedMotion = true
    state.morphTo(panel)
    assertEquals(panel, state.form)
    assertFalse(state.isMorphing)
  }

  @Test
  fun aRetargetInheritsTheSpeedOfTheJourneyItInterrupts() {
    // Un viaggio verso `panel` gia' in corsa viene ridiretto verso `far`, nella stessa direzione: il
    // viaggio nuovo eredita la velocita' e dopo lo stesso tempo e' piu' avanti di uno identico
    // partito da fermo dalla stessa forma.
    var inherited = Float.NaN
    var origin: FluidForm? = null
    runTest {
      val state = FluidPhysicsState(capsule)
      val first = launch(JourneyFrames(testScheduler)) { state.morphTo(panel) }
      advanceTimeBy(65L)
      runCurrent()
      assertTrue(state.isMorphing)
      val second = launch(JourneyFrames(testScheduler)) { state.morphTo(far) }
      runCurrent()
      origin = state.journeyOriginNow()
      advanceTimeBy(33L)
      runCurrent()
      inherited = state.progress
      first.cancel()
      second.cancel()
    }
    val start = origin ?: error("il ritargeting non ha registrato la partenza")
    val fromRest = progressAfter(start, far, 33L)
    assertTrue("ereditata $inherited, da ferma $fromRest", inherited > fromRest)
  }

  @Test
  fun anExplicitVelocityWinsOverTheInheritedOne() {
    // Un gesto che lancia il pannello: la velocita' del dito, passata da chi la conosce.
    val thrown = progressAfter(capsule, panel, 33L, initialVelocity = 5f)
    val still = progressAfter(capsule, panel, 33L)
    assertTrue("lanciato $thrown, da fermo $still", thrown > still)
  }

  @Test
  fun theInheritedVelocityIsTheProjectionOnTheNewJourney() {
    val a = Rect(0f, 0f, 100f, 100f)
    val b = Rect(0f, -100f, 100f, 0f)
    // Stessa direzione, stessa lunghezza: la stessa velocita'.
    assertEquals(2f, inheritedProgressVelocity(a, b, 2f, a, b), 1e-5f)
    // Stessa direzione, strada lunga la meta': il doppio dei viaggi al secondo.
    val half = Rect(0f, -50f, 100f, 50f)
    assertEquals(4f, inheritedProgressVelocity(a, b, 2f, a, half), 1e-5f)
    // Direzione rovesciata: all'indietro, e la sagoma prosegue un istante per la sua strada.
    assertEquals(-2f, inheritedProgressVelocity(a, b, 2f, b, a), 1e-5f)
    // Ortogonale: niente da ereditare.
    val sideways = Rect(100f, 0f, 200f, 100f)
    assertEquals(0f, inheritedProgressVelocity(a, b, 2f, a, sideways), 1e-5f)
  }

  @Test
  fun theInheritedVelocityIsBoundedAndSafe() {
    val a = Rect(0f, 0f, 100f, 100f)
    val b = Rect(0f, -400f, 100f, -300f)
    val tiny = Rect(0f, -2f, 100f, 98f)
    // Una strada nuova cortissima trasformerebbe la velocita' in un lancio: tenuta al tetto.
    assertEquals(MaxInheritedVelocity, inheritedProgressVelocity(a, b, 3f, a, tiny), 0f)
    assertEquals(MinInheritedVelocity, inheritedProgressVelocity(a, b, -3f, a, tiny), 0f)
    // Un viaggio nuovo piu' corto di un pixel non ha direzione.
    assertEquals(0f, inheritedProgressVelocity(a, b, 3f, a, a), 0f)
    assertEquals(0f, inheritedProgressVelocity(a, b, Float.NaN, a, b), 0f)
    assertEquals(0f, inheritedProgressVelocity(a, b, 0f, a, b), 0f)
  }

  @Test
  fun theRestingSlabIsKnownOnlyAtRest() {
    val state = FluidPhysicsState(capsule)
    assertEquals(capsule, state.restSlabNow())
    assertEquals(null, state.journeyTargetNow())
    val group = FluidForm.Group(listOf(capsule, panel), blendRadius = 20f)
    state.snapTo(group)
    // Un gruppo fermo e' fermo, ma non e' una lastra: resta sul campo di distanza.
    assertEquals(null, state.restSlabNow())
    assertEquals(group, state.restFormNow())
  }
}
