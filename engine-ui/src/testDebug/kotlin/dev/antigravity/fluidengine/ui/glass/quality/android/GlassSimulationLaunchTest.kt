package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
import dev.antigravity.fluidengine.ui.glass.quality.GlassMemoryProfile
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.26 · Il profilo simulato arriva con l'extra `fluid.glass.device` della prima Activity, **prima del suo contenuto**:
 * quello che l'Activity compone dopo `super.onCreate` trova già la riga del telefono simulato. Una seconda Activity con
 * un altro profilo non lo cambia; senza extra, o con un extra sbagliato, resta il dispositivo vero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, shadows = [RecoveryTestHeap::class])
class GlassSimulationLaunchTest {
  private val app: Application get() = RuntimeEnvironment.getApplication()
  private val mib = 1_048_576L
  private var maker: String? = null
  private var model: String? = null
  private var crashHandler: Thread.UncaughtExceptionHandler? = null

  /** Ciò che il contenuto vedrebbe: il tetto del vetro e le pagine, letti subito dopo `super.onCreate`. */
  class Probe : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
      super.onCreate(savedInstanceState)
      val resources = FluidGlassResources.get(this)
      glassLimit = resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes
      pages = resources.viewerHardwarePages
    }

    companion object {
      var glassLimit = -1L
      var pages: Boolean? = null
    }
  }

  @Before fun tablet() {
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    GlassVariantSignals.simulation = null
    maker = Build.SOC_MANUFACTURER
    model = Build.SOC_MODEL
    crashHandler = Thread.getDefaultUncaughtExceptionHandler()
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", "QTI")
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", "SM8550")
    val am = shadowOf(app.getSystemService(ActivityManager::class.java)!!)
    am.setIsLowRamDevice(false)
    am.setMemoryClass(512)
    am.setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 7_386_728L * 1024
      availMem = 1_466_159_104L
      threshold = 226_492_416L
    })
    shadowOf(app.getSystemService(PowerManager::class.java)!!).setCurrentThermalStatus(PowerManager.THERMAL_STATUS_NONE)
    Probe.glassLimit = -1L
    Probe.pages = null
  }

  @After fun restore() {
    GlassVariantSignals.simulation = null
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MANUFACTURER", maker)
    ReflectionHelpers.setStaticField(Build::class.java, "SOC_MODEL", model)
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    Thread.setDefaultUncaughtExceptionHandler(crashHandler)
  }

  private fun launch(extra: String?) = Robolectric.buildActivity(Probe::class.java,
    Intent(app, Probe::class.java).apply { if (extra != null) putExtra("fluid.glass.device", extra) })

  @Test fun loA52sPrimaDelContenutoDellActivity() {
    val resources = FluidGlassResources.get(app)
    assertEquals("il tablet vero: 48 MiB di vetro", 48 * mib, resources.budget.usage(FluidMemoryCategory.GLASS).limitBytes)
    assertFalse(resources.viewerHardwarePages)
    val first = launch("a52s").create()
    assertEquals("il contenuto trova già la riga Ridotta dell'A52s", 24 * mib, Probe.glassLimit)
    assertEquals(64 * mib, resources.budget.usage().limitBytes)
    assertEquals(true, Probe.pages)
    val reading = ReflectionHelpers.getField<GlassReading>(resources, "reading")
    assertEquals(GlassGpuClass.Low, reading.capacity.gpu)
    assertEquals(256 * mib, reading.capacity.heapReferenceBytes)
    first.start()
    val window = first.get().window
    assertEquals(GlassOptics.Protected, resources.decision(window).optics)
    assertEquals(GlassMemoryProfile.Reduced, resources.decision(window).memoryProfile)
    assertTrue(ShadowLog.getLogsForTag(GlassVariantSignals.SimTag).any { "profilo applicato" in it.msg })
    // Una seconda Activity con un altro profilo non cambia il processo.
    launch("s25").create()
    assertEquals(24 * mib, Probe.glassLimit)
    assertEquals("a52s", GlassVariantSignals.simulation?.label)
    first.pause().stop().destroy()
  }

  @Test fun senzaExtraOConUnExtraSbagliatoRestaIlTablet() {
    val resources = FluidGlassResources.get(app)
    launch(null).create()
    assertNull(GlassVariantSignals.simulation)
    assertEquals(48 * mib, Probe.glassLimit)
    launch("telefono-lento").create()
    assertNull(GlassVariantSignals.simulation)
    assertEquals(48 * mib, Probe.glassLimit)
    assertEquals(false, Probe.pages)
    assertEquals(GlassGpuClass.High, ReflectionHelpers.getField<GlassReading>(resources, "reading").capacity.gpu)
  }

  @Test fun ilCaloreAFasiScendeESiTogliePerLaRisalita() {
    val resources = FluidGlassResources.get(app)
    val activity = launch("tabs9,thermal=severe@0-30").create().start()
    val window = activity.get().window
    assertEquals(GlassOptics.Protected, resources.decision(window).optics)
    assertFalse(resources.prefetchAllowed.value)
    assertTrue("la termica severa accende le pagine anche sul tablet", resources.viewerHardwarePages)
    activity.pause().stop().destroy()
  }
}
