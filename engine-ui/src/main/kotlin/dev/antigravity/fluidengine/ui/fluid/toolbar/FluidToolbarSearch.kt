package dev.antigravity.fluidengine.ui.fluid.toolbar

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAnimation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPaneDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchLabels
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPurpose
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidTextFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassInkContent
import dev.antigravity.fluidengine.ui.fluid.LocalFluidBottomInset
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.SearchClearButton
import dev.antigravity.fluidengine.ui.fluid.currentGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.fluidFloatingGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidSearchContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidSearchWidth
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.Edit
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import dev.antigravity.fluidengine.ui.fluid.smoothStep
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlin.math.roundToInt

/** Le sedi di Principal appartengono al contenuto. Le altre due sono sul piano della barra. */
internal enum class FluidToolbarSearchSite {
  /** Il primo elemento della lista, sotto il titolo grande: dove le app lo mettono oggi. */
  UnderTitle,

  /** Un solo campo sale dalla posizione nel contenuto e si posa sotto la barra di stato. */
  PinnedPrincipal,

  /** La riga dei tasti in alto. */
  TopBar,

  /** Sopra il contenuto, in basso (la capsula della ricerca di F4B). */
  Bottom,
}

/** Principal conserva il posto nel contenuto quando il ritiro è disabilitato. */
internal fun fluidToolbarSearchSits(spec: FluidSearchSpec, site: FluidToolbarSearchSite): Boolean =
  spec.placement == FluidSearchPlacement.Principal &&
    (site == FluidToolbarSearchSite.UnderTitle || site == FluidToolbarSearchSite.PinnedPrincipal)

internal fun searchBar(placement: FluidSearchPlacement, width: Dp): FluidToolbarBar? = when (placement) {
  FluidSearchPlacement.Automatic -> if (width < FluidPaneDefaults.MediumBreakpoint) FluidToolbarBar.Bottom else FluidToolbarBar.Top
  FluidSearchPlacement.TopBar -> FluidToolbarBar.Top
  FluidSearchPlacement.BottomBar -> FluidToolbarBar.Bottom
  FluidSearchPlacement.Principal -> null
}

internal val LocalToolbarSearch = compositionLocalOf<ToolbarSearch?> { null }
// WWDC25 256, f7320..7330, a 30000/1001 Hz. Curva del centro della lente nella clip a 1×.
internal val ToolbarSearchTiming = tween<Float>(334, easing = CubicBezierEasing(.38f, 0f, .35f, 1f))
internal val ToolbarSearchAnimation = FluidGlassAnimation.Custom(ToolbarSearchTiming)
internal val LocalPrincipalSearchActive = compositionLocalOf { false }
private val SearchLabels = FluidSearchLabels()
private val SearchOnly = buildFluidToolbar(null) {}

/** Il proprietario vive nella pagina anche quando l'editor viene disegnato dalla barra dell'host. */
@Composable
internal fun rememberOwnedSearch(spec: FluidSearchSpec?): FluidSearchSpec? {
  if (spec == null) return null
  var localActive by rememberSaveable { mutableStateOf(spec.active) }
  return if (spec.onActiveChange != null) spec else spec.copy(active = localActive, onActiveChange = { localActive = it })
}

/** Testo, selezione e composizione IME hanno lo stesso proprietario anche nella barra di un altro sottalbero. */
@Composable
internal fun rememberToolbarSearch(spec: FluidSearchSpec?, width: Dp, declared: Boolean): ToolbarSearch? {
  if (spec == null) return null
  val bar = searchBar(spec.placement, width) ?: return null
  val editor = rememberSaveable(stateSaver = TextFieldValue.Saver) {
    mutableStateOf(TextFieldValue(spec.query, TextRange(spec.query.length)))
  }
  // La pagina riconcilia anche cancellazioni e trasformazioni richieste dall'app. Un normale eco della
  // query conserva sia la selezione sia la regione di composizione che Gboard ha appena consegnato.
  if (editor.value.text != spec.query) SideEffect {
    editor.value = TextFieldValue(spec.query, editor.value.selection)
  }
  return ToolbarSearch(spec, bar, declared, editor)
}

