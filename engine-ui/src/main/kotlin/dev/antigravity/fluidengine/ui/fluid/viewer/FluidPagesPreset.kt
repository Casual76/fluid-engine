package dev.antigravity.fluidengine.ui.fluid.viewer

import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Image
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onPlaced
import dev.antigravity.fluidengine.ui.page.LocalSharedPage
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Description
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
  val present: String = "Presenta",
  val endPresentation: String = "Chiudi presentazione",
  /** F6C.19 · Le azioni del tipo in «•••»: vai a una pagina o a una slide, le miniature, le note, lo zoom. */
  val more: String = "Altro",
  val goToPage: String = "Vai a pagina…",
  val goToSlide: String = "Vai alla slide…",
  val pageNumber: String = "Numero di pagina",
  val slideNumber: String = "Numero della slide",
  val range: (count: Int) -> String = { "Da 1 a $it" },
  val go: String = "Vai",
  val cancel: String = "Annulla",
  val pageThumbnails: String = "Miniature",
  val speakerNotes: String = "Note del relatore",
  val slideNotes: (current: Int, count: Int) -> String = { current, count -> "Slide $current di $count" },
  val noNotes: String = "Nessuna nota per questa slide",
  val fitWidth: String = "Adatta alla larghezza",
  /** F8.18 · Il nome per TalkBack dello scorrimento di tutto il documento. */
  val scrubber: String = "Scorri il documento",
  /** F8.18 · La capsula che riprende da una posizione salvata quando il file era diverso. */
  val resume: FluidViewerResumeStrings = FluidViewerResumeStrings(),
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
  val entrance = awaitViewerEntrance()
  val count = source.count.coerceAtLeast(0)
  val surface = MaterialTheme.colorScheme.surface
  if (count == 0) {
    val placed = if (entrance != null && source.countFinal) Modifier.onPlaced { entrance.placed() } else Modifier
    Box(modifier.fillMaxSize().then(placed).then(if (LocalViewerPage.current == null) Modifier.background(surface) else Modifier),
      contentAlignment = Alignment.Center) { Text(strings.empty) }
    return
  }
  key(source) {
    // F8.18: il punto dove l'utente era, per questa revisione del file. La lista parte già lì: nessun fotogramma sulla
    // prima pagina, e il viaggio d'oro arriva sul punto giusto.
    val resume = LocalViewerResumeSlot.current
    val resumeAt = remember(resume) { resume?.position?.takeIf { it.page in 0 until count } }
    val pages = rememberLazyListState(initialFirstVisibleItemIndex = resumeAt?.page ?: 0)
    val horizontal = rememberScrollState()
    val info = LocalViewerInfo.current
    val type = info?.file?.name?.substringAfterLast('.', "")?.lowercase()
    val slides = type in setOf("pptx", "odp")
    val word = type in setOf("docx", "odt", "rtf")
    val searchPages = info?.searchPages
    var finding by rememberSaveable { mutableStateOf(false) }
    var presenting by rememberSaveable { mutableStateOf(false) }
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
    if (finding && active) FluidSelectionBackEffect(enabled = true) { finding = false }
    var scale by rememberSaveable {
      mutableFloatStateOf(resumeAt?.zoom?.let { FluidZoom.clampScale(it, FluidZoom.DefaultMaxScale) } ?: FluidZoom.MinScale)
    }
    var thumbnails by rememberSaveable { mutableStateOf(false) }
    var selectedPage by remember { mutableStateOf<Int?>(null) }
    val cache = remember { ViewerPageRenderCache(source) }
    // F8.18 · Il bilancio: la risoluzione di lettura e delle anteprime segue il profilo di memoria del processo.
    val process = rememberFluidViewerResources()
    cache.budget = ViewerPagesBudget.of(process.viewerProfile)
    // F7.22: i due segnali del processo si raccolgono una volta per il documento, non in ogni pagina: a ogni salto della
    // maniglia entrano due o tre pagine nuove, e ognuna avviava due raccolte sue.
    val uiVisible = process.uiVisible.collectAsState()
    val prefetchAllowed = process.prefetchAllowed.collectAsState()
    val pageSignals = remember(uiVisible, prefetchAllowed) { ViewerPageSignals(uiVisible, prefetchAllowed) }
    // F7.22 · I salti della maniglia (e di TalkBack) passano da qui: mentre la lista salta, le pagine attraversate restano
    // fogli di carta e quella dove ci si ferma riceve prima l'anteprima, poi la pagina nitida a maniglia ferma.
    val jumps = remember(pages) { ViewerJumpTarget(pages) }
    LaunchedEffect(jumps) {
      snapshotFlow { jumps.count }.collectLatest {
        delay(ViewerJumpSettleMillis)
        jumps.jumping = false
      }
    }
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
    // F6C.19: le azioni del tipo in «•••» (Anteprima e Keynote di iOS 26): vai a una pagina o a una slide, le miniature,
    // le note del relatore se la sorgente le conosce (`FluidPageNotes`), e da ingranditi «Adatta alla larghezza».
    var goingTo by rememberSaveable { mutableStateOf(false) }
    var resumeUsed by rememberSaveable { mutableStateOf(false) }
    val stale = resume?.stale?.takeIf { !resumeUsed && it.page in 0 until count }
    var showingNotes by rememberSaveable { mutableStateOf(false) }
    val notes = source as? FluidPageNotes
    ViewerPresetToolbar {
      if (searchPages != null && finding) search(FluidSearchSpec.findInPage(query, { query = it },
        FluidSearchNavigation(cursor, hits.size,
          { if (hits.isNotEmpty()) cursor = (cursor + hits.size - 1) % hits.size },
          { if (hits.isNotEmpty()) cursor = (cursor + 1) % hits.size }), true, { finding = it }))
      if (slides) button("viewer.document.present", FluidIcons.PlayArrow, strings.present, { presenting = true },
        placement = FluidToolbarPlacement.BottomBar)
      else if (searchPages != null) button("viewer.document.find", FluidIcons.Search, strings.find, { finding = true },
        placement = FluidToolbarPlacement.BottomBar)
      // F8.18: il file è cambiato dall'ultima lettura; la posizione di prima si offre, non si applica da sola.
      if (stale != null) text("viewer.document.resume", strings.resume.page(stale.page + 1), {
        pages.requestScrollToItem(stale.page)
        selectedPage = stale.page
        resumeUsed = true
        resume?.dismissStale()
      }, placement = FluidToolbarPlacement.BottomBar)
      viewerTypeActions(strings.more) {
        buildList {
          add(FluidContextAction(if (slides) strings.goToSlide else strings.goToPage) { goingTo = true })
          add(FluidContextAction(strings.pageThumbnails, icon = FluidIcons.GridView) { thumbnails = true })
          if (slides && notes?.hasNotes == true) add(FluidContextAction(strings.speakerNotes, icon = FluidIcons.Description) {
            showingNotes = true
          })
          if (FluidZoom.isZoomed(scale)) add(FluidContextAction(strings.fitWidth, startsSection = true) {
            zoomTo(FluidZoom.MinScale)
          })
        }
      }
    }
    val transform = rememberTransformableState { zoom, _, _ -> currentZoomTo(scale * zoom) }
    val preview by remember { derivedStateOf { scrolling || transform.isTransformInProgress || jumps.jumping } }
    val current by remember(pages, count) {
      derivedStateOf {
        selectedPage?.takeIf { it < count && pages.layoutInfo.visibleItemsInfo.any { item -> item.index == it } }
          ?: visibleViewerPage(pages.layoutInfo, pages.canScrollBackward, pages.canScrollForward, count)
      }
    }
    if (resume != null) {
      // F8.18: la posizione si salva a riposo (fine dello scorrimento, cambio di zoom, pagina lasciata), mai a ogni
      // fotogramma: il negozio la tiene in memoria e la scrive più tardi.
      val currentScale by rememberUpdatedState(scale)
      val position = remember(pages) {
        {
          val first = pages.firstVisibleItemIndex
          val item = pages.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }
          val offset = item?.let { (pages.firstVisibleItemScrollOffset.toFloat() / it.size.coerceAtLeast(1)).coerceIn(0f, 1f) } ?: 0f
          FluidViewerPosition(page = first, offset = offset, zoom = currentScale)
        }
      }
      LaunchedEffect(pages, resume, resumeAt) {
        val at = resumeAt
        if (at != null && at.offset > 0f && pages.firstVisibleItemIndex == at.page && pages.firstVisibleItemScrollOffset == 0) {
          val item = snapshotFlow { pages.layoutInfo.visibleItemsInfo.firstOrNull { it.index == at.page } }.filterNotNull().first()
          pages.requestScrollToItem(at.page, (item.size * at.offset).roundToInt())
        }
        // A riposo: la pagina, lo zoom o lo scorrimento che cambiano, e poi niente per un momento (anche un salto senza
        // scorrimento: «Vai a pagina…», le miniature, la maniglia).
        snapshotFlow { Triple(pages.firstVisibleItemIndex, pages.isScrollInProgress, scale) }.drop(1).collectLatest { (_, moving, _) ->
          if (!moving) {
            delay(ViewerResumeSettleMillis)
            resume.save(position())
          }
        }
      }
      LaunchedEffect(active) { if (!active) resume.save(position()) }
      DisposableEffect(pages, resume) { onDispose { resume.save(position()) } }
    }
    val gap = when {
      slides -> 11.dp
      word -> 15.dp
      info != null -> 17.dp
      else -> FluidScreenDefaults.HorizontalPadding
    }
    val margin = if (slides) 20.dp else if (word) 25.dp else if (info == null) gap else 30.dp
    CompositionLocalProvider(LocalFluidGlassModalHostState provides modal) {
      BoxWithConstraints(modifier.fillMaxSize().then(if (LocalViewerPage.current == null) Modifier.background(surface) else Modifier).clipToBounds()) {
        val column = if (info == null) maxWidth else minOf(maxWidth, ViewerMixProfile.ColumnWidth)
        val pageWidth = (column - margin * 2).coerceAtLeast(1.dp) * scale
        val edge = maxOf(margin, (maxWidth - column) / 2 + margin)
        val pixelWidth = with(LocalDensity.current) { pageWidth.roundToPx() }
        LaunchedEffect(match, pixelWidth) {
          if (match != null) {
            val bounds = match.bounds.firstOrNull()
            val size = cache.measure(match.page)
            val offset = if (bounds == null) 0 else (bounds.top * pixelWidth * size.height / size.width).roundToInt()
            pages.scrollToItem(match.page, (offset - pages.layoutInfo.viewportSize.height / 4).coerceAtLeast(0))
          }
        }
        val top = viewerBoardTop(100.dp)
        // F6C.15b: sopra la capsula in basso anche quando l'app ha una tab bar (la capsula sale con il suo inset).
        val bottom = viewerBottomCapsuleTop() + gap
        PageCounterLayout(top, 16.dp, (maxWidth - column) / 2 + 16.dp, counter = {
          if (chromeVisible) {
            // F7.22: la pagina per TalkBack si legge qui, nella composizione. Letta dentro il blocco `semantics` (che gira
            // più tardi e non osserva niente) la lambda restava la stessa a ogni pagina e la descrizione si fermava al
            // primo salto: la lista arrivava a pagina 1000, il contatore diceva ancora «Pagina 3999».
            val pageState = strings.page(current + 1, count)
            Row(Modifier.viewerGlass(backdrop).fluidPressable(onClick = { thumbnails = true }, role = Role.Button)
              .semantics(mergeDescendants = true) {
                contentDescription = strings.thumbnails
                stateDescription = pageState
              }.heightIn(min = if (info == null) FluidGlassSize.Regular.touchTarget else 34.dp)
              .padding(horizontal = 12.dp, vertical = if (info == null) 4.dp else 0.dp),
              verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
              FluidIcon(FluidIcons.GridView, null, modifier = Modifier.size(17.dp))
              Text(strings.counter(current + 1, count), Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.labelLarge.viewerSize(13.5f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))
            }
          }
        }) { firstPageTop ->
          LazyColumn(
            modifier = Modifier.fillMaxSize().then(if (finding) Modifier.imePadding() else Modifier).glassBackdropSource(backdrop)
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
              .wrapContentWidth(Alignment.Start, unbounded = true).width(pageWidth + edge * 2),
            state = pages,
            contentPadding = remember(edge, bottom, firstPageTop) { ViewerColumnPadding(edge, firstPageTop, bottom) },
            verticalArrangement = Arrangement.spacedBy(gap),
          ) {
            items(count) { index ->
              val onScreen by remember(pages, index) { derivedStateOf { viewerPageOnScreen(pages, index) } }
              ViewerPage(cache, index, pixelWidth, preview, false, strings, strings.page(index + 1, count),
                match?.takeIf { finding && it.page == index }, entrance = entrance.takeIf { index == pages.firstVisibleItemIndex },
                onScreen = onScreen, signals = pageSignals)
            }
          }
        }
        // F8.18 · La barra di scorrimento di tutto il documento (la nota di Alessio sul bilancio): la maniglia porta a
        // qualunque pagina, anche alla millesima, e si disegnano solo le pagine che si vedono.
        if (count >= ViewerPagesBudget.ScrubberMinPages) FluidFastScroller(jumps, label = { strings.page(it + 1, count) },
          modifier = Modifier.fillMaxSize().padding(top = top, bottom = bottom), backdrop = backdrop,
          minItems = ViewerPagesBudget.ScrubberMinPages, contentDescription = strings.scrubber)
        if (presenting) ViewerSlideShow(cache, count, current, strings, pageSignals) { presenting = false }
        ViewerGoToAlert(goingTo, if (slides) strings.goToSlide else strings.goToPage,
          if (slides) strings.slideNumber else strings.pageNumber, strings.range(count), numeric = true,
          parse = { text -> text.trim().toIntOrNull()?.takeIf { it in 1..count } },
          onGo = { number ->
            pages.requestScrollToItem(number - 1)
            selectedPage = number - 1
          }, onDismiss = { goingTo = false }, go = strings.go, cancel = strings.cancel)
        if (notes != null) FluidGlassSheet(visible = showingNotes, onDismissRequest = { showingNotes = false },
          paneTitle = strings.speakerNotes, detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large)) {
          Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(FluidScreenDefaults.HorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(strings.speakerNotes, Modifier.semantics { heading() },
              style = MaterialTheme.typography.titleLarge.viewerSize(18f, FontWeight.Bold))
            Text(strings.slideNotes(current + 1, count), color = MaterialTheme.colorScheme.onSurfaceVariant,
              style = MaterialTheme.typography.bodySmall.viewerSize(13.5f))
            Text(notes.notes(current)?.takeIf(String::isNotBlank) ?: strings.noNotes,
              style = MaterialTheme.typography.bodyLarge.viewerSize(16f))
          }
        }
        if (searchFailed) Text(strings.searchError, Modifier.align(Alignment.Center).semantics { liveRegion = LiveRegionMode.Polite })
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
                val onScreen by remember(grid, index) { derivedStateOf {
                  grid.layoutInfo.visibleItemsInfo.any { it.index == index } || index == grid.firstVisibleItemIndex
                } }
                ViewerPage(cache, index, ThumbnailEdge, preview = true, thumbnail = true, strings = strings, description = null,
                  onScreen = onScreen, signals = pageSignals)
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
private fun ViewerSlideShow(cache: ViewerPageRenderCache, count: Int, initial: Int,
  strings: FluidPagesPresetStrings, signals: ViewerPageSignals, close: () -> Unit) {
  val state = rememberPagerState(initial.coerceIn(0, count - 1)) { count }
  Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
      val density = LocalDensity.current
      HorizontalPager(state, Modifier.fillMaxSize()) { page ->
        val natural by produceState<IntSize?>(null, page) { value = cache.measure(page) }
        natural?.let { size ->
          val width = minOf(maxWidth, maxHeight * (size.width.toFloat() / size.height))
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(Modifier.width(width)) {
              val onScreen by remember(state, page) { derivedStateOf {
                state.currentPage == page || state.layoutInfo.visiblePagesInfo.any { it.index == page }
              } }
              ViewerPage(cache, page, with(density) { width.roundToPx() }, false, false, strings, strings.page(page + 1, count),
                onScreen = onScreen, signals = signals)
            }
          }
        }
      }
      FluidGlassIconButton(close, Modifier.align(Alignment.TopEnd).padding(16.dp)) {
        FluidIcon(FluidIcons.Close, strings.endPresentation)
      }
    }
  }
}

