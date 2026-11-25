package dev.antigravity.fluidengine.net

import dev.antigravity.fluidengine.net.testing.FakeNetworks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.20 · L'osservatore della rete con le reti finte: la regola (Wi-Fi senza internet, VPN, rete sospesa) e la tenuta
 * (micro-cadute e cambio di rete che non lampeggiano, una caduta vera che si vede, il ritorno subito).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineNetworkTest {
  private val wifiOk = EngineNetworkCaps(internet = true, validated = true, notMetered = true)

  @Test fun `una rete validata e' online, una non validata no`() {
    assertTrue(EngineNetworkRule.stateOf(wifiOk, emptyList()).online)
    val hotel = EngineNetworkRule.stateOf(wifiOk.copy(validated = false), emptyList())
    assertTrue(hotel.connected)
    assertFalse(hotel.online)
    val portal = EngineNetworkRule.stateOf(wifiOk.copy(captivePortal = true), emptyList())
    assertFalse(portal.online)
    assertEquals(EngineNetworkState.Offline, EngineNetworkRule.stateOf(null, emptyList()))
    assertEquals(EngineNetworkState.Offline, EngineNetworkRule.stateOf(wifiOk.copy(internet = false), emptyList()))
  }

  @Test fun `i dati mobili sono a consumo, il roaming si dice, una rete sospesa non e' online`() {
    val cell = EngineNetworkCaps(internet = true, validated = true, notMetered = false, notRoaming = false)
    val state = EngineNetworkRule.stateOf(cell, emptyList())
    assertTrue(state.online)
    assertTrue(state.metered)
    assertTrue(state.roaming)
    assertFalse(EngineNetworkRule.stateOf(cell.copy(notSuspended = false), emptyList()).online)
  }

  @Test fun `una VPN vale quanto la rete sotto`() {
    val vpn = EngineNetworkCaps(internet = true, validated = true, notMetered = true, vpn = true)
    // Sotto un Wi-Fi che porta a internet: online, anche se la VPN non si dice validata.
    assertTrue(EngineNetworkRule.stateOf(vpn.copy(validated = false), listOf(wifiOk)).online)
    // Sotto un Wi-Fi senza internet: offline, anche se la VPN si dice validata.
    val below = EngineNetworkRule.stateOf(vpn, listOf(wifiOk.copy(validated = false)))
    assertTrue(below.connected)
    assertFalse(below.online)
    assertTrue(below.vpn)
    // Sotto i dati mobili: a consumo come loro.
    val cell = EngineNetworkCaps(internet = true, validated = true, notMetered = false)
    assertTrue(EngineNetworkRule.stateOf(vpn, listOf(cell)).metered)
    // Wi-Fi e dati insieme: la VPN usa il Wi-Fi, che non costa.
    assertFalse(EngineNetworkRule.stateOf(vpn, listOf(cell, wifiOk)).metered)
    // Senza sapere cosa c'e' sotto si crede alla VPN.
    assertTrue(EngineNetworkRule.stateOf(vpn, emptyList()).online)
  }

  @Test fun `le reti finte, passo per passo`() {
    val nets = FakeNetworks()
    assertTrue(nets.raw.value.online)
    nets.wifi(validated = false)
    assertFalse(nets.raw.value.online)
    assertTrue(nets.raw.value.connected)
    nets.wifi()
    nets.vpn()
    assertTrue(nets.raw.value.vpn)
    assertTrue(nets.raw.value.online)
    nets.wifi(validated = false)
    assertFalse(nets.raw.value.online)
    nets.vpnOff()
    nets.wifi()
    nets.cellular()
    assertTrue(nets.raw.value.metered)
    nets.disconnect()
    assertEquals(EngineNetworkState.Offline, nets.raw.value)
  }

  @Test fun `una micro-caduta non si vede`() = runTest {
    val nets = FakeNetworks(now = clockOf(this))
    val seen = collect(nets)
    runCurrent()
    launch { nets.microCut(600) }
    advanceTimeBy(5_000)
    runCurrent()
    assertEquals(listOf(true), seen.map { it.online })
    coroutineContext.cancelChildren()
  }

  @Test fun `il passaggio dal Wi-Fi ai dati non si vede, poi la rete costa`() = runTest {
    val nets = FakeNetworks(now = clockOf(this))
    val seen = collect(nets)
    runCurrent()
    launch { nets.handover(gapMillis = 400, validationMillis = 900) }
    advanceTimeBy(5_000)
    runCurrent()
    assertEquals(listOf(true, true), seen.map { it.online })
    assertEquals(listOf(false, true), seen.map { it.metered })
    coroutineContext.cancelChildren()
  }

  @Test fun `una caduta vera si vede dopo la tenuta, il ritorno subito`() = runTest {
    val nets = FakeNetworks(now = clockOf(this))
    val seen = collect(nets)
    runCurrent()
    nets.disconnect()
    advanceTimeBy(EngineNetworkDefaults.OfflineGraceMillis - 100)
    runCurrent()
    assertEquals(listOf(true), seen.map { it.online })
    advanceTimeBy(200)
    runCurrent()
    assertEquals(listOf(true, false), seen.map { it.online })
    nets.wifi()
    runCurrent()
    assertEquals(listOf(true, false, true), seen.map { it.online })
    coroutineContext.cancelChildren()
  }

  @Test fun `la tenuta conta dalla prima caduta, non dall'ultimo cambio`() = runTest {
    val nets = FakeNetworks(now = clockOf(this))
    val seen = collect(nets)
    runCurrent()
    nets.wifi(validated = false) // Wi-Fi senza internet
    advanceTimeBy(1_500)
    nets.disconnect() // poi niente del tutto
    advanceTimeBy(1_100)
    runCurrent()
    // 2,6 s dalla prima caduta: si vede, anche se dall'ultimo cambio ne sono passati 1,1.
    assertEquals(listOf(true, false), seen.map { it.online })
    coroutineContext.cancelChildren()
  }

  @Test fun `un Wi-Fi senza internet resta offline senza lampeggiare`() = runTest {
    val raw = MutableStateFlow(EngineNetworkState(connected = true, validated = true, metered = false))
    val seen = ArrayList<EngineNetworkState>()
    val job = launch { EngineNetwork.steady(raw, 2_500, clockOf(this@runTest)).collect { seen += it } }
    runCurrent()
    raw.value = EngineNetworkState(connected = true, validated = false, metered = false)
    advanceTimeBy(3_000)
    runCurrent()
    assertEquals(listOf(true, false), seen.map { it.online })
    assertTrue(seen.last().connected)
    job.cancel()
  }

  @Test fun `due raccolte non si pestano`() = runTest {
    val nets = FakeNetworks(now = clockOf(this))
    val a = collect(nets)
    runCurrent()
    nets.disconnect()
    advanceTimeBy(3_000)
    runCurrent()
    val b = collect(nets)
    runCurrent()
    assertEquals(listOf(true, false), a.map { it.online })
    assertEquals(listOf(false), b.map { it.online })
    coroutineContext.cancelChildren()
  }

  private fun TestScope.collect(nets: FakeNetworks): List<EngineNetworkState> {
    val seen = ArrayList<EngineNetworkState>()
    launch { nets.states.collect { seen += it } }
    return seen
  }

  private fun clockOf(scope: TestScope): () -> Long = { scope.testScheduler.currentTime }

  private fun kotlin.coroutines.CoroutineContext.cancelChildren() {
    this[kotlinx.coroutines.Job]?.children?.forEach { it.cancel() }
  }
}
