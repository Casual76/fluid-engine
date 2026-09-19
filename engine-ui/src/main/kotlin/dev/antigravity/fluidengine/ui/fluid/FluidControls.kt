package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.launch

/**
 * How much a control's colour says about it.
 *
 * The palette gives the interface exactly one accent, so the weight of a button is carried by how
 * much of that accent it is allowed to use, not by a different colour per button.
 */
enum class FluidButtonStyle {
  /** The one action the screen is about. Solid accent. */
  Filled,

  /** A secondary action that still deserves a surface. Accent at low opacity. */
  Tinted,

  /** Everything else. Accent text, no background — iOS uses this far more than the other two. */
  Plain,

  /** Deletes, revokes, logs out. Red, and only ever one per screen. */
  Destructive,
}

enum class FluidButtonSize { Large, Medium, Small }

object FluidButtonDefaults {
  fun height(size: FluidButtonSize): Dp = when (size) {
    FluidButtonSize.Large -> 50.dp
    FluidButtonSize.Medium -> 42.dp
    FluidButtonSize.Small -> 32.dp
  }

  fun horizontalPadding(size: FluidButtonSize): Dp = when (size) {
    FluidButtonSize.Large -> 22.dp
    FluidButtonSize.Medium -> 18.dp
    FluidButtonSize.Small -> 14.dp
  }

  fun shape(size: FluidButtonSize): Shape = when (size) {
    FluidButtonSize.Large -> ContinuousCornerShape(14.dp)
    FluidButtonSize.Medium -> ContinuousCornerShape(12.dp)
    FluidButtonSize.Small -> FluidCapsuleShape
  }
}

/**
 * A button.
 *
 * Three things separate this from the Material one, and all three are things a person notices
 * without being able to name them: it scales under the finger instead of rippling, its corners are
 * continuous, and a disabled button fades rather than turning grey — the greyed-out disabled state
 * is a Material signature, and it also destroys the colour relationship the rest of the screen is
 * built on.
 */
@Composable
fun FluidButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  style: FluidButtonStyle = FluidButtonStyle.Filled,
  size: FluidButtonSize = FluidButtonSize.Medium,
  enabled: Boolean = true,
  loading: Boolean = false,
  fillWidth: Boolean = false,
  leading: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val container = when (style) {
    FluidButtonStyle.Filled -> scheme.primary
    FluidButtonStyle.Tinted -> scheme.primaryContainer
    FluidButtonStyle.Destructive -> scheme.errorContainer
    FluidButtonStyle.Plain -> Color.Transparent
  }
  val content = when (style) {
    FluidButtonStyle.Filled -> scheme.onPrimary
    FluidButtonStyle.Tinted -> scheme.onPrimaryContainer
    FluidButtonStyle.Destructive -> scheme.onErrorContainer
    FluidButtonStyle.Plain -> scheme.primary
  }
  val shape = FluidButtonDefaults.shape(size)

  Box(
    modifier = modifier
      .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
      .alpha(if (enabled) 1f else 0.4f)
      .clip(shape)
      .background(container)
      .fluidPressable(onClick = onClick, enabled = enabled && !loading, role = Role.Button)
      .height(FluidButtonDefaults.height(size))
      .padding(horizontal = FluidButtonDefaults.horizontalPadding(size)),
    contentAlignment = Alignment.Center,
  ) {
    val labelStyle = when (size) {
      FluidButtonSize.Large -> MaterialTheme.typography.bodyLarge
      FluidButtonSize.Medium -> MaterialTheme.typography.bodyLarge
      FluidButtonSize.Small -> MaterialTheme.typography.bodyMedium
    }.copy(fontWeight = FontWeight.SemiBold)

    CompositionLocalProvider(LocalContentColor provides content) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // The spinner takes the icon's place rather than replacing the label, so the button keeps
        // its width and the row it sits in does not reflow the moment work starts.
        if (loading) {
          Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            FluidSpinner(size = 16.dp, color = content)
          }
        } else if (leading != null) {
          Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
        }
        Text(text = text, style = labelStyle, textAlign = TextAlign.Center)
      }
    }
  }
}

