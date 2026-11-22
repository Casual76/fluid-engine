package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.GridView
import dev.antigravity.fluidengine.ui.fluid.icons.Menu
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

internal enum class AdaptiveForm { Bottom, Rail, Top, Sidebar }

internal fun adaptiveForm(width: Dp, sidebar: Boolean) = when {
  width < FluidPaneDefaults.MediumBreakpoint -> AdaptiveForm.Bottom
  width < FluidPaneDefaults.ExpandedBreakpoint -> AdaptiveForm.Rail
  sidebar -> AdaptiveForm.Sidebar
  else -> AdaptiveForm.Top
}

// WWDC25 208. Prima il vetro si porta al fianco della pagina, poi si distende verso il basso.
// In chiusura percorre le stesse due tratte in ordine inverso.
// Bordo sinistro della prima copertina, 13 campioni da f781 a f793 dell'estratto che inizia a 138 s.
internal val AdaptiveSlide = tween<Float>(400, easing = CubicBezierEasing(.39f, .98f, .34f, .90f))
// Bordo inferiore del pannello nel medesimo filmato, da f793 a f808.
internal val AdaptiveUnfold = tween<Float>(500, easing = CubicBezierEasing(.30f, .40f, .22f, 1f))
internal val AdaptiveFold = tween<Float>(430, easing = CubicBezierEasing(.2f, .05f, .18f, 1f))
private val edge = FluidSidebarDefaults.Margin.value
private val row = FluidSidebarDefaults.ComfortableRowHeight.value
private val pad = FluidTabBarDefaults.IndicatorInset.value
internal val adaptiveTabWidth = FluidFloatingTabBarDefaults.TabWidth.value
private val SideWidth = FluidPaneSizes().side.value

/** Le tre pose, in dp dal lato di lettura. Il piano riceve solo le destinazioni. */
internal class AdaptivePoses(val width: Float, height: Float, count: Int) {
  private val barHeight = (row + 2 * pad).coerceAtMost((height - 2 * edge).coerceAtLeast(1f))
  private val barWidth = (2 * pad + row + adaptiveTabWidth * count).coerceAtMost(width - 2 * edge)
  val top = Rect((width - barWidth) / 2, edge, (width + barWidth) / 2, edge + barHeight)
  val bridge = Rect(edge, edge, edge + SideWidth, edge + barHeight)
  val side = bridge.copy(bottom = (height - edge).coerceAtLeast(bridge.bottom))
  fun end(sidebar: Boolean) = if (sidebar) side else top

  // Anche una barra molto affollata può partire con il bordo sinistro già al margine.
  // Il centro si sposta comunque, fino al centro della colonna.
  fun slide(r: Rect): Float = share(r.center.x, top.center.x, bridge.center.x)
  fun unfold(r: Rect): Float = share(r.height, bridge.height, side.height)
  fun inset(r: Rect) = (bridge.right + edge) * slide(r)
  fun ink(r: Rect) = (1f - slide(r) + unfold(r)).coerceIn(0f, 1f)
}

internal class AdaptiveCells(r: Rect, val unfold: Float) {
  val x = lerp(row + pad, pad, unfold)
  val y = pad + row * unfold
  val viewportWidth = (r.width - x - pad).coerceAtLeast(1f)
  val viewportHeight = (r.height - y - pad).coerceAtLeast(1f)
  val width = lerp(adaptiveTabWidth, viewportWidth, unfold)
  val stepX = adaptiveTabWidth * (1f - unfold)
  val stepY = row * unfold
  val toggleX = lerp(pad, r.width - row - pad, unfold)
}

private fun share(value: Float, from: Float, to: Float) =
  if (abs(to - from) < .01f) 0f else ((value - from) / (to - from)).coerceIn(0f, 1f)

