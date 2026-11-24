package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.annotation.RequiresApi
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsBaseShaders
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal
import java.io.File

/** Lettura sincrona ai confini di vita e di spesa. Nessun servizio viene interrogato nel frame. */
internal class AndroidGlassSignals(private val app: Context) {
  private val manager = app.getSystemService(ActivityManager::class.java)
  val power: PowerManager? = app.getSystemService(PowerManager::class.java)
  val previousExit = if (Build.VERSION.SDK_INT >= 30) previousExit() else null

  fun read(thermalStatus: Int? = null): GlassReading {
    val memory = platformValue { manager?.let { am -> ActivityManager.MemoryInfo().also(am::getMemoryInfo) } }
    val runtime = Runtime.getRuntime()
    val capacity = GlassVariantSignals.capacity(GlassDeviceCapacity(
      totalRamBytes = memory?.totalMem ?: 0,
      maxHeapBytes = runtime.maxMemory(),
      heapClassMiB = platformValue { manager?.memoryClass } ?: 0,
      lowRamDevice = platformValue { manager?.isLowRamDevice },
      mediaPerformanceClass = if (Build.VERSION.SDK_INT >= 31) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
    ))
    val conditions = GlassConditions(
      availableBytes = memory?.availMem ?: -1,
      pressureThresholdBytes = memory?.threshold ?: 0,
      lowMemory = memory?.lowMemory,
      heapFraction = if (capacity.heapReferenceBytes > 0)
        (runtime.totalMemory() - runtime.freeMemory()).toDouble().div(capacity.heapReferenceBytes).coerceIn(0.0, 1.0)
      else Double.NaN,
      powerSave = platformValue { power?.isPowerSaveMode },
      thermal = if (Build.VERSION.SDK_INT >= 29)
        thermalLevel(thermalStatus ?: platformValue { power?.currentThermalStatus }) else GlassThermal.Unknown,
    )
    // I verdetti sono soltanto quelli già dati per queste ricette. Nessun contesto EGL o shader nuovo.
    val ceiling = when {
      Build.VERSION.SDK_INT < 31 -> GlassOptics.Protected
      Build.VERSION.SDK_INT < 33 -> GlassOptics.Reduced
      else -> {
        val known = GlassFieldHealth.verdicts()
        val classic = PhysicsBaseShaders
        val liquid = GlassShaderSources.travel
        when {
          classic.all { known.containsKey(it.name) && known[it.name] == null } ||
            liquid.all { known.containsKey(it.name) && known[it.name] == null } -> GlassOptics.Full
          classic.any { known[it.name] != null } && liquid.any { known[it.name] != null } -> GlassOptics.Reduced
          else -> null
        }
      }
    }
    // L'engine non espone un'identità GPU/driver già acquisita. La classe multimediale e un
    // verdetto shader restano indizi indipendenti; il moto misurato può autorizzare il recupero.
    return GlassReading(capacity, GlassVariantSignals.conditions(conditions), ceiling)
  }

  @RequiresApi(30)
  private fun previousExit(): String? {
    val name = Application.getProcessName()
    val file = File(app.noBackupFilesDir, "fluid-oom-" + android.net.Uri.encode(name))
    val exit = platformValue { manager?.getHistoricalProcessExitReasons(app.packageName, 0, 0) }
      ?.firstOrNull { it.processName == name && it.realUid == Process.myUid() }
    val cause = when (exit?.reason) {
      ApplicationExitInfo.REASON_LOW_MEMORY -> "REASON_LOW_MEMORY"
      ApplicationExitInfo.REASON_CRASH -> {
        val markerMatches = file.isFile && runCatching {
          file.readText().toIntOrNull() == exit.pid && kotlin.math.abs(exit.timestamp - file.lastModified()) < 60_000
        }.getOrDefault(false)
        if (markerMatches || exit.description?.contains("java.lang.OutOfMemoryError") == true) "OutOfMemoryError" else null
      }
      else -> null
    }
    if (file.isFile) file.delete()
    // Android non fornisce sempre il tipo dell'eccezione Java nel motivo di uscita. Solo un OOM
    // non gestito lascia il PID nel file privato. Il normale gestore dell'app riceve lo stesso errore.
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    if (previous != null) {
      val pid = Process.myPid().toString().toByteArray()
      Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        try {
          var current: Throwable? = error
          var depth = 0
          while (current != null && depth++ < 32) {
            if (current is OutOfMemoryError) {
              file.writeBytes(pid)
              break
            }
            current = current.cause
          }
        } catch (_: Throwable) {
          // In OOM persino aprire il file può fallire. Preservare sempre la consegna originale.
        } finally {
          previous.uncaughtException(thread, error)
        }
      }
    }
    if (cause != null) GlassVariantSignals.logExit(cause)
    return cause
  }
}

internal data class GlassReading(val capacity: GlassDeviceCapacity, val conditions: GlassConditions, val ceiling: GlassOptics?)

private inline fun <T> platformValue(read: () -> T): T? = try { read() } catch (_: RuntimeException) { null }

private fun thermalLevel(status: Int?): GlassThermal = when (status) {
  PowerManager.THERMAL_STATUS_NONE -> GlassThermal.None
  PowerManager.THERMAL_STATUS_LIGHT -> GlassThermal.Light
  PowerManager.THERMAL_STATUS_MODERATE -> GlassThermal.Moderate
  in PowerManager.THERMAL_STATUS_SEVERE..PowerManager.THERMAL_STATUS_SHUTDOWN -> GlassThermal.SevereOrWorse
  else -> GlassThermal.Unknown
}
