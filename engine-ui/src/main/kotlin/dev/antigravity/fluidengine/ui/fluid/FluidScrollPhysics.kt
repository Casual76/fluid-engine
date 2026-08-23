package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * How much of the finger's travel the content still follows at the very start of an overscroll.
 * UIKit uses 0.55; anything higher stops reading as resistance, anything lower as a dead edge.
 */
internal const val OverscrollResistance = 0.55f

/** Hard ceiling on the bounce, as a fraction of the viewport. Keeps a violent fling on screen. */
internal const val MaximumBounceFraction = 0.24f

/** Velocity beyond this buys no extra travel: past it a fling is "as hard as it gets". */
internal const val MaximumBounceVelocityPxPerSecond = 5_500f

/**
 * UIKit's rubber band.
 *
 * `f(x) = x·d·c / (d + c·x)` — it starts by following the finger at [resistance] of its speed and
 * decays smoothly towards an asymptote at [dimensionPx], so there is never a point at which the
 * content stops dead or jumps. Deriving the asymptote from the viewport rather than from a fixed
 * dp value is what makes the give feel proportional to the surface being dragged.
 */
internal fun rubberBandTranslation(
  rawDistancePx: Float,
  dimensionPx: Float,
  resistance: Float = OverscrollResistance,
): Float {
  val dimension = dimensionPx.coerceAtLeast(0f)
  val raw = rawDistancePx.coerceAtLeast(0f)
  val c = resistance.coerceIn(0.01f, 1f)
  if (dimension <= 0f || raw <= 0f) return 0f
  return raw * dimension * c / (dimension + c * raw)
}

/** Inverse of [rubberBandTranslation]; recovers the raw drag that produced a visual offset. */
internal fun inverseRubberBandTranslation(
  visualDistancePx: Float,
  dimensionPx: Float,
  resistance: Float = OverscrollResistance,
): Float {
  val dimension = dimensionPx.coerceAtLeast(0f)
  val c = resistance.coerceIn(0.01f, 1f)
  val visual = visualDistancePx.coerceIn(0f, dimension)
  if (dimension <= 0f || visual <= 0f) return 0f
  if (visual >= dimension) return Float.MAX_VALUE
  return dimension * visual / (c * (dimension - visual))
}

/** Signed variants: a positive offset pushes content down (top edge), a negative one up (bottom). */
internal fun signedRubberBandTranslation(rawDistancePx: Float, dimensionPx: Float): Float =
  sign(rawDistancePx) * rubberBandTranslation(abs(rawDistancePx), dimensionPx)

internal fun signedInverseRubberBandTranslation(visualDistancePx: Float, dimensionPx: Float): Float =
  sign(visualDistancePx) * inverseRubberBandTranslation(abs(visualDistancePx), dimensionPx)

/**
 * How far the large title has travelled towards the compact bar, from 0 to 1.
 *
 * Derived from the list's own scroll offset rather than from re-measured text bounds. A measured
 * baseline is only correct on the frame that produced it: during a fast fling the title item is
 * recycled, its coordinates go stale, and the progress that drives the morph lags behind the
 * content by whole frames — which is exactly what a handoff that "changes font and does not line
 * up" looks like. The scroll offset, by contrast, is exact on every frame.
 */
internal fun titleCollapseProgress(scrolledPx: Float, travelPx: Float): Float {
  if (!travelPx.isFinite() || travelPx <= 1f) return if (scrolledPx > 0f) 1f else 0f
  return (scrolledPx / travelPx).coerceIn(0f, 1f)
}

/** Where the scroll must land so the title is never left stranded between its two positions. */
internal fun titleSnapTarget(scrolledPx: Float, travelPx: Float): Float =
  if (scrolledPx >= travelPx * 0.5f) travelPx else 0f

/**
 * Continues a settled fling into the nearer of the title's two resting places.
 *
 * It has to happen *inside* the fling. Watching the list go idle from an effect and then asking it
 * to scroll again means competing for the scroll session the gesture still owns: that second scroll
 * is refused outright, and the refusal arrives as a cancellation that quietly takes the watcher
 * down with it. Running here, the snap is simply the tail of the same gesture — a new touch takes
 * the session back and interrupts it, which is exactly the behaviour a finger should have.
 */
