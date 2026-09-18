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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
 * Dove si raccoglie la barra quando si ripiega.
 *
 * Non è una preferenza estetica, è una domanda su quale mano tiene il telefono: la capsula
 * ripiegata resta l'unico bersaglio della navigazione, e dove si posa decide se il pollice ci
 * arriva. [Start] è la scelta giusta per la maggior parte delle mani destre, che tengono il
 * telefono a sinistra del baricentro; [Center] è la scelta neutra e [End] lo specchio.
 */
enum class FluidFoldAlignment { Start, Center, End }

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
  /**
   * The glyph a tab is drawn with, when [FluidTabItem.icon] is not enough.
   *
   * One slot for every tab rather than a lambda inside [FluidTabItem], and that is deliberate: a
   * lambda in the item makes the list a different list on every recomposition, and this bar keys
   * real work — the indicator's spring, the drag animation — on that list. An app whose tabs swap
   * between a filled and an outlined glyph passes it here and keeps its items a value type.
   */
  tabIcon: (@Composable (item: FluidTabItem, selected: Boolean) -> Unit)? = null,
  /**
   * A band that rides above the row — a now-playing pill, a download strip, an offer.
   *
   * It travels with the fold instead of going away, and that is the whole reason it is here rather
   * than in a `Column` above the bar. Folded, the capsule closes to a square and the accessory takes
   * the width it gave up, so the two of them are one object contracting: nothing appears, nothing
   * vanishes, and a surface growing out of the accessory's rectangle keeps growing out of it the
   * whole way.
   *
   * Two consequences worth knowing before reaching for it. The bar keeps its full width while it is
   * folded — there is nothing left over to push to one side — so [foldAlignment] has no work to do
   * and is ignored. And the accessory is measured, never asked how tall it is: it gets
   * [accessoryHeight] with the bar open and the row's own height once it is folded, which is the
   * same travel the capsule beside it is making. A band left at its open height inside the folded
   * row sits in it by a hair, and a hair of margin is what makes two things read as two things.
   *
   * The content is the caller's, and so is what it knows about itself: an app that needs the
   * accessory's rectangle on screen puts its own `onGloballyPositioned` inside this slot.
   */
  accessory: (@Composable () -> Unit)? = null,
  /** How tall [accessory] is drawn while the bar is open. Folded, it takes the row. */
  accessoryHeight: Dp = FluidFoldingTabBarDefaults.AccessoryHeight,
  /**
   * Whether the bar is a search field right now.
   *
   * The field is not a second bar: [trailing] grows into it, the capsule closes onto the tab you
   * came from, and the way back is that same tab. One surface and one number, like the fold — which
   * is why the two must not run at once: lock the fold (`FluidBarFold.locked`) while searching, or
   * the capsule is being squeezed by two things that disagree about how far.
   */
  searchMode: Boolean = false,
  /**
   * The field itself, handed the modifier it has to wear so the bar can size it.
   *
   * Passed **always**, not only while [searchMode] is true: the bar composes it when the search is
   * on screen and drops it when the travel is over, and a slot that arrives null cannot be animated
   * out — it would simply be gone for the half second it should have been closing.
   */
  searchContent: (@Composable (Modifier) -> Unit)? = null,
  /** Dove finisce la barra una volta ripiegata. Vedi [FluidFoldAlignment]. */
  foldAlignment: FluidFoldAlignment = FluidFoldAlignment.Center,
  /**
   * What the capsule is made of.
   *
   * The default is the family's floating film, which is right over a page. An app whose pages are
   * full-bleed pictures wants [GlassDefaults.darkFloatingTint] instead — and wants it on everything
   * this bar touches, including whatever it carries in [accessory], or the two stop matching.
   */
  tint: GlassTint = GlassDefaults.floatingTint(),
) {
  if (items.isEmpty()) return

  val density = LocalDensity.current
  val openPx = with(density) { FluidFoldingTabBarDefaults.OpenHeight.roundToPx() }
  val foldedPx = with(density) { FluidFoldingTabBarDefaults.FoldedHeight.roundToPx() }
  val spacingPx = with(density) { FluidFoldingTabBarDefaults.Spacing.roundToPx() }
  val accessoryPx = with(density) { accessoryHeight.roundToPx() }
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  // The search's own number, animated here rather than by the caller, because unlike the fold
  // nothing outside the bar has any use for it. Read in measure like the fold is; the only thing
  // composition is told is whether the field is on screen at all.
  val searchTravel = remember { Animatable(0f) }
  val wantsSearch = searchMode && searchContent != null
  LaunchedEffect(wantsSearch, reducedMotion) {
    if (reducedMotion) {
      searchTravel.snapTo(if (wantsSearch) 1f else 0f)
    } else {
      searchTravel.animateTo(
        if (wantsSearch) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow),
      )
    }
  }
  val search: () -> Float = { searchTravel.value }
  // Two recompositions for a whole transition — one when it starts, one when it stops — instead of
  // one per frame, which is what reading `value` up here would have cost.
  val searchOnScreen = wantsSearch || searchTravel.isRunning

  // What squeezes the capsule: the fold, the search, or whichever is further along. The capsule
  // still reads `fold` for what a tap *means*, and that is not the same question.
  val squeeze: () -> Float = { maxOf(fold(), search()) }

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
          squeeze = squeeze,
          tabIcon = tabIcon,
          tint = tint,
        )
      }
      if (trailing != null) {
        Box(modifier = Modifier.layoutId(SlotTrailing)) { trailing() }
      }
      if (accessory != null) {
        Box(modifier = Modifier.layoutId(SlotAccessory)) { accessory() }
      }
      if (searchContent != null && searchOnScreen) {
        Box(modifier = Modifier.layoutId(SlotSearch)) { searchContent(Modifier.fillMaxSize()) }
      }
    },
  ) { measurables, constraints ->
    // Read here, in measure. Nothing above this line depends on the fold, so nothing above this line
    // runs again when it moves.
    val f = fold().fastCoerceIn(0f, 1f)
    val s = search().fastCoerceIn(0f, 1f)
    val shrink = maxOf(f, s)
    val width = constraints.maxWidth
    val rowHeight = lerp(openPx, foldedPx, f)

    val accessoryMeasurable = measurables.firstOrNull { it.layoutId == SlotAccessory }
    val searchMeasurable = measurables.firstOrNull { it.layoutId == SlotSearch }

    // The band above the row, and the height it takes out of the bar. Both go to nothing as the
    // accessory moves down into the row, which is why the bar's own height is a single lerp.
    val bandHeight = if (accessoryMeasurable != null) accessoryPx + spacingPx else 0
    val barHeight = fluidBarHeight(openPx, rowHeight, bandHeight, if (bandHeight > 0) f else 1f)
    val rowTop = barHeight - rowHeight

    val gap = if (trailing != null) spacingPx else 0
    // Open, the trailing control is a square of the row's height. Searching, it is everything the
    // capsule has just given up: the button *is* the field, grown.
    val trailingSquare = rowHeight
    val trailingGrown = (width - rowHeight - gap).coerceAtLeast(1)
    val trailingWidth = if (trailing != null) lerp(trailingSquare, trailingGrown, s) else 0
    val trailingPlaceable = measurables
      .firstOrNull { it.layoutId == SlotTrailing }
      ?.measure(Constraints.fixed(trailingWidth, rowHeight))

    val capsuleOpen = (width - trailingSquare - gap).coerceAtLeast(1)
    val capsuleWidth = fluidFoldedCapsuleWidth(capsuleOpen, rowHeight, shrink)
    val capsulePlaceable = measurables
      .first { it.layoutId == SlotCapsule }
      .measure(Constraints.fixed(capsuleWidth, rowHeight))

    // The accessory: the whole width above the row when the bar is open, and the room between the
    // closed capsule and the trailing control when it is folded.
    val accessoryWidth = fluidAccessoryWidth(width, capsuleWidth, gap, trailingWidth, f)
    // Open it is its own height; folded it is the row, travelling there on the same number the
    // capsule shrinks on.
    val accessoryHeightNow = lerp(accessoryPx, rowHeight, f)
    val accessoryPlaceable = accessoryMeasurable
      ?.measure(Constraints.fixed(accessoryWidth, accessoryHeightNow))

    val searchPlaceable = searchMeasurable
      ?.measure(Constraints.fixed(trailingWidth.coerceAtLeast(1), rowHeight))

    layout(width, barHeight) {
      // With an accessory the row is always the full width — there is nothing left over to push to
      // one side — so [foldAlignment] has nothing to decide and is ignored. Without one: open, the
      // pair spans the whole width and every alignment gives the same answer, because there is no
      // slack to distribute. Folded, the slack is the whole point, and the travel is interpolated so
      // the fold reads as one object contracting rather than as a control that jumps.
      val pairLeft = if (accessoryPlaceable != null) {
        0
      } else {
        val pairWidth = capsuleWidth + gap + trailingWidth
        val slack = (width - pairWidth).coerceAtLeast(0)
        val foldedLeft = when (foldAlignment) {
          FluidFoldAlignment.Start -> 0
          FluidFoldAlignment.Center -> slack / 2
          FluidFoldAlignment.End -> slack
        }
        lerp(0, foldedLeft, f)
      }
      capsulePlaceable.place(pairLeft, rowTop)
      val trailingLeft = pairLeft + capsuleWidth + gap
      trailingPlaceable?.place(width - trailingWidth, rowTop)
      // The field rides exactly where the trailing control is, because it *is* it.
      searchPlaceable?.place(width - trailingWidth, rowTop)

      if (accessoryPlaceable != null) {
        val openLeft = 0
        val foldedLeft = trailingLeft
        val left = lerp(openLeft, foldedLeft, f)
        accessoryPlaceable.place(left, lerp(0, (rowHeight - accessoryHeightNow) / 2, f))
      }
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
  /** What a tap means: folded, it means "open me". */
  fold: () -> Float,
  /**
   * How far the capsule is closed, which is not the same question.
   *
   * The fold closes it and so does the search, and while the search has it closed a tap on the one
   * tab left is not a request to open the bar — it is the way back to that tab. Keeping the two
   * apart is the difference between those two behaviours.
   */
  squeeze: () -> Float,
  tabIcon: (@Composable (item: FluidTabItem, selected: Boolean) -> Unit)?,
  tint: GlassTint,
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

  // Letti *adesso*, non catturati allora. L'animazione del trascinamento sopravvive alle
  // ricomposizioni (e deve: e' lei che tiene la molla), quindi le sue lambda vivono piu' a lungo di
  // qualunque valore abbiano chiuso dentro. Con la rotta catturata alla prima composizione, il
  // rilascio confrontava la scheda bersaglio con una selezione vecchia di tre navigazioni: credeva
  // di essere gia' li', declassava la selezione a riselezione, e la pillola tornava indietro — una
  // volta per ogni trascinamento, su ogni barra dell'app.
  val currentSelectedRoute by rememberUpdatedState(selectedRoute)
  val currentItems by rememberUpdatedState(items)
  val currentOnSelect by rememberUpdatedState(onSelect)
  val currentOnReselect by rememberUpdatedState(onReselect)
  val currentOnExpandRequest by rememberUpdatedState(onExpandRequest)

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
      // Critically damped, so the stretch tracks the drag instead of ringing after it. The
      // library's underdamped default is what made the indicator wobble as it crossed each tab;
      // see GlassDragAnimation.velocityDampingRatio.
      velocityDampingRatio = 1f,
      // Holding the lens and pushing it sideways moves the selection with the finger, tab by tab,
      // and lets go on whichever one it is nearest. This is the whole reason the indicator is a
      // `GlassDragAnimation` and not an `Animatable`, and leaving it off — which it was — turned
      // the capsule into a row of buttons wearing a lens.
      //
      // `size` here is the lens's own box, and while the bar is open the lens is exactly one tab
      // wide: it *is* the pitch, so no measurement has to be plumbed down from the layout.
      onDrag = { size, dragAmount ->
        if (squeeze() <= FoldedEnough) {
          updateValue(
            fluidTabDragTarget(
              from = targetValue,
              dragX = dragAmount.x,
              pitch = size.width,
              count = currentItems.size,
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
          currentOnExpandRequest?.invoke()
          return@GlassDragAnimation
        }
        val target = targetValue.fastRoundToInt().fastCoerceIn(0, currentItems.size - 1)
        animateToValue(target.toFloat())
        currentItems.getOrNull(target)?.let { item ->
          if (item.route == currentSelectedRoute) currentOnReselect(item) else currentOnSelect(item)
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
        fold = squeeze,
        tabIcon = tabIcon,
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
          tint = tint,
          shape = FluidCapsuleShape,
          role = GlassRole.Floating,
          exports = barGlass,
        ),
    ) {
      FluidFoldingTabRow(
        fold = squeeze,
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
        fold = squeeze,
        selectedIndex = selectedIndex,
        insetPx = insetPx,
        content = tabs,
      )
    }

    // 3. The lens.
    Box(
      modifier = Modifier
        .fluidFoldingIndicatorLayout(
          fold = squeeze,
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
          // The lens the vendored bar this design comes from had, and the engine did not.
          //
          // Three things, and they are one thing: the indicator has to read as a *piece of glass*
          // sitting on the bar even when nobody is touching it. The bend is rightly a press —
          // holding it is what asks for it — but the rim and the shadow were multiplied down by the
          // same number, so at rest there was neither, and the shape came entirely from its own
          // wash. Over a bar standing on a bright cover that wash is nearly the cover's colour and
          // the selection simply disappeared. The floor is the vendored bar's own (half a rim,
          // a third of a shadow), and the press takes both the rest of the way.
          //
          // The inner shadow is the other half: it is the thickness of the pane, seen from inside,
          // and it is what makes the press read as pressing *into* something.
          optics = remember {
            GlassDefaults.optics(GlassRole.Interactive).copy(
              blurScale = 0f,
              edgeFloor = FluidTabIndicatorEdgeFloor,
              innerShadowRadius = 8.dp,
              innerShadowAlpha = 1f,
            )
          },
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
  tabIcon: (@Composable (item: FluidTabItem, selected: Boolean) -> Unit)?,
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
      if (tabIcon != null) {
        // The app draws it, and the tint is its business: it is being given the selection precisely
        // so it can answer with a different glyph, and a colour imposed here would fight that.
        tabIcon(item, selected)
      } else {
        Icon(
          imageVector = item.icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(24.dp),
        )
      }
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

/**
 * The accessory band's width: the whole bar above the row, or the room left between the closed
 * capsule and the trailing control.
 *
 * Kept out here with the rest of the fold's arithmetic for the same reason they are: it runs on the
 * measure pass of every frame, and it is the number that decides whether the band clears the
 * capsule or sits on top of it — which is not something a screenshot makes obvious.
 */
internal fun fluidAccessoryWidth(
  width: Int,
  capsuleWidth: Int,
  gap: Int,
  trailingWidth: Int,
  fold: Float,
): Int {
  val folded = (width - capsuleWidth - gap - trailingWidth - gap).coerceAtLeast(1)
  return lerp(width.coerceAtLeast(1), folded, fold.fastCoerceIn(0f, 1f))
}

/** How tall the whole bar is: the band plus the open row, contracting to the folded row alone. */
internal fun fluidBarHeight(openHeight: Int, rowHeight: Int, bandHeight: Int, fold: Float): Int =
  lerp(bandHeight + openHeight, rowHeight, fold.fastCoerceIn(0f, 1f))

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

  /**
   * How tall an accessory band is, open and folded alike.
   *
   * A hair shorter than the folded row, so that folded it sits *inside* the row with a margin all
   * round instead of matching its edges — which would read as a second bar butted against the first
   * rather than as something carried by it.
   */
  val AccessoryHeight: Dp = 50.dp
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

  /** The same, for a bar carrying an accessory band of [AccessoryHeight]. */
  fun contentInsetWithAccessory(accessoryHeight: Dp = AccessoryHeight): Dp =
    ContentInset + accessoryHeight + Spacing
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

  /**
   * While this is true the bar stops answering the page, and stays exactly where it is.
   *
   * It is not a convenience. Something else can be *measuring* the bar — a surface growing out of
   * an accessory's rectangle, a shared element mid-flight — and a bar that folds under it moves the
   * very thing that journey is being drawn from, which shows up as a window that starts from the
   * wrong place and arrives crooked. A scroll during the lock is dropped, not stored: releasing the
   * lock must not replay a fold nobody is asking for any more.
   */
  var locked: Boolean by mutableStateOf(false)

  private var pending = 0f

  val connection: NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      if (locked) {
        pending = 0f
        return Offset.Zero
      }
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
private const val SlotAccessory = "fluid:folding-accessory"
private const val SlotSearch = "fluid:folding-search"

