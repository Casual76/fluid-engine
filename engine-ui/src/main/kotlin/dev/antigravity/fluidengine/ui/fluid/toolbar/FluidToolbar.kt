package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import dev.antigravity.fluidengine.ui.fluid.GlassContentRole
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurves
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.glassContentBlurRadiusPx
import dev.antigravity.fluidengine.ui.fluid.glassContentSigmaPx
import dev.antigravity.fluidengine.ui.fluid.FluidGlassQualifiedId
import dev.antigravity.fluidengine.ui.fluid.currentFluidGlassContainer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.max
import dev.antigravity.fluidengine.ui.fluid.FluidBadge
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlass
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAnimation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransitionWithShape
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidMenuRoundRectShape
import dev.antigravity.fluidengine.ui.fluid.fluidMenuPointerRelay
import dev.antigravity.fluidengine.ui.fluid.fluidMenuUsesField
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassInk
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentKey
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import dev.antigravity.fluidengine.ui.fluid.fluidGlassTransition
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.fluidGlass
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.fluidGlassId
import dev.antigravity.fluidengine.ui.fluid.fluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.icons.ArrowBackIosNew
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.ceil
import kotlin.math.roundToInt

// ================================================================================================================
// La DSL
// ================================================================================================================

/** Tiene le chiamate di uno scope della barra dentro il loro blocco: un `button` di un gruppo non finisce fuori. */
@DslMarker
annotation class FluidToolbarDsl

/**
 * Come la pagina mostra il suo titolo (piano «iOS in Android», F4A.2): `navigationBarTitleDisplayMode` di SwiftUI.
 *
 * Il titolo è la **pagina**, non un tasto: la barra lo tiene al centro e gli lascia il posto che i gruppi non occupano
 * (gli inset seguono la misura d'arrivo dei gruppi, non il vetro in volo: il titolo non balla mentre la barra cambia).
 */
enum class FluidToolbarTitleMode {
  /** Il titolo grande in cima alla lista, che scorre col contenuto e si aggancia alla barra: quello di sempre. */
  Large,

  /** Solo il titolo nella barra, sempre agganciato: per le pagine di dettaglio e i fogli. */
  Inline,

  /** Nessun titolo: la barra porta solo i tasti (una pagina che ha già il suo nome nel contenuto, un lettore). */
  None,
}

/**
 * Le identità che la DSL dà di serie ai tasti che hanno un ruolo solo per pagina: due pagine che hanno entrambe
 * «indietro» hanno **lo stesso** indietro (F4A.3 lo terrà fermo al pixel fra le due), e lo stesso vale per Chiudi e la
 * conferma. Un'app che ne vuole due diversi passa un `id` suo.
 */
object FluidToolbarIds {
  /** Il tasto indietro. */
  val Back: Any = FluidToolbarRoleId("back")

  /** Il tasto Chiudi. */
  val Close: Any = FluidToolbarRoleId("close")

  /** La conferma (la spunta tinta). */
  val Confirm: Any = FluidToolbarRoleId("confirm")
}

/** Un'identità di serie della DSL: un tipo suo, perché non si confonda con una stringa dell'app. */
internal data class FluidToolbarRoleId(val name: String) {
  override fun toString(): String = "fluid:$name"
}

/** Una chiave locale della DSL (spaziatori): non è un'identità da accoppiare fra due stati. */
internal data class FluidToolbarLocalId(val kind: String, val index: Int, val groupId: Any?)

/**
 * La barra in alto di una pagina, **dichiarata** (piano «iOS in Android», F4A.2): la pagina dice chi sono i suoi tasti e
 * che cosa fanno, la barra decide gruppi, vetri, misure e trabocco, e ogni cambio di stato della pagina (da «sfoglia» a
 * «seleziona», un'azione che diventa principale, un tasto che non ci sta più) si trasforma da solo sul piano flottante:
 * nessuna animazione scritta dalla pagina. È `toolbar { ToolbarItem(…) }` di SwiftUI.
 *
 * ```
 * FluidScreen(title = "Note", onBack = back, toolbar = {
 *   if (selecting) {
 *     button("share", FluidIcons.IosShare, "Condividi", onClick = share)
 *     button("delete", FluidIcons.Delete, "Elimina", onClick = delete, role = FluidButtonRole.Destructive)
 *     confirm(onClick = { selecting = false })
 *   } else {
 *     text("select", "Seleziona", onClick = { selecting = true })
 *     button("more", FluidIcons.MoreHoriz, "Altro", onClick = more)
 *   }
 * }) { … }
 * ```
 *
 * **Le regole che la barra applica da sola** (R1 B1–B10, R5 B11; il modello è `FluidToolbarModel`): le icone vicine
 * sullo stesso lato condividono un vetro; un testo, l'azione principale, la conferma, indietro e Chiudi hanno il loro;
 * uno [spacer] spezza un gruppo, un [flexibleSpacer] spinge e separa; al più tre gruppi, «Altro» compreso; quando il posto
 * non basta escono prima gli elementi a priorità bassa, e tutto ciò che sta in [overflow] è sempre nel menu «Altro».
 * L'[id] di un tasto è la sua identità fra due stati: **lo stesso id è lo stesso tasto**, che resta, si sposta o cambia
 * icona; un id nuovo nasce dal vicino o per lente.
 *
 * Chiamata dentro il blocco `toolbar` di [dev.antigravity.fluidengine.ui.fluid.FluidScreen] (e di
 * [dev.antigravity.fluidengine.ui.fluid.FluidCollapsingTopBar]): il blocco si rilegge a ogni composizione, come il
 * contenuto di un `if`.
 */
@FluidToolbarDsl
class FluidToolbarScope internal constructor() {
  internal val entries = ArrayList<FluidToolbarEntry>()
  internal var titleMode: FluidToolbarTitleMode = FluidToolbarTitleMode.Large
  internal var subtitle: String? = null
  internal var search: FluidSearchSpec? = null
  internal var overflowLabel: String? = null
  private var inOverflow = false
  private var groupId: Any? = null
  private var spacers = 0

  /**
   * Come si mostra il titolo ([FluidToolbarTitleMode]) e, se c'è, il sottotitolo: sotto il titolo grande finché non si
   * aggancia, e poi sotto il titolo agganciato, più piccolo.
   */
  fun title(mode: FluidToolbarTitleMode = FluidToolbarTitleMode.Large, subtitle: String? = null) {
    titleMode = mode
    if (subtitle != null) this.subtitle = subtitle
  }

  /** Il sottotitolo: sotto il titolo grande e, agganciato, sotto quello della barra. Null: nessuno. */
  fun subtitle(text: String?) {
    subtitle = text
  }

