package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.PowerManager
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
import dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * F7.26 · Il segnale della GPU sul percorso Android vero: `Build.SOC_MANUFACTURER` e `Build.SOC_MODEL` letti una volta
 * nel costruttore del registro (prima del primo layer, come le altre letture di F7.11), mai più dopo, e la regola delle
 * pagine che ne segue. L'heap Java è quello fisso del dispositivo di prova ([RecoveryTestHeap]): il worker di Gradle
 * condiviso non deve far scattare la pressione.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RecoveryTestHeap::class])
class GlassSocSignalTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private var maker: String? = null
  private var model: String? = null
  private var crashHandler: Thread.UncaughtExceptionHandler? = null

  @Before fun newProcess() {
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    maker = Build.SOC_MANUFACTURER
    model = Build.SOC_MODEL
    crashHandler = Thread.getDefaultUncaughtExceptionHandler()
    val am = shadowOf(app.getSystemService(ActivityManager::class.java)!!)
    am.setIsLowRamDevice(false)
    am.setMemoryClass(512)
    am.setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 7L * 1024 * 1024 * 1024 + 300L * 1024 * 1024
      availMem = 3L shl 30
      threshold = 256L shl 20
    })
    shadowOf(app.getSystemService(PowerManager::class.java)!!).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
  }

  @After fun restore() {
    soc(maker, model)
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    Thread.setDefaultUncaughtExceptionHandler(crashHandler)
  }

  private fun soc(manufacturer: String?, name: String?) {
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", manufacturer)
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", name)
  }

  private fun FluidGlassResources.reading() = ReflectionHelpers.getField<GlassReading>(this, "reading")

  @Test fun laGpuDelloS22AccendeLePagineConLaStessaRigaDelTablet() {
    // Galaxy S22 americano: 8 GB, heap 512, Snapdragon 8 Gen 1.
    soc("QTI", "SM8450")
    val resources = FluidGlassResources.get(app)
    val reading = resources.reading()
    assertEquals(GlassGpuClass.Mid, reading.capacity.gpu)
    assertFalse("una GPU media non è «riconosciuta» per il Pieno d'avvio", reading.capacity.recognizedGpuDriver)
    assertEquals(GlassMemoryProfile.Full, resources.decision(null).memoryProfile)
    assertEquals(GlassOptics.Reduced, resources.decision(null).optics)
    assertTrue("S22: accese", resources.viewerHardwarePages)
  }

  @Test fun ilTabletEAltoELaLetturaSiFaUnaVoltaSola() {
    soc("QTI", "SM8550")
    val start = System.nanoTime()
    val resources = FluidGlassResources.get(app)
    val built = System.nanoTime() - start
    val signals = ReflectionHelpers.getField<AndroidGlassSignals>(resources, "signals")
    println("F726SOC | ${signals.soc} | registro costruito in ${built / 1000} µs")
    assertEquals(GlassGpuClass.High, resources.reading().capacity.gpu)
    assertTrue(resources.reading().capacity.recognizedGpuDriver)
    assertEquals(GlassMemoryProfile.Full, resources.decision(null).memoryProfile)
    assertFalse("Tab S9: spente", resources.viewerHardwarePages)
    // Il nome cambia dopo (non succede su un telefono vero): le letture successive non lo rileggono.
    soc("Samsung", "s5e8825")
    resources.refresh()
    resources.refreshBeforeAllocation()
    assertEquals(GlassGpuClass.High, resources.reading().capacity.gpu)
    assertFalse(resources.viewerHardwarePages)
  }

  @Test fun unSocSconosciutoPartePrudente() {
    soc("unknown", "unknown")
    val resources = FluidGlassResources.get(app)
    // Il Robolectric dice «unknown» anche per la piattaforma: nessuna classe.
    val gpu = resources.reading().capacity.gpu
    println("F726SOC | sconosciuto | board=${Build.BOARD} hardware=${Build.HARDWARE} gpu=$gpu")
    assertEquals(GlassGpuClass.Unknown, gpu)
    assertEquals(GlassOptics.Reduced, resources.decision(null).optics)
    assertTrue("nel dubbio accese, finché il vetro non regge il Pieno", resources.viewerHardwarePages)
  }
}
