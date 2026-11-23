package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import androidx.compose.ui.platform.LocalConfiguration
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.FluidToolbarMenu
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarMenu
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// ================================================================================================================
// Il titolo che diventa selettore (F6B.11, idea 14 di R8)
// ================================================================================================================

/**
 * Il titolo come sorgente di un menu o di un pannello (F6B.11; `toolbarTitleMenu { }` di SwiftUI e
 * `UINavigationItem.titleMenuProvider` di UIKit, iOS 16). Lo dichiara `title(…, menu = { … })` o `title(…, panel = { … })`
 * nella barra; lo disegnano il titolo agganciato e il titolo grande di `FluidScreen`, quello della barra dell'host e quello
 * di `FluidCollapsingTopBar`.
 *
 * Com'è (scelta di Alessio del 21 novembre 2026: «il titolo nudo con la freccia»): il titolo con accanto **il piccolo disco
 * con la freccia in giù** di iOS 26 (Anteprima: un disco di ≈18 pt a ≈8 pt dall'ultima lettera, F6B.0 §3). Il titolo si
 * tronca coi puntini e il disco resta in vista. La pillola di vetro alla `LocationPill` di Weather è uno stile a scelta che
 * questo pacchetto non porta.
 */
internal class FluidTitleSelector(
  val menu: (() -> List<FluidContextAction>)?,
  val panel: (@Composable FluidTitlePanelScope.() -> Unit)?,
  /** Che cosa fa il titolo, per TalkBack («Cambia account»); null: il nome del titolo e basta. */
  val hint: String?,
) {
  init {
    require(menu == null || panel == null) { "il titolo apre un menu o un pannello, non tutti e due" }
  }
}

/**
 * Lo scope del pannello che nasce dal titolo (F6B.11, `title(…, panel = { … })`): la colonna del pannello e la sua
 * chiusura. Chiudendolo, il pannello rientra nel titolo da cui è nato. Sotto i 600 dp il pannello è un foglio dal titolo.
 */
class FluidTitlePanelScope internal constructor(column: ColumnScope, private val close: () -> Unit) : ColumnScope by column {
  /** Chiude il pannello: rientra nel titolo. Per esempio dopo che la persona ha scelto una località. */
  fun dismiss() = close()
}

/** Quale copia del titolo apre: quella agganciata alla barra, o il titolo grande nella lista. */
internal enum class FluidTitleSpot { Docked, Large }

private data class FluidTitleSourceId(val spot: FluidTitleSpot)

/**
 * Le misure del disco con la freccia, in frazioni della taglia del carattere del titolo, così scala col titolo (il titolo
 * grande ha il disco grande, e il titolo che si aggancia lo porta con sé). Dal fermo di Anteprima in iOS 26 (F6B.0 §3):
 * disco ≈18 pt e vuoto ≈8 pt accanto a un titolo in linea da ≈21; fiducia media, i punti sono letti su un fermo.
 */
internal object FluidTitleSelectorDefaults {
  /** Il diametro del disco: 0,86 della taglia del carattere (18 dp su 21). */
  const val Disc: Float = 0.86f

  /** Il vuoto fra l'ultima lettera e il disco: 0,38 della taglia del carattere (8 dp su 21). */
  const val Gap: Float = 0.38f

  /** L'inchiostro del disco: quello del titolo al 12%, come i dischi grigi dei controlli di sistema. A occhio. */
  const val DiscInk: Float = 0.12f

  /** L'inchiostro della freccia: quello del titolo all'80%. A occhio. */
  const val ArrowInk: Float = 0.8f

  /** La freccia: larga 0,42 del disco, alta 0,2, tratto 0,1 del disco. A occhio. */
  const val ArrowWidth: Float = 0.42f
  const val ArrowHeight: Float = 0.2f
  const val ArrowStroke: Float = 0.1f

  /** Sotto questa larghezza (dp) il pannello del titolo è un foglio dal titolo; sopra un popover. È la soglia del portale. */
  const val CompactWidthDp: Int = 600

  /** Quanto spazio prende il disco col suo vuoto, in frazioni della taglia del carattere. */
  val Reserve: Float get() = Disc + Gap
}

