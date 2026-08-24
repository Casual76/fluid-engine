package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Immutable
data class FluidTabItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

object FluidTabBarDefaults {
  val Height = 64.dp
  val HorizontalMargin = 14.dp
  val BottomMargin = 8.dp
  val RailWidth = 84.dp

  /** Vertical space a screen must leave free so its content clears the floating bar. */
  val ContentInset = Height + BottomMargin
}

@Immutable
internal data class TabIndicatorTiming(
  val leadingDurationMillis: Int,
  val trailingDurationMillis: Int,
)

internal fun tabIndicatorTiming(reducedMotion: Boolean): TabIndicatorTiming =
  if (reducedMotion) {
    TabIndicatorTiming(leadingDurationMillis = 0, trailingDurationMillis = 0)
  } else {
    // A finite asymmetric settle preserves the elastic-looking stretch without keeping the whole
    // window (including the backdrop blur) active for the long tail of a spring.
    TabIndicatorTiming(leadingDurationMillis = 190, trailingDurationMillis = 250)
  }

private fun indicatorSpec(durationMillis: Int) = tween<Float>(
  durationMillis = durationMillis,
  easing = FastOutSlowInEasing,
)

/**
 * The floating tab bar: a capsule of frosted glass that content scrolls *under* rather than being
 * cut off above.
 *
 * Selection is carried by colour, a restrained icon settle and a convex glass lens that travels
 * between tabs. The lens reuses the bar's backdrop and is draw-only: it adds an optical rim without
 * multiplying the number of cropped blur layers.
 */
@Composable
fun FluidTabBar(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  val tint = GlassDefaults.floatingTint()
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
  val selectionColor = MaterialTheme.colorScheme.primary
  val onSurface = MaterialTheme.colorScheme.onSurface
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val indicatorTiming = tabIndicatorTiming(reducedMotion)
  val density = LocalDensity.current
  var rowWidthPx by remember { mutableFloatStateOf(0f) }
  val underglowStart = remember { Animatable(0f) }
  val underglowEnd = remember { Animatable(0f) }
  var underglowPlaced by remember { mutableStateOf(false) }
  val itemWidthPx = if (items.isEmpty()) 0f else rowWidthPx / items.size
  val underglowHorizontalInsetPx = with(density) { 5.dp.toPx() }
  val underglowVerticalInsetPx = with(density) { 5.dp.toPx() }
  val underglowRadiusPx = with(density) { 24.dp.toPx() }

  LaunchedEffect(selectedIndex, itemWidthPx, reducedMotion) {
    if (selectedIndex < 0 || itemWidthPx <= 0f) return@LaunchedEffect
    val targetStart = selectedIndex * itemWidthPx + underglowHorizontalInsetPx
    val targetEnd = (selectedIndex + 1) * itemWidthPx - underglowHorizontalInsetPx
    if (!underglowPlaced || reducedMotion) {
      underglowStart.snapTo(targetStart)
      underglowEnd.snapTo(targetEnd)
      underglowPlaced = true
    } else {
      val movingForward = targetStart > underglowStart.value
      // The leading edge arrives first while the trailing edge follows with a calmer response. The
      // capsule stretches in the direction of travel and then recomposes itself, carrying all tab
      // continuity without wiping two full pages across one another.
      coroutineScope {
        if (movingForward) {
          launch {
            underglowEnd.animateTo(
              targetEnd,
              indicatorSpec(indicatorTiming.leadingDurationMillis),
            )
          }
          launch {
            underglowStart.animateTo(
              targetStart,
              indicatorSpec(indicatorTiming.trailingDurationMillis),
            )
          }
        } else {
          launch {
            underglowStart.animateTo(
              targetStart,
              indicatorSpec(indicatorTiming.leadingDurationMillis),
            )
          }
          launch {
            underglowEnd.animateTo(
              targetEnd,
              indicatorSpec(indicatorTiming.trailingDurationMillis),
            )
          }
        }
      }
    }
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .height(FluidTabBarDefaults.Height)
      .glassSurface(
        state = backdrop,
        tint = tint,
        shape = FluidGlassCapsuleShape,
        edge = GlassEdge.None,
      )
      .onSizeChanged { rowWidthPx = it.width.toFloat() }
      .drawWithCache {
        val lensBrushes = glassSelectionBrushes(selectionColor, onSurface, size)
        onDrawBehind {
          if (!underglowPlaced || selectedIndex < 0 || itemWidthPx <= 0f) return@onDrawBehind
          drawGlassSelectionLens(
            brushes = lensBrushes,
            topLeft = Offset(
              x = underglowStart.value,
              y = underglowVerticalInsetPx,
            ),
            lensSize = Size(
              width = (underglowEnd.value - underglowStart.value).coerceAtLeast(0f),
              height = (size.height - underglowVerticalInsetPx * 2).coerceAtLeast(0f),
            ),
            radius = underglowRadiusPx,
          )
        }
      },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    items.forEach { item ->
      key(item.route) {
        FluidTabItemContent(
          item = item,
          selected = item.route == selectedRoute,
          modifier = Modifier.weight(1f),
          onClick = {
            if (item.route == selectedRoute) onReselect(item) else onSelect(item)
          },
        )
      }
    }
  }
}