internal data class ToolbarSearch(
  val spec: FluidSearchSpec,
  val bar: FluidToolbarBar,
  val hasDeclaration: Boolean,
  val editor: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(spec.query)),
) {
  val atBottom get() = bar == FluidToolbarBar.Bottom
  val takesTop get() = !atBottom && spec.active
  val occupiesBottom get() = atBottom || takesTop && spec.expansion == FluidSearchExpansion.AboveKeyboard

  fun owns(row: FluidToolbarBar) = (row == FluidToolbarBar.Bottom) == atBottom

  fun layout(model: FluidToolbarModel, width: Float, metrics: FluidToolbarMetrics): ToolbarSearchPose {
    val active = spec.active
    val neighbours = resolveFluidToolbar(model, width, active, metrics, bar)
    val count = neighbours.elements.count { it.item.id != FluidToolbarOverflowId && !it.item.overflow }
    val compact = !active && when (spec.behavior) {
      FluidSearchBehavior.CollapseToButton -> true
      FluidSearchBehavior.AlwaysVisible -> false
      FluidSearchBehavior.Automatic -> count > 2
    }
    // Nella riga attiva tutti i comandi della stessa barra rimangono raggiungibili da Altro.
    val others = if (active) FluidToolbarModel(model.items.map { it.copy(overflow = true) }) else model
    val lens = FluidMetrics.GlassButtonLens.value
    val margin = metrics.edgeMarginDp.coerceAtMost((width - lens).coerceAtLeast(0f) / 2)
    val inside = (width - 2 * margin).coerceAtLeast(0f)
    val preferred = resolveFluidToolbar(others, width, active, metrics, bar)
    val hasOthers = preferred.groups.isNotEmpty()
    val gap = if (hasOthers) metrics.groupSpacingDp else 0f
    val room = if (hasOthers) (preferred.requiredWidthDp - 2 * metrics.edgeMarginDp).coerceAtLeast(metrics.buttonWidthDp) else 0f
    val ideal = if (compact) lens else (inside - room - gap).coerceAtLeast(lens)
    val cap = when {
      active || atBottom -> inside
      !hasDeclaration -> FluidSearchFieldDefaults.ExpandedFallbackWidth.value / 2
      else -> FluidSearchFieldDefaults.ExpandedFallbackWidth.value
    }
    val fieldWidth = ideal.coerceAtMost(cap).coerceAtMost((inside - if (hasOthers) metrics.buttonWidthDp + gap else 0f).coerceAtLeast(0f))
    val reserved = fieldWidth + gap
    val row = resolveFluidToolbar(others, (width - reserved).coerceAtLeast(0f), active, metrics, bar)
    // In basso, il campo a riposo si inserisce fra i comandi dei due lati (Mail).
    // In alto, il fissato conserva il bordo esterno finché la ricerca non è attiva.
    val after = row.groups.filter { group ->
      group.items.any { it.placement == FluidToolbarPlacement.PinnedTrailing } ||
        atBottom && !compact && !active && group.side == FluidToolbarSide.Trailing
    }
    val afterIds = after.mapTo(mutableSetOf()) { it.id }
    val shifted = if (afterIds.isEmpty()) row else row.copy(
      groups = row.groups.map { if (it.id in afterIds) it.copy(leftDp = it.leftDp + reserved) else it },
      elements = row.elements.map { if (it.groupId in afterIds) it.copy(leftDp = it.leftDp + reserved) else it },
    )
    val x = after.minOfOrNull { it.leftDp } ?: (width - margin - fieldWidth)
    return ToolbarSearchPose(shifted.copy(widthDp = width), x, fieldWidth, compact)
  }
}

internal data class ToolbarSearchPose(
  val neighbours: FluidToolbarResolved, val left: Float, val width: Float, val compact: Boolean,
) {
  fun extent(density: Float): FluidToolbarExtent {
    val rest = fluidToolbarExtent(neighbours, density)
    return rest.copy(endPx = maxOf(rest.endPx, ((neighbours.widthDp - left) * density).roundToInt()))
  }
}

