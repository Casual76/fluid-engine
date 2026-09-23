package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
  /**
   * La sagoma in cui mostrare il fuoco da tastiera con un velo dell'accento, oltre al farsi
   * avanti. Serve a chi non ha una superficie propria che si vede avanzare — un segmento, un'icona
   * di vetro — e dove il 3% su un'etichetta non si nota. Null: solo il farsi avanti.
   */
  focusShape: Shape? = null,
): Modifier {
  if (onClick == null && onLongClick == null) return this

  val resolvedInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
  val pressed by resolvedInteractionSource.collectIsPressedAsState()
  // Il fuoco da tastiera e' il gesto opposto alla pressione: la cosa si fa avanti invece di
  // cedere, come su tvOS. Senza, con una tastiera attaccata al tablet Tab spostava il fuoco su
  // elementi che non lo mostravano in nessun modo. Col dito il fuoco non arriva mai qui (in
  // modalita' touch un clickable non lo prende), quindi al tocco non cambia niente.
  val focused by resolvedInteractionSource.collectIsFocusedAsState()
  val scale = remember { Animatable(1f) }
  val haptics = LocalFluidHaptics.current

  val focusWash by animateFloatAsState(
    targetValue = if (focused && focusShape != null) 1f else 0f,
    animationSpec = if (focused) FluidMotion.instant() else FluidMotion.fadeOut(160),
    label = "fluid focus wash",
  )
  val focusColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

  LaunchedEffect(pressed, focused, pressedScale) {
    when {
      pressed -> scale.animateTo(pressedScale, FluidMotion.instant())
      focused -> scale.animateTo(FocusedScale, FluidMotion.fluid())
      else -> scale.animateTo(1f, FluidMotion.fluid())
    }
  }

  return this
    .drawWithContent {
      if (focusShape != null && focusWash > 0.001f) {
        drawOutline(
          focusShape.createOutline(size, layoutDirection, this),
          focusColor.copy(alpha = focusColor.alpha * focusWash),
        )
      }
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
  // Una riga non si fa avanti — romperebbe il gruppo, come la scala alla pressione — quindi il
  // fuoco da tastiera e' un bordo dell'accento lungo la riga. Solo da tastiera: al tocco una riga
  // non prende mai il fuoco.
  val focused by interactionSource.collectIsFocusedAsState()
  val focusRing by animateFloatAsState(
    targetValue = if (focused) 1f else 0f,
    animationSpec = if (focused) FluidMotion.instant() else FluidMotion.fadeOut(160),
    label = "fluid row focus",
  )
  val focusColor = MaterialTheme.colorScheme.primary
  val haptics = LocalFluidHaptics.current
  val highlight by animateFloatAsState(
    targetValue = if (pressed && animateFeedback) 1f else 0f,
    // Appearing instantly and fading out unhurriedly is what stops a quick tap from looking like a
    // flicker: the highlight is always visible for at least the length of the fade.
    animationSpec = if (pressed) FluidMotion.instant() else FluidMotion.fadeOut(260),
    label = "fluid row highlight",
  )

  return this
    .drawWithContent {
      drawContent()
      if (focusRing <= 0.001f) return@drawWithContent
      val width = 2.dp.toPx()
      val color = focusColor.copy(alpha = focusRing)
      if (shape == null) {
        drawRect(
          color = color,
          topLeft = Offset(width / 2, width / 2),
          size = Size(size.width - width, size.height - width),
          style = Stroke(width),
        )
      } else {
        drawOutline(shape.createOutline(size, layoutDirection, this), color, style = Stroke(width))
      }
    }
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

/** Quanto si fa avanti un elemento col fuoco da tastiera: abbastanza da vederlo, non da spostare i vicini. */
private const val FocusedScale = 1.03f
