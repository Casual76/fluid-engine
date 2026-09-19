/*
 * Vendored from Elyes Mansour's FloatingTabBar, with Kyant0's liquid-glass puck grafted in.
 * https://github.com/Kyant0/AndroidLiquidGlass — Copyright 2025 Kyant0, Apache License 2.0
 *
 * It arrives here from the app that had it, not the other way round: its glass — the refraction
 * under the puck, the hidden tinted row the puck samples back through itself, the stretch that
 * follows a dragged finger — is a level above what this engine's own bar could do, and an engine
 * whose components are worse than the app's is an engine nobody reaches for.
 *
 * Kept at the formatting it came with (four spaces, not this package's two) so that a later look at
 * upstream is a diff and not an archaeology. What did change is named where it happens: the drag
 * animation and the touch glow are the engine's, the shapes are the engine's, and the three places
 * that pre-multiplied effect parameters by the backdrop scale no longer do — see the note at the
 * hidden row, where getting that wrong is invisible until you look at the glass.
 */
@file:OptIn(ExperimentalSharedTransitionApi::class)

/*
 * FluidFloatingTabBar v1.0.1 by Elyes Mansour
 * https://github.com/elyesmansour/compose-floating-tab-bar
 * Licensed under the Apache License, Version 2.0
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored from the v1.0.1 tag: the published AAR is compiled against
 * Compose 1.8 and crashes with NoSuchMethodError on
 * SharedTransitionScope.sharedElement under the Compose version this app uses.
 * Compiling the source here keeps it binary-compatible.
 *
 * Local modifications on top of v1.0.1:
 * - Package renamed.
 * - InlineBar/ExpandedBar use Row/Column instead of ConstraintLayout, and the
 *   tab pill + standalone tab cluster is centered (iOS 26 style) instead of
 *   being pinned to opposite screen edges.
 * - The inline/expanded AnimatedContent is center-aligned so the collapse
 *   animation morphs around the center instead of the start edge.
 * - ExpandedTabs grew a draggable selection puck ported from Kyant0's
 *   AndroidLiquidGlass catalog (LiquidBottomTabs/LiquidBottomTab): equal-width
 *   tabs, a spring-damped indicator you can press-drag between tabs (release
 *   snaps to and navigates the nearest tab), a finger-tracking glow, and —
 *   when an optional [Backdrop] is supplied — chromatic lens refraction and
 *   an accent-tinted glass sample of the selected tab's icon showing through
 *   the puck. See [GlassDragAnimation] and [GlassTouchHighlight].
 */

package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.layerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberCombinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.rememberLayerBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.glass.interaction.GlassDragAnimation
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import dev.antigravity.fluidengine.ui.glass.backdrop.drawBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.blur
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.lens
import dev.antigravity.fluidengine.ui.glass.backdrop.effects.vibrancy
import dev.antigravity.fluidengine.ui.glass.backdrop.highlight.Highlight
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.InnerShadow
import dev.antigravity.fluidengine.ui.glass.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign



// Tuning for the gooey merge/split pulse across the inline<->expanded
// crossfade — see GooeyTransition.kt. Not derived from fadeIn()/fadeOut()'s
// own duration; just a reasonable match for it.
private val GooeyPeakBlur = 12.dp
private const val GooeyDurationMs = 300

// Rim/shadow the selection puck keeps when it is NOT being pressed. Both used to
// be driven straight off pressProgress, i.e. zero at rest, which left the puck
// defined only by its own dark surface wash — invisible against a dark bar.
// How long the selection puck takes to arrive when the bar unfolds, and how
// small it starts. Matched to [GooeyDurationMs], the length of the fold itself:
// the puck is not part of the shared element that travels, so the only thing
// keeping the two together is that they take the same time.
private const val PuckEnterMs = GooeyDurationMs
private const val PuckEnterScale = 0.82f

private const val PuckRestHighlightAlpha = 0.5f
private const val PuckRestShadowAlpha = 0.35f

// Vendored addition: the three shapes the bar can render. INLINE is the
// scroll-collapsed row (unchanged by search mode); EXPANDED is the normal
// all-tabs pill; SEARCH_EXPANDED replaces EXPANDED while search mode is
// active (see SearchExpandedBar).
private enum class FluidFloatingTabBarVisual { INLINE, EXPANDED, SEARCH_EXPANDED }

// Matches the established inline-row height (InlineStandaloneTab's own
// defaultMinSize floor at the FloatingNavBar call site, and the inline mini
// player's own ~48dp content height) so the search-expanded row doesn't read
// shorter than the bar's own shrunk state.
private val SearchBarRowHeight = 48.dp

/**
 * A floating tab bar that transitions between inline and expanded states based on scroll behavior.
 *
 * @param selectedTabKey the key of the currently selected tab
 * @param scrollConnection the scroll connection that handles state transitions
 * @param modifier the modifier to be applied to the tab bar
 * @param colors the colors used by the tab bar components
 * @param shapes the shapes used by the tab bar components
 * @param sizes the sizes and spacing used by the tab bar components
 * @param elevations the elevation values used by the tab bar components
 * @param tabBarContentModifier modifier applied to the tab bar sections containing the grouped tabs and standalone tab.
 * It is applied after the default styling (background, shadow, clip) but before any content padding.
 * @param inlineAccessory the accessory composable that appears in inline state (e.g., compact media player)
 * @param expandedAccessory the accessory composable that appears in expanded state (e.g., full media player)
 * @param content the content defining the tabs
 */
@Composable
fun FluidFloatingTabBar(
    selectedTabKey: Any?,
    scrollConnection: FluidFloatingTabBarScrollConnection,
    modifier: Modifier = Modifier,
    /**
     * What the bar's own pane is made of.
     *
     * Left alone, the bar makes its own glass out of [backdrop] — which is the only sensible default
     * for a component in an engine. It did not, and the consequence is the reason this parameter now
     * has a note: the app this bar was ported from builds its pane itself and hands it in here, so
     * everything looked right there, while every *other* app got a flat capsule of
     * `colors.backgroundColor` and a puck of real glass sitting on it. A bar that is only glass for
     * the one app that already had it is not a component anybody inherits.
     *
     * An app still passes its own when it has one: Fluidify's bar, its pill and the window that
     * grows out of that pill have to be the same material, and an app that lets one of the three be
     * made somewhere else has built the join it was trying to hide.
     */
    tabBarContentModifier: Modifier = Modifier,
    inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
    colors: FluidFloatingTabBarColors = FluidFloatingTabBarDefaults.colors(),
    /** See [FluidFloatingTabBarGlass]. */
    glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(),
    shapes: FluidFloatingTabBarShapes = FluidFloatingTabBarDefaults.shapes(),
    sizes: FluidFloatingTabBarSizes = FluidFloatingTabBarDefaults.sizes(),
    elevations: FluidFloatingTabBarElevations = FluidFloatingTabBarDefaults.elevations(),
    // Vendored addition: when non-null, the expanded selection puck samples this
    // backdrop for lens refraction and an accent-tinted glass preview of the
    // selected tab's icon. Null falls back to a flat colored puck.
    /**
     * What the bar's glass looks through.
     *
     * The engine's own state rather than a raw backdrop, so this reads like every other surface in
     * the package and so a caller can hand it the same page it hands a top bar or a sheet. Null
     * turns the sampling off: the bar then draws its colours flat, which is what a caller wants on
     * a device that has no `RenderEffect` and what a preview wants always.
     */
    backdrop: GlassBackdropState? = null,
    accentColor: Color? = null,
    // Vendored addition: when true (and searchBarContent non-null), the
    // expanded state renders SearchExpandedBar instead of ExpandedBar — the
    // tab group shrinks to the current screen's own icon and the standalone
    // slot expands to fill the rest of the row with searchBarContent. The
    // inline (scrolled) state is unaffected — it stays the normal collapsed
    // icon+accessory+standalone row regardless of searchMode.
    searchMode: Boolean = false,
    searchBarContent: (@Composable (Modifier) -> Unit)? = null,
    // Vendored addition: caller-held width (from a previous onExpandedWidthChanged
    // report) that SearchExpandedBar holds its row to, so the bar doesn't visibly
    // widen to fill available space when entering search mode. Null/0 falls back
    // to filling available space (e.g. first-ever composition in search mode).
    expandedContentWidthPx: Int? = null,
    onExpandedWidthChanged: ((Int) -> Unit)? = null,
    content: FluidFloatingTabBarScope.() -> Unit
) {
    // Rebuilt every composition, deliberately not remembered. The tabs hold
    // @Composable lambdas that close over selection state and colours; caching
    // them behind a key meant every value they captured had to be listed in that
    // key by hand, and anything forgotten showed up as a stale icon or colour on
    // one navigation path only. Re-running the builder is a handful of
    // allocations for a few tabs, and it makes that whole class of bug
    // unrepresentable.
    val scope = FluidFloatingTabBarScopeImpl().apply { content() }

    // Unwrapped once here. Everything below this point is private to the file and talks to
    // `drawBackdrop` directly, which is the layer a state is a handle on — except the bar's own
    // pane, which is built with `glassSurface` and wants the state itself.
    val backdropState = backdrop
    val backdrop = backdropState?.backdrop

    val isAccessoryShared = inlineAccessory != null && expandedAccessory != null

    val visual = when {
        scrollConnection.isInline -> FluidFloatingTabBarVisual.INLINE
        searchMode && searchBarContent != null -> FluidFloatingTabBarVisual.SEARCH_EXPANDED
        else -> FluidFloatingTabBarVisual.EXPANDED
    }

    // updateTransition (rather than the sugared top-level AnimatedContent) so
    // the transition object is available here to drive the gooey blur pulse,
    // not just inside the content lambda.
    val transition = updateTransition(targetState = visual, label = "floatingTabBarVisual")
    val gooeyBlurPx = with(LocalDensity.current) { GooeyPeakBlur.toPx() }
    // Liquid "gooey" merge/split (see GooeyTransition.kt): peaks mid-crossfade
    // and returns to 0 at rest, on every transition regardless of direction —
    // the keyframes spec defines the peak explicitly rather than deriving it
    // from a start/end value, since the steady-state target is always 0.
    // LOCAL CHANGE: held as State and read in the draw phase, not unwrapped
    // here. Read as a value, every frame of the fold recomposed this whole
    // subtree — the bar, both of its states, and the accessory inside them —
    // which is what made the animation stutter on a mid-range phone. The
    // modifier below reads it inside its own graphicsLayer block instead, so a
    // frame of the fold costs a draw and nothing more.
    val gooeyProgress = transition.animateFloat(
        transitionSpec = {
            keyframes {
                durationMillis = GooeyDurationMs
                0f at 0
                1f atFraction 0.5f using FastOutSlowInEasing
                0f at GooeyDurationMs
            }
        },
        label = "gooeyProgress"
    ) { _ -> 0f }

    SharedTransitionLayout(modifier = modifier) {
        // Wrapping AnimatedContent itself (not SharedTransitionLayout's own
        // modifier) so the offscreen gooey capture stays under
        // SharedTransitionLayout's own overlay mechanism — sharedElement-
        // tracked content (like the search tab's shared "standaloneTab"
        // bounds) renders via that overlay, and capturing it into the gooey
        // layer at the wrong level made it visibly pop in a frame late when
        // re-expanding. This way the overlay draws normally, on top.
        // Held as a lambda rather than a Boolean so flipping it costs no
        // recomposition — the glass surfaces call it during draw. See
        // LocalFluidTabBarBackdropFrozen.
        val frozenWhileAnimating = remember(transition) {
            { transition.currentState != transition.targetState }
        }
        // LOCAL CHANGE: and the same fact where the caller can see it.
        //
        // The composition local only reaches surfaces built inside this
        // composable — the selection puck. The bar's own pane and the pill above
        // it are built by the caller and passed in as `tabBarContentModifier`,
        // so reading the local there gave the default, "never frozen", and the
        // one moment the freeze exists for was the one it never covered. The
        // scroll connection is the object both sides already share.
        val morphing = transition.currentState != transition.targetState
        LaunchedEffect(scrollConnection, morphing) {
            scrollConnection.morphing = morphing
        }

        // The bar's own pane, when the caller has not built one. See `tabBarContentModifier`.
        //
        // Frozen while the bar changes shape, for the reason the note above gives: the caller's own
        // pane reads the scroll connection, and this one has to be held by the same fact or the fold
        // costs a full-screen capture per frame.
        val ownGlass = if (tabBarContentModifier === Modifier && backdropState != null) {
            Modifier.glassSurface(
                state = backdropState,
                tint = GlassDefaults.floatingTint(),
                shape = shapes.tabBarShape,
                role = GlassRole.Floating,
            )
        } else {
            tabBarContentModifier
        }
        CompositionLocalProvider(
            LocalFluidTabBarBackdropFrozen provides frozenWhileAnimating,
            LocalFluidFloatingTabBarGlass provides glass,
        ) {
        // Always applied: `gooey` is a no-op at radius 0 (no offscreen layer,
        // no filter), so there is nothing to gate on and nothing to recompose.
        Box(Modifier.gooey { gooeyBlurPx * gooeyProgress.value }) {
            transition.AnimatedContent(
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentAlignment = Alignment.BottomCenter
            ) { targetVisual ->
            // The accessory is built for BOTH states, and it has to be.
            //
            // It used to be built only for the state being arrived at, on the
            // grounds that both states are composed for the length of a fold
            // and the accessory is the heaviest thing either of them holds — a
            // cover to decode, two lines of text, controls, a progress line —
            // so the other copy was built, measured and thrown away sixty times
            // a second for nothing.
            //
            // Except it was not for nothing. This is a shared element, and a
            // shared element interpolates between two sets of bounds: with only
            // the arriving copy composed there is no second set, so nothing
            // travels. The pill vanished from above the tabs and reappeared
            // between them, which is exactly how it was described — "arrives
            // from nothing and disappears into the cosmic void".
            //
            // The cost is real and it is the price of this architecture: two
            // bars cross-faded with shared elements can only be continuous by
            // composing both. The way out is not to save the composition but to
            // stop having two bars — one bar and one number, which is what the
            // engine's FluidFoldingTabBar is and where this is going.
            when (targetVisual) {
                FluidFloatingTabBarVisual.INLINE -> InlineBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessory = inlineAccessory,
                    isAccessoryShared = isAccessoryShared,
                    onInlineTabClick = { scrollConnection.expand() },
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = ownGlass,
                    animatedVisibilityScope = this@AnimatedContent
                )
                FluidFloatingTabBarVisual.EXPANDED -> ExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessory = expandedAccessory,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = ownGlass,
                    animatedVisibilityScope = this@AnimatedContent,
                    backdrop = backdrop,
                    accentColor = accentColor,
                    onWidthMeasured = onExpandedWidthChanged
                )
                FluidFloatingTabBarVisual.SEARCH_EXPANDED -> SearchExpandedBar(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    accessory = expandedAccessory,
                    isAccessoryShared = isAccessoryShared,
                    colors = colors,
                    shapes = shapes,
                    sizes = sizes,
                    elevations = elevations,
                    tabBarContentModifier = ownGlass,
                    animatedVisibilityScope = this@AnimatedContent,
                    searchBarContent = searchBarContent ?: {},
                    targetWidthPx = expandedContentWidthPx
                )
            }
            }
        }
        }
    }
}

