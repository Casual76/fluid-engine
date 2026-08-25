package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

internal val FluidGlassIconTouchTargetSize: Dp = 48.dp
internal val FluidGlassIconLensSize: Dp = 44.dp

/** Height of a glass action button, matching [FluidButtonDefaults] at `Medium`. */
internal val FluidGlassButtonHeight: Dp = 48.dp

/**
 * How far a glass control may lean toward the finger, in dp.
 *
 * Small on purpose. The lean is not meant to be seen as movement — it is meant to be *felt* as the
 * control being made of something. Anything past a few dp stops reading as compliance and starts
 * reading as a bug.
 */
private const val LeanReachDp = 12f

/** How much the control swells while it is held, in dp added to its height. */
private const val PressSwellDp = 4f

/**
 * A pane of glass that behaves like a control.
 *
 * The difference from [glassSurface] is entirely in what it does with a finger, and that is the
 * whole point: the previous implementation of this modifier painted three gradients and called the
 * result a lens, so a control looked like glass in a screenshot and like a button in the hand.
 *
 * What is here instead:
 *
 *  * **It refracts.** Real displacement over a real recording, with dispersion, so the content
 *    behind the control bends and splits at its edge as the control passes over it.
 *  * **It leans.** The whole pane translates toward the touch point through a `tanh`, so it moves
 *    freely at first and then refuses — the shape of pushing on something soft that is attached.
 *  * **It swells and squashes.** Held, it grows a few dp; pushed off-axis, it stretches along the
 *    direction of the push and thins across it.
 *  * **It lights up where you touched it.** An additive hotspot follows the finger, because a
 *    specular reflection adds light. Darkening on press is the plastic-button idiom.
 *
 * The lean and the swell are applied through the renderer's own layer block, which means the
 * refraction is computed *after* the transform and corrected for it: the control slides across the
 * backdrop and keeps showing what is genuinely behind it, instead of dragging a frozen picture
 * along.
 *
 * @param backdrop what the control refracts. Give it the surface the control sits *on* — see
 *   [rememberCombinedGlassBackdrop] — not the page underneath that surface.
 * @param interactive false for a control that is only decorative or disabled; it keeps the material
 *   and drops every gesture, rather than needing a second, quieter component to exist.
 */
@Composable
fun Modifier.glassControlSurface(
  backdrop: GlassBackdropState,
  shape: Shape = FluidCapsuleShape,
  tint: GlassTint = GlassDefaults.controlTint(),
  optics: GlassOptics = GlassDefaults.optics(GlassRole.Interactive),
  selected: Boolean = false,
  interactive: Boolean = true,
  /** Independent optical weight, so a control can rest almost invisible and thicken on demand. */
  depth: () -> Float = { 1f },
): Modifier {
  val accent = MaterialTheme.colorScheme.primary
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val highlight = remember(scope, reducedMotion) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0.4f else 1f },
    )
  }
  val selectionTint = remember(tint, selected, accent) {
    if (!selected) {
      tint
    } else {
      // Selection is carried by the accent joining the material, not by an opaque fill replacing it.
      tint.copy(
        overlay = accent.copy(alpha = 0.22f),
        fallback = accent.copy(alpha = 0.9f),
      )
    }
  }

  return this
    .glassSurface(
      state = backdrop,
      tint = selectionTint,
      shape = shape,
      role = GlassRole.Interactive,
      optics = optics,
      opticalDepth = depth,
      pressed = { highlight.pressProgress },
      layerBlock = if (interactive && !reducedMotion) {
        {
          val progress = highlight.pressProgress
          val swell = lerp(1f, 1f + PressSwellDp.dp.toPx() / size.height, progress)

          // A linear follow would let the control run away with the finger. tanh keeps the first
          // few pixels nearly free and then hardens, which is what "attached but soft" feels like.
          val reach = size.minDimension
          val offset = highlight.offset
          translationX = reach * tanh(0.05f * offset.x / reach)
          translationY = reach * tanh(0.05f * offset.y / reach)

          // Stretch along the push, thin across it. The two axes are scaled by the *other*
          // dimension's ratio so a wide button does not distort more than a square one.
          val stretch = LeanReachDp.dp.toPx() / size.height
          val angle = atan2(offset.y, offset.x)
          scaleX = swell + stretch * abs(cos(angle) * offset.x / size.maxDimension) *
            (size.width / size.height).fastCoerceAtMost(1f)
          scaleY = swell + stretch * abs(sin(angle) * offset.y / size.maxDimension) *
            (size.height / size.width).fastCoerceAtMost(1f)
        }
      } else {
        null
      },
    )
    .then(if (interactive) highlight.modifier else Modifier)
    .then(if (interactive) highlight.gestureModifier else Modifier)
}

