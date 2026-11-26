package dev.antigravity.fluidengine.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.InputStream

/**
 * Le chiusure decise dal sistema (F8.24): ad ogni avvio, da Android 11, l'engine chiede ad `ActivityManager` come sono
 * finiti i processi di prima (`getHistoricalProcessExitReasons`) e tiene quelle che l'utente non ha voluto: un blocco
 * (ANR), un crash nativo, la memoria finita, troppe risorse, un avvio fallito. È la parte di MetricKit che dice
 * «l'app è stata chiusa in sottofondo» (MXAppExitMetric) e i blocchi (MXHangDiagnostic).
 *
 * Restano fuori: le uscite volute (l'app che si chiude, l'utente che la toglie dalle recenti o la ferma, un permesso
 * tolto, un aggiornamento) e il crash Java, che il gestore dei crash ha già scritto con le sue righe.
 */
internal object FluidExitReasons {
  /** Quante chiusure si leggono al massimo per avvio. */
  const val MaxPerLaunch: Int = 16

  /** Quanti byte della traccia di un ANR si leggono al massimo (il filo principale sta in cima). */
  const val MaxTraceBytes: Int = 256 * 1024

  /** Il nome di una chiusura da tenere, o nulla per quelle da lasciare. */
  fun reasonName(reason: Int): String? = when (reason) {
    ApplicationExitInfo.REASON_ANR -> "anr"
    ApplicationExitInfo.REASON_CRASH_NATIVE -> "crash_native"
    ApplicationExitInfo.REASON_LOW_MEMORY -> "low_memory"
    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "excessive_resource"
    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization_failure"
    ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "dependency_died"
    ApplicationExitInfo.REASON_SIGNALED -> "signaled"
    14 /* REASON_FREEZER, Android 13 */ -> "freezer"
    else -> null
  }

  /** «foreground» se l'app si vedeva (importanza fino a «visibile»), «background» altrimenti. */
  fun importanceName(importance: Int): String =
    if (importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) "foreground" else "background"

  /**
   * Le chiusure nuove dopo [since] (millisecondi), dalla più vecchia. Ritorna anche il momento dell'ultima letta, da
   * ricordare per il prossimo avvio.
   */
  @RequiresApi(Build.VERSION_CODES.R)
  fun read(manager: ActivityManager, packageName: String, since: Long): Pair<List<Pair<Long, FluidExitInfo>>, Long> {
    val infos = runCatching { manager.getHistoricalProcessExitReasons(packageName, 0, MaxPerLaunch) }.getOrDefault(emptyList())
    var newest = since
    val out = ArrayList<Pair<Long, FluidExitInfo>>()
    for (info in infos.sortedBy { it.timestamp }) {
      if (info.timestamp <= since) continue
      newest = maxOf(newest, info.timestamp)
      val reason = reasonName(info.reason) ?: continue
      val frames = if (info.reason == ApplicationExitInfo.REASON_ANR) {
        runCatching { info.traceInputStream?.use(::mainThreadFrames) }.getOrNull() ?: emptyList()
      } else {
        emptyList()
      }
      out += info.timestamp to FluidExitInfo(reason, importanceName(info.importance), info.pss, info.rss, frames)
    }
    return out to newest
  }

  /**
   * Le righe del codice del filo principale nella traccia di un ANR: le righe `at …` dopo `"main"`, fino alla riga
   * vuota. Si tolgono i lucchetti (`- locked <0x…>`, che portano indirizzi) e le righe native.
   */
  fun mainThreadFrames(trace: InputStream): List<String> {
    val bytes = trace.readNBytesCompat(MaxTraceBytes)
    val text = String(bytes, Charsets.UTF_8)
    val lines = text.lineSequence().iterator()
    val frames = ArrayList<String>()
    var inMain = false
    while (lines.hasNext()) {
      val line = lines.next()
      if (!inMain) {
        if (line.startsWith("\"main\"")) inMain = true
        continue
      }
      val trimmed = line.trim()
      if (trimmed.isEmpty()) break
      if (trimmed.startsWith("at ")) {
        frames += trimmed.removePrefix("at ").trim()
        if (frames.size >= FluidCrash.MaxFrames) break
      }
    }
    return frames
  }

  private fun InputStream.readNBytesCompat(max: Int): ByteArray {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    var total = 0
    while (total < max) {
      val n = read(chunk, 0, minOf(chunk.size, max - total))
      if (n < 0) break
      buffer.write(chunk, 0, n)
      total += n
    }
    return buffer.toByteArray()
  }
}