  /**
   * Il tasto indietro: il chevron in un tondo di vetro, a sinistra, gruppo a sé. Una [FluidScreen] con `onBack` e senza
   * un `back` dichiarato lo aggiunge da sola.
   */
  fun back(onClick: () -> Unit, label: String? = null, id: Any = FluidToolbarIds.Back) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Back, placement = FluidToolbarPlacement.Navigation,
      icon = FluidIcons.ArrowBackIosNew, label = label, onClick = onClick, defaultLabel = FluidToolbarDefaultLabel.Back,
      overflow = inOverflow,
    )
  }

  /**
   * Il tasto Chiudi: la X in un tondo di vetro, gruppo a sé, di serie a sinistra come l'indietro (in un foglio prende il
   * suo posto). Con [placement] la si mette altrove, per esempio `ConfirmationAction` a destra.
   */
  fun close(
    onClick: () -> Unit,
    label: String? = null,
    id: Any = FluidToolbarIds.Close,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Navigation,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Close, placement = placement, icon = FluidIcons.Close, label = label,
      onClick = onClick, defaultLabel = FluidToolbarDefaultLabel.Close, overflow = inOverflow,
    )
  }

  /**
   * La conferma: una **spunta tinta**, da sola, a destra (R1 B5). È l'azione principale della pagina (una sola): un gruppo
   * che la conteneva si divide quando un suo tasto lo diventa.
   */
  fun confirm(
    onClick: () -> Unit,
    label: String? = null,
    id: Any = FluidToolbarIds.Confirm,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Confirm, placement = FluidToolbarPlacement.ConfirmationAction,
      icon = FluidIcons.Check, label = label, onClick = onClick, enabled = enabled, tint = tint,
      defaultLabel = FluidToolbarDefaultLabel.Confirm, overflow = inOverflow,
    )
  }

  /**
   * L'azione principale con un'icona sua: vetro tinto (di serie l'accento), icona chiara, da sola a destra. Una sola per
   * barra, come la conferma, che è un'azione principale anche lei.
   */
  fun primary(
    id: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Primary, placement = FluidToolbarPlacement.PrimaryAction, icon = icon,
      label = label, onClick = onClick, enabled = enabled, tint = tint, overflow = inOverflow,
    )
  }

  /**
   * Un tasto con un'icona: vicino ad altre icone dello stesso lato divide il vetro con loro (un gruppo).
   *
   * @param id l'identità del tasto fra due stati della barra.
   * @param label il nome per TalkBack, e la voce del menu «Altro» se il tasto ci finisce.
   * @param placement dove sta: di serie a destra.
   * @param priority chi esce prima quando il posto non basta.
   * @param role conferma, chiusura, annullo o distruttivo: [FluidButtonRole.Destructive] colora l'icona e la voce del menu,
   *   [FluidButtonRole.Confirm] e [FluidButtonRole.Close] ne fanno una conferma o un Chiudi (gruppo a sé),
   *   [FluidButtonRole.Cancel] lo mette a sinistra.
   * @param tint il colore dell'icona; di serie l'inchiostro della barra.
   * @param selected la selezione **di questo tasto**: una pellicola dietro la sua icona e l'icona nell'accento, anche in un
   *   gruppo di più icone (non passa agli altri).
   * @param badge un numero sopra l'icona (0 o meno: niente); [badgeDot] un pallino senza numero.
   * @param sharedBackground falso: il tasto non ha vetro (un'icona sulla barra), e non entra in un gruppo.
   */
  fun button(
    id: Any,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
    role: FluidButtonRole? = null,
    tint: Color = Color.Unspecified,
    selected: Boolean = false,
    enabled: Boolean = true,
    badge: Int? = null,
    badgeDot: Boolean = false,
    sharedBackground: Boolean = true,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = role.toolbarRole(FluidToolbarRole.Action), placement = role.placementFor(placement),
      priority = priority, icon = icon, label = label, onClick = onClick, tint = tint, selected = selected,
      enabled = enabled, badgeCount = badge, badgeDot = badgeDot, sharedBackground = sharedBackground,
      destructive = role == FluidButtonRole.Destructive, overflow = inOverflow,
    )
  }

  /**
   * Un'azione che apre un menu al tocco o tenendo premuto. Nel secondo caso il dito può scegliere una
   * voce senza staccarsi. In un gruppo con sfondo condiviso è il suo vetro a diventare il pannello.
   */
  fun action(
    id: Any,
    icon: ImageVector,
    label: String,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.High,
    menu: () -> List<FluidContextAction>,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Action, placement = placement, priority = priority,
      icon = icon, label = label, onClick = null, overflow = inOverflow, menu = menu,
    )
  }

  /** Un tasto con una scritta («Seleziona», «Fine»): ha sempre il suo vetro, e non sta in un gruppo con le icone. */
  fun text(
    id: Any,
    text: String,
    onClick: () -> Unit,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
    role: FluidButtonRole? = null,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = if (role == FluidButtonRole.Confirm || role == FluidButtonRole.Close) role.toolbarRole(FluidToolbarRole.Text) else FluidToolbarRole.Text,
      placement = role.placementFor(placement), priority = priority, text = text, label = text, onClick = onClick,
      tint = tint, enabled = enabled, destructive = role == FluidButtonRole.Destructive, overflow = inOverflow,
    )
  }

  /**
   * Un elemento libero, **col suo vetro**, per ciò che la DSL non copre (un avatar, un indicatore): [content] sta in un
   * vetro di [width] × 44 dp (di serie un tondo). Ha la sua identità come gli altri e non si unisce a nessun gruppo.
   * Per TalkBack passare [label] e dichiarare qui [onClick] se il contenuto esegue un comando.
   * Il bersaglio della barra porta nome e azione anche quando quel comando passa nel menu «Altro».
   */
  fun item(
    id: Any,
    label: String? = null,
    width: Dp = FluidMetrics.GlassButtonLens,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
  ) {
    entries += FluidToolbarButtonEntry(
      id = id, role = FluidToolbarRole.Text, placement = placement, priority = priority, label = label,
      onClick = onClick, width = width, custom = content, overflow = inOverflow,
    )
  }

  /** Uno spazio fisso: spezza un gruppo in due vetri. Di serie la distanza fra due gruppi (12 dp); una misura più larga la sostituisce. */
  fun spacer(width: Dp = Dp.Unspecified) {
    entries += FluidToolbarSpacerEntry(FluidToolbarLocalId("spacer", spacers++, groupId), flexible = false, width = width)
  }

  /** Uno spazio flessibile: spinge ciò che segue all'altro estremo della barra. */
  fun flexibleSpacer() {
    entries += FluidToolbarSpacerEntry(FluidToolbarLocalId("flex", spacers++, groupId), flexible = true, width = Dp.Unspecified)
  }

  /**
   * Un gruppo esplicito: i suoi tasti stanno insieme, e non si uniscono ai vicini di fuori. [placement] e [priority]
   * valgono per i figli che non ne dicono uno loro.
   */
  fun group(
    id: Any,
    placement: FluidToolbarPlacement = FluidToolbarPlacement.Automatic,
    priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
    sharedBackground: Boolean = true,
    content: FluidToolbarScope.() -> Unit,
  ) {
    val inner = FluidToolbarScope().also {
      it.inOverflow = inOverflow
      it.groupId = id
    }.apply(content)
    entries += FluidToolbarGroupEntry(id, placement, priority, inOverflow, sharedBackground, inner.entries.toList())
  }

  /**
   * Ciò che sta **sempre** nel menu «Altro» (il tasto coi tre puntini, a destra), qualunque sia la larghezza. [label] è
   * il nome del tasto «Altro»; di serie le parole dell'engine.
   */
  fun overflow(label: String? = null, content: FluidToolbarScope.() -> Unit) {
    if (label != null) overflowLabel = label
    val previous = inOverflow
    inOverflow = true
    content()
    inOverflow = previous
  }

  /**
   * La ricerca della schermata sul piano condiviso con i comandi. Ha precedenza sul parametro di FluidScreen.
   */
  fun search(spec: FluidSearchSpec) {
    search = spec
  }
}

private fun FluidButtonRole?.toolbarRole(otherwise: FluidToolbarRole): FluidToolbarRole = when (this) {
  FluidButtonRole.Confirm -> FluidToolbarRole.Confirm
  FluidButtonRole.Close -> FluidToolbarRole.Close
  else -> otherwise
}

private fun FluidButtonRole?.placementFor(placement: FluidToolbarPlacement): FluidToolbarPlacement = when {
  placement != FluidToolbarPlacement.Automatic -> placement
  this == FluidButtonRole.Cancel -> FluidToolbarPlacement.CancellationAction
  this == FluidButtonRole.Destructive -> FluidToolbarPlacement.DestructiveAction
  else -> placement
}

/** Le parole di serie dei tasti con un ruolo, lette dalle etichette dell'engine al momento della resa. */
internal enum class FluidToolbarDefaultLabel { Back, Close, Confirm }

/** Una voce della DSL. */
internal sealed interface FluidToolbarEntry {
  val id: Any
}

/** Un tasto (icona, scritta, elemento libero) e tutto ciò che serve a disegnarlo. */
internal class FluidToolbarButtonEntry(
  override val id: Any,
  val role: FluidToolbarRole,
  val placement: FluidToolbarPlacement,
  val priority: FluidToolbarPriority = FluidToolbarPriority.Automatic,
  val icon: ImageVector? = null,
  val text: String? = null,
  val label: String? = null,
  val onClick: (() -> Unit)?,
  val tint: Color = Color.Unspecified,
  val selected: Boolean = false,
  val enabled: Boolean = true,
  val badgeCount: Int? = null,
  val badgeDot: Boolean = false,
  val sharedBackground: Boolean = true,
  val destructive: Boolean = false,
  val width: Dp? = null,
  val custom: (@Composable () -> Unit)? = null,
  val defaultLabel: FluidToolbarDefaultLabel? = null,
  val overflow: Boolean = false,
  val menu: (() -> List<FluidContextAction>)? = null,
) : FluidToolbarEntry

internal class FluidToolbarSpacerEntry(override val id: Any, val flexible: Boolean, val width: Dp) : FluidToolbarEntry

internal class FluidToolbarGroupEntry(
  override val id: Any,
  val placement: FluidToolbarPlacement,
  val priority: FluidToolbarPriority,
  val overflow: Boolean,
  val sharedBackground: Boolean,
  val children: List<FluidToolbarEntry>,
) : FluidToolbarEntry

/**
 * Ciò che una pagina ha dichiarato nel suo blocco `toolbar`: le voci, il modo del titolo, il sottotitolo, la ricerca.
 * Si rifà a ogni composizione; la barra ne ricava il modello puro ([fluidToolbarModel]).
 */
internal class FluidToolbarDeclaration(
  val entries: List<FluidToolbarEntry>,
  val titleMode: FluidToolbarTitleMode,
  val subtitle: String?,
  val search: FluidSearchSpec?,
  val overflowLabel: String?,
) {
  /** I tasti, anche quelli dentro i gruppi, per id. */
  val buttons: Map<Any, FluidToolbarButtonEntry> = buildMap<Any, FluidToolbarButtonEntry> { collect(this@FluidToolbarDeclaration.entries) }

  /** Solo le azioni effettivamente in basso riservano spazio, anche dentro gruppi annidati. */
  val hasBottomBar: Boolean = entries.any { it.declaresBottomAction(false) }

  /** L'indietro dichiarato, o null. */
  val back: FluidToolbarButtonEntry? get() = buttons.values.firstOrNull { it.role == FluidToolbarRole.Back }

  /** Se la pagina mette qualcosa al centro (`Principal`): il titolo agganciato cede il posto. */
  private var measuredPrincipal by mutableStateOf<Boolean?>(null)
  val hasPrincipal: Boolean get() = measuredPrincipal ?: entries.any { it.declaresPrincipal(false) }

  private fun FluidToolbarEntry.declaresPrincipal(inPrincipal: Boolean): Boolean = when (this) {
    is FluidToolbarSpacerEntry -> false
    is FluidToolbarButtonEntry -> !overflow && (placement == FluidToolbarPlacement.Principal ||
      placement == FluidToolbarPlacement.Automatic && inPrincipal)
    is FluidToolbarGroupEntry -> !overflow && children.any {
      it.declaresPrincipal(placement == FluidToolbarPlacement.Principal ||
        placement == FluidToolbarPlacement.Automatic && inPrincipal)
    }
  }

  fun measured(resolved: FluidToolbarResolved) {
    measuredPrincipal = resolved.groups.any { it.side == FluidToolbarSide.Center }
  }

  private fun MutableMap<Any, FluidToolbarButtonEntry>.collect(list: List<FluidToolbarEntry>) {
    for (entry in list) when (entry) {
      is FluidToolbarButtonEntry -> put(entry.id, entry)
      is FluidToolbarGroupEntry -> collect(entry.children)
      is FluidToolbarSpacerEntry -> Unit
    }
  }


}

/**
 * Esegue il blocco della barra. [onBack] è l'indietro della schermata: se la pagina non dichiara né un `back` né un
 * `close`, la barra lo mette in testa da sola, come faceva `FluidScreen(onBack = …)`.
 */
