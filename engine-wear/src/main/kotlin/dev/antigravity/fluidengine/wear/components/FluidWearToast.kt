package dev.antigravity.fluidengine.wear.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberEmptyGlassBackdrop
import dev.antigravity.fluidengine.wear.glass.FluidWearGlass
import dev.antigravity.fluidengine.wear.theme.FluidWearDimens

/**
 * A short notice in the middle of the screen: something the person asked for did not happen.
 *
 * The watch's answer to a phone's snackbar. A snackbar sits at the bottom edge, and on a round
 * screen the bottom edge is where the arc of actions is and where the curve cuts text short; a
 * notice in the middle, on the watch's glass made nearly opaque, is read wherever it lands. It
 * comes and goes softly; the caller decides when ([message] null hides it). Announced to TalkBack
 * as it appears.
 */
@Composable
fun FluidWearToast(message: String?, modifier: Modifier = Modifier, icon: ImageVector? = null) {
  // Keeps the last words through the exit, rather than going blank as it fades.
  var shown by remember { mutableStateOf(message) }
  if (message != null) shown = message
  BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val width = maxWidth * MaxWidthFraction
    AnimatedVisibility(
      visible = message != null,
      enter = fadeIn(FluidMotion.fadeIn()) + scaleIn(FluidMotion.snappy(), initialScale = EnterScale),
      exit = fadeOut(FluidMotion.fadeOut()) + scaleOut(targetScale = EnterScale),
    ) {
      Row(
        modifier = Modifier
          .widthIn(max = width)
          .semantics { liveRegion = LiveRegionMode.Polite }
          .glassSurface(
            state = rememberEmptyGlassBackdrop(),
            tint = FluidWearGlass.noticeTint(),
            shape = FluidCapsuleShape,
            role = GlassRole.Floating,
            optics = FluidWearGlass.WideOptics,
          )
          .padding(horizontal = FluidWearDimens.CapsulePaddingHorizontal, vertical = FluidWearDimens.CapsulePaddingVertical * 2),
        horizontalArrangement = Arrangement.spacedBy(FluidWearDimens.CapsulePaddingVertical, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(FluidWearDimens.IconSmall))
        Text(
          text = shown.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          maxLines = MaxLines,
        )
      }
    }
  }
}

private const val MaxWidthFraction = 0.78f
private const val EnterScale = 0.9f
private const val MaxLines = 3