private fun Color.luminanceIsLight(): Boolean =
  (0.299f * red + 0.587f * green + 0.114f * blue) > 0.65f

/**
 * The switch.
 *
 * Probably the single most recognisable control in either platform's kit, and the fastest tell that
 * an app is wearing someone else's clothes. The details that matter, in order:
 *
 *  * **Proportions.** 51 x 31 with a 27 thumb. A Material switch is wider relative to its height and
 *    has a much smaller thumb, and no amount of colour makes it read as the other thing.
 *  * **The track fills, it does not recolour.** Off is a neutral fill, on is the accent, and the
 *    crossfade runs slightly behind the thumb so the movement leads the colour.
 *  * **The thumb is a lens, not a disc.** The track records itself into its own layer and the thumb
 *    refracts *it*: the accent bends around the thumb's edge and the boundary between the filled and
 *    the unfilled part of the track visibly distorts as it passes underneath. This is the one place
 *    in the interface small enough for chromatic dispersion to be worth its seven samples per pixel.
 *  * **The thumb stretches.** Held down it widens by 4 dp, and while it is travelling it stretches
 *    along its direction of travel and thins across it, by how fast it is going. That single detail
 *    is most of why the real one feels like a physical object rather than a state being drawn twice.
 *
 * Nothing about the public signature changed, so no call site anywhere had to.
 */
@Composable
fun FluidSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val scheme = MaterialTheme.colorScheme
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = LocalFluidHaptics.current

  // The track publishes its own finished picture. The thumb is the only thing that reads it, and it
  // is drawn outside the recorded node, so there is no way for the recording to contain the lens.
  val trackGlass = rememberGlassBackdrop(blurRadius = 0.dp)

  val trackOff = scheme.onSurface.copy(alpha = FluidSwitchOffTrackAlpha)
  val track by animateColorAsState(
    targetValue = if (checked) scheme.primary else trackOff,
    animationSpec = FluidMotion.color(200),
    label = "switch track",
  )
  val trackBorder by animateColorAsState(
    targetValue = if (checked) {
      Color.Transparent
    } else {
      scheme.onSurface.copy(alpha = FluidSwitchOffBorderAlpha)
    },
    animationSpec = FluidMotion.color(200),
    label = "switch track border",
  )
  val progress = remember { Animatable(if (checked) 1f else 0f) }
  LaunchedEffect(checked, reducedMotion) {
    val target = if (checked) 1f else 0f
    if (reducedMotion) progress.snapTo(target) else progress.animateTo(target, FluidMotion.snappy())
  }
  val thumbWidth by animateDpAsState(
    targetValue = if (pressed && enabled) ThumbSize + 4.dp else ThumbSize,
    animationSpec = FluidMotion.dp(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy),
    label = "switch thumb width",
  )
  val press by animateFloatAsState(
    targetValue = if (pressed && enabled) 1f else 0f,
    animationSpec = FluidMotion.snappy(),
    label = "switch thumb press",
  )

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .graphicsLayer {
        if (reducedMotion) return@graphicsLayer
        // The whole control swells while it is held and settles back when it is let go — the same
        // thing the navigation pill does, at the scale of a switch. Held glass that does not move at
        // all is the tell that it is a picture of glass.
        val swell = 1f + SwitchPressSwellDp.dp.toPx() / size.width
        val amount = 1f + (swell - 1f) * press
        scaleX = amount
        scaleY = amount
      }
      .size(TrackWidth, TrackHeight)
      .then(
        if (onCheckedChange != null) {
          Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = { value ->
              haptics.play(if (value) FluidHapticEvent.ToggleOn else FluidHapticEvent.ToggleOff)
              onCheckedChange(value)
            },
          )
        } else {
          Modifier
        },
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(trackGlass)
        .background(track, FluidCapsuleShape)
        .border(1.dp, trackBorder, FluidCapsuleShape),
    )

    val travel = TrackWidth - ThumbInset * 2 - thumbWidth
    Box(
      modifier = Modifier
        .offset(x = ThumbInset)
        .size(width = thumbWidth, height = ThumbSize)
        // The shadow is the material's own, not a `Modifier.shadow`. `Modifier.shadow` clips its
        // content to the shape by default, at the thumb's *untransformed* position — so the pane,
        // once translated along the track, was cut down to the sliver where the two overlapped and
        // the thumb came out as a leaf floating in the middle of the switch. Everything optical has
        // to live inside the renderer, which knows about the travel.
        .glassSurface(
          state = trackGlass,
          tint = FluidSwitchThumbTint,
          shape = FluidCapsuleShape,
          role = GlassRole.Interactive,
          // No frosting at all. The track is a flat fill and there is nothing in it to hide; what
          // identifies the material here is the bend at the thumb's edge, so the blur comes off and
          // the lens comes all the way up.
          optics = remember {
            GlassDefaults.optics(GlassRole.Interactive).copy(
              blurScale = 0f,
              // Held to a small fraction of a 27 dp thumb. The stock Interactive numbers displace
              // 16 dp, and inside a 27 dp capsule that drags the green of the track most of the way
              // across the thumb's own silhouette.
              refractionHeight = 8.dp,
              refractionAmount = 5.dp,
              // A dome needs somewhere to be a dome. On something this small it only rounds the
              // whole thumb into a bead and takes its edges with it.
              depthEffect = false,
              // The thumb is the one element in the app that genuinely sits above its surface, so
              // it is one of the few places a shadow is spent — but tight, at the scale of a switch
              // rather than of a floating button.
              shadowRadius = 5.dp,
              shadowAlpha = 0.45f,
              innerShadowRadius = 3.dp,
              innerShadowAlpha = 0.14f,
            )
          },
          pressed = { press },
          layerBlock = {
            // The position lives in the layer rather than in the layout so that moving the thumb
            // invalidates a draw and nothing else — and so that the renderer, which inverts this
            // block when it samples, keeps handing the thumb the image the track actually has
            // behind it at every point of the travel.
            translationX = travel.toPx() * progress.value
            if (!reducedMotion) {
              val speed = (progress.velocity * 0.09f).coerceIn(-0.22f, 0.22f)
              scaleX = 1f / (1f - speed) * (1f + 0.04f * press)
              scaleY = (1f - speed * 0.35f) * (1f + 0.04f * press)
            }
          },
        ),
    )
  }
}

