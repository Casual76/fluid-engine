package dev.antigravity.fluidengine.wear.glass

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPressState
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.wear.ambient.LocalFluidWearAmbient
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A round control made of glass: the transport buttons of a watch player, and any other action
 * that has to be a thumb-press on a moving wrist.
 *
 * It is the phone's [glassControlSurface] in a circle — it refracts what is behind it, leans
 * towards the finger, swells while held and lights up where it was touched — plus a
 * [FluidGlassBurst] on the press, which is what a watch needs that a phone does not: on a wrist
 * the finger covers most of the control, and the light that escapes around it is the confirmation.
 *
 * The press is the phone's too: one [dev.antigravity.fluidengine.ui.fluid.FluidPressState] shared
 * by the glass and by [fluidPressable], so the disc behaves like every glass button of the engine.
 * A quick flick that starts on it scrolls the list it sits in; a finger that stays down for 100 ms
 * has pressed it, and from there the gesture is the disc's until the lift — it follows the finger
 * even off its rim, nothing under it scrolls, and the click fires if the finger lifts on the disc
 * (plus 16 dp). Before, the disc answered at the first touch and let go of the finger at the first
 * movement.
 *
 * The glass stays still when nothing touches it: the pane re-captures its backdrop only while its
 * own lean and swell are animating or what is behind it actually changes, and the burst is drawn
 * over the glass without re-capturing anything.
 *
 * @param size the disc as drawn; the touch target never shrinks below
 *   [FluidWearDimens.MinTouchTarget].
 * @param haptic what the press feels like; play/pause wants `Confirm`, the rest `Tap`.
 */
@Composable
fun FluidGlassDisc(
  onClick: () -> Unit,
  backdrop: GlassBackdropState,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = FluidWearDimens.DiscMedium,
  enabled: Boolean = true,
  selected: Boolean = false,
  onLongClick: (() -> Unit)? = null,
  haptic: FluidHapticEvent = FluidHapticEvent.Tap,
  burst: FluidGlassBurstState = rememberFluidGlassBurstState(),
  tint: GlassTint = GlassDefaults.controlTint(),
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable BoxScope.() -> Unit,
) {
  val ambient = LocalFluidWearAmbient.current
  val onDark = GlassDefaults.isDarkSurface()
  // Una pressione sola per il vetro e per il click, come ogni tasto di vetro del telefono.
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  val lastDown = remember { floatArrayOf(Float.NaN, Float.NaN) }

  Box(
    modifier = modifier.size(max(size, FluidWearDimens.MinTouchTarget)),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .glassControlSurface(
          backdrop = backdrop,
          shape = FluidCapsuleShape,
          tint = tint,
          selected = selected,
          interactive = enabled,
          pressState = press,
        )
        .fluidGlassBurst(burst, onDarkSurface = onDark, enabled = !ambient.isAmbient)
        .pointerInput(Unit) {
          // Only remembers where the press landed, for the burst; never consumes. Sta fuori da
          // fluidPressable, che il down lo consuma: lo legge comunque.
          awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            lastDown[0] = down.position.x
            lastDown[1] = down.position.y
          }
        }
        .fluidPressable(
          onClick = {
            val at = if (lastDown[0].isNaN()) null else Offset(lastDown[0], lastDown[1])
            burst.fire(at)
            onClick()
          },
          onLongClick = onLongClick,
          enabled = enabled,
          role = Role.Button,
          haptic = haptic,
          // Il disco ha sempre fatto sentire la soglia, non l'inizio di un gesto.
          longClickHaptic = FluidHapticEvent.Threshold,
          focusShape = FluidCapsuleShape,
          pressState = press,
        )
        .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
      contentAlignment = Alignment.Center,
    ) {
      CompositionLocalProvider(LocalContentColor provides contentColor) {
        content()
      }
    }
  }
}
