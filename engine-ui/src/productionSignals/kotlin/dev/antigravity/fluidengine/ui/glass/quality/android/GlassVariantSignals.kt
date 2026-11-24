package dev.antigravity.fluidengine.ui.glass.quality.android

import dev.antigravity.fluidengine.ui.glass.quality.GlassConditions
import dev.antigravity.fluidengine.ui.glass.quality.GlassDeviceCapacity
import dev.antigravity.fluidengine.ui.glass.quality.GlassFrameEvidence

/** In release passano esclusivamente le letture Android. */
internal object GlassVariantSignals {
  fun capacity(real: GlassDeviceCapacity) = real
  fun conditions(real: GlassConditions) = real
  fun logExit(@Suppress("UNUSED_PARAMETER") cause: String) = Unit
}

internal class GlassFrameTimings {
  @Suppress("UNUSED_PARAMETER")
  fun duration(real: Long, deadline: Long, period: Long, evidence: GlassFrameEvidence): Long = real
}
