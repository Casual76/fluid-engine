package dev.antigravity.fluidengine.foundation

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F8.20 · La regola delle condizioni: i motivi giusti, nell'ordine giusto, e «Fallo comunque». */
class FluidConditionsTest {
  private val wifi = FluidConditions(online = true, connected = true)
  private val cellular = FluidConditions(online = true, connected = true, metered = true)

  @After fun reset() = FluidDeviceConditions.reset()

  @Test fun `un download grande col Wi-Fi parte`() {
    val permit = wifi.allows(FluidWork.LargeDownload)
    assertTrue(permit.allowed)
    assertTrue(permit.reasons.isEmpty())
  }

  @Test fun `coi dati mobili un download grande aspetta il Wi-Fi e si puo' forzare`() {
    val permit = cellular.allows(FluidWork.LargeDownload)
    assertFalse(permit.allowed)
    assertEquals(listOf(FluidConditionReason.Metered), permit.reasons)
    assertTrue(permit.canForce)
    assertTrue(cellular.allows(FluidWork.LargeDownload, force = true).allowed)
  }

  @Test fun `una sincronizzazione piccola coi dati mobili parte`() {
    assertTrue(cellular.allows(FluidWork.Sync).allowed)
  }

  @Test fun `senza rete non si forza`() {
    val offline = FluidConditions.Offline
    val permit = offline.allows(FluidWork.LargeDownload, force = true)
    assertFalse(permit.allowed)
    assertEquals(FluidConditionReason.Offline, permit.reason)
    assertFalse(permit.canForce)
  }

  @Test fun `una rete senza internet e' NoInternet, non Offline`() {
    val captive = FluidConditions(online = false, connected = true)
    assertEquals(FluidConditionReason.NoInternet, captive.allows(FluidWork.Sync).reason)
  }

  @Test fun `il roaming ferma anche i media ricchi, la rete a consumo no`() {
    val roaming = cellular.copy(roaming = true)
    assertEquals(listOf(FluidConditionReason.Roaming), roaming.allows(FluidWork.LargeDownload).reasons)
    assertEquals(listOf(FluidConditionReason.Roaming), roaming.allows(FluidWork.RichMedia).reasons)
    assertTrue(cellular.allows(FluidWork.RichMedia).allowed)
  }

  @Test fun `risparmio dati e Usa pochi dati fermano i lavori che evitano i limiti`() {
    val saver = cellular.copy(dataSaver = true)
    assertEquals(listOf(FluidConditionReason.Metered, FluidConditionReason.DataSaver), saver.allows(FluidWork.LargeDownload).reasons)
    assertEquals(listOf(FluidConditionReason.DataSaver), saver.allows(FluidWork.RichMedia).reasons)
    val lowData = wifi.copy(lowData = true)
    assertEquals(listOf(FluidConditionReason.LowData), lowData.allows(FluidWork.Prefetch).reasons)
    assertTrue(lowData.allows(FluidWork.Sync).allowed)
  }

