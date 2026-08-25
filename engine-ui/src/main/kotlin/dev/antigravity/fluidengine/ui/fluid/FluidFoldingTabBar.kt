package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The navigation bar, folded and unfolded by one number.
 *
 * The thing that makes this different from [FluidTabBar] is not that it can shrink. It is *how*:
 * there is **one** bar, and the fold is a single [Animatable] that every part reads inside a
 * `layout` or a `graphicsLayer` block, never in composition. A frame of the fold therefore costs a
 * measure and a draw, and nothing recomposes at all.
 *
 * The alternative — the one most implementations reach for — is to compose an open bar and a folded
 * bar and cross-fade them with shared elements. That is more code, it is slower at rest, and while
 * the animation runs *both* bars exist, both are measured, and both carry their own pane of glass:
 * on a material this expensive that is the single worst moment to double the surface count.
 *
 * What the parts do, from 0 (open) to 1 (folded):
 *
 *  * the capsule keeps the tab you are on and gives up the others, its width travelling from the
 *    whole row to one square;
 *  * the unselected tabs keep their place in the order and lose their width, which is what makes the
 *    capsule close *onto* the tab you are on rather than sliding it sideways;
 *  * the labels go first, over the opening third of the travel, because they stop being readable
 *    before anything else does;
 *  * the indicator grows from one tab wide to the whole capsule, so the tab you are left with *is*
 *    the indicator instead of sitting inside it.
 *
 * The technique is Square's (github.com/Lelonio/Square); the material and the drag are the engine's.
 */
@Composable
fun FluidFoldingTabBar(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  /** 0 open, 1 folded. Held by the caller so a scroll, or anything else, can drive it. */
  fold: () -> Float,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
  /**
   * Called when the bar is folded and someone taps it.
   *
   * Folded, the only tab left *is* the one you are on — so a tap there would otherwise be a
   * reselect, and a reselect means "take me back to the top of this list". Which is the opposite of
   * what the tap was for: the bar is closed, and the obvious thing to do about a closed bar is open
   * it. Without this, the only way to reach the other tabs was to scroll, and pressing the bar threw
   * the page back to the top on the way.
   */
  onExpandRequest: (() -> Unit)? = null,
  /** A control that keeps its size through the fold — a search button, a profile, an overflow. */
  trailing: (@Composable () -> Unit)? = null,
) {
  if (items.isEmpty()) return

  val density = LocalDensity.current
  val openPx = with(density) { FluidFoldingTabBarDefaults.OpenHeight.roundToPx() }
  val foldedPx = with(density) { FluidFoldingTabBarDefaults.FoldedHeight.roundToPx() }
  val spacingPx = with(density) { FluidFoldingTabBarDefaults.Spacing.roundToPx() }

  Layout(
    modifier = modifier,
    content = {
      Box(modifier = Modifier.layoutId(SlotCapsule)) {
        FluidFoldingTabCapsule(
          items = items,
          selectedRoute = selectedRoute,
          onSelect = onSelect,
          onReselect = onReselect,
          onExpandRequest = onExpandRequest,
          backdrop = backdrop,
          fold = fold,
        )
      }
      if (trailing != null) {
        Box(modifier = Modifier.layoutId(SlotTrailing)) { trailing() }
      }
    },
  ) { measurables, constraints ->
    // Read here, in measure. Nothing above this line depends on the fold, so nothing above this line
    // runs again when it moves.
    val f = fold().fastCoerceIn(0f, 1f)
    val width = constraints.maxWidth
    val rowHeight = lerp(openPx, foldedPx, f)

    val trailingPlaceable = measurables
      .firstOrNull { it.layoutId == SlotTrailing }
      ?.measure(Constraints.fixed(rowHeight, rowHeight))
    val trailingWidth = trailingPlaceable?.width ?: 0
    val gap = if (trailingPlaceable != null) spacingPx else 0

    val capsuleOpen = (width - trailingWidth - gap).coerceAtLeast(1)
    val capsuleWidth = fluidFoldedCapsuleWidth(capsuleOpen, rowHeight, f)
    val capsulePlaceable = measurables
      .first { it.layoutId == SlotCapsule }
      .measure(Constraints.fixed(capsuleWidth, rowHeight))

    layout(width, rowHeight) {
      // Open, the pair spans the whole width and this is the identity. Folded, the two travel
      // *inwards* and settle together in the middle, because a control that has shrunk to a circle
      // and stayed pinned to the corner of the display is a control nobody can reach with the hand
      // already holding the phone — and it looks abandoned there besides.
      val pairWidth = capsuleWidth + gap + trailingWidth
      val pairLeft = lerp(0, ((width - pairWidth) / 2).coerceAtLeast(0), f)
      capsulePlaceable.place(pairLeft, 0)
      trailingPlaceable?.place(pairLeft + capsuleWidth + gap, 0)
    }
  }
}

