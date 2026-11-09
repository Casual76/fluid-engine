package dev.antigravity.fluidengine.ui.fluid

import dev.antigravity.fluidengine.ui.theme.LocalRouteMotionSignals
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.offset
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
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
import androidx.compose.ui.unit.isFinite
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
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import dev.antigravity.fluidengine.ui.page.FluidPageBarSpec
import dev.antigravity.fluidengine.ui.page.LocalFluidPageBar
import dev.antigravity.fluidengine.ui.page.LocalFluidPageTransition
import dev.antigravity.fluidengine.ui.page.RegisterFluidPageBar
import androidx.compose.runtime.currentCompositionLocalContext

/**
 * Coordinates chrome that lives above the navigation host without sharing render layers between
 * destinations.
 *
 * Every [FluidScreen] owns its own [GlassBackdropState]. Screens register that state while composed,
 * and the one that is actually **in front** becomes the source sampled by the floating tab bar, so
 * no two screens ever write into the same graphics-layer instances.
 *
 * "In front" is not "registered last", and the difference is a whole gesture long. During a
 * predictive back both destinations are composed for as long as the finger stays down: electing the
 * newest registration made the bar refract the destination from the very first frame, so the pill
 * changed colour while the page underneath had not moved at all. The screen tells the controller
 * whether it is in front — see `LocalFluidRouteFront` — and the last registration wins only among
 * those that say yes. With no route host in the tree nobody says no, and the old behaviour stands.
 */
/**
 * Quale registrazione la chrome deve rifrangere: l'ultima fra quelle davanti, e se nessuna si
 * dichiara davanti l'ultima e basta.
 *
 * Funzione a se' perche' e' l'unica cosa che vale la pena verificare del registro, ed e' quella che
 * si rompe in silenzio: sbagliarla non fa fallire niente, fa solo cambiare colore alla pillola nel
 * momento sbagliato di un gesto.
 */
internal fun <K> frontMostBackdropKey(order: List<K>, front: Set<K>): K? =
  order.lastOrNull { it in front } ?: order.lastOrNull()

