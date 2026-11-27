package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * F7.26 · La classe della GPU dal nome vero del SoC ([GlassGpuTable]), e cosa cambia nella politica F7.10: solo il vetro
 * d'avvio di una GPU debole e la regola delle pagine. La riga di memoria resta della memoria.
 */
class GlassGpuClassTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)

  @Test fun iNomiVeriDeiDispositiviTipo() {
    val cases = listOf(
      // Tab S9 SM-X710, letti nel logcat del tablet il 25 novembre 2026: ro.soc.model SM8550, ro.board.platform kalama.
      Triple("QTI", "SM8550", GlassGpuClass.High),
      Triple("QTI", "SM8650", GlassGpuClass.High), // 8 Gen 3
      Triple("QTI", "SM8750", GlassGpuClass.High), // 8 Elite, Galaxy S25
      Triple("QTI", "SM8850", GlassGpuClass.High), // la punta dopo, per la regola delle cifre
      Triple("QTI", "SM8450", GlassGpuClass.Mid), // 8 Gen 1, Galaxy S22 americano
      Triple("QTI", "SM8475", GlassGpuClass.Mid), // 8+ Gen 1
      Triple("QTI", "SM8350", GlassGpuClass.Mid), // 888
      Triple("QTI", "SM8635", GlassGpuClass.Mid), // 8s Gen 3
      Triple("QTI", "SM7325", GlassGpuClass.Low), // 778G, Galaxy A52s
      Triple("QTI", "SM7225", GlassGpuClass.Low), // 750G, A52 5G
      Triple("QTI", "SM6375", GlassGpuClass.Low), // 695
      Triple("QTI", "SM4250", GlassGpuClass.Low),
      Triple("QTI", "SDM845", GlassGpuClass.Low),
      Triple("Samsung", "s5e9925", GlassGpuClass.Mid), // Exynos 2200, Galaxy S22 europeo
      Triple("Samsung", "s5e9945", GlassGpuClass.High), // Exynos 2400
      Triple("Samsung", "s5e9955", GlassGpuClass.High), // Exynos 2500
      Triple("Samsung", "s5e9840", GlassGpuClass.Mid), // Exynos 2100
      Triple("Samsung", "s5e8835", GlassGpuClass.Low), // Exynos 1380, A54
      Triple("Samsung", "s5e8825", GlassGpuClass.Low), // Exynos 1280, A53 e A33
      Triple("Samsung", "s5e3830", GlassGpuClass.Low), // Exynos 850
      Triple("Mediatek", "MT6989", GlassGpuClass.High), // Dimensity 9300
      Triple("Mediatek", "MT6983W", GlassGpuClass.Mid), // Dimensity 9000, col suffisso
      Triple("Mediatek", "MT6877V/TTZA", GlassGpuClass.Low), // Dimensity 1080
      Triple("Google", "GS201", GlassGpuClass.Mid), // Tensor G2
      Triple("Google", "Tensor G4", GlassGpuClass.Mid),
      Triple("Google", "unknown", GlassGpuClass.Mid),
    )
    for ((maker, model, expected) in cases) assertEquals("$maker/$model", expected, GlassGpuTable.classify(maker, model))
  }

  @Test fun senzaNomeOConUnNomeNuovoESconosciuto() {
    assertEquals(GlassGpuClass.Unknown, GlassGpuTable.classify(null, null))
    assertEquals(GlassGpuClass.Unknown, GlassGpuTable.classify("unknown", "unknown", "unknown", "unknown"))
    assertEquals(GlassGpuClass.Unknown, GlassGpuTable.classify("", " ", null, null))
    assertEquals(GlassGpuClass.Unknown, GlassGpuTable.classify("Acme", "X1000", "goldfish", "ranchu"))
    // L'Hardware dei Samsung con lo Snapdragon dice solo «qcom»: non decide.
    assertEquals(GlassGpuClass.Unknown, GlassGpuTable.classify(null, null, "qcom", "qcom"))
  }

  @Test fun senzaSocModelDecidonoLaPiattaformaEIlNomeDelChip() {
    // Un dispositivo aggiornato da Android 11 che non ha ro.soc.model.
    assertEquals(GlassGpuClass.High, GlassGpuTable.classify("unknown", "unknown", "kalama", "qcom"))
    assertEquals(GlassGpuClass.Mid, GlassGpuTable.classify(null, null, "taro", "qcom"))
    assertEquals(GlassGpuClass.Mid, GlassGpuTable.classify(null, null, "lahaina", "qcom"))
    assertEquals(GlassGpuClass.Low, GlassGpuTable.classify(null, null, "yupik", "qcom"))
    assertEquals(GlassGpuClass.Mid, GlassGpuTable.classify(null, null, "exynos990", "exynos990"))
    assertEquals(GlassGpuClass.Mid, GlassGpuTable.classify(null, null, "s5e9925", "s5e9925"))
    assertEquals(GlassGpuClass.Low, GlassGpuTable.classify(null, null, "universal9611", "exynos9611"))
    assertEquals(GlassGpuClass.Low, GlassGpuTable.classify(null, null, "k6877v1_64", "mt6877"))
    // Il modello decide prima della piattaforma.
    assertEquals(GlassGpuClass.Low, GlassGpuTable.classify("QTI", "SM7325", "lahaina", "qcom"))
  }

  @Test fun unaGpuDeboleParteProtettaMaLaRigaRestaDellaMemoria() {
    fun policy(gpu: GlassGpuClass, ram: Long = 7 * gib + 300 * mib, heapMiB: Int = 512) = GlassQualityPolicy(
      GlassDeviceCapacity(ram, heapMiB * mib, heapMiB, false, 0, gpu == GlassGpuClass.High, gpu), cool,
      GlassOptics.Full, GlassOptics.Full)
    // 8 GB, heap 512: la riga è Piena per tutte le classi.
    for (gpu in GlassGpuClass.entries) assertEquals(gpu.name, GlassMemoryProfile.Full, policy(gpu).decision.memoryProfile)
    assertEquals(GlassOptics.Protected, policy(GlassGpuClass.Low).decision.optics)
    assertEquals(GlassOptics.Reduced, policy(GlassGpuClass.Mid).decision.optics)
    assertEquals(GlassOptics.Reduced, policy(GlassGpuClass.Unknown).decision.optics)
    assertEquals("senza classe multimediale, come il Tab S9", GlassOptics.Reduced, policy(GlassGpuClass.High).decision.optics)
    // L'A52s da 6 GB: riga Ridotta (64 MiB, 24 di vetro, dentro un quarto dell'heap da 256), vetro Protetto.
    val a52s = policy(GlassGpuClass.Low, ram = 5 * gib + 500 * mib, heapMiB = 256)
    assertEquals(GlassMemoryProfile.Reduced, a52s.decision.memoryProfile)
    assertEquals(GlassOptics.Protected, a52s.decision.optics)
    assertEquals(24 * mib, FluidMemoryLimits.Reduced.forHeap(a52s.decision.heapReferenceBytes).glassBytes)
  }

  @Test fun unaGpuDeboleRisaleSeRegge() {
    val low = GlassDeviceCapacity(5 * gib + 500 * mib, 256 * mib, 256, false, 0, false, GlassGpuClass.Low)
    val policy = GlassQualityPolicy(low, cool, GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Protected, policy.decision.optics)
    val period = 8_333_333L
    var now = 6_000_000_000L
    fun calm() {
      policy.onWindow(now, true, true, 0, period)
      repeat(3_700) {
        now += period
        policy.onFrame(now, now, period / 2, period, period / 2, GlassFrameEvidence.MeasuredApp)
      }
      policy.onWindow(now, true, false, 0, period)
      now += 6_000_000_000L
    }
    calm()
    assertEquals("trenta secondi buoni e la posa: un livello", GlassOptics.Reduced, policy.decision.optics)
    assertEquals(GlassQualityReason.SettledRecovery, policy.lastReason)
    calm()
    assertEquals(GlassOptics.Full, policy.decision.optics)
    assertEquals("la riga non si muove con l'ottica", GlassMemoryProfile.Reduced, policy.decision.memoryProfile)
  }
}