/**
 * True for exactly the frames of a tab bar transition (inline <-> expanded <->
 * search). The bar's glass surfaces pass it to `drawBackdrop`'s `frozen`, which
 * makes them reuse their last backdrop capture instead of re-recording the whole
 * screen every frame while their bounds animate.
 *
 * A lambda, not a Boolean, so flipping it triggers no recomposition — the surfaces
 * call it during draw. Static local for the same reason: the lambda's identity is
 * stable, only what it reads changes.
 */
val LocalFluidTabBarBackdropFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * The two or three numbers of this bar's glass an app is allowed to move.
 *
 * Separate from [GlassOptics], and it has to be: the optics describe a pane of the material, and
 * this bar is not a pane. It is a hidden tinted copy of its own tab row, sampled back through a
 * lensed puck that slides over it — a construction with knobs of its own that no general surface
 * has. What an app actually wants to set is how frosted the bar reads and what colour the chosen
 * tab is washed with, and that is all this carries.
 *
 * [puckColor] unspecified is the useful default: the wash then follows the side the surface is on,
 * light on dark and dark on light, which is what a chosen tab has to do to be visible at all.
 */
@Immutable
data class FluidFloatingTabBarGlass(
    /** How frosted the bar reads. Feeds the hidden row's blur, which is what the puck refracts. */
    val blurRadius: Dp = 2.dp,
    /** The wash under the chosen tab. [Color.Unspecified] follows the surface side. */
    val puckColor: Color = Color.Unspecified,
    /** How much of that wash there is at rest. It eases towards clear while the puck is pressed. */
    val puckOpacity: Float = 0.26f,
)

/**
 * What the bar's glass is made of, for the parts of it that are composed too deep to be passed a
 * parameter.
 *
 * Provided by [FluidFloatingTabBar] from its own argument. An app never reads this: it sets
 * `glass = ...` on the bar and this is how the value reaches the puck.
 */
val LocalFluidFloatingTabBarGlass = staticCompositionLocalOf { FluidFloatingTabBarGlass() }

/**
 * A [NestedScrollConnection] that handles scroll events to transition between inline and expanded states.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded).
 * @param scrollThresholdPx The minimum scroll distance in pixels required to collapse to inline.
 * @param expandThresholdPx The minimum scroll distance in pixels required to expand back. Kept
 * well below [scrollThresholdPx] so the bar returns as soon as the user scrolls up.
 * @param inlineBehavior Defines when the tab bar should transition to inline state.
 */
class FluidFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    private val scrollThresholdPx: Float,
    private val expandThresholdPx: Float = scrollThresholdPx,
    private val inlineBehavior: FluidFloatingTabBarInlineBehavior = FluidFloatingTabBarInlineBehavior.OnScrollDown
) : NestedScrollConnection {
    var isInline by mutableStateOf(initialIsInline)
        private set

    /**
     * LOCAL CHANGE: while true, scrolling leaves the bar alone.
     *
     * For searching. The bar carries the text field, and folding it takes the
     * field away mid-search: scrolling the results put it back a moment later,
     * which re-created the field, which asked for the focus again, which opened
     * the keyboard — every scroll up. And a bar that folds while you are typing
     * in it is wrong even without that.
     */
    var locked by mutableStateOf(false)

    /**
     * LOCAL CHANGE: true for exactly the frames the bar is changing shape.
     *
     * Published here rather than through a composition local because the bar's
     * glass is applied by whoever uses the bar, from outside its composition.
     */
    var morphing by mutableStateOf(false)

    /**
     * LOCAL CHANGE: true while the page under the bar is moving.
     *
     * Every glass surface samples a recording of the screen, and a recording of
     * a scrolling screen is a new recording sixty times a second — so each pane
     * re-captures and re-runs its blur and lens on every frame of every scroll.
     * Measured on the home page: 57ms a frame with the glass on, 25ms with it
     * off. Nobody can read the refraction through a surface while the thing
     * behind it is flying past, so during the scroll they hold the last one.
     */
    var scrolling by mutableStateOf(false)
        private set

    private var accumulatedScroll = 0f

    fun expand() {
        isInline = false
        accumulatedScroll = 0f
    }

    /**
     * Ends the scroll-hold by decree.
     *
     * [scrolling] is set in onPreScroll and cleared only in onPreFling — and a
     * list disposed mid-gesture (a navigation, a page swap) never delivers its
     * fling, so the flag stayed up and every glass surface replayed its last
     * capture for good: the "content is not there when I come back" bug. The
     * app calls this on every route change.
     */
    fun settle() {
        scrolling = false
    }

    fun inline() {
        isInline = true
        accumulatedScroll = 0f
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // If behavior is Never, don't change state
        if (inlineBehavior == FluidFloatingTabBarInlineBehavior.Never || locked) {
            return Offset.Zero
        }

        val scrollDelta = available.y
        if (scrollDelta != 0f) scrolling = true

        // Reset accumulated scroll if changing direction
        if ((accumulatedScroll > 0 && scrollDelta < 0) || (accumulatedScroll < 0 && scrollDelta > 0)) {
            accumulatedScroll = 0f
        }

        // Accumulate scroll
        accumulatedScroll += scrollDelta

        when (inlineBehavior) {
            FluidFloatingTabBarInlineBehavior.OnScrollDown -> {
                // Asymmetric on purpose. Collapsing wants hysteresis so a jittery
                // downward scroll can't flicker the bar, and the distance is free
                // there — the user is already moving away. Expanding is the
                // opposite: the bar is what they are reaching for, so making them
                // drag the same distance back reads as the bar being slow to
                // return. Coming back is near-immediate, with just enough travel
                // required that a shaky finger mid-scroll can't oscillate it.
                // LOCAL CHANGE: two distances, and they are not the same
                // distance.
                //
                // Upstream folds and unfolds at one threshold, and one threshold
                // is what a scrolling reader feels as flickering: every small
                // correction upwards pulls the whole bar open over the text.
                // Folding therefore wants hysteresis, and the distance is free
                // there — you are already moving away from the bar. Coming back
                // is the opposite: the bar is what you are reaching for, and
                // making you drag fifty more pixels to get it reads as the bar
                // being slow.
                //
                // For a while this side was zero — the bar only came back at the
                // very top of the page. That is defensible for a reader and
                // wrong for this app: the page is a list you scrub up and down,
                // and the way back to the music should not be "scroll all the
                // way to the top first". `expandThresholdPx` has been documented
                // as the answer to exactly this since the parameter was added;
                // it had simply never been read.
                if (accumulatedScroll <= -scrollThresholdPx && !isInline) {
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll >= expandThresholdPx && isInline) {
                    expand()
                }
            }

            FluidFloatingTabBarInlineBehavior.OnScrollUp -> {
                // Check if we've scrolled enough to trigger state change
                if (accumulatedScroll >= scrollThresholdPx && !isInline) {
                    // Scrolling up enough - transition to inline mode
                    isInline = true
                    accumulatedScroll = 0f // Reset after state change
                } else if (accumulatedScroll <= -scrollThresholdPx && isInline) {
                    // Scrolling down enough - transition to expanded mode
                    isInline = false
                    accumulatedScroll = 0f // Reset after state change
                }
            }

            FluidFloatingTabBarInlineBehavior.Never -> {
                // Already handled above, but included for completeness
            }
        }

        return Offset.Zero // Don't consume the scroll, let it pass through
    }

    /**
     * LOCAL CHANGE: the bar comes back at the top of the page.
     *
     * What reaches here is what the content could not use. Dragging downwards
     * with nothing left to scroll means the list is already at its first item,
     * which is the one moment the bar is asked for rather than in the way.
     */
    override suspend fun onPreFling(available: Velocity): Velocity {
        // The end of the gesture, fling or not: Compose calls this on release
        // either way, with a velocity of zero when there was none.
        scrolling = false
        return Velocity.Zero
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (inlineBehavior == FluidFloatingTabBarInlineBehavior.Never || locked) {
            return Offset.Zero
        }
        if (isInline && available.y > 0f) expand()
        return Offset.Zero
    }
}