private class AdaptiveStage(
  val poses: AdaptivePoses, val density: Float, val rtl: Boolean,
  namespace: FluidGlassNamespace, initialSidebar: Boolean,
) {
  val id = FluidGlassQualifiedId(namespace, "navigation")
  var plane by mutableStateOf<FluidGlassContainerState?>(null)
  var target by mutableStateOf(poses.end(initialSidebar))
  var spec by mutableStateOf(AdaptiveSlide)

  fun current(): Rect {
    val host = plane ?: return target
    if (!host.renders) return target
    val px = host.flightOf(id)?.bounds ?: host.restFields.firstOrNull()?.node ?: return target
    val leading = if (rtl) poses.width - px.right / density else px.left / density
    return Rect(leading, px.top / density, leading + px.width / density, px.bottom / density)
  }

  suspend fun travel(sidebar: Boolean, animate: Boolean) {
    val end = poses.end(sidebar)
    if (!animate || plane?.renders != true) { target = end; return }
    if (target == end) return
    val here = current()
    // Un secondo tocco durante la prima tratta può tornare direttamente alla posa di partenza.
    val needsCorner = if (sidebar) poses.slide(here) < .995f else poses.unfold(here) > .005f
    if (needsCorner) {
      spec = if (sidebar) AdaptiveSlide else AdaptiveFold
      target = poses.bridge
      snapshotFlow { current() }.first {
        abs(it.left - target.left) <= 1f / density && abs(it.width - target.width) <= 1f / density &&
          abs(it.height - target.height) <= 1f / density
      }
    }
    spec = if (sidebar) AdaptiveUnfold else AdaptiveSlide
    target = end
  }
}

/**
 * Mantiene una sola dichiarazione delle destinazioni quando la finestra cambia misura. La pagina resta composta,
 * la rotta appartiene all'app e la preferenza per il pannello sopravvive alla ricreazione dell'Activity.
 * Il contenuto riceve gli spazi liberi sopra e sotto e viene disposto accanto alla colonna. Su immagini e fondali
 * a tutta larghezza [fluidBackgroundExtension] continua la pagina sotto il vetro.
 * Il rail, la tab bar in alto e la barra laterale stanno sotto la barra di stato, come la barra in alto di [FluidScreen]
 * (F6B.12): la pagina resta a finestra intera e conta da sé la barra di stato. Se un antenato ha già consumato la barra di
 * stato (`safeDrawingPadding`, la regola di prima), non si conta due volte. Gli altri inset di sistema (la barra di
 * navigazione, i ritagli ai lati) li applica l'app al contenitore.
 * Dove la navigazione toglie spazio alla pagina (la colonna del rail e della barra laterale, per tutta l'altezza, barra
 * di stato compresa) mette lo sfondo del tema, `MaterialTheme.colorScheme.background`: la finestra non si vede mai
 * (scelta di Alessio del 22 novembre 2026). Un fondale che vuole continuare sotto il vetro, come su iPad, usa
 * [fluidBackgroundExtension]: la sua estensione si disegna sopra lo sfondo di serie, e vince.
 */
