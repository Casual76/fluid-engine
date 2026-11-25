package dev.antigravity.fluidengine.media

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidAlert
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidAsyncImage
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidImagePhase
import dev.antigravity.fluidengine.ui.fluid.FluidPermission
import dev.antigravity.fluidengine.ui.fluid.FluidPermissionState
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGrid
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridSection
import dev.antigravity.fluidengine.ui.fluid.FluidPreviewGridState
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluid.FluidSpinner
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidPermissionSource
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBack
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CheckCircle
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.Cloud
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.PhotoLibrary
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.icons.Warning
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPermission
import dev.antigravity.fluidengine.ui.fluid.rememberFluidPreviewGridState
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerThumbnail
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidViewerThumbnail
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidZoomStyle
import dev.antigravity.fluidengine.ui.page.fluidSharedElement
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.FormatStyle

/** I numeri del selettore (F8.14). */
object FluidMediaPickerDefaults {
  /**
   * Le colonne fra cui si pizzica: 3, 5 e 9, come Foto e la griglia di F8.4; il selettore parte da 3, come il selettore
   * di foto di iOS su iPhone in verticale: 3 colonne con passo di 131–132 pt su 390–393 pt e 2 pt fra le celle in iOS 15,
   * 16, 17 e 26 (fotogrammi delle sessioni WWDC21–WWDC25, misurati il 25 novembre 2026; fiducia alta). Il pizzico porta
   * comunque a 5 e a 9.
   */
  val Columns: List<Int> = listOf(3, 5, 9)

  /** La densita' di partenza, indice in [Columns]: 3 colonne. */
  const val InitialDensity: Int = 0

  /** Quanto manca alla fine della griglia quando si chiede la pagina dopo: 40 elementi. */
  const val PrefetchDistance: Int = 40

  /** L'attesa prima di cercare mentre si scrive: 300 ms, come la ricerca di Foto (a occhio, 25 novembre 2026). */
  const val SearchDebounceMillis: Long = 300L

  /** Lo spazio sotto la griglia per la lente della ricerca in basso: 96 dp. */
  val BottomBarRoom: Dp = 96.dp
}

/** I tag delle prove e delle riprese. */
object FluidMediaPickerTags {
  const val Grid: String = "fluid-media:griglia"
  const val Tabs: String = "fluid-media:schede"
  const val SearchButton: String = "fluid-media:cerca"
  const val SearchField: String = "fluid-media:campo"
  const val Confirm: String = "fluid-media:aggiungi"
  const val Cancel: String = "fluid-media:annulla"
  const val Albums: String = "fluid-media:album"
  const val Limited: String = "fluid-media:solo-alcune"
  const val Access: String = "fluid-media:accesso"
  const val Preview: String = "fluid-media:anteprima"
  const val Delivering: String = "fluid-media:consegna"

  fun cell(key: String): String = "fluid-media:cella:$key"
  fun album(source: String, id: String): String = "fluid-media:album:$source:$id"
}

private const val PageRoot = "root"
private const val PageSearch = "search"
private const val PagePreview = "preview"
private const val AlbumPrefix = "album\u0001"

private fun albumPage(album: FluidMediaAlbum): String = AlbumPrefix + listOf(album.source, album.id, album.title).joinToString("\u0001")

private class PickerTab(val id: String)

/**
 * Il selettore di foto e video di vetro (F8.14, `scelta-foto-e-video`), come il selettore di foto di iOS
 * (`PHPickerViewController`, iOS 17–26), con le sorgenti dell'engine ([FluidMediaSource]).
 *
 * - **La griglia** e' quella di F8.4 ([FluidPreviewGrid]): il pizzico fra 3, 5 e 9 colonne. Con
 *   [FluidMediaRequest.limit] > 1 la **scelta e' in ordine**: il primo tocco e' 1, il dito che scorre di lato sceglie in
 *   fila, a tetto pieno le altre foto si spengono; il tasto ✓ consegna. Con un tetto di 1 un tocco sceglie e torna, come
 *   in iOS.
 * - **La testata del selettore di iOS 26**: la «x» e il ✓ di vetro ai lati, il titolo con sotto la scelta, e nella prima
 *   pagina il segmentato «Foto | Album» ([PickerHeaderLook]); le foto passano sotto. La **ricerca** e' il tasto rotondo in
 *   basso: per significato dove la sorgente la sa fare (MyCloud Gallery), per nome altrove, con le parole che la sorgente
 *   ha capito sotto il campo.
 * - **L'anteprima a tutto schermo**: la pressione lunga su una foto la apre dal suo riquadro (il viaggio d'oro: la foto
 *   cresce dalla miniatura e ci torna, anche trascinando in giu'), si sfoglia, e da li' si sceglie o si toglie.
 * - **L'accesso**: «solo alcune foto» si dice e si gestisce, un permesso negato si chiede dal tasto (F8.11) o si sceglie
 *   col Photo Picker di Android, una sorgente chiusa si sblocca, una che non risponde lo dice.
 * - **La consegna** chiede a ogni sorgente i suoi elementi nell'ordine della scelta; se una rifiuta (il backup non
 *   risponde) non esce niente e l'avviso dice perche'.
 *
 * Dove sta: e' una pagina intera (una rotta dell'app, o un livello sopra). Il back torna indietro di una pagina, e dalla
 * prima annulla ([onCancel]).
 *
 * @param state le sorgenti, la richiesta e la scelta ([rememberFluidMediaPickerState]).
 * @param onResult gli elementi scelti, nell'ordine della scelta, pronti da leggere.
 * @param offerSystemPicker offre «Scegli con Android» quando il telefono non da' accesso alle foto (di serie vero).
 */