/**
 * The thumb's colour.
 *
 * Still white, because a switch thumb is white on both platforms and the material is not an excuse
 * to change what a control *is*. But at 62% rather than solid: the remaining 38% is the track seen
 * through it, which is the entire point of making the thumb a lens. On a device without
 * `RenderEffect` the fallback is opaque white, which is exactly the thumb this control had before.
 */
private val FluidSwitchThumbTint = GlassTint(
  overlay = Color.White.copy(alpha = 0.78f),
  fallback = Color.White,
  hairline = Color.Transparent,
)

/**
 * 56 x 31 with a 27 thumb.
 *
 * Wider than UIKit's 51, and the extra five dp are all travel. At 51 the thumb moves twenty dp and
 * the two states differ by less than the thumb's own width, which is legible at a glance and not at
 * a *glance across a settings page*: a column of switches all read as one texture. Twenty-five dp of
 * travel is where on and off stop having to be compared to each other to be told apart.
 */
private val TrackWidth = 56.dp
private val TrackHeight = 31.dp
private val ThumbSize = 27.dp
private val ThumbInset = 2.dp

/**
 * One number, on a track, with a lens for a handle.
 *
 * The same three ideas as [FluidSwitch], which is the control it is a sibling of:
 *
 *  * **The track fills, it does not recolour.** What is behind the handle is the accent, what is
 *    ahead of it is a neutral, and the boundary between them is a real edge in the picture — which
 *    matters here more than on a switch, because it is the edge the handle bends as it passes.
 *  * **The handle is a lens, not a disc.** The track records itself into its own layer and the
 *    handle refracts *it*, so the accent visibly distorts underneath the handle and the boundary
 *    pulls as it crosses. Made a flat circle instead, this is a Material slider wearing a different
 *    colour.
 *  * **The handle stretches.** Dragged fast it thins along its travel, the way the switch's thumb
 *    does. It is most of why the control feels like an object being pushed rather than a number
 *    being redrawn.
 *
 * The whole width is live, not only the handle: a tap anywhere on the track takes the value there.
 * A slider you have to hit the handle of is one a thumb misses, and on a settings page it is the
 * difference between adjusting something and hunting for it.
 *
 * Ticks come from [FluidHapticEvent.Tick] at every twentieth of the range and
 * [FluidHapticEvent.Threshold] at each end, which is where the engine's own floor of forty
 * milliseconds between continuous ticks is doing the work: on a fast drag there are far more
 * crossings than a motor can answer, and asking for all of them produces a buzz rather than a count.
 */
