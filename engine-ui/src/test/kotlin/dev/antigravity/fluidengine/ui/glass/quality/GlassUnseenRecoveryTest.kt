package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Full
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Protected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.26 · Review R2, difetto 1: dove non si vede niente (la finestra in fondo, o la decisione d'avvio senza finestra) la
 * risalita da segnale si applica alla lettura sana ([GlassQualityPolicy.riseUnseen]); una finestra in vista invece
 * aspetta la posa ([GlassSignalRecoveryTest]).
 */
class GlassUnseenRecoveryTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)
  private val period = 8_333_333L

  @Test fun unaFinestraCheNonSiVedeRisaleAllaLetturaSana() {
    val policy = GlassQualityPolicy(capable, cool, Full, Full)
    policy.onWindow(0, true, false, 0, period)
    policy.updateConditions(0, cool.copy(heapFraction = 0.9))
    assertEquals(Protected, policy.decision.optics)
    assertNull("in vista: la lettura sana da sola non cambia niente", policy.riseUnseen(1_000_000_000L))
    policy.onWindow(2_000_000_000L, false, false, 0, period)
    policy.updateConditions(6_000_000_000L, cool)
    assertEquals("in fondo si risale fino al tetto dei fotogrammi, in un passo",
      GlassQualityReason.SettledRecovery, policy.riseUnseen(6_000_000_000L))
    assertEquals(Full, policy.decision.optics)
    assertEquals(GlassMemoryProfile.Full, policy.decision.memoryProfile)
    assertTrue(policy.decision.allowPrefetch)
  }

  @Test fun laDecisioneDAvvioSenzaFinestraFaLoStesso() {
    val start = GlassQualityPolicy(capable, cool.copy(lowMemory = true), Full, Full)
    assertEquals(Protected, start.decision.optics)
    assertNull("la pressione c'è ancora", start.riseUnseen(1))
    start.updateConditions(2, cool)
    assertEquals(GlassQualityReason.SettledRecovery, start.riseUnseen(3))
    assertEquals(Full, start.decision.optics)
    assertNull(start.riseUnseen(4))
  }
}
