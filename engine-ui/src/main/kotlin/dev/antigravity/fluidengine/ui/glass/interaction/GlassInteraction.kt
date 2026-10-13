/*
 * Copyright 2025 Kyant — Apache License, Version 2.0.
 *
 * Derived from the catalog components of Kyant0/AndroidLiquidGlass, commit
 * b18eb0ff12c616546a68c72e7d0097f1ab286c87:
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DragGestureInspector.kt
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/DampedDragAnimation.kt
 *   app/src/commonMain/kotlin/com/kyant/backdrop/catalog/utils/InteractiveHighlight.kt
 *   https://github.com/Kyant0/AndroidLiquidGlass
 *
 * Those live in the library author's sample app rather than in the published artifact, so there is
 * nothing to depend on and they have to be copied. Changes made by the Fluid Engine
 * (see LICENSES/AndroidLiquidGlass.md):
 *   - the three files were merged into this one and renamed with the engine's Glass prefix;
 *   - kotlin.time.Clock, which is still experimental, was replaced with SystemClock.uptimeMillis();
 *   - the highlight gained a `strength` control and an `enabled` gate, so a disabled or
 *     reduced-motion control can hold still without a second implementation existing;
 *   - 2.11.0: a release no longer cuts a press short. The highlight's press goes through the
 *     engine's FluidPressMotion and can be driven from outside (`progress`, `touch`); the drag
 *     animation's release waits for its press to arrive before relaxing;
 *   - 2.12.0: on a light pane of glass the highlight no longer lays black over it; it adds a lighter,
 *     narrower white spot instead (`onGlass`, `glowRecipe`). The numbers above stay Kyant's for
 *     everything else;
 *   - the engine's formatting.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.antigravity.fluidengine.ui.glass.interaction

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import dev.antigravity.fluidengine.ui.fluid.FluidPressMotion
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Drag recognition without a slop threshold.
 *
 * The platform's `detectDragGestures` waits for a few pixels of travel before it admits a drag has
 * started, which is right for a scrolling list and wrong for a control that is supposed to lean
 * toward the finger the instant it lands. This reports the touch down as frame zero of the drag.
 */
suspend fun PointerInputScope.inspectDragGestures(
  onDragStart: (down: PointerInputChange) -> Unit = {},
  onDragEnd: (change: PointerInputChange) -> Unit = {},
  onDragCancel: () -> Unit = {},
  onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
  awaitEachGesture {
    val initialDown = awaitFirstDown(false, PointerEventPass.Initial)

    val down = awaitFirstDown(false)
    val drag = initialDown

    onDragStart(down)
    onDrag(drag, Offset.Zero)
    val upEvent = drag(
      pointerId = drag.id,
      onDrag = { onDrag(it, it.positionChange()) },
    )
    if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
  }
}

private suspend inline fun AwaitPointerEventScope.drag(
  pointerId: PointerId,
  onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
  val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
  if (isPointerUp) return null
  var pointer = pointerId
  while (true) {
    val change = awaitDragOrUp(pointer) ?: return null
    if (change.isConsumed) return null
    if (change.changedToUpIgnoreConsumed()) return change
    onDrag(change)
    pointer = change.id
  }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
  pointerId: PointerId,
): PointerInputChange? {
  var pointer = pointerId
  while (true) {
    val event = awaitPointerEvent()
    val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
    if (dragEvent.changedToUpIgnoreConsumed()) {
      val otherDown = event.changes.fastFirstOrNull { it.pressed }
      if (otherDown == null) return dragEvent else pointer = otherDown.id
    } else {
      if (dragEvent.previousPosition != dragEvent.position) return dragEvent
    }
  }
}

/**
 * The physics behind a control that can be pushed around.
 *
 * Holds five springs at once, and it is the fact that they are five rather than one that makes the
 * result read as a physical object:
 *
 *  * [value] follows the finger stiffly, so the thing under the touch never lags behind it;
 *  * [pressProgress] is the slower "I am being held" signal that thickens the glass;
 *  * [scaleX] and [scaleY] settle on *different* damping ratios, which is what produces the
 *    squash — a single scale spring produces a balloon;
 *  * [velocity] is tracked separately so the caller can stretch the control along its direction of
 *    travel and let it snap back when it stops.
 *
 * [release] deliberately waits for [value] to have nearly caught up with its target before it
 * relaxes anything. Letting the press collapse the moment the finger leaves is what makes a control
 * look like it was dropped rather than let go.
 */
