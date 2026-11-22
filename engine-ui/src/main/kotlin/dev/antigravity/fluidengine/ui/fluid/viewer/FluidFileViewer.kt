package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavGraphBuilder
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBackEffect
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.page.*
import dev.antigravity.fluidengine.ui.theme.BlackMediaAppearance
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import kotlin.math.abs

/** Put [fluidViewerThumbnail] before the thumbnail's drawing and clipping modifiers. */
data class FluidViewerThumbnail(val id: Any, val namespace: FluidGlassNamespace)

fun Modifier.fluidViewerThumbnail(thumbnail: FluidViewerThumbnail, shape: Shape = RectangleShape): Modifier =
  fluidTransitionSource(thumbnail.id, thumbnail.namespace, shape).fluidSharedElement(thumbnail.id, thumbnail.namespace)

private const val PageKey = "fluid.viewer.currentPage"

private class ViewerRoute(
  val entry: NavBackStackEntry,
  val initial: Int,
  val thumbnail: (Int) -> FluidViewerThumbnail?,
)

private val LocalViewerRoute = staticCompositionLocalOf<ViewerRoute?> { null }

/**
 * Register the destination on a FluidNavHost. [initial] resolves the file requested by a deep link.
 * Once a page settles, the entry remembers that page for both recreation and the return thumbnail.
 * The source may disappear (for instance a filtered grid). Expand then uses its normal source-free exit.
 */
fun NavGraphBuilder.fluidFileViewerRoute(
  route: String,
  arguments: List<NamedNavArgument> = emptyList(),
  deepLinks: List<NavDeepLink> = emptyList(),
  initial: (NavBackStackEntry) -> Int = { 0 },
  thumbnail: (NavBackStackEntry, Int) -> FluidViewerThumbnail? = { _, _ -> null },
  content: @Composable (NavBackStackEntry) -> Unit,
) {
  composable(route, arguments, deepLinks, transition = FluidPageTransition.Expand,
    dismiss = FluidPageDismiss.Down, zoomSource = { entry ->
      val index = entry.savedStateHandle.get<Int>(PageKey) ?: initial(entry)
      thumbnail(entry, index)?.let { FluidTransitionSource(it.id, it.namespace) }
    }) { entry ->
    val routeState = remember(entry) {
      ViewerRoute(entry, entry.savedStateHandle.get<Int>(PageKey) ?: initial(entry)) { thumbnail(entry, it) }
    }
    CompositionLocalProvider(LocalViewerRoute provides routeState) { content(entry) }
  }
}

/**
 * A full-screen file on a real route. Give the graph [fluidFileViewerRoute] for a zoom from the grid,
 * downward dismissal and a restorable deep link. [onBack] defaults to the host's back dispatcher.
 * [toolbar] uses the engine toolbar DSL. A tool owns the body and its own bottom actions until Done or Back.
 * [renderContent] is the integration point for a preset or an app renderer (tiles, PDF, player, code).
 * A renderer's controls consume their own gestures before the frame. [content] is never called with an invalid index.
 */
