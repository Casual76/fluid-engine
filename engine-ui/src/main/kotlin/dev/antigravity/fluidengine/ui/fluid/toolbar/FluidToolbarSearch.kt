package dev.antigravity.fluidengine.ui.fluid.toolbar

import android.graphics.Rect
import android.os.Build
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.State
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
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import dev.antigravity.fluidengine.ui.fluid.smoothStep
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassQualifiedId
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailing
import dev.antigravity.fluidengine.ui.fluid.fluidSearchIcon
import dev.antigravity.fluidengine.ui.fluid.fluidSearchImeAction
import dev.antigravity.fluidengine.ui.fluid.fluidSearchSubmit
import dev.antigravity.fluidengine.ui.fluid.fluidSearchCanSend
import dev.antigravity.fluidengine.ui.fluid.SearchCounter
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowUp
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowUpward
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
// Salute (WWDC25 256): il gruppo perde il fuoco al primo movimento e il nuovo segno entra a gruppo raccolto.
internal const val ToolbarSearchInkMillis = 250

internal fun toolbarSearchInkAlpha(progress: Float, overflow: Boolean): Float {
  val millis = progress * ToolbarSearchInkMillis
  return if (overflow) smoothStep(((millis - 167f) / (ToolbarSearchInkMillis - 167f)).coerceIn(0f, 1f))
  else 1f - smoothStep((millis / 117f).coerceIn(0f, 1f))
}

internal fun toolbarSearchInkBlur(progress: Float, overflow: Boolean): Float {
  val millis = progress * ToolbarSearchInkMillis
  if (overflow) return 1f - toolbarSearchInkAlpha(progress, true)
  val remaining = 1f - (millis / 67f).coerceIn(0f, 1f)
  return 1f - remaining * remaining
}

/** Invertendo la ricerca i due strati ripercorrono lo stesso orologio a partire dal fotogramma visibile. */
@Composable
internal fun rememberToolbarSearchInk(active: Boolean): State<Float> {
  val target = if (active) 1f else 0f
  val phase = remember { Animatable(target) }
  val scale = LocalFluidMotionPolicy.current.durationScale
  LaunchedEffect(active, scale) {
    val remaining = kotlin.math.abs(target - phase.value)
    phase.animateTo(target, tween((ToolbarSearchInkMillis * remaining * scale).roundToInt(), easing = LinearEasing))
  }
  return phase.asState()
}

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
    if (!active && !spec.showsAtRest) {
      // «Trova» a riposo (F6B.6): nessun campo e nessun posto tenuto, la riga è quella della pagina.
      val margin = metrics.edgeMarginDp.coerceAtMost((width - FluidMetrics.GlassButtonLens.value).coerceAtLeast(0f) / 2)
      return ToolbarSearchPose(resolveFluidToolbar(model, width, false, metrics, bar),
        (width - margin - FluidMetrics.GlassButtonLens.value).coerceAtLeast(0f), FluidMetrics.GlassButtonLens.value,
        compact = true, hidden = true)
    }
    if (active && !atBottom && spec.expansion != FluidSearchExpansion.AboveKeyboard) {
      // Il campo che resta in cima inghiotte la riga (F6B.6, scelta di Alessio dell'11 novembre, caso (a) di F4B): i
      // comandi si sfocano e rientrano nel campo, e tornano alla chiusura con il viaggio inverso. Niente «Altro» accanto:
      // i vetri dei vicini confluiscono in quello del campo, che prende tutta la riga. Il campo che scende sopra la
      // tastiera (caso (b)) porta con sé i comandi in «Altro», come in basso (F4B.2).
      val lens = FluidMetrics.GlassButtonLens.value
      val margin = metrics.edgeMarginDp.coerceAtMost((width - lens).coerceAtLeast(0f) / 2)
      return ToolbarSearchPose(fluidToolbarSwallowed(model, width, metrics, bar), margin, (width - 2 * margin).coerceAtLeast(0f), false)
    }
    val neighbours = resolveFluidToolbar(model, width, active, metrics, bar)
    val count = neighbours.elements.count { it.item.id != FluidToolbarOverflowId && !it.item.overflow }
    val compact = !active && when (spec.behavior) {
      FluidSearchBehavior.CollapseToButton -> true
      FluidSearchBehavior.AlwaysVisible -> false
      FluidSearchBehavior.Automatic -> count > 2
    }
    // In basso, nella riga attiva tutti i comandi della stessa barra rimangono raggiungibili da Altro: il gruppo si
    // raccoglie nel tondo, come in Foto e Salute (F4B.2). In alto il campo li inghiotte (sopra).
    val others =if (active) FluidToolbarModel(model.items.map { it.copy(overflow = true) }) else model
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

