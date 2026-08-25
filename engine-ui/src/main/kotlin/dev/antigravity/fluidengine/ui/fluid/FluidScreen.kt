package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material3.LocalContentColor

/**
 * Coordinates chrome that lives above the navigation host without sharing render layers between
 * destinations.
 *
 * Every [FluidScreen] owns its own [GlassBackdropState]. Screens register that state while composed;
 * the most recently registered destination becomes the source sampled by the floating tab bar. A
 * predictive-back cancellation simply disposes the briefly revealed destination and restores the
 * previous registration, so no two screens ever write into the same graphics-layer instances.
 */
@Stable
class FluidChromeController internal constructor(
  private val bottomBarVelocityThresholdPx: Float,
  private val densityPx: Float,
) {
  private val backdrops = LinkedHashMap<Any, GlassBackdropState>()
  private val _activeBackdrop = mutableStateOf<GlassBackdropState?>(null)
  private val _bottomBarOffsetPx = mutableFloatStateOf(0f)
  private var bottomBarTravelPx = 0f
  private var pendingScrollPx = 0f

  val activeBackdrop: State<GlassBackdropState?> = _activeBackdrop
  val bottomBarOffsetPx: State<Float> = _bottomBarOffsetPx

  internal fun registerBackdrop(key: Any, backdrop: GlassBackdropState) {
    backdrops.remove(key)
    backdrops[key] = backdrop
    _activeBackdrop.value = backdrop
    revealBottomBar()
  }

  internal fun unregisterBackdrop(key: Any) {
    val removed = backdrops.remove(key) ?: return
    if (_activeBackdrop.value === removed) {
      _activeBackdrop.value = backdrops.entries.lastOrNull()?.value
    }
  }

  fun updateBottomBarTravel(travelPx: Float) {
    bottomBarTravelPx = travelPx.coerceAtLeast(0f)
    _bottomBarOffsetPx.floatValue = _bottomBarOffsetPx.floatValue
      .coerceIn(0f, bottomBarTravelPx)
  }

  fun onBottomBarScroll(availableY: Float) {
    if (bottomBarTravelPx <= 0f || availableY == 0f) return
    // A gesture has to commit to a direction before the bar answers it. Without this the bar
    // tracked every wobble of a fast, direction-changing scroll one pixel at a time, which is what
    // made it flicker in and out instead of reading as a deliberate response.
    if (pendingScrollPx != 0f && sign(pendingScrollPx) != sign(availableY)) pendingScrollPx = 0f
    pendingScrollPx += availableY
    val threshold = bottomBarCommitThresholdPx(
      hidingTravel = pendingScrollPx < 0f,
      density = densityPx,
    )
    if (abs(pendingScrollPx) < threshold) return
    val effective = pendingScrollPx - sign(pendingScrollPx) * threshold
    pendingScrollPx = sign(pendingScrollPx) * threshold
    _bottomBarOffsetPx.floatValue = calculateBottomBarOffset(
      currentOffsetPx = _bottomBarOffsetPx.floatValue,
      availableY = effective,
      travelPx = bottomBarTravelPx,
    )
  }

  fun revealBottomBar() {
    pendingScrollPx = 0f
    _bottomBarOffsetPx.floatValue = 0f
  }

  suspend fun settleBottomBar(velocityY: Float) {
    pendingScrollPx = 0f
    val start = _bottomBarOffsetPx.floatValue
    val target = calculateBottomBarSettleTarget(
      currentOffsetPx = start,
      travelPx = bottomBarTravelPx,
      velocityY = velocityY,
      velocityThresholdPx = bottomBarVelocityThresholdPx,
    )
    if (start == target) return
    animate(
      initialValue = start,
      targetValue = target,
      animationSpec = FluidMotion.snappy(),
    ) { value, _ ->
      _bottomBarOffsetPx.floatValue = value.coerceIn(0f, bottomBarTravelPx)
    }
  }
}

@Composable
fun rememberFluidChromeController(): FluidChromeController {
  val density = LocalDensity.current.density
  val velocityThresholdPx = bottomBarVelocityThresholdPx(density)
  return remember(velocityThresholdPx, density) {
    FluidChromeController(
      bottomBarVelocityThresholdPx = velocityThresholdPx,
      densityPx = density,
    )
  }
}

@Composable
fun rememberFluidChromeScrollConnection(
  controller: FluidChromeController,
  enabled: Boolean,
): NestedScrollConnection = remember(controller, enabled) {
  object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (enabled) controller.onBottomBarScroll(available.y)
      return Offset.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      if (enabled) controller.settleBottomBar(consumed.y + available.y)
      return Velocity.Zero
    }
  }
}

internal fun calculateBottomBarOffset(
  currentOffsetPx: Float,
  availableY: Float,
  travelPx: Float,
): Float {
  if (travelPx <= 0f) return 0f
  // Nested-scroll Y is negative while content moves towards the end of the list.
  return (currentOffsetPx - availableY).coerceIn(0f, travelPx)
}