/**
 * Creates and remembers a [FluidFloatingTabBarScrollConnection] instance.
 *
 * @param initialIsInline Initial state of the tab bar (inline or expanded). Default is false.
 * @param scrollThreshold The scroll distance required to collapse to inline. Default is 50.dp.
 * @param expandThreshold The scroll distance required to expand back. Default is 8.dp — small
 * enough that the bar returns immediately on scroll up, large enough that a shaky finger
 * mid-scroll cannot oscillate it.
 * @param inlineBehavior Defines when the tab bar should transition to inline state. Default is [FluidFloatingTabBarInlineBehavior.OnScrollDown].
 * @return A remembered [FluidFloatingTabBarScrollConnection] instance.
 */
@Composable
fun rememberFluidFloatingTabBarScrollConnection(
    initialIsInline: Boolean = false,
    scrollThreshold: Dp = 50.dp,
    expandThreshold: Dp = 8.dp,
    inlineBehavior: FluidFloatingTabBarInlineBehavior = FluidFloatingTabBarInlineBehavior.OnScrollDown
): FluidFloatingTabBarScrollConnection = with(LocalDensity.current) {
    val scrollThresholdPx = scrollThreshold.toPx()
    val expandThresholdPx = expandThreshold.toPx()
    remember(scrollThresholdPx, expandThresholdPx, inlineBehavior, initialIsInline) {
        FluidFloatingTabBarScrollConnection(
            initialIsInline,
            scrollThresholdPx,
            expandThresholdPx,
            inlineBehavior,
        )
    }
}

/**
 * Defines when the floating tab bar should transition to inline state.
 */
enum class FluidFloatingTabBarInlineBehavior {
    /** Never transition to inline - it stays in expanded state */
    Never,

    /** Transition to inline when scrolling down */
    OnScrollDown,

    /** Transition to inline when scrolling up */
    OnScrollUp
}

/**
 * The same bar, for a screen whose navigation is a list of destinations and nothing more.
 *
 * The scope above is what this bar is for — a tab can be any pair of composables, and the pill that
 * carries what is playing is an accessory rather than a tab — but most screens have three
 * destinations with a label and a glyph each, and spelling that out as a DSL every time is noise.
 * This is the shape the engine's previous bar had, kept because it is the right shape for the
 * common case and because the apps that used it should not have to be rewritten to get the better
 * glass.
 */
@Composable
fun FluidFloatingTabBar(
    items: List<FluidTabItem>,
    selectedRoute: String?,
    onSelect: (FluidTabItem) -> Unit,
    scrollConnection: FluidFloatingTabBarScrollConnection,
    backdrop: GlassBackdropState? = null,
    modifier: Modifier = Modifier,
    onReselect: (FluidTabItem) -> Unit = {},
    glass: FluidFloatingTabBarGlass = FluidFloatingTabBarGlass(),
    accentColor: Color? = null,
) {
    if (items.isEmpty()) return
    FluidFloatingTabBar(
        selectedTabKey = selectedRoute,
        scrollConnection = scrollConnection,
        modifier = modifier,
        backdrop = backdrop,
        glass = glass,
        accentColor = accentColor,
    ) {
        items.forEach { item ->
            tab(
                key = item.route,
                title = { Text(item.label) },
                icon = { Icon(item.icon, contentDescription = null) },
                onClick = { if (item.route == selectedRoute) onReselect(item) else onSelect(item) },
            )
        }
    }
}

interface FluidFloatingTabBarScope {
    /**
     * Adds a regular tab to the floating tab bar.
     *
     * @param key Unique identifier for the tab
     * @param title Composable content for the tab title
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     */
    fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current }
    )

    /**
     * Adds a standalone tab to the floating tab bar.
     *
     * Note: Calling this method more than once will override the previous standalone tab value.
     *
     * @param key Unique identifier for the standalone tab
     * @param icon Composable content for the tab icon
     * @param onClick Callback invoked when the tab is clicked
     * @param indication Optional indication provider for touch feedback, defaults to LocalIndication.current
     * @param title Composable content for the tab title. Never actually shown —
     * the standalone tab is always an icon-only floating circle, in both inline
     * and expanded states (see InlineStandaloneTab / ExpandedStandaloneTab).
     */
    fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)? = { LocalIndication.current },
        title: @Composable () -> Unit = {}
    )
}

/**
 * Plain release-triggered [clickable]. Firing on finger-DOWN (the old behaviour)
 * meant any touch that turned into a scroll or a puck drag still navigated first —
 * "opens a screen even when I didn't drop the puck". Release-fire lets Compose's
 * touch-slop arbitration cancel the tap when the gesture becomes a drag/scroll, so
 * only a real tap navigates. Keeps the custom [indication].
 */
@Composable
private fun Modifier.tapClickable(
    indication: Indication?,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        onClick = onClick,
        indication = indication,
        interactionSource = interactionSource
    )
}

