/*
 * The thumb, the squashed copy of the track it refracts, and the numbers that shape both, are
 * ported from Kyant0's LiquidToggle.
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Ported and not reinvented, deliberately. The first version of this file was a switch built out
 * of the engine's own `glassSurface`: a translucent track over the page with a white lens on it.
 * It was a reasonable-looking control and it was the wrong one — what makes the real one feel like
 * glass is not that the track transmits, it is that the *thumb* is the only pane, and that what it
 * refracts is a copy of the track squashed flat, which opens up under the finger. That
 * construction has no equivalent in a general surface, so it is taken as it was written.
 *
 * What changed on the way in is named where it happens: the colour is the app's accent instead of
 * the source's hardcoded iOS green, the control has an `enabled` state and haptics because every
 * other control in this engine does, and it answers an accessibility service.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.flow.collectLatest

/**
 * The switch from the catalogue the rest of this engine's glass comes from.
 *
 * [FluidSwitch] is the one every app is using and it is not being replaced: it is a filled track
 * with a lens for a thumb, it reads at a glance down a column of twenty settings rows, and it is
 * the right control there. This is the other one, and the difference is not decoration — it is a
 * different construction:
 *
 *  * **The track is a recording, not a background.** It paints itself into its own layer, and that
 *    layer is what the thumb reads. Nothing else in the engine has one pane whose entire job is to
 *    be looked at through another.
 *  * **The thumb is the glass, and at rest it is opaque.** White at full strength, a pill with a
 *    shadow. Pressed, that white fades out completely and what is left is a lens: the page behind
 *    the switch, plus a copy of the track squashed to nothing vertically and two thirds
 *    horizontally, opening to three quarters of both as the finger stays down. The accent under
 *    the thumb visibly *unfolds*. That is the whole effect, and it only exists while it is held.
 *  * **It is dragged, not tapped.** The thumb follows the finger across the track, stretches along
 *    its direction of travel by how fast it is going, and lands on whichever side it was let go
 *    nearest. A tap still toggles it, through the same gesture path.
 *
 * The one thing changed from the source is the colour: the accent is the app's
 * [androidx.compose.material3.ColorScheme.primary], not the source's hardcoded iOS green, so this
 * switch belongs to whatever palette the app is wearing — including a Material You one that
 * follows the wallpaper.
 *
 * Below API 31 there is no `RenderEffect`, so the thumb is a plain white pill with a shadow on a
 * filled track. That is the same switch with the one thing it cannot have taken out, which is what
 * every other surface in the engine does there.
 *
 * @param backdrop what the thumb refracts, under the track's own copy. Defaults to the surface in
 *   scope: a screen's ambient canvas if it has one, otherwise the chrome backdrop, otherwise
 *   nothing — and with nothing there the thumb still has the track to bend, which is most of it.
 */