/**
 * Measure the pinned counter first, including wrapped labels and the current font scale.
 * Its clearance belongs to the start of the scrollable list; only the counter stays pinned.
 */
@Composable
private fun PageCounterLayout(top: Dp, gap: Dp, margin: Dp, counter: @Composable () -> Unit, pages: @Composable (firstPageTop: () -> Dp) -> Unit) {
  // F7.22: la colonna si compone con un contenuto che non cambia a ogni misura. Prima ogni misura passava a
  // `subcompose("pages")` una lambda nuova (con dentro la distanza della prima pagina), e la colonna intera, con le pagine in
  // vista, si ricomponeva ogni volta che la capsula cambiava larghezza: a ogni salto della maniglia e a ogni pagina nuova
  // di un lancio («37 di 3000», «2742 di 3000»). Ora la distanza è uno stato che la colonna legge nella sua misura (il
  // padding di `ViewerColumnPadding`): una distanza nuova vale già in questa misura, e la composizione non si rifà.
  val firstPageTopState = remember { mutableStateOf(0.dp) }
  val firstPageTop = remember { { firstPageTopState.value } }
  val currentPages by rememberUpdatedState(pages)
  val pagesSlot: @Composable () -> Unit = remember { { currentPages(firstPageTop) } }
  SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
    val edge = margin.roundToPx()
    val inset = top.roundToPx()
    val chip = subcompose("counter", counter).singleOrNull()?.measure(constraints.copy(
      minWidth = 0, maxWidth = (constraints.maxWidth - 2 * edge).coerceAtLeast(0),
      minHeight = 0, maxHeight = (constraints.maxHeight - inset).coerceAtLeast(0),
    ))
    val distance = (inset + (chip?.let { it.height + gap.roundToPx() } ?: 0)).toDp()
    if (firstPageTopState.value != distance) firstPageTopState.value = distance
    val list = subcompose("pages", pagesSlot).single().measure(constraints)
    layout(list.width, list.height) {
      list.placeRelative(0, 0)
      chip?.placeRelative(edge, inset, zIndex = 1f)
    }
  }
}