@Composable
fun FluidMediaPicker(
  state: FluidMediaPickerState,
  onResult: (List<FluidPickedMedia>) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  labels: FluidMediaLabels = LocalFluidMediaLabels.current,
  offerSystemPicker: Boolean = true,
) {
  val ns = rememberFluidGlassNamespace()
  val result by rememberUpdatedState(onResult)
  val scope = rememberCoroutineScope()
  var stack by rememberSaveable(saver = listSaver(save = { it.value }, restore = { mutableStateOf(it.toList()) })) {
    mutableStateOf(listOf(PageRoot))
  }
  var tab by rememberSaveable { mutableStateOf(TabPhotos) }
  val preview = remember { PreviewContext() }
  // Dopo la morte del processo l'anteprima non ha piu' la sua lista: si torna alla pagina sotto.
  if (stack.lastOrNull() == PagePreview && preview.feed == null) stack = stack.dropLast(1)
  val back: () -> Unit = { if (stack.size > 1) stack = stack.dropLast(1) else onCancel() }
  BackHandler(enabled = stack.size <= 1) { onCancel() }

  val permission = rememberFluidPermission(
    permission = if (FluidMediaKind.Video in state.request.kinds) FluidPermission.PhotosAndVideos else FluidPermission.Photos,
    reason = labels.permissionReason,
    onResult = { state.refreshAccess() },
  )
  // Al ritorno dalle impostazioni o dal dialogo lo stato del permesso cambia: l'accesso si rilegge da solo.
  LaunchedEffect(permission.status) { state.refreshAccess() }
  val system = if (offerSystemPicker) rememberFluidSystemPhotoPicker(state.request) { result(it) } else null
  val unlocker = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
    state.refreshAccess()
  }
  val unlock: (FluidMediaAccess.Locked) -> Unit = { locked ->
    val intent = locked.unlock
    if (intent != null) runCatching { unlocker.launch(IntentSenderRequest.Builder(intent.intentSender).build()) }
    else state.refreshAccess()
  }
  val pending = state.unlockNeeded
  if (pending != null) {
    LaunchedEffect(pending) {
      state.unlockNeeded = null
      unlock(pending)
    }
  }
  val confirm: () -> Unit = {
    scope.launch { state.deliver()?.let { result(it) } }
  }
  val pickOne: (FluidMediaItem) -> Unit = { item ->
    scope.launch { state.deliver(listOf(item))?.let { result(it) } }
  }
  val env = PickerEnv(state, labels, ns, permission, system, unlock, confirm, pickOne,
    openPreview = { feed, item ->
      preview.feed = feed
      preview.key = item.key
      stack = stack + PagePreview
    },
    openAlbum = { album -> stack = stack + albumPage(album) },
    openSearch = { stack = stack + PageSearch },
    back = back,
  )

  Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
    FluidPageHost(
      stack = stack,
      onBack = back,
      zoom = { key -> if (key == PagePreview) FluidZoomStyle.Zoom else null },
      modifier = Modifier.fillMaxSize(),
      zoomSource = { key -> if (key == PagePreview) preview.key?.let { FluidTransitionSource(it, ns) } else null },
    ) { key ->
      when {
        key == PageRoot -> PickerRoot(env, tab, onTab = { tab = it })
        key == PageSearch -> PickerSearch(env)
        key == PagePreview -> preview.feed?.let { feed -> PickerPreview(env, feed, preview) }
        key.startsWith(AlbumPrefix) -> {
          val parts = key.removePrefix(AlbumPrefix).split("\u0001")
          val source = state.source(parts.getOrNull(0).orEmpty())
          if (source != null && parts.size >= 3) {
            PickerGridPage(env, source, state.request.query(album = parts[1]), title = parts[2], root = false)
          }
        }
      }
    }
    if (state.delivering) PickerDelivering(state, labels, Modifier.align(Alignment.Center))
    PickerNotice(state, labels)
  }
}

