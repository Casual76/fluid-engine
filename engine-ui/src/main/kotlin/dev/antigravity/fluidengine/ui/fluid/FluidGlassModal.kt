package dev.antigravity.fluidengine.ui.fluid

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.glass.interaction.GlassTouchHighlight
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/**
 * A modal that lives *inside* the app's composition.
 *
 * The reason this exists is a system boundary, not a shortcoming. [FluidSheet] is a `ModalBottomSheet`
 * and [FluidAlert] is a `Dialog`: both open a **separate platform window**, and a separate window
 * cannot read the app's `GraphicsLayer`. That is why they fall back to painted gradients, and it is
 * not fixable from this side. A pop-up that keeps the page visible and *refracted* behind it has to
 * be composed with the page.
 *
 * The precedent was already here and already shipping: [FluidNotificationHost] is an in-root overlay
 * that takes a [GlassBackdropState] and draws real glass. This is the same shape, with three things
 * it needs that a notification does not — a scrim, an expansion from the thing you touched, and a way
 * out.
 *
 * The awkward part is that the *feature* wants to declare the modal where its data lives (inside the
 * `NavHost`, next to the screen state) while the *pixel* has to come out above the tab bar. So
 * [FluidGlassModalPortal] declares and does not draw: it hands its content lambda to the host and the
 * host runs it at the root. The lambda executes inside the host's composition and reads the same
 * snapshot state the feature reads, so it stays live without anything being pushed through it.
 */
@Stable
class FluidGlassModalHostState internal constructor() {

  internal val entries = mutableStateListOf<FluidGlassModalEntry>()
  private var sequence = 0

  /** Whether anything is currently open. An app can use it to obscure the page behind. */
  val isPresenting: Boolean get() = entries.any { it.visible }

  /**
   * Whether anything still has pixels on screen, the exit animation included.
   *
   * [isPresenting] turns false the instant a dismiss is *requested*, which is the right answer for
   * accessibility and the wrong one for anything the modal is borrowing from the page: give a
   * lifted row back at that moment and it reappears underneath a pane that is still folding into
   * it, for the whole length of the exit.
   */
  val isOnScreen: Boolean get() = entries.any { it.visible || it.lifted }

  internal fun register(entry: FluidGlassModalEntry) {
    if (entries.none { it === entry }) entries.add(entry)
  }

  internal fun unregister(entry: FluidGlassModalEntry) {
    entries.removeAll { it === entry }
  }

  /** The innermost open modal: the most recently *opened* one, not the most recently declared. */
  internal fun topEntry(): FluidGlassModalEntry? =
    entries.filter { it.visible }.maxByOrNull { it.openedAt }

  internal fun nextSequence(): Int = ++sequence

  /** Asks whatever is innermost to close. This is what back and a tap on the scrim call. */
  fun dismissTop() {
    topEntry()?.onDismissRequest?.invoke()
  }
}

@Composable
fun rememberFluidGlassModalHostState(): FluidGlassModalHostState =
  remember { FluidGlassModalHostState() }

/** Lets any feature declare a modal without owning, or duplicating, the single root host. */
val LocalFluidGlassModalHostState: ProvidableCompositionLocal<FluidGlassModalHostState?> =
  staticCompositionLocalOf { null }

/** How a modal arranges itself once it has finished arriving. */
enum class FluidGlassModalPresentation {
  /**
   * A pop-up that opens **on the thing you touched**, sized to whatever it contains.
   *
   * The default, and the one this whole file exists for. It is not a dialog that happens to have a
   * source rectangle: it is placed against that rectangle, it grows out of the corner nearest it,
   * and it takes the width and height its content asks for rather than a fixed card size. A modal
   * that appears in the middle of the screen has already lost the connection to the tap that opened
   * it, and nothing done to it afterwards gets that back.
   */
  Popover,

  /** Anchored to the bottom edge, full width. For something tall enough to want the whole height. */
  Sheet,

  /** A list of actions anchored to the thing that was long-pressed. Placed by [fluidContextMenu]. */
  ContextMenu,

  /**
   * The pop-up **replaces** what opened it, in place.
   *
   * Where [Popover] appears *beside* the anchor, this one starts at exactly the anchor's rectangle
   * and grows out of it while the anchor itself steps aside — so what the eye follows is one object
   * changing size, not a second object arriving next to the first. It is the shape of a button that
   * unfolds into its own menu and folds back once something is chosen.
   */
  Expand,
}

/** One action in a context menu. */
@Immutable
data class FluidContextAction(
  val label: String,
  val icon: ImageVector? = null,
  /** Deletes, revokes, removes. Drawn in the error colour, and worth at most one per menu. */
  val destructive: Boolean = false,
  val enabled: Boolean = true,
  val onClick: () -> Unit,
)

@Stable
internal class FluidGlassModalEntry {
  var visible by mutableStateOf(false)
  var openedAt by mutableIntStateOf(0)
  var onDismissRequest: () -> Unit by mutableStateOf({})
  var origin: () -> Rect? by mutableStateOf({ null })
  var presentation by mutableStateOf(FluidGlassModalPresentation.Popover)
  var paneTitle: String? by mutableStateOf(null)
  var content: (@Composable ColumnScope.() -> Unit) by mutableStateOf({})

  /**
   * Whether the anchored element has been lifted into the overlay and must stop drawing itself.
   *
   * Separate from [visible] because it has to outlast it. It is set when the menu opens and cleared
   * only when the overlay has finished leaving — if the row came back the instant the dismiss was
   * requested, the last 130 ms of every exit would show the row and its own lifted copy at once,
   * one of them sliding.
   */
  var lifted by mutableStateOf(false)

  /** Context menus and expanding buttons: the actions to offer, and the picture of the row. */
  var preview: GraphicsLayer? by mutableStateOf(null)
  var previewSize: Rect? by mutableStateOf(null)
  var actions: List<FluidContextAction> by mutableStateOf(emptyList())
}

/**
 * Declares a modal here and draws it at the root.
 *
 * Nothing is emitted at the call site — this composable has no size and no pixels. It writes its
 * content into the host inside a `DisposableEffect` and takes it back out in `onDispose`, so a
 * feature that leaves the composition can never leave a modal stranded above the app.
 *
 * @param origin the rectangle to grow out of, in root coordinates — normally the bounds of the row
 *   that was tapped, captured with [fluidExpandOrigin]. Null opens from the centre.
 */
@Composable
fun FluidGlassModalPortal(
  visible: Boolean,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val host = LocalFluidGlassModalHostState.current ?: return
  val entry = remember { FluidGlassModalEntry() }

  // Written after composition succeeds rather than during it. The lambda is rebuilt on every
  // recomposition of the feature, so whatever it captured is never more than one frame old — and
  // what it *reads* is snapshot state, which the host subscribes to directly when it runs it.
  SideEffect {
    if (visible && !entry.visible) entry.openedAt = host.nextSequence()
    entry.visible = visible
    entry.onDismissRequest = onDismissRequest
    entry.origin = origin
    entry.presentation = presentation
    entry.paneTitle = paneTitle
    entry.content = content
  }

  DisposableEffect(host, entry) {
    host.register(entry)
    onDispose { host.unregister(entry) }
  }
}