/** Quanto spazio, in dp, chiedono il disco e il suo vuoto accanto a un titolo di [style]. */
@Composable
internal fun fluidTitleSelectorReserve(style: TextStyle): Dp {
  val density = LocalDensity.current
  val size: TextUnit = style.fontSize
  if (!size.isSpecified) return 0.dp
  return with(density) { (size.toPx() * FluidTitleSelectorDefaults.Reserve).toDp() }
}

/**
 * Lo stato del titolo-selettore di una barra: il menu (dall'host dei modali, come quello di un tasto della barra) o il
 * pannello (un popover dal titolo, che sul telefono si adatta a foglio dal titolo), e da quale copia del titolo si è
 * aperto. Le due copie hanno ognuna la sua sorgente, perché il viaggio parta da quella che si vede.
 */
@Stable
internal class FluidTitleSelectorState(val namespace: FluidGlassNamespace) {
  var selector: FluidTitleSelector? by mutableStateOf(null)
  var title: String by mutableStateOf("")
  var panelOpen: Boolean by mutableStateOf(false)
  var openedFrom: FluidTitleSpot by mutableStateOf(FluidTitleSpot.Docked)
  internal var menu: FluidToolbarMenu? = null
  private val discs = HashMap<FluidTitleSpot, Rect>()

  /**
   * Il disco non si disegna mentre il menu nato da lui è aperto: è diventato il menu. Vale per le due copie del titolo,
   * perché mentre il titolo grande si trasforma quella che si vede è la copia agganciata.
   */
  val consumed: Boolean get() = menu?.anchor?.hidden == true

  /** Vero mentre il menu o il pannello sono aperti (per lo stato che TalkBack dice). */
  val expanded: Boolean get() = panelOpen || menu?.entry?.visible == true

  private val sources = FluidTitleSpot.entries.associateWith { FluidTransitionSource(FluidTitleSourceId(it), namespace) }

  fun source(spot: FluidTitleSpot): FluidTransitionSource = sources.getValue(spot)

  /** Il riquadro da cui nasce il menu, nella radice: un quadrato attorno al disco di [spot] ([fluidTitleSelectorAnchor]). */
  internal fun placeDisc(spot: FluidTitleSpot, rect: Rect) {
    discs[spot] = rect
  }

  /** Apre il menu o il pannello da [spot]. Falso se non c'è niente da aprire (senza host dei modali, un menu vuoto). */
  fun open(spot: FluidTitleSpot): Boolean {
    val selector = selector ?: return false
    if (expanded) return false
    openedFrom = spot
    val actions = selector.menu
    if (actions != null) {
      val menu = menu ?: return false
      // Il menu nasce dal disco, come da un tasto tondo della barra (F5A.2, B1 a): un riquadro degenere (il titolo fuori
      // vista, non ancora posato) non apre niente.
      val rest = discs[spot]?.takeIf { fluidTitleSelectorAnchorUsable(it) } ?: return false
      menu.anchor.restBounds = rest
      return menu.open(actions(), null) { }
    }
    if (selector.panel != null) {
      panelOpen = true
      return true
    }
    return false
  }
}

/**
 * Il titolo-selettore di una barra, se la barra ne dichiara uno ([selector] non null): lo stato, il menu registrato
 * nell'host dei modali e il pannello (un portale, che non emette niente qui). [face] è la faccia che il menu porta nel suo
 * viaggio (il titolo col disco).
 */