@Composable
fun FluidFileViewer(
  count: Int,
  initial: Int = LocalViewerRoute.current?.initial ?: 0,
  content: (index: Int) -> FluidViewerContent,
  toolbar: FluidToolbarScope.(index: Int) -> Unit = {},
  tools: FluidViewerToolsScope.() -> Unit = {},
  info: (@Composable (index: Int) -> Unit)? = null,
  onBack: () -> Unit = viewerBackAction(),
  modifier: Modifier = Modifier,
  onPageChange: (Int) -> Unit = {},
  strings: FluidViewerStrings = FluidViewerStrings(),
  renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = { file, visible -> ViewerBody(file, visible) },
) {
  require(count >= 0)
  val route = LocalViewerRoute.current
  // A deep link can arrive before the app has loaded the file list.
  var lastPage by rememberSaveable { mutableIntStateOf(initial.coerceAtLeast(0)) }
  val pager = if (count > 0) rememberPagerState(initialPage = lastPage.coerceIn(0, count - 1)) { count } else null
  val index = pager?.currentPage?.coerceIn(0, count - 1) ?: 0
  val onPage by rememberUpdatedState(onPageChange)
  LaunchedEffect(pager, count, route) {
    if (pager != null) snapshotFlow { pager.settledPage }.collect { page ->
      if (page in 0 until count) {
        lastPage = page
        route?.entry?.savedStateHandle?.set(PageKey, page)
        onPage(page)
      }
    }
  }
  var chromeVisible by rememberSaveable { mutableStateOf(true) }
  var infoOpen by rememberSaveable { mutableStateOf(false) }
  var selected by rememberSaveable { mutableStateOf<String?>(null) }
  val available = FluidViewerToolsScope().apply(tools).items
  val tool = available.firstOrNull { it.id == selected }?.takeIf { count > 0 }
  val hasInfo = info != null && count > 0
  LaunchedEffect(hasInfo, tool?.id) {
    if (!hasInfo) infoOpen = false
    if (count > 0 && tool == null) selected = null
  }
  val openInfo = { if (hasInfo) infoOpen = true }
  val toggle = { chromeVisible = !chromeVisible }
  val goBack = {
    when {
      infoOpen -> infoOpen = false
      tool != null -> selected = null
      else -> onBack()
    }
  }
  val front by LocalFluidRouteFront.current
  val pageSpace = LocalSharedPage.current
  val density = LocalDensity.current.density
  DisposableEffect(pageSpace, density) {
    pageSpace?.host?.verticalDismissRelease?.set(pageSpace.page) { travel, speed ->
      viewerDismissRelease(travel, speed, density)
    }
    onDispose {
      if (pageSpace != null) {
        pageSpace.host.verticalDismissRelease.remove(pageSpace.page)
        pageSpace.host.ownPageBackground.remove(pageSpace.page)
      }
    }
  }
  if (front && !LocalFluidPagePreparing.current && (infoOpen || tool != null)) {
    FluidSelectionBackEffect(enabled = true, onExit = goBack)
  }

  val backdrop = rememberGlassBackdrop()
  val presetBar = remember { ViewerPresetBar() }
  val outerModal = LocalFluidGlassModalHostState.current
  val modal = outerModal ?: rememberFluidGlassModalHostState()
  val file = if (count > 0) content(index) else null
  val photo = file?.blackBackground == true
  val pageColor = if (photo) Color.Black else MaterialTheme.colorScheme.surface
  SideEffect { if (pageSpace != null) pageSpace.host.ownPageBackground[pageSpace.page] = pageColor }
  CompositionLocalProvider(LocalFluidGlassModalHostState provides modal) {
    BoxWithConstraints(modifier.fillMaxSize()) {
      val theme = MaterialTheme.colorScheme
      val ink = if (photo) Color.White else theme.onSurface
      val body = Modifier.fillMaxSize().glassBackdropSource(backdrop)
        .then(if (pager == null || tool != null) Modifier.viewerBackdrop(if (photo) Color.Black else theme.surface) else Modifier)
        .semantics {
          customActions = buildList {
            add(CustomAccessibilityAction(if (chromeVisible) strings.hideControls else strings.showControls) { toggle(); true })
            if (hasInfo) add(CustomAccessibilityAction(strings.info) { openInfo(); true })
          }
        }
      CompositionLocalProvider(LocalContentColor provides ink) {
        Box(body, contentAlignment = Alignment.Center) {
          when {
            tool != null -> Box(Modifier.fillMaxSize().viewerVerticalInput(
              infoEnabled = false, holdDown = true, zoomed = { false }, onInfo = openInfo,
            ).viewerTap(toggle)) { tool.body(index) }
            pager == null -> Box(Modifier.fillMaxSize().viewerTap(toggle), contentAlignment = Alignment.Center) {
              Text(strings.empty)
            }
            else -> HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
              val item = if (page == index) requireNotNull(file) else content(page)
              // F6C.2: la pagina del preset immagine, condivisa con FluidImageViewer, e il canale dei preset.
              val video = (item as? FluidViewerContent.Media)?.takeIf { it.videoSurface != null }
              val ratio = (item as? FluidViewerContent.Image)?.aspectRatio
                ?: (video?.session as? FluidVideoGeometry)?.videoAspectRatio
              val pageScope = rememberViewerPage(pager, page, item.frameZoomed, presetBar)
              val zoom = pageScope.zoom
              val gestures = Modifier.fillMaxSize().viewerVerticalInput(hasInfo, infoOpen, { zoom.isZoomed }, openInfo)
              val input = if (item.frameZoomed) {
                gestures.viewerPinchDismiss(pageSpace, zoom, enabled = !infoOpen)
                  .fluidZoomInput(zoom, ratio, onTap = toggle, immediateTap = video != null)
              } else {
                gestures.viewerTap(toggle)
              }
              val source = route?.thumbnail?.invoke(page)
              val shared = if (source != null) Modifier.fluidSharedElement(source.id, source.namespace) else Modifier
              Box(input.viewerBackdrop(if (item.blackBackground) Color.Black else theme.surface), contentAlignment = Alignment.Center) {
                val fitted = if (item is FluidViewerContent.Image) Modifier.viewerFit(ratio).then(shared).fluidZoomLayer(zoom)
                  else if (video != null) Modifier.fillMaxSize() else Modifier.fillMaxSize().then(shared)
                CompositionLocalProvider(LocalContentColor provides if (item.blackBackground) Color.White else theme.onSurface,
                  LocalViewerPage provides pageScope, LocalVideoShared provides shared.takeIf { video != null }) {
                  Box(fitted, contentAlignment = Alignment.Center) { renderContent(item, chromeVisible) }
                }
              }
            }
          }
        }
      }
      BlackMediaAppearance(photo) {
        ViewerChrome(index, count, ink, chromeVisible, backdrop, toolbar, available, tool,
        select = { selected = it }, onBack = goBack, onInfo = openInfo.takeIf { hasInfo }, strings = strings,
          preset = presetBar.of(index), width = maxWidth)
      }
      if (hasInfo) ViewerInfo(infoOpen, maxWidth >= ViewerInfoBreakpoint, backdrop, strings, { infoOpen = false }) {
        info?.invoke(index)
      }
      if (outerModal == null) FluidGlassModalHost(modal, backdrop)
    }
  }
  if (front && !LocalFluidPagePreparing.current) ViewerSystemBars(chromeVisible, photo)
}