@Composable
fun FluidAdaptiveNavigation(
  items: List<FluidTabItem>,
  selectedRoute: String?,
  onSelect: (FluidTabItem) -> Unit,
  modifier: Modifier = Modifier,
  onReselect: (FluidTabItem) -> Unit = {},
  content: @Composable (PaddingValues) -> Unit,
) {
  var sidebar by rememberSaveable { mutableStateOf(false) }
  val backdrop = rememberGlassBackdrop()
  val namespace = rememberFluidGlassNamespace()
  // La navigazione adattiva è una shell: il pannello pubblica qui la sostituzione.
  // Solo la forma Bottom monta una tab bar e la prende, anche in una galleria con una shell esterna.
  val chrome = if (items.isEmpty()) LocalFluidChromeController.current else rememberFluidChromeController()
  BoxWithConstraints(modifier.fillMaxSize()) {
   val window = maxHeight
   // La barra di stato (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12: il rail saliva sotto l'ora).
   // Il riquadro interno è più basso della finestra di quanto resta della barra dopo ciò che gli antenati hanno già
   // consumato: la chrome vive lì dentro, la pagina torna a finestra intera. Si legge in misura, giusto dal primo
   // fotogramma; senza barra di stato (o già consumata) tutto è com'era.
   BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))) {
    val statusTop = (window - maxHeight).value.coerceAtLeast(0f).dp
    val form = adaptiveForm(maxWidth, sidebar)
    val wide = form == AdaptiveForm.Top || form == AdaptiveForm.Sidebar
    val density = LocalDensity.current.density
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stage = if (!wide || items.isEmpty()) null else remember(maxWidth, maxHeight, density, rtl, items.size) {
      AdaptiveStage(AdaptivePoses(maxWidth.value, maxHeight.value, items.size), density, rtl, namespace, sidebar)
    }
    val start = remember(form == AdaptiveForm.Rail, items.isEmpty(), stage) {
      {
        when {
          items.isEmpty() -> 0.dp
          stage != null -> stage.poses.inset(stage.current()).dp
          form == AdaptiveForm.Rail -> FluidPaneSizes().rail
          else -> 0.dp
        }
      }
    }
    val ground = MaterialTheme.colorScheme.background
    Box(Modifier.adaptiveWindowPage(statusTop).glassBackdropSource(backdrop).adaptiveColumnGround(start, ground)) {
      CompositionLocalProvider(LocalBackgroundExtensionStart provides start, LocalFluidChromeController provides chrome) {
        Box(Modifier.fillMaxSize().layout { measurable, constraints ->
          val dx = start().roundToPx().coerceIn(0, constraints.maxWidth)
          val page = measurable.measure(constraints.offset(horizontal = -dx))
          layout(constraints.maxWidth, page.height) { page.placeRelative(dx, 0) }
        }) {
          content(PaddingValues(
            top = if (stage != null) (stage.poses.top.bottom + edge).dp + statusTop else 0.dp,
            bottom = if (items.isNotEmpty() && form == AdaptiveForm.Bottom) FluidTabBarDefaults.ContentInset else 0.dp,
          ))
        }
      }
    }
    if (items.isEmpty()) return@BoxWithConstraints
    when (form) {
      AdaptiveForm.Bottom -> CompositionLocalProvider(LocalFluidChromeController provides chrome) {
        FluidFloatingTabBar(items, selectedRoute, onSelect, rememberFluidFloatingTabBarScrollConnection(), backdrop,
          Modifier.align(Alignment.BottomCenter).padding(horizontal = FluidTabBarDefaults.HorizontalMargin)
            .padding(bottom = FluidTabBarDefaults.BottomMargin), onReselect)
      }
      AdaptiveForm.Rail -> FluidTabRail(items, selectedRoute, onSelect, backdrop,
        Modifier.padding(FluidSidebarDefaults.Margin), onReselect)
      else -> if (stage != null) {
        val tint = GlassDefaults.floatingTint()
        val reduced = LocalFluidMotionPolicy.current.reducedMotion
        FluidGlassContainer(Modifier.fillMaxSize(), animation = FluidGlassAnimation.Custom(stage.spec)) {
          val plane = LocalFluidGlassContainer.current
          SideEffect { stage.plane = plane }
          LaunchedEffect(stage, sidebar, reduced) { stage.travel(sidebar, !reduced) }
          val target = stage.target
          val shape = if (target == stage.poses.side) ContinuousCornerShape(FluidRadius.Group) else FluidCapsuleShape
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.align(Alignment.TopStart).offset(target.left.dp, target.top.dp).size(target.width.dp, target.height.dp)
              .fluidGlassId(stage.id.id, namespace).fluidGlassPanel(backdrop, tint, shape, large = true))
          }
          Box(Modifier.fillMaxSize().layout { measurable, constraints ->
            val r = stage.current()
            val tabs = measurable.measure(Constraints.fixed(r.width.dp.roundToPx(), r.height.dp.roundToPx()))
            layout(constraints.maxWidth, constraints.maxHeight) { tabs.placeRelative(r.left.dp.roundToPx(), r.top.dp.roundToPx()) }
          }) {
            AdaptiveSidebarLight(stage)
            AdaptiveTabs(stage, items, selectedRoute, sidebar, { sidebar = !sidebar }) {
              if (it.route == selectedRoute) onReselect(it) else onSelect(it)
            }
          }
        }
      }
    }
   }
  }
}

/** La pagina a finestra intera dentro il riquadro sotto la barra di stato: misurata più alta di [top] e posata più su. */
private fun Modifier.adaptiveWindowPage(top: Dp): Modifier = layout { measurable, constraints ->
  val dy = top.roundToPx()
  if (dy == 0 || !constraints.hasBoundedHeight) {
    val page = measurable.measure(constraints)
    return@layout layout(page.width, page.height) { page.place(0, 0) }
  }
  val page = measurable.measure(Constraints.fixed(constraints.maxWidth, constraints.maxHeight + dy))
  layout(constraints.maxWidth, constraints.maxHeight) { page.place(0, -dy) }
}