@Stable
class GlassDragAnimation(
  private val animationScope: CoroutineScope,
  val initialValue: Float,
  val valueRange: ClosedRange<Float>,
  val visibilityThreshold: Float,
  val initialScale: Float,
  val pressedScale: Float,
  val onDragStarted: GlassDragAnimation.(position: Offset) -> Unit = {},
  val onDragStopped: GlassDragAnimation.() -> Unit = {},
  val onDrag: GlassDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit = { _, _ -> },
  /**
   * How hard the tracked velocity is damped — the number the stretch is built out of.
   *
   * The library's own 0.5 is underdamped, and underdamped is a choice with a sound: the velocity
   * *rings* after every change of speed, so anything driven by it keeps wobbling once the finger
   * has stopped. On a lens dragged across a row of tabs that reads as the indicator jelly-ing at
   * each crossing rather than following the hand.
   *
   * 1 is critically damped: the stretch tracks the drag and stops when the drag stops. The default
   * is left where it was so nothing that already depends on the ring changes underneath it.
   */
  val velocityDampingRatio: Float = 0.5f,
) {

  private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
  private val velocityAnimationSpec =
    spring(velocityDampingRatio, 300f, visibilityThreshold * 10f)
  private val pressProgressAnimationSpec = spring(1f, 1000f, 0.001f)
  private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
  private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

  private val valueAnimation = Animatable(initialValue, visibilityThreshold)
  private val velocityAnimation = Animatable(0f, 5f)
  private val pressProgressAnimation = Animatable(0f, 0.001f)
  private val scaleXAnimation = Animatable(initialScale, 0.001f)
  private val scaleYAnimation = Animatable(initialScale, 0.001f)

  private val mutatorMutex = MutatorMutex()
  private val velocityTracker = VelocityTracker()

  val value: Float get() = valueAnimation.value
  val progress: Float
    get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
  val targetValue: Float get() = valueAnimation.targetValue
  val pressProgress: Float get() = pressProgressAnimation.value
  val scaleX: Float get() = scaleXAnimation.value
  val scaleY: Float get() = scaleYAnimation.value
  val velocity: Float get() = velocityAnimation.value

  /**
   * True while any of the springs is moving: the thumb following a drag, the squash, the press.
   * The window in which the glass this animation drives is genuinely live. Since 2.10.0.
   */
  val isAnimating: Boolean
    get() = valueAnimation.isRunning || velocityAnimation.isRunning ||
      pressProgressAnimation.isRunning || scaleXAnimation.isRunning || scaleYAnimation.isRunning

  val modifier: Modifier = Modifier.pointerInput(Unit) {
    inspectDragGestures(
      onDragStart = { down ->
        onDragStarted(down.position)
        press()
      },
      onDragEnd = {
        onDragStopped()
        release()
      },
      onDragCancel = {
        onDragStopped()
        release()
      },
    ) { _, dragAmount ->
      onDrag(size, dragAmount)
    }
  }

  // Which press a pending release belongs to. A release waits (for the value, for the press to
  // arrive), and a new press during the wait used to be relaxed by the old release as soon as the
  // wait ended — the thumb deflating under a finger that was still on it.
  private var pressGeneration = 0

  fun press() {
    pressGeneration++
    velocityTracker.resetTracking()
    animationScope.launch {
      launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
      launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
      launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
    }
  }

  fun release() {
    val generation = pressGeneration
    animationScope.launch {
      if (value != targetValue) {
        val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
        snapshotFlow { valueAnimation.value }
          .filter { abs(it - valueAnimation.targetValue) < threshold }
          .first()
      }
      // A tap is shorter than the press. Letting go now would relax a thumb that had swollen a
      // tenth of the way, so the press is allowed to arrive first — the rule FluidPressMotion
      // applies to every button, here for the thumbs and lenses that are dragged. Since 2.11.0.
      if (pressProgressAnimation.targetValue == 1f &&
        pressProgressAnimation.value < FluidPressMotion.ArrivalFraction
      ) {
        snapshotFlow { pressProgressAnimation.value }
          .filter {
            it >= FluidPressMotion.ArrivalFraction || pressProgressAnimation.targetValue != 1f
          }
          .first()
      }
      if (generation != pressGeneration) return@launch
      launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
      launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
      launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
    }
  }

  fun updateValue(value: Float) {
    val targetValue = value.coerceIn(valueRange)
    animationScope.launch {
      valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() }
    }
  }

  fun animateToValue(value: Float) {
    animationScope.launch {
      mutatorMutex.mutate {
        press()
        val targetValue = value.coerceIn(valueRange)
        launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
        if (velocity != 0f) {
          launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
        }
        release()
      }
    }
  }

  fun snapToValue(value: Float) {
    animationScope.launch { valueAnimation.snapTo(value.coerceIn(valueRange)) }
  }

  private fun updateVelocity() {
    velocityTracker.addPosition(SystemClock.uptimeMillis(), Offset(value, 0f))
    val targetVelocity =
      velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
    animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
  }
}