internal fun calculateBottomBarSettleTarget(
  currentOffsetPx: Float,
  travelPx: Float,
  velocityY: Float,
  velocityThresholdPx: Float,
): Float {
  if (travelPx <= 0f) return 0f
  val threshold = velocityThresholdPx.coerceAtLeast(0f)
  return when {
    velocityY < -threshold -> travelPx
    velocityY > threshold -> 0f
    currentOffsetPx >= travelPx * 0.5f -> travelPx
    else -> 0f
  }
}

internal fun bottomBarVelocityThresholdPx(density: Float): Float =
  BottomBarVelocityThresholdDpPerSecond * density.coerceAtLeast(0f)

/**
 * How far a scroll must travel in one direction before the bar answers it.
 *
 * Asymmetric on purpose: getting the bar out of the way is a considered response to a deliberate
 * downward read, while getting it back is what someone does when they want to leave — so revealing
 * commits almost immediately and hiding takes real intent.
 */
internal fun bottomBarCommitThresholdPx(hidingTravel: Boolean, density: Float): Float {
  val dp = if (hidingTravel) BottomBarHideCommitDp else BottomBarRevealCommitDp
  return dp * density.coerceAtLeast(0f)
}

private const val BottomBarVelocityThresholdDpPerSecond = 200f
private const val BottomBarHideCommitDp = 36f
private const val BottomBarRevealCommitDp = 10f

val LocalFluidChromeController: ProvidableCompositionLocal<FluidChromeController?> =
  staticCompositionLocalOf { null }

/**
 * Lets the shell ask whichever screen is on top to return to its top.
 *
 * Re-tapping the active tab is the gesture people reach for when they want to get back to the start
 * of a long list, and its absence is felt as the list being "stuck" at the bottom.
 */
@Stable
class FluidScrollToTopBus {
  internal val signal = mutableIntStateOf(0)

  fun request() {
    signal.intValue++
  }
}

val LocalFluidScrollToTop: ProvidableCompositionLocal<FluidScrollToTopBus?> =
  staticCompositionLocalOf { null }

/**
 * Room a screen must leave at the bottom for the floating tab bar. Provided by the app shell so no
 * screen has to know whether it is currently hosted under one.
 */
val LocalFluidBottomInset: ProvidableCompositionLocal<Dp> = compositionLocalOf { 0.dp }

object FluidScreenDefaults {
  /** Height of the control row of the bar, matching the 44pt UIKit navigation bar. */
  val ControlRowHeight: Dp = 44.dp

  /** Initial travel kept optically clear, so a tiny touch does not flash the material on. */
  val ShieldDeadZone: Dp = 8.dp

  /** Travel after the dead zone over which the top material reaches full strength. */
  val ShieldRampDistance: Dp = 64.dp

  /** Soft tail below the 44 dp control row; it removes the hard edge of a rectangular top bar. */
  val GlassFadeTail: Dp = 34.dp

  /**
   * Room the docked title leaves on each side for bar buttons. Wider than a bar button because the
   * compact title is now large enough that a near miss reads as a collision.
   */
  val CompactTitleInset: Dp = 76.dp

  val HorizontalPadding: Dp = 20.dp
  val ItemSpacing: Dp = 14.dp
  val TitleTopSpacing: Dp = 4.dp

  /** How far the top edge must be pulled open before releasing it starts a refresh. */
  val RefreshTrigger: Dp = 76.dp

  /** How much of that stays open while the refresh runs, holding the indicator in view. */
  val RefreshHold: Dp = 56.dp
  val TitleBottomSpacing: Dp = 10.dp
  val ContentBottomSpacing: Dp = 28.dp

  /** Total height of the top chrome, including the status bar it sits under. */
  @Composable
  fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + ControlRowHeight