internal fun buildFluidToolbar(onBack: (() -> Unit)?, block: FluidToolbarScope.() -> Unit): FluidToolbarDeclaration {
  val scope = FluidToolbarScope().apply(block)
  val declared = FluidToolbarDeclaration(scope.entries.toList(), scope.titleMode, scope.subtitle, scope.search, scope.overflowLabel)
  val exits = declared.buttons.values.any { it.role == FluidToolbarRole.Back || it.role == FluidToolbarRole.Close }
  if (onBack == null || exits) return declared
  val back = FluidToolbarButtonEntry(
    id = FluidToolbarIds.Back, role = FluidToolbarRole.Back, placement = FluidToolbarPlacement.Navigation,
    icon = FluidIcons.ArrowBackIosNew, onClick = onBack, defaultLabel = FluidToolbarDefaultLabel.Back,
  )
  return FluidToolbarDeclaration(listOf(back) + declared.entries, scope.titleMode, scope.subtitle, scope.search, scope.overflowLabel)
}

/** La tinta di un tasto come numero ARGB per il modello, o null. */
internal fun fluidToolbarArgb(color: Color): Long? =
  if (color == Color.Unspecified) null else color.toArgb().toLong() and 0xFFFFFFFFL

/**
 * Il modello puro di [declaration] (F4A.1): tipi grafici tradotti in chiavi (il simbolo è l'`ImageVector`, la tinta un
 * ARGB, il badge un testo), le scritte e gli elementi liberi con la larghezza **misurata** da [textWidthDp] e da
 * [FluidToolbarButtonEntry.width]. [tintOf] dà la tinta risolta di ogni tasto (la distruttiva, la principale). Pura.
 */
internal fun fluidToolbarModel(
  declaration: FluidToolbarDeclaration,
  textWidthDp: (String) -> Float,
  tintOf: (FluidToolbarButtonEntry) -> Color = { it.tint },
  title: String? = null,
): FluidToolbarModel {
  fun convert(entry: FluidToolbarEntry): FluidToolbarItem = when (entry) {
    is FluidToolbarSpacerEntry -> FluidToolbarItem(
      id = entry.id,
      role = if (entry.flexible) FluidToolbarRole.FlexibleSpacer else FluidToolbarRole.FixedSpacer,
      widthDp = if (entry.width.isSpecified) entry.width.value else null,
      matchId = null,
    )
    is FluidToolbarGroupEntry -> FluidToolbarItem(
      id = entry.id,
      role = FluidToolbarRole.Group,
      placement = entry.placement,
      priority = entry.priority,
      overflow = entry.overflow,
      sharedBackground = entry.sharedBackground,
      children = entry.children.map(::convert),
    )
    is FluidToolbarButtonEntry -> FluidToolbarItem(
      id = entry.id,
      role = entry.role,
      placement = entry.placement,
      symbol = entry.icon,
      label = entry.text ?: entry.label,
      priority = entry.priority,
      overflow = entry.overflow,
      sharedBackground = entry.sharedBackground,
      badge = when {
        entry.badgeCount != null && entry.badgeCount > 0 -> FluidToolbarBadge(entry.badgeCount.toString())
        entry.badgeDot -> FluidToolbarBadge()
        else -> null
      },
      tintArgb = fluidToolbarArgb(tintOf(entry)),
      selected = entry.selected,
      enabled = entry.enabled,
      widthDp = when {
        entry.custom != null -> entry.width?.value
        entry.text != null -> textWidthDp(entry.text)
        else -> null
      },
      // Un elemento libero cambia contenuto da sé: la sua identità è anche la chiave del contenuto.
      contentKey = if (entry.custom != null) FluidToolbarCustomContent(entry.id) else null,
    )
  }
  return FluidToolbarModel(
    items = declaration.entries.map(::convert),
    titles = FluidToolbarTitles(
      inline = title.takeIf { declaration.titleMode != FluidToolbarTitleMode.None },
      large = title.takeIf { declaration.titleMode == FluidToolbarTitleMode.Large },
      subtitle = declaration.subtitle,
    ),
  )
}

private data class FluidToolbarCustomContent(val id: Any)

/** I numeri della barra in alto: i token di [FluidMetrics] (R6), letti qui una volta. */
internal fun fluidToolbarMetrics(): FluidToolbarMetrics = FluidToolbarMetrics(
  itemPitchDp = FluidMetrics.GroupItemPitch.value,
  groupSpacingDp = FluidMetrics.GroupSpacing.value,
  edgeMarginDp = FluidMetrics.ScreenEdgeMargin.value,
  buttonWidthDp = FluidMetrics.GlassButtonLens.value,
)

// ================================================================================================================
// L'identità dei vetri
// ================================================================================================================

/**
 * L'identità di un vetro della barra nel piano flottante: l'`id` del gruppo **alla sua nascita** ([group]), più un
 * numero che la distingue da un vetro più vecchio nato dallo stesso gruppo e ancora vivo. È l'`id` che il registro del
 * piano espone (`FluidGlassContainerDiagnostics.lastRest`, e il menu di un gruppo in F5A).
 *
 * Perché non l'id del gruppo di adesso: quello cambia quando cambiano i membri (F4A.1), e per il piano un id nuovo è un
 * vetro nuovo. Un gruppo che guadagna un'icona sarebbe un vetro che muore accanto a uno che nasce, invece del vetro
 * che si allarga (R7 04–05). La continuità la dà il grafo delle differenze ([fluidToolbarGlassIds]).
 */
internal data class FluidToolbarGlassId(val group: FluidToolbarGroupId, val serial: Int = 0) {
  /** Come lo scrive il registro del piano (`FluidGlassContainerDiagnostics.lastRest`): i membri alla nascita e il numero. */
  override fun toString(): String = "FluidToolbarGlassId(${group.members.joinToString(",")}#$serial)"
}

/**
 * Le identità dei vetri di [resolved], date quelle del suo stato di prima ([previous], [before]) e la differenza
 * [diff] fra i due (F4A.1): un gruppo d'arrivo eredita il vetro del gruppo di partenza con cui divide **più tasti
 * accoppiati restando** (`Matched`): così un gruppo che cresce o cala è lo stesso vetro, una divisione lascia il vetro
 * alla parte più grande (le altre nascono dal vicino), una fusione lo prende dalla parte più grande (le altre
 * confluiscono), e un tasto che il modello fa ripartire per lente (troppo lontano, incrociato) non trascina il suo vetro.
 * Chi non eredita ha un vetro nuovo, mai uguale a uno di prima. Pura.
 */
internal fun fluidToolbarGlassIds(
  resolved: FluidToolbarResolved,
  previous: Map<FluidToolbarGroupId, FluidToolbarGlassId> = emptyMap(),
  before: FluidToolbarResolved? = null,
  diff: FluidToolbarDiff? = null,
): Map<FluidToolbarGroupId, FluidToolbarGlassId> {
  val result = LinkedHashMap<FluidToolbarGroupId, FluidToolbarGlassId>()
  if (before != null && diff != null) {
    // Quanti tasti restano (Matched) fra ogni gruppo di prima e ogni gruppo d'arrivo.
    val weights = LinkedHashMap<Pair<FluidToolbarGroupId, FluidToolbarGroupId>, Int>()
    for (element in diff.elements) {
      if (element.change != FluidToolbarElementChange.Matched) continue
      val from = element.from?.groupId ?: continue
      val to = element.to?.groupId ?: continue
      weights.merge(from to to, 1, Int::plus)
    }
    val order = resolved.groups.map { it.id }
    val taken = HashSet<FluidToolbarGroupId>()
    for ((edge, _) in weights.entries.sortedWith(compareByDescending<Map.Entry<Pair<FluidToolbarGroupId, FluidToolbarGroupId>, Int>> { it.value }
      .thenBy { order.indexOf(it.key.second) })) {
      val (from, to) = edge
      if (from in taken || to in result) continue
      val inherited = previous[from] ?: continue
      result[to] = inherited
      taken += from
    }
  }
  val used = HashSet<FluidToolbarGlassId>(previous.values)
  used += result.values
  for (group in resolved.groups) {
    if (group.id in result) continue
    var serial = 0
    while (FluidToolbarGlassId(group.id, serial) in used) serial++
    val fresh = FluidToolbarGlassId(group.id, serial)
    result[group.id] = fresh
    used += fresh
  }
  return result
}

/**
 * La memoria della barra fra due composizioni: l'ultimo stato risolto, le identità dei suoi vetri e i tasti che stanno
 * uscendo dalla barra. Conserva il posto da cui sfumano e passa il glifo allo slot quando il vetro si posa.
 * Si aggiorna in composizione, una volta per stato risolto diverso (le ricomposizioni con lo stesso stato non la toccano).
 */
@Stable
internal class FluidToolbarMemory {
  var resolved: FluidToolbarResolved? = null
    private set
  var glassIds: Map<FluidToolbarGroupId, FluidToolbarGlassId> = emptyMap()
    private set

  /** Gli id dei tasti che c'erano nello stato di prima: un tasto nuovo in un vetro che resta entra sfocato. */
  var previousIds: Set<Any> = emptySet()
    private set

  /** Se la barra ha già mostrato uno stato: al primo nessuno entra sfocato (la pagina compare, non cambia). */
  var settled: Boolean = false
    private set

  /** I vetri dello stato di prima: un tasto nuovo entra sfocato solo in un vetro che c'era. */
  var previousGlass: Set<FluidToolbarGlassId> = emptySet()
    private set

  var ghosts by mutableStateOf<List<FluidToolbarGhost>>(emptyList())
    private set

  // La registrazione del gruppo conserva riferimenti ai layer figli anche quando il gruppo esce.
  var transfers by mutableStateOf(emptyMap<Any, FluidToolbarTransfer>())
    private set

  var arrivals by mutableStateOf(emptyMap<Any, FluidToolbarElement>())
    private set
  var searchArrivals: Set<Any> = emptySet()
    private set
  private var previousSearchActive: Boolean? = null
  private var ghostSerial = 0