private const val TabPhotos = "photos"
private const val TabAlbums = "albums"

/** Cio' che le pagine del selettore si passano. */
@Stable
private class PickerEnv(
  val state: FluidMediaPickerState,
  val labels: FluidMediaLabels,
  val ns: FluidGlassNamespace,
  val permission: FluidPermissionState,
  val system: FluidSystemPhotoPickerLauncher?,
  val unlock: (FluidMediaAccess.Locked) -> Unit,
  val confirm: () -> Unit,
  val pickOne: (FluidMediaItem) -> Unit,
  val openPreview: (FluidMediaFeed, FluidMediaItem) -> Unit,
  val openAlbum: (FluidMediaAlbum) -> Unit,
  val openSearch: () -> Unit,
  val back: () -> Unit,
) {
  val multiple: Boolean get() = state.request.limit > 1
}

/** Quale lista e quale elemento mostra l'anteprima (la chiave serve anche al ritorno nella miniatura). */
@Stable
private class PreviewContext {
  var feed: FluidMediaFeed? by mutableStateOf(null)
  var key: String? by mutableStateOf(null)
}

// --- La prima pagina: Foto e Album --------------------------------------------------------------------------------------

@Composable
private fun PickerRoot(env: PickerEnv, tab: String, onTab: (String) -> Unit) {
  val tabs: @Composable () -> Unit = { PickerTabs(env, tab, onTab) }
  Box(Modifier.fillMaxSize()) {
    if (tab == TabAlbums) {
      PickerAlbums(env, tabs)
    } else {
      PickerGridPage(env, env.state.primary, env.state.request.query(), title = env.labels.photosTab, root = true, tabs = tabs)
    }
    PickerSearchButton(env, Modifier.align(Alignment.BottomEnd))
  }
}

/** «Foto | Album», sotto i tasti della testata (il segmentato «Photos | Collections» del selettore di iOS 26). */
@Composable
private fun PickerTabs(env: PickerEnv, tab: String, onTab: (String) -> Unit) {
  FluidSegmentedControl(
    options = listOf(TabPhotos, TabAlbums),
    selected = tab,
    onSelect = onTab,
    modifier = Modifier.fillMaxWidth().testTag(FluidMediaPickerTags.Tabs),
    label = { if (it == TabPhotos) env.labels.photosTab else env.labels.albumsTab },
  )
}

/**
 * La lente in basso a destra, come il selettore di iOS 26 (la ricerca e' un tasto, non un campo): un cerchio di vetro da
 * 44 dp, il bordo basso a 28 dp dal fondo dello schermo o 4 dp sopra la barra di sistema, come la barra in basso delle
 * pagine dell'engine.
 */
@Composable
private fun PickerSearchButton(env: PickerEnv, modifier: Modifier) {
  if (env.state.sources.none { it.capabilities.search }) return
  val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  FluidGlassIconButton(
    onClick = env.openSearch,
    modifier = modifier.padding(end = 28.dp, bottom = maxOf(28.dp, nav + 4.dp)).testTag(FluidMediaPickerTags.SearchButton),
  ) {
    FluidIcon(FluidIcons.Search, env.labels.search)
  }
}

/** I numeri della testata del selettore, dal selettore di foto di iOS 26 (WWDC25, sessione 246, a 1,099 px per punto). */
internal object PickerHeaderLook {
  /** I tasti «x» e ✓ a 10 pt dai bordi dello schermo. */
  val SideMargin: Dp = 10.dp

  /** Il segmentato 11 pt sotto i tasti, coi margini di 17–18 pt dai bordi (10 + 7). */
  val TabsGap: Dp = 11.dp
  val TabsInset: Dp = 7.dp

  /** La griglia comincia 16 pt sotto il segmentato. */
  val GridGap: Dp = 16.dp

  /** Il velo della testata sopra le foto che passano sotto: il colore della pagina al 94% e all'82%, poi niente. */
  const val VeilTop: Float = 0.94f
  const val VeilMiddle: Float = 0.82f
}

/**
 * La testata del selettore, come quella del selettore di iOS 26: la «x» di vetro a sinistra, il titolo al centro con la
 * riga sotto (la scelta, o la sorgente), il ✓ in evidenza a destra, e sotto, nella prima pagina, «Foto | Album». Sta sopra
 * le foto, che le passano sotto con un velo del colore della pagina. [onHeight] riceve l'altezza, per il margine della
 * griglia.
 */