@Composable
fun FluidSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val haptics = LocalFluidHaptics.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val scope = rememberCoroutineScope()

  // The track publishes its own finished picture, and the handle is the only thing that reads it.
  // Drawn outside the recorded node, so the recording can never contain the lens.
  val trackGlass = rememberGlassBackdrop(blurRadius = 0.dp)

  val fraction = fluidSliderFraction(value, valueRange)

  // Read *now*, not captured then: the drag survives recompositions and its lambdas outlive
  // whatever they closed over. The twin note is in FluidFoldingTabBar, and it is the same bug.
  val currentOnValueChange by rememberUpdatedState(onValueChange)
  val currentFinished by rememberUpdatedState(onValueChangeFinished)
  val currentRange by rememberUpdatedState(valueRange)

  var dragging by remember { mutableStateOf(false) }
  val press by animateFloatAsState(
    targetValue = if (dragging && enabled) 1f else 0f,
    animationSpec = FluidMotion.snappy(),
    label = "slider press",
  )

  // Where the handle is, as its own animated value, so the stretch has a velocity to read. While a
  // finger is on it the position *is* the finger, so it snaps; a value arriving from elsewhere — a
  // preference restored, a reset — travels.
  val position = remember { Animatable(fraction) }
  LaunchedEffect(fraction, dragging, reducedMotion) {
    if (dragging || reducedMotion) position.snapTo(fraction) else position.animateTo(fraction, FluidMotion.snappy())
  }

  var lastTick by remember { mutableIntStateOf((fraction * SliderTickSteps).roundToInt()) }

  BoxWithConstraints(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(SliderTrackHeight)
      .semantics { this.role = Role.Switch },
    contentAlignment = Alignment.CenterStart,
  ) {
    val widthPx = constraints.maxWidth.toFloat()
    val thumbPx = with(density) { SliderThumbSize.toPx() }
    val insetPx = with(density) { SliderThumbInset.toPx() }
    val travelPx = (widthPx - thumbPx - insetPx * 2f).coerceAtLeast(1f)

    fun report(next: Float) {
      val clamped = next.fastCoerceIn(0f, 1f)
      val step = (clamped * SliderTickSteps).roundToInt()
      if (step != lastTick) {
        lastTick = step
        haptics.play(
          if (clamped <= 0f || clamped >= 1f) FluidHapticEvent.Threshold else FluidHapticEvent.Tick,
        )
      }
      currentOnValueChange(fluidSliderValue(clamped, currentRange))
    }

    fun fractionAt(x: Float): Float = fluidSliderFractionAt(x, thumbPx, insetPx, travelPx)

    Box(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(enabled, travelPx, thumbPx, insetPx) {
          if (!enabled) return@pointerInput
          detectTapGestures { offset ->
            scope.launch { position.snapTo(fractionAt(offset.x)) }
            report(fractionAt(offset.x))
            currentFinished?.invoke()
          }
        }
        .draggable(
          enabled = enabled,
          orientation = Orientation.Horizontal,
          state = rememberDraggableState { delta ->
            report((position.value + delta / travelPx).fastCoerceIn(0f, 1f))
          },
          onDragStarted = { dragging = true },
          onDragStopped = {
            dragging = false
            currentFinished?.invoke()
          },
        ),
    ) {
      // 1. The track, recorded. Two flat fills meeting at an edge, and that edge is what the lens
      // is here to bend.
      Box(
        modifier = Modifier
          .fillMaxSize()
          .clip(FluidCapsuleShape)
          .glassBackdropSource(trackGlass)
          .background(scheme.onSurface.copy(alpha = FluidSliderTrackAlpha)),
      ) {
        Box(
          modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .graphicsLayer {
              // Scaled rather than measured: the fill moves on every frame of a drag, and a width
              // in the layout would re-measure the whole control for each one.
              transformOrigin = TransformOrigin(0f, 0.5f)
              scaleX = (insetPx + thumbPx / 2f + position.value * travelPx) / size.width.coerceAtLeast(1f)
            }
            .background(scheme.primary),
        )
      }

      // 2. The handle.
      Box(
        modifier = Modifier
          .align(Alignment.CenterStart)
          .offset(x = SliderThumbInset)
          .size(SliderThumbSize)
          .glassSurface(
            state = trackGlass,
            tint = FluidSliderThumbTint,
            shape = FluidCapsuleShape,
            role = GlassRole.Interactive,
            // The switch's numbers, and for the switch's reasons: no frosting, because the track is
            // a flat fill with nothing in it to hide, and a bend held to a small fraction of the
            // handle so the accent does not get dragged across its whole silhouette.
            optics = remember {
              GlassDefaults.optics(GlassRole.Interactive).copy(
                blurScale = 0f,
                refractionHeight = 8.dp,
                refractionAmount = 5.dp,
                depthEffect = false,
                shadowRadius = 5.dp,
                shadowAlpha = 0.45f,
                innerShadowRadius = 3.dp,
                innerShadowAlpha = 0.14f,
              )
            },
            pressed = { press },
            layerBlock = {
              // In the layer, not the layout: moving the handle invalidates a draw and nothing
              // else, and the renderer inverts this block when it samples, so at every point of the
              // travel the handle is handed the image the track actually has behind it.
              translationX = travelPx * position.value
              if (!reducedMotion) {
                val speed = (position.velocity * 0.09f).coerceIn(-0.22f, 0.22f)
                scaleX = 1f / (1f - speed) * (1f + 0.06f * press)
                scaleY = (1f - speed * 0.35f) * (1f + 0.06f * press)
              }
            },
          ),
      )
    }
  }
}

