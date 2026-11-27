package dev.antigravity.fluidengine.ui.glass.quality.android

import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
import dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F7.26 · Il profilo di dispositivo simulato delle build di prova ([GlassSimulation]), dall'extra `fluid.glass.device`:
 * la grammatica, i dispositivi tipo sopra il Tab S9 vero, le fasi a tempo.
 */
class GlassSimulationTest {
  private val mib = 1_048_576L
  private val gib = 1024 * mib
  private var clock = 1_000L
  private val tick = { clock }
  /** La lettura vera del Tab S9 (24 novembre 2026). */
  private val tablet = GlassDeviceCapacity(7_386_728L * 1024, 512 * mib, 512, false, 0, true, GlassGpuClass.High)
  private val cool = GlassConditions(1_466_159_104L, 226_492_416L, false, 0.2, GlassThermal.None, false)

  @Test fun loA52sSopraIlTablet() {
    val sim = checkNotNull(GlassSimulation.parse("a52s", tick))
    val device = sim.capacity(tablet)
    assertEquals((5.5 * gib).toLong(), device.totalRamBytes)
    assertEquals(256 * mib, device.maxHeapBytes)
    assertEquals(256, device.heapClassMiB)
    assertEquals(GlassGpuClass.Low, device.gpu)
    assertFalse(device.recognizedGpuDriver)
    assertEquals(GlassMemoryProfile.Reduced, device.memoryCeiling)
    val policy = GlassQualityPolicy(device, sim.conditions(cool), GlassOptics.Full, GlassOptics.Full)
    assertEquals(GlassOptics.Protected, policy.decision.optics)
    assertEquals(256 * mib, policy.decision.heapReferenceBytes)
    assertTrue(viewerHardwarePagesFor(policy.decision.memoryProfile, policy.decision.optics, device.gpu))
  }

  @Test fun iDispositiviTipo() {
    val expected = mapOf(
      "tabs9" to Triple(GlassGpuClass.High, GlassMemoryProfile.Full, false),
      "s25" to Triple(GlassGpuClass.High, GlassMemoryProfile.Full, false),
      "s22" to Triple(GlassGpuClass.Mid, GlassMemoryProfile.Full, true),
      "a52s" to Triple(GlassGpuClass.Low, GlassMemoryProfile.Reduced, true),
      "a52s8" to Triple(GlassGpuClass.Low, GlassMemoryProfile.Reduced, true),
      "4gb" to Triple(GlassGpuClass.Low, GlassMemoryProfile.Protected, true),
      "go" to Triple(GlassGpuClass.Low, GlassMemoryProfile.Protected, true),
      "ignoto" to Triple(GlassGpuClass.Unknown, GlassMemoryProfile.Full, true),
    )
    for ((name, want) in expected) {
      val device = checkNotNull(GlassSimulation.parse(name, tick)).capacity(tablet)
      val policy = GlassQualityPolicy(device, cool, GlassOptics.Full, GlassOptics.Full)
      val pages = viewerHardwarePagesFor(policy.decision.memoryProfile, policy.decision.optics, device.gpu)
      println("F726SIM | $name | gpu ${device.gpu} | riga ${policy.decision.memoryProfile} | ottica ${policy.decision.optics} | pagine $pages")
      assertEquals(name, want, Triple(device.gpu, policy.decision.memoryProfile, pages))
    }
    // «vero» non cambia niente: accende solo le righe.
    assertEquals(tablet, checkNotNull(GlassSimulation.parse("vero", tick)).capacity(tablet))
  }

