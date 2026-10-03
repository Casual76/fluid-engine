package dev.antigravity.fluidengine.wear.components

import android.content.Context
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics

/**
 * Turns the bezel or the crown into steps.
 *
 * The two kinds of hardware disagree on what one event is. A Galaxy Watch bezel — the physical
 * ring or the touch one — clicks: every event is one detent, whatever pixel value it reports. A
 * crown is smooth and reports pixels, often a fraction of a step per event. So a bezel is counted
 * in events and a crown in accumulated distance ([PixelsPerStep]), and both come out of here as
 * whole steps with the direction of the turn. Each step plays the design system's tick.
 *
 * @param onSteps called with a non-zero number of steps; positive is clockwise.
 * @param enabled false stops listening and releases the focus.
 */
@Composable
fun Modifier.fluidRotarySteps(
  onSteps: (Int) -> Unit,
  enabled: Boolean = true,
): Modifier {
  if (!enabled) return this
  val context = LocalContext.current
  val haptics = LocalFluidHaptics.current
  val stepPx = with(LocalDensity.current) { PixelsPerStep.toPx() }
  val lowRes = remember(context) { hasLowResRotary(context) }
  val accumulator = remember { RotaryAccumulator(stepPx, lowRes) }
  val callback = rememberUpdatedState(onSteps)
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
  return this
    .onRotaryScrollEvent { event ->
      val steps = accumulator.add(event.verticalScrollPixels)
      if (steps != 0) {
        haptics.play(FluidHapticEvent.Tick)
        callback.value(steps)
      }
      true
    }
    .focusRequester(focus)
    .focusable()
}

/**
 * The arithmetic of [fluidRotarySteps], pulled out so it can be tested without a watch.
 */
internal class RotaryAccumulator(private val stepPx: Float, private val lowRes: Boolean) {
  private var pending = 0f

  fun add(pixels: Float): Int {
    if (pixels == 0f || !pixels.isFinite()) return 0
    if (lowRes) return if (pixels > 0) 1 else -1
    // A change of direction throws away what was gathered the other way: half a step clockwise
    // followed by a nudge back is the hand settling, not a step.
    if (pending != 0f && (pending > 0) != (pixels > 0)) pending = 0f
    pending += pixels
    val steps = (pending / stepPx).toInt()
    pending -= steps * stepPx
    return steps
  }
}

/** The Wear feature flag a clicking bezel declares. */
internal fun hasLowResRotary(context: Context): Boolean =
  context.packageManager.hasSystemFeature(LowResRotaryFeature)

private const val LowResRotaryFeature = "android.hardware.rotaryencoder.lowres"

/** How far a smooth crown has to turn for one step. */
private val PixelsPerStep = 32.dp
