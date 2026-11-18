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
    assertEquals("C", state.pendingPlacement)
    assertEquals(1600f, state.travelPx, 0f)
  }

  /**
   * C7 (giro di correzione di F4A): `[R,A] → [R,A,B] → [R]` mentre B aspetta il primo posizionamento, con `Expand`.
   * Prima il pop ricominciava da B a corsa zero, cioè a piena vista; ora esce A, la pagina che si vede.
   */
  @Test fun unPopAllaRadicePrimaDellaMisuraNonMostraLaPaginaMaiPosata() {
    val expand = FluidPageHostState().also {
      it.scope = scope
      it.width = 1600f
      it.transitionOf = { FluidPageTransition.Expand }
      it.onStack(listOf("R", "A"))
    }
    expand.onStack(listOf("R", "A", "B"))
    assertEquals("B", expand.pendingPlacement)
    expand.onStack(listOf("R"))
    assertEquals("esce la pagina che si vede", "A", expand.upper)
    assertEquals("R", expand.lower)
    assertEquals(FluidPageMove.Pop, expand.kind)
    assertEquals(0f, expand.travelPx, 0f)
    var time = 0L
    repeat(90) {
      clock.sendFrame(time)
      time += 16_666_667L
      assertTrue("B non torna mai sopra", expand.upper != "B")
    }
    assertFalse(expand.isMoving)
    assertEquals("R", expand.upper)
  }

  /** Lo stesso con `Slide`: la pagina mai posata resta fuori, e la sotto esce invece di sparire in un fotogramma. */
  @Test fun conSlideEsceLaPaginaCheSiVedeEnonQuellaMaiPosata() {
    state.onStack(listOf("A", "B"))
    state.pagePlaced("B")
    var time = 0L
    repeat(90) {
      clock.sendFrame(time)
      time += 16_666_667L
    }
    state.onStack(listOf("A", "B", "C"))
    state.onStack(listOf("A"))
    assertEquals("B", state.upper)
    assertEquals("A", state.lower)
    assertEquals(FluidPageMove.Pop, state.kind)
    assertEquals(0f, state.travelPx, 0f)
  }

  /** A4: il passo fra schede aspetta il primo posizionamento della scheda che arriva, come il push (F4A.7). */
  @Test fun ilCambioDiSchedaParteDalPrimoPosizionamento() {
    state.peerOf = { from, to -> if (from == "A" && to == "T") 1 else if (from == "T" && to == "A") -1 else 0 }
    state.onStack(listOf("T"))
    assertEquals(FluidPageMove.Peer, state.kind)
    assertEquals("T", state.pendingPlacement)
    clock.sendFrame(0L)
    clock.sendFrame(150_000_000L)
    assertEquals("la misura lunga non consuma il passo", 0f, state.peerTime, 0f)
    assertEquals("finché non è posata la scheda che arriva non copre niente", 1600f to 1600f, state.upperCoverage())
    state.pagePlaced("T")
    assertEquals(null, state.pendingPlacement)
    clock.sendFrame(300_000_000L)
    clock.sendFrame(316_666_667L)
    assertTrue(state.peerTime > 0f)
    assertTrue("posata, copre", state.upperCoverage().first < 1600f)
    var time = 333_333_334L
    repeat(60) {
      clock.sendFrame(time)
      time += 16_666_667L
    }
    assertFalse(state.isMoving)
    assertEquals("T", state.upper)
  }

  /** Un push che aspetta la misura sta fuori dallo schermo: anche la sua copertura è fuori. */
  @Test fun unPushInAttesaNonCopreNiente() {
    state.onStack(listOf("A", "B"))
    assertEquals(1600f to 1600f, state.upperCoverage())
  }

  @Test fun ilMovimentoRidottoSiPosaSenzaAspettareIlLayout() {
    state.reducedMotion = true
    state.onStack(listOf("A", "B"))
    assertFalse(state.isMoving)
    assertEquals("B", state.upper)
  }
}
