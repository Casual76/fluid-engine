package dev.antigravity.fluidengine.wear.glass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.wear.ambient.LocalFluidWearAmbient
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A round control made of glass: the transport buttons of a watch player, and any other action
 * that has to be a thumb-press on a moving wrist.
 *
 * It is the phone's [glassControlSurface] in a circle, made of [FluidWearGlass]: it refracts what
 * is behind it, leans towards the finger, swells while held and lights up where it was touched.
 * That is the whole press. Until 2.11.0 every disc also fired a [FluidGlassBurst] — a ring and
 * eight sparks — and on a real watch a row of discs going off like that read as a glitch rather
 * than as a confirmation; pass [burst] to have it back on the one control that deserves it.
 *
 * The glass stays still when nothing touches it: the pane re-captures its backdrop only while its
 * own lean and swell are animating or what is behind it actually changes.
 *
 * @param size the disc as drawn; the touch target never shrinks below
 *   [FluidWearDimens.MinTouchTarget].
 * @param haptic what the press feels like; play/pause wants `Confirm`, the rest `Tap`.
 * @param burst a burst of light on the press; none by default since 2.11.0.
 * @param tint [FluidWearGlass.tint] by default since 2.11.0, the same smoke as every other watch pane.
 * @param optics [FluidWearGlass.Optics] by default. Since 2.11.0.
 */
@OptIn(ExperimentalFoundationApi::class)
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
  burst: FluidGlassBurstState? = null,
  tint: GlassTint = FluidWearGlass.tint(),
  optics: GlassOptics = FluidWearGlass.Optics,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable BoxScope.() -> Unit,
) {
  val haptics = LocalFluidHaptics.current
  val ambient = LocalFluidWearAmbient.current
  val onDark = GlassDefaults.isDarkSurface()
  val interactionSource = remember { MutableInteractionSource() }
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
          optics = optics,
          selected = selected,
          interactive = enabled,
        )
        .then(
          if (burst == null) {
            Modifier
          } else {
            Modifier
              .fluidGlassBurst(burst, onDarkSurface = onDark, enabled = !ambient.isAmbient)
              .pointerInput(Unit) {
                // Only remembers where the press landed, for the burst; never consumes.
                awaitEachGesture {
                  val down = awaitFirstDown(requireUnconsumed = false)
                  lastDown[0] = down.position.x
                  lastDown[1] = down.position.y
                }
              }
          },
        )
        .combinedClickable(
          interactionSource = interactionSource,
          indication = null,
          enabled = enabled,
          role = Role.Button,
          onLongClick = onLongClick?.let { long ->
            {
              haptics.play(FluidHapticEvent.Threshold)
              long()
            }
          },
          onClick = {
            val at = if (lastDown[0].isNaN()) null else Offset(lastDown[0], lastDown[1])
            burst?.fire(at)
            haptics.play(haptic)
            onClick()
          },
        )
        .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
      contentAlignment = Alignment.Center,
    ) {
      // A disc that cannot be pressed says so with its glyph; the glass itself stays the same pane.
      CompositionLocalProvider(LocalContentColor provides if (enabled) contentColor else contentColor.copy(alpha = DisabledContentAlpha)) {
        content()
      }
    }
  }
}

private const val DisabledContentAlpha = 0.38f