/**
 * Declares a modal that presents **an item**, and stays whole while it leaves.
 *
 * This is the form to use when the modal shows a selected thing — a detail, a record, a note — and
 * "open" means "something is selected". Passing `visible = x != null` plus a lambda that reads `x`
 * to the other overload cannot survive its own dismissal: the compiler gives that lambda one
 * mutable instance whose captures are swapped in place, so on the frame the selection clears the
 * content composes to nothing and the pane would leave the screen as a hard cut, its text gone
 * while the scrim is still fading. Here the portal keeps the last non-null [item] itself and keeps
 * handing it to [content] for as long as the exit runs, so the pane leaves exactly as it stood.
 */
@Composable
fun <T : Any> FluidGlassModalPortal(
  item: T?,
  onDismissRequest: () -> Unit,
  origin: () -> Rect? = { null },
  presentation: FluidGlassModalPresentation = FluidGlassModalPresentation.Popover,
  paneTitle: String? = null,
  content: @Composable ColumnScope.(T) -> Unit,
) {
  var lastItem by remember { mutableStateOf(item) }
  if (item != null) lastItem = item
  FluidGlassModalPortal(
    visible = item != null,
    onDismissRequest = onDismissRequest,
    origin = origin,
    presentation = presentation,
    paneTitle = paneTitle,
  ) {
    lastItem?.let { content(it) }
  }
}

/**
 * Reports this element's bounds in root coordinates, for a modal to expand out of.
 *
 * The rectangle is what the eye was looking at when it tapped, and starting the pop-up anywhere else
 * costs the connection between the two. Cheap to leave on a row: it only reports when the row
 * actually moves.
 */
@Composable
fun Modifier.fluidExpandOrigin(
  /**
   * Whether the modal this element opens is on screen right now.
   *
   * While it is, the element **stops drawing**, and that is not tidiness: the pane that grew out of
   * it is glass, so the row underneath went on showing *through* it. Two rims one inside the other
   * where the card's edge should be, and the row's own title readable behind the pane's — which is
   * exactly what "the pop-up interpenetrates the button that opened it" looks like from the outside.
   *
   * The space stays reserved, because this is a draw and not a layout: nothing below the row moves
   * while the pane is up, so folding back into it lands on the same rectangle it left.
   */
  open: () -> Boolean = { false },
  onMeasured: (Rect) -> Unit,
): Modifier {
  val host = LocalFluidGlassModalHostState.current
  // Latched rather than read straight through, because the app's own "is this row's detail open"
  // goes false the moment the dismiss is requested, and the pane needs the rest of its exit to fold
  // back into a rectangle that is still empty.
  var lifted by remember { mutableStateOf(false) }
  val requested = open()
  val onScreen = host?.isOnScreen ?: false
  SideEffect {
    if (requested) lifted = true else if (!onScreen) lifted = false
  }
  return this
    .onGloballyPositioned { onMeasured(it.boundsInRoot()) }
    .drawWithContent { if (!(requested || lifted)) drawContent() }
}

/**
 * Hides everything under it from accessibility while a modal is open.
 *
 * A translucent pop-up that still lets the focus walk into the page behind it is *worse* than an
 * opaque one: the page is visible, so nothing warns the person that what they just landed on is not
 * reachable. Apply it to whatever the app puts under [FluidGlassModalHost].
 */
@Composable
fun Modifier.fluidGlassModalObscured(): Modifier {
  val host = LocalFluidGlassModalHostState.current ?: return this
  if (!host.isPresenting) return this
  return this.semantics { hideFromAccessibility() }
}

/**
 * The host. One per app, at the root, next to [FluidNotificationHost].
 *
 * @param backdrop what the scrim refracts: the chrome's backdrop, which contains the whole page.
 */
@Composable
fun FluidGlassModalHost(
  state: FluidGlassModalHostState,
  backdrop: GlassBackdropState?,
  modifier: Modifier = Modifier,
) {
  if (state.entries.isEmpty()) return
  Box(modifier = modifier.fillMaxSize()) {
    // Every registered entry, in the order they were opened, so a menu raised from inside a modal
    // sits over it instead of replacing it. An entry that is closed and finished animating draws
    // nothing at all and costs one float animation.
    state.entries.sortedBy { it.openedAt }.forEach { entry ->
      key(entry) { FluidGlassModalLayer(entry = entry, backdrop = backdrop) }
    }
  }
}

/**
 * One open modal: its scrim, its material, and the physics that gets it on screen.
 *
 * The motion is deliberately the tab bar indicator's, not a dialog's. Two springs, one per axis,
 * with **different damping ratios** — 0.62 across, 0.74 down — because a single scale spring
 * produces a balloon and two mismatched ones produce a squash. That squash is the entire difference
 * between something that appeared and something that *arrived*, and it is what makes this read as
 * the same material as the pill you can pick up and drag in the navigation bar.
 */