  /** Bottom padding a scrolling body needs so its last item clears the system and tab bars. */
  @Composable
  fun bottomContentPadding(extra: Dp = 0.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
      LocalFluidBottomInset.current + extra + ContentBottomSpacing
}

private const val TitleItemKey = "fluid:large-title"

private val RefreshIndicatorSize: Dp = 24.dp

/**
 * Geometry shared by the large title and the compact bar, expressed so that nothing it exposes
 * changes while the list is being scrolled.
 *
 * The large title's anchor is *computed*, not measured: at rest the title item sits exactly at the
 * list's top content padding, so its baseline is that padding plus the text's own first baseline.
 * Measuring it instead would read a position that already includes the elastic edge's transform —
 * the anchor would then move with the very translation the morph adds on top of it, and the title
 * would travel at twice the speed of the content it belongs to.
 *
 * Only the compact anchor is measured, and only from an untransformed slot in the bar.
 *
 * Both anchors live in the **screen's own coordinates**, not the window's. The computed one is
 * screen-local by construction (it is made of the screen's paddings), so the measured one must be
 * converted into the same space — read it in window coordinates and any offset the app puts around
 * the screen becomes a translation error. That is not hypothetical: a tablet insets every page by
 * the width of the navigation rail, and the mismatch put the resting title a rail's width to the
 * left of its page, underneath the rail.
 */
@Stable
private class FluidTitleMorphState(
  val expandedLeftInScreen: Float,
  private val expandedTopInScreen: Float,
) {
  var restingBaselineInScreen by mutableFloatStateOf(Float.NaN)
    private set
  var compactLeftInScreen by mutableFloatStateOf(Float.NaN)
    private set
  var compactBaselineInScreen by mutableFloatStateOf(Float.NaN)
    private set

  /** Where the baseline sits inside the compact text node; the pivot of the whole transform. */
  var compactLocalBaseline by mutableFloatStateOf(Float.NaN)
    private set
  var expandedLineCount by mutableIntStateOf(1)
    private set
  var compactHasVisualOverflow by mutableStateOf(false)
    private set

  private var screenCoordinates: LayoutCoordinates? = null
  private var compactSlotCoordinates: LayoutCoordinates? = null
  private var compactTextSize = IntSize.Zero

  /** Distance the title travels between its two resting places. NaN until both are measured. */
  val travelPx: Float
    get() {
      val resting = restingBaselineInScreen
      val compact = compactBaselineInScreen
      if (!resting.isFinite() || !compact.isFinite()) return Float.NaN
      return resting - compact
    }

  val isMeasured: Boolean
    get() = travelPx.isFinite() &&
      expandedLeftInScreen.isFinite() &&
      compactLeftInScreen.isFinite() &&
      compactLocalBaseline.isFinite()

  fun onExpandedTextLayout(result: TextLayoutResult) {
    expandedLineCount = result.lineCount
    restingBaselineInScreen = expandedTopInScreen + result.firstBaseline
  }

  fun onCompactTextLayout(result: TextLayoutResult) {
    compactLocalBaseline = result.firstBaseline
    compactTextSize = result.size
    compactHasVisualOverflow = result.hasVisualOverflow
    rebuildCompact()
  }

  fun onScreenPositioned(coordinates: LayoutCoordinates) {
    screenCoordinates = coordinates
    rebuildCompact()
  }

  fun onCompactSlotPositioned(coordinates: LayoutCoordinates) {
    compactSlotCoordinates = coordinates
    rebuildCompact()
  }

  private fun rebuildCompact() {
    val coordinates = compactSlotCoordinates ?: return
    val screen = screenCoordinates ?: return
    if (!coordinates.isAttached || !screen.isAttached || compactTextSize == IntSize.Zero) return
    if (!compactLocalBaseline.isFinite()) return
    // Measured from the untransformed slot, never from the text node itself: the text carries the
    // morph's own graphics layer, so its position would include the transform being solved. And
    // converted into the screen's coordinates, so whatever the app has put around the screen — a
    // navigation rail's inset, a split-screen pane — cancels out instead of becoming a translation.
    val slotTopLeft = screen.localPositionOf(coordinates, Offset.Zero)
    val slotSize = coordinates.size
    compactLeftInScreen = slotTopLeft.x + (slotSize.width - compactTextSize.width) / 2f
    compactBaselineInScreen =
      slotTopLeft.y + (slotSize.height - compactTextSize.height) / 2f + compactLocalBaseline
  }
}

/**
 * The compact title is the large title scaled down — same family, same weight, proportionally
 * scaled tracking — so that turning one into the other is a pure geometric transform. Two different
 * type styles cross-fading is what read as the title "changing font" partway through the scroll.
 *
 * At 0.62 the docked title lands near 21sp rather than the 17sp a navigation bar conventionally
 * uses. A bar title that is *only* legible reads as a caption for the page; this one still reads as
 * the page's name.
 */
private const val CompactTitleScale = 0.62f

/** How long each dynamic facet holds the bar before the next one takes over. */
private const val TitleFacetDwellMillis = 2_400L

/**
 * How far into the title's travel the subtitle has to be gone.
 *
 * Late, because it is no longer running from anything: the title travels at exactly the speed of the
 * page now, so the two can never meet. This is only tidiness — a second line arriving under a docked
 * title, inside the soft tail of the glass, is more material than that corner can carry.
 */
private const val SubtitleFadeFraction = 0.72f

/** A facet enters from below and the outgoing one leaves upwards: a single wheel, never a swap. */
private const val TitleFacetShiftFraction = 0.55f

@Composable
private fun rememberCompactTitleStyle(expanded: TextStyle): TextStyle = remember(expanded) {
  expanded.copy(
    fontSize = expanded.fontSize.scaleBy(CompactTitleScale),
    lineHeight = expanded.lineHeight.scaleBy(CompactTitleScale),
    letterSpacing = expanded.letterSpacing.scaleBy(CompactTitleScale),
  )
}

private fun TextUnit.scaleBy(factor: Float): TextUnit = if (isSpecified) this * factor else this

/**
 * An edge-to-edge screen in the shape Apple gives its own: content runs the full height of the
 * display, a large title sits at the top of the scroll as ordinary content, and a compact bar of
 * frosted glass materialises over it only once the title has scrolled underneath.
 *
 * Two things follow from the title being a list item rather than a collapsing app bar, and both are
 * the point of the design:
 *
 *  * There is no feedback loop between the bar's height and the list's content padding. A collapsing
 *    `LargeTopAppBar` changes the `Scaffold`'s reported padding as it collapses, so the content
 *    shifts on top of scrolling — the jitter that reads as "the animation is wrong".
 *  * Scrolling to the top always reaches the top. Nothing has to be dragged back open afterwards.
 */
@Composable
fun FluidScreen(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  onBack: (() -> Unit)? = null,
  actions: @Composable RowScope.() -> Unit = {},
  /**
   * Short facts the bar cycles through once the title has docked — a month, a count, a state.
   * The page's name is always the first thing shown and always what it returns to, so the bar can
   * never be caught saying something that does not identify the screen you are on.
   */
  titleFacets: List<String> = emptyList(),
  listState: LazyListState = rememberLazyListState(),
  isRefreshing: Boolean = false,
  onRefresh: (() -> Unit)? = null,
  horizontalPadding: Dp = FluidScreenDefaults.HorizontalPadding,
  itemSpacing: Dp = FluidScreenDefaults.ItemSpacing,
  extraBottomPadding: Dp = 0.dp,
  /**
   * The page's own backdrop: a colour wash and a motif, painted under everything.
   *
   * Additive, and null is exactly the screen this has always drawn. When it is set, three things
   * change at once and they only make sense together:
   *
   *  * the canvas is painted and recorded *before* the body, into a second layer of its own;
   *  * the body stops painting an opaque background, so the canvas shows through it;
   *  * the chrome refracts the two stacked — canvas behind, body in front — which is still an opaque
   *    image, so nothing about route transitions or their tests changes.
   *
   * What it buys is [LocalFluidCanvasBackdrop] for the whole subtree, and with it the ability for a
   * card *inside* the list to be glass without sampling a recording that contains itself.
   */
  ambient: FluidAmbient? = null,
  /**
   * Chrome that must sample this screen without becoming part of its recorded body. Use this for
   * floating indexes, contextual controls and other true overlays; ordinary page content belongs
   * in [content] and must stay solid.
   */
  overlay: @Composable BoxScope.(GlassBackdropState) -> Unit = {},
  content: LazyListScope.() -> Unit,
) {
  // A render layer may have only one writer. Keeping this state local is what makes overlapping
  // NavHost destinations (including predictive back) safe: each screen records into its own layers.
  val bodyBackdrop = rememberGlassBackdrop()
  // Only allocated when there is actually a canvas. Holding the layer unconditionally was simpler
  // and cost every screen in the family a spare `RenderNode` it would never draw into — which is
  // exactly the kind of "it is only one" that adds up to a tab switch you can see. The `key` is what
  // makes the conditional `remember` safe: turning a canvas on or off discards this slot rather than
  // shifting the composition's shape under it.
  val canvasBackdrop = key(ambient != null) {
    if (ambient == null) null else rememberGlassBackdrop()
  }
  // What the *chrome* refracts. With a canvas that has to be both layers, because the body alone is
  // now transparent everywhere the content is not — and a bar blurring transparency produces a smear
  // the sharp original still shows through, which is the artefact the recording exists to avoid.
  val backdrop = key(canvasBackdrop != null) {
    if (canvasBackdrop == null) {
      bodyBackdrop
    } else {
      rememberCombinedGlassBackdrop(canvasBackdrop, bodyBackdrop)
    }
  }
  val chromeController = LocalFluidChromeController.current
  val chromeRegistration = remember { Any() }
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val expandedTitleStyle = MaterialTheme.typography.displayLarge
  val compactTitleStyle = rememberCompactTitleStyle(expandedTitleStyle)

  val topBarHeight = FluidScreenDefaults.topBarHeight()
  val bottomPadding = FluidScreenDefaults.bottomContentPadding(extraBottomPadding)
  val titleLeftPx = with(density) { horizontalPadding.toPx() }
  val titleTopPx = with(density) { (topBarHeight + FluidScreenDefaults.TitleTopSpacing).toPx() }
  val titleMorphState = remember(title, density.fontScale, titleLeftPx, titleTopPx) {
    FluidTitleMorphState(expandedLeftInScreen = titleLeftPx, expandedTopInScreen = titleTopPx)
  }

  DisposableEffect(chromeController, chromeRegistration, backdrop) {
    chromeController?.registerBackdrop(chromeRegistration, backdrop)
    onDispose { chromeController?.unregisterBackdrop(chromeRegistration) }
  }

  LaunchedEffect(chromeController, listState) {
    if (chromeController == null) return@LaunchedEffect
    // Whether the gesture is still running is part of the signal, not noise. The bar settles to
    // hidden or shown from inside the fling, so a scroll that *ends* at the top — which is what the
    // title's snap-back produces — needs a second look once the gesture is over. Watching only the
    // position left the bar parked off-screen while the list was demonstrably at its start.
    snapshotFlow {
      val isAtTop = listState.firstVisibleItemIndex == 0 &&
        listState.firstVisibleItemScrollOffset == 0
      isAtTop to listState.isScrollInProgress
    }
      .distinctUntilChanged()
      .collect { (isAtTop, _) ->
        if (isAtTop) chromeController.revealBottomBar()
      }
  }

  LocalFluidScrollToTop.current?.let { bus ->
    LaunchedEffect(bus, listState) {
      snapshotFlow { bus.signal.intValue }
        .drop(1)
        .collect { listState.animateScrollToItem(0) }
    }
  }

  val scrollProgress = rememberCollapseProgress(
    listState = listState,
    titleMorphState = titleMorphState,
  )

  // The morph is welded to the scroll, and that is the whole of its correctness.
  //
  // It used to be filtered through a spring so that a fling could not cross the handoff in two
  // frames. What that actually bought was a title moving *more slowly than the page it is part of*:
  // the heading is the first item of the list, so anything below it — the subtitle, the first card —
  // kept its own speed, caught up, and passed straight through it. Given a choice between a title
  // that always takes a readable time to travel and a title that is never in the wrong place, only
  // one of them is a title. The minimum time now comes from where it belongs: the fling snaps the
  // scroll itself to one end of the handoff or the other, so the movement is always completed, is
  // always animated when a gesture leaves it partway, and never rests in between.
  val collapseProgress = scrollProgress
  val activeFacet = rememberTitleFacet(titleFacets, collapseProgress)

  // The title has two homes and no address in between. Letting a gesture stop halfway leaves the
  // type frozen at an arbitrary size over the bar, which is the most obviously unfinished state the
  // screen can be left in, so every fling ends by settling the scroll onto one of the two.
  val platformFling = ScrollableDefaults.flingBehavior()
  val flingBehavior = remember(platformFling, listState, titleMorphState, reducedMotion) {
    FluidTitleSnapFlingBehavior(
      delegate = platformFling,
      animated = !reducedMotion,
      snapDeltaPx = { titleSnapDeltaPx(listState, titleMorphState) },
    )
  }

  // The material appears as soon as anything passes under the status bar, not when the title has
  // finished leaving. Waiting for the title means a stretch of scrolling where text slides under the
  // clock with nothing behind it — the moment that makes an edge-to-edge layout look unfinished
  // rather than deliberate.
  val shieldDeadZonePx = with(density) { FluidScreenDefaults.ShieldDeadZone.toPx() }
  val shieldRampDistancePx = with(density) { FluidScreenDefaults.ShieldRampDistance.toPx() }
  val glassIntensity = remember(
    listState,
    collapseProgress,
    shieldDeadZonePx,
    shieldRampDistancePx,
  ) {
    derivedStateOf {
      calculateGlassIntensity(
        firstVisibleItemIndex = listState.firstVisibleItemIndex,
        firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
        collapseProgress = collapseProgress.value,
        deadZonePx = shieldDeadZonePx,
        rampDistancePx = shieldRampDistancePx,
      )
    }
  }

  val overscroll = remember(reducedMotion, listState) {
    FluidEdgeOverscrollState(
      reducedMotion = reducedMotion,
      canScroll = { listState.canScrollForward || listState.canScrollBackward },
    )
  }
  val refreshTriggerPx = with(density) { FluidScreenDefaults.RefreshTrigger.toPx() }
  val refreshHoldPx = with(density) { FluidScreenDefaults.RefreshHold.toPx() }
  // The gesture and the work it starts are separate facts: the edge only knows how far to open and
  // what to call, and closes again when the caller says the work is done.
  SideEffect {
    overscroll.refreshTriggerPx = if (onRefresh != null) refreshTriggerPx else 0f
    overscroll.refreshHoldPx = refreshHoldPx
    overscroll.onRefresh = onRefresh
  }
  LaunchedEffect(overscroll, isRefreshing) {
    if (!isRefreshing) overscroll.endRefresh()
  }

  // Chi dipinge il fondo dichiara anche il colore di quello che ci va sopra, **barra compresa**.
  //
  // Senza, il testo eredita LocalContentColor, che fuori da un Surface vale nero: un'app che non
  // avvolge tutto l'albero in un Surface si ritrova il titolo grande nero su fondo nero. Il primo
  // tentativo aveva avvolto solo la lista, e il titolo restava nero lo stesso: quello che si vede
  // durante il morph e' la copia della **barra**, trasformata sull'ancora del titolo grande.
  // Il vetro si assottiglia mentre la pagina corre e torna intero quando si ferma: vedi
  // [FluidGlassQuality]. Sta qui e non nelle singole superfici perché la velocità e' un fatto
  // della *pagina*, e perché cosi' ogni schermata dell'app lo eredita senza aggiungere una riga.
  val glassQuality = rememberFluidGlassQuality()
  val qualityConnection = remember(glassQuality) {
    fluidGlassQualityScrollConnection(glassQuality)
  }

  CompositionLocalProvider(
    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    LocalFluidCanvasBackdrop provides canvasBackdrop,
    LocalFluidGlassQuality provides glassQuality,
  ) {
  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      // The origin of the title morph's coordinate space. See [FluidTitleMorphState]: anchors are
      // screen-local, so the compact slot's measurement is converted against these coordinates.
      .onGloballyPositioned(titleMorphState::onScreenPositioned),
  ) {
    val contentTranslation: () -> Float = { overscroll.offsetPx }

    // The canvas, and its recording, come first — which is the whole of why a card in the list can
    // be glass. This layer is closed before a single item of the body is composed, so it is
    // structurally incapable of containing one.
    if (ambient != null && canvasBackdrop != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .glassBackdropSource(canvasBackdrop),
      ) {
        FluidAmbientCanvas(ambient)
      }
    }

    val body: @Composable () -> Unit = {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .nestedScroll(overscroll)
          .nestedScroll(qualityConnection)
          .onSizeChanged { overscroll.updateViewport(it.height.toFloat()) }
          // The background is painted *inside* the recorded region, not behind it. A snapshot of
          // text on transparency blurs into a faint smear that the sharp original still shows
          // through; the glass has to sample an opaque image to actually hide what is under it.
          .glassBackdropSource(bodyBackdrop)
          // Opaque ground first, elastic content second. Painting the background *outside* the
          // bouncing layer is what stops a bounce from dragging a transparent band under the bar,
          // and keeps the recorded snapshot showing exactly what the glass is meant to be hiding.
          //
          // With a canvas the opacity moves down a layer instead of disappearing: the canvas is
          // itself opaque and is recorded behind this one, so the pair is still a solid image and
          // the body is free to let it through.
          .then(
            if (ambient != null) {
              Modifier
            } else {
              Modifier.background(MaterialTheme.colorScheme.background)
            },
          )
          .graphicsLayer { translationY = contentTranslation() },
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(
          start = horizontalPadding,
          end = horizontalPadding,
          top = topBarHeight,
          bottom = bottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        overscrollEffect = null,
      ) {
        item(key = TitleItemKey, contentType = TitleItemKey) {
          FluidLargeTitle(
            title = title,
            subtitle = subtitle,
            style = expandedTitleStyle,
            collapseProgress = collapseProgress,
            morphState = titleMorphState,
            fontScale = density.fontScale,
            reducedMotion = reducedMotion,
          )
        }
        content()
      }
    }

    body()

    if (onRefresh != null) {
      // The indicator lives in the space the pull opens, halfway down it, so it arrives *with* the
      // content rather than sliding over the top of it. No floating disc: iOS reveals the spinner's
      // spokes as the finger pulls, in place, with nothing behind it.
      FluidSpinner(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .padding(top = topBarHeight)
          .graphicsLayer {
            val opened = overscroll.offsetPx.coerceAtLeast(0f)
            val pull = overscroll.refreshPull
            alpha = if (isRefreshing) 1f else smoothStep((pull / 0.55f).coerceIn(0f, 1f))
            translationY = opened * 0.5f - RefreshIndicatorSize.toPx() * 0.5f
          },
        size = RefreshIndicatorSize,
        progress = if (isRefreshing) null else {
          { overscroll.refreshPull }
        },
      )
    }

    FluidTopBar(
      title = title,
      backdrop = backdrop,
      collapseProgress = collapseProgress,
      activeFacet = activeFacet,
      morphState = titleMorphState,
      compactStyle = compactTitleStyle,
      contentTranslation = contentTranslation,
      fontScale = density.fontScale,
      reducedMotion = reducedMotion,
      onBack = onBack,
      actions = actions,
      glassIntensity = glassIntensity,
      onTapTitle = {
        scope.launch { listState.animateScrollToItem(0) }
      },
    )

    overlay(backdrop)
  }
  }
}