/**
 * La riga in alto con il campo attivo che la inghiotte (F6B.6): tutti i comandi sono nel trabocco del modello, ma nessun
 * gruppo resta, nemmeno «Altro». Il piano fa confluire i loro vetri in quello del campo, che copre i loro posti
 * ([GlassPlaneChange.MergeIntoNeighbour][dev.antigravity.fluidengine.ui.glass.field.GlassPlaneChange]); la memoria della
 * barra ne sfoca le icone sull'orologio della ricerca ([toolbarSearchInkAlpha]) e alla chiusura le fa rinascere dal campo.
 */
internal fun fluidToolbarSwallowed(
  model: FluidToolbarModel, width: Float, metrics: FluidToolbarMetrics, bar: FluidToolbarBar,
): FluidToolbarResolved {
  val all = resolveFluidToolbar(FluidToolbarModel(model.items.map { it.copy(overflow = true) }), width, true, metrics, bar)
  return all.copy(groups = emptyList(), elements = all.elements.filter { it.inOverflow }, requiredWidthDp = 0f, fits = true)
}

/** [hidden]: il campo non c'è (F6B.6, un campo che a riposo non si mostra). */
internal data class ToolbarSearchPose(
  val neighbours: FluidToolbarResolved, val left: Float, val width: Float, val compact: Boolean,
  val hidden: Boolean = false,
) {
  fun extent(density: Float): FluidToolbarExtent {
    val rest = fluidToolbarExtent(neighbours, density)
    if (hidden) return rest
    return rest.copy(endPx = maxOf(rest.endPx, ((neighbours.widthDp - left) * density).roundToInt()))
  }
}

/**
 * Se la ricerca attiva [spec] tocca i fissati di una barra che non è la sua (piano F4B.2: a ricerca attiva i fissati in
 * fondo alla barra in alto passano nel trabocco): sì per un campo che sta in una barra o sotto il titolo; no per un campo
 * che a riposo non c'è («trova», F6B.6), che lascia la barra in alto com'è. Null senza ricerca.
 */