@Composable
private fun FluidGlassModalLayer(
  entry: FluidGlassModalEntry,
  backdrop: GlassBackdropState?,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // 0 = at the anchor's size, 1 = at its own. What "0" actually means in pixels is solved by the
  // layout below, per axis, from the two rectangles.
  val scaleX = remember { Animatable(0f) }
  val scaleY = remember { Animatable(0f) }
  val fade = remember { Animatable(0f) }

  LaunchedEffect(entry.visible, reducedMotion) {
    if (reducedMotion) {
      scaleX.snapTo(if (entry.visible) 1f else 0f)
      scaleY.snapTo(if (entry.visible) 1f else 0f)
      fade.snapTo(if (entry.visible) 1f else 0f)
      return@LaunchedEffect
    }
    if (entry.visible) {
      // Opacity arrives well before the geometry finishes. Tying them together makes the pop-up
      // fade *in* as it grows, which reads as a dissolve; separating them lets the shape do the
      // arriving while the content is already legible.
      launch { fade.animateTo(1f, FluidMotion.fadeIn(FluidPopoverFadeInMillis)) }
      launch { scaleX.animateTo(1f, spring(FluidPopoverDampingX, FluidPopoverStiffness, 0.001f)) }
      launch { scaleY.animateTo(1f, spring(FluidPopoverDampingY, FluidPopoverStiffness, 0.001f)) }
    } else {
      // Leaving is critically damped: an overshoot on the way out reads as the pop-up bouncing off
      // the screen rather than being put away. But not *stiff* — at 900 the pane crossed most of
      // its travel inside two frames while a 130 ms fade was still at full alpha, and what the eye
      // caught was a modal that vanished with its text and a scrim left standing alone. The exit
      // has to be one gesture: the pane shrinks back onto the row it came from *while* it fades,
      // and the scrim, which reads the same fade, leaves with it.
      launch { fade.animateTo(0f, FluidMotion.fadeOut(FluidPopoverFadeOutMillis)) }
      launch { scaleX.animateTo(0f, spring(1f, FluidPopoverExitStiffness, 0.001f)) }
      launch { scaleY.animateTo(0f, spring(1f, FluidPopoverExitStiffness, 0.001f)) }
    }
  }

  // Read through a lambda by whoever *draws*, never as a composed value. `fade.value` read here
  // subscribed this whole layer — scrim, lifted row, popover, its `Layout` — to every frame of
  // every arrival, and recomposing means re-measuring the modal's content: a paragraph-heavy
  // detail re-laid-out sixty times a second is precisely the stutter an entrance cannot have.
  // Composition needs exactly one coarse fact, and it is the boolean below.
  val presence: () -> Float = { fade.value }

  // `derivedStateOf` recomputes on every animation frame but invalidates composition only when
  // the boolean flips — once per exit, instead of once per frame.
  val exitFinished by remember {
    derivedStateOf { fade.value <= 0.001f && !fade.isRunning && !scaleY.isRunning }
  }

  // Everything the exit still needs is held from the last frame the modal was open. These are
  // *values* — an actions list, a rectangle, a title — and values survive a dismissal. What CANNOT
  // be preserved this way is the content composable: a composable lambda is one mutable holder per
  // call site whose block the compiler swaps in place, so the reference kept here IS the fresh
  // lambda, captures and all. What keeps the pane full during the exit is therefore the *data*
  // overload of [FluidGlassModalPortal], which freezes the last shown item and hands it back to the
  // very same lambda.
  var lastContent by remember { mutableStateOf(entry.content) }
  var lastActions by remember { mutableStateOf(entry.actions) }
  var lastPreview by remember { mutableStateOf(entry.preview) }
  var lastPreviewBounds by remember { mutableStateOf(entry.previewSize) }
  var lastOrigin by remember { mutableStateOf<Rect?>(null) }
  var lastPaneTitle by remember { mutableStateOf(entry.paneTitle) }
  if (entry.visible) {
    lastContent = entry.content
    lastActions = entry.actions
    lastPreview = entry.preview
    lastPreviewBounds = entry.previewSize
    lastOrigin = entry.origin()
    lastPaneTitle = entry.paneTitle
    // Not only a context menu lifts its row out of the page: anything that morphs out of an anchor
    // does, because the pane is glass and the anchor would otherwise be legible through it.
    entry.lifted = true
  }

  // The fade is short and the springs are not, and a sheet slides out on the springs. Dropping
  // the layer the moment the alpha reached zero cut every exit in half.
  if (!entry.visible && exitFinished) {
    // Whatever was lifted out of the page is handed back to it here, and not one frame earlier.
    SideEffect { entry.lifted = false }
    return
  }

  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(entry.visible) {
    if (entry.visible) runCatching { focusRequester.requestFocus() }
  }

  // Predictive back drives the same retreat the drag does, so letting go halfway puts the modal
  // back where it was instead of jumping.
  var backProgress by remember { mutableStateOf(0f) }
  PredictiveBackHandler(enabled = entry.visible) { events ->
    try {
      events.collect { backProgress = it.progress.coerceIn(0f, 1f) }
      backProgress = 0f
      entry.onDismissRequest()
    } catch (_: CancellationException) {
      backProgress = 0f
    }
  }

  val scrimGlass = rememberGlassBackdrop()
  // The pop-up refracts the page *as the scrim left it* — dimmed and lightly frosted — not the raw
  // page. Handing it the page directly punches a clear hole straight through the scrim, which is
  // exactly what it looks like.
  val popoverBackdrop = if (backdrop != null) {
    rememberCombinedGlassBackdrop(backdrop, scrimGlass)
  } else {
    null
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .semantics { isTraversalGroup = true }
      .focusGroup(),
  ) {
    FluidGlassModalScrim(
      backdrop = backdrop,
      exports = scrimGlass,
      intensity = { presence() * (1f - backProgress * 0.6f) },
      onDismiss = entry.onDismissRequest,
    )

    // An invisible focus target inside the overlay: without one, the first D-pad or keyboard move
    // after opening lands somewhere in the page underneath — the same failure the accessibility
    // rule on [fluidGlassModalObscured] is about.
    Box(
      modifier = Modifier
        .size(1.dp)
        .focusRequester(focusRequester)
        .focusable(),
    )

    if (entry.presentation == FluidGlassModalPresentation.Sheet) {
      FluidGlassModalSheet(
        paneTitle = lastPaneTitle,
        backdrop = popoverBackdrop,
        // Sheets arrive by *travelling*, not by fading: the spring is the animation, and the alpha
        // only exists so the last few pixels of the exit do not pop.
        slide = { scaleY.value },
        backProgress = { backProgress },
        reducedMotion = reducedMotion,
        onDismiss = entry.onDismissRequest,
        content = lastContent,
      )
      return@Box
    }

    val menu = entry.presentation == FluidGlassModalPresentation.ContextMenu
    val expand = entry.presentation == FluidGlassModalPresentation.Expand
    if (menu) {
      FluidLiftedPreview(
        preview = lastPreview,
        bounds = lastPreviewBounds,
        backdrop = popoverBackdrop,
        // Never faded. The real row hides the moment the menu opens and comes back only at
        // [exitFinished] — the preview is the row for that whole stretch, and fading it out on the
        // menu's clock opened a two-hundred-millisecond hole where the group pane showed through
        // with no text in it. At both ends of its life the preview sits at scale 1 exactly over the
        // row it recorded, so appearing and vanishing are pixel-identical swaps, not cuts.
        presence = { 1f },
        // The row is already its own size, so it only has the lift to travel: a few percent, on the
        // same springs, so the row and the menu move as one object.
        scaleX = { lerp(1f, FluidLiftedPreviewScale, scaleX.value) * (1f - backProgress * 0.05f) },
        scaleY = { lerp(1f, FluidLiftedPreviewScale, scaleY.value) * (1f - backProgress * 0.05f) },
      )
    }

    FluidAnchoredPopover(
      anchor = lastOrigin,
      paneTitle = lastPaneTitle ?: if (menu || expand) "Azioni" else null,
      backdrop = popoverBackdrop,
      compact = menu || expand,
      overAnchor = expand,
      presence = presence,
      growth = { scaleX.value },
      growthCross = { scaleY.value },
      retreat = { backProgress },
    ) {
      if (menu || expand) {
        lastActions.forEachIndexed { index, action ->
          if (index > 0) FluidHairline(startInset = 14.dp)
          FluidContextMenuRow(action = action, onDismiss = entry.onDismissRequest)
        }
      } else {
        lastContent()
      }
    }
  }
}

/**
 * The scrim.
 *
 * Almost nothing, and that is the decision. The temptation with a full-screen pane is to turn the
 * radius up until the page behind is unrecognisable — which does separate the pop-up, at the price
 * of throwing away the only reason to keep the modal inside the app's composition at all. A page
 * averaged into grey is not "visible behind": it is grey, and a painted rectangle would have got
 * there cheaper. So the frosting stays at a fraction of a bar's, the page keeps its colour, and the
 * separation is bought with a light drop in brightness plus the pop-up's own edge and shadow.
 *
 * Displacement is zero on purpose: a lens the size of the display bends the corners of the screen
 * and reads as a fisheye rather than as depth.
 */