/** Lo slot libero conserva i propri tasti e lascia al campo il bordo di fine riga. */
internal fun fluidSearchLegacyActions(
  search: ToolbarSearch?, declaration: FluidToolbarDeclaration?, actions: @Composable RowScope.() -> Unit,
): @Composable RowScope.() -> Unit {
  if (search == null || search.atBottom || declaration != null) return actions
  return {
    if (!search.spec.active) {
      actions()
      val width = if (search.spec.behavior == FluidSearchBehavior.CollapseToButton) FluidMetrics.GlassButtonLens
        else FluidSearchFieldDefaults.ExpandedFallbackWidth / 2
      Spacer(Modifier.width(width + FluidMetrics.GroupSpacing))
    } else Spacer(Modifier.fillMaxWidth())
  }
}

/** Spazio residuo dopo gli inset della finestra, anche se l'IME arriva a viaggio già iniziato. */
internal fun toolbarSearchOffset(availablePx: Int, rowPx: Int, fraction: Float): Int =
  ((availablePx - rowPx).coerceAtLeast(0) * fraction.coerceIn(0f, 1f)).roundToInt()

/** Altezza utilizzabile in coordinate fisiche, inclusi host già ridimensionati o spostati da Android. */
internal fun searchImeHeight(hostHeight: Int, hostTop: Int, imeTop: Int, gap: Int): Int =
  (imeTop - hostTop - gap).coerceIn(0, hostHeight)

/** Il sistema può traslare la vista per mostrare il cursore anche in una finestra edge-to-edge. */
@Composable
private fun Modifier.searchImeViewport(): Modifier {
  val view = LocalView.current
  val density = LocalDensity.current
  val ime = WindowInsets.ime
  val showing = ime.getBottom(density) > 0
  var topInRoot by remember { mutableIntStateOf(0) }
  var maxHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
  // In multifinestra le metriche cambiano anche senza ruotare lo schermo.
  val windowSize = LocalWindowInfo.current.containerSize
  if (showing) DisposableEffect(view, ime, density, windowSize, topInRoot) {
    val location = IntArray(2)
    val frame = Rect()
    val windowBottom = if (Build.VERSION.SDK_INT >= 30) {
      view.context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds?.bottom
    } else null
    val gap = with(density) { 4.dp.roundToPx() }
    fun update() {
      view.getLocationOnScreen(location)
      val keyboardTop = if (windowBottom != null) windowBottom - ime.getBottom(density)
        else { view.getWindowVisibleDisplayFrame(frame); frame.bottom }
      if (keyboardTop > 0) maxHeight = searchImeHeight(Int.MAX_VALUE, location[1] + topInRoot, keyboardTop, gap)
    }
    update()
    val observer = view.viewTreeObserver
    val listener = ViewTreeObserver.OnPreDrawListener { update(); true }
    observer.addOnPreDrawListener(listener)
    onDispose { if (observer.isAlive) observer.removeOnPreDrawListener(listener) }
  }
  return onGloballyPositioned { topInRoot = it.positionInRoot().y.roundToInt() }.layout { child, constraints ->
    val height = if (showing) minOf(constraints.maxHeight, maxHeight) else constraints.maxHeight
    val placeable = child.measure(constraints.copy(minHeight = minOf(constraints.minHeight, height), maxHeight = height))
    layout(placeable.width, constraints.constrainHeight(placeable.height)) { placeable.placeRelative(0, 0) }
  }
}

