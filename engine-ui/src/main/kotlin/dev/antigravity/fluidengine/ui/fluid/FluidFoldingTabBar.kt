package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.math.abs

/**
 * The bar this engine used to have, kept under the name it had.
 *
 * It is [FluidFloatingTabBar] underneath now. Nothing in this file decides how anything looks any
 * more: an app that was calling this gets the better glass - the refraction under the puck, the
 * hidden tinted row the puck samples back through itself, the stretch that follows a dragged
 * finger - without touching a line. That is the entire reason an app depends on an engine instead
 * of copying one, and an engine that improves a component by deleting it has not improved anything
 * for anybody who was using it.
 *
 * Deprecated rather than deleted, and the difference matters both ways. Deleting it would make every
 * app pay for an improvement it did not ask for, on a schedule it did not choose; keeping it silently
 * for ever would leave the new bar's own abilities unreachable behind a signature that cannot express
 * them. So: it works, it *is* the new bar, and the deprecation says where to go when there is a
 * reason to go there.
 *
 * Everything below the bar itself - the fold, the scroll connection that drives it, the alignment -
 * is unchanged and still its own.
 */


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
 * The folding tab bar, on top of [FluidFloatingTabBar].
 *
 * The fold is the one real translation. This bar was handed a number between nought and one and drew
 * itself that much folded; the new one has two states and a scroll connection that decides which, so
 * the number is turned back into a decision here, at the same halfway point this bar always used. A
 * caller driving it from [FluidBarFold] sees what it saw.
 *
 * Two arguments are accepted and ignored, and saying so is better than pretending otherwise:
 * [tint], because the new bar's material is its own and no longer a colour a caller passes; and
 * [trailing], which has no equivalent - the new bar's standalone tab is a *tab*, with a key and a
 * selection, and quietly turning an arbitrary composable into one would be a worse surprise than
 * leaving it out.
 *
 * [foldAlignment] used to be a third, on the reasoning that the new bar folds to a shape rather
 * than towards a side. It folds towards a side as well, and it always did — it simply packed to
 * the centre with no way to say otherwise. It is honoured now, and its default has moved with the
 * new bar's: [FluidFoldAlignment.Start].
 */
@Deprecated(
  "Use FluidFloatingTabBar, which this now is. It also does what this signature cannot ask for: " +
    "an accessory inside the bar, a search field that grows in it, and tabs that are composables.",
  ReplaceWith("FluidFloatingTabBar"),
)
@Composable
fun FluidFoldingTabBar(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  backdrop: GlassBackdropState,
  fold: () -> Float,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
  onExpandRequest: (() -> Unit)? = null,
  @Suppress("UNUSED_PARAMETER") trailing: (@Composable () -> Unit)? = null,
  tabIcon: (@Composable (item: FluidTabItem, selected: Boolean) -> Unit)? = null,
  accessory: (@Composable () -> Unit)? = null,
  @Suppress("UNUSED_PARAMETER") accessoryHeight: Dp = FluidFoldingTabBarDefaults.AccessoryHeight,
  searchMode: Boolean = false,
  searchContent: (@Composable (Modifier) -> Unit)? = null,
  foldAlignment: FluidFoldAlignment = FluidFoldAlignment.Start,
  @Suppress("UNUSED_PARAMETER") tint: GlassTint = GlassDefaults.floatingTint(),
) {
  if (items.isEmpty()) return
  val scroll = rememberFluidFloatingTabBarScrollConnection()

  // Read inside an effect rather than during composition: `fold` is a lambda a caller drives from an
  // animation, so reading it here would recompose the whole bar on every frame of the fold - which
  // is exactly what the old bar went to some trouble not to do.
  LaunchedEffect(scroll) {
    snapshotFlow { fold() >= FoldedEnough }.collect { folded ->
      if (folded) scroll.inline() else scroll.expand()
    }
  }
  // Folded, the bar must not fold itself further on a scroll: the caller owns the fold here, and two
  // things driving one state fight each other.
  LaunchedEffect(scroll) { scroll.locked = true }

  FluidFloatingTabBar(
    selectedTabKey = selectedRoute,
    scrollConnection = scroll,
    modifier = modifier,
    backdrop = backdrop,
    inlineAccessory = accessory?.let { band -> { _, _ -> band() } },
    expandedAccessory = accessory?.let { band -> { _, _ -> band() } },
    searchMode = searchMode,
    searchBarContent = searchContent,
    foldAlignment = when (foldAlignment) {
      FluidFoldAlignment.Start -> Alignment.Start
      FluidFoldAlignment.Center -> Alignment.CenterHorizontally
      FluidFoldAlignment.End -> Alignment.End
    },
  ) {
    items.forEach { item ->
      val selected = item.route == selectedRoute
      tab(
        key = item.route,
        title = { Text(item.label) },
        icon = {
          if (tabIcon != null) tabIcon(item, selected)
          else Icon(item.icon, contentDescription = null)
        },
        onClick = {
          // A tap on a folded bar means "open", not "select": folded, the only tab left is the one
          // you are already on, so a reselection there is "take me to the top" - the opposite of
          // what somebody who just tapped a closed bar wants.
          when {
            fold() >= FoldedEnough && onExpandRequest != null -> onExpandRequest()
            selected -> onReselect(item)
            else -> onSelect(item)
          }
        },
      )
    }
  }
}

/** The numbers a screen needs in order to leave room for this bar. The new bar's own, now. */
object FluidFoldingTabBarDefaults {
  val OpenHeight: Dp get() = FluidFloatingTabBarDefaults.OpenHeight
  val FoldedHeight: Dp get() = FluidFloatingTabBarDefaults.FoldedHeight
  val SoloFoldedHeight: Dp get() = FluidFloatingTabBarDefaults.FoldedSoloHeight
  val Spacing: Dp get() = FluidFloatingTabBarDefaults.Spacing
  val Inset: Dp = 4.dp
  val AccessoryHeight: Dp = 50.dp
  val HorizontalMargin: Dp get() = FluidFloatingTabBarDefaults.HorizontalMargin
  val BottomMargin: Dp get() = FluidFloatingTabBarDefaults.BottomMargin

  /** Vertical space a screen must leave free so its content clears the bar at its tallest. */
  val ContentInset: Dp get() = FluidFloatingTabBarDefaults.ContentInset

  /** The same, for a bar carrying an accessory band of [AccessoryHeight]. */
  fun contentInsetWithAccessory(accessoryHeight: Dp = AccessoryHeight): Dp =
    FluidFloatingTabBarDefaults.contentInsetWithAccessory(accessoryHeight)
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


private const val FoldedEnough = 0.5f

/** How far a scroll must travel in one direction before the bar answers it. See [FluidBarFold]. */
private const val HideCommitDp = 36f
private const val RevealCommitDp = 10f
