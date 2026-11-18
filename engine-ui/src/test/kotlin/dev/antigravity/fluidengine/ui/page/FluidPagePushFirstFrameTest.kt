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

  @Test fun laMollaDelPushPartePrimaDellaMisuraEIlCallbackNonLaRiavvia() {
    state.onStack(listOf("A", "B"))
    clock.sendFrame(0L)
    clock.sendFrame(8_333_333L)
    val before = state.travelPx
    assertTrue(before in 1500f..<1600f)
    state.pagePlaced("B")
    assertEquals(before, state.travelPx, 0f)
    clock.sendFrame(16_666_666L)
    assertTrue(state.travelPx < before)
  }

  @Test fun unPopPrimaDellaMisuraNonFaRiappareLaPagina() {
    state.onStack(listOf("A", "B"))
    state.onStack(listOf("A"))
    state.pagePlaced("B")
    clock.sendFrame(100_000_000L)
    assertFalse(state.isMoving)
    assertEquals("A", state.upper)
    clock.sendFrame(116_666_667L)
    assertEquals(0f, state.travelPx, 0f)
  }

  @Test fun ilPosizionamentoDellaPaginaSostituitaNonAvviaQuellaNuova() {
    state.onStack(listOf("A", "B"))
    state.onStack(listOf("A", "C"))
    state.pagePlaced("B")
    clock.sendFrame(0L)
    clock.sendFrame(100_000_000L)
    assertEquals("C", state.pendingPlacement)
    assertEquals("A", state.lower)
    assertTrue(state.travelPx in 0f..<1600f)
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

  @Test fun laDissolvenzaTieneLaPaginaSottoELeDuePagineFerme() {
    state.reducedMotion = true
    state.onStack(listOf("A", "B"))
    clock.sendFrame(0L)
    clock.sendFrame(100_000_000L)
    assertEquals("A", state.lower)
    assertEquals(1f, state.frontFraction(), 0f)
    state.pagePlaced("B")
    clock.sendFrame(200_000_000L)
    clock.sendFrame(300_000_000L)
    assertTrue(state.frontFraction() in 0.01f..0.99f)
    for (role in listOf(FluidPageHostState.PageRole.Upper, FluidPageHostState.PageRole.Lower)) {
      assertEquals(0f, state.pageOffsetX(role), 0f)
      assertEquals(1f, state.pageScale(role), 0f)
    }
    clock.sendFrame(500_000_000L)
    assertFalse(state.isMoving)
    assertEquals("B", state.upper)
  }

  @Test fun laSchedaMantieneIlPassoMaIlTitoloNonEsceDalBordoDiLettura() {
    state.peerOf = { _, _ -> -1 }
    state.onStack(listOf("T"))
    val offset = state.pageOffsetX(FluidPageHostState.PageRole.Upper)
    assertEquals(-208f, offset, .01f)
    assertEquals(0f, offset + state.headingCompensation("T"), 0f)
    assertEquals(0f, state.headingCompensation("A"), 0f)
  }
}
