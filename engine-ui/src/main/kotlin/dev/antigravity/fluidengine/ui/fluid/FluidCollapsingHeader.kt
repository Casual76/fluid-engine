package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

/**
 * The large title that rises into a bar of glass, for a screen that is not a [FluidScreen].
 *
 * [FluidScreen] owns everything about a page: its list, its content padding, its elastic edge, its
 * backdrops, its pull to refresh. That is the right trade for a page being written now and the
 * wrong one for a page that already exists and already has all of those — an app with its own
 * shelves, its own grid, its own header art. Those pages were left with the one thing the family
 * is recognised by out of reach.
 *
 * This is that one thing, on its own. The caller keeps their list and their layout and gets:
 *
 *  * [FluidCollapsingTitle] as the first item *of their own list*, so the heading scrolls away with
 *    the content instead of being chrome bolted over it;
 *  * [FluidCollapsingTopBar] as an overlay, which stays clear until the title reaches it and then
 *    takes the title over — one piece of type travelling, never two cross-fading;
 *  * [fluidTitleCollapseOrigin] on the screen's root, which is the coordinate space both anchors
 *    are solved in.
 *
 * There is no second implementation: these call exactly what [FluidScreen] calls.
 *
 * The one thing the caller must supply that [FluidScreen] makes for itself is the [GlassBackdropState]
 * the bar refracts. It has to be a recording that does **not** contain the bar — the bar blurring a
 * picture of itself is the feedback this whole family is careful to avoid — and it has to be opaque
 * where the page is, or the blur is a smear the sharp original shows through. A recording of the
 * page's own ground plus its body is the usual answer.
 */
@Stable
class FluidTitleCollapse internal constructor(
  internal val morph: FluidTitleMorphState,
  internal val progress: State<Float>,
  internal val glassIntensity: State<Float>,
  internal val listState: LazyListState,
) {
  /**
   * 0 while the large title is at rest, 1 once it has docked in the bar.
   *
   * Exposed for callers that have something of their own to fade with the handover — a row of
   * chips, an avatar, a page's own artwork. Read it in a layer or a draw block, never in
   * composition: it changes on every frame of the scroll.
   */
  val collapseProgress: Float get() = progress.value
}

/**
 * Remembers the state the title's two homes are solved against.
 *
 * [title] is a key rather than a value: the anchors are measured from the type itself, so a screen
 * that changes its name has to measure again. [horizontalPadding] and [topBarHeight] must be the
 * ones the caller's own list actually uses — the resting anchor is *computed* from them rather than
 * measured, deliberately, because measuring it would read a position that already includes the
 * elastic edge's transform and the title would then travel at twice the speed of its own content.
 */
@Composable
fun rememberFluidTitleCollapse(
  title: String,
  listState: LazyListState,
  horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding,
  topBarHeight: Dp = FluidScreenDefaults.topBarHeight(),
): FluidTitleCollapse {
  val density = LocalDensity.current
  val titleLeftPx = with(density) { horizontalPadding.toPx() }
  val titleTopPx = with(density) { (topBarHeight + FluidScreenDefaults.TitleTopSpacing).toPx() }
  val morph = remember(title, density.fontScale, titleLeftPx, titleTopPx) {
    FluidTitleMorphState(expandedLeftInScreen = titleLeftPx, expandedTopInScreen = titleTopPx)
  }
  val progress = rememberCollapseProgress(listState, morph)

  val deadZonePx = with(density) { FluidScreenDefaults.ShieldDeadZone.toPx() }
  val rampPx = with(density) { FluidScreenDefaults.ShieldRampDistance.toPx() }
  val glassIntensity = remember(listState, progress, deadZonePx, rampPx) {
    derivedStateOf {
      calculateGlassIntensity(
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        collapseProgress = progress.value,
        deadZonePx = deadZonePx,
        rampDistancePx = rampPx,
      )
    }
  }

  return remember(morph, progress, glassIntensity, listState) {
    FluidTitleCollapse(morph, progress, glassIntensity, listState)
  }
}