/**
 * How far the compact bar has materialised, from 0 (large title fully clear of it) to 1.
 *
 * Derived from the title item's own measured position rather than from a raw scroll offset, so it
 * stays correct whatever the title's height turns out to be — one line, two lines, or with a
 * subtitle.
 */
@Composable
private fun rememberCollapseProgress(
  listState: LazyListState,
  titleMorphState: FluidTitleMorphState,
): State<Float> = remember(listState, titleMorphState) {
  derivedStateOf {
    // Before LazyColumn's first layout there is no evidence that the title has collapsed. Reporting
    // one here made the compact title and full-strength blur flash over every entering screen.
    if (listState.layoutInfo.visibleItemsInfo.isEmpty()) return@derivedStateOf 0f
    if (!titleMorphState.isMeasured) {
      return@derivedStateOf if (listState.firstVisibleItemIndex > 0) 1f else 0f
    }
    titleCollapseProgress(
      scrolledPx = listState.titleScrollOffsetPx(),
      travelPx = titleMorphState.travelPx,
    )
  }
}

/**
 * How far the list has moved away from the top, in pixels, or [Float.POSITIVE_INFINITY] once the
 * title item itself is no longer the anchor.
 */
private fun LazyListState.titleScrollOffsetPx(): Float =
  if (firstVisibleItemIndex > 0) Float.POSITIVE_INFINITY else firstVisibleItemScrollOffset.toFloat()