  @Test fun `il risparmio energetico ferma il lavoro pesante solo senza corrente`() {
    val saving = wifi.copy(powerSave = true)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(FluidWork.LargeDownload).reasons)
    assertTrue(saving.copy(charging = true).allows(FluidWork.LargeDownload).allowed)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(FluidWork.ContinuousHaptics).reasons)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(FluidWork.MotionEffects).reasons)
  }

  @Test fun `col risparmio energetico gli effetti si fermano anche in carica`() {
    // F8.20b (domanda 7, risposta di Alessio): chi accende il risparmio energetico non vuole gli effetti nemmeno col
    // cavo; i lavori grandi, attaccati alla corrente, non consumano la batteria e partono.
    val saving = wifi.copy(powerSave = true, charging = true)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(FluidWork.MotionEffects).reasons)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(FluidWork.ContinuousHaptics).reasons)
    assertTrue(saving.allows(FluidWork.LargeDownload).allowed)
    assertTrue(saving.allows(FluidWork.RichMedia).allowed)
    val custom = FluidWork.custom("animazioni", needsNetwork = false, avoidsLowPower = true, lowPowerWhileCharging = true)
    assertEquals(listOf(FluidConditionReason.PowerSave), saving.allows(custom).reasons)
    assertTrue(wifi.copy(charging = true).allows(FluidWork.MotionEffects).allowed)
  }

  @Test fun `il telefono caldo ferma la luce del vetro ma non l'aptica`() {
    val hot = wifi.copy(thermal = FluidThermal.Serious)
    assertTrue(hot.hot)
    assertEquals(listOf(FluidConditionReason.Hot), hot.allows(FluidWork.MotionEffects).reasons)
    assertTrue(hot.allows(FluidWork.ContinuousHaptics).allowed)
    assertTrue(wifi.copy(thermal = FluidThermal.Fair).allows(FluidWork.MotionEffects).allowed)
  }

  @Test fun `solo Wi-Fi e solo in carica`() {
    val needs = FluidWorkNeeds(wifiOnly = true, chargingOnly = true)
    val onCellular = cellular.allows(FluidWork.Sync, needs)
    assertEquals(listOf(FluidConditionReason.NotWifi, FluidConditionReason.NotCharging), onCellular.reasons)
    assertTrue(onCellular.canForce)
    assertTrue(cellular.copy(charging = true).allows(FluidWork.Sync, needs, force = true).allowed)
    // Su una rete a consumo un download grande dice gia' «rete a consumo»: non due volte la stessa cosa.
    assertEquals(listOf(FluidConditionReason.Metered), cellular.copy(charging = true).allows(FluidWork.LargeDownload, needs).reasons)
    assertTrue(wifi.copy(charging = true).allows(FluidWork.LargeDownload, needs).allowed)
  }

  @Test fun `tutto insieme, nell'ordine`() {
    val worst = FluidConditions(
      online = true, connected = true, metered = true, roaming = true, dataSaver = true, powerSave = true,
      thermal = FluidThermal.Critical, lowData = true,
    )
    assertEquals(
      listOf(
        FluidConditionReason.Roaming, FluidConditionReason.DataSaver, FluidConditionReason.LowData,
        FluidConditionReason.PowerSave, FluidConditionReason.Hot,
      ),
      worst.allows(FluidWork.LargeDownload).reasons,
    )
    assertTrue(worst.allows(FluidWork.LargeDownload, force = true).allowed)
  }

  @Test fun `senza lettura non si ferma niente`() {
    assertTrue(FluidConditions.Unknown.allows(FluidWork.LargeDownload, FluidWorkNeeds(wifiOnly = true, chargingOnly = true)).allowed)
  }

  @Test fun `il posto solo, Usa pochi dati e la simulazione`() {
    assertFalse(FluidDeviceConditions.current.known)
    FluidDeviceConditions.publish(wifi)
    assertTrue(FluidDeviceConditions.allows(FluidWork.LargeDownload).allowed)
    FluidDeviceConditions.lowData = true
    assertEquals(FluidConditionReason.LowData, FluidDeviceConditions.allows(FluidWork.LargeDownload).reason)
    FluidDeviceConditions.lowData = false
    FluidDeviceConditions.simulate { it.copy(metered = true) }
    assertTrue(FluidDeviceConditions.simulated)
    assertEquals(FluidConditionReason.Metered, FluidDeviceConditions.allows(FluidWork.LargeDownload).reason)
    assertFalse(FluidDeviceConditions.measured.metered)
    FluidDeviceConditions.simulate(null)
    assertTrue(FluidDeviceConditions.allows(FluidWork.LargeDownload).allowed)
  }

  @Test fun `una simulazione prima della prima lettura vale`() {
    FluidDeviceConditions.simulate { it.copy(powerSave = true) }
    assertTrue(FluidDeviceConditions.current.known)
    assertEquals(FluidConditionReason.PowerSave, FluidDeviceConditions.allows(FluidWork.ContinuousHaptics).reason)
  }

  @Test fun `le frasi dei motivi`() {
    val phrases = FluidConditionPhrases()
    assertEquals("Aspetto il Wi-Fi", phrases.waitingFor(listOf(FluidConditionReason.Metered)))
    assertEquals("Aspetto la rete", phrases.waitingFor(listOf(FluidConditionReason.NoInternet)))
    assertEquals("Il telefono è caldo", phrases.reason(FluidConditionReason.Hot))
    FluidConditionReason.all.forEach { reason ->
      assertTrue(phrases.reason(reason).isNotBlank())
      assertEquals(reason, FluidConditionReason.named(reason.name))
    }
  }

  @Test fun `i gradini del calore`() {
    assertTrue(FluidThermal.Critical > FluidThermal.Serious)
    assertEquals(FluidThermal.Critical, FluidThermal.of(9))
    assertFalse(FluidThermal.Fair.hot)
  }
}
