package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.foundation.layout.windowInsetsPadding

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
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
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.unit.Density
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidComposerHalo
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerState
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSubmitGate
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailingKind
import dev.antigravity.fluidengine.ui.fluid.FluidTextFieldVerticalPadding
import dev.antigravity.fluidengine.ui.fluid.fluidSearchFieldExtraPx
import dev.antigravity.fluidengine.ui.fluid.fluidSearchGrows
import dev.antigravity.fluidengine.ui.fluid.fluidSearchSendsText
import dev.antigravity.fluidengine.ui.fluid.fluidSearchSubmittable
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionGlyphContent
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionLook
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.action.LocalFluidActionLabels
import dev.antigravity.fluidengine.ui.fluid.action.acceptsTap
import dev.antigravity.fluidengine.ui.fluid.action.fluidActionDescribe
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionPresentation
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh
import dev.antigravity.fluidengine.ui.fluid.icons.Stop
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
internal val ToolbarSearchCurve = FluidGlassAnimation.Custom(ToolbarSearchTiming)

/**
 * Vero mentre il campo che cresce cambia altezza (F6B.7): il piano lo segue senza viaggio, con
 * [FluidGlassAnimation.None] (la scheda del piano flottante, «Un pannello che cresce»; il rischio «Il composer che
 * cresce nel piano»). Lo dà la sovrapposizione della ricerca attorno alla sua barra.
 */
internal val LocalToolbarSearchFollows = compositionLocalOf { false }

/**
 * Come il piano della ricerca anima i suoi cambi: la curva da 334 ms del campo (F4B.2), oppure nessun viaggio mentre il
 * campo cresce ([LocalToolbarSearchFollows], F6B.7). Letto dove si compone il contenitore della barra.
 */
internal val ToolbarSearchAnimation: FluidGlassAnimation
  @Composable @ReadOnlyComposable get() = if (LocalToolbarSearchFollows.current) FluidGlassAnimation.None else ToolbarSearchCurve
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
  // Il campo che cresce (F6B.7) vive con la schermata, non con la sovrapposizione che lo disegna: la lista lo legge per
  // lasciargli il posto in fondo (F6B.13), anche quando il campo lo disegna l'host delle pagine.
  val growth = remember { ToolbarSearchGrowth() }
  // La pagina riconcilia anche cancellazioni e trasformazioni richieste dall'app. Un normale eco della
  // query conserva sia la selezione sia la regione di composizione che Gboard ha appena consegnato.
  if (editor.value.text != spec.query) SideEffect {
    editor.value = TextFieldValue(spec.query, editor.value.selection)
  }
  return ToolbarSearch(spec, bar, declared, editor, growth)
}

internal data class ToolbarSearch(
  val spec: FluidSearchSpec,
  val bar: FluidToolbarBar,
  val hasDeclaration: Boolean,
  val editor: MutableState<TextFieldValue> = mutableStateOf(TextFieldValue(spec.query)),
  /** Quanto il vetro del campo supera la sua riga (F6B.7); la lista lo legge per il suo margine in fondo (F6B.13). */
  val growth: ToolbarSearchGrowth = ToolbarSearchGrowth(),
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

/** Lo spazio fra il campo e la tastiera: il campo sta 4 dp sopra di lei (F4B.2). */
internal val ToolbarSearchImeGap = 4.dp

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
    val gap = with(density) { ToolbarSearchImeGap.roundToPx() }
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
  val keyboardDock = LocalFluidKeyboardDock.current?.takeIf { it.visible && bottom }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val position = animateFloatAsState(if (bottom) 1f else 0f,
    if (reduced) snap() else FluidMotion.standard(), label = "search keyboard position")
  val metrics = toolbarSearchMetrics(search)
  val rowHeight = metrics.buttonWidthDp.dp
  val appInset = LocalFluidBottomInset.current
  val bottomPadding = fluidBottomBarOffset(appInset)
  val reserved = PaddingValues(bottom = bottomPadding)
  // Il campo che cresce (F6B.7): quanto il suo vetro supera la riga (della schermata, F6B.13), e dove sta il vetro per
  // l'alone.
  val growth = search.growth
  val frame = remember { ToolbarSearchFieldFrame() }
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalFluidCanvasBackdrop provides null,
    LocalFluidGlassQuality provides null) {
    val viewport = if (keyboardDock == null) Modifier.padding(reserved).searchImeViewport()
      else Modifier.windowInsetsPadding(keyboardDock.insets).padding(bottom = FluidKeyboardSearchClearance)
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().then(viewport)) {
      val available = constraints.maxHeight
      val page = declaration ?: SearchOnly
      val model = fluidToolbarModelOf(page, null)
      val pose = remember(model, maxWidth, search.bar, search.hasDeclaration, active, search.spec.behavior, search.spec.showsAtRest) {
        search.layout(model, maxWidth.value, metrics)
      }
      // La crescita (F6B.7): il bersaglio è il contenuto naturale del campo meno la sua riga. Una riga in più mentre si
      // scrive è un cambio del contenitore che il piano segue senza viaggio; aprire e chiudere viaggiano come sempre.
      val grows = fluidSearchGrows(search.spec)
      val target = if (grows && !pose.hidden) fluidSearchFieldExtraPx(growth.contentPx, growth.rowPx, pose.compact).toFloat() else 0f
      val posed = ToolbarSearchGrowthKey(pose.compact, pose.hidden, active, pose.left, pose.width)
      val poseChanged = growth.posed != null && growth.posed != posed
      val move = toolbarSearchGrowthMove(growth.extra.targetValue, target, poseChanged, settled = growth.measures > 1)
      SideEffect { growth.posed = posed }
      val follows = move == ToolbarSearchGrowthMove.Follow || growth.following && !poseChanged
      LaunchedEffect(growth, target, posed) {
        when (move) {
          ToolbarSearchGrowthMove.Stay -> Unit
          ToolbarSearchGrowthMove.Jump -> growth.extra.snapTo(target)
          ToolbarSearchGrowthMove.Follow -> growth.follow {
            if (reduced) growth.extra.snapTo(target) else growth.extra.animateTo(target, ToolbarSearchGrowthSpring)
          }
        }
      }
      // Il contenuto che esce (F6B.6): «trova» che se ne va, o il campo aperto che torna lente. Esce sfocato dal fantasma.
      val leaving = remember { ToolbarSearchLeaving() }
      leaving.track(pose, search.spec, animate = !reduced, extraPx = growth.extra.targetValue)
      val halo = search.spec.composer?.halo?.takeUnless { pose.hidden }
      // L'alone sa se il campo è aperto (F6B.7): si anima solo allora.
      search.spec.composer?.halo?.let { aura -> SideEffect { aura.fieldActiveChanged(active) } }
      Box(Modifier.fillMaxWidth().offset { IntOffset(0, toolbarSearchOffset(available, rowHeight.roundToPx(), position.value)) }
        .height(rowHeight).then(if (active) Modifier else Modifier.fluidToolbarMinimizeLayer(minimize, towardsBottom = search.atBottom))) {
        // L'alone del composer (F6B.7): dietro al vetro e sopra il suo bordo, fuori dal piano e fuori dalla sua cattura.
        if (halo != null) ToolbarSearchHaloLayer(halo, frame, behind = true)
        CompositionLocalProvider(LocalToolbarSearchFollows provides follows) {
          FluidToolbarGroups(page, pose.neighbours, reduced, interactive = true, searchActive = active) { namespace ->
            frame.bind(LocalFluidGlassContainer.current, namespace)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
              if (!pose.hidden) ToolbarSearchField(search, pose, namespace, Modifier, growth, frame)
              leaving.ghost?.let { ghost -> key(ghost.serial) { ToolbarSearchGhost(ghost, namespace, search.occupiesBottom) { leaving.finish(ghost) } } }
            }
          }
        }
        if (halo != null) ToolbarSearchHaloLayer(halo, frame, behind = false)
      }
      if (active && search.spec.suggestions != null) {
        Box(Modifier.align(if (bottom) Alignment.BottomCenter else Alignment.TopCenter)
          .padding(horizontal = FluidMetrics.ScreenEdgeMargin)
          .padding(top = if (bottom) 0.dp else rowHeight, bottom = if (bottom) rowHeight else 0.dp)
          // Sopra (o sotto) il campo cresciuto, non sotto il suo vetro (F6B.7).
          .offset { IntOffset(0, growth.extra.value.roundToInt().let { if (bottom) -it else it }) }) {
          search.spec.suggestions.invoke()
        }
      }
    }
  }
}

