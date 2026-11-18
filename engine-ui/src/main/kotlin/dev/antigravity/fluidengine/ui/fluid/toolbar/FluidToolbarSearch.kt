package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidPressFeedback
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchField
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidSearchLabels
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassInkContent
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidFloatingGlass
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidSearchContentAlpha
import dev.antigravity.fluidengine.ui.fluid.fluidSearchWidth
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Search
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import dev.antigravity.fluidengine.ui.fluid.smoothStep
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent

/**
 * I posti in cui una schermata offre la ricerca alla barra. [FluidScreen][dev.antigravity.fluidengine.ui.fluid.FluidScreen]
 * li chiama **tutti**, sempre; è [FluidToolbarSearchSlot] a decidere in quale il campo si posa e come. Così F4B.2 può
 * spostare il campo (nella barra in basso, da tasto a campo, sopra la tastiera) riscrivendo **questo file**, senza
 * riaprire `FluidScreen.kt` né la DSL (piano, F4A, «Ricerca: chi possiede cosa»).
 */
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

/** Le posizioni di F4B conservano il posto sotto il titolo finché non hanno una sede propria. */
internal fun fluidToolbarSearchSits(spec: FluidSearchSpec, site: FluidToolbarSearchSite): Boolean =
  site == FluidToolbarSearchSite.UnderTitle ||
    (site == FluidToolbarSearchSite.PinnedPrincipal && spec.placement == FluidSearchPlacement.Principal)

/** La chiave dell'elemento della lista che porta il campo. */
internal const val FluidToolbarSearchItemKey = "fluid:toolbar-search"

/**
 * Il punto unico della ricerca di una barra (F4A.2): il parametro `search` di `FluidScreen` e `search(spec)` della DSL
 * arrivano qui, e solo qui. In F4A posa il [FluidSearchField] di oggi **così com'è**: sempre aperto, oppure richiudibile
 * in un tondo con la lente se la pagina chiede [FluidSearchBehavior.CollapseToButton] e possiede l'apertura
 * ([FluidSearchSpec.onActiveChange]); il fuoco automatico è spento, perché un campo che sta in pagina e alza la tastiera
 * da solo coprirebbe i risultati. Posizioni, passaggio da tasto a campo, vicini e tastiera sono di F4B.2, che riscrive
 * questo file.
 */
@Composable
internal fun FluidToolbarSearchSlot(
  spec: FluidSearchSpec,
  site: FluidToolbarSearchSite,
  modifier: Modifier = Modifier,
  pinned: FluidToolbarMinimizeState? = null,
  pinnedBackdrop: GlassBackdropState? = null,
) {
  if (!fluidToolbarSearchSits(spec, site)) return
  if (pinned != null && pinnedBackdrop != null) {
    FluidToolbarPrincipalSearch(spec, modifier, pinned, pinnedBackdrop)
    return
  }
  val collapsible = spec.behavior == FluidSearchBehavior.CollapseToButton && spec.onActiveChange != null
  FluidSearchField(
    query = spec.query,
    onQueryChange = spec.onQueryChange,
    modifier = modifier.fillMaxWidth(),
    placeholder = spec.prompt,
    onSearch = spec.onSearch,
    expanded = if (collapsible) spec.active else true,
    onExpandedChange = if (collapsible) spec.onActiveChange else null,
    autoFocus = false,
  )
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
  pinned: FluidToolbarMinimizeState,
  backdrop: GlassBackdropState,
) {
  val scheme = MaterialTheme.colorScheme
  val labels = remember { FluidSearchLabels() }
  val toggle = spec.onActiveChange.takeIf { spec.behavior == FluidSearchBehavior.CollapseToButton }
  val open = toggle == null || spec.active
  val focus = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  var wasOpen by remember { mutableStateOf(open) }
  LaunchedEffect(open) {
    if (wasOpen && !open) {
      focus.clearFocus()
      keyboard?.hide()
    }
    wasOpen = open
  }
  BackHandler(enabled = toggle != null && open) { toggle?.invoke(false) }

  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val expansion = animateFloatAsState(
    targetValue = if (open) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.standard(),
    label = "principal search expansion",
  )
  val width = if (toggle == null) Modifier.fillMaxWidth() else Modifier.layout { measurable, constraints ->
    val closed = FluidSearchFieldDefaults.Height.roundToPx()
    val full = if (constraints.hasBoundedWidth) constraints.maxWidth else FluidSearchFieldDefaults.ExpandedFallbackWidth.roundToPx()
    val px = fluidSearchWidth(closed, full, expansion.value).coerceIn(constraints.minWidth, constraints.maxWidth)
    val child = measurable.measure(constraints.copy(minWidth = px, maxWidth = px))
    layout(child.width, child.height) { child.placeRelative(0, 0) }
  }

  val ink = rememberGlassInk()
  val glassVisible by remember(pinned) { derivedStateOf { pinned.progress > FluidSearchPinGlassPhase } }
  Box(
    modifier
      .then(width)
      .heightIn(min = FluidSearchFieldDefaults.Height)
      .pointerInput(Unit) { detectTapGestures { } }
      .then(if (open) Modifier else Modifier.fluidPressable(
        onClick = { toggle?.invoke(true) },
        role = Role.Button,
        focusShape = FluidCapsuleShape,
        feedback = FluidPressFeedback.Expand,
        haptic = FluidHapticEvent.Open,
      )),
  ) {
    Box(
      Modifier.matchParentSize()
        .graphicsLayer { alpha = 1f - fluidToolbarSearchGlassProgress(pinned.progress) }
        .background(scheme.surfaceContainerHighest, FluidCapsuleShape),
    )
    if (glassVisible) Box(
      Modifier.matchParentSize()
        .graphicsLayer { alpha = fluidToolbarSearchGlassProgress(pinned.progress) }
        .fluidFloatingGlass(backdrop, onPhoto = false, ink = ink),
    )
    GlassInkContent(ink, LocalContentColor.current) {
      val colors = MaterialTheme.colorScheme
      Row(
        Modifier.fillMaxWidth().heightIn(min = FluidSearchFieldDefaults.Height).clip(FluidCapsuleShape),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidIcon(
          FluidIcons.Search,
          contentDescription = labels.search.takeUnless { open },
          modifier = Modifier.padding(start = FluidSearchFieldDefaults.IconInset).size(FluidSearchFieldDefaults.IconSize),
          tint = colors.onSurfaceVariant,
        )
        if (open || expansion.value > 0.001f) {
          BasicTextField(
            value = spec.query,
            onValueChange = spec.onQueryChange,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
              .graphicsLayer { alpha = fluidSearchContentAlpha(expansion.value) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
            singleLine = true,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
              val submit = spec.onSearch
              if (submit != null) submit() else {
                keyboard?.hide()
                focus.clearFocus()
              }
            }),
            decorationBox = { inner ->
              Box(contentAlignment = Alignment.CenterStart) {
                if (spec.query.isEmpty()) Text(
                  spec.prompt ?: labels.search,
                  style = MaterialTheme.typography.bodyLarge,
                  color = colors.onSurfaceVariant,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                inner()
              }
            },
          )
          if (open && spec.query.isNotEmpty()) FluidCapsuleAction(
            icon = FluidIcons.Close, contentDescription = labels.clear, onClick = { spec.onQueryChange("") },
            size = FluidSearchFieldDefaults.ActionSize,
          )
          if (open && toggle != null) FluidCapsuleAction(
            icon = FluidIcons.Close, contentDescription = labels.close, onClick = { toggle(false) },
            size = FluidSearchFieldDefaults.ActionSize,
          )
        }
      }
    }
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
