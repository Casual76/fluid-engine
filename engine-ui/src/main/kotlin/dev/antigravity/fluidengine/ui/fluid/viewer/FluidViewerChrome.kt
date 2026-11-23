package dev.antigravity.fluidengine.ui.fluid.viewer

import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Info
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.Share
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.page.FluidPageChromeContent
import dev.antigravity.fluidengine.ui.page.LocalSharedPage
import dev.antigravity.fluidengine.ui.page.LocalFluidPageBar
import dev.antigravity.fluidengine.ui.page.LocalFluidPagePreparing
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import kotlin.math.roundToInt

data class FluidViewerStrings(
  val counter: (current: Int, count: Int) -> String = { current, count -> "$current di $count" },
  val info: String = "Informazioni",
  val done: String = "Fine",
  val empty: String = "Nessun file",
  val showControls: String = "Mostra controlli",
  val hideControls: String = "Nascondi controlli",
  val close: String = "Chiudi",
  val copy: String = "Copia valore",
  val file: String = "File",
  val name: String = "Nome",
  val type: String = "Tipo",
  val size: String = "Dimensione",
  val openWith: String = "Apri con…",
  val share: String = "Condividi",
  val actionFailed: String = "Impossibile completare l’azione",
  val pages: (Int) -> String = { "$it pagine" },
  val lines: (Int) -> String = { "$it righe" },
  val location: String = "Luogo",
  val coordinates: String = "Coordinate",
  val openMap: String = "Apri in Mappe",
  val chapters: String = "Capitoli",
  val more: String = "Altro",
  val sheets: (Int) -> String = { "$it fogli" },
  /** F6C.19 · Le voci del menu del file ([FluidViewerFileActions]) e ciò che TalkBack dice del titolo che lo apre. */
  val copyFile: String = "Copia",
  val print: String = "Stampa",
  val rename: String = "Rinomina",
  val move: String = "Sposta",
  val duplicate: String = "Duplica",
  val delete: String = "Elimina",
  val fileMenu: String = "Mostra le azioni del file",
)

internal class ViewerTool(
  val id: String, val icon: ImageVector, val label: String,
  val actions: FluidToolbarScope.(Int) -> Unit,
  val body: @Composable (Int) -> Unit,
)

class FluidViewerToolsScope internal constructor() {
  internal val items = mutableListOf<ViewerTool>()

  /** Stable [id] restores the editor after recreation. Its actions share one capsule with Done. */
  fun tool(
    id: String,
    icon: ImageVector,
    label: String,
    actions: FluidToolbarScope.(index: Int) -> Unit = {},
    content: @Composable (index: Int) -> Unit,
  ) {
    require(items.none { it.id == id }) { "Duplicate viewer tool: $id" }
    items += ViewerTool(id, icon, label, actions, content)
  }
}

/** Timing of a tap on the file. R5 V5, medium confidence (scroll grammar applied to a tap). */
internal object ViewerChromeMotion {
  const val HideMillis = 200
  const val ShowMillis = 350

  fun duration(hidden: Boolean, distance: Float, scale: Float): Int =
    ((if (hidden) HideMillis else ShowMillis) * distance.coerceIn(0f, 1f) * scale).roundToInt()
}

private enum class ViewerButton { Info, Tools, Done, More, BoardDock, TextShare }
private data class ToolButton(val id: String)
internal val ViewerBoardTopInset = 44.dp

