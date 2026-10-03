package dev.antigravity.fluidengine.wear.ambient

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.wear.ambient.AmbientLifecycleObserver

/**
 * Whether the watch is in ambient (always-on) mode, and what that mode asks of the screen.
 *
 * In ambient the display is refreshed about once a minute, the touch panel is off, and on many
 * panels only a fraction of pixels may be lit for long. A screen reading this hides what cannot
 * work there — controls, live glass, animation — and keeps what is worth glancing at.
 *
 * [updateTick] increments on every ambient refresh (`onUpdateAmbient`), which is the one moment
 * in ambient a screen may redraw. Read it in a draw or composition to be invalidated by it.
 */
@Stable
class FluidAmbientState internal constructor() {
  var isAmbient: Boolean by mutableStateOf(false)
    internal set

  /** The panel asks for lit pixels to move now and then; see [fluidBurnInShift]. */
  var burnInProtectionRequired: Boolean by mutableStateOf(false)
    internal set

  /** The panel can only show a few colours in ambient: draw outlines, no images or gradients. */
  var lowBitAmbient: Boolean by mutableStateOf(false)
    internal set

  var updateTick: Long by mutableLongStateOf(0L)
    internal set

  companion object {
    /** A state that is never ambient: previews, tests, phone-sized hosts. */
    fun interactive(): FluidAmbientState = FluidAmbientState()
  }
}

/** The ambient state of the screen this composition is on. Never ambient unless provided. */
val LocalFluidWearAmbient = staticCompositionLocalOf { FluidAmbientState.interactive() }

/**
 * Observes [activity]'s ambient mode for the lifetime of the composition.
 *
 * The activity also has to opt in to ambient support, which on current Wear OS is exactly this
 * observer being registered: without it the system simply leaves the app and shows the watch face
 * when the screen dims.
 */
@Composable
fun rememberFluidAmbientState(activity: ComponentActivity): FluidAmbientState {
  val state = remember { FluidAmbientState() }
  DisposableEffect(activity) {
    val observer = AmbientLifecycleObserver(
      activity,
      object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
          state.burnInProtectionRequired = ambientDetails.burnInProtectionRequired
          state.lowBitAmbient = ambientDetails.deviceHasLowBitAmbient
          state.isAmbient = true
          state.updateTick++
        }

        override fun onUpdateAmbient() {
          state.updateTick++
        }

        override fun onExitAmbient() {
          state.isAmbient = false
          state.updateTick++
        }
      },
    )
    activity.lifecycle.addObserver(observer)
    onDispose { activity.lifecycle.removeObserver(observer) }
  }
  return state
}

/**
 * Moves what it is applied to by a few pixels on every ambient refresh, when the panel asks for it.
 *
 * Burn-in comes from the *same* pixels being lit for hours. A cover that changes every song is
 * already most of the protection; this is the rest, and it is only switched on where the panel
 * declares it needs it. The offset walks a small fixed cycle rather than jumping at random, so the
 * movement is never large enough to read as the screen glitching.
 */
fun Modifier.fluidBurnInShift(state: FluidAmbientState, reach: Dp = BurnInReach): Modifier =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      val shift = if (state.isAmbient && state.burnInProtectionRequired) {
        burnInOffset(state.updateTick, reach.roundToPx())
      } else {
        IntOffset.Zero
      }
      placeable.place(shift)
    }
  }

/**
 * The offset for refresh number [tick]: a square walk of side [reachPx] around the origin.
 *
 * Eight positions, each a neighbour of the last, so consecutive refreshes move by at most
 * [reachPx] on each axis.
 */
internal fun burnInOffset(tick: Long, reachPx: Int): IntOffset {
  val steps = BurnInWalk
  val (x, y) = steps[Math.floorMod(tick, steps.size.toLong()).toInt()]
  return IntOffset(x * reachPx, y * reachPx)
}

private val BurnInWalk = listOf(
  0 to 0, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1,
)

/** How far [fluidBurnInShift] moves at most, on each axis. */
val BurnInReach: Dp = 6.dp