@Composable
private fun FluidTabItemContent(
  item: FluidTabItem,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  // Colour is the only thing here that has to recompose; the icon's settle is read inside the
  // graphics layer, so it plays on the render thread without touching composition. Snapping the
  // whole tab to its new state — which is what this used to do — was the reason selecting a tab
  // registered as nothing happening at all.
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val contentColor by animateColorAsState(
    targetValue = if (selected) {
      MaterialTheme.colorScheme.primary
    } else {
      MaterialTheme.colorScheme.onSurfaceVariant
    },
    animationSpec = if (reducedMotion) snap() else FluidMotion.color(200),
    label = "tab tint",
  )

  val density = LocalDensity.current
  val selectedLiftPx = with(density) { -1.5.dp.toPx() }
  val settle by animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.fluid(),
    label = "tab settle",
  )

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("top_level_${item.route}")
      .semantics {
        this.role = Role.Tab
        this.selected = selected
      }
      .fluidPressable(onClick = onClick, pressedScale = 0.96f),
    contentAlignment = Alignment.Center,
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Icon(
        imageVector = item.icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier
          .size(24.dp)
          .graphicsLayer {
            val scale = 0.94f + 0.06f * settle
            scaleX = scale
            scaleY = scale
            translationY = selectedLiftPx * settle
          },
      )
      Text(
        text = item.label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Wide-screen variant: the same material and indicator, stood on its side. */
@Composable
fun FluidTabRail(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }
  val tint = GlassDefaults.floatingTint()
  val pillColor = MaterialTheme.colorScheme.primary
  val onSurface = MaterialTheme.colorScheme.onSurface
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val indicatorTiming = tabIndicatorTiming(reducedMotion)

  var columnHeightPx by remember { mutableFloatStateOf(0f) }
  val pillTop = remember { Animatable(0f) }
  var pillPlaced by remember { mutableStateOf(false) }
  val itemHeightPx = if (items.isEmpty()) 0f else columnHeightPx / items.size

  LaunchedEffect(selectedIndex, itemHeightPx, reducedMotion) {
    if (itemHeightPx <= 0f || selectedIndex < 0) return@LaunchedEffect
    val target = selectedIndex * itemHeightPx
    if (!pillPlaced || reducedMotion) {
      pillTop.snapTo(target)
      pillPlaced = true
    } else {
      pillTop.animateTo(
        target,
        indicatorSpec(indicatorTiming.trailingDurationMillis),
      )
    }
  }

  val pillInsetPx = with(density) { 8.dp.toPx() }
  val pillRadiusPx = with(density) { 20.dp.toPx() }

  Column(
    modifier = modifier
      .width(FluidTabBarDefaults.RailWidth)
      .glassSurface(
        state = backdrop,
        tint = tint,
        shape = FluidGlassRoundedShape(28.dp),
        edge = GlassEdge.None,
      )
      .onSizeChanged { columnHeightPx = it.height.toFloat() }
      .drawWithCache {
        val lensBrushes = glassSelectionBrushes(pillColor, onSurface, size)
        onDrawBehind {
          if (!pillPlaced || selectedIndex < 0 || itemHeightPx <= 0f) return@onDrawBehind
          drawGlassSelectionLens(
            brushes = lensBrushes,
            topLeft = Offset(pillInsetPx, pillTop.value + pillInsetPx),
            lensSize = Size(size.width - pillInsetPx * 2, itemHeightPx - pillInsetPx * 2),
            radius = pillRadiusPx,
          )
        }
      },
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    items.forEach { item ->
      Box(modifier = Modifier.fillMaxWidth().height(FluidTabBarDefaults.Height)) {
        FluidTabItemContent(
          item = item,
          selected = item.route == selectedRoute,
          modifier = Modifier.fillMaxSize(),
          onClick = {
            if (item.route == selectedRoute) onReselect(item) else onSelect(item)
          },
        )
      }
    }
  }
}