// ---------------------------------------------------------------------------------------------
// The arithmetic of the slider
// ---------------------------------------------------------------------------------------------
//
// Out here for the reason the folding bar's is: it runs on every frame of a drag, and it is the
// part that goes wrong quietly. A range that is empty, or given backwards, is a caller mistake and
// not a reason to divide by zero and send the handle to an end of the track.

/** Where in its range a value sits, as 0..1. */
internal fun fluidSliderFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
  val span = range.endInclusive - range.start
  if (span <= 0f) return 0f
  return ((value - range.start) / span).fastCoerceIn(0f, 1f)
}

/** The value a fraction of the track stands for. The inverse of [fluidSliderFraction]. */
internal fun fluidSliderValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
  val span = range.endInclusive - range.start
  if (span <= 0f) return range.start
  return range.start + fraction.fastCoerceIn(0f, 1f) * span
}

/**
 * What a touch at [x] is asking for.
 *
 * The handle's *centre* goes under the finger, not its left edge: pressing the track puts the thing
 * you are holding where you pressed, and half a handle of offset is visible at this size.
 */
internal fun fluidSliderFractionAt(x: Float, thumbPx: Float, insetPx: Float, travelPx: Float): Float {
  if (travelPx <= 0f) return 0f
  return ((x - insetPx - thumbPx / 2f) / travelPx).fastCoerceIn(0f, 1f)
}

/**
 * The handle's colour, and it is the switch's thumb.
 *
 * Deliberately the same value rather than a second constant that happens to match: they are the same
 * object at two sizes, and the day one of them is retuned the other has to move with it.
 */
private val FluidSliderThumbTint = FluidSwitchThumbTint

/** How much of the track is left unfilled to read as a track at all. */
private const val FluidSliderTrackAlpha = 0.16f

/** How many crossings the range is divided into for the tick. See [FluidSlider]. */
private const val SliderTickSteps = 20

