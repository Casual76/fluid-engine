package dev.antigravity.fluidengine.ui.glass.quality.android

import android.util.Log
import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence
import dev.antigravity.fluidengine.ui.glass.quality.GlassThermal

/** Solo nelle varianti debug e bench. I flag si leggono fuori dal percorso per-frame. */
internal object GlassVariantSignals {
  private fun enabled(tag: String) = Log.isLoggable(tag, Log.VERBOSE)
  fun capacity(real: GlassDeviceCapacity) = if (enabled("FluidF7LowRam"))
    real.copy(totalRamBytes = 4L shl 30, heapClassMiB = 192, lowRamDevice = true) else real
  fun conditions(real: GlassConditions) = real.copy(
    availableBytes = if (enabled("FluidF7LowRam")) minOf(real.availableBytes, 4L shl 30) else real.availableBytes,
    thermal = when {
      enabled("FluidF7Hot") -> GlassThermal.SevereOrWorse
      enabled("FluidF7Warm") -> maxOf(real.thermal, GlassThermal.Moderate)
      else -> real.thermal
    },
  )
  fun lateFrames() = enabled("FluidF7Late")
  fun logExit(cause: String) {
    if (enabled("FluidF7Exit")) Log.d("FluidF7Exit", cause)
  }
}

internal class GlassFrameTimings {
  private val forceLate = GlassVariantSignals.lateFrames()
  fun duration(real: Long, deadline: Long, period: Long, evidence: GlassFrameEvidence): Long =
    if (forceLate && evidence == GlassFrameEvidence.MeasuredApp) deadline + period else real
}