internal val LocalVideoShared = staticCompositionLocalOf<Modifier?> { null }

internal fun Modifier.viewerFit(ratio: Float?): Modifier =
  if (ratio != null && ratio.isFinite() && ratio > 0f) aspectRatio(ratio) else fillMaxSize()

@Composable
internal fun Modifier.viewerBackdrop(color: Color, fullyArrivedOnly: Boolean = false): Modifier {
  val space = LocalSharedPage.current
  return drawBehind {
    if (space != null && space.host.hasClosingBackdrop(space.page)) return@drawBehind
    val alpha = if (space != null && space.page in space.host.ownPageBackground) space.host.backdropArrival(space.page) else 1f
    if (alpha > 0f && (!fullyArrivedOnly || alpha == 1f)) drawRect(color, alpha = alpha)
  }
}

/**
 * Standalone variant. With [onDismissRequest], a downward drag or an inward pinch at 1× shrinks the
 * media into the underlying app and calls the owner after the exit. Pass null, or use the overload
 * without the callback, to leave standalone dismissal off. Inside a page host its route owns dismissal.
 * [onBack] continues to handle the explicit back button. Video playback controls stay outside the zoom.
 */
@Composable
fun FluidFileViewer(
  count: Int,
  initial: Int = LocalViewerRoute.current?.initial ?: 0,
  content: (index: Int) -> FluidViewerContent,
  toolbar: FluidToolbarScope.(index: Int) -> Unit = {},
  tools: FluidViewerToolsScope.() -> Unit = {},
  info: (@Composable (index: Int) -> Unit)? = null,
  onBack: () -> Unit = viewerBackAction(),
  modifier: Modifier = Modifier,
  onPageChange: (Int) -> Unit = {},
  strings: FluidViewerStrings = FluidViewerStrings(),
  renderContent: @Composable (FluidViewerContent, chromeVisible: Boolean) -> Unit = { file, visible -> ViewerBody(file, visible) },
  onDismissRequest: (() -> Unit)?,
) {
  val body: @Composable (Modifier) -> Unit = { slot ->
    FluidFileViewer(count, initial, content, toolbar, tools, info, onBack, slot, onPageChange, strings, renderContent)
  }
  if (onDismissRequest == null || LocalSharedPage.current != null) {
    body(modifier)
    return
  }
  // Reuse the gesture, spring and alpha of a route without a thumbnail. The transparent lower layer
  // reveals the caller. The application keeps its Android back dispatcher, owners and inline toolbar.
  FluidPageHostImpl(stack = remember { listOf(false, true) }, onBack = onDismissRequest, modifier = modifier,
    transition = { FluidPageTransition.Expand }, backSwipe = { FluidBackSwipe.Off }, peer = null,
    onTransitionStart = {}, onSettled = {}, leaving = null, state = rememberFluidPageHostState(),
    pageColor = Color.Transparent, ownOwners = false, pageKey = { it }, dismiss = { FluidPageDismiss.Down },
    handleSystemBack = false) { file ->
    if (file) CompositionLocalProvider(LocalFluidPageBar provides null) { body(Modifier) }
  }
}

