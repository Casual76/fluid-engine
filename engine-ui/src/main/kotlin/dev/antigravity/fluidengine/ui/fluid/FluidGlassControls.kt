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
import androidx.compose.ui.util.fastCoerceIn
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight

internal val FluidGlassIconTouchTargetSize: Dp = 48.dp
internal val FluidGlassIconLensSize: Dp = 44.dp

/** Height of a glass action button, matching [FluidButtonDefaults] at `Medium`. */
internal val FluidGlassButtonHeight: Dp = 48.dp

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
 *  * **It swells and stretches.** Held, it grows — since 2.11.0 by up to 6 dp on its short side,
 *    see [fluidPressExpansion], where it used to be 4 dp of height; pushed off-axis, it stretches
 *    along the direction of the push and thins across it. A finger sliding over it ticks.
 *  * **It lights up where you touched it.** An additive hotspot follows the finger, because a
 *    specular reflection adds light. Darkening on press is the plastic-button idiom.
 *
 * All of it is one [FluidPressState], the same one [fluidPressable] drives: the press always
 * arrives before it is released, so a quick tap swells the glass fully instead of a tenth of the
 * way, and the swell, the lean, the light and the optics all read the same number. With reduced
 * motion the swell is halved and the lean dropped, but the press still happens, start to end.
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
 * @param pressState la pressione del controllo a cui questo vetro appartiene, guidata dal suo
 *   [fluidPressable] (`pressState = ` la stessa). Il vetro risponde allora quando il pulsante e'
 *   *premuto* — dopo 100 ms di dito fermo, o al rilascio di un tap piu' rapido — e da li' il gesto
 *   e' suo fino al rilascio, anche col dito fuori dal vetro: vedi [fluidButtonGesture]. Null: il
 *   vetro segue il dito da solo, dal momento in cui scende, come ha sempre fatto, e non consuma
 *   niente. Dalla 2.11.0.
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
  /** See [glassSurface]. Since 2.10.0. */
  frozen: () -> Boolean = { false },
  /** See [glassSurface]. Since 2.10.0. */
  heldWhileMoving: () -> Boolean = { false },
  pressState: FluidPressState? = null,
): Modifier {
  val accent = MaterialTheme.colorScheme.primary
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val onDark = GlassDefaults.isDarkSurface()
  val ownPress = rememberFluidPressState(FluidPressFeedback.Expand)
  val press = pressState ?: ownPress
  val highlight = remember(scope, reducedMotion, onDark, press) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0.4f else 1f },
      onDarkSurface = onDark,
      // Questo e' vetro, non una riga: sul chiaro il bagliore aggiunge luce invece di stendere
      // nero. Col nero il pulsante premuto diventava un riempimento grigio-verde (~#A8B3AE) con un
      // gradiente duro verso il dito, e non rifrangeva piu' niente. Vedi glowRecipe.
      onGlass = true,
      progress = { press.progress },
      touch = { press.touch },
    )
  }
  val selectionTint = remember(tint, selected, accent) {
    if (!selected) {
      tint
    } else {
      // Selection is carried by the accent joining the material, not by an opaque fill replacing it.
      //
      // At a fifth of an alpha this was calibrated for a control standing on a
      // page, where the material has a quiet ground to tint. Over a photograph
      // the ground is already loud — and when the accent is drawn *from* that
      // photograph, which is what an app that takes its colour from the artwork
      // does, the tinted one and the plain one came out the same colour. A
      // third survives that, and it is still the accent joining the material
      // rather than a fill replacing it: the picture goes on showing through.
      tint.copy(
        overlay = accent.copy(alpha = 0.36f),
        fallback = accent.copy(alpha = 0.9f),
        hairline = accent.copy(alpha = 0.45f),
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
      pressed = { press.progress.fastCoerceIn(0f, 1f) },
      frozen = frozen,
      heldWhileMoving = heldWhileMoving,
      layerBlock = if (interactive) {
        {
          // The same transform a solid button draws, here in the layer the renderer corrects for:
          // grow, lean toward the finger through a tanh, stretch along its drag. See
          // fluidPressTransform for the numbers and the reasons.
          val transform = fluidPressTransform(
            feedback = press.feedback,
            progress = press.progress,
            lean = press.lean,
            width = size.width,
            height = size.height,
            dp = density,
            reducedMotion = reducedMotion,
          )
          scaleX = transform.scaleX
          scaleY = transform.scaleY
          translationX = transform.translationX
          translationY = transform.translationY
        }
      } else {
        null
      },
    )
    .then(if (interactive) highlight.modifier else Modifier)
    // Segue il dito da solo solo quando nessuno lo guida: una pressione condivisa la muove il
    // fluidPressable del controllo, e due inseguitori su una pressione scatterebbero due volte a
    // ogni passo del dito.
    .then(if (interactive && pressState == null) Modifier.fluidPressTracking(ownPress, ownsPress = true) else Modifier)
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
  // One press for the click and the glass: the lens swells, leans and lights up on the same
  // timeline the action is confirmed on, and a finger sliding over it ticks once, not twice.
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
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
          pressState = press,
        )
        // The click sits inside the material, so the glass reacts to the same press the action does.
        .fluidPressable(
          onClick = onClick,
          onLongClick = onLongClick,
          enabled = enabled,
          role = Role.Button,
          focusShape = shape,
          pressState = press,
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
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  Row(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.38f)
      .glassControlSurface(
        backdrop = backdrop,
        shape = shape,
        selected = selected,
        interactive = enabled,
        pressState = press,
      )
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        focusShape = shape,
        pressState = press,
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