@Composable
private fun FluidGlassModalScrim(
  backdrop: GlassBackdropState?,
  exports: GlassBackdropState,
  intensity: () -> Float,
  onDismiss: () -> Unit,
) {
  val dark = GlassDefaults.isDarkSurface()
  val tint = remember(dark) {
    GlassTint(
      overlay = Color.Black.copy(alpha = if (dark) 0.20f else 0.12f),
      // Below API 31 there is no blur at all, so the scrim stops pretending and becomes a wall. The
      // modal works identically; you simply cannot see through it.
      fallback = Color.Black.copy(alpha = 0.92f),
      hairline = Color.Transparent,
    )
  }
  val optics = remember {
    GlassDefaults.optics(GlassRole.Modal).copy(
      // Two dp times two. Enough that the page reads as *behind* something rather than as
      // merely darker, and far too little to average it away.
      blurScale = 1.5f,
      refractionHeight = 0.dp,
      refractionAmount = 0.dp,
      vibrancy = 1f,
      highlightAlpha = 0f,
      innerShadowAlpha = 0f,
      shadowAlpha = 0f,
    )
  }

  val base = Modifier
    .fillMaxSize()
    // The arrival is an alpha on the finished pane, **not** the material's own `intensity`.
    //
    // `intensity` scales the blur radius, and a changing blur radius changes the padding the layer
    // needs, and a changing padding is the one thing that genuinely invalidates a capture. Ramping
    // it therefore re-records the whole screen and re-runs the entire effect chain on every frame of
    // the fade — on the largest surface in the system, at the exact moment something is trying to
    // animate. Fading the result instead costs one composited layer and looks identical.
    .graphicsLayer { alpha = intensity().coerceIn(0f, 1f) }
    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }

  if (backdrop == null) {
    Box(
      modifier = base.drawWithContent {
        drawRect(tint.fallback)
        drawContent()
      },
    )
  } else {
    Box(
      modifier = base.glassSurface(
        state = backdrop,
        tint = tint,
        role = GlassRole.Modal,
        optics = optics,
        exports = exports,
      ),
    )
  }
}

/**
 * Where the pop-up ended up, and the rectangle it grew out of.
 *
 * Written by the measure pass and read by the draw pass. Kept in one object rather than in
 * separate states because the start geometry and the position are one decision: split them and
 * there is a frame where the pop-up is already in its new place and still growing out of its old
 * rectangle.
 */
@Stable
private class FluidPopoverPlacement {
  var pivotX by mutableStateOf(0.5f)
  var pivotY by mutableStateOf(0.5f)

  /**
   * How small the pop-up starts, per axis, as a fraction of its finished size.
   *
   * Solved from the anchor rather than fixed, because "grows out of the button" is a statement about
   * two rectangles and not about a number: a wide button opening a narrow menu has to start wide,
   * and a constant makes that read as a zoom from somewhere else.
   */
  var startScaleX by mutableStateOf(FluidPopoverStartScale)
  var startScaleY by mutableStateOf(FluidPopoverStartScale)

  /**
   * Where the pop-up's top-left corner sits at the start of its travel, relative to where it ends —
   * which, morphing from an anchor, is the anchor's own corner. Zero for the pivot-based growth
   * a context menu keeps.
   */
  var startOffsetX by mutableStateOf(0f)
  var startOffsetY by mutableStateOf(0f)

  /** True when the pop-up starts as the anchor's exact rectangle and travels to its place. */
  var morphFromAnchor by mutableStateOf(false)

  /**
   * The anchor's rectangle in the pane's own coordinates: the window the pane opens out of.
   *
   * Held as four numbers rather than as scale factors because the pane no longer *stretches* into
   * place — it is masked into place, and a mask needs a rectangle.
   */
  var anchorLeft by mutableStateOf(0f)
  var anchorTop by mutableStateOf(0f)
  var anchorWidth by mutableStateOf(0f)
  var anchorHeight by mutableStateOf(0f)
}

/**
 * The window a morphing pop-up is seen through, from the anchor's rectangle to its own.
 *
 * This exists because the previous morph **stretched the pane's content**. Starting the pane at the
 * anchor's exact rectangle meant scaling a card 300 dp tall down to a row 90 dp tall while keeping
 * its full width: the two axes got different factors, and for the first third of every opening the
 * title of a circular was squashed to two thirds of its height. Nothing in a real interface is
 * anamorphic, so the eye reads it immediately, and no amount of spring tuning hides it.
 *
 * An opening window has none of that. The content is laid out once, at its final size, and never
 * transformed: what travels is the *hole* it is seen through. That is also what an app icon opening
 * on iOS actually does — the app is never a squashed app, it is an app you can only see part of
 * yet.
 *
 * The outline is an ordinary rounded rectangle rather than this system's continuous corner, and
 * deliberately: a generic path is CPU-rasterised into Skia's small-path atlas keyed by subpixel
 * phase, so a corner that moves every frame would re-rasterise every frame. The smoothing lives
 * inside two pixels of the corner and the corner is in motion; nobody has ever seen it.
 */
private class FluidPopoverMorphWindow(
  private val placement: FluidPopoverPlacement,
  private val growth: () -> Float,
  private val startRadiusPx: Float,
  private val endRadiusPx: Float,
) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val g = growth().coerceIn(0f, 1f)
    val left = lerp(placement.anchorLeft, 0f, g)
    val top = lerp(placement.anchorTop, 0f, g)
    val right = lerp(placement.anchorLeft + placement.anchorWidth, size.width, g)
    val bottom = lerp(placement.anchorTop + placement.anchorHeight, size.height, g)
    val radius = lerp(startRadiusPx, endRadiusPx, g)
    return Outline.Rounded(
      RoundRect(
        rect = Rect(left, top, maxOf(right, left + 1f), maxOf(bottom, top + 1f)),
        cornerRadius = CornerRadius(radius, radius),
      ),
    )
  }
}

/**
 * A pop-up placed against [anchor], sized to its content, growing out of the corner nearest it.
 *
 * A `Layout` rather than a measured offset, because the alternative flashes: anything that has to
 * read its own size out of state before it can place itself gets drawn once in the wrong place
 * first, and on something that arrives under the finger, once is enough to see.
 *
 * The size is the content's own. That is what "dynamic" means here, and it is the reason this is not
 * simply a dialog with a source rectangle: a three-line menu is three lines tall, a detail with a
 * paragraph in it is as tall as the paragraph, and neither is padded out to a card.
 */