/**
 * The coordinate origin both anchors are solved in. Put it on the screen's own root.
 *
 * Screen-local, not window-local: the computed anchor is made of the screen's own paddings, so the
 * measured one has to be converted into the same space. Read in window coordinates instead, any
 * offset the app puts around the screen becomes a translation error — a tablet's navigation rail
 * put the resting title a rail's width to the left of its own page, underneath the rail.
 */
fun Modifier.fluidTitleCollapseOrigin(collapse: FluidTitleCollapse): Modifier =
  onGloballyPositioned(collapse.morph::onScreenPositioned)

/**
 * The large title, to be emitted as the **first item of the caller's list**.
 *
 * First and nothing above it: the collapse is derived from this item's own measured position, so
 * anything between it and the top of the list makes the bar arrive at the wrong moment.
 */
@Composable
fun FluidCollapsingTitle(
  title: String,
  collapse: FluidTitleCollapse,
  subtitle: String? = null,
) {
  val density = LocalDensity.current
  FluidLargeTitle(
    title = title,
    subtitle = subtitle,
    style = androidx.compose.material3.MaterialTheme.typography.displayLarge,
    collapseProgress = collapse.progress,
    morphState = collapse.morph,
    fontScale = density.fontScale,
    reducedMotion = LocalFluidMotionPolicy.current.reducedMotion,
  )
}

/**
 * The bar the title docks into: clear at the top of the page, glass once anything has passed under
 * the status bar, and carrying the title itself once the heading has gone by.
 *
 * Place it last inside the screen's own top-aligned `Box`, over the list.
 */
@Composable
fun FluidCollapsingTopBar(
  title: String,
  collapse: FluidTitleCollapse,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)? = null,
  /** Short facts the bar cycles through once the title has docked. */
  titleFacets: List<String> = emptyList(),
  /**
   * The elastic edge's current offset, if the caller's list has one.
   *
   * The docked title is welded to the content it belongs to, which includes the content's bounce:
   * left at zero the title stands still while the page it is part of stretches past it.
   */
  contentTranslation: () -> Float = { 0f },
  onTapTitle: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val density = LocalDensity.current
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val activeFacet = rememberTitleFacet(titleFacets, collapse.progress)
  Box(modifier) {
    FluidTopBar(
      title = title,
      backdrop = backdrop,
      collapseProgress = collapse.progress,
      activeFacet = activeFacet,
      morphState = collapse.morph,
      compactStyle = rememberCompactTitleStyle(
        androidx.compose.material3.MaterialTheme.typography.displayLarge,
      ),
      contentTranslation = contentTranslation,
      fontScale = density.fontScale,
      reducedMotion = LocalFluidMotionPolicy.current.reducedMotion,
      onBack = onBack,
      actions = actions,
      glassIntensity = collapse.glassIntensity,
      onTapTitle = {
        if (onTapTitle != null) {
          onTapTitle()
        } else {
          scope.launch { collapse.listState.animateScrollToItem(0) }
        }
      },
    )
  }
}

/**
 * A fling that always finishes the handover.
 *
 * The title has two homes and no address in between: a gesture allowed to stop halfway leaves the
 * type frozen at an arbitrary size over the bar, which is the most obviously unfinished state a
 * screen can be left in. Pass this as the list's `flingBehavior`.
 */
@Composable
fun rememberFluidTitleSnapFling(collapse: FluidTitleCollapse): FlingBehavior {
  val platform = ScrollableDefaults.flingBehavior()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  return remember(platform, collapse, reducedMotion) {
    FluidTitleSnapFlingBehavior(
      delegate = platform,
      animated = !reducedMotion,
      snapDeltaPx = { titleSnapDeltaPx(collapse.listState, collapse.morph) },
    )
  }
}
