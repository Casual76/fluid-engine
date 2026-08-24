package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A semantic destination in a long, sectioned [androidx.compose.foundation.lazy.LazyColumn]. */
@Immutable
data class FluidSectionAnchor(
  val key: String,
  val label: String,
  val itemIndex: Int,
)

/** Whether a section selection should settle spatially or follow a finger without queueing motion. */
enum class FluidSectionSelectionMotion {
  Animated,
  Immediate,
}

/**
 * A compact, draggable section index for semantic jumps such as weekdays or months.
 *
 * The visible marks stay tiny, while the whole 48 dp trailing strip is the hit target. It exposes a
 * range to accessibility services instead of making several overlapping four-dp dots focusable.
 * Callers own scrolling so this primitive remains usable with lists whose static leading items vary.
 */
@Composable
fun FluidSectionIndex(
  sections: List<FluidSectionAnchor>,
  activeSectionKey: String?,
  onSelectSection: (FluidSectionAnchor, FluidSectionSelectionMotion) -> Unit,
  modifier: Modifier = Modifier,
  visible: Boolean = true,
  maxVisibleAnchors: Int = FluidSectionIndexDefaults.MaxVisibleAnchors,
  /**
   * Supply this only when the index is drawn from [FluidScreen.overlay]. A rail inside the recorded
   * list body would sample itself; the explicit parameter makes that invalid placement visible.
   */
  backdrop: GlassBackdropState? = null,
) {
  val sampledSections = remember(sections, maxVisibleAnchors, activeSectionKey) {
    sampleFluidSectionAnchors(sections, maxVisibleAnchors, preferredKey = activeSectionKey)
  }
  if (sampledSections.size < 2) return

  val selectedIndex = activeFluidSectionIndex(sampledSections, activeSectionKey)
    .coerceIn(0, sampledSections.lastIndex)
  val activeSection = sampledSections[selectedIndex]
  val currentOnSelect by rememberUpdatedState(onSelectSection)
  val haptics = LocalHapticFeedback.current
  val touchSlop = LocalViewConfiguration.current.touchSlop
  val markColor = MaterialTheme.colorScheme.primary
  val floatingTint = GlassDefaults.floatingTint()
  var interacting by remember { mutableStateOf(false) }

  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter = fadeIn(FluidMotion.fadeIn(160)) + scaleIn(
      initialScale = 0.92f,
      animationSpec = FluidMotion.snappy(),
    ),
    exit = fadeOut(FluidMotion.fadeOut(120)) + scaleOut(
      targetScale = 0.92f,
      animationSpec = FluidMotion.snappy(),
    ),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.End,
    ) {
      AnimatedVisibility(
        visible = interacting,
        enter = fadeIn(FluidMotion.fadeIn(110)) + scaleIn(
          initialScale = 0.94f,
          animationSpec = FluidMotion.snappy(),
        ),
        exit = fadeOut(FluidMotion.fadeOut(100)) + scaleOut(
          targetScale = 0.96f,
          animationSpec = FluidMotion.snappy(),
        ),
      ) {
        Text(
          text = activeSection.label,
          modifier = Modifier
            .padding(end = 4.dp)
            .then(
              if (backdrop != null) {
                Modifier.glassSurface(
                  state = backdrop,
                  tint = floatingTint,
                  shape = FluidGlassCapsuleShape,
                  role = GlassRole.Interactive,
                )
              } else {
                Modifier
                  .clip(FluidGlassCapsuleShape)
                  .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f))
              },
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
        )
      }

      Box(
        modifier = Modifier
          .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.End))
          .padding(end = FluidSectionIndexDefaults.EdgePadding)
          .width(FluidSectionIndexDefaults.TouchWidth)
          .height(sectionIndexHeight(sampledSections.size))
          .then(
            if (backdrop != null) {
              Modifier.glassSurface(
                state = backdrop,
                tint = floatingTint,
                shape = FluidGlassCapsuleShape,
                role = GlassRole.Interactive,
              )
            } else {
              Modifier
            },
          )
          .sectionIndexSemantics(
            sections = sampledSections,
            selectedIndex = selectedIndex,
            onSelect = { index ->
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Animated)
            },
          )
          .pointerInput(sampledSections, touchSlop) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              var lastPosition = down.position
              var dragged = false
              var lastDragIndex: Int? = null
              interacting = true

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val position = change.position
                if (!dragged && distance(down.position, position) >= touchSlop) {
                  dragged = true
                }
                if (dragged) {
                  val index = fluidSectionIndexForPosition(position.y, size.height.toFloat(), sampledSections.size)
                  if (index != lastDragIndex) {
                    lastDragIndex = index
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Immediate)
                  }
                  change.consume()
                }
                lastPosition = position
                if (change.changedToUpIgnoreConsumed() || !change.pressed) break
              }

              if (!dragged) {
                val index = fluidSectionIndexForPosition(lastPosition.y, size.height.toFloat(), sampledSections.size)
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Animated)
              }
              interacting = false
            }
          },
        contentAlignment = Alignment.CenterEnd,
      ) {
        Column(
          horizontalAlignment = Alignment.End,
          verticalArrangement = Arrangement.spacedBy(FluidSectionIndexDefaults.MarkSpacing),
        ) {
          sampledSections.forEachIndexed { index, _ ->
            val isActive = index == selectedIndex
            val width by animateDpAsState(
              targetValue = if (isActive) FluidSectionIndexDefaults.ActiveMarkWidth else FluidSectionIndexDefaults.MarkSize,
              animationSpec = FluidMotion.dp(
                dampingRatio = FluidMotion.DampingChrome,
                stiffness = FluidMotion.ResponseSnappy,
              ),
              label = "section index width",
            )
            val alpha by animateFloatAsState(
              targetValue = when {
                isActive -> 1f
                interacting -> 0.48f
                else -> 0.28f
              },
              animationSpec = FluidMotion.color(150),
              label = "section index alpha",
            )
            Box(
              modifier = Modifier
                .size(width = width, height = FluidSectionIndexDefaults.MarkSize)
                .drawBehind {
                  val radius = size.height / 2f
                  drawRoundRect(
                    color = markColor.copy(alpha = alpha),
                    cornerRadius = CornerRadius(radius, radius),
                  )
                },
            )
          }
        }
      }
    }
  }
}

