package dev.antigravity.fluidengine.ui.glass.quality

import dev.antigravity.fluidengine.foundation.memory.FluidMemoryLimits
import dev.antigravity.fluidengine.ui.glass.backdrop.FrostedSampleFloor
import dev.antigravity.fluidengine.ui.glass.backdrop.frostedSampleFloor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.18 · Il tetto del vetro per fascia di dispositivo, e cosa lo può abbassare.
 *
 * Sul Tab S9 (24 novembre 2026: `MemTotal` 7.386.728 kB, `dalvik.vm.heapgrowthlimit` 512m, nessuna classe multimediale,
 * nessun `low_ram`) la fascia è **Pieno**, 48 MiB di vetro. Il log del popover della capsula diceva 12 MiB: l'ottica
 * d'avvio (Ridotta, per la classe multimediale assente) e poi il primo rifiuto (Protetta) trascinavano giù la riga dei
 * tetti. Qui: la tabella delle fasce, e le sole cose che abbassano il tetto (pressione vera, termica severa, uscita per
 * memoria), mai il livello ottico.
 */
class GlassMemoryTierTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private val cool = GlassConditions(2 * gib, 256 * mib, false, 0.4, GlassThermal.Light, false)

  private data class Tier(
    val name: String, val totalRam: Long, val heapClassMiB: Int, val maxHeap: Long, val lowRam: Boolean,
    val profile: GlassMemoryProfile, val glassMiB: Double,
  )

  private fun glassBytes(policy: GlassQualityPolicy): Long {
    val row = when (policy.decision.memoryProfile) {
      GlassMemoryProfile.Full -> FluidMemoryLimits.Full
      GlassMemoryProfile.Reduced -> FluidMemoryLimits.Reduced
      GlassMemoryProfile.Protected -> FluidMemoryLimits.Protected
    }
    return row.forHeap(policy.decision.heapReferenceBytes).glassBytes
  }

  @Test fun latabellaDelleFasceDaiDueGigaAlTabletEAlTelefonoDaDodici() {
    val tiers = listOf(
      // Android Go / low-RAM (CDD 7.6.1): `isLowRamDevice`, 2 GB, heap 128 MiB.
      Tier("Go, 2 GB", 2 * gib - 200 * mib, 128, 128 * mib, true, GlassMemoryProfile.Protected, 12.0),
      // Go con heap 96 MiB: il quarto dell'heap (24 MiB) scala anche la riga Protetta, 9 MiB di vetro.
      Tier("Go, heap 96", 2 * gib - 200 * mib, 96, 96 * mib, true, GlassMemoryProfile.Protected, 9.0),
      // 4 GB (MemTotal ~3,6 GiB), heapgrowthlimit 256m: protetto per la RAM.
      Tier("4 GB", 3 * gib + 600 * mib, 256, 256 * mib, false, GlassMemoryProfile.Protected, 12.0),
      // 6 GB come l'A52s (MemTotal ~5,5 GiB), heapgrowthlimit 256m: ridotto.
      Tier("6 GB", 5 * gib + 500 * mib, 256, 256 * mib, false, GlassMemoryProfile.Reduced, 24.0),
      // 8 GB con heapgrowthlimit 256m (molti telefoni): ridotto per l'heap.
      Tier("8 GB, heap 256", 7 * gib + 300 * mib, 256, 256 * mib, false, GlassMemoryProfile.Reduced, 24.0),
      // Il Tab S9 SM-X710 misurato il 24 novembre 2026: pieno.
      Tier("Tab S9", 7_386_728L * 1024, 512, 512 * mib, false, GlassMemoryProfile.Full, 48.0),
      // 12 GB (MemTotal ~11 GiB), heapgrowthlimit 512m: pieno.
      Tier("12 GB", 11 * gib, 512, 512 * mib, false, GlassMemoryProfile.Full, 48.0),
    )
    for (tier in tiers) {
      // La classe multimediale assente e il driver sconosciuto, come sul Tab S9: l'ottica d'avvio è al massimo Ridotta.
      val device = GlassDeviceCapacity(tier.totalRam, tier.maxHeap, tier.heapClassMiB, tier.lowRam, 0, false)
      val policy = GlassQualityPolicy(device, cool, GlassOptics.Full, GlassOptics.Full)
      val glass = glassBytes(policy)
      println("F718TIER | ${tier.name} | ottica ${policy.decision.optics} | riga ${policy.decision.memoryProfile} | " +
        "vetro ${"%.1f".format(glass / mib.toDouble())} MiB")
      assertEquals(tier.name, tier.profile, policy.decision.memoryProfile)
      assertEquals(tier.name, (tier.glassMiB * mib).toLong(), glass)
    }
  }

  @Test fun ilRifiutoVisibileAbbassaLOtticaMaNonIlTetto() {
    val tablet = GlassDeviceCapacity(7_386_728L * 1024, 512 * mib, 512, false, 0, false)
    val policy = GlassQualityPolicy(tablet, cool, GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Reduced, policy.decision.optics)
    val before = glassBytes(policy)
    assertEquals(48 * mib, before)
    policy.setVisibleBudgetDenied(1_000L, true)
    assertEquals("il rifiuto chiede catture più piccole", GlassOptics.Protected, policy.decision.optics)
    assertEquals("il tetto resta della fascia", before, glassBytes(policy))
    policy.setVisibleBudgetDenied(2_000L, false)
    assertEquals(before, glassBytes(policy))
  }

  @Test fun fotogrammiInRitardoERisparmioNonToccanoIlTetto() {
    val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
    val policy = GlassQualityPolicy(capable, cool, GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Full, policy.decision.optics)
    val before = glassBytes(policy)
    policy.updateConditions(1_000L, cool.copy(powerSave = true))
    assertEquals(GlassOptics.Reduced, policy.decision.optics)
    assertEquals(before, glassBytes(policy))
    policy.updateConditions(2_000L, cool.copy(thermal = GlassThermal.Moderate))
    assertEquals(before, glassBytes(policy))
  }

  @Test fun pressioneVeraTermicaSeveraEUscitaPerMemoriaAbbassanoIlTetto() {
    val capable = GlassDeviceCapacity(8 * gib, 512 * mib, 512, false, 34, true)
    for (signals in listOf(cool.copy(lowMemory = true), cool.copy(heapFraction = 0.9),
      cool.copy(availableBytes = cool.pressureThresholdBytes), cool.copy(thermal = GlassThermal.SevereOrWorse))) {
      val policy = GlassQualityPolicy(capable, cool, GlassOptics.Full, GlassOptics.Full)
      policy.updateConditions(1_000L, signals)
      assertEquals(signals.toString(), GlassMemoryProfile.Protected, policy.decision.memoryProfile)
      policy.updateConditions(2_000L, cool)
      assertEquals("tolta la pressione, la riga aspetta la posa di recupero", GlassMemoryProfile.Protected,
        policy.decision.memoryProfile)
      // Trenta secondi di moto sano a 100 Hz e la posa: torna la fascia.
      val period = 10_000_000L
      var now = 3_000L
      policy.onWindow(now, true, true, 0, period)
      repeat(3_002) {
        now += period
        policy.onFrame(now, now, period / 2, period, period / 2, GlassFrameEvidence.MeasuredApp)
      }
      assertEquals(GlassQualityReason.SettledRecovery, policy.onWindow(now, true, false, 0, period))
      assertEquals("dopo il recupero, la fascia", GlassMemoryProfile.Full, policy.decision.memoryProfile)
    }
    // L'uscita precedente per memoria arriva come tetto della sessione (F7.11).
    val afterOom = GlassQualityPolicy(capable, cool, GlassOptics.Full, GlassOptics.Protected)
    assertEquals(GlassMemoryProfile.Protected, afterOom.decision.memoryProfile)
    assertTrue(glassBytes(afterOom) <= 12 * mib)
  }

  @Test fun laScalaMinimaDelCampioneSegueLaSfocatura() {
    // Il pannello flottante sopra lo scrim (6,25 dp a 420 dpi, raggio 16,4 px, sigma 10): un quarto di lato.
    assertEquals(0.25f, frostedSampleFloor(16.4f), 0.002f)
    // Sopra una pagina nitida (8,75 dp, raggio 23 px, sigma 13,8): un po' meno, e il passo a metà si ferma a 0,25.
    assertEquals(0.181f, frostedSampleFloor(23f), 0.002f)
    // Un vetro quasi limpido non scende sotto la metà di serie del viaggio.
    assertEquals(0.5f, frostedSampleFloor(2f), 0f)
    assertEquals(FrostedSampleFloor, frostedSampleFloor(null), 0f)
  }
}