/**
 * 30 x 26, which is the switch one notch up.
 *
 * A track this tall is not a Material slider's hairline, and that is the point: the handle is a lens
 * and a lens needs something to refract. On a hairline there is nothing behind the handle but the
 * page, and the whole control collapses into a circle with a line through it.
 */
private val SliderTrackHeight = 30.dp
private val SliderThumbSize = 26.dp
private val SliderThumbInset = 2.dp

/** How much the whole switch grows under a finger, in dp added to its width. */
private const val SwitchPressSwellDp = 5f

/** Keeps the neutral fill quiet; the outline carries the off-state boundary contrast. */
internal const val FluidSwitchOffTrackAlpha = 0.12f

/** Produces a >= 3:1 off-state outline against every app surface in light and dark themes. */
internal const val FluidSwitchOffBorderAlpha = 0.55f

/**
 * A segmented control.
 *
 * Three surfaces stacked, and the arrangement is the whole trick — it is exactly the one
 * [FluidTabBar] uses, stood down to 32 dp:
 *
 *  1. **The bar**, with the labels printed on it in the resting colour, recorded into its own layer.
 *  2. **A second copy of the labels, invisible.** Drawn at zero alpha, tinted with the accent, and
 *     recorded. Nobody ever sees it directly.
 *  3. **The pill**, a lens that refracts the bar *and* that invisible accent copy. Wherever it sits,
 *     the label underneath appears through it — magnified, and in the accent colour.
 *
 * So the selected segment is not painted a different colour: it is *seen through glass*. That is the
 * difference between a control that looks like one physical object with a moving part and a row of
 * buttons that take turns lighting up, and it is worth the two extra recordings a 32 dp control has
 * to pay for it.
 *
 * The pill can also be dragged between segments; letting go settles it on whichever it is nearest.
 */