internal fun fluidToolbarSearchTouchesBar(spec: FluidSearchSpec?): Boolean? = spec?.let { it.active && it.showsAtRest }

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
      val pose = remember(model, maxWidth, search.bar, search.hasDeclaration, active, search.spec.behavior, search.spec.showsAtRest) {
        search.layout(model, maxWidth.value, metrics)
      }
      // Il contenuto che esce (F6B.6): «trova» che se ne va, o il campo aperto che torna lente. Esce sfocato dal fantasma.
      val leaving = remember { ToolbarSearchLeaving() }
      leaving.track(pose, search.spec, animate = !reduced)
      Box(Modifier.fillMaxWidth().offset { IntOffset(0, toolbarSearchOffset(available, rowHeight.roundToPx(), position.value)) }
        .height(rowHeight).then(if (active) Modifier else Modifier.fluidToolbarMinimizeLayer(minimize, towardsBottom = search.atBottom))) {
        FluidToolbarGroups(page, pose.neighbours, reduced, interactive = true, searchActive = active) { namespace ->
          Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            if (!pose.hidden) ToolbarSearchField(search, pose, namespace, Modifier)
            leaving.ghost?.let { ghost -> key(ghost.serial) { ToolbarSearchGhost(ghost, namespace) { leaving.finish(ghost) } } }
          }
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

/** Il tasto a destra del campo aperto: la X che chiude o «Invia» (F6B.6), sulla stessa sagoma. */
internal enum class ToolbarSearchTrailingFace { Close, Send }

/** La faccia del tasto a destra: null a campo chiuso, «Invia» solo con il preset che invia e qualcosa da inviare. */
internal fun toolbarSearchTrailingFace(spec: FluidSearchSpec): ToolbarSearchTrailingFace? = when {
  !spec.active -> null
  fluidSearchCanSend(spec) -> ToolbarSearchTrailingFace.Send
  else -> ToolbarSearchTrailingFace.Close
}

/**
 * Quali comandi stanno nel campo aperto largo [widthDp] (F6B.6): il contatore con le frecce di [FluidSearchSpec.navigation]
 * e il cerchio che svuota. Prima di tutto restano la lente, un posto per il testo e il tasto a destra; poi le frecce, poi
 * il cerchio. Il cerchio c'è solo col tasto a destra che cancella e con del testo.
 */
internal data class ToolbarSearchFieldParts(val navigation: Boolean, val clear: Boolean)

/** Il posto del contatore «12/12» a cifre tabulari, con il suo margine: a occhio (F6B.6, 21 novembre 2026). */
internal const val ToolbarSearchCounterRoomDp = 40f

internal fun toolbarSearchFieldParts(spec: FluidSearchSpec, widthDp: Float): ToolbarSearchFieldParts {
  val lens = FluidMetrics.GlassButtonLens.value
  val action = FluidSearchFieldDefaults.ActionSize.value
  // La lente, il testo (almeno un tasto di larghezza) e il tasto a destra: lo stesso conto di F4B.2 per il cerchio.
  val base = lens + action * 2
  val navigation = spec.active && spec.navigation != null && widthDp >= base + action * 2 + ToolbarSearchCounterRoomDp
  val taken = base + if (navigation) action * 2 + ToolbarSearchCounterRoomDp else 0f
  val clear = spec.active && spec.trailing == FluidSearchTrailing.Clear && spec.query.isNotEmpty() && widthDp >= taken + action
  return ToolbarSearchFieldParts(navigation, clear)
}

/** Il disco di «Invia»: a occhio (F6B.6, 21 novembre 2026), il tasto pieno d'accento dentro il posto di un comando. */
private val SearchSendDisc = 30.dp
private val SearchSendGlyph = 18.dp

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
  // Un campo che a riposo non c'è («trova») resta aperto quando la tastiera scende, e torna in fondo (WWDC25 231).
  val closesWithKeyboard by rememberUpdatedState(spec.showsAtRest)
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
        } else if (hadIme && !visible && closesWithKeyboard) currentClose()
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
  val motion = LocalFluidMotionPolicy.current
  val fallback = if (motion.reducedMotion) snap<Float>() else ToolbarSearchTiming
  val height = if (nativePlane) targetHeight else animateFloatAsState(targetHeight.value, fallback, label = "search height").value.dp
  val x = if (nativePlane) pose.left else animateFloatAsState(pose.left, fallback, label = "search start").value
  val width = if (nativePlane) pose.width else animateFloatAsState(pose.width, fallback, label = "search width").value
  val reading = animateFloatAsState(if (pose.compact) 0f else 1f, fallback, label = "search reading veil")
  // Il campo che nasce senza un tasto da cui allungarsi («trova», F6B.6) entra col contenuto sfocato e scalato, da 0,75
  // (R7 A6, chi nasce senza predecessore), mentre il piano fa nascere il suo vetro per lente.
  val born = remember { Animatable(if (spec.showsAtRest || motion.reducedMotion) GlassContentDefaults.EnterEnd else 0f) }
  LaunchedEffect(born) {
    if (born.value < GlassContentDefaults.EnterEnd) born.animateTo(GlassContentDefaults.EnterEnd,
      tween((GlassContentDefaults.DurationMs * GlassContentDefaults.EnterEnd * motion.durationScale).roundToInt(), easing = LinearEasing))
  }
  val blurAvailable = Build.VERSION.SDK_INT >= 31
  val bornLayer = if (spec.showsAtRest) Modifier else Modifier.graphicsLayer {
    val u = born.value
    alpha = GlassContentCurves.alpha(GlassContentRole.Born, u, blurAvailable)
    scaleX = GlassContentCurves.scale(GlassContentRole.Born, u)
    scaleY = scaleX
    val radius = glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(GlassContentRole.Born, u) *
      GlassContentDefaults.PeakBlur, FluidSearchFieldDefaults.IconSize.toPx()))
    renderEffect = if (blurAvailable && radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
  }
  val submit = { fluidSearchSubmit(spec, close) }
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
      val ink = LocalContentColor.current
      Row(Modifier.fillMaxSize().then(bornLayer), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(lens).fillMaxHeight(), contentAlignment = Alignment.Center) {
          FluidIcon(fluidSearchIcon(spec), if (pose.compact) prompt else null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
        }
        // L'editor ha un solo proprietario. Il segnaposto e le azioni possono sfocarsi indipendentemente dal cursore.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
          // Il segnaposto entra ed esce sfocato con l'apertura, ma lascia il posto al testo subito, come in ogni campo:
          // mai due scritte nello stesso posto (F6B.6). Svuotato il testo torna fermo, come in Apple.
          if (spec.query.isEmpty()) FluidGlassContentTransition(if (!pose.compact) prompt else null,
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
            // Con più righe il testo va a capo e scorre dentro il campo; il campo che cresce è del composer (F6B.7).
            singleLine = spec.maxLines <= 1,
            maxLines = spec.maxLines,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = ink),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = fluidSearchImeAction(spec)),
            keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }, onSend = { submit() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = prompt },
          )
          else if (!pose.compact && spec.query.isNotEmpty()) Text(spec.query, color = ink,
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val actionSize = FluidSearchFieldDefaults.ActionSize
        val parts = toolbarSearchFieldParts(spec, pose.width)
        val scopeBudget = (pose.width.dp - lens - actionSize * (if (parts.clear) 3 else 2) -
          if (parts.navigation) actionSize * 2 + ToolbarSearchCounterRoomDp.dp else 0.dp).coerceAtLeast(0.dp)
        if (active && spec.scopes != null) {
          Box(Modifier.widthIn(max = minOf((pose.width / 3f).dp, scopeBudget)).horizontalScroll(rememberScrollState())) { spec.scopes.invoke() }
        }
        if (parts.clear) Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
          SearchClearButton(LocalFluidLabels.current.clear) { spec.onQueryChange("") }
        }
        val navigation = spec.navigation
        if (parts.navigation && navigation != null) {
          // «3/12» e le frecce dentro il campo, come FluidSearchField; il contatore è un'area viva (F6B.6, «trova»).
          SearchCounter(spec.query, navigation, SearchLabels, color = ink)
          FluidCapsuleAction(FluidIcons.KeyboardArrowUp, SearchLabels.previous, navigation.onPrevious,
            enabled = navigation.total > 0, tint = ink, size = actionSize)
          FluidCapsuleAction(FluidIcons.KeyboardArrowDown, SearchLabels.next, navigation.onNext,
            enabled = navigation.total > 0, tint = ink, size = actionSize)
        }
        // Un tasto solo: la X, o «Invia» appena c'è qualcosa da inviare; le due facce si scambiano sfocate (R7).
        FluidGlassContentTransition(toolbarSearchTrailingFace(spec)) { face ->
          when (face) {
            ToolbarSearchTrailingFace.Close ->
              if (active) FluidCapsuleAction(FluidIcons.Close, closeLabel, close, size = actionSize, tint = ink)
              else Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
                FluidIcon(FluidIcons.Close, null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
              }
            ToolbarSearchTrailingFace.Send -> SearchSendButton(SearchLabels.send, if (active) submit else null)
            null -> Unit
          }
        }
      }
    }
  }
}