/**
 * The capsule: the glass, the tabs printed on it, the invisible accent copy, and the lens.
 *
 * Same three-surface arrangement as [FluidTabBar] — the selected tab is *seen through* the indicator
 * rather than painted a different colour — with the fold threaded through the two `layout` blocks
 * that decide how wide anything is.
 */
@Composable
private fun FluidFoldingTabCapsule(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  onReselect: (FluidTabItem) -> Unit,
  onExpandRequest: (() -> Unit)?,
  backdrop: GlassBackdropState,
  fold: () -> Float,
) {
  val accent = MaterialTheme.colorScheme.primary
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val insetPx = with(density) { FluidFoldingTabBarDefaults.Inset.roundToPx() }
  val selectedIndex = items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0)

  val barGlass = rememberGlassBackdrop()
  val tabsGlass = rememberGlassBackdrop()
  val indicatorBackdrop = rememberCombinedGlassBackdrop(barGlass, tabsGlass)

  // The indicator's position, in tab indices, which is allowed to sit between two of them while a
  // finger is pushing it. Animated as its own value and read only in layout and draw.
  val indicator = remember(scope, items.size, isLtr) {
    GlassDragAnimation(
      animationScope = scope,
      initialValue = selectedIndex.toFloat(),
      valueRange = 0f..(items.size - 1).toFloat(),
      visibilityThreshold = 0.001f,
      initialScale = 1f,
      pressedScale = if (reducedMotion) 1f else 1.06f,
      // Holding the lens and pushing it sideways moves the selection with the finger, tab by tab,
      // and lets go on whichever one it is nearest. This is the whole reason the indicator is a
      // `GlassDragAnimation` and not an `Animatable`, and leaving it off — which it was — turned
      // the capsule into a row of buttons wearing a lens.
      //
      // `size` here is the lens's own box, and while the bar is open the lens is exactly one tab
      // wide: it *is* the pitch, so no measurement has to be plumbed down from the layout.
      onDrag = { size, dragAmount ->
        if (fold() <= FoldedEnough) {
          updateValue(
            fluidTabDragTarget(
              from = targetValue,
              dragX = dragAmount.x,
              pitch = size.width,
              count = items.size,
              isLtr = isLtr,
            ),
          )
        }
      },
      onDragStopped = {
        // Folded there is nowhere to drag to: the capsule is one tab wide, and the gesture is the
        // person asking for the bar back.
        if (fold() > FoldedEnough) {
          animateToValue(value)
          onExpandRequest?.invoke()
          return@GlassDragAnimation
        }
        val target = targetValue.fastRoundToInt().fastCoerceIn(0, items.size - 1)
        animateToValue(target.toFloat())
        items.getOrNull(target)?.let { item ->
          if (item.route == selectedRoute) onReselect(item) else onSelect(item)
        }
      },
    )
  }

  LaunchedEffect(indicator, selectedRoute) {
    snapshotFlow { items.indexOfFirst { it.route == selectedRoute }.coerceAtLeast(0) }
      .collect { index ->
        if (reducedMotion) indicator.snapToValue(index.toFloat())
        else indicator.animateToValue(index.toFloat())
      }
  }

  val tabs: @Composable () -> Unit = {
    items.forEach { item ->
      FluidFoldingTab(
        item = item,
        selected = item.route == selectedRoute,
        fold = fold,
        onClick = {
          when {
            fold() > FoldedEnough && onExpandRequest != null -> onExpandRequest()
            item.route == selectedRoute -> onReselect(item)
            else -> onSelect(item)
          }
        },
      )
    }
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // 1. The bar.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassSurface(
          state = backdrop,
          tint = GlassDefaults.floatingTint(),
          shape = FluidCapsuleShape,
          role = GlassRole.Floating,
          exports = barGlass,
        ),
    ) {
      FluidFoldingTabRow(
        fold = fold,
        selectedIndex = selectedIndex,
        insetPx = insetPx,
        content = tabs,
      )
    }

    // 2. The invisible accent copy the lens reads.
    Box(
      modifier = Modifier
        .clearAndSetSemantics {}
        .alpha(0f)
        .layerBackdrop(tabsGlass.layerBackdrop!!)
        .fillMaxSize()
        .graphicsLayer(colorFilter = ColorFilter.tint(accent)),
    ) {
      FluidFoldingTabRow(
        fold = fold,
        selectedIndex = selectedIndex,
        insetPx = insetPx,
        content = tabs,
      )
    }

    // 3. The lens.
    Box(
      modifier = Modifier
        .fluidFoldingIndicatorLayout(
          fold = fold,
          position = { indicator.value },
          count = items.size,
          insetPx = insetPx,
          isLtr = isLtr,
        )
        .then(indicator.modifier)
        .glassSurface(
          state = indicatorBackdrop,
          tint = GlassDefaults.selectionTint(),
          shape = FluidCapsuleShape,
          role = GlassRole.Interactive,
          // No frosting, ever. The lens is standing on a label six pixels tall: any blur at all and
          // the selected tab is the one word in the bar you cannot read.
          optics = remember { GlassDefaults.optics(GlassRole.Interactive).copy(blurScale = 0f) },
          // And no lens at rest either. Held still it draws the bar back exactly as it is, so the
          // tab underneath shows through crisp and in the accent colour — the selection *is* that.
          opticalDepth = { indicator.pressProgress },
          pressed = { indicator.pressProgress },
          layerBlock = if (reducedMotion) {
            null
          } else {
            {
              scaleX = indicator.scaleX
              scaleY = indicator.scaleY
              // Stretched along the direction of travel and thinned across it, by how fast it is
              // going. This is the detail that makes the indicator read as liquid.
              val velocity = indicator.velocity / 10f
              scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
              scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            }
          },
        ),
    )
  }
}