@Composable
internal fun rememberFluidTitleSelector(selector: FluidTitleSelector?, title: String, style: TextStyle): FluidTitleSelectorState? {
  if (selector == null) return null
  val namespace = rememberFluidGlassNamespace()
  val state = remember(namespace) { FluidTitleSelectorState(namespace) }
  val menu = rememberFluidToolbarMenu()
  val tint = GlassDefaults.controlTint()
  val optics = GlassDefaults.buttonOptics()
  val ink = LocalContentColor.current
  SideEffect {
    state.selector = selector
    state.title = title
    state.menu = menu
    menu.entry.paneTitle = title
    menu.anchor.tintFrom = tint
    menu.anchor.opticsFrom = optics
    menu.anchor.face = { FluidTitleSelectorFace(style, ink) }
  }
  val panel = selector.panel
  if (panel != null) {
    val body: @Composable ColumnScope.() -> Unit = {
      val scope = remember(this) { FluidTitlePanelScope(this) { state.panelOpen = false } }
      scope.panel()
    }
    // Sotto i 600 dp il pannello è un foglio **che nasce dal titolo** (lo zoom del foglio dalla sorgente, F5B.3) e ci
    // rientra: il popover adattato a foglio del portale salirebbe dal basso senza il viaggio dalla sorgente. Sopra, il
    // popover dal titolo (F5B.1).
    val compact = LocalConfiguration.current.screenWidthDp in 1 until FluidTitleSelectorDefaults.CompactWidthDp
    if (compact) {
      FluidGlassSheet(
        visible = state.panelOpen,
        onDismissRequest = { state.panelOpen = false },
        detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
        paneTitle = title,
        source = state.source(state.openedFrom),
        content = body,
      )
    } else {
      FluidGlassModalPortal(
        visible = state.panelOpen,
        onDismissRequest = { state.panelOpen = false },
        paneTitle = title,
        source = state.source(state.openedFrom),
        compactAdaptation = FluidCompactAdaptation.Popover,
        content = body,
      )
    }
  }
  return state
}

/** La faccia che il menu porta via dal titolo: il disco con la freccia (il titolo resta al suo posto). */
@Composable
private fun FluidTitleSelectorFace(style: TextStyle, ink: Color) {
  val size = with(LocalDensity.current) { if (style.fontSize.isSpecified) style.fontSize.toDp() else 17.dp }
  Canvas(Modifier.size(size * FluidTitleSelectorDefaults.Disc)) {
    drawFluidTitleDisc(Offset(this.size.width / 2f, this.size.height / 2f), this.size.width, ink)
  }
}

/**
 * Il riquadro da cui nasce il menu del titolo: un quadrato centrato sul disco ([center], nella radice), di lato il
 * diametro del disco ma almeno [minSidePx] (il vetro di un tasto della barra, 44 dp). Il menu del titolo nasce così
 * come quello di un tasto tondo della barra, la strada più provata (F5A.2, B1 a). Pura.
 */
internal fun fluidTitleSelectorAnchor(center: Offset, diameter: Float, minSidePx: Float): Rect {
  val side = maxOf(diameter, minSidePx)
  return Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)
}

/** Se [rect] può essere la sorgente di un menu: finito, non vuoto (almeno 8 px per lato). Pura. */
internal fun fluidTitleSelectorAnchorUsable(rect: Rect): Boolean =
  rect.left.isFinite() && rect.top.isFinite() && rect.right.isFinite() && rect.bottom.isFinite() &&
    rect.width >= 8f && rect.height >= 8f

/**
 * Il bersaglio di una copia del titolo ([spot]): la sorgente del pannello e il tocco (un elenco a discesa per TalkBack, che
 * dice anche [FluidTitleSelector.hint]). Va in testa alla catena del titolo, **prima** del suo layer: il layer della
 * trasformazione del titolo grande in quello agganciato sta dopo, e il disco (con l'ancora del menu) lo posa
 * [fluidTitleSelectorDisc], dopo il layer, così si trasforma col titolo.
 * [enabled] falso (la copia agganciata finché il titolo grande è in vista): niente tocco, la copia resta quella di prima.
 */
@Composable
internal fun Modifier.fluidTitleSelectorTarget(
  state: FluidTitleSelectorState?,
  spot: FluidTitleSpot,
  layout: () -> TextLayoutResult?,
  fontSizePx: Float,
  enabled: Boolean = true,
): Modifier {
  if (state == null) return this
  val haptics = LocalFluidHaptics.current
  val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
  val interaction = remember { MutableInteractionSource() }
  val hint = state.selector?.hint
  return this
    // Il pannello nasce dal titolo intero, che finché il pannello è in scena non si disegna (la sorgente consumata).
    .fluidTransitionSource(FluidTitleSourceId(spot), state.namespace, FluidCapsuleShape)
    .then(
      if (!enabled) Modifier else Modifier.clickable(
        interactionSource = interaction,
        indication = null,
        role = Role.DropdownList,
        onClickLabel = hint,
      ) { if (state.open(spot)) haptics.play(FluidHapticEvent.Open) },
    )
}