/**
 * Come si disegna il bagliore di [GlassTouchHighlight]: colore, modo di comporre e forma.
 *
 * Fuori dalla classe e senza Compose in mezzo perche' e' la parte che si e' sbagliata una volta, e
 * una scelta che non si puo' leggere senza montare un pulsante e' una scelta che nessuno rilegge.
 *
 * @param veil peso della luce che alza tutta la faccia, ovunque sia il dito.
 * @param spot peso del punto che segue il dito, al suo centro.
 * @param radiusScale raggio del punto, in lati corti della superficie.
 * @param core frazione del raggio dentro cui il punto e' al massimo: 0,5 lascia un plateau e poi
 *   scende, 0 e' una campana che scende dal centro.
 * @param flat peso del ripiego sotto API 33, dove non c'e' lo shader e il bagliore e' una lavata
 *   uniforme.
 */
internal data class GlowRecipe(
  val tone: Color,
  val blend: BlendMode,
  val veil: Float,
  val spot: Float,
  val radiusScale: Float,
  val core: Float,
  val flat: Float,
)

/** I numeri di Kyant (`InteractiveHighlight`), identici: tutto quello che non e' vetro chiaro. */
private fun kyantGlow(tone: Color, blend: BlendMode) = GlowRecipe(
  tone = tone,
  blend = blend,
  veil = 0.08f,
  spot = 0.15f,
  radiusScale = 1.5f,
  core = 0.5f,
  flat = 0.25f,
)

/**
 * Il bagliore che si accende sotto il dito, deciso da due fatti: da che parte sta la superficie
 * ([onDarkSurface]) e se quella superficie e' vetro ([onGlass]).
 *
 *  - **Scuro**: bianco additivo, com'e' sempre stato e com'e' in Kyant. Vale anche per il vetro.
 *  - **Chiaro, ma non vetro** (una riga, un pannello opaco): nero sopra, perche' li' non c'e' luce
 *    da aggiungere e non c'e' niente che debba restare trasparente. E' il comportamento di prima.
 *  - **Chiaro e vetro**: bianco ancora additivo, ma piccolo e stretto. Il nero sopra un vetro non
 *    era un bagliore, era inchiostro: stendeva ~22% di nero su una faccia che trasmette, e il
 *    pulsante smetteva di essere vetro e diventava un riempimento grigio-verde con l'etichetta
 *    spenta. Ma i numeri di Kyant non si possono riportare tali e quali: sono tarati su un fondo
 *    medio, e sommano 0,23 (58 livelli su 255) su una faccia che ne ha 20-35 di margine. Il verde e
 *    il blu arrivano al tetto per primi, quindi la faccia diventa un ciano-bianco piatto, e il
 *    plateau largo mezzo raggio copre tutto un pulsante largo cento dp: un riempimento di un altro
 *    colore. Qui il picco e' 0,07 (circa 18 livelli, sotto il margine di una faccia a 0,92) e il
 *    punto e' una campana che scende dal dito e alla fine del pulsante e' gia' zero. Si era provato
 *    `Screen`, che non taglia mai: ma aggiunge `alpha * (1 - fondo)`, cioe' pochissimo sul chiaro e
 *    moltissimo su un vetro tinto di accento, dove sbiancava mezzo pulsante.
 */
internal fun glowRecipe(onDarkSurface: Boolean, onGlass: Boolean): GlowRecipe = when {
  onDarkSurface -> kyantGlow(Color.White, BlendMode.Plus)
  onGlass -> GlowRecipe(
    tone = Color.White,
    blend = BlendMode.Plus,
    veil = 0.015f,
    spot = 0.055f,
    radiusScale = 1.15f,
    core = 0f,
    flat = 0.08f,
  )
  else -> kyantGlow(Color.Black, BlendMode.SrcOver)
}

/**
 * The bright spot that follows a finger across a pane of glass.
 *
 * Two things happen at once: the whole surface lifts slightly, and a soft radial hotspot tracks the
 * touch point. On a dark surface both are additive (`BlendMode.Plus`), because a specular reflection
 * *adds* light — darkening a control to acknowledge a press is a plastic-button idiom, and using it
 * here is most of what made the previous controls read as ordinary buttons with a grey wash.
 *
 * On a light one there is no light left to add, so the same gesture has to take some away instead;
 * see [onDarkSurface]. Left additive, a press on an opaque light surface simply did not happen.
 * A pane of *glass* is the exception, and it is the reason for [onGlass]: it transmits, so laying
 * ink over it turns it into a grey fill — see [glowRecipe].
 *
 * Below API 33 there is no AGSL, so the hotspot degrades to a flat lift. The control still responds;
 * it simply stops knowing where on itself it was touched.
 */
