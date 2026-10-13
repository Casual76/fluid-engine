package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.semantics.disabled

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
 * without being able to name them: it answers the finger with its whole body instead of rippling,
 * its corners are continuous, and a disabled button fades rather than turning grey — the greyed-out
 * disabled state is a Material signature, and it also destroys the colour relationship the rest of
 * the screen is built on.
 *
 * Since 2.11.0 the answer is [FluidPressFeedback.Expand]: the button grows under the finger, leans
 * toward it, stretches along a drag and ticks under a finger sliding across it, and a tap however
 * quick plays the whole press. Before, it shrank to 0.974 — and only its label did, because the
 * press sat *inside* the clip and the background, so the container never moved at all. The press
 * is now outside both, and the container is what grows.
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
      .fluidPressable(
        onClick = onClick,
        enabled = enabled && !loading,
        role = Role.Button,
        feedback = FluidPressFeedback.Expand,
      )
      .clip(shape)
      .background(container)
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
              focusShape = FluidCapsuleShape,
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
      .semantics { this.selected = selected }
      // Outside the clip and the fill, so the pill is what grows (2.11.0): inside them only the
      // label moved, and a chip pressed under a thumb showed nothing at all.
      .fluidPressable(
        onClick = onClick,
        enabled = enabled,
        role = Role.Button,
        focusShape = FluidCapsuleShape,
        feedback = FluidPressFeedback.Expand,
      )
      .clip(FluidCapsuleShape)
      .background(container)
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
      .fluidPressable(onClick = onClick, role = Role.RadioButton, feedback = FluidPressFeedback.Expand)
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

