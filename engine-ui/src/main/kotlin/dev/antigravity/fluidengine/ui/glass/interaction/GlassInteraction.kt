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
) {

  private val valueAnimationSpec = spring(1f, 1000f, visibilityThreshold)
  private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
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

  fun press() {
    velocityTracker.resetTracking()
    animationScope.launch {
      launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
      launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
      launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
    }
  }

  fun release() {
    animationScope.launch {
      if (value != targetValue) {
        val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
        snapshotFlow { valueAnimation.value }
          .filter { abs(it - valueAnimation.targetValue) < threshold }
          .first()
      }
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
 * The bright spot that follows a finger across a pane of glass.
 *
 * Two things happen at once: the whole surface lifts slightly, and a soft radial hotspot tracks the
 * touch point. Both are additive (`BlendMode.Plus`), because a specular reflection *adds* light —
 * darkening a control to acknowledge a press is a plastic-button idiom, and using it here is most of
 * what made the previous controls read as ordinary buttons with a grey wash.
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
) {

  private val pressProgressAnimationSpec = spring(0.5f, 300f, 0.001f)
  private val positionAnimationSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

  private val pressProgressAnimation = Animatable(0f, 0.001f)
  private val positionAnimation =
    Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

  private var startPosition = Offset.Zero

  val pressProgress: Float get() = pressProgressAnimation.value
  val offset: Offset get() = positionAnimation.value - startPosition

  private val shader =
    if (isRuntimeShaderSupported()) {
      RuntimeShader(
        """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}""",
      )
    } else {
      null
    }

  val modifier: Modifier = Modifier.drawWithContent {
    val progress = pressProgressAnimation.value * strength().coerceIn(0f, 1f)
    if (progress > 0f) {
      if (shader != null) {
        drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
        shader.apply {
          val position = position(size, positionAnimation.value)
          setFloatUniform("size", size.width, size.height)
          setColorUniform("color", Color.White.copy(0.15f * progress))
          setFloatUniform("radius", size.minDimension * 1.5f)
          setFloatUniform(
            "position",
            position.x.fastCoerceIn(0f, size.width),
            position.y.fastCoerceIn(0f, size.height),
          )
        }
        drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
      } else {
        drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
      }
    }

    drawContent()
  }

  val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
    inspectDragGestures(
      onDragStart = { down ->
        startPosition = down.position
        animationScope.launch {
          launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
          launch { positionAnimation.snapTo(startPosition) }
        }
      },
      onDragEnd = { relax() },
      onDragCancel = { relax() },
    ) { change, _ ->
      animationScope.launch { positionAnimation.snapTo(change.position) }
    }
  }

  private fun relax() {
    animationScope.launch {
      launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
      launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
    }
  }
}