@Stable
class GlassTouchHighlight(
  val animationScope: CoroutineScope,
  /** Overall weight. 0 turns the highlight off without changing any other behaviour. */
  val strength: () -> Float = { 1f },
  /** Where the hotspot sits. Defaults to the touch point; a tab bar pins it to the indicator. */
  val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
  /**
   * Whether the surface under the finger is a dark one.
   *
   * Not a colour but a flag, because the colour is only half of it: adding black adds nothing, so
   * the blend has to turn over with the paint. Ask `GlassDefaults.isDarkSurface()` at the call site
   * and pass the answer — this is not a composable, and the surface it lights is not always the one
   * the theme is on.
   */
  val onDarkSurface: Boolean = true,
  /**
   * La superficie che il bagliore illumina e' un pannello di vetro, che trasmette lo sfondo.
   *
   * Su un fondo chiaro cambia il modo di accendersi: invece di stendere nero sopra, che su un vetro
   * lo trasforma in un riempimento grigio, aggiunge luce bianca, piu' leggera e piu' stretta di
   * quella di Kyant (vedi [glowRecipe]). Su un fondo scuro non cambia niente, e non cambia niente
   * per chi non lo passa (una riga, un pannello opaco): il nero sopra e' ancora la risposta giusta
   * dove non c'e' niente che debba restare trasparente.
   * `glassControlSurface` lo passa. Dalla 2.12.0.
   */
  val onGlass: Boolean = false,
  /**
   * A press driven by someone else, 0..1. Given, the highlight lights up with it instead of with
   * its own spring, and [gestureModifier] is not needed: the control that owns the press — see
   * `FluidPressState` — tells the glass and the light the same thing. Since 2.11.0.
   */
  val progress: (() -> Float)? = null,
  /** Where the finger is, for a press driven by someone else. Since 2.11.0. */
  val touch: (() -> Offset)? = null,
) {

  private val recipe = glowRecipe(onDarkSurface, onGlass)
  private val glowTone = recipe.tone
  private val glowBlend = recipe.blend

  private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

  // Its own press, when nobody drives it, goes through the engine's press motion: the light waits
  // for the rise to arrive before it fades, so a quick tap lights the glass fully instead of
  // blinking a tenth of the way up. Same spring both ways as before. Since 2.11.0.
  private val pressMotion = FluidPressMotion(animationScope).apply {
    pressSpec = spring(0.5f, 300f, 0.001f)
    releaseSpec = spring(0.5f, 300f, 0.001f)
  }
  private val positionAnimation =
    Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

  private var startPosition = Offset.Zero

  val pressProgress: Float get() = progress?.invoke() ?: pressMotion.value
  val offset: Offset get() = positionAnimation.value - startPosition

  /**
   * True while the highlight is on screen or moving: pressed, following the finger, or settling
   * back after a release. The window in which the glass under it is genuinely live. Since 2.10.0.
   */
  val isAnimating: Boolean
    get() = pressMotion.isRunning || positionAnimation.isRunning || pressProgress > 0f

  private val shader =
    if (isRuntimeShaderSupported()) {
      RuntimeShader(
        """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float core;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * core, dist);
    return color * intensity;
}""",
      )
    } else {
      null
    }

  val modifier: Modifier = Modifier.drawWithContent {
    // Clamped: a driven press comes from a spring that may pass its ends, and light does not.
    val glow = pressProgress.coerceIn(0f, 1f) * strength().coerceIn(0f, 1f)
    if (glow > 0f) {
      if (shader != null) {
        drawRect(glowTone.copy(recipe.veil * glow), blendMode = glowBlend)
        shader.apply {
          val position = position(size, touch?.invoke() ?: positionAnimation.value)
          setFloatUniform("size", size.width, size.height)
          setColorUniform("color", glowTone.copy(recipe.spot * glow))
          setFloatUniform("radius", size.minDimension * recipe.radiusScale)
          setFloatUniform("core", recipe.core)
          setFloatUniform(
            "position",
            position.x.fastCoerceIn(0f, size.width),
            position.y.fastCoerceIn(0f, size.height),
          )
        }
        drawRect(ShaderBrush(shader.asComposeShader()), blendMode = glowBlend)
      } else {
        drawRect(glowTone.copy(recipe.flat * glow), blendMode = glowBlend)
      }
    }

    drawContent()
  }

  val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
    inspectDragGestures(
      onDragStart = { down ->
        startPosition = down.position
        pressMotion.press()
        animationScope.launch { positionAnimation.snapTo(startPosition) }
      },
      onDragEnd = { relax() },
      onDragCancel = { relax() },
    ) { change, _ ->
      animationScope.launch { positionAnimation.snapTo(change.position) }
    }
  }

  private fun relax() {
    pressMotion.release()
    animationScope.launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
  }
}