/**
 * Which fact the docked bar is currently showing, or null while it is showing the page's name.
 *
 * Cycling only ever happens while the title is docked, and returning to the top resets it, so the
 * expanded state of every screen is unconditionally its own title.
 */
@Composable
private fun rememberTitleFacet(
  facets: List<String>,
  collapseProgress: State<Float>,
): State<String?> {
  val active = remember { mutableStateOf<String?>(null) }
  val docked = remember(collapseProgress) {
    derivedStateOf { collapseProgress.value >= 0.995f }
  }
  LaunchedEffect(facets, docked, active) {
    if (facets.isEmpty()) {
      active.value = null
      return@LaunchedEffect
    }
    snapshotFlow { docked.value }.collectLatest { isDocked ->
      active.value = null
      if (!isDocked) return@collectLatest
      var index = 0
      while (true) {
        delay(TitleFacetDwellMillis)
        index = (index + 1) % (facets.size + 1)
        active.value = facets.getOrNull(index - 1)
      }
    }
  }
  return active
}

/** How far the list still has to move for the title to be all the way to one side or the other. */
private fun titleSnapDeltaPx(
  listState: LazyListState,
  morphState: FluidTitleMorphState,
): Float {
  val travel = morphState.travelPx
  if (!travel.isFinite() || travel <= 1f) return 0f
  if (listState.firstVisibleItemIndex != 0) return 0f
  val scrolled = listState.firstVisibleItemScrollOffset.toFloat()
  if (scrolled <= 0f || scrolled >= travel) return 0f
  return titleSnapTarget(scrolled, travel) - scrolled
}