  fun update(next: FluidToolbarResolved, motion: FluidToolbarMotion, searchActive: Boolean? = null) {
    val before = resolved
    val searchChanged = searchActive != null && previousSearchActive != null && searchActive != previousSearchActive
    previousSearchActive = searchActive
    if (before == next) return
    if (before == null) {
      glassIds = fluidToolbarGlassIds(next)
      previousIds = emptySet()
    } else {
      val diff = diffFluidToolbar(before, next, FluidToolbarTrigger.State, motion)
      val ids = fluidToolbarGlassIds(next, glassIds, before, diff)
      // Il rinculo del vetro può trascinare anche un posto fermo quando cambia un gruppo vicino
      // (Note → Nota). Tienilo all'ancora fino alla posa. Il badge non muove il vetro.
      val glassChanges = before.groups.size != next.groups.size || before.groups.indices.any { index ->
        val a = before.groups[index]
        val b = next.groups[index]
        a.id != b.id || a.leftDp != b.leftDp || a.widthDp != b.widthDp ||
          fluidToolbarGlassContentKey(a) != fluidToolbarGlassContentKey(b)
      }
      // Solo una corrispondenza del modello può portare lo stesso glifo fra due posti.
      val moving = diff.elements.mapNotNull { change ->
        if (change.change != FluidToolbarElementChange.Matched) return@mapNotNull null
        val start = change.from ?: return@mapNotNull null
        val end = change.to ?: return@mapNotNull null
        val from = glassIds[start.groupId] ?: return@mapNotNull null
        val to = ids[end.groupId] ?: return@mapNotNull null
        if (motion != FluidToolbarMotion.Full || start.inOverflow || end.inOverflow ||
          start.item.symbol == null || end.item.role == FluidToolbarRole.Overflow || start.item.content != end.item.content) return@mapNotNull null
        val sourceGroup = before.groups.first { it.id == start.groupId }
        val targetGroup = next.groups.first { it.id == end.groupId }
        val leavesSingle = sourceGroup.items.size == 1
        val entersSingle = targetGroup.items.size == 1
        val cross = from != to
        val resized = sourceGroup.items.map { it.id } != targetGroup.items.map { it.id }
        val active = transfers[end.item.id]
        val inkChange = start.item.tintArgb != end.item.tintArgb || start.item.selected != end.item.selected
        if (!glassChanges && !cross && !resized && start.centerDp == end.centerDp && !inkChange) {
          return@mapNotNull active?.let { end.item.id to it }
        }
        val trip = FluidToolbarTransfer(if (cross && leavesSingle) from else to,
          cross && leavesSingle, cross && entersSingle, start.centerDp, end.centerDp, transfers[end.item.id]?.lastCenter,
          !cross && resized, targetGroup.widthDp, start.item)
        end.item.id to trip
      }.toMap()
      transfers = moving
      val leaving = diff.elements.filter { change ->
        change.from?.inOverflow == false && (change.to == null || change.to.inOverflow)
      }.mapNotNull { it.from }
      val entering = diff.elements.mapNotNull { change ->
        change.to?.takeIf { !it.inOverflow && (change.from == null || change.from.inOverflow) }
      }
      val searchSwap = searchChanged && motion == FluidToolbarMotion.Full
      val fresh = leaving.filter { it.item.contentKey == null }.map { old ->
        FluidToolbarGhost(ghostSerial++, old, glassIds.getValue(old.groupId!!), glassIds[old.groupId] in ids.values,
          searchSwap)
      }
      val visible = next.elements.filterNot { it.inOverflow }.mapTo(HashSet()) { it.item.id }
      ghosts = ghosts.filter { it.element.item.id !in visible && fresh.none { new -> new.element.item.id == it.element.item.id } } + fresh
      // Uno scambio avvia i due contenuti sullo stesso orologio, anche se cambia il vetro.
      val newArrivals = entering.filter { end ->
        end.item.contentKey == null && (searchSwap || leaving.any { it.side == end.side })
      }.associateBy { it.item.id }
      val keptArrivals = arrivals.filter { (id, element) -> next.element(id) == element }
      searchArrivals = (searchArrivals intersect keptArrivals.keys) + if (searchSwap) newArrivals.keys else emptySet()
      arrivals = keptArrivals + newArrivals
      previousIds = before.elements.filter { !it.inOverflow }.mapTo(HashSet()) { it.item.id }
      previousGlass = glassIds.values.toSet()
      glassIds = ids
      settled = true
    }
    resolved = next
  }

  fun finishTransfers(done: Map<Any, FluidToolbarTransfer>) {
    transfers = transfers.filter { (id, trip) -> done[id] !== trip }
  }

  fun finishArrival(element: FluidToolbarElement) {
    if (arrivals[element.item.id] === element) {
      searchArrivals = searchArrivals - element.item.id
      arrivals = arrivals - element.item.id
    }
  }

  fun forget(ghost: FluidToolbarGhost) {
    ghosts = ghosts.filter { it.serial != ghost.serial }
  }
}

/** Un solo simbolo per tutta la traversata. Le coordinate fisiche includono l'oltrepasso del vetro. */
internal class FluidToolbarTransfer(
  val glass: FluidToolbarGlassId, val leavesSingle: Boolean, val entersSingle: Boolean,
  val startDp: Float, val endDp: Float, val interruptedCenter: Float?,
  val softened: Boolean, private val targetWidthDp: Float, val startItem: FluidToolbarItem,
) {
  var lastCenter: Float? = null
    private set
  private var firstLobe: Float? = null
  private var firstWidthDp: Float? = null

  /** Anche in un'inversione il posto d'arrivo resta quello misurato dalla Row, con i suoi arrotondamenti. */
  val sameSlot: Boolean get() = startDp == endDp

  fun changesInkInPlace(end: FluidToolbarItem): Boolean =
    startDp == endDp && startItem.isPrimary == end.isPrimary &&
      (startItem.selected != end.selected || startItem.tintArgb != end.tintArgb)

  /** La stella deve acquistare l'inchiostro neutro mentre la sua pellicola diventa trasparente. */
  fun inkFraction(end: FluidToolbarItem, filmAlpha: Float?, phase: Float): Float {
    if (filmAlpha == null || end.isPrimary == startItem.isPrimary) return GlassContentCurves.tint(phase)
    val primary = if (end.isPrimary) end else startItem
    val full = primary.tintArgb?.let { ((it ushr 24) and 255L) / 255f } ?: 1f
    if (full <= 0f) return GlassContentCurves.tint(phase)
    val film = (filmAlpha / full).coerceIn(0f, 1f)
    return if (end.isPrimary) film else 1f - film
  }

  fun center(start: Float, end: Float, lobe: Float?, widthDp: Float?, phase: Float): Float {
    // Quando il posto coincide in entrambe le barre, il rinculo resta al vetro.
    if (start == end && (interruptedCenter == null || interruptedCenter == end)) {
      return end.also { lastCenter = it }
    }
    val u = phase.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    val from = interruptedCenter ?: start
    if (firstLobe == null && lobe != null) firstLobe = lobe
    if (firstWidthDp == null) firstWidthDp = widthDp
    val initial = firstWidthDp
    val track = when {
      // Il posto che resta nello stesso gruppo segue la misura del vetro che lo porta.
      // Così lascia posto all'icona nuova durante l'allargamento della capsula.
      softened && initial != null && widthDp != null && kotlin.math.abs(targetWidthDp - initial) > 0.001f ->
        from + (end - from) * ((widthDp - initial) / (targetWidthDp - initial)).coerceIn(0f, 1f)
      lobe == null -> from + (end - from) * u
      leavesSingle -> {
        val corrected = lobe + (from - (firstLobe ?: lobe)) * (1f - u)
        corrected + (end - corrected) * u
      }
      entersSingle -> from + (lobe - from) * u
      else -> from + (end - from) * u
    }
    return track.also { lastCenter = it }
  }
}

/** L'uscita conserva le coordinate logiche della barra di partenza, anche quando il vetro cambia origine. */
internal data class FluidToolbarGhost(
  val serial: Int,
  val element: FluidToolbarElement,
  val glass: FluidToolbarGlassId,
  val glassStays: Boolean,
  val searchSwap: Boolean = false,
)

// ================================================================================================================
// La resa sul piano
// ================================================================================================================

/** Le misure della barra dichiarata che non sono già token di [FluidMetrics]. */
internal object FluidToolbarDefaults {
  /** Il titolo agganciato lascia questa altezza al sottotitolo (scelta F4A.2). */
  val SubtitleLift: Dp = 16.dp

  /** L'inchiostro secondario della barra (scelta F4A.2). */
  const val SubtitleInk: Float = 0.6f

  /** Il margine di una scritta dentro il suo vetro: la scritta di Apple nella barra ha ~14–16 pt di aria per lato. */
  val TextPadding: Dp = 16.dp

  /** Quanto la pellicola di selezione rientra dal riquadro del tasto: un tondo di 36 dp in uno da 44. */
  val SelectionInset: Dp = 4.dp

  /** L'opacità del contenuto di un tasto spento (la stessa dei controlli Material). */
  const val DisabledAlpha: Float = 0.38f

  /** Dove sta il badge rispetto all'angolo in alto a destra del tasto: sull'angolo dell'icona da 26 in un tondo da 44. */
  val BadgeOffset: Dp = 6.dp

  /** La scritta dei tasti di testo: quella del corpo, un poco più piena, come i tasti della barra di Apple. */
  @Composable
  fun textStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
}

/** Ciò che la barra disegna e misura dopo la risoluzione: i bordi interni dei gruppi ai due lati, in px. */
internal data class FluidToolbarExtent(val startPx: Int, val endPx: Int)

