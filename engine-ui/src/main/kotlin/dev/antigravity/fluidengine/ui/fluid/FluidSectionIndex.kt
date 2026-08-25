package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

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
 * A draggable section index for semantic jumps such as weekdays or months.
 *
 * At rest it is a sliver of marks — no pane, no pill, nothing that competes with the page. Touched,
 * it **unfolds into the scrollbar it actually is**: the marks spread over a full-height glass rail,
 * a lens rides the rail under the finger, and the label bubble travels with it. The unfolding is
 * one animated value read in layout and draw, so no frame of it recomposes anything.
 *
 * The whole 48 dp trailing strip around the resting marks is the hit target, and it exposes a range
 * to accessibility services instead of making several overlapping four-dp dots focusable. Callers
 * own scrolling so this primitive remains usable with lists whose static leading items vary.
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
  val controlTint = GlassDefaults.controlTint()
  val scheme = MaterialTheme.colorScheme
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  var interacting by remember { mutableStateOf(false) }
  // 0 = the resting sliver, 1 = the full-height rail with the lens on it.
  val expansion = remember { Animatable(0f) }
  // Where the finger is, as a fraction of the unfolded rail: one number drives the lens, the label
  // and the selection together.
  var fingerFraction by remember { mutableFloatStateOf(0f) }

  val compactTrackHeight = sectionIndexHeight(sampledSections.size)

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
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxHeight()
        .windowInsetsPadding(WindowInsets.systemGestures.only(WindowInsetsSides.End))
        .padding(end = FluidSectionIndexDefaults.EdgePadding)
        .width(FluidSectionIndexDefaults.TouchWidth),
    ) {
      val rootHeightPx = constraints.maxHeight.toFloat()
      val rootWidthPx = constraints.maxWidth.toFloat()
      val compactHPx = with(density) { compactTrackHeight.toPx() }
      val edgeMarginPx = with(density) { FluidSectionIndexDefaults.ExpandedMargin.toPx() }
      val markInsetPx = with(density) { FluidSectionIndexDefaults.ExpandedMarkInset.toPx() }
      val expandedHPx = (rootHeightPx - 2f * edgeMarginPx).coerceAtLeast(compactHPx)
      val restWPx = with(density) { FluidSectionIndexDefaults.ActiveMarkWidth.toPx() }
      val expandedWPx = with(density) { FluidSectionIndexDefaults.ExpandedTrackWidth.toPx() }
      val markPx = with(density) { FluidSectionIndexDefaults.MarkSize.toPx() }
      val activeMarkPx = with(density) { FluidSectionIndexDefaults.ActiveMarkWidth.toPx() }
      val markSpacingPx = with(density) { FluidSectionIndexDefaults.MarkSpacing.toPx() }
      val lensPx = with(density) { FluidSectionIndexDefaults.LensSize.toPx() }
      val labelHalfPx = with(density) { 16.dp.toPx() }

      fun trackHeight(t: Float): Float = lerpFloat(compactHPx, expandedHPx, t)
      fun trackTop(t: Float): Float = (rootHeightPx - trackHeight(t)) / 2f
      fun expandedFractionForRootY(rootY: Float): Float {
        val usable = (trackHeight(1f) - 2f * markInsetPx).coerceAtLeast(1f)
        return ((rootY - trackTop(1f) - markInsetPx) / usable).coerceIn(0f, 1f)
      }
      fun compactFractionForRootY(rootY: Float): Float {
        val usable = (trackHeight(0f)).coerceAtLeast(1f)
        return ((rootY - trackTop(0f)) / usable).coerceIn(0f, 1f)
      }
      fun indexForFraction(fraction: Float): Int =
        (fraction * (sampledSections.size - 1)).roundToInt().coerceIn(0, sampledSections.lastIndex)
      fun lensCenterY(): Float =
        trackTop(1f) + markInsetPx + fingerFraction * (trackHeight(1f) - 2f * markInsetPx)

      // The rail. Its size lives in a layout lambda and its marks in a draw lambda, both reading
      // [expansion] directly: the unfolding never recomposes.
      Box(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .layout { measurable, _ ->
            val t = expansion.value
            val w = lerpFloat(restWPx, expandedWPx, t).roundToInt().coerceAtLeast(1)
            val h = trackHeight(t).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
          }
          .then(
            if (backdrop != null) {
              Modifier.glassSurface(
                state = backdrop,
                tint = controlTint,
                shape = FluidCapsuleShape,
                role = GlassRole.Interactive,
                // The material itself is what unfolds: at rest there is no pane at all, only the
                // marks — the resting blob of glass was the single ugliest pixel this component
                // produced, and nothing about the resting job needs a surface.
                intensity = { expansion.value },
              )
            } else {
              Modifier.drawBehind {
                val t = expansion.value
                if (t > 0.01f) {
                  val radius = size.width / 2f
                  drawRoundRect(
                    color = scheme.surfaceContainerHigh.copy(alpha = 0.9f * t),
                    cornerRadius = CornerRadius(radius, radius),
                  )
                }
              }
            },
          )
          .drawBehind {
            val t = expansion.value
            val n = sampledSections.size
            for (index in 0 until n) {
              val isActive = index == selectedIndex
              // The cluster the marks rest in and the stations they spread to are both expressed
              // in the rail's own space, so the spread rides the rail's growth.
              val compactY = index * (markPx + markSpacingPx) + markPx / 2f
              val expandedY = markInsetPx + (size.height - 2f * markInsetPx) * (index / (n - 1f))
              val y = lerpFloat(compactY, expandedY, t)
              val w = if (isActive) lerpFloat(activeMarkPx, markPx * 2f, t) else markPx
              val alpha = if (isActive) 1f else lerpFloat(0.28f, 0.55f, t)
              val radius = markPx / 2f
              // Right-aligned in the resting cluster, centred once they are stations on the rail.
              val x = lerpFloat(size.width - w, (size.width - w) / 2f, t)
              drawRoundRect(
                color = markColor.copy(alpha = alpha),
                topLeft = Offset(x, y - markPx / 2f),
                size = Size(w, markPx),
                cornerRadius = CornerRadius(radius, radius),
              )
            }
          },
      )

      // The lens: a ball of glass that rides the rail under the finger.
      Box(
        modifier = Modifier
          .align(Alignment.TopEnd)
          .size(FluidSectionIndexDefaults.LensSize)
          .offset {
            val dx = ((lensPx - expandedWPx) / 2f).roundToInt()
            IntOffset(dx, (lensCenterY() - lensPx / 2f).roundToInt())
          }
          .graphicsLayer {
            val t = expansion.value
            alpha = t
            scaleX = 0.4f + 0.6f * t
            scaleY = 0.4f + 0.6f * t
          }
          .then(
            if (backdrop != null) {
              Modifier.glassSurface(
                state = backdrop,
                tint = controlTint,
                shape = FluidCapsuleShape,
                role = GlassRole.Interactive,
                opticalDepth = { expansion.value },
                pressed = { expansion.value },
              )
            } else {
              Modifier
                .clip(FluidCapsuleShape)
                .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
            },
          ),
      )

      // The label bubble, travelling with the lens. Measured unbounded: the strip is 48 dp wide
      // and the label is a month name — constrained to the strip it would ellipsise to two letters.
      AnimatedVisibility(
        visible = interacting,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .wrapContentWidth(align = Alignment.End, unbounded = true)
          .offset {
            IntOffset(
              -with(density) { (FluidSectionIndexDefaults.TouchWidth - 6.dp).roundToPx() },
              (lensCenterY() - labelHalfPx).roundToInt(),
            )
          },
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
            .then(
              if (backdrop != null) {
                Modifier.glassSurface(
                  state = backdrop,
                  tint = floatingTint,
                  shape = FluidCapsuleShape,
                  role = GlassRole.Modal,
                )
              } else {
                Modifier
                  .clip(FluidCapsuleShape)
                  .background(scheme.surfaceContainerHigh.copy(alpha = 0.96f))
              },
            )
            .padding(horizontal = 11.dp, vertical = 7.dp),
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.SemiBold,
          color = scheme.onSurface,
          maxLines = 1,
        )
      }

      // The hit target: generous around the resting cluster, deliberately NOT the whole edge — a
      // full-height grab strip would steal every scroll that starts near the screen's right edge.
      // Once a drag has begun the pointer stream stays with this node wherever the finger goes, so
      // the whole unfolded rail is reachable from here.
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .fillMaxWidth()
          .height(maxOf(compactTrackHeight + 24.dp, 120.dp))
          .sectionIndexSemantics(
            sections = sampledSections,
            selectedIndex = selectedIndex,
            onSelect = { index ->
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Animated)
            },
          )
          .pointerInput(sampledSections, touchSlop, reducedMotion) {
            awaitEachGesture {
              val down = awaitFirstDown(requireUnconsumed = false)
              // The strip owns every gesture that starts on it. Left unconsumed, the list's
              // scrollable claims the movement the moment it crosses ITS slop — and then dragging
              // the index scrolls the page under it, which reads as the control being broken.
              down.consume()
              val hitTop = (rootHeightPx - size.height) / 2f
              var lastPosition = down.position
              var dragged = false
              var lastDragIndex: Int? = null
              interacting = true
              fingerFraction = expandedFractionForRootY(hitTop + down.position.y)
              scope.launch {
                if (reducedMotion) expansion.snapTo(1f)
                else expansion.animateTo(1f, FluidMotion.snappy())
              }

              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val position = change.position
                change.consume()
                if (!dragged && distance(down.position, position) >= touchSlop) {
                  dragged = true
                }
                if (dragged) {
                  val fraction = expandedFractionForRootY(hitTop + position.y)
                  fingerFraction = fraction
                  val index = indexForFraction(fraction)
                  if (index != lastDragIndex) {
                    lastDragIndex = index
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Immediate)
                  }
                }
                lastPosition = position
                if (change.changedToUpIgnoreConsumed() || !change.pressed) break
              }

              if (!dragged) {
                // A tap aims at what was on screen when it landed — the resting cluster — so it
                // maps through the compact geometry, not the rail's.
                val index = indexForFraction(compactFractionForRootY(hitTop + lastPosition.y))
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                currentOnSelect(sampledSections[index], FluidSectionSelectionMotion.Animated)
              }
              interacting = false
              scope.launch {
                if (reducedMotion) expansion.snapTo(0f)
                else expansion.animateTo(0f, FluidMotion.snappy())
              }
            }
          },
      )
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

  /** How far the unfolded rail stays from the strip's vertical ends. */
  val ExpandedMargin = 18.dp

  /** Inset of the first and last station inside the unfolded rail. */
  val ExpandedMarkInset = 16.dp

  /** Width of the unfolded rail. */
  val ExpandedTrackWidth = 20.dp

  /** Diameter of the lens that rides the rail. */
  val LensSize = 34.dp
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
  start + (stop - start) * fraction

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
