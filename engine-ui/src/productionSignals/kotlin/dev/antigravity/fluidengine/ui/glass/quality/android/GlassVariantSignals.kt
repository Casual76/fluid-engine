package dev.antigravity.fluidengine.ui.glass.quality.android

import android.app.Activity
import android.view.FrameMetrics
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassQualityPolicy

/** In release passano esclusivamente le letture Android. Nessun profilo simulato, nessuna riga dei livelli. */
internal object GlassVariantSignals {
  fun capacity(real: GlassDeviceCapacity) = real
  fun conditions(real: GlassConditions) = real
  fun logExit(@Suppress("UNUSED_PARAMETER") cause: String) = Unit
  const val noting = false
  const val flatSource = false
  @Suppress("UNUSED_PARAMETER", "NOTHING_TO_INLINE")
  inline fun note(line: () -> String) = Unit
  @Suppress("UNUSED_PARAMETER")
  fun launch(activity: Activity, refresh: () -> Unit, sample: () -> String): Boolean = false
  @Suppress("UNUSED_PARAMETER")
  fun noteSource(layer: Any, samples: Boolean, width: Float, height: Float) = Unit
}

/** F7.26d: in release nessuna diagnostica dei voti; le chiamate di [WindowFrames] stanno dietro `noting`, qui falso. */
@Suppress("UNUSED_PARAMETER")
internal class GlassFrameVotes(displayId: Int, period: Long, policy: GlassQualityPolicy) {
  fun discarded(vsync: Long, born: Long, lastVsync: Long, now: Long) = Unit
  fun still() = Unit
  fun frame(metrics: FrameMetrics, dropped: Int, total: Long, deadline: Long, work: Long, evidence: GlassFrameEvidence) = Unit
  fun pose(why: Int) = Unit

  companion object {
    const val ScrollEnd = 0
    const val Gap = 1
    const val Quiet = 2
  }
}

internal class GlassFrameTimings {
  @Suppress("UNUSED_PARAMETER")
  fun duration(real: Long, deadline: Long, period: Long, evidence: GlassFrameEvidence): Long = real
}