/** La finestra IME guida solo la quota. Il piano conserva la propria cattura e l'editor il proprio focus. */
@Composable
internal fun FluidToolbarSearchOverlay(
  search: ToolbarSearch, declaration: FluidToolbarDeclaration?, backdrop: GlassBackdropState,
  minimize: FluidToolbarMinimizeProgress?,
) {
  val active = search.spec.active
  val bottom = search.occupiesBottom
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val position = animateFloatAsState(if (bottom) 1f else 0f,
    if (reduced) snap() else FluidMotion.standard(), label = "search keyboard position")
  val metrics = if (search.atBottom) fluidBottomBarMetrics() else fluidToolbarMetrics()
  val rowHeight = metrics.buttonWidthDp.dp
  val appInset = LocalFluidBottomInset.current
  val bottomPadding = fluidBottomBarOffset(appInset)
  val reserved = PaddingValues(bottom = bottomPadding)
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalFluidCanvasBackdrop provides null,
    LocalFluidGlassQuality provides null) {
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
      .padding(reserved).searchImeViewport()) {
      val available = constraints.maxHeight
      val page = declaration ?: SearchOnly
      val model = fluidToolbarModelOf(page, null)
      val pose = remember(model, maxWidth, search.bar, search.hasDeclaration, active, search.spec.behavior) {
        search.layout(model, maxWidth.value, metrics)
      }
      Box(Modifier.fillMaxWidth().offset { IntOffset(0, toolbarSearchOffset(available, rowHeight.roundToPx(), position.value)) }
        .height(rowHeight).then(if (active) Modifier else Modifier.fluidToolbarMinimizeLayer(minimize, towardsBottom = search.atBottom))) {
        FluidToolbarGroups(page, pose.neighbours, reduced, interactive = true) { namespace ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { ToolbarSearchField(search, pose, namespace, Modifier) }
        }
      }
      if (active && search.spec.suggestions != null) {
        Box(Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter)
          .padding(horizontal = FluidMetrics.ScreenEdgeMargin)
          .padding(top = if (bottom) 0.dp else rowHeight, bottom = if (bottom) rowHeight else 0.dp)) {
          search.spec.suggestions.invoke()
        }
      }
    }
  }
}

/** Il velo protegge l'inchiostro anche nel tratto colorato che resta più chiaro o scuro della media della sonda. */
internal fun fluidSearchReadingVeil(ink: Color): Color =
  if (ink.luminance() > .5f) Color.Black.copy(alpha = .40f) else Color.White.copy(alpha = .16f)

internal fun Modifier.fluidSearchReadingSurface(ink: Color, progress: () -> Float): Modifier {
  val veil = fluidSearchReadingVeil(ink)
  return drawBehind {
    val amount = progress().coerceIn(0f, 1f)
    if (amount > 0f) drawRoundRect(veil.copy(alpha = veil.alpha * amount),
      cornerRadius = CornerRadius(size.height / 2f))
  }
}

private object ToolbarFieldId