/**
 * Glass without a backdrop to refract.
 *
 * For the one place the live material genuinely cannot go: a sheet or a dialog, which the platform
 * puts in its own window and which therefore cannot read another window's `GraphicsLayer`. Painted
 * gradients are an honest fallback there, and a bad idea anywhere a real backdrop exists — the whole
 * point of the rest of this file is that painted highlights do not survive contact with a finger.
 */
@Composable
fun Modifier.fluidStaticGlassSurface(
  shape: Shape = FluidCapsuleShape,
  selected: Boolean = false,
  pressure: () -> Float = { 0f },
): Modifier {
  val scheme = MaterialTheme.colorScheme
  val lightSurface = scheme.surface.luminanceIsLightForGlass()
  val body = if (selected) scheme.primary else scheme.surface
  val topLight = Color.White.copy(alpha = if (lightSurface) 0.86f else 0.58f)
  val quietLight = Color.White.copy(alpha = if (lightSurface) 0.24f else 0.16f)
  val lowerShade = Color.Black.copy(alpha = if (lightSurface) 0.30f else 0.52f)
  val innerShade = scheme.onSurface.copy(alpha = if (lightSurface) 0.22f else 0.32f)

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
        0f to body.copy(alpha = if (selected) 0.30f else 0.18f),
        0.46f to body.copy(alpha = if (selected) 0.18f else 0.10f),
        1f to body.copy(alpha = if (selected) 0.24f else 0.15f),
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
    val baseRimWidth = 1.35.dp.toPx()
    val innerRimWidth = 0.95.dp.toPx()

    onDrawBehind {
      val pressed = pressure().coerceIn(0f, 1f)
      drawOutline(outline = outline, brush = bodyBrush, alpha = 1f)
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
        drawOutline(outline = outline, color = scheme.onSurface.copy(alpha = 0.055f * pressed))
      }
    }
  }
}

/**
 * A glass lens for icon actions that live in chrome or another overlay.
 *
 * 44 dp of material inside a 48 dp hit target: the lens is allowed to be the size Apple draws it
 * while the tappable area stays the size accessibility requires.
 */
@Composable
fun FluidGlassIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  onLongClick: (() -> Unit)? = null,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .size(FluidGlassIconTouchTargetSize),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(FluidGlassIconLensSize)
        .glassControlSurface(
          backdrop = backdrop,
          shape = shape,
          selected = selected,
          interactive = enabled,
        )
        // The click sits inside the material, so the glass reacts to the same press the action does.
        .fluidPressable(
          onClick = onClick,
          onLongClick = onLongClick,
          enabled = enabled,
          pressedScale = 1f,
          role = Role.Button,
        ),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}

/**
 * A glass action button.
 *
 * The counterpart of [FluidButton] for anything floating over content: same proportions and same
 * typography, but the surface transmits instead of covering. Use it where a solid accent button
 * would sit on a photograph, a map or a hero image — and keep [FluidButton] for the one solid
 * primary action a form or a settings page is about, which should still look pressed into the page.
 */
@Composable
fun FluidGlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  leading: (@Composable () -> Unit)? = null,
) {
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.onPrimaryContainer
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  Row(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .glassControlSurface(
        backdrop = backdrop,
        shape = shape,
        selected = selected,
        interactive = enabled,
      )
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        pressedScale = 1f,
        role = Role.Button,
      )
      .height(FluidGlassButtonHeight)
      .padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides contentColor) {
      if (leading != null) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) { leading() }
      }
      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
      )
    }
  }
}

private fun Color.luminanceIsLightForGlass(): Boolean =
  (0.299f * red + 0.587f * green + 0.114f * blue) > 0.5f