internal fun calculateGlassIntensity(
  firstVisibleItemIndex: Int,
  firstVisibleItemScrollOffset: Int,
  collapseProgress: Float,
  deadZonePx: Float,
  rampDistancePx: Float,
): Float {
  val scrollProgress = if (firstVisibleItemIndex > 0) {
    1f
  } else {
    val ramp = rampDistancePx.coerceAtLeast(1f)
    ((firstVisibleItemScrollOffset - deadZonePx) / ramp).coerceIn(0f, 1f)
  }
  // The title starts moving on the first pixel of scroll, so taking its progress raw made the
  // material begin to appear on the first pixel too. The opening of the handoff is discounted, so
  // the blur starts a little further into the gesture and the top of the page stays clear while the
  // page has barely moved.
  val fromTitle = ((collapseProgress.coerceIn(0f, 1f) - GlassCollapseDeadZone) /
    (1f - GlassCollapseDeadZone)).coerceIn(0f, 1f)
  return maxOf(smoothStep(fromTitle), smoothStep(scrollProgress))
}

/** Fraction of the title's travel that raises no material at all. */
internal const val GlassCollapseDeadZone = 0.16f

internal fun smoothStep(value: Float): Float {
  val clamped = value.coerceIn(0f, 1f)
  return clamped * clamped * (3f - 2f * clamped)
}