/**
 * F7.22 · Il padding della colonna delle pagine con la distanza della prima pagina letta nella misura della lista (non
 * nella composizione): vedi [PageCounterLayout].
 */
@Stable
private class ViewerColumnPadding(private val edge: Dp, private val top: () -> Dp, private val bottom: Dp) : PaddingValues {
  override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = edge
  override fun calculateTopPadding(): Dp = top()
  override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = edge
  override fun calculateBottomPadding(): Dp = bottom
}

private class ViewerPageReaders { val reads = mutableSetOf<FluidPageRead>() }

@Composable
private fun ViewerPage(
  cache: ViewerPageRenderCache, index: Int, width: Int, preview: Boolean, thumbnail: Boolean,
  strings: FluidPagesPresetStrings, description: String?, match: FluidViewerPageMatch? = null,
  entrance: ViewerEntranceClaim? = null, onScreen: Boolean = true, signals: ViewerPageSignals,
) {
  val visible = signals.uiVisible.value && (onScreen || signals.prefetchAllowed.value)
  val owners = remember(cache, index, visible, onScreen) { ViewerPageReaders() }
  DisposableEffect(owners) { onDispose { owners.reads.forEach { it.close() }; owners.reads.clear() } }
  var currentRead by remember(owners) { mutableStateOf<FluidPageRead?>(null) }
  DisposableEffect(currentRead) {
    val used = currentRead
    onDispose { used?.let { owners.reads.remove(it); it.close() } }
  }
  var size by remember(cache, index) { mutableStateOf<IntSize?>(null) }
  var bitmap by remember(owners) { mutableStateOf<ImageBitmap?>(null) }
  var failed by remember(cache, index) { mutableStateOf(false) }
  var attempt by remember(cache, index) { mutableIntStateOf(0) }
  LaunchedEffect(owners, width, preview, thumbnail, attempt, onScreen) {
    if (!visible) return@LaunchedEffect
    failed = false
    try {
      // F8.18: mentre la lista corre, una pagina che resta in vista per un fotogramma solo non chiede niente al
      // documento; il suo posto resta il foglio di carta, e il thread del PDF resta libero per le pagine su cui ci si ferma.
      if (preview && bitmap == null) repeat(ViewerPagesBudget.SettleFrames) { withFrameNanos { } }
      val natural = size ?: cache.measure(index).also { size = it }
      val target = viewerPageRenderSize(natural, width, preview, thumbnail, cache.budget)
      val previous = bitmap
      if (previous == null || previous.width < target.width || previous.height < target.height) {
        val reader = FluidPageRead(optional = !onScreen)
        try {
          val next = withContext(reader + FluidPageDisplay) { cache.render(index, target, thumbnail) }
          if (onScreen) {
            owners.reads.add(reader)
            currentRead = reader
            bitmap = next
          }
        } finally {
          if (currentRead !== reader) reader.close()
        }
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      failed = true
    }
  }
  // F8.18: una pagina non ancora misurata prende la forma della prima misurata (quasi tutti i PDF hanno pagine uguali):
  // quando arriva la sua misura vera la colonna non salta.
  val ratio = size?.let { (it.width.toFloat() / it.height).coerceIn(1f / MaxPageAspect, MaxPageAspect) }
    ?: cache.typicalAspect.floatValue.takeIf { it > 0f }?.coerceIn(1f / MaxPageAspect, MaxPageAspect) ?: DefaultPageAspect
  val paper = MaterialTheme.colorScheme.surfaceContainerHigh
  val shown = bitmap
  val claim = LocalViewerEntrance.current
  val traveling = LocalSharedPage.current?.host?.isMoving == true
  val waiting = claim != null && (!claim.revealed || traveling) && !thumbnail && shown == null && !failed
  val placed = if (entrance != null && (shown != null || failed)) Modifier.onPlaced { entrance.placed() } else Modifier
  val backing = if (waiting) Modifier else Modifier.shadow(if (thumbnail) 0.dp else 2.dp).background(paper)
  Box(Modifier.fillMaxWidth().aspectRatio(ratio).then(placed).then(backing), contentAlignment = Alignment.Center) {
    if (shown != null) {
      Image(shown, description, Modifier.fillMaxSize().drawWithContent {
        drawContent()
        match?.bounds?.forEach { rect ->
          drawRect(FluidFindDefaults.CurrentHighlight.copy(alpha = .48f),
            Offset(rect.left * this.size.width, rect.top * this.size.height), Size(rect.width * this.size.width, rect.height * this.size.height))
        }
      }, contentScale = ContentScale.Fit)
    } else if (!thumbnail && !waiting && (!preview || failed)) {
      // F7.22: mentre la lista corre o salta la pagina che aspetta è solo carta, come in Anteprima: niente scritta che
      // entra e esce a ogni pagina (e niente testo da comporre e misurare per ognuna). A lista ferma torna «Caricamento».
      Column(Modifier.padding(FluidScreenDefaults.HorizontalPadding), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (failed) strings.error else strings.loading, style = MaterialTheme.typography.bodyMedium)
        if (failed) Text(strings.retry, Modifier.heightIn(min = FluidGlassSize.Regular.touchTarget)
          .fluidPressable(onClick = { attempt++ }, role = Role.Button).wrapContentHeight(),
          color = MaterialTheme.colorScheme.primary)
      }
    }
  }
}

