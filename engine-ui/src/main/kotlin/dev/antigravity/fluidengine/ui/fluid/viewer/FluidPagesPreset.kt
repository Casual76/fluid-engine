package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidSelectionBackEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Labels for a document's pages and the available reading actions. Numbers passed to labels are one based. */
data class FluidPagesPresetStrings(
  val counter: (current: Int, count: Int) -> String = { current, count -> "$current di $count" },
  val page: (current: Int, count: Int) -> String = { current, count -> "Pagina $current di $count" },
  val thumbnails: String = "Miniature delle pagine",
  val empty: String = "Nessuna pagina",
  val loading: String = "Caricamento pagina",
  val error: String = "Impossibile mostrare la pagina",
  val retry: String = "Riprova",
  val zoomIn: String = "Ingrandisci",
  val zoomOut: String = "Riduci",
  val zoom: (percent: Int) -> String = { "Zoom $it%" },
  val find: String = "Trova",
  val searchError: String = "Impossibile cercare nel documento",
)

/**
 * A continuous vertical reader for the [FluidFileViewer] renderer slot. A pinch changes the width of the
 * whole column, preserving its place in the vertical list. Open the thumbnail sheet to jump directly to a page.
 * The source is borrowed for as long as this preset is composed. Use a new source when the document changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FluidPagesPreset(
  content: FluidViewerContent.Pages,
  chromeVisible: Boolean,
  modifier: Modifier = Modifier,
  strings: FluidPagesPresetStrings = FluidPagesPresetStrings(),
) {
  val source = content.source
  val count = source.count.coerceAtLeast(0)
  val surface = MaterialTheme.colorScheme.surface
  if (count == 0) {
    Box(modifier.fillMaxSize().then(if (LocalViewerPage.current == null) Modifier.background(surface) else Modifier), contentAlignment = Alignment.Center) { Text(strings.empty) }
    return
  }
  key(source) {
    val pages = rememberLazyListState()
    val horizontal = rememberScrollState()
    val searchPages = LocalViewerInfo.current?.searchPages
    var finding by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<FluidViewerPageMatch>()) }
    var cursor by rememberSaveable { mutableIntStateOf(0) }
    var searchFailed by remember { mutableStateOf(false) }
    val active = currentFluidViewerPageActive()
    LaunchedEffect(active) { if (!active) finding = false }
    LaunchedEffect(query, finding, searchPages) {
      hits = emptyList()
      cursor = 0
      searchFailed = false
      if (finding && searchPages != null && query.isNotBlank()) {
        try { hits = searchPages(query).filter { it.page in 0 until count } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { searchFailed = true }
      }
    }
    val match = hits.getOrNull(cursor)
    if (searchPages != null) ViewerPresetToolbar {
      if (finding) search(FluidSearchSpec.findInPage(query, { query = it }, FluidSearchNavigation(cursor, hits.size,
        { if (hits.isNotEmpty()) cursor = (cursor + hits.size - 1) % hits.size },
        { if (hits.isNotEmpty()) cursor = (cursor + 1) % hits.size }), true, { finding = it }))
      button("viewer.document.find", FluidIcons.Search, strings.find, { finding = true }, placement = FluidToolbarPlacement.BottomBar)
    }
    if (finding && active) FluidSelectionBackEffect(enabled = true) { finding = false }
    var scale by rememberSaveable { mutableFloatStateOf(FluidZoom.MinScale) }
    var thumbnails by rememberSaveable { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<Int?>(null) }
    val cache = remember { ViewerPageRenderCache(source) }
    val backdrop = rememberGlassBackdrop()
    val outerModal = LocalFluidGlassModalHostState.current
    val modal = outerModal ?: rememberFluidGlassModalHostState()
    val scrolling by remember { derivedStateOf { pages.isScrollInProgress || horizontal.isScrollInProgress } }
    LaunchedEffect(scrolling) { if (scrolling) selectedPage = null }
    val zoomTo: (Float) -> Unit = { requested ->
      val next = FluidZoom.clampScale(requested, FluidZoom.DefaultMaxScale)
      if (next != scale) {
        val offset = (pages.firstVisibleItemScrollOffset * (next / scale)).roundToInt()
        pages.requestScrollToItem(pages.firstVisibleItemIndex.coerceIn(0, count - 1), offset)
        scale = next
      }
    }
    val currentZoomTo by rememberUpdatedState(zoomTo)
    val transform = rememberTransformableState { zoom, _, _ -> currentZoomTo(scale * zoom) }
    val preview by remember { derivedStateOf { scrolling || transform.isTransformInProgress } }
    val current by remember(pages, count) {
      derivedStateOf {
        selectedPage?.takeIf { it < count && pages.layoutInfo.visibleItemsInfo.any { item -> item.index == it } }
          ?: visibleViewerPage(pages.layoutInfo, pages.canScrollBackward, pages.canScrollForward, count)
      }
    }
    val gap = FluidScreenDefaults.HorizontalPadding
    CompositionLocalProvider(LocalFluidGlassModalHostState provides modal) {
      BoxWithConstraints(modifier.fillMaxSize().then(if (LocalViewerPage.current == null) Modifier.background(surface) else Modifier).clipToBounds()) {
        val pageWidth = (maxWidth - gap * 2).coerceAtLeast(1.dp) * scale
        val pixelWidth = with(LocalDensity.current) { pageWidth.roundToPx() }
        LaunchedEffect(match, pixelWidth) {
          if (match != null) {
            val bounds = match.bounds.firstOrNull()
            val size = cache.measure(match.page)
            val offset = if (bounds == null) 0 else (bounds.top * pixelWidth * size.height / size.width).roundToInt()
            pages.scrollToItem(match.page, (offset - pages.layoutInfo.viewportSize.height / 4).coerceAtLeast(0))
          }
        }
        val top = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding() +
          FluidGlassSize.Regular.touchTarget + gap
        val bottom = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() +
          FluidFloatingCapsuleDefaults.ActionSize + gap
        LazyColumn(
          modifier = Modifier.matchParentSize().then(if (finding) Modifier.imePadding() else Modifier).glassBackdropSource(backdrop)
            .semantics {
              stateDescription = strings.zoom((scale * 100f).roundToInt())
              customActions = buildList {
                if (FluidZoom.isZoomed(scale)) add(CustomAccessibilityAction(strings.zoomOut) {
                  zoomTo(FluidZoom.step(scale, zoomIn = false)); true
                })
                if (scale < FluidZoom.DefaultMaxScale) add(CustomAccessibilityAction(strings.zoomIn) {
                  zoomTo(FluidZoom.step(scale, zoomIn = true)); true
                })
              }
            }
            .transformable(transform, canPan = { false })
            .horizontalScroll(horizontal, enabled = FluidZoom.isZoomed(scale))
            .wrapContentWidth(Alignment.Start, unbounded = true).width(pageWidth + gap * 2),
          state = pages,
          contentPadding = PaddingValues(start = gap, top = top, end = gap, bottom = bottom),
          verticalArrangement = Arrangement.spacedBy(gap),
        ) {
          items(count) { index ->
            ViewerPage(cache, index, pixelWidth, preview, false, strings, strings.page(index + 1, count),
              match?.takeIf { finding && it.page == index })
          }
        }
        if (searchFailed) Text(strings.searchError, Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite })
        if (chromeVisible) {
          Row(Modifier.align(Alignment.TopStart).padding(start = gap, top = top)
            .viewerGlass(backdrop).fluidPressable(onClick = { thumbnails = true }, role = Role.Button)
            .semantics(mergeDescendants = true) {
              contentDescription = strings.thumbnails
              stateDescription = strings.page(current + 1, count)
            }.heightIn(min = FluidGlassSize.Regular.touchTarget).padding(horizontal = gap),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FluidIcon(FluidIcons.GridView, null, modifier = Modifier.size(18.dp))
            Text(strings.counter(current + 1, count), Modifier.clearAndSetSemantics {},
              style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
          }
        }
        FluidGlassSheet(visible = thumbnails, onDismissRequest = { thumbnails = false }, paneTitle = strings.thumbnails,
          detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large)) {
          val grid = rememberLazyGridState(initialFirstVisibleItemIndex = current)
          LazyVerticalGrid(
            columns = GridCells.Adaptive(ThumbnailColumnWidth), state = grid, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(gap), horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
          ) {
            items(count) { index ->
              Column(
                Modifier.selectable(selected = index == current, role = Role.Button, onClick = {
                  pages.requestScrollToItem(index)
                  selectedPage = index
                  thumbnails = false
                }).semantics { contentDescription = strings.page(index + 1, count) },
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                ViewerPage(cache, index, ThumbnailEdge, preview = true, thumbnail = true, strings = strings, description = null)
                Text("${index + 1}", Modifier.clearAndSetSemantics { hideFromAccessibility() },
                  style = MaterialTheme.typography.labelMedium)
              }
            }
          }
        }
        if (outerModal == null) FluidGlassModalHost(modal, backdrop)
      }
    }
  }
}

@Composable
private fun ViewerPage(
  cache: ViewerPageRenderCache, index: Int, width: Int, preview: Boolean, thumbnail: Boolean,
  strings: FluidPagesPresetStrings, description: String?, match: FluidViewerPageMatch? = null,
) {
  var size by remember(cache, index) { mutableStateOf<IntSize?>(null) }
  var bitmap by remember(cache, index) { mutableStateOf<ImageBitmap?>(null) }
  var failed by remember(cache, index) { mutableStateOf(false) }
  var attempt by remember(cache, index) { mutableIntStateOf(0) }
  LaunchedEffect(cache, index, width, preview, thumbnail, attempt) {
    failed = false
    try {
      val natural = size ?: cache.measure(index).also { size = it }
      val target = viewerPageRenderSize(natural, width, preview, thumbnail)
      val previous = bitmap
      if (previous == null || previous.width < target.width || previous.height < target.height) {
        bitmap = cache.render(index, target, thumbnail)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    }
  }
  val ratio = size?.let { (it.width.toFloat() / it.height).coerceIn(1f / MaxPageAspect, MaxPageAspect) }
    ?: DefaultPageAspect
  val paper = MaterialTheme.colorScheme.surfaceContainerHigh
  Box(Modifier.fillMaxWidth().aspectRatio(ratio).shadow(if (thumbnail) 0.dp else 2.dp).background(paper), contentAlignment = Alignment.Center) {
    val shown = bitmap
    if (shown != null) {
      Image(shown, description, Modifier.fillMaxSize().drawWithContent {
        drawContent()
        match?.bounds?.forEach { rect ->
          drawRect(FluidFindDefaults.CurrentHighlight.copy(alpha = .48f),
            Offset(rect.left * this.size.width, rect.top * this.size.height), Size(rect.width * this.size.width, rect.height * this.size.height))
        }
      }, contentScale = ContentScale.Fit)
    } else if (!thumbnail) {
      Column(Modifier.padding(FluidScreenDefaults.HorizontalPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (failed) strings.error else strings.loading, style = MaterialTheme.typography.bodyMedium)
        if (failed) Text(strings.retry, Modifier.heightIn(min = FluidGlassSize.Regular.touchTarget)
          .fluidPressable(onClick = { attempt++ }, role = Role.Button).wrapContentHeight(),
          color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

internal fun visibleViewerPage(
  layout: LazyListLayoutInfo, canScrollBackward: Boolean, canScrollForward: Boolean, count: Int,
): Int {
  if (!canScrollBackward || count <= 1) return 0
  if (!canScrollForward) return count - 1
  // A page under the middle of the viewport is current. The closest edge wins in the gaps.
  val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
  return layout.visibleItemsInfo.minByOrNull { item ->
    maxOf(item.offset - middle, middle - (item.offset + item.size), 0)
  }?.index?.coerceIn(0, count - 1) ?: 0
}

private const val DefaultPageAspect = 0.7071f
private const val MaxPageAspect = 20f
private val ThumbnailColumnWidth = 112.dp
private const val ThumbnailEdge = 192
private const val PreviewEdge = 384
private const val ReadingEdge = 2048

internal fun viewerPageRenderSize(natural: IntSize, displayWidth: Int, preview: Boolean, thumbnail: Boolean): IntSize {
  require(natural.width > 0 && natural.height > 0)
  val edge = when {
    thumbnail -> ThumbnailEdge
    preview -> PreviewEdge
    else -> ReadingEdge
  }
  val scale = minOf(displayWidth.coerceAtLeast(1).toDouble() / natural.width,
    edge.toDouble() / maxOf(natural.width, natural.height))
  return IntSize((natural.width * scale).roundToInt().coerceAtLeast(1), (natural.height * scale).roundToInt().coerceAtLeast(1))
}

/** Serializes source calls, while keeping at most six rendered pages and 24 MiB of bitmap data per reader. */
internal class ViewerPageRenderCache(private val source: FluidPageSource) {
  private data class Key(val index: Int, val thumbnail: Boolean)
  private data class Entry(val target: IntSize, val bitmap: ImageBitmap) {
    val bytes: Long get() = bitmap.width.toLong() * bitmap.height * 4L
  }

