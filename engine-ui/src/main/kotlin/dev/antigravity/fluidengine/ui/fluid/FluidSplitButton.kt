package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.KeyboardArrowDown
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarElementType
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKind
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKinds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTypeSlot
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlin.math.ceil
import kotlin.math.max

// ================================================================================================================
// Il tasto diviso (F6B.11): l'azione e la freccia del menu in un vetro solo
// ================================================================================================================

/**
 * Le misure del tasto diviso (F6B.11). **Il tasto diviso visibile non c'è in iOS** (c'è in Material 3 Expressive e nei
 * menu a tendina con azione principale del Mac, R8): l'aspetto è una scelta Fluid, a occhio (23 novembre 2026), dentro la
 * scelta di Alessio del 21 novembre 2026 («un vetro solo, con un filo sottile fra azione e freccia»).
 */
object FluidSplitButtonDefaults {
  /**
   * La zona disegnata della freccia, dopo il filo: 38 dp. Più stretta dei 48 dp del bersaglio, che esce dal vetro verso la
   * fine (10 dp: meno dei 12 dp fra due gruppi della barra, quindi non tocca il bersaglio del vicino).
   */
  val ArrowZone: Dp = 38.dp

  /** Lo spessore del filo fra azione e freccia: un dp, il filo dei divisori dell'engine. */
  val Hairline: Dp = 1.dp

  /** Quanto è alto il filo: 20 dp, meno della metà del vetro da 46, così si legge come una cucitura e non come un bordo. */
  val HairlineHeight: Dp = 20.dp

  /** L'inchiostro del filo: quello del tasto al 28%, a occhio. */
  const val HairlineInk: Float = 0.28f

  /** La freccia: il chevron in giù a 20 dp, più piccolo delle icone della barra (28 dp), come un accessorio. */
  val ArrowSize: Dp = 20.dp

  /**
   * La zona dell'azione quando è un'icona: 48 dp, il bersaglio intero, così l'azione e la freccia hanno ognuna 48 dp senza
   * che l'azione esca dal vetro verso l'inizio.
   */
  val IconZone: Dp = 48.dp

  /** Quanto il tasto aggiunge dopo la sua scritta: il filo e la zona della freccia. */
  internal val TrailingDp: Float get() = Hairline.value + ArrowZone.value
}

/**
 * I due bersagli del tasto diviso in un posto largo [widthDp] con la zona della freccia [zoneDp] (in dp, dall'inizio del
 * posto): l'azione dall'inizio al filo, la freccia dal filo alla fine. Disgiunti, ognuno largo almeno [minDp]; dove la zona
 * disegnata è più stretta il bersaglio esce dal vetro **verso il bordo che non tocca l'altro** (l'azione verso l'inizio, la
 * freccia verso la fine). Pura.
 */
internal data class FluidSplitTargets(val actionLeft: Float, val actionWidth: Float, val menuLeft: Float, val menuWidth: Float) {
  val actionRight: Float get() = actionLeft + actionWidth
  val menuRight: Float get() = menuLeft + menuWidth
}

internal fun fluidSplitTargets(widthDp: Float, zoneDp: Float, minDp: Float): FluidSplitTargets {
  val split = (widthDp - zoneDp - FluidSplitButtonDefaults.Hairline.value).coerceIn(0f, widthDp)
  val actionWidth = max(split, minDp)
  return FluidSplitTargets(split - actionWidth, actionWidth, split, max(widthDp - split, minDp))
}

/** Il lato puro del tasto diviso nella barra (il punto unico di F6B.3a): la faccia è la scritta o l'icona. */
internal data class FluidToolbarSplitKind(val face: Any, val iconWidthDp: Float?) : FluidToolbarKind {
  override val type: String get() = FluidToolbarKinds.Split
  override val identity: Any get() = face
  override val state: Any? get() = null
  override val widthDp: Float? get() = iconWidthDp?.let { FluidSplitButtonDefaults.IconZone.value + FluidSplitButtonDefaults.TrailingDp }
  override val trailingDp: Float get() = FluidSplitButtonDefaults.TrailingDp
}

/**
 * Il lato della resa del tasto diviso nella barra: la faccia (l'azione, il filo, la freccia) e la zona della freccia, che
 * dà al posto il suo secondo bersaglio ([FluidToolbarElementType.menuZone]). Il menu è quello della voce (`menu`), che la
 * barra apre dallo stesso vetro (F5A.2).
 */
internal class FluidToolbarSplitType(
  override val kind: FluidToolbarSplitKind,
  private val menuLabel: String?,
) : FluidToolbarElementType() {
  override val menuZone: Dp get() = FluidSplitButtonDefaults.ArrowZone
  override val menuZoneLabel: String? get() = menuLabel

  @Composable
  override fun rememberSlot(live: Boolean): FluidToolbarTypeSlot = remember(kind) {
    object : FluidToolbarTypeSlot {
      @Composable
      override fun Face(color: Color, iconSize: Dp, onTint: Boolean) {
        FluidSplitFace(
          text = kind.face as? String, icon = kind.face as? ImageVector, color = color, iconSize = iconSize,
          actionZone = if (kind.iconWidthDp != null) FluidSplitButtonDefaults.IconZone else null,
          modifier = Modifier.fillMaxWidth(),
        )
      }

      override fun describe(receiver: SemanticsPropertyReceiver, name: String?) = Unit
    }
  }
}