@Composable
private fun SharedTransitionScope.InlineBar(
    scope: FluidFloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    onInlineTabClick: () -> Unit,
    colors: FluidFloatingTabBarColors,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    elevations: FluidFloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val standaloneTab = scope.standaloneTab
    val hasInlineTab = inlineTab != null

    // With an accessory the row spans the full width ([tab][accessory][standalone],
    // iOS 26 Apple Music style); without one, the cluster is packed and centered.
    Row(
        horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (accessory == null) Modifier.wrapContentWidth() else Modifier)
            .height(IntrinsicSize.Max)
            // Only without an accessory. With one, the band — a now-playing pill, two lines of
            // text and a progress line — is what decides how tall this row is, and a floor that
            // disagreed with it would leave the accessory and the tab beside it different heights.
            .then(
                if (accessory == null) {
                    Modifier.defaultMinSize(minHeight = sizes.inlineHeight)
                } else {
                    Modifier
                }
            )
    ) {
        if (hasInlineTab) {
            InlineTab(
                inlineTab = inlineTab,
                onInlineTabClick = onInlineTabClick,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                modifier = Modifier
            )
        }

        if (accessory != null) {
            InlineAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }

        if (standaloneTab != null) {
            InlineStandaloneTab(
                standaloneTab = standaloneTab,
                shapes = shapes,
                sizes = sizes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                tabBarContentModifier = tabBarContentModifier,
                // fillMaxHeight() + the row's IntrinsicSize.Max is supposed to
                // match this to the docked mini player's height, but intrinsic
                // measurement can under-report the mini player's actual
                // (dynamically-measured) content height — floor it explicitly
                // so the standalone tab pill can't end up shorter.
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .fillMaxHeight()
                    // Height-first: aspectRatio resolves from maxWidth by default,
                    // so on a narrow bar the leftover width — not the row height —
                    // decided the circle's diameter and it came out smaller than the
                    // bar beside it.
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.InlineTab(
    inlineTab: FluidFloatingTabBarTab,
    onInlineTabClick: () -> Unit,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    colors: FluidFloatingTabBarColors,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    Box(
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.tabBarShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.tabBarShape
            )
            .clip(shapes.tabBarShape)
            .then(tabBarContentModifier)
            .tapClickable(
                indication = inlineTab.indication?.invoke(),
                onClick = {
                    onInlineTabClick()
                    inlineTab.onClick()
                }
            )
            .padding(sizes.tabInlineContentPadding)
    ) {
        Tab(
            icon = {
                Box(
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("tab#${inlineTab.key}-icon"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        zIndexInOverlay = 1f
                    )
                ) {
                    inlineTab.icon()
                }
            },
            title = { inlineTab.title() },
            isInline = true
        )
    }
}

@Composable
private fun SharedTransitionScope.InlineStandaloneTab(
    standaloneTab: FluidFloatingTabBarTab,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    colors: FluidFloatingTabBarColors,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    // Same finger-tracking glow as the keyboard-active search pill
    // (NavBarSearchInputBar) — a soft radial light following the touch point.
    val glowScope = rememberCoroutineScope()
    val onDark = GlassDefaults.isDarkSurface()
    val glow = remember(glowScope, onDark) {
        GlassTouchHighlight(animationScope = glowScope, onDarkSurface = onDark)
    }

    Tab(
        icon = standaloneTab.icon,
        title = standaloneTab.title,
        isInline = true,
        isStandalone = true,
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.standaloneTabShape,
                elevation = elevations.inlineElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.standaloneTabShape
            )
            .clip(shapes.standaloneTabShape)
            .then(tabBarContentModifier)
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .tapClickable(
                indication = standaloneTab.indication?.invoke(),
                onClick = standaloneTab.onClick
            )
            // Unlike the regular tab pill (InlineTab, ExpandedTabs), this circle had
            // no inset at all — the icon sat flush against its edge.
            .padding(sizes.tabInlineContentPadding)
    )
}

@Composable
private fun SharedTransitionScope.InlineAccessory(
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FluidFloatingTabBarColors,
    shapes: FluidFloatingTabBarShapes,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    accessory?.let { accessory ->
        Box(
            modifier = modifier
                .then(
                    if (isAccessoryShared) {
                        Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("accessory"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    } else {
                        Modifier.animateEnterExitAccessory(
                            sharedTransitionScope = this,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                )
        ) {
            accessory(
                Modifier
                    .fillMaxSize()
                    .shadow(
                        shape = shapes.accessoryShape,
                        elevation = elevations.inlineElevation
                    )
                    .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                    .clip(shapes.accessoryShape),
                animatedVisibilityScope
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedBar(
    scope: FluidFloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FluidFloatingTabBarColors,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    elevations: FluidFloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    backdrop: Backdrop?,
    accentColor: Color?,
    // Vendored addition: reports this row's own measured width upward so
    // SearchExpandedBar can hold the bar at the exact same overall width
    // instead of expanding to fill all available space in search mode.
    onWidthMeasured: ((Int) -> Unit)? = null,
) {
    val hasTabGroup = scope.tabs.isNotEmpty()
    val standaloneTab = scope.standaloneTab

    // The accessory matches the tab row's own (content-sized, not full-bleed)
    // width, so both pills line up edge to edge. The tab row is measured first
    // (onSizeChanged below); the accessory falls back to fillMaxWidth until
    // that first measurement lands — a one-frame lag, same pattern as
    // FloatingMiniPlayer's measuredHeightPx.
    val density = LocalDensity.current
    var tabRowWidthPx by remember { mutableIntStateOf(0) }

    // Vendored addition: the pill is never wider than the room the circle
    // beside it leaves.
    //
    // tabWidth is a ceiling and not a promise. The pill was laid out at that
    // width whatever the screen could take, and on a narrow one — a 360dp
    // phone, or any phone at a larger display size — the tabs plus the circle
    // came to more than the bar had. A row does not clip what does not fit, so
    // what was left over was the circle drawn on top of the last tab.
    //
    // Both halves are measured rather than assumed: the bar's own width, and
    // the circle's, which is the row's height and so does not depend on this.
    // Measured, therefore a frame late — the weight below is what holds the
    // first frame together, since a weighted child cannot be given more room
    // than the row has left.
    val layoutDirection = LocalLayoutDirection.current
    var barWidthPx by remember { mutableIntStateOf(0) }
    var circleWidthPx by remember { mutableIntStateOf(0) }
    val tabsCount = scope.tabs.size
    // The circle is optional, and for a long time this line behaved as though it were not:
    // requiring `circleWidthPx > 0` meant a bar with no standalone tab never fitted its tabs at
    // all. Five tabs at the full 88 dp come to 448 dp, a phone gives the bar about 383 dp, and a
    // Row does not shrink what does not fit — so the last tab was measured outside the pill, the
    // labels of the ones before it wrapped onto a second line, and the bar grew to swallow the
    // wrap. Every symptom of "too tall with five items" starts here, not in the height.
    val fittedSizes = with(density) {
        val fitted = fittedTabWidth(
            barWidth = barWidthPx.toDp(),
            standaloneWidth = circleWidthPx.toDp(),
            // No circle, no gap to leave for one.
            gap = if (standaloneTab != null) sizes.componentSpacing else 0.dp,
            inset = sizes.tabBarContentPadding.calculateStartPadding(layoutDirection) +
                sizes.tabBarContentPadding.calculateEndPadding(layoutDirection),
            tabsCount = tabsCount,
            preferred = sizes.tabWidth,
        )
        if (fitted != sizes.tabWidth) sizes.copy(tabWidth = fitted) else sizes
    }

    // The standalone tab (search) is always its own floating circle — same as
    // the inline (collapsed) state — never merged into the tab group pill, so
    // it reads as one consistent circular element through both inline and
    // expanded states (see ExpandedStandaloneTab / InlineStandaloneTab, tied
    // together by the shared "standaloneTab" element).
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        // fillMaxWidth, so this measures the bar rather than its contents.
        modifier = Modifier.fillMaxWidth().onSizeChanged { barWidthPx = it.width }
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = if (tabRowWidthPx > 0) {
                    Modifier.width(with(density) { tabRowWidthPx.toDp() })
                } else {
                    Modifier.fillMaxWidth()
                }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(IntrinsicSize.Max)
                // A floor, not a fixed height, and the difference is a font scale away: at 130%
                // text a one-line label is genuinely taller than the bar's nominal height, and a
                // hard `height` would cut it in half. `defaultMinSize` only ever adds, so the bar
                // is [FluidFloatingTabBarSizes.barHeight] on every ordinary device and grows for
                // the people who need it to.
                .defaultMinSize(minHeight = sizes.barHeight)
                .onSizeChanged {
                    tabRowWidthPx = it.width
                    onWidthMeasured?.invoke(it.width)
                }
        ) {
            if (hasTabGroup) {
                ExpandedTabs(
                    scope = scope,
                    selectedTabKey = selectedTabKey,
                    shapes = shapes,
                    sizes = fittedSizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    backdrop = backdrop,
                    // The app's accent, when the caller has not named one. It used to be
                    // `colors.backgroundColor` — the bar's own fill — which tinted the hidden row
                    // the exact colour of the surface it sits on, so the accent-tinted copy of the
                    // icon the puck exists to show was tinted into invisibility.
                    accentColor = accentColor ?: MaterialTheme.colorScheme.primary,
                    // Weighted, so the row can never hand it more than what is
                    // left beside the circle; fill = false so a wide screen
                    // does not stretch it past its own width either.
                    modifier = Modifier.weight(1f, fill = false)
                )
            }

            if (standaloneTab != null) {
                ExpandedStandaloneTab(
                    standaloneTab = standaloneTab,
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier
                        .onSizeChanged { circleWidthPx = it.width }
                        // Same floor the inline row already needed: IntrinsicSize.Max
                        // can under-report the tab group's real height, and without a
                        // floor this circle shrinks to that under-reported value while
                        // the tab pill measures its true (taller) content — which is
                        // the search pill looking smaller than the nav bar.
                        .defaultMinSize(minHeight = 48.dp)
                        .fillMaxHeight()
                        // Height-first, as in the inline row: the default resolves the
                        // square from maxWidth, so leftover row width could decide the
                        // diameter instead of the bar height.
                        .aspectRatio(1f, matchHeightConstraintsFirst = true)
                )
            }
        }
    }
}

/**
 * Vendored addition: the search-active expanded shape. Same accessory-above
 * treatment as [ExpandedBar], but the row below it is the collapsed-style
 * single current-screen icon ([InlineTab], reused as-is) next to
 * [searchBarContent] filling the rest of the row — instead of the full
 * tab-group pill + circular standalone tab.
 */
@Composable
private fun SharedTransitionScope.SearchExpandedBar(
    scope: FluidFloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    accessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)?,
    isAccessoryShared: Boolean,
    colors: FluidFloatingTabBarColors,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    elevations: FluidFloatingTabBarElevations,
    tabBarContentModifier: Modifier,
    animatedVisibilityScope: AnimatedVisibilityScope,
    searchBarContent: @Composable (Modifier) -> Unit,
    // Vendored addition: the normal-expanded row's own last-measured width
    // (see ExpandedBar's onWidthMeasured) — holding this row to that same
    // width keeps the bar's overall footprint constant across search mode
    // instead of the searchBarContent's weight(1f) filling all available
    // space. Null/0 (nothing measured yet) falls back to filling available
    // width.
    targetWidthPx: Int?,
) {
    val inlineTab = scope.getInlineTab(selectedTabKey)
    val density = LocalDensity.current
    val targetWidthModifier = if (targetWidthPx != null && targetWidthPx > 0) {
        Modifier.width(with(density) { targetWidthPx.toDp() })
    } else {
        Modifier.fillMaxWidth()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (accessory != null) {
            ExpandedAccessory(
                accessory = accessory,
                isAccessoryShared = isAccessoryShared,
                shapes = shapes,
                colors = colors,
                elevations = elevations,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = targetWidthModifier
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(sizes.componentSpacing),
            verticalAlignment = Alignment.CenterVertically,
            // Fixed rather than IntrinsicSize.Max: matches the mini player's own
            // inline (shrunk) height exactly instead of letting a short
            // searchBarContent under-report its intrinsic height and shrink the row.
            modifier = Modifier.height(SearchBarRowHeight).then(targetWidthModifier)
        ) {
            if (inlineTab != null) {
                InlineTab(
                    inlineTab = inlineTab,
                    // Already expanded — a tap here should just navigate
                    // (inlineTab.onClick, fired inside InlineTab itself), not
                    // trigger the inline scroll-connection's expand().
                    onInlineTabClick = {},
                    shapes = shapes,
                    sizes = sizes,
                    colors = colors,
                    elevations = elevations,
                    animatedVisibilityScope = animatedVisibilityScope,
                    tabBarContentModifier = tabBarContentModifier,
                    modifier = Modifier.fillMaxHeight()
                )
            }

            searchBarContent(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // Same "standaloneTab" key as ExpandedStandaloneTab/InlineStandaloneTab
                    // (the search circle) — without this the circle just pops out and the
                    // bar pops in as two unrelated fades instead of one smoothly growing
                    // pill; this is what makes the tab-group icon side already morph
                    // smoothly, and lets the search-inline (scrolled) state morph back
                    // down to a circle too.
                    .sharedElement(
                        sharedContentState = rememberSharedContentState("standaloneTab"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        zIndexInOverlay = 1f
                    )
                    .shadow(shape = shapes.tabBarShape, elevation = elevations.expandedElevation)
                    .background(color = colors.backgroundColor, shape = shapes.tabBarShape)
                    .clip(shapes.tabBarShape)
                    .then(tabBarContentModifier)
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.ExpandedStandaloneTab(
    standaloneTab: FluidFloatingTabBarTab,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    colors: FluidFloatingTabBarColors,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier
) {
    // Same finger-tracking glow as the keyboard-active search pill
    // (NavBarSearchInputBar) — a soft radial light following the touch point.
    val glowScope = rememberCoroutineScope()
    val onDark = GlassDefaults.isDarkSurface()
    val glow = remember(glowScope, onDark) {
        GlassTouchHighlight(animationScope = glowScope, onDarkSurface = onDark)
    }

    Tab(
        icon = standaloneTab.icon,
        title = standaloneTab.title,
        isInline = true,
        isStandalone = true,
        modifier = modifier
            .sharedElement(
                sharedContentState = rememberSharedContentState("standaloneTab"),
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            )
            .shadow(
                shape = shapes.standaloneTabShape,
                elevation = elevations.expandedElevation
            )
            .background(
                color = colors.backgroundColor,
                shape = shapes.standaloneTabShape
            )
            .clip(shapes.standaloneTabShape)
            .then(tabBarContentModifier)
            .then(glow.gestureModifier)
            .then(glow.modifier)
            .tapClickable(
                indication = standaloneTab.indication?.invoke(),
                onClick = standaloneTab.onClick
            )
            // Unlike the regular tab pill (ExpandedTabs), this circle had no inset
            // at all — the icon sat flush against its edge.
            .padding(sizes.tabExpandedContentPadding)
    )
}

@Composable
private fun SharedTransitionScope.ExpandedAccessory(
    accessory: @Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit,
    isAccessoryShared: Boolean,
    colors: FluidFloatingTabBarColors,
    shapes: FluidFloatingTabBarShapes,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .then(
                if (isAccessoryShared) {
                    Modifier.sharedElement(
                        sharedContentState = rememberSharedContentState("accessory"),
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                } else {
                    Modifier.animateEnterExitAccessory(
                        sharedTransitionScope = this,
                        animatedVisibilityScope = animatedVisibilityScope
                    )
                }
            )
    ) {
        accessory(
            Modifier
                .shadow(
                    shape = shapes.accessoryShape,
                    elevation = elevations.expandedElevation
                )
                .background(color = colors.accessoryBackgroundColor, shapes.accessoryShape)
                .clip(shapes.accessoryShape),
            animatedVisibilityScope
        )
    }
}

@Composable
private fun SharedTransitionScope.ExpandedTabs(
    scope: FluidFloatingTabBarScopeImpl,
    selectedTabKey: Any?,
    shapes: FluidFloatingTabBarShapes,
    sizes: FluidFloatingTabBarSizes,
    colors: FluidFloatingTabBarColors,
    elevations: FluidFloatingTabBarElevations,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier,
    tabBarContentModifier: Modifier,
    backdrop: Backdrop?,
    accentColor: Color
) {
    // The standalone tab (e.g. search) never appears here — it's always its
    // own floating circle, rendered as a sibling by ExpandedBar (see
    // ExpandedStandaloneTab). Only the regular tabs form this pill/puck group.
    val allTabs = scope.tabs
    val tabsCount = allTabs.size
    if (tabsCount == 0) return
    // dampedDragAnimation is remember(...)-ed and its onDragStopped closure would
    // otherwise capture the FIRST composition's allTabs — freezing each tab's
    // onClick (and the isSelected it closed over). That stranded drag-to-Home:
    // Home started selected, so its frozen onClick did scroll-to-top forever
    // instead of navigating. Read the live tabs through this instead.
    val currentTabs = rememberUpdatedState(allTabs)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isLtr = layoutDirection == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()
    val glassConfig = LocalFluidFloatingTabBarGlass.current
    // Puck wash. Unset, it is the app's accent.
    //
    // It used to be a pair of hard greys — near-white on a dark bar, near-black on a light one —
    // chosen so the chosen tab would be visible whatever the palette did. Visible it was: on a
    // light theme the selected tab sat under a grey-black disc that belonged to no part of the
    // app, which is a thing people notice long before they notice it is legible. An accent at a
    // quarter alpha is visible for the same structural reason (it is a wash over a film that is
    // near-white on one side and a mid grey on the other, so it darkens or lightens accordingly)
    // and it is the app's own colour instead of a stranger's.
    //
    // `primary` and not `secondaryContainer`: this is a translucent wash, not a filled Material
    // indicator, and a container colour — already most of the way to the surface — has nothing
    // left to say at a quarter strength.
    val puckWash = if (glassConfig.puckColor.isSpecified) {
        glassConfig.puckColor
    } else {
        MaterialTheme.colorScheme.primary
    }
    val puckRestAlpha = glassConfig.puckOpacity.coerceIn(0f, 1f)

    val tabWidthPx = with(density) { sizes.tabWidth.toPx() }
    // The row's own content padding insets the tabs from the pill's edges, so
    // the puck (a sibling, not a row child) has to account for it explicitly —
    // otherwise it drifts out of alignment with where the tabs actually sit.
    val paddingStartPx = with(density) { sizes.tabBarContentPadding.calculateStartPadding(layoutDirection).toPx() }
    val paddingEndPx = with(density) { sizes.tabBarContentPadding.calculateEndPadding(layoutDirection).toPx() }
    val totalWidthPx = tabWidthPx * tabsCount + paddingStartPx + paddingEndPx

    // Ported from LiquidBottomTabs: the whole pill nudges a few dp in the drag
    // direction (eased, clamped) so it feels like it's being tugged, not just
    // the puck sliding inside a static shell.
    val offsetAnimation = remember(tabsCount) { Animatable(0f) }
    val panelOffset by remember(density, totalWidthPx) {
        derivedStateOf {
            val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
            with(density) {
                4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(tabsCount) {
        mutableIntStateOf(
            allTabs.indexOfFirst { it.key == selectedTabKey }.coerceIn(0, tabsCount - 1)
        )
    }
    // Matches Kyant's LiquidBottomTabs behavior exactly: navigation fires ONLY
    // in onDragStopped (finger lifted, puck settling onto its target) — never
    // mid-drag. An earlier "predictive fire" here navigated while the puck was
    // still being dragged, which opened screens before the drop.
    // inspectDragGestures (vendored) fires onDragStart/onDrag/onDragEnd for a
    // plain tap-through too, with zero net movement, and never consumes the
    // touch — so a tap that lands on the puck while it's mid-sweep between
    // two tabs would otherwise re-navigate to targetValue (the sweep's
    // destination) on top of whatever the tapped tab's own tapClickable just
    // navigated to. Gate the puck's own navigate-on-release to genuine drags.
    var hasDraggedPuck = false
    // Read out here: what is below is a remembered object, not composition, and
    // a local cannot be asked for from inside one.
    val crossingHaptics = LocalFluidHaptics.current
    val dampedDragAnimation = remember(animationScope, tabsCount) {
        GlassDragAnimation(
            animationScope = animationScope,
            initialValue = currentIndex.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            // Matches LiquidBottomTabs' literal ratio (78dp/56dp) rather than a
            // fraction of our own tab width — a proportional scale factor, not
            // an absolute size, so it should track the source value directly.
            pressedScale = 78f / 56f,
            onDragStarted = { hasDraggedPuck = false },
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().coerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                // updateValue (not animateToValue): the modifier's own
                // onDragStart/onDragEnd already own the press-grow + release
                // shrink. animateToValue pressed a SECOND time and, worse, ran
                // under mutatorMutex — a later sync could cancel it mid-settle
                // and strand the puck at the old index (puck stuck on Home while
                // a different screen is showing). updateValue just springs the
                // position, no press, no mutex.
                updateValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
                if (hasDraggedPuck) {
                    currentTabs.value.getOrNull(targetIndex)?.onClick?.invoke()
                }
            },
            onDrag = { _, dragAmount ->
                if (dragAmount != Offset.Zero) hasDraggedPuck = true
                val before = targetValue.fastRoundToInt()
                updateValue(
                    (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                        .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                )
                // One tick per tab crossed, which is the only moment in a drag
                // that means anything: the puck is continuous and the thing it is
                // choosing is not. Read off the rounded value rather than off a
                // threshold of our own, so what the finger feels is the same
                // instant the puck would settle on if it were let go.
                val after = targetValue.fastRoundToInt()
                if (after != before) crossingHaptics.play(FluidHapticEvent.Tick)
                animationScope.launch {
                    offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                }
            },
            // Critically damped, so the stretch tracks the drag instead of ringing
            // after it: the library's underdamped default is what made the puck
            // wobble as it crossed each tab.
        )
    }
    // False until this bar has run its selection sync once. Expanding back from
    // inline builds a brand new ExpandedBar (it is its own AnimatedContent
    // branch), so everything here re-remembers and this effect fires on first
    // composition — with the puck ALREADY on the right tab, because
    // dampedDragAnimation starts at currentIndex. animateToValue would then
    // press-grow and release the puck without moving it anywhere, launched into a
    // coroutine under mutatorMutex so it lands a frame or two into the crossfade:
    // the indicator appears to pop in late and twitch. On that first pass the
    // position is already correct, so there is nothing to animate.
    var hasSyncedSelection by remember(tabsCount) { mutableStateOf(false) }
    // Keeps the puck synced when selection changes from a tap (or external
    // navigation) rather than a drag on this bar.
    LaunchedEffect(selectedTabKey, tabsCount) {
        val index = allTabs.indexOfFirst { it.key == selectedTabKey }
        if (index != -1) {
            currentIndex = index
            if (!hasSyncedSelection) {
                hasSyncedSelection = true
                // Pin without the press animation; already in the right place.
                dampedDragAnimation.updateValue(index.toFloat())
                return@LaunchedEffect
            }
            // Re-pin on EVERY selection change, not only when currentIndex differs:
            // the puck's animated value can drift out from under a matching currentIndex
            // (a cancelled drag, an interrupted settle) and strand the indicator on the
            // wrong tab — the "selected icon doesn't update" bug. Forcing this here
            // always snaps it back to the actually-selected route.
            //
            // animateToValue (not updateValue): a tap should feel like a drag-release —
            // the same press/grow-then-settle the puck does when you actually drag it,
            // not just a bare position slide. This used to fall back to updateValue
            // because animateToValue's press-grow visibly doubled the selected icon
            // against the puck's glass copy — that's fixed now (the hidden backdrop row's
            // contentScale and padding are kept in sync with the visible row, see above).
            dampedDragAnimation.animateToValue(index.toFloat())
        }
    }

    val onDarkBar = GlassDefaults.isDarkSurface()
    val interactiveHighlight = remember(animationScope, onDarkBar) {
        GlassTouchHighlight(
            animationScope = animationScope,
            onDarkSurface = onDarkBar,
            position = { size, offset ->
                Offset(
                    if (isLtr) paddingStartPx + (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - paddingStartPx - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    // Invisible tinted copy of the tabs, sampled by the puck below so the
    // selected icon shows through the glass in the accent color.
    val tabsBackdrop = rememberLayerBackdrop()
    // Whether the accent-tinted copy of the tabs is worth keeping alive; see
    // where it is composed below.
    val tabsTinted = dampedDragAnimation.pressProgress > 0f

    Box(
        modifier
            .width(with(density) { totalWidthPx.toDp() })
            // The whole group travels, not just the pane.
            //
            // With the shared element on the row alone, the overlay drew that
            // row above everything in the layout for the length of the unfold —
            // and the indicator, an ordinary sibling underneath it, was hidden
            // until the overlay handed the row back. That is the pop: the
            // selection appeared at the end instead of growing with the bar.
            // Hoisted here, pane, indicator and icon keep the order they have
            // at rest and arrive together.
            .sharedElement(
                sharedContentState = rememberSharedContentState("tabGroup"),
                animatedVisibilityScope = animatedVisibilityScope,
                zIndexInOverlay = 1f
            ),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = panelOffset }
                .shadow(
                    shape = shapes.tabBarShape,
                    elevation = elevations.expandedElevation
                )
                .background(
                    color = colors.backgroundColor,
                    shape = shapes.tabBarShape
                )
                .clip(shapes.tabBarShape)
                .then(tabBarContentModifier)
                .then(interactiveHighlight.modifier)
                .padding(sizes.tabBarContentPadding),
            horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing)
        ) {
            allTabs.forEachIndexed { index, tab ->
                Tab(
                    icon = {
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    // Unselected tabs sit back; the selected one is
                                    // never dimmed, so the puck must not darken it.
                                    alpha = if (tab.key == selectedTabKey) 1f else 0.6f
                                }
                                .then(
                                    if (tab.key == selectedTabKey) {
                                        Modifier.sharedElement(
                                            sharedContentState = rememberSharedContentState("tab#${tab.key}-icon"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            zIndexInOverlay = 1f
                                        )
                                    } else {
                                        Modifier.animateEnterExitTab(
                                            sharedTransitionScope = this@ExpandedTabs,
                                            animatedVisibilityScope = animatedVisibilityScope
                                        )
                                    }
                                )
                        ) {
                            tab.icon()
                        }
                    },
                    title = {
                        Box(
                            Modifier.animateEnterExitTab(
                                sharedTransitionScope = this@ExpandedTabs,
                                animatedVisibilityScope = animatedVisibilityScope
                            )
                        ) {
                            tab.title()
                        }
                    },
                    isInline = false,
                    isStandalone = false,
                    contentScale = if (index == currentIndex) {
                        lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                    } else {
                        1f
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .skipToLookaheadSize()
                        .clip(shapes.tabShape)
                        .tapClickable(
                            indication = tab.indication?.invoke(),
                            onClick = tab.onClick
                        )
                        .padding(sizes.tabExpandedContentPadding)
                )
            }
        }

        // LOCAL CHANGE: only while the puck is being touched.
        //
        // This is a second, invisible copy of the whole tab row, kept so the
        // selected icon can show through the puck's glass in the accent colour.
        // It is also a full row composed, measured and recorded into a layer for
        // every frame the bar is on screen, which is every frame of every scroll:
        // measured at 8ms a frame on the home page, for a tint that only exists
        // while a finger is on the puck. Composed when that happens instead.
        if (backdrop != null && tabsTinted) {
            // Fixed 1dp blur here (see the blur() call below), so this is a
            // stable fraction, not something that needs recomputing per frame.
            //
            // Passed to `backdropScale` and nowhere else. In the fork this row
            // came from, `backdropScale` shrank only the recorded *size* and the
            // caller had to pre-multiply every effect parameter by it to match;
            // here the effect scope is handed a scaled density, so the same
            // multiplications would land squared. It compiles either way and it
            // shows up only as glass that looks wrong.
            val tabsBackdropScale = glassResolutionScale(1f)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    // Padding here (not after drawBackdrop, like the tail
                    // .padding below) so it actually shrinks this row's own
                    // measured bounds — matching LiquidBottomTabs' explicit
                    // .height(56dp) vs the main row's 64dp: what tabsBackdrop
                    // captures needs to be genuinely inset, not full-bleed.
                    .padding(sizes.tabBarContentPadding)
                    // Ported from LiquidBottomTabs: this hidden row isn't just a
                    // tinted icon cutout, it's a real blurred/lensed glass sample
                    // of the screen behind the bar — that's what makes the puck
                    // below look like it's made of the *bar's* frosted material
                    // (not a flat color) when it slides underneath.
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { shapes.tabBarShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            // Without any blur here, this hidden row's lens()
                            // distortion (below) samples the raw, sharp backdrop —
                            // that's what made the puck look like undistorted,
                            // "blur gone" background while dragging. Kyant's source
                            // uses a fixed 8dp; we drive it from the user's own
                            // glass blur-intensity setting instead of hardcoding.
                            blur(glassConfig.blurRadius.toPx())
                            lens(
                                15f.dp.toPx() * progress,
                                18f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(colors.backgroundColor) },
                        backdropScale = tabsBackdropScale,
                        frozen = LocalFluidTabBarBackdropFrozen.current,
                        // Same GPU-saving trick as Modifier.liquidGlass: record this
                        // hidden row at a fraction of surface resolution and let the
                        // blur hide the upscale — was left at the 1f (full-res)
                        // default here even though liquidGlass already uses this.
                    )
                    .then(interactiveHighlight.modifier)
                    // NOT a second .padding(sizes.tabBarContentPadding) here — the one
                    // above (before drawBackdrop) already insets this row's measured
                    // bounds. A second one compounded the inset only on this hidden
                    // row (the visible row above applies it exactly once), shifting
                    // its tabs right of their real counterparts — the puck's sampled
                    // icon then visibly drifted from the real icon under it.
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                horizontalArrangement = Arrangement.spacedBy(sizes.tabSpacing)
            ) {
                allTabs.forEachIndexed { index, tab ->
                    Tab(
                        icon = tab.icon,
                        title = tab.title,
                        isInline = false,
                        isStandalone = false,
                        // Must match the visible row's per-tab contentScale (see
                        // above) — this hidden row is what the puck's glass samples,
                        // so if it doesn't zoom in sync with the real icon during a
                        // press/drag, the accent-tinted copy visibly drifts from the
                        // real icon on top, reading as a ghosted double image.
                        contentScale = if (index == currentIndex) {
                            lerp(1f, 1.12f, dampedDragAnimation.pressProgress)
                        } else {
                            1f
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Must match the visible row's skipToLookaheadSize() too
                            // (see below) — without it this hidden row can measure a
                            // hair differently under the shared-transition lookahead
                            // pass, leaving the puck's sampled icon slightly off from
                            // the real one even at rest.
                            .skipToLookaheadSize()
                            .padding(sizes.tabExpandedContentPadding)
                    )
                }
            }
        }

        // The draggable selection puck. Position and press/velocity-driven
        // scale come from dampedDragAnimation; tapping a tab still works via
        // each tab's own clickable — the puck only intercepts drags.
        //
        // LOCAL CHANGE: it grows in with the tabs it belongs to. The tab group is
        // a shared element and travels between the two states; the puck is not
        // part of it, so coming back from the folded bar it simply appeared,
        // final size and full strength, over tabs that were still on their way.
        //
        // A fade alone was not enough to fix that, and it is worth saying why: at
        // 160ms over a fold that takes twice as long, what you see is a
        // fully-formed puck arriving early and quickly — which is the same
        // complaint. It has to be the same length as the movement it belongs to,
        // and it has to move: the scale is what makes it read as the puck
        // settling onto the tab rather than being switched on there.
        // Driven from this composable's own first frame rather than from the
        // enter transition, which was the second wrong answer here and is worth
        // recording. Hung off `animatedVisibilityScope.transition`, the animation
        // starts at whatever the transition already is — and by the time the
        // expanded bar composes its puck, the transition has usually moved on, so
        // the value began at 1 and nothing animated at all. An Animatable started
        // on composition cannot miss its own beginning.
        val puckEnter = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            puckEnter.animateTo(1f, tween(durationMillis = PuckEnterMs, easing = FastOutSlowInEasing))
        }
        // See the `frozen` argument below: the puck may only hold a capture once
        // it has one. A lambda so flipping it costs no recomposition — the glass
        // asks during draw.
        val tabBarFrozen = LocalFluidTabBarBackdropFrozen.current
        val puckFrozen = remember(tabBarFrozen, puckEnter) {
            { tabBarFrozen() && puckEnter.value >= 1f }
        }
        Box(
            Modifier
                .padding(sizes.tabBarContentPadding)
                .graphicsLayer {
                    val enter = puckEnter.value
                    alpha = enter
                    val enterScale = lerp(PuckEnterScale, 1f, enter)
                    scaleX = enterScale
                    scaleY = enterScale
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidthPx + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .width(sizes.tabWidth)
                .fillMaxHeight()
                // The puck's shape in plain paint, under the glass, while it is
                // arriving. Fading a pane of glass in is fading in something that
                // has nothing to show yet: a backdrop-sampling surface has no
                // capture on its first frames, so for the length of the entrance
                // the animation was running over an empty pill and the puck still
                // looked like it appeared all at once, at the end. This is what
                // grows into place; the glass takes over from underneath it as
                // soon as it has something to refract, and both draw the same
                // wash, so the hand-over is not a change of colour.
                .drawBehind {
                    val enter = puckEnter.value
                    if (enter < 1f) {
                        drawOutline(
                            shapes.tabShape.createOutline(size, layoutDirection, this),
                            color = puckWash.copy(alpha = puckRestAlpha * (1f - enter)),
                        )
                    }
                }
                .then(
                    if (backdrop != null) {
                        Modifier.drawBackdrop(
                            // Only combined while the hidden row above exists.
                            // An empty layer has no version for the surface to
                            // compare against, so sampling one makes the puck
                            // re-record the screen on every single frame — which
                            // is worse than the row it was meant to save.
                            backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
                                .takeIf { tabsTinted } ?: backdrop,
                            shape = { shapes.tabShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                // Soft frosted puck at rest (constant blur), deepening
                                //
                                blur(3f.dp.toPx() * (1f - progress))

                                // Refraction is weakest at REST and deepens while the
                                // puck is held and dragged: at rest the sharp overlay
                                // icon sits on top at full alpha, and any real bend
                                // there splits it from its lensed copy underneath —
                                // the doubled/ghosted icon. That overlay fades out
                                // over the first third of the press ramp, so by the
                                // time the bend is at full strength there is only one
                                // copy left to warp.
                                lens(
                                    lerp(0f.dp.toPx(), 10f.dp.toPx(), progress),
                                    lerp(0f.dp.toPx(), 12f.dp.toPx(), progress),
                                    // Dispersion is a drag-only flourish. It costs a
                                    // second, heavier shader, so a puck sitting still
                                    // should not be paying for it.
                                    chromaticAberration = progress > 0.01f
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                // Floored, not 0-at-rest. With no rim the puck's
                                // shape came entirely from its dark wash below,
                                // so it vanished into the bar whenever the bar
                                // itself sat on dark content. A specular rim
                                // reads against light AND dark, and unlike
                                // lightening the wash it leaves the selected
                                // icon (white by default, drawn on top) legible.
                                Highlight.Default.copy(
                                    alpha = lerp(PuckRestHighlightAlpha, 1f, progress)
                                )
                            },
                            shadow = {
                                val progress = dampedDragAnimation.pressProgress
                                // Same reasoning: a little separation at rest so
                                // the puck reads as a raised element rather than
                                // a flat patch of the bar.
                                Shadow(alpha = lerp(PuckRestShadowAlpha, 1f, progress))
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(radius = 8f.dp * progress, alpha = progress)
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                // What shows inside the puck is the hidden tinted tab
                                // row sampled back through this glass, so anything
                                // painted here lands ON TOP of the selected icon.
                                // Black at any real strength crushes it — hence an
                                // accent wash that defines the pill's shape while
                                // leaving the icon at full strength, deepening only
                                // while pressed.
                                val progress = dampedDragAnimation.pressProgress

                                // Was a hardcoded near-black at 0.8 alpha, which on a
                                // dark bar made the whole puck read as a dark blob and
                                // on a light theme was simply wrong. Colour and resting
                                // opacity are configurable now, and the unset default
                                // follows the theme instead of assuming dark.
                                drawRect(puckWash.copy(alpha = puckRestAlpha * (1f - 0.75f * progress)))
                            },
                            // LOCAL CHANGE, and this is what actually made the
                            // indicator appear out of nowhere. Freezing tells a
                            // surface to reuse its last capture instead of
                            // re-recording the screen while its bounds animate —
                            // which is right for the bar, and wrong for the puck:
                            // the puck does not exist while the bar is folded, so
                            // for the whole of the unfold it was reusing a capture
                            // it had never taken, and drew nothing at all. It
                            // arrived the instant the transition ended and the
                            // freeze lifted, at full strength, on the frame after
                            // everything else had finished moving. So it records
                            // for the frames it is arriving on, and freezes with
                            // the rest of the bar once it is there.
                            frozen = puckFrozen,
                        )
                    } else {
                        Modifier
                            .graphicsLayer {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY
                            }
                            .shadow(
                                shape = shapes.tabShape,
                                elevation = elevations.expandedElevation * dampedDragAnimation.pressProgress
                            )
                            .background(
                                colors.backgroundColor.copy(
                                    alpha = 0.5f + 0.5f * dampedDragAnimation.pressProgress
                                ),
                                shapes.tabShape
                            )
                            .clip(shapes.tabShape)
                    }
                )
        )

        // The selected icon, drawn a second time above the puck.
        //
        // What shows *through* the puck is the hidden accent-tinted tab row
        // refracted by the glass, and the puck's surface tint necessarily paints
        // over it — so that copy can never be at full opacity. Redrawing the icon
        // here is the only way to keep it crisp.
        //
        // It rides the puck's own translation and squash, so the two cannot drift
        // apart mid-drag, and it always covers the dimmed copy underneath rather
        // than ghosting beside it. Which icon to draw is derived state, so this
        // recomposes when the puck crosses into a new tab, not every frame.
        val puckIndex by remember(tabsCount) {
            derivedStateOf {
                dampedDragAnimation.value
                    .fastRoundToInt()
                    .coerceIn(0, (tabsCount - 1).coerceAtLeast(0))
            }
        }
        Box(
            Modifier
                .padding(sizes.tabBarContentPadding)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidthPx + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidthPx + panelOffset
                    scaleX = dampedDragAnimation.scaleX
                    scaleY = dampedDragAnimation.scaleY
                    // Settled state only. Fades roughly three times faster than the
                    // press ramp, so it is fully gone a third of the way into the
                    // gesture instead of lingering over the moving puck. Read in the
                    // draw phase, so it costs no recomposition.
                    //
                    // And it rides the indicator's own entrance: arriving at full
                    // strength over a puck still growing under it read as two
                    // things appearing at different times.
                    alpha = (1f - dampedDragAnimation.pressProgress * 3f)
                        .fastCoerceIn(0f, 1f) * puckEnter.value
                }
                .width(sizes.tabWidth)
                .fillMaxHeight()
                // Purely decorative: the tab underneath keeps the click and the
                // semantics, and this must never swallow either.
                .clearAndSetSemantics {},
        ) {
            currentTabs.value.getOrNull(puckIndex)?.let { tab ->
                // Must go through Tab, not just tab.icon(): Tab is a Column of
                // icon THEN title, so drawing the icon alone centres it on the
                // puck while the row centres it on the icon+title pair — which is
                // exactly how far off it looked. Same props as the row's selected
                // tab, so the two line up.
                Tab(
                    // No tint override: the icon keeps the selected colour its
                    // caller gave it, which is the point of drawing it again.
                    icon = { tab.icon() },
                    title = { tab.title() },
                    isInline = false,
                    isStandalone = false,
                    // No press zoom: only visible at rest, where the row's own lerp
                    // lands on 1f. Leaving it out also stops the overlay recomposing
                    // on every frame of a drag.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sizes.tabExpandedContentPadding),
                )
            }
        }
    }
}

@Composable
private fun Tab(
    icon: @Composable () -> Unit,
    title: @Composable () -> Unit,
    isInline: Boolean,
    modifier: Modifier = Modifier,
    isStandalone: Boolean = false,
    // Vendored addition: press-zoom scale for the icon+label, driven by the
    // drag puck's press progress (Kyant's LocalLiquidBottomTabScale trick).
    contentScale: Float = 1f
) {
    val showTitle = !isStandalone && !isInline
    Column(
        // A real gap between icon and title, centered as a group — rather than
        // nudging the icon down with a raw offset(), which shifts its drawn
        // position without growing the Column's measured height and gets it
        // clipped by the tab bar's own bounds.
        verticalArrangement = if (showTitle) {
            Arrangement.spacedBy((-2).dp, Alignment.CenterVertically)
        } else {
            Arrangement.Center
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(
                if (contentScale != 1f) {
                    Modifier.graphicsLayer {
                        scaleX = contentScale
                        scaleY = contentScale
                    }
                } else {
                    Modifier
                }
            )
    ) {
        icon()
        if (showTitle) {
            CompositionLocalProvider(
                LocalTextStyle provides FluidFloatingTabBarDefaults.labelStyle()
            ) {
                OneLine { title() }
            }
        }
    }
}

/**
 * A slot that a label cannot wrap in.
 *
 * Imposing a smaller text style stops the ordinary word from wrapping, and that is most of the
 * problem; it is not all of it, because "Impostazioni" in a five-tab bar on a 360 dp phone still
 * does not fit in the fifty-odd dp a tab gets, and one wrapped label anywhere in the row makes the
 * whole bar taller. Clipping it would be the cheap answer and it is the wrong one — the label is
 * how somebody who does not recognise the icon knows what the tab is.
 *
 * So the child is measured at unbounded width, which is the width it wants on one line, and then
 * scaled down to the width it has. Down to [MinLabelScale]; past that the word is simply too long
 * to be a tab label and it gets cut, which is the honest outcome and the rare one. The height that
 * comes out is always one line's, whatever the text, so nothing here can move the bar.
 */
@Composable
private fun OneLine(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val free = Constraints(maxHeight = constraints.maxHeight)
        val placeables = measurables.map { it.measure(free) }
        if (placeables.isEmpty()) return@Layout layout(0, 0) {}
        val wanted = placeables.maxOf { it.width }
        val room = constraints.maxWidth
        val scale = labelScale(wanted = wanted, room = room)
        val width = minOf(room, (wanted * scale).fastRoundToInt())
        val height = (placeables.maxOf { it.height } * scale).fastRoundToInt()
        layout(width, height) {
            placeables.forEach { placeable ->
                placeable.placeWithLayer(
                    x = (width - placeable.width) / 2,
                    y = (height - placeable.height) / 2,
                ) {
                    scaleX = scale
                    scaleY = scale
                }
            }
        }
    }
}

/** How far a label may be shrunk to stay on one line before it is cut instead. */
internal const val MinLabelScale = 0.75f

/**
 * How much a one-line label has to shrink to fit the slot it was given.
 *
 * Out here rather than inside the layout because the interesting cases are arithmetic — a label
 * that fits, one that is a little over, one that is hopeless — and none of them need a device.
 */
internal fun labelScale(wanted: Int, room: Int): Float =
    if (room in 1..<wanted) (room.toFloat() / wanted).coerceAtLeast(MinLabelScale) else 1f

/**
 * The width one expanded tab actually gets.
 *
 * [preferred] is a ceiling and never a promise: the bar gets the width the screen has, and what is
 * left after the standalone circle and the pill's own padding is what the tabs divide between
 * them. Returning [preferred] unchanged when there is nothing measured yet is deliberate — the
 * first frame has no measurement, and the weighted child in the row is what holds it together
 * until one arrives.
 */
internal fun fittedTabWidth(
    barWidth: Dp,
    standaloneWidth: Dp,
    gap: Dp,
    inset: Dp,
    tabsCount: Int,
    preferred: Dp,
): Dp {
    if (tabsCount <= 0 || barWidth <= 0.dp) return preferred
    val room = (barWidth - standaloneWidth - gap - inset) / tabsCount
    return if (room < preferred) room.coerceAtLeast(0.dp) else preferred
}

/**
 * A custom modifier that provides smooth enter/exit animations without clipping shadows or other content.
 * This is an alternative to animateEnterExit that uses renderInSharedTransitionScopeOverlay to prevent clipping.
 */
@Composable
private fun Modifier.animateEnterExitAccessory(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val animatedAlpha by transition.animateFloat { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        this@animateEnterExitAccessory
            .renderInSharedTransitionScopeOverlay()
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.ModulateAlpha,
                alpha = animatedAlpha
            )
    }
}

/**
 * A custom modifier that provides smooth enter/exit animations with fade and blur effects.
 */
@Composable
private fun Modifier.animateEnterExitTab(
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
): Modifier = with(sharedTransitionScope) {
    with(animatedVisibilityScope) {
        val enterStartFraction = 0.5f
        val enterEndFraction = 0.8f
        val durationMs = 150

        val animatedAlpha by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        0f atFraction enterStartFraction using FastOutSlowInEasing
                        1f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 1f
                else -> 0f
            }
        }

        val blurRadius = with(LocalDensity.current) { 50.dp.toPx() }
        val animatedBlur by transition.animateFloat(
            transitionSpec = {
                keyframes {
                    durationMillis = durationMs
                    if (targetState == EnterExitState.Visible) {
                        blurRadius atFraction enterStartFraction using FastOutSlowInEasing
                        0f atFraction enterEndFraction
                    }
                }
            }
        ) { targetState ->
            when (targetState) {
                EnterExitState.Visible -> 0f
                else -> blurRadius
            }
        }

        graphicsLayer {
            alpha = animatedAlpha
            renderEffect = BlurEffect(
                radiusX = animatedBlur,
                radiusY = animatedBlur
            )
        }
    }
}

private class FluidFloatingTabBarScopeImpl : FluidFloatingTabBarScope {
    val tabs = mutableStateListOf<FluidFloatingTabBarTab>()
    var standaloneTab: FluidFloatingTabBarTab? by mutableStateOf(null)
        private set
    private var inlineTab: FluidFloatingTabBarTab? = null

    fun getInlineTab(selectedTabKey: Any?): FluidFloatingTabBarTab? {
        // Screen-accurate: only ever report a regular tab as "the inline tab"
        // when it's actually the current screen. selectedTabKey not matching
        // any regular tab (e.g. currently on the standalone tab) falls back to
        // the last regular tab that really was selected — never a hardcoded
        // default like tabs.firstOrNull(), which would misrepresent Home as
        // selected when the user has never actually been there.
        val selectedTab = tabs.find { it.key == selectedTabKey }
        if (selectedTab != null) {
            inlineTab = selectedTab
            return selectedTab
        }
        return inlineTab
    }

    override fun tab(
        key: Any,
        title: @Composable () -> Unit,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?
    ) {
        tabs.add(
            FluidFloatingTabBarTab(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                indication = indication
            )
        )
    }
    
    override fun standaloneTab(
        key: Any,
        icon: @Composable () -> Unit,
        onClick: () -> Unit,
        indication: (@Composable () -> Indication)?,
        title: @Composable () -> Unit
    ) {
        standaloneTab = FluidFloatingTabBarTab(
            key = key,
            title = title,
            icon = icon,
            onClick = onClick,
            indication = indication
        )
    }
}

private data class FluidFloatingTabBarTab(
    val key: Any,
    val title: @Composable () -> Unit,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
    val indication: (@Composable () -> Indication)?
)

/**
 * Represents the colors used in [FluidFloatingTabBar].
 */
@Immutable
data class FluidFloatingTabBarColors(
    val backgroundColor: Color,
    val accessoryBackgroundColor: Color,
)

/**
 * Represents the shapes used in [FluidFloatingTabBar].
 */
@Immutable
data class FluidFloatingTabBarShapes(
    val tabBarShape: Shape,
    val tabShape: Shape,
    val standaloneTabShape: Shape,
    val accessoryShape: Shape,
)

/**
 * Represents the elevations used in [FluidFloatingTabBar].
 */
@Immutable
data class FluidFloatingTabBarElevations(
    val inlineElevation: Dp,
    val expandedElevation: Dp,
)

/**
 * Represents the sizes and spacing used in [FluidFloatingTabBar].
 */
@Immutable
data class FluidFloatingTabBarSizes(
    val tabBarContentPadding: PaddingValues,
    val tabInlineContentPadding: PaddingValues,
    val tabExpandedContentPadding: PaddingValues,
    val componentSpacing: Dp,
    val tabSpacing: Dp,
    // Vendored addition: fixed width of one expanded tab slot. The drag-puck
    // indicator needs a known per-tab width to map drag offset to tab index,
    // so expanded tabs are equal-width instead of sized to their content.
    val tabWidth: Dp = FluidFloatingTabBarDefaults.TabWidth,
    /** How tall the open bar stands. A floor, so large text can still grow it. */
    val barHeight: Dp = FluidFloatingTabBarDefaults.OpenHeight,
    /** The same, folded — where there is an icon and no label. */
    val inlineHeight: Dp = FluidFloatingTabBarDefaults.FoldedHeight,
)

/**
 * Contains the default values used by [FluidFloatingTabBar].
 */
object FluidFloatingTabBarDefaults {
    /**
     * Width of one expanded tab slot at full size. Callers that have to fit the
     * pill plus the standalone tab into a narrow screen shrink below this — see
     * [com.convx.music.ui.component.AppFloatingNavBar] — so it is the ceiling,
     * not a guarantee.
     */
    val TabWidth: Dp = 88.dp

    /**
     * How tall the bar stands when it is open.
     *
     * A number the bar now actually keeps. It used to be advisory — nothing enforced it, the bar
     * took whatever its content asked for, and with labels at body size wrapping onto two lines
     * that came to about ninety dp against a constant that said sixty-four. So every screen using
     * [ContentInset] left a third less room than the bar occupied, and the bar itself read as a
     * slab rather than a pill.
     *
     * Sixty is what the content comes to: four of the bar's own padding, six of the tab's, a
     * twenty-four dp icon, and one line of [labelStyle] under it.
     */
    val OpenHeight: Dp = 60.dp

    /** How tall it stands once a scroll has folded it back to its inline shape. */
    val FoldedHeight: Dp = 52.dp

    /** The gap between the bar and the accessory band above it. */
    val Spacing: Dp = 8.dp

    /** Above the system's gesture handle, not against it. */
    val BottomMargin: Dp = 16.dp

    /** The bar's side margin, which is a screen's gutter and not the bar's own. */
    val HorizontalMargin: Dp = 14.dp

    /** Vertical space a screen must leave free so its content clears the bar at its tallest. */
    val ContentInset: Dp = OpenHeight + BottomMargin

    /** The same, for a bar carrying an accessory band — a now-playing pill, say. */
    fun contentInsetWithAccessory(accessoryHeight: Dp): Dp =
        ContentInset + accessoryHeight + Spacing

    /**
     * Creates a [FluidFloatingTabBarColors] that represents the default colors used in a [FluidFloatingTabBar].
     *
     * @param backgroundColor the color used for the tab bar background
     * @param accessoryBackgroundColor the color used for the accessory background
     */
    @Composable
    fun colors(
        backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        accessoryBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ): FluidFloatingTabBarColors = FluidFloatingTabBarColors(
        backgroundColor = backgroundColor,
        accessoryBackgroundColor = accessoryBackgroundColor,
    )

    /**
     * Creates a [FluidFloatingTabBarShapes] that represents the default shapes used in a [FluidFloatingTabBar].
     *
     * @param tabBarShape the shape used to clip the tab bar
     * @param tabShape the shape used to clip individual tabs. Can be useful for example to control the click ripple effect shape
     * @param standaloneTabShape the shape used to clip the standalone tab
     * @param accessoryShape the shape used to clip the accessory container
     */
    @Composable
    fun shapes(
        // FluidCapsuleShape instead of RoundedCornerShape/
        // CircleShape: same capsule/circle silhouette, but Kyant0/Capsule's
        // continuous (superellipse) corners instead of Android's circular-arc
        // ones — smoother, and lerp-able for the puck's drag-to-search morph.
        tabBarShape: Shape = FluidCapsuleShape,
        tabShape: Shape = FluidCapsuleShape,
        standaloneTabShape: Shape = FluidCapsuleShape,
        accessoryShape: Shape = FluidCapsuleShape,
    ): FluidFloatingTabBarShapes = FluidFloatingTabBarShapes(
        tabBarShape = tabBarShape,
        tabShape = tabShape,
        standaloneTabShape = standaloneTabShape,
        accessoryShape = accessoryShape,
    )

    /**
     * Creates a [FluidFloatingTabBarSizes] that represents the default sizes used in a [FluidFloatingTabBar].
     *
     * @param tabBarContentPadding the padding applied to the tab bar content. This also applies to the standalone tab content.
     * @param tabInlineContentPadding the padding applied to tabs in inline state
     * @param tabExpandedContentPadding the padding applied to tabs in expanded state
     * @param componentSpacing the spacing between components
     * @param tabSpacing the spacing between tabs in expanded state
     */
    @Composable
    fun sizes(
        tabBarContentPadding: PaddingValues = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
        tabInlineContentPadding: PaddingValues = PaddingValues(10.dp),
        // horizontal was 20dp (the source lib's default for content-hugging
        // tabs); with fixed-width tabWidth cells that ate too much of the
        // budget and clipped labels like "Settings" — tightened to fit text.
        tabExpandedContentPadding: PaddingValues = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
        componentSpacing: Dp = 8.dp,
        tabSpacing: Dp = 0.dp,
        tabWidth: Dp = TabWidth,
        barHeight: Dp = OpenHeight,
        inlineHeight: Dp = FoldedHeight,
    ): FluidFloatingTabBarSizes = FluidFloatingTabBarSizes(
        tabBarContentPadding = tabBarContentPadding,
        tabInlineContentPadding = tabInlineContentPadding,
        tabExpandedContentPadding = tabExpandedContentPadding,
        componentSpacing = componentSpacing,
        tabSpacing = tabSpacing,
        tabWidth = tabWidth,
        barHeight = barHeight,
        inlineHeight = inlineHeight,
    )

    /**
     * What a tab's label is written in.
     *
     * The bar imposes this rather than inheriting whatever text style is in scope, and that is the
     * whole fix for the bar that was too tall. A caller writes `title = { Text(item.label) }` —
     * the obvious thing to write — and `Text` with no style takes `bodyLarge`: sixteen points, a
     * twenty-four point line. Five of those across a phone and "Bacheca" wraps, the wrap doubles
     * the label's height, and the bar grows thirty dp to hold a word that had simply been set too
     * big. A navigation label is a label; thirteen points is what one is.
     *
     * Only the size and the weight are imposed. A caller that sets a colour on its own `Text`
     * still wins, because the colour is the one thing a tab bar's owner legitimately varies.
     */
    @Composable
    fun labelStyle(): TextStyle = MaterialTheme.typography.labelMedium.copy(
        textAlign = TextAlign.Center,
    )

    /**
     * Creates a [FluidFloatingTabBarElevations] that represents the default elevations used in a [FluidFloatingTabBar].
     *
     * @param inlineElevation the elevation used for tabs in inline state
     * @param expandedElevation the elevation used for tabs in expanded state
     */
    @Composable
    fun elevations(
        inlineElevation: Dp = 6.dp,
        expandedElevation: Dp = 12.dp,
    ): FluidFloatingTabBarElevations = FluidFloatingTabBarElevations(
        inlineElevation = inlineElevation,
        expandedElevation = expandedElevation,
    )
}