/** «Invia»: il disco d'accento con la freccia in su, dentro il posto di un comando. Senza [onClick] è solo la faccia. */
@Composable
private fun SearchSendButton(label: String, onClick: (() -> Unit)?) {
  val scheme = MaterialTheme.colorScheme
  val press = if (onClick == null) Modifier.clearAndSetSemantics { } else Modifier
    .fluidPressable(onClick = onClick, role = Role.Button, focusShape = FluidCapsuleShape, feedback = FluidPressFeedback.Expand)
    .semantics { contentDescription = label }
  Box(Modifier.size(FluidSearchFieldDefaults.ActionSize).then(press), contentAlignment = Alignment.Center) {
    Box(Modifier.size(SearchSendDisc).background(scheme.primary, FluidCapsuleShape), contentAlignment = Alignment.Center) {
      FluidIcon(FluidIcons.ArrowUpward, null, size = SearchSendGlyph, tint = scheme.onPrimary)
    }
  }
}

/**
 * Il contenuto del campo che se ne va (F6B.6): la sua ultima posa aperta e la sua dichiarazione. [withLens] falso quando
 * il campo resta e torna lente: la lente è ancora la sua, escono solo parola e tasti.
 */
internal class ToolbarSearchGhostState(
  val serial: Int, val pose: ToolbarSearchPose, val spec: FluidSearchSpec, val withLens: Boolean = true,
)

