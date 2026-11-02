package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La goccia nel piano di resa (3.0): finche' e' posata il piano e' quello di sempre, al bit; mentre
 * viaggia e' l'anello della goccia nello shader poligonale, col contenuto posato sulla sagoma a riposo
 * spostata con lei; quando si posa torna la lastra. E il conducente non chiede fotogrammi a riposo.
 */
class DropPlanTest {

  private val button = FluidForm.Slab(Rect(90f, 296f, 190f, 340f), FluidCornerRadii.all(22f))
  private val panel = FluidForm.Slab(Rect(16f, 72f, 268f, 284f), FluidCornerRadii.all(26f))
  private val frame = 16_666_667L

  private fun liquidState(initial: FluidForm = button): FluidPhysicsState =
    FluidPhysicsState(initial).apply {
      liquidAllowed = true
      maskInShader = true
      density = 2.625f
    }

  /** Un passo della goccia con la sagoma a riposo e l'ancora che lo stato da' adesso. */
  private fun FluidPhysicsState.feed(time: Long) {
    val slab = dropRestSlabNow()!!
    val rest = FloatArray(liquid.points * 2)
    sampleRestContour(slab, liquid.points, rest)
    val a = dropAnchorNow()
    liquid.step(time, rest, a.x, a.y, FluidMotion.DropAnchorReachDp * density, density)
  }

  @Test
  fun aPosedDropDrawsThePlanOfBefore() {
    // Il primo fotogramma del viaggio: la goccia e' posata, il piano e' la lastra del lerp.
    val state = liquidState()
    state.driveExternally(button, panel, progress = { 0f }, startFrame = null)
    val plan = state.ensurePlan()
    assertEquals(PlanModeSlabs, plan.mode)
    assertEquals(1, plan.pieceCount)
    assertEquals(button.frame.center.y, plan.pieceRects[1], 0.001f)
    assertNull(plan.dropRest)
  }

