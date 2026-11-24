@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.glass.quality.android.GlassVariantSignals
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** Robolectric riusa la JVM. Ogni Application del banco rappresenta un nuovo processo Android. */
internal class GlassBenchDevice : Application() {
  private var previousHandler: Thread.UncaughtExceptionHandler? = null

  override fun attachBaseContext(base: Context) {
    super.attachBaseContext(base)
    previousHandler = Thread.getDefaultUncaughtExceptionHandler()
    ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
    // Prima dei provider Startup e dell'Activity, quindi anche della prima decisione della finestra.
    val manager = shadowOf(getSystemService(ActivityManager::class.java)!!)
    manager.setIsLowRamDevice(false)
    manager.setMemoryClass(1024)
    manager.setMemoryInfo(ActivityManager.MemoryInfo().apply {
      totalMem = 8L shl 30
      availMem = 4L shl 30
      threshold = 128L shl 20
    })
  }

  override fun onTerminate() {
    try {
      super.onTerminate()
    } finally {
      ReflectionHelpers.setStaticField(FluidGlassResources::class.java, "process", null)
      Thread.setDefaultUncaughtExceptionHandler(previousHandler)
    }
  }
}

/** L'heap del dispositivo di misura è stabile anche se il worker Gradle ha appena eseguito altre classi.
 * L'adattatore originale applica le stesse forzature termiche e low-RAM della debug. */
@Implements(value = GlassVariantSignals::class, isInAndroidSdk = false)
internal class GlassBenchHeap {
  @Implementation
  protected fun capacity(real: GlassDeviceCapacity): GlassDeviceCapacity = Shadow.directlyOn(
    GlassVariantSignals, GlassVariantSignals::class.java, "capacity",
    ClassParameter.from(GlassDeviceCapacity::class.java, real.copy(maxHeapBytes = 1024L shl 20)),
  )

  @Implementation
  protected fun conditions(real: GlassConditions): GlassConditions = Shadow.directlyOn(
    GlassVariantSignals, GlassVariantSignals::class.java, "conditions",
    ClassParameter.from(GlassConditions::class.java, real.copy(heapFraction = 0.2)),
  )
}