@Composable
fun FluidGlassSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
) {
  val scheme = MaterialTheme.colorScheme
  val dark = GlassDefaults.isDarkSurface()
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  val accentColor = scheme.primary
  // The source's neutral, kept. Taking it from `onSurface` instead would tint the *unfilled* half
  // of the control with the accent on any Material You palette, and "off" is precisely the state
  // that is not the accent.
  val trackColor =
    if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.2f)

  val density = LocalDensity.current
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val dragWidth = with(density) { DragWidth.toPx() }
  val animationScope = rememberCoroutineScope()
  val currentChecked by rememberUpdatedState(checked)
  val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

  var didDrag by remember { mutableStateOf(false) }
  var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }

  val dragAnimation = remember(animationScope) {
    GlassDragAnimation(
      animationScope = animationScope,
      initialValue = fraction,
      valueRange = 0f..1f,
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = 1.5f,
      onDragStopped = {
        val wanted = if (didDrag) targetValue >= 0.5f else !currentChecked
        didDrag = false
        fraction = if (wanted) 1f else 0f
        if (wanted != currentChecked) {
          haptics.play(if (wanted) FluidHapticEvent.ToggleOn else FluidHapticEvent.ToggleOff)
          currentOnCheckedChange?.invoke(wanted)
        }
      },
      onDrag = { _, dragAmount ->
        if (!didDrag) didDrag = dragAmount.x != 0f
        val delta = dragAmount.x / dragWidth
        fraction = if (isLtr) {
          (fraction + delta).fastCoerceIn(0f, 1f)
        } else {
          (fraction - delta).fastCoerceIn(0f, 1f)
        }
      },
    )
  }

  // Two directions, and they are not the same animation. This one is the finger: the fraction
  // moves under a drag and the thumb follows it without a press ramp of its own.
  LaunchedEffect(dragAnimation, reducedMotion) {
    snapshotFlow { fraction }.collectLatest { value ->
      if (reducedMotion) dragAnimation.snapToValue(value) else dragAnimation.updateValue(value)
    }
  }
  // And this one is the caller: a state changed somewhere else arrives with the press-and-settle,
  // so an external toggle reads as a toggle rather than as a jump. Also the correction path — a
  // caller that refuses the change (a switch whose state is owned elsewhere and did not move) puts
  // the thumb back where it belongs instead of leaving it lying about a state that never happened.
  LaunchedEffect(dragAnimation, reducedMotion) {
    // `currentChecked` and not `checked`: this effect is keyed on the animation, so it is not
    // restarted when the parameter changes, and a captured parameter is frozen at the composition
    // that started it. Reading the `rememberUpdatedState` is a snapshot read, which is the thing
    // `snapshotFlow` can actually see.
    snapshotFlow { currentChecked }.collectLatest { isChecked ->
      val target = if (isChecked) 1f else 0f
      if (target != fraction) {
        fraction = target
        if (reducedMotion) {
          dragAnimation.snapToValue(target)
        } else {
          dragAnimation.animateToValue(target)
        }
      }
    }
  }

  val trackBackdrop = rememberLayerBackdrop()
  // The track, flattened. At rest it is squashed to nothing vertically and two thirds
  // horizontally, and it opens to three quarters of both while the thumb is held — so what the
  // lens shows is not a picture of the track but the track *unfolding* under the finger.
  val squashedTrack: DrawScope.(DrawScope.() -> Unit) -> Unit = remember(dragAnimation) {
    { drawTrack ->
      val progress = dragAnimation.pressProgress
      scale(lerp(2f / 3f, 0.75f, progress), lerp(0f, 0.75f, progress)) { drawTrack() }
    }
  }
  val thumbBackdrop: Backdrop = rememberCombinedBackdrop(
    backdrop.backdrop,
    rememberBackdrop(trackBackdrop, squashedTrack),
  )

  val interactive = enabled && onCheckedChange != null

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .size(TrackWidth, TrackHeight)
      .semantics {
        role = Role.Switch
        toggleableState = ToggleableState(checked)
      },
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      Modifier
        .layerBackdrop(trackBackdrop)
        .clip(FluidCapsuleShape)
        .drawBehind { drawRect(lerp(trackColor, accentColor, dragAnimation.value)) }
        .size(TrackWidth, TrackHeight),
    )

    Box(
      Modifier
        .graphicsLayer {
          val padding = ThumbInset.toPx()
          translationX = if (isLtr) {
            lerp(padding, padding + dragWidth, dragAnimation.value)
          } else {
            lerp(-padding, -(padding + dragWidth), dragAnimation.value)
          }
        }
        .then(if (interactive) dragAnimation.modifier else Modifier)
        .then(
          if (isRenderEffectSupported()) {
            Modifier.drawBackdrop(
              backdrop = thumbBackdrop,
              shape = { FluidCapsuleShape },
              effects = {
                val progress = dragAnimation.pressProgress
                blur(8f.dp.toPx() * (1f - progress))
                lens(5f.dp.toPx() * progress, 10f.dp.toPx() * progress, chromaticAberration = true)
              },
              highlight = {
                val progress = dragAnimation.pressProgress
                Highlight.Ambient.copy(
                  width = Highlight.Ambient.width / 1.5f,
                  blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                  alpha = progress,
                )
              },
              shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
              innerShadow = {
                val progress = dragAnimation.pressProgress
                InnerShadow(radius = 4f.dp * progress, alpha = progress)
              },
              layerBlock = {
                scaleX = dragAnimation.scaleX
                scaleY = dragAnimation.scaleY
                val velocity = dragAnimation.velocity / 50f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
              },
              // Opaque at rest and gone under the finger: this white is what the lens replaces.
              // Everything else here is the material; this one line is the state.
              onDrawSurface = {
                drawRect(Color.White.copy(alpha = 1f - dragAnimation.pressProgress))
              },
            )
          } else {
            Modifier
              .shadow(2.dp, FluidCapsuleShape)
              .clip(FluidCapsuleShape)
              .drawBehind { drawRect(Color.White) }
          },
        )
        .size(ThumbWidth, ThumbHeight),
    )
  }
}

/**
 * 64 x 28, with a 40 x 24 thumb and two dp of padding.
 *
 * The source's numbers, kept whole. They are not interchangeable with [FluidSwitch]'s 56 x 31: the
 * thumb here is a *pill*, not a disc, and twenty dp of travel under a forty dp thumb is what makes
 * the squashed track underneath read as something being uncovered rather than something sliding.
 */
private val TrackWidth: Dp = 64.dp
private val TrackHeight: Dp = 28.dp
private val ThumbWidth: Dp = 40.dp
private val ThumbHeight: Dp = 24.dp
private val ThumbInset: Dp = 2.dp

/** How far the thumb travels, and therefore how far a finger has to drag to flip the switch. */
private val DragWidth: Dp = 20.dp