internal class FluidTitleSnapFlingBehavior(
  private val delegate: FlingBehavior,
  private val animated: Boolean,
  private val snapDeltaPx: () -> Float,
) : FlingBehavior {
  override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
    val remaining = with(delegate) { performFling(initialVelocity) }
    val delta = snapDeltaPx()
    if (!delta.isFinite() || abs(delta) < 0.5f) return remaining
    if (!animated) {
      scrollBy(delta)
      return 0f
    }
    var applied = 0f
    animate(
      initialValue = 0f,
      targetValue = delta,
      animationSpec = spring(
        dampingRatio = FluidMotion.DampingChrome,
        stiffness = FluidMotion.ResponseSnappy,
        visibilityThreshold = 0.5f,
      ),
    ) { value, _ ->
      applied += scrollBy(value - applied)
    }
    return 0f
  }
}

internal fun shouldMorphTitle(
  fontScale: Float,
  expandedLineCount: Int,
  reducedMotion: Boolean,
): Boolean = !reducedMotion && fontScale < 1.3f && expandedLineCount <= 1

/**
 * While the title morphs there is only ever one copy on screen: the bar's, transformed onto the
 * large title's own anchor. Cross-fading two copies is what made the type appear to change weight
 * halfway through, because the two copies were never the same size at the same instant.
 */
internal fun expandedTitleAlpha(
  progress: Float,
  morphing: Boolean,
  reducedMotion: Boolean,
): Float = when {
  reducedMotion -> if (progress < 1f) 1f else 0f
  morphing -> 0f
  else -> 1f - smoothStep(((progress - 0.60f) / 0.18f).coerceIn(0f, 1f))
}

/** The compact copy is either the moving title or an accessibility-safe, occluded handoff. */
internal fun compactTitleAlpha(
  progress: Float,
  morphing: Boolean,
  reducedMotion: Boolean,
): Float = when {
  reducedMotion -> if (progress >= 1f) 1f else 0f
  morphing -> 1f
  else -> smoothStep(((progress - 0.82f) / 0.18f).coerceIn(0f, 1f))
}

/**
 * One elastic edge for both ends of a scrolling body, and the gesture that refreshes it.
 *
 * Pull-to-refresh used to be a second, separate elastic effect stacked on the first: Material's
 * `PullToRefreshBox` owned the top edge and moved the page by a fixed twelve dp while its indicator
 * was positioned independently of it. That is why the top edge barely gave at all and why the
 * spinner appeared to pass through the content instead of arriving with it. Here there is one
 * displacement, the refresh threshold is a point along it, and the indicator lives in the space
 * that displacement opens — so the two can no longer disagree about where anything is.
 *
 * The rest follows from the same principle of a single source of truth:
 *
 *  * **Both edges give**, with the same curve, derived from the viewport rather than a fixed dp.
 *  * **A fling is absorbed, not clamped.** Leftover fling distance used to be accumulated frame by
 *    frame, so a fast flick reached its cap within two frames and read as a snap. The leftover
 *    *velocity* is handed to a critically damped spring instead, which is what turns reaching the
 *    end of a list into one physical excursion.
 */