object FluidSectionIndexDefaults {
  const val MaxVisibleAnchors: Int = 8
  val TouchWidth = 48.dp
  val MarkSize = 4.dp
  val ActiveMarkWidth = 16.dp
  val MarkSpacing = 10.dp
  val EdgePadding = 4.dp
}

internal fun sampleFluidSectionAnchors(
  sections: List<FluidSectionAnchor>,
  maxVisibleAnchors: Int,
  preferredKey: String? = null,
): List<FluidSectionAnchor> {
  if (sections.isEmpty() || maxVisibleAnchors <= 0) return emptyList()
  if (sections.size <= maxVisibleAnchors) return sections
  if (maxVisibleAnchors == 1) return listOf(sections.first())

  val last = sections.lastIndex.toFloat()
  val sampledIndices = (0 until maxVisibleAnchors)
    .map { sampleIndex -> ((sampleIndex * last) / (maxVisibleAnchors - 1)).roundToInt() }
    .distinct()
    .toMutableList()
  val preferredIndex = sections.indexOfFirst { it.key == preferredKey }
  if (
    preferredIndex > 0 &&
    preferredIndex < sections.lastIndex &&
    preferredIndex !in sampledIndices &&
    sampledIndices.size > 2
  ) {
    val replacementPosition = (1 until sampledIndices.lastIndex)
      .minByOrNull { position -> kotlin.math.abs(sampledIndices[position] - preferredIndex) }
    if (replacementPosition != null) sampledIndices[replacementPosition] = preferredIndex
  }
  return sampledIndices.distinct().sorted().map(sections::get)
}

internal fun activeFluidSectionIndex(
  sections: List<FluidSectionAnchor>,
  activeSectionKey: String?,
): Int {
  if (sections.isEmpty()) return -1
  val exact = sections.indexOfFirst { it.key == activeSectionKey }
  return if (exact >= 0) exact else 0
}

fun activeFluidSectionForItemIndex(
  sections: List<FluidSectionAnchor>,
  firstVisibleItemIndex: Int,
): FluidSectionAnchor? {
  var active: FluidSectionAnchor? = null
  for (section in sections) {
    if (section.itemIndex > firstVisibleItemIndex) continue
    if (active == null || section.itemIndex > active.itemIndex) active = section
  }
  return active
}

internal fun fluidSectionIndexForPosition(
  positionY: Float,
  height: Float,
  sectionCount: Int,
): Int {
  if (sectionCount <= 1 || height <= 0f) return 0
  val fraction = (positionY / height).coerceIn(0f, 1f)
  return (fraction * (sectionCount - 1)).roundToInt().coerceIn(0, sectionCount - 1)
}

private fun sectionIndexHeight(sectionCount: Int) =
  FluidSectionIndexDefaults.MarkSize * sectionCount +
    FluidSectionIndexDefaults.MarkSpacing * (sectionCount - 1).coerceAtLeast(0)

private fun distance(first: Offset, second: Offset): Float {
  val dx = second.x - first.x
  val dy = second.y - first.y
  return kotlin.math.sqrt(dx * dx + dy * dy)
}

private fun Modifier.sectionIndexSemantics(
  sections: List<FluidSectionAnchor>,
  selectedIndex: Int,
  onSelect: (Int) -> Unit,
): Modifier = semantics(mergeDescendants = true) {
  val lastIndex = sections.lastIndex
  contentDescription = "Indice sezioni"
  stateDescription = "${sections[selectedIndex].label}, ${selectedIndex + 1} di ${sections.size}"
  progressBarRangeInfo = ProgressBarRangeInfo(
    current = selectedIndex.toFloat(),
    range = 0f..lastIndex.toFloat(),
    steps = (sections.size - 2).coerceAtLeast(0),
  )
  setProgress { requested ->
    onSelect(requested.roundToInt().coerceIn(0, lastIndex))
    true
  }
  customActions = listOfNotNull(
    if (selectedIndex > 0) {
      CustomAccessibilityAction("Sezione precedente") {
        onSelect(selectedIndex - 1)
        true
      }
    } else {
      null
    },
    if (selectedIndex < lastIndex) {
      CustomAccessibilityAction("Sezione successiva") {
        onSelect(selectedIndex + 1)
        true
      }
    } else {
      null
    },
  )
}
