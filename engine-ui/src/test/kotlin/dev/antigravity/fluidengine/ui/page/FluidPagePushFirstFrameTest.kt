package dev.antigravity.fluidengine.ui.page

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidPagePushFirstFrameTest {
  private val clock = BroadcastFrameClock()
  private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher() + clock)
  private val state = FluidPageHostState().also {
    it.scope = scope
    it.width = 1600f
    it.onStack(listOf("A"))
  }

  @After fun close() = scope.cancel()

  @Test fun laMisuraLungaPrecedeIlPrimoCampioneDellaMolla() {
    state.onStack(listOf("A", "B"))
    clock.sendFrame(0L)
    state.pagePlaced("A")
    clock.sendFrame(100_000_000L)
    assertEquals(1600f, state.travelPx, 0f)
    state.pagePlaced("B")
    clock.sendFrame(200_000_000L)
    assertEquals(1600f, state.travelPx, 0f)
    clock.sendFrame(208_333_333L)
    assertTrue(state.travelPx in 1500f..<1600f)
    val previous = state.travelPx
    state.pagePlaced("B")
    clock.sendFrame(216_666_666L)
    assertTrue(state.travelPx < previous)
  }

  @Test fun unPopPrimaDellaMisuraNonFaRiappareLaPagina() {
    state.onStack(listOf("A", "B"))
    state.onStack(listOf("A"))
    state.pagePlaced("B")
    clock.sendFrame(100_000_000L)
    assertFalse(state.isMoving)
    assertEquals("A", state.upper)
  }

  @Test fun ilPosizionamentoDellaPaginaSostituitaNonAvviaQuellaNuova() {
    state.onStack(listOf("A", "B"))
    state.onStack(listOf("A", "C"))
    state.pagePlaced("B")
    clock.sendFrame(0L)
    clock.sendFrame(100_000_000L)
    assertEquals("C", state.pendingPushPage)
    assertEquals(1600f, state.travelPx, 0f)
  }

  @Test fun ilMovimentoRidottoSiPosaSenzaAspettareIlLayout() {
    state.reducedMotion = true
    state.onStack(listOf("A", "B"))
    assertFalse(state.isMoving)
    assertEquals("B", state.upper)
  }
}
