package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.ui.glass.quality.android.viewerHardwarePagesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.18b · Le pagine dei documenti nella memoria grafica per fascia del dispositivo ([viewerHardwarePagesFor]): spente sui
 * dispositivi alti (il Tab S9 e il Galaxy S25 di Alessio), accese sui più deboli, con la fascia e il livello ottico che il
 * vetro calcola già (F7).
 *
 * F7.26 · Con la classe della GPU ([GlassGpuClass]) letta dal nome vero del SoC: il Galaxy S22 (8 GB, heap 512 MiB, la
 * stessa riga di memoria del Tab S9) ora ha le pagine **accese**, come voleva Alessio. Uno sconosciuto parte acceso e le
 * spegne quando il vetro, coi fotogrammi misurati, arriva a Pieno.
 */
class ViewerHardwarePagesTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)

  private fun pages(policy: GlassQualityPolicy, gpu: GlassGpuClass) =
    viewerHardwarePagesFor(policy.decision.memoryProfile, policy.decision.optics, gpu)

  private fun device(ram: Long, heapMiB: Int, lowRam: Boolean?, mpc: Int, maker: String?, soc: String?): GlassDeviceCapacity {
    val gpu = GlassGpuTable.classify(maker, soc)
    return GlassDeviceCapacity(ram, heapMiB * mib, heapMiB, lowRam, mpc, gpu == GlassGpuClass.High, gpu)
  }

  @Test fun laRegolaDellaRigaDelVetroEDellaGpu() {
    for (memory in GlassMemoryProfile.entries) for (optics in GlassOptics.entries) for (gpu in GlassGpuClass.entries) {
      val strong = gpu == GlassGpuClass.High || gpu == GlassGpuClass.Unknown && optics == GlassOptics.Full
      val expected = memory != GlassMemoryProfile.Full || optics == GlassOptics.Protected || !strong
      assertEquals("$memory / $optics / $gpu", expected, viewerHardwarePagesFor(memory, optics, gpu))
    }
    // Con una GPU di punta la regola è quella di F8.18b, uguale a prima.
    for (memory in GlassMemoryProfile.entries) for (optics in GlassOptics.entries) {
      assertEquals(memory != GlassMemoryProfile.Full || optics == GlassOptics.Protected,
        viewerHardwarePagesFor(memory, optics, GlassGpuClass.High))
    }
  }

  @Test fun spenteSuiDispositiviAltiAcceseSuiPiuDeboli() {
    data class Row(val name: String, val device: GlassDeviceCapacity, val pages: Boolean)
    val devices = listOf(
      // Il Tab S9 misurato il 24 novembre 2026 (SM-X710): fascia Piena, vetro d'avvio Ridotto, Snapdragon 8 Gen 2.
      Row("Tab S9", device(7_386_728L * 1024, 512, false, 0, "QTI", "SM8550"), false),
      // Il Galaxy S25 (12 GB, Snapdragon 8 Elite).
      Row("S25", device(11 * gib, 512, false, 34, "QTI", "SM8750"), false),
      // Il Galaxy S22 (8 GB, heap 512): la stessa riga di memoria del Tab S9. L'europeo ha l'Exynos 2200, l'americano lo
      // Snapdragon 8 Gen 1. Fino a F7.26 aveva le pagine spente (il limite di F8.18b).
      Row("S22, Exynos 2200", device(7 * gib + 300 * mib, 512, false, 0, "Samsung", "s5e9925"), true),
      Row("S22, 8 Gen 1", device(7 * gib + 300 * mib, 512, false, 0, "QTI", "SM8450"), true),
      // Il Galaxy A52s (Snapdragon 778G), da 6 e da 8 GB, heap 256.
      Row("A52s, 6 GB", device(5 * gib + 500 * mib, 256, false, 0, "QTI", "SM7325"), true),
      Row("A52s, 8 GB", device(7 * gib + 300 * mib, 256, false, 0, "QTI", "SM7325"), true),
      // Una serie A con l'Exynos (A54, 1380) e un telefono da 8 GB con heap 512 e una GPU media: accese per la GPU.
      Row("A54", device(5 * gib + 500 * mib, 256, false, 0, "Samsung", "s5e8835"), true),
      Row("8 GB, heap 512, GPU media", device(7 * gib + 300 * mib, 512, false, 0, "QTI", "SM7450"), true),
      // 4 GB e Android Go: Protetto.
      Row("4 GB", device(3 * gib + 600 * mib, 256, false, 0, "Samsung", "s5e8825"), true),
      Row("Go, 2 GB", device(2 * gib - 200 * mib, 128, true, 0, "QTI", "SM4250"), true),
      // Una lettura incompleta (heap sconosciuto): Ridotto, nel dubbio accese.
      Row("lettura incompleta", GlassDeviceCapacity(8 * gib, 0, 0, null, 0, false), true),
      // Un SoC che la tabella non conosce, con tanta memoria: parte prudente (vedi sotto per la risalita).
      Row("sconosciuto, 12 GB", device(11 * gib, 512, false, 0, "Acme", "X1000"), true),
    )
    for ((name, device, expected) in devices) {
      val policy = GlassQualityPolicy(device, cool, GlassOptics.Full, GlassOptics.Full)
      println("F726HW | $name | gpu ${device.gpu} | riga ${policy.decision.memoryProfile} | " +
        "ottica ${policy.decision.optics} | pagine ${if (pages(policy, device.gpu)) "accese" else "spente"}")
      assertEquals(name, expected, pages(policy, device.gpu))
    }
  }

  /** Trenta secondi di moto sano a 100 Hz e la posa: il vetro sale di un livello. */
  private fun recover(policy: GlassQualityPolicy, start: Long): Long {
    val period = 10_000_000L
    var now = start + 6_000_000_000L
    policy.onWindow(now, true, true, 0, period)
    repeat(3_002) {
      now += period
      policy.onFrame(now, now, period / 2, period, period / 2, GlassFrameEvidence.MeasuredApp)
    }
    policy.onWindow(now, true, false, 0, period)
    return now
  }

  /** Tre fotogrammi in ritardo su quattro, in moto: il vetro scende di un livello (F7.10). */
  private fun stutter(policy: GlassQualityPolicy, start: Long): Long {
    val period = 10_000_000L
    var now = start + 1_000_000L
    policy.onWindow(now, true, true, 0, period)
    repeat(4) {
      now += period
      policy.onFrame(now, now, period * 2, period, period * 2, GlassFrameEvidence.MeasuredApp)
    }
    policy.onWindow(now, true, false, 0, period)
    return now
  }

  @Test fun unoSconosciutoParteAccesoELeSpegneSeRegge() {
    val unknown = device(11 * gib, 512, false, 0, "Acme", "X1000")
    val policy = GlassQualityPolicy(unknown, cool, GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Reduced, policy.decision.optics)
    assertTrue(pages(policy, unknown.gpu))
    recover(policy, 0)
    assertEquals("regge: il vetro arriva a Pieno", GlassOptics.Full, policy.decision.optics)
    assertFalse("e le pagine si spengono", pages(policy, unknown.gpu))
    // Poi scatta: il vetro scende e le pagine tornano accese.
    stutter(policy, 100_000_000_000L)
    assertEquals(GlassOptics.Reduced, policy.decision.optics)
    assertTrue(pages(policy, unknown.gpu))
  }

  @Test fun unDispositivoAltoCheFaticaLeAccende() {
    val tablet = device(7_386_728L * 1024, 512, false, 0, "QTI", "SM8550")
    // La termica severa porta riga e vetro a Protetto.
    val hot = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    assertFalse(pages(hot, tablet.gpu))
    hot.updateConditions(1_000L, cool.copy(thermal = GlassThermal.SevereOrWorse))
    assertTrue(pages(hot, tablet.gpu))
    // Un rifiuto visibile porta il vetro a Protetto e lascia la riga Piena: accese finché il vetro non risale.
    val denied = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    denied.setVisibleBudgetDenied(1_000L, true)
    assertEquals(GlassMemoryProfile.Full, denied.decision.memoryProfile)
    assertTrue(pages(denied, tablet.gpu))
    // Il risparmio energetico abbassa il vetro solo a Ridotto: restano spente.
    val s25 = device(11 * gib, 512, false, 34, "QTI", "SM8750")
    val saving = GlassQualityPolicy(s25, cool, GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Full, saving.decision.optics)
    saving.updateConditions(1_000L, cool.copy(powerSave = true))
    assertEquals(GlassOptics.Reduced, saving.decision.optics)
    assertFalse(pages(saving, s25.gpu))
    // F7.26 · Un tablet che scatta scende comunque, anche con la GPU di punta: una serie di ritardi lo porta da
    // Ridotto a Protetto, e le pagine si accendono.
    val jank = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    var now = stutter(jank, 0)
    assertEquals(GlassOptics.Protected, jank.decision.optics)
    assertTrue(pages(jank, tablet.gpu))
    // E risale alla posa: Ridotto, pagine di nuovo spente.
    now = recover(jank, now)
    assertEquals(GlassOptics.Reduced, jank.decision.optics)
    assertFalse(pages(jank, tablet.gpu))
  }
}
