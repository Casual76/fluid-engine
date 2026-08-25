package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import kotlin.math.abs
import kotlin.math.sign

@Immutable
data class FluidTabItem(
  val route: String,
  val label: String,
  val icon: ImageVector,
)

object FluidTabBarDefaults {
  val Height = 64.dp
  val HorizontalMargin = 14.dp
  /** Above the system's gesture handle, not against it. See [FluidFoldingTabBarDefaults]. */
  val BottomMargin = 16.dp
  val RailWidth = 84.dp

  /** Vertical space a screen must leave free so its content clears the floating bar. */
  val ContentInset = Height + BottomMargin

  /** Gap between the bar's edge and the indicator inside it. */
  internal val IndicatorInset = 4.dp
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
    // Kept for the rail and for reduced motion, where the indicator is a position rather than an
    // object with mass. The horizontal bar's own indicator is spring-driven and does not read this.
    TabIndicatorTiming(leadingDurationMillis = 190, trailingDurationMillis = 250)
  }

/**
 * The floating tab bar: a capsule of glass with a lens sliding inside it.
 *
 * Three surfaces, stacked, and the arrangement is the whole trick:
 *
 *  1. **The bar.** Glass over the page, with the tabs printed on it in the resting colour.
 *  2. **A second copy of the tabs, invisible.** Drawn at zero alpha, tinted with the accent, and
 *     recorded into its own layer. Nobody ever sees it directly.
 *  3. **The indicator.** A lens that refracts the page *and* that invisible accent copy. Wherever it
 *     sits, the tab underneath appears through it — magnified by the lens and in the accent colour.
 *
 * So the selected tab is not painted a different colour. It is *seen through glass*, which is what
 * makes the selection look like it belongs to a physical object rather than to a stylesheet. The
 * indicator can also be dragged: it follows the finger, squashes in the direction it is travelling,
 * and settles on whichever tab it is nearest when you let go.
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
  if (items.isEmpty()) return

  val tint = GlassDefaults.floatingTint()
  val accent = MaterialTheme.colorScheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()

  // The bar's own finished material, and the accent-tinted copy of its contents. The indicator
  // refracts both, in that order, so it holds the same image a real lens on this bar would.
  val barGlass = rememberGlassBackdrop()
  val tabsGlass = rememberGlassBackdrop()
  // The lens sits on the *bar*, so what it refracts is the bar's finished material with the accent
  // copy of the tabs laid over it — never the page directly. Handing it the page instead punches a
  // hole straight through the bar, which is exactly what it looks like.
  val indicatorBackdrop = rememberCombinedGlassBackdrop(barGlass, tabsGlass)

  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

  BoxWithConstraints(
    modifier = modifier.fillMaxWidth().height(FluidTabBarDefaults.Height),
    contentAlignment = Alignment.CenterStart,
  ) {
    val inset = FluidTabBarDefaults.IndicatorInset
    val tabWidth = with(density) {
      (constraints.maxWidth.toFloat() - inset.toPx() * 2f) / items.size
    }

    // How far the whole bar is dragged past its ends. Rubber-banded to a few dp, so pulling the
    // indicator against the first or last tab moves the bar itself a little and then refuses.
    val overscroll = remember { Animatable(0f) }
    val barOffset by remember(density, constraints) {
      derivedStateOf {
        val fraction = (overscroll.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
        with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
      }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndex) }
    val indicator = remember(scope, items.size, tabWidth, isLtr) {
      GlassDragAnimation(
        animationScope = scope,
        initialValue = selectedIndex.toFloat(),
        valueRange = 0f..(items.size - 1).toFloat(),
        visibilityThreshold = 0.001f,
        initialScale = 1f,
        // Grows to the full height of the bar while held: the lens is being lifted off the surface.
        pressedScale = if (reducedMotion) 1f else 62f / 56f,
        onDragStopped = {
          val target = targetValue.fastRoundToInt().fastCoerceIn(0, items.size - 1)
          animateToValue(target.toFloat())
          scope.launchOverscrollSettle(overscroll)
          if (target != currentIndex) {
            currentIndex = target
            items.getOrNull(target)?.let(onSelect)
          }
        },
        onDrag = { _, dragAmount ->
          if (tabWidth <= 0f) return@GlassDragAnimation
          val direction = if (isLtr) 1f else -1f
          updateValue(
            (targetValue + dragAmount.x / tabWidth * direction)
              .fastCoerceIn(0f, (items.size - 1).toFloat()),
          )
          scope.launchOverscrollDrag(overscroll, dragAmount.x)
        },
      )
    }

    // Selection can also arrive from outside — a deep link, the back stack, a restored state. The
    // indicator follows it without reporting anything back: whoever changed the route already knows.
    LaunchedEffect(selectedIndex, indicator) {
      if (currentIndex != selectedIndex) {
        currentIndex = selectedIndex
        if (reducedMotion) indicator.snapToValue(selectedIndex.toFloat())
        else indicator.animateToValue(selectedIndex.toFloat())
      }
    }

    val highlight = remember(scope, tabWidth, isLtr, reducedMotion) {
      GlassTouchHighlight(
        animationScope = scope,
        strength = { if (reducedMotion) 0f else 1f },
        // The hotspot rides the indicator rather than the finger: on a tab bar the thing being
        // pushed is the lens, and lighting up somewhere else would break that.
        position = { size, _ ->
          val centre = (indicator.value + 0.5f) * tabWidth + barOffset
          Offset(if (isLtr) centre else size.width - centre, size.height / 2f)
        },
      )
    }

    val tabs: @Composable RowScope.() -> Unit = {
      items.forEachIndexed { index, item ->
        key(item.route) {
          FluidTabItemContent(
            item = item,
            selected = index == currentIndex,
            modifier = Modifier.weight(1f),
            onClick = {
              if (item.route == selectedRoute) {
                onReselect(item)
              } else {
                currentIndex = index
                if (reducedMotion) indicator.snapToValue(index.toFloat())
                else indicator.animateToValue(index.toFloat())
                onSelect(item)
              }
            },
          )
        }
      }
    }

    // 1. The bar.
    Row(
      modifier = Modifier
        .graphicsLayer { translationX = barOffset }
        .fillMaxSize()
        .glassSurface(
          state = backdrop,
          tint = tint,
          shape = FluidCapsuleShape,
          role = GlassRole.Floating,
          exports = barGlass,
          layerBlock = if (reducedMotion) {
            null
          } else {
            {
              // The whole bar breathes with the press, a fraction of what the indicator does.
              val swell = lerp(1f, 1f + 12.dp.toPx() / size.width, indicator.pressProgress)
              scaleX = swell
              scaleY = swell
            }
          },
        )
        .then(highlight.modifier)
        .padding(horizontal = FluidTabBarDefaults.IndicatorInset),
      verticalAlignment = Alignment.CenterVertically,
      content = tabs,
    )

    // 2. The invisible accent copy the indicator reads.
    CompositionLocalProvider(LocalFluidTabTint provides accent) {
      Row(
        modifier = Modifier
          .clearAndSetSemantics {}
          .alpha(0f)
          .layerBackdrop(tabsGlass.layerBackdrop!!)
          .graphicsLayer { translationX = barOffset }
          .fillMaxSize()
          .padding(horizontal = FluidTabBarDefaults.IndicatorInset)
          .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
        verticalAlignment = Alignment.CenterVertically,
        content = tabs,
      )
    }

    // 3. The lens.
    Box(
      modifier = Modifier
        .padding(
          horizontal = FluidTabBarDefaults.IndicatorInset,
          vertical = FluidTabBarDefaults.IndicatorInset,
        )
        .graphicsLayer {
          translationX = tabIndicatorTranslation(
            value = indicator.value,
            tabWidth = tabWidth,
            barOffset = barOffset,
            containerWidth = size.width,
            isLtr = isLtr,
          )
        }
        .then(highlight.gestureModifier)
        .then(indicator.modifier)
        .glassSurface(
          state = indicatorBackdrop,
          tint = GlassDefaults.selectionTint(),
          shape = FluidCapsuleShape,
          role = GlassRole.Interactive,
          // No frosting, ever. The lens is standing on a label six pixels tall: any blur at all and
          // the selected tab is the one word in the bar you cannot read.
          optics = remember { GlassDefaults.optics(GlassRole.Interactive).copy(blurScale = 0f) },
          // And no lens at rest either. Held still, the indicator draws the bar back exactly as it
          // is, so the tab underneath shows through crisp and in the accent colour — the selection
          // *is* that, not a coloured pill. The glass thickens only while a finger is on it.
          opticalDepth = { indicator.pressProgress },
          pressed = { indicator.pressProgress },
          layerBlock = if (reducedMotion) {
            null
          } else {
            {
              scaleX = indicator.scaleX
              scaleY = indicator.scaleY
              // Stretched along the direction of travel and thinned across it, by how fast it is
              // going. This is the single detail that makes the indicator read as liquid.
              val velocity = indicator.velocity / 10f
              scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
              scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            }
          },
        )
        .fillMaxHeight()
        .width(with(density) { tabWidth.toDp() }),
    )
  }
}

/**
 * Where the lens sits, in the padded space inside the bar.
 *
 * Right to left is not a mirrored layout here but a mirrored *coordinate*: the indicator is placed
 * by a graphics-layer translation rather than by the layout system, so nothing flips it for us and
 * the arithmetic has to. Extracted because an RTL error in a draw-time expression is invisible until
 * someone actually runs the app in Arabic or Hebrew.
 */
