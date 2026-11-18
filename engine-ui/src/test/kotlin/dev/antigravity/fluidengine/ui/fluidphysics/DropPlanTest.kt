package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.translate
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
    // E si ritaglia dentro il vetro di adesso, nelle coordinate del pannello: i lati diritti del
    // ritaglio stanno dentro la goccia (mezzo pixel d'antialias), anche dove la goccia e' rimasta
    // indietro rispetto alla sagoma a riposo.
    val clip = state.travelClipIn(panel.frame, t)
    assertNotNull(clip)
    val ring = List(plan.vertCount) { androidx.compose.ui.geometry.Offset(plan.verts[it * 2], plan.verts[it * 2 + 1]) }
    val c = clip!!
    val l = c.left + panel.frame.left
    val r = c.right + panel.frame.left
    val tp = c.top + panel.frame.top
    val b = c.bottom + panel.frame.top
    val rad = c.topLeftCornerRadius.x
    for (k in 0..20) {
      val x = l + rad + (r - l - 2f * rad) * k / 20f
      val y = tp + rad + (b - tp - 2f * rad) * k / 20f
      for ((px, py) in listOf(x to tp, x to b, l to y, r to y)) {
        assertTrue("($px, $py) fuori dal vetro: ${sdPolygon(px, py, ring)}", sdPolygon(px, py, ring) <= 0.6f)
      }
    }
    // La goccia si e' allontanata dalla sagoma a riposo: il ritaglio e' piu' stretto di lei.
    assertTrue(c.height + 1f < rest.frame.height)
    // Finche' la goccia non si posa il ritaglio resta, qualunque progresso dica il chiamante (S9: la molla del menu
    // e' sopra l'1 mentre la goccia si sta ancora posando, e senza ritaglio le voci uscivano dal vetro).
    assertEquals(c, state.travelClipIn(panel.frame, 1f))
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
    // Arrivata e posata: il corpo contiene il contenuto, niente ritaglio.
    assertNull(state.travelClipIn(panel.frame, 1f))
  }

  @Test
  fun aDropStillSettlingAfterArrivalKeepsItsContentInsideTheGlass() {
    // Il caso di S9 (il contestuale a +243 ms): la molla del menu (`dropOpen`) passa l'1 a ~150 ms e ci resta fino a
    // ~355 ms, mentre la goccia, che arriva dopo la molla, e' ancora piu' stretta della sagoma a riposo. Il contenuto e'
    // posato sulla sagoma a riposo: in quei fotogrammi deve restare ritagliato dentro il vetro di adesso, angoli compresi.
    val spring = TargetBasedAnimation(FluidMotion.dropOpen<Float>(), Float.VectorConverter, 0f, 1f, AnimationVector1D(0f))
    var t = 0f
    val state = liquidState()
    state.driveExternally(
      button, panel, progress = { t },
      overshootInflationX = FluidMotion.DropInflationGain,
      overshootInflationY = FluidMotion.DropInflationGain,
      startFrame = null,
    )
    var settlingAfterArrival = 0
    var receded = 0f
    var worst = -Float.MAX_VALUE
    var i = 0
    while (i < 400) {
      val nanos = i * frame
      t = if (spring.isFinishedFromNanos(nanos)) 1f else spring.getValueFromNanos(nanos)
      state.feed(nanos)
      val plan = state.ensurePlan()
      if (t >= 1f && !state.liquid.quiet && plan.mode == PlanModePoly) {
        settlingAfterArrival++
        val ring = List(plan.vertCount) { androidx.compose.ui.geometry.Offset(plan.verts[it * 2], plan.verts[it * 2 + 1]) }
        // Senza ritaglio: quanto i lati diritti della sagoma su cui posa il contenuto escono dalla goccia.
        val content = plan.contentBounds
        val r = panel.cornerRadii.topLeft
        for (k in 0..20) {
          val x = content.left + r + (content.width - 2f * r) * k / 20f
          val y = content.top + r + (content.height - 2f * r) * k / 20f
          for ((px, py) in listOf(x to content.top, x to content.bottom, content.left to y, content.right to y)) {
            receded = maxOf(receded, sdPolygon(px, py, ring))
          }
        }
        // Col ritaglio: tutto il suo contorno, angoli compresi, sta dentro il vetro.
        val clip = state.travelClipIn(panel.frame, t)
        assertNotNull("fotogramma $i (t = $t): la goccia si sta posando e il ritaglio e' spento", clip)
        for (p in roundRectOutline(clip!!.translate(panel.frame.topLeft))) {
          worst = maxOf(worst, sdPolygon(p.x, p.y, ring))
        }
      }
      if (i > 30 && state.liquid.quiet && spring.isFinishedFromNanos(nanos)) break
      i++
    }
    assertTrue("la goccia non si stava posando dopo l'arrivo: il caso non e' provato", settlingAfterArrival > 3)
    assertTrue("la goccia non era rimasta indietro dopo l'arrivo ($receded px): il caso non e' provato", receded > 0.5f)
    assertTrue("il ritaglio esce dal vetro di $worst px", worst <= 0.6f)
    // Posata: niente ritaglio.
    assertTrue(state.liquid.quiet)
    assertNull(state.travelClipIn(panel.frame, t))
  }

  /** Il contorno di [r], lati e angoli, a passi di circa un pixel. */
  private fun roundRectOutline(r: androidx.compose.ui.geometry.RoundRect): List<androidx.compose.ui.geometry.Offset> {
    val out = ArrayList<androidx.compose.ui.geometry.Offset>()
    fun arc(cx: Float, cy: Float, radius: Float, from: Double) {
      val steps = maxOf(4, (radius * Math.PI / 2).toInt())
      for (s in 0..steps) {
        val a = from + Math.PI / 2 * s / steps
        out += androidx.compose.ui.geometry.Offset(cx + radius * kotlin.math.cos(a).toFloat(), cy + radius * kotlin.math.sin(a).toFloat())
      }
    }
    val tl = r.topLeftCornerRadius.x
    val tr = r.topRightCornerRadius.x
    val br = r.bottomRightCornerRadius.x
    val bl = r.bottomLeftCornerRadius.x
    arc(r.left + tl, r.top + tl, tl, Math.PI)
    arc(r.right - tr, r.top + tr, tr, -Math.PI / 2)
    arc(r.right - br, r.bottom - br, br, 0.0)
    arc(r.left + bl, r.bottom - bl, bl, Math.PI / 2)
    // I lati diritti, fra un arco e l'altro.
    fun side(x0: Float, y0: Float, x1: Float, y1: Float) {
      val steps = maxOf(2, maxOf(kotlin.math.abs(x1 - x0), kotlin.math.abs(y1 - y0)).toInt())
      for (s in 0..steps) out += androidx.compose.ui.geometry.Offset(x0 + (x1 - x0) * s / steps, y0 + (y1 - y0) * s / steps)
    }
    side(r.left + tl, r.top, r.right - tr, r.top)
    side(r.right, r.top + tr, r.right, r.bottom - br)
    side(r.left + bl, r.bottom, r.right - br, r.bottom)
    side(r.left, r.top + tl, r.left, r.bottom - bl)
    return out
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