@Composable
fun <T> FluidSegmentedControl(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  /**
   * What a segment shows, when a word is not it.
   *
   * [label] stays required even here, and is not optional politeness: with a glyph in the segment it
   * is the only thing a screen reader has to go on, so it becomes the description instead of the
   * text. Note that this is composed **twice** — once for the bar and once for the invisible accent
   * copy the lens reads — so it has to be a picture of a segment and not a place to start work.
   */
  content: (@Composable (option: T, selected: Boolean) -> Unit)? = null,
  label: (T) -> String,
) {
  if (options.isEmpty()) return
  val scheme = MaterialTheme.colorScheme
  val accent = scheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

  val barGlass = rememberGlassBackdrop(blurRadius = 0.dp)
  val labelsGlass = rememberGlassBackdrop(blurRadius = 0.dp)
  val pillBackdrop = rememberCombinedGlassBackdrop(barGlass, labelsGlass)

  val shape = ContinuousCornerShape(9.dp)
  val pillShape = ContinuousCornerShape(7.dp)

  BoxWithConstraints(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(SegmentHeight),
  ) {
    val inset = SegmentPillInset
    val segmentWidth = with(density) {
      (constraints.maxWidth.toFloat() - inset.toPx() * 2f) / options.size
    }
    var currentIndex by remember { mutableStateOf(selectedIndex) }
    // Letti adesso, non catturati allora: l'animazione sopravvive alle ricomposizioni e le sue
    // lambda vivono piu' a lungo di qualunque valore abbiano chiuso dentro. Vedi la nota gemella
    // in FluidFoldingTabBar.
    val currentOptions by rememberUpdatedState(options)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentEnabled by rememberUpdatedState(enabled)

    val pill = remember(scope, options.size, segmentWidth) {
      GlassDragAnimation(
        animationScope = scope,
        initialValue = selectedIndex.toFloat(),
        valueRange = 0f..(options.size - 1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        pressedScale = if (reducedMotion) 1f else 1.06f,
        onDragStopped = {
          val target = targetValue.roundToInt().coerceIn(0, currentOptions.size - 1)
          animateToValue(target.toFloat())
          if (target != currentIndex) {
            currentIndex = target
            currentOptions.getOrNull(target)?.let { currentOnSelect(it) }
          }
        },
        onDrag = { _, dragAmount ->
          if (segmentWidth <= 0f || !currentEnabled) return@GlassDragAnimation
          updateValue(
            (targetValue + dragAmount.x / segmentWidth)
              .coerceIn(0f, (currentOptions.size - 1).toFloat()),
          )
        },
      )
    }

    // Selection also arrives from outside — a restored state, a filter reset. The pill follows it
    // without reporting anything back: whoever changed it already knows.
    LaunchedEffect(selectedIndex, pill) {
      if (currentIndex != selectedIndex) {
        currentIndex = selectedIndex
        if (reducedMotion) pill.snapToValue(selectedIndex.toFloat())
        else pill.animateToValue(selectedIndex.toFloat())
      }
    }

    val segments: @Composable RowScope.() -> Unit = {
      options.forEachIndexed { index, option ->
        val isSelected = index == currentIndex
        Box(
          modifier = Modifier
            .weight(1f)
            .height(SegmentHeight)
            .semantics {
              this.role = Role.Tab
              this.selected = isSelected
              if (content != null) this.contentDescription = label(option)
            }
            .fluidPressable(
              onClick = {
                if (isSelected) return@fluidPressable
                currentIndex = index
                if (reducedMotion) pill.snapToValue(index.toFloat())
                else pill.animateToValue(index.toFloat())
                onSelect(option)
              },
              enabled = enabled,
              pressedScale = 0.96f,
            ),
          contentAlignment = Alignment.Center,
        ) {
          if (content != null) {
            content(option, isSelected)
          } else {
            Text(
              text = label(option),
              style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
              ),
              // Flat, in both copies. The accent copy is tinted wholesale by a colour filter, and a
              // per-segment colour animation underneath it would only fight the filter.
              color = if (isSelected) scheme.onSurface else scheme.onSurfaceVariant,
              maxLines = 1,
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(horizontal = 6.dp),
            )
          }
        }
      }
    }

    // 1. The bar.
    Row(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(barGlass)
        .background(scheme.onSurface.copy(alpha = 0.07f), shape)
        .padding(horizontal = inset)
        .selectableGroup(),
      verticalAlignment = Alignment.CenterVertically,
      content = segments,
    )

    // 2. The invisible accent copy the pill reads.
    Row(
      modifier = Modifier
        .clearAndSetSemantics {}
        .alpha(0f)
        .glassBackdropSource(labelsGlass)
        .fillMaxSize()
        .padding(horizontal = inset)
        .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
      verticalAlignment = Alignment.CenterVertically,
      content = segments,
    )

    // 3. The lens.
    Box(
      modifier = Modifier
        .padding(inset)
        // The slide lives *here*, ahead of the gesture modifier, exactly as in FluidTabBar — not in
        // the glass surface's layerBlock below. A layer applies only to what follows it in the
        // chain, and the gesture surface has to follow: with the translation inside the glass
        // layer, the pill was *seen* on the selected segment while its touch target stayed parked
        // over the first one. Tapping the first segment then fed the pill's drag detector instead
        // of the segment's click — a zero-length drag that reselects — so once the pill left the
        // first slot, that segment went dead and the pill itself could no longer be grabbed.
        .graphicsLayer { translationX = pill.value * segmentWidth }
        .then(pill.modifier)
        .glassSurface(
          state = pillBackdrop,
          tint = FluidSegmentPillTint(scheme.background.luminanceIsLight()),
          shape = pillShape,
          role = GlassRole.Interactive,
          // Never any frosting: the lens is standing on a label eight pixels tall, and any blur at
          // all makes the selected segment the one word in the control you cannot read.
          optics = remember {
            GlassDefaults.optics(GlassRole.Interactive).copy(
              blurScale = 0f,
              refractionHeight = 8.dp,
              refractionAmount = 8.dp,
            )
          },
          // At rest the lens draws the bar back exactly as it is, so the label underneath shows
          // through crisp and in the accent colour. The glass thickens only under a finger.
          opticalDepth = { pill.pressProgress },
          pressed = { pill.pressProgress },
          layerBlock = {
            if (!reducedMotion) {
              scaleX = pill.scaleX
              scaleY = pill.scaleY
              val velocity = pill.velocity / 10f
              scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
              scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
            }
          },
        )
        .fillMaxHeight()
        .width(with(density) { segmentWidth.toDp() }),
    )
  }
}