internal fun tabIndicatorTranslation(
  value: Float,
  tabWidth: Float,
  barOffset: Float,
  containerWidth: Float,
  isLtr: Boolean,
): Float {
  val travel = value * tabWidth + barOffset
  return if (isLtr) travel else containerWidth - travel - tabWidth
}

/** Applied by the invisible accent copy so a tab can drop its own colour animation there. */
private val LocalFluidTabTint = compositionLocalOf<Color?> { null }

@Composable
private fun FluidTabItemContent(
  item: FluidTabItem,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // The accent copy is tinted wholesale by a colour filter, so its own colour animation would only
  // fight it. Everything there resolves to one flat colour and the filter does the rest.
  val tinted = LocalFluidTabTint.current != null
  val contentColor by animateColorAsState(
    targetValue = when {
      tinted -> MaterialTheme.colorScheme.onSurface
      selected -> MaterialTheme.colorScheme.onSurface
      else -> MaterialTheme.colorScheme.onSurfaceVariant
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
      .fillMaxHeight()
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

/** Wide-screen variant: the same material and the same lens, stood on its side. */
@Composable
fun FluidTabRail(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
) {
  if (items.isEmpty()) return

  val tint = GlassDefaults.floatingTint()
  val accent = MaterialTheme.colorScheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val density = LocalDensity.current
  val timing = tabIndicatorTiming(reducedMotion)
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

  val railGlass = rememberGlassBackdrop()
  val tabsGlass = rememberGlassBackdrop()
  val indicatorBackdrop = rememberCombinedGlassBackdrop(railGlass, tabsGlass)

  val itemHeight = FluidTabBarDefaults.Height
  val pillTop = remember { Animatable(0f) }

  LaunchedEffect(selectedIndex, itemHeight, reducedMotion) {
    val target = with(density) { (selectedIndex * itemHeight.toPx()) }
    if (reducedMotion) {
      pillTop.snapTo(target)
    } else {
      pillTop.animateTo(target, spring(dampingRatio = 0.85f, stiffness = 420f))
    }
  }

  val tabs: @Composable () -> Unit = {
    items.forEach { item ->
      Box(modifier = Modifier.fillMaxWidth().height(itemHeight)) {
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

  Box(modifier = modifier.width(FluidTabBarDefaults.RailWidth)) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .glassSurface(
          state = backdrop,
          tint = tint,
          shape = ContinuousCornerShape(28.dp),
          role = GlassRole.Floating,
          exports = railGlass,
        ),
      horizontalAlignment = Alignment.CenterHorizontally,
      content = { tabs() },
    )

    CompositionLocalProvider(LocalFluidTabTint provides accent) {
      Column(
        modifier = Modifier
          .clearAndSetSemantics {}
          .alpha(0f)
          .layerBackdrop(tabsGlass.layerBackdrop!!)
          .fillMaxSize()
          .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = { tabs() },
      )
    }

    Box(
      modifier = Modifier
        .graphicsLayer { translationY = pillTop.value }
        .padding(horizontal = 8.dp, vertical = 6.dp)
        .glassSurface(
          state = indicatorBackdrop,
          tint = GlassDefaults.selectionTint(),
          shape = ContinuousCornerShape(22.dp),
          role = GlassRole.Interactive,
          optics = remember { GlassDefaults.optics(GlassRole.Interactive).copy(blurScale = 0f) },
          opticalDepth = { 0f },
        )
        .fillMaxWidth()
        .height(itemHeight),
    )
  }
}

/** Padding a floating tab bar needs around itself. */
@Composable
fun fluidTabBarPadding(): PaddingValues = PaddingValues(
  horizontal = FluidTabBarDefaults.HorizontalMargin,
  vertical = FluidTabBarDefaults.BottomMargin,
)

private fun CoroutineScope.launchOverscrollDrag(overscroll: Animatable<Float, *>, delta: Float) {
  launch { overscroll.snapTo(overscroll.value + delta) }
}

private fun CoroutineScope.launchOverscrollSettle(overscroll: Animatable<Float, *>) {
  launch { overscroll.animateTo(0f, spring(dampingRatio = 1f, stiffness = 300f)) }
}