/**
 * La barra dichiarata, sul piano flottante (F4A.2): un [FluidGlassContainer] alto quanto la riga dei tasti, ogni gruppo
 * risolto un pezzo (`fluidGlass` con l'identità del suo vetro, [FluidToolbarGlassId]), le icone a 26 dp, passo 44 dp
 * nel gruppo, 12 dp fra due gruppi, margine 16 dp. Ogni tasto ha la sua chiave di contenuto
 * ([FluidGlassContentTransition]): quando il modello cambia i gruppi cambiano larghezza in continuo, nascono dal vicino,
 * confluiscono, compaiono per lente e le icone si incrociano sfocandosi, **senza che la pagina scriva altro**.
 *
 * [onExtent] riceve, a ogni stato risolto, quanto occupano i gruppi dal bordo ai due lati: la misura d'**arrivo**, non
 * il vetro in volo, così il titolo agganciato non balla mentre i gruppi cambiano larghezza.
 */
@Composable
internal fun FluidToolbarBar(
  declaration: FluidToolbarDeclaration,
  modifier: Modifier = Modifier,
  title: String? = null,
  onExtent: ((FluidToolbarExtent) -> Unit)? = null,
  bar: FluidToolbarBar = FluidToolbarBar.Top,
) {
  val density = LocalDensity.current
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val model = fluidToolbarModelOf(declaration, title)
  val metrics = if (bar == FluidToolbarBar.Bottom) fluidBottomBarMetrics() else fluidToolbarMetrics()
  BoxWithConstraints(modifier.fillMaxWidth().height(metrics.buttonWidthDp.dp).fluidToolbarMinimizeLayer(towardsBottom = bar == FluidToolbarBar.Bottom)) {
    val width = if (constraints.hasBoundedWidth) maxWidth.value else FluidToolbarUnboundedWidthDp
    val search = LocalToolbarSearch.current
    if (search != null && search.owns(bar) && search.hasDeclaration) {
      val pose = search.layout(model, width, metrics)
      SideEffect {
        if (bar != FluidToolbarBar.Bottom) declaration.measured(pose.neighbours)
        onExtent?.invoke(pose.extent(density.density))
      }
      return@BoxWithConstraints
    }
    val active = search?.spec?.active ?: (LocalPrincipalSearchActive.current || declaration.search?.active == true)
    val resolved = remember(model, width, bar, active) { resolveFluidToolbar(model, width, active, metrics, bar) }
    if (bar != FluidToolbarBar.Bottom) SideEffect { declaration.measured(resolved) }
    if (onExtent != null) {
      val extent = fluidToolbarExtent(resolved, density.density)
      SideEffect { onExtent(extent) }
    }
    if (reducedMotion) {
      AnimatedContent(
        targetState = resolved,
        transitionSpec = {
          (fadeIn(FluidMotion.crossFade()) togetherWith fadeOut(FluidMotion.crossFade())).using(null)
        },
        label = "fluid toolbar crossfade",
      ) { shown ->
        val saved = remember(shown) { arrayOf(declaration) }
        val interactive = shown == resolved
        if (interactive) saved[0] = declaration
        FluidToolbarGroups(saved[0], shown, reducedMotion = true, interactive = interactive)
      }
    } else {
      FluidToolbarGroups(declaration, resolved, reducedMotion = false, interactive = true)
    }
  }
}

@Composable
internal fun FluidToolbarGroups(
  declaration: FluidToolbarDeclaration,
  resolved: FluidToolbarResolved,
  reducedMotion: Boolean,
  interactive: Boolean,
  searchActive: Boolean? = null,
  searchField: (@Composable (FluidGlassNamespace) -> Unit)? = null,
) {
    val memory = remember { FluidToolbarMemory() }
    memory.update(resolved, if (reducedMotion) FluidToolbarMotion.Reduced else FluidToolbarMotion.Full, searchActive)
    val searchInk = if (searchActive != null && !reducedMotion) rememberToolbarSearchInk(searchActive) else null
    val selection = HashMap<Any, State<Float>>()
    val duration = (GlassContentDefaults.DurationMs * LocalFluidMotionPolicy.current.durationScale).toInt()
    for (element in resolved.elements) {
      if (element.inOverflow) continue
      key(element.item.id) {
        selection[element.item.id] = animateFloatAsState(
          targetValue = if (element.item.selected) 1f else 0f,
          animationSpec = tween(duration, easing = FluidToolbarSelectionEasing),
          label = "toolbar selection",
        )
      }
    }
    val namespace = rememberFluidGlassNamespace()
    val menuSource = remember { FluidToolbarMenuSource() }
    LaunchedEffect(menuSource.handoff) {
      if (menuSource.instant) {
        // Il piano pubblica il riposo al prossimo frame e monta i grappoli al successivo.
        repeat(2) { withFrameNanos { } }
        menuSource.instant = false
      }
    }
    // Il passaggio al menu salta l'animazione del piano per due fotogrammi (F5A.2); fuori da quello, la ricerca usa la
    // sua (F4B.2) e il resto quella di serie.
    val animation = when {
      menuSource.instant -> FluidGlassAnimation.None
      searchField == null -> FluidGlassAnimation.Default
      else -> ToolbarSearchAnimation
    }
    FluidGlassContainer(Modifier.fillMaxSize(), animation = animation) {
      val plane = currentFluidGlassContainer(FluidCapsuleShape)
      Box(Modifier.fillMaxSize()) {
        for (group in resolved.groups) {
          val glass = memory.glassIds[group.id] ?: continue
          key(glass) {
            FluidToolbarGroupPiece(
              group = group,
              glass = glass,
              namespace = namespace,
              declaration = declaration,
              resolved = resolved,
              memory = memory,
              selection = selection,
              interactive = interactive,
              menuSource = menuSource,
              modifier = Modifier.align(Alignment.CenterStart).offset(x = group.leftDp.dp),
            )
          }
        }
        searchField?.invoke(namespace)
        val trips = memory.transfers
        val arrivals = memory.arrivals
        val density = LocalDensity.current
        val effects = remember(density) { FluidToolbarEffects(density.density) }
        // Il piano può aspettare la prima misura prima di pubblicare il viaggio.
        // Dopo la fine dell'inchiostro e della geometria il proprietario torna allo slot.
        if (trips.isNotEmpty()) LaunchedEffect(trips, plane, duration) {
          withFrameNanos { }
          delay(duration.toLong())
          do { withFrameNanos { } } while (plane?.stage != null || plane?.measurePending == true)
          memory.finishTransfers(trips)
        }

        for (ghost in memory.ghosts) key(ghost.serial) {
          val element = ghost.element
          FluidToolbarFadingFace(element, ghost.glass, namespace, plane, entering = false,
            clipToGlass = ghost.glassStays, effects = effects, height = resolved.metrics.buttonWidthDp.dp,
            searchInk = searchInk.takeIf { ghost.searchSwap },
            modifier = Modifier.align(Alignment.CenterStart).offset(x = element.leftDp.dp)) { memory.forget(ghost) }
        }
        for (element in arrivals.values) key(element) {
          val glass = memory.glassIds.getValue(element.groupId!!)
          FluidToolbarFadingFace(element, glass, namespace, plane, entering = true,
            clipToGlass = true, effects = effects, height = resolved.metrics.buttonWidthDp.dp,
            searchInk = searchInk.takeIf { element.item.id in memory.searchArrivals },
            modifier = Modifier.align(Alignment.CenterStart).offset(x = element.leftDp.dp),
            group = resolved.groups.first { it.id == element.groupId }) { memory.finishArrival(element) }
        }
        for ((id, trip) in trips) {
          val entry = declaration.buttons[id] ?: continue
          val icon = entry.icon ?: continue
          val element = resolved.element(id) ?: continue
          key(id) {
            FluidToolbarTravellingButton(trip, element, entry, icon, resolved, selection, interactive,
              namespace, plane, effects, Modifier.align(Alignment.CenterStart))
          }
        }
      }
    }
}