  private val lock = Mutex()
  private val dimensions = LinkedHashMap<Int, IntSize>(16, 0.75f, true)
  private val images = LinkedHashMap<Key, Entry>(8, 0.75f, true)
  private var bytes = 0L

  suspend fun measure(index: Int): IntSize = lock.withLock {
    dimensions[index]?.let { return@withLock it }
    val size = source.size(index)
    coroutineContext.ensureActive()
    require(size.width > 0 && size.height > 0)
    dimensions[index] = size
    if (dimensions.size > 64) dimensions.remove(dimensions.keys.first())
    size
  }

  suspend fun render(index: Int, target: IntSize, thumbnail: Boolean): ImageBitmap = lock.withLock {
    val key = Key(index, thumbnail)
    val old = images[key]
    if (old != null && old.target.width >= target.width && old.target.height >= target.height) return@withLock old.bitmap
    val bitmap = source.render(index, target)
    coroutineContext.ensureActive()
    val entry = Entry(target, bitmap)
    if (entry.bytes <= MaxBytes) {
      bytes += entry.bytes - (images.put(key, entry)?.bytes ?: 0)
      while (images.size > 6 || bytes > MaxBytes) {
        bytes -= checkNotNull(images.remove(images.keys.first())).bytes
      }
    }
    bitmap
  }

  private companion object {
    const val MaxBytes = 24L * 1024 * 1024
  }
}
