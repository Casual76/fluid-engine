package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val FluidGlassIconTouchTargetSize: Dp = 48.dp
internal val FluidGlassIconLensSize: Dp = 44.dp

/**
 * A second optical thickness inside an existing pane of glass.
 *
 * This modifier deliberately does not sample or blur a backdrop. Controls placed on a glass bar
 * already sit over its softened pixels; sampling each icon again would add two more render layers
 * per action. The translucent body, asymmetric rim and inset shadow make the control read as a
 * convex lens while reusing the parent's one backdrop pass.
 */
@Composable
fun Modifier.glassControlSurface(
  shape: Shape = FluidCapsuleShape,
  selected: Boolean = false,
  pressure: () -> Float = { 0f },
): Modifier {
  val scheme = MaterialTheme.colorScheme
  val lightSurface = scheme.surface.luminanceIsLightForGlass()
  val body = if (selected) scheme.primary else scheme.surface
  val topLight = Color.White.copy(alpha = if (lightSurface) 0.78f else 0.46f)
  val quietLight = Color.White.copy(alpha = if (lightSurface) 0.16f else 0.10f)
  val lowerShade = Color.Black.copy(alpha = if (lightSurface) 0.18f else 0.42f)
  val innerShade = scheme.onSurface.copy(alpha = if (lightSurface) 0.13f else 0.24f)

  return this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val insetPx = 2.dp.toPx()
    val innerSize = Size(
      width = (size.width - insetPx * 2f).coerceAtLeast(0f),
      height = (size.height - insetPx * 2f).coerceAtLeast(0f),
    )
    val innerOutline = if (innerSize.width > 0f && innerSize.height > 0f) {
      shape.createOutline(innerSize, layoutDirection, this)
    } else {
      null
    }
    val diagonal = Offset(size.width, size.height)
    val bodyBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to body.copy(alpha = if (selected) 0.22f else 0.13f),
        0.46f to body.copy(alpha = if (selected) 0.14f else 0.075f),
        1f to body.copy(alpha = if (selected) 0.18f else 0.10f),
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val rimBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to topLight,
        0.38f to quietLight,
        0.68f to Color.Transparent,
        1f to lowerShade,
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val innerBrush = Brush.linearGradient(
      colorStops = arrayOf(
        0f to quietLight,
        0.50f to Color.Transparent,
        1f to innerShade,
      ),
      start = Offset.Zero,
      end = diagonal,
    )
    val baseRimWidth = 1.15.dp.toPx()
    val innerRimWidth = 0.75.dp.toPx()

    onDrawBehind {
      val pressed = pressure().coerceIn(0f, 1f)
      drawOutline(
        outline = outline,
        brush = bodyBrush,
        alpha = 1f,
      )
      drawOutline(
        outline = outline,
        brush = rimBrush,
        alpha = 1f - pressed * 0.18f,
        style = Stroke(width = baseRimWidth * (1f - pressed * 0.24f)),
      )
      innerOutline?.let {
        translate(insetPx, insetPx) {
          drawOutline(
            outline = it,
            brush = innerBrush,
            alpha = 1f - pressed * 0.12f,
            style = Stroke(width = innerRimWidth),
          )
        }
      }
      if (pressed > 0.001f) {
        drawOutline(
          outline = outline,
          color = scheme.onSurface.copy(alpha = 0.055f * pressed),
        )
      }
    }
  }
}

/** A glass lens for icon actions that live in chrome or another overlay. */
@Composable
fun FluidGlassIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  content: @Composable BoxScope.() -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val pressure by animateFloatAsState(
    targetValue = if (pressed && enabled) 1f else 0f,
    animationSpec = if (reducedMotion) {
      snap()
    } else if (pressed) {
      FluidMotion.instant()
    } else {
      FluidMotion.color(220)
    },
    label = "glass control pressure",
  )

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .size(FluidGlassIconTouchTargetSize)
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        pressedScale = 0.965f,
        role = Role.Button,
        interactionSource = interactionSource,
      ),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(FluidGlassIconLensSize)
        .glassControlSurface(shape = shape, selected = selected, pressure = { pressure }),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}

private fun Color.luminanceIsLightForGlass(): Boolean =
  (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f