@Composable
private fun FluidToolbarTravellingButton(
  trip: FluidToolbarTransfer, element: FluidToolbarElement, entry: FluidToolbarButtonEntry, icon: ImageVector,
  resolved: FluidToolbarResolved, selection: Map<Any, State<Float>>, interactive: Boolean,
  namespace: FluidGlassNamespace, plane: FluidGlassContainerState?, effects: FluidToolbarEffects, modifier: Modifier,
) {
  val id = element.item.id
  val ink = LocalContentColor.current
  val scheme = MaterialTheme.colorScheme
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val density = LocalDensity.current
  val size = fluidToolbarIconSize(element.item.role)
  val stationary = trip.sameSlot
  val restX = if (stationary) fluidToolbarIconCenterPx(resolved, element, density.density, rtl) else 0f
  val glyphWidthPx = with(density) { size.roundToPx() }
  fun color(item: FluidToolbarItem): Color = when {
    item.isPrimary -> scheme.onPrimary
    item.selected -> scheme.primary
    item.tintArgb != null -> Color(item.tintArgb)
    else -> ink
  }
  val soft = trip.softened && FluidToolbarTuning.keptContent == FluidToolbarKeptContent.Soft
  val startColor = color(trip.startItem)
  val endColor = color(element.item)
  val selectionState = selection[id]
  val selectionChanges = trip.startItem.selected != element.item.selected &&
    !trip.startItem.isPrimary && !element.item.isPrimary
  val localInk = if (trip.changesInkInPlace(element.item) && !selectionChanges) {
    val phase = remember(trip) { Animatable(0f) }
    val duration = GlassContentDefaults.DurationMs * LocalFluidMotionPolicy.current.durationScale
    LaunchedEffect(phase, duration) {
      phase.animateTo(1f, tween(duration.toInt().coerceAtLeast(0), easing = LinearEasing))
    }
    phase
  } else null
  val qualified = remember(namespace, trip) { FluidGlassQualifiedId(namespace, trip.glass) }
  val labels = LocalFluidLabels.current
  val touchable = interactive && entry.onClick != null
  val haptics = LocalFluidHaptics.current
  Box(modifier.requiredSize(FluidMetrics.TouchTarget).graphicsLayer {
    fun center(x: Float) = (if (rtl) resolved.widthDp - x else x) * this.density
    val flight = plane?.flightOf(qualified)
    val u = flight?.phase ?: if (plane?.measurePending == true) 0f else 1f
    val x = if (stationary) {
      trip.center(restX, restX, null, null, u)
    } else {
      trip.center(center(trip.startDp), center(trip.endDp), flight?.bounds?.center?.x,
        flight?.bounds?.width?.div(this.density), u)
    }
    val widthPx = (resolved.widthDp * this.density).roundToInt()
    val localCenter = if (stationary) fluidToolbarPlacedIconCenterPx(this.size.width.roundToInt(), glyphWidthPx)
      else this.size.width / 2f
    val base = (if (rtl) widthPx - this.size.width else 0f) + localCenter
    translationX = x - base
    alpha = if (entry.enabled) 1f else FluidToolbarDefaults.DisabledAlpha
  }.then(if (touchable) Modifier.clickable(
    interactionSource = remember { MutableInteractionSource() }, indication = null,
    enabled = entry.enabled, role = Role.Button,
  ) {
    haptics.play(FluidHapticEvent.Tap)
    entry.onClick?.invoke()
  }.semantics {
    contentDescription = entry.accessibleName(labels.back, labels.close, labels.confirm)
    if (element.item.selected) selected = true
    element.item.badge?.text?.let { stateDescription = it }
  } else Modifier.clearAndSetSemantics { }), contentAlignment = Alignment.Center) {
    Box(Modifier.fillMaxSize().graphicsLayer {
      if (startColor != endColor) compositingStrategy = CompositingStrategy.Offscreen
      plane?.flightOf(qualified) // abbonamento al fotogramma del piano anche per una sola sfocatura
      val elapsed = plane?.contentTimeMs ?: Double.POSITIVE_INFINITY
      val blur = if (soft && android.os.Build.VERSION.SDK_INT >= 31) glassContentBlurRadiusPx(
        glassContentSigmaPx(GlassContentCurves.softBlur((elapsed / GlassContentDefaults.DurationMs).toFloat()) *
          GlassContentDefaults.PeakBlur * GlassContentDefaults.KeptSoftness, size.toPx()))
        else 0f
      renderEffect = effects.at(blur)
    }.then(if (startColor == endColor) Modifier else Modifier.drawWithContent {
      drawContent()
      val flight = plane?.flightOf(qualified)
      val u = flight?.phase ?: if (plane?.measurePending == true) 0f else 1f
      val inkFraction = if (selectionChanges && selectionState != null) {
        if (element.item.selected) selectionState.value else 1f - selectionState.value
      } else trip.inkFraction(element.item, flight?.let { plane?.filmAlpha(it) }, localInk?.value ?: u)
      drawRect(lerp(startColor, endColor, inkFraction), blendMode = BlendMode.SrcIn)
    }), contentAlignment = Alignment.Center) {
      FluidIcon(icon, null, tint = endColor, size = size)
    }
  }
}

/** Stessa somma di interi di offset, Row e allineamento del glifo a riposo, anche in RTL. */
internal fun fluidToolbarIconCenterPx(
  resolved: FluidToolbarResolved,
  element: FluidToolbarElement,
  density: Float,
  rtl: Boolean,
): Float {
  val group = resolved.groups.first { it.id == element.groupId }
  fun px(dp: Float) = (dp * density).roundToInt()
  val groupWidth = px(group.widthDp)
  val rowStart = px(group.leftDp)
  val preceding = group.items.takeWhile { it.id != element.item.id }
    .sumOf { item -> px(resolved.element(item.id)!!.widthDp) }
  val slotWidth = px(element.widthDp)
  // Row può stringere l'ultimo posto di un pixel per via degli arrotondamenti dei suoi figli.
  val measuredSlot = slotWidth.coerceAtMost((groupWidth - preceding).coerceAtLeast(0))
  val iconWidth = px(fluidToolbarIconSize(element.item.role).value).coerceAtMost(measuredSlot)
  val middle = fluidToolbarPlacedIconCenterPx(measuredSlot, iconWidth)
  return if (rtl) px(resolved.widthDp) - rowStart - preceding - measuredSlot + middle
  else rowStart + preceding + middle
}

internal fun fluidToolbarPlacedIconCenterPx(slotWidth: Int, iconWidth: Int): Float =
  ((slotWidth - iconWidth) / 2f).roundToInt() + iconWidth / 2f

internal val FluidToolbarSelectionEasing = androidx.compose.animation.core.Easing(GlassContentCurves::tint)

/**
 * Il modello puro di [declaration] col tema e le misure di adesso: le scritte misurate con lo stile dei tasti, le tinte
 * risolte (la principale, la distruttiva). È ciò che [FluidToolbarBar] risolve; serve anche a chi deve conoscere la
 * geometria di una barra senza disegnarla (la barra dell'host fra due pagine, F4A.3).
 */
@Composable
internal fun fluidToolbarModelOf(declaration: FluidToolbarDeclaration, title: String?): FluidToolbarModel {
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val textStyle = FluidToolbarDefaults.textStyle()
  val scheme = MaterialTheme.colorScheme
  return fluidToolbarModel(
    declaration = declaration,
    textWidthDp = { text ->
      val px = measurer.measure(text, textStyle, maxLines = 1, softWrap = false).size.width
      ceil(px / density.density) + FluidToolbarDefaults.TextPadding.value * 2f
    },
    tintOf = { fluidToolbarTint(it, scheme.primary, scheme.error) },
    title = title,
  )
}

/** La larghezza su cui si risolve una barra senza limite (stretta ai suoi gruppi in una riga che non la limita). */
private const val FluidToolbarUnboundedWidthDp = 10_000f

/**
 * Quanto occupano i gruppi dal bordo, ai due lati, nello stato [resolved] (in px): fino al bordo interno del gruppo più
 * interno di ogni lato; zero se il lato è vuoto. Pura.
 */
internal fun fluidToolbarExtent(resolved: FluidToolbarResolved, density: Float): FluidToolbarExtent {
  val leading = resolved.groups.filter { it.side == FluidToolbarSide.Leading }.maxOfOrNull { it.leftDp + it.widthDp } ?: 0f
  val trailing = resolved.groups.filter { it.side == FluidToolbarSide.Trailing }.minOfOrNull { it.leftDp }
  val end = if (trailing == null) 0f else resolved.widthDp - trailing
  return FluidToolbarExtent((leading * density).roundToInt(), (end * density).roundToInt())
}

/** La tinta risolta di un tasto: la sua, o l'accento per la principale, o il colore d'errore per una distruttiva. */
internal fun fluidToolbarTint(entry: FluidToolbarButtonEntry, accent: Color, error: Color): Color = when {
  entry.tint != Color.Unspecified -> entry.tint
  entry.role == FluidToolbarRole.Primary || entry.role == FluidToolbarRole.Confirm -> accent
  entry.destructive -> error
  else -> Color.Unspecified
}

