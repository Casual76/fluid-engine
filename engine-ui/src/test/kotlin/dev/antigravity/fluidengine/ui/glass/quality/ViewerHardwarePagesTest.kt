package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.ui.glass.quality.android.viewerHardwarePagesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.18b · Le pagine dei documenti nella memoria grafica per fascia del dispositivo ([viewerHardwarePagesFor]): spente sui
 * dispositivi alti (il Tab S9 e il Galaxy S25 di Alessio), accese sui più deboli, con la fascia e il livello ottico che il
 * vetro calcola già (F7). E il limite detto nel KDoc: 8 GB con heap 512 MiB (il Galaxy S22) conta come alto.
 */
class ViewerHardwarePagesTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)

  private fun pages(policy: GlassQualityPolicy) =
    viewerHardwarePagesFor(policy.decision.memoryProfile, policy.decision.optics)

  @Test fun laRegolaDellaRigaEDelVetro() {
    for (memory in GlassMemoryProfile.entries) for (optics in GlassOptics.entries) {
      val expected = memory != GlassMemoryProfile.Full || optics == GlassOptics.Protected
      assertEquals("$memory / $optics", expected, viewerHardwarePagesFor(memory, optics))
    }
  }

  @Test fun spenteSuiDispositiviAltiAcceseSuiPiuDeboli() {
    val devices = listOf(
      // Il Tab S9 misurato il 24 novembre 2026: fascia Piena, vetro d'avvio Ridotto (nessuna classe multimediale).
      Triple("Tab S9", GlassDeviceCapacity(7_386_728L * 1024, 512 * mib, 512, false, 0, false), false),
      // Un telefono alto da 12 GB come il Galaxy S25: Pieno.
      Triple("12 GB", GlassDeviceCapacity(11 * gib, 512 * mib, 512, false, 34, true), false),
      // Un serie A da 6 GB (heap 256): Ridotto.
      Triple("6 GB", GlassDeviceCapacity(5 * gib + 500 * mib, 256 * mib, 256, false, 0, false), true),
      // 8 GB con heap 256 (molti telefoni): Ridotto per l'heap.
      Triple("8 GB, heap 256", GlassDeviceCapacity(7 * gib + 300 * mib, 256 * mib, 256, false, 0, false), true),
      // 4 GB e Android Go: Protetto.
      Triple("4 GB", GlassDeviceCapacity(3 * gib + 600 * mib, 256 * mib, 256, false, 0, false), true),
      Triple("Go, 2 GB", GlassDeviceCapacity(2 * gib - 200 * mib, 128 * mib, 128, true, 0, false), true),
      // Una lettura incompleta (heap sconosciuto): Ridotto, nel dubbio accese.
      Triple("lettura incompleta", GlassDeviceCapacity(8 * gib, 0, 0, null, 0, false), true),
      // Il limite: 8 GB e heap 512 MiB (il Galaxy S22) conta come alto finché il vetro non scende a Protetto.
      Triple("8 GB, heap 512", GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 0, false), false),
    )
    for ((name, device, expected) in devices) {
      val policy = GlassQualityPolicy(device, cool, GlassOptics.Full, GlassOptics.Full)
      println("F818BHW | $name | riga ${policy.decision.memoryProfile} | ottica ${policy.decision.optics} | " +
        "pagine ${if (pages(policy)) "accese" else "spente"}")
      assertEquals(name, expected, pages(policy))
    }
  }

  @Test fun unDispositivoAltoCheFaticaLeAccende() {
    val tablet = GlassDeviceCapacity(7_386_728L * 1024, 512 * mib, 512, false, 0, false)
    // La termica severa porta riga e vetro a Protetto.
    val hot = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    assertFalse(pages(hot))
    hot.updateConditions(1_000L, cool.copy(thermal = GlassThermal.SevereOrWorse))
    assertTrue(pages(hot))
    // Un rifiuto visibile porta il vetro a Protetto e lascia la riga Piena: accese finché il vetro non risale.
    val denied = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    denied.setVisibleBudgetDenied(1_000L, true)
    assertEquals(GlassMemoryProfile.Full, denied.decision.memoryProfile)
    assertTrue(pages(denied))
    // Il risparmio energetico abbassa il vetro solo a Ridotto: restano spente.
    val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
    val saving = GlassQualityPolicy(capable, cool, GlassOptics.Full, GlassOptics.Full)
    saving.updateConditions(1_000L, cool.copy(powerSave = true))
    assertEquals(GlassOptics.Reduced, saving.decision.optics)
    assertFalse(pages(saving))
  }
}
