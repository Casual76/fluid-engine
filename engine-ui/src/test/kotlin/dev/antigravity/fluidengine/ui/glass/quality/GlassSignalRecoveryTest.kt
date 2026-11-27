package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Full
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Protected
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics.Reduced
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.26 · Review R2, difetto 1: **una discesa da segnale risale senza la prova dei fotogrammi.** Fino a F7.25 ogni
 * risalita chiedeva trenta secondi di moto misurato e tre finestre buone, anche quando il vetro era sceso per una
 * pressione di memoria o per il calore, poi passati: in un'app che si scorre poco restava giù per sempre. Ora la distanza
 * dai segnali si recupera alla prima posa con la lettura sana (un livello alla volta, dove la risalita misurata cambia la
 * cattura), o subito se la finestra non si vede; quella dai fotogrammi chiede ancora i fotogrammi.
 */
class GlassSignalRecoveryTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)
  private val period = 8_333_333L

  private class Run(val policy: GlassQualityPolicy) {
    var now = 0L
    val optics get() = policy.decision.optics

    /** Un gesto di [seconds] secondi con fotogrammi puntuali (o in ritardo), poi la posa. */
    fun gesture(seconds: Double, late: Boolean = false, period: Long = 8_333_333L): GlassQualityReason? {
      policy.onWindow(now, true, true, 0, period)
      repeat((seconds * 1e9 / period).toInt()) {
        now += period
        val total = if (late) 2 * period else period / 2
        policy.onFrame(now, now, total, period, total, GlassFrameEvidence.MeasuredApp)
      }
      return policy.onWindow(now, true, false, 0, period)
    }

    fun wait(seconds: Int) { now += seconds * 1_000_000_000L }
    fun signal(value: GlassConditions) = policy.updateConditions(now, value)
  }

  private fun run(device: GlassDeviceCapacity = capable, conditions: GlassConditions = cool, shader: GlassOptics? = Full) =
    Run(GlassQualityPolicy(device, conditions, shader, Full)).also { it.policy.onWindow(0, true, false, 0, period) }

  @Test fun unaPressioneDiMemoriaPassataRisaleAllaPrimaPosaSenzaTrentaSecondi() {
    val r = run()
    assertEquals(Full, r.optics)
    r.wait(1)
    assertEquals(GlassQualityReason.HeapPressure, r.signal(cool.copy(heapFraction = 0.9)))
    assertEquals(Protected, r.optics)
    assertEquals(GlassMemoryProfile.Protected, r.policy.decision.memoryProfile)
    assertFalse(r.policy.decision.allowPrefetch)
    // La pressione passa. Una lettura sana da sola non cambia niente a schermo: la posa è il punto della risalita.
    r.wait(6)
    assertNull(r.signal(cool))
    assertEquals(Protected, r.optics)
    // Un gesto di un secondo e la posa: un livello, la riga della fascia, il prefetch di nuovo acceso.
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(1.0))
    assertEquals(Reduced, r.optics)
    assertEquals(GlassMemoryProfile.Full, r.policy.decision.memoryProfile)
    assertTrue(r.policy.decision.allowPrefetch)
    // Il secondo livello alla posa dopo, passati i cinque secondi fra due cambi.
    assertNull(r.gesture(1.0))
    assertEquals(Reduced, r.optics)
    r.wait(5)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(1.0))
    assertEquals(Full, r.optics)
  }

  @Test fun ilCaloreChePassaRisaleAncheLui() {
    val r = run()
    assertEquals(GlassQualityReason.SevereThermal, r.signal(cool.copy(thermal = GlassThermal.SevereOrWorse)))
    r.wait(6)
    // Da Protetto la moderata non cambia il livello: resta Protetto finché la posa non trova i segnali sani.
    assertNull(r.signal(cool.copy(thermal = GlassThermal.Moderate)))
    assertNull("con la moderata non si risale", r.gesture(1.0))
    assertEquals(Protected, r.optics)
    r.signal(cool)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(1.0))
    assertEquals(Reduced, r.optics)
  }

  @Test fun unaDiscesaDaiFotogrammiChiedeAncoraIFotogrammi() {
    val r = run()
    r.policy.onWindow(r.now, true, true, 0, period)
    repeat(4) {
      r.now += period
      r.policy.onFrame(r.now, r.now, 2 * period, period, 2 * period, GlassFrameEvidence.MeasuredApp)
    }
    assertEquals(Reduced, r.optics)
    r.policy.onWindow(r.now, true, false, 0, period)
    r.wait(6)
    assertNull("un gesto corto non basta", r.gesture(2.0))
    assertEquals(Reduced, r.optics)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(31.0))
    assertEquals(Full, r.optics)
  }

  @Test fun fotogrammiESegnaliInsiemeOgnunoColSuoPasso() {
    val r = run()
    r.policy.onWindow(r.now, true, true, 0, period)
    repeat(4) {
      r.now += period
      r.policy.onFrame(r.now, r.now, 2 * period, period, 2 * period, GlassFrameEvidence.MeasuredApp)
    }
    r.policy.onWindow(r.now, true, false, 0, period)
    assertEquals(Reduced, r.optics)
    r.wait(1)
    r.signal(cool.copy(thermal = GlassThermal.SevereOrWorse))
    assertEquals(Protected, r.optics)
    r.wait(6)
    r.signal(cool)
    // La termica passata si recupera alla posa; il livello perso coi fotogrammi no.
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(1.0))
    assertEquals(Reduced, r.optics)
    r.wait(6)
    assertNull(r.gesture(1.0))
    assertEquals(Reduced, r.optics)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(31.0))
    assertEquals(Full, r.optics)
  }

  @Test fun unoSchermoFermoNonCambiaSottoGliOcchi() {
    val r = run()
    r.signal(cool.copy(lowMemory = true))
    assertEquals(Protected, r.optics)
    r.wait(60)
    // Letture sane per un minuto, nessun moto: la finestra si vede, e la risalita aspetta la posa.
    repeat(12) { r.wait(5); assertNull(r.signal(cool)) }
    assertEquals(Protected, r.optics)
  }

  @Test fun unaGpuDiPuntaNonDeveGuadagnarsiIlPienoColMoto() {
    // Il Tab S9: niente classe multimediale e, al primo avvio, nessun verdetto degli shader. Parte Ridotto (come
    // prima), ma il Pieno non va guadagnato coi trenta secondi: lo concede la prima posa sana. Fra Ridotto e Pieno a
    // riposo la cattura è la stessa (scarto zero in `F7p26WeakDeviceTest`): la risalita non si vede.
    val tablet = GlassDeviceCapacity(7_386_728L * 1024, 512 * mib, 512, false, 0, true, GlassGpuClass.High)
    val r = run(tablet, shader = null)
    assertEquals(Reduced, r.optics)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(0.5))
    assertEquals(Full, r.optics)
    // Una GPU sconosciuta invece se lo guadagna, come prima.
    val unknown = run(tablet.copy(recognizedGpuDriver = false, gpu = GlassGpuClass.Unknown), shader = null)
    assertNull(unknown.gesture(0.5))
    assertEquals(Reduced, unknown.optics)
    assertEquals(GlassQualityReason.SettledRecovery, unknown.gesture(31.0))
    assertEquals(Full, unknown.optics)
  }

  /**
   * F7.26g ([LowGpuSignalCeiling] a Protetto, la decisione del 27 novembre): una GPU bassa parte Protetta e se lo
   * guadagna col moto, a 120 Hz come a 60. La prima posa breve non cambia niente; trenta secondi buoni sì, un gradino.
   */
  @Test fun unaGpuBassaPartitaProtettaSeLoGuadagnaColMoto() {
    val a52s = GlassDeviceCapacity(5 * gib + 500 * mib, 256 * mib, 256, false, 0, false, GlassGpuClass.Low)
    val r = run(a52s)
    assertEquals(Protected, r.optics)
    assertNull("la partenza prudente non è una discesa da segnale", r.gesture(1.0))
    assertEquals(Protected, r.optics)
    assertEquals(GlassQualityReason.SettledRecovery, r.gesture(31.0))
    assertEquals(Reduced, r.optics)
  }
}