/**
 * Lo sfondo del tema nella colonna che la navigazione toglie alla pagina: largo quanto lo spostamento della pagina (lo
 * stesso arrotondamento), alto quanto la pagina a finestra intera, cioè anche sotto la barra di stato. Sta dentro la
 * registrazione del vetro, così il rail e la barra laterale ci posano sopra come sulla pagina, e sotto la pagina, così
 * l'estensione di [fluidBackgroundExtension] lo copre. A pagina intera (forma in basso, tab bar in alto) non disegna niente.
 */
private fun Modifier.adaptiveColumnGround(start: () -> Dp, color: Color) = drawBehind {
  val width = start().roundToPx().coerceIn(0, size.width.toInt()).toFloat()
  if (width <= 0f) return@drawBehind
  val left = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
  drawRect(color, topLeft = Offset(left, 0f), size = Size(width, size.height))
}

private fun Modifier.adaptiveInk(clarity: Float) = graphicsLayer {
  alpha = clarity
  val blur = (1f - clarity) * 5.dp.toPx()
  renderEffect = if (blur > .25f) BlurEffect(blur, blur) else null
}

// Il rettangolo interno segue la lastra vera anche quando un nuovo tocco inverte la corsa.
@Composable
private fun AdaptiveSidebarLight(stage: AdaptiveStage) {
  val host = LocalGlassAdaptiveHost.current
  Box(Modifier.fillMaxSize().sidebarContentLight({ host }) { stage.poses.unfold(stage.current()) })
}

@Composable
private fun AdaptiveTabs(
  stage: AdaptiveStage, items: List<FluidTabItem>, selected: String?, sidebar: Boolean,
  toggle: () -> Unit, select: (FluidTabItem) -> Unit,
) {
  val r = stage.current()
  val u = stage.poses.unfold(r)
  val clarity = stage.poses.ink(r)
  val labels = LocalFluidLabels.current
  val ink = LocalContentColor.current
  val slots = AdaptiveCells(r, u)
  Box(Modifier.fillMaxSize().clip(if (u == 0f) FluidCapsuleShape else ContinuousCornerShape(FluidRadius.Group))) {
    Box(Modifier.offset(slots.toggleX.dp, pad.dp).size(row.dp)
      .fluidPressable(onClick = toggle, role = Role.Button), contentAlignment = Alignment.Center) {
      FluidIcon(if (u > 0f) FluidIcons.GridView else FluidIcons.Menu, if (sidebar) labels.closeDrawer else labels.drawer,
        modifier = Modifier.adaptiveInk(clarity))
    }
    Box(Modifier.offset(slots.x.dp, slots.y.dp).size(slots.viewportWidth.dp, slots.viewportHeight.dp)
      .horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState()).selectableGroup()) {
      Layout(content = {
        items.forEach { item -> key(item.route) {
          val chosen = item.route == selected
          val phrase = fluidTabBadgePhrase(item.badge, labels)
          Row(Modifier.clip(FluidCapsuleShape)
            .background(ink.copy(alpha = if (chosen) .085f * clarity else 0f))
            .selectable(chosen, role = Role.Tab, onClick = { select(item) })
            .then(if (phrase == null) Modifier else Modifier.semantics { stateDescription = phrase })
            .adaptiveInk(clarity).padding(horizontal = (pad + 6f * u).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp, BiasAlignment.Horizontal(-u)),
          ) {
            if (u > 0f) FluidIcon(item.barIcon, null, Modifier.size((FluidMetrics.BarIconSize.value * u).dp))
            Box(Modifier.padding(start = (8f * u).dp)) {
              val title: @Composable () -> Unit = {
                FloatingTabLabel {
                  Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                }
              }
              if (item.badge != null) FluidTabBadgeAnchor(item.badge, title) else title()
            }
          }
        } }
      }) { children, _ ->
        val w = slots.width.dp.roundToPx().coerceAtLeast(1)
        val h = row.dp.roundToPx()
        val stepX = slots.stepX.dp.toPx()
        val stepY = slots.stepY.dp.toPx()
        val cells = children.map { it.measure(Constraints.fixed(w, h)) }
        layout(w + (stepX * cells.lastIndex).roundToInt(), h + (stepY * cells.lastIndex).roundToInt()) {
          cells.forEachIndexed { i, cell -> cell.placeRelative((i * stepX).roundToInt(), (i * stepY).roundToInt()) }
        }
      }
    }
  }
}
