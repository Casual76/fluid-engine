package dev.antigravity.fluidengine.ui.glass.backdrop

import java.util.concurrent.atomic.AtomicLong

/**
 * Counts how often glass re-captures what is behind it.
 *
 * A capture is the expensive half of the material: a replay of the backdrop's whole recording plus
 * the effect chain over it. The design is that a pane captures only when something it shows has
 * changed — its backdrop redrew, or it moved relative to it — and holds still otherwise. This is how
 * that claim is checked on a real device instead of being believed: switch [enabled] on in a debug
 * build, leave a screen alone, and [totalCaptures] must stop moving.
 *
 * Off by default, and then it costs one volatile read per capture.
 */
object FluidGlassDiagnostics {

  @Volatile
  var enabled: Boolean = false

  private val captures = AtomicLong()

  /** Captures taken since [enabled] was first switched on. */
  val totalCaptures: Long get() = captures.get()

  internal fun onCapture() {
    if (enabled) captures.incrementAndGet()
  }
}