/** Un gruppo risolto: un vetro solo (un pezzo del piano) con dentro i suoi tasti, uno per posto. */
@Composable
private fun FluidToolbarGroupPiece(
  group: FluidToolbarGroup,
  glass: FluidToolbarGlassId,
  namespace: FluidGlassNamespace,
  declaration: FluidToolbarDeclaration,
  resolved: FluidToolbarResolved,
  memory: FluidToolbarMemory,
  selection: Map<Any, State<Float>>,
  interactive: Boolean,
  menuSource: FluidToolbarMenuSource,
  modifier: Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val single = group.items.singleOrNull()
  val primary = single?.isPrimary == true
  val primaryEntry = single?.let { declaration.buttons[it.id] }
  val leased = menuSource.anchor(glass)
  val surface = when {
    leased != null -> FluidGlass.Identity
    !group.sharedBackground -> FluidGlass.Identity
    // La principale è un vetro tinto (la tinta è contenuto del vetro: R7 A4); la sua icona è chiara.
    primary -> FluidGlass.Regular.tint(primaryEntry?.let { fluidToolbarTint(it, scheme.primary, scheme.error) } ?: scheme.primary).interactive()
    else -> FluidGlass.Regular.interactive()
  }
  Row(
    modifier
      .then(if (leased?.groupBounds != null) Modifier.graphicsLayer {
        val round = leased.groupClip()
        if (round != null) shape = FluidMenuRoundRectShape(round)
        clip = round != null
      } else Modifier)
      .fluidGlassId(glass, namespace)
      .fluidGlassTransition(fluidToolbarGlassTransition(group))
      .fluidGlassContentAnchor(when (group.side) {
        FluidToolbarSide.Leading -> FluidGlassContentAnchor.Start
        FluidToolbarSide.Center -> FluidGlassContentAnchor.Center
        FluidToolbarSide.Trailing -> FluidGlassContentAnchor.End
      })
      // Che cosa porta il vetro (F4A.5b): un tondo che cambia simbolo respira, come in Apple.
      .fluidGlassContentKey(fluidToolbarGlassContentKey(group))
      .fluidGlass(surface, FluidCapsuleShape)
      .size(group.widthDp.dp, resolved.metrics.buttonWidthDp.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    for (item in group.items) {
      key(item.id) {
        val element = resolved.element(item.id)
        FluidToolbarSlot(
          item = item,
          widthDp = element?.widthDp ?: resolved.metrics.buttonWidthDp,
          entry = declaration.buttons[item.id],
          declaration = declaration,
          resolved = resolved,
          onGlass = primary,
          glass = glass, memory = memory,
          namespace = namespace,
          selection = selection.getValue(item.id),
          interactive = interactive,
          // Un tasto nuovo dentro un vetro che c'era entra sfocato; uno che arriva con un vetro nuovo lo porta il piano.
          entersBlurred = memory.settled && item.id !in memory.arrivals && item.id !in memory.previousIds && glass in memory.previousGlass,
          shapeKey = fluidToolbarSlotShapeKey(group, FluidToolbarTuning.keptContent),
          menuSource = menuSource,
          groupShared = group.sharedBackground,
          groupCount = group.items.size,
        )
      }
    }
  }
}

private class FluidToolbarMenuSource {
  private var leased by mutableStateOf<Pair<FluidToolbarGlassId, FluidMenuAnchor>?>(null)
  private var modalLeases by mutableStateOf(emptyMap<FluidToolbarGlassId, FluidMenuAnchor>())
  var instant by mutableStateOf(false)
  var handoff by mutableStateOf(0)
    private set

  fun anchor(glass: FluidToolbarGlassId): FluidMenuAnchor? =
    leased?.takeIf { it.first == glass }?.second ?: modalLeases[glass]

  fun transferModal(glass: FluidToolbarGlassId, anchor: FluidMenuAnchor, consumed: Boolean) {
    if (consumed) modalLeases = modalLeases + (glass to anchor)
    else if (modalLeases[glass] === anchor) modalLeases = modalLeases - glass
    else return
    instant = true
    handoff++
  }

  fun transfer(glass: FluidToolbarGlassId, anchor: FluidMenuAnchor, consumed: Boolean) {
    if (consumed) leased = glass to anchor
    else if (leased?.second === anchor) leased = null
    else return
    instant = true
    handoff++
  }
}

/**
 * Che cosa porta il vetro di [group] (F4A.5b, `fluidGlassContentKey`): le chiavi delle facce dei suoi tasti, nell'ordine.
 * Se cambia e il vetro resta della sua misura (il tondo dell'elenco che diventa la lente), il piano lo fa respirare.
 */
internal fun fluidToolbarGlassContentKey(group: FluidToolbarGroup): Any =
  group.items.map { Triple(it.content, it.tintArgb, it.selected) }

/**
 * La forma del vetro per i posti di [group] (F4A.5b): con la variante [FluidToolbarKeptContent.Soft], i tasti che ci sono
 * (cambia quando il gruppo perde o guadagna un tasto, cioè quando il vetro cambia larghezza), e il contenuto che resta si
 * ammorbidisce; con quella di serie null, e niente cambia.
 */
internal fun fluidToolbarSlotShapeKey(group: FluidToolbarGroup, kept: FluidToolbarKeptContent): Any? =
  if (kept == FluidToolbarKeptContent.Soft) group.items.map { it.id } else null

/** Le uscite nascono per lente; se restano, l'identità continua a tenerle ferme. */
internal fun fluidToolbarGlassTransition(group: FluidToolbarGroup): FluidGlassTransition =
  if (group.items.any { it.role == FluidToolbarRole.Back || it.role == FluidToolbarRole.Close }) {
    FluidGlassTransition.MaterializeWhenAlone
  } else {
    FluidGlassTransition.MatchedGeometry
  }

/** Quello che la faccia di un tasto mostra: la chiave dice quando si incrocia sfocandosi. */
private data class FluidToolbarFace(
  val key: Any?,
  val icon: ImageVector?,
  val text: String?,
  val color: Color,
  val custom: (@Composable () -> Unit)?,
  /** La taglia dell'icona: 26 dp nella barra, 24 il chevron dell'indietro (R6), come il tasto indietro di sempre. */
  val size: Dp = FluidMetrics.BarIconSize,
)

/** La taglia dell'icona di un tasto della barra: quella del chevron per l'indietro, quella della barra per gli altri. */
internal fun fluidToolbarIconSize(role: FluidToolbarRole): Dp =
  if (role == FluidToolbarRole.Back) FluidMetrics.BackIconSize else FluidMetrics.BarIconSize

/** Il posto di un tasto in un vetro: la faccia che si sfoca e si incrocia, la selezione, il badge, il bersaglio da 48 dp. */
@Composable
private fun FluidToolbarSlot(
  item: FluidToolbarItem,
  widthDp: Float,
  entry: FluidToolbarButtonEntry?,
  declaration: FluidToolbarDeclaration,
  resolved: FluidToolbarResolved,
  onGlass: Boolean,
  glass: FluidToolbarGlassId,
  memory: FluidToolbarMemory,
  namespace: FluidGlassNamespace,
  selection: State<Float>,
  interactive: Boolean,
  entersBlurred: Boolean,
  /** La forma del vetro, per la variante «morbida» (F4A.5b): vedi [fluidToolbarSlotShapeKey]. */
  shapeKey: Any? = null,
  menuSource: FluidToolbarMenuSource,
  groupShared: Boolean,
  groupCount: Int,
) {
  val scheme = MaterialTheme.colorScheme
  val labels = LocalFluidLabels.current
  val haptics = LocalFluidHaptics.current
  val ink = LocalContentColor.current
  val overflow = item.role == FluidToolbarRole.Overflow
  val tint = entry?.let { fluidToolbarTint(it, scheme.primary, scheme.error) } ?: Color.Unspecified
  val color = when {
    onGlass -> scheme.onPrimary
    tint != Color.Unspecified -> tint
    else -> ink
  }
  val face = FluidToolbarFace(
    key = item.content,
    icon = if (overflow) FluidIcons.MoreHoriz else entry?.icon,
    text = entry?.text,
    color = if (onGlass) color else lerp(color, scheme.primary, selection.value),
    custom = entry?.custom,
    size = fluidToolbarIconSize(item.role),
  )
  val label = when {
    overflow -> declaration.overflowLabel ?: labels.more
    entry == null -> null
    entry.label != null -> entry.label
    entry.defaultLabel == FluidToolbarDefaultLabel.Back -> labels.back
    entry.defaultLabel == FluidToolbarDefaultLabel.Close -> labels.close
    entry.defaultLabel == FluidToolbarDefaultLabel.Confirm -> labels.confirm
    else -> null
  }
  val enabled = entry?.enabled ?: true
  val textStyle = FluidToolbarDefaults.textStyle()
  // Il trabocco: le azioni nascoste, nel menu «Altro» e, per TalkBack, come azioni del tasto.
  val hidden = if (overflow) resolved.overflow.mapNotNull { declaration.buttons[it.id] } else emptyList()
  val menu = if (overflow && interactive) {
    rememberFluidContextMenu(actions = {
      hidden.flatMap { it.menu?.invoke() ?: listOf(it.contextAction(labels.back, labels.close, labels.confirm)) }
    })
  } else {
    null
  }
  val declaredMenu = if (entry?.menu != null && interactive) rememberFluidToolbarMenu() else null
  val source = if (declaredMenu != null) rememberFluidToolbarTransitionSource(item.id) else null
  val sourceCanTravel = declaredMenu != null && groupShared && fluidMenuUsesField()
  val active = menuSource.anchor(glass)
  val groupInMenu = active?.groupBounds != null
  val menuHidden = declaredMenu?.anchor?.hidden == true
  // Il piano possiede il vetro del tasto singolo. Lo cede con lo stesso passaggio istantaneo
  // del menu; su una capsula condivisa il singolo slot cede solo la propria faccia.
  val modalHandoff: ((Boolean) -> Unit)? = if (interactive && groupShared && groupCount == 1) {
    remember(menuSource, glass) {
      val anchor = FluidMenuAnchor()
      var leases = 0
      val handoff: (Boolean) -> Unit = { consume ->
        if (consume && ++leases == 1) menuSource.transferModal(glass, anchor, true)
        if (!consume && --leases == 0) menuSource.transferModal(glass, anchor, false)
      }
      handoff
    }
  } else null
  val effects = if (groupInMenu) {
    val density = LocalDensity.current.density
    remember(density) { FluidToolbarEffects(density) }
  } else {
    null
  }
  val sourceTint = if (declaredMenu != null) GlassDefaults.controlTint() else null
  val sourcePlane = if (declaredMenu != null) currentFluidGlassContainer(FluidCapsuleShape) else null
  val sourceInk = if (declaredMenu != null) rememberGlassInk() else null
  val sourceDark = if (declaredMenu != null) GlassDefaults.isDarkSurface() else false
  val openDeclaredMenu: () -> Unit = {
    if (declaredMenu != null && entry?.menu != null && active == null) {
      val anchor = declaredMenu.anchor
      anchor.face = { FluidToolbarFaceContent(face, textStyle) }
      anchor.tintFrom = sourceTint
      anchor.opticsFrom = GlassDefaults.buttonOptics()
      sourceInk?.let { ink ->
        sourcePlane?.adaptive?.let { ink.bind(it, sourceDark) } ?: ink.unbind()
        anchor.ink = ink
      }
      val groupBounds = if (sourceCanTravel) source?.resolve()?.bounds else null
      if (declaredMenu.open(entry.menu.invoke(), groupBounds) { consumed ->
          if (groupCount == 1 || anchor.groupBounds != null) {
            menuSource.transfer(glass, anchor, consumed)
          }
        }) haptics.play(FluidHapticEvent.Open)
    }
  }
  // Al primo stato la faccia c'è già; un tasto nuovo dentro un vetro che resta parte dal vuoto e arriva sfocato.
  var arrived by remember { mutableStateOf(!entersBlurred) }
  LaunchedEffect(Unit) { arrived = true }
  val width = widthDp.dp
  Box(
    Modifier
      .width(width)
      .height(resolved.metrics.buttonWidthDp.dp)
      .then(if (interactive) Modifier.fluidToolbarTransitionSource(
        item.id, FluidGlassQualifiedId(namespace, glass), modalHandoff,
      ) else Modifier)
      .then(if (declaredMenu != null) Modifier
        .fluidMenuPointerRelay(declaredMenu.anchor.handOff)
        .onGloballyPositioned { declaredMenu.anchor.restBounds = it.boundsInRoot() } else Modifier)
      .then(if (menu != null) Modifier.fluidContextMenuAnchor(menu) else Modifier),
    contentAlignment = Alignment.Center,
  ) {
    if (active == null && (item.selected || selection.value > 0f)) {
      Box(
        Modifier
          .matchParentSize()
          .padding(FluidToolbarDefaults.SelectionInset)
          .graphicsLayer { alpha = selection.value }
          .background(GlassDefaults.selectionTint().overlay, FluidCapsuleShape),
      )
    }
    val transientFace = item.id in memory.transfers || item.id in memory.arrivals
    val iconLayer = if (entry?.custom != null) null else rememberGraphicsLayer()
    if (iconLayer != null) {
      DisposableEffect(iconLayer, glass, item.id) {
        onDispose {
          iconLayer.alpha = 0f
        }
      }
      SideEffect { iconLayer.alpha = if (transientFace) 0f else 1f }
    }
    FluidGlassContentTransitionWithShape(
      targetState = if (arrived) face else null,
      key = { it?.key },
      blurReference = FluidMetrics.BarIconSize,
      shapeKey = shapeKey,
      // Il nome lo dice il bersaglio (sotto): la faccia è muta, o TalkBack direbbe la scritta due volte.
      modifier = Modifier
        .clearAndSetSemantics { }
        .then(if (iconLayer == null) Modifier else Modifier.drawWithContent {
          if (!transientFace) {
            iconLayer.record { this@drawWithContent.drawContent() }
            drawLayer(iconLayer)
          }
        })
        .graphicsLayer {
          val dim = if (enabled) 1f else FluidToolbarDefaults.DisabledAlpha
          val phase = active?.groupProgress?.invoke() ?: 0f
          val u = phase / FluidMotion.MenuGroupFaceProgressSpan
          val role = GlassContentRole.Exit
          alpha = when {
            groupInMenu -> dim * GlassContentCurves.alpha(role, u, effects != null)
            menuHidden -> 0f
            else -> dim
          }
          val s = if (groupInMenu) GlassContentCurves.scale(role, u) else 1f
          scaleX = s
          scaleY = s
          renderEffect = effects?.let {
            val sigma = glassContentSigmaPx(GlassContentCurves.blur(role, u) * GlassContentDefaults.PeakBlur,
              FluidMetrics.BarIconSize.toPx())
            it.at(glassContentBlurRadiusPx(sigma))
          }
        },
    ) { shownFace ->
      if (shownFace != null) FluidToolbarFaceContent(shownFace, textStyle)
    }
    val badge = item.badge
    if (active == null && badge != null) {
      FluidBadge(
        count = badge.text?.toIntOrNull(),
        modifier = Modifier
          .align(Alignment.TopEnd)
          .offset(x = -FluidToolbarDefaults.BadgeOffset, y = FluidToolbarDefaults.BadgeOffset),
      )
    }
    // Il bersaglio: almeno 48 dp per lato, centrato sul posto, senza cambiare la misura del vetro.
    if (interactive && active == null && item.id !in memory.transfers) Box(
      Modifier
        .requiredSize(max(width, FluidMetrics.TouchTarget), FluidMetrics.TouchTarget)
        .then(
          when {
            declaredMenu != null -> Modifier.combinedClickable(
              interactionSource = null,
              indication = null,
              enabled = enabled,
              role = Role.Button,
              onLongClick = openDeclaredMenu,
              onClick = openDeclaredMenu,
            )
            menu != null -> Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              role = Role.Button,
            ) { if (menu.open()) haptics.play(FluidHapticEvent.Open) }
            entry?.onClick != null -> Modifier.clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              enabled = enabled,
              role = Role.Button,
            ) {
              haptics.play(FluidHapticEvent.Tap)
              entry.onClick.invoke()
            }
            else -> Modifier
          },
        )
        .semantics {
          if (label != null) contentDescription = label
          if (item.selected) selected = true
          badge?.text?.let { stateDescription = it }
          if (hidden.isNotEmpty()) {
            customActions = hidden.map { it.contextAction(labels.back, labels.close, labels.confirm) }
              .filter { it.enabled }.map { action ->
                CustomAccessibilityAction(action.label) {
                  action.onClick()
                  true
                }
              }
          }
        },
    )
  }
}

