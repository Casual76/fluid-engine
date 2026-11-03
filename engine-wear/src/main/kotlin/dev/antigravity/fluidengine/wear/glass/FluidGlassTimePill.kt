package dev.antigravity.fluidengine.wear.glass

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeTextDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * The time, in a small pane of glass at the top of a screen that is a picture.
 *
 * Wear's curved `TimeText` is right over a list, where it sits on black. Over a full-screen cover
 * it is thin white type on whatever the artwork has at twelve o'clock, and a progress ring that
 * starts at twelve runs straight through it. This puts the clock on the same glass as the rest of
 * the screen and gives it a definite width, which the ring can leave a gap for (see
 * `FluidEdgeProgressRing(clearTop = …)`).
 *
 * The pane never re-captures because the minute changed: the digits are drawn over the glass, not
 * through it.
 *
 * @param backdrop the screen's glass source; null draws the time alone, which is what ambient
 *   mode wants (no glass may be lit there).
 */
@Composable
fun FluidGlassTimePill(
  backdrop: GlassBackdropState?,
  modifier: Modifier = Modifier,
  timeSource: TimeSource = TimeTextDefaults.rememberTimeSource(TimeTextDefaults.timeFormat()),
) {
  val tint = FluidWearGlass.tint()
  val style = MaterialTheme.typography.labelSmall
  val tabular = remember(style) { style.copy(fontFeatureSettings = "tnum") }
  Text(
    text = timeSource.currentTime(),
    style = tabular,
    color = MaterialTheme.colorScheme.onSurface,
    maxLines = 1,
    modifier = modifier
      .semantics { heading() }
      .then(
        if (backdrop == null) {
          Modifier
        } else {
          Modifier.glassSurface(
            state = backdrop,
            tint = tint,
            shape = FluidCapsuleShape,
            role = GlassRole.Floating,
            optics = FluidWearGlass.WideOptics,
          )
        },
      )
      .padding(
        horizontal = FluidWearDimens.TimePillPaddingHorizontal,
        vertical = FluidWearDimens.TimePillPaddingVertical,
      ),
  )
}
