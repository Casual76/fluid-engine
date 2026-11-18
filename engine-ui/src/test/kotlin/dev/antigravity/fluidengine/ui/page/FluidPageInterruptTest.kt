package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import dev.antigravity.fluidengine.ui.page.FluidPageHostState.PageRole
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** La molla vera deve passare di mano senza perdere il fotogramma o liberare le voci in viaggio. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class FluidPageInterruptTest(
  private val transition: FluidPageTransition,
  private val fraction: Float,
  private val direction: Int,
) {
  @Test fun gestureKeepsTheDrawnFrame() = atPush { rig ->
    val before = rig.frame()
    assertNotNull(rig.state.beginDrag(direction))
    rig.assertFrame(before)
    assertEquals(FluidPageMove.Back, rig.state.kind)
    assertFalse(rig.state.opening)
    assertTrue(rig.state.isInteractive)
  }

  @Test fun gestureKeepsTheSignedVelocity() = atPush { rig ->
    val before = rig.state.velocity
    assertTrue("la molla sta entrando", before < -1f)
    rig.state.beginDrag(direction)
    assertEquals(before, rig.state.velocity, VelocityTolerance)
  }

  @Test fun popKeepsTheDrawnFrame() = atPush { rig ->
    val before = rig.frame()
    rig.stack(listOf("root"))
    rig.assertFrame(before)
    assertEquals(FluidPageMove.Pop, rig.state.kind)
    assertFalse(rig.state.opening)
  }

  @Test fun popKeepsTheSignedVelocityAndStartsItsSpringThere() = atPush { rig ->
    val before = rig.state.velocity
    assertTrue("la molla sta entrando", before < -1f)
    rig.stack(listOf("root"))
    assertEquals(before, rig.state.velocity, VelocityTolerance)
    // Il primo tick della nuova Animatable e' a tempo zero: anche il suo initialVelocity conta.
    rig.tick()
    assertEquals(before, rig.state.velocity, VelocityTolerance)
  }

  @Test fun gestureHoldsThePageUntilReleaseThenCommits() = atPush { rig ->
    val token = rig.state.beginDrag(direction)!!
    val held = rig.frame()
    repeat(120) { rig.tick() }
    rig.assertFrame(held)
    rig.assertRetained()
    rig.state.release(token, 500f, decided = true)
    rig.assertRetained()
    rig.finish("root")
  }

  @Test fun cancelledGestureReturnsToThePageBeingPushed() = atPush { rig ->
    val token = rig.state.beginDrag(direction)!!
    rig.state.release(token, -100f, decided = false)
    rig.assertRetained()
    rig.finish("detail")
  }

  @Test fun popRetainsTheDepartingEntryUntilRest() = atPush { rig ->
    rig.stack(listOf("root"))
    rig.assertRetained()
    rig.finish("root")
  }

  @Test fun pushingTheSameEntryDuringPopKeepsFrameAndVelocity() {
    Rig(transition, direction).use { rig ->
      rig.stack(listOf("root", "detail"))
      rig.finish("detail")
      rig.stack(listOf("root"))
      rig.advanceTo(fraction, opening = false)
      val before = rig.frame()
      val velocity = rig.state.velocity
      assertTrue(velocity > 1f)
      rig.stack(listOf("root", "detail"))
      rig.assertFrame(before)
      assertEquals(velocity, rig.state.velocity, VelocityTolerance)
      assertEquals(FluidPageMove.Push, rig.state.kind)
      rig.assertRetained()
      rig.finish("detail")
    }
  }

  @Test fun aDifferentUpperPageStartsANewPush() = atPush { rig ->
    rig.stack(listOf("root", "detail", "other"))
    assertEquals("other", rig.state.upper)
    assertEquals("detail", rig.state.lower)
    assertEquals(rig.state.exitTravel(), rig.state.travelPx, PositionTolerance)
    assertEquals(0f, rig.state.velocity, VelocityTolerance)
    rig.finish("other")
  }

  @Test fun expandExplicitlyFinishesOpeningBeforeGestureOrPop() {
    for (gesture in listOf(false, true)) {
      Rig(FluidPageTransition.Expand, direction).use { rig ->
        rig.stack(listOf("root", "detail"))
        rig.advanceTo(fraction, opening = true)
        assertTrue(rig.state.pageScale(PageRole.Upper) < 1f)
        if (gesture) rig.state.beginDrag(direction) else rig.stack(listOf("root"))
        assertFalse(rig.state.opening)
        assertEquals(0f, rig.state.travelPx, PositionTolerance)
        assertEquals(1f, rig.state.pageScale(PageRole.Upper), ScaleTolerance)
        assertEquals(0f, rig.state.velocity, VelocityTolerance)
        rig.assertRetained()
        if (gesture) rig.state.onStack(emptyList()) else rig.finish("root")
      }
    }
  }

  @Test fun reducedMotionStillSettlesSynchronously() {
    Rig(transition, direction).use { rig ->
      rig.state.reducedMotion = true
      rig.stack(listOf("root", "detail"))
      assertFalse(rig.state.isMoving)
      assertEquals("detail", rig.state.upper)
      rig.stack(listOf("root"))
      assertFalse(rig.state.isMoving)
      assertEquals("root", rig.state.upper)
    }
  }

  private fun atPush(check: (Rig) -> Unit) {
    Rig(transition, direction).use { rig ->
      rig.stack(listOf("root", "detail"))
      rig.advanceTo(fraction, opening = true)
      check(rig)
    }
  }

  private data class Frame(val upperX: Float, val lowerX: Float, val scale: Float)

  private class Rig(transition: FluidPageTransition, direction: Int) : Closeable {
    private val clock = BroadcastFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
    private var nanos = 0L
    private var requested: List<Any> = emptyList()
    private var settled = 0
    // Modello del contratto di FluidNavHost: onSettled libera le transizioni del navigator.
    private val retained = mutableSetOf<Any>()
    val state = FluidPageHostState().apply {
      this.scope = this@Rig.scope
      width = 1000f
      layoutDirectionSign = direction
      transitionOf = { transition }
      renderable = { it in retained || it in requested }
      onTransitionStart = { retained.addAll(it) }
      onSettled = { settled++; retained.retainAll(requested.toSet()) }
      onBack = { stack(requested.dropLast(1)) }
    }

    init { stack(listOf("root")); settled = 0 }

    fun stack(pages: List<Any>) {
      retained.addAll(requested)
      retained.addAll(pages)
      requested = pages
      state.onStack(pages)
      pages.lastOrNull()?.let(state::pagePlaced)
    }

    fun tick(step: Long = 8_333_333L) { nanos += step; clock.sendFrame(nanos) }

    fun advanceTo(fraction: Float, opening: Boolean) {
      repeat(2000) {
        tick(1_000_000L)
        val current = state.upperExitFraction()
        if (if (opening) current <= fraction else current >= fraction) {
          assertEquals("frazione campionata con passi da 1 ms", fraction, current, 0.01f)
          assertTrue(state.isMoving)
          settled = 0
          return
        }
      }
      throw AssertionError("la molla non ha raggiunto $fraction")
    }

    fun frame() = Frame(state.pageOffsetX(PageRole.Upper), state.pageOffsetX(PageRole.Lower), state.pageScale(PageRole.Upper))

    fun assertFrame(before: Frame) {
      val after = frame()
      assertEquals("traslazione sopra", before.upperX, after.upperX, PositionTolerance)
      assertEquals("traslazione sotto", before.lowerX, after.lowerX, PositionTolerance)
      assertEquals("scala sopra", before.scale, after.scale, ScaleTolerance)
    }

    fun assertRetained() {
      assertEquals("nessun rilascio anticipato delle voci", 0, settled)
      assertTrue(retained.containsAll(listOf("root", "detail")))
      assertEquals("detail", state.upper)
      assertEquals("root", state.lower)
    }

    fun finish(top: String) {
      repeat(600) {
        if (!state.isMoving) {
          assertEquals(top, state.upper)
          assertEquals(FluidPageHostState.Phase.Idle, state.phase)
          assertEquals(0f, state.velocity, VelocityTolerance)
          assertEquals(requested.toSet(), retained)
          return
        }
        assertEquals("le voci si liberano solo a riposo", 0, settled)
        tick()
      }
      throw AssertionError("la molla non si e' fermata")
    }

    override fun close() { scope.cancel() }
  }

  companion object {
    // Nessun tempo passa durante la presa: tolleranze soltanto per gli arrotondamenti Float.
    private const val PositionTolerance = 0.001f // px
    private const val VelocityTolerance = 0.001f // px/s, segno compreso
    private const val ScaleTolerance = 0.000001f

    @JvmStatic @Parameterized.Parameters(name = "{0}, uscita={1}, verso={2}")
    fun cases(): List<Array<Any>> = listOf(FluidPageTransition.Slide, FluidPageTransition.ShrinkSlide).flatMap { transition ->
      listOf(0.2f, 0.5f, 0.8f).flatMap { fraction ->
        listOf(1, -1).map { direction -> arrayOf<Any>(transition, fraction, direction) }
      }
    }
  }
}