// ================================================================================================================
// Il campo che cresce (F6B.7)
// ================================================================================================================

/**
 * La molla della crescita di riga in riga e del vassoio: [FluidMotion.snappy] (smorzamento critico, risposta 0,30 s),
 * la molla del cromo che non rimbalza. **A occhio** (F6B.7, 21 novembre 2026): F6B.0 non ha trovato una clip di Messaggi
 * che mostri la crescita (ricerca F6B.0 §5); il vetro la segue fotogramma per fotogramma senza viaggio.
 */
internal val ToolbarSearchGrowthSpring = FluidMotion.snappy<Float>()

/** La posa del campo per la crescita: se cambia, il cambio d'altezza fa parte del viaggio del piano (apre, chiude). */
internal data class ToolbarSearchGrowthKey(
  val compact: Boolean, val hidden: Boolean, val active: Boolean, val left: Float, val width: Float,
)

/** Che cosa fa il vetro del campo quando l'altezza del contenuto cambia (F6B.7). */
internal enum class ToolbarSearchGrowthMove {
  /** Niente da fare. */
  Stay,

  /** Al posto nuovo in un colpo: la prima misura, o un cambio che viaggia col piano (il campo che si apre o si chiude). */
  Jump,

  /** Cresce o cala con la molla, e il piano lo segue senza viaggio ([FluidGlassAnimation.None]). */
  Follow,
}

/**
 * La mossa del vetro da [from] a [to] px oltre la riga (F6B.7). Pura. La prima misura ([settled] falso) si posa senza
 * animare: un campo che compare con tre righe di bozza le ha già. Un cambio di posa ([poseChanged]: il campo si apre, si
 * chiude, cambia larghezza) viaggia col piano sulla curva della ricerca. Il resto (una riga in più o in meno mentre si
 * scrive, il vassoio che si apre o si chiude) cresce con la molla e il piano lo segue fotogramma per fotogramma.
 */
internal fun toolbarSearchGrowthMove(from: Float, to: Float, poseChanged: Boolean, settled: Boolean): ToolbarSearchGrowthMove = when {
  from == to -> ToolbarSearchGrowthMove.Stay
  !settled || poseChanged -> ToolbarSearchGrowthMove.Jump
  else -> ToolbarSearchGrowthMove.Follow
}

/**
 * Lo stato del campo che cresce (F6B.7). Il layout del campo scrive l'altezza naturale del suo contenuto ([report]); la
 * sovrapposizione ne fa il bersaglio e anima [extra], che il layout del vetro legge (senza ricomporre). Mentre [extra]
 * cambia con la molla, [following] tiene il piano senza viaggio; due fotogrammi dopo l'arrivo la curva della ricerca
 * torna per i cambi che viaggiano.
 */
@Stable
internal class ToolbarSearchGrowth {
  /** L'altezza naturale del contenuto del campo (vassoio e righe), in px. */
  var contentPx by mutableIntStateOf(0)
    private set

  /** L'altezza della riga del campo (40 dp aperto, 46 chiuso in lente), in px. */
  var rowPx by mutableIntStateOf(0)
    private set

  /** Quante misure diverse sono arrivate: la prima si posa senza crescere. */
  var measures by mutableIntStateOf(0)
    private set

  /** Il vetro oltre la riga adesso, in px. */
  val extra = Animatable(0f)

  private var followers by mutableIntStateOf(0)

  /** Vero mentre il vetro cresce o cala con la molla: il piano lo segue senza viaggio. */
  val following: Boolean get() = followers > 0

  /** L'ultima posa composta (scritta dopo la composizione). */
  internal var posed: ToolbarSearchGrowthKey? = null

  // Le copie che il layout confronta: la misura non deve **leggere** gli stati che scrive, o si iscrive a loro e si
  // rifà a ogni scrittura.
  private var seenContent = 0
  private var seenRow = 0
  private var seenMeasures = 0

  /** Dal layout: il contenuto è alto [content] px, la riga [row] px. Scrive gli stati solo se cambiano, senza leggerli. */
  fun report(content: Int, row: Int) {
    if (content == seenContent && row == seenRow) return
    seenContent = content
    seenRow = row
    seenMeasures++
    contentPx = content
    rowPx = row
    measures = seenMeasures
  }

  /** La crescita con la molla: il piano segue senza viaggio finché dura, più due fotogrammi. */
  suspend fun follow(block: suspend () -> Unit) {
    followers++
    try {
      block()
      repeat(2) { withFrameNanos { } }
    } finally {
      followers--
    }
  }
}

/**
 * Il vetro del campo che cresce (F6B.7): alto quanto la sua riga [base] più [extraPx], col bordo dalla parte della
 * tastiera fermo. Al genitore dice d'essere alto quanto la riga, così la barra, i vicini e il bersaglio da 48 dp non si
 * spostano; il vetro sborda verso l'alto ([upward]) o verso il basso. Legge [extraPx] nel layout: la crescita non
 * ricompone.
 */