@Composable
private fun FluidLargeTitle(
  title: String,
  subtitle: String?,
  style: TextStyle,
  collapseProgress: State<Float>,
  morphState: FluidTitleMorphState,
  fontScale: Float,
  reducedMotion: Boolean,
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .padding(
        top = FluidScreenDefaults.TitleTopSpacing,
        bottom = FluidScreenDefaults.TitleBottomSpacing,
      ),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = title,
      modifier = Modifier
        .fillMaxWidth()
        .graphicsLayer {
          val morphing = shouldMorphTitle(
            fontScale = fontScale,
            expandedLineCount = morphState.expandedLineCount,
            reducedMotion = reducedMotion,
          ) && !morphState.compactHasVisualOverflow && morphState.isMeasured
          // While morphing, this copy only reserves the space and carries the heading semantics;
          // the single visible title is the bar's, transformed onto this exact anchor.
          alpha = expandedTitleAlpha(
            progress = collapseProgress.value,
            morphing = morphing,
            reducedMotion = reducedMotion,
          )
        },
      // 34sp Bold: the iOS navigation large title, exactly. Its negative tracking is what stops
      // a heading this size from looking like a banner.
      style = style,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      onTextLayout = morphState::onExpandedTextLayout,
    )
    subtitle?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        modifier = Modifier.graphicsLayer {
          alpha = 1f - smoothStep(
            (collapseProgress.value / SubtitleFadeFraction).coerceIn(0f, 1f),
          )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun FluidTopBar(
  title: String,
  backdrop: GlassBackdropState,
  collapseProgress: State<Float>,
  activeFacet: State<String?>,
  morphState: FluidTitleMorphState,
  compactStyle: TextStyle,
  contentTranslation: () -> Float,
  fontScale: Float,
  reducedMotion: Boolean,
  glassIntensity: State<Float>,
  onBack: (() -> Unit)?,
  actions: @Composable RowScope.() -> Unit,
  onTapTitle: () -> Unit,
) {
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val tint = GlassDefaults.barTint()
  val interactionSource = remember { MutableInteractionSource() }
  // The bar publishes its own finished material so the actions standing on it refract *it* rather
  // than the page three layers down — a lens resting on frosted glass shows the frosting.
  val barGlass = rememberGlassBackdrop()
  val controlBackdrop = rememberCombinedGlassBackdrop(backdrop, barGlass)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.GlassFadeTail),
  ) {
    // Clip only the sampled material. The title itself is allowed to travel below this drawing
    // region while it is still anchored to the large heading; clipping the whole bar cut the lower
    // half of the glyphs during the first third of the morph.
    Box(
      modifier = Modifier
        .fillMaxSize()
        .glassSurface(
        state = backdrop,
        tint = tint,
        edge = GlassEdge.None,
        falloff = GlassFalloff.FadeDown,
        intensity = { glassIntensity.value },
        exports = barGlass,
        ),
    )
    CompositionLocalProvider(LocalGlassBackdrop provides controlBackdrop) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .height(statusBar + FluidScreenDefaults.ControlRowHeight),
    ) {
      Spacer(Modifier.height(statusBar))
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(FluidScreenDefaults.ControlRowHeight)
        // Tap-the-bar-to-scroll-up covers the control row only and is exposed as a named action,
        // never as the anonymous unchecked selection control that `selectable` would create.
        .clickable(
          interactionSource = interactionSource,
          indication = null,
          role = Role.Button,
          onClick = onTapTitle,
        )
        .semantics { contentDescription = "Torna all'inizio" },
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = FluidScreenDefaults.CompactTitleInset)
          .onGloballyPositioned(morphState::onCompactSlotPositioned),
        contentAlignment = Alignment.Center,
      ) {
        // One wheel, not two labels taking turns: the title rides up and out as the fact rides in,
        // so the bar always looks like a single surface showing successive things.
        val facetVisibility = animateFloatAsState(
          targetValue = if (activeFacet.value != null) 1f else 0f,
          animationSpec = if (reducedMotion) snap() else FluidMotion.crossFade(220),
          label = "title facet handoff",
        )
        Text(
          text = title,
          modifier = Modifier.graphicsLayer {
            val progress = collapseProgress.value.coerceIn(0f, 1f)
            val morphing = shouldMorphTitle(
              fontScale = fontScale,
              expandedLineCount = morphState.expandedLineCount,
              reducedMotion = reducedMotion,
            ) && !morphState.compactHasVisualOverflow && morphState.isMeasured
            val facetHandoff = facetVisibility.value
            alpha = compactTitleAlpha(
              progress = progress,
              morphing = morphing,
              reducedMotion = reducedMotion,
            ) * (1f - facetHandoff)
            val facetShiftPx = -compactStyle.fontSize.toPx() * TitleFacetShiftFraction * facetHandoff
            if (!morphing) {
              transformOrigin = TransformOrigin.Center
              scaleX = 1f
              scaleY = 1f
              translationX = 0f
              translationY = facetShiftPx
              return@graphicsLayer
            }

            val travel = morphState.travelPx
            val compactLeft = morphState.compactLeftInScreen
            val compactBaseline = morphState.compactBaselineInScreen
            val localBaseline = morphState.compactLocalBaseline

            // Size and horizontal glide are eased; the vertical position is not. Tying the
            // baseline directly to the scroll is what keeps the title welded to the content it
            // belongs to — an eased vertical would visibly lag the list it is sitting in.
            val eased = smoothStep(progress)
            val scale = lerp(1f / CompactTitleScale, 1f, eased)
            val desiredLeft = lerp(morphState.expandedLeftInScreen, compactLeft, eased)
            // Never above the docked position: the title is content, and content is allowed to
            // ride an elastic edge, but nothing is allowed to ride it into the status bar.
            val desiredBaseline = (
              compactBaseline + travel * (1f - progress) + contentTranslation() * (1f - eased)
              ).coerceAtLeast(compactBaseline)

            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = desiredLeft - compactLeft
            translationY = desiredBaseline - compactBaseline + localBaseline * (1f - scale) +
              facetShiftPx
          },
          style = compactStyle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          onTextLayout = morphState::onCompactTextLayout,
        )
        FluidTitleFacet(
          facet = activeFacet.value,
          style = compactStyle,
          reducedMotion = reducedMotion,
        )
      }
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          if (onBack != null) {
            FluidBackButton(onBack)
          } else {
            Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
          }
          Spacer(Modifier.weight(1f))
          Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
          )
          Spacer(Modifier.width(6.dp))
        }
      }
    }
    }
  }
}