@Composable
private fun PickerHeader(
  env: PickerEnv,
  title: String,
  subtitle: String?,
  root: Boolean,
  tabs: (@Composable () -> Unit)?,
  backdrop: GlassBackdropState?,
  onHeight: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val page = MaterialTheme.colorScheme.background
  CompositionLocalProvider(LocalGlassBackdrop provides (backdrop ?: LocalGlassBackdrop.current)) {
    Box(
      modifier
        .fillMaxWidth()
        .onSizeChanged { onHeight(it.height) }
        .background(
          Brush.verticalGradient(
            0f to page.copy(alpha = PickerHeaderLook.VeilTop),
            0.8f to page.copy(alpha = PickerHeaderLook.VeilMiddle),
            1f to page.copy(alpha = 0f),
          ),
        ),
    ) {
      Column(
        Modifier.statusBarsPadding().padding(horizontal = PickerHeaderLook.SideMargin).padding(top = 6.dp, bottom = PickerHeaderLook.GridGap),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { PickerNavigation(env, root) }
          Column(Modifier.weight(1f).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              title,
              style = MaterialTheme.typography.titleMedium,
              textAlign = TextAlign.Center,
              maxLines = 1,
              modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
              Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
              )
            }
          }
          Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { PickerConfirm(env) }
        }
        if (tabs != null) {
          Spacer(Modifier.height(PickerHeaderLook.TabsGap))
          Box(Modifier.padding(horizontal = PickerHeaderLook.TabsInset)) { tabs() }
        }
      }
    }
  }
}

/** La riga sotto il titolo: la scelta («3 foto selezionate»), o di dove sono le foto. */
@Composable
private fun pickerSubtitle(env: PickerEnv, fallback: String?): String? {
  val count = env.state.selection.countIn(0)
  if (count == 0) return fallback
  val videos = env.state.selectedItems.count { it.kind == FluidMediaKind.Video }
  return env.labels.selected(count - videos, videos)
}

// --- Una griglia: le foto, un album, i risultati di una ricerca ------------------------------------------------------------

