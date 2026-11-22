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
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Info
import dev.antigravity.fluidengine.ui.fluid.toolbar.*
import dev.antigravity.fluidengine.ui.page.FluidPageChromeContent
import dev.antigravity.fluidengine.ui.page.LocalSharedPage
import dev.antigravity.fluidengine.ui.page.LocalFluidPageBar
import dev.antigravity.fluidengine.ui.page.LocalFluidPagePreparing
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
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

private enum class ViewerButton { Info, Tools, Done }
private data class ToolButton(val id: String)

@Composable
internal fun BoxScope.ViewerChrome(
  index: Int, count: Int, ink: Color, visible: Boolean, backdrop: GlassBackdropState,
  toolbar: FluidToolbarScope.(Int) -> Unit, tools: List<ViewerTool>, tool: ViewerTool?,
  select: (String?) -> Unit, onBack: () -> Unit, onInfo: (() -> Unit)?, strings: FluidViewerStrings,
  preset: (FluidToolbarScope.() -> Unit)? = null, width: Dp = Dp.Infinity, fileName: String? = null,
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
  val minimize = remember(progress, policy, space) {
    object : FluidToolbarMinimizeProgress {
      override val progress get() = maxOf(progress.value, 1f - (space?.let { it.host.backdropArrival(it.page) } ?: 1f))
      override val reducedMotion = policy.reducedMotion
    }
  }
  // Both lines have to fit the shared glass row. At large font scales give the title its whole height.
  val singleLineTitle = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
  val counter = if (count > 0) strings.counter(index + 1, count) else ""
  val declaration = buildFluidToolbar(onBack) {
    if (count > 0 && tool == null) toolbar(index)
    // F6C.2: i comandi del preset della pagina corrente («Trova», «A capo») e il suo campo.
    if (count > 0 && tool == null) preset?.invoke(this)
    if (fileName != null && count > 0 && !singleLineTitle) subtitle(counter)
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
  val title = when {
    fileName == null -> counter
    singleLineTitle -> "$counter · $fileName"
    else -> fileName
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
  }
}

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
    FluidGlassSheet(visible = open, onDismissRequest = close, paneTitle = strings.info,
      detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
      selectedDetent = if (large) FluidSheetDetent.Large else FluidSheetDetent.Medium,
      onSelectedDetentChange = { large = it == FluidSheetDetent.Large },
      undimmedUpTo = FluidSheetDetent.Medium) { body() }
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