@Composable
private fun rememberFluidViewerResources(): FluidGlassResources {
  val context = LocalContext.current
  return remember(context) { FluidGlassResources.get(context) }
}

/** F7.22 · I due segnali del processo per le pagine, raccolti una volta dal preset ([FluidGlassResources]). */
@Stable
internal class ViewerPageSignals(val uiVisible: State<Boolean>, val prefetchAllowed: State<Boolean>)

/**
 * F7.22 · Se la pagina [index] è in vista. Durante un salto la pagina nuova entra in composizione nella misura della
 * lista, quando `layoutInfo` dice ancora le pagine di prima: con il solo `visibleItemsInfo` nasceva «fuori vista» e si
 * ricomponeva subito dopo la misura (nuovi lettori, effetti rifatti), due composizioni per pagina a ogni salto. Le
 * pagine da `firstVisibleItemIndex` (già quella nuova) per quante se ne vedevano prima sono in vista anche prima della
 * misura. Una pagina preparata in anticipo dalla lista (oltre l'ultima in vista) resta fuori, come prima.
 */
internal fun viewerPageOnScreen(pages: LazyListState, index: Int): Boolean {
  val first = pages.firstVisibleItemIndex
  val visible = pages.layoutInfo.visibleItemsInfo
  return index >= first && index < first + maxOf(visible.size, 1) || visible.any { it.index == index }
}

