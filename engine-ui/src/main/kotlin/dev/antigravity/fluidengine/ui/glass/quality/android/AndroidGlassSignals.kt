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
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuClass
import dev.antigravity.fluidengine.ui.glass.quality.GlassGpuTable
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal
import java.io.File

/** Lettura sincrona ai confini di vita e di spesa. Nessun servizio viene interrogato nel frame. */
internal class AndroidGlassSignals(private val app: Context) {
  private val manager = app.getSystemService(ActivityManager::class.java)
  val power: PowerManager? = app.getSystemService(PowerManager::class.java)
  val previousExit = if (Build.VERSION.SDK_INT >= 30) previousExit() else null
  /** F7.26: il SoC e la classe della sua GPU, letti una volta per processo. Le letture dopo riusano questi. */
  val soc: GlassSoc = GlassSoc.read()

  fun read(thermalStatus: Int? = null): GlassReading {
    val memory = platformValue { manager?.let { am -> ActivityManager.MemoryInfo().also(am::getMemoryInfo) } }
    val runtime = Runtime.getRuntime()
    val capacity = GlassVariantSignals.capacity(GlassDeviceCapacity(
      totalRamBytes = memory?.totalMem ?: 0,
      maxHeapBytes = runtime.maxMemory(),
      heapClassMiB = platformValue { manager?.memoryClass } ?: 0,
      lowRamDevice = platformValue { manager?.isLowRamDevice },
      mediaPerformanceClass = if (Build.VERSION.SDK_INT >= 31) Build.VERSION.MEDIA_PERFORMANCE_CLASS else 0,
      recognizedGpuDriver = soc.gpu == GlassGpuClass.High,
      gpu = soc.gpu,
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
    // F7.26: la GPU riconosciuta viene dal nome del SoC ([soc]). La classe multimediale e un verdetto shader restano
    // indizi indipendenti; il moto misurato può autorizzare il recupero, e i ritardi la discesa.
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

/**
 * F7.26 · Il nome del SoC e la classe della sua GPU ([GlassGpuTable]). Si legge una volta per processo, nel costruttore
 * di [AndroidGlassSignals]: sono quattro campi statici di `Build` che Android ha già letto dalle proprietà di sistema
 * quando ha caricato la classe, quindi nessuna chiamata al sistema, nessun file e nessun contesto grafico, su qualunque
 * thread. [readNs] è quanto è costata la lettura con la classificazione, per la riga `glassBudgetStart`.
 */
internal class GlassSoc(
  val manufacturer: String?,
  val model: String?,
  val board: String?,
  val hardware: String?,
  val gpu: GlassGpuClass,
  val readNs: Long,
) {
  override fun toString() = "soc=${manufacturer ?: "-"}/${model ?: "-"} board=$board hardware=$hardware gpu=$gpu socReadNs=$readNs"

  companion object {
    fun read(): GlassSoc {
      val start = System.nanoTime()
      val manufacturer = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MANUFACTURER else null
      val model = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else null
      val board: String? = Build.BOARD
      val hardware: String? = Build.HARDWARE
      val gpu = GlassGpuTable.classify(manufacturer, model, board, hardware)
      return GlassSoc(manufacturer, model, board, hardware, gpu, System.nanoTime() - start)
    }
  }
}

private inline fun <T> platformValue(read: () -> T): T? = try { read() } catch (_: RuntimeException) { null }

private fun thermalLevel(status: Int?): GlassThermal = when (status) {
  PowerManager.THERMAL_STATUS_NONE -> GlassThermal.None
  PowerManager.THERMAL_STATUS_LIGHT -> GlassThermal.Light
  PowerManager.THERMAL_STATUS_MODERATE -> GlassThermal.Moderate
  in PowerManager.THERMAL_STATUS_SEVERE..PowerManager.THERMAL_STATUS_SHUTDOWN -> GlassThermal.SevereOrWorse
  else -> GlassThermal.Unknown
}