@Composable
private fun FluidAnchoredPopover(
  anchor: Rect?,
  paneTitle: String?,
  backdrop: GlassBackdropState?,
  compact: Boolean,
  /** True for [FluidGlassModalPresentation.Expand]: sit *on* the anchor rather than beside it. */
  overAnchor: Boolean,
  presence: () -> Float,
  growth: () -> Float,
  growthCross: () -> Float,
  retreat: () -> Float,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val gapPx = with(density) { FluidPopoverGap.toPx() }
  val marginPx = with(density) { FluidPopoverMargin.toPx() }
  val maxWidthPx = with(density) {
    (if (compact) FluidContextMenuMaxWidth else FluidPopoverMaxWidth).toPx()
  }
  val minWidthPx = with(density) { if (compact) FluidContextMenuMinWidth.toPx() else 0f }
  val placement = remember { FluidPopoverPlacement() }
  val shape = ContinuousCornerShape(if (compact) FluidRadius.Group else FluidPopoverRadius)
  val currentGrowth by rememberUpdatedState(growth)
  val startRadiusPx = with(density) { FluidRadius.Group.toPx() }
  val endRadiusPx = with(density) {
    (if (compact) FluidRadius.Group else FluidPopoverRadius).toPx()
  }
  val morphWindow = remember(placement, startRadiusPx, endRadiusPx) {
    FluidPopoverMorphWindow(
      placement = placement,
      growth = { currentGrowth() },
      startRadiusPx = startRadiusPx,
      endRadiusPx = endRadiusPx,
    )
  }

  Layout(
    modifier = Modifier.fillMaxSize(),
    content = {
      Column(
        modifier = Modifier
          .graphicsLayer {
            val g = growth()
            val gc = growthCross()
            val shrink = 1f - retreat() * 0.10f
            if (placement.morphFromAnchor) {
              // The row *becomes* the pane, and it does it by opening rather than by stretching:
              // see [FluidPopoverMorphWindow]. The only transform left is a uniform zoom of a few
              // percent around the anchor's centre — enough that the pane reads as coming toward
              // you, small enough that no letter changes shape.
              alpha = presence()
              val zoom = lerp(FluidPopoverMorphStartScale, 1f, g) * shrink
              scaleX = zoom
              scaleY = zoom
              transformOrigin = TransformOrigin(
                if (size.width > 0f) {
                  ((placement.anchorLeft + placement.anchorWidth / 2f) / size.width)
                    .coerceIn(0f, 1f)
                } else {
                  0.5f
                },
                if (size.height > 0f) {
                  ((placement.anchorTop + placement.anchorHeight / 2f) / size.height)
                    .coerceIn(0f, 1f)
                } else {
                  0.5f
                },
              )
              // The clip is dropped once the window has finished opening, because it also clips the
              // pane's drop shadow, and a pane at rest is exactly where that shadow is doing its
              // job of holding the card off the page.
              clip = g < 0.995f
              this.shape = morphWindow
            } else {
              // A context menu keeps the older growth: its row is being lifted separately right
              // beside it, and a menu that also opened out of the row's rectangle would fight the
              // very object the lift is presenting.
              alpha = presence() * ((minOf(g, gc) - 0.08f) / 0.27f).coerceIn(0f, 1f)
              scaleX = lerp(placement.startScaleX, 1f, g) * shrink
              scaleY = lerp(placement.startScaleY, 1f, gc) * shrink
              transformOrigin = TransformOrigin(placement.pivotX, placement.pivotY)
            }
          }
          .then(
            if (backdrop != null) {
              Modifier.glassSurface(
                state = backdrop,
                tint = GlassDefaults.modalTint(),
                shape = shape,
                role = GlassRole.Modal,
                optics = FluidPopoverOptics,
                // Captured once and carried. The pane spends both of its journeys being scaled and
                // translated by the layer above, and a re-capture computed mid-transform maps the
                // sources wrongly — which on every close stripped the pane of its glass and left a
                // milky card travelling down the screen. The recording's sources stay live by
                // reference, so what is frozen is only the geometry.
                sampleOnce = true,
              )
            } else {
              Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            },
          )
          .clip(shape)
          .semantics {
            paneTitle?.let { this.paneTitle = it }
            isTraversalGroup = true
          },
      ) {
        // Il contenuto arriva **dentro** la finestra, non insieme a lei. La finestra che si apre e'
        // gia' vetro dal primo fotogramma, quindi la riga non sparisce e basta: diventa il
        // materiale del pannello. Il testo entra subito dopo, mentre il vetro e' gia' li'.
        Column(
          modifier = Modifier.graphicsLayer {
            alpha = if (placement.morphFromAnchor) {
              ((growth() - FluidPopoverContentFadeStart) / FluidPopoverContentFadeSpan)
                .coerceIn(0f, 1f)
            } else {
              1f
            }
          },
          content = content,
        )
      }
    },
  ) { measurables, constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val available = (width - marginPx * 2f).coerceAtLeast(1f)
    val maxContentWidth = minOf(maxWidthPx, available).roundToInt().coerceAtLeast(1)

    val placeable = measurables.first().measure(
      constraints.copy(
        minWidth = minWidthPx.roundToInt().coerceIn(0, maxContentWidth),
        minHeight = 0,
        maxWidth = maxContentWidth,
        maxHeight = (height * FluidPopoverMaxHeightFraction - marginPx * 2f)
          .roundToInt()
          .coerceAtLeast(1),
      ),
    )

    if (anchor == null) {
      placement.pivotX = 0.5f
      placement.pivotY = 0.5f
      placement.startScaleX = FluidPopoverStartScale
      placement.startScaleY = FluidPopoverStartScale
      placement.morphFromAnchor = false
      return@Layout layout(width, height) {
        placeable.place(
          x = ((width - placeable.width) / 2f).roundToInt(),
          y = ((height - placeable.height) / 2f).roundToInt(),
        )
      }
    }

    val spaceBelow = height - anchor.bottom - gapPx - marginPx
    val spaceAbove = anchor.top - gapPx - marginPx
    val below = placeable.height <= spaceBelow || spaceBelow >= spaceAbove

    val maxX = (width - placeable.width - marginPx).coerceAtLeast(marginPx)
    // Centred on what was touched, then clamped to the screen. A pop-up pinned to the leading edge
    // of a full-width row looks like it belongs to the row text; pinned to its centre it looks like
    // it belongs to the touch.
    val x = (anchor.center.x - placeable.width / 2f).coerceIn(marginPx, maxX)
    val y = if (overAnchor) {
      // Sitting *on* the anchor: the edge the menu grows away from stays put, so the button does
      // not appear to jump the moment it unfolds.
      if (below) {
        anchor.top.coerceAtMost((height - placeable.height - marginPx).coerceAtLeast(marginPx))
      } else {
        (anchor.bottom - placeable.height).coerceAtLeast(marginPx)
      }
    } else if (below) {
      (anchor.bottom + gapPx)
        .coerceAtMost((height - placeable.height - marginPx).coerceAtLeast(marginPx))
    } else {
      (anchor.top - gapPx - placeable.height).coerceAtLeast(marginPx)
    }

    // The pivot is the point on the pop-up closest to what was touched, expressed as a fraction of
    // its own box. Growing from there is the difference between a pop-up that opens *out of* the
    // anchor and one that merely happens to be next to it.
    placement.pivotX = if (placeable.width <= 0) {
      0.5f
    } else {
      ((anchor.center.x - x) / placeable.width).coerceIn(0f, 1f)
    }
    placement.pivotY = if (below) 0f else 1f

    // Where the growth starts. Everything except a context menu morphs from the anchor's own
    // rectangle: the first frame of the pop-up IS the row or button that opened it, and the exit is
    // the pane folding back into it. A context menu is the one shape that keeps the fixed-fraction
    // corner growth — its row is being lifted separately right beside it, and a menu that also
    // stretched out of the row's rectangle would fight the very object the lift is presenting.
    val morph = (!compact || overAnchor) && placeable.width > 0 && placeable.height > 0
    placement.morphFromAnchor = morph
    if (morph) {
      placement.anchorLeft = anchor.left - x
      placement.anchorTop = anchor.top - y
      placement.anchorWidth = anchor.width
      placement.anchorHeight = anchor.height
      placement.startScaleX = 1f
      placement.startScaleY = 1f
      placement.startOffsetX = 0f
      placement.startOffsetY = 0f
    } else {
      placement.startScaleX = FluidPopoverStartScale
      placement.startScaleY = FluidPopoverStartScale
      placement.startOffsetX = 0f
      placement.startOffsetY = 0f
    }

    layout(width, height) { placeable.place(x.roundToInt(), y.roundToInt()) }
  }
}