/**
 * Chi tiene il fantasma del contenuto del campo (F6B.6). Il piano registra il contenuto alla larghezza del layout, che
 * salta subito all'arrivo: senza fantasma ciò che esce sparirebbe in un fotogramma. Due casi:
 * - il campo che a riposo non c'è («trova») esce dalla composizione e il suo vetro svanisce per lente: tutto il
 *   contenuto esce sfocato dal fantasma;
 * - il campo aperto che si chiude in una lente: la lente resta nel campo, che si stringe, e parola, segnaposto e tasti
 *   escono sfocati dove stavano, ritagliati sul vetro che si stringe (R7, chi esce).
 * Si aggiorna in composizione, come la memoria della barra.
 */
@Stable
internal class ToolbarSearchLeaving {
  private var shown: ToolbarSearchGhostState? = null
  private var serial = 0
  var ghost by mutableStateOf<ToolbarSearchGhostState?>(null)
    private set

  fun track(pose: ToolbarSearchPose, spec: FluidSearchSpec, animate: Boolean) {
    val last = shown
    if (pose.hidden) {
      shown = null
      if (last != null && animate) ghost = ToolbarSearchGhostState(++serial, last.pose, last.spec)
      return
    }
    shown = ToolbarSearchGhostState(0, pose, spec)
    val current = ghost
    when {
      last != null && last.spec.active && !last.pose.compact && !spec.active && pose.compact ->
        ghost = if (animate) ToolbarSearchGhostState(++serial, last.pose, last.spec, withLens = false) else null
      // Riaperto mentre usciva: il campo vero riprende.
      current != null && (current.withLens || !pose.compact) -> ghost = null
    }
  }

  fun finish(done: ToolbarSearchGhostState) {
    if (ghost === done) ghost = null
  }
}