/**
 * Il disco con la freccia dopo l'ultima riga del titolo (F6B.11): va **dopo** il layer del titolo, così lo segue quando il
 * titolo grande diventa quello agganciato. [layout] è l'impaginazione del titolo, [fontSizePx] la sua taglia.
 */
@Composable
internal fun Modifier.fluidTitleSelectorDisc(
  state: FluidTitleSelectorState?,
  spot: FluidTitleSpot,
  layout: () -> TextLayoutResult?,
  fontSizePx: Float,
): Modifier {
  if (state == null) return this
  val ink = LocalContentColor.current
  val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == LayoutDirection.Rtl
  val minSidePx = with(LocalDensity.current) { FluidMetrics.GlassButtonLens.toPx() }
  return this
    .onGloballyPositioned { coordinates ->
      val text = layout() ?: return@onGloballyPositioned
      val disc = fluidTitleSelectorDiscSpot(text, fontSizePx, rtl) ?: return@onGloballyPositioned
      // Dopo il layer del titolo: il centro nella radice comprende la trasformazione (il titolo grande che si aggancia).
      val center = coordinates.localToRoot(disc.center)
      val scale = coordinates.localToRoot(disc.center + Offset(1f, 0f)).x - center.x
      state.placeDisc(spot, fluidTitleSelectorAnchor(center, disc.diameter * scale, minSidePx))
    }
    .drawWithContent {
      drawContent()
      if (state.consumed) return@drawWithContent
      val text = layout() ?: return@drawWithContent
      val disc = fluidTitleSelectorDiscSpot(text, fontSizePx, rtl) ?: return@drawWithContent
      drawFluidTitleDisc(disc.center, disc.diameter, ink)
    }
}

/** Dove sta il disco nelle coordinate del titolo: il centro e il diametro. */
internal data class FluidTitleDiscSpot(val center: Offset, val diameter: Float)

/**
 * Il disco dopo la fine dell'ultima riga di [text] (a destra, o a sinistra in RTL), centrato su quella riga, con le misure
 * di [FluidTitleSelectorDefaults] per un carattere di [fontSizePx]. Pura.
 */
internal fun fluidTitleSelectorDiscSpot(text: TextLayoutResult, fontSizePx: Float, rtl: Boolean): FluidTitleDiscSpot? {
  if (text.lineCount == 0 || fontSizePx <= 0f) return null
  val line = text.lineCount - 1
  val diameter = fontSizePx * FluidTitleSelectorDefaults.Disc
  val gap = fontSizePx * FluidTitleSelectorDefaults.Gap
  val y = (text.getLineTop(line) + text.getLineBottom(line)) / 2f
  val x = if (rtl) text.getLineLeft(line) - gap - diameter / 2f else text.getLineRight(line) + gap + diameter / 2f
  return FluidTitleDiscSpot(Offset(x, y), diameter)
}

/** Il disco e la freccia in giù, centrati in [center], del diametro [diameter], con l'inchiostro del titolo [ink]. */
internal fun DrawScope.drawFluidTitleDisc(center: Offset, diameter: Float, ink: Color) {
  drawCircle(ink.copy(alpha = ink.alpha * FluidTitleSelectorDefaults.DiscInk), radius = diameter / 2f, center = center)
  val w = diameter * FluidTitleSelectorDefaults.ArrowWidth
  val h = diameter * FluidTitleSelectorDefaults.ArrowHeight
  val path = Path().apply {
    moveTo(center.x - w / 2f, center.y - h / 2f)
    lineTo(center.x, center.y + h / 2f)
    lineTo(center.x + w / 2f, center.y - h / 2f)
  }
  drawPath(
    path, ink.copy(alpha = ink.alpha * FluidTitleSelectorDefaults.ArrowInk),
    style = Stroke(width = diameter * FluidTitleSelectorDefaults.ArrowStroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}