/**
 * The tabs inside the capsule.
 *
 * The unselected ones keep their place in the order and give up their width. That is the whole of
 * why the capsule closes *onto* the tab you are on: nothing slides, the room simply runs out.
 */
@Composable
private fun FluidFoldingTabRow(
  fold: () -> Float,
  selectedIndex: Int,
  insetPx: Int,
  content: @Composable () -> Unit,
) {
  Layout(content = content) { measurables, constraints ->
    val f = fold().fastCoerceIn(0f, 1f)
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val inner = (width - insetPx * 2).coerceAtLeast(1)
    val count = measurables.size
    val open = if (count > 0) inner / count else inner
    val chosen = selectedIndex.coerceIn(0, (count - 1).coerceAtLeast(0))

    val widths = fluidFoldedTabWidths(count = count, selectedIndex = chosen, inner = inner, fold = f)
    val placeables = measurables.mapIndexed { index, measurable ->
      measurable.measure(
        Constraints.fixed(
          widths[index].coerceAtLeast(1),
          (height - insetPx * 2).coerceAtLeast(1),
        ),
      )
    }

    layout(width, height) {
      var x = insetPx
      placeables.forEachIndexed { index, placeable ->
        placeable.place(x, insetPx)
        x += widths[index]
      }
    }
  }
}