/** The fact currently riding in the bar, arriving from below as the previous one leaves upwards. */
@Composable
private fun FluidTitleFacet(
  facet: String?,
  style: TextStyle,
  reducedMotion: Boolean,
) {
  AnimatedContent(
    targetState = facet,
    transitionSpec = {
      if (reducedMotion) {
        fadeIn(snap()).togetherWith(fadeOut(snap()))
      } else {
        (
          slideInVertically { height -> (height * TitleFacetShiftFraction).toInt() } +
            fadeIn(FluidMotion.fadeIn(200))
          ).togetherWith(
          slideOutVertically { height -> -(height * TitleFacetShiftFraction).toInt() } +
            fadeOut(FluidMotion.fadeOut(150)),
        )
      }
    },
    label = "title facet",
  ) { value ->
    if (value == null) {
      Spacer(Modifier.height(0.dp))
    } else {
      Text(
        text = value,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun FluidBackButton(onBack: () -> Unit) {
  FluidGlassIconButton(
    onClick = onBack,
    modifier = Modifier.padding(start = 6.dp),
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
      contentDescription = "Indietro",
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(20.dp),
    )
  }
}

/**
 * An action in the top bar. It is a draw-only lens over the bar's shared backdrop, so every action
 * gets a physical rim without paying for another blur pass.
 */
@Composable
fun FluidBarAction(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  FluidGlassIconButton(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(22.dp),
    )
  }
}

/** Provides the chrome registry, tab-bar allowance and scroll-to-top bus to every screen below. */
@Composable
fun ProvideFluidChrome(
  controller: FluidChromeController,
  bottomInset: Dp,
  scrollToTop: FluidScrollToTopBus,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalFluidChromeController provides controller,
    LocalFluidBottomInset provides bottomInset,
    LocalFluidScrollToTop provides scrollToTop,
    content = content,
  )
}