/**
 * The row, lifted out of the page and turned into a pane of the menu's own material.
 *
 * Not a scaled copy of itself. The first version simply replayed the row's recording a little
 * larger, and what that reads as is the row *doubling* — there are suddenly two of it, one blurred
 * and one not, and neither has become anything. What actually happens on iOS is that the thing you
 * held **turns into** the same glass card the menu is made of, and keeps its own contents printed on
 * it. So the recording is drawn onto a real [GlassRole.Modal] surface with the menu's tint and the
 * menu's radius, and the pair grows out of the touch on the same two springs.
 *
 * The recording is what makes this one modifier rather than a second copy of the row's content:
 * whatever the row draws comes along, whatever it happens to be.
 */
@Composable
private fun FluidLiftedPreview(
  preview: GraphicsLayer?,
  bounds: Rect?,
  backdrop: GlassBackdropState?,
  presence: () -> Float,
  scaleX: () -> Float,
  scaleY: () -> Float,
) {
  if (preview == null || bounds == null) return
  val density = LocalDensity.current
  val shape = ContinuousCornerShape(FluidPopoverRadius)
  Box(
    modifier = Modifier
      .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
      .size(
        width = with(density) { bounds.width.toDp() },
        height = with(density) { bounds.height.toDp() },
      )
      .graphicsLayer {
        alpha = presence()
        this.scaleX = scaleX()
        this.scaleY = scaleY()
        // Grown from its own bottom edge, because that is the edge the menu appears against: the
        // row and the menu below it then open as one object rather than as two things that both
        // happen to be arriving.
        transformOrigin = TransformOrigin(0.5f, 1f)
      }
      .then(
        if (backdrop != null) {
          Modifier.glassSurface(
            state = backdrop,
            tint = GlassDefaults.modalTint(),
            shape = shape,
            role = GlassRole.Modal,
            optics = FluidPopoverOptics,
          )
        } else {
          Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
        },
      )
      .clip(shape)
      .drawWithContent {
        // Replaying a released layer is a crash, and the list underneath can dispose a row while
        // the menu is up. Nothing here is worth a crash: if the recording is gone, the pane simply
        // comes up empty and the menu keeps its actions.
        runCatching { drawLayer(preview) }
      },
  )
}

@Composable
private fun FluidContextMenuRow(
  action: FluidContextAction,
  onDismiss: () -> Unit,
) {
  val color = if (action.destructive) {
    MaterialTheme.colorScheme.error
  } else {
    MaterialTheme.colorScheme.onSurface
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 48.dp)
      .fluidRowPressable(
        onClick = {
          // A disabled action closing the menu would be indistinguishable from having performed it.
          if (!action.enabled) return@fluidRowPressable
          onDismiss()
          action.onClick()
        },
        enabled = action.enabled,
      )
      .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CompositionLocalProvider(LocalContentColor provides color) {
      Text(
        text = action.label,
        modifier = Modifier
          .weight(1f)
          .graphicsLayer { alpha = if (action.enabled) 1f else 0.38f },
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      action.icon?.let {
        Icon(
          imageVector = it,
          contentDescription = null,
          modifier = Modifier
            .size(20.dp)
            .graphicsLayer { alpha = if (action.enabled) 1f else 0.38f },
          tint = color,
        )
      }
    }
  }
}

/**
 * The bottom-anchored variant, for content tall enough to want the whole height.
 *
 * The only presentation whose edge is against the screen rather than against the thing that opened
 * it, and therefore the only one that behaves like a sheet: it travels in from below on a spring,
 * and it can be **pushed back out with a finger, from anywhere on it**.
 *
 * "From anywhere" is the part that takes work, because a sheet is allowed to contain a list and a
 * downward drag is then ambiguous. It is resolved the way Material resolves it, with nested scroll:
 * the content gets the gesture first and keeps it for as long as it has somewhere to scroll; the
 * moment it runs out — [NestedScrollConnection.onPostScroll] reporting leftover downward travel —
 * the sheet takes the remainder and starts moving. Letting go hands the fling to whichever of the
 * two is holding the gesture. A sheet with nothing scrollable in it never reaches that negotiation
 * and is simply dragged.
 */
