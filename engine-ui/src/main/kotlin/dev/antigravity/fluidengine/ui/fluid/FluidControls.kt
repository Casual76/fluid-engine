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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 *  * **The thumb stretches.** Held down, it widens by 4dp and pushes toward the far end. That single
 *    detail is most of why the real one feels like a physical object.
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
  val progress by animateFloatAsState(
    targetValue = if (checked) 1f else 0f,
    animationSpec = FluidMotion.snappy(),
    label = "switch thumb",
  )
  val thumbWidth by animateDpAsState(
    targetValue = if (pressed && enabled) ThumbSize + 4.dp else ThumbSize,
    animationSpec = FluidMotion.dp(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy),
    label = "switch thumb width",
  )

  Box(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .size(TrackWidth, TrackHeight)
      .clip(FluidCapsuleShape)
      .background(track)
      .border(1.dp, trackBorder, FluidCapsuleShape)
      .then(
        if (onCheckedChange != null) {
          Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = interactionSource,
            indication = null,
            onValueChange = onCheckedChange,
          )
        } else {
          Modifier
        },
      ),
    contentAlignment = Alignment.CenterStart,
  ) {
    val travel = TrackWidth - ThumbInset * 2 - thumbWidth
    Box(
      modifier = Modifier
        .offset(x = ThumbInset + travel * progress)
        .size(width = thumbWidth, height = ThumbSize)
        // A real shadow, not an elevation overlay: the thumb is the one element in the app that
        // genuinely sits above its surface, and it is the only place a shadow is spent.
        .shadow(2.dp, FluidCapsuleShape, ambientColor = Color.Black, spotColor = Color.Black)
        .background(Color.White, FluidCapsuleShape),
    )
  }
}

private val TrackWidth = 51.dp
private val TrackHeight = 31.dp
private val ThumbSize = 27.dp
private val ThumbInset = 2.dp

/** Keeps the neutral fill quiet; the outline carries the off-state boundary contrast. */
internal const val FluidSwitchOffTrackAlpha = 0.12f

/** Produces a >= 3:1 off-state outline against every app surface in light and dark themes. */
internal const val FluidSwitchOffBorderAlpha = 0.55f

/**
 * A segmented control.
 *
 * The pill slides between segments rather than fading in under the new one — the slide is what makes
 * the control feel like one object with a moving part, instead of a row of buttons that light up.
 */
@Composable
fun <T> FluidSegmentedControl(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  label: (T) -> String,
) {
  if (options.isEmpty()) return
  val scheme = MaterialTheme.colorScheme
  val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
  val pill = remember { Animatable(0f) }
  var segmentWidth by remember { mutableStateOf(0f) }
  var placed by remember { mutableStateOf(false) }

  LaunchedEffect(selectedIndex, segmentWidth) {
    if (segmentWidth <= 0f) return@LaunchedEffect
    val target = selectedIndex * segmentWidth
    if (!placed) {
      placed = true
      pill.snapTo(target)
    } else {
      pill.animateTo(target, FluidMotion.snappy())
    }
  }

  val pillColor = if (scheme.background.luminanceIsLight()) Color.White else scheme.surfaceContainerHighest
  val pillInset = with(LocalDensity.current) { 2.dp.toPx() }
  val pillRadius = with(LocalDensity.current) { 7.dp.toPx() }

  Row(
    modifier = modifier
      .alpha(if (enabled) 1f else 0.5f)
      .fillMaxWidth()
      .height(SegmentHeight)
      .clip(ContinuousCornerShape(9.dp))
      .background(scheme.onSurface.copy(alpha = 0.07f))
      .onGloballyPositioned { segmentWidth = it.size.width.toFloat() / options.size }
      .drawBehind {
        if (segmentWidth <= 0f) return@drawBehind
        drawRoundRect(
          color = pillColor,
          topLeft = Offset(pill.value + pillInset, pillInset),
          size = Size(segmentWidth - pillInset * 2, size.height - pillInset * 2),
          cornerRadius = CornerRadius(pillRadius, pillRadius),
        )
      }
      .selectableGroup(),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    options.forEachIndexed { index, option ->
      val isSelected = index == selectedIndex
      val weight by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = FluidMotion.color(180),
        label = "segment weight",
      )
      Box(
        modifier = Modifier
          .weight(1f)
          .height(SegmentHeight)
          .clip(ContinuousCornerShape(7.dp))
          .fluidPressable(
            onClick = { if (!isSelected) onSelect(option) },
            enabled = enabled,
            pressedScale = 0.96f,
            role = Role.Tab,
          ),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = label(option),
          style = MaterialTheme.typography.bodySmall.copy(
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
          ),
          color = lerp(scheme.onSurfaceVariant, scheme.onSurface, weight),
          maxLines = 1,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(horizontal = 6.dp),
        )
      }
    }
  }
}

private val SegmentHeight = 32.dp

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
