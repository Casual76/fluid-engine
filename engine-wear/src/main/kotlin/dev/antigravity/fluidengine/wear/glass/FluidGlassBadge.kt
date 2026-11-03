package dev.antigravity.fluidengine.wear.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A disc of glass that is not a button: a sign that something just happened.
 *
 * The play/pause glyph that appears over a full-screen cover when it is tapped, an icon over a
 * picture. Same material and lens as [FluidGlassDisc], none of its lean or touch light — nothing
 * here is waiting for a finger. Animate it in and out from the caller; it is live only while it
 * moves.
 */
@Composable
fun FluidGlassBadge(
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  size: Dp = FluidWearDimens.DiscLarge,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .size(size)
      .glassSurface(
        state = backdrop,
        tint = FluidWearGlass.tint(),
        shape = FluidCapsuleShape,
        role = GlassRole.Interactive,
        optics = FluidWearGlass.Optics,
      ),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
  }
}