@Composable
private fun viewerBackAction(): () -> Unit {
  val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
  return remember(dispatcher) { { dispatcher?.onBackPressed() } }
}

@Composable
private fun Modifier.viewerTap(onTap: () -> Unit): Modifier {
  val current by rememberUpdatedState(onTap)
  return pointerInput(Unit) { detectTapGestures(onTap = { current() }) }
}

/** Gallery-2 opens its inspector from 840 dp of actual available width (F6C.0). */
private val ViewerInfoBreakpoint = 840.dp

/** Same slow threshold as FluidImageViewer. A deliberate upward fling cancels a long pull. */
internal fun viewerDismissRelease(travel: Float, speed: Float, density: Float): Boolean {
  val fling = FluidSheetDismissVelocity.value * density
  if (abs(speed) >= fling) return speed > 0f
  return travel > FluidSheetDismissTravel.value * density
}

/** At minimum zoom, lateral motion is the pager's and downward motion the page host's.
 * Observe after children. A scrubber, scrolling document or pinch keeps its whole contact. */
@Composable
private fun Modifier.viewerVerticalInput(
  infoEnabled: Boolean, holdDown: Boolean, zoomed: () -> Boolean, onInfo: () -> Unit,
): Modifier {
  val currentZoom by rememberUpdatedState(zoomed)
  val currentInfo by rememberUpdatedState(onInfo)
  return pointerInput(infoEnabled, holdDown) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      var pinched = false
      var pulling = false
      var childOwns = false
      do {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        pinched = pinched || event.changes.count { it.pressed } > 1
        if (pinched) {
          event.changes.forEach { if (it.positionChanged()) it.consume() }
        } else if (change != null) {
          val distance = change.position - down.position
          if (!pulling && change.isConsumed && change.pressed && change.position != change.previousPosition) childOwns = true
          val vertical = abs(distance.y) > maxOf(abs(distance.x), viewConfiguration.touchSlop)
          if (!childOwns && vertical && !currentZoom() && change.pressed &&
            (infoEnabled && distance.y < 0f || holdDown && distance.y > 0f)) pulling = true
          if (pulling) {
            change.consume()
            if (!change.pressed && infoEnabled && distance.y < -viewConfiguration.touchSlop) currentInfo()
          }
        }
      } while (event.changes.any { it.pressed })
    }
  }
}

/** F6C.2 and F6C.3 provide the type-specific controls and readers. */
@Composable
private fun ViewerBody(file: FluidViewerContent, chromeVisible: Boolean) {
  when (file) {
    is FluidViewerContent.Image -> FluidImagePreset(file)
    is FluidViewerContent.Media -> FluidMediaPreset(file, chromeVisible)
    is FluidViewerContent.Pages -> FluidPagesPreset(file, chromeVisible)
    is FluidViewerContent.Text -> FluidTextPreset(file)
    is FluidViewerContent.Unknown -> FluidUnknownPreset(file)
    // F6C.8: le due forme nuove, con il preset minimo (quelli veri: F6C.9 e le famiglie).
    is FluidViewerContent.Grid -> FluidGridPreset(file, chromeVisible)
    is FluidViewerContent.Custom -> FluidCustomPreset(file, chromeVisible)
  }
}