@Composable
private fun FluidFoldingTab(
  item: FluidTabItem,
  selected: Boolean,
  fold: () -> Float,
  onClick: () -> Unit,
) {
  val contentColor = if (selected) {
    MaterialTheme.colorScheme.onSurface
  } else {
    MaterialTheme.colorScheme.onSurfaceVariant
  }
  Box(
    modifier = Modifier
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
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = item.label,
        modifier = Modifier
          // The label goes first, and it gives up its height as well as its opacity — otherwise the
          // icon stays parked above a gap for the rest of the fold.
          .fluidFoldAway(fold)
          .graphicsLayer { alpha = fluidFoldAlpha(fold()) },
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = contentColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Where the indicator sits: one tab wide while the bar is open, the whole capsule once it has
 * folded — so the tab you are left with *is* the indicator rather than sitting inside it.
 */
private fun Modifier.fluidFoldingIndicatorLayout(
  fold: () -> Float,
  position: () -> Float,
  count: Int,
  insetPx: Int,
  isLtr: Boolean,
): Modifier = layout { measurable, constraints ->
  val f = fold().fastCoerceIn(0f, 1f)
  val width = constraints.maxWidth
  val height = constraints.maxHeight
  val inner = (width - insetPx * 2).coerceAtLeast(1)
  val open = if (count > 0) inner / count else inner
  val indicatorWidth = fluidFoldedIndicatorWidth(open = open, inner = inner, fold = f)
  val placeable = measurable.measure(
    Constraints.fixed(indicatorWidth, (height - insetPx * 2).coerceAtLeast(1)),
  )
  layout(width, height) {
    val travel = fluidFoldedIndicatorTravel(position = position(), tabWidth = open, fold = f)
    // Right to left is a mirrored *coordinate*, not a mirrored layout: the indicator is placed by
    // arithmetic rather than by the layout system, so nothing flips it for us.
    val x = if (isLtr) insetPx + travel else width - insetPx - travel - indicatorWidth
    placeable.place(x, insetPx)
  }
}

/** Fades a part out over the opening [until] of the fold. */
internal fun fluidFoldAlpha(fold: Float, until: Float = 0.45f): Float =
  (1f - fold / until).fastCoerceIn(0f, 1f)

/** Lets a part give up its height on the same schedule its opacity goes. */
private fun Modifier.fluidFoldAway(fold: () -> Float, until: Float = 0.45f): Modifier =
  layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val keep = fluidFoldAlpha(fold(), until)
    layout(placeable.width, (placeable.height * keep).roundToInt()) { placeable.place(0, 0) }
  }

// ---------------------------------------------------------------------------------------------
// The arithmetic of the fold
// ---------------------------------------------------------------------------------------------
//
// Pulled out of the `layout` blocks and left visible, because it is the only part of this component
// that can be checked without a device — and it is the part that silently goes wrong. Every one of
// these runs on the measure pass of every frame of a fold, so they are also the code that must not
// allocate.

/** The capsule: the whole row when open, one square when folded. */
internal fun fluidFoldedCapsuleWidth(openWidth: Int, rowHeight: Int, fold: Float): Int =
  lerp(openWidth, rowHeight, fold.fastCoerceIn(0f, 1f)).coerceAtLeast(1)

/**
 * How wide each tab is at [fold].
 *
 * The unselected ones travel to **zero** and keep their place in the order. That is the whole trick:
 * nothing slides, the room simply runs out, and what is left is the tab you are on. Sliding it
 * instead would make a four-tab bar close by moving the selected tab up to three tab-widths across
 * the screen, which reads as the bar rearranging itself rather than closing.
 */
internal fun fluidFoldedTabWidths(
  count: Int,
  selectedIndex: Int,
  inner: Int,
  fold: Float,
): IntArray {
  if (count <= 0) return IntArray(0)
  val f = fold.fastCoerceIn(0f, 1f)
  val open = inner / count
  val chosen = selectedIndex.coerceIn(0, count - 1)
  return IntArray(count) { index ->
    lerp(open, if (index == chosen) inner else 0, f)
  }
}

/** The indicator: one tab wide open, the whole capsule folded — so the tab left *is* the indicator. */
internal fun fluidFoldedIndicatorWidth(open: Int, inner: Int, fold: Float): Int =
  lerp(open, inner, fold.fastCoerceIn(0f, 1f)).coerceAtLeast(1)

/** How far along the capsule the indicator sits. Folded it is home, because it fills the capsule. */
/**
 * Where a sideways push lands the indicator, in tab indices.
 *
 * [pitch] is the lens's own width, which while the bar is open *is* one tab: the drag is measured
 * against the thing being dragged, so no measurement has to be plumbed down from the layout. A
 * degenerate pitch — the first frame, before anything has been measured — leaves the indicator
 * exactly where it was rather than sending it to an end of the bar.
 */
internal fun fluidTabDragTarget(
  from: Float,
  dragX: Float,
  pitch: Int,
  count: Int,
  isLtr: Boolean,
): Float {
  val last = (count - 1).coerceAtLeast(0).toFloat()
  if (pitch <= 0 || count <= 1) return from.fastCoerceIn(0f, last)
  val direction = if (isLtr) 1f else -1f
  return (from + dragX / pitch * direction).fastCoerceIn(0f, last)
}

internal fun fluidFoldedIndicatorTravel(position: Float, tabWidth: Int, fold: Float): Int =
  lerp((position * tabWidth).roundToInt(), 0, fold.fastCoerceIn(0f, 1f))

object FluidFoldingTabBarDefaults {
  val OpenHeight: Dp = 64.dp

  /**
   * 58, not 52.
   *
   * Folded, this height *is* the button: the capsule closes to a square of it. Below about 56 dp
   * that square stops being a comfortable target for a thumb that is already holding the phone, and
   * the fold turns navigation into something you have to aim at.
   */
  val FoldedHeight: Dp = 58.dp
  val Spacing: Dp = 8.dp
  val Inset: Dp = 4.dp
  val HorizontalMargin: Dp = 14.dp

  /**
   * How far off the bottom edge the bar floats.
   *
   * Above the gesture handle rather than against it. At 8 dp the capsule sat on the system's own
   * navigation strip, which reads as the two of them being one thing — and put the folded button
   * within a few millimetres of the edge of the display.
   */
  val BottomMargin: Dp = 16.dp

  /** Vertical space a screen must leave free so its content clears the bar at its tallest. */
  val ContentInset: Dp = OpenHeight + BottomMargin
}

/**
 * The fold, and the scroll connection that moves it.
 *
 * Down folds it, **up unfolds it** — anywhere on the page, not only at the top. The first version
 * only came back when the list ran out of content above, on the reasoning that scrolling up is a
 * request for what is further up and not for the bar; that reasoning is fine and the behaviour it
 * produces is not, because it means the only way to reach navigation from the middle of a long list
 * is to travel to the top of it first.
 *
 * What stops it flickering is not refusing to open, it is a **commit threshold**, and an asymmetric
 * one: getting the bar out of the way is a considered response to a deliberate downward read, while
 * getting it back is what someone does when they want to leave. So revealing commits almost
 * immediately and hiding takes real intent. A gesture that changes direction resets the count, so a
 * wobble never adds up to either.
 */
@Stable
class FluidBarFold internal constructor(
  val progress: State<Float>,
  private val target: MutableFloatState,
  private val density: Float,
) {
  val folded: Boolean get() = target.floatValue > 0.5f

  private var pending = 0f

  val connection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      // The finger only. A fling settles by reporting deltas of its own, and the last of those
      // points the other way — which would reopen the bar at the end of every downward throw.
      if (source != NestedScrollSource.UserInput || available.y == 0f) return Offset.Zero
      if (pending != 0f && (pending < 0f) != (available.y < 0f)) pending = 0f
      pending += available.y
      val hiding = pending < 0f
      val threshold = (if (hiding) HideCommitDp else RevealCommitDp) * density
      if (abs(pending) < threshold) return Offset.Zero
      pending = 0f
      target.floatValue = if (hiding) 1f else 0f
      return Offset.Zero
    }
  }

  fun unfold() {
    pending = 0f
    target.floatValue = 0f
  }

  fun foldNow() {
    pending = 0f
    target.floatValue = 1f
  }
}

@Composable
fun rememberFluidBarFold(): FluidBarFold {
  val fold = remember { Animatable(0f) }
  val target = remember { mutableFloatStateOf(0f) }
  val density = LocalDensity.current.density
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  LaunchedEffect(fold, reducedMotion) {
    snapshotFlow { target.floatValue }.collect { wanted ->
      if (reducedMotion) {
        fold.snapTo(wanted)
      } else {
        // Soft, and never overshooting: this is a bar settling, not a switch.
        fold.animateTo(wanted, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow))
      }
    }
  }

  return remember(fold, density) { FluidBarFold(fold.asState(), target, density) }
}

/** Past this, the bar counts as closed and a tap on it means "open" rather than "select". */
private const val FoldedEnough = 0.5f

/** How far a scroll must travel in one direction before the bar answers it. See [FluidBarFold]. */
private const val HideCommitDp = 36f
private const val RevealCommitDp = 10f

private const val SlotCapsule = "fluid:folding-capsule"
private const val SlotTrailing = "fluid:folding-trailing"