@Composable
internal fun BoxScope.ViewerChrome(
  index: Int, count: Int, ink: Color, visible: Boolean, backdrop: GlassBackdropState,
  toolbar: FluidToolbarScope.(Int) -> Unit, tools: List<ViewerTool>, tool: ViewerTool?,
  select: (String?) -> Unit, onBack: () -> Unit, onInfo: (() -> Unit)?, strings: FluidViewerStrings,
  preset: (FluidToolbarScope.() -> Unit)? = null, width: Dp = Dp.Infinity, fileName: String? = null,
  documentRoute: Boolean = false,
  file: FluidViewerContent? = null, info: FluidViewerInfo? = null,
  /** F6C.19 · Le azioni del file della pagina e la stampa del visore: il menu che nasce dal nome. */
  fileActions: FluidViewerFileActions? = null, print: (() -> Unit)? = null,
  /** F6C.19 · «Nascondi controlli» nel «•••» di una pagina che non ha azioni del tipo. */
  onHideControls: (() -> Unit)? = null,
) {
  val policy = LocalFluidMotionPolicy.current
  val progress = remember { Animatable(if (visible) 0f else 1f) }
  LaunchedEffect(visible, policy) {
    val target = if (visible) 0f else 1f
    if (policy.reducedMotion) progress.snapTo(target) else progress.animateTo(target, tween(
      ViewerChromeMotion.duration(!visible, abs(target - progress.value), policy.durationScale), easing = LinearEasing,
    ))
  }
  val space = LocalSharedPage.current
  val arrival = remember { Animatable(0f) }
  LaunchedEffect(documentRoute, space, policy) {
    val host = space?.host ?: return@LaunchedEffect
    if (!documentRoute || policy.reducedMotion) {
      arrival.snapTo(0f)
      return@LaunchedEffect
    }
    snapshotFlow { host.upper == space.page && host.isMoving && host.opening }.collectLatest { entering ->
      if (entering) {
        arrival.snapTo(1f)
        arrival.animateTo(0f, tween(ViewerChromeMotion.duration(false, 1f, policy.durationScale), easing = LinearEasing))
      } else if (arrival.value > 0f) {
        // The spring can finish before V5 has brought the bars into focus.
        arrival.animateTo(0f, tween(ViewerChromeMotion.duration(false, arrival.value, policy.durationScale),
          easing = LinearEasing))
      }
    }
  }
  val minimize = remember(progress, arrival, policy, space) {
    object : FluidToolbarMinimizeProgress {
      override val progress get() =
        maxOf(progress.value, arrival.value, 1f - (space?.let { it.host.backdropArrival(it.page) } ?: 1f))
      override val reducedMotion = policy.reducedMotion
    }
  }
  // Both lines have to fit the shared glass row. At large font scales give the title its whole height.
  val singleLineTitle = LocalDensity.current.fontScale >= 1.5f
  val counter = if (count > 0) strings.counter(index + 1, count) else ""
  val board = fileName != null && file !is FluidViewerContent.Image &&
    (file !is FluidViewerContent.Media || file.videoSurface == null)
  val isText = file is FluidViewerContent.Text
  val language = (file as? FluidViewerContent.Text)?.source?.language?.takeIf(String::isNotBlank)
  val splitDock = board && tool == null && file !is FluidViewerContent.Unknown
  val isArchive = info?.file?.kind == FluidFileKind.Archive
  var actionFailed by remember(index, fileName) { mutableStateOf(false) }
  val measure = rememberTextMeasurer()
  val density = LocalDensity.current
  val faceStyle = MaterialTheme.typography.labelLarge.viewerSize(14.5f, FontWeight.SemiBold)
  val faceWidth: (String) -> Dp = { label ->
    with(density) { measure.measure(label, faceStyle, maxLines = 1).size.width.toDp() + 45.dp }
  }
  // F6C.19: il nome senza estensione con «⌄» apre il menu del file (il titolo selettore di F6B.11), con le voci di
  // File e Quick Look di iOS 26. Un'azione che fallisce lo dice sotto la barra, come quelle di «•••».
  val runFileAction: (() -> Unit) -> Unit = { action ->
    actionFailed = false
    fluidRunFileAction(action) { actionFailed = true }
  }
  val wantsFileMenu = fileName != null && count > 0 && tool == null
  val fileMenu: (Set<String>) -> List<ViewerFileEntry>? = { dock ->
    if (!wantsFileMenu) null
    else viewerFileMenuEntries(fileActions, info?.actions.orEmpty(), print, onInfo, dock, strings).ifEmpty { null }
  }
  val declarationBase = buildFluidToolbar(onBack) {
    var menu: List<ViewerFileEntry>? = null
    if (count > 0 && tool == null) {
      if (splitDock) {
        val app = FluidToolbarScope().apply { toolbar(index) }
        val page = FluidToolbarScope().apply { preset?.invoke(this) }
        search = page.search ?: app.search
        keyboard = page.keyboard ?: app.keyboard
        overflowLabel = page.overflowLabel ?: app.overflowLabel
        // Un titolo selettore dichiarato dall'app vince sul menu del file.
        titleSelector = app.titleSelector
        val share = app.entries.filterIsInstance<FluidToolbarButtonEntry>().firstOrNull {
          it.placement == FluidToolbarPlacement.BottomBar && (it.icon == FluidIcons.IosShare || it.icon == FluidIcons.Share)
        }
        val archiveSearch = page.entries.filterIsInstance<FluidToolbarButtonEntry>().firstOrNull {
          isArchive && it.id == CustomPresetButton.Find
        }
        // F6C.19: il «•••» della pagina, con le azioni del suo tipo (`viewerTypeActions`, o un «•••» con menu come quello
        // dell'archivio di prima).
        val typeEntry = page.entries.filterIsInstance<FluidToolbarButtonEntry>().firstOrNull {
          it.id == ViewerTypeActionsId || it.icon == FluidIcons.MoreHoriz && it.menu != null
        }
        menu = fileMenu(viewerEntryLabels(page.entries.filter { it.declaresBottomAction(false) && it !== typeEntry }) +
          listOfNotNull(share?.label))
        // «•••» tiene solo le azioni del tipo: quelle del file stanno nel titolo, senza doppioni.
        val typeMenu = typeEntry?.withoutMenuLabels(menu?.mapTo(HashSet()) { it.label }.orEmpty())
        entries += app.entries.filterNot { it === share }
        entries += page.entries.filter { !it.declaresBottomAction(false) }
        group(ViewerButton.BoardDock, placement = FluidToolbarPlacement.BottomBar) {
          if (isText) flexibleSpacer()
          else if (onInfo != null) {
            if (typeMenu != null) entries += typeMenu
            // Scelta di Alessio del 23 novembre: «•••» c'è sempre. Una pagina senza azioni del tipo ha la sola che vale
            // per ogni pagina, «Nascondi controlli» (il tocco sul file, per chi non lo conosce e per TalkBack).
            else if (menu != null) {
              if (onHideControls != null) action(ViewerButton.More, FluidIcons.MoreHoriz, strings.more) {
                listOf(FluidContextAction(strings.hideControls, onClick = onHideControls))
              }
            } else action(ViewerButton.More, FluidIcons.MoreHoriz, strings.more) {
              info?.actions.orEmpty().filterNot { it.label == strings.share && share != null }
                .map { item ->
                  FluidContextAction(item.label) {
                    actionFailed = false
                    fluidRunFileAction(item.onClick) { actionFailed = true }
                  }
                }
                .ifEmpty { listOf(FluidContextAction(strings.info, onClick = onInfo)) }
            }
            flexibleSpacer()
          }
          entries += page.entries.filter { it.declaresBottomAction(false) && it !== archiveSearch && it !== typeEntry }
            .map { it.withViewerLabels(faceWidth, faceStyle) }
          if (isText) {
            if (share != null) group(ViewerButton.TextShare) { entries += share }
            flexibleSpacer()
          } else {
            flexibleSpacer()
            (archiveSearch ?: share)?.let { entries += it }
          }
        }
      } else {
        toolbar(index)
        preset?.invoke(this)
        menu = fileMenu(viewerEntryLabels(entries.filter { it.declaresBottomAction(false) }))
      }
    }
    val fileEntries = menu
    if (fileEntries != null && titleSelector == null) {
      title(titleMode, menu = { fileEntries.toContextActions(runFileAction) }, selectorHint = strings.fileMenu)
    }
    if (fileName != null && count > 0 && !singleLineTitle) {
      subtitle(when (file) {
        is FluidViewerContent.Text -> listOfNotNull(language?.replaceFirstChar { it.titlecase() }, file.source.lineCount?.let(strings.lines))
          .joinToString(" · ").ifBlank { counter }
        is FluidViewerContent.Grid -> listOfNotNull(info?.subtitle, strings.sheets(file.source.sheets.size))
          .joinToString(" · ")
        is FluidViewerContent.Pages -> info?.subtitle?.takeIf { info.file.mimeType != "application/pdf" } ?: counter
        is FluidViewerContent.Custom -> info?.subtitle?.takeUnless { isArchive } ?: counter
        else -> counter
      })
    }
    if (tool == null && onInfo != null) button(ViewerButton.Info, FluidIcons.Info, strings.info,
      placement = FluidToolbarPlacement.Trailing, onClick = onInfo)
    group(ViewerButton.Tools, placement = FluidToolbarPlacement.BottomBar) {
      if (tool == null) {
        if (count > 0) for (item in tools) button(ToolButton(item.id), item.icon, item.label, { select(item.id) })
      } else {
        tool.actions(this, index)
        button(ViewerButton.Done, FluidIcons.Check, strings.done, { select(null) })
      }
    }
  }
  val accent = language?.let { MaterialTheme.colorScheme.tertiary }
  val declaration = if (!board) declarationBase else FluidToolbarDeclaration(
    declarationBase.entries, declarationBase.titleMode, declarationBase.subtitle, declarationBase.search,
    declarationBase.overflowLabel, declarationBase.keyboard, declarationBase.titleSelector, viewerLayout = true,
    subtitleAccent = accent,
    subtitleOrdinal = counter.takeIf { count > 1 && declarationBase.subtitle != null && declarationBase.subtitle != counter },
  )
  // F6C.19: il nome senza estensione, come in Quick Look.
  val shownName = fileName?.let(::fluidViewerTitleName)
  val title = when {
    shownName == null -> counter
    singleLineTitle -> "$counter · $shownName"
    else -> shownName
  }
  val front by LocalFluidRouteFront.current
  val chrome = if (LocalFluidPagePreparing.current) null else LocalFluidChromeController.current
  val replacement = remember { FluidBottomReplacement() }
  val anchored = LocalFluidPageBar.current != null
  CompositionLocalProvider(LocalContentColor provides ink, LocalFluidToolbarMinimize provides minimize) {
    // The shell renders these very slots on its existing glass when it has a tab bar (F6B.2).
    replacement.declaration = declaration
    replacement.replacesTabBar = declaration.hasBottomBar
    replacement.context = currentCompositionLocalContext
    replacement.minimize = minimize
    DisposableEffect(chrome, backdrop, replacement) {
      chrome?.registerBackdrop(replacement, backdrop)
      chrome?.setBottomReplacement(replacement, replacement)
      onDispose { chrome?.unregisterBackdrop(replacement) }
    }
    SideEffect { chrome?.setBackdropFront(replacement, front) }
    // Una ricerca dichiarata (F6C.2, «trova» del preset testo): la disegna la barra dell'host, o qui fuori da un host.
    val search = rememberToolbarSearch(rememberOwnedSearch(declaration.search), width, declared = true)
    FluidPageChromeContent(title, Modifier.align(Alignment.TopCenter), onBack, backdrop,
      barTint = null, toolbar = declaration, actions = {}, minimize = minimize, search = search)
    if (!anchored) {
      if (search?.occupiesBottom != true) FluidBottomBar(declaration, backdrop, minimize, Modifier.align(Alignment.BottomCenter))
      if (search != null) FluidToolbarSearchOverlay(search, declaration, backdrop, minimize)
    }
    if (actionFailed && visible) Text(strings.actionFailed,
      Modifier.align(Alignment.BottomCenter).padding(bottom = viewerBottomCapsuleTop() + 16.dp)
        .background(MaterialTheme.colorScheme.surface, FluidCapsuleShape).padding(horizontal = 14.dp, vertical = 6.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }, color = MaterialTheme.colorScheme.error,
      style = MaterialTheme.typography.bodyMedium)
  }
}