/**
 * La faccia del tasto diviso: l'azione (la scritta, o l'icona nella sua zona da [actionZone]), il filo e la freccia nella
 * sua zona. Con [modifier] largo quanto il vetro il filo cade dove cade il confine dei due bersagli.
 */
@Composable
internal fun FluidSplitFace(
  text: String?,
  icon: ImageVector?,
  color: Color,
  iconSize: Dp,
  modifier: Modifier = Modifier,
  actionZone: Dp? = null,
) {
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Box(
      if (actionZone != null) Modifier.width(actionZone) else Modifier.weight(1f, fill = true),
      contentAlignment = Alignment.Center,
    ) {
      if (icon != null) {
        FluidIcon(icon, null, tint = color, size = iconSize)
      } else if (text != null) {
        Text(
          text = text, style = FluidToolbarDefaults.textStyle(), color = color, maxLines = 1, softWrap = false,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Box(
      Modifier
        .width(FluidSplitButtonDefaults.Hairline)
        .height(FluidSplitButtonDefaults.HairlineHeight)
        .background(color.copy(alpha = color.alpha * FluidSplitButtonDefaults.HairlineInk)),
    )
    Box(Modifier.width(FluidSplitButtonDefaults.ArrowZone), contentAlignment = Alignment.Center) {
      FluidIcon(FluidIcons.KeyboardArrowDown, null, tint = color, size = FluidSplitButtonDefaults.ArrowSize)
    }
  }
}

/**
 * Un bersaglio posato a [left] (dp, dall'inizio del genitore, anche negativo) e largo [width], alto [height], centrato in
 * verticale nel genitore, che il genitore misura come se occupasse tutto il suo riquadro. Rispetta il verso (RTL).
 */
internal fun Modifier.fluidTargetAt(left: Dp, width: Dp, height: Dp = FluidMetrics.TouchTarget): Modifier =
  layout { measurable, constraints ->
    val w = width.roundToPx()
    val h = height.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(w, h))
    val parentWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else w
    val parentHeight = if (constraints.hasBoundedHeight) constraints.maxHeight else h
    layout(parentWidth, parentHeight) { placeable.placeRelative(left.roundToPx(), (parentHeight - h) / 2) }
  }

/**
 * **Il tasto diviso, da solo** (F6B.11; R8, idea 9; scelta di Alessio del 21 novembre 2026: «un vetro solo, con un filo
 * sottile fra azione e freccia»). Un vetro solo con due zone: [label] (o [icon], e allora [label] è il nome per TalkBack)
 * fa [onClick]; la freccia apre [menu] **dallo stesso vetro**, che diventa il menu e ci rientra (F5A.2, il viaggio in tre
 * tempi del tasto che si trasforma). Anche la pressione lunga sull'azione apre il menu, col dito che può scegliere una voce
 * senza staccarsi (R1 N5). «Invia ▾» con «Programma invio».
 *
 * - **Bersagli.** Due, disgiunti, ognuno di almeno 48 × 48 dp: la freccia disegnata è larga 38 dp
 *   ([FluidSplitButtonDefaults.ArrowZone]) e il suo bersaglio esce dal vetro verso la fine.
 * - **TalkBack.** Due pulsanti: [label], e [menuLabel] (di serie «Altro» col nome dell'azione).
 * - **Senza `FluidGlassModalHost`** il menu non si apre (come [FluidBarAction]): la freccia non fa niente, l'azione sì.
 *
 * Nella barra dichiarata lo stesso tasto è `split(…)` di `toolbar { }`, che lo mette in un gruppo a sé.
 */
@Composable
fun FluidSplitButton(
  label: String,
  onClick: () -> Unit,
  menu: () -> List<FluidContextAction>,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  menuLabel: String? = null,
  enabled: Boolean = true,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
) {
  val host = LocalFluidGlassModalHostState.current
  val entry = rememberFluidGlassModalEntry()
  val haptics = LocalFluidHaptics.current
  val anchor = remember { FluidMenuAnchor() }
  val press = rememberFluidPressState(FluidPressFeedback.ExpandRound)
  val density = LocalDensity.current
  val labels = LocalFluidLabels.current
  val menuName = menuLabel ?: "${labels.more} ($label)"
  val tint = GlassDefaults.controlTint()
  val barHost = LocalGlassAdaptiveHost.current
  val faceInk = rememberGlassInk()
  if (barHost != null) faceInk.bind(barHost, GlassDefaults.isDarkSurface()) else faceInk.unbind()
  val measurer = rememberTextMeasurer()
  val textStyle = FluidToolbarDefaults.textStyle()
  val height = FluidGlassSize.Regular.lens
  val width = if (icon != null) {
    FluidSplitButtonDefaults.IconZone + FluidSplitButtonDefaults.Hairline + FluidSplitButtonDefaults.ArrowZone
  } else {
    val px = measurer.measure(label, textStyle, maxLines = 1, softWrap = false).size.width
    (ceil(px / density.density) + FluidToolbarDefaults.TextPadding.value * 2f).dp +
      FluidSplitButtonDefaults.Hairline + FluidSplitButtonDefaults.ArrowZone
  }
  val menuFace: @Composable () -> Unit = {
    FluidSplitFace(
      text = label.takeIf { icon == null }, icon = icon, color = LocalContentColor.current, iconSize = FluidMetrics.BarIconSize,
      modifier = Modifier.width(width).height(height),
      actionZone = if (icon != null) FluidSplitButtonDefaults.IconZone else null,
    )
  }

  SideEffect {
    entry.presentation = FluidGlassModalPresentation.Expand
    entry.paneTitle = label
    entry.onDismissRequest = { entry.visible = false }
    entry.menuAnchor = anchor
    entry.menuHandOff = anchor.handOff
    anchor.face = menuFace
    anchor.ink = if (barHost != null) faceInk else null
    anchor.tintFrom = tint
    anchor.opticsFrom = GlassDefaults.buttonOptics()
  }
  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      entry.visible = false
      anchor.hidden = false
      host?.unregister(entry)
    }
  }
  // L'apertura è sincrona, nel gesto (come [FluidBarAction]): il vetro si nasconde nello stesso fotogramma in cui il menu
  // compare sulla sua sagoma.
  val open: (Boolean) -> Boolean = open@{ withFinger ->
    if (host == null || entry.visible || !enabled) return@open false
    val list = menu()
    if (list.isEmpty()) return@open false
    anchor.pressedBounds = if (withFinger) fluidPressedBounds(anchor.restBounds, press, density.density) else anchor.restBounds
    entry.actions = list
    entry.openedAt = host.nextSequence()
    entry.visible = true
    entry.lifted = true
    anchor.hidden = true
    entry.selection.cancel()
    if (withFinger) anchor.handOff.attach(entry.selection)
    true
  }
  val targets = fluidSplitTargets(width.value, FluidSplitButtonDefaults.ArrowZone.value, FluidMetrics.TouchTarget.value)
  val primary = onClick
  Box(modifier) {
    FluidGlassIconButtonCore(
      onClick = onClick,
      // Il vetro prende il dito (la pressione, il bagliore) ma non parla: i due nodi per TalkBack sono sotto, disgiunti.
      modifier = Modifier.fluidMenuPointerRelay(anchor.handOff).clearAndSetSemantics { },
      backdrop = currentGlassBackdrop(),
      enabled = enabled,
      selected = false,
      shape = FluidCapsuleShape,
      onLongClick = { if (open(true)) haptics.play(FluidHapticEvent.Open) },
      size = FluidGlassSize.Regular,
      haptic = FluidHapticEvent.Tap,
      longClickHaptic = null,
      press = press,
      lensModifier = Modifier.drawWithContent { if (!anchor.hidden) drawContent() },
      onLensPlaced = { coordinates ->
        val rect = coordinates.unclippedBoundsInRoot()
        anchor.restBounds = rect
        entry.previewSize = rect
        entry.origin = { rect }
      },
      lens = height,
      style = style,
      lensWidth = width,
    ) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        FluidSplitFace(
          text = label.takeIf { icon == null }, icon = icon, color = LocalContentColor.current,
          iconSize = FluidMetrics.BarIconSize, modifier = Modifier.fillMaxSize(),
          actionZone = if (icon != null) FluidSplitButtonDefaults.IconZone else null,
        )
      }
    }
    // La freccia: un bersaglio suo, dal filo alla fine, di almeno 48 dp (fuori dal vetro verso la fine se serve). Il
    // tasto di vetro è largo almeno quanto il vetro: il filo sta a [targets.menuLeft] dal suo inizio.
    val coreWidth = if (width > FluidMetrics.TouchTarget) width else FluidMetrics.TouchTarget
    val offset = (coreWidth - width) / 2
    // L'azione per TalkBack: dall'inizio al filo, senza prendere il dito (il tocco lo prende il vetro, con la sua pressione).
    Box(
      Modifier
        .matchParentSize()
        .fluidTargetAt(offset + targets.actionLeft.dp, targets.actionWidth.dp)
        .semantics {
          role = Role.Button
          contentDescription = label
          if (!enabled) disabled()
          this.onClick(label = null) {
            haptics.play(FluidHapticEvent.Tap)
            primary()
            true
          }
          this.onLongClick(label = menuName) { open(false) }
        },
    )
    Box(
      Modifier
        .matchParentSize()
        .fluidTargetAt(offset + targets.menuLeft.dp, targets.menuWidth.dp)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          enabled = enabled,
          role = Role.Button,
        ) { if (open(false)) haptics.play(FluidHapticEvent.Open) }
        .semantics { contentDescription = menuName },
    )
  }
}