@Composable
private fun PickerGridPage(
  env: PickerEnv,
  source: FluidMediaSource,
  query: FluidMediaQuery,
  title: String,
  root: Boolean,
  tabs: (@Composable () -> Unit)? = null,
) {
  val feed = env.state.feed(source, query)
  val grid = rememberFluidPreviewGridState(initialDensity = FluidMediaPickerDefaults.InitialDensity)
  val backdrop = rememberGlassBackdrop()
  var headerPx by remember { mutableIntStateOf(0) }
  val headerDp = with(LocalDensity.current) { headerPx.toDp() }
  val many = env.state.sources.size > 1
  Box(Modifier.fillMaxSize()) {
    if (source.access.allowsListing()) {
      PickerGrid(env, feed, grid, backdrop, top = headerDp, bottomRoom = if (root) FluidMediaPickerDefaults.BottomBarRoom else 24.dp)
      if (source.access == FluidMediaAccess.Limited && source.id == FluidDeviceMediaSource.SourceId) {
        PickerLimitedBanner(env, Modifier.align(Alignment.BottomCenter).padding(bottom = if (root) 96.dp else 24.dp))
      }
    } else {
      PickerAccessMessage(env, source, Modifier.align(Alignment.Center).padding(top = headerDp))
    }
    PickerHeader(
      env = env,
      title = title,
      subtitle = pickerSubtitle(env, if (many) source.label else null),
      root = root,
      tabs = tabs,
      backdrop = backdrop.takeIf { source.access.allowsListing() },
      onHeight = { headerPx = it },
      modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

@Composable
private fun PickerNavigation(env: PickerEnv, root: Boolean) {
  if (root) {
    FluidGlassIconButton(onClick = env.back, modifier = Modifier.testTag(FluidMediaPickerTags.Cancel)) {
      FluidIcon(FluidIcons.Close, env.labels.cancel)
    }
  } else {
    FluidGlassIconButton(onClick = env.back) { FluidIcon(FluidIcons.ArrowBack, env.labels.back) }
  }
}

@Composable
private fun PickerConfirm(env: PickerEnv) {
  if (!env.multiple) return
  val count = env.state.selection.countIn(0)
  FluidGlassIconButton(
    onClick = env.confirm,
    enabled = count > 0 && !env.state.delivering,
    style = FluidGlassButtonStyle.Prominent,
    modifier = Modifier.testTag(FluidMediaPickerTags.Confirm),
  ) {
    FluidIcon(FluidIcons.Check, if (count > 0) env.labels.addCount(count) else env.labels.add)
  }
}

@Composable
private fun PickerGrid(
  env: PickerEnv,
  feed: FluidMediaFeed,
  grid: FluidPreviewGridState,
  backdrop: GlassBackdropState,
  top: Dp,
  bottomRoom: Dp,
) {
  val state = env.state
  LaunchedEffect(feed) { if (feed.items.isEmpty() && !feed.finished) feed.loadMore() }
  // La pagina dopo, quando la griglia arriva vicino alla fine. Si legge lo scorrimento, non si ricompone.
  LaunchedEffect(feed, grid) {
    snapshotFlow { grid.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
      .collect { last -> if (last >= feed.items.size - FluidMediaPickerDefaults.PrefetchDistance) feed.loadMore() }
  }
  val version = feed.version
  val sections = remember(feed, version) { listOf(FluidPreviewGridSection("tutto", null, feed.items.toList())) }
  val locale = LocalConfiguration.current.locales[0]
  val cellPx = with(LocalDensity.current) { 140.dp.roundToPx() }
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + bottomRoom
  Box(Modifier.fillMaxSize()) {
    FluidPreviewGrid(
      sections = sections,
      itemKey = { it.key },
      state = grid,
      columns = FluidMediaPickerDefaults.Columns,
      contentPadding = PaddingValues(top = top, bottom = bottom),
      selection = state.selection,
      inSelectionMode = env.multiple,
      onItemClick = { item -> if (!env.multiple) env.pickOne(item) },
      onItemLongPress = { item -> env.openPreview(feed, item) },
      numbered = state.request.ordered && env.multiple,
      fastScrollLabel = { item -> item.takenAtMillis?.let { FluidFormat.date(Instant.ofEpochMilli(it), FormatStyle.MEDIUM, locale) } },
      modifier = Modifier.fillMaxSize().glassBackdropSource(backdrop).testTag(FluidMediaPickerTags.Grid),
    ) { item, _ ->
      PickerCell(env, feed, item, cellPx, locale)
    }
    if (feed.finished && feed.items.isEmpty()) {
      PickerMessage(
        icon = { FluidIcon(FluidIcons.PhotoLibrary, null, size = 44.dp) },
        title = if (feed.query.text != null) env.labels.noResults else env.labels.empty,
        detail = if (feed.query.text != null) env.labels.noResultsHint else env.labels.emptyHint,
        modifier = Modifier.align(Alignment.Center),
      )
    } else if (feed.loading && feed.items.isEmpty()) {
      FluidSpinner(Modifier.align(Alignment.Center), size = 24.dp)
    }
  }
}

/** Una cella: la miniatura che arriva (F8.1), la durata di un video, la nuvola di cio' che sta solo nel backup. */
@Composable
private fun PickerCell(env: PickerEnv, feed: FluidMediaFeed, item: FluidMediaItem, cellPx: Int, locale: java.util.Locale) {
  val labels = env.labels
  val source = feed.source
  val description = remember(item.key, locale) { pickerDescription(item, labels, locale) }
  val preview = rememberUpdatedState(env.openPreview)
  Box(
    Modifier
      .fillMaxSize()
      // La sorgente del viaggio d'oro e l'immagine condivisa: in testa, cosi' mentre l'anteprima e' in scena la cella
      // intera non si disegna e la foto ci rientra.
      .fluidViewerThumbnail(FluidViewerThumbnail(item.key, env.ns))
      .testTag(FluidMediaPickerTags.cell(item.key))
      .semantics {
        contentDescription = description
        customActions = listOf(CustomAccessibilityAction(labels.preview) { preview.value(feed, item); true })
      },
  ) {
    val phase = rememberFluidMediaThumbnail(source, item, cellPx)
    FluidAsyncImage(phase = phase, contentDescription = null, modifier = Modifier.fillMaxSize())
    if (item.kind == FluidMediaKind.Video || item.backupOnly) {
      Box(
        Modifier.fillMaxWidth().height(28.dp).align(Alignment.BottomStart)
          .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = CellScrimAlpha)))),
      )
    }
    if (item.kind == FluidMediaKind.Video) {
      // Come Foto di iOS 26: la durata in basso a destra (a 5–6 pt dal bordo). Dove c'e' il segno della scelta, che sta
      // in basso a destra come in iOS, la durata va in basso a sinistra.
      Row(
        Modifier.align(if (env.multiple) Alignment.BottomStart else Alignment.BottomEnd)
          .padding(start = 5.dp, end = 6.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        FluidIcon(FluidIcons.PlayArrow, null, tint = Color.White, size = 13.dp)
        Text(
          text = fluidMediaDurationText(item.durationMillis ?: 0L),
          color = Color.White,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
        )
      }
    }
    if (item.backupOnly) {
      FluidIcon(
        FluidIcons.Cloud, null, tint = Color.White, size = 14.dp,
        modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
      )
    }
  }
}

/** Il velo in fondo a una cella sotto la durata e la nuvola: nero al 45% (a occhio, sulle celle dei video di Foto). */
private const val CellScrimAlpha = 0.45f

/** La durata di un video come Foto: «0:42», «12:05», «1:02:33». */
fun fluidMediaDurationText(millis: Long): String {
  val total = (millis.coerceAtLeast(0L) + 500L) / 1000L
  val h = total / 3600
  val m = (total % 3600) / 60
  val s = total % 60
  return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Cosa dice TalkBack di una cella: «Video, 1 minuto e 5 secondi, 13 lug 2025, solo nel backup». */
internal fun pickerDescription(item: FluidMediaItem, labels: FluidMediaLabels, locale: java.util.Locale): String = buildList {
  add(item.description ?: if (item.kind == FluidMediaKind.Video) labels.video else labels.photo)
  if (item.description != null) add(if (item.kind == FluidMediaKind.Video) labels.video else labels.photo)
  if (item.kind == FluidMediaKind.Video) {
    val total = ((item.durationMillis ?: 0L) + 500L) / 1000L
    add(labels.duration(total / 60, total % 60))
  }
  item.takenAtMillis?.let { add(FluidFormat.date(Instant.ofEpochMilli(it), FormatStyle.MEDIUM, locale)) }
  if (item.backupOnly) add(labels.backupOnly.lowercase(locale))
}.joinToString(", ")

@Composable
private fun PickerLimitedBanner(env: PickerEnv, modifier: Modifier) {
  FluidFloatingCapsule(modifier = modifier.testTag(FluidMediaPickerTags.Limited)) {
    Text(
      env.labels.limitedTitle,
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(start = 6.dp),
    )
    FluidButton(
      text = env.labels.limitedAction,
      onClick = { env.permission.requestMore() },
      style = FluidButtonStyle.Plain,
    )
  }
}

// --- L'accesso: permesso, sblocco, sorgente che non c'e' ----------------------------------------------------------------

/** Al posto della griglia, quando la sorgente non elenca: il permesso, lo sblocco, la sorgente che non c'e'. */
@Composable
private fun PickerAccessMessage(env: PickerEnv, source: FluidMediaSource, modifier: Modifier) {
  val labels = env.labels
  val box = modifier.padding(horizontal = 32.dp).testTag(FluidMediaPickerTags.Access)
  when (val access = source.access) {
    is FluidMediaAccess.NotAllowed -> {
      val forever = access.forever || env.permission.status == FluidPermissionStatus.DeniedForever
      PickerMessage(
        icon = { FluidIcon(FluidIcons.PhotoLibrary, null, size = 44.dp) },
        title = labels.notAllowedTitle,
        detail = labels.notAllowedDetail,
        modifier = box,
      ) {
        // Il permesso e' quello delle foto del telefono: il tasto c'e' per la sorgente del telefono.
        if (source.id == FluidDeviceMediaSource.SourceId) {
          FluidButton(
            text = if (forever) labels.openSettings else labels.allowAccess,
            onClick = { if (forever) env.permission.openSettings() else env.permission.request { env.state.refreshAccess() } },
            modifier = Modifier.fluidPermissionSource(env.permission),
          )
        }
        val system = env.system
        if (system != null && source.id == FluidDeviceMediaSource.SourceId) {
          FluidButton(text = labels.systemPicker, onClick = { system.launch() }, style = FluidButtonStyle.Plain)
        }
      }
    }
    is FluidMediaAccess.Locked -> PickerMessage(
      icon = { FluidIcon(FluidIcons.Lock, null, size = 44.dp) },
      title = labels.lockedTitle(source.label),
      detail = labels.lockedDetail,
      modifier = box,
    ) {
      FluidButton(text = labels.unlock, onClick = { env.unlock(access) })
    }
    else -> PickerMessage(
      icon = { FluidIcon(FluidIcons.Warning, null, size = 44.dp) },
      title = labels.unavailableTitle(source.label),
      detail = labels.unavailableDetail,
      modifier = box,
    ) {
      FluidButton(text = labels.retry, onClick = { env.state.refreshAccess() }, style = FluidButtonStyle.Plain)
    }
  }
}

@Composable
private fun PickerMessage(
  icon: @Composable () -> Unit,
  title: String,
  detail: String,
  modifier: Modifier = Modifier,
  actions: @Composable () -> Unit = {},
) {
  Column(
    modifier.widthIn(max = 360.dp).padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { icon() }
    Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Text(
      detail,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    actions()
  }
}

// --- Gli album -----------------------------------------------------------------------------------------------------------

@Composable
private fun PickerAlbums(env: PickerEnv, tabs: @Composable () -> Unit) {
  val state = env.state
  val sources = state.sources
  LaunchedEffect(sources) { sources.forEach { state.loadAlbums(it) } }
  var headerPx by remember { mutableIntStateOf(0) }
  val top = with(LocalDensity.current) { headerPx.toDp() } + 8.dp
  val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + FluidMediaPickerDefaults.BottomBarRoom
  val thumbPx = with(LocalDensity.current) { 56.dp.roundToPx() }
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.fillMaxSize().testTag(FluidMediaPickerTags.Albums)) {
    LazyColumn(
      Modifier.fillMaxSize().glassBackdropSource(backdrop),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = bottom),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      for (source in sources) {
        item(key = "titolo:${source.id}") { FluidSectionHeader(title = source.label) }
        item(key = "gruppo:${source.id}") {
          val albums = state.albumsOf(source)
          when {
            !source.access.allowsListing() -> PickerSourceRow(env, source)
            albums == null -> Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { FluidSpinner(size = 20.dp) }
            else -> FluidListGroup {
              albums.forEachIndexed { index, album ->
                if (index > 0) FluidListDivider()
                FluidListRow(
                  title = album.title,
                  subtitle = album.count?.let { FluidFormat.number(it.toDouble()) } ?: "",
                  leadingTile = false,
                  leading = { PickerAlbumCover(source, album, thumbPx) },
                  onClick = { env.openAlbum(album) },
                  modifier = Modifier.testTag(FluidMediaPickerTags.album(source.id, album.id)),
                )
              }
            }
          }
        }
      }
    }
    PickerHeader(
      env = env,
      title = env.labels.albumsTab,
      subtitle = pickerSubtitle(env, null),
      root = true,
      tabs = tabs,
      backdrop = backdrop,
      onHeight = { headerPx = it },
      modifier = Modifier.align(Alignment.TopCenter),
    )
  }
}

/** La riga di una sorgente senza accesso: tocca per la sua prima pagina, dove si chiede l'accesso o lo sblocco. */
@Composable
private fun PickerSourceRow(env: PickerEnv, source: FluidMediaSource) {
  val access = source.access
  FluidListGroup {
    FluidListRow(
      title = source.label,
      subtitle = when (access) {
        is FluidMediaAccess.Locked -> env.labels.lockedTitle(source.label)
        is FluidMediaAccess.NotAllowed -> env.labels.notAllowedTitle
        else -> env.labels.unavailableTitle(source.label)
      },
      onClick = {
        when (access) {
          is FluidMediaAccess.Locked -> env.unlock(access)
          is FluidMediaAccess.NotAllowed -> env.permission.request { env.state.refreshAccess() }
          else -> env.state.refreshAccess()
        }
      },
    )
  }
}

@Composable
private fun PickerAlbumCover(source: FluidMediaSource, album: FluidMediaAlbum, sizePx: Int) {
  val shape = ContinuousCornerShape(FluidRadius.Small)
  Box(
    Modifier.size(56.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    contentAlignment = Alignment.Center,
  ) {
    val cover = album.cover
    if (cover != null) {
      val phase = rememberFluidMediaThumbnail(source, cover, sizePx)
      FluidAsyncImage(phase, contentDescription = null, modifier = Modifier.fillMaxSize())
    } else {
      FluidIcon(FluidIcons.PhotoLibrary, null)
    }
  }
}

// --- La ricerca ----------------------------------------------------------------------------------------------------------

@Composable
private fun PickerSearch(env: PickerEnv) {
  val state = env.state
  val labels = env.labels
  val searchable = remember(state.sources) {
    state.sources.filter { it.capabilities.search }.sortedByDescending { it.capabilities.semanticSearch }
  }
  if (searchable.isEmpty()) return
  var sourceId by rememberSaveable { mutableStateOf(searchable.first().id) }
  val source = searchable.firstOrNull { it.id == sourceId } ?: searchable.first()
  var text by rememberSaveable { mutableStateOf("") }
  var committed by rememberSaveable { mutableStateOf("") }
  LaunchedEffect(text) {
    delay(FluidMediaPickerDefaults.SearchDebounceMillis)
    committed = text.trim()
  }
  val grid = rememberFluidPreviewGridState(initialDensity = FluidMediaPickerDefaults.InitialDensity)
  val backdrop = rememberGlassBackdrop()
  var headerPx by remember { mutableIntStateOf(0) }
  val headerDp = with(LocalDensity.current) { headerPx.toDp() }
  val listing = source.access.allowsListing()
  Box(Modifier.fillMaxSize()) {
    if (!listing) {
      PickerAccessMessage(env, source, Modifier.align(Alignment.Center).padding(top = headerDp))
    } else if (committed.isNotEmpty()) {
      val feed = state.feed(source, state.request.query(text = committed))
      PickerGrid(env, feed, grid, backdrop, top = headerDp, bottomRoom = 150.dp)
      if (feed.interpretation.isNotEmpty()) {
        Text(
          feed.interpretation.joinToString(" · "),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding().padding(bottom = 132.dp),
        )
      }
    }
    PickerHeader(
      env = env,
      title = labels.search,
      subtitle = pickerSubtitle(env, source.label),
      root = false,
      tabs = null,
      backdrop = backdrop.takeIf { listing && committed.isNotEmpty() },
      onHeight = { headerPx = it },
      modifier = Modifier.align(Alignment.TopCenter),
    )
    Column(
      Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      if (searchable.size > 1) {
        FluidFloatingCapsule {
          FluidSegmentedControl(
            options = searchable.map { it.id },
            selected = source.id,
            onSelect = { sourceId = it },
            label = { id -> searchable.first { it.id == id }.label },
          )
        }
      }
      FluidSearchField(
        query = text,
        onQueryChange = { text = it },
        placeholder = if (source.capabilities.semanticSearch) labels.semanticPlaceholder else labels.searchPlaceholder,
        modifier = Modifier.fillMaxWidth().testTag(FluidMediaPickerTags.SearchField),
        onSearch = { committed = text.trim() },
      )
    }
  }
}

// --- L'anteprima ---------------------------------------------------------------------------------------------------------

@Composable
private fun PickerPreview(env: PickerEnv, feed: FluidMediaFeed, preview: PreviewContext) {
  val items = remember(feed, feed.version) { feed.items.toList() }
  val start = items.indexOfFirst { it.key == preview.key }.coerceAtLeast(0)
  val labels = env.labels
  val selection = env.state.selection
  val screenPx = with(LocalDensity.current) {
    val c = LocalConfiguration.current
    maxOf(c.screenWidthDp, c.screenHeightDp).dp.roundToPx().coerceAtMost(2048)
  }
  Box(Modifier.fillMaxSize().testTag(FluidMediaPickerTags.Preview)) {
    FluidFileViewer(
      count = items.size,
      initial = start,
      content = { i ->
        val item = items[i]
        FluidViewerContent.Image(aspectRatio = item.aspectRatio ?: 1f) {
          PreviewImage(feed.source, item, env.ns, screenPx)
        }
      },
      toolbar = { i ->
        val item = items.getOrNull(i) ?: return@FluidFileViewer
        if (env.multiple) {
          val chosen = selection.isSelected(item.key)
          button(
            id = "scegli",
            icon = FluidIcons.CheckCircle,
            label = if (chosen) labels.deselect else labels.select,
            onClick = { selection.toggle(item.key) },
            selected = chosen,
            enabled = chosen || !selection.atLimit,
          )
        } else {
          button(id = "scegli", icon = FluidIcons.Check, label = labels.select, onClick = { env.pickOne(item) })
        }
      },
      onBack = env.back,
      onPageChange = { i -> items.getOrNull(i)?.let { preview.key = it.key } },
    )
  }
}

/** La foto dell'anteprima: subito la miniatura della griglia, poi quella grande; condivisa col riquadro della griglia. */
@Composable
private fun PreviewImage(source: FluidMediaSource, item: FluidMediaItem, ns: FluidGlassNamespace, sizePx: Int) {
  val small = FluidMediaThumbnails.cached(item, with(LocalDensity.current) { 140.dp.roundToPx() })
  val large by produceState<ImageBitmap?>(null, source, item.key, sizePx) {
    value = try {
      source.thumbnail(item, sizePx)?.asImageBitmap()
    } catch (_: Exception) {
      null
    }
  }
  val shown = large ?: small
  Box(Modifier.fillMaxSize().fluidSharedElement(item.key, ns)) {
    if (shown != null) {
      Image(bitmap = shown, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
    } else {
      FluidAsyncImage(FluidImagePhase.Loading, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
  }
}

// --- La consegna -----------------------------------------------------------------------------------------------------------

@Composable
private fun PickerDelivering(state: FluidMediaPickerState, labels: FluidMediaLabels, modifier: Modifier) {
  val progress = state.progress
  FluidFloatingCapsule(modifier = modifier.testTag(FluidMediaPickerTags.Delivering)) {
    FluidSpinner(size = 20.dp, progress = progress?.fraction?.let { f -> { f } })
    Text(
      labels.preparing(progress?.total ?: state.selection.countIn(0)),
      style = MaterialTheme.typography.bodyMedium,
      modifier = Modifier.padding(horizontal = 8.dp),
    )
  }
}

@Composable
private fun PickerNotice(state: FluidMediaPickerState, labels: FluidMediaLabels) {
  val notice = state.notice ?: return
  val refusal = notice.refusal
  val message = when {
    refusal == null -> labels.skipped(notice.skipped)
    refusal == FluidMediaRefusal.Unreachable -> labels.refusedUnreachable
    refusal == FluidMediaRefusal.NothingAvailable -> labels.refusedNothing
    refusal == FluidMediaRefusal.LocationNotRemoved -> labels.refusedLocation
    refusal.code == "too-many" -> labels.refusedTooMany(refusal.limit ?: state.request.limit)
    else -> labels.refusedFailed
  }
  FluidAlert(
    onDismissRequest = { state.notice = null },
    title = if (refusal == null) labels.ok else labels.refusedTitle,
    message = message,
    actions = listOf(FluidAlertAction(labels.ok, { state.notice = null }, FluidAlertAction.Emphasis.Preferred)),
  )
}