/** Le etichette dei tasti di [entries], anche dentro i gruppi: la barra in basso le ha già, il menu del file no. */
private fun viewerEntryLabels(entries: List<FluidToolbarEntry>): Set<String> = buildSet {
  fun visit(list: List<FluidToolbarEntry>) {
    for (entry in list) when (entry) {
      is FluidToolbarButtonEntry -> {
        entry.label?.let(::add)
        entry.text?.let(::add)
      }
      is FluidToolbarGroupEntry -> visit(entry.children)
      else -> Unit
    }
  }
  visit(entries)
}

/**
 * F6C.19 · Il «•••» di una pagina senza le voci che il menu del file ha già ([labels]); null se non gli resta niente,
 * così non si apre un menu vuoto.
 */
private fun FluidToolbarButtonEntry.withoutMenuLabels(labels: Set<String>): FluidToolbarButtonEntry? {
  val original = menu ?: return this
  if (original().none { it.label !in labels }) return null
  if (labels.isEmpty()) return this
  return FluidToolbarButtonEntry(id, role, placement, priority, icon = icon, text = text, label = label, onClick = onClick,
    tint = tint, selected = selected, enabled = enabled, badgeCount = badgeCount, badgeDot = badgeDot,
    sharedBackground = sharedBackground, destructive = destructive, width = width, custom = custom,
    defaultLabel = defaultLabel, overflow = overflow, menu = { original().filterNot { it.label in labels } }, type = type,
    undo = undo, transientUndo = transientUndo)
}