internal fun Modifier.toolbarSearchGrowth(base: Dp, extraPx: () -> Float, upward: Boolean): Modifier = layout { measurable, constraints ->
  val basePx = base.roundToPx()
  val place = toolbarSearchGrowthPlace(basePx, extraPx().roundToInt(), upward)
  val placeable = measurable.measure(constraints.copy(minHeight = place.height, maxHeight = place.height))
  layout(placeable.width, constraints.constrainHeight(basePx)) { placeable.place(0, place.top) }
}

/** Il vetro del campo che cresce rispetto alla sua riga, in px (F6B.7): [top] dal bordo alto della riga, alto [height]. */
internal data class ToolbarSearchGrowthPlace(val top: Int, val height: Int) {
  val bottom: Int get() = top + height
}

/**
 * Dove sta il vetro che cresce (F6B.7). Pura. Alto la riga ([basePx]) più [extraPx]; in basso ([upward]) il bordo basso
 * resta quello della riga e sale quello alto, in alto il contrario.
 */
internal fun toolbarSearchGrowthPlace(basePx: Int, extraPx: Int, upward: Boolean): ToolbarSearchGrowthPlace {
  val extra = extraPx.coerceAtLeast(0)
  return ToolbarSearchGrowthPlace(if (upward) -extra else 0, basePx + extra)
}

// ================================================================================================================
// La lista sotto il campo cresciuto (F6B.13)
// ================================================================================================================

/** Le misure della riga del campo: quelle della barra in basso se ci sta, se no quelle della barra in alto. */
internal fun toolbarSearchMetrics(search: ToolbarSearch): FluidToolbarMetrics =
  if (search.atBottom) fluidBottomBarMetrics() else fluidToolbarMetrics()

/**
 * Dove sta il fondo della riga del campo che occupa il fondo, in px dal fondo della finestra (F6B.13). Pura. È la posa
 * di [FluidToolbarSearchOverlay]: a riposo sopra la barra di sistema ([navigationPx]) col margine della barra in basso
 * ([restPx]); con la tastiera su, [gapPx] sopra la sua quota ([keyboardPx], la quota di F6B.9, che comprende la barra di
 * sistema); con la barra sopra la tastiera in vista (F6B.9) sopra di lei, [dockClearancePx] oltre la quota.
 */
internal fun toolbarSearchRowBottomPx(
  navigationPx: Int, restPx: Int, keyboardPx: Int, gapPx: Int, dockClearancePx: Int? = null,
): Int = if (dockClearancePx != null) {
  maxOf(navigationPx, keyboardPx) + dockClearancePx
} else {
  maxOf(navigationPx + restPx, if (keyboardPx > 0) keyboardPx + gapPx else 0)
}

/**
 * Quanto Android ha spostato in su la finestra (`adjustPan`), in px (F6B.13). Pura. [windowTopPx] è il bordo alto della
 * finestra sullo schermo, [rootTopPx] quello della vista radice: spostata, la radice sta più in alto della finestra.
 */
internal fun toolbarSearchWindowPanPx(windowTopPx: Int, rootTopPx: Int): Int = (windowTopPx - rootTopPx).coerceAtLeast(0)

/**
 * Quanto la lista aggiunge in fondo per il campo che occupa il fondo (F6B.13), in px. Pura. [fieldTopPx] è il bordo alto
 * del vetro del campo dal fondo della finestra (la riga, cresciuta e alzata dalla tastiera); [assumedPx] è lo spazio che
 * la lista gli lascia già, cioè la riga a riposo. Mai meno di zero: il margine di oggi resta il minimo. Un campo a riposo
 * che si ritira con la barra ([hidden] da 0 a 1) porta via con sé anche questo spazio.
 */
internal fun toolbarSearchListExtraPx(fieldTopPx: Float, assumedPx: Float, hidden: Float = 0f): Float =
  (fieldTopPx - assumedPx).coerceAtLeast(0f) * (1f - hidden.coerceIn(0f, 1f))

/**
 * Lo spazio che il campo in fondo chiede alla lista della sua schermata (F6B.13). Il campo «Messaggio» cresce fino a
 * cinque righe (F6B.7) e sale con la tastiera (F6B.9): senza questo, a fine scorrimento le ultime righe della chat
 * restavano sotto il suo vetro, e con la tastiera su anche sotto la tastiera. La lista lo legge **mentre si misura**
 * ([extraPx]): la crescita e la tastiera, che cambiano a ogni fotogramma, rimisurano la lista e non la ricompongono.
 */
@Stable
internal class ToolbarSearchListRoom(
  private val keyboard: WindowInsets,
  private val navigation: WindowInsets,
) {
  // Il campo, scritto dopo ogni composizione con valori semplici: una composizione che non cambia niente non rimisura.
  /** Il campo occupa il fondo (sta in basso, o è aperto sopra la tastiera). */
  internal var bottom by mutableStateOf(false)

  /** La riga del campo è quella della barra in basso (altrimenti quella in alto, aperta sopra la tastiera). */
  internal var atBottom by mutableStateOf(false)

  /** Il vetro oltre la riga (F6B.7). */
  internal var growth by mutableStateOf<ToolbarSearchGrowth?>(null)

  /** Vero se il campo è aperto: allora non si ritira con la barra. */
  internal var active by mutableStateOf(false)

  /** La quota della barra sopra la tastiera, quando la barra è in vista e il campo le sta sopra (F6B.9). */
  internal var dock by mutableStateOf<WindowInsets?>(null)

  /** Il margine della barra in basso sopra la barra di sistema (`fluidBottomBarOffset`). */
  internal var rest by mutableStateOf(0.dp)

  /**
   * Gli inset che un genitore della schermata ha già consumato (per esempio `Modifier.imePadding()` attorno a
   * `FluidScreen`, come chiede l'editor di testo ricco): la schermata sta già sopra quella parte di tastiera, e la lista non
   * la conta due volte.
   */
  internal var consumed by mutableStateOf(WindowInsets(0, 0, 0, 0))

  /**
   * Quanto Android ha spostato in su la finestra per mostrare il cursore, in px (`adjustPan`, il modo di serie di
   * un'Activity che non lo dichiara): la schermata sta già più in alto di tanto, e la tastiera la copre di tanto meno. Il
   * campo lo sa già (si posa con le coordinate dello schermo, `searchImeViewport`); la lista lo toglie dalla quota.
   */
  internal var panPx by mutableIntStateOf(0)

  /** I px da aggiungere in fondo alla lista oltre [assumedPx], lo spazio che la lista lascia già alla riga del campo. */
  fun extraPx(density: Density, assumedPx: Float, hidden: Float): Float {
    if (!bottom) return 0f
    val dockInsets = dock
    val row = toolbarSearchRowBottomPx(
      navigationPx = navigation.getBottom(density),
      restPx = with(density) { rest.roundToPx() },
      // La barra sopra la tastiera si posa con gli inset (come la lista): lo spostamento della finestra vale solo per il
      // campo che si posa con le coordinate dello schermo.
      keyboardPx = ((dockInsets ?: keyboard).getBottom(density) - consumed.getBottom(density) -
        (if (dockInsets == null) panPx else 0)).coerceAtLeast(0),
      gapPx = with(density) { ToolbarSearchImeGap.roundToPx() },
      dockClearancePx = dockInsets?.let { with(density) { FluidKeyboardSearchClearance.roundToPx() } },
    )
    val metrics = if (atBottom) fluidBottomBarMetrics() else fluidToolbarMetrics()
    val rowHeight = with(density) { metrics.buttonWidthDp.dp.toPx() }
    val top = row + rowHeight + (growth?.extra?.value ?: 0f).coerceAtLeast(0f)
    return toolbarSearchListExtraPx(top, assumedPx, hidden)
  }
}