@Stable
class FluidChromeController internal constructor(
  private val bottomBarVelocityThresholdPx: Float,
  private val densityPx: Float,
) {
  private val backdrops = LinkedHashMap<Any, GlassBackdropState>()
  private val frontKeys = mutableSetOf<Any>()
  private var transitionOwner: Any? = null
  private var transitionBackdrop: GlassBackdropState? = null
  private val refreshers = HashMap<Any, () -> Unit>()
  private val _activeBackdrop = mutableStateOf<GlassBackdropState?>(null)
  private val _bottomBarOffsetPx = mutableFloatStateOf(0f)
  private var bottomBarTravelPx = 0f
  private var pendingScrollPx = 0f

  val activeBackdrop: State<GlassBackdropState?> = _activeBackdrop
  val bottomBarOffsetPx: State<Float> = _bottomBarOffsetPx

  internal fun registerBackdrop(key: Any, backdrop: GlassBackdropState) {
    backdrops.remove(key)
    backdrops[key] = backdrop
    electActiveBackdrop()
  }

  /**
   * Dice se la schermata sta occupando davvero lo schermo.
   *
   * Separata dalla registrazione perche' i due momenti non coincidono: una schermata entra in
   * composizione all'inizio di una transizione e diventa davanti solo quando la transizione si
   * compie — o non lo diventa mai, se il gesto viene annullato.
   */
  internal fun setBackdropFront(key: Any, isFront: Boolean) {
    if (!backdrops.containsKey(key)) return
    val changed = if (isFront) frontKeys.add(key) else frontKeys.remove(key)
    if (changed) {
      electActiveBackdrop()
      // La barra si rivela quando una pagina arriva davvero davanti, non quando entra in scena:
      // altrimenti bastava iniziare un gesto di back per farla ricomparire.
      if (isFront) revealBottomBar()
    }
  }

  internal fun unregisterBackdrop(key: Any) {
    refreshers.remove(key)
    backdrops.remove(key) ?: return
    frontKeys.remove(key)
    electActiveBackdrop()
  }

  /**
   * L'ultima registrazione fra quelle davanti, e se nessuna lo dichiara l'ultima e basta.
   *
   * Il ripiego tiene in piedi tutto cio' che non vive dentro un host di rotta: anteprime, la
   * gallery di esempio, una schermata sola dentro un'Activity.
   */
  private fun electActiveBackdrop() {
    val key = frontMostBackdropKey(backdrops.keys.toList(), frontKeys)
    _activeBackdrop.value = transitionBackdrop ?: key?.let(backdrops::get)
  }

  /**
   * Mentre due pagine si muovono (3.0, l'host delle pagine) la chrome rifrange **tutte e due**,
   * ognuna dove sta in quel fotogramma: [backdrop] e' l'impilamento delle due registrazioni, e
   * scavalca l'elezione finche' [owner] non lo toglie (null). Senza, il vetro della tab bar cambiava
   * fondale di colpo a meta' gesto, quando il davanti passava di mano.
   */
  internal fun setTransitionBackdrop(owner: Any, backdrop: GlassBackdropState?) {
    if (backdrop != null) {
      transitionOwner = owner
      transitionBackdrop = backdrop
    } else if (transitionOwner === owner) {
      transitionOwner = null
      transitionBackdrop = null
    } else {
      return
    }
    electActiveBackdrop()
  }

  /** L'aggiornamento della pagina registrata sotto [key], o null se non ne ha (o ne ha uno in corso). */
  internal fun setRefresh(key: Any, onRefresh: (() -> Unit)?) {
    if (onRefresh == null) refreshers.remove(key) else refreshers[key] = onRefresh
  }

  /**
   * Aggiorna la pagina che sta davanti, la stessa che la barra delle schede rifrange: e' quello che
   * fa Ctrl+R su una tastiera fisica, cioe' il gesto di tirare giu' la pagina senza toccarla.
   *
   * Davanti vuol dire la stessa elezione del fondale della barra: durante un gesto di back la
   * pagina che sta uscendo resta quella davanti finche' il gesto non si chiude, e il dettaglio di
   * una pagina a due pannelli non si registra, quindi si aggiorna l'elenco — cioe' la sezione.
   *
   * @return false se la pagina davanti non si aggiorna (non ha un `onRefresh`, o lo sta gia' facendo).
   */
  fun refreshFront(): Boolean {
    val key = frontMostBackdropKey(backdrops.keys.toList(), frontKeys) ?: return false
    val refresh = refreshers[key] ?: return false
    refresh()
    return true
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

/**
 * Quanto spazio questa schermata mette fra due item della sua lista.
 *
 * Pubblicato perche' un helper che emette PIU' item che devono leggersi come UNO solo non ha altro
 * modo di sapere quanta aria deve richiudere. Vedi `FluidGroupSegment`.
 */
val LocalFluidItemSpacing = compositionLocalOf { FluidScreenDefaults.ItemSpacing }

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

  /**
   * Quanto puo' allargarsi il contenuto di una pagina prima che il margine cresca al posto suo.
   *
   * Una riga di testo oltre gli ottanta caratteri si legge peggio, e su un tablet in orizzontale
   * una lista a tutta larghezza ne fa duecento. Il numero e' quello di una colonna di lettura comoda
   * a bodyLarge; una schermata che vuole davvero tutta la larghezza passa [Dp.Infinity].
   */
  val ContentMaxWidth: Dp = 760.dp

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
internal class FluidTitleMorphState(
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

  /**
   * Finche' risponde true le ancore non si rimisurano (3.0, l'host delle pagine).
   *
   * Dentro un host la barra sta nell'host, ferma, e la pagina si muove sotto: misurata mentre si
   * muove, l'ancora compatta prendeva la traslazione della pagina (o la parallasse di quella coperta)
   * e il titolo, a riposo, finiva un terzo di schermo piu' in la'. Le ancore valgono nelle coordinate
   * **non trasformate** della pagina, cioe' quelle che ha a riposo e davanti: si misurano solo li', e
   * [refresh] le rifa' appena il movimento finisce.
   */
  var frozen: () -> Boolean = { false }

  /** Rifa' le ancore con le coordinate che ha, appena [frozen] torna false. */
  fun refresh() = rebuildCompact()

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
    if (frozen()) return
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
internal fun rememberCompactTitleStyle(expanded: TextStyle): TextStyle = remember(expanded) {
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
  /**
   * La misura di lettura: oltre questa larghezza il contenuto resta centrato e il margine cresce.
   *
   * Vale per la lista, titolo grande compreso, perche' il titolo e' un item della lista e sta
   * allineato con quello che introduce. Non vale per la barra agganciata e per l'[overlay], che
   * appartengono allo schermo e non alla colonna. Vedi [fluidScreenPadding].
   */
  contentMaxWidth: Dp = FluidScreenDefaults.ContentMaxWidth,
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
  /**
   * Dove la schermata scrive quanto e' larga la sua colonna, per il [content] che vuole disporsi in
   * colonne ([fluidColumns]). Null — il default — e la schermata non scrive niente.
   */
  metrics: FluidScreenMetrics? = null,
  content: LazyListScope.() -> Unit,
) {
  // La larghezza si legge qui, una volta, e diventa un margine: e' l'unica riga che distingue una
  // pagina da telefono allargata da una pagina da tablet. Tutto il resto della schermata non sa
  // di essere su un tablet, e non deve saperlo — salvo chi lo chiede con [metrics].
  BoxWithConstraints(modifier = modifier) {
    val padding = fluidScreenPadding(maxWidth, horizontalPadding, contentMaxWidth)
    if (metrics != null) {
      // Scritto in composizione e non in un effetto: la lista che lo legge si misura dopo, nello
      // stesso passaggio, e cosi' il primo fotogramma ha gia' le colonne giuste invece di
      // mostrarne una e poi saltare.
      val width = if (maxWidth.isFinite) maxWidth - padding * 2 else Dp.Unspecified
      if (metrics.contentWidth != width) metrics.contentWidth = width
      val fontScale = LocalDensity.current.fontScale
      if (metrics.fontScale != fontScale) metrics.fontScale = fontScale
    }
    FluidScreenLayout(
      title = title,
      subtitle = subtitle,
      onBack = onBack,
      actions = actions,
      titleFacets = titleFacets,
      listState = listState,
      isRefreshing = isRefreshing,
      onRefresh = onRefresh,
      horizontalPadding = padding,
      itemSpacing = itemSpacing,
      extraBottomPadding = extraBottomPadding,
      // Una pagina che si e' allargata oltre la misura di lettura ha il contenuto fino all'angolo
      // dove il fondale mette il suo motivo: li' c'e' gia' la fascia col proprio, e due figure
      // accostate sono un fondale doppio. La lavata resta, il motivo no. Vedi FluidAmbient.showMotif.
      ambient = ambient?.let {
        val wide = maxWidth.isFinite && maxWidth - padding * 2 > FluidScreenDefaults.ContentMaxWidth
        if (wide) it.copy(showMotif = false) else it
      },
      overlay = overlay,
      content = content,
    )
  }
}

@Composable
private fun FluidScreenLayout(
  title: String,
  subtitle: String?,
  onBack: (() -> Unit)?,
  actions: @Composable RowScope.() -> Unit,
  titleFacets: List<String>,
  listState: LazyListState,
  isRefreshing: Boolean,
  onRefresh: (() -> Unit)?,
  horizontalPadding: Dp,
  itemSpacing: Dp,
  extraBottomPadding: Dp,
  ambient: FluidAmbient?,
  overlay: @Composable BoxScope.(GlassBackdropState) -> Unit,
  content: LazyListScope.() -> Unit,
) {
  // A render layer may have only one writer. Keeping this state local is what makes overlapping
  // NavHost destinations (including predictive back) safe: each screen records into its own layers.
  val bodyBackdrop = rememberGlassBackdrop()
  /**
   * Il fondale che qualcun altro sta gia' dipingendo, se c'e': [FluidAmbientSurface].
   *
   * Quando c'e', **vince sul proprio**, e non e' una preferenza: una finestra a piu' pannelli in cui
   * ogni pannello dipinge il suo fondale si legge come tre telefoni appoggiati uno accanto all'altro,
   * con due cuciture verticali nel mezzo. Una finestra, un fondale; i pannelli ci stanno sopra.
   */
  val inheritedCanvas = LocalFluidCanvasBackdrop.current
  val paintsOwnCanvas = ambient != null && inheritedCanvas == null
  // Only allocated when there is actually a canvas. Holding the layer unconditionally was simpler
  // and cost every screen in the family a spare `RenderNode` it would never draw into — which is
  // exactly the kind of "it is only one" that adds up to a tab switch you can see. The `key` is what
  // makes the conditional `remember` safe: turning a canvas on or off discards this slot rather than
  // shifting the composition's shape under it.
  val ownCanvas = key(paintsOwnCanvas) {
    if (paintsOwnCanvas) rememberGlassBackdrop() else null
  }
  val canvasBackdrop = ownCanvas ?: inheritedCanvas
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

  titleMorphState.FollowPageHost()

  DisposableEffect(chromeController, chromeRegistration, backdrop) {
    chromeController?.registerBackdrop(chromeRegistration, backdrop)
    onDispose { chromeController?.unregisterBackdrop(chromeRegistration) }
  }

  // Chi e' davanti lo decide la transizione di rotta, non l'ordine di composizione: durante un back
  // predittivo questa schermata resta la sorgente del vetro finche' il gesto non e' confermato.
  val routeFront = LocalFluidRouteFront.current
  val isRouteFront by routeFront
  LaunchedEffect(chromeController, chromeRegistration, isRouteFront) {
    chromeController?.setBackdropFront(chromeRegistration, isRouteFront)
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
        // Solo davanti (3.0): dentro un host di pagine quella coperta resta composta, e una lista
        // coperta che arriva in cima non deve far ricomparire la barra delle schede di un'altra.
        if (isAtTop && routeFront.value) chromeController.revealBottomBar()
      }
  }

  LocalFluidScrollToTop.current?.let { bus ->
    LaunchedEffect(bus, listState) {
      snapshotFlow { bus.signal.intValue }
        .drop(1)
        // Solo la pagina davanti: riselezionare la scheda non scorre quella coperta, che resta
        // composta sotto (3.0) e non si vede.
        .collect { if (routeFront.value) listState.animateScrollToItem(0) }
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
  // Lo stesso aggiornamento, raggiungibile anche senza il dito: vedi [FluidChromeController.refreshFront].
  SideEffect { chromeController?.setRefresh(chromeRegistration, onRefresh.takeUnless { isRefreshing }) }

  // Chi dipinge il fondo dichiara anche il colore di quello che ci va sopra, **barra compresa**.
  //
  // Senza, il testo eredita LocalContentColor, che fuori da un Surface vale nero: un'app che non
  // avvolge tutto l'albero in un Surface si ritrova il titolo grande nero su fondo nero. Il primo
  // tentativo aveva avvolto solo la lista, e il titolo restava nero lo stesso: quello che si vede
  // durante il morph e' la copia della **barra**, trasformata sull'ancora del titolo grande.
  // Il vetro si assottiglia mentre la pagina corre e torna intero quando si ferma: vedi
  // [FluidGlassQuality]. Sta qui e non nelle singole superfici perché la velocità e' un fatto
  // della *pagina*, e perché cosi' ogni schermata dell'app lo eredita senza aggiungere una riga.
  // Il tetto viene dal movimento fra pagine: durante una transizione esistono due schermate intere
  // e il conto del vetro raddoppia nel fotogramma che ne ha meno.
  val routeSignals = LocalRouteMotionSignals.current
  val glassQuality = rememberFluidGlassQuality { routeSignals.glassQuality }
  val qualityConnection = remember(glassQuality) {
    fluidGlassQualityScrollConnection(glassQuality)
  }

  CompositionLocalProvider(
    LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    LocalFluidCanvasBackdrop provides canvasBackdrop,
    LocalFluidGlassQuality provides glassQuality,
  ) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      // Opaca solo quando il fondale e' suo: dentro un pannello lo dipinge la finestra, e coprirlo
      // qui rimetterebbe la cucitura che la superficie condivisa esiste per togliere.
      .then(if (inheritedCanvas == null) Modifier.background(MaterialTheme.colorScheme.background) else Modifier)
      // The origin of the title morph's coordinate space. See [FluidTitleMorphState]: anchors are
      // screen-local, so the compact slot's measurement is converted against these coordinates.
      .onGloballyPositioned(titleMorphState::onScreenPositioned),
  ) {
    val contentTranslation: () -> Float = { overscroll.offsetPx }

    // The canvas, and its recording, come first — which is the whole of why a card in the list can
    // be glass. This layer is closed before a single item of the body is composed, so it is
    // structurally incapable of containing one.
    if (ambient != null && ownCanvas != null) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .glassBackdropSource(ownCanvas),
      ) {
        FluidAmbientCanvas(ambient)
      }
    }

    val body: @Composable () -> Unit = {
      CompositionLocalProvider(
        LocalFluidItemSpacing provides itemSpacing,
      ) {
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
            if (canvasBackdrop != null) {
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
    }

    // La barra della pagina e' di questa schermata, non del suo contenuto: una FluidScreen dentro
    // (un pannello, un foglio) disegna la sua come sempre.
    CompositionLocalProvider(LocalFluidPageBar provides null) { body() }

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
      barTint = null,
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

    CompositionLocalProvider(LocalFluidPageBar provides null) { overlay(backdrop) }
  }
  }
}

/**
 * Le ancore del titolo si misurano solo a pagina ferma e davanti (3.0): vedi
 * [FluidTitleMorphState.frozen]. Fuori da un host di pagine non cambia niente.
 */
@Composable
internal fun FluidTitleMorphState.FollowPageHost() {
  val pageTransition = LocalFluidPageTransition.current
  val routeFront = LocalFluidRouteFront.current
  frozen = if (pageTransition == null) {
    { false }
  } else {
    { pageTransition.isMoving || !routeFront.value }
  }
  if (pageTransition != null) {
    LaunchedEffect(this, pageTransition) {
      snapshotFlow { frozen() }.collect { stopped -> if (!stopped) refresh() }
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
internal fun rememberCollapseProgress(
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
internal fun rememberTitleFacet(
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
internal fun titleSnapDeltaPx(
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
internal fun FluidLargeTitle(
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
        // Il titolo grande e' l'intestazione della pagina: TalkBack lo trova scorrendo per titoli.
        .semantics { heading() }
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
internal fun FluidTopBar(
  title: String,
  backdrop: GlassBackdropState,
  /** What the bar is made of. Null takes the family's own; see GlassDefaults.barTint. */
  barTint: GlassTint?,
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
  // Dentro un host di pagine (3.0) la barra non si disegna qui: si descrive, e la disegna l'host,
  // una sola, ferma sopra le pagine che si muovono. Fuori, la barra di sempre.
  val slot = LocalFluidPageBar.current
  if (slot != null) {
    RegisterFluidPageBar(
      slot,
      FluidPageBarSpec(
        title = title,
        activeFacet = activeFacet,
        onBack = onBack,
        actions = actions,
        context = currentCompositionLocalContext,
        collapseProgress = collapseProgress,
        glassIntensity = glassIntensity,
        morphState = morphState,
        compactStyle = compactStyle,
        contentTranslation = contentTranslation,
        fontScale = fontScale,
        reducedMotion = reducedMotion,
        onTapTitle = onTapTitle,
        barTint = barTint,
        backdrop = backdrop,
      ),
    )
    return
  }

  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val tint = barTint ?: GlassDefaults.barTint()
  // The bar publishes its own finished material so the actions standing on it refract *it* rather
  // than the page three layers down — a lens resting on frosted glass shows the frosting.
  val barGlass = rememberGlassBackdrop()
  val controlBackdrop = rememberCombinedGlassBackdrop(backdrop, barGlass)

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight + FluidScreenDefaults.GlassFadeTail),
  ) {
    FluidTopBarGlass(
      backdrop = backdrop,
      tint = tint,
      intensity = { glassIntensity.value },
      exports = barGlass,
    )
    CompositionLocalProvider(
      LocalGlassBackdrop provides controlBackdrop,
      // Un controllo nella barra sta sul vetro della barra, non sul canvas della pagina che la barra
      // copre (3.0.1): chi chiede prima il canvas (chip, segmentato, campo di ricerca, tasto di vetro)
      // qui trova la barra.
      LocalFluidCanvasBackdrop provides null,
    ) {
      val controls = remember { FluidTopBarControls() }
      FluidTopBarRow(onTapTitle = onTapTitle) {
        FluidTopBarTitle(
          title = title,
          collapseProgress = collapseProgress,
          activeFacet = activeFacet,
          morphState = morphState,
          compactStyle = compactStyle,
          contentTranslation = contentTranslation,
          fontScale = fontScale,
          reducedMotion = reducedMotion,
          controls = controls,
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(modifier = Modifier.onSizeChanged { controls.startPx = it.width }) {
            if (onBack != null) {
              FluidBackButton(onBack)
            } else {
              Spacer(Modifier.width(FluidScreenDefaults.HorizontalPadding))
            }
          }
          Spacer(Modifier.weight(1f))
          Row(
            modifier = Modifier.onSizeChanged { controls.endPx = it.width },
            verticalAlignment = Alignment.CenterVertically,
          ) {
            FluidTopBarActions(actions)
            Spacer(Modifier.width(6.dp))
          }
        }
      }
    }
  }
}

/**
 * Il vetro della barra, da solo (3.0: la barra divisa in vetro, titolo e controlli, perche' l'host
 * delle pagine ne disegna una per tutte). Clip only the sampled material: the title itself is allowed
 * to travel below this drawing region while it is still anchored to the large heading; clipping the
 * whole bar cut the lower half of the glyphs during the first third of the morph.
 */
@Composable
internal fun BoxScope.FluidTopBarGlass(
  backdrop: GlassBackdropState,
  tint: GlassTint,
  intensity: () -> Float,
  exports: GlassBackdropState?,
) {
  Box(
    modifier = Modifier
      .matchParentSize()
      .glassSurface(
        state = backdrop,
        tint = tint,
        edge = GlassEdge.None,
        falloff = GlassFalloff.FadeDown,
        intensity = intensity,
        exports = exports,
      ),
  )
}

/**
 * La riga dei controlli: sotto la barra di stato, alta 44 dp, toccata riporta in cima. Tap-the-bar-
 * to-scroll-up covers the control row only and is exposed as a named action, never as the anonymous
 * unchecked selection control that `selectable` would create.
 */
@Composable
internal fun FluidTopBarRow(
  onTapTitle: (() -> Unit)?,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val interactionSource = remember { MutableInteractionSource() }
  val scrollToTopLabel = LocalFluidLabels.current.scrollToTop
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .height(statusBar + FluidScreenDefaults.ControlRowHeight),
  ) {
    Spacer(Modifier.height(statusBar))
    Box(
      modifier = modifier
        .fillMaxWidth()
        .height(FluidScreenDefaults.ControlRowHeight)
        .then(
          if (onTapTitle != null) {
            Modifier
              .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onTapTitle,
              )
              .semantics { contentDescription = scrollToTopLabel }
          } else {
            Modifier
          },
        ),
      contentAlignment = Alignment.Center,
      content = content,
    )
  }
}

/**
 * Quanto occupano i controlli di una barra, in px: il tasto indietro (o il margine) a inizio riga, le azioni
 * a fine riga. Scritto dalla misura dei controlli, letto dal layout del titolo.
 */
@Stable
internal class FluidTopBarControls {
  var startPx by mutableIntStateOf(0)
  var endPx by mutableIntStateOf(0)
}

/**
 * Il margine del titolo agganciato (3.0.1): quello di sempre ([FluidScreenDefaults.CompactTitleInset]) o, se
 * i controlli di un lato sono piu' larghi, quanto loro piu' un respiro, **uguale ai due lati** perche' il
 * titolo resta al centro. Prima il margine era fisso, e con due o tre azioni un titolo lungo correva sotto i
 * tasti invece di finire con i puntini. Letto in layout: misurare i controlli non ricompone il titolo.
 */
private fun Modifier.fluidTopBarTitleInset(controls: FluidTopBarControls?): Modifier = layout { measurable, constraints ->
  val base = FluidScreenDefaults.CompactTitleInset.roundToPx()
  val needed = controls?.let { maxOf(it.startPx, it.endPx) + FluidTopBarTitleGap.roundToPx() } ?: 0
  val inset = maxOf(base, needed).coerceAtMost(constraints.maxWidth / 2)
  val placeable = measurable.measure(constraints.offset(horizontal = -inset * 2))
  layout(constraints.maxWidth, placeable.height) { placeable.place(inset, 0) }
}

/** Il respiro fra il titolo agganciato e il controllo piu' vicino. */
private val FluidTopBarTitleGap = 8.dp

/** Le azioni di una barra, a destra, con la spaziatura di sempre. */
@Composable
internal fun FluidTopBarActions(
  actions: @Composable RowScope.() -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = actions,
  )
}

/**
 * Come si muove il titolo di una pagina mentre l'host delle pagine la cambia (3.0), letto in disegno.
 *
 * Il titolo grande e' **contenuto**: finche' non e' agganciato viaggia con la sua pagina
 * ([pageX], [pageScale] attorno a [pageCenter]). Agganciato e' **chrome**: sta nella barra e fa il
 * passo laterale e la dissolvenza del cambio di titolo ([chromeX], [chromeAlpha]). In mezzo, lerp
 * con lo stesso smoothstep del morph.
 */
internal class FluidBarTitleMotion(
  val pageX: () -> Float,
  val pageScale: () -> Float,
  val pageCenter: () -> Offset,
  val chromeX: () -> Float,
  val chromeAlpha: () -> Float,
)

/**
 * Il titolo della barra: quello agganciato e, mentre si trasforma, la copia che fa il titolo grande.
 * [morphState] null: una barra senza titolo grande (`FluidPageChrome`), il titolo e' solo quello
 * agganciato.
 */
@Composable
internal fun FluidTopBarTitle(
  title: String,
  collapseProgress: State<Float>,
  activeFacet: State<String?>,
  morphState: FluidTitleMorphState?,
  compactStyle: TextStyle,
  contentTranslation: () -> Float,
  fontScale: Float,
  reducedMotion: Boolean,
  motion: FluidBarTitleMotion? = null,
  /** Quanto occupano i controlli della barra ai due lati: il titolo agganciato si ferma prima (3.0.1). */
  controls: FluidTopBarControls? = null,
) {
  Box(
    modifier = Modifier
      .fillMaxSize()
      .fluidTopBarTitleInset(controls)
      .then(
        if (morphState != null) Modifier.onGloballyPositioned(morphState::onCompactSlotPositioned) else Modifier,
      ),
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
        val morphing = morphState != null && shouldMorphTitle(
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
        if (!morphing || morphState == null) {
          transformOrigin = TransformOrigin.Center
          scaleX = 1f
          scaleY = 1f
          translationX = 0f
          translationY = facetShiftPx
          if (motion != null) {
            val blend = smoothStep(progress)
            translationX = lerp(motion.pageX(), motion.chromeX(), blend)
            alpha *= lerp(1f, motion.chromeAlpha(), blend)
          }
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
        var scale = lerp(1f / CompactTitleScale, 1f, eased)
        val desiredLeft = lerp(morphState.expandedLeftInScreen, compactLeft, eased)
        // Never above the docked position: the title is content, and content is allowed to
        // ride an elastic edge, but nothing is allowed to ride it into the status bar.
        val desiredBaseline = (
          compactBaseline + travel * (1f - progress) + contentTranslation() * (1f - eased)
          ).coerceAtLeast(compactBaseline)

        var tx = desiredLeft - compactLeft
        var ty = desiredBaseline - compactBaseline + localBaseline * (1f - scale) + facetShiftPx
        if (motion != null) {
          // La pagina si muove sotto la barra: il titolo grande e' suo e la segue (scala attorno
          // al centro della pagina compresa), quello agganciato e' della barra e fa il passo suo.
          val pageScale = lerp(motion.pageScale(), 1f, eased)
          if (pageScale != 1f) {
            val center = motion.pageCenter()
            val originX = compactLeft
            val originY = compactBaseline - localBaseline
            tx = center.x + pageScale * (originX + tx - center.x) - originX
            ty = center.y + pageScale * (originY + ty - center.y) - originY
            scale *= pageScale
          }
          tx += lerp(motion.pageX(), motion.chromeX(), eased)
          alpha *= lerp(1f, motion.chromeAlpha(), eased)
        }

        transformOrigin = TransformOrigin(0f, 0f)
        scaleX = scale
        scaleY = scale
        translationX = tx
        translationY = ty
      },
      style = compactStyle,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      onTextLayout = { result -> morphState?.onCompactTextLayout(result) },
    )
    Box(
      modifier = if (motion == null) {
        Modifier
      } else {
        Modifier.graphicsLayer {
          translationX = motion.chromeX()
          alpha = motion.chromeAlpha()
        }
      },
    ) {
      FluidTitleFacet(
        facet = activeFacet.value,
        style = compactStyle,
        reducedMotion = reducedMotion,
      )
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
internal fun FluidBackButton(onBack: () -> Unit) {
  val backLabel = LocalFluidLabels.current.back
  FluidGlassIconButton(
    onClick = onBack,
    modifier = Modifier.padding(start = 6.dp),
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Rounded.ArrowBackIos,
      contentDescription = backLabel,
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
  /**
   * Actions raised by holding the button, in a menu the button **becomes**.
   *
   * A bar has room for two or three lenses, and the app rarely has only two or three verbs — the
   * rest used to simply not exist. Held, the button steps aside and its own rectangle unfolds into
   * the menu, exactly the way [FluidGlassMenuButton] does; tapped, it stays the one-verb control it
   * always was, so nothing is taken away from the person who never discovers the menu.
   */
  actions: (() -> List<FluidContextAction>)? = null,
  /**
   * Quale gesto apre il menu di [actions]: di serie il tenuto premuto, e il tocco resta [onClick]. Con
   * [FluidMenuOpensOn.Tap] il tocco apre il menu e [onClick] non viene chiamato — per l'azione il cui
   * unico mestiere e' il menu (i tre puntini, "Ordina"). Il tenuto premuto apre in tutti e due i casi,
   * ed e' lui che porta il dito sul menu. Dalla 3.0.
   */
  opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
) {
  val iconTint = MaterialTheme.colorScheme.primary
  FluidBarActionImpl(
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    actions = actions,
    opensOn = opensOn,
    buttonFace = {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = iconTint,
        modifier = Modifier.size(22.dp),
      )
    },
    menuFace = { Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp)) },
  )
}

/** Le misure delle azioni della barra con un contenuto proprio ([FluidBarAction] con `content`). */
object FluidBarActionDefaults {
  /**
   * Il lato di un avatar (o di un'immagine tonda) dentro la lente di una [FluidBarAction]: 32 dp in una lente da
   * 44. Il vetro che avanza (6 dp per lato) e' l'orlo che fa leggere il tasto come vetro: un avatar a
   * 44 dp coprirebbe la lente e il tasto sembrerebbe una foto incollata sulla barra.
   */
  val ContentSize: Dp = 32.dp
}

/**
 * Un'azione della barra con un **contenuto tuo** al posto dell'icona: l'avatar dell'utente, un mosaico
 * di account, una foto. E' la stessa lente di vetro, la stessa pressione e lo stesso menu dell'overload
 * con l'icona, e per il menu la stessa regola: se passi [actions], tenendo premuto (o toccando, con
 * [FluidMenuOpensOn.Tap]) il tasto **diventa** il menu, e il menu si richiude nel tasto.
 *
 * Il caso per cui esiste: l'avatar in alto a destra che apre il menu degli account. Per quel menu:
 * [content] e' disegnato **due volte** — nel tasto, e come faccia del tasto mentre il menu si apre e si
 * chiude (l'host lo porta lungo il primo tratto del viaggio) — quindi deve essere **solo disegno**: un
 * [FluidAvatar], un mosaico, un'immagine gia' decodificata, e nessun effetto collaterale (niente
 * `LaunchedEffect` che carica, niente stato catturato che cambi mentre il menu e' aperto).
 *
 * Misure: la lente e' quella di un tasto della barra, 44 dp, e il contenuto sta dentro con un orlo
 * di vetro attorno; [FluidBarActionDefaults.ContentSize] e' il lato giusto per un avatar. Il bersaglio
 * resta 48 dp.
 *
 * **TalkBack: il nome e' [contentDescription].** Il contenuto va **muto** (un [FluidAvatar] senza
 * `contentDescription`): il nome del tasto e' questo parametro, e un avatar che dicesse il suo nome
 * accanto produrrebbe «Alessio Pampaloni, Account, pulsante». Senza [contentDescription] il tasto non ha
 * nome, e va bene solo se e' il contenuto a darlo.
 *
 * @param content il disegno dentro la lente.
 */
@Composable
fun FluidBarAction(
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  actions: (() -> List<FluidContextAction>)? = null,
  opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
  content: @Composable () -> Unit,
) {
  FluidBarActionImpl(
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    actions = actions,
    opensOn = opensOn,
    buttonFace = {
      Box(
        modifier = if (contentDescription != null) {
          Modifier.semantics { this.contentDescription = contentDescription }
        } else {
          Modifier
        },
        contentAlignment = Alignment.Center,
      ) { content() }
    },
    menuFace = content,
  )
}

/**
 * Il corpo di tutte e due le [FluidBarAction]: il tasto di vetro, e il menu che il tasto diventa.
 *
 * [buttonFace] sta nel tasto (con il nome per TalkBack, se ce l'ha); [menuFace] e' la faccia che
 * l'host porta nel viaggio del menu, e non porta nomi: il tasto non c'e' piu', e due nodi con lo stesso
 * nome sono rumore.
 */
@Composable
private fun FluidBarActionImpl(
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  actions: (() -> List<FluidContextAction>)?,
  opensOn: FluidMenuOpensOn,
  buttonFace: @Composable () -> Unit,
  menuFace: @Composable () -> Unit,
) {
  if (actions == null) {
    FluidGlassIconButton(onClick = onClick, modifier = modifier, enabled = enabled) { buttonFace() }
    return
  }

  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val haptics = LocalFluidHaptics.current
  val anchor = remember { FluidMenuAnchor() }
  val press = rememberFluidPressState(FluidPressFeedback.ExpandRound)
  val density = LocalDensity.current.density
  val tint = GlassDefaults.controlTint()

  SideEffect {
    entry.presentation = FluidGlassModalPresentation.Expand
    entry.paneTitle = contentDescription
    entry.onDismissRequest = { entry.visible = false }
    entry.menuAnchor = anchor
    entry.menuHandOff = anchor.handOff
    anchor.face = menuFace
    anchor.tintFrom = tint
    anchor.opticsFrom = GlassDefaults.buttonOptics()
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      entry.visible = false
      anchor.hidden = false
      host?.unregister(entry)
    }
  }

  // L'apertura e' SINCRONA, nel gesto: azioni, ancora, sequenza e visibilita' nello stesso istante,
  // e il tasto si nasconde nello stesso fotogramma in cui la finestra compare sulla sua sagoma. Prima
  // passava da un LaunchedEffect (un fotogramma tardi) e il tasto sfumava con una molla sua: per un
  // attimo c'erano il tasto e la finestra, o nessuno dei due.
  val open: (Boolean) -> Boolean = open@{ withFinger ->
    if (host == null || entry.visible) return@open false
    val list = actions()
    if (list.isEmpty()) return@open false
    anchor.pressedBounds = if (withFinger) fluidPressedBounds(anchor.restBounds, press, density) else anchor.restBounds
    entry.actions = list
    entry.openedAt = host.nextSequence()
    entry.visible = true
    entry.lifted = true
    anchor.hidden = true
    entry.selection.cancel()
    if (withFinger) anchor.handOff.attach(entry.selection)
    true
  }

  val openTapped: () -> Unit = { open(false) }
  FluidGlassIconButtonCore(
    onClick = if (opensOn == FluidMenuOpensOn.Tap) openTapped else onClick,
    // Il relè in testa: vede il dito prima del tasto, e lo passa al menu quando il click lungo apre.
    modifier = modifier.fluidMenuPointerRelay(anchor.handOff),
    backdrop = currentGlassBackdrop(),
    enabled = enabled,
    selected = false,
    shape = FluidCapsuleShape,
    // Un Open solo, dal click lungo e solo se il menu si apre davvero: prima suonavano GestureStart
    // dal tasto, Open qui e Open dal layer dei modali.
    onLongClick = { if (open(true)) haptics.play(FluidHapticEvent.Open) },
    size = FluidGlassSize.Regular,
    haptic = if (opensOn == FluidMenuOpensOn.Tap) FluidHapticEvent.Open else FluidHapticEvent.Tap,
    longClickHaptic = null,
    press = press,
    // Il cancello sulla LENTE: si nasconde il vetro (con la sua icona), non il riquadro da 48 dp.
    lensModifier = Modifier.drawWithContent { if (!anchor.hidden) drawContent() },
    onLensPlaced = { coordinates ->
      val rect = coordinates.unclippedBoundsInRoot()
      anchor.restBounds = rect
      entry.previewSize = rect
      entry.origin = { rect }
    },
  ) { buttonFace() }
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