@Composable
private fun ToolbarSearchGhost(ghost: ToolbarSearchGhostState, namespace: FluidGlassNamespace, finished: () -> Unit) {
  val phase = remember { Animatable(0f) }
  val scale = LocalFluidMotionPolicy.current.durationScale
  LaunchedEffect(phase) {
    phase.animateTo(GlassContentDefaults.ExitEnd,
      tween((GlassContentDefaults.DurationMs * GlassContentDefaults.ExitEnd * scale).roundToInt(), easing = LinearEasing))
    finished()
  }
  val spec = ghost.spec
  val pose = ghost.pose
  val plane = LocalFluidGlassContainer.current
  val qualified = remember(namespace) { FluidGlassQualifiedId(namespace, ToolbarFieldId) }
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val ink = LocalContentColor.current
  val blurAvailable = Build.VERSION.SDK_INT >= 31
  val purposeSearch = spec.purpose == FluidSearchPurpose.Search
  val prompt = spec.prompt ?: if (purposeSearch) SearchLabels.search else LocalFluidLabels.current.message
  val lens = FluidMetrics.GlassButtonLens
  val actionSize = FluidSearchFieldDefaults.ActionSize
  Box(Modifier.offset(x = pose.left.dp).width(pose.width.dp).height(FluidMetrics.TouchTarget).clearAndSetSemantics { }
    .drawWithContent {
      // Il contenuto resta dentro il vetro che svanisce (la sagoma in volo del campo), come le facce della barra.
      val bounds = if (rtl) null else plane?.flightOf(qualified)?.bounds
      if (bounds == null) drawContent()
      else {
        val left = pose.left.dp.toPx()
        clipRect(left = bounds.left - left, right = bounds.right - left) { this@drawWithContent.drawContent() }
      }
    }
    .graphicsLayer {
      val u = phase.value
      alpha = GlassContentCurves.alpha(GlassContentRole.Exit, u, blurAvailable)
      scaleX = GlassContentCurves.scale(GlassContentRole.Exit, u)
      scaleY = scaleX
      val radius = glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(GlassContentRole.Exit, u) *
        GlassContentDefaults.PeakBlur, FluidSearchFieldDefaults.IconSize.toPx()))
      renderEffect = if (blurAvailable && radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
    }, contentAlignment = Alignment.CenterStart) {
    Row(Modifier.fillMaxWidth().height(FluidTextFieldDefaults.MinHeight), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(lens).fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (ghost.withLens) FluidIcon(fluidSearchIcon(spec), null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
      }
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        Text(spec.query.ifEmpty { prompt }, color = ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
          overflow = TextOverflow.Ellipsis)
      }
      val parts = toolbarSearchFieldParts(spec, pose.width)
      if (parts.clear) Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
        SearchClearButton(LocalFluidLabels.current.clear) { }
      }
      val navigation = spec.navigation
      if (parts.navigation && navigation != null) {
        SearchCounter(spec.query, navigation, SearchLabels, color = ink)
        for (icon in listOf(FluidIcons.KeyboardArrowUp, FluidIcons.KeyboardArrowDown)) {
          Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
            FluidIcon(icon, null, size = FluidSearchFieldDefaults.IconSize,
              tint = if (navigation.total > 0) ink else ink.copy(alpha = ink.alpha * FluidToolbarDefaults.DisabledAlpha))
          }
        }
      }
      when (toolbarSearchTrailingFace(spec)) {
        ToolbarSearchTrailingFace.Send -> SearchSendButton(SearchLabels.send, null)
        else -> Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
          FluidIcon(FluidIcons.Close, null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
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
            fluidSearchIcon(spec),
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
              singleLine = spec.maxLines <= 1,
              maxLines = spec.maxLines,
              cursorBrush = SolidColor(colors.primary),
              keyboardOptions = KeyboardOptions(imeAction = fluidSearchImeAction(spec)),
              keyboardActions = KeyboardActions(onSearch = { fluidSearchSubmit(spec) { close() } },
                onDone = { fluidSearchSubmit(spec) { close() } }, onSend = { fluidSearchSubmit(spec) { close() } }),
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
            if (open && spec.query.isNotEmpty() && spec.trailing == FluidSearchTrailing.Clear) Box(Modifier.size(FluidSearchFieldDefaults.ActionSize),
              contentAlignment = Alignment.Center) { SearchClearButton(LocalFluidLabels.current.clear) { spec.onQueryChange("") } }
            val navigation = spec.navigation
            if (active && navigation != null) {
              // «3/12» e le frecce (F6B.6), come nel campo della barra.
              SearchCounter(spec.query, navigation, SearchLabels)
              FluidCapsuleAction(FluidIcons.KeyboardArrowUp, SearchLabels.previous, navigation.onPrevious,
                enabled = navigation.total > 0, size = FluidSearchFieldDefaults.ActionSize)
              FluidCapsuleAction(FluidIcons.KeyboardArrowDown, SearchLabels.next, navigation.onNext,
                enabled = navigation.total > 0, size = FluidSearchFieldDefaults.ActionSize)
            }
            if (active) {
              if (fluidSearchCanSend(spec)) SearchSendButton(SearchLabels.send) { fluidSearchSubmit(spec) { close() } }
              else FluidCapsuleAction(
                icon = FluidIcons.Close, contentDescription = SearchLabels.close, onClick = { close() },
                size = FluidSearchFieldDefaults.ActionSize,
              )
            }
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