@Composable
private fun ToolbarSearchField(search: ToolbarSearch, pose: ToolbarSearchPose, namespace: FluidGlassNamespace, modifier: Modifier) {
  val spec = search.spec
  val active = spec.active
  val purposeSearch = spec.purpose == FluidSearchPurpose.Search
  val closeLabel = if (purposeSearch) SearchLabels.close else LocalFluidLabels.current.close
  val prompt = spec.prompt ?: if (purposeSearch) SearchLabels.search else LocalFluidLabels.current.message
  val toggle = requireNotNull(spec.onActiveChange)
  val focus = remember { FocusRequester() }
  val manager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  var wasActive by remember { mutableStateOf(active) }
  val close = { toggle(false) }
  val currentClose by rememberUpdatedState(close)
  val ime = WindowInsets.ime
  val density = LocalDensity.current
  val modals = LocalFluidGlassModalHostState.current
  LaunchedEffect(active, LocalConfiguration.current.orientation, density, modals) {
    if (active) {
      var hadIme = false
      var resumeIme = false
      snapshotFlow { (ime.getBottom(density) > 0) to (modals?.isOnScreen == true) }.collect { (visible, covered) ->
        // Altro nasconde temporaneamente la tastiera: la sua ancora e l'editor restano nello stesso piano.
        if (covered) {
          resumeIme = resumeIme || hadIme
        } else if (resumeIme) {
          resumeIme = false
          if (!visible) {
            focus.requestFocus()
            keyboard?.show()
          }
        } else if (hadIme && !visible) currentClose()
        hadIme = visible
      }
    }
  }
  BackHandler(enabled = active && modals?.isOnScreen != true, onBack = close)
  LaunchedEffect(active) {
    if (active) {
      focus.requestFocus()
      keyboard?.show()
    } else if (wasActive) {
      manager.clearFocus()
      keyboard?.hide()
    }
    wasActive = active
  }
  val lens = FluidMetrics.GlassButtonLens
  val targetHeight = if (pose.compact) lens else FluidTextFieldDefaults.MinHeight
  val nativePlane = LocalFluidGlassContainer.current?.renders == true
  val fallback = if (LocalFluidMotionPolicy.current.reducedMotion) snap<Float>() else ToolbarSearchTiming
  val height = if (nativePlane) targetHeight else animateFloatAsState(targetHeight.value, fallback, label = "search height").value.dp
  val x = if (nativePlane) pose.left else animateFloatAsState(pose.left, fallback, label = "search start").value
  val width = if (nativePlane) pose.width else animateFloatAsState(pose.width, fallback, label = "search width").value
  val reading = animateFloatAsState(if (pose.compact) 0f else 1f, fallback, label = "search reading veil")
  val activate = Modifier.fluidPressable(onClick = { toggle(true) }, role = Role.Button,
    focusShape = FluidCapsuleShape, feedback = FluidPressFeedback.Expand, haptic = FluidHapticEvent.Open)
  val target = if (active) Modifier.pointerInput(focus, keyboard) {
    // Anche il margine tra il vetro da 40 dp e il bersaglio da 48 dp riporta all'editor.
    detectTapGestures { focus.requestFocus(); keyboard?.show() }
  } else activate
  Box(modifier.offset(x = x.dp).width(width.dp).height(FluidMetrics.TouchTarget)
    .then(target), contentAlignment = Alignment.Center) {
    Row(Modifier.fillMaxWidth().height(height).fluidGlassId(ToolbarFieldId, namespace)
      .fluidGlassContentAnchor(FluidGlassContentAnchor.Start).fluidGlass(FluidGlass.Regular)
      .fluidSearchReadingSurface(LocalContentColor.current) { reading.value }.clip(FluidCapsuleShape), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(lens).fillMaxHeight(), contentAlignment = Alignment.Center) {
        FluidIcon(if (purposeSearch) FluidIcons.Search else FluidIcons.Edit,
          if (pose.compact) prompt else null, size = FluidSearchFieldDefaults.IconSize, tint = LocalContentColor.current)
      }
      val ink = LocalContentColor.current
      // L'editor ha un solo proprietario. Il segnaposto e le azioni possono sfocarsi indipendentemente dal cursore.
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        FluidGlassContentTransition(if (!pose.compact && spec.query.isEmpty()) prompt else null,
          contentAlignment = Alignment.CenterStart) { hint ->
          if (hint != null) Text(hint, color = ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        }
        if (active) BasicTextField(
          value = search.editor.value,
          onValueChange = { next ->
            val changed = next.text != search.editor.value.text
            search.editor.value = next
            if (changed) spec.onQueryChange(next.text)
          },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
          keyboardOptions = KeyboardOptions(imeAction = if (purposeSearch) ImeAction.Search else ImeAction.Done),
          keyboardActions = KeyboardActions(onSearch = { spec.onSearch?.invoke() ?: close() },
            onDone = { spec.onSearch?.invoke() ?: close() }),
          modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = prompt },
        )
        else if (!pose.compact && spec.query.isNotEmpty()) Text(spec.query, color = ink,
          style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      val actionSize = FluidSearchFieldDefaults.ActionSize
      val showClear = active && spec.query.isNotEmpty() && pose.width.dp >= lens + actionSize * 3
      val scopeBudget = (pose.width.dp - lens - actionSize * (if (showClear) 3 else 2)).coerceAtLeast(0.dp)
      if (active && spec.scopes != null) {
        Box(Modifier.widthIn(max = minOf((pose.width / 3f).dp, scopeBudget)).horizontalScroll(rememberScrollState())) { spec.scopes.invoke() }
      }
      if (showClear) Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
        SearchClearButton(LocalFluidLabels.current.clear) { spec.onQueryChange("") }
      }
      FluidGlassContentTransition(if (active) true else null) { show ->
        if (show == true) {
          if (active) FluidCapsuleAction(FluidIcons.Close, closeLabel, close, size = FluidSearchFieldDefaults.ActionSize, tint = ink)
          else Box(Modifier.size(FluidSearchFieldDefaults.ActionSize), contentAlignment = Alignment.Center) {
            FluidIcon(FluidIcons.Close, null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
          }
        }
      }
    }
  }
}