/**
 * The pill's own colour.
 *
 * Barely there, because it is not what carries the selection — the label underneath is already
 * showing through in the accent, and a strong fill here would only cover it up. It exists to give
 * the selected segment an edge to sit inside. On a device without `RenderEffect` there is no lens to
 * see through, so the fallback goes back to being the solid pill this control always had.
 */
private fun FluidSegmentPillTint(lightBackground: Boolean): GlassTint = if (lightBackground) {
  GlassTint(
    overlay = Color.White.copy(alpha = 0.42f),
    fallback = Color.White,
    hairline = Color.Transparent,
  )
} else {
  GlassTint(
    overlay = Color.White.copy(alpha = 0.14f),
    fallback = Color(0xFF3A3A3C),
    hairline = Color.Transparent,
  )
}

private val SegmentHeight = 32.dp
private val SegmentPillInset = 2.dp

/**
 * A filter pill.
 *
 * Used where a segmented control cannot be — when the options do not fit on one row, or when more
 * than one can be on at a time.
 */
@Composable
fun FluidChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leading: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val container by animateColorAsState(
    targetValue = if (selected) scheme.primary else scheme.onSurface.copy(alpha = 0.06f),
    animationSpec = FluidMotion.color(200),
    label = "chip container",
  )
  val content by animateColorAsState(
    targetValue = if (selected) scheme.onPrimary else scheme.onSurface,
    animationSpec = FluidMotion.color(200),
    label = "chip content",
  )

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.4f)
      .defaultMinSize(minHeight = 48.dp)
      .clip(FluidCapsuleShape)
      .background(container)
      .semantics { this.selected = selected }
      .fluidPressable(onClick = onClick, enabled = enabled, role = Role.Button)
      .padding(horizontal = 14.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides content) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (leading != null) {
          Box(modifier = Modifier.size(15.dp), contentAlignment = Alignment.Center) { leading() }
        }
        ProvideTextStyle(
          MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        ) {
          Text(text = label, maxLines = 1)
        }
      }
    }
  }
}

/**
 * A stepper-free numeric or textual value shown at the trailing edge of a settings row.
 *
 * iOS sets these in the secondary colour, never the accent: the value is information, and the row
 * itself is the thing you tap.
 */
@Composable
fun FluidRowValue(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    modifier = modifier,
    style = MaterialTheme.typography.bodyLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    maxLines = 1,
  )
}

/** A hairline. 0.5dp is a real width on every screen the app runs on, and it is what iOS draws. */
@Composable
fun FluidHairline(
  modifier: Modifier = Modifier,
  startInset: Dp = 0.dp,
  color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = startInset)
      .height(0.5.dp)
      .background(color),
  )
}

/** A circular dot, used to preview an accent colour in the picker. */
@Composable
fun FluidColorDot(
  color: Color,
  selected: Boolean,
  onClick: () -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  size: Dp = 30.dp,
) {
  val scheme = MaterialTheme.colorScheme
  val touchTargetSize = maxOf(48.dp, size + 10.dp)
  val ringAlpha by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = FluidMotion.color(180),
    label = "dot ring",
  )
  Box(
    modifier = modifier
      .size(touchTargetSize)
      .semantics {
        contentDescription = label
        this.selected = selected
      }
      .fluidPressable(onClick = onClick, role = Role.RadioButton)
      .drawBehind {
        if (ringAlpha > 0.001f) {
          drawCircle(
            color = scheme.onSurface.copy(alpha = FluidSelectedRingAlpha * ringAlpha),
            radius = size.toPx() / 2f + 4.dp.toPx(),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
          )
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier = Modifier
        .size(size)
        .clip(CircleShape)
        .background(color),
    )
  }
}

/** Final-state contrast is at least 3:1 on the light and dark app container surfaces. */
internal const val FluidSelectedRingAlpha = 0.55f

/** Width helper so a row of controls can reserve the switch's footprint without instantiating one. */
val FluidSwitchWidth: Dp = TrackWidth