/** Padding a floating tab bar needs around itself. */
@Composable
fun fluidTabBarPadding(): androidx.compose.foundation.layout.PaddingValues =
  androidx.compose.foundation.layout.PaddingValues(
    horizontal = FluidTabBarDefaults.HorizontalMargin,
    vertical = FluidTabBarDefaults.BottomMargin,
  )

private data class GlassSelectionBrushes(
  val body: Brush,
  val rim: Brush,
  val innerRim: Brush,
)

private fun glassSelectionBrushes(
  accent: Color,
  onSurface: Color,
  size: Size,
): GlassSelectionBrushes {
  val end = Offset(size.width, size.height)
  return GlassSelectionBrushes(
    body = Brush.linearGradient(
      colorStops = arrayOf(
        0f to Color.White.copy(alpha = 0.18f),
        0.38f to accent.copy(alpha = 0.13f),
        1f to accent.copy(alpha = 0.19f),
      ),
      start = Offset.Zero,
      end = end,
    ),
    rim = Brush.linearGradient(
      colorStops = arrayOf(
        0f to Color.White.copy(alpha = 0.78f),
        0.40f to Color.White.copy(alpha = 0.18f),
        0.68f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.22f),
      ),
      start = Offset.Zero,
      end = end,
    ),
    innerRim = Brush.linearGradient(
      colorStops = arrayOf(
        0f to Color.White.copy(alpha = 0.22f),
        0.52f to Color.Transparent,
        1f to onSurface.copy(alpha = 0.16f),
      ),
      start = Offset.Zero,
      end = end,
    ),
  )
}

private fun DrawScope.drawGlassSelectionLens(
  brushes: GlassSelectionBrushes,
  topLeft: Offset,
  lensSize: Size,
  radius: Float,
) {
  if (lensSize.width <= 0f || lensSize.height <= 0f) return
  val outerStroke = 1.15.dp.toPx()
  val inset = 2.dp.toPx()
  val innerSize = Size(
    width = (lensSize.width - inset * 2f).coerceAtLeast(0f),
    height = (lensSize.height - inset * 2f).coerceAtLeast(0f),
  )
  drawRoundRect(
    brush = brushes.body,
    topLeft = topLeft,
    size = lensSize,
    cornerRadius = CornerRadius(radius, radius),
  )
  drawRoundRect(
    brush = brushes.rim,
    topLeft = topLeft,
    size = lensSize,
    cornerRadius = CornerRadius(radius, radius),
    style = Stroke(width = outerStroke),
  )
  if (innerSize.width > 0f && innerSize.height > 0f) {
    val innerRadius = (radius - inset).coerceAtLeast(0f)
    drawRoundRect(
      brush = brushes.innerRim,
      topLeft = topLeft + Offset(inset, inset),
      size = innerSize,
      cornerRadius = CornerRadius(innerRadius, innerRadius),
      style = Stroke(width = 0.7.dp.toPx()),
    )
  }
}