// Gli stessi raggi del materiale si riusano fra icone e fotogrammi.
private class FluidToolbarEffects(density: Float) {
  // Le curve al massimo raggiungono PeakBlur sull'icona più grande della barra.
  private val maxRadius = glassContentBlurRadiusPx(glassContentSigmaPx(
    GlassContentDefaults.PeakBlur, FluidMetrics.BarIconSize.value * density))
  private val blurs by lazy(LazyThreadSafetyMode.NONE) {
    Array(kotlin.math.ceil(maxRadius * 4f).toInt() + 1) { index ->
      if (index == 0) null else BlurEffect(index / 4f, index / 4f, TileMode.Decal)
    }
  }

  fun at(radiusPx: Float): BlurEffect? =
    if (radiusPx <= 0.05f) null else blurs[(radiusPx * 4f).roundToInt().coerceIn(0, blurs.lastIndex)]

}

/** Un contenuto in uscita resta nel riferimento della barra. Chi entra segue il centro del vetro in viaggio. */
@Composable
private fun FluidToolbarFadingFace(
  element: FluidToolbarElement, glass: FluidToolbarGlassId, namespace: FluidGlassNamespace,
  plane: FluidGlassContainerState?, entering: Boolean, clipToGlass: Boolean,
  effects: FluidToolbarEffects, height: Dp, modifier: Modifier, group: FluidToolbarGroup? = null,
  searchInk: State<Float>? = null, finished: () -> Unit,
) {
  val item = element.item
  val overflow = item.id == FluidToolbarOverflowId
  val role = if (entering) GlassContentRole.Enter else GlassContentRole.Exit
  val duration = GlassContentDefaults.DurationMs * LocalFluidMotionPolicy.current.durationScale
  val end = GlassContentCurves.end(role)
  val phase = remember { Animatable(0f) }
  val qualified = remember(namespace, glass) { FluidGlassQualifiedId(namespace, glass) }
  LaunchedEffect(phase, searchInk) {
    if (searchInk == null) {
      phase.animateTo(end, tween((end * duration).roundToInt(), easing = LinearEasing))
    } else {
      val target = if (entering == overflow) 1f else 0f
      snapshotFlow { searchInk.value }.first { it == target }
    }
    if (entering) {
      do { withFrameNanos { } } while (plane?.stage != null || plane?.measurePending == true)
    }
    finished()
  }
  val ink = LocalContentColor.current
  val scheme = MaterialTheme.colorScheme
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val face = FluidToolbarFace(item.content,
    if (item.role == FluidToolbarRole.Overflow) FluidIcons.MoreHoriz else item.symbol as? ImageVector,
    item.label.takeIf { item.symbol == null && item.contentKey == null },
    when {
      item.isPrimary -> scheme.onPrimary
      item.selected -> scheme.primary
      item.tintArgb != null -> Color(item.tintArgb)
      else -> ink
    }, null, fluidToolbarIconSize(item.role))
  val width = element.widthDp.dp
  val blurAvailable = android.os.Build.VERSION.SDK_INT >= 31
  Box(modifier.size(width, height).clearAndSetSemantics { }
    .drawWithContent {
      val bounds = if (clipToGlass) plane?.flightOf(qualified)?.bounds else null
      val barWidth = plane?.coordinates?.size?.width ?: 0
      val left = if (rtl) barWidth - (element.leftDp + element.widthDp).dp.toPx() else element.leftDp.dp.toPx()
      val offset = if (entering && bounds != null && group != null) {
        val targetCenter = (group.leftDp + group.widthDp / 2f).dp.toPx()
        bounds.center.x - if (rtl) barWidth - targetCenter else targetCenter
      } else 0f
      translate(left = offset) {
        if (bounds == null) this@drawWithContent.drawContent()
        else clipRect(
          left = bounds.left - left - offset, right = bounds.right - left - offset,
        ) { this@drawWithContent.drawContent() }
      }
    }
    .graphicsLayer {
      val u = phase.value
      val inkPhase = searchInk?.value
      val opacity = if (inkPhase == null) GlassContentCurves.alpha(role, u, blurAvailable)
        else toolbarSearchInkAlpha(inkPhase, overflow)
      alpha = opacity * if (item.enabled) 1f else FluidToolbarDefaults.DisabledAlpha
      scaleX = if (inkPhase == null) GlassContentCurves.scale(role, u) else 1f
      scaleY = scaleX
      val blur = (if (inkPhase == null) GlassContentCurves.blur(role, u)
        else toolbarSearchInkBlur(inkPhase, overflow)) * GlassContentDefaults.PeakBlur
      renderEffect = if (blurAvailable) effects.at(glassContentBlurRadiusPx(glassContentSigmaPx(blur, face.size.toPx()))) else null
    }, contentAlignment = Alignment.Center) {
    FluidToolbarFaceContent(face, FluidToolbarDefaults.textStyle())
  }
}

@Composable
private fun FluidToolbarFaceContent(face: FluidToolbarFace, textStyle: TextStyle) {
  when {
    face.custom != null -> Box(Modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) { face.custom.invoke() }
    face.text != null -> Text(
      text = face.text,
      style = textStyle,
      color = face.color,
      maxLines = 1,
      softWrap = false,
      overflow = TextOverflow.Ellipsis,
    )
    face.icon != null -> FluidIcon(
      imageVector = face.icon,
      contentDescription = null,
      tint = face.color,
      size = face.size,
    )
  }
}

/** Il tasto come voce del menu «Altro». */
private fun FluidToolbarButtonEntry.contextAction(back: String, close: String, confirm: String): FluidContextAction =
  FluidContextAction(
    label = accessibleName(back, close, confirm),
    icon = icon,
    destructive = destructive,
    enabled = enabled && onClick != null,
    selected = selected,
    onClick = { onClick?.invoke() },
  )


private fun FluidToolbarButtonEntry.accessibleName(back: String, close: String, confirm: String): String =
  label ?: text ?: when (defaultLabel) {
    FluidToolbarDefaultLabel.Back -> back
    FluidToolbarDefaultLabel.Close -> close
    FluidToolbarDefaultLabel.Confirm -> confirm
    null -> id.toString()
  }