private fun FluidToolbarEntry.withViewerLabels(width: (String) -> Dp, style: androidx.compose.ui.text.TextStyle): FluidToolbarEntry {
  if (this is FluidToolbarGroupEntry) return FluidToolbarGroupEntry(id, placement, priority, overflow, sharedBackground,
    children.map { it.withViewerLabels(width, style) })
  if (this !is FluidToolbarButtonEntry || onClick == null || menu != null || custom != null || type != null || undo != null ||
    icon == null || label == null || id == TextPresetButton.Wrap) return this
  return FluidToolbarButtonEntry(id, role, placement, priority, icon = icon, label = label, onClick = onClick,
    tint = tint, selected = selected, enabled = enabled, badgeCount = badgeCount, badgeDot = badgeDot,
    sharedBackground = sharedBackground, destructive = destructive, width = width(label), overflow = overflow,
    custom = {
      val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FluidIcon(icon, null, tint = color, size = 18.dp)
        Text(label, color = color, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    })
}

@Composable
internal fun ViewerToolbarSubtitle(declaration: FluidToolbarDeclaration?, fallback: String) {
  val ink = LocalContentColor.current.copy(alpha = FluidToolbarDefaults.SubtitleInk)
  // Keep the position in the file collection available to accessibility while the visible
  // subtitle describes the document being read.
  val ordinal = declaration?.subtitleOrdinal
  val position = if (ordinal == null) Modifier else Modifier.semantics { text = AnnotatedString(ordinal) }
  Row(position, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
    declaration?.subtitleAccent?.let { Box(Modifier.size(7.dp).background(it, FluidCapsuleShape)) }
    Text(fallback, style = if (declaration?.viewerLayout == true)
      MaterialTheme.typography.bodySmall.viewerSize(12f, FontWeight.Medium) else MaterialTheme.typography.bodySmall,
      color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}

/**
 * How far from the bottom of the screen the viewer's bottom capsule ends (F6C.15b): the navigation bar, the room of a
 * tab bar when the app has one ([LocalFluidBottomInset]), the capsule's own margin ([fluidBottomBarOffset]) and its
 * height. A row that floats above the capsule (the sheet tabs of the grid) stands on this line plus a gap, and the
 * content of a page that scrolls ends above both, so at the end of the scroll nothing stays under a capsule. The
 * navigation bar counts even while it is hidden with the chrome, so the content does not jump.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun viewerBottomCapsuleTop(): Dp =
  WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding() +
    fluidBottomBarOffset(LocalFluidBottomInset.current) + FluidFloatingCapsuleDefaults.ActionSize

@Composable
internal fun viewerPhotoSpace(compactInspector: Boolean): Float {
  val target = if (compactInspector) 1f - FluidSheetMediumFraction else 1f
  if (LocalFluidMotionPolicy.current.reducedMotion) return target
  return animateFloatAsState(target, FluidMotion.gentle(), label = "ViewerPhotoSpace").value
}

@Composable
internal fun BoxScope.ViewerInfo(
  open: Boolean, wide: Boolean, backdrop: GlassBackdropState, strings: FluidViewerStrings,
  close: () -> Unit, body: @Composable () -> Unit,
) {
  var large by rememberSaveable(open) { mutableStateOf(false) }
  if (!wide) {
    val pageColors = MaterialTheme.colorScheme
    CompositionLocalProvider(LocalFluidSheetEdgeToEdge provides true) {
      FluidGlassSheet(visible = open, onDismissRequest = close, paneTitle = strings.info,
        detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
        selectedDetent = if (large) FluidSheetDetent.Large else FluidSheetDetent.Medium,
        onSelectedDetentChange = { large = it == FluidSheetDetent.Large },
        undimmedUpTo = FluidSheetDetent.Medium) {
        CompositionLocalProvider(LocalViewerInfoPageColors provides pageColors) { body() }
      }
    }
    return
  }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val side = if (LocalLayoutDirection.current == LayoutDirection.Ltr) 1 else -1
  AnimatedVisibility(open, modifier = Modifier.align(Alignment.CenterEnd),
    enter = if (reduced) androidx.compose.animation.EnterTransition.None else slideInHorizontally(FluidMotion.gentle()) { it * side },
    exit = if (reduced) androidx.compose.animation.ExitTransition.None else slideOutHorizontally(FluidMotion.gentle()) { it * side }) {
    Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(FluidScreenDefaults.HorizontalPadding)
      .width(FluidDrawerDefaults.Width).fillMaxHeight()
      .glassSurface(backdrop, GlassDefaults.panelTint(), ContinuousCornerShape(FluidRadius.Sheet))
      .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } }
      .semantics { paneTitle = strings.info }) {
      body()
    }
  }
}

/** Restore status and navigation visibility independently, including the host's original icon contrast. */
@Composable
@Suppress("DEPRECATION")
internal fun ViewerSystemBars(visible: Boolean, photo: Boolean) {
  val window = LocalActivity.current?.window ?: return
  val view = LocalView.current
  val light = !photo && MaterialTheme.colorScheme.surface.luminance() > 0.5f
  val controller = remember(window, view) { WindowCompat.getInsetsController(window, view) }
  val status = WindowInsetsCompat.Type.statusBars()
  val navigation = WindowInsetsCompat.Type.navigationBars()
  DisposableEffect(window, view) {
    val original = ViewCompat.getRootWindowInsets(view)
    // Before R a gesture bar can have no inset even when requested. Preserve the actual window flags.
    val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.R
    val flags = window.decorView.systemUiVisibility
    val statusVisible = if (legacy) flags and View.SYSTEM_UI_FLAG_FULLSCREEN == 0 &&
      window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0 else original?.isVisible(status) != false
    val navigationVisible = if (legacy) flags and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0 else original?.isVisible(navigation) != false
    val statusLight = controller.isAppearanceLightStatusBars
    val navigationLight = controller.isAppearanceLightNavigationBars
    onDispose {
      if (statusVisible) controller.show(status) else controller.hide(status)
      if (navigationVisible) controller.show(navigation) else controller.hide(navigation)
      controller.isAppearanceLightStatusBars = statusLight
      controller.isAppearanceLightNavigationBars = navigationLight
    }
  }
  LaunchedEffect(controller, visible, light) {
    controller.isAppearanceLightStatusBars = light
    controller.isAppearanceLightNavigationBars = light
    if (visible) controller.show(status or navigation) else controller.hide(status or navigation)
  }
}