/**
 * F7.22 · Lo scorrimento rapido del documento, che sa quando salta: lo stesso di `rememberFluidFastScrollTarget` su una
 * lista, più [jumping] (vero dal primo salto fino a [ViewerJumpSettleMillis] dopo l'ultimo) e [count]. Il preset lo
 * legge come uno scorrimento: le pagine attraversate restano carta, e quella dove la maniglia si ferma arriva prima in
 * anteprima e poi nitida, senza che niente si veda o si regoli.
 */
@Stable
internal class ViewerJumpTarget(private val state: LazyListState) : FluidFastScrollTarget {
  var jumping: Boolean by mutableStateOf(false)
  var count: Int by mutableIntStateOf(0)
    private set
  override val totalItems: Int get() = state.layoutInfo.totalItemsCount
  override val visibleItems: Int get() = state.layoutInfo.visibleItemsInfo.size
  override val firstIndex: Int get() = state.firstVisibleItemIndex
  override val fractionalFirstIndex: Float
    get() = FluidFastScroll.fractionalFirstIndex(
      firstIndex = state.firstVisibleItemIndex,
      firstOffsetPx = state.firstVisibleItemScrollOffset,
      firstSizePx = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0,
    )
  override val isScrollInProgress: Boolean get() = state.isScrollInProgress
  override suspend fun scrollToIndex(index: Int) {
    if (index != state.firstVisibleItemIndex) {
      jumping = true
      count++
    }
    state.scrollToItem(index)
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
/**
 * F7.22: quanto dopo l'ultimo salto della maniglia la pagina passa dall'anteprima a quella nitida. 150 ms, a occhio
 * (25 novembre 2026): più corto di un battito di ciglia, più lungo dei 16 ms fra due eventi del dito che trascina piano.
 */
internal const val ViewerJumpSettleMillis = 150L
/** F8.18: quanto la pagina resta ferma prima di salvare la posizione. 400 ms, a occhio (25 novembre 2026). */
internal const val ViewerResumeSettleMillis = 400L
private const val MaxPageAspect = 20f
private val ThumbnailColumnWidth = 112.dp
private const val ThumbnailEdge = 192
private const val PreviewEdge = 384
private const val ReadingEdge = 2048

internal fun viewerPageRenderSize(
  natural: IntSize, displayWidth: Int, preview: Boolean, thumbnail: Boolean,
  budget: ViewerPagesBudget = ViewerPagesBudget.Full,
): IntSize {
  require(natural.width > 0 && natural.height > 0)
  val edge = when {
    thumbnail -> budget.thumbnailEdge
    preview -> budget.previewEdge
    else -> budget.readingEdge
  }
  val scale = minOf(displayWidth.coerceAtLeast(1).toDouble() / natural.width,
    edge.toDouble() / maxOf(natural.width, natural.height))
  return IntSize((natural.width * scale).roundToInt().coerceAtLeast(1), (natural.height * scale).roundToInt().coerceAtLeast(1))
}

/**
 * F8.18 · Il bilancio dei documenti a pagine, **automatico e invisibile** (nota di Alessio): la barra di scorrimento è
 * per tutto il documento, ma si disegnano solo le pagine in vista (la lista pigra), si chiedono al documento solo
 * quelle su cui la lista si ferma almeno [SettleFrames] fotogrammi, e la risoluzione segue il profilo di memoria del
 * processo (F7.10: Pieno, Ridotto, Protetto). Niente da regolare: un telefono debole riceve pagine un po' meno fitte,
 * mai una domanda. I numeri sono a occhio (25 novembre 2026); il tetto dei byte resta quello di F7.13.
 */
internal class ViewerPagesBudget private constructor(val readingEdge: Int, val previewEdge: Int, val thumbnailEdge: Int) {
  companion object {
    val Full = ViewerPagesBudget(ReadingEdge, PreviewEdge, ThumbnailEdge)
    /** Ridotto: una pagina A4 del tablet a 1600 px di lato lungo; sul telefono a tutta larghezza non cambia niente. */
    val Reduced = ViewerPagesBudget(1600, 320, 160)
    /** Protetto: 1200 px, anteprime a 256 e miniature a 128. */
    val Protected = ViewerPagesBudget(1200, 256, 128)

    fun of(profile: Int): ViewerPagesBudget = when (profile) {
      0 -> Full
      1 -> Reduced
      else -> Protected
    }

    /** Da quante pagine compare la maniglia di tutto il documento. A occhio, 25 novembre 2026. */
    const val ScrubberMinPages: Int = 12
    /** I fotogrammi che una pagina deve restare in vista, mentre la lista corre, prima di chiedere la resa. */
    const val SettleFrames: Int = 2
  }
}

/** Serializes source calls, while keeping at most six rendered pages and 24 MiB of bitmap data per reader. */
internal class ViewerPageRenderCache(private val source: FluidPageSource) {
  /** F8.18: la forma della prima pagina misurata, per le pagine che non lo sono ancora. Zero finché non c'è. */
  val typicalAspect = mutableFloatStateOf(0f)
  /** F8.18: il bilancio di risoluzione scelto dal preset. */
  var budget: ViewerPagesBudget = ViewerPagesBudget.Full
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
    if (typicalAspect.floatValue == 0f) typicalAspect.floatValue = size.width.toFloat() / size.height
    if (dimensions.size > MaxDimensions) dimensions.remove(dimensions.keys.first())
    size
  }

  suspend fun render(index: Int, target: IntSize, thumbnail: Boolean): ImageBitmap = lock.withLock {
    val key = Key(index, thumbnail)
    val old = images[key]
    if (old != null && old.target.width >= target.width && old.target.height >= target.height) return@withLock old.bitmap
    val bitmap = source.render(index, target)
    coroutineContext.ensureActive()
    if (coroutineContext[FluidPageRead]?.managed == true) return@withLock bitmap
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
    /** F8.18: le misure tenute, 256 (una lista di migliaia di pagine torna indietro senza rimisurare). */
    const val MaxDimensions = 256
  }
}