  @Test fun leFasiATempo() {
    val sim = checkNotNull(GlassSimulation.parse("a52s,load=160@20-50,thermal=severe@70-90,late@30-40,thermal=moderate@90-", tick))
    assertEquals("a52s", sim.label)
    assertEquals(listOf(20_000L, 30_000L, 40_000L, 50_000L, 70_000L, 90_000L), sim.boundaries)
    fun at(seconds: Double) { clock = 1_000L + (seconds * 1000).toLong() }
    at(0.0)
    assertEquals(0, sim.gpuLoadNow())
    assertNull(sim.thermalNow())
    assertFalse(sim.lateNow())
    at(25.0)
    assertEquals(160, sim.gpuLoadNow())
    at(35.0)
    assertTrue(sim.lateNow())
    at(75.0)
    assertEquals(0, sim.gpuLoadNow())
    assertEquals(GlassThermal.SevereOrWorse, sim.thermalNow())
    assertEquals(GlassThermal.SevereOrWorse, sim.conditions(cool).thermal)
    at(95.0)
    assertEquals(GlassThermal.Moderate, sim.thermalNow())
    // La RAM disponibile non supera un terzo di quella simulata; quella vera del tablet è già sotto.
    assertEquals(cool.availableBytes, sim.conditions(cool).availableBytes)
    val go = checkNotNull(GlassSimulation.parse("go", tick))
    assertEquals((1.8 * gib).toLong() / 3, go.conditions(cool.copy(availableBytes = 4 * gib)).availableBytes)
  }

  @Test fun suMisuraEScritteSbagliate() {
    val custom = checkNotNull(GlassSimulation.parse("ram=3.5,heap=192,lowram=1,soc=SM6375", tick))
    assertEquals("su misura", custom.label)
    val device = custom.capacity(tablet)
    assertEquals(GlassGpuClass.Low, device.gpu)
    assertEquals(true, device.lowRamDevice)
    assertEquals(192 * mib, device.maxHeapBytes)
    assertEquals("a52s+", checkNotNull(GlassSimulation.parse("a52s,gpu=mid", tick)).label)
    assertEquals(GlassGpuClass.Mid, checkNotNull(GlassSimulation.parse("a52s,gpu=mid", tick)).capacity(tablet).gpu)
    // L'heap non supera mai quello vero: ART non dà di più.
    assertEquals(256 * mib, checkNotNull(GlassSimulation.parse("s22", tick)).capacity(tablet.copy(maxHeapBytes = 256 * mib)).maxHeapBytes)
    // La sorgente senza texture, per la domanda 3: solo se chiesta.
    assertFalse(checkNotNull(GlassSimulation.parse("a52s", tick)).flatSource)
    assertTrue(checkNotNull(GlassSimulation.parse("a52s,fonte=piatta", tick)).flatSource)
    assertEquals("a52s", checkNotNull(GlassSimulation.parse("a52s,fonte=piatta", tick)).label)
    assertFalse(checkNotNull(GlassSimulation.parse("a52s,fonte=texture", tick)).flatSource)
    // F7.26g: il registro del vetro esaurito a tempo, per il gradino più basso e il suo ritorno; non cambia il nome.
    val full = checkNotNull(GlassSimulation.parse("vero,pieno@0-12", tick))
    assertEquals("vero", full.label)
    assertTrue(full.exhaustNow())
    assertEquals(listOf(12_000L), full.boundaries)
    assertFalse(checkNotNull(GlassSimulation.parse("vero", tick)).exhaustNow())
    // F7.26d: la diagnostica dei voti per fotogramma, solo se chiesta; non cambia il nome del profilo.
    assertFalse(checkNotNull(GlassSimulation.parse("a52s", tick)).everyFrame)
    assertTrue(checkNotNull(GlassSimulation.parse("a52s,voti=ogni", tick)).everyFrame)
    assertEquals("a52s", checkNotNull(GlassSimulation.parse("a52s,voti=ogni", tick)).label)
    assertFalse(checkNotNull(GlassSimulation.parse("vero,voti=finestre", tick)).everyFrame)
    for (bad in listOf("", "  ", "boh", "a52s,heap=tanta", "a52s,thermal=caldo", "a52s,load=10@30-20", "a52s,colore=blu",
      "a52s,fonte=vetro", "a52s,voti=tutti")) {
      assertNull(bad, GlassSimulation.parse(bad, tick))
    }
  }
}