/**
 * Chi sta sotto riceve gli inset che i genitori hanno già consumato (F6B.13): la lista della schermata li toglie dalla
 * quota della tastiera. È `onConsumedWindowInsetsChanged` di Compose, sperimentale: chiusa qui, in un punto solo.
 */
@OptIn(ExperimentalLayoutApi::class)
internal fun Modifier.fluidConsumedInsets(onChange: (WindowInsets) -> Unit): Modifier =
  onConsumedWindowInsetsChanged(onChange)

/**
 * Lo spazio del campo in fondo per la lista di una schermata (F6B.13). [dock] è la barra sopra la tastiera della
 * schermata, se c'è: quando è in vista il campo le sta sopra, come lo posa [FluidToolbarSearchOverlay].
 */
@Composable
internal fun rememberToolbarSearchListRoom(search: ToolbarSearch, dock: FluidKeyboardDock?): ToolbarSearchListRoom {
  val keyboard = rememberFluidKeyboardQuota()
  val navigation = WindowInsets.navigationBars
  val room = remember(keyboard, navigation) { ToolbarSearchListRoom(keyboard, navigation) }
  // Lo spostamento della finestra (adjustPan), letto prima di ogni disegno finché la tastiera è su, come fa il campo.
  val view = LocalView.current
  val density = LocalDensity.current
  val showing = WindowInsets.ime.getBottom(density) > 0
  if (showing) DisposableEffect(view, room) {
    val location = IntArray(2)
    val windowTop = if (Build.VERSION.SDK_INT >= 30) {
      view.context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds?.top
    } else null
    fun update() {
      if (windowTop == null) return
      view.rootView.getLocationOnScreen(location)
      val pan = toolbarSearchWindowPanPx(windowTop, location[1])
      if (room.panPx != pan) room.panPx = pan
    }
    update()
    val observer = view.viewTreeObserver
    val listener = ViewTreeObserver.OnPreDrawListener { update(); true }
    observer.addOnPreDrawListener(listener)
    onDispose {
      if (observer.isAlive) observer.removeOnPreDrawListener(listener)
      room.panPx = 0
    }
  }
  val rest = fluidBottomBarOffset(LocalFluidBottomInset.current)
  val dockInsets = dock?.takeIf { it.visible && search.occupiesBottom }?.insets
  val bottom = search.occupiesBottom
  val atBottom = search.atBottom
  val active = search.spec.active
  SideEffect {
    room.bottom = bottom
    room.atBottom = atBottom
    room.growth = search.growth
    room.active = active
    room.dock = dockInsets
    room.rest = rest
  }
  return room
}

/** La riga della voce prende il posto del testo (F6B.7): l'app la dà, e il campo non è chiuso in una lente. Pura. */
internal fun toolbarSearchShowsVoiceLine(spec: FluidSearchSpec, pose: ToolbarSearchPose): Boolean =
  spec.composer?.voiceLine != null && !pose.compact && !pose.hidden

/**
 * Il contenuto del campo che cresce alla sua altezza naturale (F6B.7), ancorato al bordo dalla parte della tastiera e
 * ritagliato dal vetro: mentre il vetro cresce, la riga di sopra (o il vassoio) entra dal bordo che sale. Dice a [report]
 * l'altezza naturale.
 */
internal fun Modifier.toolbarSearchNatural(upward: Boolean, report: (Int) -> Unit): Modifier = layout { measurable, constraints ->
  val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
  report(placeable.height)
  val width = constraints.constrainWidth(placeable.width)
  val height = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
  layout(width, height) { placeable.place(0, if (upward) height - placeable.height else 0) }
}

/**
 * Dove sta il vetro del campo, per l'alone (F6B.7): in viaggio la sagoma del piano, ferma quella del layout, nelle
 * coordinate della riga. [version] cambia a ogni posa del vetro, così il disegno dell'alone si rifà anche quando il
 * campo cresce senza viaggio.
 */
@Stable
internal class ToolbarSearchFieldFrame {
  private var plane: FluidGlassContainerState? = null
  private var qualified: FluidGlassQualifiedId? = null
  private var namespace: FluidGlassNamespace? = null
  internal var row: LayoutCoordinates? = null
  private var glass: LayoutCoordinates? = null

  /** Il raggio del vetro fermo, in px: quello della capsula o dei 20 dp del campo su più righe. */
  internal var radiusPx = Float.POSITIVE_INFINITY

  private val placements = mutableIntStateOf(0)
  private var placed = 0

  /** Cambia a ogni posa del vetro: il disegno dell'alone lo legge e si rifà. */
  val version: Int get() = placements.intValue

  fun bind(plane: FluidGlassContainerState?, namespace: FluidGlassNamespace) {
    this.plane = plane
    if (this.namespace !== namespace) {
      this.namespace = namespace
      qualified = FluidGlassQualifiedId(namespace, ToolbarFieldId)
    }
  }

  /**
   * Il vetro è stato posato. Solo **scrittura**: la posa è osservata, e leggere qui lo stato che si scrive (`version++`)
   * la iscriverebbe a lui, rifacendo la posa a ogni scrittura senza fine (trovato da `FluidAssistantFieldHaloTest`).
   */
  fun placed(coordinates: LayoutCoordinates) {
    glass = coordinates
    placements.intValue = ++placed
  }