/** La chiave dell'elemento della lista che porta il campo. */
internal const val FluidToolbarSearchItemKey = "fluid:toolbar-search"

/** La sede principale conserva lo stesso editor durante il ritiro della testata. */
@Composable
internal fun FluidToolbarSearchSlot(
  spec: FluidSearchSpec,
  site: FluidToolbarSearchSite,
  modifier: Modifier = Modifier,
  pinned: FluidToolbarMinimizeState? = null,
  pinnedBackdrop: GlassBackdropState? = null,
) {
  if (!fluidToolbarSearchSits(spec, site)) return
  FluidToolbarPrincipalSearch(spec, modifier, pinned, pinnedBackdrop ?: currentGlassBackdrop())
}

/** Solo nell'ultimo tratto il campo è abbastanza vicino alla fascia da rifrangerla (R5 V5). */
internal const val FluidSearchPinGlassPhase = 0.86f

internal fun fluidToolbarSearchGlassProgress(progress: Float): Float =
  smoothStep(((progress - FluidSearchPinGlassPhase) / (1f - FluidSearchPinGlassPhase)).coerceIn(0f, 1f))

/** Il materiale è sotto l'editor e cambia senza ricreare la sua selezione o la sessione dell'IME. */
@Composable
private fun FluidToolbarPrincipalSearch(
  spec: FluidSearchSpec,
  modifier: Modifier,
  pinned: FluidToolbarMinimizeState?,
  backdrop: GlassBackdropState,
) {
  val scheme = MaterialTheme.colorScheme
  val toggle = requireNotNull(spec.onActiveChange)
  val active = spec.active
  val collapsible = spec.behavior == FluidSearchBehavior.CollapseToButton
  val open = !collapsible || active
  val request = remember { FocusRequester() }
  val searching = spec.purpose == FluidSearchPurpose.Search
  val prompt = spec.prompt ?: if (searching) SearchLabels.search else LocalFluidLabels.current.message
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  var wasActive by remember { mutableStateOf(active) }
  LaunchedEffect(active) {
    if (active) {
      request.requestFocus()
      keyboard?.show()
    } else if (wasActive) {
      focus.clearFocus()
      keyboard?.hide()
    }
    wasActive = active
  }
  fun close() {
    focus.clearFocus()
    keyboard?.hide()
    toggle(false)
  }
  BackHandler(enabled = active) { close() }

  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val expansion = animateFloatAsState(
    targetValue = if (open) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.standard(),
    label = "principal search expansion",
  )
  val width = if (!collapsible) Modifier.fillMaxWidth() else Modifier.layout { measurable, constraints ->
    val closed = FluidMetrics.GlassButtonLens.roundToPx()
    val full = if (constraints.hasBoundedWidth) constraints.maxWidth else FluidSearchFieldDefaults.ExpandedFallbackWidth.roundToPx()
    val px = fluidSearchWidth(closed, full, expansion.value).coerceIn(constraints.minWidth, constraints.maxWidth)
    val child = measurable.measure(constraints.copy(minWidth = px, maxWidth = px))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
  }

  val ink = rememberGlassInk()
  val glassVisible by remember(pinned) { derivedStateOf { (pinned?.progress ?: 0f) > FluidSearchPinGlassPhase } }
  Column(modifier.then(width)) {
    Box(
      Modifier.fillMaxWidth()
        .heightIn(min = if (open) FluidTextFieldDefaults.MinHeight else FluidMetrics.GlassButtonLens)
        .pointerInput(Unit) { detectTapGestures { } }
        .then(if (open) Modifier else Modifier.fluidPressable(
          onClick = { toggle(true) },
          role = Role.Button,
          focusShape = FluidCapsuleShape,
          feedback = FluidPressFeedback.Expand,
          haptic = FluidHapticEvent.Open,
        )),
    ) {
      Box(
        Modifier.matchParentSize()
          .graphicsLayer { alpha = 1f - fluidToolbarSearchGlassProgress((pinned?.progress ?: 0f)) }
          .background(scheme.surfaceContainerHighest, FluidCapsuleShape),
      )
      if (glassVisible) Box(
        Modifier.matchParentSize()
          .graphicsLayer { alpha = fluidToolbarSearchGlassProgress((pinned?.progress ?: 0f)) }
          .fluidFloatingGlass(backdrop, onPhoto = false, ink = ink),
      )
      GlassInkContent(ink, LocalContentColor.current) {
        val colors = MaterialTheme.colorScheme
        Row(
          Modifier.fillMaxWidth().heightIn(min = if (open) FluidTextFieldDefaults.MinHeight else FluidMetrics.GlassButtonLens).clip(FluidCapsuleShape),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FluidIcon(
            if (searching) FluidIcons.Search else FluidIcons.Edit,
            contentDescription = prompt.takeUnless { open },
            modifier = Modifier.padding(start = FluidSearchFieldDefaults.IconInset).size(FluidSearchFieldDefaults.IconSize),
            tint = colors.onSurfaceVariant,
          )
          if (open || expansion.value > 0.001f) {
            BasicTextField(
              value = spec.query,
              onValueChange = spec.onQueryChange,
              modifier = Modifier.weight(1f).focusRequester(request).onFocusChanged { if (it.isFocused && !active) toggle(true) }.padding(horizontal = 10.dp)
                .graphicsLayer { alpha = fluidSearchContentAlpha(expansion.value) },
              textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
              singleLine = true,
              cursorBrush = SolidColor(colors.primary),
              keyboardOptions = KeyboardOptions(imeAction = if (searching) ImeAction.Search else ImeAction.Done),
              keyboardActions = KeyboardActions(onSearch = { spec.onSearch?.invoke() ?: close() },
                onDone = { spec.onSearch?.invoke() ?: close() }),
              decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                  if (spec.query.isEmpty()) Text(
                    prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                  inner()
                }
              },
            )
            if (active) spec.scopes?.invoke()
            if (open && spec.query.isNotEmpty()) Box(Modifier.size(FluidSearchFieldDefaults.ActionSize),
              contentAlignment = Alignment.Center) { SearchClearButton(LocalFluidLabels.current.clear) { spec.onQueryChange("") } }
            if (active) FluidCapsuleAction(
              icon = FluidIcons.Close, contentDescription = SearchLabels.close, onClick = { close() },
              size = FluidSearchFieldDefaults.ActionSize,
            )
          }
        }
      }
    }
    if (active) spec.suggestions?.invoke()
  }
}

/**
 * Il campo come elemento della lista di una schermata, sotto il titolo grande: la forma di [FluidToolbarSearchSlot] per
 * [FluidToolbarSearchSite.UnderTitle]. Niente se [spec] è null o se il campo sta altrove.
 */
internal fun LazyListScope.fluidToolbarSearchItem(spec: FluidSearchSpec?) {
  if (spec == null || !fluidToolbarSearchSits(spec, FluidToolbarSearchSite.UnderTitle)) return
  item(key = FluidToolbarSearchItemKey, contentType = FluidToolbarSearchItemKey) {
    FluidToolbarSearchSlot(spec, FluidToolbarSearchSite.UnderTitle)
  }
}
