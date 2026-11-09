package dev.antigravity.fluidengine.ui.fluid

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluidNotificationOutcomeTest {
  private fun TestScope.host(): FluidNotificationHostState = FluidNotificationHostState().also { state ->
    state.setHostActive(true)
    backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
      state.runQueue({ it.durationMillis }, { testScheduler.currentTime })
    }
  }
  private fun notice(id: String, group: String? = null) =
    FluidNotification(id, "Titolo", "Testo", durationMillis = 2_000, replaceGroup = group)

  @Test fun sostituzioneDuranteUscitaNonAttendeHandshakeVecchio() = runTest {
    val state = host()
    val a = backgroundScope.async { state.showForResult(notice("a", "g")) }
    runCurrent(); state.markPresented("a"); state.dismissCurrent(); runCurrent()
    assertEquals(FluidNotificationResult.Dismissed, a.await())
    val b = backgroundScope.async { state.showForResult(notice("b", "g")) }
    runCurrent()
    assertEquals("b", state.currentNotification?.id)
    state.markExitCompleted("a")
    state.markPresented("b"); advanceTimeBy(2_000); runCurrent()
    assertEquals(FluidNotificationResult.TimedOut, b.await())
  }

  @Test fun sostituisceAncheRichiesteAccodateSenzaAlterareAltriGruppi() = runTest {
    val state = host()
    backgroundScope.launch { state.show(notice("fifo")) }
    val a = backgroundScope.async { state.showForResult(notice("a", "g")) }
    backgroundScope.launch { state.show(notice("altro")) }
    val b = backgroundScope.async { state.showForResult(notice("b", "g")) }
    runCurrent()
    assertEquals(FluidNotificationResult.Replaced, a.await())
    state.markPresented("fifo"); state.dismissCurrent(); runCurrent(); state.markExitCompleted("fifo"); runCurrent()
    assertEquals("altro", state.currentNotification?.id)
    state.markPresented("altro"); state.dismissCurrent(); runCurrent(); state.markExitCompleted("altro"); runCurrent()
    assertEquals("b", state.currentNotification?.id)
    assertFalse(b.isCompleted)
  }

  @Test fun doppioToccoCallbackUnaVoltaEEsitoCondiviso() = runTest {
    val state = host()
    var clicks = 0
    val action = FluidNotificationAction("Ripristina") { clicks++ }
    val n = notice("a").copy(inlineActions = listOf(action))
    val result = backgroundScope.async { state.showForResult(n) }
    val duplicate = backgroundScope.async { state.showForResult(n) }
    runCurrent(); state.markPresented("a")
    state.performAction("a", action); state.performAction("a", action); runCurrent()
    assertEquals(1, clicks)
    assertEquals(FluidNotificationResult.ActionPerformed("Ripristina"), result.await())
    assertEquals(result.await(), duplicate.await())
  }

  @Test fun scadenzaApplicativaRimuoveAzioneDuranteHoldSenzaRiavviareTimeout() = runTest {
    val state = host()
    var clicks = 0
    val action = FluidNotificationAction("Ripristina") { clicks++ }
    val n = notice("a").copy(inlineActions = listOf(action))
    val result = backgroundScope.async { state.showForResult(n) }
    runCurrent(); state.markPresented("a"); state.setTouched("a", true)
    advanceTimeBy(10_000); runCurrent()
    assertTrue(state.updateCurrent(n.copy(message = "Completata", inlineActions = emptyList())))
    state.performAction("a", action)
    assertEquals(0, clicks)
    assertEquals("Completata", state.currentNotification?.message)
    assertFalse(result.isCompleted)
    state.setTouched("a", false); advanceTimeBy(2_000); runCurrent()
    assertEquals(FluidNotificationResult.TimedOut, result.await())
  }

  @Test fun sostituzionePrimaPresentazioneRilasciaEntrambeLeAttese() = runTest {
    val state = host()
    val a = backgroundScope.async { state.showForResult(notice("a", "g")) }
    val delivery = backgroundScope.async { state.show(notice("a", "g")) }
    runCurrent()
    backgroundScope.launch { state.show(notice("b", "g")) }
    runCurrent()
    assertEquals(FluidNotificationResult.Replaced, a.await())
    assertEquals(FluidNotificationDelivery.Rejected, delivery.await())
    assertEquals("b", state.currentNotification?.id)
  }

  @Test fun rifiutoEUpdateDiIdentitaDiversa() = runTest {
    val state = host()
    assertEquals(FluidNotificationResult.Rejected, state.showForResult(notice("")))
    assertFalse(state.updateCurrent(notice("nessuna")))
  }

  @Test fun riavvioHostRiconsegnaNonPresentataEConservaEsito() = runTest {
    val state = FluidNotificationHostState()
    state.setHostActive(true)
    var runner = backgroundScope.launch { state.runQueue({ 2_000 }, { testScheduler.currentTime }) }
    val result = backgroundScope.async { state.showForResult(notice("a")) }
    runCurrent(); runner.cancel(); runCurrent()
    assertFalse(result.isCompleted)
    runner = backgroundScope.launch { state.runQueue({ 2_000 }, { testScheduler.currentTime }) }
    runCurrent(); state.markPresented("a"); advanceTimeBy(2_000); runCurrent()
    assertEquals(FluidNotificationResult.TimedOut, result.await())
    runner.cancel()
  }

  @Test fun updateNonRinnovaBudgetEGruppoSostituisceAncheDuranteHoldInattivo() = runTest {
    val state = host()
    val a = backgroundScope.async { state.showForResult(notice("a", "g")) }
    runCurrent(); state.markPresented("a"); advanceTimeBy(1_000); runCurrent()
    assertTrue(state.updateCurrent(notice("a", "g").copy(message = "Nuovo testo", durationMillis = 90_000)))
    advanceTimeBy(1_000); runCurrent()
    assertEquals(FluidNotificationResult.TimedOut, a.await())
    backgroundScope.launch { state.show(notice("b", "g")) }
    runCurrent(); state.markPresented("b"); state.setTouched("b", true); state.setHostActive(false)
    val b = backgroundScope.async { state.showForResult(notice("b", "g")) }
    backgroundScope.launch { state.show(notice("c", "g")) }
    runCurrent()
    assertEquals(FluidNotificationResult.Replaced, b.await())
    assertEquals("c", state.currentNotification?.id)
  }

  @Test fun clickObsoletoDopoReplaceNonEsegueCallback() = runTest {
    val state = host()
    var clicks = 0
    val action = FluidNotificationAction("Ripristina") { clicks++ }
    val a = backgroundScope.async { state.showForResult(notice("a", "g").copy(inlineActions = listOf(action))) }
    runCurrent(); state.markPresented("a")
    backgroundScope.launch { state.show(notice("b", "g")) }
    runCurrent(); state.performAction("a", action)
    assertEquals(FluidNotificationResult.Replaced, a.await())
    assertEquals(0, clicks)
  }

  @Test fun azionePrimaHandshakeNonLasciaDeliverySospesa() = runTest {
    val state = host()
    val action = FluidNotificationAction("Ripristina") { }
    val n = notice("a").copy(inlineActions = listOf(action))
    val delivery = backgroundScope.async { state.show(n) }
    val result = backgroundScope.async { state.showForResult(n) }
    runCurrent(); state.performAction("a", action); runCurrent()
    state.markExitCompleted("a"); runCurrent()
    assertEquals(FluidNotificationDelivery.Rejected, delivery.await())
    assertEquals(FluidNotificationResult.ActionPerformed("Ripristina"), result.await())
  }
}