  /** La sagoma del vetro adesso, in px della riga; null se non c'è ancora. */
  fun shape(): RoundRect? {
    version
    val flying = qualified?.let { plane?.flightOf(it) }
    val bounds = flying?.bounds ?: run {
      val row = row?.takeIf { it.isAttached } ?: return null
      val glass = glass?.takeIf { it.isAttached } ?: return null
      row.localBoundingBoxOf(glass, clipBounds = false)
    }
    val radius = minOf(bounds.height / 2f, bounds.width / 2f, radiusPx)
    return RoundRect(bounds, CornerRadius(radius))
  }
}

/** Lo strato dell'alone, dietro o sopra il piano della barra: un suo strato grafico, che si ridisegna da solo. */
@Composable
private fun BoxScope.ToolbarSearchHaloLayer(halo: FluidComposerHalo, frame: ToolbarSearchFieldFrame, behind: Boolean) {
  Spacer(Modifier.matchParentSize().then(if (behind) Modifier.onPlaced { frame.row = it } else Modifier)
    .graphicsLayer().drawBehind {
      val glass = frame.shape() ?: return@drawBehind
      with(halo) { if (behind) drawBehindGlass(glass) else drawOverGlass(glass) }
    })
}

/** Il velo protegge l'inchiostro anche nel tratto colorato che resta più chiaro o scuro della media della sonda. */
internal fun fluidSearchReadingVeil(ink: Color): Color =
  if (ink.luminance() > .5f) Color.Black.copy(alpha = .40f) else Color.White.copy(alpha = .16f)

internal fun Modifier.fluidSearchReadingSurface(ink: Color, progress: () -> Float): Modifier =
  fluidSearchReadingSurface(ink, null, progress)

/** Il velo di lettura con il raggio [radius] (F6B.7, il campo su più righe); null: la capsula. */
internal fun Modifier.fluidSearchReadingSurface(ink: Color, radius: Dp?, progress: () -> Float): Modifier {
  val veil = fluidSearchReadingVeil(ink)
  return drawBehind {
    val amount = progress().coerceIn(0f, 1f)
    val corner = if (radius == null) size.height / 2f else minOf(radius.toPx(), size.height / 2f)
    if (amount > 0f) drawRoundRect(veil.copy(alpha = veil.alpha * amount), cornerRadius = CornerRadius(corner))
  }
}

private object ToolbarFieldId

/** Il tasto a destra del campo aperto: la X che chiude o «Invia» (F6B.6), sulla stessa sagoma. */
internal enum class ToolbarSearchTrailingFace { Close, Send }

/** La faccia del tasto a destra di F6B.6: null a campo chiuso o con le facce del composer ([toolbarSearchKeyFace]). */
internal fun toolbarSearchTrailingFace(spec: FluidSearchSpec): ToolbarSearchTrailingFace? = when (toolbarSearchKeyFace(spec)) {
  ToolbarSearchKeyFace.Close -> ToolbarSearchTrailingFace.Close
  ToolbarSearchKeyFace.Send -> ToolbarSearchTrailingFace.Send
  ToolbarSearchKeyFace.Voice, ToolbarSearchKeyFace.Stop, null -> null
}

/**
 * Tutte le facce del tasto a destra, sulla stessa sagoma (F6B.7): la X che chiude o «Invia» (F6B.6), il microfono e lo
 * stop del composer. Si scambiano sfocate (R7): mai due tasti. Un tipo a sé, accanto a [ToolbarSearchTrailingFace].
 */
internal enum class ToolbarSearchKeyFace { Close, Send, Voice, Stop }

/**
 * La faccia del tasto a destra (F6B.6, F6B.7):
 * - la ricerca e «Invia»: nessuna a campo chiuso; aperto la X, o «Invia» con qualcosa da inviare;
 * - il microfono del composer: il microfono a campo vuoto, «Invia» con del testo, anche a riposo;
 * - lo stop: lo stop, anche a riposo.
 */
internal fun toolbarSearchKeyFace(spec: FluidSearchSpec): ToolbarSearchKeyFace? = when (spec.trailing.kind) {
  FluidSearchTrailingKind.Voice -> if (fluidSearchCanSend(spec)) ToolbarSearchKeyFace.Send else ToolbarSearchKeyFace.Voice
  FluidSearchTrailingKind.Stop -> ToolbarSearchKeyFace.Stop
  FluidSearchTrailingKind.Clear, FluidSearchTrailingKind.Send -> when {
    !spec.active -> null
    fluidSearchCanSend(spec) -> ToolbarSearchKeyFace.Send
    else -> ToolbarSearchKeyFace.Close
  }
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
  val clear = spec.active && spec.trailing.kind == FluidSearchTrailingKind.Clear && spec.query.isNotEmpty() && widthDp >= taken + action
  return ToolbarSearchFieldParts(navigation, clear)
}

/** Il disco di «Invia»: a occhio (F6B.6, 21 novembre 2026), il tasto pieno d'accento dentro il posto di un comando. */
private val SearchSendDisc = 30.dp
private val SearchSendGlyph = 18.dp