@Composable
private fun FluidGlassModalSheet(
  paneTitle: String?,
  backdrop: GlassBackdropState?,
  slide: () -> Float,
  backProgress: () -> Float,
  reducedMotion: Boolean,
  onDismiss: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
  val density = LocalDensity.current
  val scope = rememberCoroutineScope()
  val dismissThresholdPx = with(density) { FluidSheetDismissTravel.toPx() }
  val flingDismissPx = with(density) { FluidSheetDismissVelocity.toPx() }

  // Plain state, not an `Animatable`: nested-scroll callbacks are not suspending, and a drag has to
  // land on the same frame as the finger. The animatable only comes out to settle it.
  var dragPx by remember { mutableFloatStateOf(0f) }
  var height by remember { mutableFloatStateOf(0f) }

  fun settle(velocity: Float) {
    val shouldDismiss = dragPx > dismissThresholdPx || velocity > flingDismissPx
    if (shouldDismiss) {
      onDismiss()
      return
    }
    scope.launch {
      val from = dragPx
      androidx.compose.animation.core.animate(
        initialValue = from,
        targetValue = 0f,
        initialVelocity = velocity,
        animationSpec = spring(0.9f, 500f, 0.5f),
      ) { value, _ -> dragPx = value }
    }
  }

  val nested = remember(dismissThresholdPx, flingDismissPx) {
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // Pulling back up while the sheet is already out of place closes that gap first, before the
        // content is allowed to scroll. Otherwise a half-dragged sheet scrolls its list instead of
        // returning, which reads as the drag having been ignored.
        if (source != NestedScrollSource.UserInput || available.y >= 0f || dragPx <= 0f) {
          return Offset.Zero
        }
        val used = (-available.y).coerceAtMost(dragPx)
        dragPx -= used
        return Offset(0f, -used)
      }

      override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
      ): Offset {
        if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        dragPx += available.y
        return Offset(0f, available.y)
      }

      override suspend fun onPreFling(available: Velocity): Velocity {
        if (dragPx <= 0f) return Velocity.Zero
        settle(available.y)
        return available
      }
    }
  }

  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = FluidSheetMaxHeight)
        .onSizeChanged { height = it.height.toFloat() }
        .nestedScroll(nested)
        .draggable(
          orientation = Orientation.Vertical,
          state = rememberDraggableState { delta -> dragPx = (dragPx + delta).coerceAtLeast(0f) },
          onDragStopped = { velocity -> settle(velocity) },
        )
        .graphicsLayer {
          val travelled = slide().coerceIn(0f, 1f)
          // Below the screen at 0, home at 1, and the drag is added on top in raw pixels so it
          // tracks the finger exactly rather than through an eased curve.
          translationY = if (reducedMotion) {
            dragPx
          } else {
            size.height * (1f - travelled) + dragPx
          }
          // The alpha only covers the last sliver of the exit; the travel is the animation.
          alpha = (travelled * 4f).coerceIn(0f, 1f)
          val retreat = 1f - backProgress() * 0.06f
          scaleX = retreat
          scaleY = retreat
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
        .then(
          if (backdrop != null) {
            Modifier.glassSurface(
              state = backdrop,
              tint = GlassDefaults.modalTint(),
              shape = shape,
              role = GlassRole.Modal,
              optics = FluidPopoverOptics,
            )
          } else {
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
          },
        )
        .clip(shape)
        .semantics {
          paneTitle?.let { this.paneTitle = it }
          isTraversalGroup = true
        },
    ) {
      FluidGrabber()
      content()
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Context menu
// ---------------------------------------------------------------------------------------------

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted row is not the same instance — that one is still in the list, under the scrim. It is a
 * **recording**: the element paints itself into a `GraphicsLayer` and the host replays it. Recording
 * costs nothing while nobody is touching the row, because it only starts on the finger going down
 * and stops when it comes back up.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
/**
 * A context menu that something else decides when to open.
 *
 * Two pieces instead of one, and the split is not tidiness — it is the only arrangement that works.
 * A `detectTapGestures { onLongPress }` sitting next to a row's own `combinedClickable` never fires:
 * the click handler consumes the gesture first and the long press is simply eaten, silently, which
 * is exactly what happened the first time this shipped. Anything that already handles clicks has to
 * raise the menu from **its own** long-press callback, and that is what [open] is for.
 *
 * [Modifier.fluidContextMenu] is the convenience form for elements that do *not* handle clicks: it
 * pairs this with a gesture of its own.
 */
@Stable
class FluidContextMenuController internal constructor(
  private val host: FluidGlassModalHostState?,
  internal val entry: FluidGlassModalEntry,
  internal val layer: GraphicsLayer,
) {
  internal var actionsProvider: () -> List<FluidContextAction> = { emptyList() }

  /** Whether the element is currently painting itself into [layer] for the overlay to replay. */
  internal var recording by mutableStateOf(false)

  val isOpen: Boolean get() = entry.visible

  /** Starts capturing the element's picture. Called on touch-down, long before a menu can open. */
  internal fun arm(pressed: Boolean) {
    recording = pressed || entry.visible
    if (recording) entry.preview = layer
  }

  /** Raises the menu. Returns false when there is nothing to show, so the caller can fall through. */
  fun open(): Boolean {
    val resolvedHost = host ?: return false
    val list = actionsProvider()
    if (list.isEmpty()) return false
    entry.actions = list
    entry.preview = layer
    entry.openedAt = resolvedHost.nextSequence()
    entry.visible = true
    entry.lifted = true
    recording = true
    return true
  }

  fun dismiss() {
    entry.visible = false
    recording = false
  }
}

/**
 * The state behind a context menu, so a component that owns its own gestures can raise one.
 *
 * Pair it with [Modifier.fluidContextMenuAnchor] on the element the menu belongs to, and call
 * [FluidContextMenuController.open] from that element's long-press.
 */
@Composable
fun rememberFluidContextMenu(
  actions: () -> List<FluidContextAction>,
): FluidContextMenuController {
  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val layer = rememberGraphicsLayer()
  val controller = remember(host, entry, layer) {
    FluidContextMenuController(host, entry, layer)
  }

  SideEffect {
    controller.actionsProvider = actions
    entry.presentation = FluidGlassModalPresentation.ContextMenu
    entry.onDismissRequest = controller::dismiss
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      // The recording belongs to this node and dies with it. Clearing the host's reference first is
      // what stops the overlay from replaying a layer the runtime has already released.
      entry.preview = null
      entry.visible = false
      host?.unregister(entry)
    }
  }

  return controller
}

/**
 * Marks the element a [FluidContextMenuController] belongs to.
 *
 * Reports where it is, and paints it into a `GraphicsLayer` while a finger is on it so the overlay
 * has a finished picture to lift. The press is observed on the **initial** pass and nothing is
 * consumed, so this can sit under a row that handles its own clicks without taking anything away
 * from it — which is the entire reason the capture is separate from the gesture.
 *
 * Recording only runs between touch-down and touch-up, so a list of these costs nothing at rest.
 */
@Composable
fun Modifier.fluidContextMenuAnchor(controller: FluidContextMenuController): Modifier {
  val scope = rememberCoroutineScope()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  // The same specular hotspot the glass controls carry, for the same reason: a row that is about to
  // become a pane of glass has to answer the finger like one *before* it does. Without it a long
  // press is a blank half-second and then a whole overlay, with nothing in between to say the press
  // was even received — and half a second of nothing is how long it takes to decide a control
  // is broken and lift your finger off it.
  val highlight = remember(scope, reducedMotion) {
    GlassTouchHighlight(
      animationScope = scope,
      strength = { if (reducedMotion) 0f else 1f },
    )
  }
  return this
    .onGloballyPositioned {
      val rect = it.boundsInRoot()
      controller.entry.previewSize = rect
      controller.entry.origin = { rect }
    }
    .pointerInput(controller) {
      awaitPointerEventScope {
        while (true) {
          val event = awaitPointerEvent(PointerEventPass.Initial)
          controller.arm(event.changes.any { it.pressed })
        }
      }
    }
    .graphicsLayer {
      if (reducedMotion) return@graphicsLayer
      // Swells under the finger, the way the navigation pill does, and settles back when it is let
      // go. Rows normally must not scale — shrinking one row of a stack breaks the group's
      // silhouette — but this one is on its way *out* of the stack, and growing says so.
      val swell = 1f + FluidContextAnchorSwell * highlight.pressProgress
      scaleX = swell
      scaleY = swell
    }
    // Ahead of the capture, not inside it. The trail belongs to the finger and the capture is a
    // still: record the glow too and the lifted pane arrives with a bright smear frozen onto it,
    // sitting wherever the finger happened to be on the frame the press was recognised.
    .then(highlight.modifier)
    .then(highlight.gestureModifier)
    .drawWithContent {
      // Lifted: the row is *in the overlay now*, and drawing it here as well is what made the
      // menu look broken. The copy in front is a pane of glass, so the row still sitting in the
      // page was being refracted through it and arriving a second time, blurred and a few pixels
      // off — every line of it doubled behind itself. The space stays reserved, because this is a
      // draw and not a layout, so nothing below it moves while the menu is up.
      if (controller.entry.lifted) return@drawWithContent
      if (controller.recording) {
        controller.layer.record { this@drawWithContent.drawContent() }
        drawLayer(controller.layer)
      } else {
        drawContent()
      }
    }
}

