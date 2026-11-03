package dev.antigravity.fluidengine.wear.glass

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassOptics
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A capsule of glass that carries text: the title of what is playing, a status, a label over a
 * picture.
 *
 * Floating glass, not control glass: it does not lean or swell, because text that moves under the
 * eye is text nobody can read. When it is tappable it answers with the design system's press scale
 * instead.
 *
 * @param tint [FluidWearGlass.tint] by default since 2.11.0 — the same smoke as the discs beside it.
 *   Before that it was the phone's floating tint, a black scrim, and the capsule and the discs
 *   under it read as two different materials.
 * @param optics [FluidWearGlass.WideOptics] by default. Since 2.11.0.
 */
@Composable
fun FluidGlassCapsule(
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  tint: GlassTint = FluidWearGlass.tint(),
  optics: GlassOptics = FluidWearGlass.WideOptics,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable RowScope.() -> Unit,
) {
  Row(
    modifier = modifier
      .then(
        if (onClick != null || onLongClick != null) {
          Modifier.fluidPressable(onClick = onClick, onLongClick = onLongClick)
        } else {
          Modifier
        },
      )
      .glassSurface(
        state = backdrop,
        tint = tint,
        shape = FluidCapsuleShape,
        role = GlassRole.Floating,
        optics = optics,
      )
      .padding(
        horizontal = FluidWearDimens.CapsulePaddingHorizontal,
        vertical = FluidWearDimens.CapsulePaddingVertical,
      ),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      content()
    }
  }
}