@Composable
private fun ToolbarSearchField(
  search: ToolbarSearch, pose: ToolbarSearchPose, namespace: FluidGlassNamespace, modifier: Modifier,
  growth: ToolbarSearchGrowth, frame: ToolbarSearchFieldFrame,
) {
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
  // Niente doppio invio (F6B.7): lo stesso testo non riparte subito, e il tasto che cambia faccia non risponde.
  val gate = remember { FluidSearchSubmitGate() }
  val submit = {
    if (fluidSearchSubmittable(spec) && (!fluidSearchSendsText(spec) || gate.trySubmit(spec.query))) fluidSearchSubmit(spec, close)
  }
  val activate = Modifier.fluidPressable(onClick = { toggle(true) }, role = Role.Button,
    focusShape = FluidCapsuleShape, feedback = FluidPressFeedback.Expand, haptic = FluidHapticEvent.Open)
  val target = if (active) Modifier.pointerInput(focus, keyboard) {
    // Anche il margine tra il vetro da 40 dp e il bersaglio da 48 dp riporta all'editor.
    detectTapGestures { focus.requestFocus(); keyboard?.show() }
  } else activate
  val face = toolbarSearchKeyFace(spec).takeUnless { pose.compact }
  val editor: @Composable (Modifier, Boolean) -> Unit = { editorModifier, singleLine ->
    BasicTextField(
      value = search.editor.value,
      onValueChange = { next ->
        val changed = next.text != search.editor.value.text
        search.editor.value = next
        if (changed) spec.onQueryChange(next.text)
      },
      // Con più righe il testo va a capo e il campo cresce fino a `maxLines` righe, poi scorre dentro (F6B.7).
      singleLine = singleLine,
      maxLines = spec.maxLines,
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = LocalContentColor.current),
      cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
      keyboardOptions = KeyboardOptions(imeAction = fluidSearchImeAction(spec)),
      keyboardActions = KeyboardActions(onSearch = { submit() }, onDone = { submit() }, onSend = { submit() }),
      modifier = editorModifier.fillMaxWidth().focusRequester(focus).semantics { contentDescription = prompt },
    )
  }
  Box(modifier.offset(x = x.dp).width(width.dp).height(FluidMetrics.TouchTarget)
    .then(target), contentAlignment = Alignment.Center) {
    if (!fluidSearchGrows(spec)) {
      // Il campo di una riga: quello di F6B.6, al pixel.
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
            if (active) editor(Modifier, spec.maxLines <= 1)
            else if (!pose.compact && spec.query.isNotEmpty()) Text(spec.query, color = ink,
              style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          ToolbarSearchFieldTrailing(spec, pose, face, active, ink, closeLabel, close, submit, gate) { it() }
        }
      }
    } else {
      // Il campo che cresce (F6B.7): il vetro sale dal bordo opposto alla tastiera, il contenuto sta alla sua altezza
      // naturale ancorato a quel bordo, e il piano segue il vetro senza viaggio mentre cresce.
      val upward = search.occupiesBottom
      val shape = if (pose.compact) FluidCapsuleShape else ToolbarSearchMultilineShape
      frame.radiusPx = with(density) { if (pose.compact) Float.POSITIVE_INFINITY else FluidTextFieldDefaults.MultilineRadius.toPx() }
      val composer = spec.composer
      Box(Modifier.fillMaxWidth().toolbarSearchGrowth(height, { growth.extra.value }, upward)
        .onPlaced(frame::placed)
        .toolbarSearchOverflowTaps(upward, { growth.extra.value }, height) { if (active) { focus.requestFocus(); keyboard?.show() } else toggle(true) }
        .fluidGlassId(ToolbarFieldId, namespace)
        .fluidGlassContentAnchor(FluidGlassContentAnchor.Start).fluidGlass(FluidGlass.Regular, shape)
        .fluidSearchReadingSurface(LocalContentColor.current, if (pose.compact) null else FluidTextFieldDefaults.MultilineRadius) { reading.value }
        .clip(shape)) {
        val ink = LocalContentColor.current
        val rowPx = with(density) { height.roundToPx() }
        Column(Modifier.fillMaxWidth().then(bornLayer).toolbarSearchNatural(upward) { growth.report(it, rowPx) }) {
          // Il vassoio degli allegati, sopra il testo (F6B.7): quando l'app lo toglie il suo contenuto esce sfocato, poi
          // il vetro si richiude dal bordo di sopra.
          val tray = rememberLastSlot(composer?.attachments.takeUnless { pose.compact })
          FluidGlassContentTransition(tray.shown, contentAlignment = Alignment.BottomStart) { shown ->
            if (shown) Box(Modifier.fillMaxWidth().padding(start = ToolbarSearchTrayInset, end = ToolbarSearchTrayInset,
              top = ToolbarSearchTrayInset)) { tray.last() }
          }
          Row(Modifier.fillMaxWidth().heightIn(min = height), verticalAlignment = Alignment.Bottom) {
            // Lo slot a sinistra (il «+»), accanto all'ultima riga; chiuso in lente, l'icona del preset.
            Box(Modifier.width(lens).height(height), contentAlignment = Alignment.Center) {
              val leading = rememberLastSlot(composer?.leading.takeUnless { pose.compact })
              FluidGlassContentTransition(leading.shown) { shown ->
                if (shown) leading.last()
                else FluidIcon(fluidSearchIcon(spec), if (pose.compact) prompt else null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
              }
            }
            Box(Modifier.weight(1f).heightIn(min = height)) {
              val voice = rememberLastSlot(composer?.voiceLine.takeIf { toolbarSearchShowsVoiceLine(spec, pose) })
              Box(Modifier.fillMaxWidth().padding(vertical = FluidTextFieldVerticalPadding)
                .toolbarSearchVoiceSwap(voice.shown), contentAlignment = Alignment.CenterStart) {
                if (spec.query.isEmpty()) FluidGlassContentTransition(if (!pose.compact) prompt else null,
                  contentAlignment = Alignment.CenterStart) { hint ->
                  if (hint != null) Text(hint, color = ink, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                }
                if (active) editor(Modifier, spec.maxLines <= 1)
                else if (!pose.compact && spec.query.isNotEmpty()) Text(spec.query, color = ink,
                  style = MaterialTheme.typography.bodyLarge, maxLines = spec.maxLines, overflow = TextOverflow.Ellipsis)
              }
              // La riga della voce al posto del testo, mentre si ascolta (F6B.7): entra ed esce sfocata (R7).
              FluidGlassContentTransition(voice.shown, modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterStart) { shown -> if (shown) voice.last() }
            }
            ToolbarSearchFieldTrailing(spec, pose, face, active, ink, closeLabel, close, submit, gate) { item ->
              Box(Modifier.height(height), contentAlignment = Alignment.Center) { item() }
            }
          }
        }
      }
    }
  }
}

/**
 * Uno slot dell'app che c'è o non c'è (F6B.7): [shown] dice se c'è, [last] è l'ultimo contenuto dato, che resta per lo
 * strato che esce sfocato quando l'app lo toglie.
 */
@Stable
private class ToolbarSearchSlot {
  var shown by mutableStateOf(false)
  var content by mutableStateOf<(@Composable () -> Unit)?>(null)
  val last: @Composable () -> Unit = { content?.invoke() }
}

@Composable
private fun rememberLastSlot(slot: (@Composable () -> Unit)?): ToolbarSearchSlot {
  val holder = remember { ToolbarSearchSlot() }
  holder.shown = slot != null
  if (slot != null) holder.content = slot
  return holder
}

/** La sagoma del campo su più righe (F6B.7): i 20 dp della capsula a una riga, che restano mentre cresce. */
private val ToolbarSearchMultilineShape = ContinuousCornerShape(FluidTextFieldDefaults.MultilineRadius)

/** Il margine del vassoio degli allegati dentro il vetro: a occhio (F6B.7, 21 novembre 2026), un quarto di riga. */
private val ToolbarSearchTrayInset = 6.dp

/**
 * I comandi a destra del campo (F6B.6, F6B.7): gli ambiti, il cerchio che svuota, «3/12» con le frecce e il tasto a
 * destra a stati. [slot] posa ogni comando: nel campo che cresce stanno in fondo, accanto all'ultima riga.
 */