@Stable
internal class FluidEdgeOverscrollState(
  private val reducedMotion: Boolean,
  /**
   * Whether the list has anywhere to go at all.
   *
   * A page shorter than its viewport has no "end" to bounce against — there is nothing the give
   * could be telling you. Letting it bounce anyway pushed the whole page, large title included,
   * up past the status bar, because the one thing a short page cannot do is scroll its title away
   * first. UIKit makes the same distinction, and for the same reason.
   */
  private val canScroll: () -> Boolean = { true },
) : NestedScrollConnection {

  private var dimensionPx = 0f
  private var rawDistancePx = 0f
  private var holdingForRefresh = false

  /** Positive pushes content down (top edge), negative pushes it up (bottom edge). */
  var offsetPx by mutableFloatStateOf(0f)
    private set

  /** Zero disables the gesture entirely, for screens that have nothing to refresh. */
  var refreshTriggerPx: Float = 0f
  var refreshHoldPx: Float = 0f
  var onRefresh: (() -> Unit)? = null

  /** How close the pull is to arming a refresh, from 0 to 1. Drives the indicator's own reveal. */
  val refreshPull: Float
    get() = if (refreshTriggerPx <= 0f) 0f else (offsetPx / refreshTriggerPx).coerceIn(0f, 1f)

  fun updateViewport(heightPx: Float) {
    dimensionPx = (heightPx * MaximumBounceFraction).coerceAtLeast(0f)
  }

  override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
    if (source != NestedScrollSource.UserInput) return Offset.Zero
    // While a refresh is running the opened space is not the user's to close: the list scrolls
    // under it exactly as it would under any other inset.
    if (holdingForRefresh) return Offset.Zero
    val offset = offsetPx
    if (offset == 0f || available.y == 0f) return Offset.Zero
    // Returning to rest always happens before the list itself moves, so the content never scrolls
    // while it is still displaced — the double movement that makes an elastic edge feel loose.
    val consumedY = when {
      offset > 0f && available.y < 0f -> max(available.y, -offset)
      offset < 0f && available.y > 0f -> min(available.y, -offset)
      else -> return Offset.Zero
    }
    applyOffset(offset + consumedY)
    return Offset(0f, consumedY)
  }

  override fun onPostScroll(
    consumed: Offset,
    available: Offset,
    source: NestedScrollSource,
  ): Offset {
    if (reducedMotion || dimensionPx <= 0f || holdingForRefresh) return Offset.Zero
    // Fling leftovers are deliberately ignored here: they arrive as velocity in onPostFling, where
    // one spring can express the whole excursion instead of a per-frame accumulation.
    if (source != NestedScrollSource.UserInput) return Offset.Zero
    val leftover = available.y
    if (leftover == 0f) return Offset.Zero
    // The top edge always gives: it is where a refresh is pulled from, whatever the page holds.
    // The bottom edge only exists once there is a bottom to reach.
    if (leftover < 0f && rawDistancePx <= 0f && !canScroll()) return Offset.Zero
    rawDistancePx += leftover
    offsetPx = signedRubberBandTranslation(rawDistancePx, dimensionPx)
    return Offset(0f, leftover)
  }

  override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
    val leftover = available.y
    if (holdingForRefresh) return Velocity(0f, leftover)
    val refresh = onRefresh
    if (refresh != null && refreshTriggerPx > 0f && offsetPx >= refreshTriggerPx) {
      holdingForRefresh = true
      refresh()
      settleTo(refreshHoldPx, velocityY = 0f)
      return Velocity(0f, leftover)
    }
    settleTo(0f, leftover)
    return Velocity(0f, leftover)
  }

  /** Called when the work the gesture started has finished, so the opened space can close. */
  suspend fun endRefresh() {
    if (!holdingForRefresh) return
    holdingForRefresh = false
    settleTo(0f, velocityY = 0f)
  }

  private fun applyOffset(value: Float) {
    val clamped = value.coerceIn(-dimensionPx, dimensionPx)
    offsetPx = clamped
    rawDistancePx = signedInverseRubberBandTranslation(clamped, dimensionPx)
  }

  private suspend fun settleTo(target: Float, velocityY: Float) {
    if (offsetPx == target && velocityY == 0f) return
    if (reducedMotion || dimensionPx <= 0f) {
      offsetPx = target
      rawDistancePx = signedInverseRubberBandTranslation(target, dimensionPx)
      return
    }
    val ceiling = dimensionPx
    animate(
      initialValue = offsetPx,
      targetValue = target,
      initialVelocity = velocityY.coerceIn(
        -MaximumBounceVelocityPxPerSecond,
        MaximumBounceVelocityPxPerSecond,
      ),
      animationSpec = spring(
        dampingRatio = FluidMotion.DampingChrome,
        stiffness = FluidMotion.ResponseStandard,
        visibilityThreshold = 0.5f,
      ),
    ) { value, _ ->
      offsetPx = value.coerceIn(-ceiling, ceiling)
    }
    offsetPx = target
    rawDistancePx = signedInverseRubberBandTranslation(target, dimensionPx)
  }
}
