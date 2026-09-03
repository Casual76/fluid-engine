package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.scale
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics

/**
 * Press feedback in the shape Apple gives it: the element itself yields under the finger.
 *
 * Deliberately different from the Material default in three ways, each of which is a reason the
 * previous treatment read as "off":
 *
 *  * **No ripple.** A ripple announces a touch point; a scale announces that the *thing you touched*
 *    responded. The second is what makes a surface feel physical.
 *  * **No shape morphing here.** Animating a corner radius from a screen forces a re-clip every
 *    frame and makes the element's silhouette wobble against its neighbours. Since 1.9.0 shape
 *    morphing exists, but it lives in ONE place — Fluid-physics
 *    ([dev.antigravity.fluidengine.ui.fluidphysics]) — which earns it with its own discipline: the
 *    layer clip never chases the shape (the shader's alpha mask sculpts the silhouette), and the
 *    morph is transient by contract (at rest the surface holds one stable Shape instance). A
 *    corner radius animated ad hoc in a screen is still the bug it always was.
 *  * **Asymmetric timing.** Pressing in is near-instant so the response beats the finger; releasing
 *    is slower and slightly springy, which is what sells the elasticity.
 *
 * The scale is applied in the draw phase, so a press invalidates only this node without recomposing
 * anything or keeping a separate RenderNode alive for every clickable card while it is at rest.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fluidPressable(
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  pressedScale: Float = 0.974f,
  role: androidx.compose.ui.semantics.Role? = null,
  interactionSource: MutableInteractionSource? = null,
  /** Cosa si sente al rilascio; null per un controllo che vibra gia' per conto suo. */
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
): Modifier {
  if (onClick == null && onLongClick == null) return this

  val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
  val pressed by resolvedInteractionSource.collectIsPressedAsState()
  val scale = remember { Animatable(1f) }
  val haptics = LocalFluidHaptics.current

  LaunchedEffect(pressed, pressedScale) {
    if (pressed) {
      scale.animateTo(pressedScale, FluidMotion.instant())
    } else {
      scale.animateTo(1f, FluidMotion.fluid())
    }
  }

  return this
    .drawWithContent {
      val value = scale.value
      if (value == 1f) {
        drawContent()
      } else {
        scale(scaleX = value, scaleY = value, pivot = center) {
          this@drawWithContent.drawContent()
        }
      }
    }
    .combinedClickable(
      interactionSource = resolvedInteractionSource,
      indication = null,
      enabled = enabled,
      role = role,
      onClick = {
        haptic?.let { haptics.play(it) }
        onClick?.invoke()
      },
      onLongClick = onLongClick?.let {
        {
          haptics.play(FluidHapticEvent.GestureStart)
          it()
        }
      },
    )
}

/**
 * Press feedback for a row inside a grouped list.
 *
 * Rows must not scale: a single row shrinking inside a stack of identical rows breaks the group's
 * silhouette. iOS instead tints the row's background for as long as the finger is down, which is
 * what this does.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.fluidRowPressable(
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  animateFeedback: Boolean = true,
  shape: Shape? = null,
  highlightColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
): Modifier {
  if (onClick == null && onLongClick == null) return this

  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val haptics = LocalFluidHaptics.current
  val highlight by animateFloatAsState(
    targetValue = if (pressed && animateFeedback) 1f else 0f,
    // Appearing instantly and fading out unhurriedly is what stops a quick tap from looking like a
    // flicker: the highlight is always visible for at least the length of the fade.
    animationSpec = if (pressed) FluidMotion.instant() else FluidMotion.fadeOut(260),
    label = "fluid row highlight",
  )

  return this
    .drawBehind {
      if (highlight <= 0.001f) return@drawBehind
      val color = highlightColor.copy(alpha = highlightColor.alpha * highlight)
      if (shape == null) {
        drawRect(color)
      } else {
        drawOutline(shape.createOutline(size, layoutDirection, this), color)
      }
    }
    .combinedClickable(
      interactionSource = interactionSource,
      indication = null,
      enabled = enabled,
      onClick = {
        haptic?.let { haptics.play(it) }
        onClick?.invoke()
      },
      onLongClick = onLongClick?.let {
        {
          haptics.play(FluidHapticEvent.GestureStart)
          it()
        }
      },
    )
}