@Composable
private fun RowScope.ToolbarSearchFieldTrailing(
  spec: FluidSearchSpec, pose: ToolbarSearchPose, face: ToolbarSearchKeyFace?, active: Boolean, ink: Color,
  closeLabel: String, close: () -> Unit, submit: () -> Unit, gate: FluidSearchSubmitGate,
  slot: @Composable (@Composable () -> Unit) -> Unit,
) {
  val lens = FluidMetrics.GlassButtonLens
  val actionSize = FluidSearchFieldDefaults.ActionSize
  val parts = toolbarSearchFieldParts(spec, pose.width)
  val scopeBudget = (pose.width.dp - lens - actionSize * (if (parts.clear) 3 else 2) -
    if (parts.navigation) actionSize * 2 + ToolbarSearchCounterRoomDp.dp else 0.dp).coerceAtLeast(0.dp)
  if (active && spec.scopes != null) slot {
    Box(Modifier.widthIn(max = minOf((pose.width / 3f).dp, scopeBudget)).horizontalScroll(rememberScrollState())) { spec.scopes.invoke() }
  }
  if (parts.clear) slot {
    Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
      SearchClearButton(LocalFluidLabels.current.clear) { spec.onQueryChange("") }
    }
  }
  val navigation = spec.navigation
  if (parts.navigation && navigation != null) slot {
    // «3/12» e le frecce dentro il campo, come FluidSearchField; il contatore è un'area viva (F6B.6, «trova»).
    Row(verticalAlignment = Alignment.CenterVertically) {
      SearchCounter(spec.query, navigation, SearchLabels, color = ink)
      FluidCapsuleAction(FluidIcons.KeyboardArrowUp, SearchLabels.previous, navigation.onPrevious,
        enabled = navigation.total > 0, tint = ink, size = actionSize)
      FluidCapsuleAction(FluidIcons.KeyboardArrowDown, SearchLabels.next, navigation.onNext,
        enabled = navigation.total > 0, tint = ink, size = actionSize)
    }
  }
  // Un tasto solo: la X, «Invia», il microfono o lo stop; le facce si scambiano sfocate sulla stessa sagoma (R7).
  slot {
    FluidGlassContentTransition(face) { shown ->
      ToolbarSearchTrailingKey(spec, shown, active, ink, closeLabel, close, submit, gate)
    }
  }
}

/** Una faccia del tasto a destra (F6B.6, F6B.7). Il microfono e lo stop non rispondono subito dopo un invio. */
@Composable
private fun ToolbarSearchTrailingKey(
  spec: FluidSearchSpec, face: ToolbarSearchKeyFace?, active: Boolean, ink: Color, closeLabel: String,
  close: () -> Unit, submit: () -> Unit, gate: FluidSearchSubmitGate,
) {
  val actionSize = FluidSearchFieldDefaults.ActionSize
  val trailing = spec.trailing
  when (face) {
    ToolbarSearchKeyFace.Close ->
      if (active) FluidCapsuleAction(FluidIcons.Close, closeLabel, close, size = actionSize, tint = ink)
      else Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
        FluidIcon(FluidIcons.Close, null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
      }
    // Il microfono del composer invia anche a riposo, con una bozza; «Invia» della ricerca solo a campo aperto.
    ToolbarSearchKeyFace.Send -> SearchSendButton(SearchLabels.send,
      if (active || trailing.kind == FluidSearchTrailingKind.Voice) submit else null,
      sending = trailing.sending, stateful = trailing != FluidSearchTrailing.Send)
    ToolbarSearchKeyFace.Voice -> FluidCapsuleAction(FluidIcons.Mic, SearchLabels.dictate,
      { if (gate.tapAllowed()) trailing.onVoice?.invoke() }, size = actionSize, tint = ink)
    ToolbarSearchKeyFace.Stop -> FluidCapsuleAction(trailing.stopIcon ?: FluidIcons.Filled.Stop,
      trailing.stopLabel ?: SearchLabels.stop, { if (gate.tapAllowed()) trailing.onStop?.invoke() }, size = actionSize, tint = ink)
    null -> Unit
  }
}

/**
 * I tocchi sulla parte del vetro che sborda dalla riga (F6B.7): il genitore ne conosce solo la riga, quindi un tocco sopra
 * (o sotto) di lei, fuori dall'editor e dai tasti, apre il campo o rimette il cursore. Dentro la riga il tocco resta della
 * scatola del campo, con la sua pressione.
 */
@Composable
private fun Modifier.toolbarSearchOverflowTaps(upward: Boolean, extraPx: () -> Float, base: Dp, onTap: () -> Unit): Modifier {
  val tap by rememberUpdatedState(onTap)
  return pointerInput(upward, base) {
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = true)
      val extra = extraPx()
      val inOverflow = if (upward) down.position.y < extra else down.position.y > base.toPx()
      if (!inOverflow) return@awaitEachGesture
      down.consume()
      val up = waitForUpOrCancellation() ?: return@awaitEachGesture
      up.consume()
      tap()
    }
  }
}

/**
 * Il testo che lascia il posto alla riga della voce e torna (F6B.7): lo scambio sfocato di R7 sullo strato del testo,
 * con le curve del contenuto ([GlassContentCurves]). L'editor resta nella composizione, col suo fuoco. La prima posa non
 * anima; con il movimento ridotto il cambio è di colpo.
 */
@Composable
private fun Modifier.toolbarSearchVoiceSwap(voice: Boolean): Modifier {
  val policy = LocalFluidMotionPolicy.current
  val phase = remember { Animatable(if (voice) GlassContentDefaults.ExitEnd else GlassContentDefaults.EnterEnd) }
  val first = remember { booleanArrayOf(true) }
  LaunchedEffect(voice, policy.reducedMotion) {
    val end = if (voice) GlassContentDefaults.ExitEnd else GlassContentDefaults.EnterEnd
    if (first[0] || policy.reducedMotion) {
      first[0] = false
      phase.snapTo(end)
      return@LaunchedEffect
    }
    phase.snapTo(0f)
    phase.animateTo(end, tween((GlassContentDefaults.DurationMs * end * policy.durationScale).roundToInt(), easing = LinearEasing))
  }
  val blurAvailable = Build.VERSION.SDK_INT >= 31
  return graphicsLayer {
    val u = phase.value
    val role = if (voice) GlassContentRole.Exit else GlassContentRole.Enter
    alpha = if (voice && u >= GlassContentDefaults.ExitEnd) 0f else GlassContentCurves.alpha(role, u, blurAvailable)
    scaleX = GlassContentCurves.scale(role, u)
    scaleY = scaleX
    val radius = glassContentBlurRadiusPx(glassContentSigmaPx(GlassContentCurves.blur(role, u) *
      GlassContentDefaults.PeakBlur, GlassContentDefaults.BlurReference.toPx()))
    renderEffect = if (blurAvailable && radius > 0.05f) BlurEffect(radius, radius, TileMode.Decal) else null
  }
}