  @Test
  fun aTravellingDropIsAFreeRingWithTheContentOnItsRestShape() {
    var t = 0f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null)
    for (i in 0..6) {
      t = i / 10f
      state.feed(i * frame)
    }
    val plan = state.ensurePlan()
    assertEquals(PlanModePoly, plan.mode)
    assertEquals(DropRenderPoints, plan.vertCount)
    assertEquals(0f, plan.soften, 0f)
    assertNotNull(plan.dropRest)
    // Il contenuto sta sulla sagoma a riposo spostata dello scarto del baricentro della goccia.
    val rest = state.dropRestSlabNow()!!
    assertEquals(rest.frame.left + state.liquid.offsetX, plan.contentBounds.left, 0.001f)
    assertEquals(rest.frame.top + state.liquid.offsetY, plan.contentBounds.top, 0.001f)
    // E si ritaglia sul suo rettangolo arrotondato, nelle coordinate del pannello.
    val clip = state.travelClipIn(panel.frame, t)
    assertNotNull(clip)
    assertEquals(plan.contentBounds.left - panel.frame.left, clip!!.left, 0.001f)
    assertNull(state.travelClipIn(panel.frame, 1f))
    // Bordo e ombra non sono "di una lastra" mentre la goccia viaggia.
    assertNull(state.restFormNow())
  }

  @Test
  fun aSettledDropGivesBackTheExactSlab() {
    var t = 0f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null)
    var i = 0
    while (i < 400) {
      t = (i / 20f).coerceAtMost(1f)
      state.feed(i * frame)
      if (i > 20 && state.liquid.quiet) break
      i++
    }
    assertTrue("la goccia non si e' posata", state.liquid.quiet)
    val plan = state.ensurePlan()
    assertEquals(PlanModeSlabs, plan.mode)
    assertEquals(panel.frame.left, plan.pieceRects[0] - plan.pieceRects[2], 0.001f)
    assertNotNull(state.restFormNow())
  }

  @Test
  fun aJourneyStoppedHalfwayFreezesOnTheSlabOfThatInstant() {
    // L'annullo a meta': il chiamante ferma la sua molla a 0,5. La goccia si posa sulla lastra di
    // quell'istante e il piano e' quella lastra al bit, la stessa che disegnava la geometria: niente
    // salto all'arrivo ne' alla partenza.
    var t = 0f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null)
    var i = 0
    var deformed = 0f
    while (i < 400) {
      t = (i / 20f).coerceAtMost(0.5f)
      state.feed(i * frame)
      for (k in 0 until state.liquid.points) deformed = maxOf(deformed, abs(state.liquid.displacementY[k]))
      if (i > 10 && state.liquid.quiet) break
      i++
    }
    assertTrue("la goccia non si e' deformata: $deformed", deformed > 5f)
    assertTrue("la goccia non si e' posata", state.liquid.quiet)
    val plan = state.ensurePlan()
    assertEquals(PlanModeSlabs, plan.mode)
    assertNull(plan.dropRest)
    val half = dropRestSlab(button, panel, 0.5f, 0f, 0f).frame
    assertEquals(half.center.x, plan.pieceRects[0], 0.001f)
    assertEquals(half.center.y, plan.pieceRects[1], 0.001f)
    assertEquals(half.width / 2f, plan.pieceRects[2], 0.001f)
    assertEquals(half.height / 2f, plan.pieceRects[3], 0.001f)
    // Il contenuto torna sul riquadro della lastra: lo scarto del baricentro e' zero esatto.
    assertEquals(0f, state.liquid.offsetX, 0f)
    assertEquals(0f, state.liquid.offsetY, 0f)
  }

  @Test
  fun aMorphSettlesOnItsRestPlanInstance() = runTest {
    val state = liquidState()
    // Prima del viaggio il piano di riposo e' un'istanza; dopo il viaggio e la goccia posata, a forma
    // ferma, e' di nuovo stabile.
    state.snapTo(panel)
    val before = state.ensurePlan()
    assertSame(before, state.ensurePlan())
  }

  @Test
  fun withoutTheLiquidThereIsNoDrop() {
    var t = 0f
    val state = liquidState()
    state.driveExternally(button, panel, progress = { t }, startFrame = null)
    for (i in 0..4) {
      t = i / 10f
      state.feed(i * frame)
    }
    assertEquals(PlanModePoly, state.ensurePlan().mode)
    // Le animazioni ridotte (o l'interruttore della galleria): la goccia si posa di colpo.
    state.liquidAllowed = false
    assertTrue(state.liquid.quiet)
    assertEquals(PlanModeSlabs, state.ensurePlan().mode)
  }

  @Test
  fun theAnchorIsTheCentreOfTheStart() {
    val state = liquidState()
    state.driveExternally(button, panel, progress = { 0.3f }, startFrame = null)
    assertEquals(button.frame.center, state.dropAnchorNow())
    val live = Rect(80f, 290f, 200f, 346f)
    state.driveExternally(button, panel, progress = { 0.3f }, startFrame = { live })
    assertEquals(live.center, state.dropAnchorNow())
  }

  // --- Il conducente ---------------------------------------------------------------------------

  /** Un fotogramma ogni 16 ms di tempo virtuale, contando quanti ne vengono chiesti. */
  private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
    var requested = 0
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      requested++
      delay(16L)
      return onFrame(scheduler.currentTime * 1_000_000L)
    }
  }

  @Test
  fun theDriverDeformsTheJourneySettlesToTheSlabAndThenAsksForNothing() = runTest {
    val state = liquidState()
    val clock = Frames(testScheduler)
    val progress = mutableFloatStateOf(0f)
    state.driveExternally(button, panel, progress = { progress.floatValue }, startFrame = null)
    val driver = launch(clock) { state.runLiquid() }
    runCurrent()
    // A riposo il conducente aspetta la sagoma: nessun fotogramma chiesto (D9).
    advanceTimeBy(500L)
    runCurrent()
    assertEquals(0, clock.requested)

    var maxTail = 0f
    val mover = launch {
      for (i in 1..30) {
        progress.floatValue = (i / 24f).coerceAtMost(1f)
        Snapshot.sendApplyNotifications()
        delay(16L)
        for (k in 0 until state.liquid.points) maxTail = maxOf(maxTail, abs(state.liquid.displacementY[k]))
      }
    }
    advanceTimeBy(600L)
    runCurrent()
    assertTrue("il viaggio non ha lasciato indietro la coda: $maxTail", maxTail > 5f)

    // Arrivata: la goccia si posa a zero esatto, il piano torna la lastra e il conducente si ferma.
    advanceTimeBy(3000L)
    runCurrent()
    assertTrue(state.liquid.quiet)
    assertEquals(PlanModeSlabs, state.ensurePlan().mode)
    val settled = clock.requested
    advanceTimeBy(1000L)
    runCurrent()
    assertEquals(settled, clock.requested)
    mover.cancel()
    driver.cancel()
  }

  private class Scale(override val scaleFactor: Float) : MotionDurationScale

  /** Lo scarto massimo raggiunto dalla goccia in un viaggio di 30 fotogrammi, con la scala [scale]. */
  private fun kotlinx.coroutines.test.TestScope.tailWith(scale: Float): Float {
    val state = liquidState()
    val clock = Frames(testScheduler)
    val progress = mutableFloatStateOf(0f)
    state.driveExternally(button, panel, progress = { progress.floatValue }, startFrame = null)
    val driver = launch(clock + Scale(scale)) { state.runLiquid() }
    runCurrent()
    var maxTail = 0f
    val mover = launch {
      for (i in 1..30) {
        progress.floatValue = (i / 24f).coerceAtMost(1f)
        Snapshot.sendApplyNotifications()
        delay(16L)
        for (k in 0 until state.liquid.points) maxTail = maxOf(maxTail, abs(state.liquid.displacementY[k]))
      }
    }
    advanceTimeBy(600L)
    runCurrent()
    mover.cancel()
    driver.cancel()
    return maxTail
  }

  @Test
  fun theDropRunsOnTheTimeOfTheAnimations() = runTest {
    // Il viaggio qui e' lo stesso a ogni scala (lo muove il test): con la scala 5 la goccia vive un
    // quinto del tempo per fotogramma e resta piu' indietro; con la scala zero non c'e' goccia.
    val normal = tailWith(1f)
    val slow = tailWith(5f)
    assertTrue("al rallentatore la coda non e' rimasta piu' indietro: $slow contro $normal", slow > normal * 1.2f)
    assertEquals(0f, tailWith(0f), 0f)
  }
}