/** How much a row swells while it is being held. A few percent: it is a row, not a card yet. */
private const val FluidContextAnchorSwell = 0.02f

/**
 * Long-press this element to raise a menu of actions anchored to it.
 *
 * The sequence is the iOS one, and every step of it is doing a job: the haptic confirms the press
 * before anything moves, the page falls behind a lightly frosted scrim so the menu has somewhere to
 * be, the row itself is redrawn *in its own place* and lifted, and the actions grow out of the
 * corner nearest it.
 *
 * The lifted element is not the same instance — that one is still in the list, under the scrim. It
 * is a **recording**, replayed one level up.
 *
 * Use this only on something that does **not** already handle clicks. On anything that does — a
 * list row, a card with an `onClick` — the click handler consumes the gesture and this never fires;
 * use [rememberFluidContextMenu] with [fluidContextMenuAnchor] and open the menu from that
 * element's own long-press instead. [dev.antigravity.fluidengine.ui.theme.FluidListRow] does exactly
 * that behind its `contextActions` parameter.
 *
 * Below API 31 the menu keeps working with opaque surfaces. It loses the blur, not the function.
 */
@Composable
fun Modifier.fluidContextMenu(
  actions: () -> List<FluidContextAction>,
  enabled: Boolean = true,
): Modifier {
  val controller = rememberFluidContextMenu(actions)
  val haptics = LocalHapticFeedback.current
  if (!enabled) return this
  return this
    .fluidContextMenuAnchor(controller)
    .pointerInput(controller) {
      detectTapGestures(
        onLongPress = {
          if (controller.open()) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        },
      )
    }
}

/**
 * The pop-up's own material.
 *
 * Real glass, with the lens on. A modal that is merely translucent is the failure this file exists
 * to avoid: now that the scrim barely frosts the page, a flat wash over it would show the page
 * straight through, unbent, and read as a semi-transparent rectangle rather than as a pane. The blur
 * stays modest and the *displacement* does the identifying — the page visibly bending into the
 * pop-up's perimeter is the whole tell that there is a thickness there.
 */
private val FluidPopoverOptics = GlassOptics(
  // The one surface here that *is* frosted, and the division of labour is deliberate: the scrim
  // barely touches the page, the pop-up frosts hard. That is the way round Apple does it — the page
  // stays itself and merely dims, and the thing in front of it is the pane you cannot read through.
  // The reverse (a heavy scrim under a clear pop-up) hides the page and gains nothing.
  //
  // This was twelve dp, on top of a scrim that had already blurred the same pixels, and the result
  // was the complaint that started this pass: a pop-up you cannot see anything through is not
  // glass, it is a light grey card, and every one of the expensive things below — the bevel, the
  // rim, the vibrancy — was being spent describing the edge of a blank. The material is the
  // *bend*; frosting is only what keeps the text on top readable, and five dp over an
  // already-dimmed page is enough for that.
  blurScale = 2.5f,
  // And the bevel comes in to match. Twenty-eight dp of displacement on a menu 210 dp wide meant a
  // quarter of the pane was edge: what should read as a thick slab read as a fisheye, with the page
  // smeared round the outside of it in a coloured halo.
  refractionHeight = 14.dp,
  refractionAmount = 18.dp,
  depthEffect = false,
  dispersion = false,
  vibrancy = 1.6f,
  highlightWidth = 0.8.dp,
  // Bright enough to find the edge, dim enough that the pane is not outlined in white. At 0.7 the
  // rim was the loudest thing on the screen and the pop-up read as a sticker.
  highlightAlpha = 0.45f,
  highlightAngle = 90f,
  innerShadowRadius = 8.dp,
  innerShadowAlpha = 0.16f,
  // Tight and soft. A wide dark shadow over a scrim that is already darkening the page reads as
  // grime around the pop-up rather than as height.
  shadowRadius = 18.dp,
  shadowAlpha = 0.36f,
  pressedDepthBoost = 0f,
)

/**
 * How small the pop-up starts.
 *
 * Not zero. Growing from nothing is a zoom, and a zoom has no material in it — the pop-up has to be
 * recognisably itself from the first frame and simply be *small*, the way the tab bar's pill is
 * always a pill however hard it is squashed.
 */
private const val FluidPopoverStartScale = 0.42f

/** How much the lifted row grows. Any more and it stops being the row and becomes a card. */
private const val FluidLiftedPreviewScale = 1.03f

/**
 * Across and down. Different on purpose: two matched springs give a balloon, two mismatched ones
 * give the squash that makes this read as the same object as the navigation pill.
 */
private const val FluidPopoverDampingX = 0.62f
private const val FluidPopoverDampingY = 0.74f
private const val FluidPopoverStiffness = 260f

private const val FluidPopoverFadeInMillis = 110

/**
 * L'uscita dura piu' dell'ingresso, ed e' voluto: all'ingresso il contenuto deve essere leggibile
 * il prima possibile, all'uscita l'occhio sta seguendo il pannello che *torna nella riga*, e un
 * fade che finisce prima della corsa lascia uno scrim orfano sopra una pagina senza modale.
 */
private const val FluidPopoverFadeOutMillis = 240
private const val FluidPopoverExitStiffness = 420f

/**
 * Quanto e' piccolo il pannello quando la finestra comincia ad aprirsi.
 *
 * Pochi punti percentuali: la crescita vera la fa la finestra, questo zoom serve solo a togliere
 * l'impressione che il pannello fosse gia' li', dietro la riga, ad aspettare. Piu' di cosi' e il
 * testo comincia a scalare, che e' esattamente il difetto da cui si veniva.
 */
private const val FluidPopoverMorphStartScale = 0.94f

/** Dove comincia e quanto dura la comparsa del contenuto, lungo la corsa della finestra. */
private const val FluidPopoverContentFadeStart = 0.18f
private const val FluidPopoverContentFadeSpan = 0.42f

private val FluidPopoverMargin = 16.dp
private val FluidPopoverGap = 10.dp
// Largo abbastanza da far respirare un dettaglio con allegati su un tablet; su un telefono e' la
// larghezza dello schermo meno i margini a decidere, quindi il numero grande non cambia niente li'.
private val FluidPopoverMaxWidth = 620.dp
private const val FluidPopoverMaxHeightFraction = 0.78f
/**
 * The pop-up's corner, and it is [FluidRadius.Group] on purpose.
 *
 * A pop-up that grows out of a row is growing out of a *grouped list*, and it starts at that row's
 * rectangle: if its radius were its own number, the first frame would be the row with the wrong
 * corners and the animation would be spent correcting them. Sharing the group's radius means the
 * corner the pop-up starts with is the corner it was standing on.
 */
private val FluidPopoverRadius = FluidRadius.Group

private val FluidSheetMaxHeight = 640.dp

/** How far the sheet must be pushed down before letting go dismisses instead of springing back. */
private val FluidSheetDismissTravel = 110.dp

/** Or how fast, in dp per second: a short flick down closes it however little it travelled. */
private val FluidSheetDismissVelocity = 900.dp

private val FluidContextMenuMinWidth = 210.dp
private val FluidContextMenuMaxWidth = 300.dp