/** Le parole di «Invia» con lo stato dell'invio (F6B.3a): quelle del preset Invia dell'app. */
@Composable
private fun rememberSearchSendPreset(): FluidActionPreset {
  val words = LocalFluidActionLabels.current.send
  return remember(words) {
    FluidActionPreset.custom(idle = FluidIcons.ArrowUpward, success = FluidIcons.Check, failure = FluidIcons.Refresh, words = words)
  }
}

/**
 * «Invia»: il disco d'accento con la freccia in su, dentro il posto di un comando. Senza [onClick] è solo la faccia.
 *
 * Con [stateful] (F6B.7) la freccia è il glifo di un tasto con esito ([sending], F6B.3a): mentre lavora l'anello gira
 * attorno alla freccia e il tasto è spento (niente doppio invio), poi la spunta o il fallimento; TalkBack lo dice.
 */
@Composable
private fun SearchSendButton(
  label: String, onClick: (() -> Unit)?, sending: FluidActionState = FluidActionState.Idle, stateful: Boolean = false,
) {
  val scheme = MaterialTheme.colorScheme
  val preset = if (stateful) rememberSearchSendPreset() else null
  val labels = LocalFluidActionLabels.current
  val press = if (onClick == null) Modifier.clearAndSetSemantics { } else Modifier
    .fluidPressable(onClick = { if (sending.acceptsTap) onClick() }, role = Role.Button, focusShape = FluidCapsuleShape,
      feedback = FluidPressFeedback.Expand)
    .semantics {
      if (preset != null && sending != FluidActionState.Idle) fluidActionDescribe(this, preset, sending, labels, label, null)
      else contentDescription = label
    }
  Box(Modifier.size(FluidSearchFieldDefaults.ActionSize).then(press), contentAlignment = Alignment.Center) {
    Box(Modifier.size(SearchSendDisc).background(scheme.primary, FluidCapsuleShape), contentAlignment = Alignment.Center) {
      if (preset == null) FluidIcon(FluidIcons.ArrowUpward, null, size = SearchSendGlyph, tint = scheme.onPrimary)
      else {
        val presentation = rememberFluidActionPresentation(preset, sending, live = onClick != null)
        // Sull'accento il rosso non si legge: il fallimento resta nell'inchiostro del disco, come sui tasti pieni.
        FluidActionGlyphContent(preset, sending, presentation,
          FluidActionLook(SearchSendGlyph, scheme.onPrimary, scheme.onPrimary, LocalFluidMotionPolicy.current.reducedMotion))
      }
    }
  }
}

/**
 * Il contenuto del campo che se ne va (F6B.6): la sua ultima posa aperta e la sua dichiarazione. [withLens] falso quando
 * il campo resta e torna lente: la lente è ancora la sua, escono solo parola e tasti.
 */
internal class ToolbarSearchGhostState(
  val serial: Int, val pose: ToolbarSearchPose, val spec: FluidSearchSpec, val withLens: Boolean = true,
  /** (F6B.7) quanto il vetro del campo che cresce superava la riga, in px: il testo esce con tutte le sue righe. */
  val extraPx: Float = 0f,
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

  fun track(pose: ToolbarSearchPose, spec: FluidSearchSpec, animate: Boolean, extraPx: Float = 0f) {
    val last = shown
    if (pose.hidden) {
      shown = null
      if (last != null && animate) ghost = ToolbarSearchGhostState(++serial, last.pose, last.spec, extraPx = last.extraPx)
      return
    }
    shown = ToolbarSearchGhostState(0, pose, spec, extraPx = extraPx)
    val current = ghost
    when {
      last != null && last.spec.active && !last.pose.compact && !spec.active && pose.compact ->
        ghost = if (animate) ToolbarSearchGhostState(++serial, last.pose, last.spec, withLens = false, extraPx = last.extraPx) else null
      // Riaperto mentre usciva: il campo vero riprende.
      current != null && (current.withLens || !pose.compact) -> ghost = null
    }
  }

  fun finish(done: ToolbarSearchGhostState) {
    if (ghost === done) ghost = null
  }
}

@Composable
private fun ToolbarSearchGhost(ghost: ToolbarSearchGhostState, namespace: FluidGlassNamespace, upward: Boolean, finished: () -> Unit) {
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
    // Il campo che cresceva esce con tutte le sue righe, ancorate al bordo dalla parte della tastiera (F6B.7).
    val grew = ghost.extraPx > 0.5f
    val rowHeight = FluidTextFieldDefaults.MinHeight
    Row(Modifier.fillMaxWidth().then(if (grew) Modifier.toolbarSearchGrowth(rowHeight, { ghost.extraPx }, upward)
      else Modifier.height(rowHeight)), verticalAlignment = if (grew) Alignment.Bottom else Alignment.CenterVertically) {
      Box(Modifier.width(lens).height(rowHeight), contentAlignment = Alignment.Center) {
        if (ghost.withLens) FluidIcon(fluidSearchIcon(spec), null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
      }
      Box(Modifier.weight(1f).then(if (grew) Modifier.padding(vertical = FluidTextFieldVerticalPadding) else Modifier),
        contentAlignment = Alignment.CenterStart) {
        Text(spec.query.ifEmpty { prompt }, color = ink, style = MaterialTheme.typography.bodyLarge,
          maxLines = if (grew) spec.maxLines else 1, overflow = TextOverflow.Ellipsis)
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
      when (val face = toolbarSearchKeyFace(spec)) {
        ToolbarSearchKeyFace.Send -> SearchSendButton(SearchLabels.send, null)
        else -> Box(Modifier.size(actionSize), contentAlignment = Alignment.Center) {
          val icon = when (face) {
            ToolbarSearchKeyFace.Voice -> FluidIcons.Mic
            ToolbarSearchKeyFace.Stop -> spec.trailing.stopIcon ?: FluidIcons.Filled.Stop
            else -> FluidIcons.Close
          }
          FluidIcon(icon, null, size = FluidSearchFieldDefaults.IconSize, tint = ink)
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
            if (open && spec.query.isNotEmpty() && spec.trailing.kind == FluidSearchTrailingKind.Clear) Box(Modifier.size(FluidSearchFieldDefaults.ActionSize),
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
              if (fluidSearchCanSend(spec)) SearchSendButton(SearchLabels.send, onClick = { fluidSearchSubmit(spec) { close() } })
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
